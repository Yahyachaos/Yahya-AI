# v80 Room Visual Polish — Candidate #12 ceiling PASS / Candidate #13 runtime checkpoint

Live GitHub is authoritative. This file records the exact Candidate #12 evidence/manual verdict and the bounded Candidate #13 floor runtime now awaiting normal validation. It does not reopen any accepted/protected runtime block.

## Active strand
- Repository: `Yahyachaos/Yahya-AI`
- PR: #111 — **DRAFT**, open, not merged
- Branch: `auto/celine/v80-human-videochat-presence`
- Canonical visual target image: `/Refernzbild.png`
- Canonical visual target contract: `docs/celine/room-source/v80-2026-08-28/ROOM_VISUAL_TARGET_REFERENCE.md`
- Candidate #12 runtime-equivalent checkpoint: `3422e5707c7a30735d35c53777371b090d09d0b5`
- Candidate #13 bot cleanup/head: `ba9870fb8a82961652a474505fd55dd163973d54`

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

Manual inspection of the actual `home.png`, `call.png` and `home-return.png` against `/Refernzbild.png` shows the ceiling now reads as a coherent warm tan/beige field with clear separation from the cream wall. It is no longer cool-grey/khaki enough to justify another ceiling iteration. The remaining dominant shell mismatch is the floor, which is too saturated orange/red-brown compared with the reference's darker natural medium warm-brown wood.

## Candidate #13 bounded runtime now implemented
Candidate #13 changes only the existing `room_floor` runtime material factor in LINEAR space from `(0.64, 0.44, 0.28, 1.0)` to `(0.48, 0.38, 0.30, 1.0)`.

Preserved exactly:
- accepted Candidate #12 ceiling and all wall factors
- floor metallic `0.0`, roughness `0.62`, reflectance `0.45`
- indirect and directional lighting
- exposure/skybox
- room GLB bytes, geometry/transforms
- Celine, camera/zoom, anchors/navigation/actions and Lamp behavior

This docs-only user-authored checkpoint preserves Candidate #13 runtime exactly and exists only to obtain the normal PR validation run after the bot-authored runtime head.

## Exact next action
Obtain exactly one Android build on this runtime-equivalent user-authored checkpoint. If successful, run exactly one targeted HOME -> CALL -> HOME Room Visual Polish proof, inspect the real images against `/Refernzbild.png`, and record PASS/FAIL. Do not stack a camera, ceiling, global-light or other runtime change before that evidence. No merge, no release, no NavMesh/free navigation.
