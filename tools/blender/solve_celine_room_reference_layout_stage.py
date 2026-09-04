#!/usr/bin/env python3
"""Staged extension of the canonical reference-constrained room solver.

This module deliberately reuses the canonical projection solver instead of
applying a proof-time transform.  It only adds the next high-confidence,
measured secondary object to the same screen-space solve.  Source GLB bytes
remain immutable and the accepted result is written on the normal instance
anchor by the base solver.

Proof #45 made the largest remaining measured geometry error unambiguous:
`room_round_mirror` is projected on the far-right side while Refernzbild.png
has the mirror clipped by the far-left image edge.  The target bbox is already
recorded with high confidence in CELINE_ROOM_REFERENCE_LAYOUT_TARGETS.json.
The bounds below encode only the physically valid +X side-wall mounting zone
for that measured left-edge presentation; position/height/scale/yaw are still
optimized by the canonical bbox objective.
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

if "room_round_mirror" not in solver.PRIMARY_IDS:
    solver.PRIMARY_IDS.append("room_round_mirror")

solver.main()
