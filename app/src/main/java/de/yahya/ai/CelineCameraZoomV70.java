package de.yahya.ai;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.view.Choreographer;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.filament.Camera;
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
 *
 * v80 early-room correction: the user-approved v25 TRUE3D checkpoint is the room/camera authority.
 * Its 41.1 degree view angle corresponds to the original 32 mm Filament lens. Later generated HOME
 * 38 mm and CALL 50 mm overrides compressed the perceived room depth, while CALL_DEFAULT_ZOOM 2.80
 * added a second aggressive dolly. This owner now restores the v25 projection every frame and keeps
 * the default CALL dolly neutral. Furniture, room geometry, Celine scale and manual pinch bounds are
 * untouched so the next proof isolates camera projection/framing only.
 */
final class CelineCameraZoomV70 {
    static final float ZOOM_MIN = 0.55f;
    static final float ZOOM_MAX = 4.60f;
    static final float CALL_DEFAULT_ZOOM = 1.00f;
    static final float CALL_BASE_FOCUS_Y = 0.00f;
    static final float FACE_FOCUS_Y = 0.85f;
    static final float TARGET_DISTANCE = 5.0f;
    static final float PRODUCTION_HALF_DEPTH = 0.314f;
    static final float NEAR_PLANE = 0.05f;
    static final double V25_FOCAL_LENGTH_MM = 32.0;
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
            if (paused) {
                if (activity.isFinishing() || activity.isDestroyed()
                        || !decor.isAttachedToWindow() || !decor.hasWindowFocus()) return;
                paused = false;
                Celine3DDiagnostics.record(activity, "V70-147", "Kamera-Zoom Controller reaktiviert",
                        "sichtbares fokussiertes MainActivity-Fenster nach Lifecycle-Pause");
            }

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
        final Camera camera;
        final Field zoomField;
        final Field panYField;
        boolean homeZoomLocked;
        boolean wasInCall;
        boolean cullingConfigured;
        boolean projectionLogged;
        float lastLoggedZoom = Float.NaN;
        float lastClampedRequest = Float.NaN;

        Driver(Activity activity, View decor, Celine3DView view) throws Exception {
            this.activity = activity;
            this.decor = decor;
            this.view = view;
            camera = (Camera) field(view, "camera");
            zoomField = Celine3DView.class.getDeclaredField("cameraZoom");
            zoomField.setAccessible(true);
            panYField = Celine3DView.class.getDeclaredField("cameraPanY");
            panYField.setAccessible(true);
            disableCelineFrustumCulling();
        }

        void apply() throws Exception {
            applyPrivateCiZoomIfPresent();
            float requestedZoom = zoomField.getFloat(view);
            float zoom = clamp(requestedZoom, ZOOM_MIN, ZOOM_MAX);
            if (Math.abs(requestedZoom - zoom) > 0.001f) {
                zoomField.setFloat(view, zoom);
                if (Float.isNaN(lastClampedRequest) || Math.abs(lastClampedRequest - requestedZoom) > 0.002f) {
                    lastClampedRequest = requestedZoom;
                    Celine3DDiagnostics.record(activity, "V70-144", "Unsicheren Kamera-Zoom begrenzt",
                            "requested=" + requestedZoom + " applied=" + zoom + " safeBounds=" + ZOOM_MIN + ".." + ZOOM_MAX);
                }
            }
            boolean callNow = CelineCallUpperBodyPresenceV55.isCallStage(view);
            if (callNow && !wasInCall && Math.abs(zoom - 1.0f) < 0.05f) {
                zoom = CALL_DEFAULT_ZOOM;
                zoomField.setFloat(view, zoom);
                Celine3DDiagnostics.record(activity, "V80-210",
                        "CALL Standardkamera auf v25 Raumframing gesetzt",
                        "zoom=" + zoom + " · v25Projection=32mm · modelScaleUnchanged=true");
            } else if (!callNow && wasInCall) {
                zoom = 1.0f;
                zoomField.setFloat(view, zoom);
                Celine3DDiagnostics.record(activity, "V80-211",
                        "HOME Kamera nach CALL sicher zurückgesetzt",
                        "zoom=1.0 · focusY=0.0");
            }
            wasInCall = callNow;

            enforceV25Projection();

            float focusY = focusY(callNow, zoom);
            panYField.setFloat(view, focusY);

            if (callNow) {
                if (homeZoomLocked) homeZoomLocked = false;
                logZoomIfChanged(zoom, "CALL v25 room camera");
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

        private void enforceV25Projection() {
            int width = Math.max(1, view.getWidth());
            int height = Math.max(1, view.getHeight());
            camera.setLensProjection(V25_FOCAL_LENGTH_MM,
                    (double) width / (double) height, NEAR_PLANE, 1000.0);
            if (!projectionLogged) {
                projectionLogged = true;
                Celine3DDiagnostics.record(activity, "V80-212",
                        "v25 TRUE3D Kameraprojektion aktiv",
                        "lens=32mm viewAngle~=41.1deg near=0.05 far=1000 · HOME/CALL shared");
            }
        }

        void releaseHomeZoomLock() {
            if (!homeZoomLocked) return;
            homeZoomLocked = false;
            try { zoomField.setFloat(view, 1.0f); } catch (Throwable ignored) {}
            try { panYField.setFloat(view, 0.0f); } catch (Throwable ignored) {}
            wasInCall = false;
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
            marker.delete();
            if (count <= 0) return;
            float requested = Float.parseFloat(new String(data, 0, count, StandardCharsets.UTF_8).trim());
            float zoom = clamp(requested, ZOOM_MIN, ZOOM_MAX);
            zoomField.setFloat(view, zoom);
            lastLoggedZoom = Float.NaN;
            Celine3DDiagnostics.record(activity, "V70-141", "Privater Emulator-Zoom gesetzt",
                    "zoom=" + zoom + " requested=" + requested + " bounds=" + ZOOM_MIN + ".." + ZOOM_MAX);
        }

        private float focusY(boolean callNow, float zoom) {
            float progress = clamp((zoom - 1.0f) / (ZOOM_MAX - 1.0f), 0.0f, 1.0f);
            float eased = progress * progress * (3.0f - 2.0f * progress);
            float base = callNow ? CALL_BASE_FOCUS_Y : 0.0f;
            return base + (FACE_FOCUS_Y - base) * eased;
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
