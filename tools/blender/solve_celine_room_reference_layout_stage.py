#!/usr/bin/env python3
"""Proof #104 dresser visible-silhouette interpolation refinement.

The prior staged solver remains byte-for-byte in
solve_celine_room_reference_layout_stage_base.py. This wrapper loads it without
running the final main() and changes only the derived room_dresser anchor.

Proof #102 and Proof #103 bracket the authoritative rendered dresser silhouette
on the same exact camera/reference grid:

Proof #102: right=0.173091, top=0.442220, bottom=0.731574, height=0.289354
Proof #103: right=0.181091, top=0.402184, bottom=0.708826, height=0.306642
Target:     right=0.184000, top=0.420000, bottom=0.718000, height=0.298000

A 0.55 interpolation between the two measured depth/vertical-scale states lands
at user-Z=-0.077 and vertical scale=0.8602543, predicting top~=0.4202,
bottom~=0.7191 and height~=0.2989. At that same depth the visible right edge is
predicted ~=0.17749, so increase only horizontal scale by 0.184/0.17749 to
0.8235394. Keep Proof #92 X/yaw. This is a bounded derived-anchor correction
from two real rendered instance-ID masks, not eyeballing or projected-AABB
acceptance. Original Kommode.glb bytes remain immutable; no child/proof-only
geometry transform is introduced.
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
    -0.077,                    # interpolated Proof #102/#103 depth
    0.8235393808004019,        # visible-mask horizontal correction
    0.8602542716363035,        # interpolated visible-height scale
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
    a["reference_dresser_visual_fit_authority"] = "real_instance_id_masks_proof102_proof103_interpolation"
    a["reference_dresser_visible_bbox_proof102"] = [0.0, 0.1730909091, 0.4422202002, 0.7315741583]
    a["reference_dresser_visible_bbox_proof103"] = [0.0, 0.1810909091, 0.4021838035, 0.7088262056]
    a["reference_dresser_visible_bbox_target"] = [0.0, 0.184, 0.420, 0.718]
    a["reference_dresser_interpolation_fraction"] = 0.55
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


def _solve_instance_proof104(camera, instance_id, target):
    if instance_id == "room_dresser":
        return _solve_dresser_visible_mask_calibrated(camera, target)
    return _previous_dispatch(camera, instance_id, target)


solver.solve_instance = _solve_instance_proof104
solver.main()
