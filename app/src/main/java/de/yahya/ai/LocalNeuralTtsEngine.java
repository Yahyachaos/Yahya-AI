package de.yahya.ai;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Local Supertonic 3 speech engine backed by sherpa-onnx.
 *
 * This class deliberately reports itself as unavailable until every required
 * model file exists on the device. No cloud fallback is hidden inside it.
 */
public final class LocalNeuralTtsEngine {
    public interface Listener {
        void onPreparing();
        void onSpeaking();
        void onDone();
        void onError(Throwable error);
    }

    public static final String MODEL_DIR_NAME = "supertonic3";
    private static final String[] REQUIRED = {
            "duration_predictor.int8.onnx",
            "text_encoder.int8.onnx",
            "vector_estimator.int8.onnx",
            "vocoder.int8.onnx",
            "tts.json",
            "unicode_indexer.bin",
            "voice.bin"
    };

    private final Context context;
    private volatile Object offlineTts;
    private volatile AudioTrack activeTrack;

    public LocalNeuralTtsEngine(Context context) {
        this.context = context.getApplicationContext();
    }

    public File getModelDir() {
        return new File(new File(context.getFilesDir(), "tts"), MODEL_DIR_NAME);
    }

    public boolean isModelInstalled() {
        File dir = getModelDir();
        if (!dir.isDirectory()) return false;
        for (String name : REQUIRED) {
            File f = new File(dir, name);
            if (!f.isFile() || f.length() <= 0) return false;
        }
        return true;
    }

    public void speak(String text, Listener listener) {
        if (text == null || text.trim().isEmpty()) return;
        if (!isModelInstalled()) {
            listener.onError(new IllegalStateException("Supertonic model is not installed"));
            return;
        }
        new Thread(() -> {
            try {
                listener.onPreparing();
                Object tts = ensureInitialized();
                Object config = createGenerationConfig();
                Method generate = findMethod(tts.getClass(), "generateWithConfig", 2);
                Object audio = generate.invoke(tts, text, config);
                Method getSamples = audio.getClass().getMethod("getSamples");
                Method getSampleRate = audio.getClass().getMethod("getSampleRate");
                float[] samples = (float[]) getSamples.invoke(audio);
                int sampleRate = (Integer) getSampleRate.invoke(audio);
                if (samples == null || samples.length == 0 || sampleRate <= 0) {
                    throw new IllegalStateException("Supertonic returned no audio");
                }
                listener.onSpeaking();
                playBlocking(samples, sampleRate);
                listener.onDone();
            } catch (Throwable error) {
                SpeechAudioBus.reset();
                listener.onError(unwrap(error));
            }
        }, "celin-local-tts").start();
    }

    private synchronized Object ensureInitialized() throws Exception {
        if (offlineTts != null) return offlineTts;
        File dir = getModelDir();

        Class<?> ttsKt = Class.forName("com.k2fsa.sherpa.onnx.TtsKt");
        Method factory = findMethod(ttsKt, "getOfflineTtsConfig", 20);
        Object config = factory.invoke(null,
                dir.getAbsolutePath(), "", "", "", "", "", "", "", "", "",
                Integer.valueOf(2), false, true,
                "duration_predictor.int8.onnx",
                "text_encoder.int8.onnx",
                "vector_estimator.int8.onnx",
                "vocoder.int8.onnx",
                "tts.json",
                "unicode_indexer.bin",
                "voice.bin");

        Class<?> offlineClass = Class.forName("com.k2fsa.sherpa.onnx.OfflineTts");
        Constructor<?> ctor = null;
        for (Constructor<?> c : offlineClass.getConstructors()) {
            if (c.getParameterTypes().length == 2) { ctor = c; break; }
        }
        if (ctor == null) throw new NoSuchMethodException("OfflineTts constructor");
        offlineTts = ctor.newInstance(null, config);
        return offlineTts;
    }

    private Object createGenerationConfig() throws Exception {
        Class<?> c = Class.forName("com.k2fsa.sherpa.onnx.GenerationConfig");
        Constructor<?> ctor = null;
        for (Constructor<?> x : c.getConstructors()) {
            if (x.getParameterTypes().length == 8) { ctor = x; break; }
        }
        if (ctor == null) throw new NoSuchMethodException("GenerationConfig constructor");
        Map<String, String> extra = new HashMap<>();
        extra.put("lang", "de");
        // Speaker 2 is only the initial candidate. We will audition all female
        // speakers on the target device before locking Celine's final voice.
        return ctor.newInstance(0.20f, 0.96f, 2, null, 0, null, 8, extra);
    }

    private void playBlocking(float[] samples, int sampleRate) {
        int min = AudioTrack.getMinBufferSize(sampleRate,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT);
        int buffer = Math.max(min, Math.min(samples.length * 4, 1024 * 1024));
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        AudioFormat format = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build();
        AudioTrack track = new AudioTrack(attrs, format, buffer,
                AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
        activeTrack = track;
        float envelope = 0f;
        try {
            track.play();
            int pos = 0;
            // ~85 ms at 24 kHz: responsive enough for visible mouth motion while
            // avoiding UI updates at audio-sample frequency.
            final int visualChunk = Math.max(512, sampleRate / 12);
            while (pos < samples.length) {
                int count = Math.min(visualChunk, samples.length - pos);
                float level = normalizedRms(samples, pos, count);
                envelope = envelope * 0.42f + level * 0.58f;
                SpeechAudioBus.publish(envelope);
                int written = track.write(samples, pos, count, AudioTrack.WRITE_BLOCKING);
                if (written < 0) throw new IllegalStateException("AudioTrack write failed: " + written);
                pos += written;
            }
            SpeechAudioBus.reset();
            long waitMs = Math.max(50L, (long) samples.length * 1000L / sampleRate + 80L);
            try { Thread.sleep(waitMs); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        } finally {
            SpeechAudioBus.reset();
            try { track.stop(); } catch (Exception ignored) {}
            try { track.release(); } catch (Exception ignored) {}
            if (activeTrack == track) activeTrack = null;
        }
    }

    private static float normalizedRms(float[] samples, int offset, int count) {
        if (samples == null || count <= 0) return 0f;
        double sum = 0.0;
        int end = Math.min(samples.length, offset + count);
        for (int i = offset; i < end; i++) {
            float v = samples[i];
            sum += v * v;
        }
        int n = Math.max(1, end - offset);
        double rms = Math.sqrt(sum / n);
        // Ignore near-silence and map ordinary speech energy into 0..1.
        float level = (float) ((rms - 0.006) / 0.105);
        if (level < 0f) return 0f;
        if (level > 1f) return 1f;
        return level;
    }

    public synchronized void release() {
        SpeechAudioBus.reset();
        AudioTrack track = activeTrack;
        activeTrack = null;
        if (track != null) {
            try { track.pause(); } catch (Exception ignored) {}
            try { track.flush(); } catch (Exception ignored) {}
            try { track.stop(); } catch (Exception ignored) {}
            try { track.release(); } catch (Exception ignored) {}
        }
        Object tts = offlineTts;
        offlineTts = null;
        if (tts != null) {
            try { tts.getClass().getMethod("release").invoke(tts); } catch (Exception ignored) {}
        }
    }

    private static Method findMethod(Class<?> c, String name, int params) throws NoSuchMethodException {
        for (Method m : c.getMethods()) {
            if (m.getName().equals(name) && m.getParameterTypes().length == params) return m;
        }
        throw new NoSuchMethodException(c.getName() + "." + name + "/" + params);
    }

    private static Throwable unwrap(Throwable t) {
        Throwable cause = t.getCause();
        return cause != null ? cause : t;
    }
}
