#!/usr/bin/env python3
import struct
import sys
import zlib
from pathlib import Path

path = Path(sys.argv[1])
label = sys.argv[2] if len(sys.argv) > 2 else path.stem
raw = path.read_bytes()
if not raw.startswith(b"\x89PNG\r\n\x1a\n"):
    raise SystemExit(f"{label}: not a PNG")

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
            raise SystemExit(f"{label}: unsupported PNG bit_depth/interlace")
    elif kind == b"IDAT":
        idat.extend(data)
    elif kind == b"IEND":
        break

if width is None or height is None:
    raise SystemExit(f"{label}: missing IHDR")
channels = {2: 3, 6: 4}.get(color_type)
if channels is None:
    raise SystemExit(f"{label}: unsupported color type {color_type}")

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

for y in range(height):
    f = packed[off]
    off += 1
    scan = bytearray(packed[off:off+stride])
    off += stride
    recon = bytearray(stride)
    for x in range(stride):
        a = recon[x-channels] if x >= channels else 0
        b = prev[x]
        c = prev[x-channels] if x >= channels else 0
        if f == 0: val = scan[x]
        elif f == 1: val = (scan[x] + a) & 255
        elif f == 2: val = (scan[x] + b) & 255
        elif f == 3: val = (scan[x] + ((a+b)//2)) & 255
        elif f == 4: val = (scan[x] + paeth(a,b,c)) & 255
        else: raise SystemExit(f"{label}: unsupported PNG filter {f}")
        recon[x] = val
    rows.append(recon)
    prev = recon

# Restrict to the upper/central content area so purple/red Android UI buttons cannot satisfy the
# assertion. The synthetic Filament avatar is bright magenta and lives inside the avatar stage.
x0, x1 = int(width * 0.16), int(width * 0.84)
y0, y1 = int(height * 0.12), int(height * 0.68)
count = 0
for y in range(y0, y1):
    row = rows[y]
    for x in range(x0, x1):
        i = x * channels
        r, g, b = row[i], row[i+1], row[i+2]
        if r >= 190 and b >= 160 and g <= 95 and (r - g) >= 110 and (b - g) >= 80:
            count += 1

required = max(180, int(width * height * 0.00015))
print(f"{label}: magenta-avatar pixels={count}, required={required}, image={width}x{height}")
if count < required:
    raise SystemExit(f"{label}: 3D avatar marker is NOT visible")
