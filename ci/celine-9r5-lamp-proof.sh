#!/usr/bin/env bash
# Targeted 9R.5 Lamp proof; technical checks are fail-closed and manual visual review is mandatory.
set -euo pipefail

APK="${1:-ci-apk/app-debug.apk}"
OUT="${2:-lamp-proof}"
PACKAGE="de.yahya.ai"
ACTIVITY="de.yahya.ai/.MainActivity"
ROOM_MARKER="celine-ci-room-action-v9r"
CAPTURE_MANIFEST="$OUT/9r5-lamp-capture-manifest.tsv"
mkdir -p "$OUT"
: > "$OUT/9r5-lamp-runtime-log.txt"
: > "$CAPTURE_MANIFEST"

fail() {
  echo "9R.5 lamp proof ERROR: $*" >&2
  adb logcat -d | grep -E 'de\.yahya\.ai|FATAL EXCEPTION|ROOM-|REN-|V45-|V61-|V70-|V76-|V77-|V80-' | tail -460 || true
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
  local needle="$1" steps="${2:-240}"
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
  cat "$OUT/$path" >> "$OUT/9r5-lamp-runtime-log.txt"
}

request_and_wait_anchor() {
  local target="$1" prefix="$2" label="$3" defer_runtime="${4:-false}" transition_delay="${5:-0.28}"
  adb logcat -c || true
  write_target "$target"
  wait_log "V80-472"
  wait_log "target=$target"
  sleep "$transition_delay"
  capture_product "${prefix}-${label}-transition" "9R5_LAMP_${label}_TRANSITION"
  wait_log "V80-475" 340
  wait_log "anchor=$target" 340
  sleep 0.20
  capture_product "${prefix}-${label}-arrived" "9R5_LAMP_${label}_ARRIVED"
  if [[ "$defer_runtime" != "true" ]]; then
    append_runtime "${prefix}-${label}-runtime.txt"
  fi
}

[[ -s "$APK" ]] || fail "missing APK: $APK"
adb install -r "$APK" >/dev/null
adb shell am force-stop "$PACKAGE" || true
adb logcat -c || true
adb shell am start -W -n "$ACTIVITY" >/dev/null
wait_text "Mit Celin" /sdcard/9r5-lamp-home.xml "$OUT/210-9r5-lamp-home.xml"
sleep 1.5
PID_HOME="$(pid)"
[[ -n "$PID_HOME" ]] || fail "HOME process missing"
capture_product 210-9r5-lamp-camera-talk-start 9R5_LAMP_CAMERA_TALK_START
append_runtime 210-9r5-lamp-initial-runtime.txt

# First deliberate Lamp visit. The safe runtime root is +0.10 m toward room center and keeps the
# whole room_walk_left -> Lamp segment outside the floor-lamp AABB for the 0.35 m avatar capsule.
request_and_wait_anchor lamp_anchor 212 9r5-lamp-on true 0.55
wait_log "V80-483" 340
wait_log "anchor=lamp_anchor" 340
wait_log "pose=LAMP_INTERACT" 340
wait_log "V80-484" 340
wait_log "enabled=true" 340
wait_log "lightEntity=floor_lamp_light" 340
wait_log "handContact=false" 340
wait_log "centralOwner=true" 340
wait_log "noTeleport=true" 340
sleep 0.20
capture_product 216-9r5-lamp-on-look-stable 9R5_LAMP_ON_LOOK_STABLE

# The CI emulator advances capped deltaSeconds conservatively. Wait long enough for the bounded
# lamp-look contribution to fade completely before judging social/user re-acquisition.
sleep 4.50
capture_product 217-9r5-lamp-user-reacquire 9R5_LAMP_USER_REACQUIRE
[[ "$(pid)" = "$PID_HOME" ]] || fail "process changed during first lamp hold"
append_runtime 212-9r5-lamp-on-runtime.txt
append_runtime 216-9r5-lamp-on-look-runtime.txt
append_runtime 217-9r5-lamp-reacquire-runtime.txt

# Walk away so a second explicit Lamp visit is a new bounded interaction and can toggle the real
# Filament light back OFF. This proves the environment state is reversible, not a baked frame.
request_and_wait_anchor room_walk_anchor_left 220 9r5-lamp-first-walk-away false 0.55
request_and_wait_anchor lamp_anchor 222 9r5-lamp-off true 0.55
wait_log "V80-483" 340
wait_log "anchor=lamp_anchor" 340
wait_log "pose=LAMP_INTERACT" 340
wait_log "V80-484" 340
wait_log "enabled=false" 340
wait_log "lightEntity=floor_lamp_light" 340
wait_log "handContact=false" 340
sleep 0.20
capture_product 224-9r5-lamp-off-stable 9R5_LAMP_OFF_STABLE
append_runtime 222-9r5-lamp-off-runtime.txt
append_runtime 224-9r5-lamp-off-state-runtime.txt

request_and_wait_anchor room_walk_anchor_left 226 9r5-lamp-final-walk-away false 0.55
request_and_wait_anchor camera_talk_anchor 230 9r5-lamp-camera-return
wait_log "V80-477" 280
sleep 1.0
capture_product 232-9r5-lamp-camera-talk-final 9R5_LAMP_CAMERA_TALK_FINAL
append_runtime 232-9r5-lamp-camera-final-runtime.txt

validate_products
python3 ci/check-home-return-zoom.py \
  "$OUT/210-9r5-lamp-camera-talk-start.png" "$OUT/232-9r5-lamp-camera-talk-final.png" \
  | tee "$OUT/9r5-lamp-camera-return-zoom.txt"

for required in 'ROOM-120' 'V80-471' 'V80-472' 'V80-475' 'V80-483' 'V80-484' 'V80-477'; do
  grep -Fq "$required" "$OUT/9r5-lamp-runtime-log.txt" || fail "required lamp evidence missing: $required"
done
grep -Fq 'lampSafetyX=+0.1' "$OUT/9r5-lamp-runtime-log.txt" || fail "lamp X safety evidence missing"
grep -Fq 'lampDepth=authored' "$OUT/9r5-lamp-runtime-log.txt" || fail "lamp authored-depth evidence missing"
grep -Fq 'lampReach=false' "$OUT/9r5-lamp-runtime-log.txt" || fail "lamp no-reach evidence missing"
grep -Fq 'lampSwitchTarget=false' "$OUT/9r5-lamp-runtime-log.txt" || fail "lamp no-switch-target evidence missing"
grep -Fq 'lampLightEntity=true' "$OUT/9r5-lamp-runtime-log.txt" || fail "lamp light-entity contract evidence missing"
grep -Fq 'pose=LAMP_INTERACT' "$OUT/9r5-lamp-runtime-log.txt" || fail "LAMP_INTERACT evidence missing"
grep -Fq 'enabled=true lightEntity=floor_lamp_light' "$OUT/9r5-lamp-runtime-log.txt" || fail "lamp ON evidence missing"
grep -Fq 'enabled=false lightEntity=floor_lamp_light' "$OUT/9r5-lamp-runtime-log.txt" || fail "lamp OFF evidence missing"
grep -Fq 'handContact=false switchTarget=false cameraFixed=true' "$OUT/9r5-lamp-runtime-log.txt" || fail "lamp no-contact/fixed-camera evidence missing"
grep -Fq 'centralOwner=true' "$OUT/9r5-lamp-runtime-log.txt" || fail "central owner evidence missing"
grep -Fq 'noTeleport=true' "$OUT/9r5-lamp-runtime-log.txt" || fail "no-teleport evidence missing"
if grep -Eq 'pose=(WINDOW_STAND|DRESSER_STAND|MIRROR_STAND|SHELF_STAND|CHAIR_SIT|BED_EDGE_SIT|BED_RELAX|BED_LIE)|V80-499|V79-598|V79-599|V76-298|V76-299|V61-102|V61-199|REN-399|ROOM-199|FATAL EXCEPTION|SIGABRT' "$OUT/9r5-lamp-runtime-log.txt"; then
  fail "runtime/source failure or protected non-Lamp pose activation detected during lamp proof"
fi

cat > "$OUT/9r5-lamp-summary.txt" <<EOF
PASS 9R.5 lamp technical gate
RUNTIME_HEAD=${GITHUB_SHA:-unknown}
CHAIN=camera_talk_to_lamp_on_look_to_user_reacquire_to_walk_away_to_lamp_off_to_walk_away_to_camera_talk
LAMP_INTERACT=central_owner_bounded_pose
LAMP_ROOT=x_plus_0_10_authored_depth
LAMP_LIGHT=floor_lamp_light
LAMP_ON_OFF=reversible_two_deliberate_visits
LAMP_REACH=false
SWITCH_TARGET=false
HAND_CONTACT=false
USER_REACQUIRE=bounded_lamp_look_fades_to_social_gaze
CAMERA=physically_fixed_no_chase
TELEPORT=false
PROTECTED_WINDOW_DRESSER_MIRROR_SHELF_9R3_9R4=not_invoked
MANUAL_LAMP_CLEARANCE_LOOK_LIGHTING_REACQUIRE_TRANSITION_REVIEW=required
EOF

echo "PASS: 9R.5 bounded Lamp interaction completed; mandatory manual visual review remains."
