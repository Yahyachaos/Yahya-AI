package de.yahya.ai;

/**
 * 9R.3 authored bed-pose contribution for the central v80 production owner.
 *
 * This class never writes Filament transforms. It only owns bounded pose/root targets which
 * CelineProductionPresenceV80 mixes into its existing single transform transaction.
 */
final class CelineBedPoseV9R3 {
    private static final String BED_APPROACH = "bed_approach_anchor";
    private static final String BED_EDGE = "bed_edge_sit_anchor";
    private static final String BED_RELAX = "bed_relax_anchor";
    private static final String BED_LIE = "bed_lie_anchor";
    private static final String BED_EXIT = "bed_exit_anchor";

    private static final int HIPS = 0;
    private static final int SPINE = 1;
    private static final int SPINE01 = 2;
    private static final int SPINE02 = 3;
    private static final int NECK = 4;
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

    // The production rig's standing pelvis is approximately 0.88 m above the calibrated sole.
    // Derive bed contact from the immutable 4R mattress plane; this remains a 9R-only correction.
    private static final float STANDING_PELVIS_HEIGHT_M = 0.88f;
    // Proof #125 showed the pelvis was vertically close but still sat just outside the mattress
    // silhouette. Move only the 9R contact pose slightly inward; the accepted room/anchors stay
    // immutable.
    private static final float EDGE_MATTRESS_INSET_X_M = 0.12f;
    private static final float RELAX_MATTRESS_INSET_X_M = 0.06f;
    // Lying skin/bone volume needs a small support-depth offset below the pelvis contact plane.
    private static final float RELAX_SUPPORT_DROP_M = 0.025f;
    private static final float LIE_SUPPORT_DROP_M = 0.09f;

    private final float edgeSeatRootY;
    private final float relaxRootY;
    private final float lieRootY;
    private final float edgeOffsetX;
    private final float edgeOffsetZ;
    private final float relaxOffsetX;
    private final float relaxOffsetZ;
    private final float lieOffsetX;
    private final float lieOffsetZ;
    private final float exitOffsetX;
    private final float exitOffsetZ;

    private float edgeBlend;
    private float relaxBlend;
    private float lieBlend;
    private float rootX;
    private float rootY;
    private float rootZ;
    private float rootPitch;
    private float rootRoll;
    private String requestedAnchor = BED_APPROACH;

    CelineBedPoseV9R3(CelineRoomWorldContractV80 world) {
        CelineRoomWorldContractV80.Anchor approach = require(world, BED_APPROACH);
        CelineRoomWorldContractV80.Anchor edge = require(world, BED_EDGE);
        CelineRoomWorldContractV80.Anchor relax = require(world, BED_RELAX);
        CelineRoomWorldContractV80.Anchor lie = require(world, BED_LIE);
        CelineRoomWorldContractV80.Anchor exit = require(world, BED_EXIT);

        edgeSeatRootY = world.bedMattressY - STANDING_PELVIS_HEIGHT_M;
        relaxRootY = edgeSeatRootY - RELAX_SUPPORT_DROP_M;
        lieRootY = edgeSeatRootY - LIE_SUPPORT_DROP_M;
        edgeOffsetX = edge.localX - approach.localX + EDGE_MATTRESS_INSET_X_M;
        edgeOffsetZ = edge.localZ - approach.localZ;
        relaxOffsetX = relax.localX - approach.localX + RELAX_MATTRESS_INSET_X_M;
        relaxOffsetZ = relax.localZ - approach.localZ;
        lieOffsetX = lie.localX - approach.localX;
        lieOffsetZ = lie.localZ - approach.localZ;
        exitOffsetX = exit.localX - approach.localX;
        exitOffsetZ = exit.localZ - approach.localZ;
    }

    void update(String anchorId, float deltaSeconds, boolean enabled) {
        requestedAnchor = enabled && anchorId != null ? anchorId : BED_APPROACH;
        float edgeTarget = enabled && BED_EDGE.equals(anchorId) ? 1.0f : 0.0f;
        float relaxTarget = enabled && BED_RELAX.equals(anchorId) ? 1.0f : 0.0f;
        float lieTarget = enabled && BED_LIE.equals(anchorId) ? 1.0f : 0.0f;
        float poseStep = Math.max(0.0f, deltaSeconds) * POSE_BLEND_PER_SECOND;
        edgeBlend = approach(edgeBlend, edgeTarget, poseStep);
        relaxBlend = approach(relaxBlend, relaxTarget, poseStep);
        lieBlend = approach(lieBlend, lieTarget, poseStep);

        float targetX = 0.0f;
        float targetY = 0.0f;
        float targetZ = 0.0f;
        float targetPitch = 0.0f;
        float targetRoll = 0.0f;
        if (enabled && BED_EDGE.equals(anchorId)) {
            targetX = edgeOffsetX;
            targetY = edgeSeatRootY;
            targetZ = edgeOffsetZ;
        } else if (enabled && BED_RELAX.equals(anchorId)) {
            targetX = relaxOffsetX;
            targetY = relaxRootY;
            targetZ = relaxOffsetZ;
            targetRoll = 1.0f;
        } else if (enabled && BED_LIE.equals(anchorId)) {
            targetX = lieOffsetX;
            targetY = lieRootY;
            targetZ = lieOffsetZ;
            targetRoll = 1.5f;
        } else if (enabled && BED_EXIT.equals(anchorId)) {
            targetX = exitOffsetX;
            targetZ = exitOffsetZ;
        }
        float rootStep = Math.max(0.0f, deltaSeconds) * ROOT_BLEND_PER_SECOND;
        rootX = approach(rootX, targetX, rootStep * 0.65f);
        rootY = approach(rootY, targetY, rootStep * 0.55f);
        rootZ = approach(rootZ, targetZ, rootStep * 0.65f);
        // Scene/root pitch remains zero. Recline is authored at the skeletal pelvis below.
        rootPitch = approach(rootPitch, targetPitch, rootStep * 70.0f);
        rootRoll = approach(rootRoll, targetRoll, rootStep * 18.0f);
    }

    void applyBase(float[] angles, float home) {
        float edge = home * smooth(edgeBlend);
        float relax = home * smooth(relaxBlend);
        float lie = home * smooth(lieBlend);

        // Proof #125 established that negative pelvis pitch folds Celine forward. Positive pitch
        // reclines her toward the bed behind the seated pose, while the contact root stays fixed.
        add(angles, HIPS,
                edge * -4.0f + relax * 24.0f + lie * 86.0f,
                0.0f, 0.0f);
        add(angles, LEFT_UP_LEG, edge * -78.0f + relax * -50.0f + lie * -3.0f,
                0.0f, edge * 4.0f + relax * 4.0f);
        add(angles, RIGHT_UP_LEG, edge * -74.0f + relax * -47.0f + lie * -2.0f,
                0.0f, edge * -5.0f + relax * -3.0f);
        add(angles, LEFT_LEG, edge * 88.0f + relax * 57.0f + lie * 4.0f, 0.0f, 0.0f);
        add(angles, RIGHT_LEG, edge * 84.0f + relax * 54.0f + lie * 3.0f, 0.0f, 0.0f);
        add(angles, LEFT_FOOT, edge * -9.0f + relax * -5.0f, 0.0f, 0.0f);
        add(angles, RIGHT_FOOT, edge * -8.0f + relax * -4.0f, 0.0f, 0.0f);
    }

    void applyPosture(float[] angles, float home) {
        float edge = home * smooth(edgeBlend);
        float relax = home * smooth(relaxBlend);
        float lie = home * smooth(lieBlend);
        // Counter-flex the spine slightly so recline reads as supported/relaxed rather than as a
        // rigid board or abdominal crunch.
        add(angles, SPINE, edge * 3.0f + relax * -4.0f + lie * -2.0f, 0.0f, 0.0f);
        add(angles, SPINE01, edge * 4.0f + relax * -5.0f + lie * -2.0f, 0.0f, 0.0f);
        add(angles, SPINE02, edge * 3.0f + relax * -3.0f + lie * -1.0f, 0.0f, 0.0f);
        add(angles, NECK, relax * 3.0f + lie * 5.0f, lie * -3.0f, 0.0f);
        add(angles, LEFT_SHOULDER, 0.0f, 0.0f, relax * -4.0f + lie * -6.0f);
        add(angles, RIGHT_SHOULDER, 0.0f, 0.0f, relax * 4.0f + lie * 6.0f);
        add(angles, LEFT_ARM, relax * -16.0f + lie * -10.0f, 0.0f,
                relax * 14.0f + lie * 14.0f);
        add(angles, RIGHT_ARM, relax * -14.0f + lie * -9.0f, 0.0f,
                relax * -14.0f + lie * -14.0f);
        add(angles, LEFT_FOREARM, relax * -20.0f + lie * -7.0f, 0.0f, 0.0f);
        add(angles, RIGHT_FOREARM, relax * -18.0f + lie * -6.0f, 0.0f, 0.0f);
    }

    float activity() {
        return clamp(Math.max(edgeBlend, Math.max(relaxBlend, lieBlend)), 0.0f, 1.0f);
    }

    float rootX() { return rootX; }
    float rootY() { return rootY; }
    float rootZ() { return rootZ; }
    float rootPitch() { return rootPitch; }
    float rootRoll() { return rootRoll; }

    boolean isBedAnchor(String id) {
        return BED_EDGE.equals(id) || BED_RELAX.equals(id) || BED_LIE.equals(id)
                || BED_EXIT.equals(id);
    }

    boolean settled(String anchorId) {
        if (anchorId == null || !anchorId.equals(requestedAnchor)) return false;
        float targetEdge = BED_EDGE.equals(anchorId) ? 1.0f : 0.0f;
        float targetRelax = BED_RELAX.equals(anchorId) ? 1.0f : 0.0f;
        float targetLie = BED_LIE.equals(anchorId) ? 1.0f : 0.0f;
        return Math.abs(edgeBlend - targetEdge) < 0.025f
                && Math.abs(relaxBlend - targetRelax) < 0.025f
                && Math.abs(lieBlend - targetLie) < 0.025f;
    }

    String poseName(String anchorId) {
        if (BED_EDGE.equals(anchorId)) return "BED_EDGE_SIT";
        if (BED_RELAX.equals(anchorId)) return "BED_RELAX";
        if (BED_LIE.equals(anchorId)) return "BED_LIE";
        if (BED_EXIT.equals(anchorId)) return "STAND_EXIT";
        return "STAND_TALK";
    }

    private static CelineRoomWorldContractV80.Anchor require(
            CelineRoomWorldContractV80 world, String id) {
        CelineRoomWorldContractV80.Anchor anchor = world == null ? null : world.anchor(id);
        if (anchor == null) throw new IllegalStateException("9R.3 bed anchor missing: " + id);
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

    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }
}
