#!/usr/bin/env python3
"""Proof #93 bounded dresser extension for the canonical staged room solver.

The prior staged solver is preserved byte-for-byte in
solve_celine_room_reference_layout_stage_base.py. This wrapper loads that exact
stage without executing its final solver.main(), then adds one measured change:
a grounded anisotropic anchor solve for room_dresser.

Proof #92 (head fe8fc5bbb6c741410bfd0b4c26fd6766b818bd63) measured the dresser at
left/right/top/bottom = 0.000/0.183361/0.387747/0.737558 against the authoritative
Refernzbild.png target 0.000/0.184/0.420/0.718. Horizontal coverage is already
essentially exact, while projected height is 0.349811 versus 0.298 (+0.051811,
about +17.4%). A uniform scale cannot reduce that vertical error without
regressing the accepted width. Solve horizontal footprint and vertical height
independently on the normal instance anchor, re-grounding every trial. Source
Kommode.glb bytes remain immutable; no proof-time hidden geometry is introduced.
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


def _apply_dresser_anisotropic_params(params):
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
    # Vertical anchor scale changes the source pivot-to-floor offset. Score only
    # actual grounded candidates, matching the corrected lamp/bed solve contract.
    solver.reground(instance_id)


def _solve_dresser_from_seed(camera, target, seed):
    p = list(seed)
    bounds = [
        (1.70, 2.20),
        (-0.70, 0.10),
        (0.58, 0.92),
        (0.45, 0.80),
        (70.0, 105.0),
    ]
    steps = [0.08, 0.08, 0.035, 0.035, 3.0]
    p = solver.clamp_params_to_bounds(p, bounds)
    _apply_dresser_anisotropic_params(p)
    best_box = solver.projected_bbox(camera, "room_dresser")
    best = solver.bbox_objective(best_box, target) + solver.side_wall_fit_penalty("room_dresser")

    for _ in range(8):
        improved = True
        guard = 0
        while improved and guard < 80:
            guard += 1
            improved = False
            for i in range(len(p)):
                for sign in (-1.0, 1.0):
                    cand = list(p)
                    cand[i] = solver.clamp(cand[i] + sign * steps[i], *bounds[i])
                    _apply_dresser_anisotropic_params(cand)
                    box = solver.projected_bbox(camera, "room_dresser")
                    score = solver.bbox_objective(box, target) + solver.side_wall_fit_penalty("room_dresser")
                    if score + 1e-8 < best:
                        p, best, best_box, improved = cand, score, box, True
                    else:
                        _apply_dresser_anisotropic_params(p)
        steps = [s * 0.5 for s in steps]

    _apply_dresser_anisotropic_params(p)
    final_box = solver.projected_bbox(camera, "room_dresser")
    final_score = float(
        solver.bbox_objective(final_box, target)
        + solver.side_wall_fit_penalty("room_dresser")
    )
    return p, final_box, final_score


def _solve_dresser_anisotropic(camera, target):
    # Preserve Proof #92 as an explicit non-regression seed and add the measured
    # vertical-ratio seed (0.7221875 * 0.298 / 0.349811 ~= 0.6153). Coordinate
    # descent then solves depth/position/footprint around both grounded branches.
    proof92_uniform = [2.1353125, 0.0, 0.7221875166893006, 0.7221875166893006, 87.71484625447816]
    measured_vertical = [2.1353125, 0.0, 0.7221875166893006, 0.6153, 87.71484625447816]
    candidates = [
        _solve_dresser_from_seed(camera, target, proof92_uniform),
        _solve_dresser_from_seed(camera, target, measured_vertical),
    ]
    best = min(candidates, key=lambda item: float(item[2]))
    params = list(best[0])
    _apply_dresser_anisotropic_params(params)
    final_box = solver.projected_bbox(camera, "room_dresser")
    final_score = float(
        solver.bbox_objective(final_box, target)
        + solver.side_wall_fit_penalty("room_dresser")
    )
    a = solver.anchor("room_dresser")
    a["reference_target_center_xy"] = [float(target["center_x"]), float(target["center_y"])]
    a["reference_target_size_wh"] = [float(target["width"]), float(target["height"])]
    a["reference_screen_objective"] = final_score
    a["reference_dresser_fit_terms"] = (
        "full_bbox_with_independent_horizontal_vertical_scale; every trial regrounded; "
        "proof92_uniform_non_regression_seed"
    )
    return params, final_box, final_score


def _solve_instance_proof93(camera, instance_id, target):
    if instance_id == "room_dresser":
        return _solve_dresser_anisotropic(camera, target)
    return _previous_dispatch(camera, instance_id, target)


solver.solve_instance = _solve_instance_proof93
solver.main()
