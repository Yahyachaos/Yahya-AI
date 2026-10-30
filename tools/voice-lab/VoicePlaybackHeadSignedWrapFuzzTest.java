/**
 * FUTURE-ONLY pure JVM seeded checks for AudioTrack signed 32-bit head wraps.
 * An independent unsigned modulo oracle, not an Android acoustic or device test.
 */
public final class VoicePlaybackHeadSignedWrapFuzzTest {
    private static int assertions;
    private static void require(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
        assertions++;
    }
    private static long next(long x) {
        x ^= x << 13;
        x ^= x >>> 7;
        return x ^ (x << 17);
    }
    public static void main(String[] args) {
        long seed = 0x4D15F10B3A2C5E7DL;
        int wrapEvents = 0;
        for (int run = 0; run < 1000; run++) {
            seed = next(seed);
            long id = run + 1L;
            long raw = 0xfffff000L + (seed & 0xfffL);
            raw &= 0xffffffffL;
            VoicePlaybackHeadTracker subject = new VoicePlaybackHeadTracker();
            subject.begin(id, (int) raw);
            require(subject.playedFrames() == 0L, "initial zero " + run);
            require(subject.observe(id, (int)raw, 0) == 0, "initial sample " + run);
            long oracle = 0L;
            for (int step = 0; step < 128; step++) {
                seed = next(seed);
                long delta = 1 + ((seed >>> 11) & 0x1fffL);
                long previous = raw;
                long future = (previous + delta) & 0xffffffffL;
                if (future < previous) wrapEvents++;
                long accepted = oracle + delta + (step % 7);
                require(subject.observe(id + 1, (int) future, accepted) == -1,
                        "wrong generation " + run + ":" + step);
                require(subject.observe(id, (int)future, oracle + delta - 1) == -1,
                        "before PCM acceptance " + run + ":" + step);
                require(subject.playedFrames() == oracle,
                        "unsafe samples must not mutate " + run + ":" + step);
                require(subject.observe(id, (int)future, accepted) == oracle + delta,
                        "unsigned wrap advancement " + run + ":" + step);
                raw = future;
                oracle += delta;
                require(subject.playedFrames() == oracle,
                        "oracle cursor " + run + ":" + step);
                require(subject.observe(id, (int)raw, accepted) == oracle,
                        "same-frame repoll " + run + ":" + step);
                if (step % 13 == 0) {
                    long backwards = (raw - 1L) & 0xffffffffL;
                    require(subject.observe(id, (int)backwards, accepted + 100) == -1,
                            "backward jitter cannot be large wrap " + run + ":" + step);
                    require(subject.observe(id, (int)raw, accepted) == oracle,
                            "backward jitter leaves cursor stable " + run + ":" + step);
                }
            }
            require(oracle > 0, "actual oracle progressed " + run);
            subject.stop(id - 1);
            require(subject.observe(id, (int)raw, oracle) == oracle,
                    "wrong STOP does not stop active generation " + run);
            subject.stop(id);
            require(subject.observe(id, (int)raw, oracle) == -1,
                    "stopped stream must be silent " + run);
            boolean duplicateBeginRejected = false;
            try { subject.begin(id, (int)raw); }
            catch (IllegalArgumentException e) { duplicateBeginRejected = true; }
            require(duplicateBeginRejected, "stopped generation cannot reuse ID " + run);
            subject.begin(id + 1, (int)raw);
            require(subject.observe(id, (int)raw, oracle) == -1,
                    "old callback rejected after restart " + run);
            require(subject.observe(id + 1, (int)raw, 0) == 0,
                    "new stream must rebase raw counter " + run);
        }
        require(wrapEvents >= 900, "sufficient real 32-bit wrap boundaries covered: " + wrapEvents);
        System.out.println("VOICE_PLAYBACK_HEAD_SIGNED_WRAP_FUZZ_PASS checks=" + assertions
                + " wrapEvents=" + wrapEvents);
    }
}
