# Celine persona — protected runtime integration seam

Status: **Core persona policy/adapter are promoted and Android-compiled; central runtime integration is NOT implemented or authorized.** This is a planning/compatibility contract, not evidence of live Persona activation, persistence, or a finished APK.

## Read-only source reconciliation (2026-10-25, local 02:53 CEST)

These Git SHAs are **historical checkpoints only**. Fresh-reconcile live `main`, PR #111, PR #113, branch owners, Actions and release state before any write.

| Source | Checked identity |
|---|---|
| `main` | `5a23ccf75fb82b97d262cf3d8be24a6fe77fb26b` |
| Core PR #113 (DRAFT) | `9b061fc97020daf14d8fd3a7943da2e71dcd2aff` |
| Room PR #111 (DRAFT) | `b9ba9adb06db16b4c02957f6ddeddf5be2c16a2e` |
| Core `MainActivity.java` Git blob | `3595109b1eb957e39bfcb0d0885c08038def7e04` |
| Room `MainActivity.java` Git blob | `da77b51263a52367f2b174af3561533540f659fa` |
| Room `CelineStructuredMemory.java` | `14b11f6c14a45614c672190bcd384e431d2c6870` |
| Room `CelineContextBrokerG14.java` | `21212f6b3c6092569d703f4cd784bf34f483ab08` |

The branch `MainActivity.java` implementations **differ substantively**. Core's old file still reads flat `prefs.getString("memory", "")`, appends a flat memory blob and has no goal/context-broker field. Room's newer file instead constructs `CelineStructuredMemory`, `CelineGoalTaskRuntime`, and `CelineContextBrokerG14` in `onCreate`, routes goal observation through `submit`, and composes the normal instructions using structured `contextBroker.promptContext(...)`. A naive replacement of Room `MainActivity` with the Core version would silently regress G1 structured-memory/context/task behavior.

**No safe merge by whole-file copy:** Preserve the actually selected integrated `MainActivity` and surgically add Persona only after an explicit exclusive serial shared-ownership handoff. It is false that the branches have no shared-file overlap: `app/build.gradle` and `.github/workflows/android-build.yml` are changed on both PRs. MainActivity is also a shared *subsystem* even though the Core PR has not edited that file.

## What is genuinely already implemented

- `app/src/main/java/de/yahya/ai/CelinePersonaMode.java` is byte-identical to `ci/prototypes/CelinePersonaMode.java`; Git blob `bdcf304cf51198c5c84fda659c0eeb2513239a5e`.
- `app/src/main/java/de/yahya/ai/CelinePersonaPromptAdapter.java` is byte-identical to its prototype; Git blob `674e1f6739d47102048d78d1e7dd997ad3f25cb3`.
- Core has `versionCode 82`; Room has separate `versionCode 81`. The coordinated previous promotion handoff was completed and **released**; it grants **no ongoing shared ownership**.
- Android #1606 / run `40128768137` successfully compiled the two promoted classes and uploaded `yahya-ai-debug` artifact `12574766225`, verified runtime fingerprint `0dc03a335b7d9f0be8d1aebbe1b045b8c29581f9c7e10cead05de7c592aba724`. This is a Core-only build artifact, not an integrated application or an updater delivery.
- The promoted-runtime orchestrator `ci/celine_persona_promoted_runtime_contract_test.py` references seven canonical Java contracts. Its orchestration/fixture checks and separate 62-assertion Java smoke check have prior reported PASS; **the full seven canonical harnesses against the exact promoted Runtime have NOT yet been executed**. Do not turn #1610's scope-only CI SUCCESS into a Java contract PASS.

## Protected minimal integration plan — only AFTER separately recorded exclusive ownership

1. Reconcile current Room/main-derived conversation, memory, goal/context owner and the target app branch; preserve all newer behavior rather than applying the stale Core `MainActivity` as a replacement. Identify the actual central integration owner and version/CI owner first.
2. Initialize **one app-owned `CelinePersonaMode`** in the selected `MainActivity`/controller, outside `CelineStructuredMemory`, `CelineGoalTaskRuntime`, `CelineContextBrokerG14`, tool policy, Room and model response state.
3. In the selected `submit(String text)`, process `personaMode.apply(DIRECT_USER, text)` **before** `handleLocalCommand(text)`, its `rememberExplicit(...)` side effect, `goalTaskRuntime.observeUserText(text)`, provider calls and `learnWithAI`. A consumed persona command updates mode, acknowledges locally, restores UI idle/ready state and returns. It must not be interpreted as a tool/device command, a durable goal, a profile memory or a normal provider request. All other text continues through the existing, unchanged paths.
4. In the selected `callOpenAI(...)`, build the **complete currently integrated normal instructions** first (including G1 `contextBroker.promptContext(...)`, device state and `ConversationIntelligenceV78.instructionSuffix(...)` where present), then set `body.put("instructions", CelinePersonaPromptAdapter.compose(normalInstructions, personaMode))`. Never replace this with Core's older `prefs.getString("memory", "")` prompt path. Inactive mode must return the exact original String object/value; after deactivation the very next provider request must be normal.
5. If and only if persistence is in the authorized handoff, use a **dedicated app-owned Persona key/store** and tested `CelinePersonaStateCodec` / `restorePersistedState` seam. Do not persist activation provenance as inferred memory or infer activation from messages/model/tool output; invalid or future-schema payloads fail closed. This codec is currently a `ci/prototypes/**` component only and is **not** an integrated persistent Android store.
6. Preserve package identity, signing, version monotonicity and updater compatibility; coordinate any shared `app/build.gradle` or CI changes separately. Neither the existing Core v82 APK nor Room v81 APK may be represented as a combined product or as an in-app button update.

## Required focused acceptance after the handoff

- Run both base Persona contracts **and** all seven canonical promoted-runtime Java harnesses on the exact source; distinguish newly executed results from previously reported smoke/fixture PASS.
- Check exact standalone direct-user activation/deactivation and all existing Unicode/control/model/tool rejection vectors. Non-user content never changes state.
- Consumed persona commands must not reach `rememberExplicit`, device/tool execution, goal observation, `callOpenAI`, `learnWithAI` or normal user memory; normal non-persona turns must still reach the unchanged pipeline.
- Inactive prompt object/content, full Room G1 memory retrieval/context, explicit correction, goal-task observation and `ConversationIntelligenceV78` continuity must remain unchanged. On deactivation, the very next composed prompt must be normal.
- If persistence is authorized, validate restart roundtrip and corrupt-payload fail-closed behavior with separate dedicated state; do not claim this from pure Java codec checks alone.
- After a coherent, authorized runtime+version change, perform the smallest relevant conversation/memory regression and exactly one required Android build at its exact head/fingerprint. Treat a green compile as distinct from install/start/HOME/CALL and from real Persona/memory integration.
- Room G2.3 remains `PAUSED_UNTIL_EXACT_ROOM_REBUILD_COMPLETE`; this Persona work does not authorize bypassing G2.3, later G2/G3, final-main integration, merge, release or preview publication.

## Current blocker and exactly one next action

**Blocker:** no explicit currently valid, conflict-free serialized handoff covers central `MainActivity`/conversation prompt/Persona-state store and coordinated shared-version surfaces. Room's structured-memory context and goal pipeline must be retained. `releases/latest` remains v80, so Actions APKs cannot reach the existing in-app updater button.

`exact_next_action`: **Obtain and record the exclusive serialized central conversation/prompt/Persona-state ownership handoff against freshly read PR #111/#113 heads, then implement the bounded `submit` + `callOpenAI` wiring on top of the chosen up-to-date G1 memory/context/goal pipeline, run targeted contracts and exactly one warranted Android build; if the handoff remains absent, continue read-only reconciliation and independently authorized Core tests without stopping the hourly Core worker.**

Changed here: this Core-owned integration design document only. No runtime classes, app wiring, structured memory, Room, shared workflow/version, release/update channel, branch, PR or queue modified.
