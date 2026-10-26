# Celine Adult Persona Mode — opt-in contract

Status: **CORE-BRANCH PERSONA + G1-AWARE MAINACTIVITY/PROMPT/ENCRYPTED STATE INTEGRATED AND EMULATOR-TESTED; COMBINED ROOM+CORE APP INTEGRATION NOT DONE; RELEASE BLOCKED BY ROOM VISUAL FAIL AND ROOT AGENTS FINAL-GATE**

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

**Current 26 Oct Core-branch reality (supersedes the preceding historical promotion checkpoint):** `MainActivity.java` on **Core #113** (blob `11591afc5312bdeec026637883fded2355a6ad57`) now wires direct persona commands **before** tools/goals/learning, the G1-aware complete normal context plus persona prompt adapter in `callOpenAI`, AndroidKeyStore AES-GCM app-owned `CelinePersonaStateStore`, Settings-only OFF, and event-marked command/ACK history exclusion before `ConversationIntelligenceV78.selectContextStart` and provider `body.input`; visible chat remains visible. Last Core executable/built head `7500822f30b5f4fcbf43c1dd7fc54e85a051297b`, fingerprint `a51b95829c73501ce6ff1cbeeb7530d89755f989f3237d47db84d09d0c4f8782`, genuine Android #1668/run `40219218622` compiled v82 with live APK artifact `12608596107`. Exact Core #113 emulator #21/run `40247105318` / job `121330886485` **completed SUCCESS**, verified decoded log `ANDROID_PERSONA_TAMPER_G1_MEMORY_ISOLATION_PASS`, `ANDROID_KEYSTORE_TAMPER_EXPLICIT_RECOVERY_PASS`, `CORE_EMULATOR_SMOKE_PASS`, genuine proof artifact `12620086057` sha256 `01176f7fe20d3a762a595684228a66a9e38e4f1a2973276f4ebd8f456fb74662`. Older CI-only HEADs do not change this runtime fingerprint. **None of these results establish a COMBINED Room+Core APK, real Samsung install, full Room visual PASS or GitHub button update**; `releases/latest` was verified as v80 on 26 Oct. Provider actual network-response and physical device remain separate unproven properties.

**Current cross-branch integration boundary:** Room #111 still owns a **different** G1-aware `MainActivity.java` blob `da77b51263a52367f2b174af3561533540f659fa`, without the Core Persona additions. Core #113 now also includes the structured G1 classes, but the branches are **not combined** and overlapping `MainActivity`, `app/build.gradle` and common workflow must never be overwritten wholesale. Previous temporary Core MainActivity lease was CLEANLY RETURNED to Room in #111 comment `6401289986`. A new explicit exclusive serialized owner handoff, exact baseline selection and merge strategy are still required. Release-closure coordination request posted to Room #111 comment `6405848957`. The separate Room rebuild remains WHOLE-SCENE VISUAL FAIL and Cognitive `G2.3 PAUSED_UNTIL_EXACT_ROOM_REBUILD_COMPLETE`, so no final app merge/release is authorized.

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

**No independent Core-only runtime defect is established by the current full Core emulator/contract evidence.** The actual release blockers are (1) Core #113 and Room #111 still have divergent central app integration and protected build/version paths without a current exclusive serialized integrator handoff; (2) Room #111 remains whole-scene **VISUAL FAIL**, not integration-ready, with G2.3 PAUSED; (3) neither included workstream is cleanly combined/exact-main validated. `releases/latest` remains v80, not the actual Core v82 Android Actions artifact. Root `AGENTS.md` §10 release barrier applies; no unauthorized merge/release or misleading `fertig` claim.

## exact_next_action

**Reconcile the explicit Room-owner response to #111 comment `6405848957` and the current real Room visual verdict at the next hourly Core continuation; obtain the single named serialized integrator plus exact shared-file heads/lease, then perform the bounded G1-preserving Persona integration on the agreed owned branch and relevant combined compile/emulator checks.** Do **not** relaunch already passed Core #21 or invent extra persona microtests; do not edit Room or shared Java/version/CI paths before that grant, and do not start exact-main/final Release until Room's actual whole-scene visual acceptance and both workstreams' clean completion satisfy root `AGENTS.md`. Keep Core automation alive regardless of feedback.

## Final-app barrier

This workstream may become `integration-ready`, but no finished APK/release may be assembled until every included active workstream has completed and handed off cleanly as required by root `AGENTS.md`.
