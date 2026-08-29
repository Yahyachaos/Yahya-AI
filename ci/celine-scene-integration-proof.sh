#!/usr/bin/env bash
# 9R.1 proof trigger head: CI-only; runtime fingerprint must resolve to Build #682.
set -euo pipefail
APK="${1:-ci-apk/app-debug.apk}"; OUT="${2:-avatar-lab-proof}"; PACKAGE="de.yahya.ai"; ACTIVITY="de.yahya.ai/.MainActivity"; ROOM_MARKER="celine-ci-room-action-v9r"
mkdir -p "$OUT"; : > "$OUT/9r1-runtime-log.txt"
fail(){ echo "9R.1 locomotion proof ERROR: $*" >&2; adb logcat -d | grep -E 'de\.yahya\.ai|FATAL EXCEPTION|REN-|V45-|V61-|V70-|V76-|V77-|V80-' | tail -360 || true; exit 1; }
pid(){ adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r ' || true; }
dump_ui(){ local remote="$1" local_file="$2" attempt; for attempt in 1 2 3 4; do adb shell rm -f "$remote" >/dev/null 2>&1 || true; rm -f "$local_file"; if adb shell uiautomator dump "$remote" >/dev/null 2>&1 && adb pull "$remote" "$local_file" >/dev/null 2>&1 && grep -q '<hierarchy' "$local_file"; then return 0; fi; [[ -n "$(pid)" ]] || fail "app process died while collecting $local_file"; sleep 0.55; done; fail "could not collect valid UI hierarchy: $local_file"; }
wait_text(){ local text="$1" remote="$2" local_file="$3"; for _ in $(seq 1 28); do if dump_ui "$remote" "$local_file" && grep -Fq "$text" "$local_file"; then return 0; fi; sleep 0.4; done; fail "timed out waiting for UI text: $text"; }
capture_product(){ local name="$1" label="$2"; adb exec-out screencap -p > "$OUT/$name.png"; [[ -s "$OUT/$name.png" ]] || fail "empty screenshot: $name"; python3 ci/check-real-celine-render.py "$OUT/$name.png" "$label"; python3 ci/check-celine-person-presence.py "$OUT/$name.png" "$label"; }
wait_log(){ local needle="$1" steps="${2:-90}"; for _ in $(seq 1 "$steps"); do if adb logcat -d | grep -Fq "$needle"; then return 0; fi; [[ -n "$(pid)" ]] || fail "app process died while waiting for log: $needle"; sleep 0.22; done; fail "timed out waiting for runtime evidence: $needle"; }
write_target(){ local target="$1"; adb shell "run-as $PACKAGE sh -c 'printf %s $target > files/$ROOM_MARKER.tmp && mv files/$ROOM_MARKER.tmp files/$ROOM_MARKER'" || fail "could not write private 9R room marker: $target"; }
run_destination(){ local target="$1" prefix="$2" label="$3"; adb logcat -c || true; write_target "$target"; wait_log "V80-472"; wait_log "target=$target"; wait_log "V80-473"; sleep 0.24; capture_product "${prefix}-${label}-turn" "9R1_${label}_TURN"; wait_log "V80-474"; capture_product "${prefix}-${label}-walk-a" "9R1_${label}_WALK_A"; sleep 0.40; capture_product "${prefix}-${label}-walk-b" "9R1_${label}_WALK_B"; wait_log "V80-475" 180; wait_log "anchor=$target" 180; capture_product "${prefix}-${label}-stop" "9R1_${label}_STOP"; sleep 1.05; capture_product "${prefix}-${label}-idle" "9R1_${label}_IDLE"; adb logcat -d -v threadtime > "$OUT/${prefix}-${label}-runtime.txt" 2>&1 || true; cat "$OUT/${prefix}-${label}-runtime.txt" >> "$OUT/9r1-runtime-log.txt"; grep -Fq "V80-472" "$OUT/${prefix}-${label}-runtime.txt" || fail "$label missing accepted-route evidence"; grep -Fq "target=$target" "$OUT/${prefix}-${label}-runtime.txt" || fail "$label missing exact target evidence"; grep -Fq "cameraFixed=true" "$OUT/${prefix}-${label}-runtime.txt" || fail "$label did not preserve fixed webcam camera contract"; grep -Fq "V80-473" "$OUT/${prefix}-${label}-runtime.txt" || fail "$label missing turn-before-walk evidence"; grep -Fq "V80-474" "$OUT/${prefix}-${label}-runtime.txt" || fail "$label missing Walking activation evidence"; grep -Fq "clip=Walking" "$OUT/${prefix}-${label}-runtime.txt" || fail "$label did not use canonical Walking clip"; grep -Fq "sourceSha256=95c68ce04d85bbffbb2fd3253dc211bb4047283744b0efa83717353b62d03b83" "$OUT/${prefix}-${label}-runtime.txt" || fail "$label canonical Walking source SHA mismatch"; grep -Fq "V80-475" "$OUT/${prefix}-${label}-runtime.txt" || fail "$label missing final anchor evidence"; grep -Fq "anchor=$target" "$OUT/${prefix}-${label}-runtime.txt" || fail "$label did not reach requested final anchor"; grep -Fq "walkStopped=true" "$OUT/${prefix}-${label}-runtime.txt" || fail "$label did not stop the gait at arrival"; grep -Fq "noTeleport=true" "$OUT/${prefix}-${label}-runtime.txt" || fail "$label missing no-teleport arrival contract"; if grep -Eq 'V80-479|V80-499|V79-598|V79-599|V76-298|V76-299|V61-102|V61-199|REN-399|FATAL EXCEPTION|SIGABRT' "$OUT/${prefix}-${label}-runtime.txt"; then fail "$label runtime/source failure detected"; fi; }
[[ -s "$APK" ]] || fail "missing APK: $APK"; adb install -r "$APK" >/dev/null; adb shell am force-stop "$PACKAGE" || true; adb logcat -c || true; adb shell am start -W -n "$ACTIVITY" >/dev/null
wait_text "Mit Celin" /sdcard/9r1-home.xml "$OUT/15-9r1-home.xml"; sleep 1.5; PID_HOME="$(pid)"; [[ -n "$PID_HOME" ]] || fail "HOME process missing"; grep -Fq 'Celin 3D Ansicht' "$OUT/15-9r1-home.xml" || fail "HOME 3D stage missing"; capture_product 15-9r1-camera-talk-start 9R1_CAMERA_TALK_START
run_destination bed_approach_anchor 16 9r1-bed; [[ "$(pid)" = "$PID_HOME" ]] || fail "process changed after bed route"
run_destination chair_approach_anchor 21 9r1-chair; [[ "$(pid)" = "$PID_HOME" ]] || fail "process changed after chair route"
run_destination window_anchor 26 9r1-window; [[ "$(pid)" = "$PID_HOME" ]] || fail "process changed after window route"
run_destination camera_talk_anchor 31 9r1-camera-return; [[ "$(pid)" = "$PID_HOME" ]] || fail "process changed after camera return route"
sleep 1.1; capture_product 36-9r1-camera-final 9R1_CAMERA_FINAL; python3 ci/check-home-return-zoom.py "$OUT/15-9r1-camera-talk-start.png" "$OUT/36-9r1-camera-final.png" | tee "$OUT/9r1-camera-return-zoom.txt"
for required in 'V80-472' 'V80-473' 'V80-474' 'V80-475'; do grep -Fq "$required" "$OUT/9r1-runtime-log.txt" || fail "required 9R.1 evidence missing: $required"; done
if grep -Eq 'V80-479|V80-499|V79-598|V79-599|V76-298|V76-299|V61-102|V61-199|REN-399|FATAL EXCEPTION|SIGABRT' "$OUT/9r1-runtime-log.txt"; then fail "runtime/source failure detected during 9R.1 route chain"; fi
cat > "$OUT/9r1-summary.txt" <<EOF
PASS 9R.1 locomotion technical gate
RUNTIME_HEAD=${GITHUB_SHA:-unknown}
WALKING_SOURCE_SHA256=95c68ce04d85bbffbb2fd3253dc211bb4047283744b0efa83717353b62d03b83
CAMERA_TALK_TO_BED=turn_walk_stop_idle
BED_TO_CHAIR=turn_walk_stop_idle
CHAIR_TO_WINDOW=turn_walk_stop_idle
WINDOW_TO_CAMERA_TALK=turn_walk_stop_idle
NAV_GRAPH=bounded_4R_contract
CAMERA=physically_fixed_no_chase
TELEPORT=false
CALL_SEATED_CONTRACT=untouched
MANUAL_GAIT_FOOTSKATE_CLEARANCE_REVIEW=required
EOF
echo "PASS: 9R.1 bounded room locomotion chain completed; mandatory manual visual review still required."
