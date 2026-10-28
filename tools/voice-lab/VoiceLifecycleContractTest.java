/** Standalone deterministic JVM regressions, no Android, network, audio or model dependency. */
public final class VoiceLifecycleContractTest {
    private static int passed;
    private static void ok(boolean condition, String name) {
        if (!condition) throw new AssertionError("FAIL " + name);
        passed++;
    }
    private static void eq(Object expected, Object actual, String name) {
        ok(expected.equals(actual), name + " expected=" + expected + " actual=" + actual);
    }
    public static void main(String[] args) {
        VoiceLifecycleContract c = new VoiceLifecycleContract();
        eq(VoiceLifecycleContract.Phase.IDLE, c.phase(), "initial state");
        long id = c.begin(100);
        ok(id > 0, "generation created");
        eq(VoiceLifecycleContract.Phase.PREPARING, c.phase(), "preparing");
        ok(c.prepared(id, 101), "prepared");
        ok(c.acceptPcm(id, 100, VoiceLifecycleContract.Mouth.OPEN, 102), "first PCM chunk accepted");
        eq(VoiceLifecycleContract.Mouth.CLOSED, c.playedMouth(), "queued PCM not mouth movement");
        ok(c.acceptPcm(id, 25, VoiceLifecycleContract.Mouth.LABIAL, 103), "second PCM chunk accepted");
        ok(c.playbackHead(id, 90, 110), "actual head first segment");
        eq(VoiceLifecycleContract.Mouth.OPEN, c.playedMouth(), "mouth follows first played chunk");
        ok(c.playbackHead(id, 100, 111), "boundary first chunk");
        eq(VoiceLifecycleContract.Mouth.OPEN, c.playedMouth(), "boundary last played sample");
        ok(c.playbackHead(id, 101, 112), "enter labial");
        eq(VoiceLifecycleContract.Mouth.LABIAL, c.playedMouth(), "labial synced to played PCM");
        ok(!c.playbackHead(id, 50, 113), "rewinding head rejected");
        ok(!c.playbackHead(id, 126, 113), "future unqueued head rejected");
        ok(c.synthesisDone(id, 114), "synthesis done event");
        eq(VoiceLifecycleContract.Phase.DRAINING, c.phase(), "tail still draining");
        ok(c.playbackHead(id, 125, 115), "played complete tail");
        eq(VoiceLifecycleContract.Phase.COMPLETED, c.phase(), "completion only after tail");
        eq(VoiceLifecycleContract.Mouth.CLOSED, c.playedMouth(), "mouth cleared at completion");
        ok(!c.synthesisDone(id, 116), "duplicate done ignored");
        ok(!c.acceptPcm(id, 1, VoiceLifecycleContract.Mouth.OPEN, 117), "completed cannot accept PCM");

        long old = c.begin(200);
        ok(c.prepared(old, 201), "second prepared");
        ok(c.acceptPcm(old, 500, VoiceLifecycleContract.Mouth.ROUNDED, 202), "second playing");
        long fresh = c.begin(203);
        ok(fresh > old, "new utterance invalidates old generation");
        ok(!c.engineDied(old, 204), "stale engine crash cannot kill new speech");
        ok(!c.playbackHead(old, 200, 205), "stale PCM ack cannot move new speech");
        eq(VoiceLifecycleContract.Mouth.CLOSED, c.playedMouth(), "new utterance starts silent");
        ok(c.prepared(fresh, 206), "new speech prepared");
        ok(c.interrupt(fresh, 207), "user interrupt accepted");
        eq(VoiceLifecycleContract.Phase.CANCELLED, c.phase(), "cancelled state");
        eq(VoiceLifecycleContract.Mouth.CLOSED, c.playedMouth(), "interrupted mouth neutral");
        ok(!c.prepared(fresh, 208), "cancelled cannot resume by late callback");

        long kill = c.begin(300);
        ok(c.engineDied(kill, 301), "TTS process death handled");
        eq(VoiceLifecycleContract.Phase.FAILED, c.phase(), "failed on engine kill");
        eq("TTS engine terminated", c.failureReason(), "engine death reason");
        ok(!c.engineDied(kill, 302), "duplicate engine failure ignored");
        long retry = c.begin(303);
        eq("", c.failureReason(), "retry clears failure");
        ok(!c.prepared(kill, 304), "pre-retry callbacks isolated");
        ok(c.tick(6304), "preparing timeout");
        eq("synthesis timeout", c.failureReason(), "preparing timeout diagnosed");
        long stall = c.begin(7000);
        ok(c.prepared(stall, 7001), "buffering starts");
        ok(c.tick(10502), "buffering stall timeout");
        long drain = c.begin(11000);
        ok(c.prepared(drain, 11001), "drain prepare");
        ok(c.acceptPcm(drain, 500, VoiceLifecycleContract.Mouth.TEETH, 11002), "drain PCM accepted");
        ok(c.synthesisDone(drain, 11003), "drain synthesis done");
        ok(c.tick(14504), "drain stuck timeout");
        eq(VoiceLifecycleContract.Phase.FAILED, c.phase(), "drain fail-closed");

        long empty = c.begin(15000);
        ok(c.prepared(empty, 15001), "empty synth prepare");
        ok(c.synthesisDone(empty, 15002), "empty synth terminal");
        eq(VoiceLifecycleContract.Phase.FAILED, c.phase(), "empty synth rejected");
        long invalid = c.begin(16000);
        ok(c.prepared(invalid, 16001), "invalid PCM prepared");
        ok(!c.acceptPcm(invalid, 0, VoiceLifecycleContract.Mouth.OPEN, 16002), "zero PCM rejected");
        ok(!c.acceptPcm(invalid, 1, null, 16003), "null cue rejected");
        ok(!c.prepared(invalid, 15999), "backward time rejected");
        ok(!c.playbackHead(invalid, 1, 16004), "head before PCM rejected");
        ok(!c.tick(16000), "backward timer ignored");
        System.out.println("VOICE_LIFECYCLE_CONTRACT_PASS checks=" + passed);
    }
}
