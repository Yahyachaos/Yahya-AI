#!/usr/bin/env python3
"""Profile a GLB without importing it into Blender.

ROOM recovery helper: reports mesh/primitive/triangle/material/texture counts for a
committed or proof-generated derived room asset. It never mutates the GLB.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import struct
from pathlib import Path


def fail(message: str) -> None:
    raise SystemExit(f"CELINE_ROOM_GLB_PROFILE FAIL: {message}")


def read_glb(path: Path) -> dict:
    data = path.read_bytes()
    if len(data) < 20 or data[:4] != b"glTF":
        fail(f"invalid GLB magic/header: {path}")
    version, declared = struct.unpack_from("<II", data, 4)
    if version != 2 or declared != len(data):
        fail(f"invalid GLB header version={version} declared={declared} actual={len(data)}")

    offset = 12
    chunks = []
    while offset + 8 <= len(data):
        length, chunk_type = struct.unpack_from("<II", data, offset)
        offset += 8
        end = offset + length
        if end > len(data):
            fail("chunk exceeds declared GLB length")
        chunks.append((chunk_type, data[offset:end]))
        offset = end
    if offset != len(data) or not chunks:
        fail("invalid GLB chunk table")

    json_chunks = [payload for chunk_type, payload in chunks if chunk_type == 0x4E4F534A]
    if len(json_chunks) != 1:
        fail(f"expected one JSON chunk, got {len(json_chunks)}")
    try:
        return json.loads(json_chunks[0].decode("utf-8").rstrip(" \t\r\n\x00"))
    except Exception as exc:
        fail(f"invalid glTF JSON chunk: {exc}")


def accessor_count(doc: dict, accessor_index: int | None) -> int:
    if accessor_index is None:
        return 0
    accessors = doc.get("accessors", [])
    if accessor_index < 0 or accessor_index >= len(accessors):
        fail(f"accessor index out of range: {accessor_index}")
    count = accessors[accessor_index].get("count")
    if not isinstance(count, int) or count < 0:
        fail(f"invalid accessor count at {accessor_index}: {count}")
    return count


def primitive_vertex_count(doc: dict, primitive: dict) -> int:
    attributes = primitive.get("attributes", {})
    position = attributes.get("POSITION") if isinstance(attributes, dict) else None
    return accessor_count(doc, position)


def primitive_element_count(doc: dict, primitive: dict) -> int:
    if "indices" in primitive:
        return accessor_count(doc, primitive.get("indices"))
    return primitive_vertex_count(doc, primitive)


def primitive_triangle_count(doc: dict, primitive: dict) -> int:
    count = primitive_element_count(doc, primitive)
    mode = int(primitive.get("mode", 4))
    if mode == 4:  # TRIANGLES
        if count % 3:
            fail(f"TRIANGLES primitive element count not divisible by 3: {count}")
        return count // 3
    if mode in (5, 6):  # TRIANGLE_STRIP / TRIANGLE_FAN
        return max(0, count - 2)
    return 0


def profile(path: Path) -> dict:
    doc = read_glb(path)
    meshes = doc.get("meshes", [])
    mesh_rows = []
    total_primitives = 0
    total_triangles = 0
    total_position_vertices = 0
    non_triangle_modes = {}

    for mesh_index, mesh in enumerate(meshes):
        mesh_triangles = 0
        mesh_vertices = 0
        primitives = mesh.get("primitives", [])
        for primitive in primitives:
            total_primitives += 1
            mode = int(primitive.get("mode", 4))
            if mode not in (4, 5, 6):
                non_triangle_modes[str(mode)] = non_triangle_modes.get(str(mode), 0) + 1
            triangles = primitive_triangle_count(doc, primitive)
            vertices = primitive_vertex_count(doc, primitive)
            mesh_triangles += triangles
            mesh_vertices += vertices
        total_triangles += mesh_triangles
        total_position_vertices += mesh_vertices
        mesh_rows.append({
            "mesh_index": mesh_index,
            "name": mesh.get("name", ""),
            "primitive_count": len(primitives),
            "position_vertices": mesh_vertices,
            "triangles": mesh_triangles,
        })

    nodes = doc.get("nodes", [])
    node_rows = []
    for node_index, node in enumerate(nodes):
        mesh_index = node.get("mesh")
        if not isinstance(mesh_index, int) or mesh_index < 0 or mesh_index >= len(mesh_rows):
            continue
        mesh_row = mesh_rows[mesh_index]
        node_rows.append({
            "node_index": node_index,
            "name": node.get("name", ""),
            "mesh_index": mesh_index,
            "mesh_name": mesh_row["name"],
            "triangles": mesh_row["triangles"],
            "position_vertices": mesh_row["position_vertices"],
        })
    node_rows.sort(key=lambda row: (-row["triangles"], row["name"], row["node_index"]))

    return {
        "schema": 1,
        "purpose": "ROOM recovery read-only derived-GLB geometry/appearance profile",
        "path": str(path),
        "bytes": path.stat().st_size,
        "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        "asset": doc.get("asset", {}),
        "scene_count": len(doc.get("scenes", [])),
        "node_count": len(nodes),
        "mesh_count": len(meshes),
        "primitive_count": total_primitives,
        "position_vertices_across_mesh_primitives": total_position_vertices,
        "triangles_across_meshes": total_triangles,
        "non_triangle_primitive_modes": non_triangle_modes,
        "material_count": len(doc.get("materials", [])),
        "texture_count": len(doc.get("textures", [])),
        "image_count": len(doc.get("images", [])),
        "sampler_count": len(doc.get("samplers", [])),
        "buffer_count": len(doc.get("buffers", [])),
        "buffer_view_count": len(doc.get("bufferViews", [])),
        "accessor_count": len(doc.get("accessors", [])),
        "extensions_used": doc.get("extensionsUsed", []),
        "extensions_required": doc.get("extensionsRequired", []),
        "top_mesh_nodes_by_triangles": node_rows[:30],
        "meshes": mesh_rows,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("glb", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if not args.glb.is_file():
        fail(f"missing GLB: {args.glb}")
    result = profile(args.glb)
    text = json.dumps(result, indent=2, ensure_ascii=False) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(text, encoding="utf-8")
    else:
        print(text, end="")
    print(
        "CELINE_ROOM_GLB_PROFILE PASS "
        f"triangles={result['triangles_across_meshes']} "
        f"meshes={result['mesh_count']} materials={result['material_count']} "
        f"textures={result['texture_count']} bytes={result['bytes']}"
    )


if __name__ == "__main__":
    main()
