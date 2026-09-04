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
  adb logcat -d | grep -E 'de\.yahya\.ai|Filament|gltfio|FATAL EXCEPTION|SIGABRT|V80-|V79-|V76-|V75-|V74-|V70-|V62-|V61-|V60-|V39-|CTL-|REN-|VIS-' | tail -300 || true
  exit 1
}

wait_for_log() {
  local needle="$1"
  local label="$2"
  for _ in $(seq 1 35); do
    if adb logcat -d | grep -q "$needle"; then
      echo "Ready: $label"
      return 0
    fi
    sleep 1
  done
  fail "timed out waiting for $label ($needle)"
}

dump_ui_with_retry() {
  local remote="$1"
  local local_file="$2"
  local label="$3"
  local attempt
  for attempt in $(seq 1 8); do
    if adb shell "rm -f '$remote'; uiautomator dump '$remote' >/dev/null 2>&1; test -s '$remote'" >/dev/null 2>&1 \
      && adb pull "$remote" "$local_file" >/dev/null 2>&1 \
      && [[ -s "$local_file" ]]; then
      echo "Ready: $label UI hierarchy (attempt $attempt)"
      return 0
    fi
    echo "Retry: $label UI hierarchy unavailable (attempt $attempt/8)"
    sleep 2
  done
  fail "$label UI hierarchy unavailable after retries"
}

[[ -s "$APK" ]] || fail "missing APK: $APK"
[[ -s "$CANDIDATE" ]] || fail "missing candidate: $CANDIDATE"

CANDIDATE_BYTES="$(wc -c < "$CANDIDATE" | tr -d ' ')"
if [[ "$CANDIDATE_BYTES" -lt 1000000 ]]; then
  fail "candidate unexpectedly small: $CANDIDATE_BYTES"
fi
CANDIDATE_SHA="$(sha256sum "$CANDIDATE" | awk '{print $1}')"
APK_CANDIDATE_SHA="$(unzip -p "$APK" assets/models/celine.glb | sha256sum | awk '{print $1}')"
[[ "$APK_CANDIDATE_SHA" = "$CANDIDATE_SHA" ]] ||
  fail "APK candidate mismatch expected=$CANDIDATE_SHA packaged=$APK_CANDIDATE_SHA"
echo "Packaged production candidate: bytes=$CANDIDATE_BYTES sha256=$CANDIDATE_SHA"

adb install -r "$APK"
adb shell pm clear "$PACKAGE" >/dev/null || fail "could not clear private model state"
adb shell am force-stop "$PACKAGE" || true
adb logcat -c || true
PRIVATE_MODEL="$(adb shell "run-as $PACKAGE sh -c 'test -e files/models/celine.glb && echo yes || echo no'" | tr -d '\r ' || true)"
[[ "$PRIVATE_MODEL" = "no" ]] || fail "private candidate unexpectedly present before APK-source proof"

adb shell pm grant "$PACKAGE" android.permission.RECORD_AUDIO || true
adb shell am start -W -n "$ACTIVITY"
wait_for_log 'V61-110' 'packaged production rig-scale correction'
wait_for_log 'V39-150' 'packaged production texture binding'
wait_for_log 'V75-160' 'v75 semantic material ownership after V39'
wait_for_log 'CTL-350' 'confirmed 3D activation after visible-frame probe'
wait_for_log 'V80-400' 'central production root/body/head owner binding'
wait_for_log 'V80-410' 'central layered HOME presence'

PID="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
[[ -n "$PID" ]] || fail "process died before HOME proof"

adb exec-out screencap -p > real-candidate-home.png || fail "HOME screenshot failed"
dump_ui_with_retry /sdcard/celine-real-home.xml real-candidate-home.xml HOME

grep -q 'Celin 3D Ansicht' real-candidate-home.xml || fail "HOME 3D stage missing"
grep -q 'Mit Celin' real-candidate-home.xml || fail "HOME call entry missing"
python3 ci/check-real-celine-render.py real-candidate-home.png HOME
python3 ci/check-celine-person-presence.py real-candidate-home.png HOME

# v76 must use the packaged production asset, never an injected private file.
adb logcat -d > real-candidate-logcat-home.txt
if ! grep -q 'REN-306' real-candidate-logcat-home.txt; then
  fail "packaged APK production model source was not selected (REN-306 missing)"
fi
if grep -q 'REN-305' real-candidate-logcat-home.txt; then
  fail "private model unexpectedly overrode the packaged v76 production candidate"
fi
if ! grep -q 'V76-210' real-candidate-logcat-home.txt; then
  fail "real candidate loaded but exact fifteen-target morph runtime did not activate (V76-210 missing)"
fi
if ! grep -q 'V61-110' real-candidate-logcat-home.txt; then
  fail "packaged production rig-scale correction did not activate (V61-110 missing)"
fi
if ! grep -q 'V39-150' real-candidate-logcat-home.txt; then
  fail "packaged production texture was not explicitly bound (V39-150 missing)"
fi
if ! grep -q 'V75-160' real-candidate-logcat-home.txt; then
  fail "v75 semantic material owner did not restore top/jeans/shoes/hair after V39"
fi
if ! grep -q 'CTL-350' real-candidate-logcat-home.txt; then
  fail "3D controller did not confirm the visible candidate (CTL-350 missing)"
fi
if grep -Eq 'V80-499|V75-199|V39-158|V39-159|V61-102|V61-199|V76-298|V76-299|V62-298|V62-299|REN-399|FATAL EXCEPTION|SIGABRT' real-candidate-logcat-home.txt; then
  fail "runtime/source error detected during real-candidate HOME proof"
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
wait_for_log 'target=CALL eased=true snap=false' 'eased central HOME-to-CALL handoff'
wait_for_log 'V80-420' 'central layered CALL presence'

PID_CALL="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
[[ -n "$PID_CALL" ]] || fail "process died opening CALL"
# Capture the real visual checkpoint before querying accessibility. A transient
# UiTestAutomationBridge null-root must never discard an otherwise valid CALL frame.
adb exec-out screencap -p > real-candidate-call.png || fail "CALL screenshot failed"
dump_ui_with_retry /sdcard/celine-real-call.xml real-candidate-call.xml CALL
grep -q 'Live mit Celin' real-candidate-call.xml || fail "CALL overlay missing"
grep -q 'Celin 3D Ansicht' real-candidate-call.xml || fail "CALL 3D stage missing"
python3 ci/check-real-celine-render.py real-candidate-call.png CALL
python3 ci/check-celine-person-presence.py real-candidate-call.png CALL

# Lifecycle regression: close CALL and prove the same process and a visible real candidate recover.
# The SurfaceView transition guard keeps the last real CALL frame above the stage while the HOME
# surface/room is rebuilt. Do not capture that intentionally stale bridge frame as HOME evidence;
# wait for a NEW V80-512 after the return, which means the cover has been replaced by a direct,
# stable target SurfaceView frame.
RETURN_READY_BEFORE="$(adb logcat -d | grep -c 'V80-512' || true)"
adb shell input keyevent 4
sleep 5
wait_for_log 'target=HOME eased=true snap=false' 'eased central CALL-to-HOME handoff'
RETURN_READY_NOW="$(adb logcat -d | grep -c 'V80-512' || true)"
for _ in $(seq 1 25); do
  if [[ "$RETURN_READY_NOW" -gt "$RETURN_READY_BEFORE" ]]; then
    echo "Ready: direct HOME surface frame after CALL"
    break
  fi
  sleep 1
  RETURN_READY_NOW="$(adb logcat -d | grep -c 'V80-512' || true)"
done
[[ "$RETURN_READY_NOW" -gt "$RETURN_READY_BEFORE" ]] ||
  fail "HOME SurfaceView did not publish a new direct V80-512 frame after CALL"
PID_RETURN="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
[[ -n "$PID_RETURN" ]] || fail "process died returning HOME"
adb exec-out screencap -p > real-candidate-home-return.png || fail "HOME-return screenshot failed"
dump_ui_with_retry /sdcard/celine-real-return.xml real-candidate-home-return.xml HOME_RETURN
grep -q 'Mit Celin' real-candidate-home-return.xml || fail "HOME did not recover"
grep -q 'Celin 3D Ansicht' real-candidate-home-return.xml || fail "HOME-return 3D stage missing"
python3 ci/check-real-celine-render.py real-candidate-home-return.png HOME_RETURN
python3 ci/check-celine-person-presence.py real-candidate-home-return.png HOME_RETURN
python3 ci/check-home-return-zoom.py real-candidate-home.png real-candidate-home-return.png

adb logcat -d > real-candidate-logcat-final.txt
if grep -Eq 'V80-499|V75-199|V39-158|V39-159|V61-102|V61-199|V76-298|V76-299|V62-298|V62-299|REN-399|FATAL EXCEPTION|SIGABRT' real-candidate-logcat-final.txt; then
  fail "runtime error detected across HOME/CALL lifecycle"
fi
if ! grep -q 'V76-210' real-candidate-logcat-final.txt; then
  fail "v76 morph runtime activation evidence missing after lifecycle"
fi
if ! grep -q 'V75-160' real-candidate-logcat-final.txt; then
  fail "v75 semantic material ownership evidence missing after lifecycle"
fi
for marker in V80-400 V80-410 V80-420; do
  grep -q "$marker" real-candidate-logcat-final.txt || fail "v80 central-owner lifecycle marker missing: $marker"
done
grep -q 'target=CALL eased=true snap=false' real-candidate-logcat-final.txt || fail "eased CALL entry marker missing"
grep -q 'target=HOME eased=true snap=false' real-candidate-logcat-final.txt || fail "eased HOME return marker missing"

printf 'PASS packaged v79 blink-localized facial rig + v75 semantic material + v80 central layered HOME/CALL owner: bytes=%s sha=%s pid_home=%s pid_call=%s pid_return=%s\n' "$CANDIDATE_BYTES" "$CANDIDATE_SHA" "$PID" "$PID_CALL" "$PID_RETURN"
