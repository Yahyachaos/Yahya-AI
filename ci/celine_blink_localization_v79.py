#!/usr/bin/env python3
"""v80 Block 7: localize Celine blink to visible eyelids without collapsing both lid shells."""

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
EYELID_Z_MIN = 0.075
EYELID_BOUNDS = {
    "left": {"x": (-0.068, -0.015), "upper_y": (1.558, 1.579), "lower_y": (1.538, 1.558)},
    "right": {"x": (0.018, 0.071), "upper_y": (1.558, 1.579), "lower_y": (1.538, 1.558)},
}
# v79 collapsed every selected upper/lower vertex toward exactly y=1.558. On a real device that
# can become a folded/coplanar dark band at near-full closure. Keep the visible-eyelid localization,
# but preserve each shell's topology by translating the selected upper/lower strips instead.
UPPER_CLOSE_FRACTION = 0.74
LOWER_CLOSE_FRACTION = 0.22
UPPER_DEPTH_FRACTION = 0.035
LOWER_DEPTH_FRACTION = 0.020
MIN_RESIDUAL_MEAN_GAP_M = 0.00035
MAX_RESIDUAL_MEAN_GAP_M = 0.00250


def load_glb(path):
    raw = bytearray(open(path, "rb").read())
    if raw[:4] != b"glTF":
        raise SystemExit("Input is not a GLB")
    offset = 12
    document = None
    binary_start = None
    while offset < len(raw):
        length, chunk_type = struct.unpack_from("<II", raw, offset)
        data_start = offset + 8
        if chunk_type == 0x4E4F534A:
            document = json.loads(bytes(raw[data_start:data_start + length]).decode("utf-8").rstrip("\x00 "))
        elif chunk_type == 0x004E4942:
            binary_start = data_start
        offset = data_start + length
    if document is None or binary_start is None:
        raise SystemExit("GLB JSON/BIN chunk missing")
    return raw, document, binary_start


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
    fmt = {5121: "B", 5123: "H", 5125: "I"}[accessor["componentType"]]
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


def selected_layer(position, side, layer):
    x, y, z = position
    bounds = EYELID_BOUNDS[side]
    return (
        in_range(x, bounds["x"])
        and z >= EYELID_Z_MIN
        and in_range(y, bounds[layer + "_y"], upper_inclusive=(layer == "upper"))
    )


parser = argparse.ArgumentParser(description="Build topology-preserving visible-eyelid blink for v80 Block 7")
parser.add_argument("input_glb")
parser.add_argument("output_glb")
parser.add_argument("--report", required=True)
parser.add_argument("--expected-input-sha256", default=EXPECTED_V76_SHA256)
args = parser.parse_args()

raw, document, binary_start = load_glb(args.input_glb)
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
positions = [read_vec3(raw, position_start, position_stride, i) for i in range(position_count)]

bindings = skin_primitive["targets"]
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

selection = {side: {layer: [] for layer in ("upper", "lower")} for side in ("left", "right")}
for i, position in enumerate(positions):
    if i not in skin_vertices:
        continue
    for side in ("left", "right"):
        for layer in ("upper", "lower"):
            if selected_layer(position, side, layer):
                selection[side][layer].append(i)
                break

for side in ("left", "right"):
    if min(len(selection[side][layer]) for layer in ("upper", "lower")) < 10:
        raise SystemExit("Visible eyelid surface selection unexpectedly sparse: " + json.dumps({
            s: {l: len(selection[s][l]) for l in ("upper", "lower")} for s in selection
        }))

gaps = {}
deltas = {}
for side in ("left", "right"):
    upper_mean = sum(positions[i][1] for i in selection[side]["upper"]) / len(selection[side]["upper"])
    lower_mean = sum(positions[i][1] for i in selection[side]["lower"]) / len(selection[side]["lower"])
    gap = upper_mean - lower_mean
    if not (0.008 <= gap <= 0.025):
        raise SystemExit("Visible eyelid mean gap outside guarded range for %s: %.6f" % (side, gap))
    gaps[side] = gap
    deltas[side] = {
        "upper": (0.0, -gap * UPPER_CLOSE_FRACTION, -gap * UPPER_DEPTH_FRACTION),
        "lower": (0.0, gap * LOWER_CLOSE_FRACTION, -gap * LOWER_DEPTH_FRACTION),
    }
    residual = gap * (1.0 - UPPER_CLOSE_FRACTION - LOWER_CLOSE_FRACTION)
    if not (MIN_RESIDUAL_MEAN_GAP_M <= residual <= MAX_RESIDUAL_MEAN_GAP_M):
        raise SystemExit("Residual full-blink mean gap outside guard for %s: %.6f" % (side, residual))

removed_old = {"left": 0, "right": 0}
for i in range(count):
    old_left = read_vec3(raw, left_start, left_stride, i)
    old_right = read_vec3(raw, right_start, right_stride, i)
    if max(abs(v) for v in old_left) > 1.0e-9:
        removed_old["left"] += 1
    if max(abs(v) for v in old_right) > 1.0e-9:
        removed_old["right"] += 1

    left = (0.0, 0.0, 0.0)
    right = (0.0, 0.0, 0.0)
    if i in selection["left"]["upper"]:
        left = deltas["left"]["upper"]
    elif i in selection["left"]["lower"]:
        left = deltas["left"]["lower"]
    if i in selection["right"]["upper"]:
        right = deltas["right"]["upper"]
    elif i in selection["right"]["lower"]:
        right = deltas["right"]["lower"]

    write_vec3(raw, left_start, left_stride, i, left)
    write_vec3(raw, right_start, right_stride, i, right)
    write_vec3(raw, both_start, both_stride, i,
               (left[0] + right[0], left[1] + right[1], left[2] + right[2]))

max_abs_y_delta = max(abs(deltas[s][l][1]) for s in deltas for l in deltas[s])
if max_abs_y_delta <= 0.0 or max_abs_y_delta > 0.025:
    raise SystemExit("Topology-preserving eyelid displacement out of bounded range: %.6f" % max_abs_y_delta)

output_sha = hashlib.sha256(raw).hexdigest()
os.makedirs(os.path.dirname(os.path.abspath(args.output_glb)), exist_ok=True)
open(args.output_glb, "wb").write(raw)
report = {
    "schema": 3,
    "status": "PASS",
    "policy": "v80_visible_eyelid_topology_preserving_closure",
    "input_sha256": input_sha,
    "output_sha256": output_sha,
    "removed_old_blink_vertices": removed_old,
    "visible_eyelid_vertices": {
        side: {layer: len(selection[side][layer]) for layer in ("upper", "lower")}
        for side in ("left", "right")
    },
    "neutral_mean_gap_m": gaps,
    "full_weight_residual_mean_gap_m": {
        side: gaps[side] * (1.0 - UPPER_CLOSE_FRACTION - LOWER_CLOSE_FRACTION)
        for side in gaps
    },
    "upper_close_fraction": UPPER_CLOSE_FRACTION,
    "lower_close_fraction": LOWER_CLOSE_FRACTION,
    "upper_depth_fraction": UPPER_DEPTH_FRACTION,
    "lower_depth_fraction": LOWER_DEPTH_FRACTION,
    "front_depth_min_m": EYELID_Z_MIN,
    "max_abs_y_delta_m": max_abs_y_delta,
    "preserved": ["neutral geometry", "rig", "materials", "textures", "all non-blink morph targets"],
    "reason": "Block 7 replaces v79's single-seam upper/lower collapse with localized topology-preserving lid-strip motion so near-full blinks do not create a folded/coplanar dark band.",
}
os.makedirs(os.path.dirname(os.path.abspath(args.report)), exist_ok=True)
open(args.report, "w", encoding="utf-8").write(json.dumps(report, indent=2) + "\n")
print(json.dumps(report, indent=2))
