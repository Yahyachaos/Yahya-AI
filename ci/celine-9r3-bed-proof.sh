#!/usr/bin/env bash
# Targeted 9R.3 bed-chain proof; uses an exact runtime-fingerprint APK and requires manual review.
set -euo pipefail

APK="${1:-ci-apk/app-debug.apk}"
OUT="${2:-avatar-lab-proof}"
PACKAGE="de.yahya.ai"
ACTIVITY="de.yahya.ai/.MainActivity"
ROOM_MARKER="celine-ci-room-action-v9r"
mkdir -p "$OUT"
: > "$OUT/9r3-runtime-log.txt"

fail() {
  echo "9R.3 bed proof ERROR: $*" >&2
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

request_and_wait_anchor() {
  local target="$1" prefix="$2" label="$3"
  adb logcat -c || true
  write_target "$target"
  wait_log "V80-472"
  wait_log "target=$target"
  sleep 0.28
  capture_product "${prefix}-${label}-transition" "9R3_${label}_TRANSITION"
  wait_log "V80-475" 240
  wait_log "anchor=$target" 240
  sleep 0.18
  capture_product "${prefix}-${label}-arrived" "9R3_${label}_ARRIVED"
}

wait_bed_pose() {
  local target="$1" pose="$2" prefix="$3" label="$4"
  request_and_wait_anchor "$target" "$prefix" "$label"
  wait_log "V80-483" 260
  wait_log "anchor=$target" 260
  wait_log "pose=$pose" 260
  wait_log "centralOwner=true" 260
  wait_log "noTeleport=true" 260
  sleep 0.35
  capture_product "${prefix}-${label}-stable" "9R3_${label}_STABLE"
  sleep 0.80
  capture_product "${prefix}-${label}-hold" "9R3_${label}_HOLD"
  adb logcat -d -v threadtime > "$OUT/${prefix}-${label}-runtime.txt" 2>&1 || true
  cat "$OUT/${prefix}-${label}-runtime.txt" >> "$OUT/9r3-runtime-log.txt"
}

[[ -s "$APK" ]] || fail "missing APK: $APK"
adb install -r "$APK" >/dev/null
adb shell am force-stop "$PACKAGE" || true
adb logcat -c || true
adb shell am start -W -n "$ACTIVITY" >/dev/null
wait_text "Mit Celin" /sdcard/9r3-home.xml "$OUT/60-9r3-home.xml"
sleep 1.5
PID_HOME="$(pid)"
[[ -n "$PID_HOME" ]] || fail "HOME process missing"
grep -Fq 'Celin 3D Ansicht' "$OUT/60-9r3-home.xml" || fail "HOME 3D stage missing"
capture_product 60-9r3-camera-talk-start 9R3_CAMERA_TALK_START

# Walk from camera talk to the already accepted safe bed approach.
request_and_wait_anchor bed_approach_anchor 61 9r3-bed-approach
wait_log "floorCalibrated=true" 180
[[ "$(pid)" = "$PID_HOME" ]] || fail "process changed after bed approach"
adb logcat -d -v threadtime > "$OUT/61-9r3-bed-approach-runtime.txt" 2>&1 || true
cat "$OUT/61-9r3-bed-approach-runtime.txt" >> "$OUT/9r3-runtime-log.txt"
grep -Fq 'cameraFixed=true' "$OUT/61-9r3-bed-approach-runtime.txt" || fail "bed approach lost fixed camera"
grep -Fq 'noTeleport=true' "$OUT/61-9r3-bed-approach-runtime.txt" || fail "bed approach missing no-teleport contract"

# One authored bed chain. No variants.
wait_bed_pose bed_edge_sit_anchor BED_EDGE_SIT 64 9r3-edge-sit
wait_bed_pose bed_relax_anchor BED_RELAX 68 9r3-relax
wait_bed_pose bed_lie_anchor BED_LIE 72 9r3-lie

# Sit up through the required reverse intermediates.
wait_bed_pose bed_relax_anchor BED_RELAX 76 9r3-sit-up-relax
wait_bed_pose bed_edge_sit_anchor BED_EDGE_SIT 80 9r3-edge-sit-return
wait_bed_pose bed_exit_anchor STAND_EXIT 84 9r3-stand-exit

# Walk away via the normal bed approach corridor, then return to camera talk.
request_and_wait_anchor bed_approach_anchor 88 9r3-bed-departure
request_and_wait_anchor room_walk_anchor_right 90 9r3-walk-away
request_and_wait_anchor camera_talk_anchor 92 9r3-camera-return
wait_log "V80-477" 240
sleep 1.0
capture_product 96-9r3-camera-talk-final 9R3_CAMERA_TALK_FINAL
adb logcat -d -v threadtime > "$OUT/92-9r3-camera-return-runtime.txt" 2>&1 || true
cat "$OUT/92-9r3-camera-return-runtime.txt" >> "$OUT/9r3-runtime-log.txt"
python3 ci/check-home-return-zoom.py \
  "$OUT/60-9r3-camera-talk-start.png" "$OUT/96-9r3-camera-talk-final.png" \
  | tee "$OUT/9r3-camera-return-zoom.txt"

for required in 'V80-472' 'V80-475' 'V80-483' 'V80-477'; do
  grep -Fq "$required" "$OUT/9r3-runtime-log.txt" || fail "required 9R.3 evidence missing: $required"
done
for pose in BED_EDGE_SIT BED_RELAX BED_LIE STAND_EXIT; do
  grep -Fq "pose=$pose" "$OUT/9r3-runtime-log.txt" || fail "required 9R.3 pose missing: $pose"
done
grep -Fq 'centralOwner=true' "$OUT/9r3-runtime-log.txt" || fail "central owner evidence missing"
grep -Fq 'cameraFixed=true' "$OUT/9r3-runtime-log.txt" || fail "fixed-camera evidence missing"
grep -Fq 'noTeleport=true' "$OUT/9r3-runtime-log.txt" || fail "no-teleport evidence missing"
if grep -Eq 'chair_sit_anchor|V80-499|V79-598|V79-599|V76-298|V76-299|V61-102|V61-199|REN-399|FATAL EXCEPTION|SIGABRT' "$OUT/9r3-runtime-log.txt"; then
  fail "runtime/source failure or premature chair phase detected during 9R.3"
fi

cat > "$OUT/9r3-summary.txt" <<EOF
PASS 9R.3 bed-chain technical gate
RUNTIME_HEAD=${GITHUB_SHA:-unknown}
CHAIN=camera_talk_to_bed_approach_to_edge_sit_to_relax_to_lie_to_relax_to_edge_sit_to_stand_to_walk_away_to_camera_talk
BED_EDGE_SIT=central_owner_eased
BED_RELAX=central_owner_eased
BED_LIE=central_owner_eased
BED_EXIT=central_owner_eased_stand
CAMERA=physically_fixed_no_chase
TELEPORT=false
CHAIR_PHASE=locked
MANUAL_BED_CONTACT_PENETRATION_FLOATING_TRANSITION_REVIEW=required
EOF

echo "PASS: 9R.3 bounded bed chain completed; mandatory manual visual review remains."
