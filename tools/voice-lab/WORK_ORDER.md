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

**exact_next_action:** Add an isolated, deterministic adversarial callback-sequence/replay fixture under `tools/voice-lab/**` that stresses mixed engine callbacks, partial `AudioTrack.write` acceptance, head jitter and cancellation over hundreds of synthetic events; establish a bounded-cue-retention policy before any production wiring. Keep v83 entirely untouched.
