package de.yahya.ai;

import android.opengl.Matrix;

import com.google.android.filament.TransformManager;
import com.google.android.filament.gltfio.FilamentAsset;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Bounded v80 reference-image layout correction.
 *
 * M0 measured the production HOME frame directly against /Refernzbild.png. The dominant structural
 * mismatch is global rather than material-specific: the current wall/floor boundary sits at normalized
 * y~0.828 while the target is y~0.490, and the bed/window/chair/rug are all roughly 0.29-0.37 viewport
 * heights too low. Celine itself is already close to the target center, so moving the camera would damage
 * the protected subject framing. M1 therefore raises only the derived room root as one bounded probe.
 *
 * Source GLB bytes remain immutable. Logical anchors/actions and the localized Lamp contract are not
 * claimed reconciled by this visual candidate; M3 owns that reconciliation only after geometry settles.
 */
final class CelineRoomReferenceLayoutV80 {
    static final float ROOM_ROOT_Y_OFFSET_M = 1.00f;
    static final float FOREGROUND_TABLE_Z_OFFSET_M = 0.35f;
    static final float BED_X_OFFSET_M = -0.45f;

    private static final String[] BED_MARKER_NODES = {
            "bed_approach_anchor",
            "bed_edge_sit_anchor",
            "bed_relax_anchor",
            "bed_lie_anchor",
            "bed_exit_anchor"
    };

    private static final WeakHashMap<Celine3DView, FilamentAsset> APPLIED =
            new WeakHashMap<>();

    private CelineRoomReferenceLayoutV80() {}

    static void ensure(Celine3DView view) {
        if (view == null) return;
        try {
            Object state = roomState(view);
            if (state == null) return;

            Field assetField = state.getClass().getDeclaredField("roomAsset");
            Field transformsField = state.getClass().getDeclaredField("transforms");
            assetField.setAccessible(true);
            transformsField.setAccessible(true);
            FilamentAsset asset = (FilamentAsset) assetField.get(state);
            TransformManager transforms = (TransformManager) transformsField.get(state);
            if (asset == null || transforms == null) return;

            synchronized (APPLIED) {
                if (APPLIED.get(view) == asset) return;
            }

            translateEntity(transforms, asset.getRoot(),
                    0.0f, ROOM_ROOT_Y_OFFSET_M, 0.0f, true, "room root");

            translateParentLocal(asset, transforms,
                    "room_foreground_table", 0.0f, 0.0f, FOREGROUND_TABLE_Z_OFFSET_M, true);
            translateParentLocal(asset, transforms,
                    "foreground_table_approach_anchor", 0.0f, 0.0f, FOREGROUND_TABLE_Z_OFFSET_M, true);
            translateParentLocal(asset, transforms,
                    "foreground_table_lean_anchor", 0.0f, 0.0f, FOREGROUND_TABLE_Z_OFFSET_M, true);

            translateParentLocal(asset, transforms,
                    "room_bed", BED_X_OFFSET_M, 0.0f, 0.0f, true);
            int movedBedMarkers = 0;
            for (String marker : BED_MARKER_NODES) {
                if (translateParentLocal(asset, transforms,
                        marker, BED_X_OFFSET_M, 0.0f, 0.0f, false)) {
                    movedBedMarkers++;
                }
            }

            synchronized (APPLIED) { APPLIED.put(view, asset); }
            Celine3DDiagnostics.record(view.getContext(), "ROOM-150",
                    "Referenzraum Layout korrigiert",
                    "roomRootY=+" + ROOM_ROOT_Y_OFFSET_M
                            + "m tableZ=+" + FOREGROUND_TABLE_Z_OFFSET_M
                            + "m bedX=" + BED_X_OFFSET_M + "m"
                            + " bedMarkerNodesMoved=" + movedBedMarkers
                            + " sourceGLB=unchanged camera/Celine=unchanged"
                            + " logicalAnchorsLamp=pending_M3_after_geometry");
        } catch (Throwable error) {
            Celine3DDiagnostics.error(view.getContext(), "ROOM-159",
                    "Referenzraum Layout-Korrektur FEHLER", error);
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

    private static boolean translateParentLocal(
            FilamentAsset asset, TransformManager transforms,
            String entityName, float deltaX, float deltaY, float deltaZ,
            boolean required) {
        int entity = asset.getFirstEntityByName(entityName);
        if (entity == 0) {
            if (required) {
                throw new IllegalStateException("Reference layout entity missing: " + entityName);
            }
            return false;
        }
        return translateEntity(transforms, entity, deltaX, deltaY, deltaZ, required, entityName);
    }

    private static boolean translateEntity(
            TransformManager transforms, int entity,
            float deltaX, float deltaY, float deltaZ,
            boolean required, String label) {
        if (entity == 0) {
            if (required) throw new IllegalStateException("Reference layout entity missing: " + label);
            return false;
        }
        int instance = transforms.getInstance(entity);
        if (instance == 0) {
            if (required) {
                throw new IllegalStateException("Reference layout transform missing: " + label);
            }
            return false;
        }

        float[] base = transforms.getTransform(instance, new float[16]);
        float[] translation = new float[16];
        float[] adjusted = new float[16];
        Matrix.setIdentityM(translation, 0);
        Matrix.translateM(translation, 0, deltaX, deltaY, deltaZ);
        // Pre-multiply so the exact delta is expressed in the entity parent's room coordinates and
        // is not scaled/rotated by the furniture node's existing local transform.
        Matrix.multiplyMM(adjusted, 0, translation, 0, base, 0);
        transforms.setTransform(instance, adjusted);
        return true;
    }
}
