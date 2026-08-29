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
    // Manual 9R.1 evidence showed the accepted 4R back/window corridor can place Celine behind
    // the drape silhouette. Keep 4R immutable and move only the 9R runtime corridor toward the
    // fixed camera. The final window hold already proved this bounded offset is clear of drapes.
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

        Map<String, List<String>> frozen = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : mutable.entrySet()) {
            frozen.put(entry.getKey(),
                    Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
        Celine3DDiagnostics.record(context, "V80-471", "9R Nav-Graph geladen",
                "anchors=" + world.anchors.size() + " edges=" + edges.length()
                        + " cameraChase=false teleport=false"
                        + " drapeCorridorSafetyZ=+" + WINDOW_SAFETY_Z_OFFSET_M
                        + " corridor=back_center>shelf>window");
        return new CelineRoomNavigatorV9R(world, frozen);
    }

    CelineRoomWorldContractV80.Anchor anchor(String id) {
        CelineRoomWorldContractV80.Anchor source = world.anchor(id);
        if (source == null) return null;

        float resolvedFacing = source.facingYDeg;
        if (Float.isNaN(resolvedFacing)) {
            // Some 4R transit/approach anchors deliberately left final facing unspecified. 9R must
            // resolve that at runtime without mutating the accepted 4R contract. Default those
            // holds to the existing camera-talk social facing; travel direction is still used
            // while walking.
            CelineRoomWorldContractV80.Anchor talk = world.anchor(TALK_ANCHOR);
            resolvedFacing = talk != null && !Float.isNaN(talk.facingYDeg)
                    ? talk.facingYDeg : 180.0f;
        }

        if (BACK_CENTER_ANCHOR.equals(source.id)
                || SHELF_ANCHOR.equals(source.id)
                || WINDOW_ANCHOR.equals(source.id)) {
            return new CelineRoomWorldContractV80.Anchor(
                    source.id, source.kind, source.objectId, source.approachAnchor, source.poseMode,
                    source.localX, source.localY, source.localZ + WINDOW_SAFETY_Z_OFFSET_M,
                    resolvedFacing, source.clearanceRadius, source.contactCalibrationRequired);
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
