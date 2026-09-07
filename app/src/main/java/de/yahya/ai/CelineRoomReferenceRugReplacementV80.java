package de.yahya.ai;

import android.opengl.Matrix;
import android.view.View;

import com.google.android.filament.Engine;
import com.google.android.filament.Scene;
import com.google.android.filament.TransformManager;
import com.google.android.filament.gltfio.FilamentAsset;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * New root-cause rug strategy after the rejected smooth-overlay attempt.
 *
 * The old attempt placed a flat surface above room_rug but kept the high-relief source renderable in
 * the Scene, so the source gaps/facets remained visible. This owner first asks the existing derived
 * surface owner to duplicate the isolated rug material, then applies a bounded reference-size fit to
 * that derived entity and removes only the source room_rug entity from the Scene. The immutable
 * Teppisch.glb bytes, source renderable, accepted source TRS and material remain alive and are restored
 * unchanged on release.
 *
 * #1285 visible rug bbox is 0.207677..0.857283 / 0.539975..0.782288 versus reference
 * 0.205..0.870 / 0.520..0.795. The local plane fit therefore uses only measured width/depth ratios;
 * no camera, shell, Celine, anchor or source-rug transform is changed.
 */
final class CelineRoomReferenceRugReplacementV80 {
    private static final String SOURCE_RUG = "room_rug";
    private static final float WIDTH_SCALE = 1.0236969f;
    private static final float DEPTH_SCALE = 1.1348993f;

    private static final WeakHashMap<Celine3DView, State> STATES = new WeakHashMap<>();

    private CelineRoomReferenceRugReplacementV80() {}

    static void apply(Celine3DView view, Engine engine) throws Exception {
        if (view == null || engine == null) return;
        synchronized (STATES) {
            if (STATES.containsKey(view)) return;
        }

        FilamentAsset asset = currentRoomAsset(view);
        if (asset == null) throw new IllegalStateException("rug replacement: room asset fehlt");
        Scene scene = (Scene) field(view, "scene");
        if (scene == null) throw new IllegalStateException("rug replacement: scene fehlt");
        int sourceEntity = asset.getFirstEntityByName(SOURCE_RUG);
        if (sourceEntity == 0) throw new IllegalStateException("rug replacement: source rug fehlt");

        boolean surfaceApplied = false;
        boolean sourceHidden = false;
        State state = null;
        try {
            CelineRoomReferenceRugSurfaceV80.apply(view, engine);
            surfaceApplied = true;

            int derivedEntity = derivedSurfaceEntity(view);
            TransformManager transforms = engine.getTransformManager();
            int derivedInstance = transforms.getInstance(derivedEntity);
            if (derivedInstance == 0) {
                throw new IllegalStateException("rug replacement: derived transform fehlt");
            }
            float[] local = transforms.getTransform(derivedInstance, new float[16]);
            float[] fit = new float[16];
            float[] adjusted = new float[16];
            Matrix.setIdentityM(fit, 0);
            Matrix.scaleM(fit, 0, WIDTH_SCALE, DEPTH_SCALE, 1.0f);
            Matrix.multiplyMM(adjusted, 0, local, 0, fit, 0);
            transforms.setTransform(derivedInstance, adjusted);

            // This is the causal difference from rejected #1264: do not let the immutable high-relief
            // source renderable contribute pixels while the smooth derived replacement is active.
            scene.removeEntity(sourceEntity);
            sourceHidden = true;

            state = new State(view, scene, sourceEntity);
            synchronized (STATES) { STATES.put(view, state); }
            view.addOnAttachStateChangeListener(state);

            Celine3DDiagnostics.record(view.getContext(), "ROOM-157",
                    "Teppich als glatte Ableitung mit ausgeblendeter Quell-Geometrie aktiv",
                    "authority=Proof#1285 widthScale=" + WIDTH_SCALE
                            + " depthScale=" + DEPTH_SCALE
                            + " sourceEntityHidden=true sourceEntityDestroyed=false"
                            + " sourceGLBMutated=false sourceTRSMutated=false"
                            + " material=isolatedRugDuplicate"
                            + " roomShell/camera/Celine/anchors unchanged=true");
        } catch (Throwable error) {
            if (sourceHidden) {
                try { scene.addEntity(sourceEntity); } catch (Throwable ignored) {}
            }
            if (state != null) {
                synchronized (STATES) { STATES.remove(view); }
                try { view.removeOnAttachStateChangeListener(state); } catch (Throwable ignored) {}
            }
            if (surfaceApplied) {
                try { CelineRoomReferenceRugSurfaceV80.release(view); } catch (Throwable ignored) {}
            }
            throw error;
        }
    }

    static void release(Celine3DView view) {
        State state;
        synchronized (STATES) { state = STATES.remove(view); }
        if (state != null) state.restore(true);
        CelineRoomReferenceRugSurfaceV80.release(view);
    }

    private static int derivedSurfaceEntity(Celine3DView view) throws Exception {
        Field statesField = CelineRoomReferenceRugSurfaceV80.class.getDeclaredField("STATES");
        statesField.setAccessible(true);
        Object rawStates = statesField.get(null);
        if (!(rawStates instanceof Map)) {
            throw new IllegalStateException("rug replacement: derived STATES is not a map");
        }
        Object state = ((Map<?, ?>) rawStates).get(view);
        if (state == null) throw new IllegalStateException("rug replacement: derived state fehlt");
        Field entityField = state.getClass().getDeclaredField("entity");
        entityField.setAccessible(true);
        int entity = entityField.getInt(state);
        if (entity == 0) throw new IllegalStateException("rug replacement: derived entity fehlt");
        return entity;
    }

    private static FilamentAsset currentRoomAsset(Celine3DView view) throws Exception {
        Field statesField = CelineRoomEnvironmentV80.class.getDeclaredField("STATES");
        statesField.setAccessible(true);
        Object rawStates = statesField.get(null);
        if (!(rawStates instanceof Map)) return null;
        Map<?, ?> states = (Map<?, ?>) rawStates;
        Object state;
        synchronized (states) { state = states.get(view); }
        if (state == null) return null;
        Field assetField = state.getClass().getDeclaredField("roomAsset");
        assetField.setAccessible(true);
        return (FilamentAsset) assetField.get(state);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static final class State implements View.OnAttachStateChangeListener {
        final Celine3DView view;
        final Scene scene;
        final int sourceEntity;
        boolean restored;

        State(Celine3DView view, Scene scene, int sourceEntity) {
            this.view = view;
            this.scene = scene;
            this.sourceEntity = sourceEntity;
        }

        @Override public void onViewAttachedToWindow(View v) {}

        @Override public void onViewDetachedFromWindow(View v) {
            synchronized (STATES) { STATES.remove(view); }
            restore(false);
            try { CelineRoomReferenceRugSurfaceV80.release(view); } catch (Throwable ignored) {}
        }

        void restore(boolean removeListener) {
            if (restored) return;
            restored = true;
            if (removeListener) {
                try { view.removeOnAttachStateChangeListener(this); } catch (Throwable ignored) {}
            }
            try { scene.addEntity(sourceEntity); } catch (Throwable ignored) {}
        }
    }
}
