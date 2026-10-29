/** Deterministic Android-free tests for signed 32-bit AudioTrack head handling. */
public final class VoicePlaybackHeadTrackerTest {
    private static int checks;
    private static void eq(long expected, long actual, String name) {
        if (expected != actual) throw new AssertionError("FAIL " + name + " expected=" + expected + " actual=" + actual);
        checks++;
    }
    private static void ok(boolean condition, String name) {
        if (!condition) throw new AssertionError("FAIL " + name);
        checks++;
    }

    public static void main(String[] args) {
        VoicePlaybackHeadTracker h = new VoicePlaybackHeadTracker();
        eq(-1, h.observe(1, 0, 0), "no stream yet");
        h.begin(1, 0);
        eq(0, h.observe(1, 0, 10), "initial head stays silent");
        eq(5, h.observe(1, 5, 10), "head advances");
        eq(5, h.observe(1, 5, 10), "duplicate head ignored as progress");
        eq(-1, h.observe(1, 4, 10), "one-frame backwards jitter rejected");
        eq(5, h.playedFrames(), "jitter preserves current head");
        eq(-1, h.observe(1, 20, 10), "head beyond accepted PCM rejected");
        eq(8, h.observe(1, 8, 10), "recover after invalid observation");
        eq(-1, h.observe(1, 9, 7), "accepted limit cannot lag head");
        eq(8, h.playedFrames(), "failed accepted check does not advance");
        eq(-1, h.observe(2, 9, 10), "stale generation rejected");
        h.stop(2);
        eq(9, h.observe(1, 9, 10), "unrelated stop does nothing");
        h.stop(1);
        eq(-1, h.observe(1, 10, 10), "released track rejects head");

        // Old raw counters near 2^32 can roll over while relative playback is small.
        h.begin(2, 0xfffffffc);
        eq(0, h.observe(2, 0xfffffffc, 10), "near-wrap baseline is relative zero");
        eq(2, h.observe(2, 0xfffffffe, 10), "near-wrap position");
        eq(4, h.observe(2, 0x00000000, 10), "unsigned wrap mapped to 64-bit frames");
        eq(7, h.observe(2, 0x00000003, 10), "head after wrap");
        eq(-1, h.observe(1, 4, 10), "prior generation cannot observe wrapped track");
        eq(-1, h.observe(2, 0x80000003, 3000000000L), "ambiguous exact half-wrap rejected");
        eq(7, h.playedFrames(), "ambiguous wrap cannot advance");
        eq(-1, h.observe(2, 0xffffffff, 10), "backwards jump cannot fake second wrap");
        eq(8, h.observe(2, 4, 10), "valid continuation after rejected wrap");

        // Integration semantics: only accepted frames may affect the mouth cue.
        VoiceLifecycleContract c = new VoiceLifecycleContract();
        long id = c.begin(100);
        ok(c.prepared(id, 101), "integrated contract prepared");
        ok(c.acceptPcm(id, 3, VoiceLifecycleContract.Mouth.LABIAL, 102), "integrated first PCM write");
        ok(c.acceptPcm(id, 4, VoiceLifecycleContract.Mouth.TEETH, 103), "integrated second PCM write");
        h.begin(id, 0);
        long relativeHead = h.observe(id, 3, c.acceptedFrames());
        eq(3, relativeHead, "integrated head first boundary");
        ok(c.playbackHead(id, relativeHead, 104), "first cue delivered at head");
        ok(c.playedMouth() == VoiceLifecycleContract.Mouth.LABIAL, "first viseme at sample 2");
        relativeHead = h.observe(id, 4, c.acceptedFrames());
        eq(4, relativeHead, "integrated head second cue");
        ok(c.playbackHead(id, relativeHead, 105), "second cue delivered");
        ok(c.playedMouth() == VoiceLifecycleContract.Mouth.TEETH, "second viseme at sample 3");
        eq(-1, h.observe(id, 40, c.acceptedFrames()), "no cue can lead unwritten frames");
        ok(c.synthesisDone(id, 106), "integrated synth done");
        relativeHead = h.observe(id, 7, c.acceptedFrames());
        ok(c.playbackHead(id, relativeHead, 107), "integrated drain complete");
        ok(c.phase() == VoiceLifecycleContract.Phase.COMPLETED, "integrated completed state");
        System.out.println("VOICE_HEAD_TRACKER_PASS checks=" + checks);
    }
}
