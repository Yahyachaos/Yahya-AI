#!/usr/bin/env python3
import struct
import sys
import zlib
from pathlib import Path


def decode(path):
    raw = Path(path).read_bytes()
    if not raw.startswith(b"\x89PNG\r\n\x1a\n"):
        raise SystemExit(f"{path}: not a PNG")
    pos = 8
    width = height = bit_depth = color_type = None
    idat = bytearray()
    while pos + 8 <= len(raw):
        length = struct.unpack(">I", raw[pos:pos + 4])[0]
        kind = raw[pos + 4:pos + 8]
        data = raw[pos + 8:pos + 8 + length]
        pos += 12 + length
        if kind == b"IHDR":
            width, height, bit_depth, color_type, _, _, interlace = struct.unpack(">IIBBBBB", data)
            if bit_depth != 8 or interlace != 0:
                raise SystemExit(f"{path}: unsupported PNG")
        elif kind == b"IDAT":
            idat.extend(data)
        elif kind == b"IEND":
            break
    channels = {2: 3, 6: 4}.get(color_type)
    if not width or not height or not channels:
        raise SystemExit(f"{path}: missing PNG metadata")
    packed = zlib.decompress(bytes(idat))
    stride = width * channels
    rows = []
    off = 0
    prev = bytearray(stride)

    def paeth(a, b, c):
        p = a + b - c
        pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
        if pa <= pb and pa <= pc:
            return a
        if pb <= pc:
            return b
        return c

    for _ in range(height):
        filt = packed[off]
        off += 1
        scan = bytearray(packed[off:off + stride])
        off += stride
        recon = bytearray(stride)
        for x in range(stride):
            a = recon[x - channels] if x >= channels else 0
            b = prev[x]
            c = prev[x - channels] if x >= channels else 0
            if filt == 0:
                value = scan[x]
            elif filt == 1:
                value = (scan[x] + a) & 255
            elif filt == 2:
                value = (scan[x] + b) & 255
            elif filt == 3:
                value = (scan[x] + ((a + b) // 2)) & 255
            elif filt == 4:
                value = (scan[x] + paeth(a, b, c)) & 255
            else:
                raise SystemExit(f"{path}: unsupported PNG filter {filt}")
            recon[x] = value
        rows.append(recon)
        prev = recon
    return width, height, channels, rows


def avatar_height(path):
    width, height, channels, rows = decode(path)
    # Same warm Celine-detail family used by the stable HOME-return gate, but with a taller crop
    # so the 2.20 near checkpoint can grow without being artificially capped by the old window.
    x0, x1 = int(width * 0.28), int(width * 0.68)
    y0, y1 = int(height * 0.08), int(height * 0.76)
    active = []
    warm_pixels = 0
    for y in range(y0, y1):
        row = rows[y]
        count = 0
        for x in range(x0, x1):
            i = x * channels
            r, g, b = row[i], row[i + 1], row[i + 2]
            spread = max(r, g, b) - min(r, g, b)
            if r >= 65 and spread >= 18 and r - g >= 9 and r - b >= 18 and g <= int(r * 0.82):
                count += 1
        if count >= 5:
            active.append(y)
            warm_pixels += count
    if len(active) < 18:
        raise SystemExit(f"{path}: Celine warm-detail rows missing")
    active.sort()
    trim = max(1, len(active) // 50)
    lo = active[trim]
    hi = active[-trim - 1]
    return hi - lo + 1, warm_pixels, (lo, hi), (width, height)


if len(sys.argv) != 4:
    raise SystemExit("usage: check-camera-zoom-range.py FAR.png DEFAULT.png NEAR.png")

far_h, far_px, far_bounds, size_far = avatar_height(sys.argv[1])
def_h, def_px, def_bounds, size_def = avatar_height(sys.argv[2])
near_h, near_px, near_bounds, size_near = avatar_height(sys.argv[3])
if not (size_far == size_def == size_near):
    raise SystemExit(f"zoom image size mismatch: {size_far}, {size_def}, {size_near}")

far_ratio = def_h / float(far_h)
near_ratio = near_h / float(def_h)
print(
    "V70 zoom range proof: "
    f"farHeight={far_h} defaultHeight={def_h} nearHeight={near_h} "
    f"far->default={far_ratio:.3f} default->near={near_ratio:.3f} "
    f"warm={far_px}/{def_px}/{near_px} bounds={far_bounds}/{def_bounds}/{near_bounds}"
)

# The camera must actually move toward Celine. Keep margins generous for v44's small natural
# body motion but reject the old failure where pinch changed state without a visible scale change.
if far_ratio < 1.10:
    raise SystemExit(f"default is not meaningfully closer than zoom 0.55: ratio={far_ratio:.3f}")
if near_ratio < 1.10:
    raise SystemExit(f"zoom 2.20 is not meaningfully closer than default: ratio={near_ratio:.3f}")
