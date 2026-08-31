#!/usr/bin/env bash
set -euo pipefail

APK="${1:-ci-apk/app-debug.apk}"
OUT="${2:-block12-temporal-proof}"
PACKAGE="de.yahya.ai"
ACTIVITY="de.yahya.ai/.MainActivity"
ROOM_MARKER="celine-ci-room-action-v9r"
ZOOM_MARKER="celine-ci-camera-zoom-v70"
mkdir -p "$OUT"

fail(){
  echo "BLOCK12 ERROR: $*" >&2
  adb logcat -d | grep -E 'de\.yahya\.ai|FATAL EXCEPTION|SIGABRT|V80-|V79-|V77-|V76-|V70-|V61-|REN-|ROOM-' | tail -500 || true
  exit 1
}
pid(){ adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r ' || true; }
wait_log(){ local needle="$1" steps="${2:-300}"; for _ in $(seq 1 "$steps"); do adb logcat -d | grep -Fq "$needle" && return 0; [[ -n "$(pid)" ]] || fail "process died waiting for $needle"; sleep 0.20; done; fail "timeout waiting for $needle"; }
wait_text(){ local text="$1" remote="$2" local="$3"; for _ in $(seq 1 35); do adb shell uiautomator dump "$remote" >/dev/null 2>&1 || true; adb pull "$remote" "$local" >/dev/null 2>&1 || true; grep -Fq "$text" "$local" 2>/dev/null && return 0; [[ -n "$(pid)" ]] || fail "process died waiting for UI $text"; sleep 0.45; done; fail "timeout waiting for UI $text"; }
center_for(){ python3 - "$1" "$2" <<'PY'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot(); needle=sys.argv[2]
for node in root.iter('node'):
    if needle not in node.attrib.get('text',''): continue
    m=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',node.attrib.get('bounds',''))
    if m:
        x1,y1,x2,y2=map(int,m.groups()); print((x1+x2)//2,(y1+y2)//2); raise SystemExit
raise SystemExit(2)
PY
}
capture(){ local name="$1" label="$2"; adb exec-out screencap -p > "$OUT/$name.png"; [[ -s "$OUT/$name.png" ]] || fail "empty screenshot $name"; python3 ci/check-real-celine-render.py "$OUT/$name.png" "$label"; python3 ci/check-celine-person-presence.py "$OUT/$name.png" "$label"; }
write_room(){ local target="$1"; adb shell "run-as $PACKAGE sh -c 'printf %s $target > files/$ROOM_MARKER.tmp && mv files/$ROOM_MARKER.tmp files/$ROOM_MARKER'" || fail "room marker $target"; }
room_anchor(){ local target="$1" label="$2"; adb logcat -c || true; write_room "$target"; wait_log "V80-472"; wait_log "target=$target"; wait_log "V80-475" 420; wait_log "anchor=$target" 420; sleep 0.28; capture "$label" "BLOCK12_${label}"; }
room_pose(){ local target="$1" pose="$2" label="$3"; room_anchor "$target" "$label-arrived"; wait_log "V80-483" 420; wait_log "pose=$pose" 420; wait_log "centralOwner=true" 420; wait_log "noTeleport=true" 420; sleep 0.50; capture "$label-stable" "BLOCK12_${label}_STABLE"; }
table_lean(){ local label="$1"; room_anchor foreground_table_lean_anchor "$label-arrived"; wait_log "V80-480" 420; wait_log "anchor=foreground_table_lean_anchor" 420; wait_log "handContact=false" 420; wait_log "centralOwner=true" 420; wait_log "cameraFixed=true" 420; sleep 0.50; capture "$label-stable" "BLOCK12_${label}_STABLE"; }
set_zoom(){ local requested="$1" expected="${2:-$1}"; adb shell "run-as $PACKAGE sh -c 'printf %s $requested > files/$ZOOM_MARKER.tmp && mv files/$ZOOM_MARKER.tmp files/$ZOOM_MARKER'" || fail "zoom marker $requested"; for _ in $(seq 1 45); do adb logcat -d | grep -F 'V70-141' | grep -F "requested=$requested" | grep -Fq "zoom=$expected" && { sleep 1.2; return 0; }; sleep 0.35; done; fail "zoom not consumed requested=$requested expected=$expected"; }

[[ -s "$APK" ]] || fail "missing APK $APK"
adb install -r "$APK" >/dev/null
adb shell pm clear "$PACKAGE" >/dev/null || fail "could not clear app state"
adb shell pm grant "$PACKAGE" android.permission.RECORD_AUDIO || true
adb shell am force-stop "$PACKAGE" || true
adb logcat -c || true
adb shell am start -W -n "$ACTIVITY" >/dev/null
wait_text "Mit Celin" /sdcard/block12-home.xml "$OUT/home.xml"
sleep 2
PID0="$(pid)"; [[ -n "$PID0" ]] || fail "HOME process missing"

# One continuous installed-app recording. Individual stills are acceptance checkpoints, not substitutes.
adb shell rm -f /sdcard/block12-temporal.mp4 >/dev/null 2>&1 || true
adb shell 'screenrecord --bit-rate 4000000 --time-limit 180 /sdcard/block12-temporal.mp4 >/dev/null 2>&1 &' || true
sleep 1
capture 01-home-start BLOCK12_HOME_START
# Long enough to expose bounded idle/head/arm life and natural blink opportunities.
sleep 10
capture 02-home-idle-late BLOCK12_HOME_IDLE_LATE

# Exercise a real speaking/listening request without a cloud key; this keeps the installed product surface active.
adb shell am start -W -n "$ACTIVITY" --ez wake_celin true --es wake_command "gerätestatus" >/dev/null || true
sleep 6
capture 03-home-speaking-window BLOCK12_HOME_SPEAKING_WINDOW

# Real production CALL and real camera marker checkpoints used by the accepted pinch/dolly path.
wait_text "Mit Celin" /sdcard/block12-home-call.xml "$OUT/home-call.xml"
read -r CALL_X CALL_Y <<< "$(center_for "$OUT/home-call.xml" "Mit Celin")"
adb shell input tap "$CALL_X" "$CALL_Y"
wait_text "Live mit Celin" /sdcard/block12-call.xml "$OUT/call.xml"
wait_log "target=CALL eased=true snap=false" 240
sleep 2
capture 10-call-normal BLOCK12_CALL_NORMAL
set_zoom 2.8; capture 11-call-zoom-normal BLOCK12_CALL_ZOOM_NORMAL
set_zoom 3.5; capture 12-call-head BLOCK12_CALL_HEAD
set_zoom 4.6; capture 13-call-face BLOCK12_CALL_FACE
set_zoom 2.8; capture 14-call-back-out BLOCK12_CALL_BACK_OUT
adb shell input keyevent 4
wait_text "Mit Celin" /sdcard/block12-home-return.xml "$OUT/home-return.xml"
wait_log "target=HOME eased=true snap=false" 240
sleep 1
capture 20-home-after-call BLOCK12_HOME_AFTER_CALL
[[ "$(pid)" = "$PID0" ]] || fail "process changed across HOME/CALL/HOME"

# Table approach + lean + return. Table lean has its own accepted 9R.2 diagnostic contract (V80-480), not a V80-483 authored pose.
room_anchor foreground_table_approach_anchor 30-table-approach
table_lean 31-table-lean
room_anchor camera_talk_anchor 32-table-return

# Accepted complete bed chain, forward and reverse, then walk away and return.
room_anchor bed_approach_anchor 40-bed-approach
room_pose bed_edge_sit_anchor BED_EDGE_SIT 41-bed-edge
room_pose bed_relax_anchor BED_RELAX 42-bed-relax
room_pose bed_lie_anchor BED_LIE 43-bed-lie
room_pose bed_relax_anchor BED_RELAX 44-bed-situp-relax
room_pose bed_edge_sit_anchor BED_EDGE_SIT 45-bed-edge-return
room_pose bed_exit_anchor STAND_EXIT 46-bed-stand
room_anchor bed_approach_anchor 47-bed-depart
room_anchor room_walk_anchor_right 48-bed-walk-away
room_anchor camera_talk_anchor 49-bed-return

# Accepted chair chain.
room_anchor chair_approach_anchor 50-chair-approach
room_pose chair_sit_anchor CHAIR_SIT 51-chair-sit
sleep 2
capture 52-chair-relaxed-hold BLOCK12_CHAIR_RELAXED_HOLD
room_anchor chair_approach_anchor 53-chair-stand
room_anchor room_walk_anchor_left 54-chair-walk-away
room_anchor camera_talk_anchor 55-chair-return

# Window presence and glance/recovery.
room_pose window_anchor WINDOW_STAND 60-window
sleep 2
capture 61-window-hold BLOCK12_WINDOW_HOLD
room_anchor camera_talk_anchor 62-window-return

# Implemented lamp interaction: deliberate visit toggles real local light, then return.
room_anchor lamp_anchor 70-lamp-arrive
wait_log "V80-483" 420
wait_log "pose=LAMP_INTERACT" 420
wait_log "V80-484" 420
wait_log "lightEntity=floor_lamp_light" 420
capture 71-lamp-interact BLOCK12_LAMP_INTERACT
room_anchor room_walk_anchor_left 72-lamp-walk-away
room_anchor camera_talk_anchor 73-final-camera-talk
sleep 3
capture 80-home-final BLOCK12_HOME_FINAL
[[ "$(pid)" = "$PID0" ]] || fail "process changed during room-action sequence"

adb logcat -d -v threadtime > "$OUT/runtime.txt" 2>&1 || true
# Stop and retrieve the continuous recording before technical assertions.
adb shell 'pkill -INT screenrecord >/dev/null 2>&1 || true' || true
sleep 2
adb pull /sdcard/block12-temporal.mp4 "$OUT/block12-temporal.mp4" >/dev/null 2>&1 || true
[[ -s "$OUT/block12-temporal.mp4" ]] || fail "continuous temporal recording missing"

# Structural/temporal invariants. Manual video/frame inspection remains mandatory.
for marker in 'V80-400' 'V80-410' 'V80-420' 'target=CALL eased=true snap=false' 'target=HOME eased=true snap=false' 'V70-141' 'V80-472' 'V80-475' 'V80-480' 'V80-483'; do
  grep -Fq "$marker" "$OUT/runtime.txt" || fail "required temporal marker missing: $marker"
done
for pose in 'BED_EDGE_SIT' 'BED_RELAX' 'BED_LIE' 'STAND_EXIT' 'CHAIR_SIT' 'WINDOW_STAND' 'LAMP_INTERACT'; do
  grep -Fq "pose=$pose" "$OUT/runtime.txt" || fail "required pose missing: $pose"
done
grep -Fq 'lightEntity=floor_lamp_light' "$OUT/runtime.txt" || fail "lamp light evidence missing"
for z in 'requested=2.8 zoom=2.8' 'requested=3.5 zoom=3.5' 'requested=4.6 zoom=4.6'; do
  req="${z%% *}"; val="${z##* }"; grep -F 'V70-141' "$OUT/runtime.txt" | grep -F "$req" | grep -Fq "$val" || fail "zoom evidence missing: $z"
done
if grep -Eq 'V80-499|V79-598|V79-599|V76-298|V76-299|V61-102|V61-199|REN-399|ROOM-199|FATAL EXCEPTION|SIGABRT' "$OUT/runtime.txt"; then fail "runtime/source failure detected"; fi

cat > "$OUT/summary.txt" <<EOF
PASS Block 12 structural continuous-sequence gate; manual video acceptance required
RUNTIME_HEAD=53451e58b5b02af4a803876b5d89b9230e981145
RUNTIME_FINGERPRINT=23ffe5179f6fc38bdaedc8e745b6a1828c763815d1496bb44bd0eb7e573fe93e
HOME_IDLE=recorded
SPEAKING_WINDOW=recorded
HOME_CALL_HOME=same_process
ZOOM=2.8_to_3.5_to_4.6_to_2.8
TABLE=approach_lean_return
BED=edge_relax_lie_relax_edge_stand_walk_return
CHAIR=approach_sit_hold_stand_walk_return
WINDOW=stand_hold_return
LAMP=interact_real_light_return
VIDEO=block12-temporal.mp4
MANUAL_ACCEPTANCE=required
EOF

echo "PASS: Block 12 continuous structural sequence captured; inspect actual video and checkpoint frames before visual acceptance."
