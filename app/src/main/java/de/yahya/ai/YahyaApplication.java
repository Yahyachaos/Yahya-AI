package de.yahya.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/** v39: controlled A/B/C texture diagnostics with neutral renderer settings. */
public final class YahyaApplication extends Application implements Application.ActivityLifecycleCallbacks {
    private static final int IMPORT_BUTTON_ID = 0x71A11;
    private static final int MODE_BUTTON_ID = 0x71A12;

    @Override public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
    }

    /** Runs before MainActivity creates CelineAvatarController / Celine3DView. */
    @Override public void onActivityPreCreated(Activity activity, Bundle state) {
        if (!(activity instanceof MainActivity)) return;
        String prepared = CelineTexturePipelineV39.prepareWorkingModel(activity);
        Celine3DDiagnostics.record(activity, "V39-100", "A/B/C-Pipeline vor Activity vorbereitet", prepared);
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) {
        if (!(activity instanceof MainActivity)) return;
        Celine3DDiagnostics.record(activity, "V39-106", "v38-Rescue-Hacks deaktiviert",
                "kein Emissive-Fill · kein konkurrierendes GLB-Rewrite · neutrales v36-Licht");
    }

    @Override public void onActivityResumed(Activity activity) {
        if (!(activity instanceof MainActivity)) return;
        View decor = activity.getWindow().getDecorView();
        decor.post(() -> ensureControls(activity));

        // No v37 Celine3DVisualTuning here: keep the neutral v36 exposure/light so textures cannot
        // be washed out by the former keyLight=85000 / bright-camera debug settings.
        decor.postDelayed(() -> CelineFallbackAnimator.ensure(decor), 450L);

        // The actual material inspection / explicit C binding happens only after gltfio created the
        // real Celine3DView. Re-run a few times to survive Surface recreation and dialogs.
        decor.postDelayed(() -> applyTexturePass(activity, decor), 900L);
        decor.postDelayed(() -> applyTexturePass(activity, decor), 1900L);
        decor.postDelayed(() -> applyTexturePass(activity, decor), 4200L);
        decor.postDelayed(() -> applyTexturePass(activity, decor), 7600L);
    }

    private void applyTexturePass(Activity activity, View decor) {
        CelineTexturePipelineV39.applyRuntime(decor);
        updateModeButton(activity);
    }

    private void ensureControls(Activity activity) {
        FrameLayout content = activity.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) return;
        View child = content.getChildAt(0);
        if (!(child instanceof LinearLayout)) return;
        LinearLayout root = (LinearLayout) child;

        int d = Math.max(1, (int) activity.getResources().getDisplayMetrics().density);
        int index = Math.min(3, root.getChildCount());

        if (content.findViewById(IMPORT_BUTTON_ID) == null) {
            Button importButton = new Button(activity);
            importButton.setId(IMPORT_BUTTON_ID);
            importButton.setText("⬆  3D-Avatar importieren");
            importButton.setAllCaps(false);
            importButton.setTextSize(15f);
            importButton.setOnClickListener(v -> activity.startActivity(new Intent(activity, AvatarPickerActivity.class)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, Math.max(4, 5 * d));
            root.addView(importButton, index, lp);
            index++;
        }

        if (content.findViewById(MODE_BUTTON_ID) == null) {
            Button modeButton = new Button(activity);
            modeButton.setId(MODE_BUTTON_ID);
            modeButton.setAllCaps(false);
            modeButton.setTextSize(13f);
            modeButton.setOnClickListener(v -> showModePicker(activity));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, Math.max(6, 8 * d));
            root.addView(modeButton, index, lp);
        }
        updateModeButton(activity);
    }

    private void updateModeButton(Activity activity) {
        View v = activity.findViewById(MODE_BUTTON_ID);
        if (v instanceof Button) {
            ((Button) v).setText("3D-TEST  " + CelineTexturePipelineV39.statusLine(activity) + "  ▾");
        }
    }

    private void showModePicker(Activity activity) {
        final CelineTexturePipelineV39.Mode[] modes = {
                CelineTexturePipelineV39.Mode.A_ORIGINAL,
                CelineTexturePipelineV39.Mode.B_CLEAN_PBR,
                CelineTexturePipelineV39.Mode.C_FORCE_TEXTURE
        };
        String[] items = {
                "A · ORIGINAL – Meshy-Material + automatische Textur",
                "B · CLEAN PBR – emissive aus, metallic 0, automatische Textur",
                "C · FORCE TEXTURE – Clean PBR + PNG explizit auf GPU/baseColorMap"
        };
        new AlertDialog.Builder(activity)
                .setTitle("Celine 3D · Textur-Test")
                .setMessage("Modus wählen. Die App startet danach neu. Für einen wirklich sauberen Test bitte die originale Meshy-ZIP einmal über „3D-Avatar importieren“ importieren.")
                .setItems(items, (dialog, which) -> {
                    Celine3DDiagnostics.clear(activity);
                    CelineTexturePipelineV39.setMode(activity, modes[Math.max(0, Math.min(which, modes.length - 1))]);
                    Intent restart = Intent.makeRestartActivityTask(new ComponentName(activity, MainActivity.class));
                    activity.startActivity(restart);
                })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityPaused(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
    @Override public void onActivityDestroyed(Activity activity) {}
}
