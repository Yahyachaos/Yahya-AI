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

/** v70 camera zoom safety owner for HOME/CALL. */
final class CelineCameraZoomV70 {
    static final float ZOOM_MIN = 0.55f;
    static final float ZOOM_MAX = 4.60f;

    // Real Candidate #1162 makes the remaining global perspective error measurable: the exact
    // 4.40x4.20 shell reaches the top edge before the back-wall ceiling line, so the reference
    // ceiling and side-wall wedges disappear. Re-projecting the physical shell through Filament's
    // 24 mm vertical sensor model against the six high-confidence architecture landmarks gives the
    // smallest current-camera correction at normalized zoom 0.785714, panX=0.071327 and
    // panY=-0.192402. HOME uses baseZoom=1.0 and CALL uses baseZoom=0.70, therefore these paired
    // defaults produce the same architecture camera in both real product surfaces. They leave the
    // reference lens/eye direction, room dimensions, furniture TRS, Celine scale/rig and source GLBs
    // unchanged; subsequent visual evidence must decide every furniture delta under this camera.
    static final float HOME_DEFAULT_ZOOM = 0.7857143f;
    static final float CALL_DEFAULT_ZOOM = 0.55f;
    static final float REFERENCE_PAN_X = 0.071327f;
    static final float REFERENCE_PAN_Y = -0.192402f;

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
        final Field panXField;
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
            panXField = Celine3DView.class.getDeclaredField("cameraPanX");
            panXField.setAccessible(true);
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
            if (!callNow && !wasInCall && Math.abs(zoom - 1.0f) < 0.05f) {
                zoom = HOME_DEFAULT_ZOOM;
                zoomField.setFloat(view, zoom);
                panXField.setFloat(view, REFERENCE_PAN_X);
                Celine3DDiagnostics.record(activity, "V80-209",
                        "HOME Kamera auf Referenzarchitektur gesetzt",
                        "zoom=" + zoom + " panX=" + REFERENCE_PAN_X + " panY=" + REFERENCE_PAN_Y
                                + " · normalizedZoom=0.785714 · sourceGeometryUnchanged=true");
            }

            if (callNow && !wasInCall
                    && (Math.abs(zoom - HOME_DEFAULT_ZOOM) < 0.05f || Math.abs(zoom - 1.0f) < 0.05f)) {
                zoom = CALL_DEFAULT_ZOOM;
                zoomField.setFloat(view, zoom);
                panXField.setFloat(view, REFERENCE_PAN_X);
                Celine3DDiagnostics.record(activity, "V80-210",
                        "CALL Kamera auf Referenzarchitektur gesetzt",
                        "zoom=" + zoom + " · referenceBase=0.70 · normalizedZoom=0.785714"
                                + " · panX=" + REFERENCE_PAN_X + " panY=" + REFERENCE_PAN_Y
                                + " · roomDimensionsUnchanged=true");
            } else if (!callNow && wasInCall) {
                zoom = HOME_DEFAULT_ZOOM;
                zoomField.setFloat(view, zoom);
                panXField.setFloat(view, REFERENCE_PAN_X);
                Celine3DDiagnostics.record(activity, "V80-211",
                        "HOME Referenzarchitektur nach CALL wiederhergestellt",
                        "zoom=" + HOME_DEFAULT_ZOOM + " · panX=" + REFERENCE_PAN_X
                                + " · panY=" + REFERENCE_PAN_Y);
            }
            wasInCall = callNow;

            enforceV25Projection();

            float focusY = focusY(callNow, zoom);
            panYField.setFloat(view, focusY);

            if (callNow) {
                if (homeZoomLocked) homeZoomLocked = false;
                logZoomIfChanged(zoom, "CALL reference-room architecture");
                return;
            }

            if (Math.abs(zoom - HOME_DEFAULT_ZOOM) > 0.002f) {
                if (!homeZoomLocked) {
                    homeZoomLocked = true;
                    boolean stopped = CelineVideoChatV44.pauseForCall(view);
                    Celine3DDiagnostics.record(activity, "V70-140", "HOME Zoom Einzelbesitzer aktiv",
                            "Celine3DView camera-only · v44Paused=" + stopped);
                }
            } else if (homeZoomLocked) {
                homeZoomLocked = false;
                CelineVideoChatV44.resumeAfterCall(activity, decor);
                Celine3DDiagnostics.record(activity, "V70-142", "HOME Referenzkamera wiederhergestellt",
                        "zoom=" + HOME_DEFAULT_ZOOM + " · v44 room motion may resume");
            }
            logZoomIfChanged(zoom, homeZoomLocked ? "HOME Celine3DView-only" : "HOME reference architecture");
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
            if (!homeZoomLocked && !wasInCall) return;
            homeZoomLocked = false;
            try { zoomField.setFloat(view, HOME_DEFAULT_ZOOM); } catch (Throwable ignored) {}
            try { panXField.setFloat(view, REFERENCE_PAN_X); } catch (Throwable ignored) {}
            try { panYField.setFloat(view, REFERENCE_PAN_Y); } catch (Throwable ignored) {}
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
            float desiredFocusY = REFERENCE_PAN_Y + (FACE_FOCUS_Y - REFERENCE_PAN_Y) * eased;
            if (callNow && zoom > 1.0f) {
                // The reference room camera targets y=-1.10 and Celine3DView converts cameraPanY
                // with a 0.28 factor. For close CALL zooms, translate the avatar-focus curve into
                // that reference coordinate system so the dolly approaches Celine instead of the
                // bed/floor. Keep the far/default CALL preview untouched for room judging.
                return (1.10f + desiredFocusY) / 0.28f;
            }
            return desiredFocusY;
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
