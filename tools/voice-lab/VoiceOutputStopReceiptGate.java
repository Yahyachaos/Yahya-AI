/**
 * FUTURE-ONLY, Android-free stop-acknowledgement identity contract.
 * An external adapter must separately verify that old physical audio output
 * REALLY stopped; a call to acknowledgeStopped() is only a typed CLAIM.
 * This gate never authorizes text replay, skip, or a v83 integration.
 */
public final class VoiceOutputStopReceiptGate {
    public static final class StopRequest {
        public final long nonce;
        public final long planGeneration;
        public final long audioGeneration;
        public final long issuedNs;
        private StopRequest(long nonce, long plan, long audio, long issued) {
            this.nonce = nonce;
            this.planGeneration = plan;
            this.audioGeneration = audio;
            this.issuedNs = issued;
        }
    }

    private final long maxAgeNs;
    private long nextNonce;
    private long lastPlan;
    private long lastAudio;
    private long lastEventNs;
    private StopRequest pending;
    private boolean externallyStopped;
    // A consumed abort terminalizes this exact plan/audio identity.
    // Unconsumed/invalidated requests may still retry with a fresh nonce.
    private boolean consumedLastEpoch;

    public VoiceOutputStopReceiptGate(long maxAgeNs) {
        if (maxAgeNs <= 0) throw new IllegalArgumentException("positive stop-claim TTL required");
        this.maxAgeNs = maxAgeNs;
    }

    /** At most one pending request, bound to the old plan AND old audio epoch. */
    public synchronized StopRequest request(long plan, long audio, long nowNs) {
        if (pending != null || plan <= 0 || audio <= 0 || nowNs < 0
                || plan < lastPlan || audio < lastAudio
                || (plan > lastPlan && audio <= lastAudio)
                || (consumedLastEpoch && plan == lastPlan)
                || nowNs < lastEventNs
                || nextNonce == Long.MAX_VALUE) return null;
        pending = new StopRequest(++nextNonce, plan, audio, nowNs);
        lastPlan = plan;
        lastAudio = audio;
        lastEventNs = nowNs;
        externallyStopped = false;
        consumedLastEpoch = false;
        return pending;
    }

    /** A typed external STOP claim; never independently proves speaker silence. */
    public synchronized boolean acknowledgeStopped(StopRequest request,
            long observedPlan, long observedAudio, long stoppedNs) {
        if (!matches(request, observedPlan, observedAudio)) return false;
        if (externallyStopped) {
            // Even a duplicate current-epoch callback reveals a later clock value.
            // It never grants a second ACK; an expired/backward claim poisons the lease.
            if (stoppedNs > lastEventNs) lastEventNs = stoppedNs;
            if (!fresh(stoppedNs)) {
                pending = null;
                externallyStopped = false;
            }
            return false;
        }
        if (!fresh(stoppedNs)) {
            if (stoppedNs > lastEventNs) lastEventNs = stoppedNs;
            pending = null; // fail closed on clock reversal / expiry
            externallyStopped = false;
            return false;
        }
        lastEventNs = stoppedNs;
        externallyStopped = true;
        return true;
    }

    /** Consume once when authorizing an explicit user-chosen NEW text plan. */
    public synchronized boolean consumeForExplicitAbort(StopRequest request,
            long observedPlan, long observedAudio, long nowNs) {
        if (!matches(request, observedPlan, observedAudio) || !externallyStopped)
            return false;
        boolean valid = fresh(nowNs);
        if (nowNs > lastEventNs) lastEventNs = nowNs;
        pending = null;
        externallyStopped = false;
        if (valid) consumedLastEpoch = true;
        return valid;
    }

    /** Cancel after output error, timeout, user cancellation, or ownership loss. */
    public synchronized boolean invalidate(StopRequest request) {
        if (request == null || pending != request) return false;
        pending = null;
        externallyStopped = false;
        return true;
    }

    public synchronized boolean hasPendingStop() { return pending != null; }

    private boolean matches(StopRequest request, long plan, long audio) {
        return request != null && pending == request
                && request.nonce == nextNonce && request.planGeneration == plan
                && request.audioGeneration == audio;
    }

    private boolean fresh(long nowNs) {
        return pending != null && nowNs >= lastEventNs
                && nowNs >= pending.issuedNs
                && nowNs - pending.issuedNs <= maxAgeNs;
    }
}
