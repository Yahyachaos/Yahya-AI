#!/usr/bin/env bash
set -euo pipefail

APK="ci-apk/app-debug.apk"
PACKAGE="de.yahya.ai"
ACTIVITY="de.yahya.ai/.MainActivity"
FIXTURE="ci/celine-smoke.glb"

collect_evidence() {
  adb logcat -d > emulator-logcat.txt 2>/dev/null || true
  adb shell "run-as $PACKAGE cat shared_prefs/yahya_ai.xml" > emulator-prefs.xml 2>/dev/null || true
}
trap collect_evidence EXIT

if [[ ! -f "$APK" ]]; then
  echo "Missing APK: $APK"
  exit 1
fi

python3 ci/generate-celine-smoke-glb.py "$FIXTURE"
if [[ ! -s "$FIXTURE" ]]; then
  echo "Synthetic Filament fixture was not generated"
  exit 1
fi

adb install -r "$APK"
adb shell am force-stop "$PACKAGE" || true

# Put a deterministic, non-private GLB into the same internal path used by the real Meshy model.
# This is done after installation and before MainActivity starts, so V39/V43 and Celine3DView see
# a real 3D asset rather than silently exercising the 2D fallback.
cat "$FIXTURE" | adb shell "run-as $PACKAGE sh -c 'mkdir -p files/models; cat > files/models/celine.glb'"
REMOTE_BYTES="$(adb shell "run-as $PACKAGE sh -c 'wc -c < files/models/celine.glb'" | tr -d '\r ' || true)"
if [[ -z "$REMOTE_BYTES" || "$REMOTE_BYTES" -lt 100000 ]]; then
  echo "CI 3D model was not installed into app-private storage (bytes=$REMOTE_BYTES)"
  exit 1
fi

echo "Injected synthetic 3D model: $REMOTE_BYTES bytes"
adb shell pm grant "$PACKAGE" android.permission.RECORD_AUDIO || true
adb shell am start -W -n "$ACTIVITY"
sleep 12

PID="$(adb shell pidof "$PACKAGE" | tr -d '\r')"
if [[ -z "$PID" ]]; then
  echo "Yahya AI process is not running"
  exit 1
fi

if ! adb shell dumpsys activity activities | grep -q "$ACTIVITY"; then
  echo "MainActivity is not the active Yahya AI activity"
  adb shell dumpsys activity activities | grep -A4 -B4 "$PACKAGE" || true
  exit 1
fi

adb exec-out screencap -p > emulator-home.png
adb shell uiautomator dump /sdcard/yahya-window.xml >/dev/null
adb pull /sdcard/yahya-window.xml emulator-window.xml >/dev/null

if ! grep -q 'Update prüfen' emulator-window.xml; then
  echo "Update button was not found in the rendered UI"
  cat emulator-window.xml
  exit 1
fi

if ! grep -q 'Mit Celin' emulator-window.xml; then
  echo "Videochat entry button was not found in the rendered UI"
  cat emulator-window.xml
  exit 1
fi

# This is the key regression gate: the screenshot itself must contain pixels from the Filament
# avatar fixture. A visible room or a running Activity is no longer enough for a green build.
python3 ci/check-magenta-avatar.py emulator-home.png HOME

read -r TAP_X TAP_Y <<< "$(python3 - <<'PY'
import re
import xml.etree.ElementTree as ET
root = ET.parse('emulator-window.xml').getroot()
for node in root.iter('node'):
    text = node.attrib.get('text', '')
    if 'Mit Celin' not in text:
        continue
    m = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds', ''))
    if not m:
        continue
    x1, y1, x2, y2 = map(int, m.groups())
    print((x1+x2)//2, (y1+y2)//2)
    raise SystemExit(0)
raise SystemExit('Could not resolve videochat button bounds')
PY
)"

echo "Opening videochat at $TAP_X,$TAP_Y"
adb shell input tap "$TAP_X" "$TAP_Y"
sleep 6

adb shell uiautomator dump /sdcard/yahya-call.xml >/dev/null
adb pull /sdcard/yahya-call.xml emulator-call.xml >/dev/null
if ! grep -q 'Live mit Celin' emulator-call.xml; then
  echo "Live videochat overlay did not open"
  cat emulator-call.xml
  exit 1
fi

adb exec-out screencap -p > emulator-call.png
python3 ci/check-magenta-avatar.py emulator-call.png CALL

echo "Avatar visibility smoke test passed with PID=$PID"
echo "Verified: real Filament model + HOME avatar pixels + CALL avatar pixels + updater + videochat"
