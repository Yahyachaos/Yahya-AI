#!/usr/bin/env bash
set -euo pipefail

PACKAGE="de.yahya.ai"
MARKER="celine-ci-camera-zoom-v70"

fail() {
  echo "ZOOM ERROR: $*" >&2
  adb logcat -d | grep -E 'de\.yahya\.ai|V80-21|V70-14|V70-15|V60-12|REN-|FATAL EXCEPTION|SIGABRT' | tail -260 || true
  exit 1
}

wait_text() {
  local text="$1" remote="$2" local_file="$3"
  for _ in $(seq 1 24); do
    adb shell uiautomator dump "$remote" >/dev/null 2>&1 || true
    adb pull "$remote" "$local_file" >/dev/null 2>&1 || true
    grep -Fq "$text" "$local_file" 2>/dev/null && return 0
    sleep 0.5
  done
  fail "timed out waiting for UI text: $text"
}

center_for() {
  local file="$1" needle="$2"
  python3 - "$file" "$needle" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot(); needle=sys.argv[2]
for node in root.iter('node'):
    if needle not in node.attrib.get('text',''): continue
    match=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds',''))
    if not match: continue
    x1,y1,x2,y2=map(int,match.groups()); print((x1+x2)//2,(y1+y2)//2); raise SystemExit
raise SystemExit(2)
PY
}

set_zoom() {
  local requested="$1"
  local expected="${2:-$1}"
  adb shell "run-as $PACKAGE sh -c 'printf %s $requested > files/$MARKER.tmp && mv files/$MARKER.tmp files/$MARKER'" \
    || fail "could not write private zoom marker $requested"
  for _ in $(seq 1 30); do
    if adb logcat -d | grep -F 'V70-141' | grep -F "requested=$requested" | grep -Fq "zoom=$expected"; then
      sleep 2
      return 0
    fi
    sleep 1
  done
  fail "runtime did not consume zoom marker requested=$requested expected=$expected"
}

capture_zoom() {
  local value="$1"
  local name="$2"
  set_zoom "$value"
  adb exec-out screencap -p > "$name"
  python3 ci/check-real-celine-render.py "$name" "CALL_ZOOM_$value"
  python3 ci/check-celine-person-presence.py "$name" "CALL_ZOOM_$value"
}

PID_HOME="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r ' || true)"
[[ -n "$PID_HOME" ]] || fail "Yahya AI is not alive before camera proof"

# The preceding candidate lifecycle proof returns to HOME. Re-enter the real CALL surface and drive
# only the private debug marker that feeds the same cameraZoom field as production pinch.
wait_text "Mit Celin" /sdcard/celine-v80-zoom-home.xml real-candidate-zoom-home.xml
read -r CALL_X CALL_Y <<< "$(center_for real-candidate-zoom-home.xml "Mit Celin")"
[[ -n "${CALL_X:-}" && -n "${CALL_Y:-}" ]] || fail "CALL entry coordinates missing"
adb shell input tap "$CALL_X" "$CALL_Y"
wait_text "Live mit Celin" /sdcard/celine-v80-zoom-call.xml real-candidate-zoom-call.xml
sleep 4
grep -Fq 'V80-210' <(adb logcat -d) || fail "v80 default CALL framing did not activate"

# These are real camera/dolly checkpoints, not avatar/root scaling. The previous 1.45/1.75/2.10
# sequence was manually rejected because it still showed full-body/torso framing. Keep the corrected
# vertical focus curve and exercise a wider bounded range for normal video-call, head/shoulders and
# true face-close inspection.
capture_zoom "2.8" real-candidate-zoom-call-normal.png
capture_zoom "3.5" real-candidate-zoom-call-head.png
capture_zoom "4.6" real-candidate-zoom-call-face.png
python3 ci/check-camera-zoom-range.py \
  real-candidate-zoom-call-normal.png \
  real-candidate-zoom-call-head.png \
  real-candidate-zoom-call-face.png

# A clearly unsafe request must clamp to the measured v80 bound. Then return to normal CALL and
# Android back must restore a visible HOME camera in the same process.
set_zoom "2.8"
set_zoom "9.0" "4.6"
set_zoom "2.8"
adb shell input keyevent 4
wait_text "Mit Celin" /sdcard/celine-v80-zoom-return.xml real-candidate-zoom-home-return.xml
sleep 2
adb exec-out screencap -p > real-candidate-zoom-home-return.png
python3 ci/check-real-celine-render.py real-candidate-zoom-home-return.png ZOOM_HOME_RETURN
python3 ci/check-celine-person-presence.py real-candidate-zoom-home-return.png ZOOM_HOME_RETURN

PID_RETURN="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r ' || true)"
[[ "$PID_RETURN" = "$PID_HOME" ]] || fail "process restarted during CALL camera proof: home=$PID_HOME return=$PID_RETURN"
adb logcat -d > real-candidate-zoom-logcat.txt

for marker in V70-150 V80-210 V80-211; do
  grep -q "$marker" real-candidate-zoom-logcat.txt || fail "camera marker missing: $marker"
done
for checkpoint in 'requested=2.8 zoom=2.8' 'requested=3.5 zoom=3.5' 'requested=4.6 zoom=4.6' 'requested=9.0 zoom=4.6'; do
  requested="${checkpoint%% *}"
  zoom="${checkpoint##* }"
  grep -F 'V70-141' real-candidate-zoom-logcat.txt | grep -F "$requested" | grep -Fq "$zoom" \
    || fail "zoom checkpoint missing: $checkpoint"
done
if grep -Eq 'V70-148|V70-149|REN-399|FATAL EXCEPTION|SIGABRT' real-candidate-zoom-logcat.txt; then
  fail "runtime error detected during v80 CALL camera proof"
fi

printf 'PASS v80 production CALL camera checkpoints: normal 2.80 -> head 3.50 -> face 4.60 -> HOME reset, same pid=%s; manual visual acceptance still required\n' "$PID_HOME"
