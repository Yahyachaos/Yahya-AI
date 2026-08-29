package de.yahya.ai;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.opengl.Matrix;
import android.view.View;

import com.google.android.filament.TransformManager;
import com.google.android.filament.gltfio.Animator;
import com.google.android.filament.gltfio.FilamentAsset;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * v80 central production owner for Celine's root, body, room locomotion, head and face layers.
 *
 * Blocks 4-9 remain protected. 9R.1 extends this same writer with bounded world-root travel over
 * the accepted 4R nav graph and a real Walking clip derived from the canonical Meshy companion.
 * 9R.2 adds only a bounded table-contact lean inside this same owner. 9R.3 adds a bounded bed
 * edge/relax/lie/stand chain through a pose-contribution helper that never writes transforms on its
 * own; this class remains the only body/root transaction writer.
 */
final class CelineProductionPresenceV80 {
    enum Stage { AUTO, HOME, CALL }
    enum LayerView { COMBINED, BASE_ONLY, BREATHING_POSTURE, CONVERSATION, GAZE_HEAD }

    private enum RoomMotion { AMBIENT, TURNING, WALKING, SETTLING, ANCHOR_IDLE, AMBIENT_HANDOFF }

    private static final int LAYER_BASE = 1;
    private static final int LAYER_POSTURE = 1 << 1;
    private static final int LAYER_CONVERSATION = 1 << 2;
    private static final int LAYER_GAZE = 1 << 3;
    private static final int LAYER_ALL =
            LAYER_BASE | LAYER_POSTURE | LAYER_CONVERSATION | LAYER_GAZE;

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

    private static final String[] WALKING_BONE_NAMES = {
            "Hips", "Spine", "Spine01", "Spine02",
            "LeftShoulder", "RightShoulder", "LeftArm", "RightArm",
            "LeftForeArm", "RightForeArm", "LeftHand", "RightHand",
            "LeftUpLeg", "RightUpLeg", "LeftLeg", "RightLeg", "LeftFoot", "RightFoot"
    };

    private static final String TALK_ANCHOR = "camera_talk_anchor";
    private static final String TABLE_LEAN_ANCHOR = "foreground_table_lean_anchor";
    private static final String BED_APPROACH_ANCHOR = "bed_approach_anchor";
    private static final String BED_EDGE_ANCHOR = "bed_edge_sit_anchor";
    private static final String BED_RELAX_ANCHOR = "bed_relax_anchor";
    private static final String BED_LIE_ANCHOR = "bed_lie_anchor";
    private static final String BED_EXIT_ANCHOR = "bed_exit_anchor";
    private static final String CI_ROOM_ACTION_FILE = "celine-ci-room-action-v9r";
    private static final float CALL_ROOT_DOWN = -0.30f;
    private static final float CALL_ROOT_FORWARD = 0.12f;
    private static final long HOME_ARM_LOOP_NANOS = 5_200_000_000L;
    private static final long CALL_ARM_LOOP_NANOS = 6_100_000_000L;
    private static final float MAX_SOCIAL_GAZE_X = 0.12f;
    private static final float MAX_SOCIAL_GAZE_Y = 0.08f;
    private static final float WALK_CYCLE_DISTANCE_M = 0.74f;
    private static final float WALK_SPEED_MPS = 0.72f;
    private static final float TURN_SPEED_DPS = 165.0f;
    private static final float FINAL_TURN_SPEED_DPS = 120.0f;
    private static final float ROOM_FLOOR_BLEND_PER_SECOND = 2.4f;
    private static final float TABLE_LEAN_BLEND_PER_SECOND = 2.2f;
    private static final float MAX_ROOM_FLOOR_CORRECTION_M = 0.65f;

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

    static String[] walkingBoneNames() {
        return WALKING_BONE_NAMES.clone();
    }

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
        CelineMorphRuntimeV62.onFrame(view, frameTimeNanos);
    }

    static boolean requestRoomAnchor(Celine3DView view, String anchorId) {
        Mixer mixer = mixerFor(view);
        return mixer != null && mixer.requestRoomAnchor(anchorId);
    }

    static void setDiagnostic(Celine3DView view, Stage stage, LayerView layers) {
        Mixer mixer = mixerFor(view);
        if (mixer == null) return;
        mixer.diagnosticDisabled = false;
        mixer.stage = stage == null || stage == Stage.AUTO ? Stage.HOME : stage;
        mixer.layerMask = maskFor(layers);
        Celine3DDiagnostics.record(view.getContext(), "V80-440",
                "Avatar Lab Production-Owner gesetzt",
                "stage=" + mixer.stage + " layers="
                        + (layers == null ? LayerView.COMBINED : layers)
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
        synchronized (MIXERS) {
            mixer = MIXERS.get(view);
        }
        return mixer == null ? new HomeFrame() : mixer.homeFrame;
    }

    static float socialLookX(Celine3DView view) {
        synchronized (MIXERS) {
            Mixer mixer = MIXERS.get(view);
            return mixer == null ? 0.0f : mixer.socialGazeX;
        }
    }

    static float socialLookY(Celine3DView view) {
        synchronized (MIXERS) {
            Mixer mixer = MIXERS.get(view);
            return mixer == null ? 0.0f : mixer.socialGazeY;
        }
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
            case BASE_ONLY:
                return LAYER_BASE;
            case BREATHING_POSTURE:
                return LAYER_BASE | LAYER_POSTURE;
            case CONVERSATION:
                return LAYER_BASE | LAYER_CONVERSATION;
            case GAZE_HEAD:
                return LAYER_BASE | LAYER_GAZE;
            case COMBINED:
            default:
                return LAYER_ALL;
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
        final float[] locomotionQuats = new float[BONE_COUNT * 4];
        final HomeFrame homeFrame = new HomeFrame();
        final CelineWalkingClipV9R walkingClip;
        final CelineRoomNavigatorV9R roomNavigator;
        final CelineRoomWorldContractV80.Anchor talkAnchor;
        final CelineBedPoseV9R3 bedPose;
        final float roomFloorCorrectionY;

        Stage stage = Stage.AUTO;
        int layerMask = LAYER_ALL;
        boolean diagnosticDisabled;
        boolean loggedHome;
        boolean loggedCall;
        boolean loggedBlock5Home;
        boolean loggedBlock5Call;
        boolean loggedBlock6Home;
        boolean loggedBlock6Call;
        boolean loggedTableLean;
        boolean targetCall;
        boolean targetInitialized;
        boolean inCallNow;
        float callBlend;
        float socialGazeX;
        float socialGazeY;
        float socialGazeTargetX;
        float socialGazeTargetY;
        long lastFrameNanos;
        long motionStartNanos;
        long armPhaseStartNanos;
        long nextGazeShiftNanos;
        long nextCiRoomMarkerCheckNanos;
        int gazeShiftSerial;
        String loggedBedPoseAnchor;

        RoomMotion roomMotion = RoomMotion.AMBIENT;
        String currentAnchorId = TALK_ANCHOR;
        String pendingTargetId;
        List<String> roomRoute = Collections.emptyList();
        int routeIndex;
        float roomX;
        float roomZ;
        float roomYaw;
        float segmentTargetX;
        float segmentTargetZ;
        float segmentTravelYaw;
        float segmentFinalYaw;
        float walkBlend;
        float walkBob;
        float roomFloorBlend;
        float tableLeanBlend;
        float legacyHomeGaitScale = 1.0f;
        double walkDistanceMeters;

        Mixer(Celine3DView view) throws Exception {
            this.view = view;
            Context context = view.getContext();
            if (context instanceof Activity) {
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

            CelineRoomWorldContractV80 world = CelineRoomWorldContractV80.load(context);
            roomNavigator = CelineRoomNavigatorV9R.load(context, world);
            talkAnchor = roomNavigator.anchor(TALK_ANCHOR);
            if (talkAnchor == null) throw new IllegalStateException("9R camera_talk_anchor fehlt");
            bedPose = new CelineBedPoseV9R3(world);
            roomFloorCorrectionY = resolveRoomFloorCorrectionY();
            walkingClip = CelineWalkingClipV9R.load(context);
            resetLocomotionQuats();

            Celine3DDiagnostics.record(context, "V80-400", "Central Production Presence gebunden",
                    "owner=CelineProductionPresenceV80 bones=" + resolved + "/" + BONE_COUNT
                            + " root=scene/seat base"
                            + " order=base>posture>conversation>gaze>face"
                            + " face=CelineMorphRuntimeV62 PCM=v77"
                            + " block5SixJointArms=true fingerBones=false"
                            + " block6SocialGaze=true independentWriter=false"
                            + " 9RNav=true walking=MeshyCanonical tableLean=true bedChain=true"
                            + " probe=" + probeModel);
            Celine3DDiagnostics.record(context, "V80-470", "9R Floor-Root kalibriert",
                    "floorY=" + talkAnchor.worldY
                            + " nominalSoleY=" + rootBase[13]
                            + " correctionY=" + roomFloorCorrectionY
                            + " scope=9R_room_root_only eased=true CALL=false");
        }

        private float resolveRoomFloorCorrectionY() {
            if (probeModel) return 0.0f;
            float correction = talkAnchor.worldY - rootBase[13];
            if (Float.isNaN(correction) || Float.isInfinite(correction)
                    || correction > 0.0f
                    || correction < -MAX_ROOM_FLOOR_CORRECTION_M) {
                throw new IllegalStateException(
                        "Ungültige 9R Floor-Kalibrierung: floor=" + talkAnchor.worldY
                                + " sole=" + rootBase[13] + " correction=" + correction);
            }
            return correction;
        }

        void applyBody(long frameTimeNanos) {
            if (diagnosticDisabled) return;
            boolean callNow = stage == Stage.CALL || (stage == Stage.AUTO && isCallStage(view));
            inCallNow = callNow;
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
                    : Math.min(0.10f,
                    Math.max(0.0f, (frameTimeNanos - lastFrameNanos) * 1.0e-9f));
            lastFrameNanos = frameTimeNanos;
            float targetBlend = callNow ? 1.0f : 0.0f;
            float ease = Math.min(1.0f, deltaSeconds * 4.5f);
            callBlend += (targetBlend - callBlend) * ease;
            if (Math.abs(callBlend - targetBlend) < 0.001f) callBlend = targetBlend;

            double t = Math.max(0L, frameTimeNanos - motionStartNanos) * 1.0e-9;
            updateHomeFrame(t);
            consumePrivateRoomMarker(frameTimeNanos);
            updateRoomLocomotion(deltaSeconds, callNow);
            updateLocomotionPose();

            Arrays.fill(angles, 0.0f);
            float home = 1.0f - callBlend;
            float call = callBlend;
            if ((layerMask & LAYER_BASE) != 0) applyBaseLayer(home, call);
            if ((layerMask & LAYER_POSTURE) != 0) applyPostureLayer(t, home, call);
            if ((layerMask & LAYER_CONVERSATION) != 0) {
                applyConversationLayer(frameTimeNanos, home, call);
            }
            if ((layerMask & LAYER_GAZE) != 0) {
                applyGazeLayer(frameTimeNanos, deltaSeconds, t, home, call);
            } else {
                socialGazeX = 0.0f;
                socialGazeY = 0.0f;
            }

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
                Celine3DDiagnostics.record(view.getContext(), "V80-420",
                        "Central CALL Presence aktiv",
                        "root/seat+posture+conversation+gaze+face · oneTransaction=true oneSkinUpdate=true");
            } else if (!callNow && !loggedHome) {
                loggedHome = true;
                Celine3DDiagnostics.record(view.getContext(), "V80-410",
                        "Central HOME Presence aktiv",
                        "root/world+posture+conversation+gaze+face · oneTransaction=true oneSkinUpdate=true");
            }
        }

        boolean requestRoomAnchor(String anchorId) {
            String target = anchorId == null ? "" : anchorId.trim();
            if (target.isEmpty() || roomNavigator.anchor(target) == null) {
                Celine3DDiagnostics.record(view.getContext(), "V80-476",
                        "9R Room-Ziel abgelehnt", "target=" + target + " reason=unknown_anchor");
                return false;
            }
            if (inCallNow || stage == Stage.CALL) {
                Celine3DDiagnostics.record(view.getContext(), "V80-476",
                        "9R Room-Ziel abgelehnt",
                        "target=" + target + " reason=CALL_seated_contract_protected");
                return false;
            }

            if (roomMotion == RoomMotion.TURNING
                    || roomMotion == RoomMotion.WALKING
                    || roomMotion == RoomMotion.SETTLING) {
                pendingTargetId = target;
                Celine3DDiagnostics.record(view.getContext(), "V80-478",
                        "9R Room-Ziel sicher gepuffert",
                        "target=" + target + " untilNextAnchor=true");
                return true;
            }

            if (roomMotion == RoomMotion.AMBIENT) {
                roomX = homeFrame.x;
                roomZ = homeFrame.z;
                roomYaw = homeFrame.yaw;
            }

            if (target.equals(currentAnchorId)) {
                Celine3DDiagnostics.record(view.getContext(), "V80-475",
                        "9R Nav-Anker bereits erreicht",
                        "anchor=" + currentAnchorId + " noTeleport=true");
                return true;
            }
            return startRoute(target);
        }

        private boolean startRoute(String target) {
            List<String> route = roomNavigator.route(currentAnchorId, target);
            if (route.size() < 2) {
                Celine3DDiagnostics.record(view.getContext(), "V80-476",
                        "9R Room-Ziel abgelehnt",
                        "target=" + target + " from=" + currentAnchorId + " reason=no_nav_route");
                return false;
            }
            roomRoute = route;
            routeIndex = 1;
            pendingTargetId = null;
            prepareSegment(roomRoute.get(routeIndex));
            Celine3DDiagnostics.record(view.getContext(), "V80-472",
                    "9R Room-Ziel angenommen",
                    "from=" + currentAnchorId + " target=" + target
                            + " route=" + route + " cameraFixed=true");
            return true;
        }

        private void prepareSegment(String anchorId) {
            CelineRoomWorldContractV80.Anchor target = roomNavigator.anchor(anchorId);
            if (target == null) {
                failRoomMotion("missing segment anchor " + anchorId);
                return;
            }
            segmentTargetX = target.localX - talkAnchor.localX;
            segmentTargetZ = target.localZ - talkAnchor.localZ;
            float dx = segmentTargetX - roomX;
            float dz = segmentTargetZ - roomZ;
            if (Math.abs(dx) + Math.abs(dz) <= 0.01f) {
                segmentTravelYaw = roomYaw;
            } else {
                segmentTravelYaw = (float) Math.toDegrees(Math.atan2(dx, dz));
            }
            segmentFinalYaw = wrapDegrees(target.facingYDeg - talkAnchor.facingYDeg);
            roomMotion = RoomMotion.TURNING;
            Celine3DDiagnostics.record(view.getContext(), "V80-473",
                    "9R Vor Laufweg ausgerichtet",
                    "from=" + currentAnchorId + " to=" + anchorId
                            + " travelYaw=" + segmentTravelYaw + " turnBeforeWalk=true");
        }

        private void updateRoomLocomotion(float deltaSeconds, boolean callNow) {
            float floorBlendTarget = !callNow
                    && roomMotion != RoomMotion.AMBIENT
                    && roomMotion != RoomMotion.AMBIENT_HANDOFF ? 1.0f : 0.0f;
            roomFloorBlend = approach(roomFloorBlend, floorBlendTarget,
                    deltaSeconds * ROOM_FLOOR_BLEND_PER_SECOND);

            boolean tableLeanHold = !callNow
                    && TABLE_LEAN_ANCHOR.equals(currentAnchorId)
                    && roomMotion == RoomMotion.ANCHOR_IDLE;
            tableLeanBlend = approach(tableLeanBlend, tableLeanHold ? 1.0f : 0.0f,
                    deltaSeconds * TABLE_LEAN_BLEND_PER_SECOND);
            if (tableLeanHold && tableLeanBlend >= 0.98f && !loggedTableLean) {
                loggedTableLean = true;
                Celine3DDiagnostics.record(view.getContext(), "V80-480",
                        "9R.2 Table-Lean stabil",
                        "anchor=" + TABLE_LEAN_ANCHOR
                                + " blend=" + tableLeanBlend
                                + " handContact=false centralOwner=true cameraFixed=true");
            } else if (!tableLeanHold && tableLeanBlend <= 0.02f) {
                loggedTableLean = false;
            }

            boolean bedEnabled = !callNow && (bedPose.isBedAnchor(currentAnchorId)
                    || BED_APPROACH_ANCHOR.equals(currentAnchorId));
            bedPose.update(currentAnchorId, deltaSeconds, bedEnabled);
            if (bedPose.isBedAnchor(currentAnchorId)
                    && roomMotion == RoomMotion.ANCHOR_IDLE
                    && bedPose.settled(currentAnchorId)) {
                if (!currentAnchorId.equals(loggedBedPoseAnchor)) {
                    loggedBedPoseAnchor = currentAnchorId;
                    Celine3DDiagnostics.record(view.getContext(), "V80-483",
                            "9R.3 Bett-Pose stabil",
                            "anchor=" + currentAnchorId
                                    + " pose=" + bedPose.poseName(currentAnchorId)
                                    + " centralOwner=true eased=true cameraFixed=true"
                                    + " rootOffset=" + bedPose.rootX() + ","
                                    + bedPose.rootY() + "," + bedPose.rootZ()
                                    + " noTeleport=true");
                }
            } else if (loggedBedPoseAnchor != null
                    && !loggedBedPoseAnchor.equals(currentAnchorId)) {
                loggedBedPoseAnchor = null;
            }

            if (callNow) {
                walkBlend = approach(walkBlend, 0.0f, deltaSeconds * 5.5f);
                walkBob = 0.0f;
                legacyHomeGaitScale = 0.0f;
                return;
            }

            switch (roomMotion) {
                case AMBIENT:
                    walkBlend = approach(walkBlend, 0.0f, deltaSeconds * 5.5f);
                    walkBob = 0.0f;
                    legacyHomeGaitScale = 1.0f;
                    break;

                case TURNING:
                    legacyHomeGaitScale = 0.0f;
                    walkBlend = approach(walkBlend, 0.0f, deltaSeconds * 5.5f);
                    roomYaw = approachAngle(roomYaw, segmentTravelYaw, TURN_SPEED_DPS * deltaSeconds);
                    if (angleDistance(roomYaw, segmentTravelYaw) <= 1.2f) {
                        roomYaw = wrapDegrees(segmentTravelYaw);
                        roomMotion = RoomMotion.WALKING;
                        Celine3DDiagnostics.record(view.getContext(), "V80-474",
                                "9R Walking aktiv",
                                "from=" + currentAnchorId + " to=" + roomRoute.get(routeIndex)
                                        + " speed=" + WALK_SPEED_MPS
                                        + "mps clip=Walking sourceSha256="
                                        + CelineWalkingClipV9R.SOURCE_SHA256);
                    }
                    break;

                case WALKING:
                    legacyHomeGaitScale = 0.0f;
                    walkBlend = approach(walkBlend, 1.0f, deltaSeconds * 4.8f);
                    float dx = segmentTargetX - roomX;
                    float dz = segmentTargetZ - roomZ;
                    float distance = (float) Math.sqrt(dx * dx + dz * dz);
                    if (distance <= 0.012f) {
                        roomX = segmentTargetX;
                        roomZ = segmentTargetZ;
                        arriveSegment();
                        break;
                    }
                    float speed = Math.min(WALK_SPEED_MPS,
                            Math.max(0.18f, distance * 2.25f));
                    float step = Math.min(distance, speed * deltaSeconds);
                    if (distance > 0.00001f) {
                        roomX += dx / distance * step;
                        roomZ += dz / distance * step;
                        walkDistanceMeters += step;
                    }
                    if (step >= distance - 0.00001f) {
                        roomX = segmentTargetX;
                        roomZ = segmentTargetZ;
                        arriveSegment();
                    }
                    break;

                case SETTLING:
                    legacyHomeGaitScale = 0.0f;
                    walkBlend = approach(walkBlend, 0.0f, deltaSeconds * 3.8f);
                    roomYaw = approachAngle(
                            roomYaw, segmentFinalYaw, FINAL_TURN_SPEED_DPS * deltaSeconds);
                    if (walkBlend <= 0.015f
                            && angleDistance(roomYaw, segmentFinalYaw) <= 1.0f) {
                        walkBlend = 0.0f;
                        roomYaw = wrapDegrees(segmentFinalYaw);
                        if (TALK_ANCHOR.equals(currentAnchorId)) {
                            roomMotion = RoomMotion.AMBIENT_HANDOFF;
                        } else {
                            roomMotion = RoomMotion.ANCHOR_IDLE;
                        }
                        Celine3DDiagnostics.record(view.getContext(), "V80-475",
                                "9R Nav-Anker erreicht",
                                "anchor=" + currentAnchorId + " x=" + roomX + " z=" + roomZ
                                        + " facing=" + roomYaw
                                        + " floorY=" + talkAnchor.worldY
                                        + " soleY=" + (rootBase[13]
                                        + roomFloorCorrectionY * smoothStep(roomFloorBlend))
                                        + " floorBlend=" + smoothStep(roomFloorBlend)
                                        + " floorCalibrated=" + (roomFloorBlend > 0.98f)
                                        + " walkStopped=true noTeleport=true");
                    }
                    break;

                case ANCHOR_IDLE:
                    legacyHomeGaitScale = 0.0f;
                    walkBlend = approach(walkBlend, 0.0f, deltaSeconds * 5.5f);
                    walkBob = 0.0f;
                    break;

                case AMBIENT_HANDOFF:
                    float blend = Math.min(1.0f, deltaSeconds * 3.0f);
                    roomX += (homeFrame.x - roomX) * blend;
                    roomZ += (homeFrame.z - roomZ) * blend;
                    roomYaw = approachAngle(
                            roomYaw, homeFrame.yaw, FINAL_TURN_SPEED_DPS * deltaSeconds);
                    legacyHomeGaitScale =
                            approach(legacyHomeGaitScale, 1.0f, deltaSeconds * 2.4f);
                    walkBlend = approach(walkBlend, 0.0f, deltaSeconds * 5.5f);
                    walkBob = 0.0f;
                    if (Math.abs(roomX - homeFrame.x) < 0.015f
                            && Math.abs(roomZ - homeFrame.z) < 0.015f
                            && angleDistance(roomYaw, homeFrame.yaw) < 1.0f
                            && roomFloorBlend < 0.015f
                            && legacyHomeGaitScale > 0.97f) {
                        roomMotion = RoomMotion.AMBIENT;
                        legacyHomeGaitScale = 1.0f;
                        Celine3DDiagnostics.record(view.getContext(), "V80-477",
                                "9R Camera-Talk Ambient wiederhergestellt",
                                "anchor=" + TALK_ANCHOR + " eased=true snap=false");
                    }
                    break;
            }
        }

        private void arriveSegment() {
            String arrived = roomRoute.get(routeIndex);
            currentAnchorId = arrived;

            if (pendingTargetId != null) {
                String pending = pendingTargetId;
                pendingTargetId = null;
                List<String> next = roomNavigator.route(currentAnchorId, pending);
                if (next.size() >= 2) {
                    roomRoute = next;
                    routeIndex = 1;
                    prepareSegment(roomRoute.get(routeIndex));
                    return;
                }
            }

            if (routeIndex + 1 < roomRoute.size()) {
                routeIndex++;
                prepareSegment(roomRoute.get(routeIndex));
                return;
            }

            CelineRoomWorldContractV80.Anchor anchor = roomNavigator.anchor(currentAnchorId);
            segmentFinalYaw = anchor == null
                    ? roomYaw : wrapDegrees(anchor.facingYDeg - talkAnchor.facingYDeg);
            roomMotion = RoomMotion.SETTLING;
        }

        private void failRoomMotion(String reason) {
            pendingTargetId = null;
            roomRoute = Collections.emptyList();
            routeIndex = 0;
            roomMotion = TALK_ANCHOR.equals(currentAnchorId)
                    ? RoomMotion.AMBIENT_HANDOFF : RoomMotion.ANCHOR_IDLE;
            Celine3DDiagnostics.record(view.getContext(), "V80-479",
                    "9R Room-Locomotion sicher gestoppt", "reason=" + reason);
        }

        private void consumePrivateRoomMarker(long frameTimeNanos) {
            if (stage != Stage.AUTO) return;
            if ((view.getContext().getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
                return;
            }
            if (frameTimeNanos < nextCiRoomMarkerCheckNanos) return;
            nextCiRoomMarkerCheckNanos = frameTimeNanos + 220_000_000L;

            File marker = new File(view.getContext().getFilesDir(), CI_ROOM_ACTION_FILE);
            if (!marker.isFile()) return;
            try {
                byte[] data = new byte[(int) Math.min(128L, marker.length())];
                int count;
                try (FileInputStream input = new FileInputStream(marker)) {
                    count = input.read(data);
                }
                marker.delete();
                if (count <= 0) return;
                String target = new String(data, 0, count, StandardCharsets.UTF_8).trim();
                if ("cancel".equalsIgnoreCase(target) || "home".equalsIgnoreCase(target)) {
                    target = TALK_ANCHOR;
                }
                requestRoomAnchor(target);
            } catch (Throwable error) {
                marker.delete();
                Celine3DDiagnostics.error(view.getContext(), "V80-479",
                        "9R privater Proof-Marker FEHLER", error);
            }
        }

        private void updateLocomotionPose() {
            resetLocomotionQuats();
            if (walkBlend <= 0.0001f) {
                walkBob = 0.0f;
                return;
            }
            double clipSeconds =
                    (walkDistanceMeters / WALK_CYCLE_DISTANCE_M) * walkingClip.durationSeconds();
            float[] q = new float[4];
            for (int i = 0; i < BONE_NAMES.length; i++) {
                if (!walkingClip.sampleRotation(BONE_NAMES[i], clipSeconds, walkBlend, q)) continue;
                int o = i * 4;
                locomotionQuats[o] = q[0];
                locomotionQuats[o + 1] = q[1];
                locomotionQuats[o + 2] = q[2];
                locomotionQuats[o + 3] = q[3];
            }
            walkBob = walkingClip.sampleHipsBob(clipSeconds, walkBlend);
        }

        private void resetLocomotionQuats() {
            for (int i = 0; i < BONE_COUNT; i++) {
                int o = i * 4;
                locomotionQuats[o] = 0.0f;
                locomotionQuats[o + 1] = 0.0f;
                locomotionQuats[o + 2] = 0.0f;
                locomotionQuats[o + 3] = 1.0f;
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
            homeFrame.bob =
                    Math.abs((float) Math.sin(t * 2.65)) * 0.018f * walkAmount;
            homeFrame.yaw = clamp(dx * 42.0f, -3.0f, 3.0f);
        }

        private void applyBaseLayer(float home, float call) {
            float gait = homeFrame.gait * legacyHomeGaitScale;
            add(HIPS, 0.0f, 0.0f, home * gait * 0.55f);
            add(HIPS, call * -5.0f, 0.0f, 0.0f);
            add(LEFT_UP_LEG, home * gait * 5.0f + call * -82.0f,
                    0.0f, call * 4.0f);
            add(RIGHT_UP_LEG, home * -gait * 5.0f + call * -82.0f,
                    0.0f, call * -4.0f);
            add(LEFT_LEG, home * -gait * 2.4f + call * 92.0f, 0.0f, 0.0f);
            add(RIGHT_LEG, home * gait * 2.4f + call * 92.0f, 0.0f, 0.0f);
            add(LEFT_FOOT, call * -8.0f, 0.0f, 0.0f);
            add(RIGHT_FOOT, call * -8.0f, 0.0f, 0.0f);
            bedPose.applyBase(angles, home * (1.0f - walkBlend));
        }

        private void applyPostureLayer(double t, float home, float call) {
            double theta = Math.PI * 2.0 * ((t % 4.0) / 4.0);
            float wave = (float) Math.sin(theta);
            float second = (float) Math.sin(theta * 2.0);
            float callSlow = (float) Math.sin(t * 0.62 + 0.45);
            float callDrift = (float) Math.sin(t * 0.37 + 1.10);
            float homeProcedural = home * (1.0f - walkBlend) * (1.0f - 0.70f * bedPose.activity());
            add(HIPS,
                    homeProcedural * (-2.0f + 0.10f * second),
                    homeProcedural * (-3.5f + 0.12f * wave),
                    homeProcedural * (6.0f + 0.18f * wave));
            add(LEFT_SHOULDER,
                    homeProcedural * (-1.2f - 0.10f * wave - 0.03f * second)
                            + call * (-0.12f + 0.20f * callSlow),
                    0.0f,
                    homeProcedural * (-0.7f - 0.05f * wave)
                            + call * -0.14f * callDrift);
            add(RIGHT_SHOULDER,
                    homeProcedural * (-0.6f - 0.08f * wave + 0.02f * second)
                            + call * (-0.08f - 0.17f * callDrift),
                    0.0f,
                    homeProcedural * (0.5f + 0.04f * wave)
                            + call * 0.12f * callSlow);
            add(SPINE, call * (1.20f + 0.18f * callDrift), 0.0f, 0.0f);
            add(SPINE01, call * (1.60f + 0.24f * callSlow), 0.0f, 0.0f);
            add(SPINE02, call * (1.20f + 0.16f * callDrift), 0.0f, 0.0f);

            float tableLean = home * smoothStep(tableLeanBlend) * (1.0f - walkBlend);
            add(HIPS, tableLean * -3.0f, 0.0f, 0.0f);
            add(SPINE, tableLean * 4.0f, 0.0f, 0.0f);
            add(SPINE01, tableLean * 5.5f, 0.0f, 0.0f);
            add(SPINE02, tableLean * 4.0f, 0.0f, 0.0f);
            add(NECK, tableLean * -1.5f, 0.0f, 0.0f);
            bedPose.applyPosture(angles, home * (1.0f - walkBlend));
        }

        private void applyConversationLayer(long frameTimeNanos, float home, float call) {
            long duration = targetCall ? CALL_ARM_LOOP_NANOS : HOME_ARM_LOOP_NANOS;
            long elapsed = Math.max(0L, frameTimeNanos - armPhaseStartNanos);
            double theta = Math.PI * 2.0
                    * ((double) (elapsed % duration) / (double) duration);

            float leftWave = (float) Math.sin(theta);
            float rightWave = (float) Math.sin(theta + 1.17);
            float leftSecond = (float) Math.sin(theta * 2.0 + 0.35);
            float rightSecond = (float) Math.sin(theta * 2.0 + 1.72);
            float leftThird = (float) Math.sin(theta * 3.0 + 0.20);
            float rightThird = (float) Math.sin(theta * 3.0 + 1.31);
            float leftBreath = (float) Math.sin(theta - 0.45);
            float rightBreath = (float) Math.sin(theta + 0.78);
            float speech =
                    targetCall && avatarState(view) == CelineAvatarController.State.SPEAKING
                            ? speechEnergy(view) : 0.0f;

            float homePresence = home * (1.0f - walkBlend)
                    * (1.0f - 0.62f * bedPose.activity());
            float gait = homeFrame.gait * legacyHomeGaitScale;
            add(LEFT_SHOULDER,
                    homePresence * (-gait * 0.9f + 0.18f * leftBreath), 0.0f, 0.0f);
            add(RIGHT_SHOULDER,
                    homePresence * (gait * 0.9f + 0.16f * rightBreath), 0.0f, 0.0f);

            float leftArmPitchHome =
                    -gait * 2.2f + 2.55f * leftWave + 0.40f * leftSecond;
            float rightArmPitchHome =
                    gait * 2.2f + 2.30f * rightWave + 0.36f * rightSecond;
            float leftArmPitchCall =
                    2.05f * leftWave + 0.52f * leftSecond + speech * 1.05f * leftThird;
            float rightArmPitchCall =
                    1.85f * rightWave + 0.48f * rightSecond + speech * 0.92f * rightThird;

            add(LEFT_ARM,
                    homePresence * leftArmPitchHome + call * leftArmPitchCall,
                    0.0f,
                    homePresence * (29.5f + 1.05f * leftBreath)
                            + call * (30.5f + 0.84f * leftBreath
                            + 0.30f * speech * leftSecond));
            add(RIGHT_ARM,
                    homePresence * rightArmPitchHome + call * rightArmPitchCall,
                    0.0f,
                    homePresence * (-29.5f - 0.96f * rightBreath)
                            + call * (-30.5f - 0.78f * rightBreath
                            - 0.28f * speech * rightSecond));

            add(LEFT_FOREARM,
                    homePresence * (-6.0f + 1.80f * leftSecond + 0.30f * leftThird)
                            + call * (-14.0f + 2.15f * leftSecond
                            + speech * 0.90f * leftThird),
                    0.0f, 0.0f);
            add(RIGHT_FOREARM,
                    homePresence * (-6.0f + 1.65f * rightSecond + 0.28f * rightThird)
                            + call * (-14.0f + 1.95f * rightSecond
                            + speech * 0.82f * rightThird),
                    0.0f, 0.0f);
            add(LEFT_HAND,
                    homePresence * (3.20f * leftWave + 0.82f * leftSecond)
                            + call * (3.05f * leftWave + 0.76f * leftSecond
                            + speech * 1.10f * leftThird),
                    0.0f,
                    homePresence * (1.45f * leftSecond + 0.32f * leftThird)
                            + call * (1.38f * leftSecond + 0.28f * leftThird
                            + speech * 0.45f * leftThird));
            add(RIGHT_HAND,
                    homePresence * (2.95f * rightWave + 0.76f * rightSecond)
                            + call * (2.82f * rightWave + 0.70f * rightSecond
                            + speech * 1.00f * rightThird),
                    0.0f,
                    homePresence * (1.34f * rightSecond + 0.30f * rightThird)
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

        private void applyGazeLayer(long frameTimeNanos, float deltaSeconds,
                                    double t, float home, float call) {
            if (probeModel) {
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

            CelineAvatarController.State state = avatarState(view);
            updateSocialGaze(frameTimeNanos, deltaSeconds, state);
            float lookX = view.v76LookActive()
                    ? clamp(view.v76LookX(), -MAX_SOCIAL_GAZE_X, MAX_SOCIAL_GAZE_X)
                    : socialGazeX;
            float lookY = view.v76LookActive()
                    ? clamp(view.v76LookY(), -MAX_SOCIAL_GAZE_Y, MAX_SOCIAL_GAZE_Y)
                    : socialGazeY;

            float independentYaw = 0.14f * (float) Math.sin(t * 0.31 + 0.4)
                    + 0.07f * (float) Math.sin(t * 0.17 + 1.3);
            float independentPitch = 0.09f * (float) Math.sin(t * 0.27 + 0.8)
                    + 0.04f * (float) Math.sin(t * 0.11 + 2.0);
            float independentRoll = 0.06f * (float) Math.sin(t * 0.21 + 1.1);
            float speech = speechEnergy(view);
            float nodEnvelope = state == CelineAvatarController.State.SPEAKING
                    ? pow4(Math.max(0.0f, (float) Math.sin(t * 0.61 + 0.5))) : 0.0f;
            float homeNod = (float) Math.sin(t * 3.2 + 0.2)
                    * (0.12f + 0.28f * speech) * nodEnvelope;
            float callNod = (float) Math.sin(t * 3.0 + 0.6)
                    * (0.10f + 0.22f * speech) * nodEnvelope;
            float listenTilt =
                    state == CelineAvatarController.State.LISTENING ? 0.20f : 0.0f;

            add(NECK,
                    home * (independentPitch * 0.28f + lookY * 2.7f)
                            + call * (independentPitch * 0.32f + lookY * 3.1f),
                    home * (independentYaw * 0.30f + lookX * 4.0f)
                            + call * (independentYaw * 0.34f + lookX * 4.5f),
                    home * (independentRoll * 0.25f + listenTilt * 0.22f)
                            + call * (independentRoll * 0.28f + listenTilt * 0.18f));
            add(HEAD,
                    home * (independentPitch + lookY * 8.5f + homeNod)
                            + call * (independentPitch * 0.90f + lookY * 7.5f + callNod),
                    home * (independentYaw + lookX * 13.5f)
                            + call * (independentYaw * 0.92f + lookX * 12.0f),
                    home * (independentRoll + listenTilt)
                            + call * (independentRoll * 0.86f + listenTilt * 0.82f));

            if (home > 0.98f && !loggedBlock6Home) {
                loggedBlock6Home = true;
                Celine3DDiagnostics.record(view.getContext(), "V80-460",
                        "Block 6 HOME Social Presence aktiv",
                        "cameraAttention=true microSaccades=true headNeckFollow=true"
                                + " stateAware=true constantBobbing=false independentWriter=false");
            }
            if (call > 0.98f && !loggedBlock6Call) {
                loggedBlock6Call = true;
                Celine3DDiagnostics.record(view.getContext(), "V80-461",
                        "Block 6 CALL Social Presence aktiv",
                        "cameraAttention=true microSaccades=true headNeckFollow=true"
                                + " stateAware=true gazeX<=0.12 gazeY<=0.08 independentWriter=false");
            }
        }

        private void updateSocialGaze(long frameTimeNanos, float deltaSeconds,
                                      CelineAvatarController.State state) {
            if (nextGazeShiftNanos == 0L) {
                nextGazeShiftNanos = frameTimeNanos + 1_800_000_000L;
            }
            if (frameTimeNanos >= nextGazeShiftNanos) {
                gazeShiftSerial++;
                int cadence;
                float centerAmplitude;
                float glanceAmplitude;
                float verticalAmplitude;
                float verticalCenter;
                long baseDelayMs;
                long delayRangeMs;
                switch (state) {
                    case LISTENING:
                        cadence = 7;
                        centerAmplitude = 0.018f;
                        glanceAmplitude = 0.060f;
                        verticalAmplitude = 0.014f;
                        verticalCenter = -0.012f;
                        baseDelayMs = 2900L;
                        delayRangeMs = 1500L;
                        break;
                    case THINKING:
                        cadence = 3;
                        centerAmplitude = 0.040f;
                        glanceAmplitude = 0.110f;
                        verticalAmplitude = 0.032f;
                        verticalCenter = -0.030f;
                        baseDelayMs = 1750L;
                        delayRangeMs = 1250L;
                        break;
                    case SPEAKING:
                        cadence = 5;
                        centerAmplitude = 0.030f;
                        glanceAmplitude = 0.082f;
                        verticalAmplitude = 0.022f;
                        verticalCenter = -0.010f;
                        baseDelayMs = 2200L;
                        delayRangeMs = 1450L;
                        break;
                    case IDLE:
                    default:
                        cadence = 6;
                        centerAmplitude = 0.034f;
                        glanceAmplitude = 0.075f;
                        verticalAmplitude = 0.024f;
                        verticalCenter = -0.006f;
                        baseDelayMs = 2750L;
                        delayRangeMs = 1700L;
                        break;
                }
                boolean briefGlance = gazeShiftSerial % cadence == 0;
                float horizontalAmplitude = briefGlance ? glanceAmplitude : centerAmplitude;
                socialGazeTargetX = clamp(
                        deterministicSigned(gazeShiftSerial, 17) * horizontalAmplitude,
                        -MAX_SOCIAL_GAZE_X, MAX_SOCIAL_GAZE_X);
                socialGazeTargetY = clamp(
                        verticalCenter + deterministicSigned(gazeShiftSerial, 53) * verticalAmplitude,
                        -MAX_SOCIAL_GAZE_Y, MAX_SOCIAL_GAZE_Y);
                long delayMs = briefGlance
                        ? 1050L + (long) (deterministicUnit(gazeShiftSerial, 71) * 650.0f)
                        : baseDelayMs
                        + (long) (deterministicUnit(gazeShiftSerial, 89) * delayRangeMs);
                nextGazeShiftNanos = frameTimeNanos + delayMs * 1_000_000L;
            }

            float ease = Math.min(1.0f, deltaSeconds * 5.2f);
            socialGazeX += (socialGazeTargetX - socialGazeX) * ease;
            socialGazeY += (socialGazeTargetY - socialGazeY) * ease;
        }

        private static float deterministicSigned(int serial, int salt) {
            return deterministicUnit(serial, salt) * 2.0f - 1.0f;
        }

        private static float deterministicUnit(int serial, int salt) {
            long value = serial * 1_103_515_245L + salt * 12_345L + 0x9E3779B9L;
            value ^= value >>> 16;
            value *= 0x45D9F3BL;
            value ^= value >>> 16;
            return (value & 0x7FFFFFFFL) / 2_147_483_647.0f;
        }

        private static float pow4(float value) {
            float square = value * value;
            return square * square;
        }

        private void applyRoot(float home, float call) {
            boolean roomOwnsRoot = roomMotion != RoomMotion.AMBIENT;
            float floorMix = smoothStep(roomFloorBlend);
            float bedActivity = bedPose.activity();
            float homeX = roomOwnsRoot ? roomX + bedPose.rootX() : homeFrame.x;
            float homeZ = roomOwnsRoot ? roomZ + bedPose.rootZ() : homeFrame.z;
            float homeYaw = roomOwnsRoot ? roomYaw : homeFrame.yaw;
            float homeBob = roomOwnsRoot
                    ? (homeFrame.bob + (walkBob - homeFrame.bob) * floorMix)
                    * (1.0f - 0.92f * bedActivity)
                    : homeFrame.bob;
            float roomFloorY = roomOwnsRoot
                    ? roomFloorCorrectionY * floorMix + bedPose.rootY() : 0.0f;

            float x = (layerMask & LAYER_BASE) == 0 ? 0.0f : home * homeX;
            float y = (layerMask & LAYER_BASE) == 0 ? 0.0f
                    : home * (homeBob + roomFloorY) + call * CALL_ROOT_DOWN;
            float z = (layerMask & LAYER_BASE) == 0 ? 0.0f
                    : home * homeZ + call * CALL_ROOT_FORWARD;
            float yaw = (layerMask & LAYER_BASE) == 0 ? 0.0f : home * homeYaw;
            float bedPitch = (layerMask & LAYER_BASE) == 0 ? 0.0f
                    : home * bedPose.rootPitch();
            float bedRoll = (layerMask & LAYER_BASE) == 0 ? 0.0f
                    : home * bedPose.rootRoll();

            float[] localRotation = new float[16];
            float[] rotated = new float[16];
            float[] worldMove = new float[16];
            float[] out = new float[16];
            Matrix.setIdentityM(localRotation, 0);
            if (yaw != 0.0f) {
                Matrix.rotateM(localRotation, 0, yaw, 0.0f, 1.0f, 0.0f);
            }
            if (bedPitch != 0.0f) {
                Matrix.rotateM(localRotation, 0, bedPitch, 1.0f, 0.0f, 0.0f);
            }
            if (bedRoll != 0.0f) {
                Matrix.rotateM(localRotation, 0, bedRoll, 0.0f, 0.0f, 1.0f);
            }
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

            float[] procedural = new float[16];
            Matrix.setIdentityM(procedural, 0);
            if (yaw != 0.0f) {
                Matrix.rotateM(procedural, 0, yaw, 0.0f, 1.0f, 0.0f);
            }
            if (pitch != 0.0f) {
                Matrix.rotateM(procedural, 0, pitch, 1.0f, 0.0f, 0.0f);
            }
            if (roll != 0.0f) {
                Matrix.rotateM(procedural, 0, roll, 0.0f, 0.0f, 1.0f);
            }

            int qo = index * 4;
            float[] q = {
                    locomotionQuats[qo], locomotionQuats[qo + 1],
                    locomotionQuats[qo + 2], locomotionQuats[qo + 3]
            };
            float[] locomotion = new float[16];
            float[] withLocomotion = new float[16];
            float[] out = new float[16];
            CelineWalkingClipV9R.quaternionMatrix(q, locomotion);
            Matrix.multiplyMM(withLocomotion, 0, bone.base, 0, locomotion, 0);
            Matrix.multiplyMM(out, 0, withLocomotion, 0, procedural, 0);
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
                try {
                    transforms.commitLocalTransformTransaction();
                } catch (Throwable ignored) {
                }
            }
            try {
                animator.updateBoneMatrices();
            } catch (Throwable ignored) {
            }
        }

        private Bone bone(FilamentAsset asset, String name) {
            try {
                int entity = asset.getFirstEntityByName(name);
                if (entity == 0 && "neck".equals(name)) {
                    entity = asset.getFirstEntityByName("Neck");
                }
                if (entity == 0) return null;
                int instance = transforms.getInstance(entity);
                if (instance == 0) return null;
                return new Bone(instance,
                        transforms.getTransform(instance, new float[16]));
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    private static CelineAvatarController.State avatarState(Celine3DView view) {
        try {
            Object value = field(view, "avatarState");
            if (value instanceof CelineAvatarController.State) {
                return (CelineAvatarController.State) value;
            }
        } catch (Throwable ignored) {
        }
        return CelineAvatarController.State.IDLE;
    }

    private static float speechEnergy(Celine3DView view) {
        try {
            Object value = field(view, "speechEnergy");
            if (value instanceof Number) {
                return clamp(((Number) value).floatValue(), 0.0f, 1.0f);
            }
        } catch (Throwable ignored) {
        }
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

    private static float approach(float value, float target, float maxDelta) {
        if (value < target) return Math.min(target, value + Math.max(0.0f, maxDelta));
        return Math.max(target, value - Math.max(0.0f, maxDelta));
    }

    private static float smoothStep(float value) {
        float bounded = clamp(value, 0.0f, 1.0f);
        return bounded * bounded * (3.0f - 2.0f * bounded);
    }

    private static float approachAngle(float value, float target, float maxDelta) {
        float delta = wrapDegrees(target - value);
        float bounded = clamp(delta, -Math.max(0.0f, maxDelta), Math.max(0.0f, maxDelta));
        return wrapDegrees(value + bounded);
    }

    private static float angleDistance(float a, float b) {
        return Math.abs(wrapDegrees(b - a));
    }

    private static float wrapDegrees(float degrees) {
        float out = degrees % 360.0f;
        if (out > 180.0f) out -= 360.0f;
        if (out < -180.0f) out += 360.0f;
        return out;
    }

    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }
}
