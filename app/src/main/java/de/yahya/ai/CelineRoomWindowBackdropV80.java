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
 * One bounded derived geometry repair for the reference window.
 *
 * Proof #64-#66 demonstrated that the large vertical openings remain at identical positions even
 * when room_window_drapes receives radically different fully opaque base-color atlases. The source
 * drape mesh is therefore too sparse to act as a complete night window. Keep that immutable source
 * geometry intact and place one opaque, dark backing plane a few centimeters behind it. The plane is
 * parented to the accepted room root; no Celine/camera/anchor/furniture transform is changed.
 */
final class CelineRoomWindowBackdropV80 {
    // Proof #118 measures the visible derived window at about x=0.148..0.601 versus the canonical
    // target x=0.195..0.581. Narrow the whole derived window group by 0.8521 around a slightly
    // right-shifted center. These values are mirrored by curtain/sheer/fold derived layers.
    private static final float CENTER_X = -0.605f;
    private static final float CENTER_Y = 1.20f;
    private static final float CENTER_Z = -2.755f;
    private static final float HALF_WIDTH = 1.210f;
    private static final float HALF_HEIGHT = 1.12f;

    private static final WeakHashMap<Celine3DView, State> STATES = new WeakHashMap<>();

    private CelineRoomWindowBackdropV80() {}

    static void apply(Celine3DView view, FilamentAsset asset, Engine engine) throws Exception {
        synchronized (STATES) {
            if (STATES.containsKey(view)) return;
        }

        int wallEntity = asset.getFirstEntityByName("room_back_wall");
        if (wallEntity == 0) throw new IllegalStateException("window backdrop: back wall fehlt");
        RenderableManager renderables = engine.getRenderableManager();
        int wallRenderable = renderables.getInstance(wallEntity);
        if (wallRenderable == 0 || renderables.getPrimitiveCount(wallRenderable) < 1) {
            throw new IllegalStateException("window backdrop: back wall material fehlt");
        }
        MaterialInstance source = renderables.getMaterialInstanceAt(wallRenderable, 0);
        if (source == null) throw new IllegalStateException("window backdrop: source material null");

        Scene scene = (Scene) field(view, "scene");
        TransformManager transforms = engine.getTransformManager();
        int roomRoot = asset.getRoot();
        int roomRootTransform = transforms.getInstance(roomRoot);
        if (roomRootTransform == 0) throw new IllegalStateException("window backdrop: room root transform fehlt");

        MaterialInstance material = null;
        VertexBuffer vertices = null;
        IndexBuffer indices = null;
        int entity = 0;
        boolean sceneAdded = false;
        try {
            material = MaterialInstance.duplicate(source, "v80-window-night-backdrop");
            set4(material, "baseColorFactor", 0.045f, 0.055f, 0.075f, 1.0f);
            set1(material, "metallicFactor", 0.0f);
            set1(material, "roughnessFactor", 0.96f);
            set1(material, "reflectance", 0.22f);
            set3(material, "emissiveFactor", 0.006f, 0.010f, 0.018f);
            set1(material, "emissiveStrength", 1.0f);

            vertices = createVertices(engine);
            indices = createIndices(engine);
            entity = EntityManager.get().create();
            new RenderableManager.Builder(1)
                    .boundingBox(new Box(0f, 0f, 0f, HALF_WIDTH, HALF_HEIGHT, 0.02f))
                    .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertices, indices)
                    .material(0, material)
                    .castShadows(false)
                    .receiveShadows(false)
                    .culling(false)
                    .build(engine, entity);

            float[] local = new float[16];
            Matrix.setIdentityM(local, 0);
            Matrix.translateM(local, 0, CENTER_X, CENTER_Y, CENTER_Z);
            transforms.create(entity, roomRootTransform, local);
            scene.addEntity(entity);
            sceneAdded = true;

            State state = new State(scene, entity, material, vertices, indices);
            synchronized (STATES) { STATES.put(view, state); }
            Celine3DDiagnostics.record(view.getContext(), "ROOM-148",
                    "Fenster-Nachtfläche hinter sparse drapes aktiv",
                    "center=" + CENTER_X + "," + CENTER_Y + "," + CENTER_Z
                            + " size=" + (HALF_WIDTH * 2f) + "x" + (HALF_HEIGHT * 2f)
                            + " · source GLB/UV/anchors/camera unchanged");
        } catch (Throwable error) {
            if (sceneAdded && entity != 0) {
                try { scene.remove(entity); } catch (Throwable ignored) {}
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
        for (int v = 0; v < 4; v++) {
            int offset = v * 13;
            for (int i = 0; i < 13; i++) buffer.put(data[offset + i]);
        }
        buffer.flip();

        // Separate UV1 buffer avoids growing the interleaved layout while satisfying any glTF
        // material variant that declares a second UV channel.
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
        try { state.scene.remove(state.entity); } catch (Throwable ignored) {}
        try { engine.destroyEntity(state.entity); } catch (Throwable ignored) {}
        try { EntityManager.get().destroy(state.entity); } catch (Throwable ignored) {}
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
        final int entity;
        final MaterialInstance material;
        final VertexBuffer vertices;
        final IndexBuffer indices;

        State(Scene scene, int entity, MaterialInstance material,
              VertexBuffer vertices, IndexBuffer indices) {
            this.scene = scene;
            this.entity = entity;
            this.material = material;
            this.vertices = vertices;
            this.indices = indices;
        }
    }
}
