# Celine validation policy

This policy exists to keep Yahya AI / Celine development safe **and** efficient. Expensive Android emulator, video, lifecycle, keyboard, zoom, multiview and release gates are not iteration-time defaults.

## 1. Iteration mode — draft PR

Use a draft PR while the active queue step is still being implemented or repaired.

- Run fast, directly relevant checks first.
- Keep the normal Android compile/build check for runtime changes.
- Do **not** automatically run the full Android emulator visibility suite on every draft-PR commit.
- Do **not** automatically run the full Celine HOME -> CALL -> HOME, zoom and multiview render proof on every draft-PR commit.
- A targeted expensive gate may be started manually with `workflow_dispatch` when it is needed to diagnose a specific visual/lifecycle problem.
- A new commit makes previous PR-head evidence stale. Obsolete in-progress PR runs should be cancelled rather than allowed to consume runner time.

## 2. Final exact-head mode — ready for review

When the implementation is believed complete, mark the PR ready for review. That final-head state is the point for the expensive proof suite.

Required as applicable to the active work order:

- Android build
- Android emulator avatar visibility
- HOME -> CALL -> HOME lifecycle
- zoom/framing
- keyboard/IME only when the active change can affect input/focus/layout, or when required by the work order
- audio/voice/lip-sync only when the active change can affect those paths, or when required by the work order
- real Celine render/multiview proof for visible avatar/geometry/material/camera changes
- any task-specific real-device or video evidence required by the canonical queue/work order

If the final head changes after a failure or review fix, re-run the affected gates and the complete final gate set required by that work order before merge.

## 3. Main/release mode

After merging the exact validated PR head:

- validate the actual merge SHA on `main`
- run the production Android/emulator gate required for release
- publish the APK only from that exact validated `main` SHA
- verify tag, asset, digest, version and downloadability/testability
- reconcile `ci/CELINE_PROGRESS_QUEUE.json`

Do not repeat unrelated PR-only diagnostic suites after release unless the work order explicitly requires them.

## 4. Single-flight and agent efficiency

- Exactly one Celine implementation strand may be active.
- Waiting on GitHub Actions is not a reason to start a second branch or PR.
- Do not spend an agent run repeatedly polling a long external job when useful work on the same single-flight step can be done safely in parallel with that external check.
- Do not run keyboard, video, zoom, lifecycle, audio or multiview tests merely because they exist. Run them when the changed surface or the active work order requires them.
- Compiler/build failures, targeted unit/contract failures and task-specific regression checks should be fixed before the expensive final suite.
- The final exact-head and exact-main safety requirements are never weakened by this policy; only redundant iteration-time repetitions are removed.

## 5. Workflow convention

The repository CI follows this convention:

- Draft pull request = iteration mode; expensive emulator/render jobs are skipped automatically.
- Ready-for-review pull request = final exact-head mode; expensive gates run automatically when their path filters match.
- `workflow_dispatch` = explicit targeted/manual proof when a draft needs an expensive diagnostic gate.
- `main` = production validation and release behavior remains protected.
