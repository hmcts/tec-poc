#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/authority_pcn_id.sh
source "${SCRIPT_DIR}/lib/authority_pcn_id.sh"
TEC_API_URL="${TEC_API_URL:-http://localhost:4013}"
CASE_COUNT="${CASE_COUNT:-20}"

usage() {
  cat <<EOF
Usage: ${0} <batch-case-reference|->
       ${0} -h|--help

Create ${CASE_COUNT} randomised TEC PCN cases through the local API
(${TEC_API_URL}/pcn-cases).

Arguments:
  <batch-case-reference>  Optional CCD case reference of a TEC_BATCH case to link
                          each PCN to after create (hyphens optional). The batch
                          must already exist or the script exits before creating
                          any PCNs. Use '-' to create without linking.
  -h, --help              Show this help and exit

If BATCH_CASE_REFERENCE is set in the environment and no argument is passed,
that value is used as the batch case reference.

Optional environment variables:
  TEC_API_URL, CASE_COUNT (default: 20), BATCH_CASE_REFERENCE, LOCAL_AUTHORITY
  (if LOCAL_AUTHORITY is set, every case uses it; otherwise authorities are randomised),
  BATCH_REGISTRATION_REASON, BATCH_REGISTRATION_REASON_CODE
  (reason defaults: CLRC007 / "Linked when creating the case during batch
  registration"; passed through to link-pcn-to-batch.sh when linking)

Examples:
  ${0} -                                    # create ${CASE_COUNT} unlinked PCNs
  CASE_COUNT=5 ${0} -                       # create 5 unlinked PCNs
  LOCAL_AUTHORITY=manchesterCityCouncil ${0} -
  ${0} 1234-5678-9012-3456                  # create and link each to batch
  BATCH_CASE_REFERENCE=1234567890123456 ${0}
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

# Optional: positional arg or BATCH_CASE_REFERENCE env — link each new PCN to a TEC_BATCH case.
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

if [[ -n "${BATCH_CASE_REFERENCE_RAW}" ]]; then
  echo "Verifying batch case ${BATCH_CASE_REFERENCE_RAW} exists..." >&2
  "${SCRIPT_DIR}/assert-batch-case-exists.sh" "${BATCH_CASE_REFERENCE_RAW}"
  echo "Each created PCN will be linked to batch case ${BATCH_CASE_REFERENCE_RAW}." >&2
fi
if [[ -n "${LOCAL_AUTHORITY:-}" ]]; then
  echo "Each created PCN will use local authority ${LOCAL_AUTHORITY}." >&2
fi


FIRST_NAMES=(
  "OLIVER" "AMELIA" "NOAH" "ISLA" "ARTHUR" "FREYA" "LEO" "AVA"
  "THEODORE" "MAISIE" "FELIX" "IMOGEN" "HUGO" "CLARA" "JASPER" "ELIZA"
)
LAST_NAMES=(
  "WHITFIELD" "HARTLEY" "PEMBERTON" "BLACKWOOD" "FAIRCHILD" "THORNE"
  "MERRIWEATHER" "ASHFORD" "COLLINGWOOD" "BRAITHWAITE" "LANGFORD"
  "WINTERS" "HOLLAND" "SINCLAIR" "BEAUMONT" "PRESCOTT"
)
STREETS=(
  "12 MAPLE GROVE" "45 CHURCH LANE" "8 HIGHFIELD ROAD" "27 VICTORIA STREET"
  "3 THE ORCHARD" "19 STATION APPROACH" "6 WILLOW CLOSE" "31 PARK VIEW"
  "14 CHESTNUT AVENUE" "22 MILL ROAD" "9 BROOKSIDE" "17 ELM COURT"
  "5 RIVERSIDE WALK" "38 QUEENS ROAD" "11 GARDEN CLOSE" "24 NORTH STREET"
)
CITIES=(
  "MANCHESTER" "BIRMINGHAM" "BRISTOL" "LEEDS" "SHEFFIELD" "NOTTINGHAM"
  "NEWCASTLE" "LEICESTER" "SOUTHAMPTON" "READING" "BRIGHTON" "YORK"
  "CAMBRIDGE" "OXFORD" "EXETER" "NORWICH"
)
POSTCODES=(
  "M1 4BT" "B33 8TH" "BS1 5TR" "LS2 7EW" "S1 2HE" "NG1 5FS"
  "NE1 4LP" "LE1 6TP" "SO14 3GS" "RG1 8DS" "BN1 4GW" "YO1 7HH"
  "CB2 1TN" "OX1 3BH" "EX4 3LS" "NR2 1NH"
)
NATURE_OF_OFFENCE_CODES=(01 02 03 04 05 06 07 08 09 10 11 12)

# Only authorities with a TEC id, so each PCN prefix matches the case's local authority.
LOCAL_AUTHORITIES=()
while IFS= read -r line || [[ -n "${line}" ]]; do
  [[ -z "${line}" || "${line}" == \#* ]] && continue
  LOCAL_AUTHORITIES+=("${line%% *}")
done < "${SCRIPT_DIR}/lib/local_authority_pcn_ids.txt"

random_from() {
  local items=("$@")
  echo "${items[RANDOM % ${#items[@]}]}"
}

random_vehicle_registration() {
  local letters=(A B C D E F G H J K L M N P R S T V W X Y Z)
  printf '%s%s%02d%s%s%s' \
    "${letters[RANDOM % ${#letters[@]}]}" \
    "${letters[RANDOM % ${#letters[@]}]}" \
    "$((RANDOM % 100))" \
    "${letters[RANDOM % ${#letters[@]}]}" \
    "${letters[RANDOM % ${#letters[@]}]}" \
    "${letters[RANDOM % ${#letters[@]}]}"
}

random_charge_certificate_date() {
  local day=$((1 + RANDOM % 28))
  local month=$((1 + RANDOM % 12))
  local year=$((24 + RANDOM % 2))
  printf '%02d%02d%02d' "${day}" "${month}" "${year}"
}

random_amount_due() {
  echo "$((5000 + (RANDOM % 46) * 1000 + (RANDOM % 10) * 100 + (RANDOM % 4) * 25))"
}

build_case_payload() {
  local index="$1"
  local sequence_base="$2"
  local authority_code
  local file_number
  local batch_number
  local penalty_charge_number
  local file_identifier
  local batch_identifier
  local respondent_name
  local amount_due
  local local_authority

  local_authority="${LOCAL_AUTHORITY:-$(random_from "${LOCAL_AUTHORITIES[@]}")}"
  authority_code="$(authority_pcn_id "${local_authority}")"
  file_number="$(printf '%05d' "$(((sequence_base + index * 17) % 100000))")"
  batch_number="$(printf '%06d' "$(((sequence_base + index * 37) % 1000000))")"
  penalty_charge_number="$("${SCRIPT_DIR}/generate-pcn-number.sh" "${authority_code}")"
  file_identifier="R${authority_code}${file_number}"
  batch_identifier="R${authority_code}${batch_number}"
  respondent_name="$(random_from "${FIRST_NAMES[@]}") $(random_from "${LAST_NAMES[@]}")"
  amount_due="$(random_amount_due)"

  jq --null-input --compact-output \
    --arg fileIdentifier "${file_identifier}" \
    --arg batchIdentifier "${batch_identifier}" \
    --arg penaltyChargeNumber "${penalty_charge_number}" \
    --arg localAuthority "${local_authority}" \
    --arg respondentDetails1 "${respondent_name}" \
    --arg respondentDetails2 "$(random_from "${STREETS[@]}")" \
    --arg respondentDetails3 "$(random_from "${CITIES[@]}")" \
    --arg respondentDetails4 "$(random_from "${POSTCODES[@]}")" \
    --arg vehicleRegistrationNumber "$(random_vehicle_registration)" \
    --arg natureOfOffence "$(random_from "${NATURE_OF_OFFENCE_CODES[@]}")" \
    --arg dateChargeCertificateServed "$(random_charge_certificate_date)" \
    --argjson amountDue "${amount_due}" \
    '{
      fileIdentifier: $fileIdentifier,
      batchIdentifier: $batchIdentifier,
      penaltyChargeNumber: $penaltyChargeNumber,
      localAuthority: $localAuthority,
      respondentDetails1: $respondentDetails1,
      respondentDetails2: $respondentDetails2,
      respondentDetails3: $respondentDetails3,
      respondentDetails4: $respondentDetails4,
      vehicleRegistrationNumber: $vehicleRegistrationNumber,
      natureOfOffence: $natureOfOffence,
      dateChargeCertificateServed: $dateChargeCertificateServed,
      amountDue: $amountDue
    }'
}

token="$("${SCRIPT_DIR}/get-local-idam-token.sh")"
sequence_base=$(($(date +%s) ^ $$))

echo "Creating ${CASE_COUNT} TEC cases through ${TEC_API_URL}/pcn-cases..." >&2

created=0
for index in $(seq 0 $((CASE_COUNT - 1))); do
  case_data="$(build_case_payload "${index}" "${sequence_base}")"
  penalty_charge_number="$(jq --raw-output '.penaltyChargeNumber' <<<"${case_data}")"
  respondent_name="$(jq --raw-output '.respondentDetails1' <<<"${case_data}")"

  response="$({
    curl --silent --show-error --fail-with-body \
      --connect-timeout 5 \
      --max-time 120 \
      --request POST "${TEC_API_URL}/pcn-cases" \
      --header "Authorization: Bearer ${token}" \
      --header 'Content-Type: application/json' \
      --data "${case_data}"
  } 2>&1)" || {
    echo "Failed to create case $((index + 1))/${CASE_COUNT} (${penalty_charge_number}, ${respondent_name})" >&2
    echo "${response}" >&2
    exit 1
  }

  created=$((created + 1))
  jq --arg penaltyChargeNumber "${penalty_charge_number}" \
    --arg respondentDetails1 "${respondent_name}" \
    '. + {
      penaltyChargeNumber: $penaltyChargeNumber,
      respondentDetails1: $respondentDetails1
    }' <<<"${response}"

  if [[ -n "${BATCH_CASE_REFERENCE_RAW}" ]]; then
    case_reference="$(jq --raw-output '.caseReference // empty' <<<"${response}")"
    if [[ -z "${case_reference}" ]]; then
      echo "Created case response did not include caseReference; cannot link to batch" >&2
      exit 1
    fi
    echo "Linking PCN case ${case_reference} to batch case ${BATCH_CASE_REFERENCE_RAW}..." >&2
    "${SCRIPT_DIR}/link-pcn-to-batch.sh" "${case_reference}" "${BATCH_CASE_REFERENCE_RAW}" >/dev/null
  fi
done

echo "Created ${created} TEC cases." >&2
