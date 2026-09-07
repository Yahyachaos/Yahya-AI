package de.yahya.ai;

import android.opengl.Matrix;

import com.google.android.filament.Engine;
import com.google.android.filament.TransformManager;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Fail-closed residual owner for reference-derived wall art/candle parts.
 *
 * Right art and candle reuse exact successful actual-CALL responses from Proof #1295. The left art
 * pair is already horizontally close in Proof #1285; only its measured missing vertical tail is
 * corrected by scaling local Y and lowering both frames/prints together. These are generated room
 * details, not immutable source furniture GLBs.
 */
final class CelineRoomDerivedRasterResidualV80 {
    private static final int EXPECTED_PART_COUNT = 9;
    private static final int RIGHT_ART_INDEX = 3;
    private static final int LEFT_NEAR_FRAME_INDEX = 4;
    private static final int LEFT_NEAR_PRINT_INDEX = 5;
    private static final int LEFT_FAR_FRAME_INDEX = 6;
    private static final int LEFT_FAR_PRINT_INDEX = 7;
    private static final int CANDLE_INDEX = 8;
    private static final float BASE_MATRIX_TOLERANCE = 0.0025f;

    private static final float RIGHT_ART_BASE_X = 2.000000f;
    private static final float RIGHT_ART_BASE_Y = 1.772788f;
    private static final float RIGHT_ART_BASE_Z = 0.207086f;
    private static final float RIGHT_ART_YAW_DEG = -90.0f;
    private static final float RIGHT_ART_CANDIDATE_Y = 1.766978f;
    private static final float RIGHT_ART_CANDIDATE_Z = 0.216709f;
    private static final float RIGHT_ART_SCALE_X = 1.054015f;
    private static final float RIGHT_ART_SCALE_Y = 0.984100f;

    private static final float CANDLE_BASE_X = -0.836842f;
    private static final float CANDLE_BASE_Y = 0.902352f;
    private static final float CANDLE_BASE_Z = 3.020114f;
    private static final float CANDLE_CANDIDATE_Y = 0.906940f;
    private static final float CANDLE_SCALE_X = 1.051440f;
    private static final float CANDLE_SCALE_Y = 0.995510f;

    private static final float LEFT_ART_FRAME_X = -2.000000f;
    private static final float LEFT_ART_PRINT_X = -1.996000f;
    private static final float LEFT_ART_BASE_Y = 1.693847f;
    private static final float LEFT_ART_NEAR_Z = -0.167611f;
    private static final float LEFT_ART_FAR_Z = -0.439817f;
    private static final float LEFT_ART_YAW_DEG = 90.0f;
    // #1285: y=0.150062..0.265683; target y=0.150..0.271. Keep top nearly fixed by
    // increasing height 1.04652x while shifting screen center downward ~0.00263. The established
    // derived-part vertical response maps that to a bounded -0.00327 m room-local Y shift.
    private static final float LEFT_ART_CANDIDATE_Y = 1.690578f;
    private static final float LEFT_ART_SCALE_Y = 1.046520f;

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

        int rightArt = partEntity(parts.get(RIGHT_ART_INDEX));
        int nearFrame = partEntity(parts.get(LEFT_NEAR_FRAME_INDEX));
        int nearPrint = partEntity(parts.get(LEFT_NEAR_PRINT_INDEX));
        int farFrame = partEntity(parts.get(LEFT_FAR_FRAME_INDEX));
        int farPrint = partEntity(parts.get(LEFT_FAR_PRINT_INDEX));
        int candle = partEntity(parts.get(CANDLE_INDEX));

        verify(transforms, rightArt,
                trs(RIGHT_ART_BASE_X, RIGHT_ART_BASE_Y, RIGHT_ART_BASE_Z,
                        RIGHT_ART_YAW_DEG, 1f, 1f), "right-wall-art");
        verify(transforms, candle,
                trs(CANDLE_BASE_X, CANDLE_BASE_Y, CANDLE_BASE_Z, 0f, 1f, 1f),
                "foreground-candle");
        verify(transforms, nearFrame,
                trs(LEFT_ART_FRAME_X, LEFT_ART_BASE_Y, LEFT_ART_NEAR_Z,
                        LEFT_ART_YAW_DEG, 1f, 1f), "left-near-frame");
        verify(transforms, nearPrint,
                trs(LEFT_ART_PRINT_X, LEFT_ART_BASE_Y, LEFT_ART_NEAR_Z,
                        LEFT_ART_YAW_DEG, 1f, 1f), "left-near-print");
        verify(transforms, farFrame,
                trs(LEFT_ART_FRAME_X, LEFT_ART_BASE_Y, LEFT_ART_FAR_Z,
                        LEFT_ART_YAW_DEG, 1f, 1f), "left-far-frame");
        verify(transforms, farPrint,
                trs(LEFT_ART_PRINT_X, LEFT_ART_BASE_Y, LEFT_ART_FAR_Z,
                        LEFT_ART_YAW_DEG, 1f, 1f), "left-far-print");

        set(transforms, rightArt,
                trs(RIGHT_ART_BASE_X, RIGHT_ART_CANDIDATE_Y, RIGHT_ART_CANDIDATE_Z,
                        RIGHT_ART_YAW_DEG, RIGHT_ART_SCALE_X, RIGHT_ART_SCALE_Y));
        set(transforms, candle,
                trs(CANDLE_BASE_X, CANDLE_CANDIDATE_Y, CANDLE_BASE_Z,
                        0f, CANDLE_SCALE_X, CANDLE_SCALE_Y));
        set(transforms, nearFrame,
                trs(LEFT_ART_FRAME_X, LEFT_ART_CANDIDATE_Y, LEFT_ART_NEAR_Z,
                        LEFT_ART_YAW_DEG, 1f, LEFT_ART_SCALE_Y));
        set(transforms, nearPrint,
                trs(LEFT_ART_PRINT_X, LEFT_ART_CANDIDATE_Y, LEFT_ART_NEAR_Z,
                        LEFT_ART_YAW_DEG, 1f, LEFT_ART_SCALE_Y));
        set(transforms, farFrame,
                trs(LEFT_ART_FRAME_X, LEFT_ART_CANDIDATE_Y, LEFT_ART_FAR_Z,
                        LEFT_ART_YAW_DEG, 1f, LEFT_ART_SCALE_Y));
        set(transforms, farPrint,
                trs(LEFT_ART_PRINT_X, LEFT_ART_CANDIDATE_Y, LEFT_ART_FAR_Z,
                        LEFT_ART_YAW_DEG, 1f, LEFT_ART_SCALE_Y));

        synchronized (APPLIED) { APPLIED.put(view, Boolean.TRUE); }
        Celine3DDiagnostics.record(view.getContext(), "ROOM-155",
                "Abgeleitete Referenzdetails rastergenau nachgeführt",
                "authority=Proof#1285+#1295"
                        + " rightArt=proven1295 candle=proven1295"
                        + " leftArt=verticalMeasuredOnly"
                        + " sourceGLBsMutated=false roomShell/camera/Celine/anchors unchanged=true");
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
        Map<?, ?> states = (Map<?, ?>) rawStates;
        Object state;
        synchronized (states) { state = states.get(view); }
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

    private static void verify(
            TransformManager transforms, int entity, float[] expected, String label) {
        int instance = transforms.getInstance(entity);
        if (instance == 0) throw new IllegalStateException("derived residual transform missing " + label);
        float[] actual = transforms.getTransform(instance, new float[16]);
        float max = 0.0f;
        for (int i = 0; i < 16; i++) max = Math.max(max, Math.abs(actual[i] - expected[i]));
        if (max > BASE_MATRIX_TOLERANCE) {
            throw new IllegalStateException(
                    "derived raster residual baseline changed " + label + " maxDelta=" + max);
        }
    }

    private static void set(TransformManager transforms, int entity, float[] value) {
        int instance = transforms.getInstance(entity);
        if (instance == 0) throw new IllegalStateException("derived residual candidate transform missing");
        transforms.setTransform(instance, value);
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
