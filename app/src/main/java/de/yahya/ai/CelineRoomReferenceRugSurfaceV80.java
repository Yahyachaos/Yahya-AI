package de.yahya.ai;

import android.opengl.Matrix;
import android.view.View;

import com.google.android.filament.Box;
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
 * Derived smooth top surface for the immutable source rug.
 *
 * Real Candidate #1259 and #1262 are raster-equivalent in the canonical rug witness after
 * independently neutralizing normalScale and aoStrength. Their rug ROI differs by only about
 * 0.0004 RGB levels on average, while the strong horizontal gaps remain plainly visible.
 * The current optimized room GLB confirms that room_rug is a high-relief source mesh; therefore
 * another material-map tweak cannot fill the actual geometric gaps/facets.
 *
 * Keep Teppisch.glb and the accepted room_rug TRS untouched. This owner adds one thin, smooth,
 * horizontal derived surface just above the source rug's measured top and uses the already-isolated
 * rug material. The surface follows the accepted source envelope and yaw, so this bounded candidate
 * changes only visible pile relief, not room layout, camera, Celine, anchors or source bytes.
 */
final class CelineRoomReferenceRugSurfaceV80 {
    // Current accepted room_rug TRS from CelineRoomReferenceLayoutV80.
    private static final float CENTER_X = -0.052922f;
    private static final float CENTER_Y = 0.0442f;
    private static final float CENTER_Z = 0.150383f;
    private static final float WIDTH_M = 2.732899f;
    private static final float DEPTH_M = 2.412007f;
    private static final float YAW_DEG = 5.820313f;

    private static final WeakHashMap<Celine3DView, State> STATES = new WeakHashMap<>();

    private CelineRoomReferenceRugSurfaceV80() {}

    static void apply(Celine3DView view, Engine engine) throws Exception {
        if (view == null || engine == null) return;
        synchronized (STATES) {
            if (STATES.containsKey(view)) return;
        }

        FilamentAsset asset = currentRoomAsset(view);
        if (asset == null) throw new IllegalStateException("reference rug surface: room asset fehlt");
        Scene scene = (Scene) field(view, "scene");
        if (scene == null) throw new IllegalStateException("reference rug surface: scene fehlt");
        TransformManager transforms = engine.getTransformManager();
        int rootTransform = transforms.getInstance(asset.getRoot());
        if (rootTransform == 0) throw new IllegalStateException("reference rug surface: room root fehlt");

        MaterialInstance donor = rugMaterial(asset, engine);
        MaterialInstance material = null;
        VertexBuffer vertices = null;
        IndexBuffer indices = null;
        int entity = 0;
        boolean added = false;
        try {
            material = MaterialInstance.duplicate(donor, "v80-reference-rug-smooth-surface");
            if (material.getMaterial().hasParameter("normalScale")) {
                material.setParameter("normalScale", 0.0f);
            }
            if (material.getMaterial().hasParameter("aoStrength")) {
                material.setParameter("aoStrength", 0.0f);
            }
            if (material.getMaterial().hasParameter("metallicFactor")) {
                material.setParameter("metallicFactor", 0.0f);
            }
            if (material.getMaterial().hasParameter("roughnessFactor")) {
                material.setParameter("roughnessFactor", 0.96f);
            }
            if (material.getMaterial().hasParameter("reflectance")) {
                material.setParameter("reflectance", 0.28f);
            }

            float halfW = WIDTH_M * 0.5f;
            float halfD = DEPTH_M * 0.5f;
            float[] xy = {
                    -halfW, halfD,
                     halfW, halfD,
                     halfW, -halfD,
                    -halfW, -halfD
            };
            short[] face = {0, 3, 2, 0, 2, 1};
            vertices = createVertices(engine, xy);
            indices = createIndices(engine, face);
            engine.flushAndWait();

            entity = EntityManager.get().create();
            new RenderableManager.Builder(1)
                    .boundingBox(new Box(0f, 0f, 0f,
                            halfW + 0.01f, halfD + 0.01f, 0.02f))
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
            Matrix.rotateM(local, 0, 90.0f, 1f, 0f, 0f);
            transforms.create(entity, rootTransform, local);
            scene.addEntity(entity);
            added = true;

            State state = new State(view, engine, scene, entity, material, vertices, indices);
            synchronized (STATES) { STATES.put(view, state); }
            view.addOnAttachStateChangeListener(state);

            Celine3DDiagnostics.record(view.getContext(), "ROOM-153",
                    "Referenz-Teppich glatte Ableitungsoberfläche aktiv",
                    "source=Teppisch.glb immutable=true acceptedTRS=true"
                            + " center=" + CENTER_X + "," + CENTER_Y + "," + CENTER_Z
                            + " size=" + WIDTH_M + "x" + DEPTH_M
                            + " yaw=" + YAW_DEG
                            + " sourceReliefOccluded=true material=isolatedRug"
                            + " room/camera/Celine/anchors unchanged");
        } catch (Throwable error) {
            if (added && entity != 0) {
                try { scene.removeEntity(entity); } catch (Throwable ignored) {}
            }
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
            float u = 0.5f + x / WIDTH_M;
            float v = 0.5f + y / DEPTH_M;
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

    private static MaterialInstance rugMaterial(FilamentAsset asset, Engine engine) {
        int entity = asset.getFirstEntityByName("room_rug");
        if (entity == 0) throw new IllegalStateException("reference rug surface: room_rug fehlt");
        RenderableManager manager = engine.getRenderableManager();
        int renderable = manager.getInstance(entity);
        if (renderable == 0 || manager.getPrimitiveCount(renderable) < 1) {
            throw new IllegalStateException("reference rug surface: rug renderable fehlt");
        }
        MaterialInstance material = manager.getMaterialInstanceAt(renderable, 0);
        if (material == null) throw new IllegalStateException("reference rug surface: rug material fehlt");
        return material;
    }

    private static FilamentAsset currentRoomAsset(Celine3DView view) throws Exception {
        Field statesField = CelineRoomEnvironmentV80.class.getDeclaredField("STATES");
        statesField.setAccessible(true);
        Object rawStates = statesField.get(null);
        if (!(rawStates instanceof Map)) return null;
        Object state;
        Map<?, ?> states = (Map<?, ?>) rawStates;
        synchronized (states) { state = states.get(view); }
        if (state == null) return null;
        Field assetField = state.getClass().getDeclaredField("roomAsset");
        assetField.setAccessible(true);
        return (FilamentAsset) assetField.get(state);
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
