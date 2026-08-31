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
 * This helper deliberately does not create a second light system and does not touch camera,
 * indirect light, room materials, geometry, transforms, Celine, anchors/actions or the accepted
 * interactive floor-lamp light. It only warms and softens the already-existing directional key.
 */
final class CelineRoomReferenceLightingV80 {
    private static final float KEY_RED = 1.00f;
    private static final float KEY_GREEN = 0.68f;
    private static final float KEY_BLUE = 0.45f;
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

            synchronized (APPLIED) { APPLIED.add(view); }
            Celine3DDiagnostics.record(view.getContext(), "ROOM-140",
                    "Referenzraum R1 warmes Key-Light aktiv",
                    "directionalColor=" + KEY_RED + "," + KEY_GREEN + "," + KEY_BLUE
                            + " intensity=" + KEY_LUX
                            + " · indirect/exposure/materials/camera/60k-lamp unchanged");
        } catch (Throwable error) {
            Celine3DDiagnostics.error(view.getContext(), "ROOM-149",
                    "Referenzraum R1 Key-Light FEHLER", error);
        }
    }
}
