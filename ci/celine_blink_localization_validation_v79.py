#!/usr/bin/env python3
"""Validate that v79 changes only blink POSITION payloads and confines them to skin/face."""

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
        fail("morph accessor must be float32 VEC3")
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


parser = argparse.ArgumentParser(description="Validate v79 blink localization")
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

before_targets = []
after_targets = []
for binding in bindings:
    if set(binding) != {"POSITION"}: fail("unexpected morph attributes")
    accessor = binding["POSITION"]
    before_targets.append(vecs(before_doc, before_bin, accessor))
    after_targets.append(vecs(after_doc, after_bin, accessor))

# All non-blink channels must be byte-identical.
for index, name in enumerate(TARGET_NAMES[3:], start=3):
    if before_targets[index] != after_targets[index]:
        fail(name + " changed during blink-only repair")

removed = {"left": 0, "right": 0}
kept = {
    "left": {"upper": 0, "lower": 0},
    "right": {"upper": 0, "lower": 0},
}
for side_index, side_name in ((0, "left"), (1, "right")):
    for vertex_index, (old, new) in enumerate(zip(before_targets[side_index], after_targets[side_index])):
        old_nonzero = math.sqrt(sum(value * value for value in old)) > 1.0e-9
        if old_nonzero and vertex_index not in skin_vertices:
            if any(abs(value) > 1.0e-9 for value in new):
                fail(side_name + " still contains non-skin blink motion")
            removed[side_name] += 1
        elif old_nonzero:
            if new != old:
                fail(side_name + " skin/face blink delta changed")
            if old[1] < -1.0e-9:
                kept[side_name]["upper"] += 1
            elif old[1] > 1.0e-9:
                kept[side_name]["lower"] += 1
        elif new != old:
            fail(side_name + " introduced a new blink vertex")
    if removed[side_name] == 0 or min(kept[side_name].values()) == 0:
        fail(side_name + " repair did not remove non-skin motion and preserve both eyelids")

max_comp_error = 0.0
for left, right, both in zip(after_targets[0], after_targets[1], after_targets[2]):
    error = math.sqrt(sum((both[c] - left[c] - right[c]) ** 2 for c in range(3)))
    max_comp_error = max(max_comp_error, error)
if max_comp_error > 2.0e-6:
    fail("BlinkBoth composition error %.9f m" % max_comp_error)
if before_raw == after_raw:
    fail("candidate is unchanged")

report = {
    "schema": 1,
    "status": "PASS",
    "policy": "v79_blink_payload_only_canonical_skin_face",
    "v76_sha256": hashlib.sha256(before_raw).hexdigest(),
    "v79_sha256": hashlib.sha256(after_raw).hexdigest(),
    "removed_non_skin_vertices": removed,
    "preserved_skin_face_vertices": kept,
    "blink_bilateral_composition_max_error_m": max_comp_error,
    "non_blink_targets_byte_identical": True,
    "json_contract_identical": True,
}
os.makedirs(os.path.dirname(os.path.abspath(args.report)), exist_ok=True)
open(args.report, "w", encoding="utf-8").write(json.dumps(report, indent=2) + "\n")
print(json.dumps(report, indent=2))
