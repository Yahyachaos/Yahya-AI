#!/usr/bin/env python3
import hashlib
import struct
import sys
import zlib
from pathlib import Path


def decode(path):
    raw = Path(path).read_bytes()
    if not raw.startswith(b"\x89PNG\r\n\x1a\n"):
        raise SystemExit(f"{path}: not a PNG")
    pos = 8
    width = height = color_type = None
    idat = bytearray()
    while pos + 8 <= len(raw):
        length = struct.unpack(">I", raw[pos:pos + 4])[0]
        kind = raw[pos + 4:pos + 8]
        data = raw[pos + 8:pos + 8 + length]
        pos += 12 + length
        if kind == b"IHDR":
            width, height, depth, color_type, _comp, _filter, interlace = struct.unpack(
                ">IIBBBBB", data
            )
            if depth != 8 or interlace != 0:
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
        mode = packed[offset]
        offset += 1
        scan = bytearray(packed[offset:offset + stride])
        offset += stride
        reconstructed = bytearray(stride)
        for index in range(stride):
            left = reconstructed[index - channels] if index >= channels else 0
            up = previous[index]
            upper_left = previous[index - channels] if index >= channels else 0
            if mode == 0:
                value = scan[index]
            elif mode == 1:
                value = (scan[index] + left) & 255
            elif mode == 2:
                value = (scan[index] + up) & 255
            elif mode == 3:
                value = (scan[index] + ((left + up) // 2)) & 255
            elif mode == 4:
                value = (scan[index] + paeth(left, up, upper_left)) & 255
            else:
                raise SystemExit(f"{path}: unsupported PNG filter {mode}")
            reconstructed[index] = value
        rows.append(reconstructed)
        previous = reconstructed
    return raw, width, height, channels, rows


def is_face_skin(red, green, blue):
    return red > 135 and 45 < green < 130 and blue < 90 and red - green > 32


def framing(path):
    raw, width, height, channels, rows = decode(path)
    x0, x1 = int(width * 0.20), int(width * 0.80)
    y0, y1 = int(height * 0.12), int(height * 0.75)
    skin = []
    peak_row = 0
    for y in range(y0, y1):
        row = rows[y]
        row_count = 0
        for x in range(x0, x1):
            index = x * channels
            if is_face_skin(row[index], row[index + 1], row[index + 2]):
                skin.append((x, y))
                row_count += 1
        peak_row = max(peak_row, row_count)
    if len(skin) < 500:
        raise SystemExit(f"{path}: production Celine face/skin presence is too weak: {len(skin)}")
    xs = sorted(x for x, _ in skin)
    ys = sorted(y for _, y in skin)
    trim = max(1, len(skin) // 100)
    left, right = xs[trim], xs[-trim - 1]
    top, bottom = ys[trim], ys[-trim - 1]
    center_x = sum(xs) / len(xs)
    center_y = sum(ys) / len(ys)
    return {
        "path": path,
        "size": (width, height),
        "sha256": hashlib.sha256(raw).hexdigest(),
        "skin": len(skin),
        "peak_row": peak_row,
        "bounds": (left, top, right, bottom),
        "center": (center_x, center_y),
    }


if len(sys.argv) != 4:
    raise SystemExit("usage: check-camera-zoom-range.py CALL_NORMAL.png CALL_HEAD.png CALL_FACE.png")

normal, head, face = [framing(path) for path in sys.argv[1:]]
if not (normal["size"] == head["size"] == face["size"]):
    raise SystemExit(f"zoom image size mismatch: {normal['size']}, {head['size']}, {face['size']}")
if len({normal["sha256"], head["sha256"], face["sha256"]}) != 3:
    raise SystemExit("camera framing checkpoints are stale-identical")

head_ratio = head["peak_row"] / float(normal["peak_row"])
face_ratio = face["peak_row"] / float(head["peak_row"])
skin_ratio = face["skin"] / float(normal["skin"])
width, height = face["size"]
face_left, face_top, face_right, face_bottom = face["bounds"]
face_center_x, face_center_y = face["center"]

print(
    "V80 production CALL camera framing: "
    f"peakRow={normal['peak_row']}->{head['peak_row']}->{face['peak_row']} "
    f"ratios={head_ratio:.3f}/{face_ratio:.3f} skin={normal['skin']}->{head['skin']}->{face['skin']} "
    f"skinRatio={skin_ratio:.3f} bounds={normal['bounds']}/{head['bounds']}/{face['bounds']} "
    f"faceCenter=({face_center_x:.1f},{face_center_y:.1f}) "
    "manualVisualAcceptanceRequired=true"
)

if head_ratio < 1.08:
    raise SystemExit(f"head-and-shoulders checkpoint is not closer than normal CALL: {head_ratio:.3f}")
if face_ratio < 1.08:
    raise SystemExit(f"face-close checkpoint is not materially closer: peak={face_ratio:.3f}")
# Total skin count is not monotonic once a legitimate close-up intentionally removes hands/lower
# body from the viewport. Keep a loss guard, but use peak face-row width for proximity.
if skin_ratio < 0.75:
    raise SystemExit(f"face-close lost too much person/face evidence: skin={skin_ratio:.3f}")
if face_left <= int(width * 0.08) or face_right >= int(width * 0.92):
    raise SystemExit(f"face-close is horizontally clipped: bounds={face['bounds']}")
if face_top <= int(height * 0.08) or face_bottom >= int(height * 0.74):
    raise SystemExit(f"face-close escaped the safe CALL viewport: bounds={face['bounds']}")
if not (width * 0.30 <= face_center_x <= width * 0.70):
    raise SystemExit(f"face-close drifted away from horizontal center: x={face_center_x:.1f}")
if not (height * 0.18 <= face_center_y <= height * 0.62):
    raise SystemExit(f"face-close drifted away from conversational vertical center: y={face_center_y:.1f}")
