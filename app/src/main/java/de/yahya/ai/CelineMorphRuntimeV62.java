package de.yahya.ai;

import com.google.android.filament.Engine;
import com.google.android.filament.RenderableManager;
import com.google.android.filament.gltfio.FilamentAsset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Guarded runtime for the exact final-geometry v76 facial target contract. */
final class CelineMorphRuntimeV62 {
    private static final int TARGET_COUNT = CelineFacialMotionPlanner.TARGET_COUNT;
    private static final Map<Celine3DView, RuntimeState> STATES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final class RuntimeState {
        final CelineFacialMotionPlanner planner =
                new CelineFacialMotionPlanner(System.nanoTime() / 1_000_000L);
        int[] morphInstances;
        volatile SpeechVisemeAnalyzer.Cue cue = SpeechVisemeAnalyzer.silent();
        volatile float[] diagnosticWeights;
        boolean probed;
        boolean enabled;
        boolean loggedMissing;
    }

    private CelineMorphRuntimeV62() {}

    static void onViseme(Celine3DView view, SpeechVisemeAnalyzer.Cue cue) {
        if (view == null) return;
        stateFor(view).cue = cue == null ? SpeechVisemeAnalyzer.silent() : cue;
    }

    /**
     * v79 Avatar Lab-only diagnostic override. Uses the same guarded final-geometry morph runtime
     * and therefore cannot silently switch to a fallback face. The supplied array is copied.
     */
    static void setDiagnosticWeights(Celine3DView view, float[] requested) {
        if (view == null) return;
        if (requested == null) {
            clearDiagnosticWeights(view);
            return;
        }
        float[] safe = new float[TARGET_COUNT];
        int count = Math.min(TARGET_COUNT, requested.length);
        for (int i = 0; i < count; i++) safe[i] = clamp01(requested[i]);
        stateFor(view).diagnosticWeights = safe;
    }

    static void setDiagnosticTarget(Celine3DView view, int target, float value) {
        if (view == null) return;
        float[] weights = new float[TARGET_COUNT];
        if (target >= 0 && target < TARGET_COUNT) weights[target] = clamp01(value);
        setDiagnosticWeights(view, weights);
    }

    static void clearDiagnosticWeights(Celine3DView view) {
        if (view == null) return;
        RuntimeState state = stateFor(view);
        state.diagnosticWeights = null;
        state.planner.reset(System.nanoTime() / 1_000_000L);
    }

    static void onFrame(Celine3DView view, long frameTimeNanos) {
        if (view == null) return;
        RuntimeState state = stateFor(view);
        if (!state.probed) probeMorphRig(view, state);
        if (!state.enabled || state.morphInstances == null || state.morphInstances.length == 0) return;

        CelineFacialMotionPlanner.Frame frame = state.planner.update(
                frameTimeNanos / 1_000_000L,
                view.v62AvatarState(),
                state.cue,
                view.v76SpeechEnergy(),
                view.v76LookX(),
                view.v76LookY(),
                view.v76LookActive());
        float[] diagnostic = state.diagnosticWeights;
        float[] output = diagnostic == null ? frame.weights : diagnostic;
        try {
            RenderableManager manager = view.v62Engine().getRenderableManager();
            for (int instance : state.morphInstances) {
                manager.setMorphWeights(instance, output, 0);
            }
        } catch (Throwable error) {
            state.enabled = false;
            state.diagnosticWeights = null;
            state.planner.reset(frameTimeNanos / 1_000_000L);
            Celine3DDiagnostics.error(view.getContext(), "V76-299",
                    "Final-geometry Face-Morph Runtime deaktiviert", error);
        }
    }

    private static void probeMorphRig(Celine3DView view, RuntimeState state) {
        state.probed = true;
        try {
            Engine engine = view.v62Engine();
            FilamentAsset asset = view.v62Asset();
            RenderableManager manager = engine.getRenderableManager();
            ArrayList<Integer> instances = new ArrayList<>();
            int bestTargetCount = 0;
            boolean incompatibleRenderable = false;
            for (int entity : asset.getEntities()) {
                if (!manager.hasComponent(entity)) continue;
                int instance = manager.getInstance(entity);
                if (instance == 0) continue;
                int count = manager.getMorphTargetCount(instance);
                bestTargetCount = Math.max(bestTargetCount, count);
                if (count == TARGET_COUNT) instances.add(instance);
                else if (count > 0) incompatibleRenderable = true;
            }
            state.morphInstances = new int[instances.size()];
            for (int i = 0; i < instances.size(); i++) state.morphInstances[i] = instances.get(i);
            state.enabled = !incompatibleRenderable && state.morphInstances.length > 0;
            if (state.enabled) {
                Celine3DDiagnostics.record(view.getContext(), "V76-210",
                        "Final-geometry Face-Morph Rig aktiv",
                        "renderables=" + state.morphInstances.length + " targets=" + TARGET_COUNT);
            } else if (!state.loggedMissing) {
                state.loggedMissing = true;
                Celine3DDiagnostics.record(view.getContext(), "V76-201",
                        "v76 Face-Morph Vertrag nicht vorhanden",
                        "maxTargets=" + bestTargetCount + " incompatible=" + incompatibleRenderable
                                + " · neutrale v75 Baseline bleibt aktiv");
            }
        } catch (Throwable error) {
            state.enabled = false;
            Celine3DDiagnostics.error(view.getContext(), "V76-298",
                    "Final-geometry Face-Morph Probe fehlgeschlagen", error);
        }
    }

    private static RuntimeState stateFor(Celine3DView view) {
        synchronized (STATES) {
            RuntimeState state = STATES.get(view);
            if (state == null) {
                state = new RuntimeState();
                STATES.put(view, state);
            }
            return state;
        }
    }

    private static float clamp01(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return 0.0f;
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
