#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../android"
mode="${1:-verify}"
case "$mode" in
  record) task=recordRoborazziDebug ;;
  compare) task=compareRoborazziDebug ;;
  verify) task=verifyRoborazziDebug ;;
  *) echo "Usage: $0 [record|compare|verify] [Gradle options...]" >&2; exit 2 ;;
esac
if (( $# > 0 )); then shift; fi
./gradlew ":app:$task" --tests 'com.trainerloop.ui.screenshots.*' "$@"

printf '\nScreenshot task succeeded: %s\n' "$task"
printf 'PNG directory: %s/app/src/test/screenshots\n' "$PWD"
printf 'Available PNGs (references and any retained comparisons):\n'
for png in "$PWD"/app/src/test/screenshots/*.png; do
  [[ -f "$png" ]] && printf '  %s\n' "$png"
done
printf 'Report: %s/app/build/reports/roborazzi/index.html\n' "$PWD"
printf 'Visual inspection is still required: open the relevant PNGs with an image tool.\n'
