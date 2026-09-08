#!/usr/bin/env python3
"""Export a bounded source-PBR recovery room from the immutable canonical furniture GLBs.

This script runs after build_celine_room_440x420.py and before the screen-space solver.
It does not modify any source GLB. Imported source textures are resized only in Blender's
in-memory working scene, remain separate (no atlas), and are embedded into a derived GLB.
"""

import bpy
import hashlib
import json
import os
import struct
from pathlib import Path

COLLECTION_NAME = "Celine_Room_440x420"
ROOT_NAME = "room_world_root"
OWNED_PROP = "celine_room_builder_owned"
EXPECTED_INSTANCE_ROOTS = (
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


def fail(message):
    print("CELINE_ROOM_SOURCE_PBR_EXPORT FAIL")
    print(message)
    raise RuntimeError(message)


def sha256_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(8 * 1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def validate_glb(path):
    with path.open("rb") as handle:
        header = handle.read(12)
    if len(header) != 12 or header[:4] != b"glTF":
        fail(f"invalid GLB magic: {path}")
    version, declared = struct.unpack("<II", header[4:12])
    actual = path.stat().st_size
    if version != 2 or declared != actual:
        fail(f"invalid GLB header version={version} declared={declared} actual={actual}")


def resize_owned_images(max_edge):
    report = []
    for image in sorted(bpy.data.images, key=lambda item: item.name):
        if not bool(image.get(OWNED_PROP, False)):
            continue
        width, height = int(image.size[0]), int(image.size[1])
        row = {
            "name": image.name,
            "source": image.filepath or image.filepath_raw or "",
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
        report.append(row)
    return report


def export_room():
    proof_dir = Path(os.environ.get("CELINE_ROOM_PROOF_DIR", "ci-room-proof")).resolve()
    proof_dir.mkdir(parents=True, exist_ok=True)
    output = proof_dir / "celine_room_v80_source_pbr_recovery.glb"
    manifest_path = proof_dir / "source-pbr-recovery-export.json"
    max_edge = int(os.environ.get("CELINE_ROOM_RECOVERY_TEXTURE_MAX", "1024"))
    if max_edge < 256 or max_edge > 4096:
        fail(f"unsafe texture max edge: {max_edge}")

    collection = bpy.data.collections.get(COLLECTION_NAME)
    root = bpy.data.objects.get(ROOT_NAME)
    if collection is None or root is None or not bool(root.get(OWNED_PROP, False)):
        fail("canonical builder-owned room collection/root missing")

    missing = [name for name in EXPECTED_INSTANCE_ROOTS if bpy.data.objects.get(name) is None]
    if missing:
        fail(f"missing canonical instance roots: {missing}")

    image_report = resize_owned_images(max_edge)

    bpy.ops.object.select_all(action="DESELECT")
    selected = []
    for obj in collection.all_objects:
        if not bool(obj.get(OWNED_PROP, False)):
            continue
        if obj.type in {"CAMERA", "LIGHT"}:
            continue
        obj.hide_set(False)
        obj.hide_viewport = False
        obj.select_set(True)
        selected.append(obj)
    if not selected:
        fail("no builder-owned room objects selected for export")
    bpy.context.view_layer.objects.active = root

    supported = set(bpy.ops.export_scene.gltf.get_rna_type().properties.keys())
    requested = {
        "filepath": str(output),
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
    kwargs = {key: value for key, value in requested.items() if key in supported}
    result = bpy.ops.export_scene.gltf(**kwargs)
    if "FINISHED" not in result or not output.is_file():
        fail(f"Blender glTF export failed: {result}")

    validate_glb(output)
    resized = [row for row in image_report if row["resized"]]
    manifest = {
        "schema": 1,
        "purpose": "ROOM recovery root-cause candidate; derived from immutable canonical source GLBs",
        "source_mutation": False,
        "texture_strategy": "separate source PBR images; in-memory proportional downscale only; no atlas",
        "texture_max_edge": max_edge,
        "instance_roots": list(EXPECTED_INSTANCE_ROOTS),
        "selected_object_count": len(selected),
        "owned_image_count": len(image_report),
        "resized_image_count": len(resized),
        "bytes": output.stat().st_size,
        "sha256": sha256_file(output),
        "images": image_report,
    }
    manifest_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    print("CELINE_ROOM_SOURCE_PBR_EXPORT PASS")
    print(f"output={output}")
    print(f"bytes={manifest['bytes']}")
    print(f"sha256={manifest['sha256']}")
    print(f"images={manifest['owned_image_count']} resized={manifest['resized_image_count']} max_edge={max_edge}")


export_room()
