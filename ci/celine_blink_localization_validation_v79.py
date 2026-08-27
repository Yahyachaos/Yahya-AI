#!/usr/bin/env python3
"""Validate that v79 changes only blink POSITION payloads on the canonical visible eyelids."""

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
    formats = {5121: "B", 5123: "H", 5125: "I"}
    fmt = formats[accessor["componentType"]]
    size = struct.calcsize("<" + fmt)
    stride = view.get("byteStride", size)
    start = view.get("byteOffset", 0) + accessor.get("byteOffset", 0)
    return {
        struct.unpack_from("<" + fmt, binary, start + index * stride)[0]
        for index in range(accessor["count"])
    }


def in_range(value, bounds, upper_inclusive=True):
    low, high = bounds
    return low <= value <= high if upper_inclusive else low <= value < high


def expected_delta(position, side, layer):
    bounds = EYELID_BOUNDS[side]
    x, y, z = position
    if not in_range(x, bounds["x"]) or z < EYELID_Z_MIN:
        return None
    if not in_range(y, bounds[layer + "_y"], upper_inclusive=(layer == "upper")):
        return None
    return (0.0, EYELID_CLOSURE_Y - y, 0.0)


def close_vec(a, b, tolerance=2.0e-6):
    return all(abs(float(x) - float(y)) <= tolerance for x, y in zip(a, b))


parser = argparse.ArgumentParser(description="Validate v79 visible-eyelid blink localization")
parser.add_argument("v76_glb")
parser.add_argument("v79_glb")
parser.add_argument("--report", required=True)
args = parser.parse_args()

before_raw, before_doc, before_bin = load_glb(args.v76_glb)
after_raw, after_doc, after_bin = load_glb(args.v79_glb)
if before_doc != after_doc:
    fail("GLB JSON structure changed; v79 blink repair must be binary-payload only")
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

# All non-blink channels and all neutral/JSON contracts must remain byte-identical.
for index, name in enumerate(TARGET_NAMES[3:], start=3):
    if before_targets[index] != after_targets[index]:
        fail(name + " changed during blink-only repair")

expected_counts = {
    "left": {"upper": 0, "lower": 0},
    "right": {"upper": 0, "lower": 0},
}
max_abs_y_delta = 0.0
for vertex_index, position in enumerate(positions):
    expected = {"left": (0.0, 0.0, 0.0), "right": (0.0, 0.0, 0.0)}
    if vertex_index in skin_vertices:
        for side in ("left", "right"):
            for layer in ("upper", "lower"):
                delta = expected_delta(position, side, layer)
                if delta is None:
                    continue
                expected[side] = delta
                expected_counts[side][layer] += 1
                max_abs_y_delta = max(max_abs_y_delta, abs(delta[1]))
                break

    for side_index, side_name in ((0, "left"), (1, "right")):
        actual = after_targets[side_index][vertex_index]
        if not close_vec(actual, expected[side_name]):
            fail("%s blink vertex %d is outside deterministic visible-eyelid contract" %
                 (side_name, vertex_index))
        if vertex_index not in skin_vertices and any(abs(v) > 1.0e-9 for v in actual):
            fail(side_name + " contains non-skin blink motion")

for side in ("left", "right"):
    if min(expected_counts[side].values()) < 10:
        fail(side + " visible eyelid selection unexpectedly sparse")
if max_abs_y_delta <= 0.0 or max_abs_y_delta > 0.025:
    fail("visible eyelid displacement outside bounded range %.9f m" % max_abs_y_delta)

max_comp_error = 0.0
for left, right, both in zip(after_targets[0], after_targets[1], after_targets[2]):
    error = math.sqrt(sum((both[c] - left[c] - right[c]) ** 2 for c in range(3)))
    max_comp_error = max(max_comp_error, error)
if max_comp_error > 2.0e-6:
    fail("BlinkBoth composition error %.9f m" % max_comp_error)
if before_raw == after_raw:
    fail("candidate is unchanged")

report = {
    "schema": 2,
    "status": "PASS",
    "policy": "v79_visible_canonical_skin_eyelid_closure",
    "v76_sha256": hashlib.sha256(before_raw).hexdigest(),
    "v79_sha256": hashlib.sha256(after_raw).hexdigest(),
    "visible_eyelid_vertices": expected_counts,
    "closure_y_m": EYELID_CLOSURE_Y,
    "front_depth_min_m": EYELID_Z_MIN,
    "max_abs_y_delta_m": max_abs_y_delta,
    "blink_bilateral_composition_max_error_m": max_comp_error,
    "non_blink_targets_byte_identical": True,
    "json_contract_identical": True,
    "non_skin_blink_motion": False,
}
os.makedirs(os.path.dirname(os.path.abspath(args.report)), exist_ok=True)
open(args.report, "w", encoding="utf-8").write(json.dumps(report, indent=2) + "\n")
print(json.dumps(report, indent=2))