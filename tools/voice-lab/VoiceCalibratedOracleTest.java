import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** FUTURE ONLY: independent per-frame oracle for acoustic-cursor visemes; no device/audio. */
public final class VoiceCalibratedOracleTest {
    private static int checks;
    private static void ok(boolean yes, String message) {
        if (!yes) throw new AssertionError("FAIL " + message);
        checks++;
    }
    private static void same(Object expected, Object got, String message) {
        ok(expected.equals(got), message + " expected=" + expected + " got=" + got);
    }

    public static void main(String[] args) {
        Random rng = new Random(0xC3115A110L);
        VoiceLifecycleContract life = new VoiceLifecycleContract();
        VoicePlaybackHeadTracker rawTracker = new VoicePlaybackHeadTracker();
        VoiceAcousticTimingContract acoustic = new VoiceAcousticTimingContract();
        for (int round = 0; round < 30; round++) {
            long now = 10000L + round * 10000L;
            long id = life.begin(now++);
            ok(life.prepared(id, now++), "prepare epoch " + round);
            rawTracker.begin(id, 0);
            acoustic.begin(id, 48000);
            int delay = rng.nextInt(40);
            ok(acoustic.acceptMeasuredDelay(id, delay), "synthetic measured-delay fixture " + round);
            ok(life.enableCalibratedPresentation(id, now), "enable before raw head " + round);
            ok(life.playedMouth() == VoiceLifecycleContract.Mouth.CLOSED,
                    "raw UI path is disabled under calibration " + round);
            List<VoiceLifecycleContract.Mouth> oracle = new ArrayList<>();
            long raw = 0L;
            long ns = 1_000_000_000L + round * 10_000_000_000L;
            for (int step = 0; step < 250; step++) {
                if (rng.nextInt(4) != 0 || oracle.isEmpty()) {
                    int accepted = 1 + rng.nextInt(7);
                    VoiceLifecycleContract.Mouth mouth = VoiceLifecycleContract.Mouth.values()[rng.nextInt(6)];
                    ok(life.canAcceptPcm(id, accepted, mouth, now),
                            "oracle budget " + round + ":" + step);
                    ok(life.acceptPcm(id, accepted, mouth, now++),
                            "oracle accepted write " + round + ":" + step);
                    for (int i = 0; i < accepted; i++) oracle.add(mouth);
                }
                long pending = oracle.size() - raw;
                long rawDelta = rng.nextInt((int) Math.min(9L, pending) + 1);
                raw += rawDelta;
                long relative = rawTracker.observe(id, (int) raw, life.acceptedFrames());
                same(raw, relative, "raw tracker oracle " + round + ":" + step);
                ok(life.playbackHead(id, relative, now++), "raw physical ack " + round + ":" + step);
                ns += 20_000_000L;
                ok(acoustic.observe(id, relative, life.acceptedFrames(), ns, ns + 1_000_000L),
                        "fresh acoustic clock " + round + ":" + step);
                long cursor = acoustic.estimatedMouthFrame(id, ns + 2_000_000L);
                long expectedFrame = Math.max(0L, raw - delay);
                same(expectedFrame, cursor, "calibrated cursor oracle " + round + ":" + step);
                VoiceLifecycleContract.Mouth expected = cursor == 0
                        ? VoiceLifecycleContract.Mouth.CLOSED
                        : oracle.get((int) cursor - 1);
                same(expected, life.calibratedMouthAt(id, cursor, now),
                        "per-sample mouth oracle " + round + ":" + step);
                ok(life.retainedCueCount() <= 256, "cue cap preserved " + round + ":" + step);
                same(VoiceLifecycleContract.Mouth.CLOSED, life.playedMouth(),
                        "raw UI cannot lead calibration " + round + ":" + step);
                if (step % 37 == 0) {
                    same(VoiceLifecycleContract.Mouth.CLOSED, life.calibratedMouthAt(id + 1000, cursor, now),
                            "invalid epoch silent " + round + ":" + step);
                    same(VoiceLifecycleContract.Mouth.CLOSED, life.calibratedMouthAt(id, -1, now),
                            "invalid timing sample silent " + round + ":" + step);
                }
            }
            if (round % 3 == 0) {
                ok(life.interrupt(id, now++), "interrupted epoch " + round);
                same(VoiceLifecycleContract.Mouth.CLOSED, life.calibratedMouthAt(id, raw, now),
                        "interrupted mouth silent " + round);
                ok(!life.synthesisDone(id, now), "post-interrupt done refused " + round);
            } else {
                ok(life.synthesisDone(id, now++), "synthesis done " + round);
                if (life.phase() == VoiceLifecycleContract.Phase.DRAINING) {
                    long complete = rawTracker.observe(id, (int) life.acceptedFrames(), life.acceptedFrames());
                    ok(life.playbackHead(id, complete, now++), "raw drains tail " + round);
                }
                same(VoiceLifecycleContract.Phase.COMPLETED, life.phase(),
                        "physical completion independent of acoustic cursor " + round);
                same(VoiceLifecycleContract.Mouth.CLOSED, life.calibratedMouthAt(id, raw, now),
                        "completed mouth silent " + round);
            }
            rawTracker.stop(id);
            acoustic.stop(id);
        }
        System.out.println("VOICE_CALIBRATED_ORACLE_PASS checks=" + checks);
    }
}
