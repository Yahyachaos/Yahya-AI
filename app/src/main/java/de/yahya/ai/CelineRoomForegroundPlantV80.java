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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Bounded derived reference geometry that is not available in the immutable furniture source set.
 *
 * The accepted foreground plant remains byte-for-byte source-independent. Real Candidate #1191 also
 * proves the primary dresser envelope is now effectively exact: x=0..191 px and y=341..584 px on
 * the 1016x813 CALL stage, i.e. y=0.4194..0.7183 versus target 0.420..0.718. Primary geometry is
 * therefore frozen. Real Candidate #1192 proves the first right-wall-art plane was raster-empty even
 * though its projected bbox solved the target. The bounded hypothesis is shell occlusion: pull only
 * this derived plane 18 cm farther inside the physical right wall and re-solve its Y/Z/size so its
 * target projection remains x=0.858..0.969 / y=0.076..0.283. Material/image detail stays deferred.
 */
final class CelineRoomForegroundPlantV80 {
    private static final float PLANT_YAW_DEG = -6.253965f;
    private static final float PLANT_PITCH_DEG = 8.344122f;
    private static final float PLANT_DEPTH_Z = 3.020114f;

    private static final float FOLIAGE_CENTER_X = 0.129526f;
    private static final float FOLIAGE_CENTER_Y = 0.939708f;
    private static final float FOLIAGE_WIDTH = 0.2650f;
    private static final float FOLIAGE_HEIGHT = 0.3038f;

    private static final float POT_CENTER_X = 0.138526f;
    private static final float POT_CENTER_Y = 0.827708f;
    private static final float POT_TOP_WIDTH = 0.1700f;
    private static final float POT_BOTTOM_WIDTH = 0.1320f;
    private static final float POT_HEIGHT = 0.2616f;

    // #1192 visibility correction. x=+2.00 m is safely room-side of the +2.20 m shell face. The
    // remaining values are an exact reprojection of the unchanged screen target on that parallel plane.
    private static final float RIGHT_ART_CENTER_X = 2.000000f;
    private static final float RIGHT_ART_CENTER_Y = 1.589331f;
    private static final float RIGHT_ART_CENTER_Z = -0.545906f;
    private static final float RIGHT_ART_WIDTH = 0.671234f;
    private static final float RIGHT_ART_HEIGHT = 0.695069f;
    private static final float RIGHT_ART_YAW_DEG = -90.0f;

    private static final WeakHashMap<Celine3DView, State> STATES = new WeakHashMap<>();

    private CelineRoomForegroundPlantV80() {}

    static void apply(Celine3DView view, Engine engine) throws Exception {
        if (view == null || engine == null) return;
        synchronized (STATES) {
            if (STATES.containsKey(view)) return;
        }

        FilamentAsset roomAsset = currentRoomAsset(view);
        if (roomAsset == null) throw new IllegalStateException("derived room geometry: room asset missing");
        Scene scene = (Scene) field(view, "scene");
        if (scene == null) throw new IllegalStateException("derived room geometry: scene missing");
        TransformManager transforms = engine.getTransformManager();
        int rootTransform = transforms.getInstance(roomAsset.getRoot());
        if (rootTransform == 0) throw new IllegalStateException("derived room geometry: root transform missing");

        MaterialInstance donor = firstMaterial(roomAsset, engine, "room_back_wall");
        State state = new State(view, engine, scene);
        try {
            state.parts.add(createFoliage(engine, scene, transforms, rootTransform, donor));
            state.parts.add(createPot(engine, scene, transforms, rootTransform, donor));
            state.parts.add(createStem(engine, scene, transforms, rootTransform, donor));
            state.parts.add(createRightWallArt(engine, scene, transforms, rootTransform, donor));
            synchronized (STATES) { STATES.put(view, state); }
            view.addOnAttachStateChangeListener(state);
            Celine3DDiagnostics.record(view.getContext(), "ROOM-145",
                    "Bounded Referenz-Zusatzgeometrie aktiv",
                    "plantTarget=x0.812..1.000/y0.645..0.946"
                            + " plantMeasured1189=x0.807..0.999/y0.641..0.945"
                            + " rightArtTarget=x0.858..0.969/y0.076..0.283"
                            + " rightArtLocal=" + RIGHT_ART_CENTER_X + ","
                            + RIGHT_ART_CENTER_Y + "," + RIGHT_ART_CENTER_Z
                            + " size=" + RIGHT_ART_WIDTH + "x" + RIGHT_ART_HEIGHT
                            + " wallAligned=true shellOcclusionRetry=true materialDetailDeferred=true"
                            + " sourceGLBsMutated=false camera/Celine/anchors unchanged");
        } catch (Throwable error) {
            state.destroy();
            throw error;
        }
    }

    static void release(Celine3DView view) {
        State state;
        synchronized (STATES) { state = STATES.remove(view); }
        if (state != null) state.destroy();
    }

    private static Part createFoliage(Engine engine, Scene scene, TransformManager transforms,
                                      int parent, MaterialInstance donor) {
        float[][] ring = {
                {-0.12f,-0.50f}, {-0.43f,-0.43f}, {-0.62f,-0.26f}, {-0.90f,-0.18f},
                {-0.66f, 0.02f}, {-0.82f, 0.22f}, {-0.48f, 0.18f}, {-0.58f, 0.52f},
                {-0.24f, 0.38f}, {-0.08f, 0.82f}, { 0.12f, 0.50f}, { 0.34f, 0.76f},
                { 0.40f, 0.38f}, { 0.72f, 0.52f}, { 0.60f, 0.18f}, { 0.92f, 0.08f},
                { 0.64f,-0.10f}, { 0.80f,-0.32f}, { 0.40f,-0.24f}, { 0.26f,-0.54f}
        };
        float[] xy = new float[2 + ring.length * 2];
        xy[0] = 0f;
        xy[1] = 0f;
        for (int i = 0; i < ring.length; i++) {
            xy[2 + i * 2] = ring[i][0] * FOLIAGE_WIDTH * 0.5f;
            xy[3 + i * 2] = ring[i][1] * FOLIAGE_HEIGHT * 0.5f;
        }
        short[] indices = new short[ring.length * 3];
        for (int i = 0; i < ring.length; i++) {
            int next = (i + 1) % ring.length;
            indices[i * 3] = 0;
            indices[i * 3 + 1] = (short) (1 + next);
            indices[i * 3 + 2] = (short) (1 + i);
        }
        MaterialInstance material = duplicateSolid(donor, "v80-reference-fg-plant-foliage",
                0.105f, 0.155f, 0.065f, 1.0f, 0.82f);
        return createPart(engine, scene, transforms, parent, xy, indices,
                FOLIAGE_CENTER_X, FOLIAGE_CENTER_Y, PLANT_DEPTH_Z,
                FOLIAGE_WIDTH, FOLIAGE_HEIGHT, PLANT_YAW_DEG, PLANT_PITCH_DEG, material);
    }

    private static Part createPot(Engine engine, Scene scene, TransformManager transforms,
                                  int parent, MaterialInstance donor) {
        float hwTop = POT_TOP_WIDTH * 0.5f;
        float hwBottom = POT_BOTTOM_WIDTH * 0.5f;
        float hh = POT_HEIGHT * 0.5f;
        float[] xy = {
                -hwTop, hh,
                 hwTop, hh,
                 hwBottom, -hh,
                -hwBottom, -hh
        };
        short[] indices = {0, 3, 2, 0, 2, 1};
        MaterialInstance material = duplicateSolid(donor, "v80-reference-fg-plant-pot",
                0.43f, 0.42f, 0.39f, 1.0f, 0.76f);
        return createPart(engine, scene, transforms, parent, xy, indices,
                POT_CENTER_X, POT_CENTER_Y, PLANT_DEPTH_Z + 0.002f,
                POT_TOP_WIDTH, POT_HEIGHT, PLANT_YAW_DEG, PLANT_PITCH_DEG, material);
    }

    private static Part createStem(Engine engine, Scene scene, TransformManager transforms,
                                   int parent, MaterialInstance donor) {
        float w = 0.024f;
        float h = 0.145f;
        float[] xy = {-w/2, h/2, w/2, h/2, w/2, -h/2, -w/2, -h/2};
        short[] indices = {0, 3, 2, 0, 2, 1};
        MaterialInstance material = duplicateSolid(donor, "v80-reference-fg-plant-stem",
                0.10f, 0.075f, 0.045f, 1.0f, 0.90f);
        return createPart(engine, scene, transforms, parent, xy, indices,
                0.134526f, 0.902708f, PLANT_DEPTH_Z + 0.004f,
                w, h, PLANT_YAW_DEG, PLANT_PITCH_DEG, material);
    }

    private static Part createRightWallArt(Engine engine, Scene scene, TransformManager transforms,
                                           int parent, MaterialInstance donor) {
        float hw = RIGHT_ART_WIDTH * 0.5f;
        float hh = RIGHT_ART_HEIGHT * 0.5f;
        float[] xy = {-hw, hh, hw, hh, hw, -hh, -hw, -hh};
        short[] indices = {0, 3, 2, 0, 2, 1};
        MaterialInstance material = duplicateSolid(donor, "v80-reference-right-wall-art",
                0.13f, 0.085f, 0.050f, 1.0f, 0.72f);
        return createPart(engine, scene, transforms, parent, xy, indices,
                RIGHT_ART_CENTER_X, RIGHT_ART_CENTER_Y, RIGHT_ART_CENTER_Z,
                RIGHT_ART_WIDTH, RIGHT_ART_HEIGHT, RIGHT_ART_YAW_DEG, 0.0f, material);
    }

    private static Part createPart(Engine engine, Scene scene, TransformManager transforms,
                                   int parent, float[] xy, short[] triangleIndices,
                                   float centerX, float centerY, float centerZ,
                                   float boundsWidth, float boundsHeight,
                                   float yawDeg, float pitchDeg,
                                   MaterialInstance material) {
        VertexBuffer vertices = null;
        IndexBuffer indices = null;
        int entity = 0;
        boolean added = false;
        try {
            vertices = createVertices(engine, xy);
            indices = createIndices(engine, triangleIndices);
            engine.flushAndWait();
            entity = EntityManager.get().create();
            new RenderableManager.Builder(1)
                    .boundingBox(new Box(0f, 0f, 0f,
                            Math.max(0.02f, boundsWidth * 0.55f),
                            Math.max(0.02f, boundsHeight * 0.55f), 0.02f))
                    .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertices, indices)
                    .material(0, material)
                    .castShadows(true)
                    .receiveShadows(true)
                    .culling(false)
                    .build(engine, entity);

            float[] local = new float[16];
            Matrix.setIdentityM(local, 0);
            Matrix.translateM(local, 0, centerX, centerY, centerZ);
            if (yawDeg != 0f) Matrix.rotateM(local, 0, yawDeg, 0f, 1f, 0f);
            if (pitchDeg != 0f) Matrix.rotateM(local, 0, pitchDeg, 1f, 0f, 0f);
            transforms.create(entity, parent, local);
            scene.addEntity(entity);
            added = true;
            return new Part(entity, material, vertices, indices);
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
            float u = 0.5f + x;
            float v = 0.5f + y;
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

    private static MaterialInstance duplicateSolid(MaterialInstance donor, String name,
                                                   float r, float g, float b, float a,
                                                   float roughness) {
        MaterialInstance material = MaterialInstance.duplicate(donor, name);
        try {
            if (material.getMaterial().hasParameter("baseColorFactor")) {
                material.setParameter("baseColorFactor", Colors.RgbaType.LINEAR, r, g, b, a);
            }
            if (material.getMaterial().hasParameter("metallicFactor")) {
                material.setParameter("metallicFactor", 0.0f);
            }
            if (material.getMaterial().hasParameter("roughnessFactor")) {
                material.setParameter("roughnessFactor", roughness);
            }
        } catch (Throwable ignored) {}
        return material;
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
        if (entity == 0) throw new IllegalStateException("derived geometry donor missing: " + entityName);
        RenderableManager renderables = engine.getRenderableManager();
        int renderable = renderables.getInstance(entity);
        if (renderable == 0 || renderables.getPrimitiveCount(renderable) < 1) {
            throw new IllegalStateException("derived geometry donor renderable missing: " + entityName);
        }
        MaterialInstance material = renderables.getMaterialInstanceAt(renderable, 0);
        if (material == null) throw new IllegalStateException("derived geometry donor material missing");
        return material;
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static final class Part {
        final int entity;
        final MaterialInstance material;
        final VertexBuffer vertices;
        final IndexBuffer indices;

        Part(int entity, MaterialInstance material, VertexBuffer vertices, IndexBuffer indices) {
            this.entity = entity;
            this.material = material;
            this.vertices = vertices;
            this.indices = indices;
        }
    }

    private static final class State implements View.OnAttachStateChangeListener {
        final Celine3DView view;
        final Engine engine;
        final Scene scene;
        final List<Part> parts = new ArrayList<>();
        boolean destroyed;

        State(Celine3DView view, Engine engine, Scene scene) {
            this.view = view;
            this.engine = engine;
            this.scene = scene;
        }

        @Override public void onViewAttachedToWindow(View v) {}

        @Override public void onViewDetachedFromWindow(View v) {
            CelineRoomForegroundPlantV80.release(view);
        }

        void destroy() {
            if (destroyed) return;
            destroyed = true;
            view.removeOnAttachStateChangeListener(this);
            for (Part part : parts) {
                try { scene.removeEntity(part.entity); } catch (Throwable ignored) {}
                try { engine.destroyEntity(part.entity); } catch (Throwable ignored) {}
                try { EntityManager.get().destroy(part.entity); } catch (Throwable ignored) {}
                try { engine.destroyIndexBuffer(part.indices); } catch (Throwable ignored) {}
                try { engine.destroyVertexBuffer(part.vertices); } catch (Throwable ignored) {}
                try { engine.destroyMaterialInstance(part.material); } catch (Throwable ignored) {}
            }
            parts.clear();
        }
    }
}
