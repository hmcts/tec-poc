#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

CCD_URL="${CCD_DATA_STORE_URL:-http://localhost:4452}"
EVENT_ID="${EVENT_ID:-registrationPaymentSucceeded}"
CASE_REFERENCE="${1:-}"
PAYMENT_REFERENCE="${PAYMENT_REFERENCE:-TEC-PAY-$(date +%s)}"

for command in curl jq; do
  if ! command -v "${command}" >/dev/null 2>&1; then
    echo "Required command not found: ${command}" >&2
    exit 1
  fi
done

if [[ -z "${CASE_REFERENCE}" ]]; then
  echo "Usage: ${0} <case-reference>" >&2
  echo "Optional environment variables: PAYMENT_REFERENCE" >&2
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
  --arg paymentReference "${PAYMENT_REFERENCE}" \
  '{
    event: {
      id: $eventId,
      summary: "Registration payment succeeded",
      description: "Registration payment succeeded"
    },
    data: {
      paymentReference: $paymentReference
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

jq . <<<"${submit_response}"
