#!/usr/bin/env bash
set -euo pipefail

OUT="${1:-room-polish-proof}"
APK="${2:-ci-apk/app-debug.apk}"
PACKAGE="de.yahya.ai"
MAIN="de.yahya.ai/.MainActivity"
mkdir -p "$OUT"

fail() {
  echo "room visual polish proof ERROR: $*" >&2
  adb logcat -d | grep -E 'de\.yahya\.ai|FATAL EXCEPTION|SIGABRT|OutOfMemory|ROOM-|V80-|CTL-' | tail -300 || true
  exit 1
}

wait_log() {
  local needle="$1" label="$2"
  for _ in $(seq 1 75); do
    if adb logcat -d | grep -Fq "$needle"; then
      echo "Ready: $label"
      return 0
    fi
    sleep 1
  done
  fail "timed out waiting for $label ($needle)"
}

capture() {
  local name="$1" label="$2"
  adb exec-out screencap -p > "$OUT/$name.png"
  python3 ci/check-real-celine-render.py "$OUT/$name.png" "$label"
}

test -s "$APK" || fail "APK missing"
adb install -r "$APK" >/dev/null
adb shell pm clear "$PACKAGE" >/dev/null || fail "clear state"
adb shell am force-stop "$PACKAGE" || true
adb logcat -c || true
adb shell pm grant "$PACKAGE" android.permission.RECORD_AUDIO || true
adb shell am start -W -n "$MAIN" > "$OUT/activity-start.txt" || fail "HOME launch"

wait_log 'ROOM-100' 'modular room active'
wait_log 'ROOM-117' 'warm shell material polish active'
wait_log 'V80-410' 'HOME layered frame'
wait_log 'CTL-350' 'canonical Celine visible'
sleep 2

adb shell uiautomator dump /sdcard/room-polish-home.xml >/dev/null || fail "HOME UI dump"
adb pull /sdcard/room-polish-home.xml "$OUT/home.xml" >/dev/null || fail "HOME UI pull"
capture home ROOM_POLISH_HOME
grep -q 'Celin 3D Ansicht' "$OUT/home.xml" || fail "HOME 3D stage missing"
grep -q 'Mit Celin' "$OUT/home.xml" || fail "CALL entry missing"

read -r TAP_X TAP_Y <<< "$(python3 - "$OUT/home.xml" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for node in root.iter('node'):
    if 'Mit Celin' not in node.attrib.get('text', ''):
        continue
    match = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds', ''))
    if match:
        x1, y1, x2, y2 = map(int, match.groups())
        print((x1 + x2) // 2, (y1 + y2) // 2)
        raise SystemExit
raise SystemExit('CALL bounds missing')
PY
)"
[[ -n "${TAP_X:-}" && -n "${TAP_Y:-}" ]] || fail "CALL coordinates missing"
adb shell input tap "$TAP_X" "$TAP_Y"
wait_log 'target=CALL eased=true snap=false' 'HOME to CALL transition'
wait_log 'V80-420' 'CALL layered frame'
sleep 2
capture call ROOM_POLISH_CALL

adb shell input keyevent 4
wait_log 'target=HOME eased=true snap=false' 'CALL to HOME transition'
sleep 2
capture home-return ROOM_POLISH_HOME_RETURN

adb logcat -d -v threadtime > "$OUT/logcat.txt"
grep -Fq 'ROOM-117' "$OUT/logcat.txt" || fail "warm shell diagnostic absent"
grep -Fq 'ROOM-100' "$OUT/logcat.txt" || fail "room activation absent"
if grep -Eq 'ROOM-198|ROOM-199|V80-499|REN-399|FATAL EXCEPTION|SIGABRT|OutOfMemoryError' "$OUT/logcat.txt"; then
  fail "room/renderer/lifecycle failure detected"
fi

cat > "$OUT/summary.txt" <<'EOF'
STRUCTURAL_PASS targeted v80 room visual polish proof
EXPECTED_VISUAL=warm_beige_walls_and_ceiling_warmer_wood_floor
PROTECTED=canonical_Celine_camera_room_root_furniture_transforms_navigation_lamp
CELINE_PRESENCE=CTL-350_plus_manual_image_review
VISUAL_ACCEPTANCE=manual_review_required
EOF

(cd "$OUT" && sha256sum \
  home.png call.png home-return.png logcat.txt runtime-source.txt apk.sha256 summary.txt \
  > evidence-sha256.txt)

echo "PASS: room visual polish runtime captured; manual image review still required."
