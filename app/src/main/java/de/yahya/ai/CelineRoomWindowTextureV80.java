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

    static void apply(Celine3DView view, FilamentAsset asset, Engine engine) throws Exception {
        int entity = asset.getFirstEntityByName("room_window_drapes");
        if (entity == 0) throw new IllegalStateException("window entity fehlt");
        RenderableManager manager = engine.getRenderableManager();
        int renderable = manager.getInstance(entity);
        if (renderable == 0 || manager.getPrimitiveCount(renderable) != 1) {
            throw new IllegalStateException("window renderable/primitive ungültig");
        }
        MaterialInstance material = manager.getMaterialInstanceAt(renderable, 0);
        if (material == null || !material.getMaterial().hasParameter("baseColorMap")) {
            throw new IllegalStateException("window baseColorMap fehlt");
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
            material.setParameter("baseColorMap", texture, sampler);
            material.setParameter("baseColorFactor", Colors.RgbaType.LINEAR, 1f, 1f, 1f, 1f);
            synchronized (TEXTURES) { TEXTURES.put(view, texture); }
        } catch (Throwable error) {
            try { engine.destroyTexture(texture); } catch (Throwable ignored) {}
            throw error;
        } finally {
            bitmap.recycle();
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

            float target = luminance < 0.5f
                    ? 0.50f + 0.20f * (luminance / 0.5f)
                    : 0.70f + 0.18f * ((luminance - 0.5f) / 0.5f);
            float chromaScale = 0.20f;
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
