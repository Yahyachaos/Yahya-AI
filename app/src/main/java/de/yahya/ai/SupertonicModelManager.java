package de.yahya.ai;

import android.content.Context;
import android.os.StatFs;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

/** Downloads and installs the official Supertonic 3 INT8 model atomically. */
public final class SupertonicModelManager {
    public interface Listener {
        void onStatus(String text);
        void onProgress(int percent);
        void onInstalled();
        void onError(Throwable error);
    }

    public static final String ARCHIVE_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/" +
            "sherpa-onnx-supertonic-3-tts-int8-2026-05-11.tar.bz2";

    private static final long MIN_FREE_BYTES = 320L * 1024L * 1024L;
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

    public SupertonicModelManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public File getModelDir() {
        return new File(new File(context.getFilesDir(), "tts"), LocalNeuralTtsEngine.MODEL_DIR_NAME);
    }

    public boolean isInstalled() {
        File dir = getModelDir();
        if (!dir.isDirectory()) return false;
        for (String name : REQUIRED) {
            File f = new File(dir, name);
            if (!f.isFile() || f.length() <= 0) return false;
        }
        return true;
    }

    public void install(Listener listener) {
        if (isInstalled()) {
            listener.onInstalled();
            return;
        }
        new Thread(() -> {
            File archive = null;
            File staging = null;
            try {
                ensureFreeSpace();
                listener.onStatus("Celines Offline-Stimme wird heruntergeladen …");
                archive = new File(context.getCacheDir(), "supertonic3-model.tar.bz2");
                download(archive, listener);

                listener.onStatus("Sprachmodell wird geprüft und eingerichtet …");
                File parent = getModelDir().getParentFile();
                if (parent == null) throw new IOException("Model parent missing");
                if (!parent.exists() && !parent.mkdirs()) throw new IOException("Cannot create TTS directory");
                staging = new File(parent, LocalNeuralTtsEngine.MODEL_DIR_NAME + ".installing");
                deleteRecursively(staging);
                if (!staging.mkdirs()) throw new IOException("Cannot create staging directory");
                extractRequired(archive, staging);
                verify(staging);

                File target = getModelDir();
                deleteRecursively(target);
                if (!staging.renameTo(target)) throw new IOException("Cannot activate model directory");
                staging = null;
                listener.onProgress(100);
                listener.onInstalled();
            } catch (Throwable error) {
                listener.onError(error);
            } finally {
                if (archive != null) archive.delete();
                if (staging != null) deleteRecursively(staging);
            }
        }, "celin-model-install").start();
    }

    private void ensureFreeSpace() throws IOException {
        StatFs stat = new StatFs(context.getFilesDir().getAbsolutePath());
        long free = stat.getAvailableBytes();
        if (free < MIN_FREE_BYTES) {
            throw new IOException("Zu wenig freier Speicher. Für die lokale Stimme werden mindestens 320 MB frei benötigt.");
        }
    }

    private void download(File out, Listener listener) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(ARCHIVE_URL).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "YahyaAI-Celin/1.0");
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) throw new IOException("Model download HTTP " + code);
        long total = c.getContentLengthLong();
        try (InputStream in = new BufferedInputStream(c.getInputStream());
             OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
            byte[] buf = new byte[64 * 1024];
            long done = 0;
            int last = -1;
            int n;
            while ((n = in.read(buf)) >= 0) {
                if (n == 0) continue;
                os.write(buf, 0, n);
                done += n;
                if (total > 0) {
                    int pct = (int) Math.min(95, (done * 95L) / total);
                    if (pct != last) {
                        last = pct;
                        listener.onProgress(pct);
                    }
                }
            }
        } finally {
            c.disconnect();
        }
        if (!out.isFile() || out.length() < 1024 * 1024) throw new IOException("Model archive is incomplete");
    }

    private void extractRequired(File archive, File staging) throws IOException {
        Set<String> needed = new HashSet<>();
        for (String x : REQUIRED) needed.add(x);
        try (InputStream fis = new BufferedInputStream(new FileInputStream(archive));
             BZip2CompressorInputStream bz = new BZip2CompressorInputStream(fis, true);
             TarArchiveInputStream tar = new TarArchiveInputStream(bz)) {
            TarArchiveEntry e;
            byte[] buf = new byte[64 * 1024];
            while ((e = tar.getNextTarEntry()) != null) {
                if (!e.isFile()) continue;
                String name = new File(e.getName()).getName();
                if (!needed.contains(name)) continue;
                File dest = new File(staging, name);
                try (OutputStream out = new BufferedOutputStream(new FileOutputStream(dest))) {
                    int n;
                    while ((n = tar.read(buf)) >= 0) {
                        if (n > 0) out.write(buf, 0, n);
                    }
                }
                needed.remove(name);
            }
        }
        if (!needed.isEmpty()) throw new IOException("Model archive misses required files: " + needed);
    }

    private void verify(File dir) throws IOException {
        for (String name : REQUIRED) {
            File f = new File(dir, name);
            if (!f.isFile() || f.length() <= 0) throw new IOException("Invalid model file: " + name);
        }
    }

    public void remove() {
        deleteRecursively(getModelDir());
    }

    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) deleteRecursively(c);
        }
        f.delete();
    }
}
