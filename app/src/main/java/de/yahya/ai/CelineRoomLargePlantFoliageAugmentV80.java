package de.yahya.ai;

import android.opengl.Matrix;
import android.view.View;

import com.google.android.filament.Box;
import com.google.android.filament.Colors;
import com.google.android.filament.Engine;
import com.google.android.filament.EntityManager;
import com.google.android.filament.IndexBuffer;
import com.google.android.filament.MaterialInstance;
import com.google.android.filament.RenderableManager;
import com.google.android.filament.Scene;
import com.google.android.filament.TransformManager;
import com.google.android.filament.VertexBuffer;
import com.google.android.filament.gltfio.FilamentAsset;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Corrected second and final derived-foliage attempt after Proof #1307.
 *
 * #1307 proved that this owner executed but contributed no left-envelope pixels. Exact CALL-camera
 * math plus the room GLB material contract identified one concrete implementation defect: the
 * generated fan used center,next,current, so its transformed front normal matched the camera forward
 * vector and pointed away from the camera while the duplicated RoomWarmOffWhite donor is single-sided.
 * This version changes only the fan winding to center,current,next. Geometry, center, depth, material,
 * accepted source-plant TRS, pot/trunk, source GLB bytes, room shell, camera/FOV, Celine and anchors
 * remain unchanged. If the fresh proof still misses the scoreboard gate, this foliage family is
 * STOP-LOSS.
 */
final class CelineRoomLargePlantFoliageAugmentV80 {
    private static final float CENTER_X = -1.995771f;
    private static final float CENTER_Y = 0.955592f;
    private static final float CENTER_Z = -1.414942f;
    private static final float YAW_DEG = -5.820900f;
    private static final float PITCH_DEG = -13.629234f;
    private static final float BOUNDS_WIDTH = 0.215591f;
    private static final float BOUNDS_HEIGHT = 1.151655f;

    private static final float[][] RING = {
            {-0.026221f,  0.575827f}, {-0.072835f,  0.459263f},
            {-0.101969f,  0.272760f}, {-0.061181f,  0.132883f},
            {-0.107795f, -0.030307f}, {-0.072835f, -0.193497f},
            {-0.090315f, -0.356687f}, {-0.026221f, -0.463926f},
            { 0.008740f, -0.575827f}, { 0.049528f, -0.426625f},
            { 0.084488f, -0.240122f}, { 0.055354f, -0.053620f},
            { 0.107795f,  0.132883f}, { 0.067008f,  0.296073f},
            { 0.096142f,  0.482576f}
    };

    private static final WeakHashMap<Celine3DView, State> STATES = new WeakHashMap<>();

    private CelineRoomLargePlantFoliageAugmentV80() {}

    static void apply(Celine3DView view, Engine engine) throws Exception {
        if (view == null || engine == null) return;
        synchronized (STATES) {
            if (STATES.containsKey(view)) return;
        }

        FilamentAsset asset = currentRoomAsset(view);
        if (asset == null) throw new IllegalStateException("large-plant augment: room asset fehlt");
        Scene scene = (Scene) field(view, "scene");
        if (scene == null) throw new IllegalStateException("large-plant augment: scene fehlt");
        TransformManager transforms = engine.getTransformManager();
        int rootTransform = transforms.getInstance(asset.getRoot());
        if (rootTransform == 0) throw new IllegalStateException("large-plant augment: room root fehlt");

        MaterialInstance donor = firstMaterial(asset, engine, "room_back_wall");
        MaterialInstance material = null;
        VertexBuffer vertices = null;
        IndexBuffer indices = null;
        int entity = 0;
        boolean added = false;
        try {
            material = MaterialInstance.duplicate(donor, "v80-room-large-plant-foliage-augment-frontface");
            try {
                if (material.getMaterial().hasParameter("baseColorFactor")) {
                    material.setParameter("baseColorFactor", Colors.RgbaType.LINEAR,
                            0.090f, 0.145f, 0.055f, 1.0f);
                }
                if (material.getMaterial().hasParameter("metallicFactor")) {
                    material.setParameter("metallicFactor", 0.0f);
                }
                if (material.getMaterial().hasParameter("roughnessFactor")) {
                    material.setParameter("roughnessFactor", 0.86f);
                }
            } catch (Throwable ignored) {}

            float[] xy = new float[2 + RING.length * 2];
            for (int i = 0; i < RING.length; i++) {
                xy[2 + i * 2] = RING[i][0];
                xy[3 + i * 2] = RING[i][1];
            }
            short[] faces = new short[RING.length * 3];
            for (int i = 0; i < RING.length; i++) {
                int next = (i + 1) % RING.length;
                faces[i * 3] = 0;
                // Corrected after #1307: front normal must point from polygon toward CALL camera.
                faces[i * 3 + 1] = (short) (1 + i);
                faces[i * 3 + 2] = (short) (1 + next);
            }

            vertices = createVertices(engine, xy);
            indices = createIndices(engine, faces);
            engine.flushAndWait();

            entity = EntityManager.get().create();
            new RenderableManager.Builder(1)
                    .boundingBox(new Box(0f, 0f, 0f,
                            BOUNDS_WIDTH * 0.55f, BOUNDS_HEIGHT * 0.55f, 0.03f))
                    .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertices, indices)
                    .material(0, material)
                    .castShadows(false)
                    .receiveShadows(true)
                    .culling(false)
                    .build(engine, entity);

            float[] local = new float[16];
            Matrix.setIdentityM(local, 0);
            Matrix.translateM(local, 0, CENTER_X, CENTER_Y, CENTER_Z);
            Matrix.rotateM(local, 0, YAW_DEG, 0f, 1f, 0f);
            Matrix.rotateM(local, 0, PITCH_DEG, 1f, 0f, 0f);
            transforms.create(entity, rootTransform, local);
            scene.addEntity(entity);
            added = true;

            State state = new State(view, engine, scene, entity, material, vertices, indices);
            synchronized (STATES) { STATES.put(view, state); }
            view.addOnAttachStateChangeListener(state);
            Celine3DDiagnostics.record(view.getContext(), "ROOM-156",
                    "Große Pflanze nur am Laub rasterbegrenzt ergänzt",
                    "authority=Proof#1285 rootCause=Proof#1307_backface"
                            + " winding=center-current-next frontFaceTowardCall=true"
                            + " inverseProjectedCALL=x0.131..0.168/y0.225..0.472 depth=4.05m"
                            + " acceptedSourcePlantTRSUnchanged=true potTrunkUnchanged=true"
                            + " sourceGLBMutated=false roomShell/camera/Celine/anchors unchanged=true");
        } catch (Throwable error) {
            if (added && entity != 0) try { scene.removeEntity(entity); } catch (Throwable ignored) {}
            destroy(engine, entity, material, vertices, indices);
            throw error;
        }
    }

    static void release(Celine3DView view) {
        State state;
        synchronized (STATES) { state = STATES.remove(view); }
        if (state != null) state.destroy(false);
    }

    private static VertexBuffer createVertices(Engine engine, float[] xy) {
        int count = xy.length / 2;
        final int stride = 15 * 4;
        FloatBuffer data = ByteBuffer.allocateDirect(count * stride)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        for (int i = 0; i < count; i++) {
            float x = xy[i * 2];
            float y = xy[i * 2 + 1];
            data.put(x).put(y).put(0f);
            data.put(0f).put(0f).put(0f).put(1f);
            data.put(1f).put(1f).put(1f).put(1f);
            float u = 0.5f + x / Math.max(0.001f, BOUNDS_WIDTH);
            float v = 0.5f + y / Math.max(0.001f, BOUNDS_HEIGHT);
            data.put(u).put(v).put(u).put(v);
        }
        data.flip();
        VertexBuffer buffer = new VertexBuffer.Builder()
                .vertexCount(count).bufferCount(1)
                .attribute(VertexBuffer.VertexAttribute.POSITION, 0,
                        VertexBuffer.AttributeType.FLOAT3, 0, stride)
                .attribute(VertexBuffer.VertexAttribute.TANGENTS, 0,
                        VertexBuffer.AttributeType.FLOAT4, 3 * 4, stride)
                .attribute(VertexBuffer.VertexAttribute.COLOR, 0,
                        VertexBuffer.AttributeType.FLOAT4, 7 * 4, stride)
                .attribute(VertexBuffer.VertexAttribute.UV0, 0,
                        VertexBuffer.AttributeType.FLOAT2, 11 * 4, stride)
                .attribute(VertexBuffer.VertexAttribute.UV1, 0,
                        VertexBuffer.AttributeType.FLOAT2, 13 * 4, stride)
                .build(engine);
        buffer.setBufferAt(engine, 0, data);
        return buffer;
    }

    private static IndexBuffer createIndices(Engine engine, short[] values) {
        ShortBuffer data = ByteBuffer.allocateDirect(values.length * 2)
                .order(ByteOrder.nativeOrder()).asShortBuffer();
        data.put(values).flip();
        IndexBuffer buffer = new IndexBuffer.Builder()
                .indexCount(values.length)
                .bufferType(IndexBuffer.Builder.IndexType.USHORT)
                .build(engine);
        buffer.setBuffer(engine, data);
        return buffer;
    }

    private static FilamentAsset currentRoomAsset(Celine3DView view) throws Exception {
        Field statesField = CelineRoomEnvironmentV80.class.getDeclaredField("STATES");
        statesField.setAccessible(true);
        Object raw = statesField.get(null);
        if (!(raw instanceof Map)) return null;
        Object state;
        synchronized (raw) { state = ((Map<?, ?>) raw).get(view); }
        if (state == null) return null;
        Field assetField = state.getClass().getDeclaredField("roomAsset");
        assetField.setAccessible(true);
        return (FilamentAsset) assetField.get(state);
    }

    private static MaterialInstance firstMaterial(FilamentAsset asset, Engine engine, String name) {
        int entity = asset.getFirstEntityByName(name);
        if (entity == 0) throw new IllegalStateException("large-plant augment donor missing " + name);
        RenderableManager manager = engine.getRenderableManager();
        int renderable = manager.getInstance(entity);
        if (renderable == 0 || manager.getPrimitiveCount(renderable) < 1) {
            throw new IllegalStateException("large-plant augment donor renderable missing " + name);
        }
        MaterialInstance material = manager.getMaterialInstanceAt(renderable, 0);
        if (material == null) throw new IllegalStateException("large-plant augment donor material missing");
        return material;
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void destroy(Engine engine, int entity, MaterialInstance material,
                                VertexBuffer vertices, IndexBuffer indices) {
        if (entity != 0) {
            try { engine.destroyEntity(entity); } catch (Throwable ignored) {}
            try { EntityManager.get().destroy(entity); } catch (Throwable ignored) {}
        }
        if (indices != null) try { engine.destroyIndexBuffer(indices); } catch (Throwable ignored) {}
        if (vertices != null) try { engine.destroyVertexBuffer(vertices); } catch (Throwable ignored) {}
        if (material != null) try { engine.destroyMaterialInstance(material); } catch (Throwable ignored) {}
    }

    private static final class State implements View.OnAttachStateChangeListener {
        final Celine3DView view;
        final Engine engine;
        final Scene scene;
        int entity;
        MaterialInstance material;
        VertexBuffer vertices;
        IndexBuffer indices;
        boolean destroyed;

        State(Celine3DView view, Engine engine, Scene scene, int entity,
              MaterialInstance material, VertexBuffer vertices, IndexBuffer indices) {
            this.view = view;
            this.engine = engine;
            this.scene = scene;
            this.entity = entity;
            this.material = material;
            this.vertices = vertices;
            this.indices = indices;
        }

        @Override public void onViewAttachedToWindow(View v) {}
        @Override public void onViewDetachedFromWindow(View v) {
            synchronized (STATES) { STATES.remove(view); }
            destroy(true);
        }

        void destroy(boolean removeListener) {
            if (destroyed) return;
            destroyed = true;
            if (removeListener) view.removeOnAttachStateChangeListener(this);
            int current = entity;
            entity = 0;
            if (current != 0) {
                try { scene.removeEntity(current); } catch (Throwable ignored) {}
            }
            CelineRoomLargePlantFoliageAugmentV80.destroy(
                    engine, current, material, vertices, indices);
            material = null;
            vertices = null;
            indices = null;
        }
    }
}
