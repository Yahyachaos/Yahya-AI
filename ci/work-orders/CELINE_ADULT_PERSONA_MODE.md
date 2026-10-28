# Celine Adult Persona Mode — opt-in contract

Status (28 Oct 2026): **CORE IMPLEMENTATION ACCEPTANCE MET — FROZEN, CLEAN INTEGRATION-READY; OFFICIAL v83 FUNCTIONAL INTERIM SCOPE IS ALLOWED TO EXCLUDE DOCUMENTED ROOM VISUAL DEFECTS (NOT A VISUAL PASS/G2.3 RELEASE); NO APPROVED EXACT-MAIN/SIGNER/IN-APP RELEASE YET.**

Issue: `#112` — Product requirement: opt-in adult flirt mode for Celine.

## 27 Oct 2026 decision — official functional interim release (live readback 28 Oct)

**Binding user product-scope correction, not an exemption from repository governance:** The user explicitly requested one **official, installable, compatible production Yahya-AI v83 (or a later vNN) functional interim release via the already installed `Settings > Update prüfen > Update herunterladen` flow**, even though the Room visual reconstruction remains visibly wrong. For this narrowly defined release, the already documented Room visual mismatches are **USER-ACCEPTED KNOWN ISSUES / excluded from the included functional scope**, not a visual PASS and not an authorization to resume G2.3. Older portions of this work-order saying full visual acceptance is an unconditional prerequisite to *this* interim release are superseded by this decision; full room fidelity remains a **later** workstream. If live `AGENTS.md` or Room work-order demands more, the sole Room owner must record the smallest transparent governance/scope reconciliation rather than silently waive a gate.

**What is actually proven (never infer more):** Core #113 remains DRAFT, tested executable/built HEAD `7500822f30b5f4fcbf43c1dd7fc54e85a051297b`, fingerprint `a51b95829c73501ce6ff1cbeeb7530d89755f989f3237d47db84d09d0c4f8782`, and [Core emulator #21](https://github.com/Yahyachaos/Yahya-AI/actions/runs/40247105318) terminal PASS. On 28 Oct, Core and Room each returned identical Git blob SHA-1 for the five checked Java files `CelinePersonaMode`, `CelinePersonaPromptAdapter`, `CelinePersonaStateStore`, `MainActivity`, and `ConversationIntelligenceV78`; this is **source parity**, not a merge into `main`. Separate **nondebug v83 production functional emulator #63** [run 40634902617](https://github.com/Yahyachaos/Yahya-AI/actions/runs/40634902617) is terminal SUCCESS on its previously recorded v83 runtime fingerprint `d7e6ef58941d1ebd05d89fe3f17c8a6c8d8cd6541db2dd9f9bfddcb554e3e07f`; do not relabel this as exact-main, visual, S25 Ultra, production signer custody, or in-app updater proof. The disposable-key Android API 26/27/28/32/33 signing test [#4](https://github.com/Yahyachaos/Yahya-AI/actions/runs/40665564888) is terminal SUCCESS **for synthetic keys only**.

**Current barriers and authorized owner:** Existing Room #111 is the **sole prospective serialized Core+Room integrator and publisher** after the included functional Room scope is **explicitly frozen, honestly COMPLETE/integration-ready and cleanly handed off in Room's own work-order/PR** under `AGENTS.md` §10/12; the excluded future visual perfection must be recorded separately. Historical production-signing private-key exposure remains a **hard release-security gate**; disposable signing tests do not establish safe key custody or old-install migration, especially Android 8 API 26/27. The exact `main` combined runtime build + required emulator/install checks, `de.yahya.ai`, numerically newer `versionCode > 82`, validated package/signature/key parity, APK and SHA-256, real GitHub `releases/latest` stable vNN asset and actual installed Settings-button download/install remain mandatory. Current readback 28 Oct: `main=5a23ccf75fb82b97d262cf3d8be24a6fe77fb26b`, Room DRAFT `8674bd6baf1699283c2bb164a4532fe107542446`, Core DRAFT `1382019f903d04c279b9a58b0d7bbea9ee92f6bd`; public `releases/latest` **v80**, not v83.

**Core ownership remains released:** No Core runtime or shared paths are to be written, no competing merger, Android build or release. This documentation-only clarification does not grant Core any Room/shared lease, mark Room functional scope COMPLETE, authorize an old exposed signer, or create another worker/PR/branch. All real build and security gates stay with the single authorized Room integrator. After official readback, resume only previously approved ongoing Core development through its owner scope and handoff rules.


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

## 26 Oct 2026 — clean Core integration-ready source handoff to existing Room owner

**Core #113 isolated implementation scope:** Core Persona policy, PromptAdapter, AndroidKeyStore AES-GCM app-owned store, direct-user parser, Settings OFF, active prompt composition, G1-aware provider-history exclusion and preserved normal UI bubbles are implemented and tested on Core's independent v82 branch. Acceptance cases **1–12** have the deterministic promoted-Java contracts and real Android emulator evidence. Case **13** (normal conversation/context/memory unchanged when OFF) has the existing real-source provider serialization + 8/8 G1 regression contracts and a separate real v80→v82 G1/persona/memory Android emulator smoke. This constitutes **integration-ready Core source**, NOT combined app acceptance, cloud-network provider success, S25 Ultra install, or final release approval.

**Immutable handoff anchors:** current Core implementation baseline / last runtime-code-and-built commit `7500822f30b5f4fcbf43c1dd7fc54e85a051297b`; unchanged runtime fingerprint `a51b95829c73501ce6ff1cbeeb7530d89755f989f3237d47db84d09d0c4f8782`; actual Android #1668/run `40219218622` with live artifact `12608596107` and ZIP sha256 `8a7d1a2bff9d3f2116d7f29ff5e5aba8d770cadc410bd2ce982a6868ecd66254`. Runtime Java blobs: `CelinePersonaMode.java`=`bdcf304cf51198c5c84fda659c0eeb2513239a5e`; `CelinePersonaPromptAdapter.java`=`674e1f6739d47102048d78d1e7dd997ad3f25cb3`; `CelinePersonaStateStore.java`=`007787f8c03d92d8e4e5632c038c9337051f9260`; Core `MainActivity.java`=`11591afc5312bdeec026637883fded2355a6ad57`. Exact-head Android emulator #21/run `40247105318` job `121330886485` succeeded; real artifact `12620086057` sha256 `01176f7fe20d3a762a595684228a66a9e38e4f1a2973276f4ebd8f456fb74662`. These artifacts belong to the **Core v82** fingerprint; do not reuse for a combined runtime with a different fingerprint.

**Sole serialized integrator decision:** Existing Room #111 owner explicitly accepted the role of **one prospective Room+G1+Persona integrator/publisher** in Room comment `6406163871` after prior Core request `6405848957`. **No MainActivity/Gradle/workflow/Queue/release lease was granted to Core.** Room's different G1-aware MainActivity blob `da77b51263a52367f2b174af3561533540f659fa` must be integrated surgically, not replaced wholesale. Core's tested three dedicated Persona Java classes and the bounded MainActivity behavior are now offered as immutable, clean integration inputs to that one named Room integrator, **effective only at the future exclusive serialized integration checkpoint after Room visual acceptance and clean worker completion**. Core releases its implementation edit lease over those inputs for that integration and will not mutate or start concurrent Android builds on them without a new explicit non-overlapping grant. This is **not** permission to merge, release or edit another worker's live files now.

**Hard product gate:** Room #111 DRAFT at last owner response `08cac93ea47401c9acb434b40a51fd01cb84b77b`; whole-scene VISUAL FAIL against `Refernzbild.png`, `G2.3 PAUSED_UNTIL_EXACT_ROOM_REBUILD_COMPLETE`. Room Android #1697/run `40253417789` built its **separate** fingerprint `d1dc829b81e30910bc2760c01e0d6f383697b4858645b06a79a757f1af4a40f5`, not a Core-integrated APK; whole-room visual proof for that runtime still pending at last read. Root `AGENTS.md` §§10–12 bars a finished release until Room accepted, both workstreams clean and combined exact-main validation complete. Existing updater's last verified `releases/latest` was v80 only; no button update exists.

## Current blocker

**No new independent Core-only runtime defect is established.** The user's 27 Oct decision excludes documented Room visual defects **only for the scoped official functional interim version**; WHOLE ROOM VISUAL FAIL and G2.3 PAUSED remain true. Remaining release blockers: (1) Room owner has not yet verified and recorded the explicit included **functional Room freeze / COMPLETE integration-ready** handoff and serialized main ownership for this scoped version; (2) the exposed historical production signing key still lacks approved custody/migration policy; (3) combined exact-main final validation, secure signed APK, stable asset and actual Settings-button install readback are absent. Prior v83 functional PASS is real but narrower. Live `releases/latest` remains v80. All `AGENTS.md` §§10–12 gates still apply.

## exact_next_action

**At the next existing Core continuation:** read-only reconcile live Core/Room/main heads, five protected Core/Room source blobs, the Room owner's **explicit functional-interim scope freeze and serialized Shared-file handoff**, production-signing security decision, and published updater state. If no authorized exclusive Room integration handoff exists, retain the proven frozen Core source and do not create further status-only PR comments, duplicate tests, shared edits, merges or releases. The one action for the sole Room owner remains to document the actually complete included Room **functional** scope/clean handoff and keep unaccepted visuals excluded for this version, before any authorized secure signing/exact-main publication; do not demand perfect Room visuals as a precondition for this specifically authorized interim version.


## Final-app barrier

This workstream may become `integration-ready`, but no finished APK/release may be assembled until every included active workstream has completed and handed off cleanly as required by root `AGENTS.md`.
