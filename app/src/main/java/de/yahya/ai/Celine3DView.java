package de.yahya.ai;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.view.Choreographer;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.widget.FrameLayout;

import com.google.android.filament.Engine;
import com.google.android.filament.IndirectLight;
import com.google.android.filament.Skybox;
import com.google.android.filament.android.UiHelper;
import com.google.android.filament.utils.Float3;
import com.google.android.filament.utils.ModelViewer;
import com.google.android.filament.utils.Utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Minimal 3D baseline for Celine.
 *
 * v29 intentionally contains NO bone animation, NO morph animation and NO renderer switching.
 * The only goal is to prove that the imported GLB is visibly rendered on the target Android device.
 */
public final class Celine3DView extends FrameLayout {
    private static final String MODEL_PATH = "models/celine.glb";
    private static final String IMPORT_DIR = "models";
    private static final String IMPORT_FILE = "celine.glb";

    static { Utils.INSTANCE.init(); }

    public interface VisibilityCallback { void onResult(boolean visible); }

    private final SurfaceView surfaceView;
    private final Choreographer choreographer;
    private final ModelViewer viewer;
    private final Skybox skybox;
    private final IndirectLight indirectLight;
    private boolean running;
    private volatile Throwable renderError;

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override public void doFrame(long frameTimeNanos) {
            if (!running) return;
            choreographer.postFrameCallback(this);
            try {
                // Standstill on purpose. Rendering itself is the only thing under test in v29.
                viewer.render(frameTimeNanos);
            } catch (Throwable e) {
                renderError = e;
                running = false;
                choreographer.removeFrameCallback(this);
            }
        }
    };

    public Celine3DView(Context context) throws Exception { this(context, true); }

    /** The boolean is retained only for source compatibility with the controller. */
    public Celine3DView(Context context, boolean ignoredRendererChoice) throws Exception {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);

        choreographer = Choreographer.getInstance();
        surfaceView = new SurfaceView(context);
        // No special Z-order tricks in the baseline: use Android's normal SurfaceView path.
        addView(surfaceView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        Engine engine = Engine.create();
        UiHelper helper = new UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK);

        // No camera manipulator. ModelViewer will not overwrite the fixed camera below.
        viewer = new ModelViewer(surfaceView, engine, helper, null);

        // Constant dark background. If the model renders, its skin/hair/clothes are much brighter.
        skybox = new Skybox.Builder()
                .color(0.018f, 0.022f, 0.030f, 1.0f)
                .build(engine);
        viewer.getScene().setSkybox(skybox);

        // Filament's ModelViewer explicitly expects the app to provide indirect/environment light.
        // A one-band white SH is enough for this diagnostic baseline and avoids any KTX/IBL files.
        indirectLight = new IndirectLight.Builder()
                .irradiance(1, new float[]{1.0f, 1.0f, 1.0f})
                .intensity(30000.0f)
                .build(engine);
        viewer.getScene().setIndirectLight(indirectLight);

        viewer.loadModelGlb(readModel(context));
        if (viewer.getAsset() == null) {
            throw new IllegalStateException("Filament konnte die importierte GLB-Datei nicht laden.");
        }

        // Official ModelViewer normalization. This compensates Meshy's Armature scale of 0.01.
        viewer.transformToUnitCube(new Float3(0f, 0f, -4f));

        // Fixed camera directly in front of the centered model.
        viewer.setCameraFocalLength(32f);
        viewer.getCamera().lookAt(
                0.0, 0.0, 1.0,
                0.0, 0.0, -4.0,
                0.0, 1.0, 0.0
        );
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

    public String getRendererName() { return "SurfaceView · Filament 1.74"; }

    public String getRenderFailureReason() {
        Throwable e = renderError;
        if (e == null) return null;
        String m = e.getMessage();
        return m == null || m.trim().isEmpty() ? e.getClass().getSimpleName() : m;
    }

    /** Wait for the 27 MB GLB / 4K texture to become GPU-ready, then inspect real surface pixels. */
    public void verifyVisibleFrame(Handler handler, VisibilityCallback callback) {
        probeVisibleFrame(handler, callback, 40);
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
            handler.postDelayed(() -> probeVisibleFrame(handler, callback, remaining - 1), 300L);
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
                    handler.postDelayed(() -> probeVisibleFrame(handler, callback, remaining - 1), 300L);
                }
            }, handler);
        } catch (Throwable e) {
            sample.recycle();
            handler.postDelayed(() -> probeVisibleFrame(handler, callback, remaining - 1), 300L);
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
            int r = Color.red(c);
            int g = Color.green(c);
            int b = Color.blue(c);
            if (Math.max(r, Math.max(g, b)) > 34 && r + g + b > 105) {
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
        super.onDetachedFromWindow();
    }

    // v29 standstill baseline: retained API hooks are intentionally no-ops.
    public void setAvatarState(CelineAvatarController.State next) {}
    public void setSpeechEnergy(float level) {}
    public void setLook(float x, float y) {}
    public void releaseLook() {}
    public void setViseme(SpeechVisemeAnalyzer.Cue cue) {}

    private static ByteBuffer readModel(Context context) throws Exception {
        File imported = importedModelFile(context);
        if (imported.isFile() && imported.length() > 32) {
            try (InputStream in = new FileInputStream(imported)) {
                return readAll(in);
            }
        }
        try (InputStream in = context.getAssets().open(MODEL_PATH)) {
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
