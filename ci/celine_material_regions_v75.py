#!/usr/bin/env python3
"""Split the single v75 skinned primitive into reference-colored material regions.

The source mesh, vertex attributes, skin, bones, morph targets and triangle topology stay
unchanged. Only triangle-to-material assignment changes. This avoids repainting the shared
4096 atlas, which exact-head review proved cross-contaminates unrelated body regions.

The Android renderer intentionally normalizes baseColorFactor to white at runtime for legacy
Meshy material repair. Therefore the v75 semantic colors are stored as tiny embedded base-color
textures as well as factors, so the visible reference palette survives that protected runtime path.
"""
import argparse
from collections import Counter
from array import array
import binascii
import hashlib
import json
import math
import os
import struct
import sys
import zlib

COMPONENT = {5121: "B", 5123: "H", 5125: "I", 5126: "f"}
COMPONENTS = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}


def load_glb(path):
    raw = open(path, "rb").read()
    if raw[:4] != b"glTF":
        raise SystemExit("input is not GLB")
    off, doc, binary = 12, None, b""
    while off < len(raw):
        length, kind = struct.unpack_from("<II", raw, off); off += 8
        chunk = raw[off:off + length]; off += length
        if kind == 0x4E4F534A:
            doc = json.loads(chunk.decode("utf-8").rstrip("\x00 "))
        elif kind == 0x004E4942:
            binary = chunk
    if doc is None:
        raise SystemExit("GLB JSON missing")
    return raw, doc, binary


def read_accessor(doc, binary, idx):
    acc = doc["accessors"][idx]
    view = doc["bufferViews"][acc["bufferView"]]
    fmt = COMPONENT[acc["componentType"]]
    n = COMPONENTS[acc["type"]]
    item = struct.calcsize("<" + fmt) * n
    stride = view.get("byteStride", item)
    start = view.get("byteOffset", 0) + acc.get("byteOffset", 0)
    unpack = struct.Struct("<" + fmt * n).unpack_from
    return [unpack(binary, start + i * stride) for i in range(acc["count"])]


def joint_weight(js, ws, wanted):
    return sum(float(w) for j, w in zip(js, ws) if int(j) in wanted)


def append_indices(doc, binary_out, values, component_type):
    while len(binary_out) % 4:
        binary_out.append(0)
    fmt = COMPONENT[component_type]
    arr = array(fmt, values)
    if sys.byteorder != "little":
        arr.byteswap()
    data = arr.tobytes()
    offset = len(binary_out)
    binary_out.extend(data)
    view_index = len(doc.setdefault("bufferViews", []))
    doc["bufferViews"].append({"buffer": 0, "byteOffset": offset, "byteLength": len(data), "target": 34963})
    accessor_index = len(doc.setdefault("accessors", []))
    doc["accessors"].append({
        "bufferView": view_index,
        "componentType": component_type,
        "count": len(values),
        "type": "SCALAR",
        "min": [min(values)],
        "max": [max(values)],
    })
    return accessor_index


def png_chunk(kind, payload):
    return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", binascii.crc32(kind + payload) & 0xFFFFFFFF)


def solid_png(rgb):
    pixel = bytes(max(0, min(255, int(round(c * 255.0)))) for c in rgb)
    ihdr = struct.pack(">IIBBBBB", 1, 1, 8, 2, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + png_chunk(b"IHDR", ihdr)
            + png_chunk(b"IDAT", zlib.compress(b"\x00" + pixel, 9)) + png_chunk(b"IEND", b""))


def append_solid_texture(doc, binary_out, rgb, name):
    while len(binary_out) % 4:
        binary_out.append(0)
    png = solid_png(rgb)
    offset = len(binary_out)
    binary_out.extend(png)
    view_index = len(doc.setdefault("bufferViews", []))
    doc["bufferViews"].append({"buffer": 0, "byteOffset": offset, "byteLength": len(png)})
    image_index = len(doc.setdefault("images", []))
    doc["images"].append({"name": name + "_image", "bufferView": view_index, "mimeType": "image/png"})
    texture_index = len(doc.setdefault("textures", []))
    doc["textures"].append({"name": name + "_texture", "source": image_index})
    return texture_index


def material(name, rgb, roughness, texture_index):
    return {
        "name": name,
        "doubleSided": True,
        "pbrMetallicRoughness": {
            "baseColorFactor": [rgb[0], rgb[1], rgb[2], 1.0],
            "baseColorTexture": {"index": texture_index},
            "metallicFactor": 0.0,
            "roughnessFactor": roughness,
        },
        "emissiveFactor": [0.0, 0.0, 0.0],
    }


def choose_vertex_label(position, js, ws, by_name):
    x, y, z = map(float, position)
    legs = {by_name[n] for n in ("Hips", "LeftUpLeg", "LeftLeg", "RightUpLeg", "RightLeg") if n in by_name}
    feet = {by_name[n] for n in ("LeftFoot", "LeftToeBase", "RightFoot", "RightToeBase") if n in by_name}
    torso = {by_name[n] for n in ("Hips", "Spine", "Spine01", "Spine02") if n in by_name}
    arms = {by_name[n] for n in ("LeftShoulder", "RightShoulder", "LeftArm", "RightArm", "LeftForeArm", "RightForeArm") if n in by_name}
    hands = {by_name[n] for n in ("LeftHand", "RightHand") if n in by_name}
    head = {by_name[n] for n in ("Head", "neck") if n in by_name}
    shoulders = {by_name[n] for n in ("LeftShoulder", "RightShoulder") if n in by_name}

    if y < 0.24 and joint_weight(js, ws, feet) >= 0.22:
        return "shoes"
    if 0.13 < y < 1.03 and joint_weight(js, ws, legs) >= 0.42:
        return "jeans"
    arm_w = joint_weight(js, ws, arms)
    hand_w = joint_weight(js, ws, hands)
    torso_w = joint_weight(js, ws, torso)
    if 0.86 < y < 1.40 and (torso_w >= 0.48 or (arm_w >= 0.46 and hand_w < 0.25)):
        return "top"
    head_w = joint_weight(js, ws, head)
    shoulder_w = joint_weight(js, ws, shoulders)
    hair = y > 1.16 and ((head_w >= 0.40 and (z < 0.028 or abs(x) > 0.060)) or (shoulder_w >= 0.42 and z < -0.040 and abs(x) < 0.20))
    if hair:
        return "hair"
    return "skin"


def choose_triangle(labels):
    counts = Counter(labels)
    for label in ("shoes", "jeans", "top", "hair"):
        if counts[label] >= 2:
            return label
    return "skin"


def write_glb(doc, binary):
    doc["buffers"][0]["byteLength"] = len(binary)
    json_bytes = json.dumps(doc, separators=(",", ":")).encode("utf-8")
    while len(json_bytes) % 4:
        json_bytes += b" "
    while len(binary) % 4:
        binary.append(0)
    total = 12 + 8 + len(json_bytes) + 8 + len(binary)
    return (b"glTF" + struct.pack("<II", 2, total) + struct.pack("<II", len(json_bytes), 0x4E4F534A) + json_bytes
            + struct.pack("<II", len(binary), 0x004E4942) + bytes(binary))


parser = argparse.ArgumentParser()
parser.add_argument("input_glb")
parser.add_argument("output_glb")
parser.add_argument("--report", default="CELINE_V75_MATERIAL_REGIONS.json")
args = parser.parse_args()

raw, doc, binary = load_glb(args.input_glb)
if len(doc.get("meshes", [])) != 1 or len(doc["meshes"][0].get("primitives", [])) != 1:
    raise SystemExit("expected one monolithic v75 primitive")
primitive = doc["meshes"][0]["primitives"][0]
attrs = primitive["attributes"]
positions = read_accessor(doc, binary, attrs["POSITION"])
joints = read_accessor(doc, binary, attrs["JOINTS_0"])
weights = read_accessor(doc, binary, attrs["WEIGHTS_0"])
indices = [int(row[0]) for row in read_accessor(doc, binary, primitive["indices"])]
index_component = doc["accessors"][primitive["indices"]]["componentType"]
if index_component not in (5121, 5123, 5125):
    raise SystemExit("unsupported index component type")
if len(indices) % 3:
    raise SystemExit("triangle index stream is not divisible by 3")

joint_names = [doc["nodes"][i].get("name", "") for i in doc["skins"][0]["joints"]]
by_name = {name: i for i, name in enumerate(joint_names)}
labels = [choose_vertex_label(p, js, ws, by_name) for p, js, ws in zip(positions, joints, weights)]
groups = {name: [] for name in ("skin", "top", "jeans", "shoes", "hair")}
for i in range(0, len(indices), 3):
    tri = indices[i:i + 3]
    label = choose_triangle([labels[j] for j in tri])
    groups[label].extend(tri)

if sum(len(v) for v in groups.values()) != len(indices):
    raise SystemExit("material split lost topology")
for required in ("skin", "top", "jeans", "shoes", "hair"):
    if len(groups[required]) < 60:
        raise SystemExit(f"material region too small: {required}={len(groups[required]) // 3} triangles")

palette = {
    "top": (0.76, 0.64, 0.51),
    "jeans": (0.018, 0.020, 0.024),
    "shoes": (0.98, 0.97, 0.94),
    "hair": (0.88, 0.70, 0.46),
}
binary_out = bytearray(binary)
texture_index = {
    label: append_solid_texture(doc, binary_out, rgb, "CelineV75_" + label)
    for label, rgb in palette.items()
}
materials = list(doc.get("materials", []))
material_index = {
    "skin": primitive.get("material", 0),
    "top": len(materials),
    "jeans": len(materials) + 1,
    "shoes": len(materials) + 2,
    "hair": len(materials) + 3,
}
materials.extend([
    material("CelineV75_BeigeRibbedTop", palette["top"], 0.78, texture_index["top"]),
    material("CelineV75_FittedBlackJeans", palette["jeans"], 0.72, texture_index["jeans"]),
    material("CelineV75_WhiteSneakers", palette["shoes"], 0.66, texture_index["shoes"]),
    material("CelineV75_GoldenBlondeHair", palette["hair"], 0.72, texture_index["hair"]),
])
doc["materials"] = materials
new_primitives = []
for label in ("skin", "top", "jeans", "shoes", "hair"):
    values = groups[label]
    p = {k: v for k, v in primitive.items() if k not in ("indices", "material")}
    p["indices"] = append_indices(doc, binary_out, values, index_component)
    p["material"] = material_index[label]
    new_primitives.append(p)
doc["meshes"][0]["primitives"] = new_primitives
doc.setdefault("asset", {})["generator"] = "Yahya-AI Celine v75 semantic material-region split"
output = write_glb(doc, binary_out)
os.makedirs(os.path.dirname(os.path.abspath(args.output_glb)), exist_ok=True)
open(args.output_glb, "wb").write(output)
report = {
    "schema": 1,
    "status": "PASS",
    "policy": "shared_uv_safe_triangle_material_split_with_runtime_stable_solid_textures",
    "input_sha256": hashlib.sha256(raw).hexdigest(),
    "output_sha256": hashlib.sha256(output).hexdigest(),
    "triangle_count": len(indices) // 3,
    "region_triangles": {k: len(v) // 3 for k, v in groups.items()},
    "materials": ["canonical skin/face atlas", "beige ribbed top", "fitted black jeans", "white sneakers", "golden blonde hair"],
    "runtime_color_guard": "semantic colors use embedded 1x1 baseColorTexture so legacy baseColorFactor normalization cannot erase them",
    "preserved": ["positions", "normals", "uvs", "joints", "weights", "morph targets", "skin", "bones", "animations", "triangle topology"],
}
os.makedirs(os.path.dirname(os.path.abspath(args.report)), exist_ok=True)
open(args.report, "w", encoding="utf-8").write(json.dumps(report, indent=2) + "\n")
print(json.dumps(report, indent=2))
