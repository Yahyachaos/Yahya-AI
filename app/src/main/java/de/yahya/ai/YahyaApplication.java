package de.yahya.ai;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/** Adds the in-app avatar import control plus v37 reveal and v38 texture rescue passes. */
public final class YahyaApplication extends Application implements Application.ActivityLifecycleCallbacks {
    private static final int IMPORT_BUTTON_ID = 0x71A11;

    @Override public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) {
        if (!(activity instanceof MainActivity)) return;
        // MainActivity has now built CelineAvatarController (including the older material cleanup),
        // but its posted 3D start has not yet run. This is the exact safe window to restore the
        // embedded Meshy atlas as both BaseColor and a low-strength Emissive texture before gltfio
        // reads celine.glb.
        boolean changed = CelineTextureRescue.apply(activity);
        Celine3DDiagnostics.record(activity,
                changed ? "V38-110" : "V38-111",
                changed ? "Textur-Rettung vor 3D-Laden angewendet" : "Textur-Rettung bereits vorhanden",
                Celine3DDiagnostics.modelSnapshot(activity));
    }

    @Override public void onActivityResumed(Activity activity) {
        if (!(activity instanceof MainActivity)) return;
        View decor = activity.getWindow().getDecorView();
        decor.post(() -> ensureImportButton(activity));

        // Keep a small early 2D motion only while Filament is still starting. Once v36 has actually
        // switched the avatar to 3D, v37 removes the hidden fallback and performs a real-bone reveal.
        decor.postDelayed(() -> CelineFallbackAnimator.ensure(decor), 450L);
        decor.postDelayed(() -> Celine3DForceReveal.ensure(decor), 1400L);
        decor.postDelayed(() -> Celine3DForceReveal.ensure(decor), 4200L);
        decor.postDelayed(() -> Celine3DForceReveal.ensure(decor), 8000L);

        // v34's runtime PBR pass intentionally zeroed emissive. v38 restores the low-strength
        // emissive atlas AFTER the real Celine3DView exists. Re-apply a few times so Samsung
        // Surface recreation/dialog transitions cannot leave the old white/gray material active.
        decor.postDelayed(() -> CelineRuntimeTextureRescue.ensure(decor), 1100L);
        decor.postDelayed(() -> CelineRuntimeTextureRescue.ensure(decor), 2200L);
        decor.postDelayed(() -> CelineRuntimeTextureRescue.ensure(decor), 4800L);
        decor.postDelayed(() -> CelineRuntimeTextureRescue.ensure(decor), 8500L);
    }

    private void ensureImportButton(Activity activity) {
        FrameLayout content = activity.findViewById(android.R.id.content);
        if (content == null || content.findViewById(IMPORT_BUTTON_ID) != null || content.getChildCount() == 0) return;
        View child = content.getChildAt(0);
        if (!(child instanceof LinearLayout)) return;
        LinearLayout root = (LinearLayout) child;

        Button button = new Button(activity);
        button.setId(IMPORT_BUTTON_ID);
        button.setText("⬆  3D-Avatar importieren");
        button.setAllCaps(false);
        button.setTextSize(15f);
        button.setOnClickListener(v -> activity.startActivity(new android.content.Intent(activity, AvatarPickerActivity.class)));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        int d = (int) activity.getResources().getDisplayMetrics().density;
        lp.setMargins(0, 0, 0, Math.max(6, 8 * d));

        int index = Math.min(3, root.getChildCount());
        root.addView(button, index, lp);
    }

    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityPaused(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
    @Override public void onActivityDestroyed(Activity activity) {}
}
