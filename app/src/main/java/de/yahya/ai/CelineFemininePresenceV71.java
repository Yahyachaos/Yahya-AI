package de.yahya.ai;

import android.app.Activity;
import android.opengl.Matrix;
import android.view.Choreographer;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.filament.TransformManager;
import com.google.android.filament.gltfio.Animator;
import com.google.android.filament.gltfio.FilamentAsset;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * v71 begins roadmap order 3 with a deliberately narrow feminine HOME posture owner.
 *
 * The v58 shoulder path stays quarantined because real production evidence proved that adding
 * shoulder transforms could pull a large textured mesh fragment into frame. Hips, however, were
 * already production-proven before that regression. This owner therefore writes only Hips, only
 * on HOME, from the renderer-captured base. CALL is left entirely to v70's seated lower-body owner.
 * No shoulder, arm, root, leg, head, face, camera or layout geometry is touched here.
 */
final class CelineFemininePresenceV71 {
    private static final float HOME_HIPS_PITCH = -1.0f;
    private static final float HOME_HIPS_YAW = -1.6f;
    private static final float HOME_HIPS_ROLL = 2.2f;

    private static final WeakHashMap<Activity, Controller> CONTROLLERS = new WeakHashMap<>();

    private CelineFemininePresenceV71() {}

    static void install(Activity activity, View decor) {
        if (!(activity instanceof MainActivity) || decor == null) return;
        Controller controller;
        synchronized (CONTROLLERS) {
            controller = CONTROLLERS.get(activity);
            if (controller == null) {
                controller = new Controller(activity, decor);
                CONTROLLERS.put(activity, controller);
            }
        }
        controller.resume();
    }

    static void onPaused(Activity activity) {
        Controller controller;
        synchronized (CONTROLLERS) { controller = CONTROLLERS.get(activity); }
        if (controller != null) controller.pause();
    }

    static void onDestroyed(Activity activity) {
        Controller controller;
        synchronized (CONTROLLERS) { controller = CONTROLLERS.remove(activity); }
        if (controller != null) controller.destroy();
    }

    private static final class Controller implements Choreographer.FrameCallback {
        final Activity activity;
        final View decor;
        final Choreographer choreographer = Choreographer.getInstance();
        boolean running;
        boolean paused;
        Celine3DView boundView;
        Driver driver;

        Controller(Activity activity, View decor) {
            this.activity = activity;
            this.decor = decor;
        }

        void resume() {
            paused = false;
            if (running) return;
            running = true;
            choreographer.postFrameCallback(this);
        }

        void pause() {
            paused = true;
            if (driver != null) driver.restore(false);
        }

        void destroy() {
            running = false;
            paused = true;
            choreographer.removeFrameCallback(this);
            if (driver != null) driver.restore(false);
            driver = null;
            boundView = null;
        }

        @Override public void doFrame(long frameTimeNanos) {
            if (!running) return;
            choreographer.postFrameCallback(this);
            if (paused) return;

            Celine3DView view = find3D(decor);
            if (view == null || !view.isAttachedToWindow()) return;
            if (driver == null || boundView != view) {
                try {
                    if (driver != null) driver.restore(false);
                    boundView = view;
                    driver = new Driver(activity, view);
                } catch (Throwable error) {
                    Celine3DDiagnostics.error(activity, "V71-199",
                            "Feminine HOME-Praesenz Initialisierung FEHLER", error);
                    driver = null;
                    return;
                }
            }

            try {
                driver.apply();
            } catch (Throwable error) {
                Celine3DDiagnostics.error(activity, "V71-198",
                        "Feminine HOME-Praesenz Frame FEHLER", error);
                driver.disableAfterFailure();
            }
        }
    }

    private static final class Bone {
        final int instance;
        final float[] base;

        Bone(int instance, float[] base) {
            this.instance = instance;
            this.base = base;
        }
    }

    private static final class Driver {
        final Activity activity;
        final Celine3DView view;
        final FilamentAsset asset;
        final TransformManager transforms;
        final Animator animator;
        final Bone hips;
        final boolean probeModel;
        final boolean supported;

        boolean disabled;
        boolean homeApplied;
        boolean loggedFrame;

        Driver(Activity activity, Celine3DView view) throws Exception {
            this.activity = activity;
            this.view = view;
            asset = (FilamentAsset) field(view, "asset");
            transforms = (TransformManager) field(view, "transformManager");
            animator = asset.getInstance().getAnimator();
            if (animator == null) throw new IllegalStateException("Filament Animator fehlt");

            hips = feminineBone(view, asset, transforms, "hips", "Hips");
            probeModel = asset.getFirstEntityByName("CelineSkinningProbe") != 0;
            supported = hips != null;
            if (!supported && !probeModel) {
                throw new IllegalStateException("Produktions-Rig Hips fehlt");
            }
            if (!supported) {
                Celine3DDiagnostics.record(activity, "V71-101",
                        "Feminine Praesenz auf Probe-Rig kontrolliert uebersprungen",
                        "CelineSkinningProbe ohne Hips · kein Produktionsfehler");
                return;
            }

            Celine3DDiagnostics.record(activity, "V71-100", "Feminine HOME-Huefte gebunden",
                    "Hips-only · v58 Schultern weiter quarantiniert · Root/Arme/Beine/Kopf unangetastet · probe="
                            + probeModel);
        }

        void apply() {
            if (disabled || !supported) return;
            boolean callNow = CelineCallUpperBodyPresenceV55.isCallStage(view);
            if (callNow) {
                if (homeApplied) restore(true);
                return;
            }

            try {
                transforms.openLocalTransformTransaction();
                applyRotation(hips, HOME_HIPS_PITCH, HOME_HIPS_YAW, HOME_HIPS_ROLL);
            } finally {
                transforms.commitLocalTransformTransaction();
            }
            animator.updateBoneMatrices();
            homeApplied = true;

            if (!loggedFrame) {
                loggedFrame = true;
                Celine3DDiagnostics.record(activity, "V71-110", "Feminine HOME-Balance aktiv",
                        "Hips pitch=-1.0° · yaw=-1.6° · roll=2.2° · statisch · keine Schulterproduktion");
            }
        }

        void disableAfterFailure() {
            disabled = true;
            restore(false);
        }

        void restore(boolean logCallHandoff) {
            if (!supported || !homeApplied) return;
            try {
                transforms.openLocalTransformTransaction();
                transforms.setTransform(hips.instance, hips.base);
            } catch (Throwable ignored) {
            } finally {
                try { transforms.commitLocalTransformTransaction(); } catch (Throwable ignored) {}
            }
            try { animator.updateBoneMatrices(); } catch (Throwable ignored) {}
            homeApplied = false;
            loggedFrame = false;
            if (logCallHandoff) {
                Celine3DDiagnostics.record(activity, "V71-120", "CALL Huefte freigegeben",
                        "Hips exakt auf Basis restauriert · v70 uebernimmt sitzende CALL-Pose");
            }
        }

        private void applyRotation(Bone bone, float pitch, float yaw, float roll) {
            float[] delta = new float[16];
            float[] out = new float[16];
            Matrix.setIdentityM(delta, 0);
            if (yaw != 0f) Matrix.rotateM(delta, 0, yaw, 0f, 1f, 0f);
            if (pitch != 0f) Matrix.rotateM(delta, 0, pitch, 1f, 0f, 0f);
            if (roll != 0f) Matrix.rotateM(delta, 0, roll, 0f, 0f, 1f);
            Matrix.multiplyMM(out, 0, bone.base, 0, delta, 0);
            transforms.setTransform(bone.instance, out);
        }
    }

    private static Bone feminineBone(Celine3DView view, FilamentAsset asset,
                                     TransformManager transforms, String v44Field, String entityName) {
        int entity = asset.getFirstEntityByName(entityName);
        if (entity == 0) return null;
        int instance = transforms.getInstance(entity);
        if (instance == 0) return null;
        float[] base = extractV44Base(view, v44Field);
        if (base == null) base = transforms.getTransform(instance, new float[16]);
        return new Bone(instance, base);
    }

    private static float[] extractV44Base(Celine3DView view, String fieldName) {
        try {
            Field statesField = CelineVideoChatV44.class.getDeclaredField("STATES");
            statesField.setAccessible(true);
            Object raw = statesField.get(null);
            if (!(raw instanceof Map)) return null;
            Object motion;
            synchronized (raw) { motion = ((Map<?, ?>) raw).get(view); }
            if (motion == null) return null;
            Field valueField = motion.getClass().getDeclaredField(fieldName);
            valueField.setAccessible(true);
            Object value = valueField.get(motion);
            if (value instanceof float[]) return ((float[]) value).clone();
            if (value != null) {
                Field baseField = value.getClass().getDeclaredField("base");
                baseField.setAccessible(true);
                Object base = baseField.get(value);
                if (base instanceof float[]) return ((float[]) base).clone();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Celine3DView find3D(View view) {
        if (view instanceof Celine3DView) return (Celine3DView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Celine3DView found = find3D(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
}
