package de.yahya.ai;

import com.google.android.filament.Engine;
import com.google.android.filament.RenderableManager;
import com.google.android.filament.gltfio.FilamentAsset;

import java.lang.reflect.Field;

/** Lab-only guard for camera inspection of Meshy's skinned geometry. */
final class CelineAvatarLabCameraGuardV79 {
    private CelineAvatarLabCameraGuardV79() {}

    static void disableStaleBoundsCulling(Celine3DView view) throws Exception {
        Engine engine = (Engine) field(view, "engine");
        FilamentAsset asset = (FilamentAsset) field(view, "asset");
        RenderableManager renderables = engine.getRenderableManager();
        int disabled = 0;
        for (int entity : asset.getEntities()) {
            if (!renderables.hasComponent(entity)) continue;
            int instance = renderables.getInstance(entity);
            if (instance == 0) continue;
            renderables.setCulling(instance, false);
            disabled++;
        }
        if (disabled == 0) {
            throw new IllegalStateException("Keine Avatar-Renderables für Lab-Kamera gefunden");
        }
        Celine3DDiagnostics.record(view.getContext(), "V79-520",
                "Avatar Lab Frustum-Culling deaktiviert",
                "renderables=" + disabled + " · nur Lab-Kamera");
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = Celine3DView.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
