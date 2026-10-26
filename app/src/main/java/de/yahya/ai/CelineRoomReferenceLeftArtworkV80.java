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
import java.util.ArrayList;
import java.util.WeakHashMap;

/**
 * Two small, source-reference-derived frames missing from the source-fidelity Room.
 *
 * The two visible botanical prints are measured together at screen
 * x=.103..155, y=.150..271 on Refernzbild.png. The existing fixed CALL camera
 * projects these wall-mounted panels to that envelope: the precise position,
 * visual appearance and occlusions still require authentic Android A/B review.
 *
 * Only the existing, packaged 1402x1122 canonical reference alias supplies pixels.
 * No image generation, furniture-GLB modification, glass/opening, opaque backplane,
 * camera, Celine, Core, or shared build/updater change occurs here.
 */
final class CelineRoomReferenceLeftArtworkV80 {
    private static final String REFERENCE_ASSET = "textures/room_reference_wall_art_source.png";
    private static final int REFERENCE_WIDTH = 1402;
    private static final int REFERENCE_HEIGHT = 1122;
    // Pre-common-180-degree shell local +X is the physical left interior wall.
    private static final float WALL_LOCAL_X = 2.185f;
    private static final float ROOT_ART_Y = 1.690f;

    private static final Frame[] FRAMES = {
            // Fixed exact-raster crops of the two botanical prints. Preserve the
            // original crop as-is: no synthesis, replacement or invented picture.
            new Frame("near", 143, 164, 45, 105, .850f, .170f, .240f),
            new Frame("far", 181, 177, 43, 108, 1.280f, .170f, .240f)
    };
    private static final WeakHashMap<Celine3DView, State> STATES = new WeakHashMap<>();

    private CelineRoomReferenceLeftArtworkV80() {}

    static void apply(Celine3DView view, FilamentAsset shell, Engine engine, Scene scene)
            throws Exception {
        if (view == null || shell == null || engine == null || scene == null) return;
        synchronized (STATES) {
            if (STATES.containsKey(view)) return;
        }
        int donorEntity = shell.getFirstEntityByName("room_shell_right");
        if (donorEntity == 0) throw new IllegalStateException("left art: wall donor absent");
        RenderableManager renderables = engine.getRenderableManager();
        int donorInstance = renderables.getInstance(donorEntity);
        if (donorInstance == 0 || renderables.getPrimitiveCount(donorInstance) < 1) {
            throw new IllegalStateException("left art: wall donor not renderable");
        }
        MaterialInstance donor = renderables.getMaterialInstanceAt(donorInstance, 0);
        if (donor == null) throw new IllegalStateException("left art: missing donor material");
        TransformManager transforms = engine.getTransformManager();
        int parent = transforms.getInstance(shell.getRoot());
        if (parent == 0) throw new IllegalStateException("left art: room root missing");

        Bitmap reference;
        try (InputStream input = view.getContext().getAssets().open(REFERENCE_ASSET)) {
            reference = BitmapFactory.decodeStream(input);
        }
        if (reference == null || reference.getWidth() != REFERENCE_WIDTH
                || reference.getHeight() != REFERENCE_HEIGHT) {
            if (reference != null) reference.recycle();
            throw new IllegalStateException("left art: canonical 1402x1122 reference absent");
        }

        State state = new State(engine, scene);
        try {
            for (Frame frame : FRAMES) {
                state.parts.add(makeFrame(reference, frame, parent, transforms, renderables,
                        donor, engine, scene));
            }
            synchronized (STATES) { STATES.put(view, state); }
            Celine3DDiagnostics.record(view.getContext(), "ROOM-155",
                    "Zwei echte Referenz-Wandbilder links sichtbar",
                    "source=canonical-1402x1122 croppedOnly=true frames=2"
                            + " target=x.103..155/y.150..271"
                            + " wallLocalX=" + WALL_LOCAL_X
                            + " localZ=.850,1.280 halfZ=.170 halfY=.240"
                            + " sourceFurniture12Unchanged=true roomShellUnchanged=true"
                            + " glassBackplanesAndCelineCameraCoreUnchanged=true");
        } catch (Throwable error) {
            state.destroy();
            throw error;
        } finally {
            reference.recycle();
        }
    }

    private static Part makeFrame(Bitmap reference, Frame frame, int parent,
                                  TransformManager transforms,
                                  RenderableManager renderables, MaterialInstance donor,
                                  Engine engine, Scene scene) throws Exception {
        Texture texture = null;
        MaterialInstance material = null;
        VertexBuffer vertices = null;
        IndexBuffer indices = null;
        int entity = 0;
        boolean added = false;
        Bitmap cropped = Bitmap.createBitmap(reference, frame.cropX, frame.cropY,
                frame.cropWidth, frame.cropHeight);
        try {
            texture = new Texture.Builder().width(frame.cropWidth).height(frame.cropHeight)
                    .levels(1).sampler(Texture.Sampler.SAMPLER_2D)
                    .format(Texture.InternalFormat.SRGB8_A8).build(engine);
            TextureHelper.setBitmap(engine, texture, 0, cropped);
            engine.flushAndWait();

            material = MaterialInstance.duplicate(donor,
                    "room-canonical-left-botanical-" + frame.name);
            if (!material.getMaterial().hasParameter("baseColorMap")
                    || !material.getMaterial().hasParameter("baseColorIndex")) {
                throw new IllegalStateException("left art: source wall texture parameters absent");
            }
            TextureSampler sampler = new TextureSampler(TextureSampler.MinFilter.LINEAR,
                    TextureSampler.MagFilter.LINEAR, TextureSampler.WrapMode.CLAMP_TO_EDGE);
            material.setParameter("baseColorMap", texture, sampler);
            material.setParameter("baseColorIndex", 0);
            if (material.getMaterial().hasParameter("baseColorFactor")) {
                material.setParameter("baseColorFactor", Colors.RgbaType.LINEAR,
                        1f, 1f, 1f, 1f);
            }
            if (material.getMaterial().hasParameter("metallicFactor")) {
                material.setParameter("metallicFactor", 0f);
            }
            if (material.getMaterial().hasParameter("roughnessFactor")) {
                material.setParameter("roughnessFactor", .92f);
            }

            vertices = makeVertices(engine, frame.halfHeight, frame.halfZ);
            indices = makeIndices(engine);
            entity = EntityManager.get().create();
            new RenderableManager.Builder(1)
                    .boundingBox(new Box(0f, 0f, 0f, .025f,
                            frame.halfHeight, frame.halfZ))
                    .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertices, indices)
                    .material(0, material)
                    .castShadows(false).receiveShadows(true).culling(false)
                    .build(engine, entity);
            int renderable = renderables.getInstance(entity);
            if (renderable == 0) throw new IllegalStateException("left art: renderable missing");
            renderables.setLightChannel(renderable, 0, false);
            renderables.setLightChannel(renderable, 1, true);

            float[] local = new float[16];
            Matrix.setIdentityM(local, 0);
            Matrix.translateM(local, 0, WALL_LOCAL_X, ROOT_ART_Y, frame.centerZ);
            transforms.create(entity, parent, local);
            scene.addEntity(entity);
            added = true;
            return new Part(entity, material, texture, vertices, indices);
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
        } finally {
            cropped.recycle();
        }
    }

    private static VertexBuffer makeVertices(Engine engine, float h, float w) {
        final int stride = 15 * 4;
        // -90 degrees about local Y: quad normal faces the room, opposite
        // the right-wall artwork (+90 degrees).
        final float q = 0.70710677f;
        float[] data = {
            0f,-h, w,  0f,-q,0f,q, 1f,1f,1f,1f,  0f,1f, 0f,1f,
            0f,-h,-w,  0f,-q,0f,q, 1f,1f,1f,1f,  1f,1f, 1f,1f,
            0f, h,-w,  0f,-q,0f,q, 1f,1f,1f,1f,  1f,0f, 1f,0f,
            0f, h, w,  0f,-q,0f,q, 1f,1f,1f,1f,  0f,0f, 0f,0f
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

    private static IndexBuffer makeIndices(Engine engine) {
        ShortBuffer buffer = ByteBuffer.allocateDirect(6 * 2)
                .order(ByteOrder.nativeOrder()).asShortBuffer();
        buffer.put(new short[]{0, 1, 2, 0, 2, 3}).flip();
        IndexBuffer result = new IndexBuffer.Builder().indexCount(6)
                .bufferType(IndexBuffer.Builder.IndexType.USHORT).build(engine);
        result.setBuffer(engine, buffer);
        return result;
    }

    static void release(Celine3DView view, Engine engine) {
        State state;
        synchronized (STATES) { state = STATES.remove(view); }
        if (state != null) state.destroy();
    }

    private static final class Frame {
        final String name;
        final int cropX, cropY, cropWidth, cropHeight;
        final float centerZ, halfZ, halfHeight;
        Frame(String name, int cropX, int cropY, int cropWidth, int cropHeight,
              float centerZ, float halfZ, float halfHeight) {
            this.name = name;
            this.cropX = cropX;
            this.cropY = cropY;
            this.cropWidth = cropWidth;
            this.cropHeight = cropHeight;
            this.centerZ = centerZ;
            this.halfZ = halfZ;
            this.halfHeight = halfHeight;
        }
    }

    private static final class Part {
        final int entity;
        final MaterialInstance material;
        final Texture texture;
        final VertexBuffer vertices;
        final IndexBuffer indices;
        Part(int entity, MaterialInstance material, Texture texture,
             VertexBuffer vertices, IndexBuffer indices) {
            this.entity = entity;
            this.material = material;
            this.texture = texture;
            this.vertices = vertices;
            this.indices = indices;
        }
    }

    private static final class State {
        final Engine engine;
        final Scene scene;
        final ArrayList<Part> parts = new ArrayList<>();
        State(Engine engine, Scene scene) {
            this.engine = engine;
            this.scene = scene;
        }
        void destroy() {
            for (int i = parts.size() - 1; i >= 0; i--) {
                Part p = parts.get(i);
                try { scene.removeEntity(p.entity); } catch (Throwable ignored) {}
                try { engine.destroyEntity(p.entity); } catch (Throwable ignored) {}
                try { EntityManager.get().destroy(p.entity); } catch (Throwable ignored) {}
                try { engine.destroyIndexBuffer(p.indices); } catch (Throwable ignored) {}
                try { engine.destroyVertexBuffer(p.vertices); } catch (Throwable ignored) {}
                try { engine.destroyMaterialInstance(p.material); } catch (Throwable ignored) {}
                try { engine.destroyTexture(p.texture); } catch (Throwable ignored) {}
            }
            parts.clear();
        }
    }
}
