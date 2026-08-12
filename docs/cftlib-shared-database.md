# CFTLib database layout for decentralised CCD

This document explains the database ownership used by the current proof of concept. For the end-to-end component and
request flows, see [TEC decentralised CCD architecture](ccd-architecture.md).

## One PostgreSQL server, multiple databases

With `./gradlew bootWithCCD`, CFTLib exposes a shared PostgreSQL server on `localhost:6432`. “Shared” describes the
server/container; it does not mean that every service writes to one logical database. CFTLib provisions separate
databases for its platform services and this project adds a `tec` database with:

```groovy
environment 'RSE_LIB_ADDITIONAL_DATABASES', 'tec'
```

TEC receives the matching connection settings from `build.gradle`:

```text
DB_HOST=localhost
DB_PORT=6432
DB_NAME=tec
DB_USER_NAME=postgres
DB_PASSWORD=postgres
```

Without CFTLib overrides, `application.yaml` defaults to PostgreSQL on `localhost:5432`, database/user/password `tec`.

## Data ownership

| Location | Owner | Purpose |
| --- | --- | --- |
| `datastore.public.*` | CCD Data Store | CCD's central orchestration, access and routing data |
| `tec.ccd.*` | Decentralised runtime embedded in TEC | Local CCD lifecycle state, revisions, event history and runtime bookkeeping |
| `tec.public.tec_case` | TEC application | The PCN business fields used by this proof of concept |

Tables with similar names in `datastore.public` and `tec.ccd` are different physical objects in different databases.
They serve different owners and transaction boundaries; they are not full replicas of one another.

```text
CCD client
    |
    v
CCD Data Store (datastore database)
    |
    | route case type TEC to http://localhost:4013
    v
TEC decentralised runtime (tec.ccd schema)
    |
    | invoke event handler or TecCaseView
    v
TEC business persistence (tec.public.tec_case)
```

## TEC business table

Flyway migration `V1__create_tec_case.sql` creates one application-owned table. It stores:

- the CCD case reference as its primary key;
- file, batch and penalty-charge identifiers;
- six respondent detail lines;
- vehicle, offence, certificate-date and amount fields;
- payment status/reference and closure reason; and
- registration document/date.

`payment_status` defaults to `PENDING`. The migration enforces identifier formats and matching authority prefixes,
uppercase respondent details, vehicle/offence/date formats, a unique file/batch/PCN tuple, and an amount range of
0–999999 pence. The HTTP request applies corresponding validation before calling CCD.

The table deliberately represents the current single-table POC, not a future normalised datafile/batch/PCN model.

## Why the `tec.ccd` schema also exists

Setting `decentralised = true` adds the CCD decentralised runtime to TEC. Migrations bundled with that dependency
create its tables in the `ccd` schema. They are runtime-owned implementation detail used to execute delegated CCD
operations safely, including lifecycle state, revisions, concurrency and event history.

Keeping this data in the same `tec` database allows the runtime bookkeeping and the Java event handler's write to
participate in one local database transaction:

```text
BEGIN
  update/insert tec.ccd runtime records
  insert/update tec.public.tec_case
COMMIT
```

If the handler fails, the local business and lifecycle changes can roll back together without a distributed
transaction across service-owned databases.

Application code should not treat the `ccd` schema as its business model. `TecCaseRepository` reads and writes only
`public.tec_case`; the embedded runtime owns the `ccd` schema.

## Reads and projections

The business table is the source of current PCN field values. When CCD asks TEC for a case, the runtime calls
`TecCaseView`, which uses only the CCD case reference to load `public.tec_case` and return a `TecCase` projection.

This separates the concerns:

| Concern | Source of truth |
| --- | --- |
| PCN business facts | `tec.public.tec_case` |
| Decentralised CCD lifecycle and history | Runtime-owned tables in `tec.ccd` |
| Central CCD orchestration and routing | CCD Data Store's `datastore` database |
| Case definition and permissions | Generated `TEC` definition imported into Definition Store |
| Current CCD-facing values | Projection returned by `TecCaseView` |

Exact runtime table names and columns are owned by the pinned SDK dependency and should not be relied on by TEC
application code.

## Local versus production

The shared PostgreSQL server is a CFTLib convenience. A production deployment would use platform-managed CCD
databases and a TEC-owned PostgreSQL service, with credentials and networking supplied by the environment. The
ownership boundaries remain the same even though the databases would no longer share one local container.
