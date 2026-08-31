package de.yahya.ai;

import com.google.android.filament.Engine;
import com.google.android.filament.IndirectLight;
import com.google.android.filament.LightManager;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * v80 reference-realism owner for the bounded reference-room passes.
 *
 * R1 evidence rejected the overly orange key and then confirmed that enabling shadows on the same
 * warm-neutral directional key restores badly missing room depth. The inspected R1-repair proof
 * also showed that Celine and the room shadow side became too dark compared with the warm, bright
 * reference. R2 therefore raised only the existing indirect fill from 8000 to 10000.
 *
 * The next manually confirmed mismatch is composition/derived geometry: the foreground table is
 * rendered as a fully displayed central slab instead of the cropped near-camera occluder required
 * by the canonical reference. CelineRoomReferenceLayoutV80 applies that one bounded table-layout
 * candidate before lighting. Canonical Celine, camera semantics, source GLB bytes and the accepted
 * interactive floor-lamp behavior remain untouched.
 */
final class CelineRoomReferenceLightingV80 {
    private static final float KEY_RED = 1.00f;
    private static final float KEY_GREEN = 0.80f;
    private static final float KEY_BLUE = 0.66f;
    private static final float KEY_LUX = 11000.0f;
    private static final float INDIRECT_LUX = 10000.0f;

    private static final Set<Celine3DView> APPLIED =
            Collections.newSetFromMap(new WeakHashMap<Celine3DView, Boolean>());

    private CelineRoomReferenceLightingV80() {}

    static void ensure(Celine3DView view) {
        if (view == null) return;

        // Layout has its own room-asset identity guard, so it can reapply after a Surface/room
        // rebuild even when this lighting owner has already been applied to the same Celine3DView.
        CelineRoomReferenceLayoutV80.ensure(view);

        synchronized (APPLIED) {
            if (APPLIED.contains(view)) return;
        }

        try {
            Field engineField = Celine3DView.class.getDeclaredField("engine");
            Field lightEntityField = Celine3DView.class.getDeclaredField("lightEntity");
            Field indirectField = Celine3DView.class.getDeclaredField("indirectLight");
            engineField.setAccessible(true);
            lightEntityField.setAccessible(true);
            indirectField.setAccessible(true);

            Engine engine = (Engine) engineField.get(view);
            int lightEntity = lightEntityField.getInt(view);
            IndirectLight indirect = (IndirectLight) indirectField.get(view);
            if (engine == null || lightEntity == 0 || indirect == null) return;

            LightManager lights = engine.getLightManager();
            int instance = lights.getInstance(lightEntity);
            if (instance == 0) return;

            lights.setColor(instance, KEY_RED, KEY_GREEN, KEY_BLUE);
            lights.setIntensity(instance, KEY_LUX);
            lights.setShadowCaster(instance, true);
            indirect.setIntensity(INDIRECT_LUX);

            synchronized (APPLIED) { APPLIED.add(view); }
            Celine3DDiagnostics.record(view.getContext(), "ROOM-140",
                    "Referenzraum R1/R2 Key und Fill aktiv",
                    "directionalColor=" + KEY_RED + "," + KEY_GREEN + "," + KEY_BLUE
                            + " keyIntensity=" + KEY_LUX + " shadows=true"
                            + " indirectIntensity=" + INDIRECT_LUX
                            + " · exposure/materials/camera/60k-lamp unchanged");
        } catch (Throwable error) {
            Celine3DDiagnostics.error(view.getContext(), "ROOM-149",
                    "Referenzraum R1/R2 Beleuchtung FEHLER", error);
        }
    }
}
