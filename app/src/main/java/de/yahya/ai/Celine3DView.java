package de.yahya.ai;

import android.content.Context;
import android.view.Choreographer;
import android.view.SurfaceView;
import android.widget.FrameLayout;

import com.google.android.filament.Engine;
import com.google.android.filament.android.UiHelper;
import com.google.android.filament.gltfio.Animator;
import com.google.android.filament.utils.Float3;
import com.google.android.filament.utils.ModelViewer;
import com.google.android.filament.utils.Utils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

/**
 * Real-time renderer for Celine's rigged GLB avatar.
 *
 * Contract:
 *   app/src/main/assets/models/celine.glb
 *
 * The GLB is expected to contain a humanoid skeleton and preferably named clips for
 * idle/listening/thinking/talking. If exact names differ, fuzzy matching is used.
 */
public final class Celine3DView extends FrameLayout {
    private static final String MODEL_PATH = "models/celine.glb";

    static {
        Utils.INSTANCE.init();
    }

    private final SurfaceView surface;
    private final Choreographer choreographer;
    private final ModelViewer viewer;
    private final long startedAtNanos = System.nanoTime();

    private boolean running;
    private CelineAvatarController.State state = CelineAvatarController.State.IDLE;
    private int activeAnimation = -1;
    private float speechEnergy;

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override public void doFrame(long frameTimeNanos) {
            if (!running) return;
            choreographer.postFrameCallback(this);

            Animator animator = viewer.getAnimator();
            if (animator != null && activeAnimation >= 0 && activeAnimation < animator.getAnimationCount()) {
                float seconds = (frameTimeNanos - startedAtNanos) / 1_000_000_000f;
                float speed = state == CelineAvatarController.State.SPEAKING
                        ? (0.92f + speechEnergy * 0.30f)
                        : 1f;
                float duration = animator.getAnimationDuration(activeAnimation);
                float t = duration > 0.001f ? (seconds * speed) % duration : seconds * speed;
                animator.applyAnimation(activeAnimation, t);
                animator.updateBoneMatrices();
            }
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
        viewer = new ModelViewer(
                surface,
                Engine.create(),
                new UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK),
                null
        );

        surface.setOnTouchListener((v, event) -> {
            viewer.onTouchEvent(event);
            return true;
        });

        ByteBuffer glb = readAsset(context, MODEL_PATH);
        viewer.loadModelGlb(glb);
        viewer.transformToUnitCube(new Float3(0f, 0f, -3.1f));
        chooseAnimation();
    }

    public static boolean hasModel(Context context) {
        try (InputStream in = context.getAssets().open(MODEL_PATH)) {
            return in.available() > 32;
        } catch (Exception ignored) {
            return false;
        }
    }

    public void setAvatarState(CelineAvatarController.State next) {
        state = next == null ? CelineAvatarController.State.IDLE : next;
        chooseAnimation();
    }

    public void setSpeechEnergy(float level) {
        speechEnergy = Math.max(0f, Math.min(1f, level));
    }

    public void setViseme(SpeechVisemeAnalyzer.Cue cue) {
        // The GLB animation already moves the jaw while speaking. Exact ARKit-style
        // morph-target mapping is intentionally isolated here for the production rig.
        // Once celine.glb exposes named facial targets, this method is the only place
        // that needs to drive their weights from SpeechVisemeAnalyzer.
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

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startRendering();
    }

    @Override protected void onDetachedFromWindow() {
        stopRendering();
        super.onDetachedFromWindow();
    }

    private void chooseAnimation() {
        Animator animator = viewer.getAnimator();
        if (animator == null || animator.getAnimationCount() == 0) {
            activeAnimation = -1;
            return;
        }

        String[] wanted;
        switch (state) {
            case LISTENING:
                wanted = new String[]{"listen", "listening", "attentive", "idle"};
                break;
            case THINKING:
                wanted = new String[]{"think", "thinking", "ponder", "idle"};
                break;
            case SPEAKING:
                wanted = new String[]{"talk", "talking", "speak", "speaking", "conversation", "idle"};
                break;
            case IDLE:
            default:
                wanted = new String[]{"idle", "breath", "breathing", "stand"};
                break;
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
        activeAnimation = 0;
    }

    private static ByteBuffer readAsset(Context context, String path) throws Exception {
        try (InputStream in = context.getAssets().open(path);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            byte[] bytes = out.toByteArray();
            ByteBuffer direct = ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.nativeOrder());
            direct.put(bytes);
            direct.rewind();
            return direct;
        }
    }
}
