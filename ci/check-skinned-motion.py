#!/usr/bin/env python3
import math
import struct
import sys
import zlib
from pathlib import Path

if len(sys.argv) < 4:
    raise SystemExit("usage: check-skinned-motion.py frame-a.png frame-b.png frame-c.png")


def decode_png(path: Path):
    raw = path.read_bytes()
    if not raw.startswith(b"\x89PNG\r\n\x1a\n"):
        raise SystemExit(f"{path}: not a PNG")

    pos = 8
    width = height = color_type = bit_depth = None
    idat = bytearray()
    while pos + 8 <= len(raw):
        length = struct.unpack(">I", raw[pos:pos+4])[0]
        kind = raw[pos+4:pos+8]
        data = raw[pos+8:pos+8+length]
        pos += 12 + length
        if kind == b"IHDR":
            width, height, bit_depth, color_type, comp, filt, interlace = struct.unpack(">IIBBBBB", data)
            if bit_depth != 8 or interlace != 0:
                raise SystemExit(f"{path}: unsupported PNG bit depth/interlace")
        elif kind == b"IDAT":
            idat.extend(data)
        elif kind == b"IEND":
            break

    channels = {2: 3, 6: 4}.get(color_type)
    if width is None or height is None or channels is None:
        raise SystemExit(f"{path}: unsupported/missing PNG metadata")

    packed = zlib.decompress(bytes(idat))
    stride = width * channels
    rows = []
    off = 0
    prev = bytearray(stride)

    def paeth(a, b, c):
        p = a + b - c
        pa, pb, pc = abs(p-a), abs(p-b), abs(p-c)
        if pa <= pb and pa <= pc:
            return a
        if pb <= pc:
            return b
        return c

    for _ in range(height):
        f = packed[off]
        off += 1
        scan = bytearray(packed[off:off+stride])
        off += stride
        recon = bytearray(stride)
        for x in range(stride):
            a = recon[x-channels] if x >= channels else 0
            b = prev[x]
            c = prev[x-channels] if x >= channels else 0
            if f == 0:
                value = scan[x]
            elif f == 1:
                value = (scan[x] + a) & 255
            elif f == 2:
                value = (scan[x] + b) & 255
            elif f == 3:
                value = (scan[x] + ((a+b)//2)) & 255
            elif f == 4:
                value = (scan[x] + paeth(a, b, c)) & 255
            else:
                raise SystemExit(f"{path}: unsupported PNG filter {f}")
            recon[x] = value
        rows.append(recon)
        prev = recon
    return width, height, channels, rows


def magenta_centroid(path: Path):
    width, height, channels, rows = decode_png(path)
    x0, x1 = int(width * 0.12), int(width * 0.88)
    y0, y1 = int(height * 0.08), int(height * 0.70)
    count = 0
    sx = 0.0
    sy = 0.0
    for y in range(y0, y1):
        row = rows[y]
        for x in range(x0, x1):
            i = x * channels
            r, g, b = row[i], row[i+1], row[i+2]
            if r >= 190 and b >= 160 and g <= 95 and (r-g) >= 110 and (b-g) >= 80:
                count += 1
                sx += x
                sy += y
    required = max(180, int(width * height * 0.00015))
    if count < required:
        raise SystemExit(f"{path}: skinned magenta probe not visible: {count} < {required}")
    return count, sx / count, sy / count


frames = []
for arg in sys.argv[1:4]:
    path = Path(arg)
    count, cx, cy = magenta_centroid(path)
    frames.append((path.name, count, cx, cy))
    print(f"{path.name}: magenta={count}, centroid=({cx:.2f},{cy:.2f})")

max_distance = 0.0
max_pair = None
for i in range(len(frames)):
    for j in range(i + 1, len(frames)):
        dx = frames[i][2] - frames[j][2]
        dy = frames[i][3] - frames[j][3]
        distance = math.hypot(dx, dy)
        if distance > max_distance:
            max_distance = distance
            max_pair = (frames[i][0], frames[j][0])

# v53 camera motion is intentionally sub-pixel/small. The CI-only v54 Head probe is much larger;
# requiring a visible centroid shift proves the skinned geometry actually followed the Head joint.
required_motion = 5.0
print(f"skinned motion: max centroid shift={max_distance:.2f}px pair={max_pair}, required={required_motion:.2f}px")
if max_distance < required_motion:
    raise SystemExit("Head-only skinning probe did not visibly move the rendered mesh")
