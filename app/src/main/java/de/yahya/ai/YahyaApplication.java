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

/** v43: true KHR_materials_unlit diagnostic while keeping the proven FORCE-C texture path. */
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

        // Step 1: recreate exactly the same controlled FORCE-C working model used by v42.
        CelineTexturePipelineV39.setMode(activity, CelineTexturePipelineV39.Mode.C_FORCE_TEXTURE);
        String prepared = CelineTexturePipelineV39.prepareWorkingModel(activity);
        Celine3DDiagnostics.record(activity, "V43-001", "FORCE-C Quelle neu vorbereitet",
                "Mesh/Rig/PNG unverändert · " + prepared);

        // Step 2: change only the disposable working copy's shading model to real glTF UNLIT.
        String unlit = CelineTrueUnlitProbeV43.prepareWorkingModel(activity);
        Celine3DDiagnostics.record(activity, "V43-002", "TRUE-UNLIT vor Filament aktiviert",
                unlit);
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) {
        if (!(activity instanceof MainActivity)) return;
        Celine3DDiagnostics.record(activity, "V43-003", "v43 Beweistest vorbereitet",
                "gleiche FORCE-C GPU-PNG + gleiche UVs · nur Shading=LIT→KHR_materials_unlit · v42 emissive override AUS");
    }

    @Override public void onActivityResumed(Activity activity) {
        if (!(activity instanceof MainActivity)) return;
        View decor = activity.getWindow().getDecorView();
        decor.post(() -> ensureControls(activity));
        decor.postDelayed(() -> CelineFallbackAnimator.ensure(decor), 450L);

        // Repeat the proven v39 baseColor bind several times because the Surface can be recreated.
        // v42's emissive override is intentionally NOT called. The actual material itself is now UNLIT.
        decor.postDelayed(() -> applyTexturePass(activity, decor), 900L);
        decor.postDelayed(() -> applyTexturePass(activity, decor), 1900L);
        decor.postDelayed(() -> applyTexturePass(activity, decor), 4200L);
        decor.postDelayed(() -> applyTexturePass(activity, decor), 7600L);
    }

    private void applyTexturePass(Activity activity, View decor) {
        // Same exact forced 4096x4096 texture path as v42.
        CelineTexturePipelineV39.applyRuntime(decor);

        // Keep v41's environment unchanged for an apples-to-apples comparison. In true UNLIT it
        // should have no visible influence, which is itself part of the diagnostic.
        CelineSoftLightV41.ensure(decor);

        // Audit only; do not rebind through emissive or introduce another shader/material hack.
        CelineTrueUnlitProbeV43.auditRuntime(decor);
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
            ((Button) v).setText("3D-TEST V43 · TRUE UNLIT · FORCE-C BaseColor");
        }
    }

    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityPaused(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
    @Override public void onActivityDestroyed(Activity activity) {}
}
