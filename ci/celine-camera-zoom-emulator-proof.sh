#!/usr/bin/env bash
set -euo pipefail

PACKAGE="de.yahya.ai"
MARKER="celine-ci-camera-zoom-v70"

fail() {
  echo "ZOOM ERROR: $*"
  adb logcat -d | grep -E 'de\.yahya\.ai|V70-14|V70-15|V60-12|REN-|FATAL EXCEPTION|SIGABRT' | tail -220 || true
  exit 1
}

set_zoom() {
  local requested="$1"
  local expected="${2:-$1}"
  adb shell "run-as $PACKAGE sh -c 'printf %s $requested > files/$MARKER'" || fail "could not write private zoom marker $requested"
  for _ in $(seq 1 30); do
    if adb logcat -d | grep -F 'V70-141' | grep -F "requested=$requested" | grep -Fq "zoom=$expected"; then
      sleep 2
      return 0
    fi
    sleep 1
  done
  fail "runtime did not consume zoom marker requested=$requested expected=$expected"
}

capture_zoom() {
  local value="$1"
  local name="$2"
  set_zoom "$value"
  adb exec-out screencap -p > "$name"
  python3 ci/check-real-celine-render.py "$name" "ZOOM_$value"
  python3 ci/check-celine-person-presence.py "$name" "ZOOM_$value"
}

PID="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
[[ -n "$PID" ]] || fail "Yahya AI is not alive before zoom proof"

# The preceding HOME/CALL/HOME proof leaves the app back on HOME. Exercise the exact effective safe
# zoom bounds through a private run-as marker consumed by the debug APK; production UI still uses pinch.
capture_zoom "1.0" real-candidate-zoom-default.png
capture_zoom "0.55" real-candidate-zoom-far.png
capture_zoom "1.25" real-candidate-zoom-near.png
python3 ci/check-camera-zoom-range.py \
  real-candidate-zoom-far.png \
  real-candidate-zoom-default.png \
  real-candidate-zoom-near.png

# Move away from the safe near bound first, then prove that the old unsafe request is actively
# clamped back to 1.25. This makes the clamp an observable camera state transition in the emulator.
set_zoom "1.0"
set_zoom "2.2" "1.25"

# Restore the normal presentation before evidence collection finishes.
set_zoom "1.0"
adb logcat -d > real-candidate-zoom-logcat.txt

grep -q 'V70-150' real-candidate-zoom-logcat.txt || fail "Celine frustum-culling guard did not activate"
grep -q 'V70-140' real-candidate-zoom-logcat.txt || fail "HOME single camera-owner zoom handoff did not activate"
for checkpoint in 'requested=1.0 zoom=1.0' 'requested=0.55 zoom=0.55' 'requested=1.25 zoom=1.25' 'requested=2.2 zoom=1.25'; do
  requested="${checkpoint%% *}"
  zoom="${checkpoint##* }"
  grep -F 'V70-141' real-candidate-zoom-logcat.txt | grep -F "${requested}" | grep -Fq "${zoom}" || fail "zoom checkpoint missing: $checkpoint"
done
if grep -Eq 'V70-148|V70-149|REN-399|FATAL EXCEPTION|SIGABRT' real-candidate-zoom-logcat.txt; then
  fail "runtime error detected during zoom range proof"
fi

printf 'PASS v70 camera zoom range: 0.55 -> 1.00 -> 1.25 stays fully framed, changes real avatar scale, and clamps legacy 2.20 (pid=%s)\n' "$PID"
