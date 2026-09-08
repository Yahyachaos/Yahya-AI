#!/usr/bin/env python3
"""Staged reference-layout solve plus the bounded architecture recovery checkpoint.

The prior staged solver remains byte-for-byte in
solve_celine_room_reference_layout_stage_base.py. This wrapper preserves the
accepted Proof #104 dresser transform and Proof #111 grounded front-facing chair
transform, then changes only the derived room_rug anchor.

Proof #121 established a reliable real-rendered rug width checkpoint:
x=0.20349..0.86773 versus target x=0.2055..0.8705, while the vertical envelope
remained too high/short at y=0.50818..0.77273 versus target y=0.5205..0.7955.
The width-only Proof #121 state is therefore calibration evidence but is not yet
runtime acceptance.

Unprojecting those image-space edges through the accepted Blender camera onto
the rug floor plane z=0.012 gives the next bounded world correction without
eyeballing: move the anchor +0.207829 m in user depth and +0.013758 m world X,
reduce local Y/depth span by 0.926761, and compensate perspective width with
local X 1.7087076. Preserve local Z/thickness 1.6410157, yaw 5.8203125 degrees,
exact rug floor grounding, all accepted other geometry and the immutable source
rug GLB bytes. Projected object AABB remains diagnostic only; the real rendered
instance-ID silhouette is visual authority.

Recovery checkpoint after rejected whole-scene candidate #1379: the solved
reference camera is intentionally outside the canonical +2.10 m front plane so
the foreground table can occupy only the lower frame. The front wall is already
cut away for proof rendering, but the finite canonical shell stopped exactly at
+2.10 m. Source-PBR Proof #458 exposed dark world above the front ceiling edge;
the first ceiling-only overhang removed the center opening but exact fresh-reload
Proof #460 still exposed two large dark triangular wedges because the left/right
side walls still ended at +2.10 m. Close the camera-facing cutaway coherently by
extending the ceiling AND both side-wall slabs to the solved camera plus the same
small margin. Their canonical interior planes, the clear 4.40 x 4.20 x 2.65 m
room volume, furniture transforms, materials and proof camera remain unchanged.
This is shell closure for the actual camera owner, not a proof-time appearance
or furniture patch.
"""

from pathlib import Path
import math

import bpy

STAGE_BASE = Path(__file__).with_name("solve_celine_room_reference_layout_stage_base.py")
source = STAGE_BASE.read_text(encoding="utf-8")
sentinel = "\nsolver.main()\n"
if not source.endswith(sentinel):
    raise RuntimeError("canonical staged solver no longer ends with solver.main(); reconcile wrapper")
source = source[:-len(sentinel)] + "\n"
namespace = {
    "__file__": str(STAGE_BASE),
    "__name__": "celine_room_reference_layout_stage_base",
}
exec(compile(source, str(STAGE_BASE), "exec"), namespace)
solver = namespace["solver"]
_previous_dispatch = solver.solve_instance

DRESSER_PARAMS = [
    2.1353125,
    -0.077,
    0.8235393808004019,
    0.8602542716363035,
    87.71484625447816,
]

CHAIR_PARAMS = [
    1.69921875,
    -2.05,
    0.455615302013423,
    0.6259727564102563,
    170.375,
]

RUG_PARAMS = [
    -0.05342981506564297,
    -0.08748335231922333,
    1.7087075584071436,
    1.3898819933680306,
    1.6410156726837158,
    5.8203125,
]

CANONICAL_BACK_PLANE_Y = -2.10
CANONICAL_FRONT_PLANE_Y = 2.10
REFERENCE_CAMERA_SHELL_MARGIN_M = 0.20
MAX_CAMERA_SHELL_OVERHANG_M = 1.25


def _apply_anisotropic(instance_id, params, authority):
    x, z_depth, horizontal_scale, vertical_scale, rot = params
    a = solver.anchor(instance_id)
    audit = a.get("user_location_xyz", [0.0, float(a.location.z), 0.0])
    user_y = float(audit[1]) if len(audit) == 3 else float(a.location.z)
    a.location.x = float(x)
    a.location.y = float(z_depth)
    a.rotation_mode = "XYZ"
    a.rotation_euler.z = math.radians(-float(rot))
    a.scale = (float(horizontal_scale), float(horizontal_scale), float(vertical_scale))
    a["user_location_xyz"] = [float(a.location.x), float(user_y), float(a.location.y)]
    a["user_rotation_y_deg"] = float(rot)
    if "user_uniform_scale" in a:
        del a["user_uniform_scale"]
    a["user_scale_xyz"] = [float(horizontal_scale), float(horizontal_scale), float(vertical_scale)]
    a["reference_solved"] = True
    a["reference_anisotropic_anchor_scale"] = True
    a["reference_visual_fit_authority"] = authority
    solver.reground(instance_id)


def _apply_rug_planar(instance_id, params, authority):
    x, z_depth, scale_x, scale_depth, scale_vertical, rot = params
    a = solver.anchor(instance_id)
    audit = a.get("user_location_xyz", [0.0, float(a.location.z), 0.0])
    user_y = float(audit[1]) if len(audit) == 3 else float(a.location.z)
    a.location.x = float(x)
    a.location.y = float(z_depth)
    a.rotation_mode = "XYZ"
    a.rotation_euler.z = math.radians(-float(rot))
    a.scale = (float(scale_x), float(scale_depth), float(scale_vertical))
    a["user_location_xyz"] = [float(a.location.x), float(user_y), float(a.location.y)]
    a["user_rotation_y_deg"] = float(rot)
    if "user_uniform_scale" in a:
        del a["user_uniform_scale"]
    a["user_scale_xyz"] = [float(scale_x), float(scale_depth), float(scale_vertical)]
    a["reference_solved"] = True
    a["reference_anisotropic_anchor_scale"] = True
    a["reference_visual_fit_authority"] = authority
    a["reference_rug_visible_bbox_proof121"] = [
        0.2034883721, 0.8677325581, 0.5081818182, 0.7727272727
    ]
    a["reference_rug_visible_bbox_target"] = [0.2055, 0.8705, 0.5205, 0.7955]
    a["reference_rug_floorplane_delta_world_x"] = 0.0137576849
    a["reference_rug_floorplane_delta_user_depth"] = 0.2078291477
    a["reference_rug_floorplane_depth_span_ratio"] = 0.9267611384
    a["reference_rug_floorplane_width_span_ratio"] = 1.0127562666
    a["reference_rug_largest_residual"] = "vertical_placement_and_depth_span_after_proof121"
    solver.reground(instance_id)


def _solve_fixed_visible_mask(camera, instance_id, target, params, authority):
    _apply_anisotropic(instance_id, params, authority)
    candidate = solver.projected_bbox(camera, instance_id)
    diagnostic_score = float(
        solver.bbox_objective(candidate, target)
        + solver.side_wall_fit_penalty(instance_id)
    )
    a = solver.anchor(instance_id)
    a["reference_target_center_xy"] = [float(target["center_x"]), float(target["center_y"])]
    a["reference_target_size_wh"] = [float(target["width"]), float(target["height"])]
    a["reference_screen_objective"] = diagnostic_score
    a["reference_screen_objective_role"] = "diagnostic_only_real_rendered_silhouette_is_visual_authority"
    return list(params), candidate, diagnostic_score


def _solve_rug_visible_mask(camera, target):
    _apply_rug_planar(
        "room_rug",
        RUG_PARAMS,
        "floor_plane_unprojection_from_real_instance_id_proof121",
    )
    candidate = solver.projected_bbox(camera, "room_rug")
    diagnostic_score = float(
        solver.bbox_objective(candidate, target)
        + solver.side_wall_fit_penalty("room_rug")
    )
    a = solver.anchor("room_rug")
    a["reference_target_center_xy"] = [float(target["center_x"]), float(target["center_y"])]
    a["reference_target_size_wh"] = [float(target["width"]), float(target["height"])]
    a["reference_screen_objective"] = diagnostic_score
    a["reference_screen_objective_role"] = "diagnostic_only_real_rendered_silhouette_is_visual_authority"
    return list(RUG_PARAMS), candidate, diagnostic_score


def _solve_instance_rug_bounded(camera, instance_id, target):
    if instance_id == "room_dresser":
        a = solver.anchor(instance_id)
        a["reference_dresser_visible_bbox_target"] = [0.0, 0.184, 0.420, 0.718]
        return _solve_fixed_visible_mask(
            camera,
            instance_id,
            target,
            DRESSER_PARAMS,
            "accepted_real_instance_id_silhouette_proof104",
        )
    if instance_id == "room_lounge_chair":
        a = solver.anchor(instance_id)
        a["reference_chair_visible_bbox_proof111"] = [
            0.2136627907, 0.3335755814, 0.3727272727, 0.5272727273
        ]
        a["reference_chair_visible_bbox_target"] = [0.217, 0.333, 0.368, 0.508]
        return _solve_fixed_visible_mask(
            camera,
            instance_id,
            target,
            CHAIR_PARAMS,
            "accepted_real_instance_id_silhouette_proof111",
        )
    if instance_id == "room_rug":
        return _solve_rug_visible_mask(camera, target)
    return _previous_dispatch(camera, instance_id, target)


def _apply_reference_camera_shell_cutaway():
    camera = bpy.data.objects.get(solver.CAMERA_NAME)
    ceiling = bpy.data.objects.get("room_shell_ceiling")
    left = bpy.data.objects.get("room_shell_left")
    right = bpy.data.objects.get("room_shell_right")
    root = bpy.data.objects.get(solver.ROOT_NAME)
    if camera is None or camera.type != "CAMERA":
        raise RuntimeError("clean-baseline shell cutaway requires the solved reference camera")
    if not bool(camera.get("reference_solved", False)):
        raise RuntimeError("clean-baseline shell cutaway refuses an unsolved proof camera")
    for name, obj in (("ceiling", ceiling), ("left", left), ("right", right)):
        if obj is None or not bool(obj.get("room_shell", False)):
            raise RuntimeError(f"clean-baseline shell cutaway requires builder-owned {name} shell")
    if root is None:
        raise RuntimeError("clean-baseline shell cutaway requires room_world_root")

    camera_y = float(camera.location.y)
    if camera_y <= CANONICAL_FRONT_PLANE_Y:
        desired_front = CANONICAL_FRONT_PLANE_Y
    else:
        desired_front = camera_y + REFERENCE_CAMERA_SHELL_MARGIN_M
    overhang = desired_front - CANONICAL_FRONT_PLANE_Y
    if overhang < -1.0e-6 or overhang > MAX_CAMERA_SHELL_OVERHANG_M:
        raise RuntimeError(
            f"unsafe solved-camera shell overhang {overhang:.4f} m for camera y={camera_y:.4f}"
        )

    new_depth = desired_front - CANONICAL_BACK_PLANE_Y
    new_center_y = (CANONICAL_BACK_PLANE_Y + desired_front) * 0.5
    for obj in (ceiling, left, right):
        obj.location.y = new_center_y
        dims = obj.dimensions.copy()
        dims.y = new_depth
        obj.dimensions = dims
        obj["reference_camera_cutaway"] = True
        obj["canonical_clear_depth_m"] = 4.20
        obj["canonical_back_plane_y"] = CANONICAL_BACK_PLANE_Y
        obj["canonical_front_plane_y"] = CANONICAL_FRONT_PLANE_Y
        obj["camera_cutaway_front_y"] = desired_front
        obj["camera_cutaway_overhang_m"] = overhang
        obj["camera_cutaway_margin_m"] = REFERENCE_CAMERA_SHELL_MARGIN_M

    root["reference_camera_shell_cutaway"] = True
    root["reference_camera_shell_overhang_m"] = overhang
    root["reference_camera_shell_extended_members"] = [
        "room_shell_ceiling", "room_shell_left", "room_shell_right"
    ]
    bpy.context.view_layer.update()
    print(
        "CELINE_ROOM_CAMERA_SHELL_CUTAWAY PASS "
        f"camera_y={camera_y:.4f} canonical_front={CANONICAL_FRONT_PLANE_Y:.4f} "
        f"shell_front={desired_front:.4f} overhang={overhang:.4f} "
        "members=ceiling,left,right",
        flush=True,
    )


solver.solve_instance = _solve_instance_rug_bounded
solver.main()
_apply_reference_camera_shell_cutaway()
