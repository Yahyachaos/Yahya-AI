import java.util.Random;

/** Future-only seeded invariant replay, not a physical speaker test. */
public final class VoiceVerifiedPresentationReplayTest {
    private static long checks;
    private static void ok(boolean b, String label) {
        if (!b) throw new AssertionError("FAIL " + label);
        checks++;
    }
    private static void eq(long expected, long actual, String label) {
        ok(expected == actual, label + " expected=" + expected + " got=" + actual);
    }
    public static void main(String[] args) {
        VoiceVerifiedPresentationClock clock = new VoiceVerifiedPresentationClock();
        Random random = new Random(0xC3E11A2026L);
        for (int round=1;round<=40;round++) {
            long id = round;
            clock.begin(id,48000);
            long captured=0, raw=0, accepted=0, t=round*10_000_000_000L;
            eq(-1, clock.presentedFrame(id,t), "generation starts with no speaker evidence");
            for (int step=0;step<130;step++) {
                accepted += random.nextInt(80)+1;
                raw += random.nextInt((int)Math.min(accepted-raw, 95)+1);
                captured += random.nextInt((int)Math.min(raw-captured, 95)+1);
                t += 20_000_000L;
                long poll=t+1_000_000L;
                // Invalid current proof must never update the last good physical baseline.
                if (step%23==11) {
                    ok(!clock.observe(id,VoiceVerifiedPresentationClock.EvidenceKind.RAW_HEAD_ONLY,
                            captured,raw,accepted,t,poll), "raw-head shortcut explicitly rejected");
                    eq(-1,clock.presentedFrame(id,poll),"invalid proof immediately hides presentation");
                }
                ok(clock.observe(id,VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                        captured,raw,accepted,t,poll), "valid externally-marked fixture accepted");
                eq(captured,clock.presentedFrame(id,poll+1_000_000L),"only observed sample is returned");
                eq(captured,clock.presentedFrame(id,poll+99_000_000L),"time cannot autonomously advance frame");
                eq(-1,clock.presentedFrame(id,poll+201_000_000L),"aged capture has no usable presentation");
                eq(-1,clock.presentedFrame(id+1000,poll),"wrong epoch cursor blocked");
            }
            // New independent captures can progress after RAW AudioTrack finishes.
            raw=accepted;
            for (int tail=0;tail<8;tail++) {
                captured += Math.min(raw-captured, 70);
                t += 20_000_000L;
                ok(clock.observe(id,VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                        captured,raw,accepted,t,t+1_000_000L),"late externally confirmed tail accepted");
                eq(captured,clock.presentedFrame(id,t+2_000_000L),"acoustic cursor tracks only confirmed tail");
            }
            // Poison after actual impossible raw-head reset, not from stale other generation.
            ok(!clock.observe(id-1,VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                    captured,raw-1,accepted,t+10_000_000L,t+11_000_000L),"prior generation cannot corrupt current baseline");
            ok(!clock.observe(id,VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                    captured,raw-1,accepted,t+10_000_000L,t+11_000_000L),"physical track-head reset poisons generation");
            eq(-1,clock.presentedFrame(id,t+12_000_000L),"after reset all previous proof invalidated");
            ok(!clock.observe(id,VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                    captured,raw,accepted,t+20_000_000L,t+21_000_000L),"cannot heal poisoned epoch by later callback");
            clock.stop(id);
        }
        long id=41;
        clock.begin(id,192000);
        long nearEnd=Long.MAX_VALUE-4_000_000L;
        ok(clock.observe(id,VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                200,200,200,nearEnd,nearEnd+1_000_000L),"high monotonic timestamp accepted");
        eq(200,clock.presentedFrame(id,nearEnd+2_000_000L),"high timestamp arithmetic does not wrap");
        ok(clock.observe(id,VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                250,260,260,nearEnd+1_000_000L,nearEnd+2_000_000L),"high timestamp monotonic followup");
        eq(250,clock.presentedFrame(id,nearEnd+3_000_000L),"new speaker sample remains bounded");
        System.out.println("VOICE_VERIFIED_PRESENTATION_REPLAY_PASS checks="+checks);
    }
}
