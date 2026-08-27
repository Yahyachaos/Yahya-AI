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

finalize() {
  local status=$?
  local evidence_count=0
  trap - EXIT
  timeout 15s adb logcat -d -v threadtime > "$OUT/logcat.txt" 2>&1 || true
  timeout 10s adb shell dumpsys window windows > "$OUT/window.txt" 2>&1 || true
  evidence_count="$(find "$OUT" -maxdepth 1 -type f -name '[0-9][0-9]-*.png' ! -name '00-warmup-visible.png' | wc -l | tr -d ' ')"
  {
    echo "Avatar Lab lightweight proof finished."
    echo "Status=$([ "$status" -eq 0 ] && echo PASS || echo FAIL)"
    echo "APK=$APK"
    echo "CaptureActivity=$CAPTURE_ACTIVITY"
    echo "EvidenceScreenshots=$evidence_count"
  } > "$OUT/summary.txt"
  exit "$status"
}
trap finalize EXIT

launch_state() {
  local pose="$1"
  local camera="$2"
  local orbit="$3"
  local face="$4"
  local process_mode="${5:-keep}"

  # Cold Filament warm-up may require a complete process restart. Once one visible frame exists,
  # keep the process alive: Proof #18 showed that killing the process after a successful warm-up
  # discarded the useful renderer state and the first real evidence launch returned blank again.
  if [ "$process_mode" = "restart" ]; then
    adb shell am force-stop de.yahya.ai
  fi

  # CLEAR_TOP replaces the debug capture Activity state without killing the app process. The
  # Activity uses standard launch mode, so its new intent extras are applied by a fresh instance
  # while process-level Filament/shader caches remain available.
  adb shell am start -W --activity-clear-top -n "$CAPTURE_ACTIVITY" \
    --es ci_pose "$pose" \
    --es ci_camera "$camera" \
    --es ci_orbit "$orbit" \
    --es ci_face "$face" >/dev/null
  sleep 1.65
}

png_color_count() {
  python3 - "$1" <<'PY'
import struct
import sys
import zlib

data = open(sys.argv[1], "rb").read()
if data[:8] != b"\x89PNG\r\n\x1a\n":
    raise SystemExit("not a PNG")
pos = 8
idat = bytearray()
header = None
while pos + 12 <= len(data):
    length = struct.unpack(">I", data[pos:pos + 4])[0]
    kind = data[pos + 4:pos + 8]
    body = data[pos + 8:pos + 8 + length]
    pos += 12 + length
    if kind == b"IHDR":
        header = struct.unpack(">IIBBBBB", body)
    elif kind == b"IDAT":
        idat.extend(body)
    elif kind == b"IEND":
        break
if header is None:
    raise SystemExit("missing IHDR")
width, height, depth, color_type, compression, filter_method, interlace = header
channels = {0: 1, 2: 3, 4: 2, 6: 4}.get(color_type)
if depth != 8 or channels is None or compression or filter_method or interlace:
    raise SystemExit("unsupported PNG layout")
raw = zlib.decompress(bytes(idat))
stride = width * channels
previous = bytearray(stride)
offset = 0
colors = set()

def paeth(a, b, c):
    p = a + b - c
    pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
    return a if pa <= pb and pa <= pc else b if pb <= pc else c

for _ in range(height):
    mode = raw[offset]
    scan = bytearray(raw[offset + 1:offset + 1 + stride])
    offset += stride + 1
    for i in range(stride):
        left = scan[i - channels] if i >= channels else 0
        up = previous[i]
        upper_left = previous[i - channels] if i >= channels else 0
        if mode == 1:
            scan[i] = (scan[i] + left) & 255
        elif mode == 2:
            scan[i] = (scan[i] + up) & 255
        elif mode == 3:
            scan[i] = (scan[i] + ((left + up) >> 1)) & 255
        elif mode == 4:
            scan[i] = (scan[i] + paeth(left, up, upper_left)) & 255
        elif mode != 0:
            raise SystemExit("unsupported PNG filter")
    for x in range(0, stride, channels):
        if color_type in (0, 4):
            color = (scan[x], scan[x], scan[x])
        else:
            color = (scan[x], scan[x + 1], scan[x + 2])
        colors.add(color)
        if len(colors) >= 1000:
            print(1000)
            raise SystemExit(0)
    previous = scan
print(len(colors))
PY
}

capture() {
  local name="$1"
  local target="$OUT/${name}.png"
  local attempt
  local colors=0

  # Proof #18 spent almost the whole capture timeout repeating screenshots from one permanently
  # blank Activity launch. Keep this retry window deliberately small; capture_state() will replace
  # the Activity and retry the state instead of burning minutes on the same dead compositor frame.
  for attempt in 1 2; do
    adb exec-out screencap -p > "$target"
    if [ -s "$target" ]; then
      colors="$(png_color_count "$target" 2>/dev/null || echo 0)"
      if [ "$colors" -ge 1000 ]; then
        echo "Visible screenshot: $name colors=$colors attempt=$attempt"
        return 0
      fi
    fi
    sleep 0.55
  done

  echo "Visually blank screenshot: $name colors=$colors" >&2
  return 1
}

capture_state() {
  local pose="$1"
  local camera="$2"
  local orbit="$3"
  local face="$4"
  local name="$5"
  local state_attempt

  # Retry the complete debug Activity state, but keep the already-warmed app process alive.
  for state_attempt in 1 2 3; do
    launch_state "$pose" "$camera" "$orbit" "$face" keep
    if capture "$name"; then
      return 0
    fi
    echo "Relaunching evidence state: $name state_attempt=$state_attempt" >&2
  done

  echo "Evidence state never became visible after bounded relaunches: $name" >&2
  return 1
}

warm_renderer_cache() {
  local target="$OUT/.warmup.png"
  local warmup
  local colors=0

  # The software Filament backend can need more than one cold process start. Restart only during
  # this warm-up stage. As soon as one compositor-visible avatar frame exists, preserve that process
  # for all real evidence states and keep a diagnostic copy of the successful warm-up frame.
  for warmup in 1 2 3 4; do
    launch_state stand full front neutral restart
    adb exec-out screencap -p > "$target"
    colors="$(png_color_count "$target" 2>/dev/null || echo 0)"
    echo "Renderer warm-up: attempt=$warmup colors=$colors"
    if [ "$colors" -ge 1000 ]; then
      cp "$target" "$OUT/00-warmup-visible.png"
      return 0
    fi
  done

  echo "Renderer never produced a visible Avatar Lab warm-up frame: colors=$colors" >&2
  return 1
}

warm_renderer_cache

# Close-up uses a held morph instead of a timed animation, so cheek/eyelid comparison is exact.
capture_state stand face front neutral "01-face-neutral-close"
capture_state stand face front blink85 "02-face-blink-85-held"
capture_state stand face front neutral "03-face-open-after"

# Full-body grounding.
capture_state stand full front neutral "04-standing-front"
capture_state seated full front neutral "05-seated-front"

# Two frames from one continuous arm/hand mode prove visible motion instead of a frozen pose.
capture_state arms full front neutral "06-arms-hands-a"
sleep 1.45
capture "07-arms-hands-b"

# Two frames from one continuous walk mode.
capture_state walk full front neutral "08-walk-a"
sleep 0.72
capture "09-walk-b"

# Deterministic branch-avatar orientation checks.
capture_state stand full profile_left neutral "10-profile-left"
capture_state stand full three_right neutral "11-three-quarter-right"
capture_state stand full front neutral "12-front-return"

# Persist diagnostics before assertions so a failing assertion still has inspectable evidence.
timeout 15s adb logcat -d -v threadtime > "$OUT/logcat.txt" 2>&1 || true

evidence_count="$(find "$OUT" -maxdepth 1 -type f -name '[0-9][0-9]-*.png' ! -name '00-warmup-visible.png' | wc -l | tr -d ' ')"
if [ "$evidence_count" -ne 12 ]; then
  echo "Expected 12 evidence screenshots, found $evidence_count" >&2
  exit 1
fi

if ! grep -Fq "V61-110" "$OUT/logcat.txt" || ! grep -Fq "Meshy Rig-Scale korrigiert" "$OUT/logcat.txt"; then
  echo "Missing required V61-110 Meshy rig-scale correction evidence in logcat" >&2
  exit 1
fi

echo "Avatar Lab evidence passed structural/visibility guards; manual visual acceptance is still required."
