#!/usr/bin/env bash
# Single-command dispatcher for android-emulator-runner. The action executes YAML script lines
# separately, so all multi-step proof selection must live inside this wrapper.
set -euo pipefail

scope="${1:-full}"
apk="${2:-ci-apk/app-debug.apk}"
out="${3:-avatar-lab-proof}"

case "$scope" in
  9r4)
    exec timeout 720s bash ci/celine-9r4-chair-proof.sh "$apk" "$out"
    ;;
  9r3)
    exec timeout 720s bash ci/celine-9r3-bed-proof.sh "$apk" "$out"
    ;;
  9r2)
    exec timeout 540s bash ci/celine-9r2-table-proof.sh "$apk" "$out"
    ;;
  9r1)
    exec timeout 720s bash ci/celine-scene-integration-proof.sh "$apk" "$out"
    ;;
  full)
    exec timeout 600s bash -c '
      set -euo pipefail
      apk="$1"
      out="$2"
      bash ci/avatar-lab-proof.sh "$apk" "$out"
      bash ci/celine-scene-integration-proof.sh "$apk" "$out"
      bash ci/celine-avatar-lab-settings-proof.sh "$apk" "$out"
    ' _ "$apk" "$out"
    ;;
  *)
    echo "Unknown Avatar Lab proof scope: $scope" >&2
    exit 2
    ;;
esac
