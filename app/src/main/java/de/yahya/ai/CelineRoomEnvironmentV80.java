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
    private static final float ROOM_WARM_FLOOR_FILL_X = -0.80f
            + CelineRoomWorldContractV80.RUNTIME_OFFSET_X;
    private static final float ROOM_WARM_FLOOR_FILL_Y = 2.25f
            + CelineRoomWorldContractV80.RUNTIME_OFFSET_Y;
    private static final float ROOM_WARM_FLOOR_FILL_Z = 0.75f
            + CelineRoomWorldContractV80.RUNTIME_OFFSET_Z;
    private static final float ROOM_WARM_FLOOR_FILL_LUMENS = 38_000.0f;
    private static final float ROOM_WARM_FLOOR_FILL_FALLOFF_M = 2.85f;
    private static final float ROOM_WARM_FLOOR_FILL_CONE_INNER_RAD = 0.34f;
    private static final float ROOM_WARM_FLOOR_FILL_CONE_OUTER_RAD = 0.50f;
    // The CURRENT source-fidelity floor-lamp GLB has a reference-solved anchor at
    // (+1.71406245, +0.72000003, +1.70734370) before the common 180-degree room yaw.
    // alignRoomRoot rotates it to X=-1.71406245, Z=-1.70734370 and then applies the
    // locked runtime offset. The old 6.4m room constants (-1.55,+1.55) left the light
    // 3.26 m from its actual shade. Bind this focused source-light to the real fitted
    // lamp position without mutating the original furniture GLB or accepted toggle.
    private static final float FLOOR_LAMP_LIGHT_X = -1.71406245f
            + CelineRoomWorldContractV80.RUNTIME_OFFSET_X;
    private static final float FLOOR_LAMP_LIGHT_Y = 1.45f
            + CelineRoomWorldContractV80.RUNTIME_OFFSET_Y;
    private static final float FLOOR_LAMP_LIGHT_Z = -1.70734370f
            + CelineRoomWorldContractV80.RUNTIME_OFFSET_Z;
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
                seatAnchor = new SeatAnchor(
                        0.0f, -0.72f, -4.12f,
                        0.0f, 1.0f, 0.0f,
                        0.0f, 0.0f, 1.0f,
                        1.15f, 0.95f, -1.55f,
                        0.0f, 0.05f, -4.53f,
                        roomShellAsset.getRoot());
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
                        "Filament SeatAnchor bereit", seatAnchor.diagnosticSummary());
                Celine3DDiagnostics.record(context, "ROOM-116",
                        "Recovery-Geometrie aktiv",
                        "sourceGlbsImmutable=true roomPartitions14=true authoredFurnitureInstances13=true extraReferenceGeometry=false furnitureOrientationOverride=false furnitureSharedRootYawDeg=0 shellNativeS25Frame=true sharedWorldContractPendingBuild=true frontWallHidden=true originalDrapeMaterial=true sharedCelineKeyMutated=false roomLightChannel=1 neutralKeyIntensity="
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

        /**
         * C135 exact Android CALL #340 vs canonical /Refernzbild.png:
         * The previous #339 clipped the derived foreground pot at the right edge;
         * #340 restores its complete silhouette, a genuine partial improvement,
         * but measured visible pot bbox remains x=749..840,y=696..803 on
         * 1016x813 CALL stage versus reference approximately x=870..977,
         * y=650..769. The derived source must move right and deeper on the
         * SAME foreground table, without raising its foot or replacing its GLB.
         * A coherent fitted x/z/scale correction uses world .18,-.80,-1.78
         * and 1.70x. Await one authentic Android raster A/B; no visual PASS
         * granted from predicted geometry or green compilation.
         * All 12 original LFS source GLBs, 13 authored furniture instances,
         * shell/window/backplanes, camera, Celine and Core remain untouched.
         */
        private void positionForegroundDerivedPlant(FilamentAsset asset) {
            final int rootEntity = asset.getRoot();
            final int root = transforms.getInstance(rootEntity);
            final int anchorEntity = asset.getFirstEntityByName("room_plant_small__anchor");
            if (anchorEntity == 0 || root == 0) {
                throw new IllegalStateException("Foreground source plant root/anchor missing");
            }
            final int anchor = transforms.getInstance(anchorEntity);
            if (anchor == 0) {
                throw new IllegalStateException("Foreground source plant transform missing");
            }
            float[] rootBase = transforms.getTransform(root, new float[16]);
            float[] originalAnchor = transforms.getTransform(anchor, new float[16]);
            float[] derivedScale = new float[16];
            float[] scaledAnchor = new float[16];
            float[] worldAnchor = new float[16];
            float[] worldOffset = new float[16];
            float[] fittedRoot = new float[16];
            Matrix.setIdentityM(derivedScale, 0);
            Matrix.scaleM(derivedScale, 0, 1.70f, 1.70f, 1.70f);
            Matrix.multiplyMM(scaledAnchor, 0, originalAnchor, 0, derivedScale, 0);
            Matrix.multiplyMM(worldAnchor, 0, rootBase, 0, scaledAnchor, 0);
            Matrix.setIdentityM(worldOffset, 0);
            Matrix.translateM(worldOffset, 0,
                    0.18f - worldAnchor[12],
                    -0.80f - worldAnchor[13],
                    -1.78f - worldAnchor[14]);
            Matrix.multiplyMM(fittedRoot, 0, worldOffset, 0, rootBase, 0);
            transforms.setTransform(anchor, scaledAnchor);
            transforms.setTransform(root, fittedRoot);
            Celine3DDiagnostics.record(context, "ROOM-150",
                    "Quelltreue Vordergrundpflanze als eigene Instanz",
                    "reference=Refernzbild.png #340 target=x.812..1.000,y.645..946"
                            + " worldAnchor=0.18,-0.80,-1.78 derivedScale=1.70"
                            + " canonicalSources12=true authoredInstances13Unchanged=true"
                            + " windowsGlassBackplanesUnchanged=true");
        }

        /**
         * C129: exact #324 Android CALL has the drape silhouette's left/top almost
         * aligned, but the right edge ends roughly 40-45 stage pixels before
         * Refernzbild.png. Stretch the already-derived partition horizontally
         * by 13.5%, then compensate by half the added source-local width so the
         * accepted projected left edge stays put. The common 180-degree frame
         * maps negative local X toward screen-right. No opacity, GLASS/opening,
         * backplane, source geometry, camera or other furniture gets changed.
         * Accept this correction ONLY after an authentic Android CALL raster A/B.
         */
        private void correctWindowDrapeVisibleWidth(FilamentAsset asset) {
            final int entity = asset.getFirstEntityByName("room_window_drapes__anchor");
            if (entity == 0) {
                throw new IllegalStateException("Room source-drape partition anchor missing");
            }
            final int instance = transforms.getInstance(entity);
            if (instance == 0) {
                throw new IllegalStateException("Room source-drape anchor transform missing");
            }
            float[] base = transforms.getTransform(instance, new float[16]);
            float[] correction = new float[16];
            float[] fitted = new float[16];
            Matrix.setIdentityM(correction, 0);
            Matrix.translateM(correction, 0, -0.094f, 0.0f, 0.0f);
            Matrix.scaleM(correction, 0, 1.135f, 1.0f, 1.0f);
            Matrix.multiplyMM(fitted, 0, base, 0, correction, 0);
            transforms.setTransform(instance, fitted);
            Celine3DDiagnostics.record(context, "ROOM-147",
                    "Quelltreue Fenster-Gardinenbreite kalibriert",
                    "source=Refernzbild.png #324 axis=localX factor=1.135"
                            + " sourceLocalShiftX=-0.094 preserveLeftTop=true"
                            + " windowsGlassBackplaneChanged=false originalsChanged=false");
        }

        /**
         * C148, measured on the real C147 Android HOME-CALL-HOME #367 CALL stage:
         * the existing sourced top-rail/drape envelope spans approximately
         * x=0.199..0.545 versus Refernzbild.png's x=0.205..0.588.
         * Its top is already near the canonical target, so preserve all Y/Z,
         * camera and room-shell geometry. Expand the real drape mesh by 10.5%
         * in local X and compensate its left edge by a small source-local shift.
         *
         * IMPORTANT: the night-depth node shares the partition anchor. Changing
         * that anchor again would also stretch the independent, authored opaque
         * background. Apply this second, bounded correction ONLY to the drape
         * mesh_node.010 child; leave the partition anchor's previously validated
         * transform and the night plane's transform/material strictly unchanged.
         * This is a runtime-derived transform, not a source-GLB edit, and no
         * GLASS/open-window/backplane semantic choice is fabricated.
         *
         * Projection estimate only: KEEP only if next genuine Android CALL A/B
         * visibly improves silhouette with no lost source fabric or black gaps.
         */
        private void widenVisibleSourceDrapeOnly(FilamentAsset asset) {
            final int drapeEntity = asset.getFirstEntityByName("mesh_node.010");
            final int nightEntity =
                    asset.getFirstEntityByName("CELINE_WindowNightDepth_Background");
            if (drapeEntity == 0 || nightEntity == 0 || drapeEntity == nightEntity) {
                throw new IllegalStateException("Source drape/night identities ambiguous");
            }
            final int drapeTransform = transforms.getInstance(drapeEntity);
            if (drapeTransform == 0) {
                throw new IllegalStateException("Source drape local transform missing");
            }
            float[] before = transforms.getTransform(drapeTransform, new float[16]);
            float[] correction = new float[16];
            float[] widened = new float[16];
            Matrix.setIdentityM(correction, 0);
            Matrix.translateM(correction, 0, -0.090f, 0.0f, 0.0f);
            Matrix.scaleM(correction, 0, 1.105f, 1.0f, 1.0f);
            Matrix.multiplyMM(widened, 0, before, 0, correction, 0);
            transforms.setTransform(drapeTransform, widened);
            Celine3DDiagnostics.record(context, "ROOM-158",
                    "Quellgardinen-Silhouette erweitert, Nachtfläche unverändert",
                    "reference=Refernzbild.png androidProof=367"
                            + " oldEnvelope=.199..545 target=.205..588"
                            + " drapeLocalXScale=1.105 drapeLocalXShift=-0.090"
                            + " nightDepthTransformMaterialChanged=false"
                            + " originalFurnitureGlbsChanged=false"
                            + " windowGlassOpeningsBackplanesChanged=false");
        }

        /**
         * C130 / genuine Android CALL #327 vs Refernzbild.png:
         * the existing derived dresser reads low and slightly too far to screen right
         * (visible top ~0.434 vs 0.420; foot ~0.729 vs 0.718; right ~0.190 vs 0.184).
         * Retain the already-proved +8 cm source X / +9 cm deeper parent shift.
         * C131 corrective A/B after exact #331 Android CALL: -8 degrees
         * increased the clipped dresser top-edge diagonal and shifted the
         * right envelope outside the reference. Source-vertex projection at
         * the exact CALL camera instead fits a +6-degree local yaw and an
         * 8 cm source-X re-centering while preserving the accepted +9 cm
         * depth, local/vertical scale, grounded floor and all original GLBs.
         * Predicted right edge is ~188/1016 vs the canonical 0.184*1016=187;
         * this geometric prediction is NOT visual acceptance.
         * Accept ONLY after a genuine Android HOME/CALL/HOME visual A/B.
         */
        private void correctDresserReferenceEnvelope(FilamentAsset asset) {
            final int entity = asset.getFirstEntityByName("room_dresser__anchor");
            if (entity == 0) {
                throw new IllegalStateException("Room source-dresser partition anchor missing");
            }
            final int instance = transforms.getInstance(entity);
            if (instance == 0) {
                throw new IllegalStateException("Room source-dresser anchor transform missing");
            }
            float[] base = transforms.getTransform(instance, new float[16]);
            float[] parentSpaceShift = new float[16];
            float[] localYaw = new float[16];
            float[] rotated = new float[16];
            float[] fitted = new float[16];
            Matrix.setIdentityM(parentSpaceShift, 0);
            // Common room-root 180-degree Y alignment maps +source X to
            // screen-left and +source Z toward the back of the actual room.
            Matrix.translateM(parentSpaceShift, 0, 0.0f, 0.0f, 0.090f);
            // Post-multiply: rotate around the dresser's existing anchor,
            // without changing the accepted parent shift or grounding.
            Matrix.setRotateM(localYaw, 0, 6.0f, 0.0f, 1.0f, 0.0f);
            Matrix.multiplyMM(rotated, 0, base, 0, localYaw, 0);
            Matrix.multiplyMM(fitted, 0, parentSpaceShift, 0, rotated, 0);
            transforms.setTransform(instance, fitted);
            Celine3DDiagnostics.record(context, "ROOM-148",
                    "Referenzgebundene Kommodentiefe beibehalten",
                    "source=Refernzbild.png #331 sourceX=+0.000m sourceZ=+0.090m"
                            + " parentSpace=true preserveFloorScale=true"
                            + " originalsChanged=false otherPartitionsChanged=false");
            Celine3DDiagnostics.record(context, "ROOM-149",
                    "Kommodenfront im CALL referenzgebunden ausgerichtet",
                    "source=Refernzbild.png #331 anchorLocalYaw=+6deg"
                            + " preserveDepthShiftScaleFloor=true"
                            + " originalGlbChanged=false cameraChanged=false");
        }

        private void applyUserApprovedFurnitureOrientation(FilamentAsset asset) {
            // The prepared 4R GLB baked the bed with its headboard toward the room center and left
            // the two bedside drawer fronts sideways. Correct only those confirmed orientation
            // defects at the furniture-node level; footprint, anchors, camera and Celine stay fixed.
            applyLocalYaw(asset, "room_bed", -180.0f);
            applyLocalYaw(asset, "room_nightstand_back", 90.0f);
            applyLocalYaw(asset, "room_nightstand_front", 90.0f);
        }

        private void applyLocalYaw(FilamentAsset asset, String entityName, float deltaDegrees) {
            int entity = asset.getFirstEntityByName(entityName);
            if (entity == 0) {
                throw new IllegalStateException("4R Raum-Entity fehlt: " + entityName);
            }
            int instance = transforms.getInstance(entity);
            if (instance == 0) {
                throw new IllegalStateException("4R Transform fehlt: " + entityName);
            }
            float[] base = transforms.getTransform(instance, new float[16]);
            float[] yaw = new float[16];
            float[] corrected = new float[16];
            Matrix.setRotateM(yaw, 0, deltaDegrees, 0.0f, 1.0f, 0.0f);
            // Post-multiply so the node keeps its world translation and rotates around its own
            // origin. All three affected source nodes use uniform scale.
            Matrix.multiplyMM(corrected, 0, base, 0, yaw, 0);
            transforms.setTransform(instance, corrected);
        }

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
