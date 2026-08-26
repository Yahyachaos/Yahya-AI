package de.yahya.ai;

/**
 * v77 stateful mouth-pose stabilizer driven only by the PCM that is sent to AudioTrack.
 * It keeps the v76 morph contract intact while reducing frame-to-frame viseme chatter.
 */
final class SpeechLipSyncV77 {
    static final class Frame {
        final float level;
        final SpeechVisemeAnalyzer.Cue cue;

        Frame(float level, SpeechVisemeAnalyzer.Cue cue) {
            this.level = clamp(level);
            this.cue = cue == null ? SpeechVisemeAnalyzer.silent() : cue;
        }
    }

    private float level;
    private float openness;
    private float width;
    private float roundness;
    private SpeechVisemeAnalyzer.Shape shape = SpeechVisemeAnalyzer.Shape.CLOSED;
    private SpeechVisemeAnalyzer.Shape candidate = SpeechVisemeAnalyzer.Shape.CLOSED;
    private int candidateFrames;
    private int quietFrames;

    Frame analyze(float[] samples, int offset, int count, int sampleRate) {
        SpeechVisemeAnalyzer.Cue raw = SpeechVisemeAnalyzer.analyze(samples, offset, count, sampleRate);
        float rawLevel = normalizedRms(samples, offset, count);

        // Fast attack follows consonant/vowel onsets; slower release avoids a robotic mouth snap.
        float levelBlend = rawLevel > level ? 0.78f : 0.34f;
        level += (rawLevel - level) * levelBlend;

        if (rawLevel < 0.025f) quietFrames++; else quietFrames = 0;
        if (quietFrames >= 3) {
            shape = SpeechVisemeAnalyzer.Shape.CLOSED;
            candidate = shape;
            candidateFrames = 0;
            openness *= 0.42f;
            width *= 0.48f;
            roundness *= 0.48f;
            if (quietFrames >= 6) {
                level = 0f;
                openness = width = roundness = 0f;
            }
            return new Frame(level, new SpeechVisemeAnalyzer.Cue(shape, openness, width, roundness));
        }

        SpeechVisemeAnalyzer.Shape next = raw.shape;
        boolean closureCritical = next == SpeechVisemeAnalyzer.Shape.CLOSED
                || next == SpeechVisemeAnalyzer.Shape.LABIAL
                || next == SpeechVisemeAnalyzer.Shape.TEETH;
        if (next == shape) {
            candidate = next;
            candidateFrames = 0;
        } else if (closureCritical) {
            // German B/P/M and F/V must land quickly enough to remain readable.
            shape = next;
            candidate = next;
            candidateFrames = 0;
        } else {
            if (candidate == next) candidateFrames++; else {
                candidate = next;
                candidateFrames = 1;
            }
            if (candidateFrames >= 2) {
                shape = candidate;
                candidateFrames = 0;
            }
        }

        float mouthBlend = raw.openness > openness ? 0.68f : 0.42f;
        openness += (raw.openness - openness) * mouthBlend;
        width += (raw.width - width) * 0.52f;
        roundness += (raw.roundness - roundness) * 0.52f;

        // Keep closed/labial frames genuinely closed instead of leaving a floating half-open mouth.
        if (shape == SpeechVisemeAnalyzer.Shape.CLOSED || shape == SpeechVisemeAnalyzer.Shape.LABIAL) {
            openness = Math.min(openness, shape == SpeechVisemeAnalyzer.Shape.CLOSED ? 0.07f : 0.16f);
        }
        return new Frame(level, new SpeechVisemeAnalyzer.Cue(shape, openness, width, roundness));
    }

    Frame silentFrame() {
        level = openness = width = roundness = 0f;
        shape = candidate = SpeechVisemeAnalyzer.Shape.CLOSED;
        candidateFrames = quietFrames = 0;
        return new Frame(0f, SpeechVisemeAnalyzer.silent());
    }

    private static float normalizedRms(float[] samples, int offset, int count) {
        if (samples == null || count <= 0 || offset < 0 || offset >= samples.length) return 0f;
        int end = Math.min(samples.length, offset + count);
        double sum = 0.0;
        for (int i = offset; i < end; i++) {
            float v = samples[i];
            sum += v * v;
        }
        double rms = Math.sqrt(sum / Math.max(1, end - offset));
        return clamp((float) ((rms - 0.003) / 0.055));
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
