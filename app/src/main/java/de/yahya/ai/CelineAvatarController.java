package de.yahya.ai;

import android.animation.ObjectAnimator;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;

/**
 * Owns Celine's avatar state and subtle whole-frame motion.
 *
 * This is intentionally conservative: it keeps the approved Celine artwork
 * unchanged and only coordinates motion/state. Facial layers (blink, gaze,
 * mouth/visemes) can be added behind this controller later without coupling
 * them to MainActivity.
 */
public final class CelineAvatarController {
    public enum State { IDLE, LISTENING, THINKING, SPEAKING }

    private final ImageView avatar;
    private final float density;
    private ObjectAnimator breath;
    private ObjectAnimator sway;
    private ObjectAnimator lift;
    private State state = State.IDLE;

    public CelineAvatarController(ImageView avatar, float density) {
        this.avatar = avatar;
        this.density = density;
    }

    public State getState() { return state; }

    public void setState(State next) {
        if (next == null) next = State.IDLE;
        state = next;
        stopMotion();
        avatar.setImageResource(de.yahya.ai.R.drawable.celine_avatar);
        switch (next) {
            case LISTENING:
                startBreath(1.0f, 1.018f, 1050L);
                startLift(0f, -dp(1.2f), 1800L);
                break;
            case THINKING:
                startBreath(0.998f, 1.009f, 2100L);
                startSway(-0.16f, 0.16f, 4200L);
                break;
            case SPEAKING:
                startBreath(1.0f, 1.012f, 1250L);
                startSway(-0.12f, 0.12f, 2800L);
                startLift(0f, -dp(0.8f), 2200L);
                break;
            case IDLE:
            default:
                startBreath(1.0f, 1.009f, 4700L);
                startSway(-0.20f, 0.20f, 7800L);
                startLift(0f, -dp(1.3f), 6100L);
                break;
        }
    }

    /** Small reaction to touch without changing Celine's artwork. */
    public void lookToward(float normalizedX, float normalizedY) {
        if (avatar == null) return;
        float x = clamp(normalizedX, -0.5f, 0.5f);
        float y = clamp(normalizedY, -0.5f, 0.5f);
        avatar.setTranslationX(x * dp(8f));
        avatar.setTranslationY(y * dp(4f));
        avatar.setRotation(x * 0.55f);
    }

    public void releaseLook() {
        if (avatar == null) return;
        avatar.animate().translationX(0f).translationY(0f).rotation(0f)
                .setDuration(320L).setInterpolator(new AccelerateDecelerateInterpolator()).start();
    }

    public void release() { stopMotion(); }

    private void startBreath(float from, float to, long duration) {
        breath = ObjectAnimator.ofFloat(avatar, "scaleX", from, to, from);
        breath.setDuration(duration);
        breath.setRepeatCount(ObjectAnimator.INFINITE);
        breath.setInterpolator(new AccelerateDecelerateInterpolator());
        breath.addUpdateListener(a -> avatar.setScaleY((Float) a.getAnimatedValue()));
        breath.start();
    }

    private void startSway(float from, float to, long duration) {
        sway = ObjectAnimator.ofFloat(avatar, "rotation", from, to, from);
        sway.setDuration(duration);
        sway.setRepeatCount(ObjectAnimator.INFINITE);
        sway.setInterpolator(new AccelerateDecelerateInterpolator());
        sway.start();
    }

    private void startLift(float from, float to, long duration) {
        lift = ObjectAnimator.ofFloat(avatar, "translationY", from, to, from);
        lift.setDuration(duration);
        lift.setRepeatCount(ObjectAnimator.INFINITE);
        lift.setInterpolator(new AccelerateDecelerateInterpolator());
        lift.start();
    }

    private void stopMotion() {
        try { if (breath != null) breath.cancel(); } catch (Exception ignored) {}
        try { if (sway != null) sway.cancel(); } catch (Exception ignored) {}
        try { if (lift != null) lift.cancel(); } catch (Exception ignored) {}
        breath = null; sway = null; lift = null;
        if (avatar != null) {
            avatar.setScaleX(1f); avatar.setScaleY(1f); avatar.setAlpha(1f);
            avatar.setRotation(0f); avatar.setTranslationX(0f); avatar.setTranslationY(0f);
        }
    }

    private float dp(float value) { return value * density; }
    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
}
