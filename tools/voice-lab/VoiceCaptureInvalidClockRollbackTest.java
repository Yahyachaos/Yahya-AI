/** JVM-only regression: invalid external speaker claims must not permit backdated recapture. */
public final class VoiceCaptureInvalidClockRollbackTest {
    private static int checks;
    private static void ok(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
        checks++;
    }
    private static boolean claim(VoiceVerifiedPresentationClock clock, long id,
            long presented, long raw, long accepted, long capture, long poll) {
        return clock.observe(id, VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                presented, raw, accepted, capture, poll);
    }
    public static void main(String[] args) {
        long t = 1_000_000_000L;
        for (int i = 0; i < 120; i++) {
            VoiceVerifiedPresentationClock clock = new VoiceVerifiedPresentationClock();
            long id = i + 1;
            clock.begin(id, 48000);
            ok(claim(clock, id, 10, 20, 20, t, t + 1_000_000), "initial speaker claim");
            long future = t + (i + 20L) * 1_000_000;
            // Current-epoch external claim has plausible monotonic capture time,
            // but invalid accepted-vs-raw frames. Its clock is still observable.
            ok(!claim(clock, id, 11, 24, 23, future, future + 1_000_000),
                    "malformed newer capture rejected");
            ok(clock.presentedFrame(id, future + 2_000_000) == -1,
                    "bad sample clears old speaker proof");
            // Reusing an *older* capture would rewind the observed physical clock.
            long rewind = future - 1_000_000;
            ok(!claim(clock, id, 11, 24, 24, rewind, rewind + 1_000_000),
                    "later valid-shaped capture must not backdate an invalid external event");
            ok(clock.presentedFrame(id, future + 3_000_000) == -1,
                    "backdated capture may not restore next-sentence proof");
            ok(!claim(clock, id, 12, 24, 24, future + 4_000_000, future + 5_000_000),
                    "contradicted capture epoch stays poisoned");
            clock.begin(id + 1000, 48000);
            ok(claim(clock, id + 1000, 10, 20, 20, future + 6_000_000, future + 7_000_000),
                    "new audio epoch can recover independently");
        }
        // A type-only RAW head shortcut does not assert any external capture provenance.
        VoiceVerifiedPresentationClock raw = new VoiceVerifiedPresentationClock();
        raw.begin(1, 48000);
        ok(!raw.observe(1, VoiceVerifiedPresentationClock.EvidenceKind.RAW_HEAD_ONLY,
                10, 20, 20, t + 10_000_000, t + 11_000_000), "raw shortcut rejected");
        ok(claim(raw, 1, 10, 20, 20, t + 10_000_000, t + 11_000_000),
                "valid external sample can replace raw-only shortcut at the same timestamp");
        // End-to-end *contract* gate: a bad newer speaker claim cannot be
        // laundered into permission to speak the following German chunk.
        GermanSpeakerTailProofGate speaker = new GermanSpeakerTailProofGate();
        speaker.begin(1, 48000);
        ok(speaker.markRawDrained(1, 24, 24), "speaker gate knows raw drain");
        ok(speaker.observeCapturedTail(1,
                VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                10, 24, 24, t, t + 1_000_000), "original incomplete speaker capture");
        ok(!speaker.observeCapturedTail(1,
                VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                25, 24, 24, t + 80_000_000, t + 81_000_000),
                "over-presented late claim rejected by clock");
        ok(!speaker.observeCapturedTail(1,
                VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                24, 24, 24, t + 79_000_000, t + 80_000_000),
                "backdated full-tail claim rejected");
        ok(!speaker.permitNextChunk(1, t + 82_000_000),
                "no next German chunk after an out-of-order speaker claim");
        System.out.println("VOICE_CAPTURE_INVALID_CLOCK_ROLLBACK_PASS checks=" + checks);
    }
}
