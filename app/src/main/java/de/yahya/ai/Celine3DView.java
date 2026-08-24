package de.yahya.ai;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.opengl.Matrix;
import android.os.Handler;
import android.view.Choreographer;
import android.view.PixelCopy;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;

import com.google.android.filament.Box;
import com.google.android.filament.Camera;
import com.google.android.filament.Engine;
import com.google.android.filament.EntityManager;
import com.google.android.filament.IndirectLight;
import com.google.android.filament.LightManager;
import com.google.android.filament.MaterialInstance;
import com.google.android.filament.Renderer;
import com.google.android.filament.Scene;
import com.google.android.filament.Skybox;
import com.google.android.filament.SwapChain;
import com.google.android.filament.TransformManager;
import com.google.android.filament.Viewport;
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
 * v30 direct 3D renderer.
 *
 * Keeps the proven v29 SurfaceView path, corrects Meshy's over-bright emissive material,
 * frames Celine larger, and adds subtle procedural skeleton motion so the 3D avatar is alive
 * even when the imported Character_output.glb only contains a one-frame bind-pose clip.
 */
public final class Celine3DView extends FrameLayout {
    private static final String MODEL_PATH = "models/celine.glb";
    private static final String IMPORT_DIR = "models";
    private static final String IMPORT_FILE = "celine.glb";
    private static final float MODEL_TARGET_SIZE = 3.15f;

    static { Gltfio.init(); }

    public interface VisibilityCallback { void onResult(boolean visible); }

    private static final class BonePose {
        final int instance;
        final float[] base;
        BonePose(int instance, float[] base) {
            this.instance = instance;
            this.base = base;
        }
    }

    private final SurfaceView surfaceView;
    private final Choreographer choreographer;
    private final Engine engine;
    private final Renderer renderer;
    private final Scene scene;
    private final com.google.android.filament.View filamentView;
    private final Camera camera;
    private final int cameraEntity;
    private final int lightEntity;
    private final UbershaderProvider materialProvider;
    private final AssetLoader assetLoader;
    private final ResourceLoader resourceLoader;
    private final FilamentAsset asset;
    private final Skybox skybox;
    private final IndirectLight indirectLight;
    private final TransformManager transformManager;

    private BonePose headBone;
    private BonePose neckBone;
    private BonePose spineBone;
    private BonePose spine01Bone;
    private BonePose spine02Bone;

    private SwapChain swapChain;
    private boolean running;
    private volatile Throwable renderError;
    private volatile float speechEnergy;
    private volatile float lookX;
    private volatile float lookY;
    private volatile boolean lookActive;
    private volatile CelineAvatarController.State avatarState = CelineAvatarController.State.IDLE;

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override public void doFrame(long frameTimeNanos) {
            if (!running) return;
            choreographer.postFrameCallback(this);
            try {
                if (swapChain == null || !isSurfaceReady()) return;
                updateLivePose(frameTimeNanos);
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
        transformManager = engine.getTransformManager();

        cameraEntity = EntityManager.get().create();
        camera = engine.createCamera(cameraEntity);
        camera.setExposure(8.0f, 1.0f / 125.0f, 100.0f);
        filamentView.setScene(scene);
        filamentView.setCamera(camera);

        skybox = new Skybox.Builder()
                .color(0.018f, 0.022f, 0.030f, 1.0f)
                .build(engine);
        scene.setSkybox(skybox);

        indirectLight = new IndirectLight.Builder()
                .irradiance(1, new float[]{1.0f, 0.98f, 0.96f})
                .intensity(7000.0f)
                .build(engine);
        scene.setIndirectLight(indirectLight);

        lightEntity = EntityManager.get().create();
        new LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(1.0f, 0.96f, 0.92f)
                .intensity(32000.0f)
                .direction(-0.28f, -0.55f, -1.0f)
                .castShadows(false)
                .build(engine, lightEntity);
        scene.addEntity(lightEntity);

        materialProvider = new UbershaderProvider(engine);
        assetLoader = new AssetLoader(engine, materialProvider, EntityManager.get());
        resourceLoader = new ResourceLoader(engine, true);
        asset = assetLoader.createAsset(readModel(context));
        if (asset == null) {
            throw new IllegalStateException("gltfio konnte die importierte GLB-Datei nicht laden.");
        }
        resourceLoader.loadResources(asset);
        tameMeshyMaterials();
        asset.releaseSourceData();

        normalizeAsset(asset);
        captureLiveBones();
        scene.addEntities(asset.getEntities());

        camera.lookAt(0.0, 0.0, 1.0, 0.0, 0.0, -4.0, 0.0, 1.0, 0.0);

        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override public void surfaceCreated(SurfaceHolder holder) {
                try { createSwapChain(holder); } catch (Throwable e) { renderError = e; }
            }

            @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                try {
                    if (swapChain == null) createSwapChain(holder);
                    resizeViewport(width, height);
                } catch (Throwable e) {
                    renderError = e;
                }
            }

            @Override public void surfaceDestroyed(SurfaceHolder holder) {
                destroySwapChain();
            }
        });
    }

    private void tameMeshyMaterials() {
        try {
            MaterialInstance[] instances = asset.getInstance().getMaterialInstances();
            for (MaterialInstance material : instances) {
                try { material.setParameter("emissiveFactor", 0.0f, 0.0f, 0.0f); } catch (Throwable ignored) {}
                try { material.setParameter("emissiveStrength", 0.0f); } catch (Throwable ignored) {}
                try { material.setParameter("specularColorFactor", 1.0f, 1.0f, 1.0f); } catch (Throwable ignored) {}
                try { material.setParameter("specularFactor", 1.0f); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    private void captureLiveBones() {
        headBone = captureBone("Head");
        neckBone = captureBone("neck");
        spineBone = captureBone("Spine");
        spine01Bone = captureBone("Spine01");
        spine02Bone = captureBone("Spine02");
    }

    private BonePose captureBone(String name) {
        try {
            int entity = asset.getFirstEntityByName(name);
            if (entity == 0) return null;
            int instance = transformManager.getInstance(entity);
            if (instance == 0) return null;
            return new BonePose(instance, transformManager.getTransform(instance, new float[16]));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void updateLivePose(long frameTimeNanos) {
        if (headBone == null && neckBone == null && spineBone == null && spine01Bone == null && spine02Bone == null) return;

        double t = frameTimeNanos * 1.0e-9;
        float breath = (float) Math.sin(t * 1.45);
        float slowSway = (float) Math.sin(t * 0.58);
        float micro = (float) Math.sin(t * 0.91 + 0.7);
        float speech = clamp(speechEnergy, 0.0f, 1.0f);
        float speakingNod = avatarState == CelineAvatarController.State.SPEAKING
                ? (float) Math.sin(t * 5.7) * (0.8f + 1.8f * speech)
                : 0.0f;
        float listeningTilt = avatarState == CelineAvatarController.State.LISTENING ? 1.2f : 0.0f;

        float targetYaw = lookActive ? clamp(lookX, -1.0f, 1.0f) * 7.0f : slowSway * 1.3f;
        float targetPitch = lookActive ? clamp(-lookY, -1.0f, 1.0f) * 4.5f : breath * 0.65f;

        try {
            transformManager.openLocalTransformTransaction();
            applyBone(spineBone, 0.25f * breath, 0.0f, 0.45f * slowSway);
            applyBone(spine01Bone, 0.45f * breath, 0.0f, 0.65f * slowSway);
            applyBone(spine02Bone, 0.55f * breath, 0.15f * micro, 0.75f * slowSway);
            applyBone(neckBone,
                    targetPitch * 0.35f + speakingNod * 0.20f,
                    targetYaw * 0.45f,
                    -0.45f * slowSway + listeningTilt * 0.35f);
            applyBone(headBone,
                    targetPitch + speakingNod,
                    targetYaw,
                    -0.85f * slowSway + listeningTilt);
        } finally {
            transformManager.commitLocalTransformTransaction();
        }
    }

    private void applyBone(BonePose bone, float pitchDeg, float yawDeg, float rollDeg) {
        if (bone == null) return;
        float[] delta = new float[16];
        float[] out = new float[16];
        Matrix.setIdentityM(delta, 0);
        if (yawDeg != 0.0f) Matrix.rotateM(delta, 0, yawDeg, 0.0f, 1.0f, 0.0f);
        if (pitchDeg != 0.0f) Matrix.rotateM(delta, 0, pitchDeg, 1.0f, 0.0f, 0.0f);
        if (rollDeg != 0.0f) Matrix.rotateM(delta, 0, rollDeg, 0.0f, 0.0f, 1.0f);
        Matrix.multiplyMM(out, 0, bone.base, 0, delta, 0);
        transformManager.setTransform(bone.instance, out);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private void createSwapChain(SurfaceHolder holder) {
        if (holder == null || holder.getSurface() == null || !holder.getSurface().isValid()) return;
        destroySwapChain();
        swapChain = engine.createSwapChain(holder.getSurface());
    }

    private void destroySwapChain() {
        try {
            if (swapChain != null) {
                engine.destroySwapChain(swapChain);
                engine.flushAndWait();
                swapChain = null;
            }
        } catch (Throwable e) {
            renderError = e;
            swapChain = null;
        }
    }

    private void resizeViewport(int width, int height) {
        if (width <= 0 || height <= 0) return;
        filamentView.setViewport(new Viewport(0, 0, width, height));
        camera.setLensProjection(32.0, (double) width / (double) height, 0.05, 1000.0);
        camera.lookAt(0.0, 0.0, 1.0, 0.0, 0.0, -4.0, 0.0, 1.0, 0.0);
    }

    private boolean isSurfaceReady() {
        try {
            return surfaceView.getHolder() != null &&
                    surfaceView.getHolder().getSurface() != null &&
                    surfaceView.getHolder().getSurface().isValid();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void normalizeAsset(FilamentAsset loadedAsset) {
        Box box = loadedAsset.getBoundingBox();
        float[] center = box.getCenter();
        float[] half = box.getHalfExtent();
        float maxExtent = 2.0f * Math.max(half[0], Math.max(half[1], half[2]));
        if (!(maxExtent > 0.000001f) || Float.isNaN(maxExtent) || Float.isInfinite(maxExtent)) {
            throw new IllegalStateException("Ungültige 3D-Modellgröße: " + maxExtent);
        }
        float scale = MODEL_TARGET_SIZE / maxExtent;

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

        int instance = transformManager.getInstance(loadedAsset.getRoot());
        if (instance == 0) throw new IllegalStateException("3D-Root-Transform fehlt.");
        transformManager.setTransform(instance, transform);
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

    public String getRendererName() { return "Direct SurfaceView · Filament 1.72 · v30"; }

    public String getRenderFailureReason() {
        Throwable e = renderError;
        if (e == null) return null;
        String m = e.getMessage();
        return m == null || m.trim().isEmpty() ? e.getClass().getSimpleName() : m;
    }

    public void verifyVisibleFrame(Handler handler, VisibilityCallback callback) {
        probeVisibleFrame(handler, callback, 35);
    }

    private void probeVisibleFrame(Handler handler, VisibilityCallback callback, int remaining) {
        if (callback == null) return;
        if (renderError != null || remaining <= 0) {
            callback.onResult(false);
            return;
        }
        if (!isAttachedToWindow() || getWidth() <= 0 || getHeight() <= 0 || !running || !isSurfaceReady()) {
            handler.postDelayed(() -> probeVisibleFrame(handler, callback, remaining - 1), 250L);
            return;
        }

        final Bitmap sample = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        try {
            PixelCopy.request(surfaceView, sample, result -> {
                boolean visible = result == PixelCopy.SUCCESS && hasModelPixels(sample);
                sample.recycle();
                if (visible) callback.onResult(true);
                else if (remaining <= 1 || renderError != null) callback.onResult(false);
                else handler.postDelayed(() -> probeVisibleFrame(handler, callback, remaining - 1), 250L);
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
            if (Math.max(r, Math.max(g, b)) > 32 && r + g + b > 95) {
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
        destroySwapChain();
        super.onDetachedFromWindow();
    }

    public void setAvatarState(CelineAvatarController.State next) {
        avatarState = next == null ? CelineAvatarController.State.IDLE : next;
    }

    public void setSpeechEnergy(float level) {
        speechEnergy = clamp(level, 0.0f, 1.0f);
    }

    public void setLook(float x, float y) {
        lookX = clamp(x, -1.0f, 1.0f);
        lookY = clamp(y, -1.0f, 1.0f);
        lookActive = true;
    }

    public void releaseLook() {
        lookActive = false;
    }

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
