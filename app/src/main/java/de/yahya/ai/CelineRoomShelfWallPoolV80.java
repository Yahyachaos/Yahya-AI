package de.yahya.ai;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.opengl.Matrix;
import android.view.View;

import com.google.android.filament.Box;
import com.google.android.filament.Engine;
import com.google.android.filament.EntityManager;
import com.google.android.filament.IndexBuffer;
import com.google.android.filament.MaterialInstance;
import com.google.android.filament.RenderableManager;
import com.google.android.filament.Scene;
import com.google.android.filament.Texture;
import com.google.android.filament.TextureSampler;
import com.google.android.filament.TransformManager;
import com.google.android.filament.VertexBuffer;
import com.google.android.filament.android.TextureHelper;
import com.google.android.filament.gltfio.FilamentAsset;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Proof #1358-authorized localized material owner for the warm under-shelf back-wall pool.
 *
 * Two dynamic-light strategies are under STOP-LOSS and the broad back-wall material is already
 * accepted. This owner therefore overlays only the measured clean residual ROI with a tiny plane
 * carrying a smooth radial emissive map. It duplicates the final isolated back-wall material after
 * the window/post-pass has completed, so baseColorMap/PBR response stays identical at the boundary.
 * No global light, wall factor, room geometry, furniture TRS, camera, Celine or source GLB changes.
 */
final class CelineRoomShelfWallPoolV80 {
    private static final float CENTER_X = 1.176f;
    private static final float CENTER_Y = 1.63f;
    private static final float CENTER_Z = -2.055f;
    private static final float HALF_WIDTH = 0.18f;
    private static final float HALF_HEIGHT = 0.16f;
    private static final int MAP_SIZE = 64;
    private static final float EMISSIVE_RED = 0.05f;
    private static final float EMISSIVE_GREEN = 0.042f;
    private static final float EMISSIVE_BLUE = 0.02f;

    private static final WeakHashMap<Celine3DView, State> STATES = new WeakHashMap<>();

    private CelineRoomShelfWallPoolV80() {}

    static void apply(Celine3DView view, Engine engine) throws Exception {
        if (view == null || engine == null) return;
        synchronized (STATES) {
            if (STATES.containsKey(view)) return;
        }

        FilamentAsset roomAsset = currentRoomAsset(view);
        if (roomAsset == null) throw new IllegalStateException("shelf wall pool: room asset missing");
        Scene scene = (Scene) field(view, "scene");
        if (scene == null) throw new IllegalStateException("shelf wall pool: scene missing");
        TransformManager transforms = engine.getTransformManager();
        int roomRootTransform = transforms.getInstance(roomAsset.getRoot());
        if (roomRootTransform == 0) {
            throw new IllegalStateException("shelf wall pool: room root transform missing");
        }

        MaterialInstance donor = firstMaterial(roomAsset, engine, "room_back_wall");
        MaterialInstance material = null;
        Texture emissiveMap = null;
        VertexBuffer vertices = null;
        IndexBuffer indices = null;
        int entity = 0;
        boolean added = false;
        try {
            material = MaterialInstance.duplicate(donor, "v80-reference-shelf-wall-pool");
            if (!material.getMaterial().hasParameter("emissiveMap")) {
                throw new IllegalStateException("shelf wall pool: donor emissiveMap missing");
            }
            if (!material.getMaterial().hasParameter("emissiveFactor")) {
                throw new IllegalStateException("shelf wall pool: donor emissiveFactor missing");
            }
            emissiveMap = createRadialEmissiveTexture(engine);
            TextureSampler sampler = new TextureSampler(TextureSampler.MinFilter.LINEAR,
                    TextureSampler.MagFilter.LINEAR, TextureSampler.WrapMode.CLAMP_TO_EDGE);
            material.setParameter("emissiveMap", emissiveMap, sampler);
            material.setParameter("emissiveFactor", EMISSIVE_RED, EMISSIVE_GREEN, EMISSIVE_BLUE);
            if (material.getMaterial().hasParameter("emissiveStrength")) {
                material.setParameter("emissiveStrength", 1.0f);
            }

            vertices = createVertices(engine);
            indices = createIndices(engine);
            engine.flushAndWait();

            entity = EntityManager.get().create();
            new RenderableManager.Builder(1)
                    .boundingBox(new Box(0f, 0f, 0f, HALF_WIDTH, HALF_HEIGHT, 0.002f))
                    .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertices, indices)
                    .material(0, material)
                    .castShadows(false)
                    .receiveShadows(true)
                    .culling(false)
                    .build(engine, entity);

            float[] local = new float[16];
            Matrix.setIdentityM(local, 0);
            Matrix.translateM(local, 0, CENTER_X, CENTER_Y, CENTER_Z);
            transforms.create(entity, roomRootTransform, local);
            scene.addEntity(entity);
            added = true;

            State state = new State(view, engine, scene, entity, material, emissiveMap, vertices, indices);
            synchronized (STATES) { STATES.put(view, state); }
            view.addOnAttachStateChangeListener(state);
            Celine3DDiagnostics.record(view.getContext(), "ROOM-153",
                    "Lokaler Referenz-Wandpool aktiv",
                    "proof=1358 owner=material-plane center=" + CENTER_X + "," + CENTER_Y + "," + CENTER_Z
                            + " size=" + (HALF_WIDTH * 2f) + "x" + (HALF_HEIGHT * 2f)
                            + " emissive=" + EMISSIVE_RED + "," + EMISSIVE_GREEN + "," + EMISSIVE_BLUE
                            + " map=64x64 radial dynamicLight=false baseWallFactorChanged=false"
                            + " · geometry/furniture/camera/Celine/sourceGLBs unchanged");
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
            if (emissiveMap != null) try { engine.destroyTexture(emissiveMap); } catch (Throwable ignored) {}
            if (material != null) try { engine.destroyMaterialInstance(material); } catch (Throwable ignored) {}
            throw error;
        }
    }

    static void release(Celine3DView view) {
        State state;
        synchronized (STATES) { state = STATES.remove(view); }
        if (state != null) state.destroy();
    }

    private static MaterialInstance firstMaterial(
            FilamentAsset asset, Engine engine, String entityName) {
        int entity = asset.getFirstEntityByName(entityName);
        if (entity == 0) throw new IllegalStateException("shelf wall pool donor missing: " + entityName);
        RenderableManager renderables = engine.getRenderableManager();
        int renderable = renderables.getInstance(entity);
        if (renderable == 0 || renderables.getPrimitiveCount(renderable) < 1) {
            throw new IllegalStateException("shelf wall pool donor renderable missing: " + entityName);
        }
        MaterialInstance material = renderables.getMaterialInstanceAt(renderable, 0);
        if (material == null) throw new IllegalStateException("shelf wall pool donor material missing");
        return material;
    }

    private static Texture createRadialEmissiveTexture(Engine engine) {
        Bitmap bitmap = Bitmap.createBitmap(MAP_SIZE, MAP_SIZE, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < MAP_SIZE; y++) {
            float ny = ((y + 0.5f) / MAP_SIZE) * 2f - 1f;
            for (int x = 0; x < MAP_SIZE; x++) {
                float nx = ((x + 0.5f) / MAP_SIZE) * 2f - 1f;
                float radius = (float) Math.sqrt(nx * nx + ny * ny);
                float t = Math.max(0f, 1f - Math.min(1f, radius));
                float smooth = t * t * (3f - 2f * t);
                int value = Math.round(255f * smooth);
                bitmap.setPixel(x, y, Color.argb(255, value, value, value));
            }
        }
        Texture texture = new Texture.Builder()
                .width(MAP_SIZE)
                .height(MAP_SIZE)
                .levels(1)
                .sampler(Texture.Sampler.SAMPLER_2D)
                .format(Texture.InternalFormat.SRGB8_A8)
                .build(engine);
        try {
            TextureHelper.setBitmap(engine, texture, 0, bitmap);
            engine.flushAndWait();
            return texture;
        } catch (Throwable error) {
            try { engine.destroyTexture(texture); } catch (Throwable ignored) {}
            throw error;
        } finally {
            bitmap.recycle();
        }
    }

    private static VertexBuffer createVertices(Engine engine) {
        final int stride = 15 * 4;
        float[] data = {
                -HALF_WIDTH, -HALF_HEIGHT, 0f,  0f,0f,0f,1f,  1f,1f,1f,1f,  0f,0f, 0f,0f,
                 HALF_WIDTH, -HALF_HEIGHT, 0f,  0f,0f,0f,1f,  1f,1f,1f,1f,  1f,0f, 1f,0f,
                 HALF_WIDTH,  HALF_HEIGHT, 0f,  0f,0f,0f,1f,  1f,1f,1f,1f,  1f,1f, 1f,1f,
                -HALF_WIDTH,  HALF_HEIGHT, 0f,  0f,0f,0f,1f,  1f,1f,1f,1f,  0f,1f, 0f,1f,
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

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static final class State implements View.OnAttachStateChangeListener {
        final Celine3DView view;
        final Engine engine;
        final Scene scene;
        final int entity;
        final MaterialInstance material;
        final Texture emissiveMap;
        final VertexBuffer vertices;
        final IndexBuffer indices;
        boolean destroyed;

        State(Celine3DView view, Engine engine, Scene scene, int entity,
              MaterialInstance material, Texture emissiveMap,
              VertexBuffer vertices, IndexBuffer indices) {
            this.view = view;
            this.engine = engine;
            this.scene = scene;
            this.entity = entity;
            this.material = material;
            this.emissiveMap = emissiveMap;
            this.vertices = vertices;
            this.indices = indices;
        }

        @Override public void onViewAttachedToWindow(View v) {
        }

        @Override public void onViewDetachedFromWindow(View v) {
            CelineRoomShelfWallPoolV80.release(view);
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
            try { engine.destroyTexture(emissiveMap); } catch (Throwable ignored) {}
            try { engine.destroyMaterialInstance(material); } catch (Throwable ignored) {}
        }
    }
}
