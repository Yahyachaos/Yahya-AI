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
 * not move the visible window. Real Candidate #1147 / #1151 measured the visible derived group at
 * x=0.209..0.639 (width=0.430, center=0.424), while Refernzbild.png requires x=0.205..0.588
 * (width=0.383, center=0.397). The horizontal correction is retained unchanged.
 *
 * Real Candidate #1153 on the exact 1016x813 CALL stage was opened against the exact repository
 * Refernzbild.png blob e85c43b5e365982aa862329eecfb31ab502db793. Horizontal alignment is now
 * materially on target (visible group about x=0.199..0.587 versus target x=0.205..0.588), so X is
 * frozen. The remaining high-confidence architecture error is vertical: the full derived silhouette
 * is about y=0.080..0.510 (height=0.430, center=0.295), versus target y=0.086..0.477
 * (height=0.391, center=0.2815). Refit from the already-proved #1153 transform instead of stacking
 * an arbitrary micro-tweak: total Y scale = 0.9354067 * 0.391/0.430 = 0.8505675. On the same plane,
 * the observed 0.430 viewport height across 2*1.12*0.9354067 m gives about 0.20522 viewport/m;
 * moving the projected center upward by 0.0135 therefore requires +0.06578 m room-local Y, taking
 * the corrected group center from 1.3206 m to 1.3864 m.
 *
 * The affine correction is applied uniformly to all generated window entities so backdrop, curtains,
 * sheers and folds cannot drift apart. Z/materials/camera/Celine/source-GLB bytes remain untouched.
 */
final class CelineRoomWindowDerivedGroupV80 {
    private static final float OLD_CENTER_X = -0.605f;
    private static final float NEW_CENTER_X = -0.775f;
    private static final float HORIZONTAL_SCALE = 0.89069767f;

    private static final float OLD_CENTER_Y = 1.20f;
    private static final float NEW_CENTER_Y = 1.3864f;
    private static final float VERTICAL_SCALE = 0.8505675f;

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
                "Abgeleitete Fenstergruppe X/Y vermessen",
                "CALL#1153 currentY=0.080..0.510 targetY=0.086..0.477"
                        + " scaleX=" + HORIZONTAL_SCALE
                        + " centerX=" + OLD_CENTER_X + "->" + NEW_CENTER_X
                        + " scaleY=" + VERTICAL_SCALE
                        + " centerY=" + OLD_CENTER_Y + "->" + NEW_CENTER_Y
                        + " entities=" + adjusted
                        + " · Z/materials/camera/Celine/source-GLB unchanged");
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
