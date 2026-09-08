#!/usr/bin/env python3
"""Export a reduced source-PBR recovery room as bounded GLB partitions.

This is proof-only recovery tooling. It runs after the canonical 440x420 room has
been built, reference-solved, and reduced in-memory by
apply_celine_room_source_fidelity_budget.py. It never mutates the 12 pinned source
GLBs and deliberately avoids the already-rejected strategy of exporting the whole
~4M-triangle scene through one Blender glTF invocation.

Each furniture instance is exported independently from its canonical anchor and
one additional partition contains only the six builder-owned shell meshes. The
result is evidence for whether clean source-fidelity geometry can cross Blender's
export boundary in bounded chunks before any Runtime promotion is attempted.
"""

from __future__ import annotations

import hashlib
import json
import os
import struct
from pathlib import Path

import bpy


ROOT_NAME = "room_world_root"
OWNED_PROP = "celine_room_builder_owned"
INSTANCE_IDS = (
    "room_bed",
    "room_dresser",
    "room_plant_large",
    "room_plant_small",
    "room_floor_lamp",
    "room_nightstand_rear",
    "room_nightstand_front",
    "room_lounge_chair",
    "room_rug",
    "room_foreground_table",
    "room_window_drapes",
    "room_wall_shelf_books",
    "room_round_mirror",
)
SHELL_OBJECTS = (
    "room_shell_floor",
    "room_shell_ceiling",
    "room_shell_left",
    "room_shell_right",
    "room_shell_back",
    "room_shell_front",
)


def fail(message: str) -> None:
    print("CELINE_ROOM_PARTITIONED_PBR_EXPORT FAIL", flush=True)
    print(message, flush=True)
    raise RuntimeError(message)


def proof_dir() -> Path:
    path = Path(os.environ.get("CELINE_ROOM_PROOF_DIR", "ci-room-proof")).resolve()
    path.mkdir(parents=True, exist_ok=True)
    return path


def partition_dir() -> Path:
    path = Path(
        os.environ.get(
            "CELINE_ROOM_PARTITION_DIR",
            str(proof_dir() / "partitioned-pbr"),
        )
    ).resolve()
    path.mkdir(parents=True, exist_ok=True)
    for old in path.glob("*.glb"):
        old.unlink()
    return path


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(8 * 1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def validate_glb(path: Path) -> dict[str, object]:
    data = path.read_bytes()
    if len(data) < 20 or data[:4] != b"glTF":
        fail(f"invalid GLB magic: {path}")
    version, declared = struct.unpack_from("<II", data, 4)
    if version != 2 or declared != len(data):
        fail(
            f"invalid GLB header version={version} declared={declared} actual={len(data)} path={path}"
        )
    json_length, json_type = struct.unpack_from("<II", data, 12)
    if json_type != 0x4E4F534A:
        fail(f"first GLB chunk is not JSON: {path}")
    start = 20
    end = start + json_length
    payload = json.loads(data[start:end].decode("utf-8").rstrip("\x00 \t\r\n"))
    return payload


def descendants(root: bpy.types.Object) -> list[bpy.types.Object]:
    out: list[bpy.types.Object] = []
    stack = list(root.children)
    while stack:
        obj = stack.pop()
        out.append(obj)
        stack.extend(obj.children)
    return out


def triangle_count(objects: list[bpy.types.Object]) -> int:
    total = 0
    for obj in objects:
        if obj.type != "MESH" or obj.data is None:
            continue
        obj.data.calc_loop_triangles()
        total += len(obj.data.loop_triangles)
    return total


def resize_owned_images(max_edge: int) -> list[dict[str, object]]:
    rows: list[dict[str, object]] = []
    for image in sorted(bpy.data.images, key=lambda item: item.name):
        if not bool(image.get(OWNED_PROP, False)):
            continue
        width, height = int(image.size[0]), int(image.size[1])
        row: dict[str, object] = {
            "name": image.name,
            "before": [width, height],
            "after": [width, height],
            "resized": False,
        }
        if width > 0 and height > 0 and max(width, height) > max_edge:
            scale = float(max_edge) / float(max(width, height))
            new_width = max(1, int(round(width * scale)))
            new_height = max(1, int(round(height * scale)))
            image.scale(new_width, new_height)
            row["after"] = [new_width, new_height]
            row["resized"] = True
        rows.append(row)
    return rows


def export_kwargs(path: Path) -> dict[str, object]:
    supported = set(bpy.ops.export_scene.gltf.get_rna_type().properties.keys())
    requested: dict[str, object] = {
        "filepath": str(path),
        "export_format": "GLB",
        "use_selection": True,
        "export_cameras": False,
        "export_lights": False,
        "export_materials": "EXPORT",
        "export_image_format": "AUTO",
        "export_texcoords": True,
        "export_normals": True,
        "export_extras": True,
        "export_yup": True,
        "export_apply": False,
        "export_draco_mesh_compression_enable": False,
    }
    return {key: value for key, value in requested.items() if key in supported}


def select_only(objects: list[bpy.types.Object], active: bpy.types.Object) -> None:
    bpy.ops.object.select_all(action="DESELECT")
    for obj in objects:
        if not bool(obj.get(OWNED_PROP, False)):
            fail(f"refusing to export non-builder-owned object: {obj.name}")
        obj.hide_set(False)
        obj.hide_viewport = False
        obj.select_set(True)
    bpy.context.view_layer.objects.active = active


def export_partition(
    part_id: str,
    objects: list[bpy.types.Object],
    active: bpy.types.Object,
    output_dir: Path,
    forbidden_name_fragments: tuple[str, ...],
) -> dict[str, object]:
    if not objects:
        fail(f"partition {part_id} has no objects")
    tris = triangle_count(objects)
    if part_id != "room_shell" and tris <= 0:
        fail(f"partition {part_id} has no mesh triangles")

    output = output_dir / f"{part_id}.glb"
    if output.exists():
        output.unlink()
    select_only(objects, active)
    try:
        result = bpy.ops.export_scene.gltf(**export_kwargs(output))
    finally:
        bpy.ops.object.select_all(action="DESELECT")
    if "FINISHED" not in result or not output.is_file() or output.stat().st_size < 128:
        fail(f"partition export failed: id={part_id} result={result} path={output}")

    payload = validate_glb(output)
    node_names = [str(node.get("name", "")) for node in payload.get("nodes", [])]
    for fragment in forbidden_name_fragments:
        if any(fragment in name for name in node_names):
            fail(f"partition {part_id} leaked forbidden node family {fragment}: {node_names[:30]}")

    row = {
        "id": part_id,
        "file": output.name,
        "bytes": output.stat().st_size,
        "sha256": sha256_file(output),
        "selected_object_count": len(objects),
        "triangles": tris,
        "glb_nodes": len(payload.get("nodes", [])),
        "glb_meshes": len(payload.get("meshes", [])),
        "glb_materials": len(payload.get("materials", [])),
        "glb_images": len(payload.get("images", [])),
        "glb_textures": len(payload.get("textures", [])),
    }
    print(
        "CELINE_ROOM_PARTITIONED_PBR_ITEM "
        f"id={part_id} triangles={tris} bytes={row['bytes']} nodes={row['glb_nodes']} "
        f"meshes={row['glb_meshes']} images={row['glb_images']}",
        flush=True,
    )
    return row


def main() -> None:
    max_edge = int(os.environ.get("CELINE_ROOM_RECOVERY_TEXTURE_MAX", "1024"))
    if max_edge < 256 or max_edge > 4096:
        fail(f"unsafe texture max edge: {max_edge}")

    root = bpy.data.objects.get(ROOT_NAME)
    if root is None or not bool(root.get(OWNED_PROP, False)):
        fail(f"missing builder-owned root: {ROOT_NAME}")

    missing_shell = [name for name in SHELL_OBJECTS if bpy.data.objects.get(name) is None]
    if missing_shell:
        fail(f"missing shell objects: {missing_shell}")
    missing_instances = [
        instance_id
        for instance_id in INSTANCE_IDS
        if bpy.data.objects.get(f"{instance_id}__anchor") is None
    ]
    if missing_instances:
        fail(f"missing canonical instance anchors: {missing_instances}")

    images = resize_owned_images(max_edge)
    output_dir = partition_dir()
    records: list[dict[str, object]] = []

    shell_objects = [root] + [bpy.data.objects[name] for name in SHELL_OBJECTS]
    records.append(
        export_partition(
            "room_shell",
            shell_objects,
            root,
            output_dir,
            tuple(f"{instance_id}__" for instance_id in INSTANCE_IDS),
        )
    )

    for instance_id in INSTANCE_IDS:
        anchor = bpy.data.objects.get(f"{instance_id}__anchor")
        if anchor is None:
            fail(f"missing anchor for {instance_id}")
        objects = [anchor] + descendants(anchor)
        sibling_fragments = tuple(
            f"{other}__" for other in INSTANCE_IDS if other != instance_id
        )
        records.append(
            export_partition(
                instance_id,
                objects,
                anchor,
                output_dir,
                sibling_fragments,
            )
        )

    if len(records) != 14:
        fail(f"partition count mismatch: {len(records)}, expected 14")
    total_triangles = sum(int(row["triangles"]) for row in records)
    total_bytes = sum(int(row["bytes"]) for row in records)
    if total_triangles < 3_000_000:
        fail(f"unexpectedly low partitioned triangle total: {total_triangles}")

    resized = [row for row in images if bool(row["resized"])]
    report = {
        "schema": 1,
        "purpose": "proof-only bounded export after combined Blender export/reload STOP-LOSS",
        "strategy": "one shell GLB plus one GLB per canonical furniture instance; no combined export",
        "source_glbs_mutated": False,
        "runtime_promoted": False,
        "texture_strategy": "separate source PBR images; proportional in-memory resize only; no atlas",
        "texture_max_edge": max_edge,
        "owned_image_count": len(images),
        "resized_image_count": len(resized),
        "partition_count": len(records),
        "total_triangles": total_triangles,
        "total_glb_bytes": total_bytes,
        "partitions": records,
    }
    manifest = proof_dir() / "partitioned-pbr-export.json"
    manifest.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    print("CELINE_ROOM_PARTITIONED_PBR_EXPORT PASS", flush=True)
    print(
        f"partitions={len(records)} triangles={total_triangles} bytes={total_bytes} "
        f"images={len(images)} resized={len(resized)} sourceGlbsMutated=false runtimePromoted=false",
        flush=True,
    )


main()
