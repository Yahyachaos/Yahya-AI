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

    // Proof #124 showed that keeping the standing root height while bending the legs makes Celine
    // visibly hover above the mattress. The production rig's standing pelvis is approximately
    // 0.88 m above the calibrated sole/floor plane, so derive the bounded contact drop from the
    // immutable 4R mattress plane instead of applying a global avatar Y shift.
    private static final float STANDING_PELVIS_HEIGHT_M = 0.88f;

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
        relaxRootY = edgeSeatRootY + 0.02f;
        lieRootY = edgeSeatRootY + 0.04f;
        edgeOffsetX = edge.localX - approach.localX;
        edgeOffsetZ = edge.localZ - approach.localZ;
        relaxOffsetX = relax.localX - approach.localX;
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
            targetRoll = 1.5f;
        } else if (enabled && BED_LIE.equals(anchorId)) {
            targetX = lieOffsetX;
            targetY = lieRootY;
            targetZ = lieOffsetZ;
            targetRoll = 2.0f;
        } else if (enabled && BED_EXIT.equals(anchorId)) {
            targetX = exitOffsetX;
            targetZ = exitOffsetZ;
        }
        float rootStep = Math.max(0.0f, deltaSeconds) * ROOT_BLEND_PER_SECOND;
        rootX = approach(rootX, targetX, rootStep * 0.65f);
        rootY = approach(rootY, targetY, rootStep * 0.55f);
        rootZ = approach(rootZ, targetZ, rootStep * 0.65f);
        // Never recline around the scene/root pivot: that pivot is effectively at the sole and
        // Proof #124 showed the body swinging away from the bed. Recline at the Hips bone below.
        rootPitch = approach(rootPitch, targetPitch, rootStep * 70.0f);
        rootRoll = approach(rootRoll, targetRoll, rootStep * 18.0f);
    }

    void applyBase(float[] angles, float home) {
        float edge = home * smooth(edgeBlend);
        float relax = home * smooth(relaxBlend);
        float lie = home * smooth(lieBlend);

        // Hips is the first skeletal/pelvis pivot. Rotating here keeps the mattress contact point
        // stable while the whole articulated body reclines; rotating the Filament scene root does
        // not, because that pivot is effectively at the feet.
        add(angles, HIPS,
                edge * -4.0f + relax * -22.0f + lie * -86.0f,
                0.0f, 0.0f);
        add(angles, LEFT_UP_LEG, edge * -78.0f + relax * -48.0f + lie * -6.0f,
                0.0f, edge * 4.0f + relax * 5.0f);
        add(angles, RIGHT_UP_LEG, edge * -74.0f + relax * -44.0f + lie * -4.0f,
                0.0f, edge * -5.0f + relax * -4.0f);
        add(angles, LEFT_LEG, edge * 88.0f + relax * 54.0f + lie * 8.0f, 0.0f, 0.0f);
        add(angles, RIGHT_LEG, edge * 84.0f + relax * 50.0f + lie * 7.0f, 0.0f, 0.0f);
        add(angles, LEFT_FOOT, edge * -9.0f + relax * -6.0f, 0.0f, 0.0f);
        add(angles, RIGHT_FOOT, edge * -8.0f + relax * -5.0f, 0.0f, 0.0f);
    }

    void applyPosture(float[] angles, float home) {
        float edge = home * smooth(edgeBlend);
        float relax = home * smooth(relaxBlend);
        float lie = home * smooth(lieBlend);
        add(angles, SPINE, edge * 3.0f + relax * 7.0f + lie * 2.0f, 0.0f, 0.0f);
        add(angles, SPINE01, edge * 4.0f + relax * 9.0f + lie * 3.0f, 0.0f, 0.0f);
        add(angles, SPINE02, edge * 3.0f + relax * 7.0f + lie * 2.0f, 0.0f, 0.0f);
        add(angles, NECK, relax * -3.0f + lie * 5.0f, lie * -4.0f, 0.0f);
        add(angles, LEFT_SHOULDER, 0.0f, 0.0f, relax * -3.0f + lie * -7.0f);
        add(angles, RIGHT_SHOULDER, 0.0f, 0.0f, relax * 3.0f + lie * 7.0f);
        add(angles, LEFT_ARM, relax * -10.0f + lie * -12.0f, 0.0f,
                relax * 12.0f + lie * 16.0f);
        add(angles, RIGHT_ARM, relax * -8.0f + lie * -10.0f, 0.0f,
                relax * -12.0f + lie * -16.0f);
        add(angles, LEFT_FOREARM, relax * -18.0f + lie * -8.0f, 0.0f, 0.0f);
        add(angles, RIGHT_FOREARM, relax * -16.0f + lie * -7.0f, 0.0f, 0.0f);
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
