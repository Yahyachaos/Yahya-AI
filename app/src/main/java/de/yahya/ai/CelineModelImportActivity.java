package de.yahya.ai;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Imports Celine's production GLB. The model must include the facial morph rig used by the app. */
public final class CelineModelImportActivity extends Activity {
    private static final String[] REQUIRED_FACE_TARGETS = new String[]{
            "jawOpen", "mouthWide", "mouthRound", "mouthLabial",
            "blinkLeft", "blinkRight", "smile"
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Uri uri = getIntent() == null ? null : getIntent().getData();
        if (uri == null) { finishToMain(false, "Keine 3D-Datei gefunden."); return; }
        new Thread(() -> importModel(uri)).start();
    }

    private void importModel(Uri uri) {
        File target = Celine3DView.importedModelFile(this);
        File parent = target.getParentFile();
        if (parent != null) parent.mkdirs();
        File temp = new File(parent, "celine.glb.tmp");
        File fallback = new File(parent, "celine_candidate.glb.tmp");
        boolean ok = false;
        String message;
        try (InputStream raw = getContentResolver().openInputStream(uri)) {
            if (raw == null) throw new IllegalStateException("Datei kann nicht geöffnet werden.");
            BufferedInputStream in = new BufferedInputStream(raw);
            in.mark(8);
            byte[] header = new byte[4];
            int h = in.read(header);
            in.reset();
            if (h != 4) throw new IllegalArgumentException("Datei ist leer oder beschädigt.");

            if (header[0]=='g' && header[1]=='l' && header[2]=='T' && header[3]=='F') {
                copyGlb(in, temp);
                validateProductionGlb(temp);
            } else if (header[0]=='P' && header[1]=='K') {
                extractCompatibleGlb(in, temp, fallback);
            } else {
                throw new IllegalArgumentException("Bitte Celines .glb-Datei oder ein ZIP mit einer kompatiblen GLB auswählen.");
            }

            if (temp.length() < 100_000) throw new IllegalArgumentException("Die gefundene GLB-Datei ist unerwartet klein.");
            if (target.exists() && !target.delete()) throw new IllegalStateException("Altes Celine-Modell konnte nicht ersetzt werden.");
            if (!temp.renameTo(target)) throw new IllegalStateException("Celine-Modell konnte nicht gespeichert werden.");
            fallback.delete();

            File failed = new File(parent, "celine.failed.glb");
            if (failed.exists()) failed.delete();
            getSharedPreferences("yahya_ai", MODE_PRIVATE).edit()
                    .putBoolean("celine_3d_load_in_progress", false)
                    .commit();

            ok = true;
            message = "3D-Celin mit Gesichts-Rig importiert. Yahya AI startet jetzt neu.";
        } catch (Throwable e) {
            temp.delete(); fallback.delete();
            message = "Import fehlgeschlagen: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        final boolean result = ok;
        final String text = message;
        runOnUiThread(() -> finishToMain(result, text));
    }

    private static void copyGlb(InputStream in, File target) throws Exception {
        try (FileOutputStream out = new FileOutputStream(target)) { copy(in, out); }
        validateGlb(target);
    }

    /**
     * A Meshy/Mixamo ZIP can contain several GLBs. Do not pick by filename alone: only accept
     * the model that actually contains Celine's seven facial morph targets. This prevents the
     * raw biped export from reaching native gltfio and also guarantees lip-sync/blinks can work.
     */
    private static void extractCompatibleGlb(InputStream input, File target, File candidate) throws Exception {
        String lastReason = null;
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName().toLowerCase();
                if (!name.endsWith(".glb")) continue;

                if (candidate.exists()) candidate.delete();
                try (FileOutputStream out = new FileOutputStream(candidate)) { copy(zip, out); }
                try {
                    validateProductionGlb(candidate);
                    if (target.exists()) target.delete();
                    if (!candidate.renameTo(target)) throw new IllegalStateException("Kompatible GLB konnte nicht übernommen werden.");
                    return;
                } catch (IllegalArgumentException incompatible) {
                    lastReason = incompatible.getMessage();
                    candidate.delete();
                }
            }
        }
        if (lastReason != null) throw new IllegalArgumentException(lastReason);
        throw new IllegalArgumentException("Im ZIP wurde keine kompatible Celine-GLB gefunden.");
    }

    private static void validateProductionGlb(File file) throws Exception {
        validateGlb(file);
        String json = readJsonChunk(file);
        if (!json.contains("\"char1\"") || !json.contains("\"Armature\"")) {
            throw new IllegalArgumentException("Das Modell enthält nicht Celines erwartetes Körper-Rig.");
        }
        for (String target : REQUIRED_FACE_TARGETS) {
            if (!json.contains("\"" + target + "\"")) {
                throw new IllegalArgumentException("Diese Meshy-Datei hat noch kein Gesichts-Rig. Bitte die Datei celine_facial_v1.glb auswählen.");
            }
        }
    }

    private static String readJsonChunk(File file) throws Exception {
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] header = new byte[12];
            if (readFully(in, header) != header.length) throw new IllegalArgumentException("GLB-Header ist unvollständig.");
            byte[] chunkHeader = new byte[8];
            if (readFully(in, chunkHeader) != chunkHeader.length) throw new IllegalArgumentException("GLB enthält keinen JSON-Block.");
            int length = littleEndianInt(chunkHeader, 0);
            int type = littleEndianInt(chunkHeader, 4);
            if (type != 0x4E4F534A || length <= 0 || length > 16 * 1024 * 1024) {
                throw new IllegalArgumentException("GLB enthält keinen gültigen JSON-Block.");
            }
            byte[] json = new byte[length];
            if (readFully(in, json) != length) throw new IllegalArgumentException("GLB-JSON ist unvollständig.");
            return new String(json, StandardCharsets.UTF_8);
        }
    }

    private static int readFully(InputStream in, byte[] data) throws Exception {
        int off = 0;
        while (off < data.length) {
            int n = in.read(data, off, data.length - off);
            if (n < 0) break;
            off += n;
        }
        return off;
    }

    private static int littleEndianInt(byte[] b, int off) {
        return (b[off] & 0xff) | ((b[off+1] & 0xff) << 8) | ((b[off+2] & 0xff) << 16) | ((b[off+3] & 0xff) << 24);
    }

    private static void validateGlb(File file) throws Exception {
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] h = new byte[4];
            if (in.read(h) != 4 || h[0] != 'g' || h[1] != 'l' || h[2] != 'T' || h[3] != 'F') {
                throw new IllegalArgumentException("Gefundene Datei ist keine gültige GLB.");
            }
        }
    }

    private static void copy(InputStream in, FileOutputStream out) throws Exception {
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        out.flush();
    }

    private void finishToMain(boolean ok, String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        if (ok) {
            Intent restart = Intent.makeRestartActivityTask(new ComponentName(this, MainActivity.class));
            startActivity(restart);
        } else {
            Intent main = new Intent(this, MainActivity.class);
            main.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(main);
        }
        finish();
    }
}
