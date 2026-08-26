#!/usr/bin/env bash
set -euo pipefail

APK="${1:-ci-apk/app-debug.apk}"
OUT="${2:-avatar-lab-proof}"
CAPTURE_ACTIVITY="de.yahya.ai/.CelineAvatarLabCaptureActivity"
mkdir -p "$OUT"

if [ ! -f "$APK" ]; then
  echo "APK not found: $APK" >&2
  exit 1
fi

adb install -r "$APK" >/dev/null
adb logcat -c || true

launch_state() {
  local pose="$1"
  local camera="$2"
  local orbit="$3"
  local face="$4"
  adb shell am force-stop de.yahya.ai
  adb shell am start -W -n "$CAPTURE_ACTIVITY" \
    --es ci_pose "$pose" \
    --es ci_camera "$camera" \
    --es ci_orbit "$orbit" \
    --es ci_face "$face" >/dev/null
  # Asset/Filament startup plus the held diagnostic face state settle well inside this window.
  sleep 1.65
}

capture() {
  local name="$1"
  adb exec-out screencap -p > "$OUT/${name}.png"
  if [ ! -s "$OUT/${name}.png" ]; then
    echo "Empty screenshot: $name" >&2
    return 1
  fi
}

# Close-up uses a held morph instead of a timed animation, so cheek/eyelid comparison is exact.
launch_state stand face front neutral
capture "01-face-neutral-close"
launch_state stand face front blink85
capture "02-face-blink-85-held"
launch_state stand face front neutral
capture "03-face-open-after"

# Full-body grounding.
launch_state stand full front neutral
capture "04-standing-front"
launch_state seated full front neutral
capture "05-seated-front"

# Two frames from one continuous arm/hand mode prove visible motion instead of a frozen pose.
launch_state arms full front neutral
sleep 0.20
capture "06-arms-hands-a"
sleep 1.45
capture "07-arms-hands-b"

# Two frames from one continuous walk mode.
launch_state walk full front neutral
sleep 0.15
capture "08-walk-a"
sleep 0.72
capture "09-walk-b"

# Deterministic branch-avatar orientation checks.
launch_state stand full profile_left neutral
capture "10-profile-left"
launch_state stand full three_right neutral
capture "11-three-quarter-right"
launch_state stand full front neutral
capture "12-front-return"

adb logcat -d -v threadtime > "$OUT/logcat.txt" || true
printf '%s\n' \
  "Avatar Lab lightweight proof complete." \
  "APK=$APK" \
  "CaptureActivity=$CAPTURE_ACTIVITY" \
  "Screenshots=$(find "$OUT" -maxdepth 1 -name '*.png' | wc -l)" \
  > "$OUT/summary.txt"
cat "$OUT/summary.txt"
