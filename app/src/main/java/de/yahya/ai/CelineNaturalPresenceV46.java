package de.yahya.ai;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.Looper;
import android.view.Choreographer;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.google.android.filament.Camera;
import com.google.android.filament.TransformManager;
import com.google.android.filament.gltfio.Animator;
import com.google.android.filament.gltfio.FilamentAsset;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * v46 natural-presence layer.
 *
 * The model in the current Meshy package contains only a one-frame bind-pose clip in the
 * Character_output GLB (the second GLB contains Walking/Running but no seated idle). Therefore
 * the live-call pose is built from the existing body rig and pushed to the skin every frame via
 * Animator.updateBoneMatrices(). This is important: changing TransformManager nodes alone does
 * not update a skinned mesh.
 *
 * Home/room: relaxed arms instead of the A-pose, while v44 may continue its bounded movement.
 * Live call: v44 locomotion is paused, Celine sits in a chair with bent hips/knees, relaxed arms,
 * breathing and state-dependent micro gestures, and the camera uses an upper-body call framing.
 */
final class CelineNaturalPresenceV46 {
    private static final WeakHashMap<Activity, Controller> CONTROLLERS = new WeakHashMap<>();

    private CelineNaturalPresenceV46() {}

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
        boolean callMode;
        PoseDriver driver;
        Celine3DView boundView;

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
            if (driver != null) {
                try { driver.leaveCall(true); } catch (Throwable ignored) {}
            }
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
                    if (driver != null) driver.leaveCall(true);
                    boundView = view;
                    driver = new PoseDriver(activity, decor, view);
                } catch (Throwable e) {
                    Celine3DDiagnostics.error(activity, "V46-199", "Natural-Presence Initialisierung FEHLER", e);
                    driver = null;
                    return;
                }
            }

            boolean callNow = findTagged(decor, "v45-stage-slot") != null;
            try {
                if (callNow && !callMode) {
                    callMode = true;
                    driver.enterCall();
                } else if (!callNow && callMode) {
                    callMode = false;
                    driver.leaveCall(false);
                }

                if (callMode) driver.applySeated(frameTimeNanos);
                else driver.applyRelaxedStanding(frameTimeNanos);
            } catch (Throwable e) {
                Celine3DDiagnostics.error(activity, "V46-198", "Natural-Presence Frame FEHLER", e);
            }
        }
    }

    private static final class Bone {
        final int instance;
        final float[] base;
        Bone(int instance, float[] base) { this.instance = instance; this.base = base; }
    }

    private static final class PoseDriver {
        final Activity activity;
        final View decor;
        final Celine3DView view;
        final FilamentAsset asset;
        final TransformManager transforms;
        final Camera camera;
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
        final Bone leftShoulder;
        final Bone rightShoulder;
        final Bone leftArm;
        final Bone rightArm;
        final Bone leftForeArm;
        final Bone rightForeArm;
        final Bone leftHand;
        final Bone rightHand;

        boolean seated;
        boolean v44WasStopped;
        long startNanos;
        SeatView seatView;

        PoseDriver(Activity activity, View decor, Celine3DView view) throws Exception {
            this.activity = activity;
            this.decor = decor;
            this.view = view;
            asset = (FilamentAsset) getField(view, "asset");
            transforms = (TransformManager) getField(view, "transformManager");
            camera = (Camera) getField(view, "camera");
            animator = asset.getInstance().getAnimator();

            rootInstance = transforms.getInstance(asset.getRoot());
            if (rootInstance == 0) throw new IllegalStateException("Celine root transform fehlt");
            float[] v44Root = extractV44RootBase(view);
            rootBase = v44Root != null ? v44Root : transforms.getTransform(rootInstance, new float[16]);

            hips = bone("Hips");
            leftUpLeg = bone("LeftUpLeg");
            rightUpLeg = bone("RightUpLeg");
            leftLeg = bone("LeftLeg");
            rightLeg = bone("RightLeg");
            leftFoot = bone("LeftFoot");
            rightFoot = bone("RightFoot");
            leftShoulder = bone("LeftShoulder");
            rightShoulder = bone("RightShoulder");
            leftArm = bone("LeftArm");
            rightArm = bone("RightArm");
            leftForeArm = bone("LeftForeArm");
            rightForeArm = bone("RightForeArm");
            leftHand = bone("LeftHand");
            rightHand = bone("RightHand");

            int animationCount = animator.getAnimationCount();
            StringBuilder clips = new StringBuilder();
            for (int i = 0; i < animationCount; i++) {
                if (i > 0) clips.append(", ");
                String name = animator.getAnimationName(i);
                clips.append(name == null || name.trim().isEmpty() ? ("clip" + i) : name)
                        .append("=")
                        .append(String.format(Locale.US, "%.3fs", animator.getAnimationDuration(i)));
            }
            Celine3DDiagnostics.record(activity, "V46-100", "Rig/Animationen auditiert",
                    "animations=" + animationCount + " [" + clips + "] · seatedProcedural=true · skinMatrices=Animator.updateBoneMatrices");
        }

        void enterCall() {
            if (seated) return;
            seated = true;
            startNanos = 0L;
            v44WasStopped = stopV44Motion(view);
            installSeat();
            Celine3DDiagnostics.record(activity, "V46-110", "Sitzender Videochat aktiviert",
                    "hips=-70° · knees=90° · feet=-15° · arms=relaxed · v44LocomotionStopped=" + v44WasStopped);
        }

        void leaveCall(boolean destroying) {
            if (!seated && !destroying) return;
            seated = false;
            removeSeat();
            restoreBody();
            try { animator.updateBoneMatrices(); } catch (Throwable ignored) {}
            if (!destroying) {
                mainPost(() -> CelineVideoChatV44.ensure(activity, decor), 80L);
                Celine3DDiagnostics.record(activity, "V46-120", "Sitzender Videochat beendet",
                        "Root/Beine restauriert · v44 Raumbewegung wird wieder gestartet");
            }
        }

        void applyRelaxedStanding(long frameTimeNanos) {
            double t = frameTimeNanos * 1.0e-9;
            float sway = (float) Math.sin(t * 0.55);
            float breath = (float) Math.sin(t * 1.30);
            try {
                transforms.openLocalTransformTransaction();
                apply(leftShoulder, 0f, 0f, 1.5f + 0.5f * sway);
                apply(rightShoulder, 0f, 0f, -1.5f - 0.5f * sway);
                apply(leftArm, 0.4f * breath, 0f, 29.5f + 0.7f * sway);
                apply(rightArm, -0.4f * breath, 0f, -29.5f - 0.7f * sway);
                apply(leftForeArm, -6.0f + 0.5f * breath, 0f, 0f);
                apply(rightForeArm, -6.0f - 0.5f * breath, 0f, 0f);
            } finally {
                transforms.commitLocalTransformTransaction();
            }
            animator.updateBoneMatrices();
        }

        void applySeated(long frameTimeNanos) {
            if (!seated) return;
            if (startNanos == 0L) startNanos = frameTimeNanos;
            double t = (frameTimeNanos - startNanos) * 1.0e-9;

            float breath = (float) Math.sin(t * 1.32);
            float slow = (float) Math.sin(t * 0.46);
            float micro = (float) Math.sin(t * 0.83 + 0.9);
            CelineAvatarController.State state = readAvatarState(view);
            float speaking = state == CelineAvatarController.State.SPEAKING ? 1f : 0f;
            float listening = state == CelineAvatarController.State.LISTENING ? 1f : 0f;
            float thinking = state == CelineAvatarController.State.THINKING ? 1f : 0f;

            float gesture = speaking * (float) Math.sin(t * 3.15);
            float asymmetric = speaking * (float) Math.sin(t * 1.72 + 0.5);
            float listenEase = listening * 1.1f;
            float thinkStill = thinking * 0.45f;

            try {
                transforms.openLocalTransformTransaction();
                applyRoot(0f, -0.16f + 0.006f * breath, 0.08f, 0.35f * slow);

                apply(hips, -1.0f + 0.5f * breath, 0.45f * slow, 0.35f * slow);
                apply(leftUpLeg, -70.0f + 0.8f * slow, -1.0f, 1.2f);
                apply(rightUpLeg, -70.0f - 0.8f * slow, 1.0f, -1.2f);
                apply(leftLeg, 90.0f - 0.7f * slow, 0f, 0f);
                apply(rightLeg, 90.0f + 0.7f * slow, 0f, 0f);
                apply(leftFoot, -15.0f + 0.4f * micro, 0f, 0f);
                apply(rightFoot, -15.0f - 0.4f * micro, 0f, 0f);

                apply(leftShoulder, 0.2f * breath, 0f, 2.4f + 0.5f * slow);
                apply(rightShoulder, -0.2f * breath, 0f, -2.4f - 0.5f * slow);
                apply(leftArm, -1.0f + 1.7f * gesture, -1.2f * asymmetric,
                        30.5f + 0.6f * slow + listenEase);
                apply(rightArm, -1.0f - 1.4f * gesture, 1.0f * asymmetric,
                        -30.5f - 0.6f * slow - listenEase);
                apply(leftForeArm, -14.0f - 4.0f * gesture + thinkStill, 0.8f * asymmetric, 1.2f);
                apply(rightForeArm, -14.0f + 3.6f * gesture + thinkStill, -0.8f * asymmetric, -1.2f);
                apply(leftHand, 0.8f * gesture, 0f, 2.0f + 0.8f * micro);
                apply(rightHand, -0.8f * gesture, 0f, -2.0f - 0.8f * micro);
            } finally {
                transforms.commitLocalTransformTransaction();
            }

            animator.updateBoneMatrices();

            int w = Math.max(1, view.getWidth());
            int h = Math.max(1, view.getHeight());
            camera.setLensProjection(58.0, (double) w / (double) h, 0.05, 1000.0);
            camera.lookAt(
                    0.02 + 0.018 * slow, 0.62 + 0.012 * breath, 0.78,
                    0.00 + 0.025 * slow, 0.34 + 0.008 * breath, -3.92,
                    0.0, 1.0, 0.0);
        }

        private Bone bone(String name) {
            int entity = asset.getFirstEntityByName(name);
            if (entity == 0) return null;
            int instance = transforms.getInstance(entity);
            if (instance == 0) return null;
            return new Bone(instance, transforms.getTransform(instance, new float[16]));
        }

        private void applyRoot(float x, float y, float z, float yawDeg) {
            float[] localRotation = new float[16];
            float[] rotated = new float[16];
            float[] worldMove = new float[16];
            float[] out = new float[16];
            Matrix.setIdentityM(localRotation, 0);
            Matrix.rotateM(localRotation, 0, yawDeg, 0f, 1f, 0f);
            Matrix.multiplyMM(rotated, 0, rootBase, 0, localRotation, 0);
            Matrix.setIdentityM(worldMove, 0);
            Matrix.translateM(worldMove, 0, x, y, z);
            Matrix.multiplyMM(out, 0, worldMove, 0, rotated, 0);
            transforms.setTransform(rootInstance, out);
        }

        private void apply(Bone bone, float pitch, float yaw, float roll) {
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

        private void restoreBody() {
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
                restore(leftShoulder);
                restore(rightShoulder);
                restore(leftArm);
                restore(rightArm);
                restore(leftForeArm);
                restore(rightForeArm);
                restore(leftHand);
                restore(rightHand);
            } finally {
                transforms.commitLocalTransformTransaction();
            }
        }

        private void restore(Bone bone) {
            if (bone != null) transforms.setTransform(bone.instance, bone.base);
        }

        private void installSeat() {
            if (!(view.getParent() instanceof FrameLayout)) return;
            FrameLayout stage = (FrameLayout) view.getParent();
            for (int i = 0; i < stage.getChildCount(); i++) {
                if (stage.getChildAt(i) instanceof SeatView) {
                    seatView = (SeatView) stage.getChildAt(i);
                    seatView.setVisibility(View.VISIBLE);
                    return;
                }
            }
            seatView = new SeatView(activity);
            int index = Math.max(0, stage.indexOfChild(view));
            stage.addView(seatView, index, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        private void removeSeat() {
            if (seatView != null) {
                seatView.setVisibility(View.GONE);
                if (seatView.getParent() instanceof ViewGroup) {
                    ((ViewGroup) seatView.getParent()).removeView(seatView);
                }
                seatView = null;
            }
        }

        private static float[] extractV44RootBase(Celine3DView view) {
            try {
                Field statesField = CelineVideoChatV44.class.getDeclaredField("STATES");
                statesField.setAccessible(true);
                Object raw = statesField.get(null);
                if (!(raw instanceof Map)) return null;
                Object motion;
                synchronized (raw) { motion = ((Map<?, ?>) raw).get(view); }
                if (motion == null) return null;
                Field f = motion.getClass().getDeclaredField("rootBase");
                f.setAccessible(true);
                Object value = f.get(motion);
                if (value instanceof float[]) return ((float[]) value).clone();
            } catch (Throwable ignored) {}
            return null;
        }

        private static boolean stopV44Motion(Celine3DView view) {
            try {
                Field statesField = CelineVideoChatV44.class.getDeclaredField("STATES");
                statesField.setAccessible(true);
                Object raw = statesField.get(null);
                if (!(raw instanceof Map)) return false;
                Object motion;
                synchronized (raw) { motion = ((Map<?, ?>) raw).get(view); }
                if (motion == null) return false;
                Field running = motion.getClass().getDeclaredField("running");
                running.setAccessible(true);
                running.setBoolean(motion, false);
                return true;
            } catch (Throwable ignored) { return false; }
        }

        private void mainPost(Runnable r, long delay) {
            new Handler(Looper.getMainLooper()).postDelayed(r, delay);
        }
    }

    private static final class SeatView extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        SeatView(Activity activity) {
            super(activity);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float cx = w * 0.50f;
            paint.setColor(Color.rgb(47, 43, 51));
            canvas.drawRoundRect(new RectF(cx - w * 0.18f, h * 0.43f,
                    cx + w * 0.18f, h * 0.72f), 30f, 30f, paint);
            paint.setColor(Color.rgb(58, 52, 61));
            canvas.drawRoundRect(new RectF(cx - w * 0.21f, h * 0.64f,
                    cx + w * 0.21f, h * 0.74f), 26f, 26f, paint);
            paint.setColor(Color.rgb(37, 34, 40));
            canvas.drawRoundRect(new RectF(cx - w * 0.18f, h * 0.72f,
                    cx - w * 0.145f, h * 0.92f), 12f, 12f, paint);
            canvas.drawRoundRect(new RectF(cx + w * 0.145f, h * 0.72f,
                    cx + w * 0.18f, h * 0.92f), 12f, 12f, paint);
        }
    }

    private static CelineAvatarController.State readAvatarState(Celine3DView view) {
        try {
            Field f = Celine3DView.class.getDeclaredField("avatarState");
            f.setAccessible(true);
            Object state = f.get(view);
            if (state instanceof CelineAvatarController.State) return (CelineAvatarController.State) state;
        } catch (Throwable ignored) {}
        return CelineAvatarController.State.IDLE;
    }

    private static Object getField(Object target, String name) throws Exception {
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

    private static View findTagged(View root, String tag) {
        if (tag.equals(root.getTag())) return root;
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                View found = findTagged(g.getChildAt(i), tag);
                if (found != null) return found;
            }
        }
        return null;
    }
}
