package de.yahya.ai;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable 9R navigator over the already accepted 4R anchor/nav graph. */
final class CelineRoomNavigatorV9R {
    private static final String TALK_ANCHOR = "camera_talk_anchor";
    private static final String BACK_CENTER_ANCHOR = "back_center_nav_anchor";
    private static final String SHELF_ANCHOR = "shelf_anchor";
    private static final String WINDOW_ANCHOR = "window_anchor";
    private static final String TABLE_APPROACH_ANCHOR = "foreground_table_approach_anchor";
    private static final String TABLE_LEAN_ANCHOR = "foreground_table_lean_anchor";
    private static final String BED_APPROACH_ANCHOR = "bed_approach_anchor";
    private static final String BED_EDGE_SIT_ANCHOR = "bed_edge_sit_anchor";
    private static final String BED_RELAX_ANCHOR = "bed_relax_anchor";
    private static final String BED_LIE_ANCHOR = "bed_lie_anchor";
    private static final String BED_EXIT_ANCHOR = "bed_exit_anchor";
    private static final String CHAIR_APPROACH_ANCHOR = "chair_approach_anchor";
    private static final String CHAIR_SIT_ANCHOR = "chair_sit_anchor";
    // Manual 9R.1 evidence showed the accepted 4R back/window corridor can place Celine behind
    // the drape silhouette. Keep 4R immutable and move only the 9R runtime corridor toward the
    // fixed camera. Proof #117 accepted this exact window root position and facing with grounded
    // visible feet; 9R.5 therefore keeps both intact and turns only the upper body/head.
    private static final float WINDOW_SAFETY_Z_OFFSET_M = 0.65f;

    private final CelineRoomWorldContractV80 world;
    private final Map<String, List<String>> graph;

    private CelineRoomNavigatorV9R(CelineRoomWorldContractV80 world,
                                   Map<String, List<String>> graph) {
        this.world = world;
        this.graph = Collections.unmodifiableMap(graph);
    }

    static CelineRoomNavigatorV9R load(Context context, CelineRoomWorldContractV80 world)
            throws Exception {
        if (world == null) throw new IllegalStateException("9R world contract missing");
        JSONObject nav = readJson(context, CelineRoomWorldContractV80.NAV_COLLISION_PATH);
        require(nav.optInt("schema") == 1, "nav schema");
        JSONArray edges = nav.getJSONArray("edges");
        require(edges.length() == world.navEdgeCount, "edge count");

        Map<String, Set<String>> mutable = new LinkedHashMap<>();
        for (String id : world.anchors.keySet()) mutable.put(id, new LinkedHashSet<>());
        for (int i = 0; i < edges.length(); i++) {
            JSONObject edge = edges.getJSONObject(i);
            String from = edge.getString("from");
            String to = edge.getString("to");
            require(world.anchor(from) != null && world.anchor(to) != null,
                    "edge anchor " + from + " -> " + to);
            mutable.get(from).add(to);
            if (edge.optBoolean("bidirectional", false)) mutable.get(to).add(from);
        }

        // 9R.2 table and accepted 9R.3 bed contact edges remain enabled. 9R.4 unlocks exactly the
        // one already-authored lounge-chair contact edge; no later dresser/mirror/shelf/lamp
        // interaction capability is introduced here.
        boolean tableContactEnabled = false;
        boolean chairContactEnabled = false;
        int bedContactEdgesEnabled = 0;
        JSONArray contactEdges = nav.getJSONArray("contact_edges");
        for (int i = 0; i < contactEdges.length(); i++) {
            JSONObject edge = contactEdges.getJSONObject(i);
            String from = edge.getString("from");
            String to = edge.getString("to");
            if (TABLE_APPROACH_ANCHOR.equals(from) && TABLE_LEAN_ANCHOR.equals(to)) {
                require(world.anchor(from) != null && world.anchor(to) != null,
                        "table contact anchors");
                mutable.get(from).add(to);
                mutable.get(to).add(from);
                tableContactEnabled = true;
                continue;
            }
            if (isAuthoredBedContact(from, to)) {
                require(world.anchor(from) != null && world.anchor(to) != null,
                        "bed contact anchors " + from + " -> " + to);
                mutable.get(from).add(to);
                mutable.get(to).add(from);
                bedContactEdgesEnabled++;
                continue;
            }
            if (CHAIR_APPROACH_ANCHOR.equals(from) && CHAIR_SIT_ANCHOR.equals(to)) {
                require(world.anchor(from) != null && world.anchor(to) != null,
                        "chair contact anchors");
                mutable.get(from).add(to);
                mutable.get(to).add(from);
                chairContactEnabled = true;
            }
        }
        require(tableContactEnabled, "9R.2 table contact edge");
        require(bedContactEdgesEnabled == 4, "9R.3 four authored bed contact edges");
        require(chairContactEnabled, "9R.4 authored chair contact edge");

        // The 4R anchor contract explicitly allows bed_exit -> bed_approach as the standing exit
        // handoff, while the contact-edge list only describes furniture-contact transitions.
        require(world.anchor(BED_EXIT_ANCHOR) != null && world.anchor(BED_APPROACH_ANCHOR) != null,
                "9R.3 bed exit standing bridge anchors");
        mutable.get(BED_EXIT_ANCHOR).add(BED_APPROACH_ANCHOR);
        mutable.get(BED_APPROACH_ANCHOR).add(BED_EXIT_ANCHOR);

        Map<String, List<String>> frozen = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : mutable.entrySet()) {
            frozen.put(entry.getKey(),
                    Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
        Celine3DDiagnostics.record(context, "V80-471", "9R Nav-Graph geladen",
                "anchors=" + world.anchors.size() + " edges=" + edges.length()
                        + " cameraChase=false teleport=false"
                        + " drapeCorridorSafetyZ=+" + WINDOW_SAFETY_Z_OFFSET_M
                        + " corridor=back_center>shelf>window"
                        + " windowRootFacing=accepted9R1"
                        + " tableContactEdge=true bedContactEdges=" + bedContactEdgesEnabled
                        + " bedExitBridge=true bedContactRootOwnedByCentralPose=true"
                        + " chairContact=true chairContactRootOwnedByCentralPose=true"
                        + " chairFacingCalibratedToApproach=true");
        return new CelineRoomNavigatorV9R(world, frozen);
    }

    CelineRoomWorldContractV80.Anchor anchor(String id) {
        CelineRoomWorldContractV80.Anchor source = world.anchor(id);
        if (source == null) return null;

        float resolvedFacing = resolvedFacing(source);

        if (BACK_CENTER_ANCHOR.equals(source.id)
                || SHELF_ANCHOR.equals(source.id)
                || WINDOW_ANCHOR.equals(source.id)) {
            return new CelineRoomWorldContractV80.Anchor(
                    source.id, source.kind, source.objectId, source.approachAnchor, source.poseMode,
                    source.localX, source.localY, source.localZ + WINDOW_SAFETY_Z_OFFSET_M,
                    resolvedFacing, source.clearanceRadius, source.contactCalibrationRequired);
        }

        if (isBedContactAnchor(source.id)) {
            // Contact movement inside the mattress/bed footprint is not locomotion. Keep the
            // navigator's root at the proven bed-approach point and let CelineBedPoseV9R3 ease the
            // authored x/y/z + body orientation in the existing central transform owner.
            CelineRoomWorldContractV80.Anchor approach = world.anchor(BED_APPROACH_ANCHOR);
            require(approach != null, "9R.3 bed approach anchor for virtual contact root");
            return new CelineRoomWorldContractV80.Anchor(
                    source.id, source.kind, source.objectId, source.approachAnchor, source.poseMode,
                    approach.localX, source.localY, approach.localZ,
                    resolvedFacing, source.clearanceRadius, source.contactCalibrationRequired);
        }

        if (CHAIR_SIT_ANCHOR.equals(source.id)) {
            // Walking still ends outside the lounge-chair collider. Keep x/z at chair_approach so
            // locomotion never travels through furniture. Manual proof #131 showed the prepared
            // draft chair facing=0 turns Celine behind the final visible seat/back geometry even
            // though the contact pose itself remains anatomically coherent. Use the already proven
            // approach/social facing for the contact hold; only the central pose helper lowers and
            // translates into the seat.
            CelineRoomWorldContractV80.Anchor approach = world.anchor(CHAIR_APPROACH_ANCHOR);
            require(approach != null, "9R.4 chair approach anchor for virtual contact root");
            float chairFacing = resolvedFacing(approach);
            return new CelineRoomWorldContractV80.Anchor(
                    source.id, source.kind, source.objectId, source.approachAnchor, source.poseMode,
                    approach.localX, source.localY, approach.localZ,
                    chairFacing, source.clearanceRadius, source.contactCalibrationRequired);
        }

        if (!Float.isNaN(source.facingYDeg)) return source;
        return new CelineRoomWorldContractV80.Anchor(
                source.id, source.kind, source.objectId, source.approachAnchor, source.poseMode,
                source.localX, source.localY, source.localZ, resolvedFacing,
                source.clearanceRadius, source.contactCalibrationRequired);
    }

    List<String> route(String from, String to) {
        if (from == null || to == null || graph.get(from) == null || graph.get(to) == null) {
            return Collections.emptyList();
        }
        if (from.equals(to)) return Collections.singletonList(from);

        ArrayDeque<String> queue = new ArrayDeque<>();
        Map<String, String> parent = new LinkedHashMap<>();
        queue.add(from);
        parent.put(from, null);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            List<String> next = graph.get(current);
            if (next == null) continue;
            for (String candidate : next) {
                if (parent.containsKey(candidate)) continue;
                parent.put(candidate, current);
                if (candidate.equals(to)) {
                    ArrayList<String> path = new ArrayList<>();
                    String cursor = to;
                    while (cursor != null) {
                        path.add(cursor);
                        cursor = parent.get(cursor);
                    }
                    Collections.reverse(path);
                    return path;
                }
                queue.addLast(candidate);
            }
        }
        return Collections.emptyList();
    }

    private float resolvedFacing(CelineRoomWorldContractV80.Anchor source) {
        float facing = source.facingYDeg;
        if (!Float.isNaN(facing)) return facing;
        // Some 4R transit/approach anchors deliberately left final facing unspecified. Default
        // those holds to the existing camera-talk social facing; travel direction still owns yaw
        // while walking.
        CelineRoomWorldContractV80.Anchor talk = world.anchor(TALK_ANCHOR);
        return talk != null && !Float.isNaN(talk.facingYDeg) ? talk.facingYDeg : 180.0f;
    }

    private static boolean isAuthoredBedContact(String from, String to) {
        return (BED_APPROACH_ANCHOR.equals(from) && BED_EDGE_SIT_ANCHOR.equals(to))
                || (BED_EDGE_SIT_ANCHOR.equals(from) && BED_RELAX_ANCHOR.equals(to))
                || (BED_RELAX_ANCHOR.equals(from) && BED_LIE_ANCHOR.equals(to))
                || (BED_EDGE_SIT_ANCHOR.equals(from) && BED_EXIT_ANCHOR.equals(to));
    }

    private static boolean isBedContactAnchor(String id) {
        return BED_EDGE_SIT_ANCHOR.equals(id)
                || BED_RELAX_ANCHOR.equals(id)
                || BED_LIE_ANCHOR.equals(id)
                || BED_EXIT_ANCHOR.equals(id);
    }

    private static JSONObject readJson(Context context, String path) throws Exception {
        StringBuilder text = new StringBuilder(16_384);
        try (InputStream in = context.getAssets().open(path);
             InputStreamReader reader = new InputStreamReader(in, "UTF-8")) {
            char[] chunk = new char[8_192];
            int read;
            while ((read = reader.read(chunk)) >= 0) {
                if (read > 0) text.append(chunk, 0, read);
            }
        }
        return new JSONObject(text.toString());
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new IllegalStateException("9R nav invalid: " + label);
    }
}
