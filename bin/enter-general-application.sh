#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

CCD_URL="${CCD_DATA_STORE_URL:-http://localhost:4452}"
CDAM_URL="${CASE_DOCUMENT_AM_URL:-http://localhost:4455}"
EVENT_ID="${EVENT_ID:-enterGeneralApplication}"
CASE_TYPE_ID="${CASE_TYPE_ID:-TEC}"
JURISDICTION_ID="${JURISDICTION_ID:-TEC}"
CLASSIFICATION="${DOCUMENT_CLASSIFICATION:-PUBLIC}"
TEMPLATES_DIR="${SCRIPT_DIR}/templates"
N244_PATH="${N244_FILE:-${TEMPLATES_DIR}/N244_0622.pdf}"

# The event is granted to the clerk role only (caseworker-tec), not the system user.
IDAM_USERNAME="${IDAM_USERNAME:-tec-demo@test.com}"

CASE_REFERENCE_RAW="${1:-}"
APPLICANT_RAW="${2:-}"

APPLICATION_TYPE_RAW="${APPLICATION_TYPE:-something else}"
FEE_AMOUNT="${FEE_AMOUNT:-12600}"
APPLIED_FOR_HWF_RAW="${APPLIED_FOR_HWF:-no}"
HWF_REFERENCE="${HWF_REFERENCE:-}"
ALL_PARTIES_AGREE_RAW="${ALL_PARTIES_AGREE:-yes}"
WITHOUT_NOTICE_RAW="${WITHOUT_NOTICE:-}"
WITHIN_14_DAYS_RAW="${WITHIN_14_DAYS:-}"
SOMETHING_ELSE_DETAILS="${SOMETHING_ELSE_DETAILS:-}"

for command in curl jq; do
  if ! command -v "${command}" >/dev/null 2>&1; then
    echo "Required command not found: ${command}" >&2
    exit 1
  fi
done

usage() {
  cat <<EOF
Usage: ${0} <case-reference> <applicant>

Record an N244 on a TEC PCN as if a clerk had submitted enterGeneralApplication:
  - upload N244_0622.pdf to Case Document AM
  - submit enterGeneralApplication (state unchanged, application Issued)
  - the event stores the general application and files the N244 under
    Case File View → Applications

<applicant>  local authority | respondent
             (also local-authority, la, LOCAL_AUTHORITY, RESPONDENT)

The event is clerk-only. This script signs in as tec-demo@test.com unless
IDAM_USERNAME is set.

Optional environment variables:
  APPLICATION_TYPE       adjourn | set aside | something else (default: something else)
  DATE_RECEIVED          YYYY-MM-DD, must be in the past (default: yesterday)
  SOMETHING_ELSE_DETAILS categories for "something else"
                         (default: OOT refusal appeal)
  WITHIN_14_DAYS         yes | no (default: yes; sent only for adjourn)
  FEE_AMOUNT             pence already received (default: 12600, £126.00)
  APPLIED_FOR_HWF        yes | no (default: no)
  HWF_REFERENCE          required when APPLIED_FOR_HWF=yes
  ALL_PARTIES_AGREE      yes | no (default: yes)
  WITHOUT_NOTICE         yes | no (default: no; sent only when parties do not agree)
  N244_FILE              PDF to upload (default: bin/templates/N244_0622.pdf)
  CCD_DATA_STORE_URL, CASE_DOCUMENT_AM_URL, IDAM_USERNAME, EVENT_ID

The fee must already have been received. The event rejects an unpaid fee.
EOF
}

if [[ -z "${CASE_REFERENCE_RAW}" || -z "${APPLICANT_RAW}" \
  || "${CASE_REFERENCE_RAW}" == "-h" || "${CASE_REFERENCE_RAW}" == "--help" ]]; then
  usage >&2
  exit 1
fi

CASE_REFERENCE="$(printf '%s' "${CASE_REFERENCE_RAW}" | tr -d '-')"
if [[ ! "${CASE_REFERENCE}" =~ ^[0-9]+$ ]]; then
  echo "Case reference must contain digits (hyphens optional): '${CASE_REFERENCE_RAW}'" >&2
  exit 1
fi

normalise_applicant() {
  local value
  value="$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]' | tr -d '_-[:space:]')"
  case "${value}" in
    localauthority|la)
      printf '%s\n' "LOCAL_AUTHORITY"
      ;;
    respondent)
      printf '%s\n' "RESPONDENT"
      ;;
    *)
      echo "Unknown applicant '${1}'. Use 'local authority' or 'respondent'." >&2
      exit 1
      ;;
  esac
}

normalise_application_type() {
  local value
  value="$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]' | tr -d '_-[:space:]')"
  case "${value}" in
    adjourn)
      printf '%s\n' "ADJOURN"
      ;;
    setaside)
      printf '%s\n' "SET_ASIDE"
      ;;
    somethingelse)
      printf '%s\n' "SOMETHING_ELSE"
      ;;
    *)
      echo "Unknown APPLICATION_TYPE '${1}'. Use adjourn, set aside, or something else." >&2
      exit 1
      ;;
  esac
}

normalise_yes_no() {
  local label="$1"
  local value
  value="$(printf '%s' "$2" | tr '[:upper:]' '[:lower:]' | tr -d '_-[:space:]')"
  case "${value}" in
    yes)
      printf '%s\n' "Yes"
      ;;
    no)
      printf '%s\n' "No"
      ;;
    *)
      echo "Unknown ${label} '${2}'. Use yes or no." >&2
      exit 1
      ;;
  esac
}

APPLICANT_CODE="$(normalise_applicant "${APPLICANT_RAW}")"
APPLICATION_TYPE_CODE="$(normalise_application_type "${APPLICATION_TYPE_RAW}")"
APPLIED_FOR_HWF="$(normalise_yes_no "APPLIED_FOR_HWF" "${APPLIED_FOR_HWF_RAW}")"
ALL_PARTIES_AGREE="$(normalise_yes_no "ALL_PARTIES_AGREE" "${ALL_PARTIES_AGREE_RAW}")"

if [[ -z "${DATE_RECEIVED:-}" ]]; then
  if DATE_RECEIVED="$(date -v-1d +%Y-%m-%d 2>/dev/null)"; then
    :
  else
    DATE_RECEIVED="$(date -d 'yesterday' +%Y-%m-%d)"
  fi
fi

if [[ ! "${DATE_RECEIVED}" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
  echo "DATE_RECEIVED must be YYYY-MM-DD: '${DATE_RECEIVED}'" >&2
  exit 1
fi

TODAY="$(date +%Y-%m-%d)"
if [[ ! "${DATE_RECEIVED}" < "${TODAY}" ]]; then
  echo "DATE_RECEIVED must be in the past: '${DATE_RECEIVED}'" >&2
  exit 1
fi

if [[ ! "${FEE_AMOUNT}" =~ ^[0-9]+$ ]] || (( FEE_AMOUNT > 999999 )); then
  echo "FEE_AMOUNT must be a whole number of pence from 0 to 999999: '${FEE_AMOUNT}'" >&2
  exit 1
fi

if [[ "${APPLICATION_TYPE_CODE}" == "SOMETHING_ELSE" && -z "${SOMETHING_ELSE_DETAILS}" ]]; then
  SOMETHING_ELSE_DETAILS="OOT refusal appeal"
fi

if [[ "${APPLICATION_TYPE_CODE}" == "ADJOURN" ]]; then
  if [[ -z "${WITHIN_14_DAYS_RAW}" ]]; then
    WITHIN_14_DAYS_RAW="yes"
  fi
  WITHIN_14_DAYS="$(normalise_yes_no "WITHIN_14_DAYS" "${WITHIN_14_DAYS_RAW}")"
fi

if [[ "${APPLIED_FOR_HWF}" == "Yes" && -z "${HWF_REFERENCE}" ]]; then
  echo "HWF_REFERENCE is required when APPLIED_FOR_HWF=yes." >&2
  exit 1
fi

if [[ "${ALL_PARTIES_AGREE}" == "No" ]]; then
  if [[ -z "${WITHOUT_NOTICE_RAW}" ]]; then
    WITHOUT_NOTICE_RAW="no"
  fi
  WITHOUT_NOTICE="$(normalise_yes_no "WITHOUT_NOTICE" "${WITHOUT_NOTICE_RAW}")"
fi

if [[ ! -f "${N244_PATH}" ]]; then
  echo "N244 file not found: ${N244_PATH}" >&2
  exit 1
fi

if ! curl --silent --fail --connect-timeout 1 "${DM_STORE_URL:-http://localhost:4506}/health" >/dev/null 2>&1; then
  echo "Local dm-store not reachable; starting ./bin/start-local-dm-store.sh..." >&2
  "${SCRIPT_DIR}/start-local-dm-store.sh"
fi

user_token="$(IDAM_USERNAME="${IDAM_USERNAME}" "${SCRIPT_DIR}/get-local-idam-token.sh")"
upload_service_token="$("${SCRIPT_DIR}/get-local-s2s-token.sh" xui_webapp)"
ccd_service_token="$("${SCRIPT_DIR}/get-local-s2s-token.sh" tec_api)"

FILENAME="$(basename -- "${N244_PATH}")"

echo "Uploading ${FILENAME} to Case Document AM..." >&2

upload_response="$({
  curl --silent --show-error --fail-with-body \
    --connect-timeout 5 \
    --max-time 120 \
    --request POST "${CDAM_URL}/cases/documents" \
    --header "Authorization: Bearer ${user_token}" \
    --header "ServiceAuthorization: ${upload_service_token}" \
    --form "classification=${CLASSIFICATION}" \
    --form "caseTypeId=${CASE_TYPE_ID}" \
    --form "jurisdictionId=${JURISDICTION_ID}" \
    --form "files=@${N244_PATH}"
} 2>&1)" || {
  echo "Failed to upload document to ${CDAM_URL}/cases/documents" >&2
  echo "${upload_response}" >&2
  exit 1
}

document_url="$(jq --raw-output '.documents[0]._links.self.href // empty' <<<"${upload_response}")"
document_binary_url="$(jq --raw-output '.documents[0]._links.binary.href // empty' <<<"${upload_response}")"
document_hash="$(jq --raw-output '.documents[0].hashToken // empty' <<<"${upload_response}")"
uploaded_filename="$(jq --raw-output '.documents[0].originalDocumentName // empty' <<<"${upload_response}")"

if [[ -z "${document_url}" || -z "${document_binary_url}" || -z "${document_hash}" ]]; then
  echo "Unexpected Case Document AM response; expected documents[0] with links and hashToken" >&2
  echo "${upload_response}" >&2
  exit 1
fi

to_cdam_document_url() {
  local url="$1"
  local document_id

  if [[ "${url}" == *"/cases/documents/"* ]]; then
    printf '%s\n' "${url}"
    return 0
  fi

  document_id="$(sed -E 's|.*/documents/([0-9a-fA-F-]{36}).*|\1|' <<<"${url}")"
  if [[ -z "${document_id}" || "${document_id}" == "${url}" ]]; then
    echo "Unable to derive Case Document AM URL from: ${url}" >&2
    exit 1
  fi

  if [[ "${url}" == *"/binary" ]]; then
    printf '%s\n' "${CDAM_URL}/cases/documents/${document_id}/binary"
  else
    printf '%s\n' "${CDAM_URL}/cases/documents/${document_id}"
  fi
}

document_url="$(to_cdam_document_url "${document_url}")"
document_binary_url="$(to_cdam_document_url "${document_binary_url}")"

if [[ -n "${uploaded_filename}" ]]; then
  FILENAME="${uploaded_filename}"
fi

echo "Submitting ${EVENT_ID} for case ${CASE_REFERENCE} as ${APPLICANT_CODE}..." >&2

event_trigger_url="${CCD_URL}/cases/${CASE_REFERENCE}/event-triggers/${EVENT_ID}"
start_response="$({
  curl --silent --show-error --fail-with-body \
    --connect-timeout 5 \
    --max-time 120 \
    --request GET "${event_trigger_url}" \
    --header "Authorization: Bearer ${user_token}" \
    --header "ServiceAuthorization: ${ccd_service_token}" \
    --header 'experimental: true'
} 2>&1)" || {
  echo "Failed to start ${EVENT_ID} for case ${CASE_REFERENCE}" >&2
  echo "${start_response}" >&2
  exit 1
}

event_token="$(jq --raw-output '.token // empty' <<<"${start_response}")"
if [[ -z "${event_token}" ]]; then
  echo "CCD start-event response did not contain a token" >&2
  echo "${start_response}" >&2
  exit 1
fi

event_data="$(jq --null-input --compact-output \
  --arg applicant "${APPLICANT_CODE}" \
  --arg dateReceived "${DATE_RECEIVED}" \
  --arg applicationType "${APPLICATION_TYPE_CODE}" \
  --arg feeAmount "${FEE_AMOUNT}" \
  --arg appliedForHwf "${APPLIED_FOR_HWF}" \
  --arg allPartiesAgree "${ALL_PARTIES_AGREE}" \
  --arg documentUrl "${document_url}" \
  --arg documentBinaryUrl "${document_binary_url}" \
  --arg documentFilename "${FILENAME}" \
  --arg documentHash "${document_hash}" \
  '{
    genAppApplicant: $applicant,
    genAppDateReceived: $dateReceived,
    genAppApplicationType: $applicationType,
    genAppFeeReceived: "Yes",
    genAppFeeAmountReceived: $feeAmount,
    genAppAppliedForHwf: $appliedForHwf,
    genAppAllPartiesAgree: $allPartiesAgree,
    genAppDocument: {
      document_url: $documentUrl,
      document_binary_url: $documentBinaryUrl,
      document_filename: $documentFilename,
      document_hash: $documentHash
    }
  }')"

if [[ "${APPLICATION_TYPE_CODE}" == "SOMETHING_ELSE" ]]; then
  event_data="$(jq --compact-output --arg details "${SOMETHING_ELSE_DETAILS}" \
    '. + {genAppSomethingElseDetails: $details}' <<<"${event_data}")"
fi

if [[ "${APPLICATION_TYPE_CODE}" == "ADJOURN" ]]; then
  event_data="$(jq --compact-output --arg within14Days "${WITHIN_14_DAYS}" \
    '. + {genAppWithin14Days: $within14Days}' <<<"${event_data}")"
fi

if [[ "${APPLIED_FOR_HWF}" == "Yes" ]]; then
  event_data="$(jq --compact-output --arg hwfReference "${HWF_REFERENCE}" \
    '. + {genAppHwfReference: $hwfReference}' <<<"${event_data}")"
fi

if [[ "${ALL_PARTIES_AGREE}" == "No" ]]; then
  event_data="$(jq --compact-output --arg withoutNotice "${WITHOUT_NOTICE}" \
    '. + {genAppWithoutNotice: $withoutNotice}' <<<"${event_data}")"
fi

submit_body="$(jq --null-input --compact-output \
  --arg eventId "${EVENT_ID}" \
  --arg eventToken "${event_token}" \
  --argjson data "${event_data}" \
  '{
    event: {
      id: $eventId,
      summary: "Enter a general application",
      description: "Enter N244 general application"
    },
    data: $data,
    event_token: $eventToken
  }')"

submit_response="$({
  curl --silent --show-error --fail-with-body \
    --connect-timeout 5 \
    --max-time 120 \
    --request POST "${CCD_URL}/cases/${CASE_REFERENCE}/events" \
    --header "Authorization: Bearer ${user_token}" \
    --header "ServiceAuthorization: ${ccd_service_token}" \
    --header 'Content-Type: application/json' \
    --header 'experimental: true' \
    --data "${submit_body}"
} 2>&1)" || {
  echo "Failed to submit ${EVENT_ID} for case ${CASE_REFERENCE}" >&2
  echo "${submit_response}" >&2
  exit 1
}

jq --null-input \
  --arg caseReference "${CASE_REFERENCE}" \
  --arg applicant "${APPLICANT_CODE}" \
  --arg applicationType "${APPLICATION_TYPE_CODE}" \
  --arg dateReceived "${DATE_RECEIVED}" \
  --arg pdfFilename "${FILENAME}" \
  --argjson generalApplication "${event_data}" \
  --argjson enterGeneralApplication "$(jq '.' <<<"${submit_response}")" \
  '{
    caseReference: $caseReference,
    applicant: $applicant,
    applicationType: $applicationType,
    dateReceived: $dateReceived,
    pdfFilename: $pdfFilename,
    generalApplication: $generalApplication,
    enterGeneralApplication: $enterGeneralApplication
  }'
