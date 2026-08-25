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
import java.util.WeakHashMap;

/**
 * v69 removes the visible A-pose through one deliberately narrow skinning owner.
 *
 * Only LeftArm, RightArm, LeftForeArm and RightForeArm may be written. The quarantined
 * shoulder/Hips/root/leg path remains untouched, as do the face, camera and all layout geometry.
 * Every frame starts from the captured production bind transforms and pushes the four resulting
 * matrices to the skin. Any runtime failure restores those exact bases and disables this owner.
 */
final class CelineArmPoseV69 {
    private static final float HOME_LEFT_ARM_ROLL = 29.5f;
    private static final float HOME_RIGHT_ARM_ROLL = -29.5f;
    private static final float HOME_FOREARM_PITCH = -6.0f;
    private static final float CALL_LEFT_ARM_ROLL = 30.5f;
    private static final float CALL_RIGHT_ARM_ROLL = -30.5f;
    private static final float CALL_FOREARM_PITCH = -14.0f;

    private static final WeakHashMap<Activity, Controller> CONTROLLERS = new WeakHashMap<>();

    private CelineArmPoseV69() {}

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

        void pause() { paused = true; }

        void destroy() {
            running = false;
            paused = true;
            choreographer.removeFrameCallback(this);
            if (driver != null) driver.restoreAll();
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
                    if (driver != null) driver.restoreAll();
                    boundView = view;
                    driver = new Driver(activity, view);
                } catch (Throwable error) {
                    Celine3DDiagnostics.error(activity, "V69-199",
                            "Arm-Pose Initialisierung FEHLER", error);
                    driver = null;
                    return;
                }
            }

            try {
                driver.apply();
            } catch (Throwable error) {
                Celine3DDiagnostics.error(activity, "V69-198",
                        "Arm-Pose Frame FEHLER", error);
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
        final FilamentAsset asset;
        final TransformManager transforms;
        final Animator animator;
        final Bone leftArm;
        final Bone rightArm;
        final Bone leftForeArm;
        final Bone rightForeArm;
        final boolean probeModel;
        final boolean supported;

        boolean disabled;
        boolean seenHome;
        boolean lastCall;

        Driver(Activity activity, Celine3DView view) throws Exception {
            this.activity = activity;
            asset = (FilamentAsset) field(view, "asset");
            transforms = (TransformManager) field(view, "transformManager");
            animator = asset.getInstance().getAnimator();
            if (animator == null) throw new IllegalStateException("Filament Animator fehlt");

            probeModel = asset.getFirstEntityByName("CelineSkinningProbe") != 0;
            leftArm = armBone(asset, transforms, "LeftArm");
            rightArm = armBone(asset, transforms, "RightArm");
            leftForeArm = armBone(asset, transforms, "LeftForeArm");
            rightForeArm = armBone(asset, transforms, "RightForeArm");
            supported = leftArm != null && rightArm != null
                    && leftForeArm != null && rightForeArm != null;

            if (!supported && !probeModel) {
                throw new IllegalStateException("Produktions-Rig hat nicht alle vier Arm-Joints");
            }
            if (!supported) {
                Celine3DDiagnostics.record(activity, "V69-101",
                        "Arm-Pose auf synthetischem Probe-Rig kontrolliert übersprungen",
                        "CelineSkinningProbe hat nicht alle vier Arm-Joints · kein Produktionsfehler");
                return;
            }

            Celine3DDiagnostics.record(activity, "V69-100", "Vier-Joint Arm-Rig gebunden",
                    "LeftArm + RightArm + LeftForeArm + RightForeArm · "
                            + "Schultern/Hüfte/Root/Beine unverändert · probe=" + probeModel);
        }

        void apply() {
            if (disabled || !supported) return;
            boolean inCall = CelineCallUpperBodyPresenceV55.isCallStage(findView());
            float leftRoll = inCall ? CALL_LEFT_ARM_ROLL : HOME_LEFT_ARM_ROLL;
            float rightRoll = inCall ? CALL_RIGHT_ARM_ROLL : HOME_RIGHT_ARM_ROLL;
            float forearmPitch = inCall ? CALL_FOREARM_PITCH : HOME_FOREARM_PITCH;

            try {
                transforms.openLocalTransformTransaction();
                applyRotation(leftArm, 0f, 0f, leftRoll);
                applyRotation(rightArm, 0f, 0f, rightRoll);
                applyRotation(leftForeArm, forearmPitch, 0f, 0f);
                applyRotation(rightForeArm, forearmPitch, 0f, 0f);
            } finally {
                transforms.commitLocalTransformTransaction();
            }
            animator.updateBoneMatrices();

            if (inCall && !lastCall) {
                Celine3DDiagnostics.record(activity, "V69-120", "CALL Arm-Pose aktiv",
                        "Arme entspannt abgesenkt · Unterarme leicht angewinkelt · nur vier Arm-Joints");
            } else if (!inCall && !seenHome) {
                seenHome = true;
                Celine3DDiagnostics.record(activity, "V69-110", "HOME A-Pose entfernt",
                        "Arme körpernah abgesenkt · Schultern/Hüfte/Root/Beine unangetastet");
            } else if (!inCall && lastCall) {
                Celine3DDiagnostics.record(activity, "V69-130", "HOME Arm-Pose wiederhergestellt",
                        "CALL → HOME sauber · identische HOME Vier-Joint-Basis");
            }
            lastCall = inCall;
        }

        private View findView() {
            Object root = activity.getWindow().getDecorView();
            Celine3DView view = root instanceof View ? find3D((View) root) : null;
            if (view == null) throw new IllegalStateException("Celine3DView nicht mehr gebunden");
            return view;
        }

        void disableAfterFailure() {
            disabled = true;
            restoreAll();
        }

        void restoreAll() {
            if (!supported) return;
            try {
                transforms.openLocalTransformTransaction();
                restore(leftArm);
                restore(rightArm);
                restore(leftForeArm);
                restore(rightForeArm);
            } catch (Throwable ignored) {
            } finally {
                try { transforms.commitLocalTransformTransaction(); } catch (Throwable ignored) {}
            }
            try { animator.updateBoneMatrices(); } catch (Throwable ignored) {}
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

    private static Bone armBone(FilamentAsset asset, TransformManager transforms, String name) {
        try {
            int entity = asset.getFirstEntityByName(name);
            if (entity == 0) return null;
            int instance = transforms.getInstance(entity);
            if (instance == 0) return null;
            return new Bone(instance, transforms.getTransform(instance, new float[16]));
        } catch (Throwable ignored) {
            return null;
        }
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
