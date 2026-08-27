# Yahya AI — Repository Agent Instructions

This file is the permanent entry point for Codex and any other development agent working on `Yahyachaos/Yahya-AI`.

It defines **how to discover the current task safely**.  
It intentionally does **not** hardcode the current version, PR number, branch SHA, workflow run number, or temporary blocker.

---

## 1. Source of Truth

- `Yahyachaos/Yahya-AI` is the only project Source of Truth.
- Do not replace a newer GitHub state with an older chat transcript, ZIP, local copy, screenshot, remembered SHA, or historical README.
- Live GitHub state wins over stale documentation.
- Historical files may explain context but must not be treated as the current task unless the canonical queue/work-order points to them.

---

## 2. Mandatory Start Chronology — Before Any Change

For every continuation, maintenance, debugging, implementation, validation, merge, or release task:

1. Read this `AGENTS.md` completely.
2. Read `ci/CELINE_PROGRESS_QUEUE.json` completely.
3. From `active`, identify at minimum:
   - active branch
   - active PR
   - active work-order
   - current status
   - latest runtime-code head
   - latest built runtime head
   - latest relevant visual proof and PASS/FAIL
   - current blocker/root cause
   - `exact_next_action`
4. Read the active work-order specified by the queue completely.
5. Read `ci/CELINE_VALIDATION_POLICY.md`.
6. Fresh-reconcile live GitHub:
   - actual `main` SHA
   - PR state/draft state
   - actual branch head
   - commits newer than the queue checkpoint
   - reviews/comments when relevant
   - checks/workflow runs
   - relevant artifacts/evidence
   - merge/release state when applicable
7. Determine whether another agent or workflow is already executing the same Celine step.
8. Reconcile differences between queue and live GitHub before changing code.
9. Only then perform exactly the next necessary bounded action.

Never begin implementation merely from a remembered state.

---

## 3. State Precedence

When information conflicts, use this precedence:

1. Live GitHub facts for current repository/PR/branch/run/release state.
2. This `AGENTS.md` for permanent operating rules.
3. `ci/CELINE_PROGRESS_QUEUE.json` for the intended active strand and handoff state.
4. The active work-order for task-specific requirements and acceptance criteria.
5. `ci/CELINE_VALIDATION_POLICY.md` for validation scope and phase rules.
6. Canonical manifests/reference documents for assets and protected inputs.
7. Historical README/docs only as background.

If queue and live GitHub disagree, do not blindly follow either:
reconcile the difference first, then update the queue only when the true state is known.

---

## 4. Strict Single-Flight Development

- Exactly one active Celine implementation strand may exist.
- While the queue/live GitHub show an active Celine branch/PR, do not create:
  - a second Celine branch,
  - a second Celine PR,
  - a parallel version block,
  - a competing queue step.
- Waiting for GitHub Actions is not permission to start the next version.
- Continue/fix the current active strand until it reaches its required completion state.
- Do not advance a branch head unnecessarily while a useful exact-head validation is still running and needed as evidence.
- Never silently abandon a failing active step and start a cleaner replacement branch.

---

## 5. Truth About Heads and Validation

Always distinguish these states:

- **current branch head** — newest commit on the active branch.
- **latest runtime-code head** — newest commit that changes executable/runtime behavior.
- **latest built runtime head** — runtime-code head with a successful required build.
- **latest visually accepted head** — exact runtime state whose required visual evidence was actually inspected and accepted.
- **documentation/queue-only head** — commit that changes docs, queue, instructions, metadata, or CI control without changing the already validated runtime behavior.

Rules:

- A documentation/queue-only commit does not magically become a newly runtime-validated head.
- Never report a docs-only head as having passed a runtime build/proof that actually ran on an older runtime head.
- Evidence must be bound to the SHA it actually tested.
- If a later docs/CI-only head contains the same runtime tree, preserve the distinction in the queue/handoff.
- A SUCCESS workflow conclusion is not visual acceptance.
- Visual tasks require human/agent inspection of the produced images/video/frames.
- Blank, black, off-screen, corrupt, stale, fallback, badly framed, or visibly wrong evidence is FAIL.

---

## 6. Canonical Celine Asset

Before touching or replacing Celine source geometry, rig, morphs, materials, or model inputs:

```text
READ ci/CELINE_SOURCE_ASSET.json
```

The source declared by that manifest is canonical.

Current repository structure includes the canonical Celine source under the model asset area, but agents must resolve the source through the manifest rather than guessing filenames.

Rules:

- Never silently swap in a test model, old GLB, fallback image, transient upload, locally remembered asset, or unrelated export.
- Large model files may be stored with Git LFS.
- A small text pointer in ordinary Git metadata is not the real binary model size.
- Preserve validated rig, morph, material, reference and runtime behavior unless the active work-order requires a controlled change.
- When changing a canonical asset, validate identity/hash/topology/rig expectations required by the active work-order.

Useful asset/reference entry points:

```text
ci/CELINE_SOURCE_ASSET.json
app/src/main/assets/models/CELINE_MESHY_RIG_AUDIT.txt
app/src/main/assets/models/README_CELINE_3D.txt
docs/celine/reference/v2/REFERENCE_MANIFEST.json
docs/celine/reference/v2/REFERENCE_RETRIEVAL.md
```

---

## 7. Technical Map — Orientation, Not Mandatory Reading

Use this as a map. Do **not** read every file on every task.

Core orientation:

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

Body/camera/presence examples:

```text
app/src/main/java/de/yahya/ai/CelineNaturalBodyMotionV73.java
app/src/main/java/de/yahya/ai/CelineArmHandPresenceV74.java
app/src/main/java/de/yahya/ai/CelineArmHandPresenceV79.java
app/src/main/java/de/yahya/ai/CelineSeatedCallV70.java
app/src/main/java/de/yahya/ai/CelineCameraZoomV70.java
app/src/main/java/de/yahya/ai/CelineProductInteractionLockV79.java
```

Voice/conversation examples:

```text
app/src/main/java/de/yahya/ai/SpeechAudioBus.java
app/src/main/java/de/yahya/ai/SpeechLipSyncV77.java
app/src/main/java/de/yahya/ai/ConversationIntelligenceV78.java
```

Read only the technical files relevant to the active work-order and the current defect/change.

---

## 8. Validation Strategy — Efficient but Safe

The canonical validation rules are in:

```text
ci/CELINE_VALIDATION_POLICY.md
```

General operating rule:

### Draft / iteration phase

- Use the smallest relevant check first.
- Runtime-code change: run the relevant compile/Android build.
- Avatar/geometry/rig/morph/material/camera/pose visual change: run targeted Avatar Lab or other relevant visual proof.
- Run keyboard/audio/video/lifecycle/multiview tests only when:
  - the changed surface can affect them, or
  - the active work-order explicitly requires them.
- Do not run the entire expensive suite after every small change.
- Fix compile errors, targeted contract failures, and task-specific defects before final gates.

Important entry points:

```text
.github/workflows/android-build.yml
.github/workflows/celine-avatar-lab-proof.yml
ci/avatar-lab-proof.sh
.github/workflows/celine-real-candidate-render-proof.yml
ci/celine-real-candidate-emulator-proof.sh
```

Specialized checks exist under `ci/`, including camera, rig-scale, skinning, facial/morph, keyboard, wake-word and other guards. Use them only when relevant.

### Final exact-head phase

When the active work-order is actually complete:

- move to final/Ready-for-Review state only when justified,
- run the complete exact-head gate set required by that work-order,
- ensure evidence is for the actual final head,
- if the final runtime head changes, rerun affected and required final gates.

### Main / release phase

After merging the exact validated head:

- validate the actual merge SHA on `main`,
- run required exact-main release gates,
- publish the APK only from the exact validated main state,
- verify/read back tag, target SHA, version, asset name, digest and required release wording,
- update the queue only after release/readback succeeds.

---

## 9. Permanent Work Chronology

Use this lifecycle:

```text
RECONCILE
→ DRAFT ITERATION
→ TARGETED VALIDATION / PROOF
→ WORK-ORDER ACCEPTANCE
→ FINAL EXACT-HEAD
→ MERGE EXACT VALIDATED HEAD
→ EXACT-MAIN VALIDATION
→ RELEASE / READBACK
→ QUEUE RECONCILIATION
→ NEXT WORK ORDER
```

Do not skip stages by assumption.

A new version/work-order may start only after the prior block reaches the completion state required by its work-order and queue policy.

---

## 10. Queue / Handoff Discipline

Before stopping, handing off, or declaring a meaningful step complete, make the queue understandable to a completely new agent with no chat history.

`ci/CELINE_PROGRESS_QUEUE.json` should accurately identify, as applicable:

- active branch
- active PR
- actual current branch head
- latest runtime-code head
- latest built runtime head
- relevant checks/run IDs and conclusions
- latest visual proof and PASS/FAIL
- artifact identifiers/digests when relevant
- current blocker or confirmed root cause
- exactly one clear `exact_next_action`

Never erase useful historical truth merely to make the queue shorter.

### Queue write safety

Before writing the queue:

1. Fresh-reconcile main, PR, head, workflows and release state.
2. Re-read the current queue from the actual branch.
3. Do not overwrite a newer queue version from a stale local blob.
4. Preserve the distinction between runtime validation and later docs/queue-only commits.
5. Do not claim visual acceptance without visual inspection.

---

## 11. Protected-Behavior Principle

Preserve previously validated behavior unless the active work-order requires a minimal, evidence-backed change.

Before modifying a mature subsystem, identify:
- which earlier behavior it protects,
- which targeted regression requires touching it,
- which focused validation proves the change did not break adjacent behavior.

Do not rewrite working infrastructure merely because a cleaner implementation is possible.

---

## 12. If the User Only Says “Continue Yahya AI”

Interpret a generic continuation request such as:

```text
Arbeite auf Yahya-AI weiter.
```

as:

1. Open/focus `Yahyachaos/Yahya-AI`.
2. Read this `AGENTS.md`.
3. Perform the Mandatory Start Chronology.
4. Fresh-reconcile GitHub and the queue.
5. Resume the one active strand at the true `exact_next_action`.
6. Do not create a new strand unless the current canonical state explicitly requires one.

No prior chat transcript is required if the repository handoff is healthy.

---

## 13. `START_HERE.txt`

`START_HERE.txt` is only a redirect for humans or agents that open it accidentally.

Do not use it as a development state document.

The canonical development entry remains this `AGENTS.md`.
