#!/usr/bin/env sh

print_help() {
  echo "Build and run the local TEC API POC Docker stack

  Usage:

  ./run-in-docker.sh [OPTION]

  Options:
    --clean, -c                   Clean before assembling the application
    --help, -h                    Print this help block
  "
}

# script execution flags
GRADLE_CLEAN=false

if docker compose version >/dev/null 2>&1
then
  compose() { docker compose "$@"; }
elif command -v docker-compose >/dev/null 2>&1
then
  compose() { docker-compose "$@"; }
else
  echo "Docker Compose is required" >&2
  exit 1
fi

execute_script() {
  cd "$(dirname "$0")/.." || exit 1

  if [ "${GRADLE_CLEAN}" = true ]
  then
    echo "Clearing previous build.."
    ./gradlew clean
  fi

  echo "Assembling distribution.."
  ./gradlew assemble

  echo "Bringing up docker containers.."

  compose up --build
}

while true ; do
  case "$1" in
    -h|--help) print_help ; shift ; break ;;
    -c|--clean) GRADLE_CLEAN=true ; shift ;;
    *) execute_script ; break ;;
  esac
done
