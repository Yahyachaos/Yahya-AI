#!/usr/bin/env python3
"""Room-only fail-closed audit of the derived back-wall window aperture.

The reference room needs actual outdoor depth behind the window. This audit checks
the *derived* room shell; it never modifies canonical furniture/source GLBs and
does not grant visual acceptance. Invoke on the current source-fidelity shell GLB.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import struct

JSON_CHUNK = 0x4E4F534A
BIN_CHUNK = 0x004E4942
EXPECTED_BBOX = ((-2.2, -1.325, -0.05), (2.2, 1.325, 0.05))
# These are source-local coordinates, not screen pixels.
APERTURE_CENTER_X = 0.75
APERTURE_CENTER_LOCAL_Y = 1.65 - 1.325


def glb(path: Path):
    raw = path.read_bytes()
    magic, version, total = struct.unpack_from("<III", raw)
    if (magic, version, total) != (0x46546C67, 2, len(raw)):
        raise ValueError("not a complete GLB 2.0")
    pos, metadata, binary = 12, None, None
    while pos < len(raw):
        size, kind = struct.unpack_from("<II", raw, pos)
        pos += 8
        chunk = raw[pos:pos + size]
        pos += size
        if len(chunk) != size:
            raise ValueError("truncated GLB chunk")
        if kind == JSON_CHUNK:
            metadata = json.loads(chunk.rstrip(b" \\x00").decode("utf-8"))
        elif kind == BIN_CHUNK:
            binary = chunk
    if pos != len(raw) or metadata is None or binary is None:
        raise ValueError("missing/invalid GLB chunks")
    return metadata, binary


def read_accessor(doc, binary, index):
    acc = doc["accessors"][index]
    view = doc["bufferViews"][acc["bufferView"]]
    ncomp = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4}[acc["type"]]
    fmt = {5121: "B", 5123: "H", 5125: "I", 5126: "f"}[acc["componentType"]]
    size = struct.calcsize("<" + fmt)
    stride = view.get("byteStride", size * ncomp)
    base = view.get("byteOffset", 0) + acc.get("byteOffset", 0)
    return [struct.unpack_from("<" + fmt * ncomp, binary, base + i * stride)
            for i in range(acc["count"])]


def covers_xy(tri, x, y):
    # Barycentric point-in-triangle in the source wall's XY plane.
    ax, ay = tri[0][:2]
    bx, by = tri[1][:2]
    cx, cy = tri[2][:2]
    det = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
    if abs(det) < 1e-10:
        return False
    u = ((by - cy) * (x - cx) + (cx - bx) * (y - cy)) / det
    v = ((cy - ay) * (x - cx) + (ax - cx) * (y - cy)) / det
    return u >= -1e-6 and v >= -1e-6 and (u + v) <= 1 + 1e-6


def audit(path: Path):
    doc, binary = glb(path)
    back = next((n for n in doc["nodes"] if n.get("name") == "room_shell_back"), None)
    if back is None:
        raise ValueError("back wall node is missing")
    mesh = doc["meshes"][back["mesh"]]
    coverage = []
    for primitive in mesh["primitives"]:
        vertices = read_accessor(doc, binary, primitive["attributes"]["POSITION"])
        faces = read_accessor(doc, binary, primitive["indices"])
        triangles = [(vertices[faces[i][0]], vertices[faces[i + 1][0]],
                      vertices[faces[i + 2][0]]) for i in range(0, len(faces), 3)]
        coverage.append(any(covers_xy(t, APERTURE_CENTER_X, APERTURE_CENTER_LOCAL_Y)
                            for t in triangles))
    result = {
        "derived_glb": str(path),
        "back_wall_exists": True,
        "sample_x": APERTURE_CENTER_X,
        "sample_world_height_y": APERTURE_CENTER_LOCAL_Y + back["translation"][1],
        "center_blocked": any(coverage),
        "candidate_aperture_structural": "PASS" if not any(coverage) else "FAIL",
        "visual_acceptance": "UNASSESSED",
        "canonical_furniture_glbs_changed": False,
    }
    print(json.dumps(result, indent=2))
    return 0 if not any(coverage) else 1


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("derived_room_shell_glb", type=Path)
    raise SystemExit(audit(parser.parse_args().derived_room_shell_glb))
