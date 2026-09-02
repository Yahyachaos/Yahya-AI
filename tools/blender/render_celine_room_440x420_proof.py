#!/usr/bin/env python3
"""Render deterministic real-Blender visual proof for the exact Celine 4.40 x 4.20 room.

This script assumes tools/blender/build_celine_room_440x420.py has already run in
this same Blender process. It does not generate or substitute geometry. It only
adds proof-only cameras, renders three interior views, and removes the proof
camera afterwards.
"""

from pathlib import Path
import math
import os

import bpy
from mathutils import Vector

ROOT_NAME = "room_world_root"
PROOF_DIR = Path(os.environ.get("CELINE_ROOM_PROOF_DIR", "ci-room-proof")).resolve()
RENDER_SIZE = (1280, 720)


def fail(message: str) -> None:
    print("CELINE_ROOM_440x420_VISUAL_PROOF FAIL")
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


def render_view(scene: bpy.types.Scene, camera: bpy.types.Object, name: str,
                camera_user, target_user, lens: float) -> Path:
    camera.location = user_to_blender(*camera_user)
    camera.data.lens = lens
    point_camera(camera, user_to_blender(*target_user))
    scene.camera = camera
    out = PROOF_DIR / f"{name}.png"
    scene.render.filepath = str(out)
    bpy.ops.render.render(write_still=True)
    if not out.exists() or out.stat().st_size < 10_000:
        fail(f"render missing or unexpectedly small: {out}")
    print(f"REAL_BLENDER_RENDER {name} {out.stat().st_size} bytes")
    return out


def main() -> None:
    root = bpy.data.objects.get(ROOT_NAME)
    if root is None:
        fail(f"missing required room root {ROOT_NAME}; run builder first")

    PROOF_DIR.mkdir(parents=True, exist_ok=True)
    scene = bpy.context.scene
    configure_workbench(scene)
    camera = make_camera()

    # All camera positions are inside the exact clear envelope. User-space axes:
    # X left/right, Y height, Z depth. These views are intentionally wide enough
    # to expose room proportions, furniture orientation and wall relationships.
    views = (
        (
            "01_front_wide",
            (0.00, 1.48, 1.92),
            (0.00, 0.95, -0.45),
            23.0,
        ),
        (
            "02_front_left",
            (-1.72, 1.50, 1.72),
            (0.30, 0.92, -0.48),
            24.0,
        ),
        (
            "03_front_right",
            (1.72, 1.50, 1.72),
            (-0.30, 0.92, -0.48),
            24.0,
        ),
    )

    outputs = [render_view(scene, camera, *view) for view in views]

    # Proof camera is not part of the room contract and must not persist in the
    # generated scene after the proof images are written.
    camera_data = camera.data
    bpy.data.objects.remove(camera, do_unlink=True)
    if camera_data.users == 0:
        bpy.data.cameras.remove(camera_data)

    print("CELINE_ROOM_440x420_VISUAL_PROOF PASS")
    print(f"Real Blender renders: {len(outputs)}")
    print("No generated/substitute geometry was introduced by the visual proof.")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        if "CELINE_ROOM_440x420_VISUAL_PROOF FAIL" not in str(exc):
            print("CELINE_ROOM_440x420_VISUAL_PROOF FAIL")
            print(str(exc))
        raise
