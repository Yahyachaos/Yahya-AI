package de.yahya.ai;

import android.opengl.Matrix;

import com.google.android.filament.Engine;
import com.google.android.filament.TransformManager;
import com.google.android.filament.gltfio.FilamentAsset;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Fail-closed owner for secondary room-raster corrections independently proven by Real Candidate
 * #1295. The rejected large-plant X/yaw strategy is intentionally excluded: this owner only applies
 * the chair and shelf corrections that improved in the actual CALL proof.
 *
 * Every transform is absolute and verified against CelineRoomReferenceLayoutV80 before writing.
 * Source GLB bytes, accepted large-plant state, rug source TRS, room shell, camera/FOV, Celine,
 * anchors and navigation remain untouched.
 */
final class CelineRoomSecondaryRasterResidualV80 {
    private static final String CHAIR = "room_lounge_chair";
    private static final String SHELF = "room_wall_shelf_books";
    private static final float BASE_MATRIX_TOLERANCE = 0.0025f;

    private static final Spec CHAIR_BASE = new Spec(
            CHAIR,
            -1.643872f, 0.457133f, -2.107537f,
            0.495673f, 0.505830f, 0.433192f, 170.375000f);
    private static final Spec CHAIR_CANDIDATE = new Spec(
            CHAIR,
            -1.671258f, 0.457133f, -2.107537f,
            0.509056f, 0.505830f, 0.433192f, 170.375000f);

    private static final Spec SHELF_BASE = new Spec(
            SHELF,
            1.439041f, 1.844809f, -1.916250f,
            0.421335f, 0.362738f, 0.351875f, 5.820313f);
    private static final Spec SHELF_CANDIDATE = new Spec(
            SHELF,
            1.439041f, 1.823316f, -1.916250f,
            0.421335f, 0.392109f, 0.351875f, 5.820313f);

    private static final WeakHashMap<Celine3DView, Boolean> APPLIED = new WeakHashMap<>();

    private CelineRoomSecondaryRasterResidualV80() {}

    static void apply(Celine3DView view, Engine engine) throws Exception {
        if (view == null || engine == null) return;
        synchronized (APPLIED) {
            if (APPLIED.containsKey(view)) return;
        }

        FilamentAsset asset = currentRoomAsset(view);
        if (asset == null) throw new IllegalStateException("secondary raster residual: room asset fehlt");
        TransformManager transforms = engine.getTransformManager();

        verifyBaseline(asset, transforms, CHAIR_BASE);
        verifyBaseline(asset, transforms, SHELF_BASE);
        applySpec(asset, transforms, CHAIR_CANDIDATE);
        applySpec(asset, transforms, SHELF_CANDIDATE);

        synchronized (APPLIED) { APPLIED.put(view, Boolean.TRUE); }
        Celine3DDiagnostics.record(view.getContext(), "ROOM-156",
                "Bewährte sekundäre Rasterkorrekturen getrennt angewendet",
                "authority=RealCandidate#1295 chair=true shelf=true"
                        + " rejectedPlantStrategyRetried=false"
                        + " sourceGLBMutated=false rugSourceTRSChanged=false"
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
                    "secondary raster residual: entity fehlt " + expectedSpec.entityName);
        }
        int instance = transforms.getInstance(entity);
        if (instance == 0) {
            throw new IllegalStateException(
                    "secondary raster residual: transform fehlt " + expectedSpec.entityName);
        }
        float maxDelta = maxAbsDelta(
                transforms.getTransform(instance, new float[16]), absoluteTrs(expectedSpec));
        if (maxDelta > BASE_MATRIX_TOLERANCE) {
            throw new IllegalStateException(
                    "secondary raster residual: baseline changed entity="
                            + expectedSpec.entityName + " maxDelta=" + maxDelta);
        }
    }

    private static void applySpec(
            FilamentAsset asset, TransformManager transforms, Spec candidate) {
        int entity = asset.getFirstEntityByName(candidate.entityName);
        if (entity == 0) {
            throw new IllegalStateException(
                    "secondary raster residual: candidate entity fehlt " + candidate.entityName);
        }
        int instance = transforms.getInstance(entity);
        if (instance == 0) {
            throw new IllegalStateException(
                    "secondary raster residual: candidate transform fehlt " + candidate.entityName);
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
