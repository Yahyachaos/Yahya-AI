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

**Validation:** `python3 ci/celine_future_memory_recall_contract_test.py` uses javac and java. Historical baseline 20/20; latest local **PASS 34/34** on verified source/test Git blobs `0ac2b07c9480f303462421f83a0dbfce147c1094` and `02f26f7e64fc37c0732a323259c6472fb1946e69`. Exact FUTURE-ONLY source/test branch head `8e0f2d9b950de321ec62dbff3b5ee75e6c28a615`. Seven additional monotonic-privacy cases in the first batch (expired public/private successor, stale correction abstention, duplicate-ID mismatch/order and expired duplicate) and four authoritative retired-ID cases in the second batch extend the prior 23-case checkpoint. All tests compile/run in a local temporary Java directory. No Android runtime build/proof is required for these isolated `ci/` files. This is **prototype-only** evidence, not durable learning, production privacy, app integration, external-provider answers, v83 or device PASS.

## 29 Oct 2026 actual future-only implementation and source-grounded limitation

**Completed in this isolated branch:** `174508ff` fixes expired successor resurrection and duplicate-ID ambiguity; `74b9d0c2` installs seven deterministic cases; `4ae6ae31` adds a separate retired-ID policy input so old facts do not reappear when the source owner prunes an expired successor; `8e0f2d9b` adds four tests. Local red→green witness: expired successor originally returned `old-cat`, and conflicting duplicate `same-id` originally returned an eligible but contradictory public projection; after the fix, all 34 cases pass. An overloaded `retrieve(query, candidates, retiredIds, maxResults, now)` and matching correction-target function now accept **explicit source-authorized retractions** and fail closed on null retraction sets. The legacy overload still exists for complete-lineage projections and is **not safe on pruned lineages**. The prototype never guesses or persists retired IDs.

**Exact read-only G1 source finding:** on current Room branch, `CelineBrain.java` blob `be4263b6855f1190d1654cb6716918f5012291b0` exposes `CelineMemoryPrivacy {LOCAL_PRIVATE, LOCAL_SENSITIVE, SHAREABLE_ON_REQUEST}` plus item `privacyScope`, `provenance`, `supersedesId`, and `expiresAtEpochMs`. Room `CelineMemoryEngine.java` blob `9a548eaaa1f17d62ecc711ea6d1453045e81910b` removes expired entries **before** its `consolidate()` supersession pass; `activeSnapshot()` likewise ignores expired successors. Thus the new future-only retrieval policy **cannot guarantee production no-resurrection** if G1 has already erased the successor and supplies neither it nor a durable retired-ID tombstone. The current `CelineContextBrokerG14.java` blob `21212f6b3c6092569d703f4cd784bf34f483ab08` projects retrieved memory summaries without its own `privacyScope` filter. No protected G1 production file is changed here; durable tombstones and explicit share authorization require a separate post-v83 G1 ownership/adapter gate. Never describe this prototype as a production privacy fix.

**CI truth:** automated Android Build #1829 [run 40793127578](https://github.com/Yahyachaos/Yahya-AI/actions/runs/40793127578) was terminal FAIL at `android-actions/setup-android@v3` before app compilation; earlier #1823 had the same SDK/environment blocker. The pure-Java 34/34 local test is independent and does not claim an APK. No v83 merge, app runtime, shared queue, workflows, source assets or release files were touched. User's 29 Oct decision explicitly excludes this PR from v83 under root AGENTS §10.

## Deferred integration, required checks
After a published v83 and a separate authorized app-source ownership handoff, audit exact current `CelineMemoryItem` privacy/provenance mapping and `CelineContextBrokerG14` dispatch. Add real-source regression fixtures for correction ambiguity before any optional G1 adapter. Preserve encrypted storage, historical updates/migrations, deterministic deletion, provider permissions and evidence. Do not mutate live v83 source or create a second integration writer.

**exact_next_action:** Within the SAME FUTURE-ONLY prototype/test ownership, add a source-grounded fail-closed `CelineMemoryPrivacy` projection fixture for actual G1 enum values, unknown scope and explicit authorization, using current Room `CelineBrain.java` only as read-only evidence. Test it with isolated javac/java and commit on PR #116; do NOT wire the Android app, alter Core #113/Room #111, or treat future-only progress as blocking v83.