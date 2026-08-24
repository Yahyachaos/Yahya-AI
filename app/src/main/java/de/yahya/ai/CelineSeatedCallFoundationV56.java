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
 * Proven CALL skinning owner, extended incrementally through v58.
 *
 * v58 adds only LeftShoulder + RightShoulder to the already proven Hips + neck + Head owner.
 * Shoulder motion is symmetric, sub-degree and CALL-only. No arm, root, leg or v46/v48 pose path
 * is restored. Spine nodes remain pinned to renderer-captured bases.
 */
final class CelineSeatedCallFoundationV56 {
    private static final WeakHashMap<Activity, Controller> CONTROLLERS = new WeakHashMap<>();
    private CelineSeatedCallFoundationV56() {}

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

    private static final class Controller implements Choreographer.FrameCallback {
        final Activity activity;
        final View decor;
        final Choreographer choreographer = Choreographer.getInstance();
        boolean running;
        boolean paused;
        Celine3DView boundView;
        Driver driver;

        Controller(Activity activity, View decor) { this.activity = activity; this.decor = decor; }

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
            if (driver != null) driver.restore();
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
                    if (driver != null) driver.restore();
                    boundView = view;
                    driver = new Driver(activity, view);
                } catch (Throwable e) {
                    Celine3DDiagnostics.error(activity, "V58-199", "CALL Schulterpraesenz Initialisierung FEHLER", e);
                    driver = null;
                    return;
                }
            }
            try {
                driver.apply(frameTimeNanos);
            } catch (Throwable e) {
                Celine3DDiagnostics.error(activity, "V58-198", "CALL Schulterpraesenz Frame FEHLER", e);
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
        final FilamentAsset asset;
        final TransformManager transforms;
        final Animator animator;
        final Bone hips;
        final Bone leftShoulder;
        final Bone rightShoulder;
        final Bone head;
        final Bone neck;
        final Bone spine;
        final Bone spine01;
        final Bone spine02;
        final boolean probeModel;
        boolean logged;
        boolean disabled;
        boolean wasInCall;

        Driver(Activity activity, Celine3DView view) throws Exception {
            this.activity = activity;
            this.view = view;
            asset = (FilamentAsset) field(view, "asset");
            transforms = (TransformManager) field(view, "transformManager");
            animator = asset.getInstance().getAnimator();
            if (animator == null) throw new IllegalStateException("Filament Animator fehlt");
            hips = rendererBone(view, asset, transforms, "hipsBone", "Hips");
            leftShoulder = assetBone(asset, transforms, "LeftShoulder");
            rightShoulder = assetBone(asset, transforms, "RightShoulder");
            head = rendererBone(view, asset, transforms, "headBone", "Head");
            neck = rendererBone(view, asset, transforms, "neckBone", "neck");
            spine = rendererBone(view, asset, transforms, "spineBone", "Spine");
            spine01 = rendererBone(view, asset, transforms, "spine01Bone", "Spine01");
            spine02 = rendererBone(view, asset, transforms, "spine02Bone", "Spine02");
            if (hips == null || leftShoulder == null || rightShoulder == null || head == null || neck == null) {
                throw new IllegalStateException("Hips/LeftShoulder/RightShoulder/neck/Head joints fehlen");
            }
            probeModel = asset.getFirstEntityByName("CelineSkinningProbe") != 0;
            Celine3DDiagnostics.record(activity, "V56-100", "CALL Sitzbasis gebunden",
                    "Hips=true · neck=true · Head=true · probe=" + probeModel + " · Spine bleibt Basis");
            Celine3DDiagnostics.record(activity, "V57-100", "CALL Blickpraesenz gebunden",
                    "v56 owner · state-aware neck+Head · probe=" + probeModel);
            Celine3DDiagnostics.record(activity, "V58-100", "CALL Schulterpraesenz gebunden",
                    "LeftShoulder=true · RightShoulder=true · same skinning owner · probe=" + probeModel);
        }

        void apply(long frameTimeNanos) {
            if (disabled) return;
            boolean inCall = CelineCallUpperBodyPresenceV55.isCallStage(view);
            if (!inCall) {
                if (wasInCall) restore();
                wasInCall = false;
                return;
            }
            wasInCall = true;

            double t = frameTimeNanos * 1.0e-9;
            float hipsPitch, hipsYaw, hipsRoll;
            float leftShoulderPitch, leftShoulderYaw, leftShoulderRoll;
            float rightShoulderPitch, rightShoulderYaw, rightShoulderRoll;
            float neckPitch, neckYaw, neckRoll;
            float headPitch, headYaw, headRoll;
            if (probeModel) {
                hipsPitch = -18.0f + (float) Math.sin(t * Math.PI * 0.5) * 7.0f;
                hipsYaw = (float) Math.sin(t * Math.PI * 0.75) * 8.0f;
                hipsRoll = 0f;
                float shoulderProbe = (float) Math.sin(t * Math.PI * 0.8) * 13.0f;
                leftShoulderPitch = shoulderProbe;
                leftShoulderYaw = 0f;
                leftShoulderRoll = (float) Math.cos(t * Math.PI * 0.65) * 9.0f;
                rightShoulderPitch = -shoulderProbe;
                rightShoulderYaw = 0f;
                rightShoulderRoll = (float) Math.cos(t * Math.PI * 0.65) * -9.0f;
                neckYaw = (float) Math.sin(t * Math.PI) * 11.0f;
                neckPitch = (float) Math.cos(t * Math.PI * 0.5) * 4.0f;
                neckRoll = (float) Math.sin(t * Math.PI * 0.5 + 0.7) * 2.0f;
                headYaw = (float) Math.sin(t * Math.PI + 1.0) * -16.0f;
                headPitch = (float) Math.cos(t * Math.PI + 0.5) * 6.0f;
                headRoll = (float) Math.sin(t * Math.PI * 0.75) * 3.0f;
            } else {
                CelineAvatarController.State state = avatarState(view);
                float speech = speechEnergy(view);
                float slow = (float) Math.sin(t * 0.48);
                float glance = (float) Math.sin(t * 0.23 + 1.7);
                float breath = (float) Math.sin(t * 1.28 + 0.4);
                float motionScale = 1.0f;
                float intentYaw = 0f;
                float intentPitch = 0f;
                float intentRoll = 0f;
                float nod = 0f;
                float shoulderBreath = 0.12f;
                float shoulderSpeech = 0f;

                switch (state) {
                    case LISTENING:
                        motionScale = 0.52f;
                        intentPitch = -0.10f;
                        intentRoll = 0.18f;
                        shoulderBreath = 0.08f;
                        break;
                    case THINKING:
                        motionScale = 0.72f;
                        intentYaw = glance * 0.34f;
                        intentPitch = -0.05f;
                        shoulderBreath = 0.10f;
                        break;
                    case SPEAKING:
                        motionScale = 0.92f + 0.18f * speech;
                        intentYaw = (float) Math.sin(t * 1.05) * 0.10f * speech;
                        nod = (float) Math.sin(t * 4.0) * (0.18f + 0.32f * speech);
                        shoulderBreath = 0.14f;
                        shoulderSpeech = (float) Math.sin(t * 2.1 + 0.6) * 0.10f * speech;
                        break;
                    case IDLE:
                    default:
                        break;
                }

                hipsPitch = -4.0f + breath * 0.20f;
                hipsYaw = slow * 0.18f;
                hipsRoll = slow * 0.08f;

                // v58 shoulder presence: symmetric breathing/voice response only. The largest
                // production rotation remains below half a degree and never touches arms.
                float shoulderLift = breath * shoulderBreath + shoulderSpeech;
                leftShoulderPitch = shoulderLift;
                leftShoulderYaw = 0f;
                leftShoulderRoll = -0.10f + slow * 0.05f * motionScale;
                rightShoulderPitch = shoulderLift;
                rightShoulderYaw = 0f;
                rightShoulderRoll = 0.10f - slow * 0.05f * motionScale;

                neckYaw = slow * 0.32f * motionScale + intentYaw * 0.35f;
                neckPitch = breath * 0.16f * motionScale + intentPitch * 0.35f;
                neckRoll = -slow * 0.10f * motionScale + intentRoll * 0.25f;
                headYaw = slow * 0.58f * motionScale + intentYaw;
                headPitch = breath * 0.24f * motionScale + intentPitch + nod;
                headRoll = -slow * 0.16f * motionScale + intentRoll;
            }

            try {
                transforms.openLocalTransformTransaction();
                applyRotation(hips, hipsPitch, hipsYaw, hipsRoll);
                restore(spine);
                restore(spine01);
                restore(spine02);
                applyRotation(leftShoulder, leftShoulderPitch, leftShoulderYaw, leftShoulderRoll);
                applyRotation(rightShoulder, rightShoulderPitch, rightShoulderYaw, rightShoulderRoll);
                applyRotation(neck, neckPitch, neckYaw, neckRoll);
                applyRotation(head, headPitch, headYaw, headRoll);
            } finally {
                transforms.commitLocalTransformTransaction();
            }
            animator.updateBoneMatrices();
            if (!logged) {
                logged = true;
                Celine3DDiagnostics.record(activity, "V56-110", "CALL Sitzbasis Skinning aktiv",
                        "Animator.updateBoneMatrices OK · Hips+neck+Head · CALL-only · probe=" + probeModel);
                Celine3DDiagnostics.record(activity, "V57-120", "CALL Blickpraesenz aktiv",
                        "state-aware neck+Head refinement · same owner · probe=" + probeModel);
                Celine3DDiagnostics.record(activity, "V58-120", "CALL Schulterpraesenz aktiv",
                        "Hips+LeftShoulder+RightShoulder+neck+Head · one owner · probe=" + probeModel);
            }
        }

        void disableAfterFailure() { disabled = true; restore(); }

        void restore() {
            try {
                transforms.openLocalTransformTransaction();
                restore(hips); restore(spine); restore(spine01); restore(spine02);
                restore(leftShoulder); restore(rightShoulder); restore(neck); restore(head);
            } catch (Throwable ignored) {
            } finally {
                try { transforms.commitLocalTransformTransaction(); } catch (Throwable ignored) {}
            }
            try { animator.updateBoneMatrices(); } catch (Throwable ignored) {}
        }

        private void restore(Bone bone) { if (bone != null) transforms.setTransform(bone.instance, bone.base); }

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

    private static Bone rendererBone(Celine3DView view, FilamentAsset asset, TransformManager transforms,
                                     String fieldName, String entityName) {
        try {
            Field f = Celine3DView.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            Object pose = f.get(view);
            if (pose != null) {
                Field instanceField = pose.getClass().getDeclaredField("instance");
                Field baseField = pose.getClass().getDeclaredField("base");
                instanceField.setAccessible(true); baseField.setAccessible(true);
                int instance = instanceField.getInt(pose);
                Object base = baseField.get(pose);
                if (instance != 0 && base instanceof float[]) return new Bone(instance, ((float[]) base).clone());
            }
        } catch (Throwable ignored) {}
        return assetBone(asset, transforms, entityName);
    }

    private static Bone assetBone(FilamentAsset asset, TransformManager transforms, String entityName) {
        try {
            int entity = asset.getFirstEntityByName(entityName);
            if (entity == 0) return null;
            int instance = transforms.getInstance(entity);
            if (instance == 0) return null;
            return new Bone(instance, transforms.getTransform(instance, new float[16]));
        } catch (Throwable ignored) { return null; }
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
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
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
