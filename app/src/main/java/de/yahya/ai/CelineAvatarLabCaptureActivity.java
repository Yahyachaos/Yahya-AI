package de.yahya.ai;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.FrameLayout;

import java.lang.reflect.Field;

/**
 * Debug-only, panel-free capture surface for the v79 Avatar Lab proof.
 *
 * Human inspection stays in CelineAvatarLabActivity. CI uses this Activity so screenshots contain
 * only the real branch avatar and renderer: no button panel, no guessed taps, no hidden horizontal
 * controls. Every requested state comes from explicit intent extras and therefore remains
 * deterministic and cheap to repeat.
 */
public final class CelineAvatarLabCaptureActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Celine3DView celineView;
    private CelineAvatarLabPoseDriverV79 poseDriver;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
            finish();
            return;
        }

        // Keep normal system bars for CI capture. Entering Android immersive/fullscreen mode causes
        // the platform's one-time "Viewing full screen" education overlay, which can cover the
        // avatar evidence even though Filament rendered correctly underneath it.
        getWindow().setStatusBarColor(Color.rgb(10, 12, 18));
        getWindow().setNavigationBarColor(Color.rgb(10, 12, 18));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(10, 12, 18));
        try {
            celineView = new Celine3DView(this, true);
            root.addView(celineView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            setContentView(root);

            // Reuse the renderer-owned reference camera that already produced v75 multi-view
            // evidence. Never rotate/scale/translate the normalized avatar root for inspection:
            // doing so can rotate the normalization translation and move Celine out of frame.
            celineView.v75SetReferenceYaw(referenceYaw(getIntent().getStringExtra("ci_orbit")));

            poseDriver = new CelineAvatarLabPoseDriverV79(celineView);
            disableRendererLivePoseForDeterministicCapture();
            poseDriver.setMode(poseMode(getIntent().getStringExtra("ci_pose")));
            poseDriver.start();

            applyCamera(getIntent().getStringExtra("ci_camera"));
            celineView.setAvatarState(CelineAvatarController.State.LISTENING);
            celineView.setSpeechEnergy(0f);
            celineView.releaseLook();

            // Morph runtime binds after the view/asset exists. Holding the requested diagnostic
            // state removes timing races from blink screenshots.
            ui.postDelayed(this::applyFace, 650L);
            Celine3DDiagnostics.record(this, "V79-510", "Avatar Lab Capture bereit",
                    "pose=" + value("ci_pose", "stand")
                            + " camera=" + value("ci_camera", "full")
                            + " orbit=" + value("ci_orbit", "front")
                            + " face=" + value("ci_face", "neutral"));
        } catch (Throwable error) {
            Celine3DDiagnostics.error(this, "V79-599", "Avatar Lab Capture Initialisierung FEHLER", error);
            finish();
        }
    }

    private void applyFace() {
        if (celineView == null) return;
        String face = value("ci_face", "neutral");
        if ("blink85".equals(face)) {
            float[] weights = new float[CelineFacialMotionPlanner.TARGET_COUNT];
            weights[CelineFacialMotionPlanner.BLINK_BOTH] = 0.85f;
            weights[CelineFacialMotionPlanner.BLINK_LEFT] = 0.035f;
            weights[CelineFacialMotionPlanner.BLINK_RIGHT] = 0.020f;
            CelineMorphRuntimeV62.setDiagnosticWeights(celineView, weights);
        } else if ("blink100".equals(face)) {
            float[] weights = new float[CelineFacialMotionPlanner.TARGET_COUNT];
            weights[CelineFacialMotionPlanner.BLINK_BOTH] = 0.96f;
            weights[CelineFacialMotionPlanner.BLINK_LEFT] = 0.040f;
            weights[CelineFacialMotionPlanner.BLINK_RIGHT] = 0.025f;
            CelineMorphRuntimeV62.setDiagnosticWeights(celineView, weights);
        } else {
            CelineMorphRuntimeV62.clearDiagnosticWeights(celineView);
        }
    }

    private CelineAvatarLabPoseDriverV79.Mode poseMode(String raw) {
        String value = raw == null ? "stand" : raw.trim().toLowerCase();
        switch (value) {
            case "seated": return CelineAvatarLabPoseDriverV79.Mode.SEATED;
            case "walk": return CelineAvatarLabPoseDriverV79.Mode.WALK;
            case "arms": return CelineAvatarLabPoseDriverV79.Mode.ARMS;
            case "bend": return CelineAvatarLabPoseDriverV79.Mode.BEND;
            case "weight_left": return CelineAvatarLabPoseDriverV79.Mode.WEIGHT_LEFT;
            case "weight_right": return CelineAvatarLabPoseDriverV79.Mode.WEIGHT_RIGHT;
            case "live": return CelineAvatarLabPoseDriverV79.Mode.LIVE;
            case "stand":
            default: return CelineAvatarLabPoseDriverV79.Mode.STAND;
        }
    }

    private void applyCamera(String raw) throws Exception {
        String value = raw == null ? "full" : raw.trim().toLowerCase();
        float panY;
        float zoom;
        switch (value) {
            case "face":
                panY = 0.22f;
                zoom = 1.75f;
                break;
            case "upper":
                panY = 0.05f;
                zoom = 1.05f;
                break;
            case "full":
            default:
                panY = -0.15f;
                zoom = 0.68f;
                break;
        }
        setFloat("cameraPanX", 0f);
        setFloat("cameraPanY", panY);
        setFloat("cameraZoom", zoom);
    }

    private float referenceYaw(String raw) {
        String value = raw == null ? "front" : raw.trim().toLowerCase();
        switch (value) {
            case "back": return 180f;
            case "profile_left": return -90f;
            case "three_left": return -45f;
            case "profile_right": return 90f;
            case "three_right": return 45f;
            case "front":
            default: return 0f;
        }
    }

    private void disableRendererLivePoseForDeterministicCapture() {
        // The Lab pose driver owns these exact joints during capture. Nulling only this Activity's
        // private renderer handles prevents two frame callbacks from racing on the same spine/head.
        setObjectQuietly("headBone", null);
        setObjectQuietly("neckBone", null);
        setObjectQuietly("spineBone", null);
        setObjectQuietly("spine01Bone", null);
        setObjectQuietly("spine02Bone", null);
    }

    private void setFloat(String name, float value) throws Exception {
        Field field = Celine3DView.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setFloat(celineView, value);
    }

    private void setObjectQuietly(String name, Object value) {
        try {
            Field field = Celine3DView.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(celineView, value);
        } catch (Throwable ignored) {}
    }

    private String value(String key, String fallback) {
        String raw = getIntent() == null ? null : getIntent().getStringExtra(key);
        return raw == null || raw.trim().isEmpty() ? fallback : raw.trim();
    }

    @Override protected void onResume() {
        super.onResume();
        if (celineView != null) celineView.startRendering();
        if (poseDriver != null) poseDriver.start();
    }

    @Override protected void onPause() {
        ui.removeCallbacksAndMessages(null);
        if (celineView != null) {
            try { CelineMorphRuntimeV62.clearDiagnosticWeights(celineView); } catch (Throwable ignored) {}
            celineView.stopRendering();
        }
        if (poseDriver != null) poseDriver.stop();
        super.onPause();
    }
}
