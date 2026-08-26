#!/usr/bin/env bash
set -euo pipefail

APK="${1:-ci-apk/app-debug.apk}"
OUT="${2:-avatar-lab-proof}"
mkdir -p "$OUT"

if [ ! -f "$APK" ]; then
  echo "APK not found: $APK" >&2
  exit 1
fi

adb install -r "$APK" >/dev/null
adb shell am force-stop de.yahya.ai
adb shell am start -W -n de.yahya.ai/.CelineAvatarLabActivity >/dev/null
sleep 2

capture() {
  local name="$1"
  adb exec-out screencap -p > "$OUT/${name}.png"
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml "$OUT/${name}.xml" >/dev/null 2>&1 || true
}

tap_text() {
  local wanted="$1"
  adb shell uiautomator dump /sdcard/lab.xml >/dev/null 2>&1
  adb pull /sdcard/lab.xml "$OUT/lab.xml" >/dev/null 2>&1
  local coords
  coords="$(python3 - "$OUT/lab.xml" "$wanted" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

path, wanted = sys.argv[1:]
root = ET.parse(path).getroot()
for node in root.iter('node'):
    if (node.attrib.get('text') != wanted:
        continue
    bounds = node.attrib.get('bounds', '')
    match = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', bounds)
    if not match:
        continue
    x1, y1, x2, y2 = map(int, match.groups())
    print((x1 + x2) // 2, (y1 + y2) // 2)
    raise SystemExit(0)
raise SystemExit(f'button not found: {wanted}')
PY
)"
  local x y
  read -r x y <<<"$coords"
  adb shell input tap "$x" "$y"
  sleep 0.25
}

# Deterministic neutral close-up.
tap_text "Gesicht nah"
tap_text "Gesicht neutral"
capture "01-face-neutral-close"

# Slow blink: take one near-closed frame and then confirmed-open frame.
tap_text "Blink langsam"
sleep 0.32
capture "02-face-blink-near-closed"
sleep 0.85
capture "03-face-blink-open-after"

# Full-body standing and seated grounding.
tap_text "Ganzkörper"
tap_text "Stehen"
sleep 0.35
capture "04-standing-front"

tap_text "Sitzen"
sleep 0.35
capture "05-seated-front"

# Arm/hand movement: two frames separated in the same loop prove it is not frozen.
tap_text "Arme/Hände"
sleep 0.35
capture "06-arms-hands-a"
sleep 1.45
capture "07-arms-hands-b"

# Walk-in-place two frames.
tap_text "Laufen"
sleep 0.25
capture "08-walk-a"
sleep 0.72
capture "09-walk-b"

# Profile and three-quarter inspection of the same branch avatar.
tap_text "Stehen"
tap_text "Profil links"
sleep 0.3
capture "10-profile-left"
tap_text "3/4 rechts"
sleep 0.3
capture "11-three-quarter-right"
tap_text "Front"
sleep 0.2
capture "12-front-return"

adb logcat -d -v threadtime > "$OUT/logcat.txt" || true
printf '%s\n' \
  "Avatar Lab lightweight proof complete." \
  "APK=$APK" \
  "Screenshots=$(find "$OUT" -maxdepth 1 -name '*.png' | wc -l)" \
  > "$OUT/summary.txt"
cat "$OUT/summary.txt"
