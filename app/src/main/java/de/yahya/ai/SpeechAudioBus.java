package de.yahya.ai;

import android.os.Handler;
import android.os.Looper;

import java.lang.ref.WeakReference;

/** Neutral bridge from speech audio analysis to UI/avatar reactions. */
public final class SpeechAudioBus {
    public interface Listener {
        void onSpeechAudioLevel(float level);
        void onSpeechViseme(SpeechVisemeAnalyzer.Cue cue);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WeakReference<Listener> listener = new WeakReference<>(null);

    private SpeechAudioBus() {}

    public static synchronized void setListener(Listener next) {
        listener = new WeakReference<>(next);
    }

    public static synchronized void clearListener(Listener expected) {
        Listener current = listener.get();
        if (current == expected) listener = new WeakReference<>(null);
    }

    public static void publish(float level) {
        final float value = Math.max(0f, Math.min(1f, level));
        MAIN.post(() -> {
            Listener target = getListener();
            if (target != null) target.onSpeechAudioLevel(value);
        });
    }

    public static void publishViseme(SpeechVisemeAnalyzer.Cue cue) {
        final SpeechVisemeAnalyzer.Cue value = cue == null ? SpeechVisemeAnalyzer.silent() : cue;
        MAIN.post(() -> {
            Listener target = getListener();
            if (target != null) target.onSpeechViseme(value);
        });
    }

    public static void reset() {
        publish(0f);
        publishViseme(SpeechVisemeAnalyzer.silent());
    }

    private static synchronized Listener getListener() { return listener.get(); }
}
