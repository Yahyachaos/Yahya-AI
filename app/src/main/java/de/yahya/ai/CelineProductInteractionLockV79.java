package de.yahya.ai;

import android.app.Activity;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.util.WeakHashMap;

/**
 * v79 production interaction owner.
 *
 * HOME/CALL must treat Celine as a person anchored in the scene, not a draggable 3D object.
 * The old Celine3DView SurfaceView accepted one-finger scroll and translated camera target/pan,
 * which visually let the user slide Celine around like a toy. v79 consumes that gesture in the
 * product while preserving pinch as a true camera-distance (dolly) control.
 *
 * The branch-live Celine Avatar Lab is a separate Activity and is intentionally not affected.
 */
final class CelineProductInteractionLockV79 {
    private static final WeakHashMap<Activity, Controller> CONTROLLERS = new WeakHashMap<>();

    private CelineProductInteractionLockV79() {}

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
        if (controller != null) controller.paused = true;
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
        SurfaceView boundSurface;
        ScaleGestureDetector scaleDetector;
        Field zoomField;
        float pinchStartZoom = 1.0f;

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

        void destroy() {
            running = false;
            paused = true;
            choreographer.removeFrameCallback(this);
            if (boundSurface != null) boundSurface.setOnTouchListener(null);
            boundSurface = null;
            boundView = null;
            scaleDetector = null;
            zoomField = null;
        }

        @Override public void doFrame(long frameTimeNanos) {
            if (!running) return;
            choreographer.postFrameCallback(this);
            if (paused) return;
            Celine3DView view = find3D(decor);
            if (view == null || !view.isAttachedToWindow()) return;
            if (view == boundView && boundSurface != null) return;
            bind(view);
        }

        private void bind(Celine3DView view) {
            try {
                if (boundSurface != null) boundSurface.setOnTouchListener(null);
                boundView = view;
                boundSurface = (SurfaceView) field(view, "surfaceView");
                zoomField = Celine3DView.class.getDeclaredField("cameraZoom");
                zoomField.setAccessible(true);
                scaleDetector = new ScaleGestureDetector(activity,
                        new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                            @Override public boolean onScaleBegin(ScaleGestureDetector detector) {
                                try { pinchStartZoom = zoomField.getFloat(boundView); }
                                catch (Throwable ignored) { pinchStartZoom = 1.0f; }
                                return true;
                            }

                            @Override public boolean onScale(ScaleGestureDetector detector) {
                                if (boundView == null || zoomField == null) return false;
                                try {
                                    float current = zoomField.getFloat(boundView);
                                    float next = clamp(current * detector.getScaleFactor(),
                                            CelineCameraZoomV70.ZOOM_MIN,
                                            CelineCameraZoomV70.ZOOM_MAX);
                                    zoomField.setFloat(boundView, next);
                                    return true;
                                } catch (Throwable error) {
                                    Celine3DDiagnostics.error(activity, "V79-319",
                                            "Produkt-Kamera Dolly FEHLER", error);
                                    return false;
                                }
                            }
                        });

                boundSurface.setOnTouchListener((surface, event) -> {
                    ScaleGestureDetector detector = scaleDetector;
                    if (detector != null) detector.onTouchEvent(event);

                    // Consume all direct manipulation in production. Two-finger pinch is handled
                    // above; one-finger motion intentionally has no pan/translation side effect.
                    if (event.getPointerCount() >= 2 || (detector != null && detector.isInProgress())) {
                        return true;
                    }
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                        case MotionEvent.ACTION_MOVE:
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            return true;
                        default:
                            return true;
                    }
                });

                // Remove any stale pan left by the pre-v79 gesture owner before this binder won.
                setFloat(view, "cameraPanX", 0.0f);
                setFloat(view, "cameraPanY", 0.0f);
                Celine3DDiagnostics.record(activity, "V79-310",
                        "Produkt-Avatar räumlich verankert",
                        "oneFingerDrag=blocked · pinch=trueCameraDolly · HOME/CALL only");
            } catch (Throwable error) {
                boundView = null;
                boundSurface = null;
                scaleDetector = null;
                zoomField = null;
                Celine3DDiagnostics.error(activity, "V79-318",
                        "Produkt-Interaktionslock Initialisierung FEHLER", error);
            }
        }
    }

    private static void setFloat(Object target, String name, float value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setFloat(target, value);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Celine3DView find3D(View root) {
        if (root instanceof Celine3DView) return (Celine3DView) root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                Celine3DView found = find3D(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return 1.0f;
        return Math.max(min, Math.min(max, value));
    }
}
