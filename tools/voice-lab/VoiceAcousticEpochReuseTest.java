/** FUTURE-ONLY synthetic callback-epoch safety; NOT real speaker/lip-sync proof. */
public final class VoiceAcousticEpochReuseTest {
    private static int checks;
    private static void check(boolean valid, String why) {
        if (!valid) throw new AssertionError(why);
        checks++;
    }
    private static void rejectBegin(VoiceAcousticTimingContract clock, long generation) {
        boolean rejected = false;
        try { clock.begin(generation, 48000); }
        catch (IllegalArgumentException | IllegalStateException expected) { rejected = true; }
        check(rejected, "must not reuse or rewind an acoustic playback generation " + generation);
    }
    public static void main(String[] args) {
        VoiceAcousticTimingContract clock = new VoiceAcousticTimingContract();
        final long t = 1_000_000_000L;
        clock.begin(5, 48000);
        check(clock.acceptMeasuredDelay(5, 480), "calibrated old lease");
        check(clock.observe(5, 1200, 1200, t, t + 1_000_000L), "old playback sample");
        check(clock.estimatedMouthFrame(5, t + 2_000_000L) == 720, "old cue established");
        rejectBegin(clock, 5);
        rejectBegin(clock, 4);
        check(clock.estimatedMouthFrame(5, t + 3_000_000L) == 720,
                "rejected reset must not discard or revive prior observed evidence");
        clock.begin(6, 48000);
        check(clock.estimatedMouthFrame(6, t + 4_000_000L) == -1,
                "a new generation cannot inherit prior calibration");
        check(!clock.observe(5, 1400, 1400, t + 5_000_000L, t + 6_000_000L),
                "stale callback from previous generation refused");
        clock.stop(5);
        check(clock.acceptMeasuredDelay(6, 0), "new generation independently calibrated");
        check(clock.observe(6, 150, 150, t + 7_000_000L, t + 8_000_000L),
                "new generation fresh sample");
        check(clock.estimatedMouthFrame(6, t + 9_000_000L) == 150,
                "only new epoch's measured sound controls mouth");
        for (long i = 7; i < 207; i++) {
            clock.begin(i, 48000);
            rejectBegin(clock, i);
            rejectBegin(clock, i - 1);
            check(clock.acceptMeasuredDelay(i, 0), "current epoch calibratable");
            check(!clock.observe(i - 1, 1, 1, t, t), "previous epoch cannot provide head");
            check(clock.observe(i, 12, 12, t + i * 1000, t + i * 1000 + 1),
                    "current epoch owns its own head");
        }
        System.out.println("VOICE_ACOUSTIC_EPOCH_REUSE_PASS checks=" + checks);
    }
}
