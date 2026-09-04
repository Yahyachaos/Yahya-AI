# Celine Room Reconstruction Measurement Contract

Authority: `/Refernzbild.png` on the active canonical v80 branch. Structural CI success is never visual acceptance. The 12 original furniture GLBs remain immutable source-of-origin files; only derived instance anchors/runtime TRS may change.

## Canonical room / proof basis

- Room shell: `4.40 x 4.20 x 2.65 m` under `room_world_root`.
- Source authority: exactly 12 original GLBs from pinned commit `df50816187978cbf5faf818ad484c3f682be7588`; 13 instances because one nightstand source is instanced twice.
- Exact proof grid: `1376 x 1100`; reference image copied into every proof artifact.
- Accepted camera solve `[cam_x, cam_z, height, target_x, target_height, lens_mm]`:
  `[0.380078125, 2.8734375, 1.2234375, 0.0565625, 0.45, 20.846875]`.
- Camera architecture objective: `0.25394842276012974`.
- No whole-room visual PASS exists yet.

## Mandatory early in-app HOME/CALL checkpoint

Runtime lifecycle head `125111b435907ff963b5fa4b72df9423ebd7a096` built in Android Build #1189 with runtime fingerprint `a023f48987202505f78ab229faadda017d6d2ca74f31dfa760ebf1ea8bd941aa` and APK sha256 `8c718e370b4ce0999334ba31ab14e6827e650c360fd9e2ebaf860496f22d617f`.

Real Candidate #1077 later timed out only in the outer 300 s multiview stage after the required initial HOME, CALL and HOME-return evidence had completed. Direct inspection of those actual screenshots confirmed the previous nested/stale extra HOME room frame is gone: initial HOME, CALL and HOME return each show one direct room/Celine scene in the same process. This satisfies the early CALL checkpoint but is not room visual acceptance.

## Stable accepted geometry from Proof #92

Exact Room Proof #92 / run `33898105968` / artifact `9946757642` established the accepted window, bed, foreground table, mirror, floor-lamp visible terms and front-facing chair orientation branch. Important image-space checkpoints:

| Instance | Reference target | Proof #92 | Decision |
|---|---:|---:|---|
| window | center `(0.397,0.282)`, size `(0.383,0.391)` | `(0.397034,0.282075)`, `(0.382996,0.391057)` | preserve |
| bed | center `(0.749,0.488)`, size `(0.498,0.329)` | `(0.749064,0.488066)`, `(0.497836,0.329116)` | preserve |
| foreground table | center `(0.500,0.891)`, size `(1.000,0.218)` | `(0.500000,0.890993)`, `(1.000000,0.218014)` | preserve verified near-frontal yaw branch |
| mirror | center `(0.039,0.216)`, size `(0.078,0.242)` | `(0.038982,0.216078)`, `(0.077963,0.241759)` | preserve |
| large plant | center `(0.190,0.370)`, size `(0.115,0.330)` | `(0.196649,0.370397)`, `(0.119822,0.333129)` | close; later only if still visible |
| front nightstand | center `(0.958,0.606)`, size `(0.084,0.200)` | `(0.949395,0.582305)`, `(0.101210,0.202482)` | later visible correction |
| rug | center `(0.538,0.658)`, size `(0.665,0.275)` | `(0.538189,0.654296)`, `(0.657215,0.303103)` | later visible correction |
| rear nightstand | center `(0.7465,0.422)`, size `(0.085,0.200)` | `(0.732630,0.463889)`, `(0.094731,0.199622)` | low confidence because bed occludes lower cabinet; do not rank from raw AABB alone |

Floor-lamp acceptance remains based on reliable visible center-X/top/width terms, not its full unoccluded object AABB, because drape/scene occlusion makes the reference bottom unreliable.

## Dresser reconstruction — Proofs #101 through #104

`Kommode.glb` exposed an important measurement failure: projected object bounding-box corners include empty corners after yaw and are not equivalent to the actually occupied visible cabinet silhouette.

- **Proof #101**: projected AABB objective became almost zero, but direct `01_front_wide.png` / `02_instance_id.png` inspection showed the dresser visibly too short and low. This numerical result was rejected.
- **Proof #102**: authority switched to the real rendered instance-ID silhouette. Visible cabinet envelope improved to approximately `x=0.000..0.1731`, `y=0.4422..0.7316` against reference cabinet target `x=0.000..0.184`, `y=0.420..0.718`.
- **Proof #103**: deeper branch overshot upward/tall; visible envelope approximately `x=0.000..0.1811`, `y=0.4022..0.7088`.
- **Proof #104** / run `33906011837` / artifact `9949637717` interpolated the two real rendered branches and was manually inspected against `/Refernzbild.png`. The left-edge clipping, cabinet body scale and floor/leg envelope are sufficiently aligned to stop dresser-only iteration. No whole-room PASS is implied.

Accepted Proof #104 derived dresser anchor, source GLB unchanged:

- user X `2.1353125`
- user Z/depth `-0.077`
- horizontal scale `0.8235393808`
- vertical scale `0.8602542716`
- user yaw `87.7148463 deg`
- exact re-grounding preserved

Runtime mapping accepted in `CelineRoomReferenceLayoutV80.java`:

- Filament X `-2.135313`
- Filament Y `0.560357` (same immutable source pivot-to-floor offset under the new vertical scale)
- Filament Z `-0.077000`
- scale `(0.823539, 0.860254, 0.823539)`
- yaw `87.714844 deg`

Android Build #1194 / run `33906857976` built this runtime transform successfully on head `edca8088c9cc7dd60a5bcdb5e6c0f92b5a9e1dfd`. Runtime fingerprint: `a153a19fbf954610f5af53c4960dfc522b9624baa1f4b8224247f0121b552f00`. Exactly one APK build was required for this runtime fingerprint.

## Next largest reliable visible geometry error

The lounge chair is now the next high-confidence target. Its front/seat-facing branch is already correct, but the real Proof #104 instance-ID silhouette is approximately:

- current visible x `0.2224..0.3307`, y `0.4182..0.5245`
- reference target x `0.217..0.333`, y `0.368..0.508`

Horizontal placement/width is already close. The visible chair body is materially too low and too short vertically. Do not begin this correction until the exact Proof #104 dresser runtime head has completed one real in-app HOME/CALL checkpoint.

After chair, re-rank using real visible evidence rather than stale projected AABBs. Current likely candidates are front nightstand and rug; the rear nightstand remains lower-confidence while bed-occluded.

## Iteration rule

For each bounded step: re-read live authority -> confirm single-flight -> measure actual reference/proof -> change one evidenced primary error -> run only the smallest required proof -> inspect the actual image -> record target/current/delta -> continue. Solver/proof-only changes do not require an APK build; runtime fingerprint changes require exactly one Android build. No visual PASS may be recorded while a relevant visible difference remains.
