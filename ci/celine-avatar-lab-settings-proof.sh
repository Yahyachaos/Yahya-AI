#!/usr/bin/env bash
set -euo pipefail

APK="${1:-ci-apk/app-debug.apk}"
OUT="${2:-avatar-lab-proof}"
PACKAGE="de.yahya.ai"
MAIN="de.yahya.ai/.MainActivity"
mkdir -p "$OUT"

fail() {
  echo "Settings proof ERROR: $*" >&2
  adb logcat -d | grep -E 'de\.yahya\.ai|FATAL EXCEPTION|REN-|V79-|CTL-' | tail -220 || true
  exit 1
}

wait_text() {
  local text="$1" file="$2"
  for _ in $(seq 1 20); do
    adb shell uiautomator dump /sdcard/celine-settings.xml >/dev/null 2>&1 || true
    adb pull /sdcard/celine-settings.xml "$file" >/dev/null 2>&1 || true
    grep -Fq "$text" "$file" 2>/dev/null && return 0
    sleep 0.5
  done
  fail "timed out waiting for UI text: $text"
}

center_for() {
  local file="$1" needle="$2"
  python3 - "$file" "$needle" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot(); needle=sys.argv[2]
for n in root.iter('node'):
    values=(n.attrib.get('text',''), n.attrib.get('content-desc',''))
    if not any(needle in v for v in values): continue
    m=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.attrib.get('bounds',''))
    if not m: continue
    x1,y1,x2,y2=map(int,m.groups()); print((x1+x2)//2,(y1+y2)//2); raise SystemExit
raise SystemExit(2)
PY
}

[[ -s "$APK" ]] || fail "missing APK: $APK"
adb install -r "$APK" >/dev/null
adb shell am force-stop "$PACKAGE" || true
adb logcat -c || true
adb shell pm grant "$PACKAGE" android.permission.RECORD_AUDIO || true
adb shell am start -W -n "$MAIN" >/dev/null
sleep 2

PID_HOME="$(adb shell pidof "$PACKAGE" | tr -d '\r ' || true)"
[[ -n "$PID_HOME" ]] || fail "HOME process missing"
wait_text "Mit Celin" "$OUT/17-settings-home-before.xml"

read -r GEAR_X GEAR_Y <<< "$(center_for "$OUT/17-settings-home-before.xml" "Einstellungen")"
[[ -n "${GEAR_X:-}" && -n "${GEAR_Y:-}" ]] || fail "Settings gear coordinates missing"
adb shell input tap "$GEAR_X" "$GEAR_Y"
wait_text "Celine Avatar Lab" "$OUT/18-settings-hub.xml"
adb exec-out screencap -p > "$OUT/18-settings-hub.png"
grep -Fq "Update prüfen" "$OUT/18-settings-hub.xml" || fail "update action disappeared from Settings hub"
grep -Fq "Weitere Einstellungen" "$OUT/18-settings-hub.xml" || fail "original Settings action disappeared"

read -r LAB_X LAB_Y <<< "$(center_for "$OUT/18-settings-hub.xml" "Celine Avatar Lab")"
[[ -n "${LAB_X:-}" && -n "${LAB_Y:-}" ]] || fail "Avatar Lab Settings entry coordinates missing"
adb shell input tap "$LAB_X" "$LAB_Y"
wait_text "Celine Avatar Lab · v79 branch-live" "$OUT/19-avatar-lab-open.xml"
sleep 1.2
adb exec-out screencap -p > "$OUT/19-avatar-lab-open.png"
grep -Fq "Ganzkörper" "$OUT/19-avatar-lab-open.xml" || fail "Avatar Lab controls missing"
grep -Fq "HOME" "$OUT/19-avatar-lab-open.xml" || fail "Avatar Lab HOME return control missing"

PID_LAB="$(adb shell pidof "$PACKAGE" | tr -d '\r ' || true)"
[[ "$PID_LAB" = "$PID_HOME" ]] || fail "app process restarted entering Avatar Lab: home=$PID_HOME lab=$PID_LAB"

# Android back exercises the same Activity finish/back-stack path as the visible HOME control,
# without depending on the horizontally scrollable diagnostic button row in emulator automation.
adb shell input keyevent 4
wait_text "Mit Celin" "$OUT/20-settings-home-return.xml"
sleep 1.0
adb exec-out screencap -p > "$OUT/20-settings-home-return.png"
PID_RETURN="$(adb shell pidof "$PACKAGE" | tr -d '\r ' || true)"
[[ "$PID_RETURN" = "$PID_HOME" ]] || fail "app process restarted returning HOME: before=$PID_HOME return=$PID_RETURN"
grep -Fq "Celin 3D Ansicht" "$OUT/20-settings-home-return.xml" || fail "HOME 3D stage missing after Avatar Lab return"
python3 ci/check-real-celine-render.py "$OUT/20-settings-home-return.png" HOME_RETURN
python3 ci/check-celine-person-presence.py "$OUT/20-settings-home-return.png" HOME_RETURN

timeout 15s adb logcat -d -v threadtime > "$OUT/settings-logcat.txt" 2>&1 || true
if grep -Eq 'REN-399|FATAL EXCEPTION|SIGABRT' "$OUT/settings-logcat.txt"; then
  fail "runtime error detected across Settings -> Avatar Lab -> HOME"
fi

printf 'PASS Settings -> Celine Avatar Lab -> same-process HOME return pid=%s\n' "$PID_HOME" | tee "$OUT/settings-summary.txt"
