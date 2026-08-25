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
  local value="$1"
  adb shell "run-as $PACKAGE sh -c 'printf %s $value > files/$MARKER'" || fail "could not write private zoom marker $value"
  for _ in $(seq 1 30); do
    if adb logcat -d | grep -F 'V70-141' | grep -Fq "zoom=$value"; then
      sleep 2
      return 0
    fi
    sleep 1
  done
  fail "runtime did not consume zoom marker $value"
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

# The preceding HOME/CALL/HOME proof leaves the app back on HOME. Exercise the exact public zoom
# bounds through a private run-as marker consumed by the debug APK; production UI still uses pinch.
capture_zoom "1.0" real-candidate-zoom-default.png
capture_zoom "0.55" real-candidate-zoom-far.png
capture_zoom "2.2" real-candidate-zoom-near.png
python3 ci/check-camera-zoom-range.py \
  real-candidate-zoom-far.png \
  real-candidate-zoom-default.png \
  real-candidate-zoom-near.png

# Restore the normal presentation before evidence collection finishes.
set_zoom "1.0"
adb logcat -d > real-candidate-zoom-logcat.txt

grep -q 'V70-150' real-candidate-zoom-logcat.txt || fail "Celine frustum-culling guard did not activate"
grep -q 'V70-140' real-candidate-zoom-logcat.txt || fail "HOME single camera-owner zoom handoff did not activate"
for value in 1.0 0.55 2.2; do
  grep -F 'V70-141' real-candidate-zoom-logcat.txt | grep -Fq "zoom=$value" || fail "zoom checkpoint missing: $value"
done
if grep -Eq 'V70-148|V70-149|REN-399|FATAL EXCEPTION|SIGABRT' real-candidate-zoom-logcat.txt; then
  fail "runtime error detected during zoom range proof"
fi

printf 'PASS v70 camera zoom range: 0.55 -> 1.00 -> 2.20 stays visible and changes real avatar scale (pid=%s)\n' "$PID"
