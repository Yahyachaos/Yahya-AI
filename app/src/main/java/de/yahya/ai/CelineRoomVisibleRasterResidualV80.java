package de.yahya.ai;

import android.opengl.Matrix;

import com.google.android.filament.Engine;
import com.google.android.filament.TransformManager;
import com.google.android.filament.gltfio.FilamentAsset;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Fail-closed visible-raster residual owner for one coherent reference-room geometry batch.
 *
 * Proof #1285 is the current corrected-expanded baseline. The rug remains STOP-LOSS and is not
 * touched. The large plant is a single fused mesh (one node/mesh/primitive), so the rejected #1274
 * aggressive X/SX solve cannot preserve pot/trunk semantics independently. This candidate instead
 * keeps the accepted #1270 X-scale, moves X only a bounded additional amount and relaxes yaw toward
 * the reference-facing silhouette. A projection/occlusion check predicts x~0.138..0.247 while
 * keeping the low pot/trunk band far closer to its accepted location than #1274.
 *
 * Chair and shelf use only bounded interpolation inside already observed #1285 -> #1274 real-raster
 * responses: 90% of the chair horizontal step, and 44.36% of the rejected shelf vertical step.
 * Every write is absolute and verifies the accepted CelineRoomReferenceLayoutV80 matrix first.
 * Source GLB bytes, room shell, camera/FOV, Celine, anchors and navigation remain untouched.
 */
final class CelineRoomVisibleRasterResidualV80 {
    private static final String LARGE_PLANT = "room_plant_large";
    private static final String CHAIR = "room_lounge_chair";
    private static final String SHELF = "room_wall_shelf_books";

    private static final Spec PLANT_BASE = new Spec(
            LARGE_PLANT,
            -2.067954f, 0.977047f, -1.399038f,
            0.882475f, 1.026340f, 0.882475f, -15.292969f);
    private static final Spec CHAIR_BASE = new Spec(
            CHAIR,
            -1.643872f, 0.457133f, -2.107537f,
            0.495673f, 0.505830f, 0.433192f, 170.375000f);
    private static final Spec SHELF_BASE = new Spec(
            SHELF,
            1.439041f, 1.844809f, -1.916250f,
            0.421335f, 0.362738f, 0.351875f, 5.820313f);

    // #1285 current runtime is X=-2.107476/SX=1.110767/yaw=-15.292969 after the accepted
    // half-step. Keep SX, Y/Z/SY/SZ fixed and use a new yaw-inclusive, occlusion-constrained solve.
    private static final Spec PLANT_CANDIDATE = new Spec(
            LARGE_PLANT,
            -2.180000f, 0.977047f, -1.399038f,
            1.110767f, 1.026340f, 0.882475f, -2.500000f);

    // #1274 proved the full chair step improves its horizontal envelope; retain only 90% so the
    // left edge lands at ~0.21703 instead of overshooting the 0.217 target.
    private static final Spec CHAIR_CANDIDATE = new Spec(
            CHAIR,
            -1.671258f, 0.457133f, -2.107537f,
            0.509056f, 0.505830f, 0.433192f, 170.375000f);

    // #1274 shelf bottom=0.26937 overshot target 0.255 from #1285 bottom=0.24354. Interpolate
    // 44.36% of that observed Y/SY response, leaving horizontal/depth/orientation untouched.
    private static final Spec SHELF_CANDIDATE = new Spec(
            SHELF,
            1.439041f, 1.823316f, -1.916250f,
            0.421335f, 0.392109f, 0.351875f, 5.820313f);

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

        applySpec(asset, transforms, PLANT_CANDIDATE);
        applySpec(asset, transforms, CHAIR_CANDIDATE);
        applySpec(asset, transforms, SHELF_CANDIDATE);
        synchronized (APPLIED) { APPLIED.put(view, Boolean.TRUE); }

        Celine3DDiagnostics.record(view.getContext(), "ROOM-154",
                "Gemessenen Möbel-Rasterrest konservativ gebündelt",
                "authority=Proof#1285 target=Refernzbild.png"
                        + " plant=fusedMesh newStrategy X/yaw with SX frozen"
                        + " plantPredictedX~0.138..0.247 target=0.132..0.247"
                        + " chairStep=90pct"
                        + " shelfStep=44.36pct"
                        + " rejected1274AggressivePlantStrategyRetried=false"
                        + " rugStrategyRetried=false"
                        + " sourceGLBMutated=false"
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
