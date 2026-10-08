#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<EOF
Usage: ${0} <local-authority-id> [unique-number]
       ${0} -h|--help

Print a penalty charge number in the form AA9999999X0 (or AAA9999999X0).

  AA / AAA   Local authority identifier (2 or 3 letters)
  9999999    Unique 7-digit PCN number
  X          Mod 11 check digit (0-9, or A when the remainder is 10)
  0          Numeric suffix (always 0)

The check digit is calculated from the local authority identifier and the
7-digit number only. The suffix is not included. If either of those parts is
missing or incomplete, no check digit is calculated.

Letters in the local authority identifier convert to digits as:

  A-J -> 1 2 3 4 5 6 7 8 9 0
  K-T -> 1 2 3 4 5 6 7 8 9 0
  U-Z -> 1 2 3 4 5 6

For a 2-letter identifier the nine values are weighted 9, 8, ... 1.
For a 3-letter identifier the ten values are weighted 10, 9, ... 1.
The check digit is the sum modulo 11, or A when that remainder is 10.

Arguments:
  <local-authority-id>  2 or 3 letters, for example LR or WCC.
                        May be combined with the unique number (LR2468037).
  [unique-number]       7 digits. Omit to generate a number at random.
  -h, --help            Show this help and exit

Examples:
  ${0} LR 2468037     # LR246803780
  ${0} LR2468037      # LR246803780
  ${0} AB             # AB plus a random 7-digit number, check digit, and 0
EOF
}

die() {
  echo "${1}" >&2
  exit 1
}

# A-J, K-T, and U-Z each restart at 1. J and T are 0. Z is 6.
letter_value() {
  local letter="$1"
  local index=$(( $(printf '%d' "'${letter}") - 65 ))
  if (( index < 0 || index > 25 )); then
    die "Local authority identifier must contain only letters A-Z"
  fi
  echo $(( (index % 10 + 1) % 10 ))
}

# Body is the local authority identifier plus the 7-digit number.
check_digit() {
  local body="$1"
  local length="${#body}"
  local authority_length
  local weight
  local sum=0
  local index=0
  local character
  local value

  if (( length == 9 )); then
    authority_length=2
  elif (( length == 10 )); then
    authority_length=3
  else
    die "Cannot calculate a check digit: local authority identifier or unique PCN number is missing"
  fi

  weight="${length}"
  while (( index < length )); do
    character="${body:index:1}"
    if (( index < authority_length )); then
      if [[ ! "${character}" =~ ^[A-Z]$ ]]; then
        die "Cannot calculate a check digit: local authority identifier or unique PCN number is missing"
      fi
      value="$(letter_value "${character}")"
    else
      if [[ ! "${character}" =~ ^[0-9]$ ]]; then
        die "Cannot calculate a check digit: local authority identifier or unique PCN number is missing"
      fi
      value=$((10#${character}))
    fi
    sum=$((sum + value * weight))
    weight=$((weight - 1))
    index=$((index + 1))
  done

  local remainder=$((sum % 11))
  if (( remainder == 10 )); then
    echo "A"
  else
    echo "${remainder}"
  fi
}

random_unique_number() {
  local raw
  if [[ -r /dev/urandom ]]; then
    raw="$(od -An -N4 -tu4 /dev/urandom | tr -d '[:space:]')"
  else
    raw="$((RANDOM * 32768 + RANDOM))"
  fi
  printf '%07d' "$((10#${raw} % 10000000))"
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ $# -lt 1 || $# -gt 2 ]]; then
  usage >&2
  exit 1
fi

authority_id="$(printf '%s' "${1}" | tr '[:lower:]' '[:upper:]')"
unique_number="${2:-}"

if [[ -z "${unique_number}" && "${authority_id}" =~ ^([A-Z]{2,3})([0-9]{7})$ ]]; then
  unique_number="${BASH_REMATCH[2]}"
  authority_id="${BASH_REMATCH[1]}"
fi

if [[ ! "${authority_id}" =~ ^[A-Z]{2,3}$ ]]; then
  die "Local authority identifier must be 2 or 3 letters"
fi

if [[ -z "${unique_number}" ]]; then
  unique_number="$(random_unique_number)"
fi

if [[ ! "${unique_number}" =~ ^[0-9]{7}$ ]]; then
  die "Unique PCN number must be 7 digits"
fi

suffix="0"
digit="$(check_digit "${authority_id}${unique_number}")"
printf '%s%s%s%s\n' "${authority_id}" "${unique_number}" "${digit}" "${suffix}"
