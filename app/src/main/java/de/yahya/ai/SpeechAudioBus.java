package de.yahya.ai;

import android.os.Handler;
import android.os.Looper;

import java.lang.ref.WeakReference;

/**
 * Tiny neutral bridge for audio-driven UI reactions.
 *
 * The speech engine publishes normalized mouth-energy values here without
 * depending on the avatar implementation. The avatar may subscribe while it
 * is visible. This keeps TTS and avatar modules logically separate.
 */
public final class SpeechAudioBus {
    public interface Listener {
        void onSpeechAudioLevel(float level);
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
            Listener target;
            synchronized (SpeechAudioBus.class) {
                target = listener.get();
            }
            if (target != null) target.onSpeechAudioLevel(value);
        });
    }

    public static void reset() {
        publish(0f);
    }
}
