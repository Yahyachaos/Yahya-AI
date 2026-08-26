package de.yahya.ai;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.view.Choreographer;
import android.view.View;
import android.view.ViewGroup;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.WeakHashMap;

/**
 * Private v75 exact-head reference-view driver.
 *
 * Normal users cannot activate this path. A debuggable build may place one short command in the
 * app-private files directory; CI consumes it once and rotates only the camera around the same
 * production Celine candidate. No model/root/bone transform is changed. This provides the real
 * front/right/left/back render evidence required by the v75 work order without adding a user-facing
 * orbit mode or weakening the production camera clamps.
 */
final class CelineReferenceViewV75 {
    static final String CI_VIEW_FILE = "celine-ci-reference-view-v75";
    private static final WeakHashMap<Activity, Controller> CONTROLLERS = new WeakHashMap<>();

    private CelineReferenceViewV75() {}

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
        String active = "FRONT";

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
            resetFront();
        }

        void destroy() {
            running = false;
            paused = true;
            choreographer.removeFrameCallback(this);
            resetFront();
            boundView = null;
        }

        @Override public void doFrame(long frameTimeNanos) {
            if (!running) return;
            choreographer.postFrameCallback(this);
            if (paused) return;
            if ((activity.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0) return;
            Celine3DView view = find3D(decor);
            if (view == null || !view.isAttachedToWindow()) return;
            boundView = view;
            try {
                consume(view);
            } catch (Throwable error) {
                Celine3DDiagnostics.error(activity, "V75-179", "Privater Referenzkamera-Treiber FEHLER", error);
                resetFront();
            }
        }

        private void consume(Celine3DView view) throws Exception {
            File marker = new File(activity.getFilesDir(), CI_VIEW_FILE);
            if (!marker.isFile()) return;
            byte[] data = new byte[(int) Math.min(32L, marker.length())];
            int count;
            try (FileInputStream in = new FileInputStream(marker)) {
                count = in.read(data);
            }
            marker.delete();
            if (count <= 0) return;
            String command = new String(data, 0, count, StandardCharsets.UTF_8)
                    .trim().toUpperCase(Locale.ROOT);
            float yaw;
            switch (command) {
                case "RIGHT": yaw = 90.0f; break;
                case "LEFT": yaw = -90.0f; break;
                case "BACK": yaw = 180.0f; break;
                case "FRONT": yaw = 0.0f; break;
                default: throw new IllegalArgumentException("unknown v75 reference view " + command);
            }
            view.v75SetReferenceYaw(yaw);
            active = command;
            if (yaw == 0.0f) {
                CelineVideoChatV44.resumeAfterCall(activity, decor);
            } else {
                CelineVideoChatV44.pauseForCall(view);
            }
            Celine3DDiagnostics.record(activity, "V75-171", "Privater v75 Referenzblick gesetzt",
                    "view=" + command + " yaw=" + yaw + " · production model/candidate unchanged");
        }

        private void resetFront() {
            try {
                if (boundView != null) boundView.v75SetReferenceYaw(0.0f);
                if (boundView != null) CelineVideoChatV44.resumeAfterCall(activity, decor);
            } catch (Throwable ignored) {}
            active = "FRONT";
        }
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
