#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly STUB_SCRIPT="${SCRIPT_DIR}/local-dm-store-stub.py"
readonly PID_FILE="${SCRIPT_DIR}/.local-dm-store-stub.pid"
readonly LOG_FILE="${SCRIPT_DIR}/.local-dm-store-stub.log"
readonly PORT="${DM_STORE_PORT:-4506}"

if ! command -v python3 >/dev/null 2>&1; then
  echo "Required command not found: python3" >&2
  exit 1
fi

if [[ -f "${PID_FILE}" ]]; then
  existing_pid="$(<"${PID_FILE}")"
  if kill -0 "${existing_pid}" 2>/dev/null; then
    echo "Local dm-store stub already running (pid ${existing_pid}) on port ${PORT}"
    exit 0
  fi
  rm -f "${PID_FILE}"
fi

if command -v lsof >/dev/null 2>&1; then
  if lsof -nP -iTCP:"${PORT}" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "Port ${PORT} is already in use; not starting another dm-store stub." >&2
    lsof -nP -iTCP:"${PORT}" -sTCP:LISTEN >&2 || true
    exit 1
  fi
fi

nohup python3 "${STUB_SCRIPT}" >"${LOG_FILE}" 2>&1 &
echo $! >"${PID_FILE}"

for _ in {1..20}; do
  if curl --silent --fail --connect-timeout 1 "http://localhost:${PORT}/health" >/dev/null 2>&1; then
    echo "Local dm-store stub started on http://localhost:${PORT} (pid $(<"${PID_FILE}"))"
    echo "Logs: ${LOG_FILE}"
    exit 0
  fi
  sleep 0.2
done

echo "Failed to start local dm-store stub; see ${LOG_FILE}" >&2
exit 1
