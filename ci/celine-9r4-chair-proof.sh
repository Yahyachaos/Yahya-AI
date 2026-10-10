#!/usr/bin/env bash
# Targeted 9R.4 lounge-chair proof; exact runtime-fingerprint APK + mandatory manual review.
set -euo pipefail

APK="${1:-ci-apk/app-debug.apk}"
OUT="${2:-avatar-lab-proof}"
PACKAGE="de.yahya.ai"
ACTIVITY="de.yahya.ai/.MainActivity"
ROOM_MARKER="celine-ci-room-action-v9r"
mkdir -p "$OUT"
: > "$OUT/9r4-runtime-log.txt"

fail() {
  echo "9R.4 chair proof ERROR: $*" >&2
  adb logcat -d | grep -E 'de\.yahya\.ai|FATAL EXCEPTION|REN-|V45-|V61-|V70-|V76-|V77-|V80-' | tail -420 || true
  exit 1
}

pid() { adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r ' || true; }

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
    sleep 0.55
  done
  return 1
}

wait_text() {
  local text="$1" remote="$2" local_file="$3"
  for _ in $(seq 1 28); do
    if dump_ui "$remote" "$local_file" && grep -Fq "$text" "$local_file"; then return 0; fi
    sleep 0.4
  done
  fail "timed out waiting for UI text: $text"
}

capture_product() {
  local name="$1" label="$2"
  adb exec-out screencap -p > "$OUT/$name.png"
  [[ -s "$OUT/$name.png" ]] || fail "empty screenshot: $name"
  python3 ci/check-real-celine-render.py "$OUT/$name.png" "$label"
  python3 ci/check-celine-person-presence.py "$OUT/$name.png" "$label"
}

wait_log() {
  local needle="$1" steps="${2:-180}"
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
    || fail "could not write private 9R room marker: $target"
}

append_runtime() {
  local path="$1"
  adb logcat -d -v threadtime > "$OUT/$path" 2>&1 || true
  cat "$OUT/$path" >> "$OUT/9r4-runtime-log.txt"
}

request_and_wait_anchor() {
  local target="$1" prefix="$2" label="$3"
  adb logcat -c || true
  write_target "$target"
  wait_log "V80-472"
  wait_log "target=$target"
  sleep 0.28
  capture_product "${prefix}-${label}-transition" "9R4_${label}_TRANSITION"
  wait_log "V80-475" 240
  wait_log "anchor=$target" 240
  sleep 0.18
  capture_product "${prefix}-${label}-arrived" "9R4_${label}_ARRIVED"
  append_runtime "${prefix}-${label}-runtime.txt"
}

wait_chair_sit() {
  request_and_wait_anchor chair_sit_anchor 104 9r4-chair-sit
  wait_log "V80-483" 300
  wait_log "anchor=chair_sit_anchor" 300
  wait_log "pose=CHAIR_SIT" 300
  wait_log "centralOwner=true" 300
  wait_log "noTeleport=true" 300
  sleep 0.35
  capture_product 108-9r4-chair-sit-stable 9R4_CHAIR_SIT_STABLE
  sleep 0.90
  capture_product 109-9r4-chair-relaxed-hold 9R4_CHAIR_RELAXED_HOLD
  append_runtime 108-9r4-chair-sit-runtime.txt
}

[[ -s "$APK" ]] || fail "missing APK: $APK"
adb install -r "$APK" >/dev/null
adb shell am force-stop "$PACKAGE" || true
adb logcat -c || true
adb shell am start -W -n "$ACTIVITY" >/dev/null
wait_text "Mit Celin" /sdcard/9r4-home.xml "$OUT/100-9r4-home.xml"
sleep 1.5
PID_HOME="$(pid)"
[[ -n "$PID_HOME" ]] || fail "HOME process missing"
grep -Fq 'Celin 3D Ansicht' "$OUT/100-9r4-home.xml" || fail "HOME 3D stage missing"
capture_product 100-9r4-camera-talk-start 9R4_CAMERA_TALK_START
append_runtime 100-9r4-initial-runtime.txt
grep -Fq 'chairContact=true' "$OUT/100-9r4-initial-runtime.txt" \
  || fail "9R.4 chair contact edge was not enabled"

# Walk only to the verified standing approach outside the chair collider.
request_and_wait_anchor chair_approach_anchor 101 9r4-chair-approach
wait_log "floorCalibrated=true" 180
[[ "$(pid)" = "$PID_HOME" ]] || fail "process changed after chair approach"
append_runtime 101-9r4-chair-approach-runtime-final.txt
grep -Fq 'cameraFixed=true' "$OUT/101-9r4-chair-approach-runtime-final.txt" \
  || fail "chair approach lost fixed camera"
grep -Fq 'noTeleport=true' "$OUT/101-9r4-chair-approach-runtime-final.txt" \
  || fail "chair approach missing no-teleport contract"

# One authored chair sit/relaxed hold. No variants.
wait_chair_sit

# Controlled stand back to the verified approach. Root/pose easing needs to complete before walking.
adb logcat -c || true
write_target chair_approach_anchor
wait_log "V80-472"
wait_log "target=chair_approach_anchor"
sleep 0.22
capture_product 112-9r4-chair-stand-transition 9R4_CHAIR_STAND_TRANSITION
wait_log "V80-475" 240
wait_log "anchor=chair_approach_anchor" 240
sleep 1.70
capture_product 114-9r4-chair-stand-stable 9R4_CHAIR_STAND_STABLE
append_runtime 114-9r4-chair-stand-runtime.txt
[[ "$(pid)" = "$PID_HOME" ]] || fail "process changed after chair stand"

# Walk away through the accepted left corridor and return to the camera talk anchor.
request_and_wait_anchor room_walk_anchor_left 116 9r4-walk-away
request_and_wait_anchor camera_talk_anchor 120 9r4-camera-return
wait_log "V80-477" 240
sleep 1.0
capture_product 124-9r4-camera-talk-final 9R4_CAMERA_TALK_FINAL
append_runtime 120-9r4-camera-return-runtime.txt
python3 ci/check-home-return-zoom.py \
  "$OUT/100-9r4-camera-talk-start.png" "$OUT/124-9r4-camera-talk-final.png" \
  | tee "$OUT/9r4-camera-return-zoom.txt"

for required in 'V80-471' 'V80-472' 'V80-475' 'V80-483' 'V80-477'; do
  grep -Fq "$required" "$OUT/9r4-runtime-log.txt" || fail "required 9R.4 evidence missing: $required"
done
grep -Fq 'chairContact=true' "$OUT/9r4-runtime-log.txt" || fail "chair contact edge evidence missing"
grep -Fq 'pose=CHAIR_SIT' "$OUT/9r4-runtime-log.txt" || fail "CHAIR_SIT evidence missing"
grep -Fq 'centralOwner=true' "$OUT/9r4-runtime-log.txt" || fail "central owner evidence missing"
grep -Fq 'cameraFixed=true' "$OUT/9r4-runtime-log.txt" || fail "fixed-camera evidence missing"
grep -Fq 'noTeleport=true' "$OUT/9r4-runtime-log.txt" || fail "no-teleport evidence missing"
if grep -Eq 'bed_edge_sit_anchor|bed_relax_anchor|bed_lie_anchor|V80-499|V79-598|V79-599|V76-298|V76-299|V61-102|V61-199|REN-399|FATAL EXCEPTION|SIGABRT' "$OUT/9r4-runtime-log.txt"; then
  fail "runtime/source failure or protected bed-chain activation detected during 9R.4"
fi

cat > "$OUT/9r4-summary.txt" <<EOF
PASS 9R.4 chair-chain technical gate
RUNTIME_HEAD=${GITHUB_SHA:-unknown}
CHAIN=camera_talk_to_chair_approach_to_chair_sit_relaxed_hold_to_stand_to_walk_away_to_camera_talk
CHAIR_APPROACH=accepted_nav_corridor
CHAIR_SIT=central_owner_eased
CHAIR_RELAXED_HOLD=central_owner_eased
CHAIR_STAND=controlled_eased
CAMERA=physically_fixed_no_chase
TELEPORT=false
BED_CHAIN=protected_not_invoked
MANUAL_CHAIR_PELVIS_BACK_FOOT_CONTACT_TRANSITION_REVIEW=required
EOF

echo "PASS: 9R.4 bounded chair chain completed; mandatory manual visual review remains."
