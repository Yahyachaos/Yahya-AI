package de.yahya.ai;

import com.google.android.filament.Camera;
import com.google.android.filament.Engine;
import com.google.android.filament.MaterialInstance;
import com.google.android.filament.RenderableManager;
import com.google.android.filament.gltfio.FilamentAsset;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

/**
 * Recovery-only appearance checkpoint for the reference-room window/camera path.
 *
 * The rejected #1379 raster and the subsequent clean-source proofs removed the competing Room-side
 * lighting and derived-window/material stack. Candidate #1402 then bound an opaque neutral atlas
 * directly to the immutable source primitive; the same large ragged holes remained and the whole
 * window became flatter, so texture alpha/color is rejected as the hole cause. Test the next bounded
 * source-only root cause: back-face rejection on the very thin folded drape mesh. Enable the runtime
 * material's double-sided capability only; keep the original source texture/PBR and geometry bytes.
 *
 * No derived planes, substitute geometry, source hiding, material duplication, furniture transforms,
 * FOV/pose writes, source-GLB mutation or Celine changes are owned here.
 */
final class CelineRoomWindowBackdropV80 {
    // One stop darker than the renderer default f/8 exposure while preserving shutter and ISO.
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

        int entity = asset.getFirstEntityByName("room_window_drapes");
        if (entity == 0) throw new IllegalStateException("recovery source window fehlt");
        RenderableManager renderables = engine.getRenderableManager();
        int renderable = renderables.getInstance(entity);
        if (renderable == 0 || renderables.getPrimitiveCount(renderable) != 1) {
            throw new IllegalStateException("recovery source window renderable/primitive ungültig");
        }
        MaterialInstance material = renderables.getMaterialInstanceAt(renderable, 0);
        if (material == null) throw new IllegalStateException("recovery source window material fehlt");

        // Use reflection so this bounded diagnostic stays source-compatible across the exact Filament
        // Java runtime already pinned by the app. Absence of the runtime toggle fails closed rather
        // than introducing substitute geometry or another material owner.
        Method doubleSided = material.getClass().getMethod("setDoubleSided", boolean.class);
        doubleSided.invoke(material, true);

        Camera camera = (Camera) field(view, "camera");
        if (camera == null) throw new IllegalStateException("recovery proof camera fehlt");
        camera.setExposure(RECOVERY_APERTURE, RECOVERY_SHUTTER_SECONDS, RECOVERY_ISO);

        synchronized (STATES) {
            STATES.put(view, Boolean.TRUE);
        }
        Celine3DDiagnostics.record(view.getContext(), "ROOM-148",
                "Recovery-Sourcefenster doppelseitig mit kameraeigener Belichtung aktiv",
                "aperture=" + RECOVERY_APERTURE
                        + " · shutter=1/125"
                        + " · iso=" + RECOVERY_ISO
                        + " · causalTest=sourceBackfaceCulling"
                        + " · sourceDoubleSided=true"
                        + " · sourceTexturePBR=true"
                        + " · rendererKeyOwner=Celine3DView unchanged"
                        + " · rendererIndirectOwner=Celine3DView unchanged"
                        + " · derivedWindowPlanes=false"
                        + " · sourceWindowGeometry=true"
                        + " · sourceGLBMutated=false"
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
