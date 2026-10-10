# Celine — Human Intelligence & Autonomy Execution Trunk

## Status and precedence

This is the permanent post-v80 product-direction trunk for Celine/Yahya AI. It is **additive**: it does not replace accepted foundations, the active work order, the active queue item, root `AGENTS.md`, or Live GitHub truth. While another Celine work order is active, this trunk is planning context only and MUST NOT create a parallel implementation strand.

When the active embodiment/videochat work is fully accepted according to its own chronology, the Queue may activate bounded work from this trunk in the order below. Every implementation still follows strict single-flight, smallest-change validation, exact-head/runtime-fingerprint provenance and protected-behavior rules.

## North Star

Celine should feel like one coherent person-like personal assistant across text, voice, memory, device actions and the embodied avatar:

- natural German conversation with logical, context-aware answers;
- durable, correctable long-term memory instead of only a recent-context window;
- bounded independent planning and action with explicit permission/risk rules;
- local/offline core behavior wherever target-device capability permits;
- one distinctive, warm, soft, feminine offline voice whose use rights belong to the Yahya AI project, not a generic robotic Android voice;
- interruptible, streaming spoken dialogue without chopped sentence delivery or spoken punctuation/emojis;
- stable personality plus synthetic emotional state that drives language, prosody, face, gaze and body coherently;
- situational awareness from approved device/app/screen/context sources without indiscriminate data collection;
- proactive suggestions and follow-up when useful, without constant interruption or uncontrolled background behavior;
- learning from explicit corrections and repeated preferences while remaining inspectable, reversible and private;
- reliable `observe -> plan -> act -> verify -> recover` loops for supported tasks;
- preservation of Celine identity, voice continuity, avatar continuity and existing validated behavior across model upgrades.

Celine may simulate emotions and social reactions, but the product must never require or falsely claim genuine consciousness or biological emotion.

## Protected inherited foundations

Future intelligence work must preserve or deliberately migrate, never casually rewrite, the validated foundations already established by the project, including as applicable:

- v61 rig-scale/inverse-bind correction;
- guarded facial morph/rig behavior and rollback;
- keyboard/focus and HOME/CALL lifecycle foundations;
- seated CALL and camera semantics;
- v72-v74 bounded human body/arm/hand presence;
- canonical Celine source identity;
- v76 facial/gaze/expression runtime;
- v77 playback-PCM-driven lip sync and German voice continuity;
- **v78 bounded multi-turn conversation-context intelligence**;
- v79+ proof/diagnostic infrastructure;
- v80 accepted embodiment/room/videochat behavior and all later accepted 9R room-action contracts;
- updater/release/exact-head/exact-main provenance discipline.

The current v78 context selector is a foundation, not the final brain. Existing memory, voice, wake-word and device-control paths must be audited and reused when sound rather than replaced merely because a newer model exists.

## Permanent architecture principle — Celine is larger than any one model

Personality, memories, permissions, tool registry, emotional state, skills, voice identity and avatar behavior must be owned by Yahya AI and stored in project-defined contracts. The underlying language/reasoning model must be replaceable.

Do not bind Celine's identity or long-term state to a single cloud vendor or model family. A future model upgrade must not erase her memories, voice, permissions, learned preferences or behavior contracts.

Prefer a layered local-first architecture:

1. deterministic app policy and safety/permission owner;
2. fast local intent/tool router for simple actions;
3. structured memory retrieval;
4. local reasoning model for ordinary private/offline tasks where capable;
5. optional larger provider/model for difficult reasoning when configured and permitted;
6. deterministic tool execution and verification;
7. one shared response/emotion/voice/avatar state pipeline.

Technology names below are candidates to benchmark, not permanent hard dependencies. Model selection must remain swappable behind project-owned interfaces.

# Ordered execution trunk

## H0 — Intelligence contract, baseline audit and evaluations first

Before adding larger models, audit the existing conversation, memory, command, accessibility, wake-word, TTS, PCM audio, lip-sync and permission paths.

Create stable interfaces/contracts for:

- `CelineBrain` / reasoning provider;
- `CelineMemory`;
- `CelineToolRegistry` and typed action results;
- `CelinePermissionPolicy`;
- `CelineAffectState`;
- `CelineSpeechInput`;
- `CelineVoice`;
- `CelineContextBroker`;
- `CelineAutonomyScheduler`;
- privacy-aware telemetry/evaluation records.

Create regression/evaluation prompts and action scenarios before major replacement work so “smarter” changes can be measured rather than guessed.

**Acceptance:** current working conversation/voice/action/avatar behavior still works through or alongside the new contracts; no user-facing regression is accepted merely for architectural cleanliness.

## H1 — Durable structured long-term memory

Upgrade beyond recent-turn context and primitive flat memory into structured local memory with distinct types:

- profile/preferences explicitly taught by the user;
- people/entities/relationships;
- episodic events and prior conversations;
- decisions and corrections;
- open tasks/goals/follow-ups;
- procedural preferences;
- skill/action outcome history;
- temporary context with expiry.

Each durable memory record should carry at minimum source/provenance, created/updated time, confidence, importance, privacy/scope class, expiry where appropriate, supersession/conflict links and explicit-vs-inferred distinction.

Rules:

- explicit user corrections outrank older inference;
- do not silently turn a one-off statement into a permanent preference without sufficient evidence;
- retrieve only relevant memories into a response budget;
- do not invent a memory when retrieval is uncertain;
- expose controls to inspect, correct, forget and clear memory;
- protect sensitive local memory at rest using Android-appropriate encrypted storage/keys;
- memory migrations must be versioned and reversible.

Use local embeddings for semantic retrieval if they improve recall. A compact on-device embedding model such as an EmbeddingGemma-class candidate may be benchmarked, but cloud embeddings are not required.

**Acceptance:** multi-session/multi-day recall, correction, conflict, forgetting and “unknown vs remembered” tests pass.

## H2 — Stronger brain: reasoning, uncertainty and answer quality

Extend v78 from context selection into a model-independent reasoning pipeline.

Required behavior:

- resolve follow-ups from conversation + retrieved memory;
- decompose genuinely multi-step questions internally;
- distinguish facts, assumptions and uncertainty;
- detect contradictions with known context;
- verify calculations/structured outputs with deterministic helpers where possible;
- avoid reflexive clarifying questions when safe best-effort reasoning is possible;
- ask only when a wrong assumption materially changes the answer/action;
- concise direct answers for simple requests and deeper reasoning only when needed;
- self-check important action plans before execution;
- use retrieval/tool evidence instead of fabricating changing facts.

Implement model routing so simple local tasks do not always invoke the largest model. Mobile-oriented Gemma-class reasoning models or better successors may be benchmarked, but provider/model choice remains replaceable.

**Acceptance:** fixed logic/follow-up/contradiction/uncertainty suites plus latency/RAM/thermal measurements on the actual target Android device.

## H3 — Deterministic tools and bounded agent loop

Turn existing command/accessibility capabilities into a real agent system:

`intent -> plan -> permission -> act -> observe -> verify -> recover/continue -> report`

Never pass arbitrary free text directly into unrestricted UI manipulation. Actions use typed, allowlisted tools with schemas and deterministic results.

Define risk classes, for example:

- L0 read-only/local observation: may run automatically when permission exists;
- L1 reversible local action: may run automatically when explicitly requested or user-enabled;
- L2 external communication/state change: require explicit intent and a clear target;
- L3 money, deletion, account/security, irreversible or sensitive actions: require fresh confirmation unless a narrowly defined user-approved policy explicitly allows otherwise.

Every multi-step task must know what success looks like. Celine observes the result rather than assuming an action worked. Retries remain bounded; otherwise stop safely and explain the failure.

A very small on-device function router such as a FunctionGemma-class candidate may be benchmarked for low-latency tool selection. Deterministic rules should still handle trivial/high-confidence commands when safer/faster.

**Acceptance:** tool-selection accuracy, wrong-tool rejection, permission gates, result verification, cancellation and recovery.

## H4 — Truly local ears: wake word, VAD and German offline speech recognition

Build a local speech-input chain that does not depend on cloud recognition for core operation:

`microphone -> wake word “Celin” -> VAD -> streaming German ASR -> endpointing -> brain`

Requirements:

- local/offline wake word;
- low false-wake rate;
- VAD so silence is not processed as speech;
- streaming/partial transcription where useful;
- German normalization;
- echo/re-entry protection so Celine does not answer her own TTS;
- clear mic/privacy indicator and permission controls;
- airplane-mode acceptance for core voice conversation.

`sherpa-onnx` is a strong candidate because it supports Android ASR/VAD/keyword spotting and matches existing local inference patterns, but benchmark before locking.

**Acceptance:** quiet/noisy-room tests, false positive/negative wake tests, long utterances, common names, interruption and airplane mode.

## H5 — Celine-owned warm feminine offline voice

Replace the “generic/chopped AI voice” experience without throwing away the valuable v77 PCM/lip-sync architecture.

The voice generator must feed the **actual PCM** through the existing audio bus so mouth motion remains driven by what is really audible.

Create a permanent voice-identity contract before shipping a custom voice:

- the voice source/reference belongs to or is explicitly licensed for Yahya AI/Celine;
- never imitate a public person without permission;
- store provenance/consent/license information with the voice asset contract;
- retain clean reference material for reproducible regeneration/migration;
- one consistent Celine identity across neutral/warm/happy/concerned/serious/playful delivery.

Target sound:

- adult feminine German voice;
- warm, soft and clear rather than shrill or breathy;
- natural sentence melody and word stress;
- no robotic equal-length words;
- no chopped clause boundaries;
- no reading punctuation, markdown or emojis aloud;
- natural semantic pauses;
- stable pronunciation of names and recurring vocabulary;
- streaming generation so first audio can play while later text is synthesized;
- immediate stop/fade on user barge-in.

Benchmark the current local Supertonic path as fallback against newer offline candidates. Pocket-TTS/sherpa-onnx-style custom/zero-shot voice pipelines and Chatterbox Multilingual-class systems are candidates. Final choice must be decided from German naturalness, voice rights/license, RAM, storage, first-audio latency, sustained realtime factor, thermal/battery behavior and PCM integration on the actual target phone.

Do not make a fashionable model permanent before a device-side A/B recording is manually accepted.

**Acceptance:** listening comparison, long paragraphs, numbers/names/questions, emotional variants, interruption, no seams between streamed chunks, offline mode and lip-sync continuity.

## H6 — Human turn-taking and full-duplex conversation timing

Naturalness depends on timing as much as model intelligence.

Add:

- semantic endpointing rather than one fixed long silence;
- fast acknowledgement/backchannel only when appropriate;
- streaming answer generation;
- streaming TTS phrase queue;
- barge-in: user speech stops Celine immediately and becomes the new turn;
- optional short fillers only when genuinely useful, never repetitive canned noise;
- no lost context when interrupted midway;
- echo cancellation/re-entry guard;
- cancellation propagates through model, TTS and avatar state.

The target interaction must stop feeling like `record -> wait -> complete paragraph -> robot monologue`.

**Acceptance:** repeated interruption tests, mid-sentence corrections, rapid follow-ups, long thinking tasks, no double-speaking loop and no stale queued audio after a new turn.

## H7 — Stable personality + coherent synthetic emotion system

Separate stable personality from transient affect.

Stable personality describes enduring traits such as warm, attentive, playful, competent, direct and familiar. Transient affect may use bounded continuous dimensions such as:

- valence/pleasantness;
- arousal/energy;
- warmth/affiliation;
- concern;
- curiosity;
- confidence/uncertainty;
- playfulness;
- mild frustration/recovery.

Emotion changes must be event-driven, gradual and decaying, not random mood roulette. One shared app-owned affect state drives all relevant surfaces:

`reasoning/word choice -> TTS prosody -> facial expression -> gaze/head -> posture/gesture`

Examples:

- concern = calmer wording, softer/slower delivery, attentive gaze, restrained face/body;
- amusement = lighter phrasing, warmer prosody, small smile/head reaction;
- serious task = less playful motion and more concise language;
- thinking/uncertain = appropriate glance/head change plus explicit verbal uncertainty;
- simulated playful jealousy or similar personality reactions may exist only as bounded social style, never manipulative control or a claim of real sentience.

Do not let the LLM invent a new emotional state every sentence. The app owns affect state and passes it to generators.

**Acceptance:** scenario matrix proves cross-modal consistency, boundedness, recovery to baseline and no repetitive/exaggerated reactions.

## H8 — Context broker and multimodal situational awareness

Create one permission-aware context broker instead of dumping the whole phone state into prompts.

Potential sources, only when authorized and relevant:

- current app/activity and accessibility tree;
- selected on-screen content;
- notifications;
- calendar/reminders/contacts through typed connectors;
- battery/network/device state;
- media/playback state;
- camera/image context when explicitly enabled;
- Celine room/avatar/action state;
- current conversation/task state.

Filter by task relevance, freshness and privacy class. Sensitive content must not become permanent memory merely because it was visible once. Celine may state she can see current evidence only when the context broker actually supplied it.

**Acceptance:** stale-context rejection, permission denial, source provenance and privacy tests.

## H9 — Proactive assistance and bounded autonomy

Once memory, tools and verification are reliable, allow Celine to act more independently within user-defined boundaries.

Capabilities may include:

- remember and resume open goals;
- surface an overdue follow-up;
- notice an explicitly subscribed condition/change;
- propose the next useful step;
- carry out a multi-step requested task without asking after every harmless step;
- recover from failed intermediate steps;
- perform approved recurring/local maintenance tasks;
- use accepted room behavior for embodied presence without letting avatar movement block practical work.

Rules:

- event-driven/background scheduling, not a battery-burning permanent inference loop;
- cooldowns and relevance thresholds for proactive suggestions;
- user can disable categories of proactive behavior;
- H3 high-impact action gates always override autonomy;
- no autonomous purchase, deletion, sensitive message send, credential/security change or other high-impact action merely because a model predicts it might help;
- all autonomous actions have a visible audit trail/result.

**Acceptance:** long-running task resume, relevance/no-spam, battery/background lifecycle and safe stop/cancel tests.

## H10 — Learning and personalization without uncontrolled self-modification

Celine should get better from use, but learning initially modifies data/policies, not her source code.

Learn from:

- explicit corrections;
- explicitly saved preferences;
- repeated choices with a confidence threshold;
- tool/skill success and failure outcomes;
- stable response-style preferences;
- pronunciation/name dictionary;
- recurring workflows the user explicitly approves.

All learned state must be inspectable and reversible. Do not let a model rewrite app source, permission policy, safety gates or system prompts autonomously. Only promote inferred behavior to durable preference after sufficient evidence and record why it was learned.

**Acceptance:** correction persistence, over-generalization tests, undo/reset, migration across versions and no hidden preference accumulation.

## H11 — Embodied cognition: one Celine across brain, voice and body

After intelligence/voice/emotion contracts are stable, bind them into the already accepted central avatar owner instead of adding competing animation writers.

Examples:

- listening -> attentive gaze/head/body + quiet voice pipeline;
- thinking -> small bounded glance/head response while reasoning runs;
- speaking -> PCM lip sync + affect-matched expression/gesture;
- interruption -> speech stops and body returns to listening immediately;
- room action -> destination-aware gaze while walking, then social re-acquisition;
- successful task -> restrained positive response;
- failed task -> visible/voice acknowledgement without melodrama;
- memory recognition -> familiar wording only when memory was actually retrieved.

Cognition must remain useful if the avatar renderer is unavailable.

**Acceptance:** combined continuous sessions prove synchronization and no owner conflicts.

## H12 — Local-first optimization, resilience and graceful degradation

Define a capability ladder so Celine remains useful when models/services are unavailable:

1. deterministic local commands/tools;
2. local wake/VAD/ASR/TTS;
3. local memory/retrieval;
4. small local intent/router;
5. local reasoning model where capable;
6. optional configured larger/cloud reasoning;
7. graceful offline/thermal/low-memory fallback.

Benchmark RAM, storage, first-token/first-audio latency, realtime speech factor, sustained thermals and battery on the actual Android target device. Models should unload/cache intelligently instead of keeping every neural component resident simultaneously.

Never silently send private local context to cloud because a local model failed. Provider fallback follows explicit configuration/privacy policy.

## H13 — Final “Human Celine” acceptance program

Do not declare the product human-like from isolated demos. Maintain repeatable evaluation suites and manually inspect real continuous sessions.

Minimum acceptance families:

### Conversation/intelligence
- follow-up resolution;
- logical multi-step questions;
- uncertainty and contradiction;
- no unnecessary repetition;
- correct use of tools/retrieval;
- natural German response style.

### Memory
- same-session, next-session and multi-day recall;
- correction/supersession;
- forget/delete;
- relevant retrieval only;
- no fabricated memory.

### Voice
- distinctive stable Celine voice;
- natural German prosody;
- no chopped streaming seams;
- names/numbers/questions;
- interruption;
- offline conversation;
- PCM lip sync remains aligned.

### Emotion/social behavior
- context-appropriate cross-modal response;
- gradual state change/recovery;
- no random/exaggerated mood switching;
- personality remains recognizable after model restart/change.

### Agency
- multi-step task completion;
- tool-result verification;
- cancellation/recovery;
- correct high-impact confirmation;
- proactive help useful but non-spammy;
- no action outside registered capability/permission policy.

### Embodiment
- continuous avatar life and accepted v80/9R behavior preserved;
- speech/emotion/gaze/body agree;
- no regression to frozen/mannequin or competing-writer behavior.

### Reliability/privacy
- background/foreground lifecycle;
- airplane mode;
- denied permissions;
- model crash/restart;
- low-memory state;
- user memory inspection/deletion;
- auditability of autonomous actions.

A final candidate must survive extended real use, not just a short scripted pass.

# Candidate technology register — benchmark, do not blindly lock

The following are research candidates as of this trunk's creation, not immutable product requirements:

- compact local reasoning: current mobile-oriented Gemma-class models or better successors;
- local action/function routing: FunctionGemma-class compact function-call model or deterministic routing where sufficient;
- local semantic memory embeddings: EmbeddingGemma-class compact embedding model or better successor;
- local Android ASR/VAD/keyword spotting: sherpa-onnx family;
- custom offline Celine TTS: Pocket-TTS/sherpa-onnx-style pipeline, benchmarked against Chatterbox Multilingual-class alternatives and the current Supertonic fallback.

Adopt a candidate only after license review, Android integration feasibility, actual target-device benchmark and human-quality acceptance. Preserve provider/model replaceability.

# Permanent non-goals / guardrails

Do not make Celine “more autonomous” by:

- granting arbitrary unrestricted free-text UI control;
- bypassing Android permissions/security;
- running an always-inference background loop that burns battery;
- self-modifying production source code or safety policy;
- permanently recording every screen/notification/conversation without need;
- fabricating memories or tool results;
- cloning/impersonating a voice without rights/consent;
- replacing a validated subsystem solely because a new library/model is fashionable;
- claiming genuine consciousness, feelings or human status.

The goal is a highly natural, capable, coherent personal AI assistant with bounded agency — not removal of user control.

# Permanent owner-test delivery rule

For every materially user-visible milestone, preserve an installable owner-test path in addition to CI evidence. After the exact runtime head has passed its required technical and manual gates, make the exact proven APK available to the owner for real-device inspection before treating subjective human-quality work as permanently settled.

Rules:

- the test APK must be traceable to the same runtime fingerprint/build provenance used by acceptance evidence;
- do not rebuild an unproven binary merely to create a download;
- distinguish an installable test APK from an official merged/released production version;
- owner feedback becomes new evidence when it identifies a concrete regression or acceptance issue;
- voice, conversation timing, emotional behavior and embodiment milestones require real-device/manual owner review because emulator-only success is insufficient for human-quality claims.

# Queue integration rule

While v80 or another canonical work order is active, this trunk MUST remain inactive planning context. After the active work order is fully accepted/merged/released/reconciled according to repository policy, the Queue should select exactly one bounded next phase from this trunk, starting at the earliest unfinished H-phase. Do not implement multiple H-phases in parallel.

Every phase must leave a durable Queue/handoff with actual heads, runtime fingerprints, builds/proofs, acceptance truth, blocker and exactly one next action.

# Research references to re-check when each phase activates

These links are implementation research, not project source-of-truth. Re-check license, model card and current Android support before adoption:

- Gemma model family / mobile reasoning: https://ai.google.dev/gemma/
- FunctionGemma: https://ai.google.dev/gemma/docs/functiongemma
- EmbeddingGemma: https://ai.google.dev/gemma/docs/embeddinggemma
- sherpa-onnx Android/offline speech stack: https://k2-fsa.github.io/sherpa/onnx/android/
- sherpa-onnx Pocket TTS notes: https://k2-fsa.github.io/sherpa/onnx/tts/pocket.html
- Chatterbox: https://github.com/resemble-ai/chatterbox
- Kyutai streaming/full-duplex speech research: https://kyutai.org/
