#!/usr/bin/env bash
set -euo pipefail

PACKAGE="de.yahya.ai"
SRC="app/src/main/java/de/yahya/ai/Celine3DView.java"
HOME_XML="emulator-home-return.xml"
DIAG="emulator-v79-camera-diagnostics.xml"

fail_camera() {
  echo "V79 CAMERA ERROR: $*"
  adb shell "run-as $PACKAGE cat shared_prefs/celine_3d_diagnostics.xml" 2>/dev/null || true
  adb logcat -d | grep -E 'de\.yahya\.ai|V79-|V60-|REN-|VIS-|CTL-' | tail -220 || true
  exit 1
}

[[ -f "$SRC" ]] || fail_camera "Celine3DView source missing"
grep -q 'new GestureDetector' "$SRC" || fail_camera "gesture detector missing"
grep -q 'new ScaleGestureDetector' "$SRC" || fail_camera "pinch ScaleGestureDetector missing"
grep -q 'onDoubleTap' "$SRC" || fail_camera "double-tap reset handler missing"
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

[[ -n "${X1:-}" && -n "${Y1:-}" && -n "${X2:-}" && -n "${Y2:-}" ]] || fail_camera "could not resolve safe product gesture coordinates"

# v79 product semantics intentionally anchor Celine in HOME/CALL. A one-finger swipe here must
# NOT revive v60 free camera/actor pan; orbit remains available only inside Avatar Lab.
echo "v79 anchored product swipe proof: $X1,$Y1 -> $X2,$Y2"
adb shell input swipe "$X1" "$Y1" "$X2" "$Y2" 550
sleep 1
adb shell "run-as $PACKAGE cat shared_prefs/celine_3d_diagnostics.xml" > "$DIAG" 2>/dev/null || fail_camera "could not read v79 camera diagnostics after swipe"
grep -q 'V79-310' "$DIAG" || fail_camera "v79 product interaction owner was not installed"
if grep -q 'V60-120' "$DIAG"; then
  fail_camera "HOME/CALL one-finger swipe unexpectedly changed product camera pan"
fi

# Pinch cannot be injected portably by adb. Keep it fail-closed structurally: the production view
# still owns a ScaleGestureDetector and the v79 diagnostic must declare true-camera dolly semantics.
grep -q 'pinch=trueCameraDolly' "$DIAG" || fail_camera "v79 true-camera pinch dolly contract missing"

echo "v79 product camera controls passed: one-finger anchored, pinch true-camera dolly contract active; Avatar Lab owns orbit proof"
