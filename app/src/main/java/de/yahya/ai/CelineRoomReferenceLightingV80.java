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
 * Proof #51 is structurally stable after the softened window pass, but manual HOME/CALL/HOME review
 * still shows Celine under a strong brown/orange cast even with the 1.00/0.95/0.90 key. Keep intensity,
 * fill, practical, room materials, geometry, camera and Celine fixed; this bounded witness changes only
 * the directional key chromaticity to neutral white so remaining warmth comes from the room/practical.
 */
final class CelineRoomReferenceLightingV80 {
    private static final float KEY_RED = 1.00f;
    private static final float KEY_GREEN = 1.00f;
    private static final float KEY_BLUE = 1.00f;
    private static final float KEY_LUX = 5000.0f;
    private static final float INDIRECT_LUX = 8000.0f;

    private static final float CEILING_RED = 0.88f;
    private static final float CEILING_GREEN = 0.80f;
    private static final float CEILING_BLUE = 0.72f;
    private static final float CEILING_ROUGHNESS = 0.92f;
    private static final float CEILING_REFLECTANCE = 0.38f;

    private static final float WINDOW_RED = 0.88f;
    private static final float WINDOW_GREEN = 0.84f;
    private static final float WINDOW_BLUE = 0.80f;

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

            FilamentAsset roomAsset = currentRoomAsset(view);
            if (roomAsset == null) return;
            applyReferenceCeilingMaterial(roomAsset, engine);
            applyReferenceWindowMaterial(roomAsset, engine);

            PracticalLightState practical = createPracticalLight(view, engine, scene);
            synchronized (APPLIED) {
                APPLIED.add(view);
                PRACTICALS.put(view, practical);
            }

            Celine3DDiagnostics.record(view.getContext(), "ROOM-140",
                    "Referenzraum neutral-key aktiv",
                    "directionalColor=" + KEY_RED + "," + KEY_GREEN + "," + KEY_BLUE
                            + " keyIntensity=" + KEY_LUX + " shadows=true"
                            + " indirectIntensity=" + INDIRECT_LUX
                            + " ceiling=" + CEILING_RED + "," + CEILING_GREEN + "," + CEILING_BLUE
                            + " windowFactor=" + WINDOW_RED + "," + WINDOW_GREEN + "," + WINDOW_BLUE
                            + " practical=front_nightstand_focused_spot@" + PRACTICAL_LUMENS + "lm"
                            + " · geometry/camera/Celine/60k-lamp unchanged");
        } catch (Throwable error) {
            Celine3DDiagnostics.error(view.getContext(), "ROOM-149",
                    "Referenzraum neutral-key FEHLER", error);
        }
    }

    private static FilamentAsset currentRoomAsset(Celine3DView view) throws Exception {
        Field statesField = CelineRoomEnvironmentV80.class.getDeclaredField("STATES");
        statesField.setAccessible(true);
        Object rawStates = statesField.get(null);
        if (!(rawStates instanceof Map)) return null;
        Object state = ((Map<?, ?>) rawStates).get(view);
        if (state == null) return null;
        Field roomAssetField = state.getClass().getDeclaredField("roomAsset");
        roomAssetField.setAccessible(true);
        return (FilamentAsset) roomAssetField.get(state);
    }

    private static void applyReferenceCeilingMaterial(FilamentAsset asset, Engine engine) {
        MaterialInstance material = singleMaterial(asset, engine, "room_ceiling", "ceiling");
        material.setParameter("baseColorFactor", Colors.RgbaType.LINEAR,
                CEILING_RED, CEILING_GREEN, CEILING_BLUE, 1.0f);
        material.setParameter("metallicFactor", 0.0f);
        material.setParameter("roughnessFactor", CEILING_ROUGHNESS);
        material.setParameter("reflectance", CEILING_REFLECTANCE);
    }

    private static void applyReferenceWindowMaterial(FilamentAsset asset, Engine engine) {
        MaterialInstance material = singleMaterial(asset, engine, "room_window_drapes", "window");
        material.setParameter("baseColorFactor", Colors.RgbaType.LINEAR,
                WINDOW_RED, WINDOW_GREEN, WINDOW_BLUE, 1.0f);
    }

    private static MaterialInstance singleMaterial(
            FilamentAsset asset, Engine engine, String entityName, String diagnosticName) {
        int entity = asset.getFirstEntityByName(entityName);
        if (entity == 0) throw new IllegalStateException(diagnosticName + " entity fehlt");
        RenderableManager manager = engine.getRenderableManager();
        int renderable = manager.getInstance(entity);
        if (renderable == 0) throw new IllegalStateException(diagnosticName + " renderable fehlt");
        if (manager.getPrimitiveCount(renderable) != 1) {
            throw new IllegalStateException(diagnosticName + " primitive count != 1");
        }
        MaterialInstance material = manager.getMaterialInstanceAt(renderable, 0);
        if (material == null) throw new IllegalStateException(diagnosticName + " material fehlt");
        return material;
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
