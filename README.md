# TEC API POC

This codebase is a local sandbox for experimentation around CCD config and its effect upon Manage Cases.

Functionality is underpinned by runtime supplied by [rse-cft-lib](https://github.com/hmcts/rse-cft-lib). See
[Where TEC sits in HMCTS](tech_docs/source/hmcts-context.html.md.erb) for the wider service, the shared platform, and the common components in use,
[TEC decentralised CCD architecture](tech_docs/source/ccd-architecture.html.md.erb) for the build-time and local runtime architecture, and
[CFTLib Shared Database](tech_docs/source/cftlib-shared-database.html.md.erb) for a description of the decentralised CCD datamodel.
Rendered versions are on the tech docs site at http://localhost:4568 (for example
[/ccd-architecture.html](http://localhost:4568/ccd-architecture.html)).

All CCD config including states, events, roles and case types are for illustration only.

## Prerequisites

- Java 21
- Docker
- An authenticated HMCTS Azure Container Registry session (`az acr login --name hmctsprod`)
- `jq` for the command-line example below
- Ruby 3.3 and Bundler (optional; needed for the design/tech docs previews started with `bootWithCCD`)

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
- Manage Case (XUI): http://localhost:3000 (nav-injection proxy; real container on :3002)
- Design docs (GOV.UK Tech Docs / Middleman): http://localhost:4567
- Tech docs (GOV.UK Tech Docs / Middleman): http://localhost:4568
- CCD Data Store: http://localhost:4452
- IDAM simulator: http://localhost:5062
- S2S simulator: http://localhost:8489
- Shared PostgreSQL: `localhost:6432` (the TEC database is `tec`)

`bootWithCCD` starts both docs previews via `bin/start-design-docs.sh` (:4567) and
`bin/start-tech-docs.sh` (:4568) (first run may run `bundle install` under each site). If
Ruby/Bundler are missing, the stack still starts and the docs servers are skipped; install
Ruby 3.3 and re-run, or start either script alone.

Design docs publish to GitHub Pages from `.github/workflows/deploy-pages.yml` when `design_docs/`
changes on `main`. Enable **Settings → Pages → Source: GitHub Actions**, and keep `host` /
`github_repo` in `design_docs/config/tech-docs.yml` aligned with the Pages URL.

Stop the Java stack with `Ctrl-C`. The Docker containers will continue to run; to tear everything down
(Java processes, local stubs/proxies, design/tech docs servers, and CFTLib containers) and free the required ports:

```bash
./bin/stop-boot-with-ccd.sh
```

To stop and start again in one step:

```bash
./bin/restart-boot-with-ccd.sh
```

### Use Manage Case

CFTLib starts the Manage Case web application in Docker on port **3002**. A local proxy on
**http://localhost:3000** injects a TEC **Upload batch file** primary-nav item (see
[tech_docs/source/exui-navigation.html.md.erb](tech_docs/source/exui-navigation.html.md.erb)
or http://localhost:4568/exui-navigation.html).

Open http://localhost:3000 and sign in with a configured local account:

```text
Username: tec-demo@test.com
Password: password
```

Local authority demo user (GM-scoped TEC / batch access; no Create case / exceptions / Tasks):

```text
Username: tec-la-demo@test.com
Password: password
```

After sign-in you should see **Upload batch file** in the primary navigation (local simulation of the
ExUI `menuConfigs` change). Clerks also see **Create case**; LA users do not. That link opens the
`uploadBatch` CCD wizard (`/cases/case-create/TEC/TEC_BATCH/uploadBatch`): select batch type, upload
a file, review placeholder validation, confirm the statement of truth, Check your answers, then
Submit. The confirmation screen shows the new case number (no Manage cases link in the body). Open
Case list via **Manage cases** and use the case type filter to switch between PCN cases
(`TEC` / **TEC PCN**), batches (`TEC_BATCH` / **TEC Batch**), and exception cases
(`TEC_EXCEPTION` / **TEC Exception**) — LA users do not see Exception.

Seed scripts leave `localAuthority` as-is; `CaseAccessCategory` is derived server-side. For LA-visible
cases, override when seeding, e.g. `LOCAL_AUTHORITY=manchesterCityCouncil ./bin/create-tec-case.sh -`
(or the same env var with `./bin/create-tec-cases.sh -`).
Re-seed or recreate cases after this change so categories are populated.

Journey detail: [tech_docs/source/ccd-architecture.html.md.erb](tech_docs/source/ccd-architecture.html.md.erb#upload-batch-file-journey-uploadbatch)
(or http://localhost:4568/ccd-architecture.html#upload-batch-file-journey-uploadbatch).

## Clear TEC cases

With `bootWithCCD` running, wipe all local TEC case data (PCN, batch, exception)
from the `tec` and `datastore` databases **and** the Elasticsearch search indices (so ExUI Find
case / work-basket do not keep ghost results):

```bash
./bin/clear-tec-cases.sh          # asks y/n before deleting
./bin/clear-tec-cases.sh --yes    # skip confirmation
```

Then recreate cases with the seed scripts below.

Full inventory of every script under `bin/`:
[tech_docs/source/local-scripts.html.md.erb](tech_docs/source/local-scripts.html.md.erb)
(or http://localhost:4568/local-scripts.html when tech docs are running).

## Create a PCN case

With `bootWithCCD` running, create a valid TEC case using the local system user. Run any of the
scripts with no arguments (or `-h` / `--help`) to print usage:

```bash
./bin/create-tec-case.sh -
```

Pass `-` to create without a batch link. The script generates unique valid identifiers and submits
an amount of `12345` pence. Set `AMOUNT_DUE`, `FILE_IDENTIFIER`, `BATCH_IDENTIFIER`, or
`PENALTY_CHARGE_NUMBER` to override those defaults.

To create the PCN already linked to an existing batch case (`TEC_BATCH`), pass the batch case
reference as an argument or set `BATCH_CASE_REFERENCE` (hyphens optional). The script verifies the
batch exists (and is `TEC_BATCH`) first, then creates the PCN and runs `link-pcn-to-batch.sh`
(`linkBatchCase` with `batchLinkCase` + `batchLinkType`, then batch `linkPcnCases`). Behaviour
depends on the batch type: registration sets Case details **Batch case**; other types use
`tec_batch_pcn_link` only.

```bash
./bin/create-tec-batch.sh westminster   # note the batch caseReference from the response
./bin/create-tec-case.sh <batch-case-reference>
# or
BATCH_CASE_REFERENCE=<batch-case-reference> ./bin/create-tec-case.sh
```

Bulk seeding scripts accept the same option:

```bash
CASE_COUNT=20 ./bin/create-tec-cases.sh -
CASE_COUNT=20 ./bin/create-tec-cases.sh <batch-case-reference>
CASES_PER_AUTHORITY=10 ./bin/create-gm-tec-cases.sh -
CASES_PER_AUTHORITY=10 ./bin/create-gm-tec-cases.sh <batch-case-reference>
```

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
    "penaltyChargeNumber": "TE1234567A0",
    "localAuthority": "westminster",
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
`localAuthority` is a FixedList code from the 2023 England councils list (for example `westminster`,
`manchesterCityCouncil`).

The response contains the CCD-generated reference and initial state:

```json
{
  "caseReference": 1755000000000000,
  "state": "PENDING_CASE_ISSUED"
}
```

## Create a batch

Batches are a second CCD case type (`TEC_BATCH`, display name **TEC Batch**).

**In Manage Case:** use primary nav **Upload batch file** to run the clerk `uploadBatch` wizard (see
above). That creates a real batch case through CCD.

**Via API / scripts** (hidden `createBatch` event — for seeding demos). No args shows help
(unless `LOCAL_AUTHORITY` is set):

```bash
./bin/create-tec-batch.sh westminster
./bin/create-tec-batch.sh westminster warrantAuthRequests
./bin/create-tec-batch.sh manchesterCityCouncil
./bin/create-tec-batches.sh 6
./bin/create-tec-batches.sh 3 registration
```

Pass a FixedList local-authority code as the first argument (or set `LOCAL_AUTHORITY`). Optional
second argument is the batch type / operation (`registration` default, or `OPERATION` if set):
`registration`, `warrantAuthRequests`, `warrantReissueRequests`, `outOfTimeDecisions`,
`changeOfAddress`, `caseClosureRequests`, `transferRequest`. Other optional overrides: `FILE_IDENTIFIER`,
`BATCH_IDENTIFIER`, `PCN_COUNT`, `RECEIVED_VIA`, `SUBMITTER_EMAIL`,
`TARGET_STATE` (`QUEUED_FOR_PROCESSING` | `PROCESSING_STARTED` | `PROCESSING_COMPLETE` | `PROCESSING_FAILED`).
`create-tec-batches.sh` requires a count; it rotates authorities and batch types unless
`LOCAL_AUTHORITY` / `[batch-type]` / `OPERATION` is set.
Completed batches get sample Inputs/Outputs documents attached for Case File View demos.
To finish a queued batch with real Outputs files:
`./bin/complete-batch-processing.sh <batch-ref> <file-1> <file-2>`.

See [local-scripts.html](tech_docs/source/local-scripts.html.md.erb) for the full `bin/` inventory.

In Manage Case, open Case list → set case type to **TEC Batch** → open a row for Tasks,
Batch details, Case File View, History, and Linked Cases. Batch details shows file identifier, batch identifier, local
authority, **Submitter email**, and batch type (and no longer shows batch validation result).
Registration batches also show **Fees due** while queued and **Fees paid** when processing is
complete (`PCN count × £11`). Linked Cases lists PCN cases that reference the batch.
Filter by **File identifier**, **Submitter email**, and optionally batch identifier; case list results
show file identifier and submitter email rather than batch identifier.
See [tech_docs/source/ccd-architecture.html.md.erb](tech_docs/source/ccd-architecture.html.md.erb#batch-details-presentation)
(or http://localhost:4568/ccd-architecture.html#batch-details-presentation).

## Create an exception case

Exception cases are a third CCD case type (`TEC_EXCEPTION`, display name **TEC Exception**). Case
details show Form validation result and Associated TEC case as `—`, plus a PCN. Clerk Next steps
are **Reject item** (radio reason + optional History comment; case stays open) and **Edit PCN**.

With `bootWithCCD` running:

```bash
./bin/create-tec-exception-case.sh
```

Optional override: `PENALTY_CHARGE_NUMBER`.

Or via the API (same token pattern as PCN create):

```bash
curl --request POST http://localhost:4013/exception-cases \
  --header "Authorization: Bearer ${TOKEN}" \
  --header 'Content-Type: application/json' \
  --data '{
    "penaltyChargeNumber": "AB1234567A0"
  }'
```

In Manage Case, open Case list → set case type to **TEC Exception** → open a row for Tasks,
Roles and access, Case details, Case File View, and History.

### Prototype Tasks tab (local)

The **Tasks** tab is a CCD collection tab backed by prototype data in `TecCaseView`, not Work Allocation.
No extra docker services or Azure registry access are required.

Create a case and move it between states to see how Tasks change. Prefer
`set-case-state.sh` for arbitrary jumps (including `CLOSED`); use
`transition-to-case-issued.sh` when you want the real payment event:

```bash
./bin/create-tec-case.sh -
./bin/transition-to-case-issued.sh <case-reference-from-output>
./bin/set-case-state.sh <case-reference> AWAITING_RESPONDENT_RESPONSE
./bin/set-case-state.sh <case-reference> CLOSED
```

Open the case in Manage Case as `tec-demo@test.com` to see the **Tasks** tab.

### Attach a document to Case File View (local)

Case File View folders are empty until documents are attached. CFTLib's Case Document AM API
proxies uploads to dm-store on port `4506`. `bootWithCCD` starts the local dm-store stub
automatically; if Case File View opens blank, ensure the stub is still running
(the document viewer loads binaries through CDAM → dm-store):

```bash
./bin/start-local-dm-store.sh
```

With `bootWithCCD` running (restart it after pulling these changes so the attach event, migration and
document URL pattern are loaded), create a case and attach a file:

```bash
./bin/create-tec-case.sh -
./bin/attach-case-file-document.sh <case-reference> "Hearing documents" ./path/to/file.pdf
```

If the filename has spaces, quote it:

```bash
./bin/attach-case-file-document.sh <case-reference> "Applications" "Witness statement - Out of time.pdf"
```

`<folder>` may be a category id or label: `hearingDocuments`, `ordersAndNoticesOfHearings`,
`applications`, `correspondence`, `uncategorisedDocuments` (or the matching display labels).

Refresh the case in Manage Case to see the file under the chosen Case File View folder.

### Link a PCN case to a batch case (local)

With `bootWithCCD` running (restart after pulling so the `linkBatchCase` /
`linkPcnCases` events and `V17` / `V20` migrations are loaded), link a PCN to a batch case
(registration, warrant auth, or other batch types).

**At create time** (preferred for new seed data):

```bash
./bin/create-tec-batch.sh westminster
./bin/create-tec-case.sh <batch-case-reference>
```

**For an existing PCN**:

```bash
./bin/link-pcn-to-batch.sh <pcn-case-reference> <batch-case-reference>
```

Hyphens in either case reference are optional. Both paths:

1. Submit PCN `linkBatchCase` (`batchLinkCase` CaseLink to `TEC_BATCH` + `batchLinkType` matching
   the batch operation — not Case details `batchCase`) — registration sets Case details **Batch
   case** via the current `tec_case_registration.batch_case_reference`; other types record membership
   in `tec_batch_pcn_link` only. A case may have one registration batch per registration. History
   records the event either way. Linking is additive: existing registration / Linked Cases entries remain.
2. Submit batch `linkPcnCases` with the full `caseLinks` collection and Reason `CLRC007` (Other);
   `OtherDescription` depends on the batch type (for example **Linked as part of a batch of
   registrations** or **… warrant auth requests**) — ExUI Linked Cases shows the PCN under the
   batch's **linked to** list and the batch under the PCN's **linked from** list (alongside any
   other batches already linked)

If Reasons stay blank locally, run `./bin/fix-linked-case-reasons.sh` (nav proxy LOV stub) and
restart the app so CaseView emits `CLRC007`.

### Generate a sample TE9/PE3 application (local)

With `bootWithCCD` running, generate application data for an existing case, submit the
`recordApplication` event, fill the TE9/PE3 PDF template, and attach it under **Applications**:

```bash
./bin/generate-application.sh <case-reference> "out of time" TE9
./bin/generate-application.sh <case-reference> "in time" PE3
```

Hyphens in the case reference are ignored. Type may be `in time` / `out of time` (or
`in-time` / `out-of-time`, `inTime` / `outOfTime`). Form must be `TE9` or `PE3`.
Attached PDFs include the current registration PCN, for example
`Witness statement - Out of time - AB0531612A0.pdf` or
`Statutory declaration - Out of time - AB0531612A2.pdf`.

The script copies PCN, VRN, name and address from the case where possible and randomly
fills the remaining application fields. Set `SEED=<n>` for reproducible random values.
On first run the script creates `bin/.venv-generate-application` and installs `pypdf` /
`reportlab` there for PDF filling.

Each recorded form stores its own **Form validation result**, shown at the top of that form's
Case details section. The default is `Form valid`. Set `FORM_VALIDATION_RESULT` to store a
different message on that form only:

```bash
FORM_VALIDATION_RESULT="Invalid - fields missing" \
  ./bin/generate-application.sh <case-reference> "out of time" TE9
```

These messages also change the generated form so the data matches the result:

- `Invalid - name does not match registration` — applicant and full name `JORDAN UNRELATED`
- `Invalid - fields missing` — location, address, and declaration left blank
- `Invalid - application not signed` — no signature on the PDF

Any other text is stored as the message and the rest of the form is filled as usual.

### Generate a sample TE7/PE2 time-extension request (local)

With `bootWithCCD` running, generate time-extension data for an existing case, submit the
`recordTimeExtension` event, fill the TE7/PE2 PDF template, and attach it under **Applications**:

```bash
./bin/generate-time-extension.sh <case-reference> TE7
./bin/generate-time-extension.sh <case-reference> PE2
```

Hyphens in the case reference are ignored. Form must be `TE7` or `PE2`. Set `SEED=<n>` for
reproducible random values. The script reuses the same Python venv as
`generate-application.sh`. Attached PDFs are named from the section heading plus the current
registration PCN (for TE7, the heading depends on permission sought), for example
`Application to file out of time - AB0531612A2.pdf` or
`Application for extension of time - AB0531612A0.pdf`.

`FORM_VALIDATION_RESULT` works the same way as on `generate-application.sh`: it replaces the
default `Form valid` message on that TE7 or PE2. For a TE7,
`Invalid - application is for more time, expecting application to file out of time` also sets
permission sought to **for more time**, so the PDF and Case details heading are
**Application for extension of time**. `Invalid - fields missing` leaves the address and reasons
blank. `Invalid - application not signed` records signed and dated as **No** and omits the
signature. `Invalid - name does not match registration` uses the name `JORDAN UNRELATED`.

### Add a registration (local)

With `bootWithCCD` running, add another registration to an existing PCN. The script creates a
registration batch (`PCN_COUNT=1`), submits `addRegistration` with the next PCN suffix, and links
that batch. Respondent details, local authority, vehicle, offence, certificate date, and amount
are copied from the case. Case-list fields stay as they were at create.

```bash
./bin/add-tec-registration.sh <case-reference>
```

### Enter an N244 general application (local)

With `bootWithCCD` running, upload the N244 and submit `enterGeneralApplication` for an
existing PCN. Case state stays the same. Case details gains a **General applications** row
(state **Issued**), and the PDF appears under Case File View → **Applications**.

The event is clerk-only, so the script signs in as `tec-demo@test.com`.

```bash
./bin/enter-general-application.sh <case-reference> "local authority"
./bin/enter-general-application.sh <case-reference> respondent
APPLICATION_TYPE=adjourn WITHIN_14_DAYS=yes \
  ./bin/enter-general-application.sh <case-reference> respondent
```

Applicant is `local authority` or `respondent`. Defaults are a something-else application
received yesterday, categories **OOT refusal appeal**, fee £126.00. Override with
`APPLICATION_TYPE`, `SOMETHING_ELSE_DETAILS`, `DATE_RECEIVED`, `FEE_AMOUNT` (pence),
`APPLIED_FOR_HWF`, `HWF_REFERENCE`, `ALL_PARTIES_AGREE`, and `WITHOUT_NOTICE`.
`DATE_RECEIVED` must be in the past. The event rejects an unpaid fee.

### Apply a warrant authorisation (local)

```bash
./bin/apply-warrant-authorisation.sh <case-reference>
DATE_OF_ISSUE=2026-09-22 DATE_OF_EXPIRY=2027-09-22 STATUS=active \
  ./bin/apply-warrant-authorisation.sh <case-reference>
```

Submits the system `applyWarrantAuthorisation` event. Case details then shows a **Warrant
authorisations** section listing each entry (date of issue, date of expiry, status). Defaults:
issue = today, expiry = today + 1 year, status = `active` (`expired` / `cancelled` also accepted).

Documents are stored by the local dm-store stub under `bin/.local-dm-store-data/`. If that stub
was restarted before persistence was added, older folder entries can still appear while the viewer
stays blank — re-attach the file once so the binary is available again.

Manage Case loads the viewer via its `/documentsv2` proxy to Case Document AM (`:4455`).
`bootWithCCD` sets that through `XUI_DOCUMENTS_API(_V2)` in `build.gradle` (compose interpolates
these into the XUI container). If the viewer is empty and XUI logs show proxying to `:5062`
instead of `:4455`, recreate Manage Case with those env vars set, or restart `bootWithCCD`
after pulling the Gradle fix so Manage Case picks up the CDAM URLs.
