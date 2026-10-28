# FUTURE ONLY — G1 long-term memory recall and correction targeting

**Status:** Isolated, pure-Java policy prototype only. **NOT in Yahya AI v83. NOT integrated into Android or any provider.** Registered issue #115 and separate DRAFT workstream under the user's 29 Oct 2026 decision. Existing Room #111 exclusively owns serialized v83 integration and publication. Frozen Core #113's tested v83 source and fingerprint must remain unchanged.

## Governance and exclusive ownership
Root AGENTS.md §§2/4/5/10/12, the main queue, active Core/Room workorders and validation policy apply. Existing cognitive worker owns only:
- `ci/prototypes/CelineFutureMemoryRecall.java`
- `ci/celine_future_memory_recall_contract_test.py`
- `ci/work-orders/CELINE_FUTURE_MEMORY_RECALL.md`

All other app Java, Room/scene, shared MainActivity, Gradle, signing, updater, workflow, central queue, AGENTS, main and stable release paths are protected. Do not cherry-pick this future branch into v83. The product decision **explicitly excludes this future-only scope from v83** so it does not prevent the already handed-off Room #111 release. Each run ends with a clean handoff; no new worker.

## Prior G1 inventory — reuse rather than rewrite
- `CelineStructuredMemory.java`: existing persistence, explicit remember, correction by selected ID, forget, legacy import.
- `CelineProtectedMemoryStorage.java`: existing AndroidKeyStore AES-GCM protection.
- `CelineMemoryEngine.java`: existing token retrieval, expiry, supersession, consolidation and fuzzy correction-target selection. It currently considers a *single shared term* sufficient for `findCorrectionTarget`; the future policy explores stricter ambiguity handling.
- `CelineMemoryControls.java`: existing inspect/selected-ID correct/forget UI; remains the safe fallback.
- `CelineGoalTaskGraph.java` / `CelineGoalTaskRuntime.java`: existing persistent task graph and resume, not to be duplicated.
- `CelineContextBrokerG14.java`: existing G1 context bridge; no provider wiring is modified here.

The older Persona workorder references `ci/work-orders/CELINE_COGNITIVE_OS_EXECUTION_ORDER.md`, which was **NOT FOUND** on the inspected Core branch. This is a bounded, explicitly user-authorized memory-prototype task, **not** invented approval for other unseen roadmap stages or paused G2.3 work.

## Prototype acceptance
`CelineFutureMemoryRecall` accepts only projections of already-owned G1 memories. It does not persist, learn from chat, mutate memories, call a network, or issue tools. It provides bounded deterministic German token normalization and aliases; query-term evidence scoring; expiry and supersession; privacy and explicit authorization checks; conservative corrections that suggest an ID only for an unambiguous multi-term match. A sensitive successor suppresses an old eligible predecessor. Unknown/conflicting corrections require the existing exact-ID user UI. `SHAREABLE_ON_REQUEST` is rejected until an explicit permission adapter is implemented.

**Validation:** `python3 ci/celine_future_memory_recall_contract_test.py` uses javac and java, runs 20 deterministic assertions; locally **PASS 20/20** after two evidence-driven corrections (generic-match overreach and hidden sensitive-successor semantics). No Android runtime build/proof is required for these isolated `ci/` files. The result proves **prototype behavior only**, not durable learning, app integration, external-provider answers or v83 readiness.

## Deferred integration, required checks
After a published v83 and a separate authorized app-source ownership handoff, audit exact current `CelineMemoryItem` privacy/provenance mapping and `CelineContextBrokerG14` dispatch. Add real-source regression fixtures for correction ambiguity before any optional G1 adapter. Preserve encrypted storage, historical updates/migrations, deterministic deletion, provider permissions and evidence. Do not mutate live v83 source or create a second integration writer.

**exact_next_action:** Check the actual post-v83 G1 source call sites for a minimal Candidate adapter and strict share authorization; add a failing exact-source fixture for the strongest proven ambiguity case before proposing any runtime wiring.