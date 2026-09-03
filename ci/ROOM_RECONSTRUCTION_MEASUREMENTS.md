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

## Bounded correction 2 — X-handedness

Proof #16 tested a proof-only X mirror at `room_world_root`. Direct visual inspection confirmed the hypothesis: mirror, bed and side-specific furniture all moved toward the reference sides together. Head `8f57b986d32ad1ee9821ff9cf655d99c1ef2e6f7` then retained that confirmed presentation handedness for the next reconstruction proof while keeping the 12 source GLBs and exact source-anchor metadata unchanged.

## Proof #17 — manually inspected 2026-09-04

- Exact Room 440x420 Blender Proof #17 / run `33804765830`.
- Exact head: `8f57b986d32ad1ee9821ff9cf655d99c1ef2e6f7`.
- Artifact: `9917398137`, digest `sha256:355eaee22c3b2a0ea0138b2b92d50bfc50400392ec13811edcb44ffa0dc20016`.
- Structural result: SUCCESS. Builder reported `CELINE_ROOM_440x420 PASS`; the primary frame and `/Refernzbild.png` are both 1376 x 1100.
- Manual visual verdict: WHOLE-SCENE FAIL. The X direction is improved, but gross furniture depth/scale remains wrong.

Measured/visible deltas after Proof #17:

| Feature | Reference | Proof #17 | Evidence-backed conclusion |
|---|---:|---:|---|
| back-wall / ceiling boundary, center | y ~= 0.08 | y ~= 0.20 | camera/vertical composition remains far off; defer until gross furniture occlusion is reduced |
| foreground tabletop upper/far band | y ~= 0.78 | y ~= 0.64 | still about 0.14 H too high |
| round wall mirror | far left, center near x ~= 0.04 | far left | handedness is confirmed; mirror size/height still need later correction |
| bed mass | right half, begins near x ~= 0.50 | intrudes into center from about x ~= 0.32 | bed placement/scale remains a major later correction |
| floor lamp projected envelope | small back-left accent, roughly x 0.25..0.29 and y 0.28..0.43 | huge near-camera occluder, roughly x 0.12..0.34 and y 0.37..0.88 | lamp is about 3–4x too dominant in projected height and hides the lounge-chair zone |
| lounge chair | clearly readable left-middle | largely hidden by the lamp | cannot judge chair until lamp occlusion is removed |
| right nightstand/furniture | compact at far-right edge | oversized and too far into frame | still wrong, but lamp is the largest isolated occluder first |

The floor lamp is now the best bounded next target because it is independently identifiable, it blocks another required object, and its error is explained by both depth and scale rather than by camera polish. With the fixed proof camera, its current anchor depth is user Z `+1.05`, only about 2.55 m from the camera at Z `+3.60`; the intended back-left reference placement reads near the chair/window zone. Moving only derived child geometry to effective user Z `-0.95` changes camera distance to about 4.55 m. Combining that perspective ratio (`2.55 / 4.55 ~= 0.56`) with a derived geometry scale factor `0.48` predicts about `0.27x` the current projected size, close to the measured target of roughly one quarter to one third of the current lamp height.

## Bounded correction 3 — proof-only floor-lamp depth/scale diagnostic

For the next real Blender proof only:

- keep `room_floor_lamp__anchor` exact and auditable at its current prescribed source contract;
- keep `Lampe.glb` bytes unchanged;
- shift only `room_floor_lamp__geometry` to effective user Z depth `-0.95`;
- apply derived uniform geometry factor `0.48` below the immutable anchor;
- re-ground the lamp after scaling so its mesh base remains at floor user Y `0.00`;
- do not change camera, bed, table, chair, nightstands, mirror, window or room shell in the same iteration.

Acceptance for this diagnostic:

- lamp must stop dominating the left foreground and expose the lounge-chair zone;
- its projected height should approach the reference back-left lamp silhouette instead of occupying roughly half the frame height;
- no source GLB or canonical source-anchor metadata may change;
- if the scene improves, integrate the smallest equivalent derived reconstruction/runtime transform and then re-measure the next largest furniture error;
- if the scene worsens, reject this hypothesis and restore Proof #17 lamp geometry behavior.

## Iteration order

1. Run exactly one real Blender primary proof for bounded correction 3 and inspect it directly against `/Refernzbild.png` on the same 1376 x 1100 grid.
2. If lamp depth/scale is confirmed, integrate that derived calibration and re-measure the bed, right nightstand, mirror, table vertical band and chair.
3. Correct exactly the next largest measured geometric/proportion error; do not begin camera micro-polish while large furniture mismatches remain.
4. As soon as the gross room geometry/perspective/möbelaufstellung becomes meaningfully readable, produce and actually inspect the required early in-app CALL preview.
5. Camera/FOV/target micro-alignment follows gross geometry/layout; materials/light/window/detail polish follows camera.

No visual PASS may be recorded while a relevant visible delta remains.
