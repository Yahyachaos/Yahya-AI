#!/usr/bin/env python3
"""Staged extension of the canonical reference-constrained room solver.

This module deliberately reuses the canonical projection solver instead of
applying proof-time furniture transforms. It adds only measured secondary
objects to the same auditable screen-space solve. Source GLB bytes remain
immutable and accepted transforms are written on the normal instance anchors
by the base solver.

Proof #47 confirmed the mirror against its measured far-left target. Proof #48
then moved the front nightstand from the wrong left edge to the required right
bedside zone. The largest remaining medium-or-higher-confidence whole-scene
screen-space mismatch is now `room_plant_large`: it is still right of center
while Refernzbild.png places the tall plant on the left beside the dresser/
chair/window group. Solve that single cross-room error next while preserving
the already solved camera and furniture anchors.
"""

from importlib.util import module_from_spec, spec_from_file_location
from pathlib import Path

BASE_SOLVER = Path(__file__).with_name("solve_celine_room_reference_layout.py")

spec = spec_from_file_location("celine_room_reference_solver_base", BASE_SOLVER)
if spec is None or spec.loader is None:
    raise RuntimeError(f"cannot load canonical room solver: {BASE_SOLVER}")
solver = module_from_spec(spec)
spec.loader.exec_module(solver)

# Positive user-X projects toward image-left for the solved reference camera.
# Keep the mirror on the physical +X side wall and facing into the room.
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

# Proof #47 -> #48 corrected the gross horizontal nightstand inversion.
# Keep it in the right bedside zone and continue solving it against the same
# measured bbox on every exact-room render so later changes cannot regress it.
solver.SOLVE_LIMITS["room_nightstand_front"] = {
    "wall": False,
    "bounds": [
        (-2.15, -1.15),
        (-0.20, 1.35),
        (0.15, 0.80),
        (45.0, 135.0),
    ],
    "steps": [0.12, 0.18, 0.08, 7.5],
}

# Proof #48 current large-plant projection is approximately:
#   center=(0.7431, 0.3863), size=(0.1705, 0.3112)
# Reference target (medium confidence):
#   center=(0.190, 0.370), size=(0.115, 0.330)
# This is a gross side inversion rather than a micro-size defect. Positive
# user-X is the reference-left side for this camera. The plant remains a floor
# object; the base solver re-grounds the accepted derived anchor exactly.
solver.SOLVE_LIMITS["room_plant_large"] = {
    "wall": False,
    "bounds": [
        (1.05, 2.15),     # left dresser/chair/window side
        (-1.80, -0.20),   # back/left room depth band
        (0.45, 1.30),     # uniform derived scale
        (-45.0, 45.0),    # plant yaw has no semantic facing requirement
    ],
    "steps": [0.14, 0.18, 0.08, 7.5],
}

for instance_id in (
    "room_round_mirror",
    "room_nightstand_front",
    "room_plant_large",
):
    if instance_id not in solver.PRIMARY_IDS:
        solver.PRIMARY_IDS.append(instance_id)

solver.main()
