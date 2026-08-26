package de.yahya.ai;

import android.app.Activity;
import android.opengl.Matrix;
import android.view.Choreographer;

import com.google.android.filament.TransformManager;
import com.google.android.filament.gltfio.Animator;
import com.google.android.filament.gltfio.FilamentAsset;

import java.lang.reflect.Field;

/**
 * Lab-only deterministic pose driver for the exact branch-live Celine rig.
 * It never installs in HOME/CALL and restores every captured transform when stopped.
 */
final class CelineAvatarLabPoseDriverV79 implements Choreographer.FrameCallback {
    enum Mode { LIVE, STAND, WEIGHT_LEFT, WEIGHT_RIGHT, SEATED, BEND, WALK, ARMS }

    private static final class Bone {
        final int instance;
        final float[] base;
        Bone(int instance, float[] base) { this.instance = instance; this.base = base; }
    }

    private final Celine3DView view;
    private final Choreographer choreographer = Choreographer.getInstance();
    private final FilamentAsset asset;
    private final TransformManager transforms;
    private final Animator animator;
    private final int rootInstance;
    private final float[] rootBase;

    private final Bone hips;
    private final Bone spine;
    private final Bone spine01;
    private final Bone spine02;
    private final Bone neck;
    private final Bone head;
    private final Bone leftShoulder;
    private final Bone rightShoulder;
    private final Bone leftArm;
    private final Bone rightArm;
    private final Bone leftForeArm;
    private final Bone rightForeArm;
    private final Bone leftHand;
    private final Bone rightHand;
    private final Bone leftUpLeg;
    private final Bone rightUpLeg;
    private final Bone leftLeg;
    private final Bone rightLeg;
    private final Bone leftFoot;
    private final Bone rightFoot;

    private volatile Mode mode = Mode.LIVE;
    private volatile boolean headOverride;
    private volatile float headPitch;
    private volatile float headYaw;
    private volatile float headRoll;
    private boolean running;
    private long modeStartNanos;

    CelineAvatarLabPoseDriverV79(Celine3DView view) throws Exception {
        this.view = view;
        if (!(view.getContext() instanceof Activity)) {
            throw new IllegalStateException("Avatar Lab benötigt Activity-Kontext für Produktions-Rig-Skalierung");
        }
        // Celine3DView first sees Meshy's tiny pre-skinning bounds. HOME/CALL correct that known
        // 0.01 Armature case through v61. Apply the exact same guarded correction synchronously
        // before the Lab snapshots root/bone baselines, otherwise the skinned body appears ~100x
        // oversized and every deterministic camera view is meaningless.
        CelineMeshyRigScaleV61.repairImmediate((Activity) view.getContext(), view);

        asset = (FilamentAsset) field(view, "asset");
        transforms = (TransformManager) field(view, "transformManager");
        animator = asset.getInstance().getAnimator();
        if (animator == null) throw new IllegalStateException("Filament Animator fehlt");
        rootInstance = transforms.getInstance(asset.getRoot());
        if (rootInstance == 0) throw new IllegalStateException("Celine Root-Transform fehlt");
        rootBase = transforms.getTransform(rootInstance, new float[16]);

        hips = bone("Hips");
        spine = bone("Spine");
        spine01 = bone("Spine01");
        spine02 = bone("Spine02");
        neck = bone("neck", "Neck");
        head = bone("Head");
        leftShoulder = bone("LeftShoulder");
        rightShoulder = bone("RightShoulder");
        leftArm = bone("LeftArm");
        rightArm = bone("RightArm");
        leftForeArm = bone("LeftForeArm");
        rightForeArm = bone("RightForeArm");
        leftHand = bone("LeftHand");
        rightHand = bone("RightHand");
        leftUpLeg = bone("LeftUpLeg");
        rightUpLeg = bone("RightUpLeg");
        leftLeg = bone("LeftLeg");
        rightLeg = bone("RightLeg");
        leftFoot = bone("LeftFoot");
        rightFoot = bone("RightFoot");

        Celine3DDiagnostics.record(view.getContext(), "V79-100", "Avatar Lab Pose-Rig gebunden",
                capabilitySummary());
    }

    String capabilitySummary() {
        int upper = count(hips, spine, spine01, spine02, neck, head,
                leftShoulder, rightShoulder, leftArm, rightArm, leftForeArm, rightForeArm,
                leftHand, rightHand);
        int lower = count(leftUpLeg, rightUpLeg, leftLeg, rightLeg, leftFoot, rightFoot);
        return "upper=" + upper + "/14 lower=" + lower + "/6 root=true";
    }

    void start() {
        if (running) return;
        running = true;
        modeStartNanos = System.nanoTime();
        choreographer.postFrameCallback(this);
    }

    void stop() {
        running = false;
        choreographer.removeFrameCallback(this);
        restoreAll();
    }

    void setMode(Mode next) {
        mode = next == null ? Mode.LIVE : next;
        modeStartNanos = System.nanoTime();
        if (mode == Mode.LIVE && !headOverride) restoreAll();
    }

    Mode getMode() { return mode; }

    void setHead(float pitch, float yaw, float roll) {
        headOverride = true;
        headPitch = clamp(pitch, -24f, 24f);
        headYaw = clamp(yaw, -38f, 38f);
        headRoll = clamp(roll, -22f, 22f);
    }

    void clearHead() {
        headOverride = false;
        headPitch = headYaw = headRoll = 0f;
    }

    @Override public void doFrame(long frameTimeNanos) {
        if (!running) return;
        choreographer.postFrameCallback(this);
        try {
            apply(frameTimeNanos);
        } catch (Throwable error) {
            Celine3DDiagnostics.error(view.getContext(), "V79-199", "Avatar Lab Pose-Frame FEHLER", error);
            running = false;
            choreographer.removeFrameCallback(this);
            restoreAll();
        }
    }

    private void apply(long now) {
        Mode current = mode;
        if (current == Mode.LIVE && !headOverride) return;

        double t = Math.max(0L, now - modeStartNanos) / 1_000_000_000.0;
        float wave = (float) Math.sin(t * Math.PI * 1.4);
        float walk = (float) Math.sin(t * Math.PI * 2.0);
        float walkAbs = Math.abs(walk);

        try {
            transforms.openLocalTransformTransaction();
            restoreControlledNoTransaction();

            switch (current) {
                case STAND:
                    applyStandingBase();
                    break;
                case WEIGHT_LEFT:
                    applyStandingBase();
                    rotate(hips, -1.8f, -2.0f, 4.5f);
                    rotate(leftShoulder, -1.0f, 0f, -1.7f);
                    rotate(rightShoulder, -0.5f, 0f, 1.3f);
                    break;
                case WEIGHT_RIGHT:
                    applyStandingBase();
                    rotate(hips, -1.8f, 2.0f, -4.5f);
                    rotate(leftShoulder, -0.5f, 0f, -1.3f);
                    rotate(rightShoulder, -1.0f, 0f, 1.7f);
                    break;
                case SEATED:
                    translateRoot(0f, -0.30f, 0.12f);
                    rotate(hips, -5.0f, 0f, 0f);
                    rotate(leftUpLeg, -88f, -2f, 0.5f);
                    rotate(rightUpLeg, -88f, 2f, -0.5f);
                    rotate(leftLeg, 92f, 0f, 0f);
                    rotate(rightLeg, 92f, 0f, 0f);
                    rotate(leftFoot, -8f, 0f, 0f);
                    rotate(rightFoot, -8f, 0f, 0f);
                    rotate(leftArm, 0f, 0f, 30.5f);
                    rotate(rightArm, 0f, 0f, -30.5f);
                    rotate(leftForeArm, -14f, 0f, 0f);
                    rotate(rightForeArm, -14f, 0f, 0f);
                    rotate(spine, 1.2f, 0f, 0f);
                    rotate(spine01, 1.6f, 0f, 0f);
                    rotate(spine02, 1.2f, 0f, 0f);
                    break;
                case BEND:
                    applyStandingArms();
                    rotate(hips, -13f, 0f, 0f);
                    rotate(spine, 4.0f, 0f, 0f);
                    rotate(spine01, 7.0f, 0f, 0f);
                    rotate(spine02, 9.0f, 0f, 0f);
                    rotate(neck, 2.0f, 0f, 0f);
                    rotate(head, 4.0f, 0f, 0f);
                    break;
                case WALK:
                    translateRoot(0f, 0.025f * walkAbs, 0f);
                    rotate(hips, -1.5f, 2.0f * walk, 2.0f * walk);
                    rotate(leftUpLeg, 24f * walk, 0f, 0f);
                    rotate(rightUpLeg, -24f * walk, 0f, 0f);
                    rotate(leftLeg, Math.max(0f, -walk) * 38f, 0f, 0f);
                    rotate(rightLeg, Math.max(0f, walk) * 38f, 0f, 0f);
                    rotate(leftFoot, -5f * walk, 0f, 0f);
                    rotate(rightFoot, 5f * walk, 0f, 0f);
                    rotate(leftArm, -7f * walk, 0f, 29.5f);
                    rotate(rightArm, 7f * walk, 0f, -29.5f);
                    rotate(leftForeArm, -8f, 0f, 0f);
                    rotate(rightForeArm, -8f, 0f, 0f);
                    rotate(spine02, 0f, -2.5f * walk, 0f);
                    break;
                case ARMS:
                    rotate(leftArm, 3.2f * wave, 0f, 29.5f + 0.8f * wave);
                    rotate(rightArm, -3.0f * wave, 0f, -29.5f - 0.8f * wave);
                    rotate(leftForeArm, -10f + 3.0f * wave, 0f, 0f);
                    rotate(rightForeArm, -10f - 3.0f * wave, 0f, 0f);
                    rotate(leftHand, 4.0f * wave, 0f, 2.0f * wave);
                    rotate(rightHand, -4.0f * wave, 0f, -2.0f * wave);
                    break;
                case LIVE:
                default:
                    break;
            }

            if (headOverride) {
                rotate(neck, headPitch * 0.35f, headYaw * 0.35f, headRoll * 0.25f);
                rotate(head, headPitch, headYaw, headRoll);
            }
        } finally {
            transforms.commitLocalTransformTransaction();
        }
        animator.updateBoneMatrices();
    }

    private void applyStandingBase() {
        rotate(hips, -2.0f, -3.5f, 6.0f);
        rotate(leftShoulder, -1.2f, 0f, -0.7f);
        rotate(rightShoulder, -0.6f, 0f, 0.5f);
        applyStandingArms();
    }

    private void applyStandingArms() {
        rotate(leftArm, 0f, 0f, 29.5f);
        rotate(rightArm, 0f, 0f, -29.5f);
        rotate(leftForeArm, -6f, 0f, 0f);
        rotate(rightForeArm, -6f, 0f, 0f);
    }

    private void restoreControlledNoTransaction() {
        transforms.setTransform(rootInstance, rootBase);
        restore(hips); restore(spine); restore(spine01); restore(spine02); restore(neck); restore(head);
        restore(leftShoulder); restore(rightShoulder);
        restore(leftArm); restore(rightArm); restore(leftForeArm); restore(rightForeArm);
        restore(leftHand); restore(rightHand);
        restore(leftUpLeg); restore(rightUpLeg); restore(leftLeg); restore(rightLeg);
        restore(leftFoot); restore(rightFoot);
    }

    private void restoreAll() {
        try {
            transforms.openLocalTransformTransaction();
            restoreControlledNoTransaction();
        } catch (Throwable ignored) {
        } finally {
            try { transforms.commitLocalTransformTransaction(); } catch (Throwable ignored) {}
        }
        try { animator.updateBoneMatrices(); } catch (Throwable ignored) {}
    }

    private void translateRoot(float x, float y, float z) {
        float[] move = new float[16];
        float[] out = new float[16];
        Matrix.setIdentityM(move, 0);
        Matrix.translateM(move, 0, x, y, z);
        Matrix.multiplyMM(out, 0, move, 0, rootBase, 0);
        transforms.setTransform(rootInstance, out);
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

    private void restore(Bone bone) {
        if (bone != null) transforms.setTransform(bone.instance, bone.base);
    }

    private Bone bone(String... names) {
        for (String name : names) {
            try {
                int entity = asset.getFirstEntityByName(name);
                if (entity == 0) continue;
                int instance = transforms.getInstance(entity);
                if (instance == 0) continue;
                return new Bone(instance, transforms.getTransform(instance, new float[16]));
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static int count(Bone... bones) {
        int count = 0;
        for (Bone bone : bones) if (bone != null) count++;
        return count;
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return 0f;
        return Math.max(min, Math.min(max, value));
    }
}
