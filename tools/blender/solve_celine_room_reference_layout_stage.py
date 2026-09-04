#!/usr/bin/env python3
"""Staged extension of the canonical reference-constrained room solver.

This module deliberately reuses the canonical projection solver instead of
applying proof-time furniture transforms. It adds only measured secondary
objects to the same auditable screen-space solve. Source GLB bytes remain
immutable and accepted transforms are written on the normal instance anchors
by the base solver.

Proof #47 confirmed the mirror, #48/#50 corrected the front nightstand, #49
corrected the large plant, #51 corrected the small plant from a large
left-floor object to the measured tiny right-bedside target, #52 fitted the
wall shelf closely to its measured target, #53 moved the floor-lamp anchor
toward the back/window zone, #54 moved the rear bedside unit to the correct
bed-side zone, #55 moved the dresser onto the deeper measured branch, and #56
moved the visible floor-lamp top from y=0.382 to y=0.295 (target y=0.273) by
fitting the visible, non-occluded landmarks rather than its hidden base.

Proof #57 confirmed that merely widening the foreground-table yaw bounds is not
enough: coordinate descent stayed in the same yaw≈5.9° local minimum, still
width=0.845 versus target=1.000 and top=0.739 versus target=0.782. Proof #58
confirmed the multistart orientation search still lands at the same near-depth
saturation (user-Z=2.10). Proof #59 then proved that widening depth alone is not
a safe search: a fresh builder seed converged all the way to user-Z=2.55,
clipped almost the whole table out of the frame, and regressed the objective
from 11.489 to 26.004. Proof #60 recovered a numerically exact clipped bbox at
user-Z=2.37, but direct inspection of the instance-ID render showed the table
as a strongly diagonal triangular/trapezoid foreground mass. The reference and
Proof #58 both show the tabletop's far edge approximately horizontal. Preserve
the widened depth search but constrain the source orientation to that visually
correct near-frontal yaw branch; bbox equality alone is not visual acceptance.

Proof #61 then made the largest remaining raw visible delta explicit: the
canonical floor-lamp top was close vertically, but its projected width was
0.1066 versus the measured 0.0360. A uniform anchor scale cannot independently
match the source lamp's height and narrow reference silhouette. Keep the source
GLB bytes untouched and solve an auditable anisotropic *anchor* scale for this
one instance: horizontal X/Y footprint and vertical Z height are optimized
independently, with floor contact preserved. This is runtime layout state, not a
proof-time geometry mutation.
"""

from importlib.util import module_from_spec, spec_from_file_location
from pathlib import Path
import math

import bpy

BASE_SOLVER = Path(__file__).with_name("solve_celine_room_reference_layout.py")

spec = spec_from_file_location("celine_room_reference_solver_base", BASE_SOLVER)
if spec is None or spec.loader is None:
    raise RuntimeError(f"cannot load canonical room solver: {BASE_SOLVER}")
solver = module_from_spec(spec)
spec.loader.exec_module(solver)

solver.SOLVE_LIMITS["room_round_mirror"] = {
    "wall": True,
    "bounds": [
        (1.95, 2.20),
        (-0.60, 0.85),
        (0.85, 2.15),
        (0.15, 0.85),
        (-115.0, -65.0),
    ],
    "steps": [0.05, 0.18, 0.16, 0.08, 5.0],
}

solver.SOLVE_LIMITS["room_nightstand_front"] = {
    "wall": False,
    "bounds": [
        (-2.15, -1.15),
        (-1.10, 0.55),
        (0.15, 0.80),
        (45.0, 135.0),
    ],
    "steps": [0.12, 0.18, 0.08, 7.5],
}

solver.SOLVE_LIMITS["room_plant_large"] = {
    "wall": False,
    "bounds": [
        (1.05, 2.15),
        (-1.80, -0.20),
        (0.45, 1.30),
        (-45.0, 45.0),
    ],
    "steps": [0.14, 0.18, 0.08, 7.5],
}

# The reference small plant is a tabletop/bedside object, not floor-standing.
solver.FLOOR_IDS.discard("room_plant_small")
solver.SOLVE_LIMITS["room_plant_small"] = {
    "wall": True,
    "bounds": [
        (-2.15, -1.30),
        (-0.95, 0.70),
        (0.45, 1.30),
        (0.10, 0.55),
        (-60.0, 60.0),
    ],
    "steps": [0.12, 0.18, 0.10, 0.06, 7.5],
}

solver.SOLVE_LIMITS["room_wall_shelf_books"] = {
    "wall": True,
    "bounds": [
        (-1.90, -0.45),
        (-2.08, -1.65),
        (1.20, 2.35),
        (0.15, 0.65),
        (-20.0, 20.0),
    ],
    "steps": [0.16, 0.08, 0.12, 0.06, 5.0],
}

# The reference floor lamp stands behind the lounge chair. Its lower pole/base is
# intentionally occluded, so only the visible top/center/width are reliable fit
# terms. The staged custom solve below preserves ground contact while allowing
# horizontal footprint and vertical height to differ at the normal instance
# anchor. The immutable source GLB itself is never edited.
solver.SOLVE_LIMITS["room_floor_lamp"] = {
    "wall": False,
    "bounds": [
        (1.35, 1.95),
        (-2.08, -1.35),
        (0.08, 0.85),
        (-30.0, 30.0),
    ],
    "steps": [0.10, 0.10, 0.08, 5.0],
}

# Proof #60 matched the clipped axis-aligned bbox only by rotating the tabletop
# to yaw -14.8°, which makes its far edge visibly diagonal. Proof #58's yaw
# +5.86° keeps that edge approximately horizontal like Refernzbild.png. Search
# depth/scale freely, but remain on that visually valid near-frontal branch.
solver.SOLVE_LIMITS["room_foreground_table"] = {
    "wall": False,
    "bounds": [
        (-0.75, 0.75),
        (1.15, 2.55),
        (0.20, 1.80),
        (-2.0, 12.0),
    ],
    "steps": [0.20, 0.18, 0.15, 2.0],
}

_base_solve_instance = solver.solve_instance


def _floor_lamp_visible_objective(candidate, target):
    if candidate is None:
        return 1e9
    return (
        ((candidate["center_x"] - float(target["center_x"])) / 0.020) ** 2 +
        ((candidate["top"] - float(target["top"])) / 0.018) ** 2 +
        ((candidate["width"] - float(target["width"])) / 0.025) ** 2
    )


def _apply_floor_lamp_params(params, reground_now=True):
    instance_id = "room_floor_lamp"
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
    if reground_now:
        solver.reground(instance_id)
    else:
        bpy.context.view_layer.update()


def _solve_floor_lamp_anisotropic(camera, target):
    instance_id = "room_floor_lamp"
    # Proof #61 supplies a deterministic location/yaw/height seed. Its uniform
    # 0.73 scale already placed the top near target; only the horizontal
    # silhouette needs strong compression. Search around that measured state.
    p = [1.8078125, -2.08, 0.25, 0.73, -19.9609375]
    bounds = [
        (1.35, 1.95),
        (-2.08, -1.35),
        (0.10, 0.55),
        (0.50, 1.10),
        (-30.0, 30.0),
    ]
    steps = [0.08, 0.08, 0.05, 0.06, 4.0]
    p = solver.clamp_params_to_bounds(p, bounds)
    _apply_floor_lamp_params(p, reground_now=True)
    best_box = solver.projected_bbox(camera, instance_id)
    best = _floor_lamp_visible_objective(best_box, target)

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
                    _apply_floor_lamp_params(cand, reground_now=False)
                    box = solver.projected_bbox(camera, instance_id)
                    score = _floor_lamp_visible_objective(box, target)
                    if score + 1e-8 < best:
                        p, best, best_box, improved = cand, score, box, True
                    else:
                        _apply_floor_lamp_params(p, reground_now=False)
        steps = [s * 0.5 for s in steps]

    _apply_floor_lamp_params(p, reground_now=True)
    final_box = solver.projected_bbox(camera, instance_id)
    final_score = float(_floor_lamp_visible_objective(final_box, target))
    a = solver.anchor(instance_id)
    a["reference_target_center_xy"] = [float(target["center_x"]), float(target["center_y"])]
    a["reference_target_size_wh"] = [float(target["width"]), float(target["height"])]
    a["reference_screen_objective"] = final_score
    a["reference_floor_lamp_fit_terms"] = "center_x,top,width"
    return p, final_box, final_score


def _solve_foreground_table_multistart(camera, target):
    instance_id = "room_foreground_table"
    original = solver.current_anchor_params(instance_id, wall=False)

    # Exact measured candidate from Proof #58. Because the architecture/camera
    # solve is deterministic, retaining this seed makes the widened search
    # fail-safe. The depth multistarts explore the missing foreground interval
    # while the yaw bounds preserve the directly verified horizontal-edge branch.
    proof58 = [0.30625, 2.10, 0.5199218988418579, 5.859375]
    depth_seeds = [2.10, 2.22, 2.34, 2.46]
    seeds = [list(original), list(proof58)]
    seeds.extend([[proof58[0], depth, proof58[2], proof58[3]] for depth in depth_seeds[1:]])

    best = None
    for seed in seeds:
        solver.apply_anchor_params(instance_id, seed, wall=False, reground_now=True)
        result = _base_solve_instance(camera, instance_id, target)
        if best is None or float(result[2]) < float(best[2]):
            best = result
    if best is None:
        raise RuntimeError("foreground table multistart produced no candidate")

    best_params = list(best[0])
    solver.apply_anchor_params(instance_id, best_params, wall=False, reground_now=True)
    final_box = solver.projected_bbox(camera, instance_id)
    final_score = float(
        solver.bbox_objective(final_box, target) +
        solver.side_wall_fit_penalty(instance_id)
    )
    a = solver.anchor(instance_id)
    a["reference_target_center_xy"] = [float(target["center_x"]), float(target["center_y"])]
    a["reference_target_size_wh"] = [float(target["width"]), float(target["height"])]
    a["reference_screen_objective"] = final_score
    a["reference_multistart_yaw_seeds_deg"] = [float(seed[3]) for seed in seeds]
    a["reference_multistart_depth_seeds"] = [float(seed[1]) for seed in seeds]
    a["reference_non_regression_seed"] = "proof58_exact_candidate"
    a["reference_visual_yaw_branch_deg"] = [-2.0, 12.0]
    return best_params, final_box, final_score


def _solve_instance_staged(camera, instance_id, target):
    if instance_id == "room_foreground_table":
        return _solve_foreground_table_multistart(camera, target)
    if instance_id == "room_floor_lamp":
        return _solve_floor_lamp_anisotropic(camera, target)
    return _base_solve_instance(camera, instance_id, target)


solver.solve_instance = _solve_instance_staged

# The rear bedside source is partly hidden by the bed. Its current target is
# intentionally coarse vertically; its bounded solve primarily fixes placement.
solver.SOLVE_LIMITS["room_nightstand_rear"] = {
    "wall": False,
    "bounds": [
        (-1.60, -0.30),
        (-1.80, -0.35),
        (0.15, 0.80),
        (45.0, 135.0),
    ],
    "steps": [0.16, 0.18, 0.08, 7.5],
}

# Keep the already successful deeper dresser branch from Proof #55.
solver.SOLVE_LIMITS["room_dresser"] = {
    "wall": False,
    "bounds": [
        (0.85, 2.20),
        (-0.90, 0.00),
        (0.25, 1.60),
        (45.0, 135.0),
    ],
    "steps": [0.18, 0.16, 0.12, 7.5],
}

for instance_id in (
    "room_round_mirror",
    "room_nightstand_front",
    "room_plant_large",
    "room_plant_small",
    "room_wall_shelf_books",
    "room_nightstand_rear",
):
    if instance_id not in solver.PRIMARY_IDS:
        solver.PRIMARY_IDS.append(instance_id)

solver.main()
