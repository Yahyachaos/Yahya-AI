package de.yahya.ai;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Imports only a production-ready Celine GLB into the app's private on-device model slot. */
public final class CelineModelImportActivity extends Activity {
    private static final long MAX_GLB_BYTES = 220L * 1024L * 1024L;

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

            temp.delete();
            if (header[0]=='g' && header[1]=='l' && header[2]=='T' && header[3]=='F') {
                copyGlb(in, temp);
                CelineGlbValidator.requireProductionCeline(temp);
            } else if (header[0]=='P' && header[1]=='K') {
                extractCompatibleCelineGlb(in, temp);
                CelineGlbValidator.requireProductionCeline(temp);
            } else {
                throw new IllegalArgumentException("Bitte celine_facial_v1.glb auswählen. ZIP-Dateien werden nur akzeptiert, wenn sie genau dieses kompatible Celine-Modell enthalten.");
            }

            // Do not touch the last working avatar until the new file has passed all checks.
            if (target.exists() && !target.delete()) {
                throw new IllegalStateException("Altes Celine-Modell konnte nicht ersetzt werden.");
            }
            if (!temp.renameTo(target)) {
                throw new IllegalStateException("Celine-Modell konnte nicht gespeichert werden.");
            }

            File failed = new File(parent, "celine.failed.glb");
            if (failed.exists()) failed.delete();
            getSharedPreferences("yahya_ai", MODE_PRIVATE).edit()
                    .putBoolean("celine_3d_load_in_progress", false)
                    .commit();

            ok = true;
            message = "Celines Gesichts-Avatar wurde geprüft und importiert. Yahya AI startet jetzt neu.";
        } catch (Throwable e) {
            temp.delete();
            message = "Import abgelehnt: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        final boolean result = ok;
        final String text = message;
        runOnUiThread(() -> finishToMain(result, text));
    }

    private static void copyGlb(InputStream in, File target) throws Exception {
        try (FileOutputStream out = new FileOutputStream(target)) {
            copyLimited(in, out, MAX_GLB_BYTES);
        }
    }

    /**
     * Some file managers hand us the original Meshy ZIP. We inspect every GLB inside it, but only
     * keep one if it actually contains Celine's seven facial morphs and expected rig. This means the
     * old body-only Meshy export can no longer be installed accidentally and crash the 3D startup.
     */
    private static void extractCompatibleCelineGlb(InputStream input, File target) throws Exception {
        String lastReason = null;
        boolean sawGlb = false;
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName().toLowerCase();
                if (!name.endsWith(".glb")) continue;
                sawGlb = true;
                target.delete();
                try (FileOutputStream out = new FileOutputStream(target)) {
                    copyLimited(zip, out, MAX_GLB_BYTES);
                }
                try {
                    CelineGlbValidator.requireProductionCeline(target);
                    return;
                } catch (Throwable incompatible) {
                    lastReason = incompatible.getMessage();
                    target.delete();
                }
            }
        }
        if (!sawGlb) {
            throw new IllegalArgumentException("Im ZIP wurde keine GLB-Datei gefunden.");
        }
        if (lastReason != null && !lastReason.trim().isEmpty()) {
            throw new IllegalArgumentException(lastReason);
        }
        throw new IllegalArgumentException("Im ZIP wurde kein kompatibler Celine-Gesichtsavatar gefunden.");
    }

    private static void copyLimited(InputStream in, FileOutputStream out, long maxBytes) throws Exception {
        byte[] buf = new byte[64 * 1024];
        long total = 0L;
        int n;
        while ((n = in.read(buf)) >= 0) {
            total += n;
            if (total > maxBytes) {
                throw new IllegalArgumentException("Die 3D-Datei ist zu groß. Maximal 220 MB werden unterstützt.");
            }
            out.write(buf, 0, n);
        }
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
