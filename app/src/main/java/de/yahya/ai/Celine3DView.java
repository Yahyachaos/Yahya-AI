package de.yahya.ai;

import android.content.Context;
import android.graphics.Color;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.android.filament.Engine;
import com.google.android.filament.Skybox;
import com.google.android.filament.TransformManager;
import com.google.android.filament.android.UiHelper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

/**
 * Realtime 3D Celin renderer.
 *
 * The final Celin model must be a rigged GLB at assets/models/celine.glb.
 * The portrait/image-swap path is intentionally not used here.
 */
public final class Celine3DView extends FrameLayout implements Choreographer.FrameCallback {
    public enum State { IDLE, LISTENING, THINKING, SPEAKING }

    private final SurfaceView surface;
    private final TextView status;
    private final Choreographer choreographer;
    private Engine engine;
    private Object viewer;
    private Object asset;
    private Method renderMethod;
    private Method destroyModelMethod;
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
        addView(status, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

        choreographer = Choreographer.getInstance();
        initializeRenderer();
    }

    private void initializeRenderer() {
        try {
            // ModelViewer lives in Filament's Kotlin utility layer. Using reflection here keeps the
            // Java app independent from Kotlin default-parameter constructor bridges while still
            // using the real Filament renderer at runtime.
            Class<?> utils = Class.forName("com.google.android.filament.utils.Utils");
            try { utils.getMethod("init").invoke(null); } catch (NoSuchMethodException ignored) {}

            engine = Engine.create();
            UiHelper helper = new UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK);
            Class<?> viewerClass = Class.forName("com.google.android.filament.utils.ModelViewer");
            Constructor<?> ctor = null;
            for (Constructor<?> c : viewerClass.getConstructors()) {
                Class<?>[] p = c.getParameterTypes();
                if (p.length >= 4 && SurfaceView.class.isAssignableFrom(p[0]) && Engine.class.isAssignableFrom(p[1])) {
                    ctor = c;
                    break;
                }
            }
            if (ctor == null) throw new NoSuchMethodException("ModelViewer SurfaceView constructor");

            Object[] args = new Object[ctor.getParameterTypes().length];
            args[0] = surface;
            args[1] = engine;
            args[2] = helper;
            for (int i = 3; i < args.length; i++) args[i] = null;
            viewer = ctor.newInstance(args);

            renderMethod = viewerClass.getMethod("render", long.class);
            destroyModelMethod = viewerClass.getMethod("destroyModel");
            Object scene = viewerClass.getMethod("getScene").invoke(viewer);
            Object skybox = new Skybox.Builder().color(0.018f, 0.018f, 0.028f, 1.0f).build(engine);
            scene.getClass().getMethod("setSkybox", Skybox.class).invoke(scene, skybox);

            loadCelinModel(viewerClass);
            running = true;
            startedNanos = System.nanoTime();
            choreographer.postFrameCallback(this);
        } catch (Throwable t) {
            modelLoaded = false;
            status.setText("3D-Renderer konnte nicht gestartet werden: " + rootName(t));
        }
    }

    private void loadCelinModel(Class<?> viewerClass) {
        try {
            ByteBuffer data = readAsset("models/celine.glb");
            Method load = null;
            for (Method m : viewerClass.getMethods()) {
                if (m.getName().equals("loadModelGlb") && m.getParameterTypes().length == 1) { load = m; break; }
            }
            if (load == null) throw new NoSuchMethodException("loadModelGlb");
            load.invoke(viewer, data);
            asset = viewerClass.getMethod("getAsset").invoke(viewer);
            modelLoaded = asset != null;
            status.setText(modelLoaded ? "Celin · Echtzeit-3D" : "Celin 3D-Modell konnte nicht geladen werden");
            if (modelLoaded) {
                try { viewerClass.getMethod("transformToUnitCube").invoke(viewer); } catch (Throwable ignored) {}
                status.postDelayed(() -> status.setVisibility(GONE), 1300L);
            }
        } catch (Throwable missing) {
            asset = null;
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
    public void setState(State next) { state = next == null ? State.IDLE : next; }
    public void setSpeechLevel(float value) { speech = clamp(value, 0f, 1f); }
    public void setGaze(float x, float y) { yaw = clamp(x, -1f, 1f) * 7.0f; pitch = clamp(y, -1f, 1f) * 4.0f; }
    public void releaseGaze() { yaw *= 0.35f; pitch *= 0.35f; }

    @Override public void doFrame(long frameTimeNanos) {
        if (!running || viewer == null) return;
        animateRoot(frameTimeNanos);
        try { renderMethod.invoke(viewer, frameTimeNanos); } catch (Throwable ignored) {}
        choreographer.postFrameCallback(this);
    }

    private void animateRoot(long now) {
        if (asset == null || engine == null) return;
        float t = (now - startedNanos) / 1_000_000_000f;
        float breathe = (float)Math.sin(t * 1.45f) * 0.35f;
        float statePitch = 0f, stateYaw = 0f, z = 0f;
        switch (state) {
            case LISTENING:
                statePitch = -1.2f + (float)Math.sin(t * 1.2f) * 0.45f; z = 0.012f; break;
            case THINKING:
                stateYaw = (float)Math.sin(t * 0.65f) * 2.0f; statePitch = 1.0f; break;
            case SPEAKING:
                stateYaw = (float)Math.sin(t * 1.8f) * (0.8f + speech * 1.2f);
                statePitch = (float)Math.sin(t * 2.35f) * (0.45f + speech * 0.9f);
                z = speech * 0.010f; break;
            case IDLE:
            default:
                stateYaw = (float)Math.sin(t * 0.38f) * 0.55f; break;
        }

        float rx = (pitch + statePitch + breathe * 0.16f) * 0.0174532925f;
        float ry = (yaw + stateYaw) * 0.0174532925f;
        float sx = (float)Math.sin(rx), cx = (float)Math.cos(rx);
        float sy = (float)Math.sin(ry), cy = (float)Math.cos(ry);
        float[] m = new float[]{
                cy, sx*sy, -cx*sy, 0,
                0, cx, sx, 0,
                sy, -sx*cy, cx*cy, 0,
                0, breathe * 0.0018f, z, 1
        };

        try {
            Method getRoot = asset.getClass().getMethod("getRoot");
            int root = (Integer)getRoot.invoke(asset);
            TransformManager tm = engine.getTransformManager();
            int instance = tm.getInstance(root);
            if (instance != 0) tm.setTransform(instance, m);
        } catch (Throwable ignored) {}
    }

    public void destroy() {
        running = false;
        choreographer.removeFrameCallback(this);
        if (viewer != null && destroyModelMethod != null) {
            try { destroyModelMethod.invoke(viewer); } catch (Throwable ignored) {}
        }
        viewer = null;
        asset = null;
    }

    private static String rootName(Throwable t) {
        Throwable x = t;
        while (x.getCause() != null) x = x.getCause();
        return x.getClass().getSimpleName();
    }
    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
}
