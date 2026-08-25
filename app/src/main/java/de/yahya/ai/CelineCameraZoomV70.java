package de.yahya.ai;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.view.Choreographer;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.filament.Engine;
import com.google.android.filament.RenderableManager;
import com.google.android.filament.gltfio.FilamentAsset;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.WeakHashMap;

/**
 * v70 camera zoom safety owner.
 *
 * Celine3DView has always implemented the user's bounded pinch camera. v44 also owns a HOME
 * camera-follow callback at zoom=1, however, so the two writers can race while a pinch is active.
 * This guard pauses only v44 HOME motion while zoom differs from 1.0, leaving Celine3DView as the
 * sole camera writer. CALL remains owned by the existing v47 call lock and is never unlocked here.
 *
 * The production glTF is both skinned and morphed after a large normalization scale. Filament's
 * renderable AABB is therefore not trusted for close-camera frustum decisions: culling is disabled
 * only on entities belonging to Celine's FilamentAsset. Depth testing and material back-face
 * culling are untouched.
 */
final class CelineCameraZoomV70 {
    static final float ZOOM_MIN = 0.55f;
    static final float ZOOM_MAX = 2.20f;
    static final float TARGET_DISTANCE = 5.0f;
    static final float PRODUCTION_HALF_DEPTH = 0.314f;
    static final float NEAR_PLANE = 0.05f;
    static final String CI_ZOOM_FILE = "celine-ci-camera-zoom-v70";

    private static final WeakHashMap<Activity, Controller> CONTROLLERS = new WeakHashMap<>();

    private CelineCameraZoomV70() {}

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
        Celine3DView boundView;
        Driver driver;

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

        void pause() {
            paused = true;
            if (driver != null) driver.releaseHomeZoomLock();
        }

        void destroy() {
            running = false;
            paused = true;
            choreographer.removeFrameCallback(this);
            if (driver != null) driver.releaseHomeZoomLock();
            driver = null;
            boundView = null;
        }

        @Override public void doFrame(long frameTimeNanos) {
            if (!running) return;
            choreographer.postFrameCallback(this);
            if (paused) return;

            Celine3DView view = find3D(decor);
            if (view == null || !view.isAttachedToWindow()) return;
            if (driver == null || boundView != view) {
                if (driver != null) driver.releaseHomeZoomLock();
                try {
                    boundView = view;
                    driver = new Driver(activity, decor, view);
                } catch (Throwable error) {
                    Celine3DDiagnostics.error(activity, "V70-149", "Kamera-Zoom Guard Initialisierung FEHLER", error);
                    driver = null;
                    return;
                }
            }
            try {
                driver.apply();
            } catch (Throwable error) {
                Celine3DDiagnostics.error(activity, "V70-148", "Kamera-Zoom Guard Frame FEHLER", error);
                driver.releaseHomeZoomLock();
            }
        }
    }

    private static final class Driver {
        final Activity activity;
        final View decor;
        final Celine3DView view;
        final Field zoomField;
        boolean homeZoomLocked;
        boolean cullingConfigured;
        float lastLoggedZoom = Float.NaN;

        Driver(Activity activity, View decor, Celine3DView view) throws Exception {
            this.activity = activity;
            this.decor = decor;
            this.view = view;
            zoomField = Celine3DView.class.getDeclaredField("cameraZoom");
            zoomField.setAccessible(true);
            disableCelineFrustumCulling();
        }

        void apply() throws Exception {
            applyPrivateCiZoomIfPresent();
            float zoom = clamp(zoomField.getFloat(view), ZOOM_MIN, ZOOM_MAX);
            boolean callNow = CelineCallUpperBodyPresenceV55.isCallStage(view);

            // v47 already owns the v44 lock throughout CALL. Never release its shared lock here.
            if (callNow) {
                if (homeZoomLocked) homeZoomLocked = false;
                logZoomIfChanged(zoom, "CALL v47 camera lock");
                return;
            }

            if (Math.abs(zoom - 1.0f) > 0.002f) {
                if (!homeZoomLocked) {
                    homeZoomLocked = true;
                    boolean stopped = CelineVideoChatV44.pauseForCall(view);
                    Celine3DDiagnostics.record(activity, "V70-140", "HOME Zoom Einzelbesitzer aktiv",
                            "Celine3DView camera-only · v44Paused=" + stopped);
                }
            } else if (homeZoomLocked) {
                homeZoomLocked = false;
                CelineVideoChatV44.resumeAfterCall(activity, decor);
                Celine3DDiagnostics.record(activity, "V70-142", "HOME Defaultkamera wiederhergestellt",
                        "zoom=1 · v44 room motion may resume");
            }
            logZoomIfChanged(zoom, homeZoomLocked ? "HOME Celine3DView-only" : "HOME default v44");
        }

        void releaseHomeZoomLock() {
            if (!homeZoomLocked) return;
            homeZoomLocked = false;
            try { zoomField.setFloat(view, 1.0f); } catch (Throwable ignored) {}
            try { CelineVideoChatV44.resumeAfterCall(activity, decor); } catch (Throwable ignored) {}
        }

        private void disableCelineFrustumCulling() throws Exception {
            if (cullingConfigured) return;
            Engine engine = (Engine) field(view, "engine");
            FilamentAsset asset = (FilamentAsset) field(view, "asset");
            RenderableManager manager = engine.getRenderableManager();
            int changed = 0;
            for (int entity : asset.getEntities()) {
                if (!manager.hasComponent(entity)) continue;
                int instance = manager.getInstance(entity);
                if (instance == 0) continue;
                manager.setCulling(instance, false);
                changed++;
            }
            if (changed <= 0) throw new IllegalStateException("Kein Celine-Renderable für Frustum-Guard gefunden");
            cullingConfigured = true;
            float minDistance = TARGET_DISTANCE / ZOOM_MAX;
            float frontClearance = minDistance - PRODUCTION_HALF_DEPTH;
            Celine3DDiagnostics.record(activity, "V70-150", "Celine Frustum-Culling deaktiviert",
                    "renderables=" + changed + " · zoomMax=" + ZOOM_MAX + " · targetDistance=" + minDistance
                            + " · measuredHalfDepth=" + PRODUCTION_HALF_DEPTH + " · frontClearance=" + frontClearance
                            + " · nearPlane=" + NEAR_PLANE);
        }

        private void applyPrivateCiZoomIfPresent() throws Exception {
            if ((activity.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0) return;
            File marker = new File(activity.getFilesDir(), CI_ZOOM_FILE);
            if (!marker.isFile()) return;
            byte[] data = new byte[(int) Math.min(64L, marker.length())];
            int count;
            try (FileInputStream in = new FileInputStream(marker)) {
                count = in.read(data);
            }
            // Consume first so a malformed marker cannot loop on every rendered frame.
            //noinspection ResultOfMethodCallIgnored
            marker.delete();
            if (count <= 0) return;
            float requested = Float.parseFloat(new String(data, 0, count, StandardCharsets.UTF_8).trim());
            float zoom = clamp(requested, ZOOM_MIN, ZOOM_MAX);
            zoomField.setFloat(view, zoom);
            lastLoggedZoom = Float.NaN;
            Celine3DDiagnostics.record(activity, "V70-141", "Privater Emulator-Zoom gesetzt",
                    "zoom=" + zoom + " requested=" + requested + " bounds=" + ZOOM_MIN + ".." + ZOOM_MAX);
        }

        private void logZoomIfChanged(float zoom, String owner) {
            if (!Float.isNaN(lastLoggedZoom) && Math.abs(lastLoggedZoom - zoom) < 0.002f) return;
            lastLoggedZoom = zoom;
            float distance = TARGET_DISTANCE / zoom;
            Celine3DDiagnostics.record(activity, "V70-143", "Geometrischer Kamera-Zoom aktiv",
                    "zoom=" + zoom + " distance=" + distance + " owner=" + owner);
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
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

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
