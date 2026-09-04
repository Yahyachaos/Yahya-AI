#!/usr/bin/env python3
"""Staged extension of the canonical reference-constrained room solver.

This module deliberately reuses the canonical projection solver instead of
applying proof-time furniture transforms. It adds only measured secondary
objects to the same auditable screen-space solve. Source GLB bytes remain
immutable and accepted transforms are written on the normal instance anchors
by the base solver.

Proof #47 confirmed the mirror against its measured far-left target. Proof #48
moved the front nightstand from the wrong left edge to the required right-side
bedside zone. Proof #49 then corrected the large plant's gross side inversion.
The largest remaining medium-confidence whole-scene error with a clean semantic
cause is now the front nightstand's depth: its projected bbox is still about
12% of image height too low because the previous bound allowed it to drift to
user-Z +1.32 m, implausibly close to the proof camera. Keep it on the right, but
constrain it to the actual bedside depth band and let the same measured bbox
solve X/Z/scale/yaw there. Preserve camera, primary furniture, source GLBs and
all proof-time geometry.
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

# Proof #49 reference_solve.json:
#   candidate center=(0.9500, 0.7251), size=(0.1000, 0.2041)
#   target    center=(0.9580, 0.6060), size=(0.0840, 0.2000)
# Horizontal side and projected size are now close, but the object remains far
# too low. The accepted bed itself solves around user-Z -0.44 m, and this
# nightstand belongs beside the bed/headboard rather than in the near-camera
# foreground. Narrow only the semantic depth band; keep the same measured target
# and uniform derived transform solve.
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

# Proof #48 -> #49 corrected the large plant's gross side inversion. Keep it
# solved against the same measured medium-confidence target on every later
# exact-room render so subsequent secondary-object work cannot regress it.
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

for instance_id in (
    "room_round_mirror",
    "room_nightstand_front",
    "room_plant_large",
):
    if instance_id not in solver.PRIMARY_IDS:
        solver.PRIMARY_IDS.append(instance_id)

solver.main()
