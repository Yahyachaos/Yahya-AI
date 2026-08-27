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

1. Read this `AGENTS.md` completely.
2. Read `ci/CELINE_PROGRESS_QUEUE.json` completely.
3. From `active`, identify at minimum the active branch, PR, work-order, status, latest runtime-code head, last built runtime head, latest visual proof and PASS/FAIL, blocker/root cause, and `exact_next_action`.
4. Read the active work-order specified by the queue completely.
5. Read `ci/CELINE_VALIDATION_POLICY.md` completely.
6. Read `ci/CELINE_SOURCE_ASSET.json` before any Celine asset/rig/model work.
7. Fresh-reconcile live GitHub: actual `main`, PR/draft state, actual branch head, commits newer than the queue checkpoint, reviews/comments when relevant, checks/workflow runs, relevant artifacts/evidence, and merge/release state when applicable.
8. Determine whether another agent or useful workflow is already executing the same Celine step.
9. Reconcile differences between Queue and Live GitHub before writing.
10. Only then perform exactly the next necessary bounded action.

Never begin implementation merely from remembered state.

## 3. State precedence

When information conflicts, use this precedence:

1. Live GitHub facts for repository/PR/branch/run/release state.
2. This `AGENTS.md` for permanent operating rules.
3. `ci/CELINE_PROGRESS_QUEUE.json` for the intended active strand and handoff.
4. The active work-order for task-specific requirements and acceptance criteria.
5. `ci/CELINE_VALIDATION_POLICY.md` for validation scope and phase rules.
6. Canonical manifests/reference documents for protected inputs.
7. Historical README/docs only as background.

If Queue and Live GitHub disagree, reconcile first. Never overwrite newer live state with an older queue snapshot.

## 4. Strict single-flight

- Exactly one active Celine implementation strand may exist.
- While Queue/Live GitHub show an active Celine branch/PR, never create a second Celine branch, PR, parallel version block, or competing queue step.
- Waiting for GitHub Actions is not permission to start the next version.
- Do not advance a branch head unnecessarily while a useful exact-head validation is still running and needed as evidence.
- Never silently abandon a failing active step and start a cleaner replacement branch.

## 5. Truth about heads, fingerprints, builds, and visual evidence

Always distinguish:

- **current branch head** — newest commit on the active branch.
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
→ one Android build.

Visual runtime surface affected?
→ exactly one targeted Avatar Lab proof after the relevant build/reuse decision.

Proof fails?
→ change only the confirmed cause, then repeat the smallest relevant check.

Work-order acceptance complete?
→ only then move to final exact-head gates.
```

Operational loop:

```text
RECONCILE
→ CLASSIFY CHANGE
→ ONE BOUNDED CHANGE
→ SMALLEST REQUIRED BUILD/CHECK
→ ONE TARGETED PROOF IF NEEDED
→ INSPECT ACTUAL EVIDENCE
→ RECORD TRUTH
→ NEXT BOUNDED CHANGE
```

Do not stack speculative runtime fixes before inspecting evidence from the previous one.

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
- avatar/geometry/rig/morph/material/camera/pose visual changes get the relevant Avatar Lab proof;
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
- move to Ready for Review only when the active work-order is actually complete;
- run the complete exact-head gates required by the work-order;
- final transitions deliberately rebuild/revalidate even if an older runtime fingerprint exists when policy requires exact-head evidence.

Main/release:
- validate the actual merge SHA on main;
- publish only from exact validated main;
- read back tag, target SHA, version, asset name, digest, and release wording;
- reconcile Queue only after readback succeeds.

## 11. Permanent work chronology

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

## 12. Queue / handoff discipline

Before stopping or handing off, `ci/CELINE_PROGRESS_QUEUE.json` must let a completely new agent continue without chat history.

Record as applicable:
- active branch/PR;
- actual current branch head;
- runtime fingerprint;
- latest runtime-code head;
- latest built runtime head;
- relevant checks/run IDs and conclusions;
- latest visual proof and PASS/FAIL;
- artifact identifiers/digests;
- current blocker/root cause;
- exactly one clear `exact_next_action`.

Before a queue write: fresh-reconcile main/PR/workflows/release, re-read the actual queue, do not overwrite a newer queue blob, preserve docs-only vs runtime distinctions, and never claim visual acceptance without inspecting evidence.

## 13. Protected behavior principle

Preserve previously validated behavior unless the active work-order requires a minimal evidence-backed change. Identify what an older subsystem protects, why the current regression requires touching it, and which focused check proves adjacent behavior remains intact. Do not rewrite working infrastructure merely because a cleaner implementation is possible.

## 14. If the user only says “Continue Yahya AI”

Interpret a generic continuation request as:

1. Open/focus `Yahyachaos/Yahya-AI`.
2. Read this `AGENTS.md`.
3. Execute the Mandatory Start Chronology.
4. Fresh-reconcile GitHub and Queue.
5. Resume the one active strand at the true `exact_next_action` using the Efficiency Fast Path.
6. Do not create a new strand unless the canonical state explicitly requires one.

No prior chat transcript is required if the repository handoff is healthy.

## 15. START_HERE.txt

`START_HERE.txt` is only a redirect for humans or agents that open it accidentally. Do not use it as a development-state document. The canonical development entry is this `AGENTS.md`.
