package de.yahya.ai;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Toast;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
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

    /* sherpa voice.bin is ordered F1..F5, M1..M5. F5 is the gentle female preset. */
    private static final int DEFAULT_SID = 4;
    private static final float DEFAULT_SPEED = 0.96f;
    private static final int DEFAULT_STEPS = 12;

    private final Context context;
    private volatile Object offlineTts;
    private volatile AudioTrack activeTrack;
    private volatile Throwable lastError;
    private volatile int speakerId = DEFAULT_SID;
    private volatile float speed = DEFAULT_SPEED;
    private volatile int numSteps = DEFAULT_STEPS;

    public LocalNeuralTtsEngine(Context context) { this.context = context.getApplicationContext(); }

    public File getModelDir() { return new File(new File(context.getFilesDir(), "tts"), MODEL_DIR_NAME); }

    /** Only the five female Supertonic presets are allowed for Celine. */
    public void setVoiceProfile(int sid, float newSpeed) {
        speakerId = Math.max(0, Math.min(4, sid));
        speed = Math.max(0.82f, Math.min(1.12f, newSpeed));
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
        b.append("Modelordner: ").append(getModelDir().getAbsolutePath()).append('\n');
        b.append("Modell vollständig: ").append(isModelInstalled() ? "JA" : "NEIN").append('\n');
        b.append("Celine female SID: ").append(speakerId).append(" | Speed: ").append(speed).append('\n');
        Throwable e = lastError;
        if (e != null) b.append("Letzter Sprachfehler: ").append(describe(e)).append('\n');
        return b.toString();
    }

    public String getLastErrorSummary() { return lastError == null ? "Kein Fehler gespeichert" : describe(lastError); }

    public void speak(String text, Listener listener) {
        if (text == null || text.trim().isEmpty()) return;
        if (!isModelInstalled()) {
            IllegalStateException e = new IllegalStateException("Supertonic model is not installed");
            lastError = e; listener.onError(e); showDeviceError(e); return;
        }
        new Thread(() -> {
            try {
                listener.onPreparing();
                Object tts = ensureInitialized();
                Object config = createGenerationConfig();
                Method generate = findMethod(tts.getClass(), "generateWithConfig", 2);
                Object audio = generate.invoke(tts, text, config);
                float[] samples = (float[]) audio.getClass().getMethod("getSamples").invoke(audio);
                int sampleRate = (Integer) audio.getClass().getMethod("getSampleRate").invoke(audio);
                if (samples == null || samples.length == 0 || sampleRate <= 0) throw new IllegalStateException("Supertonic returned no audio");
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
        new Handler(Looper.getMainLooper()).postDelayed(() -> Toast.makeText(context, message, Toast.LENGTH_LONG).show(), 900L);
    }

    private synchronized Object ensureInitialized() throws Exception {
        if (offlineTts != null) return offlineTts;
        File dir = getModelDir();
        Class<?> ttsKt = Class.forName("com.k2fsa.sherpa.onnx.TtsKt");
        Method factory = findMethod(ttsKt, "getOfflineTtsConfig", 20);
        Object config = factory.invoke(null,
                dir.getAbsolutePath(), "", "", "", "", "", "", "", "", "",
                Integer.valueOf(3), false, true,
                "duration_predictor.int8.onnx", "text_encoder.int8.onnx",
                "vector_estimator.int8.onnx", "vocoder.int8.onnx", "tts.json",
                "unicode_indexer.bin", "voice.bin");
        Class<?> offlineClass = Class.forName("com.k2fsa.sherpa.onnx.OfflineTts");
        Constructor<?> ctor = null;
        for (Constructor<?> c : offlineClass.getConstructors()) if (c.getParameterTypes().length == 2) { ctor = c; break; }
        if (ctor == null) throw new NoSuchMethodException("OfflineTts constructor");
        offlineTts = ctor.newInstance(null, config);
        return offlineTts;
    }

    private Object createGenerationConfig() throws Exception {
        Class<?> c = Class.forName("com.k2fsa.sherpa.onnx.GenerationConfig");
        Constructor<?> ctor = null;
        for (Constructor<?> x : c.getConstructors()) if (x.getParameterTypes().length == 8) { ctor = x; break; }
        if (ctor == null) throw new NoSuchMethodException("GenerationConfig constructor");
        Map<String, String> extra = new HashMap<>(); extra.put("lang", "de");
        return ctor.newInstance(0.16f, speed, speakerId, null, 0, null, numSteps, extra);
    }

    private void playBlocking(float[] samples, int sampleRate) {
        int min = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT);
        int buffer = Math.max(min, Math.min(samples.length * 4, 1024 * 1024));
        AudioAttributes attrs = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
        AudioFormat format = new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build();
        AudioTrack track = new AudioTrack(attrs, format, buffer, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
        activeTrack = track;
        float envelope = 0f;
        try {
            track.play();
            int pos = 0; final int visualChunk = Math.max(320, sampleRate / 36);
            while (pos < samples.length) {
                int count = Math.min(visualChunk, samples.length - pos);
                float level = normalizedRms(samples, pos, count);
                envelope = envelope * 0.18f + level * 0.82f;
                SpeechAudioBus.publish(envelope);
                SpeechAudioBus.publishViseme(SpeechVisemeAnalyzer.analyze(samples, pos, count, sampleRate));
                int written = track.write(samples, pos, count, AudioTrack.WRITE_BLOCKING);
                if (written < 0) throw new IllegalStateException("AudioTrack write failed: " + written);
                if (written == 0) {
                    SystemClock.sleep(4L);
                    continue;
                }
                pos += written;
            }

            // WRITE_BLOCKING only guarantees that PCM has been accepted by AudioTrack. It does not
            // guarantee that the speaker has already played the tail. Keep the track alive until its
            // playback head reaches the final frame so long answers cannot lose their last words.
            waitForPlaybackHead(track, samples.length, sampleRate);
            SpeechAudioBus.reset();
        } finally {
            SpeechAudioBus.reset();
            try { track.stop(); } catch (Exception ignored) {}
            try { track.release(); } catch (Exception ignored) {}
            if (activeTrack == track) activeTrack = null;
        }
    }

    private void waitForPlaybackHead(AudioTrack track, int expectedFrames, int sampleRate) {
        if (track == null || expectedFrames <= 0 || sampleRate <= 0) return;
        long expected = expectedFrames & 0xffffffffL;
        long audioMs = Math.max(1L, (expectedFrames * 1000L) / sampleRate);
        long deadline = SystemClock.elapsedRealtime() + audioMs + 2500L;
        long lastHead = -1L;
        long stalledSince = SystemClock.elapsedRealtime();

        while (SystemClock.elapsedRealtime() < deadline) {
            if (activeTrack != track) return;
            long head = ((long) track.getPlaybackHeadPosition()) & 0xffffffffL;
            if (head >= expected) return;
            if (head != lastHead) {
                lastHead = head;
                stalledSince = SystemClock.elapsedRealtime();
            } else if (SystemClock.elapsedRealtime() - stalledSince > 1200L && track.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                return;
            }
            SystemClock.sleep(12L);
        }
    }

    private static float normalizedRms(float[] samples, int offset, int count) {
        if (samples == null || count <= 0) return 0f;
        double sum=0.0; int end=Math.min(samples.length,offset+count);
        for(int i=offset;i<end;i++){float v=samples[i];sum+=v*v;}
        double rms=Math.sqrt(sum/Math.max(1,end-offset));
        return Math.max(0f,Math.min(1f,(float)((rms-0.003)/0.055)));
    }

    public synchronized void release() {
        SpeechAudioBus.reset();
        AudioTrack track=activeTrack; activeTrack=null;
        if(track!=null){try{track.pause();}catch(Exception ignored){} try{track.flush();}catch(Exception ignored){} try{track.stop();}catch(Exception ignored){} try{track.release();}catch(Exception ignored){}}
        Object tts=offlineTts; offlineTts=null;
        if(tts!=null) try{tts.getClass().getMethod("release").invoke(tts);}catch(Exception ignored){}
    }

    private static Method findMethod(Class<?> c,String name,int params)throws NoSuchMethodException{
        for(Method m:c.getMethods())if(m.getName().equals(name)&&m.getParameterTypes().length==params)return m;
        throw new NoSuchMethodException(c.getName()+"."+name+"/"+params);
    }
    private static Throwable unwrap(Throwable t){return t!=null&&t.getCause()!=null?t.getCause():t;}
    private static String describe(Throwable t){if(t==null)return"unbekannt";String m=t.getMessage();return m==null||m.trim().isEmpty()?t.getClass().getSimpleName():t.getClass().getSimpleName()+": "+m;}
}
