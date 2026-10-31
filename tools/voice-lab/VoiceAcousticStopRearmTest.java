/** Future-only: a calibrated or stopped AudioTrack generation must never be re-armed without begin(newId). */
public final class VoiceAcousticStopRearmTest {
    private static int checks;
    private static void check(boolean value, String why) {
        if (!value) throw new AssertionError(why);
        checks++;
    }
    private static void reject(boolean value, String why) { check(!value, why); }
    private static void once() {
        VoiceAcousticTimingContract c = new VoiceAcousticTimingContract();
        c.begin(1, 48000);
        check(c.acceptMeasuredDelay(1, 240), "initial external delay");
        check(c.observe(1, 1200, 2000, 1_000_000_000L, 1_001_000_000L), "initial sample");
        check(c.estimatedMouthFrame(1, 1_002_000_000L) == 960, "mouth initial");
        reject(c.acceptMeasuredDelay(1, 0), "same epoch must not wipe trusted baseline with recalibration");
        check(c.estimatedMouthFrame(1, 1_003_000_000L) == 960, "rejected recalibration keeps old baseline");
        reject(c.observe(1, 1199, 2000, 1_004_000_000L, 1_005_000_000L),
                "rollback must be detected despite attempted recalibration");
        check(c.estimatedMouthFrame(1, 1_006_000_000L) == -1, "rollback closes display");
        c.stop(1);
        reject(c.acceptMeasuredDelay(1, 240), "stop must never permit same-generation calibration");
        reject(c.observe(1, 1300, 2000, 1_007_000_000L, 1_008_000_000L),
                "stopped sample refused");
        check(c.estimatedMouthFrame(1, 1_009_000_000L) == -1, "stop silences mouth");
        c.begin(2, 48000);
        check(c.acceptMeasuredDelay(2, 0), "new epoch may calibrate");
        check(c.observe(2, 200, 200, 1_010_000_000L, 1_011_000_000L), "new sample");
        check(c.estimatedMouthFrame(2, 1_012_000_000L) == 200, "new mouth permitted");
        c.stop(2);
        reject(c.acceptMeasuredDelay(2, 0), "normal stop cannot be rearmed either");
        check(c.estimatedMouthFrame(2, 1_013_000_000L) == -1, "normal stop remains silent");
    }
    private static void many() {
        for (int i = 1; i <= 200; i++) {
            VoiceAcousticTimingContract c = new VoiceAcousticTimingContract();
            c.begin(i, 48000);
            check(c.acceptMeasuredDelay(i, i%2), "initial lease " + i);
            reject(c.acceptMeasuredDelay(i, i%2), "duplicate calibrate " + i);
            c.stop(i);
            reject(c.acceptMeasuredDelay(i, 0), "post-stop recalibration " + i);
            check(c.estimatedMouthFrame(i, 1000) == -1, "post-stop mouth " + i);
            c.begin(i + 1L, 48000);
            check(c.acceptMeasuredDelay(i + 1L, 0), "fresh generation " + i);
        }
    }
    public static void main(String[] args) {
        once();
        many();
        System.out.println("VOICE_ACOUSTIC_STOP_REARM_PASS checks=" + checks);
    }
}
