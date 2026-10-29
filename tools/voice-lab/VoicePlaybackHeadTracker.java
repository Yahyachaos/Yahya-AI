/**
 * FUTURE-ONLY Android-free adapter for AudioTrack's signed int (unsigned 32-bit)
 * playback-head counter. Maps observations into one generation's relative 64-bit
 * frame count, rejecting stale tracks, backward/jittery samples, implausibly large
 * jumps and positions ahead of PCM that AudioTrack has accepted.
 *
 * Assumptions: the physical AudioTrack head belongs exclusively to this generation,
 * the first raw sample is taken at playback start, the raw counter is sampled before
 * 2^31 frames elapse, and playback is not allowed to reset the raw counter in-place.
 * A fresh/rebuilt track requires a fresh begin(). No audio latency calibration or
 * device timestamp measurement is claimed here.
 */
public final class VoicePlaybackHeadTracker {
    private static final long MODULO_MASK = 0xffffffffL;
    private static final long HALF_WRAP = 0x80000000L;

    private long generation;
    private long unsignedRaw;
    private long playedFrames;
    private boolean started;

    /** Supply the first raw value from the actual new AudioTrack. */
    public synchronized void begin(long id, int initialSignedRaw) {
        if (id <= 0) throw new IllegalArgumentException("invalid generation");
        generation = id;
        unsignedRaw = Integer.toUnsignedLong(initialSignedRaw);
        playedFrames = 0L;
        started = true;
    }

    /** Returns relative emitted frames, or -1 when the observation is unsafe. */
    public synchronized long observe(long id, int signedRaw, long maxAcceptedFrames) {
        if (!started || id != generation || maxAcceptedFrames < playedFrames) return -1L;
        long nextRaw = Integer.toUnsignedLong(signedRaw);
        long delta = (nextRaw - unsignedRaw) & MODULO_MASK;
        // Half-wrap ambiguity is intentionally rejected; a backwards 1-frame jitter
        // looks like 0xffffffff in modular arithmetic, not a 4 billion frame leap.
        if (delta >= HALF_WRAP || delta > maxAcceptedFrames - playedFrames) return -1L;
        playedFrames += delta;
        unsignedRaw = nextRaw;
        return playedFrames;
    }

    public synchronized long playedFrames() { return playedFrames; }
    public synchronized long generation() { return generation; }

    /** Invalidate all callbacks from a released or interrupted AudioTrack. */
    public synchronized void stop(long id) {
        if (started && id == generation) started = false;
    }
}
