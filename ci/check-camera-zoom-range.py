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


def is_warm_detail(r, g, b):
    spread = max(r, g, b) - min(r, g, b)
    return r >= 65 and spread >= 18 and r - g >= 9 and r - b >= 18 and g <= int(r * 0.82)


def is_skin_detail(r, g, b):
    return r > 135 and 45 < g < 125 and b < 85 and r - g > 35


def avatar_height(path):
    width, height, channels, rows = decode(path)
    # Same warm Celine-detail family used by the stable HOME-return gate. The crop is intentionally
    # much taller than the room card so clipping cannot be hidden by the measurement window itself.
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
            if is_warm_detail(r, g, b):
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


def framing_anchors(path):
    width, height, channels, rows = decode(path)
    # Empirical anchors from the exact v70 production avatar. At the old 2.20 checkpoint the head
    # leaves the viewport and this face crop loses almost all strict skin pixels; simultaneously the
    # feet leave the lower central crop. A usable close checkpoint must retain both relative to the
    # fully framed default image, so torso-only zoom can no longer pass as "visible".
    face = (int(width * 0.45), int(width * 0.56), int(height * 0.22), int(height * 0.29))
    lower = (int(width * 0.38), int(width * 0.62), int(height * 0.47), int(height * 0.62))
    face_skin = 0
    lower_warm = 0
    for y in range(face[2], face[3]):
        row = rows[y]
        for x in range(face[0], face[1]):
            i = x * channels
            if is_skin_detail(row[i], row[i + 1], row[i + 2]):
                face_skin += 1
    for y in range(lower[2], lower[3]):
        row = rows[y]
        for x in range(lower[0], lower[1]):
            i = x * channels
            if is_warm_detail(row[i], row[i + 1], row[i + 2]):
                lower_warm += 1
    return face_skin, lower_warm, face, lower


if len(sys.argv) != 4:
    raise SystemExit("usage: check-camera-zoom-range.py FAR.png DEFAULT.png NEAR.png")

far_h, far_px, far_bounds, size_far = avatar_height(sys.argv[1])
def_h, def_px, def_bounds, size_def = avatar_height(sys.argv[2])
near_h, near_px, near_bounds, size_near = avatar_height(sys.argv[3])
if not (size_far == size_def == size_near):
    raise SystemExit(f"zoom image size mismatch: {size_far}, {size_def}, {size_near}")

def_face, def_lower, face_crop, lower_crop = framing_anchors(sys.argv[2])
near_face, near_lower, _, _ = framing_anchors(sys.argv[3])
# Exact-head v71 evidence captured a fully visible default frame with 567 strict face pixels while
# the unchanged retry exceeded the former 1000-pixel floor. Keep 500 as the absolute presence floor;
# the relative near/default face and lower-body ratios below remain the clipping protection.
if def_face < 500 or def_lower < 500:
    raise SystemExit(
        f"default framing anchors are unexpectedly weak: face={def_face} lower={def_lower} "
        f"faceCrop={face_crop} lowerCrop={lower_crop}"
    )

far_ratio = def_h / float(far_h)
near_ratio = near_h / float(def_h)
face_ratio = near_face / float(def_face)
lower_ratio = near_lower / float(def_lower)
print(
    "V70 zoom range proof: "
    f"farHeight={far_h} defaultHeight={def_h} nearHeight={near_h} "
    f"far->default={far_ratio:.3f} default->near={near_ratio:.3f} "
    f"warm={far_px}/{def_px}/{near_px} bounds={far_bounds}/{def_bounds}/{near_bounds} "
    f"face={def_face}->{near_face}({face_ratio:.3f}) lower={def_lower}->{near_lower}({lower_ratio:.3f})"
)

# The camera must actually move toward Celine. Keep margins generous for v44's small natural body
# motion but reject the old failure where pinch changed state without a visible scale change.
if far_ratio < 1.10:
    raise SystemExit(f"default is not meaningfully closer than zoom 0.55: ratio={far_ratio:.3f}")
if near_ratio < 1.10:
    raise SystemExit(f"safe near zoom is not meaningfully closer than default: ratio={near_ratio:.3f}")

# The old 2.20 image kept a large torso visible while losing the head and feet. Relative anchors
# explicitly protect both ends of the person so a clipped close-up cannot satisfy the visual gate.
if face_ratio < 0.50:
    raise SystemExit(
        f"safe near zoom clipped or lost Celine's face/head anchor: ratio={face_ratio:.3f} "
        f"default={def_face} near={near_face}"
    )
if lower_ratio < 0.50:
    raise SystemExit(
        f"safe near zoom clipped or lost Celine's lower-body/feet anchor: ratio={lower_ratio:.3f} "
        f"default={def_lower} near={near_lower}"
    )
