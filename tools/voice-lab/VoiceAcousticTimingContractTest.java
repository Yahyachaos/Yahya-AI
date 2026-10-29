/** Deterministic future-only post-calibration frame cursor checks, no device timing. */
public final class VoiceAcousticTimingContractTest {
    private static int checks;
    private static void eq(long expected, long got, String description) {
        if (expected != got) throw new AssertionError("FAIL " + description + " expected=" + expected + " got=" + got);
        checks++;
    }
    private static void ok(boolean condition, String description) {
        if (!condition) throw new AssertionError("FAIL " + description);
        checks++;
    }
    public static void main(String[] args) {
        VoiceAcousticTimingContract c = new VoiceAcousticTimingContract();
        c.begin(1, 48000);
        eq(-1, c.estimatedMouthFrame(1, 1000), "no device calibration means no cursor");
        ok(!c.observe(1, 1, 2, 0, 0), "cannot observe without calibration");
        ok(!c.acceptMeasuredDelay(2, 480), "wrong-generation calibration rejected");
        ok(!c.acceptMeasuredDelay(1, -1), "negative latency refused");
        ok(!c.acceptMeasuredDelay(1, 48000), "unbounded delay refused");
        ok(c.acceptMeasuredDelay(1, 480), "device-provided 10ms delay accepted");
        eq(-1, c.estimatedMouthFrame(1, 1000), "calibration alone cannot fake played frames");
        long t = 1_000_000_000L;
        ok(!c.observe(1, 96, 120, t + 1, t), "future hardware stamp refused");
        ok(!c.observe(1, 96, 120, t, t + 250_000_000L), "stale sample refused");
        ok(!c.observe(1, 121, 120, t, t), "unwritten head refused");
        ok(c.observe(1, 96, 120, t, t + 10_000_000L), "fresh accepted head");
        eq(0, c.estimatedMouthFrame(1, t + 11_000_000L), "latency holds mouth at zero");
        ok(c.observe(1, 960, 2000, t + 40_000_000L, t + 41_000_000L), "head advances after fresh stamp");
        eq(480, c.estimatedMouthFrame(1, t + 42_000_000L), "mouth lags observed head by supplied delay");
        eq(-1, c.estimatedMouthFrame(1, t + 250_000_000L), "no optimistic extrapolation after stale poll");
        ok(!c.observe(1, 959, 2000, t + 50_000_000L, t + 51_000_000L), "backwards head rejected");
        ok(!c.observe(1, 1000, 2000, t + 30_000_000L, t + 52_000_000L), "backwards hardware time rejected");
        ok(!c.observe(1, 1000, 2000, t + 40_000_000L, t + 52_000_000L), "advanced head without new hardware stamp rejected");
        ok(!c.observe(1, 30000, 50000, t + 45_000_000L, t + 53_000_000L), "implausible head jump rejected");
        eq(480, c.estimatedMouthFrame(1, t + 54_000_000L), "invalid samples preserve accepted cursor");
        eq(-1, c.estimatedMouthFrame(2, t + 54_000_000L), "stale generation never gets mouth cue");
        c.stop(2);
        eq(480, c.estimatedMouthFrame(1, t + 55_000_000L), "unrelated generation stop ignored");
        c.stop(1);
        eq(-1, c.estimatedMouthFrame(1, t + 55_000_000L), "stopped track no cursor");
        c.begin(2, 48000);
        eq(-1, c.estimatedMouthFrame(2, t + 60_000_000L), "new generation requires new calibration");
        ok(c.acceptMeasuredDelay(2, 0), "explicit measured zero delay accepted");
        ok(c.observe(2, 200, 200, t + 70_000_000L, t + 71_000_000L), "fresh second generation");
        eq(200, c.estimatedMouthFrame(2, t + 72_000_000L), "zero-calibrated second generation frame");
        System.out.println("VOICE_ACOUSTIC_TIMING_CONTRACT_PASS checks=" + checks);
    }
}
