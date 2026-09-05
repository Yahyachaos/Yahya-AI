#!/usr/bin/env bash
# Targeted 9R.5 shelf proof; manual visual review remains mandatory.
set -euo pipefail

APK="${1:-ci-apk/app-debug.apk}"
OUT="${2:-avatar-lab-proof}"
PACKAGE="de.yahya.ai"
ACTIVITY="de.yahya.ai/.MainActivity"
ROOM_MARKER="celine-ci-room-action-v9r"
CAPTURE_MANIFEST="$OUT/9r5-shelf-capture-manifest.tsv"
mkdir -p "$OUT"
: > "$OUT/9r5-shelf-runtime-log.txt"
: > "$CAPTURE_MANIFEST"

fail() {
  echo "9R.5 shelf proof ERROR: $*" >&2
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
  cat "$OUT/$path" >> "$OUT/9r5-shelf-runtime-log.txt"
}

request_and_wait_anchor() {
  local target="$1" prefix="$2" label="$3" defer_runtime="${4:-false}" transition_delay="${5:-0.28}"
  adb logcat -c || true
  write_target "$target"
  wait_log "V80-472"
  wait_log "target=$target"
  sleep "$transition_delay"
  capture_product "${prefix}-${label}-transition" "9R5_SHELF_${label}_TRANSITION"
  wait_log "V80-475" 320
  wait_log "anchor=$target" 320
  sleep 0.20
  capture_product "${prefix}-${label}-arrived" "9R5_SHELF_${label}_ARRIVED"
  if [[ "$defer_runtime" != "true" ]]; then
    append_runtime "${prefix}-${label}-runtime.txt"
  fi
}

[[ -s "$APK" ]] || fail "missing APK: $APK"
adb install -r "$APK" >/dev/null
adb shell am force-stop "$PACKAGE" || true
adb logcat -c || true
adb shell am start -W -n "$ACTIVITY" >/dev/null
wait_text "Mit Celin" /sdcard/9r5-shelf-home.xml "$OUT/190-9r5-shelf-home.xml"
sleep 1.5
PID_HOME="$(pid)"
[[ -n "$PID_HOME" ]] || fail "HOME process missing"
capture_product 190-9r5-shelf-camera-talk-start 9R5_SHELF_CAMERA_TALK_START
append_runtime 190-9r5-shelf-initial-runtime.txt

# Proof #158 established that authored Shelf depth is outside the fixed-camera reviewable room view.
# The runtime now reuses the accepted Window +0.65 m camera-side depth correction and moves Shelf
# +0.50 m toward room center, keeping both adjacent Shelf segments >0.4545 m right of the chair AABB.
request_and_wait_anchor shelf_anchor 192 9r5-shelf true 0.55

wait_log "V80-483" 320
wait_log "anchor=shelf_anchor" 320
wait_log "pose=SHELF_STAND" 320
wait_log "centralOwner=true" 320
wait_log "noTeleport=true" 320
sleep 0.25
capture_product 196-9r5-shelf-look-stable 9R5_SHELF_LOOK_STABLE

# Slow CI rendering can advance the runtime's capped deltaSeconds more slowly than wall time.
# Use the conservative envelope already proven for Mirror so the bounded shelf-look contribution
# has fully faded before sampling normal social/user gaze again.
sleep 4.50
capture_product 197-9r5-shelf-user-reacquire 9R5_SHELF_USER_REACQUIRE
[[ "$(pid)" = "$PID_HOME" ]] || fail "process changed during shelf hold"
append_runtime 192-9r5-shelf-runtime.txt
append_runtime 196-9r5-shelf-look-runtime.txt
append_runtime 197-9r5-shelf-reacquire-runtime.txt

# Walk away through the adjacent authored back-center anchor, then return to camera talk.
request_and_wait_anchor back_center_nav_anchor 200 9r5-shelf-walk-away false 0.55
request_and_wait_anchor camera_talk_anchor 204 9r5-shelf-camera-return
wait_log "V80-477" 260
sleep 1.0
capture_product 206-9r5-shelf-camera-talk-final 9R5_SHELF_CAMERA_TALK_FINAL
append_runtime 206-9r5-shelf-camera-final-runtime.txt

validate_products
python3 ci/check-home-return-zoom.py \
  "$OUT/190-9r5-shelf-camera-talk-start.png" "$OUT/206-9r5-shelf-camera-talk-final.png" \
  | tee "$OUT/9r5-shelf-camera-return-zoom.txt"

for required in 'V80-471' 'V80-472' 'V80-475' 'V80-483' 'V80-477'; do
  grep -Fq "$required" "$OUT/9r5-shelf-runtime-log.txt" || fail "required shelf evidence missing: $required"
done
grep -Fq 'shelfSafetyX=+0.5' "$OUT/9r5-shelf-runtime-log.txt" || fail "shelf X safety evidence missing"
grep -Fq 'shelfSafetyZ=+0.65' "$OUT/9r5-shelf-runtime-log.txt" || fail "shelf Z safety evidence missing"
grep -Fq 'shelfDepth=cameraSide' "$OUT/9r5-shelf-runtime-log.txt" || fail "shelf camera-side depth evidence missing"
grep -Fq 'shelfReach=false' "$OUT/9r5-shelf-runtime-log.txt" || fail "shelf no-reach evidence missing"
grep -Fq 'shelfBookPickup=false' "$OUT/9r5-shelf-runtime-log.txt" || fail "shelf no-book-pickup evidence missing"
grep -Fq 'pose=SHELF_STAND' "$OUT/9r5-shelf-runtime-log.txt" || fail "SHELF_STAND evidence missing"
grep -Fq 'centralOwner=true' "$OUT/9r5-shelf-runtime-log.txt" || fail "central owner evidence missing"
grep -Fq 'cameraFixed=true' "$OUT/9r5-shelf-runtime-log.txt" || fail "fixed-camera evidence missing"
grep -Fq 'noTeleport=true' "$OUT/9r5-shelf-runtime-log.txt" || fail "no-teleport evidence missing"
if grep -Eq 'pose=(WINDOW_STAND|DRESSER_STAND|MIRROR_STAND|CHAIR_SIT|BED_EDGE_SIT|BED_RELAX|BED_LIE)|V80-499|V79-598|V79-599|V76-298|V76-299|V61-102|V61-199|REN-399|FATAL EXCEPTION|SIGABRT' "$OUT/9r5-shelf-runtime-log.txt"; then
  fail "runtime/source failure or protected non-Shelf pose activation detected during shelf proof"
fi

cat > "$OUT/9r5-shelf-summary.txt" <<EOF
PASS 9R.5 shelf technical gate
RUNTIME_HEAD=${GITHUB_SHA:-unknown}
CHAIN=camera_talk_to_shelf_look_to_user_reacquire_to_walk_away_to_camera_talk
SHELF_STAND=central_owner_bounded_pose
SHELF_ROOT=x_plus_0_50_camera_side_depth
SHELF_REACH=false
BOOK_PICKUP=false
HAND_CONTACT=false
USER_REACQUIRE=bounded_shelf_look_fades_to_social_gaze
CAMERA=physically_fixed_no_chase
TELEPORT=false
PROTECTED_WINDOW_DRESSER_MIRROR_9R3_9R4=not_invoked
MANUAL_SHELF_CLEARANCE_ROUTE_LOOK_REACQUIRE_TRANSITION_REVIEW=required
EOF

echo "PASS: 9R.5 bounded shelf interaction completed; mandatory manual visual review remains."
