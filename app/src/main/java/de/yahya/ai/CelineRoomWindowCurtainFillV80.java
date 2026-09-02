package de.yahya.ai;

import android.opengl.Matrix;

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
import java.util.WeakHashMap;

/**
 * Bounded derived geometry fill for the sparse reference drapes.
 *
 * Proof #64-#66 showed that the large curtain gaps are geometry-driven, not texture alpha. Proof #67
 * then confirmed that the derived night backdrop correctly closes the bright holes, while the source
 * drape mesh itself still reads as ragged vertical strips. Keep the immutable source drapes visible as
 * the detailed front layer, but place two broad warm-fabric panels behind the left/right source strips
 * and in front of the accepted night backdrop. This preserves the central night opening and does not
 * touch Celine, camera, anchors, furniture transforms, source GLB bytes or the interactive lamp.
 */
final class CelineRoomWindowCurtainFillV80 {
    private static final float CENTER_Y = 1.20f;
    private static final float CENTER_Z = -2.735f;
    private static final float HALF_WIDTH = 0.44f;
    private static final float HALF_HEIGHT = 1.08f;
    // Proof #113 measured group translation: all visible derived window layers move -1.14 m together.
    private static final float LEFT_CENTER_X = -1.67f;
    private static final float RIGHT_CENTER_X = 0.29f;

    private static final WeakHashMap<Celine3DView, State> STATES = new WeakHashMap<>();

    private CelineRoomWindowCurtainFillV80() {}

    static void apply(Celine3DView view, FilamentAsset asset, Engine engine) throws Exception {
        synchronized (STATES) {
            if (STATES.containsKey(view)) return;
        }

        int wallEntity = asset.getFirstEntityByName("room_back_wall");
        if (wallEntity == 0) throw new IllegalStateException("curtain fill: back wall fehlt");
        RenderableManager renderables = engine.getRenderableManager();
        int wallRenderable = renderables.getInstance(wallEntity);
        if (wallRenderable == 0 || renderables.getPrimitiveCount(wallRenderable) < 1) {
            throw new IllegalStateException("curtain fill: back wall material fehlt");
        }
        MaterialInstance source = renderables.getMaterialInstanceAt(wallRenderable, 0);
        if (source == null) throw new IllegalStateException("curtain fill: source material null");

        Scene scene = (Scene) field(view, "scene");
        TransformManager transforms = engine.getTransformManager();
        int roomRootTransform = transforms.getInstance(asset.getRoot());
        if (roomRootTransform == 0) throw new IllegalStateException("curtain fill: room root transform fehlt");

        MaterialInstance material = null;
        VertexBuffer vertices = null;
        IndexBuffer indices = null;
        int[] entities = new int[]{0, 0};
        boolean[] sceneAdded = new boolean[]{false, false};
        try {
            material = MaterialInstance.duplicate(source, "v80-window-curtain-fill");
            set4(material, "baseColorFactor", 0.52f, 0.42f, 0.34f, 1.0f);
            set1(material, "metallicFactor", 0.0f);
            set1(material, "roughnessFactor", 0.94f);
            set1(material, "reflectance", 0.28f);
            set3(material, "emissiveFactor", 0.0f, 0.0f, 0.0f);
            set1(material, "emissiveStrength", 0.0f);

            vertices = createVertices(engine);
            indices = createIndices(engine);
            float[] centers = new float[]{LEFT_CENTER_X, RIGHT_CENTER_X};
            for (int i = 0; i < entities.length; i++) {
                int entity = EntityManager.get().create();
                entities[i] = entity;
                new RenderableManager.Builder(1)
                        .boundingBox(new Box(0f, 0f, 0f, HALF_WIDTH, HALF_HEIGHT, 0.02f))
                        .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertices, indices)
                        .material(0, material)
                        .castShadows(false)
                        .receiveShadows(true)
                        .culling(false)
                        .build(engine, entity);

                float[] local = new float[16];
                Matrix.setIdentityM(local, 0);
                Matrix.translateM(local, 0, centers[i], CENTER_Y, CENTER_Z);
                transforms.create(entity, roomRootTransform, local);
                scene.addEntity(entity);
                sceneAdded[i] = true;
            }

            State state = new State(scene, entities, material, vertices, indices);
            synchronized (STATES) { STATES.put(view, state); }
            Celine3DDiagnostics.record(view.getContext(), "ROOM-149",
                    "Breite Vorhang-Füllflächen hinter sparse drapes aktiv",
                    "left=" + LEFT_CENTER_X + " right=" + RIGHT_CENTER_X
                            + " y=" + CENTER_Y + " z=" + CENTER_Z
                            + " panel=" + (HALF_WIDTH * 2f) + "x" + (HALF_HEIGHT * 2f)
                            + " · central night opening/source drapes preserved"
                            + " · source GLB/Celine/camera/anchors/lamp unchanged");
        } catch (Throwable error) {
            for (int i = 0; i < entities.length; i++) {
                if (sceneAdded[i] && entities[i] != 0) {
                    try { scene.remove(entities[i]); } catch (Throwable ignored) {}
                }
                if (entities[i] != 0) {
                    try { engine.destroyEntity(entities[i]); } catch (Throwable ignored) {}
                    try { EntityManager.get().destroy(entities[i]); } catch (Throwable ignored) {}
                }
            }
            if (indices != null) try { engine.destroyIndexBuffer(indices); } catch (Throwable ignored) {}
            if (vertices != null) try { engine.destroyVertexBuffer(vertices); } catch (Throwable ignored) {}
            if (material != null) try { engine.destroyMaterialInstance(material); } catch (Throwable ignored) {}
            throw error;
        }
    }

    private static VertexBuffer createVertices(Engine engine) {
        final int stride = 13 * 4;
        float[] data = {
                -HALF_WIDTH, -HALF_HEIGHT, 0f,  0f,0f,0f,1f,  1f,1f,1f,1f,  0f,0f,
                 HALF_WIDTH, -HALF_HEIGHT, 0f,  0f,0f,0f,1f,  1f,1f,1f,1f,  1f,0f,
                 HALF_WIDTH,  HALF_HEIGHT, 0f,  0f,0f,0f,1f,  1f,1f,1f,1f,  1f,1f,
                -HALF_WIDTH,  HALF_HEIGHT, 0f,  0f,0f,0f,1f,  1f,1f,1f,1f,  0f,1f,
        };
        ByteBuffer raw = ByteBuffer.allocateDirect(4 * stride).order(ByteOrder.nativeOrder());
        FloatBuffer buffer = raw.asFloatBuffer();
        buffer.put(data).flip();

        FloatBuffer uv1 = ByteBuffer.allocateDirect(4 * 2 * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        uv1.put(new float[]{0f,0f, 1f,0f, 1f,1f, 0f,1f}).flip();

        VertexBuffer vb = new VertexBuffer.Builder()
                .vertexCount(4)
                .bufferCount(2)
                .attribute(VertexBuffer.VertexAttribute.POSITION, 0,
                        VertexBuffer.AttributeType.FLOAT3, 0, stride)
                .attribute(VertexBuffer.VertexAttribute.TANGENTS, 0,
                        VertexBuffer.AttributeType.FLOAT4, 3 * 4, stride)
                .attribute(VertexBuffer.VertexAttribute.COLOR, 0,
                        VertexBuffer.AttributeType.FLOAT4, 7 * 4, stride)
                .attribute(VertexBuffer.VertexAttribute.UV0, 0,
                        VertexBuffer.AttributeType.FLOAT2, 11 * 4, stride)
                .attribute(VertexBuffer.VertexAttribute.UV1, 1,
                        VertexBuffer.AttributeType.FLOAT2, 0, 2 * 4)
                .build(engine);
        vb.setBufferAt(engine, 0, buffer);
        vb.setBufferAt(engine, 1, uv1);
        return vb;
    }

    private static IndexBuffer createIndices(Engine engine) {
        ShortBuffer data = ByteBuffer.allocateDirect(6 * 2)
                .order(ByteOrder.nativeOrder()).asShortBuffer();
        data.put(new short[]{0, 1, 2, 0, 2, 3}).flip();
        IndexBuffer ib = new IndexBuffer.Builder()
                .indexCount(6)
                .bufferType(IndexBuffer.Builder.IndexType.USHORT)
                .build(engine);
        ib.setBuffer(engine, data);
        return ib;
    }

    static void release(Celine3DView view, Engine engine) {
        State state;
        synchronized (STATES) { state = STATES.remove(view); }
        if (state == null) return;
        for (int entity : state.entities) {
            try { state.scene.remove(entity); } catch (Throwable ignored) {}
            try { engine.destroyEntity(entity); } catch (Throwable ignored) {}
            try { EntityManager.get().destroy(entity); } catch (Throwable ignored) {}
        }
        try { engine.destroyIndexBuffer(state.indices); } catch (Throwable ignored) {}
        try { engine.destroyVertexBuffer(state.vertices); } catch (Throwable ignored) {}
        try { engine.destroyMaterialInstance(state.material); } catch (Throwable ignored) {}
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

    private static void set3(MaterialInstance instance, String name, float x, float y, float z) {
        try { if (instance.getMaterial().hasParameter(name)) instance.setParameter(name, x, y, z); }
        catch (Throwable ignored) {}
    }

    private static void set4(MaterialInstance instance, String name, float r, float g, float b, float a) {
        try {
            if (instance.getMaterial().hasParameter(name)) {
                instance.setParameter(name, Colors.RgbaType.LINEAR, r, g, b, a);
            }
        } catch (Throwable ignored) {}
    }

    private static final class State {
        final Scene scene;
        final int[] entities;
        final MaterialInstance material;
        final VertexBuffer vertices;
        final IndexBuffer indices;

        State(Scene scene, int[] entities, MaterialInstance material,
              VertexBuffer vertices, IndexBuffer indices) {
            this.scene = scene;
            this.entities = entities;
            this.material = material;
            this.vertices = vertices;
            this.indices = indices;
        }
    }
}
