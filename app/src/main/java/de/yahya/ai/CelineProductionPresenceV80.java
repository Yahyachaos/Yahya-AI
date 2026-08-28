package de.yahya.ai;

import android.app.Activity;
import android.content.Context;
import android.opengl.Matrix;
import android.view.View;

import com.google.android.filament.TransformManager;
import com.google.android.filament.gltfio.Animator;
import com.google.android.filament.gltfio.FilamentAsset;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * v80 production owner for Celine's root, body, head and facial animation layers.
 *
 * Block 4 established the single writer. Block 5 keeps that ownership and makes the existing
 * v73/v74/v79 body/arm/hand foundations visibly useful in the actual HOME/CALL product: calm,
 * asynchronous six-joint arm/forearm/wrist life plus small upper-body settling. The canonical rig
 * has no proven finger bones, so fingers remain deliberately untouched.
 *
 * One renderer-frame call composes every accepted procedural transform from immutable rig bases,
 * commits one local-transform transaction, updates skin matrices once, and then advances the
 * guarded v76 face planner (blink/expression/gaze/PCM viseme). HOME, CALL and Avatar Lab production
 * modes all enter through this class. Older versioned owners remain as historical rollback code but
 * are no longer installed in the production lifecycle.
 */
final class CelineProductionPresenceV80 {
    enum Stage { AUTO, HOME, CALL }
    enum LayerView { COMBINED, BASE_ONLY, BREATHING_POSTURE, CONVERSATION, GAZE_HEAD }

    private static final int LAYER_BASE = 1;
    private static final int LAYER_POSTURE = 1 << 1;
    private static final int LAYER_CONVERSATION = 1 << 2;
    private static final int LAYER_GAZE = 1 << 3;
    private static final int LAYER_ALL = LAYER_BASE | LAYER_POSTURE | LAYER_CONVERSATION | LAYER_GAZE;

    private static final int HIPS = 0;
    private static final int SPINE = 1;
    private static final int SPINE01 = 2;
    private static final int SPINE02 = 3;
    private static final int NECK = 4;
    private static final int HEAD = 5;
    private static final int LEFT_SHOULDER = 6;
    private static final int RIGHT_SHOULDER = 7;
    private static final int LEFT_ARM = 8;
    private static final int RIGHT_ARM = 9;
    private static final int LEFT_FOREARM = 10;
    private static final int RIGHT_FOREARM = 11;
    private static final int LEFT_HAND = 12;
    private static final int RIGHT_HAND = 13;
    private static final int LEFT_UP_LEG = 14;
    private static final int RIGHT_UP_LEG = 15;
    private static final int LEFT_LEG = 16;
    private static final int RIGHT_LEG = 17;
    private static final int LEFT_FOOT = 18;
    private static final int RIGHT_FOOT = 19;
    private static final int BONE_COUNT = 20;

    private static final String[] BONE_NAMES = {
            "Hips", "Spine", "Spine01", "Spine02", "neck", "Head",
            "LeftShoulder", "RightShoulder", "LeftArm", "RightArm",
            "LeftForeArm", "RightForeArm", "LeftHand", "RightHand",
            "LeftUpLeg", "RightUpLeg", "LeftLeg", "RightLeg", "LeftFoot", "RightFoot"
    };

    private static final float CALL_ROOT_DOWN = -0.30f;
    private static final float CALL_ROOT_FORWARD = 0.12f;
    private static final long HOME_ARM_LOOP_NANOS = 5_200_000_000L;
    private static final long CALL_ARM_LOOP_NANOS = 6_100_000_000L;

    private static final WeakHashMap<Celine3DView, Mixer> MIXERS = new WeakHashMap<>();

    static final class HomeFrame {
        float x;
        float bob;
        float z;
        float yaw;
        float gait;
    }

    private static final class Bone {
        final int instance;
        final float[] base;

        Bone(int instance, float[] base) {
            this.instance = instance;
            this.base = base;
        }
    }

    private CelineProductionPresenceV80() {}

    static void install(Activity activity, View decor) {
        if (activity == null || decor == null) return;
        Celine3DView view = find3D(decor);
        if (view == null) return;
        mixerFor(view);
    }

    static void onFrame(Celine3DView view, long frameTimeNanos) {
        if (view == null) return;
        Mixer mixer = mixerFor(view);
        if (mixer != null) mixer.applyBody(frameTimeNanos);
        // Facial output is the final layer and retains the guarded v76 rollback plus v77 PCM cue.
        CelineMorphRuntimeV62.onFrame(view, frameTimeNanos);
    }

    static void setDiagnostic(Celine3DView view, Stage stage, LayerView layers) {
        Mixer mixer = mixerFor(view);
        if (mixer == null) return;
        mixer.diagnosticDisabled = false;
        mixer.stage = stage == null || stage == Stage.AUTO ? Stage.HOME : stage;
        mixer.layerMask = maskFor(layers);
        Celine3DDiagnostics.record(view.getContext(), "V80-440", "Avatar Lab Production-Owner gesetzt",
                "stage=" + mixer.stage + " layers=" + (layers == null ? LayerView.COMBINED : layers)
                        + " owner=CelineProductionPresenceV80");
    }

    static void clearDiagnostic(Celine3DView view) {
        Mixer mixer = mixerFor(view);
        if (mixer == null) return;
        mixer.diagnosticDisabled = false;
        mixer.stage = Stage.AUTO;
        mixer.layerMask = LAYER_ALL;
    }

    static void disableForDiagnosticPose(Celine3DView view) {
        Mixer mixer = mixerFor(view);
        if (mixer == null) return;
        mixer.diagnosticDisabled = true;
        mixer.restoreBases();
    }

    static HomeFrame homeFrame(Celine3DView view) {
        Mixer mixer;
        synchronized (MIXERS) { mixer = MIXERS.get(view); }
        return mixer == null ? new HomeFrame() : mixer.homeFrame;
    }

    static void onDestroyed(Activity activity) {
        if (activity == null) return;
        synchronized (MIXERS) {
            Iterator<Map.Entry<Celine3DView, Mixer>> it = MIXERS.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Celine3DView, Mixer> entry = it.next();
                Celine3DView view = entry.getKey();
                if (view == null || view.getContext() == activity) it.remove();
            }
        }
    }

    private static Mixer mixerFor(Celine3DView view) {
        synchronized (MIXERS) {
            Mixer mixer = MIXERS.get(view);
            if (mixer != null) return mixer;
            try {
                mixer = new Mixer(view);
                MIXERS.put(view, mixer);
                return mixer;
            } catch (Throwable error) {
                Celine3DDiagnostics.error(view.getContext(), "V80-499",
                        "Central Production Presence Initialisierung FEHLER", error);
                return null;
            }
        }
    }

    private static int maskFor(LayerView view) {
        if (view == null || view == LayerView.COMBINED) return LAYER_ALL;
        switch (view) {
            case BASE_ONLY: return LAYER_BASE;
            case BREATHING_POSTURE: return LAYER_BASE | LAYER_POSTURE;
            case CONVERSATION: return LAYER_BASE | LAYER_CONVERSATION;
            case GAZE_HEAD: return LAYER_BASE | LAYER_GAZE;
            case COMBINED:
            default: return LAYER_ALL;
        }
    }

    private static final class Mixer {
        final Celine3DView view;
        final TransformManager transforms;
        final Animator animator;
        final int rootInstance;
        final float[] rootBase;
        final boolean probeModel;
        final Bone[] bones = new Bone[BONE_COUNT];
        final float[] angles = new float[BONE_COUNT * 3];
        final HomeFrame homeFrame = new HomeFrame();

        Stage stage = Stage.AUTO;
        int layerMask = LAYER_ALL;
        boolean diagnosticDisabled;
        boolean loggedHome;
        boolean loggedCall;
        boolean loggedBlock5Home;
        boolean loggedBlock5Call;
        boolean targetCall;
        boolean targetInitialized;
        float callBlend;
        long lastFrameNanos;
        long motionStartNanos;
        long armPhaseStartNanos;

        Mixer(Celine3DView view) throws Exception {
            this.view = view;
            Context context = view.getContext();
            if (context instanceof Activity) {
                // Reuse the exact protected v61 correction before immutable root/bone bases are read.
                CelineMeshyRigScaleV61.repairImmediate((Activity) context, view);
            }
            FilamentAsset asset = (FilamentAsset) field(view, "asset");
            transforms = (TransformManager) field(view, "transformManager");
            animator = asset.getInstance().getAnimator();
            if (animator == null) throw new IllegalStateException("Filament Animator fehlt");
            rootInstance = transforms.getInstance(asset.getRoot());
            if (rootInstance == 0) throw new IllegalStateException("Celine Root-Transform fehlt");
            rootBase = transforms.getTransform(rootInstance, new float[16]);
            probeModel = asset.getFirstEntityByName("CelineSkinningProbe") != 0;
            int resolved = 0;
            for (int i = 0; i < BONE_NAMES.length; i++) {
                bones[i] = bone(asset, BONE_NAMES[i]);
                if (bones[i] != null) resolved++;
            }
            Celine3DDiagnostics.record(context, "V80-400", "Central Production Presence gebunden",
                    "owner=CelineProductionPresenceV80 bones=" + resolved + "/" + BONE_COUNT
                            + " root=scene/seat base"
                            + " order=base>posture>conversation>gaze>face"
                            + " face=CelineMorphRuntimeV62 PCM=v77"
                            + " block5SixJointArms=true fingerBones=false"
                            + " probe=" + probeModel);
        }

        void applyBody(long frameTimeNanos) {
            if (diagnosticDisabled) return;
            boolean callNow = stage == Stage.CALL || (stage == Stage.AUTO && isCallStage(view));
            if (!targetInitialized) {
                targetInitialized = true;
                targetCall = callNow;
                callBlend = callNow ? 1.0f : 0.0f;
                armPhaseStartNanos = frameTimeNanos;
            } else if (targetCall != callNow) {
                targetCall = callNow;
                armPhaseStartNanos = frameTimeNanos;
                Celine3DDiagnostics.record(view.getContext(), "V80-430",
                        "Production Presence Übergang gestartet",
                        "target=" + (callNow ? "CALL" : "HOME") + " eased=true snap=false");
            }

            if (motionStartNanos == 0L) motionStartNanos = frameTimeNanos;
            float deltaSeconds = lastFrameNanos == 0L ? 0.0f
                    : Math.min(0.10f, Math.max(0.0f, (frameTimeNanos - lastFrameNanos) * 1.0e-9f));
            lastFrameNanos = frameTimeNanos;
            float targetBlend = callNow ? 1.0f : 0.0f;
            float ease = Math.min(1.0f, deltaSeconds * 4.5f);
            callBlend += (targetBlend - callBlend) * ease;
            if (Math.abs(callBlend - targetBlend) < 0.001f) callBlend = targetBlend;

            double t = Math.max(0L, frameTimeNanos - motionStartNanos) * 1.0e-9;
            updateHomeFrame(t);
            Arrays.fill(angles, 0.0f);

            float home = 1.0f - callBlend;
            float call = callBlend;
            if ((layerMask & LAYER_BASE) != 0) applyBaseLayer(home, call);
            if ((layerMask & LAYER_POSTURE) != 0) applyPostureLayer(t, home, call);
            if ((layerMask & LAYER_CONVERSATION) != 0) {
                applyConversationLayer(frameTimeNanos, home, call);
            }
            if ((layerMask & LAYER_GAZE) != 0) applyGazeLayer(t, home, call);

            try {
                transforms.openLocalTransformTransaction();
                applyRoot(home, call);
                for (int i = 0; i < bones.length; i++) applyBone(i);
            } finally {
                transforms.commitLocalTransformTransaction();
            }
            animator.updateBoneMatrices();

            if (callNow && !loggedCall) {
                loggedCall = true;
                Celine3DDiagnostics.record(view.getContext(), "V80-420", "Central CALL Presence aktiv",
                        "root/seat+posture+conversation+gaze+face · oneTransaction=true oneSkinUpdate=true");
            } else if (!callNow && !loggedHome) {
                loggedHome = true;
                Celine3DDiagnostics.record(view.getContext(), "V80-410", "Central HOME Presence aktiv",
                        "root/world+posture+conversation+gaze+face · oneTransaction=true oneSkinUpdate=true");
            }
        }

        private void updateHomeFrame(double t) {
            float x = 0.30f * (float) Math.sin(t * 0.20);
            float z = 0.16f * (float) Math.sin(t * 0.13 + 1.1);
            float dx = 0.30f * 0.20f * (float) Math.cos(t * 0.20);
            float dz = 0.16f * 0.13f * (float) Math.cos(t * 0.13 + 1.1);
            float speed = (float) Math.sqrt(dx * dx + dz * dz);
            float walkAmount = clamp(speed / 0.052f, 0.0f, 1.0f);
            float gait = (float) Math.sin(t * 2.65) * walkAmount;
            homeFrame.x = x;
            homeFrame.z = z;
            homeFrame.gait = gait;
            homeFrame.bob = Math.abs((float) Math.sin(t * 2.65)) * 0.018f * walkAmount;
            homeFrame.yaw = clamp(dx * 42.0f, -3.0f, 3.0f);
        }

        private void applyBaseLayer(float home, float call) {
            add(HIPS, 0.0f, 0.0f, home * homeFrame.gait * 0.55f);
            add(HIPS, call * -5.0f, 0.0f, 0.0f);
            add(LEFT_UP_LEG, home * homeFrame.gait * 5.0f + call * -82.0f,
                    0.0f, call * 4.0f);
            add(RIGHT_UP_LEG, home * -homeFrame.gait * 5.0f + call * -82.0f,
                    0.0f, call * -4.0f);
            add(LEFT_LEG, home * -homeFrame.gait * 2.4f + call * 92.0f, 0.0f, 0.0f);
            add(RIGHT_LEG, home * homeFrame.gait * 2.4f + call * 92.0f, 0.0f, 0.0f);
            add(LEFT_FOOT, call * -8.0f, 0.0f, 0.0f);
            add(RIGHT_FOOT, call * -8.0f, 0.0f, 0.0f);
        }

        private void applyPostureLayer(double t, float home, float call) {
            double theta = Math.PI * 2.0 * ((t % 4.0) / 4.0);
            float wave = (float) Math.sin(theta);
            float second = (float) Math.sin(theta * 2.0);
            float callSlow = (float) Math.sin(t * 0.62 + 0.45);
            float callDrift = (float) Math.sin(t * 0.37 + 1.10);
            add(HIPS,
                    home * (-2.0f + 0.10f * second),
                    home * (-3.5f + 0.12f * wave),
                    home * (6.0f + 0.18f * wave));
            add(LEFT_SHOULDER,
                    home * (-1.2f - 0.10f * wave - 0.03f * second)
                            + call * (-0.12f + 0.20f * callSlow),
                    0.0f,
                    home * (-0.7f - 0.05f * wave) + call * -0.14f * callDrift);
            add(RIGHT_SHOULDER,
                    home * (-0.6f - 0.08f * wave + 0.02f * second)
                            + call * (-0.08f - 0.17f * callDrift),
                    0.0f,
                    home * (0.5f + 0.04f * wave) + call * 0.12f * callSlow);
            add(SPINE, call * (1.20f + 0.18f * callDrift), 0.0f, 0.0f);
            add(SPINE01, call * (1.60f + 0.24f * callSlow), 0.0f, 0.0f);
            add(SPINE02, call * (1.20f + 0.16f * callDrift), 0.0f, 0.0f);
        }

        private void applyConversationLayer(long frameTimeNanos, float home, float call) {
            long duration = targetCall ? CALL_ARM_LOOP_NANOS : HOME_ARM_LOOP_NANOS;
            long elapsed = Math.max(0L, frameTimeNanos - armPhaseStartNanos);
            double theta = Math.PI * 2.0 * ((double) (elapsed % duration) / (double) duration);

            // Different phases on left/right make the body read as one person settling naturally,
            // not two mirrored metronomes. Integer harmonics keep both loops seamless.
            float leftWave = (float) Math.sin(theta);
            float rightWave = (float) Math.sin(theta + 1.17);
            float leftSecond = (float) Math.sin(theta * 2.0 + 0.35);
            float rightSecond = (float) Math.sin(theta * 2.0 + 1.72);
            float leftThird = (float) Math.sin(theta * 3.0 + 0.20);
            float rightThird = (float) Math.sin(theta * 3.0 + 1.31);
            float leftBreath = (float) Math.sin(theta - 0.45);
            float rightBreath = (float) Math.sin(theta + 0.78);
            float speech = targetCall && avatarState(view) == CelineAvatarController.State.SPEAKING
                    ? speechEnergy(view) : 0.0f;

            add(LEFT_SHOULDER,
                    home * (-homeFrame.gait * 0.9f + 0.18f * leftBreath), 0.0f, 0.0f);
            add(RIGHT_SHOULDER,
                    home * (homeFrame.gait * 0.9f + 0.16f * rightBreath), 0.0f, 0.0f);

            float leftArmPitchHome = -homeFrame.gait * 2.2f
                    + 2.55f * leftWave + 0.40f * leftSecond;
            float rightArmPitchHome = homeFrame.gait * 2.2f
                    + 2.30f * rightWave + 0.36f * rightSecond;
            float leftArmPitchCall = 2.05f * leftWave + 0.52f * leftSecond
                    + speech * 1.05f * leftThird;
            float rightArmPitchCall = 1.85f * rightWave + 0.48f * rightSecond
                    + speech * 0.92f * rightThird;
            add(LEFT_ARM,
                    home * leftArmPitchHome + call * leftArmPitchCall,
                    0.0f,
                    home * (29.5f + 1.05f * leftBreath)
                            + call * (30.5f + 0.84f * leftBreath
                            + 0.30f * speech * leftSecond));
            add(RIGHT_ARM,
                    home * rightArmPitchHome + call * rightArmPitchCall,
                    0.0f,
                    home * (-29.5f - 0.96f * rightBreath)
                            + call * (-30.5f - 0.78f * rightBreath
                            - 0.28f * speech * rightSecond));

            add(LEFT_FOREARM,
                    home * (-6.0f + 1.80f * leftSecond + 0.30f * leftThird)
                            + call * (-14.0f + 2.15f * leftSecond
                            + speech * 0.90f * leftThird),
                    0.0f, 0.0f);
            add(RIGHT_FOREARM,
                    home * (-6.0f + 1.65f * rightSecond + 0.28f * rightThird)
                            + call * (-14.0f + 1.95f * rightSecond
                            + speech * 0.82f * rightThird),
                    0.0f, 0.0f);
            add(LEFT_HAND,
                    home * (3.20f * leftWave + 0.82f * leftSecond)
                            + call * (3.05f * leftWave + 0.76f * leftSecond
                            + speech * 1.10f * leftThird),
                    0.0f,
                    home * (1.45f * leftSecond + 0.32f * leftThird)
                            + call * (1.38f * leftSecond + 0.28f * leftThird
                            + speech * 0.45f * leftThird));
            add(RIGHT_HAND,
                    home * (2.95f * rightWave + 0.76f * rightSecond)
                            + call * (2.82f * rightWave + 0.70f * rightSecond
                            + speech * 1.00f * rightThird),
                    0.0f,
                    home * (1.34f * rightSecond + 0.30f * rightThird)
                            + call * (1.28f * rightSecond + 0.26f * rightThird
                            + speech * 0.40f * rightThird));

            if (home > 0.98f && !loggedBlock5Home) {
                loggedBlock5Home = true;
                Celine3DDiagnostics.record(view.getContext(), "V80-450",
                        "Block 5 HOME Arm/Hand-Leben aktiv",
                        "async=true arm~3deg forearm~2deg wrist~4deg fingerBones=false calm=true");
            }
            if (call > 0.98f && !loggedBlock5Call) {
                loggedBlock5Call = true;
                Celine3DDiagnostics.record(view.getContext(), "V80-451",
                        "Block 5 CALL Arm/Hand-Leben aktiv",
                        "async=true arm~3deg forearm~3deg wrist~4deg speechBounded=true fingerBones=false");
            }
        }

        private void applyGazeLayer(double t, float home, float call) {
            if (probeModel) {
                // Preserve the CI-only visible skinning capability probe under the central owner.
                // These large angles are unreachable for the canonical production model.
                add(NECK,
                        call * (float) Math.cos(t * Math.PI * 0.5) * 4.0f,
                        call * (float) Math.sin(t * Math.PI) * 11.0f,
                        call * (float) Math.sin(t * Math.PI * 0.5 + 0.7) * 2.0f);
                add(HEAD,
                        home * (float) Math.cos(t * Math.PI) * 5.0f
                                + call * (float) Math.cos(t * Math.PI + 0.5) * 6.0f,
                        home * (float) Math.sin(t * Math.PI) * 14.0f
                                + call * (float) Math.sin(t * Math.PI + 1.0) * -16.0f,
                        home * (float) Math.sin(t * Math.PI * 0.5 + 0.4) * 2.5f
                                + call * (float) Math.sin(t * Math.PI * 0.75) * 3.0f);
                return;
            }
            float slowHome = (float) Math.sin(t * 0.55);
            float breathHome = (float) Math.sin(t * 1.35 + 0.3);
            float speech = speechEnergy(view);
            CelineAvatarController.State state = avatarState(view);
            float homeNod = state == CelineAvatarController.State.SPEAKING
                    ? (float) Math.sin(t * 4.4) * (0.25f + 0.45f * speech) : 0.0f;
            float homeListen = state == CelineAvatarController.State.LISTENING ? 0.28f : 0.0f;

            float slowCall = (float) Math.sin(t * 0.48);
            float breathCall = (float) Math.sin(t * 1.28 + 0.4);
            float callNod = state == CelineAvatarController.State.SPEAKING
                    ? (float) Math.sin(t * 4.0) * (0.18f + 0.32f * speech) : 0.0f;

            add(NECK,
                    call * breathCall * 0.16f,
                    call * slowCall * 0.32f,
                    call * -slowCall * 0.10f);
            add(HEAD,
                    home * (breathHome * 0.32f + homeNod)
                            + call * (breathCall * 0.24f + callNod),
                    home * slowHome * 0.70f + call * slowCall * 0.58f,
                    home * (-slowHome * 0.24f + homeListen)
                            + call * (-slowCall * 0.16f
                            + (state == CelineAvatarController.State.LISTENING ? 0.18f : 0.0f)));
        }

        private void applyRoot(float home, float call) {
            float x = (layerMask & LAYER_BASE) == 0 ? 0.0f : home * homeFrame.x;
            float y = (layerMask & LAYER_BASE) == 0 ? 0.0f
                    : home * homeFrame.bob + call * CALL_ROOT_DOWN;
            float z = (layerMask & LAYER_BASE) == 0 ? 0.0f
                    : home * homeFrame.z + call * CALL_ROOT_FORWARD;
            float yaw = (layerMask & LAYER_BASE) == 0 ? 0.0f : home * homeFrame.yaw;
            float[] localRotation = new float[16];
            float[] rotated = new float[16];
            float[] worldMove = new float[16];
            float[] out = new float[16];
            Matrix.setIdentityM(localRotation, 0);
            if (yaw != 0.0f) Matrix.rotateM(localRotation, 0, yaw, 0.0f, 1.0f, 0.0f);
            Matrix.multiplyMM(rotated, 0, rootBase, 0, localRotation, 0);
            Matrix.setIdentityM(worldMove, 0);
            Matrix.translateM(worldMove, 0, x, y, z);
            Matrix.multiplyMM(out, 0, worldMove, 0, rotated, 0);
            transforms.setTransform(rootInstance, out);
        }

        private void add(int index, float pitch, float yaw, float roll) {
            int offset = index * 3;
            angles[offset] += pitch;
            angles[offset + 1] += yaw;
            angles[offset + 2] += roll;
        }

        private void applyBone(int index) {
            Bone bone = bones[index];
            if (bone == null) return;
            int offset = index * 3;
            float pitch = angles[offset];
            float yaw = angles[offset + 1];
            float roll = angles[offset + 2];
            float[] delta = new float[16];
            float[] out = new float[16];
            Matrix.setIdentityM(delta, 0);
            if (yaw != 0.0f) Matrix.rotateM(delta, 0, yaw, 0.0f, 1.0f, 0.0f);
            if (pitch != 0.0f) Matrix.rotateM(delta, 0, pitch, 1.0f, 0.0f, 0.0f);
            if (roll != 0.0f) Matrix.rotateM(delta, 0, roll, 0.0f, 0.0f, 1.0f);
            Matrix.multiplyMM(out, 0, bone.base, 0, delta, 0);
            transforms.setTransform(bone.instance, out);
        }

        void restoreBases() {
            try {
                transforms.openLocalTransformTransaction();
                transforms.setTransform(rootInstance, rootBase);
                for (Bone bone : bones) {
                    if (bone != null) transforms.setTransform(bone.instance, bone.base);
                }
            } catch (Throwable ignored) {
            } finally {
                try { transforms.commitLocalTransformTransaction(); } catch (Throwable ignored) {}
            }
            try { animator.updateBoneMatrices(); } catch (Throwable ignored) {}
        }

        private Bone bone(FilamentAsset asset, String name) {
            try {
                int entity = asset.getFirstEntityByName(name);
                if (entity == 0 && "neck".equals(name)) entity = asset.getFirstEntityByName("Neck");
                if (entity == 0) return null;
                int instance = transforms.getInstance(entity);
                if (instance == 0) return null;
                return new Bone(instance, transforms.getTransform(instance, new float[16]));
            } catch (Throwable ignored) {
                return null;
            }
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
            if (value instanceof Number) return clamp(((Number) value).floatValue(), 0.0f, 1.0f);
        } catch (Throwable ignored) {}
        return 0.0f;
    }

    private static boolean isCallStage(View view) {
        View current = view;
        while (current != null) {
            Object tag = current.getTag();
            if (tag != null && "v45-stage-slot".equals(tag.toString())) return true;
            Object parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private static Celine3DView find3D(View root) {
        if (root instanceof Celine3DView) return (Celine3DView) root;
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                Celine3DView found = find3D(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }
}
