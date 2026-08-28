package de.yahya.ai;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.opengl.Matrix;
import android.os.Handler;
import android.view.Choreographer;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.ScaleGestureDetector;
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
import com.google.android.filament.RenderableManager;
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

/** v60 direct Filament renderer with bounded camera search controls and production-model diagnostics. */
public final class Celine3DView extends FrameLayout {
    private static final String MODEL_PATH = "models/celine.glb";
    private static final String IMPORT_DIR = "models";
    private static final String IMPORT_FILE = "celine.glb";
    private static final float MODEL_TARGET_SIZE = 3.15f;
    private static final float CAMERA_TARGET_Z = -4.0f;
    private static final float CAMERA_BASE_DISTANCE = 5.0f;
    private static final float CAMERA_PAN_X_MAX = 2.50f;
    private static final float CAMERA_PAN_Y_MAX = 2.00f;
    private static final float CAMERA_ZOOM_MIN = 0.55f;
    private static final float CAMERA_ZOOM_MAX = 4.60f;

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

    private final Context appContext;
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
    private final GestureDetector gestureDetector;
    private final ScaleGestureDetector scaleGestureDetector;

    private BonePose headBone;
    private BonePose neckBone;
    private BonePose spineBone;
    private BonePose spine01Bone;
    private BonePose spine02Bone;

    private SwapChain swapChain;
    private boolean running;
    private boolean firstFrameLogged;
    private volatile Throwable renderError;
    private volatile float speechEnergy;
    private volatile float lookX;
    private volatile float lookY;
    private volatile boolean lookActive;
    private volatile float cameraPanX;
    private volatile float cameraPanY;
    private volatile float cameraZoom = 1.0f;
    private volatile boolean diagnosticCameraOrbitEnabled;
    private volatile float diagnosticCameraOrbitYawDeg;
    private volatile CelineAvatarController.State avatarState = CelineAvatarController.State.IDLE;

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override public void doFrame(long frameTimeNanos) {
            if (!running) return;
            choreographer.postFrameCallback(this);
            try {
                if (swapChain == null || !isSurfaceReady()) return;
                updateCameraPresence(frameTimeNanos);
                updateLivePose(frameTimeNanos);
                if (renderer.beginFrame(swapChain, frameTimeNanos)) {
                    renderer.render(filamentView);
                    renderer.endFrame();
                    if (!firstFrameLogged) {
                        firstFrameLogged = true;
                        Celine3DDiagnostics.record(appContext, "REN-331", "Erster Filament-Frame gerendert",
                                "Surface=" + getWidth() + "x" + getHeight());
                    }
                }
            } catch (Throwable e) {
                renderError = e;
                Celine3DDiagnostics.error(appContext, "REN-399", "Filament Frame FEHLER", e);
                running = false;
                choreographer.removeFrameCallback(this);
            }
        }
    };

    public Celine3DView(Context context) throws Exception { this(context, true); }

    public Celine3DView(Context context, boolean ignoredRendererChoice) throws Exception {
        super(context);
        appContext = context.getApplicationContext();
        Celine3DDiagnostics.record(appContext, "REN-300", "Celine3DView Konstruktor", Celine3DDiagnostics.modelSnapshot(appContext));
        setClipChildren(false);
        setClipToPadding(false);

        choreographer = Choreographer.getInstance();
        surfaceView = new SurfaceView(context);
        addView(surfaceView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        Celine3DDiagnostics.record(appContext, "REN-301", "SurfaceView erstellt", "OK");

        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                float before = cameraZoom;
                cameraZoom = clamp(cameraZoom * detector.getScaleFactor(), CAMERA_ZOOM_MIN, CAMERA_ZOOM_MAX);
                if (Math.abs(cameraZoom - before) > 0.001f) {
                    Celine3DDiagnostics.record(appContext, "V60-121", "Kamera Zoom geändert",
                            "zoom=" + cameraZoom + " bounds=" + CAMERA_ZOOM_MIN + ".." + CAMERA_ZOOM_MAX);
                }
                return true;
            }
        });
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }

            @Override public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (scaleGestureDetector.isInProgress()) return false;
                float width = Math.max(1.0f, getWidth());
                float height = Math.max(1.0f, getHeight());
                cameraPanX = clamp(cameraPanX + (distanceX / width) * 4.2f, -CAMERA_PAN_X_MAX, CAMERA_PAN_X_MAX);
                cameraPanY = clamp(cameraPanY - (distanceY / height) * 3.4f, -CAMERA_PAN_Y_MAX, CAMERA_PAN_Y_MAX);
                Celine3DDiagnostics.record(appContext, "V60-120", "Kamera Suchposition geändert",
                        "pan=" + cameraPanX + "," + cameraPanY + " zoom=" + cameraZoom);
                return true;
            }

            @Override public boolean onDoubleTap(MotionEvent e) {
                resetCameraSearch();
                return true;
            }
        });
        surfaceView.setOnTouchListener((view, event) -> {
            boolean scaled = scaleGestureDetector.onTouchEvent(event);
            boolean gestured = gestureDetector.onTouchEvent(event);
            return scaled || gestured || event.getActionMasked() == MotionEvent.ACTION_MOVE;
        });
        Celine3DDiagnostics.record(appContext, "V60-119", "Bounded Kamera-Suche aktiv",
                "1 Finger Pan/Orbit · Pinch Zoom · Doppeltipp Reset");

        engine = Engine.create();
        Celine3DDiagnostics.record(appContext, "REN-302", "Filament Engine erstellt", String.valueOf(engine != null));
        renderer = engine.createRenderer();
        scene = engine.createScene();
        filamentView = engine.createView();
        transformManager = engine.getTransformManager();
        Celine3DDiagnostics.record(appContext, "REN-303", "Renderer/Scene/View erstellt", "OK");

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
        Celine3DDiagnostics.record(appContext, "REN-304", "Licht/Kamera eingerichtet", "OK");

        materialProvider = new UbershaderProvider(engine);
        assetLoader = new AssetLoader(engine, materialProvider, EntityManager.get());
        resourceLoader = new ResourceLoader(engine, true);
        ByteBuffer modelBuffer = readModel(context);
        Celine3DDiagnostics.record(appContext, "REN-310", "GLB ByteBuffer bereit", modelBuffer.remaining() + " Bytes");
        asset = assetLoader.createAsset(modelBuffer);
        if (asset == null) {
            Celine3DDiagnostics.record(appContext, "REN-398", "gltfio createAsset FEHLER", "asset == null");
            throw new IllegalStateException("gltfio konnte die importierte GLB-Datei nicht laden.");
        }
        int renderableCount = countRenderables(asset);
        Celine3DDiagnostics.record(appContext, "REN-311", "gltfio Asset erstellt",
                "entities=" + asset.getEntities().length + " renderables=" + renderableCount);
        Celine3DDiagnostics.record(appContext, renderableCount > 0 ? "V60-110" : "V60-199",
                renderableCount > 0 ? "Produktionsmodell enthält Renderables" : "Produktionsmodell OHNE Renderables",
                "entities=" + asset.getEntities().length + " renderables=" + renderableCount);

        resourceLoader.loadResources(asset);
        Celine3DDiagnostics.record(appContext, "REN-312", "GLB Ressourcen geladen", "loadResources OK");
        tameMeshyMaterials();
        Celine3DDiagnostics.record(appContext, "REN-313", "Runtime-Materialwerte gesetzt", "PBR repair angewendet");
        asset.releaseSourceData();

        normalizeAsset(asset);
        captureLiveBones();
        scene.addEntities(asset.getEntities());
        Celine3DDiagnostics.record(appContext, "REN-316", "Entities zur Scene hinzugefügt",
                "entities=" + asset.getEntities().length + " renderables=" + renderableCount);
        Celine3DDiagnostics.record(appContext, "V60-111", "Produktionsmodell zur Scene hinzugefügt",
                "root=" + asset.getRoot() + " entities=" + asset.getEntities().length + " renderables=" + renderableCount);

        resetCameraSearch();

        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override public void surfaceCreated(SurfaceHolder holder) {
                Celine3DDiagnostics.record(appContext, "REN-320", "Surface created", "valid=" + isSurfaceReady());
                try { createSwapChain(holder); }
                catch (Throwable e) {
                    renderError = e;
                    Celine3DDiagnostics.error(appContext, "REN-397", "SwapChain bei surfaceCreated FEHLER", e);
                }
            }

            @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                Celine3DDiagnostics.record(appContext, "REN-322", "Surface changed", width + "x" + height + " format=" + format);
                try {
                    if (swapChain == null) createSwapChain(holder);
                    resizeViewport(width, height);
                } catch (Throwable e) {
                    renderError = e;
                    Celine3DDiagnostics.error(appContext, "REN-396", "Viewport/SwapChain FEHLER", e);
                }
            }

            @Override public void surfaceDestroyed(SurfaceHolder holder) {
                Celine3DDiagnostics.record(appContext, "REN-323", "Surface destroyed", "stop/destroy swapchain");
                destroySwapChain();
            }
        });
        Celine3DDiagnostics.record(appContext, "REN-319", "3D-Konstruktor abgeschlossen", "warte auf Surface/Frames");
    }

    private int countRenderables(FilamentAsset loadedAsset) {
        try {
            RenderableManager manager = engine.getRenderableManager();
            int count = 0;
            for (int entity : loadedAsset.getEntities()) {
                if (manager.hasComponent(entity)) count++;
            }
            return count;
        } catch (Throwable e) {
            Celine3DDiagnostics.error(appContext, "V60-198", "Renderable-Zählung fehlgeschlagen", e);
            return -1;
        }
    }

    private void tameMeshyMaterials() {
        try {
            MaterialInstance[] instances = asset.getInstance().getMaterialInstances();
            for (MaterialInstance material : instances) {
                try { material.setParameter("metallicFactor", 0.0f); } catch (Throwable ignored) {}
                try { material.setParameter("roughnessFactor", 0.75f); } catch (Throwable ignored) {}
                try { material.setParameter("baseColorFactor", 1.0f, 1.0f, 1.0f, 1.0f); } catch (Throwable ignored) {}
                try { material.setParameter("emissiveFactor", 0.0f, 0.0f, 0.0f); } catch (Throwable ignored) {}
                try { material.setParameter("emissiveStrength", 0.0f); } catch (Throwable ignored) {}
                try { material.setParameter("specularFactor", 0.3f); } catch (Throwable ignored) {}
                try { material.setParameter("specularColorFactor", 1.0f, 1.0f, 1.0f); } catch (Throwable ignored) {}
                try { material.setParameter("reflectance", 0.5f); } catch (Throwable ignored) {}
            }
        } catch (Throwable e) {
            Celine3DDiagnostics.error(appContext, "REN-395", "Material-Reparatur Exception", e);
        }
    }

    private void captureLiveBones() {
        headBone = captureBone("Head");
        neckBone = captureBone("neck");
        spineBone = captureBone("Spine");
        spine01Bone = captureBone("Spine01");
        spine02Bone = captureBone("Spine02");
        int count = 0;
        if (headBone != null) count++;
        if (neckBone != null) count++;
        if (spineBone != null) count++;
        if (spine01Bone != null) count++;
        if (spine02Bone != null) count++;
        Celine3DDiagnostics.record(appContext, "REN-315", "Live-Bones erfasst",
                count + "/5 · Head=" + (headBone != null) + " neck=" + (neckBone != null) +
                        " Spine=" + (spineBone != null) + " Spine01=" + (spine01Bone != null) +
                        " Spine02=" + (spine02Bone != null));
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

    private void updateCameraPresence(long frameTimeNanos) {
        double t = frameTimeNanos * 1.0e-9;
        float amplitude;
        float speed;
        float breathDepth;
        switch (avatarState) {
            case LISTENING:
                amplitude = 0.0055f;
                speed = 0.46f;
                breathDepth = 0.0035f;
                break;
            case THINKING:
                amplitude = 0.0140f;
                speed = 0.32f;
                breathDepth = 0.0045f;
                break;
            case SPEAKING:
                amplitude = 0.0090f + 0.0040f * clamp(speechEnergy, 0.0f, 1.0f);
                speed = 0.62f;
                breathDepth = 0.0045f + 0.0025f * clamp(speechEnergy, 0.0f, 1.0f);
                break;
            case IDLE:
            default:
                amplitude = 0.0080f;
                speed = 0.40f;
                breathDepth = 0.0060f;
                break;
        }
        float side = (float) Math.sin(t * speed) * amplitude;
        float lift = (float) Math.sin(t * (speed * 0.73f) + 1.1) * amplitude * 0.32f;
        float breath = (float) Math.sin(t * 1.45 + 0.35);
        float depth = breath * breathDepth;
        float breathLift = breath * breathDepth * 0.30f;
        float microTargetX = side * 0.22f;
        float microTargetY = lift * 0.18f + breathLift * 0.12f;
        float distance = CAMERA_BASE_DISTANCE / cameraZoom;

        if (diagnosticCameraOrbitEnabled) {
            float yawRad = (float) Math.toRadians(diagnosticCameraOrbitYawDeg);
            float eyeX = (float) Math.sin(yawRad) * distance + side;
            float eyeY = cameraPanY + lift + breathLift;
            float eyeZ = CAMERA_TARGET_Z + (float) Math.cos(yawRad) * distance + depth;
            camera.lookAt(eyeX, eyeY, eyeZ, 0.0, cameraPanY, CAMERA_TARGET_Z, 0.0, 1.0, 0.0);
            return;
        }

        float eyeX = side + cameraPanX * 0.28f;
        float eyeY = lift + breathLift + cameraPanY * 0.28f;
        float eyeZ = CAMERA_TARGET_Z + distance + depth;
        float targetX = cameraPanX + microTargetX;
        float targetY = cameraPanY + microTargetY;
        camera.lookAt(eyeX, eyeY, eyeZ, targetX, targetY, CAMERA_TARGET_Z, 0.0, 1.0, 0.0);
    }

    void v79SetDiagnosticCameraOrbit(float yawDeg) {
        diagnosticCameraOrbitYawDeg = clamp(yawDeg, -180.0f, 180.0f);
        diagnosticCameraOrbitEnabled = true;
        cameraPanX = 0.0f;
        Celine3DDiagnostics.record(appContext, "V79-540", "Avatar Lab echter Kamera-Orbit aktiv",
                "yaw=" + diagnosticCameraOrbitYawDeg + " target=0," + cameraPanY + "," + CAMERA_TARGET_Z + " rootScaleChanged=false");
    }

    void v79ClearDiagnosticCameraOrbit() {
        diagnosticCameraOrbitEnabled = false;
        diagnosticCameraOrbitYawDeg = 0.0f;
    }

    private void resetCameraSearch() {
        cameraPanX = 0.0f;
        cameraPanY = 0.0f;
        cameraZoom = 1.0f;
        diagnosticCameraOrbitEnabled = false;
        diagnosticCameraOrbitYawDeg = 0.0f;
        camera.lookAt(0.0, 0.0, 1.0, 0.0, 0.0, CAMERA_TARGET_Z, 0.0, 1.0, 0.0);
        Celine3DDiagnostics.record(appContext, "V60-122", "Kamera auf sicheren Default zurückgesetzt",
                "pan=0,0 zoom=1 targetZ=" + CAMERA_TARGET_Z);
    }

    private void updateLivePose(long frameTimeNanos) {
        CelineProductionPresenceV80.onFrame(this, frameTimeNanos);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private void createSwapChain(SurfaceHolder holder) {
        if (holder == null || holder.getSurface() == null || !holder.getSurface().isValid()) {
            Celine3DDiagnostics.record(appContext, "REN-321", "SwapChain übersprungen", "Surface ungültig");
            return;
        }
        destroySwapChain();
        swapChain = engine.createSwapChain(holder.getSurface());
        Celine3DDiagnostics.record(appContext, "REN-321", "SwapChain erstellt", "swapChain=" + (swapChain != null));
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
            Celine3DDiagnostics.error(appContext, "REN-394", "SwapChain destroy FEHLER", e);
        }
    }

    private void resizeViewport(int width, int height) {
        if (width <= 0 || height <= 0) return;
        filamentView.setViewport(new Viewport(0, 0, width, height));
        camera.setLensProjection(32.0, (double) width / (double) height, 0.05, 1000.0);
        updateCameraPresence(System.nanoTime());
        Celine3DDiagnostics.record(appContext, "REN-324", "Viewport gesetzt", width + "x" + height + " · v60 bounded camera retained");
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
        Matrix.translateM(centerAtTarget, 0, 0.0f, 0.0f, CAMERA_TARGET_Z);
        Matrix.multiplyMM(temp, 0, scaleMatrix, 0, moveToOrigin, 0);
        Matrix.multiplyMM(transform, 0, centerAtTarget, 0, temp, 0);

        int instance = transformManager.getInstance(loadedAsset.getRoot());
        if (instance == 0) throw new IllegalStateException("3D-Root-Transform fehlt.");
        transformManager.setTransform(instance, transform);
        Celine3DDiagnostics.record(appContext, "REN-314", "Modell normalisiert",
                "maxExtent=" + maxExtent + " scale=" + scale + " center=" + center[0] + "," + center[1] + "," + center[2]);
        Celine3DDiagnostics.record(appContext, "V60-112", "Produktions-Bounds für Kamera-Suche",
                "center=" + center[0] + "," + center[1] + "," + center[2] +
                        " half=" + half[0] + "," + half[1] + "," + half[2] +
                        " maxExtent=" + maxExtent + " scale=" + scale + " targetZ=" + CAMERA_TARGET_Z);
    }

    public static File importedModelFile(Context context) {
        File dir = new File(context.getFilesDir(), IMPORT_DIR);
        return new File(dir, IMPORT_FILE);
    }

    public static boolean hasModel(Context context) {
        File imported = importedModelFile(context);
        if (imported.isFile() && imported.length() > 32) {
            Celine3DDiagnostics.record(context, "MOD-101", "Privates 3D-Modell gefunden", imported.length() + " Bytes");
            return true;
        }
        try (InputStream in = context.getAssets().open(MODEL_PATH)) {
            int available = in.available();
            boolean ok = available > 32;
            Celine3DDiagnostics.record(context, ok ? "MOD-102" : "MOD-199", "APK-3D-Asset geprüft",
                    "available=" + available + " · ok=" + ok);
            return ok;
        } catch (Exception e) {
            Celine3DDiagnostics.record(context, "MOD-199", "KEIN 3D-Modell gefunden",
                    "private=" + (imported.isFile() ? imported.length() : 0L) + " Bytes · asset fehlt · " + e.getClass().getSimpleName());
            return false;
        }
    }

    public String getRendererName() { return "Direct SurfaceView · Filament 1.72 · v60 bounded camera diagnostics"; }

    public String getRenderFailureReason() {
        Throwable e = renderError;
        if (e == null) return null;
        String m = e.getMessage();
        return m == null || m.trim().isEmpty() ? e.getClass().getSimpleName() : m;
    }

    public void verifyVisibleFrame(Handler handler, VisibilityCallback callback) {
        Celine3DDiagnostics.record(appContext, "VIS-400", "PixelCopy-Sichtbarkeitstest gestartet", "35 Versuche max");
        probeVisibleFrame(handler, callback, 35);
    }

    private void probeVisibleFrame(Handler handler, VisibilityCallback callback, int remaining) {
        if (callback == null) return;
        if (renderError != null || remaining <= 0) {
            Celine3DDiagnostics.record(appContext, "VIS-499", "Sichtbarkeitstest beendet",
                    "renderError=" + getRenderFailureReason() + " remaining=" + remaining);
            callback.onResult(false);
            return;
        }
        if (!isAttachedToWindow() || getWidth() <= 0 || getHeight() <= 0 || !running || !isSurfaceReady()) {
            if (remaining == 35 || remaining <= 1) {
                Celine3DDiagnostics.record(appContext, "VIS-401", "Warte auf renderfähige Surface",
                        "attached=" + isAttachedToWindow() + " size=" + getWidth() + "x" + getHeight() +
                                " running=" + running + " surface=" + isSurfaceReady() + " remaining=" + remaining);
            }
            handler.postDelayed(() -> probeVisibleFrame(handler, callback, remaining - 1), 250L);
            return;
        }

        final Bitmap sample = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        try {
            PixelCopy.request(surfaceView, sample, result -> {
                int[] stats = modelPixelStats(sample);
                boolean visible = result == PixelCopy.SUCCESS && stats[0] >= stats[1];
                if (visible || remaining <= 1 || result != PixelCopy.SUCCESS) {
                    Celine3DDiagnostics.record(appContext, visible ? "VIS-450" : "VIS-498",
                            visible ? "3D-Pixel erkannt" : "PixelCopy ohne bestätigte 3D-Pixel",
                            "pixelCopy=" + result + " bright=" + stats[0] + " required=" + stats[1] + " remaining=" + remaining);
                }
                sample.recycle();
                if (visible) callback.onResult(true);
                else if (remaining <= 1 || renderError != null) callback.onResult(false);
                else handler.postDelayed(() -> probeVisibleFrame(handler, callback, remaining - 1), 250L);
            }, handler);
        } catch (Throwable e) {
            sample.recycle();
            if (remaining <= 1) Celine3DDiagnostics.error(appContext, "VIS-497", "PixelCopy Exception", e);
            handler.postDelayed(() -> probeVisibleFrame(handler, callback, remaining - 1), 250L);
        }
    }

    private static int[] modelPixelStats(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        int modelPixels = 0;
        int required = Math.max(24, pixels.length / 250);
        for (int c : pixels) {
            int r = Color.red(c), g = Color.green(c), b = Color.blue(c);
            if (Math.max(r, Math.max(g, b)) > 32 && r + g + b > 95) modelPixels++;
        }
        return new int[]{modelPixels, required};
    }

    public void startRendering() {
        if (!running && renderError == null) {
            running = true;
            Celine3DDiagnostics.record(appContext, "REN-330", "Rendering gestartet", "Choreographer callback aktiv");
            choreographer.postFrameCallback(frameCallback);
        }
    }

    public void stopRendering() {
        running = false;
        choreographer.removeFrameCallback(frameCallback);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Celine3DDiagnostics.record(appContext, "REN-325", "Celine3DView attached", getWidth() + "x" + getHeight());
        startRendering();
    }

    @Override protected void onDetachedFromWindow() {
        Celine3DDiagnostics.record(appContext, "REN-326", "Celine3DView detached", "stop renderer");
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
            Celine3DDiagnostics.record(context, "REN-305", "Modellquelle gewählt", "PRIVATE celine.glb · " + imported.length() + " Bytes");
            Celine3DDiagnostics.record(context, "V60-101", "Produktionsmodellquelle PRIVATE", imported.getAbsolutePath() + " · " + imported.length() + " Bytes");
            try (InputStream in = new FileInputStream(imported)) { return readAll(in); }
        }
        try (InputStream in = context.getAssets().open(MODEL_PATH)) {
            int available = in.available();
            Celine3DDiagnostics.record(context, "REN-306", "Modellquelle gewählt", "APK ASSET models/celine.glb · available=" + available);
            Celine3DDiagnostics.record(context, "V60-102", "Produktionsmodellquelle APK", MODEL_PATH + " · available=" + available);
            return readAll(in);
        }
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
