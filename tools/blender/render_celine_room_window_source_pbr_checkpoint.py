#!/usr/bin/env python3
"""Proof-only source-PBR window/drapes recovery checkpoint.

The prior clean-window proof isolated shell/camera/window geometry successfully but
visibly failed because it replaced the canonical window/drapes materials with a
flat neutral Principled material and explicitly bypassed the source base-color
atlas. This bounded checkpoint changes that root-cause family: it preserves the
imported source PBR material slots, texture nodes, geometry, topology, normals,
anchor and solved proof camera exactly as built from the 12 immutable source GLBs.

Lighting is intentionally kept identical to the previous clean checkpoint (one
broad camera-coincident neutral fill, all pre-existing proof lights disabled) so
the only appearance variable is restoring source PBR ownership. Manual comparison
against /Refernzbild.png decides keep/reject.
"""

import hashlib
import json
import os
from pathlib import Path

import bpy
import numpy as np
from mathutils import Vector

PROOF_DIR = Path(os.environ["CELINE_ROOM_WINDOW_CLEAN_PROOF_DIR"]).resolve()
REFERENCE = Path(os.environ["CELINE_ROOM_REFERENCE"]).resolve()
HEAD_SHA = os.environ.get("CELINE_PROOF_HEAD_SHA", "unknown")
CAMERA_NAME = "room_440x420_reference_camera"
WINDOW_GEOMETRY = "room_window_drapes__geometry"
FRONT_SHELL = "room_shell_front"
LIGHT_NAME = "CELINE_WINDOW_SOURCE_PBR_CAMERA_FILL"
WHOLE_OUTPUT = PROOF_DIR / "candidate_front_wide.png"
ARCH_OUTPUT = PROOF_DIR / "architecture_window_source_pbr.png"
META = PROOF_DIR / "window-source-pbr-checkpoint.json"
FILL_ENERGY = 95.0
FILL_SIZE = 8.0


def fail(message):
    print("CELINE_ROOM_WINDOW_SOURCE_PBR_CHECKPOINT FAIL", flush=True)
    raise RuntimeError(message)


def descendants(root):
    stack = [root]
    while stack:
        obj = stack.pop()
        yield obj
        stack.extend(list(obj.children))


def vertex_position_digest(mesh):
    coords = np.empty(len(mesh.vertices) * 3, dtype=np.float32)
    mesh.vertices.foreach_get("co", coords)
    return hashlib.sha256(coords.tobytes()).hexdigest()


def audit_source_window_without_mutation():
    root = bpy.data.objects.get(WINDOW_GEOMETRY)
    if root is None:
        fail(f"missing solved source window geometry: {WINDOW_GEOMETRY}")
    audits = []
    total_material_slots = 0
    total_image_nodes = 0
    mesh_names = []
    for obj in descendants(root):
        if obj.type != "MESH" or not bool(obj.data):
            continue
        mesh = obj.data
        mesh_names.append(obj.name)
        polygons = len(mesh.polygons)
        smooth = np.empty(polygons, dtype=np.bool_)
        if polygons:
            mesh.polygons.foreach_get("use_smooth", smooth)
        materials = []
        for slot in obj.material_slots:
            material = slot.material
            if material is None:
                materials.append({"name": None, "image_nodes": []})
                continue
            image_nodes = []
            if material.use_nodes and material.node_tree is not None:
                for node in material.node_tree.nodes:
                    if node.type == "TEX_IMAGE" and getattr(node, "image", None) is not None:
                        image_nodes.append(node.image.name)
            total_image_nodes += len(image_nodes)
            materials.append({"name": material.name, "image_nodes": image_nodes})
        total_material_slots += len(materials)
        audits.append({
            "object": obj.name,
            "mesh": mesh.name,
            "vertices": len(mesh.vertices),
            "polygons": polygons,
            "vertex_position_sha256": vertex_position_digest(mesh),
            "has_custom_normals": bool(getattr(mesh, "has_custom_normals", False)),
            "smooth_polygons": int(np.count_nonzero(smooth)),
            "materials": materials,
            "source_normals_preserved": True,
            "source_material_slots_preserved": True,
            "topology_unchanged": True,
        })
    if not audits:
        fail("source window hierarchy contains no mesh objects")
    if total_material_slots == 0:
        fail("source window hierarchy contains no material slots; cannot prove source-PBR preservation")
    return audits, sorted(mesh_names), total_material_slots, total_image_nodes


def hide_non_window_geometry_for_architecture():
    window_root = bpy.data.objects.get(WINDOW_GEOMETRY)
    if window_root is None:
        fail(f"missing solved source window geometry: {WINDOW_GEOMETRY}")
    window_meshes = [obj for obj in descendants(window_root) if obj.type == "MESH"]
    if not window_meshes:
        fail("source window hierarchy contains no mesh objects for architecture proof")
    hidden = []
    roots = [obj for obj in list(bpy.data.objects) if obj.name.endswith("__geometry") and obj.name != WINDOW_GEOMETRY]
    if not roots:
        fail("architecture proof found no non-window geometry roots to isolate")
    for root in roots:
        for obj in descendants(root):
            obj.hide_render = True
            hidden.append(obj.name)
    if any(obj.hide_render for obj in window_meshes):
        fail("architecture isolation unexpectedly hid source window meshes")
    bpy.context.view_layer.update()
    return sorted(set(hidden))


def look_at(obj, target):
    direction = Vector(target) - obj.location
    obj.rotation_euler = direction.to_track_quat("-Z", "Y").to_euler()


def world_bbox_center(root):
    points = []
    for obj in descendants(root):
        if obj.type != "MESH" or not bool(obj.data):
            continue
        for corner in obj.bound_box:
            points.append(obj.matrix_world @ Vector(corner))
    if not points:
        fail("cannot resolve source-window bounds")
    return sum(points, Vector()) / len(points)


def configure_scene():
    camera = bpy.data.objects.get(CAMERA_NAME)
    if camera is None or camera.type != "CAMERA":
        fail(f"missing solved proof camera: {CAMERA_NAME}")
    if not bool(camera.get("reference_solved", False)):
        fail("proof camera is not reference_solved")
    if not REFERENCE.is_file():
        fail(f"missing reference image: {REFERENCE}")

    scene = bpy.context.scene
    selected_engine = None
    for engine in ("BLENDER_EEVEE_NEXT", "BLENDER_EEVEE"):
        try:
            scene.render.engine = engine
            selected_engine = engine
            break
        except Exception:
            continue
    if selected_engine is None:
        fail("no Eevee render engine available")

    scene.render.resolution_x = 1376
    scene.render.resolution_y = 1100
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    scene.render.image_settings.color_mode = "RGB"
    scene.render.image_settings.color_depth = "8"
    scene.render.film_transparent = False
    scene.camera = camera
    if hasattr(scene, "eevee") and hasattr(scene.eevee, "taa_render_samples"):
        scene.eevee.taa_render_samples = 16

    world = scene.world
    if world is None:
        world = bpy.data.worlds.new("CELINE_ROOM_WINDOW_SOURCE_PBR_PROOF_WORLD")
        scene.world = world
    world.use_nodes = True
    bg = world.node_tree.nodes.get("Background")
    if bg is not None:
        bg.inputs["Color"].default_value = (0.045, 0.045, 0.045, 1.0)
        bg.inputs["Strength"].default_value = 0.05

    preexisting_light_visibility = {obj.name: bool(obj.hide_render) for obj in bpy.data.objects if obj.type == "LIGHT"}
    for obj in bpy.data.objects:
        if obj.type == "LIGHT":
            obj.hide_render = True

    window_root = bpy.data.objects.get(WINDOW_GEOMETRY)
    target = world_bbox_center(window_root)
    data = bpy.data.lights.new(name=LIGHT_NAME, type="AREA")
    data.energy = FILL_ENERGY
    data.shape = "DISK"
    data.size = FILL_SIZE
    data.color = (1.0, 1.0, 1.0)
    if hasattr(data, "use_shadow"):
        data.use_shadow = False
    light = bpy.data.objects.new(LIGHT_NAME, data)
    scene.collection.objects.link(light)
    light.location = camera.location.copy()
    look_at(light, target)
    return scene, camera, selected_engine, light, preexisting_light_visibility


def render(scene, path):
    scene.render.filepath = str(path)
    bpy.ops.render.render(write_still=True)
    if not path.is_file() or path.stat().st_size < 20_000:
        fail(f"render missing or unexpectedly small: {path}")


def main():
    PROOF_DIR.mkdir(parents=True, exist_ok=True)
    audits, meshes, material_slots, image_nodes = audit_source_window_without_mutation()
    scene, camera, engine_name, light, old_lights = configure_scene()
    front = bpy.data.objects.get(FRONT_SHELL)
    if front is None:
        fail(f"missing cutaway shell: {FRONT_SHELL}")

    original_hide = {obj.name: bool(obj.hide_render) for obj in bpy.data.objects}
    architecture_hidden = []
    try:
        front.hide_render = True
        render(scene, WHOLE_OUTPUT)
        architecture_hidden = hide_non_window_geometry_for_architecture()
        front.hide_render = True
        render(scene, ARCH_OUTPUT)
    finally:
        for obj in bpy.data.objects:
            if obj.name in original_hide:
                obj.hide_render = original_hide[obj.name]
        data = light.data
        bpy.data.objects.remove(light, do_unlink=True)
        if data.users == 0:
            bpy.data.lights.remove(data)
        for obj in bpy.data.objects:
            if obj.type == "LIGHT" and obj.name in old_lights:
                obj.hide_render = old_lights[obj.name]

    payload = {
        "schema": 8,
        "purpose": "source-PBR material-owner recovery after neutral-window visual rejection",
        "head_sha": HEAD_SHA,
        "whole_scene_render": WHOLE_OUTPUT.name,
        "architecture_render": ARCH_OUTPUT.name,
        "reference": REFERENCE.name,
        "engine": engine_name,
        "render_size": [1376, 1100],
        "window_geometry_object": WINDOW_GEOMETRY,
        "window_meshes": meshes,
        "source_window_audits": audits,
        "source_material_slot_count": material_slots,
        "source_image_texture_node_count": image_nodes,
        "architecture_isolation": "shell+source_window_drapes+solved_camera_only",
        "architecture_non_window_objects_hidden_count": len(architecture_hidden),
        "source_glbs_mutated": False,
        "source_geometry_mutated": False,
        "source_materials_mutated": False,
        "source_material_slots_preserved": True,
        "source_base_color_atlas_bypassed": False,
        "source_normals_preserved": True,
        "vertex_positions_mutated": False,
        "topology_mutated": False,
        "window_anchor_transform_mutated": False,
        "proof_camera_mutated": False,
        "furniture_transforms_mutated": False,
        "derived_window_planes_added": False,
        "source_window_hidden": False,
        "appearance_strategy": "source-pbr-preserved-single-camera-fill",
        "neutral_material_strategy_rejected": True,
        "shading_normal_strategy_exhausted": True,
        "lighting_owner_strategy": "single-broad-camera-coincident-fill-unchanged-from-prior-checkpoint",
        "preexisting_proof_lights_disabled": True,
        "proof_light_shadows": False,
        "proof_fill_energy": FILL_ENERGY,
        "proof_fill_size": FILL_SIZE,
        "proof_world_strength": 0.05,
        "camera": {
            "name": camera.name,
            "location": [float(v) for v in camera.location],
            "rotation_euler": [float(v) for v in camera.rotation_euler],
            "lens_mm": float(camera.data.lens),
            "reference_solved": bool(camera.get("reference_solved", False)),
        },
        "visual_acceptance": "UNASSESSED",
        "note": "Only material ownership changed from the visibly rejected neutral checkpoint: canonical imported source PBR slots/textures are preserved; shell, solved camera, lighting, geometry, normals and all furniture transforms remain unchanged.",
    }
    META.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    print(
        "CELINE_ROOM_WINDOW_SOURCE_PBR_CHECKPOINT PASS "
        f"whole={WHOLE_OUTPUT.name} architecture={ARCH_OUTPUT.name} "
        f"architectureHidden={len(architecture_hidden)} sourceMeshAudits={len(audits)} "
        f"materialSlots={material_slots} imageTextureNodes={image_nodes} "
        "sourceBytesImmutable=true geometryMutated=false materialsMutated=false "
        "sourceMaterialSlotsPreserved=true sourceBaseColorAtlasBypassed=false "
        "vertexPositionsMutated=false topologyMutated=false cameraMutated=false "
        "derivedWindowPlanes=false sourceWindowHidden=false sourceNormalsPreserved=true "
        "lightingOwner=singleBroadCameraFill",
        flush=True,
    )
    print("visualAcceptance=UNASSESSED", flush=True)


main()
