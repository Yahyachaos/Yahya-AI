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

  # A Filament frame callback can complete before the emulator compositor presents Celine.
  # Retry only the screenshot (never the Activity/build) until the frame contains materially
  # more than the known 291-297-color empty system-bars/background image. The parser uses only
  # Python's standard library so readiness does not depend on mutable runner packages.
  for attempt in $(seq 1 14); do
    adb exec-out screencap -p > "$target"
    if [ -s "$target" ]; then
      colors="$(png_color_count "$target" 2>/dev/null || echo 0)"
      if [ "$colors" -ge 1000 ]; then
        echo "Visible screenshot: $name colors=$colors attempt=$attempt"
        return 0
      fi
    fi
    sleep 0.50
  done

  echo "Visually blank screenshot after bounded retries: $name colors=$colors" >&2
  return 1
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
