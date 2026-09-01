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
 * The foreground-table correction keeps only the near table edge/surface in the product frame.
 * Direct manual inspection of the canonical /Refernzbild.png now also proves a larger geometry gap:
 * the bed must read as the major right-side room mass with its upper/headboard region visible, while
 * the previous runtime left only a small lower-right bed fragment in frame. Keep the source GLB
 * immutable and move only the derived runtime bed node 0.45 m left in parent/world-room X. Embedded
 * bed marker nodes, when present, receive the same delta so the GLB remains internally coherent;
 * logical 9R metadata remains pending until this visual geometry candidate is accepted.
 */
final class CelineRoomReferenceLayoutV80 {
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
                    "tableZ=+" + FOREGROUND_TABLE_Z_OFFSET_M
                            + "m bedX=" + BED_X_OFFSET_M + "m"
                            + " bedMarkerNodesMoved=" + movedBedMarkers
                            + " sourceGLB=unchanged camera/Celine=unchanged"
                            + " logical9Rmetadata=pending_visual_acceptance");
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
        int instance = transforms.getInstance(entity);
        if (instance == 0) {
            if (required) {
                throw new IllegalStateException("Reference layout transform missing: " + entityName);
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
