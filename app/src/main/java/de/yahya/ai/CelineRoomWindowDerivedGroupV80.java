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
 * Real Candidate #1179 reopened the window after the earlier full-mesh projection solve: on the
 * exact 1016x813 CALL raster the visible derived silhouette is x=0.2470..0.5689 with top y=0.1464,
 * while Refernzbild.png requires x=0.205..0.588 and top y=0.086. The source mesh is hidden, so this
 * is a derived-group raster correction, not a source-GLB transform. Refit from the base group rather
 * than stacking another post-transform: horizontal scale 0.89069767 * (0.383/0.3219) = 1.05976144.
 * The visible center must move left from 0.40795 to 0.39650; solving that shift through the accepted
 * Filament camera while accounting for the simultaneous vertical move gives centerX=-0.89386.
 *
 * The current derived backdrop top analytically projects to y=0.1445, matching the measured raster
 * top 0.1464 closely enough to identify the same owner. Keep the accepted vertical scale 0.8505675
 * and solve only group Y translation through the exact camera; centerY=1.753992 projects the backdrop
 * top to y=0.086. Z, materials, camera, Celine and all immutable source-GLB bytes remain untouched.
 */
final class CelineRoomWindowDerivedGroupV80 {
    private static final float OLD_CENTER_X = -0.605f;
    private static final float NEW_CENTER_X = -0.893860f;
    private static final float HORIZONTAL_SCALE = 1.05976144f;

    private static final float OLD_CENTER_Y = 1.20f;
    private static final float NEW_CENTER_Y = 1.753992f;
    private static final float VERTICAL_SCALE = 0.8505675f;

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
            if (adjusted != 11) {
                throw new IllegalStateException(
                        "derived window correction expected 11 entities, adjusted=" + adjusted);
            }

            synchronized (APPLIED) { APPLIED.put(view, Boolean.TRUE); }
            Celine3DDiagnostics.record(view.getContext(), "ROOM-143",
                    "Abgeleitete Fenstergruppe rastergenau nachvermessen",
                    "CALL#1179 visibleX=0.2470..0.5689 targetX=0.205..0.588"
                            + " visibleTop=0.1464 targetTop=0.086"
                            + " scaleX=" + HORIZONTAL_SCALE
                            + " centerX=" + OLD_CENTER_X + "->" + NEW_CENTER_X
                            + " scaleY=" + VERTICAL_SCALE
                            + " centerY=" + OLD_CENTER_Y + "->" + NEW_CENTER_Y
                            + " entities=" + adjusted
                            + " · Z/materials/camera/Celine/source-GLB unchanged");
        }

        // #1183 shows the reference laptop is a separate missing foreground object, not part of the
        // immutable table source. Create/retry it independently from the already-applied window group
        // so a laptop failure can never stack the accepted window affine transform.
        CelineRoomForegroundLaptopV80.apply(view, engine);
    }

    static void release(Celine3DView view) {
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
