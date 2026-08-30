# v80 Room Visual Polish Candidate #6 — manual verdict / Candidate #7 runtime checkpoint

Live GitHub is authoritative. This file records the exact Candidate #6 build/proof/manual review and the currently active smallest bounded Candidate #7 correction. It does not reopen any accepted/protected runtime block.

## Active strand
- Repository: `Yahyachaos/Yahya-AI`
- PR: #111 — **DRAFT**, open, not merged
- Branch: `auto/celine/v80-human-videochat-presence`
- Candidate #6 runtime commit: `ff8a9d5d6419d3feb6e3fe3b28b9014e8e5a87fb`
- Candidate #6 proof/checkpoint head: `b564b7ee028883183e8821a6a70b84836e417591`
- Candidate #7 runtime commit: `7162a80332eb046d981a65377a5c31543eb49dd3`
- Runtime file changed by Candidate #7: `app/src/main/java/de/yahya/ai/Celine3DView.java`

## Protected baseline
Unchanged and protected:
- skybox
- directional-light color, direction and intensity (`14000`)
- camera exposure
- all room material factors
- room GLB bytes, geometry and transforms
- all 12 immutable original furniture GLBs
- canonical Celine and Celine separation from room/furniture
- camera/zoom, accepted poses, anchors/navigation and all accepted 9R actions
- accepted 60,000 lm Lamp behavior

## Candidate #6 exact evidence and verdict
Android Build **#814**, run `33325634994`: **SUCCESS** on proof/checkpoint head `b564b7ee028883183e8821a6a70b84836e417591`.

Celine Room Visual Polish Proof **#9**, run `33333764359`: **SUCCESS**.

- runtime-equivalent Candidate #6 state: `ff8a9d5d6419d3feb6e3fe3b28b9014e8e5a87fb` plus docs-only checkpoint
- evidence artifact: `9738427755`
- evidence digest: `sha256:99e84f3549b7bb571e8c6e9fbf55a0a1d658ae3f97023942b9925ef48efb2188`
- structural result: PASS; Celine remained visible and HOME -> CALL -> HOME remained stable
- manual verdict: **FAIL, improved but no longer worth another intensity-only step**

At `8000` indirect intensity the upper ceiling field remains distinctly darker grey/taupe than the warm-beige wall. The evidence indicates the remaining ceiling mismatch is now predominantly color balance rather than missing fill quantity. The floor remains intentionally untouched and strongly red/orange-brown.

## Candidate #7 bounded runtime correction
Runtime commit `7162a80332eb046d981a65377a5c31543eb49dd3` changes exactly one runtime value:

- indirect intensity remains `8000`
- indirect irradiance `(1.0, 0.94, 0.88)` -> `(1.0, 1.0, 1.0)`

Everything protected above remains unchanged. Directional intensity remains `14000`. No floor-material, exposure or second lighting change is stacked into Candidate #7. The bounded one-shot workflow removed itself after the exact runtime replacement.

Because the Candidate #7 runtime commit is bot-authored, this user-authored docs-only checkpoint exists so the normal PR Android build can validate the exact same Candidate #7 runtime fingerprint without altering runtime behavior.

## Exact next action
Allow exactly one Android build on this runtime-equivalent checkpoint. If successful, run exactly one targeted HOME/CALL/HOME Room Visual Polish proof, manually inspect the actual images and record PASS/FAIL. Do not change floor material, directional light, exposure or any protected behavior until Candidate #7 evidence is inspected. Do not begin continuous free/NavMesh navigation.
