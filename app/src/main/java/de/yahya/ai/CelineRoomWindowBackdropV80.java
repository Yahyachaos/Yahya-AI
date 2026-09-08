package de.yahya.ai;

import com.google.android.filament.Engine;
import com.google.android.filament.IndirectLight;
import com.google.android.filament.LightManager;
import com.google.android.filament.gltfio.FilamentAsset;

import java.lang.reflect.Field;
import java.util.WeakHashMap;

/**
 * Recovery-only neutral lighting owner for the reference-room architecture checkpoint.
 *
 * Real Candidate #1379 and the later recovery panes demonstrated that stacking derived window
 * geometry/material layers on top of the source room can make the CALL raster look numerically
 * plausible while visibly corrupting the window/drapes appearance. This recovery owner therefore
 * does one thing only: normalize the already existing renderer key/indirect illumination and leave
 * the source window/drapes plus their source PBR untouched.
 *
 * No derived window planes, textures, source hiding, material duplication, furniture transforms,
 * camera/FOV writes, source-GLB mutation or Celine changes are owned here.
 */
final class CelineRoomWindowBackdropV80 {
    private static final float RECOVERY_KEY_RED = 1.00f;
    private static final float RECOVERY_KEY_GREEN = 0.95f;
    private static final float RECOVERY_KEY_BLUE = 0.90f;
    private static final float RECOVERY_KEY_LUX = 5000.0f;
    private static final float RECOVERY_INDIRECT_LUX = 5600.0f;

    private static final WeakHashMap<Celine3DView, Boolean> STATES = new WeakHashMap<>();

    private CelineRoomWindowBackdropV80() {}

    static void apply(Celine3DView view, FilamentAsset asset, Engine engine) throws Exception {
        if (view == null || asset == null || engine == null) return;
        synchronized (STATES) {
            if (Boolean.TRUE.equals(STATES.get(view))) return;
        }

        applyRecoveryNeutralLighting(view, engine);
        synchronized (STATES) {
            STATES.put(view, Boolean.TRUE);
        }
        Celine3DDiagnostics.record(view.getContext(), "ROOM-148",
                "Recovery-Sourcefenster mit neutralem Licht aktiv",
                "key=" + RECOVERY_KEY_LUX + "lux"
                        + " · indirect=" + RECOVERY_INDIRECT_LUX + "lux"
                        + " · derivedWindowPlanes=false"
                        + " · sourceWindowDrapes=true"
                        + " · sourcePBR=true"
                        + " · experimentalTextures=false"
                        + " · sourceHide=false"
                        + " · ceilingBedOverrides=false"
                        + " · cameraFurnitureCelineUnchanged=true");
    }

    private static void applyRecoveryNeutralLighting(Celine3DView view, Engine engine)
            throws Exception {
        Object rawLightEntity = field(view, "lightEntity");
        if (!(rawLightEntity instanceof Integer)) {
            throw new IllegalStateException("recovery key light entity fehlt");
        }
        int lightEntity = (Integer) rawLightEntity;
        IndirectLight indirect = (IndirectLight) field(view, "indirectLight");
        if (lightEntity == 0 || indirect == null) {
            throw new IllegalStateException("recovery key/indirect light fehlt");
        }
        LightManager lights = engine.getLightManager();
        int light = lights.getInstance(lightEntity);
        if (light == 0) throw new IllegalStateException("recovery key light instance fehlt");
        lights.setColor(light, RECOVERY_KEY_RED, RECOVERY_KEY_GREEN, RECOVERY_KEY_BLUE);
        lights.setIntensity(light, RECOVERY_KEY_LUX);
        lights.setShadowCaster(light, true);
        indirect.setIntensity(RECOVERY_INDIRECT_LUX);
    }

    static void release(Celine3DView view, Engine engine) {
        synchronized (STATES) {
            STATES.remove(view);
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
