import java.util.List;
import java.util.Random;

/**
 * Independent transcript oracle for STRICT speaker-gated synthetic event replays.
 * No capture device is connected; EXTERNAL_CAPTURE is intentionally a test claim.
 */
public final class GermanStreamingSpeakerGatedReplayTest {
    private static int checks;
    private static void ok(boolean condition, String label) {
        if (!condition) throw new AssertionError("FAIL " + label);
        checks++;
    }
    private static void eq(Object expected, Object actual, String label) {
        ok(java.util.Objects.equals(expected, actual), label + " expected=" + expected + " got=" + actual);
    }
    private static void rejected(boolean accepted, String label) { ok(!accepted, label); }
    private static void newPlanRejected(GermanStreamingSpeakerGatedSession session, String label) {
        boolean threw = false;
        try { session.begin("Unerlaubt!"); }
        catch (IllegalStateException ex) { threw = true; }
        ok(threw, label);
    }

    public static void main(String[] args) {
        String[][] references = {
            {"Erste.", "Zweite.", "Dritte."},
            {"Hallo!", "Weiter?", "Fertig."},
            {"Heute ist Donnerstag.", "Morgen kommt Freitag.", "Bis dann!"}
        };
        Random rng = new Random(0xA11D10C5L);
        for (int run = 0; run < 120; run++) {
            String[] chunks = references[run % references.length];
            String text = String.join(" ", chunks);
            GermanStreamingSpeakerGatedSession session = new GermanStreamingSpeakerGatedSession(48000);
            long generation = session.begin(text);
            long nowMs = 100L + run * 10000L;
            long nowNs = 1_000_000_000L + run * 10_000_000_000L;
            GermanStreamingRawDrainCoordinator.Dispatch next = session.lease(generation, ++nowMs, nowNs);
            ok(next != null, "run " + run + " initial dispatch");
            StringBuilder independentHeard = new StringBuilder();
            for (int i = 0; i < chunks.length; i++) {
                GermanStreamingRawDrainCoordinator.Dispatch d = next;
                eq(chunks[i], d.text, "run " + run + " expected exact chunk " + i);
                eq(i, session.confirmedChunks(), "run " + run + " no skipped chunk");
                int frames = 64 + rng.nextInt(150);
                ok(session.prepared(d, ++nowMs), "run " + run + " prepared");
                int first = 1 + rng.nextInt(frames - 1);
                ok(session.acceptedPcm(d, first, VoiceLifecycleContract.Mouth.OPEN, ++nowMs),
                        "run " + run + " partial PCM accepted");
                if (rng.nextInt(5) == 0) {
                    boolean killed = rng.nextBoolean();
                    if (killed) ok(session.engineDied(d, ++nowMs), "run " + run + " TTS killed");
                    else ok(session.interrupt(d, ++nowMs), "run " + run + " interrupted");
                    eq(GermanStreamingDeliveryLedger.Phase.UNCERTAIN, session.phase(),
                            "run " + run + " unconfirmed partial speech");
                    eq(i, session.confirmedChunks(), "run " + run + " partial audio not confirmed");
                    rejected(session.playbackHead(d, frames, ++nowMs),
                            "run " + run + " late original playback rejected");
                    long oldId = generation;
                    generation = session.restartProvenSilent(oldId);
                    ok(generation > oldId, "run " + run + " externally asserted silent restart");
                    d = session.lease(generation, ++nowMs, nowNs += 10_000_000L);
                    ok(d != null, "run " + run + " restart lease");
                    eq(chunks[i], d.text, "run " + run + " replay same text");
                    ok(session.prepared(d, ++nowMs), "run " + run + " replay prepared");
                    ok(session.acceptedPcm(d, frames, VoiceLifecycleContract.Mouth.LABIAL, ++nowMs),
                            "run " + run + " replay accepted frames");
                } else {
                    ok(session.acceptedPcm(d, frames-first, VoiceLifecycleContract.Mouth.LABIAL, ++nowMs),
                            "run " + run + " remaining PCM accepted");
                }
                if (rng.nextBoolean()) {
                    ok(session.synthesisEnded(d, ++nowMs), "run " + run + " synthesis before drain");
                    ok(session.playbackHead(d, frames-1, ++nowMs),
                            "run " + run + " head one frame short");
                    eq(i, session.confirmedChunks(), "run " + run + " cannot confirm early");
                    ok(session.playbackHead(d, frames, ++nowMs), "run " + run + " final head");
                } else {
                    ok(session.playbackHead(d, frames-1, ++nowMs), "run " + run + " head before synth");
                    eq(i, session.confirmedChunks(), "run " + run + " premature drain blocked");
                    ok(session.synthesisEnded(d, ++nowMs), "run " + run + " synthesis after head");
                    ok(session.playbackHead(d, frames, ++nowMs), "run " + run + " final head");
                }
                if (independentHeard.length() != 0) independentHeard.append(' ');
                independentHeard.append(chunks[i]);
                eq(i+1, session.confirmedChunks(), "run " + run + " exactly one chunk ack");
                eq(independentHeard.toString(), session.confirmedText(),
                        "run " + run + " independent heard-text prefix");
                ok(session.hasUnprovenSpeakerTail(), "run " + run + " pending external speaker tail");
                rejected(session.playbackHead(d, frames, ++nowMs),
                        "run " + run + " old raw callback cannot ack twice");
                rejected(session.observeSpeakerTail(d.audioGeneration,
                        VoiceVerifiedPresentationClock.EvidenceKind.RAW_HEAD_ONLY,
                        frames, frames, frames, nowNs, nowNs+1_000_000L),
                        "run " + run + " typed raw-only proof rejected");
                if (i < chunks.length - 1) {
                    next = session.lease(generation, ++nowMs, nowNs + 2_000_000L);
                    eq(null, next, "run " + run + " no new chunk without acoustic proof");
                } else {
                    newPlanRejected(session, "run " + run + " no next utterance without final proof");
                    rejected(session.confirmFinalTail(nowNs + 2_000_000L),
                            "run " + run + " final tail cannot pass with raw only");
                }
                ok(session.observeSpeakerTail(d.audioGeneration,
                        VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                        frames-1, frames, frames, nowNs+10_000_000L, nowNs+11_000_000L),
                        "run " + run + " partially presented claim");
                if (i < chunks.length - 1) {
                    eq(null, session.lease(generation, ++nowMs, nowNs+12_000_000L),
                            "run " + run + " partial speaker proof cannot advance");
                } else rejected(session.confirmFinalTail(nowNs+12_000_000L),
                        "run " + run + " partial final proof not enough");
                nowNs += 100_000_000L;
                ok(session.observeSpeakerTail(d.audioGeneration,
                        VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                        frames, frames, frames, nowNs, nowNs+1_000_000L),
                        "run " + run + " full presenter claim");
                if (rng.nextBoolean()) {
                    // Invalidated proof cannot be resurrected by moving the poll clock backwards.
                    if (i < chunks.length - 1)
                        eq(null, session.lease(generation, ++nowMs, nowNs+302_000_000L),
                                "run " + run + " expired proof cannot lease");
                    else rejected(session.confirmFinalTail(nowNs+302_000_000L),
                            "run " + run + " expired final proof cannot permit");
                    if (i < chunks.length - 1)
                        eq(null, session.lease(generation, ++nowMs, nowNs+102_000_000L),
                                "run " + run + " stale clock cannot resurrect proof");
                    else rejected(session.confirmFinalTail(nowNs+102_000_000L),
                            "run " + run + " stale clock cannot resurrect final");
                    nowNs += 320_000_000L;
                    ok(session.observeSpeakerTail(d.audioGeneration,
                            VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                            frames, frames, frames, nowNs, nowNs+1_000_000L),
                            "run " + run + " refreshed external tail proof");
                }
                if (i < chunks.length-1) {
                    next = session.lease(generation, ++nowMs, nowNs+2_000_000L);
                    ok(next != null, "run " + run + " permitted next chunk with external claim");
                    rejected(session.observeSpeakerTail(d.audioGeneration,
                            VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                            frames, frames, frames, nowNs+3_000_000L, nowNs+4_000_000L),
                            "run " + run + " previous audio cannot inject proof into successor");
                } else {
                    ok(session.confirmFinalTail(nowNs+2_000_000L),
                            "run " + run + " final actual claim releases final tail");
                    rejected(session.confirmFinalTail(nowNs+3_000_000L),
                            "run " + run + " final permit one time only");
                }
                nowNs += 1_000_000_000L;
            }
            eq(GermanStreamingDeliveryLedger.Phase.COMPLETE, session.phase(),
                    "run " + run + " completed all German chunks");
            eq(text, session.confirmedText(), "run " + run + " full independent transcript");
            ok(!session.hasUnprovenSpeakerTail(), "run " + run + " no pending speaker claim");
        }
        System.out.println("VOICE_GERMAN_GATED_REPLAY_PASS checks=" + checks);
    }
}
