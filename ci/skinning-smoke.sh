#!/usr/bin/env bash
set -euo pipefail

PACKAGE="de.yahya.ai"
ACTIVITY="de.yahya.ai/.MainActivity"
FIXTURE="ci/celine-skinned-probe.glb"

fail_skin() {
  echo "SKINNING ERROR: $*"
  adb shell "run-as $PACKAGE cat shared_prefs/celine_3d_diagnostics.xml" 2>/dev/null || true
  adb logcat -d | grep -E 'de\.yahya\.ai|Filament|gltfio|FATAL EXCEPTION|SIGABRT|UiAutomation|V54-|V55-|V56-|V57-|V58-|V59-|V60-|V61-|REN-' | tail -320 || true
  exit 1
}

# API 35 uiautomator occasionally terminates its own UiAutomation process while the tested app
# remains healthy. Retry only the dump operation; never restart Yahya AI or mask an app/process
# failure. This keeps the v61/v59 lifecycle proof strict while removing the proven infrastructure
# race seen on main Android Build #370.
dump_ui() {
  local remote="$1"
  local label="$2"
  local attempt
  for attempt in 1 2 3; do
    adb shell rm -f "$remote" >/dev/null 2>&1 || true
    if adb shell uiautomator dump "$remote" >/dev/null 2>&1; then
      echo "UI dump $label succeeded on attempt $attempt"
      return 0
    fi
    local pid
    pid="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
    [[ -n "$pid" ]] || return 1
    echo "UI dump $label transient failure on attempt $attempt; Yahya AI PID=$pid, retrying..."
    sleep 2
  done
  return 1
}

# Keep the current multi-panel synthetic fixture: v61 preserves v59's proven production ownership,
# so only the safe neck+Head owner may be active in CALL. Unused shoulder/Hips panels are harmless
# and make accidental reactivation easier to detect through diagnostics.
python3 ci/generate-celine-skinned-probe-glb.py "$FIXTURE"
[[ -s "$FIXTURE" ]] || fail_skin "Skinned CI fixture was not generated"
adb shell am force-stop "$PACKAGE" || true
cat "$FIXTURE" | adb shell "run-as $PACKAGE sh -c 'mkdir -p files/models; cat > files/models/celine.glb'"
REMOTE_BYTES="$(adb shell "run-as $PACKAGE sh -c 'wc -c < files/models/celine.glb'" | tr -d '\r ' || true)"
[[ -n "$REMOTE_BYTES" && "$REMOTE_BYTES" -ge 100000 ]] || fail_skin "Skinned CI model install failed (bytes=$REMOTE_BYTES)"
echo "Injected current skinned Celine probe for v61 safe-owner proof: $REMOTE_BYTES bytes"
adb shell pm grant "$PACKAGE" android.permission.RECORD_AUDIO || true
adb shell am start -W -n "$ACTIVITY"
sleep 8
PID="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
[[ -n "$PID" ]] || fail_skin "Yahya AI process died before v61 CALL proof"

dump_ui /sdcard/v59-home.xml HOME || fail_skin "Could not dump HOME UI after bounded retries"
adb pull /sdcard/v59-home.xml v59-home.xml >/dev/null || fail_skin "Could not pull HOME UI"
read -r TAP_X TAP_Y <<< "$(python3 - <<'PY'
import re, xml.etree.ElementTree as ET
root=ET.parse('v59-home.xml').getroot()
for n in root.iter('node'):
    if 'Mit Celin' not in n.attrib.get('text',''): continue
    m=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.attrib.get('bounds',''))
    if m:
        x1,y1,x2,y2=map(int,m.groups());print((x1+x2)//2,(y1+y2)//2);raise SystemExit
raise SystemExit('videochat button not found')
PY
)"
[[ -n "${TAP_X:-}" && -n "${TAP_Y:-}" ]] || fail_skin "Could not resolve CALL entry coordinates"
adb shell input tap "$TAP_X" "$TAP_Y"
sleep 6
PID_CALL="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
[[ -n "$PID_CALL" ]] || fail_skin "Yahya AI process died while opening CALL"
dump_ui /sdcard/v59-call.xml CALL || fail_skin "Could not dump CALL UI after bounded retries"
adb pull /sdcard/v59-call.xml v59-call.xml >/dev/null || fail_skin "Could not pull CALL UI"
grep -q 'Live mit Celin' v59-call.xml || fail_skin "CALL did not open for v61 proof"

adb exec-out screencap -p > emulator-skin-a.png
sleep 0.65
adb exec-out screencap -p > emulator-skin-b.png
sleep 0.65
adb exec-out screencap -p > emulator-skin-c.png
python3 ci/check-magenta-avatar.py emulator-skin-a.png SKIN_A || fail_skin "Skinned probe missing in frame A"
python3 ci/check-magenta-avatar.py emulator-skin-b.png SKIN_B || fail_skin "Skinned probe missing in frame B"
python3 ci/check-magenta-avatar.py emulator-skin-c.png SKIN_C || fail_skin "Skinned probe missing in frame C"
python3 ci/check-skinned-motion.py emulator-skin-a.png emulator-skin-b.png emulator-skin-c.png || fail_skin "Safe neck+Head skinning did not visibly deform the probe"

adb shell "run-as $PACKAGE cat shared_prefs/celine_3d_diagnostics.xml" > emulator-celine-diagnostics.xml 2>/dev/null || fail_skin "Could not read diagnostics"
# v61 deliberately changed only the versioned safety marker while preserving v59 ownership semantics.
grep -q 'V61-003' emulator-celine-diagnostics.xml || fail_skin "v61 safety-mode marker missing"
grep -q 'HOME Head-only' emulator-celine-diagnostics.xml || fail_skin "v61 diagnostics do not confirm HOME Head-only ownership"
grep -q 'V55-110' emulator-celine-diagnostics.xml || fail_skin "V55-110 safe CALL marker missing"
grep -q 'CALL neck+Head\|neck+Head' emulator-celine-diagnostics.xml || fail_skin "v61 diagnostics do not confirm CALL neck+Head ownership"
grep -q 'probe=true' emulator-celine-diagnostics.xml || fail_skin "CelineSkinningProbe not detected"

# The quarantined v56-v58 runtime owner must still never become active in v61 CALL.
if grep -q 'V56-110\|V57-120\|V58-120' emulator-celine-diagnostics.xml; then
  cat emulator-celine-diagnostics.xml
  fail_skin "Quarantined v56-v58 CALL skinning owner became active in v61"
fi
if grep -q 'V61-198\|V61-199\|V59-198\|V59-199\|V55-198\|V55-199\|V54-198\|V54-199' emulator-celine-diagnostics.xml; then
  cat emulator-celine-diagnostics.xml
  fail_skin "Guarded v61/v59/v55/v54 skinning path recorded a runtime error"
fi

adb shell input keyevent 4
sleep 2
PID_AFTER="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
[[ -n "$PID_AFTER" ]] || fail_skin "Process died restoring HOME after v61 proof"
echo "v61 CALL framing safety proof passed with PID=$PID_AFTER"
echo "Verified: v61 marker + preserved v59 HOME Head-only/CALL neck+Head ownership + moving pixels + HOME recovery; v56-v58 owner quarantined"
