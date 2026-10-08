#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

CCD_URL="${CCD_DATA_STORE_URL:-http://localhost:4452}"
EVENT_ID="${EVENT_ID:-addRegistration}"
CASE_REFERENCE_RAW="${1:-}"

for command in curl jq; do
  if ! command -v "${command}" >/dev/null 2>&1; then
    echo "Required command not found: ${command}" >&2
    exit 1
  fi
done

usage() {
  cat <<EOF
Usage: ${0} <case-reference>

Add a registration to an existing TEC PCN case:
  1. Read the case PCN stem and the current registration PCN
  2. Create a registration TEC_BATCH (PCN_COUNT=1) for the case's local authority
  3. Submit addRegistration with the next PCN suffix
  4. Link that batch to the case (Linked Cases and the new registration's Batch case)

The new PCN is the stem plus one more than the current registration suffix.
Suffix 9 cannot be incremented. Respondent details 1–6, local authority, vehicle,
offence, certificate date, and amount are copied from the case. File and batch
identifiers come from the new batch. Payment starts PENDING. Case-list fields
on the case are not updated.

Hyphens in the case reference are optional.

Optional environment variables:
  CCD_DATA_STORE_URL, EVENT_ID
EOF
}

if [[ -z "${CASE_REFERENCE_RAW}" \
  || "${CASE_REFERENCE_RAW}" == "-h" || "${CASE_REFERENCE_RAW}" == "--help" ]]; then
  usage >&2
  exit 1
fi

CASE_REFERENCE="$(printf '%s' "${CASE_REFERENCE_RAW}" | tr -d '-')"
if [[ ! "${CASE_REFERENCE}" =~ ^[0-9]+$ ]]; then
  echo "Case reference must contain digits (hyphens optional): '${CASE_REFERENCE_RAW}'" >&2
  exit 1
fi

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

pcn_stem="$(jq --raw-output '.data.pcnStem // empty' <<<"${case_response}")"
current_pcn="$(jq --raw-output '.data.penaltyChargeNumber // empty' <<<"${case_response}")"
local_authority="$(jq --raw-output '.data.localAuthority // empty' <<<"${case_response}")"

if [[ -z "${pcn_stem}" || ! "${pcn_stem}" =~ ^[A-Z]{2,3}[0-9]{7}[0-9A]$ ]]; then
  echo "Case ${CASE_REFERENCE} has no PCN stem" >&2
  exit 1
fi
if [[ -z "${current_pcn}" || ! "${current_pcn}" =~ ^[A-Z]{2,3}[0-9]{7}[0-9A][0-9]$ ]]; then
  echo "Case ${CASE_REFERENCE} has no current registration penalty charge number" >&2
  exit 1
fi
if [[ "${current_pcn}" != "${pcn_stem}"[0-9] ]]; then
  echo "Current registration PCN ${current_pcn} does not match stem ${pcn_stem}" >&2
  exit 1
fi
if [[ -z "${local_authority}" ]]; then
  echo "Case ${CASE_REFERENCE} has no local authority" >&2
  exit 1
fi

suffix="${current_pcn: -1}"
if [[ "${suffix}" == "9" ]]; then
  echo "PCN suffix cannot exceed 9 (current registration is ${current_pcn})" >&2
  exit 1
fi
next_suffix=$((suffix + 1))
next_pcn="${pcn_stem}${next_suffix}"
authority_code="${pcn_stem:0:${#pcn_stem}-8}"

batch_seed=$(($(date +%s) ^ $$ ^ RANDOM))
file_identifier="$(printf 'R%s%05d' "${authority_code}" "$((batch_seed % 100000))")"
batch_identifier="$(printf 'R%s%06d' "${authority_code}" "$(((batch_seed * 37) % 1000000))")"

echo "Creating registration batch for ${local_authority} (${file_identifier} / ${batch_identifier})..." >&2
batch_response="$(
  FILE_IDENTIFIER="${file_identifier}" \
  BATCH_IDENTIFIER="${batch_identifier}" \
  AUTHORITY_CODE="${authority_code}" \
  PCN_COUNT=1 \
  "${SCRIPT_DIR}/create-tec-batch.sh" "${local_authority}" registration
)"
batch_case_reference="$(jq --raw-output '.caseReference // empty' <<<"${batch_response}")"
if [[ -z "${batch_case_reference}" ]]; then
  echo "Batch create response did not include caseReference" >&2
  echo "${batch_response}" >&2
  exit 1
fi

event_data="$(jq --null-input --compact-output \
  --arg fileIdentifier "${file_identifier}" \
  --arg batchIdentifier "${batch_identifier}" \
  --arg penaltyChargeNumber "${next_pcn}" \
  --argjson caseData "$(jq '.data' <<<"${case_response}")" \
  '$caseData | {
    fileIdentifier: $fileIdentifier,
    batchIdentifier: $batchIdentifier,
    penaltyChargeNumber: $penaltyChargeNumber,
    localAuthority,
    respondentDetails1,
    respondentDetails2,
    respondentDetails3,
    respondentDetails4,
    respondentDetails5,
    respondentDetails6,
    vehicleRegistrationNumber,
    natureOfOffence,
    dateChargeCertificateServed,
    amountDue
  }')"

echo "Submitting ${EVENT_ID} with PCN ${next_pcn}..." >&2
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
  --argjson data "${event_data}" \
  '{
    event: {
      id: $eventId,
      summary: "Add registration",
      description: "Add registration"
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

echo "Linking PCN case ${CASE_REFERENCE} to batch case ${batch_case_reference}..." >&2
"${SCRIPT_DIR}/link-pcn-to-batch.sh" "${CASE_REFERENCE}" "${batch_case_reference}" >/dev/null

jq --null-input \
  --arg caseReference "${CASE_REFERENCE}" \
  --arg penaltyChargeNumber "${next_pcn}" \
  --arg pcnStem "${pcn_stem}" \
  --arg fileIdentifier "${file_identifier}" \
  --arg batchIdentifier "${batch_identifier}" \
  --arg batchCaseReference "${batch_case_reference}" \
  '{
    caseReference: $caseReference,
    pcnStem: $pcnStem,
    penaltyChargeNumber: $penaltyChargeNumber,
    fileIdentifier: $fileIdentifier,
    batchIdentifier: $batchIdentifier,
    batchCaseReference: $batchCaseReference
  }'
