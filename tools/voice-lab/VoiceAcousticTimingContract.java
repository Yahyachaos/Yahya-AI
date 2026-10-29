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
    private long sourceTimestampNs;
    private long pollTimestampNs;
    private boolean calibrated;
    private boolean hasObservation;
    // Preserve the last accepted baseline even after a malformed sample closes UI.
    // A real same-epoch head/clock reset invalidates this audio generation entirely.
    private boolean hasBaseline;
    private boolean poisoned;

    public synchronized void begin(long generation, int sampleRate) {
        if (generation <= this.generation || sampleRate < 8000 || sampleRate > 192000) {
            throw new IllegalArgumentException("fresh stream generation and valid sample rate required");
        }
        this.generation = generation;
        this.sampleRate = sampleRate;
        this.measuredDelayFrames = 0;
        this.headFrames = 0;
        this.sourceTimestampNs = 0;
        this.pollTimestampNs = 0;
        this.calibrated = false;
        this.hasObservation = false;
        this.hasBaseline = false;
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
        if (head < 0 || accepted < head || sourceNs < 0 || pollNs < sourceNs
                || pollNs - sourceNs > FRESH_NS) {
            hasObservation = false;
            return false;
        }
        if (hasBaseline) {
            // A fixed timestamp cannot justify an advanced sample position.
            if (head > headFrames && sourceNs == sourceTimestampNs) {
                hasObservation = false;
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
                return false;
            }
        }
        headFrames = head;
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
