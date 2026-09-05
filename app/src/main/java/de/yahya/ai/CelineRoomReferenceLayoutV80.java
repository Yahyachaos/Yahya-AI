package de.yahya.ai;

import android.opengl.Matrix;

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

    // Real in-app CALL Proof #1131 shows the runtime carrier bed at approximately y=0.290..0.688
    // on the exact 1016x813 stage, while Refernzbild.png requires y=0.323..0.652. Its horizontal
    // envelope and vertical center are already essentially on target, so correct only the derived
    // carrier's vertical scale by 0.329/0.398 ~= 0.827. Preserve bed X/depth/yaw, horizontal scale,
    // source bytes, Celine and camera; do not stack a depth or grounding correction before the next
    // real HOME/CALL proof.
    private static final Spec BED =
            new Spec("room_bed", 1.030469f, 0.620523f, -0.387500f,
                    1.123125f, 1.221400f, 1.123125f, -84.437500f);

    // Real in-app CALL Proof #1126 is authoritative over the Blender-only carrier assumption for
    // the Android runtime. On its exact 1016x813 CALL stage the visible dresser envelope measured
    // x=0..143 px and y=343..535 px, while Refernzbild.png requires x=0..187 px and y=341..584 px.
    // The runtime carrier therefore presents this immutable source instance at only ~77% of the
    // required horizontal and ~80% of the required vertical silhouette. The derived scale correction
    // is already applied. Real Candidate #1142 then shows the resulting dominant visible dresser face
    // as the irregular orange/brown reverse face while the reference requires the clean fluted front.
    // The projection solver does not search a 180-degree yaw offset, and a front/back flip preserves
    // the solved anchor/scale while changing only which immutable source face is presented. Flip only
    // this derived runtime yaw by 180 degrees; source bytes, position, scale, camera and Celine stay
    // untouched until the next real HOME/CALL proof decides the orientation.
    private static final Spec DRESSER =
            new Spec("room_dresser", -2.135313f, 0.560357f, -0.077000f,
                    1.069137f, 1.079882f, 1.069137f, -92.285156f);

    // Real in-app CALL Proof #1135 measured the first large-plant correction on the exact 1016x813
    // stage at x=0.110..0.262 (width 0.1516, center 0.1860), while Refernzbild.png requires
    // x=0.132..0.247 (width 0.1150, center 0.1895). The previous x/z scale jump from 0.674688 to
    // 1.231573 expanded the isolated green silhouette from width 0.0630 to 0.1516, so the measured
    // scale/width response is non-linear. Interpolating that observed response gives a bounded next
    // x/z footprint scale of about 1.019, while the center is already within ~0.0035 stage-width of
    // target. Keep x=-1.930 and correct width only; preserve plant Y scale/height, depth anchor, yaw,
    // source bytes, camera, Celine and every other furniture instance until the next real CALL proof.
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

    // Real in-app CALL Proof #1130 measured the legacy runtime carrier chair at approximately
    // x=0.159..0.296, y=0.310..0.523 versus Refernzbild.png x=0.217..0.333,
    // y=0.368..0.508. Proof #1131 confirms the bounded correction: the chair is now approximately
    // x=0.219..0.326 and y=0.386..0.518, placing its dominant body in the reference zone. Preserve
    // this derived runtime branch while larger room/furniture deltas remain; only a later residual
    // micro-alignment may revisit it.
    private static final Spec CHAIR =
            new Spec("room_lounge_chair", -1.452000f, 0.371500f, -2.050000f,
                    0.385800f, 0.411000f, 0.385800f, 170.375000f);

    // Real Candidate #1144 is authoritative for the current Android projection. On its exact
    // 1016x813 CALL stage the reference rug target is x=0.205..0.870. Across the lower visible
    // textured bands the reference centers around x=0.543..0.544, while the live rug centers around
    // x=0.581..0.603, leaving a dominant +0.04..+0.06 stage-width right shift. Measured runtime
    // chair X response is about 0.18..0.20 stage-width per metre, so apply one bounded -0.25 m
    // derived X translation only. Preserve rug scale/depth/yaw, source bytes, camera, Celine and all
    // other furniture until the next real HOME/CALL proof determines the residual.
    private static final Spec RUG =
            new Spec("room_rug", -0.196570f, 0.012676f, -0.087483f,
                    1.708708f, 1.641016f, 1.389882f, 5.820313f);
    private static final Spec TABLE =
            new Spec("room_foreground_table", -0.251563f, 0.291672f, 2.280000f,
                    1.031000f, 0.667240f, 0.519922f, -2.000000f);
    private static final Spec WINDOW =
            new Spec("room_window_drapes", -0.575000f, 1.400000f, -2.092500f,
                    1.490625f, 1.490625f, 1.490625f, -8.437500f);
    private static final Spec SHELF =
            new Spec("room_wall_shelf_books", 1.400000f, 1.894062f, -1.916250f,
                    0.351875f, 0.351875f, 0.351875f, 5.820313f);

    // Real Candidate #1141 leaves the entire authoritative mirror envelope empty in both HOME and
    // CALL even though exact Proof #111 places this immutable source at x=0..0.07796,
    // y=0.09520..0.33696. Position/scale already encode that solved envelope. Flip only the
    // orientation normal by 180 degrees so the one-sided runtime carrier faces the camera while
    // preserving the same geometric plane, center, scale and screen-space footprint.
    private static final Spec MIRROR =
            new Spec("room_round_mirror", -1.960156f, 1.468750f, 0.565000f,
                    0.290000f, 0.290000f, 0.290000f, 114.687500f);

    private static final Spec[] ROOM_FURNITURE = {
            WINDOW, SHELF, MIRROR, BED, DRESSER, LARGE_PLANT, CHAIR, LAMP,
            RUG, NIGHTSTAND_FRONT, NIGHTSTAND_BACK, SMALL_PLANT, TABLE
    };

    private static final WeakHashMap<Celine3DView, FilamentAsset> APPLIED = new WeakHashMap<>();

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

            // Recompose only the visible shell from the immutable legacy carrier. The original shell
            // is 6.4 x 5.8 x 2.8 m with its origin at the room centre/floor. Move wall planes to the
            // exact 4.4 x 4.2 bounds and scale their spans; interaction anchors are not touched here.
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

            synchronized (APPLIED) {
                APPLIED.put(view, asset);
            }
            Celine3DDiagnostics.record(view.getContext(), "ROOM-150",
                    "4.40x4.20 Referenz-Solverlayout aktiv",
                    "authority=Refernzbild.png + exact-room Proof#111"
                            + " shell=4.40x4.20x2.65"
                            + " furniture=13 referenceSolvedAbsoluteTRS"
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
