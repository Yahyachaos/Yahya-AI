#!/usr/bin/env python3
"""v79: localize Celine blink to upper eyelids without touching other morph channels."""

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
kept_left = 0
kept_right = 0
for i in range(count):
    left = read_vec3(raw, left_start, left_stride, i)
    right = read_vec3(raw, right_start, right_stride, i)

    # v76 lower-lid vertices move upward (positive Y). The generated lower-lid box overlaps
    # the defined cheek band, producing the user-visible "cheek blink". v79 deliberately
    # removes the lower-lid contribution and keeps the upper-lid downward closure untouched.
    if left[1] > 1.0e-9:
        left = (0.0, 0.0, 0.0)
        removed_left += 1
    elif max(abs(v) for v in left) > 1.0e-9:
        kept_left += 1
    if right[1] > 1.0e-9:
        right = (0.0, 0.0, 0.0)
        removed_right += 1
    elif max(abs(v) for v in right) > 1.0e-9:
        kept_right += 1

    write_vec3(raw, left_start, left_stride, i, left)
    write_vec3(raw, right_start, right_stride, i, right)
    write_vec3(raw, both_start, both_stride, i,
               (left[0] + right[0], left[1] + right[1], left[2] + right[2]))

if removed_left == 0 or removed_right == 0:
    raise SystemExit("No lower-lid contribution found to remove")
if kept_left == 0 or kept_right == 0:
    raise SystemExit("Repair would remove the entire blink")

output_sha = hashlib.sha256(raw).hexdigest()
os.makedirs(os.path.dirname(os.path.abspath(args.output_glb)), exist_ok=True)
open(args.output_glb, "wb").write(raw)
report = {
    "schema": 1,
    "status": "PASS",
    "policy": "v79_upper_eyelid_only_blink_localization",
    "input_sha256": input_sha,
    "output_sha256": output_sha,
    "removed_positive_y_vertices": {"left": removed_left, "right": removed_right},
    "kept_upper_lid_vertices": {"left": kept_left, "right": kept_right},
    "preserved": ["neutral geometry", "rig", "materials", "textures", "all non-blink morph targets"],
    "reason": "v76 lower-eyelid selection overlapped the cheek band; v79 removes upward lower-lid motion and recomposes BlinkBoth",
}
os.makedirs(os.path.dirname(os.path.abspath(args.report)), exist_ok=True)
open(args.report, "w", encoding="utf-8").write(json.dumps(report, indent=2) + "\n")
print(json.dumps(report, indent=2))
