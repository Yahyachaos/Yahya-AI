package de.yahya.ai;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import com.google.android.filament.Colors;
import com.google.android.filament.Engine;
import com.google.android.filament.MaterialInstance;
import com.google.android.filament.RenderableManager;
import com.google.android.filament.Texture;
import com.google.android.filament.TextureSampler;
import com.google.android.filament.android.TextureHelper;
import com.google.android.filament.gltfio.FilamentAsset;

import java.io.InputStream;
import java.util.WeakHashMap;

/**
 * v80 bounded derived-material owner for the original source drape primitive only.
 *
 * The immutable Fenstermitgardinen.glb and the optimized room GLB both carry the same high-contrast,
 * patchy white/taupe Meshy base-color atlas. Reference-room evidence shows that preserving that atlas
 * verbatim cannot produce the warm cream sheers, brown curtains and dark night separation required by
 * Refernzbild.png. This owner binds only a derived color-balanced copy that preserves the original UV
 * islands. Geometry, UVs, normal map, metallic/roughness map and immutable source bytes stay unchanged.
 */
final class CelineRoomWindowTextureV80 {
    private static final String TEXTURE_ASSET = "textures/room_window_drapes_reference.jpg";
    private static final WeakHashMap<Celine3DView, Texture> TEXTURES = new WeakHashMap<>();

    private CelineRoomWindowTextureV80() {}

    /**
     * C136 exact-room recovery: only rebalance the existing UV-mapped source fabric.
     * In particular do not resurrect the rejected backdrop, broad planes, altered
     * opening/GLASS interpretation, fold meshes or source-hiding side effects.
     */
    static void applySourceMaterialOnly(Celine3DView view, FilamentAsset asset,
                                        Engine engine) throws Exception {
        applyInternal(view, asset, engine, false);
    }

    static void apply(Celine3DView view, FilamentAsset asset, Engine engine) throws Exception {
        applyInternal(view, asset, engine, true);
    }

    private static void applyInternal(Celine3DView view, FilamentAsset asset,
                                      Engine engine, boolean includeLegacyLayers) throws Exception {
        // C139 source-proven identity from the exact APK partition GLB
        // sha256 c070b36a8d25da6ed5295b327c01d9aa215ac4d7bdab3035225c0689bb6b6ac5:
        // mesh_node.010 = 499998-triangle ORIGINAL drape mesh (material.010);
        // CELINE_WindowNightDepth_Background = independent two-triangle OPAQUE night plane.
        // The previous ROOM-150 correction recolored BOTH, accidentally replacing the
        // night plane's source atlas with the cream/brown drape atlas. Preserve its
        // original PBR and opacity; only the actual curtain gets the fabric palette.
        RenderableManager manager = engine.getRenderableManager();
        int drapeEntity = asset.getFirstEntityByName("mesh_node.010");
        int nightEntity = asset.getFirstEntityByName("CELINE_WindowNightDepth_Background");
        if (drapeEntity == 0 || nightEntity == 0 || drapeEntity == nightEntity) {
            throw new IllegalStateException("window source identities ambiguous: drape="
                    + drapeEntity + " night=" + nightEntity);
        }
        int drapeRenderable = manager.getInstance(drapeEntity);
        int nightRenderable = manager.getInstance(nightEntity);
        if (drapeRenderable == 0 || nightRenderable == 0
                || manager.getPrimitiveCount(drapeRenderable) != 1
                || manager.getPrimitiveCount(nightRenderable) != 1) {
            throw new IllegalStateException("window source renderables changed");
        }
        int sourceRenderableCount = 0;
        for (int entity : asset.getEntities()) {
            if (manager.getInstance(entity) != 0) sourceRenderableCount++;
        }
        if (sourceRenderableCount != 2) {
            throw new IllegalStateException("window source renderable count="
                    + sourceRenderableCount + " expected=2");
        }
        MaterialInstance drapeMaterial =
                manager.getMaterialInstanceAt(drapeRenderable, 0);
        MaterialInstance nightMaterial =
                manager.getMaterialInstanceAt(nightRenderable, 0);
        if (drapeMaterial == null || nightMaterial == null
                || drapeMaterial == nightMaterial
                || !drapeMaterial.getMaterial().hasParameter("baseColorMap")) {
            throw new IllegalStateException("window drape/night materials not independent");
        }

        synchronized (TEXTURES) {
            Texture previous = TEXTURES.remove(view);
            if (previous != null) {
                try { engine.destroyTexture(previous); } catch (Throwable ignored) {}
            }
        }

        Bitmap bitmap;
        try (InputStream input = view.getContext().getAssets().open(TEXTURE_ASSET)) {
            bitmap = BitmapFactory.decodeStream(input);
        }
        if (bitmap == null) throw new IllegalStateException("window reference texture decode fehlgeschlagen");
        if (bitmap.getConfig() == Bitmap.Config.HARDWARE) {
            Bitmap copy = bitmap.copy(Bitmap.Config.ARGB_8888, false);
            bitmap.recycle();
            bitmap = copy;
        }

        Bitmap balanced = balanceDrapeAtlas(bitmap);
        if (balanced != bitmap) {
            bitmap.recycle();
            bitmap = balanced;
        }

        Texture texture = new Texture.Builder()
                .width(bitmap.getWidth())
                .height(bitmap.getHeight())
                .levels(1)
                .sampler(Texture.Sampler.SAMPLER_2D)
                .format(Texture.InternalFormat.SRGB8_A8)
                .build(engine);
        try {
            TextureHelper.setBitmap(engine, texture, 0, bitmap);
            engine.flushAndWait();
            TextureSampler sampler = new TextureSampler(TextureSampler.MinFilter.LINEAR,
                    TextureSampler.MagFilter.LINEAR, TextureSampler.WrapMode.CLAMP_TO_EDGE);
            drapeMaterial.setParameter("baseColorMap", texture, sampler);
            drapeMaterial.setParameter("baseColorFactor", Colors.RgbaType.LINEAR,
                    1f, 1f, 1f, 1f);
            synchronized (TEXTURES) { TEXTURES.put(view, texture); }
        } catch (Throwable error) {
            try { engine.destroyTexture(texture); } catch (Throwable ignored) {}
            throw error;
        } finally {
            bitmap.recycle();
        }

        if (!includeLegacyLayers) {
            Celine3DDiagnostics.record(view.getContext(), "ROOM-150",
                    "Quellgardinen nur farblich harmonisiert",
                    "originalMeshVisible=true originalUVs=true"
                            + " contrastBalancedFabricAtlas=true sourceDrapeMesh=mesh_node.010"
                            + " originalNightDepthMaterial=untouched derivedPlanes=false opaqueBackplanesChanged=false"
                            + " glassWindowSemanticsChanged=false");
            return;
        }

        // Proof #72 confirms the sparse source mesh should stay hidden, but the clean derived coverage
        // is too flat. Preserve all accepted coverage and add only shallow fold facets in front.
        CelineRoomWindowBackdropV80.apply(view, asset, engine);
        CelineRoomWindowCurtainFillV80.apply(view, asset, engine);
        CelineRoomWindowSheerFillV80.apply(view, asset, engine);
        CelineRoomWindowFoldDetailV80.apply(view, asset, engine);
        CelineRoomWindowSourceVisibilityV80.hide(view, asset);
        CelineRoomWindowDerivedGroupV80.apply(view, engine);
    }

    /**
     * ROOM-156: The packaged 1024x1024 UV atlas is genuinely bimodal: ~33% brown
     * islands (source luminance below 0.50), ~67% light cream islands. The C137
     * single continuous gray-beige curve turned both original fabric domains
     * into cold/low-contrast cloth in actual C145 Android CALL and Window #182.
     *
     * Recover separate warm-brown outer cloth and cream sheer source domains
     * from the source atlas's OWN bimodal color segmentation, not screen-space
     * geometry, an invented window plane, a new GLASS material or global lights.
     * Smooth the narrow transition so JPEG antialiasing has no abrupt edge.
     * Retain local weave/image contrast, original alpha, UV coordinates, source
     * normal/roughness maps, and the independent OPAQUE night-depth material.
     */
    private static Bitmap balanceDrapeAtlas(Bitmap source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] pixels = new int[width * height];
        source.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int i = 0; i < pixels.length; i++) {
            int color = pixels[i];
            float r = Color.red(color) / 255f;
            float g = Color.green(color) / 255f;
            float b = Color.blue(color) / 255f;
            float luminance = 0.2126f * r + 0.7152f * g + 0.0722f * b;

            // The atlas contains two genuine source-fabric color families,
            // with a sparse 0.45..0.67 luminance valley between them.
            float sheer = clamp01((luminance - 0.45f) / 0.22f);
            sheer = sheer * sheer * (3f - 2f * sheer);
            float brownDetail = Math.max(-0.055f,
                    Math.min(0.055f, (luminance - 0.31f) * 0.25f));
            float creamDetail = Math.max(-0.035f,
                    Math.min(0.035f, (luminance - 0.74f) * 0.18f));
            float brownWeight = 1f - sheer;
            float nr = clamp01(brownWeight * (0.58f + brownDetail)
                    + sheer * (0.98f + creamDetail));
            float ng = clamp01(brownWeight * (0.40f + brownDetail)
                    + sheer * (0.85f + creamDetail));
            float nb = clamp01(brownWeight * (0.24f + brownDetail)
                    + sheer * (0.66f + creamDetail));
            pixels[i] = Color.argb(Color.alpha(color), Math.round(nr * 255f),
                    Math.round(ng * 255f), Math.round(nb * 255f));
        }
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        result.setPixels(pixels, 0, width, 0, 0, width, height);
        return result;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    static void release(Celine3DView view, Engine engine) {
        CelineRoomWindowDerivedGroupV80.release(view);
        CelineRoomWindowSourceVisibilityV80.release(view);
        CelineRoomWindowFoldDetailV80.release(view, engine);
        CelineRoomWindowSheerFillV80.release(view, engine);
        CelineRoomWindowCurtainFillV80.release(view, engine);
        CelineRoomWindowBackdropV80.release(view, engine);
        Texture texture;
        synchronized (TEXTURES) { texture = TEXTURES.remove(view); }
        if (texture != null) {
            try { engine.destroyTexture(texture); } catch (Throwable ignored) {}
        }
    }
}
