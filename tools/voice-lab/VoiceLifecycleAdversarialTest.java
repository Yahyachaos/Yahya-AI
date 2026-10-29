import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** FUTURE-ONLY source-independent deterministic callback/write/head regression fixtures. */
public final class VoiceLifecycleAdversarialTest {
    private static int checks;
    private static void ok(boolean yes, String message) {
        if (!yes) throw new AssertionError("FAIL " + message);
        checks++;
    }
    private static void eq(Object expected, Object actual, String message) {
        ok(expected.equals(actual), message + ": expected " + expected + ", actual " + actual);
    }

    public static void main(String[] args) {
        partialWritesAndHeadJitter();
        boundedCueRetention();
        backlogFailClosed();
        deterministicAdversarialReplay();
        System.out.println("VOICE_ADVERSARIAL_PASS checks=" + checks);
    }

    private static void partialWritesAndHeadJitter() {
        VoiceLifecycleContract c = new VoiceLifecycleContract();
        long id = c.begin(10);
        ok(c.prepared(id, 11), "partial prepare");
        ok(c.acceptPcm(id, 13, VoiceLifecycleContract.Mouth.LABIAL, 12), "short AudioTrack write accepted");
        ok(!c.acceptPcm(id, 0, VoiceLifecycleContract.Mouth.TEETH, 13), "zero write does not advance");
        ok(!c.acceptPcm(id, -3, VoiceLifecycleContract.Mouth.TEETH, 14), "negative AudioTrack error not PCM");
        eq(13L, c.acceptedFrames(), "only actual frames counted");
        ok(c.playbackHead(id, 8, 15), "head reaches first short write");
        eq(VoiceLifecycleContract.Mouth.LABIAL, c.playedMouth(), "initial partial writes sync");
        ok(c.acceptPcm(id, 5, VoiceLifecycleContract.Mouth.TEETH, 16), "second partial write");
        ok(!c.playbackHead(id, 7, 17), "head jitter backwards rejected");
        eq(8L, c.playedFrames(), "jitter did not rewind");
        ok(c.playbackHead(id, 13, 18), "first chunk exact boundary");
        eq(VoiceLifecycleContract.Mouth.LABIAL, c.playedMouth(), "boundary still first cue");
        ok(c.playbackHead(id, 14, 19), "head enters second chunk");
        eq(VoiceLifecycleContract.Mouth.TEETH, c.playedMouth(), "second cue at actual played offset");
        eq(1, c.retainedCueCount(), "first cue pruned after crossing its end");
        ok(c.synthesisDone(id, 20), "synth done before speaker tail");
        ok(c.playbackHead(id, 18, 21), "speaker tail drained");
        eq(VoiceLifecycleContract.Phase.COMPLETED, c.phase(), "real tail completion");
        eq(0, c.retainedCueCount(), "terminal clears cues");
    }

    private static void boundedCueRetention() {
        VoiceLifecycleContract c = new VoiceLifecycleContract();
        long id = c.begin(100);
        ok(c.prepared(id, 101), "long utterance prepared");
        for (int i = 0; i < 5000; i++) {
            VoiceLifecycleContract.Mouth m = (i % 2 == 0)
                    ? VoiceLifecycleContract.Mouth.OPEN : VoiceLifecycleContract.Mouth.LABIAL;
            ok(c.acceptPcm(id, 7, m, 102 + i), "stream accepts chunk " + i);
            ok(c.playbackHead(id, 7L * (i + 1), 103 + i), "stream head " + i);
            eq(m, c.playedMouth(), "stream cue " + i);
            ok(c.retainedCueCount() <= 2, "bounded played cue retention " + i);
        }
        eq(35000L, c.acceptedFrames(), "total streamed frames");
        ok(c.synthesisDone(id, 5200), "long synth done");
        eq(VoiceLifecycleContract.Phase.COMPLETED, c.phase(), "long stream drains");
    }

    private static void backlogFailClosed() {
        VoiceLifecycleContract c = new VoiceLifecycleContract();
        long id = c.begin(200);
        ok(c.prepared(id, 201), "backlog prepared");
        for (int i = 0; i < 256; i++) {
            VoiceLifecycleContract.Mouth m = i % 2 == 0
                    ? VoiceLifecycleContract.Mouth.OPEN : VoiceLifecycleContract.Mouth.CLOSED;
            ok(c.acceptPcm(id, 1, m, 202 + i), "backlog accepted within cap " + i);
        }
        eq(256, c.retainedCueCount(), "cap reached exactly");
        ok(!c.acceptPcm(id, 1, VoiceLifecycleContract.Mouth.OPEN, 459), "backlog rejects overflowing cue");
        eq(VoiceLifecycleContract.Phase.FAILED, c.phase(), "backlog failure stops lifecycle");
        eq("cue backlog exceeded", c.failureReason(), "backlog failure explicit");
        eq(0, c.retainedCueCount(), "backlog failure clears cues");
        long next = c.begin(460);
        ok(next > id, "new generation can recover");
        ok(!c.playbackHead(id, 1, 461), "old callbacks rejected after overflow");
    }

    private static void deterministicAdversarialReplay() {
        Random random = new Random(0xC311A17L);
        VoiceLifecycleContract c = new VoiceLifecycleContract();
        for (int utterance = 0; utterance < 60; utterance++) {
            long now = 100000L + 10000L * utterance;
            long id = c.begin(now);
            ok(c.prepared(id, ++now), "replay setup " + utterance);
            List<VoiceLifecycleContract.Mouth> oracle = new ArrayList<>();
            long played = 0;
            for (int step = 0; step < 180; step++) {
                now++;
                if (random.nextBoolean() || oracle.isEmpty()) {
                    int returnedFrames = 1 + random.nextInt(7); // actual short write, not requested count
                    VoiceLifecycleContract.Mouth mouth = VoiceLifecycleContract.Mouth.values()[random.nextInt(6)];
                    ok(c.acceptPcm(id, returnedFrames, mouth, now), "replay returned write " + utterance + ":" + step);
                    for (int frame = 0; frame < returnedFrames; frame++) oracle.add(mouth);
                } else {
                    long target = played + random.nextInt((int) (oracle.size() - played + 1));
                    ok(c.playbackHead(id, target, now), "replay head " + utterance + ":" + step);
                    played = target;
                }
                ok(c.retainedCueCount() <= 256, "replay bounded " + utterance + ":" + step);
                if (played == 0) {
                    eq(VoiceLifecycleContract.Mouth.CLOSED, c.playedMouth(), "replay initial silent");
                } else {
                    eq(oracle.get((int) played - 1), c.playedMouth(), "replay oracle sample " + utterance + ":" + step);
                }
            }
            ok(c.synthesisDone(id, ++now), "replay synth done " + utterance);
            if (c.phase() == VoiceLifecycleContract.Phase.DRAINING) {
                ok(c.playbackHead(id, c.acceptedFrames(), ++now), "replay final tail " + utterance);
            } else {
                eq(VoiceLifecycleContract.Phase.COMPLETED, c.phase(), "replay already drained " + utterance);
            }
            eq(VoiceLifecycleContract.Phase.COMPLETED, c.phase(), "replay terminal " + utterance);
            eq(0, c.retainedCueCount(), "replay terminal cleanup " + utterance);
            ok(!c.acceptPcm(id, 5, VoiceLifecycleContract.Mouth.OPEN, ++now), "replay stale post-completion");
        }
        long old = c.begin(900000);
        ok(c.prepared(old, 900001), "interrupt restart setup");
        ok(c.acceptPcm(old, 10, VoiceLifecycleContract.Mouth.TEETH, 900002), "interrupt prior PCM");
        ok(c.interrupt(old, 900003), "interrupt prior utterance");
        long newer = c.begin(900004);
        ok(!c.synthesisDone(old, 900005), "late synth completion ignored");
        ok(!c.engineDied(old, 900006), "old engine kill ignored");
        ok(!c.playbackHead(old, 8, 900007), "old playback head ignored");
        ok(!c.acceptPcm(old, 100, VoiceLifecycleContract.Mouth.ROUNDED, 900008), "old PCM ignored");
        eq(VoiceLifecycleContract.Phase.PREPARING, c.phase(), "new utterance still preparing");
        ok(c.prepared(newer, 900009), "new utterance after stale events");
    }
}
