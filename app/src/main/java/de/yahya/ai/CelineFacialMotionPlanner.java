package de.yahya.ai;

/**
 * Pure runtime planner for the validated six-target Celine facial candidate.
 *
 * This class intentionally does not touch Filament or the production GLB. It only computes
 * bounded target weights so the renderer hook can stay disabled until a morph-enabled Celine
 * candidate passes identity, HOME/CALL framing and lifecycle gates.
 */
public final class CelineFacialMotionPlanner {
    public static final int BLINK_LEFT = 0;
    public static final int BLINK_RIGHT = 1;
    public static final int BLINK_BOTH = 2;
    public static final int JAW_OPEN = 3;
    public static final int ROUNDED_VOWEL = 4;
    public static final int SPREAD_VOWEL = 5;
    public static final int TARGET_COUNT = 6;

    private static final float MAX_BLINK = 0.92f;
    private static final float MAX_JAW = 0.56f;
    private static final float MAX_VOWEL = 0.42f;
    private static final float MAX_MICRO = 0.055f;

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
        CelineAvatarController.State safeState = state == null
                ? CelineAvatarController.State.IDLE : state;
        SpeechVisemeAnalyzer.Cue safeCue = cue == null ? SpeechVisemeAnalyzer.silent() : cue;
        float energy = clamp01(speechEnergy);

        float[] target = new float[TARGET_COUNT];
        applyBlink(nowMs, safeState, target);
        applySpeech(safeState, safeCue, energy, target);

        float smoothing = safeState == CelineAvatarController.State.SPEAKING ? 0.30f : 0.22f;
        for (int i = 0; i < TARGET_COUNT; i++) {
            current[i] = current[i] + (target[i] - current[i]) * smoothing;
            current[i] = clamp01(current[i]);
        }

        float micro = microExpression(nowMs, safeState, energy);
        return new Frame(current.clone(), micro);
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
        final long closeMs = 72L;
        final long holdMs = 34L;
        final long openMs = 108L;
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

        float stateScale = state == CelineAvatarController.State.THINKING ? 0.88f : 1.0f;
        float lead = MAX_BLINK * stateScale * phase;
        float lag = MAX_BLINK * stateScale * clamp01((phase - 0.08f) / 0.92f);
        if (blinkLeadLeft) {
            target[BLINK_LEFT] = lead;
            target[BLINK_RIGHT] = lag;
        } else {
            target[BLINK_LEFT] = lag;
            target[BLINK_RIGHT] = lead;
        }
        target[BLINK_BOTH] = Math.min(MAX_BLINK, Math.min(target[BLINK_LEFT], target[BLINK_RIGHT]) * 0.32f);
    }

    private void applySpeech(CelineAvatarController.State state,
                             SpeechVisemeAnalyzer.Cue cue,
                             float energy,
                             float[] target) {
        if (state != CelineAvatarController.State.SPEAKING) return;

        float openness = clamp01(cue.openness);
        float width = clamp01(cue.width);
        float roundness = clamp01(cue.roundness);
        float voiced = 0.35f + 0.65f * energy;

        target[JAW_OPEN] = Math.min(MAX_JAW, openness * MAX_JAW * voiced);
        switch (cue.shape) {
            case ROUND:
            case LABIAL:
                target[ROUNDED_VOWEL] = Math.min(MAX_VOWEL, (0.22f + 0.78f * roundness) * MAX_VOWEL * voiced);
                target[SPREAD_VOWEL] = Math.min(MAX_VOWEL * 0.18f, width * 0.08f);
                break;
            case WIDE:
            case TEETH:
                target[SPREAD_VOWEL] = Math.min(MAX_VOWEL, (0.20f + 0.80f * width) * MAX_VOWEL * voiced);
                target[ROUNDED_VOWEL] = Math.min(MAX_VOWEL * 0.15f, roundness * 0.06f);
                break;
            case OPEN:
                target[ROUNDED_VOWEL] = Math.min(MAX_VOWEL * 0.36f, roundness * 0.15f);
                target[SPREAD_VOWEL] = Math.min(MAX_VOWEL * 0.36f, width * 0.15f);
                break;
            case CLOSED:
            default:
                target[JAW_OPEN] *= 0.18f;
                break;
        }
    }

    private float microExpression(long nowMs, CelineAvatarController.State state, float energy) {
        double t = nowMs / 1000.0;
        float base = (float) Math.sin(t * 0.41 + 0.9) * 0.5f + 0.5f;
        float scale;
        switch (state) {
            case LISTENING:
                scale = 0.80f;
                break;
            case THINKING:
                scale = 0.45f;
                break;
            case SPEAKING:
                scale = 0.55f + 0.25f * energy;
                break;
            case IDLE:
            default:
                scale = 0.65f;
                break;
        }
        return Math.min(MAX_MICRO, base * MAX_MICRO * scale);
    }

    private void scheduleNextBlink(long nowMs) {
        // Deterministic bounded cadence: 2.6-4.7 s, avoiding jittery rapid blinking.
        long interval = 2600L + ((blinkSerial * 977L + 613L) % 2100L);
        nextBlinkAtMs = nowMs + interval;
    }

    private static float smoothstep(float x) {
        return x * x * (3.0f - 2.0f * x);
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
