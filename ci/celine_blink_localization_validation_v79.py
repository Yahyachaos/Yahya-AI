#!/usr/bin/env python3
"""Validate v80 Block-7 topology-preserving visible-eyelid blink localization."""

import argparse
import hashlib
import json
import math
import os
import struct

TARGET_NAMES = [
    "BlinkLeft", "BlinkRight", "BlinkBoth", "JawOpen",
    "RoundedVowelProof", "SpreadVowelProof", "BilabialPress", "Labiodental",
    "Smile", "Thoughtful", "Surprised", "GazeLeft", "GazeRight", "GazeUp", "GazeDown",
]
EYELID_Z_MIN = 0.075
EYELID_BOUNDS = {
    "left": {"x": (-0.068, -0.015), "upper_y": (1.558, 1.579), "lower_y": (1.538, 1.558)},
    "right": {"x": (0.018, 0.071), "upper_y": (1.558, 1.579), "lower_y": (1.538, 1.558)},
}
UPPER_CLOSE_FRACTION = 0.74
LOWER_CLOSE_FRACTION = 0.22
UPPER_DEPTH_FRACTION = 0.035
LOWER_DEPTH_FRACTION = 0.020
MIN_RESIDUAL_MEAN_GAP_M = 0.00035
MAX_RESIDUAL_MEAN_GAP_M = 0.00250


def fail(message):
    raise SystemExit("FAIL " + message)


def load_glb(path):
    raw = open(path, "rb").read()
    if raw[:4] != b"glTF": fail(path + " is not a GLB")
    offset = 12
    document = None
    binary = None
    while offset < len(raw):
        length, chunk_type = struct.unpack_from("<II", raw, offset)
        start = offset + 8
        chunk = raw[start:start + length]
        if chunk_type == 0x4E4F534A:
            document = json.loads(chunk.decode("utf-8").rstrip("\x00 "))
        elif chunk_type == 0x004E4942:
            binary = chunk
        offset = start + length
    if document is None or binary is None: fail("GLB JSON/BIN chunk missing")
    return raw, document, binary


def accessor_bytes(document, binary, accessor_index):
    accessor = document["accessors"][accessor_index]
    view = document["bufferViews"][accessor["bufferView"]]
    if accessor.get("componentType") != 5126 or accessor.get("type") != "VEC3":
        fail("morph/position accessor must be float32 VEC3")
    stride = view.get("byteStride", 12)
    start = view.get("byteOffset", 0) + accessor.get("byteOffset", 0)
    return b"".join(binary[start + i * stride:start + i * stride + 12] for i in range(accessor["count"]))


def vecs(document, binary, accessor_index):
    return list(struct.iter_unpack("<fff", accessor_bytes(document, binary, accessor_index)))


def indices(document, binary, accessor_index):
    accessor = document["accessors"][accessor_index]
    if accessor.get("type") != "SCALAR" or accessor.get("componentType") not in (5121, 5123, 5125):
        fail("skin primitive indices must be unsigned scalar values")
    view = document["bufferViews"][accessor["bufferView"]]
    fmt = {5121: "B", 5123: "H", 5125: "I"}[accessor["componentType"]]
    size = struct.calcsize("<" + fmt)
    stride = view.get("byteStride", size)
    start = view.get("byteOffset", 0) + accessor.get("byteOffset", 0)
    return {
        struct.unpack_from("<" + fmt, binary, start + i * stride)[0]
        for i in range(accessor["count"])
    }


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


def close_vec(a, b, tolerance=2.0e-6):
    return all(abs(float(x) - float(y)) <= tolerance for x, y in zip(a, b))


parser = argparse.ArgumentParser(description="Validate v80 topology-preserving visible-eyelid blink")
parser.add_argument("v76_glb")
parser.add_argument("v80_glb")
parser.add_argument("--report", required=True)
args = parser.parse_args()

before_raw, before_doc, before_bin = load_glb(args.v76_glb)
after_raw, after_doc, after_bin = load_glb(args.v80_glb)
if before_doc != after_doc:
    fail("GLB JSON structure changed; Block-7 blink repair must be binary-payload only")
mesh = after_doc.get("meshes", [None])[0]
if mesh is None or mesh.get("extras", {}).get("targetNames") != TARGET_NAMES:
    fail("15-target facial contract changed")
primitives = mesh.get("primitives", [])
if not primitives: fail("mesh primitives missing")
bindings = primitives[0].get("targets", [])
if len(bindings) != len(TARGET_NAMES): fail("target binding count changed")
skin_material = after_doc.get("materials", [])[primitives[0].get("material", -1)]
if skin_material.get("name") != "Material_1" or "indices" not in primitives[0]:
    fail("deterministic v75 canonical skin/face primitive missing")
skin_vertices = indices(after_doc, after_bin, primitives[0]["indices"])
position_binding = primitives[0].get("attributes", {}).get("POSITION")
if position_binding is None: fail("canonical neutral POSITION binding missing")
positions = vecs(after_doc, after_bin, position_binding)

before_targets = []
after_targets = []
for binding in bindings:
    if set(binding) != {"POSITION"}: fail("unexpected morph attributes")
    accessor = binding["POSITION"]
    before_targets.append(vecs(before_doc, before_bin, accessor))
    after_targets.append(vecs(after_doc, after_bin, accessor))

for index, name in enumerate(TARGET_NAMES[3:], start=3):
    if before_targets[index] != after_targets[index]:
        fail(name + " changed during blink-only repair")

selection = {side: {layer: [] for layer in ("upper", "lower")} for side in ("left", "right")}
for vertex_index, position in enumerate(positions):
    if vertex_index not in skin_vertices:
        continue
    for side in ("left", "right"):
        for layer in ("upper", "lower"):
            if selected_layer(position, side, layer):
                selection[side][layer].append(vertex_index)
                break

for side in selection:
    if min(len(selection[side][layer]) for layer in ("upper", "lower")) < 10:
        fail(side + " visible eyelid selection unexpectedly sparse")

gaps = {}
expected_delta = {}
residuals = {}
for side in ("left", "right"):
    upper_mean = sum(positions[i][1] for i in selection[side]["upper"]) / len(selection[side]["upper"])
    lower_mean = sum(positions[i][1] for i in selection[side]["lower"]) / len(selection[side]["lower"])
    gap = upper_mean - lower_mean
    if not (0.008 <= gap <= 0.025): fail(side + " neutral mean gap outside guard")
    gaps[side] = gap
    expected_delta[side] = {
        "upper": (0.0, -gap * UPPER_CLOSE_FRACTION, -gap * UPPER_DEPTH_FRACTION),
        "lower": (0.0, gap * LOWER_CLOSE_FRACTION, -gap * LOWER_DEPTH_FRACTION),
    }
    residual = gap * (1.0 - UPPER_CLOSE_FRACTION - LOWER_CLOSE_FRACTION)
    residuals[side] = residual
    if not (MIN_RESIDUAL_MEAN_GAP_M <= residual <= MAX_RESIDUAL_MEAN_GAP_M):
        fail(side + " full-weight residual mean gap outside guard")

for vertex_index, position in enumerate(positions):
    expected = {"left": (0.0, 0.0, 0.0), "right": (0.0, 0.0, 0.0)}
    for side in ("left", "right"):
        if vertex_index in selection[side]["upper"]:
            expected[side] = expected_delta[side]["upper"]
        elif vertex_index in selection[side]["lower"]:
            expected[side] = expected_delta[side]["lower"]
    for target_index, side in ((0, "left"), (1, "right")):
        actual = after_targets[target_index][vertex_index]
        if not close_vec(actual, expected[side]):
            fail("%s blink vertex %d violates topology-preserving contract" % (side, vertex_index))
        if vertex_index not in skin_vertices and any(abs(v) > 1.0e-9 for v in actual):
            fail(side + " contains non-skin blink motion")

# Prove the repair no longer collapses every selected lid vertex onto one Y plane.
for target_index, side in ((0, "left"), (1, "right")):
    final_upper_y = [positions[i][1] + after_targets[target_index][i][1] for i in selection[side]["upper"]]
    final_lower_y = [positions[i][1] + after_targets[target_index][i][1] for i in selection[side]["lower"]]
    if len({round(v, 6) for v in final_upper_y}) < 20:
        fail(side + " upper lid topology collapsed to too few Y levels")
    if len({round(v, 6) for v in final_lower_y}) < 8:
        fail(side + " lower lid topology collapsed to too few Y levels")
    mean_gap = sum(final_upper_y) / len(final_upper_y) - sum(final_lower_y) / len(final_lower_y)
    if abs(mean_gap - residuals[side]) > 2.0e-6:
        fail(side + " final mean gap mismatch %.9f" % mean_gap)

max_comp_error = 0.0
for left, right, both in zip(after_targets[0], after_targets[1], after_targets[2]):
    error = math.sqrt(sum((both[c] - left[c] - right[c]) ** 2 for c in range(3)))
    max_comp_error = max(max_comp_error, error)
if max_comp_error > 2.0e-6:
    fail("BlinkBoth composition error %.9f m" % max_comp_error)
if before_raw == after_raw:
    fail("candidate is unchanged")

max_abs_y_delta = max(abs(expected_delta[s][l][1]) for s in expected_delta for l in expected_delta[s])
report = {
    "schema": 3,
    "status": "PASS",
    "policy": "v80_visible_eyelid_topology_preserving_closure",
    "v76_sha256": hashlib.sha256(before_raw).hexdigest(),
    "v80_sha256": hashlib.sha256(after_raw).hexdigest(),
    "visible_eyelid_vertices": {
        side: {layer: len(selection[side][layer]) for layer in ("upper", "lower")}
        for side in selection
    },
    "neutral_mean_gap_m": gaps,
    "full_weight_residual_mean_gap_m": residuals,
    "max_abs_y_delta_m": max_abs_y_delta,
    "blink_bilateral_composition_max_error_m": max_comp_error,
    "non_blink_targets_byte_identical": True,
    "json_contract_identical": True,
    "non_skin_blink_motion": False,
    "single_seam_collapse": False,
}
os.makedirs(os.path.dirname(os.path.abspath(args.report)), exist_ok=True)
open(args.report, "w", encoding="utf-8").write(json.dumps(report, indent=2) + "\n")
print(json.dumps(report, indent=2))
