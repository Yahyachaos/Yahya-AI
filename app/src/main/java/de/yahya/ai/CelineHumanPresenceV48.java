package de.yahya.ai;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.lang.ref.WeakReference;
import java.util.Random;

/**
 * Small, renderer-independent human-presence layer for 3D Celine.
 *
 * It deliberately touches only the existing head/neck look API. The Filament material pipeline,
 * model normalization and v47 call-motion lock stay unchanged. The goal is believable camera
 * attention: mostly eye-contact, short thought glances, small speaking shifts and an immediate
 * override whenever the UI explicitly asks Celine to look somewhere.
 */
final class CelineHumanPresenceV48 {
    private static final long EXPLICIT_LOOK_HOLD_MS = 1700L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random(0xCE11E48L);
    private WeakReference<Celine3DView> viewRef = new WeakReference<>(null);
    private CelineAvatarController.State state = CelineAvatarController.State.IDLE;
    private boolean running;
    private long explicitLookUntil;

    private final Runnable gazeTick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            Celine3DView view = viewRef.get();
            if (view == null) {
                stop();
                return;
            }

            long now = SystemClock.uptimeMillis();
            if (now >= explicitLookUntil) {
                float[] target = nextTarget(state);
                view.setLook(target[0], target[1]);
            }
            handler.postDelayed(this, nextDelayMs(state));
        }
    };

    void attach(Celine3DView view) {
        if (view == null) return;
        viewRef = new WeakReference<>(view);
        if (!running) {
            running = true;
            Celine3DDiagnostics.record(view.getContext(), "V48-100", "Human-Presence gestartet",
                    "camera-attention=on · state-aware gaze=on");
            view.setLook(0.0f, -0.01f);
            handler.postDelayed(gazeTick, 950L);
        }
    }

    void setState(CelineAvatarController.State next) {
        state = next == null ? CelineAvatarController.State.IDLE : next;
        Celine3DView view = viewRef.get();
        if (view != null) {
            Celine3DDiagnostics.record(view.getContext(), "V48-110", "Human-Presence Zustand",
                    state.name());
            if (state == CelineAvatarController.State.LISTENING) {
                view.setLook(0.0f, -0.015f);
            }
        }
    }

    void explicitLook(float x, float y) {
        Celine3DView view = viewRef.get();
        if (view == null) return;
        explicitLookUntil = SystemClock.uptimeMillis() + EXPLICIT_LOOK_HOLD_MS;
        view.setLook(x, y);
    }

    void releaseExplicitLook() {
        explicitLookUntil = SystemClock.uptimeMillis() + 450L;
        Celine3DView view = viewRef.get();
        if (view != null) view.setLook(0.0f, -0.01f);
    }

    void stop() {
        running = false;
        handler.removeCallbacks(gazeTick);
        Celine3DView view = viewRef.get();
        if (view != null) view.releaseLook();
        viewRef.clear();
    }

    private float[] nextTarget(CelineAvatarController.State current) {
        float x;
        float y;
        switch (current) {
            case LISTENING:
                // Strong eye contact while the user is talking.
                x = jitter(0.022f);
                y = -0.012f + jitter(0.018f);
                break;
            case THINKING:
                // Occasional brief side/up glance, but never enough to look distracted.
                x = random.nextFloat() < 0.52f ? jitter(0.055f) : signedRange(0.075f, 0.14f);
                y = -0.045f + jitter(0.045f);
                break;
            case SPEAKING:
                // Conversational eye contact with slightly more movement than listening.
                x = random.nextFloat() < 0.80f ? jitter(0.04f) : signedRange(0.07f, 0.115f);
                y = -0.012f + jitter(0.028f);
                break;
            case IDLE:
            default:
                x = random.nextFloat() < 0.86f ? jitter(0.05f) : signedRange(0.06f, 0.105f);
                y = -0.005f + jitter(0.03f);
                break;
        }
        return new float[]{clamp(x, -0.18f, 0.18f), clamp(y, -0.12f, 0.09f)};
    }

    private long nextDelayMs(CelineAvatarController.State current) {
        switch (current) {
            case LISTENING: return 2400L + random.nextInt(1900);
            case THINKING: return 1350L + random.nextInt(1550);
            case SPEAKING: return 1700L + random.nextInt(1700);
            case IDLE:
            default: return 2300L + random.nextInt(2400);
        }
    }

    private float jitter(float amount) {
        return (random.nextFloat() * 2.0f - 1.0f) * amount;
    }

    private float signedRange(float min, float max) {
        float value = min + random.nextFloat() * (max - min);
        return random.nextBoolean() ? value : -value;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
