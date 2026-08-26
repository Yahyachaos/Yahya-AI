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
 * v73 adds one restrained, seamless HOME breathing/weight-shift loop inside roadmap order 4.
 *
 * Blender 4.5.12 front/right/back renders on the exact canonical skeleton proved the four-second
 * loop and an exact frame-1/frame-97 seam. Production keeps v72's strict safety boundary: Hips,
 * LeftShoulder and RightShoulder must all resolve from v44 renderer-captured bases or the owner
 * fails closed. CALL restores all three bases and remains owned by v70. Arms, root, spine, legs,
 * head, face, camera and external layout geometry remain untouched here.
 */
final class CelineNaturalBodyMotionV73 {
    private static final float HOME_HIPS_PITCH = -2.0f;
    private static final float HOME_HIPS_YAW = -3.5f;
    private static final float HOME_HIPS_ROLL = 6.0f;
    private static final float HOME_LEFT_SHOULDER_PITCH = -1.2f;
    private static final float HOME_LEFT_SHOULDER_ROLL = -0.7f;
    private static final float HOME_RIGHT_SHOULDER_PITCH = -0.6f;
    private static final float HOME_RIGHT_SHOULDER_ROLL = 0.5f;

    private static final long LOOP_DURATION_NANOS = 4_000_000_000L;
    private static final float HIPS_PITCH_AMPLITUDE = 0.10f;
    private static final float HIPS_YAW_AMPLITUDE = 0.12f;
    private static final float HIPS_ROLL_AMPLITUDE = 0.18f;
    private static final float LEFT_SHOULDER_PITCH_WAVE = -0.10f;
    private static final float LEFT_SHOULDER_PITCH_SECOND = -0.03f;
    private static final float LEFT_SHOULDER_ROLL_AMPLITUDE = -0.05f;
    private static final float RIGHT_SHOULDER_PITCH_WAVE = -0.08f;
    private static final float RIGHT_SHOULDER_PITCH_SECOND = 0.02f;
    private static final float RIGHT_SHOULDER_ROLL_AMPLITUDE = 0.04f;

    private static final WeakHashMap<Activity, Controller> CONTROLLERS = new WeakHashMap<>();

    private CelineNaturalBodyMotionV73() {}

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
                    Celine3DDiagnostics.error(activity, "V73-199",
                            "Natuerliche HOME-Bewegung Initialisierung FEHLER", error);
                    driver = null;
                    return;
                }
            }

            try {
                driver.apply(frameTimeNanos);
            } catch (Throwable error) {
                Celine3DDiagnostics.error(activity, "V73-198",
                        "Natuerliche HOME-Bewegung Frame FEHLER", error);
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
        long loopStartNanos;

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
                Celine3DDiagnostics.record(activity, "V73-101",
                        "Natuerliche Bewegung auf Probe-Rig kontrolliert uebersprungen",
                        "Probe ohne vollstaendige v44 Hips/Schulter-Basen · kein Produktionsfehler");
                return;
            }

            Celine3DDiagnostics.record(activity, "V73-100", "Natuerliche HOME-Basis gebunden",
                    "Hips+LeftShoulder+RightShoulder aus v44 Basis · alter v58 Direktpfad bleibt aus · "
                            + "Root/Arme/Beine/Kopf unangetastet · probe=" + probeModel);
        }

        void apply(long frameTimeNanos) {
            if (disabled || !supported) return;
            boolean callNow = CelineCallUpperBodyPresenceV55.isCallStage(view);
            if (callNow) {
                if (homeApplied) restore(true);
                return;
            }

            if (!homeApplied || loopStartNanos == 0L) loopStartNanos = frameTimeNanos;
            long elapsed = Math.max(0L, frameTimeNanos - loopStartNanos);
            double theta = Math.PI * 2.0
                    * ((double) (elapsed % LOOP_DURATION_NANOS) / (double) LOOP_DURATION_NANOS);
            float wave = (float) Math.sin(theta);
            float second = (float) Math.sin(theta * 2.0);

            float hipsPitch = HOME_HIPS_PITCH + HIPS_PITCH_AMPLITUDE * second;
            float hipsYaw = HOME_HIPS_YAW + HIPS_YAW_AMPLITUDE * wave;
            float hipsRoll = HOME_HIPS_ROLL + HIPS_ROLL_AMPLITUDE * wave;
            float leftPitch = HOME_LEFT_SHOULDER_PITCH
                    + LEFT_SHOULDER_PITCH_WAVE * wave
                    + LEFT_SHOULDER_PITCH_SECOND * second;
            float leftRoll = HOME_LEFT_SHOULDER_ROLL
                    + LEFT_SHOULDER_ROLL_AMPLITUDE * wave;
            float rightPitch = HOME_RIGHT_SHOULDER_PITCH
                    + RIGHT_SHOULDER_PITCH_WAVE * wave
                    + RIGHT_SHOULDER_PITCH_SECOND * second;
            float rightRoll = HOME_RIGHT_SHOULDER_ROLL
                    + RIGHT_SHOULDER_ROLL_AMPLITUDE * wave;

            try {
                transforms.openLocalTransformTransaction();
                applyRotation(hips, hipsPitch, hipsYaw, hipsRoll);
                applyRotation(leftShoulder, leftPitch, 0f, leftRoll);
                applyRotation(rightShoulder, rightPitch, 0f, rightRoll);
            } finally {
                transforms.commitLocalTransformTransaction();
            }
            animator.updateBoneMatrices();
            homeApplied = true;

            if (!loggedFrame) {
                loggedFrame = true;
                Celine3DDiagnostics.record(activity, "V73-110",
                        "Blender-gepruefte HOME-Koerperbewegung aktiv",
                        "4.0 s Loop · Hips max=0.10/0.12/0.18° · Schultern max=0.13/0.05°"
                                + " · v72 Basen · nahtlos");
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
            loopStartNanos = 0L;
            if (logCallHandoff) {
                Celine3DDiagnostics.record(activity, "V73-120", "CALL Koerperbewegung freigegeben",
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
