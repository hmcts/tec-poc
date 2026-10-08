#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_NAME="$(basename "${BASH_SOURCE[0]}")"

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-6432}"
DB_USER="${DB_USER:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-postgres}"
ES_URL="${ES_URL:-http://localhost:9200}"

# CCD case-type search aliases used by ExUI Find case / work-basket.
readonly ES_TEC_ALIASES=(
  tec_cases
  tec_batch_cases
  tec_exception_cases
)

ASSUME_YES=false
PSQL_BACKEND=""
DOCKER_CONTAINER_ID=""
ES_AVAILABLE=false

usage() {
  cat <<EOF
Usage: ${SCRIPT_NAME} [OPTIONS]

Permanently delete all TEC case data from the local CFTLib Postgres and
Elasticsearch so you can re-seed without restarting the stack. Clears:

  - tec.public business tables (PCN, batch, exception + documents + warrant authorisations)
  - tec.ccd decentralised lifecycle rows for jurisdiction TEC
  - datastore.public CCD orchestration rows for jurisdiction TEC
    (including case_link / Linked Cases)
  - Elasticsearch TEC search indices (ExUI Find case / work-basket results)

Does not stop CFTLib, wipe Definition Store / IDAM, or clear local dm-store files.

Options:
  -y, --yes   Skip the interactive confirmation prompt
  -h, --help  Show this help and exit

Optional environment variables:
  DB_HOST (default: localhost), DB_PORT (default: 6432),
  DB_USER (default: postgres), DB_PASSWORD (default: postgres),
  ES_URL (default: http://localhost:9200)

Examples:
  ${SCRIPT_NAME}
  ${SCRIPT_NAME} --yes
EOF
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      -y | --yes)
        ASSUME_YES=true
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

resolve_psql_backend() {
  if command -v psql >/dev/null 2>&1; then
    if PGPASSWORD="${DB_PASSWORD}" psql \
      -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d tec \
      -v ON_ERROR_STOP=1 -c 'SELECT 1' >/dev/null 2>&1; then
      PSQL_BACKEND="host"
      return 0
    fi
  fi

  if ! command -v docker >/dev/null 2>&1; then
    echo "Neither host psql (reachable at ${DB_HOST}:${DB_PORT}) nor docker is available." >&2
    echo "Start the stack with ./gradlew bootWithCCD and try again." >&2
    exit 1
  fi

  if ! docker info >/dev/null 2>&1; then
    echo "Docker is not running or is not accessible." >&2
    exit 1
  fi

  DOCKER_CONTAINER_ID="$(docker ps --filter 'name=cftlib-shared-database' -q | head -1)"
  if [[ -z "${DOCKER_CONTAINER_ID}" ]]; then
    DOCKER_CONTAINER_ID="$(docker ps --filter 'name=cftlib' --filter 'publish=6432' -q | head -1)"
  fi

  if [[ -z "${DOCKER_CONTAINER_ID}" ]]; then
    echo "Could not find a running CFTLib Postgres container (name contains cftlib-shared-database)." >&2
    echo "Start the stack with ./gradlew bootWithCCD and try again." >&2
    exit 1
  fi

  if ! docker exec "${DOCKER_CONTAINER_ID}" \
    psql -U postgres -d tec -v ON_ERROR_STOP=1 -c 'SELECT 1' >/dev/null 2>&1; then
    echo "Found container ${DOCKER_CONTAINER_ID} but could not connect to database tec." >&2
    exit 1
  fi

  if ! docker exec "${DOCKER_CONTAINER_ID}" \
    psql -U postgres -d datastore -v ON_ERROR_STOP=1 -c 'SELECT 1' >/dev/null 2>&1; then
    echo "Found container ${DOCKER_CONTAINER_ID} but could not connect to database datastore." >&2
    exit 1
  fi

  PSQL_BACKEND="docker"
}

run_psql() {
  local database="$1"
  shift

  case "${PSQL_BACKEND}" in
    host)
      PGPASSWORD="${DB_PASSWORD}" psql \
        -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${database}" \
        -v ON_ERROR_STOP=1 --quiet --tuples-only --no-align "$@"
      ;;
    docker)
      # Do not use -i here: it would steal stdin from the y/n confirmation prompt.
      docker exec "${DOCKER_CONTAINER_ID}" \
        psql -U postgres -d "${database}" \
        -v ON_ERROR_STOP=1 --quiet --tuples-only --no-align "$@"
      ;;
    *)
      echo "Internal error: PSQL_BACKEND not set" >&2
      exit 1
      ;;
  esac
}

run_psql_file() {
  local database="$1"
  local sql="$2"

  case "${PSQL_BACKEND}" in
    host)
      PGPASSWORD="${DB_PASSWORD}" psql \
        -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${database}" \
        -v ON_ERROR_STOP=1 --quiet <<<"${sql}"
      ;;
    docker)
      docker exec -i "${DOCKER_CONTAINER_ID}" \
        psql -U postgres -d "${database}" \
        -v ON_ERROR_STOP=1 --quiet <<<"${sql}"
      ;;
    *)
      echo "Internal error: PSQL_BACKEND not set" >&2
      exit 1
      ;;
  esac
}

count_or_zero() {
  local database="$1"
  local sql="$2"
  local result

  result="$(run_psql "${database}" -c "${sql}" 2>/dev/null || true)"
  if [[ -z "${result}" ]]; then
    printf '0'
  else
    printf '%s' "${result}"
  fi
}

probe_elasticsearch() {
  if ! command -v curl >/dev/null 2>&1; then
    echo "Warning: curl not found; cannot clear Elasticsearch search indices." >&2
    ES_AVAILABLE=false
    return 0
  fi

  if curl --silent --fail --max-time 3 "${ES_URL}" >/dev/null 2>&1; then
    ES_AVAILABLE=true
  else
    ES_AVAILABLE=false
    echo "Warning: Elasticsearch not reachable at ${ES_URL}; search indices will not be cleared." >&2
    echo "Ghost cases may still appear in ExUI Find case until ES is cleared." >&2
  fi
}

es_alias_doc_count() {
  local alias="$1"
  local count

  count="$(curl --silent --fail --max-time 5 \
    "${ES_URL}/${alias}/_count" 2>/dev/null \
    | sed -n 's/.*"count"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' \
    || true)"

  if [[ -z "${count}" ]]; then
    printf 'n/a'
  else
    printf '%s' "${count}"
  fi
}

print_counts() {
  local label="$1"
  local alias

  echo
  echo "${label}"
  echo "  tec.public:"
  echo "    tec_case:              $(count_or_zero tec 'SELECT count(*) FROM tec_case')"
  echo "    tec_case_registration: $(count_or_zero tec 'SELECT count(*) FROM tec_case_registration')"
  echo "    tec_case_te9:          $(count_or_zero tec 'SELECT count(*) FROM tec_case_te9')"
  echo "    tec_case_te7:          $(count_or_zero tec 'SELECT count(*) FROM tec_case_te7')"
  echo "    tec_case_pe3:          $(count_or_zero tec 'SELECT count(*) FROM tec_case_pe3')"
  echo "    tec_case_pe2:          $(count_or_zero tec 'SELECT count(*) FROM tec_case_pe2')"
  echo "    tec_case_document:     $(count_or_zero tec 'SELECT count(*) FROM tec_case_document')"
  echo "    tec_case_warrant_authorisation: $(count_or_zero tec 'SELECT count(*) FROM tec_case_warrant_authorisation')"
  echo "    tec_batch:             $(count_or_zero tec 'SELECT count(*) FROM tec_batch')"
  echo "    tec_batch_document:    $(count_or_zero tec 'SELECT count(*) FROM tec_batch_document')"
  echo "    tec_batch_pcn_link:    $(count_or_zero tec 'SELECT count(*) FROM tec_batch_pcn_link')"
  echo "    tec_exception_case:    $(count_or_zero tec 'SELECT count(*) FROM tec_exception_case')"
  echo "  tec.ccd:"
  echo "    case_data (TEC):       $(count_or_zero tec "SELECT count(*) FROM ccd.case_data WHERE jurisdiction = 'TEC'")"
  echo "  datastore.public:"
  echo "    case_data (TEC):       $(count_or_zero datastore "SELECT count(*) FROM case_data WHERE jurisdiction = 'TEC'")"
  echo "    case_link (TEC refs):  $(count_or_zero datastore "SELECT count(*) FROM case_link WHERE case_id IN (SELECT id FROM case_data WHERE jurisdiction = 'TEC') OR linked_case_id IN (SELECT id FROM case_data WHERE jurisdiction = 'TEC')")"
  if [[ "${ES_AVAILABLE}" == true ]]; then
    echo "  elasticsearch (${ES_URL}):"
    for alias in "${ES_TEC_ALIASES[@]}"; do
      printf '    %-22s %s\n' "${alias}:" "$(es_alias_doc_count "${alias}")"
    done
  else
    echo "  elasticsearch:         unreachable (skipped)"
  fi
}

confirm_or_abort() {
  local reply

  if [[ "${ASSUME_YES}" == true ]]; then
    echo "Skipping confirmation (--yes)."
    return 0
  fi

  echo
  read -r -p "This will permanently delete all TEC cases. Continue? [y/N] " reply
  case "${reply}" in
    y | Y | yes | YES)
      return 0
      ;;
    *)
      echo "Aborted. No changes made."
      exit 0
      ;;
  esac
}

clear_tec_database() {
  echo
  echo "Clearing tec database (business tables + ccd lifecycle)..."
  run_psql_file tec "$(cat <<'SQL'
TRUNCATE TABLE
  tec_case_document,
  tec_case_warrant_authorisation,
  tec_case_general_application,
  tec_case_registration,
  tec_case_te9,
  tec_case_te7,
  tec_case_pe3,
  tec_case_pe2,
  tec_batch_pcn_link,
  tec_batch_document,
  tec_case,
  tec_exception_case,
  tec_batch
RESTART IDENTITY CASCADE;

DELETE FROM ccd.case_data WHERE jurisdiction = 'TEC';
SQL
)"
}

clear_datastore_database() {
  echo "Clearing datastore database (CCD orchestration for jurisdiction TEC)..."
  run_psql_file datastore "$(cat <<'SQL'
DO $$
DECLARE
  tec_ids bigint[];
BEGIN
  SELECT coalesce(array_agg(id), ARRAY[]::bigint[])
    INTO tec_ids
    FROM case_data
   WHERE jurisdiction = 'TEC';

  IF cardinality(tec_ids) = 0 THEN
    RETURN;
  END IF;

  IF to_regclass('public.case_event_significant_items') IS NOT NULL THEN
    DELETE FROM case_event_significant_items
     WHERE case_event_id IN (
       SELECT id FROM case_event WHERE case_data_id = ANY (tec_ids)
     );
  END IF;

  IF to_regclass('public.case_event') IS NOT NULL THEN
    DELETE FROM case_event WHERE case_data_id = ANY (tec_ids);
  END IF;

  IF to_regclass('public.case_users_audit') IS NOT NULL THEN
    DELETE FROM case_users_audit WHERE case_data_id = ANY (tec_ids);
  END IF;

  IF to_regclass('public.case_users') IS NOT NULL THEN
    DELETE FROM case_users WHERE case_data_id = ANY (tec_ids);
  END IF;

  IF to_regclass('public.all_events') IS NOT NULL THEN
    EXECUTE 'DELETE FROM all_events WHERE case_data_id = ANY ($1)' USING tec_ids;
  END IF;

  IF to_regclass('public.case_link') IS NOT NULL THEN
    DELETE FROM case_link
     WHERE case_id = ANY (tec_ids)
        OR linked_case_id = ANY (tec_ids);
  END IF;

  DELETE FROM case_data WHERE id = ANY (tec_ids);
END $$;
SQL
)"
}

clear_elasticsearch() {
  local aliases_csv
  local response
  local deleted

  if [[ "${ES_AVAILABLE}" != true ]]; then
    echo "Skipping Elasticsearch clear (not reachable)."
    return 0
  fi

  aliases_csv="$(IFS=,; echo "${ES_TEC_ALIASES[*]}")"
  echo "Clearing Elasticsearch TEC search indices (${aliases_csv})..."

  response="$(curl --silent --show-error --fail --max-time 60 \
    -X POST "${ES_URL}/${aliases_csv}/_delete_by_query?conflicts=proceed&refresh=true" \
    -H 'Content-Type: application/json' \
    -d '{"query":{"match_all":{}}}' 2>&1)" || {
    echo "Failed to clear Elasticsearch indices via ${ES_URL}:" >&2
    echo "${response}" >&2
    exit 1
  }

  deleted="$(sed -n 's/.*"deleted"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' <<<"${response}" | head -1)"
  echo "Deleted ${deleted:-?} Elasticsearch document(s)."
}

main() {
  parse_args "$@"
  resolve_psql_backend
  probe_elasticsearch

  echo "Clear TEC cases (local CFTLib Postgres + Elasticsearch)"
  if [[ "${PSQL_BACKEND}" == "host" ]]; then
    echo "Using host psql at ${DB_HOST}:${DB_PORT}"
  else
    echo "Using docker exec on container ${DOCKER_CONTAINER_ID}"
  fi
  if [[ "${ES_AVAILABLE}" == true ]]; then
    echo "Using Elasticsearch at ${ES_URL}"
  fi
  echo
  echo "This will permanently delete all TEC cases from:"
  echo "  - database tec (public business tables + ccd schema)"
  echo "  - database datastore (CCD orchestration, including case_link)"
  echo "  - Elasticsearch TEC search indices (ExUI Find case / work-basket)"

  print_counts "Current row counts:"
  confirm_or_abort

  clear_tec_database
  clear_datastore_database
  clear_elasticsearch

  print_counts "Row counts after clear:"
  echo
  echo "Done. Re-seed with ./bin/create-tec-batch.sh / ./bin/create-tec-case.sh as needed."
}

main "$@"
