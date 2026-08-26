#!/usr/bin/env python3
"""Rebind Celine's facial morph targets to the final v75 geometry."""

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
LEGACY_TARGET_NAMES = [
    "BlinkLeft", "BlinkRight", "BlinkBoth", "JawOpen",
    "RoundedVowelProof", "SpreadVowelProof",
]
TARGET_NAMES = LEGACY_TARGET_NAMES + [
    "BilabialPress", "Labiodental", "Smile", "Thoughtful", "Surprised",
    "GazeLeft", "GazeRight", "GazeUp", "GazeDown",
]


def load_glb(path):
    raw = open(path, "rb").read()
    if raw[:4] != b"glTF":
        raise SystemExit("Input is not a GLB")
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


def joint_weight(js, ws, wanted):
    return sum(float(weight) for joint, weight in zip(js, ws) if int(joint) in wanted)


def zeros(vertex_count):
    return array("f", [0.0]) * (vertex_count * 3)


def add(target, vertex, component, value):
    target[vertex * 3 + component] += value


def combine(*targets):
    return array("f", (sum(float(target[i]) for target in targets) for i in range(len(targets[0]))))


def payload(values):
    result = array("f", values)
    if sys.byteorder != "little":
        result.byteswap()
    return result.tobytes()


def extrema(values):
    lows = [math.inf, math.inf, math.inf]
    highs = [-math.inf, -math.inf, -math.inf]
    for i in range(0, len(values), 3):
        for component in range(3):
            value = float(values[i + component])
            lows[component] = min(lows[component], value)
            highs[component] = max(highs[component], value)
    return lows, highs


def append_target(document, binary_out, values, vertex_count):
    while len(binary_out) % 4:
        binary_out.append(0)
    offset = len(binary_out)
    data = payload(values)
    binary_out.extend(data)
    view_index = len(document.setdefault("bufferViews", []))
    document["bufferViews"].append({
        "buffer": 0, "byteOffset": offset, "byteLength": len(data), "target": 34962,
    })
    accessor_index = len(document.setdefault("accessors", []))
    low, high = extrema(values)
    document["accessors"].append({
        "bufferView": view_index,
        "componentType": 5126,
        "count": vertex_count,
        "type": "VEC3",
        "min": low,
        "max": high,
    })
    return {"POSITION": accessor_index}


def write_glb(document, binary_out):
    document["buffers"][0]["byteLength"] = len(binary_out)
    json_bytes = json.dumps(document, separators=(",", ":")).encode("utf-8")
    while len(json_bytes) % 4:
        json_bytes += b" "
    while len(binary_out) % 4:
        binary_out.append(0)
    total = 12 + 8 + len(json_bytes) + 8 + len(binary_out)
    return (struct.pack("<4sII", b"glTF", 2, total)
            + struct.pack("<II", len(json_bytes), 0x4E4F534A) + json_bytes
            + struct.pack("<II", len(binary_out), 0x004E4942) + bytes(binary_out))


parser = argparse.ArgumentParser(description="Generate the guarded Celine v76 facial rig")
parser.add_argument("input_glb")
parser.add_argument("output_glb")
parser.add_argument("--report", default="CELINE_FACIAL_RIG_V76.json")
args = parser.parse_args()

if os.path.abspath(args.input_glb) == os.path.abspath(args.output_glb):
    raise SystemExit("Refusing in-place write")

raw, document, binary = load_glb(args.input_glb)
if document.get("asset", {}).get("generator") != "Yahya-AI Celine v75 semantic material-region split":
    raise SystemExit("Input is not the guarded final v75 material candidate")
if len(document.get("meshes", [])) != 1 or len(document["meshes"][0].get("primitives", [])) != 5:
    raise SystemExit("Expected the five final v75 semantic primitives")

mesh = document["meshes"][0]
primitives = mesh["primitives"]
first = primitives[0]
if mesh.get("extras", {}).get("targetNames") != LEGACY_TARGET_NAMES:
    raise SystemExit("Legacy six-target facial contract changed")
if any(primitive.get("attributes") != first.get("attributes") for primitive in primitives):
    raise SystemExit("v75 semantic primitives do not share vertex attributes")

attributes = first["attributes"]
positions = read_accessor(document, binary, attributes["POSITION"])
joints = read_accessor(document, binary, attributes["JOINTS_0"])
weights = read_accessor(document, binary, attributes["WEIGHTS_0"])
joint_names = [document["nodes"][index].get("name", "") for index in document["skins"][0]["joints"]]
by_name = {name: index for index, name in enumerate(joint_names)}
if "Head" not in by_name:
    raise SystemExit("Head joint missing")
head_joints = {by_name["Head"]}
if "neck" in by_name:
    head_joints.add(by_name["neck"])
head_mask = [joint_weight(js, ws, head_joints) >= 0.5 for js, ws in zip(joints, weights)]
head_positions = [position for position, selected in zip(positions, head_mask) if selected]
if not head_positions:
    raise SystemExit("Head-weighted vertex region is empty")

lows = [min(position[c] for position in head_positions) for c in range(3)]
highs = [max(position[c] for position in head_positions) for c in range(3)]
center = [(lows[c] + highs[c]) / 2.0 for c in range(3)]
span = [highs[c] - lows[c] for c in range(3)]
if min(span) <= 0.0:
    raise SystemExit("Invalid final v75 head bounds")

region_names = (
    "upper_eyelid_xneg", "lower_eyelid_xneg", "upper_eyelid_xpos", "lower_eyelid_xpos",
    "eye_surface_xneg", "eye_surface_xpos", "brow_xneg", "brow_xpos",
    "cheek_xneg", "cheek_xpos", "upper_lip", "lower_lip",
    "mouth_corner_xneg", "mouth_corner_xpos", "chin", "jawline_xneg", "jawline_xpos",
)
regions = {name: set() for name in region_names}
for index, (position, selected) in enumerate(zip(positions, head_mask)):
    if not selected:
        continue
    x_norm = (position[0] - center[0]) / (span[0] / 2.0)
    y_norm = (position[1] - lows[1]) / span[1]
    z_norm = (position[2] - lows[2]) / span[2]
    front = z_norm >= 0.52
    if front and 0.49 <= y_norm <= 0.57 and -0.60 <= x_norm <= -0.12: regions["upper_eyelid_xneg"].add(index)
    if front and 0.43 <= y_norm < 0.49 and -0.60 <= x_norm <= -0.12: regions["lower_eyelid_xneg"].add(index)
    if front and 0.49 <= y_norm <= 0.57 and 0.12 <= x_norm <= 0.60: regions["upper_eyelid_xpos"].add(index)
    if front and 0.43 <= y_norm < 0.49 and 0.12 <= x_norm <= 0.60: regions["lower_eyelid_xpos"].add(index)
    if z_norm >= 0.67 and 0.445 <= y_norm <= 0.555 and -0.50 <= x_norm <= -0.18: regions["eye_surface_xneg"].add(index)
    if z_norm >= 0.67 and 0.445 <= y_norm <= 0.555 and 0.18 <= x_norm <= 0.50: regions["eye_surface_xpos"].add(index)
    if front and 0.57 < y_norm <= 0.69 and -0.68 <= x_norm <= -0.08: regions["brow_xneg"].add(index)
    if front and 0.57 < y_norm <= 0.69 and 0.08 <= x_norm <= 0.68: regions["brow_xpos"].add(index)
    if front and 0.24 <= y_norm <= 0.46 and -0.72 <= x_norm <= -0.22: regions["cheek_xneg"].add(index)
    if front and 0.24 <= y_norm <= 0.46 and 0.22 <= x_norm <= 0.72: regions["cheek_xpos"].add(index)
    if front and 0.205 <= y_norm < 0.275 and abs(x_norm) <= 0.42: regions["upper_lip"].add(index)
    if front and 0.145 <= y_norm < 0.215 and abs(x_norm) <= 0.42: regions["lower_lip"].add(index)
    if front and 0.16 <= y_norm <= 0.27 and -0.52 <= x_norm <= -0.34: regions["mouth_corner_xneg"].add(index)
    if front and 0.16 <= y_norm <= 0.27 and 0.34 <= x_norm <= 0.52: regions["mouth_corner_xpos"].add(index)
    if front and 0.04 <= y_norm <= 0.17 and abs(x_norm) <= 0.45: regions["chin"].add(index)
    if 0.02 <= y_norm <= 0.30 and -0.92 <= x_norm <= -0.45: regions["jawline_xneg"].add(index)
    if 0.02 <= y_norm <= 0.30 and 0.45 <= x_norm <= 0.92: regions["jawline_xpos"].add(index)

required = [name for name in region_names if not regions[name]]
if required:
    raise SystemExit("Empty final-v75 facial regions: " + ", ".join(required))

vertex_count = len(positions)
width, height = span[0], span[1]


def blink(side):
    target = zeros(vertex_count)
    upper = regions["upper_eyelid_" + side]
    lower = regions["lower_eyelid_" + side]
    upper_y = sum(positions[index][1] for index in upper) / len(upper)
    lower_y = sum(positions[index][1] for index in lower) / len(lower)
    gap = max(height * 0.018, upper_y - lower_y)
    for index in upper:
        add(target, index, 1, -gap * 0.76)
        add(target, index, 2, -gap * 0.04)
    for index in lower:
        add(target, index, 1, gap * 0.24)
        add(target, index, 2, -gap * 0.03)
    return target


def jaw_open(strength=1.0):
    target = zeros(vertex_count)
    for index in regions["lower_lip"]:
        add(target, index, 1, -height * 0.045 * strength)
        add(target, index, 2, height * 0.010 * strength)
    for index in regions["chin"]:
        add(target, index, 1, -height * 0.068 * strength)
        add(target, index, 2, -height * 0.006 * strength)
    for name in ("jawline_xneg", "jawline_xpos"):
        for index in regions[name]:
            add(target, index, 1, -height * 0.043 * strength)
            add(target, index, 2, -height * 0.004 * strength)
    return target


left = blink("xneg")
right = blink("xpos")
both = combine(left, right)
jaw = jaw_open()

rounded = jaw_open(0.48)
for index in regions["upper_lip"]: add(rounded, index, 2, height * 0.013)
for index in regions["lower_lip"]: add(rounded, index, 2, height * 0.015)
for index in regions["mouth_corner_xneg"]: add(rounded, index, 0, width * 0.020)
for index in regions["mouth_corner_xpos"]: add(rounded, index, 0, -width * 0.020)

spread = jaw_open(0.34)
for index in regions["mouth_corner_xneg"]: add(spread, index, 0, -width * 0.026)
for index in regions["mouth_corner_xpos"]: add(spread, index, 0, width * 0.026)
for index in regions["cheek_xneg"]: add(spread, index, 0, -width * 0.005)
for index in regions["cheek_xpos"]: add(spread, index, 0, width * 0.005)

bilabial = zeros(vertex_count)
for index in regions["upper_lip"]:
    add(bilabial, index, 1, -height * 0.010)
    add(bilabial, index, 2, height * 0.008)
for index in regions["lower_lip"]:
    add(bilabial, index, 1, height * 0.012)
    add(bilabial, index, 2, height * 0.010)

labiodental = jaw_open(0.12)
for index in regions["lower_lip"]:
    add(labiodental, index, 1, height * 0.015)
    add(labiodental, index, 2, -height * 0.010)
for index in regions["mouth_corner_xneg"]: add(labiodental, index, 0, -width * 0.008)
for index in regions["mouth_corner_xpos"]: add(labiodental, index, 0, width * 0.008)

smile = zeros(vertex_count)
for name, direction in (("mouth_corner_xneg", -1.0), ("mouth_corner_xpos", 1.0)):
    for index in regions[name]:
        add(smile, index, 0, direction * width * 0.026)
        add(smile, index, 1, height * 0.022)
        add(smile, index, 2, height * 0.004)
for name, direction in (("cheek_xneg", -1.0), ("cheek_xpos", 1.0)):
    for index in regions[name]:
        add(smile, index, 0, direction * width * 0.004)
        add(smile, index, 1, height * 0.009)

thoughtful = zeros(vertex_count)
for index in regions["brow_xneg"]: add(thoughtful, index, 1, height * 0.018)
for index in regions["brow_xpos"]: add(thoughtful, index, 1, -height * 0.004)
for index in regions["mouth_corner_xpos"]:
    add(thoughtful, index, 1, -height * 0.005)
    add(thoughtful, index, 0, -width * 0.004)

surprised = jaw_open(0.30)
for name in ("brow_xneg", "brow_xpos"):
    for index in regions[name]: add(surprised, index, 1, height * 0.022)
for name in ("upper_eyelid_xneg", "upper_eyelid_xpos"):
    for index in regions[name]: add(surprised, index, 1, height * 0.008)
for name in ("lower_eyelid_xneg", "lower_eyelid_xpos"):
    for index in regions[name]: add(surprised, index, 1, -height * 0.004)
for index in regions["upper_lip"]: add(surprised, index, 2, height * 0.006)
for index in regions["lower_lip"]: add(surprised, index, 2, height * 0.008)


def gaze(component, amount):
    target = zeros(vertex_count)
    for name in ("eye_surface_xneg", "eye_surface_xpos"):
        for index in regions[name]: add(target, index, component, amount)
    return target


gaze_left = gaze(0, -width * 0.012)
gaze_right = gaze(0, width * 0.012)
gaze_up = gaze(1, height * 0.010)
gaze_down = gaze(1, -height * 0.010)

target_values = [
    left, right, both, jaw, rounded, spread, bilabial, labiodental,
    smile, thoughtful, surprised, gaze_left, gaze_right, gaze_up, gaze_down,
]
binary_out = bytearray(binary)
target_bindings = [append_target(document, binary_out, values, vertex_count) for values in target_values]
for primitive in primitives:
    primitive["targets"] = target_bindings
mesh["weights"] = [0.0] * len(TARGET_NAMES)
mesh.setdefault("extras", {})["targetNames"] = TARGET_NAMES
document.setdefault("asset", {})["generator"] = "Yahya-AI Celine v76 final-geometry facial rig"
output = write_glb(document, binary_out)

os.makedirs(os.path.dirname(os.path.abspath(args.output_glb)), exist_ok=True)
open(args.output_glb, "wb").write(output)
metrics = {}
for name, values in zip(TARGET_NAMES, target_values):
    norms = [math.sqrt(sum(float(values[i + c]) ** 2 for c in range(3))) for i in range(0, len(values), 3)]
    metrics[name] = {
        "nonzero_vertices": sum(norm > 1.0e-9 for norm in norms),
        "max_delta_m": max(norms),
    }
report = {
    "schema": 1,
    "status": "PASS",
    "policy": "final_v75_geometry_rebind_append_only_fail_closed",
    "input_sha256": hashlib.sha256(raw).hexdigest(),
    "output_sha256": hashlib.sha256(output).hexdigest(),
    "vertex_count": vertex_count,
    "target_names": TARGET_NAMES,
    "region_vertices": {name: len(values) for name, values in regions.items()},
    "metrics": metrics,
    "preserved": ["neutral positions", "normals", "indices", "materials", "textures", "skin", "bones", "animations"],
    "fallback": "runtime requires the exact 15-target contract; any probe/write failure disables all facial morphs",
}
os.makedirs(os.path.dirname(os.path.abspath(args.report)), exist_ok=True)
open(args.report, "w", encoding="utf-8").write(json.dumps(report, indent=2) + "\n")
print(json.dumps(report, indent=2))

