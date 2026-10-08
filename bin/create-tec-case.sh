#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/authority_pcn_id.sh
source "${SCRIPT_DIR}/lib/authority_pcn_id.sh"
TEC_API_URL="${TEC_API_URL:-http://localhost:4013}"

usage() {
  cat <<EOF
Usage: ${0} <batch-case-reference|->
       ${0} -h|--help

Create a TEC PCN case through the local API (${TEC_API_URL}/pcn-cases).

Arguments:
  <batch-case-reference>  Optional CCD case reference of a TEC_BATCH case to link
                          after create (hyphens optional). The batch must already
                          exist or the script exits before creating the PCN.
                          Use '-' to create without linking.
  -h, --help              Show this help and exit

If BATCH_CASE_REFERENCE is set in the environment and no argument is passed,
that value is used as the batch case reference.

Optional environment variables:
  TEC_API_URL, BATCH_CASE_REFERENCE, AMOUNT_DUE, FILE_IDENTIFIER,
  BATCH_IDENTIFIER, PENALTY_CHARGE_NUMBER, LOCAL_AUTHORITY,
  BATCH_REGISTRATION_REASON, BATCH_REGISTRATION_REASON_CODE
  (reason defaults: CLRC007 / "Linked when creating the case during batch
  registration"; passed through to link-pcn-to-batch.sh when linking)

The PCN is generated with generate-pcn-number.sh from LOCAL_AUTHORITY's TEC
id (default: westminster, id WE). File and batch identifiers use that same
id. Set PENALTY_CHARGE_NUMBER to supply a PCN instead; file and batch
identifiers then use that PCN's authority id unless overridden.

Examples:
  ${0} -                                    # create unlinked PCN
  ${0} 1234-5678-9012-3456                  # create and link to batch
  BATCH_CASE_REFERENCE=1234567890123456 ${0}
  LOCAL_AUTHORITY=manchesterCityCouncil ${0} -
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

# Optional: positional arg or BATCH_CASE_REFERENCE env — link the new PCN to a TEC_BATCH case.
# Pass '-' to create without linking. With no args and no env, show help.
if [[ $# -eq 0 ]]; then
  if [[ -n "${BATCH_CASE_REFERENCE:-}" ]]; then
    BATCH_CASE_REFERENCE_RAW="${BATCH_CASE_REFERENCE}"
  else
    usage >&2
    exit 1
  fi
elif [[ "${1}" == "-" ]]; then
  BATCH_CASE_REFERENCE_RAW=""
else
  BATCH_CASE_REFERENCE_RAW="${1}"
fi

for command in curl jq; do
  if ! command -v "${command}" >/dev/null 2>&1; then
    echo "Required command not found: ${command}" >&2
    exit 1
  fi
done

if [[ -n "${BATCH_CASE_REFERENCE_RAW}" && -z "${SKIP_BATCH_CASE_ASSERT:-}" ]]; then
  echo "Verifying batch case ${BATCH_CASE_REFERENCE_RAW} exists..." >&2
  "${SCRIPT_DIR}/assert-batch-case-exists.sh" "${BATCH_CASE_REFERENCE_RAW}"
fi

local_authority="${LOCAL_AUTHORITY:-westminster}"
case_seed=$(($(date +%s) ^ $$ ^ RANDOM))
file_number="$(printf '%05d' "$((case_seed % 100000))")"
batch_number="$(printf '%06d' "$(((case_seed * 37) % 1000000))")"

if [[ -n "${PENALTY_CHARGE_NUMBER:-}" ]]; then
  penalty_charge_number="${PENALTY_CHARGE_NUMBER}"
  if [[ ! "${penalty_charge_number}" =~ ^([A-Z]{2,3})[0-9]{7}[0-9A][0-9]$ ]]; then
    echo "PENALTY_CHARGE_NUMBER must be an authority id, 7 digits, a check digit, and a suffix" >&2
    exit 1
  fi
  authority_code="${BASH_REMATCH[1]}"
else
  authority_code="$(authority_pcn_id "${local_authority}")"
  penalty_charge_number="$("${SCRIPT_DIR}/generate-pcn-number.sh" "${authority_code}")"
fi

file_identifier="${FILE_IDENTIFIER:-R${authority_code}${file_number}}"
batch_identifier="${BATCH_IDENTIFIER:-R${authority_code}${batch_number}}"
amount_due="${AMOUNT_DUE:-12345}"

case_data="$(jq --null-input --compact-output \
  --arg fileIdentifier "${file_identifier}" \
  --arg batchIdentifier "${batch_identifier}" \
  --arg penaltyChargeNumber "${penalty_charge_number}" \
  --arg localAuthority "${local_authority}" \
  --argjson amountDue "${amount_due}" \
  '{
    fileIdentifier: $fileIdentifier,
    batchIdentifier: $batchIdentifier,
    penaltyChargeNumber: $penaltyChargeNumber,
    localAuthority: $localAuthority,
    respondentDetails1: "ALEX EXAMPLE",
    respondentDetails2: "1 EXAMPLE STREET",
    respondentDetails3: "LONDON",
    respondentDetails4: "SW1A 1AA",
    vehicleRegistrationNumber: "AB12CDE",
    natureOfOffence: "01",
    dateChargeCertificateServed: "260824",
    amountDue: $amountDue
  }')"

token="$("${SCRIPT_DIR}/get-local-idam-token.sh")"

response="$({
  curl --silent --show-error --fail-with-body \
    --connect-timeout 5 \
    --max-time 120 \
    --request POST "${TEC_API_URL}/pcn-cases" \
    --header "Authorization: Bearer ${token}" \
    --header 'Content-Type: application/json' \
    --data "${case_data}"
} 2>&1)" || {
  echo "Failed to create a TEC case through ${TEC_API_URL}/pcn-cases" >&2
  echo "${response}" >&2
  exit 1
}

jq . <<<"${response}"

if [[ -n "${BATCH_CASE_REFERENCE_RAW}" ]]; then
  case_reference="$(jq --raw-output '.caseReference // empty' <<<"${response}")"
  if [[ -z "${case_reference}" ]]; then
    echo "Created case response did not include caseReference; cannot link to batch" >&2
    exit 1
  fi
  echo "Linking PCN case ${case_reference} to batch case ${BATCH_CASE_REFERENCE_RAW}..." >&2
  "${SCRIPT_DIR}/link-pcn-to-batch.sh" "${case_reference}" "${BATCH_CASE_REFERENCE_RAW}" >/dev/null
fi
