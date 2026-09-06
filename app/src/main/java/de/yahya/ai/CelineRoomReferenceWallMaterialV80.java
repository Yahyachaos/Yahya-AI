package de.yahya.ai;

import com.google.android.filament.Colors;
import com.google.android.filament.Engine;
import com.google.android.filament.MaterialInstance;
import com.google.android.filament.RenderableManager;
import com.google.android.filament.gltfio.FilamentAsset;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Bounded, per-entity material isolation for the exact-room right wall.
 *
 * Real Candidate #1218 on the canonical 1016x813 CALL stage measured a clean right-wall witness at
 * x=760..850/y=100..220 around RGB 125/111/95, while the same Refernzbild.png witness is about
 * 135/96/61. Candidate #1219 proved the isolated owner works but the first solve overshot to about
 * 146/82/36. Interpolating each material factor from the measured #1218 -> #1219 screen response to
 * the reference target yields 0.927/0.671/0.438. Geometry/camera seams stay accepted, and the shared
 * room-shell donor remains untouched. No source GLB bytes, transforms, furniture or Celine change.
 */
final class CelineRoomReferenceWallMaterialV80 {
    private static final String ENTITY = "room_right_wall";
    private static final float RED = 0.927f;
    private static final float GREEN = 0.671f;
    private static final float BLUE = 0.438f;
    private static final float ROUGHNESS = 0.90f;
    private static final float REFLECTANCE = 0.38f;

    private static final WeakHashMap<Celine3DView, State> STATES = new WeakHashMap<>();

    private CelineRoomReferenceWallMaterialV80() {}

    static void apply(Celine3DView view, Engine engine) throws Exception {
        if (view == null || engine == null) return;
        synchronized (STATES) {
            if (STATES.containsKey(view)) return;
        }

        FilamentAsset asset = currentRoomAsset(view);
        if (asset == null) throw new IllegalStateException("right-wall material: room asset fehlt");
        int entity = asset.getFirstEntityByName(ENTITY);
        if (entity == 0) throw new IllegalStateException("right-wall material: entity fehlt");
        RenderableManager manager = engine.getRenderableManager();
        int renderable = manager.getInstance(entity);
        if (renderable == 0) throw new IllegalStateException("right-wall material: renderable fehlt");
        int count = manager.getPrimitiveCount(renderable);
        if (count <= 0) throw new IllegalStateException("right-wall material: primitives fehlen");

        List<MaterialInstance> originals = new ArrayList<>(count);
        List<MaterialInstance> replacements = new ArrayList<>(count);
        try {
            for (int primitive = 0; primitive < count; primitive++) {
                MaterialInstance original = manager.getMaterialInstanceAt(renderable, primitive);
                if (original == null) {
                    throw new IllegalStateException("right-wall material: original fehlt " + primitive);
                }
                MaterialInstance replacement = MaterialInstance.duplicate(
                        original, "v80-reference-right-wall-" + primitive);
                tune(replacement);
                originals.add(original);
                replacements.add(replacement);
                manager.setMaterialInstanceAt(renderable, primitive, replacement);
            }
            synchronized (STATES) {
                STATES.put(view, new State(engine, renderable, originals, replacements));
            }
            Celine3DDiagnostics.record(view.getContext(), "ROOM-152",
                    "Rechte Referenzwand materialisoliert",
                    "screenWitness#1219~=146/82/36 target~=135/96/61"
                            + " baseColor=" + RED + "," + GREEN + "," + BLUE
                            + " roughness=" + ROUGHNESS + " reflectance=" + REFLECTANCE
                            + " · shared shell/source GLB/transforms/camera/Celine unchanged");
        } catch (Throwable error) {
            for (int primitive = 0; primitive < originals.size(); primitive++) {
                try { manager.setMaterialInstanceAt(renderable, primitive, originals.get(primitive)); }
                catch (Throwable ignored) {}
            }
            for (MaterialInstance replacement : replacements) {
                try { engine.destroyMaterialInstance(replacement); } catch (Throwable ignored) {}
            }
            throw error;
        }
    }

    static void release(Celine3DView view) {
        State state;
        synchronized (STATES) { state = STATES.remove(view); }
        if (state == null) return;
        RenderableManager manager = state.engine.getRenderableManager();
        for (int primitive = 0; primitive < state.originals.size(); primitive++) {
            try { manager.setMaterialInstanceAt(state.renderable, primitive, state.originals.get(primitive)); }
            catch (Throwable ignored) {}
        }
        for (MaterialInstance replacement : state.replacements) {
            try { state.engine.destroyMaterialInstance(replacement); } catch (Throwable ignored) {}
        }
    }

    private static FilamentAsset currentRoomAsset(Celine3DView view) throws Exception {
        Field statesField = CelineRoomEnvironmentV80.class.getDeclaredField("STATES");
        statesField.setAccessible(true);
        Object rawStates = statesField.get(null);
        if (!(rawStates instanceof Map)) return null;
        Object state;
        Map<?, ?> states = (Map<?, ?>) rawStates;
        synchronized (states) { state = states.get(view); }
        if (state == null) return null;
        Field assetField = state.getClass().getDeclaredField("roomAsset");
        assetField.setAccessible(true);
        return (FilamentAsset) assetField.get(state);
    }

    private static void tune(MaterialInstance material) {
        if (material.getMaterial().hasParameter("baseColorFactor")) {
            material.setParameter("baseColorFactor", Colors.RgbaType.LINEAR,
                    RED, GREEN, BLUE, 1.0f);
        }
        if (material.getMaterial().hasParameter("metallicFactor")) {
            material.setParameter("metallicFactor", 0.0f);
        }
        if (material.getMaterial().hasParameter("roughnessFactor")) {
            material.setParameter("roughnessFactor", ROUGHNESS);
        }
        if (material.getMaterial().hasParameter("reflectance")) {
            material.setParameter("reflectance", REFLECTANCE);
        }
    }

    private static final class State {
        final Engine engine;
        final int renderable;
        final List<MaterialInstance> originals;
        final List<MaterialInstance> replacements;

        State(Engine engine, int renderable, List<MaterialInstance> originals,
              List<MaterialInstance> replacements) {
            this.engine = engine;
            this.renderable = renderable;
            this.originals = originals;
            this.replacements = replacements;
        }
    }
}
