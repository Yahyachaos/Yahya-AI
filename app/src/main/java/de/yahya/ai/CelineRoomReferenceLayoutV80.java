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
 * M1 established the stable HOME camera foundation. M2 changes only measured derived furniture
 * transforms while keeping the 12 source GLBs immutable and canonical Celine separate.
 *
 * Frozen measured placements:
 * - foreground table: Z +0.35 m, local scale X/Y/Z 1.90/1.66/1.38
 * - lounge chair: X +0.65 m, Z -1.50 m, local scale 0.60, local yaw -20 deg
 * - floor lamp: X +0.16 m, Z -3.35 m, local scale X/Z 0.34 and Y 0.54
 * - dresser: Z -2.25 m, local yaw 180 deg
 * - large plant: X +0.92 m
 * - bed X: -0.75 m, Z: -1.25 m, source-local X scale 1.47
 *
 * Proof #121 put the chair around normalized y~0.466..0.627. The first measured depth correction
 * to Z -0.50 m produced Proof #122 at y~0.432..0.583 versus the exact reference target
 * y~0.365..0.498. That observed -0.034/-0.044 movement for a -0.50 m depth change is now the
 * empirical calibration: another -1.00 m predicts approximately -0.068/-0.088, matching the
 * remaining vertical error without changing X, scale or yaw. Move only chair depth and its two chair
 * marker nodes by the same total offset. Camera, bed, lamp, dresser, rug, window group, Celine,
 * materials, lighting and all source GLBs remain unchanged until the next proof is inspected against
 * /Refernzbild.png.
 *
 * The legacy room_window_drapes parent-X calibration remains recorded below for provenance, but the
 * sparse source entity is currently hidden by CelineRoomWindowSourceVisibilityV80; visible window
 * placement is owned by the derived window layers instead.
 */
final class CelineRoomReferenceLayoutV80 {
    static final float FOREGROUND_TABLE_Z_OFFSET_M = 0.35f;
    static final float FOREGROUND_TABLE_SCALE_X_FACTOR = 1.90f;
    static final float FOREGROUND_TABLE_SCALE_Y_FACTOR = 1.66f;
    static final float FOREGROUND_TABLE_SCALE_Z_FACTOR = 1.38f;
    static final float BED_X_OFFSET_M = -0.75f;
    static final float BED_Z_OFFSET_M = -1.25f;
    static final float BED_SCALE_X_FACTOR = 1.47f;
    static final float LOUNGE_CHAIR_X_OFFSET_M = 0.65f;
    static final float LOUNGE_CHAIR_Z_OFFSET_M = -1.50f;
    static final float LOUNGE_CHAIR_SCALE_FACTOR = 0.60f;
    static final float LOUNGE_CHAIR_YAW_OFFSET_DEG = -20.0f;
    static final float FLOOR_LAMP_X_OFFSET_M = 0.16f;
    static final float FLOOR_LAMP_Z_OFFSET_M = -3.35f;
    static final float FLOOR_LAMP_SCALE_XZ_FACTOR = 0.34f;
    static final float FLOOR_LAMP_SCALE_Y_FACTOR = 0.54f;
    static final float DRESSER_Z_OFFSET_M = -2.25f;
    static final float DRESSER_SCALE_Z_FACTOR = 2.60f;
    static final float DRESSER_YAW_OFFSET_DEG = 180.0f;
    static final float LARGE_PLANT_X_OFFSET_M = 0.92f;
    static final float RUG_SCALE_FACTOR = 1.45f;
    static final float WALL_SHELF_X_OFFSET_M = 3.00f;
    static final float WALL_SHELF_SCALE_X_FACTOR = 0.43f;
    static final float WINDOW_X_OFFSET_M = -85.70f;

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
            scaleLocalXyz(asset, transforms, "room_foreground_table",
                    FOREGROUND_TABLE_SCALE_X_FACTOR,
                    FOREGROUND_TABLE_SCALE_Y_FACTOR,
                    FOREGROUND_TABLE_SCALE_Z_FACTOR,
                    true);
            translateParentLocal(asset, transforms,
                    "foreground_table_approach_anchor", 0.0f, 0.0f, FOREGROUND_TABLE_Z_OFFSET_M, true);
            translateParentLocal(asset, transforms,
                    "foreground_table_lean_anchor", 0.0f, 0.0f, FOREGROUND_TABLE_Z_OFFSET_M, true);

            translateParentLocal(asset, transforms,
                    "room_bed", BED_X_OFFSET_M, 0.0f, BED_Z_OFFSET_M, true);
            scaleLocalXyz(asset, transforms, "room_bed",
                    BED_SCALE_X_FACTOR, 1.0f, 1.0f, true);
            int movedBedMarkers = 0;
            for (String marker : BED_MARKER_NODES) {
                if (translateParentLocal(asset, transforms,
                        marker, BED_X_OFFSET_M, 0.0f, BED_Z_OFFSET_M, false)) {
                    movedBedMarkers++;
                }
            }

            translateParentLocal(asset, transforms,
                    "room_lounge_chair", LOUNGE_CHAIR_X_OFFSET_M, 0.0f, LOUNGE_CHAIR_Z_OFFSET_M, true);
            rotateLocalYaw(asset, transforms,
                    "room_lounge_chair", LOUNGE_CHAIR_YAW_OFFSET_DEG, true);
            scaleLocal(asset, transforms,
                    "room_lounge_chair", LOUNGE_CHAIR_SCALE_FACTOR, true);
            int movedChairMarkers = 0;
            for (String marker : CHAIR_MARKER_NODES) {
                if (translateParentLocal(asset, transforms,
                        marker, LOUNGE_CHAIR_X_OFFSET_M, 0.0f, LOUNGE_CHAIR_Z_OFFSET_M, false)) {
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
            rotateLocalYaw(asset, transforms,
                    "room_dresser", DRESSER_YAW_OFFSET_DEG, true);
            scaleLocalXyz(asset, transforms, "room_dresser",
                    1.0f, 1.0f, DRESSER_SCALE_Z_FACTOR, true);
            boolean movedDresserMarker = translateParentLocal(asset, transforms,
                    "dresser_anchor", 0.0f, 0.0f, DRESSER_Z_OFFSET_M, false);

            translateParentLocal(asset, transforms,
                    "room_plant_large", LARGE_PLANT_X_OFFSET_M, 0.0f, 0.0f, true);

            scaleLocal(asset, transforms,
                    "room_rug", RUG_SCALE_FACTOR, true);

            translateParentLocal(asset, transforms,
                    "room_wall_shelf_books", WALL_SHELF_X_OFFSET_M, 0.0f, 0.0f, true);
            scaleLocalXyz(asset, transforms, "room_wall_shelf_books",
                    WALL_SHELF_SCALE_X_FACTOR, 1.0f, 1.0f, true);

            translateParentLocal(asset, transforms,
                    "room_window_drapes", WINDOW_X_OFFSET_M, 0.0f, 0.0f, true);

            synchronized (APPLIED) { APPLIED.put(view, asset); }
            Celine3DDiagnostics.record(view.getContext(), "ROOM-150",
                    "Referenzraum Layout korrigiert",
                    "tableZ=+" + FOREGROUND_TABLE_Z_OFFSET_M
                            + "m tableScaleXYZ=" + FOREGROUND_TABLE_SCALE_X_FACTOR + ","
                            + FOREGROUND_TABLE_SCALE_Y_FACTOR + ","
                            + FOREGROUND_TABLE_SCALE_Z_FACTOR
                            + " bedX=" + BED_X_OFFSET_M + "m"
                            + " bedZ=" + BED_Z_OFFSET_M + "m"
                            + " bedScaleXYZ=" + BED_SCALE_X_FACTOR + ",1.0,1.0"
                            + " bedMarkerNodesMoved=" + movedBedMarkers
                            + " chairX=+" + LOUNGE_CHAIR_X_OFFSET_M + "m"
                            + " chairZ=" + LOUNGE_CHAIR_Z_OFFSET_M + "m"
                            + " chairYaw=" + LOUNGE_CHAIR_YAW_OFFSET_DEG + "deg"
                            + " chairScaleFactor=" + LOUNGE_CHAIR_SCALE_FACTOR
                            + " chairMarkerNodesMoved=" + movedChairMarkers
                            + " lampX=+" + FLOOR_LAMP_X_OFFSET_M + "m"
                            + " lampZ=" + FLOOR_LAMP_Z_OFFSET_M + "m"
                            + " lampScaleXYZ=" + FLOOR_LAMP_SCALE_XZ_FACTOR + ","
                            + FLOOR_LAMP_SCALE_Y_FACTOR + "," + FLOOR_LAMP_SCALE_XZ_FACTOR
                            + " dresserZ=" + DRESSER_Z_OFFSET_M + "m"
                            + " dresserYaw=" + DRESSER_YAW_OFFSET_DEG + "deg"
                            + " dresserScaleXYZ=1.0,1.0," + DRESSER_SCALE_Z_FACTOR
                            + " dresserMarkerMoved=" + movedDresserMarker
                            + " largePlantX=+" + LARGE_PLANT_X_OFFSET_M + "m"
                            + " rugScale=" + RUG_SCALE_FACTOR
                            + " wallShelfX=+" + WALL_SHELF_X_OFFSET_M + "m"
                            + " wallShelfScaleXYZ=" + WALL_SHELF_SCALE_X_FACTOR + ",1.0,1.0"
                            + " windowX=" + WINDOW_X_OFFSET_M + "m"
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

    private static boolean rotateLocalYaw(
            FilamentAsset asset, TransformManager transforms,
            String entityName, float deltaDegrees, boolean required) {
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
        float[] yaw = new float[16];
        float[] adjusted = new float[16];
        Matrix.setRotateM(yaw, 0, deltaDegrees, 0.0f, 1.0f, 0.0f);
        Matrix.multiplyMM(adjusted, 0, base, 0, yaw, 0);
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
