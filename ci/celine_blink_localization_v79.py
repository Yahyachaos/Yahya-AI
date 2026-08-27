#!/usr/bin/env python3
"""v79: localize Celine blink to the canonical visible skin/eyelid surface only."""

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

# Exact final-v75/v76 canonical face coordinates. Proof #30 showed that merely retaining
# the old head-normalized v76 skin vertices leaves the visible eyes open because those
# vertices sit too low on the final face. These bounds target only the actual front eyelid
# shell on Material_1. They intentionally exclude cheek, brow, nose and hair surfaces.
EYELID_Z_MIN = 0.075
EYELID_CLOSURE_Y = 1.558
EYELID_BOUNDS = {
    "left": {
        "x": (-0.068, -0.015),
        "upper_y": (1.558, 1.579),
        "lower_y": (1.538, 1.558),
    },
    "right": {
        "x": (0.018, 0.071),
        "upper_y": (1.558, 1.579),
        "lower_y": (1.538, 1.558),
    },
}


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
        raise SystemExit("Expected float32 VEC3 accessor")
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


def in_range(value, bounds, upper_inclusive=True):
    low, high = bounds
    return low <= value <= high if upper_inclusive else low <= value < high


def eyelid_delta(position, side, layer):
    bounds = EYELID_BOUNDS[side]
    x, y, z = position
    if not in_range(x, bounds["x"]) or z < EYELID_Z_MIN:
        return None
    y_bounds = bounds[layer + "_y"]
    if not in_range(y, y_bounds, upper_inclusive=(layer == "upper")):
        return None
    # Close toward one physically narrow seam. At diagnostic weight 0.96 this leaves a tiny
    # natural residual rather than overshooting/crossing the lids. No X movement and no cheek
    # pull are introduced; depth is left untouched to preserve the canonical face silhouette.
    return (0.0, EYELID_CLOSURE_Y - y, 0.0)


parser = argparse.ArgumentParser(description="Localize v76 Celine blink to visible eyelids for v79")
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

position_binding = skin_primitive.get("attributes", {}).get("POSITION")
if position_binding is None:
    raise SystemExit("Canonical neutral POSITION binding missing")
position_start, position_stride, position_count = accessor_layout(document, position_binding, binary_start)

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
if right_count != count or both_count != count or position_count != count:
    raise SystemExit("Blink/neutral vertex-count mismatch")

removed_old = {"left": 0, "right": 0}
affected = {
    "left": {"upper": 0, "lower": 0},
    "right": {"upper": 0, "lower": 0},
}
max_abs_y_delta = 0.0
for i in range(count):
    old_left = read_vec3(raw, left_start, left_stride, i)
    old_right = read_vec3(raw, right_start, right_stride, i)
    if max(abs(v) for v in old_left) > 1.0e-9:
        removed_old["left"] += 1
    if max(abs(v) for v in old_right) > 1.0e-9:
        removed_old["right"] += 1

    left = (0.0, 0.0, 0.0)
    right = (0.0, 0.0, 0.0)
    if i in skin_vertices:
        position = read_vec3(raw, position_start, position_stride, i)
        for side in ("left", "right"):
            for layer in ("upper", "lower"):
                delta = eyelid_delta(position, side, layer)
                if delta is None:
                    continue
                if side == "left":
                    left = delta
                else:
                    right = delta
                affected[side][layer] += 1
                max_abs_y_delta = max(max_abs_y_delta, abs(delta[1]))
                break

    write_vec3(raw, left_start, left_stride, i, left)
    write_vec3(raw, right_start, right_stride, i, right)
    write_vec3(raw, both_start, both_stride, i,
               (left[0] + right[0], left[1] + right[1], left[2] + right[2]))

if min(affected["left"].values()) < 10 or min(affected["right"].values()) < 10:
    raise SystemExit("Visible eyelid surface selection is unexpectedly sparse: " + json.dumps(affected))
if max_abs_y_delta <= 0.0 or max_abs_y_delta > 0.025:
    raise SystemExit("Visible eyelid closure displacement out of bounded range: %.6f" % max_abs_y_delta)

output_sha = hashlib.sha256(raw).hexdigest()
os.makedirs(os.path.dirname(os.path.abspath(args.output_glb)), exist_ok=True)
open(args.output_glb, "wb").write(raw)
report = {
    "schema": 2,
    "status": "PASS",
    "policy": "v79_visible_canonical_skin_eyelid_closure",
    "input_sha256": input_sha,
    "output_sha256": output_sha,
    "removed_old_broad_blink_vertices": removed_old,
    "visible_eyelid_vertices": affected,
    "closure_y_m": EYELID_CLOSURE_Y,
    "front_depth_min_m": EYELID_Z_MIN,
    "max_abs_y_delta_m": max_abs_y_delta,
    "preserved": ["neutral geometry", "rig", "materials", "textures", "all non-blink morph targets"],
    "reason": "Proof #30 confirmed the old head-normalized v76 skin subset sits below the visible eyelid seam; v79 now rebuilds only the three blink POSITION payloads on the canonical front skin eyelid shell and recomposes BlinkBoth",
}
os.makedirs(os.path.dirname(os.path.abspath(args.report)), exist_ok=True)
open(args.report, "w", encoding="utf-8").write(json.dumps(report, indent=2) + "\n")
print(json.dumps(report, indent=2))