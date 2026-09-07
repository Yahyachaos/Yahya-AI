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
 * Bounded derived central sheer coverage for the v80 reference window.
 *
 * Proof #69 confirms the broad side masses are a useful improvement, but the middle still reads as
 * an exposed dark opening with ragged source strips. Refernzbild.png instead shows two smooth light
 * sheer panels over the night window. Keep the accepted night backing and side fills unchanged and add
 * only two warm-cream central panels behind the immutable source drape detail, preserving visible dark
 * night depth between and around them. No Celine, camera, anchors, furniture transforms or source GLB
 * bytes change.
 *
 * Witness #1343 isolates the remaining sheer error after the outer baseColorMap strategy was rejected:
 * the broad sheers are live material owners, but each is still a single planar four-vertex board.
 * Historical #1217 -> #1218 evidence proves the independent fold-geometry owner changes the real CALL
 * raster, while old Proof #73 already rejected many dark overlay strips as regular bars. Therefore keep
 * the accepted sheer color/envelope and replace only the broad base surface with one very shallow smooth
 * pleated mesh. The pleat depth stays within 6 mm around the accepted Z center, and the tangent frame
 * follows the low-amplitude wave so normal lighting can produce continuous fabric variation without
 * another texture-map experiment or a stack of hard stripe overlays.
 */
final class CelineRoomWindowSheerFillV80 {
    private static final float CENTER_Y = 1.20f;
    private static final float CENTER_Z = -2.730f;
    // Real Candidate #1211 measures the old sheers at about x=344..396 and x=420..473 on the
    // canonical 1016px CALL stage. Refernzbild.png instead places the two light sheer masses around
    // x=272..355 and x=470..536 with a dark central opening between them. The existing sheers sit
    // 5 mm in front of the side-curtain fill, so widening/repositioning only this layer can correct
    // the visible internal partition while preserving the already-close outer window envelope.
    private static final float HALF_WIDTH = 0.225f;
    private static final float HALF_HEIGHT = 1.06f;
    private static final float LEFT_CENTER_X = -1.175f;
    private static final float RIGHT_CENTER_X = -0.035f;

    // Bounded smooth base-sheer pleat from CELINE_ROOM_WINDOW_SHEER_PLEAT_WITNESS_1343.json.
    // Two waves across each 45 cm panel give broad fabric folds rather than the rejected narrow bars.
    // Height-dependent amplitude/phase prevents a perfectly repeated extrusion while keeping the exact
    // X/Y envelope and accepted center Z. 17x9 stays tiny compared with the immutable source assets.
    private static final int PLEAT_COLUMNS = 17;
    private static final int PLEAT_ROWS = 9;
    private static final float PLEAT_CYCLES = 2.0f;
    private static final float PLEAT_DEPTH = 0.006f;

    private static final WeakHashMap<Celine3DView, State> STATES = new WeakHashMap<>();

    private CelineRoomWindowSheerFillV80() {}

    static void apply(Celine3DView view, FilamentAsset asset, Engine engine) throws Exception {
        synchronized (STATES) {
            if (STATES.containsKey(view)) return;
        }

        int wallEntity = asset.getFirstEntityByName("room_back_wall");
        if (wallEntity == 0) throw new IllegalStateException("sheer fill: back wall fehlt");
        RenderableManager renderables = engine.getRenderableManager();
        int wallRenderable = renderables.getInstance(wallEntity);
        if (wallRenderable == 0 || renderables.getPrimitiveCount(wallRenderable) < 1) {
            throw new IllegalStateException("sheer fill: back wall material fehlt");
        }
        MaterialInstance source = renderables.getMaterialInstanceAt(wallRenderable, 0);
        if (source == null) throw new IllegalStateException("sheer fill: source material null");

        Scene scene = (Scene) field(view, "scene");
        TransformManager transforms = engine.getTransformManager();
        int roomRootTransform = transforms.getInstance(asset.getRoot());
        if (roomRootTransform == 0) throw new IllegalStateException("sheer fill: room root transform fehlt");

        MaterialInstance material = null;
        VertexBuffer vertices = null;
        IndexBuffer indices = null;
        int[] entities = new int[]{0, 0};
        boolean[] sceneAdded = new boolean[]{false, false};
        try {
            material = MaterialInstance.duplicate(source, "v80-window-sheer-fill");
            // Real Candidate #1214 verifies the corrected partition but renders the broad derived
            // sheers around RGB 115/104/91. The reference reads distinctly warmer through these
            // panels. Keep geometry fixed and apply one bounded material-only move toward warm cream.
            // The accepted #1216/#1336 factor and resulting ~115/90/68 center stay unchanged here.
            set4(material, "baseColorFactor", 0.78f, 0.62f, 0.48f, 1.0f);
            set1(material, "metallicFactor", 0.0f);
            set1(material, "roughnessFactor", 0.96f);
            set1(material, "reflectance", 0.30f);
            set3(material, "emissiveFactor", 0.0f, 0.0f, 0.0f);
            set1(material, "emissiveStrength", 0.0f);

            vertices = createPleatedVertices(engine);
            indices = createPleatedIndices(engine);
            float[] centers = new float[]{LEFT_CENTER_X, RIGHT_CENTER_X};
            for (int i = 0; i < entities.length; i++) {
                int entity = EntityManager.get().create();
                entities[i] = entity;
                new RenderableManager.Builder(1)
                        .boundingBox(new Box(0f, 0f, 0f,
                                HALF_WIDTH, HALF_HEIGHT, PLEAT_DEPTH + 0.004f))
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
            Celine3DDiagnostics.record(view.getContext(), "ROOM-146",
                    "Referenzkalibrierte zentrale Gardinen-Füllflächen mit weicher Basisfalte aktiv",
                    "left=" + LEFT_CENTER_X + " right=" + RIGHT_CENTER_X
                            + " y=" + CENTER_Y + " z=" + CENTER_Z
                            + " panel=" + (HALF_WIDTH * 2f) + "x" + (HALF_HEIGHT * 2f)
                            + " · pleatGrid=" + PLEAT_COLUMNS + "x" + PLEAT_ROWS
                            + " cycles=" + PLEAT_CYCLES + " depth=" + PLEAT_DEPTH
                            + " · accepted ~115/90/68 factor preserved"
                            + " · no baseColorMap retry / no hard overlay-bar expansion"
                            + " · outer window/source/Celine/camera/anchors/lamp unchanged");
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

    private static VertexBuffer createPleatedVertices(Engine engine) {
        final int stride = 13 * 4;
        final int vertexCount = PLEAT_COLUMNS * PLEAT_ROWS;
        FloatBuffer data = ByteBuffer.allocateDirect(vertexCount * stride)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        FloatBuffer uv1 = ByteBuffer.allocateDirect(vertexCount * 2 * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();

        final float width = HALF_WIDTH * 2.0f;
        final float wavePerMeter = (float) (2.0 * Math.PI * PLEAT_CYCLES / width);
        for (int row = 0; row < PLEAT_ROWS; row++) {
            float v = row / (float) (PLEAT_ROWS - 1);
            float y = -HALF_HEIGHT + (2.0f * HALF_HEIGHT * v);
            float heightEnvelope = 0.82f
                    + 0.18f * (float) Math.cos(Math.PI * (v - 0.35f));
            float amplitude = PLEAT_DEPTH * heightEnvelope;
            float phase = 0.22f * (float) Math.sin(Math.PI * (v - 0.5f));

            for (int column = 0; column < PLEAT_COLUMNS; column++) {
                float u = column / (float) (PLEAT_COLUMNS - 1);
                float x = -HALF_WIDTH + (width * u);
                float angle = (float) (2.0 * Math.PI * PLEAT_CYCLES * u) + phase;
                float z = amplitude * (float) Math.cos(angle);

                // For surface P(x,y)=(x,y,z(x,y)), approximate the front normal with the dominant
                // x slope. Height-dependent amplitude already changes that slope down the panel.
                float dzdx = -amplitude * wavePerMeter * (float) Math.sin(angle);
                float nx = -dzdx;
                float nz = 1.0f;
                float invLength = 1.0f / (float) Math.sqrt(nx * nx + nz * nz);
                nx *= invLength;
                nz *= invLength;
                float normalAngleY = (float) Math.atan2(nx, nz);
                float qy = (float) Math.sin(normalAngleY * 0.5f);
                float qw = (float) Math.cos(normalAngleY * 0.5f);

                data.put(x).put(y).put(z);
                data.put(0.0f).put(qy).put(0.0f).put(qw);
                data.put(1.0f).put(1.0f).put(1.0f).put(1.0f);
                data.put(u).put(v);
                uv1.put(u).put(v);
            }
        }
        data.flip();
        uv1.flip();

        VertexBuffer vb = new VertexBuffer.Builder()
                .vertexCount(vertexCount)
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
        vb.setBufferAt(engine, 0, data);
        vb.setBufferAt(engine, 1, uv1);
        return vb;
    }

    private static IndexBuffer createPleatedIndices(Engine engine) {
        final int quadCount = (PLEAT_COLUMNS - 1) * (PLEAT_ROWS - 1);
        final int indexCount = quadCount * 6;
        ShortBuffer data = ByteBuffer.allocateDirect(indexCount * 2)
                .order(ByteOrder.nativeOrder()).asShortBuffer();
        for (int row = 0; row < PLEAT_ROWS - 1; row++) {
            for (int column = 0; column < PLEAT_COLUMNS - 1; column++) {
                int topLeft = row * PLEAT_COLUMNS + column;
                int topRight = topLeft + 1;
                int bottomLeft = topLeft + PLEAT_COLUMNS;
                int bottomRight = bottomLeft + 1;
                data.put((short) topLeft).put((short) topRight).put((short) bottomRight);
                data.put((short) topLeft).put((short) bottomRight).put((short) bottomLeft);
            }
        }
        data.flip();
        IndexBuffer ib = new IndexBuffer.Builder()
                .indexCount(indexCount)
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
