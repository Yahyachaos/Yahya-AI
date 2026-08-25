#!/usr/bin/env bash
set -euo pipefail

# Stage 0 proves the v61 production-only Meshy 0.01 / inverse-bind x100 correction stays wired.
bash ci/v61-meshy-rig-scale-guard.sh

# Stage 1 preserves every proven HOME / CALL / HOME-return visibility and layout gate.
bash ci/emulator-visibility-smoke.sh

# Stage 1b proves v60's bounded camera-search controls on the recovered HOME stage.
bash ci/v60-camera-controls-smoke.sh

# Stage 2 swaps in a minimal one-joint skinned fixture and proves that Head skinning moves pixels
# without reviving the old broad v46 pose layer.
bash ci/skinning-smoke.sh
