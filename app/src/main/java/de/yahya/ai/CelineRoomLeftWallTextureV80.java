package de.yahya.ai;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.android.filament.Engine;
import com.google.android.filament.MaterialInstance;
import com.google.android.filament.RenderableManager;
import com.google.android.filament.Texture;
import com.google.android.filament.TextureSampler;
import com.google.android.filament.android.TextureHelper;
import com.google.android.filament.gltfio.FilamentAsset;

import java.util.WeakHashMap;

/**
 * One bounded spatial-material candidate for the clean upper-left reference-wall residual.
 *
 * Real Candidate #1364 measures the accepted left_upper_blank CALL ROI at luma median/std
 * 88.9008/0.0 versus reference 77.1346/6.2397. The accepted per-wall baseColorFactor is already
 * color-correct for the broad wall and stays untouched. This owner adds only one smooth,
 * deterministic, low-frequency baseColorMap on room_left_wall through its existing TEXCOORD_0.
 * Back/right walls, floor/rug, lighting, geometry, camera, furniture, Celine and source GLBs remain
 * untouched. The candidate is intentionally single-shot: if the measured ROI does not clear both
 * 20% error-reduction gates, the strategy is reverted rather than amplitude-tuned repeatedly.
 */
final class CelineRoomLeftWallTextureV80 {
    private static final String ENTITY = "room_left_wall";
    private static final int SIZE = 192;
    private static final WeakHashMap<Celine3DView, Texture> TEXTURES = new WeakHashMap<>();

    private CelineRoomLeftWallTextureV80() {}

    static void apply(Celine3DView view, FilamentAsset asset, Engine engine) throws Exception {
        if (view == null || asset == null || engine == null) return;
        synchronized (TEXTURES) {
            if (TEXTURES.containsKey(view)) return;
        }

        int entity = asset.getFirstEntityByName(ENTITY);
        if (entity == 0) throw new IllegalStateException("left-wall texture: entity fehlt");
        RenderableManager renderables = engine.getRenderableManager();
        int instance = renderables.getInstance(entity);
        if (instance == 0) throw new IllegalStateException("left-wall texture: renderable fehlt");
        int primitives = renderables.getPrimitiveCount(instance);
        if (primitives <= 0) throw new IllegalStateException("left-wall texture: primitives fehlen");

        Bitmap bitmap = createReferenceField();
        Texture texture = null;
        try {
            texture = new Texture.Builder()
                    .width(bitmap.getWidth())
                    .height(bitmap.getHeight())
                    .levels(1)
                    .sampler(Texture.Sampler.SAMPLER_2D)
                    .format(Texture.InternalFormat.SRGB8_A8)
                    .build(engine);
            TextureHelper.setBitmap(engine, texture, 0, bitmap);
            engine.flushAndWait();

            TextureSampler sampler = new TextureSampler(
                    TextureSampler.MinFilter.LINEAR,
                    TextureSampler.MagFilter.LINEAR,
                    TextureSampler.WrapMode.REPEAT);
            for (int primitive = 0; primitive < primitives; primitive++) {
                MaterialInstance material = renderables.getMaterialInstanceAt(instance, primitive);
                if (material == null) {
                    throw new IllegalStateException("left-wall texture: material fehlt " + primitive);
                }
                if (!material.getMaterial().hasParameter("baseColorMap")) {
                    throw new IllegalStateException("left-wall texture: baseColorMap fehlt " + primitive);
                }
                material.setParameter("baseColorMap", texture, sampler);
            }

            synchronized (TEXTURES) {
                TEXTURES.put(view, texture);
            }
            texture = null;
            Celine3DDiagnostics.record(view.getContext(), "ROOM-153",
                    "Linke Referenzwand mit bounded low-frequency baseColorMap",
                    "entity=" + ENTITY
                            + " size=" + SIZE + "x" + SIZE
                            + " path=existing_TEXCOORD_0"
                            + " baselineMedianStd=88.9008/0.0"
                            + " referenceMedianStd=77.1346/6.2397"
                            + " wallFactorUnchanged=true otherSurfacesUnchanged=true"
                            + " geometry/camera/Celine/sourceGLB=false");
        } finally {
            bitmap.recycle();
            if (texture != null) {
                try { engine.destroyTexture(texture); } catch (Throwable ignored) {}
            }
        }
    }

    static void release(Celine3DView view, Engine engine) {
        if (view == null || engine == null) return;
        Texture texture;
        synchronized (TEXTURES) {
            texture = TEXTURES.remove(view);
        }
        if (texture != null) {
            try { engine.destroyTexture(texture); } catch (Throwable ignored) {}
        }
    }

    private static Bitmap createReferenceField() {
        int[] pixels = new int[SIZE * SIZE];
        final double twoPi = Math.PI * 2.0;
        for (int y = 0; y < SIZE; y++) {
            double v = (y + 0.5) / SIZE;
            for (int x = 0; x < SIZE; x++) {
                double u = (x + 0.5) / SIZE;
                double broad =
                        0.46 * Math.sin(twoPi * u) * Math.cos(twoPi * v)
                                + 0.27 * Math.cos(twoPi * (2.0 * u + v) + 0.7)
                                + 0.19 * Math.sin(twoPi * (u - 2.0 * v) + 1.4);
                int fine = (hash(x / 5, y / 5, 137) % 5) - 2;
                int variation = (int) Math.round(broad * 10.0) + fine;

                // Channel bases encode the measured residual direction: reduce red/green more than
                // blue so the accepted 124/83/44 wall moves toward reference 106/72/42 without
                // altering the already-accepted uniform MaterialInstance factors.
                int r = clamp255(238 + variation);
                int g = clamp255(240 + variation);
                int b = clamp255(250 + variation);
                pixels[y * SIZE + x] = Color.rgb(r, g, b);
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE);
        return bitmap;
    }

    private static int hash(int x, int y, int seed) {
        int value = x * 0x45d9f3b + y * 0x119de1f3 + seed * 0x27d4eb2d;
        value ^= value >>> 16;
        value *= 0x45d9f3b;
        value ^= value >>> 16;
        return value & 0x7fffffff;
    }

    private static int clamp255(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
