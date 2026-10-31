import java.util.ArrayList;
import java.util.List;

/**
 * FUTURE-ONLY, Android-free reference contract for Celin's speech playback lifecycle.
 * A caller MUST serialize or synchronize event delivery and apply the returned generation
 * to every Android TTS, neural synthesis and AudioTrack callback. This class itself is
 * synchronized for defensive use by asynchronous callers.
 *
 * No audio is generated or played here. Cues are committed ONLY after PCM write accepts
 * frames, and become visible ONLY when the actual AudioTrack head reaches those frames.
 */
public final class VoiceLifecycleContract {
    public enum Phase { IDLE, PREPARING, BUFFERING, PLAYING, DRAINING, COMPLETED, CANCELLED, FAILED }
    public enum Mouth { CLOSED, OPEN, LABIAL, TEETH, ROUNDED, NEUTRAL }

    private static final long PREPARE_TIMEOUT_MS = 6000L;
    private static final long PROGRESS_TIMEOUT_MS = 3500L;
    // A single utterance may not indefinitely accumulate as-yet-unplayed cue boundaries.
    private static final int MAX_RETAINED_CUES = 256;
    // Experimental UI-only cap: device-calibrated 0..500ms acoustic offset plus
    // timestamp/render slack. NOT a measured Samsung latency or audio drain timeout.
    private static final long MAX_PRESENTATION_TAIL_MS = 750L;

    private static final class Segment {
        final long start;
        long end;
        final Mouth mouth;
        Segment(long start, long end, Mouth mouth) {
            this.start = start; this.end = end; this.mouth = mouth;
        }
    }

    private final List<Segment> acceptedCues = new ArrayList<>();
    private long generation;
    private long acceptedFrames;
    private long playedFrames;
    // Optional calibrated UI path. Raw playedFrames STILL owns terminal drain.
    private long presentationFrames;
    private boolean calibratedPresentation;
    private boolean boundedPresentationTail;
    // Physical stream is already COMPLETED; this stores bounded, UI-only catch-up.
    // Never use it to extend audio playback, engine ownership or synthesis lifespan.
    private long presentationTailStartedMs = -1L;
    private long lastProgressMs;
    private Phase phase = Phase.IDLE;
    private String failure = "";

    public synchronized long begin(long nowMs) {
        if (nowMs < 0) throw new IllegalArgumentException("negative monotonic time");
        if (generation == Long.MAX_VALUE) throw new IllegalStateException("generation exhausted");
        generation++;
        acceptedCues.clear();
        acceptedFrames = playedFrames = presentationFrames = 0L;
        calibratedPresentation = false;
        boundedPresentationTail = false;
        presentationTailStartedMs = -1L;
        lastProgressMs = nowMs;
        failure = "";
        phase = Phase.PREPARING;
        return generation;
    }

    public synchronized boolean prepared(long id, long nowMs) {
        if (!current(id, nowMs) || phase != Phase.PREPARING) return false;
        phase = Phase.BUFFERING;
        lastProgressMs = nowMs;
        return true;
    }

    /**
     * Advisory budget check BEFORE a write to avoid accepting PCM whose cues cannot
     * be retained. Caller must serialize the check with subsequent write/acceptPcm;
     * this is not a cross-thread reservation and does not replace fail-stop handling.
     */
    public synchronized boolean canAcceptPcm(long id, int frameCount, Mouth mouth, long nowMs) {
        if (!current(id, nowMs) || !(phase == Phase.BUFFERING || phase == Phase.PLAYING)
                || frameCount <= 0 || mouth == null || acceptedFrames > Long.MAX_VALUE - frameCount) return false;
        pruneConsumed();
        if (acceptedCues.size() < MAX_RETAINED_CUES) return true;
        Segment last = acceptedCues.get(acceptedCues.size() - 1);
        return last.end == acceptedFrames && last.mouth == mouth;
    }

    /** Call only AFTER a successful AudioTrack.write; use its actual returned frame count. */
    public synchronized boolean acceptPcm(long id, int frameCount, Mouth mouth, long nowMs) {
        if (!current(id, nowMs) || !(phase == Phase.BUFFERING || phase == Phase.PLAYING)
                || frameCount <= 0 || mouth == null || acceptedFrames > Long.MAX_VALUE - frameCount) {
            return false;
        }
        // Retire fully consumed intervals, but preserve the cue of playedFrames-1
        // (the last actually emitted sample) even when the head is at its boundary.
        pruneConsumed();
        int retained = acceptedCues.size();
        Segment last = retained == 0 ? null : acceptedCues.get(retained - 1);
        if (last != null && last.end == acceptedFrames && last.mouth == mouth) {
            // Adjacent identical visemes never need duplicate cue objects.
            last.end += frameCount;
        } else {
            if (retained >= MAX_RETAINED_CUES) {
                // Post-write detection is fail-stop: integration must stop the track and
                // preflight its queue budget BEFORE the next write; never silently
                // drop unplayed cues or claim a sample was rendered correctly.
                fail("cue backlog exceeded");
                return false;
            }
            acceptedCues.add(new Segment(acceptedFrames, acceptedFrames + frameCount, mouth));
        }
        acceptedFrames += frameCount;
        lastProgressMs = nowMs;
        phase = Phase.PLAYING;
        return true;
    }

    /** Playback head must be monotonic and may never claim PCM that was not accepted. */
    public synchronized boolean playbackHead(long id, long framePosition, long nowMs) {
        if (!current(id, nowMs) || !(phase == Phase.PLAYING || phase == Phase.DRAINING)
                || framePosition < playedFrames || framePosition > acceptedFrames) return false;
        if (framePosition > playedFrames) lastProgressMs = nowMs;
        playedFrames = framePosition;
        pruneConsumed();
        if (phase == Phase.DRAINING && playedFrames == acceptedFrames) complete(nowMs);
        return true;
    }

    /**
     * Opt into conservative externally calibrated mouth rendering BEFORE the first raw
     * playback-head advancement. Physical raw head remains the ONLY drain authority.
     * The caller must have a real device-calibrated VoiceAcousticTimingContract, and
     * MUST NOT render playedMouth() once this mode is selected.
     */
    public synchronized boolean enableCalibratedPresentation(long id, long nowMs) {
        if (!current(id, nowMs) || calibratedPresentation || playedFrames != 0
                || !(phase == Phase.BUFFERING || phase == Phase.PLAYING)) return false;
        calibratedPresentation = true;
        presentationFrames = 0L;
        return true;
    }

    /**
     * Future adapter MUST explicitly opt in to post-drain mouth retention while the
     * raw track is still at frame zero, after real calibration; no default enablement.
     * This is UI-only and never extends physical playback or a dead TTS engine.
     */
    public synchronized boolean enableBoundedPresentationTail(long id, long nowMs) {
        if (!current(id, nowMs) || !calibratedPresentation || boundedPresentationTail
                || playedFrames != 0
                || !(phase == Phase.BUFFERING || phase == Phase.PLAYING)) return false;
        boundedPresentationTail = true;
        return true;
    }

    /**
     * Only pass a fresh valid non-leading cursor from VoiceAcousticTimingContract.
     * If the acoustic clock returns -1, the adapter MUST show CLOSED instead and
     * must NOT call this method. Stale/invalid epochs likewise return CLOSED.
     * The committed PCM cue is retained until this presentation cursor consumes it,
     * even when the raw AudioTrack head is ahead.
     */
    public synchronized Mouth calibratedMouthAt(long id, long framePosition, long nowMs) {
        if (!current(id, nowMs) || !calibratedPresentation) return Mouth.CLOSED;
        if (phase == Phase.COMPLETED) {
            // A raw-head drain can precede acoustic presentation of the final syllable.
            // Keep only a bounded tail; callbacks must still carry valid, fresh external
            // calibration. This method never synthesizes/plays additional PCM.
            if (!hasPresentationTail()) return Mouth.CLOSED;
            if (nowMs - presentationTailStartedMs > MAX_PRESENTATION_TAIL_MS) {
                clearPresentationTail();
                return Mouth.CLOSED;
            }
        } else if (!(phase == Phase.PLAYING || phase == Phase.DRAINING)) {
            return Mouth.CLOSED;
        }
        if (framePosition < presentationFrames || framePosition > playedFrames)
            return Mouth.CLOSED;
        presentationFrames = framePosition;
        if (phase == Phase.COMPLETED && framePosition == acceptedFrames) {
            // A confirmed final presentation cursor closes the mouth, not the raw head.
            clearPresentationTail();
            return Mouth.CLOSED;
        }
        pruneConsumed();
        return mouthForFrame(framePosition);
    }

    /** Synthesis ending is NOT the same as speaker playback ending. */
    public synchronized boolean synthesisDone(long id, long nowMs) {
        if (!current(id, nowMs) || !(phase == Phase.PLAYING || phase == Phase.BUFFERING)) return false;
        if (acceptedFrames == 0) {
            fail("empty synth result");
            return true;
        }
        phase = Phase.DRAINING;
        if (playedFrames == acceptedFrames) complete(nowMs);
        return true;
    }

    public synchronized boolean engineDied(long id, long nowMs) {
        if (!current(id, nowMs)) return false;
        if (hasPresentationTail()) {
            clearPresentationTail(); // terminal state remains truthful: physical drain finished
            return true;
        }
        if (!active()) return false;
        fail("TTS engine terminated");
        return true;
    }

    public synchronized boolean interrupt(long id, long nowMs) {
        if (!current(id, nowMs)) return false;
        if (hasPresentationTail()) {
            // Actual stop/release must silence the delayed UI immediately.
            clearPresentationTail();
            return true;
        }
        if (!active()) return false;
        acceptedCues.clear();
        phase = Phase.CANCELLED;
        return true;
    }

    /** The caller must use a monotonic elapsedRealtime-like clock. */
    public synchronized boolean tick(long nowMs) {
        if (nowMs < 0 || nowMs < lastProgressMs) return false;
        if (hasPresentationTail() && nowMs - presentationTailStartedMs > MAX_PRESENTATION_TAIL_MS) {
            clearPresentationTail();
            return true;
        }
        if (!active()) return false;
        long timeout = phase == Phase.PREPARING ? PREPARE_TIMEOUT_MS : PROGRESS_TIMEOUT_MS;
        if (nowMs - lastProgressMs <= timeout) return false;
        fail(phase == Phase.PREPARING ? "synthesis timeout" : "playback/synthesis stalled");
        return true;
    }

    /** Legacy, raw-head-only cue: disabled in calibrated mode to prevent early lips. */
    public synchronized Mouth playedMouth() {
        if (calibratedPresentation || !(phase == Phase.PLAYING || phase == Phase.DRAINING))
            return Mouth.CLOSED;
        return mouthForFrame(playedFrames);
    }

    private Mouth mouthForFrame(long framePosition) {
        if (framePosition == 0) return Mouth.CLOSED;
        long currentSample = framePosition - 1L;
        for (int i = acceptedCues.size() - 1; i >= 0; i--) {
            Segment s = acceptedCues.get(i);
            if (currentSample >= s.start && currentSample < s.end) return s.mouth;
        }
        return Mouth.CLOSED;
    }

    public synchronized Phase phase() { return phase; }
    public synchronized String failureReason() { return failure; }
    public synchronized long generation() { return generation; }
    public synchronized long acceptedFrames() { return acceptedFrames; }
    public synchronized long playedFrames() { return playedFrames; }
    /** Test-only diagnostic; a production caller should not depend on the queue shape. */
    public synchronized int retainedCueCount() { return acceptedCues.size(); }

    private void pruneConsumed() {
        // Strict < retains the segment containing last emitted sample
        // at an exact frame boundary. Only samples earlier than that are gone.
        int count = 0;
        long consumedHead = calibratedPresentation ? presentationFrames : playedFrames;
        while (count < acceptedCues.size() && acceptedCues.get(count).end < consumedHead) count++;
        if (count > 0) acceptedCues.subList(0, count).clear();
    }

    private boolean active() {
        return phase == Phase.PREPARING || phase == Phase.BUFFERING
                || phase == Phase.PLAYING || phase == Phase.DRAINING;
    }

    private boolean current(long id, long nowMs) {
        if (id != generation || id <= 0 || nowMs < 0 || nowMs < lastProgressMs) return false;
        if (active()) {
            long timeout = phase == Phase.PREPARING ? PREPARE_TIMEOUT_MS : PROGRESS_TIMEOUT_MS;
            if (nowMs - lastProgressMs > timeout) {
                fail(phase == Phase.PREPARING ? "synthesis timeout" : "playback/synthesis stalled");
                return false;
            }
        }
        return true;
    }

    private boolean hasPresentationTail() {
        return phase == Phase.COMPLETED && calibratedPresentation
                && presentationTailStartedMs >= 0 && !acceptedCues.isEmpty();
    }

    private void clearPresentationTail() {
        acceptedCues.clear();
        presentationTailStartedMs = -1L;
    }

    private void complete(long nowMs) {
        phase = Phase.COMPLETED;
        lastProgressMs = nowMs;
        // Physical audio-drain completion remains the state authority. Only keep an
        // already calibrated, not-yet-presented mouth cue for a short UI grace period.
        if (calibratedPresentation && boundedPresentationTail
                && presentationFrames < acceptedFrames && !acceptedCues.isEmpty())
            presentationTailStartedMs = nowMs;
        else clearPresentationTail();
    }

    private void fail(String reason) {
        phase = Phase.FAILED;
        failure = reason;
        clearPresentationTail();
    }
}
