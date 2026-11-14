/**
 * FUTURE-ONLY Java-only regression for delayed/duplicate completion callbacks.
 * No physical speaker / Android / natural-language audio evidence is implied.
 */
public final class VoiceLateCompletionIsolationTest {
    private static int checks;
    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError("#" + checks + " " + message);
    }
    public static void main(String[] args) {
        VoiceLifecycleContract v = new VoiceLifecycleContract();
        for (int trial = 0; trial < 200; trial++) {
            long t = 10000L + 100L * trial;
            long old = v.begin(t);
            check(v.prepared(old, t+1), "old prepared");
            check(v.acceptPcm(old, 4, VoiceLifecycleContract.Mouth.LABIAL, t+2), "old PCM");
            check(v.interrupt(old, t+3), "old cancelled");
            check(!v.synthesisDone(old, t+4), "old completion after STOP denied");
            check(!v.synthesisDone(old, t+4), "old duplicate denied");
            check(!v.playbackHead(old, 4, t+4), "old playback head after STOP denied");
            long next = v.begin(t+5);
            check(next != old, "strict epoch rollover");
            check(v.prepared(next, t+6), "new prepared");
            check(!v.synthesisDone(old, t+7), "replayed old completion after restart denied");
            check(!v.engineDied(old, t+7), "old engine death after restart denied");
            check(v.acceptPcm(next, 6, VoiceLifecycleContract.Mouth.OPEN, t+8), "new PCM admitted");
            check(v.synthesisDone(next, t+9), "new synthesis completion accepted");
            check(v.phase() == VoiceLifecycleContract.Phase.DRAINING, "not speaker-drained yet");
            check(!v.synthesisDone(next, t+9), "duplicate same epoch completion denied");
            check(v.playbackHead(next, 6, t+10), "actual head completes");
            check(v.phase() == VoiceLifecycleContract.Phase.COMPLETED, "terminal completed");
            check(!v.synthesisDone(next, t+11), "post-drain duplicate cannot reopen");
            check(!v.playbackHead(next, 6, t+11), "post-drain head cannot reopen");
            check(v.playedMouth() == VoiceLifecycleContract.Mouth.CLOSED, "terminal lips closed");
        }
        System.out.println("VOICE_LATE_COMPLETION_ISOLATION_PASS checks=" + checks);
    }
}
