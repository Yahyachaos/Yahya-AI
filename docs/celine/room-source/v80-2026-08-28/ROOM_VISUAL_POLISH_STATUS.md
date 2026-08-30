# v80 Room Visual Polish Candidate #5 — manual verdict / Candidate #6 next bounded step

Live GitHub is authoritative. This file records the exact Candidate #5 build/proof/manual review and the next smallest bounded correction. It does not reopen any accepted/protected runtime block.

## Active strand
- Repository: `Yahyachaos/Yahya-AI`
- PR: #111 — **DRAFT**, open, not merged
- Branch: `auto/celine/v80-human-videochat-presence`
- Candidate #5 runtime commit: `4947e9b0ee2b16e55f0707b09124a7c8b41bdebd`
- Candidate #5 proof/checkpoint head: `7dfffb38d5059f3e7573231cb93c980b7fd6e77f`
- Runtime file changed by Candidate #5: `app/src/main/java/de/yahya/ai/Celine3DView.java`

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

## Candidate #5 bounded runtime correction
Runtime commit `4947e9b0ee2b16e55f0707b09124a7c8b41bdebd` changed exactly one runtime value:

- indirect irradiance remained `(1.0, 0.94, 0.88)`
- indirect intensity `5000` -> `6500`

No floor-material, directional-light, exposure, furniture, Celine, camera/navigation or Lamp change was stacked into Candidate #5.

## Candidate #5 exact build evidence
Android Build **#810**, run `33319969079`: **SUCCESS** on runtime-equivalent proof/checkpoint head `7dfffb38d5059f3e7573231cb93c980b7fd6e77f`.

No merge and no release.

## Candidate #5 exact targeted visual proof
Celine Room Visual Polish Proof **#8**, run `33322768977`: **SUCCESS**.

- proof head: `7dfffb38d5059f3e7573231cb93c980b7fd6e77f`
- runtime-equivalent Candidate #5 state: `4947e9b0ee2b16e55f0707b09124a7c8b41bdebd` plus docs-only checkpoint
- evidence artifact: `9735393106`
- evidence digest: `sha256:9d55cbacad828e2327ea7f004683602797cbaaca24c84f320e501e7f475c9284`
- captured evidence: `home.png`, `call.png`, `home-return.png`, diagnostics
- structural result: PASS; Celine remained visible and HOME -> CALL -> HOME remained stable
- workflow SUCCESS is not visual acceptance

## Candidate #5 manual visual verdict
**FAIL, but improved again.**

The ceiling is visibly lighter than Candidate #4 and the indirect-fill correction continues to move in the correct direction. However, the large upper ceiling field still reads as a distinctly darker grey-taupe band than the warm-beige wall. The room therefore still lacks the intended coherent light warm-beige shell and Candidate #5 is not accepted as the visual baseline.

The floor remains strongly red/orange-brown. This remains intentionally outside the current candidate so that the ceiling cause stays isolated. HOME, CALL and HOME-return remain stable; Celine stays visible and no protected camera, navigation, furniture, transform or Lamp behavior is disturbed.

## Confirmed diagnosis after Candidate #5
Candidate #4 and #5 together show a monotonic, visible improvement from indirect fill alone. There is still useful headroom before changing any second variable. The safest next iteration is therefore one more bounded indirect-intensity step while keeping irradiance and all direct-light/material parameters unchanged.

## Exact next action — Candidate #6
Make exactly one bounded Candidate #6 correction in `Celine3DView.java`:

- keep indirect irradiance exactly `(1.0, 0.94, 0.88)`;
- increase only indirect-light intensity from `6500` to `8000`;
- keep directional intensity at `14000` and all other protected values unchanged;
- do not change the floor material in the same candidate;
- do not alter exposure, skybox, GLB bytes, transforms, Celine, camera/zoom, anchors/navigation or Lamp parameters.

Then obtain exactly one necessary Android build, run exactly one targeted HOME/CALL/HOME Room Visual Polish proof, manually inspect the actual images and record PASS/FAIL. Do not begin continuous free/NavMesh navigation.
