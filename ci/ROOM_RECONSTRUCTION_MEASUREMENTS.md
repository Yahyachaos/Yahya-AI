# Celine Room Reconstruction Measurement Contract

Authority: `/Refernzbild.png` on the active canonical v80 branch. Structural CI success is never visual acceptance. The 12 original furniture GLBs remain immutable source-of-origin files; only derived instance anchors/runtime TRS may change.

## Canonical room / proof basis

- Room shell: `4.40 x 4.20 x 2.65 m` under `room_world_root`.
- Source authority: exactly 12 original GLBs from pinned commit `df50816187978cbf5faf818ad484c3f682be7588`; 13 instances because one nightstand source is instanced twice.
- Exact proof grid: `1376 x 1100`; `/Refernzbild.png` is copied into each exact proof artifact.
- Accepted camera solve `[cam_x, cam_z, height, target_x, target_height, lens_mm]`:
  `[0.380078125, 2.8734375, 1.2234375, 0.0565625, 0.45, 20.846875]`.
- Camera architecture objective: `0.25394842276012974`.
- No whole-room visual PASS exists yet.

## Early HOME/CALL lifecycle checkpoint

Runtime head `125111b435907ff963b5fa4b72df9423ebd7a096` built in Android Build #1189 with runtime fingerprint `a023f48987202505f78ab229faadda017d6d2ca74f31dfa760ebf1ea8bd941aa`. Real Candidate #1077 later timed out only in an outer multiview stage after required initial HOME/CALL/HOME-return evidence had completed. Direct screenshot inspection confirmed the stale nested HOME-return room was removed.

The first post-rebuild geometry lifecycle checkpoint was then completed on the Proof #104 dresser runtime: Real Candidate #1086 / run `33907201165` / artifact `9950159008` succeeded. Its real HOME, CALL and HOME-return images were opened and visually inspected; all were nonblank/direct and HOME return contained exactly one room/Celine scene.

## Stable accepted geometry inherited from Proof #92

Exact Room Proof #92 / run `33898105968` / artifact `9946757642` established the accepted room shell/camera branch plus window, bed, foreground table, mirror, floor-lamp visible terms and the chair's front-facing orientation branch.

| Instance | Reference target | Proof #92 checkpoint | Status |
|---|---:|---:|---|
| window | center `(0.397,0.282)`, size `(0.383,0.391)` | `(0.397034,0.282075)`, `(0.382996,0.391057)` | preserve |
| bed | center `(0.749,0.488)`, size `(0.498,0.329)` | `(0.749064,0.488066)`, `(0.497836,0.329116)` | preserve |
| foreground table | center `(0.500,0.891)`, size `(1.000,0.218)` | `(0.500000,0.890993)`, `(1.000000,0.218014)` | preserve verified near-frontal branch |
| mirror | center `(0.039,0.216)`, size `(0.078,0.242)` | `(0.038982,0.216078)`, `(0.077963,0.241759)` | preserve |
| large plant | center `(0.190,0.370)`, size `(0.115,0.330)` | `(0.196649,0.370397)`, `(0.119822,0.333129)` | close; later only if visible residual remains |
| front nightstand | center `(0.958,0.606)`, size `(0.084,0.200)` | `(0.949395,0.582305)`, `(0.101210,0.202482)` | later visible correction |
| rug | center `(0.538,0.658)`, size `(0.665,0.275)` | `(0.538189,0.654296)`, `(0.657215,0.303103)` | later visible correction |
| rear nightstand | center `(0.7465,0.422)`, size `(0.085,0.200)` | `(0.732630,0.463889)`, `(0.094731,0.199622)` | low confidence while bed-occluded; do not rank from raw AABB alone |

Floor-lamp acceptance remains based on reliable visible center-X/top/width terms rather than its full unoccluded object AABB.

## Dresser — rendered-silhouette acceptance

`Kommode.glb` demonstrated that projected object bounding-box corners can include empty yawed corners and are not equivalent to the actual visible silhouette.

- Proof #101 produced an almost-zero projected-AABB objective but a visibly worse short/low dresser; it was rejected.
- Proof #102 switched authority to the real instance-ID render; visible cabinet envelope approximately `x=0.000..0.1731`, `y=0.4422..0.7316`.
- Proof #103 bracketed the opposite side at approximately `x=0.000..0.1811`, `y=0.4022..0.7088`.
- Proof #104 / run `33906011837` / artifact `9949637717` interpolated those real rendered branches and was visually accepted for dresser propagation.

Accepted Proof #104 derived dresser anchor:
- user X `2.1353125`
- user Z/depth `-0.077`
- horizontal scale `0.8235393808`
- vertical scale `0.8602542716`
- yaw `87.7148463 deg`
- exact re-grounding, source GLB unchanged

Runtime dresser mapping in `CelineRoomReferenceLayoutV80.java`: `(-2.135313, 0.560357, -0.077000)`, scale `(0.823539, 0.860254, 0.823539)`, yaw `87.714844 deg`. Android Build #1194 produced runtime fingerprint `a153a19fbf954610f5af53c4960dfc522b9624baa1f4b8224247f0121b552f00`; Real Candidate #1086 passed its real in-app checkpoint.

## Lounge chair — Proof #111 accepted

Proof #104 real instance-ID chair silhouette before correction:
- current x `0.2224..0.3307`, y `0.4182..0.5245`
- reference target x `0.217..0.333`, y `0.368..0.508`

The front-facing yaw branch was already correct. Proof #111 changed only derived anchor scale while preserving X/depth/yaw and exact grounding:
- user X `1.69921875`
- user Z/depth `-2.05`
- horizontal scale `0.4556153020`
- vertical scale `0.6259727564`
- yaw `170.375 deg`

Exact Room Proof #111 / run `33908112566` / artifact `9950443389` was opened and manually inspected. Real rendered chair silhouette became approximately x `0.2137..0.3328`, y `0.3727..0.5264`; the important visible top/right/front-facing body is now closely aligned to the reference while lower-leg extension preserves real floor contact. This exact geometry is accepted for runtime.

Runtime chair mapping in `CelineRoomReferenceLayoutV80.java`:
- Filament XYZ `(-1.699219, 0.565723, -2.050000)`
- scale `(0.455615, 0.625973, 0.455615)`
- yaw `170.375000 deg`
- Y preserves the immutable source pivot-to-floor ratio under the accepted vertical scale

Android Build #1197 / run `33908787276` built this runtime head `30c759f71f1d742349615959bdcb7e96792ff453` successfully. Runtime fingerprint: `e09d1c4ae308e854b4d965c85d207b4cbefd10b8a8e6fc1a95eab340e31166fa`. Exactly one APK build was required for this runtime fingerprint.

## Current next-error ranking from real Proof #111 instance-ID evidence

1. **Rug** — large visible area. Real silhouette approximately x `0.2151..0.8612`, y `0.5036..0.8036`; target x `0.2055..0.8705`, y `0.5205..0.7955`. It is visibly too tall (`~0.300 H` vs target `0.275 H`) and slightly narrow. Correct only after the Proof #111 chair runtime completes one real HOME/CALL checkpoint.
2. **Front nightstand** — real silhouette approximately x `0.9012..0.9869`, y `0.4927..0.6800`; target x `0.916..1.000`, y `0.506..0.706`. Width is close, but the whole visible cabinet is shifted left/up and slightly short.
3. Rear nightstand remains lower-confidence because its lower body is bed-occluded. Measure visible evidence before changing it.
4. Only after primary geometry/layout residuals are small: camera/FOV micro-alignment, then materials/textures/light/shadows/window details/final polish.

## Iteration rule

For every bounded step: re-read live authority -> confirm single-flight -> measure actual reference/proof -> change one evidenced primary error -> run only the smallest required proof -> open and inspect the actual image -> record target/current/delta -> continue. Solver/proof-only changes require no APK build; each runtime fingerprint change requires exactly one Android build. No visual PASS may be recorded while a relevant visible difference remains.
