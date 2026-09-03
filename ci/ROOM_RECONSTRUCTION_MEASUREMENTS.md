# Celine Room Reconstruction Measurement Contract

Authority: `/Refernzbild.png` on the active canonical v80 branch. This document records image-space evidence and bounded transform decisions so room reconstruction does not regress into eyeballing.

## Proof baseline

- Baseline real-Blender proof: Exact Room 440x420 Blender Proof #13.
- Baseline head: `3de5afc7e894b305ebd7021ece9d43753189934c`.
- Grid: 1376 x 1100, exactly matching the reference grid.
- Proof camera: user `(0.00, 1.55, 3.60)`, target `(0.00, 0.95, -0.30)`, 24 mm / 36 mm sensor.
- Visual verdict: WHOLE-SCENE FAIL. A green Blender workflow is structural evidence only, not visual acceptance.

## Normalized image measurements (x/W, y/H)

| Feature | Reference target | Proof #13 current | Delta / decision |
|---|---:|---:|---|
| back-wall / ceiling boundary, center | y ~= 0.08 | y ~= 0.20 | still ~+0.12 too low; do not camera-polish until gross furniture layout is corrected |
| foreground tabletop visible top band | y ~= 0.78 | y ~= 0.64 | table begins ~0.14 too high in frame |
| foreground tabletop horizontal coverage | >= 0.95 W (cropped by both sides) | ~= 0.47 W | candidate is less than half the required apparent width |
| bed visible envelope | right half, roughly x 0.50..1.00 | central/right, roughly x 0.25..0.75 | bed group is displaced left and obstructs room center |
| right nightstand zone | x ~= 0.91..1.00 | large right furniture mass starts ~= 0.72 | right-side furniture scale/assignment remains wrong |
| lounge chair | left-middle, x ~= 0.21..0.33 | not cleanly readable in intended silhouette | blocked/miscomposed; defer until larger foreground/bed errors are reduced |
| window / drapes | central-left, broad opening x ~= 0.24..0.59 | central, x ~= 0.30..0.63 | coarse position is nearer than furniture, defer micro-correction |

These measurements are deliberately coarse until each major occluder is corrected. Re-measure from the next real proof rather than carrying guessed deltas forward.

## Bounded correction 1 — foreground table

Evidence: Proof #13 shows `Tischfürlaptop.glb` occupying only about 47% of frame width and starting around y=0.64, while the reference foreground table is a near-full-width cropped strip whose top starts around y=0.78. With the proof camera fixed for this iteration, the highest-confidence correction is to bring the canonical derived table instance toward the front/camera and increase its derived uniform scale. The original GLB bytes remain untouched.

Approved trial transform for Proof #14:

- `room_foreground_table.location`: `(0.00, 0.36, 1.55)` -> `(0.00, 0.36, 2.05)`
- `room_foreground_table.scale`: `0.68` -> `1.10`
- rotation unchanged at `0 deg`
- source remains immutable `Tischfürlaptop.glb` from pinned commit `df50816187978cbf5faf818ad484c3f682be7588`

Proof #14 showed that this uniform correction achieved the needed horizontal coverage but over-occluded the room vertically. Proof #15 therefore kept the measured wide projection as a derived child calibration while returning the effective table geometry to z-depth `1.55` and effective user scale `(1.45, 0.68, 0.68)` for X / height / depth. Original GLB bytes remained untouched.

## Proof #15 — manually inspected 2026-09-03

- Exact Room 440x420 Blender Proof #15 / run `33800454371`.
- Exact head: `27beefe23d4e5537b4319f3a88d1ef1eb58c1850`.
- Artifact: `9911173760`, digest `sha256:69ef51bdddc70c885b10ccb9d3ea3781a052225944727b133a1037ea469e7c13`.
- Structural result: SUCCESS. All 12 pinned original GLBs materialized; builder reported `CELINE_ROOM_440x420 PASS`; the reference-comparable Blender primary image was rendered on the exact 1376 x 1100 grid.
- Manual visual verdict: WHOLE-SCENE FAIL.

Measured/visible deltas after Proof #15:

| Feature | Reference | Proof #15 | Evidence-backed conclusion |
|---|---:|---:|---|
| foreground table horizontal coverage | >= 0.95 W, cropped at both sides | approximately full width | horizontal table coverage is substantially improved |
| foreground table upper/far visible band | y ~= 0.78 | y ~= 0.64 | still about 0.14 H too high; not accepted |
| round wall mirror | far left wall, center near x ~= 0.04 | far right wall, center near x ~= 0.96 | near-exact screen-space X inversion |
| bed mass | right half, x roughly 0.50..1.00 | central/left, x roughly 0.25..0.75 | same X-direction mismatch as mirror |
| dresser/left-side mass | left side in reference | corresponding side-specific mass resolves on the opposite side / is obscured | same global handedness error must be tested before per-object X nudges |
| window | central-left | central/right-biased under current front-camera handedness | secondary to the global X-direction mismatch |

The mirror is the highest-confidence marker because its reference and candidate centers are almost complementary around x=0.5. The bed and other side-specific furniture follow the same direction error. This is larger than a local bed or mirror nudge and must be tested as one global presentation-axis error before changing individual anchors.

## Bounded correction 2 — proof-only X-handedness diagnostic

Keep every source GLB and prescribed furniture anchor untouched and apply a temporary **proof-only X mirror at `room_world_root`** during the real Blender primary render, then restore the root transform immediately after rendering. This tests the measured whole-scene handedness error without yet rewriting the room builder/runtime coordinate contract.

Acceptance for this diagnostic:

- real Blender output only; no post-render image flip and no generated substitute geometry;
- root transform restored after render; source GLBs and furniture anchors unchanged;
- reference round mirror must move from the wrong far-right screen location to the intended far-left side;
- bed/side-specific furniture must move in the same direction toward the reference rather than being individually guessed;
- if the whole-scene comparison improves, record the result and only then choose the smallest auditable runtime/builder integration;
- if it worsens the scene, restore the prior proof path and reject the hypothesis.

## Iteration order

1. Run exactly one real Blender primary proof for the proof-only X-handedness diagnostic and inspect it directly against `/Refernzbild.png` on the same 1376 x 1100 grid.
2. If global handedness is confirmed, integrate the smallest auditable derived room presentation transform while preserving the 12 immutable source GLBs and explicit user-space anchor metadata.
3. Re-measure foreground table vertical band, bed envelope, dresser, chair, mirror and window after the handedness correction; then fix exactly the largest remaining geometric delta.
4. After gross geometry/perspective/möbelaufstellung becomes meaningfully readable, produce and actually inspect the required early in-app CALL preview.
5. Camera micro-alignment follows geometry/layout; materials/light/window polish follows camera.

No visual PASS may be recorded while a relevant visible delta remains.
