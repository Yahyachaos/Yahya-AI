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

Approved trial transform for the next proof:

- `room_foreground_table.location`: `(0.00, 0.36, 1.55)` -> `(0.00, 0.36, 2.05)`
- `room_foreground_table.scale`: `0.68` -> `1.10`
- rotation unchanged at `0 deg`
- source remains immutable `Tischfürlaptop.glb` from pinned commit `df50816187978cbf5faf818ad484c3f682be7588`

Reasoning: camera-to-anchor depth shrinks from about 2.05 m to 1.55 m while scale increases by 1.62x, giving an approximate projected-width multiplier near 2.1x, matching the measured requirement from ~0.47 W toward the near-full-width reference crop. This is a trial subject to the next real rendered measurement, not visual acceptance.

## Iteration order

1. Render exactly one real Blender primary proof for bounded correction 1 and compare on the same 1376 x 1100 grid.
2. If the table reaches the intended foreground band without invalid occlusion, freeze that improvement and correct the largest remaining bed/right-side layout delta next.
3. After gross geometry/perspective/möbelaufstellung becomes meaningfully readable, produce and actually inspect the required early in-app CALL preview.
4. Camera micro-alignment follows geometry/layout; materials/light/window polish follows camera.

No visual PASS may be recorded while a relevant visible delta remains.
