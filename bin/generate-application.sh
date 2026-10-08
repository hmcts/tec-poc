#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

CCD_URL="${CCD_DATA_STORE_URL:-http://localhost:4452}"
EVENT_ID="${EVENT_ID:-recordApplication}"
TEMPLATES_DIR="${SCRIPT_DIR}/templates"
PYTHON_HELPER="${SCRIPT_DIR}/lib/generate_application.py"
PYTHON_BIN="python3"

CASE_REFERENCE_RAW="${1:-}"
TYPE_RAW="${2:-}"
FORM_RAW="${3:-}"

for command in curl jq python3; do
  if ! command -v "${command}" >/dev/null 2>&1; then
    echo "Required command not found: ${command}" >&2
    exit 1
  fi
done

usage() {
  cat <<EOF
Usage: ${0} <case-reference> <type> <form>

Generate a TE9 or PE3 application for a TEC case:
  - look up case details from CCD
  - populate application fields from the case (plus random remaining fields)
  - submit the recordApplication event
  - fill the PDF template and attach it under Case File View → Applications

<type>  in time | out of time  (also in-time, out-of-time, inTime, outOfTime)
<form>  TE9 | PE3

Optional environment variables:
  CCD_DATA_STORE_URL, SEED
  FORM_VALIDATION_RESULT   replaces the default "Form valid" message stored on the form.
                           Examples:
                             Invalid - name does not match registration
                             Invalid - fields missing
                             Invalid - application not signed
EOF
}

if [[ -z "${CASE_REFERENCE_RAW}" || -z "${TYPE_RAW}" || -z "${FORM_RAW}" ]]; then
  usage >&2
  exit 1
fi

CASE_REFERENCE="$(printf '%s' "${CASE_REFERENCE_RAW}" | tr -d '-')"
if [[ ! "${CASE_REFERENCE}" =~ ^[0-9]+$ ]]; then
  echo "Case reference must contain digits (hyphens optional): '${CASE_REFERENCE_RAW}'" >&2
  exit 1
fi

normalise_type() {
  local value
  value="$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]' | tr -d '_-[:space:]')"
  case "${value}" in
    intime)
      printf '%s\n' "inTime"
      ;;
    outoftime)
      printf '%s\n' "outOfTime"
      ;;
    *)
      echo "Unknown type '${1}'. Use 'in time' or 'out of time'." >&2
      exit 1
      ;;
  esac
}

normalise_form() {
  local value
  value="$(printf '%s' "$1" | tr '[:lower:]' '[:upper:]')"
  case "${value}" in
    TE9|PE3)
      printf '%s\n' "${value}"
      ;;
    *)
      echo "Unknown form '${1}'. Use TE9 or PE3." >&2
      exit 1
      ;;
  esac
}

TYPE_CODE="$(normalise_type "${TYPE_RAW}")"
FORM_CODE="$(normalise_form "${FORM_RAW}")"

if [[ "${TYPE_CODE}" == "inTime" ]]; then
  TYPE_LABEL="In time"
else
  TYPE_LABEL="Out of time"
fi

if [[ "${FORM_CODE}" == "TE9" ]]; then
  SECTION_LABEL="Witness statement - ${TYPE_LABEL}"
else
  SECTION_LABEL="Statutory declaration - ${TYPE_LABEL}"
fi

TEMPLATE_PATH="${TEMPLATES_DIR}/${FORM_CODE}.pdf"
if [[ ! -f "${TEMPLATE_PATH}" ]]; then
  echo "Template not found: ${TEMPLATE_PATH}" >&2
  exit 1
fi

ensure_python_deps() {
  local venv_dir="${SCRIPT_DIR}/.venv-generate-application"
  local python_bin="${venv_dir}/bin/python"

  if [[ -x "${python_bin}" ]] && "${python_bin}" -c 'import pypdf, reportlab' >/dev/null 2>&1; then
    PYTHON_BIN="${python_bin}"
    return 0
  fi

  echo "Preparing local Python venv for PDF filling (pypdf, reportlab)..." >&2
  python3 -m venv "${venv_dir}"
  "${venv_dir}/bin/python" -m pip install --quiet --upgrade pip
  "${venv_dir}/bin/python" -m pip install --quiet pypdf reportlab
  "${venv_dir}/bin/python" -c 'import pypdf, reportlab' >/dev/null 2>&1 || {
    echo "Failed to install pypdf/reportlab into ${venv_dir}" >&2
    exit 1
  }
  PYTHON_BIN="${python_bin}"
}

ensure_python_deps

user_token="$("${SCRIPT_DIR}/get-local-idam-token.sh")"
service_token="$("${SCRIPT_DIR}/get-local-s2s-token.sh" tec_api)"

echo "Fetching case ${CASE_REFERENCE} from CCD..." >&2
case_response="$({
  curl --silent --show-error --fail-with-body \
    --connect-timeout 5 \
    --max-time 60 \
    --request GET "${CCD_URL}/cases/${CASE_REFERENCE}" \
    --header "Authorization: Bearer ${user_token}" \
    --header "ServiceAuthorization: ${service_token}" \
    --header 'experimental: true'
} 2>&1)" || {
  echo "Failed to fetch case ${CASE_REFERENCE} from ${CCD_URL}" >&2
  echo "${case_response}" >&2
  exit 1
}

if ! jq --exit-status '.data' >/dev/null <<<"${case_response}"; then
  echo "CCD response for case ${CASE_REFERENCE} did not contain data" >&2
  echo "${case_response}" | jq . >&2 || echo "${case_response}" >&2
  exit 1
fi

WORKDIR="$(mktemp -d "${TMPDIR:-/tmp}/tec-generate-application.XXXXXX")"
cleanup() {
  rm -rf "${WORKDIR}"
}
trap cleanup EXIT

CASE_JSON_PATH="${WORKDIR}/case.json"
PAYLOAD_PATH="${WORKDIR}/payload.json"
registration_pcn="$(jq --raw-output '.data.penaltyChargeNumber // empty' <<<"${case_response}")"
if [[ -z "${registration_pcn}" ]]; then
  echo "Case ${CASE_REFERENCE} has no registration penalty charge number" >&2
  exit 1
fi
PDF_BASENAME="${SECTION_LABEL} - ${registration_pcn}.pdf"
PDF_PATH="${WORKDIR}/${PDF_BASENAME}"

printf '%s\n' "${case_response}" >"${CASE_JSON_PATH}"

PYTHON_ARGS=(
  "${PYTHON_HELPER}"
  --case-json "${CASE_JSON_PATH}"
  --type "${TYPE_CODE}"
  --form "${FORM_CODE}"
  --template "${TEMPLATE_PATH}"
  --out-pdf "${PDF_PATH}"
  --out-payload "${PAYLOAD_PATH}"
)
if [[ -n "${SEED:-}" ]]; then
  PYTHON_ARGS+=(--seed "${SEED}")
fi
if [[ -n "${FORM_VALIDATION_RESULT:-}" ]]; then
  PYTHON_ARGS+=(--form-validation-result "${FORM_VALIDATION_RESULT}")
fi

echo "Building application payload and filling ${FORM_CODE} PDF..." >&2
"${PYTHON_BIN}" "${PYTHON_ARGS[@]}" >/dev/null

if [[ ! -f "${PAYLOAD_PATH}" || ! -f "${PDF_PATH}" ]]; then
  echo "Python helper did not produce payload/PDF outputs" >&2
  exit 1
fi

echo "Submitting ${EVENT_ID} for case ${CASE_REFERENCE}..." >&2

event_trigger_url="${CCD_URL}/cases/${CASE_REFERENCE}/event-triggers/${EVENT_ID}"
start_response="$({
  curl --silent --show-error --fail-with-body \
    --connect-timeout 5 \
    --max-time 120 \
    --request GET "${event_trigger_url}" \
    --header "Authorization: Bearer ${user_token}" \
    --header "ServiceAuthorization: ${service_token}" \
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
  --argjson data "$(cat "${PAYLOAD_PATH}")" \
  '{
    event: {
      id: $eventId,
      summary: "Record application",
      description: "Record OCR application data"
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
    --header "ServiceAuthorization: ${service_token}" \
    --header 'Content-Type: application/json' \
    --header 'experimental: true' \
    --data "${submit_body}"
} 2>&1)" || {
  echo "Failed to submit ${EVENT_ID} for case ${CASE_REFERENCE}" >&2
  echo "${submit_response}" >&2
  exit 1
}

echo "Attaching filled PDF to Case File View → Applications..." >&2
ATTACH_RESPONSE="$("${SCRIPT_DIR}/attach-case-file-document.sh" \
  "${CASE_REFERENCE}" \
  "Applications" \
  "${PDF_PATH}")"

jq --null-input \
  --arg caseReference "${CASE_REFERENCE}" \
  --arg type "${TYPE_CODE}" \
  --arg form "${FORM_CODE}" \
  --arg pdfFilename "${PDF_BASENAME}" \
  --argjson payload "$(cat "${PAYLOAD_PATH}")" \
  --argjson recordApplication "$(jq '.' <<<"${submit_response}")" \
  '{
    caseReference: $caseReference,
    type: $type,
    form: $form,
    pdfFilename: $pdfFilename,
    application: $payload,
    recordApplication: $recordApplication
  }'

echo >&2
echo "Attached document response:" >&2
jq . <<<"${ATTACH_RESPONSE}" >&2 || printf '%s\n' "${ATTACH_RESPONSE}" >&2
