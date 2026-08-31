package de.yahya.ai;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Bounded Block-12 lifecycle guard for the existing SurfaceView-backed production renderer.
 *
 * A SurfaceView does not participate in a normal window PixelCopy composition. Reparenting the
 * existing avatar stage therefore creates a short surface recreation gap that cannot be hidden by
 * a window-only bitmap. This guard keeps a recent direct PixelCopy of the live Celine SurfaceView
 * and shows only those pixels above the bounded HOME/CALL transition while the same production
 * SurfaceView is recreated. It owns no camera, pose, room, model or renderer state.
 */
final class CelineSurfaceTransitionGuardV80 {
    private static final long SNAPSHOT_INTERVAL_MS = 320L;
    private static final long MIN_READY_MS = 620L;
    private static final int MAX_READY_ATTEMPTS = 48;
    private static final WeakHashMap<Celine3DView, Guard> GUARDS = new WeakHashMap<>();

    private CelineSurfaceTransitionGuardV80() {}

    static void ensure(Activity activity, Celine3DView view) {
        if (activity == null || view == null) return;
        synchronized (GUARDS) {
            if (GUARDS.containsKey(view)) return;
            try {
                Guard guard = new Guard(activity, view);
                GUARDS.put(view, guard);
                guard.install();
            } catch (Throwable error) {
                Celine3DDiagnostics.error(activity, "V80-519",
                        "CALL Surface-Transition-Guard FEHLER", error);
            }
        }
    }

    private static final class Guard implements View.OnAttachStateChangeListener {
        final Activity activity;
        final Celine3DView view;
        final SurfaceView surface;
        final Handler main = new Handler(Looper.getMainLooper());

        Bitmap lastFrame;
        Bitmap coverSourceFrame;
        ImageView cover;
        boolean snapshotInFlight;
        boolean installed;
        int lastLeft;
        int lastTop;
        int lastWidth;
        int lastHeight;
        long transitionStartedAt;

        final Runnable snapshotLoop = new Runnable() {
            @Override public void run() {
                if (!installed || !view.isAttachedToWindow()) return;
                captureSurfaceFrame();
                main.postDelayed(this, SNAPSHOT_INTERVAL_MS);
            }
        };

        Guard(Activity activity, Celine3DView view) throws Exception {
            this.activity = activity;
            this.view = view;
            Field field = Celine3DView.class.getDeclaredField("surfaceView");
            field.setAccessible(true);
            surface = (SurfaceView) field.get(view);
            if (surface == null) throw new IllegalStateException("Celine SurfaceView fehlt");
        }

        void install() {
            if (installed) return;
            installed = true;
            view.addOnAttachStateChangeListener(this);
            main.removeCallbacks(snapshotLoop);
            main.post(snapshotLoop);
            Celine3DDiagnostics.record(activity, "V80-510",
                    "CALL Surface-Transition-Guard aktiv",
                    "directSurfacePixelCopy=true cameraPoseRendererOwnership=false");
        }

        void captureSurfaceFrame() {
            if (snapshotInFlight || cover != null || !surface.isAttachedToWindow()) return;
            int width = surface.getWidth();
            int height = surface.getHeight();
            if (width <= 1 || height <= 1) return;

            final Bitmap bitmap;
            try {
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            } catch (Throwable error) {
                return;
            }
            snapshotInFlight = true;
            try {
                PixelCopy.request(surface, bitmap, result -> {
                    snapshotInFlight = false;
                    if (!installed || cover != null || result != PixelCopy.SUCCESS || bitmap.isRecycled()
                            || !hasVisibleCelinePixels(bitmap)) {
                        if (!bitmap.isRecycled()) bitmap.recycle();
                        return;
                    }
                    storeFrame(bitmap);
                }, main);
            } catch (Throwable error) {
                snapshotInFlight = false;
                bitmap.recycle();
            }
        }

        void storeFrame(Bitmap bitmap) {
            int[] contentLocation = new int[2];
            int[] surfaceLocation = new int[2];
            ViewGroup content = activity.findViewById(android.R.id.content);
            if (content == null) {
                bitmap.recycle();
                return;
            }
            content.getLocationInWindow(contentLocation);
            surface.getLocationInWindow(surfaceLocation);
            Bitmap previous = lastFrame;
            lastFrame = bitmap;
            lastLeft = surfaceLocation[0] - contentLocation[0];
            lastTop = surfaceLocation[1] - contentLocation[1];
            lastWidth = bitmap.getWidth();
            lastHeight = bitmap.getHeight();
            if (cover == null && previous != null && previous != bitmap && !previous.isRecycled()) {
                previous.recycle();
            }
        }

        @Override public void onViewDetachedFromWindow(View v) {
            main.removeCallbacks(snapshotLoop);
            if (!installed || lastFrame == null || lastFrame.isRecycled()
                    || lastWidth <= 1 || lastHeight <= 1) {
                Celine3DDiagnostics.record(activity, "V80-518",
                        "CALL Surface-Transition ohne Snapshot",
                        "failClosedCoverUnavailable=true");
                return;
            }
            installCover();
        }

        @Override public void onViewAttachedToWindow(View v) {
            if (!installed) return;
            main.removeCallbacks(snapshotLoop);
            if (cover == null) {
                main.post(snapshotLoop);
                return;
            }
            transitionStartedAt = SystemClock.elapsedRealtime();
            main.postDelayed(() -> waitForReady(-1, -1, 0, 0), 140L);
        }

        void installCover() {
            ViewGroup rawContent = activity.findViewById(android.R.id.content);
            if (!(rawContent instanceof FrameLayout)) return;
            FrameLayout content = (FrameLayout) rawContent;
            removeCover(false);

            ImageView image = new ImageView(activity);
            image.setScaleType(ImageView.ScaleType.FIT_XY);
            image.setImageBitmap(lastFrame);
            coverSourceFrame = lastFrame;
            image.setClickable(false);
            image.setFocusable(false);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(lastWidth, lastHeight);
            lp.leftMargin = lastLeft;
            lp.topMargin = lastTop;
            content.addView(image, lp);
            image.setElevation(100000f);
            image.bringToFront();
            cover = image;
            transitionStartedAt = SystemClock.elapsedRealtime();
            main.post(image::bringToFront);
            main.postDelayed(image::bringToFront, 80L);
            main.postDelayed(image::bringToFront, 220L);
            Celine3DDiagnostics.record(activity, "V80-511",
                    "Letzten echten Celine-Frame über Surface-Rebuild gehalten",
                    "rect=" + lastLeft + "," + lastTop + " " + lastWidth + "x" + lastHeight);
        }

        void waitForReady(int priorWidth, int priorHeight, int stablePasses, int attempts) {
            if (!installed || cover == null) return;
            if (!view.isAttachedToWindow()) {
                if (attempts < MAX_READY_ATTEMPTS) {
                    main.postDelayed(() -> waitForReady(priorWidth, priorHeight, 0, attempts + 1), 140L);
                }
                return;
            }
            int width = surface.getWidth();
            int height = surface.getHeight();
            int nextStable = width > 1 && height > 1 && width == priorWidth && height == priorHeight
                    ? stablePasses + 1 : 0;
            long elapsed = SystemClock.elapsedRealtime() - transitionStartedAt;
            if (attempts >= MAX_READY_ATTEMPTS) {
                Celine3DDiagnostics.record(activity, "V80-517",
                        "CALL Surface-Transition Cover Timeout",
                        "coverRetained=false attempts=" + attempts + " elapsed=" + elapsed);
                removeCover(true);
                main.post(snapshotLoop);
                return;
            }
            if (elapsed < MIN_READY_MS || nextStable < 3 || snapshotInFlight) {
                main.postDelayed(() -> waitForReady(width, height, nextStable, attempts + 1), 140L);
                return;
            }
            captureReadyTargetFrame(width, height, attempts);
        }

        void captureReadyTargetFrame(int width, int height, int attempts) {
            if (cover == null || snapshotInFlight || !surface.isAttachedToWindow()) {
                main.postDelayed(() -> waitForReady(width, height, 0, attempts + 1), 140L);
                return;
            }
            final Bitmap bitmap;
            try {
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            } catch (Throwable error) {
                main.postDelayed(() -> waitForReady(width, height, 0, attempts + 1), 160L);
                return;
            }
            snapshotInFlight = true;
            try {
                PixelCopy.request(surface, bitmap, result -> {
                    snapshotInFlight = false;
                    if (!installed || cover == null) {
                        bitmap.recycle();
                        return;
                    }
                    if (result != PixelCopy.SUCCESS || !hasVisibleCelinePixels(bitmap)) {
                        bitmap.recycle();
                        main.postDelayed(() -> waitForReady(width, height, 0, attempts + 1), 140L);
                        return;
                    }
                    storeFrame(bitmap);
                    ImageView current = cover;
                    if (current == null) return;
                    current.setImageBitmap(lastFrame);
                    moveCoverToCurrentSurface();
                    clearV45TransitionCover();
                    current.animate().alpha(0f).setDuration(130L).withEndAction(() -> {
                        removeCover(true);
                        main.post(snapshotLoop);
                        Celine3DDiagnostics.record(activity, "V80-512",
                                "Surface-Rebuild auf direkten echten Zielframe freigegeben",
                                "directSurfaceFrameVisible=true stableStage=true v45CoverCleared=true");
                    }).start();
                }, main);
            } catch (Throwable error) {
                snapshotInFlight = false;
                bitmap.recycle();
                main.postDelayed(() -> waitForReady(width, height, 0, attempts + 1), 160L);
            }
        }

        boolean hasVisibleCelinePixels(Bitmap bitmap) {
            if (bitmap == null || bitmap.isRecycled()) return false;
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            if (width <= 1 || height <= 1) return false;
            int stride = Math.max(1, Math.min(width, height) / 180);
            int sampled = 0;
            int visible = 0;
            for (int y = 0; y < height; y += stride) {
                for (int x = 0; x < width; x += stride) {
                    int pixel = bitmap.getPixel(x, y);
                    sampled++;
                    int alpha = (pixel >>> 24) & 0xff;
                    int red = (pixel >>> 16) & 0xff;
                    int green = (pixel >>> 8) & 0xff;
                    int blue = pixel & 0xff;
                    if (alpha > 20 && red + green + blue > 75) visible++;
                }
            }
            return visible >= Math.max(16, sampled / 500);
        }

        void moveCoverToCurrentSurface() {
            if (cover == null) return;
            ViewGroup content = activity.findViewById(android.R.id.content);
            if (content == null) return;
            int[] contentLocation = new int[2];
            int[] surfaceLocation = new int[2];
            content.getLocationInWindow(contentLocation);
            surface.getLocationInWindow(surfaceLocation);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    Math.max(1, surface.getWidth()), Math.max(1, surface.getHeight()));
            lp.leftMargin = surfaceLocation[0] - contentLocation[0];
            lp.topMargin = surfaceLocation[1] - contentLocation[1];
            cover.setLayoutParams(lp);
            cover.bringToFront();
        }

        Object v45Session() {
            try {
                Field sessionsField = CelineVideoCallV45.class.getDeclaredField("SESSIONS");
                sessionsField.setAccessible(true);
                Object raw = sessionsField.get(null);
                if (!(raw instanceof Map)) return null;
                return ((Map<?, ?>) raw).get(activity);
            } catch (Throwable ignored) {
                return null;
            }
        }

        void clearV45TransitionCover() {
            Object session = v45Session();
            if (session == null) return;
            try {
                Method method = session.getClass().getDeclaredMethod("clearTransitionCover");
                method.setAccessible(true);
                method.invoke(session);
            } catch (Throwable ignored) {}
        }

        void removeCover(boolean keepFrame) {
            ImageView current = cover;
            Bitmap staleCoverFrame = coverSourceFrame;
            cover = null;
            coverSourceFrame = null;
            if (current != null) {
                current.animate().cancel();
                current.setImageDrawable(null);
                if (current.getParent() instanceof ViewGroup) {
                    ((ViewGroup) current.getParent()).removeView(current);
                }
            }
            if (staleCoverFrame != null && staleCoverFrame != lastFrame && !staleCoverFrame.isRecycled()) {
                staleCoverFrame.recycle();
            }
            if (!keepFrame && lastFrame != null && lastFrame.isRecycled()) lastFrame = null;
        }
    }
}
