#!/usr/bin/env python3
"""Proof-only flat-shading isolation for the v80 source window/drapes.

Controlled evidence established:
- source/neutral Principled lighting shows the diagonal curtain defect;
- the same geometry rendered unlit does not;
- discarding imported custom split normals alone does not improve it.

This is the second and final shading-normal-family isolation attempt. It clones
only the in-memory proof mesh, clears imported custom normals and disables smooth
normal interpolation on the clone while keeping vertex positions, polygon count,
anchor, solved camera, shell, furniture transforms, lighting and immutable GLB
bytes unchanged. The same neutral Principled material from the failed lit proof
is kept so the only meaningful variable is smooth-vs-face shading.

If this is not visibly better, the shading-normal strategy is exhausted and the
next recovery step must change root-cause family instead of stacking appearance
patches. Manual comparison against Refernzbild.png remains mandatory.
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
MATERIAL_NAME = "CELINE_440_WindowDrapesCleanNeutral"
WHOLE_OUTPUT = PROOF_DIR / "candidate_front_wide.png"
ARCH_OUTPUT = PROOF_DIR / "architecture_window_clean.png"
META = PROOF_DIR / "window-clean-checkpoint.json"
NEUTRAL_COLOR = (0.34, 0.27, 0.21, 1.0)
ROUGHNESS = 0.88


def fail(message):
    print("CELINE_ROOM_WINDOW_CLEAN_CHECKPOINT FAIL", flush=True)
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


def clone_and_force_face_shading():
    root = bpy.data.objects.get(WINDOW_GEOMETRY)
    if root is None:
        fail(f"missing solved source window geometry: {WINDOW_GEOMETRY}")

    audits = []
    for obj in descendants(root):
        if obj.type != "MESH" or not bool(obj.data):
            continue
        source_mesh = obj.data
        before_vertices = len(source_mesh.vertices)
        before_polygons = len(source_mesh.polygons)
        before_digest = vertex_position_digest(source_mesh)
        had_custom_normals = bool(getattr(source_mesh, "has_custom_normals", False))
        smooth_before = np.empty(before_polygons, dtype=np.bool_)
        source_mesh.polygons.foreach_get("use_smooth", smooth_before)

        # The canonical imported source datablock remains untouched. Only this
        # proof object's in-memory mesh clone receives shading-flag changes.
        proof_mesh = source_mesh.copy()
        proof_mesh.name = f"{source_mesh.name}__proof_flat_face_shading"
        obj.data = proof_mesh

        custom_normals_cleared = False
        if hasattr(proof_mesh, "free_normals_split"):
            try:
                proof_mesh.free_normals_split()
                custom_normals_cleared = had_custom_normals
            except RuntimeError:
                custom_normals_cleared = False

        if before_polygons:
            proof_mesh.polygons.foreach_set(
                "use_smooth", np.zeros(before_polygons, dtype=np.bool_)
            )
        proof_mesh.update()

        after_digest = vertex_position_digest(proof_mesh)
        smooth_after = np.empty(before_polygons, dtype=np.bool_)
        proof_mesh.polygons.foreach_get("use_smooth", smooth_after)
        if before_vertices != len(proof_mesh.vertices):
            fail(f"face-shading proof changed vertex count for {obj.name}")
        if before_polygons != len(proof_mesh.polygons):
            fail(f"face-shading proof changed polygon count for {obj.name}")
        if before_digest != after_digest:
            fail(f"face-shading proof changed vertex positions for {obj.name}")
        if bool(np.any(smooth_after)):
            fail(f"face-shading proof left smooth polygons enabled for {obj.name}")

        obj["reference_window_flat_face_shading"] = True
        obj["reference_window_source_mesh"] = source_mesh.name
        audits.append(
            {
                "object": obj.name,
                "source_mesh": source_mesh.name,
                "proof_mesh": proof_mesh.name,
                "had_custom_normals": had_custom_normals,
                "custom_normals_cleared": custom_normals_cleared,
                "smooth_polygons_before": int(np.count_nonzero(smooth_before)),
                "smooth_polygons_after": int(np.count_nonzero(smooth_after)),
                "vertices": before_vertices,
                "polygons": before_polygons,
                "vertex_position_sha256_before": before_digest,
                "vertex_position_sha256_after": after_digest,
                "vertex_positions_unchanged": True,
                "topology_unchanged": True,
                "proof_flat_face_shading": True,
            }
        )

    if not audits:
        fail("source window hierarchy contains no mesh objects for face-shading isolation")
    bpy.context.view_layer.update()
    return audits


def make_neutral_principled_material():
    existing = bpy.data.materials.get(MATERIAL_NAME)
    if existing is not None and not bool(existing.get("celine_room_builder_owned", False)):
        fail(f"refusing to replace non-room-owned material {MATERIAL_NAME}")
    material = existing if existing is not None else bpy.data.materials.new(MATERIAL_NAME)
    material["celine_room_builder_owned"] = True
    material["reference_clean_window_checkpoint"] = True
    material["source_glb_bytes_mutated"] = False
    material["source_base_color_atlas_bypassed"] = True
    material["appearance_strategy"] = "uniform-neutral-principled-flat-face-shading"
    material.use_nodes = True
    nodes = material.node_tree.nodes
    links = material.node_tree.links
    nodes.clear()
    output = nodes.new("ShaderNodeOutputMaterial")
    bsdf = nodes.new("ShaderNodeBsdfPrincipled")
    bsdf.inputs["Base Color"].default_value = NEUTRAL_COLOR
    bsdf.inputs["Metallic"].default_value = 0.0
    bsdf.inputs["Roughness"].default_value = ROUGHNESS
    if "Specular" in bsdf.inputs:
        bsdf.inputs["Specular"].default_value = 0.24
    if "Specular IOR Level" in bsdf.inputs:
        bsdf.inputs["Specular IOR Level"].default_value = 0.24
    links.new(bsdf.outputs["BSDF"], output.inputs["Surface"])
    material.diffuse_color = NEUTRAL_COLOR
    return material


def apply_neutral_principled_material():
    root = bpy.data.objects.get(WINDOW_GEOMETRY)
    if root is None:
        fail(f"missing solved source window geometry: {WINDOW_GEOMETRY}")
    material = make_neutral_principled_material()
    meshes = []
    for obj in descendants(root):
        if obj.type != "MESH" or not bool(obj.data):
            continue
        obj.data.materials.clear()
        obj.data.materials.append(material)
        obj["reference_clean_window_material"] = material.name
        meshes.append(obj.name)
    if not meshes:
        fail("source window hierarchy contains no mesh objects")
    bpy.context.view_layer.update()
    return meshes


def hide_non_window_geometry_for_architecture():
    window_root = bpy.data.objects.get(WINDOW_GEOMETRY)
    if window_root is None:
        fail(f"missing solved source window geometry: {WINDOW_GEOMETRY}")
    window_meshes = [obj for obj in descendants(window_root) if obj.type == "MESH"]
    if not window_meshes:
        fail("source window hierarchy contains no mesh objects for architecture proof")

    hidden = []
    geometry_roots = [
        obj for obj in list(bpy.data.objects)
        if obj.name.endswith("__geometry") and obj.name != WINDOW_GEOMETRY
    ]
    if not geometry_roots:
        fail("architecture proof found no non-window geometry roots to isolate")
    for root in geometry_roots:
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
        world = bpy.data.worlds.new("CELINE_ROOM_WINDOW_CLEAN_PROOF_WORLD")
        scene.world = world
    world.use_nodes = True
    bg = world.node_tree.nodes.get("Background")
    if bg is not None:
        bg.inputs["Color"].default_value = (0.055, 0.055, 0.055, 1.0)
        bg.inputs["Strength"].default_value = 0.08

    lights = []
    specs = (
        ("CELINE_WINDOW_CLEAN_KEY", (0.4, 1.45, 2.45), 220.0, 3.4),
        ("CELINE_WINDOW_CLEAN_FILL", (-1.75, 0.15, 1.85), 120.0, 2.8),
        ("CELINE_WINDOW_CLEAN_WINDOW_FILL", (1.25, -1.72, 2.20), 80.0, 2.4),
    )
    for name, location, energy, size in specs:
        data = bpy.data.lights.new(name=name, type="AREA")
        data.energy = energy
        data.shape = "DISK"
        data.size = size
        data.color = (1.0, 1.0, 1.0)
        if hasattr(data, "use_shadow"):
            data.use_shadow = False
        obj = bpy.data.objects.new(name, data)
        scene.collection.objects.link(obj)
        obj.location = location
        look_at(obj, (0.0, -0.15, 1.0))
        lights.append(obj)
    return scene, camera, selected_engine, lights


def render(scene, path):
    scene.render.filepath = str(path)
    bpy.ops.render.render(write_still=True)
    if not path.is_file() or path.stat().st_size < 20_000:
        fail(f"render missing or unexpectedly small: {path}")


def main():
    PROOF_DIR.mkdir(parents=True, exist_ok=True)
    shading_audits = clone_and_force_face_shading()
    meshes = apply_neutral_principled_material()
    scene, camera, engine_name, lights = configure_scene()
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
        for obj in lights:
            data = obj.data
            bpy.data.objects.remove(obj, do_unlink=True)
            if data.users == 0:
                bpy.data.lights.remove(data)

    payload = {
        "schema": 6,
        "purpose": "final shading-normal-family isolation: disable smooth normal interpolation on proof-only window mesh clone",
        "head_sha": HEAD_SHA,
        "whole_scene_render": WHOLE_OUTPUT.name,
        "architecture_render": ARCH_OUTPUT.name,
        "reference": REFERENCE.name,
        "engine": engine_name,
        "render_size": [1376, 1100],
        "window_geometry_object": WINDOW_GEOMETRY,
        "window_meshes": meshes,
        "window_face_shading_audits": shading_audits,
        "architecture_isolation": "shell+source_window_drapes+solved_camera_only",
        "architecture_non_window_objects_hidden_count": len(architecture_hidden),
        "source_glbs_mutated": False,
        "source_geometry_mutated": False,
        "proof_mesh_datablocks_cloned": True,
        "proof_custom_normals_cleared": True,
        "proof_flat_face_shading": True,
        "vertex_positions_mutated": False,
        "topology_mutated": False,
        "window_anchor_transform_mutated": False,
        "proof_camera_mutated": False,
        "furniture_transforms_mutated": False,
        "derived_window_planes_added": False,
        "source_window_hidden": False,
        "source_base_color_atlas_bypassed": True,
        "clean_material": MATERIAL_NAME,
        "clean_material_strategy": "uniform-neutral-principled-flat-face-shading",
        "normal_and_light_response_removed": False,
        "neutral_color_linear": list(NEUTRAL_COLOR),
        "roughness": ROUGHNESS,
        "material_color_strategy_exhausted": True,
        "shading_normal_strategy_final_attempt": True,
        "proof_light_shadows": False,
        "proof_world_strength": 0.08,
        "proof_area_light_energies": [220.0, 120.0, 80.0],
        "camera": {
            "name": camera.name,
            "location": [float(v) for v in camera.location],
            "rotation_euler": [float(v) for v in camera.rotation_euler],
            "lens_mm": float(camera.data.lens),
            "reference_solved": bool(camera.get("reference_solved", False)),
        },
        "visual_acceptance": "UNASSESSED",
        "note": "Same neutral lit checkpoint as prior attempts, but proof-clone smooth shading is disabled. If the diagonal defect persists, leave the shading-normal family and change root cause.",
    }
    META.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    print(
        "CELINE_ROOM_WINDOW_CLEAN_CHECKPOINT PASS "
        f"whole={WHOLE_OUTPUT.name} architecture={ARCH_OUTPUT.name} "
        f"architectureHidden={len(architecture_hidden)} flatFaceMeshes={len(shading_audits)} "
        "sourceBytesImmutable=true geometryMutated=false vertexPositionsMutated=false "
        "topologyMutated=false cameraMutated=false derivedWindowPlanes=false "
        "sourceWindowHidden=false normalLightResponse=true flatFaceShading=true",
        flush=True,
    )
    print("visualAcceptance=UNASSESSED", flush=True)


main()
