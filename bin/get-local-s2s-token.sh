#!/usr/bin/env bash

set -euo pipefail

S2S_URL="${IDAM_S2S_AUTH_URL:-http://localhost:8489}"
SERVICE_NAME="${1:-tec_api}"

for command in curl jq; do
  if ! command -v "${command}" >/dev/null 2>&1; then
    echo "Required command not found: ${command}" >&2
    exit 1
  fi
done

request_body="$(jq --null-input --compact-output --arg microservice "${SERVICE_NAME}" '{microservice: $microservice}')"

response="$({
  curl --silent --show-error --fail-with-body \
    --connect-timeout 5 \
    --max-time 30 \
    --request POST "${S2S_URL}/lease" \
    --header 'Content-Type: application/json' \
    --data "${request_body}"
} 2>&1)" || {
  echo "Failed to obtain an S2S token from ${S2S_URL}/lease" >&2
  echo "${response}" >&2
  exit 1
}

printf '%s\n' "${response}"
