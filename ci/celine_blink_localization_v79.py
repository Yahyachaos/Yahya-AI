#!/usr/bin/env python3
"""v79: localize Celine blink to the canonical skin/face surface only."""

import argparse
import hashlib
import json
import os
import struct

TARGET_NAMES = [
    "BlinkLeft", "BlinkRight", "BlinkBoth", "JawOpen",
    "RoundedVowelProof", "SpreadVowelProof", "BilabialPress", "Labiodental",
    "Smile", "Thoughtful", "Surprised", "GazeLeft", "GazeRight", "GazeUp", "GazeDown",
]
EXPECTED_V76_SHA256 = "46828b88dc7917def64881c6bc348b790a2bab445401e0ba3fac240327253923"


def load_glb(path):
    raw = bytearray(open(path, "rb").read())
    if raw[:4] != b"glTF":
        raise SystemExit("Input is not a GLB")
    offset = 12
    document = None
    binary_start = None
    binary_length = None
    while offset < len(raw):
        length, chunk_type = struct.unpack_from("<II", raw, offset)
        data_start = offset + 8
        if chunk_type == 0x4E4F534A:
            document = json.loads(bytes(raw[data_start:data_start + length]).decode("utf-8").rstrip("\x00 "))
        elif chunk_type == 0x004E4942:
            binary_start = data_start
            binary_length = length
        offset = data_start + length
    if document is None or binary_start is None:
        raise SystemExit("GLB JSON/BIN chunk missing")
    return raw, document, binary_start, binary_length


def accessor_layout(document, accessor_index, binary_start):
    accessor = document["accessors"][accessor_index]
    if accessor.get("componentType") != 5126 or accessor.get("type") != "VEC3":
        raise SystemExit("Blink morph accessor must be float32 VEC3")
    view = document["bufferViews"][accessor["bufferView"]]
    stride = view.get("byteStride", 12)
    start = binary_start + view.get("byteOffset", 0) + accessor.get("byteOffset", 0)
    return start, stride, accessor["count"]


def read_indices(raw, document, accessor_index, binary_start):
    accessor = document["accessors"][accessor_index]
    if accessor.get("type") != "SCALAR" or accessor.get("componentType") not in (5121, 5123, 5125):
        raise SystemExit("Skin primitive indices must be unsigned scalar values")
    view = document["bufferViews"][accessor["bufferView"]]
    formats = {5121: "B", 5123: "H", 5125: "I"}
    fmt = formats[accessor["componentType"]]
    size = struct.calcsize("<" + fmt)
    stride = view.get("byteStride", size)
    start = binary_start + view.get("byteOffset", 0) + accessor.get("byteOffset", 0)
    return {
        struct.unpack_from("<" + fmt, raw, start + index * stride)[0]
        for index in range(accessor["count"])
    }


def read_vec3(raw, start, stride, index):
    return struct.unpack_from("<fff", raw, start + index * stride)


def write_vec3(raw, start, stride, index, value):
    struct.pack_into("<fff", raw, start + index * stride, *value)


parser = argparse.ArgumentParser(description="Localize v76 Celine blink to eyelids for v79")
parser.add_argument("input_glb")
parser.add_argument("output_glb")
parser.add_argument("--report", required=True)
parser.add_argument("--expected-input-sha256", default=EXPECTED_V76_SHA256)
args = parser.parse_args()

raw, document, binary_start, _ = load_glb(args.input_glb)
input_sha = hashlib.sha256(raw).hexdigest()
if args.expected_input_sha256 and input_sha != args.expected_input_sha256:
    raise SystemExit("Exact v76 input hash mismatch: expected=" + args.expected_input_sha256 + " actual=" + input_sha)
if document.get("asset", {}).get("generator") != "Yahya-AI Celine v76 final-geometry facial rig":
    raise SystemExit("Input is not the guarded v76 facial-rig candidate")
mesh = document.get("meshes", [None])[0]
if mesh is None or mesh.get("extras", {}).get("targetNames") != TARGET_NAMES:
    raise SystemExit("Exact 15-target facial contract missing")
primitives = mesh.get("primitives", [])
if not primitives or len(primitives[0].get("targets", [])) != len(TARGET_NAMES):
    raise SystemExit("Morph bindings missing")
skin_primitive = primitives[0]
skin_material = document.get("materials", [])[skin_primitive.get("material", -1)]
if skin_material.get("name") != "Material_1" or "indices" not in skin_primitive:
    raise SystemExit("Deterministic v75 canonical skin/face primitive missing")
skin_vertices = read_indices(raw, document, skin_primitive["indices"], binary_start)

bindings = primitives[0]["targets"]
layouts = []
for target_index in range(3):
    binding = bindings[target_index]
    if set(binding) != {"POSITION"}:
        raise SystemExit("Blink target contains unexpected attributes")
    layouts.append(accessor_layout(document, binding["POSITION"], binary_start))

left_start, left_stride, count = layouts[0]
right_start, right_stride, right_count = layouts[1]
both_start, both_stride, both_count = layouts[2]
if right_count != count or both_count != count:
    raise SystemExit("Blink target vertex-count mismatch")

removed_left = 0
removed_right = 0
kept_left_upper = 0
kept_right_upper = 0
kept_left_lower = 0
kept_right_lower = 0
for i in range(count):
    left = read_vec3(raw, left_start, left_stride, i)
    right = read_vec3(raw, right_start, right_stride, i)

    # v76 selected a broad head-weighted box. On the final material-split mesh most of that
    # box belongs to hair islands, while the actual textured eyelids belong to primitive 0's
    # canonical skin/face atlas. Keep the original balanced upper/lower closure only on that
    # face surface and remove every non-skin contribution.
    if i not in skin_vertices and max(abs(v) for v in left) > 1.0e-9:
        left = (0.0, 0.0, 0.0)
        removed_left += 1
    elif left[1] < -1.0e-9:
        kept_left_upper += 1
    elif left[1] > 1.0e-9:
        kept_left_lower += 1
    if i not in skin_vertices and max(abs(v) for v in right) > 1.0e-9:
        right = (0.0, 0.0, 0.0)
        removed_right += 1
    elif right[1] < -1.0e-9:
        kept_right_upper += 1
    elif right[1] > 1.0e-9:
        kept_right_lower += 1

    write_vec3(raw, left_start, left_stride, i, left)
    write_vec3(raw, right_start, right_stride, i, right)
    write_vec3(raw, both_start, both_stride, i,
               (left[0] + right[0], left[1] + right[1], left[2] + right[2]))

if removed_left == 0 or removed_right == 0:
    raise SystemExit("No non-skin blink contribution found to remove")
if min(kept_left_upper, kept_right_upper, kept_left_lower, kept_right_lower) == 0:
    raise SystemExit("Repair must retain upper and lower eyelid closure on both sides")

output_sha = hashlib.sha256(raw).hexdigest()
os.makedirs(os.path.dirname(os.path.abspath(args.output_glb)), exist_ok=True)
open(args.output_glb, "wb").write(raw)
report = {
    "schema": 1,
    "status": "PASS",
    "policy": "v79_canonical_skin_face_blink_localization",
    "input_sha256": input_sha,
    "output_sha256": output_sha,
    "removed_non_skin_vertices": {"left": removed_left, "right": removed_right},
    "kept_skin_face_vertices": {
        "left": {"upper": kept_left_upper, "lower": kept_left_lower},
        "right": {"upper": kept_right_upper, "lower": kept_right_lower},
    },
    "preserved": ["neutral geometry", "rig", "materials", "textures", "all non-blink morph targets"],
    "reason": "v76 head-space eyelid boxes selected mostly hair-material vertices; v79 keeps balanced closure only on the canonical skin/face primitive and recomposes BlinkBoth",
}
os.makedirs(os.path.dirname(os.path.abspath(args.report)), exist_ok=True)
open(args.report, "w", encoding="utf-8").write(json.dumps(report, indent=2) + "\n")
print(json.dumps(report, indent=2))
