package de.yahya.ai;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;

/**
 * v61 keeps the v59 safe skinning ownership and adds the production Meshy rig-scale correction
 * proven necessary by the user's v60 device video. HOME stays Head-only and CALL stays neck+Head;
 * the v56-v58 Hips/shoulder path remains quarantined.
 * TRUE-UNLIT/FORCE-C, layout, camera controls and updater placement remain unchanged.
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
        Celine3DDiagnostics.record(activity, "V61-003", "Meshy Rig-Scale Guard aktiv",
                "0.01 Armature / inverse-bind x100 Korrektur · v60 Kamera-Steuerung beibehalten");
    }

    @Override public void onActivityResumed(Activity activity) {
        if (!(activity instanceof MainActivity)) return;
        View decor = activity.getWindow().getDecorView();
        decor.postDelayed(() -> CelineEmulatorRenderGuardV49.apply(decor), 80L);
        decor.postDelayed(() -> CelineEmulatorRenderGuardV49.apply(decor), 260L);
        decor.postDelayed(() -> CelineEmulatorRenderGuardV49.apply(decor), 620L);
        CelineMeshyRigScaleV61.install(activity, decor);
        CelineSingleBonePresenceV54.install(activity, decor);
        CelineCallUpperBodyPresenceV55.install(activity, decor);
        CelineArmPoseV69.install(activity, decor);
        CelineSeatedCallV70.install(activity, decor);
        CelineCameraZoomV70.install(activity, decor);
        CelineCallImeGuardV70.install(activity, decor);
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
        CelineCameraZoomV70.install(activity, decor);
        CelineCallImeGuardV70.install(activity, decor);
        CelineUpdaterV47.install(activity, decor);
        CelineUpdaterSettingsV50.install(activity, decor);
        CelineMeshyRigScaleV61.install(activity, decor);
        CelineSingleBonePresenceV54.install(activity, decor);
        CelineCallUpperBodyPresenceV55.install(activity, decor);
        CelineArmPoseV69.install(activity, decor);
        CelineSeatedCallV70.install(activity, decor);
    }

    @Override public void onActivityPaused(Activity activity) {
        if (activity instanceof MainActivity) {
            CelineMeshyRigScaleV61.onPaused(activity);
            CelineCameraZoomV70.onPaused(activity);
            CelineSeatedCallV70.onPaused(activity);
            CelineArmPoseV69.onPaused(activity);
            CelineCallUpperBodyPresenceV55.onPaused(activity);
            CelineSingleBonePresenceV54.onPaused(activity);
            CelineCallMotionLockV47.onPaused(activity);
            CelineVideoCallV45.onPaused(activity);
        }
    }

    @Override public void onActivityDestroyed(Activity activity) {
        if (activity instanceof MainActivity) {
            CelineMeshyRigScaleV61.onDestroyed(activity);
            CelineCameraZoomV70.onDestroyed(activity);
            CelineCallImeGuardV70.onDestroyed(activity);
            CelineSeatedCallV70.onDestroyed(activity);
            CelineArmPoseV69.onDestroyed(activity);
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
