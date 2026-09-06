# Yahya AI — Repository Agent Instructions

This file is the permanent entry point for Codex and every development agent working on `Yahyachaos/Yahya-AI`.
It defines how to discover the current task safely and efficiently. It intentionally contains no current version, PR number, branch SHA, workflow run number, temporary blocker, or `exact_next_action`.

## 1. Source of truth

- `Yahyachaos/Yahya-AI` is the only project Source of Truth.
- Never replace newer GitHub state with an old chat, ZIP, local copy, screenshot, remembered SHA, README, or stale PR body.
- Live GitHub facts win over stale handoff text.
- Historical files are context only unless the canonical queue/work-order points to them.

## 2. Mandatory start chronology — before any change

For every continuation, maintenance, debugging, implementation, validation, merge, or release task:

1. Read this `AGENTS.md` completely from current live `main`.
2. Read `ci/CELINE_PROGRESS_QUEUE.json` completely.
3. From the queue and any registered workstream entries, identify at minimum each active workstream's branch/PR, work-order, status, owned scope/paths, latest runtime-code head, last built runtime head, latest visual proof and PASS/FAIL, blocker/root cause, and `exact_next_action`.
4. Read the active work-order for the workstream you intend to touch completely.
5. Read `ci/CELINE_VALIDATION_POLICY.md` completely.
6. Read `ci/CELINE_SOURCE_ASSET.json` before any Celine asset/rig/model work.
7. Fresh-reconcile live GitHub: actual `main`, active PR/draft states, actual workstream branch heads, commits newer than queue checkpoints, reviews/comments when relevant, checks/workflow runs, relevant artifacts/evidence, and merge/release state when applicable.
8. Determine whether another agent or useful workflow is already executing the same workstream step or touching overlapping paths/subsystems.
9. Reconcile differences between Queue and Live GitHub before writing.
10. Only then perform exactly the next necessary bounded action inside one clearly owned workstream.

Never begin implementation merely from remembered state.

## 3. State precedence

When information conflicts, use this precedence:

1. Live GitHub facts for repository/PR/branch/run/release state.
2. This `AGENTS.md` for permanent operating rules.
3. `ci/CELINE_PROGRESS_QUEUE.json` for intended workstream state and handoffs.
4. The active work-order for task-specific requirements and acceptance criteria.
5. `ci/CELINE_VALIDATION_POLICY.md` for validation scope and phase rules.
6. Canonical manifests/reference documents for protected inputs.
7. Historical README/docs only as background.

If Queue and Live GitHub disagree, reconcile first. Never overwrite newer live state with an older queue snapshot.

## 4. Controlled parallel workstreams

Parallel development is allowed only for genuinely independent workstreams. The old global "exactly one Celine implementation strand" rule is replaced by **one writer per workstream plus strict ownership boundaries**.

Requirements:

- Each active workstream must have a clear name, goal, work-order or issue, branch/PR when code changes are involved, owned files/subsystems, protected shared files, current head, validation state, blocker/root cause and exactly one `exact_next_action`.
- Exactly one writer may mutate a given workstream at a time. Never run two writers on the same branch, same files, same runtime subsystem, same proof target or same queue state.
- Two workstreams may run in parallel only when their owned paths/subsystems do not overlap and neither depends on unvalidated output from the other.
- Room/scene/layout/camera/material work may proceed in parallel with a separate conversation/persona/intelligence/backlog workstream only when the second workstream does not touch room-owned runtime files or shared integration surfaces.
- Shared integration surfaces are exclusive unless explicitly handed off. At minimum treat `AGENTS.md`, `ci/CELINE_PROGRESS_QUEUE.json`, release/version metadata, central app wiring, shared runtime manifests, common build configuration and any file named by two workstreams as protected shared state.
- A workstream must not modify another workstream's owned files merely because a cleaner implementation is possible. If ownership must change, stop, reconcile, record the handoff, then transfer ownership explicitly.
- Waiting for GitHub Actions in one workstream is permission to advance another **independent registered workstream**, but never permission to start duplicate work on the waiting workstream.
- Do not advance a branch head unnecessarily while a useful exact-head validation for that same workstream is still running and needed as evidence.
- Never silently abandon a failing workstream and replace it with a cleaner parallel branch.
- Integration, Ready-for-Review transitions, merges, exact-main validation, versioning and release remain serialized and coordinated. Parallel workstreams do not get to merge or release independently without reconciliation.

Before any write, answer: **Which workstream owns this file? Is another writer/workflow touching it? Does this write invalidate evidence another workstream still needs?** If any answer is unclear, do not write until reconciled.

## 5. Truth about heads, fingerprints, builds, and visual evidence

Always distinguish per workstream:

- **current branch head** — newest commit on that workstream's branch.
- **runtime fingerprint** — SHA-256 identity of runtime/build-relevant repository inputs produced by `ci/celine-runtime-fingerprint.sh`.
- **latest runtime-code head** — newest commit that changes executable/runtime behavior.
- **latest built runtime head** — runtime-code head with a successful required Android build.
- **latest visually accepted runtime head** — exact runtime state whose required visual evidence was actually inspected and accepted.
- **docs/queue/CI-only head** — newer commit that does not change the runtime fingerprint.

Rules:

- A docs/queue/CI-only head is not a newly runtime-validated head.
- Runtime-equivalent APK reuse is allowed only when the runtime fingerprint matches and the selected successful Android run owns a live `yahya-ai-debug` artifact.
- A SUCCESS workflow without an APK artifact is never an APK source.
- Evidence remains bound to the SHA/runtime fingerprint it actually tested.
- Workflow SUCCESS is not visual acceptance.
- Blank, black, off-screen, corrupt, stale, fallback, badly framed, mislabeled, or visibly wrong evidence is FAIL.
- `ci/celine-avatar-proof-preflight.py` is fail-closed structural preflight only; it never grants visual PASS.

## 6. Efficiency fast path — use this on every bounded iteration

Classify the change before running anything:

```text
Docs / queue / handoff only?
→ no Android APK build.

Proof / CI only and runtime fingerprint unchanged?
→ reuse a verified runtime-equivalent APK.

Runtime code / build input / runtime asset changed?
→ one Android build for that workstream change.

Visual runtime surface affected?
→ exactly one targeted proof after the relevant build/reuse decision.

Proof fails?
→ change only the confirmed cause, then repeat the smallest relevant check.

Work-order acceptance complete?
→ only then move that workstream toward integration/final exact-head gates.
```

Operational loop:

```text
RECONCILE
→ CONFIRM WORKSTREAM OWNERSHIP
→ CLASSIFY CHANGE
→ ONE BOUNDED CHANGE OR ONE LOGICALLY COHERENT BATCH
→ SMALLEST REQUIRED BUILD/CHECK
→ ONE TARGETED PROOF IF NEEDED
→ INSPECT ACTUAL EVIDENCE
→ RECORD TRUTH
→ NEXT BOUNDED CHANGE
```

A bounded change may include several tightly coupled values or objects when they form one coherent correction (for example a measured furniture-layout pass). Do not force one-build-per-object micro-iterations when one evidence-backed batch can be validated together. Do not stack unrelated speculative fixes before inspecting evidence.

## 7. Efficiency infrastructure map

Use these helpers instead of re-inventing state detection:

```text
ci/celine-runtime-fingerprint.sh       runtime/build-input identity
ci/android-build-scope.sh              draft build-vs-reuse decision
ci/celine-find-runtime-apk.sh          exact fingerprint APK lookup
ci/celine-avatar-proof-preflight.py    blank/stale/basic-image fail-closed guard
ci/celine-avatar-proof-compare.py      baseline/candidate evidence metadata comparison
ci/celine-reconcile-state.py           machine-verifiable GitHub reconciliation snapshot
ci/evidence/celine-avatar-accepted.json criterion-specific accepted visual evidence
```

Machine reconciliation must not invent `visual_acceptance`, root cause, blocker, or `exact_next_action`; those remain explicit agent judgments after evidence inspection.

## 8. Canonical Celine asset

Before touching or replacing Celine source geometry, rig, morphs, materials, or model inputs, read:

```text
ci/CELINE_SOURCE_ASSET.json
```

The source declared there is canonical.

- Never silently swap in a test model, old GLB, fallback image, transient upload, locally remembered asset, or unrelated export.
- Large model files may be Git LFS pointers in normal Git metadata; a small pointer is not the binary model size.
- Preserve validated rig, morph, material, reference, and runtime behavior unless the active work-order requires a controlled change.

Useful references:

```text
ci/CELINE_SOURCE_ASSET.json
app/src/main/assets/models/CELINE_MESHY_RIG_AUDIT.txt
app/src/main/assets/models/README_CELINE_3D.txt
docs/celine/reference/v2/REFERENCE_MANIFEST.json
docs/celine/reference/v2/REFERENCE_RETRIEVAL.md
```

## 9. Technical map — orientation, not mandatory reading

Read only surfaces relevant to the active task.

Core:
```text
app/src/main/java/de/yahya/ai/Celine3DView.java
app/src/main/java/de/yahya/ai/CelineAvatarController.java
app/src/main/java/de/yahya/ai/MainActivity.java
```

Scale/rig/morph:
```text
app/src/main/java/de/yahya/ai/CelineMeshyRigScaleV61.java
app/src/main/java/de/yahya/ai/CelineMorphRuntimeV62.java
app/src/main/java/de/yahya/ai/CelineGlbValidator.java
```

Avatar Lab / visual diagnostics:
```text
app/src/main/java/de/yahya/ai/CelineAvatarLabActivity.java
app/src/main/java/de/yahya/ai/CelineAvatarLabCaptureActivity.java
app/src/main/java/de/yahya/ai/CelineAvatarLabPoseDriverV79.java
```

Body/camera/presence:
```text
app/src/main/java/de/yahya/ai/CelineNaturalBodyMotionV73.java
app/src/main/java/de/yahya/ai/CelineArmHandPresenceV74.java
app/src/main/java/de/yahya/ai/CelineArmHandPresenceV79.java
app/src/main/java/de/yahya/ai/CelineSeatedCallV70.java
app/src/main/java/de/yahya/ai/CelineCameraZoomV70.java
app/src/main/java/de/yahya/ai/CelineProductInteractionLockV79.java
```

Voice/conversation:
```text
app/src/main/java/de/yahya/ai/SpeechAudioBus.java
app/src/main/java/de/yahya/ai/SpeechLipSyncV77.java
app/src/main/java/de/yahya/ai/ConversationIntelligenceV78.java
```

## 10. Validation strategy

Canonical policy: `ci/CELINE_VALIDATION_POLICY.md`.

Draft iteration:
- use the smallest directly relevant check first;
- runtime fingerprint change gets one Android build;
- runtime-equivalent docs/CI/proof changes reuse a verified APK when safe;
- avatar/geometry/rig/morph/material/camera/pose visual changes get the relevant visual proof;
- keyboard/audio/video/lifecycle/multiview gates run only when the changed surface or work-order requires them;
- do not run the full expensive suite after every small change.

Important entry points:
```text
.github/workflows/android-build.yml
.github/workflows/celine-avatar-lab-proof.yml
ci/avatar-lab-proof.sh
.github/workflows/celine-real-candidate-render-proof.yml
ci/celine-real-candidate-emulator-proof.sh
```

Final exact-head:
- move a workstream to Ready for Review only when its work-order is actually complete and integration ownership is clear;
- run the complete exact-head gates required by the work-order;
- final transitions deliberately rebuild/revalidate even if an older runtime fingerprint exists when policy requires exact-head evidence.

Main/release:
- integrate/merge only reconciled, validated workstream heads;
- validate the actual merge SHA on main;
- publish only from exact validated main;
- read back tag, target SHA, version, asset name, digest, and release wording;
- reconcile Queue only after readback succeeds.

## 11. Permanent work chronology

Per workstream:

```text
RECONCILE
→ CLAIM/CONFIRM OWNERSHIP
→ DRAFT ITERATION
→ TARGETED VALIDATION / PROOF
→ WORK-ORDER ACCEPTANCE
→ CLEAN HANDOFF / INTEGRATION-READY STATE
```

Serialized integration/release:

```text
RECONCILE ALL WORKSTREAMS
→ FINAL EXACT-HEAD
→ MERGE EXACT VALIDATED HEAD(S) IN CONTROLLED ORDER
→ EXACT-MAIN VALIDATION
→ RELEASE / READBACK
→ QUEUE RECONCILIATION
```

Do not skip stages by assumption.

## 12. Mandatory clean completion and handoff discipline

**Every agent assignment must end cleanly.** Never stop with ambiguous ownership, undocumented local intent, or an unclear continuation point.

Before stopping, handing off, switching workstreams, or declaring an assignment complete:

1. Fresh-reconcile `main`, the workstream branch/PR, relevant workflows/artifacts and any overlapping workstream.
2. Ensure all intended writes are committed/pushed to the correct branch; do not leave hidden or unrecorded changes.
3. Record the actual current branch head and, when applicable, runtime fingerprint, latest runtime-code head and latest built runtime head.
4. Record what was changed and what was deliberately not changed.
5. Record the smallest relevant validation performed and its real conclusion. For visual work, include the latest actual proof and manual PASS/FAIL; never substitute workflow SUCCESS for visual acceptance.
6. Record artifacts/run IDs/digests when they are relevant to continuation.
7. Record the current blocker/root cause, or explicitly state that no blocker exists.
8. Leave **exactly one clear `exact_next_action`** for that workstream, unless the workstream is fully complete.
9. If fully complete, mark the workstream complete/integration-ready and state what acceptance criteria were satisfied. Do not invent a next action merely to keep work alive.
10. Release ownership of the files/subsystems so another worker can safely continue.

`ci/CELINE_PROGRESS_QUEUE.json` and/or the canonical workstream PR/issue handoff must let a completely new agent continue without chat history. When multiple workstreams exist, the queue/handoff must distinguish them explicitly rather than flattening their heads or evidence together.

Before a queue write: fresh-reconcile main/PR/workflows/release, re-read the actual queue, do not overwrite a newer queue blob, preserve docs-only vs runtime distinctions, and never claim visual acceptance without inspecting evidence.

## 13. Protected behavior principle

Preserve previously validated behavior unless the active work-order requires a minimal evidence-backed change. Identify what an older subsystem protects, why the current regression requires touching it, and which focused check proves adjacent behavior remains intact. Do not rewrite working infrastructure merely because a cleaner implementation is possible.

## 14. If the user only says “Continue Yahya AI”

Interpret a generic continuation request as:

1. Open/focus `Yahyachaos/Yahya-AI`.
2. Read current live-main `AGENTS.md`.
3. Execute the Mandatory Start Chronology.
4. Fresh-reconcile GitHub, Queue and all registered active workstreams.
5. Resume an unowned or explicitly assigned workstream at its true `exact_next_action` using the Efficiency Fast Path.
6. Never compete with an existing writer. If one workstream is busy/waiting, useful work may continue on another registered independent workstream with non-overlapping ownership.
7. Finish the assignment with the mandatory clean handoff protocol in section 12.

No prior chat transcript is required if the repository handoff is healthy.

## 15. START_HERE.txt

`START_HERE.txt` is only a redirect for humans or agents that open it accidentally. Do not use it as a development-state document. The canonical development entry is this `AGENTS.md`.
