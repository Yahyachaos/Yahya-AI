#!/usr/bin/env python3
"""Render the actual reference-solved Celine room without proof-time geometry hacks.

Expected execution order in one Blender process:
1) build_celine_room_440x420.py
2) solve_celine_room_reference_layout.py
3) this renderer

The renderer may hide the front shell for the camera cutaway, but it must not
move/scale/mirror furniture, room_world_root, or the solved reference camera.
Visual acceptance is separate from structural render success.
"""

from pathlib import Path
import json
import os

import bpy

ROOT_NAME = "room_world_root"
CAMERA_NAME = "room_440x420_reference_camera"
PROOF_DIR = Path(os.environ.get("CELINE_ROOM_PROOF_DIR", "ci-room-proof")).resolve()
REFERENCE = Path(os.environ.get("CELINE_ROOM_REFERENCE", "Refernzbild.png")).resolve()
RENDER_SIZE = (1376, 1100)

# Deterministic instance colors are diagnostic only. Proof #40 confirmed that a
# dominant smooth central slab survives the bed-yaw flip, but the normal gray
# Workbench render cannot identify which overlapping source instance owns it.
# This second real-Blender pass changes only viewport display color, restores it
# afterwards, and never changes geometry, transforms, camera or source bytes.
INSTANCE_COLORS = {
    "room_bed": (0.95, 0.10, 0.10, 1.0),
    "room_dresser": (0.10, 0.70, 0.15, 1.0),
    "room_plant_large": (0.30, 0.90, 0.20, 1.0),
    "room_plant_small": (0.95, 0.85, 0.10, 1.0),
    "room_floor_lamp": (1.00, 0.45, 0.05, 1.0),
    "room_nightstand_rear": (0.05, 0.85, 0.90, 1.0),
    "room_nightstand_front": (0.10, 0.35, 0.95, 1.0),
    "room_lounge_chair": (0.90, 0.10, 0.85, 1.0),
    "room_rug": (0.55, 0.15, 0.85, 1.0),
    "room_foreground_table": (0.95, 0.35, 0.60, 1.0),
    "room_window_drapes": (0.10, 0.75, 0.65, 1.0),
    "room_wall_shelf_books": (0.80, 0.55, 0.10, 1.0),
    "room_round_mirror": (0.25, 0.25, 0.25, 1.0),
}


def fail(message: str) -> None:
    print("CELINE_ROOM_440x420_RENDER_PROOF FAIL", flush=True)
    raise RuntimeError(message)


def configure_workbench(scene: bpy.types.Scene) -> None:
    """Configure a geometry-first proof render that stays exact-grid but avoids costly polish.

    The room rebuild is still in geometry/perspective acceptance. Shadows, cavity,
    outlines and high-sample viewport AA add substantial software-OpenGL cost on
    the canonical ~1.5 GiB furniture set without changing any solved geometry.
    Keep the actual imported meshes/camera/transforms, but render them with the
    lightest deterministic Workbench presentation needed for silhouette/layout
    comparison against the reference.
    """
    scene.render.engine = "BLENDER_WORKBENCH"
    scene.render.resolution_x = RENDER_SIZE[0]
    scene.render.resolution_y = RENDER_SIZE[1]
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    scene.render.image_settings.color_mode = "RGB"
    scene.render.image_settings.color_depth = "8"
    scene.render.film_transparent = False

    shading = scene.display.shading
    shading.light = "STUDIO"
    shading.color_type = "MATERIAL"
    shading.show_shadows = False
    shading.show_cavity = False
    if hasattr(shading, "show_specular_highlight"):
        shading.show_specular_highlight = False
    if hasattr(shading, "show_outline"):
        shading.show_outline = False

    # Blender 4.x supports FXAA for Workbench output. Avoid multi-sample render
    # passes while preserving the exact 1376x1100 comparison grid.
    if hasattr(scene.display, "render_aa"):
        try:
            scene.display.render_aa = "FXAA"
        except TypeError:
            scene.display.render_aa = "OFF"


def descendants(root: bpy.types.Object):
    out = []
    stack = list(root.children)
    while stack:
        obj = stack.pop()
        out.append(obj)
        stack.extend(list(obj.children))
    return out


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
        # IMPORTANT: no camera mutation here. The solver owns the reference camera.
        scene.camera = camera
        out = PROOF_DIR / "01_front_wide.png"
        scene.render.filepath = str(out)
        print(
            f"CELINE_ROOM_440x420_RENDER_BEGIN engine={scene.render.engine} "
            f"size={RENDER_SIZE[0]}x{RENDER_SIZE[1]} shadows=false cavity=false",
            flush=True,
        )
        bpy.ops.render.render(write_still=True)
        print("CELINE_ROOM_440x420_RENDER_END", flush=True)
        if not out.exists() or out.stat().st_size < 10_000:
            fail(f"render missing or unexpectedly small: {out}")
        return out
    finally:
        restore_visibility(previous)


def render_instance_id(scene: bpy.types.Scene, camera: bpy.types.Object) -> Path:
    """Render deterministic per-instance colors without changing scene geometry."""
    previous_cutaway = set_cutaway_visibility(("room_shell_front",))
    previous_color_type = scene.display.shading.color_type
    previous_colors = {}
    try:
        for instance_id, color in INSTANCE_COLORS.items():
            geometry = bpy.data.objects.get(f"{instance_id}__geometry")
            if geometry is None:
                fail(f"missing geometry root for instance-id diagnostic: {instance_id}")
            for obj in descendants(geometry):
                if obj.type != "MESH":
                    continue
                previous_colors[obj.name] = tuple(float(v) for v in obj.color)
                obj.color = color

        scene.display.shading.color_type = "OBJECT"
        scene.camera = camera
        out = PROOF_DIR / "02_instance_id.png"
        scene.render.filepath = str(out)
        print("CELINE_ROOM_INSTANCE_ID_RENDER_BEGIN", flush=True)
        bpy.ops.render.render(write_still=True)
        print("CELINE_ROOM_INSTANCE_ID_RENDER_END", flush=True)
        if not out.exists() or out.stat().st_size < 10_000:
            fail(f"instance-id render missing or unexpectedly small: {out}")
        mapping = {
            instance_id: [float(v) for v in color]
            for instance_id, color in INSTANCE_COLORS.items()
        }
        (PROOF_DIR / "instance_id_colors.json").write_text(
            json.dumps(mapping, indent=2) + "\n", encoding="utf-8"
        )
        return out
    finally:
        for name, color in previous_colors.items():
            obj = bpy.data.objects.get(name)
            if obj is not None:
                obj.color = color
        scene.display.shading.color_type = previous_color_type
        restore_visibility(previous_cutaway)


def write_structural_metadata(
    output: Path,
    instance_id_output: Path,
    root: bpy.types.Object,
    camera: bpy.types.Object,
) -> None:
    payload = {
        "render": output.name,
        "instance_id_render": instance_id_output.name,
        "reference": REFERENCE.name,
        "render_size": list(RENDER_SIZE),
        "render_mode": "geometry_first_workbench_fast",
        "shadows": False,
        "cavity": False,
        "proof_time_geometry_mutation": False,
        "proof_time_appearance_diagnostic_only": True,
        "proof_time_root_mirror": False,
        "proof_time_camera_mutation": False,
        "root_scale": [float(v) for v in root.scale],
        "camera": {
            "name": camera.name,
            "location": [float(v) for v in camera.location],
            "rotation_euler": [float(v) for v in camera.rotation_euler],
            "lens_mm": float(camera.data.lens),
            "reference_solved": bool(camera.get("reference_solved", False)),
        },
        "visual_acceptance": "UNASSESSED",
        "note": "Structural render success is not visual acceptance. Compare actual output against Refernzbild.png; 02_instance_id.png is diagnostic color only.",
    }
    (PROOF_DIR / "render-proof.json").write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    root = bpy.data.objects.get(ROOT_NAME)
    if root is None:
        fail(f"missing required room root {ROOT_NAME}; run builder first")
    if any(float(v) < 0.0 for v in root.scale):
        fail(f"negative root scale detected; global/proof mirroring is forbidden: {tuple(root.scale)}")

    camera = bpy.data.objects.get(CAMERA_NAME)
    if camera is None or camera.type != "CAMERA":
        fail(f"missing solved reference camera {CAMERA_NAME}; run reference solver first")
    if not bool(camera.get("reference_solved", False)):
        fail(f"camera {CAMERA_NAME} is not marked reference_solved")

    if not REFERENCE.is_file():
        fail(f"missing canonical reference image: {REFERENCE}")

    PROOF_DIR.mkdir(parents=True, exist_ok=True)
    scene = bpy.context.scene
    configure_workbench(scene)
    output = render_primary(scene, camera)
    instance_id_output = render_instance_id(scene, camera)
    write_structural_metadata(output, instance_id_output, root, camera)

    for name in ("room_shell_front", "room_shell_ceiling", "room_shell_left", "room_shell_right"):
        obj = bpy.data.objects.get(name)
        if obj is None:
            fail(f"missing shell object after proof render: {name}")
        if obj.hide_render:
            fail(f"proof cutaway visibility leaked into generated room: {name}")

    print("CELINE_ROOM_440x420_RENDER_PROOF PASS", flush=True)
    print(f"Real Blender primary render: {output}", flush=True)
    print(f"Real Blender instance-id diagnostic: {instance_id_output}", flush=True)
    print("proofTimeGeometryMutation=false proofTimeRootMirror=false proofTimeCameraMutation=false", flush=True)
    print("visualAcceptance=UNASSESSED", flush=True)


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        if "CELINE_ROOM_440x420_RENDER_PROOF FAIL" not in str(exc):
            print("CELINE_ROOM_440x420_RENDER_PROOF FAIL", flush=True)
            print(str(exc), flush=True)
        raise
