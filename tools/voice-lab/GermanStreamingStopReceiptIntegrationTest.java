/** FUTURE-ONLY exact-coordinator stop identity; no actual speaker-stop proof. */
public final class GermanStreamingStopReceiptIntegrationTest {
    private static int checks;
    private static void check(boolean b, String why) { if (!b) throw new AssertionError(why); checks++; }
    private static void reject(boolean b, String why) { check(!b, why); }
    private static final VoiceExplicitAbortGate.UserAction USER =
            VoiceExplicitAbortGate.UserAction.ABANDON_UNKNOWN_SPEECH_AND_START_NEW_TEXT;
    private static final VoiceExplicitAbortGate.OldOutputStop LEGACY =
            VoiceExplicitAbortGate.OldOutputStop.EXTERNALLY_CONFIRMED_STOPPED;

    private static GermanStreamingRawDrainCoordinator.Dispatch drained(
            GermanStreamingSpeakerGatedSession s, long plan, long ms, long ns) {
        var d = s.lease(plan, ms, ns);
        check(d != null, "lease");
        check(s.prepared(d, ms + 1), "prepare");
        check(s.acceptedPcm(d, 96, VoiceLifecycleContract.Mouth.OPEN, ms + 2), "PCM");
        check(s.synthesisEnded(d, ms + 3), "synthesis");
        check(s.playbackHead(d, 96, ms + 4), "raw head drain");
        check(s.hasUnprovenSpeakerTail(), "raw is not speaker proof");
        return d;
    }

    /** Old enum-only STOP used to allow a new plan without any epoch-bound receipt. */
    private static void legacyBypassMustFailClosed() {
        var s = new GermanStreamingSpeakerGatedSession(48000);
        long plan = s.begin("Erster. Zweiter.");
        var d = drained(s, plan, 101, 1_000_000_000L);
        reject(s.abortAndBeginNewSession(plan, USER, LEGACY, "Unberechtigt.") != null,
                "unscoped enum-only stop must fail closed");
        check(s.generation() == plan, "legacy stop may not replace plan");
        check(s.hasUnprovenSpeakerTail(), "legacy stop preserves unknown audio");
        reject(s.requestExplicitAbortStop(plan + 1, 1_010_000_000L) != null,
                "cannot request stop on wrong plan");
        var request = s.requestExplicitAbortStop(plan, 1_010_000_000L);
        check(request != null, "matching stop request");
        check(request.planGeneration == plan && request.audioGeneration == d.audioGeneration,
                "stop receipt bound to old audio and plan");
        reject(s.lease(plan, 110, 1_020_000_000L) != null,
                "no next raw lease while STOP unresolved");
        reject(s.acknowledgeExplicitAbortStop(request, plan, d.audioGeneration + 1, 1_030_000_000L),
                "wrong audio cannot ACK");
        reject(s.abortAndBeginNewSession(plan, USER, request, "No.", 1_035_000_000L) != null,
                "ACK must precede abandon");
        check(s.acknowledgeExplicitAbortStop(request, plan, d.audioGeneration, 1_040_000_000L),
                "exact old STOP claim");
        reject(s.acknowledgeExplicitAbortStop(request, plan, d.audioGeneration, 1_050_000_000L),
                "STOP only accepted once");
        reject(s.abortAndBeginNewSession(plan, null, request, "No.", 1_055_000_000L) != null,
                "user action required");
        reject(s.abortAndBeginNewSession(plan + 1, USER, request, "No.", 1_060_000_000L) != null,
                "wrong plan cannot consume STOP");
        var receipt = s.abortAndBeginNewSession(plan, USER, request, "Ganz neu. Fertig.",
                1_070_000_000L);
        check(receipt != null && receipt.previousSpeechAudibilityUnknown,
                "new plan only after bound user+STOP claim, audibility unknown");
        check(receipt.previousRawDrainedChunks == 1 && "Erster.".equals(receipt.previousRawDrainedText),
                "only raw-drained diagnostic text");
        check(receipt.unresolvedSpeakerAudioGeneration == d.audioGeneration,
                "diagnostic original speaker epoch");
        reject(s.abortAndBeginNewSession(plan, USER, request, "Replayed.", 1_080_000_000L) != null,
                "old request cannot replay");
        reject(s.acknowledgeExplicitAbortStop(request, plan, d.audioGeneration, 1_081_000_000L),
                "old stop callback rejected");
        reject(s.observeSpeakerTail(d.audioGeneration,
                VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                96,96,96,1_090_000_000L,1_091_000_000L), "old capture rejected");
        var next = s.lease(receipt.newPlanGeneration, 111, 1_100_000_000L);
        check(next != null && next.audioGeneration > d.audioGeneration,
                "new epoch cannot reuse previous speaker output");
        check("Ganz neu.".equals(next.text), "new first phrase is not old replay");
    }

    private static void timeoutRollbackAndRetry() {
        for (int round = 0; round < 150; round++) {
            var s = new GermanStreamingSpeakerGatedSession(48000);
            long plan = s.begin("Hallo. Weiter.");
            long ns = 3_000_000_000L + round * 2_000_000_000L;
            var old = drained(s, plan, 100, ns);
            var expired = s.requestExplicitAbortStop(plan, ns + 10_000_000L);
            check(expired != null, "stop request");
            reject(s.requestExplicitAbortStop(plan, ns + 11_000_000L) != null,
                    "no concurrent request");
            reject(s.acknowledgeExplicitAbortStop(expired, plan, old.audioGeneration,
                    ns + 520_000_000L), "expired ACK cannot be accepted");
            reject(s.abortAndBeginNewSession(plan, USER, expired, "Invalid.", ns + 521_000_000L)
                    != null, "expired ACK cannot authorize abort");
            reject(s.requestExplicitAbortStop(plan, ns + 519_000_000L) != null,
                    "backdated STOP retry rejected");
            var current = s.requestExplicitAbortStop(plan, ns + 530_000_000L);
            check(current != null && current.nonce > expired.nonce,
                    "new nonce after expired stop");
            reject(s.acknowledgeExplicitAbortStop(expired, plan, old.audioGeneration,
                    ns + 540_000_000L), "old ACK cannot stop new lease");
            reject(s.acknowledgeExplicitAbortStop(current, plan + 1, old.audioGeneration,
                    ns + 545_000_000L), "wrong plan cannot ACK");
            check(s.acknowledgeExplicitAbortStop(current, plan, old.audioGeneration,
                    ns + 550_000_000L), "new STOP claim accepted");
            reject(s.abortAndBeginNewSession(plan, USER, current, "Backdated.",
                    ns + 549_000_000L) != null, "abort time cannot roll back");
            reject(s.abortAndBeginNewSession(plan, USER, current, "Late.",
                    ns + 551_000_000L) != null, "poisoned/consumed request not resurrected");
            var third = s.requestExplicitAbortStop(plan, ns + 560_000_000L);
            check(third != null, "fresh retry after clock poison");
            check(s.acknowledgeExplicitAbortStop(third, plan, old.audioGeneration,
                    ns + 570_000_000L), "fresh ACK");
            var receipt = s.abortAndBeginNewSession(plan, USER, third, "Frischer Text.",
                    ns + 580_000_000L);
            check(receipt != null, "new session after explicit stop reset");
            check(s.generation() == receipt.newPlanGeneration,
                    "new plan generation current");
            reject(s.playbackHead(old, 96, 107), "old callbacks rejected");
        }
    }

    private static void cancelAndEngineDeath() {
        var s = new GermanStreamingSpeakerGatedSession(48000);
        long plan = s.begin("Ungewiss. Danach.");
        var d = s.lease(plan, 100, 10_000_000_000L);
        check(d != null && s.prepared(d, 101), "engine ready");
        check(s.acceptedPcm(d, 80, VoiceLifecycleContract.Mouth.LABIAL, 102), "engine PCM");
        check(s.engineDied(d, 103), "engine died");
        check(s.phase() == GermanStreamingDeliveryLedger.Phase.UNCERTAIN,
                "uncertain old audio");
        var old = s.requestExplicitAbortStop(plan, 10_100_000_000L);
        check(old != null && old.audioGeneration == d.audioGeneration,
                "old engine STOP receipt bound to original audio");
        reject(s.acknowledgeExplicitAbortStop(old, plan + 1, d.audioGeneration,
                10_110_000_000L), "wrong plan old engine");
        check(s.cancelExplicitAbortStop(old), "cancel STOP");
        reject(s.acknowledgeExplicitAbortStop(old, plan, d.audioGeneration, 10_120_000_000L),
                "cancelled late ACK rejected");
        reject(s.abortAndBeginNewSession(plan, USER, old, "No.", 10_130_000_000L) != null,
                "cancelled request cannot abort");
        var fresh = s.requestExplicitAbortStop(plan, 10_140_000_000L);
        check(fresh != null && fresh.nonce > old.nonce, "new engine STOP nonce");
        check(s.acknowledgeExplicitAbortStop(fresh, plan, d.audioGeneration, 10_150_000_000L),
                "fresh engine stop claim");
        var receipt = s.abortAndBeginNewSession(plan, USER, fresh, "Neustart.",
                10_160_000_000L);
        check(receipt != null && receipt.previousRawDrainedChunks == 0,
                "not falsely counted as heard");
        reject(s.prepared(d, 104), "old TTS callback rejected after replacement");
        check(s.lease(receipt.newPlanGeneration, 105, 10_170_000_000L) != null,
                "fresh speech dispatch");
    }

    public static void main(String[] args) {
        legacyBypassMustFailClosed();
        timeoutRollbackAndRetry();
        cancelAndEngineDeath();
        System.out.println("VOICE_STOP_RECEIPT_INTEGRATION_PASS checks=" + checks);
    }
}
