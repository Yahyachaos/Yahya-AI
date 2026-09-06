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
 * Bounded per-entity material isolation for the exact-room shell planes and the current largest
 * reference-material residual.
 *
 * Real Candidate #1224 locks the isolated right wall at RGB 135/96/61 (reference 135/96/61),
 * the isolated back wall at RGB 120/82/49 (reference 120/83/49), the isolated left wall at
 * RGB 124/83/44 (reference 123/83/43), and the exposed floor witness x=140..200/y=550..590
 * at RGB 77/43/14 (reference 77/43/14). Preserve those accepted tuples.
 * Real Candidate #1225 measured the first isolated ceiling candidate 1.197/0.857/0.446 at
 * RGB 172/121/62 versus reference 152/110/72 on x=450..650/y=15..70. Combined with the
 * pre-isolation witness RGB 127/113/100 at factor 1.0/0.88/0.62, per-channel interpolation
 * gives the bounded second candidate 1.109/0.889/0.492. Roughness/reflectance stay at the
 * existing wall values 0.90/0.38.
 *
 * Real Candidate #1248 makes the rug the largest remaining broad semantic/material mismatch.
 * On the exact 1016x813 CALL stage, the clear left-rug witness x=240..430/y=470..650 is
 * RGB 113/88/68 while the same normalized reference witness is 152/110/76. More importantly,
 * the source rug renders as high-contrast horizontal strips with dark gaps whereas Refernzbild.png
 * is a dense, continuous warm pile. Preserve the already accepted rug TRS and immutable source GLB,
 * but replace only the runtime rug material with an opaque duplicate of the already-isolated floor
 * material. Using the accepted horizontal floor response (0.474/0.272/0.091 -> 77/43/14), the
 * target witness solves to the bounded warm-pile base 0.936/0.696/0.494. This deliberately removes
 * the source color/alpha map from the render path without modifying source bytes or geometry.
 *
 * Real Candidate #1251 exposed a detach-order lifecycle defect: the room asset can be released before
 * this material owner receives its view-detach callback. A stored RenderableManager instance handle is
 * therefore not safe to mutate during release. Keep the stable entity id and re-resolve the current
 * renderable instance before restoring originals; if the entity no longer has a renderable component,
 * only destroy this owner's replacement material instances.
 *
 * Each corrected surface receives its own duplicate material. No source GLB bytes, transforms,
 * camera, furniture TRS or Celine identity/rig change.
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

    private static final String CEILING_ENTITY = "room_ceiling";
    private static final float CEILING_RED = 1.109f;
    private static final float CEILING_GREEN = 0.889f;
    private static final float CEILING_BLUE = 0.492f;

    private static final String RUG_ENTITY = "room_rug";
    private static final float RUG_RED = 0.936f;
    private static final float RUG_GREEN = 0.696f;
    private static final float RUG_BLUE = 0.494f;

    private static final float WALL_ROUGHNESS = 0.90f;
    private static final float WALL_REFLECTANCE = 0.38f;
    private static final float FLOOR_ROUGHNESS = 0.62f;
    private static final float FLOOR_REFLECTANCE = 0.45f;
    private static final float RUG_ROUGHNESS = 0.96f;
    private static final float RUG_REFLECTANCE = 0.28f;

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
        Entry ceiling = null;
        Entry rug = null;
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
            ceiling = applyEntity(asset, engine, CEILING_ENTITY,
                    CEILING_RED, CEILING_GREEN, CEILING_BLUE,
                    WALL_ROUGHNESS, WALL_REFLECTANCE, "ceiling");
            rug = applyOpaqueEntityFromDonor(asset, engine, RUG_ENTITY, FLOOR_ENTITY,
                    RUG_RED, RUG_GREEN, RUG_BLUE,
                    RUG_ROUGHNESS, RUG_REFLECTANCE, "rug-warm-pile");
            synchronized (STATES) {
                STATES.put(view, new WallState(engine, right, back, left, floor, ceiling, rug));
            }
            Celine3DDiagnostics.record(view.getContext(), "ROOM-152",
                    "Referenz-Shell und Rug materialisoliert",
                    "right#1224=135/96/61 target=135/96/61 base="
                            + RIGHT_RED + "," + RIGHT_GREEN + "," + RIGHT_BLUE
                            + " · back#1224=120/82/49 target=120/83/49 base="
                            + BACK_RED + "," + BACK_GREEN + "," + BACK_BLUE
                            + " · left#1224=124/83/44 target=123/83/43 base="
                            + LEFT_RED + "," + LEFT_GREEN + "," + LEFT_BLUE
                            + " · floor#1224=77/43/14 target=77/43/14 base="
                            + FLOOR_RED + "," + FLOOR_GREEN + "," + FLOOR_BLUE
                            + " · ceiling#1225=172/121/62 target=152/110/72 base="
                            + CEILING_RED + "," + CEILING_GREEN + "," + CEILING_BLUE
                            + " · rug#1248=113/88/68 target=152/110/76 base="
                            + RUG_RED + "," + RUG_GREEN + "," + RUG_BLUE
                            + " donor=isolatedFloor opaque=true sourceMapBypassed=true"
                            + " · source GLB/transforms/camera/Celine unchanged");
        } catch (Throwable error) {
            releaseEntry(engine, rug);
            releaseEntry(engine, ceiling);
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
        releaseEntry(state.engine, state.rug);
        releaseEntry(state.engine, state.ceiling);
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
            return new Entry(entity, renderable, originals, replacements);
        } catch (Throwable error) {
            Entry partial = new Entry(entity, renderable, originals, replacements);
            releaseEntry(engine, partial);
            throw error;
        }
    }

    private static Entry applyOpaqueEntityFromDonor(FilamentAsset asset, Engine engine,
                                                     String entityName, String donorEntityName,
                                                     float red, float green, float blue,
                                                     float roughness, float reflectance,
                                                     String suffix) {
        RenderableManager manager = engine.getRenderableManager();
        int entity = asset.getFirstEntityByName(entityName);
        if (entity == 0) throw new IllegalStateException(entityName + " material: entity fehlt");
        int renderable = manager.getInstance(entity);
        if (renderable == 0) throw new IllegalStateException(entityName + " material: renderable fehlt");
        int count = manager.getPrimitiveCount(renderable);
        if (count <= 0) throw new IllegalStateException(entityName + " material: primitives fehlen");

        int donorEntity = asset.getFirstEntityByName(donorEntityName);
        if (donorEntity == 0) {
            throw new IllegalStateException(entityName + " material: donor entity fehlt " + donorEntityName);
        }
        int donorRenderable = manager.getInstance(donorEntity);
        if (donorRenderable == 0 || manager.getPrimitiveCount(donorRenderable) <= 0) {
            throw new IllegalStateException(entityName + " material: donor renderable fehlt " + donorEntityName);
        }
        MaterialInstance donor = manager.getMaterialInstanceAt(donorRenderable, 0);
        if (donor == null) {
            throw new IllegalStateException(entityName + " material: donor material fehlt " + donorEntityName);
        }

        List<MaterialInstance> originals = new ArrayList<>(count);
        List<MaterialInstance> replacements = new ArrayList<>(count);
        try {
            for (int primitive = 0; primitive < count; primitive++) {
                MaterialInstance original = manager.getMaterialInstanceAt(renderable, primitive);
                if (original == null) {
                    throw new IllegalStateException(entityName + " material: original fehlt " + primitive);
                }
                MaterialInstance replacement = MaterialInstance.duplicate(
                        donor, "v80-reference-" + suffix + "-" + primitive);
                tune(replacement, red, green, blue, roughness, reflectance);
                originals.add(original);
                replacements.add(replacement);
                manager.setMaterialInstanceAt(renderable, primitive, replacement);
            }
            return new Entry(entity, renderable, originals, replacements);
        } catch (Throwable error) {
            Entry partial = new Entry(entity, renderable, originals, replacements);
            releaseEntry(engine, partial);
            throw error;
        }
    }

    private static void releaseEntry(Engine engine, Entry entry) {
        if (engine == null || entry == null) return;
        RenderableManager manager = engine.getRenderableManager();
        int renderable = 0;
        try { renderable = manager.getInstance(entry.entity); }
        catch (Throwable ignored) {}
        if (renderable != 0) {
            int primitiveCount = 0;
            try { primitiveCount = manager.getPrimitiveCount(renderable); }
            catch (Throwable ignored) {}
            int restoreCount = Math.min(primitiveCount, entry.originals.size());
            for (int primitive = 0; primitive < restoreCount; primitive++) {
                try { manager.setMaterialInstanceAt(renderable, primitive, entry.originals.get(primitive)); }
                catch (Throwable ignored) {}
            }
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
        final int entity;
        final int renderable;
        final List<MaterialInstance> originals;
        final List<MaterialInstance> replacements;

        Entry(int entity, int renderable, List<MaterialInstance> originals,
              List<MaterialInstance> replacements) {
            this.entity = entity;
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
        final Entry ceiling;
        final Entry rug;

        WallState(Engine engine, Entry right, Entry back, Entry left, Entry floor, Entry ceiling,
                  Entry rug) {
            this.engine = engine;
            this.right = right;
            this.back = back;
            this.left = left;
            this.floor = floor;
            this.ceiling = ceiling;
            this.rug = rug;
        }
    }
}
