#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
readonly PARTIAL_PATH="${REPO_ROOT}/design_docs/source/partials/_local_demo_case_links.html.erb"
readonly EXAMPLES_PARTIAL_PATH="${REPO_ROOT}/design_docs/source/partials/_previous_registration_examples.html.erb"
readonly JSON_PATH="${SCRIPT_DIR}/.demo-catalogue.json"
readonly TEMPLATES_DIR="${SCRIPT_DIR}/templates"

# shellcheck source=lib/authority_pcn_id.sh
source "${SCRIPT_DIR}/lib/authority_pcn_id.sh"

TEC_API_URL="${TEC_API_URL:-http://localhost:4013}"
EXUI_BASE_URL="${EXUI_BASE_URL:-http://localhost:3000}"
DESIGN_DOCS_URL="${DESIGN_DOCS_URL:-http://localhost:4567/local-demo-cases.html}"
SKIP_CLEAR="${SKIP_CLEAR:-false}"
REGISTRATION_PCN_COUNT="${REGISTRATION_PCN_COUNT:-3}"

# Accumulated catalogue entries as a JSON array string.
CATALOGUE_JSON='[]'

usage() {
  cat <<EOF
Usage: ${0} [OPTIONS]
       ${0} -h|--help

Clear local TEC cases, seed a curated demo catalogue using existing bin/
scripts, and write design-docs case links.

Options:
  --skip-clear   Do not run clear-tec-cases.sh (re-seed on top of existing data)
  -h, --help     Show this help and exit

Optional environment variables:
  TEC_API_URL (default: http://localhost:4013)
  EXUI_BASE_URL (default: http://localhost:3000)
  DESIGN_DOCS_URL (default: http://localhost:4567/local-demo-cases.html)
  SKIP_CLEAR (default: false)
  REGISTRATION_PCN_COUNT (default: 3) — filler PCNs linked to the shared processed registration batch
  LOCAL_AUTHORITY (default: westminster) — catalogue batches and PCNs use this authority's TEC id

See tech docs: Local demo catalogue seed.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-clear)
      SKIP_CLEAR=true
      ;;
    -h | --help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
  shift
done

for command in curl jq; do
  if ! command -v "${command}" >/dev/null 2>&1; then
    echo "Required command not found: ${command}" >&2
    exit 1
  fi
done

require_stack() {
  if ! curl --silent --show-error --fail --connect-timeout 3 --max-time 10 \
    "${TEC_API_URL}/health" >/dev/null 2>&1; then
    echo "TEC API not reachable at ${TEC_API_URL}/health." >&2
    echo "Start the stack with ./gradlew bootWithCCD and try again." >&2
    exit 1
  fi
}

DEMO_LOCAL_AUTHORITY="${LOCAL_AUTHORITY:-westminster}"
DEMO_AUTHORITY_ID="$(authority_pcn_id "${DEMO_LOCAL_AUTHORITY}")"
export AUTHORITY_CODE="${DEMO_AUTHORITY_ID}"

unique_batch_identifier() {
  # CreateBatchRequest: ^R[A-Z]{2,3}[0-9]{6}$ using the catalogue authority's TEC id.
  printf 'R%s%06d' "${DEMO_AUTHORITY_ID}" "$((($(date +%s) + RANDOM + $$) % 1000000))"
}

# Capture JSON stdout from a create script; print caseReference on stdout.
# Returns non-zero on failure (safe to use inside `if`).
create_ref_from() {
  local response
  if ! response="$("$@")"; then
    return 1
  fi
  local ref
  ref="$(jq --raw-output '.caseReference // empty' <<<"${response}")"
  if [[ -z "${ref}" ]]; then
    echo "Create script did not return caseReference:" >&2
    echo "${response}" >&2
    return 1
  fi
  printf '%s\n' "${ref}"
}

require_ref() {
  local ref
  if ! ref="$(create_ref_from "$@")"; then
    exit 1
  fi
  printf '%s\n' "${ref}"
}

# PCN for the catalogue authority. Check digit comes from generate-pcn-number.sh.
# File and batch identifiers use the same TEC id so create-tec-case validation accepts them.
require_demo_pcn() {
  local pcn file_number batch_number
  pcn="$("${SCRIPT_DIR}/generate-pcn-number.sh" "${DEMO_AUTHORITY_ID}")"
  file_number="$(printf '%05d' "$((($(date +%s) + RANDOM + $$) % 100000))")"
  batch_number="$(printf '%06d' "$((($(date +%s) * 37 + RANDOM) % 1000000))")"
  require_ref env \
    "LOCAL_AUTHORITY=${DEMO_LOCAL_AUTHORITY}" \
    "PENALTY_CHARGE_NUMBER=${pcn}" \
    "FILE_IDENTIFIER=R${DEMO_AUTHORITY_ID}${file_number}" \
    "BATCH_IDENTIFIER=R${DEMO_AUTHORITY_ID}${batch_number}" \
    "${SCRIPT_DIR}/create-tec-case.sh" -
}

require_demo_exception() {
  local pcn
  pcn="$("${SCRIPT_DIR}/generate-pcn-number.sh" "${DEMO_AUTHORITY_ID}")"
  PENALTY_CHARGE_NUMBER="${pcn}" require_ref "${SCRIPT_DIR}/create-tec-exception-case.sh"
}

# Run a mutate script that prints CCD/event JSON; print .state (or fallback).
state_from_json_cmd() {
  local fallback="$1"
  shift
  local response state
  response="$("$@")" || return 1
  state="$(jq --raw-output '.state // empty' <<<"${response}")"
  if [[ -z "${state}" ]]; then
    printf '%s\n' "${fallback}"
  else
    printf '%s\n' "${state}"
  fi
}

attach_pcn_doc() {
  local case_reference="$1"
  local folder="$2"
  local file_path="$3"
  echo "  attaching $(basename -- "${file_path}") → ${folder} on PCN ${case_reference}..." >&2
  "${SCRIPT_DIR}/attach-case-file-document.sh" \
    "${case_reference}" "${folder}" "${file_path}" >/dev/null
}

attach_batch_doc() {
  local case_reference="$1"
  local folder="$2"
  local file_path="$3"
  echo "  attaching $(basename -- "${file_path}") → ${folder} on batch ${case_reference}..." >&2
  CASE_TYPE_ID=TEC_BATCH EVENT_ID=attachBatchDocument \
    "${SCRIPT_DIR}/attach-case-file-document.sh" \
    "${case_reference}" "${folder}" "${file_path}" >/dev/null
}

# Every seeded batch gets Batch file.xlsx under Inputs; transferRequest also gets TE10.png.
attach_standard_batch_inputs() {
  local batch_ref="$1"
  local batch_type="${2:-}"
  attach_batch_doc "${batch_ref}" inputs "${TEMPLATES_DIR}/Batch file.xlsx"
  if [[ "${batch_type}" == "transferRequest" ]]; then
    attach_batch_doc "${batch_ref}" inputs "${TEMPLATES_DIR}/TE10.png"
  fi
}

gen_application() {
  local case_reference="$1"
  local timing="$2"
  local form="$3"
  local validation_result="${4:-}"
  echo "  generating ${timing} ${form} application on ${case_reference}..." >&2
  if [[ -n "${validation_result}" ]]; then
    FORM_VALIDATION_RESULT="${validation_result}" \
      "${SCRIPT_DIR}/generate-application.sh" \
      "${case_reference}" "${timing}" "${form}" >/dev/null
  else
    "${SCRIPT_DIR}/generate-application.sh" \
      "${case_reference}" "${timing}" "${form}" >/dev/null
  fi
}

# A registration batch used by one registration only.
seed_own_registration_batch() {
  local batch_ref
  echo "  creating registration batch..." >&2
  BATCH_IDENTIFIER="$(unique_batch_identifier)"
  batch_ref="$(
    TARGET_STATE=PROCESSING_COMPLETE \
      require_ref "${SCRIPT_DIR}/create-tec-batch.sh" "${DEMO_LOCAL_AUTHORITY}" registration
  )"
  attach_standard_batch_inputs "${batch_ref}" registration
  printf '%s\n' "${batch_ref}"
}

# Record a TE9 against the registration that is current, then add the next
# registration on a new registration batch.
add_registration_after_te9() {
  local case_reference="$1"
  local response batch_ref
  gen_application "${case_reference}" "in time" TE9
  echo "  adding registration on ${case_reference}..." >&2
  response="$(
    TARGET_STATE=PROCESSING_COMPLETE \
      "${SCRIPT_DIR}/add-tec-registration.sh" "${case_reference}"
  )"
  batch_ref="$(jq --raw-output '.batchCaseReference // empty' <<<"${response}")"
  if [[ -z "${batch_ref}" ]]; then
    echo "add-tec-registration.sh did not return batchCaseReference" >&2
    echo "${response}" >&2
    exit 1
  fi
  attach_standard_batch_inputs "${batch_ref}" registration
}

gen_time_extension() {
  local case_reference="$1"
  local form="$2"
  local validation_result="${3:-}"
  echo "  generating ${form} time extension on ${case_reference}..." >&2
  if [[ -n "${validation_result}" ]]; then
    FORM_VALIDATION_RESULT="${validation_result}" \
      "${SCRIPT_DIR}/generate-time-extension.sh" \
      "${case_reference}" "${form}" >/dev/null
  else
    "${SCRIPT_DIR}/generate-time-extension.sh" \
      "${case_reference}" "${form}" >/dev/null
  fi
}

# N244 on the five OOT-appeal demos. Same path as enter-general-application.sh:
# Issued general application plus N244_0622.pdf under Applications. Defaults
# (something else, categories "OOT refusal appeal", fee £126) come from that
# script. Applicant defaults to the respondent.
attach_n244() {
  local case_reference="$1"
  local applicant="${2:-respondent}"
  echo "  entering N244 general application on ${case_reference} as ${applicant}..." >&2
  "${SCRIPT_DIR}/enter-general-application.sh" \
    "${case_reference}" "${applicant}" >/dev/null
}

link_pcn_to_batch() {
  local pcn_ref="$1"
  local batch_ref="$2"
  echo "  linking ${pcn_ref} → ${batch_ref}..." >&2
  "${SCRIPT_DIR}/link-pcn-to-batch.sh" "${pcn_ref}" "${batch_ref}" >/dev/null
}

record_entry() {
  local id="$1"
  local title="$2"
  local case_type="$3"
  local state="$4"
  local case_reference="$5"
  local explanation="$6"
  local url="${EXUI_BASE_URL}/cases/case-details/${case_reference}"

  CATALOGUE_JSON="$(jq \
    --arg id "${id}" \
    --arg title "${title}" \
    --arg caseType "${case_type}" \
    --arg state "${state}" \
    --arg caseReference "${case_reference}" \
    --arg url "${url}" \
    --arg explanation "${explanation}" \
    '. + [{
      id: $id,
      title: $title,
      caseType: $caseType,
      state: $state,
      caseReference: $caseReference,
      url: $url,
      explanation: $explanation
    }]' <<<"${CATALOGUE_JSON}")"

  echo "  recorded ${id}: ${case_type} ${case_reference} (${state})" >&2
}

html_escape() {
  # Minimal escape for table cell text.
  local s="$1"
  s="${s//&/&amp;}"
  s="${s//</&lt;}"
  s="${s//>/&gt;}"
  s="${s//\"/&quot;}"
  printf '%s' "${s}"
}

write_partial() {
  local generated_at
  generated_at="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  mkdir -p "$(dirname "${PARTIAL_PATH}")"

  {
    cat <<EOF
<%#
  Generated by bin/seed-demo-catalogue.sh at ${generated_at}.
  Do not edit by hand — re-run the seed script. This file is gitignored.
%>
<table class="govuk-table">
  <caption class="govuk-table__caption govuk-table__caption--m">Seeded local demo cases</caption>
  <thead class="govuk-table__head">
    <tr class="govuk-table__row">
      <th scope="col" class="govuk-table__header">Demo</th>
      <th scope="col" class="govuk-table__header">Type</th>
      <th scope="col" class="govuk-table__header">State</th>
      <th scope="col" class="govuk-table__header">Case</th>
      <th scope="col" class="govuk-table__header">What to look at</th>
    </tr>
  </thead>
  <tbody class="govuk-table__body">
EOF

    local count
    count="$(jq 'length' <<<"${CATALOGUE_JSON}")"
    local i
    for ((i = 0; i < count; i++)); do
      local id title case_type state case_reference url explanation
      id="$(jq --raw-output --argjson i "${i}" '.[$i].id' <<<"${CATALOGUE_JSON}")"
      title="$(jq --raw-output --argjson i "${i}" '.[$i].title' <<<"${CATALOGUE_JSON}")"
      case_type="$(jq --raw-output --argjson i "${i}" '.[$i].caseType' <<<"${CATALOGUE_JSON}")"
      state="$(jq --raw-output --argjson i "${i}" '.[$i].state' <<<"${CATALOGUE_JSON}")"
      case_reference="$(jq --raw-output --argjson i "${i}" '.[$i].caseReference' <<<"${CATALOGUE_JSON}")"
      url="$(jq --raw-output --argjson i "${i}" '.[$i].url' <<<"${CATALOGUE_JSON}")"
      explanation="$(jq --raw-output --argjson i "${i}" '.[$i].explanation' <<<"${CATALOGUE_JSON}")"

      cat <<EOF
    <tr class="govuk-table__row" id="demo-$(html_escape "${id}")">
      <th scope="row" class="govuk-table__header">$(html_escape "${title}")</th>
      <td class="govuk-table__cell"><code>$(html_escape "${case_type}")</code></td>
      <td class="govuk-table__cell"><code>$(html_escape "${state}")</code></td>
      <td class="govuk-table__cell"><a class="govuk-link" href="$(html_escape "${url}")">${case_reference}</a></td>
      <td class="govuk-table__cell">$(html_escape "${explanation}")</td>
    </tr>
EOF
    done

    cat <<EOF
  </tbody>
</table>
<p class="govuk-body-s">Generated ${generated_at}. Open links while signed in to Manage Cases on ${EXUI_BASE_URL}.</p>
EOF
  } >"${PARTIAL_PATH}"
}

format_case_reference() {
  local raw="${1//-/}"
  if [[ ${#raw} -eq 16 && "${raw}" =~ ^[0-9]+$ ]]; then
    printf '%s-%s-%s-%s' "${raw:0:4}" "${raw:4:4}" "${raw:8:4}" "${raw:12:4}"
  else
    printf '%s' "$1"
  fi
}

# Case numbers for the catalogue PCNs that have previous registrations.
# Included from the design docs penalty charge numbers page.
write_previous_registration_examples() {
  local generated_at examples count i
  generated_at="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  examples="$(jq -c '[.[] | select(.id == "pcn-two-previous-registrations" or .id == "pcn-three-previous-registrations")]' <<<"${CATALOGUE_JSON}")"
  mkdir -p "$(dirname "${EXAMPLES_PARTIAL_PATH}")"

  {
    cat <<EOF
<%#
  Generated by bin/seed-demo-catalogue.sh at ${generated_at}.
  Do not edit by hand — re-run the seed script. This file is gitignored.
%>
<table class="govuk-table">
  <caption class="govuk-table__caption govuk-table__caption--m">Cases with previous registrations</caption>
  <thead class="govuk-table__head">
    <tr class="govuk-table__row">
      <th scope="col" class="govuk-table__header">Demo</th>
      <th scope="col" class="govuk-table__header">Case</th>
      <th scope="col" class="govuk-table__header">What to look at</th>
    </tr>
  </thead>
  <tbody class="govuk-table__body">
EOF

    count="$(jq 'length' <<<"${examples}")"
    for ((i = 0; i < count; i++)); do
      local title case_reference url explanation display_reference
      title="$(jq --raw-output --argjson i "${i}" '.[$i].title' <<<"${examples}")"
      case_reference="$(jq --raw-output --argjson i "${i}" '.[$i].caseReference' <<<"${examples}")"
      url="$(jq --raw-output --argjson i "${i}" '.[$i].url' <<<"${examples}")"
      explanation="$(jq --raw-output --argjson i "${i}" '.[$i].explanation' <<<"${examples}")"
      display_reference="$(format_case_reference "${case_reference}")"

      cat <<EOF
    <tr class="govuk-table__row">
      <th scope="row" class="govuk-table__header">$(html_escape "${title}")</th>
      <td class="govuk-table__cell"><a class="govuk-link" href="$(html_escape "${url}")">$(html_escape "${display_reference}")</a></td>
      <td class="govuk-table__cell">$(html_escape "${explanation}")</td>
    </tr>
EOF
    done

    cat <<EOF
  </tbody>
</table>
<p class="govuk-body-s">Generated ${generated_at}. Open links while signed in to Manage Cases on ${EXUI_BASE_URL}.</p>
EOF
  } >"${EXAMPLES_PARTIAL_PATH}"
}

write_json() {
  jq --null-input \
    --arg generatedAt "$(date -u +"%Y-%m-%dT%H:%M:%SZ")" \
    --argjson entries "${CATALOGUE_JSON}" \
    '{ generatedAt: $generatedAt, entries: $entries }' \
    >"${JSON_PATH}"
}

seed_catalogue() {
  local ref batch_ref state
  local reg_batch_ref warrant_auth_complete_ref companion_ref

  export BATCH_IDENTIFIER

  # --- Shared batches (must exist before any PCN that links to them) ---

  echo "Seeding batch-registration-processed..." >&2
  BATCH_IDENTIFIER="$(unique_batch_identifier)"
  reg_batch_ref="$(
    TARGET_STATE=PROCESSING_COMPLETE \
      require_ref "${SCRIPT_DIR}/create-tec-batch.sh" "${DEMO_LOCAL_AUTHORITY}" registration
  )"
  attach_standard_batch_inputs "${reg_batch_ref}" registration
  record_entry \
    "batch-registration-processed" \
    "Batch — registration processed (shared)" \
    "TEC_BATCH" \
    "PROCESSING_COMPLETE" \
    "${reg_batch_ref}" \
    "Shared processed registration batch. Catalogue PCNs with a single registration link here (Case details Batch case + Linked Cases). The previous-registration demos use a separate batch for each registration. Filler PCNs are added at the end of the seed."

  echo "Seeding batch-registration-queued..." >&2
  BATCH_IDENTIFIER="$(unique_batch_identifier)"
  batch_ref="$(
    TARGET_STATE=QUEUED_FOR_PROCESSING \
      require_ref "${SCRIPT_DIR}/create-tec-batch.sh" "${DEMO_LOCAL_AUTHORITY}" registration
  )"
  attach_standard_batch_inputs "${batch_ref}" registration
  record_entry \
    "batch-registration-queued" \
    "Batch — registration queued (unlinked)" \
    "TEC_BATCH" \
    "QUEUED_FOR_PROCESSING" \
    "${batch_ref}" \
    "Registration batch still queued with no linked PCNs. Case File View Inputs has Batch file.xlsx; Linked Cases is empty."

  echo "Seeding batch-queued..." >&2
  BATCH_IDENTIFIER="$(unique_batch_identifier)"
  batch_ref="$(
    TARGET_STATE=QUEUED_FOR_PROCESSING \
      require_ref "${SCRIPT_DIR}/create-tec-batch.sh" "${DEMO_LOCAL_AUTHORITY}" warrantAuthRequests
  )"
  attach_standard_batch_inputs "${batch_ref}" warrantAuthRequests
  record_entry \
    "batch-queued" \
    "Batch — queued for processing" \
    "TEC_BATCH" \
    "QUEUED_FOR_PROCESSING" \
    "${batch_ref}" \
    "Warrant-auth batch still queued and unlinked. Case File View Inputs has Batch file.xlsx."

  echo "Seeding batch-processing-complete..." >&2
  BATCH_IDENTIFIER="$(unique_batch_identifier)"
  warrant_auth_complete_ref="$(
    TARGET_STATE=PROCESSING_COMPLETE \
      require_ref "${SCRIPT_DIR}/create-tec-batch.sh" "${DEMO_LOCAL_AUTHORITY}" warrantAuthRequests
  )"
  attach_standard_batch_inputs "${warrant_auth_complete_ref}" warrantAuthRequests
  attach_batch_doc "${warrant_auth_complete_ref}" outputs "${TEMPLATES_DIR}/PE3.pdf"
  record_entry \
    "batch-processing-complete" \
    "Batch — processing complete" \
    "TEC_BATCH" \
    "PROCESSING_COMPLETE" \
    "${warrant_auth_complete_ref}" \
    "Processed warrant-auth batch (Inputs: Batch file.xlsx; Outputs: PE3.pdf). Warrant PCN demos link here."

  echo "Seeding batch-processing-failed..." >&2
  BATCH_IDENTIFIER="$(unique_batch_identifier)"
  batch_ref="$(
    TARGET_STATE=PROCESSING_FAILED \
      require_ref "${SCRIPT_DIR}/create-tec-batch.sh" "${DEMO_LOCAL_AUTHORITY}" warrantAuthRequests
  )"
  attach_standard_batch_inputs "${batch_ref}" warrantAuthRequests
  record_entry \
    "batch-processing-failed" \
    "Batch — processing failed" \
    "TEC_BATCH" \
    "PROCESSING_FAILED" \
    "${batch_ref}" \
    "Warrant-auth batch that failed during processing and has no linked PCNs. Case File View Inputs has Batch file.xlsx."

  # --- Catalogue PCNs (create → link registration → type-specific → mutate) ---

  echo "Seeding pcn-pending-case-issued..." >&2
  ref="$(require_demo_pcn)"
  link_pcn_to_batch "${ref}" "${reg_batch_ref}"
  record_entry \
    "pcn-pending-case-issued" \
    "PCN — pending case issued" \
    "TEC" \
    "PENDING_CASE_ISSUED" \
    "${ref}" \
    "Fresh registration with payment still pending. Linked Cases shows the shared processed registration batch. Case File View is empty."

  echo "Seeding pcn-case-issued..." >&2
  ref="$(require_demo_pcn)"
  link_pcn_to_batch "${ref}" "${reg_batch_ref}"
  state="$(state_from_json_cmd CASE_ISSUED "${SCRIPT_DIR}/transition-to-case-issued.sh" "${ref}")"
  gen_application "${ref}" "in time" TE9
  record_entry \
    "pcn-case-issued" \
    "PCN — case issued" \
    "TEC" \
    "${state}" \
    "${ref}" \
    "After registration payment succeeded. Linked Cases shows the shared registration batch; Applications has an in-time TE9."

  echo "Seeding pcn-awaiting-oot-validation..." >&2
  ref="$(require_demo_pcn)"
  link_pcn_to_batch "${ref}" "${reg_batch_ref}"
  gen_application "${ref}" "out of time" TE9 "Invalid - fields missing"
  gen_time_extension "${ref}" TE7 \
    "Invalid - application is for more time, expecting application to file out of time"
  state="$(state_from_json_cmd AWAITING_OOT_VALIDATION \
    "${SCRIPT_DIR}/set-case-state.sh" "${ref}" AWAITING_OOT_VALIDATION)"
  record_entry \
    "pcn-awaiting-oot-validation" \
    "PCN — awaiting OOT validation" \
    "TEC" \
    "${state}" \
    "${ref}" \
    "Out-of-time application waiting for a clerk to check the forms. The TE9 form validation result is Invalid - fields missing. The TE7 is an application for more time, so its result is Invalid - application is for more time, expecting application to file out of time. Next steps offers Edit TE9 application, Edit TE7 application, and Validate OOT application."

  echo "Seeding pcn-awaiting-oot-validation-pe..." >&2
  ref="$(require_demo_pcn)"
  link_pcn_to_batch "${ref}" "${reg_batch_ref}"
  gen_application "${ref}" "out of time" PE3 "Invalid - name does not match registration"
  gen_time_extension "${ref}" PE2 "Invalid - application not signed"
  state="$(state_from_json_cmd AWAITING_OOT_VALIDATION \
    "${SCRIPT_DIR}/set-case-state.sh" "${ref}" AWAITING_OOT_VALIDATION)"
  record_entry \
    "pcn-awaiting-oot-validation-pe" \
    "PCN — awaiting OOT validation (PE3 and PE2)" \
    "TEC" \
    "${state}" \
    "${ref}" \
    "Out-of-time statutory declaration waiting for a clerk to check the forms. The PE3 form validation result is Invalid - name does not match registration. The PE2 result is Invalid - application not signed. Next steps offers Edit PE3 application, Edit PE2 application, and Validate OOT application."

  echo "Seeding pcn-awaiting-la-oot..." >&2
  ref="$(require_demo_pcn)"
  link_pcn_to_batch "${ref}" "${reg_batch_ref}"
  gen_application "${ref}" "out of time" TE9
  gen_time_extension "${ref}" TE7
  state="$(state_from_json_cmd AWAITING_LA_OOT_RESPONSE \
    "${SCRIPT_DIR}/set-case-state.sh" "${ref}" AWAITING_LA_OOT_RESPONSE)"
  record_entry \
    "pcn-awaiting-la-oot" \
    "PCN — awaiting LA OOT response" \
    "TEC" \
    "${state}" \
    "${ref}" \
    "Waiting for an LA out-of-time response (not yet linked to an outOfTimeDecisions batch). Linked Cases shows registration only; Applications has OOT TE9 and TE7."

  echo "Seeding pcn-pending-refusal-decision..." >&2
  ref="$(require_demo_pcn)"
  link_pcn_to_batch "${ref}" "${reg_batch_ref}"
  BATCH_IDENTIFIER="$(unique_batch_identifier)"
  companion_ref="$(require_ref "${SCRIPT_DIR}/create-tec-batch.sh" "${DEMO_LOCAL_AUTHORITY}" outOfTimeDecisions)"
  attach_standard_batch_inputs "${companion_ref}" outOfTimeDecisions
  link_pcn_to_batch "${ref}" "${companion_ref}"
  gen_application "${ref}" "out of time" TE9
  gen_time_extension "${ref}" TE7
  state="$(state_from_json_cmd PENDING_REFUSAL_DECISION \
    "${SCRIPT_DIR}/set-case-state.sh" "${ref}" PENDING_REFUSAL_DECISION)"
  record_entry \
    "pcn-pending-refusal-decision" \
    "PCN — pending refusal decision" \
    "TEC" \
    "${state}" \
    "${ref}" \
    "Linked Cases shows the shared registration batch and an out-of-time decisions companion. Applications has OOT TE9 and TE7. Case details shows Out of time application decision (Refused) under Registration. Next step includes Review OOT refusal decision."

  echo "Seeding pcn-pending-oot-appeal-payment..." >&2
  ref="$(require_demo_pcn)"
  link_pcn_to_batch "${ref}" "${reg_batch_ref}"
  BATCH_IDENTIFIER="$(unique_batch_identifier)"
  companion_ref="$(require_ref "${SCRIPT_DIR}/create-tec-batch.sh" "${DEMO_LOCAL_AUTHORITY}" outOfTimeDecisions)"
  attach_standard_batch_inputs "${companion_ref}" outOfTimeDecisions
  link_pcn_to_batch "${ref}" "${companion_ref}"
  gen_application "${ref}" "out of time" TE9
  gen_time_extension "${ref}" TE7
  state="$(state_from_json_cmd PENDING_OOT_APPEAL_PAYMENT \
    "${SCRIPT_DIR}/set-case-state.sh" "${ref}" PENDING_OOT_APPEAL_PAYMENT)"
  attach_n244 "${ref}"
  record_entry \
    "pcn-pending-oot-appeal-payment" \
    "PCN — pending OOT appeal payment" \
    "TEC" \
    "${state}" \
    "${ref}" \
    "Linked Cases shows registration and an out-of-time decisions companion. Applications has OOT TE9, TE7, and N244_0622.pdf. Case details shows General applications: Issued, respondent, something else, OOT refusal appeal."

  echo "Seeding pcn-oot-appeal-payment-confirmed..." >&2
  ref="$(require_demo_pcn)"
  link_pcn_to_batch "${ref}" "${reg_batch_ref}"
  BATCH_IDENTIFIER="$(unique_batch_identifier)"
  companion_ref="$(require_ref "${SCRIPT_DIR}/create-tec-batch.sh" "${DEMO_LOCAL_AUTHORITY}" outOfTimeDecisions)"
  attach_standard_batch_inputs "${companion_ref}" outOfTimeDecisions
  link_pcn_to_batch "${ref}" "${companion_ref}"
  gen_application "${ref}" "out of time" TE9
  gen_time_extension "${ref}" TE7
  state="$(state_from_json_cmd OOT_APPEAL_PAYMENT_CONFIRMED \
    "${SCRIPT_DIR}/set-case-state.sh" "${ref}" OOT_APPEAL_PAYMENT_CONFIRMED)"
  attach_n244 "${ref}"
  record_entry \
    "pcn-oot-appeal-payment-confirmed" \
    "PCN — OOT appeal payment confirmed" \
    "TEC" \
    "${state}" \
    "${ref}" \
    "Linked Cases shows registration and an out-of-time decisions companion. Applications has OOT TE9, TE7, and N244_0622.pdf. Case details shows General applications: Issued, respondent, something else, OOT refusal appeal."

  echo "Seeding pcn-pending-oot-appeal-decision..." >&2
  ref="$(require_demo_pcn)"
  link_pcn_to_batch "${ref}" "${reg_batch_ref}"
  BATCH_IDENTIFIER="$(unique_batch_identifier)"
  companion_ref="$(require_ref "${SCRIPT_DIR}/create-tec-batch.sh" "${DEMO_LOCAL_AUTHORITY}" outOfTimeDecisions)"
  attach_standard_batch_inputs "${companion_ref}" outOfTimeDecisions
  link_pcn_to_batch "${ref}" "${companion_ref}"
  gen_application "${ref}" "out of time" TE9
  gen_time_extension "${ref}" TE7
  state="$(state_from_json_cmd PENDING_OOT_APPEAL_DECISION \
    "${SCRIPT_DIR}/set-case-state.sh" "${ref}" PENDING_OOT_APPEAL_DECISION)"
  attach_n244 "${ref}"
  record_entry \
    "pcn-pending-oot-appeal-decision" \
    "PCN — pending OOT appeal decision" \
    "TEC" \
    "${state}" \
    "${ref}" \
    "Linked Cases shows registration and an out-of-time decisions companion. Applications has OOT TE9, TE7, and N244_0622.pdf. Case details shows General applications: Issued, respondent, something else, OOT refusal appeal."

  echo "Seeding pcn-oot-appeal-refused..." >&2
  ref="$(require_demo_pcn)"
  link_pcn_to_batch "${ref}" "${reg_batch_ref}"
  BATCH_IDENTIFIER="$(unique_batch_identifier)"
  companion_ref="$(require_ref "${SCRIPT_DIR}/create-tec-batch.sh" "${DEMO_LOCAL_AUTHORITY}" outOfTimeDecisions)"
  attach_standard_batch_inputs "${companion_ref}" outOfTimeDecisions
  link_pcn_to_batch "${ref}" "${companion_ref}"
  gen_application "${ref}" "out of time" TE9
  gen_time_extension "${ref}" TE7
  state="$(state_from_json_cmd OOT_APPEAL_REFUSED \
    "${SCRIPT_DIR}/set-case-state.sh" "${ref}" OOT_APPEAL_REFUSED)"
  attach_n244 "${ref}"
  record_entry \
    "pcn-oot-appeal-refused" \
    "PCN — OOT appeal refused" \
    "TEC" \
    "${state}" \
    "${ref}" \
    "Linked Cases shows registration and an out-of-time decisions companion. Applications has OOT TE9, TE7, and N244_0622.pdf. Case details shows General applications: Issued, respondent, something else, OOT refusal appeal."

  echo "Seeding pcn-case-revoked-oot-appeal-accepted..." >&2
  ref="$(require_demo_pcn)"
  link_pcn_to_batch "${ref}" "${reg_batch_ref}"
  BATCH_IDENTIFIER="$(unique_batch_identifier)"
  companion_ref="$(require_ref "${SCRIPT_DIR}/create-tec-batch.sh" "${DEMO_LOCAL_AUTHORITY}" outOfTimeDecisions)"
  attach_standard_batch_inputs "${companion_ref}" outOfTimeDecisions
  link_pcn_to_batch "${ref}" "${companion_ref}"
  gen_application "${ref}" "out of time" TE9
  gen_time_extension "${ref}" TE7
  state="$(state_from_json_cmd CASE_REVOKED_OOT_APPEAL_ACCEPTED \
    "${SCRIPT_DIR}/set-case-state.sh" "${ref}" CASE_REVOKED_OOT_APPEAL_ACCEPTED)"
  attach_n244 "${ref}"
  record_entry \
    "pcn-case-revoked-oot-appeal-accepted" \
    "PCN — case revoked OOT appeal accepted" \
    "TEC" \
    "${state}" \
    "${ref}" \
    "Linked Cases shows registration and an out-of-time decisions companion. Applications has OOT TE9, TE7, and N244_0622.pdf. Case details shows General applications: Issued, respondent, something else, OOT refusal appeal."

  echo "Seeding pcn-warrant-issued..." >&2
  ref="$(require_demo_pcn)"
  link_pcn_to_batch "${ref}" "${reg_batch_ref}"
  link_pcn_to_batch "${ref}" "${warrant_auth_complete_ref}"
  state="$(
    STATUS=active state_from_json_cmd WARRANT_AUTHORISATION_ISSUED \
      "${SCRIPT_DIR}/apply-warrant-authorisation.sh" "${ref}"
  )"
  record_entry \
    "pcn-warrant-issued" \
    "PCN — warrant authorisation issued" \
    "TEC" \
    "${state}" \
    "${ref}" \
    "Active warrant on Case details. Linked Cases shows the shared registration batch and the processed warrant-auth batch."

  echo "Seeding pcn-warrant-expired..." >&2
  ref="$(require_demo_pcn)"
  link_pcn_to_batch "${ref}" "${reg_batch_ref}"
  link_pcn_to_batch "${ref}" "${warrant_auth_complete_ref}"
  BATCH_IDENTIFIER="$(unique_batch_identifier)"
  companion_ref="$(require_ref "${SCRIPT_DIR}/create-tec-batch.sh" "${DEMO_LOCAL_AUTHORITY}" warrantReissueRequests)"
  attach_standard_batch_inputs "${companion_ref}" warrantReissueRequests
  link_pcn_to_batch "${ref}" "${companion_ref}"
  state="$(
    STATUS=expired state_from_json_cmd WARRANT_AUTHORISATION_EXPIRED \
      "${SCRIPT_DIR}/apply-warrant-authorisation.sh" "${ref}"
  )"
  record_entry \
    "pcn-warrant-expired" \
    "PCN — warrant authorisation expired" \
    "TEC" \
    "${state}" \
    "${ref}" \
    "Expired warrant for comparison. Linked Cases shows registration, processed warrant-auth, and a warrant-reissue companion batch."

  echo "Seeding pcn-refer-enforcement..." >&2
  ref="$(require_demo_pcn)"
  link_pcn_to_batch "${ref}" "${reg_batch_ref}"
  BATCH_IDENTIFIER="$(unique_batch_identifier)"
  companion_ref="$(require_ref "${SCRIPT_DIR}/create-tec-batch.sh" "${DEMO_LOCAL_AUTHORITY}" transferRequest)"
  attach_standard_batch_inputs "${companion_ref}" transferRequest
  link_pcn_to_batch "${ref}" "${companion_ref}"
  record_entry \
    "pcn-refer-enforcement" \
    "PCN — refer for enforcement" \
    "TEC" \
    "REFER_FOR_ENFORCEMENT" \
    "${ref}" \
    "Linked Cases shows the shared registration batch and a transfer-request companion (Inputs: Batch file.xlsx and TE10.png). State is Refer for Enforcement."

  echo "Seeding pcn-closed..." >&2
  ref="$(require_demo_pcn)"
  link_pcn_to_batch "${ref}" "${reg_batch_ref}"
  BATCH_IDENTIFIER="$(unique_batch_identifier)"
  companion_ref="$(require_ref "${SCRIPT_DIR}/create-tec-batch.sh" "${DEMO_LOCAL_AUTHORITY}" caseClosureRequests)"
  attach_standard_batch_inputs "${companion_ref}" caseClosureRequests
  link_pcn_to_batch "${ref}" "${companion_ref}"
  record_entry \
    "pcn-closed" \
    "PCN — closed" \
    "TEC" \
    "CLOSED" \
    "${ref}" \
    "Linked Cases shows the shared registration batch and a case-closure-requests companion (Inputs: Batch file.xlsx). State is Closed."

  echo "Seeding pcn-two-previous-registrations..." >&2
  ref="$(require_demo_pcn)"
  link_pcn_to_batch "${ref}" "$(seed_own_registration_batch)"
  state="$(state_from_json_cmd CASE_ISSUED "${SCRIPT_DIR}/transition-to-case-issued.sh" "${ref}")"
  add_registration_after_te9 "${ref}"
  add_registration_after_te9 "${ref}"
  record_entry \
    "pcn-two-previous-registrations" \
    "PCN — two previous registrations" \
    "TEC" \
    "${state}" \
    "${ref}" \
    "Previous registrations lists two earlier registrations, lowest suffix first. Each registration is on its own registration batch. Applications has two in-time TE9s, one for each previous registration PCN. Case details shows the latest registration only."

  echo "Seeding pcn-three-previous-registrations..." >&2
  ref="$(require_demo_pcn)"
  link_pcn_to_batch "${ref}" "$(seed_own_registration_batch)"
  state="$(state_from_json_cmd CASE_ISSUED "${SCRIPT_DIR}/transition-to-case-issued.sh" "${ref}")"
  add_registration_after_te9 "${ref}"
  add_registration_after_te9 "${ref}"
  add_registration_after_te9 "${ref}"
  record_entry \
    "pcn-three-previous-registrations" \
    "PCN — three previous registrations" \
    "TEC" \
    "${state}" \
    "${ref}" \
    "Previous registrations lists three earlier registrations, lowest suffix first. Each registration is on its own registration batch. Applications has three in-time TE9s, one for each previous registration PCN. Case details shows the latest registration only."

  echo "Seeding filler PCNs on shared registration (${REGISTRATION_PCN_COUNT})..." >&2
  CASE_COUNT="${REGISTRATION_PCN_COUNT}" \
    "${SCRIPT_DIR}/create-tec-cases.sh" "${reg_batch_ref}" >/dev/null

  echo "Seeding exception-pending-review..." >&2
  ref="$(require_demo_exception)"
  record_entry \
    "exception-pending-review" \
    "Exception — pending review" \
    "TEC_EXCEPTION" \
    "EXCEPTION_PENDING_REVIEW" \
    "${ref}" \
    "Exception case for review Tasks (no Case File View attach event yet). Use Case list filtered to TEC Exception."
}

main() {
  require_stack

  if [[ "${SKIP_CLEAR}" == "true" ]]; then
    echo "Skipping clear (--skip-clear)." >&2
  else
    echo "Clearing existing TEC cases..." >&2
    "${SCRIPT_DIR}/clear-tec-cases.sh" --yes
  fi

  echo "Seeding demo catalogue..." >&2
  seed_catalogue

  write_partial
  write_previous_registration_examples
  write_json

  local count
  count="$(jq 'length' <<<"${CATALOGUE_JSON}")"
  echo >&2
  echo "Seeded ${count} catalogue entries." >&2
  echo "Wrote ${PARTIAL_PATH}" >&2
  echo "Wrote ${EXAMPLES_PARTIAL_PATH}" >&2
  echo "Wrote ${JSON_PATH}" >&2
  echo "Design docs: ${DESIGN_DOCS_URL}" >&2
  echo "Sign in to Manage Cases at ${EXUI_BASE_URL} as tec-demo@test.com / password." >&2
}

main
