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
 * CALL upper-body owner. v79 keeps neck+Head motion but also gives the already-owned spine chain
 * one small symmetric seated pitch so the upper body belongs to the seated pelvis instead of
 * looking like a standing torso pasted onto bent legs.
 */
final class CelineCallUpperBodyPresenceV55 {
    private static final float CALL_SPINE_PITCH = 1.20f;
    private static final float CALL_SPINE01_PITCH = 1.60f;
    private static final float CALL_SPINE02_PITCH = 1.20f;

    // The reference-room camera already supplies the correct perspective. CALL used to enter with
    // cameraZoom=1.0 while Celine3DView applies CALL_REFERENCE_BASE_ZOOM=0.70, effectively moving
    // the avatar camera 30% closer and cropping Celine's head. Enter CALL at the base zoom instead,
    // which makes normalizedZoom=1.0 and preserves the exact reference-camera distance. This only
    // touches Celine's overlay camera; the independently rendered room camera remains unchanged.
    private static final float CALL_REFERENCE_ENTRY_ZOOM = 0.70f;

    private static final WeakHashMap<Activity, Controller> CONTROLLERS = new WeakHashMap<>();
    private CelineCallUpperBodyPresenceV55() {}

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
                    Celine3DDiagnostics.error(activity, "V55-199", "CALL Oberkörper-Rig Initialisierung FEHLER", e);
                    driver = null;
                    return;
                }
            }
            try {
                driver.apply(frameTimeNanos);
            } catch (Throwable e) {
                Celine3DDiagnostics.error(activity, "V55-198", "CALL Oberkörper-Rig Frame FEHLER", e);
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
        final Bone head;
        final Bone neck;
        final Bone spine;
        final Bone spine01;
        final Bone spine02;
        final boolean probeModel;
        boolean logged;
        boolean disabled;
        boolean wasInCall;
        float preCallCameraZoom = Float.NaN;

        Driver(Activity activity, Celine3DView view) throws Exception {
            this.activity = activity;
            this.view = view;
            asset = (FilamentAsset) field(view, "asset");
            transforms = (TransformManager) field(view, "transformManager");
            animator = asset.getInstance().getAnimator();
            if (animator == null) throw new IllegalStateException("Filament Animator fehlt");
            head = rendererBone(view, asset, transforms, "headBone", "Head");
            neck = rendererBone(view, asset, transforms, "neckBone", "neck");
            spine = rendererBone(view, asset, transforms, "spineBone", "Spine");
            spine01 = rendererBone(view, asset, transforms, "spine01Bone", "Spine01");
            spine02 = rendererBone(view, asset, transforms, "spine02Bone", "Spine02");
            if (head == null || neck == null) throw new IllegalStateException("Head/neck joints fehlen");
            probeModel = asset.getFirstEntityByName("CelineSkinningProbe") != 0;
            Celine3DDiagnostics.record(activity, "V55-100", "CALL Oberkörper-Rig gebunden",
                    "Head+neck=true · spineChain=" + (spine != null && spine01 != null && spine02 != null)
                            + " · seatedPitch=v79 · probe=" + probeModel);
        }

        void apply(long frameTimeNanos) {
            if (disabled) return;
            boolean inCall = isCallStage(view);
            if (!inCall) {
                if (wasInCall) restore();
                wasInCall = false;
                return;
            }
            if (!wasInCall) {
                preCallCameraZoom = cameraZoom(view);
                setCameraZoom(view, CALL_REFERENCE_ENTRY_ZOOM);
                Celine3DDiagnostics.record(activity, "V80-CALL-CAM-110", "CALL Referenzkamera gerahmt",
                        "entryZoom=" + CALL_REFERENCE_ENTRY_ZOOM + " previousZoom=" + preCallCameraZoom
                                + " normalizedZoom=1.0");
            }
            wasInCall = true;

            double t = frameTimeNanos * 1.0e-9;
            float neckPitch, neckYaw, neckRoll;
            float headPitch, headYaw, headRoll;
            if (probeModel) {
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
                float breath = (float) Math.sin(t * 1.28 + 0.4);
                float nod = state == CelineAvatarController.State.SPEAKING
                        ? (float) Math.sin(t * 4.0) * (0.18f + 0.32f * speech) : 0f;
                neckYaw = slow * 0.32f;
                neckPitch = breath * 0.16f;
                neckRoll = -slow * 0.10f;
                headYaw = slow * 0.58f;
                headPitch = breath * 0.24f + nod;
                headRoll = -slow * 0.16f + (state == CelineAvatarController.State.LISTENING ? 0.18f : 0f);
            }

            try {
                transforms.openLocalTransformTransaction();
                if (probeModel) {
                    restore(spine);
                    restore(spine01);
                    restore(spine02);
                } else {
                    applyRotation(spine, CALL_SPINE_PITCH, 0f, 0f);
                    applyRotation(spine01, CALL_SPINE01_PITCH, 0f, 0f);
                    applyRotation(spine02, CALL_SPINE02_PITCH, 0f, 0f);
                }
                applyRotation(neck, neckPitch, neckYaw, neckRoll);
                applyRotation(head, headPitch, headYaw, headRoll);
            } finally {
                transforms.commitLocalTransformTransaction();
            }
            animator.updateBoneMatrices();
            if (!logged) {
                logged = true;
                Celine3DDiagnostics.record(activity, "V55-110", "CALL Oberkörper-Skinning aktiv",
                        "spine=1.2°/1.6°/1.2° seated · neck+Head live · CALL-only · probe=" + probeModel);
            }
        }

        void disableAfterFailure() { disabled = true; restore(); }

        void restore() {
            try {
                transforms.openLocalTransformTransaction();
                restore(spine); restore(spine01); restore(spine02); restore(neck); restore(head);
            } catch (Throwable ignored) {
            } finally {
                try { transforms.commitLocalTransformTransaction(); } catch (Throwable ignored) {}
            }
            try { animator.updateBoneMatrices(); } catch (Throwable ignored) {}
            restoreCameraZoom();
            logged = false;
        }

        private void restoreCameraZoom() {
            if (Float.isNaN(preCallCameraZoom)) return;
            setCameraZoom(view, preCallCameraZoom);
            preCallCameraZoom = Float.NaN;
        }

        private void restore(Bone bone) { if (bone != null) transforms.setTransform(bone.instance, bone.base); }

        private void applyRotation(Bone bone, float pitch, float yaw, float roll) {
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

    static boolean isCallStage(View view) {
        View cur = view;
        while (cur != null) {
            Object tag = cur.getTag();
            if (tag != null && "v45-stage-slot".equals(tag.toString())) return true;
            Object parent = cur.getParent();
            cur = parent instanceof View ? (View) parent : null;
        }
        return false;
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

    private static float cameraZoom(Celine3DView view) {
        try {
            Object value = field(view, "cameraZoom");
            if (value instanceof Number) return ((Number) value).floatValue();
        } catch (Throwable ignored) {}
        return 1.0f;
    }

    private static void setCameraZoom(Celine3DView view, float zoom) {
        try {
            Field f = Celine3DView.class.getDeclaredField("cameraZoom");
            f.setAccessible(true);
            f.setFloat(view, zoom);
        } catch (Throwable e) {
            throw new IllegalStateException("CALL cameraZoom konnte nicht gesetzt werden", e);
        }
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
