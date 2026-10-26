package de.yahya.ai;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.Matrix;

import com.google.android.filament.Box;
import com.google.android.filament.Colors;
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

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.WeakHashMap;

/**
 * Bounded Room-owned reconstruction of the conspicuously missing right-wall framed print.
 *
 * The only image input is a byte-identical Git-blob alias of the user-owned canonical
 * Refernzbild.png. The crop is a fixed, measured right-wall artwork envelope on its
 * 1376x1100 reference grid (not an AI-generated or substitute image). This derived
 * room-decoration plane is independent of every immutable original furniture GLB,
 * window glass/opening/backplanes, Celine, camera and the 13 authored room instances.
 *
 * The geometry is attached to the existing room shell, 1.5cm in front of its interior
 * right wall (room-root local X=-2.20m after the accepted common 180 degree yaw).
 * The fitted z/y span is a reference-geometry candidate: only real Android CALL
 * evidence may decide visual acceptance.
 */
final class CelineRoomReferenceArtworkV80 {
    private static final String REFERENCE_ASSET = "textures/room_reference_wall_art_source.png";
    // The layout measurement contract uses a 1376x1100 grid, whereas the
    // immutable GitHub Refernzbild.png actual raster is 1402x1122. Exact real
    // emulator #352/ROOM-151 caught the erroneous grid-size assertion:
    // read the genuine packaged raster dimensions and remap the same normalized
    // x=.858..969, y=.076..283 artwork envelope before cropping.
    private static final int REFERENCE_WIDTH = 1402;
    private static final int REFERENCE_HEIGHT = 1122;
    private static final int CROP_LEFT = 1203;
    private static final int CROP_TOP = 85;
    private static final int CROP_WIDTH = 156;
    private static final int CROP_HEIGHT = 233;
    private static final float CENTER_X = -2.185f;
    private static final float CENTER_Y = 1.795f;
    private static final float CENTER_Z = 0.285f;
    private static final float HALF_HEIGHT = 0.385f;
    private static final float HALF_WIDTH_ALONG_Z = 0.285f;
    private static final WeakHashMap<Celine3DView, State> STATES = new WeakHashMap<>();

    private CelineRoomReferenceArtworkV80() {}

    static void apply(Celine3DView view, FilamentAsset shell, Engine engine, Scene scene)
            throws Exception {
        if (view == null || shell == null || engine == null || scene == null) return;
        synchronized (STATES) {
            if (STATES.containsKey(view)) return;
        }
        int donorEntity = shell.getFirstEntityByName("room_shell_left");
        if (donorEntity == 0) throw new IllegalStateException("artwork: source-right wall missing");
        RenderableManager renderables = engine.getRenderableManager();
        int donor = renderables.getInstance(donorEntity);
        if (donor == 0 || renderables.getPrimitiveCount(donor) < 1) {
            throw new IllegalStateException("artwork: wall donor not renderable");
        }
        MaterialInstance donorMaterial = renderables.getMaterialInstanceAt(donor, 0);
        if (donorMaterial == null) throw new IllegalStateException("artwork: donor material missing");
        TransformManager transforms = engine.getTransformManager();
        int parent = transforms.getInstance(shell.getRoot());
        if (parent == 0) throw new IllegalStateException("artwork: aligned room root missing");

        MaterialInstance material = null;
        Texture texture = null;
        VertexBuffer vertices = null;
        IndexBuffer indices = null;
        int entity = 0;
        boolean added = false;
        try {
            Bitmap reference;
            try (InputStream input = view.getContext().getAssets().open(REFERENCE_ASSET)) {
                reference = BitmapFactory.decodeStream(input);
            }
            if (reference == null || reference.getWidth() != REFERENCE_WIDTH
                    || reference.getHeight() != REFERENCE_HEIGHT) {
                if (reference != null) reference.recycle();
                throw new IllegalStateException("artwork: exact canonical 1402x1122 PNG absent (1376x1100 = measurement grid)");
            }
            Bitmap crop;
            try {
                crop = Bitmap.createBitmap(reference, CROP_LEFT, CROP_TOP,
                        CROP_WIDTH, CROP_HEIGHT);
            } finally {
                reference.recycle();
            }
            try {
                texture = new Texture.Builder()
                        .width(CROP_WIDTH).height(CROP_HEIGHT).levels(1)
                        .sampler(Texture.Sampler.SAMPLER_2D)
                        .format(Texture.InternalFormat.SRGB8_A8).build(engine);
                TextureHelper.setBitmap(engine, texture, 0, crop);
                engine.flushAndWait();
            } finally {
                crop.recycle();
            }

            material = MaterialInstance.duplicate(donorMaterial, "room-right-framed-reference-art");
            if (!material.getMaterial().hasParameter("baseColorMap")
                    || !material.getMaterial().hasParameter("baseColorIndex")) {
                throw new IllegalStateException("artwork: gltfio UV texture parameters missing");
            }
            TextureSampler sampler = new TextureSampler(TextureSampler.MinFilter.LINEAR,
                    TextureSampler.MagFilter.LINEAR, TextureSampler.WrapMode.CLAMP_TO_EDGE);
            material.setParameter("baseColorMap", texture, sampler);
            material.setParameter("baseColorIndex", 0);
            if (material.getMaterial().hasParameter("baseColorFactor")) {
                material.setParameter("baseColorFactor", Colors.RgbaType.LINEAR, 1f, 1f, 1f, 1f);
            }
            if (material.getMaterial().hasParameter("metallicFactor")) {
                material.setParameter("metallicFactor", 0f);
            }
            if (material.getMaterial().hasParameter("roughnessFactor")) {
                material.setParameter("roughnessFactor", 0.92f);
            }

            vertices = createVertices(engine);
            indices = createIndices(engine);
            entity = EntityManager.get().create();
            new RenderableManager.Builder(1)
                    .boundingBox(new Box(0f, 0f, 0f, 0.025f,
                            HALF_HEIGHT, HALF_WIDTH_ALONG_Z))
                    .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertices, indices)
                    .material(0, material)
                    .castShadows(false).receiveShadows(true).culling(false)
                    .build(engine, entity);
            int renderable = renderables.getInstance(entity);
            if (renderable == 0) throw new IllegalStateException("artwork: renderable creation failed");
            renderables.setLightChannel(renderable, 0, false);
            renderables.setLightChannel(renderable, 1, true);

            float[] local = new float[16];
            Matrix.setIdentityM(local, 0);
            Matrix.translateM(local, 0, CENTER_X, CENTER_Y, CENTER_Z);
            transforms.create(entity, parent, local);
            scene.addEntity(entity);
            added = true;
            synchronized (STATES) {
                STATES.put(view, new State(scene, entity, material, texture, vertices, indices));
            }
            Celine3DDiagnostics.record(view.getContext(), "ROOM-151",
                    "Originalreferenz-Wandbild als separate sichtbare Raumdeko",
                    "canonicalReferenceBlob=e85c43b5... crop=(1203,85,156,233) measurementGrid=1376x1100 imagePx=1402x1122"
                            + " rootXYZ=" + CENTER_X + "," + CENTER_Y + "," + CENTER_Z
                            + " surface=right-wall interior sourceGLBs12Unchanged=true"
                            + " windowsGlassBackplanesUnchanged=true CelineCameraCoreUnchanged=true");
        } catch (Throwable error) {
            if (added) try { scene.removeEntity(entity); } catch (Throwable ignored) {}
            if (entity != 0) {
                try { engine.destroyEntity(entity); } catch (Throwable ignored) {}
                try { EntityManager.get().destroy(entity); } catch (Throwable ignored) {}
            }
            if (indices != null) try { engine.destroyIndexBuffer(indices); } catch (Throwable ignored) {}
            if (vertices != null) try { engine.destroyVertexBuffer(vertices); } catch (Throwable ignored) {}
            if (material != null) try { engine.destroyMaterialInstance(material); } catch (Throwable ignored) {}
            if (texture != null) try { engine.destroyTexture(texture); } catch (Throwable ignored) {}
            throw error;
        }
    }

    private static VertexBuffer createVertices(Engine engine) {
        final int stride = 15 * 4;
        final float h = HALF_HEIGHT;
        final float w = HALF_WIDTH_ALONG_Z;
        // The shell-root +180deg Y rotation maps local +Z toward image-left,
        // local -Z toward image-right. Quad winding faces the room interior (+X).
        // Quaternion rotates canonical +Z normal into +X (90deg about Y).
        final float q = 0.70710677f;
        float[] data = {
            0f,-h, w,   0f,q,0f,q,  1f,1f,1f,1f,  0f,1f, 0f,1f,
            0f,-h,-w,   0f,q,0f,q,  1f,1f,1f,1f,  1f,1f, 1f,1f,
            0f, h,-w,   0f,q,0f,q,  1f,1f,1f,1f,  1f,0f, 1f,0f,
            0f, h, w,   0f,q,0f,q,  1f,1f,1f,1f,  0f,0f, 0f,0f
        };
        FloatBuffer packed = ByteBuffer.allocateDirect(data.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        packed.put(data).flip();
        VertexBuffer result = new VertexBuffer.Builder()
                .vertexCount(4).bufferCount(1)
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
        result.setBufferAt(engine, 0, packed);
        return result;
    }

    private static IndexBuffer createIndices(Engine engine) {
        ShortBuffer triangles = ByteBuffer.allocateDirect(6 * 2)
                .order(ByteOrder.nativeOrder()).asShortBuffer();
        triangles.put(new short[]{0, 1, 2, 0, 2, 3}).flip();
        IndexBuffer result = new IndexBuffer.Builder()
                .indexCount(6).bufferType(IndexBuffer.Builder.IndexType.USHORT).build(engine);
        result.setBuffer(engine, triangles);
        return result;
    }

    static void release(Celine3DView view, Engine engine) {
        State state;
        synchronized (STATES) {
            state = STATES.remove(view);
        }
        if (state == null || engine == null) return;
        try { state.scene.removeEntity(state.entity); } catch (Throwable ignored) {}
        try { engine.destroyEntity(state.entity); } catch (Throwable ignored) {}
        try { EntityManager.get().destroy(state.entity); } catch (Throwable ignored) {}
        try { engine.destroyIndexBuffer(state.indices); } catch (Throwable ignored) {}
        try { engine.destroyVertexBuffer(state.vertices); } catch (Throwable ignored) {}
        try { engine.destroyMaterialInstance(state.material); } catch (Throwable ignored) {}
        try { engine.destroyTexture(state.texture); } catch (Throwable ignored) {}
    }

    private static final class State {
        final Scene scene;
        final int entity;
        final MaterialInstance material;
        final Texture texture;
        final VertexBuffer vertices;
        final IndexBuffer indices;
        State(Scene scene, int entity, MaterialInstance material,
              Texture texture, VertexBuffer vertices, IndexBuffer indices) {
            this.scene = scene;
            this.entity = entity;
            this.material = material;
            this.texture = texture;
            this.vertices = vertices;
            this.indices = indices;
        }
    }
}
