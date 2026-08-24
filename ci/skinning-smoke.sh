#!/usr/bin/env bash
set -euo pipefail

PACKAGE="de.yahya.ai"
ACTIVITY="de.yahya.ai/.MainActivity"
FIXTURE="ci/celine-skinned-probe.glb"

fail_skin() {
  echo "SKINNING ERROR: $*"
  adb shell "run-as $PACKAGE cat shared_prefs/celine_3d_diagnostics.xml" 2>/dev/null || true
  adb logcat -d | grep -E 'de\.yahya\.ai|Filament|gltfio|FATAL EXCEPTION|SIGABRT|V54-|V55-|REN-' | tail -260 || true
  exit 1
}

python3 ci/generate-celine-skinned-probe-glb.py "$FIXTURE"
if [[ ! -s "$FIXTURE" ]]; then
  fail_skin "Two-joint skinning fixture was not generated"
fi

adb shell am force-stop "$PACKAGE" || true
cat "$FIXTURE" | adb shell "run-as $PACKAGE sh -c 'mkdir -p files/models; cat > files/models/celine.glb'"
REMOTE_BYTES="$(adb shell "run-as $PACKAGE sh -c 'wc -c < files/models/celine.glb'" | tr -d '\r ' || true)"
if [[ -z "$REMOTE_BYTES" || "$REMOTE_BYTES" -lt 100000 ]]; then
  fail_skin "Skinned CI model was not installed into app-private storage (bytes=$REMOTE_BYTES)"
fi

echo "Injected neck+Head skinned Celine probe: $REMOTE_BYTES bytes"
adb shell pm grant "$PACKAGE" android.permission.RECORD_AUDIO || true
adb shell am start -W -n "$ACTIVITY"
sleep 8

PID="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
if [[ -z "$PID" ]]; then
  fail_skin "Yahya AI process died before CALL multi-joint proof"
fi

adb shell uiautomator dump /sdcard/v55-home.xml >/dev/null || fail_skin "Could not dump HOME UI"
adb pull /sdcard/v55-home.xml v55-home.xml >/dev/null || fail_skin "Could not pull HOME UI"
read -r TAP_X TAP_Y <<< "$(python3 - <<'PY'
import re, xml.etree.ElementTree as ET
root=ET.parse('v55-home.xml').getroot()
for n in root.iter('node'):
    if 'Mit Celin' not in n.attrib.get('text',''): continue
    m=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.attrib.get('bounds',''))
    if not m: continue
    x1,y1,x2,y2=map(int,m.groups()); print((x1+x2)//2,(y1+y2)//2); raise SystemExit
raise SystemExit('videochat button not found')
PY
)"
[[ -n "${TAP_X:-}" && -n "${TAP_Y:-}" ]] || fail_skin "Could not resolve CALL entry coordinates"
adb shell input tap "$TAP_X" "$TAP_Y"
sleep 6

adb shell uiautomator dump /sdcard/v55-call.xml >/dev/null || fail_skin "Could not dump CALL UI"
adb pull /sdcard/v55-call.xml v55-call.xml >/dev/null || fail_skin "Could not pull CALL UI"
grep -q 'Live mit Celin' v55-call.xml || fail_skin "CALL did not open for v55 proof"

adb exec-out screencap -p > emulator-skin-a.png
sleep 0.65
adb exec-out screencap -p > emulator-skin-b.png
sleep 0.65
adb exec-out screencap -p > emulator-skin-c.png

python3 ci/check-magenta-avatar.py emulator-skin-a.png SKIN_A || fail_skin "Skinned probe missing in frame A"
python3 ci/check-magenta-avatar.py emulator-skin-b.png SKIN_B || fail_skin "Skinned probe missing in frame B"
python3 ci/check-magenta-avatar.py emulator-skin-c.png SKIN_C || fail_skin "Skinned probe missing in frame C"
python3 ci/check-skinned-motion.py emulator-skin-a.png emulator-skin-b.png emulator-skin-c.png \
  || fail_skin "neck+Head joints did not visibly deform the skinned mesh in CALL"

adb shell "run-as $PACKAGE cat shared_prefs/celine_3d_diagnostics.xml" > emulator-celine-diagnostics.xml 2>/dev/null \
  || fail_skin "Could not read Celine diagnostics after v55 skinning proof"

if ! grep -q 'V55-110' emulator-celine-diagnostics.xml; then
  cat emulator-celine-diagnostics.xml
  fail_skin "Animator.updateBoneMatrices success marker V55-110 is missing"
fi
if ! grep -q 'neck+Head' emulator-celine-diagnostics.xml; then
  cat emulator-celine-diagnostics.xml
  fail_skin "v55 diagnostics do not confirm neck+Head ownership"
fi
if ! grep -q 'probe=true' emulator-celine-diagnostics.xml; then
  cat emulator-celine-diagnostics.xml
  fail_skin "The dedicated CelineSkinningProbe model was not detected"
fi
if grep -q 'V55-198\|V55-199\|V54-198\|V54-199' emulator-celine-diagnostics.xml; then
  cat emulator-celine-diagnostics.xml
  fail_skin "Guarded skinning driver recorded a runtime error"
fi

# Leaving CALL must restore the same stage without crashing; stage visibility is already covered by stage 1.
adb shell input keyevent 4
sleep 2
PID_AFTER="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
[[ -n "$PID_AFTER" ]] || fail_skin "Yahya AI process died while restoring HOME after v55 proof"

echo "v55 CALL multi-joint skinning proof passed with PID=$PID_AFTER"
echo "Verified: CALL-only neck+Head Animator.updateBoneMatrices + visible moving skinned pixels + HOME recovery"
