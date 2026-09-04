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
bed-side zone, and #55 moved the dresser onto the deeper measured branch.
Direct inspection of Proof #55 now leaves the floor lamp as the largest measured
systematic mismatch. Its full projected bbox is y=0.382..0.519 while the visible
reference lamp is y=0.273..0.420. The lower pole/base is occluded by the lounge
chair in Refernzbild.png, so fitting the hidden full-object bottom/height against
the visible target creates a false local minimum. Fit this grounded source by
its reliable visible top/center/width while leaving the hidden lower extent free
to remain behind the chair. Preserve camera, all other solved anchors, source
GLBs and proof-time geometry.
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

# The wall shelf is partially occlusion-free and can use its ordinary full bbox.
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


def _solve_instance_occlusion_aware(camera, instance_id, target):
    if instance_id != "room_floor_lamp":
        return _base_solve_instance(camera, instance_id, target)
    solver.bbox_objective = _floor_lamp_visible_objective
    try:
        return _base_solve_instance(camera, instance_id, target)
    finally:
        solver.bbox_objective = _base_bbox_objective


solver.solve_instance = _solve_instance_occlusion_aware

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

# Proof #54 dresser candidate y=0.491..0.808 while target y=0.420..0.718.
# Keep the already successful deeper branch from Proof #55.
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
