# Celine Room Progress Scoreboard / Stop-Loss Policy

This policy exists to prevent long chains of subjective one-object iterations during the reference-constrained room rebuild.

## Core rule

`/Refernzbild.png` remains the visual authority. Numerical scoring assists prioritization and regression detection; it never overrides an obvious manual visual mismatch.

After each logically coherent geometry/layout/camera batch that produces a real HOME/CALL proof:

1. Open the actual proof and reject blank/stale/wrong-scene/wrong-framing evidence immediately.
2. Measure all reliably visible reference objects affected by the batch in normalized raster coordinates.
3. Store the measurements in a proof-specific JSON file.
4. Run `tools/celine-room-scoreboard.py` against `ci/evidence/CELINE_ROOM_REFERENCE_LAYOUT_TARGETS.json`.
5. Compare the new scoreboard with the previous accepted/best scoreboard when available.
6. Keep the candidate as progress only when the whole-scene score improves materially and manual visual inspection agrees.
7. If the weighted whole-scene score regresses, revert/reject the candidate instead of stacking another speculative patch on top.

## Batch, not one-object churn

A bounded room change may contain several furniture transforms/rotations/scales when they are part of one measured composition correction. Examples: bed + both nightstands; dresser + lounge chair; or a coherent camera/layout pass.

Do not force separate Android builds/proofs for every chair, nightstand or small transform when one shared measurement pass supports a combined correction.

Keep unrelated surfaces separated: do not mix material/light polish into a geometry/layout batch.

## Priority

Always work from the largest reliable weighted visual delta toward smaller deltas. Primary architecture/furniture receives higher scoring weight than secondary decoration.

The scoreboard reports the five largest weighted deltas. The next geometry/layout batch should normally target one coherent group containing the largest reliable deltas, unless manual visual evidence establishes a stronger dependency.

## Stop-loss

Do not repeat essentially the same tuning strategy indefinitely.

- If a candidate is `REGRESSED`, reject/revert that strategy before continuing.
- If two consecutive candidates using the same causal strategy are `FLAT` or fail to materially improve the intended metric, stop tuning that variable/family and perform a fresh root-cause analysis.
- Do not move to micro-polish while primary geometry/camera score remains materially outside the work-order tolerances.

## Validation cost

For one runtime-changing coherent room batch: exactly one necessary Android build and one smallest relevant real HOME/CALL proof. Do not run a full final suite during draft iteration.

Docs/measurement/scoreboard-only changes do not require an Android build.

## Handoff

Each room handoff must record:

- current branch head and runtime-code head separately when a docs/tool-only commit is newer;
- runtime fingerprint;
- proof/run/artifact used for measurements;
- scoreboard total score and decision (`NO_BASELINE`, `IMPROVED`, `FLAT`, or `REGRESSED`);
- largest remaining deltas;
- manual visual PASS/FAIL;
- exactly one `exact_next_action` or `complete/integration-ready`.
