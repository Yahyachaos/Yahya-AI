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
 * Bounded derived geometry repair for the reference window.
 *
 * The immutable source drape mesh is too sparse to provide the reference night opening, so the
 * accepted derived backdrop stays behind it. Real Candidate #1232 then locks the central night
 * witness to RGB 36/26/19 versus reference 37/27/20, but the true CALL raster still has one broad
 * uninterrupted dark opening where Refernzbild.png has a clear warm vertical center mullion around
 * x=409..421 on the canonical 1016px stage. Add only that measured mullion in front of the accepted
 * backdrop/derived sheers. No window envelope, source GLB, camera, furniture transform or Celine
 * identity/rig changes.
 */
final class CelineRoomWindowBackdropV80 {
    // Proof #118 measures the visible derived window at about x=0.148..0.601 versus the canonical
    // target x=0.195..0.581. These accepted backdrop dimensions remain protected.
    private static final float CENTER_X = -0.605f;
    private static final float CENTER_Y = 1.20f;
    private static final float CENTER_Z = -2.755f;
    private static final float HALF_WIDTH = 1.210f;
    private static final float HALF_HEIGHT = 1.12f;

    // Real Candidate #1232 + exact reference mapping: current dark opening spans approximately
    // x=356..465 with no center division; reference carries a warm vertical frame near x=409..421.
    // The accepted sheer centers map at about 136 px / room-local meter, placing the measured frame
    // center at local X ~= -0.56 and a 12px raster width at ~0.09m world width.
    private static final float MULLION_CENTER_X = -0.560f;
    private static final float MULLION_CENTER_Y = 1.20f;
    private static final float MULLION_CENTER_Z = -2.710f;
    private static final float MULLION_HALF_WIDTH = 0.045f;
    private static final float MULLION_HALF_HEIGHT = 0.98f;

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
        MaterialInstance mullionMaterial = null;
        VertexBuffer vertices = null;
        VertexBuffer mullionVertices = null;
        IndexBuffer indices = null;
        int entity = 0;
        int mullionEntity = 0;
        boolean sceneAdded = false;
        boolean mullionSceneAdded = false;
        try {
            material = MaterialInstance.duplicate(source, "v80-window-night-backdrop");
            // Real Candidate #1232 accepts this tuple: central night RGB 36/26/19 versus reference
            // 37/27/20. Preserve it exactly while adding only the missing structural mullion.
            set4(material, "baseColorFactor", 0.188f, 0.128f, 0.084f, 1.0f);
            set1(material, "metallicFactor", 0.0f);
            set1(material, "roughnessFactor", 0.96f);
            set1(material, "reflectance", 0.22f);
            set3(material, "emissiveFactor", 0.006f, 0.010f, 0.018f);
            set1(material, "emissiveStrength", 1.0f);

            mullionMaterial = MaterialInstance.duplicate(source, "v80-window-center-mullion");
            // Exact reference center-frame witness is a warm cream/brown under room lighting,
            // around RGB 130/92/58. Use the already-proven shell response as a bounded starting
            // factor; this candidate is accepted/rejected only by the next true CALL raster.
            set4(mullionMaterial, "baseColorFactor", 0.90f, 0.65f, 0.42f, 1.0f);
            set1(mullionMaterial, "metallicFactor", 0.0f);
            set1(mullionMaterial, "roughnessFactor", 0.90f);
            set1(mullionMaterial, "reflectance", 0.35f);
            set3(mullionMaterial, "emissiveFactor", 0.0f, 0.0f, 0.0f);
            set1(mullionMaterial, "emissiveStrength", 0.0f);

            vertices = createVertices(engine, HALF_WIDTH, HALF_HEIGHT);
            mullionVertices = createVertices(engine, MULLION_HALF_WIDTH, MULLION_HALF_HEIGHT);
            indices = createIndices(engine);

            entity = createPlane(engine, material, vertices, indices, HALF_WIDTH, HALF_HEIGHT);
            place(transforms, entity, roomRootTransform, CENTER_X, CENTER_Y, CENTER_Z);
            scene.addEntity(entity);
            sceneAdded = true;

            mullionEntity = createPlane(engine, mullionMaterial, mullionVertices, indices,
                    MULLION_HALF_WIDTH, MULLION_HALF_HEIGHT);
            place(transforms, mullionEntity, roomRootTransform,
                    MULLION_CENTER_X, MULLION_CENTER_Y, MULLION_CENTER_Z);
            scene.addEntity(mullionEntity);
            mullionSceneAdded = true;

            State state = new State(scene, entity, mullionEntity, material, mullionMaterial,
                    vertices, mullionVertices, indices);
            synchronized (STATES) { STATES.put(view, state); }
            Celine3DDiagnostics.record(view.getContext(), "ROOM-148",
                    "Fenster-Nachtfläche + gemessener Mittelpfosten aktiv",
                    "backdrop=" + CENTER_X + "," + CENTER_Y + "," + CENTER_Z
                            + " size=" + (HALF_WIDTH * 2f) + "x" + (HALF_HEIGHT * 2f)
                            + " · #1232 night=36/26/19 ref=37/27/20"
                            + " · mullion=" + MULLION_CENTER_X + "," + MULLION_CENTER_Y + ","
                            + MULLION_CENTER_Z + " size=" + (MULLION_HALF_WIDTH * 2f) + "x"
                            + (MULLION_HALF_HEIGHT * 2f) + " targetRasterX=409..421"
                            + " · source GLB/window envelope/camera/furniture/Celine unchanged");
        } catch (Throwable error) {
            if (mullionSceneAdded && mullionEntity != 0) {
                try { scene.remove(mullionEntity); } catch (Throwable ignored) {}
            }
            if (mullionEntity != 0) {
                try { engine.destroyEntity(mullionEntity); } catch (Throwable ignored) {}
                try { EntityManager.get().destroy(mullionEntity); } catch (Throwable ignored) {}
            }
            if (sceneAdded && entity != 0) {
                try { scene.remove(entity); } catch (Throwable ignored) {}
            }
            if (entity != 0) {
                try { engine.destroyEntity(entity); } catch (Throwable ignored) {}
                try { EntityManager.get().destroy(entity); } catch (Throwable ignored) {}
            }
            if (indices != null) try { engine.destroyIndexBuffer(indices); } catch (Throwable ignored) {}
            if (mullionVertices != null) try { engine.destroyVertexBuffer(mullionVertices); } catch (Throwable ignored) {}
            if (vertices != null) try { engine.destroyVertexBuffer(vertices); } catch (Throwable ignored) {}
            if (mullionMaterial != null) try { engine.destroyMaterialInstance(mullionMaterial); } catch (Throwable ignored) {}
            if (material != null) try { engine.destroyMaterialInstance(material); } catch (Throwable ignored) {}
            throw error;
        }
    }

    private static int createPlane(Engine engine, MaterialInstance material, VertexBuffer vertices,
                                   IndexBuffer indices, float halfWidth, float halfHeight) {
        int entity = EntityManager.get().create();
        new RenderableManager.Builder(1)
                .boundingBox(new Box(0f, 0f, 0f, halfWidth, halfHeight, 0.02f))
                .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertices, indices)
                .material(0, material)
                .castShadows(false)
                .receiveShadows(false)
                .culling(false)
                .build(engine, entity);
        return entity;
    }

    private static void place(TransformManager transforms, int entity, int parent,
                              float x, float y, float z) {
        float[] local = new float[16];
        Matrix.setIdentityM(local, 0);
        Matrix.translateM(local, 0, x, y, z);
        transforms.create(entity, parent, local);
    }

    private static VertexBuffer createVertices(Engine engine, float halfWidth, float halfHeight) {
        final int stride = 13 * 4;
        float[] data = {
                -halfWidth, -halfHeight, 0f,  0f,0f,0f,1f,  1f,1f,1f,1f,  0f,0f,
                 halfWidth, -halfHeight, 0f,  0f,0f,0f,1f,  1f,1f,1f,1f,  1f,0f,
                 halfWidth,  halfHeight, 0f,  0f,0f,0f,1f,  1f,1f,1f,1f,  1f,1f,
                -halfWidth,  halfHeight, 0f,  0f,0f,0f,1f,  1f,1f,1f,1f,  0f,1f,
        };
        ByteBuffer raw = ByteBuffer.allocateDirect(4 * stride).order(ByteOrder.nativeOrder());
        FloatBuffer buffer = raw.asFloatBuffer();
        for (int v = 0; v < 4; v++) {
            int offset = v * 13;
            for (int i = 0; i < 13; i++) buffer.put(data[offset + i]);
        }
        buffer.flip();

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
        try { state.scene.remove(state.mullionEntity); } catch (Throwable ignored) {}
        try { engine.destroyEntity(state.mullionEntity); } catch (Throwable ignored) {}
        try { EntityManager.get().destroy(state.mullionEntity); } catch (Throwable ignored) {}
        try { state.scene.remove(state.entity); } catch (Throwable ignored) {}
        try { engine.destroyEntity(state.entity); } catch (Throwable ignored) {}
        try { EntityManager.get().destroy(state.entity); } catch (Throwable ignored) {}
        try { engine.destroyIndexBuffer(state.indices); } catch (Throwable ignored) {}
        try { engine.destroyVertexBuffer(state.mullionVertices); } catch (Throwable ignored) {}
        try { engine.destroyVertexBuffer(state.vertices); } catch (Throwable ignored) {}
        try { engine.destroyMaterialInstance(state.mullionMaterial); } catch (Throwable ignored) {}
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
        final int mullionEntity;
        final MaterialInstance material;
        final MaterialInstance mullionMaterial;
        final VertexBuffer vertices;
        final VertexBuffer mullionVertices;
        final IndexBuffer indices;

        State(Scene scene, int entity, int mullionEntity, MaterialInstance material,
              MaterialInstance mullionMaterial, VertexBuffer vertices,
              VertexBuffer mullionVertices, IndexBuffer indices) {
            this.scene = scene;
            this.entity = entity;
            this.mullionEntity = mullionEntity;
            this.material = material;
            this.mullionMaterial = mullionMaterial;
            this.vertices = vertices;
            this.mullionVertices = mullionVertices;
            this.indices = indices;
        }
    }
}
