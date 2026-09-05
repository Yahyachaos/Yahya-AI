package de.yahya.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Choreographer;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.android.filament.Camera;
import com.google.android.filament.Engine;
import com.google.android.filament.Renderer;
import com.google.android.filament.Scene;
import com.google.android.filament.SwapChain;

import java.lang.reflect.Field;
import java.util.WeakHashMap;

/**
 * v44 presentation and HOME camera-follow layer. Since v80, root and rig transforms belong only to
 * CelineProductionPresenceV80; this class consumes its HOME motion snapshot for camera follow.
 */
final class CelineVideoChatV44 {
    private static final long TRANSPARENT_SWAP_CHAIN = 0x1L;
    private static final WeakHashMap<Celine3DView, MotionState> STATES = new WeakHashMap<>();
    private static final WeakHashMap<Celine3DView, Boolean> CALL_LOCKS = new WeakHashMap<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private CelineVideoChatV44() {}

    static void ensure(Activity activity, View decor) {
        if (activity == null || decor == null) return;
        Celine3DView threeD = find3D(decor);
        if (threeD == null) return;

        CelineSurfaceTransitionGuardV80.ensure(activity, threeD);
        installRoom(activity, threeD);
        hideDiagnosticBadge(threeD.getParent() instanceof ViewGroup ? (ViewGroup) threeD.getParent() : null);
        installDiagnosticsLongPress(activity, threeD);

        if (isCallLocked(threeD)) {
            MotionState existing;
            synchronized (STATES) { existing = STATES.get(threeD); }
            if (existing != null) {
                existing.stopForCall();
                existing.ensurePresentation();
            }
            return;
        }

        MotionState state;
        synchronized (STATES) {
            state = STATES.get(threeD);
            if (state == null) {
                try {
                    state = new MotionState(activity, threeD);
                    STATES.put(threeD, state);
                    state.start();
                    Celine3DDiagnostics.record(activity, "V44-120", "Videochat-Kamerafolge gestartet",
                            "cameraFollow=on · rigOwner=CelineProductionPresenceV80");
                } catch (Throwable e) {
                    Celine3DDiagnostics.error(activity, "V44-199", "Videochat-Bewegung FEHLER", e);
                    return;
                }
            }
        }
        state.ensurePresentation();
    }

    /** Called by the seated-call owner before it starts changing root/bones/camera. */
    static boolean pauseForCall(Celine3DView view) {
        if (view == null) return false;
        synchronized (CALL_LOCKS) { CALL_LOCKS.put(view, Boolean.TRUE); }
        MotionState state;
        synchronized (STATES) { state = STATES.get(view); }
        if (state != null) {
            state.stopForCall();
            return true;
        }
        return false;
    }

    /** Called only after the seated-call owner has restored its pose. */
    static void resumeAfterCall(Activity activity, View decor) {
        Celine3DView view = find3D(decor);
        if (view != null) {
            synchronized (CALL_LOCKS) { CALL_LOCKS.remove(view); }
        }
        MAIN.postDelayed(() -> ensure(activity, decor), 90L);
    }

    private static boolean isCallLocked(Celine3DView view) {
        synchronized (CALL_LOCKS) { return Boolean.TRUE.equals(CALL_LOCKS.get(view)); }
    }

    private static void installRoom(Activity activity, Celine3DView threeD) {
        if (!(threeD.getParent() instanceof FrameLayout)) return;
        FrameLayout stage = (FrameLayout) threeD.getParent();
        CelineRoomBackdropView room = null;
        for (int i = 0; i < stage.getChildCount(); i++) {
            if (stage.getChildAt(i) instanceof CelineRoomBackdropView) {
                room = (CelineRoomBackdropView) stage.getChildAt(i);
                break;
            }
        }
        if (room == null) {
            room = new CelineRoomBackdropView(activity);
            stage.addView(room, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            Celine3DDiagnostics.record(activity, "V44-100", "Celines Raum eingeblendet",
                    "Backdrop hinter transparenter Filament-Surface");
        }

        ViewGroup.LayoutParams lp = stage.getLayoutParams();
        // HOME owns this legacy fixed stage height. During CALL the v45 reference-aspect slot is
        // authoritative; resizing its child here made the Filament surface 1016x998 inside a
        // 1016x813 slot, changing camera aspect/projection and clipping the reference composition.
        if (lp != null && !isCallLocked(threeD)) {
            float d = Math.max(1f, activity.getResources().getDisplayMetrics().density);
            int screenH = activity.getResources().getDisplayMetrics().heightPixels;
            int target = Math.min(Math.round(455f * d), Math.round(screenH * 0.53f));
            int minimum = Math.round(380f * d);
            target = Math.max(minimum, target);
            if (lp.height != target) {
                lp.height = target;
                stage.setLayoutParams(lp);
            }
        }
    }

    private static void hideDiagnosticBadge(ViewGroup stage) {
        if (stage == null) return;
        for (int i = 0; i < stage.getChildCount(); i++) {
            View child = stage.getChildAt(i);
            if (child instanceof TextView) {
                CharSequence text = ((TextView) child).getText();
                if (text != null && text.toString().startsWith("3D-DIAG")) child.setVisibility(View.GONE);
            }
        }
    }

    private static void installDiagnosticsLongPress(Activity activity, Celine3DView threeD) {
        if (!(threeD.getParent() instanceof View)) return;
        View stage = (View) threeD.getParent();
        Object tag = stage.getTag();
        if (tag instanceof String && ((String) tag).contains("v44diag")) return;
        stage.setTag("v44diag");
        stage.setLongClickable(true);
        stage.setOnLongClickListener(v -> {
            TextView report = new TextView(activity);
            report.setText(Celine3DDiagnostics.report(activity));
            report.setTextIsSelectable(true);
            int p = Math.round(14f * activity.getResources().getDisplayMetrics().density);
            report.setPadding(p, p, p, p);
            new AlertDialog.Builder(activity)
                    .setTitle("Celine 3D Diagnose")
                    .setView(report)
                    .setPositiveButton("Schließen", null)
                    .show();
            return true;
        });
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

    private static final class MotionState implements Choreographer.FrameCallback {
        final Activity activity;
        final Celine3DView view;
        final Engine engine;
        final Renderer renderer;
        final Scene scene;
        final com.google.android.filament.View filamentView;
        final Camera camera;
        final SurfaceView surface;
        final Field swapChainField;
        final Choreographer choreographer = Choreographer.getInstance();
        boolean running;
        boolean transparentCallbackInstalled;

        MotionState(Activity activity, Celine3DView view) throws Exception {
            this.activity = activity;
            this.view = view;
            engine = (Engine) getField(view, "engine");
            renderer = (Renderer) getField(view, "renderer");
            scene = (Scene) getField(view, "scene");
            filamentView = (com.google.android.filament.View) getField(view, "filamentView");
            camera = (Camera) getField(view, "camera");
            surface = (SurfaceView) getField(view, "surfaceView");
            swapChainField = Celine3DView.class.getDeclaredField("swapChain");
            swapChainField.setAccessible(true);
        }

        void start() {
            if (running) return;
            running = true;
            choreographer.postFrameCallback(this);
        }

        void stopForCall() {
            running = false;
            choreographer.removeFrameCallback(this);
            synchronized (STATES) {
                if (STATES.get(view) == this) STATES.remove(view);
            }
        }

        void ensurePresentation() {
            try {
                surface.setZOrderOnTop(true);
                surface.getHolder().setFormat(PixelFormat.TRANSLUCENT);
                filamentView.setBlendMode(com.google.android.filament.View.BlendMode.TRANSLUCENT);
                scene.setSkybox(null);

                Renderer.ClearOptions clear = renderer.getClearOptions();
                clear.clear = true;
                clear.discard = true;
                clear.clearColor = new double[]{0.0, 0.0, 0.0, 0.0};
                renderer.setClearOptions(clear);

                if (!transparentCallbackInstalled) {
                    transparentCallbackInstalled = true;
                    surface.getHolder().addCallback(new SurfaceHolder.Callback() {
                        @Override public void surfaceCreated(SurfaceHolder holder) {
                            MAIN.post(() -> replaceTransparentSwapChain(holder));
                        }
                        @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                            MAIN.post(() -> replaceTransparentSwapChain(holder));
                        }
                        @Override public void surfaceDestroyed(SurfaceHolder holder) {}
                    });
                }
                replaceTransparentSwapChain(surface.getHolder());

                int w = Math.max(1, view.getWidth());
                int h = Math.max(1, view.getHeight());
                camera.setLensProjection(38.0, (double) w / (double) h, 0.05, 1000.0);
                Celine3DDiagnostics.record(activity, "V44-110", "Videochat-Kamera/Raum aktiv",
                        "transparentSurface=on · lens=38mm · stage=" + w + "x" + h);
            } catch (Throwable e) {
                Celine3DDiagnostics.error(activity, "V44-198", "Videochat-Präsentation FEHLER", e);
            }
        }

        private void replaceTransparentSwapChain(SurfaceHolder holder) {
            try {
                Surface s = holder == null ? null : holder.getSurface();
                if (s == null || !s.isValid()) return;
                SwapChain old = (SwapChain) swapChainField.get(view);
                if (old != null) {
                    engine.destroySwapChain(old);
                    engine.flushAndWait();
                }
                SwapChain replacement = engine.createSwapChain(s, TRANSPARENT_SWAP_CHAIN);
                swapChainField.set(view, replacement);
            } catch (Throwable e) {
                Celine3DDiagnostics.error(activity, "V44-197", "Transparenter SwapChain FEHLER", e);
            }
        }

        @Override public void doFrame(long frameTimeNanos) {
            if (!running || !view.isAttachedToWindow() || isCallLocked(view)) {
                running = false;
                synchronized (STATES) {
                    if (STATES.get(view) == this) STATES.remove(view);
                }
                return;
            }
            choreographer.postFrameCallback(this);

            CelineProductionPresenceV80.HomeFrame motion =
                    CelineProductionPresenceV80.homeFrame(view);
            float x = motion.x;
            float z = motion.z;
            float bob = motion.bob;

            double targetX = x * 0.48;
            double targetY = 0.38 + bob * 0.35;
            double targetZ = -4.0 + z * 0.30;
            double cameraX = x * 0.16;
            double cameraY = 0.56;
            double cameraZ = 0.62 + z * 0.08;
            camera.lookAt(cameraX, cameraY, cameraZ,
                    targetX, targetY, targetZ,
                    0.0, 1.0, 0.0);
        }

    }

    private static Object getField(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

}