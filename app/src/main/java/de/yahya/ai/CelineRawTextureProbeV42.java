package de.yahya.ai;

import android.view.View;
import android.view.ViewGroup;

import com.google.android.filament.Engine;
import com.google.android.filament.Material;
import com.google.android.filament.MaterialInstance;
import com.google.android.filament.RenderableManager;
import com.google.android.filament.Texture;
import com.google.android.filament.TextureSampler;
import com.google.android.filament.gltfio.FilamentAsset;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * v42 decisive texture-path probe.
 *
 * Reuses the exact GPU texture that v39 already proved as TEXTURE_OK and binds it directly to the
 * material instance that RenderableManager says is actually drawing the mesh. The normal PBR/base
 * color contribution is then disabled and the same texture is emitted through emissiveMap + UV0.
 * This removes directional/indirect lighting, metallic/roughness and baseColor shading from the
 * equation without touching the GLB, rig, mesh, skin or texture bytes.
 */
final class CelineRawTextureProbeV42 {
    private static final float[] IDENTITY3 = new float[]{
            1f, 0f, 0f,
            0f, 1f, 0f,
            0f, 0f, 1f
    };

    private CelineRawTextureProbeV42() {}

    static void apply(View root) {
        if (root == null) return;
        Celine3DView threeD = find3D(root);
        if (threeD == null) return;

        try {
            Engine engine = (Engine) privateField(Celine3DView.class, "engine").get(threeD);
            FilamentAsset asset = (FilamentAsset) privateField(Celine3DView.class, "asset").get(threeD);
            if (engine == null || asset == null) return;

            Texture provenTexture = findV39Texture(threeD);
            if (provenTexture == null) {
                Celine3DDiagnostics.record(root.getContext(), "V42-199",
                        "RAW-TEXTURE wartet auf GPU-Textur",
                        "V39 TEXTURE_OK-Textur noch nicht im Cache");
                return;
            }

            RenderableManager rm = engine.getRenderableManager();
            List<MaterialInstance> drawingMaterials = new ArrayList<>();
            int renderables = 0;
            int primitives = 0;
            LinkedHashSet<String> materialNames = new LinkedHashSet<>();

            for (int entity : asset.getEntities()) {
                if (!rm.hasComponent(entity)) continue;
                renderables++;
                int instance = rm.getInstance(entity);
                int pc = rm.getPrimitiveCount(instance);
                primitives += pc;
                for (int p = 0; p < pc; p++) {
                    MaterialInstance mi = rm.getMaterialInstanceAt(instance, p);
                    if (mi == null) continue;
                    drawingMaterials.add(mi);
                    try { materialNames.add(mi.getName()); } catch (Throwable ignored) {}
                }
            }

            MaterialInstance[] assetMaterials = asset.getInstance() == null
                    ? null : asset.getInstance().getMaterialInstances();
            int assetMaterialCount = assetMaterials == null ? 0 : assetMaterials.length;

            Celine3DDiagnostics.record(root.getContext(), "V42-110",
                    "Tatsaechliches Renderable auditiert",
                    "renderables=" + renderables +
                            " · primitives=" + primitives +
                            " · drawingMats=" + drawingMaterials.size() +
                            " · assetMats=" + assetMaterialCount +
                            " · names=" + materialNames);

            if (drawingMaterials.isEmpty()) {
                Celine3DDiagnostics.record(root.getContext(), "V42-198",
                        "Kein zeichnendes Material gefunden",
                        "renderables=" + renderables + " primitives=" + primitives);
                return;
            }

            TextureSampler sampler = new TextureSampler(
                    TextureSampler.MinFilter.LINEAR,
                    TextureSampler.MagFilter.LINEAR,
                    TextureSampler.WrapMode.REPEAT);

            int bound = 0;
            for (MaterialInstance mi : drawingMaterials) {
                Material mat = mi.getMaterial();
                if (mat == null) continue;

                // First lock the UV transform to identity on the real drawing material.
                setMat3(mi, "baseColorUvMatrix", IDENTITY3);
                if (mat.hasParameter("baseColorIndex")) mi.setParameter("baseColorIndex", -1);
                if (mat.hasParameter("baseColorFactor")) mi.setParameter("baseColorFactor", 0f, 0f, 0f, 1f);

                // The exact same 4096x4096 GPU texture that passed V39-150 is now emitted directly.
                if (!mat.hasParameter("emissiveMap") || !mat.hasParameter("emissiveIndex")) continue;
                mi.setParameter("emissiveMap", provenTexture, sampler);
                mi.setParameter("emissiveIndex", 0);
                setMat3(mi, "emissiveUvMatrix", IDENTITY3);
                if (mat.hasParameter("emissiveFactor")) mi.setParameter("emissiveFactor", 1f, 1f, 1f);
                if (mat.hasParameter("emissiveStrength")) mi.setParameter("emissiveStrength", 1.0f);
                if (mat.hasParameter("metallicFactor")) mi.setParameter("metallicFactor", 0f);
                if (mat.hasParameter("roughnessFactor")) mi.setParameter("roughnessFactor", 1f);
                bound++;
            }

            if (bound > 0) {
                Celine3DDiagnostics.record(root.getContext(), "V42-150",
                        "RAW_TEXTURE AKTIV",
                        "emissiveMap=V39 GPU texture · uv=0 · uvMatrix=IDENTITY · baseColor=OFF · boundPrimitives=" + bound);
            } else {
                Celine3DDiagnostics.record(root.getContext(), "V42-159",
                        "RAW_TEXTURE konnte nicht gebunden werden",
                        "drawingMaterials=" + drawingMaterials.size());
            }
        } catch (Throwable e) {
            Celine3DDiagnostics.error(root.getContext(), "V42-299", "RAW-TEXTURE Probe FEHLER", e);
        }
    }

    private static Field privateField(Class<?> type, String name) throws Exception {
        Field f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    @SuppressWarnings("unchecked")
    private static Texture findV39Texture(Celine3DView view) throws Exception {
        Field f = CelineTexturePipelineV39.class.getDeclaredField("FORCED_TEXTURES");
        f.setAccessible(true);
        Object value = f.get(null);
        if (!(value instanceof Map)) return null;
        Map<Celine3DView, Texture> map = (Map<Celine3DView, Texture>) value;
        synchronized (map) {
            return map.get(view);
        }
    }

    private static void setMat3(MaterialInstance mi, String name, float[] matrix) {
        try {
            if (mi.getMaterial().hasParameter(name)) {
                mi.setParameter(name, MaterialInstance.FloatElement.MAT3, matrix, 0, 1);
            }
        } catch (Throwable ignored) {}
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
}
