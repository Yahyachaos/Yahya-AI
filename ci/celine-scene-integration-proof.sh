#!/usr/bin/env bash
set -euo pipefail

APK="${1:-ci-apk/app-debug.apk}"
OUT="${2:-avatar-lab-proof}"
PACKAGE="de.yahya.ai"
ACTIVITY="de.yahya.ai/.MainActivity"
CAPTURE_ACTIVITY="de.yahya.ai/.CelineAvatarLabCaptureActivity"
mkdir -p "$OUT"

fail() {
  echo "Scene proof ERROR: $*" >&2
  adb logcat -d | grep -E 'de\.yahya\.ai|FATAL EXCEPTION|REN-|V44-|V70-|V76-|V79-|V80-' | tail -260 || true
  exit 1
}

wait_log() {
  local needle="$1" label="$2"
  for _ in $(seq 1 30); do
    adb logcat -d | grep -q "$needle" && { echo "Scene ready: $label"; return 0; }
    sleep 1
  done
  fail "timed out waiting for $label ($needle)"
}

[[ -s "$APK" ]] || fail "missing APK: $APK"
adb install -r "$APK" >/dev/null
adb shell am force-stop "$PACKAGE" || true
adb logcat -c || true
adb shell pm grant "$PACKAGE" android.permission.RECORD_AUDIO || true
adb shell am start -W -n "$ACTIVITY" >/dev/null
wait_log 'CTL-350' 'visible HOME Celine'
wait_log 'V44-100' 'production room backdrop'
sleep 1.2

adb shell uiautomator dump /sdcard/celine-scene-home.xml >/dev/null || fail "HOME UI dump failed"
adb pull /sdcard/celine-scene-home.xml "$OUT/15-home-scene.xml" >/dev/null || fail "HOME UI pull failed"
adb exec-out screencap -p > "$OUT/15-home-scene.png"
python3 ci/check-real-celine-render.py "$OUT/15-home-scene.png" HOME
python3 ci/check-celine-person-presence.py "$OUT/15-home-scene.png" HOME

grep -q 'Celin 3D Ansicht' "$OUT/15-home-scene.xml" || fail "HOME 3D stage missing"
grep -q 'Mit Celin' "$OUT/15-home-scene.xml" || fail "HOME call entry missing"

read -r TAP_X TAP_Y <<< "$(python3 - "$OUT/15-home-scene.xml" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
for n in root.iter('node'):
    if 'Mit Celin' not in n.attrib.get('text',''): continue
    m=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.attrib.get('bounds',''))
    if not m: continue
    x1,y1,x2,y2=map(int,m.groups()); print((x1+x2)//2,(y1+y2)//2); raise SystemExit
raise SystemExit('call button bounds missing')
PY
)"
[[ -n "${TAP_X:-}" && -n "${TAP_Y:-}" ]] || fail "CALL tap coordinates missing"
adb shell input tap "$TAP_X" "$TAP_Y"
wait_log 'V80-420' 'central layered CALL seat entry'
sleep 1.5

adb shell uiautomator dump /sdcard/celine-scene-call.xml >/dev/null || fail "CALL UI dump failed"
adb pull /sdcard/celine-scene-call.xml "$OUT/16-call-scene.xml" >/dev/null || fail "CALL UI pull failed"
adb exec-out screencap -p > "$OUT/16-call-scene.png"
python3 ci/check-real-celine-render.py "$OUT/16-call-scene.png" CALL
python3 ci/check-celine-person-presence.py "$OUT/16-call-scene.png" CALL

grep -q 'Live mit Celin' "$OUT/16-call-scene.xml" || fail "CALL overlay missing"
grep -q 'Celin 3D Ansicht' "$OUT/16-call-scene.xml" || fail "CALL 3D stage missing"

# Block 7: capture the exact current production-owner CALL body/head state with a close diagnostic
# camera while driving only the guarded final-geometry blink morph. This supplements the real
# product HOME/CALL frames above; it does not scale or replace Celine and it does not use a
# synthetic model. The four states must later be manually inspected.
block7_launch() {
  local face="$1" restart="${2:-keep}"
  if [[ "$restart" == "restart" ]]; then adb shell am force-stop "$PACKAGE" || true; fi
  adb shell am start -W --activity-single-top -n "$CAPTURE_ACTIVITY" \
    --es ci_pose production_call --es ci_camera face --es ci_orbit front --es ci_face "$face" >/dev/null
  if [[ "$restart" == "restart" ]]; then sleep 1.8; else sleep 0.75; fi
}

block7_capture() {
  local face="$1" name="$2" restart="${3:-keep}" attempt
  for attempt in 1 2; do
    block7_launch "$face" "$restart"
    # Consume the software-emulator SurfaceView's previously latched frame after in-place updates.
    adb exec-out screencap -p >/dev/null || true
    sleep 0.45
    adb exec-out screencap -p > "$OUT/$name.png"
    if [[ -s "$OUT/$name.png" ]]; then
      echo "Block-7 evidence captured: $name face=$face attempt=$attempt"
      return 0
    fi
    restart=keep
  done
  fail "Block-7 evidence frame remained empty: $name"
}

block7_capture neutral 17-block7-blink-open restart
block7_capture blink85 18-block7-blink-partial
block7_capture blink100 19-block7-blink-closed
block7_capture neutral 20-block7-blink-reopen

timeout 15s adb logcat -d -v threadtime > "$OUT/scene-logcat.txt" 2>&1 || true
if grep -Eq 'REN-399|V76-299|V80-499|FATAL EXCEPTION|SIGABRT' "$OUT/scene-logcat.txt"; then
  fail "runtime error detected during targeted scene/blink proof"
fi
if ! grep -Fq 'V80-440' "$OUT/scene-logcat.txt" || ! grep -Fq 'stage=CALL' "$OUT/scene-logcat.txt"; then
  fail "Block-7 capture did not bind the central production CALL owner"
fi
if ! grep -Fq 'V76-210' "$OUT/scene-logcat.txt"; then
  fail "Block-7 capture did not bind the guarded final-geometry face morph runtime"
fi
if ! grep -Fq 'face=blink85' "$OUT/scene-logcat.txt" || ! grep -Fq 'face=blink100' "$OUT/scene-logcat.txt"; then
  fail "Block-7 partial/closed diagnostic morph states missing from logs"
fi

for frame in 17-block7-blink-open 18-block7-blink-partial 19-block7-blink-closed 20-block7-blink-reopen; do
  [[ -s "$OUT/$frame.png" ]] || fail "missing Block-7 evidence $frame"
done

echo "Targeted HOME/CALL plus Block-7 open/partial/closed/reopen evidence captured; manual visual acceptance is still required."
