package de.yahya.ai;

import com.google.android.filament.Engine;
import com.google.android.filament.gltfio.FilamentAsset;

import java.util.WeakHashMap;

/**
 * Recovery-only source-window checkpoint for the reference-room architecture pass.
 *
 * Real Candidate #1379 and the later recovery attempts demonstrated that stacking derived window
 * geometry/material/lighting layers on top of the source room can make the CALL raster numerically
 * plausible while visibly corrupting the whole scene. The clean recovery baseline therefore leaves
 * both the source window/drapes PBR and the renderer-owned illumination untouched.
 *
 * Celine3DView remains the single owner of key/indirect renderer lighting. This class owns no
 * derived window planes, textures, source hiding, material duplication, light/exposure mutation,
 * furniture transforms, camera/FOV writes, source-GLB mutation or Celine changes.
 */
final class CelineRoomWindowBackdropV80 {
    private static final WeakHashMap<Celine3DView, Boolean> STATES = new WeakHashMap<>();

    private CelineRoomWindowBackdropV80() {}

    static void apply(Celine3DView view, FilamentAsset asset, Engine engine) {
        if (view == null || asset == null || engine == null) return;
        synchronized (STATES) {
            if (Boolean.TRUE.equals(STATES.get(view))) return;
            STATES.put(view, Boolean.TRUE);
        }
        Celine3DDiagnostics.record(view.getContext(), "ROOM-148",
                "Recovery-Sourcefenster ohne Appearance-Override aktiv",
                "rendererLightingOwner=Celine3DView"
                        + " · rendererLightingMutated=false"
                        + " · derivedWindowPlanes=false"
                        + " · sourceWindowDrapes=true"
                        + " · sourcePBR=true"
                        + " · experimentalTextures=false"
                        + " · sourceHide=false"
                        + " · ceilingBedOverrides=false"
                        + " · cameraFurnitureCelineUnchanged=true");
    }

    static void release(Celine3DView view, Engine engine) {
        synchronized (STATES) {
            STATES.remove(view);
        }
    }
}