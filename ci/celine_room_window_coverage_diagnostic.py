#!/usr/bin/env python3
"""Read-only Room diagnostic: how much of the physical window hole is covered.

Works on a DERIVED, unaccepted window GLB, never on the twelve immutable
source furniture GLBs. Counts orthographic projected triangle layers per
material semantic label. Labels are hypotheses, not verified physical surfaces.
A structural or diagnostic PASS NEVER constitutes Android visual acceptance.
"""
from __future__ import annotations
import argparse
import json
import math
import struct
from pathlib import Path

APERTURE = (-0.4494766, 1.6462827, 0.594875, 2.205125)
COMPONENTS = {5121: ("B", 1), 5123: ("H", 2), 5125: ("I", 4), 5126: ("f", 4)}
DIMENSIONS = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4}


def read_glb(path):
    raw = path.read_bytes()
    if len(raw) < 28 or struct.unpack_from("<III", raw) != (0x46546c67, 2, len(raw)):
        raise ValueError("invalid GLB version or length")
    offset, document, blob = 12, None, None
    while offset < len(raw):
        if offset + 8 > len(raw):
            raise ValueError("truncated chunk header")
        length, kind = struct.unpack_from("<II", raw, offset)
        offset += 8
        if offset + length > len(raw):
            raise ValueError("truncated chunk")
        chunk = raw[offset:offset + length]
        offset += length
        if kind == 0x4E4F534A:
            document = json.loads(chunk.rstrip(b" \\x00").decode("utf-8"))
        elif kind == 0x004E4942:
            blob = chunk
    if offset != len(raw) or document is None or blob is None:
        raise ValueError("missing GLB data")
    return document, blob


def accessor(doc, data, index):
    item = doc["accessors"][index]
    view = doc["bufferViews"][item["bufferView"]]
    fmt, byte_size = COMPONENTS[item["componentType"]]
    dimension = DIMENSIONS[item["type"]]
    stride = view.get("byteStride", dimension * byte_size)
    if stride < dimension * byte_size:
        raise ValueError("bad accessor stride")
    base = view.get("byteOffset", 0) + item.get("byteOffset", 0)
    if base + max(0, item["count"] - 1) * stride + dimension * byte_size > len(data):
        raise ValueError("accessor outside BIN chunk")
    unpack = struct.Struct("<" + fmt * dimension).unpack_from
    return [unpack(data, base + row * stride) for row in range(item["count"])]


def paint(counts, points, a, b, c, nx, ny):
    ax, ay = points[a]
    bx, by = points[b]
    cx, cy = points[c]
    ux, uy, vx, vy = bx - ax, by - ay, cx - ax, cy - ay
    determinant = ux * vy - uy * vx
    if abs(determinant) < 1e-8:
        return
    x0 = max(0, int(math.floor(min(ax, bx, cx))))
    x1 = min(nx - 1, int(math.ceil(max(ax, bx, cx))))
    y0 = max(0, int(math.floor(min(ay, by, cy))))
    y1 = min(ny - 1, int(math.ceil(max(ay, by, cy))))
    if x0 > x1 or y0 > y1:
        return
    inverse = 1 / determinant
    for y in range(y0, y1 + 1):
        dy = y + .5 - ay
        for x in range(x0, x1 + 1):
            dx = x + .5 - ax
            u = (dx * vy - dy * vx) * inverse
            v = (ux * dy - uy * dx) * inverse
            if u >= -1e-5 and v >= -1e-5 and u + v <= 1.00001:
                counts[y * nx + x] += 1


def inspect(path, center_x=.575, center_height=1.4, scale=1.490625,
            yaw=8.4375, nx=100, ny=78):
    if scale <= 0 or nx < 4 or ny < 4:
        raise ValueError("invalid projected transform or grid")
    doc, data = read_glb(path)
    nodes = [node for node in doc.get("nodes", []) if node.get("name") == "mesh_node.010"]
    if len(nodes) != 1 or "mesh" not in nodes[0]:
        raise ValueError("original derived source-fidelity window node not found")
    xmin, xmax, ymin, ymax = APERTURE
    cos, sin = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    mesh = doc["meshes"][nodes[0]["mesh"]]
    counts, cache, triangles = {}, {}, 0
    for primitive in mesh["primitives"]:
        if primitive.get("mode", 4) != 4:
            raise ValueError("unsupported mesh topology")
        material_id = primitive.get("material")
        if not isinstance(material_id, int) or not 0 <= material_id < len(doc.get("materials", [])):
            raise ValueError("material missing")
        material = doc["materials"][material_id]
        name = (material.get("extras") or {}).get("celine_room_semantic_surface")
        if not isinstance(name, str):
            name = "UNVERIFIED_MATERIAL_" + str(material_id)
        if name not in counts:
            counts[name] = [0] * (nx * ny)
        position_id = primitive["attributes"]["POSITION"]
        if position_id not in cache:
            source = accessor(doc, data, position_id)
            cache[position_id] = [
                ((center_x + scale * (p[0] * cos + p[2] * sin) - xmin) / (xmax - xmin) * nx,
                 (center_height + scale * p[1] - ymin) / (ymax - ymin) * ny)
                for p in source
            ]
        projected = cache[position_id]
        indices = accessor(doc, data, primitive["indices"])
        if len(indices) % 3:
            raise ValueError("indices not triangles")
        for index in range(0, len(indices), 3):
            a, b, c = (indices[index + q][0] for q in range(3))
            if max(a, b, c) >= len(projected):
                raise ValueError("source index outside vertices")
            paint(counts[name], projected, a, b, c, nx, ny)
        triangles += len(indices) // 3
    metrics = {}
    for name, layers in sorted(counts.items()):
        positive = sorted(number for number in layers if number > 0)
        metrics[name] = {
            "fraction_aperture_covered": round(len(positive) / (nx * ny), 6),
            "median_layers_where_covered": positive[len(positive) // 2] if positive else None,
            "max_layers": positive[-1] if positive else 0,
        }
    union = sum(any(group[i] for group in counts.values()) for i in range(nx * ny))
    return {
        "derived_window": str(path),
        "grid": [nx, ny],
        "hole_world_x_height": list(APERTURE),
        "assumed_anchor": [center_x, center_height, scale, yaw],
        "original_window_triangle_count_expected": 499998,
        "triangle_count": triangles,
        "any_window_coverage_fraction": round(union / (nx * ny), 6),
        "semantic_material_labels_are_unverified": True,
        "semantic_coverage": metrics,
        "curtain_overcoverage_warning": metrics.get("curtain", {}).get(
            "fraction_aperture_covered", 0) > 0.65,
        "method": "orthographic projected source triangles, not Filament depth testing",
        "android_visual_acceptance": "UNASSESSED_REQUIRES_REAL_CALL_PROOF",
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("derived_window", type=Path)
    parser.add_argument("--center-x", type=float, default=.575)
    parser.add_argument("--center-height", type=float, default=1.4)
    parser.add_argument("--scale", type=float, default=1.490625)
    parser.add_argument("--yaw", type=float, default=8.4375)
    parser.add_argument("--grid-x", type=int, default=100)
    parser.add_argument("--grid-y", type=int, default=78)
    options = parser.parse_args()
    try:
        result = inspect(options.derived_window, options.center_x,
                         options.center_height, options.scale,
                         options.yaw, options.grid_x, options.grid_y)
        print(json.dumps(result, indent=2))
    except Exception as exc:
        print(json.dumps({"diagnostic": "FAIL", "reason": str(exc),
                          "android_visual_acceptance": "UNASSESSED"}, indent=2))
        raise SystemExit(1)


if __name__ == "__main__":
    main()
