/** FUTURE ONLY: cross-contract PCM, head, calibrated mouth and stream lifecycle regression. */
public final class VoiceCrossContractPlaybackTest {
    private static int checks;
    private static void ok(boolean yes, String msg) {
        if (!yes) throw new AssertionError("FAIL " + msg);
        checks++;
    }
    private static void eq(Object expected, Object actual, String msg) {
        ok(expected.equals(actual), msg + " expected=" + expected + " actual=" + actual);
    }
    public static void main(String[] args) {
        VoiceLifecycleContract life = new VoiceLifecycleContract();
        VoicePlaybackHeadTracker head = new VoicePlaybackHeadTracker();
        VoiceAcousticTimingContract acoustic = new VoiceAcousticTimingContract();
        long id = life.begin(100);
        ok(life.prepared(id, 101), "prepared");
        head.begin(id, 0);
        acoustic.begin(id, 48000);
        ok(life.acceptPcm(id, 300, VoiceLifecycleContract.Mouth.OPEN, 102), "first 300 frames accepted");
        ok(life.acceptPcm(id, 700, VoiceLifecycleContract.Mouth.TEETH, 103), "next 700 frames accepted");
        ok(acoustic.acceptMeasuredDelay(id, 300), "externally supplied delay fixture");
        ok(life.enableCalibratedPresentation(id, 103), "calibrated path armed before raw head");
        long raw = head.observe(id, 500, life.acceptedFrames());
        eq(500L, raw, "raw head 500");
        ok(life.playbackHead(id, raw, 104), "raw head acknowledged");
        long t = 1_000_000_000L;
        ok(acoustic.observe(id, raw, life.acceptedFrames(), t, t + 1_000_000L), "calibrated observation");
        eq(200L, acoustic.estimatedMouthFrame(id, t + 2_000_000L), "200 frame conservative presentation cursor");
        // Genuine mismatch in pre-fix source: raw-head cue TEETH vs calibrated presentation OPEN.
        eq(VoiceLifecycleContract.Mouth.CLOSED, life.playedMouth(), "raw-head mouth suppressed in calibrated mode");
        eq(VoiceLifecycleContract.Mouth.OPEN, life.calibratedMouthAt(id,
                acoustic.estimatedMouthFrame(id, t + 2_000_000L), 104),
                "calibrated mouth must not lead physical presentation");
        eq(2, life.retainedCueCount(), "raw head cannot prune unheard calibrated first cue");
        eq(VoiceLifecycleContract.Mouth.CLOSED, life.calibratedMouthAt(id, 501, 105),
                "cursor may not exceed physically played head");
        eq(VoiceLifecycleContract.Mouth.CLOSED, life.calibratedMouthAt(id + 1, 200, 105),
                "wrong-generation cursor is silent");
        eq(VoiceLifecycleContract.Mouth.OPEN, life.calibratedMouthAt(id, 300, 105),
                "exact cue boundary uses last presented sample");
        eq(VoiceLifecycleContract.Mouth.TEETH, life.calibratedMouthAt(id, 301, 105),
                "new cue shown only when calibrated cursor enters it");
        eq(1, life.retainedCueCount(), "first cue pruned only after presentation advances");
        eq(VoiceLifecycleContract.Mouth.CLOSED, life.calibratedMouthAt(id, 299, 106),
                "backtracking calibrated cursor refuses stale frame");
        eq(VoiceLifecycleContract.Mouth.CLOSED, life.calibratedMouthAt(id, -1, 106),
                "invalid acoustic sample must fail closed");
        ok(life.synthesisDone(id, 107), "synthesis completion begins speaker drain");
        eq(VoiceLifecycleContract.Phase.DRAINING, life.phase(), "raw speaker tail not finished");
        eq(-1L, acoustic.estimatedMouthFrame(id, t + 250_000_000L),
                "stale hardware clock cannot justify mouth output");
        long rawTail = head.observe(id, 1000, life.acceptedFrames());
        eq(1000L, rawTail, "actual speaker head finally drains");
        ok(life.playbackHead(id, rawTail, 108), "raw head completes speaker drain");
        eq(VoiceLifecycleContract.Phase.COMPLETED, life.phase(), "drain completion does not wait on stale acoustic cursor");
        eq(VoiceLifecycleContract.Mouth.CLOSED, life.calibratedMouthAt(id, 700, 109),
                "no trailing mouth callback after terminal raw drain");
        eq(0, life.retainedCueCount(), "terminal drain clears pending cues");
        head.stop(id);
        acoustic.stop(id);
        long next = life.begin(200);
        ok(!life.enableCalibratedPresentation(id, 201), "prior generation cannot select new presentation mode");
        ok(life.prepared(next, 201), "new utterance prepared");
        head.begin(next, 10);
        acoustic.begin(next, 48000);
        ok(life.acceptPcm(next, 400, VoiceLifecycleContract.Mouth.ROUNDED, 202), "new stream PCM");
        ok(acoustic.acceptMeasuredDelay(next, 0), "explicitly measured zero-delay fixture");
        ok(life.enableCalibratedPresentation(next, 202), "new stream enters calibrated mode");
        eq(-1L, head.observe(id, 50, life.acceptedFrames()), "stale track callback blocked");
        eq(-1L, acoustic.estimatedMouthFrame(id, t + 2_000_000L), "stale acoustic epoch blocked");
        ok(life.playbackHead(next, head.observe(next, 210, life.acceptedFrames()), 203), "fresh head progresses");
        ok(acoustic.observe(next, 200, 400, t + 300_000_000L, t + 301_000_000L), "fresh acoustic epoch");
        eq(VoiceLifecycleContract.Mouth.ROUNDED, life.calibratedMouthAt(next,
                acoustic.estimatedMouthFrame(next, t + 302_000_000L), 203), "fresh mouth independent of old epoch");
        ok(life.interrupt(next, 204), "new utterance interrupted");
        eq(VoiceLifecycleContract.Mouth.CLOSED, life.calibratedMouthAt(next, 200, 205), "interrupted mouth silent");
        eq(VoiceLifecycleContract.Mouth.CLOSED, life.playedMouth(), "no raw mouth after interrupt");
        System.out.println("VOICE_CROSS_CONTRACT_PASS checks=" + checks);
    }
}
