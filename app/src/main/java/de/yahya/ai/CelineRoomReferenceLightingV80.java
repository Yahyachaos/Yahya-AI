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
 * R1 evidence rejected the overly orange key and then confirmed that enabling shadows on the same
 * warm-neutral directional key restores badly missing room depth. R2 raised the existing indirect
 * fill to keep Celine and the shadow side readable. R3 moved the front-nightstand practical from an
 * almost invisible point light to a focused warm spot aimed at the nearby bed mass.
 *
 * The latest inspected R3-focused HOME/CALL/HOME proof is structurally stable, but the room still
 * has a large flat yellow ceiling field that is visibly unlike the canonical warm-neutral reference.
 * The next bounded material refinement therefore changes only the already-isolated ceiling material
 * toward a coherent cream/beige response. Walls, floor, geometry, furniture transforms, camera,
 * Celine and the protected interactive 60k floor-lamp behavior remain untouched.
 */
final class CelineRoomReferenceLightingV80 {
    private static final float KEY_RED = 1.00f;
    private static final float KEY_GREEN = 0.80f;
    private static final float KEY_BLUE = 0.66f;
    private static final float KEY_LUX = 11000.0f;
    private static final float INDIRECT_LUX = 10000.0f;

    private static final float CEILING_RED = 0.88f;
    private static final float CEILING_GREEN = 0.80f;
    private static final float CEILING_BLUE = 0.72f;
    private static final float CEILING_ROUGHNESS = 0.92f;
    private static final float CEILING_REFLECTANCE = 0.38f;

    // Assembly front nightstand: (2.66, 0.609148, 0.50). Keep the practical just above the visible
    // shade after the locked room-root offset. Aim it toward the nearby bed center so its energy is
    // a localized practical-light pool rather than an omnidirectional fill.
    private static final float PRACTICAL_X = 2.66f + CelineRoomWorldContractV80.RUNTIME_OFFSET_X;
    private static final float PRACTICAL_Y = 1.28f + CelineRoomWorldContractV80.RUNTIME_OFFSET_Y;
    private static final float PRACTICAL_Z = 0.50f + CelineRoomWorldContractV80.RUNTIME_OFFSET_Z;
    private static final float PRACTICAL_DIR_X = -0.43410667f;
    private static final float PRACTICAL_DIR_Y = -0.47690592f;
    private static final float PRACTICAL_DIR_Z = -0.76427230f;
    private static final float PRACTICAL_INNER_RAD = 0.48869219f; // 28 degrees
    private static final float PRACTICAL_OUTER_RAD = 0.87266463f; // 50 degrees
    private static final float PRACTICAL_LUMENS = 3000.0f;
    private static final float PRACTICAL_FALLOFF_M = 3.0f;

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

            // Do not lock this owner onto the view until the actual room asset is available and the
            // isolated ceiling material was reached. That keeps lifecycle rebuilds fail-closed.
            if (!applyReferenceCeilingMaterial(view, engine)) return;

            PracticalLightState practical = createPracticalLight(view, engine, scene);
            synchronized (APPLIED) {
                APPLIED.add(view);
                PRACTICALS.put(view, practical);
            }

            Celine3DDiagnostics.record(view.getContext(), "ROOM-140",
                    "Referenzraum R1/R2/R3/R4 aktiv",
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
                    "Referenzraum R1/R2/R3/R4 FEHLER", error);
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
