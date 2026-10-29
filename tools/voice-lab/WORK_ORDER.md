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
