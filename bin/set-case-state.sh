#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

CCD_URL="${CCD_DATA_STORE_URL:-http://localhost:4452}"
EVENT_ID="${EVENT_ID:-setCaseState}"
CASE_REFERENCE_RAW="${1:-}"
TARGET_STATE_RAW="${2:-}"

readonly VALID_STATES=(
  PENDING_CASE_ISSUED
  CASE_ISSUED
  AWAITING_RESPONDENT_RESPONSE
  AWAITING_OOT_VALIDATION
  AWAITING_LA_OOT_RESPONSE
  CASE_REVOKED_IN_TIME
  CASE_REVOKED_OOT_ACCEPTED
  CASE_REVOKED_LA_NO_RESPONSE
  PENDING_REFUSAL_DECISION
  REFUSAL_ORDER
  CASE_REVOKED_LA_REFUSAL_OVERTURNED
  PENDING_OOT_APPEAL_PAYMENT
  OOT_APPEAL_PAYMENT_CONFIRMED
  PENDING_OOT_APPEAL_DECISION
  OOT_APPEAL_REFUSED
  CASE_REVOKED_OOT_APPEAL_ACCEPTED
  WARRANT_AUTHORISATION_ISSUED
  WARRANT_AUTHORISATION_EXPIRED
  REFER_FOR_ENFORCEMENT
  CLOSED
)

for command in curl jq; do
  if ! command -v "${command}" >/dev/null 2>&1; then
    echo "Required command not found: ${command}" >&2
    exit 1
  fi
done

usage() {
  cat <<EOF
Usage: ${0} <case-reference> <state>

Set a TEC PCN case to the given CCD state via the system setCaseState event
(local prototyping — skips normal lifecycle events).

States (any CaseState):
  PENDING_CASE_ISSUED
  CASE_ISSUED
  AWAITING_RESPONDENT_RESPONSE
  AWAITING_OOT_VALIDATION
  AWAITING_LA_OOT_RESPONSE
  CASE_REVOKED_IN_TIME
  CASE_REVOKED_OOT_ACCEPTED
  CASE_REVOKED_LA_NO_RESPONSE
  PENDING_REFUSAL_DECISION
  REFUSAL_ORDER
  CASE_REVOKED_LA_REFUSAL_OVERTURNED
  PENDING_OOT_APPEAL_PAYMENT
  OOT_APPEAL_PAYMENT_CONFIRMED
  PENDING_OOT_APPEAL_DECISION
  OOT_APPEAL_REFUSED
  CASE_REVOKED_OOT_APPEAL_ACCEPTED
  WARRANT_AUTHORISATION_ISSUED
  WARRANT_AUTHORISATION_EXPIRED
  REFER_FOR_ENFORCEMENT
  CLOSED

Optional environment variables:
  CCD_DATA_STORE_URL, EVENT_ID

Examples:
  ${0} 1788364399834478 CASE_ISSUED
  ${0} 1788-3643-9983-4478 AWAITING_RESPONDENT_RESPONSE
  ${0} 1788364399834478 REFER_FOR_ENFORCEMENT
EOF
}

if [[ -z "${CASE_REFERENCE_RAW}" || -z "${TARGET_STATE_RAW}" \
  || "${CASE_REFERENCE_RAW}" == "-h" || "${CASE_REFERENCE_RAW}" == "--help" ]]; then
  usage >&2
  exit 1
fi

CASE_REFERENCE="$(printf '%s' "${CASE_REFERENCE_RAW}" | tr -d '-')"
if [[ ! "${CASE_REFERENCE}" =~ ^[0-9]+$ ]]; then
  echo "Case reference must contain digits (hyphens optional): '${CASE_REFERENCE_RAW}'" >&2
  exit 1
fi

TARGET_STATE="$(printf '%s' "${TARGET_STATE_RAW}" | tr '[:lower:]' '[:upper:]')"
valid=false
for state in "${VALID_STATES[@]}"; do
  if [[ "${TARGET_STATE}" == "${state}" ]]; then
    valid=true
    break
  fi
done
if [[ "${valid}" != true ]]; then
  echo "Unknown state '${TARGET_STATE_RAW}'. Use one of: ${VALID_STATES[*]}" >&2
  exit 1
fi

user_token="$("${SCRIPT_DIR}/get-local-idam-token.sh")"
service_token="$("${SCRIPT_DIR}/get-local-s2s-token.sh" tec_api)"

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

submit_url="${CCD_URL}/cases/${CASE_REFERENCE}/events"
submit_body="$(jq --null-input --compact-output \
  --arg eventId "${EVENT_ID}" \
  --arg eventToken "${event_token}" \
  --arg targetState "${TARGET_STATE}" \
  '{
    event: {
      id: $eventId,
      summary: ("Set case state to " + $targetState),
      description: ("Set case state to " + $targetState)
    },
    data: {
      targetCaseState: $targetState
    },
    event_token: $eventToken
  }')"

submit_response="$({
  curl --silent --show-error --fail-with-body \
    --connect-timeout 5 \
    --max-time 120 \
    --request POST "${submit_url}" \
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

final_state="$(jq --raw-output '.state // empty' <<<"${submit_response}")"
echo "Case ${CASE_REFERENCE} is now ${final_state:-${TARGET_STATE}}." >&2
jq . <<<"${submit_response}"
