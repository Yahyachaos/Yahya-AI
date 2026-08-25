package de.yahya.ai;

import com.google.android.filament.Engine;
import com.google.android.filament.RenderableManager;
import com.google.android.filament.gltfio.FilamentAsset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * v62 guarded facial morph runtime.
 *
 * Target order is intentionally locked to the validated private candidate:
 * 0 BlinkLeft, 1 BlinkRight, 2 BlinkBoth, 3 JawOpen,
 * 4 RoundedVowelProof, 5 SpreadVowelProof.
 *
 * If the currently imported private celine.glb has no six-target morph rig this class is a no-op,
 * so v61/v59 rendering and ownership remain the rollback baseline.
 */
final class CelineMorphRuntimeV62 {
    private static final int TARGET_COUNT = 6;
    private static final int BLINK_LEFT = 0;
    private static final int BLINK_RIGHT = 1;
    private static final int BLINK_BOTH = 2;
    private static final int JAW_OPEN = 3;
    private static final int ROUND = 4;
    private static final int SPREAD = 5;

    private static final Map<Celine3DView, RuntimeState> STATES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final class RuntimeState {
        final float[] current = new float[TARGET_COUNT];
        final float[] target = new float[TARGET_COUNT];
        int[] morphInstances;
        volatile SpeechVisemeAnalyzer.Cue cue = SpeechVisemeAnalyzer.silent();
        long lastFrameNanos;
        boolean probed;
        boolean enabled;
        boolean loggedMissing;
    }

    private CelineMorphRuntimeV62() {}

    static void onViseme(Celine3DView view, SpeechVisemeAnalyzer.Cue cue) {
        if (view == null) return;
        RuntimeState state = stateFor(view);
        state.cue = cue == null ? SpeechVisemeAnalyzer.silent() : cue;
    }

    static void onFrame(Celine3DView view, long frameTimeNanos) {
        if (view == null) return;
        RuntimeState state = stateFor(view);
        if (!state.probed) probeMorphRig(view, state);
        if (!state.enabled || state.morphInstances == null || state.morphInstances.length == 0) return;

        double t = frameTimeNanos * 1.0e-9;
        float dt = state.lastFrameNanos == 0L
                ? (1.0f / 60.0f)
                : clamp((frameTimeNanos - state.lastFrameNanos) * 1.0e-9f, 0.001f, 0.060f);
        state.lastFrameNanos = frameTimeNanos;

        buildTargets(view, state, t);
        float attack = 1.0f - (float) Math.exp(-dt * 17.0f);
        float release = 1.0f - (float) Math.exp(-dt * 11.0f);
        for (int i = 0; i < TARGET_COUNT; i++) {
            float a = state.target[i] > state.current[i] ? attack : release;
            state.current[i] += (state.target[i] - state.current[i]) * a;
            if (Math.abs(state.current[i]) < 0.0005f) state.current[i] = 0.0f;
        }

        try {
            RenderableManager manager = view.v62Engine().getRenderableManager();
            for (int instance : state.morphInstances) {
                manager.setMorphWeights(instance, state.current, 0);
            }
        } catch (Throwable e) {
            state.enabled = false;
            Celine3DDiagnostics.error(view.getContext(), "V62-299", "Face-Morph Runtime deaktiviert", e);
        }
    }

    private static void buildTargets(Celine3DView view, RuntimeState state, double t) {
        for (int i = 0; i < TARGET_COUNT; i++) state.target[i] = 0.0f;

        // Natural blink pulse: roughly every 4-5 s with a slow deterministic jitter.
        double clock = t + Math.sin(t * 0.173) * 0.48 + Math.sin(t * 0.071 + 1.4) * 0.31;
        double cycle = 4.35;
        double phase = clock - Math.floor(clock / cycle) * cycle;
        float blink = 0.0f;
        if (phase < 0.155) {
            double p = phase / 0.155;
            blink = (float) (p < 0.46 ? p / 0.46 : (1.0 - p) / 0.54);
            blink = clamp(blink, 0.0f, 1.0f);
            blink = blink * blink * (3.0f - 2.0f * blink);
        }
        if (blink > 0.0f) {
            float asym = 0.018f * (float) Math.sin(t * 0.83 + 0.9);
            state.target[BLINK_BOTH] = clamp(blink * 0.92f, 0.0f, 0.95f);
            state.target[BLINK_LEFT] = clamp(blink * (0.035f + Math.max(0.0f, asym)), 0.0f, 0.07f);
            state.target[BLINK_RIGHT] = clamp(blink * (0.035f + Math.max(0.0f, -asym)), 0.0f, 0.07f);
        }

        CelineAvatarController.State avatarState = view.v62AvatarState();
        SpeechVisemeAnalyzer.Cue cue = state.cue == null ? SpeechVisemeAnalyzer.silent() : state.cue;
        if (avatarState == CelineAvatarController.State.SPEAKING) {
            float openness = clamp(cue.openness, 0.0f, 1.0f);
            float width = clamp(cue.width, 0.0f, 1.0f);
            float roundness = clamp(cue.roundness, 0.0f, 1.0f);
            switch (cue.shape) {
                case ROUND:
                    state.target[JAW_OPEN] = 0.12f + openness * 0.46f;
                    state.target[ROUND] = 0.18f + roundness * 0.48f;
                    break;
                case WIDE:
                    state.target[JAW_OPEN] = openness * 0.50f;
                    state.target[SPREAD] = 0.12f + width * 0.50f;
                    break;
                case LABIAL:
                    state.target[JAW_OPEN] = openness * 0.18f;
                    state.target[ROUND] = 0.28f + roundness * 0.22f;
                    break;
                case TEETH:
                    state.target[JAW_OPEN] = openness * 0.28f;
                    state.target[SPREAD] = 0.18f + width * 0.36f;
                    break;
                case OPEN:
                    state.target[JAW_OPEN] = openness * 0.70f;
                    state.target[ROUND] = roundness * 0.08f;
                    state.target[SPREAD] = width * 0.08f;
                    break;
                case CLOSED:
                default:
                    break;
            }
            state.target[JAW_OPEN] = clamp(state.target[JAW_OPEN], 0.0f, 0.72f);
            state.target[ROUND] = clamp(state.target[ROUND], 0.0f, 0.62f);
            state.target[SPREAD] = clamp(state.target[SPREAD], 0.0f, 0.62f);
        } else {
            // Very small state-aware microexpression; never competes with speech shapes.
            float micro = (float) (0.5 + 0.5 * Math.sin(t * 0.37 + 0.4));
            if (avatarState == CelineAvatarController.State.LISTENING) {
                state.target[SPREAD] = 0.010f + micro * 0.010f;
            } else if (avatarState == CelineAvatarController.State.THINKING) {
                state.target[ROUND] = 0.008f + micro * 0.012f;
            } else {
                state.target[SPREAD] = micro * 0.010f;
            }
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
            for (int entity : asset.getEntities()) {
                if (!manager.hasComponent(entity)) continue;
                int instance = manager.getInstance(entity);
                if (instance == 0) continue;
                int count = manager.getMorphTargetCount(instance);
                bestTargetCount = Math.max(bestTargetCount, count);
                if (count >= TARGET_COUNT) instances.add(instance);
            }
            state.morphInstances = new int[instances.size()];
            for (int i = 0; i < instances.size(); i++) state.morphInstances[i] = instances.get(i);
            state.enabled = state.morphInstances.length > 0;
            if (state.enabled) {
                Celine3DDiagnostics.record(view.getContext(), "V62-210", "Private Face-Morph Rig aktiv",
                        "renderables=" + state.morphInstances.length + " targets>=6");
            } else if (!state.loggedMissing) {
                state.loggedMissing = true;
                Celine3DDiagnostics.record(view.getContext(), "V62-201", "Face-Morph Rig nicht vorhanden",
                        "maxTargets=" + bestTargetCount + " · v61/v59 Baseline bleibt aktiv");
            }
        } catch (Throwable e) {
            state.enabled = false;
            Celine3DDiagnostics.error(view.getContext(), "V62-298", "Face-Morph Probe fehlgeschlagen", e);
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

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
