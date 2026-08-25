#!/usr/bin/env bash
set -euo pipefail

APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
CANDIDATE="${2:-morph-proof/celine_morph_candidate.glb}"
PACKAGE="de.yahya.ai"
ACTIVITY="de.yahya.ai/.MainActivity"

collect() {
  adb logcat -d > real-candidate-logcat.txt 2>/dev/null || true
  adb shell "run-as $PACKAGE cat shared_prefs/yahya_ai.xml" > real-candidate-prefs.xml 2>/dev/null || true
}
trap collect EXIT

fail() {
  echo "ERROR: $*"
  adb logcat -d | grep -E 'de\.yahya\.ai|Filament|gltfio|FATAL EXCEPTION|SIGABRT|V62-|V61-|V60-|REN-|VIS-' | tail -260 || true
  exit 1
}

[[ -s "$APK" ]] || fail "missing APK: $APK"
[[ -s "$CANDIDATE" ]] || fail "missing candidate: $CANDIDATE"

CANDIDATE_BYTES="$(wc -c < "$CANDIDATE" | tr -d ' ')"
if [[ "$CANDIDATE_BYTES" -lt 1000000 ]]; then
  fail "candidate unexpectedly small: $CANDIDATE_BYTES"
fi

echo "Candidate local bytes=$CANDIDATE_BYTES"
adb install -r "$APK"
adb shell am force-stop "$PACKAGE" || true
adb logcat -c || true

# Candidate is injected only into app-private storage. The checked-in production source/baseline is
# never overwritten by this proof.
cat "$CANDIDATE" | adb shell "run-as $PACKAGE sh -c 'mkdir -p files/models; cat > files/models/celine.glb'"
REMOTE_BYTES="$(adb shell "run-as $PACKAGE sh -c 'wc -c < files/models/celine.glb'" | tr -d '\r ' || true)"
[[ "$REMOTE_BYTES" = "$CANDIDATE_BYTES" ]] || fail "candidate injection byte mismatch local=$CANDIDATE_BYTES remote=$REMOTE_BYTES"
echo "Injected real candidate copy: $REMOTE_BYTES bytes"

adb shell pm grant "$PACKAGE" android.permission.RECORD_AUDIO || true
adb shell am start -W -n "$ACTIVITY"
sleep 13

PID="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
[[ -n "$PID" ]] || fail "process died before HOME proof"

adb shell uiautomator dump /sdcard/celine-real-home.xml >/dev/null || fail "HOME UI dump failed"
adb pull /sdcard/celine-real-home.xml real-candidate-home.xml >/dev/null || fail "HOME UI pull failed"
adb exec-out screencap -p > real-candidate-home.png

grep -q 'Celin 3D Ansicht' real-candidate-home.xml || fail "HOME 3D stage missing"
grep -q 'Mit Celin' real-candidate-home.xml || fail "HOME call entry missing"
python3 ci/check-real-celine-render.py real-candidate-home.png HOME

# The v63 guarded runtime must recognize this exact real candidate as morph-capable.
adb logcat -d > real-candidate-logcat-home.txt
if ! grep -q 'V62-210' real-candidate-logcat-home.txt; then
  fail "real candidate loaded but six-target morph runtime did not activate (V62-210 missing)"
fi
if grep -Eq 'V62-298|V62-299|REN-399|FATAL EXCEPTION|SIGABRT' real-candidate-logcat-home.txt; then
  fail "runtime error detected during real-candidate HOME proof"
fi

read -r TAP_X TAP_Y <<< "$(python3 - <<'PY'
import re, xml.etree.ElementTree as ET
root=ET.parse('real-candidate-home.xml').getroot()
for n in root.iter('node'):
    if 'Mit Celin' not in n.attrib.get('text',''): continue
    m=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.attrib.get('bounds',''))
    if not m: continue
    x1,y1,x2,y2=map(int,m.groups()); print((x1+x2)//2,(y1+y2)//2); raise SystemExit
raise SystemExit('call button bounds missing')
PY
)"
[[ -n "${TAP_X:-}" && -n "${TAP_Y:-}" ]] || fail "CALL tap coordinates missing"
adb shell input tap "$TAP_X" "$TAP_Y"
sleep 7

PID_CALL="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
[[ -n "$PID_CALL" ]] || fail "process died opening CALL"
adb shell uiautomator dump /sdcard/celine-real-call.xml >/dev/null || fail "CALL UI dump failed"
adb pull /sdcard/celine-real-call.xml real-candidate-call.xml >/dev/null || fail "CALL UI pull failed"
adb exec-out screencap -p > real-candidate-call.png
grep -q 'Live mit Celin' real-candidate-call.xml || fail "CALL overlay missing"
grep -q 'Celin 3D Ansicht' real-candidate-call.xml || fail "CALL 3D stage missing"
python3 ci/check-real-celine-render.py real-candidate-call.png CALL

# Lifecycle regression: close CALL and prove the same process and a visible real candidate recover.
adb shell input keyevent 4
sleep 5
PID_RETURN="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
[[ -n "$PID_RETURN" ]] || fail "process died returning HOME"
adb shell uiautomator dump /sdcard/celine-real-return.xml >/dev/null || fail "HOME-return UI dump failed"
adb pull /sdcard/celine-real-return.xml real-candidate-home-return.xml >/dev/null || fail "HOME-return UI pull failed"
adb exec-out screencap -p > real-candidate-home-return.png
grep -q 'Mit Celin' real-candidate-home-return.xml || fail "HOME did not recover"
grep -q 'Celin 3D Ansicht' real-candidate-home-return.xml || fail "HOME-return 3D stage missing"
python3 ci/check-real-celine-render.py real-candidate-home-return.png HOME_RETURN
python3 ci/check-home-return-zoom.py real-candidate-home.png real-candidate-home-return.png

adb logcat -d > real-candidate-logcat-final.txt
if grep -Eq 'V62-298|V62-299|REN-399|FATAL EXCEPTION|SIGABRT' real-candidate-logcat-final.txt; then
  fail "runtime error detected across HOME/CALL lifecycle"
fi
if ! grep -q 'V62-210' real-candidate-logcat-final.txt; then
  fail "morph runtime activation evidence missing after lifecycle"
fi

printf 'PASS real Celine candidate: bytes=%s pid_home=%s pid_call=%s pid_return=%s\n' "$CANDIDATE_BYTES" "$PID" "$PID_CALL" "$PID_RETURN"
