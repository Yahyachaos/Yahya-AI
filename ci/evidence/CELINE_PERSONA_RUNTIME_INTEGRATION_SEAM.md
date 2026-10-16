# Celine persona runtime integration seam

Status: OWNERSHIP-SAFE PREPARATION COMPLETE; runtime write still blocked on explicit shared runtime/version ownership.

Issue #112. Core PR #113.

## Live runtime inspected

Read-only audit used Room PR #111 branch head `2b77571d2b0d32341d7c4c64154bf53ae8905aa7`. That head is docs-only. The Room recovery handoff records latest runtime-code head `4e93b4e1832bad2036ad41f94cc1b3d45c9b0ec9` and runtime fingerprint `cdf62a5f0bd05cd33eef7073eb60298bb927b478fa1d51d7b789d3455a2e832f`.

PR #113 changed files have zero path overlap with PR #111 changed files at this checkpoint.

## Current MainActivity anchors

On the live Room branch:
- `onCreate(...)` initializes SharedPreferences, structured memory, goal-task runtime, context broker, and device services.
- `handleLocalCommand(...)` owns deterministic local/device command routing.
- `submit(String text)` currently runs: add user turn -> local-command routing -> goal observation -> provider availability -> `callOpenAI(...)` -> `learnWithAI(...)`.
- `callOpenAI(...)` builds the stable normal instructions from the existing Celine identity prompt, context-broker output, and `ConversationIntelligenceV78.instructionSuffix(...)`.
- `learnWithAI(...)` is a separate inferred-memory extraction path.

## Bounded runtime seam after ownership transfer

1. Promote the already-tested `CelinePersonaMode` and `CelinePersonaPromptAdapter` into `app/src/main/java/de/yahya/ai/` without changing their tested semantics.
2. Add one app-owned persona-state instance to `MainActivity`; do not place persona state inside memory, tool, provider, or Room owners.
3. In `submit(...)`, evaluate the persona command parser before local/device command routing, goal observation, provider calls, and inferred-memory extraction.
4. If the input is a consumed persona-state command, update only persona state, acknowledge locally, restore idle UI state, and return. It must not become a device/tool action, goal observation, provider request, or inferred-memory extraction.
5. Non-persona user text keeps the existing submit flow unchanged.
6. In `callOpenAI(...)`, first build the entire existing normal instructions exactly as today; then pass that value through `CelinePersonaPromptAdapter.compose(normalInstructions, personaMode)` immediately before `body.put("instructions", ...)`.
7. Inactive mode must preserve the exact normal instructions object/content. Active mode may only append the already-tested persona contribution.
8. If persistence is included in the same ownership window, store only dedicated app-owned persona state and restore through `restorePersistedState(...)`; corrupt state must fail closed. Never infer persona state from conversation history, memories, tool output, model output, or Room state.
9. Deactivation must update app-owned state before any later request composes its prompt.

## Protected surfaces not needed for first promotion

No first-pass change is required in `ConversationIntelligenceV78`, `CelineStructuredMemory`, `CelineMemoryEngine`, `CelineProtectedMemoryStorage`, device/tool permission policy, Room/scene/layout/camera/material/light/window files, Celine model/rig/voice assets, or Room proofs.

## Validation after promotion

Run both existing persona contracts first. Verify consumed persona commands do not reach goal observation, device/tool routing, provider calls, or inferred-memory extraction. Verify normal text follows the unchanged path. Verify inactive prompt composition is unchanged and deactivation removes the contribution before the next request. If persistence is included, verify roundtrip and corrupt-state fail-closed behavior. Then run the smallest affected conversation regression, satisfy the coordinated higher versionCode gate, and run exactly one Android build. No Room visual proof is required for a persona-only batch.

## Blocker

The remaining runtime promotion, coordinated versionCode change, and central MainActivity/store wiring are shared integration surfaces. No explicit ownership transfer to Core exists while Room PR #111 is active.

## exact_next_action

Fresh-reconcile main, PR #111 and PR #113. Once shared runtime/version ownership is explicitly transferred, promote the two tested persona classes, apply only the bounded `submit(...)` and `callOpenAI(...)` seam above, satisfy the versionCode gate, run the two persona contracts plus the smallest affected conversation regression, then exactly one Android build.
