package de.yahya.ai;

import android.app.Activity;
import android.opengl.Matrix;
import android.view.Choreographer;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.filament.Box;
import com.google.android.filament.TransformManager;
import com.google.android.filament.gltfio.FilamentAsset;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.WeakHashMap;

/**
 * v61 production hotfix for Meshy biped GLBs that carry a 0.01 Armature scale while their
 * skinning inverse-bind matrices compensate by roughly x100. FilamentAsset#getBoundingBox can
 * therefore describe the tiny pre-skinning node bounds while the actual skinned character is
 * about 100x larger. v60 then normalized the tiny bounds and blew the rendered skin up again.
 *
 * This guard leaves synthetic/small CI fixtures and ordinary GLBs untouched. For the known
 * production Meshy rig it converts the tiny asset bounds back to the effective skinned-space
 * bounds before applying the root framing transform. The renderer, camera controls and safe
 * HOME/CALL skinning ownership remain unchanged.
 */
final class CelineMeshyRigScaleV61 {
    private static final float TARGET_HEIGHT = 2.35f;
    private static final float CAMERA_TARGET_Z = -4.0f;
    private static final float MAX_TINY_BOUNDS = 0.25f;
    private static final float MIN_RIG_SCALE = 0.005f;
    private static final float MAX_RIG_SCALE = 0.050f;
    private static final long MIN_PRODUCTION_BYTES = 1_000_000L;

    private static final WeakHashMap<Activity, Controller> CONTROLLERS = new WeakHashMap<>();

    private CelineMeshyRigScaleV61() {}

    static void install(Activity activity, View decor) {
        if (!(activity instanceof MainActivity) || decor == null) return;
        Controller controller;
        synchronized (CONTROLLERS) {
            controller = CONTROLLERS.get(activity);
            if (controller == null) {
                controller = new Controller(activity, decor);
                CONTROLLERS.put(activity, controller);
            }
        }
        controller.resume();
    }

    static void onPaused(Activity activity) {
        Controller controller;
        synchronized (CONTROLLERS) { controller = CONTROLLERS.get(activity); }
        if (controller != null) controller.pause();
    }

    static void onDestroyed(Activity activity) {
        Controller controller;
        synchronized (CONTROLLERS) { controller = CONTROLLERS.remove(activity); }
        if (controller != null) controller.destroy();
    }

    private static final class Controller implements Choreographer.FrameCallback {
        final Activity activity;
        final View decor;
        final Choreographer choreographer = Choreographer.getInstance();
        boolean running;
        boolean paused;
        Celine3DView repairedView;
        boolean loggedWaiting;

        Controller(Activity activity, View decor) {
            this.activity = activity;
            this.decor = decor;
        }

        void resume() {
            paused = false;
            if (running) return;
            running = true;
            choreographer.postFrameCallback(this);
        }

        void pause() { paused = true; }

        void destroy() {
            running = false;
            paused = true;
            choreographer.removeFrameCallback(this);
            repairedView = null;
        }

        @Override public void doFrame(long frameTimeNanos) {
            if (!running) return;
            choreographer.postFrameCallback(this);
            if (paused) return;

            Celine3DView view = find3D(decor);
            if (view == null || !view.isAttachedToWindow()) {
                if (!loggedWaiting) {
                    loggedWaiting = true;
                    Celine3DDiagnostics.record(activity, "V61-100", "Warte auf Celine3DView",
                            "Meshy Rig-Scale Guard aktiv");
                }
                return;
            }
            loggedWaiting = false;
            if (repairedView == view) return;

            try {
                if (repair(activity, view)) repairedView = view;
                else repairedView = view; // Decision is stable for this renderer instance.
            } catch (Throwable e) {
                Celine3DDiagnostics.error(activity, "V61-199", "Meshy Rig-Scale Reparatur FEHLER", e);
                repairedView = view;
            }
        }
    }

    private static boolean repair(Activity activity, Celine3DView view) throws Exception {
        long bytes = productionModelBytes(activity);

        FilamentAsset asset = (FilamentAsset) field(view, "asset");
        TransformManager transforms = (TransformManager) field(view, "transformManager");
        if (asset == null || transforms == null) return false;

        Box box = asset.getBoundingBox();
        float[] center = box.getCenter();
        float[] half = box.getHalfExtent();
        float maxExtent = 2.0f * Math.max(half[0], Math.max(half[1], half[2]));

        int armatureEntity = asset.getFirstEntityByName("Armature");
        int hipsEntity = asset.getFirstEntityByName("Hips");
        int headEntity = asset.getFirstEntityByName("Head");
        if (armatureEntity == 0 || hipsEntity == 0 || headEntity == 0) {
            Celine3DDiagnostics.record(activity, "V61-101", "Kein Meshy-Biped-Sonderfall",
                    "Armature=" + (armatureEntity != 0) + " Hips=" + (hipsEntity != 0) + " Head=" + (headEntity != 0));
            return false;
        }

        int armatureInstance = transforms.getInstance(armatureEntity);
        if (armatureInstance == 0) return false;
        float[] armature = transforms.getTransform(armatureInstance, new float[16]);
        float sx = basisLength(armature, 0);
        float sy = basisLength(armature, 4);
        float sz = basisLength(armature, 8);
        float rigScale = (sx + sy + sz) / 3.0f;

        boolean productionSized = bytes >= MIN_PRODUCTION_BYTES;
        boolean tinyBounds = maxExtent > 0.000001f && maxExtent < MAX_TINY_BOUNDS;
        boolean hundredScaleRig = rigScale >= MIN_RIG_SCALE && rigScale <= MAX_RIG_SCALE;

        if (!productionSized || !tinyBounds || !hundredScaleRig) {
            Celine3DDiagnostics.record(activity, "V61-102", "Rig-Scale Korrektur nicht nötig",
                    "bytes=" + bytes + " maxExtent=" + maxExtent + " armatureScale=" + rigScale +
                            " production=" + productionSized + " tiny=" + tinyBounds + " rig=" + hundredScaleRig);
            return false;
        }

        float correction = 1.0f / rigScale;
        float correctedExtent = maxExtent * correction;
        if (!(correctedExtent > 0.10f) || Float.isNaN(correctedExtent) || Float.isInfinite(correctedExtent)) {
            throw new IllegalStateException("Ungültige korrigierte Modellgröße: " + correctedExtent);
        }

        float correctedCenterX = center[0] * correction;
        float correctedCenterY = center[1] * correction;
        float correctedCenterZ = center[2] * correction;
        float rootScale = TARGET_HEIGHT / correctedExtent;

        float[] moveToOrigin = new float[16];
        float[] scaleMatrix = new float[16];
        float[] centerAtTarget = new float[16];
        float[] temp = new float[16];
        float[] transform = new float[16];
        Matrix.setIdentityM(moveToOrigin, 0);
        Matrix.translateM(moveToOrigin, 0, -correctedCenterX, -correctedCenterY, -correctedCenterZ);
        Matrix.setIdentityM(scaleMatrix, 0);
        Matrix.scaleM(scaleMatrix, 0, rootScale, rootScale, rootScale);
        Matrix.setIdentityM(centerAtTarget, 0);
        Matrix.translateM(centerAtTarget, 0, 0.0f, 0.0f, CAMERA_TARGET_Z);
        Matrix.multiplyMM(temp, 0, scaleMatrix, 0, moveToOrigin, 0);
        Matrix.multiplyMM(transform, 0, centerAtTarget, 0, temp, 0);

        int rootInstance = transforms.getInstance(asset.getRoot());
        if (rootInstance == 0) throw new IllegalStateException("3D-Root-Transform fehlt");
        transforms.setTransform(rootInstance, transform);

        Celine3DDiagnostics.record(activity, "V61-110", "Meshy Rig-Scale korrigiert",
                "rawExtent=" + maxExtent + " armatureScale=" + rigScale + " correction=" + correction +
                        " correctedExtent=" + correctedExtent + " rootScale=" + rootScale +
                        " targetHeight=" + TARGET_HEIGHT + " center=" + correctedCenterX + "," + correctedCenterY + "," + correctedCenterZ);
        return true;
    }

    private static long productionModelBytes(Activity activity) {
        File imported = Celine3DView.importedModelFile(activity);
        if (imported.isFile() && imported.length() > 32L) return imported.length();
        try (InputStream in = activity.getAssets().open("models/celine.glb")) {
            long available = in.available();
            if (available > 0L) return available;
            long total = 0L;
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) >= 0) total += n;
            return total;
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static float basisLength(float[] m, int offset) {
        float x = m[offset];
        float y = m[offset + 1];
        float z = m[offset + 2];
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    private static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static Celine3DView find3D(View view) {
        if (view instanceof Celine3DView) return (Celine3DView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Celine3DView found = find3D(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
}
