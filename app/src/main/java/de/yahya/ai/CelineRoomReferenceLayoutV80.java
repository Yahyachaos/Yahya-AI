package de.yahya.ai;

import android.opengl.Matrix;

import com.google.android.filament.TransformManager;
import com.google.android.filament.gltfio.FilamentAsset;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * User-approved v25 TRUE3D room transfer.
 *
 * Sole geometry/layout authority:
 * docs/celine/room-v25/v25-room-layout.json
 *
 * This class deliberately replaces the later measured-room correction sequence instead of
 * stacking on it. The 12 source furniture GLBs remain unchanged; only the loaded combined-room
 * node transforms are corrected to the approved v25 composition.
 */
final class CelineRoomReferenceLayoutV80 {
    // v25 shell: front world Z -1.05, back world Z -8.00 with the locked room root Z -4.00.
    private static final float SHELL_BACK_Z_OFFSET_M = -1.10f;
    private static final float SHELL_CENTER_Z_OFFSET_M = -0.525f;
    private static final float SHELL_DEPTH_SCALE_Z = 1.198275862f; // 6.95 / 5.80

    // Deltas/factors are derived only from the immutable assembly base transforms and the
    // machine-readable v25 checkpoint. No later measured-room values are retained.
    private static final float BED_DX = -0.14f;
    private static final float BED_DZ = -1.07f;
    private static final float BED_SCALE = 1.237288136f; // 1.46 / 1.18
    private static final float BED_EXTRA_YAW_DEG = 180.0f; // env leaves 90deg; v25 target is -90deg

    private static final float DRESSER_DX = 0.08f;
    private static final float DRESSER_DZ = -1.05f;
    private static final float DRESSER_SCALE = 1.368421053f; // 1.30 / 0.95

    private static final float LARGE_PLANT_DX = -0.02f;
    private static final float LARGE_PLANT_DZ = -1.02f;
    private static final float LARGE_PLANT_SCALE = 1.122448980f; // 1.10 / 0.98

    private static final float SMALL_PLANT_DX = -0.03f;
    private static final float SMALL_PLANT_DZ = -0.13f;
    private static final float SMALL_PLANT_SCALE = 0.50f; // 0.32 / 0.64

    private static final float LAMP_DX = -0.07f;
    private static final float LAMP_DZ = -4.73f;
    private static final float LAMP_SCALE = 0.651162791f; // 0.56 / 0.86

    private static final float NIGHTSTAND_FRONT_DX = -0.11f;
    private static final float NIGHTSTAND_FRONT_DZ = -0.92f;
    private static final float NIGHTSTAND_FRONT_SCALE = 0.765625f; // 0.49 / 0.64
    private static final float NIGHTSTAND_BACK_DX = -0.11f;
    private static final float NIGHTSTAND_BACK_DZ = -1.24f;
    private static final float NIGHTSTAND_BACK_SCALE = 0.671875f; // 0.43 / 0.64
    private static final float NIGHTSTAND_EXTRA_YAW_DEG = 90.0f; // env leaves 180deg; v25 target 270deg

    private static final float CHAIR_DX = -0.53f;
    private static final float CHAIR_DZ = -2.18f;
    private static final float CHAIR_SCALE = 0.86f; // 0.43 / 0.50
    private static final float CHAIR_YAW_DEG = 25.0f;

    private static final float RUG_DX = 0.44f;
    private static final float RUG_DZ = -1.01f;
    private static final float RUG_SCALE_X = 1.342857143f; // 2.35 / 1.75
    private static final float RUG_SCALE_Y = 0.571428571f; // 1.00 / 1.75
    private static final float RUG_SCALE_Z = 0.937142857f; // 1.64 / 1.75

    private static final float TABLE_DZ = 1.10f;
    private static final float TABLE_SCALE_X = 1.893939394f; // 1.25 / 0.66
    private static final float TABLE_SCALE_Y = 1.666666667f; // 1.10 / 0.66
    private static final float TABLE_SCALE_Z = 1.378787879f; // 0.91 / 0.66

    private static final float WINDOW_DX = -1.27f;
    private static final float WINDOW_DY = 0.230168f;
    private static final float WINDOW_DZ = -1.097f;
    private static final float WINDOW_SCALE = 0.967741935f; // 1.50 / 1.55

    private static final float SHELF_DX = 3.40f;
    private static final float SHELF_DY = 0.380630f;
    private static final float SHELF_DZ = -1.098f;
    private static final float SHELF_SCALE = 0.652777778f; // 0.47 / 0.72

    private static final float MIRROR_DX = 0.04f;
    private static final float MIRROR_DY = -0.02f;
    private static final float MIRROR_SCALE = 0.890909091f; // 0.49 / 0.55

    private static final String[] BED_MARKER_NODES = {
            "bed_approach_anchor", "bed_edge_sit_anchor", "bed_relax_anchor",
            "bed_lie_anchor", "bed_exit_anchor"
    };
    private static final String[] CHAIR_MARKER_NODES = {
            "chair_approach_anchor", "chair_sit_anchor"
    };
    private static final WeakHashMap<Celine3DView, FilamentAsset> APPLIED = new WeakHashMap<>();

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

            // v25 room depth. Keep the viewer/front edge fixed and extend only toward the back.
            translateParentLocal(asset, transforms, "room_back_wall", 0f, 0f, SHELL_BACK_Z_OFFSET_M, true);
            for (String shell : new String[]{"room_floor", "room_left_wall", "room_right_wall", "room_ceiling"}) {
                translateParentLocal(asset, transforms, shell, 0f, 0f, SHELL_CENTER_Z_OFFSET_M, true);
                scaleLocalXyz(asset, transforms, shell, 1f, 1f, SHELL_DEPTH_SCALE_Z, true);
            }

            translateParentLocal(asset, transforms, "room_bed", BED_DX, 0f, BED_DZ, true);
            rotateLocalYaw(asset, transforms, "room_bed", BED_EXTRA_YAW_DEG, true);
            scaleLocal(asset, transforms, "room_bed", BED_SCALE, true);
            int movedBedMarkers = 0;
            for (String marker : BED_MARKER_NODES) {
                if (translateParentLocal(asset, transforms, marker, BED_DX, 0f, BED_DZ, false)) movedBedMarkers++;
            }

            translateParentLocal(asset, transforms, "room_dresser", DRESSER_DX, 0f, DRESSER_DZ, true);
            scaleLocal(asset, transforms, "room_dresser", DRESSER_SCALE, true);
            boolean movedDresserMarker = translateParentLocal(
                    asset, transforms, "dresser_anchor", DRESSER_DX, 0f, DRESSER_DZ, false);

            translateParentLocal(asset, transforms, "room_plant_large", LARGE_PLANT_DX, 0f, LARGE_PLANT_DZ, true);
            scaleLocal(asset, transforms, "room_plant_large", LARGE_PLANT_SCALE, true);

            translateParentLocal(asset, transforms, "room_plant_small", SMALL_PLANT_DX, 0f, SMALL_PLANT_DZ, true);
            scaleLocal(asset, transforms, "room_plant_small", SMALL_PLANT_SCALE, true);

            translateParentLocal(asset, transforms, "room_floor_lamp", LAMP_DX, 0f, LAMP_DZ, true);
            scaleLocal(asset, transforms, "room_floor_lamp", LAMP_SCALE, true);
            boolean movedLampMarker = translateParentLocal(
                    asset, transforms, "lamp_anchor", LAMP_DX, 0f, LAMP_DZ, false);

            translateParentLocal(asset, transforms, "room_nightstand_front",
                    NIGHTSTAND_FRONT_DX, 0f, NIGHTSTAND_FRONT_DZ, true);
            rotateLocalYaw(asset, transforms, "room_nightstand_front", NIGHTSTAND_EXTRA_YAW_DEG, true);
            scaleLocal(asset, transforms, "room_nightstand_front", NIGHTSTAND_FRONT_SCALE, true);

            translateParentLocal(asset, transforms, "room_nightstand_back",
                    NIGHTSTAND_BACK_DX, 0f, NIGHTSTAND_BACK_DZ, true);
            rotateLocalYaw(asset, transforms, "room_nightstand_back", NIGHTSTAND_EXTRA_YAW_DEG, true);
            scaleLocal(asset, transforms, "room_nightstand_back", NIGHTSTAND_BACK_SCALE, true);

            translateParentLocal(asset, transforms, "room_lounge_chair", CHAIR_DX, 0f, CHAIR_DZ, true);
            rotateLocalYaw(asset, transforms, "room_lounge_chair", CHAIR_YAW_DEG, true);
            scaleLocal(asset, transforms, "room_lounge_chair", CHAIR_SCALE, true);
            int movedChairMarkers = 0;
            for (String marker : CHAIR_MARKER_NODES) {
                if (translateParentLocal(asset, transforms, marker, CHAIR_DX, 0f, CHAIR_DZ, false)) movedChairMarkers++;
            }

            translateParentLocal(asset, transforms, "room_rug", RUG_DX, 0f, RUG_DZ, true);
            scaleLocalXyz(asset, transforms, "room_rug", RUG_SCALE_X, RUG_SCALE_Y, RUG_SCALE_Z, true);

            translateParentLocal(asset, transforms, "room_foreground_table", 0f, 0f, TABLE_DZ, true);
            scaleLocalXyz(asset, transforms, "room_foreground_table",
                    TABLE_SCALE_X, TABLE_SCALE_Y, TABLE_SCALE_Z, true);
            translateParentLocal(asset, transforms, "foreground_table_approach_anchor", 0f, 0f, TABLE_DZ, false);
            translateParentLocal(asset, transforms, "foreground_table_lean_anchor", 0f, 0f, TABLE_DZ, false);

            translateParentLocal(asset, transforms, "room_window_drapes", WINDOW_DX, WINDOW_DY, WINDOW_DZ, true);
            scaleLocal(asset, transforms, "room_window_drapes", WINDOW_SCALE, true);
            translateParentLocal(asset, transforms, "window_anchor", WINDOW_DX, 0f, WINDOW_DZ, false);

            translateParentLocal(asset, transforms, "room_wall_shelf_books", SHELF_DX, SHELF_DY, SHELF_DZ, true);
            scaleLocal(asset, transforms, "room_wall_shelf_books", SHELF_SCALE, true);
            translateParentLocal(asset, transforms, "shelf_anchor", SHELF_DX, 0f, SHELF_DZ, false);

            translateParentLocal(asset, transforms, "room_round_mirror", MIRROR_DX, MIRROR_DY, 0f, true);
            scaleLocal(asset, transforms, "room_round_mirror", MIRROR_SCALE, true);
            translateParentLocal(asset, transforms, "mirror_anchor", MIRROR_DX, 0f, 0f, false);

            synchronized (APPLIED) { APPLIED.put(view, asset); }
            Celine3DDiagnostics.record(view.getContext(), "ROOM-150", "v25 TRUE3D Raumlayout aktiv",
                    "authority=docs/celine/room-v25/v25-room-layout.json"
                            + " backWorldZ=-8.0 frontWorldZ=-1.05"
                            + " bedTarget=(1.81,-1.82) yaw=-90 scale=1.46"
                            + " chairTarget=(-2.28,-3.18) yaw=25 scale=0.43"
                            + " tableTargetZ=3.20 windowX=-0.82 shelfX=1.35"
                            + " nightstandsYaw=270"
                            + " bedMarkersMoved=" + movedBedMarkers
                            + " chairMarkersMoved=" + movedChairMarkers
                            + " dresserMarkerMoved=" + movedDresserMarker
                            + " lampMarkerMoved=" + movedLampMarker
                            + " sourceGLBs=unchanged camera/Celine=unchanged");
        } catch (Throwable error) {
            Celine3DDiagnostics.error(view.getContext(), "ROOM-159", "v25 TRUE3D Raumlayout FEHLER", error);
        }
    }

    private static Object roomState(Celine3DView view) throws Exception {
        Field statesField = CelineRoomEnvironmentV80.class.getDeclaredField("STATES");
        statesField.setAccessible(true);
        Object value = statesField.get(null);
        if (!(value instanceof Map)) return null;
        Map<?, ?> states = (Map<?, ?>) value;
        synchronized (states) { return states.get(view); }
    }

    private static boolean translateParentLocal(FilamentAsset asset, TransformManager transforms, String entityName,
                                                float deltaX, float deltaY, float deltaZ, boolean required) {
        int entity = asset.getFirstEntityByName(entityName);
        if (entity == 0) {
            if (required) throw new IllegalStateException("v25 layout entity missing: " + entityName);
            return false;
        }
        int instance = transforms.getInstance(entity);
        if (instance == 0) {
            if (required) throw new IllegalStateException("v25 layout transform missing: " + entityName);
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

    private static boolean rotateLocalYaw(FilamentAsset asset, TransformManager transforms, String entityName,
                                          float deltaDegrees, boolean required) {
        int entity = asset.getFirstEntityByName(entityName);
        if (entity == 0) {
            if (required) throw new IllegalStateException("v25 layout entity missing: " + entityName);
            return false;
        }
        int instance = transforms.getInstance(entity);
        if (instance == 0) {
            if (required) throw new IllegalStateException("v25 layout transform missing: " + entityName);
            return false;
        }
        float[] base = transforms.getTransform(instance, new float[16]);
        float[] yaw = new float[16];
        float[] adjusted = new float[16];
        Matrix.setRotateM(yaw, 0, deltaDegrees, 0f, 1f, 0f);
        Matrix.multiplyMM(adjusted, 0, base, 0, yaw, 0);
        transforms.setTransform(instance, adjusted);
        return true;
    }

    private static boolean scaleLocal(FilamentAsset asset, TransformManager transforms, String entityName,
                                      float factor, boolean required) {
        return scaleLocalXyz(asset, transforms, entityName, factor, factor, factor, required);
    }

    private static boolean scaleLocalXyz(FilamentAsset asset, TransformManager transforms, String entityName,
                                         float factorX, float factorY, float factorZ, boolean required) {
        int entity = asset.getFirstEntityByName(entityName);
        if (entity == 0) {
            if (required) throw new IllegalStateException("v25 layout entity missing: " + entityName);
            return false;
        }
        int instance = transforms.getInstance(entity);
        if (instance == 0) {
            if (required) throw new IllegalStateException("v25 layout transform missing: " + entityName);
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
