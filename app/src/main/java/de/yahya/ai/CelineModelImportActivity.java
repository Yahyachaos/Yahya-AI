package de.yahya.ai;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Imports a GLB or Meshy ZIP as Celine's private on-device production model. */
public final class CelineModelImportActivity extends Activity {
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
        File fallback = new File(parent, "celine_fallback.glb.tmp");
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
            } else if (header[0]=='P' && header[1]=='K') {
                extractBestGlb(in, temp, fallback);
            } else {
                throw new IllegalArgumentException("Bitte eine .glb oder das originale Meshy-ZIP auswählen.");
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
            message = "3D-Celin importiert. Yahya AI wird jetzt neu gestartet und lädt den Avatar.";
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

    private static void extractBestGlb(InputStream input, File target, File fallback) throws Exception {
        boolean haveFallback = false;
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName().toLowerCase();
                if (!name.endsWith(".glb")) continue;
                boolean preferred = name.contains("character_output");
                File candidate = preferred ? target : fallback;
                try (FileOutputStream out = new FileOutputStream(candidate)) { copy(zip, out); }
                validateGlb(candidate);
                if (preferred) return;
                haveFallback = true;
            }
        }
        if (haveFallback && fallback.isFile()) {
            if (target.exists()) target.delete();
            if (!fallback.renameTo(target)) throw new IllegalStateException("GLB aus ZIP konnte nicht übernommen werden.");
            return;
        }
        throw new IllegalArgumentException("Im ZIP wurde keine GLB-Datei gefunden.");
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
            // A clean task restart guarantees MainActivity rebuilds the avatar host and sees
            // the newly imported private model immediately.
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
