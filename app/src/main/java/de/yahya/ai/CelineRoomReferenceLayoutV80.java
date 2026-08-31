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
 * The baseline production proof showed the foreground table as a fully displayed central slab,
 * while the canonical room reference requires it to behave as a near-camera foreground occluder:
 * only the near surface/edge should enter the bottom of frame. The first +0.70 m candidate moved
 * the table completely out of the HOME frame. This measured midpoint candidate keeps the source
 * GLB immutable and moves only the derived runtime table node +0.35 m toward the fixed +Z viewer.
 * The two embedded table anchor marker nodes move with the same delta so the GLB stays internally
 * coherent; logical 9R anchor/nav metadata remains unchanged until this visual candidate is
 * manually accepted.
 */
final class CelineRoomReferenceLayoutV80 {
    static final float FOREGROUND_TABLE_Z_OFFSET_M = 0.35f;

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

            translateParentLocalZ(asset, transforms,
                    "room_foreground_table", FOREGROUND_TABLE_Z_OFFSET_M);
            translateParentLocalZ(asset, transforms,
                    "foreground_table_approach_anchor", FOREGROUND_TABLE_Z_OFFSET_M);
            translateParentLocalZ(asset, transforms,
                    "foreground_table_lean_anchor", FOREGROUND_TABLE_Z_OFFSET_M);

            synchronized (APPLIED) { APPLIED.put(view, asset); }
            Celine3DDiagnostics.record(view.getContext(), "ROOM-150",
                    "Referenzraum Vordergrundtisch korrigiert",
                    "tableZ=+" + FOREGROUND_TABLE_Z_OFFSET_M
                            + "m anchorMarkers=sameDelta"
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

    private static void translateParentLocalZ(
            FilamentAsset asset, TransformManager transforms,
            String entityName, float deltaZ) {
        int entity = asset.getFirstEntityByName(entityName);
        if (entity == 0) {
            throw new IllegalStateException("Reference layout entity missing: " + entityName);
        }
        int instance = transforms.getInstance(entity);
        if (instance == 0) {
            throw new IllegalStateException("Reference layout transform missing: " + entityName);
        }

        float[] base = transforms.getTransform(instance, new float[16]);
        float[] translation = new float[16];
        float[] adjusted = new float[16];
        Matrix.setIdentityM(translation, 0);
        Matrix.translateM(translation, 0, 0.0f, 0.0f, deltaZ);
        // Pre-multiply so the exact delta is expressed in the entity parent's room coordinates and
        // is not scaled by the furniture node's existing uniform scale.
        Matrix.multiplyMM(adjusted, 0, translation, 0, base, 0);
        transforms.setTransform(instance, adjusted);
    }
}
