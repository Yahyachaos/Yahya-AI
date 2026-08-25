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

ime_visible() {
  grep -Eq 'mInputShown=true|mDecorViewVisible=true|mWindowVisible=true' emulator-ime.txt
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
KEYBOARD_READY=false
for _ in $(seq 1 8); do
  sleep 1
  adb shell dumpsys input_method > emulator-ime.txt
  dump_ui emulator-keyboard-ready.xml
  if ime_visible &&
     python3 - <<'PY'
import xml.etree.ElementTree as ET
nodes=list(ET.parse("emulator-keyboard-ready.xml").getroot().iter("node"))
edit=next((n for n in nodes if n.attrib.get("class")=="android.widget.EditText" and n.attrib.get("content-desc")=="Celin Nachricht schreiben"),None)
stage=next((n for n in nodes if n.attrib.get("content-desc")=="Celin 3D Ansicht"),None)
raise SystemExit(0 if edit is not None and edit.attrib.get("focused")=="true" and stage is None else 1)
PY
  then
    KEYBOARD_READY=true
    break
  fi
done
if [[ "$KEYBOARD_READY" != "true" ]]; then
  fail "composer did not remain focused after keyboard layout settled"
fi

adb shell input text "$MARKER"
sleep 2
adb shell dumpsys input_method > emulator-ime.txt
adb shell dumpsys window > emulator-window-state.txt
if ! ime_visible; then
  fail "software keyboard was not confirmed as visible after typing"
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
edit=next((n for n in nodes if n.attrib.get("class")=="android.widget.EditText" and marker in n.attrib.get("text","") and n.attrib.get("focused")=="true"),None)
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
import re
import xml.etree.ElementTree as ET

pat=re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")

def read(path):
    return list(ET.parse(path).getroot().iter("node"))

def bounds(node):
    m=pat.fullmatch(node.attrib.get("bounds",""))
    return tuple(map(int,m.groups())) if m else None

def markers(nodes):
    stage=next((n for n in nodes if n.attrib.get("content-desc")=="Celin 3D Ansicht"),None)
    composer=next((n for n in nodes if n.attrib.get("class")=="android.widget.EditText" and n.attrib.get("content-desc")=="Celin Nachricht schreiben"),None)
    video=next((n for n in nodes if n.attrib.get("class")=="android.widget.Button" and "Mit Celin" in n.attrib.get("text","")),None)
    if stage is None or composer is None or video is None:
        raise SystemExit("HOME avatar/composer/videochat marker missing after keyboard lifecycle")
    return {"stage":bounds(stage),"composer":bounds(composer),"videochat":bounds(video)}

before=markers(read("emulator-keyboard-before.xml"))
restored_nodes=read("emulator-keyboard-restored.xml")
restored=markers(restored_nodes)
if before!=restored:
    raise SystemExit(f"HOME geometry shifted across keyboard lifecycle: before={before} restored={restored}")
composer=next((n for n in restored_nodes if n.attrib.get("class")=="android.widget.EditText" and n.attrib.get("content-desc")=="Celin Nachricht schreiben"),None)
if composer is None or composer.attrib.get("focused")!="true":
    raise SystemExit("HOME composer no longer retains focus after IME close; CALL inheritance regression setup not reproduced")
print(f"keyboard-close HOME restoration exact with retained focus: {restored}")
PY

# v70 regression gate: enter CALL from the exact HOME state where the composer is still focused
# after the IME was dismissed. CALL must explicitly clear that inherited focus and keep the IME
# hidden so the video stage, caption and call controls retain full-height geometry.
read -r VIDEO_X VIDEO_Y <<< "$(python3 - <<'PY'
import re
import xml.etree.ElementTree as ET
root=ET.parse("emulator-keyboard-restored.xml").getroot()
pat=re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")
for node in root.iter("node"):
    if node.attrib.get("class")!="android.widget.Button" or "Mit Celin" not in node.attrib.get("text",""):
        continue
    m=pat.fullmatch(node.attrib.get("bounds",""))
    if not m:
        continue
    x1,y1,x2,y2=map(int,m.groups())
    print((x1+x2)//2,(y1+y2)//2)
    raise SystemExit(0)
raise SystemExit("videochat button not found after HOME keyboard restore")
PY
)"
[[ -n "${VIDEO_X:-}" && -n "${VIDEO_Y:-}" ]] || fail "videochat coordinates missing after HOME keyboard restore"

adb shell input tap "$VIDEO_X" "$VIDEO_Y"
sleep 4
PID_CALL="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
[[ -n "$PID_CALL" ]] || fail "app process died entering CALL from retained HOME focus"

dump_ui emulator-call.xml
adb exec-out screencap -p > emulator-call.png
adb shell dumpsys input_method > emulator-ime.txt
adb shell dumpsys window > emulator-window-state.txt
if ime_visible; then
  fail "software keyboard remained/reopened in CALL after inherited HOME focus"
fi

python3 - <<'PY'
import os
import re
import xml.etree.ElementTree as ET

root=ET.parse("emulator-call.xml").getroot()
pat=re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")
screen_h=int(os.environ["SCREEN_HEIGHT"])

def bounds(node):
    m=pat.fullmatch(node.attrib.get("bounds",""))
    return tuple(map(int,m.groups())) if m else None

nodes=list(root.iter("node"))
content=next((n for n in nodes if n.attrib.get("resource-id")=="android:id/content"),None)
live=next((n for n in nodes if "Live mit Celin" in n.attrib.get("text","") and n.attrib.get("class")=="android.widget.TextView"),None)
stage=next((n for n in nodes if n.attrib.get("content-desc")=="Celin 3D Ansicht"),None)
caption=next((n for n in nodes if "Sag einfach etwas" in n.attrib.get("text","")),None)
mic=next((n for n in nodes if n.attrib.get("class")=="android.widget.Button" and "Mikrofon" in n.attrib.get("text","")),None)
end=next((n for n in nodes if n.attrib.get("class")=="android.widget.Button" and "Auflegen" in n.attrib.get("text","")),None)
home_edit=next((n for n in nodes if n.attrib.get("class")=="android.widget.EditText" and n.attrib.get("content-desc")=="Celin Nachricht schreiben"),None)
required={"content":content,"live":live,"stage":stage,"caption":caption,"mic":mic,"end":end}
missing=[name for name,node in required.items() if node is None or bounds(node) is None]
if missing:
    raise SystemExit(f"CALL markers missing after keyboard transition: {missing}")
cb=bounds(content); sb=bounds(stage); capb=bounds(caption); mb=bounds(mic); eb=bounds(end)
if cb[3] < int(screen_h*.85):
    raise SystemExit(f"CALL content is still IME-compressed: content={cb} screenH={screen_h}")
if sb[3]-sb[1] < int(screen_h*.45):
    raise SystemExit(f"CALL avatar stage is too short after HOME keyboard transition: stage={sb} screenH={screen_h}")
if capb[3] > cb[3] or mb[3] > cb[3] or eb[3] > cb[3]:
    raise SystemExit(f"CALL caption/controls extend outside visible content: content={cb} caption={capb} mic={mb} end={eb}")
if home_edit is not None and home_edit.attrib.get("focused")=="true":
    raise SystemExit("HOME composer still owns focus underneath CALL")
print(f"CALL keyboard inheritance fixed: content={cb} stage={sb} caption={capb} mic={mb} end={eb}")
PY

python3 ci/check-magenta-avatar.py emulator-call.png CALL_KEYBOARD_TRANSITION || fail "CALL avatar pixels missing after HOME keyboard transition"

adb shell input keyevent 4
sleep 3
dump_ui emulator-home-return.xml
adb exec-out screencap -p > emulator-home-return.png
adb shell dumpsys input_method > emulator-ime.txt
if ime_visible; then
  fail "software keyboard reopened after returning HOME from CALL"
fi
python3 - <<'PY'
import re
import xml.etree.ElementTree as ET
pat=re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")

def read(path): return list(ET.parse(path).getroot().iter("node"))
def b(n):
    m=pat.fullmatch(n.attrib.get("bounds",""))
    return tuple(map(int,m.groups())) if m else None

def markers(nodes):
    stage=next((n for n in nodes if n.attrib.get("content-desc")=="Celin 3D Ansicht"),None)
    composer=next((n for n in nodes if n.attrib.get("class")=="android.widget.EditText" and n.attrib.get("content-desc")=="Celin Nachricht schreiben"),None)
    video=next((n for n in nodes if n.attrib.get("class")=="android.widget.Button" and "Mit Celin" in n.attrib.get("text","")),None)
    if stage is None or composer is None or video is None:
        raise SystemExit("HOME markers missing after CALL keyboard-transition return")
    return {"stage":b(stage),"composer":b(composer),"videochat":b(video)}, composer
before,_=markers(read("emulator-keyboard-before.xml"))
after,composer=markers(read("emulator-home-return.xml"))
if before!=after:
    raise SystemExit(f"HOME geometry changed after CALL keyboard transition: before={before} after={after}")
if composer.attrib.get("focused")=="true":
    raise SystemExit("HOME composer focus leaked back after CALL return")
print(f"CALL return preserved HOME geometry with IME hidden: {after}")
PY
python3 ci/check-magenta-avatar.py emulator-home-return.png HOME_RETURN_KEYBOARD_TRANSITION || fail "HOME-return avatar pixels missing after CALL keyboard transition"

echo "Keyboard chat + CALL inherited-focus visibility proof passed with PID=$PID_CALL"
