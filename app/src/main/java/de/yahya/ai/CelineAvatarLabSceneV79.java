package de.yahya.ai;

import android.app.Activity;
import android.graphics.PixelFormat;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;

import com.google.android.filament.Camera;
import com.google.android.filament.Engine;
import com.google.android.filament.Renderer;
import com.google.android.filament.Scene;
import com.google.android.filament.SwapChain;

import java.lang.reflect.Field;

/**
 * Lab-only scene adapter for deterministic CALL seat-contact evidence.
 *
 * It deliberately reuses the production CelineRoomBackdropView and the same transparent Filament
 * presentation semantics used by CelineVideoChatV44. It never changes the avatar/root scale. The
 * `call` camera preset uses Celine3DView's normal camera position (zoom=1, pan=0) plus the exact
 * 50 mm projection selected by CelineVideoCallV45 for the real CALL stage.
 */
final class CelineAvatarLabSceneV79 {
    private static final long TRANSPARENT_SWAP_CHAIN = 0x1L;

    private final Activity activity;
    private final Celine3DView view;
    private final CelineRoomBackdropView room;
    private final Engine engine;
    private final Renderer renderer;
    private final Scene scene;
    private final com.google.android.filament.View filamentView;
    private final Camera camera;
    private final SurfaceView surface;
    private final Field swapChainField;
    private boolean callbackInstalled;

    static CelineAvatarLabSceneV79 install(Activity activity, FrameLayout root,
                                           Celine3DView view) throws Exception {
        CelineRoomBackdropView room = new CelineRoomBackdropView(activity);
        room.setVisibility(View.GONE);
        root.addView(room, 0, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        CelineAvatarLabSceneV79 adapter = new CelineAvatarLabSceneV79(activity, view, room);
        adapter.ensureTransparentPresentation();
        return adapter;
    }

    private CelineAvatarLabSceneV79(Activity activity, Celine3DView view,
                                    CelineRoomBackdropView room) throws Exception {
        this.activity = activity;
        this.view = view;
        this.room = room;
        engine = (Engine) field(view, "engine");
        renderer = (Renderer) field(view, "renderer");
        scene = (Scene) field(view, "scene");
        filamentView = (com.google.android.filament.View) field(view, "filamentView");
        camera = (Camera) field(view, "camera");
        surface = (SurfaceView) field(view, "surfaceView");
        swapChainField = Celine3DView.class.getDeclaredField("swapChain");
        swapChainField.setAccessible(true);
    }

    void apply(String pose, String cameraPreset) {
        boolean call = "call".equalsIgnoreCase(cameraPreset);
        boolean seated = "seated".equalsIgnoreCase(pose);
        room.setSeatedCallMode(call && seated);
        room.setVisibility(call ? View.VISIBLE : View.GONE);

        int w = Math.max(1, view.getWidth());
        int h = Math.max(1, view.getHeight());
        camera.setLensProjection(call ? 50.0 : 32.0,
                (double) w / (double) h, 0.05, 1000.0);
        Celine3DDiagnostics.record(activity, "V79-530", "Avatar Lab Produktionsszene gesetzt",
                "scene=" + (call ? "CALL" : "diagnostic")
                        + " seated=" + seated
                        + " lensMm=" + (call ? 50 : 32)
                        + " rootScaleChanged=false");
    }

    private void ensureTransparentPresentation() throws Exception {
        surface.setZOrderOnTop(true);
        surface.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        filamentView.setBlendMode(com.google.android.filament.View.BlendMode.TRANSLUCENT);
        scene.setSkybox(null);

        Renderer.ClearOptions clear = renderer.getClearOptions();
        clear.clear = true;
        clear.discard = true;
        clear.clearColor = new double[]{0.0, 0.0, 0.0, 0.0};
        renderer.setClearOptions(clear);

        if (!callbackInstalled) {
            callbackInstalled = true;
            surface.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override public void surfaceCreated(SurfaceHolder holder) {
                    surface.post(() -> replaceTransparentSwapChain(holder));
                }

                @Override public void surfaceChanged(SurfaceHolder holder, int format,
                                                     int width, int height) {
                    surface.post(() -> replaceTransparentSwapChain(holder));
                }

                @Override public void surfaceDestroyed(SurfaceHolder holder) {}
            });
        }
        replaceTransparentSwapChain(surface.getHolder());
    }

    private void replaceTransparentSwapChain(SurfaceHolder holder) {
        try {
            Surface currentSurface = holder == null ? null : holder.getSurface();
            if (currentSurface == null || !currentSurface.isValid()) return;
            SwapChain old = (SwapChain) swapChainField.get(view);
            if (old != null) {
                engine.destroySwapChain(old);
                engine.flushAndWait();
            }
            swapChainField.set(view, engine.createSwapChain(currentSurface, TRANSPARENT_SWAP_CHAIN));
            Celine3DDiagnostics.record(activity, "V79-531", "Avatar Lab transparenter SwapChain aktiv",
                    "productionRoomBehindFilament=true");
        } catch (Throwable error) {
            Celine3DDiagnostics.error(activity, "V79-539",
                    "Avatar Lab transparenter SwapChain FEHLER", error);
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
