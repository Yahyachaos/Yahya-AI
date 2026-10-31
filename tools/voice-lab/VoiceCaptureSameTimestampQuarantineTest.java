/** FUTURE-ONLY JVM test: contradictory external capture reuse cannot grant fresh speaker proof. */
public final class VoiceCaptureSameTimestampQuarantineTest {
    private static int checks;
    private static void ok(boolean truth, String label) { if (!truth) throw new AssertionError(label); checks++; }
    private static boolean claim(VoiceVerifiedPresentationClock c, long id, long presented,
            long raw, long accepted, long ns) {
        return c.observe(id, VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                presented, raw, accepted, ns, ns + 1_000_000L);
    }
    public static void main(String[] ignored) {
        for (int run = 0; run < 100; run++) {
            long id = run + 1L;
            long ns = 1_000_000_000L + run * 50_000_000L;
            VoiceVerifiedPresentationClock c = new VoiceVerifiedPresentationClock();
            c.begin(id, 48000);
            ok(claim(c, id, 10, 20, 20, ns), "baseline accepted");
            long later = ns + 25_000_000L;
            ok(!claim(c, id, 40, 39, 40, later), "malformed external capture rejected");
            ok(c.presentedFrame(id, later + 2_000_000L) == -1,
                    "bad sample silences acoustic proof");
            ok(!claim(c, id, 40, 40, 40, later),
                    "same-timestamp altered speaker sample cannot launder invalid capture");
            ok(c.presentedFrame(id, later + 3_000_000L) == -1,
                    "unproven captured tail must remain silent");
            VoiceVerifiedPresentationClock recover = new VoiceVerifiedPresentationClock();
            recover.begin(id, 48000);
            ok(claim(recover, id, 10, 20, 20, ns), "recovery baseline");
            ok(!claim(recover, id, 40, 39, 40, later), "recovery invalid sample");
            ok(claim(recover, id, 40, 40, 40, later + 25_000_000L),
                    "newer independently timestamped capture recovers");
            ok(recover.presentedFrame(id, later + 27_000_000L) == 40,
                    "fresh independent claim usable");
            c.begin(id + 1000, 48000);
            ok(claim(c, id + 1000, 40, 40, 40, later + 40_000_000L),
                    "new generation recovers with independently fresh capture");
        }
        System.out.println("VOICE_CAPTURE_SAME_TIMESTAMP_QUARANTINE_PASS checks="+checks);
    }
}
