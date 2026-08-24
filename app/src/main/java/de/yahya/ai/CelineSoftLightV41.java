package de.yahya.ai;

import android.view.View;
import android.view.ViewGroup;

import com.google.android.filament.Camera;
import com.google.android.filament.Engine;
import com.google.android.filament.IndirectLight;
import com.google.android.filament.LightManager;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * v41 diagnostic light pass.
 *
 * v40 proved the embedded 4096x4096 atlas is explicitly bound to baseColorMap. The remaining
 * white upper-body regions therefore need a controlled exposure/light test, not another GLB or
 * texture rewrite. This helper only lowers scene illumination; it leaves model, rig and texture
 * bindings untouched.
 */
final class CelineSoftLightV41 {
    private static final float KEY_LUX = 7000.0f;
    private static final float IBL_LUX = 1800.0f;
    private static final Set<Celine3DView> APPLIED =
            Collections.newSetFromMap(new WeakHashMap<Celine3DView, Boolean>());

    private CelineSoftLightV41() {}

    static void ensure(View root) {
        Celine3DView threeD = find3D(root);
        if (threeD == null) return;
        synchronized (APPLIED) {
            if (APPLIED.contains(threeD)) return;
        }

        try {
            Field engineField = Celine3DView.class.getDeclaredField("engine");
            Field lightEntityField = Celine3DView.class.getDeclaredField("lightEntity");
            Field indirectField = Celine3DView.class.getDeclaredField("indirectLight");
            Field cameraField = Celine3DView.class.getDeclaredField("camera");
            engineField.setAccessible(true);
            lightEntityField.setAccessible(true);
            indirectField.setAccessible(true);
            cameraField.setAccessible(true);

            Engine engine = (Engine) engineField.get(threeD);
            int lightEntity = lightEntityField.getInt(threeD);
            IndirectLight indirect = (IndirectLight) indirectField.get(threeD);
            Camera camera = (Camera) cameraField.get(threeD);
            if (engine == null || camera == null) return;

            LightManager lights = engine.getLightManager();
            int lightInstance = lights.getInstance(lightEntity);
            if (lightInstance != 0) lights.setIntensity(lightInstance, KEY_LUX);
            if (indirect != null) indirect.setIntensity(IBL_LUX);

            // Keep the proven v36 camera exposure constant. Only light quantity changes.
            camera.setExposure(8.0f, 1.0f / 125.0f, 100.0f);

            synchronized (APPLIED) { APPLIED.add(threeD); }
            Celine3DDiagnostics.record(root.getContext(), "V41-100", "SOFT-LIGHT aktiv",
                    "key=" + KEY_LUX + " lux · indirect=" + IBL_LUX +
                            " lux · camera=f/8 1/125 ISO100 · FORCE-C unverändert");
        } catch (Throwable e) {
            Celine3DDiagnostics.error(root.getContext(), "V41-199", "SOFT-LIGHT FEHLER", e);
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
