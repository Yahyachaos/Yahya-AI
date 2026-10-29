/**
 * FUTURE ONLY: model the real acoustic-cursor ceiling after AudioTrack raw drain.
 * It proves bounded UI retention alone CANNOT synthesize the missing final timestamp.
 * All latencies here are fixtures, NOT measured S25 speaker output.
 */
public final class VoiceTailAcousticCeilingTest {
    private static int checks;
    private static void ok(boolean v, String m) {
        if (!v) throw new AssertionError("FAIL " + m);
        checks++;
    }
    private static void eq(Object e, Object a, String m) {
        ok(e.equals(a), m + " expected=" + e + " actual=" + a);
    }
    public static void main(String[] args) {
        VoiceLifecycleContract life = new VoiceLifecycleContract();
        VoiceAcousticTimingContract clock = new VoiceAcousticTimingContract();
        long id = life.begin(100);
        ok(life.prepared(id, 101), "prepared");
        clock.begin(id, 48000);
        ok(clock.acceptMeasuredDelay(id, 100), "synthetic 100-frame delay admitted");
        ok(life.enableCalibratedPresentation(id, 102), "calibrated mode");
        ok(life.enableBoundedPresentationTail(id, 102), "bounded experimental tail opt-in");
        ok(life.acceptPcm(id, 300, VoiceLifecycleContract.Mouth.OPEN, 103), "first 300 frames");
        ok(life.acceptPcm(id, 180, VoiceLifecycleContract.Mouth.LABIAL, 104), "final 180 frames");
        ok(life.synthesisDone(id, 105), "synthesis completed");
        ok(life.playbackHead(id, 480, 106), "physical head drained exactly to N");
        eq(VoiceLifecycleContract.Phase.COMPLETED, life.phase(), "raw drain is final lifecycle authority");
        long ns = 1_000_000_000L;
        ok(clock.observe(id, 480, 480, ns, ns + 1_000_000L), "fresh raw-head timestamp");
        long cursor = clock.estimatedMouthFrame(id, ns + 2_000_000L);
        eq(380L, cursor, "cursor capped at N-minus-delay");
        eq(VoiceLifecycleContract.Mouth.LABIAL, life.calibratedMouthAt(id, cursor, 107),
                "retained last cue can still appear after raw drain");
        eq(100, (int) (life.playedFrames() - cursor), "final 100 frames not observed acoustically");
        eq(380L, clock.estimatedMouthFrame(id, ns + 90_000_000L),
                "merely waiting cannot invent 100 missing frames");
        eq(-1L, clock.estimatedMouthFrame(id, ns + 202_000_000L),
                "hardware sample becomes stale after 200ms freshness bound");
        eq(VoiceLifecycleContract.Mouth.CLOSED, life.calibratedMouthAt(id, -1, 307),
                "invalid timestamp is UI silence, never extrapolation");
        ok(life.tick(857), "UI-only tail expires at 751ms after physical drain");
        eq(0, life.retainedCueCount(), "expiry releases retained mouth data");
        eq(VoiceLifecycleContract.Mouth.CLOSED, life.calibratedMouthAt(id, 380, 858),
                "stale cursor cannot revive expired terminal mouth");
        eq(VoiceLifecycleContract.Phase.COMPLETED, life.phase(),
                "expiry does not claim a second audio playback or broken drain");
        clock.stop(id);

        long next = life.begin(1000);
        clock.begin(next, 48000);
        ok(clock.acceptMeasuredDelay(next, 100), "separate fresh calibration fixture");
        eq(-1L, clock.estimatedMouthFrame(id, ns + 10_000_000L),
                "old-generation acoustic data refused");
        eq(VoiceLifecycleContract.Mouth.CLOSED, life.calibratedMouthAt(id, 200, 1001),
                "old-generation mouth cannot leak into restart");
        ok(life.prepared(next, 1001), "new stream ready");
        ok(life.enableCalibratedPresentation(next, 1001), "new calibrated mode");
        ok(!life.enableBoundedPresentationTail(id, 1002), "old epoch cannot enable new tail");
        System.out.println("VOICE_TAIL_ACOUSTIC_CEILING_PASS checks=" + checks);
    }
}
