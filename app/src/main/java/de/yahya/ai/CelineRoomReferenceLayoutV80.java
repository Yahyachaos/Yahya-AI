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

/** Runtime owner for the measured 4.40 m x 4.20 m reference-room reconstruction. */
final class CelineRoomReferenceLayoutV80 {
    private static final float ROOM_WIDTH_SCALE_X = 4.40f / 6.40f;
    private static final float ROOM_DEPTH_SCALE_Z = 4.20f / 5.80f;
    private static final float ROOM_HEIGHT_SCALE_Y = 2.65f / 2.80f;

    private static final Spec BED =
            new Spec("room_bed", 1.146877f, 0.660358f, -0.076940f,
                    1.252188f, 1.316294f, 1.128440f, -84.437500f);
    private static final Spec DRESSER =
            new Spec("room_dresser", -2.135313f, 0.470772f, 0.426774f,
                    0.733027f, 0.593359f, 0.967225f, -92.285156f);
    private static final Spec LARGE_PLANT =
            new Spec("room_plant_large", -2.067954f, 0.977047f, -1.399038f,
                    0.882475f, 1.026340f, 0.882475f, -15.292969f);
    private static final Spec SMALL_PLANT =
            new Spec("room_plant_small", 2.384907f, 0.522728f, 0.355000f,
                    0.185160f, 0.124451f, 0.185160f, 21.972656f);
    private static final Spec LAMP =
            new Spec("room_floor_lamp", -1.732388f, 0.738777f, -1.900000f,
                    0.268900f, 0.775624f, 0.268900f, -20.710938f);
    private static final Spec NIGHTSTAND_FRONT =
            new Spec("room_nightstand_front", 2.046130f, 0.296737f, 0.926459f,
                    0.212205f, 0.311766f, 0.935485f, 130.195313f);
    private static final Spec NIGHTSTAND_BACK =
            new Spec("room_nightstand_back", 2.012802f, 0.594386f, -1.419782f,
                    0.374178f, 0.624493f, 0.524375f, 106.699219f);
    private static final Spec CHAIR =
            new Spec("room_lounge_chair", -1.722376f, 0.457133f, -2.107537f,
                    0.433192f, 0.505830f, 0.433192f, 170.375000f);
    private static final Spec RUG =
            new Spec("room_rug", -0.047270f, 0.012676f, 0.151025f,
                    2.128651f, 1.641016f, 1.269665f, 5.820313f);
    private static final Spec TABLE =
            new Spec("room_foreground_table", -0.251563f, 0.291672f, 2.912718f,
                    1.031000f, 0.667240f, 0.519922f, -2.000000f);

    // Real Candidate #1179 supplies fresh raster evidence on the exact 1016x813 CALL surface.
    // The previous full-mesh projection solve claimed x=0.205..0.588 / y=0.086..0.477, but the
    // actually visible drape raster was x=251..578 px (0.2470..0.5689), with the reliable visible
    // top at y=119 px (0.1464) and floor transition near y=409 px (0.5031). Because visible pixels,
    // not hidden/invisible mesh extrema, are the acceptance authority, solve the raster silhouette
    // itself. Width/height scale ratios are taken directly from target/current visible envelopes.
    // X/Y translation uses the accepted CALL-camera Jacobian and the two prior window solve points;
    // depth, Z scale and yaw remain frozen. This is one bounded derived-TRS correction only.
    private static final Spec WINDOW =
            new Spec("room_window_drapes", -0.702176f, 1.489298f, -2.092500f,
                    1.876080f, 1.776690f, 1.490625f, -8.437500f);

    private static final Spec SHELF =
            new Spec("room_wall_shelf_books", 1.439041f, 1.844809f, -1.916250f,
                    0.421335f, 0.362738f, 0.351875f, 5.820313f);
    private static final Spec MIRROR =
            new Spec("room_round_mirror", -2.099133f, 1.606177f, 0.471762f,
                    0.406764f, 0.406764f, 0.406764f, -85.984561f);

    private static final Spec[] ROOM_FURNITURE = {
            WINDOW, SHELF, MIRROR, BED, DRESSER, LARGE_PLANT, CHAIR, LAMP,
            RUG, NIGHTSTAND_FRONT, NIGHTSTAND_BACK, SMALL_PLANT, TABLE
    };

    private static final WeakHashMap<Celine3DView, FilamentAsset> APPLIED = new WeakHashMap<>();
    private static final WeakHashMap<Celine3DView, MaterialInstance> MIRROR_MATERIAL_OVERRIDES =
            new WeakHashMap<>();

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

            translateParentLocal(asset, transforms, "room_left_wall", 1.0f, 0f, 0f, true);
            translateParentLocal(asset, transforms, "room_right_wall", -1.0f, 0f, 0f, true);
            translateParentLocal(asset, transforms, "room_back_wall", 0f, 0f, 0.8f, true);
            translateParentLocal(asset, transforms, "room_ceiling", 0f, -0.15f, 0f, true);

            scaleLocalXyz(asset, transforms, "room_floor",
                    ROOM_WIDTH_SCALE_X, 1f, ROOM_DEPTH_SCALE_Z, true);
            scaleLocalXyz(asset, transforms, "room_ceiling",
                    ROOM_WIDTH_SCALE_X, 1f, ROOM_DEPTH_SCALE_Z, true);
            scaleLocalXyz(asset, transforms, "room_back_wall",
                    ROOM_WIDTH_SCALE_X, ROOM_HEIGHT_SCALE_Y, 1f, true);
            scaleLocalXyz(asset, transforms, "room_left_wall",
                    1f, ROOM_HEIGHT_SCALE_Y, ROOM_DEPTH_SCALE_Z, true);
            scaleLocalXyz(asset, transforms, "room_right_wall",
                    1f, ROOM_HEIGHT_SCALE_Y, ROOM_DEPTH_SCALE_Z, true);

            for (Spec spec : ROOM_FURNITURE) {
                setAbsoluteTrs(asset, transforms, spec, true);
            }
            disableMirrorFrustumCulling(view, asset);
            applyMirrorReferenceVisibilityMaterial(view, asset);

            synchronized (APPLIED) {
                APPLIED.put(view, asset);
            }
            Celine3DDiagnostics.record(view.getContext(), "ROOM-150",
                    "4.40x4.20 Referenz-Solverlayout aktiv",
                    "authority=Refernzbild.png + real CALL raster"
                            + " shell=4.40x4.20x2.65"
                            + " furniture=13 referenceSolvedAbsoluteTRS"
                            + " windowRasterAware=true"
                            + " mirrorFrustumCulling=false"
                            + " mirrorReferenceMaterial=opaqueWallDuplicate"
                            + " sourceGLBsMutated=false"
                            + " canonicalCelineScale=false"
                            + " anchorsChanged=false");
        } catch (Throwable error) {
            Celine3DDiagnostics.error(view.getContext(), "ROOM-159",
                    "Referenz-Solverlayout FEHLER", error);
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

    private static void disableMirrorFrustumCulling(Celine3DView view, FilamentAsset asset)
            throws Exception {
        Engine engine = engine(view);
        int entity = asset.getFirstEntityByName(MIRROR.entityName);
        if (entity == 0) throw new IllegalStateException("mirror culling: entity missing");
        RenderableManager renderables = engine.getRenderableManager();
        int instance = renderables.getInstance(entity);
        if (instance == 0) throw new IllegalStateException("mirror culling: renderable missing");
        renderables.setCulling(instance, false);
    }

    private static void applyMirrorReferenceVisibilityMaterial(
            Celine3DView view, FilamentAsset asset) throws Exception {
        Engine engine = engine(view);
        RenderableManager renderables = engine.getRenderableManager();

        int mirrorEntity = asset.getFirstEntityByName(MIRROR.entityName);
        if (mirrorEntity == 0) throw new IllegalStateException("mirror material: entity missing");
        int mirrorInstance = renderables.getInstance(mirrorEntity);
        if (mirrorInstance == 0) throw new IllegalStateException("mirror material: renderable missing");
        if (renderables.getPrimitiveCount(mirrorInstance) != 1) {
            throw new IllegalStateException("mirror material: primitive count != 1");
        }

        int wallEntity = asset.getFirstEntityByName("room_back_wall");
        if (wallEntity == 0) throw new IllegalStateException("mirror material: wall donor missing");
        int wallInstance = renderables.getInstance(wallEntity);
        if (wallInstance == 0) throw new IllegalStateException("mirror material: wall donor renderable missing");
        if (renderables.getPrimitiveCount(wallInstance) < 1) {
            throw new IllegalStateException("mirror material: wall donor primitive missing");
        }
        MaterialInstance wallMaterial = renderables.getMaterialInstanceAt(wallInstance, 0);
        if (wallMaterial == null) throw new IllegalStateException("mirror material: wall donor material missing");

        MaterialInstance replacement = MaterialInstance.duplicate(
                wallMaterial, "v80-reference-mirror-opaque");
        boolean bound = false;
        try {
            if (replacement.getMaterial().hasParameter("baseColorFactor")) {
                replacement.setParameter("baseColorFactor", Colors.RgbaType.LINEAR,
                        0.12f, 0.095f, 0.075f, 1.0f);
            }
            if (replacement.getMaterial().hasParameter("metallicFactor")) {
                replacement.setParameter("metallicFactor", 0.0f);
            }
            if (replacement.getMaterial().hasParameter("roughnessFactor")) {
                replacement.setParameter("roughnessFactor", 0.58f);
            }
            if (replacement.getMaterial().hasParameter("reflectance")) {
                replacement.setParameter("reflectance", 0.38f);
            }
            renderables.setMaterialInstanceAt(mirrorInstance, 0, replacement);
            bound = true;
        } finally {
            if (!bound) {
                try { engine.destroyMaterialInstance(replacement); } catch (Throwable ignored) {}
            }
        }

        MaterialInstance previous;
        synchronized (MIRROR_MATERIAL_OVERRIDES) {
            previous = MIRROR_MATERIAL_OVERRIDES.put(view, replacement);
        }
        if (previous != null && previous != replacement) {
            try { engine.destroyMaterialInstance(previous); } catch (Throwable ignored) {}
        }
        Celine3DDiagnostics.record(view.getContext(), "ROOM-151",
                "Referenzspiegel mit opakem Runtime-Material gebunden",
                "donor=room_back_wall baseColor=0.12,0.095,0.075 alpha=1"
                        + " metallic=0 roughness=0.58 reflectance=0.38"
                        + " sourceMirrorMaterialPreserved=true sourceGLBMutated=false realtimeReflection=false");
    }

    private static Engine engine(Celine3DView view) throws Exception {
        Field engineField = Celine3DView.class.getDeclaredField("engine");
        engineField.setAccessible(true);
        Engine engine = (Engine) engineField.get(view);
        if (engine == null) throw new IllegalStateException("room layout: engine missing");
        return engine;
    }

    private static void setAbsoluteTrs(FilamentAsset asset, TransformManager transforms,
                                       Spec spec, boolean required) {
        int entity = asset.getFirstEntityByName(spec.entityName);
        if (entity == 0) {
            if (required) throw new IllegalStateException("room absolute entity missing: " + spec.entityName);
            return;
        }
        int instance = transforms.getInstance(entity);
        if (instance == 0) {
            if (required) throw new IllegalStateException("room absolute transform missing: " + spec.entityName);
            return;
        }

        float[] matrix = new float[16];
        Matrix.setIdentityM(matrix, 0);
        Matrix.translateM(matrix, 0, spec.x, spec.y, spec.z);
        if (spec.yawDeg != 0.0f) Matrix.rotateM(matrix, 0, spec.yawDeg, 0f, 1f, 0f);
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
            if (required) throw new IllegalStateException("room layout entity missing: " + entityName);
            return false;
        }
        int instance = transforms.getInstance(entity);
        if (instance == 0) {
            if (required) throw new IllegalStateException("room layout transform missing: " + entityName);
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
            if (required) throw new IllegalStateException("room layout entity missing: " + entityName);
            return false;
        }
        int instance = transforms.getInstance(entity);
        if (instance == 0) {
            if (required) throw new IllegalStateException("room layout transform missing: " + entityName);
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
