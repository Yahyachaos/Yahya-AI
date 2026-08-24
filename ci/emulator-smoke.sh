#!/usr/bin/env bash
set -euo pipefail

APK="ci-apk/app-debug.apk"
PACKAGE="de.yahya.ai"
ACTIVITY="de.yahya.ai/.MainActivity"

if [[ ! -f "$APK" ]]; then
  echo "Missing APK: $APK"
  exit 1
fi

adb install -r "$APK"
adb shell am start -W -n "$ACTIVITY"
sleep 10

PID="$(adb shell pidof "$PACKAGE" | tr -d '\r')"
if [[ -z "$PID" ]]; then
  echo "Yahya AI process is not running"
  adb logcat -d | tail -300
  exit 1
fi

if ! adb shell dumpsys activity activities | grep -q "$ACTIVITY"; then
  echo "MainActivity is not the active Yahya AI activity"
  adb shell dumpsys activity activities | grep -A4 -B4 "$PACKAGE" || true
  exit 1
fi

adb exec-out screencap -p > emulator-home.png
adb shell uiautomator dump /sdcard/yahya-window.xml
adb pull /sdcard/yahya-window.xml emulator-window.xml

if ! grep -q 'Update prüfen' emulator-window.xml; then
  echo "V47 update button was not found in the rendered UI"
  cat emulator-window.xml
  exit 1
fi

if ! grep -q 'Mit Celin' emulator-window.xml; then
  echo "Videochat entry button was not found in the rendered UI"
  cat emulator-window.xml
  exit 1
fi

echo "Emulator smoke test passed with PID=$PID"
echo "Verified: MainActivity + Update prüfen + Mit Celin videochatten"
