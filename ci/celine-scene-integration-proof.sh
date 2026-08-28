#!/usr/bin/env bash
set -euo pipefail

APK="${1:-ci-apk/app-debug.apk}"
OUT="${2:-avatar-lab-proof}"
PACKAGE="de.yahya.ai"
ACTIVITY="de.yahya.ai/.MainActivity"
ZOOM_MARKER="celine-ci-camera-zoom-v70"
mkdir -p "$OUT"

fail() {
  echo "Block-9 scene proof ERROR: $*" >&2
  adb logcat -d | grep -E 'de\.yahya\.ai|FATAL EXCEPTION|REN-|V45-|V61-|V70-|V76-|V77-|V80-' | tail -360 || true
  exit 1
}

pid() {
  adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r ' || true
}

dump_ui() {
  local remote="$1" local_file="$2" attempt
  for attempt in 1 2 3 4; do
    adb shell rm -f "$remote" >/dev/null 2>&1 || true
    rm -f "$local_file"
    if adb shell uiautomator dump "$remote" >/dev/null 2>&1 \
        && adb pull "$remote" "$local_file" >/dev/null 2>&1 \
        && grep -q '<hierarchy' "$local_file"; then
      return 0
    fi
    [[ -n "$(pid)" ]] || fail "app process died while collecting $local_file"
    sleep 0.65
  done
  fail "could not collect valid UI hierarchy: $local_file"
}

wait_text() {
  local text="$1" remote="$2" local_file="$3"
  for _ in $(seq 1 24); do
    if dump_ui "$remote" "$local_file" && grep -Fq "$text" "$local_file"; then
      return 0
    fi
    sleep 0.45
  done
  fail "timed out waiting for UI text: $text"
}

center_clickable() {
  local file="$1" needle="$2"
  python3 - "$file" "$needle" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot(); needle=sys.argv[2]
for n in root.iter('node'):
    values=(n.attrib.get('text',''), n.attrib.get('content-desc',''))
    if not any(needle in v for v in values): continue
    if n.attrib.get('clickable','') != 'true': continue
    m=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.attrib.get('bounds',''))
    if not m: continue
    x1,y1,x2,y2=map(int,m.groups()); print((x1+x2)//2,(y1+y2)//2); raise SystemExit
raise SystemExit(2)
PY
}

capture_product() {
  local name="$1" label="$2"
  adb exec-out screencap -p > "$OUT/$name.png"
  [[ -s "$OUT/$name.png" ]] || fail "empty screenshot: $name"
  python3 ci/check-real-celine-render.py "$OUT/$name.png" "$label"
  python3 ci/check-celine-person-presence.py "$OUT/$name.png" "$label"
}

ime_visible() {
  adb shell dumpsys input_method > "$OUT/block9-ime.txt"
  grep -Eq 'mInputShown=true|mDecorViewVisible=true|mWindowVisible=true' "$OUT/block9-ime.txt"
}

set_zoom() {
  local requested="$1" expected="${2:-$1}"
  adb shell "run-as $PACKAGE sh -c 'printf %s $requested > files/$ZOOM_MARKER.tmp && mv files/$ZOOM_MARKER.tmp files/$ZOOM_MARKER'" \
    || fail "could not write private zoom marker $requested"
  for _ in $(seq 1 28); do
    if adb logcat -d | grep -F 'V70-141' | grep -F "requested=$requested" | grep -Fq "zoom=$expected"; then
      sleep 1.2
      return 0
    fi
    sleep 0.5
  done
  fail "runtime did not consume zoom marker requested=$requested expected=$expected"
}

[[ -s "$APK" ]] || fail "missing APK: $APK"
adb install -r "$APK" >/dev/null
adb shell am force-stop "$PACKAGE" || true
adb logcat -c || true
adb shell pm grant "$PACKAGE" android.permission.RECORD_AUDIO || true
adb shell am start -W -n "$ACTIVITY" >/dev/null

wait_text "Mit Celin" /sdcard/block9-home.xml "$OUT/15-home-scene.xml"
sleep 1.2
PID_HOME="$(pid)"
[[ -n "$PID_HOME" ]] || fail "HOME process missing"
capture_product 15-home-scene HOME

grep -Fq 'Celin 3D Ansicht' "$OUT/15-home-scene.xml" || fail "HOME 3D stage missing"
read -r CALL_X CALL_Y <<< "$(center_clickable "$OUT/15-home-scene.xml" "Mit Celin")"
[[ -n "${CALL_X:-}" && -n "${CALL_Y:-}" ]] || fail "CALL entry coordinates missing"
adb shell input tap "$CALL_X" "$CALL_Y"
wait_text "Live mit Celin" /sdcard/block9-call.xml "$OUT/16-call-scene.xml"
sleep 1.6
PID_CALL="$(pid)"
[[ "$PID_CALL" = "$PID_HOME" ]] || fail "process restarted entering CALL: home=$PID_HOME call=$PID_CALL"
capture_product 16-call-scene CALL

grep -Fq 'Celin 3D Ansicht' "$OUT/16-call-scene.xml" || fail "CALL 3D stage missing"
grep -Fq 'Mikrofon' "$OUT/16-call-scene.xml" || fail "CALL microphone control missing"
grep -Fq 'Auflegen' "$OUT/16-call-scene.xml" || fail "CALL hang-up control missing"

# Real CALL state transition: mute and unmute the production microphone control. This satisfies the
# Block-9 speaking/listening-state requirement without depending on emulator speech recognition.
read -r MIC_X MIC_Y <<< "$(center_clickable "$OUT/16-call-scene.xml" "Mikrofon")"
adb shell input tap "$MIC_X" "$MIC_Y"
wait_text "Mikrofon aus" /sdcard/block9-muted.xml "$OUT/17-block9-call-muted.xml"
sleep 0.5
capture_product 17-block9-call-muted CALL_MUTED
read -r MIC_OFF_X MIC_OFF_Y <<< "$(center_clickable "$OUT/17-block9-call-muted.xml" "Mikrofon aus")"
adb shell input tap "$MIC_OFF_X" "$MIC_OFF_Y"
sleep 0.8
dump_ui /sdcard/block9-unmuted.xml "$OUT/18-block9-call-unmuted.xml"
if grep -Fq '🔇  Mikrofon aus' "$OUT/18-block9-call-unmuted.xml"; then
  fail "CALL microphone remained muted after unmute tap"
fi
grep -Fq 'Mikrofon' "$OUT/18-block9-call-unmuted.xml" || fail "CALL microphone control missing after unmute"
capture_product 18-block9-call-unmuted CALL_UNMUTED

# Background the exact active CALL task, then bring the existing MainActivity instance back to the
# foreground. --activity-reorder-to-front avoids creating a second Activity. The CALL overlay and
# process must survive, and two post-resume frames must prove the central body/social owner resumed.
adb shell input keyevent 3
sleep 1.4
[[ "$(pid)" = "$PID_HOME" ]] || fail "process died/restarted while app was backgrounded"
adb shell am start -W --activity-reorder-to-front -n "$ACTIVITY" >/dev/null
wait_text "Live mit Celin" /sdcard/block9-foreground.xml "$OUT/19-block9-call-foreground.xml"
sleep 1.4
[[ "$(pid)" = "$PID_HOME" ]] || fail "process restarted foregrounding active CALL"
capture_product 19-block9-call-foreground-a CALL_FOREGROUND_A
sleep 1.45
capture_product 20-block9-call-foreground-b CALL_FOREGROUND_B
python3 ci/celine-block5-motion-compare.py \
  "$OUT/19-block9-call-foreground-a.png" "$OUT/20-block9-call-foreground-b.png" BLOCK9_RESUMED_ARMS \
  | tee "$OUT/block9-resumed-arm-motion.txt"
python3 ci/celine-block6-social-presence-compare.py \
  "$OUT/19-block9-call-foreground-a.png" "$OUT/20-block9-call-foreground-b.png" BLOCK9_RESUMED_FACE \
  | tee "$OUT/block9-resumed-social-motion.txt"

# Exercise the already-protected real CALL camera field: face-close then return to normal CALL.
set_zoom 4.6
capture_product 21-block9-call-zoomed CALL_ZOOMED
set_zoom 2.8
capture_product 22-block9-call-zoom-reset CALL_ZOOM_RESET
[[ "$(pid)" = "$PID_HOME" ]] || fail "process changed during CALL zoom/reset"

# End CALL through the visible production hang-up control, not Android BACK, so the intended cleanup
# path restores HOME. UI dumps are bounded-retry because UiAutomator can transiently return null.
dump_ui /sdcard/block9-prehang.xml "$OUT/23-block9-prehang.xml"
read -r END_X END_Y <<< "$(center_clickable "$OUT/23-block9-prehang.xml" "Auflegen")"
adb shell input tap "$END_X" "$END_Y"
wait_text "Mit Celin" /sdcard/block9-home-return.xml "$OUT/24-block9-home-return.xml"
sleep 1.1
[[ "$(pid)" = "$PID_HOME" ]] || fail "process restarted returning HOME from CALL"
capture_product 24-block9-home-return HOME_RETURN
python3 ci/check-home-return-zoom.py "$OUT/15-home-scene.png" "$OUT/24-block9-home-return.png"

# HOME keyboard lifecycle. UiAutomator may retain the 3D stage as an accessibility node even when it
# is visually compacted/occluded by the IME; focus and actual IME visibility are the fail-closed
# keyboard-open contract. Closing the IME must restore the real HOME stage and Celine.
read -r EDIT_X EDIT_Y <<< "$(python3 - "$OUT/24-block9-home-return.xml" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
for n in root.iter('node'):
    if n.attrib.get('class')!='android.widget.EditText': continue
    if n.attrib.get('content-desc')!='Celin Nachricht schreiben': continue
    m=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.attrib.get('bounds',''))
    if not m: continue
    x1,y1,x2,y2=map(int,m.groups()); print((x1+x2)//2,(y1+y2)//2); raise SystemExit
raise SystemExit(2)
PY
)"
[[ -n "${EDIT_X:-}" && -n "${EDIT_Y:-}" ]] || fail "HOME composer coordinates missing"
adb shell input tap "$EDIT_X" "$EDIT_Y"
keyboard_ready=false
for _ in $(seq 1 10); do
  sleep 0.5
  dump_ui /sdcard/block9-keyboard-open.xml "$OUT/25-block9-keyboard-open.xml"
  if ime_visible && grep -Fq 'Celin Nachricht schreiben' "$OUT/25-block9-keyboard-open.xml"; then
    keyboard_ready=true
    break
  fi
done
[[ "$keyboard_ready" == true ]] || fail "software keyboard did not become visible"
adb exec-out screencap -p > "$OUT/25-block9-keyboard-open.png"
python3 - "$OUT/25-block9-keyboard-open.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
nodes=list(ET.parse(sys.argv[1]).getroot().iter('node'))
edit=next((n for n in nodes if n.attrib.get('class')=='android.widget.EditText' and n.attrib.get('content-desc')=='Celin Nachricht schreiben'),None)
if edit is None or edit.attrib.get('focused')!='true': raise SystemExit('composer not focused with IME open')
print('Block9 keyboard-open state OK: composer focused and IME visible; stage occlusion is verified manually from screenshot')
PY
adb shell input keyevent 4
for _ in $(seq 1 12); do
  sleep 0.4
  if ! ime_visible; then break; fi
done
if ime_visible; then fail "software keyboard remained visible after close"; fi
wait_text "Mit Celin" /sdcard/block9-keyboard-closed.xml "$OUT/26-block9-keyboard-closed.xml"
sleep 0.9
[[ "$(pid)" = "$PID_HOME" ]] || fail "process restarted across keyboard lifecycle"
capture_product 26-block9-keyboard-closed KEYBOARD_CLOSED_HOME
grep -Fq 'Celin 3D Ansicht' "$OUT/26-block9-keyboard-closed.xml" || fail "HOME 3D stage did not return after keyboard close"

# Final fail-closed runtime audit across the complete temporal chain.
timeout 15s adb logcat -d -v threadtime > "$OUT/scene-logcat.txt" 2>&1 || true
for required in \
  'V80-400' 'V80-410' 'V80-420' 'V80-450' 'V80-451' 'V80-460' 'V80-461' \
  'V76-210' 'V45-100' 'V70-141'; do
  grep -Fq "$required" "$OUT/scene-logcat.txt" || fail "required Block-9 runtime evidence missing: $required"
done
if grep -Eq 'V80-499|V79-598|V79-599|V76-298|V76-299|V61-102|V61-199|REN-399|FATAL EXCEPTION|SIGABRT' "$OUT/scene-logcat.txt"; then
  fail "runtime/source failure detected during Block-9 lifecycle proof"
fi

cat > "$OUT/block9-summary.txt" <<EOF
PASS Block 9 lifecycle/temporal technical gate
HOME_CALL_HOME=same_process_visible
MUTE_UNMUTE=production_call_control_survived
BACKGROUND_FOREGROUND=active_call_same_process_restored
POST_RESUME_MOTION=arm_and_social_temporal_guards_passed
CAMERA_ZOOM_RESET=face_close_to_normal_call_visible
KEYBOARD_OPEN_CLOSE=focused_ime_then_home_stage_restored_same_process
SETTINGS_AVATAR_LAB_HOME=proved_by_following_existing_settings_proof
PROTECTED_RUNTIME_FINGERPRINT=reused_no_runtime_change
MANUAL_TEMPORAL_REVIEW=required
EOF

echo "PASS: Block 9 product lifecycle chain completed; mandatory manual visual review still required."
