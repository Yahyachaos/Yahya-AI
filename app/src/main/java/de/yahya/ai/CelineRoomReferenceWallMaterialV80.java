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
 * Bounded per-entity material isolation for the exact-room shell planes.
 *
 * Real Candidate #1223 locks the isolated right wall at RGB 135/96/61 (reference 135/96/61),
 * the isolated back wall at RGB 120/82/49 (reference 120/83/49), and the isolated left wall at
 * RGB 124/83/44 (reference 123/83/43) on canonical clean witnesses. Preserve those accepted tuples.
 * The next broad shell residual is the exposed left floor at x=140..200/y=550..590: current median
 * RGB 78/60/46 versus reference 77/43/14. The floor's existing environment factor is
 * 0.48/0.38/0.30; applying the measured target/current raster ratios gives the bounded candidate
 * 0.474/0.272/0.091. Roughness/reflectance stay at the existing floor values 0.62/0.45.
 * Each corrected surface receives its own duplicate material; the shared shell donor remains
 * untouched. No source GLB bytes, transforms, camera, furniture or Celine change.
 */
final class CelineRoomReferenceWallMaterialV80 {
    private static final String RIGHT_ENTITY = "room_right_wall";
    private static final float RIGHT_RED = 0.927f;
    private static final float RIGHT_GREEN = 0.671f;
    private static final float RIGHT_BLUE = 0.438f;

    private static final String BACK_ENTITY = "room_back_wall";
    private static final float BACK_RED = 0.813f;
    private static final float BACK_GREEN = 0.569f;
    private static final float BACK_BLUE = 0.346f;

    private static final String LEFT_ENTITY = "room_left_wall";
    private static final float LEFT_RED = 0.853f;
    private static final float LEFT_GREEN = 0.584f;
    private static final float LEFT_BLUE = 0.317f;

    private static final String FLOOR_ENTITY = "room_floor";
    private static final float FLOOR_RED = 0.474f;
    private static final float FLOOR_GREEN = 0.272f;
    private static final float FLOOR_BLUE = 0.091f;

    private static final float WALL_ROUGHNESS = 0.90f;
    private static final float WALL_REFLECTANCE = 0.38f;
    private static final float FLOOR_ROUGHNESS = 0.62f;
    private static final float FLOOR_REFLECTANCE = 0.45f;

    private static final WeakHashMap<Celine3DView, WallState> STATES = new WeakHashMap<>();

    private CelineRoomReferenceWallMaterialV80() {}

    static void apply(Celine3DView view, Engine engine) throws Exception {
        if (view == null || engine == null) return;
        synchronized (STATES) {
            if (STATES.containsKey(view)) return;
        }

        FilamentAsset asset = currentRoomAsset(view);
        if (asset == null) throw new IllegalStateException("reference shell material: room asset fehlt");
        Entry right = null;
        Entry back = null;
        Entry left = null;
        Entry floor = null;
        try {
            right = applyEntity(asset, engine, RIGHT_ENTITY,
                    RIGHT_RED, RIGHT_GREEN, RIGHT_BLUE,
                    WALL_ROUGHNESS, WALL_REFLECTANCE, "right-wall");
            back = applyEntity(asset, engine, BACK_ENTITY,
                    BACK_RED, BACK_GREEN, BACK_BLUE,
                    WALL_ROUGHNESS, WALL_REFLECTANCE, "back-wall");
            left = applyEntity(asset, engine, LEFT_ENTITY,
                    LEFT_RED, LEFT_GREEN, LEFT_BLUE,
                    WALL_ROUGHNESS, WALL_REFLECTANCE, "left-wall");
            floor = applyEntity(asset, engine, FLOOR_ENTITY,
                    FLOOR_RED, FLOOR_GREEN, FLOOR_BLUE,
                    FLOOR_ROUGHNESS, FLOOR_REFLECTANCE, "floor");
            synchronized (STATES) {
                STATES.put(view, new WallState(engine, right, back, left, floor));
            }
            Celine3DDiagnostics.record(view.getContext(), "ROOM-152",
                    "Referenz-Shell materialisoliert",
                    "right#1223=135/96/61 target=135/96/61 base="
                            + RIGHT_RED + "," + RIGHT_GREEN + "," + RIGHT_BLUE
                            + " · back#1223=120/82/49 target=120/83/49 base="
                            + BACK_RED + "," + BACK_GREEN + "," + BACK_BLUE
                            + " · left#1223=124/83/44 target=123/83/43 base="
                            + LEFT_RED + "," + LEFT_GREEN + "," + LEFT_BLUE
                            + " · floorCurrent=78/60/46 target=77/43/14 base="
                            + FLOOR_RED + "," + FLOOR_GREEN + "," + FLOOR_BLUE
                            + " · shared shell/source GLB/transforms/camera/Celine unchanged");
        } catch (Throwable error) {
            releaseEntry(engine, floor);
            releaseEntry(engine, left);
            releaseEntry(engine, back);
            releaseEntry(engine, right);
            throw error;
        }
    }

    static void release(Celine3DView view) {
        WallState state;
        synchronized (STATES) { state = STATES.remove(view); }
        if (state == null) return;
        releaseEntry(state.engine, state.floor);
        releaseEntry(state.engine, state.left);
        releaseEntry(state.engine, state.back);
        releaseEntry(state.engine, state.right);
    }

    private static Entry applyEntity(FilamentAsset asset, Engine engine, String entityName,
                                     float red, float green, float blue,
                                     float roughness, float reflectance, String suffix) {
        int entity = asset.getFirstEntityByName(entityName);
        if (entity == 0) throw new IllegalStateException(entityName + " material: entity fehlt");
        RenderableManager manager = engine.getRenderableManager();
        int renderable = manager.getInstance(entity);
        if (renderable == 0) throw new IllegalStateException(entityName + " material: renderable fehlt");
        int count = manager.getPrimitiveCount(renderable);
        if (count <= 0) throw new IllegalStateException(entityName + " material: primitives fehlen");

        List<MaterialInstance> originals = new ArrayList<>(count);
        List<MaterialInstance> replacements = new ArrayList<>(count);
        try {
            for (int primitive = 0; primitive < count; primitive++) {
                MaterialInstance original = manager.getMaterialInstanceAt(renderable, primitive);
                if (original == null) {
                    throw new IllegalStateException(entityName + " material: original fehlt " + primitive);
                }
                MaterialInstance replacement = MaterialInstance.duplicate(
                        original, "v80-reference-" + suffix + "-" + primitive);
                tune(replacement, red, green, blue, roughness, reflectance);
                originals.add(original);
                replacements.add(replacement);
                manager.setMaterialInstanceAt(renderable, primitive, replacement);
            }
            return new Entry(renderable, originals, replacements);
        } catch (Throwable error) {
            Entry partial = new Entry(renderable, originals, replacements);
            releaseEntry(engine, partial);
            throw error;
        }
    }

    private static void releaseEntry(Engine engine, Entry entry) {
        if (engine == null || entry == null) return;
        RenderableManager manager = engine.getRenderableManager();
        for (int primitive = 0; primitive < entry.originals.size(); primitive++) {
            try { manager.setMaterialInstanceAt(entry.renderable, primitive, entry.originals.get(primitive)); }
            catch (Throwable ignored) {}
        }
        for (MaterialInstance replacement : entry.replacements) {
            try { engine.destroyMaterialInstance(replacement); } catch (Throwable ignored) {}
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

    private static void tune(MaterialInstance material, float red, float green, float blue,
                             float roughness, float reflectance) {
        if (material.getMaterial().hasParameter("baseColorFactor")) {
            material.setParameter("baseColorFactor", Colors.RgbaType.LINEAR,
                    red, green, blue, 1.0f);
        }
        if (material.getMaterial().hasParameter("metallicFactor")) {
            material.setParameter("metallicFactor", 0.0f);
        }
        if (material.getMaterial().hasParameter("roughnessFactor")) {
            material.setParameter("roughnessFactor", roughness);
        }
        if (material.getMaterial().hasParameter("reflectance")) {
            material.setParameter("reflectance", reflectance);
        }
    }

    private static final class Entry {
        final int renderable;
        final List<MaterialInstance> originals;
        final List<MaterialInstance> replacements;

        Entry(int renderable, List<MaterialInstance> originals,
              List<MaterialInstance> replacements) {
            this.renderable = renderable;
            this.originals = originals;
            this.replacements = replacements;
        }
    }

    private static final class WallState {
        final Engine engine;
        final Entry right;
        final Entry back;
        final Entry left;
        final Entry floor;

        WallState(Engine engine, Entry right, Entry back, Entry left, Entry floor) {
            this.engine = engine;
            this.right = right;
            this.back = back;
            this.left = left;
            this.floor = floor;
        }
    }
}
