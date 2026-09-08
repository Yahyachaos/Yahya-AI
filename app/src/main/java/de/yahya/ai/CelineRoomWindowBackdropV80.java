package de.yahya.ai;

import com.google.android.filament.Camera;
import com.google.android.filament.Engine;
import com.google.android.filament.gltfio.FilamentAsset;

import java.lang.reflect.Field;
import java.util.WeakHashMap;

/**
 * Recovery-only appearance checkpoint for the reference-room window/camera path.
 *
 * The rejected #1379 raster and the two subsequent lighting attempts proved that room-side key-light
 * replacement is the wrong causal strategy: the 5k-lux override leaves the source PBR muddy while
 * restoring the renderer-owned 32k-lux key clips the shell/window almost white. Keep the accepted
 * renderer light owner untouched and move the bounded recovery correction to the actual proof camera
 * exposure instead. This preserves source PBR response and avoids another competing light writer.
 *
 * Window/drapes stay the immutable source geometry/PBR. No derived planes, textures, source hiding,
 * material duplication, furniture transforms, FOV/pose writes, source-GLB mutation or Celine changes
 * are owned here.
 */
final class CelineRoomWindowBackdropV80 {
    // One stop darker than the renderer default f/8 exposure while preserving shutter and ISO.
    // This is a different causal strategy from the two rejected global-light attempts and is scoped
    // to the room activation path only. The proof camera remains owned geometrically by Celine3DView.
    private static final float RECOVERY_APERTURE = 11.3f;
    private static final float RECOVERY_SHUTTER_SECONDS = 1.0f / 125.0f;
    private static final float RECOVERY_ISO = 100.0f;

    private static final WeakHashMap<Celine3DView, Boolean> STATES = new WeakHashMap<>();

    private CelineRoomWindowBackdropV80() {}

    static void apply(Celine3DView view, FilamentAsset asset, Engine engine) throws Exception {
        if (view == null || asset == null || engine == null) return;
        synchronized (STATES) {
            if (Boolean.TRUE.equals(STATES.get(view))) return;
        }

        Camera camera = (Camera) field(view, "camera");
        if (camera == null) throw new IllegalStateException("recovery proof camera fehlt");
        camera.setExposure(RECOVERY_APERTURE, RECOVERY_SHUTTER_SECONDS, RECOVERY_ISO);

        synchronized (STATES) {
            STATES.put(view, Boolean.TRUE);
        }
        Celine3DDiagnostics.record(view.getContext(), "ROOM-148",
                "Recovery-Sourcefenster mit kameraeigener Belichtung aktiv",
                "aperture=" + RECOVERY_APERTURE
                        + " · shutter=1/125"
                        + " · iso=" + RECOVERY_ISO
                        + " · rendererKeyOwner=Celine3DView unchanged"
                        + " · rendererIndirectOwner=Celine3DView unchanged"
                        + " · derivedWindowPlanes=false"
                        + " · sourceWindowDrapes=true"
                        + " · sourcePBR=true"
                        + " · experimentalTextures=false"
                        + " · sourceHide=false"
                        + " · ceilingBedOverrides=false"
                        + " · furnitureFovPoseCelineUnchanged=true");
    }

    static void release(Celine3DView view, Engine engine) {
        synchronized (STATES) {
            STATES.remove(view);
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
