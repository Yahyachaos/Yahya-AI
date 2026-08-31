# v80 Room Visual Polish — Candidate #10 verdict / Candidate #11 runtime checkpoint

Live GitHub is authoritative. This file records the exact Candidate #10 evidence/manual verdict and the bounded Candidate #11 runtime now awaiting normal validation. It does not reopen any accepted/protected runtime block.

## Active strand
- Repository: `Yahyachaos/Yahya-AI`
- PR: #111 — **DRAFT**, open, not merged
- Branch: `auto/celine/v80-human-videochat-presence`
- Canonical visual target image: `/Refernzbild.png`
- Canonical visual target contract: `docs/celine/room-source/v80-2026-08-28/ROOM_VISUAL_TARGET_REFERENCE.md`
- Candidate #10 runtime-equivalent checkpoint: `2da06e190f9056b2da66f5f7a66e9c7615a306a6`
- Candidate #11 bot cleanup/head: `22a8b52a30ebd829811f9c5bf383778032e01808`

## Protected baseline
Unchanged and protected:
- indirect lighting `(1.0, 1.0, 1.0)` at `8000`
- directional-light color, direction and intensity (`14000`)
- camera exposure
- room GLB bytes, geometry and transforms
- all 12 immutable original furniture GLBs
- canonical Celine and Celine separation from room/furniture
- camera/zoom, accepted poses, anchors/navigation and all accepted 9R actions
- accepted 60,000 lm Lamp behavior

## Candidate #10 exact evidence and verdict
Candidate #10 changed only the isolated ceiling duplicate base color in LINEAR space from `(1.0, 1.0, 0.92, 1.0)` to `(1.0, 0.97, 0.82, 1.0)`.

Android Build **#835 / run `33343347585`: SUCCESS**.

Celine Room Visual Polish Proof **#15 / run `33343499962`: SUCCESS structurally** on exact proof head `2da06e190f9056b2da66f5f7a66e9c7615a306a6`.

- evidence artifact: `9741299592`
- evidence digest: `sha256:e12dd8462603541d47cec9de4200dcc51566f35d7b5ed158063631fa89bdb6a5`
- structural result: PASS; HOME -> CALL -> HOME remained stable and Celine remained visible
- manual verdict: **FAIL**

Manual inspection of the actual `home.png`, `call.png` and `home-return.png` against `/Refernzbild.png` shows Candidate #10 is warmer than Candidate #9 and removes the cool-grey look, but the ceiling still reads as a pale khaki/green-beige flat band. The reference ceiling is a warmer tan/beige with less green cast and more convincing warm shading. The floor remains too orange/red-brown and was intentionally untouched.

## Diagnosis
The ceiling brightness is now close enough that the remaining confirmed mismatch is mainly chroma balance: too much green/yellow neutrality and not enough warm tan character. Global fill quantity, entity targeting, floor, camera and other protected surfaces are not the current ceiling cause.

## Candidate #11 bounded runtime now implemented
Candidate #11 changes only the isolated `room_ceiling` duplicate base color in LINEAR space from Candidate #10 `(1.0, 0.97, 0.82, 1.0)` to `(1.0, 0.95, 0.76, 1.0)`.

Preserved exactly:
- ceiling metallic `0.0`, roughness `0.88`, reflectance `0.40`
- all wall factors
- floor factor/material
- indirect and directional lighting
- exposure/skybox
- room GLB bytes, geometry/transforms
- Celine, camera/zoom, anchors/navigation/actions and Lamp behavior

This docs-only user-authored checkpoint preserves Candidate #11 runtime exactly and exists only to obtain normal PR validation after the bot-authored runtime head.

## Exact next action
Obtain exactly one Android build on this runtime-equivalent user-authored checkpoint. If successful, run exactly one targeted HOME -> CALL -> HOME Room Visual Polish proof, inspect the real images against `/Refernzbild.png`, and record PASS/FAIL. Do not stack a floor, camera, global-light or other runtime change before that evidence. No merge, no release, no NavMesh/free navigation.
