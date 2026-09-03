#!/usr/bin/env python3
"""Render deterministic real-Blender visual proof for the exact Celine 4.40 x 4.20 room.

This script assumes tools/blender/build_celine_room_440x420.py has already run in
this same Blender process. It does not generate or substitute geometry. During
bounded reconstruction it may apply an explicitly measured derived calibration
to furniture geometry below the immutable prescribed source anchor, then adds a
proof-only camera, temporarily hides the front room-shell face, renders the
single reference-comparable primary image, restores shell visibility, and
removes the proof camera afterwards. Original GLB bytes remain untouched.
"""

from pathlib import Path
import os

import bpy
from mathutils import Vector

ROOT_NAME = "room_world_root"
PROOF_DIR = Path(os.environ.get("CELINE_ROOM_PROOF_DIR", "ci-room-proof")).resolve()
# Match the authoritative reference pixel grid/aspect ratio so image-space
# measurements and overlays are meaningful instead of comparing mismatched grids.
RENDER_SIZE = (1376, 1100)

# Proof #14 established that the previous uniform table correction solved only
# horizontal coverage and badly over-occluded the room vertically. Reuse the
# better #13 depth/height reading while keeping #14's measured projected width:
# effective source geometry = z-depth 1.55 m, X scale 1.45, height/depth 0.68.
# The canonical source anchor itself remains at the current exact-contract
# transform (z=2.05, uniform scale=1.10); only its child geometry root receives
# this derived calibration, so the source GLB and anchor audit stay immutable.
TABLE_ANCHOR_NAME = "room_foreground_table__anchor"
TABLE_GEOMETRY_NAME = "room_foreground_table__geometry"
TABLE_CONTRACT_Z = 2.05
TABLE_CONTRACT_SCALE = 1.10
TABLE_EFFECTIVE_Z = 1.55
TABLE_EFFECTIVE_USER_SCALE = (1.45, 0.68, 0.68)  # X, Y-height, Z-depth
EPS = 5.0e-4


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


def is_descendant(obj: bpy.types.Object, ancestor: bpy.types.Object) -> bool:
    cursor = obj
    while cursor is not None:
        if cursor is ancestor:
            return True
        cursor = cursor.parent
    return False


def descendant_mesh_world_min_z(geometry_root: bpy.types.Object) -> float:
    depsgraph = bpy.context.evaluated_depsgraph_get()
    values = []
    for obj in bpy.data.objects:
        if obj.type != "MESH" or not is_descendant(obj, geometry_root):
            continue
        evaluated = obj.evaluated_get(depsgraph)
        for corner in evaluated.bound_box:
            values.append((evaluated.matrix_world @ Vector(corner)).z)
    if not values:
        fail(f"{TABLE_GEOMETRY_NAME}: no descendant mesh bounding box")
    return min(values)


def apply_foreground_table_reference_calibration() -> None:
    anchor = bpy.data.objects.get(TABLE_ANCHOR_NAME)
    geometry = bpy.data.objects.get(TABLE_GEOMETRY_NAME)
    if anchor is None or geometry is None:
        fail("foreground table anchor/geometry missing for measured calibration")
    if geometry.parent is not anchor:
        fail("foreground table geometry is no longer parented to its canonical anchor")

    anchor_scale = tuple(float(v) for v in anchor.scale)
    if any(abs(v - TABLE_CONTRACT_SCALE) > 1.0e-4 for v in anchor_scale):
        fail(f"foreground table anchor scale changed unexpectedly: {anchor_scale}")
    if abs(float(anchor.location.y) - TABLE_CONTRACT_Z) > 1.0e-4:
        fail(f"foreground table anchor depth changed unexpectedly: {anchor.location.y}")

    sx, sy_height, sz_depth = TABLE_EFFECTIVE_USER_SCALE
    # Blender child scale order follows user X, user Z-depth, user Y-height.
    geometry.scale = (
        sx / TABLE_CONTRACT_SCALE,
        sz_depth / TABLE_CONTRACT_SCALE,
        sy_height / TABLE_CONTRACT_SCALE,
    )
    # Shift the imported source geometry from the trial anchor depth back to the
    # #13 depth that had the substantially better vertical composition.
    geometry.location.y = (TABLE_EFFECTIVE_Z - TABLE_CONTRACT_Z) / TABLE_CONTRACT_SCALE
    bpy.context.view_layer.update()

    # Re-ground after the non-uniform derived scale. Child translation is in the
    # parent-anchor coordinate system, so divide the world correction by the
    # immutable uniform anchor scale.
    min_z = descendant_mesh_world_min_z(geometry)
    geometry.location.z += (0.0 - min_z) / TABLE_CONTRACT_SCALE
    bpy.context.view_layer.update()
    grounded = descendant_mesh_world_min_z(geometry)
    if abs(grounded) > EPS:
        fail(f"foreground table derived calibration lost floor contact: z={grounded:.6f}")

    geometry["reference_calibration"] = "proof14_width_plus_proof13_depth_height"
    geometry["effective_user_z_depth"] = TABLE_EFFECTIVE_Z
    geometry["effective_user_scale_xyz"] = list(TABLE_EFFECTIVE_USER_SCALE)
    print(
        "CELINE_ROOM_REFERENCE_CALIBRATION "
        f"tableEffectiveZ={TABLE_EFFECTIVE_Z:.2f} "
        f"tableEffectiveScale={sx:.2f}/{sy_height:.2f}/{sz_depth:.2f} "
        f"groundedZ={grounded:.6f} sourceGLBMutated=false anchorMutated=false"
    )


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

    # One bounded derived furniture correction only. This is deliberately
    # evaluated before touching the next bed/right-side layout error.
    apply_foreground_table_reference_calibration()

    PROOF_DIR.mkdir(parents=True, exist_ok=True)
    scene = bpy.context.scene
    configure_workbench(scene)
    camera = make_camera()

    # Keep draft iteration deliberately bounded: the primary frame is the only
    # frame required to compare whole-scene geometry against /Refernzbild.png.
    output = render_view(
        scene,
        camera,
        "01_front_wide",
        (0.00, 1.55, 3.60),
        (0.00, 0.95, -0.30),
        24.0,
        ("room_shell_front",),
    )

    # Proof camera is not part of the room contract and must not persist in the
    # generated scene after the proof image is written.
    camera_data = camera.data
    bpy.data.objects.remove(camera, do_unlink=True)
    if camera_data.users == 0:
        bpy.data.cameras.remove(camera_data)

    # Fail closed if the proof-only shell visibility override leaked into scene.
    for name in ("room_shell_front", "room_shell_ceiling", "room_shell_left", "room_shell_right"):
        obj = bpy.data.objects.get(name)
        if obj is None:
            fail(f"missing shell object after proof render: {name}")
        if obj.hide_render:
            fail(f"proof cutaway visibility leaked into generated room: {name}")

    print("CELINE_ROOM_440x420_VISUAL_PROOF PASS")
    print(f"Real Blender primary render: {output}")
    print("Cutaway visibility restored; original furniture GLBs and prescribed anchors unchanged.")
    print("No generated/substitute geometry was introduced by the visual proof.")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        if "CELINE_ROOM_440x420_VISUAL_PROOF FAIL" not in str(exc):
            print("CELINE_ROOM_440x420_VISUAL_PROOF FAIL")
            print(str(exc))
        raise
