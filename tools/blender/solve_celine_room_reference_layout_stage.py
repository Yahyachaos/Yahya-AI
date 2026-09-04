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
from 11.489 to 26.004. Keep the widened feasible envelope, but include the exact
Proof #58 candidate as a non-regression seed and add bounded depth multistarts
around it so the optimizer can search the useful foreground interval without
losing the best measured state.
"""

from importlib.util import module_from_spec, spec_from_file_location
from pathlib import Path

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
# terms. Keep the source grounded and search the normal derived anchor near the
# back/window zone; do not move child geometry or apply proof-time transforms.
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

# Proof #58: yaw multistart improved no further because the table remained
# saturated at user-Z=2.10 while its top was still 0.739 (target 0.782) and
# width 0.845 (target 1.000). The open camera-facing side of the room has no
# front wall; allow this foreground anchor to approach the camera, but keep a
# conservative 0.32 m anchor clearance from the solved camera at user-Z≈2.87.
solver.SOLVE_LIMITS["room_foreground_table"] = {
    "wall": False,
    "bounds": [
        (-0.75, 0.75),
        (1.15, 2.55),
        (0.20, 1.80),
        (-100.0, 100.0),
    ],
    "steps": [0.20, 0.18, 0.15, 15.0],
}

_base_solve_instance = solver.solve_instance
_base_bbox_objective = solver.bbox_objective


def _floor_lamp_visible_objective(candidate, target):
    if candidate is None:
        return 1e9
    return (
        ((candidate["center_x"] - float(target["center_x"])) / 0.020) ** 2 +
        ((candidate["top"] - float(target["top"])) / 0.018) ** 2 +
        ((candidate["width"] - float(target["width"])) / 0.050) ** 2
    )


def _solve_foreground_table_multistart(camera, target):
    instance_id = "room_foreground_table"
    original = solver.current_anchor_params(instance_id, wall=False)

    # Exact measured candidate from Proof #58. Because the architecture/camera
    # solve is deterministic, retaining this seed makes the widened search
    # fail-safe: a later multistart can improve it, but cannot regress past it.
    proof58 = [0.30625, 2.10, 0.5199218988418579, 5.859375]
    depth_seeds = [2.10, 2.22, 2.34, 2.46]
    seeds = [
        list(original),
        [original[0], original[1], original[2], 90.0],
        [original[0], original[1], original[2], -90.0],
        list(proof58),
    ]
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
    return best_params, final_box, final_score


def _solve_instance_staged(camera, instance_id, target):
    if instance_id == "room_foreground_table":
        return _solve_foreground_table_multistart(camera, target)
    if instance_id != "room_floor_lamp":
        return _base_solve_instance(camera, instance_id, target)
    solver.bbox_objective = _floor_lamp_visible_objective
    try:
        return _base_solve_instance(camera, instance_id, target)
    finally:
        solver.bbox_objective = _base_bbox_objective


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
