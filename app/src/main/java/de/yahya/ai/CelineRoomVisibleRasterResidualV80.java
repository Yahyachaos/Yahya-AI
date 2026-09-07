package de.yahya.ai;

import android.opengl.Matrix;

import com.google.android.filament.Engine;
import com.google.android.filament.TransformManager;
import com.google.android.filament.gltfio.FilamentAsset;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Bounded visible-raster residual owner for already measured room furniture.
 *
 * Proof #1266 measured the unchanged large-plant visible raster on the exact 1016x813 CALL stage at
 * x=0.161417..0.237205 / y=0.207872..0.535055, while Refernzbild.png requires
 * x=0.132..0.247 / y=0.205..0.535. The vertical envelope is already effectively on target; the
 * remaining reliable medium-confidence error is horizontal width/center only.
 *
 * Earlier all-X/Z plant corrections showed a non-linear visible-width response and overshot. This
 * candidate therefore uses a conservative half-step: widen local X only by half of the measured
 * width-ratio residual and shift world X by half of the established chair CALL-raster Jacobian.
 * Y/Z, local Y/Z scale, yaw, source GLB bytes, room shell, camera and Celine remain frozen. The
 * expected accepted baseline matrix is checked before writing so this owner fails closed instead of
 * stacking on an unknown concurrent transform.
 */
final class CelineRoomVisibleRasterResidualV80 {
    private static final String LARGE_PLANT = "room_plant_large";

    private static final float BASE_X = -2.067954f;
    private static final float BASE_Y = 0.977047f;
    private static final float BASE_Z = -1.399038f;
    private static final float BASE_SX = 0.882475f;
    private static final float BASE_SY = 1.026340f;
    private static final float BASE_SZ = 0.882475f;
    private static final float BASE_YAW_DEG = -15.292969f;

    // Half-step from Proof #1266 visible width 0.075788 toward target 0.115000.
    private static final float CANDIDATE_X = -2.107476f;
    private static final float CANDIDATE_SX = 1.110767f;

    private static final float BASE_MATRIX_TOLERANCE = 0.0025f;
    private static final WeakHashMap<Celine3DView, Boolean> APPLIED = new WeakHashMap<>();

    private CelineRoomVisibleRasterResidualV80() {}

    static void apply(Celine3DView view, Engine engine) throws Exception {
        if (view == null || engine == null) return;
        synchronized (APPLIED) {
            if (APPLIED.containsKey(view)) return;
        }

        FilamentAsset asset = currentRoomAsset(view);
        if (asset == null) throw new IllegalStateException("visible-raster residual: room asset fehlt");
        int entity = asset.getFirstEntityByName(LARGE_PLANT);
        if (entity == 0) throw new IllegalStateException("visible-raster residual: large plant fehlt");
        TransformManager transforms = engine.getTransformManager();
        int instance = transforms.getInstance(entity);
        if (instance == 0) throw new IllegalStateException("visible-raster residual: plant transform fehlt");

        float[] current = transforms.getTransform(instance, new float[16]);
        float[] expected = absoluteTrs(BASE_X, BASE_Y, BASE_Z,
                BASE_SX, BASE_SY, BASE_SZ, BASE_YAW_DEG);
        float maxDelta = maxAbsDelta(current, expected);
        if (maxDelta > BASE_MATRIX_TOLERANCE) {
            throw new IllegalStateException(
                    "visible-raster residual: plant baseline changed maxDelta=" + maxDelta);
        }

        float[] candidate = absoluteTrs(CANDIDATE_X, BASE_Y, BASE_Z,
                CANDIDATE_SX, BASE_SY, BASE_SZ, BASE_YAW_DEG);
        transforms.setTransform(instance, candidate);
        synchronized (APPLIED) { APPLIED.put(view, Boolean.TRUE); }

        Celine3DDiagnostics.record(view.getContext(), "ROOM-154",
                "Große Pflanze sichtbaren Rasterrest halbiert",
                "CALL#1266 visibleX=0.161417..0.237205 targetX=0.132..0.247"
                        + " baseX=" + BASE_X + " candidateX=" + CANDIDATE_X
                        + " baseSX=" + BASE_SX + " candidateSX=" + CANDIDATE_SX
                        + " Y/Z/SY/SZ/yaw unchanged=true sourceGLBMutated=false"
                        + " camera/Celine/anchors unchanged=true");
    }

    static void release(Celine3DView view) {
        synchronized (APPLIED) { APPLIED.remove(view); }
    }

    private static float[] absoluteTrs(float x, float y, float z,
                                       float sx, float sy, float sz, float yawDeg) {
        float[] matrix = new float[16];
        Matrix.setIdentityM(matrix, 0);
        Matrix.translateM(matrix, 0, x, y, z);
        Matrix.rotateM(matrix, 0, yawDeg, 0f, 1f, 0f);
        Matrix.scaleM(matrix, 0, sx, sy, sz);
        return matrix;
    }

    private static float maxAbsDelta(float[] a, float[] b) {
        float max = 0.0f;
        for (int i = 0; i < 16; i++) max = Math.max(max, Math.abs(a[i] - b[i]));
        return max;
    }

    private static FilamentAsset currentRoomAsset(Celine3DView view) throws Exception {
        Field statesField = CelineRoomEnvironmentV80.class.getDeclaredField("STATES");
        statesField.setAccessible(true);
        Object rawStates = statesField.get(null);
        if (!(rawStates instanceof Map)) return null;
        Map<?, ?> states = (Map<?, ?>) rawStates;
        Object state;
        synchronized (states) { state = states.get(view); }
        if (state == null) return null;
        Field assetField = state.getClass().getDeclaredField("roomAsset");
        assetField.setAccessible(true);
        return (FilamentAsset) assetField.get(state);
    }
}
