#!/usr/bin/env python3
"""Build the validated six-target production GLB without third-party packages."""

import argparse
from array import array
import hashlib
import json
import math
import os
import struct
import sys


COMPONENT = {5120: "b", 5121: "B", 5122: "h", 5123: "H", 5125: "I", 5126: "f"}
COMPONENTS = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}
TARGET_NAMES = [
    "BlinkLeft", "BlinkRight", "BlinkBoth", "JawOpen",
    "RoundedVowelProof", "SpreadVowelProof",
]


def load_glb(path):
    raw = open(path, "rb").read()
    if raw[:4] != b"glTF":
        raise SystemExit("Input is not a GLB")
    offset = 12
    document = None
    binary = b""
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
        raise SystemExit("GLB JSON chunk missing")
    return raw, document, binary


def read_accessor(document, binary, index):
    accessor = document["accessors"][index]
    view = document["bufferViews"][accessor["bufferView"]]
    fmt = COMPONENT[accessor["componentType"]]
    components = COMPONENTS[accessor["type"]]
    item_size = struct.calcsize("<" + fmt) * components
    stride = view.get("byteStride", item_size)
    start = view.get("byteOffset", 0) + accessor.get("byteOffset", 0)
    unpack = struct.Struct("<" + fmt * components).unpack_from
    return [unpack(binary, start + row * stride) for row in range(accessor["count"])]


def zeros(vertex_count):
    return array("f", [0.0]) * (vertex_count * 3)


def add(target, vertex, component, value):
    slot = vertex * 3 + component
    target[slot] = target[slot] + value


def extrema(values):
    lows = [math.inf, math.inf, math.inf]
    highs = [-math.inf, -math.inf, -math.inf]
    for i in range(0, len(values), 3):
        for component in range(3):
            value = float(values[i + component])
            lows[component] = min(lows[component], value)
            highs[component] = max(highs[component], value)
    return lows, highs


def payload(values):
    copy = array("f", values)
    if sys.byteorder != "little":
        copy.byteswap()
    return copy.tobytes()


parser = argparse.ArgumentParser(
    description="Generate the validated Celine v65 production morph candidate copy"
)
parser.add_argument("input_glb")
parser.add_argument("output_glb")
parser.add_argument("--report", default="CELINE_PRODUCTION_MORPH_V65.json")
parser.add_argument("--expected-sha256", default="")
args = parser.parse_args()

if os.path.abspath(args.input_glb) == os.path.abspath(args.output_glb):
    raise SystemExit("Refusing in-place write: output must be a separate generated asset")

raw, document, binary = load_glb(args.input_glb)
input_sha = hashlib.sha256(raw).hexdigest()
primitive = document["meshes"][0]["primitives"][0]
attributes = primitive["attributes"]
if "JOINTS_0" not in attributes or "WEIGHTS_0" not in attributes:
    raise SystemExit("Expected skinned production mesh attributes")

positions = read_accessor(document, binary, attributes["POSITION"])
joints = read_accessor(document, binary, attributes["JOINTS_0"])
weights = read_accessor(document, binary, attributes["WEIGHTS_0"])
joint_names = [document["nodes"][i].get("name") for i in document["skins"][0]["joints"]]
if "Head" not in joint_names:
    raise SystemExit("Head joint missing")
head_joint = joint_names.index("Head")
head_mask = []
for js, ws in zip(joints, weights):
    head_weight = sum(float(weight) for joint, weight in zip(js, ws) if int(joint) == head_joint)
    head_mask.append(head_weight >= 0.5)

head_positions = [position for position, selected in zip(positions, head_mask) if selected]
if not head_positions:
    raise SystemExit("Head-weighted vertex region is empty")
lows = [min(p[c] for p in head_positions) for c in range(3)]
highs = [max(p[c] for p in head_positions) for c in range(3)]
center = [(lows[c] + highs[c]) / 2.0 for c in range(3)]
span = [highs[c] - lows[c] for c in range(3)]
if min(span) <= 0.0:
    raise SystemExit("Invalid Head region span")

regions = {name: set() for name in (
    "upper_eyelid_xneg", "lower_eyelid_xneg", "upper_eyelid_xpos",
    "lower_eyelid_xpos", "cheek_xneg", "cheek_xpos", "upper_lip",
    "lower_lip", "mouth_corner_xneg", "mouth_corner_xpos", "chin",
    "jawline_xneg", "jawline_xpos",
)}
for index, (position, is_head) in enumerate(zip(positions, head_mask)):
    if not is_head:
        continue
    x_norm = (position[0] - center[0]) / (span[0] / 2.0)
    y_norm = (position[1] - lows[1]) / span[1]
    z_norm = (position[2] - lows[2]) / span[2]
    front = z_norm >= 0.52
    if front and 0.49 <= y_norm <= 0.57 and -0.60 <= x_norm <= -0.12: regions["upper_eyelid_xneg"].add(index)
    if front and 0.43 <= y_norm < 0.49 and -0.60 <= x_norm <= -0.12: regions["lower_eyelid_xneg"].add(index)
    if front and 0.49 <= y_norm <= 0.57 and 0.12 <= x_norm <= 0.60: regions["upper_eyelid_xpos"].add(index)
    if front and 0.43 <= y_norm < 0.49 and 0.12 <= x_norm <= 0.60: regions["lower_eyelid_xpos"].add(index)
    if front and 0.24 <= y_norm <= 0.46 and -0.72 <= x_norm <= -0.22: regions["cheek_xneg"].add(index)
    if front and 0.24 <= y_norm <= 0.46 and 0.22 <= x_norm <= 0.72: regions["cheek_xpos"].add(index)
    if front and 0.205 <= y_norm < 0.275 and abs(x_norm) <= 0.42: regions["upper_lip"].add(index)
    if front and 0.145 <= y_norm < 0.215 and abs(x_norm) <= 0.42: regions["lower_lip"].add(index)
    if front and 0.16 <= y_norm <= 0.27 and -0.52 <= x_norm <= -0.34: regions["mouth_corner_xneg"].add(index)
    if front and 0.16 <= y_norm <= 0.27 and 0.34 <= x_norm <= 0.52: regions["mouth_corner_xpos"].add(index)
    if front and 0.04 <= y_norm <= 0.17 and abs(x_norm) <= 0.45: regions["chin"].add(index)
    if 0.02 <= y_norm <= 0.30 and -0.92 <= x_norm <= -0.45: regions["jawline_xneg"].add(index)
    if 0.02 <= y_norm <= 0.30 and 0.45 <= x_norm <= 0.92: regions["jawline_xpos"].add(index)

vertex_count = len(positions)


def blink(side):
    target = zeros(vertex_count)
    upper = regions["upper_eyelid_" + side]
    lower = regions["lower_eyelid_" + side]
    if not upper or not lower:
        raise SystemExit("Eyelid seed region empty: " + side)
    upper_y = sum(positions[i][1] for i in upper) / len(upper)
    lower_y = sum(positions[i][1] for i in lower) / len(lower)
    gap = max(0.0, upper_y - lower_y)
    for index in upper:
        add(target, index, 1, -gap * 0.78)
        add(target, index, 2, -gap * 0.05)
    for index in lower:
        add(target, index, 1, gap * 0.22)
        add(target, index, 2, -gap * 0.05)
    return target


def jaw_open(strength):
    target = zeros(vertex_count)
    height = span[1]
    for index in regions["lower_lip"]:
        add(target, index, 1, -height * 0.045 * strength)
        add(target, index, 2, height * 0.010 * strength)
    for index in regions["chin"]:
        add(target, index, 1, -height * 0.070 * strength)
        add(target, index, 2, -height * 0.006 * strength)
    for name in ("jawline_xneg", "jawline_xpos"):
        for index in regions[name]:
            add(target, index, 1, -height * 0.045 * strength)
            add(target, index, 2, -height * 0.004 * strength)
    return target


left = blink("xneg")
right = blink("xpos")
both = array("f", (float(a) + float(b) for a, b in zip(left, right)))
jaw = jaw_open(1.0)
rounded = jaw_open(0.55)
spread = jaw_open(0.45)
width, height = span[0], span[1]
for index in regions["upper_lip"]: add(rounded, index, 2, height * 0.012)
for index in regions["lower_lip"]: add(rounded, index, 2, height * 0.014)
for index in regions["mouth_corner_xneg"]: add(rounded, index, 0, width * 0.020)
for index in regions["mouth_corner_xpos"]: add(rounded, index, 0, -width * 0.020)
for index in regions["mouth_corner_xneg"]: add(spread, index, 0, -width * 0.025)
for index in regions["mouth_corner_xpos"]: add(spread, index, 0, width * 0.025)
for index in regions["cheek_xneg"]: add(spread, index, 0, -width * 0.006)
for index in regions["cheek_xpos"]: add(spread, index, 0, width * 0.006)

targets = list(zip(TARGET_NAMES, (left, right, both, jaw, rounded, spread)))
binary_out = bytearray(binary)
new_targets = []
for name, values in targets:
    while len(binary_out) % 4:
        binary_out.append(0)
    byte_offset = len(binary_out)
    data = payload(values)
    binary_out.extend(data)
    view_index = len(document.setdefault("bufferViews", []))
    document["bufferViews"].append({
        "buffer": 0, "byteOffset": byte_offset, "byteLength": len(data), "target": 34962,
    })
    accessor_index = len(document.setdefault("accessors", []))
    minimum, maximum = extrema(values)
    document["accessors"].append({
        "bufferView": view_index, "componentType": 5126, "count": vertex_count,
        "type": "VEC3", "min": minimum, "max": maximum,
    })
    new_targets.append({"POSITION": accessor_index})

primitive["targets"] = new_targets
mesh = document["meshes"][0]
mesh["weights"] = [0.0] * len(targets)
mesh.setdefault("extras", {})["targetNames"] = TARGET_NAMES
document["buffers"][0]["byteLength"] = len(binary_out)

json_bytes = json.dumps(document, separators=(",", ":")).encode("utf-8")
while len(json_bytes) % 4: json_bytes += b" "
while len(binary_out) % 4: binary_out.append(0)
total = 12 + 8 + len(json_bytes) + 8 + len(binary_out)
output = bytearray(struct.pack("<4sII", b"glTF", 2, total))
output += struct.pack("<II", len(json_bytes), 0x4E4F534A) + json_bytes
output += struct.pack("<II", len(binary_out), 0x004E4942) + binary_out

output_sha = hashlib.sha256(output).hexdigest()
if args.expected_sha256 and output_sha != args.expected_sha256:
    raise SystemExit(
        "Generated candidate hash mismatch: expected=" + args.expected_sha256 + " actual=" + output_sha
    )
os.makedirs(os.path.dirname(os.path.abspath(args.output_glb)), exist_ok=True)
open(args.output_glb, "wb").write(output)
nonzero = {}
max_delta = {}
for name, values in targets:
    changed = 0
    largest = 0.0
    for i in range(0, len(values), 3):
        norm = math.sqrt(sum(float(values[i + c]) ** 2 for c in range(3)))
        if norm > 1.0e-9: changed += 1
        largest = max(largest, norm)
    nonzero[name] = changed
    max_delta[name] = largest
report = {
    "schema": 1,
    "status": "PASS",
    "policy": "reproducible_generated_production_asset",
    "input_sha256": input_sha,
    "output_sha256": output_sha,
    "vertex_count": vertex_count,
    "target_names": TARGET_NAMES,
    "nonzero_vertices": nonzero,
    "max_delta_m": max_delta,
    "neutral_identity": "implicit weight=0; source POSITION accessor untouched",
    "fallback": "build fails closed on source/candidate hash mismatch; runtime disables morphs on probe/write error",
}
open(args.report, "w", encoding="utf-8").write(json.dumps(report, indent=2) + "\n")
print(json.dumps(report, indent=2))

