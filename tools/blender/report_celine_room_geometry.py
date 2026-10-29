#!/usr/bin/env python3
"""Report exact mesh density for the builder-owned Celine room proof scene."""

import bpy
import json
import os
from pathlib import Path

ROOT_NAME = "room_world_root"
ANCHOR_PROP = "celine_room_instance_anchor"
OWNED_PROP = "celine_room_builder_owned"


def fail(message):
    print("CELINE_ROOM_GEOMETRY_PROFILE FAIL")
    raise RuntimeError(message)


def descendants(root):
    result = []
    stack = list(root.children)
    while stack:
        obj = stack.pop()
        result.append(obj)
        stack.extend(obj.children)
    return result


def mesh_metrics(objects):
    vertices = edges = polygons = triangles = loops = 0
    mesh_objects = 0
    seen_meshes = set()
    for obj in objects:
        if obj.type != "MESH" or obj.data is None:
            continue
        mesh_objects += 1
        mesh = obj.data
        # Imported instances in this builder are independent imports. Count each object
        # for scene cost, but avoid accidental double-counting of a shared mesh datablock
        # inside one instance hierarchy.
        key = mesh.as_pointer()
        if key in seen_meshes:
            continue
        seen_meshes.add(key)
        mesh.calc_loop_triangles()
        vertices += len(mesh.vertices)
        edges += len(mesh.edges)
        polygons += len(mesh.polygons)
        triangles += len(mesh.loop_triangles)
        loops += len(mesh.loops)
    return {
        "meshObjects": mesh_objects,
        "uniqueMeshDatablocks": len(seen_meshes),
        "vertices": vertices,
        "edges": edges,
        "polygons": polygons,
        "triangles": triangles,
        "loops": loops,
    }


def main():
    root = bpy.data.objects.get(ROOT_NAME)
    if root is None or not bool(root.get(OWNED_PROP, False)):
        fail("builder-owned room_world_root missing")

    anchors = [
        obj for obj in bpy.data.objects
        if bool(obj.get(OWNED_PROP, False)) and bool(obj.get(ANCHOR_PROP, False))
    ]
    if len(anchors) != 13:
        fail(f"expected 13 instance anchors, got {len(anchors)}")

    instances = []
    totals = {"meshObjects": 0, "uniqueMeshDatablocks": 0, "vertices": 0, "edges": 0, "polygons": 0, "triangles": 0, "loops": 0}
    for anchor in sorted(anchors, key=lambda item: item.name):
        metrics = mesh_metrics(descendants(anchor))
        row = {
            "instanceId": anchor.name.removesuffix("__anchor"),
            "sourceFile": str(anchor.get("source_file", "")),
            **metrics,
        }
        instances.append(row)
        for key in totals:
            totals[key] += metrics[key]

    proof_dir = Path(os.environ.get("CELINE_ROOM_PROOF_DIR", "ci-room-proof")).resolve()
    proof_dir.mkdir(parents=True, exist_ok=True)
    output = proof_dir / "geometry-profile.json"
    payload = {
        "schema": 1,
        "purpose": "source-fidelity recovery profile before selecting any bounded geometry reduction",
        "root": ROOT_NAME,
        "instances": instances,
        "sceneInstanceTotals": totals,
    }
    output.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    print("CELINE_ROOM_GEOMETRY_PROFILE PASS")
    print(f"instances={len(instances)}")
    print(f"triangles={totals['triangles']}")
    print(f"vertices={totals['vertices']}")
    for row in sorted(instances, key=lambda item: item["triangles"], reverse=True):
        print(f"{row['instanceId']} source={row['sourceFile']} triangles={row['triangles']} vertices={row['vertices']}")


main()
