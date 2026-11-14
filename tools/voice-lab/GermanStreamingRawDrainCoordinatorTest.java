import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/** No device I/O. Source-exact deterministic contract checks for post-v83 wiring. */
public final class GermanStreamingRawDrainCoordinatorTest {
    private static int checks;
    private static void ok(boolean result, String label) {
        if (!result) throw new AssertionError("FAIL " + label);
        checks++;
    }
    private static void eq(Object expected, Object actual, String label) {
        ok(Objects.equals(expected, actual), label + " expected=" + expected + " actual=" + actual);
    }
    private static long now;
    private static long advance() { return ++now; }
    private static String norm(String s) { return s.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim(); }

    public static void main(String[] args) {
        GermanStreamingRawDrainCoordinator c = new GermanStreamingRawDrainCoordinator();
        now = 100;
        long g = c.begin("Erster Satz. Zweiter Satz.");
        eq(2, c.chunkCount(), "two German sentences");
        GermanStreamingRawDrainCoordinator.Dispatch first = c.lease(g, advance());
        ok(first != null, "first bound dispatch");
        eq(null, c.lease(g, advance()), "no duplicate lease");
        ok(c.prepared(first, advance()), "TTS preparation");
        ok(c.canAcceptPcm(first, 100, VoiceLifecycleContract.Mouth.OPEN, advance()), "PCM preflight");
        ok(c.acceptedPcm(first, 60, VoiceLifecycleContract.Mouth.OPEN, advance()), "partial PCM write");
        ok(c.acceptedPcm(first, 40, VoiceLifecycleContract.Mouth.LABIAL, advance()), "remaining frames");
        eq(100L, c.acceptedFrames(), "accepted only actual PCM frames");
        ok(c.synthesisEnded(first, advance()), "synthesis complete");
        eq(0, c.confirmedChunks(), "synthesis alone never confirms speech");
        eq(GermanStreamingDeliveryLedger.Phase.IN_FLIGHT, c.phase(), "still in flight after synth");
        ok(c.playbackHead(first, 99L, advance()), "partial raw head");
        eq(0, c.confirmedChunks(), "one raw frame short must not advance text");
        ok(!c.playbackHead(first, 101L, advance()), "unaccepted raw frame rejected");
        eq(0, c.confirmedChunks(), "invalid head does not confirm text");
        ok(c.playbackHead(first, 100L, advance()), "physical raw head fully consumed");
        eq(1, c.confirmedChunks(), "exact drain acknowledges once");
        eq(GermanStreamingDeliveryLedger.Phase.READY, c.phase(), "next German chunk ready");
        ok(!c.playbackHead(first, 100L, advance()), "stale previous head rejected");
        ok(!c.synthesisEnded(first, advance()), "stale previous synth rejected");
        GermanStreamingRawDrainCoordinator.Dispatch second = c.lease(g, advance());
        ok(second != null && second.audioGeneration > first.audioGeneration, "fresh audio epoch");
        eq("Zweiter Satz.", second.text, "second chunk text");
        ok(c.prepared(second, advance()), "second prepare");
        ok(!c.playbackHead(second, 50L, advance()), "head cannot fabricate unwritten PCM");
        ok(c.acceptedPcm(second, 80, VoiceLifecycleContract.Mouth.TEETH, advance()), "second PCM");
        ok(c.playbackHead(second, 80L, advance()), "raw drain before synth done");
        eq(1, c.confirmedChunks(), "raw head alone cannot ack before synth done");
        ok(c.synthesisEnded(second, advance()), "synthesis done after full raw drain");
        eq(GermanStreamingDeliveryLedger.Phase.COMPLETE, c.phase(), "whole plan confirmed");
        eq(norm("Erster Satz. Zweiter Satz."), c.confirmedText(), "no missing text");
        ok(!c.engineDied(first, advance()), "old engine death cannot poison completed speech");

        g = c.begin("Hallo! Noch einmal.");
        GermanStreamingRawDrainCoordinator.Dispatch interrupted = c.lease(g, advance());
        ok(c.prepared(interrupted, advance()), "prepare before engine kill");
        ok(c.acceptedPcm(interrupted, 120, VoiceLifecycleContract.Mouth.OPEN, advance()), "PCM before engine death");
        ok(c.engineDied(interrupted, advance()), "engine death terminal");
        eq(GermanStreamingDeliveryLedger.Phase.UNCERTAIN, c.phase(), "engine death marks words uncertain");
        eq(0, c.confirmedChunks(), "queued PCM not silently counted as heard");
        ok(!c.playbackHead(interrupted, 120L, advance()), "late dead track head ignored");
        eq(-1L, c.restart(g, GermanStreamingDeliveryLedger.Recovery.CONTINUE_WITH_NO_IN_FLIGHT),
                "cannot auto-resume uncertain audio");
        long g2 = c.restart(g, GermanStreamingDeliveryLedger.Recovery.REPLAY_ONLY_IF_EXTERNALLY_PROVEN_SILENT);
        ok(g2 > g, "external proven-silence claim rotates generation");
        GermanStreamingRawDrainCoordinator.Dispatch retry = c.lease(g2, advance());
        eq(interrupted.text, retry.text, "replay same text only with explicit proof claim");
        ok(!c.prepared(interrupted, advance()), "old callback cannot prepare replay");
        ok(c.prepared(retry, advance()), "fresh callback prepares replay");
        ok(c.acceptedPcm(retry, 20, VoiceLifecycleContract.Mouth.LABIAL, advance()), "retry PCM");
        ok(c.interrupt(retry, advance()), "interrupt active retry");
        eq(GermanStreamingDeliveryLedger.Phase.UNCERTAIN, c.phase(), "interrupt remains audibly ambiguous");
        long g3 = c.restart(g2, GermanStreamingDeliveryLedger.Recovery.ADVANCE_ONLY_IF_EXTERNALLY_PROVEN_FULL_DRAIN);
        ok(g3 > g2, "explicit fully-drained external proof claim");
        eq(1, c.confirmedChunks(), "previously uncertain chunk advanced only by external decision");
        GermanStreamingRawDrainCoordinator.Dispatch follow = c.lease(g3, advance());
        eq("Noch einmal.", follow.text, "continuation after external full-drain claim");
        ok(!c.synthesisEnded(retry, advance()), "old synth cannot close new lease");
        ok(c.prepared(follow, advance()), "next prepare after recovery");
        ok(c.acceptedPcm(follow, 20, VoiceLifecycleContract.Mouth.ROUNDED, advance()), "next PCM");
        ok(c.synthesisEnded(follow, advance()), "next synthesis end");
        ok(c.playbackHead(follow, 20, advance()), "next raw drain");
        eq(GermanStreamingDeliveryLedger.Phase.COMPLETE, c.phase(), "recovered plan complete");

        g = c.begin("Ausfall. Zweiter.");
        GermanStreamingRawDrainCoordinator.Dispatch timed = c.lease(g, advance());
        ok(c.tick(now + 6001), "engine preparation timeout without callback");
        now += 6001;
        eq(GermanStreamingDeliveryLedger.Phase.UNCERTAIN, c.phase(), "timeout uncertain");
        ok(!c.prepared(timed, advance()), "late preparation ignored after timeout");
        eq(-1L, c.restart(g, GermanStreamingDeliveryLedger.Recovery.CONTINUE_WITH_NO_IN_FLIGHT),
                "timeout cannot automatically skip");
        long g4 = c.restart(g, GermanStreamingDeliveryLedger.Recovery.REPLAY_ONLY_IF_EXTERNALLY_PROVEN_SILENT);
        ok(g4 > g, "external proof for stalled prepare");
        GermanStreamingRawDrainCoordinator.Dispatch stalled = c.lease(g4, advance());
        ok(c.prepared(stalled, advance()), "prepared stalled test");
        ok(c.acceptedPcm(stalled, 25, VoiceLifecycleContract.Mouth.OPEN, advance()), "written stalled test");
        ok(c.tick(now + 3501), "playback stall watchdog");
        now += 3501;
        eq(GermanStreamingDeliveryLedger.Phase.UNCERTAIN, c.phase(), "stalled speaker not automatically acked");

        Random random = new Random(0xCE11C0DEL);
        String[] corpus = {
            "Guten Morgen. Ich bin Celin. Wie geht es dir?",
            "Am 29.10.2026. Danach Dr. Lang. Fertig!",
            "Mail max@example.de. Fertig!"
        };
        for (int run = 0; run < 100; run++) {
            GermanStreamingRawDrainCoordinator s = new GermanStreamingRawDrainCoordinator();
            long id = s.begin(corpus[run % corpus.length]);
            List<String> confirmed = new ArrayList<>();
            int loops = 0;
            while (s.phase() != GermanStreamingDeliveryLedger.Phase.COMPLETE) {
                ok(++loops < 70, "bounded randomized run " + run);
                GermanStreamingRawDrainCoordinator.Dispatch d = s.lease(id, advance());
                ok(d != null, "leased chunk " + run);
                ok(s.prepared(d, advance()), "prepared chunk " + run);
                int total = 10 + random.nextInt(100);
                int part = 1 + random.nextInt(total);
                ok(s.acceptedPcm(d, part, VoiceLifecycleContract.Mouth.OPEN, advance()), "first accepted PCM");
                if (part < total) ok(s.acceptedPcm(d, total - part,
                        VoiceLifecycleContract.Mouth.LABIAL, advance()), "remaining accepted PCM");
                if (random.nextBoolean()) {
                    ok(s.synthesisEnded(d, advance()), "synth first");
                    long near = total - 1L;
                    ok(s.playbackHead(d, near, advance()), "raw head partial");
                    eq(confirmed.size(), s.confirmedChunks(), "no early ack");
                } else {
                    ok(s.playbackHead(d, total - 1L, advance()), "head before synth");
                    ok(s.synthesisEnded(d, advance()), "synth last");
                }
                if (random.nextInt(5) == 0) {
                    ok(s.interrupt(d, advance()), "seeded interruption");
                    boolean externalFullDrain = random.nextBoolean();
                    if (externalFullDrain) confirmed.add(d.text);
                    long old = id;
                    id = s.restart(id, externalFullDrain
                            ? GermanStreamingDeliveryLedger.Recovery.ADVANCE_ONLY_IF_EXTERNALLY_PROVEN_FULL_DRAIN
                            : GermanStreamingDeliveryLedger.Recovery.REPLAY_ONLY_IF_EXTERNALLY_PROVEN_SILENT);
                    ok(id > old, "new generation after confirmed external policy");
                    ok(!s.playbackHead(d, total, advance()), "old head rejected after restart");
                } else {
                    ok(s.playbackHead(d, total, advance()), "full raw head");
                    confirmed.add(d.text);
                }
                eq(confirmed.size(), s.confirmedChunks(), "confirmed prefix size " + run);
                eq(String.join(" ", confirmed), s.confirmedText(), "confirmed prefix transcript " + run);
            }
            eq(norm(corpus[run % corpus.length]), String.join(" ", confirmed), "complete text " + run);
        }
        System.out.println("VOICE_GERMAN_RAW_DRAIN_COORDINATOR_PASS checks=" + checks);
    }
}
