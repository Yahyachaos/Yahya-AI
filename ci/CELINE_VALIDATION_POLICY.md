# Celine validation policy

This policy exists to keep Yahya AI / Celine development safe **and** efficient. Expensive Android emulator, video, lifecycle, keyboard, zoom, multiview and release gates are not iteration-time defaults.

## 1. Iteration mode — draft PR

Use a draft PR while a workstream queue step is still being implemented or repaired.

- Run fast, directly relevant checks first.
- Keep the normal Android compile/build check for runtime changes.
- Do **not** automatically run the full Android emulator visibility suite on every draft-PR commit.
- Do **not** automatically run the full Celine HOME -> CALL -> HOME, zoom and multiview render proof on every draft-PR commit.
- A targeted expensive gate may be started manually with `workflow_dispatch` when it is needed to diagnose a specific visual/lifecycle problem.
- A new commit makes previous exact-head evidence stale for that workstream. Obsolete in-progress runs should be cancelled rather than allowed to consume runner time when they are no longer useful.
- A logically coherent measured batch may be validated together. Do not force one build/proof per furniture item or per individual constant when several changes belong to one bounded correction and share one acceptance criterion.

## 2. Final exact-head mode — ready for review

When an implementation workstream is believed complete and integration ownership is clear, mark its PR ready for review only when the canonical work-order permits it. That final-head state is the point for the expensive proof suite.

Required as applicable to the active work order:

- Android build
- Android emulator avatar visibility
- HOME -> CALL -> HOME lifecycle
- zoom/framing
- keyboard/IME only when the active change can affect input/focus/layout, or when required by the work order
- audio/voice/lip-sync only when the active change can affect those paths, or when required by the work order
- real Celine render/multiview proof for visible avatar/geometry/material/camera changes
- any task-specific real-device or video evidence required by the canonical queue/work order

If the final head changes after a failure or review fix, re-run the affected gates and the complete final gate set required by that work order before integration/merge.

## 3. Main/release mode

Integration, main validation and release remain serialized even when draft implementation workstreams run in parallel.

After merging reconciled exact validated workstream head(s) in a controlled order:

- validate the actual resulting merge SHA on `main`
- run the production Android/emulator gate required for release
- publish the APK only from that exact validated `main` SHA
- verify tag, asset, digest, version and downloadability/testability
- reconcile `ci/CELINE_PROGRESS_QUEUE.json`

Do not repeat unrelated PR-only diagnostic suites after release unless the work order explicitly requires them.

## 4. Controlled parallel workstreams and agent efficiency

- Multiple Celine workstreams may be active only when they are genuinely independent and explicitly registered/documented with separate ownership.
- Exactly one writer may mutate a given workstream at a time.
- Parallel workstreams must not overlap in owned files, runtime subsystems, proof targets or queue state. Shared integration files are exclusive unless an explicit handoff transfers ownership.
- Room/scene/layout/camera/material work may run in parallel with a separate conversation/persona/intelligence/backlog workstream only when their owned paths and runtime dependencies do not overlap.
- Waiting on GitHub Actions in one workstream is allowed while another independent registered workstream progresses. It is never a reason to duplicate or race the waiting workstream.
- Do not spend an agent run repeatedly polling a long external job when useful work on another independent owned workstream can be done safely.
- Do not run keyboard, video, zoom, lifecycle, audio or multiview tests merely because they exist. Run them when the changed surface or the active work order requires them.
- Compiler/build failures, targeted unit/contract failures and task-specific regression checks should be fixed before the expensive final suite.
- The final exact-head and exact-main safety requirements are never weakened by parallelism; only redundant iteration-time repetitions are removed.
- Integration order must be reconciled before merge. A workstream may not assume another branch's unmerged changes are present unless its branch explicitly contains them.

## 5. Mandatory clean completion per assignment

Every agent assignment must finish with a truthful, reproducible handoff for its workstream:

- fresh current branch/PR head;
- runtime fingerprint when relevant;
- latest runtime-code and built runtime heads when relevant;
- exact checks/proofs performed and their real conclusions;
- relevant artifact/run IDs and digests;
- blocker/root cause or explicit `none`;
- exactly one `exact_next_action`, unless the workstream is fully complete;
- explicit completion/integration-ready status when all work-order acceptance criteria are satisfied;
- confirmation that no hidden/uncommitted/unpushed changes remain and file/subsystem ownership is released.

A completely new agent must be able to continue from repository state without relying on chat history.

## 6. Workflow convention

The repository CI follows this convention:

- Draft pull request = iteration mode; expensive emulator/render jobs are skipped automatically unless explicitly dispatched/required.
- Ready-for-review pull request = final exact-head mode; expensive gates run automatically when their path filters match.
- `workflow_dispatch` = explicit targeted/manual proof when a draft needs an expensive diagnostic gate.
- `main` = production validation and release behavior remains protected.
