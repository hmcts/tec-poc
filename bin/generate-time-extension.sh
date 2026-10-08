#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

CCD_URL="${CCD_DATA_STORE_URL:-http://localhost:4452}"
EVENT_ID="${EVENT_ID:-recordTimeExtension}"
TEMPLATES_DIR="${SCRIPT_DIR}/templates"
PYTHON_HELPER="${SCRIPT_DIR}/lib/generate_time_extension.py"
PYTHON_BIN="python3"

CASE_REFERENCE_RAW="${1:-}"
FORM_RAW="${2:-}"

for command in curl jq python3; do
  if ! command -v "${command}" >/dev/null 2>&1; then
    echo "Required command not found: ${command}" >&2
    exit 1
  fi
done

usage() {
  cat <<EOF
Usage: ${0} <case-reference> <form>

Generate a TE7 or PE2 time-extension request for a TEC case:
  - look up case details from CCD
  - populate time-extension fields from the case (plus random remaining fields)
  - submit the recordTimeExtension event
  - fill the PDF template and attach it under Case File View → Applications

<form>  TE7 | PE2

Optional environment variables:
  CCD_DATA_STORE_URL, SEED
  FORM_VALIDATION_RESULT   replaces the default "Form valid" message stored on the form.
                           Examples:
                             Invalid - application is for more time, expecting application to file out of time
                             Invalid - name does not match registration
                             Invalid - fields missing
                             Invalid - application not signed
EOF
}

if [[ -z "${CASE_REFERENCE_RAW}" || -z "${FORM_RAW}" ]]; then
  usage >&2
  exit 1
fi

CASE_REFERENCE="$(printf '%s' "${CASE_REFERENCE_RAW}" | tr -d '-')"
if [[ ! "${CASE_REFERENCE}" =~ ^[0-9]+$ ]]; then
  echo "Case reference must contain digits (hyphens optional): '${CASE_REFERENCE_RAW}'" >&2
  exit 1
fi

normalise_form() {
  local value
  value="$(printf '%s' "$1" | tr '[:lower:]' '[:upper:]')"
  case "${value}" in
    TE7|PE2)
      printf '%s\n' "${value}"
      ;;
    *)
      echo "Unknown form '${1}'. Use TE7 or PE2." >&2
      exit 1
      ;;
  esac
}

FORM_CODE="$(normalise_form "${FORM_RAW}")"

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

WORKDIR="$(mktemp -d "${TMPDIR:-/tmp}/tec-generate-time-extension.XXXXXX")"
cleanup() {
  rm -rf "${WORKDIR}"
}
trap cleanup EXIT

CASE_JSON_PATH="${WORKDIR}/case.json"
PAYLOAD_PATH="${WORKDIR}/payload.json"
PDF_PATH="${WORKDIR}/time-extension.pdf"

printf '%s\n' "${case_response}" >"${CASE_JSON_PATH}"

PYTHON_ARGS=(
  "${PYTHON_HELPER}"
  --case-json "${CASE_JSON_PATH}"
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

echo "Building time-extension payload and filling ${FORM_CODE} PDF..." >&2
"${PYTHON_BIN}" "${PYTHON_ARGS[@]}" >/dev/null

if [[ ! -f "${PAYLOAD_PATH}" || ! -f "${PDF_PATH}" ]]; then
  echo "Python helper did not produce payload/PDF outputs" >&2
  exit 1
fi

if [[ "${FORM_CODE}" == "TE7" ]]; then
  PERMISSION="$(jq --raw-output '.timeExtensionPermissionType // empty' "${PAYLOAD_PATH}")"
  case "${PERMISSION}" in
    forMoreTime)
      SECTION_LABEL="Application for extension of time"
      ;;
    *)
      SECTION_LABEL="Application to file out of time"
      ;;
  esac
else
  SECTION_LABEL="Application to file out of time"
fi

registration_pcn="$(jq --raw-output '.data.penaltyChargeNumber // empty' "${CASE_JSON_PATH}")"
if [[ -z "${registration_pcn}" ]]; then
  echo "Case ${CASE_REFERENCE} has no registration penalty charge number" >&2
  exit 1
fi
ATTACH_PDF_PATH="${WORKDIR}/${SECTION_LABEL} - ${registration_pcn}.pdf"
mv "${PDF_PATH}" "${ATTACH_PDF_PATH}"
PDF_PATH="${ATTACH_PDF_PATH}"

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
      summary: "Record time extension",
      description: "Record TE7/PE2 time extension request data"
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

PDF_BASENAME="$(basename -- "${PDF_PATH}")"
jq --null-input \
  --arg caseReference "${CASE_REFERENCE}" \
  --arg form "${FORM_CODE}" \
  --arg pdfFilename "${PDF_BASENAME}" \
  --argjson payload "$(cat "${PAYLOAD_PATH}")" \
  --argjson recordTimeExtension "$(jq '.' <<<"${submit_response}")" \
  '{
    caseReference: $caseReference,
    form: $form,
    pdfFilename: $pdfFilename,
    timeExtension: $payload,
    recordTimeExtension: $recordTimeExtension
  }'

echo >&2
echo "Attached document response:" >&2
jq . <<<"${ATTACH_RESPONSE}" >&2 || printf '%s\n' "${ATTACH_RESPONSE}" >&2
