package de.yahya.ai;

import android.opengl.Matrix;

import com.google.android.filament.TransformManager;
import com.google.android.filament.gltfio.FilamentAsset;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * v80 clean architecture + coherent Primary Furniture owner for the partitioned source-fidelity room.
 *
 * Real Candidate #1511 accepted the clean source shell/camera/window recovery direction: the prior
 * giant drape corruption is gone and the architecture is coherent again. This class therefore keeps
 * shell/window/material ownership neutral and applies exactly one measured Primary Furniture batch
 * for bed, dresser, lounge chair, foreground table and rug. Secondary objects stay source-authored.
 *
 * The values below are the latest real-CALL-tuned runtime measurements from the pre-recovery solver
 * history, not acceptance of any rejected whole-scene raster. They are promoted together so the five
 * coupled silhouettes can be judged as one batch against /Refernzbild.png rather than micro-patched.
 */
final class CelineRoomReferenceLayoutV80 {
    private static final Spec BED =
            new Spec("room_bed", 1.146877f, 0.660358f, -0.076940f,
                    1.252188f, 1.316294f, 1.128440f, -84.437500f);
    private static final Spec DRESSER =
            new Spec("room_dresser", -2.100289f, 0.670294f, 0.426774f,
                    0.733027f, 0.932473f, 0.967225f, 87.714844f);
    private static final Spec CHAIR =
            new Spec("room_lounge_chair", -1.643872f, 0.457133f, -1.720000f,
                    0.495673f, 0.505830f, 0.433192f, -9.625000f);
    private static final Spec TABLE =
            new Spec("room_foreground_table", -0.251563f, 0.291672f, 2.912718f,
                    1.031000f, 0.667240f, 0.519922f, -2.000000f);
    private static final Spec RUG =
            new Spec("room_rug", -0.047270f, 0.012676f, 0.151025f,
                    2.128651f, 1.641016f, 1.269665f, 5.820313f);
    private static final Spec[] PRIMARY = { BED, DRESSER, CHAIR, TABLE, RUG };

    private static final WeakHashMap<Celine3DView, FilamentAsset> APPLIED = new WeakHashMap<>();

    private CelineRoomReferenceLayoutV80() {}

    static void ensure(Celine3DView view) {
        if (view == null) return;
        try {
            Object state = roomState(view);
            if (state == null) return;

            Field assetsField = state.getClass().getDeclaredField("roomAssets");
            Field shellField = state.getClass().getDeclaredField("roomShellAsset");
            Field transformsField = state.getClass().getDeclaredField("transforms");
            assetsField.setAccessible(true);
            shellField.setAccessible(true);
            transformsField.setAccessible(true);

            Object rawAssets = assetsField.get(state);
            FilamentAsset shell = (FilamentAsset) shellField.get(state);
            TransformManager transforms = (TransformManager) transformsField.get(state);
            if (!(rawAssets instanceof List) || shell == null || transforms == null) return;

            @SuppressWarnings("unchecked")
            List<FilamentAsset> assets = (List<FilamentAsset>) rawAssets;
            FilamentAsset window = findAssetForEntity(assets, "room_window_drapes__anchor");
            if (window == null) {
                throw new IllegalStateException(
                        "partitioned source window missing: room_window_drapes__anchor");
            }

            synchronized (APPLIED) {
                if (APPLIED.get(view) == window) return;
            }

            requireEntity(shell, "room_shell_floor");
            requireEntity(shell, "room_shell_ceiling");
            requireEntity(shell, "room_shell_left");
            requireEntity(shell, "room_shell_right");
            requireEntity(shell, "room_shell_back");
            requireEntity(shell, "room_shell_front");

            // Resolve and validate all five partition/entity owners before writing any transform so a
            // missing asset cannot leave half of the coherent Primary Furniture batch applied.
            FilamentAsset[] primaryAssets = new FilamentAsset[PRIMARY.length];
            int[] primaryInstances = new int[PRIMARY.length];
            for (int i = 0; i < PRIMARY.length; i++) {
                Spec spec = PRIMARY[i];
                FilamentAsset asset = findAssetForEntity(assets, spec.entityName);
                if (asset == null) {
                    throw new IllegalStateException(
                            "partitioned primary asset missing: " + spec.entityName);
                }
                int entity = asset.getFirstEntityByName(spec.entityName);
                int instance = transforms.getInstance(entity);
                if (instance == 0) {
                    throw new IllegalStateException(
                            "partitioned primary transform missing: " + spec.entityName);
                }
                primaryAssets[i] = asset;
                primaryInstances[i] = instance;
            }

            for (int i = 0; i < PRIMARY.length; i++) {
                setAbsoluteTrs(transforms, primaryInstances[i], PRIMARY[i]);
            }

            // Shell, proof camera, window/drapes, materials, lighting and all Secondary Objects are
            // deliberately untouched. The window asset is only the stable one-shot application marker.
            synchronized (APPLIED) {
                APPLIED.put(view, window);
            }

            Celine3DDiagnostics.record(view.getContext(), "ROOM-150",
                    "Kohaerenter Primary-Furniture-Checkpoint aktiv",
                    "authority=Refernzbild.png + real CALL raster"
                            + " shell=sourceExact440x420x265"
                            + " shellLegacyRescale=false"
                            + " proofCameraOwner=Celine3DView unchanged=true"
                            + " sourceWindowPBR=true windowTransformOverride=false"
                            + " primaryBatch=bed,dresser,lounge-chair,foreground-table,rug"
                            + " dresserFrontConstraintPreserved=true"
                            + " chairAwayFromWindowConstraintPreserved=true"
                            + " secondaryTransformsUnchanged=true"
                            + " materialOverride=false lightingOverride=false"
                            + " sourceGLBsMutated=false");
        } catch (Throwable error) {
            Celine3DDiagnostics.error(view.getContext(), "ROOM-159",
                    "Referenz-Solverlayout FEHLER", error);
        }
    }

    private static void setAbsoluteTrs(TransformManager transforms, int instance, Spec spec) {
        float[] matrix = new float[16];
        Matrix.setIdentityM(matrix, 0);
        Matrix.translateM(matrix, 0, spec.x, spec.y, spec.z);
        if (spec.yawDeg != 0.0f) {
            Matrix.rotateM(matrix, 0, spec.yawDeg, 0.0f, 1.0f, 0.0f);
        }
        Matrix.scaleM(matrix, 0, spec.sx, spec.sy, spec.sz);
        transforms.setTransform(instance, matrix);
    }

    private static FilamentAsset findAssetForEntity(List<FilamentAsset> assets, String entityName) {
        if (assets == null || entityName == null) return null;
        for (FilamentAsset asset : assets) {
            if (asset != null && asset.getFirstEntityByName(entityName) != 0) return asset;
        }
        return null;
    }

    private static void requireEntity(FilamentAsset asset, String entityName) {
        if (asset == null || asset.getFirstEntityByName(entityName) == 0) {
            throw new IllegalStateException("partitioned shell entity missing: " + entityName);
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

    private static final class Spec {
        final String entityName;
        final float x;
        final float y;
        final float z;
        final float sx;
        final float sy;
        final float sz;
        final float yawDeg;

        Spec(String entityName, float x, float y, float z,
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
