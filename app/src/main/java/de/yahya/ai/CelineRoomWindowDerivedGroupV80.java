package de.yahya.ai;

import android.opengl.Matrix;

import com.google.android.filament.Engine;
import com.google.android.filament.TransformManager;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Measurement-owned affine correction for the derived v80 window layers.
 *
 * The immutable room_window_drapes source mesh is intentionally hidden after the derived backdrop,
 * side curtains, sheers and fold facets are created. Therefore moving only room_window_drapes does
 * not move the visible window. All corrections below are applied uniformly to the generated window
 * entities so backdrop, curtains, sheers and folds cannot drift apart.
 *
 * Proof #1285 is the corrected expanded baseline. Its exact 1016x813 CALL window raster is
 * x=0.210630..0.593504 / y=0.086101..0.479705 versus canonical x=0.205..0.588 /
 * y=0.086..0.477. Width and vertical placement are already close; the reliable remaining
 * horizontal residual is a nearly pure -0.005567 normalized center shift.
 *
 * The same derived-group owner has an observed screen/world response: the prior +0.071743m
 * center-X move shifted the visible center +0.0093504 normalized (#1266 -> #1285). Refit the same
 * base affine owner, not a stacked post-transform: -0.0055669 therefore maps to -0.042707m and
 * NEW_CENTER_X=-0.864824. Existing X/Y scales, center-Y, Z, materials, camera, Celine and immutable
 * source-GLB bytes remain unchanged.
 */
final class CelineRoomWindowDerivedGroupV80 {
    private static final float OLD_CENTER_X = -0.605f;
    private static final float NEW_CENTER_X = -0.864824f;
    private static final float HORIZONTAL_SCALE = 1.03095712f;

    private static final float OLD_CENTER_Y = 1.20f;
    private static final float NEW_CENTER_Y = 1.753992f;
    private static final float VERTICAL_SCALE = 0.80470520f;

    private static final int EXPECTED_DERIVED_ENTITY_COUNT = 12;

    private static final WeakHashMap<Celine3DView, Boolean> APPLIED = new WeakHashMap<>();

    private CelineRoomWindowDerivedGroupV80() {}

    static void apply(Celine3DView view, Engine engine) throws Exception {
        if (view == null || engine == null) return;

        boolean adjustWindow;
        synchronized (APPLIED) {
            adjustWindow = !APPLIED.containsKey(view);
        }
        if (adjustWindow) {
            TransformManager transforms = engine.getTransformManager();
            int adjusted = 0;
            adjusted += adjustState(view, transforms, CelineRoomWindowBackdropV80.class);
            adjusted += adjustState(view, transforms, CelineRoomWindowCurtainFillV80.class);
            adjusted += adjustState(view, transforms, CelineRoomWindowSheerFillV80.class);
            adjusted += adjustState(view, transforms, CelineRoomWindowFoldDetailV80.class);
            if (adjusted != EXPECTED_DERIVED_ENTITY_COUNT) {
                throw new IllegalStateException(
                        "derived window correction expected " + EXPECTED_DERIVED_ENTITY_COUNT
                                + " entities, adjusted=" + adjusted);
            }

            synchronized (APPLIED) { APPLIED.put(view, Boolean.TRUE); }
            Celine3DDiagnostics.record(view.getContext(), "ROOM-143",
                    "Abgeleitete Fenstergruppe rastergenau nachvermessen",
                    "CALL#1285 visibleX=0.210630..0.593504 targetX=0.205..0.588"
                            + " visibleY=0.086101..0.479705 targetY=0.086..0.477"
                            + " scaleX=" + HORIZONTAL_SCALE
                            + " centerX=" + OLD_CENTER_X + "->" + NEW_CENTER_X
                            + " scaleY=" + VERTICAL_SCALE
                            + " centerY=" + OLD_CENTER_Y + "->" + NEW_CENTER_Y
                            + " entities=" + adjusted
                            + " · Z/materials/camera/Celine/source-GLB unchanged");
        }

        CelineRoomForegroundLaptopV80.apply(view, engine);
        CelineRoomForegroundPlantV80.apply(view, engine);
        CelineRoomDerivedRasterResidualV80.apply(view, engine);
        CelineRoomReferenceWallMaterialV80.apply(view, engine);
        CelineRoomVisibleRasterResidualV80.apply(view, engine);
        // Real Candidate #1264 proves the smooth derived rug surface does not solve the visible
        // horizontal banding (row-jump 2.3925 -> 2.4100 versus reference ~0.69). Keep the immutable
        // source rug and accepted rug TRS, but do not wire that rejected runtime strategy.
    }

    static void release(Celine3DView view) {
        CelineRoomVisibleRasterResidualV80.release(view);
        CelineRoomReferenceWallMaterialV80.release(view);
        CelineRoomDerivedRasterResidualV80.release(view);
        CelineRoomForegroundPlantV80.release(view);
        CelineRoomForegroundLaptopV80.release(view);
        synchronized (APPLIED) { APPLIED.remove(view); }
    }

    private static int adjustState(Celine3DView view, TransformManager transforms,
                                   Class<?> owner) throws Exception {
        Field statesField = owner.getDeclaredField("STATES");
        statesField.setAccessible(true);
        Object rawStates = statesField.get(null);
        if (!(rawStates instanceof Map)) {
            throw new IllegalStateException(owner.getSimpleName() + " STATES is not a map");
        }
        Object state = ((Map<?, ?>) rawStates).get(view);
        if (state == null) {
            throw new IllegalStateException(owner.getSimpleName() + " state missing");
        }

        try {
            Field entityField = state.getClass().getDeclaredField("entity");
            entityField.setAccessible(true);
            return adjustEntity(transforms, entityField.getInt(state));
        } catch (NoSuchFieldException noSingleEntity) {
            Field entitiesField = state.getClass().getDeclaredField("entities");
            entitiesField.setAccessible(true);
            Object entities = entitiesField.get(state);
            int count = 0;
            if (entities instanceof int[]) {
                for (int entity : (int[]) entities) count += adjustEntity(transforms, entity);
                return count;
            }
            if (entities instanceof List) {
                for (Object value : (List<?>) entities) {
                    if (!(value instanceof Number)) {
                        throw new IllegalStateException(owner.getSimpleName() + " entity is not numeric");
                    }
                    count += adjustEntity(transforms, ((Number) value).intValue());
                }
                return count;
            }
            throw new IllegalStateException(owner.getSimpleName() + " entities unsupported");
        }
    }

    private static int adjustEntity(TransformManager transforms, int entity) {
        if (entity == 0) return 0;
        int instance = transforms.getInstance(entity);
        if (instance == 0) return 0;

        float[] local = transforms.getTransform(instance, new float[16]);
        local[12] = NEW_CENTER_X + (local[12] - OLD_CENTER_X) * HORIZONTAL_SCALE;
        local[13] = NEW_CENTER_Y + (local[13] - OLD_CENTER_Y) * VERTICAL_SCALE;

        float[] scale = new float[16];
        float[] adjusted = new float[16];
        Matrix.setIdentityM(scale, 0);
        Matrix.scaleM(scale, 0, HORIZONTAL_SCALE, VERTICAL_SCALE, 1.0f);
        Matrix.multiplyMM(adjusted, 0, local, 0, scale, 0);
        transforms.setTransform(instance, adjusted);
        return 1;
    }
}
