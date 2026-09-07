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
 * Real Candidate #1179 reopened the window after the earlier full-mesh projection solve. The first
 * derived-group raster correction deliberately replaced hidden-mesh bounds with visible pixels.
 * Proof #1262/#1266 now supply the stable post-correction residual on the exact 1016x813 CALL stage:
 * x=199..599 px (0.195866..0.589567) and y=61..397 px (0.075031..0.488315), versus canonical
 * x=0.205..0.588 and y=0.086..0.477. The visible center is already vertically correct, while both
 * width and height are slightly oversized and horizontal center remains 0.003783 too far left.
 *
 * Refit the same base affine owner instead of stacking another transform. Scale X by the measured
 * width ratio 0.383/0.3937008 and scale Y by 0.391/0.4132841. For horizontal translation use the
 * observed #1179 -> #1262 screen/world response of the same owner: the prior -0.288860 m center-X
 * move shifted visible center by -0.0152335 normalized, so +0.0037835 requires +0.071743 m. This is
 * a bounded raster residual correction only. Z, materials, camera, Celine and immutable source-GLB
 * bytes remain untouched.
 */
final class CelineRoomWindowDerivedGroupV80 {
    private static final float OLD_CENTER_X = -0.605f;
    private static final float NEW_CENTER_X = -0.822117f;
    private static final float HORIZONTAL_SCALE = 1.03095712f;

    private static final float OLD_CENTER_Y = 1.20f;
    private static final float NEW_CENTER_Y = 1.753992f;
    private static final float VERTICAL_SCALE = 0.80470520f;

    // The accepted backdrop used to contribute one entity. Candidate a80dc42 intentionally splits
    // that same accepted backdrop into two panes, so the complete derived window group is now 12.
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
                    "CALL#1266 visibleX=0.195866..0.589567 targetX=0.205..0.588"
                            + " visibleY=0.075031..0.488315 targetY=0.086..0.477"
                            + " scaleX=" + HORIZONTAL_SCALE
                            + " centerX=" + OLD_CENTER_X + "->" + NEW_CENTER_X
                            + " scaleY=" + VERTICAL_SCALE
                            + " centerY=" + OLD_CENTER_Y + "->" + NEW_CENTER_Y
                            + " entities=" + adjusted
                            + " · Z/materials/camera/Celine/source-GLB unchanged");
        }

        // #1183 shows the reference laptop is a separate missing foreground object, not part of the
        // immutable table source. Both foreground details retry independently from the one-time window
        // affine correction so any derived-detail failure cannot stack accepted window transforms.
        CelineRoomForegroundLaptopV80.apply(view, engine);
        CelineRoomForegroundPlantV80.apply(view, engine);
        // Real Candidate #1218 isolates the next largest remaining broad-shell residual to the right
        // wall. Keep that material correction in the same already-established room post-pass, but let
        // its own owner duplicate only the right-wall material so no shared shell donor is mutated.
        CelineRoomReferenceWallMaterialV80.apply(view, engine);
        // Real Candidate #1264 proves the smooth derived rug surface does not solve the visible
        // horizontal banding (row-jump 2.3925 -> 2.4100 versus reference ~0.69). Keep the immutable
        // source rug and accepted rug TRS, but do not wire that rejected runtime strategy.
    }

    static void release(Celine3DView view) {
        CelineRoomReferenceWallMaterialV80.release(view);
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
