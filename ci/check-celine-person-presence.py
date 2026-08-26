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
    length = struct.unpack(">I", raw[pos:pos + 4])[0]
    kind = raw[pos + 4:pos + 8]
    data = raw[pos + 8:pos + 8 + length]
    pos += 12 + length
    if kind == b"IHDR":
        width, height, bit_depth, color_type, _comp, _filt, interlace = struct.unpack(
            ">IIBBBBB", data
        )
        if bit_depth != 8 or interlace != 0:
            raise SystemExit(f"{label}: unsupported PNG format")
    elif kind == b"IDAT":
        idat.extend(data)
    elif kind == b"IEND":
        break

channels = {2: 3, 6: 4}.get(color_type)
if width is None or height is None or channels is None:
    raise SystemExit(f"{label}: unsupported/missing PNG metadata")

packed = zlib.decompress(bytes(idat))
stride = width * channels
rows = []
offset = 0
previous = bytearray(stride)

def paeth(a, b, c):
    value = a + b - c
    pa, pb, pc = abs(value - a), abs(value - b), abs(value - c)
    if pa <= pb and pa <= pc:
        return a
    if pb <= pc:
        return b
    return c

for _ in range(height):
    filter_type = packed[offset]
    offset += 1
    scan = bytearray(packed[offset:offset + stride])
    offset += stride
    reconstructed = bytearray(stride)
    for index in range(stride):
        left = reconstructed[index - channels] if index >= channels else 0
        up = previous[index]
        upper_left = previous[index - channels] if index >= channels else 0
        if filter_type == 0:
            value = scan[index]
        elif filter_type == 1:
            value = (scan[index] + left) & 255
        elif filter_type == 2:
            value = (scan[index] + up) & 255
        elif filter_type == 3:
            value = (scan[index] + ((left + up) // 2)) & 255
        elif filter_type == 4:
            value = (scan[index] + paeth(left, up, upper_left)) & 255
        else:
            raise SystemExit(f"{label}: unsupported filter {filter_type}")
        reconstructed[index] = value
    rows.append(reconstructed)
    previous = reconstructed

# Celine is centered in the real HOME and CALL stage. The strict warm skin/hair mask excludes the
# beige lamp, purple window and dark furniture that allowed an empty CALL room to pass the generic
# color-diversity check on v70 attempt 2.
x0, x1 = int(width * 0.32), int(width * 0.61)
y0, y1 = int(height * 0.20), int(height * 0.72)
skin_like = 0
sample_count = max(1, (x1 - x0) * (y1 - y0))
for y in range(y0, y1):
    row = rows[y]
    for x in range(x0, x1):
        index = x * channels
        red, green, blue = row[index], row[index + 1], row[index + 2]
        if (
            red > 135
            and 45 < green < 125
            and blue < 85
            and red - green > 35
        ):
            skin_like += 1

ratio = skin_like / sample_count
print(
    f"{label}: central Celine presence skin_like={skin_like} "
    f"ratio={ratio:.4f} crop=({x0},{y0})-({x1},{y1})"
)
if ratio < 0.003:
    raise SystemExit(f"{label}: real Celine person is not visibly present in the central stage")
