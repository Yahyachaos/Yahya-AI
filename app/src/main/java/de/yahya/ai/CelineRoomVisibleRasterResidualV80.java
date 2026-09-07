package de.yahya.ai;

import android.opengl.Matrix;

import com.google.android.filament.Engine;
import com.google.android.filament.TransformManager;
import com.google.android.filament.gltfio.FilamentAsset;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Bounded visible-raster residual owner for the measured room-furniture batch.
 *
 * Proof #1270 is the current exact CALL raster after the accepted chair/window solves and the first
 * conservative large-plant half-step. The whole-scene scoreboard remained FLAT, so continuing with
 * one-object micro-patches cannot cross the material-improvement threshold. This owner therefore
 * closes the three largest separable furniture residuals that share the same room-asset transform
 * path: large plant, lounge chair and wall shelf. The rug is deliberately excluded because its
 * smooth-surface/material strategy has already been rejected by real visual evidence.
 *
 * Every write is absolute and fail-closed against the expected current layout matrix. Y/Z/scale/yaw
 * axes that are already on target remain frozen per object. Source GLB bytes, room shell, camera,
 * Celine and anchors are untouched.
 */
final class CelineRoomVisibleRasterResidualV80 {
    private static final String LARGE_PLANT = "room_plant_large";
    private static final String CHAIR = "room_lounge_chair";
    private static final String SHELF = "room_wall_shelf_books";

    // Accepted layout baselines before this residual owner runs.
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

    // Proof #1266 -> #1270 gives an empirical plant raster response. Solving that measured response
    // from #1270 x=0.159449..0.253937 toward target x=0.132..0.247 requires the following absolute
    // X/SX pair. Vertical envelope and all remaining axes are frozen.
    private static final Spec PLANT_CANDIDATE = new Spec(
            LARGE_PLANT,
            -2.354559f, 0.977047f, -1.399038f,
            1.361167f, 1.026340f, 0.882475f, -15.292969f);

    // #1262 -> #1266 supplies the chair response for simultaneous X/SX. #1270 remains slightly too
    // far right and narrow (0.221457..0.334646 vs 0.217..0.333); close only that residual.
    private static final Spec CHAIR_CANDIDATE = new Spec(
            CHAIR,
            -1.674301f, 0.457133f, -2.107537f,
            0.510543f, 0.505830f, 0.433192f, 170.375000f);

    // #1270 shelf horizontal raster is already on target. Extend only its visible vertical span from
    // 0.067651 toward 0.080 and translate Y with the established CALL vertical camera response so the
    // center moves from 0.209717 toward 0.215 without disturbing X/Z/yaw.
    private static final Spec SHELF_CANDIDATE = new Spec(
            SHELF,
            1.439041f, 1.796354f, -1.916250f,
            0.421335f, 0.428954f, 0.351875f, 5.820313f);

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
                "Gemessenen Möbel-Rasterrest gebündelt geschlossen",
                "authority=Proof#1270 target=Refernzbild.png"
                        + " plant=X/SX"
                        + " chair=X/SX"
                        + " shelf=Y/SY"
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