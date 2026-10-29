package de.yahya.ai;

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
 * This recovery checkpoint therefore owns validation only: verify the exact source shell and the
 * source window/drapes partition are present, then leave their authored source-PBR transforms intact.
 * Camera/FOV is owned by Celine3DView/CelineCameraZoomV70. Primary furniture is deliberately deferred
 * to the later coherent primary-furniture batch required by the recovery solve order.
 */
final class CelineRoomReferenceLayoutV80 {
    private static final WeakHashMap<Celine3DView, FilamentAsset> APPLIED = new WeakHashMap<>();

    private CelineRoomReferenceLayoutV80() {}

    static void ensure(Celine3DView view) {
        if (view == null) return;
        try {
            Object state = roomState(view);
            if (state == null) return;

            Field assetsField = state.getClass().getDeclaredField("roomAssets");
            Field shellField = state.getClass().getDeclaredField("roomShellAsset");
            assetsField.setAccessible(true);
            shellField.setAccessible(true);

            Object rawAssets = assetsField.get(state);
            FilamentAsset shell = (FilamentAsset) shellField.get(state);
            if (!(rawAssets instanceof List) || shell == null) return;

            @SuppressWarnings("unchecked")
            List<FilamentAsset> assets = (List<FilamentAsset>) rawAssets;
            FilamentAsset window = findAssetForEntity(assets, "room_window_drapes__anchor");
            if (window == null) {
                throw new IllegalStateException(
                        "partitioned source window missing: room_window_drapes__anchor");
            }

            synchronized (APPLIED) {
                if (APPLIED.get(view) == window) return;
            }

            requireEntity(shell, "room_shell_floor");
            requireEntity(shell, "room_shell_ceiling");
            requireEntity(shell, "room_shell_left");
            requireEntity(shell, "room_shell_right");
            requireEntity(shell, "room_shell_back");
            requireEntity(shell, "room_shell_front");

            // Deliberately no transforms or materials here. The 14 partition exports already own the
            // clean source-fidelity baseline. The previous window-anchor correction was visually
            // regressive and remains discarded; furniture is solved later as one coherent batch.
            synchronized (APPLIED) {
                APPLIED.put(view, window);
            }

            Celine3DDiagnostics.record(view.getContext(), "ROOM-150",
                    "Partitionierter Clean-Architecture-Owner aktiv",
                    "authority=Refernzbild.png"
                            + " shell=sourceExact440x420x265"
                            + " shellLegacyRescale=false"
                            + " proofCameraOwner=Celine3DView"
                            + " sourceWindowPBR=true"
                            + " windowTransformOverride=false"
                            + " furnitureTransformOverride=false"
                            + " mirrorMaterialOverride=false"
                            + " sourceGLBsMutated=false");
        } catch (Throwable error) {
            Celine3DDiagnostics.error(view.getContext(), "ROOM-159",
                    "Referenz-Solverlayout FEHLER", error);
        }
    }

    private static FilamentAsset findAssetForEntity(List<FilamentAsset> assets, String entityName) {
        if (assets == null || entityName == null) return null;
        for (FilamentAsset asset : assets) {
            if (asset != null && asset.getFirstEntityByName(entityName) != 0) return asset;
        }
        return null;
    }

    private static void requireEntity(FilamentAsset asset, String entityName) {
        if (asset == null || asset.getFirstEntityByName(entityName) == 0) {
            throw new IllegalStateException("partitioned shell entity missing: " + entityName);
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
