package de.yahya.ai;

/** Lightweight local PCM-to-mouth-shape estimator. */
public final class SpeechVisemeAnalyzer {
    public enum Shape { CLOSED, OPEN, WIDE, ROUND }

    public static final class Cue {
        public final Shape shape;
        public final float openness;
        public final float width;
        public final float roundness;

        Cue(Shape shape, float openness, float width, float roundness) {
            this.shape = shape;
            this.openness = clamp(openness);
            this.width = clamp(width);
            this.roundness = clamp(roundness);
        }
    }

    private SpeechVisemeAnalyzer() {}

    public static Cue analyze(float[] samples, int offset, int length, int sampleRate) {
        if (samples == null || length <= 8 || sampleRate <= 0) return silent();
        int end = Math.min(samples.length, offset + length);
        if (offset < 0 || offset >= end) return silent();

        double sumSq = 0.0;
        double diffSq = 0.0;
        int zeroCrossings = 0;
        float prev = samples[offset];
        for (int i = offset; i < end; i++) {
            float s = samples[i];
            sumSq += s * s;
            if (i > offset) {
                float d = s - prev;
                diffSq += d * d;
                if ((s >= 0f) != (prev >= 0f)) zeroCrossings++;
            }
            prev = s;
        }

        int n = end - offset;
        float rms = (float) Math.sqrt(sumSq / Math.max(1, n));
        if (rms < 0.0085f) return silent();

        float openness = clamp((rms - 0.008f) * 8.0f);
        float roughness = (float) Math.sqrt(diffSq / Math.max(1, n - 1)) / Math.max(0.0001f, rms);
        float zcr = zeroCrossings / (float) Math.max(1, n - 1);
        float bright = clamp((roughness - 0.55f) / 1.45f + zcr * 1.6f);
        float round = clamp((1.0f - bright) * 0.85f + openness * 0.12f);
        float wide = clamp(bright * 0.78f + openness * 0.20f);

        Shape shape;
        if (openness < 0.14f) shape = Shape.CLOSED;
        else if (round > 0.58f && wide < 0.55f) shape = Shape.ROUND;
        else if (wide > 0.55f) shape = Shape.WIDE;
        else shape = Shape.OPEN;
        return new Cue(shape, openness, wide, round);
    }

    public static Cue silent() { return new Cue(Shape.CLOSED, 0f, 0f, 0f); }
    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }
}
