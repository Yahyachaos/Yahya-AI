package de.yahya.ai;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.opengl.Matrix;
import android.os.Handler;
import android.view.Choreographer;
import android.view.PixelCopy;
import android.view.Surface;
import android.view.SurfaceView;
import android.widget.FrameLayout;

import com.google.android.filament.Box;
import com.google.android.filament.Camera;
import com.google.android.filament.Engine;
import com.google.android.filament.EntityManager;
import com.google.android.filament.IndirectLight;
import com.google.android.filament.LightManager;
import com.google.android.filament.Renderer;
import com.google.android.filament.Scene;
import com.google.android.filament.Skybox;
import com.google.android.filament.SwapChain;
import com.google.android.filament.TransformManager;
import com.google.android.filament.Viewport;
import com.google.android.filament.android.UiHelper;
import com.google.android.filament.gltfio.AssetLoader;
import com.google.android.filament.gltfio.FilamentAsset;
import com.google.android.filament.gltfio.Gltfio;
import com.google.android.filament.gltfio.ResourceLoader;
import com.google.android.filament.gltfio.UbershaderProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * v29 minimal 3D baseline.
 *
 * No ModelViewer, no animation, no morphs, no renderer switching. The imported GLB is loaded
 * directly through gltfio and rendered into one SurfaceView with a fixed camera and fixed light.
 */
public final class Celine3DView extends FrameLayout {
    private static final String MODEL_PATH = "models/celine.glb";
    private static final String IMPORT_DIR = "models";
    private static final String IMPORT_FILE = "celine.glb";

    static { Gltfio.init(); }

    public interface VisibilityCallback { void onResult(boolean visible); }

    private final SurfaceView surfaceView;
    private final Choreographer choreographer;
    private final Engine engine;
    private final Renderer renderer;
    private final Scene scene;
    private final com.google.android.filament.View filamentView;
    private final Camera camera;
    private final int cameraEntity;
    private final int lightEntity;
    private final UiHelper uiHelper;
    private final UbershaderProvider materialProvider;
    private final AssetLoader assetLoader;
    private final ResourceLoader resourceLoader;
    private final FilamentAsset asset;
    private final Skybox skybox;
    private final IndirectLight indirectLight;

    private SwapChain swapChain;
    private boolean running;
    private volatile Throwable renderError;

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override public void doFrame(long frameTimeNanos) {
            if (!running) return;
            choreographer.postFrameCallback(this);
            try {
                if (!uiHelper.isReadyToRender() || swapChain == null) return;
                if (renderer.beginFrame(swapChain, frameTimeNanos)) {
                    renderer.render(filamentView);
                    renderer.endFrame();
                }
            } catch (Throwable e) {
                renderError = e;
                running = false;
                choreographer.removeFrameCallback(this);
            }
        }
    };

    public Celine3DView(Context context) throws Exception { this(context, true); }

    /** Retained for source compatibility; v29 always uses one SurfaceView renderer. */
    public Celine3DView(Context context, boolean ignoredRendererChoice) throws Exception {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);

        choreographer = Choreographer.getInstance();
        surfaceView = new SurfaceView(context);
        addView(surfaceView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        engine = Engine.create();
        renderer = engine.createRenderer();
        scene = engine.createScene();
        filamentView = engine.createView();

        cameraEntity = EntityManager.get().create();
        camera = engine.createCamera(cameraEntity);
        camera.setExposure(16.0f, 1.0f / 125.0f, 100.0f);
        filamentView.setScene(scene);
        filamentView.setCamera(camera);

        // Dark neutral background: if Celine renders, her skin / hair / clothes are unmistakable.
        skybox = new Skybox.Builder()
                .color(0.018f, 0.022f, 0.030f, 1.0f)
                .build(engine);
        scene.setSkybox(skybox);

        // Constant ambient light so PBR materials are never left unlit.
        indirectLight = new IndirectLight.Builder()
                .irradiance(1, new float[]{1.0f, 1.0f, 1.0f})
                .intensity(30000.0f)
                .build(engine);
        scene.setIndirectLight(indirectLight);

        // Strong front/upper directional key light.
        lightEntity = EntityManager.get().create();
        new LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(1.0f, 0.96f, 0.92f)
                .intensity(110000.0f)
                .direction(-0.35f, -0.65f, -1.0f)
                .castShadows(false)
                .build(engine, lightEntity);
        scene.addEntity(lightEntity);

        // Load the monolithic GLB synchronously. The Meshy texture is embedded in the GLB, so no
        // external URI resolver is needed. Synchronous loading is intentional for this baseline.
        materialProvider = new UbershaderProvider(engine);
        assetLoader = new AssetLoader(engine, materialProvider, EntityManager.get());
        resourceLoader = new ResourceLoader(engine, true);
        asset = assetLoader.createAsset(readModel(context));
        if (asset == null) {
            throw new IllegalStateException("gltfio konnte die importierte GLB-Datei nicht laden.");
        }
        resourceLoader.loadResources(asset);
        asset.releaseSourceData();

        normalizeAsset(asset);
        scene.addEntities(asset.getEntities());

        // Fixed camera. The model root is normalized to a 2-unit cube centered at z=-4.
        camera.lookAt(
                0.0, 0.0, 1.0,
                0.0, 0.0, -4.0,
                0.0, 1.0, 0.0
        );

        uiHelper = new UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK);
        uiHelper.setRenderCallback(new UiHelper.RendererCallback() {
            @Override public void onNativeWindowChanged(Surface surface) {
                try {
                    if (swapChain != null) engine.destroySwapChain(swapChain);
                    swapChain = engine.createSwapChain(surface, uiHelper.getSwapChainFlags());
                } catch (Throwable e) {
                    renderError = e;
                }
            }

            @Override public void onDetachedFromSurface() {
                try {
                    if (swapChain != null) {
                        engine.destroySwapChain(swapChain);
                        engine.flushAndWait();
                        swapChain = null;
                    }
                } catch (Throwable e) {
                    renderError = e;
                }
            }

            @Override public void onResized(int width, int height) {
                if (width <= 0 || height <= 0) return;
                filamentView.setViewport(new Viewport(0, 0, width, height));
                camera.setLensProjection(32.0, (double) width / (double) height, 0.05, 1000.0);
                camera.lookAt(0.0, 0.0, 1.0, 0.0, 0.0, -4.0, 0.0, 1.0, 0.0);
            }
        });
        uiHelper.attachTo(surfaceView);
    }

    /** Equivalent to ModelViewer.transformToUnitCube, but with no filament-utils dependency. */
    private void normalizeAsset(FilamentAsset loadedAsset) {
        Box box = loadedAsset.getBoundingBox();
        float[] center = box.getCenter();
        float[] half = box.getHalfExtent();
        float maxExtent = 2.0f * Math.max(half[0], Math.max(half[1], half[2]));
        if (!(maxExtent > 0.000001f) || Float.isNaN(maxExtent) || Float.isInfinite(maxExtent)) {
            throw new IllegalStateException("Ungültige 3D-Modellgröße: " + maxExtent);
        }
        float scale = 2.0f / maxExtent;

        float[] moveToOrigin = new float[16];
        float[] scaleMatrix = new float[16];
        float[] centerAtTarget = new float[16];
        float[] temp = new float[16];
        float[] transform = new float[16];
        Matrix.setIdentityM(moveToOrigin, 0);
        Matrix.translateM(moveToOrigin, 0, -center[0], -center[1], -center[2]);
        Matrix.setIdentityM(scaleMatrix, 0);
        Matrix.scaleM(scaleMatrix, 0, scale, scale, scale);
        Matrix.setIdentityM(centerAtTarget, 0);
        Matrix.translateM(centerAtTarget, 0, 0.0f, 0.0f, -4.0f);
        Matrix.multiplyMM(temp, 0, scaleMatrix, 0, moveToOrigin, 0);
        Matrix.multiplyMM(transform, 0, centerAtTarget, 0, temp, 0);

        TransformManager tm = engine.getTransformManager();
        int instance = tm.getInstance(loadedAsset.getRoot());
        if (instance == 0) throw new IllegalStateException("3D-Root-Transform fehlt.");
        tm.setTransform(instance, transform);
    }

    public static File importedModelFile(Context context) {
        File dir = new File(context.getFilesDir(), IMPORT_DIR);
        return new File(dir, IMPORT_FILE);
    }

    public static boolean hasModel(Context context) {
        File imported = importedModelFile(context);
        if (imported.isFile() && imported.length() > 32) return true;
        try (InputStream in = context.getAssets().open(MODEL_PATH)) {
            return in.available() > 32;
        } catch (Exception ignored) {
            return false;
        }
    }

    public String getRendererName() { return "Direct SurfaceView · Filament 1.72"; }

    public String getRenderFailureReason() {
        Throwable e = renderError;
        if (e == null) return null;
        String m = e.getMessage();
        return m == null || m.trim().isEmpty() ? e.getClass().getSimpleName() : m;
    }

    /** Check actual SurfaceView pixels, not merely whether a FilamentAsset object exists. */
    public void verifyVisibleFrame(Handler handler, VisibilityCallback callback) {
        probeVisibleFrame(handler, callback, 35);
    }

    private void probeVisibleFrame(Handler handler, VisibilityCallback callback, int remaining) {
        if (callback == null) return;
        if (renderError != null || remaining <= 0) {
            callback.onResult(false);
            return;
        }
        if (!isAttachedToWindow() || getWidth() <= 0 || getHeight() <= 0 || !running ||
                surfaceView.getHolder() == null || surfaceView.getHolder().getSurface() == null ||
                !surfaceView.getHolder().getSurface().isValid()) {
            handler.postDelayed(() -> probeVisibleFrame(handler, callback, remaining - 1), 250L);
            return;
        }

        final Bitmap sample = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        try {
            PixelCopy.request(surfaceView, sample, result -> {
                boolean visible = result == PixelCopy.SUCCESS && hasModelPixels(sample);
                sample.recycle();
                if (visible) {
                    callback.onResult(true);
                } else if (remaining <= 1 || renderError != null) {
                    callback.onResult(false);
                } else {
                    handler.postDelayed(() -> probeVisibleFrame(handler, callback, remaining - 1), 250L);
                }
            }, handler);
        } catch (Throwable e) {
            sample.recycle();
            handler.postDelayed(() -> probeVisibleFrame(handler, callback, remaining - 1), 250L);
        }
    }

    private static boolean hasModelPixels(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        int modelPixels = 0;
        int required = Math.max(24, pixels.length / 250);
        for (int c : pixels) {
            int r = Color.red(c), g = Color.green(c), b = Color.blue(c);
            if (Math.max(r, Math.max(g, b)) > 38 && r + g + b > 115) {
                if (++modelPixels >= required) return true;
            }
        }
        return false;
    }

    public void startRendering() {
        if (!running && renderError == null) {
            running = true;
            choreographer.postFrameCallback(frameCallback);
        }
    }

    public void stopRendering() {
        running = false;
        choreographer.removeFrameCallback(frameCallback);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startRendering();
    }

    @Override protected void onDetachedFromWindow() {
        stopRendering();
        try { uiHelper.detach(); } catch (Throwable ignored) {}
        super.onDetachedFromWindow();
    }

    // v29 is deliberately static. These API hooks return in the animation build after 3D is visible.
    public void setAvatarState(CelineAvatarController.State next) {}
    public void setSpeechEnergy(float level) {}
    public void setLook(float x, float y) {}
    public void releaseLook() {}
    public void setViseme(SpeechVisemeAnalyzer.Cue cue) {}

    private static ByteBuffer readModel(Context context) throws Exception {
        File imported = importedModelFile(context);
        if (imported.isFile() && imported.length() > 32) {
            try (InputStream in = new FileInputStream(imported)) { return readAll(in); }
        }
        try (InputStream in = context.getAssets().open(MODEL_PATH)) { return readAll(in); }
    }

    private static ByteBuffer readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[64 * 1024];
        int n;
        while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
        byte[] bytes = out.toByteArray();
        ByteBuffer direct = ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.nativeOrder());
        direct.put(bytes);
        direct.rewind();
        return direct;
    }
}
