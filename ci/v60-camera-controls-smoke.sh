#!/usr/bin/env bash
set -euo pipefail

PACKAGE="de.yahya.ai"
SRC="app/src/main/java/de/yahya/ai/Celine3DView.java"
HOME_XML="emulator-home-return.xml"
DIAG="emulator-v60-camera-diagnostics.xml"

fail_camera() {
  echo "V60 CAMERA ERROR: $*"
  adb shell "run-as $PACKAGE cat shared_prefs/celine_3d_diagnostics.xml" 2>/dev/null || true
  adb logcat -d | grep -E 'de\.yahya\.ai|V60-|REN-|VIS-|CTL-' | tail -220 || true
  exit 1
}

[[ -f "$SRC" ]] || fail_camera "Celine3DView source missing"
grep -q 'new GestureDetector' "$SRC" || fail_camera "one-finger GestureDetector missing"
grep -q 'new ScaleGestureDetector' "$SRC" || fail_camera "pinch ScaleGestureDetector missing"
grep -q 'onDoubleTap' "$SRC" || fail_camera "double-tap reset handler missing"
grep -q 'CAMERA_PAN_X_MAX' "$SRC" || fail_camera "bounded horizontal pan missing"
grep -q 'CAMERA_PAN_Y_MAX' "$SRC" || fail_camera "bounded vertical pan missing"
grep -q 'CAMERA_ZOOM_MIN' "$SRC" || fail_camera "minimum zoom clamp missing"
grep -q 'CAMERA_ZOOM_MAX' "$SRC" || fail_camera "maximum zoom clamp missing"
grep -q 'resetCameraSearch' "$SRC" || fail_camera "camera reset implementation missing"

PID="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
[[ -n "$PID" ]] || fail_camera "Yahya AI is not alive after HOME recovery"
[[ -f "$HOME_XML" ]] || fail_camera "HOME-return UI evidence missing"

read -r X1 Y1 X2 Y2 <<< "$(python3 - <<'PY'
import re, xml.etree.ElementTree as ET
root=ET.parse('emulator-home-return.xml').getroot()
pat=re.compile(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]')
for node in root.iter('node'):
    if node.attrib.get('content-desc','') != 'Celin 3D Ansicht':
        continue
    m=pat.fullmatch(node.attrib.get('bounds',''))
    if not m:
        continue
    x1,y1,x2,y2=map(int,m.groups())
    cx=(x1+x2)//2
    cy=(y1+y2)//2
    tx=min(x2-24,cx+160)
    if tx <= cx:
        tx=max(x1+24,cx-160)
    print(cx,cy,tx,cy)
    raise SystemExit(0)
raise SystemExit('Celin 3D Ansicht bounds missing')
PY
)"

[[ -n "${X1:-}" && -n "${Y1:-}" && -n "${X2:-}" && -n "${Y2:-}" ]] || fail_camera "could not resolve safe camera gesture coordinates"

echo "v60 camera swipe proof: $X1,$Y1 -> $X2,$Y2"
adb shell input swipe "$X1" "$Y1" "$X2" "$Y2" 550
sleep 1
adb shell "run-as $PACKAGE cat shared_prefs/celine_3d_diagnostics.xml" > "$DIAG" 2>/dev/null || fail_camera "could not read v60 camera diagnostics after swipe"
grep -q 'V60-119' "$DIAG" || fail_camera "bounded camera controls were not installed"
grep -q 'V60-120' "$DIAG" || fail_camera "real one-finger swipe did not change camera pan"

echo "v60 camera double-tap reset proof at $X1,$Y1"
adb shell "input tap $X1 $Y1; input tap $X1 $Y1"
sleep 1
adb shell "run-as $PACKAGE cat shared_prefs/celine_3d_diagnostics.xml" > "$DIAG" 2>/dev/null || fail_camera "could not read v60 camera diagnostics after reset"
grep -q 'V60-122' "$DIAG" || fail_camera "double-tap did not reset camera search"

# adb's input command does not provide reliable portable multi-touch pinch injection. The runtime
# pinch path is therefore contract-checked above and compiled in the APK; manual pinch remains part
# of the real-device acceptance check together with the private imported Celine GLB.
echo "v60 bounded camera controls passed: drag executed, reset executed, pinch contract compiled"
