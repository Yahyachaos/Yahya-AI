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

    public synchronized void begin(long generation, int sampleRate) {
        if (generation <= 0 || sampleRate < 8000 || sampleRate > 192000) {
            throw new IllegalArgumentException("invalid stream identity or sample rate");
        }
        this.generation = generation;
        this.sampleRate = sampleRate;
        this.measuredDelayFrames = 0;
        this.headFrames = 0;
        this.sourceTimestampNs = 0;
        this.pollTimestampNs = 0;
        this.calibrated = false;
        this.hasObservation = false;
    }

    /**
     * Delay is NOT guessed. Accept only a bounded external device/loopback result.
     * Zero is legal if measured; it must not be used as an unmeasured default.
     */
    public synchronized boolean acceptMeasuredDelay(long id, long measuredFrames) {
        if (id != generation || id <= 0 || measuredFrames < 0
                || measuredFrames > sampleRate / 2L) return false;
        measuredDelayFrames = measuredFrames;
        calibrated = true;
        hasObservation = false; // a new calibration invalidates the old sample
        return true;
    }

    /** Reject late, stale, future, nonmonotonic or unaccepted sample positions. */
    public synchronized boolean observe(long id, long head, long accepted,
                                         long sourceNs, long pollNs) {
        if (!calibrated || id != generation || head < 0 || accepted < head
                || sourceNs < 0 || pollNs < sourceNs || pollNs - sourceNs > FRESH_NS) return false;
        if (hasObservation) {
            if (head < headFrames || sourceNs < sourceTimestampNs || pollNs < pollTimestampNs) return false;
            // A fixed timestamp cannot justify an advanced sample position.
            if (head > headFrames && sourceNs == sourceTimestampNs) return false;
            long elapsedNs = sourceNs - sourceTimestampNs;
            // Allow at most a 200ms scheduling/poll tolerance beyond the reported interval.
            long permitted = (elapsedNs / 1_000_000L) * sampleRate / 1000L
                    + sampleRate / 5L;
            if (head - headFrames > permitted) return false;
        }
        headFrames = head;
        sourceTimestampNs = sourceNs;
        pollTimestampNs = pollNs;
        hasObservation = true;
        return true;
    }

    /** -1 means no safe calibrated estimate; otherwise a non-leading frame cursor. */
    public synchronized long estimatedMouthFrame(long id, long nowNs) {
        if (!calibrated || !hasObservation || id != generation || nowNs < pollTimestampNs
                || nowNs - pollTimestampNs > FRESH_NS) return -1;
        return Math.max(0L, headFrames - measuredDelayFrames);
    }

    public synchronized void stop(long id) {
        if (generation == id) {
            calibrated = false;
            hasObservation = false;
        }
    }
}
