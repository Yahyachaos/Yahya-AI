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
 * Measurement-owned derived foreground geometry for the reference-visible laptop lid.
 *
 * Real Candidate #1183 confirms the immutable room_foreground_table source contains the table but
 * not the dominant dark laptop lid visible across the lower foreground of Refernzbild.png. The 12
 * original furniture GLBs therefore remain immutable source-of-origin files; this missing reference
 * object is represented as one app-owned plane parented to the accepted room root.
 *
 * On the exact 1016x813 CALL stage the measured reference lid is approximately
 * TL=(164.5,689.3), TR=(840.6,696.8), BR=(813.1,812.3), BL=(192.0,812.3). Solving one physical
 * rectangle against the accepted Filament camera while keeping its lower edge on the foreground
 * table yields the room-local transform below. No camera, Celine, anchor, room shell or source-GLB
 * transform is changed by this owner.
 */
final class CelineRoomForegroundLaptopV80 {
    private static final float CENTER_X = -0.38287254f;
    private static final float CENTER_Y = 0.77675030f;
    private static final float CENTER_Z = 3.02011395f;
    private static final float WIDTH = 0.77374204f;
    private static final float HEIGHT = 0.19152808f;
    private static final float YAW_DEG = -6.253965f;
    private static final float PITCH_DEG = 8.344122f;

    private static final WeakHashMap<Celine3DView, State> STATES = new WeakHashMap<>();

    private CelineRoomForegroundLaptopV80() {}

    static void apply(Celine3DView view, Engine engine) throws Exception {
        if (view == null || engine == null) return;
        synchronized (STATES) {
            if (STATES.containsKey(view)) return;
        }

        FilamentAsset roomAsset = currentRoomAsset(view);
        if (roomAsset == null) throw new IllegalStateException("laptop: room asset missing");
        Scene scene = (Scene) field(view, "scene");
        if (scene == null) throw new IllegalStateException("laptop: scene missing");
        TransformManager transforms = engine.getTransformManager();
        int roomRootTransform = transforms.getInstance(roomAsset.getRoot());
        if (roomRootTransform == 0) throw new IllegalStateException("laptop: room root transform missing");

        MaterialInstance donor = firstMaterial(roomAsset, engine, "room_back_wall");
        MaterialInstance material = null;
        VertexBuffer vertices = null;
        IndexBuffer indices = null;
        int entity = 0;
        boolean added = false;
        try {
            material = MaterialInstance.duplicate(donor, "v80-reference-foreground-laptop");
            set4(material, "baseColorFactor", 0.055f, 0.050f, 0.045f, 1.0f);
            set1(material, "metallicFactor", 0.0f);
            set1(material, "roughnessFactor", 0.48f);
            set1(material, "reflectance", 0.42f);

            vertices = createVertices(engine);
            indices = createIndices(engine);
            engine.flushAndWait();

            entity = EntityManager.get().create();
            new RenderableManager.Builder(1)
                    .boundingBox(new Box(0f, 0f, 0f, WIDTH * 0.5f, HEIGHT * 0.5f, 0.02f))
                    .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertices, indices)
                    .material(0, material)
                    .castShadows(true)
                    .receiveShadows(true)
                    .culling(false)
                    .build(engine, entity);

            float[] local = new float[16];
            Matrix.setIdentityM(local, 0);
            Matrix.translateM(local, 0, CENTER_X, CENTER_Y, CENTER_Z);
            Matrix.rotateM(local, 0, YAW_DEG, 0f, 1f, 0f);
            Matrix.rotateM(local, 0, PITCH_DEG, 1f, 0f, 0f);
            transforms.create(entity, roomRootTransform, local);
            scene.addEntity(entity);
            added = true;

            State state = new State(view, engine, scene, entity, material, vertices, indices);
            synchronized (STATES) { STATES.put(view, state); }
            view.addOnAttachStateChangeListener(state);
            Celine3DDiagnostics.record(view.getContext(), "ROOM-144",
                    "Referenz-Laptop als abgeleitete Vordergrundgeometrie aktiv",
                    "CALL targetPx=(164.5,689.3)-(840.6,696.8)-(813.1,812.3)-(192.0,812.3)"
                            + " center=" + CENTER_X + "," + CENTER_Y + "," + CENTER_Z
                            + " size=" + WIDTH + "x" + HEIGHT
                            + " yaw=" + YAW_DEG + " pitch=" + PITCH_DEG
                            + " lowerEdgeOnTable=true sourceGLBsMutated=false"
                            + " camera/Celine/anchors unchanged");
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
        if (state != null) state.destroy();
    }

    private static FilamentAsset currentRoomAsset(Celine3DView view) throws Exception {
        Field statesField = CelineRoomEnvironmentV80.class.getDeclaredField("STATES");
        statesField.setAccessible(true);
        Object rawStates = statesField.get(null);
        if (!(rawStates instanceof Map)) return null;
        Object state = ((Map<?, ?>) rawStates).get(view);
        if (state == null) return null;
        Field assetField = state.getClass().getDeclaredField("roomAsset");
        assetField.setAccessible(true);
        return (FilamentAsset) assetField.get(state);
    }

    private static MaterialInstance firstMaterial(
            FilamentAsset asset, Engine engine, String entityName) {
        int entity = asset.getFirstEntityByName(entityName);
        if (entity == 0) throw new IllegalStateException("laptop donor entity missing: " + entityName);
        RenderableManager renderables = engine.getRenderableManager();
        int renderable = renderables.getInstance(entity);
        if (renderable == 0 || renderables.getPrimitiveCount(renderable) < 1) {
            throw new IllegalStateException("laptop donor renderable missing: " + entityName);
        }
        MaterialInstance material = renderables.getMaterialInstanceAt(renderable, 0);
        if (material == null) throw new IllegalStateException("laptop donor material missing");
        return material;
    }

    private static VertexBuffer createVertices(Engine engine) {
        final float halfWidth = WIDTH * 0.5f;
        final float halfHeight = HEIGHT * 0.5f;
        final int stride = 15 * 4;
        float[] data = {
                -halfWidth, -halfHeight, 0f,  0f,0f,0f,1f,  1f,1f,1f,1f,  0f,0f, 0f,0f,
                 halfWidth, -halfHeight, 0f,  0f,0f,0f,1f,  1f,1f,1f,1f,  1f,0f, 1f,0f,
                 halfWidth,  halfHeight, 0f,  0f,0f,0f,1f,  1f,1f,1f,1f,  1f,1f, 1f,1f,
                -halfWidth,  halfHeight, 0f,  0f,0f,0f,1f,  1f,1f,1f,1f,  0f,1f, 0f,1f,
        };
        FloatBuffer buffer = ByteBuffer.allocateDirect(4 * stride)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        buffer.put(data).flip();

        VertexBuffer vertices = new VertexBuffer.Builder()
                .vertexCount(4)
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
        vertices.setBufferAt(engine, 0, buffer);
        return vertices;
    }

    private static IndexBuffer createIndices(Engine engine) {
        ShortBuffer data = ByteBuffer.allocateDirect(6 * 2)
                .order(ByteOrder.nativeOrder()).asShortBuffer();
        data.put(new short[]{0, 1, 2, 0, 2, 3}).flip();
        IndexBuffer indices = new IndexBuffer.Builder()
                .indexCount(6)
                .bufferType(IndexBuffer.Builder.IndexType.USHORT)
                .build(engine);
        indices.setBuffer(engine, data);
        return indices;
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void set1(MaterialInstance instance, String name, float value) {
        try { if (instance.getMaterial().hasParameter(name)) instance.setParameter(name, value); }
        catch (Throwable ignored) {}
    }

    private static void set4(MaterialInstance instance, String name,
                             float r, float g, float b, float a) {
        try {
            if (instance.getMaterial().hasParameter(name)) {
                instance.setParameter(name, Colors.RgbaType.LINEAR, r, g, b, a);
            }
        } catch (Throwable ignored) {}
    }

    private static final class State implements View.OnAttachStateChangeListener {
        final Celine3DView view;
        final Engine engine;
        final Scene scene;
        final int entity;
        final MaterialInstance material;
        final VertexBuffer vertices;
        final IndexBuffer indices;
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

        @Override public void onViewAttachedToWindow(View v) {
        }

        @Override public void onViewDetachedFromWindow(View v) {
            CelineRoomForegroundLaptopV80.release(view);
        }

        void destroy() {
            if (destroyed) return;
            destroyed = true;
            view.removeOnAttachStateChangeListener(this);
            try { scene.removeEntity(entity); } catch (Throwable ignored) {}
            try { engine.destroyEntity(entity); } catch (Throwable ignored) {}
            try { EntityManager.get().destroy(entity); } catch (Throwable ignored) {}
            try { engine.destroyIndexBuffer(indices); } catch (Throwable ignored) {}
            try { engine.destroyVertexBuffer(vertices); } catch (Throwable ignored) {}
            try { engine.destroyMaterialInstance(material); } catch (Throwable ignored) {}
        }
    }
}
