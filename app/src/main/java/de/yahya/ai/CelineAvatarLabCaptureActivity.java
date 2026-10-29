package de.yahya.ai;

import android.app.Activity;
import android.content.Intent;
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
 *
 * CI state changes deliberately reuse this exact Activity instance through FLAG_ACTIVITY_SINGLE_TOP.
 * Recreating the SurfaceView/Filament renderer between every screenshot made a compositor-visible
 * warm frame disappear again before the first real evidence frame. onNewIntent therefore changes
 * pose/camera/orbit/face in-place while preserving the renderer and its Surface/SwapChain.
 */
public final class CelineAvatarLabCaptureActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Celine3DView celineView;
    private CelineAvatarLabPoseDriverV79 poseDriver;
    private CelineAvatarLabSceneV79 sceneDriver;

    /**
     * Debug-only Block-8 bridge. It mirrors CelineAvatarController's production bus forwarding so
     * deterministic PCM fixtures can exercise the real v77 stabilizer + one guarded face planner
     * without requiring the ~300 MB local TTS model in CI.
     */
    private final SpeechAudioBus.Listener block8SpeechBridge = new SpeechAudioBus.Listener() {
        @Override public void onSpeechAudioLevel(float level) {
            if (celineView != null) celineView.setSpeechEnergy(level);
        }

        @Override public void onSpeechViseme(SpeechVisemeAnalyzer.Cue cue) {
            if (celineView != null) celineView.setViseme(cue);
        }
    };

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
            SpeechAudioBus.setListener(block8SpeechBridge);
            // Meshy's pre-skinning bounds remain tiny after the production v61 root repair. A
            // face-targeted dolly can therefore cull the actually visible skinned body. Disable
            // culling only inside the diagnostic Lab; HOME/CALL renderer policy is untouched.
            CelineAvatarLabCameraGuardV79.disableStaleBoundsCulling(celineView);
            root.addView(celineView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));

            // The scene adapter installs the production room behind the transparent Filament
            // surface, but keeps it hidden for ordinary Lab captures. Only the explicit `call`
            // preset reveals the exact production chair/backdrop and applies the 50 mm CALL lens.
            sceneDriver = CelineAvatarLabSceneV79.install(this, root, celineView);
            setContentView(root);

            // v79 orbit moves the actual Filament camera around a fixed subject target. The model
            // root/reference yaw stays untouched so evidence cannot fake an orbit by rotating Celine.
            celineView.v75SetReferenceYaw(0f);
            celineView.v79SetDiagnosticCameraOrbit(referenceYaw(value(getIntent(), "ci_orbit", "front")));
            poseDriver = new CelineAvatarLabPoseDriverV79(celineView);
            applyRequestedState(getIntent(), true);
        } catch (Throwable error) {
            Celine3DDiagnostics.error(this, "V79-599", "Avatar Lab Capture Initialisierung FEHLER", error);
            finish();
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (celineView == null || poseDriver == null) return;
        try {
            applyRequestedState(intent, false);
        } catch (Throwable error) {
            Celine3DDiagnostics.error(this, "V79-598", "Avatar Lab In-Place-State FEHLER", error);
        }
    }

    private void applyRequestedState(Intent intent, boolean initial) throws Exception {
        String pose = value(intent, "ci_pose", "stand");
        String camera = value(intent, "ci_camera", "full");
        String orbit = value(intent, "ci_orbit", "front");
        String face = value(intent, "ci_face", "neutral");

        // Do not rebuild, translate or rotate the normalized model root when changing diagnostic
        // views. Orbit belongs exclusively to the camera around the fixed Celine target.
        celineView.v75SetReferenceYaw(0f);
        celineView.v79SetDiagnosticCameraOrbit(referenceYaw(orbit));
        poseDriver.setMode(poseMode(pose));
        poseDriver.start();
        applyCamera(camera);
        if (sceneDriver != null) sceneDriver.apply(pose, camera);
        celineView.setAvatarState(CelineAvatarController.State.LISTENING);
        celineView.setSpeechEnergy(0f);
        celineView.releaseLook();

        // Morph runtime is asynchronous on the first construction only. Later CI state changes are
        // applied to the already-bound runtime after a short compositor settle interval.
        ui.removeCallbacksAndMessages(null);
        if (initial) {
            ui.postDelayed(this::applyFace, 650L);
        } else {
            ui.postDelayed(this::applyFace, 120L);
        }

        Celine3DDiagnostics.record(this, initial ? "V79-510" : "V79-511",
                initial ? "Avatar Lab Capture bereit" : "Avatar Lab Capture State in-place geändert",
                "pose=" + pose + " camera=" + camera + " orbit=" + orbit + " face=" + face);
    }

    private void applyFace() {
        if (celineView == null) return;
        String face = value(getIntent(), "ci_face", "neutral");
        if ("blink85".equals(face)) {
            float[] weights = new float[CelineFacialMotionPlanner.TARGET_COUNT];
            weights[CelineFacialMotionPlanner.BLINK_BOTH] = 0.85f;
            weights[CelineFacialMotionPlanner.BLINK_LEFT] = 0.035f;
            weights[CelineFacialMotionPlanner.BLINK_RIGHT] = 0.020f;
            CelineMorphRuntimeV62.setDiagnosticWeights(celineView, weights);
            Celine3DDiagnostics.record(this, "V79-512", "Avatar Lab Diagnosegesicht gesetzt",
                    "face=blink85 both=0.85");
        } else if ("blink100".equals(face)) {
            float[] weights = new float[CelineFacialMotionPlanner.TARGET_COUNT];
            weights[CelineFacialMotionPlanner.BLINK_BOTH] = 0.96f;
            weights[CelineFacialMotionPlanner.BLINK_LEFT] = 0.040f;
            weights[CelineFacialMotionPlanner.BLINK_RIGHT] = 0.025f;
            CelineMorphRuntimeV62.setDiagnosticWeights(celineView, weights);
            Celine3DDiagnostics.record(this, "V79-512", "Avatar Lab Diagnosegesicht gesetzt",
                    "face=blink100 both=0.96");
        } else if (face.startsWith("block8_pcm_")) {
            applyBlock8Pcm(face.substring("block8_pcm_".length()));
        } else if ("block8_silent".equals(face)) {
            CelineMorphRuntimeV62.clearDiagnosticWeights(celineView);
            celineView.setAvatarState(CelineAvatarController.State.IDLE);
            SpeechAudioBus.reset();
            Celine3DDiagnostics.record(this, "V80-821", "Block-8 PCM-Gesicht auf neutral zurückgesetzt",
                    "state=IDLE level=0 cue=CLOSED source=SpeechAudioBus");
        } else {
            CelineMorphRuntimeV62.clearDiagnosticWeights(celineView);
            SpeechAudioBus.reset();
            Celine3DDiagnostics.record(this, "V79-512", "Avatar Lab Diagnosegesicht gesetzt",
                    "face=neutral");
        }
    }

    private void applyBlock8Pcm(String fixture) {
        CelineMorphRuntimeV62.clearDiagnosticWeights(celineView);
        float amplitude;
        float frequencyHz;
        switch (fixture) {
            case "round":
                amplitude = 0.080f;
                frequencyHz = 200f;
                break;
            case "wide":
                amplitude = 0.080f;
                frequencyHz = 6000f;
                break;
            case "labial":
                amplitude = 0.050f;
                frequencyHz = 200f;
                break;
            case "start":
            default:
                amplitude = 0.050f;
                frequencyHz = 3000f;
                fixture = "start";
                break;
        }

        final int sampleRate = 24000;
        final int count = sampleRate / 50;
        float[] pcm = new float[count];
        for (int i = 0; i < count; i++) {
            pcm[i] = amplitude * (float) Math.sin((2.0 * Math.PI * frequencyHz * i) / sampleRate);
        }

        // Feed enough consecutive playback-equivalent frames for v77's non-closure hysteresis to
        // settle exactly as it does at ~50 Hz in LocalNeuralTtsEngine.playBlocking().
        SpeechLipSyncV77 lipSync = new SpeechLipSyncV77();
        SpeechLipSyncV77.Frame frame = null;
        for (int i = 0; i < 3; i++) frame = lipSync.analyze(pcm, 0, pcm.length, sampleRate);
        if (frame == null) return;

        celineView.setAvatarState(CelineAvatarController.State.SPEAKING);
        SpeechAudioBus.publish(frame.level);
        SpeechAudioBus.publishViseme(frame.cue);
        Celine3DDiagnostics.record(this, "V80-820", "Block-8 PCM-Viseme gesetzt",
                "fixture=" + fixture + " shape=" + frame.cue.shape
                        + " level=" + frame.level + " openness=" + frame.cue.openness
                        + " width=" + frame.cue.width + " roundness=" + frame.cue.roundness
                        + " source=SpeechLipSyncV77->SpeechAudioBus owner=CelineProductionPresenceV80");
    }

    private CelineAvatarLabPoseDriverV79.Mode poseMode(String raw) {
        String value = raw == null ? "stand" : raw.trim().toLowerCase();
        switch (value) {
            case "production_home": return CelineAvatarLabPoseDriverV79.Mode.PRODUCTION_HOME;
            case "production_call": return CelineAvatarLabPoseDriverV79.Mode.PRODUCTION_CALL;
            case "layer_base": return CelineAvatarLabPoseDriverV79.Mode.LAYER_BASE;
            case "layer_breathing": return CelineAvatarLabPoseDriverV79.Mode.LAYER_BREATHING_POSTURE;
            case "layer_conversation": return CelineAvatarLabPoseDriverV79.Mode.LAYER_CONVERSATION;
            case "layer_gaze": return CelineAvatarLabPoseDriverV79.Mode.LAYER_GAZE_HEAD;
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
            case "call":
                // Mirror production CALL semantics: Celine3DView stays at its normal centered
                // camera position while the scene adapter applies CelineVideoCallV45's 50 mm lens.
                // No model/root scaling or free avatar translation is used.
                panY = 0f;
                zoom = 1.0f;
                break;
            case "face":
                // The normalized avatar's eyes sit roughly 1.1 world units above its origin.
                // Dolly toward that target instead of enlarging the model root.
                panY = 1.10f;
                zoom = 1.75f;
                break;
            case "upper":
                panY = 0.05f;
                zoom = 1.05f;
                break;
            case "dolly_near":
                // Same fixed target as dolly_far: only the camera radius changes.
                panY = 0.0f;
                zoom = 1.20f;
                break;
            case "dolly_far":
                panY = 0.0f;
                zoom = 0.70f;
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
        if ("dolly_near".equals(value) || "dolly_far".equals(value)) {
            Celine3DDiagnostics.record(this, "V79-541", "Avatar Lab echte Kamera-Dolly gesetzt",
                    "preset=" + value + " zoom=" + zoom + " fixedTarget=0," + panY + ",-4 rootScaleChanged=false");
        }
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

    private void setFloat(String name, float value) throws Exception {
        Field field = Celine3DView.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setFloat(celineView, value);
    }

    private static String value(Intent intent, String key, String fallback) {
        String raw = intent == null ? null : intent.getStringExtra(key);
        return raw == null || raw.trim().isEmpty() ? fallback : raw.trim();
    }

    @Override protected void onResume() {
        super.onResume();
        SpeechAudioBus.setListener(block8SpeechBridge);
        if (celineView != null) celineView.startRendering();
        if (poseDriver != null) poseDriver.start();
    }

    @Override protected void onPause() {
        ui.removeCallbacksAndMessages(null);
        SpeechAudioBus.reset();
        SpeechAudioBus.clearListener(block8SpeechBridge);
        if (celineView != null) {
            try { CelineMorphRuntimeV62.clearDiagnosticWeights(celineView); } catch (Throwable ignored) {}
            celineView.stopRendering();
        }
        if (poseDriver != null) poseDriver.stop();
        super.onPause();
    }
}