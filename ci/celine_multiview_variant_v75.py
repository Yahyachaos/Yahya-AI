#!/usr/bin/env python3
"""Create a view-only rotated GLB from the exact v75 candidate.

The variant is evidence-only: it preserves every existing node/mesh/skin/material/buffer
and wraps the active scene roots in one new Y-rotation parent. This lets the real Android
Filament renderer capture left/right/back silhouette evidence without changing production
geometry or runtime ownership.
"""
import argparse
import hashlib
import json
import math
import os
import struct


def load_glb(path):
    raw = open(path, "rb").read()
    if raw[:4] != b"glTF":
        raise SystemExit("input is not GLB")
    version, total = struct.unpack_from("<II", raw, 4)
    if version != 2 or total != len(raw):
        raise SystemExit("invalid GLB header")
    off = 12
    doc = None
    bin_chunk = b""
    while off < len(raw):
        length, kind = struct.unpack_from("<II", raw, off)
        off += 8
        chunk = raw[off:off + length]
        off += length
        if kind == 0x4E4F534A:
            doc = json.loads(chunk.decode("utf-8").rstrip("\x00 "))
        elif kind == 0x004E4942:
            bin_chunk = chunk
    if doc is None:
        raise SystemExit("GLB JSON missing")
    return raw, doc, bin_chunk


def write_glb(doc, binary):
    js = json.dumps(doc, separators=(",", ":")).encode("utf-8")
    while len(js) % 4:
        js += b" "
    binary = bytearray(binary)
    while len(binary) % 4:
        binary.append(0)
    total = 12 + 8 + len(js) + (8 + len(binary) if binary else 0)
    out = bytearray(b"glTF" + struct.pack("<II", 2, total))
    out.extend(struct.pack("<II", len(js), 0x4E4F534A))
    out.extend(js)
    if binary:
        out.extend(struct.pack("<II", len(binary), 0x004E4942))
        out.extend(binary)
    return bytes(out)


def quat_y(degrees):
    half = math.radians(degrees) * 0.5
    return [0.0, math.sin(half), 0.0, math.cos(half)]


p = argparse.ArgumentParser()
p.add_argument("input_glb")
p.add_argument("output_glb")
p.add_argument("--yaw", type=float, required=True)
p.add_argument("--report")
a = p.parse_args()

raw, doc, binary = load_glb(a.input_glb)
scene_idx = int(doc.get("scene", 0))
scenes = doc.get("scenes", [])
if not (0 <= scene_idx < len(scenes)):
    raise SystemExit("active scene missing")
roots = list(scenes[scene_idx].get("nodes", []))
if not roots:
    raise SystemExit("active scene has no roots")

before = {
    "nodes": len(doc.get("nodes", [])),
    "meshes": len(doc.get("meshes", [])),
    "skins": len(doc.get("skins", [])),
    "materials": len(doc.get("materials", [])),
    "accessors": len(doc.get("accessors", [])),
    "bufferViews": len(doc.get("bufferViews", [])),
}
wrapper = len(doc.setdefault("nodes", []))
doc["nodes"].append({
    "name": "CelineV75_EvidenceYaw",
    "rotation": quat_y(a.yaw),
    "children": roots,
})
scenes[scene_idx]["nodes"] = [wrapper]
doc.setdefault("asset", {})["generator"] = str(doc.get("asset", {}).get("generator", "")) + " | v75 evidence yaw wrapper"

out = write_glb(doc, binary)
os.makedirs(os.path.dirname(os.path.abspath(a.output_glb)), exist_ok=True)
open(a.output_glb, "wb").write(out)
report = {
    "schema": 1,
    "status": "PASS",
    "policy": "evidence_only_scene_root_yaw_wrapper",
    "yaw_degrees": a.yaw,
    "input_sha256": hashlib.sha256(raw).hexdigest(),
    "output_sha256": hashlib.sha256(out).hexdigest(),
    "original_scene_roots": roots,
    "wrapper_node": wrapper,
    "preserved_counts": before,
    "post_counts": {**before, "nodes": before["nodes"] + 1},
    "binary_payload_sha256": hashlib.sha256(binary).hexdigest(),
}
if a.report:
    os.makedirs(os.path.dirname(os.path.abspath(a.report)), exist_ok=True)
    open(a.report, "w", encoding="utf-8").write(json.dumps(report, indent=2) + "\n")
print(json.dumps(report, indent=2))
