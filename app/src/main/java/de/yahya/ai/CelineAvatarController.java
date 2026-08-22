package de.yahya.ai;

import android.animation.ObjectAnimator;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;

/** Owns Celine's avatar state, whole-frame motion and facial overlay state. */
public final class CelineAvatarController implements SpeechAudioBus.Listener {
    public enum State { IDLE, LISTENING, THINKING, SPEAKING }

    private final View motionView;
    private final ImageView avatar;
    private final CelineFaceOverlayView face;
    private final float density;
    private ObjectAnimator breath, sway, lift;
    private State state = State.IDLE;

    public CelineAvatarController(View motionView, ImageView avatar,
                                  CelineFaceOverlayView face, float density) {
        this.motionView = motionView;
        this.avatar = avatar;
        this.face = face;
        this.density = density;
        if (face != null) face.start();
        SpeechAudioBus.setListener(this);
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
                startBreath(1.0f, 1.010f, 1550L);
                startLift(0f, -dp(0.55f), 2600L);
                break;
            case THINKING:
                startBreath(0.999f, 1.006f, 2500L);
                startSway(-0.10f, 0.10f, 5200L);
                break;
            case SPEAKING:
                startBreath(1.0f, 1.008f, 1750L);
                startSway(-0.075f, 0.075f, 3700L);
                startLift(0f, -dp(0.45f), 2800L);
                break;
            case IDLE:
            default:
                startBreath(1.0f, 1.0055f, 5600L);
                startSway(-0.11f, 0.11f, 9200L);
                startLift(0f, -dp(0.65f), 7400L);
                break;
        }
    }

    @Override public void onSpeechAudioLevel(float level) {
        if (face != null) face.setMouthLevel(state == State.SPEAKING ? level : 0f);
    }

    @Override public void onSpeechViseme(SpeechVisemeAnalyzer.Cue cue) {
        if (face != null) face.setViseme(state == State.SPEAKING ? cue : SpeechVisemeAnalyzer.silent());
    }

    public void lookToward(float normalizedX, float normalizedY) {
        if (motionView == null) return;
        float x = clamp(normalizedX, -0.5f, 0.5f), y = clamp(normalizedY, -0.5f, 0.5f);
        // Keep the head movement subtle; the overlay handles the smaller eye micro-shift.
        motionView.setTranslationX(x * dp(3.2f));
        motionView.setTranslationY(y * dp(1.8f));
        motionView.setRotation(x * 0.24f);
        if (face != null) face.setGaze(x * 2f, y * 2f);
    }

    public void releaseLook() {
        if (motionView != null) {
            motionView.animate().translationX(0f).translationY(0f).rotation(0f)
                    .setDuration(360L).setInterpolator(new AccelerateDecelerateInterpolator()).start();
        }
        if (face != null) face.releaseGaze();
    }

    public void blink() { if (face != null) face.blinkNow(false); }

    public void release() {
        SpeechAudioBus.clearListener(this);
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
