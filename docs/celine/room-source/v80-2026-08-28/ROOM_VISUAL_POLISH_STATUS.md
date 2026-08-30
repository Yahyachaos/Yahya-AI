# v80 Room Visual Polish — Candidate #8 verdict / Candidate #9 validation

Live GitHub is authoritative. This file records the exact Candidate #8 proof verdict and the bounded Candidate #9 runtime now under normal validation. It does not reopen any accepted/protected runtime block.

## Active strand
- Repository: `Yahyachaos/Yahya-AI`
- PR: #111 — **DRAFT**, open, not merged
- Branch: `auto/celine/v80-human-videochat-presence`
- Canonical visual target image: `/Refernzbild.png`
- Canonical visual target contract: `docs/celine/room-source/v80-2026-08-28/ROOM_VISUAL_TARGET_REFERENCE.md`
- Candidate #8 runtime commit: `a0e740e91b6befdd5a6c61a55574c2738a96e0d3`
- Candidate #9 bot cleanup/head: `1f67fa7fe4783578dbc0823c14e7cba4055bd300`
- Candidate #9 user-authored checkpoint: `66713faa34f7bdb4f34495d295f6cf823c034f08`
- Runtime file changed by Candidate #9: `app/src/main/java/de/yahya/ai/CelineRoomEnvironmentV80.java`

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

## Candidate #8 exact evidence and verdict
Android Build **#822**, run `33340472461`: **SUCCESS**.

Celine Room Visual Polish Proof **#11**, run `33340655448`: **SUCCESS structurally**.

- evidence artifact: `9740466041`
- evidence digest: `sha256:1837f2e199538964e97d9333b7d094431d37f824e14c7fca8ee1769eca8afbd8`
- structural result: PASS; Celine remained visible and HOME -> CALL -> HOME remained stable
- manual verdict: **FAIL**

Manual inspection against `/Refernzbild.png` shows that the isolated ceiling material works technically, but the ceiling remains visibly too grey/taupe and too separated from the warm cream wall. The floor also remains too orange/red-brown, but floor changes are intentionally deferred until the ceiling candidate is accepted. Workflow SUCCESS is not visual acceptance.

## Candidate #9 bounded runtime
Candidate #9 changes only the already-isolated ceiling material toward the user reference:
- ceiling duplicate base color changed in LINEAR space from `(1.0, 0.92, 0.82, 1.0)` to `(1.0, 1.0, 0.92, 1.0)`
- ceiling metallic remains `0.0`
- ceiling roughness remains `0.88`
- ceiling reflectance remains `0.40`
- wall material factors unchanged
- floor unchanged
- indirect/directional lighting and exposure unchanged
- GLB bytes/transforms, Celine, camera/zoom, anchors/navigation and Lamp behavior unchanged

The direct Android Build #829 on the GitHub-Actions-authored Candidate #9 head concluded `action_required`, which is the known GitHub approval behavior for bot-authored PR heads rather than a compiler/runtime failure.

The runtime-equivalent user-authored checkpoint `66713faa34f7bdb4f34495d295f6cf823c034f08` then received Android Build **#830 / run `33342948601`: SUCCESS**.

## Exact next action
Run exactly one targeted HOME -> CALL -> HOME Room Visual Polish proof for Candidate #9, inspect the actual images against `/Refernzbild.png`, and record PASS/FAIL. Do not change floor, camera, global lighting, GLB bytes/transforms, Celine, navigation or Lamp before that evidence. No merge, no release, no NavMesh/free navigation.
