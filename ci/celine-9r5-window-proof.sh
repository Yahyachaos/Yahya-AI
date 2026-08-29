#!/usr/bin/env bash
# Targeted 9R.5 first-destination proof: window only; manual visual review remains mandatory.
set -euo pipefail

APK="${1:-ci-apk/app-debug.apk}"
OUT="${2:-avatar-lab-proof}"
PACKAGE="de.yahya.ai"
ACTIVITY="de.yahya.ai/.MainActivity"
ROOM_MARKER="celine-ci-room-action-v9r"
mkdir -p "$OUT"
: > "$OUT/9r5-window-runtime-log.txt"

fail() {
  echo "9R.5 window proof ERROR: $*" >&2
  adb logcat -d | grep -E 'de\.yahya\.ai|FATAL EXCEPTION|REN-|V45-|V61-|V70-|V76-|V77-|V80-' | tail -420 || true
  exit 1
}

pid() { adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r ' || true; }

capture_product() {
  local name="$1" label="$2"
  adb exec-out screencap -p > "$OUT/$name.png"
  [[ -s "$OUT/$name.png" ]] || fail "empty screenshot: $name"
  python3 ci/check-real-celine-render.py "$OUT/$name.png" "$label"
  python3 ci/check-celine-person-presence.py "$OUT/$name.png" "$label"
}

wait_text() {
  local text="$1" remote="$2" local_file="$3"
  for _ in $(seq 1 28); do
    adb shell rm -f "$remote" >/dev/null 2>&1 || true
    rm -f "$local_file"
    if adb shell uiautomator dump "$remote" >/dev/null 2>&1 \
      && adb pull "$remote" "$local_file" >/dev/null 2>&1 \
      && grep -q '<hierarchy' "$local_file" \
      && grep -Fq "$text" "$local_file"; then return 0; fi
    [[ -n "$(pid)" ]] || fail "app process died while waiting for UI: $text"
    sleep 0.45
  done
  fail "timed out waiting for UI text: $text"
}

wait_log() {
  local needle="$1" steps="${2:-220}"
  for _ in $(seq 1 "$steps"); do
    if adb logcat -d | grep -Fq "$needle"; then return 0; fi
    [[ -n "$(pid)" ]] || fail "app process died while waiting for log: $needle"
    sleep 0.22
  done
  fail "timed out waiting for runtime evidence: $needle"
}

write_target() {
  local target="$1"
  adb shell "run-as $PACKAGE sh -c 'printf %s $target > files/$ROOM_MARKER.tmp && mv files/$ROOM_MARKER.tmp files/$ROOM_MARKER'" \
    || fail "could not write private room marker: $target"
}

append_runtime() {
  local path="$1"
  adb logcat -d -v threadtime > "$OUT/$path" 2>&1 || true
  cat "$OUT/$path" >> "$OUT/9r5-window-runtime-log.txt"
}

request_and_wait_anchor() {
  local target="$1" prefix="$2" label="$3"
  adb logcat -c || true
  write_target "$target"
  wait_log "V80-472"
  wait_log "target=$target"
  sleep 0.28
  capture_product "${prefix}-${label}-transition" "9R5_WINDOW_${label}_TRANSITION"
  wait_log "V80-475" 320
  wait_log "anchor=$target" 320
  sleep 0.20
  capture_product "${prefix}-${label}-arrived" "9R5_WINDOW_${label}_ARRIVED"
  append_runtime "${prefix}-${label}-runtime.txt"
}

[[ -s "$APK" ]] || fail "missing APK: $APK"
adb install -r "$APK" >/dev/null
adb shell am force-stop "$PACKAGE" || true
adb logcat -c || true
adb shell am start -W -n "$ACTIVITY" >/dev/null
wait_text "Mit Celin" /sdcard/9r5-window-home.xml "$OUT/130-9r5-window-home.xml"
sleep 1.5
PID_HOME="$(pid)"
[[ -n "$PID_HOME" ]] || fail "HOME process missing"
capture_product 130-9r5-window-camera-talk-start 9R5_WINDOW_CAMERA_TALK_START
append_runtime 130-9r5-window-initial-runtime.txt

request_and_wait_anchor window_anchor 132 9r5-window
wait_log "V80-483" 320
wait_log "anchor=window_anchor" 320
wait_log "pose=WINDOW_STAND" 320
wait_log "centralOwner=true" 320
wait_log "noTeleport=true" 320
sleep 0.45
capture_product 136-9r5-window-outward-stable 9R5_WINDOW_OUTWARD_STABLE
append_runtime 136-9r5-window-outward-runtime.txt

# The window contribution deliberately spends the first hold looking outward, then performs one
# slow bounded head/neck counter-turn toward the user while the body remains partially oriented.
sleep 2.10
capture_product 137-9r5-window-user-reacquire 9R5_WINDOW_USER_REACQUIRE
append_runtime 137-9r5-window-reacquire-runtime.txt
[[ "$(pid)" = "$PID_HOME" ]] || fail "process changed during window hold"

request_and_wait_anchor room_walk_anchor_right 140 9r5-window-walk-away
request_and_wait_anchor camera_talk_anchor 142 9r5-window-camera-return
wait_log "V80-477" 260
sleep 1.0
capture_product 144-9r5-window-camera-talk-final 9R5_WINDOW_CAMERA_TALK_FINAL
append_runtime 144-9r5-window-camera-final-runtime.txt
python3 ci/check-home-return-zoom.py \
  "$OUT/130-9r5-window-camera-talk-start.png" "$OUT/144-9r5-window-camera-talk-final.png" \
  | tee "$OUT/9r5-window-camera-return-zoom.txt"

for required in 'V80-471' 'V80-472' 'V80-475' 'V80-483' 'V80-477'; do
  grep -Fq "$required" "$OUT/9r5-window-runtime-log.txt" || fail "required window evidence missing: $required"
done
grep -Fq 'pose=WINDOW_STAND' "$OUT/9r5-window-runtime-log.txt" || fail "WINDOW_STAND evidence missing"
grep -Fq 'centralOwner=true' "$OUT/9r5-window-runtime-log.txt" || fail "central owner evidence missing"
grep -Fq 'cameraFixed=true' "$OUT/9r5-window-runtime-log.txt" || fail "fixed-camera evidence missing"
grep -Fq 'noTeleport=true' "$OUT/9r5-window-runtime-log.txt" || fail "no-teleport evidence missing"
if grep -Eq 'chair_sit_anchor|bed_edge_sit_anchor|bed_relax_anchor|bed_lie_anchor|V80-499|V79-598|V79-599|V76-298|V76-299|V61-102|V61-199|REN-399|FATAL EXCEPTION|SIGABRT' "$OUT/9r5-window-runtime-log.txt"; then
  fail "runtime/source failure or protected furniture-chain activation detected during window proof"
fi

cat > "$OUT/9r5-window-summary.txt" <<EOF
PASS 9R.5 window technical gate
RUNTIME_HEAD=${GITHUB_SHA:-unknown}
CHAIN=camera_talk_to_window_to_outward_hold_to_user_reacquire_to_walk_away_to_camera_talk
WINDOW_STAND=central_owner_bounded_pose
WINDOW_OUTWARD=partial_body_orientation
USER_REACQUIRE=bounded_head_neck_counterturn
CAMERA=physically_fixed_no_chase
TELEPORT=false
PROTECTED_9R3_9R4=not_invoked
MANUAL_WINDOW_ORIENTATION_GAZE_TRANSITION_REVIEW=required
EOF

echo "PASS: 9R.5 bounded window destination completed; mandatory manual visual review remains."
