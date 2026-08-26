package de.yahya.ai;

/** Pure, bounded planner for Celine's final-geometry v76 facial contract. */
public final class CelineFacialMotionPlanner {
    public static final int BLINK_LEFT = 0;
    public static final int BLINK_RIGHT = 1;
    public static final int BLINK_BOTH = 2;
    public static final int JAW_OPEN = 3;
    public static final int ROUNDED_VOWEL = 4;
    public static final int SPREAD_VOWEL = 5;
    public static final int BILABIAL_PRESS = 6;
    public static final int LABIODENTAL = 7;
    public static final int SMILE = 8;
    public static final int THOUGHTFUL = 9;
    public static final int SURPRISED = 10;
    public static final int GAZE_LEFT = 11;
    public static final int GAZE_RIGHT = 12;
    public static final int GAZE_UP = 13;
    public static final int GAZE_DOWN = 14;
    public static final int TARGET_COUNT = 15;

    private static final float MAX_BLINK = 0.94f;
    private static final float MAX_JAW = 0.66f;
    private static final float MAX_VOWEL = 0.58f;
    private static final float MAX_LIP_CONTACT = 0.72f;
    private static final float MAX_EXPRESSION = 0.34f;
    private static final float MAX_GAZE = 0.32f;

    private final float[] current = new float[TARGET_COUNT];
    private long nextBlinkAtMs;
    private long blinkStartMs = -1L;
    private boolean blinkLeadLeft = true;
    private int blinkSerial;

    public static final class Frame {
        public final float[] weights;
        public final float microSmile;

        Frame(float[] weights, float microSmile) {
            this.weights = weights;
            this.microSmile = microSmile;
        }
    }

    public CelineFacialMotionPlanner(long nowMs) {
        scheduleNextBlink(nowMs);
    }

    public Frame update(long nowMs,
                        CelineAvatarController.State state,
                        SpeechVisemeAnalyzer.Cue cue,
                        float speechEnergy) {
        return update(nowMs, state, cue, speechEnergy, 0.0f, 0.0f, false);
    }

    public Frame update(long nowMs,
                        CelineAvatarController.State state,
                        SpeechVisemeAnalyzer.Cue cue,
                        float speechEnergy,
                        float lookX,
                        float lookY,
                        boolean lookActive) {
        CelineAvatarController.State safeState = state == null
                ? CelineAvatarController.State.IDLE : state;
        SpeechVisemeAnalyzer.Cue safeCue = cue == null ? SpeechVisemeAnalyzer.silent() : cue;
        float energy = clamp01(speechEnergy);

        float[] target = new float[TARGET_COUNT];
        applyBlink(nowMs, safeState, target);
        applySpeech(safeState, safeCue, energy, target);
        applyExpression(nowMs, safeState, safeCue, energy, target);
        applyGaze(nowMs, lookX, lookY, lookActive, target);

        float smoothing = safeState == CelineAvatarController.State.SPEAKING ? 0.34f : 0.26f;
        for (int i = 0; i < TARGET_COUNT; i++) {
            current[i] += (target[i] - current[i]) * smoothing;
            current[i] = clamp01(current[i]);
            if (current[i] < 0.0005f) current[i] = 0.0f;
        }
        return new Frame(current.clone(), current[SMILE]);
    }

    public void reset(long nowMs) {
        for (int i = 0; i < TARGET_COUNT; i++) current[i] = 0.0f;
        blinkStartMs = -1L;
        scheduleNextBlink(nowMs);
    }

    private void applyBlink(long nowMs, CelineAvatarController.State state, float[] target) {
        if (blinkStartMs < 0L && nowMs >= nextBlinkAtMs) {
            blinkStartMs = nowMs;
            blinkLeadLeft = (blinkSerial++ & 1) == 0;
        }
        if (blinkStartMs < 0L) return;

        long elapsed = nowMs - blinkStartMs;
        final long closeMs = 76L;
        final long holdMs = 30L;
        final long openMs = 116L;
        final long totalMs = closeMs + holdMs + openMs;
        if (elapsed >= totalMs) {
            blinkStartMs = -1L;
            scheduleNextBlink(nowMs);
            return;
        }

        float phase;
        if (elapsed < closeMs) {
            phase = elapsed / (float) closeMs;
        } else if (elapsed < closeMs + holdMs) {
            phase = 1.0f;
        } else {
            phase = 1.0f - (elapsed - closeMs - holdMs) / (float) openMs;
        }
        phase = smoothstep(clamp01(phase));
        float stateScale = state == CelineAvatarController.State.THINKING ? 0.90f : 1.0f;
        target[BLINK_BOTH] = MAX_BLINK * stateScale * phase;
        float asymmetry = 0.045f * phase;
        target[blinkLeadLeft ? BLINK_LEFT : BLINK_RIGHT] = asymmetry;
        target[blinkLeadLeft ? BLINK_RIGHT : BLINK_LEFT] = asymmetry * 0.45f;
    }

    private void applySpeech(CelineAvatarController.State state,
                             SpeechVisemeAnalyzer.Cue cue,
                             float energy,
                             float[] target) {
        if (state != CelineAvatarController.State.SPEAKING) return;
        float openness = clamp01(cue.openness);
        float width = clamp01(cue.width);
        float roundness = clamp01(cue.roundness);
        float voiced = 0.30f + 0.70f * energy;

        switch (cue.shape) {
            case ROUND:
                target[JAW_OPEN] = openness * MAX_JAW * 0.82f * voiced;
                target[ROUNDED_VOWEL] = (0.20f + 0.80f * roundness) * MAX_VOWEL * voiced;
                break;
            case WIDE:
                target[JAW_OPEN] = openness * MAX_JAW * 0.78f * voiced;
                target[SPREAD_VOWEL] = (0.18f + 0.82f * width) * MAX_VOWEL * voiced;
                break;
            case LABIAL:
                target[JAW_OPEN] = openness * MAX_JAW * 0.16f * voiced;
                target[BILABIAL_PRESS] = (0.42f + 0.58f * (1.0f - openness)) * MAX_LIP_CONTACT * voiced;
                target[ROUNDED_VOWEL] = roundness * MAX_VOWEL * 0.24f;
                break;
            case TEETH:
                target[JAW_OPEN] = openness * MAX_JAW * 0.30f * voiced;
                target[LABIODENTAL] = (0.34f + 0.66f * width) * MAX_LIP_CONTACT * voiced;
                target[SPREAD_VOWEL] = width * MAX_VOWEL * 0.30f;
                break;
            case OPEN:
                target[JAW_OPEN] = openness * MAX_JAW * voiced;
                target[ROUNDED_VOWEL] = roundness * MAX_VOWEL * 0.18f;
                target[SPREAD_VOWEL] = width * MAX_VOWEL * 0.18f;
                break;
            case CLOSED:
            default:
                target[BILABIAL_PRESS] = energy * MAX_LIP_CONTACT * 0.16f;
                break;
        }
        target[JAW_OPEN] = Math.min(MAX_JAW, target[JAW_OPEN]);
        target[ROUNDED_VOWEL] = Math.min(MAX_VOWEL, target[ROUNDED_VOWEL]);
        target[SPREAD_VOWEL] = Math.min(MAX_VOWEL, target[SPREAD_VOWEL]);
        target[BILABIAL_PRESS] = Math.min(MAX_LIP_CONTACT, target[BILABIAL_PRESS]);
        target[LABIODENTAL] = Math.min(MAX_LIP_CONTACT, target[LABIODENTAL]);
    }

    private void applyExpression(long nowMs,
                                 CelineAvatarController.State state,
                                 SpeechVisemeAnalyzer.Cue cue,
                                 float energy,
                                 float[] target) {
        double t = nowMs / 1000.0;
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 0.37 + 0.8);
        switch (state) {
            case LISTENING:
                target[SMILE] = 0.10f + pulse * 0.06f;
                break;
            case THINKING:
                target[THOUGHTFUL] = 0.18f + pulse * 0.09f;
                break;
            case SPEAKING:
                target[SMILE] = (0.04f + pulse * 0.04f) * (1.0f - energy * 0.45f);
                if (cue.shape != null && "OPEN".equals(cue.shape.name())
                        && cue.openness > 0.82f && energy > 0.78f) {
                    target[SURPRISED] = Math.min(MAX_EXPRESSION, (cue.openness - 0.82f) * 1.45f);
                }
                break;
            case IDLE:
            default:
                target[SMILE] = 0.04f + pulse * 0.04f;
                break;
        }
        target[SMILE] = Math.min(MAX_EXPRESSION, target[SMILE]);
        target[THOUGHTFUL] = Math.min(MAX_EXPRESSION, target[THOUGHTFUL]);
        target[SURPRISED] = Math.min(MAX_EXPRESSION, target[SURPRISED]);
    }

    private void applyGaze(long nowMs, float lookX, float lookY, boolean lookActive, float[] target) {
        float x;
        float y;
        if (lookActive) {
            x = clampSigned(lookX);
            y = clampSigned(lookY);
        } else {
            double t = nowMs / 1000.0;
            x = 0.22f * (float) Math.sin(t * 0.29 + 0.6);
            y = 0.12f * (float) Math.sin(t * 0.23 + 1.7);
        }
        if (x < 0.0f) target[GAZE_LEFT] = Math.min(MAX_GAZE, -x * MAX_GAZE);
        else target[GAZE_RIGHT] = Math.min(MAX_GAZE, x * MAX_GAZE);
        if (y < 0.0f) target[GAZE_UP] = Math.min(MAX_GAZE, -y * MAX_GAZE);
        else target[GAZE_DOWN] = Math.min(MAX_GAZE, y * MAX_GAZE);
    }

    private void scheduleNextBlink(long nowMs) {
        long interval = 2700L + ((blinkSerial * 977L + 613L) % 2200L);
        nextBlinkAtMs = nowMs + interval;
    }

    private static float smoothstep(float x) {
        return x * x * (3.0f - 2.0f * x);
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static float clampSigned(float value) {
        return Math.max(-1.0f, Math.min(1.0f, value));
    }
}

