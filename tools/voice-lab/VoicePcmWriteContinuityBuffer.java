import java.util.Arrays;

/**
 * FUTURE-ONLY pure-Java pre-AudioTrack PCM FIFO. It does NOT synthesize voice,
 * prove that a device accepted frames, or establish acoustic playback.
 * A future serialized adapter must pass the REAL returned AudioTrack.write frame
 * count to commitWritten(), keeping a write lease exclusive until that return.
 */
public final class VoicePcmWriteContinuityBuffer {
    public enum State { IDLE, PRIMING, READY, DRAINING, COMPLETED, INTERRUPTED, FAILED }

    public static final class WriteLease {
        public final long epoch;
        public final int frames;
        public final float[] interleavedPcm;
        private WriteLease(long epoch, int frames, float[] pcm) {
            this.epoch = epoch;
            this.frames = frames;
            this.interleavedPcm = pcm;
        }
    }

    private final int channels, capacityFrames, primeFrames;
    private final long maxIdleMs;
    private final float[] ring;
    private int head, queued, underruns;
    private long epoch, lastEventMs, lastProgressMs;
    private boolean synthesisDone, primed;
    private State state = State.IDLE;
    private WriteLease pending;

    public VoicePcmWriteContinuityBuffer(int channels, int capacityFrames,
            int primeFrames, long maxIdleMs) {
        if (channels < 1 || channels > 8 || capacityFrames < 2
                || capacityFrames > 1_000_000 || primeFrames < 1
                || primeFrames > capacityFrames || maxIdleMs <= 0
                || (long) capacityFrames * channels > Integer.MAX_VALUE)
            throw new IllegalArgumentException("invalid PCM queue contract");
        this.channels = channels;
        this.capacityFrames = capacityFrames;
        this.primeFrames = primeFrames;
        this.maxIdleMs = maxIdleMs;
        ring = new float[channels * capacityFrames];
    }

    public synchronized long begin(long nowMs) {
        if (nowMs < 0 || epoch == Long.MAX_VALUE)
            throw new IllegalArgumentException("invalid epoch/clock");
        // Never replace an active/unverified stream. A later adapter must first
        // establish an independently safe STOP and call interrupt()/engineDied().
        if (state == State.PRIMING || state == State.READY || state == State.DRAINING)
            throw new IllegalStateException("previous PCM output unresolved");
        epoch++;
        head = queued = underruns = 0;
        pending = null;
        synthesisDone = primed = false;
        state = State.PRIMING;
        lastEventMs = lastProgressMs = nowMs;
        return epoch;
    }

    private boolean current(long id, long ms) {
        if (id != epoch || state == State.IDLE || state == State.INTERRUPTED
                || state == State.FAILED || state == State.COMPLETED) return false;
        if (ms < lastEventMs || ms < lastProgressMs || ms - lastProgressMs > maxIdleMs) {
            clear(State.FAILED);
            return false;
        }
        lastEventMs = ms;
        return true;
    }

    private void clear(State reason) {
        head = queued = 0;
        pending = null;
        state = reason;
    }

    /** Return ACTUAL queued input frames, never silently pretend full acceptance. */
    public synchronized int offer(long id, float[] samples, int offsetFrames,
            int requestedFrames, long nowMs) {
        if (!current(id, nowMs) || synthesisDone || samples == null || offsetFrames < 0
                || requestedFrames < 0 || offsetFrames > samples.length / channels
                || requestedFrames > samples.length / channels - offsetFrames
                || samples.length % channels != 0) return -1;
        // Scan all caller-supplied frames before any mutation, even when backpressured.
        int offset = offsetFrames * channels;
        for (int p = 0; p < requestedFrames * channels; p++) {
            if (!Float.isFinite(samples[offset + p])) {
                clear(State.FAILED);
                return -1;
            }
        }
        int accepted = Math.min(requestedFrames, capacityFrames - queued);
        for (int f = 0; f < accepted; f++) {
            int destination = ((head + queued + f) % capacityFrames) * channels;
            System.arraycopy(samples, offset + f * channels, ring, destination, channels);
        }
        queued += accepted;
        if (accepted > 0) lastProgressMs = nowMs;
        if (!primed && queued >= primeFrames) {
            primed = true;
            state = State.READY;
        }
        return accepted;
    }

    /** Snapshot ONLY: no bytes are removed until the real AudioTrack return is committed. */
    public synchronized WriteLease lease(long id, int desiredFrames, long nowMs) {
        if (!current(id, nowMs) || desiredFrames <= 0 || pending != null) return null;
        if (!primed && !synthesisDone) return null;
        if (queued == 0) {
            if (synthesisDone) state = State.COMPLETED;
            else if (primed) underruns++;
            return null;
        }
        int frames = Math.min(desiredFrames, queued);
        float[] copy = new float[frames * channels];
        for (int f = 0; f < frames; f++) {
            System.arraycopy(ring, ((head + f) % capacityFrames) * channels,
                    copy, f * channels, channels);
        }
        pending = new WriteLease(id, frames, copy);
        return pending;
    }

    /** Returns false on an old/stale/invalid result: it never advances the queue. */
    public synchronized boolean commitWritten(WriteLease lease, int acceptedFrames, long nowMs) {
        if (lease == null || lease != pending || !current(lease.epoch, nowMs))
            return false;
        if (acceptedFrames < 0 || acceptedFrames > lease.frames || acceptedFrames > queued) {
            // An impossible AudioTrack return cannot be retried as if unconsumed.
            clear(State.FAILED);
            return false;
        }
        head = (head + acceptedFrames) % capacityFrames;
        queued -= acceptedFrames;
        if (acceptedFrames > 0) lastProgressMs = nowMs;
        pending = null;
        if (synthesisDone && queued == 0) state = State.COMPLETED;
        return true;
    }

    public synchronized boolean synthesisEnded(long id, long nowMs) {
        if (!current(id, nowMs) || synthesisDone) return false;
        synthesisDone = true;
        state = queued == 0 && pending == null ? State.COMPLETED : State.DRAINING;
        return true;
    }

    public synchronized boolean tick(long id, long nowMs) { return current(id, nowMs); }

    /** An interruption invalidates every uncommitted sample and outstanding write lease. */
    public synchronized boolean interrupt(long id) {
        if (id != epoch || state == State.IDLE || state == State.INTERRUPTED
                || state == State.FAILED || state == State.COMPLETED) return false;
        clear(State.INTERRUPTED);
        return true;
    }

    public synchronized boolean engineDied(long id) {
        if (id != epoch || state == State.IDLE || state == State.INTERRUPTED
                || state == State.FAILED || state == State.COMPLETED) return false;
        clear(State.FAILED);
        return true;
    }

    public synchronized State state() { return state; }
    public synchronized int queuedFrames() { return queued; }
    public synchronized int underruns() { return underruns; }
    public synchronized long epoch() { return epoch; }
    public synchronized int channels() { return channels; }
}
