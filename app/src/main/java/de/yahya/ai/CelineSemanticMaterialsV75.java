package de.yahya.ai;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.filament.MaterialInstance;
import com.google.android.filament.gltfio.FilamentAsset;

import java.lang.reflect.Field;

/**
 * v75 semantic material guard.
 *
 * v75 split Celine into canonical skin plus four semantic material regions. Its later runtime owner
 * rebound those four regions to 1x1 solid-color textures so an older material-normalization pass could
 * not erase the intended palette. v80 evidence now shows that this solid-color rebinding is itself a
 * production regression: CALL loses texture/detail diversity and hair/top read as broad brown/orange
 * blocks even when room lighting and the canonical source PBR response are preserved.
 *
 * V39 production FORCE-C already runs immediately before this owner and binds the canonical Meshy atlas
 * to every Celine material instance. Keep the validated semantic triangle split and material names, but do
 * not replace that textured atlas with flat 1x1 colors. Older/private models without the four semantic
 * names remain a no-op. Source GLB bytes, rig, geometry and material-region assignments are untouched.
 */
final class CelineSemanticMaterialsV75 {
    private static final String TOP = "CelineV75_BeigeRibbedTop";
    private static final String JEANS = "CelineV75_FittedBlackJeans";
    private static final String SHOES = "CelineV75_WhiteSneakers";
    private static final String HAIR = "CelineV75_GoldenBlondeHair";

    private CelineSemanticMaterialsV75() {}

    static void apply(Activity activity, View decor) {
        if (!(activity instanceof MainActivity) || decor == null) return;
        Celine3DView view = find3D(decor);
        if (view == null || !view.isAttachedToWindow()) return;

        try {
            FilamentAsset asset = (FilamentAsset) field(view, "asset");
            if (asset == null || asset.getInstance() == null) return;
            MaterialInstance[] instances = asset.getInstance().getMaterialInstances();
            if (instances == null || instances.length == 0) return;

            boolean[] seen = new boolean[4];
            int semanticCount = 0;
            for (MaterialInstance instance : instances) {
                int index = semanticIndex(instance);
                if (index < 0) continue;
                seen[index] = true;
                semanticCount++;
            }
            if (semanticCount == 0) return;
            if (semanticCount != 4) {
                throw new IllegalStateException("expected 4 v75 semantic materials, found " + semanticCount);
            }
            for (int i = 0; i < seen.length; i++) {
                if (!seen[i]) throw new IllegalStateException("missing semantic material index " + i);
            }

            Celine3DDiagnostics.record(activity, "V75-160",
                    "v75 Semantikregionen auf kanonischem Texturatlas belassen",
                    "materials=" + semanticCount
                            + " · V39 FORCE-C atlas retained"
                            + " · no 1x1 flat-color rebinding"
                            + " · source GLB/rig/geometry/material split unchanged");
        } catch (Throwable error) {
            Celine3DDiagnostics.error(activity, "V75-199", "v75 Semantikmaterial FEHLER", error);
        }
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
}
