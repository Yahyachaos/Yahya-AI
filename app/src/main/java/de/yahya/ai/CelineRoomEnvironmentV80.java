package de.yahya.ai;

import android.content.Context;
import android.opengl.Matrix;

import com.google.android.filament.Box;
import com.google.android.filament.Engine;
import com.google.android.filament.EntityManager;
import com.google.android.filament.LightManager;
import com.google.android.filament.RenderableManager;
import com.google.android.filament.Scene;
import com.google.android.filament.TransformManager;
import com.google.android.filament.gltfio.AssetLoader;
import com.google.android.filament.gltfio.FilamentAsset;
import com.google.android.filament.gltfio.ResourceLoader;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/**
 * v80 Block-3 Filament room environment.
 *
 * Owns only room geometry/material resources and world anchors. It never writes Celine's root,
 * bones, camera, speech, lip-sync or animation state. The legacy Canvas room remains available as
 * a fail-closed runtime fallback when this environment cannot be built.
 */
final class CelineRoomEnvironmentV80 {
    private static final String ROOM_PARTITION_BASE = "models/room/source-fidelity/";
    private static final String ROOM_SHELL_PATH = ROOM_PARTITION_BASE + "room_shell.glb";
    private static final String[] ROOM_PATHS = {
            ROOM_SHELL_PATH,
            ROOM_PARTITION_BASE + "room_bed.glb",
            ROOM_PARTITION_BASE + "room_dresser.glb",
            ROOM_PARTITION_BASE + "room_plant_large.glb",
            ROOM_PARTITION_BASE + "room_plant_small.glb",
            ROOM_PARTITION_BASE + "room_floor_lamp.glb",
            ROOM_PARTITION_BASE + "room_nightstand_rear.glb",
            ROOM_PARTITION_BASE + "room_nightstand_front.glb",
            ROOM_PARTITION_BASE + "room_lounge_chair.glb",
            ROOM_PARTITION_BASE + "room_rug.glb",
            ROOM_PARTITION_BASE + "room_foreground_table.glb",
            ROOM_PARTITION_BASE + "room_window_drapes.glb",
            ROOM_PARTITION_BASE + "room_wall_shelf_books.glb",
            ROOM_PARTITION_BASE + "room_round_mirror.glb"
    };
    // Exactly thirteen user-authored furniture instances, in the same index order
    // as ROOM_PATHS after the shell. "back" is the GLB filename "rear".
    private static final String[] S25_IDS = {
            "room_bed", "room_dresser", "room_plant_large", "room_plant_small",
            "room_floor_lamp", "room_nightstand_back", "room_nightstand_front",
            "room_lounge_chair", "room_rug", "room_foreground_table",
            "room_window_drapes", "room_wall_shelf_books", "room_round_mirror"
    };
    private static final String S25_LAYOUT_ASSET =
            "models/room/layouts/YahyaAI_Raumlayout_FINAL_2026-10-31.json";
    private static final String S25_LAYOUT_SHA256 =
            "471a0cbfd267299f878e74783d003dfc808a6fb2c3fc6449e1d62acc7ca7611f";
    private static final String FLOOR_LAMP_LIGHT_ID = "floor_lamp_light";
    private static final int ROOM_LIGHT_CHANNEL = 1;
    // Phase-A floor-raster #286 showed an underlit, featureless floor while the back
    // wall was already bright. Angle only the Room channel-1 key further downward:
    // measured source normals give ideal floor incidence 0.239 -> 0.500 and
    // back-wall incidence 0.966 -> 0.860 (not a Filament/IBL visual PASS).
    // Retain intensity, color, isolation, shadows and Celine's channel-0 lighting.
    private static final float ROOM_RECOVERY_NEUTRAL_KEY_INTENSITY = 14_000.0f;
    private static final float ROOM_RECOVERY_NEUTRAL_KEY_DIR_X = 0.10f;
    private static final float ROOM_RECOVERY_NEUTRAL_KEY_DIR_Y = -0.50f;
    private static final float ROOM_RECOVERY_NEUTRAL_KEY_DIR_Z = -0.86f;
    // C127 exact Android CALL #322: left/near bare wood remains much darker
    // than the same reference ROIs, while calibrated walls/ceiling are close.
    // A constrained downward, room-channel-only warm fill targets left/near floor;
    // do not retune the shared key or alter the 9R.5 user-toggle lamp.
    // S25 native shell and loaded furniture use the floor-centered XYZ frame.
    // Auxiliary Room light positions must not inherit the old shared 4R runtime
    // offsets. Preserve the source-local fill location and its light parameters.
    private static final float ROOM_WARM_FLOOR_FILL_X = -0.80f;
    private static final float ROOM_WARM_FLOOR_FILL_Y = 2.25f;
    private static final float ROOM_WARM_FLOOR_FILL_Z = 0.75f;
    private static final float ROOM_WARM_FLOOR_FILL_LUMENS = 38_000.0f;
    private static final float ROOM_WARM_FLOOR_FILL_FALLOFF_M = 2.85f;
    private static final float ROOM_WARM_FLOOR_FILL_CONE_INNER_RAD = 0.34f;
    private static final float ROOM_WARM_FLOOR_FILL_CONE_OUTER_RAD = 0.50f;
    // Legacy optional lamp spotlight: retain the pre-existing room-local bulb
    // candidate until actual S25 GLB bulb geometry and ON/OFF pixels are checked.
    // Only remove the obsolete WorldContract offset that placed this light well
    // outside the native 4.40 x 4.20 x 2.65 m shell. Never move the GLB itself.
    private static final float FLOOR_LAMP_LIGHT_X = -1.71406245f;
    private static final float FLOOR_LAMP_LIGHT_Y = 1.45f;
    private static final float FLOOR_LAMP_LIGHT_Z = -1.70734370f;
    private static final float FLOOR_LAMP_LIGHT_LUMENS = 60000.0f;
    // 9R.5 exact real ON/OFF proof #273 changed 0 static wall/floor pixels.
    // The former fitted-chair aim was behind this bulb toward the opaque back
    // wall (old dir z=-0.31021); its focused pool was outside the visible floor.
    // Aim from the unchanged derived physical lamp bulb (-1.71406245,1.45,
    // -1.70734370) at a nearby source-room floor patch (-1.45,0.0,-1.00),
    // beside/in front of the lounge chair and inside the locked 4.40x4.20 room.
    // Unit direction = normalize((0.26406245,-1.45,0.70734370)), length=1.6348m.
    // This is a bounded lamp-only geometric/raster experiment: preserve default
    // OFF and accepted 9R.5 ON/OFF, 60 klm, 3.4m falloff, cones and room channel.
    // Retain only if actual same-runtime Android ON/OFF ROOM pixels improve;
    // rollback on zero delta, clipping, blowout or new interaction regressions.
    private static final float FLOOR_LAMP_LIGHT_FALLOFF_M = 3.4f;
    private static final float FLOOR_LAMP_LIGHT_DIR_X = 0.16152605f;
    private static final float FLOOR_LAMP_LIGHT_DIR_Y = -0.88695980f;
    private static final float FLOOR_LAMP_LIGHT_DIR_Z = 0.43267961f;
    private static final float FLOOR_LAMP_SPOT_INNER_RAD = 0.41887902f; // 24 degrees
    private static final float FLOOR_LAMP_SPOT_OUTER_RAD = 0.69813170f; // 40 degrees
    private static final WeakHashMap<Celine3DView, State> STATES = new WeakHashMap<>();

    static final class SeatAnchor {
        final float centerX;
        final float centerY;
        final float centerZ;
        final float normalX;
        final float normalY;
        final float normalZ;
        final float forwardX;
        final float forwardY;
        final float forwardZ;
        final float width;
        final float depth;
        final float floorY;
        final float backrestX;
        final float backrestY;
        final float backrestZ;
        final int roomRootEntity;

        SeatAnchor(float centerX, float centerY, float centerZ,
                   float normalX, float normalY, float normalZ,
                   float forwardX, float forwardY, float forwardZ,
                   float width, float depth, float floorY,
                   float backrestX, float backrestY, float backrestZ,
                   int roomRootEntity) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.centerZ = centerZ;
            this.normalX = normalX;
            this.normalY = normalY;
            this.normalZ = normalZ;
            this.forwardX = forwardX;
            this.forwardY = forwardY;
            this.forwardZ = forwardZ;
            this.width = width;
            this.depth = depth;
            this.floorY = floorY;
            this.backrestX = backrestX;
            this.backrestY = backrestY;
            this.backrestZ = backrestZ;
            this.roomRootEntity = roomRootEntity;
        }

        String diagnosticSummary() {
            return "seat=" + centerX + "," + centerY + "," + centerZ
                    + " size=" + width + "x" + depth
                    + " floorY=" + floorY
                    + " root=" + roomRootEntity;
        }
    }

    private CelineRoomEnvironmentV80() {}

    static boolean ensure(Context context, Celine3DView view) {
        if (context == null || view == null) return false;
        // Main-thread attachment callbacks must not enter the worker-owned gltfio.
        // This dispatch never blocks UI and does not claim Room built until proven.
        if (!view.isFilamentOwnerThread()) {
            view.dispatchFilamentWork(() -> ensure(context, view));
            return false;
        }
        State state;
        synchronized (STATES) {
            state = STATES.get(view);
            if (state == null) {
                try {
                    state = new State(context.getApplicationContext(), view);
                    STATES.put(view, state);
                } catch (Throwable error) {
                    Celine3DDiagnostics.error(context, "ROOM-199",
                            "Filament-Raum Initialisierung FEHLER", error);
                    return false;
                }
            }
        }
        return state.ensureBuilt();
    }

    static void release(Celine3DView view) {
        if (view == null) return;
        // Detach may arrive from both the backdrop and avatar. This serial
        // cleanup is idempotent and runs after any already-queued room import.
        view.dispatchFilamentWork(() -> {
            State state;
            synchronized (STATES) { state = STATES.remove(view); }
            if (state != null) state.destroyRoom();
        });
    }

    static boolean isActive(Celine3DView view) {
        if (view == null) return false;
        synchronized (STATES) {
            State state = STATES.get(view);
            return state != null && state.isBuilt();
        }
    }

    static SeatAnchor getSeatAnchor(Celine3DView view) {
        if (view == null) return null;
        synchronized (STATES) {
            State state = STATES.get(view);
            return state == null ? null : state.seatAnchor;
        }
    }

    static CelineRoomWorldContractV80 getWorldContract(Celine3DView view) {
        if (view == null) return null;
        synchronized (STATES) {
            State state = STATES.get(view);
            return state == null ? null : state.worldContract;
        }
    }

    static CelineRoomWorldContractV80.Anchor getWorldAnchor(
            Celine3DView view, String anchorId) {
        CelineRoomWorldContractV80 contract = getWorldContract(view);
        return contract == null ? null : contract.anchor(anchorId);
    }

    /**
     * 9R.5 Lamp owns one real localized Filament focused spot light, not an emissive-material fake.
     * The app exposes only one active room, so a bounded Lamp interaction may toggle the currently
     * built room state without taking transform ownership from CelineProductionPresenceV80.
     */
    static boolean toggleActiveFloorLamp() {
        State target = null;
        synchronized (STATES) {
            for (State state : STATES.values()) {
                if (state != null && state.isBuilt()) { target = state; break; }
            }
        }
        if (target == null) return false;
        final State active = target;
        // A successful enqueue is an accepted interaction, not proof of a
        // synchronous GPU effect; the light JNI always runs on Engine owner.
        active.view.dispatchFilamentWork(() -> {
            synchronized (STATES) {
                if (STATES.get(active.view) != active) return;
            }
            active.toggleFloorLamp();
        });
        return true;
    }

    private static final class State {
        final Context context;
        final Celine3DView view;
        final Engine engine;
        final Scene scene;
        final TransformManager transforms;
        final AssetLoader assetLoader;
        final ResourceLoader resourceLoader;

        final ArrayList<FilamentAsset> roomAssets = new ArrayList<>();
        FilamentAsset roomShellAsset;
        volatile SeatAnchor seatAnchor;
        volatile CelineRoomWorldContractV80 worldContract;
        int recoveryRoomKeyLightEntity;
        int warmFloorFillLightEntity;
        int floorLampLightEntity;
        boolean floorLampLightEnabled;
        boolean failureLogged;
        // Read by Android Canvas/UI while gltfio imports run on the Filament owner.
        // Do not lock a multi-second native ResourceLoader transaction from onDraw.
        // Publish true only after the entire Room (including lights/materials) is ready.
        private volatile boolean roomBuilt;

        State(Context context, Celine3DView view) throws Exception {
            this.context = context;
            this.view = view;
            engine = (Engine) field(view, "engine");
            scene = (Scene) field(view, "scene");
            transforms = engine.getTransformManager();
            assetLoader = (AssetLoader) field(view, "assetLoader");
            resourceLoader = (ResourceLoader) field(view, "resourceLoader");
        }

        boolean isBuilt() {
            return roomBuilt;
        }

        synchronized boolean ensureBuilt() {
            if (isBuilt()) return true;
            ArrayList<FilamentAsset> candidates = new ArrayList<>();
            try {
                CelineRoomWorldContractV80 contract = CelineRoomWorldContractV80.load(context);
                Map<String, JSONObject> exactLayout = readExactS25Layout();
                int exactApplied = 0;
                int verifiedFurnitureMeshNodes = 0;
                // Expected GLB world matrices captured from actual Filament nodes, not JSON guesses.
                Map<Integer, float[]> expectedS25Roots = new LinkedHashMap<>();
                Map<Integer, float[]> expectedS25Meshes = new LinkedHashMap<>();
                FilamentAsset shell = null;
                int renderables = 0;
                for (int partitionIndex = 0; partitionIndex < ROOM_PATHS.length; partitionIndex++) {
                    final String roomPath = ROOM_PATHS[partitionIndex];
                    ByteBuffer source = readAsset(context, roomPath);
                    FilamentAsset part = assetLoader.createAsset(source);
                    if (part == null) {
                        throw new IllegalStateException("gltfio konnte Raum-Partition nicht laden: " + roomPath);
                    }
                    try {
                        resourceLoader.loadResources(part);
                        part.releaseSourceData();
                        int partRenderables = countRenderables(part);
                        if (partRenderables <= 0) {
                            throw new IllegalStateException("Raum-Partition ohne Renderables: " + roomPath);
                        }
                        if (partitionIndex == 0) {
                            // Native S25 shell and 13 furniture GLBs share the
                            // floor-centered XYZ frame. No legacy 180-degree yaw or
                            // WorldContract offset is allowed on the shell alone.
                            verifyNativeS25ShellFrame(part);
                        } else {
                            String authoredId = S25_IDS[partitionIndex - 1];
                            verifiedFurnitureMeshNodes += applyExactS25Furniture(
                                    part, authoredId, exactLayout.get(authoredId),
                                    expectedS25Roots, expectedS25Meshes);
                            exactApplied++;
                        }
                        // Preserve original S25 GLB material/UV/texture without image-derived tint.
                        candidates.add(part);
                        renderables += partRenderables;
                        if (ROOM_SHELL_PATH.equals(roomPath)) shell = part;
                    } catch (Throwable error) {
                        try { assetLoader.destroyAsset(part); } catch (Throwable ignored) {}
                        throw error;
                    }
                }
                if (shell == null || exactApplied != 13 || verifiedFurnitureMeshNodes < 13) {
                    throw new IllegalStateException("S25 layout requires shell plus exactly 13 GLB instances: " + exactApplied);
                }
                validateWorldEntities(candidates, contract);

                for (FilamentAsset part : candidates) scene.addEntities(part.getEntities());
                // The native floor-centered shell must also be physically attached;
                // valid imported root matrices alone cannot establish Scene membership.
                verifyNativeS25ShellAfterAttach(shell);
                // Verify the same thirteen authored GLB hierarchies after Scene attachment.
                // A pre-attachment root readback alone must not count as running scene proof.
                verifyS25WorldNodesAfterAttach(expectedS25Roots, expectedS25Meshes,
                        exactApplied, verifiedFurnitureMeshNodes);
                int frontWallEntity = shell.getFirstEntityByName("room_shell_front");
                if (frontWallEntity == 0) {
                    throw new IllegalStateException("Recovery shell front entity missing");
                }
                scene.removeEntity(frontWallEntity);
                roomAssets.addAll(candidates);
                candidates.clear();
                roomShellAsset = shell;
                worldContract = contract;
                // Historical seat geometry lies outside the authored S25 room bounds.
                // Fail closed until S25 GLB chair/bed contact is calibrated and proved.
                // Do not derive a seat position from the reference photo or legacy offsets.
                seatAnchor = null;
                configureRoomLightChannels();
                createRecoveryRoomKeyLight();
                createWarmFloorFillLight();
                createFloorLampLight();
                // Real-source shell's top-face UVs are meter-remapped; keep furniture/walls as-is.
                // Texture failure is reported, never misrepresented as floor visual acceptance.
                try {
                    CelineRoomSurfaceTextureV80.applyFloor(view, shell, engine);
                } catch (Throwable floorError) {
                    Celine3DDiagnostics.error(context, "ROOM-145",
                            "Holzboden-Bindung FEHLER; Original-Boden unveraendert", floorError);
                }
                // S25 scene is the shell plus thirteen user-authored furniture GLBs:
                // reject reference-photo artwork, extra laptop and candle geometry.
                Celine3DDiagnostics.record(context, "ROOM-100",
                        "Source-PBR Filament-Raum aktiv",
                        "partitions=" + roomAssets.size() + " renderables=" + renderables
                                + " strategy=4M_partitioned_source_fidelity");
                Celine3DDiagnostics.record(context, "ROOM-105",
                        "4R Weltvertrag aktiv", contract.diagnosticSummary());
                Celine3DDiagnostics.record(context, "ROOM-110",
                        "S25 Sitzkontakt nicht freigegeben",
                        "seatAnchor=null legacySeatOutsideS25=true contactGeometry=UNVERIFIED");
                Celine3DDiagnostics.record(context, "ROOM-116",
                        "Recovery-Geometrie aktiv",
                        "sourceGlbsImmutable=true roomPartitions14=true authoredFurnitureInstances13=true extraReferenceGeometry=false furnitureOrientationOverride=false furnitureSharedRootYawDeg=0 shellNativeS25Frame=true sharedWorldContractS25Dimensions=true contactCalibrated=false frontWallHidden=true originalDrapeMaterial=true sharedCelineKeyMutated=false roomLightChannel=1 neutralKeyIntensity="
                                + ROOM_RECOVERY_NEUTRAL_KEY_INTENSITY);
                Celine3DDiagnostics.record(context, "ROOM-120",
                        "9R.5 Lampenlicht bereit",
                        "entity=" + FLOOR_LAMP_LIGHT_ID
                                + " type=FOCUSED_SPOT enabled=false lumens="
                                + FLOOR_LAMP_LIGHT_LUMENS + " falloff="
                                + FLOOR_LAMP_LIGHT_FALLOFF_M + "m position="
                                + FLOOR_LAMP_LIGHT_X + "," + FLOOR_LAMP_LIGHT_Y + ","
                                + FLOOR_LAMP_LIGHT_Z + " direction="
                                + FLOOR_LAMP_LIGHT_DIR_X + "," + FLOOR_LAMP_LIGHT_DIR_Y + ","
                                + FLOOR_LAMP_LIGHT_DIR_Z + " coneRad="
                                + FLOOR_LAMP_SPOT_INNER_RAD + "/" + FLOOR_LAMP_SPOT_OUTER_RAD
                                + " materialEmission=false");
                failureLogged = false;
                roomBuilt = true;
                // Emit whole-scene 13/13 evidence only after scene attachment and
                // all resource/light setup succeeds; partial imports must never
                // produce a misleading completed-room diagnostic.
                Celine3DDiagnostics.record(context, "ROOM-S25-13",
                        "13/13 S25 GLB roots and descendant nodes verified after Room build",
                        "verifiedFurniture=" + exactApplied
                                + " verifiedMeshNodes=" + verifiedFurnitureMeshNodes
                                + " postAttachRootNodes=" + expectedS25Roots.size()
                                + " postAttachMeshNodes=" + expectedS25Meshes.size()
                                + " sourceSHA256=" + S25_LAYOUT_SHA256
                                + " room=4.4x4.2x2.65"
                                + " shellAndWorldContract=UNVERIFIED visual=UNVERIFIED G2.3=PAUSED");
                return true;
            } catch (Throwable error) {
                for (FilamentAsset candidate : candidates) {
                    try { assetLoader.destroyAsset(candidate); } catch (Throwable ignored) {}
                }
                if (!roomAssets.isEmpty() || recoveryRoomKeyLightEntity != 0
                        || warmFloorFillLightEntity != 0 || floorLampLightEntity != 0) {
                    try { destroyRoom(); } catch (Throwable ignored) {}
                } else {
                    roomAssets.clear();
                    roomShellAsset = null;
                    seatAnchor = null;
                    worldContract = null;
                    recoveryRoomKeyLightEntity = 0;
                    warmFloorFillLightEntity = 0;
                }
                if (!failureLogged) {
                    failureLogged = true;
                    Celine3DDiagnostics.error(context, "ROOM-199",
                            "Filament-Raum FEHLER - Canvas-Fallback bleibt aktiv", error);
                }
                return false;
            }
        }


        /**
         * Fail-closed source read. Never rewrite, clamp or normalize the S25 export.
         * The legacy yaw/scale and locked flags remain independent JSON metadata.
         */
        private Map<String, JSONObject> readExactS25Layout() throws Exception {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(8192);
            try (InputStream in = context.getAssets().open(S25_LAYOUT_ASSET)) {
                byte[] block = new byte[4096];
                int count;
                while ((count = in.read(block)) != -1) {
                    if (count > 0) bytes.write(block, 0, count);
                }
            }
            byte[] raw = bytes.toByteArray();
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(raw);
            StringBuilder digest = new StringBuilder(64);
            for (byte b : hash) {
                digest.append(Character.forDigit((b >>> 4) & 15, 16));
                digest.append(Character.forDigit(b & 15, 16));
            }
            if (!S25_LAYOUT_SHA256.equals(digest.toString())) {
                throw new IllegalStateException("Canonical S25 source bytes changed: " + digest);
            }
            JSONObject json = new JSONObject(new String(raw, StandardCharsets.UTF_8));
            if (!"yahya-room-layout-editor/v1".equals(json.getString("schema"))) {
                throw new IllegalStateException("Unexpected S25 layout schema");
            }
            JSONObject room = json.getJSONObject("room");
            JSONObject originalRoom = json.getJSONObject("original_room_m");
            for (JSONObject dimensions : new JSONObject[] { room, originalRoom }) {
                if (dimensions.getDouble("width") != 4.4
                        || dimensions.getDouble("depth") != 4.2
                        || dimensions.getDouble("height") != 2.65) {
                    throw new IllegalStateException("S25 room shell dimensions are not 4.4x4.2x2.65m");
                }
            }
            JSONArray objects = json.getJSONArray("objects");
            if (objects.length() != S25_IDS.length) {
                throw new IllegalStateException("S25 requires exactly 13 author objects");
            }
            Map<String, JSONObject> byId = new LinkedHashMap<>();
            Set<String> expected = new HashSet<>();
            for (String id : S25_IDS) expected.add(id);
            for (int i = 0; i < objects.length(); i++) {
                JSONObject item = objects.getJSONObject(i);
                String id = item.getString("id");
                if (!expected.contains(id) || byId.put(id, item) != null) {
                    throw new IllegalStateException("Unexpected or duplicate S25 furniture ID: " + id);
                }
                requireFiniteTriple(item, "translation_m", false);
                requireFiniteTriple(item, "rotation_euler_deg", false);
                requireFiniteTriple(item, "scale_xyz", true);
                if (!Double.isFinite(item.getDouble("rotation_y_deg"))
                        || !Double.isFinite(item.getDouble("uniform_scale"))) {
                    throw new IllegalStateException("Invalid independent S25 metadata: " + id);
                }
                item.getBoolean("locked");
            }
            if (byId.size() != S25_IDS.length) {
                throw new IllegalStateException("Missing S25 furniture IDs");
            }
            return byId;
        }

        private double[] requireFiniteTriple(JSONObject object, String field, boolean positive)
                throws Exception {
            JSONArray a = object.getJSONArray(field);
            if (a.length() != 3) throw new IllegalStateException("Invalid S25 " + field);
            double[] values = new double[3];
            for (int i = 0; i < 3; i++) {
                values[i] = a.getDouble(i);
                if (!Double.isFinite(values[i]) || (positive && values[i] <= 0)) {
                    throw new IllegalStateException("Invalid S25 " + field + " axis " + i);
                }
            }
            return values;
        }

        /**
         * THREE editor: wrapper T(position)*RZ*RY*RX*S(scale_xyz);
         * GLTFLoader scene offset T(-Box3.setFromObject(model).getCenter()).
         * gltfio already owns the identical unmodified GLB hierarchy; therefore
         * set ROOT to wrapper*T(-source-bounds-center)*original-root. Never
         * apply the legacy 180deg root yaw or image-fitted drape/dresser offsets.
         */
        private int applyExactS25Furniture(FilamentAsset part, String id, JSONObject item,
                Map<Integer, float[]> expectedS25Roots,
                Map<Integer, float[]> expectedS25Meshes) throws Exception {
            if (item == null) throw new IllegalStateException("Missing S25 transform " + id);
            // Verify that the *loaded partition* owns this furniture anchor, not
            // merely that the combined scene contains an anchor with this name.
            // The user id "back" intentionally maps to the GLB's "rear" anchor.
            String sourceAnchor = ("room_nightstand_back".equals(id)
                    ? "room_nightstand_rear" : id) + "__anchor";
            int anchorEntity = part.getFirstEntityByName(sourceAnchor);
            if (anchorEntity == 0 || transforms.getInstance(anchorEntity) == 0) {
                throw new IllegalStateException("Wrong or unanchored S25 GLB partition "
                        + id + " expected=" + sourceAnchor);
            }
            double[] pos = requireFiniteTriple(item, "translation_m", false);
            double[] euler = requireFiniteTriple(item, "rotation_euler_deg", false);
            double[] scale = requireFiniteTriple(item, "scale_xyz", true);
            int root = transforms.getInstance(part.getRoot());
            if (root == 0) throw new IllegalStateException("Missing GLB root: " + id);
            float[] originalRoot = transforms.getTransform(root, new float[16]);
            float[] center = originalGeometryCenter(part);
            // Snapshot the REAL loaded GLB renderable nodes before changing their
            // common gltfio root. Root readback alone cannot prove descendant motion.
            RenderableManager meshManager = engine.getRenderableManager();
            Map<Integer, float[]> originalMeshWorlds = new LinkedHashMap<>();
            for (int entity : part.getEntities()) {
                if (!meshManager.hasComponent(entity)) continue;
                int meshTransform = transforms.getInstance(entity);
                if (meshTransform == 0) {
                    throw new IllegalStateException("S25 renderable has no transform: " + id);
                }
                originalMeshWorlds.put(entity,
                        transforms.getWorldTransform(meshTransform, new float[16]));
            }
            if (originalMeshWorlds.isEmpty()) {
                throw new IllegalStateException("S25 furniture GLB has no renderables: " + id);
            }
            float[] wrapper = new float[16];
            float[] target = new float[16];
            Matrix.setIdentityM(wrapper, 0);
            Matrix.translateM(wrapper, 0, (float) pos[0], (float) pos[1], (float) pos[2]);
            Matrix.rotateM(wrapper, 0, (float) euler[2], 0, 0, 1);
            Matrix.rotateM(wrapper, 0, (float) euler[1], 0, 1, 0);
            Matrix.rotateM(wrapper, 0, (float) euler[0], 1, 0, 0);
            Matrix.scaleM(wrapper, 0, (float) scale[0], (float) scale[1], (float) scale[2]);
            Matrix.translateM(wrapper, 0, -center[0], -center[1], -center[2]);
            Matrix.multiplyMM(target, 0, wrapper, 0, originalRoot, 0);
            transforms.setTransform(root, target);

            // Actual Filament root readback: measured float matrix vs serialized
            // target. The untouched JSON doubles retain exact user precision.
            float[] observed = transforms.getTransform(root, new float[16]);
            float worst = 0f;
            for (int i = 0; i < 16; i++) {
                if (!Float.isFinite(observed[i])) {
                    throw new IllegalStateException("Nonfinite Filament S25 root: " + id);
                }
                worst = Math.max(worst, Math.abs(observed[i] - target[i]));
            }
            if (worst > 0.00002f) {
                throw new IllegalStateException("Filament S25 root readback mismatch " + id
                        + " worst=" + worst);
            }
            if (expectedS25Roots.put(part.getRoot(),
                    transforms.getWorldTransform(root, new float[16])) != null) {
                throw new IllegalStateException("Duplicate Filament furniture root entity: " + id);
            }
            // With the unmodified GLB hierarchy, each descendant must move as
            // authoredWrapper * originalWorld. Detect lost/doubled node transforms,
            // not just a successfully written but visually unused root matrix.
            float meshWorst = 0f;
            for (Map.Entry<Integer, float[]> mesh : originalMeshWorlds.entrySet()) {
                int meshTransform = transforms.getInstance(mesh.getKey());
                float[] observedMeshWorld = transforms.getWorldTransform(
                        meshTransform, new float[16]);
                float[] expectedMeshWorld = new float[16];
                Matrix.multiplyMM(expectedMeshWorld, 0, wrapper, 0, mesh.getValue(), 0);
                for (int axis = 0; axis < 16; axis++) {
                    if (!Float.isFinite(observedMeshWorld[axis])
                            || !Float.isFinite(expectedMeshWorld[axis])) {
                        throw new IllegalStateException("Nonfinite S25 GLB node matrix: " + id);
                    }
                    meshWorst = Math.max(meshWorst,
                            Math.abs(observedMeshWorld[axis] - expectedMeshWorld[axis]));
                }
                if (expectedS25Meshes.put(mesh.getKey(), observedMeshWorld) != null) {
                    throw new IllegalStateException("Duplicate S25 renderable entity: " + id);
                }
            }
            if (meshWorst > 0.0001f) {
                throw new IllegalStateException("S25 GLB node world readback mismatch " + id
                        + " worst=" + meshWorst);
            }
            Celine3DDiagnostics.record(context, "ROOM-S25-READBACK",
                    "Verified authored furniture root and " + originalMeshWorlds.size()
                            + " GLB renderable nodes " + id,
                    "translation_m=" + pos[0] + "," + pos[1] + "," + pos[2]
                            + " euler_xyz_deg=" + euler[0] + "," + euler[1] + "," + euler[2]
                            + " scale_xyz=" + scale[0] + "," + scale[1] + "," + scale[2]
                            + " rotation_y_deg_legacy=" + item.getDouble("rotation_y_deg")
                            + " uniform_scale_legacy=" + item.getDouble("uniform_scale")
                            + " locked=" + item.getBoolean("locked")
                            + " originalCenter=" + center[0] + "," + center[1] + "," + center[2]
                            + " rootMatrixMaxError=" + worst
                            + " meshWorldMatrixMaxError=" + meshWorst
                            + " meshNodesChecked=" + originalMeshWorlds.size());
            return originalMeshWorlds.size();
        }

        /**
         * The first 13/13 checks run while gltfio assets are imported. Re-read the
         * actual loaded root and renderable world matrices AFTER Scene.addEntities,
         * without reapplying any transform or modifying the user's JSON values.
         * This is structural proof only, not evidence of a visible Android frame.
         */
        private void verifyS25WorldNodesAfterAttach(
                Map<Integer, float[]> rootWorlds, Map<Integer, float[]> meshWorlds,
                int expectedFurniture, int expectedMeshNodes) {
            if (rootWorlds.size() != expectedFurniture || expectedFurniture != S25_IDS.length
                    || meshWorlds.size() != expectedMeshNodes || expectedMeshNodes < S25_IDS.length) {
                throw new IllegalStateException("Incomplete post-attach S25 scene readback");
            }
            // World matrices alone can be valid even for a renderable absent from
            // the actual Filament Scene. Verify genuine post-attachment membership.
            RenderableManager sceneRenderables = engine.getRenderableManager();
            for (Integer meshEntity : meshWorlds.keySet()) {
                if (!scene.hasEntity(meshEntity)
                        || !sceneRenderables.hasComponent(meshEntity)) {
                    throw new IllegalStateException(
                            "S25 furniture mesh missing from Filament Scene: " + meshEntity);
                }
            }
            Map<Integer, float[]> all = new LinkedHashMap<>(rootWorlds);
            for (Map.Entry<Integer, float[]> node : meshWorlds.entrySet()) {
                if (all.put(node.getKey(), node.getValue()) != null) {
                    throw new IllegalStateException("S25 root/renderable entity collision");
                }
            }
            float worst = 0f;
            for (Map.Entry<Integer, float[]> node : all.entrySet()) {
                int instance = transforms.getInstance(node.getKey());
                if (instance == 0) {
                    throw new IllegalStateException("Detached S25 scene node " + node.getKey());
                }
                float[] actual = transforms.getWorldTransform(instance, new float[16]);
                for (int i = 0; i < 16; i++) {
                    if (!Float.isFinite(actual[i]) || !Float.isFinite(node.getValue()[i])) {
                        throw new IllegalStateException("Nonfinite S25 attached GLB node");
                    }
                    worst = Math.max(worst, Math.abs(actual[i] - node.getValue()[i]));
                }
            }
            if (worst > 0.0001f) {
                throw new IllegalStateException("S25 post-attach node movement mismatch: " + worst);
            }
            Celine3DDiagnostics.record(context, "ROOM-S25-ATTACH",
                    "13/13 GLB scene roots and real descendant renderables unchanged after Scene.addEntities",
                    "rootNodes=" + rootWorlds.size() + " meshNodes=" + meshWorlds.size()
                            + " maxMatrixError=" + worst
                            + " AndroidVisibleFrame=UNVERIFIED shell=UNVERIFIED");
        }

        /**
         * Recheck the REAL source shell after Scene attachment. A gltfio asset
         * can own valid native root transforms even if its meshes were never
         * attached to the Filament Scene. Do not modify furniture or shell TRS.
         * This structural check cannot establish Android raster visibility.
         */
        private void verifyNativeS25ShellAfterAttach(FilamentAsset shell) {
            int authoredRoot = shell.getFirstEntityByName("room_world_root");
            if (authoredRoot == 0) {
                throw new IllegalStateException("Attached S25 shell source root missing");
            }
            for (int entity : new int[] { shell.getRoot(), authoredRoot }) {
                int instance = transforms.getInstance(entity);
                if (instance == 0) {
                    throw new IllegalStateException("Attached S25 shell transform missing");
                }
                float[] world = transforms.getWorldTransform(instance, new float[16]);
                for (int i = 0; i < 16; i++) {
                    float identity = (i % 5 == 0 ? 1.0f : 0.0f);
                    if (!Float.isFinite(world[i])
                            || Math.abs(world[i] - identity) > 0.00002f) {
                        throw new IllegalStateException(
                                "Attached S25 shell displaced from native XYZ frame: "
                                        + entity + " component=" + i);
                    }
                }
            }
            RenderableManager renderables = engine.getRenderableManager();
            int attachedMeshes = 0;
            for (int entity : shell.getEntities()) {
                if (!renderables.hasComponent(entity)) continue;
                if (!scene.hasEntity(entity)) {
                    throw new IllegalStateException(
                            "S25 shell renderable absent from Filament Scene: " + entity);
                }
                int instance = transforms.getInstance(entity);
                if (instance == 0) {
                    throw new IllegalStateException("S25 shell renderable lacks transform");
                }
                float[] world = transforms.getWorldTransform(instance, new float[16]);
                for (float value : world) {
                    if (!Float.isFinite(value)) {
                        throw new IllegalStateException("Nonfinite attached S25 shell mesh");
                    }
                }
                attachedMeshes++;
            }
            if (attachedMeshes == 0) {
                throw new IllegalStateException("Attached S25 shell contains no renderables");
            }
            Celine3DDiagnostics.record(context, "ROOM-S25-SHELL-ATTACH",
                    "Native shell root and real scene renderables verified after attachment",
                    "sourceRoom_m=4.40x4.20x2.65"
                            + " rootWorld=IDENTITY attachedMeshes=" + attachedMeshes
                            + " sharedWorldContract=UNVERIFIED AndroidVisibleFrame=UNVERIFIED");
        }

        /**
         * Mirrors Three.js Box3.setFromObject BEFORE model.position.sub(center).
         * Includes GLB anchor/mesh baked transforms (rather than assuming the
         * authoring anchor's origin is the visible geometry's center).
         */
        private float[] originalGeometryCenter(FilamentAsset part) {
            RenderableManager manager = engine.getRenderableManager();
            float[] low = { Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY };
            float[] high = { Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY };
            int seen = 0;
            for (int entity : part.getEntities()) {
                if (!manager.hasComponent(entity)) continue;
                int renderInstance = manager.getInstance(entity);
                int transformInstance = transforms.getInstance(entity);
                if (renderInstance == 0 || transformInstance == 0) {
                    throw new IllegalStateException("Renderable missing GLB transform: " + entity);
                }
                // Filament 1.72.0 exposes an explicit Box out-argument and
                // float[3] getters (not the newer getCenterX() accessors).
                Box b = manager.getAxisAlignedBoundingBox(renderInstance, new Box());
                float[] matrix = transforms.getWorldTransform(transformInstance, new float[16]);
                float[] c = b.getCenter();
                float[] r = b.getHalfExtent();
                for (int corner = 0; corner < 8; corner++) {
                    float x = c[0] + ((corner & 1) == 0 ? -r[0] : r[0]);
                    float y = c[1] + ((corner & 2) == 0 ? -r[1] : r[1]);
                    float z = c[2] + ((corner & 4) == 0 ? -r[2] : r[2]);
                    float[] v = {
                            matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12],
                            matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13],
                            matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14]
                    };
                    for (int a = 0; a < 3; a++) {
                        low[a] = Math.min(low[a], v[a]);
                        high[a] = Math.max(high[a], v[a]);
                    }
                }
                seen++;
            }
            if (seen == 0) throw new IllegalStateException("No renderable AABBs to center");
            float[] center = new float[3];
            for (int i = 0; i < 3; i++) {
                center[i] = (low[i] + high[i]) * 0.5f;
                if (!Float.isFinite(center[i])) {
                    throw new IllegalStateException("Invalid source GLB geometry center");
                }
            }
            return center;
        }

        private int countRenderables(FilamentAsset asset) {
            RenderableManager manager = engine.getRenderableManager();
            int count = 0;
            for (int entity : asset.getEntities()) {
                if (manager.hasComponent(entity)) count++;
            }
            return count;
        }

        /**
         * The original room_shell.glb defines a 4.40 x 4.20 x 2.65 m
         * floor-centered user XYZ space, exactly like the S25 furniture JSON.
         * The old shell-only 180-degree rotation and runtime offset separated
         * the shell from all thirteen authored furniture instances.
         * Validate both imported GLB roots instead of changing the source.
         * Shared WorldContract, camera and 9R data remain BUILD-owned.
         */
        private void verifyNativeS25ShellFrame(FilamentAsset asset) {
            int authoredRoot = asset.getFirstEntityByName("room_world_root");
            if (authoredRoot == 0) {
                throw new IllegalStateException("S25 shell missing source room_world_root");
            }
            for (int entity : new int[] { asset.getRoot(), authoredRoot }) {
                int instance = transforms.getInstance(entity);
                if (instance == 0) {
                    throw new IllegalStateException("S25 shell root transform missing");
                }
                float[] actual = transforms.getTransform(instance, new float[16]);
                for (int i = 0; i < 16; i++) {
                    float identity = i % 5 == 0 ? 1.0f : 0.0f;
                    if (!Float.isFinite(actual[i])
                            || Math.abs(actual[i] - identity) > 0.00002f) {
                        throw new IllegalStateException(
                                "S25 shell already transformed (no silent yaw/offset): "
                                        + entity + " component=" + i);
                    }
                }
            }
            Celine3DDiagnostics.record(context, "ROOM-S25-SHELL",
                    "Native floor-centered source shell retained without legacy yaw or offset",
                    "interior_m=4.4,4.2,2.65 shellRoot=IDENTITY"
                            + " oldRuntimeOffsetApplied=false"
                            + " sharedWorldContractAndCamera=UNVERIFIED");
        }

        // The obsolete photo-derived adjustment helpers are intentionally removed.
        // Only the byte-verified S25 authoring layout may transform furniture nodes.

        private void validateWorldEntities(
                List<FilamentAsset> assets, CelineRoomWorldContractV80 contract) {
            requireEntity(assets, "room_world_root");
            requireEntity(assets, "room_shell_floor");
            requireEntity(assets, "room_bed__anchor");
            requireEntity(assets, "room_dresser__anchor");
            requireEntity(assets, "room_lounge_chair__anchor");
            requireEntity(assets, "room_foreground_table__anchor");
            requireEntity(assets, "room_floor_lamp__anchor");
            requireEntity(assets, "room_nightstand_rear__anchor");
            requireEntity(assets, "room_nightstand_front__anchor");
            requireEntity(assets, "room_window_drapes__anchor");
            if (contract.anchors.isEmpty()) {
                throw new IllegalStateException("4R anchor metadata missing");
            }
        }

        private void requireEntity(List<FilamentAsset> assets, String name) {
            for (FilamentAsset asset : assets) {
                if (asset != null && asset.getFirstEntityByName(name) != 0) return;
            }
            throw new IllegalStateException("Recovery room entity missing: " + name);
        }

        private void configureRoomLightChannels() {
            RenderableManager renderables = engine.getRenderableManager();
            int isolated = 0;
            for (FilamentAsset asset : roomAssets) {
                if (asset == null) continue;
                for (int entity : asset.getEntities()) {
                    if (!renderables.hasComponent(entity)) continue;
                    int instance = renderables.getInstance(entity);
                    if (instance == 0) continue;
                    renderables.setLightChannel(instance, 0, false);
                    renderables.setLightChannel(instance, ROOM_LIGHT_CHANNEL, true);
                    isolated++;
                }
            }
            if (isolated <= 0) {
                throw new IllegalStateException("Recovery Room light-channel isolation found no renderables");
            }
            Celine3DDiagnostics.record(context, "ROOM-118",
                    "Source-PBR Room-Licht isoliert",
                    "renderables=" + isolated + " roomChannel=" + ROOM_LIGHT_CHANNEL
                            + " sharedCelineKeyMutated=false sourceMaterialsMutated=false");
        }

        private void createRecoveryRoomKeyLight() {
            int entity = EntityManager.get().create();
            try {
                new LightManager.Builder(LightManager.Type.DIRECTIONAL)
                        .color(1.0f, 0.98f, 0.96f)
                        .intensity(ROOM_RECOVERY_NEUTRAL_KEY_INTENSITY)
                        .direction(ROOM_RECOVERY_NEUTRAL_KEY_DIR_X,
                                ROOM_RECOVERY_NEUTRAL_KEY_DIR_Y,
                                ROOM_RECOVERY_NEUTRAL_KEY_DIR_Z)
                        .castShadows(false)
                        .lightChannel(0, false)
                        .lightChannel(ROOM_LIGHT_CHANNEL, true)
                        .build(engine, entity);
                scene.addEntity(entity);
                recoveryRoomKeyLightEntity = entity;
                Celine3DDiagnostics.record(context, "ROOM-119",
                        "Neutraler Source-PBR Room-Key aktiv",
                        "channel=" + ROOM_LIGHT_CHANNEL
                                + " intensity=" + ROOM_RECOVERY_NEUTRAL_KEY_INTENSITY
                                + " direction=" + ROOM_RECOVERY_NEUTRAL_KEY_DIR_X + ","
                                + ROOM_RECOVERY_NEUTRAL_KEY_DIR_Y + ","
                                + ROOM_RECOVERY_NEUTRAL_KEY_DIR_Z
                                + " shadows=false cameraFurnitureCelineUnchanged=true");
            } catch (Throwable error) {
                try { engine.getLightManager().destroy(entity); } catch (Throwable ignored) {}
                try { EntityManager.get().destroy(entity); } catch (Throwable ignored) {}
                throw error;
            }
        }

        private void createWarmFloorFillLight() {
            int entity = EntityManager.get().create();
            try {
                // Center of the beam reaches approximately (-1.24, 0, +0.80)
                // in 4.40x4.20 room-local coordinates. The narrow cone minimizes
                // light on already tuned back/side walls and the ceiling underside.
                new LightManager.Builder(LightManager.Type.FOCUSED_SPOT)
                        .position(ROOM_WARM_FLOOR_FILL_X,
                                ROOM_WARM_FLOOR_FILL_Y, ROOM_WARM_FLOOR_FILL_Z)
                        .direction(-0.19f, -0.98f, 0.02f)
                        .spotLightCone(ROOM_WARM_FLOOR_FILL_CONE_INNER_RAD,
                                ROOM_WARM_FLOOR_FILL_CONE_OUTER_RAD)
                        .color(1.0f, 0.78f, 0.60f)
                        .intensity(ROOM_WARM_FLOOR_FILL_LUMENS)
                        .falloff(ROOM_WARM_FLOOR_FILL_FALLOFF_M)
                        .castShadows(false)
                        .lightChannel(0, false)
                        .lightChannel(ROOM_LIGHT_CHANNEL, true)
                        .build(engine, entity);
                scene.addEntity(entity);
                warmFloorFillLightEntity = entity;
                Celine3DDiagnostics.record(context, "ROOM-146",
                        "Begrenztes warmes Bodenfuelllicht aktiv",
                        "channel=" + ROOM_LIGHT_CHANNEL
                                + " lumens=" + ROOM_WARM_FLOOR_FILL_LUMENS
                                + " falloff=" + ROOM_WARM_FLOOR_FILL_FALLOFF_M
                                + "m shadows=false furnitureCameraCelineUnchanged=true");
            } catch (Throwable error) {
                try { engine.getLightManager().destroy(entity); } catch (Throwable ignored) {}
                try { EntityManager.get().destroy(entity); } catch (Throwable ignored) {}
                throw error;
            }
        }

        private void createFloorLampLight() {
            int entity = EntityManager.get().create();
            try {
                new LightManager.Builder(LightManager.Type.FOCUSED_SPOT)
                        .position(FLOOR_LAMP_LIGHT_X, FLOOR_LAMP_LIGHT_Y, FLOOR_LAMP_LIGHT_Z)
                        .direction(FLOOR_LAMP_LIGHT_DIR_X,
                                FLOOR_LAMP_LIGHT_DIR_Y, FLOOR_LAMP_LIGHT_DIR_Z)
                        .spotLightCone(FLOOR_LAMP_SPOT_INNER_RAD, FLOOR_LAMP_SPOT_OUTER_RAD)
                        .color(1.0f, 0.55f, 0.30f)
                        .intensity(FLOOR_LAMP_LIGHT_LUMENS)
                        .falloff(FLOOR_LAMP_LIGHT_FALLOFF_M)
                        .castShadows(false)
                        .lightChannel(0, false)
                        .lightChannel(ROOM_LIGHT_CHANNEL, false)
                        .build(engine, entity);
                scene.addEntity(entity);
                floorLampLightEntity = entity;
                floorLampLightEnabled = false;
            } catch (Throwable error) {
                try { engine.getLightManager().destroy(entity); } catch (Throwable ignored) {}
                try { EntityManager.get().destroy(entity); } catch (Throwable ignored) {}
                throw error;
            }
        }

        synchronized boolean toggleFloorLamp() {
            if (!isBuilt() || floorLampLightEntity == 0) return false;
            LightManager lights = engine.getLightManager();
            int instance = lights.getInstance(floorLampLightEntity);
            if (instance == 0) return false;
            boolean next = !floorLampLightEnabled;
            lights.setLightChannel(instance, ROOM_LIGHT_CHANNEL, next);
            floorLampLightEnabled = next;
            Celine3DDiagnostics.record(context, "V80-484",
                    "9R.5 Lampenstatus gewechselt",
                    "enabled=" + next + " lightEntity=" + FLOOR_LAMP_LIGHT_ID
                            + " type=FOCUSED_SPOT lumens=" + FLOOR_LAMP_LIGHT_LUMENS
                            + " handContact=false switchTarget=false cameraFixed=true");
            return true;
        }

        synchronized void destroyRoom() {
            // Stop Canvas/interaction readers before releasing owner-thread Filament state.
            roomBuilt = false;
            ArrayList<FilamentAsset> current = new ArrayList<>(roomAssets);
            int roomKeyLight = recoveryRoomKeyLightEntity;
            int floorFillLight = warmFloorFillLightEntity;
            int lampLight = floorLampLightEntity;
            roomAssets.clear();
            roomShellAsset = null;
            seatAnchor = null;
            worldContract = null;
            recoveryRoomKeyLightEntity = 0;
            warmFloorFillLightEntity = 0;
            floorLampLightEntity = 0;
            floorLampLightEnabled = false;
            CelineRoomForegroundPlantV80.releaseCandleOnly(view);
            CelineRoomForegroundLaptopV80.release(view);
            CelineRoomReferenceLeftArtworkV80.release(view, engine);
            CelineRoomReferenceArtworkV80.release(view, engine);
            CelineRoomWindowTextureV80.release(view, engine);
            CelineRoomSurfaceTextureV80.release(view, engine);
            if (current.isEmpty() && roomKeyLight == 0 && floorFillLight == 0 && lampLight == 0) return;
            try {
                if (roomKeyLight != 0) {
                    try { scene.removeEntity(roomKeyLight); } catch (Throwable ignored) {}
                    try { engine.getLightManager().destroy(roomKeyLight); } catch (Throwable ignored) {}
                    try { EntityManager.get().destroy(roomKeyLight); } catch (Throwable ignored) {}
                }
                if (floorFillLight != 0) {
                    try { scene.removeEntity(floorFillLight); } catch (Throwable ignored) {}
                    try { engine.getLightManager().destroy(floorFillLight); } catch (Throwable ignored) {}
                    try { EntityManager.get().destroy(floorFillLight); } catch (Throwable ignored) {}
                }
                if (lampLight != 0) {
                    try { scene.removeEntity(lampLight); } catch (Throwable ignored) {}
                    try { engine.getLightManager().destroy(lampLight); } catch (Throwable ignored) {}
                    try { EntityManager.get().destroy(lampLight); } catch (Throwable ignored) {}
                }
                for (FilamentAsset asset : current) {
                    for (int entity : asset.getEntities()) {
                        try { scene.removeEntity(entity); } catch (Throwable ignored) {}
                    }
                    assetLoader.destroyAsset(asset);
                }
                Celine3DDiagnostics.record(context, "ROOM-130",
                        "Filament-Raum freigegeben", "detach lifecycle cleanup partitions=" + current.size());
            } catch (Throwable error) {
                Celine3DDiagnostics.error(context, "ROOM-198",
                        "Filament-Raum Cleanup FEHLER", error);
            }
        }
    }

    private static ByteBuffer readAsset(Context context, String path) throws Exception {
        try (InputStream in = context.getAssets().open(path)) {
            int expected = Math.max(8_192, in.available());
            ByteBuffer buffer = ByteBuffer.allocateDirect(expected)
                    .order(ByteOrder.nativeOrder());
            byte[] chunk = new byte[64 * 1024];
            int read;
            while ((read = in.read(chunk)) >= 0) {
                if (read <= 0) continue;
                if (buffer.remaining() < read) {
                    int needed = buffer.position() + read;
                    int capacity = buffer.capacity();
                    while (capacity < needed) {
                        capacity = Math.max(capacity + 8_192, capacity * 2);
                    }
                    ByteBuffer grown = ByteBuffer.allocateDirect(capacity)
                            .order(ByteOrder.nativeOrder());
                    buffer.flip();
                    grown.put(buffer);
                    buffer = grown;
                }
                buffer.put(chunk, 0, read);
            }
            buffer.flip();
            return buffer;
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
