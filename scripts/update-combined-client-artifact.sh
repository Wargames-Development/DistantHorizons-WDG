#!/usr/bin/env bash
set -euo pipefail

REPOSITORY="$(
    cd "$(dirname "${BASH_SOURCE[0]}")/.."
    pwd
)"

UPDATER="$REPOSITORY/scripts/update-combined-client-artifact.py"
COMMANDS_DIR="${COMMANDS_DIR:-}"

arguments=(update "$@")

if [[ -n "$COMMANDS_DIR" ]]; then
    arguments+=(--commands-dir "$COMMANDS_DIR")
fi

python3 "$UPDATER" "${arguments[@]}"

(
    cd "$REPOSITORY"

    ./gradlew \
        --no-daemon \
        --no-watch-fs \
        spotlessApply \
        :buildSrc:test \
        verifyRepository
)

git -C "$REPOSITORY" diff --check

printf '\nDEPENDENCY UPDATE PREVALIDATION PASSED\n'
printf 'Run the complete Change 006 validation before committing.\n'
printf '\nGit status:\n'
git -C "$REPOSITORY" status --short
