#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

CCD_URL="${CCD_DATA_STORE_URL:-http://localhost:4452}"
CDAM_URL="${CASE_DOCUMENT_AM_URL:-http://localhost:4455}"
EVENT_ID="${EVENT_ID:-attachCaseFileDocument}"
CASE_TYPE_ID="${CASE_TYPE_ID:-TEC}"
JURISDICTION_ID="${JURISDICTION_ID:-TEC}"
CLASSIFICATION="${DOCUMENT_CLASSIFICATION:-PUBLIC}"

CASE_REFERENCE="${1:-}"
FOLDER="${2:-}"
FILE_PATH="${3:-}"

for command in curl jq; do
  if ! command -v "${command}" >/dev/null 2>&1; then
    echo "Required command not found: ${command}" >&2
    exit 1
  fi
done

usage() {
  cat <<EOF
Usage: ${0} <case-reference> <folder> <file-path>

Attach a file to a TEC case so it appears in the Case File View folder.

<folder> may be a category id or label:
  hearingDocuments ("Hearing documents")
  ordersAndNoticesOfHearings ("Orders and notices of hearings")
  applications ("Applications")
  correspondence ("Correspondence")
  uncategorisedDocuments ("Uncategorised")

Optional environment variables:
  CCD_DATA_STORE_URL, CASE_DOCUMENT_AM_URL, DOCUMENT_CLASSIFICATION
EOF
}

if [[ -z "${CASE_REFERENCE}" || -z "${FOLDER}" || -z "${FILE_PATH}" ]]; then
  usage >&2
  exit 1
fi

if [[ ! -f "${FILE_PATH}" ]]; then
  echo "File not found: ${FILE_PATH}" >&2
  exit 1
fi

resolve_category_id() {
  local folder="$1"
  local normalised
  normalised="$(printf '%s' "${folder}" | tr '[:upper:]' '[:lower:]')"

  case "${normalised}" in
    hearingdocuments|"hearing documents")
      printf '%s\n' "hearingDocuments"
      ;;
    ordersandnoticesofhearings|"orders and notices of hearings")
      printf '%s\n' "ordersAndNoticesOfHearings"
      ;;
    applications)
      printf '%s\n' "applications"
      ;;
    correspondence)
      printf '%s\n' "correspondence"
      ;;
    uncategoriseddocuments|uncategorised|"uncategorised documents")
      printf '%s\n' "uncategorisedDocuments"
      ;;
    *)
      echo "Unknown Case File View folder: '${folder}'" >&2
      usage >&2
      exit 1
      ;;
  esac
}

CATEGORY_ID="$(resolve_category_id "${FOLDER}")"
FILENAME="$(basename -- "${FILE_PATH}")"

# Ensure the local dm-store stub is reachable (CDAM proxies uploads to :4506).
if ! curl --silent --fail --connect-timeout 1 "${DM_STORE_URL:-http://localhost:4506}/health" >/dev/null 2>&1; then
  echo "Local dm-store not reachable; starting ./bin/start-local-dm-store.sh..." >&2
  "${SCRIPT_DIR}/start-local-dm-store.sh"
fi

user_token="$("${SCRIPT_DIR}/get-local-idam-token.sh")"
upload_service_token="$("${SCRIPT_DIR}/get-local-s2s-token.sh" xui_webapp)"
ccd_service_token="$("${SCRIPT_DIR}/get-local-s2s-token.sh" tec_api)"

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
    --form "files=@${FILE_PATH}"
} 2>&1)" || {
  echo "Failed to upload document to ${CDAM_URL}/cases/documents" >&2
  echo "${upload_response}" >&2
  echo >&2
  echo "If CDAM cannot reach dm-store, set DM_STORE_BASE_URL for the local stack" >&2
  echo "or start a reachable document store, then retry." >&2
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

# Local CDAM often returns raw dm-store links (":4506/documents/..."). CCD validates
# document fields against the Case Document AM path ("/cases/documents/..."), so rewrite.
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

echo "Submitting ${EVENT_ID} for case ${CASE_REFERENCE} into folder ${CATEGORY_ID}..." >&2

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

submit_body="$(jq --null-input --compact-output \
  --arg eventId "${EVENT_ID}" \
  --arg eventToken "${event_token}" \
  --arg documentUrl "${document_url}" \
  --arg documentBinaryUrl "${document_binary_url}" \
  --arg documentFilename "${FILENAME}" \
  --arg documentHash "${document_hash}" \
  --arg categoryId "${CATEGORY_ID}" \
  '{
    event: {
      id: $eventId,
      summary: "Attach case file document",
      description: "Attach case file document"
    },
    data: {
      caseFileDocument: {
        document_url: $documentUrl,
        document_binary_url: $documentBinaryUrl,
        document_filename: $documentFilename,
        document_hash: $documentHash,
        category_id: $categoryId
      }
    },
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

echo "${submit_response}" | jq .

categories_response="$({
  curl --silent --show-error --fail-with-body \
    --connect-timeout 5 \
    --max-time 60 \
    --request GET "${CCD_URL}/categoriesAndDocuments/${CASE_REFERENCE}" \
    --header "Authorization: Bearer ${user_token}" \
    --header "ServiceAuthorization: ${ccd_service_token}" \
    --header 'experimental: true'
} 2>&1)" || {
  echo "Attached document, but failed to fetch categoriesAndDocuments for verification" >&2
  echo "${categories_response}" >&2
  exit 0
}

echo >&2
echo "Case File View categories and documents:" >&2
echo "${categories_response}" | jq .
