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

## Proof #183 — lifecycle accepted, dominant real-app framing delta isolated

Proof-only head `d5912cf0b1427f3c9f37acfa387f1c980176ac39` reused runtime source `f8338467b77dfbad6a2cc9ab525505c032b08c0f`, fingerprint `1e00a5a9cf761e3698bcced0748164694f997ba6495a6c77c3b7afaf13c8e31b`, from Android Build run `33914454849`. Room Visual Polish Proof #183 / run `33917925872` / artifact `9953944716` completed structurally and its real `home.png`, `call.png` and `home-return.png` were opened manually.

Lifecycle verdict: **PASS for the bounded compositor blocker only**. HOME return now contains exactly one room and one Celine; the nested stale CALL-sized SurfaceView visible in prior Proof #122 is gone. This does not constitute room visual acceptance.

Measured current HOME framing from the real proof:
- settled HOME SurfaceView: `964 x 761 px` (`REN-324` / pre-CALL `V80-511`).
- largest warm room/render component within that exact surface: approximately `x=187..798`, `y=272..622`, envelope `612 x 351 px`.
- occupied fraction: approximately `0.635 W x 0.461 H`; the authoritative reference is an immersive crop that fills essentially the frame, so this global underfill dominates the smaller remaining furniture deltas.
- the 12 original furniture GLBs and accepted derived furniture TRS are therefore not the first correction target.

Root-cause reconciliation from the same real log and current source:
- `Celine3DView` applies the accepted exact reconstruction projection every frame: lens `20.846875 mm`; converted runtime eye `(-0.380078125,-0.3265625,-1.1265625)`; target `(-0.0565625,-1.10,-4.30)`.
- immediately afterward the legacy `CelineReferenceHomeCameraV80` owner is still active (`ROOM-160`) and rewrites HOME to eye `(0,1.05,3.10)`, target `(0,-0.60,-4.00)`, a `7.10 m` dolly branch. This explains the real-app dollhouse composition even though the exact proof camera itself is already present in runtime.
- bounded correction: retire only that stale second HOME camera write and preserve `Celine3DView` as the single exact camera owner. Do not alter room geometry, accepted furniture TRS, Celine identity/rig or materials in the same step.

After that runtime correction: exactly one Android build, then one real HOME/CALL/HOME proof. Accept the step only if HOME is no longer globally underfilled, CALL remains direct/nonblank, and HOME return remains exactly one room/Celine. Then remeasure reference-vs-runtime object silhouettes before choosing the next largest delta.

## Current exact in-app checkpoint — Real Candidate #1136

Live runtime head `b3f4056303a3dce1bb19ed18702ed1a7dbd9f294` (`fix(room): close large plant width residual`) was built once in Android Build #1229 / run `33948858111`. Runtime fingerprint: `bbffc4ae9af3f5796d200d6b944390d1c1dc25c3c08bbc30b78f6a4002758f0b`. APK SHA256: `8b027832712e2e8fecc12eb483a0dcb073a03cb6ced0dd5b4a5fde4e267cfb37`.

Real Candidate #1136 / run `33948858110` / artifact `9964261276` emitted the required initial `real-candidate-home.png`, `real-candidate-call.png` and `real-candidate-home-return.png` before a later diagnostic zoom assertion failed. Those three initial lifecycle images are valid visual evidence and were opened directly. HOME SurfaceView is `964 x 761`; CALL SurfaceView is `1016 x 813`. They are nonblank and contain the current real room/Celine scene.

Canonical visual authority was independently revalidated from the exact source image hash: SHA256 `c5bbbfcffdcf60ac4d59149e21e5860de3b99b5b15864569d489a699dc7986e1`. The proof contract continues to compare on the normalized `1376 x 1100` grid.

Whole-scene visual verdict remains **FAIL**. The room now occupies broadly correct zones, and the foreground table begins near the intended lower image band, but Celine is the dominant composition error before smaller furniture polish:

- HOME: Celine's head/upper torso is clipped by the top of the real 3D Surface and her projected body is grossly larger than the fully visible standing reference person.
- CALL: Celine is also vertically clipped/high and visually floats over the bed rather than reading as a stable seated/contact composition. CALL is already materially smaller than HOME, which is a useful depth control observation.
- The current room furniture should not receive another micro-scale/yaw tweak before this global HOME presentation depth mismatch is closed and re-proved.

Exact current source reconciliation explains the HOME/CALL size split without changing Celine's canonical scale or the accepted camera:

- `Celine3DView.normalizeAsset()` places Celine's canonical root base at runtime Z `-4.0`.
- The one accepted camera owner remains lens `20.846875 mm`, eye Z `-1.1265625`.
- `CelineProductionPresenceV80.applyRoot()` currently adds legacy `camera_talk_anchor.localZ = +1.15 m` to ambient HOME, putting the standing root near Z `-2.85 m` before its small ambient bob/drift.
- CALL instead uses `CALL_ROOT_FORWARD = +0.12 m`, putting the root near Z `-3.88 m`.
- Camera-to-root depth therefore changes from roughly `1.72 m` in HOME to `2.75 m` in CALL. A pinhole projection predicts an approximately `1.6x` HOME scale increase, consistent with the observed top clipping and oversized HOME body.
- `camera_talk_anchor = 1.15 m` comes from the legacy `6.4 x 5.8 m` world contract, while the active visual room is now the exact `4.40 x 4.20 m` reconstruction. This is now new real-runtime evidence that the talk presentation depth is stale; it is not a reason to resize Celine or retune the camera.

### Next bounded correction

Rebase only the runtime `camera_talk_anchor` presentation Z from legacy `1.15 m` to the empirically validated CALL/reference depth `0.12 m`. Preserve every other anchor coordinate, route endpoint, room/furniture TRS, camera/lens, canonical Celine scale/rig, source GLBs, materials and light. The navigator's relative-Z algebra keeps non-talk destination world-Z endpoints unchanged when only the talk baseline changes; the talk/home start and return become continuous at the new presentation depth.

Then do exactly one Android build for the new runtime fingerprint and exactly one real HOME -> CALL -> HOME-return proof. Open all three real images. Accept only the bounded depth correction if HOME now contains Celine fully at reference-like standing scale without breaking CALL or HOME return. Do not touch CALL vertical/contact or secondary furniture in the same step; whichever remains the largest measured visible delta becomes the next single-flight action.

## Iteration rule

For every bounded step: re-read live authority -> confirm single-flight -> measure actual reference/proof -> change one evidenced primary error -> run only the smallest required proof -> open and inspect the actual image -> record target/current/delta -> continue. Solver/proof-only changes require no APK build; each runtime fingerprint change requires exactly one Android build. No visual PASS may be recorded while a relevant visible difference remains.