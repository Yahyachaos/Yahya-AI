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
    /** A rejected plausible capture may recover only with a fresh independent clock. */
    private static void rejectedSameCaptureCannotReopenMouth() {
        for (int i = 0; i < 100; i++) {
            VoiceAcousticTimingContract c = new VoiceAcousticTimingContract();
            long t = 2_000_000_000L + i * 1_000_000_000L;
            c.begin(1, 48000);
            ok(c.acceptMeasuredDelay(1, 240), "external delay fixture " + i);
            ok(c.observe(1, 1000, 1200, t, t + 1_000_000L), "trusted baseline " + i);
            long later = t + 20_000_000L;
            ok(!c.observe(1, 1050, 1049, later, later + 1_000_000L),
                    "malformed capture refused " + i);
            eq(-1, c.estimatedMouthFrame(1, later + 2_000_000L), "invalid closes lips " + i);
            ok(!c.observe(1, 1050, 1200, later, later + 1_000_000L),
                    "same exact clock cannot launder altered capture " + i);
            ok(!c.observe(1, 1050, 1200, later, later + 2_000_000L),
                    "same source with later poll is not independent " + i);
            ok(!c.observe(1, 1050, 1200, later + 500_000L, later + 1_000_000L),
                    "same poll with later source is not independent " + i);
            eq(-1, c.estimatedMouthFrame(1, later + 3_000_000L), "replayed proof stays silent " + i);
            ok(c.observe(1, 1050, 1200, later + 20_000_000L, later + 21_000_000L),
                    "independently newer source and poll recovers " + i);
            eq(810, c.estimatedMouthFrame(1, later + 22_000_000L),
                    "new independent clock has conservative mouth " + i);
            c.stop(1);
            eq(-1, c.estimatedMouthFrame(1, later + 23_000_000L), "STOP closes lips " + i);
        }
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
        eq(-1, c.estimatedMouthFrame(1, t + 54_000_000L), "invalid same-epoch proofs must quarantine cached cursor");
        eq(-1, c.estimatedMouthFrame(2, t + 54_000_000L), "stale generation never gets mouth cue");
        c.stop(2);
        eq(-1, c.estimatedMouthFrame(1, t + 55_000_000L), "unrelated generation stop keeps quarantine");
        c.stop(1);
        eq(-1, c.estimatedMouthFrame(1, t + 55_000_000L), "stopped track no cursor");
        c.begin(2, 48000);
        eq(-1, c.estimatedMouthFrame(2, t + 60_000_000L), "new generation requires new calibration");
        ok(c.acceptMeasuredDelay(2, 0), "explicit measured zero delay accepted");
        ok(c.observe(2, 200, 200, t + 70_000_000L, t + 71_000_000L), "fresh second generation");
        eq(200, c.estimatedMouthFrame(2, t + 72_000_000L), "zero-calibrated second generation frame");
        rejectedSameCaptureCannotReopenMouth();
        System.out.println("VOICE_ACOUSTIC_TIMING_CONTRACT_PASS checks=" + checks);
    }
}
