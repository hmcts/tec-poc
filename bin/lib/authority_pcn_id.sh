#!/usr/bin/env bash

# Look up the TEC two-alpha local authority id for a FixedList code.
# Source this file; do not execute it.

authority_pcn_id() {
  local authority="${1:-}"
  local ids_file id
  ids_file="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/local_authority_pcn_ids.txt"
  if [[ -z "${authority}" ]]; then
    echo "Local authority is required to choose a PCN authority id" >&2
    return 1
  fi
  id="$(awk -v code="${authority}" '$1 == code { print $2; exit }' "${ids_file}")"
  if [[ -z "${id}" ]]; then
    echo "No TEC local authority id for ${authority}" >&2
    return 1
  fi
  printf '%s\n' "${id}"
}
