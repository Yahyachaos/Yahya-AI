#!/usr/bin/env bash
# Targeted 9R.5 mirror proof; manual visual review remains mandatory.
set -euo pipefail

APK="${1:-ci-apk/app-debug.apk}"
OUT="${2:-avatar-lab-proof}"
PACKAGE="de.yahya.ai"
ACTIVITY="de.yahya.ai/.MainActivity"
ROOM_MARKER="celine-ci-room-action-v9r"
CAPTURE_MANIFEST="$OUT/9r5-mirror-capture-manifest.tsv"
mkdir -p "$OUT"
: > "$OUT/9r5-mirror-runtime-log.txt"
: > "$CAPTURE_MANIFEST"

fail() {
  echo "9R.5 mirror proof ERROR: $*" >&2
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
  cat "$OUT/$path" >> "$OUT/9r5-mirror-runtime-log.txt"
}

request_and_wait_anchor() {
  local target="$1" prefix="$2" label="$3" defer_runtime="${4:-false}" transition_delay="${5:-0.28}"
  adb logcat -c || true
  write_target "$target"
  wait_log "V80-472"
  wait_log "target=$target"
  sleep "$transition_delay"
  capture_product "${prefix}-${label}-transition" "9R5_MIRROR_${label}_TRANSITION"
  wait_log "V80-475" 320
  wait_log "anchor=$target" 320
  sleep 0.20
  capture_product "${prefix}-${label}-arrived" "9R5_MIRROR_${label}_ARRIVED"
  if [[ "$defer_runtime" != "true" ]]; then
    append_runtime "${prefix}-${label}-runtime.txt"
  fi
}

[[ -s "$APK" ]] || fail "missing APK: $APK"
adb install -r "$APK" >/dev/null
adb shell am force-stop "$PACKAGE" || true
adb logcat -c || true
adb shell am start -W -n "$ACTIVITY" >/dev/null
wait_text "Mit Celin" /sdcard/9r5-mirror-home.xml "$OUT/170-9r5-mirror-home.xml"
sleep 1.5
PID_HOME="$(pid)"
[[ -n "$PID_HOME" ]] || fail "HOME process missing"
capture_product 170-9r5-mirror-camera-talk-start 9R5_MIRROR_CAMERA_TALK_START
append_runtime 170-9r5-mirror-initial-runtime.txt

# The canonical Mirror is an interaction_step whose approach is the already accepted Dresser.
# Reach Dresser through normal locomotion, then enter Mirror without moving the accepted root.
request_and_wait_anchor dresser_anchor 172 9r5-mirror-dresser-approach
request_and_wait_anchor mirror_anchor 174 9r5-mirror false 0.10

# Preserve the short mirror-check hold: once MIRROR_STAND settles, capture immediately before any
# expensive validation/log processing can consume the 1.50 s check phase.
wait_log "V80-483" 320
wait_log "anchor=mirror_anchor" 320
wait_log "pose=MIRROR_STAND" 320
wait_log "centralOwner=true" 320
wait_log "noTeleport=true" 320
sleep 0.25
capture_product 176-9r5-mirror-check-stable 9R5_MIRROR_CHECK_STABLE

# After the bounded hold/ramp Celine should clearly re-acquire the fixed-camera user while the
# accepted Dresser/Mirror root remains unchanged.
sleep 1.85
capture_product 177-9r5-mirror-user-reacquire 9R5_MIRROR_USER_REACQUIRE
[[ "$(pid)" = "$PID_HOME" ]] || fail "process changed during mirror hold"
append_runtime 174-9r5-mirror-runtime.txt
append_runtime 176-9r5-mirror-check-runtime.txt
append_runtime 177-9r5-mirror-reacquire-runtime.txt

# Route away through the authored mirror -> dresser -> room_walk_anchor_left chain. Sample after
# visible locomotion begins, then return to the fixed camera-talk anchor.
request_and_wait_anchor room_walk_anchor_left 180 9r5-mirror-walk-away false 0.55
request_and_wait_anchor camera_talk_anchor 184 9r5-mirror-camera-return
wait_log "V80-477" 260
sleep 1.0
capture_product 186-9r5-mirror-camera-talk-final 9R5_MIRROR_CAMERA_TALK_FINAL
append_runtime 186-9r5-mirror-camera-final-runtime.txt

validate_products
python3 ci/check-home-return-zoom.py \
  "$OUT/170-9r5-mirror-camera-talk-start.png" "$OUT/186-9r5-mirror-camera-talk-final.png" \
  | tee "$OUT/9r5-mirror-camera-return-zoom.txt"

for required in 'V80-471' 'V80-472' 'V80-475' 'V80-483' 'V80-477'; do
  grep -Fq "$required" "$OUT/9r5-mirror-runtime-log.txt" || fail "required mirror evidence missing: $required"
done
grep -Fq 'dresserSafetyX=+0.65' "$OUT/9r5-mirror-runtime-log.txt" || fail "accepted dresser X-root evidence missing"
grep -Fq 'dresserSafetyZ=+0.6' "$OUT/9r5-mirror-runtime-log.txt" || fail "accepted dresser Z-root evidence missing"
grep -Fq 'mirrorRoot=acceptedDresser' "$OUT/9r5-mirror-runtime-log.txt" || fail "mirror root inheritance evidence missing"
grep -Fq 'mirrorContact=false' "$OUT/9r5-mirror-runtime-log.txt" || fail "mirror no-contact evidence missing"
grep -Fq 'mirrorRealtimeReflection=false' "$OUT/9r5-mirror-runtime-log.txt" || fail "mirror reflection policy evidence missing"
grep -Fq 'pose=MIRROR_STAND' "$OUT/9r5-mirror-runtime-log.txt" || fail "MIRROR_STAND evidence missing"
grep -Fq 'centralOwner=true' "$OUT/9r5-mirror-runtime-log.txt" || fail "central owner evidence missing"
grep -Fq 'cameraFixed=true' "$OUT/9r5-mirror-runtime-log.txt" || fail "fixed-camera evidence missing"
grep -Fq 'noTeleport=true' "$OUT/9r5-mirror-runtime-log.txt" || fail "no-teleport evidence missing"
if grep -Eq 'pose=(WINDOW_STAND|CHAIR_SIT|BED_EDGE_SIT|BED_RELAX|BED_LIE)|V80-499|V79-598|V79-599|V76-298|V76-299|V61-102|V61-199|REN-399|FATAL EXCEPTION|SIGABRT' "$OUT/9r5-mirror-runtime-log.txt"; then
  fail "runtime/source failure or protected non-Mirror pose activation detected during mirror proof"
fi

cat > "$OUT/9r5-mirror-summary.txt" <<EOF
PASS 9R.5 mirror technical gate
RUNTIME_HEAD=${GITHUB_SHA:-unknown}
CHAIN=camera_talk_to_dresser_to_mirror_check_to_user_reacquire_to_walk_away_to_camera_talk
MIRROR_STAND=central_owner_bounded_pose
MIRROR_ROOT=accepted_dresser
MIRROR_REFLECTION=realtime_not_required
HAND_CONTACT=false
USER_REACQUIRE=bounded_upper_body_head_neck_counterturn
CAMERA=physically_fixed_no_chase
TELEPORT=false
PROTECTED_WINDOW_9R3_9R4=not_invoked
ACCEPTED_DRESSER=approach_and_root_reused
MANUAL_MIRROR_CLEARANCE_CHECK_GESTURE_REACQUIRE_TRANSITION_REVIEW=required
EOF

echo "PASS: 9R.5 bounded mirror interaction completed; mandatory manual visual review remains."
