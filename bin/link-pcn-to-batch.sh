#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

CCD_URL="${CCD_DATA_STORE_URL:-http://localhost:4452}"
EVENT_ID="${EVENT_ID:-linkBatchCase}"
BATCH_LINK_EVENT_ID="${BATCH_LINK_EVENT_ID:-linkPcnCases}"
PCN_CASE_REFERENCE_RAW="${1:-}"
BATCH_CASE_REFERENCE_RAW="${2:-}"
# ExUI Reasons column looks up Reason via CaseLinkingReasonCode LOV; free text goes in OtherDescription.
BATCH_LINK_REASON_CODE="${BATCH_LINK_REASON_CODE:-${BATCH_REGISTRATION_REASON_CODE:-CLRC007}}"
BATCH_REGISTRATION_REASON="${BATCH_REGISTRATION_REASON:-Linked as part of a batch of registrations}"
BATCH_WARRANT_AUTH_REASON="${BATCH_WARRANT_AUTH_REASON:-Linked as part of a batch of warrant auth requests}"
BATCH_WARRANT_REISSUE_REASON="${BATCH_WARRANT_REISSUE_REASON:-Linked as part of a batch of warrant reissue requests}"
BATCH_OUT_OF_TIME_REASON="${BATCH_OUT_OF_TIME_REASON:-Linked as part of a batch of out-of-time decisions}"
BATCH_CHANGE_OF_ADDRESS_REASON="${BATCH_CHANGE_OF_ADDRESS_REASON:-Linked as part of a batch of change of address}"
BATCH_CASE_CLOSURE_REASON="${BATCH_CASE_CLOSURE_REASON:-Linked as part of a batch of case closure requests}"
BATCH_TRANSFER_REQUEST_REASON="${BATCH_TRANSFER_REQUEST_REASON:-Linked as part of a batch of transfer requests}"

for command in curl jq; do
  if ! command -v "${command}" >/dev/null 2>&1; then
    echo "Required command not found: ${command}" >&2
    exit 1
  fi
done

usage() {
  cat <<EOF
Usage: ${0} <pcn-case-reference> <batch-case-reference>

Link a TEC PCN case to a TEC Batch case:
  1. Load the batch to read its operation (batch type)
  2. PCN event linkBatchCase with batchLinkCase + batchLinkType
     - registration → sets the current registration's batch link (Case details Batch case)
     - other types → tec_batch_pcn_link membership only (does not change Batch case)
  3. Batch event linkPcnCases with the full caseLinks collection so ExUI
     shows the PCN under the batch's "linked to" list and the batch under
     the PCN's "linked from" list, with Reason=CLRC007 (Other) and
     OtherDescription chosen from the batch's operation:
       registration          → "${BATCH_REGISTRATION_REASON}"
       warrantAuthRequests   → "${BATCH_WARRANT_AUTH_REASON}"
       warrantReissueRequests → "${BATCH_WARRANT_REISSUE_REASON}"
       outOfTimeDecisions    → "${BATCH_OUT_OF_TIME_REASON}"
       changeOfAddress       → "${BATCH_CHANGE_OF_ADDRESS_REASON}"
       caseClosureRequests   → "${BATCH_CASE_CLOSURE_REASON}"
       transferRequest       → "${BATCH_TRANSFER_REQUEST_REASON}"

PCN CCD case state side-effects from linkBatchCase:
  transferRequest       → REFER_FOR_ENFORCEMENT
  caseClosureRequests   → CLOSED
  other batch types     → state unchanged

Hyphens in either case reference are optional.

Optional environment variables:
  CCD_DATA_STORE_URL, EVENT_ID, BATCH_LINK_EVENT_ID, BATCH_LINK_REASON_CODE
  (default CLRC007 = Other), and BATCH_*_REASON overrides for each batch type
EOF
}

# Maps BatchOperation JSON value → OtherDescription free text.
reason_for_batch_operation() {
  local operation="$1"
  case "${operation}" in
    registration)
      printf '%s\n' "${BATCH_REGISTRATION_REASON}"
      ;;
    warrantAuthRequests)
      printf '%s\n' "${BATCH_WARRANT_AUTH_REASON}"
      ;;
    warrantReissueRequests)
      printf '%s\n' "${BATCH_WARRANT_REISSUE_REASON}"
      ;;
    outOfTimeDecisions)
      printf '%s\n' "${BATCH_OUT_OF_TIME_REASON}"
      ;;
    changeOfAddress)
      printf '%s\n' "${BATCH_CHANGE_OF_ADDRESS_REASON}"
      ;;
    caseClosureRequests)
      printf '%s\n' "${BATCH_CASE_CLOSURE_REASON}"
      ;;
    transferRequest)
      printf '%s\n' "${BATCH_TRANSFER_REQUEST_REASON}"
      ;;
    *)
      echo "Unsupported or missing batch operation '${operation}' on batch case; cannot choose link reason" >&2
      exit 1
      ;;
  esac
}

if [[ -z "${PCN_CASE_REFERENCE_RAW}" || -z "${BATCH_CASE_REFERENCE_RAW}" ]]; then
  usage >&2
  exit 1
fi

normalise_case_reference() {
  local raw="$1"
  local digits
  digits="$(printf '%s' "${raw}" | tr -d '-')"
  if [[ ! "${digits}" =~ ^[0-9]+$ ]]; then
    echo "Case reference must contain digits (hyphens optional): '${raw}'" >&2
    exit 1
  fi
  printf '%s\n' "${digits}"
}

PCN_CASE_REFERENCE="$(normalise_case_reference "${PCN_CASE_REFERENCE_RAW}")"
BATCH_CASE_REFERENCE="$(normalise_case_reference "${BATCH_CASE_REFERENCE_RAW}")"

user_token="$("${SCRIPT_DIR}/get-local-idam-token.sh")"
service_token="$("${SCRIPT_DIR}/get-local-s2s-token.sh" tec_api)"

echo "Loading batch case ${BATCH_CASE_REFERENCE}..." >&2

batch_case_response="$({
  curl --silent --show-error --fail-with-body \
    --connect-timeout 5 \
    --max-time 120 \
    --request GET "${CCD_URL}/cases/${BATCH_CASE_REFERENCE}" \
    --header "Authorization: Bearer ${user_token}" \
    --header "ServiceAuthorization: ${service_token}" \
    --header 'experimental: true'
} 2>&1)" || {
  echo "Failed to load batch case ${BATCH_CASE_REFERENCE}" >&2
  echo "${batch_case_response}" >&2
  exit 1
}

batch_operation="$(jq --raw-output '.data.operation // empty' <<<"${batch_case_response}")"
batch_link_reason="$(reason_for_batch_operation "${batch_operation}")"
echo "Linking PCN case ${PCN_CASE_REFERENCE} to batch case ${BATCH_CASE_REFERENCE} (operation=${batch_operation})..." >&2
echo "Using link reason: Other - ${batch_link_reason}" >&2

event_trigger_url="${CCD_URL}/cases/${PCN_CASE_REFERENCE}/event-triggers/${EVENT_ID}"

start_response="$({
  curl --silent --show-error --fail-with-body \
    --connect-timeout 5 \
    --max-time 120 \
    --request GET "${event_trigger_url}" \
    --header "Authorization: Bearer ${user_token}" \
    --header "ServiceAuthorization: ${service_token}" \
    --header 'experimental: true'
} 2>&1)" || {
  echo "Failed to start ${EVENT_ID} for case ${PCN_CASE_REFERENCE}" >&2
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
  --arg batchCaseReference "${BATCH_CASE_REFERENCE}" \
  --arg batchLinkType "${batch_operation}" \
  '{
    event: {
      id: $eventId,
      summary: "Link batch case",
      description: "Link PCN case to batch case"
    },
    data: {
      batchLinkCase: {
        CaseReference: $batchCaseReference,
        CaseType: "TEC_BATCH"
      },
      batchLinkType: $batchLinkType
    },
    event_token: $eventToken
  }')"

submit_response="$({
  curl --silent --show-error --fail-with-body \
    --connect-timeout 5 \
    --max-time 120 \
    --request POST "${CCD_URL}/cases/${PCN_CASE_REFERENCE}/events" \
    --header "Authorization: Bearer ${user_token}" \
    --header "ServiceAuthorization: ${service_token}" \
    --header 'Content-Type: application/json' \
    --header 'experimental: true' \
    --data "${submit_body}"
} 2>&1)" || {
  echo "Failed to submit ${EVENT_ID} for case ${PCN_CASE_REFERENCE}" >&2
  echo "${submit_response}" >&2
  exit 1
}

pcn_state="$(jq --raw-output '.state // empty' <<<"${submit_response}")"
if [[ -n "${pcn_state}" ]]; then
  echo "PCN case ${PCN_CASE_REFERENCE} is now ${pcn_state}." >&2
else
  echo "PCN case ${PCN_CASE_REFERENCE} linked (state unchanged)." >&2
fi

echo "Refreshing batch caseLinks (linked to) for case ${BATCH_CASE_REFERENCE}..." >&2

# Reload batch so CaseView includes the PCN we just linked.
batch_case_response="$({
  curl --silent --show-error --fail-with-body \
    --connect-timeout 5 \
    --max-time 120 \
    --request GET "${CCD_URL}/cases/${BATCH_CASE_REFERENCE}" \
    --header "Authorization: Bearer ${user_token}" \
    --header "ServiceAuthorization: ${service_token}" \
    --header 'experimental: true'
} 2>&1)" || {
  echo "Failed to reload batch case ${BATCH_CASE_REFERENCE}" >&2
  echo "${batch_case_response}" >&2
  exit 1
}

# CaseView returns caseLinks for all PCNs linked to this batch.
case_links_json="$(jq --compact-output \
  --arg reason "${batch_link_reason}" \
  --arg reasonCode "${BATCH_LINK_REASON_CODE}" '
  (.data.caseLinks // []) as $existing
  | if ($existing | length) > 0 then
      [
        $existing[]
        | . as $entry
        | {
            id: ($entry.id // $entry.value.CaseReference),
            value: {
              CaseReference: $entry.value.CaseReference,
              CaseType: ($entry.value.CaseType // "TEC"),
              ReasonForLink: [ {
                id: "1",
                value: { Reason: $reasonCode, OtherDescription: $reason }
              } ]
            }
          }
      ]
    else
      []
    end
' <<<"${batch_case_response}")"

# Ensure the PCN we just linked is present even if CaseView was empty.
case_links_json="$(jq --compact-output \
  --arg pcn "${PCN_CASE_REFERENCE}" \
  --arg reason "${batch_link_reason}" \
  --arg reasonCode "${BATCH_LINK_REASON_CODE}" '
  . as $links
  | if any(.[]; .value.CaseReference == $pcn) then .
    else . + [{
      id: $pcn,
      value: {
        CaseReference: $pcn,
        CaseType: "TEC",
        ReasonForLink: [ {
          id: "1",
          value: { Reason: $reasonCode, OtherDescription: $reason }
        } ]
      }
    }]
    end
' <<<"${case_links_json}")"

batch_event_trigger_url="${CCD_URL}/cases/${BATCH_CASE_REFERENCE}/event-triggers/${BATCH_LINK_EVENT_ID}"

batch_start_response="$({
  curl --silent --show-error --fail-with-body \
    --connect-timeout 5 \
    --max-time 120 \
    --request GET "${batch_event_trigger_url}" \
    --header "Authorization: Bearer ${user_token}" \
    --header "ServiceAuthorization: ${service_token}" \
    --header 'experimental: true'
} 2>&1)" || {
  echo "Failed to start ${BATCH_LINK_EVENT_ID} for batch case ${BATCH_CASE_REFERENCE}" >&2
  echo "${batch_start_response}" >&2
  exit 1
}

batch_event_token="$(jq --raw-output '.token // empty' <<<"${batch_start_response}")"
if [[ -z "${batch_event_token}" ]]; then
  echo "CCD start-event response did not contain a token for ${BATCH_LINK_EVENT_ID}" >&2
  echo "${batch_start_response}" >&2
  exit 1
fi

batch_submit_body="$(jq --null-input --compact-output \
  --arg eventToken "${batch_event_token}" \
  --argjson caseLinks "${case_links_json}" \
  '{
    event: {
      id: "linkPcnCases",
      summary: "Link PCN cases",
      description: "Link PCN cases to batch case"
    },
    data: {
      caseLinks: $caseLinks
    },
    event_token: $eventToken
  }')"

batch_submit_response="$({
  curl --silent --show-error --fail-with-body \
    --connect-timeout 5 \
    --max-time 120 \
    --request POST "${CCD_URL}/cases/${BATCH_CASE_REFERENCE}/events" \
    --header "Authorization: Bearer ${user_token}" \
    --header "ServiceAuthorization: ${service_token}" \
    --header 'Content-Type: application/json' \
    --header 'experimental: true' \
    --data "${batch_submit_body}"
} 2>&1)" || {
  echo "Failed to submit ${BATCH_LINK_EVENT_ID} for batch case ${BATCH_CASE_REFERENCE}" >&2
  echo "${batch_submit_response}" >&2
  exit 1
}

jq . <<<"${submit_response}"
