package de.yahya.ai;

import android.animation.ObjectAnimator;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;

/**
 * Owns Celine's avatar state, whole-frame motion and facial overlay state.
 * The approved portrait remains untouched; motion is applied to a container so
 * the non-destructive face overlay stays perfectly aligned with the image.
 */
public final class CelineAvatarController {
    public enum State { IDLE, LISTENING, THINKING, SPEAKING }

    private final View motionView;
    private final ImageView avatar;
    private final CelineFaceOverlayView face;
    private final float density;
    private ObjectAnimator breath;
    private ObjectAnimator sway;
    private ObjectAnimator lift;
    private State state = State.IDLE;

    public CelineAvatarController(View motionView, ImageView avatar,
                                  CelineFaceOverlayView face, float density) {
        this.motionView = motionView;
        this.avatar = avatar;
        this.face = face;
        this.density = density;
        if (face != null) face.start();
    }

    public State getState() { return state; }

    public void setState(State next) {
        if (next == null) next = State.IDLE;
        state = next;
        stopMotion();
        avatar.setImageResource(de.yahya.ai.R.drawable.celine_avatar);
        syncFaceState(next);
        switch (next) {
            case LISTENING:
                startBreath(1.0f, 1.014f, 1250L);
                startLift(0f, -dp(0.8f), 2100L);
                break;
            case THINKING:
                startBreath(0.999f, 1.007f, 2300L);
                startSway(-0.12f, 0.12f, 4800L);
                break;
            case SPEAKING:
                startBreath(1.0f, 1.010f, 1450L);
                startSway(-0.09f, 0.09f, 3300L);
                startLift(0f, -dp(0.6f), 2500L);
                break;
            case IDLE:
            default:
                startBreath(1.0f, 1.007f, 5200L);
                startSway(-0.14f, 0.14f, 8800L);
                startLift(0f, -dp(0.9f), 6900L);
                break;
        }
    }

    /** Small reaction to touch while keeping image and facial overlay aligned. */
    public void lookToward(float normalizedX, float normalizedY) {
        if (motionView == null) return;
        float x = clamp(normalizedX, -0.5f, 0.5f);
        float y = clamp(normalizedY, -0.5f, 0.5f);
        motionView.setTranslationX(x * dp(6f));
        motionView.setTranslationY(y * dp(3f));
        motionView.setRotation(x * 0.38f);
    }

    public void releaseLook() {
        if (motionView == null) return;
        motionView.animate().translationX(0f).translationY(0f).rotation(0f)
                .setDuration(360L).setInterpolator(new AccelerateDecelerateInterpolator()).start();
    }

    public void blink() {
        if (face != null) face.blinkNow(false);
    }

    public void release() {
        stopMotion();
        if (face != null) face.stop();
    }

    private void syncFaceState(State next) {
        if (face == null) return;
        switch (next) {
            case LISTENING: face.setActivity(CelineFaceOverlayView.Activity.LISTENING); break;
            case THINKING: face.setActivity(CelineFaceOverlayView.Activity.THINKING); break;
            case SPEAKING: face.setActivity(CelineFaceOverlayView.Activity.SPEAKING); break;
            case IDLE:
            default: face.setActivity(CelineFaceOverlayView.Activity.IDLE); break;
        }
    }

    private void startBreath(float from, float to, long duration) {
        breath = ObjectAnimator.ofFloat(motionView, "scaleX", from, to, from);
        breath.setDuration(duration);
        breath.setRepeatCount(ObjectAnimator.INFINITE);
        breath.setInterpolator(new AccelerateDecelerateInterpolator());
        breath.addUpdateListener(a -> motionView.setScaleY((Float) a.getAnimatedValue()));
        breath.start();
    }

    private void startSway(float from, float to, long duration) {
        sway = ObjectAnimator.ofFloat(motionView, "rotation", from, to, from);
        sway.setDuration(duration);
        sway.setRepeatCount(ObjectAnimator.INFINITE);
        sway.setInterpolator(new AccelerateDecelerateInterpolator());
        sway.start();
    }

    private void startLift(float from, float to, long duration) {
        lift = ObjectAnimator.ofFloat(motionView, "translationY", from, to, from);
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
        if (motionView != null) {
            motionView.setScaleX(1f); motionView.setScaleY(1f); motionView.setAlpha(1f);
            motionView.setRotation(0f); motionView.setTranslationX(0f); motionView.setTranslationY(0f);
        }
    }

    private float dp(float value) { return value * density; }
    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
}
