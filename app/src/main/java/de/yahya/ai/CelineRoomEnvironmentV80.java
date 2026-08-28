package de.yahya.ai;

import android.content.Context;
import android.opengl.Matrix;
import android.view.View;

import com.google.android.filament.Engine;
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
import java.util.WeakHashMap;

/**
 * v80 Block-3 Filament room environment.
 *
 * Owns only room geometry/material resources and world anchors. It never writes Celine's root,
 * bones, camera, speech, lip-sync or animation state. The legacy Canvas room remains available as
 * a fail-closed runtime fallback when this environment cannot be built.
 */
final class CelineRoomEnvironmentV80 {
    private static final String ROOM_PATH =
            "models/room/celine_room_v80_final_modular.glb";
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

    private static final class State implements View.OnAttachStateChangeListener {
        final Context context;
        final Celine3DView view;
        final Engine engine;
        final Scene scene;
        final TransformManager transforms;
        final AssetLoader assetLoader;
        final ResourceLoader resourceLoader;

        FilamentAsset roomAsset;
        SeatAnchor seatAnchor;
        CelineRoomWorldContractV80 worldContract;
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
            return roomAsset != null;
        }

        synchronized boolean ensureBuilt() {
            if (roomAsset != null) return true;
            FilamentAsset candidate = null;
            try {
                CelineRoomWorldContractV80 contract =
                        CelineRoomWorldContractV80.load(context);
                ByteBuffer source = readAsset(context, ROOM_PATH);
                candidate = assetLoader.createAsset(source);
                if (candidate == null) {
                    throw new IllegalStateException("gltfio konnte " + ROOM_PATH + " nicht laden");
                }
                resourceLoader.loadResources(candidate);
                candidate.releaseSourceData();

                int renderables = countRenderables(candidate);
                if (renderables <= 0) {
                    throw new IllegalStateException("Filament-Raum enthält keine Renderables");
                }

                alignRoomRoot(candidate);
                validateWorldEntities(candidate, contract);

                scene.addEntities(candidate.getEntities());
                roomAsset = candidate;
                candidate = null;
                worldContract = contract;
                seatAnchor = new SeatAnchor(
                        0.0f, -0.72f, -4.12f,
                        0.0f, 1.0f, 0.0f,
                        0.0f, 0.0f, 1.0f,
                        1.15f, 0.95f, -1.55f,
                        0.0f, 0.05f, -4.53f,
                        roomAsset.getRoot());

                Celine3DDiagnostics.record(context, "ROOM-100",
                        "Finaler modularer Filament-Raum aktiv",
                        "renderables=" + renderables + " path=" + ROOM_PATH
                                + " sha256=" + contract.roomSha256);
                Celine3DDiagnostics.record(context, "ROOM-105",
                        "4R Weltvertrag aktiv", contract.diagnosticSummary());
                Celine3DDiagnostics.record(context, "ROOM-110",
                        "Filament SeatAnchor bereit", seatAnchor.diagnosticSummary());
                failureLogged = false;
                return true;
            } catch (Throwable error) {
                if (candidate != null) {
                    try { assetLoader.destroyAsset(candidate); } catch (Throwable ignored) {}
                }
                roomAsset = null;
                seatAnchor = null;
                worldContract = null;
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

        private void validateWorldEntities(
                FilamentAsset asset, CelineRoomWorldContractV80 contract) {
            requireEntity(asset, "room_world_root");
            requireEntity(asset, "room_floor");
            requireEntity(asset, "room_bed");
            requireEntity(asset, "room_lounge_chair");
            requireEntity(asset, "room_foreground_table");
            requireEntity(asset, "room_nightstand_back");
            requireEntity(asset, "room_nightstand_front");
            for (String anchorId : contract.anchors.keySet()) {
                requireEntity(asset, anchorId);
            }
        }

        private void requireEntity(FilamentAsset asset, String name) {
            if (asset.getFirstEntityByName(name) == 0) {
                throw new IllegalStateException("4R Raum-Entity fehlt: " + name);
            }
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
            FilamentAsset current = roomAsset;
            roomAsset = null;
            seatAnchor = null;
            worldContract = null;
            if (current == null) return;
            try {
                for (int entity : current.getEntities()) {
                    try { scene.removeEntity(entity); } catch (Throwable ignored) {}
                }
                assetLoader.destroyAsset(current);
                Celine3DDiagnostics.record(context, "ROOM-130",
                        "Filament-Raum freigegeben", "detach lifecycle cleanup");
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
