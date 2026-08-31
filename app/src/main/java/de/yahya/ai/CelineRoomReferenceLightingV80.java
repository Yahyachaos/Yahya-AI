package de.yahya.ai;

import android.view.View;

import com.google.android.filament.Engine;
import com.google.android.filament.EntityManager;
import com.google.android.filament.IndirectLight;
import com.google.android.filament.LightManager;
import com.google.android.filament.Scene;

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
 * The subsequent bounded table-layout correction now restores the required near-camera foreground
 * crop. The exact production proof still reads broadly and globally lit versus the canonical warm
 * evening reference. R3 therefore adds one restrained always-on practical point light at the
 * visible front/right nightstand lamp. It is deliberately separate from the accepted interactive
 * 60,000 lm floor-lamp toggle and does not change camera, room/furniture transforms or Celine.
 */
final class CelineRoomReferenceLightingV80 {
    private static final float KEY_RED = 1.00f;
    private static final float KEY_GREEN = 0.80f;
    private static final float KEY_BLUE = 0.66f;
    private static final float KEY_LUX = 11000.0f;
    private static final float INDIRECT_LUX = 10000.0f;

    // Assembly front nightstand: (2.66, 0.609148, 0.50). Put a small warm point just above the
    // visible shade, after the already locked room-root runtime offset. This is a local practical,
    // not another global fill and not the protected interactive floor-lamp light.
    private static final float PRACTICAL_X = 2.66f + CelineRoomWorldContractV80.RUNTIME_OFFSET_X;
    private static final float PRACTICAL_Y = 1.28f + CelineRoomWorldContractV80.RUNTIME_OFFSET_Y;
    private static final float PRACTICAL_Z = 0.50f + CelineRoomWorldContractV80.RUNTIME_OFFSET_Z;
    private static final float PRACTICAL_LUMENS = 6000.0f;
    private static final float PRACTICAL_FALLOFF_M = 2.35f;

    private static final Set<Celine3DView> APPLIED =
            Collections.newSetFromMap(new WeakHashMap<Celine3DView, Boolean>());
    private static final WeakHashMap<Celine3DView, PracticalLightState> PRACTICALS =
            new WeakHashMap<>();

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
            Field sceneField = Celine3DView.class.getDeclaredField("scene");
            Field lightEntityField = Celine3DView.class.getDeclaredField("lightEntity");
            Field indirectField = Celine3DView.class.getDeclaredField("indirectLight");
            engineField.setAccessible(true);
            sceneField.setAccessible(true);
            lightEntityField.setAccessible(true);
            indirectField.setAccessible(true);

            Engine engine = (Engine) engineField.get(view);
            Scene scene = (Scene) sceneField.get(view);
            int lightEntity = lightEntityField.getInt(view);
            IndirectLight indirect = (IndirectLight) indirectField.get(view);
            if (engine == null || scene == null || lightEntity == 0 || indirect == null) return;

            LightManager lights = engine.getLightManager();
            int instance = lights.getInstance(lightEntity);
            if (instance == 0) return;

            lights.setColor(instance, KEY_RED, KEY_GREEN, KEY_BLUE);
            lights.setIntensity(instance, KEY_LUX);
            lights.setShadowCaster(instance, true);
            indirect.setIntensity(INDIRECT_LUX);

            PracticalLightState practical = createPracticalLight(view, engine, scene);
            synchronized (APPLIED) {
                APPLIED.add(view);
                PRACTICALS.put(view, practical);
            }

            Celine3DDiagnostics.record(view.getContext(), "ROOM-140",
                    "Referenzraum R1/R2/R3 Licht aktiv",
                    "directionalColor=" + KEY_RED + "," + KEY_GREEN + "," + KEY_BLUE
                            + " keyIntensity=" + KEY_LUX + " shadows=true"
                            + " indirectIntensity=" + INDIRECT_LUX
                            + " practical=front_nightstand_point@" + PRACTICAL_LUMENS + "lm"
                            + " falloff=" + PRACTICAL_FALLOFF_M + "m"
                            + " · exposure/materials/camera/60k-lamp unchanged");
        } catch (Throwable error) {
            Celine3DDiagnostics.error(view.getContext(), "ROOM-149",
                    "Referenzraum R1/R2/R3 Beleuchtung FEHLER", error);
        }
    }

    private static PracticalLightState createPracticalLight(
            Celine3DView view, Engine engine, Scene scene) {
        int entity = EntityManager.get().create();
        try {
            new LightManager.Builder(LightManager.Type.POINT)
                    .position(PRACTICAL_X, PRACTICAL_Y, PRACTICAL_Z)
                    .color(1.0f, 0.58f, 0.34f)
                    .intensity(PRACTICAL_LUMENS)
                    .falloff(PRACTICAL_FALLOFF_M)
                    .castShadows(false)
                    .lightChannel(0, true)
                    .build(engine, entity);
            scene.addEntity(entity);
            PracticalLightState state = new PracticalLightState(view, engine, scene, entity);
            view.addOnAttachStateChangeListener(state);
            return state;
        } catch (Throwable error) {
            try { engine.getLightManager().destroy(entity); } catch (Throwable ignored) {}
            try { EntityManager.get().destroy(entity); } catch (Throwable ignored) {}
            throw error;
        }
    }

    private static final class PracticalLightState implements View.OnAttachStateChangeListener {
        final Celine3DView view;
        final Engine engine;
        final Scene scene;
        int entity;

        PracticalLightState(Celine3DView view, Engine engine, Scene scene, int entity) {
            this.view = view;
            this.engine = engine;
            this.scene = scene;
            this.entity = entity;
        }

        @Override public void onViewAttachedToWindow(View v) {
            // The regular production owner will call ensure() again if a rebuilt surface needs it.
        }

        @Override public void onViewDetachedFromWindow(View v) {
            destroy();
        }

        void destroy() {
            int current = entity;
            if (current == 0) return;
            entity = 0;
            view.removeOnAttachStateChangeListener(this);
            try { scene.removeEntity(current); } catch (Throwable ignored) {}
            try { engine.getLightManager().destroy(current); } catch (Throwable ignored) {}
            try { EntityManager.get().destroy(current); } catch (Throwable ignored) {}
            synchronized (APPLIED) {
                APPLIED.remove(view);
                PRACTICALS.remove(view);
            }
        }
    }
}
