#!/usr/bin/env python3
"""Bounded rug rendered-silhouette correction on the accepted room branch.

The prior staged solver remains byte-for-byte in
solve_celine_room_reference_layout_stage_base.py. This wrapper preserves the
accepted Proof #104 dresser transform and Proof #111 front-facing grounded chair
transform, then changes only the derived room_rug anchor.

Proof #118 tested the first planar rug correction against the real rendered
instance-ID silhouette. Its largest remaining error is horizontal width:
current x=0.224564..0.846657 (width 0.622093) versus the authoritative reference
target x=0.2055..0.8705 (width 0.665). The rug is therefore still about 6.45%
too narrow. Its vertical residual is smaller and is deliberately not corrected
in this same bounded step.

Keep the accepted rug X/depth/yaw and the Proof #118 depth/thickness scales.
Correct only local X from 1.6871853720 by the measured real-render ratio
0.665/0.6220930233, yielding 1.8035538584. Preserve local Y/depth
1.4997197613, local Z/thickness 1.6410156727 and exact floor grounding. Original
rug GLB bytes remain immutable; no child/proof-only geometry transform is
introduced. Projected object-AABB acceptance remains diagnostic only; actual
rendered silhouette remains visual authority.
"""

from pathlib import Path
import math

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
    -0.0671875,
    -0.2953125,
    1.8035538584164221,
    1.499719761334807,
    1.6410156726837158,
    5.8203125,
]


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
    a["reference_rug_visible_bbox_proof111"] = [
        0.2151162791, 0.8611918605, 0.5036363636, 0.8036363636
    ]
    a["reference_rug_visible_bbox_proof118"] = [
        0.2245639535, 0.8466569767, 0.5081818182, 0.7727272727
    ]
    a["reference_rug_visible_bbox_target"] = [0.2055, 0.8705, 0.5205, 0.7955]
    a["reference_rug_width_ratio_proof118"] = 1.0689719626
    a["reference_rug_largest_residual"] = "width_only_before_next_exact_render"
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
        "real_instance_id_silhouette_proof118_width_residual",
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


solver.solve_instance = _solve_instance_rug_bounded
solver.main()
