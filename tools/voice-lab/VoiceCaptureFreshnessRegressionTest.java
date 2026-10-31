/** Android-free fail-closed acoustic capture freshness across delayed callback polls. */
public final class VoiceCaptureFreshnessRegressionTest {
    private static int checks;
    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
        checks++;
    }
    public static void main(String[] args) {
        var c = new VoiceVerifiedPresentationClock();
        c.begin(1L, 48_000);
        long captureNs = 1_000_000_000L;
        long pollNs = captureNs + 190_000_000L;
        check(c.observe(1, VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                480, 480, 480, captureNs, pollNs), "capture inside 200ms accepted");
        check(c.presentedFrame(1, captureNs + 199_000_000L) == 480,
                "fresh 199ms old capture accepted");
        check(c.presentedFrame(1, captureNs + 201_000_000L) == -1,
                "capture age 201ms must expire even if poll only 11ms earlier");
        check(c.presentedFrame(1, captureNs + 380_000_000L) == -1,
                "poll age alone cannot revive 380ms old capture");
        check(c.observe(1, VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                480, 480, 480, captureNs + 400_000_000L, captureNs + 401_000_000L),
                "new independent capture refreshes original epoch");
        check(c.presentedFrame(1, captureNs + 402_000_000L) == 480,
                "true fresh capture may be consumed");
        c.stop(1);
        check(c.presentedFrame(1, captureNs + 403_000_000L) == -1, "stop stays terminal");
        var speaker = new GermanSpeakerTailProofGate();
        speaker.begin(1, 48_000);
        check(speaker.markRawDrained(1, 480, 480), "matching raw drain");
        check(speaker.observeCapturedTail(1,
                VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                480, 480, 480, captureNs, pollNs), "valid but old final tail");
        check(!speaker.permitNextChunk(1, captureNs + 380_000_000L),
                "old final capture must not lease next German chunk");
        check(speaker.observeCapturedTail(1,
                VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                480, 480, 480, captureNs + 400_000_000L, captureNs + 401_000_000L),
                "fresh follow-on external capture");
        check(speaker.permitNextChunk(1, captureNs + 402_000_000L),
                "new capture permits exactly one next chunk");
        check(!speaker.permitNextChunk(1, captureNs + 403_000_000L),
                "fresh permit is single use");
        System.out.println("VOICE_CAPTURE_FRESHNESS_PASS checks="+checks);
    }
}
