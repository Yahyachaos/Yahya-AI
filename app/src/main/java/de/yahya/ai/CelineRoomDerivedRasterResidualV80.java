package de.yahya.ai;

import android.opengl.Matrix;

import com.google.android.filament.Engine;
import com.google.android.filament.TransformManager;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Bounded residual owner for reference-derived foreground parts created by
 * CelineRoomForegroundPlantV80.
 *
 * Proof #1285 leaves two independent, reliably measured residuals: right-wall art is slightly
 * narrow/left and the foreground candle is slightly wide-low. These are generated geometry, not
 * immutable source furniture GLBs. The owner verifies their exact accepted local matrices and part
 * indices before applying one small affine correction. It never writes plant foliage, dresser,
 * source assets, camera, Celine, room shell, anchors or navigation.
 */
final class CelineRoomDerivedRasterResidualV80 {
    private static final int EXPECTED_PART_COUNT = 9;
    private static final int RIGHT_ART_INDEX = 3;
    private static final int CANDLE_INDEX = 8;
    private static final float BASE_MATRIX_TOLERANCE = 0.0025f;

    private static final float RIGHT_ART_BASE_X = 2.000000f;
    private static final float RIGHT_ART_BASE_Y = 1.772788f;
    private static final float RIGHT_ART_BASE_Z = 0.207086f;
    private static final float RIGHT_ART_YAW_DEG = -90.0f;
    // Relative exact-camera projection solve from #1285 bbox 0.859252..0.961614 /
    // 0.075031..0.282903 to target 0.858..0.969 / 0.076..0.283.
    private static final float RIGHT_ART_CANDIDATE_Y = 1.766978f;
    private static final float RIGHT_ART_CANDIDATE_Z = 0.216709f;
    private static final float RIGHT_ART_SCALE_X = 1.054015f;
    private static final float RIGHT_ART_SCALE_Y = 0.984100f;

    private static final float CANDLE_BASE_X = -0.836842f;
    private static final float CANDLE_BASE_Y = 0.902352f;
    private static final float CANDLE_BASE_Z = 3.020114f;
    // #1195->#1196 established +0.068m world Y -> -47.5px CALL response. #1285 needs
    // ~3.2px upward center correction; width/height are direct measured ratios.
    private static final float CANDLE_CANDIDATE_Y = 0.906940f;
    private static final float CANDLE_SCALE_X = 1.051440f;
    private static final float CANDLE_SCALE_Y = 0.995510f;

    private static final WeakHashMap<Celine3DView, Boolean> APPLIED = new WeakHashMap<>();

    private CelineRoomDerivedRasterResidualV80() {}

    static void apply(Celine3DView view, Engine engine) throws Exception {
        if (view == null || engine == null) return;
        synchronized (APPLIED) {
            if (APPLIED.containsKey(view)) return;
        }

        List<?> parts = foregroundParts(view);
        if (parts.size() != EXPECTED_PART_COUNT) {
            throw new IllegalStateException(
                    "derived raster residual expected " + EXPECTED_PART_COUNT
                            + " foreground parts, found=" + parts.size());
        }
        TransformManager transforms = engine.getTransformManager();

        int rightArtEntity = partEntity(parts.get(RIGHT_ART_INDEX));
        int candleEntity = partEntity(parts.get(CANDLE_INDEX));
        int rightArtInstance = transforms.getInstance(rightArtEntity);
        int candleInstance = transforms.getInstance(candleEntity);
        if (rightArtInstance == 0 || candleInstance == 0) {
            throw new IllegalStateException("derived raster residual transform missing");
        }

        float[] rightArtBase = trs(
                RIGHT_ART_BASE_X, RIGHT_ART_BASE_Y, RIGHT_ART_BASE_Z, RIGHT_ART_YAW_DEG,
                1.0f, 1.0f);
        float[] candleBase = trs(
                CANDLE_BASE_X, CANDLE_BASE_Y, CANDLE_BASE_Z, 0.0f, 1.0f, 1.0f);
        verifyMatrix(transforms.getTransform(rightArtInstance, new float[16]), rightArtBase,
                "right-wall-art");
        verifyMatrix(transforms.getTransform(candleInstance, new float[16]), candleBase,
                "foreground-candle");

        transforms.setTransform(rightArtInstance, trs(
                RIGHT_ART_BASE_X, RIGHT_ART_CANDIDATE_Y, RIGHT_ART_CANDIDATE_Z,
                RIGHT_ART_YAW_DEG, RIGHT_ART_SCALE_X, RIGHT_ART_SCALE_Y));
        transforms.setTransform(candleInstance, trs(
                CANDLE_BASE_X, CANDLE_CANDIDATE_Y, CANDLE_BASE_Z,
                0.0f, CANDLE_SCALE_X, CANDLE_SCALE_Y));

        synchronized (APPLIED) { APPLIED.put(view, Boolean.TRUE); }
        Celine3DDiagnostics.record(view.getContext(), "ROOM-155",
                "Abgeleitete Referenzdetails rastergenau nachgeführt",
                "authority=Proof#1285"
                        + " rightArt=small Y/Z/localXY affine"
                        + " candle=small Y/localXY affine"
                        + " sourceGLBsMutated=false plantFoliageMutated=false"
                        + " roomShell/camera/Celine/anchors unchanged=true");
    }

    static void release(Celine3DView view) {
        synchronized (APPLIED) { APPLIED.remove(view); }
    }

    private static List<?> foregroundParts(Celine3DView view) throws Exception {
        Field statesField = CelineRoomForegroundPlantV80.class.getDeclaredField("STATES");
        statesField.setAccessible(true);
        Object rawStates = statesField.get(null);
        if (!(rawStates instanceof Map)) {
            throw new IllegalStateException("foreground-derived STATES is not a map");
        }
        Object state = ((Map<?, ?>) rawStates).get(view);
        if (state == null) throw new IllegalStateException("foreground-derived state missing");
        Field partsField = state.getClass().getDeclaredField("parts");
        partsField.setAccessible(true);
        Object rawParts = partsField.get(state);
        if (!(rawParts instanceof List)) {
            throw new IllegalStateException("foreground-derived parts is not a list");
        }
        return (List<?>) rawParts;
    }

    private static int partEntity(Object part) throws Exception {
        if (part == null) throw new IllegalStateException("foreground-derived part missing");
        Field entityField = part.getClass().getDeclaredField("entity");
        entityField.setAccessible(true);
        int entity = entityField.getInt(part);
        if (entity == 0) throw new IllegalStateException("foreground-derived entity missing");
        return entity;
    }

    private static void verifyMatrix(float[] actual, float[] expected, String label) {
        float max = 0.0f;
        for (int i = 0; i < 16; i++) max = Math.max(max, Math.abs(actual[i] - expected[i]));
        if (max > BASE_MATRIX_TOLERANCE) {
            throw new IllegalStateException(
                    "derived raster residual baseline changed " + label + " maxDelta=" + max);
        }
    }

    private static float[] trs(
            float x, float y, float z, float yawDeg, float scaleX, float scaleY) {
        float[] matrix = new float[16];
        Matrix.setIdentityM(matrix, 0);
        Matrix.translateM(matrix, 0, x, y, z);
        if (yawDeg != 0.0f) Matrix.rotateM(matrix, 0, yawDeg, 0f, 1f, 0f);
        if (scaleX != 1.0f || scaleY != 1.0f) {
            Matrix.scaleM(matrix, 0, scaleX, scaleY, 1.0f);
        }
        return matrix;
    }
}
