package de.yahya.ai;

import android.opengl.Matrix;

import com.google.android.filament.TransformManager;
import com.google.android.filament.gltfio.FilamentAsset;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Exact visual transfer of the user-approved v25 TRUE3D room checkpoint.
 *
 * Sole authorities:
 * - docs/celine/room-v25/v25-room-layout.json
 * - v25 preview sha256 ba5b33d14933b0a685030cc9ebb0e4d66be739ece70e3942cdd2f9d6834c8f9d
 *
 * The previous implementation applied deltas on top of the later v80 combined-room transforms.
 * That preserved later assembly state and produced a visibly different room even when individual
 * target numbers looked correct. This implementation instead writes the v25 parent-node TRS
 * absolutely for every visible furniture instance. The optimized meshes/textures stay unchanged;
 * only their parent transforms are replaced. No later measured-room coordinates participate.
 */
final class CelineRoomReferenceLayoutV80 {
    // v25 shell: local front +2.95 / local back -4.00 under the locked root world Z -4.00,
    // therefore world front -1.05 / world back -8.00 and total depth 6.95 m.
    private static final float SHELL_BACK_Z_OFFSET_M = -1.10f;
    private static final float SHELL_CENTER_Z_OFFSET_M = -0.525f;
    private static final float SHELL_DEPTH_SCALE_Z = 1.198275862f; // 6.95 / 5.80

    // Absolute v25 furniture parent-node TRS. Y values are the v25 renderer's exact floor/contact
    // placement expressed in room-local coordinates (world floor -1.55 + runtime root Y -1.55).
    private static final Spec BED =
            new Spec("room_bed", 1.81f, 0.613172780f, -1.82f,
                    1.46f, 1.46f, 1.46f, -90.0f);
    private static final Spec DRESSER =
            new Spec("room_dresser", -2.82f, 0.846802211f, -0.70f,
                    1.30f, 1.30f, 1.30f, 90.0f);
    private static final Spec LARGE_PLANT =
            new Spec("room_plant_large", -2.72f, 1.047276327f, -2.92f,
                    1.10f, 1.10f, 1.10f, 0.0f);
    private static final Spec SMALL_PLANT =
            new Spec("room_plant_small", 1.72f, 0.304679500f, -2.55f,
                    0.32f, 0.32f, 0.32f, 0.0f);
    private static final Spec LAMP =
            new Spec("room_floor_lamp", -1.62f, 0.533396744f, -3.18f,
                    0.56f, 0.56f, 0.56f, 0.0f);
    private static final Spec NIGHTSTAND_FRONT =
            new Spec("room_nightstand_front", 2.55f, 0.466378938f, -0.42f,
                    0.49f, 0.49f, 0.49f, 270.0f);
    private static final Spec NIGHTSTAND_BACK =
            new Spec("room_nightstand_back", 2.55f, 0.409271313f, -3.18f,
                    0.43f, 0.43f, 0.43f, 270.0f);
    private static final Spec CHAIR =
            new Spec("room_lounge_chair", -2.28f, 0.388613360f, -3.18f,
                    0.43f, 0.43f, 0.43f, 25.0f);
    private static final Spec RUG =
            new Spec("room_rug", 0.14f, 0.007724571f, -0.91f,
                    2.35f, 1.00f, 1.64f, 0.0f);
    private static final Spec TABLE =
            new Spec("room_foreground_table", 0.0f, 0.617091667f, 3.20f,
                    1.25f, 1.10f, 0.91f, 0.0f);
    private static final Spec WINDOW =
            new Spec("room_window_drapes", -0.82f, 1.43f, -3.80f,
                    1.50f, 1.50f, 1.50f, 0.0f);
    private static final Spec SHELF =
            new Spec("room_wall_shelf_books", 1.35f, 2.13f, -3.66f,
                    0.47f, 0.47f, 0.47f, 0.0f);
    private static final Spec MIRROR =
            new Spec("room_round_mirror", -3.08f, 1.73f, 0.35f,
                    0.49f, 0.49f, 0.49f, 90.0f);

    private static final Spec[] V25_FURNITURE = {
            WINDOW, SHELF, MIRROR, BED, DRESSER, LARGE_PLANT, CHAIR, LAMP,
            RUG, NIGHTSTAND_FRONT, NIGHTSTAND_BACK, SMALL_PLANT, TABLE
    };

    // Marker deltas remain data-alignment only. They do not affect the visual proof. They are kept
    // bounded so previously accepted interaction paths do not silently point to the old furniture.
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

            // Shell uses the immutable 5.8 m source shell only as geometry. Extend its back edge to
            // the v25 absolute bounds while keeping the camera/front edge fixed.
            translateParentLocal(asset, transforms, "room_back_wall",
                    0f, 0f, SHELL_BACK_Z_OFFSET_M, true);
            for (String shell : new String[]{
                    "room_floor", "room_left_wall", "room_right_wall", "room_ceiling"}) {
                translateParentLocal(asset, transforms, shell,
                        0f, 0f, SHELL_CENTER_Z_OFFSET_M, true);
                scaleLocalXyz(asset, transforms, shell,
                        1f, 1f, SHELL_DEPTH_SCALE_Z, true);
            }

            // Critical difference from the rejected transfer: do not read/modify the current
            // furniture matrices. Replace every visible furniture parent matrix with v25 TRS.
            for (Spec spec : V25_FURNITURE) {
                setAbsoluteTrs(asset, transforms, spec, true);
            }

            // Keep interaction markers approximately attached to the same visual objects. These
            // deltas are from immutable assembly-base anchors to the v25 positions only.
            int movedBedMarkers = 0;
            for (String marker : BED_MARKER_NODES) {
                if (translateParentLocal(asset, transforms, marker, -0.14f, 0f, -1.07f, false)) {
                    movedBedMarkers++;
                }
            }
            boolean movedDresserMarker = translateParentLocal(
                    asset, transforms, "dresser_anchor", 0.08f, 0f, -1.05f, false);
            boolean movedLampMarker = translateParentLocal(
                    asset, transforms, "lamp_anchor", -0.07f, 0f, -4.73f, false);
            int movedChairMarkers = 0;
            for (String marker : CHAIR_MARKER_NODES) {
                if (translateParentLocal(asset, transforms, marker, -0.53f, 0f, -2.18f, false)) {
                    movedChairMarkers++;
                }
            }
            translateParentLocal(asset, transforms,
                    "foreground_table_approach_anchor", 0f, 0f, 1.10f, false);
            translateParentLocal(asset, transforms,
                    "foreground_table_lean_anchor", 0f, 0f, 1.10f, false);
            translateParentLocal(asset, transforms,
                    "window_anchor", -1.27f, 0f, -1.097f, false);
            translateParentLocal(asset, transforms,
                    "shelf_anchor", 3.40f, 0f, -1.098f, false);
            translateParentLocal(asset, transforms,
                    "mirror_anchor", 0.04f, 0f, 0f, false);

            synchronized (APPLIED) {
                APPLIED.put(view, asset);
            }
            Celine3DDiagnostics.record(view.getContext(), "ROOM-150",
                    "v25 TRUE3D Absolutlayout aktiv",
                    "authority=v25-json+preview-ba5b33d14933"
                            + " shellWorldZ=-1.05..-8.00"
                            + " furniture=13 absoluteTRS"
                            + " bed=(1.81,-1.82) yaw=-90 scale=1.46"
                            + " chair=(-2.28,-3.18) yaw=25 scale=0.43"
                            + " tableZ=3.20 window=(-0.82,-3.80) shelf=(1.35,-3.66)"
                            + " nightstandsYaw=270"
                            + " bedMarkersMoved=" + movedBedMarkers
                            + " chairMarkersMoved=" + movedChairMarkers
                            + " dresserMarkerMoved=" + movedDresserMarker
                            + " lampMarkerMoved=" + movedLampMarker
                            + " laterMeasuredRoom=false deltaFurniture=false");
        } catch (Throwable error) {
            Celine3DDiagnostics.error(view.getContext(), "ROOM-159",
                    "v25 TRUE3D Absolutlayout FEHLER", error);
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

    private static void setAbsoluteTrs(FilamentAsset asset, TransformManager transforms,
                                       Spec spec, boolean required) {
        int entity = asset.getFirstEntityByName(spec.entityName);
        if (entity == 0) {
            if (required) {
                throw new IllegalStateException("v25 absolute entity missing: " + spec.entityName);
            }
            return;
        }
        int instance = transforms.getInstance(entity);
        if (instance == 0) {
            if (required) {
                throw new IllegalStateException("v25 absolute transform missing: " + spec.entityName);
            }
            return;
        }

        float[] matrix = new float[16];
        Matrix.setIdentityM(matrix, 0);
        Matrix.translateM(matrix, 0, spec.x, spec.y, spec.z);
        if (spec.yawDeg != 0.0f) {
            Matrix.rotateM(matrix, 0, spec.yawDeg, 0f, 1f, 0f);
        }
        Matrix.scaleM(matrix, 0, spec.sx, spec.sy, spec.sz);
        transforms.setTransform(instance, matrix);
    }

    private static boolean translateParentLocal(FilamentAsset asset,
                                                TransformManager transforms,
                                                String entityName,
                                                float deltaX, float deltaY, float deltaZ,
                                                boolean required) {
        int entity = asset.getFirstEntityByName(entityName);
        if (entity == 0) {
            if (required) {
                throw new IllegalStateException("v25 layout entity missing: " + entityName);
            }
            return false;
        }
        int instance = transforms.getInstance(entity);
        if (instance == 0) {
            if (required) {
                throw new IllegalStateException("v25 layout transform missing: " + entityName);
            }
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

    private static boolean scaleLocalXyz(FilamentAsset asset,
                                         TransformManager transforms,
                                         String entityName,
                                         float scaleX, float scaleY, float scaleZ,
                                         boolean required) {
        int entity = asset.getFirstEntityByName(entityName);
        if (entity == 0) {
            if (required) {
                throw new IllegalStateException("v25 layout entity missing: " + entityName);
            }
            return false;
        }
        int instance = transforms.getInstance(entity);
        if (instance == 0) {
            if (required) {
                throw new IllegalStateException("v25 layout transform missing: " + entityName);
            }
            return false;
        }
        float[] base = transforms.getTransform(instance, new float[16]);
        float[] scale = new float[16];
        float[] adjusted = new float[16];
        Matrix.setIdentityM(scale, 0);
        Matrix.scaleM(scale, 0, scaleX, scaleY, scaleZ);
        Matrix.multiplyMM(adjusted, 0, base, 0, scale, 0);
        transforms.setTransform(instance, adjusted);
        return true;
    }

    private static final class Spec {
        final String entityName;
        final float x;
        final float y;
        final float z;
        final float sx;
        final float sy;
        final float sz;
        final float yawDeg;

        Spec(String entityName,
             float x, float y, float z,
             float sx, float sy, float sz,
             float yawDeg) {
            this.entityName = entityName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.yawDeg = yawDeg;
        }
    }
}
