package de.yahya.ai;

import android.content.Context;
import android.opengl.Matrix;
import android.view.View;

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
    private static final String FLOOR_LAMP_LIGHT_ID = "floor_lamp_light";
    // Assembly contract places the physical lamp at room-local (-1.55, 1.55). The restrained
    // runtime light sits inside its shade, then applies the already locked room root offset.
    private static final float FLOOR_LAMP_LIGHT_X = -1.55f
            + CelineRoomWorldContractV80.RUNTIME_OFFSET_X;
    private static final float FLOOR_LAMP_LIGHT_Y = 1.45f
            + CelineRoomWorldContractV80.RUNTIME_OFFSET_Y;
    private static final float FLOOR_LAMP_LIGHT_Z = 1.55f
            + CelineRoomWorldContractV80.RUNTIME_OFFSET_Z;
    private static final float FLOOR_LAMP_LIGHT_LUMENS = 60000.0f;
    // Proof #18 confirmed the default renderable light channel is enabled, but the previous
    // steep down/back beam still lands mostly outside the fixed-camera witness. Keep the proven
    // 60,000 lm and lamp position unchanged; aim the warm spot through the visible lounge-chair area.
    private static final float FLOOR_LAMP_LIGHT_FALLOFF_M = 3.4f;
    private static final float FLOOR_LAMP_LIGHT_DIR_X = -0.07282363f;
    private static final float FLOOR_LAMP_LIGHT_DIR_Y = -0.36411816f;
    private static final float FLOOR_LAMP_LIGHT_DIR_Z = -0.92850131f;
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
        synchronized (STATES) {
            for (State state : STATES.values()) {
                if (state != null && state.isBuilt()) return state.toggleFloorLamp();
            }
        }
        return false;
    }

    private static final class State implements View.OnAttachStateChangeListener {
        final Context context;
        final Celine3DView view;
        final Engine engine;
        final Scene scene;
        final TransformManager transforms;
        final AssetLoader assetLoader;
        final ResourceLoader resourceLoader;

        final ArrayList<FilamentAsset> roomAssets = new ArrayList<>();
        FilamentAsset roomShellAsset;
        SeatAnchor seatAnchor;
        CelineRoomWorldContractV80 worldContract;
        int floorLampLightEntity;
        boolean floorLampLightEnabled;
        boolean listenerInstalled;
        boolean failureLogged;

        State(Context context, Celine3DView view) throws Exception {
            this.context = context;
            this.view = view;
            engine = (Engine) field(view, "engine");
            scene = (Scene) field(view, "scene");
            transforms = engine.getTransformManager();
            assetLoader = (AssetLoader) field(view, "assetLoader");
            resourceLoader = (ResourceLoader) field(view, "resourceLoader");
            installListener();
        }

        synchronized boolean isBuilt() {
            return roomShellAsset != null && !roomAssets.isEmpty();
        }

        synchronized boolean ensureBuilt() {
            if (isBuilt()) return true;
            ArrayList<FilamentAsset> candidates = new ArrayList<>();
            try {
                CelineRoomWorldContractV80 contract = CelineRoomWorldContractV80.load(context);
                FilamentAsset shell = null;
                int renderables = 0;
                for (String roomPath : ROOM_PATHS) {
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
                        alignRoomRoot(part);
                        candidates.add(part);
                        renderables += partRenderables;
                        if (ROOM_SHELL_PATH.equals(roomPath)) shell = part;
                    } catch (Throwable error) {
                        try { assetLoader.destroyAsset(part); } catch (Throwable ignored) {}
                        throw error;
                    }
                }
                if (shell == null) throw new IllegalStateException("Room shell partition missing");
                validateWorldEntities(candidates, contract);

                for (FilamentAsset part : candidates) scene.addEntities(part.getEntities());
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
                createFloorLampLight();

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
                        "combined098M=false partitions14=true sourceGlbsImmutable=true runtimeOrientationOverride=false");
                Celine3DDiagnostics.record(context, "ROOM-120",
                        "9R.5 Lampenlicht bereit",
                        "entity=" + FLOOR_LAMP_LIGHT_ID
                                + " type=FOCUSED_SPOT enabled=false lumens="
                                + FLOOR_LAMP_LIGHT_LUMENS + " falloff="
                                + FLOOR_LAMP_LIGHT_FALLOFF_M + "m direction="
                                + FLOOR_LAMP_LIGHT_DIR_X + "," + FLOOR_LAMP_LIGHT_DIR_Y + ","
                                + FLOOR_LAMP_LIGHT_DIR_Z + " coneRad="
                                + FLOOR_LAMP_SPOT_INNER_RAD + "/" + FLOOR_LAMP_SPOT_OUTER_RAD
                                + " materialEmission=false");
                failureLogged = false;
                return true;
            } catch (Throwable error) {
                for (FilamentAsset candidate : candidates) {
                    try { assetLoader.destroyAsset(candidate); } catch (Throwable ignored) {}
                }
                if (!roomAssets.isEmpty() || floorLampLightEntity != 0) {
                    try { destroyRoom(); } catch (Throwable ignored) {}
                } else {
                    roomAssets.clear();
                    roomShellAsset = null;
                    seatAnchor = null;
                    worldContract = null;
                }
                if (!failureLogged) {
                    failureLogged = true;
                    Celine3DDiagnostics.error(context, "ROOM-199",
                            "Filament-Raum FEHLER - Canvas-Fallback bleibt aktiv", error);
                }
                return false;
            }
        }

        private int countRenderables(FilamentAsset asset) {
            RenderableManager manager = engine.getRenderableManager();
            int count = 0;
            for (int entity : asset.getEntities()) {
                if (manager.hasComponent(entity)) count++;
            }
            return count;
        }

        private void alignRoomRoot(FilamentAsset asset) {
            int root = transforms.getInstance(asset.getRoot());
            if (root == 0) {
                throw new IllegalStateException("4R Raum-Root-Transform fehlt");
            }
            float[] base = transforms.getTransform(root, new float[16]);
            float[] translation = new float[16];
            float[] aligned = new float[16];
            Matrix.setIdentityM(translation, 0);
            Matrix.translateM(translation, 0,
                    CelineRoomWorldContractV80.RUNTIME_OFFSET_X,
                    CelineRoomWorldContractV80.RUNTIME_OFFSET_Y,
                    CelineRoomWorldContractV80.RUNTIME_OFFSET_Z);
            Matrix.multiplyMM(aligned, 0, translation, 0, base, 0);
            transforms.setTransform(root, aligned);
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
                        .lightChannel(0, true)
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
            lights.setLightChannel(instance, 0, next);
            floorLampLightEnabled = next;
            Celine3DDiagnostics.record(context, "V80-484",
                    "9R.5 Lampenstatus gewechselt",
                    "enabled=" + next + " lightEntity=" + FLOOR_LAMP_LIGHT_ID
                            + " type=FOCUSED_SPOT lumens=" + FLOOR_LAMP_LIGHT_LUMENS
                            + " handContact=false switchTarget=false cameraFixed=true");
            return true;
        }

        private void installListener() {
            if (listenerInstalled) return;
            listenerInstalled = true;
            view.addOnAttachStateChangeListener(this);
        }

        @Override public void onViewAttachedToWindow(View v) {
            // CelineRoomBackdropView owns the safe rebuild request and fallback invalidation.
        }

        @Override public void onViewDetachedFromWindow(View v) {
            destroyRoom();
        }

        synchronized void destroyRoom() {
            ArrayList<FilamentAsset> current = new ArrayList<>(roomAssets);
            int lampLight = floorLampLightEntity;
            roomAssets.clear();
            roomShellAsset = null;
            seatAnchor = null;
            worldContract = null;
            floorLampLightEntity = 0;
            floorLampLightEnabled = false;
            if (current.isEmpty() && lampLight == 0) return;
            try {
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