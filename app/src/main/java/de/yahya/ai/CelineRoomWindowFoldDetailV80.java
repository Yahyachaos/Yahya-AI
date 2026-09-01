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
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/**
 * Small derived fold facets for the v80 reference window.
 *
 * Proof #72 validates hiding the shredded source mesh: the window becomes clean but too flat and
 * board-like. Proof #73 then showed that many dark narrow facets read as regular bars instead of fabric.
 * Keep only a few low-contrast, gently rotated facets to create restrained fold light/shadow variation
 * without changing the source GLB, room transforms, window coverage, Celine, camera or anchors.
 */
final class CelineRoomWindowFoldDetailV80 {
    private static final float CENTER_Y = 1.20f;
    private static final float CENTER_Z = -2.705f;
    private static final float HALF_HEIGHT = 1.03f;
    private static final float HALF_WIDTH_SIDE = 0.060f;
    private static final float HALF_WIDTH_SHEER = 0.045f;

    private static final float[] SIDE_X = {-0.70f, -0.32f, 1.22f, 1.62f};
    private static final float[] SHEER_X = {0.20f, 0.70f};
    private static final float[] ANGLES = {4.5f, -4.0f, 4.0f, -4.5f};

    private static final WeakHashMap<Celine3DView, State> STATES = new WeakHashMap<>();

    private CelineRoomWindowFoldDetailV80() {}

    static void apply(Celine3DView view, FilamentAsset asset, Engine engine) throws Exception {
        synchronized (STATES) {
            if (STATES.containsKey(view)) return;
        }

        int wallEntity = asset.getFirstEntityByName("room_back_wall");
        if (wallEntity == 0) throw new IllegalStateException("window folds: back wall fehlt");
        RenderableManager renderables = engine.getRenderableManager();
        int wallRenderable = renderables.getInstance(wallEntity);
        if (wallRenderable == 0 || renderables.getPrimitiveCount(wallRenderable) < 1) {
            throw new IllegalStateException("window folds: back wall material fehlt");
        }
        MaterialInstance source = renderables.getMaterialInstanceAt(wallRenderable, 0);
        if (source == null) throw new IllegalStateException("window folds: source material null");

        Scene scene = (Scene) field(view, "scene");
        TransformManager transforms = engine.getTransformManager();
        int roomRootTransform = transforms.getInstance(asset.getRoot());
        if (roomRootTransform == 0) throw new IllegalStateException("window folds: room root transform fehlt");

        MaterialInstance sideMaterial = null;
        MaterialInstance sheerMaterial = null;
        VertexBuffer sideVertices = null;
        VertexBuffer sheerVertices = null;
        IndexBuffer indices = null;
        List<Integer> entities = new ArrayList<>();
        try {
            sideMaterial = MaterialInstance.duplicate(source, "v80-window-side-folds");
            set4(sideMaterial, "baseColorFactor", 0.47f, 0.37f, 0.30f, 1f);
            tuneFabric(sideMaterial);
            sheerMaterial = MaterialInstance.duplicate(source, "v80-window-sheer-folds");
            set4(sheerMaterial, "baseColorFactor", 0.73f, 0.67f, 0.60f, 1f);
            tuneFabric(sheerMaterial);

            sideVertices = createVertices(engine, HALF_WIDTH_SIDE);
            sheerVertices = createVertices(engine, HALF_WIDTH_SHEER);
            indices = createIndices(engine);

            for (int i = 0; i < SIDE_X.length; i++) {
                int entity = createFacet(engine, sideMaterial, sideVertices, indices, HALF_WIDTH_SIDE);
                place(transforms, entity, roomRootTransform, SIDE_X[i], ANGLES[i % ANGLES.length]);
                scene.addEntity(entity);
                entities.add(entity);
            }
            for (int i = 0; i < SHEER_X.length; i++) {
                int entity = createFacet(engine, sheerMaterial, sheerVertices, indices, HALF_WIDTH_SHEER);
                place(transforms, entity, roomRootTransform, SHEER_X[i], ANGLES[(i + 1) % ANGLES.length] * 0.70f);
                scene.addEntity(entity);
                entities.add(entity);
            }

            State state = new State(scene, entities, sideMaterial, sheerMaterial,
                    sideVertices, sheerVertices, indices);
            synchronized (STATES) { STATES.put(view, state); }
            Celine3DDiagnostics.record(view.getContext(), "ROOM-144",
                    "Subtile abgeleitete Vorhangfalten aktiv",
                    "sideFacets=" + SIDE_X.length + " sheerFacets=" + SHEER_X.length
                            + " z=" + CENTER_Z + " · sparse low-contrast fold detail only"
                            + " · source GLB/coverage/Celine/camera/anchors/lamp unchanged");
        } catch (Throwable error) {
            for (int entity : entities) destroyEntity(scene, engine, entity);
            if (indices != null) try { engine.destroyIndexBuffer(indices); } catch (Throwable ignored) {}
            if (sideVertices != null) try { engine.destroyVertexBuffer(sideVertices); } catch (Throwable ignored) {}
            if (sheerVertices != null) try { engine.destroyVertexBuffer(sheerVertices); } catch (Throwable ignored) {}
            if (sideMaterial != null) try { engine.destroyMaterialInstance(sideMaterial); } catch (Throwable ignored) {}
            if (sheerMaterial != null) try { engine.destroyMaterialInstance(sheerMaterial); } catch (Throwable ignored) {}
            throw error;
        }
    }

    private static void tuneFabric(MaterialInstance material) {
        set1(material, "metallicFactor", 0f);
        set1(material, "roughnessFactor", 0.96f);
        set1(material, "reflectance", 0.26f);
        set3(material, "emissiveFactor", 0f, 0f, 0f);
        set1(material, "emissiveStrength", 0f);
    }

    private static int createFacet(Engine engine, MaterialInstance material,
                                   VertexBuffer vertices, IndexBuffer indices, float halfWidth) {
        int entity = EntityManager.get().create();
        new RenderableManager.Builder(1)
                .boundingBox(new Box(0f, 0f, 0f, halfWidth, HALF_HEIGHT, 0.06f))
                .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertices, indices)
                .material(0, material)
                .castShadows(false)
                .receiveShadows(true)
                .culling(false)
                .build(engine, entity);
        return entity;
    }

    private static void place(TransformManager transforms, int entity, int parent, float x, float angle) {
        float[] local = new float[16];
        Matrix.setIdentityM(local, 0);
        Matrix.translateM(local, 0, x, CENTER_Y, CENTER_Z);
        Matrix.rotateM(local, 0, angle, 0f, 1f, 0f);
        transforms.create(entity, parent, local);
    }

    private static VertexBuffer createVertices(Engine engine, float halfWidth) {
        final int stride = 13 * 4;
        float[] data = {
                -halfWidth, -HALF_HEIGHT, 0f,  0f,0f,0f,1f,  1f,1f,1f,1f,  0f,0f,
                 halfWidth, -HALF_HEIGHT, 0f,  0f,0f,0f,1f,  1f,1f,1f,1f,  1f,0f,
                 halfWidth,  HALF_HEIGHT, 0f,  0f,0f,0f,1f,  1f,1f,1f,1f,  1f,1f,
                -halfWidth,  HALF_HEIGHT, 0f,  0f,0f,0f,1f,  1f,1f,1f,1f,  0f,1f,
        };
        FloatBuffer buffer = ByteBuffer.allocateDirect(4 * stride)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        buffer.put(data).flip();
        FloatBuffer uv1 = ByteBuffer.allocateDirect(4 * 2 * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        uv1.put(new float[]{0f,0f, 1f,0f, 1f,1f, 0f,1f}).flip();
        VertexBuffer vb = new VertexBuffer.Builder()
                .vertexCount(4).bufferCount(2)
                .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, stride)
                .attribute(VertexBuffer.VertexAttribute.TANGENTS, 0, VertexBuffer.AttributeType.FLOAT4, 3 * 4, stride)
                .attribute(VertexBuffer.VertexAttribute.COLOR, 0, VertexBuffer.AttributeType.FLOAT4, 7 * 4, stride)
                .attribute(VertexBuffer.VertexAttribute.UV0, 0, VertexBuffer.AttributeType.FLOAT2, 11 * 4, stride)
                .attribute(VertexBuffer.VertexAttribute.UV1, 1, VertexBuffer.AttributeType.FLOAT2, 0, 2 * 4)
                .build(engine);
        vb.setBufferAt(engine, 0, buffer);
        vb.setBufferAt(engine, 1, uv1);
        return vb;
    }

    private static IndexBuffer createIndices(Engine engine) {
        ShortBuffer data = ByteBuffer.allocateDirect(6 * 2)
                .order(ByteOrder.nativeOrder()).asShortBuffer();
        data.put(new short[]{0,1,2, 0,2,3}).flip();
        IndexBuffer ib = new IndexBuffer.Builder()
                .indexCount(6).bufferType(IndexBuffer.Builder.IndexType.USHORT).build(engine);
        ib.setBuffer(engine, data);
        return ib;
    }

    static void release(Celine3DView view, Engine engine) {
        State state;
        synchronized (STATES) { state = STATES.remove(view); }
        if (state == null) return;
        for (int entity : state.entities) destroyEntity(state.scene, engine, entity);
        try { engine.destroyIndexBuffer(state.indices); } catch (Throwable ignored) {}
        try { engine.destroyVertexBuffer(state.sideVertices); } catch (Throwable ignored) {}
        try { engine.destroyVertexBuffer(state.sheerVertices); } catch (Throwable ignored) {}
        try { engine.destroyMaterialInstance(state.sideMaterial); } catch (Throwable ignored) {}
        try { engine.destroyMaterialInstance(state.sheerMaterial); } catch (Throwable ignored) {}
    }

    private static void destroyEntity(Scene scene, Engine engine, int entity) {
        try { scene.remove(entity); } catch (Throwable ignored) {}
        try { engine.destroyEntity(entity); } catch (Throwable ignored) {}
        try { EntityManager.get().destroy(entity); } catch (Throwable ignored) {}
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
    private static void set1(MaterialInstance instance, String name, float value) {
        try { if (instance.getMaterial().hasParameter(name)) instance.setParameter(name, value); } catch (Throwable ignored) {}
    }
    private static void set3(MaterialInstance instance, String name, float x, float y, float z) {
        try { if (instance.getMaterial().hasParameter(name)) instance.setParameter(name, x, y, z); } catch (Throwable ignored) {}
    }
    private static void set4(MaterialInstance instance, String name, float r, float g, float b, float a) {
        try { if (instance.getMaterial().hasParameter(name)) instance.setParameter(name, Colors.RgbaType.LINEAR, r,g,b,a); }
        catch (Throwable ignored) {}
    }

    private static final class State {
        final Scene scene;
        final List<Integer> entities;
        final MaterialInstance sideMaterial;
        final MaterialInstance sheerMaterial;
        final VertexBuffer sideVertices;
        final VertexBuffer sheerVertices;
        final IndexBuffer indices;
        State(Scene scene, List<Integer> entities, MaterialInstance sideMaterial, MaterialInstance sheerMaterial,
              VertexBuffer sideVertices, VertexBuffer sheerVertices, IndexBuffer indices) {
            this.scene = scene; this.entities = entities; this.sideMaterial = sideMaterial;
            this.sheerMaterial = sheerMaterial; this.sideVertices = sideVertices;
            this.sheerVertices = sheerVertices; this.indices = indices;
        }
    }
}
