#!/usr/bin/env bash
set -euo pipefail

PACKAGE="de.yahya.ai"
ACTIVITY="de.yahya.ai/.MainActivity"
MARKER="keyboardproofv67"

fail() {
  echo "ERROR: $*"
  adb shell dumpsys input_method | tail -160 || true
  exit 1
}

dump_ui() {
  local local_file="$1"
  local remote_file="/sdcard/$local_file"
  for _ in $(seq 1 6); do
    if adb shell uiautomator dump "$remote_file" >/dev/null 2>&1 &&
       adb pull "$remote_file" "$local_file" >/dev/null 2>&1; then
      return 0
    fi
    if [[ -z "$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)" ]]; then
      fail "app process died while collecting $local_file"
    fi
    sleep 1
  done
  fail "could not collect $local_file"
}

MANIFEST="app/src/main/AndroidManifest.xml"
grep -q 'android:windowSoftInputMode="adjustResize|stateAlwaysHidden"' "$MANIFEST" ||
  fail "MainActivity does not use adjustResize"
if grep -q 'android:windowSoftInputMode="[^"]*adjustPan' "$MANIFEST"; then
  fail "legacy adjustPan is still active"
fi

adb shell input keyevent 4 || true
adb shell am start -W -n "$ACTIVITY" >/dev/null
sleep 2
PID="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
[[ -n "$PID" ]] || fail "app process missing before keyboard proof"

dump_ui emulator-keyboard-before.xml
read -r EDIT_X EDIT_Y <<< "$(python3 - <<'PY'
import re
import xml.etree.ElementTree as ET
root=ET.parse("emulator-keyboard-before.xml").getroot()
pat=re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")
for node in root.iter("node"):
    if node.attrib.get("class")!="android.widget.EditText":
        continue
    m=pat.fullmatch(node.attrib.get("bounds",""))
    if not m:
        continue
    x1,y1,x2,y2=map(int,m.groups())
    print((x1+x2)//2,(y1+y2)//2)
    raise SystemExit(0)
raise SystemExit("chat EditText not found")
PY
)"
[[ -n "${EDIT_X:-}" && -n "${EDIT_Y:-}" ]] || fail "composer coordinates missing"

adb shell input tap "$EDIT_X" "$EDIT_Y"
sleep 1
adb shell input text "$MARKER"
sleep 2
adb shell dumpsys input_method > emulator-ime.txt
adb shell dumpsys window > emulator-window-state.txt
if ! grep -Eq 'mInputShown=true|mIsInputViewShown=true|isInputViewShown=true|inputShown=true' emulator-ime.txt; then
  fail "software keyboard was not confirmed as visible"
fi

dump_ui emulator-keyboard.xml
adb exec-out screencap -p > emulator-keyboard.png
SCREEN_HEIGHT="$(adb shell wm size | sed -nE 's/.*Physical size: [0-9]+x([0-9]+).*/\1/p' | tail -1 | tr -d '\r')"
[[ "$SCREEN_HEIGHT" =~ ^[0-9]+$ ]] || fail "physical screen height unavailable"
export SCREEN_HEIGHT MARKER

python3 - <<'PY'
import os
import re
import xml.etree.ElementTree as ET

root=ET.parse("emulator-keyboard.xml").getroot()
pat=re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")
screen_h=int(os.environ["SCREEN_HEIGHT"])
marker=os.environ["MARKER"]

def bounds(node):
    m=pat.fullmatch(node.attrib.get("bounds",""))
    return tuple(map(int,m.groups())) if m else None

nodes=list(root.iter("node"))
edit=next((n for n in nodes if n.attrib.get("class")=="android.widget.EditText" and marker in n.attrib.get("text","")),None)
if edit is None or bounds(edit) is None:
    raise SystemExit("typed marker is not visible in the real chat composer")
eb=bounds(edit)
if eb[3]<=eb[1] or eb[3]>int(screen_h*.80):
    raise SystemExit(f"composer is not safely above the keyboard: composer={eb} screenH={screen_h}")

buttons=[(n,bounds(n)) for n in nodes if n.attrib.get("class")=="android.widget.Button" and bounds(n)]
send=next(((n,b) for n,b in buttons if b[0]>=eb[2]-4 and abs(b[3]-eb[3])<100),None)
if send is None:
    raise SystemExit(f"visible send control not found beside composer={eb}")
sb=send[1]
if sb[3]>int(screen_h*.80):
    raise SystemExit(f"send control is not safely above the keyboard: send={sb} screenH={screen_h}")

profile_visible=any(n.attrib.get("content-desc")=="Celin 3D Ansicht" and n.attrib.get("visible-to-user","true")=="true" for n in nodes)
if profile_visible:
    raise SystemExit("large avatar presentation was not compacted while typing")

print(f"keyboard-open layout OK: composer={eb} send={sb} screenH={screen_h}")
PY

adb shell input keyevent 4
sleep 2
dump_ui emulator-keyboard-restored.xml
adb exec-out screencap -p > emulator-keyboard-restored.png
python3 - <<'PY'
import xml.etree.ElementTree as ET
root=ET.parse("emulator-keyboard-restored.xml").getroot()
nodes=list(root.iter("node"))
if not any(n.attrib.get("content-desc")=="Celin 3D Ansicht" for n in nodes):
    raise SystemExit("HOME avatar presentation did not return after closing keyboard")
if not any(n.attrib.get("class")=="android.widget.Button" and "Mit Celin" in n.attrib.get("text","") for n in nodes):
    raise SystemExit("HOME interaction control did not return after closing keyboard")
print("keyboard-close HOME restoration OK")
PY

echo "Keyboard chat visibility proof passed with PID=$PID"
