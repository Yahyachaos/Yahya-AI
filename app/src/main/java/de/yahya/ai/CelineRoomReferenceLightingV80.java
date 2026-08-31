package de.yahya.ai;

import com.google.android.filament.Engine;
import com.google.android.filament.LightManager;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * v80 reference-realism R1: one bounded adjustment of the existing shared directional key.
 *
 * The first R1 proof showed that forcing a very warm 1.00/0.68/0.45 key over-warmed Celine while
 * the room still read flat because the existing directional light did not cast shadows. This
 * evidence-backed repair restores the already-proven warm-neutral key chroma, keeps the softer
 * quantity, and enables shadows on that same existing key. No second light system is created.
 * Camera, indirect light, room materials, geometry, transforms, Celine ownership, anchors/actions
 * and the accepted interactive floor-lamp light remain untouched.
 */
final class CelineRoomReferenceLightingV80 {
    private static final float KEY_RED = 1.00f;
    private static final float KEY_GREEN = 0.80f;
    private static final float KEY_BLUE = 0.66f;
    private static final float KEY_LUX = 11000.0f;

    private static final Set<Celine3DView> APPLIED =
            Collections.newSetFromMap(new WeakHashMap<Celine3DView, Boolean>());

    private CelineRoomReferenceLightingV80() {}

    static void ensure(Celine3DView view) {
        if (view == null) return;
        synchronized (APPLIED) {
            if (APPLIED.contains(view)) return;
        }

        try {
            Field engineField = Celine3DView.class.getDeclaredField("engine");
            Field lightEntityField = Celine3DView.class.getDeclaredField("lightEntity");
            engineField.setAccessible(true);
            lightEntityField.setAccessible(true);

            Engine engine = (Engine) engineField.get(view);
            int lightEntity = lightEntityField.getInt(view);
            if (engine == null || lightEntity == 0) return;

            LightManager lights = engine.getLightManager();
            int instance = lights.getInstance(lightEntity);
            if (instance == 0) return;

            lights.setColor(instance, KEY_RED, KEY_GREEN, KEY_BLUE);
            lights.setIntensity(instance, KEY_LUX);
            lights.setShadowCaster(instance, true);

            synchronized (APPLIED) { APPLIED.add(view); }
            Celine3DDiagnostics.record(view.getContext(), "ROOM-140",
                    "Referenzraum R1 warm-neutrales Key-Light mit Tiefe aktiv",
                    "directionalColor=" + KEY_RED + "," + KEY_GREEN + "," + KEY_BLUE
                            + " intensity=" + KEY_LUX + " shadows=true"
                            + " · indirect/exposure/materials/camera/60k-lamp unchanged");
        } catch (Throwable error) {
            Celine3DDiagnostics.error(view.getContext(), "ROOM-149",
                    "Referenzraum R1 Key-Light FEHLER", error);
        }
    }
}
