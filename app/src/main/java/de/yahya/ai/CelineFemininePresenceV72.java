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
 * v72 performs the first isolated shoulder re-enable inside roadmap order 3.
 *
 * v58 captured shoulders directly from an arbitrary running transform and produced a real-avatar
 * deformation even with sub-degree motion. Blender 4.5.12 previews on the exact canonical skeleton
 * prove this static candidate from front/right/back. Production is stricter: Hips, LeftShoulder and
 * RightShoulder must all resolve from v44's renderer-captured bases or the owner fails closed.
 * CALL restores all three bases and remains owned by v70. Arms, root, legs, head, face, camera and
 * external layout geometry remain untouched here.
 */
final class CelineFemininePresenceV72 {
    private static final float HOME_HIPS_PITCH = -2.0f;
    private static final float HOME_HIPS_YAW = -3.5f;
    private static final float HOME_HIPS_ROLL = 6.0f;
    private static final float HOME_LEFT_SHOULDER_PITCH = -1.2f;
    private static final float HOME_LEFT_SHOULDER_ROLL = -0.7f;
    private static final float HOME_RIGHT_SHOULDER_PITCH = -0.6f;
    private static final float HOME_RIGHT_SHOULDER_ROLL = 0.5f;

    private static final WeakHashMap<Activity, Controller> CONTROLLERS = new WeakHashMap<>();

    private CelineFemininePresenceV72() {}

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
                    Celine3DDiagnostics.error(activity, "V72-199",
                            "Feminine HOME-Praesenz Initialisierung FEHLER", error);
                    driver = null;
                    return;
                }
            }

            try {
                driver.apply();
            } catch (Throwable error) {
                Celine3DDiagnostics.error(activity, "V72-198",
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
        final Bone leftShoulder;
        final Bone rightShoulder;
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
            leftShoulder = feminineBone(view, asset, transforms, "leftShoulder", "LeftShoulder");
            rightShoulder = feminineBone(view, asset, transforms, "rightShoulder", "RightShoulder");
            probeModel = asset.getFirstEntityByName("CelineSkinningProbe") != 0;
            supported = hips != null && leftShoulder != null && rightShoulder != null;
            if (!supported && !probeModel) {
                throw new IllegalStateException(
                        "v44 Basis fuer Hips/LeftShoulder/RightShoulder noch nicht vollstaendig");
            }
            if (!supported) {
                Celine3DDiagnostics.record(activity, "V72-101",
                        "Feminine Praesenz auf Probe-Rig kontrolliert uebersprungen",
                        "Probe ohne vollstaendige v44 Hips/Schulter-Basen · kein Produktionsfehler");
                return;
            }

            Celine3DDiagnostics.record(activity, "V72-100", "Feminine HOME-Basis gebunden",
                    "Hips+LeftShoulder+RightShoulder aus v44 Basis · alter v58 Direktpfad bleibt aus · "
                            + "Root/Arme/Beine/Kopf unangetastet · probe=" + probeModel);
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
                applyRotation(leftShoulder, HOME_LEFT_SHOULDER_PITCH, 0f,
                        HOME_LEFT_SHOULDER_ROLL);
                applyRotation(rightShoulder, HOME_RIGHT_SHOULDER_PITCH, 0f,
                        HOME_RIGHT_SHOULDER_ROLL);
            } finally {
                transforms.commitLocalTransformTransaction();
            }
            animator.updateBoneMatrices();
            homeApplied = true;

            if (!loggedFrame) {
                loggedFrame = true;
                Celine3DDiagnostics.record(activity, "V72-110", "Blender-gepruefte HOME-Balance aktiv",
                        "Hips=-2.0/-3.5/6.0° · LeftShoulder=-1.2/0/-0.7° · "
                                + "RightShoulder=-0.6/0/0.5° · statisch");
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
                transforms.setTransform(leftShoulder.instance, leftShoulder.base);
                transforms.setTransform(rightShoulder.instance, rightShoulder.base);
            } catch (Throwable ignored) {
            } finally {
                try { transforms.commitLocalTransformTransaction(); } catch (Throwable ignored) {}
            }
            try { animator.updateBoneMatrices(); } catch (Throwable ignored) {}
            homeApplied = false;
            loggedFrame = false;
            if (logCallHandoff) {
                Celine3DDiagnostics.record(activity, "V72-120", "CALL Koerperbasis freigegeben",
                        "Hips+beide Schultern exakt restauriert · v70 uebernimmt sitzende CALL-Pose");
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
        if (base == null) return null;
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
