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
 * Post-v83 S25 user-authored native-frame contract. Legacy 9R geometry is NOT calibrated.
 *
 * The room action graph is data-only in 4R. Walking, contact poses, natural-language commands and
 * autonomous room behavior remain deliberately disabled until the later 9R phase.
 */
final class CelineRoomWorldContractV80 {
    static final String WORLD_PATH = "models/room/celine_room_v80_world_contract.json";
    static final String ASSEMBLY_PATH = "models/room/celine_room_v80_assembly.json";
    static final String ANCHORS_PATH = "models/room/celine_room_v80_anchors.json";
    static final String NAV_COLLISION_PATH = "models/room/celine_room_v80_nav_collision.json";
    static final String S25_LAYOUT_PATH = "models/room/layouts/YahyaAI_Raumlayout_FINAL_2026-10-31.json";
    static final String S25_LAYOUT_SHA256 = "471a0cbfd267299f878e74783d003dfc808a6fb2c3fc6449e1d62acc7ca7611f";

    static final float RUNTIME_OFFSET_X = 0.0f;
    static final float RUNTIME_OFFSET_Y = 0.0f;
    static final float RUNTIME_OFFSET_Z = 0.0f;

    // The old photo-fitted HOME depth override does not belong to the S25-authored world.
    // Draft navigation/contact anchors remain unverified until real transformed mesh proof.

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
        requireClose(width, 4.4f, "room width");
        requireClose(depth, 4.2f, "room depth");
        requireClose(height, 2.65f, "room height");

        JSONObject viewer = world.getJSONObject("viewer");
        require("laptop_webcam".equals(viewer.optString("type")), "viewer type");
        require(!viewer.optBoolean("render_laptop", true), "laptop rendering disabled");
        require(viewer.optBoolean("foreground_table_visible", false),
                "foreground table visible");

        JSONObject contacts = world.getJSONObject("contact_planes");
        float bedY = finite(contacts, "bed_mattress_y_m");
        float chairY = finite(contacts, "chair_seat_y_m");
        float tableY = finite(contacts, "foreground_table_top_y_m");
        // Old asset contact heights are recorded for compatibility, NOT S25 contact proof.
        require("UNVERIFIED_LEGACY".equals(world.optString("contact_planes_status")),
                "S25 contact calibration not accepted");

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
        JSONObject assemblyShell = assembly.getJSONObject("room_shell_m");
        requireClose(finitePositive(assemblyShell, "width"), width, "assembly S25 width");
        requireClose(finitePositive(assemblyShell, "depth"), depth, "assembly S25 depth");
        requireClose(finitePositive(assemblyShell, "height"), height, "assembly S25 height");
        require(S25_LAYOUT_SHA256.equals(assembly.optString("authoritative_layout_sha256")),
                "S25 assembly authority SHA256");
        validateS25Assembly(assembly, readJson(context, S25_LAYOUT_PATH), width, depth, height);

        require("PENDING_S25_GEOMETRY_PROOF".equals(anchorDocument.optString("s25_status")),
                "anchor geometry pending, not accepted");
        JSONObject anchorSource = anchorDocument.getJSONObject("anchors");
        JSONObject lockedAnchors = world.getJSONObject("anchors");
        require(anchorSource.length() == ANCHOR_COUNT, "prepared anchor count");
        require(lockedAnchors.length() == ANCHOR_COUNT, "locked anchor count");
        Map<String, Anchor> anchors = parseAnchors(
                lockedAnchors, anchorSource, bedY, chairY, tableY);

        require(nav.optInt("schema") == 1, "nav schema");
        require("PENDING_S25_MESH_AABBS".equals(nav.optString("s25_status")),
                "collision geometry pending, not accepted");
        JSONObject navBounds = nav.getJSONObject("room_bounds_m");
        JSONArray navMin = navBounds.getJSONArray("min");
        JSONArray navMax = navBounds.getJSONArray("max");
        requireClose(finite(navMin, 0, "nav min x"), -width / 2f, "S25 nav min x");
        requireClose(finite(navMin, 1, "nav min y"), 0f, "S25 nav floor");
        requireClose(finite(navMin, 2, "nav min z"), -depth / 2f, "S25 nav min z");
        requireClose(finite(navMax, 0, "nav max x"), width / 2f, "S25 nav max x");
        requireClose(finite(navMax, 1, "nav max y"), height, "S25 nav height");
        requireClose(finite(navMax, 2, "nav max z"), depth / 2f, "S25 nav max z");
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
                + foregroundTableTopY
                + " s25NavCollision=UNVERIFIED s25Contact=UNVERIFIED"
                + " s25RuntimeAndVisual=UNVERIFIED";
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
            float z = sourceZ; // No reference-photo-derived talk-root override.
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


    // Retain all original independent editor fields; legacy yaw/scale never substitute
    // for full Euler rotations and XYZ scale. The canonical JSON remains byte-unmodified.
    private static void validateS25Assembly(
            JSONObject assembly, JSONObject layout, float width, float depth, float height)
            throws Exception {
        require("yahya-room-layout-editor/v1".equals(layout.optString("schema")),
                "S25 layout schema");
        require("2026-10-31T03:29:20.855Z".equals(layout.optString("saved_at")),
                "S25 layout checkpoint");
        JSONObject room = layout.getJSONObject("room");
        requireClose(finitePositive(room, "width"), width, "S25 layout width");
        requireClose(finitePositive(room, "depth"), depth, "S25 layout depth");
        requireClose(finitePositive(room, "height"), height, "S25 layout height");
        JSONArray authored = layout.getJSONArray("objects");
        JSONArray objects = assembly.getJSONArray("objects");
        require(authored.length() == FURNITURE_INSTANCES, "S25 author object count");
        Map<String, JSONObject> byId = new LinkedHashMap<>();
        for (int i = 0; i < authored.length(); i++) {
            JSONObject source = authored.getJSONObject(i);
            String id = source.getString("id");
            require(!byId.containsKey(id), "duplicate author id " + id);
            byId.put(id, source);
        }
        for (int i = 0; i < objects.length(); i++) {
            JSONObject entry = objects.getJSONObject(i);
            String id = entry.getString("id");
            JSONObject source = byId.remove(id);
            require(source != null, "missing or duplicated S25 assembly id " + id);
            JSONObject transform = entry.getJSONObject("transform");
            requireNumbersEqual(transform.getJSONArray("translation_m"),
                    source.getJSONArray("translation_m"), id + " XYZ");
            requireNumbersEqual(transform.getJSONArray("rotation_euler_deg"),
                    source.getJSONArray("rotation_euler_deg"), id + " Euler");
            requireNumbersEqual(transform.getJSONArray("scale_xyz"),
                    source.getJSONArray("scale_xyz"), id + " XYZ scale");
            require(Double.compare(transform.getDouble("rotation_y_deg"),
                    source.getDouble("rotation_y_deg")) == 0, id + " legacy yaw");
            require(Double.compare(transform.getDouble("uniform_scale"),
                    source.getDouble("uniform_scale")) == 0, id + " legacy scale");
            require(transform.getBoolean("locked") == source.getBoolean("locked"),
                    id + " lock");
        }
        require(byId.isEmpty(), "unmatched S25 furniture ids");
    }

    private static void requireNumbersEqual(
            JSONArray actual, JSONArray expected, String label) throws Exception {
        require(actual.length() == 3 && expected.length() == 3, label + " length");
        for (int i = 0; i < 3; i++) {
            double a = actual.getDouble(i);
            double b = expected.getDouble(i);
            require(Double.isFinite(a) && Double.isFinite(b) && Double.compare(a, b) == 0,
                    label + " entry " + i + " mismatch");
        }
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