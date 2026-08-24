package de.yahya.ai;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.util.Random;
import java.util.WeakHashMap;

/**
 * v48 human-presence layer for 3D Celine.
 *
 * This controller is intentionally narrow: it only drives the existing Celine3DView look target.
 * It does not touch GLB bytes, materials, TRUE-UNLIT/FORCE-C, camera projection, model scaling,
 * seated body posing or the v47 call-motion lock.
 *
 * The resulting behavior is less robotic: Celine keeps camera attention most of the time, makes
 * small natural gaze shifts while idle/speaking, listens with stronger eye contact and briefly
 * looks aside/up while thinking. Avatar state is read from the already-running renderer so no
 * duplicate speech state machine is introduced.
 */
final class CelineHumanPresenceV48 {
    private static final WeakHashMap<Activity, Controller> CONTROLLERS = new WeakHashMap<>();

    private CelineHumanPresenceV48() {}

    static void install(Activity activity, View decor) {
        if (!(activity instanceof MainActivity) || decor == null) return;
        Controller c;
        synchronized (CONTROLLERS) {
            c = CONTROLLERS.get(activity);
            if (c == null) {
                c = new Controller(activity, decor);
                CONTROLLERS.put(activity, c);
            }
        }
        c.resume();
    }

    static void onPaused(Activity activity) {
        Controller c;
        synchronized (CONTROLLERS) { c = CONTROLLERS.get(activity); }
        if (c != null) c.pause();
    }

    static void onDestroyed(Activity activity) {
        Controller c;
        synchronized (CONTROLLERS) { c = CONTROLLERS.remove(activity); }
        if (c != null) c.destroy();
    }

    private static final class Controller {
        final Activity activity;
        final View decor;
        final Handler handler = new Handler(Looper.getMainLooper());
        final Random random = new Random(0xCE11E48L);

        boolean running;
        boolean announced;
        Celine3DView boundView;
        CelineAvatarController.State lastState = CelineAvatarController.State.IDLE;

        final Runnable tick = new Runnable() {
            @Override public void run() {
                if (!running) return;
                Celine3DView view = find3D(decor);
                if (view == null || !view.isAttachedToWindow()) {
                    handler.postDelayed(this, 700L);
                    return;
                }

                if (boundView != view) {
                    if (boundView != null) {
                        try { boundView.releaseLook(); } catch (Throwable ignored) {}
                    }
                    boundView = view;
                    announced = false;
                }

                CelineAvatarController.State state = readAvatarState(view);
                if (!announced) {
                    announced = true;
                    Celine3DDiagnostics.record(activity, "V48-100", "Human-Presence gestartet",
                            "camera-attention=on · state-aware gaze=on · renderer pipeline unchanged");
                }
                if (state != lastState) {
                    lastState = state;
                    Celine3DDiagnostics.record(activity, "V48-110", "Human-Presence Zustand", state.name());
                }

                float[] target = nextTarget(state);
                view.setLook(target[0], target[1]);
                handler.postDelayed(this, nextDelayMs(state));
            }
        };

        Controller(Activity activity, View decor) {
            this.activity = activity;
            this.decor = decor;
        }

        void resume() {
            if (running) return;
            running = true;
            handler.post(tick);
        }

        void pause() {
            running = false;
            handler.removeCallbacks(tick);
        }

        void destroy() {
            pause();
            if (boundView != null) {
                try { boundView.releaseLook(); } catch (Throwable ignored) {}
            }
            boundView = null;
        }

        private float[] nextTarget(CelineAvatarController.State state) {
            float x;
            float y;
            switch (state) {
                case LISTENING:
                    // Strong camera attention while the user is speaking.
                    x = jitter(0.018f);
                    y = -0.012f + jitter(0.014f);
                    break;
                case THINKING:
                    // Brief cognitive glance, still subtle enough for a video call.
                    x = random.nextFloat() < 0.55f ? jitter(0.045f) : signedRange(0.070f, 0.125f);
                    y = -0.040f + jitter(0.035f);
                    break;
                case SPEAKING:
                    // Mostly eye contact, with occasional small conversational side glances.
                    x = random.nextFloat() < 0.82f ? jitter(0.035f) : signedRange(0.060f, 0.100f);
                    y = -0.010f + jitter(0.024f);
                    break;
                case IDLE:
                default:
                    x = random.nextFloat() < 0.88f ? jitter(0.043f) : signedRange(0.055f, 0.090f);
                    y = -0.004f + jitter(0.025f);
                    break;
            }
            return new float[]{clamp(x, -0.16f, 0.16f), clamp(y, -0.10f, 0.08f)};
        }

        private long nextDelayMs(CelineAvatarController.State state) {
            switch (state) {
                case LISTENING: return 2600L + random.nextInt(1800);
                case THINKING: return 1450L + random.nextInt(1450);
                case SPEAKING: return 1850L + random.nextInt(1650);
                case IDLE:
                default: return 2500L + random.nextInt(2200);
            }
        }

        private float jitter(float amount) {
            return (random.nextFloat() * 2.0f - 1.0f) * amount;
        }

        private float signedRange(float min, float max) {
            float value = min + random.nextFloat() * (max - min);
            return random.nextBoolean() ? value : -value;
        }
    }

    private static CelineAvatarController.State readAvatarState(Celine3DView view) {
        try {
            Field f = Celine3DView.class.getDeclaredField("avatarState");
            f.setAccessible(true);
            Object value = f.get(view);
            if (value instanceof CelineAvatarController.State) {
                return (CelineAvatarController.State) value;
            }
        } catch (Throwable ignored) {}
        return CelineAvatarController.State.IDLE;
    }

    private static Celine3DView find3D(View root) {
        if (root instanceof Celine3DView) return (Celine3DView) root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                Celine3DView found = find3D(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
