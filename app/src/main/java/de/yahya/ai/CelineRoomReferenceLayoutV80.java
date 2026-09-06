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
 * Runtime application of the measured 4.40 m x 4.20 m reference-room reconstruction.
 *
 * The immutable combined GLB remains only the runtime geometry carrier. Furniture source bytes are
 * never changed here. The visible shell and the 13 named furniture instances are transformed from
 * the real Blender reference solve (accepted through Proof #111), using the Android/Filament mapping:
 * Blender/user X -> -Filament X, user height -> Filament Y, user depth -> Filament Z.
 *
 * Celine, her rig and her canonical scale are not touched by this owner.
 */
final class CelineRoomReferenceLayoutV80 {
    private static final float ROOM_WIDTH_SCALE_X = 4.40f / 6.40f;
    private static final float ROOM_DEPTH_SCALE_Z = 4.20f / 5.80f;
    private static final float ROOM_HEIGHT_SCALE_Y = 2.65f / 2.80f;

    private static final Spec BED =
            new Spec("room_bed", 1.030469f, 0.620523f, -0.387500f,
                    1.123125f, 1.221400f, 1.123125f, -84.437500f);

    private static final Spec DRESSER =
            new Spec("room_dresser", -2.135313f, 0.560357f, -0.077000f,
                    1.069137f, 1.079882f, 1.069137f, -92.285156f);

    private static final Spec LARGE_PLANT =
            new Spec("room_plant_large", -1.930000f, 0.982714f, -1.800000f,
                    1.019000f, 1.032188f, 1.019000f, -15.292969f);
    private static final Spec SMALL_PLANT =
            new Spec("room_plant_small", 2.129375f, 0.572656f, 0.355000f,
                    0.105625f, 0.105625f, 0.105625f, 21.972656f);
    private static final Spec LAMP =
            new Spec("room_floor_lamp", -1.714063f, 0.755149f, -1.707344f,
                    0.125000f, 0.792813f, 0.125000f, -20.710938f);
    private static final Spec NIGHTSTAND_FRONT =
            new Spec("room_nightstand_front", 1.936563f, 0.312902f, 0.550000f,
                    0.328750f, 0.328750f, 0.328750f, 130.195313f);
    private static final Spec NIGHTSTAND_BACK =
            new Spec("room_nightstand_back", 1.600000f, 0.499097f, -0.908438f,
                    0.524375f, 0.524375f, 0.524375f, 106.699219f);

    private static final Spec CHAIR =
            new Spec("room_lounge_chair", -1.452000f, 0.371500f, -2.050000f,
                    0.385800f, 0.411000f, 0.385800f, 170.375000f);

    private static final Spec RUG =
            new Spec("room_rug", -0.196570f, 0.012676f, -0.087483f,
                    1.708708f, 1.641016f, 1.389882f, 5.820313f);

    // Real Candidate #1163 confirms the architecture camera now exposes the reference ceiling
    // perspective, but also makes the foreground table the largest remaining screen-space error:
    // its top edge projects at y~=0.622 while Refernzbild.png measures y=0.782. Keeping the source
    // table bytes, X/Y, scale and yaw fixed, the exact Filament projection solves only user-depth
    // z=2.912718 so the tabletop starts at y=0.782 and remains naturally clipped across both side
    // edges like the reference foreground band.
    private static final Spec TABLE =
            new Spec("room_foreground_table", -0.251563f, 0.291672f, 2.912718f,
                    1.031000f, 0.667240f, 0.519922f, -2.000000f);
    private static final Spec WINDOW =
            new Spec("room_window_drapes", -0.575000f, 1.400000f, -2.092500f,
                    1.490625f, 1.490625f, 1.490625f, -8.437500f);

    private static final Spec SHELF =
            new Spec("room_wall_shelf_books", 1.245000f, 1.600000f, -1.916250f,
                    0.351875f, 0.351875f, 0.351875f, 5.820313f);

    // Real Candidate #1161 falsifies the winding hypothesis: the negative-X scale still leaves the
    // reference mirror absent. Re-projecting all 26,677 immutable source vertices with Filament's
    // actual setLensProjection contract (24 mm vertical sensor), the exact CALL aspect 1016/813,
    // the accepted 20.846875 mm lens and Proof#63 look-at shows the preceding mirror at
    // x=-0.100..-0.0065, i.e. entirely left of the SurfaceView. The bounded contact solve below
    // restores positive uniform scale, keeps the nearest vertex at x=-2.150 m (1 cm inside the
    // -2.160 m left-wall inner face), and projects the full source mesh to the measured reference
    // x=0.000..0.078 / y=0.095..0.337. Source mesh/material bytes remain untouched.
    private static final Spec MIRROR =
            new Spec("room_round_mirror", -2.054610f, 1.458353f, -0.520918f,
                    0.423300f, 0.423300f, 0.423300f, -79.474886f);

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
                    "authority=Refernzbild.png + exact-room Proof#111"
                            + " shell=4.40x4.20x2.65"
                            + " furniture=13 referenceSolvedAbsoluteTRS"
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
