package de.yahya.ai;

import android.animation.ObjectAnimator;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;

import java.util.Random;

/**
 * Owns Celine's avatar state and keeps the portrait in constant, subtle motion.
 * The goal is a video-call presence instead of a periodically wobbling still image:
 * breathing is slow, head movement is irregular, gaze drifts independently and speech
 * creates small, non-repeating reactions.
 */
public final class CelineAvatarController implements SpeechAudioBus.Listener {
    public enum State { IDLE, LISTENING, THINKING, SPEAKING }

    private final View motionView;
    private final ImageView avatar;
    private final CelineFaceOverlayView face;
    private final float density;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private ObjectAnimator breath, lift;
    private State state = State.IDLE;
    private boolean released;
    private boolean userLooking;
    private float speechEnergy;

    private final Runnable microMotionTask = new Runnable() {
        @Override public void run() {
            if (released || motionView == null) return;
            if (!userLooking) playMicroMotion();
            scheduleMicroMotion();
        }
    };

    private final Runnable gazeTask = new Runnable() {
        @Override public void run() {
            if (released || face == null) return;
            if (!userLooking) playNaturalGaze();
            scheduleGaze();
        }
    };

    public CelineAvatarController(View motionView, ImageView avatar,
                                  CelineFaceOverlayView face, float density) {
        this.motionView = motionView;
        this.avatar = avatar;
        this.face = face;
        this.density = density;
        if (face != null) face.start();
        if (motionView != null) {
            motionView.post(() -> {
                motionView.setPivotX(motionView.getWidth() * 0.50f);
                motionView.setPivotY(motionView.getHeight() * 0.78f);
            });
        }
        SpeechAudioBus.setListener(this);
        scheduleMicroMotion();
        scheduleGaze();
    }

    public State getState() { return state; }

    public void setState(State next) {
        if (next == null) next = State.IDLE;
        state = next;
        stopLoopsOnly();
        avatar.setImageResource(de.yahya.ai.R.drawable.celine_avatar);
        syncFaceState(next);

        switch (next) {
            case LISTENING:
                startBreath(0.998f, 1.011f, 3100L);
                startLift(dp(0.3f), -dp(1.6f), 3500L);
                break;
            case THINKING:
                startBreath(0.998f, 1.009f, 3900L);
                startLift(dp(0.2f), -dp(1.3f), 4300L);
                break;
            case SPEAKING:
                startBreath(0.998f, 1.014f, 2500L);
                startLift(dp(0.3f), -dp(1.8f), 3000L);
                break;
            case IDLE:
            default:
                startBreath(0.999f, 1.008f, 4700L);
                startLift(dp(0.2f), -dp(1.2f), 5200L);
                break;
        }
    }

    @Override public void onSpeechAudioLevel(float level) {
        float clamped = clamp(level, 0f, 1f);
        speechEnergy = speechEnergy * 0.72f + clamped * 0.28f;
        if (face != null) face.setMouthLevel(state == State.SPEAKING ? clamped : 0f);

        if (avatar != null && state == State.SPEAKING && !userLooking) {
            // Tiny shoulder/head emphasis from the real output envelope. Keep this deliberately
            // restrained so lips provide the visible speech cue instead of whole-frame shaking.
            float x = (speechEnergy - 0.35f) * dp(0.45f);
            float y = -speechEnergy * dp(0.32f);
            avatar.setTranslationX(x);
            avatar.setTranslationY(y);
        }
    }

    @Override public void onSpeechViseme(SpeechVisemeAnalyzer.Cue cue) {
        if (face != null) face.setViseme(state == State.SPEAKING ? cue : SpeechVisemeAnalyzer.silent());
    }

    public void lookToward(float normalizedX, float normalizedY) {
        userLooking = true;
        if (motionView == null) return;
        float x = clamp(normalizedX, -0.5f, 0.5f), y = clamp(normalizedY, -0.5f, 0.5f);
        motionView.animate().cancel();
        motionView.setTranslationX(x * dp(5.0f));
        motionView.setTranslationY(y * dp(2.8f));
        motionView.setRotation(x * 0.65f);
        if (face != null) face.setGaze(x * 2f, y * 2f);
    }

    public void releaseLook() {
        userLooking = false;
        if (motionView != null) {
            motionView.animate().translationX(0f).translationY(0f).rotation(0f)
                    .setDuration(500L).setInterpolator(new AccelerateDecelerateInterpolator()).start();
        }
        if (face != null) face.releaseGaze();
    }

    public void blink() { if (face != null) face.blinkNow(false); }

    public void release() {
        released = true;
        handler.removeCallbacksAndMessages(null);
        SpeechAudioBus.clearListener(this);
        stopMotion();
        if (face != null) face.stop();
    }

    private void playMicroMotion() {
        if (motionView == null) return;

        float rotation;
        float x;
        float y;
        long duration;

        switch (state) {
            case LISTENING:
                // Listener behaviour: occasional small nods and slight lean toward the user.
                rotation = randomBetween(-0.35f, 0.35f);
                x = randomBetween(-dp(1.0f), dp(1.0f));
                y = randomBetween(-dp(2.2f), -dp(0.4f));
                duration = 650L + random.nextInt(500);
                break;
            case THINKING:
                rotation = randomBetween(-0.75f, 0.75f);
                x = randomBetween(-dp(1.6f), dp(1.6f));
                y = randomBetween(-dp(1.5f), dp(0.6f));
                duration = 900L + random.nextInt(850);
                break;
            case SPEAKING:
                rotation = randomBetween(-0.55f, 0.55f);
                x = randomBetween(-dp(1.4f), dp(1.4f));
                y = randomBetween(-dp(1.9f), dp(0.2f));
                duration = 520L + random.nextInt(620);
                break;
            case IDLE:
            default:
                rotation = randomBetween(-0.40f, 0.40f);
                x = randomBetween(-dp(0.9f), dp(0.9f));
                y = randomBetween(-dp(1.0f), dp(0.6f));
                duration = 1100L + random.nextInt(1000);
                break;
        }

        motionView.animate().cancel();
        motionView.animate()
                .rotation(rotation)
                .translationX(x)
                .translationY(y)
                .setDuration(duration)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    if (released || userLooking || motionView == null) return;
                    motionView.animate()
                            .rotation(randomBetween(-0.10f, 0.10f))
                            .translationX(0f)
                            .translationY(0f)
                            .setDuration(700L + random.nextInt(450))
                            .setInterpolator(new AccelerateDecelerateInterpolator())
                            .start();
                })
                .start();

        if (state == State.LISTENING && random.nextInt(3) == 0 && face != null) face.blinkNow(false);
    }

    private void playNaturalGaze() {
        if (face == null) return;
        // Most of the time Celin looks near the camera, with brief human-like glances.
        float rangeX = state == State.THINKING ? 0.55f : 0.30f;
        float rangeY = state == State.THINKING ? 0.34f : 0.20f;
        float gx = randomBetween(-rangeX, rangeX);
        float gy = randomBetween(-rangeY, rangeY);
        if (random.nextInt(5) != 0) { gx *= 0.45f; gy *= 0.45f; }
        face.setGaze(gx, gy);
        handler.postDelayed(() -> {
            if (!released && !userLooking && face != null) face.releaseGaze();
        }, 450L + random.nextInt(800));
    }

    private void scheduleMicroMotion() {
        if (released) return;
        long delay;
        switch (state) {
            case SPEAKING: delay = 1200L + random.nextInt(1800); break;
            case LISTENING: delay = 1700L + random.nextInt(2600); break;
            case THINKING: delay = 2200L + random.nextInt(3000); break;
            case IDLE:
            default: delay = 2800L + random.nextInt(4200); break;
        }
        handler.removeCallbacks(microMotionTask);
        handler.postDelayed(microMotionTask, delay);
    }

    private void scheduleGaze() {
        if (released) return;
        long delay = state == State.THINKING
                ? 1600L + random.nextInt(2600)
                : 2600L + random.nextInt(4200);
        handler.removeCallbacks(gazeTask);
        handler.postDelayed(gazeTask, delay);
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
        scheduleMicroMotion();
        scheduleGaze();
    }

    private void startBreath(float from, float to, long duration) {
        if (motionView == null) return;
        breath = ObjectAnimator.ofFloat(motionView, "scaleY", from, to, from);
        breath.setDuration(duration);
        breath.setRepeatCount(ObjectAnimator.INFINITE);
        breath.setInterpolator(new AccelerateDecelerateInterpolator());
        breath.addUpdateListener(a -> {
            float sy = (Float) a.getAnimatedValue();
            motionView.setScaleY(sy);
            // Chest expansion is slightly wider than vertical movement, but never enough to
            // look like a zoom animation.
            motionView.setScaleX(1f + (sy - 1f) * 0.48f);
        });
        breath.start();
    }

    private void startLift(float from, float to, long duration) {
        if (avatar == null) return;
        lift = ObjectAnimator.ofFloat(avatar, "translationY", from, to, from);
        lift.setDuration(duration);
        lift.setRepeatCount(ObjectAnimator.INFINITE);
        lift.setInterpolator(new AccelerateDecelerateInterpolator());
        lift.start();
    }

    private void stopLoopsOnly() {
        try { if (breath != null) breath.cancel(); } catch (Exception ignored) {}
        try { if (lift != null) lift.cancel(); } catch (Exception ignored) {}
        breath = null;
        lift = null;
        speechEnergy = 0f;
        if (motionView != null) {
            motionView.setScaleX(1f);
            motionView.setScaleY(1f);
        }
        if (avatar != null) {
            avatar.setTranslationX(0f);
            avatar.setTranslationY(0f);
        }
    }

    private void stopMotion() {
        stopLoopsOnly();
        if (motionView != null) {
            motionView.animate().cancel();
            motionView.setAlpha(1f);
            motionView.setRotation(0f);
            motionView.setTranslationX(0f);
            motionView.setTranslationY(0f);
        }
    }

    private float dp(float value) { return value * density; }
    private float randomBetween(float min, float max) { return min + random.nextFloat() * (max - min); }
    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
}
