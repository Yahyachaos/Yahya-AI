package de.yahya.ai;

import android.opengl.Matrix;

import com.google.android.filament.Colors;
import com.google.android.filament.Engine;
import com.google.android.filament.MaterialInstance;
import com.google.android.filament.RenderableManager;
import com.google.android.filament.TransformManager;
import com.google.android.filament.gltfio.FilamentAsset;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Bounded visible-raster residual owner for already measured room furniture plus the first bounded
 * room-only material witness after geometry STOP-LOSS.
 *
 * Proof #1266 measured the unchanged large-plant visible raster on the exact 1016x813 CALL stage at
 * x=0.161417..0.237205 / y=0.207872..0.535055, while Refernzbild.png requires
 * x=0.132..0.247 / y=0.205..0.535. The accepted plant half-step below remains byte-for-byte in
 * behavior; no rejected plant strategy is reopened.
 *
 * Proof #1314 closed the derived/procedural foliage family. The read-only geometry expansion then
 * found no newly reliable non-STOP-LOSS geometry batch that could honestly clear the scoreboard gate.
 * `CELINE_ROOM_BED_MATERIAL_WITNESS_1314.json` identifies a large room-only appearance residual:
 * accepted duvet/headboard medians are materially darker than the canonical reference. Apply only the
 * geometric half-step of the measured linear-light target/current color ratio to a duplicated
 * `room_bed` material instance. Textures, roughness, normals, AO, source GLB bytes, bed TRS, room
 * shell, global renderer light, camera, Celine and anchors stay untouched.
 */
final class CelineRoomVisibleRasterResidualV80 {
    private static final String LARGE_PLANT = "room_plant_large";
    private static final String BED = "room_bed";

    private static final float BASE_X = -2.067954f;
    private static final float BASE_Y = 0.977047f;
    private static final float BASE_Z = -1.399038f;
    private static final float BASE_SX = 0.882475f;
    private static final float BASE_SY = 1.026340f;
    private static final float BASE_SZ = 0.882475f;
    private static final float BASE_YAW_DEG = -15.292969f;

    // Accepted half-step from Proof #1266 visible width 0.075788 toward target 0.115000.
    private static final float CANDIDATE_X = -2.107476f;
    private static final float CANDIDATE_SX = 1.110767f;

    // Bounded room-bed material half-step from CELINE_ROOM_BED_MATERIAL_WITNESS_1314.json.
    private static final float BED_RED = 1.722159f;
    private static final float BED_GREEN = 1.525363f;
    private static final float BED_BLUE = 1.239566f;

    private static final float BASE_MATRIX_TOLERANCE = 0.0025f;
    private static final WeakHashMap<Celine3DView, Boolean> APPLIED = new WeakHashMap<>();
    private static final WeakHashMap<Celine3DView, BedMaterialState> BED_STATES = new WeakHashMap<>();

    private CelineRoomVisibleRasterResidualV80() {}

    static void apply(Celine3DView view, Engine engine) throws Exception {
        if (view == null || engine == null) return;
        synchronized (APPLIED) {
            if (APPLIED.containsKey(view)) return;
        }

        FilamentAsset asset = currentRoomAsset(view);
        if (asset == null) throw new IllegalStateException("visible-raster residual: room asset fehlt");

        applyBedMaterial(view, engine, asset);
        boolean success = false;
        try {
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
            success = true;

            Celine3DDiagnostics.record(view.getContext(), "ROOM-154",
                    "Große Pflanze sichtbar akzeptiert · Bettmaterial warm halbiert",
                    "plant=accepted#1266-half-step unchanged"
                            + " bedBaseColor=" + BED_RED + "," + BED_GREEN + "," + BED_BLUE
                            + " bedTexture/roughness/normal/AO/TRS unchanged=true"
                            + " sourceGLBMutated=false globalLightChanged=false"
                            + " camera/Celine/anchors unchanged=true");
        } finally {
            if (!success) releaseBedMaterial(view);
        }
    }

    static void release(Celine3DView view) {
        releaseBedMaterial(view);
        synchronized (APPLIED) { APPLIED.remove(view); }
    }

    private static void applyBedMaterial(Celine3DView view, Engine engine, FilamentAsset asset) {
        synchronized (BED_STATES) {
            if (BED_STATES.containsKey(view)) return;
        }
        int entity = asset.getFirstEntityByName(BED);
        if (entity == 0) throw new IllegalStateException("bed material: room_bed fehlt");
        RenderableManager renderables = engine.getRenderableManager();
        int instance = renderables.getInstance(entity);
        if (instance == 0) throw new IllegalStateException("bed material: renderable fehlt");
        int count = renderables.getPrimitiveCount(instance);
        if (count <= 0) throw new IllegalStateException("bed material: primitives fehlen");

        MaterialInstance[] originals = new MaterialInstance[count];
        MaterialInstance[] replacements = new MaterialInstance[count];
        int bound = 0;
        try {
            for (int primitive = 0; primitive < count; primitive++) {
                MaterialInstance original = renderables.getMaterialInstanceAt(instance, primitive);
                if (original == null) throw new IllegalStateException("bed material: original fehlt " + primitive);
                MaterialInstance replacement = MaterialInstance.duplicate(
                        original, "v80-reference-bed-half-step-" + primitive);
                if (!replacement.getMaterial().hasParameter("baseColorFactor")) {
                    try { engine.destroyMaterialInstance(replacement); } catch (Throwable ignored) {}
                    throw new IllegalStateException("bed material: baseColorFactor fehlt " + primitive);
                }
                replacement.setParameter("baseColorFactor", Colors.RgbaType.LINEAR,
                        BED_RED, BED_GREEN, BED_BLUE, 1.0f);
                originals[primitive] = original;
                replacements[primitive] = replacement;
                renderables.setMaterialInstanceAt(instance, primitive, replacement);
                bound++;
            }
            synchronized (BED_STATES) {
                BED_STATES.put(view, new BedMaterialState(engine, entity, originals, replacements));
            }
        } catch (Throwable error) {
            for (int primitive = 0; primitive < bound; primitive++) {
                try { renderables.setMaterialInstanceAt(instance, primitive, originals[primitive]); }
                catch (Throwable ignored) {}
            }
            for (MaterialInstance replacement : replacements) {
                if (replacement != null) {
                    try { engine.destroyMaterialInstance(replacement); } catch (Throwable ignored) {}
                }
            }
            throw error;
        }
    }

    private static void releaseBedMaterial(Celine3DView view) {
        BedMaterialState state;
        synchronized (BED_STATES) { state = BED_STATES.remove(view); }
        if (state == null) return;
        try {
            RenderableManager renderables = state.engine.getRenderableManager();
            int instance = renderables.getInstance(state.entity);
            if (instance != 0) {
                int count = Math.min(renderables.getPrimitiveCount(instance), state.originals.length);
                for (int primitive = 0; primitive < count; primitive++) {
                    MaterialInstance original = state.originals[primitive];
                    if (original != null) {
                        try { renderables.setMaterialInstanceAt(instance, primitive, original); }
                        catch (Throwable ignored) {}
                    }
                }
            }
        } finally {
            for (MaterialInstance replacement : state.replacements) {
                if (replacement != null) {
                    try { state.engine.destroyMaterialInstance(replacement); } catch (Throwable ignored) {}
                }
            }
        }
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

    private static final class BedMaterialState {
        final Engine engine;
        final int entity;
        final MaterialInstance[] originals;
        final MaterialInstance[] replacements;

        BedMaterialState(Engine engine, int entity,
                         MaterialInstance[] originals, MaterialInstance[] replacements) {
            this.engine = engine;
            this.entity = entity;
            this.originals = originals;
            this.replacements = replacements;
        }
    }
}
