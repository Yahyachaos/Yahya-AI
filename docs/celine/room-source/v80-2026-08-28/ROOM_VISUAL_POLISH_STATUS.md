# v80 Room Visual Polish — Candidate #11 verdict / Candidate #12 runtime checkpoint

Live GitHub is authoritative. This file records the exact Candidate #11 evidence/manual verdict and the bounded Candidate #12 runtime now awaiting normal validation. It does not reopen any accepted/protected runtime block.

## Active strand
- Repository: `Yahyachaos/Yahya-AI`
- PR: #111 — **DRAFT**, open, not merged
- Branch: `auto/celine/v80-human-videochat-presence`
- Canonical visual target image: `/Refernzbild.png`
- Canonical visual target contract: `docs/celine/room-source/v80-2026-08-28/ROOM_VISUAL_TARGET_REFERENCE.md`
- Candidate #11 runtime-equivalent checkpoint: `70044ab5eefbde550d8cf905bd2c5eb9c48e0a2e`
- Candidate #12 bot cleanup/head: `feb08501faa81717b65d56c40879639daa88e929`

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

## Candidate #11 exact evidence and verdict
Candidate #11 changed only the isolated ceiling duplicate base color in LINEAR space from `(1.0, 0.97, 0.82, 1.0)` to `(1.0, 0.95, 0.76, 1.0)`.

Android Build **#839 / run `33343848182`: SUCCESS**.

Celine Room Visual Polish Proof **#16 / run `33344105526`: SUCCESS structurally** on exact proof head `70044ab5eefbde550d8cf905bd2c5eb9c48e0a2e`.

- evidence artifact: `9741467560`
- evidence digest: `sha256:cee90e2967114f2de9282185760038a62e9f386d18139696dc210b98bf8ada19`
- runtime fingerprint: `b3e808b14deeaec798eb6a0da0d6dd42c97979e99a6955cfc05dab4af40f7627`
- structural result: PASS; HOME -> CALL -> HOME remained stable and Celine remained visible
- manual verdict: **FAIL**

Manual inspection of the actual `home.png`, `call.png` and `home-return.png` against `/Refernzbild.png` shows Candidate #11 is warmer than #10, but the ceiling still reads as a broad pale beige/khaki slab. The reference ceiling is materially darker and warmer tan/brown under evening light, with stronger separation from the cream wall. The floor remains too orange/red-brown and was intentionally untouched.

## Diagnosis
The isolated ceiling override remains proven and stable. Candidate #11 confirms that the remaining ceiling mismatch is still the isolated base-color balance: it needs less green/blue contribution and more tan/brown character. Do not use global fill, camera, floor or another protected surface to compensate for this ceiling mismatch.

## Candidate #12 bounded runtime now implemented
Candidate #12 changes only the isolated `room_ceiling` duplicate base color in LINEAR space from Candidate #11 `(1.0, 0.95, 0.76, 1.0)` to darker/warmer tan `(1.0, 0.88, 0.62, 1.0)`.

Preserved exactly:
- ceiling metallic `0.0`, roughness `0.88`, reflectance `0.40`
- all wall factors
- floor factor/material
- indirect and directional lighting
- exposure/skybox
- room GLB bytes, geometry/transforms
- Celine, camera/zoom, anchors/navigation/actions and Lamp behavior

This docs-only user-authored checkpoint preserves Candidate #12 runtime exactly and exists only to obtain the normal PR validation run after the bot-authored runtime head.

## Exact next action
Obtain exactly one Android build on this runtime-equivalent user-authored checkpoint. If successful, run exactly one targeted HOME -> CALL -> HOME Room Visual Polish proof, inspect the real images against `/Refernzbild.png`, and record PASS/FAIL. Do not stack a floor, camera, global-light or other runtime change before that evidence. No merge, no release, no NavMesh/free navigation.
