package de.yahya.ai;

import android.view.View;
import android.view.ViewGroup;

import com.google.android.filament.MaterialInstance;
import com.google.android.filament.gltfio.FilamentAsset;

import java.lang.reflect.Field;

/** Runtime half of v38: re-applies the rescued material after Celine3DView's v34 PBR pass. */
final class CelineRuntimeTextureRescue {
    private CelineRuntimeTextureRescue() {}

    static void ensure(View root) {
        if (root == null) return;
        Celine3DView view = find3D(root);
        if (view == null) return;
        try {
            Field assetField = Celine3DView.class.getDeclaredField("asset");
            assetField.setAccessible(true);
            FilamentAsset asset = (FilamentAsset) assetField.get(view);
            if (asset == null || asset.getInstance() == null) return;

            MaterialInstance[] materials = asset.getInstance().getMaterialInstances();
            int changed = 0;
            for (MaterialInstance material : materials) {
                if (material == null) continue;
                try { material.setParameter("metallicFactor", 0.0f); } catch (Throwable ignored) {}
                try { material.setParameter("roughnessFactor", 0.78f); } catch (Throwable ignored) {}
                try { material.setParameter("baseColorFactor", 1.0f, 1.0f, 1.0f, 1.0f); } catch (Throwable ignored) {}
                // The GLB now binds the embedded atlas to emissiveTexture as well as baseColorTexture.
                // Keep this at a low fill level: it reveals the real atlas without flattening all light.
                try { material.setParameter("emissiveFactor", 0.38f, 0.38f, 0.38f); } catch (Throwable ignored) {}
                try { material.setParameter("emissiveStrength", 1.0f); } catch (Throwable ignored) {}
                try { material.setParameter("specularFactor", 0.18f); } catch (Throwable ignored) {}
                try { material.setParameter("specularColorFactor", 1.0f, 1.0f, 1.0f); } catch (Throwable ignored) {}
                try { material.setParameter("reflectance", 0.35f); } catch (Throwable ignored) {}
                changed++;
            }
            Celine3DDiagnostics.record(root.getContext(), "V38-170", "Runtime-Texturmaterial aktiviert",
                    "materials=" + changed + " · emissiveFill=0.38");
        } catch (Throwable e) {
            Celine3DDiagnostics.error(root.getContext(), "V38-198", "Runtime-Texturmaterial FEHLER", e);
        }
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
