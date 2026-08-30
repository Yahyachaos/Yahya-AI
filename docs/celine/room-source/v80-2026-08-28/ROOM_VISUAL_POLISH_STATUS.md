# v80 Room Visual Polish Candidate #7 — manual verdict / Candidate #8 plan

Live GitHub is authoritative. This file records the exact Candidate #7 build/proof/manual review and the next smallest evidence-backed correction. It does not reopen any accepted/protected runtime block.

## Active strand
- Repository: `Yahyachaos/Yahya-AI`
- PR: #111 — **DRAFT**, open, not merged
- Branch: `auto/celine/v80-human-videochat-presence`
- Candidate #7 runtime commit: `7162a80332eb046d981a65377a5c31543eb49dd3`
- Candidate #7 proof/checkpoint head: `ff3a49fb4517c076831b578120fb49664337245b`
- Runtime file changed by Candidate #7: `app/src/main/java/de/yahya/ai/Celine3DView.java`

## Protected baseline
Unchanged and protected:
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

Manual inspection of `home.png`, `call.png` and `home-return.png` shows the ceiling remains a distinct darker grey/taupe band while the back wall is warm light beige. Neutralizing indirect irradiance to `(1.0, 1.0, 1.0)` did not remove the mismatch. The floor remains strongly orange/red-brown. Celine and the room structure remain stable.

## Diagnosis after Candidates #4–#7
Candidates #4–#6 showed that more indirect fill lightens the ceiling, but Candidate #7 proves global indirect color alone cannot make the ceiling match the wall. The accepted GLB maps wall and ceiling nodes to the same glTF material, and the current runtime tuning mutates the shared `MaterialInstance`; therefore wall and ceiling cannot be independently corrected by another global factor without also shifting the wall/floor lighting.

The next bounded correction should stop global-light chasing and isolate only the ceiling material at runtime by duplicating the existing glTF `MaterialInstance` for `room_ceiling`, rebinding only that renderable primitive, and applying a slightly lighter warm-beige factor to that duplicate. This keeps textures/material shader features inherited from the original instance and leaves wall instances, floor, directional light, exposure, GLB bytes, transforms, Celine, camera/zoom, anchors/navigation and Lamp behavior unchanged.

## Candidate #8 exact bounded plan
- keep Candidate #7 lighting unchanged: indirect `(1.0, 1.0, 1.0)` at `8000`, directional `14000`
- duplicate the existing `room_ceiling` material instance only
- bind the duplicate only to `room_ceiling`
- set only the duplicate ceiling base color to a lighter warm beige
- keep wall material factors unchanged
- keep floor unchanged for this candidate
- destroy the duplicate material instance during room cleanup

After the runtime change: one Android build, then exactly one HOME/CALL/HOME Room Visual Polish proof, then manual image inspection and PASS/FAIL. No merge, no release, no NavMesh/free navigation.
