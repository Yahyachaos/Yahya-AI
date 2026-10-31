/**
 * FUTURE-ONLY, pure-Java wiring between GermanStreamingDeliveryLedger and
 * VoiceLifecycleContract. A per-chunk ticket cannot advance solely because TTS
 * synthesis ended: it requires the SAME audio generation's accepted PCM frames
 * to be fully consumed by the observed monotonic raw playback head.
 *
 * This is NOT proof of acoustic speaker output or words heard. A post-v83 Android
 * adapter must supply actual AudioTrack.write frame counts and a trustworthy raw
 * head, serialize callbacks, and externally resolve interrupted partial speech.
 * No Android runtime is changed or exercised by this contract.
 */
public final class GermanStreamingRawDrainCoordinator {
    public static final class Dispatch {
        public final long planGeneration;
        public final long audioGeneration;
        public final int chunkIndex;
        public final String text;
        private final GermanStreamingDeliveryLedger.Ticket ticket;
        private Dispatch(GermanStreamingDeliveryLedger.Ticket ticket, long audioGeneration) {
            this.ticket = ticket;
            this.planGeneration = ticket.generation;
            this.audioGeneration = audioGeneration;
            this.chunkIndex = ticket.chunkIndex;
            this.text = ticket.text;
        }
    }

    private final GermanStreamingDeliveryLedger plan = new GermanStreamingDeliveryLedger();
    private final VoiceLifecycleContract audio = new VoiceLifecycleContract();
    private Dispatch active;

    /**
     * Explicit-user-abort primitive; NEVER a playback acknowledgement. Its
     * caller must separately confirm user choice and old-output stop. Retaining
     * this coordinator preserves monotonic audio callback epochs across plans.
     */
    public synchronized long abandonForExplicitNewPlan(long expectedGeneration, String newText) {
        GermanStreamingDeliveryLedger.Phase phase = plan.phase();
        if (expectedGeneration <= 0 || expectedGeneration != plan.generation()
                || active != null
                || !(phase == GermanStreamingDeliveryLedger.Phase.READY
                    || phase == GermanStreamingDeliveryLedger.Phase.COMPLETE
                    || phase == GermanStreamingDeliveryLedger.Phase.UNCERTAIN
                    || phase == GermanStreamingDeliveryLedger.Phase.SUSPENDED))
            return -1L;
        return plan.begin(newText);
    }

    /** A new plan may not silently discard an unfinished or uncertain prior plan. */
    public synchronized long begin(String text) {
        if (active != null || (plan.phase() != GermanStreamingDeliveryLedger.Phase.IDLE
                && plan.phase() != GermanStreamingDeliveryLedger.Phase.COMPLETE))
            throw new IllegalStateException("previous speech must be resolved first");
        return plan.begin(text);
    }

    public synchronized Dispatch lease(long planGeneration, long nowMs) {
        if (nowMs < 0 || active != null || planGeneration != plan.generation()
                || plan.phase() != GermanStreamingDeliveryLedger.Phase.READY) return null;
        // Rotate the audio epoch before issuing a new ticket, so callbacks from a
        // previously drained chunk cannot mutate the next chunk's lifecycle.
        long audioGeneration = audio.begin(nowMs);
        GermanStreamingDeliveryLedger.Ticket ticket = plan.leaseNext(planGeneration);
        if (ticket == null) throw new IllegalStateException("lost plan ownership during lease");
        active = new Dispatch(ticket, audioGeneration);
        return active;
    }

    public synchronized boolean prepared(Dispatch d, long nowMs) {
        if (!isCurrent(d)) return false;
        boolean result = audio.prepared(d.audioGeneration, nowMs);
        stopOnLifecycleFailure();
        return result;
    }

    /** Advisory only: MUST be serialized with the real AudioTrack write/accept. */
    public synchronized boolean canAcceptPcm(Dispatch d, int frames,
            VoiceLifecycleContract.Mouth mouth, long nowMs) {
        if (!isCurrent(d)) return false;
        boolean result = audio.canAcceptPcm(d.audioGeneration, frames, mouth, nowMs);
        stopOnLifecycleFailure();
        return result;
    }

    /** Call only with actual frames returned as accepted by the PCM output device. */
    public synchronized boolean acceptedPcm(Dispatch d, int frames,
            VoiceLifecycleContract.Mouth mouth, long nowMs) {
        if (!isCurrent(d)) return false;
        boolean result = audio.acceptPcm(d.audioGeneration, frames, mouth, nowMs);
        stopOnLifecycleFailure();
        return result;
    }

    public synchronized boolean synthesisEnded(Dispatch d, long nowMs) {
        if (!isCurrent(d)) return false;
        boolean result = audio.synthesisDone(d.audioGeneration, nowMs);
        stopOnLifecycleFailure();
        if (!result || !isCurrent(d)) return false;
        if (!plan.synthesisEnded(d.ticket)) {
            // Cross-contract disagreement must fail closed, not skip text.
            audio.interrupt(d.audioGeneration, nowMs);
            plan.stop(d.planGeneration);
            active = null;
            return false;
        }
        acknowledgeOnlyObservedRawDrain(d);
        return true;
    }

    public synchronized boolean playbackHead(Dispatch d, long actualFrames, long nowMs) {
        if (!isCurrent(d)) return false;
        boolean result = audio.playbackHead(d.audioGeneration, actualFrames, nowMs);
        stopOnLifecycleFailure();
        if (result && isCurrent(d)) acknowledgeOnlyObservedRawDrain(d);
        return result;
    }

    /** An engine death makes in-flight audibility UNCERTAIN, even if PCM was queued. */
    public synchronized boolean engineDied(Dispatch d, long nowMs) {
        if (!isCurrent(d)) return false;
        boolean result = audio.engineDied(d.audioGeneration, nowMs);
        if (result || audio.phase() == VoiceLifecycleContract.Phase.FAILED) {
            plan.stop(d.planGeneration);
            active = null;
        }
        return result;
    }

    /** Stop is bounded to the exact current dispatch; stale tickets cannot stop a successor. */
    public synchronized boolean interrupt(Dispatch d, long nowMs) {
        if (!isCurrent(d)) return false;
        boolean result = audio.interrupt(d.audioGeneration, nowMs);
        if (result || audio.phase() == VoiceLifecycleContract.Phase.FAILED) {
            plan.stop(d.planGeneration);
            active = null;
        }
        return result;
    }

    public synchronized boolean tick(long nowMs) {
        if (active == null) return false;
        boolean result = audio.tick(nowMs);
        stopOnLifecycleFailure();
        return result;
    }

    public synchronized boolean suspendReady(long planGeneration) {
        if (active != null) return false;
        return plan.stop(planGeneration);
    }

    /**
     * SILENT/FULL_DRAIN proofs must be supplied by a future independent adapter.
     * Passing an enum is NOT itself real playback/capture evidence.
     */
    public synchronized long restart(long planGeneration,
            GermanStreamingDeliveryLedger.Recovery externalDecision) {
        if (active != null) return -1L;
        return plan.restart(planGeneration, externalDecision);
    }

    public synchronized GermanStreamingDeliveryLedger.Phase phase() { return plan.phase(); }
    public synchronized int confirmedChunks() { return plan.confirmedChunks(); }
    public synchronized int chunkCount() { return plan.chunkCount(); }
    public synchronized long generation() { return plan.generation(); }
    public synchronized String confirmedText() { return plan.confirmedText(); }
    public synchronized String normalizedPlanText() { return plan.normalizedPlanText(); }
    public synchronized VoiceLifecycleContract.Phase audioPhase() { return audio.phase(); }
    public synchronized long acceptedFrames() { return audio.acceptedFrames(); }
    public synchronized long playedFrames() { return audio.playedFrames(); }

    private boolean isCurrent(Dispatch d) {
        return d != null && d == active && d.planGeneration == plan.generation()
                && d.audioGeneration == audio.generation()
                && plan.phase() == GermanStreamingDeliveryLedger.Phase.IN_FLIGHT;
    }

    private void acknowledgeOnlyObservedRawDrain(Dispatch d) {
        if (!isCurrent(d) || audio.phase() != VoiceLifecycleContract.Phase.COMPLETED
                || audio.acceptedFrames() <= 0
                || audio.playedFrames() != audio.acceptedFrames()) return;
        if (plan.playbackDrained(d.ticket)) active = null;
    }

    private void stopOnLifecycleFailure() {
        if (active != null && audio.phase() == VoiceLifecycleContract.Phase.FAILED) {
            plan.stop(active.planGeneration);
            active = null;
        }
    }
}
