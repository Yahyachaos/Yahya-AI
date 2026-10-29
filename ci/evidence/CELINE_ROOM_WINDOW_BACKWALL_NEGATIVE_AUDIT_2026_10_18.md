# ROOM clean-baseline negative aperture audit — 2026-10-18

Scope: **read-only analysis of the exact current committed derived Room shell**, not a runtime change or visual acceptance.

- Room PR #111, branch `auto/celine/v80-human-videochat-presence`, audited head `f69e9c6e82bd8a23bfec24c508ec32c9fd6e33b4`.
- Audited file: `app/src/main/assets/models/room/source-fidelity/room_shell.glb`, Git blob `8daccbe83023414ccb360414dd5c1714d67a6076`, size **12,472 bytes**.
- Parsed actual GLB 2.0 accessor/index triangles of `room_shell_back`: **12 triangles** forming a closed opaque wall, node translation `[0,1.325,2.15]`, material `CELINE_440_WallsCleanNeutral` with baseColor alpha=1, no alphaMode override (thus OPAQUE).
- On the committed wall (not the proposed uncommitted local aperture), **9/9** center/window sample points at X=[0.30,0.75,1.20]m and world Y=[1.10,1.65,2.20]m are blocked by back-wall triangles; **12/12** tested surrounding points are also blocked. This conclusively establishes that the committed Room has **no actual opening** through the back wall in the target sample region.
- The previously proposed 9/9-clear aperture result was a **local derivative only**, not the committed binary and not an Android validation. Do not treat that result as deployed.
- Exact optimized `room_window_drapes.glb` is Git blob `1740b0d3683488e58cde944bada009e30b6be19f`, 18,551,108 bytes; material alpha and semantic segmentation remain **UNVERIFIED** via the available binary read path.
- Independent immutable original `Fenstermitgardinen.glb` source audit established a single OPAQUE material with an alpha-less pale JPEG atlas, but that result alone does not prove the optimized material topology.
- Last built runtime head `ffe4db33b7c73c2cdaa48753e95d880071b1f2f7`, fingerprint `dea4c685866295bb5c6c4e68883820d709ea4d49c29facd4d84a7cf5fba3ff18`. Android #1567 PASS, real CALL #286 technically PASS but **visual FAIL**.
- No Celine/Core/Persona/shared path or 12 original furniture GLBs modified; **no visual PASS** and no merge/release.

**exact_next_action:** Reconcile live PR/head, acquire an authorized binary material-topology audit of the **exact optimized** window partition, then implement the coherent derived aperture+curtain/glazing/night-depth+lighting owner correction on the existing Room branch; build once and manually inspect one real HOME→CALL→HOME raster before any furniture pass.
