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
 * M1 established the closest stable HOME camera foundation against /Refernzbild.png. M2 changes only
 * measured derived furniture transforms while keeping source GLB bytes immutable. Proof #85 measured
 * the lounge chair around normalized x~0.08..0.24 versus target x~0.21..0.33; Proof #86 confirms the
 * +0.65 m parent-X correction centers it near the reference, but the visible chair width is still about
 * 0.20 of the viewport versus a ~0.12 target. Proof #87 confirms the 0.60 local chair size correction
 * brings its X placement and apparent width close to the target, so those accepted values stay fixed.
 *
 * Proof #87 exposed the floor lamp as a huge clipped foreground object. Moving only its derived parent
 * depth by -3.35 m placed the assembly origin from z=+1.55 at z=-1.80. Proof #88 proved that depth
 * correction correct, Proof #89 established the measured non-uniform local size correction X/Z 0.34
 * and Y 0.54, and Proof #90 on exact runtime 7ccc32cc confirmed valid HOME while only the later CALL
 * diversity gate fails. The lamp is now close enough in size/X to freeze while larger M2 errors remain.
 *
 * Proof #91 on exact runtime 48ac963d confirms the dresser depth move restores the missing low-left
 * cabinet at the correct depth family. HOME is valid (std=55.80, colors=90); the proof fails only later
 * at the known CALL diversity gate. Manual normalized HOME measurement gives dresser visible width
 * ~0.124 versus reference ~0.180 while its height/depth placement are already much closer. Preserve the
 * accepted -2.25 m Z correction and widen only local dresser X by 1.45. Keep dresser Y/Z scale,
 * parent X/Y, rotation and source GLB unchanged. Logical interaction metadata remains M3 after visual
 * geometry settles.
 */
final class CelineRoomReferenceLayoutV80 {
    static final float FOREGROUND_TABLE_Z_OFFSET_M = 0.35f;
    static final float BED_X_OFFSET_M = -0.45f;
    static final float LOUNGE_CHAIR_X_OFFSET_M = 0.65f;
    static final float LOUNGE_CHAIR_SCALE_FACTOR = 0.60f;
    static final float FLOOR_LAMP_X_OFFSET_M = 0.16f;
    static final float FLOOR_LAMP_Z_OFFSET_M = -3.35f;
    static final float FLOOR_LAMP_SCALE_XZ_FACTOR = 0.34f;
    static final float FLOOR_LAMP_SCALE_Y_FACTOR = 0.54f;
    static final float DRESSER_Z_OFFSET_M = -2.25f;
    static final float DRESSER_SCALE_X_FACTOR = 1.45f;

    private static final String[] BED_MARKER_NODES = {
            "bed_approach_anchor",
            "bed_edge_sit_anchor",
            "bed_relax_anchor",
            "bed_lie_anchor",
            "bed_exit_anchor"
    };

    private static final String[] CHAIR_MARKER_NODES = {
            "chair_approach_anchor",
            "chair_sit_anchor"
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

            translateParentLocal(asset, transforms,
                    "room_lounge_chair", LOUNGE_CHAIR_X_OFFSET_M, 0.0f, 0.0f, true);
            scaleLocal(asset, transforms,
                    "room_lounge_chair", LOUNGE_CHAIR_SCALE_FACTOR, true);
            int movedChairMarkers = 0;
            for (String marker : CHAIR_MARKER_NODES) {
                if (translateParentLocal(asset, transforms,
                        marker, LOUNGE_CHAIR_X_OFFSET_M, 0.0f, 0.0f, false)) {
                    movedChairMarkers++;
                }
            }

            translateParentLocal(asset, transforms,
                    "room_floor_lamp", FLOOR_LAMP_X_OFFSET_M, 0.0f, FLOOR_LAMP_Z_OFFSET_M, true);
            scaleLocalXyz(asset, transforms, "room_floor_lamp",
                    FLOOR_LAMP_SCALE_XZ_FACTOR,
                    FLOOR_LAMP_SCALE_Y_FACTOR,
                    FLOOR_LAMP_SCALE_XZ_FACTOR,
                    true);
            boolean movedLampMarker = translateParentLocal(asset, transforms,
                    "lamp_anchor", FLOOR_LAMP_X_OFFSET_M, 0.0f, FLOOR_LAMP_Z_OFFSET_M, false);

            translateParentLocal(asset, transforms,
                    "room_dresser", 0.0f, 0.0f, DRESSER_Z_OFFSET_M, true);
            scaleLocalXyz(asset, transforms, "room_dresser",
                    DRESSER_SCALE_X_FACTOR, 1.0f, 1.0f, true);
            boolean movedDresserMarker = translateParentLocal(asset, transforms,
                    "dresser_anchor", 0.0f, 0.0f, DRESSER_Z_OFFSET_M, false);

            synchronized (APPLIED) { APPLIED.put(view, asset); }
            Celine3DDiagnostics.record(view.getContext(), "ROOM-150",
                    "Referenzraum Layout korrigiert",
                    "tableZ=+" + FOREGROUND_TABLE_Z_OFFSET_M
                            + "m bedX=" + BED_X_OFFSET_M + "m"
                            + " bedMarkerNodesMoved=" + movedBedMarkers
                            + " chairX=+" + LOUNGE_CHAIR_X_OFFSET_M + "m"
                            + " chairScaleFactor=" + LOUNGE_CHAIR_SCALE_FACTOR
                            + " chairMarkerNodesMoved=" + movedChairMarkers
                            + " lampX=+" + FLOOR_LAMP_X_OFFSET_M + "m"
                            + " lampZ=" + FLOOR_LAMP_Z_OFFSET_M + "m"
                            + " lampScaleXYZ=" + FLOOR_LAMP_SCALE_XZ_FACTOR + ","
                            + FLOOR_LAMP_SCALE_Y_FACTOR + "," + FLOOR_LAMP_SCALE_XZ_FACTOR
                            + " lampMarkerMoved=" + movedLampMarker
                            + " dresserZ=" + DRESSER_Z_OFFSET_M + "m"
                            + " dresserScaleXYZ=" + DRESSER_SCALE_X_FACTOR + ",1.0,1.0"
                            + " dresserMarkerMoved=" + movedDresserMarker
                            + " sourceGLB=unchanged derivedNonUniformScale=true"
                            + " camera/Celine=unchanged"
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
            if (required) throw new IllegalStateException("Reference layout entity missing: " + entityName);
            return false;
        }
        int instance = transforms.getInstance(entity);
        if (instance == 0) {
            if (required) throw new IllegalStateException("Reference layout transform missing: " + entityName);
            return false;
        }

        float[] base = transforms.getTransform(instance, new float[16]);
        float[] translation = new float[16];
        float[] adjusted = new float[16];
        Matrix.setIdentityM(translation, 0);
        Matrix.translateM(translation, 0, deltaX, deltaY, deltaZ);
        Matrix.multiplyMM(adjusted, 0, translation, 0, base, 0);
        transforms.setTransform(instance, adjusted);
        return true;
    }

    private static boolean scaleLocal(
            FilamentAsset asset, TransformManager transforms,
            String entityName, float factor, boolean required) {
        return scaleLocalXyz(asset, transforms, entityName, factor, factor, factor, required);
    }

    private static boolean scaleLocalXyz(
            FilamentAsset asset, TransformManager transforms,
            String entityName, float factorX, float factorY, float factorZ,
            boolean required) {
        int entity = asset.getFirstEntityByName(entityName);
        if (entity == 0) {
            if (required) throw new IllegalStateException("Reference layout entity missing: " + entityName);
            return false;
        }
        int instance = transforms.getInstance(entity);
        if (instance == 0) {
            if (required) throw new IllegalStateException("Reference layout transform missing: " + entityName);
            return false;
        }

        float[] base = transforms.getTransform(instance, new float[16]);
        float[] scale = new float[16];
        float[] adjusted = new float[16];
        Matrix.setIdentityM(scale, 0);
        Matrix.scaleM(scale, 0, factorX, factorY, factorZ);
        Matrix.multiplyMM(adjusted, 0, base, 0, scale, 0);
        transforms.setTransform(instance, adjusted);
        return true;
    }
}
