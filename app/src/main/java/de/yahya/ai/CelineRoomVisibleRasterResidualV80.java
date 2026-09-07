package de.yahya.ai;

import android.opengl.Matrix;

import com.google.android.filament.Engine;
import com.google.android.filament.TransformManager;
import com.google.android.filament.gltfio.FilamentAsset;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Fail-closed visible-raster residual owner for the accepted source furniture.
 *
 * The accepted large-plant half-step from Proof #1285 is preserved exactly. Proof #1295 rejected a
 * different plant X/yaw strategy, but independently measured useful chair and shelf responses. This
 * owner keeps the accepted plant half-step and applies only those proven non-plant residuals plus a
 * direct vertical mirror-height solve from the same corrected-expanded reference measurement set.
 * Rug, source GLB bytes, room shell, camera/FOV, Celine, anchors and navigation remain untouched.
 */
final class CelineRoomVisibleRasterResidualV80 {
    private static final String LARGE_PLANT = "room_plant_large";
    private static final String CHAIR = "room_lounge_chair";
    private static final String SHELF = "room_wall_shelf_books";
    private static final String MIRROR = "room_round_mirror";

    private static final Spec PLANT_BASE = new Spec(
            LARGE_PLANT,
            -2.067954f, 0.977047f, -1.399038f,
            0.882475f, 1.026340f, 0.882475f, -15.292969f);
    // Accepted #1285 runtime half-step. Do not change as part of the rejected #1295 plant strategy.
    private static final Spec PLANT_ACCEPTED = new Spec(
            LARGE_PLANT,
            -2.107476f, 0.977047f, -1.399038f,
            1.110767f, 1.026340f, 0.882475f, -15.292969f);

    private static final Spec CHAIR_BASE = new Spec(
            CHAIR,
            -1.643872f, 0.457133f, -2.107537f,
            0.495673f, 0.505830f, 0.433192f, 170.375000f);
    // Proof #1295 actual CALL: x=0.217520..0.334646 vs target 0.217..0.333.
    private static final Spec CHAIR_CANDIDATE = new Spec(
            CHAIR,
            -1.671258f, 0.457133f, -2.107537f,
            0.509056f, 0.505830f, 0.433192f, 170.375000f);

    private static final Spec SHELF_BASE = new Spec(
            SHELF,
            1.439041f, 1.844809f, -1.916250f,
            0.421335f, 0.362738f, 0.351875f, 5.820313f);
    // Proof #1295 actual CALL: bottom=0.255843 vs target 0.255, with horizontal/top fit retained.
    private static final Spec SHELF_CANDIDATE = new Spec(
            SHELF,
            1.439041f, 1.823316f, -1.916250f,
            0.421335f, 0.392109f, 0.351875f, 5.820313f);

    private static final Spec MIRROR_BASE = new Spec(
            MIRROR,
            -2.099133f, 1.606177f, 0.471762f,
            0.406764f, 0.406764f, 0.406764f, -85.984561f);
    // #1285 mirror is already centered/width-correct: y=0.097171..0.334563 vs target 0.095..0.337.
    // Scale only local Y by target/current visible-height ratio 0.242/0.2373923739.
    private static final Spec MIRROR_CANDIDATE = new Spec(
            MIRROR,
            -2.099133f, 1.606177f, 0.471762f,
            0.406764f, 0.414659f, 0.406764f, -85.984561f);

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
        TransformManager transforms = engine.getTransformManager();

        verifyBaseline(asset, transforms, PLANT_BASE);
        verifyBaseline(asset, transforms, CHAIR_BASE);
        verifyBaseline(asset, transforms, SHELF_BASE);
        verifyBaseline(asset, transforms, MIRROR_BASE);

        applySpec(asset, transforms, PLANT_ACCEPTED);
        applySpec(asset, transforms, CHAIR_CANDIDATE);
        applySpec(asset, transforms, SHELF_CANDIDATE);
        applySpec(asset, transforms, MIRROR_CANDIDATE);
        synchronized (APPLIED) { APPLIED.put(view, Boolean.TRUE); }

        Celine3DDiagnostics.record(view.getContext(), "ROOM-154",
                "Korrigierten Möbel-Rasterrest gebündelt angewendet",
                "authority=Proof#1285+#1295"
                        + " plantAccepted1285=true rejected1295PlantStrategyRetried=false"
                        + " chair=proven1295 shelf=proven1295 mirror=verticalOnly"
                        + " rugStrategyRetried=false sourceGLBMutated=false"
                        + " roomShell/camera/Celine/anchors unchanged=true");
    }

    static void release(Celine3DView view) {
        synchronized (APPLIED) { APPLIED.remove(view); }
    }

    private static void verifyBaseline(
            FilamentAsset asset, TransformManager transforms, Spec expectedSpec) {
        int entity = asset.getFirstEntityByName(expectedSpec.entityName);
        if (entity == 0) {
            throw new IllegalStateException(
                    "visible-raster residual: entity fehlt " + expectedSpec.entityName);
        }
        int instance = transforms.getInstance(entity);
        if (instance == 0) {
            throw new IllegalStateException(
                    "visible-raster residual: transform fehlt " + expectedSpec.entityName);
        }
        float[] current = transforms.getTransform(instance, new float[16]);
        float[] expected = absoluteTrs(expectedSpec);
        float maxDelta = maxAbsDelta(current, expected);
        if (maxDelta > BASE_MATRIX_TOLERANCE) {
            throw new IllegalStateException(
                    "visible-raster residual: baseline changed entity="
                            + expectedSpec.entityName + " maxDelta=" + maxDelta);
        }
    }

    private static void applySpec(
            FilamentAsset asset, TransformManager transforms, Spec candidate) {
        int entity = asset.getFirstEntityByName(candidate.entityName);
        if (entity == 0) {
            throw new IllegalStateException(
                    "visible-raster residual: candidate entity fehlt " + candidate.entityName);
        }
        int instance = transforms.getInstance(entity);
        if (instance == 0) {
            throw new IllegalStateException(
                    "visible-raster residual: candidate transform fehlt " + candidate.entityName);
        }
        transforms.setTransform(instance, absoluteTrs(candidate));
    }

    private static float[] absoluteTrs(Spec spec) {
        float[] matrix = new float[16];
        Matrix.setIdentityM(matrix, 0);
        Matrix.translateM(matrix, 0, spec.x, spec.y, spec.z);
        Matrix.rotateM(matrix, 0, spec.yawDeg, 0f, 1f, 0f);
        Matrix.scaleM(matrix, 0, spec.sx, spec.sy, spec.sz);
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
             float sx, float sy, float sz, float yawDeg) {
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
