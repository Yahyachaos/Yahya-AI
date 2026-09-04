package de.yahya.ai;

import com.google.android.filament.Engine;
import com.google.android.filament.Scene;
import com.google.android.filament.TransformManager;
import com.google.android.filament.gltfio.FilamentAsset;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * v80 bounded visibility owner for the immutable sparse window/drape renderable.
 *
 * Proofs #64-#71 show that the source room_window_drapes mesh remains visibly ragged even when its
 * texture and the derived backing/fill layers change. The exact-room rebuild later compressed the
 * legacy back-wall depth from roughly -2.90 m to -2.10 m, while the four derived window layers kept
 * their legacy -2.755..-2.705 m local Z values. Real in-app visual proof #181 therefore showed a
 * blank back wall even though ROOM-148/149/146/144 all reported active: those derived planes were
 * physically behind the new wall. Rebase only that already-derived window group +0.660 m toward the
 * camera, preserving every internal 20/25/50 mm layer separation and leaving the deepest night plane
 * at -2.095 m, 5 mm in front of the exact -2.100 m back wall. Then hide the known-ragged immutable
 * source drape as before. Source bytes/transforms, furniture, room shell, camera, anchors and Celine
 * remain untouched.
 */
final class CelineRoomWindowSourceVisibilityV80 {
    private static final float EXACT_ROOM_DERIVED_WINDOW_Z_REBASE = 0.660f;
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
                "Derived window auf exakte Rückwandtiefe rebased; sparse Quelle ausgeblendet",
                "entities=" + rebased + " zShift=" + EXACT_ROOM_DERIVED_WINDOW_Z_REBASE
                        + " · backdropZ=-2.095 curtainZ=-2.075 sheerZ=-2.070 foldsZ=-2.045"
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
        // Filament/Android matrices store parent-local translation in indices 12..14. Adjusting only
        // index 14 preserves each fold facet's accepted yaw and every X/Y/scale/material parameter.
        local[14] += EXACT_ROOM_DERIVED_WINDOW_Z_REBASE;
        transforms.setTransform(instance, local);
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
