package de.yahya.ai;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.k2fsa.sherpa.onnx.GeneratedAudio;
import com.k2fsa.sherpa.onnx.GenerationConfig;
import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** Local Supertonic 3 speech engine backed by sherpa-onnx. */
public final class LocalNeuralTtsEngine {
    public interface Listener {
        void onPreparing();
        void onSpeaking();
        void onDone();
        void onError(Throwable error);
    }

    public static final String MODEL_DIR_NAME = "supertonic3";
    private static final String[] REQUIRED = {
            "duration_predictor.int8.onnx", "text_encoder.int8.onnx",
            "vector_estimator.int8.onnx", "vocoder.int8.onnx", "tts.json",
            "unicode_indexer.bin", "voice.bin"
    };

    // Supertonic voice.bin contains 10 speakers. The shipped order is M1-M5, F1-F5.
    // F3 is a deliberately soft female default for Celine.
    private static final int DEFAULT_FEMALE_SID = 7;
    private static final float DEFAULT_SPEED = 0.93f;
    private static final int DEFAULT_STEPS = 12;

    private final Context context;
    private volatile OfflineTts offlineTts;
    private volatile AudioTrack activeTrack;
    private volatile Throwable lastError;
    private volatile int speakerId = DEFAULT_FEMALE_SID;
    private volatile float speed = DEFAULT_SPEED;
    private volatile int numSteps = DEFAULT_STEPS;

    public LocalNeuralTtsEngine(Context context) {
        this.context = context.getApplicationContext();
    }

    public File getModelDir() {
        return new File(new File(context.getFilesDir(), "tts"), MODEL_DIR_NAME);
    }

    public void setVoiceProfile(int sid, float speed) {
        this.speakerId = Math.max(0, Math.min(9, sid));
        this.speed = Math.max(0.78f, Math.min(1.18f, speed));
    }

    public int getSpeakerId() { return speakerId; }
    public float getSpeed() { return speed; }

    public boolean isModelInstalled() {
        File dir = getModelDir();
        if (!dir.isDirectory()) return false;
        for (String name : REQUIRED) {
            File f = new File(dir, name);
            if (!f.isFile() || f.length() <= 0) return false;
        }
        return true;
    }

    public String diagnosticReport() {
        StringBuilder b = new StringBuilder();
        File dir = getModelDir();
        b.append("Modelordner: ").append(dir.getAbsolutePath()).append('\n');
        b.append("Ordner vorhanden: ").append(dir.isDirectory() ? "JA" : "NEIN").append("\n\n");
        for (String name : REQUIRED) {
            File f = new File(dir, name);
            b.append(name).append(": ");
            if (f.isFile()) b.append(f.length()).append(" Bytes"); else b.append("FEHLT");
            b.append('\n');
        }
        b.append("\nModell vollständig: ").append(isModelInstalled() ? "JA" : "NEIN").append('\n');
        b.append("Speaker-ID: ").append(speakerId).append(" | Speed: ").append(speed).append('\n');
        b.append("ABI: ");
        try { b.append(android.os.Build.SUPPORTED_ABIS == null ? "?" : java.util.Arrays.toString(android.os.Build.SUPPORTED_ABIS)); }
        catch (Throwable ignored) { b.append("?"); }
        b.append('\n');
        try {
            ensureInitialized();
            b.append("Engine-Initialisierung: OK\n");
        } catch (Throwable e) {
            lastError = unwrap(e);
            b.append("Engine-Initialisierung: FEHLER: ").append(describe(lastError)).append('\n');
        }
        Throwable le = lastError;
        if (le != null) b.append("Letzter Sprachfehler: ").append(describe(le)).append('\n');
        return b.toString();
    }

    public String getLastErrorSummary() {
        Throwable e = lastError;
        return e == null ? "Kein Fehler gespeichert" : describe(e);
    }

    public void speak(String text, Listener listener) {
        if (text == null || text.trim().isEmpty()) return;
        if (!isModelInstalled()) {
            IllegalStateException e = new IllegalStateException("Supertonic model is not installed");
            lastError = e;
            listener.onError(e);
            showDeviceError(e);
            return;
        }

        new Thread(() -> {
            try {
                listener.onPreparing();
                OfflineTts tts = ensureInitialized();
                GenerationConfig config = createGenerationConfig();
                GeneratedAudio audio = tts.generateWithConfig(text, config);
                float[] samples = audio.getSamples();
                int sampleRate = audio.getSampleRate();
                if (samples == null || samples.length == 0 || sampleRate <= 0)
                    throw new IllegalStateException("Supertonic returned no audio");
                lastError = null;
                listener.onSpeaking();
                playBlocking(samples, sampleRate);
                listener.onDone();
            } catch (Throwable error) {
                SpeechAudioBus.reset();
                lastError = unwrap(error);
                listener.onError(lastError);
                showDeviceError(lastError);
            }
        }, "celin-local-tts").start();
    }

    private void showDeviceError(Throwable error) {
        final String message = "Celine TTS Diagnose: " + describe(error);
        new Handler(Looper.getMainLooper()).postDelayed(
                () -> Toast.makeText(context, message, Toast.LENGTH_LONG).show(), 900L);
    }

    private synchronized OfflineTts ensureInitialized() {
        if (offlineTts != null) return offlineTts;
        File dir = getModelDir();

        OfflineTtsSupertonicModelConfig supertonic = OfflineTtsSupertonicModelConfig.builder()
                .setDurationPredictor(new File(dir, "duration_predictor.int8.onnx").getAbsolutePath())
                .setTextEncoder(new File(dir, "text_encoder.int8.onnx").getAbsolutePath())
                .setVectorEstimator(new File(dir, "vector_estimator.int8.onnx").getAbsolutePath())
                .setVocoder(new File(dir, "vocoder.int8.onnx").getAbsolutePath())
                .setTtsJson(new File(dir, "tts.json").getAbsolutePath())
                .setUnicodeIndexer(new File(dir, "unicode_indexer.bin").getAbsolutePath())
                .setVoiceStyle(new File(dir, "voice.bin").getAbsolutePath())
                .build();

        OfflineTtsModelConfig model = OfflineTtsModelConfig.builder()
                .setSupertonic(supertonic)
                .setNumThreads(3)
                .setDebug(false)
                .build();

        OfflineTtsConfig config = OfflineTtsConfig.builder().setModel(model).build();
        offlineTts = new OfflineTts(config);
        return offlineTts;
    }

    private GenerationConfig createGenerationConfig() {
        GenerationConfig config = new GenerationConfig();
        config.setSid(speakerId);
        config.setSpeed(speed);
        config.setNumSteps(numSteps);
        Map<String, String> extra = new HashMap<>();
        extra.put("lang", "de");
        config.setExtra(extra);
        return config;
    }

    private void playBlocking(float[] samples, int sampleRate) {
        int min = AudioTrack.getMinBufferSize(sampleRate,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT);
        int buffer = Math.max(min, Math.min(samples.length * 4, 1024 * 1024));
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
        AudioFormat format = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build();
        AudioTrack track = new AudioTrack(attrs, format, buffer,
                AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
        activeTrack = track;
        float envelope = 0f;
        try {
            track.play();
            int pos = 0;
            // ~30 visual updates/sec: responsive mouth without overloading the UI thread.
            final int visualChunk = Math.max(384, sampleRate / 30);
            while (pos < samples.length) {
                int count = Math.min(visualChunk, samples.length - pos);
                float level = normalizedRms(samples, pos, count);
                envelope = envelope * 0.24f + level * 0.76f;
                SpeechAudioBus.publish(envelope);
                SpeechAudioBus.publishViseme(SpeechVisemeAnalyzer.analyze(samples, pos, count, sampleRate));
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
        for (int i = offset; i < end; i++) { float v = samples[i]; sum += v * v; }
        int n = Math.max(1, end - offset);
        double rms = Math.sqrt(sum / n);
        // More sensitive than the prototype so normal conversational speech visibly drives the mouth.
        float level = (float) ((rms - 0.0035) / 0.065);
        return Math.max(0f, Math.min(1f, level));
    }

    public synchronized void release() {
        SpeechAudioBus.reset();
        AudioTrack track = activeTrack; activeTrack = null;
        if (track != null) {
            try { track.pause(); } catch (Exception ignored) {}
            try { track.flush(); } catch (Exception ignored) {}
            try { track.stop(); } catch (Exception ignored) {}
            try { track.release(); } catch (Exception ignored) {}
        }
        OfflineTts tts = offlineTts; offlineTts = null;
        if (tts != null) try { tts.release(); } catch (Exception ignored) {}
    }

    private static Throwable unwrap(Throwable t) {
        Throwable cause = t == null ? null : t.getCause();
        return cause != null ? cause : t;
    }

    private static String describe(Throwable t) {
        if (t == null) return "unbekannt";
        String m = t.getMessage();
        if (m == null || m.trim().isEmpty()) m = t.getClass().getName();
        else m = t.getClass().getSimpleName() + ": " + m;
        Throwable c = t.getCause();
        if (c != null && c != t) {
            String cm = c.getMessage();
            if (cm == null || cm.trim().isEmpty()) cm = c.getClass().getName();
            else cm = c.getClass().getSimpleName() + ": " + cm;
            m += " | Ursache: " + cm;
        }
        return m;
    }
}
