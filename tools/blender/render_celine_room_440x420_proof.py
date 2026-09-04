#!/usr/bin/env python3
"""Render the actual Celine room scene without proof-time geometry hacks.

This script assumes tools/blender/build_celine_room_440x420.py has already run in
this Blender process. It may hide the front shell for the camera cutaway, but it
must not move/scale/mirror furniture or room_world_root. Visual acceptance is a
separate manual/reference-comparison decision; this script reports structural
render success only.
"""

from pathlib import Path
import json
import os

import bpy
from mathutils import Vector

ROOT_NAME = "room_world_root"
PROOF_DIR = Path(os.environ.get("CELINE_ROOM_PROOF_DIR", "ci-room-proof")).resolve()
REFERENCE = Path(os.environ.get("CELINE_ROOM_REFERENCE", "Refernzbild.png")).resolve()
RENDER_SIZE = (1376, 1100)


def fail(message: str) -> None:
    print("CELINE_ROOM_440x420_RENDER_PROOF FAIL")
    raise RuntimeError(message)


def user_to_blender(x: float, y_height: float, z_depth: float) -> Vector:
    return Vector((x, z_depth, y_height))


def point_camera(camera: bpy.types.Object, target: Vector) -> None:
    direction = target - camera.location
    if direction.length < 1e-6:
        fail("proof camera target is coincident with camera position")
    camera.rotation_euler = direction.to_track_quat("-Z", "Y").to_euler()


def make_camera() -> bpy.types.Object:
    existing = bpy.data.objects.get("room_440x420_proof_camera")
    if existing is not None:
        bpy.data.objects.remove(existing, do_unlink=True)
    data = bpy.data.cameras.new("room_440x420_proof_camera_data")
    data.lens = 24.0
    data.sensor_width = 36.0
    data.clip_start = 0.05
    data.clip_end = 50.0
    camera = bpy.data.objects.new("room_440x420_proof_camera", data)
    bpy.context.scene.collection.objects.link(camera)
    return camera


def configure_workbench(scene: bpy.types.Scene) -> None:
    scene.render.engine = "BLENDER_WORKBENCH"
    scene.render.resolution_x = RENDER_SIZE[0]
    scene.render.resolution_y = RENDER_SIZE[1]
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    scene.render.film_transparent = False

    shading = scene.display.shading
    shading.light = "STUDIO"
    shading.color_type = "MATERIAL"
    shading.show_shadows = True
    shading.show_cavity = True
    shading.cavity_type = "WORLD"
    shading.curvature_ridge_factor = 1.5
    shading.curvature_valley_factor = 1.2


def set_cutaway_visibility(hidden_names):
    previous = {}
    for name in hidden_names:
        obj = bpy.data.objects.get(name)
        if obj is None:
            fail(f"missing proof cutaway shell object: {name}")
        previous[name] = obj.hide_render
        obj.hide_render = True
    return previous


def restore_visibility(previous):
    for name, hidden in previous.items():
        obj = bpy.data.objects.get(name)
        if obj is not None:
            obj.hide_render = hidden


def render_primary(scene: bpy.types.Scene, camera: bpy.types.Object) -> Path:
    previous = set_cutaway_visibility(("room_shell_front",))
    try:
        # This is a neutral starting camera only. Reference-constrained camera
        # solving belongs in the builder/layout calibration, not hidden here.
        camera.location = user_to_blender(0.00, 1.55, 3.60)
        camera.data.lens = 24.0
        point_camera(camera, user_to_blender(0.00, 0.95, -0.30))
        scene.camera = camera
        out = PROOF_DIR / "01_front_wide.png"
        scene.render.filepath = str(out)
        bpy.ops.render.render(write_still=True)
        if not out.exists() or out.stat().st_size < 10_000:
            fail(f"render missing or unexpectedly small: {out}")
        return out
    finally:
        restore_visibility(previous)


def write_structural_metadata(output: Path, root: bpy.types.Object) -> None:
    payload = {
        "render": output.name,
        "reference": REFERENCE.name,
        "render_size": list(RENDER_SIZE),
        "proof_time_geometry_mutation": False,
        "proof_time_root_mirror": False,
        "root_scale": [float(v) for v in root.scale],
        "visual_acceptance": "UNASSESSED",
        "note": "Structural render success is not visual acceptance. Compare actual output against Refernzbild.png.",
    }
    (PROOF_DIR / "render-proof.json").write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    root = bpy.data.objects.get(ROOT_NAME)
    if root is None:
        fail(f"missing required room root {ROOT_NAME}; run builder first")

    # Fail closed if a historical proof-only root mirror leaked into the actual
    # scene. Handedness must be solved in the builder/layout contract.
    if float(root.scale.x) < 0.0 or float(root.scale.y) < 0.0 or float(root.scale.z) < 0.0:
        fail(f"negative root scale detected; proof-time/global mirroring is forbidden: {tuple(root.scale)}")

    if not REFERENCE.is_file():
        fail(f"missing canonical reference image: {REFERENCE}")

    PROOF_DIR.mkdir(parents=True, exist_ok=True)
    scene = bpy.context.scene
    configure_workbench(scene)
    camera = make_camera()
    output = render_primary(scene, camera)
    write_structural_metadata(output, root)

    camera_data = camera.data
    bpy.data.objects.remove(camera, do_unlink=True)
    if camera_data.users == 0:
        bpy.data.cameras.remove(camera_data)

    for name in ("room_shell_front", "room_shell_ceiling", "room_shell_left", "room_shell_right"):
        obj = bpy.data.objects.get(name)
        if obj is None:
            fail(f"missing shell object after proof render: {name}")
        if obj.hide_render:
            fail(f"proof cutaway visibility leaked into generated room: {name}")

    print("CELINE_ROOM_440x420_RENDER_PROOF PASS")
    print(f"Real Blender primary render: {output}")
    print("proofTimeGeometryMutation=false proofTimeRootMirror=false")
    print("visualAcceptance=UNASSESSED")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        if "CELINE_ROOM_440x420_RENDER_PROOF FAIL" not in str(exc):
            print("CELINE_ROOM_440x420_RENDER_PROOF FAIL")
            print(str(exc))
        raise
