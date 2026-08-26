package de.yahya.ai;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * v79 branch-live visual harness for inspecting the exact Celine renderer/asset on the current branch.
 * This is intentionally lightweight: it is useful during iteration and does not wait for a release.
 */
public final class CelineAvatarLabActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Celine3DView celineView;
    private CelineAvatarLabPoseDriverV79 poseDriver;
    private TextView status;
    private int faceSequence;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(10, 12, 18));
        getWindow().setNavigationBarColor(Color.rgb(10, 12, 18));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(10, 12, 18));

        try {
            celineView = new Celine3DView(this, true);
            root.addView(celineView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            try {
                poseDriver = new CelineAvatarLabPoseDriverV79(celineView);
            } catch (Throwable poseError) {
                Celine3DDiagnostics.error(this, "V79-198", "Avatar Lab Pose-Driver nicht verfügbar", poseError);
            }
        } catch (Throwable error) {
            TextView failed = new TextView(this);
            failed.setTextColor(Color.WHITE);
            failed.setTextSize(17f);
            failed.setGravity(Gravity.CENTER);
            failed.setText("Celine Avatar Lab konnte den aktuellen Branch-Avatar nicht laden.\n\n" + safe(error));
            root.addView(failed, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        }

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(8), dp(10), dp(10));
        panel.setBackgroundColor(0xD9121620);

        TextView title = new TextView(this);
        title.setText("Celine Avatar Lab · v79 branch-live");
        title.setTextColor(Color.WHITE);
        title.setTextSize(17f);
        panel.addView(title);

        status = new TextView(this);
        status.setText("Aktueller Branch-Avatar · Neutral / IDLE");
        status.setTextColor(0xFFB8C4D8);
        status.setTextSize(12f);
        status.setPadding(0, dp(2), 0, dp(5));
        panel.addView(status);

        panel.addView(row(
                button("Neutral", v -> state(CelineAvatarController.State.IDLE, "Neutral / IDLE")),
                button("Zuhören", v -> state(CelineAvatarController.State.LISTENING, "LISTENING")),
                button("Denken", v -> state(CelineAvatarController.State.THINKING, "THINKING")),
                button("Sprechen", v -> { state(CelineAvatarController.State.SPEAKING, "SPEAKING"); if (celineView != null) celineView.setSpeechEnergy(0.75f); })
        ));

        panel.addView(row(
                button("Live-Körper", v -> pose(CelineAvatarLabPoseDriverV79.Mode.LIVE, "Körper: Live")),
                button("Stehen", v -> pose(CelineAvatarLabPoseDriverV79.Mode.STAND, "Körper: Stehen")),
                button("Gewicht links", v -> pose(CelineAvatarLabPoseDriverV79.Mode.WEIGHT_LEFT, "Körper: Gewicht links")),
                button("Gewicht rechts", v -> pose(CelineAvatarLabPoseDriverV79.Mode.WEIGHT_RIGHT, "Körper: Gewicht rechts")),
                button("Sitzen", v -> pose(CelineAvatarLabPoseDriverV79.Mode.SEATED, "Körper: Sitzen")),
                button("Vorbeugen", v -> pose(CelineAvatarLabPoseDriverV79.Mode.BEND, "Körper: Vorbeugen")),
                button("Laufen", v -> pose(CelineAvatarLabPoseDriverV79.Mode.WALK, "Körper: Lauf-in-place")),
                button("Arme/Hände", v -> pose(CelineAvatarLabPoseDriverV79.Mode.ARMS, "Körper: Arm/Hand-Diagnose"))
        ));

        panel.addView(row(
                button("Kopf ←", v -> head(0f, -26f, 0f, "Kopf links")),
                button("Kopf →", v -> head(0f, 26f, 0f, "Kopf rechts")),
                button("Kopf ↑", v -> head(-16f, 0f, 0f, "Kopf hoch")),
                button("Kopf ↓", v -> head(16f, 0f, 0f, "Kopf runter")),
                button("Neigung ←", v -> head(0f, 0f, -14f, "Kopfneigung links")),
                button("Neigung →", v -> head(0f, 0f, 14f, "Kopfneigung rechts")),
                button("Kopf neutral", v -> clearHead())
        ));

        panel.addView(row(
                button("Blick ←", v -> look(-1f, 0f, "Blick links")),
                button("Blick ↑", v -> look(0f, -1f, "Blick hoch")),
                button("Blick •", v -> { if (celineView != null) celineView.releaseLook(); setStatus("Blick automatisch / Mitte"); }),
                button("Blick ↓", v -> look(0f, 1f, "Blick runter")),
                button("Blick →", v -> look(1f, 0f, "Blick rechts"))
        ));

        panel.addView(row(
                button("Blink", v -> blink(false)),
                button("Blink langsam", v -> blink(true)),
                button("Gesicht neutral", v -> clearFace("Gesicht neutral")),
                button("Lächeln", v -> morph(CelineFacialMotionPlanner.SMILE, 0.32f, "Lächeln")),
                button("Nachdenklich", v -> morph(CelineFacialMotionPlanner.THOUGHTFUL, 0.30f, "Nachdenklich")),
                button("Überrascht", v -> morph(CelineFacialMotionPlanner.SURPRISED, 0.32f, "Überrascht")),
                button("Kuss", v -> kiss())
        ));

        panel.addView(row(
                button("Viseme O", v -> faceCombo("Viseme O", new int[]{CelineFacialMotionPlanner.JAW_OPEN, CelineFacialMotionPlanner.ROUNDED_VOWEL}, new float[]{0.34f, 0.56f})),
                button("Viseme breit", v -> faceCombo("Viseme breit", new int[]{CelineFacialMotionPlanner.JAW_OPEN, CelineFacialMotionPlanner.SPREAD_VOWEL}, new float[]{0.32f, 0.55f})),
                button("B/P/M", v -> morph(CelineFacialMotionPlanner.BILABIAL_PRESS, 0.70f, "Viseme B/P/M")),
                button("F/V", v -> faceCombo("Viseme F/V", new int[]{CelineFacialMotionPlanner.LABIODENTAL, CelineFacialMotionPlanner.JAW_OPEN}, new float[]{0.67f, 0.16f}))
        ));

        panel.addView(row(
                button("Ganzkörper", v -> camera(0f, -0.15f, 0.68f, "Kamera: Ganzkörper")),
                button("Oberkörper", v -> camera(0f, 0.05f, 1.05f, "Kamera: Oberkörper")),
                button("Gesicht nah", v -> camera(0f, 0.22f, 1.75f, "Kamera: Gesicht-Nahaufnahme")),
                button("Kamera Reset", v -> resetCamera())
        ));

        panel.addView(row(
                button("Front", v -> orbit(0f, "Front")),
                button("3/4 links", v -> orbit(-42f, "3/4 links")),
                button("Profil links", v -> orbit(-90f, "Profil links")),
                button("Rücken", v -> orbit(180f, "Rücken")),
                button("Profil rechts", v -> orbit(90f, "Profil rechts")),
                button("3/4 rechts", v -> orbit(42f, "3/4 rechts"))
        ));

        panel.addView(row(
                button("Sprechenergie 0", v -> speech(0f)),
                button("Sprechenergie 50", v -> speech(0.5f)),
                button("Sprechenergie 100", v -> speech(1f)),
                button("Alles neutral", v -> resetAll()),
                button("HOME", v -> finish())
        ));

        TextView hint = new TextView(this);
        String rig = poseDriver == null ? "Pose-Rig nicht gebunden" : poseDriver.capabilitySummary();
        hint.setText("Branch-live Test des echten Renderers/Rigs. Blink/Mimik/Viseme laufen über den v76 Final-Geometry-Morphpfad. Körpermodi schreiben ausschließlich im Lab deterministische Rig-Posen. Orbit bewegt die Kamera um den festen Avatar; Dolly verändert die echte Kameradistanz, nie die Modellskalierung. Rig: " + rig);
        hint.setTextColor(0xFF8F9CAF);
        hint.setTextSize(11f);
        hint.setPadding(0, dp(5), 0, 0);
        panel.addView(hint);

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        root.addView(panel, panelParams);
        setContentView(root);
    }

    private LinearLayout row(Button... buttons) {
        LinearLayout raw = new LinearLayout(this);
        raw.setOrientation(LinearLayout.HORIZONTAL);
        for (Button button : buttons) raw.addView(button);
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.addView(raw);
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.addView(scroller, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return shell;
    }

    private Button button(String label, View.OnClickListener click) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(11f);
        button.setAllCaps(false);
        button.setOnClickListener(click);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(42));
        p.setMargins(0, dp(2), dp(4), dp(2));
        button.setLayoutParams(p);
        return button;
    }

    private void state(CelineAvatarController.State state, String label) {
        clearFace(null);
        if (celineView != null) celineView.setAvatarState(state);
        setStatus(label);
    }

    private void pose(CelineAvatarLabPoseDriverV79.Mode mode, String label) {
        if (poseDriver == null) {
            setStatus("Pose-Driver nicht verfügbar");
            return;
        }
        poseDriver.setMode(mode);
        setStatus(label);
    }

    private void head(float pitch, float yaw, float roll, String label) {
        if (poseDriver == null) {
            setStatus("Kopf-Driver nicht verfügbar");
            return;
        }
        poseDriver.setHead(pitch, yaw, roll);
        setStatus(label);
    }

    private void clearHead() {
        if (poseDriver != null) poseDriver.clearHead();
        setStatus("Kopf neutral");
    }

    private void look(float x, float y, String label) {
        if (celineView != null) celineView.setLook(x, y);
        setStatus(label);
    }

    private void speech(float level) {
        clearFace(null);
        if (celineView != null) {
            celineView.setAvatarState(CelineAvatarController.State.SPEAKING);
            celineView.setSpeechEnergy(level);
        }
        setStatus("SPEAKING · Energie " + Math.round(level * 100f) + "%");
    }

    private void blink(boolean slow) {
        if (celineView == null) return;
        final int sequence = ++faceSequence;
        if (!slow) {
            float[] weights = new float[CelineFacialMotionPlanner.TARGET_COUNT];
            weights[CelineFacialMotionPlanner.BLINK_BOTH] = 0.94f;
            weights[CelineFacialMotionPlanner.BLINK_LEFT] = 0.04f;
            weights[CelineFacialMotionPlanner.BLINK_RIGHT] = 0.02f;
            CelineMorphRuntimeV62.setDiagnosticWeights(celineView, weights);
            setStatus("Blink · Augen geschlossen");
            ui.postDelayed(() -> {
                if (sequence != faceSequence || celineView == null) return;
                CelineMorphRuntimeV62.clearDiagnosticWeights(celineView);
                setStatus("Blink · wieder offen");
            }, 190L);
            return;
        }

        setStatus("Blink langsam · 0%");
        slowBlinkStage(sequence, 0L, 0.22f, "20%");
        slowBlinkStage(sequence, 180L, 0.48f, "50%");
        slowBlinkStage(sequence, 360L, 0.76f, "80%");
        slowBlinkStage(sequence, 540L, 0.94f, "100%");
        slowBlinkStage(sequence, 760L, 0.70f, "75%");
        slowBlinkStage(sequence, 940L, 0.38f, "40%");
        ui.postDelayed(() -> {
            if (sequence != faceSequence || celineView == null) return;
            CelineMorphRuntimeV62.clearDiagnosticWeights(celineView);
            setStatus("Blink langsam · offen");
        }, 1140L);
    }

    private void slowBlinkStage(int sequence, long delay, float amount, String label) {
        ui.postDelayed(() -> {
            if (sequence != faceSequence || celineView == null) return;
            float[] weights = new float[CelineFacialMotionPlanner.TARGET_COUNT];
            weights[CelineFacialMotionPlanner.BLINK_BOTH] = amount;
            weights[CelineFacialMotionPlanner.BLINK_LEFT] = amount * 0.045f;
            weights[CelineFacialMotionPlanner.BLINK_RIGHT] = amount * 0.025f;
            CelineMorphRuntimeV62.setDiagnosticWeights(celineView, weights);
            setStatus("Blink langsam · " + label);
        }, delay);
    }

    private void morph(int target, float value, String label) {
        faceSequence++;
        if (celineView != null) CelineMorphRuntimeV62.setDiagnosticTarget(celineView, target, value);
        setStatus(label);
    }

    private void faceCombo(String label, int[] targets, float[] values) {
        faceSequence++;
        if (celineView == null) return;
        float[] weights = new float[CelineFacialMotionPlanner.TARGET_COUNT];
        int count = Math.min(targets.length, values.length);
        for (int i = 0; i < count; i++) {
            int target = targets[i];
            if (target >= 0 && target < weights.length) weights[target] = values[i];
        }
        CelineMorphRuntimeV62.setDiagnosticWeights(celineView, weights);
        setStatus(label);
    }

    private void kiss() {
        faceCombo("Kuss / Pucker-Test", new int[]{
                CelineFacialMotionPlanner.ROUNDED_VOWEL,
                CelineFacialMotionPlanner.BILABIAL_PRESS,
                CelineFacialMotionPlanner.JAW_OPEN
        }, new float[]{0.58f, 0.34f, 0.08f});
    }

    private void clearFace(String label) {
        faceSequence++;
        if (celineView != null) CelineMorphRuntimeV62.clearDiagnosticWeights(celineView);
        if (label != null) setStatus(label);
    }

    /**
     * Temporary v79 harness bridge into the current production renderer's bounded camera fields.
     * Keeps iteration changes isolated from HOME/CALL until the real camera API replaces it.
     */
    private void camera(float panX, float panY, float zoom, String label) {
        if (celineView == null) return;
        try {
            setFloat("cameraPanX", panX);
            setFloat("cameraPanY", panY);
            setFloat("cameraZoom", zoom);
            setStatus(label + " · Dolly " + zoom);
        } catch (Throwable error) {
            setStatus("Kamera-Preset fehlgeschlagen: " + safe(error));
        }
    }

    private void orbit(float yaw, String label) {
        if (celineView == null) return;
        try {
            celineView.v75SetReferenceYaw(yaw);
            setFloat("cameraPanX", 0f);
            setFloat("cameraPanY", 0f);
            setStatus("Kamera-Orbit: " + label + " · " + Math.round(yaw) + "°");
        } catch (Throwable error) {
            setStatus("Orbit fehlgeschlagen: " + safe(error));
        }
    }

    private void resetCamera() {
        if (celineView == null) return;
        try {
            Method reset = Celine3DView.class.getDeclaredMethod("resetCameraSearch");
            reset.setAccessible(true);
            reset.invoke(celineView);
            celineView.v75SetReferenceYaw(0f);
            setStatus("Kamera reset");
        } catch (Throwable error) {
            setStatus("Reset fehlgeschlagen: " + safe(error));
        }
    }

    private void resetAll() {
        faceSequence++;
        if (celineView != null) {
            CelineMorphRuntimeV62.clearDiagnosticWeights(celineView);
            celineView.v75SetReferenceYaw(0f);
            celineView.releaseLook();
            celineView.setSpeechEnergy(0f);
            celineView.setAvatarState(CelineAvatarController.State.IDLE);
        }
        if (poseDriver != null) {
            poseDriver.clearHead();
            poseDriver.setMode(CelineAvatarLabPoseDriverV79.Mode.LIVE);
        }
        try {
            if (celineView != null) {
                Method reset = Celine3DView.class.getDeclaredMethod("resetCameraSearch");
                reset.setAccessible(true);
                reset.invoke(celineView);
            }
        } catch (Throwable ignored) {}
        setStatus("Alles neutral / Live");
    }

    private void setFloat(String name, float value) throws Exception {
        Field field = Celine3DView.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setFloat(celineView, value);
    }

    private void setStatus(String value) {
        if (status != null) status.setText("Aktueller Branch-Avatar · " + value);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String safe(Throwable error) {
        if (error == null) return "Unbekannter Fehler";
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() : message;
    }

    @Override protected void onResume() {
        super.onResume();
        if (celineView != null) celineView.startRendering();
        if (poseDriver != null) poseDriver.start();
    }

    @Override protected void onPause() {
        faceSequence++;
        if (celineView != null) {
            CelineMorphRuntimeV62.clearDiagnosticWeights(celineView);
            celineView.stopRendering();
        }
        if (poseDriver != null) {
            poseDriver.clearHead();
            poseDriver.setMode(CelineAvatarLabPoseDriverV79.Mode.LIVE);
            poseDriver.stop();
        }
        super.onPause();
    }
}
