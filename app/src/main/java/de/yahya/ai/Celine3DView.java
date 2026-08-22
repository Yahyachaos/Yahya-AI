package de.yahya.ai;

import android.content.Context;
import android.graphics.Color;
import android.view.Choreographer;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.view.Gravity;

import com.google.android.filament.Engine;
import com.google.android.filament.Skybox;
import com.google.android.filament.TransformManager;
import com.google.android.filament.android.UiHelper;
import com.google.android.filament.gltfio.FilamentAsset;
import com.google.android.filament.utils.ModelViewer;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Realtime 3D Celin renderer.
 *
 * The final Celin model must be a rigged GLB at assets/models/celine.glb.
 * This view intentionally does NOT fall back to portrait frame swapping.
 * When a model is present it is rendered continuously with Filament and its
 * root transform is driven by conversation state, gaze and speech energy.
 */
public final class Celine3DView extends FrameLayout implements Choreographer.FrameCallback {
    public enum State { IDLE, LISTENING, THINKING, SPEAKING }

    private final SurfaceView surface;
    private final TextView status;
    private final Choreographer choreographer;
    private ModelViewer viewer;
    private boolean running;
    private boolean modelLoaded;
    private float yaw;
    private float pitch;
    private float speech;
    private State state = State.IDLE;
    private long startedNanos;

    public Celine3DView(Context context) {
        super(context);
        setBackgroundColor(Color.rgb(18, 18, 24));

        surface = new SurfaceView(context);
        addView(surface, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        status = new TextView(context);
        status.setTextColor(Color.WHITE);
        status.setTextSize(13f);
        status.setGravity(Gravity.CENTER);
        status.setPadding(28, 28, 28, 28);
        status.setBackgroundColor(Color.argb(130, 0, 0, 0));
        LayoutParams sp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        addView(status, sp);

        choreographer = Choreographer.getInstance();
        initializeRenderer();
    }

    private void initializeRenderer() {
        try {
            Engine engine = Engine.create();
            UiHelper helper = new UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK);
            viewer = new ModelViewer(surface, engine, helper, null);
            viewer.getScene().setSkybox(new Skybox.Builder().color(0.018f, 0.018f, 0.028f, 1.0f).build(engine));
            loadCelinModel();
            running = true;
            startedNanos = System.nanoTime();
            choreographer.postFrameCallback(this);
        } catch (Throwable t) {
            modelLoaded = false;
            status.setText("3D-Renderer konnte nicht gestartet werden: " + t.getClass().getSimpleName());
        }
    }

    private void loadCelinModel() {
        try {
            ByteBuffer data = readAsset("models/celine.glb");
            viewer.loadModelGlb(data);
            modelLoaded = viewer.getAsset() != null;
            status.setText(modelLoaded ? "Celin · Echtzeit-3D" : "Celin 3D-Modell konnte nicht geladen werden");
            if (modelLoaded) status.postDelayed(() -> status.setVisibility(GONE), 1300L);
        } catch (Throwable missing) {
            modelLoaded = false;
            status.setText("3D-Modell fehlt noch: assets/models/celine.glb");
        }
    }

    private ByteBuffer readAsset(String path) throws Exception {
        InputStream in = getContext().getAssets().open(path);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return ByteBuffer.wrap(out.toByteArray());
    }

    public boolean isModelLoaded() { return modelLoaded; }

    public void setState(State next) {
        state = next == null ? State.IDLE : next;
    }

    public void setSpeechLevel(float value) {
        speech = clamp(value, 0f, 1f);
    }

    public void setGaze(float x, float y) {
        yaw = clamp(x, -1f, 1f) * 7.0f;
        pitch = clamp(y, -1f, 1f) * 4.0f;
    }

    public void releaseGaze() {
        yaw *= 0.35f;
        pitch *= 0.35f;
    }

    @Override public void doFrame(long frameTimeNanos) {
        if (!running || viewer == null) return;
        animateRoot(frameTimeNanos);
        try { viewer.render(frameTimeNanos); } catch (Throwable ignored) {}
        choreographer.postFrameCallback(this);
    }

    private void animateRoot(long now) {
        FilamentAsset asset = viewer.getAsset();
        if (asset == null) return;

        float t = (now - startedNanos) / 1_000_000_000f;
        float breathe = (float) Math.sin(t * 1.45f) * 0.35f;
        float statePitch = 0f;
        float stateYaw = 0f;
        float z = 0f;

        switch (state) {
            case LISTENING:
                statePitch = -1.2f + (float)Math.sin(t * 1.2f) * 0.45f;
                z = 0.012f;
                break;
            case THINKING:
                stateYaw = (float)Math.sin(t * 0.65f) * 2.0f;
                statePitch = 1.0f;
                break;
            case SPEAKING:
                stateYaw = (float)Math.sin(t * 1.8f) * (0.8f + speech * 1.2f);
                statePitch = (float)Math.sin(t * 2.35f) * (0.45f + speech * 0.9f);
                z = speech * 0.010f;
                break;
            case IDLE:
            default:
                stateYaw = (float)Math.sin(t * 0.38f) * 0.55f;
                break;
        }

        float rx = (pitch + statePitch + breathe * 0.16f) * 0.0174532925f;
        float ry = (yaw + stateYaw) * 0.0174532925f;
        float sx = (float)Math.sin(rx), cx = (float)Math.cos(rx);
        float sy = (float)Math.sin(ry), cy = (float)Math.cos(ry);

        float[] m = new float[]{
                cy, sx*sy, -cx*sy, 0,
                0,  cx,     sx,     0,
                sy, -sx*cy, cx*cy,  0,
                0,  breathe*0.0018f, z, 1
        };

        try {
            TransformManager tm = viewer.getEngine().getTransformManager();
            int instance = tm.getInstance(asset.getRoot());
            if (instance != 0) tm.setTransform(instance, m);
        } catch (Throwable ignored) {}
    }

    public void destroy() {
        running = false;
        choreographer.removeFrameCallback(this);
        if (viewer != null) {
            try { viewer.destroyModel(); } catch (Throwable ignored) {}
            viewer = null;
        }
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
