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
 * Derived foliage-only augmentation for the fused immutable room_plant_large mesh.
 *
 * Proof #1274 proved an aggressive source X/SX solve loses the pot/trunk. Proof #1295 proved a
 * distinct source X/yaw solve preserves pot/trunk but narrows the foliage and regresses the plant
 * score. The source mesh is one fused node/mesh/primitive, so foliage cannot be transformed
 * independently inside that GLB. This owner therefore leaves the accepted #1285 source plant
 * transform untouched and adds only a thin, camera-facing irregular leaf cluster behind its left
 * foliage edge. The cluster was inverse-projected from the exact CALL camera at camera depth 4.05 m:
 * screen x approximately 0.131..0.168 / y 0.225..0.472. Combined with the accepted source raster
 * x=0.15945..0.25394 / y=0.20541..0.53506, the predicted overall envelope becomes approximately
 * x=0.131..0.25394 while keeping the accepted pot/trunk and vertical bounds.
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
            {-0.026221f,  0.575827f},
            {-0.072835f,  0.459263f},
            {-0.101969f,  0.272760f},
            {-0.061181f,  0.132883f},
            {-0.107795f, -0.030307f},
            {-0.072835f, -0.193497f},
            {-0.090315f, -0.356687f},
            {-0.026221f, -0.463926f},
            { 0.008740f, -0.575827f},
            { 0.049528f, -0.426625f},
            { 0.084488f, -0.240122f},
            { 0.055354f, -0.053620f},
            { 0.107795f,  0.132883f},
            { 0.067008f,  0.296073f},
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
            material = MaterialInstance.duplicate(donor, "v80-room-large-plant-foliage-augment");
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
            xy[0] = 0f;
            xy[1] = 0f;
            for (int i = 0; i < RING.length; i++) {
                xy[2 + i * 2] = RING[i][0];
                xy[3 + i * 2] = RING[i][1];
            }
            short[] faces = new short[RING.length * 3];
            for (int i = 0; i < RING.length; i++) {
                int next = (i + 1) % RING.length;
                faces[i * 3] = 0;
                faces[i * 3 + 1] = (short) (1 + next);
                faces[i * 3 + 2] = (short) (1 + i);
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
                    "authority=Proof#1285 target=Refernzbild.png"
                            + " inverseProjectedCALL=x0.131..0.168/y0.225..0.472 depth=4.05m"
                            + " acceptedSourcePlantTRSUnchanged=true potTrunkUnchanged=true"
                            + " sourceGLBMutated=false roomShell/camera/Celine/anchors unchanged=true");
        } catch (Throwable error) {
            if (added && entity != 0) try { scene.removeEntity(entity); } catch (Throwable ignored) {}
            if (entity != 0) {
                try { engine.destroyEntity(entity); } catch (Throwable ignored) {}
                try { EntityManager.get().destroy(entity); } catch (Throwable ignored) {}
            }
            if (indices != null) try { engine.destroyIndexBuffer(indices); } catch (Throwable ignored) {}
            if (vertices != null) try { engine.destroyVertexBuffer(vertices); } catch (Throwable ignored) {}
            if (material != null) try { engine.destroyMaterialInstance(material); } catch (Throwable ignored) {}
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
        VertexBuffer vertices = new VertexBuffer.Builder()
                .vertexCount(count)
                .bufferCount(1)
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
        vertices.setBufferAt(engine, 0, data);
        return vertices;
    }

    private static IndexBuffer createIndices(Engine engine, short[] values) {
        ShortBuffer data = ByteBuffer.allocateDirect(values.length * 2)
                .order(ByteOrder.nativeOrder()).asShortBuffer();
        data.put(values).flip();
        IndexBuffer indices = new IndexBuffer.Builder()
                .indexCount(values.length)
                .bufferType(IndexBuffer.Builder.IndexType.USHORT)
                .build(engine);
        indices.setBuffer(engine, data);
        return indices;
    }

    private static FilamentAsset currentRoomAsset(Celine3DView view) throws Exception {
        Field statesField = CelineRoomEnvironmentV80.class.getDeclaredField("STATES");
        statesField.setAccessible(true);
        Object rawStates = statesField.get(null);
        if (!(rawStates instanceof Map)) return null;
        Map<?, ?> states = (Map<?, ?>) rawStates;
        Object state;
        synchronized (states) { state = states.get(view); }
        if (state == null) return null;
        Field assetField = state.getClass().getDeclaredField("roomAsset");
        assetField.setAccessible(true);
        return (FilamentAsset) assetField.get(state);
    }

    private static MaterialInstance firstMaterial(
            FilamentAsset asset, Engine engine, String entityName) {
        int entity = asset.getFirstEntityByName(entityName);
        if (entity == 0) throw new IllegalStateException("large-plant augment donor missing " + entityName);
        RenderableManager renderables = engine.getRenderableManager();
        int renderable = renderables.getInstance(entity);
        if (renderable == 0 || renderables.getPrimitiveCount(renderable) < 1) {
            throw new IllegalStateException("large-plant augment donor renderable missing " + entityName);
        }
        MaterialInstance material = renderables.getMaterialInstanceAt(renderable, 0);
        if (material == null) throw new IllegalStateException("large-plant augment donor material missing");
        return material;
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
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
                try { engine.destroyEntity(current); } catch (Throwable ignored) {}
                try { EntityManager.get().destroy(current); } catch (Throwable ignored) {}
            }
            if (indices != null) {
                try { engine.destroyIndexBuffer(indices); } catch (Throwable ignored) {}
                indices = null;
            }
            if (vertices != null) {
                try { engine.destroyVertexBuffer(vertices); } catch (Throwable ignored) {}
                vertices = null;
            }
            if (material != null) {
                try { engine.destroyMaterialInstance(material); } catch (Throwable ignored) {}
                material = null;
            }
        }
    }
}
