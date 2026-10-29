# Voice-Lab — independent future Celin speech work

**Status (29 October 2026): FUTURE-ONLY pure Java prototype; not in v83; not a release gate.** [Issue #117](https://github.com/Yahyachaos/Yahya-AI/issues/117). Single writer: existing Sprach-Labor automation. Owned paths ONLY `tools/voice-lab/**` on the `future/voice-lab-tts-lifecycle-20261029` DRAFT workstream. This branch must not be integrated into the current v83 Room #111 branch or the frozen Core #113 branch; never touch `app/**`, `ci/**`, shared queue, Gradle, signing, updater, or workflows. The v83 version-scoped included functionality excludes all of Voice-Lab.

## Read-only architecture inventory

- [`SpeechAudioBus.java`](../../app/src/main/java/de/yahya/ai/SpeechAudioBus.java): asynchronous Android-main-thread weak-listener dispatch of speech levels and viseme cues; `reset()` posts silence.
- [`SpeechLipSyncV77.java`](../../app/src/main/java/de/yahya/ai/SpeechLipSyncV77.java): stateful PCM-window shape smoothing, with fast German B/P/M/F/V-like closures and quiet-frame reset; it only knows analyzed source PCM, not the output device's actual presentation time.
- [`LocalNeuralTtsEngine.java`](../../app/src/main/java/de/yahya/ai/LocalNeuralTtsEngine.java): sherpa-onnx Supertonic 3 model (female SID 4 default) synthesizes the full text, then uses a float PCM `AudioTrack`. Approx. 50Hz mouth cues are published **before** the corresponding blocking `AudioTrack.write` and can lead the playback head. Each `speak` creates a new thread; `release` can stop the current track but old worker callbacks have no shared generation/utterance token. `waitForPlaybackHead` accounts for the audio tail; it can exit when the track is released, after which the prior worker may still invoke `onDone`. This is a **source-grounded concurrency risk**, not a proven Samsung bug.
- [`SupertonicModelManager.java`](../../app/src/main/java/de/yahya/ai/SupertonicModelManager.java): separate local model archive installer and validation; an installed local model is preferred. No new model was downloaded in this Voice-Lab.
- [`MainActivity.java`](../../app/src/main/java/de/yahya/ai/MainActivity.java): routes to installed local neural synthesis first, otherwise online `/v1/audio/speech` (MP3 + `MediaPlayer`) or system `TextToSpeech`. Online MP3 playback has only general speaking animation here, not proven PCM-driven viseme alignment. Legacy `TextToSpeech` voice choice may differ by device/installed engine; no verified Samsung S25 Ultra voice-quality recording exists.

## Three prioritized problem classes

1. **Playback ownership / interruption**: no common utterance epoch across local TTS, the device TTS fallback and MP3 player; late callbacks could report false completion/reset another speech. Isolate this with a deterministic reducer before proposing an app integration.
2. **Real-time lip sync**: source-PCM visual cues are generated before frames are written; use actual accepted PCM offsets and advancing playback-head positions before emitting mouth cues. Online MP3 is not proven mouth-synchronized.
3. **TTS reliability and German quality**: true Android-8 stock `com.svox.pico` native crashes are in the real [combined v83 smoke #72 logs](https://github.com/Yahyachaos/Yahya-AI/actions/runs/40776082910), including API26 job 123128261817. They are separate legacy OS failures, not proof that the Samsung S25 Ultra speech stack is broken. A good German voice, low latency, natural intonation, engine licensing, and S25 playback require independent device/model listening and are not solved by this prototype.

## Implemented Java playback contract

`VoiceLifecycleContract.java` is Android-free and does not synthesize, download, play or change production speech. All methods are synchronized. It implements:

- monotonic generation IDs, rejecting callbacks from interrupted/replaced utterances;
- explicit PREPARING / BUFFERING / PLAYING / DRAINING and terminal states;
- PCM acceptance based on *returned successful write frame counts*, with no mouth movement until the actual playback head advances into that accepted segment;
- recognition that `synthesisDone` precedes playback-tail completion;
- cancellation, engine death, empty synthesis, zero/null cue rejection and timeout handling with a monotonic clock;
- fail-closed timeouts checked on each event even if the periodic watchdog is delayed.

Run the Android-free contract:

```sh
sh tools/voice-lab/run_tests.sh
```

**Deterministic local test evidence (OpenJDK 21, no Android):**

- Initial contract and assertions: `VOICE_LIFECYCLE_CONTRACT_PASS checks=57`.
- Added a regression that invokes `prepared()` 7001ms late *without* `tick()`: actual initial test failed with `AssertionError: FAIL late preparation must time out without scheduled watchdog`.
- Fixed deadline enforcement in `current()` (for preparation and PCM arrival); after fix: `VOICE_LIFECYCLE_CONTRACT_PASS checks=62` including stale callbacks, interrupted speech, engine death, stalled buffer/drain, head boundaries and delayed callback rejection.

This is a JVM logic test only. There is **no Android APK**, no rerun of v83 CI, no Supertonic runtime proof, no streaming acoustic benchmark, no real mic/speaker recording, no S25 Ultra empirical voice evaluation, and no proof the legacy Pico system SIGSEGV is repaired.

## Release isolation / known limitations

Source frozen for v83: [Room #111](https://github.com/Yahyachaos/Yahya-AI/pull/111) sole combined integrator/publisher and [Core #113](https://github.com/Yahyachaos/Yahya-AI/pull/113) immutable tested Persona/G1 handoff. Branch based only on main `5a23ccf75fb82b97d262cf3d8be24a6fe77fb26b`. No shared/production Android/runtime input was altered. Full future Android integration requires a later explicit owner decision and bounded device testing.

The current prototype retains all accepted cue intervals until terminal completion; production should age out intervals as the playback head advances, use a stable audio-head wrap/latency mapping, and avoid emitting UI callbacks from a stale/finished epoch.

## Continued implementation and source-exact tests — 29 Oct 2026, later Voice-Lab run

This remains **one FUTURE-ONLY voice workstream**, not v83 integration and not a completed natural voice. Sole owned file prefix `tools/voice-lab/**`, existing DRAFT PR #118. No `app/**`, `ci/**`, Room #111/Core #113, Gradle, shared Queue, signing, updater, workflows, Android APK or release changes.

### Executed coherent substeps

1. **Actual partial-write/replay + bounded cue buffer.** `VoiceLifecycleContract` now retires fully consumed cue segments without discarding the last *played* sample at exact head boundaries; it coalesces adjacent equal mouth shapes. A maximum of 256 retained cue segments is enforced without silently discarding unheard visemes. Queue overrun is **FAIL-STOP** (`cue backlog exceeded`), and `canAcceptPcm` provides an advisory pre-write budget check. The caller must serialize this preflight with `AudioTrack.write` and the actual returned accepted frame-count callback; a concurrent reservation is NOT claimed. The Android adapter must stop an actual track on fail-stop.
2. **Adversarial deterministic regression.** New `VoiceLifecycleAdversarialTest` covers 5,000 sequential short writes + head advances, a 256-segment silent-head backlog/overflow/recovery, positive/zero/negative partial writes, exact cue boundary, backwards-head jitter, synthesis-before-drain, 60 seeded utterance replays × 180 callbacks against an independent frame-by-frame oracle, cancelled generations and late engine events. Genuine initial RED: `first cue pruned after crossing its end` expected 1 but got 2; source correction and all tests PASS. A prior missing `retainedCueCount` accessor caused the initial test to fail compilation; tests were made executable and the behavioral failure then reproduced.
3. **Signed 32-bit playback-head unwrapping.** New Android-free `VoicePlaybackHeadTracker` and focused test convert signed `AudioTrack.getPlaybackHeadPosition()` observations relative to a fresh stream baseline into monotonic 64-bit positions. Rejects stale generation, backwards jitter, impossible future head, unsigned half-wrap ambiguity and observations after stop; exact wrap and integration with playback-head-driven viseme selection are covered. Assumes no in-place AudioTrack reset and head polling more often than 2^31 frames; these are adapter requirements, not measured device properties.
4. **Conservative spoken-German text chunk planning.** New `GermanSpeechChunkPlanner` and tests preserve words, split natural sentence boundaries and long text at whitespace, and avoid mistaken splits for `Dr.`, `z. B.`, decimals and `29.10.`. It **does not** synthesize German speech or claim to solve spoken `Punkt Punkt`; it does not silently remove the user's words/markup. Numeric-date/ordinal boundary was first RED (`Nr. 4.` incorrectly grouped), then corrected and PASS.

### Exactly executed local JVM test evidence (OpenJDK 21)

From a clean temporary class-output directory using **the same Java source blobs as the GitHub PR files**, executed `sh tools/voice-lab/run_tests.sh`:

```text
VOICE_LIFECYCLE_CONTRACT_PASS checks=62
VOICE_ADVERSARIAL_PASS checks=53059
VOICE_HEAD_TRACKER_PASS checks=35
VOICE_GERMAN_TEXT_PLANNER_PASS checks=20
```

Total **53,176 deterministic assertions**. Local `git hash-object` matched fetched GitHub file blob IDs for all 8 files listed below (source/runner, not Android binaries):

- `VoiceLifecycleContract.java` = `770212539268e8e73325fdb8d3890d390887eb51`
- `VoiceLifecycleContractTest.java` = `6f545fdbf66a97a97484cdb8532476755328fc04`
- `VoiceLifecycleAdversarialTest.java` = `bad04e83ee1a884d0b51ea5da5f8bf2d865e4ed8`
- `VoicePlaybackHeadTracker.java` = `4ab9ee3bc2e4deff3ccfe91df56f453ae8d60004`
- `VoicePlaybackHeadTrackerTest.java` = `33d4d2264cff188805b0f4797e7a038f080d19aa`
- `GermanSpeechChunkPlanner.java` = `879159f4e3150a08d79e9d4f80a705a3214e5d66`
- `GermanSpeechChunkPlannerTest.java` = `95412cc240be68479b8ef8ddff996f9b200e34f5`
- `run_tests.sh` = `42727bc3096dc03ee222544b137dca83781a5c2b`

None of this validates synthesis, speaker output, German naturalness, Android hardware timing, lip movement on S25 Ultra, third-party model licensing, or the old `com.svox.pico` Android-8 SIGSEGV. In particular `AudioTrack` head counter is *not* a calibrated acoustic presentation timestamp; sound hardware latency and drift need future device measurements. The existing Supertonic 3 / sherpa-onnx female preset implementation remains untouched.

### Remaining technical gap / handoff

A production integrator must later measure actual S25 Ultra output/timestamp latency and preserve epoch-safe ownership across neural/offline, device TTS and online MP3 fallback; this future prototype may be used only after an exclusive post-v83 ownership transfer and genuine device acceptance. Android 8 OS Pico failure is separately tracked and not declared fixed.

**Completed previous step (superseded by the continuation below):** Extend the isolated German text-planner adversarial vectors for URL/email, quoted sentence endings, numeric dates and long unbroken tokens; fix only reproducible split/loss cases, rerun exact-source JVM tests, then independently study a timestamp-to-viseme latency calibration contract. Remain FUTURE ONLY; never touch v83/Room/Core files.




## 29 Oct 2026 — continued Voice-Lab implementation (FUTURE ONLY, v83 excluded)

**Current release ownership correction:** The newly approved, existing **Yahya AI — Build & Release** worker (automation ID `6ae29695b7a48191af25b982ab3606f0`) owns all v83 builds, shared integration, signing/updater and releases. Older references above to Room #111 as the sole release publisher describe the former arrangement, not the latest product decision. Room #111 still exclusively owns its Room source; Core #113 remains its frozen v83 handoff. **Voice-Lab #118 is a separate future-only writer, not an included v83 release gate.**

### Real bounded changes and TDD evidence

- **German chunk-planner repair:** A new independent adversarial test initially **FAILED** on `Sie schrieb: "Hallo!" Danach ging sie.` (the entire text was wrongly grouped in one chunk). The actual planner now considers trailing quotation/parenthesis characters, grouped punctuation such as `?!`, ordinals before spelled-out German month names (e.g. `5. November`), and distinguishes a short `29.10.` date fragment from the completed `29.10.2026.` sentence boundary. Mail addresses/URLs are preserved; oversize unbroken tokens are preserved rather than silently truncated. None of these heuristics establishes spoken German naturalness.
- **Conservative timing contract:** New standalone `VoiceAcousticTimingContract` rejects uncalibrated/stale/future/non-monotonic/overaccepted hardware-head samples, wrong generation and impossible jumps. An externally measured acoustic delay (not guessed by this lab) is mandatory before returning a conservative mouth-frame cursor. No optimistic playback-head extrapolation. This is only a JVM contract, not a Samsung timestamp measurement; actual sound-card latency, visual pipeline latency and conversion from real `AudioTimestamp` remain unknown.

**Source-exact OpenJDK 21 local regression, no Android:**
```text
VOICE_GERMAN_TEXT_PLANNER_PASS checks=20
VOICE_GERMAN_ADVERSARIAL_PASS checks=18
VOICE_ACOUSTIC_TIMING_CONTRACT_PASS checks=27
```
**65 checks actually executed in this continuation.** Earlier 53,176 lifecycle/adversarial/head/planner assertions from the prior run are recorded above but were **not rerun** by this local verification. The updated `run_tests.sh` includes all six test mains for the next exact checkout. Local `git hash-object` matched the fetched GitHub Git blob IDs for every newly/changed executable source tested:

- `GermanSpeechChunkPlanner.java` `742e66ef73e9e8ea14c83f53d0f2a6dcb73fd6a1`;
- `GermanSpeechChunkPlannerRegressionTest.java` `3924c9f8de3357731de53ff2002aae0b9cb0525f`;
- `VoiceAcousticTimingContract.java` `500adfa86b59181b8010402f7befcbcd9b66dac4`;
- `VoiceAcousticTimingContractTest.java` `8d7a9f6038f9c8b73af43e21f149337bc2e79bd0`;
- existing unchanged `GermanSpeechChunkPlannerTest.java` `95412cc240be68479b8ef8ddff996f9b200e34f5`.
- new runner blob `16558d5a996b38c9d5ceaef4be5f5b91f5a530d1` (full runner not locally executed during this continuation).

**New source commits:** `e0076a9140a0cf55ba3a00fb57075545e81ae7e2`, `be8e9dbad236fea40273c61a9d3d799ebaf6bce0`, `0ef4691fa925ea7c71d718cd3af1f4c588d09df5`, `75d184a5ee26320b3d82917fee8d115a0368267e`, `c207f8988ee64fd0f7a490e3168ff50e6261ca71`, `8421c5ef954f3809019908bd2a6e92a1c88382f8`. All commits are `tools/voice-lab/**` only.

**For the Build & Release worker — deferred integration handoff, NOT v83:** Do not build/integrate Voice-Lab now. A later explicitly serialized post-v83 adapter would need `VoicePlaybackHeadTracker` (fresh AudioTrack generation and unsigned wrap), measured acoustic output delay and valid `AudioTimestamp` normalization, `VoiceAcousticTimingContract` (conservative UI cursor), `VoiceLifecycleContract` (accepted PCM/engine cancellation), and per-device actual listening/lip-sync acceptance. The existing Supertonic local model is untouched and no Android APK or physical S25 evidence exists. Old Android 8 Pico OS native crashes are not a v83 gate under the user's now approved Android 10/API29 minimum decision; the build worker owns any version/minSdk change.

**Open limitations / acceptance:** Whole future Voice-Lab is still `IN_PROGRESS`. A 240-character bound is intentionally relaxed for a single unsplittable token rather than losing text; unnatural phrasing, abbreviation ambiguity, unsupported markup, drift, underrun and true live synthesis quality remain unmeasured. Neither real speaker output nor a naturally flowing German female voice is claimed.

**exact_next_action:** In `tools/voice-lab/**` only, add and run an Android-free cross-contract regression that feeds a calibrated conservative presentation cursor into the existing PCM/mouth lifecycle across stop/restart, stale timestamp and actual drain completion, then refine only a reproduced issue; preserve the future-only boundary and clean handoff.


## 29 Oct 2026 — cross-contract calibrated-mouth vs raw-drain correction (FUTURE ONLY)

**Actual reproduced RED on the source-exact original VoiceLifecycleContract blob `770212539268e8e73325fdb8d3890d390887eb51`:** after accepting 300 OPEN frames + 700 TEETH frames, the signed raw AudioTrack head reached 500 while an explicitly fixture-calibrated delay of 300 frames gave conservative mouth cursor 200. The original `playedMouth()` returned `TEETH` but acoustic presentation required `OPEN`. This is a reproducible **pure-JVM cross-contract alignment defect**, **not** an observed Samsung sound/video defect. Original source, tracker and acoustic contract were recreated in a clean local JDK harness with `git hash-object` equal to their GitHub blobs before running the failing test:

```
FAIL mouth must not lead physical presentation expected=OPEN actual=TEETH
```

**Fix in Voice-owned paths only:** `VoiceLifecycleContract` now has an optional mode `enableCalibratedPresentation(id, nowMs)` that must be selected **before the first raw playback-head advancement**. In that mode, `calibratedMouthAt(id, trustedFrame, nowMs)` uses a **fresh, externally validated, conservative** frame from `VoiceAcousticTimingContract`; cue retirement follows that frame rather than the faster raw head. The legacy raw `playedMouth()` becomes CLOSED while this mode is enabled, to avoid accidentally drawing early mouth poses. Raw `playbackHead()` remains the independent drain/completion authority; interruption, engine failure and completion retain fail-closed terminal silence. Invalid, old-generation, backwards and ahead-of-raw presentation cursors return CLOSED. The higher-level Android adapter must also show CLOSED if the acoustic timestamp contract returns -1, and must never infer a calibration automatically. This **does not** resolve physically measured final-tail latency or establish Android wiring.

**New deterministic cross-contract regression:** `VoiceCrossContractPlaybackTest.java`, 40 checks for precisely lagging visemes, sample boundaries, retention/pruning, stale hardware timestamp, physical drain, generation rollover, restart, stale callbacks and interrupt. Runner adds the seventh JVM main; no Android build/proof requested.

**Actually rerun locally using OpenJDK 21 (all listed original baseline Java test/source blobs had matching GitHub `git hash-object` IDs; modified prototype blob checked back against live GitHub):**

```
VOICE_LIFECYCLE_CONTRACT_PASS checks=62
VOICE_ADVERSARIAL_PASS checks=53059
VOICE_HEAD_TRACKER_PASS checks=35
VOICE_ACOUSTIC_TIMING_CONTRACT_PASS checks=27
VOICE_CROSS_CONTRACT_PASS checks=40
```

Total **53,223 executed** assertions on the five listed mains, including the previously recorded 53,059 adversarial replay assertions actually rerun this time. The unchanged German planner suites were **not** rerun in this iteration; the updated repository runner includes them, but no exact-full-run PASS is claimed. Precise modified GitHub blobs: `VoiceLifecycleContract.java` `125a3f1986aeac72c78f6eb60db00f9e7867af5d`, `VoiceCrossContractPlaybackTest.java` `72457a4607f57ea4b90331bb42595e6ce44aa0b2`; unchanged `VoicePlaybackHeadTracker.java` `4ab9ee3bc2e4deff3ccfe91df56f453ae8d60004`, `VoiceAcousticTimingContract.java` `500adfa86b59181b8010402f7befcbcd9b66dac4`.

**Ownership/version handoff:** Existing Voice-Lab DRAFT PR #118 is the *only* FUTURE-ONLY writer under `tools/voice-lab/**`. The approved separate Build & Release worker owns all actual v83 Android/Gradle/minSdk/signing/updater/production integration, not Room #111. This Voice-Lab work is explicitly excluded from v83 and has no installation, S25 Ultra, TTS naturalness, real PCM-to-lips, or release PASS. No `app/**`, Core #113, Room #111, CI, queue, minSdk, shared surface, signing, model or APK was modified; full device/calibration/permissions integration stays for a separate later serialized handoff.

**Known limitation:** Conservative calibrated mouth returns CLOSED after the physical raw tail is considered complete even if the estimated acoustic cursor has not caught up. This prevents stuck late mouth callbacks but does **not** prove perfectly aligned final syllables. The cue buffer remains capped at 256 entries, including when the externally calibrated cursor legitimately lags raw playback.

**exact_next_action (supersedes all earlier entries):** Within `tools/voice-lab/**` only, write and run one deterministic combined fixture for prolonged calibrated latency with the **256-cue cap** and engine restart/death; verify that no unheard cue is silently pruned and fail-stop/recovery are deterministic. Fix only a reproduced defect; then continue toward source-independent German listening-quality/real calibration requirements without asserting device acceptance.


### Same continuous Voice-Lab run — second bounded substep: calibrated long-lag cue pressure

Continued the **same** future-only assignment immediately after the first RED/FIX/PASS rather than declaring the full Voice project complete. Expanded the `VoiceCrossContractPlaybackTest` at blob `c07157989cb53571197beef9fcb36b7bcf96d509` (commit `4e2557c506f8fa99eede82ad7e7b0ed6a4a7ac19`) with two deterministic 256-distinct-cue long-lag fixtures:

- **Unsafe overflow:** physical raw playback head advances to 255, but a fixture-supplied 300-frame delay keeps the conservative display cursor at 0. All 256 unheard cues remain intact; preflight rejects cue 257 and a postwrite overflow becomes explicit `FAILED: cue backlog exceeded`, with cleanup and a safely recoverable new generation. Stale engine death is rejected; actual new-generation engine death is distinguished.
- **Safe catch-up:** fixture-supplied 200-frame lag gives a display cursor at 55; only the first 54 fully presented cue objects are retired (256 → 202), and the next distinct cue is accepted. Synthesis and actual physical raw-drain completion clear all queued cues, with no fake visual PASS.

**Reran source-matched JVM regressions** against the final GitHub test and modified lifecycle blobs, OpenJDK 21, no Android build:

```
VOICE_LIFECYCLE_CONTRACT_PASS checks=62
VOICE_ADVERSARIAL_PASS checks=53059
VOICE_HEAD_TRACKER_PASS checks=35
VOICE_ACOUSTIC_TIMING_CONTRACT_PASS checks=27
VOICE_CROSS_CONTRACT_PASS checks=585
```

**53,768 executed checks total** for these five suites. Newly modified `VoiceCrossContractPlaybackTest.java` local and GitHub blob hashes match `c07157989cb53571197beef9fcb36b7bcf96d509`; lifecycle blob remains `125a3f1986aeac72c78f6eb60db00f9e7867af5d`. German-specific tests were not rerun. All actual writes still exclusively under `tools/voice-lab/**`; future-only, no v83 integration or production changes.

**Open blocker:** no measured S25 Ultra speaker latency, natural German female voice listening result, native synthesis/AudioTrack integration or actual mouth-video comparison. External simulated latency values above are test fixtures, not real device measurements. Physical final syllable timing remains unresolved; no new effect on v83/AP29 or the old Android-8 Pico issue.

**exact_next_action (replaces the preceding one):** Add a seeded, independent acoustic-presentation-vs-raw-head frame oracle to `tools/voice-lab/**` under interrupted generations, stale timestamps and randomized partial writes. Run it with the unchanged five JVM suites; fix only a proven contract regression before making any later Android wiring proposal. Keep v83/Room/Core/build ownership strictly separate.


### Same run, third bounded substep — seeded independent calibrated-PCM oracle

Added `VoiceCalibratedOracleTest.java` to the FUTURE-ONLY Voice branch; source blob `6ca8ab807c1a47b82881305c949e29e4262c6146`, create commit `6129ddd8f0c7b477a5978b2f0a1e54ba1a1f5d43`. The independent per-frame expected-mouth oracle covers **30 sequential utterance generations × 250 step streams** with seeded randomized returned partial PCM writes, actual accepted frame records, raw head advancement, simulated (explicit) measured-delay values 0..39 frames, conservative fresh presentation timestamps, independent frame-by-frame viseme comparison, cue cap, stale/wrong-epoch observations, interruption of every third utterance and physical raw-tail completion of the rest. It is deterministic and does **not** generate, hear, or play real audio. All tests run on the actual modified lifecycle blob verified above; the new seeded fixture itself has a matching source-exact GitHub blob SHA.

**Genuine OpenJDK 21 execution in this run, locally sourced matching GitHub blobs, no Android:**

```
VOICE_LIFECYCLE_CONTRACT_PASS checks=62
VOICE_ADVERSARIAL_PASS checks=53059
VOICE_HEAD_TRACKER_PASS checks=35
VOICE_ACOUSTIC_TIMING_CONTRACT_PASS checks=27
VOICE_CROSS_CONTRACT_PASS checks=585
VOICE_CALIBRATED_ORACLE_PASS checks=64306
```

**118,074 deterministic assertions PASS** in the six listed suites; the unchanged two German planner suites were not rerun here, so this is not a claim that the complete 8-main runner was executed. Runner now includes all 8 mains and only uses a temporary local Java output directory; no CI, Android/Room/Core source/build, model asset or v83 binary altered. Changed files remain only `tools/voice-lab/**`; actual output SHA and commit should be read back before any future integration.

**Unproven:** true S25 speaker/codec latency, round-trip real presentation timestamps, end-of-stream final-syllable timing and naturally sounding German female TTS. The raw hardware head is used for terminal lifecycle completion; a delayed acoustic cursor can be behind at that moment, and the safe terminal CLOSED result may clip the last synthetic mouth cue in a real UI. This remains an *explicit acceptance gap*, not a completed sync solution. Replacing actual playback completion authority with acoustic cursor would be unsafe without measured real output evidence.

**exact_next_action (supersedes older):** In this **same Voice-Lab future-only PR** inspect and test the unresolved final-syllable display tail with a conservative Android-free latency/terminal policy fixture that never keeps an old generation alive after physical stop, and documents the tradeoff versus early-close mouth pose. Fix only a reproducible contract issue; avoid speculative real-device compensation. A future explicit exclusive Build & Release handoff is needed before **post-v83** Android adapter implementation.


## 29 Oct 2026 — final-syllable physical drain versus acoustic cursor (FUTURE ONLY)

### Genuine source-exact JVM RED and bounded FIX
Original VoiceLifecycleContract.java blob 125a3f1986aeac72c78f6eb60db00f9e7867af5d was reconstructed byte-for-byte in a clean OpenJDK 21 harness, with git hash-object equal to the live GitHub source. After 120 OPEN frames + 80 LABIAL frames, calibrated mouth at frame 100, synthesis done, and genuine raw playback-head advancement to frame 200, the lifecycle truthfully changed to COMPLETED but returned CLOSED for still-lagged presentation cursor 150. The actual pre-fix test failed:
- AssertionError: FAIL lagged final syllable must not be truncated by raw drain expected=LABIAL actual=CLOSED.

Fixed the FUTURE-ONLY VoiceLifecycleContract.java, exact GitHub/local blob 4be28a3f59174bdce848dc13f1d8c5944d998cf0. Opt-in enableBoundedPresentationTail is allowed only after calibrated mode and before raw-head motion. Physical playback completion remains COMPLETED regardless of UI catch-up. Only already accepted PCM cues may remain for up to 750ms UI-only (experimental safety cap, NOT a measured Samsung latency); a confirmed final cursor, interrupt/track stop, engine death, generation restart, timeout watchdog and failure always close the mouth and drop the retained cues. No synthesis, audio output or Android runtime was changed. Without explicit opt-in, the previously tested terminal behavior remains unchanged.

### Actual source-exact tests and commits
- VOICE_FINAL_SYLLABLE_TAIL_PASS checks=88; Java fixture blob 18268a3a9d897580e6ab8ffbf05fc01de83fc30e.
- VOICE_TAIL_ACOUSTIC_CEILING_PASS checks=26; Java fixture blob cade7420447cec17a9c8355f5af74ded2e211bdb.
- 114 fresh deterministic checks PASS on OpenJDK 21. Both executed fixtures and modified lifecycle source matched live GitHub git-blob SHA-1. Unmodified, compiled acoustic clock source also matched GitHub blob 500adfa86b59181b8010402f7befcbcd9b66dac4.
- Updated future-only run_tests.sh blob e3600a761426a030d0b0c6e6337ab2342ba4aa15 includes the two new fixtures; local sh -n syntax PASS. The full 10-main runner and historical 118,074-check suite were NOT rerun now, so their earlier counts must not be presented as fresh results.
- Actual commits: 3c58e252 (lifecycle), 72b88fa8 (final-syllable fixture), 5f5a23da (acoustic-ceiling fixture), 31b006c5 (additional fail-closed coverage), d2120d53 (runner). All changed paths are under tools/voice-lab/** only.

### Important unresolved acoustic ceiling
A genuine cross-contract test proves that the unchanged conservative VoiceAcousticTimingContract with a fixture delay of 100 frames and actual raw head=480 produces presentation cursor=380 even after 90ms. A 200ms-old timestamp becomes invalid (-1). Therefore a finite UI retention window does NOT supply missing actual speaker-presentation evidence, cannot claim that all final sounds or lips are synchronized, and must never invent final audio samples by raw-clock extrapolation. The simulated latency numbers here are not S25 Ultra measurements. No recorded real acoustic output, natural German female listening acceptance, hardware lip-sync, Android integration or APK exists from this work.

### Owner/version boundary and deferred Build handoff
Registered sole writer: Sprach-Labor DRAFT PR #118, issue #117, exclusive tools/voice-lab/**. This workstream is explicitly EXCLUDED from v83. Room PR #111, frozen Core PR #113, app/**, ci/**, AGENTS.md, Gradle, shared Queue, Android10/API29 change, signing, updater, release, GLBs, Android builds and other workers remain untouched. Only Build & Release worker ID 6ae29695b7a48191af25b982ab3606f0 may later integrate this into an Android app after a separate explicit serialized ownership transfer, post-v83. Required later evidence: trustworthy S25 Ultra speaker-delay or hardware presentation timestamps, epoch-safe AudioTrack wiring, real PCM/speaker/mouth video A/B, and actual natural German female voice evaluation.

### Handoff
Status: FUTURE-ONLY VOICE WORKSTREAM STILL IN_PROGRESS. No unresolved GitHub write or running Voice-specific test was intentionally left by this iteration. Physical final-syllable timestamping and natural voice quality remain unproven; Android-8 Pico crashes are separate and not an Android10 v83 gate.
**exact_next_action:** Inside tools/voice-lab/** only, create and test a strictly externally evidence-gated post-drain presentation clock that refuses to advance beyond observed true speaker/presentation frame evidence; demonstrate stale-generation/clock-reset/reuse rejection and document the irreducible missing-evidence case before any post-v83 adapter handoff.


### Same continued Voice-Lab work — independent externally confirmed speaker cursor, NOT Android

Immediately after the UI-tail RED/FIX/PASS, the same Voice-Lab assignment advanced to the next real, independent problem: the raw AudioTrack head with fixed delay cannot prove the missing final captured acoustic frames. Implemented pure-Java VoiceVerifiedPresentationClock.java **blob 73fe8081685221532c4c60b67a935e48e929fcda**, strictly requiring an explicit EXTERNAL_CAPTURE measurement tagged to the current generation, externally supplied accepted/raw/presented frame counts, monotonic capture/poll clocks, bounded freshness (200ms) and plausible sample-rate progression. It never increments the cursor merely from elapsed wall time; RAW_HEAD_ONLY is rejected, no capture means result -1, and conflicting monotonic rollback poisons the generation until an entirely new begin. A source-proven bug in the first local iteration was fixed: invalid current evidence cleared the displayed sample but also disabled the monotonic baseline check, allowing a later same-generation lower frame; the final implementation retains hasBaseline separately from hasEvidence, so rollback after a rejected observation permanently fails closed until next generation. These are synthetic evidence contracts, not evidence that an actual Samsung capture implementation exists.

Added VoiceVerifiedPresentationClockTest.java **blob ee5e4a229fc74c26205596cca2b3a9785d2152be**, with 39 executed OpenJDK-21 deterministic assertions covering explicit captured-frame progress from 380 to 420 to 480 despite a physically drained raw head of 480, no extrapolation without new captured data, 200ms expiry, wrong epochs, stop, impossible frames, proof-kind rejection, same-timestamp advancement, clock rollback and baseline preservation after invalid data. Both local Java source git-blob SHA-1 values matched exact live GitHub file content, and the directly run JVM emitted:
- VOICE_FINAL_SYLLABLE_TAIL_PASS checks=88
- VOICE_TAIL_ACOUSTIC_CEILING_PASS checks=26
- VOICE_VERIFIED_PRESENTATION_CLOCK_PASS checks=39
- **153 actual freshly executed checks PASS** (three mains, compiled together with exact-source existing VoiceLifecycleContract and VoiceAcousticTimingContract). No claims about previous untouched suites being rerun.
- Future-only run_tests.sh blob 397402d511ba876533cd03f29ead5e3c94039ec9 includes all three new fixtures; shell syntax PASS. Full 11-main runner was not executed in this environment because unrelated older source/test files were not materialized. The prior 118,074-check benchmark remains historic, not newly validated in this specific run.

**Integration safety limitation:** EvidenceKind.EXTERNAL_CAPTURE is a typed *claim made by the future adapter*, NOT cryptographic or physical validation. Until a real S25 Ultra capture pipeline provides genuine measured presented PCM/timestamps and binds them to the correct AudioTrack generation, no physical cue or final-syllable audio-vs-mouth timing can be claimed PASS. The original bounded UI-only 750ms tail may expire if no fresh external evidence exists. A real implementation must fail closed then, not guess. No modification to real TTS models, v83/Core/Room/build, Android API29, signers, update application or releases.

**Build-worker handoff (post-v83 ONLY):** Do not integrate PR #118 into v83. For a later explicit exclusive serialized app integration, the Build & Release owner needs trustworthy external audio-capture provenance and normalized Android AudioTimestamp/AudioTrack PCM-head evidence, physical speaker/visible-mouth recordings and natural German female voice listening tests before a real integration PASS. Voice-domain writer keeps exclusive tools/voice-lab/** ownership; app/**, ci/**, shared runtime and releases are out of scope.

**exact_next_action (supersedes earlier):** In this SAME FUTURE-ONLY workstream, add a deterministic multi-generation adversarial replay comparing VoiceVerifiedPresentationClock accepted external-proof events with an independent pure-Java reference model, focusing on loss/recovery of evidence, poisoned epoch, max timestamp arithmetic and track reset. Correct only a reproduced Java defect, then perform source-exact targeted JVM validation; do not change app, CI, Android or v83 release state.


## 29 Oct 2026 — German streaming continuity, FUTURE-ONLY, same Voice-Lab writer

Baseline Voice #118 e4dc1ac22d2954faf0e3c567f118625e720b60ac; live main 5a23ccf75fb82b97d262cf3d8be24a6fe77fb26b. Exclusive tools/voice-lab/**; separated from Build & Release, Room #111 and Core #113. Only the two new pure Java files, the Voice-only runner and this work-order are in the bounded update.

New GermanStreamingDeliveryLedger consumes the unchanged source-exact GermanSpeechChunkPlanner plan and issues at most one generation-bound chunk ticket. Synthesis-ended does NOT count as actual physical playback drain. An in-flight stop is UNCERTAIN, old callbacks cannot acknowledge it, and automatic replay/skip is blocked. Restart requires new generation plus explicit external SILENT or FULLY_DRAINED evidence claim, not inferred by this JVM. Caller must prove the chosen policy from actual output before any future integration; partial heard words are inherently ambiguous.

Exact local OpenJDK validation: pre-existing planner blob 742e66ef73e9e8ea14c83f53d0f2a6dcb73fd6a1; new ledger blob dbaaf76fbf38f95add351b23678bc504dec36f51 and new test blob dc9df5c81671039aa532c001d6898ee8396d6e25 matched local git hash-object. Compiled these three Java files and executed GermanStreamingDeliveryLedgerTest: VOICE_GERMAN_STREAM_LEDGER_PASS checks=5842, including 200 seeded generations, German punctuation/abbreviations/date/URL/email/long-token cases, real-drain-vs-synthesis distinction, stale callbacks and independent normalized transcript continuity oracle. Mutation negative control (remove restart epoch increment in a TEMPORARY local copy) actually returned FAIL new generation on replay, exit=1; original restored and all 5,842 checks PASS. This is a regression sensitivity test, not a reproduced production device bug. Full historical runner and prior 27,477 checks not rerun in this iteration. Runner now includes this new Java main.

This remains in-progress, unintegrated, not v83, with NO Android build/manual workflow, real speaker capture, female German listening acceptance, S25 hardware or physical lipsync PASS. Android-8 Pico OS crash is separate and is not a minSdk29 v83 gate. Future Android wiring requires serialized permission from the dedicated Build & Release owner, outside Voice-owned paths.

exact_next_action: In tools/voice-lab/** only, independently cross-check chunk acknowledgement against VoiceLifecycleContract physical drain and engine-death/interruption under stale-generation races; correct only a reproduced JVM issue and leave an exact SHA/test handoff for post-v83 integration. Never touch app/CI/Room/Core/Gradle/updater/release.


## 29 Oct 2026 — SOURCE-EXACT cross-contract German raw-drain gate (post-v83 only)

**Live baseline:** main `5a23ccf75fb82b97d262cf3d8be24a6fe77fb26b`; Voice DRAFT PR #118 `1379501b8f15ac76158b888e8ee8327f35c0fde6` (before this new commit); sole owned path tools/voice-lab/**. Explicitly excluded from v83 by #117, independently of the approved Build & Release owner. Room #111, Core #113, Android APIs/Gradle/CI/app/queue/update/release remain untouched.

**Proven cross-contract hazard (not an Android production claim):** With exactly fetched Git blobs GermanSpeechChunkPlanner.java `742e66ef73e9e8ea14c83f53d0f2a6dcb73fd6a1`, GermanStreamingDeliveryLedger.java `dbaaf76fbf38f95add351b23678bc504dec36f51`, VoiceLifecycleContract.java `4be28a3f59174bdce848dc13f1d8c5944d998cf0`, a deliberately unsafe direct adapter could call ledger.playbackDrained(ticket) after synthesis-ended while the audio phase was still DRAINING and raw playedFrames=0/160. The temporary isolated negative-control Java main really failed with AssertionError `FAIL direct ledger permits advancing before raw AudioTrack head drains` (exit 1). This is an integration-contract misuse demonstrated on JVM, not proof the actual Android app does it.

**Bounded new implementation:** GermanStreamingRawDrainCoordinator ties plan chunk and audio generation to an immutable dispatch identity, forwards actual accepted PCM counts and observed monotonic raw playback head to the existing lifecycle, and acknowledges the next text chunk only after BOTH synthesis ended and same-generation lifecycle COMPLETED with positive accepted frames and playedFrames==acceptedFrames. Engine death, interruption, prepare/playback timeout, stale old tickets and impossible head jumps cannot silently confirm text. Audibility on interruption remains UNCERTAIN and needs a separate externally supported SILENT/FULL-DRAINED policy; a typed enum is not real external evidence.

**Genuinely executed local OpenJDK 21 targeted validation** (the 3 unchanged source blobs above match fetched GitHub SHA-1 exactly; newly created Java blobs match locally executed bytes):
`VOICE_GERMAN_RAW_DRAIN_COORDINATOR_PASS checks=3422`. Scenarios: partial PCM acceptance, head one frame short, overflow/unwritten-head rejection, synthesis-before/after-drain ordering, TTS kill, stale-generation callbacks, replay/advance only with explicit external decisions, preparation/drain watchdog, 100 seeded multi-chunk plans with an independent normalized transcript prefix. The intentionally unsafe direct adapter failed before the bounded coordinator passed. The full historical run_tests.sh suite was NOT executed in this continuation. The runner includes the new Java main for subsequent full regression.

**Boundary and unresolved FAIL:** Raw AudioTrack head completion is NOT a measured external acoustic-speaker-output event or guaranteed final syllable heard. A future adapter could still start a following chunk before delayed speaker presentation/tail concludes, especially if its audio tracks differ. No S25 Ultra speaker capture/lag, German natural female voice listening, Android wiring or physical lipsync PASS exists. No Android-8 Pico OS failure is promoted to an Android10 v83 gate.

**Ownership/readback:** single Voice-Lab DRAFT writer retains only tools/voice-lab/** on the existing workstream; release worker ID 6ae29695b7a48191af25b982ab3606f0 alone owns post-handoff Android integration. This source remains FUTURE-ONLY and is not an integration-ready product.

**exact_next_action (supersedes earlier):** Within tools/voice-lab/** only, design a separate externally captured speaker-tail acknowledgement gate for advancing the *next* spoken chunk; add deterministic negative tests for missing/expired/stale/poisoned physical presentation evidence, and refuse auto-advance if real audible completion is unprovable. Do not change v83, app/CI/shared files or invent device measurement.
