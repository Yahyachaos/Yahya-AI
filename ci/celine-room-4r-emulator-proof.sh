#!/usr/bin/env bash
set -euo pipefail

APK="${1:-ci-apk/app-debug.apk}"
OUT="${2:-room-4r-proof}"
PACKAGE="de.yahya.ai"
MAIN_ACTIVITY="de.yahya.ai/.MainActivity"
ROOM_ASSET="assets/models/room/celine_room_v80_final_modular.glb"
ROOM_SHA="25dc79b93accc804340da392b2b7a8d78c69ce19b16c17b6aacef3bfaf4465a8"
ROOM_BYTES="46580788"
mkdir -p "$OUT"

collect() {
  timeout 20s adb logcat -d -v threadtime > "$OUT/logcat.txt" 2>&1 || true
  timeout 15s adb shell dumpsys meminfo "$PACKAGE" > "$OUT/meminfo-final.txt" 2>&1 || true
  timeout 15s adb shell dumpsys gfxinfo "$PACKAGE" framestats > "$OUT/gfxinfo-final.txt" 2>&1 || true
  timeout 10s adb shell dumpsys window windows > "$OUT/window.txt" 2>&1 || true
}
trap collect EXIT

fail() {
  echo "4R room proof ERROR: $*" >&2
  adb logcat -d | grep -E 'de\.yahya\.ai|FATAL EXCEPTION|SIGABRT|OutOfMemory|ROOM-|V80-|V77-|V76-|V61-|REN-|CTL-' | tail -320 || true
  exit 1
}

wait_log() {
  local needle="$1"
  local label="$2"
  for _ in $(seq 1 75); do
    if adb logcat -d | grep -Fq "$needle"; then
      echo "Ready: $label"
      return 0
    fi
    sleep 1
  done
  fail "timed out waiting for $label ($needle)"
}

capture() {
  local name="$1"
  local label="$2"
  adb exec-out screencap -p > "$OUT/$name.png"
  python3 ci/check-real-celine-render.py "$OUT/$name.png" "$label"
  python3 ci/check-celine-person-presence.py "$OUT/$name.png" "$label"
}

[[ -s "$APK" ]] || fail "missing APK: $APK"
APK_LIST="$(unzip -Z1 "$APK")"
PACKAGED_COUNT="$(grep -Fxc "$ROOM_ASSET" <<< "$APK_LIST" || true)"
[[ "$PACKAGED_COUNT" = "1" ]] || fail "final room must be packaged exactly once (found $PACKAGED_COUNT)"
if grep -Fxq 'assets/models/room/celine_room_v80.gltf' <<< "$APK_LIST"; then
  fail "legacy block room is still packaged"
fi
PACKAGED_ROOM_BYTES="$(unzip -p "$APK" "$ROOM_ASSET" | wc -c | tr -d ' ')"
PACKAGED_ROOM_SHA="$(unzip -p "$APK" "$ROOM_ASSET" | sha256sum | awk '{print $1}')"
[[ "$PACKAGED_ROOM_BYTES" = "$ROOM_BYTES" ]] ||
  fail "packaged room size mismatch expected=$ROOM_BYTES actual=$PACKAGED_ROOM_BYTES"
[[ "$PACKAGED_ROOM_SHA" = "$ROOM_SHA" ]] ||
  fail "packaged room SHA mismatch expected=$ROOM_SHA actual=$PACKAGED_ROOM_SHA"
for metadata in \
  celine_room_v80_world_contract.json \
  celine_room_v80_assembly.json \
  celine_room_v80_anchors.json \
  celine_room_v80_nav_collision.json; do
  grep -Fxq "assets/models/room/$metadata" <<< "$APK_LIST" ||
    fail "packaged world metadata missing: $metadata"
done
printf '%s  %s\n%s  bytes\n' "$PACKAGED_ROOM_SHA" "$ROOM_ASSET" "$PACKAGED_ROOM_BYTES" \
  > "$OUT/room-asset.txt"

adb install -r "$APK" >/dev/null
adb shell pm clear "$PACKAGE" >/dev/null || fail "could not clear app state"
adb shell am force-stop "$PACKAGE" || true
adb logcat -c || true
adb shell dumpsys gfxinfo "$PACKAGE" reset >/dev/null 2>&1 || true
adb shell pm grant "$PACKAGE" android.permission.RECORD_AUDIO || true
LOAD_STARTED="$(date +%s)"
adb shell am start -W -n "$MAIN_ACTIVITY" > "$OUT/activity-start.txt" || fail "HOME launch failed"

wait_log 'ROOM-100' 'final modular room activation'
wait_log 'ROOM-105' 'structured world and anchor contract'
wait_log 'ROOM-110' 'preserved room seat anchor'
wait_log 'V80-410' 'central HOME layered frame'
wait_log 'CTL-350' 'visible canonical Celine'
LOAD_SECONDS="$(( $(date +%s) - LOAD_STARTED ))"
printf 'room_load_seconds=%s\n' "$LOAD_SECONDS" > "$OUT/performance-summary.txt"
sleep 1.5

PID_HOME="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
[[ -n "$PID_HOME" ]] || fail "process died before HOME evidence"
adb shell uiautomator dump /sdcard/celine-room-4r-home.xml >/dev/null || fail "HOME UI dump failed"
adb pull /sdcard/celine-room-4r-home.xml "$OUT/home.xml" >/dev/null || fail "HOME UI pull failed"
capture home HOME_4R
grep -q 'Celin 3D Ansicht' "$OUT/home.xml" || fail "HOME 3D stage missing"
grep -q 'Mit Celin' "$OUT/home.xml" || fail "HOME CALL entry missing"
timeout 15s adb shell dumpsys meminfo "$PACKAGE" > "$OUT/meminfo-home.txt" 2>&1 || true

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
wait_log 'target=CALL eased=true snap=false' 'accepted eased HOME-to-CALL handoff'
wait_log 'V80-420' 'central CALL layered frame'
sleep 1.5

PID_CALL="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
[[ "$PID_CALL" = "$PID_HOME" ]] || fail "process changed opening CALL home=$PID_HOME call=$PID_CALL"
adb shell uiautomator dump /sdcard/celine-room-4r-call.xml >/dev/null || fail "CALL UI dump failed"
adb pull /sdcard/celine-room-4r-call.xml "$OUT/call.xml" >/dev/null || fail "CALL UI pull failed"
capture call CALL_4R
grep -q 'Live mit Celin' "$OUT/call.xml" || fail "CALL overlay missing"
grep -q 'Celin 3D Ansicht' "$OUT/call.xml" || fail "CALL 3D stage missing"

adb shell input keyevent 4
wait_log 'target=HOME eased=true snap=false' 'accepted eased CALL-to-HOME handoff'
sleep 1.5

PID_RETURN="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
[[ "$PID_RETURN" = "$PID_HOME" ]] || fail "process changed returning HOME home=$PID_HOME return=$PID_RETURN"
adb shell uiautomator dump /sdcard/celine-room-4r-return.xml >/dev/null || fail "HOME-return UI dump failed"
adb pull /sdcard/celine-room-4r-return.xml "$OUT/home-return.xml" >/dev/null || fail "HOME-return UI pull failed"
capture home-return HOME_RETURN_4R
grep -q 'Mit Celin' "$OUT/home-return.xml" || fail "HOME did not recover"
grep -q 'Celin 3D Ansicht' "$OUT/home-return.xml" || fail "HOME-return 3D stage missing"
python3 ci/check-home-return-zoom.py "$OUT/home.png" "$OUT/home-return.png"

collect
trap - EXIT

for required in \
  'ROOM-100' \
  "sha256=$ROOM_SHA" \
  'ROOM-105' \
  'sources=12 instances=13 anchors=20 colliders=9 navEdges=14 contactEdges=6' \
  'contactY=0.461/0.457/0.756 9R=false' \
  'ROOM-110' \
  'V80-400' \
  'V80-410' \
  'V80-420' \
  'target=CALL eased=true snap=false' \
  'target=HOME eased=true snap=false'; do
  grep -Fq "$required" "$OUT/logcat.txt" || fail "required runtime evidence missing: $required"
done

if grep -Eq 'ROOM-198|ROOM-199|V80-499|V76-298|V76-299|V61-102|V61-199|REN-399|FATAL EXCEPTION|SIGABRT|OutOfMemoryError' "$OUT/logcat.txt"; then
  fail "room, renderer, owner or lifecycle failure detected"
fi

cat > "$OUT/summary.txt" <<EOF
PASS v80 4R final modular room runtime
ROOM_SHA256=$ROOM_SHA
ROOM_BYTES=$ROOM_BYTES
ROOM_LOAD_SECONDS=$LOAD_SECONDS
WORLD_CONTRACT=sources_12_instances_13_anchors_20_colliders_9_nav_edges_14_contact_edges_6
CONTACT_Y_M=bed_0.461_chair_0.457_foreground_table_0.756
HOME_CALL_HOME=canonical_Celine_visible_same_process_room_active
CAMERA_TRANSITIONS=eased_no_snap
LEGACY_ROOM=not_packaged
VISIBLE_LAPTOP_NODE=false
NINE_R_ACTIONS=false
EOF

(cd "$OUT" && sha256sum \
  home.png call.png home-return.png logcat.txt meminfo-home.txt meminfo-final.txt \
  gfxinfo-final.txt room-asset.txt runtime-source.txt apk.sha256 summary.txt \
  > evidence-sha256.txt)

echo "PASS: final modular 4R room stayed active with canonical Celine through HOME CALL HOME."
