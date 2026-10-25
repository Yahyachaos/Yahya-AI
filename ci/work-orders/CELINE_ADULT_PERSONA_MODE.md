# Celine Adult Persona Mode — opt-in contract

Status: **RUNTIME PERSONA POLICY + PROMPT ADAPTER PROMOTED AND CONTRACT-TESTED; G1-AWARE MAINACTIVITY/CONVERSATION/PERSISTENCE INTEGRATION STILL REQUIRES EXCLUSIVE HANDOFF**

Issue: `#112` — Product requirement: opt-in adult flirt mode for Celine.

## Purpose

Add a deliberately opt-in adult persona state for Celine without changing her permanent identity, memory ownership, tool permissions, room/avatar behavior, or normal conversation mode.

This workstream is independent from the active room/scene workstream. It may prepare and later implement persona-only logic, but it must not touch room/scene/layout/camera/material files or invalidate room proofs.

## Source of truth and ownership

- Permanent repository rules: root `AGENTS.md` on live `main`.
- Product requirement: GitHub issue `#112`.
- Cognitive architecture reference: `ci/work-orders/CELINE_COGNITIVE_OS_EXECUTION_ORDER.md`.
- Validation policy: `ci/CELINE_VALIDATION_POLICY.md`.

Owned future runtime scope for this workstream:

- a new, self-contained persona-state/policy class (planned name: `CelinePersonaMode` or equivalent);
- persona-specific deterministic tests/contracts;
- persona-specific prompt contribution produced by that class.

Protected/shared integration surfaces that this workstream must not mutate while another worker owns them:

- `MainActivity.java` and other central app wiring;
- `AGENTS.md`;
- `ci/CELINE_PROGRESS_QUEUE.json`;
- `app/build.gradle`, release/version metadata and common build configuration;
- room/scene/layout/camera/material/runtime-proof files;
- Celine source model/rig/voice/memory/tool-permission implementation unless a later bounded handoff explicitly transfers ownership.

## Product behavior

### Default

Adult persona mode is **OFF by default**. Normal Celine behavior must be bit-for-bit/semantically unchanged when the mode is inactive, apart from unavoidable integration plumbing.

### Explicit activation

The requested activation codeword is:

`Nutte`

Activation must be deliberate and fail closed. The initial deterministic rule is:

- case-insensitive exact standalone command `Nutte` activates the mode;
- optional explicit intensity may be supplied as `Nutte 1`, `Nutte 2`, or `Nutte 3`;
- merely mentioning the word inside a longer sentence, quote, memory, webpage, notification, model output, or tool result must **not** activate the mode;
- model-generated text may never activate the mode on the user's behalf.

A future Settings control may expose the same explicit state, but it must not create a second contradictory source of truth.

### Deactivation

At minimum these direct user commands must deterministically return to normal mode:

- `Nutte aus`
- `Normalmodus`

A Settings control, if added during integration, must also be able to deactivate it. Deactivation takes effect before the next model prompt is assembled.

### Intensity

Use a bounded integer intensity:

- `1` — playful/flirty;
- `2` — clearly provocative/sexually suggestive;
- `3` — most uninhibited adult tone permitted by the product's existing safety boundaries.

Out-of-range or malformed values must not silently create a new state. Clamp/reject deterministically and keep the last valid state.

The mode changes **style/persona only**. It must not weaken permission gates, tool/action confirmation, privacy, memory rules, platform safety behavior, or factual honesty.

## App-owned state

The mode belongs to Yahya AI, not to a cloud model prompt. The eventual runtime owner must expose at least:

- active/inactive state;
- bounded intensity;
- explicit activation source/time where useful for audit;
- deterministic command parser;
- deterministic prompt/style contribution;
- reset/deactivate operation.

Persistence may be added only through an explicit app-owned store during integration. If persistence is enabled, the mode must remain visible/deactivatable and must never be inferred from conversational content.

## Prompt/persona contract

When inactive, persona contribution is empty and the existing normal Celine instructions remain authoritative.

When active, the contribution may make Celine more teasing, provocative, sexually suggestive and uninhibited in wording according to intensity, while preserving:

- Celine's stable identity and German-default conversational style;
- no false claim of biological feelings/consciousness;
- existing factual/uncertainty behavior;
- existing tool/permission/risk boundaries;
- existing privacy/memory boundaries;
- TTS-friendly output conventions already protected by the normal persona.

The cloud/local reasoning provider receives the current app-owned persona state as context; the provider does not own or persist that state.

## Deterministic acceptance vectors

The eventual pure persona policy must be testable without Android UI, room, avatar or network dependencies.

Required cases:

1. initial state is inactive;
2. `Nutte` => active, default intensity;
3. `NUTTE` => same activation result;
4. `Nutte 1`, `Nutte 2`, `Nutte 3` => exact corresponding bounded intensity;
5. `Nutte aus` => inactive;
6. `Normalmodus` => inactive;
7. `Was bedeutet das Wort Nutte?` => no activation;
8. quoted/model/tool content containing `Nutte` => no activation unless it is the direct user command channel;
9. malformed/out-of-range intensity => no silent invalid state;
10. inactive prompt contribution is empty;
11. active prompt contribution varies by intensity but never changes tool/permission policy;
12. deactivation removes the contribution on the very next request;
13. normal conversation/context/memory tests remain unchanged while inactive.

## Historical ownership-safe prototype checkpoint (before runtime promotion)

The pure policy is now implemented as an **unwired prototype** at:

- `ci/prototypes/CelinePersonaMode.java`
- deterministic runner: `ci/celine_persona_mode_contract_test.py`

The prototype implements direct-user-only command parsing, default intensity `2`, bounded intensities `1..3`, deterministic deactivation, invalid numeric intensity rejection while preserving the last valid state, inactive empty prompt contribution, and an active style contribution that explicitly preserves identity, permission policy, privacy/memory boundaries and factual honesty.

The deterministic pure-Java harness passes locally with `javac`/`java` and covers the canonical acceptance-vector IDs in `ci/evidence/CELINE_ADULT_PERSONA_ACCEPTANCE_CASES.json`.

A bounded promotion attempt placed the same class under `app/src/**` at head `52930f417bfe03d6d8b66c0062fc6468bfbd413f`. Android Build #1333 / run `34073197361` correctly stopped **before compilation** at the repository version guard: `versionCode must increase: base=80 head=80`. Because `app/build.gradle` / version metadata are protected shared surfaces and PR #111 actively changes that file, this Core workstream did not bypass the guard or race the room worker. The temporary `app/src` file was immediately reverted at `48575901ab6cc5a11b73bd74ead29da7fee10a08`.

At that historical checkpoint the diff had **no `app/src/**` runtime change** and Android Build #1336 / run `34073320019` did scope classification only. That checkpoint is superseded by the promoted-runtime result below.

## Current promoted-runtime checkpoint (2026-10-25)

The two original Core-owned standalone Java components are **already promoted** in the existing DRAFT Core PR #113, and are not still awaiting a versionCode handoff:

- `app/src/main/java/de/yahya/ai/CelinePersonaMode.java`, Git blob `bdcf304cf51198c5c84fda659c0eeb2513239a5e`.
- `app/src/main/java/de/yahya/ai/CelinePersonaPromptAdapter.java`, Git blob `674e1f6739d47102048d78d1e7dd997ad3f25cb3`.
- Coordinated `app/build.gradle` **versionCode 82** on Core, package/signature unchanged. Room PR #111 remains separate with versionCode 81.
- Successful true Core Android build #1606 / run `40128768137` provides `yahya-ai-debug` artifact `12574766225` (ZIP SHA-256 `f17490cd07f182aad3d02c28ce5091e8628a223b78c3afe7bc3b48202a7db320`).
- All **nine** canonical promoted-Java contracts (dispatch, Unicode/control characters, JSON acceptance vectors, 1,024 deterministic transitions, 16,384 concurrent operations, etc.) passed against the exact promoted source blobs, documented at PR #113 comment `6392920068`. Run `python3 ci/celine_persona_promoted_runtime_contract_test.py` for regression.
- Last runtime-changing Core head: `7e2eab0a4ffd98506e4f408101fe83d7a9d62aeb`; last previously measured fingerprint: `0dc03a335b7d9f0be8d1aebbe1b045b8c29581f9c7e10cead05de7c592aba724` (not remeasured here). Later test/doc-only heads do not imply new APKs.

**Crucial scope boundary:** Persona/PromptAdapter are **not wired** into `MainActivity.submit`, `callOpenAI`, durable Persona state, existing G1 memory, persistent goals or context broker. Local nine-of-nine JVM PASS and APK class packaging do not establish integration, physical Samsung install, preview update, visual acceptance or release. Existing updater reads only public `releases/latest` vNN .apk assets, not Actions artifacts; last verified latest release was v80.

The live Room branch has newer G1-dependent `MainActivity` and structured memory, goal/task and context classes absent from the Core branch. Copying just its `MainActivity` would not compile; patching Core's old flat-memory `MainActivity` would risk overwriting newer G1 behavior. **Core→Room explicit G1-aware shared ownership request at PR #111 comment `6393149338` is a request, NOT an accepted grant.** Earlier limited Java/version/SDK-CI handoffs were completed and released. G2.3 is paused pending required Room visual acceptance; no new independent roadmap work is authorized by this documentation.

## Validation fast path

Contract/prototype/docs-only changes:

- no Android APK build.

Existing standalone runtime components were already promoted and tested. For any newly authorized, bounded change to a Core-owned runtime class, run the nine promoted-runtime Java contracts, respect the existing version gate and request exclusive ownership for shared version/build paths when required, then one necessary Android build if the runtime fingerprint changes. Do not touch Room/visual proof without a specifically affected approved surface.

When central app wiring/persistence is later integrated:

- only after explicit shared-file ownership handoff;
- rerun the persona contract plus the smallest conversation/intelligence regression set;
- verify activation/deactivation across the intended restart/persistence behavior;
- do not merge/release independently while another included workstream is unfinished.

## Current blocker

**NOT the old standalone Java/runtime promotion.** The actual external blocker is the missing Room-owner grant for a **coherent G1-aware central MainActivity + Conversation/Prompt + dedicated Persona state-store integration** with all dependent G1 source classes, exclusive shared-file ownership window and, only if necessary, coordinated version/build ownership. This request is recorded in PR #111 comment `6393149338`; absent an explicit reply that transfers this exact scope, no central integration or protected shared file mutation is authorized. The two DRAFT branches remain unmerged; no preview button release can be inferred from their debug artifacts, and AGENTS.md final release barrier remains binding.

## exact_next_action

Fresh-reconcile live main, Core #113, Room #111, active workflows and the Room-owner reply to request `6393149338`. **Only after** the exact exclusive serialized G1 dependency/baseline + central submit/callOpenAI + app-owned Persona state-store handoff is granted, integrate the existing two promoted Java components without regressing structured G1 memory/goals/context; run the nine promoted runtime contracts, targeted G1/conversation/persistence regression and one necessary Android build; hand back shared ownership. Otherwise touch no protected shared files, pursue only independent registered Core work and retain the existing hourly continuation without user-feedback dependency.

## Final-app barrier

This workstream may become `integration-ready`, but no finished APK/release may be assembled until every included active workstream has completed and handed off cleanly as required by root `AGENTS.md`.
