package de.yahya.ai;

import android.opengl.Matrix;

import com.google.android.filament.Engine;
import com.google.android.filament.TransformManager;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Measurement-owned horizontal correction for the derived v80 window layers.
 *
 * The immutable room_window_drapes source mesh is intentionally hidden after the derived backdrop,
 * side curtains, sheers and fold facets are created. Therefore moving only room_window_drapes does
 * not move the visible window. Real Candidate #1147 / #1151 on the exact 873x698 CALL stage measure
 * the visible derived group at x=0.209..0.639 (width=0.430, center=0.424), while Refernzbild.png
 * requires x=0.205..0.588 (width=0.383, center=0.397). Keep Y/Z/materials/camera/Celine untouched.
 *
 * The horizontal scale is 0.383/0.430 = 0.89069767. Proof #118 established a local-X projection
 * response of about 0.1588235 viewport/m for this same derived group, so the -0.027 center delta is
 * -0.170 m. The accepted derived group center therefore moves -0.605 -> -0.775 m. This post-create
 * affine correction is applied uniformly to every generated window layer so they cannot drift apart.
 */
final class CelineRoomWindowDerivedGroupV80 {
    private static final float OLD_CENTER_X = -0.605f;
    private static final float NEW_CENTER_X = -0.775f;
    private static final float HORIZONTAL_SCALE = 0.89069767f;

    private static final WeakHashMap<Celine3DView, Boolean> APPLIED = new WeakHashMap<>();

    private CelineRoomWindowDerivedGroupV80() {}

    static void apply(Celine3DView view, Engine engine) throws Exception {
        if (view == null || engine == null) return;
        synchronized (APPLIED) {
            if (APPLIED.containsKey(view)) return;
        }

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
                "Abgeleitete Fenstergruppe horizontal vermessen",
                "CALL=873x698 currentX=0.209..0.639 targetX=0.205..0.588"
                        + " scaleX=" + HORIZONTAL_SCALE
                        + " centerX=" + OLD_CENTER_X + "->" + NEW_CENTER_X
                        + " entities=" + adjusted
                        + " · Y/Z/materials/camera/Celine/source-GLB unchanged");
    }

    static void release(Celine3DView view) {
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

        float[] scale = new float[16];
        float[] adjusted = new float[16];
        Matrix.setIdentityM(scale, 0);
        Matrix.scaleM(scale, 0, HORIZONTAL_SCALE, 1.0f, 1.0f);
        Matrix.multiplyMM(adjusted, 0, local, 0, scale, 0);
        transforms.setTransform(instance, adjusted);
        return 1;
    }
}
