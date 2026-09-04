#!/usr/bin/env python3
"""Proof #103 dresser visible-silhouette depth/height refinement.

The prior staged solver remains byte-for-byte in
solve_celine_room_reference_layout_stage_base.py. This wrapper loads it without
running the final main() and changes only the derived room_dresser anchor.

Proof #101 proved projected object-AABB corners are not a valid dresser visual
proxy. Proof #102 therefore calibrated from the real rendered instance-ID mask
and improved the visible cabinet from Proof #92 bbox
x=0.000000..0.167273, y=0.463148..0.721565 to Proof #102
x=0.000000..0.173091, y=0.442220..0.731574 against the authoritative target
x=0.000000..0.184000, y=0.420000..0.718000.

The remaining error is now primarily depth/vertical projection: the grounded
bottom is +0.01357 H too low and the clipped right edge is -0.01091 W too far
left. With the fixed Proof #92 camera, translating the same left-side world
branch from user-Z 0.00 to -0.14 predicts roughly -0.0137 H at the floor and
+0.011 W at the visible right edge, addressing both errors together. Because a
deeper grounded object shrinks vertically, raise only vertical anchor scale from
0.8328094 to 0.8827092 so the post-depth visible height remains near the measured
0.298 target. Preserve Proof #102 horizontal scale, X and yaw. Original
Kommode.glb bytes remain immutable and no child/proof-only geometry transform is
introduced.
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
    2.1353125,                 # Proof #92 user X
    -0.14,                     # bounded deeper correction from Proof #102 mask delta
    0.7944062683582307,        # Proof #102 measured horizontal mask scale
    0.8827092055304706,        # depth-compensated visible-height scale
    87.71484625447816,         # Proof #92 user yaw
]


def _apply_dresser_visible_mask_calibration(params):
    instance_id = "room_dresser"
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
    a["reference_dresser_visual_fit_authority"] = "real_instance_id_mask_proof102"
    a["reference_dresser_visible_bbox_proof102"] = [0.0, 0.1730909091, 0.4422202002, 0.7315741583]
    a["reference_dresser_visible_bbox_target"] = [0.0, 0.184, 0.420, 0.718]
    a["reference_dresser_depth_delta_user_z"] = -0.14
    a["reference_dresser_vertical_depth_compensation"] = 1.0599174985
    solver.reground(instance_id)


def _solve_dresser_visible_mask_calibrated(camera, target):
    params = list(DRESSER_PARAMS)
    _apply_dresser_visible_mask_calibration(params)
    candidate = solver.projected_bbox(camera, "room_dresser")
    diagnostic_score = float(
        solver.bbox_objective(candidate, target)
        + solver.side_wall_fit_penalty("room_dresser")
    )
    a = solver.anchor("room_dresser")
    a["reference_target_center_xy"] = [float(target["center_x"]), float(target["center_y"])]
    a["reference_target_size_wh"] = [float(target["width"]), float(target["height"])]
    a["reference_screen_objective"] = diagnostic_score
    a["reference_screen_objective_role"] = "diagnostic_only_for_dresser_after_proof101"
    return params, candidate, diagnostic_score


def _solve_instance_proof103(camera, instance_id, target):
    if instance_id == "room_dresser":
        return _solve_dresser_visible_mask_calibrated(camera, target)
    return _previous_dispatch(camera, instance_id, target)


solver.solve_instance = _solve_instance_proof103
solver.main()
