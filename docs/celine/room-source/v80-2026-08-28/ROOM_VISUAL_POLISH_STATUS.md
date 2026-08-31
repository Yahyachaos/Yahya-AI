# v80 Room Visual Polish — Candidate #9 verdict / Candidate #10 runtime checkpoint

Live GitHub is authoritative. This file records the exact Candidate #9 evidence/manual verdict and the bounded Candidate #10 runtime now awaiting normal user-authored validation. It does not reopen any accepted/protected runtime block.

## Active strand
- Repository: `Yahyachaos/Yahya-AI`
- PR: #111 — **DRAFT**, open, not merged
- Branch: `auto/celine/v80-human-videochat-presence`
- Canonical visual target image: `/Refernzbild.png`
- Canonical visual target contract: `docs/celine/room-source/v80-2026-08-28/ROOM_VISUAL_TARGET_REFERENCE.md`
- Candidate #9 runtime-equivalent checkpoint: `66713faa34f7bdb4f34495d295f6cf823c034f08`
- Candidate #9 proof head: `1ea33ee23db10378700e00a5b09446afe3534dd7`
- Candidate #10 bot cleanup/head: `9d4514902ad1cd06e6ca08af16afff599e5eb7b8`

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

## Candidate #9 exact evidence and verdict
Candidate #9 changed only the isolated ceiling duplicate base color in LINEAR space from `(1.0, 0.92, 0.82, 1.0)` to `(1.0, 1.0, 0.92, 1.0)`.

Android Build **#830 / run `33342948601`: SUCCESS** on the runtime-equivalent user checkpoint. A later docs-only descendant also received Android Build #831 without changing runtime identity.

Celine Room Visual Polish Proof **#14 / run `33343083927`: SUCCESS structurally** on proof head `1ea33ee23db10378700e00a5b09446afe3534dd7`.

- evidence artifact: `9741173060`
- evidence digest: `sha256:1268c51678e3b963aab71853cd2459519d789bc71ff8483934eb02c3bfa69dc5`
- structural result: PASS; HOME -> CALL -> HOME remained stable and Celine remained visible
- manual verdict: **FAIL**

Manual inspection of the actual `home.png`, `call.png` and `home-return.png` against `/Refernzbild.png` shows Candidate #9 successfully lightened the upper ceiling field, but it overshot toward a pale cool/grey-cream slab. The reference ceiling is visibly warm beige/tan with natural warm shading, not a cool flat band. The wall remains warm cream and the floor remains too orange/red-brown; the floor was intentionally untouched for this candidate.

## Diagnosis
The isolated ceiling override is now proven to control the ceiling independently. Candidate #8 was too dark/taupe; Candidate #9 is lighter but too cool/pale. The remaining confirmed ceiling mismatch is therefore the isolated ceiling color balance, not global fill quantity and not entity targeting.

## Candidate #10 bounded runtime now implemented
Candidate #10 changes only the isolated `room_ceiling` duplicate base color in LINEAR space from Candidate #9 `(1.0, 1.0, 0.92, 1.0)` to `(1.0, 0.97, 0.82, 1.0)`.

Preserved exactly:
- ceiling metallic `0.0`, roughness `0.88`, reflectance `0.40`
- all wall factors
- floor factor/material
- indirect and directional lighting
- exposure/skybox
- room GLB bytes, geometry/transforms
- Celine, camera/zoom, anchors/navigation/actions and Lamp behavior

The direct Android Build #834 on the GitHub-Actions-authored Candidate #10 head concluded `action_required`, which is the known GitHub approval behavior for bot-authored PR heads rather than a compiler/runtime failure. This docs-only user-authored checkpoint preserves Candidate #10 runtime exactly and exists only to obtain the normal PR validation run.

## Exact next action
Obtain exactly one Android build on this runtime-equivalent user-authored checkpoint. If successful, run exactly one targeted HOME -> CALL -> HOME Room Visual Polish proof, inspect the real images against `/Refernzbild.png`, and record PASS/FAIL. Do not stack a floor, camera, global-light or other runtime change before that evidence. No merge, no release, no NavMesh/free navigation.
