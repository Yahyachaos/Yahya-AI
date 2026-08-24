#!/usr/bin/env bash
set -euo pipefail

# Stage 1 preserves every proven HOME / CALL / HOME-return visibility and layout gate.
bash ci/emulator-visibility-smoke.sh

# Stage 2 swaps in a minimal one-joint skinned fixture and proves that Head skinning moves pixels
# without reviving the old broad v46 pose layer.
bash ci/skinning-smoke.sh
