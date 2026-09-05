package de.yahya.ai;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable v80 4R room/world contract.
 *
 * The room action graph is data-only in 4R. Walking, contact poses, natural-language commands and
 * autonomous room behavior remain deliberately disabled until the later 9R phase.
 */
final class CelineRoomWorldContractV80 {
    static final String WORLD_PATH = "models/room/celine_room_v80_world_contract.json";
    static final String ASSEMBLY_PATH = "models/room/celine_room_v80_assembly.json";
    static final String ANCHORS_PATH = "models/room/celine_room_v80_anchors.json";
    static final String NAV_COLLISION_PATH = "models/room/celine_room_v80_nav_collision.json";

    static final float RUNTIME_OFFSET_X = 0.0f;
    static final float RUNTIME_OFFSET_Y = -1.55f;
    static final float RUNTIME_OFFSET_Z = -4.0f;

    // Real in-app Candidate #1136 proved that carrying the legacy 6.4x5.8 camera-talk Z=1.15 m
    // into the exact 4.40x4.20 reconstruction makes ambient HOME Celine roughly 1.6x too large and
    // clips her upper body. CALL already uses +0.12 m with the accepted camera and is the empirical
    // reference-depth control. Rebase only the runtime parsed talk anchor; immutable source JSON,
    // every other anchor and all route destination world-Z values stay unchanged because the
    // navigator subtracts talk.localZ and the central root owner adds the same talk.localZ back.
    private static final float RECONSTRUCTION_TALK_LOCAL_Z = 0.12f;

    private static final String ROOM_ID = "celine_room_v80_final_modular";
    private static final String ROOM_SHA256 =
            "25dc79b93accc804340da392b2b7a8d78c69ce19b16c17b6aacef3bfaf4465a8";
    private static final long ROOM_BYTES = 46_580_788L;
    private static final int SOURCE_ASSETS = 12;
    private static final int FURNITURE_INSTANCES = 13;
    private static final int ANCHOR_COUNT = 20;
    private static final String ROOM_ACTION_PHASE =
            "4R_WORLD_ONLY_NO_9R_LOCOMOTION_YET";

    static final class Anchor {
        final String id;
        final String kind;
        final String objectId;
        final String approachAnchor;
        final String poseMode;
        final float localX;
        final float localY;
        final float localZ;
        final float worldX;
        final float worldY;
        final float worldZ;
        final float facingYDeg;
        final float clearanceRadius;
        final boolean contactCalibrationRequired;

        Anchor(String id, String kind, String objectId, String approachAnchor, String poseMode,
               float localX, float localY, float localZ, float facingYDeg,
               float clearanceRadius, boolean contactCalibrationRequired) {
            this.id = id;
            this.kind = kind;
            this.objectId = objectId;
            this.approachAnchor = approachAnchor;
            this.poseMode = poseMode;
            this.localX = localX;
            this.localY = localY;
            this.localZ = localZ;
            this.worldX = localX + RUNTIME_OFFSET_X;
            this.worldY = localY + RUNTIME_OFFSET_Y;
            this.worldZ = localZ + RUNTIME_OFFSET_Z;
            this.facingYDeg = facingYDeg;
            this.clearanceRadius = clearanceRadius;
            this.contactCalibrationRequired = contactCalibrationRequired;
        }

        String diagnosticSummary() {
            return id + "=" + worldX + "," + worldY + "," + worldZ
                    + " local=" + localX + "," + localY + "," + localZ;
        }
    }

    final String roomId;
    final String roomSha256;
    final long roomBytes;
    final float width;
    final float depth;
    final float height;
    final float bedMattressY;
    final float chairSeatY;
    final float foregroundTableTopY;
    final int sourceAssetCount;
    final int furnitureInstanceCount;
    final int colliderCount;
    final int navEdgeCount;
    final int contactEdgeCount;
    final String roomActionPhase;
    final boolean noVisibleLaptop;
    final boolean foregroundTableVisible;
    final Map<String, Anchor> anchors;

    private CelineRoomWorldContractV80(
            float width, float depth, float height,
            float bedMattressY, float chairSeatY, float foregroundTableTopY,
            int colliderCount, int navEdgeCount, int contactEdgeCount,
            Map<String, Anchor> anchors) {
        this.roomId = ROOM_ID;
        this.roomSha256 = ROOM_SHA256;
        this.roomBytes = ROOM_BYTES;
        this.width = width;
        this.depth = depth;
        this.height = height;
        this.bedMattressY = bedMattressY;
        this.chairSeatY = chairSeatY;
        this.foregroundTableTopY = foregroundTableTopY;
        this.sourceAssetCount = SOURCE_ASSETS;
        this.furnitureInstanceCount = FURNITURE_INSTANCES;
        this.colliderCount = colliderCount;
        this.navEdgeCount = navEdgeCount;
        this.contactEdgeCount = contactEdgeCount;
        this.roomActionPhase = ROOM_ACTION_PHASE;
        this.noVisibleLaptop = true;
        this.foregroundTableVisible = true;
        this.anchors = Collections.unmodifiableMap(anchors);
    }

    static CelineRoomWorldContractV80 load(Context context) throws Exception {
        JSONObject world = readJson(context, WORLD_PATH);
        JSONObject assembly = readJson(context, ASSEMBLY_PATH);
        JSONObject anchorDocument = readJson(context, ANCHORS_PATH);
        JSONObject nav = readJson(context, NAV_COLLISION_PATH);

        require(world.optInt("schema") == 1, "world schema");
        require("4R".equals(world.optString("phase")), "world phase");
        require(ROOM_ID.equals(world.optString("room_id")), "world room id");
        require("PASS_STATIC_ASSET_CANDIDATE".equals(
                world.getJSONObject("validation").optString("result")), "static acceptance");

        JSONObject combined = world.getJSONObject("combined_room");
        require(ROOM_SHA256.equals(combined.optString("sha256")), "room sha256");
        require(combined.optLong("bytes") == ROOM_BYTES, "room byte count");
        require(combined.optInt("furniture_instances") == FURNITURE_INSTANCES,
                "furniture instance count");
        require(combined.optInt("anchor_nodes") == ANCHOR_COUNT, "anchor node count");
        require(combined.optInt("nightstand_instances") == 2, "two nightstand instances");
        require(!combined.optBoolean("has_visible_laptop_node", true), "no laptop node");
        require(arrayContains(combined.getJSONArray("extras_keys"), "room_action_phase"),
                "GLB room action phase is declared");
        require(arrayContains(world.getJSONArray("runtime_integration_next"),
                "wire structured anchors/nav metadata without enabling 9R locomotion yet"),
                "9R remains disabled");

        JSONObject shell = world.getJSONObject("room_shell_m");
        float width = finitePositive(shell, "width");
        float depth = finitePositive(shell, "depth");
        float height = finitePositive(shell, "height");
        requireClose(width, 6.4f, "room width");
        requireClose(depth, 5.8f, "room depth");
        requireClose(height, 2.8f, "room height");

        JSONObject viewer = world.getJSONObject("viewer");
        require("laptop_webcam".equals(viewer.optString("type")), "viewer type");
        require(!viewer.optBoolean("render_laptop", true), "laptop rendering disabled");
        require(viewer.optBoolean("foreground_table_visible", false),
                "foreground table visible");

        JSONObject contacts = world.getJSONObject("contact_planes");
        float bedY = finite(contacts, "bed_mattress_y_m");
        float chairY = finite(contacts, "chair_seat_y_m");
        float tableY = finite(contacts, "foreground_table_top_y_m");
        requireClose(bedY, 0.461f, "bed contact");
        requireClose(chairY, 0.457f, "chair contact");
        requireClose(tableY, 0.756f, "table contact");

        require(assembly.optInt("schema") == 1, "assembly schema");
        require(ROOM_ID.equals(assembly.optString("room_id")), "assembly room id");
        require("room_world_root".equals(assembly.optString("world_root")),
                "assembly root");
        JSONObject source = assembly.getJSONObject("source_contract");
        require(source.optInt("expected_unique_glbs") == SOURCE_ASSETS,
                "twelve optimized sources");
        require(source.optInt("instantiated_scene_objects") == FURNITURE_INSTANCES,
                "thirteen scene instances");
        require(source.optInt("nightstand_source_instances") == 2,
                "one nightstand source used twice");
        require(assembly.getJSONArray("objects").length() == FURNITURE_INSTANCES,
                "assembly object count");

        JSONObject anchorSource = anchorDocument.getJSONObject("anchors");
        JSONObject lockedAnchors = world.getJSONObject("anchors");
        require(anchorSource.length() == ANCHOR_COUNT, "prepared anchor count");
        require(lockedAnchors.length() == ANCHOR_COUNT, "locked anchor count");
        Map<String, Anchor> anchors = parseAnchors(
                lockedAnchors, anchorSource, bedY, chairY, tableY);

        require(nav.optInt("schema") == 1, "nav schema");
        JSONArray colliders = nav.getJSONArray("colliders");
        JSONArray edges = nav.getJSONArray("edges");
        JSONArray contactEdges = nav.getJSONArray("contact_edges");
        require(colliders.length() == 9, "collider count");
        require(edges.length() == 14, "navigation edge count");
        require(contactEdges.length() == 6, "contact edge count");
        require(arrayContains(nav.getJSONArray("route_policy"), "camera never chases avatar"),
                "fixed viewer route policy");

        for (String required : Arrays.asList(
                "recovery_home_anchor", "camera_talk_anchor", "camera_near_anchor",
                "foreground_table_approach_anchor", "foreground_table_lean_anchor",
                "bed_approach_anchor", "bed_edge_sit_anchor", "bed_relax_anchor",
                "bed_lie_anchor", "bed_exit_anchor", "chair_approach_anchor",
                "chair_sit_anchor", "window_anchor", "dresser_anchor", "mirror_anchor",
                "shelf_anchor", "lamp_anchor")) {
            require(anchors.containsKey(required), "required anchor " + required);
        }

        return new CelineRoomWorldContractV80(
                width, depth, height, bedY, chairY, tableY,
                colliders.length(), edges.length(), contactEdges.length(), anchors);
    }

    Anchor anchor(String id) {
        return id == null ? null : anchors.get(id);
    }

    String diagnosticSummary() {
        return "room=" + roomId + " sha256=" + roomSha256
                + " shell=" + width + "x" + depth + "x" + height
                + " sources=" + sourceAssetCount + " instances=" + furnitureInstanceCount
                + " anchors=" + anchors.size() + " colliders=" + colliderCount
                + " navEdges=" + navEdgeCount + " contactEdges=" + contactEdgeCount
                + " contactY=" + bedMattressY + "/" + chairSeatY + "/"
                + foregroundTableTopY + " 9R=false";
    }

    private static Map<String, Anchor> parseAnchors(
            JSONObject locked, JSONObject prepared,
            float bedY, float chairY, float tableY) throws Exception {
        Map<String, Anchor> result = new LinkedHashMap<>();
        Iterator<String> ids = locked.keys();
        while (ids.hasNext()) {
            String id = ids.next();
            require(prepared.has(id), "prepared anchor mirrors " + id);
            JSONObject value = locked.getJSONObject(id);
            JSONArray xz = value.getJSONArray("world_xz_m");
            float x = finite(xz, 0, id + " x");
            float sourceZ = finite(xz, 1, id + " z");
            float z = "camera_talk_anchor".equals(id)
                    ? RECONSTRUCTION_TALK_LOCAL_Z : sourceZ;
            float y = value.isNull("world_y_m")
                    ? resolvedContactY(id, bedY, chairY, tableY)
                    : finite(value, "world_y_m");
            float facing = value.isNull("facing_y_deg")
                    ? Float.NaN : finite(value, "facing_y_deg");
            Anchor anchor = new Anchor(
                    id,
                    value.optString("kind", "unknown"),
                    nullableString(value, "object_id"),
                    nullableString(value, "approach_anchor"),
                    nullableString(value, "pose_mode"),
                    x, y, z, facing,
                    (float) value.optDouble("clearance_radius_m", 0.0),
                    value.optBoolean("contact_calibration_required", false));
            result.put(id, anchor);
        }
        return result;
    }

    private static float resolvedContactY(
            String id, float bedY, float chairY, float tableY) {
        if ("foreground_table_lean_anchor".equals(id)) return tableY;
        if ("chair_sit_anchor".equals(id)) return chairY;
        if ("bed_edge_sit_anchor".equals(id)
                || "bed_relax_anchor".equals(id)
                || "bed_lie_anchor".equals(id)) return bedY;
        return 0.0f;
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

    private static String nullableString(JSONObject object, String key) {
        return object.isNull(key) ? null : object.optString(key, null);
    }

    private static float finitePositive(JSONObject object, String key) throws Exception {
        float value = finite(object, key);
        require(value > 0.0f, key + " positive");
        return value;
    }

    private static float finite(JSONObject object, String key) throws Exception {
        double value = object.getDouble(key);
        require(!Double.isNaN(value) && !Double.isInfinite(value), key + " finite");
        return (float) value;
    }

    private static float finite(JSONArray array, int index, String label) throws Exception {
        double value = array.getDouble(index);
        require(!Double.isNaN(value) && !Double.isInfinite(value), label + " finite");
        return (float) value;
    }

    private static boolean arrayContains(JSONArray array, String expected) throws Exception {
        for (int i = 0; i < array.length(); i++) {
            if (expected.equals(array.getString(i))) return true;
        }
        return false;
    }

    private static void requireClose(float actual, float expected, String label) throws Exception {
        require(Math.abs(actual - expected) <= 0.001f,
                label + " expected=" + expected + " actual=" + actual);
    }

    private static void require(boolean condition, String label) throws Exception {
        if (!condition) throw new IllegalStateException("4R room contract invalid: " + label);
    }
}