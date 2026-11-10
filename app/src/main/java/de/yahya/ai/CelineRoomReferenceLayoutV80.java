package de.yahya.ai;

import com.google.android.filament.Engine;
import com.google.android.filament.RenderableManager;
import com.google.android.filament.Scene;
import com.google.android.filament.gltfio.FilamentAsset;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * v80 clean architecture owner for the partitioned source-fidelity room runtime.
 *
 * The rejected combined-room recovery accumulated legacy shell rescaling, per-furniture TRS writes,
 * mirror material replacement and later window-only corrections. After promotion to the 14-partition
 * source-fidelity runtime those writes no longer have a valid single roomAsset owner; continuing to
 * reflect that field produces ROOM-159 and, more importantly, risks re-applying transforms that were
 * solved against the rejected combined derivative rather than the immutable partition sources.
 *
 * The exact S25 furniture transforms are already owned and applied by CelineRoomEnvironmentV80.
 * This separate validation-only guard verifies the source shell and window/drapes partition;
 * it must never reapply obsolete photo-derived transforms to any furniture or the camera.
 * The byte-verified S25 JSON is authoritative; camera/FOV remains Celine3DView-owned.
 */
final class CelineRoomReferenceLayoutV80 {
    private static final WeakHashMap<Celine3DView, FilamentAsset> APPLIED = new WeakHashMap<>();

    private CelineRoomReferenceLayoutV80() {}

    static boolean ensure(Celine3DView view) {
        if (view == null) return false;
        try {
            Object state = roomState(view);
            if (state == null) {
                throw new IllegalStateException("Room state missing after native build");
            }

            Field assetsField = state.getClass().getDeclaredField("roomAssets");
            Field shellField = state.getClass().getDeclaredField("roomShellAsset");
            Field sceneField = state.getClass().getDeclaredField("scene");
            Field engineField = state.getClass().getDeclaredField("engine");
            assetsField.setAccessible(true);
            shellField.setAccessible(true);
            sceneField.setAccessible(true);
            engineField.setAccessible(true);

            Object rawAssets = assetsField.get(state);
            FilamentAsset shell = (FilamentAsset) shellField.get(state);
            Scene scene = (Scene) sceneField.get(state);
            Engine engine = (Engine) engineField.get(state);
            if (!(rawAssets instanceof List) || shell == null || scene == null || engine == null) {
                throw new IllegalStateException("Room shell, scene or partitions missing after native build");
            }

            @SuppressWarnings("unchecked")
            List<FilamentAsset> assets = (List<FilamentAsset>) rawAssets;
            FilamentAsset window = findAssetForEntity(assets, "room_window_drapes__anchor");
            if (window == null) {
                throw new IllegalStateException(
                        "partitioned source window missing: room_window_drapes__anchor");
            }

            // Recheck the live Filament Scene before the APPLIED fast path.
            // An existing GLB node name alone does NOT prove it is still drawn
            // after a Scene detach/rebuild. The front wall is intentionally hidden.
            RenderableManager renderables = engine.getRenderableManager();
            requireAttachedRenderable(shell, scene, renderables, "room_shell_floor");
            requireAttachedRenderable(shell, scene, renderables, "room_shell_ceiling");
            requireAttachedRenderable(shell, scene, renderables, "room_shell_left");
            requireAttachedRenderable(shell, scene, renderables, "room_shell_right");
            requireAttachedRenderable(shell, scene, renderables, "room_shell_back");
            int front = shell.getFirstEntityByName("room_shell_front");
            if (front == 0 || scene.hasEntity(front)) {
                throw new IllegalStateException("S25 front wall missing or unexpectedly visible");
            }
            boolean windowRenderableAttached = false;
            for (int entity : window.getEntities()) {
                if (scene.hasEntity(entity) && renderables.hasComponent(entity)) {
                    windowRenderableAttached = true;
                    break;
                }
            }
            if (!windowRenderableAttached) {
                throw new IllegalStateException("S25 window partition has no attached renderable");
            }
            // A previously READY room can lose a furniture renderable while
            // the shell and drapes remain attached. Recheck all 13 imported
            // source partitions on EVERY activation, before the APPLIED fast path.
            // This is scene membership only; never touch authored XYZ/Euler/scale.
            requireS25FurnitureAttached(assets, shell, scene, renderables);

            synchronized (APPLIED) {
                if (APPLIED.get(view) == window) return true;
            }

            // Deliberately no transforms or materials here. The 14 partition exports already own the
            // clean source-fidelity baseline. The previous window-anchor correction was visually
            // regressive and remains discarded; furniture is solved later as one coherent batch.
            synchronized (APPLIED) {
                APPLIED.put(view, window);
            }

            Celine3DDiagnostics.record(view.getContext(), "ROOM-150",
                    "Partitionierter Clean-Architecture-Owner aktiv",
                    "authority=YahyaAI_Raumlayout_FINAL_2026-10-31.json"
                            + " shell=sourceExact440x420x265"
                            + " shellLegacyRescale=false"
                            + " proofCameraOwner=Celine3DView"
                            + " sourceWindowPBR=true"
                            + " windowTransformOverride=false"
                            + " furnitureTransformOverride=false"
                            + " mirrorMaterialOverride=false"
                            + " sourceGLBsMutated=false");
            return true;
        } catch (Throwable error) {
            Celine3DDiagnostics.error(view.getContext(), "ROOM-159",
                    "Referenz-Solverlayout FEHLER", error);
            return false;
        }
    }

    // Exact S25 JSON instance order, mapping only the legacy "back" ID to
    // the immutable GLB's actual "rear" anchor. Source GLB bytes are unchanged.
    private static final String[] S25_SOURCE_ANCHORS = {
            "room_bed__anchor", "room_dresser__anchor",
            "room_plant_large__anchor", "room_plant_small__anchor",
            "room_floor_lamp__anchor", "room_nightstand_rear__anchor",
            "room_nightstand_front__anchor", "room_lounge_chair__anchor",
            "room_rug__anchor", "room_foreground_table__anchor",
            "room_window_drapes__anchor", "room_wall_shelf_books__anchor",
            "room_round_mirror__anchor"
    };

    private static void requireS25FurnitureAttached(
            List<FilamentAsset> assets, FilamentAsset shell, Scene scene,
            RenderableManager renderables) {
        if (assets.size() != S25_SOURCE_ANCHORS.length + 1
                || assets.get(0) != shell) {
            throw new IllegalStateException("S25 room must contain shell plus 13 ordered GLB partitions");
        }
        java.util.HashSet<Integer> roots = new java.util.HashSet<>();
        for (int i = 0; i < S25_SOURCE_ANCHORS.length; i++) {
            FilamentAsset part = assets.get(i + 1);
            String anchor = S25_SOURCE_ANCHORS[i];
            if (part == null || part == shell
                    || part.getFirstEntityByName(anchor) == 0
                    || !roots.add(part.getRoot())) {
                throw new IllegalStateException("S25 furniture partition missing or duplicated: " + anchor);
            }
            int attachedRenderables = 0;
            for (int entity : part.getEntities()) {
                if (!renderables.hasComponent(entity)) continue;
                if (!scene.hasEntity(entity)) {
                    throw new IllegalStateException("S25 furniture renderable detached: " + anchor);
                }
                attachedRenderables++;
            }
            if (attachedRenderables == 0) {
                throw new IllegalStateException("S25 furniture has no live renderable: " + anchor);
            }
        }
    }

    private static FilamentAsset findAssetForEntity(List<FilamentAsset> assets, String entityName) {
        if (assets == null || entityName == null) return null;
        for (FilamentAsset asset : assets) {
            if (asset != null && asset.getFirstEntityByName(entityName) != 0) return asset;
        }
        return null;
    }

    private static void requireAttachedRenderable(
            FilamentAsset asset, Scene scene, RenderableManager renderables, String entityName) {
        int entity = asset.getFirstEntityByName(entityName);
        if (entity == 0 || !scene.hasEntity(entity) || !renderables.hasComponent(entity)) {
            throw new IllegalStateException("partitioned shell renderable not attached: " + entityName);
        }
    }

    private static Object roomState(Celine3DView view) throws Exception {
        Field statesField = CelineRoomEnvironmentV80.class.getDeclaredField("STATES");
        statesField.setAccessible(true);
        Object value = statesField.get(null);
        if (!(value instanceof Map)) return null;
        Map<?, ?> states = (Map<?, ?>) value;
        synchronized (states) {
            return states.get(view);
        }
    }
}
