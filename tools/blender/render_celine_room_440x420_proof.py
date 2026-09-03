#!/usr/bin/env python3
"""Render deterministic real-Blender visual proof for the exact Celine 4.40 x 4.20 room.

This script assumes tools/blender/build_celine_room_440x420.py has already run in
this same Blender process. It does not generate or substitute geometry. It only
adds a proof-only camera, temporarily hides selected room-shell faces to create
cutaway views, renders three overview images, restores shell visibility, and
removes the proof camera afterwards.
"""

from pathlib import Path
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
    data.lens = 32.0
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


def render_view(scene: bpy.types.Scene, camera: bpy.types.Object, name: str,
                camera_user, target_user, lens: float, hidden_shell_names) -> Path:
    previous = set_cutaway_visibility(hidden_shell_names)
    try:
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
    finally:
        restore_visibility(previous)


def main() -> None:
    root = bpy.data.objects.get(ROOT_NAME)
    if root is None:
        fail(f"missing required room root {ROOT_NAME}; run builder first")

    PROOF_DIR.mkdir(parents=True, exist_ok=True)
    scene = bpy.context.scene
    configure_workbench(scene)
    camera = make_camera()

    # Exterior elevated cutaway views deliberately do NOT alter the room contract.
    # Front wall and ceiling are hidden only while rendering so the exact compact
    # 4.40 x 4.20 layout can be judged without the foreground table/lamp blocking
    # most of the frame. Side walls remain visible except for the near wall in the
    # corresponding oblique view, then all visibility is restored immediately.
    views = (
        (
            "01_front_wide",
            (0.00, 3.60, 5.40),
            (0.00, 0.72, -0.20),
            32.0,
            ("room_shell_front", "room_shell_ceiling"),
        ),
        (
            "02_front_left",
            (-2.80, 3.30, 4.80),
            (0.00, 0.72, -0.30),
            31.0,
            ("room_shell_front", "room_shell_ceiling", "room_shell_left"),
        ),
        (
            "03_front_right",
            (2.80, 3.30, 4.80),
            (0.00, 0.72, -0.30),
            31.0,
            ("room_shell_front", "room_shell_ceiling", "room_shell_right"),
        ),
    )

    outputs = [render_view(scene, camera, *view) for view in views]

    # Proof camera is not part of the room contract and must not persist in the
    # generated scene after the proof images are written.
    camera_data = camera.data
    bpy.data.objects.remove(camera, do_unlink=True)
    if camera_data.users == 0:
        bpy.data.cameras.remove(camera_data)

    # Fail closed if a proof-only shell visibility override leaked into the scene.
    for name in ("room_shell_front", "room_shell_ceiling", "room_shell_left", "room_shell_right"):
        obj = bpy.data.objects.get(name)
        if obj is None:
            fail(f"missing shell object after proof render: {name}")
        if obj.hide_render:
            fail(f"proof cutaway visibility leaked into generated room: {name}")

    print("CELINE_ROOM_440x420_VISUAL_PROOF PASS")
    print(f"Real Blender renders: {len(outputs)}")
    print("Cutaway visibility restored; room geometry and prescribed transforms unchanged.")
    print("No generated/substitute geometry was introduced by the visual proof.")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        if "CELINE_ROOM_440x420_VISUAL_PROOF FAIL" not in str(exc):
            print("CELINE_ROOM_440x420_VISUAL_PROOF FAIL")
            print(str(exc))
        raise
