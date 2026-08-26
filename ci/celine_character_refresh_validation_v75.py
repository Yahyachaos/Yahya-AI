#!/usr/bin/env python3
"""Fail-closed structural and visual-measurement guard for Celine v75."""

import argparse
import hashlib
import json
import math
import os
import struct


COMPONENT_SIZE = {5120: 1, 5121: 1, 5122: 2, 5123: 2, 5125: 4, 5126: 4}
COMPONENT = {5120: "b", 5121: "B", 5122: "h", 5123: "H", 5125: "I", 5126: "f"}
COMPONENTS = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}
SOURCE_SHA256 = "0c9fa09f898fbc8c0503be252c8fec1ee815a3a4990422e5c302e3113d7c1b55"
V65_SHA256 = "6e507144afa22f0534be0419884932a0c6aaa16b8b2013580013ffe5056bb146"
REFERENCE_HASHES = {
    "face_master.png": "5467e4db0ce3f76b5f5abc76d067c1691c23a3252c7e033f24dfe8c57cf86008",
    "body_front_master.png": "b163a76ca610840078d62a06013c587af7799bfdef75904d4d1aca4afaa7b331",
    "body_side_facing_right.png": "34e3f272770948a8ebcdbfff76cf30b11a96291264da7da88862e5335c692036",
    "body_side_facing_left.png": "afb66fb47b79a61063243810ea4a025708c8b4fb19867b7f10d4305a2e3b4baa",
    "body_back_master.png": "3464e04f381ccaf250a3fb86bb16bb358e2f2820e3a130b0be32abba1e2432e5",
}


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
    item = COMPONENT_SIZE[accessor["componentType"]] * COMPONENTS[accessor["type"]]
    stride = view.get("byteStride", item)
    start = view.get("byteOffset", 0) + accessor.get("byteOffset", 0)
    if stride == item:
        return binary[start:start + accessor["count"] * item]
    return b"".join(binary[start + row * stride:start + row * stride + item] for row in range(accessor["count"]))


def read_accessor(document, binary, index):
    accessor = document["accessors"][index]
    view = document["bufferViews"][accessor["bufferView"]]
    fmt = COMPONENT[accessor["componentType"]]
    count = COMPONENTS[accessor["type"]]
    item = struct.calcsize("<" + fmt) * count
    stride = view.get("byteStride", item)
    start = view.get("byteOffset", 0) + accessor.get("byteOffset", 0)
    unpack = struct.Struct("<" + fmt * count).unpack_from
    return [unpack(binary, start + row * stride) for row in range(accessor["count"])]


def image_bytes(document, binary):
    view = document["bufferViews"][document["images"][0]["bufferView"]]
    start = view.get("byteOffset", 0)
    return binary[start:start + view["byteLength"]]


def png_size(data):
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        fail("refreshed texture is not PNG")
    return struct.unpack(">II", data[16:24])


def joint_weight(js, ws, wanted):
    return sum(float(weight) for joint, weight in zip(js, ws) if int(joint) in wanted)


def widths(positions, joints, weights, wanted, y0, y1):
    values = [abs(float(position[0])) for position, js, ws in zip(positions, joints, weights)
              if y0 <= position[1] <= y1 and abs(position[0]) < 0.25 and joint_weight(js, ws, wanted) >= 0.45]
    if not values:
        fail("empty silhouette measurement band")
    values.sort()
    return 2.0 * values[int(0.95 * (len(values) - 1))]


parser = argparse.ArgumentParser(description="Validate guarded Celine v75 character refresh")
parser.add_argument("source_glb")
parser.add_argument("v65_glb")
parser.add_argument("candidate_glb")
parser.add_argument("reference_manifest")
parser.add_argument("--report", default="CELINE_CHARACTER_REFRESH_VALIDATION_V75.json")
parser.add_argument("--expected-sha256", default="")
args = parser.parse_args()

if len({os.path.abspath(args.source_glb), os.path.abspath(args.v65_glb), os.path.abspath(args.candidate_glb)}) != 3:
    fail("source, intermediate and candidate must be distinct files")
sraw, source_document, source_binary = load_glb(args.source_glb)
vraw, vdocument, vbinary = load_glb(args.v65_glb)
craw, cdocument, cbinary = load_glb(args.candidate_glb)
if hashlib.sha256(sraw).hexdigest() != SOURCE_SHA256:
    fail("canonical source hash mismatch")
if hashlib.sha256(vraw).hexdigest() != V65_SHA256:
    fail("v65 intermediate hash mismatch")
candidate_sha = hashlib.sha256(craw).hexdigest()
if args.expected_sha256 and candidate_sha != args.expected_sha256:
    fail(f"candidate hash mismatch expected={args.expected_sha256} actual={candidate_sha}")

manifest = json.load(open(args.reference_manifest, encoding="utf-8"))
actual_hashes = {name: item["sha256"] for name, item in manifest["files"].items()
                 if item.get("role", "").startswith("PRIMARY")}
if actual_hashes != REFERENCE_HASHES:
    fail("frozen v2 PRIMARY reference hashes changed")

vp = vdocument["meshes"][0]["primitives"][0]
cp = cdocument["meshes"][0]["primitives"][0]
if len(cdocument["meshes"]) != 1 or len(cdocument["meshes"][0]["primitives"]) != 1:
    fail("mesh/primitive structure changed")
if len(cdocument["accessors"]) != len(vdocument["accessors"]) + 2:
    fail("unexpected accessor additions")
if len(cdocument["bufferViews"]) != len(vdocument["bufferViews"]) + 2:
    fail("unexpected bufferView additions")
for attribute in ("TEXCOORD_0", "JOINTS_0", "WEIGHTS_0"):
    if accessor_bytes(vdocument, vbinary, vp["attributes"][attribute]) != accessor_bytes(cdocument, cbinary, cp["attributes"][attribute]):
        fail(attribute + " changed")
if accessor_bytes(vdocument, vbinary, vp["indices"]) != accessor_bytes(cdocument, cbinary, cp["indices"]):
    fail("topology/index stream changed")
if vp.get("targets") != cp.get("targets") or vdocument["meshes"][0].get("extras") != cdocument["meshes"][0].get("extras"):
    fail("facial morph target binding changed")
for target in vp.get("targets", []):
    for accessor in target.values():
        if accessor_bytes(vdocument, vbinary, accessor) != accessor_bytes(cdocument, cbinary, accessor):
            fail("facial morph delta changed")
for key in ("nodes", "skins", "animations", "scenes", "scene"):
    if vdocument.get(key) != cdocument.get(key):
        fail(key + " changed")

vpositions = read_accessor(vdocument, vbinary, vp["attributes"]["POSITION"])
cpositions = read_accessor(cdocument, cbinary, cp["attributes"]["POSITION"])
normals = read_accessor(cdocument, cbinary, cp["attributes"]["NORMAL"])
joints = read_accessor(vdocument, vbinary, vp["attributes"]["JOINTS_0"])
weights = read_accessor(vdocument, vbinary, vp["attributes"]["WEIGHTS_0"])
if len(vpositions) != 66700 or len(cpositions) != len(vpositions) or len(normals) != len(vpositions):
    fail("vertex count changed")
deltas = [math.dist(a, b) for a, b in zip(vpositions, cpositions)]
if not 20000 <= sum(delta > 1.0e-9 for delta in deltas) <= len(deltas):
    fail("unexpected changed vertex count")
if max(deltas) > 0.12:
    fail("position deformation exceeds 0.12 m")
if any(not all(math.isfinite(value) for value in row) for row in cpositions + normals):
    fail("non-finite geometry")
normal_lengths = [math.sqrt(sum(value * value for value in normal)) for normal in normals]
if min(normal_lengths) < 0.95 or max(normal_lengths) > 1.05:
    fail("recomputed normals are not unit length")

names = [vdocument["nodes"][index].get("name") for index in vdocument["skins"][0]["joints"]]
by_name = {name: index for index, name in enumerate(names)}
legs = {by_name[name] for name in ("Hips", "LeftUpLeg", "LeftLeg", "RightUpLeg", "RightLeg")}
source_waist = widths(vpositions, joints, weights, legs, 0.90, 1.10)
candidate_waist = widths(cpositions, joints, weights, legs, 0.90, 1.10)
source_hips = widths(vpositions, joints, weights, legs, 0.64, 0.88)
candidate_hips = widths(cpositions, joints, weights, legs, 0.64, 0.88)
if candidate_waist >= source_waist * 0.985:
    fail("waist silhouette did not become measurably slimmer")
if candidate_hips <= source_hips * 1.045:
    fail("hip silhouette did not become measurably fuller")
rear_source = min(position[2] for position, js, ws in zip(vpositions, joints, weights)
                  if 0.64 <= position[1] <= 0.90 and abs(position[0]) < 0.22 and joint_weight(js, ws, legs) >= 0.45)
rear_candidate = min(position[2] for position, js, ws in zip(cpositions, joints, weights)
                     if 0.64 <= position[1] <= 0.90 and abs(position[0]) < 0.22 and joint_weight(js, ws, legs) >= 0.45)
if rear_candidate >= rear_source - 0.004:
    fail("rear silhouette did not gain controlled glute projection")

old_image = image_bytes(vdocument, vbinary)
new_image = image_bytes(cdocument, cbinary)
if png_size(old_image) != (4096, 4096) or png_size(new_image) != (4096, 4096):
    fail("texture dimensions changed")
if hashlib.sha256(old_image).digest() == hashlib.sha256(new_image).digest():
    fail("v75 texture atlas did not change")
if cdocument["images"][0].get("name") != "celine_v75_master_reference_texture":
    fail("v75 texture identity marker missing")

report = {
    "schema": 1,
    "status": "PASS",
    "policy": "v75_fail_closed_structural_and_silhouette_guard",
    "source_sha256": SOURCE_SHA256,
    "v65_sha256": V65_SHA256,
    "candidate_sha256": candidate_sha,
    "reference_hashes_verified": REFERENCE_HASHES,
    "topology_preserved": True,
    "bones_skin_animations_preserved": True,
    "facial_morph_deltas_preserved": True,
    "changed_vertices": sum(delta > 1.0e-9 for delta in deltas),
    "max_position_delta_m": max(deltas),
    "waist_width_ratio": candidate_waist / source_waist,
    "hip_width_ratio": candidate_hips / source_hips,
    "rear_projection_delta_m": rear_source - rear_candidate,
    "normal_length_range": [min(normal_lengths), max(normal_lengths)],
    "texture_sha256_before": hashlib.sha256(old_image).hexdigest(),
    "texture_sha256_after": hashlib.sha256(new_image).hexdigest(),
    "remaining_gate": "real HOME/CALL/HOME-return and zoom emulator images on exact PR head",
}
os.makedirs(os.path.dirname(os.path.abspath(args.report)), exist_ok=True)
open(args.report, "w", encoding="utf-8").write(json.dumps(report, indent=2) + "\n")
print(json.dumps(report, indent=2))
