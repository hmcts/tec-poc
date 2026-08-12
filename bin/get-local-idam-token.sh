#!/usr/bin/env bash

set -euo pipefail

IDAM_URL="${IDAM_API_URL:-http://localhost:5062}"
CLIENT_ID="${IDAM_CLIENT_ID:-tec}"
CLIENT_SECRET="${IDAM_CLIENT_SECRET:-123456}"
USERNAME="${IDAM_USERNAME:-tec-system@test.com}"
PASSWORD="${IDAM_PASSWORD:-password}"

for command in curl jq; do
  if ! command -v "${command}" >/dev/null 2>&1; then
    echo "Required command not found: ${command}" >&2
    exit 1
  fi
done

response="$({
  curl --silent --show-error --fail-with-body \
    --connect-timeout 5 \
    --max-time 30 \
    --request POST "${IDAM_URL}/o/token" \
    --header 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode 'grant_type=password' \
    --data-urlencode "client_id=${CLIENT_ID}" \
    --data-urlencode "client_secret=${CLIENT_SECRET}" \
    --data-urlencode "username=${USERNAME}" \
    --data-urlencode "password=${PASSWORD}" \
    --data-urlencode 'scope=openid profile roles'
} 2>&1)" || {
  echo "Failed to obtain a token from ${IDAM_URL}/o/token" >&2
  echo "${response}" >&2
  exit 1
}

token="$(jq --raw-output '.access_token // empty' <<<"${response}")"

if [[ -z "${token}" ]]; then
  echo 'IDAM response did not contain an access_token' >&2
  exit 1
fi

printf '%s\n' "${token}"
