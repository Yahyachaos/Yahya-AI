# Celine Adult Persona Mode — opt-in contract

Status: **PROTOTYPE READY — runtime promotion blocked until shared runtime/version ownership is explicitly handed off**

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

## Current ownership-safe prototype checkpoint

The pure policy is now implemented as an **unwired prototype** at:

- `ci/prototypes/CelinePersonaMode.java`
- deterministic runner: `ci/celine_persona_mode_contract_test.py`

The prototype implements direct-user-only command parsing, default intensity `2`, bounded intensities `1..3`, deterministic deactivation, invalid numeric intensity rejection while preserving the last valid state, inactive empty prompt contribution, and an active style contribution that explicitly preserves identity, permission policy, privacy/memory boundaries and factual honesty.

The deterministic pure-Java harness passes locally with `javac`/`java` and covers the canonical acceptance-vector IDs in `ci/evidence/CELINE_ADULT_PERSONA_ACCEPTANCE_CASES.json`.

A bounded promotion attempt placed the same class under `app/src/**` at head `52930f417bfe03d6d8b66c0062fc6468bfbd413f`. Android Build #1333 / run `34073197361` correctly stopped **before compilation** at the repository version guard: `versionCode must increase: base=80 head=80`. Because `app/build.gradle` / version metadata are protected shared surfaces and PR #111 actively changes that file, this Core workstream did not bypass the guard or race the room worker. The temporary `app/src` file was immediately reverted at `48575901ab6cc5a11b73bd74ead29da7fee10a08`.

Current final diff therefore has **no `app/src/**` runtime change**. Android Build #1336 / run `34073320019` completed SUCCESS for scope classification with build/emulator/publish correctly skipped.

## Validation fast path

Contract/prototype/docs-only changes:

- no Android APK build.

When the standalone runtime persona policy is promoted later:

- run `python3 ci/celine_persona_mode_contract_test.py` first;
- because `app/src/**` changes the runtime fingerprint, the same coordinated ownership window must also satisfy the repository's required versionCode policy;
- run exactly one required Android build for that coherent persona-policy batch;
- no room/avatar/video proof unless integration actually changes those surfaces.

When central app wiring/persistence is later integrated:

- only after explicit shared-file ownership handoff;
- rerun the persona contract plus the smallest conversation/intelligence regression set;
- verify activation/deactivation across the intended restart/persistence behavior;
- do not merge/release independently while another included workstream is unfinished.

## Current blocker

No product-design blocker exists. **Runtime promotion is blocked by shared ownership and the repository version gate.** Any `app/src/**` runtime change on this PR requires a higher `versionCode`, but `app/build.gradle` / version metadata are protected shared surfaces and are also changed by active room PR `#111`. Central `MainActivity`/Cognitive-OS wiring is likewise shared. This workstream must not race either surface.

The ownership-safe prototype deliberately advances and tests the policy without weakening CI or creating conflicting runtime evidence.

## exact_next_action

Fresh-reconcile live `main`, PR `#111`, active workflows and PR `#113`. Once an explicit shared runtime/version ownership handoff exists, promote the already-tested `ci/prototypes/CelinePersonaMode.java` into `app/src/main/java/de/yahya/ai/CelinePersonaMode.java`, satisfy the required coordinated versionCode policy without overwriting newer room/integration state, run `python3 ci/celine_persona_mode_contract_test.py`, then run exactly one Android build. Central `MainActivity`/persistence wiring remains a later serialized integration step unless that same handoff explicitly includes it.

## Final-app barrier

This workstream may become `integration-ready`, but no finished APK/release may be assembled until every included active workstream has completed and handed off cleanly as required by root `AGENTS.md`.
