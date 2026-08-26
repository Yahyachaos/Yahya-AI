#!/usr/bin/env python3
"""Validate that v79 changes only the three blink POSITION payloads and removes cheek-like lower-lid motion."""

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
kept = {"left": 0, "right": 0}
for side_index, side_name in ((0, "left"), (1, "right")):
    for old, new in zip(before_targets[side_index], after_targets[side_index]):
        if old[1] > 1.0e-9:
            if any(abs(value) > 1.0e-9 for value in new):
                fail(side_name + " still contains positive-Y lower-lid/cheek blink motion")
            removed[side_name] += 1
        elif math.sqrt(sum(value * value for value in old)) > 1.0e-9:
            if new != old:
                fail(side_name + " upper-lid blink delta changed")
            kept[side_name] += 1
        elif new != old:
            fail(side_name + " introduced a new blink vertex")
    if removed[side_name] == 0 or kept[side_name] == 0:
        fail(side_name + " repair did not both remove lower motion and preserve upper closure")

max_comp_error = 0.0
for left, right, both in zip(after_targets[0], after_targets[1], after_targets[2]):
    error = math.sqrt(sum((both[c] - left[c] - right[c]) ** 2 for c in range(3)))
    max_comp_error = max(max_comp_error, error)
    if both[1] > 1.0e-9:
        fail("BlinkBoth still has positive-Y lower-lid/cheek motion")
if max_comp_error > 2.0e-6:
    fail("BlinkBoth composition error %.9f m" % max_comp_error)
if before_raw == after_raw:
    fail("candidate is unchanged")

report = {
    "schema": 1,
    "status": "PASS",
    "policy": "v79_blink_payload_only_no_positive_y_lower_motion",
    "v76_sha256": hashlib.sha256(before_raw).hexdigest(),
    "v79_sha256": hashlib.sha256(after_raw).hexdigest(),
    "removed_positive_y_vertices": removed,
    "preserved_upper_lid_vertices": kept,
    "blink_bilateral_composition_max_error_m": max_comp_error,
    "non_blink_targets_byte_identical": True,
    "json_contract_identical": True,
}
os.makedirs(os.path.dirname(os.path.abspath(args.report)), exist_ok=True)
open(args.report, "w", encoding="utf-8").write(json.dumps(report, indent=2) + "\n")
print(json.dumps(report, indent=2))
