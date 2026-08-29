#!/usr/bin/env bash
# Targeted 9R.5 dresser proof; manual visual review remains mandatory.
set -euo pipefail

APK="${1:-ci-apk/app-debug.apk}"
OUT="${2:-avatar-lab-proof}"
PACKAGE="de.yahya.ai"
ACTIVITY="de.yahya.ai/.MainActivity"
ROOM_MARKER="celine-ci-room-action-v9r"
CAPTURE_MANIFEST="$OUT/9r5-dresser-capture-manifest.tsv"
mkdir -p "$OUT"
: > "$OUT/9r5-dresser-runtime-log.txt"
: > "$CAPTURE_MANIFEST"

fail() {
  echo "9R.5 dresser proof ERROR: $*" >&2
  adb logcat -d | grep -E 'de\.yahya\.ai|FATAL EXCEPTION|REN-|V45-|V61-|V70-|V76-|V77-|V80-' | tail -420 || true
  exit 1
}

pid() { adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r ' || true; }

capture_product() {
  local name="$1" label="$2"
  adb exec-out screencap -p > "$OUT/$name.png"
  [[ -s "$OUT/$name.png" ]] || fail "empty screenshot: $name"
  printf '%s\t%s\n' "$name" "$label" >> "$CAPTURE_MANIFEST"
}

validate_products() {
  local name label
  while IFS=$'\t' read -r name label; do
    [[ -n "$name" && -n "$label" ]] || fail "invalid capture manifest entry"
    python3 ci/check-real-celine-render.py "$OUT/$name.png" "$label"
    python3 ci/check-celine-person-presence.py "$OUT/$name.png" "$label"
  done < "$CAPTURE_MANIFEST"
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
  cat "$OUT/$path" >> "$OUT/9r5-dresser-runtime-log.txt"
}

request_and_wait_anchor() {
  local target="$1" prefix="$2" label="$3" defer_runtime="${4:-false}"
  adb logcat -c || true
  write_target "$target"
  wait_log "V80-472"
  wait_log "target=$target"
  sleep 0.28
  capture_product "${prefix}-${label}-transition" "9R5_DRESSER_${label}_TRANSITION"
  wait_log "V80-475" 320
  wait_log "anchor=$target" 320
  sleep 0.20
  capture_product "${prefix}-${label}-arrived" "9R5_DRESSER_${label}_ARRIVED"
  if [[ "$defer_runtime" != "true" ]]; then
    append_runtime "${prefix}-${label}-runtime.txt"
  fi
}

[[ -s "$APK" ]] || fail "missing APK: $APK"
adb install -r "$APK" >/dev/null
adb shell am force-stop "$PACKAGE" || true
adb logcat -c || true
adb shell am start -W -n "$ACTIVITY" >/dev/null
wait_text "Mit Celin" /sdcard/9r5-dresser-home.xml "$OUT/150-9r5-dresser-home.xml"
sleep 1.5
PID_HOME="$(pid)"
[[ -n "$PID_HOME" ]] || fail "HOME process missing"
capture_product 150-9r5-dresser-camera-talk-start 9R5_DRESSER_CAMERA_TALK_START
append_runtime 150-9r5-dresser-initial-runtime.txt

# Keep the Dresser hold captures free of expensive log dumps. The runtime intentionally holds the
# inspect phase only briefly before user re-acquisition, so evidence logging must not shift the
# screenshot labelled inspect-stable into the later reacquire phase.
request_and_wait_anchor dresser_anchor 152 9r5-dresser true
wait_log "V80-483" 320
wait_log "anchor=dresser_anchor" 320
wait_log "pose=DRESSER_STAND" 320
wait_log "centralOwner=true" 320
wait_log "noTeleport=true" 320
sleep 0.35
capture_product 156-9r5-dresser-inspect-stable 9R5_DRESSER_INSPECT_STABLE

# Runtime holds the final dresser-facing body orientation, then performs one bounded upper-body
# re-acquisition toward the fixed-camera user. Keep this timed sequence free of validators/log dumps.
sleep 1.75
capture_product 157-9r5-dresser-user-reacquire 9R5_DRESSER_USER_REACQUIRE
[[ "$(pid)" = "$PID_HOME" ]] || fail "process changed during dresser hold"
append_runtime 152-9r5-dresser-runtime.txt
append_runtime 156-9r5-dresser-inspect-runtime.txt
append_runtime 157-9r5-dresser-reacquire-runtime.txt

request_and_wait_anchor room_walk_anchor_left 162 9r5-dresser-walk-away
request_and_wait_anchor camera_talk_anchor 166 9r5-dresser-camera-return
wait_log "V80-477" 260
sleep 1.0
capture_product 168-9r5-dresser-camera-talk-final 9R5_DRESSER_CAMERA_TALK_FINAL
append_runtime 168-9r5-dresser-camera-final-runtime.txt

validate_products
python3 ci/check-home-return-zoom.py \
  "$OUT/150-9r5-dresser-camera-talk-start.png" "$OUT/168-9r5-dresser-camera-talk-final.png" \
  | tee "$OUT/9r5-dresser-camera-return-zoom.txt"

for required in 'V80-471' 'V80-472' 'V80-475' 'V80-483' 'V80-477'; do
  grep -Fq "$required" "$OUT/9r5-dresser-runtime-log.txt" || fail "required dresser evidence missing: $required"
done
grep -Fq 'dresserSafetyX=+0.65' "$OUT/9r5-dresser-runtime-log.txt" || fail "dresser runtime X-clearance evidence missing"
grep -Fq 'dresserSafetyZ=+0.6' "$OUT/9r5-dresser-runtime-log.txt" || fail "dresser runtime depth evidence missing"
grep -Fq 'pose=DRESSER_STAND' "$OUT/9r5-dresser-runtime-log.txt" || fail "DRESSER_STAND evidence missing"
grep -Fq 'centralOwner=true' "$OUT/9r5-dresser-runtime-log.txt" || fail "central owner evidence missing"
grep -Fq 'cameraFixed=true' "$OUT/9r5-dresser-runtime-log.txt" || fail "fixed-camera evidence missing"
grep -Fq 'noTeleport=true' "$OUT/9r5-dresser-runtime-log.txt" || fail "no-teleport evidence missing"
if grep -Eq 'pose=(WINDOW_STAND|CHAIR_SIT|BED_EDGE_SIT|BED_RELAX|BED_LIE)|V80-499|V79-598|V79-599|V76-298|V76-299|V61-102|V61-199|REN-399|FATAL EXCEPTION|SIGABRT' "$OUT/9r5-dresser-runtime-log.txt"; then
  fail "runtime/source failure or protected destination/contact pose activation detected during dresser proof"
fi

cat > "$OUT/9r5-dresser-summary.txt" <<EOF
PASS 9R.5 dresser technical gate
RUNTIME_HEAD=${GITHUB_SHA:-unknown}
CHAIN=camera_talk_to_dresser_to_inspect_to_user_reacquire_to_walk_away_to_camera_talk
DRESSER_STAND=central_owner_bounded_pose
DRESSER_CLEARANCE_X_OFFSET_M=+0.65
DRESSER_VISIBILITY_Z_OFFSET_M=+0.60
HAND_CONTACT=false
USER_REACQUIRE=bounded_upper_body_head_neck_counterturn
CAMERA=physically_fixed_no_chase
TELEPORT=false
PROTECTED_WINDOW_9R3_9R4=not_invoked
MANUAL_DRESSER_CLEARANCE_ORIENTATION_GAZE_TRANSITION_REVIEW=required
EOF

echo "PASS: 9R.5 bounded dresser destination completed; mandatory manual visual review remains."
