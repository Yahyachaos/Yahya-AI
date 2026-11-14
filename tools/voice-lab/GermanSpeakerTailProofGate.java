/**
 * FUTURE-ONLY advisory permit for moving to another German speech chunk after
 * raw PCM drain. It refuses a next-chunk permit without a FRESH, same-epoch
 * EXTERNAL_CAPTURE claim covering every accepted/presented PCM frame.
 *
 * This standalone contract MUST later be wired serially into the chunk lease
 * path, or it can be bypassed. EXTERNAL_CAPTURE is caller-supplied metadata,
 * not proof that a Samsung speaker was really recorded. No Android integration.
 */
public final class GermanSpeakerTailProofGate {
    private final VoiceVerifiedPresentationClock verifiedClock = new VoiceVerifiedPresentationClock();
    private long epoch;
    private long acceptedFrames;
    private long lastPermitPollNs = -1L;
    private boolean rawDrained, permitted, stopped;

    public synchronized void begin(long audioGeneration, int sampleRate) {
        if (epoch > 0 && !permitted && !stopped)
            throw new IllegalStateException("unresolved previous audible tail");
        verifiedClock.begin(audioGeneration, sampleRate);
        epoch = audioGeneration;
        acceptedFrames = 0L;
        lastPermitPollNs = -1L;
        rawDrained = permitted = stopped = false;
    }

    /** Invoke only after the matching VoiceLifecycleContract is COMPLETED. */
    public synchronized boolean markRawDrained(long id, long accepted, long rawPlayed) {
        if (id != epoch || stopped || permitted || rawDrained || accepted <= 0
                || accepted != rawPlayed) return false;
        rawDrained = true;
        acceptedFrames = accepted;
        return true;
    }

    /** Do not substitute AudioTrack timestamps for independently captured sound. */
    public synchronized boolean observeCapturedTail(long id,
            VoiceVerifiedPresentationClock.EvidenceKind kind, long speakerPresented,
            long rawHead, long accepted, long captureNs, long pollNs) {
        if (id != epoch || stopped || permitted || !rawDrained
                || accepted != acceptedFrames || rawHead != acceptedFrames) return false;
        return verifiedClock.observe(id, kind, speakerPresented, rawHead, accepted, captureNs, pollNs);
    }

    /** Single-use permit, to be consumed synchronously with next-chunk lease. */
    public synchronized boolean permitNextChunk(long id, long nowNs) {
        if (id != epoch || stopped || permitted || !rawDrained || acceptedFrames <= 0
                || nowNs < 0 || (lastPermitPollNs >= 0 && nowNs < lastPermitPollNs))
            return false;
        // Query time is a monotonic device clock, never a caller-selected old
        // timestamp used to resurrect an already-expired capture.
        lastPermitPollNs = nowNs;
        if (verifiedClock.presentedFrame(id, nowNs) != acceptedFrames) return false;
        permitted = true;
        verifiedClock.stop(id);
        return true;
    }

    public synchronized void stop(long id) {
        if (id == epoch) {
            stopped = true;
            verifiedClock.stop(id);
        }
    }
}
