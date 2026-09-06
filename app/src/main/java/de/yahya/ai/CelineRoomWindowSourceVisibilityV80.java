package de.yahya.ai;

import android.opengl.Matrix;

import com.google.android.filament.Engine;
import com.google.android.filament.Scene;
import com.google.android.filament.TransformManager;
import com.google.android.filament.gltfio.FilamentAsset;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * v80 bounded visibility/placement owner for the immutable sparse window/drape renderable.
 *
 * Proofs #64-#71 show that the source room_window_drapes mesh remains visibly ragged even when its
 * texture and the derived backing/fill layers change. The exact-room rebuild later compressed the
 * legacy back-wall depth from roughly -2.90 m to -2.10 m, while the four derived window layers kept
 * their legacy -2.755..-2.705 m local Z values. Real in-app visual proof #181 therefore showed a
 * blank back wall even though ROOM-148/149/146/144 all reported active. Proof #1125 then showed only
 * the shallow fold strips because the broad derived planes were still inside the physical back-wall
 * face. A +0.720 m Z rebase puts every derived layer in front of that wall; real CALL Proof #1126
 * confirms the broad curtains/night field are now actually visible.
 *
 * The canonical Refernzbild.png measurement contract requires the visible derived window/drape
 * envelope at x=0.205..0.588 on the exact 1016x813 CALL stage. Proof #1132 exposed that the prior
 * #1127 refit had used a shifted comparison envelope and rendered roughly 0.275..0.734. The first
 * canonical correction (xScale=1.17955, xShift=0.41024) was then verified by real CALL Proof #1133:
 * it fixes the left edge at about x=0.206, but the slanted outer right curtain still reaches roughly
 * x=0.64..0.645 instead of 0.588. This remains the largest measured architecture-envelope error.
 *
 * Keep the solve reproducible rather than eyeballing it. Runtime checkpoints provide four measured
 * horizontal transfer samples: #1126 (1/0 -> ~0.192..0.462), #1127
 * (1.4185/0.867 -> ~0.207..0.734), #1132 (1.259/0.964 -> ~0.275..0.734), and #1133
 * (1.17955/0.41024 -> ~0.206..0.64..0.645). A least-squares two-edge affine fit across those real
 * samples solves the canonical 0.205..0.588 envelope at xScale~=1.151 and xShift~=0.356.
 * Change only that measured horizontal group transform. Preserve the current Y/Z correction until
 * the next real HOME/CALL proof; source bytes/transforms, furniture, room shell, camera, anchors and
 * Celine remain untouched.
 */
final class CelineRoomWindowSourceVisibilityV80 {
    private static final float EXACT_ROOM_DERIVED_WINDOW_Z_REBASE = 0.720f;
    private static final float EXACT_ROOM_DERIVED_WINDOW_X_SCALE = 1.151f;
    private static final float EXACT_ROOM_DERIVED_WINDOW_X_SHIFT = 0.356f;
    private static final float EXACT_ROOM_DERIVED_WINDOW_Y_SCALE = 1.323f;
    private static final float EXACT_ROOM_DERIVED_WINDOW_Y_SHIFT = -0.876f;
    private static final WeakHashMap<Celine3DView, State> STATES = new WeakHashMap<>();

    private CelineRoomWindowSourceVisibilityV80() {}

    static void hide(Celine3DView view, FilamentAsset asset) throws Exception {
        synchronized (STATES) {
            if (STATES.containsKey(view)) return;
        }
        int entity = asset.getFirstEntityByName("room_window_drapes");
        if (entity == 0) throw new IllegalStateException("window source visibility: entity fehlt");
        Scene scene = (Scene) field(view, "scene");

        int rebased = rebaseDerivedWindow(view);
        if (rebased != 11) {
            throw new IllegalStateException(
                    "derived exact-room window rebase incomplete: " + rebased + "/11 entities");
        }

        scene.remove(entity);
        synchronized (STATES) { STATES.put(view, new State(scene, entity)); }
        Celine3DDiagnostics.record(view.getContext(), "ROOM-145",
                "Derived window gegen kanonischen CALL-Referenzbbox kalibriert; sparse Quelle ausgeblendet",
                "entities=" + rebased
                        + " zShift=" + EXACT_ROOM_DERIVED_WINDOW_Z_REBASE
                        + " xScale=" + EXACT_ROOM_DERIVED_WINDOW_X_SCALE
                        + " xShift=" + EXACT_ROOM_DERIVED_WINDOW_X_SHIFT
                        + " yScale=" + EXACT_ROOM_DERIVED_WINDOW_Y_SCALE
                        + " yShift=" + EXACT_ROOM_DERIVED_WINDOW_Y_SHIFT
                        + " · targetBBoxX=0.205..0.588 authority=Refernzbild.png + Proof#1133"
                        + " · source GLB/transform/Celine/camera/anchors/lamp unchanged");
    }

    private static int rebaseDerivedWindow(Celine3DView view) throws Exception {
        Engine engine = (Engine) field(view, "engine");
        if (engine == null) throw new IllegalStateException("derived window rebase: engine fehlt");
        TransformManager transforms = engine.getTransformManager();
        int count = 0;
        count += rebaseStateEntities(CelineRoomWindowBackdropV80.class, view, transforms);
        count += rebaseStateEntities(CelineRoomWindowCurtainFillV80.class, view, transforms);
        count += rebaseStateEntities(CelineRoomWindowSheerFillV80.class, view, transforms);
        count += rebaseStateEntities(CelineRoomWindowFoldDetailV80.class, view, transforms);
        return count;
    }

    private static int rebaseStateEntities(Class<?> owner, Celine3DView view,
                                           TransformManager transforms) throws Exception {
        Field statesField = owner.getDeclaredField("STATES");
        statesField.setAccessible(true);
        Object statesValue = statesField.get(null);
        if (!(statesValue instanceof Map)) {
            throw new IllegalStateException("derived window rebase: STATES fehlt bei " + owner.getSimpleName());
        }
        Object state;
        Map<?, ?> states = (Map<?, ?>) statesValue;
        synchronized (states) {
            state = states.get(view);
        }
        if (state == null) {
            throw new IllegalStateException("derived window rebase: State fehlt bei " + owner.getSimpleName());
        }

        Field single = declaredFieldOrNull(state.getClass(), "entity");
        if (single != null) {
            single.setAccessible(true);
            int entity = single.getInt(state);
            rebaseEntity(transforms, entity);
            return 1;
        }

        Field multiple = state.getClass().getDeclaredField("entities");
        multiple.setAccessible(true);
        Object value = multiple.get(state);
        if (value instanceof int[]) {
            int count = 0;
            for (int entity : (int[]) value) {
                rebaseEntity(transforms, entity);
                count++;
            }
            return count;
        }
        if (value instanceof List) {
            int count = 0;
            for (Object item : (List<?>) value) {
                if (!(item instanceof Integer)) {
                    throw new IllegalStateException("derived window rebase: ungültige entity list");
                }
                rebaseEntity(transforms, (Integer) item);
                count++;
            }
            return count;
        }
        throw new IllegalStateException("derived window rebase: entities-Typ unbekannt bei "
                + owner.getSimpleName());
    }

    private static void rebaseEntity(TransformManager transforms, int entity) {
        int instance = transforms.getInstance(entity);
        if (instance == 0) {
            throw new IllegalStateException("derived window rebase: Transform fehlt entity=" + entity);
        }
        float[] local = transforms.getTransform(instance, new float[16]);
        float[] group = new float[16];
        float[] adjusted = new float[16];
        Matrix.setIdentityM(group, 0);
        Matrix.translateM(group, 0,
                EXACT_ROOM_DERIVED_WINDOW_X_SHIFT,
                EXACT_ROOM_DERIVED_WINDOW_Y_SHIFT,
                EXACT_ROOM_DERIVED_WINDOW_Z_REBASE);
        Matrix.scaleM(group, 0,
                EXACT_ROOM_DERIVED_WINDOW_X_SCALE,
                EXACT_ROOM_DERIVED_WINDOW_Y_SCALE,
                1.0f);
        Matrix.multiplyMM(adjusted, 0, group, 0, local, 0);
        transforms.setTransform(instance, adjusted);
    }

    private static Field declaredFieldOrNull(Class<?> type, String name) {
        try { return type.getDeclaredField(name); }
        catch (NoSuchFieldException ignored) { return null; }
    }

    static void release(Celine3DView view) {
        State state;
        synchronized (STATES) { state = STATES.remove(view); }
        if (state == null) return;
        try { state.scene.addEntity(state.entity); } catch (Throwable ignored) {}
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static final class State {
        final Scene scene;
        final int entity;

        State(Scene scene, int entity) {
            this.scene = scene;
            this.entity = entity;
        }
    }
}
