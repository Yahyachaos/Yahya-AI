package de.yahya.ai;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;

/**
 * v44 production-style startup: keep the proven v43 TRUE-UNLIT / FORCE-C texture path,
 * remove the old on-screen test controls, and layer the video-chat room/presence on top.
 */
public final class YahyaApplication extends Application implements Application.ActivityLifecycleCallbacks {
    @Override public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
    }

    @Override public void onActivityPreCreated(Activity activity, Bundle state) {
        if (!(activity instanceof MainActivity)) return;

        // Preserve the exact visual path that finally rendered Celine correctly.
        CelineTexturePipelineV39.setMode(activity, CelineTexturePipelineV39.Mode.C_FORCE_TEXTURE);
        String prepared = CelineTexturePipelineV39.prepareWorkingModel(activity);
        Celine3DDiagnostics.record(activity, "V44-001", "Produktionsquelle vorbereitet",
                "FORCE-C · " + prepared);

        String unlit = CelineTrueUnlitProbeV43.prepareWorkingModel(activity);
        Celine3DDiagnostics.record(activity, "V44-002", "TRUE-UNLIT beibehalten", unlit);
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) {
        if (!(activity instanceof MainActivity)) return;
        Celine3DDiagnostics.record(activity, "V44-003", "Videochat-Modus vorbereitet",
                "keine v41/v42 Materialexperimente · TRUE-UNLIT bleibt Referenz");
    }

    @Override public void onActivityResumed(Activity activity) {
        if (!(activity instanceof MainActivity)) return;
        View decor = activity.getWindow().getDecorView();

        // Keep the normal 2D fallback alive only until the 3D candidate is ready.
        decor.postDelayed(() -> CelineFallbackAnimator.ensure(decor), 450L);

        // First pass normally lands after Celine3DView has been created. Repeating a few times is
        // intentional: Samsung/Android may recreate the Surface while the activity settles.
        decor.postDelayed(() -> applyProduction(activity, decor), 850L);
        decor.postDelayed(() -> applyProduction(activity, decor), 1800L);
        decor.postDelayed(() -> applyProduction(activity, decor), 3800L);
    }

    private void applyProduction(Activity activity, View decor) {
        // Proven 4096x4096 baseColor GPU binding from v39.
        CelineTexturePipelineV39.applyRuntime(decor);
        // Audit the true-unlit state; no emissive/PBR rescue is reintroduced.
        CelineTrueUnlitProbeV43.auditRuntime(decor);
        // Presentation only: room, transparent SurfaceView, close camera and bounded movement.
        CelineVideoChatV44.ensure(activity, decor);
    }

    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityPaused(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
    @Override public void onActivityDestroyed(Activity activity) {}
}
