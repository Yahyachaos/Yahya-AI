#!/usr/bin/env bash
set -euo pipefail

APK="${1:-ci-apk/app-debug.apk}"
OUT="${2:-layered-owner-proof}"
PACKAGE="de.yahya.ai"
MAIN_ACTIVITY="de.yahya.ai/.MainActivity"
LAB_ACTIVITY="de.yahya.ai/.CelineAvatarLabCaptureActivity"
mkdir -p "$OUT"

collect() {
  timeout 15s adb logcat -d -v threadtime > "$OUT/logcat.txt" 2>&1 || true
  timeout 10s adb shell dumpsys window windows > "$OUT/window.txt" 2>&1 || true
}
trap collect EXIT

fail() {
  echo "Layered-owner proof ERROR: $*" >&2
  adb logcat -d | grep -E 'de\.yahya\.ai|FATAL EXCEPTION|SIGABRT|V80-|V79-|V77-|V76-|V61-|REN-|CTL-' | tail -300 || true
  exit 1
}

wait_log() {
  local needle="$1"
  local label="$2"
  for _ in $(seq 1 35); do
    if adb logcat -d | grep -Fq "$needle"; then
      echo "Ready: $label"
      return 0
    fi
    sleep 1
  done
  fail "timed out waiting for $label ($needle)"
}

capture_product() {
  local name="$1"
  local label="$2"
  adb exec-out screencap -p > "$OUT/$name.png"
  python3 ci/check-real-celine-render.py "$OUT/$name.png" "$label"
  python3 ci/check-celine-person-presence.py "$OUT/$name.png" "$label"
}

capture_lab() {
  local name="$1"
  local label="$2"
  local attempt
  for attempt in 1 2 3; do
    adb exec-out screencap -p > "$OUT/$name.png"
    if python3 ci/check-real-celine-render.py "$OUT/$name.png" "$label"; then
      echo "Visible Avatar Lab production frame: $label attempt=$attempt"
      return 0
    fi
    sleep 0.55
  done
  fail "Avatar Lab production frame stayed blank: $label"
}

[[ -s "$APK" ]] || fail "missing APK: $APK"
adb install -r "$APK" >/dev/null
adb shell pm clear "$PACKAGE" >/dev/null || fail "could not clear app state"
adb shell am force-stop "$PACKAGE" || true
adb logcat -c || true
adb shell pm grant "$PACKAGE" android.permission.RECORD_AUDIO || true
adb shell am start -W -n "$MAIN_ACTIVITY" >/dev/null

wait_log 'V61-110' 'protected v61 rig-scale correction'
wait_log 'V80-400' 'central production owner bind'
wait_log 'block5SixJointArms=true fingerBones=false' 'Block 5 six-joint/no-finger contract'
wait_log 'V80-410' 'central HOME layered frame'
wait_log 'V80-450' 'Block 5 HOME asynchronous arm/hand layer'
wait_log 'V76-210' 'guarded v76 face/morph output'
wait_log 'CTL-350' 'visible production Celine'
sleep 1.2

adb shell uiautomator dump /sdcard/celine-v80-owner-home.xml >/dev/null || fail "HOME UI dump failed"
adb pull /sdcard/celine-v80-owner-home.xml "$OUT/home.xml" >/dev/null || fail "HOME UI pull failed"
capture_product home HOME
grep -q 'Celin 3D Ansicht' "$OUT/home.xml" || fail "HOME 3D stage missing"
grep -q 'Mit Celin' "$OUT/home.xml" || fail "HOME CALL entry missing"
# Keep a second actual-product HOME frame for human temporal inspection.
sleep 1.35
capture_product home-motion-b HOME_MOTION_B

read -r TAP_X TAP_Y <<< "$(python3 - "$OUT/home.xml" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

root = ET.parse(sys.argv[1]).getroot()
for node in root.iter('node'):
    if 'Mit Celin' not in node.attrib.get('text', ''):
        continue
    match = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds', ''))
    if not match:
        continue
    x1, y1, x2, y2 = map(int, match.groups())
    print((x1 + x2) // 2, (y1 + y2) // 2)
    raise SystemExit
raise SystemExit('CALL button bounds missing')
PY
)"
[[ -n "${TAP_X:-}" && -n "${TAP_Y:-}" ]] || fail "CALL tap coordinates missing"
adb shell input tap "$TAP_X" "$TAP_Y"
wait_log 'target=CALL eased=true snap=false' 'eased HOME-to-CALL handoff'
wait_log 'V80-420' 'central CALL layered frame'
wait_log 'V80-451' 'Block 5 CALL asynchronous arm/hand layer'
sleep 1.3

adb shell uiautomator dump /sdcard/celine-v80-owner-call.xml >/dev/null || fail "CALL UI dump failed"
adb pull /sdcard/celine-v80-owner-call.xml "$OUT/call.xml" >/dev/null || fail "CALL UI pull failed"
capture_product call CALL
grep -q 'Live mit Celin' "$OUT/call.xml" || fail "CALL overlay missing"
grep -q 'Celin 3D Ansicht' "$OUT/call.xml" || fail "CALL 3D stage missing"

# Block 5 is temporal: one static screenshot cannot prove non-frozen arms/hands. Capture two more
# actual-product CALL frames at deliberately different phases and fail closed if either lateral arm
# region remains effectively unchanged. Human review of all three frames is still mandatory.
sleep 1.45
capture_product call-motion-b CALL_MOTION_B
sleep 1.45
capture_product call-motion-c CALL_MOTION_C
python3 ci/celine-block5-motion-compare.py "$OUT/call.png" "$OUT/call-motion-b.png" CALL_A_TO_B \
  | tee "$OUT/call-motion-a-b.txt"
python3 ci/celine-block5-motion-compare.py "$OUT/call-motion-b.png" "$OUT/call-motion-c.png" CALL_B_TO_C \
  | tee "$OUT/call-motion-b-c.txt"

adb shell input keyevent 4
wait_log 'target=HOME eased=true snap=false' 'eased CALL-to-HOME handoff'
sleep 1.2
home_return_ui_ready=false
for attempt in 1 2 3; do
  adb shell rm -f /sdcard/celine-v80-owner-return.xml || true
  rm -f "$OUT/home-return.xml"
  if adb shell uiautomator dump /sdcard/celine-v80-owner-return.xml \
      >"$OUT/home-return-uiautomator-attempt-$attempt.txt" 2>&1 \
      && adb pull /sdcard/celine-v80-owner-return.xml "$OUT/home-return.xml" >/dev/null 2>&1 \
      && grep -q '<hierarchy' "$OUT/home-return.xml"; then
    echo "HOME-return UI ready on attempt $attempt"
    home_return_ui_ready=true
    break
  fi
  echo "HOME-return UI transient failure on attempt $attempt; retrying..."
  sleep 0.75
done
[[ "$home_return_ui_ready" == true ]] \
  || fail "HOME-return UI dump/pull stayed invalid after 3 attempts"
capture_product home-return HOME_RETURN
grep -q 'Mit Celin' "$OUT/home-return.xml" || fail "HOME did not recover"
python3 ci/check-home-return-zoom.py "$OUT/home.png" "$OUT/home-return.png"

# Prove that Avatar Lab production modes select this same mixer instead of its legacy pose writer.
adb shell am start -W --activity-single-top -n "$LAB_ACTIVITY" \
  --es ci_pose production_home --es ci_camera full --es ci_orbit front --es ci_face neutral >/dev/null
wait_log 'stage=HOME layers=COMBINED owner=CelineProductionPresenceV80' \
  'Avatar Lab combined Production HOME owner'
sleep 1.65
capture_lab lab-production-home LAB_PRODUCTION_HOME

adb shell am start -W --activity-single-top -n "$LAB_ACTIVITY" \
  --es ci_pose production_call --es ci_camera call --es ci_orbit front --es ci_face neutral >/dev/null
wait_log 'stage=CALL layers=COMBINED owner=CelineProductionPresenceV80' \
  'Avatar Lab combined Production CALL owner'
wait_log 'scene=CALL seated=true lensMm=50 rootScaleChanged=false' \
  'Avatar Lab protected CALL camera/seat adapter'
sleep 1.0
capture_lab lab-production-call LAB_PRODUCTION_CALL

collect
trap - EXIT

for required in \
  'V80-400' \
  'order=base>posture>conversation>gaze>face' \
  'block5SixJointArms=true fingerBones=false' \
  'V80-410' \
  'V80-420' \
  'V80-450' \
  'V80-451' \
  'async=true' \
  'oneTransaction=true oneSkinUpdate=true' \
  'target=CALL eased=true snap=false' \
  'target=HOME eased=true snap=false' \
  'stage=HOME layers=COMBINED owner=CelineProductionPresenceV80' \
  'stage=CALL layers=COMBINED owner=CelineProductionPresenceV80' \
  'V76-210'; do
  grep -Fq "$required" "$OUT/logcat.txt" || fail "required runtime evidence missing: $required"
done

if grep -Eq 'V80-499|V79-598|V79-599|V76-298|V76-299|V61-102|V61-199|REN-399|FATAL EXCEPTION|SIGABRT' "$OUT/logcat.txt"; then
  fail "runtime/source failure detected during focused layered-owner proof"
fi

cat > "$OUT/summary.txt" <<EOF
PASS central v80 layered owner + Block 5 temporal arm/hand gate
HOME_CALL_HOME=visible_and_eased
BLOCK5_HOME=two_actual_product_frames_captured
BLOCK5_CALL=three_actual_product_frames_bilateral_motion_guard_passed
BLOCK5_ARM_HAND=asynchronous_six_joint_no_finger_bones
AVATAR_LAB_PRODUCTION_HOME_CALL=same_CelineProductionPresenceV80
TRANSFORM_FRAME=one_transaction_one_skin_update
FACE_LAYER=v76_guarded_runtime_active_v77_PCM_route_structurally_preserved
CAMERA_SEAT_ROOM=visible_targeted_regression_guard_passed
MANUAL_TEMPORAL_REVIEW=required
EOF

echo "PASS: exact APK uses central Block-5 asynchronous arm/hand motion in HOME/CALL; manual frame review remains required."
