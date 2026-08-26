#!/usr/bin/env python3
"""Generate Celine v75 from the validated v65 candidate without external packages.

The canonical LFS source and the v65 intermediate stay immutable.  This stage appends
new POSITION/NORMAL/texture payloads and redirects the glTF accessors to them.  Bones,
skin weights, indices, animations and facial morph deltas are intentionally untouched.
"""

import argparse
from array import array
import binascii
import hashlib
import json
import math
import os
import struct
import sys
import zlib


COMPONENT = {5120: "b", 5121: "B", 5122: "h", 5123: "H", 5125: "I", 5126: "f"}
COMPONENTS = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}
SOURCE_SHA256 = "0c9fa09f898fbc8c0503be252c8fec1ee815a3a4990422e5c302e3113d7c1b55"
V65_SHA256 = "6e507144afa22f0534be0419884932a0c6aaa16b8b2013580013ffe5056bb146"
MASK_SIZE = 1024


def load_glb(path):
    raw = open(path, "rb").read()
    if raw[:4] != b"glTF":
        raise SystemExit("Input is not a GLB")
    offset = 12
    document = None
    binary = b""
    while offset < len(raw):
        length, chunk_type = struct.unpack_from("<II", raw, offset)
        offset += 8
        chunk = raw[offset:offset + length]
        offset += length
        if chunk_type == 0x4E4F534A:
            document = json.loads(chunk.decode("utf-8").rstrip("\x00 "))
        elif chunk_type == 0x004E4942:
            binary = chunk
    if document is None:
        raise SystemExit("GLB JSON chunk missing")
    return raw, document, binary


def read_accessor(document, binary, index):
    accessor = document["accessors"][index]
    view = document["bufferViews"][accessor["bufferView"]]
    fmt = COMPONENT[accessor["componentType"]]
    components = COMPONENTS[accessor["type"]]
    item_size = struct.calcsize("<" + fmt) * components
    stride = view.get("byteStride", item_size)
    start = view.get("byteOffset", 0) + accessor.get("byteOffset", 0)
    unpack = struct.Struct("<" + fmt * components).unpack_from
    return [unpack(binary, start + row * stride) for row in range(accessor["count"])]


def smooth_band(value, start, peak, end):
    if value <= start or value >= end:
        return 0.0
    if value < peak:
        t = (value - start) / (peak - start)
    else:
        t = (end - value) / (end - peak)
    return t * t * (3.0 - 2.0 * t)


def joint_weight(js, ws, wanted):
    return sum(float(weight) for joint, weight in zip(js, ws) if int(joint) in wanted)


def payload(values):
    out = array("f", values)
    if sys.byteorder != "little":
        out.byteswap()
    return out.tobytes()


def append_vec3(document, binary_out, values, target=34962):
    while len(binary_out) % 4:
        binary_out.append(0)
    offset = len(binary_out)
    data = payload(component for row in values for component in row)
    binary_out.extend(data)
    view_index = len(document.setdefault("bufferViews", []))
    document["bufferViews"].append({
        "buffer": 0, "byteOffset": offset, "byteLength": len(data), "target": target,
    })
    accessor_index = len(document.setdefault("accessors", []))
    lows = [min(row[c] for row in values) for c in range(3)]
    highs = [max(row[c] for row in values) for c in range(3)]
    document["accessors"].append({
        "bufferView": view_index, "componentType": 5126, "count": len(values),
        "type": "VEC3", "min": lows, "max": highs,
    })
    return accessor_index


def normals_for(positions, indices):
    normals = [[0.0, 0.0, 0.0] for _ in positions]
    for offset in range(0, len(indices), 3):
        ia, ib, ic = (int(indices[offset + i][0]) for i in range(3))
        a, b, c = positions[ia], positions[ib], positions[ic]
        ab = (b[0] - a[0], b[1] - a[1], b[2] - a[2])
        ac = (c[0] - a[0], c[1] - a[1], c[2] - a[2])
        cross = (
            ab[1] * ac[2] - ab[2] * ac[1],
            ab[2] * ac[0] - ab[0] * ac[2],
            ab[0] * ac[1] - ab[1] * ac[0],
        )
        for index in (ia, ib, ic):
            normals[index][0] += cross[0]
            normals[index][1] += cross[1]
            normals[index][2] += cross[2]
    result = []
    for normal in normals:
        length = math.sqrt(sum(value * value for value in normal))
        result.append(tuple(value / length for value in normal) if length > 1.0e-12 else (0.0, 1.0, 0.0))
    return result


def png_chunks(data):
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise SystemExit("Embedded v75 texture must be PNG")
    offset = 8
    while offset < len(data):
        length = struct.unpack(">I", data[offset:offset + 4])[0]
        kind = data[offset + 4:offset + 8]
        payload_data = data[offset + 8:offset + 8 + length]
        yield kind, payload_data
        offset += 12 + length


def paeth(a, b, c):
    p = a + b - c
    pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
    return a if pa <= pb and pa <= pc else b if pb <= pc else c


def decode_rgb_png(data):
    chunks = list(png_chunks(data))
    ihdr = next(payload_data for kind, payload_data in chunks if kind == b"IHDR")
    width, height, depth, color, compression, filtering, interlace = struct.unpack(">IIBBBBB", ihdr)
    if (depth, color, compression, filtering, interlace) != (8, 2, 0, 0, 0):
        raise SystemExit("Expected non-interlaced 8-bit RGB PNG")
    packed = zlib.decompress(b"".join(payload_data for kind, payload_data in chunks if kind == b"IDAT"))
    stride = width * 3
    rows, filter_types, previous = [], [], bytearray(stride)
    offset = 0
    for _ in range(height):
        filter_type = packed[offset]
        offset += 1
        encoded = packed[offset:offset + stride]
        offset += stride
        row = bytearray(stride)
        for i, value in enumerate(encoded):
            left = row[i - 3] if i >= 3 else 0
            up = previous[i]
            upper_left = previous[i - 3] if i >= 3 else 0
            predictor = (0, left, up, (left + up) // 2, paeth(left, up, upper_left))[filter_type]
            row[i] = (value + predictor) & 255
        rows.append(row)
        filter_types.append(filter_type)
        previous = row
    return width, height, rows, filter_types


def make_chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", binascii.crc32(kind + data) & 0xFFFFFFFF)


def encode_rgb_png(width, height, rows, filter_types):
    packed = bytearray()
    previous = bytearray(width * 3)
    for row, filter_type in zip(rows, filter_types):
        packed.append(filter_type)
        encoded = bytearray(len(row))
        for i, value in enumerate(row):
            left = row[i - 3] if i >= 3 else 0
            up = previous[i]
            upper_left = previous[i - 3] if i >= 3 else 0
            predictor = (0, left, up, (left + up) // 2, paeth(left, up, upper_left))[filter_type]
            encoded[i] = (value - predictor) & 255
        packed.extend(encoded)
        previous = row
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + make_chunk(b"IHDR", ihdr) + make_chunk(b"IDAT", zlib.compress(bytes(packed), 9)) + make_chunk(b"IEND", b"")


def fill_triangle(mask, points, label):
    # Atlas UV islands never intentionally span the wrap seam.  Ignore degenerate seam triangles.
    xs = [point[0] for point in points]
    ys = [point[1] for point in points]
    if max(xs) - min(xs) > MASK_SIZE // 2 or max(ys) - min(ys) > MASK_SIZE // 2:
        return
    y0 = max(0, int(math.floor(min(ys))))
    y1 = min(MASK_SIZE - 1, int(math.ceil(max(ys))))
    edges = list(zip(points, points[1:] + points[:1]))
    for y in range(y0, y1 + 1):
        scan_y = y + 0.5
        crossings = []
        for (xa, ya), (xb, yb) in edges:
            if (ya <= scan_y < yb) or (yb <= scan_y < ya):
                crossings.append(xa + (scan_y - ya) * (xb - xa) / (yb - ya))
        if len(crossings) < 2:
            continue
        start = max(0, int(math.floor(min(crossings))))
        end = min(MASK_SIZE - 1, int(math.ceil(max(crossings))))
        if end >= start:
            begin = y * MASK_SIZE + start
            mask[begin:begin + end - start + 1] = bytes([label]) * (end - start + 1)


def semantic_labels(positions, joints, weights, joint_names):
    by_name = {name: index for index, name in enumerate(joint_names)}
    legs = {by_name[name] for name in ("Hips", "LeftUpLeg", "LeftLeg", "RightUpLeg", "RightLeg")}
    feet = {by_name[name] for name in ("LeftFoot", "LeftToeBase", "RightFoot", "RightToeBase")}
    torso = {by_name[name] for name in ("Hips", "Spine", "Spine01", "Spine02", "LeftShoulder", "RightShoulder", "LeftArm", "RightArm", "LeftForeArm", "RightForeArm")}
    head = {by_name["Head"], by_name["neck"]}
    hair_support = head | {by_name["LeftShoulder"], by_name["RightShoulder"]}
    head_positions = [position for position, js, ws in zip(positions, joints, weights) if joint_weight(js, ws, head) >= 0.5]
    lows = [min(position[c] for position in head_positions) for c in range(3)]
    highs = [max(position[c] for position in head_positions) for c in range(3)]
    center_x = (lows[0] + highs[0]) / 2.0
    span = [highs[c] - lows[c] for c in range(3)]
    result = []
    for position, js, ws in zip(positions, joints, weights):
        x, y, z = position
        leg_weight = joint_weight(js, ws, legs)
        foot_weight = joint_weight(js, ws, feet)
        torso_weight = joint_weight(js, ws, torso)
        head_weight = joint_weight(js, ws, head)
        hair_support_weight = joint_weight(js, ws, hair_support)
        x_norm = (x - center_x) / (span[0] / 2.0)
        y_norm = (y - lows[1]) / span[1]
        z_norm = (z - lows[2]) / span[2]
        labels = set()
        if y < 0.19 and foot_weight >= 0.32:
            labels.add(3)  # white sneaker
        elif 0.13 < y < 1.01 and leg_weight >= 0.48:
            labels.add(2)  # fitted black jeans
        if 0.88 < y < 1.39 and torso_weight >= 0.52:
            labels.add(1)  # beige top
        hair_geometry = (
            head_weight >= 0.42 and (z < 0.025 or abs(x) > 0.065)
        ) or (
            hair_support_weight >= 0.45 and z < -0.045 and abs(x) < 0.18
        )
        if y > 1.17 and hair_geometry:
            labels.add(4)  # golden-blonde hair
        if head_weight >= 0.5 and z_norm > 0.68 and 0.475 < y_norm < 0.535 and 0.23 < abs(x_norm) < 0.49:
            labels.add(5)  # green iris/eye patch
        result.append(labels)
    return result


def texture_mask(positions, joints, weights, uvs, indices, joint_names):
    labels = semantic_labels(positions, joints, weights, joint_names)
    mask = bytearray(MASK_SIZE * MASK_SIZE)
    priority = (1, 2, 3, 4, 5)
    for offset in range(0, len(indices), 3):
        triangle = [int(indices[offset + i][0]) for i in range(3)]
        chosen = 0
        for label in priority:
            required = 3 if label == 5 else 2
            if sum(label in labels[index] for index in triangle) >= required:
                chosen = label
        if not chosen:
            continue
        points = [
            (float(uvs[index][0]) * (MASK_SIZE - 1), (1.0 - float(uvs[index][1])) * (MASK_SIZE - 1))
            for index in triangle
        ]
        fill_triangle(mask, points, chosen)
    return mask


def recolor_texture(png, mask):
    width, height, rows, filter_types = decode_rgb_png(png)
    if width != 4096 or height != 4096 or width // MASK_SIZE != 4:
        raise SystemExit("Expected the canonical 4096x4096 texture atlas")
    # Reference-derived material palette: beige top, charcoal-black denim, warm-white
    # sneakers, dimensional golden blonde hair and restrained green eyes.
    targets = {
        1: ((184, 151, 113), 58),
        2: ((29, 31, 35), 78),
        3: ((224, 220, 208), 82),
        4: ((191, 142, 74), 30),
        5: ((55, 99, 68), 72),
    }
    changed = {label: 0 for label in targets}
    scale = width // MASK_SIZE
    for y, row in enumerate(rows):
        mask_offset = (y // scale) * MASK_SIZE
        for x in range(width):
            label = mask[mask_offset + x // scale]
            if label not in targets:
                continue
            target, strength = targets[label]
            slot = x * 3
            original = (row[slot], row[slot + 1], row[slot + 2])
            luminance = (54 * original[0] + 183 * original[1] + 19 * original[2]) // 256
            for channel in range(3):
                detailed = max(0, min(255, target[channel] + (luminance - 112) * 3 // 5))
                row[slot + channel] = (original[channel] * (100 - strength) + detailed * strength + 50) // 100
            changed[label] += 1
    return encode_rgb_png(width, height, rows, filter_types), changed


def transform_positions(positions, joints, weights, joint_names):
    by_name = {name: index for index, name in enumerate(joint_names)}
    legs = {by_name[name] for name in ("Hips", "LeftUpLeg", "LeftLeg", "RightUpLeg", "RightLeg")}
    head_joints = {by_name["Head"], by_name["neck"]}
    hair_support = head_joints | {by_name["LeftShoulder"], by_name["RightShoulder"]}
    head_positions = [position for position, js, ws in zip(positions, joints, weights) if joint_weight(js, ws, head_joints) >= 0.5]
    lows = [min(position[c] for position in head_positions) for c in range(3)]
    highs = [max(position[c] for position in head_positions) for c in range(3)]
    center = [(lows[c] + highs[c]) / 2.0 for c in range(3)]
    span = [highs[c] - lows[c] for c in range(3)]
    result, categories = [], []
    for position, js, ws in zip(positions, joints, weights):
        x, y, z = map(float, position)
        original = (x, y, z)
        kinds = set()
        leg_weight = joint_weight(js, ws, legs)
        head_weight = joint_weight(js, ws, head_joints)
        hair_support_weight = joint_weight(js, ws, hair_support)

        waist = smooth_band(y, 0.83, 1.02, 1.20) * min(1.0, leg_weight + 0.35)
        if waist > 0.0 and abs(x) < 0.26:
            x *= 1.0 - 0.075 * waist
            z *= 1.0 - 0.025 * waist
            kinds.add("waist")

        hip = smooth_band(y, 0.56, 0.76, 0.98) * min(1.0, leg_weight / 0.55)
        if hip > 0.0 and abs(x) < 0.25:
            x *= 1.0 + 0.115 * hip
            if z < 0.025:
                z -= 0.020 * hip * (0.35 + 0.65 * min(1.0, max(0.0, -z / 0.12)))
            kinds.add("hips_glute")

        if head_weight >= 0.5:
            xn = (x - center[0]) / (span[0] / 2.0)
            yn = (y - lows[1]) / span[1]
            zn = (z - lows[2]) / span[2]
            if zn >= 0.50:
                oval = smooth_band(yn, 0.00, 0.23, 0.82)
                x = center[0] + (x - center[0]) * (1.0 - 0.075 * oval)
                if yn < 0.28:
                    x = center[0] + (x - center[0]) * 0.94
                    y -= 0.0035 * (1.0 - yn / 0.28)
                if 0.30 < yn < 0.64 and abs(xn) < 0.31:
                    x = center[0] + (x - center[0]) * 0.88
                    if 0.31 < yn < 0.43:
                        z -= 0.0025
                if 0.14 < yn < 0.28 and abs(xn) < 0.46:
                    z += 0.0032 * smooth_band(yn, 0.14, 0.21, 0.28)
                kinds.add("face")

        hair = y > 1.17 and (
            (head_weight >= 0.42 and (z < 0.025 or abs(x) > 0.065))
            or (hair_support_weight >= 0.45 and z < -0.045 and abs(x) < 0.18)
        )
        if hair:
            volume = smooth_band(y, 1.17, 1.43, 1.71)
            side = 1.0 if x >= center[0] else -1.0
            x += side * 0.010 * volume
            if z < 0.03:
                z -= 0.012 * volume
            length = max(0.0, min(1.0, (1.46 - y) / 0.29))
            y -= 0.120 * length * length
            kinds.add("hair")

        result.append((x, y, z))
        categories.append(kinds)
        if not all(math.isfinite(value) for value in result[-1]):
            raise SystemExit("Non-finite v75 position")
        if math.dist(original, result[-1]) > 0.18:
            raise SystemExit("v75 position delta exceeded absolute safety bound")
    return result, categories


parser = argparse.ArgumentParser(description="Generate the guarded Celine v75 character refresh")
parser.add_argument("source_glb", help="immutable canonical source, used for hash verification")
parser.add_argument("v65_glb", help="validated v65 intermediate with facial morph targets")
parser.add_argument("output_glb")
parser.add_argument("--report", default="CELINE_CHARACTER_REFRESH_V75.json")
parser.add_argument("--expected-sha256", default="")
args = parser.parse_args()

if len({os.path.abspath(args.source_glb), os.path.abspath(args.v65_glb), os.path.abspath(args.output_glb)}) != 3:
    raise SystemExit("Source, intermediate and output paths must be distinct")
source_raw = open(args.source_glb, "rb").read()
if hashlib.sha256(source_raw).hexdigest() != SOURCE_SHA256:
    raise SystemExit("Canonical source hash mismatch; refusing visual refresh")
raw, document, binary = load_glb(args.v65_glb)
if hashlib.sha256(raw).hexdigest() != V65_SHA256:
    raise SystemExit("Validated v65 intermediate hash mismatch")

primitive = document["meshes"][0]["primitives"][0]
attributes = primitive["attributes"]
positions = read_accessor(document, binary, attributes["POSITION"])
joints = read_accessor(document, binary, attributes["JOINTS_0"])
weights = read_accessor(document, binary, attributes["WEIGHTS_0"])
uvs = read_accessor(document, binary, attributes["TEXCOORD_0"])
indices = read_accessor(document, binary, primitive["indices"])
joint_names = [document["nodes"][index].get("name") for index in document["skins"][0]["joints"]]

new_positions, categories = transform_positions(positions, joints, weights, joint_names)
new_normals = normals_for(new_positions, indices)
mask = texture_mask(positions, joints, weights, uvs, indices, joint_names)
image = document["images"][0]
old_view = document["bufferViews"][image["bufferView"]]
old_image = binary[old_view.get("byteOffset", 0):old_view.get("byteOffset", 0) + old_view["byteLength"]]
new_image, recolored = recolor_texture(old_image, mask)

binary_out = bytearray(binary)
attributes["POSITION"] = append_vec3(document, binary_out, new_positions)
attributes["NORMAL"] = append_vec3(document, binary_out, new_normals)
if len(new_image) > old_view["byteLength"]:
    raise SystemExit("Refreshed texture no longer fits the guarded canonical image allocation")
image_offset = old_view.get("byteOffset", 0)
binary_out[image_offset:image_offset + len(new_image)] = new_image
binary_out[image_offset + len(new_image):image_offset + old_view["byteLength"]] = b"\x00" * (old_view["byteLength"] - len(new_image))
old_view["byteLength"] = len(new_image)
image["name"] = "celine_v75_master_reference_texture"
document.setdefault("asset", {})["generator"] = "Yahya-AI deterministic Celine v75 character refresh"
document["buffers"][0]["byteLength"] = len(binary_out)

json_bytes = json.dumps(document, separators=(",", ":")).encode("utf-8")
while len(json_bytes) % 4:
    json_bytes += b" "
while len(binary_out) % 4:
    binary_out.append(0)
total = 12 + 8 + len(json_bytes) + 8 + len(binary_out)
output = bytearray(struct.pack("<4sII", b"glTF", 2, total))
output += struct.pack("<II", len(json_bytes), 0x4E4F534A) + json_bytes
output += struct.pack("<II", len(binary_out), 0x004E4942) + binary_out
output_sha = hashlib.sha256(output).hexdigest()
if args.expected_sha256 and output_sha != args.expected_sha256:
    raise SystemExit(f"Generated v75 hash mismatch: expected={args.expected_sha256} actual={output_sha}")

os.makedirs(os.path.dirname(os.path.abspath(args.output_glb)), exist_ok=True)
open(args.output_glb, "wb").write(output)
deltas = [math.dist(a, b) for a, b in zip(positions, new_positions)]
counts = {name: sum(name in category for category in categories) for name in ("face", "hair", "waist", "hips_glute")}
report = {
    "schema": 1,
    "status": "PASS",
    "policy": "deterministic_master_reference_refresh",
    "canonical_source_sha256": SOURCE_SHA256,
    "v65_intermediate_sha256": V65_SHA256,
    "output_sha256": output_sha,
    "vertex_count": len(positions),
    "triangle_count": len(indices) // 3,
    "changed_vertices": sum(delta > 1.0e-9 for delta in deltas),
    "max_position_delta_m": max(deltas),
    "region_vertices": counts,
    "texture_sha256_before": hashlib.sha256(old_image).hexdigest(),
    "texture_sha256_after": hashlib.sha256(new_image).hexdigest(),
    "texture_recolored_pixels": {str(key): value for key, value in recolored.items()},
    "preserved": ["indices", "JOINTS_0", "WEIGHTS_0", "skin", "bones", "animations", "morph target deltas"],
    "references": "docs/celine/reference/v2/REFERENCE_MANIFEST.json",
}
open(args.report, "w", encoding="utf-8").write(json.dumps(report, indent=2) + "\n")
print(json.dumps(report, indent=2))
