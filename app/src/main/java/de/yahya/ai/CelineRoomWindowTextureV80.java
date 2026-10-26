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
 * v80 bounded derived-material owner for the single room_window_drapes primitive.
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
        // Android #343 proved that the optimized window partition has no entity
        // literally named "room_window_drapes", although the partition renders.
        // Resolve its sole actual renderable rather than silently skipping the
        // correction. Fail closed on ambiguous/multi-primitive GLB structure.
        // Genuine Android #345: the optimized drape partition contains exactly TWO
        // renderable entities (ROOM-150: renderable count=2), not one. The old
        // singleton gate kept this correction completely disabled in production.
        // Bind the UV-preserving palette only to primitive-0 material instances
        // that actually expose baseColorMap; never recolor unrelated partitions.
        RenderableManager manager = engine.getRenderableManager();
        java.util.ArrayList<MaterialInstance> drapeMaterials = new java.util.ArrayList<>();
        int renderableCount = 0;
        for (int entity : asset.getEntities()) {
            int candidate = manager.getInstance(entity);
            if (candidate == 0) continue;
            renderableCount++;
            if (manager.getPrimitiveCount(candidate) != 1) {
                throw new IllegalStateException("window primitive count="
                        + manager.getPrimitiveCount(candidate));
            }
            MaterialInstance candidateMaterial = manager.getMaterialInstanceAt(candidate, 0);
            if (candidateMaterial != null
                    && candidateMaterial.getMaterial().hasParameter("baseColorMap")) {
                drapeMaterials.add(candidateMaterial);
            }
        }
        if (renderableCount != 2 || drapeMaterials.isEmpty()) {
            throw new IllegalStateException("window partition structure renderables="
                    + renderableCount + " baseColorMapMaterials=" + drapeMaterials.size());
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
            for (MaterialInstance drapeMaterial : drapeMaterials) {
                drapeMaterial.setParameter("baseColorMap", texture, sampler);
                drapeMaterial.setParameter("baseColorFactor", Colors.RgbaType.LINEAR,
                        1f, 1f, 1f, 1f);
            }
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
                            + " contrastBalancedFabricAtlas=true"
                            + " derivedPlanes=false opaqueBackplanesChanged=false"
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

            // C137: the C136 palette compressed the original white/taupe atlas
            // into nearly one pale beige: the real #346 CALL still rendered the
            // opaque source-fold regions much lighter than the warm brown outer
            // curtains in Refernzbild.png. Preserve near-white sheer highlights
            // while restoring distinctly darker warm fabric midtones. This is
            // intentionally atlas-color ONLY; no geometry, alpha, GLASS, opening,
            // night-depth/backplane, source-GLB or other room material changes.
            float target = luminance < 0.78f
                    ? 0.20f + 0.56f * luminance
                    : 0.6368f + 1.10636f * (luminance - 0.78f);
            float chromaScale = 0.32f;
            float nr = clamp01(target + (r - luminance) * chromaScale + 0.035f);
            float ng = clamp01(target + (g - luminance) * chromaScale + 0.010f);
            float nb = clamp01(target + (b - luminance) * chromaScale - 0.035f);
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
