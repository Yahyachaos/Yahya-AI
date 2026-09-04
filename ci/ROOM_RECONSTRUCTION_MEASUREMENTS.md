# Celine Room Reconstruction Measurement Contract

Authority: `/Refernzbild.png` on the active canonical v80 branch. This is the current reproducible image-space reconstruction contract. Structural CI success is never visual acceptance. The 12 original furniture GLBs remain immutable source-of-origin files; only derived instance anchors/runtime TRS may change.

## Current canonical evidence

### Exact room proof baseline — Proof #92

- Workflow: `Celine Exact Room 440x420 Blender Proof` #92, run `33898105968`.
- Exact proof head: `fe8fc5bbb6c741410bfd0b4c26fd6766b818bd63`.
- Artifact: `9946757642`.
- Reference/proof grid: exactly `1376 x 1100`.
- Room shell: `4.40 x 4.20 x 2.65 m`.
- Source authority: 12 GLBs from pinned commit `df50816187978cbf5faf818ad484c3f682be7588`; 13 instances because the nightstand source is instanced twice.
- Camera solve `[cam_x, cam_z, height, target_x, target_height, lens_mm]`:
  `[0.380078125, 2.8734375, 1.2234375, 0.0565625, 0.45, 20.846875]`.
- Camera architecture objective: `0.25394842276012974`.
- Manual verdict: **WHOLE-SCENE FAIL / reconstruction still active**. Proof #92 is the accepted measured geometry checkpoint, not final visual acceptance.

### Early in-app HOME/CALL checkpoint — Real Candidate #1077

- Exact runtime head: `125111b435907ff963b5fa4b72df9423ebd7a096`.
- Runtime fingerprint: `a023f48987202505f78ab229faadda017d6d2ca74f31dfa760ebf1ea8bd941aa`.
- Android Build #1189: SUCCESS; APK sha256 `8c718e370b4ce0999334ba31ab14e6827e650c360fd9e2ebaf860496f22d617f`.
- Real Candidate run #1077: workflow conclusion FAILURE only because the later outer 300 s multiview stage timed out with exit 124.
- The required early lifecycle evidence itself completed before that timeout: initial HOME, CALL, HOME-return and same-process lifecycle checks all passed structurally.
- Direct image inspection of `real-candidate-home.png`, `real-candidate-call.png` and `real-candidate-home-return.png` confirms the earlier nested/stale extra room frame on HOME return is gone. HOME return shows one direct room/Celine scene again.
- This satisfies the mandatory early CALL checkpoint for coarse room assessment, but the visible room still differs materially from `/Refernzbild.png`; no room PASS is claimed.

## Proof #92 measured target/current/delta table

All values are normalized image coordinates on the same 1376 x 1100 grid. Delta is `current - target`.

| Instance | Confidence | Target center `(x,y)` / size `(w,h)` | Proof #92 center `(x,y)` / size `(w,h)` | Delta `(cx,cy,w,h)` | Decision |
|---|---|---|---|---|---|
| window / drapes | high | `(0.397,0.282) / (0.383,0.391)` | `(0.397034,0.282075) / (0.382996,0.391057)` | `(+0.000034,+0.000075,-0.000004,+0.000057)` | projection effectively exact; preserve |
| bed | medium | `(0.749,0.488) / (0.498,0.329)` | `(0.749064,0.488066) / (0.497836,0.329116)` | `(+0.000064,+0.000066,-0.000164,+0.000116)` | Proof #92 anisotropic bed solve accepted; preserve |
| dresser | high | `(0.092,0.569) / (0.184,0.298)` | `(0.091681,0.562652) / (0.183361,0.349811)` | `(-0.000319,-0.006348,-0.000639,+0.051811)` | **largest remaining high-confidence visible proportion error: ~17.4% too tall while width is already exact** |
| lounge chair | high | `(0.275,0.438) / (0.116,0.140)` | `(0.276245,0.458918) / (0.119737,0.137465)` | `(+0.001245,+0.020918,+0.003737,-0.002535)` | front-facing branch confirmed; still ~0.021 H too low |
| foreground table | high | `(0.500,0.891) / (1.000,0.218)` | `(0.500000,0.890993) / (1.000000,0.218014)` | `(0.000000,-0.000007,0.000000,+0.000014)` | projection exact on verified near-frontal yaw branch; preserve |
| rug | medium | `(0.538,0.658) / (0.665,0.275)` | `(0.538189,0.654296) / (0.657215,0.303103)` | `(+0.000189,-0.003704,-0.007785,+0.028103)` | vertically ~0.028 H too large; later than dresser |
| round mirror | high | `(0.039,0.216) / (0.078,0.242)` | `(0.038982,0.216078) / (0.077963,0.241759)` | `(-0.000018,+0.000078,-0.000037,-0.000241)` | effectively exact; preserve |
| front nightstand | medium | `(0.958,0.606) / (0.084,0.200)` | `(0.949395,0.582305) / (0.101210,0.202482)` | `(-0.008605,-0.023695,+0.017210,+0.002482)` | clearly visible, too far left/wide and ~0.024 H high; later correction |
| large plant | medium | `(0.190,0.370) / (0.115,0.330)` | `(0.196649,0.370397) / (0.119822,0.333129)` | `(+0.006649,+0.000397,+0.004822,+0.003129)` | close; preserve until larger errors gone |
| small plant | low | `(0.959,0.493) / (0.049,0.059)` | `(0.958952,0.492947) / (0.048986,0.058966)` | `(-0.000048,-0.000053,-0.000014,-0.000034)` | numerically exact but low-confidence/occluded; do not polish now |
| wall shelf/books | medium | `(0.662,0.215) / (0.103,0.080)` | `(0.662097,0.215112) / (0.096666,0.087065)` | `(+0.000097,+0.000112,-0.006334,+0.007065)` | close; later |
| rear nightstand | low | `(0.7465,0.422) / (0.085,0.200)` | `(0.732630,0.463889) / (0.094731,0.199622)` | `(-0.013870,+0.041889,+0.009731,-0.000378)` | largest raw bbox objective, but lower cabinet is bed-occluded and target bottom/height are explicitly coarse; do not let this low-confidence extrapolation outrank directly visible high-confidence dresser error |

Floor-lamp note: Proof #92 candidate center-X/top/width match the high-confidence visible terms essentially exactly (`dx ~= -0.000010`, `dtop ~= +0.000021`, `dwidth ~= +0.000002`). Its full unoccluded source bbox bottom extends below the measured visible lamp envelope because drapes/scene occlusion make the reference bottom unreliable. The canonical lamp objective intentionally uses center-X/top/width and must not be replaced by blind full-bbox equality.

## Current ranked visual correction order

1. **Dresser vertical proportion** — high-confidence and directly visible. Proof #92 width is already within `0.000639 W`, but height is `+0.051811 H` (~17.4%) too large. Uniform scale is therefore structurally incapable of matching both dimensions. Next proof must split horizontal footprint from vertical height on the normal grounded dresser anchor.
2. Lounge-chair vertical placement — high-confidence, front orientation already corrected, center-Y `+0.020918 H` too low.
3. Front nightstand — medium-confidence but clearly visible; center-Y `-0.023695 H`, width `+0.017210 W`.
4. Rug vertical envelope — medium-confidence, height `+0.028103 H`.
5. Rear nightstand — raw numeric error is larger, but its target is low-confidence and partially bed-occluded. Use real visible silhouette evidence before changing its floor-extrapolated bbox.
6. Only after remaining primary geometry errors are small: camera/FOV micro-alignment, then materials/textures/light/shadows/window detail/final polish.

## Bounded correction 4 — Proof #93 dresser anisotropic anchor solve

Evidence-backed hypothesis: `Kommode.glb` is already at essentially the correct projected horizontal span, but a uniform anchor scale leaves it about 17.4% too tall. The next exact-room proof may change **only the derived dresser anchor solve**:

- preserve all 12 original furniture GLB bytes;
- preserve room shell, camera solve, bed, window, foreground table, lamp, plants, chair, rug, mirror, shelf and both nightstands for this bounded iteration;
- solve dresser anchor X/depth/yaw plus independent horizontal and vertical scale;
- re-ground every anisotropic candidate before scoring so the objective measures the actual floor-contact state;
- include Proof #92 uniform dresser state as an explicit non-regression seed;
- measure full dresser bbox against the high-confidence target `left/right/top/bottom = 0.000/0.184/0.420/0.718`;
- no proof-time child-geometry offset, hidden mesh, source edit or negative whole-room mirror is allowed.

Acceptance for Proof #93:

- `01_front_wide.png` must be opened and compared directly with `/Refernzbild.png`;
- dresser must visibly retain its correct left-side width while reducing the excessive vertical mass;
- `reference_solve.json` must report a lower dresser objective than Proof #92 (`1.174884729302439`) without regressing already accepted primary geometry;
- if visually confirmed, propagate only the accepted derived dresser TRS into `CelineRoomReferenceLayoutV80.java`, then perform exactly one Android build for the new runtime fingerprint and the smallest relevant real in-app visual proof;
- if the dresser worsens despite a lower bbox objective, reject the numerical result and preserve Proof #92 runtime state.

## Iteration rule

For each bounded change: re-read live authority -> confirm single-flight -> measure -> change one evidenced primary error -> run only the smallest required proof -> inspect the actual image -> document target/current/delta -> continue to the next largest visible error. Do not declare completion while a relevant visible difference remains.
