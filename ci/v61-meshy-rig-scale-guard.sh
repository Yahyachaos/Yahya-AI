#!/usr/bin/env bash
set -euo pipefail

SRC="app/src/main/java/de/yahya/ai/CelineMeshyRigScaleV61.java"
APP="app/src/main/java/de/yahya/ai/YahyaApplication.java"
GRADLE="app/build.gradle"

test -f "$SRC" || { echo "ERROR: v61 Meshy rig-scale guard missing"; exit 1; }
grep -q 'versionCode 61' "$GRADLE" || { echo "ERROR: versionCode 61 missing"; exit 1; }
grep -q 'CelineMeshyRigScaleV61.install(activity, decor)' "$APP" || { echo "ERROR: v61 guard not wired into application lifecycle"; exit 1; }
grep -q 'asset.getFirstEntityByName("Armature")' "$SRC" || { echo "ERROR: Armature detection missing"; exit 1; }
grep -q 'asset.getFirstEntityByName("Hips")' "$SRC" || { echo "ERROR: Hips detection missing"; exit 1; }
grep -q 'float correction = 1.0f / rigScale' "$SRC" || { echo "ERROR: inverse rig-scale correction missing"; exit 1; }
grep -q 'TARGET_HEIGHT = 2.35f' "$SRC" || { echo "ERROR: safe default target height changed"; exit 1; }
grep -q 'MIN_PRODUCTION_BYTES = 1_000_000L' "$SRC" || { echo "ERROR: production-only guard missing"; exit 1; }

python3 - <<'PY'
# Regression math from the production Meshy layout: Armature=0.01 and effective body extent ~= 1.7 m.
# Filament's tiny pre-skinning bounds can therefore be ~= 0.017 m. v60 normalized that tiny value,
# which leads to an enormous root scale. v61 must recover effective skinned-space size first.
raw_extent = 0.017
rig_scale = 0.01
target = 2.35
old_scale = 3.15 / raw_extent
corrected_extent = raw_extent / rig_scale
new_scale = target / corrected_extent
assert 1.6 < corrected_extent < 1.8, corrected_extent
assert 1.2 < new_scale < 1.6, new_scale
assert old_scale / new_scale > 100.0, (old_scale, new_scale)
print(f"v61 Meshy math OK: old={old_scale:.2f} correctedExtent={corrected_extent:.2f} new={new_scale:.3f}")
PY

echo "v61 Meshy rig-scale guard OK"
