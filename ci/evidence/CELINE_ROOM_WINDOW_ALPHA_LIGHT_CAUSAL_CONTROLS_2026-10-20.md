# Room window causal controls — 2026-10-20

**STATUS: DIAGNOSTIC ONLY / NOT A RUNTIME OR ANDROID VISUAL PASS.**

## Live ownership and retained evidence

Existing Room PR #111 `auto/celine/v80-human-videochat-presence` is OPEN/DRAFT, pre-write head `e681bf59f3c319d21e7fa175713c363fca28c8fd`. Core PR #113 is protected and was not touched.

Last built Room runtime SHA: `ffe4db33b7c73c2cdaa48753e95d880071b1f2f7`; fingerprint `dea4c685866295bb5c6c4e68883820d709ea4d49c29facd4d84a7cf5fba3ff18`. Android Build #1567 / run `38900853726` PASS with live `yahya-ai-debug` artifact `12045643858`. Actual HOME/CALL/HOME proof #286 / run `38912197608` artifact `12051186261` was downloaded and the real `call.png` compared with the bundled exact `reference.png`: **WHOLE-SCENE VISUAL FAIL**. These are old runtime-equivalent results, not a C8 Android proof.

All 12 immutable original furniture-source GLB identities were verified again using pinned source commit `df50816187978cbf5faf818ad484c3f682be7588`: exactly 12/12 Git LFS pointer SHA-256 OIDs and byte sizes match `ROOM_SOURCE_GITHUB_LFS_BRIDGE.json`. No original source bytes were modified.

## Isolated controls: why repeated cloth-alpha experiments did not fix stripes

Test subject: **LOCAL, UNACCEPTED C8** derived window GLB SHA-256 `ef26d6572073ff1c3cb9fcd047f15c788ba7b93d50a52a59e35946ecd3924e46`, retaining 499,998 original-derived window triangles plus 60 separately authored frame and 4 glass triangles. VTK 9.6.2 offscreen renderer with depth peeling, fixed wall/camera/frame/glass/night backdrop. Only the original-derived cloth actor's texture, opacity, lighting changed between six controls. This is a falsification experiment, **not** Filament/Android validation.

The metrics are row-averaged absolute adjacent-X brightness differences of the cloth-window crops (730x680 offline image; left ROI x=180..289 y=235..454, right ROI x=415..524 y=235..454). Larger values indicate more vertical striping, not higher overall image quality:

| Control | Left | Right | Stripes >6 intensity steps, left/right |
| --- | ---: | ---: | --- |
| C8 RGBA atlas + translucency, normal light | 3.062 | 3.192 | 19 / 20 |
| Flat-colored alpha 0.202, normal light | 3.899 | 4.142 | 22 / 21 |
| Flat-colored alpha 0.202, **unlit** | 4.058 | 4.260 | 25 / 25 |
| Flat-colored **opaque**, normal light | 3.065 | 3.221 | 22 / 20 |
| Flat-colored **opaque**, **unlit** | 0.126 | 0.073 | 0 / 0 |
| **Cloth actor absent** | 0.266 | 0.000 | 1 / 0 |

**Supported causal narrowing:** the striped appearance is NOT solely encoded in the original albedo texture or the newly hypothesized PNG alpha atlas. It remains when all texture data and cloth lighting are removed but geometric multilayer alpha composition remains. It also persists for lit opaque cloth, yet largely disappears for unlit opaque cloth. This isolates **two interacting offline rendering mechanisms**: alpha/depth compositing across the source geometry and lighting/normal response of that geometry. It does not prove precisely which Filament path dominates the real Android artifact.

**Strategy switch:** do not repeat C3-C8 speculative opacity or `doubleSided` micro-patches. Before another visual promotion, inspect the actual Filament glTF alpha/depth/material owners and the reliable physical front/back curtain segmentation. Treat frame and glass as independently owned architecture with separate structural gates; retain canonical source geometry provenance; include wall aperture, ceiling architecture, real CALL proof camera, window depth, and room lighting in **one bounded clean-baseline batch**.

## Change classification and acceptance

This commit is **diagnostics/docs only**. No runtime asset, Java code, furniture transforms, source GLBs, shared build/release surface, version metadata, Core PR #113, or `main` changes. No Android build should be triggered to claim a new APK merely for this report. C8 is NOT ACCEPTED; current whole-scene verdict remains **VISUAL FAIL**; no merge/release/final APK.

**Blocker:** an authorized path to integrate a correctly bounded large derived GLB binary and its room-owned proof of material/depth ownership is not yet established. The diagnostic does not authorize skipping semantic/fail-closed gates.

`exact_next_action`: Fresh-reconcile main/AGENTS, both queues, work-order, validation and manifests, PR #111/Core #113 and exact proof artifacts; use the material/alpha/light controls above to identify the actual Filament cloth transparency and material owner; prepare one coherent aperture + physically partitioned curtain/frame/glass + ceiling + CALL camera + lighting batch, validate source vs authored faces and alpha/depth behavior, integrate only through authorized existing Room PR #111, perform one Android build and one true HOME→CALL→HOME proof and manually compare actual images with `Refernzbild.png`. Retain only on visible whole-scene improvement and continue remaining approved room interactions afterward.
