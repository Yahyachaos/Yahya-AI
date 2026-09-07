# Celine room visual rejection — Real Candidate #1379

Status: **VISUAL FAIL / REJECTED AS CONTINUATION BASELINE**

Authority: `/Refernzbild.png` and the user's direct visual verdict on the real in-app CALL proof.

Rejected runtime head: `b209b796dc032b680c50caa4cb2db11253cbc4f0`

Current docs-only recovery handoff head: `777ad1beb59142b27e889e8424261c4b3b3dc2f5`

Build: Android Build #1423 — SUCCESS

Real Candidate: #1379 / run `34167990007`; workflow was cancelled during the broader proof sequence, but the exact-head HOME/CALL/HOME-return images were uploaded and are usable for the bounded room visual verdict.

## User-visible verdict

The current room remains materially unacceptable as a realistic bedroom even after the bounded dresser/nightstand/chair/plant transform corrections. The user explicitly rejected the whole visual result as looking very bad.

The rejection is **not** permission to continue micro-adjusting individual furniture transforms. Current bounding-box/geometry scores are insufficient as a sole quality signal because a scene may be numerically near target while materials, shell appearance, depth cues, orientation readability and overall realism are still visibly wrong.

## Recovery rule

Do not stack another furniture micro-patch on this rejected raster.

Recover in this order:

1. Reconcile and freeze all user-confirmed orientation facts as constraints, not as an accepted whole-scene baseline.
2. Establish a clean architecture/appearance checkpoint: room shell + proof camera + window/drapes first.
3. Audit and remove/revert experimental room appearance/material layers that visibly damage the shell or source furniture presentation. Prefer a clean source-PBR/neutral runtime baseline over more spatial texture hacks.
4. Verify the architecture landmarks and window envelope against `ci/evidence/CELINE_ROOM_REFERENCE_LAYOUT_TARGETS.json` and manually inspect the real CALL image.
5. Only after the architecture/appearance checkpoint is visibly coherent, reintroduce/solve the primary furniture set as one measured composition batch: bed, dresser, lounge chair, foreground table, rug.
6. Secondary furniture/details only after the primary composition is coherent.
7. Material/light polish follows geometry/composition, but source-texture corruption or destructive overrides are root-cause defects and may be repaired during the clean-baseline recovery.

## Protected inputs

- `/Refernzbild.png` remains the visual authority.
- 4.40 m × 4.20 m × 2.65 m room contract remains fixed.
- 12 original textured furniture GLBs remain immutable source-of-origin assets.
- Canonical Celine identity/rig, anchors/navigation and Core/Persona workstream remain protected.
- No new branch/PR, no merge, no release.

## Stop-loss

A numerically improved candidate must still be rejected if the actual CALL raster remains obviously unrealistic, corrupted, clipped, materially broken or compositionally wrong. Workflow success never overrides manual visual failure.

`exact_next_action`: fresh-reconcile PR #111, inspect the active room material/appearance owners and the current shell/window path, then produce one bounded **clean architecture/appearance baseline** change (not another individual-furniture tweak). Run the smallest required Android build and exactly one targeted real CALL proof; manually compare the actual raster against `/Refernzbild.png` before any further furniture work.
