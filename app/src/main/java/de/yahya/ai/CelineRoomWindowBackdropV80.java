package de.yahya.ai;

import android.opengl.Matrix;

import com.google.android.filament.Box;
import com.google.android.filament.Colors;
import com.google.android.filament.Engine;
import com.google.android.filament.EntityManager;
import com.google.android.filament.IndexBuffer;
import com.google.android.filament.IndirectLight;
import com.google.android.filament.LightManager;
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
 * Recovery after the user-rejected #1379 raster deliberately keeps this owner small: preserve the
 * source room/window materials, use only the accepted two dark night panes, and normalize the real
 * renderer's existing key/indirect lights without activating the experimental texture/fill/source-hide
 * stack, ceiling/bed material overrides or practical light owned by CelineRoomReferenceLightingV80.
 * Geometry, source GLBs, camera/FOV, furniture transforms and Celine remain untouched.
 */
final class CelineRoomWindowBackdropV80 {
    // Accepted outer backdrop contract from Proof #118.
    private static final float OUTER_CENTER_X = -0.605f;
    private static final float CENTER_Y = 1.20f;
    private static final float CENTER_Z = -2.755f;
    private static final float OUTER_HALF_WIDTH = 1.210f;
    private static final float HALF_HEIGHT = 1.12f;

    // Clean recovery lighting: reuse the measured global key/fill values only. No material owner,
    // source hiding, derived curtain fill, bed emission or practical light is installed here.
    private static final float RECOVERY_KEY_RED = 1.00f;
    private static final float RECOVERY_KEY_GREEN = 0.95f;
    private static final float RECOVERY_KEY_BLUE = 0.90f;
    private static final float RECOVERY_KEY_LUX = 5000.0f;
    private static final float RECOVERY_INDIRECT_LUX = 5600.0f;

    // Real Candidate #1232 exact mapping: current uninterrupted dark opening is missing the
    // reference vertical window division around CALL-stage x=409..421. The accepted sheer-center
    // mapping is about 136 px per room-local meter, so a 12px structural division is ~0.09m wide.
    // Keep the accepted full outer extent [-1.815, +0.605] and remove only the gap
    // [-0.605, -0.515] centered at -0.560.
    private static final float GAP_CENTER_X = -0.560f;
    private static final float GAP_HALF_WIDTH = 0.045f;
    private static final float LEFT_EDGE_X = OUTER_CENTER_X - OUTER_HALF_WIDTH;
    private static final float RIGHT_EDGE_X = OUTER_CENTER_X + OUTER_HALF_WIDTH;
    private static final float GAP_LEFT_X = GAP_CENTER_X - GAP_HALF_WIDTH;
    private static final float GAP_RIGHT_X = GAP_CENTER_X + GAP_HALF_WIDTH;
    private static final float LEFT_CENTER_X = (LEFT_EDGE_X + GAP_LEFT_X) * 0.5f;
    private static final float LEFT_HALF_WIDTH = (GAP_LEFT_X - LEFT_EDGE_X) * 0.5f;
    private static final float RIGHT_CENTER_X = (GAP_RIGHT_X + RIGHT_EDGE_X) * 0.5f;
    private static final float RIGHT_HALF_WIDTH = (RIGHT_EDGE_X - GAP_RIGHT_X) * 0.5f;

    private static final WeakHashMap<Celine3DView, State> STATES = new WeakHashMap<>();

    private CelineRoomWindowBackdropV80() {}

    static void apply(Celine3DView view, FilamentAsset asset, Engine engine) throws Exception {
        synchronized (STATES) {
            if (STATES.containsKey(view)) return;
        }

        applyRecoveryNeutralLighting(view, engine);

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
        int roomRootTransform = transforms.getInstance(asset.getRoot());
        if (roomRootTransform == 0) throw new IllegalStateException("window backdrop: room root transform fehlt");

        MaterialInstance material = null;
        VertexBuffer leftVertices = null;
        VertexBuffer rightVertices = null;
        IndexBuffer indices = null;
        int[] entities = new int[]{0, 0};
        boolean[] sceneAdded = new boolean[]{false, false};
        try {
            material = MaterialInstance.duplicate(source, "v80-window-night-backdrop");
            // Real Candidate #1232 accepted this response for the night opening. Recovery preserves
            // it without any of the later texture/fill/fold/source-hide appearance stack.
            set4(material, "baseColorFactor", 0.188f, 0.128f, 0.084f, 1.0f);
            set1(material, "metallicFactor", 0.0f);
            set1(material, "roughnessFactor", 0.96f);
            set1(material, "reflectance", 0.22f);
            set3(material, "emissiveFactor", 0.006f, 0.010f, 0.018f);
            set1(material, "emissiveStrength", 1.0f);

            leftVertices = createVertices(engine, LEFT_HALF_WIDTH, HALF_HEIGHT);
            rightVertices = createVertices(engine, RIGHT_HALF_WIDTH, HALF_HEIGHT);
            indices = createIndices(engine);

            entities[0] = createPlane(engine, material, leftVertices, indices,
                    LEFT_HALF_WIDTH, HALF_HEIGHT);
            place(transforms, entities[0], roomRootTransform,
                    LEFT_CENTER_X, CENTER_Y, CENTER_Z);
            scene.addEntity(entities[0]);
            sceneAdded[0] = true;

            entities[1] = createPlane(engine, material, rightVertices, indices,
                    RIGHT_HALF_WIDTH, HALF_HEIGHT);
            place(transforms, entities[1], roomRootTransform,
                    RIGHT_CENTER_X, CENTER_Y, CENTER_Z);
            scene.addEntity(entities[1]);
            sceneAdded[1] = true;

            State state = new State(scene, entities, material, leftVertices, rightVertices, indices);
            synchronized (STATES) { STATES.put(view, state); }
            Celine3DDiagnostics.record(view.getContext(), "ROOM-148",
                    "Recovery-Fenster mit neutralem Key/Fill aktiv",
                    "outer=" + LEFT_EDGE_X + ".." + RIGHT_EDGE_X
                            + " · gap=" + GAP_LEFT_X + ".." + GAP_RIGHT_X
                            + " center=" + GAP_CENTER_X + " width=" + (GAP_HALF_WIDTH * 2f)
                            + " · key=" + RECOVERY_KEY_LUX + "lux"
                            + " · indirect=" + RECOVERY_INDIRECT_LUX + "lux"
                            + " · experimentalWindowTexture/fills/sourceHide=false"
                            + " · ceiling/bed/practical overrides=false"
                            + " · source GLB/camera/furniture/Celine unchanged");
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
            if (rightVertices != null) try { engine.destroyVertexBuffer(rightVertices); } catch (Throwable ignored) {}
            if (leftVertices != null) try { engine.destroyVertexBuffer(leftVertices); } catch (Throwable ignored) {}
            if (material != null) try { engine.destroyMaterialInstance(material); } catch (Throwable ignored) {}
            throw error;
        }
    }

    private static void applyRecoveryNeutralLighting(Celine3DView view, Engine engine) throws Exception {
        Object rawLightEntity = field(view, "lightEntity");
        if (!(rawLightEntity instanceof Integer)) {
            throw new IllegalStateException("recovery key light entity fehlt");
        }
        int lightEntity = (Integer) rawLightEntity;
        IndirectLight indirect = (IndirectLight) field(view, "indirectLight");
        if (lightEntity == 0 || indirect == null) {
            throw new IllegalStateException("recovery key/indirect light fehlt");
        }
        LightManager lights = engine.getLightManager();
        int light = lights.getInstance(lightEntity);
        if (light == 0) throw new IllegalStateException("recovery key light instance fehlt");
        lights.setColor(light, RECOVERY_KEY_RED, RECOVERY_KEY_GREEN, RECOVERY_KEY_BLUE);
        lights.setIntensity(light, RECOVERY_KEY_LUX);
        lights.setShadowCaster(light, true);
        indirect.setIntensity(RECOVERY_INDIRECT_LUX);
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
        for (int entity : state.entities) {
            try { state.scene.remove(entity); } catch (Throwable ignored) {}
            try { engine.destroyEntity(entity); } catch (Throwable ignored) {}
            try { EntityManager.get().destroy(entity); } catch (Throwable ignored) {}
        }
        try { engine.destroyIndexBuffer(state.indices); } catch (Throwable ignored) {}
        try { engine.destroyVertexBuffer(state.rightVertices); } catch (Throwable ignored) {}
        try { engine.destroyVertexBuffer(state.leftVertices); } catch (Throwable ignored) {}
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
        final VertexBuffer leftVertices;
        final VertexBuffer rightVertices;
        final IndexBuffer indices;

        State(Scene scene, int[] entities, MaterialInstance material,
              VertexBuffer leftVertices, VertexBuffer rightVertices, IndexBuffer indices) {
            this.scene = scene;
            this.entities = entities;
            this.material = material;
            this.leftVertices = leftVertices;
            this.rightVertices = rightVertices;
            this.indices = indices;
        }
    }
}
