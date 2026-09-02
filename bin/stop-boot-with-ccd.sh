#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_NAME="$(basename "${BASH_SOURCE[0]}")"

# Ports used by ./gradlew bootWithCCD (Java services and Docker infrastructure).
readonly CFTLIB_PORTS=(
  3000   # Manage Case (XUI)
  4013   # TEC API and decentralised callbacks
  4452   # CCD Data Store
  4453   # CCD Definition Store / User Profile
  4455   # CCD Case Document AM API
  4506   # Local dm-store stub (bin/start-local-dm-store.sh)
  5062   # IDAM simulator
  6432   # Shared PostgreSQL
  8087   # WA Task Management API
  8489   # S2S simulator
)

STOP_ALL_DOCKER=false

usage() {
  cat <<EOF
Usage: ${SCRIPT_NAME} [OPTIONS]

Stop leftover bootWithCCD / CFTLib processes and Docker containers so a fresh
./gradlew bootWithCCD run can start cleanly.

By default this script:
  1. Stops any running bootWithCCD / CFTLib Java processes
  2. Stops Docker containers whose names contain "cftlib"
  3. Checks that required local ports are free

Options:
  --all-docker    Stop all running Docker containers, not just CFTLib ones
  -h, --help      Show this help message
EOF
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --all-docker)
        STOP_ALL_DOCKER=true
        ;;
      -h | --help)
        usage
        exit 0
        ;;
      *)
        echo "Unknown option: $1" >&2
        usage >&2
        exit 1
        ;;
    esac
    shift
  done
}

require_docker() {
  if ! command -v docker >/dev/null 2>&1; then
    echo "Required command not found: docker" >&2
    exit 1
  fi

  if ! docker info >/dev/null 2>&1; then
    echo "Docker is not running or is not accessible." >&2
    exit 1
  fi
}

cftlib_process_pattern() {
  printf '%s' '[.]gradlew bootWithCCD|rse-cft-lib.*LibRunner'
}

stop_matching_processes() {
  local signal="$1"
  local pattern

  pattern="$(cftlib_process_pattern)"
  pkill "-${signal}" -f "${pattern}" 2>/dev/null || true
}

list_matching_processes() {
  local pattern

  pattern="$(cftlib_process_pattern)"
  pgrep -fl "${pattern}" 2>/dev/null || true
}

stop_boot_with_ccd_processes() {
  local processes

  echo "Checking for running bootWithCCD / CFTLib Java processes..."

  processes="$(list_matching_processes)"
  if [[ -z "${processes}" ]]; then
    echo "No bootWithCCD / CFTLib Java processes found."
  else
    echo "Stopping bootWithCCD / CFTLib Java processes:"
    echo "${processes}"
    stop_matching_processes TERM

    echo "Waiting for processes to exit..."
    for _ in {1..10}; do
      if [[ -z "$(list_matching_processes)" ]]; then
        echo "bootWithCCD / CFTLib Java processes stopped."
        break
      fi
      sleep 1
    done

    if [[ -n "$(list_matching_processes)" ]]; then
      echo "Some processes did not stop gracefully; sending SIGKILL..."
      stop_matching_processes KILL
      sleep 1
    fi

    processes="$(list_matching_processes)"
    if [[ -n "${processes}" ]]; then
      echo "Unable to stop the following processes:" >&2
      echo "${processes}" >&2
      exit 1
    fi

    echo "bootWithCCD / CFTLib Java processes stopped."
  fi

  stop_local_dm_store_stub
}

stop_local_dm_store_stub() {
  local pid_file
  local pid

  pid_file="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/.local-dm-store-stub.pid"
  if [[ ! -f "${pid_file}" ]]; then
    return 0
  fi

  pid="$(<"${pid_file}")"
  if [[ -n "${pid}" ]] && kill -0 "${pid}" 2>/dev/null; then
    echo "Stopping local dm-store stub (pid ${pid})..."
    kill "${pid}" 2>/dev/null || true
  fi
  rm -f "${pid_file}"
}

port_pattern() {
  local port
  local -a parts=()

  for port in "${CFTLIB_PORTS[@]}"; do
    parts+=(":${port}\\b")
  done

  (IFS='|'; printf '%s' "${parts[*]}")
}

check_required_ports() {
  local pattern
  local listeners

  pattern="$(port_pattern)"
  echo "Checking required ports are free..."

  if ! command -v lsof >/dev/null 2>&1; then
    echo "Required command not found: lsof" >&2
    exit 1
  fi

  listeners="$(lsof -nP -iTCP -sTCP:LISTEN 2>/dev/null | grep -E "${pattern}" || true)"

  if [[ -z "${listeners}" ]]; then
    echo "All required ports are free."
    return 0
  fi

  echo "The following required ports are still in use:" >&2
  echo "${listeners}" >&2
  echo >&2
  echo "Stop the process using each port, or run ${SCRIPT_NAME} again after any" >&2
  echo "leftover bootWithCCD instance has fully shut down." >&2
  exit 1
}

collect_container_ids() {
  if [[ "${STOP_ALL_DOCKER}" == true ]]; then
    docker ps -q
  else
    docker ps --filter "name=cftlib" -q
  fi
}

stop_docker_containers() {
  local container_ids
  local -a container_id_array=()

  if [[ "${STOP_ALL_DOCKER}" == true ]]; then
    echo "Stopping all running Docker containers..."
  else
    echo 'Stopping Docker containers matching name "cftlib"...'
  fi

  container_ids="$(collect_container_ids)"
  if [[ -z "${container_ids}" ]]; then
    if [[ "${STOP_ALL_DOCKER}" == true ]]; then
      echo "No running Docker containers found."
    else
      echo 'No running Docker containers matching name "cftlib" found.'
    fi
    return 0
  fi

  while IFS= read -r container_id; do
    [[ -n "${container_id}" ]] && container_id_array+=("${container_id}")
  done <<<"${container_ids}"

  docker stop "${container_id_array[@]}"
  echo "Stopped ${#container_id_array[@]} Docker container(s)."
}

main() {
  parse_args "$@"
  require_docker
  stop_boot_with_ccd_processes
  stop_docker_containers
  check_required_ports
  echo "Cleanup complete. You can now run ./gradlew bootWithCCD"
}

main "$@"
