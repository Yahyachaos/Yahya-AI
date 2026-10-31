/** FUTURE-ONLY JVM contract regression: a speaker capture identity is immutable. */
public final class VoiceCaptureSameClockMutationTest {
    private static int checks;
    private static void check(boolean condition, String why) {
        if (!condition) throw new AssertionError(why);
        checks++;
    }
    public static void main(String[] args) {
        for (int round = 0; round < 500; round++) {
            long t = 1_000_000_000L + round * 100_000_000L;
            var c = new VoiceVerifiedPresentationClock();
            c.begin(1, 48000);
            check(c.observe(1, VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                    120, 150, 1000, t, t + 1000), "initial source sample");
            check(c.presentedFrame(1, t + 2000) == 120, "first acoustic cursor");
            check(c.observe(1, VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                    120, 150, 1000, t, t + 1500), "exact duplicate may be re-polled");
            long mutatedRaw = round % 3 == 1 ? 150 : 151 + round;
            long mutatedAccepted = round % 3 == 0 ? 1000 : 1001 + round;
            check(!c.observe(1, VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                    120, mutatedRaw, mutatedAccepted, t, t + 2000),
                    "same capture time cannot substitute altered raw/accepted counts " + round);
            check(c.presentedFrame(1, t + 3000) == -1, "invalid duplicate closes mouth");
            if ((round & 1) == 0) {
                check(c.observe(1, VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                        200, 300, 1100, t + 20_000_000L, t + 20_001_000L),
                        "new independent capture recovers a rejected mutation");
                check(c.presentedFrame(1, t + 20_002_000L) == 200, "recovered cursor");
            } else {
                check(!c.observe(1, VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                        120, 150, 1000, t, t + 3000), "replayed rejected clock poisons epoch");
                check(!c.observe(1, VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                        200, 300, 1100, t + 20_000_000L, t + 20_001_000L),
                        "poisoned epoch requires new generation");
            }
            c.stop(1);
            check(c.presentedFrame(1, t + 20_003_000L) == -1, "STOP silences cursor");
            c.begin(2, 48000);
            check(c.observe(2, VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                    0, 0, 120, t + 25_000_000L, t + 25_001_000L),
                    "new generation starts independently");
            check(c.presentedFrame(2, t + 25_002_000L) == 0, "fresh generation cursor");
        }
        System.out.println("VOICE_CAPTURE_SAME_CLOCK_MUTATION_PASS checks=" + checks);
    }
}
