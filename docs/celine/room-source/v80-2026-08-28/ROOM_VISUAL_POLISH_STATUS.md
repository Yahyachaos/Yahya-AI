# v80 Room Visual Polish — Candidate #12 ceiling PASS / Candidate #13 floor PASS

Live GitHub is authoritative. This file records the exact Candidate #12 ceiling acceptance and Candidate #13 floor acceptance from the latest targeted production-equivalent evidence. It does not reopen any accepted/protected runtime block.

## Active strand
- Repository: `Yahyachaos/Yahya-AI`
- PR: #111 — **DRAFT**, open, not merged
- Branch: `auto/celine/v80-human-videochat-presence`
- Canonical visual target image: `/Refernzbild.png`
- Canonical visual target contract: `docs/celine/room-source/v80-2026-08-28/ROOM_VISUAL_TARGET_REFERENCE.md`
- Candidate #12 runtime-equivalent checkpoint: `3422e5707c7a30735d35c53777371b090d09d0b5`
- Candidate #13 runtime-code head: `c147fa1c1514b0c66c107c18fb4a8607a427682c`
- Candidate #13 validated checkpoint: `1dbf3a14f95639aff854e0a4a38ea20e45337ead`

## Protected baseline
Unchanged and protected:
- accepted Candidate #12 ceiling base color `(1.0, 0.88, 0.62, 1.0)` LINEAR
- ceiling metallic `0.0`, roughness `0.88`, reflectance `0.40`
- indirect lighting `(1.0, 1.0, 1.0)` at `8000`
- directional-light color, direction and intensity (`14000`)
- camera exposure
- room GLB bytes, geometry and transforms
- all 12 immutable original furniture GLBs
- canonical Celine and Celine separation from room/furniture
- camera/zoom, accepted poses, anchors/navigation and all accepted 9R actions
- accepted 60,000 lm Lamp behavior

## Candidate #12 exact evidence and verdict
Candidate #12 changed only the isolated ceiling duplicate base color in LINEAR space from `(1.0, 0.95, 0.76, 1.0)` to `(1.0, 0.88, 0.62, 1.0)`.

Android Build **#843 / run `33368243270`: SUCCESS**.

Celine Room Visual Polish Proof **#17 / run `33378776586`: SUCCESS structurally** on exact proof head `3422e5707c7a30735d35c53777371b090d09d0b5`.

- evidence artifact: `9752976064`
- evidence digest: `sha256:dfc91df5c4c236c34c83bc59301d0f01c1bd4e63904f286f3283d02835eea3f3`
- structural result: PASS; HOME -> CALL -> HOME remained stable and Celine remained visible
- manual ceiling verdict: **PASS**

The ceiling reads as a coherent warm tan/beige field with clear separation from the cream wall. It is no longer cool-grey/khaki enough to justify another ceiling iteration.

## Candidate #13 exact runtime and evidence
Candidate #13 changed only the existing `room_floor` runtime material factor in LINEAR space from `(0.64, 0.44, 0.28, 1.0)` to `(0.48, 0.38, 0.30, 1.0)` at runtime-code head `c147fa1c1514b0c66c107c18fb4a8607a427682c`.

Preserved exactly:
- accepted Candidate #12 ceiling and all wall factors
- floor metallic `0.0`, roughness `0.62`, reflectance `0.45`
- indirect and directional lighting
- exposure/skybox
- room GLB bytes, geometry/transforms
- Celine, camera/zoom, anchors/navigation/actions and Lamp behavior

Android Build **#847 / run `33379564664`: SUCCESS** on runtime-equivalent checkpoint `1dbf3a14f95639aff854e0a4a38ea20e45337ead`.

- runtime fingerprint: `00c96cfca78b53c4d71211075fb5a49c107b02ae133c5bd5c2af7470632ffb6f`
- APK artifact: `9753249448`
- APK artifact digest: `sha256:5660b5d0e1e1c7afa134903bf5a1c43823628af399ea6a94e0fdab3e2b59666b`
- runtime-fingerprint artifact: `9753250023`
- fingerprint artifact digest: `sha256:798fb7d4e630c30c55d24f03942c892216377a0d5cdc45902ea8c24838d25700`

Celine Room Visual Polish Proof **#18 / run `33380542256`: SUCCESS structurally** on the same exact checkpoint.

- evidence artifact: `9753636780`
- evidence digest: `sha256:2311e0ad1e5ac5b0314b34699eb81d540d8b7373ebadfc58126c3798ae3b428f`
- structural result: PASS; HOME -> CALL -> HOME stable, Celine visible, no blank renderer
- manual floor verdict: **PASS**

Manual inspection of `home.png`, `call.png` and `home-return.png`, including direct comparison with Candidate #12, shows that the visible floor now reads as a darker, less saturated natural warm brown instead of the previous orange/red-brown emphasis. The accepted Candidate #12 ceiling remains unchanged. No further floor-base-color iteration is justified by this bounded criterion.

## Current room-polish state
Candidate #12 ceiling and Candidate #13 floor are accepted/protected for this room-polish strand. This does **not** claim that every remaining composition/lighting/material difference to the canonical room reference is solved, and it does not authorize reopening camera, transforms, accepted anchors/actions, Lamp behavior, room GLB bytes or furniture originals.

## Exact next action
Fresh-reconcile the live branch/PR after this docs-only acceptance commit and determine the next explicitly authorized canonical v80 action. Do not make another room runtime change merely because a broader visual difference exists; first bind the next bounded change to the current queue/work-order/visual-target contract. No merge, no release, no NavMesh/free navigation, no new branch and no new PR.
