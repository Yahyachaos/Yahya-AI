#!/usr/bin/env bash
set -euo pipefail

PACKAGE="de.yahya.ai"
ACTIVITY="de.yahya.ai/.MainActivity"
FIXTURE="ci/celine-skinned-probe.glb"

fail_skin() {
  echo "SKINNING ERROR: $*"
  adb shell "run-as $PACKAGE cat shared_prefs/celine_3d_diagnostics.xml" 2>/dev/null || true
  adb logcat -d | grep -E 'de\.yahya\.ai|Filament|gltfio|FATAL EXCEPTION|SIGABRT|V54-|REN-' | tail -220 || true
  exit 1
}

python3 ci/generate-celine-skinned-probe-glb.py "$FIXTURE"
if [[ ! -s "$FIXTURE" ]]; then
  fail_skin "One-joint skinning fixture was not generated"
fi

adb shell am force-stop "$PACKAGE" || true
cat "$FIXTURE" | adb shell "run-as $PACKAGE sh -c 'mkdir -p files/models; cat > files/models/celine.glb'"
REMOTE_BYTES="$(adb shell "run-as $PACKAGE sh -c 'wc -c < files/models/celine.glb'" | tr -d '\r ' || true)"
if [[ -z "$REMOTE_BYTES" || "$REMOTE_BYTES" -lt 100000 ]]; then
  fail_skin "Skinned CI model was not installed into app-private storage (bytes=$REMOTE_BYTES)"
fi

echo "Injected one-joint skinned Celine probe: $REMOTE_BYTES bytes"
adb shell am start -W -n "$ACTIVITY"
sleep 8

PID="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
if [[ -z "$PID" ]]; then
  fail_skin "Yahya AI process died during one-joint skinning proof"
fi

adb exec-out screencap -p > emulator-skin-a.png
sleep 0.65
adb exec-out screencap -p > emulator-skin-b.png
sleep 0.65
adb exec-out screencap -p > emulator-skin-c.png

python3 ci/check-magenta-avatar.py emulator-skin-a.png SKIN_A || fail_skin "Skinned probe missing in frame A"
python3 ci/check-magenta-avatar.py emulator-skin-b.png SKIN_B || fail_skin "Skinned probe missing in frame B"
python3 ci/check-magenta-avatar.py emulator-skin-c.png SKIN_C || fail_skin "Skinned probe missing in frame C"
python3 ci/check-skinned-motion.py emulator-skin-a.png emulator-skin-b.png emulator-skin-c.png \
  || fail_skin "Head joint did not visibly deform the skinned mesh"

adb shell "run-as $PACKAGE cat shared_prefs/celine_3d_diagnostics.xml" \
  > emulator-celine-diagnostics.xml 2>/dev/null \
  || fail_skin "Could not read Celine diagnostics after skinning proof"

if ! grep -q 'V54-110' emulator-celine-diagnostics.xml; then
  cat emulator-celine-diagnostics.xml
  fail_skin "Animator.updateBoneMatrices success marker V54-110 is missing"
fi
if ! grep -q 'probe=true' emulator-celine-diagnostics.xml; then
  cat emulator-celine-diagnostics.xml
  fail_skin "The dedicated CelineSkinningProbe model was not detected"
fi
if grep -q 'V54-198\|V54-199' emulator-celine-diagnostics.xml; then
  cat emulator-celine-diagnostics.xml
  fail_skin "The guarded one-joint driver recorded a runtime error"
fi

echo "One-joint skinning proof passed with PID=$PID"
echo "Verified: Head-only Animator.updateBoneMatrices + visible moving skinned pixels"
