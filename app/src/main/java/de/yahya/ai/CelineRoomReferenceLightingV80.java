package de.yahya.ai;

import com.google.android.filament.Engine;
import com.google.android.filament.IndirectLight;
import com.google.android.filament.LightManager;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * v80 bounded clean-appearance checkpoint for the reference room.
 *
 * The rejected whole-scene raster accumulated a strong directional key, a second warm practical,
 * ceiling/bed material edits, and a derived window/drape replacement. Exact source-PBR evidence on
 * the partitioned Room runtime removed the dominant torn/faceted geometry corruption, while the
 * first real CALL raster on that clean geometry exposed a separate appearance-owner conflict:
 * CelineRoomEnvironmentV80 deliberately rebalances the shared directional key to the bounded Room
 * value, then this owner was destructively zeroing that same key again and collapsing the shell into
 * darkness.
 *
 * Recovery therefore removes only that conflicting zero-lux write. The Room environment remains the
 * sole owner of direct-key intensity/color; this checkpoint keeps the neutral indirect fill and does
 * not mutate room materials, window/drape visibility, furniture transforms, camera/FOV, source GLBs,
 * or the independent lamp interaction owner.
 */
final class CelineRoomReferenceLightingV80 {
    private static final float INDIRECT_LUX = 5600.0f;

    private static final Set<Celine3DView> APPLIED =
            Collections.newSetFromMap(new WeakHashMap<Celine3DView, Boolean>());

    private CelineRoomReferenceLightingV80() {}

    static void ensure(Celine3DView view) {
        if (view == null) return;

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

            // Single-owner recovery: CelineRoomEnvironmentV80 already owns and restores the
            // partition-room shared directional-key rebalance. Do not overwrite that intensity or
            // color here. Keep the established no-shadow response and bounded neutral indirect fill.
            lights.setShadowCaster(instance, false);
            indirect.setIntensity(INDIRECT_LUX);

            synchronized (APPLIED) {
                APPLIED.add(view);
            }

            Celine3DDiagnostics.record(view.getContext(), "ROOM-140",
                    "Referenzraum single-owner Recovery-Baseline aktiv",
                    "directKeyOwner=roomEnvironment directKeyOverride=none shadows=false"
                            + " indirectIntensity=" + INDIRECT_LUX
                            + " materialOverrides=none windowOverride=none practicalOverride=none"
                            + " · room source-PBR/camera/FOV/furniture/source-GLBs unverändert");
        } catch (Throwable error) {
            Celine3DDiagnostics.error(view.getContext(), "ROOM-149",
                    "Referenzraum Recovery-Baseline FEHLER", error);
        }
    }
}
