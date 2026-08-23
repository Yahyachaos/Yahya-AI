package de.yahya.ai;

import android.content.Context;
import android.opengl.Matrix;
import android.view.Choreographer;
import android.view.SurfaceView;
import android.widget.FrameLayout;

import com.google.android.filament.Engine;
import com.google.android.filament.TransformManager;
import com.google.android.filament.android.UiHelper;
import com.google.android.filament.gltfio.Animator;
import com.google.android.filament.gltfio.FilamentAsset;
import com.google.android.filament.utils.Float3;
import com.google.android.filament.utils.ModelViewer;
import com.google.android.filament.utils.Utils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

/** Real-time renderer for Celine's rigged GLB avatar. */
public final class Celine3DView extends FrameLayout {
    private static final String MODEL_PATH = "models/celine.glb";

    static { Utils.INSTANCE.init(); }

    private final SurfaceView surface;
    private final Choreographer choreographer;
    private final ModelViewer viewer;
    private final long startedAtNanos = System.nanoTime();

    private boolean running;
    private CelineAvatarController.State state = CelineAvatarController.State.IDLE;
    private int activeAnimation = -1;
    private float speechEnergy;

    private BonePose head, neck, spine, spine01, spine02;

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override public void doFrame(long frameTimeNanos) {
            if (!running) return;
            choreographer.postFrameCallback(this);

            final float seconds = (frameTimeNanos - startedAtNanos) / 1_000_000_000f;
            Animator animator = viewer.getAnimator();

            if (animator != null && activeAnimation >= 0 && activeAnimation < animator.getAnimationCount()) {
                float duration = animator.getAnimationDuration(activeAnimation);
                float t = duration > 0.001f ? seconds % duration : seconds;
                animator.applyAnimation(activeAnimation, t);
            }

            applyProceduralPose(seconds);
            if (animator != null) animator.updateBoneMatrices();
            viewer.render(frameTimeNanos);
        }
    };

    public Celine3DView(Context context) throws Exception {
        super(context);
        setClipChildren(true);
        setClipToPadding(true);

        surface = new SurfaceView(context);
        addView(surface, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        choreographer = Choreographer.getInstance();
        viewer = new ModelViewer(surface, Engine.create(), new UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK), null);
        surface.setOnTouchListener((v, event) -> { viewer.onTouchEvent(event); return true; });

        viewer.loadModelGlb(readAsset(context, MODEL_PATH));
        viewer.transformToUnitCube(new Float3(0f, 0f, -3.1f));
        captureMeshyRig();
        chooseAnimation();
    }

    public static boolean hasModel(Context context) {
        try (InputStream in = context.getAssets().open(MODEL_PATH)) {
            return in.available() > 32;
        } catch (Exception ignored) { return false; }
    }

    public void setAvatarState(CelineAvatarController.State next) {
        state = next == null ? CelineAvatarController.State.IDLE : next;
        chooseAnimation();
    }

    public void setSpeechEnergy(float level) { speechEnergy = clamp(level); }

    public void setViseme(SpeechVisemeAnalyzer.Cue cue) {
        // Current Meshy export contains no facial morph targets. Speech energy still drives
        // natural head/upper-body motion. Facial blendshape control will activate once the
        // production GLB exposes jaw/eye/mouth targets.
    }

    public void startRendering() {
        if (running) return;
        running = true;
        choreographer.postFrameCallback(frameCallback);
    }

    public void stopRendering() {
        running = false;
        choreographer.removeFrameCallback(frameCallback);
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); startRendering(); }
    @Override protected void onDetachedFromWindow() { stopRendering(); super.onDetachedFromWindow(); }

    private void captureMeshyRig() {
        FilamentAsset asset = viewer.getAsset();
        if (asset == null) return;
        head = capture(asset, "Head");
        neck = capture(asset, "neck");
        spine = capture(asset, "Spine");
        spine01 = capture(asset, "Spine01");
        spine02 = capture(asset, "Spine02");
    }

    private BonePose capture(FilamentAsset asset, String name) {
        int entity = asset.getFirstEntityByName(name);
        if (entity == 0) return null;
        TransformManager tm = viewer.getEngine().getTransformManager();
        int instance = tm.getInstance(entity);
        if (instance == 0) return null;
        float[] base = new float[16];
        tm.getTransform(instance, base);
        return new BonePose(entity, instance, base);
    }

    private void applyProceduralPose(float t) {
        float breath = (float)Math.sin(t * 1.65f);
        float slow = (float)Math.sin(t * 0.72f + 0.8f);
        float talk = state == CelineAvatarController.State.SPEAKING ? speechEnergy : 0f;

        float headPitch = 0f, headYaw = 0f, headRoll = 0f, chestPitch = 0f, chestRoll = 0f;
        switch (state) {
            case LISTENING:
                headPitch = 1.4f + slow * 1.0f;
                headYaw = (float)Math.sin(t * 0.45f) * 1.7f;
                headRoll = (float)Math.sin(t * 0.31f) * 0.8f;
                chestPitch = breath * 0.45f;
                break;
            case THINKING:
                headPitch = -1.2f + slow * 1.4f;
                headYaw = 3.2f + (float)Math.sin(t * 0.38f) * 2.1f;
                headRoll = -2.0f + (float)Math.sin(t * 0.29f) * 0.7f;
                chestPitch = breath * 0.35f;
                chestRoll = (float)Math.sin(t * 0.33f) * 0.55f;
                break;
            case SPEAKING:
                headPitch = (float)Math.sin(t * 2.15f) * (0.8f + talk * 1.9f);
                headYaw = (float)Math.sin(t * 0.83f) * (1.4f + talk * 1.8f);
                headRoll = (float)Math.sin(t * 0.61f + 1.1f) * 0.8f;
                chestPitch = breath * 0.55f + talk * 0.45f;
                chestRoll = (float)Math.sin(t * 1.07f) * talk * 0.7f;
                break;
            case IDLE:
            default:
                headPitch = slow * 0.65f;
                headYaw = (float)Math.sin(t * 0.34f) * 0.9f;
                headRoll = (float)Math.sin(t * 0.27f + 1.4f) * 0.45f;
                chestPitch = breath * 0.38f;
                break;
        }

        applyRotation(spine, chestPitch * 0.35f, 0f, chestRoll * 0.25f);
        applyRotation(spine01, chestPitch * 0.45f, 0f, chestRoll * 0.45f);
        applyRotation(spine02, chestPitch * 0.60f, 0f, chestRoll * 0.65f);
        applyRotation(neck, headPitch * 0.30f, headYaw * 0.25f, headRoll * 0.25f);
        applyRotation(head, headPitch * 0.70f, headYaw * 0.75f, headRoll * 0.75f);
    }

    private void applyRotation(BonePose bone, float xDeg, float yDeg, float zDeg) {
        if (bone == null) return;
        float[] rx = new float[16], ry = new float[16], rz = new float[16];
        float[] temp = new float[16], rot = new float[16], out = new float[16];
        Matrix.setRotateM(rx, 0, xDeg, 1f, 0f, 0f);
        Matrix.setRotateM(ry, 0, yDeg, 0f, 1f, 0f);
        Matrix.setRotateM(rz, 0, zDeg, 0f, 0f, 1f);
        Matrix.multiplyMM(temp, 0, ry, 0, rx, 0);
        Matrix.multiplyMM(rot, 0, rz, 0, temp, 0);
        Matrix.multiplyMM(out, 0, bone.base, 0, rot, 0);
        viewer.getEngine().getTransformManager().setTransform(bone.instance, out);
    }

    private void chooseAnimation() {
        Animator animator = viewer.getAnimator();
        activeAnimation = -1;
        if (animator == null || animator.getAnimationCount() == 0) return;

        String[] wanted;
        switch (state) {
            case LISTENING: wanted = new String[]{"listen", "attentive"}; break;
            case THINKING: wanted = new String[]{"think", "ponder"}; break;
            case SPEAKING: wanted = new String[]{"talk", "speak", "conversation"}; break;
            case IDLE:
            default: wanted = new String[]{"idle", "breath", "stand"}; break;
        }

        for (String key : wanted) {
            for (int i = 0; i < animator.getAnimationCount(); i++) {
                String name = animator.getAnimationName(i);
                if (name != null && name.toLowerCase(Locale.ROOT).contains(key)) {
                    activeAnimation = i;
                    return;
                }
            }
        }
        // Deliberately do not fall back to Walking/Running or Meshy's one-frame baselayer.
    }

    private static ByteBuffer readAsset(Context context, String path) throws Exception {
        try (InputStream in = context.getAssets().open(path); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            byte[] bytes = out.toByteArray();
            ByteBuffer direct = ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.nativeOrder());
            direct.put(bytes); direct.rewind(); return direct;
        }
    }

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }

    private static final class BonePose {
        final int entity;
        final int instance;
        final float[] base;
        BonePose(int entity, int instance, float[] base) { this.entity = entity; this.instance = instance; this.base = base; }
    }
}
