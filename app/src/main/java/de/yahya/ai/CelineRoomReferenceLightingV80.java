package de.yahya.ai;

import android.view.View;

import com.google.android.filament.Colors;
import com.google.android.filament.Engine;
import com.google.android.filament.EntityManager;
import com.google.android.filament.IndirectLight;
import com.google.android.filament.LightManager;
import com.google.android.filament.MaterialInstance;
import com.google.android.filament.RenderableManager;
import com.google.android.filament.Scene;
import com.google.android.filament.gltfio.FilamentAsset;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * v80 reference-realism owner for the bounded reference-room passes.
 *
 * R1 evidence rejected the overly orange key and confirmed that directional shadows restore missing
 * depth. R2 raised indirect fill so Celine and shadow-side surfaces stayed readable. R3 converted the
 * nearly invisible front-nightstand point light into a focused warm practical aimed at the bed.
 * R4 neutralized the previously flat yellow ceiling toward the canonical cream/beige reference.
 *
 * Proof #46 with the warm-neutral 5000-lux directional key is structurally stable, but manual HOME,
 * CALL and HOME-return inspection still shows the room washed by broad global fill: the back wall,
 * window surround, chair, Celine and bed remain too uniformly readable for the canonical evening
 * reference. Keep the accepted 5000-lux key, focused practical, materials, geometry, camera and
 * Celine fixed; lower only the existing indirect fill from 9000 to 6500 so local light falloff and
 * darker room zones can become visible without returning to the earlier underfilled 8000-era key.
 */
final class CelineRoomReferenceLightingV80 {
    private static final float KEY_RED = 1.00f;
    private static final float KEY_GREEN = 0.90f;
    private static final float KEY_BLUE = 0.82f;
    private static final float KEY_LUX = 5000.0f;
    private static final float INDIRECT_LUX = 6500.0f;

    private static final float CEILING_RED = 0.88f;
    private static final float CEILING_GREEN = 0.80f;
    private static final float CEILING_BLUE = 0.72f;
    private static final float CEILING_ROUGHNESS = 0.92f;
    private static final float CEILING_REFLECTANCE = 0.38f;

    private static final float PRACTICAL_X = 2.66f + CelineRoomWorldContractV80.RUNTIME_OFFSET_X;
    private static final float PRACTICAL_Y = 1.28f + CelineRoomWorldContractV80.RUNTIME_OFFSET_Y;
    private static final float PRACTICAL_Z = 0.50f + CelineRoomWorldContractV80.RUNTIME_OFFSET_Z;
    private static final float PRACTICAL_DIR_X = -0.43410667f;
    private static final float PRACTICAL_DIR_Y = -0.47690592f;
    private static final float PRACTICAL_DIR_Z = -0.76427230f;
    private static final float PRACTICAL_INNER_RAD = 0.48869219f; // 28 degrees
    private static final float PRACTICAL_OUTER_RAD = 0.87266463f; // 50 degrees
    private static final float PRACTICAL_LUMENS = 6000.0f;
    private static final float PRACTICAL_FALLOFF_M = 3.0f;

    private static final Set<Celine3DView> APPLIED =
            Collections.newSetFromMap(new WeakHashMap<Celine3DView, Boolean>());
    private static final WeakHashMap<Celine3DView, PracticalLightState> PRACTICALS =
            new WeakHashMap<>();

    private CelineRoomReferenceLightingV80() {}

    static void ensure(Celine3DView view) {
        if (view == null) return;

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

            if (!applyReferenceCeilingMaterial(view, engine)) return;

            PracticalLightState practical = createPracticalLight(view, engine, scene);
            synchronized (APPLIED) {
                APPLIED.add(view);
                PRACTICALS.put(view, practical);
            }

            Celine3DDiagnostics.record(view.getContext(), "ROOM-140",
                    "Referenzraum local-depth aktiv",
                    "directionalColor=" + KEY_RED + "," + KEY_GREEN + "," + KEY_BLUE
                            + " keyIntensity=" + KEY_LUX + " shadows=true"
                            + " indirectIntensity=" + INDIRECT_LUX
                            + " ceiling=" + CEILING_RED + "," + CEILING_GREEN + "," + CEILING_BLUE
                            + " practical=front_nightstand_focused_spot@" + PRACTICAL_LUMENS + "lm"
                            + " falloff=" + PRACTICAL_FALLOFF_M + "m"
                            + " direction=" + PRACTICAL_DIR_X + "," + PRACTICAL_DIR_Y + "," + PRACTICAL_DIR_Z
                            + " coneRad=" + PRACTICAL_INNER_RAD + "/" + PRACTICAL_OUTER_RAD
                            + " · walls/floor/camera/Celine/60k-lamp unchanged");
        } catch (Throwable error) {
            Celine3DDiagnostics.error(view.getContext(), "ROOM-149",
                    "Referenzraum local-depth FEHLER", error);
        }
    }

    private static boolean applyReferenceCeilingMaterial(Celine3DView view, Engine engine)
            throws Exception {
        Field statesField = CelineRoomEnvironmentV80.class.getDeclaredField("STATES");
        statesField.setAccessible(true);
        Object rawStates = statesField.get(null);
        if (!(rawStates instanceof Map)) return false;
        Object state = ((Map<?, ?>) rawStates).get(view);
        if (state == null) return false;

        Field roomAssetField = state.getClass().getDeclaredField("roomAsset");
        roomAssetField.setAccessible(true);
        FilamentAsset asset = (FilamentAsset) roomAssetField.get(state);
        if (asset == null) return false;

        int entity = asset.getFirstEntityByName("room_ceiling");
        if (entity == 0) throw new IllegalStateException("R4 ceiling entity fehlt");
        RenderableManager manager = engine.getRenderableManager();
        int renderable = manager.getInstance(entity);
        if (renderable == 0) throw new IllegalStateException("R4 ceiling renderable fehlt");
        if (manager.getPrimitiveCount(renderable) != 1) {
            throw new IllegalStateException("R4 ceiling primitive count != 1");
        }
        MaterialInstance material = manager.getMaterialInstanceAt(renderable, 0);
        if (material == null) throw new IllegalStateException("R4 ceiling material fehlt");
        material.setParameter("baseColorFactor", Colors.RgbaType.LINEAR,
                CEILING_RED, CEILING_GREEN, CEILING_BLUE, 1.0f);
        material.setParameter("metallicFactor", 0.0f);
        material.setParameter("roughnessFactor", CEILING_ROUGHNESS);
        material.setParameter("reflectance", CEILING_REFLECTANCE);
        return true;
    }

    private static PracticalLightState createPracticalLight(
            Celine3DView view, Engine engine, Scene scene) {
        int entity = EntityManager.get().create();
        try {
            new LightManager.Builder(LightManager.Type.FOCUSED_SPOT)
                    .position(PRACTICAL_X, PRACTICAL_Y, PRACTICAL_Z)
                    .direction(PRACTICAL_DIR_X, PRACTICAL_DIR_Y, PRACTICAL_DIR_Z)
                    .spotLightCone(PRACTICAL_INNER_RAD, PRACTICAL_OUTER_RAD)
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
