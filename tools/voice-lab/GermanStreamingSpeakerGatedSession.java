/**
 * FUTURE-ONLY strict composition of the two experimental gates. Unlike using
 * GermanStreamingRawDrainCoordinator directly, callers cannot lease the next
 * text chunk while a prior one has no fresh final EXTERNAL_CAPTURE tail claim.
 * Neither this class nor its typed evidence proves any actual Samsung sound.
 */
public final class GermanStreamingSpeakerGatedSession {
    private final GermanStreamingRawDrainCoordinator raw = new GermanStreamingRawDrainCoordinator();
    private final GermanSpeakerTailProofGate speaker = new GermanSpeakerTailProofGate();
    // Strict future-only STOP identity; 500ms is an experimental CLAIM TTL.
    private final VoiceOutputStopReceiptGate outputStops =
            new VoiceOutputStopReceiptGate(500_000_000L);
    private VoiceOutputStopReceiptGate.StopRequest pendingStopRequest;
    private long lastAudioGeneration;
    private final int sampleRate;
    private GermanStreamingRawDrainCoordinator.Dispatch pendingTail;
    private GermanStreamingRawDrainCoordinator.Dispatch activeDispatch;

    public GermanStreamingSpeakerGatedSession(int sampleRate) {
        if (sampleRate < 8000 || sampleRate > 192000)
            throw new IllegalArgumentException("invalid device rate");
        this.sampleRate = sampleRate;
    }

    public synchronized long begin(String text) {
        if (pendingTail != null) throw new IllegalStateException("speaker tail not proven");
        return raw.begin(text);
    }

    /** Raw-drain diagnostics only; previous audible words remain UNKNOWN. */
    public static final class AbandonReceipt {
        public final long previousPlanGeneration, newPlanGeneration;
        public final int previousRawDrainedChunks;
        public final String previousRawDrainedText;
        public final long unresolvedSpeakerAudioGeneration;
        public final boolean previousSpeechAudibilityUnknown = true;
        private AbandonReceipt(long previousId, long newId, int rawCount,
                String rawText, long unresolvedAudioId) {
            previousPlanGeneration = previousId;
            newPlanGeneration = newId;
            previousRawDrainedChunks = rawCount;
            previousRawDrainedText = rawText;
            unresolvedSpeakerAudioGeneration = unresolvedAudioId;
        }
    }

    /** True only for an old session with uncertain physical output and no active dispatch. */
    private boolean abandonable() {
        GermanStreamingDeliveryLedger.Phase phase = raw.phase();
        return activeDispatch == null && (
                (pendingTail != null && (phase == GermanStreamingDeliveryLedger.Phase.READY
                        || phase == GermanStreamingDeliveryLedger.Phase.COMPLETE))
                || (pendingTail == null && phase == GermanStreamingDeliveryLedger.Phase.UNCERTAIN));
    }

    /** Issue a one-shot STOP request bound to the currently unresolved old audio epoch. */
    public synchronized VoiceOutputStopReceiptGate.StopRequest requestExplicitAbortStop(
            long expectedPlanGeneration, long nowNs) {
        if (!abandonable() || pendingStopRequest != null
                || expectedPlanGeneration <= 0 || expectedPlanGeneration != raw.generation()
                || lastAudioGeneration <= 0 || nowNs < 0) return null;
        pendingStopRequest = outputStops.request(raw.generation(), lastAudioGeneration, nowNs);
        return pendingStopRequest;
    }

    /** The STOP acknowledgement is still a caller-supplied CLAIM, not hardware proof. */
    public synchronized boolean acknowledgeExplicitAbortStop(
            VoiceOutputStopReceiptGate.StopRequest request,
            long observedPlanGeneration, long observedAudioGeneration, long stoppedNs) {
        if (request == null || request != pendingStopRequest) return false;
        boolean accepted = outputStops.acknowledgeStopped(request,
                observedPlanGeneration, observedAudioGeneration, stoppedNs);
        if (!outputStops.hasPendingStop()) pendingStopRequest = null;
        return accepted;
    }

    /** Explicit cancellation is required if the old output stop was not confirmed. */
    public synchronized boolean cancelExplicitAbortStop(
            VoiceOutputStopReceiptGate.StopRequest request) {
        if (request == null || request != pendingStopRequest
                || !outputStops.invalidate(request)) return false;
        pendingStopRequest = null;
        return true;
    }

    /**
     * Explicit user-directed new plan, never a replay or a speaker-tail ACK.
     * Caller must independently verify actual user intent and physical output STOP.
     * No adapter, microphone, Samsung playback or real human action is present here.
     */
    public synchronized AbandonReceipt abortAndBeginNewSession(long expectedPlanGeneration,
            VoiceExplicitAbortGate.UserAction userAction,
            VoiceOutputStopReceiptGate.StopRequest oldStopRequest, String newText, long nowNs) {
        if (!abandonable() || oldStopRequest == null || oldStopRequest != pendingStopRequest
                || oldStopRequest.planGeneration != expectedPlanGeneration
                || oldStopRequest.audioGeneration != lastAudioGeneration
                // Reject empty/invisible replacement BEFORE consuming one-shot STOP.
                || !VoiceAbortNewTextGate.hasNonInvisibleContent(newText)
                || !VoiceExplicitAbortGate.mayAbandon(expectedPlanGeneration, raw.generation(),
                    activeDispatch != null,
                    pendingTail != null
                            ? VoiceExplicitAbortGate.PriorState.RAW_DRAINED_SPEAKER_UNPROVEN
                            : VoiceExplicitAbortGate.PriorState.INTERRUPTED_UNCERTAIN,
                    userAction, VoiceExplicitAbortGate.OldOutputStop.EXTERNALLY_CONFIRMED_STOPPED))
            return null;
        if (!outputStops.consumeForExplicitAbort(oldStopRequest, expectedPlanGeneration,
                lastAudioGeneration, nowNs)) {
            if (!outputStops.hasPendingStop()) pendingStopRequest = null;
            return null;
        }
        pendingStopRequest = null;
        int oldRawCount = raw.confirmedChunks();
        String oldRawText = raw.confirmedText();
        long oldSpeakerEpoch = pendingTail == null ? -1L : pendingTail.audioGeneration;
        long newGeneration = raw.abandonForExplicitNewPlan(expectedPlanGeneration, newText);
        if (newGeneration <= expectedPlanGeneration) return null;
        if (pendingTail != null) speaker.stop(pendingTail.audioGeneration);
        pendingTail = null;
        activeDispatch = null;
        return new AbandonReceipt(expectedPlanGeneration, newGeneration,
                oldRawCount, oldRawText, oldSpeakerEpoch);
    }

    /** Legacy enum-only STOP is deliberately rejected: it has no epoch/nonce/TTL. */
    @Deprecated
    public synchronized AbandonReceipt abortAndBeginNewSession(long expectedPlanGeneration,
            VoiceExplicitAbortGate.UserAction userAction,
            VoiceExplicitAbortGate.OldOutputStop unscopedStop, String newText) {
        return null;
    }

    /** Refuses lease if the previous raw-drained chunk still lacks speaker proof. */
    public synchronized GermanStreamingRawDrainCoordinator.Dispatch lease(
            long planGeneration, long nowMs, long nowNs) {
        if (pendingStopRequest != null || planGeneration != raw.generation() || nowMs < 0 || nowNs < 0
                || raw.phase() != GermanStreamingDeliveryLedger.Phase.READY) return null;
        if (pendingTail != null) {
            if (!speaker.permitNextChunk(pendingTail.audioGeneration, nowNs)) return null;
            pendingTail = null;
        }
        GermanStreamingRawDrainCoordinator.Dispatch dispatch = raw.lease(planGeneration, nowMs);
        if (dispatch != null) {
            speaker.begin(dispatch.audioGeneration, sampleRate);
            activeDispatch = dispatch;
            lastAudioGeneration = dispatch.audioGeneration;
        }
        return dispatch;
    }

    public synchronized boolean prepared(GermanStreamingRawDrainCoordinator.Dispatch d, long ms) {
        boolean result = raw.prepared(d, ms);
        stopDeadAudioEpoch();
        return result;
    }
    /** Caller must serialize this advisory check with actual PCM writes. */
    public synchronized boolean canAcceptPcm(GermanStreamingRawDrainCoordinator.Dispatch d,
            int frames, VoiceLifecycleContract.Mouth cue, long ms) {
        boolean result = raw.canAcceptPcm(d, frames, cue, ms);
        stopDeadAudioEpoch();
        return result;
    }
    public synchronized boolean acceptedPcm(GermanStreamingRawDrainCoordinator.Dispatch d,
            int actualWrittenFrames, VoiceLifecycleContract.Mouth cue, long ms) {
        boolean result = raw.acceptedPcm(d, actualWrittenFrames, cue, ms);
        stopDeadAudioEpoch();
        return result;
    }
    public synchronized boolean synthesisEnded(GermanStreamingRawDrainCoordinator.Dispatch d, long ms) {
        boolean accepted = raw.synthesisEnded(d, ms);
        if (accepted) captureRawDrainIfComplete(d);
        stopDeadAudioEpoch();
        return accepted;
    }
    public synchronized boolean playbackHead(GermanStreamingRawDrainCoordinator.Dispatch d,
            long frames, long ms) {
        boolean accepted = raw.playbackHead(d, frames, ms);
        if (accepted) captureRawDrainIfComplete(d);
        stopDeadAudioEpoch();
        return accepted;
    }
    public synchronized boolean engineDied(GermanStreamingRawDrainCoordinator.Dispatch d, long ms) {
        boolean result = raw.engineDied(d, ms);
        if (result) {
            speaker.stop(d.audioGeneration);
            if (activeDispatch == d) activeDispatch = null;
        }
        stopDeadAudioEpoch();
        return result;
    }
    public synchronized boolean interrupt(GermanStreamingRawDrainCoordinator.Dispatch d, long ms) {
        boolean result = raw.interrupt(d, ms);
        if (result) {
            speaker.stop(d.audioGeneration);
            if (activeDispatch == d) activeDispatch = null;
        }
        stopDeadAudioEpoch();
        return result;
    }
    public synchronized boolean tick(long ms) {
        boolean result = raw.tick(ms);
        stopDeadAudioEpoch();
        return result;
    }

    /** Only an explicitly proven SILENT recovery is allowed by this gate. */
    public synchronized long restartProvenSilent(long oldPlanGeneration) {
        if (pendingTail != null || pendingStopRequest != null) return -1L;
        stopDeadAudioEpoch();
        return raw.restart(oldPlanGeneration,
                GermanStreamingDeliveryLedger.Recovery.REPLAY_ONLY_IF_EXTERNALLY_PROVEN_SILENT);
    }

    /** Pending raw-drained tails accept only exact-epoch external capture claims. */
    public synchronized boolean observeSpeakerTail(long audioGeneration,
            VoiceVerifiedPresentationClock.EvidenceKind kind, long presentedFrames,
            long rawFrames, long acceptedFrames, long captureNs, long pollNs) {
        if (pendingTail == null || pendingTail.audioGeneration != audioGeneration) return false;
        return speaker.observeCapturedTail(audioGeneration, kind,
                presentedFrames, rawFrames, acceptedFrames, captureNs, pollNs);
    }

    /** A final chunk also needs a speaker-tail proof before a NEW text plan. */
    public synchronized boolean confirmFinalTail(long nowNs) {
        if (pendingStopRequest != null || pendingTail == null
                || raw.phase() != GermanStreamingDeliveryLedger.Phase.COMPLETE)
            return false;
        if (!speaker.permitNextChunk(pendingTail.audioGeneration, nowNs)) return false;
        pendingTail = null;
        return true;
    }

    public synchronized GermanStreamingDeliveryLedger.Phase phase() { return raw.phase(); }
    public synchronized int confirmedChunks() { return raw.confirmedChunks(); }
    public synchronized String confirmedText() { return raw.confirmedText(); }
    public synchronized long generation() { return raw.generation(); }
    public synchronized long acceptedFrames() { return raw.acceptedFrames(); }
    public synchronized long playedFrames() { return raw.playedFrames(); }
    public synchronized boolean hasUnprovenSpeakerTail() { return pendingTail != null; }

    private void captureRawDrainIfComplete(GermanStreamingRawDrainCoordinator.Dispatch d) {
        if (pendingTail != null || d.planGeneration != raw.generation()) return;
        if (raw.phase() != GermanStreamingDeliveryLedger.Phase.READY
                && raw.phase() != GermanStreamingDeliveryLedger.Phase.COMPLETE) return;
        if (speaker.markRawDrained(d.audioGeneration, raw.acceptedFrames(), raw.playedFrames())) {
            pendingTail = d;
            if (activeDispatch == d) activeDispatch = null;
        }
    }

    /** A timeout can fail the raw lifecycle without an explicit engine-death callback. */
    private void stopDeadAudioEpoch() {
        if (activeDispatch != null
                && raw.phase() == GermanStreamingDeliveryLedger.Phase.UNCERTAIN) {
            speaker.stop(activeDispatch.audioGeneration);
            activeDispatch = null;
        }
    }
}
