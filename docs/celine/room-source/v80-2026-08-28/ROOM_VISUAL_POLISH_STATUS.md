# v80 Room Visual Polish Candidate #4 — manual verdict and next bounded step

Live GitHub is authoritative. This file records the exact Candidate #4 build/proof/manual review and the next smallest bounded correction. It does not reopen any accepted/protected runtime block.

## Active strand
- Repository: `Yahyachaos/Yahya-AI`
- PR: #111 — **DRAFT**, open, not merged
- Branch: `auto/celine/v80-human-videochat-presence`
- Candidate #4 runtime commit: `db7b3bb3485e5b051f8ca8ba93a597b6841401cc`
- Candidate #4 proof/checkpoint head: `b0deede039e1f66e76687ebdd7191c731f08adf5`
- Runtime file changed: `app/src/main/java/de/yahya/ai/Celine3DView.java`

## Candidate #4 bounded change
Candidate #4 changed only the shared Filament indirect fill:

- irradiance: `(1.0, 0.82, 0.68)` -> `(1.0, 0.94, 0.88)`
- intensity: `3000` -> `5000`

Protected and unchanged:
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

## Exact build evidence
Android Build **#806**, run `33318676495`: **SUCCESS**.

The targeted proof resolved Candidate #4 to runtime fingerprint:
`12aa306296f933cb2d9e63717046ff0a00825b1e154100408c2badc37ac02ca2`.

No merge and no release.

## Exact targeted visual proof
Celine Room Visual Polish Proof **#7**, run `33318897762`: **SUCCESS**.

- proof head: `b0deede039e1f66e76687ebdd7191c731f08adf5`
- runtime-equivalent Candidate #4 state: `db7b3bb3485e5b051f8ca8ba93a597b6841401cc` plus docs-only checkpoint
- evidence artifact: `9734328505`
- evidence digest: `sha256:64fd8f14c2daa87b3903cc0b3718ffe9b3e3cc2fe82aa0d947a98660fb3c3300`
- captured evidence: `home.png`, `call.png`, `home-return.png`, diagnostics
- structural result: PASS; Celine remained visible and HOME -> CALL -> HOME remained stable
- workflow SUCCESS is not visual acceptance

## Manual visual verdict — Candidate #4
**FAIL, but materially improved.**

The indirect-fill diagnosis was correct: the upper ceiling is substantially brighter than Candidate #3 and no longer reads as the same near-dark brown field. However, the large ceiling area still reads as a noticeably darker taupe/brown-grey band than the warm-beige back wall, so Candidate #4 is not yet a coherent light warm-beige room baseline.

The floor remains strongly red/orange-brown. That was expected because Candidate #4 intentionally did not change the floor material; the floor is therefore not used as a reason to stack a second change into Candidate #4.

HOME, CALL and HOME-return remained visually/structurally stable. Celine stayed visible. No protected camera, navigation, room transform, furniture or Lamp behavior was disturbed.

## Confirmed diagnosis after Candidate #4
Candidate #4 proves the ceiling mismatch is primarily indirect-fill limited: increasing and neutralizing indirect light visibly lifts the downward-facing ceiling while preserving the directly lit wall. Because the correction moved in the right direction without requiring directional-light or exposure changes, the safest next iteration is one further indirect-fill intensity adjustment only.

## Exact next action
After fresh AGENTS/Queue/PR reconciliation, make exactly one bounded **Candidate #5 indirect-fill intensity correction** in `Celine3DView.java`:

- keep irradiance exactly `(1.0, 0.94, 0.88)`;
- increase only indirect-light intensity from `5000` to `6500`;
- keep skybox, directional-light direction/color/intensity, camera exposure, all room material factors, GLB bytes, geometry/transforms, Celine, camera/zoom, anchors/navigation and Lamp parameters unchanged;
- do not change the floor material in the same candidate;
- do not restore the rejected `7000` indirect + `32000` directional setup.

Then obtain exactly one necessary Android build, run exactly one targeted HOME/CALL/HOME Room Visual Polish proof, manually inspect the actual images and record PASS/FAIL. Do not begin continuous free/NavMesh navigation.
