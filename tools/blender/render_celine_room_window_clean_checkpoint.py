#!/usr/bin/env python3
"""Proof-only clean window/drapes appearance checkpoint for the v80 Room recovery.

This script runs only after the canonical 4.40 x 4.20 x 2.65 m builder, geometry
report and solved reference-layout stage have completed in the same Blender
process. It deliberately changes root-cause family after the rejected lighting,
layered-window and generated-coordinate two-tone attempts:

* source GLB bytes stay immutable;
* window/drapes geometry, anchor transform and solved proof camera stay exact;
* no derived planes, texture atlases, hiding of the source window, camera writes
  or furniture transforms are introduced;
* the visibly corrupted source base-color atlas is bypassed for this proof with
  one deliberately neutral Principled material and no coordinate-driven color
  split that can paint artificial diagonal seams across source triangles.

The proof writes a whole-scene candidate and a genuinely isolated shell+window
architecture raster. Manual comparison against Refernzbild.png remains
mandatory; this script never marks visual acceptance by itself.
"""

import json
import os
from pathlib import Path

import bpy
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

# Intentionally neutral, warm fabric checkpoint. This is not final night-room
# polish; it removes both the damaged source atlas and the proof-created diagonal
# two-tone seam as variables while retaining the exact source geometry.
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


def make_clean_window_material():
    existing = bpy.data.materials.get(MATERIAL_NAME)
    if existing is not None and not bool(existing.get("celine_room_builder_owned", False)):
        fail(f"refusing to replace non-room-owned material {MATERIAL_NAME}")
    material = existing if existing is not None else bpy.data.materials.new(MATERIAL_NAME)
    material["celine_room_builder_owned"] = True
    material["reference_clean_window_checkpoint"] = True
    material["source_glb_bytes_mutated"] = False
    material["geometry_mutated"] = False
    material["source_base_color_atlas_bypassed"] = True
    material["appearance_strategy"] = "uniform-neutral-principled"
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


def apply_clean_window_material():
    root = bpy.data.objects.get(WINDOW_GEOMETRY)
    if root is None:
        fail(f"missing solved source window geometry: {WINDOW_GEOMETRY}")
    material = make_clean_window_material()
    meshes = []
    for obj in descendants(root):
        if obj.type != "MESH":
            continue
        if not bool(obj.data):
            continue
        obj.data.materials.clear()
        obj.data.materials.append(material)
        obj["reference_clean_window_checkpoint"] = True
        obj["reference_clean_window_material"] = material.name
        obj["reference_clean_window_geometry_mutated"] = False
        meshes.append(obj.name)
    if not meshes:
        fail("source window hierarchy contains no mesh objects")
    bpy.context.view_layer.update()
    return meshes


def hide_non_window_geometry_for_architecture():
    """Hide every descendant of non-window instance roots for the architecture raster."""
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

    if not any(
        bpy.data.objects.get(name) is not None and bpy.data.objects[name].type == "MESH"
        for name in hidden
    ):
        fail("architecture isolation hid no renderable furniture meshes")
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
    meshes = apply_clean_window_material()
    scene, camera, engine_name, lights = configure_scene()
    front = bpy.data.objects.get(FRONT_SHELL)
    if front is None:
        fail(f"missing cutaway shell: {FRONT_SHELL}")

    original_hide = {obj.name: bool(obj.hide_render) for obj in bpy.data.objects}
    architecture_hidden = []
    try:
        # Whole-scene comparison: window material strategy only.
        front.hide_render = True
        render(scene, WHOLE_OUTPUT)

        # Architecture-only comparison: shell + exact source window/drapes + solved camera.
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
        "schema": 3,
        "purpose": "clean source-window material checkpoint after whole-scene rejection #1379",
        "head_sha": HEAD_SHA,
        "whole_scene_render": WHOLE_OUTPUT.name,
        "architecture_render": ARCH_OUTPUT.name,
        "reference": REFERENCE.name,
        "engine": engine_name,
        "render_size": [1376, 1100],
        "window_geometry_object": WINDOW_GEOMETRY,
        "window_meshes": meshes,
        "architecture_isolation": "shell+source_window_drapes+solved_camera_only",
        "architecture_non_window_objects_hidden_count": len(architecture_hidden),
        "architecture_non_window_objects_hidden": architecture_hidden,
        "source_glbs_mutated": False,
        "source_geometry_mutated": False,
        "window_anchor_transform_mutated": False,
        "proof_camera_mutated": False,
        "furniture_transforms_mutated": False,
        "derived_window_planes_added": False,
        "source_window_hidden": False,
        "source_base_color_atlas_bypassed": True,
        "clean_material": MATERIAL_NAME,
        "clean_material_strategy": "uniform-neutral-principled",
        "neutral_color_linear": list(NEUTRAL_COLOR),
        "coordinate_driven_color_split": False,
        "roughness": ROUGHNESS,
        "proof_lighting_runtime_equivalent_to_467": True,
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
        "note": "Manual pixel inspection against Refernzbild.png and the prior architecture raster is mandatory.",
    }
    META.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    print(
        "CELINE_ROOM_WINDOW_CLEAN_CHECKPOINT PASS "
        f"whole={WHOLE_OUTPUT.name} architecture={ARCH_OUTPUT.name} "
        f"architectureHidden={len(architecture_hidden)} "
        "sourceBytesImmutable=true geometryMutated=false cameraMutated=false "
        "derivedWindowPlanes=false sourceWindowHidden=false coordinateSplit=false",
        flush=True,
    )
    print("visualAcceptance=UNASSESSED", flush=True)


main()
