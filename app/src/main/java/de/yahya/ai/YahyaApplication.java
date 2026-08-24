package de.yahya.ai;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;

/**
 * v59 safety rollback after the real-avatar framing regression seen on v58.
 * HOME keeps the proven v54 Head-only owner. CALL is deliberately handed back to the proven v55
 * neck + Head owner while the v56-v58 Hips/shoulder path is quarantined for production models.
 * TRUE-UNLIT/FORCE-C, layout and updater placement remain unchanged.
 */
public final class YahyaApplication extends Application implements Application.ActivityLifecycleCallbacks {
    @Override public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
    }

    @Override public void onActivityPreCreated(Activity activity, Bundle state) {
        if (!(activity instanceof MainActivity)) return;
        CelineTexturePipelineV39.setMode(activity, CelineTexturePipelineV39.Mode.C_FORCE_TEXTURE);
        String prepared = CelineTexturePipelineV39.prepareWorkingModel(activity);
        Celine3DDiagnostics.record(activity, "V49-001", "Produktionsquelle vorbereitet", "FORCE-C · " + prepared);
        String unlit = CelineTrueUnlitProbeV43.prepareWorkingModel(activity);
        Celine3DDiagnostics.record(activity, "V49-002", "TRUE-UNLIT beibehalten", unlit);
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) {
        if (!(activity instanceof MainActivity)) return;
        Celine3DDiagnostics.record(activity, "V59-003", "CALL Skinning Sicherheitsmodus aktiv",
                "HOME Head-only · CALL neck+Head · v56-v58 Hips/Shoulder Produktion quarantiniert");
    }

    @Override public void onActivityResumed(Activity activity) {
        if (!(activity instanceof MainActivity)) return;
        View decor = activity.getWindow().getDecorView();
        decor.postDelayed(() -> CelineEmulatorRenderGuardV49.apply(decor), 80L);
        decor.postDelayed(() -> CelineEmulatorRenderGuardV49.apply(decor), 260L);
        decor.postDelayed(() -> CelineEmulatorRenderGuardV49.apply(decor), 620L);
        CelineSingleBonePresenceV54.install(activity, decor);
        CelineCallUpperBodyPresenceV55.install(activity, decor);
        decor.postDelayed(() -> CelineFallbackAnimator.ensure(decor), 450L);
        decor.postDelayed(() -> applyProduction(activity, decor), 850L);
        decor.postDelayed(() -> applyProduction(activity, decor), 1800L);
        decor.postDelayed(() -> applyProduction(activity, decor), 3800L);
        decor.postDelayed(() -> CelineVideoCallV45.install(activity, decor), 700L);
        decor.postDelayed(() -> CelineCallMotionLockV47.install(activity, decor), 760L);
        decor.postDelayed(() -> CelineUpdaterV47.install(activity, decor), 1100L);
        decor.postDelayed(() -> CelineUpdaterSettingsV50.install(activity, decor), 1180L);
        decor.postDelayed(() -> CelineVideoCallV45.install(activity, decor), 1700L);
        decor.postDelayed(() -> CelineCallMotionLockV47.install(activity, decor), 1760L);
        decor.postDelayed(() -> CelineUpdaterV47.install(activity, decor), 2200L);
        decor.postDelayed(() -> CelineUpdaterSettingsV50.install(activity, decor), 2280L);
    }

    private void applyProduction(Activity activity, View decor) {
        CelineEmulatorRenderGuardV49.apply(decor);
        CelineTexturePipelineV39.applyRuntime(decor);
        CelineTrueUnlitProbeV43.auditRuntime(decor);
        CelineVideoChatV44.ensure(activity, decor);
        CelineLayoutV50.install(activity, decor);
        CelineEmulatorRenderGuardV49.apply(decor);
        CelineVideoCallV45.install(activity, decor);
        CelineCallMotionLockV47.install(activity, decor);
        CelineUpdaterV47.install(activity, decor);
        CelineUpdaterSettingsV50.install(activity, decor);
        CelineSingleBonePresenceV54.install(activity, decor);
        CelineCallUpperBodyPresenceV55.install(activity, decor);
    }

    @Override public void onActivityPaused(Activity activity) {
        if (activity instanceof MainActivity) {
            CelineCallUpperBodyPresenceV55.onPaused(activity);
            CelineSingleBonePresenceV54.onPaused(activity);
            CelineCallMotionLockV47.onPaused(activity);
            CelineVideoCallV45.onPaused(activity);
        }
    }

    @Override public void onActivityDestroyed(Activity activity) {
        if (activity instanceof MainActivity) {
            CelineCallUpperBodyPresenceV55.onDestroyed(activity);
            CelineSingleBonePresenceV54.onDestroyed(activity);
            CelineCallMotionLockV47.onDestroyed(activity);
            CelineVideoCallV45.onDestroyed(activity);
        }
    }

    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
}
