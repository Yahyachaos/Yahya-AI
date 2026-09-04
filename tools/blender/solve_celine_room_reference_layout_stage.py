#!/usr/bin/env python3
"""Proof #102 bounded dresser calibration from the real instance-ID silhouette.

The prior staged solver is preserved byte-for-byte in
solve_celine_room_reference_layout_stage_base.py. This wrapper loads that exact
stage without executing its final solver.main(), then changes only the derived
room_dresser anchor.

Proof #101 disproved using projected object AABB corners as dresser visual
acceptance. Its numerical bbox became almost exact, yet direct inspection of the
real instance-ID render showed the visible cabinet shorter and lower than the
reference. The reason is source-box empty-corner projection: the projected
object bound overstates the occupied dresser silhouette after yaw.

Use the actual rendered Proof #92 instance-ID dresser component instead:
visible bbox x=0.000000..0.167273, y=0.463148..0.721565. The authoritative
reference target is x=0.000000..0.184000, y=0.420000..0.718000. With the source
already grounded and left-clipped, the measured scale ratios are therefore
0.184/0.167273 = 1.100000 horizontally and 0.298/0.258417 = 1.153176 vertically.
Apply those ratios to the accepted Proof #92 uniform scale 0.7221875, yielding
horizontal scale 0.7944063 and vertical scale 0.8328094. Keep Proof #92 X,
depth and yaw fixed for this bounded test. Source Kommode.glb bytes remain
immutable; no child-geometry or proof-only hidden transform is introduced.
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

# Exact Proof #92 world branch; only scale split changes.
DRESSER_PARAMS = [
    2.1353125,                 # user X
    0.0,                       # user Z/depth
    0.7944062683582307,        # horizontal X/Y footprint from real mask ratio
    0.8328093524323216,        # vertical Z height from real mask ratio
    87.71484625447816,         # user yaw
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
    a["reference_dresser_visual_fit_authority"] = "real_instance_id_mask_proof92"
    a["reference_dresser_visible_bbox_proof92"] = [0.0, 0.1672727273, 0.4631483167, 0.7215650591]
    a["reference_dresser_visible_bbox_target"] = [0.0, 0.184, 0.420, 0.718]
    a["reference_dresser_horizontal_scale_ratio"] = 1.1
    a["reference_dresser_vertical_scale_ratio"] = 1.1531760563
    # Preserve exact floor contact after vertical scale changes.
    solver.reground(instance_id)


def _solve_dresser_visible_mask_calibrated(camera, target):
    params = list(DRESSER_PARAMS)
    _apply_dresser_visible_mask_calibration(params)
    # Keep the legacy projected-AABB numbers for diagnostics only. They are not
    # dresser visual acceptance after Proof #101 disproved that proxy.
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


def _solve_instance_proof102(camera, instance_id, target):
    if instance_id == "room_dresser":
        return _solve_dresser_visible_mask_calibrated(camera, target)
    return _previous_dispatch(camera, instance_id, target)


solver.solve_instance = _solve_instance_proof102
solver.main()
