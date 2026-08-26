package de.yahya.ai;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
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
    private Celine3DView celineView;
    private TextView status;

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
                button("Blick ←", v -> look(-1f, 0f, "Blick links")),
                button("Blick ↑", v -> look(0f, -1f, "Blick hoch")),
                button("Blick •", v -> { if (celineView != null) celineView.releaseLook(); setStatus("Blick automatisch / Mitte"); }),
                button("Blick ↓", v -> look(0f, 1f, "Blick runter")),
                button("Blick →", v -> look(1f, 0f, "Blick rechts"))
        ));

        panel.addView(row(
                button("Ganzkörper", v -> camera(0f, -0.15f, 0.68f, "Kamera: Ganzkörper")),
                button("Oberkörper", v -> camera(0f, 0.05f, 1.05f, "Kamera: Oberkörper")),
                button("Gesicht nah", v -> camera(0f, 0.22f, 1.75f, "Kamera: Gesicht-Nahaufnahme")),
                button("Reset", v -> resetCamera())
        ));

        panel.addView(row(
                button("Sprechenergie 0", v -> speech(0f)),
                button("Sprechenergie 50", v -> speech(0.5f)),
                button("Sprechenergie 100", v -> speech(1f)),
                button("HOME", v -> finish())
        ));

        TextView hint = new TextView(this);
        hint.setText("Direkter Test des aktuellen Branch-Renderers. Pinch verändert die echte Kameradistanz; Doppeltipp setzt zurück. Die nächsten v79-Schritte hängen Blink, Sitzen, Laufen, Arme/Hände und echte Orbit-/Profil-Presets direkt an diesen Harness.");
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
        if (celineView != null) celineView.setAvatarState(state);
        setStatus(label);
    }

    private void look(float x, float y, String label) {
        if (celineView != null) celineView.setLook(x, y);
        setStatus(label);
    }

    private void speech(float level) {
        if (celineView != null) {
            celineView.setAvatarState(CelineAvatarController.State.SPEAKING);
            celineView.setSpeechEnergy(level);
        }
        setStatus("SPEAKING · Energie " + Math.round(level * 100f) + "%");
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

    private void resetCamera() {
        if (celineView == null) return;
        try {
            Method reset = Celine3DView.class.getDeclaredMethod("resetCameraSearch");
            reset.setAccessible(true);
            reset.invoke(celineView);
            celineView.releaseLook();
            celineView.setSpeechEnergy(0f);
            celineView.setAvatarState(CelineAvatarController.State.IDLE);
            setStatus("Neutral / Kamera reset");
        } catch (Throwable error) {
            setStatus("Reset fehlgeschlagen: " + safe(error));
        }
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
    }

    @Override protected void onPause() {
        if (celineView != null) celineView.stopRendering();
        super.onPause();
    }
}
