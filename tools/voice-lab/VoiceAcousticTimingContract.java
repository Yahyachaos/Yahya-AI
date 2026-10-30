/**
 * FUTURE ONLY: pure-Java conservative cursor for post-device-calibrated mouth timing.
 * Accepts the RELATIVE, unwrapped track head from VoicePlaybackHeadTracker and a
 * monotonic hardware timestamp. This class neither measures output latency nor
 * guarantees a speaker/microphone presentation instant: a physical S25 test must
 * supply a valid measuredDelayFrames before the cursor can become available.
 * No extrapolation beyond frames actually observed as played is permitted.
 */
public final class VoiceAcousticTimingContract {
    private static final long FRESH_NS = 200_000_000L;
    private long generation;
    private int sampleRate;
    private long measuredDelayFrames;
    private long headFrames;
    // AudioTrack accepted-PCM count is cumulative within one output generation.
    private long acceptedFrames;
    private long sourceTimestampNs;
    private long pollTimestampNs;
    private boolean calibrated;
    private boolean hasObservation;
    // Preserve the last accepted baseline even after a malformed sample closes UI.
    // A real same-epoch head/clock reset invalidates this audio generation entirely.
    private boolean hasBaseline;
    // Plausible clock samples advance a provenance high-water mark even if
    // their frame fields are rejected. A delayed older sample cannot revive
    // a mouth cursor after that newer (malformed) hardware observation.
    private boolean hasPlausibleClock;
    private long highestSourceNs;
    private long highestPollNs;
    // After invalid-but-plausibly-timestamped evidence, an altered report at the
    // same clock is not an independent capture and cannot reopen the mouth.
    private boolean rejectedPlausibleClock;
    private boolean poisoned;

    public synchronized void begin(long generation, int sampleRate) {
        if (generation <= this.generation || sampleRate < 8000 || sampleRate > 192000) {
            throw new IllegalArgumentException("fresh stream generation and valid sample rate required");
        }
        this.generation = generation;
        this.sampleRate = sampleRate;
        this.measuredDelayFrames = 0;
        this.headFrames = 0;
        this.acceptedFrames = 0;
        this.sourceTimestampNs = 0;
        this.pollTimestampNs = 0;
        this.calibrated = false;
        this.hasObservation = false;
        this.hasBaseline = false;
        this.hasPlausibleClock = false;
        this.highestSourceNs = 0;
        this.highestPollNs = 0;
        this.rejectedPlausibleClock = false;
        this.poisoned = false;
    }

    /**
     * Delay is NOT guessed. Accept only a bounded external device/loopback result.
     * Zero is legal if measured; it must not be used as an unmeasured default.
     */
    public synchronized boolean acceptMeasuredDelay(long id, long measuredFrames) {
        if (id != generation || id <= 0 || calibrated || poisoned || measuredFrames < 0
                || measuredFrames > sampleRate / 2L) return false;
        measuredDelayFrames = measuredFrames;
        calibrated = true;
        hasObservation = false; // a new calibration invalidates the old sample
        hasBaseline = false;
        acceptedFrames = 0;
        hasPlausibleClock = false;
        highestSourceNs = highestPollNs = 0;
        rejectedPlausibleClock = false;
        return true;
    }

    /**
     * A rejected current-generation sample MUST close the visual cursor immediately.
     * True rollback in the hardware head/clock poisons the generation; a transient
     * malformed sample may recover only against the last trusted monotonic baseline.
     * Old-generation callbacks do not affect the currently trusted sample.
     */
    public synchronized boolean observe(long id, long head, long accepted,
                                         long sourceNs, long pollNs) {
        if (!calibrated || id != generation || poisoned) return false;
        if (hasBaseline && (head < headFrames || sourceNs < sourceTimestampNs
                || pollNs < pollTimestampNs)) {
            hasObservation = false;
            poisoned = true;
            return false;
        }
        // A timestamp-shaped observation is chronologically significant even
        // when its head/accepted-frame fields are invalid. Do not treat it as
        // valid audio, but do not later accept evidence from before its clock.
        // Baseline rollbacks above remain terminal; speculative invalid samples
        // only close presentation until a fresh monotonic observation arrives.
        boolean plausibleClock = sourceNs >= 0 && pollNs >= sourceNs
                && pollNs - sourceNs <= FRESH_NS;
        if (plausibleClock) {
            if (hasPlausibleClock && (sourceNs < highestSourceNs || pollNs < highestPollNs
                    || (rejectedPlausibleClock
                        && (sourceNs <= highestSourceNs || pollNs <= highestPollNs)))) {
                hasObservation = false;
                return false;
            }
            hasPlausibleClock = true;
            highestSourceNs = sourceNs;
            highestPollNs = pollNs;
        }
        if (head < 0 || accepted < head || sourceNs < 0 || pollNs < sourceNs
                || pollNs - sourceNs > FRESH_NS) {
            hasObservation = false;
            if (plausibleClock && hasBaseline) rejectedPlausibleClock = true;
            return false;
        }
        // A well-formed cumulative PCM count cannot rewind within the same
        // AudioTrack generation. A malformed head-ahead-of-accepted sample was
        // already quarantined above and remains recoverable against the baseline.
        if (hasBaseline && accepted < acceptedFrames) {
            hasObservation = false;
            poisoned = true;
            return false;
        }
        if (hasBaseline) {
            // A fixed timestamp cannot justify an advanced sample position.
            if (head > headFrames && sourceNs == sourceTimestampNs) {
                hasObservation = false;
                if (plausibleClock && hasBaseline) rejectedPlausibleClock = true;
                return false;
            }
            long elapsedNs = sourceNs - sourceTimestampNs;
            // Allow at most a 200ms scheduling/poll tolerance beyond the reported interval.
            long wholeMs = elapsedNs / 1_000_000L;
            long seconds = wholeMs / 1000L;
            long base = seconds > Long.MAX_VALUE / sampleRate
                    ? Long.MAX_VALUE : seconds * sampleRate;
            long extra = (wholeMs % 1000L) * sampleRate / 1000L + sampleRate / 5L;
            long permitted = Long.MAX_VALUE - base < extra ? Long.MAX_VALUE : base + extra;
            if (head - headFrames > permitted) {
                hasObservation = false;
                if (plausibleClock && hasBaseline) rejectedPlausibleClock = true;
                return false;
            }
        }
        rejectedPlausibleClock = false;
        headFrames = head;
        acceptedFrames = accepted;
        sourceTimestampNs = sourceNs;
        pollTimestampNs = pollNs;
        hasObservation = hasBaseline = true;
        return true;
    }

    /** -1 means no safe calibrated estimate; otherwise a non-leading frame cursor. */
    public synchronized long estimatedMouthFrame(long id, long nowNs) {
        if (!calibrated || !hasObservation || id != generation || nowNs < pollTimestampNs
                || nowNs - pollTimestampNs > FRESH_NS
                || nowNs < sourceTimestampNs || nowNs - sourceTimestampNs > FRESH_NS) return -1;
        return Math.max(0L, headFrames - measuredDelayFrames);
    }

    public synchronized void stop(long id) {
        if (generation == id) {
            calibrated = false;
            hasObservation = false;
            poisoned = true; // terminal: a stopped track may only restart under begin(newId)
        }
    }
}
