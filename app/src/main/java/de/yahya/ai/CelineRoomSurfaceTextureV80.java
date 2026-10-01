package de.yahya.ai;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.android.filament.Colors;
import com.google.android.filament.Engine;
import com.google.android.filament.MaterialInstance;
import com.google.android.filament.RenderableManager;
import com.google.android.filament.Texture;
import com.google.android.filament.TextureSampler;
import com.google.android.filament.android.TextureHelper;
import com.google.android.filament.gltfio.FilamentAsset;

import java.util.WeakHashMap;

/**
 * Real room-surface texture owner.
 *
 * The optimized furniture GLBs already contain their PBR base-color/normal/roughness maps. The room
 * shell did not: walls and floor were only flat baseColorFactor values, which made the real app look
 * like an unfinished clay render even when furniture textures were loaded. This owner creates two
 * small deterministic runtime maps using the shell's existing UVs: subtle warm plaster for the walls
 * and restrained warm wood planks for the floor. It does not touch furniture textures or source GLBs.
 */
final class CelineRoomSurfaceTextureV80 {
    private static final WeakHashMap<Celine3DView, State> STATES = new WeakHashMap<>();

    private CelineRoomSurfaceTextureV80() {}

    static void apply(Celine3DView view, FilamentAsset asset, Engine engine) throws Exception {
        if (view == null || asset == null || engine == null) return;
        release(view, engine);

        Bitmap plaster = createPlaster(384, 384);
        Bitmap wood = createWood(512, 512);
        Texture wallTexture = null;
        Texture floorTexture = null;
        try {
            wallTexture = upload(engine, plaster);
            floorTexture = upload(engine, wood);
            engine.flushAndWait();

            TextureSampler wallSampler = new TextureSampler(
                    TextureSampler.MinFilter.LINEAR,
                    TextureSampler.MagFilter.LINEAR,
                    TextureSampler.WrapMode.REPEAT);
            TextureSampler floorSampler = new TextureSampler(
                    TextureSampler.MinFilter.LINEAR,
                    TextureSampler.MagFilter.LINEAR,
                    TextureSampler.WrapMode.REPEAT);

            for (String wall : new String[]{"room_back_wall", "room_left_wall", "room_right_wall"}) {
                bind(asset, engine, wall, wallTexture, wallSampler,
                        0.98f, 0.96f, 0.92f, 0.90f, 0.34f);
            }
            bind(asset, engine, "room_floor", floorTexture, floorSampler,
                    0.98f, 0.92f, 0.84f, 0.66f, 0.42f);

            synchronized (STATES) {
                STATES.put(view, new State(wallTexture, floorTexture));
            }
            wallTexture = null;
            floorTexture = null;

            Celine3DDiagnostics.record(view.getContext(), "ROOM-145",
                    "Echte Raumoberflächen-Texturen aktiv",
                    "walls=deterministic-warm-plaster-384"
                            + " floor=deterministic-warm-wood-512"
                            + " uv=existing-shell-texcoord0"
                            + " furniturePbr=untouched sourceGlb=unchanged");
        } finally {
            plaster.recycle();
            wood.recycle();
            if (wallTexture != null) {
                try { engine.destroyTexture(wallTexture); } catch (Throwable ignored) {}
            }
            if (floorTexture != null) {
                try { engine.destroyTexture(floorTexture); } catch (Throwable ignored) {}
            }
        }
    }

    static void release(Celine3DView view, Engine engine) {
        if (view == null || engine == null) return;
        State previous;
        synchronized (STATES) {
            previous = STATES.remove(view);
        }
        if (previous == null) return;
        try { engine.destroyTexture(previous.wall); } catch (Throwable ignored) {}
        try { engine.destroyTexture(previous.floor); } catch (Throwable ignored) {}
    }

    private static Texture upload(Engine engine, Bitmap bitmap) {
        Texture texture = new Texture.Builder()
                .width(bitmap.getWidth())
                .height(bitmap.getHeight())
                .levels(1)
                .sampler(Texture.Sampler.SAMPLER_2D)
                .format(Texture.InternalFormat.SRGB8_A8)
                .build(engine);
        boolean uploaded = false;
        try {
            TextureHelper.setBitmap(engine, texture, 0, bitmap);
            uploaded = true;
            return texture;
        } finally {
            if (!uploaded) {
                try { engine.destroyTexture(texture); } catch (Throwable ignored) {}
            }
        }
    }

    private static void bind(FilamentAsset asset, Engine engine, String entityName,
                             Texture texture, TextureSampler sampler,
                             float red, float green, float blue,
                             float roughness, float reflectance) {
        int entity = asset.getFirstEntityByName(entityName);
        if (entity == 0) throw new IllegalStateException("surface entity fehlt: " + entityName);
        RenderableManager manager = engine.getRenderableManager();
        int renderable = manager.getInstance(entity);
        if (renderable == 0) throw new IllegalStateException("surface renderable fehlt: " + entityName);
        int primitives = manager.getPrimitiveCount(renderable);
        if (primitives <= 0) throw new IllegalStateException("surface primitive fehlt: " + entityName);
        for (int primitive = 0; primitive < primitives; primitive++) {
            MaterialInstance material = manager.getMaterialInstanceAt(renderable, primitive);
            if (material == null) throw new IllegalStateException("surface material fehlt: " + entityName);
            if (!material.getMaterial().hasParameter("baseColorMap")) {
                throw new IllegalStateException("baseColorMap nicht verfuegbar: " + entityName);
            }
            material.setParameter("baseColorMap", texture, sampler);
            if (material.getMaterial().hasParameter("baseColorFactor")) {
                material.setParameter("baseColorFactor", Colors.RgbaType.LINEAR,
                        red, green, blue, 1.0f);
            }
            if (material.getMaterial().hasParameter("metallicFactor")) {
                material.setParameter("metallicFactor", 0.0f);
            }
            if (material.getMaterial().hasParameter("roughnessFactor")) {
                material.setParameter("roughnessFactor", roughness);
            }
            if (material.getMaterial().hasParameter("reflectance")) {
                material.setParameter("reflectance", reflectance);
            }
        }
    }

    private static Bitmap createPlaster(int width, int height) {
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int n1 = hash(x, y, 17) % 13 - 6;
                int n2 = hash(x / 9, y / 9, 41) % 9 - 4;
                int wave = Math.round((float) Math.sin((x + y * 0.37) * 0.055) * 2.0f);
                int variation = n1 + n2 + wave;
                int r = clamp255(216 + variation);
                int g = clamp255(205 + variation);
                int b = clamp255(188 + variation);
                pixels[y * width + x] = Color.rgb(r, g, b);
            }
        }
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        result.setPixels(pixels, 0, width, 0, 0, width, height);
        return result;
    }

    private static Bitmap createWood(int width, int height) {
        int[] pixels = new int[width * height];
        int plankHeight = Math.max(24, height / 9);
        for (int y = 0; y < height; y++) {
            int plank = y / plankHeight;
            int localY = y % plankHeight;
            int plankTone = (hash(plank, 0, 73) % 21) - 10;
            boolean seam = localY <= 1 || localY >= plankHeight - 2;
            for (int x = 0; x < width; x++) {
                float grain = (float) Math.sin(x * 0.10 + plank * 1.73)
                        + 0.55f * (float) Math.sin(x * 0.031 + localY * 0.16 + plank);
                int fine = hash(x / 3, y, 97) % 9 - 4;
                int g = Math.round(grain * 5.0f) + fine;
                int r = 132 + plankTone + g;
                int gr = 91 + plankTone / 2 + g / 2;
                int b = 57 + plankTone / 3 + g / 3;
                if (seam) {
                    r -= 30;
                    gr -= 25;
                    b -= 18;
                }
                // A few short cross-joints keep the surface from reading as one stretched stripe.
                int jointX = ((plank * 137) + 83) % width;
                if (Math.abs(x - jointX) <= 1 && localY > 2 && localY < plankHeight - 3) {
                    r -= 22;
                    gr -= 18;
                    b -= 12;
                }
                pixels[y * width + x] = Color.rgb(clamp255(r), clamp255(gr), clamp255(b));
            }
        }
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        result.setPixels(pixels, 0, width, 0, 0, width, height);
        return result;
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

    private static final class State {
        final Texture wall;
        final Texture floor;

        State(Texture wall, Texture floor) {
            this.wall = wall;
            this.floor = floor;
        }
    }
}
