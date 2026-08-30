# v80 Room Visual Polish Candidate #5 — manual verdict / Candidate #6 runtime checkpoint

Live GitHub is authoritative. This file records the exact Candidate #5 build/proof/manual review and the currently active smallest bounded Candidate #6 correction. It does not reopen any accepted/protected runtime block.

## Active strand
- Repository: `Yahyachaos/Yahya-AI`
- PR: #111 — **DRAFT**, open, not merged
- Branch: `auto/celine/v80-human-videochat-presence`
- Candidate #5 runtime commit: `4947e9b0ee2b16e55f0707b09124a7c8b41bdebd`
- Candidate #5 proof/checkpoint head: `7dfffb38d5059f3e7573231cb93c980b7fd6e77f`
- Candidate #6 runtime commit: `ff8a9d5d6419d3feb6e3fe3b28b9014e8e5a87fb`
- Runtime file changed by Candidate #6: `app/src/main/java/de/yahya/ai/Celine3DView.java`

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

The previously rejected combined daylight setup (`7000` indirect plus `32000` directional) was not restored.

## Candidate #5 exact evidence and verdict
Android Build **#810**, run `33319969079`: **SUCCESS** on proof/checkpoint head `7dfffb38d5059f3e7573231cb93c980b7fd6e77f`.

Celine Room Visual Polish Proof **#8**, run `33322768977`: **SUCCESS**.

- runtime-equivalent Candidate #5 state: `4947e9b0ee2b16e55f0707b09124a7c8b41bdebd` plus docs-only checkpoint
- evidence artifact: `9735393106`
- evidence digest: `sha256:9d55cbacad828e2327ea7f004683602797cbaaca24c84f320e501e7f475c9284`
- structural result: PASS; Celine remained visible and HOME -> CALL -> HOME remained stable
- manual verdict: **FAIL, but improved again**

The ceiling is visibly lighter than Candidate #4, but the upper ceiling field still reads as a distinctly darker grey-taupe band than the warm-beige wall. The floor remains intentionally untouched and strongly red/orange-brown. No protected camera, navigation, furniture, transform or Lamp behavior was disturbed.

## Candidate #6 bounded runtime correction
Runtime commit `ff8a9d5d6419d3feb6e3fe3b28b9014e8e5a87fb` changes exactly one runtime value:

- indirect irradiance remains `(1.0, 0.94, 0.88)`
- indirect intensity `6500` -> `8000`

Everything protected above remains unchanged. Directional intensity remains `14000`. No floor-material, exposure or second lighting change is stacked into Candidate #6.

The runtime change was applied by the bounded one-shot workflow and that workflow removed itself after the exact replacement. Because bot-authored runtime commits can make the direct PR Android run require approval, this user-authored docs-only checkpoint exists so the normal PR build can validate the exact same Candidate #6 runtime fingerprint without altering runtime behavior.

## Exact next action
Allow exactly one Android build on this runtime-equivalent checkpoint. If successful, run exactly one targeted HOME/CALL/HOME Room Visual Polish proof, manually inspect the actual images and record PASS/FAIL. Do not change the floor material, directional light, exposure or any protected behavior until Candidate #6 evidence is inspected. Do not begin continuous free/NavMesh navigation.
