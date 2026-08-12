# TEC API POC

This codebase is a local sandbox for experimentation around CCD config and its effect upon Manage Cases.

Functionality is underpinned by runtime supplied by [rse-cft-lib](https://github.com/hmcts/rse-cft-lib). See
(AI-generated) doc
[TEC decentralised CCD architecture](docs/ccd-architecture.md) for the build-time and local runtime architecture, and
[CFTLib Shared Database](docs/cftlib-shared-database.md) for a description of the decentralised CCD datamodel.

All CCD config including states, events, roles and case types are for illustration only.

## Prerequisites

- Java 21
- Docker
- An authenticated HMCTS Azure Container Registry session (`az acr login --name hmctsprod`)
- `jq` for the command-line example below

Gradle is provided by the checked-in `./gradlew` wrapper.

## Run TEC with a local CCD stack

Start the application together with CCD Data Store, Definition Store, User Profile, local IDAM/S2S simulators, and
their supporting infrastructure:

```bash
./gradlew bootWithCCD
```

CFTLib runs the Java services in isolated classloaders in one JVM and uses Docker for
supporting infrastructure. It is therefore a clear stand-in for the CFT platform, but avoids the cost of
running every CCD Java service as a separate container.

The local services are:

- TEC API and decentralised callback runtime: http://localhost:4013
- Manage Case (XUI): http://localhost:3000
- CCD Data Store: http://localhost:4452
- IDAM simulator: http://localhost:5062
- S2S simulator: http://localhost:8489
- Shared PostgreSQL: `localhost:6432` (the TEC database is `tec`)

Stop the Java stack with `Ctrl-C`. The docker containers will continue to run, so they must be stopped separately if
desired.

### Use Manage Case

CFTLib starts the Manage Case web application in Docker.
Open http://localhost:3000 and sign in with the configured local clerk account:

```text
Username: tec-demo@test.com
Password: password
```

## Create a PCN case

With `bootWithCCD` running, create a valid TEC case using the local system user:

```bash
./bin/create-tec-case.sh
```

The script generates unique valid identifiers and submits an amount of `12345` pence. Set `AMOUNT_DUE`,
`FILE_IDENTIFIER`, `BATCH_IDENTIFIER`, or `PENALTY_CHARGE_NUMBER` to override those defaults.

To make the request manually, obtain a token for the local TEC system user (password `password`):

```bash
TOKEN=$(curl --silent --request POST http://localhost:5062/o/token \
  --header 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'grant_type=password' \
  --data-urlencode 'client_id=tec' \
  --data-urlencode 'client_secret=123456' \
  --data-urlencode 'username=tec-system@test.com' \
  --data-urlencode 'password=password' \
  --data-urlencode 'scope=openid profile roles' | jq --raw-output '.access_token')
```

Then call the small TEC-facing API:

```bash
curl --request POST http://localhost:4013/pcn-cases \
  --header "Authorization: Bearer ${TOKEN}" \
  --header 'Content-Type: application/json' \
  --data '{
    "fileIdentifier": "RTE12345",
    "batchIdentifier": "RTE123456",
    "penaltyChargeNumber": "TE1234567A8",
    "respondentDetails1": "ALEX EXAMPLE",
    "respondentDetails2": "1 EXAMPLE STREET",
    "respondentDetails3": "LONDON",
    "respondentDetails4": "SW1A 1AA",
    "vehicleRegistrationNumber": "AB12CDE",
    "natureOfOffence": "01",
    "dateChargeCertificateServed": "260824",
    "amountDue": 12345
  }'
```

`amountDue` is expressed in pence; for example, `12345` represents £123.45.

The response contains the CCD-generated reference and initial state:

```json
{
  "caseReference": 1755000000000000,
  "state": "PENDING_CASE_ISSUED"
}
```
