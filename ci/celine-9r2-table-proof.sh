#!/usr/bin/env bash
# Targeted 9R.2 camera/table interaction proof; runtime fingerprint must resolve to an exact build.
set -euo pipefail

APK="${1:-ci-apk/app-debug.apk}"
OUT="${2:-avatar-lab-proof}"
PACKAGE="de.yahya.ai"
ACTIVITY="de.yahya.ai/.MainActivity"
ROOM_MARKER="celine-ci-room-action-v9r"
mkdir -p "$OUT"
: > "$OUT/9r2-runtime-log.txt"

fail() {
  echo "9R.2 table proof ERROR: $*" >&2
  adb logcat -d | grep -E 'de\.yahya\.ai|FATAL EXCEPTION|REN-|V45-|V61-|V70-|V76-|V77-|V80-' | tail -360 || true
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
  # A SurfaceView can transiently keep uiautomator busy while Filament settles. Do not abort the
  # whole proof here: wait_text owns the bounded outer retry loop and remains fail-closed.
  return 1
}

wait_text() {
  local text="$1" remote="$2" local_file="$3"
  for _ in $(seq 1 28); do
    if dump_ui "$remote" "$local_file" && grep -Fq "$text" "$local_file"; then return 0; fi
    [[ -n "$(pid)" ]] || fail "app process died while waiting for UI text: $text"
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
  local needle="$1" steps="${2:-120}"
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

run_nav_hold() {
  local target="$1" prefix="$2" label="$3"
  adb logcat -c || true
  write_target "$target"
  wait_log "V80-472"
  wait_log "target=$target"
  wait_log "V80-473"
  sleep 0.24
  capture_product "${prefix}-${label}-turn" "9R2_${label}_TURN"
  wait_log "V80-474"
  sleep 0.34
  capture_product "${prefix}-${label}-walk" "9R2_${label}_WALK"
  wait_log "V80-475" 180
  wait_log "anchor=$target" 180
  wait_log "floorCalibrated=true" 180
  capture_product "${prefix}-${label}-stop" "9R2_${label}_STOP"
  sleep 0.85
  capture_product "${prefix}-${label}-idle" "9R2_${label}_IDLE"
  adb logcat -d -v threadtime > "$OUT/${prefix}-${label}-runtime.txt" 2>&1 || true
  cat "$OUT/${prefix}-${label}-runtime.txt" >> "$OUT/9r2-runtime-log.txt"
  grep -Fq "cameraFixed=true" "$OUT/${prefix}-${label}-runtime.txt" || fail "$label lost fixed-camera contract"
  grep -Fq "anchor=$target" "$OUT/${prefix}-${label}-runtime.txt" || fail "$label did not reach target"
  grep -Fq "floorCalibrated=true" "$OUT/${prefix}-${label}-runtime.txt" || fail "$label missing floor calibration"
  grep -Fq "noTeleport=true" "$OUT/${prefix}-${label}-runtime.txt" || fail "$label missing no-teleport contract"
}

[[ -s "$APK" ]] || fail "missing APK: $APK"
adb install -r "$APK" >/dev/null
adb shell am force-stop "$PACKAGE" || true
adb logcat -c || true
adb shell am start -W -n "$ACTIVITY" >/dev/null
wait_text "Mit Celin" /sdcard/9r2-home.xml "$OUT/40-9r2-home.xml"
sleep 1.5
PID_HOME="$(pid)"
[[ -n "$PID_HOME" ]] || fail "HOME process missing"
grep -Fq 'Celin 3D Ansicht' "$OUT/40-9r2-home.xml" || fail "HOME 3D stage missing"
capture_product 40-9r2-camera-talk-start 9R2_CAMERA_TALK_START

run_nav_hold camera_near_anchor 41 9r2-camera-near
[[ "$(pid)" = "$PID_HOME" ]] || fail "process changed after camera-near route"

run_nav_hold foreground_table_approach_anchor 46 9r2-table-approach
[[ "$(pid)" = "$PID_HOME" ]] || fail "process changed after table-approach route"

adb logcat -c || true
write_target foreground_table_lean_anchor
wait_log "V80-472"
wait_log "target=foreground_table_lean_anchor"
wait_log "route=[foreground_table_approach_anchor, foreground_table_lean_anchor]"
wait_log "V80-474"
sleep 0.28
capture_product 51-9r2-table-lean-transition 9R2_TABLE_LEAN_TRANSITION
wait_log "V80-475" 180
wait_log "anchor=foreground_table_lean_anchor" 180
wait_log "floorCalibrated=true" 180
wait_log "V80-480" 180
wait_log "handContact=false" 180
capture_product 52-9r2-table-lean-stable 9R2_TABLE_LEAN_STABLE
sleep 1.0
capture_product 53-9r2-table-lean-hold 9R2_TABLE_LEAN_HOLD
adb logcat -d -v threadtime > "$OUT/51-9r2-table-lean-runtime.txt" 2>&1 || true
cat "$OUT/51-9r2-table-lean-runtime.txt" >> "$OUT/9r2-runtime-log.txt"
grep -Fq "V80-480" "$OUT/51-9r2-table-lean-runtime.txt" || fail "stable table lean diagnostic missing"
grep -Fq "centralOwner=true" "$OUT/51-9r2-table-lean-runtime.txt" || fail "table lean is not central-owner evidence"
grep -Fq "cameraFixed=true" "$OUT/51-9r2-table-lean-runtime.txt" || fail "table lean lost fixed camera"
grep -Fq "handContact=false" "$OUT/51-9r2-table-lean-runtime.txt" || fail "optional hand contact contract unclear"

adb logcat -c || true
write_target camera_talk_anchor
wait_log "V80-472"
wait_log "target=camera_talk_anchor"
wait_log "V80-474"
sleep 0.34
capture_product 54-9r2-camera-return-walk 9R2_CAMERA_RETURN_WALK
wait_log "V80-475" 220
wait_log "anchor=camera_talk_anchor" 220
wait_log "V80-477" 220
sleep 1.0
capture_product 55-9r2-camera-talk-final 9R2_CAMERA_TALK_FINAL
adb logcat -d -v threadtime > "$OUT/54-9r2-camera-return-runtime.txt" 2>&1 || true
cat "$OUT/54-9r2-camera-return-runtime.txt" >> "$OUT/9r2-runtime-log.txt"
python3 ci/check-home-return-zoom.py \
  "$OUT/40-9r2-camera-talk-start.png" "$OUT/55-9r2-camera-talk-final.png" \
  | tee "$OUT/9r2-camera-return-zoom.txt"

for required in 'V80-472' 'V80-473' 'V80-474' 'V80-475' 'V80-480' 'V80-477'; do
  grep -Fq "$required" "$OUT/9r2-runtime-log.txt" || fail "required 9R.2 evidence missing: $required"
done
if grep -Eq 'V80-479|V80-499|V79-598|V79-599|V76-298|V76-299|V61-102|V61-199|REN-399|FATAL EXCEPTION|SIGABRT' "$OUT/9r2-runtime-log.txt"; then
  fail "runtime/source failure detected during 9R.2 camera/table chain"
fi

cat > "$OUT/9r2-summary.txt" <<EOF
PASS 9R.2 camera/table technical gate
RUNTIME_HEAD=${GITHUB_SHA:-unknown}
CAMERA_TALK_TO_NEAR=turn_walk_stop_idle
NEAR_TO_TABLE_APPROACH=turn_walk_stop_idle
TABLE_APPROACH_TO_LEAN=bounded_contact_edge_with_eased_central_owner_lean
TABLE_HAND_CONTACT=false_optional_omitted
RETURN_TO_CAMERA_TALK=walk_settle_ambient_handoff
CAMERA=physically_fixed_no_chase
TELEPORT=false
LATER_CONTACT_PHASES=locked
MANUAL_TABLE_CLEARANCE_LEAN_RETURN_REVIEW=required
EOF

echo "PASS: 9R.2 bounded camera/table interaction chain completed; mandatory manual visual review remains."
