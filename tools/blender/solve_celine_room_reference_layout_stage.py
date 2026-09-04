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
wall shelf closely to its measured target, and #53 moved the floor-lamp anchor
toward the back/window zone. Direct inspection of Proof #53 now exposes a more
important visible layout error than further lamp micro-tuning: the unsolved
`room_nightstand_rear` (the same canonical Nachttisch.glb source, including its
bedside lamp) still renders as a cyan cabinet/lamp beside the lounge chair on
the left. `/Refernzbild.png` instead shows that bedside unit behind the bed near
screen x≈0.70–0.79. Solve that normal derived anchor from the newly recorded,
explicitly occlusion-qualified target. Preserve camera, all other solved
anchors, source GLBs and proof-time geometry.
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

# Proof #51 current wall-shelf projection:
#   center=(0.6668, 0.2661), size=(0.1935, 0.1696)
# Reference target:
#   center=(0.662, 0.215), size=(0.103, 0.080)
# The shelf is visibly about twice the intended projected size. Keep it on the
# back/right wall band and solve the normal derived anchor; no child/proof hack.
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

# Proof #52 current floor-lamp projection:
#   center=(0.2713, 0.5035), size=(0.0320, 0.0602)
# Reference target:
#   center=(0.264, 0.347), size=(0.036, 0.147)
# Its horizontal placement is already close, but the grounded base is far too
# low because the old Z-depth bound stops at -1.55 m. The reference floor lamp
# stands in the back/window zone. Extend only the auditable anchor search toward
# the back wall and allow enough scale to match the measured projected height.
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

# Proof #53 instance-ID evidence shows the rear Nachttisch source still at the
# lounge-chair/left-window side (projected full bbox about x=0.158..0.288,
# y=0.332..0.561). The reference shows the same bedside-unit/lamp family behind
# the bed around x=0.704..0.789. The lower cabinet/legs are occluded by the bed,
# so the target records that vertical uncertainty explicitly; this bounded solve
# is primarily the large left/right placement correction and keeps floor contact.
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
