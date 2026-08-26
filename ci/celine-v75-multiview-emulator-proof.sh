#!/usr/bin/env bash
set -euo pipefail

APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
CANDIDATE="${2:-morph-proof/celine_v76_candidate.glb}"
PACKAGE="de.yahya.ai"
ACTIVITY="de.yahya.ai/.MainActivity"
ZOOM_MARKER="celine-ci-camera-zoom-v70"
OUTDIR="morph-proof/v76-multiview"
mkdir -p "$OUTDIR"

fail() {
  echo "V76 MULTIVIEW ERROR: $*"
  adb logcat -d | grep -E 'de\.yahya\.ai|CTL-|REN-|VIS-|V39-|V60-|V61-|V70-|V74-|V76-|FATAL EXCEPTION|SIGABRT' | tail -260 || true
  exit 1
}

wait_for_log() {
  local needle="$1" label="$2"
  for _ in $(seq 1 40); do
    if adb logcat -d | grep -Fq "$needle"; then
      echo "Ready: $label"
      return 0
    fi
    sleep 1
  done
  fail "timed out waiting for $label ($needle)"
}

install_private_variant() {
  local file="$1"
  adb push "$file" /data/local/tmp/celine-v76-evidence.glb >/dev/null || fail "could not push evidence GLB"
  adb shell "run-as $PACKAGE mkdir -p files/models" || fail "could not create private model dir"
  adb shell "run-as $PACKAGE cp /data/local/tmp/celine-v76-evidence.glb files/models/celine.glb" || fail "could not install private evidence GLB"
  adb shell rm -f /data/local/tmp/celine-v76-evidence.glb || true
}

start_private_view() {
  adb shell am force-stop "$PACKAGE" || true
  adb logcat -c || true
  adb shell am start -W -n "$ACTIVITY" >/dev/null || fail "activity start failed"
  wait_for_log 'REN-305' 'private exact-candidate evidence source'
  wait_for_log 'CTL-350' 'visible Filament candidate'
  sleep 2
  if adb logcat -d | grep -F 'CTL-110' | grep -Fq 'changed=true'; then
    fail "runtime material repair mutated the evidence candidate"
  fi
  if adb logcat -d | grep -Eq 'REN-399|FATAL EXCEPTION|SIGABRT'; then
    fail "runtime error while preparing evidence view"
  fi
}

capture_yaw() {
  local yaw="$1" label="$2" png="$3"
  local glb="$OUTDIR/celine-yaw-${label}.glb"
  local report="$OUTDIR/celine-yaw-${label}.json"
  python3 ci/celine_multiview_variant_v75.py "$CANDIDATE" "$glb" --yaw "$yaw" --report "$report"
  install_private_variant "$glb"
  start_private_view
  adb exec-out screencap -p > "$png"
  python3 ci/check-real-celine-render.py "$png" "V76_${label}"
  echo "Captured yaw=$yaw -> $png"
}

[[ -s "$APK" ]] || fail "missing APK: $APK"
[[ -s "$CANDIDATE" ]] || fail "missing candidate: $CANDIDATE"
BASE_SHA="$(sha256sum "$CANDIDATE" | awk '{print $1}')"
APK_SHA="$(unzip -p "$APK" assets/models/celine.glb | sha256sum | awk '{print $1}')"
[[ "$BASE_SHA" = "$APK_SHA" ]] || fail "candidate/APK mismatch before multiview evidence"

# Render the exact packaged candidate through the real Android Filament renderer. The only
# difference in these temporary evidence copies is a parent-node Y rotation; binary mesh/skin/
# material payload remains byte-identical and is recorded by each JSON report.
capture_yaw "0" "front" "real-candidate-front.png"
capture_yaw "90" "profile-right" "real-candidate-profile-right.png"
capture_yaw "-90" "profile-left" "real-candidate-profile-left.png"
capture_yaw "180" "back" "real-candidate-back.png"

# Dedicated face evidence: use the unrotated exact candidate, request the proven safe near zoom,
# then crop only actual runtime pixels. No synthesis/recoloring is allowed.
install_private_variant "$OUTDIR/celine-yaw-front.glb"
start_private_view
adb shell "run-as $PACKAGE sh -c 'printf %s 1.25 > files/$ZOOM_MARKER.tmp && mv files/$ZOOM_MARKER.tmp files/$ZOOM_MARKER'" || fail "could not request face-evidence zoom"
for _ in $(seq 1 30); do
  if adb logcat -d | grep -F 'V70-141' | grep -F 'requested=1.25' | grep -Fq 'zoom=1.25'; then
    break
  fi
  sleep 1
done
adb logcat -d | grep -F 'V70-141' | grep -F 'requested=1.25' | grep -Fq 'zoom=1.25' || fail "face-evidence zoom was not applied"
sleep 2
adb exec-out screencap -p > real-candidate-face-source.png
python3 ci/check-real-celine-render.py real-candidate-face-source.png V76_FACE_SOURCE
python3 ci/celine_png_crop_v75.py real-candidate-face-source.png real-candidate-face-close.png --left 0.23 --top 0.05 --right 0.77 --bottom 0.50

# Fail closed: remove the temporary private variant and prove the app returns to the packaged exact
# candidate before the workflow exits.
adb shell "run-as $PACKAGE rm -f files/models/celine.glb files/$ZOOM_MARKER files/$ZOOM_MARKER.tmp" || true
adb shell am force-stop "$PACKAGE" || true
adb logcat -c || true
adb shell am start -W -n "$ACTIVITY" >/dev/null || fail "packaged-source restore start failed"
wait_for_log 'REN-306' 'packaged v76 source restored'
wait_for_log 'CTL-350' 'packaged v75 candidate visible after restore'
adb logcat -d > real-candidate-multiview-logcat.txt
if grep -Eq 'REN-305|REN-399|FATAL EXCEPTION|SIGABRT' real-candidate-multiview-logcat.txt; then
  fail "private source or runtime error remained after evidence restore"
fi

printf 'PASS v76 mandatory multiview evidence: exact_candidate_sha=%s front/right/left/back + runtime-pixel face close-up; packaged source restored\n' "$BASE_SHA"
