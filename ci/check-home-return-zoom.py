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
        length = struct.unpack(">I", raw[pos:pos+4])[0]
        kind = raw[pos+4:pos+8]
        data = raw[pos+8:pos+8+length]
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
        pa, pb, pc = abs(p-a), abs(p-b), abs(p-c)
        if pa <= pb and pa <= pc: return a
        if pb <= pc: return b
        return c

    for _ in range(height):
        filt = packed[off]; off += 1
        scan = bytearray(packed[off:off+stride]); off += stride
        recon = bytearray(stride)
        for x in range(stride):
            a = recon[x-channels] if x >= channels else 0
            b = prev[x]
            c = prev[x-channels] if x >= channels else 0
            if filt == 0: val = scan[x]
            elif filt == 1: val = (scan[x] + a) & 255
            elif filt == 2: val = (scan[x] + b) & 255
            elif filt == 3: val = (scan[x] + ((a+b)//2)) & 255
            elif filt == 4: val = (scan[x] + paeth(a,b,c)) & 255
            else: raise SystemExit(f"{path}: unsupported filter {filt}")
            recon[x] = val
        rows.append(recon)
        prev = recon
    return width, height, channels, rows


def warm_avatar_height(path):
    width, height, channels, rows = decode(path)
    # Central stage slice. Celine's skin/hair/top are warm textured pixels here; window/plant/UI
    # are outside or fail the warm-color test. This makes the measurement independent of the room.
    x0, x1 = int(width * 0.34), int(width * 0.64)
    y0, y1 = int(height * 0.20), int(height * 0.56)
    active = []
    total = 0
    for y in range(y0, y1):
        row = rows[y]
        count = 0
        for x in range(x0, x1):
            i = x * channels
            r, g, b = row[i], row[i+1], row[i+2]
            spread = max(r,g,b) - min(r,g,b)
            # Warm Celine texture / skin / hair / shoes. Exclude beige lamp by requiring a
            # meaningful red lead over green and blue.
            if r >= 65 and spread >= 18 and r - g >= 9 and r - b >= 18 and g <= int(r * 0.82):
                count += 1
        if count >= 5:
            active.append(y)
            total += count
    if len(active) < 20:
        raise SystemExit(f"{path}: could not resolve enough Celine warm-detail rows")
    # Ignore isolated edge rows by trimming 2% from either end.
    active.sort()
    trim = max(1, len(active)//50)
    lo = active[trim]
    hi = active[-trim-1]
    return hi - lo + 1, total, (lo, hi), (width, height)


if len(sys.argv) != 3:
    raise SystemExit("usage: check-home-return-zoom.py HOME.png HOME_RETURN.png")

home_h, home_px, home_bounds, size1 = warm_avatar_height(sys.argv[1])
ret_h, ret_px, ret_bounds, size2 = warm_avatar_height(sys.argv[2])
if size1 != size2:
    raise SystemExit(f"image size mismatch: {size1} vs {size2}")
ratio = ret_h / float(home_h)
print(f"HOME zoom proof: homeHeight={home_h} returnHeight={ret_h} ratio={ratio:.3f} homeWarm={home_px} returnWarm={ret_px} bounds={home_bounds}->{ret_bounds}")

# The previous 38mm -> 32mm lifecycle regression produced roughly a 0.84 scale ratio. Allow
# natural motion but reject meaningful camera/lens changes on HOME return.
if ratio < 0.93 or ratio > 1.07:
    raise SystemExit(f"HOME_RETURN zoom/framing changed too much: ratio={ratio:.3f}")
