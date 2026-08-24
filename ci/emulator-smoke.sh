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

# Updater must no longer occupy HOME.
if grep -qi 'Update prüfen' emulator-window.xml; then
  cat emulator-window.xml
  fail_with_log "Update button is still visible on HOME instead of settings"
fi

if ! grep -q 'Mit Celin' emulator-window.xml; then
  cat emulator-window.xml
  fail_with_log "Videochat entry button was not found in the rendered UI"
fi

# v50 UX gate: the avatar may not consume the whole HOME screen anymore. The composer must be a
# real, fully visible control above the videochat button so the conversation remains usable.
python3 - <<'PY' || exit 17
import re
import sys
import xml.etree.ElementTree as ET

root = ET.parse('emulator-window.xml').getroot()
pat = re.compile(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]')

def bounds(node):
    m = pat.fullmatch(node.attrib.get('bounds', ''))
    return tuple(map(int, m.groups())) if m else None

def find(desc=None, text_contains=None):
    for node in root.iter('node'):
        if desc is not None and node.attrib.get('content-desc', '') == desc:
            return node
        if text_contains is not None and text_contains in node.attrib.get('text', ''):
            return node
    return None

root_bounds = bounds(root.find('node')) if root.find('node') is not None else None
stage = find(desc='Celin 3D Ansicht')
composer = find(desc='Celin Nachricht schreiben')
video = find(text_contains='Mit Celin')
if not root_bounds or not stage or not composer or not video:
    print('V50 layout markers missing', file=sys.stderr)
    sys.exit(1)

rb, sb, cb, vb = root_bounds, bounds(stage), bounds(composer), bounds(video)
if not sb or not cb or not vb:
    print('V50 layout bounds missing', file=sys.stderr)
    sys.exit(1)

screen_h = rb[3] - rb[1]
stage_h = sb[3] - sb[1]
if stage_h > int(screen_h * 0.47):
    print(f'HOME avatar stage too tall: {stage_h}/{screen_h}', file=sys.stderr)
    sys.exit(1)
if cb[3] <= cb[1] or cb[3] > screen_h:
    print(f'HOME composer not fully visible: {cb}', file=sys.stderr)
    sys.exit(1)
if cb[3] >= vb[1]:
    print(f'HOME composer overlaps videochat button: composer={cb} video={vb}', file=sys.stderr)
    sys.exit(1)

print(f'HOME layout OK: stage={sb}, composer={cb}, video={vb}, screenH={screen_h}')
PY
if [[ "$?" -ne 0 ]]; then
  cat emulator-window.xml
  fail_with_log "HOME composer/layout visibility gate failed"
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

# The same avatar stage must grow into the call slot instead of retaining HOME's compact height.
python3 - <<'PY' || exit 18
import re
import sys
import xml.etree.ElementTree as ET
root = ET.parse('emulator-call.xml').getroot()
pat = re.compile(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]')

def b(node):
    m = pat.fullmatch(node.attrib.get('bounds', ''))
    return tuple(map(int, m.groups())) if m else None

nodes = list(root.iter('node'))
root_node = root.find('node')
stage = next((n for n in nodes if n.attrib.get('content-desc', '') == 'Celin 3D Ansicht'), None)
if root_node is None or stage is None or b(root_node) is None or b(stage) is None:
    print('CALL stage layout marker missing', file=sys.stderr)
    sys.exit(1)
rb, sb = b(root_node), b(stage)
screen_h = rb[3] - rb[1]
stage_h = sb[3] - sb[1]
if stage_h < int(screen_h * 0.45):
    print(f'CALL avatar stage did not fill call slot: {stage_h}/{screen_h}', file=sys.stderr)
    sys.exit(1)
print(f'CALL layout OK: stage={sb}, screenH={screen_h}')
PY
if [[ "$?" -ne 0 ]]; then
  cat emulator-call.xml
  fail_with_log "CALL stage layout gate failed"
fi

adb exec-out screencap -p > emulator-call.png
python3 ci/check-magenta-avatar.py emulator-call.png CALL || fail_with_log "CALL 3D avatar pixels missing"

echo "Avatar visibility smoke test passed with PID=$PID"
echo "Verified: HOME composer space + HOME avatar pixels + CALL stage fill + CALL avatar pixels + updater only in settings"
