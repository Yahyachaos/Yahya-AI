#!/usr/bin/env python3
"""Validate that v76 changes only Celine's facial morph contract and payload."""

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
MAX_DELTA_M = {
    "BlinkLeft": 0.035, "BlinkRight": 0.035, "BlinkBoth": 0.035,
    "JawOpen": 0.055, "RoundedVowelProof": 0.040, "SpreadVowelProof": 0.040,
    "BilabialPress": 0.025, "Labiodental": 0.030, "Smile": 0.035,
    "Thoughtful": 0.025, "Surprised": 0.045,
    "GazeLeft": 0.012, "GazeRight": 0.012, "GazeUp": 0.012, "GazeDown": 0.012,
}
COMPONENT_SIZE = {5120: 1, 5121: 1, 5122: 2, 5123: 2, 5125: 4, 5126: 4}
COMPONENTS = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}


def fail(message):
    raise SystemExit("FAIL " + message)


def load_glb(path):
    raw = open(path, "rb").read()
    if raw[:4] != b"glTF":
        fail(path + " is not a GLB")
    offset, document, binary = 12, None, b""
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
        fail(path + " has no JSON chunk")
    return raw, document, binary


def accessor_bytes(document, binary, index):
    accessor = document["accessors"][index]
    view = document["bufferViews"][accessor["bufferView"]]
    item_size = COMPONENT_SIZE[accessor["componentType"]] * COMPONENTS[accessor["type"]]
    stride = view.get("byteStride", item_size)
    start = view.get("byteOffset", 0) + accessor.get("byteOffset", 0)
    if stride == item_size:
        return binary[start:start + accessor["count"] * item_size]
    return b"".join(binary[start + row * stride:start + row * stride + item_size]
                    for row in range(accessor["count"]))


def target_values(document, binary, target):
    if set(target) != {"POSITION"}:
        fail("morph target contains attributes other than POSITION")
    accessor = document["accessors"][target["POSITION"]]
    if accessor["componentType"] != 5126 or accessor["type"] != "VEC3":
        fail("morph POSITION accessor must be float32 VEC3")
    return list(struct.iter_unpack("<fff", accessor_bytes(document, binary, target["POSITION"])))


parser = argparse.ArgumentParser(description="Validate the guarded Celine v76 facial rig")
parser.add_argument("input_glb")
parser.add_argument("candidate_glb")
parser.add_argument("--report", default="CELINE_FACIAL_RIG_VALIDATION_V76.json")
args = parser.parse_args()

if os.path.abspath(args.input_glb) == os.path.abspath(args.candidate_glb):
    fail("input and candidate must be distinct files")
input_raw, input_document, input_binary = load_glb(args.input_glb)
candidate_raw, candidate_document, candidate_binary = load_glb(args.candidate_glb)

if input_document.get("asset", {}).get("generator") != "Yahya-AI Celine v75 semantic material-region split":
    fail("input is not the final guarded v75 candidate")
if candidate_document.get("asset", {}).get("generator") != "Yahya-AI Celine v76 final-geometry facial rig":
    fail("candidate generator contract missing")
if len(input_document.get("meshes", [])) != 1 or len(candidate_document.get("meshes", [])) != 1:
    fail("mesh count changed")
input_mesh = input_document["meshes"][0]
candidate_mesh = candidate_document["meshes"][0]
input_primitives = input_mesh.get("primitives", [])
candidate_primitives = candidate_mesh.get("primitives", [])
if len(input_primitives) != 5 or len(candidate_primitives) != 5:
    fail("semantic primitive count changed")
if candidate_mesh.get("extras", {}).get("targetNames") != TARGET_NAMES:
    fail("target name/order contract changed")
if candidate_mesh.get("weights") != [0.0] * len(TARGET_NAMES):
    fail("neutral morph weights must all be zero")

if len(candidate_document.get("accessors", [])) != len(input_document.get("accessors", [])) + len(TARGET_NAMES):
    fail("candidate must append exactly one accessor per target")
if len(candidate_document.get("bufferViews", [])) != len(input_document.get("bufferViews", [])) + len(TARGET_NAMES):
    fail("candidate must append exactly one bufferView per target")
if not candidate_binary.startswith(input_binary):
    fail("v75 binary payload is not an immutable prefix")

for key in ("nodes", "skins", "animations", "scenes", "scene", "materials", "textures", "images", "samplers"):
    if candidate_document.get(key) != input_document.get(key):
        fail(key + " changed")

bindings = candidate_primitives[0].get("targets", [])
if len(bindings) != len(TARGET_NAMES):
    fail("target binding count mismatch")
for index, (before, after) in enumerate(zip(input_primitives, candidate_primitives)):
    for key in ("attributes", "indices", "material", "mode"):
        if before.get(key) != after.get(key):
            fail(f"primitive {index} {key} changed")
    if after.get("targets") != bindings:
        fail(f"primitive {index} does not share the canonical target bindings")
    for accessor in before.get("attributes", {}).values():
        if accessor_bytes(input_document, input_binary, accessor) != accessor_bytes(candidate_document, candidate_binary, accessor):
            fail(f"primitive {index} neutral attribute payload changed")
    if accessor_bytes(input_document, input_binary, before["indices"]) != accessor_bytes(candidate_document, candidate_binary, after["indices"]):
        fail(f"primitive {index} index payload changed")

vertex_count = candidate_document["accessors"][candidate_primitives[0]["attributes"]["POSITION"]]["count"]
metrics = {}
all_values = []
for name, target in zip(TARGET_NAMES, bindings):
    values = target_values(candidate_document, candidate_binary, target)
    if len(values) != vertex_count:
        fail(name + " vertex count mismatch")
    nonzero = 0
    maximum = 0.0
    for row in values:
        if not all(math.isfinite(value) for value in row):
            fail(name + " contains a non-finite delta")
        norm = math.sqrt(sum(value * value for value in row))
        maximum = max(maximum, norm)
        if norm > 1.0e-9:
            nonzero += 1
    if nonzero == 0:
        fail(name + " is empty")
    if maximum > MAX_DELTA_M[name]:
        fail(f"{name} max delta {maximum:.6f} m exceeds {MAX_DELTA_M[name]:.6f} m")
    metrics[name] = {"nonzero_vertices": nonzero, "max_delta_m": maximum}
    all_values.append(values)

max_blink_error = 0.0
for left, right, both in zip(all_values[0], all_values[1], all_values[2]):
    error = math.sqrt(sum((both[i] - left[i] - right[i]) ** 2 for i in range(3)))
    max_blink_error = max(max_blink_error, error)
if max_blink_error > 2.0e-6:
    fail(f"BlinkBoth composition error {max_blink_error:.9f} m")

for first, second, label in ((11, 12, "horizontal gaze"), (13, 14, "vertical gaze")):
    max_opposition_error = 0.0
    for a, b in zip(all_values[first], all_values[second]):
        error = math.sqrt(sum((a[i] + b[i]) ** 2 for i in range(3)))
        max_opposition_error = max(max_opposition_error, error)
    if max_opposition_error > 2.0e-6:
        fail(label + " targets are not symmetric opposites")

report = {
    "schema": 1,
    "status": "PASS",
    "policy": "v76_append_only_facial_contract_validation",
    "input_sha256": hashlib.sha256(input_raw).hexdigest(),
    "candidate_sha256": hashlib.sha256(candidate_raw).hexdigest(),
    "neutral_geometry_materials_rig_preserved": True,
    "target_names": TARGET_NAMES,
    "metrics": metrics,
    "blink_bilateral_composition_max_error_m": max_blink_error,
    "runtime_fallback_contract": "exact target count required; any probe/write failure disables morph output",
    "remaining_gate": "exact-head facial render, speech, gaze and HOME/CALL/HOME lifecycle evidence",
}
os.makedirs(os.path.dirname(os.path.abspath(args.report)), exist_ok=True)
open(args.report, "w", encoding="utf-8").write(json.dumps(report, indent=2) + "\n")
print(json.dumps(report, indent=2))

