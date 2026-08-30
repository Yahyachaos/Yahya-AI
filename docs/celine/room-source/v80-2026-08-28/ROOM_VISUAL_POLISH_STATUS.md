# v80 Room Visual Polish Candidate #6 — manual verdict / Candidate #7 next bounded step

Live GitHub is authoritative. This file records the exact Candidate #6 build/proof/manual review and the next smallest bounded correction. It does not reopen any accepted/protected runtime block.

## Active strand
- Repository: `Yahyachaos/Yahya-AI`
- PR: #111 — **DRAFT**, open, not merged
- Branch: `auto/celine/v80-human-videochat-presence`
- Candidate #6 runtime commit: `ff8a9d5d6419d3feb6e3fe3b28b9014e8e5a87fb`
- Candidate #6 proof/checkpoint head: `b564b7ee028883183e8821a6a70b84836e417591`
- Runtime file: `app/src/main/java/de/yahya/ai/Celine3DView.java`

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

## Candidate #6 exact evidence
Android Build **#814**, run `33325634994`: **SUCCESS** on runtime-equivalent checkpoint head `b564b7ee028883183e8821a6a70b84836e417591`.

Celine Room Visual Polish Proof **#9**, run `33333764359`: **SUCCESS**.

- proof head: `b564b7ee028883183e8821a6a70b84836e417591`
- runtime-equivalent Candidate #6 state: `ff8a9d5d6419d3feb6e3fe3b28b9014e8e5a87fb` plus docs-only checkpoint
- evidence artifact: `9738427755`
- evidence digest: `sha256:99e84f3549b7bb571e8c6e9fbf55a0a1d658ae3f97023942b9925ef48efb2188`
- captured evidence: `home.png`, `call.png`, `home-return.png`, diagnostics
- structural result: PASS; Celine remained visible and HOME -> CALL -> HOME remained stable
- workflow SUCCESS is not visual acceptance

## Candidate #6 manual visual verdict
**FAIL, improved but no longer worth another intensity-only step.**

The ceiling is lighter than Candidate #5 and the intensity-only diagnosis continued to move in the correct direction, but the large upper ceiling field still reads as a distinctly darker grey/taupe band than the warm-beige wall. At `8000` indirect intensity the remaining problem is now predominantly color balance rather than missing fill quantity. Continuing to raise intensity alone risks flattening Celine and the room without removing the grey/taupe separation.

The floor remains strongly red/orange-brown and intentionally untouched. HOME, CALL and HOME-return remain stable; Celine stays visible. No protected camera, navigation, furniture, transform or Lamp behavior was disturbed.

## Exact next action — Candidate #7
Make exactly one bounded Candidate #7 indirect-color correction in `Celine3DView.java`:

- keep indirect intensity exactly `8000`;
- change only indirect irradiance from `(1.0, 0.94, 0.88)` to neutral `(1.0, 1.0, 1.0)`;
- keep directional intensity/color/direction unchanged at the accepted Candidate #6 values;
- do not change floor material, exposure, skybox, GLB bytes, transforms, Celine, camera/zoom, anchors/navigation or Lamp parameters.

Then obtain exactly one necessary Android build, run exactly one targeted HOME/CALL/HOME Room Visual Polish proof, manually inspect the actual images and record PASS/FAIL. Do not begin continuous free/NavMesh navigation.
