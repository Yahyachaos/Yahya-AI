# Room camera / architecture projection root-cause witness — 2026-10-20

**DIAGNOSTIC ONLY — NO ANDROID VISUAL ACCEPTANCE.** This note does not promote C9/C10, replace furniture/source assets, or validate a new runtime.

## Live baseline at calculation
- Room PR #111 OPEN/DRAFT; branch head before this note: `4ef0948d1005d3d694f7c9bc198204dd90cac671`.
- Latest built runtime-code head: `ffe4db33b7c73c2cdaa48753e95d880071b1f2f7`, runtime fingerprint `dea4c685866295bb5c6c4e68883820d709ea4d49c29facd4d84a7cf5fba3ff18`; Build #1567 PASS with APK; Room HOME/CALL/HOME #286 remains **MANUAL VISUAL FAIL**.
- Camera source: `Celine3DView.updateCameraPresence()`, actual constant `REFERENCE_FOCAL_LENGTH_MM=17.36812218122181`, eye `(-.380078125,-.3265625,-1.1265625)`, target `(-.0565625,-1.10,-4.30)`. CALL entry zoom 0.70 / reference-base 0.70 means normalized camera zoom 1.0.
- Independent camera contract witness: `CelineCameraZoomV70.java` still records the previous lens as **20.846875 mm**, while the actual renderer hard-codes **17.36812218122181 mm**. The diagnostic log is not itself runtime authority.
- Unaccepted C9 derived GLB supplies the **unaltered original 499,998-triangle window primitive** plus independently authored *unaccepted* frame/glass extras. Only the original primitive was used for this projection; the 12 canonical furniture GLBs were not touched.

## Same-camera 1D focal-length test

Project the exact source-window vertex bounds and nominal physical back-wall/ceiling corners into the 1016 × 813 CALL stage with the renderer's eye/target, 180° room-root frame, room offset, and 36 mm horizontal film gauge. The *only* variable is focal length. Normalized image origin top-left.

| Reference landmark | Canonical target | Current 17.368122 mm prediction | Existing 20.846875 mm contract prediction |
| --- | --- | --- | --- |
| Back-wall/ceiling LEFT | (0.190, 0.078) | (0.2398, 0.1477) | **(0.1877, 0.0771)** |
| Back-wall/ceiling RIGHT | (0.748, 0.098) | (0.7105, 0.1666) | **(0.7527, 0.0998)** |
| Window/drapes LEFT | 0.205 | 0.2562 | **0.2074** |
| Window/drapes RIGHT | 0.588 | 0.5774 | **0.5929** |
| Window/drapes TOP | 0.086 | 0.1588 | **0.0905** |
| Window/drapes BOTTOM | 0.477 | 0.4795 | **0.4754** |

All eight measured component errors, RMS: **0.05183 image fraction** at 17.368122 mm vs **0.00323** at 20.846875 mm. The unconstrained 1D least-squares fit is **20.86047 mm**, within 0.07% of the pre-existing 20.846875 mm contract. Exact screen coordinates remain subject to raster occlusion and Filament implementation; this is a geometric prediction, not captured Android evidence.

**Interpretation:** Both the ceiling seam and window silhouette are collectively **too small and low** under the current renderer lens. The geometry-scale error has a single shared camera owner. Physically stretching/moving the window alone would compensate the wrong focal length, exceed ceiling clearance, break wall/pane registration, and damage the reference-constrained architecture. Correct the camera lens *together with* physical aperture, independently owned believable drapes/glass/frame, ceiling/wall shell and Room light/material owners as a coherent clean-baseline batch, then inspect the actual CALL raster.

## Not concluded
- The original opaque back wall remains structurally incompatible with clear panes (separate 16/16 obstruction finding); a focal-length change does not make a hole.
- The original fragmented curtain primitive remains visually streaked; a lens change does not fix semantic material/geometry ownership.
- C9 and C10 remain UNACCEPTED and have **no new Android proof**.
- This is not a license for camera-only acceptance, one-object micro-patching, Core writes, merge/release, or final APK.

`exact_next_action`: After fresh live PR/main/workflow ownership reconciliation, prepare one coherent Room-owned camera-lens (restoring measured ~20.846875 mm), physical rear-wall aperture, independent frame/glass/drape semantics, ceiling/material/light baseline and fail-closed cross-partition gates on existing PR #111. Build Android exactly once for that runtime batch, capture one real HOME→CALL→HOME proof, and inspect its actual CALL raster against `Refernzbild.png`. Continue remaining queued Room/Interaction phases only when whole-scene appearance materially improves.
