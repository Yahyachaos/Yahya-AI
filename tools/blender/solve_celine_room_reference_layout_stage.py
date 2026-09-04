#!/usr/bin/env python3
"""Staged extension of the canonical reference-constrained room solver.

This module deliberately reuses the canonical projection solver instead of
applying proof-time furniture transforms. It adds only measured secondary
objects to the same auditable screen-space solve. Source GLB bytes remain
immutable and accepted transforms are written on the normal instance anchors
by the base solver.

Proof #47 confirmed the mirror correction against the real Blender render: its
projected bbox now matches the measured far-left target essentially exactly.
The largest remaining measured screen-space error is `room_nightstand_front`:
it is still a large left-edge object while Refernzbild.png requires a compact
nightstand clipped by the far-right edge. Solve that one next without changing
camera, primary furniture, source GLBs, or proof-time geometry.
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
        (1.95, 2.20),     # user X: left side-wall mounting plane
        (-0.60, 0.85),    # user Z depth: visible left-wall span
        (0.85, 2.15),     # user Y height
        (0.15, 0.85),     # derived uniform instance scale
        (-115.0, -65.0),  # inward-facing side-wall yaw branch
    ],
    "steps": [0.05, 0.18, 0.16, 0.08, 5.0],
}

# Proof #47 / reference_solve.json:
#   candidate center=(0.1016, 0.5370), size=(0.2032, 0.4009)
#   target    center=(0.9580, 0.6060), size=(0.0840, 0.2000)
# The target is medium-confidence but the opposite-side error is gross and
# visually unambiguous. Negative user-X is the physical right side in this
# camera presentation. Keep the original nightstand yaw branch and let the
# canonical solver fit only X/Z/scale/yaw inside a bounded bedside zone.
solver.SOLVE_LIMITS["room_nightstand_front"] = {
    "wall": False,
    "bounds": [
        (-2.15, -1.15),   # user X: right bedside zone
        (-0.20, 1.35),    # user Z depth
        (0.15, 0.80),     # derived uniform instance scale
        (45.0, 135.0),    # preserve the canonical front-facing yaw branch
    ],
    "steps": [0.12, 0.18, 0.08, 7.5],
}

for instance_id in ("room_round_mirror", "room_nightstand_front"):
    if instance_id not in solver.PRIMARY_IDS:
        solver.PRIMARY_IDS.append(instance_id)

solver.main()
