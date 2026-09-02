package de.yahya.ai;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.filament.Engine;
import com.google.android.filament.Material;
import com.google.android.filament.MaterialInstance;
import com.google.android.filament.Texture;
import com.google.android.filament.TextureSampler;
import com.google.android.filament.android.TextureHelper;
import com.google.android.filament.gltfio.FilamentAsset;

import java.lang.reflect.Field;
import java.util.WeakHashMap;

/**
 * v75 semantic material owner.
 *
 * V39 intentionally keeps forcing the canonical Meshy atlas onto legacy material instances. That
 * behavior is protected because it repaired the historical white/black renderer regression. v75,
 * however, splits the monolithic primitive into four named semantic materials plus canonical skin.
 * Rebinding the canonical atlas to all five instances erases the v75 outfit/hair palette.
 *
 * This owner runs immediately after V39 and touches only the four CelineV75_* material instances.
 * The canonical skin/face material remains completely owned by V39. Older/private models that do
 * not contain the v75 semantic names are a no-op.
 */
final class CelineSemanticMaterialsV75 {
    private static final WeakHashMap<Activity, State> STATES = new WeakHashMap<>();

    private static final String TOP = "CelineV75_BeigeRibbedTop";
    private static final String JEANS = "CelineV75_FittedBlackJeans";
    private static final String SHOES = "CelineV75_WhiteSneakers";
    private static final String HAIR = "CelineV75_GoldenBlondeHair";

    private static final int[][] COLORS = {
            {194, 163, 130}, // warm beige
            {5, 5, 6},       // fitted black denim
            {250, 247, 240}, // warm white sneakers
            {224, 179, 117}, // golden blonde
    };

    private CelineSemanticMaterialsV75() {}

    static void apply(Activity activity, View decor) {
        if (!(activity instanceof MainActivity) || decor == null) return;
        Celine3DView view = find3D(decor);
        if (view == null || !view.isAttachedToWindow()) return;

        try {
            Engine engine = (Engine) field(view, "engine");
            FilamentAsset asset = (FilamentAsset) field(view, "asset");
            if (engine == null || asset == null || asset.getInstance() == null) return;
            MaterialInstance[] instances = asset.getInstance().getMaterialInstances();
            if (instances == null || instances.length == 0) return;

            int semanticCount = 0;
            for (MaterialInstance instance : instances) {
                if (semanticIndex(instance) >= 0) semanticCount++;
            }
            if (semanticCount == 0) return; // legacy/private model: preserve old owner exactly
            if (semanticCount != 4) {
                throw new IllegalStateException("expected 4 v75 semantic materials, found " + semanticCount);
            }

            State state;
            synchronized (STATES) {
                state = STATES.get(activity);
                if (state == null || state.view != view || state.engine != engine) {
                    state = new State(view, engine, createTextures(engine));
                    STATES.put(activity, state);
                }
            }

            TextureSampler sampler = new TextureSampler(TextureSampler.MinFilter.LINEAR,
                    TextureSampler.MagFilter.LINEAR, TextureSampler.WrapMode.REPEAT);
            boolean[] seen = new boolean[4];
            int bound = 0;
            for (MaterialInstance instance : instances) {
                int index = semanticIndex(instance);
                if (index < 0) continue;
                Material material = instance.getMaterial();
                String samplerName = findBaseColorSampler(material);
                if (samplerName == null) {
                    throw new IllegalStateException("semantic material has no base-color sampler: " + safeName(instance));
                }
                instance.setParameter(samplerName, state.textures[index], sampler);
                set4(instance, "baseColorFactor", 1f, 1f, 1f, 1f);
                set1(instance, "metallicFactor", 0f);
                set1(instance, "roughnessFactor", 0.75f);
                set3(instance, "emissiveFactor", 0f, 0f, 0f);
                set1(instance, "emissiveStrength", 0f);
                set1(instance, "specularFactor", 0.3f);
                set3(instance, "specularColorFactor", 1f, 1f, 1f);
                seen[index] = true;
                bound++;
            }
            for (int i = 0; i < seen.length; i++) {
                if (!seen[i]) throw new IllegalStateException("missing semantic material index " + i);
            }
            Celine3DDiagnostics.record(activity, "V75-160", "v75 Semantikfarben nach V39 gebunden",
                    "materials=" + bound + " · skin/face remains V39 owner · top/jeans/shoes/hair restored");
        } catch (Throwable error) {
            Celine3DDiagnostics.error(activity, "V75-199", "v75 Semantikmaterial FEHLER", error);
        }
    }

    private static Texture[] createTextures(Engine engine) {
        Texture[] result = new Texture[COLORS.length];
        for (int i = 0; i < COLORS.length; i++) {
            Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.rgb(COLORS[i][0], COLORS[i][1], COLORS[i][2]));
            Texture texture = new Texture.Builder()
                    .width(1).height(1).levels(1)
                    .sampler(Texture.Sampler.SAMPLER_2D)
                    .format(Texture.InternalFormat.SRGB8_A8)
                    .build(engine);
            TextureHelper.setBitmap(engine, texture, 0, bitmap);
            bitmap.recycle();
            result[i] = texture;
        }
        engine.flushAndWait();
        return result;
    }

    private static int semanticIndex(MaterialInstance instance) {
        String name = safeName(instance);
        if (name.contains(TOP)) return 0;
        if (name.contains(JEANS)) return 1;
        if (name.contains(SHOES)) return 2;
        if (name.contains(HAIR)) return 3;
        return -1;
    }

    private static String safeName(MaterialInstance instance) {
        try {
            String name = instance == null ? null : instance.getName();
            return name == null ? "" : name;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String findBaseColorSampler(Material material) {
        if (material == null) return null;
        if (material.hasParameter("baseColorMap")) return "baseColorMap";
        for (Material.Parameter parameter : material.getParameters()) {
            if (parameter.type != Material.Parameter.Type.SAMPLER_2D) continue;
            String name = parameter.name == null ? "" : parameter.name.toLowerCase();
            if (name.contains("base") && name.contains("color")) return parameter.name;
        }
        return null;
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Celine3DView find3D(View view) {
        if (view instanceof Celine3DView) return (Celine3DView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Celine3DView found = find3D(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void set1(MaterialInstance instance, String name, float value) {
        try { if (instance.getMaterial().hasParameter(name)) instance.setParameter(name, value); }
        catch (Throwable ignored) {}
    }

    private static void set3(MaterialInstance instance, String name, float x, float y, float z) {
        try { if (instance.getMaterial().hasParameter(name)) instance.setParameter(name, x, y, z); }
        catch (Throwable ignored) {}
    }

    private static void set4(MaterialInstance instance, String name, float x, float y, float z, float w) {
        try { if (instance.getMaterial().hasParameter(name)) instance.setParameter(name, x, y, z, w); }
        catch (Throwable ignored) {}
    }

    private static final class State {
        final Celine3DView view;
        final Engine engine;
        final Texture[] textures;
        State(Celine3DView view, Engine engine, Texture[] textures) {
            this.view = view;
            this.engine = engine;
            this.textures = textures;
        }
    }
}
