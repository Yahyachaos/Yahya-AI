#!/usr/bin/env bash
set -euo pipefail

PACKAGE="de.yahya.ai"
ACTIVITY="de.yahya.ai/.MainActivity"
FIXTURE="ci/celine-skinned-probe.glb"

fail_skin() {
  echo "SKINNING ERROR: $*"
  adb shell "run-as $PACKAGE cat shared_prefs/celine_3d_diagnostics.xml" 2>/dev/null || true
  adb logcat -d | grep -E 'de\.yahya\.ai|Filament|gltfio|FATAL EXCEPTION|SIGABRT|V54-|V55-|V56-|V57-|V58-|REN-' | tail -320 || true
  exit 1
}

python3 ci/generate-celine-skinned-probe-glb.py "$FIXTURE"
[[ -s "$FIXTURE" ]] || fail_skin "Five-joint skinning fixture was not generated"
adb shell am force-stop "$PACKAGE" || true
cat "$FIXTURE" | adb shell "run-as $PACKAGE sh -c 'mkdir -p files/models; cat > files/models/celine.glb'"
REMOTE_BYTES="$(adb shell "run-as $PACKAGE sh -c 'wc -c < files/models/celine.glb'" | tr -d '\r ' || true)"
[[ -n "$REMOTE_BYTES" && "$REMOTE_BYTES" -ge 100000 ]] || fail_skin "Skinned CI model install failed (bytes=$REMOTE_BYTES)"
echo "Injected Hips+LeftShoulder+RightShoulder+neck+Head skinned Celine probe: $REMOTE_BYTES bytes"
adb shell pm grant "$PACKAGE" android.permission.RECORD_AUDIO || true
adb shell am start -W -n "$ACTIVITY"
sleep 8
PID="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
[[ -n "$PID" ]] || fail_skin "Yahya AI process died before v58 CALL proof"

adb shell uiautomator dump /sdcard/v58-home.xml >/dev/null || fail_skin "Could not dump HOME UI"
adb pull /sdcard/v58-home.xml v58-home.xml >/dev/null || fail_skin "Could not pull HOME UI"
read -r TAP_X TAP_Y <<< "$(python3 - <<'PY'
import re, xml.etree.ElementTree as ET
root=ET.parse('v58-home.xml').getroot()
for n in root.iter('node'):
    if 'Mit Celin' not in n.attrib.get('text',''): continue
    m=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.attrib.get('bounds',''))
    if m:
        x1,y1,x2,y2=map(int,m.groups());print((x1+x2)//2,(y1+y2)//2);raise SystemExit
raise SystemExit('videochat button not found')
PY
)"
[[ -n "${TAP_X:-}" && -n "${TAP_Y:-}" ]] || fail_skin "Could not resolve CALL entry coordinates"
adb shell input tap "$TAP_X" "$TAP_Y"
sleep 6
adb shell uiautomator dump /sdcard/v58-call.xml >/dev/null || fail_skin "Could not dump CALL UI"
adb pull /sdcard/v58-call.xml v58-call.xml >/dev/null || fail_skin "Could not pull CALL UI"
grep -q 'Live mit Celin' v58-call.xml || fail_skin "CALL did not open for v58 proof"

adb exec-out screencap -p > emulator-skin-a.png
sleep 0.65
adb exec-out screencap -p > emulator-skin-b.png
sleep 0.65
adb exec-out screencap -p > emulator-skin-c.png
python3 ci/check-magenta-avatar.py emulator-skin-a.png SKIN_A || fail_skin "Skinned probe missing in frame A"
python3 ci/check-magenta-avatar.py emulator-skin-b.png SKIN_B || fail_skin "Skinned probe missing in frame B"
python3 ci/check-magenta-avatar.py emulator-skin-c.png SKIN_C || fail_skin "Skinned probe missing in frame C"
python3 ci/check-skinned-motion.py emulator-skin-a.png emulator-skin-b.png emulator-skin-c.png || fail_skin "Five-joint skinning probe did not visibly deform the mesh"

adb shell "run-as $PACKAGE cat shared_prefs/celine_3d_diagnostics.xml" > emulator-celine-diagnostics.xml 2>/dev/null || fail_skin "Could not read diagnostics"
grep -q 'V56-110' emulator-celine-diagnostics.xml || fail_skin "V56-110 baseline marker missing"
grep -q 'V57-120' emulator-celine-diagnostics.xml || fail_skin "V57-120 head/gaze marker missing"
grep -q 'V58-120' emulator-celine-diagnostics.xml || fail_skin "V58-120 shoulder marker missing"
grep -q 'Hips+LeftShoulder+RightShoulder+neck+Head' emulator-celine-diagnostics.xml || fail_skin "v58 diagnostics do not confirm five-joint ownership"
grep -q 'probe=true' emulator-celine-diagnostics.xml || fail_skin "CelineSkinningProbe not detected"
if grep -q 'V58-198\|V58-199\|V57-198\|V57-199\|V56-198\|V56-199\|V55-198\|V55-199\|V54-198\|V54-199' emulator-celine-diagnostics.xml; then
  cat emulator-celine-diagnostics.xml
  fail_skin "Guarded skinning driver recorded a runtime error"
fi

adb shell input keyevent 4
sleep 2
PID_AFTER="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
[[ -n "$PID_AFTER" ]] || fail_skin "Process died restoring HOME after v58 proof"
echo "v58 CALL shoulder presence proof passed with PID=$PID_AFTER"
echo "Verified: Hips+shoulders+neck+Head skinning + V58 marker + moving pixels + HOME recovery"
