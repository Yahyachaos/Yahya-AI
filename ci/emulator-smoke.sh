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

fail_with_log() {
  echo "ERROR: $*"
  adb logcat -d | grep -E 'de\.yahya\.ai|Filament|gltfio|FATAL EXCEPTION|SIGABRT|V50-|V49-|V43-|V39-|REN-|CTL-' | tail -220 || true
  exit 1
}

if [[ ! -f "$APK" ]]; then
  fail_with_log "Missing APK: $APK"
fi

python3 ci/generate-celine-smoke-glb.py "$FIXTURE"
if [[ ! -s "$FIXTURE" ]]; then
  fail_with_log "Synthetic Filament fixture was not generated"
fi

adb install -r "$APK"
adb shell am force-stop "$PACKAGE" || true

# Put a deterministic, non-private GLB into the exact private path used by the real Meshy model.
cat "$FIXTURE" | adb shell "run-as $PACKAGE sh -c 'mkdir -p files/models; cat > files/models/celine.glb'"
REMOTE_BYTES="$(adb shell "run-as $PACKAGE sh -c 'wc -c < files/models/celine.glb'" | tr -d '\r ' || true)"
if [[ -z "$REMOTE_BYTES" || "$REMOTE_BYTES" -lt 100000 ]]; then
  fail_with_log "CI 3D model was not installed into app-private storage (bytes=$REMOTE_BYTES)"
fi

echo "Injected synthetic 3D model: $REMOTE_BYTES bytes"
adb shell pm grant "$PACKAGE" android.permission.RECORD_AUDIO || true
adb shell am start -W -n "$ACTIVITY"
sleep 12

PID="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
if [[ -z "$PID" ]]; then
  fail_with_log "Yahya AI process died before HOME visibility proof"
fi

echo "HOME process alive: PID=$PID"
if ! adb shell dumpsys activity activities | grep -q "$ACTIVITY"; then
  adb shell dumpsys activity activities | grep -A4 -B4 "$PACKAGE" || true
  fail_with_log "MainActivity is not the active Yahya AI activity"
fi

adb exec-out screencap -p > emulator-home.png
adb shell uiautomator dump /sdcard/yahya-window.xml >/dev/null || fail_with_log "HOME UI dump failed"
adb pull /sdcard/yahya-window.xml emulator-window.xml >/dev/null || fail_with_log "HOME UI pull failed"

# v50 UX gate: updater must no longer occupy the bottom of HOME.
if grep -qi 'Update prüfen' emulator-window.xml; then
  cat emulator-window.xml
  fail_with_log "Update button is still visible on HOME instead of settings"
fi

if ! grep -q 'Mit Celin' emulator-window.xml; then
  cat emulator-window.xml
  fail_with_log "Videochat entry button was not found in the rendered UI"
fi

# Critical gate: the screenshot itself must contain pixels from the real Filament fixture.
python3 ci/check-magenta-avatar.py emulator-home.png HOME || fail_with_log "HOME 3D avatar pixels missing"

# Verify the gear opens settings and exposes App & Updates + Update prüfen.
read -r GEAR_X GEAR_Y <<< "$(python3 - <<'PY'
import re
import xml.etree.ElementTree as ET
root = ET.parse('emulator-window.xml').getroot()
for node in root.iter('node'):
    text = node.attrib.get('text', '')
    desc = node.attrib.get('content-desc', '')
    if text != '⚙' and desc != 'Einstellungen':
        continue
    m = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds', ''))
    if not m:
        continue
    x1, y1, x2, y2 = map(int, m.groups())
    print((x1+x2)//2, (y1+y2)//2)
    raise SystemExit(0)
raise SystemExit('Could not resolve settings gear bounds')
PY
)"
if [[ -z "${GEAR_X:-}" || -z "${GEAR_Y:-}" ]]; then
  fail_with_log "Could not resolve settings gear coordinates"
fi
adb shell input tap "$GEAR_X" "$GEAR_Y"
sleep 1
adb shell uiautomator dump /sdcard/yahya-settings.xml >/dev/null || fail_with_log "Settings UI dump failed"
adb pull /sdcard/yahya-settings.xml emulator-settings.xml >/dev/null || fail_with_log "Settings UI pull failed"
adb exec-out screencap -p > emulator-settings.png
if ! grep -q 'App &amp; Updates\|App & Updates' emulator-settings.xml; then
  cat emulator-settings.xml
  fail_with_log "App & Updates section was not found behind the settings gear"
fi
if ! grep -qi 'Update prüfen' emulator-settings.xml; then
  cat emulator-settings.xml
  fail_with_log "Update prüfen was not found in settings"
fi
adb shell input keyevent 4
sleep 1

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

if [[ -z "${TAP_X:-}" || -z "${TAP_Y:-}" ]]; then
  fail_with_log "Could not resolve videochat button coordinates"
fi

echo "Opening videochat at $TAP_X,$TAP_Y"
adb shell input tap "$TAP_X" "$TAP_Y"
sleep 6

PID="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
if [[ -z "$PID" ]]; then
  fail_with_log "Yahya AI process died while opening CALL"
fi

adb shell uiautomator dump /sdcard/yahya-call.xml >/dev/null || fail_with_log "CALL UI dump failed"
adb pull /sdcard/yahya-call.xml emulator-call.xml >/dev/null || fail_with_log "CALL UI pull failed"
if ! grep -q 'Live mit Celin' emulator-call.xml; then
  cat emulator-call.xml
  fail_with_log "Live videochat overlay did not open"
fi

adb exec-out screencap -p > emulator-call.png
python3 ci/check-magenta-avatar.py emulator-call.png CALL || fail_with_log "CALL 3D avatar pixels missing"

echo "Avatar visibility smoke test passed with PID=$PID"
echo "Verified: HOME avatar pixels + CALL avatar pixels + updater only in settings + videochat"
