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
 * v70 supplies only the missing seated CALL lower-body foundation.
 *
 * The previously deforming v58 shoulder path remains quarantined. This owner writes the asset
 * root plus Hips, both upper legs, both lower legs and both feet only while CALL is active.
 * v55 continues to own neck+Head and v69 continues to own the four arm joints. HOME is never
 * continuously written; after CALL, every v70 transform is restored once and v44 resumes.
 */
final class CelineSeatedCallV70 {
    private static final float ROOT_DOWN = -0.30f;
    private static final float ROOT_FORWARD = 0.12f;
    private static final float HIPS_PITCH = -5.0f;
    private static final float UPPER_LEG_PITCH = -88.0f;
    private static final float LOWER_LEG_PITCH = 92.0f;
    private static final float FOOT_PITCH = -8.0f;

    private static final WeakHashMap<Activity, Controller> CONTROLLERS = new WeakHashMap<>();

    private CelineSeatedCallV70() {}

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
            if (driver != null) driver.restoreForHome(false);
        }

        void destroy() {
            running = false;
            paused = true;
            choreographer.removeFrameCallback(this);
            if (driver != null) driver.restoreForHome(false);
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
                    if (driver != null) driver.restoreForHome(false);
                    boundView = view;
                    driver = new Driver(activity, decor, view);
                } catch (Throwable error) {
                    Celine3DDiagnostics.error(activity, "V70-199",
                            "Sitzende CALL-Pose Initialisierung FEHLER", error);
                    driver = null;
                    return;
                }
            }

            try {
                driver.apply();
            } catch (Throwable error) {
                Celine3DDiagnostics.error(activity, "V70-198",
                        "Sitzende CALL-Pose Frame FEHLER", error);
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
        final View decor;
        final Celine3DView view;
        final FilamentAsset asset;
        final TransformManager transforms;
        final Animator animator;
        final int rootInstance;
        final float[] rootBase;
        final Bone hips;
        final Bone leftUpLeg;
        final Bone rightUpLeg;
        final Bone leftLeg;
        final Bone rightLeg;
        final Bone leftFoot;
        final Bone rightFoot;
        final boolean probeModel;
        final boolean supported;

        boolean disabled;
        boolean inCall;
        boolean loggedFrame;

        Driver(Activity activity, View decor, Celine3DView view) throws Exception {
            this.activity = activity;
            this.decor = decor;
            this.view = view;
            asset = (FilamentAsset) field(view, "asset");
            transforms = (TransformManager) field(view, "transformManager");
            animator = asset.getInstance().getAnimator();
            if (animator == null) throw new IllegalStateException("Filament Animator fehlt");

            rootInstance = transforms.getInstance(asset.getRoot());
            if (rootInstance == 0) throw new IllegalStateException("Celine Root-Transform fehlt");
            float[] capturedRoot = extractV44Base(view, "rootBase");
            rootBase = capturedRoot != null
                    ? capturedRoot
                    : transforms.getTransform(rootInstance, new float[16]);

            hips = seatedBone(view, asset, transforms, "hips", "Hips");
            leftUpLeg = seatedBone(view, asset, transforms, "leftUpLeg", "LeftUpLeg");
            rightUpLeg = seatedBone(view, asset, transforms, "rightUpLeg", "RightUpLeg");
            leftLeg = seatedBone(view, asset, transforms, "leftLeg", "LeftLeg");
            rightLeg = seatedBone(view, asset, transforms, "rightLeg", "RightLeg");
            leftFoot = seatedBone(view, asset, transforms, "leftFoot", "LeftFoot");
            rightFoot = seatedBone(view, asset, transforms, "rightFoot", "RightFoot");

            probeModel = asset.getFirstEntityByName("CelineSkinningProbe") != 0;
            supported = hips != null && leftUpLeg != null && rightUpLeg != null
                    && leftLeg != null && rightLeg != null
                    && leftFoot != null && rightFoot != null;
            if (!supported && !probeModel) {
                throw new IllegalStateException("Produktions-Rig hat nicht alle sieben Sitz-Joints");
            }
            if (!supported) {
                Celine3DDiagnostics.record(activity, "V70-101",
                        "Sitzpose auf synthetischem Probe-Rig kontrolliert übersprungen",
                        "CelineSkinningProbe hat nicht alle sieben Sitz-Joints · kein Produktionsfehler");
                return;
            }

            Celine3DDiagnostics.record(activity, "V70-100", "Sitzendes Unterkörper-Rig gebunden",
                    "Root + Hips + Left/RightUpLeg + Left/RightLeg + Left/RightFoot · "
                            + "Schultern/Arme/Kopf unangetastet · probe=" + probeModel);
        }

        void apply() {
            if (disabled || !supported) return;
            boolean callNow = CelineCallUpperBodyPresenceV55.isCallStage(view);
            if (!callNow) {
                if (inCall) restoreForHome(true);
                return;
            }

            if (!inCall) {
                inCall = true;
                loggedFrame = false;
                Celine3DDiagnostics.record(activity, "V70-110", "Sitzender CALL aktiviert",
                        "Unterkörper CALL-only · v55 Kopf + v69 Arme bleiben getrennte Besitzer");
            }

            try {
                transforms.openLocalTransformTransaction();
                applyRoot(ROOT_DOWN, ROOT_FORWARD);
                applyRotation(hips, HIPS_PITCH, 0f, 0f);
                applyRotation(leftUpLeg, UPPER_LEG_PITCH, -6.0f, 1.5f);
                applyRotation(rightUpLeg, UPPER_LEG_PITCH, 6.0f, -1.5f);
                applyRotation(leftLeg, LOWER_LEG_PITCH, 0f, 0f);
                applyRotation(rightLeg, LOWER_LEG_PITCH, 0f, 0f);
                applyRotation(leftFoot, FOOT_PITCH, 0f, 0f);
                applyRotation(rightFoot, FOOT_PITCH, 0f, 0f);
            } finally {
                transforms.commitLocalTransformTransaction();
            }
            animator.updateBoneMatrices();

            if (!loggedFrame) {
                loggedFrame = true;
                Celine3DDiagnostics.record(activity, "V70-120", "Sitzende CALL-Matrizen aktiv",
                        "hips=-5° · upperLeg=-88° · knees=92° · feet=-8° · rootDown=-0.30 · baseline CALL camera unchanged");
            }
        }

        void disableAfterFailure() {
            disabled = true;
            restoreForHome(false);
        }

        void restoreForHome(boolean logReturn) {
            if (!supported) return;
            if (!inCall && !logReturn) return;
            try {
                transforms.openLocalTransformTransaction();
                transforms.setTransform(rootInstance, rootBase);
                restore(hips);
                restore(leftUpLeg);
                restore(rightUpLeg);
                restore(leftLeg);
                restore(rightLeg);
                restore(leftFoot);
                restore(rightFoot);
            } catch (Throwable ignored) {
            } finally {
                try { transforms.commitLocalTransformTransaction(); } catch (Throwable ignored) {}
            }
            try { animator.updateBoneMatrices(); } catch (Throwable ignored) {}
            inCall = false;
            loggedFrame = false;
            if (logReturn) {
                Celine3DDiagnostics.record(activity, "V70-130", "HOME Unterkörper wiederhergestellt",
                        "Root/Hüfte/Beine/Füße auf exakte HOME-Basen zurückgesetzt");
            }
        }

        private void applyRoot(float down, float forward) {
            float[] worldMove = new float[16];
            float[] out = new float[16];
            Matrix.setIdentityM(worldMove, 0);
            Matrix.translateM(worldMove, 0, 0f, down, forward);
            Matrix.multiplyMM(out, 0, worldMove, 0, rootBase, 0);
            transforms.setTransform(rootInstance, out);
        }

        private void restore(Bone bone) {
            if (bone != null) transforms.setTransform(bone.instance, bone.base);
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

    private static Bone seatedBone(Celine3DView view, FilamentAsset asset,
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
