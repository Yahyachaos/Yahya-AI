package de.yahya.ai;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/** v41: keeps FORCE-C texture binding and tests a controlled softer lighting setup. */
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

        // v40 proved explicit GPU/baseColorMap binding works. Keep that exact path unchanged.
        CelineTexturePipelineV39.setMode(activity, CelineTexturePipelineV39.Mode.C_FORCE_TEXTURE);
        String prepared = CelineTexturePipelineV39.prepareWorkingModel(activity);
        Celine3DDiagnostics.record(activity, "V41-001", "FORCE-C beibehalten",
                "Texturpfad unverändert · " + prepared);
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) {
        if (!(activity instanceof MainActivity)) return;
        Celine3DDiagnostics.record(activity, "V41-002", "v41 Licht-Test vorbereitet",
                "keine GLB-Änderung · keine neue Textur-Reparatur · nur Lichtmenge wird reduziert");
    }

    @Override public void onActivityResumed(Activity activity) {
        if (!(activity instanceof MainActivity)) return;
        View decor = activity.getWindow().getDecorView();
        decor.post(() -> ensureControls(activity));

        decor.postDelayed(() -> CelineFallbackAnimator.ensure(decor), 450L);

        // Explicit C binding plus a controlled softer scene light. Re-apply after Surface/dialog
        // recreation without touching the model or texture source.
        decor.postDelayed(() -> applyTexturePass(activity, decor), 900L);
        decor.postDelayed(() -> applyTexturePass(activity, decor), 1900L);
        decor.postDelayed(() -> applyTexturePass(activity, decor), 4200L);
        decor.postDelayed(() -> applyTexturePass(activity, decor), 7600L);
    }

    private void applyTexturePass(Activity activity, View decor) {
        CelineTexturePipelineV39.applyRuntime(decor);
        CelineSoftLightV41.ensure(decor);
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
            modeButton.setEnabled(false);
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
            ((Button) v).setText("3D-TEST C · FORCE TEXTURE · SOFT LIGHT · " + CelineTexturePipelineV39.statusLine(activity));
        }
    }

    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityPaused(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
    @Override public void onActivityDestroyed(Activity activity) {}
}
