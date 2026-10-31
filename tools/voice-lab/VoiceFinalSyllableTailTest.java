/** FUTURE-ONLY JVM fixture: physical AudioTrack drain is not acoustic mouth completion. */
public final class VoiceFinalSyllableTailTest {
    private static int checks;
    private static void ok(boolean value, String label) {
        if (!value) throw new AssertionError("FAIL " + label);
        checks++;
    }
    private static void eq(Object expected, Object actual, String label) {
        ok(expected.equals(actual), label + " expected=" + expected + " actual=" + actual);
    }
    private static void tailAfterRawDrain() {
        VoiceLifecycleContract v = new VoiceLifecycleContract();
        long id = v.begin(10);
        ok(v.prepared(id, 11), "prepared");
        ok(v.enableCalibratedPresentation(id, 12), "calibration selected before physical head");
        ok(v.enableBoundedPresentationTail(id, 12), "explicit future-only tail opt-in");
        ok(v.acceptPcm(id, 120, VoiceLifecycleContract.Mouth.OPEN, 13), "vowel written");
        ok(v.acceptPcm(id, 80, VoiceLifecycleContract.Mouth.LABIAL, 14), "final labial written");
        ok(v.playbackHead(id, 120, 15), "raw head reaches first cue boundary");
        eq(VoiceLifecycleContract.Mouth.OPEN, v.calibratedMouthAt(id, 100, 16), "first presented vowel");
        ok(v.synthesisDone(id, 17), "synthesis completed, speaker still draining");
        ok(v.playbackHead(id, 200, 18), "raw speaker drain complete");
        eq(VoiceLifecycleContract.Phase.COMPLETED, v.phase(), "raw completion remains authoritative");
        eq(VoiceLifecycleContract.Mouth.CLOSED, v.playedMouth(), "legacy uncalibrated mouth never shown");
        eq(VoiceLifecycleContract.Mouth.LABIAL, v.calibratedMouthAt(id, 150, 19),
                "lagged final syllable must not be truncated by raw drain");
        eq(VoiceLifecycleContract.Mouth.LABIAL, v.calibratedMouthAt(id, 199, 20),
                "last accepted syllable remains visible before confirmed presentation end");
        eq(VoiceLifecycleContract.Mouth.CLOSED, v.calibratedMouthAt(id, 200, 21),
                "presentation end explicitly closes mouth");
        eq(0, v.retainedCueCount(), "presentation catch-up releases cues");
        eq(VoiceLifecycleContract.Mouth.CLOSED, v.calibratedMouthAt(id, 199, 22),
                "completed presentation never reopens");
    }
    private static void timeoutAndCancellation() {
        VoiceLifecycleContract v = new VoiceLifecycleContract();
        long id = v.begin(1000);
        ok(v.prepared(id, 1001), "prepare for tail expiration");
        ok(v.enableCalibratedPresentation(id, 1002), "calibration for expiration");
        ok(v.enableBoundedPresentationTail(id, 1002), "tail opt-in before raw head");
        ok(v.acceptPcm(id, 300, VoiceLifecycleContract.Mouth.TEETH, 1003), "spoken PCM");
        ok(v.synthesisDone(id, 1004), "synthesis done");
        ok(v.playbackHead(id, 300, 1005), "raw drain");
        eq(VoiceLifecycleContract.Mouth.TEETH, v.calibratedMouthAt(id, 200, 1755),
                "at exact 750ms bound keep final syllable");
        eq(VoiceLifecycleContract.Mouth.CLOSED, v.calibratedMouthAt(id, 250, 1756),
                "after 750ms bound fail closed");
        eq(0, v.retainedCueCount(), "expired tail freed");
        eq(VoiceLifecycleContract.Phase.COMPLETED, v.phase(), "expiry does not falsify physical completion");

        long next = v.begin(1800);
        ok(v.prepared(next, 1801), "restart prepared");
        ok(v.enableCalibratedPresentation(next, 1801), "restart calibration");
        ok(v.enableBoundedPresentationTail(next, 1801), "restart tail opt-in");
        ok(v.acceptPcm(next, 50, VoiceLifecycleContract.Mouth.ROUNDED, 1802), "new speech");
        ok(v.synthesisDone(next, 1803), "new synthesis done");
        ok(v.playbackHead(next, 50, 1804), "new raw drain");
        ok(!v.engineDied(id, 1805), "stale engine callback ignored");
        eq(VoiceLifecycleContract.Mouth.CLOSED, v.calibratedMouthAt(id, 40, 1805),
                "stale generation never displays a mouth");
        ok(v.interrupt(next, 1806), "explicit stop after physical drain ends UI tail");
        eq(VoiceLifecycleContract.Mouth.CLOSED, v.calibratedMouthAt(next, 40, 1807),
                "stopped UI tail silent");
        eq(0, v.retainedCueCount(), "stop clears tail buffer");
        eq(VoiceLifecycleContract.Phase.COMPLETED, v.phase(), "physical drain truth preserved on UI stop");
        ok(!v.interrupt(next, 1808), "stop cannot rearm a finished generation");
    }
    private static void failClosedAndLegacy() {
        VoiceLifecycleContract v = new VoiceLifecycleContract();
        long id = v.begin(100);
        ok(v.prepared(id, 101), "legacy prepare");
        ok(v.acceptPcm(id, 10, VoiceLifecycleContract.Mouth.OPEN, 102), "legacy PCM");
        ok(v.synthesisDone(id, 103), "legacy synth done");
        ok(v.playbackHead(id, 10, 104), "legacy raw drain");
        eq(VoiceLifecycleContract.Phase.COMPLETED, v.phase(), "legacy completion");
        eq(0, v.retainedCueCount(), "uncalibrated completion releases cues immediately");

        id = v.begin(200);
        ok(v.prepared(id, 201), "engine death prepare");
        ok(v.enableCalibratedPresentation(id, 201), "engine death calibrate");
        ok(v.acceptPcm(id, 10, VoiceLifecycleContract.Mouth.LABIAL, 202), "engine death PCM");
        ok(v.engineDied(id, 203), "engine death fails closed");
        eq(VoiceLifecycleContract.Phase.FAILED, v.phase(), "engine death state");
        eq(VoiceLifecycleContract.Mouth.CLOSED, v.calibratedMouthAt(id, 4, 204),
                "no tail after engine death");
        eq(0, v.retainedCueCount(), "engine failure clears cue buffers");

        id = v.begin(300);
        ok(v.prepared(id, 301), "interrupt prepare");
        ok(v.enableCalibratedPresentation(id, 301), "interrupt calibrate");
        ok(v.acceptPcm(id, 15, VoiceLifecycleContract.Mouth.TEETH, 302), "interrupt PCM");
        ok(v.interrupt(id, 303), "interrupt cancels active speech");
        eq(VoiceLifecycleContract.Phase.CANCELLED, v.phase(), "cancelled phase");
        eq(VoiceLifecycleContract.Mouth.CLOSED, v.calibratedMouthAt(id, 10, 304),
                "no tail after interruption");
    }
    private static void optInAndPostDrainSafety() {
        VoiceLifecycleContract v = new VoiceLifecycleContract();
        long id = v.begin(400);
        ok(v.prepared(id, 401), "opt-in phase ready");
        ok(!v.enableBoundedPresentationTail(id, 401), "tail opt-in requires calibration");
        ok(v.enableCalibratedPresentation(id, 401), "opt-in calibration");
        ok(v.enableBoundedPresentationTail(id, 401), "tail opt-in once");
        ok(!v.enableBoundedPresentationTail(id, 401), "duplicate tail opt-in rejected");
        ok(v.acceptPcm(id, 60, VoiceLifecycleContract.Mouth.OPEN, 402), "PCM for post-drain death");
        ok(v.synthesisDone(id, 403), "post-drain death synth");
        ok(v.playbackHead(id, 60, 404), "post-drain death raw completed");
        ok(v.engineDied(id, 405), "engine death after drain silences UI tail");
        eq(VoiceLifecycleContract.Phase.COMPLETED, v.phase(),
                "engine death after raw drain does not relabel physical completion");
        eq(0, v.retainedCueCount(), "engine death drops terminal cues");
        eq(VoiceLifecycleContract.Mouth.CLOSED, v.calibratedMouthAt(id, 40, 406),
                "late acoustic callback after death silent");
        ok(!v.engineDied(id, 407), "duplicate engine death not accepted");

        id = v.begin(500);
        ok(v.prepared(id, 501), "legacy-calibrated ready");
        ok(v.enableCalibratedPresentation(id, 501), "legacy calibration without new tail opt-in");
        ok(v.acceptPcm(id, 50, VoiceLifecycleContract.Mouth.TEETH, 502), "legacy calibration PCM");
        ok(v.synthesisDone(id, 503), "legacy calibration synthesis");
        ok(v.playbackHead(id, 50, 504), "legacy calibration physical drain");
        eq(0, v.retainedCueCount(), "legacy calibrated behavior unchanged without opt-in");
        eq(VoiceLifecycleContract.Mouth.CLOSED, v.calibratedMouthAt(id, 40, 505),
                "no retroactive tail for earlier behavior");

        id = v.begin(600);
        ok(v.prepared(id, 601), "watchdog-ready stream");
        ok(v.enableCalibratedPresentation(id, 601), "watchdog calibration");
        ok(v.enableBoundedPresentationTail(id, 601), "watchdog bounded tail opt-in");
        ok(v.acceptPcm(id, 10, VoiceLifecycleContract.Mouth.ROUNDED, 602), "watchdog PCM");
        ok(v.synthesisDone(id, 603), "watchdog synthesis");
        ok(v.playbackHead(id, 10, 604), "watchdog raw drain");
        ok(!v.tick(1354), "exact 750ms watchdog boundary retains cue");
        ok(v.tick(1355), "watchdog closes missing acoustic end after 751ms");
        eq(0, v.retainedCueCount(), "watchdog frees bounded tail without UI callback");
        eq(VoiceLifecycleContract.Phase.COMPLETED, v.phase(),
                "watchdog only affects UI, never physical phase");
    }
    public static void main(String[] args) {
        tailAfterRawDrain();
        timeoutAndCancellation();
        failClosedAndLegacy();
        optInAndPostDrainSafety();
        System.out.println("VOICE_FINAL_SYLLABLE_TAIL_PASS checks=" + checks);
    }
}
