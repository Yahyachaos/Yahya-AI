package de.yahya.ai;

/**
 * 9R.3/9R.4 furniture contact plus bounded 9R.5 destination pose contribution for the central
 * v80 production owner.
 *
 * This class never writes Filament transforms. It only owns bounded pose/root targets which
 * CelineProductionPresenceV80 mixes into its existing single transform transaction.
 *
 * The accepted 9R.3 bed, 9R.4 chair, 9R.5 window and 9R.5 dresser paths remain unchanged.
 * Later 9R.5 destinations are added one at a time; mirror is the current bounded interaction.
 */
final class CelineBedPoseV9R3 {
    private static final String BED_APPROACH = "bed_approach_anchor";
    private static final String BED_EDGE = "bed_edge_sit_anchor";
    private static final String BED_RELAX = "bed_relax_anchor";
    private static final String BED_LIE = "bed_lie_anchor";
    private static final String BED_EXIT = "bed_exit_anchor";
    private static final String CHAIR_SIT = "chair_sit_anchor";
    private static final String WINDOW = "window_anchor";
    private static final String DRESSER = "dresser_anchor";
    private static final String MIRROR = "mirror_anchor";

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
    private static final int LEFT_UP_LEG = 14;
    private static final int RIGHT_UP_LEG = 15;
    private static final int LEFT_LEG = 16;
    private static final int RIGHT_LEG = 17;
    private static final int LEFT_FOOT = 18;
    private static final int RIGHT_FOOT = 19;

    private static final float POSE_BLEND_PER_SECOND = 1.85f;
    private static final float ROOT_BLEND_PER_SECOND = 1.55f;
    private static final float WINDOW_BLEND_PER_SECOND = 0.90f;
    private static final float DRESSER_BLEND_PER_SECOND = 1.05f;
    private static final float MIRROR_BLEND_PER_SECOND = 1.05f;

    // Production rig pelvis height above the calibrated standing sole plane.
    private static final float STANDING_PELVIS_HEIGHT_M = 0.88f;
    // Accepted 9R.3 bed calibration. Keep these values unchanged while extending later blocks.
    private static final float EDGE_MATTRESS_INSET_X_M = 0.34f;
    private static final float RELAX_MATTRESS_INSET_X_M = 0.16f;
    private static final float LIE_MATTRESS_INSET_X_M = -0.10f;
    private static final float RELAX_SUPPORT_DROP_M = 0.055f;
    private static final float LIE_SUPPORT_DROP_M = 0.110f;
    private static final float RELAX_ROOT_PITCH_DEG = -34.0f;
    private static final float LIE_ROOT_PITCH_DEG = -82.0f;

    // Accepted 9R.4 final-mesh contact calibration. Keep immutable unless new independent evidence
    // explicitly reopens the protected chair block.
    private static final float CHAIR_SUPPORT_RISE_M = 0.025f;
    private static final float CHAIR_FINAL_MESH_X_CALIBRATION_M = 0.00f;
    private static final float CHAIR_FINAL_MESH_FORWARD_Z_M = 1.14f;
    private static final float CHAIR_TURN_BLEND_PER_SECOND = 0.62f;
    private static final float CHAIR_SIT_BLEND_PER_SECOND = 1.35f;
    private static final float CHAIR_STAND_BLEND_PER_SECOND = 3.80f;
    private static final float CHAIR_UNTURN_BLEND_PER_SECOND = 0.72f;

    // Accepted 9R.5 Window calibration. Proof #143 is the protected visual acceptance evidence.
    private static final float WINDOW_REACQUIRE_DELAY_SECONDS = 1.40f;
    private static final float WINDOW_REACQUIRE_RAMP_SECONDS = 0.55f;
    private static final float WINDOW_SPINE_YAW_DEG = -7.0f;
    private static final float WINDOW_SPINE01_YAW_DEG = -6.0f;
    private static final float WINDOW_SPINE02_YAW_DEG = -5.0f;
    private static final float WINDOW_OUTWARD_NECK_YAW_DEG = -18.0f;
    private static final float WINDOW_OUTWARD_HEAD_YAW_DEG = -52.0f;
    private static final float WINDOW_USER_NECK_YAW_DEG = 8.0f;
    private static final float WINDOW_USER_HEAD_YAW_DEG = 18.0f;

    // Accepted 9R.5 Dresser calibration from Proof #152. Keep immutable while Mirror is added.
    private static final float DRESSER_REACQUIRE_DELAY_SECONDS = 1.35f;
    private static final float DRESSER_REACQUIRE_RAMP_SECONDS = 0.55f;
    private static final float DRESSER_INSPECT_NECK_PITCH_DEG = 4.0f;
    private static final float DRESSER_INSPECT_HEAD_PITCH_DEG = 8.0f;
    private static final float DRESSER_USER_SPINE02_YAW_DEG = -8.0f;
    private static final float DRESSER_USER_NECK_YAW_DEG = -22.0f;
    private static final float DRESSER_USER_HEAD_YAW_DEG = -50.0f;

    // 9R.5 Mirror is an interaction_step at the accepted Dresser root. No hand contact and no
    // reflection pipeline are required by the locked room contract. Keep the whole body facing the
    // round mirror, add one small face-height head-tilt/check gesture, then make the same bounded
    // side-to-user re-acquisition that is already visually coherent at this root. HIPS/legs stay
    // untouched so the accepted grounded stance cannot be disturbed.
    private static final float MIRROR_REACQUIRE_DELAY_SECONDS = 1.50f;
    private static final float MIRROR_REACQUIRE_RAMP_SECONDS = 0.55f;
    private static final float MIRROR_CHECK_NECK_PITCH_DEG = -1.5f;
    private static final float MIRROR_CHECK_HEAD_PITCH_DEG = -2.5f;
    private static final float MIRROR_CHECK_HEAD_ROLL_DEG = 4.0f;
    private static final float MIRROR_USER_SPINE02_YAW_DEG = -8.0f;
    private static final float MIRROR_USER_NECK_YAW_DEG = -22.0f;
    private static final float MIRROR_USER_HEAD_YAW_DEG = -50.0f;

    private final float edgeSeatRootY;
    private final float edgeOffsetX;
    private final float edgeOffsetZ;
    private final float relaxOffsetX;
    private final float relaxOffsetZ;
    private final float lieOffsetX;
    private final float lieOffsetZ;
    private final float exitOffsetX;
    private final float exitOffsetZ;
    private final float chairSeatRootY;
    private final float chairOffsetX;
    private final float chairOffsetZ;

    private float edgeBlend;
    private float relaxBlend;
    private float lieBlend;
    private float chairTurnBlend;
    private float chairSitBlend;
    private float windowBlend;
    private float windowHoldSeconds;
    private float dresserBlend;
    private float dresserHoldSeconds;
    private float mirrorBlend;
    private float mirrorHoldSeconds;
    private float rootX;
    private float rootY;
    private float rootZ;
    private float rootPitch;
    private float rootRoll;
    private float targetRootX;
    private float targetRootY;
    private float targetRootZ;
    private float targetRootPitch;
    private float targetRootRoll;
    private String requestedAnchor = BED_APPROACH;

    CelineBedPoseV9R3(CelineRoomWorldContractV80 world) {
        CelineRoomWorldContractV80.Anchor approach = require(world, BED_APPROACH);
        CelineRoomWorldContractV80.Anchor edge = require(world, BED_EDGE);
        CelineRoomWorldContractV80.Anchor relax = require(world, BED_RELAX);
        CelineRoomWorldContractV80.Anchor lie = require(world, BED_LIE);
        CelineRoomWorldContractV80.Anchor exit = require(world, BED_EXIT);
        CelineRoomWorldContractV80.Anchor chairApproach = require(world, "chair_approach_anchor");
        CelineRoomWorldContractV80.Anchor chairSit = require(world, CHAIR_SIT);
        require(world, WINDOW);
        require(world, DRESSER);
        require(world, MIRROR);

        edgeSeatRootY = world.bedMattressY - STANDING_PELVIS_HEIGHT_M;
        edgeOffsetX = edge.localX - approach.localX;
        edgeOffsetZ = edge.localZ - approach.localZ;
        relaxOffsetX = relax.localX - approach.localX;
        relaxOffsetZ = relax.localZ - approach.localZ;
        lieOffsetX = lie.localX - approach.localX;
        lieOffsetZ = lie.localZ - approach.localZ;
        exitOffsetX = exit.localX - approach.localX;
        exitOffsetZ = exit.localZ - approach.localZ;

        chairSeatRootY = world.chairSeatY - STANDING_PELVIS_HEIGHT_M + CHAIR_SUPPORT_RISE_M;
        chairOffsetX = chairSit.localX - chairApproach.localX
                + CHAIR_FINAL_MESH_X_CALIBRATION_M;
        chairOffsetZ = chairSit.localZ - chairApproach.localZ
                + CHAIR_FINAL_MESH_FORWARD_Z_M;
    }

    void update(String anchorId, float deltaSeconds, boolean enabled) {
        requestedAnchor = enabled && anchorId != null ? anchorId : BED_APPROACH;
        boolean chairTarget = enabled && CHAIR_SIT.equals(anchorId);
        boolean windowTarget = enabled && WINDOW.equals(anchorId);
        boolean dresserTarget = enabled && DRESSER.equals(anchorId);
        boolean mirrorTarget = enabled && MIRROR.equals(anchorId);
        float edgeTarget = enabled && BED_EDGE.equals(anchorId) ? 1.0f : 0.0f;
        float relaxTarget = enabled && BED_RELAX.equals(anchorId) ? 1.0f : 0.0f;
        float lieTarget = enabled && BED_LIE.equals(anchorId) ? 1.0f : 0.0f;
        float dt = Math.max(0.0f, deltaSeconds);
        float poseStep = dt * POSE_BLEND_PER_SECOND;
        edgeBlend = approach(edgeBlend, edgeTarget, poseStep);
        relaxBlend = approach(relaxBlend, relaxTarget, poseStep);
        lieBlend = approach(lieBlend, lieTarget, poseStep);
        windowBlend = approach(windowBlend, windowTarget ? 1.0f : 0.0f,
                dt * WINDOW_BLEND_PER_SECOND);
        if (windowTarget && windowBlend >= 0.985f) {
            windowHoldSeconds += dt;
        } else {
            windowHoldSeconds = 0.0f;
        }
        dresserBlend = approach(dresserBlend, dresserTarget ? 1.0f : 0.0f,
                dt * DRESSER_BLEND_PER_SECOND);
        if (dresserTarget && dresserBlend >= 0.985f) {
            dresserHoldSeconds += dt;
        } else {
            dresserHoldSeconds = 0.0f;
        }
        mirrorBlend = approach(mirrorBlend, mirrorTarget ? 1.0f : 0.0f,
                dt * MIRROR_BLEND_PER_SECOND);
        if (mirrorTarget && mirrorBlend >= 0.985f) {
            mirrorHoldSeconds += dt;
        } else {
            mirrorHoldSeconds = 0.0f;
        }

        if (chairTarget) {
            chairTurnBlend = approach(
                    chairTurnBlend, 1.0f, dt * CHAIR_TURN_BLEND_PER_SECOND);
            if (chairTurnBlend >= 0.965f) {
                chairSitBlend = approach(
                        chairSitBlend, 1.0f, dt * CHAIR_SIT_BLEND_PER_SECOND);
            }
        } else {
            // Stand first, then let the central root return toward the approach orientation.
            chairSitBlend = approach(
                    chairSitBlend, 0.0f, dt * CHAIR_STAND_BLEND_PER_SECOND);
            if (chairSitBlend <= 0.035f) {
                chairTurnBlend = approach(
                        chairTurnBlend, 0.0f, dt * CHAIR_UNTURN_BLEND_PER_SECOND);
            }
        }

        targetRootX = 0.0f;
        targetRootY = 0.0f;
        targetRootZ = 0.0f;
        targetRootPitch = 0.0f;
        targetRootRoll = 0.0f;
        if (enabled && BED_EDGE.equals(anchorId)) {
            targetRootX = edgeOffsetX + EDGE_MATTRESS_INSET_X_M;
            targetRootY = edgeSeatRootY;
            targetRootZ = edgeOffsetZ;
        } else if (enabled && BED_RELAX.equals(anchorId)) {
            targetRootPitch = RELAX_ROOT_PITCH_DEG;
            targetRootX = relaxOffsetX + RELAX_MATTRESS_INSET_X_M
                    + pelvisPivotCompensationX(targetRootPitch);
            targetRootY = edgeSeatRootY - RELAX_SUPPORT_DROP_M
                    + pelvisPivotCompensationY(targetRootPitch);
            targetRootZ = relaxOffsetZ;
        } else if (enabled && BED_LIE.equals(anchorId)) {
            targetRootPitch = LIE_ROOT_PITCH_DEG;
            targetRootX = lieOffsetX + LIE_MATTRESS_INSET_X_M
                    + pelvisPivotCompensationX(targetRootPitch);
            targetRootY = edgeSeatRootY - LIE_SUPPORT_DROP_M
                    + pelvisPivotCompensationY(targetRootPitch);
            targetRootZ = lieOffsetZ;
        } else if (enabled && BED_EXIT.equals(anchorId)) {
            targetRootX = exitOffsetX;
            targetRootZ = exitOffsetZ;
        } else if (chairTarget) {
            float seat = smooth(chairSitBlend);
            targetRootX = chairOffsetX * seat;
            targetRootY = chairSeatRootY * seat;
            targetRootZ = chairOffsetZ * seat;
        }

        float rootStep = dt * ROOT_BLEND_PER_SECOND;
        rootX = approach(rootX, targetRootX, rootStep * 0.65f);
        rootY = approach(rootY, targetRootY, rootStep * 0.55f);
        rootZ = approach(rootZ, targetRootZ, rootStep * 0.65f);
        rootPitch = approach(rootPitch, targetRootPitch, rootStep * 70.0f);
        rootRoll = approach(rootRoll, targetRootRoll, rootStep * 18.0f);
    }

    void applyBase(float[] angles, float home) {
        float edge = home * smooth(edgeBlend);
        float relax = home * smooth(relaxBlend);
        float lie = home * smooth(lieBlend);
        float chair = home * smooth(chairSitBlend);

        // Accepted 9R.3 bed contribution.
        add(angles, HIPS, edge * -4.0f + relax * -2.0f, 0.0f, 0.0f);
        add(angles, LEFT_UP_LEG, edge * -78.0f + relax * -50.0f + lie * -1.0f,
                0.0f, edge * 4.0f + relax * 3.0f);
        add(angles, RIGHT_UP_LEG, edge * -74.0f + relax * -47.0f + lie * -1.0f,
                0.0f, edge * -5.0f + relax * -3.0f);
        add(angles, LEFT_LEG, edge * 88.0f + relax * 60.0f + lie * 2.0f, 0.0f, 0.0f);
        add(angles, RIGHT_LEG, edge * 84.0f + relax * 57.0f + lie * 2.0f, 0.0f, 0.0f);
        add(angles, LEFT_FOOT, edge * -9.0f + relax * -5.0f, 0.0f, 0.0f);
        add(angles, RIGHT_FOOT, edge * -8.0f + relax * -4.0f, 0.0f, 0.0f);

        // Accepted 9R.4 chair contribution.
        add(angles, HIPS, chair * -5.0f, 0.0f, chair * 1.5f);
        add(angles, LEFT_UP_LEG, chair * -76.0f, 0.0f, chair * 5.0f);
        add(angles, RIGHT_UP_LEG, chair * -73.0f, 0.0f, chair * -5.0f);
        add(angles, LEFT_LEG, chair * 86.0f, 0.0f, 0.0f);
        add(angles, RIGHT_LEG, chair * 83.0f, 0.0f, 0.0f);
        add(angles, LEFT_FOOT, chair * -7.0f, 0.0f, 0.0f);
        add(angles, RIGHT_FOOT, chair * -7.0f, 0.0f, 0.0f);

        // 9R.5 standing destinations intentionally never alter HIPS/legs. Accepted locomotion owns
        // foot placement while central room yaw owns whole-body destination facing.
    }

    void applyPosture(float[] angles, float home) {
        float edge = home * smooth(edgeBlend);
        float relax = home * smooth(relaxBlend);
        float lie = home * smooth(lieBlend);
        float chair = home * smooth(chairSitBlend);
        float window = home * smooth(windowBlend);
        float dresser = home * smooth(dresserBlend);
        float mirror = home * smooth(mirrorBlend);

        // Accepted 9R.3 bed contribution.
        add(angles, SPINE, edge * 3.0f + relax * 2.0f + lie * 1.0f, 0.0f, 0.0f);
        add(angles, SPINE01, edge * 4.0f + relax * 3.0f + lie * 1.5f, 0.0f, 0.0f);
        add(angles, SPINE02, edge * 3.0f + relax * 2.0f + lie * 1.0f, 0.0f, 0.0f);
        add(angles, NECK, relax * 2.0f + lie * 4.0f, lie * -2.0f, 0.0f);
        add(angles, LEFT_SHOULDER, 0.0f, 0.0f, relax * -3.0f + lie * -5.0f);
        add(angles, RIGHT_SHOULDER, 0.0f, 0.0f, relax * 3.0f + lie * 5.0f);
        add(angles, LEFT_ARM, relax * -14.0f + lie * -7.0f, 0.0f,
                relax * 12.0f + lie * 12.0f);
        add(angles, RIGHT_ARM, relax * -12.0f + lie * -6.0f, 0.0f,
                relax * -12.0f + lie * -12.0f);
        add(angles, LEFT_FOREARM, relax * -18.0f + lie * -5.0f, 0.0f, 0.0f);
        add(angles, RIGHT_FOREARM, relax * -16.0f + lie * -5.0f, 0.0f, 0.0f);

        // Accepted 9R.4 relaxed chair hold.
        add(angles, SPINE, chair * 4.0f, 0.0f, chair * 1.0f);
        add(angles, SPINE01, chair * 6.0f, 0.0f, chair * 0.8f);
        add(angles, SPINE02, chair * 4.5f, 0.0f, chair * 0.5f);
        add(angles, NECK, chair * -1.5f, 0.0f, 0.0f);
        add(angles, LEFT_SHOULDER, chair * -1.5f, 0.0f, chair * -3.0f);
        add(angles, RIGHT_SHOULDER, chair * -1.0f, 0.0f, chair * 3.0f);
        add(angles, LEFT_ARM, chair * -10.0f, 0.0f, chair * 18.0f);
        add(angles, RIGHT_ARM, chair * -9.0f, 0.0f, chair * -18.0f);
        add(angles, LEFT_FOREARM, chair * -20.0f, 0.0f, 0.0f);
        add(angles, RIGHT_FOREARM, chair * -18.0f, 0.0f, 0.0f);

        // Accepted 9R.5 window behavior from Proof #143.
        add(angles, SPINE, 0.0f, window * WINDOW_SPINE_YAW_DEG, 0.0f);
        add(angles, SPINE01, 0.0f, window * WINDOW_SPINE01_YAW_DEG, 0.0f);
        add(angles, SPINE02, 0.0f, window * WINDOW_SPINE02_YAW_DEG, 0.0f);
        float windowReacquireProgress = clamp(
                (windowHoldSeconds - WINDOW_REACQUIRE_DELAY_SECONDS)
                        / WINDOW_REACQUIRE_RAMP_SECONDS,
                0.0f, 1.0f);
        float windowReacquire = window * smooth(windowReacquireProgress);
        float windowOutward = window * (1.0f - smooth(windowReacquireProgress));
        add(angles, NECK,
                windowOutward * -1.0f + windowReacquire * 0.5f,
                windowOutward * WINDOW_OUTWARD_NECK_YAW_DEG
                        + windowReacquire * WINDOW_USER_NECK_YAW_DEG,
                0.0f);
        add(angles, HEAD,
                windowOutward * -2.0f + windowReacquire * 1.5f,
                windowOutward * WINDOW_OUTWARD_HEAD_YAW_DEG
                        + windowReacquire * WINDOW_USER_HEAD_YAW_DEG,
                0.0f);

        // Accepted 9R.5 dresser: root facing already points toward the sideboard. Keep that stable
        // for a brief decor/top inspection, then return only upper spine/head/neck toward the user.
        float dresserReacquireProgress = clamp(
                (dresserHoldSeconds - DRESSER_REACQUIRE_DELAY_SECONDS)
                        / DRESSER_REACQUIRE_RAMP_SECONDS,
                0.0f, 1.0f);
        float dresserReacquire = dresser * smooth(dresserReacquireProgress);
        float dresserInspect = dresser * (1.0f - smooth(dresserReacquireProgress));
        add(angles, SPINE02, 0.0f,
                dresserReacquire * DRESSER_USER_SPINE02_YAW_DEG, 0.0f);
        add(angles, NECK,
                dresserInspect * DRESSER_INSPECT_NECK_PITCH_DEG,
                dresserReacquire * DRESSER_USER_NECK_YAW_DEG,
                0.0f);
        add(angles, HEAD,
                dresserInspect * DRESSER_INSPECT_HEAD_PITCH_DEG,
                dresserReacquire * DRESSER_USER_HEAD_YAW_DEG,
                0.0f);

        // 9R.5 mirror: keep the accepted Dresser root/facing, look at face height with a restrained
        // head tilt as the bounded mirror-check gesture, then clearly re-acquire the fixed-camera
        // user. No shoulder/arm/hand contribution is added, avoiding self/furniture intersection.
        float mirrorReacquireProgress = clamp(
                (mirrorHoldSeconds - MIRROR_REACQUIRE_DELAY_SECONDS)
                        / MIRROR_REACQUIRE_RAMP_SECONDS,
                0.0f, 1.0f);
        float mirrorReacquire = mirror * smooth(mirrorReacquireProgress);
        float mirrorCheck = mirror * (1.0f - smooth(mirrorReacquireProgress));
        add(angles, SPINE02, 0.0f,
                mirrorReacquire * MIRROR_USER_SPINE02_YAW_DEG, 0.0f);
        add(angles, NECK,
                mirrorCheck * MIRROR_CHECK_NECK_PITCH_DEG,
                mirrorReacquire * MIRROR_USER_NECK_YAW_DEG,
                0.0f);
        add(angles, HEAD,
                mirrorCheck * MIRROR_CHECK_HEAD_PITCH_DEG,
                mirrorReacquire * MIRROR_USER_HEAD_YAW_DEG,
                mirrorCheck * MIRROR_CHECK_HEAD_ROLL_DEG);
    }

    float activity() {
        return clamp(Math.max(
                Math.max(edgeBlend, Math.max(relaxBlend, lieBlend)),
                Math.max(Math.max(chairSitBlend, chairTurnBlend * 0.45f),
                        Math.max(Math.max(windowBlend, dresserBlend), mirrorBlend))),
                0.0f, 1.0f);
    }

    float rootX() { return rootX; }
    float rootY() { return rootY; }
    float rootZ() { return rootZ; }
    float rootPitch() { return rootPitch; }
    float rootRoll() { return rootRoll; }

    /**
     * Compatibility name retained because the accepted central owner already calls it. It now
     * means any bounded destination/contact pose contribution owned through this helper.
     */
    boolean isBedAnchor(String id) {
        return BED_EDGE.equals(id) || BED_RELAX.equals(id) || BED_LIE.equals(id)
                || BED_EXIT.equals(id) || CHAIR_SIT.equals(id) || WINDOW.equals(id)
                || DRESSER.equals(id) || MIRROR.equals(id);
    }

    boolean settled(String anchorId) {
        if (anchorId == null || !anchorId.equals(requestedAnchor)) return false;
        float targetEdge = BED_EDGE.equals(anchorId) ? 1.0f : 0.0f;
        float targetRelax = BED_RELAX.equals(anchorId) ? 1.0f : 0.0f;
        float targetLie = BED_LIE.equals(anchorId) ? 1.0f : 0.0f;
        float targetChairTurn = CHAIR_SIT.equals(anchorId) ? 1.0f : 0.0f;
        float targetChairSit = CHAIR_SIT.equals(anchorId) ? 1.0f : 0.0f;
        float targetWindow = WINDOW.equals(anchorId) ? 1.0f : 0.0f;
        float targetDresser = DRESSER.equals(anchorId) ? 1.0f : 0.0f;
        float targetMirror = MIRROR.equals(anchorId) ? 1.0f : 0.0f;
        return Math.abs(edgeBlend - targetEdge) < 0.025f
                && Math.abs(relaxBlend - targetRelax) < 0.025f
                && Math.abs(lieBlend - targetLie) < 0.025f
                && Math.abs(chairTurnBlend - targetChairTurn) < 0.025f
                && Math.abs(chairSitBlend - targetChairSit) < 0.025f
                && Math.abs(windowBlend - targetWindow) < 0.025f
                && Math.abs(dresserBlend - targetDresser) < 0.025f
                && Math.abs(mirrorBlend - targetMirror) < 0.025f
                && Math.abs(rootX - targetRootX) < 0.020f
                && Math.abs(rootY - targetRootY) < 0.020f
                && Math.abs(rootZ - targetRootZ) < 0.020f
                && Math.abs(rootPitch - targetRootPitch) < 1.5f
                && Math.abs(rootRoll - targetRootRoll) < 0.75f;
    }

    String poseName(String anchorId) {
        if (BED_EDGE.equals(anchorId)) return "BED_EDGE_SIT";
        if (BED_RELAX.equals(anchorId)) return "BED_RELAX";
        if (BED_LIE.equals(anchorId)) return "BED_LIE";
        if (BED_EXIT.equals(anchorId)) return "STAND_EXIT";
        if (CHAIR_SIT.equals(anchorId)) return "CHAIR_SIT";
        if (WINDOW.equals(anchorId)) return "WINDOW_STAND";
        if (DRESSER.equals(anchorId)) return "DRESSER_STAND";
        if (MIRROR.equals(anchorId)) return "MIRROR_STAND";
        return "STAND_TALK";
    }

    private static float pelvisPivotCompensationX(float pitchDeg) {
        double radians = Math.toRadians(pitchDeg);
        return STANDING_PELVIS_HEIGHT_M * (float) Math.sin(radians);
    }

    private static float pelvisPivotCompensationY(float pitchDeg) {
        double radians = Math.toRadians(pitchDeg);
        return STANDING_PELVIS_HEIGHT_M * (1.0f - (float) Math.cos(radians));
    }

    private static CelineRoomWorldContractV80.Anchor require(
            CelineRoomWorldContractV80 world, String id) {
        CelineRoomWorldContractV80.Anchor anchor = world == null ? null : world.anchor(id);
        if (anchor == null) throw new IllegalStateException("9R furniture anchor missing: " + id);
        return anchor;
    }

    private static void add(float[] angles, int index, float pitch, float yaw, float roll) {
        int offset = index * 3;
        if (angles == null || offset + 2 >= angles.length) return;
        angles[offset] += pitch;
        angles[offset + 1] += yaw;
        angles[offset + 2] += roll;
    }

    private static float approach(float value, float target, float maxDelta) {
        if (value < target) return Math.min(target, value + Math.max(0.0f, maxDelta));
        return Math.max(target, value - Math.max(0.0f, maxDelta));
    }

    private static float smooth(float value) {
        float bounded = clamp(value, 0.0f, 1.0f);
        return bounded * bounded * (3.0f - 2.0f * bounded);
    }

    private static float pow4(float value) {
        float square = value * value;
        return square * square;
    }

    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }
}
