package de.yahya.ai;

import com.google.android.filament.Scene;
import com.google.android.filament.gltfio.FilamentAsset;

import java.lang.reflect.Field;
import java.util.WeakHashMap;

/**
 * v80 bounded visibility test for the immutable sparse window/drape renderable.
 *
 * Proofs #64-#71 show that the source room_window_drapes mesh remains visibly ragged even when its
 * texture and the derived backing/fill layers change. Hide only that runtime entity after the derived
 * night/side/sheer coverage is in place. The GLB bytes, entity transform and all source data stay
 * untouched; release restores the entity to the scene. This is deliberately one reversible layering
 * experiment, not a source-asset rewrite.
 */
final class CelineRoomWindowSourceVisibilityV80 {
    private static final WeakHashMap<Celine3DView, State> STATES = new WeakHashMap<>();

    private CelineRoomWindowSourceVisibilityV80() {}

    static void hide(Celine3DView view, FilamentAsset asset) throws Exception {
        synchronized (STATES) {
            if (STATES.containsKey(view)) return;
        }
        int entity = asset.getFirstEntityByName("room_window_drapes");
        if (entity == 0) throw new IllegalStateException("window source visibility: entity fehlt");
        Scene scene = (Scene) field(view, "scene");
        scene.remove(entity);
        synchronized (STATES) { STATES.put(view, new State(scene, entity)); }
        Celine3DDiagnostics.record(view.getContext(), "ROOM-145",
                "Sparse Quell-Gardinen für derived reference window ausgeblendet",
                "entity=room_window_drapes · runtime scene visibility only"
                        + " · source GLB/transform/Celine/camera/anchors/lamp unchanged");
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
