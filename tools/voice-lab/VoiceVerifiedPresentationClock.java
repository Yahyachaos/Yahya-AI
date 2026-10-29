/**
 * FUTURE-ONLY contract for a independently measured SPEAKER presentation position.
 * It never synthesizes timestamps, downloads models or reads Android hardware.
 * A later device adapter must supply REAL external/captured sound-frame evidence;
 * merely passing AudioTrack's raw head or a guessed delay is NOT valid proof.
 */
public final class VoiceVerifiedPresentationClock {
    public enum EvidenceKind { EXTERNAL_CAPTURE, RAW_HEAD_ONLY }
    private static final long MAX_AGE_NS = 200_000_000L;
    private long generation;
    private int sampleRate;
    private long lastPresented, lastRaw, lastAccepted, lastCaptureNs, lastPollNs;
    private boolean hasEvidence, hasBaseline, stopped, poisoned;

    public synchronized void begin(long id, int rate) {
        if (id <= generation || id <= 0 || rate < 8000 || rate > 192000)
            throw new IllegalArgumentException("new generation and valid sample rate required");
        generation = id;
        sampleRate = rate;
        lastPresented = lastRaw = lastAccepted = lastCaptureNs = lastPollNs = 0;
        hasEvidence = hasBaseline = stopped = poisoned = false;
    }

    /**
     * A caller MUST prove that presentedFrames is an external actual speaker-capture
     * measurement with this utterance's epoch, not a raw head/timer extrapolation.
     * Invalid current-epoch values fail closed; contradictory position/clock reset
     * poisons this epoch until begin(nextId, rate). Old-epoch callbacks are ignored.
     */
    public synchronized boolean observe(long id, EvidenceKind kind, long presentedFrames,
            long rawHeadFrames, long acceptedFrames, long captureNs, long pollNs) {
        if (id != generation || id <= 0 || stopped || poisoned) return false;
        if (kind != EvidenceKind.EXTERNAL_CAPTURE || presentedFrames < 0
                || rawHeadFrames < presentedFrames || rawHeadFrames > acceptedFrames
                || captureNs < 0 || pollNs < captureNs || pollNs - captureNs > MAX_AGE_NS) {
            hasEvidence = false;
            return false;
        }
        if (hasBaseline) {
            if (presentedFrames < lastPresented || rawHeadFrames < lastRaw
                    || acceptedFrames < lastAccepted || captureNs < lastCaptureNs
                    || pollNs < lastPollNs) {
                hasEvidence = false;
                poisoned = true; // same-epoch resets require an entirely new stream
                return false;
            }
            if (presentedFrames > lastPresented && captureNs == lastCaptureNs) {
                hasEvidence = false;
                return false;
            }
            long deltaFrames = presentedFrames - lastPresented;
            long elapsedNs = captureNs - lastCaptureNs;
            // Conservative physical plausibility. The 100ms scheduling cushion is an
            // upper error bound for rejecting impossible jumps, NOT estimated playback.
            long safeSeconds = elapsedNs / 1_000_000_000L;
            long residualMs = elapsedNs % 1_000_000_000L / 1_000_000L;
            long allowed = safeSeconds > Long.MAX_VALUE / sampleRate
                    ? Long.MAX_VALUE : safeSeconds * sampleRate;
            long extra = residualMs * sampleRate / 1000L + sampleRate / 10L;
            allowed = Long.MAX_VALUE - allowed < extra ? Long.MAX_VALUE : allowed + extra;
            if (deltaFrames > allowed) {
                hasEvidence = false;
                return false;
            }
        }
        lastPresented = presentedFrames;
        lastRaw = rawHeadFrames;
        lastAccepted = acceptedFrames;
        lastCaptureNs = captureNs;
        lastPollNs = pollNs;
        hasEvidence = hasBaseline = true;
        return true;
    }

    /** -1 is the ONLY correct result with no fresh independent capture proof. */
    public synchronized long presentedFrame(long id, long nowNs) {
        if (!hasEvidence || stopped || poisoned || id != generation
                || nowNs < lastPollNs || nowNs - lastPollNs > MAX_AGE_NS) return -1L;
        return lastPresented;
    }

    public synchronized void stop(long id) {
        if (id == generation) {
            stopped = true;
            hasEvidence = false;
        }
    }
}
