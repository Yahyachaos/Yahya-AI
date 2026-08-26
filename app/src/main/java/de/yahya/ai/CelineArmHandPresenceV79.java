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
 * v79 replaces the visually imperceptible v74 loop while keeping exactly the same six-joint safety
 * boundary. No shoulders, hips, root, legs, face, camera or nonexistent finger bones are touched.
 * HOME and CALL both get slow bounded arm/forearm/wrist life around the proven v69 base poses.
 */
final class CelineArmHandPresenceV79 {
    private static final float HOME_LEFT_ARM_ROLL = 29.5f;
    private static final float HOME_RIGHT_ARM_ROLL = -29.5f;
    private static final float HOME_FOREARM_PITCH = -6.0f;
    private static final float CALL_LEFT_ARM_ROLL = 30.5f;
    private static final float CALL_RIGHT_ARM_ROLL = -30.5f;
    private static final float CALL_FOREARM_PITCH = -14.0f;

    private static final long HOME_LOOP_NANOS = 5_200_000_000L;
    private static final long CALL_LOOP_NANOS = 6_100_000_000L;

    private static final WeakHashMap<Activity, Controller> CONTROLLERS = new WeakHashMap<>();

    private CelineArmHandPresenceV79() {}

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
            if (driver != null) driver.restoreAll();
        }

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
                    Celine3DDiagnostics.error(activity, "V79-429", "Arm/Hand Presence Initialisierung FEHLER", error);
                    driver = null;
                    return;
                }
            }
            try {
                driver.apply(frameTimeNanos);
            } catch (Throwable error) {
                Celine3DDiagnostics.error(activity, "V79-428", "Arm/Hand Presence Frame FEHLER", error);
                driver.disableAfterFailure();
            }
        }
    }

    private static final class Bone {
        final int instance;
        final float[] base;
        Bone(int instance, float[] base) { this.instance = instance; this.base = base; }
    }

    private static final class Driver {
        final Activity activity;
        final Celine3DView view;
        final TransformManager transforms;
        final Animator animator;
        final Bone leftArm;
        final Bone rightArm;
        final Bone leftForeArm;
        final Bone rightForeArm;
        final Bone leftHand;
        final Bone rightHand;
        final boolean supported;
        boolean disabled;
        boolean lastCall;
        boolean loggedHome;
        boolean loggedCall;
        long phaseStart;

        Driver(Activity activity, Celine3DView view) throws Exception {
            this.activity = activity;
            this.view = view;
            FilamentAsset asset = (FilamentAsset) field(view, "asset");
            transforms = (TransformManager) field(view, "transformManager");
            animator = asset.getInstance().getAnimator();
            if (animator == null) throw new IllegalStateException("Filament Animator fehlt");
            leftArm = bone(asset, "LeftArm");
            rightArm = bone(asset, "RightArm");
            leftForeArm = bone(asset, "LeftForeArm");
            rightForeArm = bone(asset, "RightForeArm");
            leftHand = bone(asset, "LeftHand");
            rightHand = bone(asset, "RightHand");
            supported = leftArm != null && rightArm != null && leftForeArm != null && rightForeArm != null
                    && leftHand != null && rightHand != null;
            if (!supported && asset.getFirstEntityByName("CelineSkinningProbe") == 0) {
                throw new IllegalStateException("Produktions-Rig hat nicht alle sechs Arm/Hand-Joints");
            }
            Celine3DDiagnostics.record(activity, "V79-400", "v79 Arm/Hand-Rig gebunden",
                    "sixJoint=" + supported + " · noFingerBones=true · HOME+CALL bounded motion");
        }

        void apply(long frameTimeNanos) {
            if (disabled || !supported) return;
            boolean call = CelineCallUpperBodyPresenceV55.isCallStage(view);
            if (call != lastCall || phaseStart == 0L) phaseStart = frameTimeNanos;
            lastCall = call;

            long duration = call ? CALL_LOOP_NANOS : HOME_LOOP_NANOS;
            long elapsed = Math.max(0L, frameTimeNanos - phaseStart);
            double theta = Math.PI * 2.0 * ((double) (elapsed % duration) / (double) duration);
            float wave = (float) Math.sin(theta);
            float second = (float) Math.sin(theta * 2.0 + 0.6);
            float breath = (float) Math.sin(theta - 0.45);
            float speech = call && avatarState(view) == CelineAvatarController.State.SPEAKING
                    ? speechEnergy(view) : 0f;

            float leftArmPitch;
            float rightArmPitch;
            float leftArmRoll;
            float rightArmRoll;
            float leftForePitch;
            float rightForePitch;
            float leftHandPitch;
            float rightHandPitch;
            float leftHandRoll;
            float rightHandRoll;

            if (call) {
                // Seated: slightly more elbow/wrist life, still below conversational gesticulation.
                leftArmPitch = 0.72f * wave + 0.22f * speech * second;
                rightArmPitch = -0.66f * wave - 0.20f * speech * second;
                leftArmRoll = CALL_LEFT_ARM_ROLL + 0.42f * breath;
                rightArmRoll = CALL_RIGHT_ARM_ROLL - 0.40f * breath;
                leftForePitch = CALL_FOREARM_PITCH + 0.95f * second + 0.35f * speech * wave;
                rightForePitch = CALL_FOREARM_PITCH - 0.88f * second - 0.32f * speech * wave;
                leftHandPitch = 1.45f * wave + 0.45f * speech * second;
                rightHandPitch = -1.35f * wave - 0.42f * speech * second;
                leftHandRoll = 0.70f * second;
                rightHandRoll = -0.65f * second;
            } else {
                // HOME: visible breathing/weight-presence without waving.
                leftArmPitch = 1.18f * wave;
                rightArmPitch = -1.05f * wave;
                leftArmRoll = HOME_LEFT_ARM_ROLL + 0.68f * breath;
                rightArmRoll = HOME_RIGHT_ARM_ROLL - 0.62f * breath;
                leftForePitch = HOME_FOREARM_PITCH + 0.72f * second;
                rightForePitch = HOME_FOREARM_PITCH - 0.68f * second;
                leftHandPitch = 1.85f * wave + 0.32f * second;
                rightHandPitch = -1.72f * wave - 0.28f * second;
                leftHandRoll = 0.82f * second;
                rightHandRoll = -0.76f * second;
            }

            try {
                transforms.openLocalTransformTransaction();
                rotate(leftArm, leftArmPitch, 0f, leftArmRoll);
                rotate(rightArm, rightArmPitch, 0f, rightArmRoll);
                rotate(leftForeArm, leftForePitch, 0f, 0f);
                rotate(rightForeArm, rightForePitch, 0f, 0f);
                rotate(leftHand, leftHandPitch, 0f, leftHandRoll);
                rotate(rightHand, rightHandPitch, 0f, rightHandRoll);
            } finally {
                transforms.commitLocalTransformTransaction();
            }
            animator.updateBoneMatrices();

            if (call && !loggedCall) {
                loggedCall = true;
                Celine3DDiagnostics.record(activity, "V79-420", "CALL Arm/Hand Presence aktiv",
                        "arm<=0.94° forearm<=1.30° hand<=2.1° · speech-bounded · no puppet wave");
            } else if (!call && !loggedHome) {
                loggedHome = true;
                Celine3DDiagnostics.record(activity, "V79-410", "HOME Arm/Hand Presence aktiv",
                        "arm<=1.2° forearm<=0.72° hand<=2.2° · slow seamless loop");
            }
        }

        void disableAfterFailure() { disabled = true; restoreAll(); }

        void restoreAll() {
            if (!supported) return;
            try {
                transforms.openLocalTransformTransaction();
                restore(leftArm); restore(rightArm); restore(leftForeArm); restore(rightForeArm);
                restore(leftHand); restore(rightHand);
            } catch (Throwable ignored) {
            } finally {
                try { transforms.commitLocalTransformTransaction(); } catch (Throwable ignored) {}
            }
            try { animator.updateBoneMatrices(); } catch (Throwable ignored) {}
            phaseStart = 0L;
        }

        private Bone bone(FilamentAsset asset, String name) {
            try {
                int entity = asset.getFirstEntityByName(name);
                if (entity == 0) return null;
                int instance = transforms.getInstance(entity);
                if (instance == 0) return null;
                return new Bone(instance, transforms.getTransform(instance, new float[16]));
            } catch (Throwable ignored) { return null; }
        }

        private void restore(Bone bone) {
            if (bone != null) transforms.setTransform(bone.instance, bone.base);
        }

        private void rotate(Bone bone, float pitch, float yaw, float roll) {
            if (bone == null) return;
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

    private static CelineAvatarController.State avatarState(Celine3DView view) {
        try {
            Object value = field(view, "avatarState");
            if (value instanceof CelineAvatarController.State) return (CelineAvatarController.State) value;
        } catch (Throwable ignored) {}
        return CelineAvatarController.State.IDLE;
    }

    private static float speechEnergy(Celine3DView view) {
        try {
            Object value = field(view, "speechEnergy");
            if (value instanceof Number) return Math.max(0f, Math.min(1f, ((Number) value).floatValue()));
        } catch (Throwable ignored) {}
        return 0f;
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
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
}
