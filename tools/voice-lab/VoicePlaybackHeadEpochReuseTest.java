/** FUTURE-ONLY monotonic AudioTrack head ownership, no Samsung hardware proof. */
public final class VoicePlaybackHeadEpochReuseTest {
    private static int checks;
    private static void ok(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
        checks++;
    }
    private static void rejectsBegin(VoicePlaybackHeadTracker h, long id) {
        boolean rejected = false;
        try { h.begin(id, 100); }
        catch (IllegalArgumentException e) { rejected = true; }
        ok(rejected, "old or duplicate AudioTrack generation " + id + " must be rejected");
    }
    public static void main(String[] args) {
        VoicePlaybackHeadTracker h = new VoicePlaybackHeadTracker();
        h.begin(7, 0);
        ok(h.observe(7, 5, 10) == 5, "initial head advanced");
        rejectsBegin(h, 7);
        rejectsBegin(h, 6);
        ok(h.generation() == 7 && h.playedFrames() == 5,
                "rejected begin cannot alter active generation or head");
        ok(h.observe(7, 6, 10) == 6, "active head remains usable after rejected begin");
        h.stop(7);
        ok(h.observe(7, 7, 10) == -1, "stopped epoch cannot play");
        rejectsBegin(h, 7);
        h.begin(8, 0xfffffffE);
        ok(h.observe(7, 7, 10) == -1, "old epoch callbacks are stale");
        ok(h.observe(8, 0, 10) == 2, "new epoch preserves unsigned wrap behavior");
        for (int i = 0; i < 200; i++) {
            long id = 9L + i;
            h.begin(id, i);
            ok(h.observe(id, i + 3, 3) == 3, "new epoch observable " + id);
            rejectsBegin(h, id);
            rejectsBegin(h, id - 1);
            ok(h.generation() == id && h.playedFrames() == 3,
                    "rejected duplicate cannot rewind cursor " + id);
            ok(h.observe(id, i + 4, 4) == 4, "valid new head after rejection " + id);
        }
        System.out.println("VOICE_PLAYBACK_HEAD_EPOCH_REUSE_PASS checks=" + checks);
    }
}
