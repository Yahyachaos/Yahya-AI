package de.yahya.ai;

import java.util.WeakHashMap;

/**
 * Compatibility hook for the retired pre-reconstruction HOME camera owner.
 *
 * The exact 4.40 x 4.20 room solve is now owned directly by Celine3DView immediately before every
 * Filament frame. That owner applies the accepted Proof #63 focal length, eye and target in the
 * runtime room coordinate system. Keeping a second camera writer here reintroduced an older
 * 7.10 m dolly after the exact camera had already been applied and produced the real-app dollhouse
 * framing seen in Proof #183.
 *
 * This hook intentionally performs no camera write. It remains only because older call sites still
 * invoke it while the v80 branch is being reconstructed. Celine, room geometry, furniture TRS and
 * CALL framing are not changed here.
 */
final class CelineReferenceHomeCameraV80 {
    private static final WeakHashMap<Celine3DView, Boolean> LOGGED = new WeakHashMap<>();

    private CelineReferenceHomeCameraV80() {}

    static void apply(Celine3DView view) {
        if (view == null || CelineCallUpperBodyPresenceV55.isCallStage(view)) return;
        synchronized (LOGGED) {
            if (LOGGED.put(view, Boolean.TRUE) != null) return;
        }
        Celine3DDiagnostics.record(view.getContext(), "ROOM-160",
                "Staler HOME-Kamera-Override stillgelegt",
                "cameraWrite=false owner=Celine3DView Proof#63"
                        + " lens=20.846875"
                        + " eye=-0.380078125,-0.3265625,-1.1265625"
                        + " target=-0.0565625,-1.10,-4.30"
                        + " roomRoot=false furnitureTRS=false canonicalCeline=false");
    }
}
