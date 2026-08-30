# v80 Room Visual Polish Candidate #7 verdict / Candidate #8 runtime checkpoint

Live GitHub is authoritative. This file records the exact Candidate #7 evidence and the bounded Candidate #8 runtime now under validation. It does not reopen any accepted/protected runtime block.

## Active strand
- Repository: `Yahyachaos/Yahya-AI`
- PR: #111 — **DRAFT**, open, not merged
- Branch: `auto/celine/v80-human-videochat-presence`
- Candidate #7 runtime commit: `7162a80332eb046d981a65377a5c31543eb49dd3`
- Candidate #7 proof/checkpoint head: `ff3a49fb4517c076831b578120fb49664337245b`
- Candidate #8 runtime commit: `a0e740e91b6befdd5a6c61a55574c2738a96e0d3`
- Runtime file changed by Candidate #8: `app/src/main/java/de/yahya/ai/CelineRoomEnvironmentV80.java`

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

## Candidate #7 exact evidence and verdict
Android Build **#818**, run `33334515166`: **SUCCESS** on runtime-equivalent checkpoint `ff3a49fb4517c076831b578120fb49664337245b`.

Celine Room Visual Polish Proof **#10**, run `33336089904`: **SUCCESS**.

- evidence artifact: `9739098601`
- evidence digest: `sha256:37f4e6840a40f1007d863f7844cbe7ff5d8b5b2a16391156c397b6a0251cbd0e`
- runtime fingerprint: `df7ea60660cbb70ac0b0b6430d8bfc276e0a5f73327e7c39cf8ffc54a205e0fc`
- structural result: PASS; Celine remained visible and HOME -> CALL -> HOME remained stable
- manual verdict: **FAIL**

Manual inspection of the actual `home.png`, `call.png` and `home-return.png` shows the ceiling remains a distinct darker grey/taupe band while the back wall is warm light beige. Neutralizing indirect irradiance to `(1.0, 1.0, 1.0)` did not remove the mismatch. The floor remains strongly orange/red-brown. Celine and room structure remain stable.

## Diagnosis after Candidates #4–#7
Candidates #4–#6 showed that more indirect fill lightens the ceiling, but Candidate #7 proves global indirect color alone cannot make the ceiling match the wall. The accepted GLB maps wall and ceiling nodes to the same glTF material, and the prior runtime tuning mutates the shared `MaterialInstance`; therefore another global factor would also shift the wall rather than independently fixing the ceiling.

## Candidate #8 bounded runtime now implemented
Candidate #8 runtime commit `a0e740e91b6befdd5a6c61a55574c2738a96e0d3` stops global-light chasing and isolates only the ceiling material at runtime:
- duplicates the existing `room_ceiling` material instance only
- binds the duplicate only to the single ceiling primitive
- sets only the duplicate ceiling base color to lighter warm beige `(1.0, 0.92, 0.82, 1.0)` in LINEAR space
- preserves ceiling metallic `0.0`, roughness `0.88`, reflectance `0.40`
- destroys the duplicate material instance during room cleanup
- leaves wall material factors and floor unchanged
- leaves indirect/directional lighting, exposure, GLB bytes, transforms, Celine, camera/zoom, anchors/navigation and Lamp behavior unchanged

The direct Android Build #821 on the GitHub-Actions-authored runtime commit concluded `action_required`, which is the known GitHub approval behavior for bot-authored PR heads rather than a runtime/build failure. This docs-only user checkpoint preserves Candidate #8 runtime exactly and exists only to obtain the normal PR validation run.

## Exact next action
Run exactly one Android build on this runtime-equivalent user checkpoint. If successful, run exactly one HOME/CALL/HOME Room Visual Polish proof, inspect the actual images, and record PASS/FAIL. Do not change the floor or any protected behavior before that evidence. No merge, no release, no NavMesh/free navigation.
