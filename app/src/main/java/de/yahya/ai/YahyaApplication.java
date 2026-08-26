package de.yahya.ai;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;

/**
 * v79 keeps the proven motion/lifecycle owners and adds narrowly scoped visual-realism owners.
 * The Avatar Lab remains a separate branch-live diagnostic Activity; product HOME/CALL interaction
 * is anchored and no longer exposes one-finger object-like dragging.
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
                "HOME Head-only · CALL neck+Head+seated spine · v56-v58 Hips/Shoulder Produktion quarantiniert");
        Celine3DDiagnostics.record(activity, "V61-003", "Meshy Rig-Scale Guard aktiv",
                "0.01 Armature / inverse-bind x100 Korrektur · v60 Kamera-Steuerung beibehalten");
        Celine3DDiagnostics.record(activity, "V79-004", "v79 Arm/Hand-Sicherheitsmodus aktiv",
                "sechs gewichtete Joints · sichtbare bounded HOME/CALL Loops · keine Fingerknochen");
        Celine3DDiagnostics.record(activity, "V75-003", "v75 Semantikmaterial-Owner aktiv",
                "V39 bleibt Haut/Gesicht-Owner · v75 übernimmt nur top/jeans/shoes/hair nach V39");
        Celine3DDiagnostics.record(activity, "V79-003", "v79 Produkt-Interaktion verankert",
                "oneFingerDrag=blocked · pinch=trueCameraDolly · Avatar Lab separat");
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
        CelineArmHandPresenceV79.install(activity, decor);
        CelineSeatedCallV70.install(activity, decor);
        CelineNaturalBodyMotionV73.install(activity, decor);
        CelineCameraZoomV70.install(activity, decor);
        CelineProductInteractionLockV79.install(activity, decor);
        CelineReferenceViewV75.install(activity, decor);
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
        CelineSemanticMaterialsV75.apply(activity, decor);
        CelineTrueUnlitProbeV43.auditRuntime(decor);
        CelineVideoChatV44.ensure(activity, decor);
        CelineLayoutV50.install(activity, decor);
        CelineEmulatorRenderGuardV49.apply(decor);
        CelineVideoCallV45.install(activity, decor);
        CelineCallMotionLockV47.install(activity, decor);
        CelineCameraZoomV70.install(activity, decor);
        CelineProductInteractionLockV79.install(activity, decor);
        CelineReferenceViewV75.install(activity, decor);
        CelineCallImeGuardV70.install(activity, decor);
        CelineUpdaterV47.install(activity, decor);
        CelineUpdaterSettingsV50.install(activity, decor);
        CelineMeshyRigScaleV61.install(activity, decor);
        CelineSingleBonePresenceV54.install(activity, decor);
        CelineCallUpperBodyPresenceV55.install(activity, decor);
        CelineArmHandPresenceV79.install(activity, decor);
        CelineSeatedCallV70.install(activity, decor);
        CelineNaturalBodyMotionV73.install(activity, decor);
    }

    @Override public void onActivityPaused(Activity activity) {
        if (activity instanceof MainActivity) {
            CelineReferenceViewV75.onPaused(activity);
            CelineProductInteractionLockV79.onPaused(activity);
            CelineMeshyRigScaleV61.onPaused(activity);
            CelineCameraZoomV70.onPaused(activity);
            CelineNaturalBodyMotionV73.onPaused(activity);
            CelineSeatedCallV70.onPaused(activity);
            CelineArmHandPresenceV79.onPaused(activity);
            CelineCallUpperBodyPresenceV55.onPaused(activity);
            CelineSingleBonePresenceV54.onPaused(activity);
            CelineCallMotionLockV47.onPaused(activity);
            CelineVideoCallV45.onPaused(activity);
        }
    }

    @Override public void onActivityDestroyed(Activity activity) {
        if (activity instanceof MainActivity) {
            CelineReferenceViewV75.onDestroyed(activity);
            CelineProductInteractionLockV79.onDestroyed(activity);
            CelineMeshyRigScaleV61.onDestroyed(activity);
            CelineCameraZoomV70.onDestroyed(activity);
            CelineCallImeGuardV70.onDestroyed(activity);
            CelineNaturalBodyMotionV73.onDestroyed(activity);
            CelineSeatedCallV70.onDestroyed(activity);
            CelineArmHandPresenceV79.onDestroyed(activity);
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
