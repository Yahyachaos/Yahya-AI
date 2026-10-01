#!/usr/bin/env bash
set -euo pipefail

mkdir -p block10-launcher-proof
APK="$(find ci-apk -name '*.apk' -print -quit)"
test -n "$APK"
adb install -r "$APK"
adb shell dumpsys package de.yahya.ai > block10-launcher-proof/package.txt
adb shell cmd overlay list > block10-launcher-proof/overlay-list.txt || true
adb shell wm size > block10-launcher-proof/wm-size.txt

launcher_pkg="$(adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r' | cut -d/ -f1)"
printf 'launcher=%s\n' "$launcher_pkg" > block10-launcher-proof/launcher.txt

open_drawer_and_find() {
  local shape="$1"
  adb shell input keyevent KEYCODE_HOME
  sleep 2
  adb shell input swipe 540 1650 540 450 350 || true
  sleep 2
  local found=0
  for i in $(seq 1 12); do
    adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
    adb pull /sdcard/window.xml "block10-launcher-proof/${shape}-ui-${i}.xml" >/dev/null 2>&1 || true
    if grep -q 'text="Yahya AI"' "block10-launcher-proof/${shape}-ui-${i}.xml" 2>/dev/null; then
      found=1
      break
    fi
    adb shell input swipe 540 1550 540 650 250 || true
    sleep 1
  done
  adb shell screencap -p "/sdcard/${shape}.png"
  adb pull "/sdcard/${shape}.png" "block10-launcher-proof/${shape}.png" >/dev/null
  printf '%s Yahya_AI_found=%s\n' "$shape" "$found" | tee -a block10-launcher-proof/result.txt
  test "$found" = 1
}

open_drawer_and_find default

apply_shape() {
  local label="$1"
  local regex="$2"
  local pkg
  pkg="$(sed -n 's/^[[:space:]]*\[[^]]*\][[:space:]]*//p' block10-launcher-proof/overlay-list.txt | grep -E "$regex" | head -1 || true)"
  if [ -z "$pkg" ]; then
    printf '%s overlay=UNAVAILABLE\n' "$label" | tee -a block10-launcher-proof/result.txt
    return 0
  fi
  printf '%s overlay=%s\n' "$label" "$pkg" | tee -a block10-launcher-proof/result.txt
  adb shell cmd overlay enable-exclusive --user 0 --category "$pkg"
  adb shell am force-stop "$launcher_pkg" || true
  sleep 2
  open_drawer_and_find "$label"
}

apply_shape circle 'com\.android\.theme\.icon\.circle$'
apply_shape squircle 'com\.android\.theme\.icon\.squircle$'
apply_shape rounded-square 'com\.android\.theme\.icon\.(roundedrect|roundedrectangle)$'

grep -q 'Yahya_AI_found=1' block10-launcher-proof/result.txt
