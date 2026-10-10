# Celine Cognitive OS — Canonical Brain Execution Order

## Status and precedence

Activated by explicit user direction on 2026-09-02 on the existing single-flight strand:

- repository: `Yahyachaos/Yahya-AI`
- PR: `#111` (remain DRAFT)
- branch: `auto/celine/v80-human-videochat-presence`
- no new branch or PR
- no merge or release

This file is the **active product execution order** for Celine intelligence work while the current user override remains in force.

The room is **FROZEN by user direction**. Yahya will build/rebuild the room separately. No room reconstruction, room measurement, furniture transform, room camera, room material, room lighting, window treatment, NavMesh/free-navigation or room visual-proof work may resume unless Yahya explicitly reopens it.

Historical v80/avatar/room work orders remain protected evidence and contracts for already accepted behavior, but they are no longer the active execution scheduler:

- `ci/work-orders/V80_CELINE_HUMAN_VIDEOCHAT_PRESENCE.md` — protected historical embodiment/videochat requirements.
- `ci/work-orders/V80_CELINE_INTERACTIVE_ROOM_EMBODIMENT_AMENDMENT.md` — protected historical room/embodiment contract; execution is frozen.
- `ci/work-orders/CELINE_HUMAN_INTELLIGENCE_EXECUTION_TRUNK.md` — retained as a **requirements library, acceptance catalog and technology register**. Its old H0→H13 numbering is **not the active implementation chronology**.

Live GitHub and root `AGENTS.md` keep their normal higher authority for repository/run state and permanent working rules. `ci/CELINE_PROGRESS_QUEUE.json` must point to this work order while this plan is active.

## North Star

Celine must become a coherent personal AI system whose identity and useful cognition belong to Yahya AI, not to any single external language-model provider.

Permanent target:

> **Celine remains Celine when no OpenAI or other cloud model is available.**

Cloud models may be optional teachers, reviewers or temporary high-capability fallbacks. They must never own Celine's identity, long-term memory, personality, goals, permissions, learned skills or persistent state.

Celine must improve with use not merely by accumulating facts, but by becoming measurably better at completing Yahya's tasks.

The goal is not a claim of consciousness or biological emotion. The goal is a highly capable, persistent, learning, natural personal AI with bounded agency and an app-owned cognitive identity.

## Permanent cognitive loop

All mature Celine behavior should converge on one central loop:

`OBSERVE -> ORIENT/ATTEND -> RETRIEVE -> DEFINE GOAL -> REASON/SIMULATE -> PLAN -> PERMISSION -> ACT -> OBSERVE RESULT -> VERIFY -> RECOVER/CONTINUE -> LEARN -> RESPOND`

Simple requests may skip unnecessary stages. High-impact or uncertain actions must not skip permission/verification requirements.

## Celine Cognitive OS — permanent brain regions

The architecture is not one giant prompt around one LLM. Celine is the whole system; neural models are replaceable reasoning components inside it.

### Identity Kernel
Owns stable Celine identity, personality contract, language style, user relationship model, capability boundaries and durable behavioral invariants. Model replacement must not reset identity.

### Self Model / Metacognition
Tracks what Celine knows, what she only infers, what evidence is fresh, what capabilities/tools are currently available, where confidence is low, what failed previously and when external verification is required.

### Attention Manager
Chooses which goal, memory, screen evidence, device context and problem deserve current processing. Prevents context flooding and irrelevant-memory injection.

### Working Memory
Holds the current conversation/task state, active assumptions, current plan, recent tool results, unresolved questions and next step. Must persist enough structured state to recover safely after Activity/process/device restart.

### Semantic Memory
Durable factual knowledge about the user, people, devices, projects, preferences and stable entities.

### Episodic Memory
Events and prior sessions: what happened, when, in which context, what was attempted and what outcome occurred.

### Procedural / Skill Memory
Reusable successful procedures and workflows. Repeated successful plans should become cheaper, safer skills instead of being reasoned from scratch each time.

### Personal Knowledge Graph
Links people, devices, projects, places, tasks, events, memories, decisions and skills so references such as “das Quad”, a part, a repair attempt and a project can resolve to the same entity graph.

### Goal & Task Graph
Stores durable goals separately from conversational memory: goal, subgoals, dependencies, status, last confirmed step, blockers, next action and completion evidence. This is what makes “mach weiter” work across restarts and days.

### World Model
Represents the currently supported observable state: active app/screen, device state, relevant notifications/context, current task state, recent action results and freshness/provenance. It distinguishes observed truth from assumption.

### Knowledge Cortex
Separates general/domain knowledge retrieval from personal memory. Manuals, project docs, local indexes and later authorized fresh retrieval belong here instead of contaminating personal memory.

### Executive / Planner
Decides whether to answer directly, retrieve memory, inspect context, use a tool, decompose a task, simulate alternatives, ask a necessary question or escalate to a stronger reasoning provider.

### Fast Brain
Low-latency local path for deterministic commands, continuation/correction handling, simple classification, tool routing and cheap conversational decisions.

### Deep Brain
Replaceable local reasoning model for difficult language, planning and reasoning. It is a component of Celine, not Celine's identity.

### Critic / Verifier
For important tasks, checks plans, contradictions, calculations, action results and success conditions independently enough to catch plausible-but-wrong execution.

### Tool Cortex
Typed, allowlisted capabilities with explicit schemas and deterministic results. Existing `DeviceBridge`, accessibility, notifications and later connectors are migrated behind this layer instead of arbitrary free-text UI control.

### Permission Policy
Owns action risk classes and fresh confirmation rules. Model confidence never bypasses Android/security/user permission boundaries.

### Simulator
For genuinely complex actions, evaluates plausible routes and failure modes before touching external state. Simulation is structured planning, not an uncontrolled hidden action loop.

### Causal / Outcome Memory
Records not only what happened but why an action likely succeeded/failed, what correction fixed it and what evidence supports that conclusion.

### Learning Engine
Learns explicit corrections, stable preferences, tool outcomes, successful skills, pronunciation and approved recurring workflows. Learning initially changes inspectable data/policies, not production source code.

### Memory Consolidator
Periodically/at appropriate events merges duplicates, summarizes episodes, resolves superseded facts, applies expiry, strengthens repeated evidence and prevents raw conversation accumulation. It must be event-driven/bounded, not a permanent battery-burning inference loop.

### Skill Compiler
Promotes repeatedly successful verified procedures into reusable deterministic or bounded skills with preconditions, steps and success checks.

### Curiosity / Initiative Engine
Identifies information gaps only when they matter to an active goal. It asks or proposes the next useful step rather than generating random “curious” behavior.

### Affect & Personality State
Stable personality is separate from bounded transient synthetic affect. One app-owned state can later drive wording, voice prosody, face, gaze and body consistently.

### Autonomy Scheduler
Resumes open goals and runs explicitly approved recurring/proactive work within policy. No always-inference loop.

### Resource Brain
Uses RAM, battery, thermals, connectivity, latency and model residency to choose the cheapest sufficient cognitive route and degrade gracefully.

### External Teacher Gateway
Optional access to stronger external models. External systems are teachers/reviewers/fallbacks, never owners of memory or identity. Their outputs are treated as evidence/candidate solutions, not blindly persisted as Celine truth.

### Brain Versioning
Reasoning-model versions can be replaced, benchmarked or rolled back without losing Celine identity, memories, goals, skills, permissions or personality. Training datasets/adapters/models require version/provenance tracking.

## Canonical implementation chronology — five Brain Generations

The previous H0→H13 list is retained only as a requirements and acceptance library. **Do not execute it linearly.** The active implementation sequence is the five generations below, strictly single-flight.

# Brain Generation 1 — Cognitive Kernel & Persistent Mind

Purpose: create the actual app-owned brain before adding more model power.

## G1.0 Baseline, contracts and intelligence evaluation harness

Before architecture replacement, freeze measurable baselines for:

- follow-up/correction resolution;
- same-session and restart continuity;
- explicit memory recall/correction/forget;
- multi-step reasoning cases;
- uncertainty/contradiction handling;
- tool selection and wrong-tool rejection;
- action success verification;
- latency and unnecessary cloud-call count.

Inventory existing `MainActivity`, `ConversationIntelligenceV78`, flat memory, `DeviceBridge`, accessibility, notification, wake, speech and voice paths.

No large runtime rewrite before this baseline exists.

## G1.1 Central `CelineBrain` owner and contracts

Create stable project-owned interfaces/owners for at least:

- `CelineBrain`
- `CelineMemory`
- `CelineWorkingState`
- `CelineGoalGraph`
- `CelineContextBroker`
- `CelineToolRegistry`
- `CelinePermissionPolicy`
- `CelineReasoningProvider`
- `CelineVerifier`
- `CelineLearningEngine`
- `CelineAffectState`
- `CelineResourcePolicy`

`MainActivity` becomes presentation/input orchestration and must stop owning the reasoning policy itself. Migration is incremental; current working behavior must survive each bounded step.

## G1.2 Structured memory

Replace the flat memory blob with versioned structured records supporting:

- semantic/profile/preference memory;
- episodic memory;
- decisions/corrections;
- open goals/tasks;
- procedural/skill history;
- temporary context with expiry.

Every durable record carries provenance, timestamps, confidence, importance, privacy/scope, explicit-vs-inferred state, expiry where relevant and supersession/conflict links.

Explicit correction outranks older inference. Never dump the whole memory store into every prompt.

## G1.3 Knowledge graph + Goal/Task graph + persistent working state

Introduce stable entity links and durable task state so Celine can resume the correct project/goal after restart and understand cross-session “weiter” requests.

Persist structured state, not private free-form hidden reasoning transcripts.

## G1.4 Self model + Attention + Context Broker

Celine distinguishes known/observed/inferred/unknown state, chooses relevant context and retrieves only what the current goal needs.

## G1.5 Consolidation, privacy and memory controls

Add conflict resolution, expiry, deduplication, consolidation, inspect/correct/forget controls and Android-appropriate protected storage/migration.

### Generation 1 acceptance

- identity/memory/goals survive model/provider restart;
- cross-session task resume works;
- corrections supersede stale memory;
- irrelevant memory is excluded;
- unknown is not fabricated as remembered;
- current behavior has no regression against G1.0 baselines.

# Brain Generation 2 — Agency, World Model & Verified Action

Purpose: turn existing device capabilities into real bounded intelligence.

## G2.1 Typed Tool Cortex

Wrap existing device/accessibility/notification capabilities as typed allowlisted tools with preconditions, result objects and explicit failure states.

## G2.2 Permission policy

Use bounded risk classes:

- L0 read-only/local observation;
- L1 reversible local action;
- L2 external communication/state change requiring explicit target/intent;
- L3 money, deletion, credentials/security, irreversible or sensitive actions requiring fresh confirmation unless a narrowly defined approved policy exists.

## G2.3 World Model

After each action, update observed state and freshness/provenance rather than assuming success.

## G2.4 Planner + Simulator

Support multi-step plans, dependencies, expected outcomes and alternative paths. Simulation is used when complexity/risk justifies it.

## G2.5 Observe -> Act -> Verify -> Recover loop

Every multi-step task defines success criteria, observes the result, retries only within bounds and recovers or reports failure accurately.

## G2.6 Causal outcome memory

Store verified successes/failures and likely causes so future planning improves.

### Generation 2 acceptance

- multi-step device tasks complete with verification;
- wrong-tool use is rejected;
- permission gates work;
- failed intermediate steps recover safely;
- Celine never claims success without result evidence.

# Brain Generation 3 — Celine-Owned Local Neural Brain

Purpose: make external reasoning optional instead of foundational.

## G3.1 Provider-independent reasoning interface

No permanent business logic may depend on a single model identifier/vendor. Providers are replaceable behind `CelineReasoningProvider`.

## G3.2 Fast Brain

Benchmark/implement the smallest sufficient local router/classifier for simple intent, continuation, memory/tool routing and deterministic actions.

## G3.3 Deep Brain

Benchmark a capable local Android reasoning/language model against the G1.0/G2 evaluation suites on the actual target device. Selection depends on quality, German ability, tool-use support, RAM, storage, first-token latency, sustained thermals and battery.

## G3.4 Routing

Use deterministic/local paths for simple tasks; Deep Brain only when needed. External reasoning may be configured for cases local capability cannot yet satisfy.

## G3.5 External Teacher Gateway

External models may review hard cases, provide candidate solutions and help create curated training examples. Their output is not automatically Celine memory or truth.

## G3.6 Resource Brain and graceful degradation

Choose model/capability based on RAM, thermal, battery, connectivity and privacy. No silent cloud fallback for private local context.

### Generation 3 acceptance

- core conversation/memory/tools work with cloud disabled;
- local brain passes agreed quality threshold;
- provider/model can be changed without identity/memory reset;
- cloud usage is optional, explicit and measurable.

# Brain Generation 4 — Experience, Skills & Controlled Self-Improvement

Purpose: make Celine genuinely better from use.

## G4.1 Outcome learning

Learn from verified task outcomes, not plausible text alone.

## G4.2 Correction/preference learning

Explicit corrections are durable immediately; inferred preferences require repeated evidence/confidence and remain reversible.

## G4.3 Skill Compiler

Convert repeated verified workflows into reusable skills with preconditions, typed steps, expected results and rollback/recovery rules.

## G4.4 Personal training dataset

Curate high-quality examples containing task/context, chosen plan, tool sequence, verified outcome, failure, user correction and final successful route where appropriate. Sensitive data handling and provenance are mandatory.

## G4.5 Distillation / adapter training

Use curated examples to improve Celine's own local neural components. External teachers can contribute candidate supervision, but only reviewed/high-quality data is promoted.

## G4.6 Brain versioning and rollback

Every trained model/adapter/dataset has version/provenance/evaluation results. A worse brain can be rolled back without rolling back memory/identity.

### Generation 4 acceptance

- repeated tasks become measurably faster/more reliable;
- prior failures reduce recurrence;
- learned preferences are inspectable/undoable;
- new brain version beats prior version on fixed suites and real owner tasks before promotion.

# Brain Generation 5 — Natural Continuous Celine & Bounded Autonomy

Purpose: bind the mature cognitive core into natural speech, personality, initiative and embodiment without sacrificing reliability.

## G5.1 Curiosity/initiative

Ask for missing information only when it materially helps an active goal; propose useful next actions without spam.

## G5.2 Stable personality + bounded affect

Identity is permanent; transient synthetic affect is gradual, event-driven and decays. The app owns affect state rather than letting the LLM invent mood per sentence.

## G5.3 Proactive/autonomous scheduler

Resume open goals, approved recurring workflows and subscribed conditions within policy. Event-driven, cancellable and auditable; never an always-on inference loop.

## G5.4 Local ears and full-duplex conversation

Local wake word, VAD, German streaming ASR, semantic endpointing, barge-in, cancellation propagation and echo/re-entry protection.

## G5.5 Celine-owned local voice

Preserve actual-PCM lip-sync architecture. Select the final offline voice from real-device German quality/license/latency/thermal testing, not fashion.

## G5.6 One cognitive state across voice and body

Listening/thinking/speaking/success/failure/memory-recognition states feed voice, facial/gaze/body behavior through existing accepted central avatar ownership. Cognition must still work when rendering is unavailable.

## G5.7 Long-session acceptance

Run extended real use covering conversation, memory, goals, tools, interruptions, offline mode, failures, recovery, learning, privacy controls, resource pressure and model restart.

### Generation 5 acceptance

Celine feels continuous because her memory, goals, skills, timing, voice and actions agree over time — not because one short demo sounds human.

## Mapping of the old H0-H13 trunk into the new chronology

The old requirements are not deleted. Their **ordering is superseded** as follows:

- old H0 contracts/evals -> G1.0/G1.1 and then continuous evaluation across every generation.
- old H1 memory -> G1.2-G1.5.
- old H2 stronger brain -> split across G1 Executive/Self Model and G3 local neural brain; external large models are optional providers/teachers, not the core identity.
- old H3 agent loop -> G2.
- old H4 local ears -> G5.4, after the cognitive/action core is reliable.
- old H5 offline voice -> G5.5.
- old H6 human turn-taking -> G5.4/G5.5.
- old H7 personality/emotion -> Identity Kernel starts in G1; transient affect integration completes in G5.2/G5.6.
- old H8 context broker -> moved earlier to G1.4 because planning cannot be reliable without controlled context.
- old H9 proactive autonomy -> G5.1/G5.3 after verified agency and learning.
- old H10 learning -> expanded into G4 outcome learning, skill compilation, dataset governance and distillation.
- old H11 embodied cognition -> G5.6; room movement remains frozen until explicitly reopened.
- old H12 local-first optimization -> G3.6 plus continuous Resource Brain policy.
- old H13 final acceptance -> evaluation begins at G1.0 and continues every generation; final extended acceptance is G5.7.

## What is explicitly no longer active

Do not treat any of the following as the next task while this order is active:

- room reconstruction/re-measurement or reference matching;
- furniture scale/orientation iteration;
- room camera/material/light/window polish;
- room visual proofs or final room temporal acceptance;
- NavMesh/free navigation;
- old linear H0→H13 execution merely because the numbering exists;
- “upgrade the LLM” as a substitute for building CelineBrain/memory/goals/tools;
- a single flat memory text blob as the target architecture;
- a permanent cloud model as Celine's identity;
- uncontrolled source-code self-modification;
- always-on autonomous inference loops.

## Protected existing foundations

Preserve unless independent evidence requires a bounded repair:

- canonical Celine source identity/rig/morph foundations;
- restored accepted Celine palette, illumination and PBR response;
- accepted seated CALL lower-body values;
- central animation/presence ownership;
- v77 actual-PCM lip sync and working voice paths;
- v78 conversation-context foundation plus the 2026-09-02 correction/continuation/topical-anchor improvements;
- keyboard/lifecycle/updater/release discipline;
- existing device/accessibility/notification capabilities until migrated behind typed tools.

The room runtime may remain present for compatibility, but it is not an active quality target while frozen.

## Validation discipline for Cognitive OS work

Follow root `AGENTS.md` and `ci/CELINE_VALIDATION_POLICY.md`.

- docs/queue/work-order only -> no Android build.
- pure deterministic contract/test logic -> smallest relevant unit/contract test where available; build only if runtime/build inputs changed.
- runtime Java/build input change -> exactly one Android build.
- conversation/memory/brain changes -> deterministic intelligence evaluation first; no avatar/room/video proof unless the changed surface genuinely affects them.
- tool/action changes -> targeted action/permission/verification tests.
- voice/audio changes -> targeted voice/audio tests only.
- avatar-visible changes -> targeted visual proof only.
- no full expensive suite on every bounded iteration.

## Permanent single-flight rule

Only one generation and one bounded sub-block may be active at once. Do not implement G1 memory, G2 tools and G3 local models in parallel just because they are architecturally independent.

## Exact next action after plan activation

**Execute G1.0 only.**

Create the baseline intelligence evaluation/audit contract for the existing runtime before a major CelineBrain rewrite. It must cover at minimum continuation/correction/topical-reference behavior, memory recall/correction/forget, multi-step reasoning/uncertainty, current device-tool routing, restart/task-resume gaps and cloud-call count/latency observations.

Do not yet implement the large structured-memory migration or local neural model. Once G1.0 is recorded and the current baseline is reproducible, proceed to G1.1 central `CelineBrain` contracts as the next bounded block.
