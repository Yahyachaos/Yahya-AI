#!/usr/bin/env bash
set -euo pipefail

APK="${1:-ci-apk/app-debug.apk}"
OUT="${2:-avatar-lab-proof}"
PACKAGE="de.yahya.ai"
ACTIVITY="de.yahya.ai/.MainActivity"
CAPTURE_ACTIVITY="de.yahya.ai/.CelineAvatarLabCaptureActivity"
mkdir -p "$OUT"

fail() {
  echo "Scene proof ERROR: $*" >&2
  adb logcat -d | grep -E 'de\.yahya\.ai|FATAL EXCEPTION|REN-|V44-|V70-|V76-|V79-|V80-' | tail -340 || true
  exit 1
}

wait_log() {
  local needle="$1" label="$2"
  for _ in $(seq 1 30); do
    adb logcat -d | grep -q "$needle" && { echo "Scene ready: $label"; return 0; }
    sleep 1
  done
  fail "timed out waiting for $label ($needle)"
}

[[ -s "$APK" ]] || fail "missing APK: $APK"
adb install -r "$APK" >/dev/null
adb shell am force-stop "$PACKAGE" || true
adb logcat -c || true
adb shell pm grant "$PACKAGE" android.permission.RECORD_AUDIO || true
adb shell am start -W -n "$ACTIVITY" >/dev/null
wait_log 'CTL-350' 'visible HOME Celine'
wait_log 'V44-100' 'production room backdrop'
sleep 1.2

adb shell uiautomator dump /sdcard/celine-scene-home.xml >/dev/null || fail "HOME UI dump failed"
adb pull /sdcard/celine-scene-home.xml "$OUT/15-home-scene.xml" >/dev/null || fail "HOME UI pull failed"
adb exec-out screencap -p > "$OUT/15-home-scene.png"
python3 ci/check-real-celine-render.py "$OUT/15-home-scene.png" HOME
python3 ci/check-celine-person-presence.py "$OUT/15-home-scene.png" HOME

grep -q 'Celin 3D Ansicht' "$OUT/15-home-scene.xml" || fail "HOME 3D stage missing"
grep -q 'Mit Celin' "$OUT/15-home-scene.xml" || fail "HOME call entry missing"

read -r TAP_X TAP_Y <<< "$(python3 - "$OUT/15-home-scene.xml" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
for n in root.iter('node'):
    if 'Mit Celin' not in n.attrib.get('text',''): continue
    m=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.attrib.get('bounds',''))
    if not m: continue
    x1,y1,x2,y2=map(int,m.groups()); print((x1+x2)//2,(y1+y2)//2); raise SystemExit
raise SystemExit('call button bounds missing')
PY
)"
[[ -n "${TAP_X:-}" && -n "${TAP_Y:-}" ]] || fail "CALL tap coordinates missing"
adb shell input tap "$TAP_X" "$TAP_Y"
wait_log 'V80-420' 'central layered CALL seat entry'
sleep 1.5

adb shell uiautomator dump /sdcard/celine-scene-call.xml >/dev/null || fail "CALL UI dump failed"
adb pull /sdcard/celine-scene-call.xml "$OUT/16-call-scene.xml" >/dev/null || fail "CALL UI pull failed"
adb exec-out screencap -p > "$OUT/16-call-scene.png"
python3 ci/check-real-celine-render.py "$OUT/16-call-scene.png" CALL
python3 ci/check-celine-person-presence.py "$OUT/16-call-scene.png" CALL

grep -q 'Live mit Celin' "$OUT/16-call-scene.xml" || fail "CALL overlay missing"
grep -q 'Celin 3D Ansicht' "$OUT/16-call-scene.xml" || fail "CALL 3D stage missing"

# Block 7 is already technically and manually accepted on this exact runtime lineage. Block 8
# protects that result by recording a real natural planner blink while PCM-driven speech remains
# active; no held diagnostic blink override is used here.

# Block 8: the debug-only capture bridge feeds deterministic 20 ms PCM fixtures through the exact
# SpeechLipSyncV77 stabilizer and SpeechAudioBus, while CelineProductionPresenceV80 remains the
# production body/head owner and CelineMorphRuntimeV62 remains the sole face writer.
block8_launch() {
  local face="$1" restart="${2:-keep}"
  if [[ "$restart" == "restart" ]]; then adb shell am force-stop "$PACKAGE" || true; fi
  adb shell am start -W --activity-single-top -n "$CAPTURE_ACTIVITY" \
    --es ci_pose production_call --es ci_camera face --es ci_orbit front --es ci_face "$face" >/dev/null
  if [[ "$restart" == "restart" ]]; then sleep 1.8; else sleep 0.72; fi
}

block8_capture() {
  local face="$1" name="$2" restart="${3:-keep}" attempt
  for attempt in 1 2; do
    block8_launch "$face" "$restart"
    adb exec-out screencap -p >/dev/null || true
    sleep 0.42
    adb exec-out screencap -p > "$OUT/$name.png"
    if [[ -s "$OUT/$name.png" ]]; then
      echo "Block-8 evidence captured: $name face=$face attempt=$attempt"
      return 0
    fi
    restart=keep
  done
  fail "Block-8 evidence frame remained empty: $name"
}

block8_capture block8_silent 21-block8-neutral-before restart
block8_capture block8_pcm_start 22-block8-speech-start
block8_capture block8_pcm_round 23-block8-speech-round
block8_capture block8_pcm_wide 24-block8-speech-wide
block8_capture block8_pcm_labial 25-block8-speech-labial

# Start sustained ROUND speech without doing the software-emulator's slow double-screencap first.
# The planner was just reset by this state and schedules its first natural blink at ~3313 ms. A
# four-second real screen recording begins ~0.7 s after that reset, so the complete close/hold/reopen
# necessarily falls inside the temporal evidence instead of relying on a lucky still-frame sample.
block8_launch block8_pcm_round
adb shell rm -f /sdcard/block8-speech-sustain-natural-blink.mp4 || true
adb shell screenrecord --time-limit 4 --bit-rate 4000000 \
  /sdcard/block8-speech-sustain-natural-blink.mp4 >/dev/null 2>&1 \
  || fail "Block-8 natural speech-blink screenrecord failed"
adb pull /sdcard/block8-speech-sustain-natural-blink.mp4 \
  "$OUT/26-block8-speech-sustain-natural-blink.mp4" >/dev/null \
  || fail "Block-8 natural speech-blink video pull failed"
[[ -s "$OUT/26-block8-speech-sustain-natural-blink.mp4" ]] \
  || fail "Block-8 natural speech-blink video is empty"
echo "Block-8 temporal video captured: sustained PCM ROUND speech with natural planner blink"

block8_capture block8_silent 28-block8-neutral-after

timeout 15s adb logcat -d -v threadtime > "$OUT/scene-logcat.txt" 2>&1 || true
if grep -Eq 'REN-399|V76-299|V80-499|FATAL EXCEPTION|SIGABRT' "$OUT/scene-logcat.txt"; then
  fail "runtime error detected during targeted scene/speech-face proof"
fi
if ! grep -Fq 'V80-440' "$OUT/scene-logcat.txt" || ! grep -Fq 'stage=CALL' "$OUT/scene-logcat.txt"; then
  fail "targeted capture did not bind the central production CALL owner"
fi
if ! grep -Fq 'V76-210' "$OUT/scene-logcat.txt"; then
  fail "targeted capture did not bind the guarded final-geometry face morph runtime"
fi
if ! grep -Fq 'V80-820' "$OUT/scene-logcat.txt" \
    || ! grep -Fq 'fixture=start shape=OPEN' "$OUT/scene-logcat.txt" \
    || ! grep -Fq 'fixture=round shape=ROUND' "$OUT/scene-logcat.txt" \
    || ! grep -Fq 'fixture=wide shape=WIDE' "$OUT/scene-logcat.txt" \
    || ! grep -Fq 'fixture=labial shape=LABIAL' "$OUT/scene-logcat.txt" \
    || ! grep -Fq 'source=SpeechLipSyncV77->SpeechAudioBus owner=CelineProductionPresenceV80' "$OUT/scene-logcat.txt"; then
  fail "Block-8 playback-PCM speech-face route or expected viseme fixtures missing from logs"
fi
if ! grep -Fq 'V80-821' "$OUT/scene-logcat.txt" || ! grep -Fq 'state=IDLE level=0 cue=CLOSED' "$OUT/scene-logcat.txt"; then
  fail "Block-8 speech-end neutral reset missing from logs"
fi

for frame in \
  21-block8-neutral-before 22-block8-speech-start 23-block8-speech-round \
  24-block8-speech-wide 25-block8-speech-labial 28-block8-neutral-after; do
  [[ -s "$OUT/$frame.png" ]] || fail "missing targeted evidence $frame"
done
[[ -s "$OUT/26-block8-speech-sustain-natural-blink.mp4" ]] \
  || fail "missing Block-8 sustained speech natural-blink video"

echo "Targeted HOME/CALL plus Block-8 PCM speech/face evidence captured; sustained speech includes a real temporal natural-blink recording for mandatory manual review."