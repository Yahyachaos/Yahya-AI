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

/** Imports a compatible Celine GLB into the app's private on-device model slot. */
public final class CelineModelImportActivity extends Activity {
    private static final long MAX_GLB_BYTES = 220L * 1024L * 1024L;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Uri uri = getIntent() == null ? null : getIntent().getData();
        Celine3DDiagnostics.record(this, "IMP-200", "Import gestartet",
                uri == null ? "URI fehlt" : String.valueOf(uri));
        if (uri == null) {
            Celine3DDiagnostics.record(this, "IMP-299", "Import abgebrochen", "Keine 3D-Datei/URI erhalten");
            finishToMain(false, "Keine 3D-Datei gefunden.");
            return;
        }
        new Thread(() -> importModel(uri), "celine-3d-import").start();
    }

    private void importModel(Uri uri) {
        File target = Celine3DView.importedModelFile(this);
        File parent = target.getParentFile();
        if (parent != null) parent.mkdirs();
        File temp = new File(parent, "celine.glb.tmp");
        File bodyFallback = new File(parent, "celine.body.tmp");
        boolean ok = false;
        String message;
        Celine3DDiagnostics.record(this, "IMP-201", "Ziel vorbereitet",
                "target=" + target.getAbsolutePath() + " · vorhanden=" + target.isFile() + " · alt=" + (target.isFile() ? target.length() : 0L));
        try (InputStream raw = getContentResolver().openInputStream(uri)) {
            if (raw == null) throw new IllegalStateException("Datei kann nicht geöffnet werden.");
            BufferedInputStream in = new BufferedInputStream(raw);
            in.mark(8);
            byte[] header = new byte[4];
            int h = in.read(header);
            in.reset();
            if (h != 4) throw new IllegalArgumentException("Datei ist leer oder beschädigt.");

            temp.delete();
            bodyFallback.delete();
            boolean facial;
            if (header[0]=='g' && header[1]=='l' && header[2]=='T' && header[3]=='F') {
                Celine3DDiagnostics.record(this, "IMP-210", "Dateityp erkannt", "GLB");
                copyGlb(in, temp);
                Celine3DDiagnostics.record(this, "IMP-211", "GLB kopiert", temp.length() + " Bytes");
                facial = CelineGlbValidator.requireProductionCeline(temp);
                Celine3DDiagnostics.record(this, "IMP-212", "Validator bestanden",
                        facial ? "Facial-Rig vollständig" : "Meshy Body-Rig kompatibel");
            } else if (header[0]=='P' && header[1]=='K') {
                Celine3DDiagnostics.record(this, "IMP-220", "Dateityp erkannt", "ZIP");
                facial = extractBestCelineGlb(in, temp, bodyFallback);
                Celine3DDiagnostics.record(this, "IMP-223", "ZIP-Auswahl abgeschlossen",
                        (facial ? "Facial-Rig" : "Meshy Body-Rig") + " · temp=" + temp.length() + " Bytes");
            } else {
                throw new IllegalArgumentException("Bitte eine Celine-GLB oder das Meshy-ZIP auswählen.");
            }

            if (!temp.isFile() || temp.length() < 100_000L) {
                throw new IllegalStateException("Validierte GLB fehlt nach Import oder ist zu klein: " + temp.length());
            }

            // Do not touch the last working avatar until the new file has passed all checks.
            if (target.exists() && !target.delete()) {
                throw new IllegalStateException("Altes Celine-Modell konnte nicht ersetzt werden.");
            }
            if (!temp.renameTo(target)) {
                throw new IllegalStateException("Celine-Modell konnte nicht gespeichert werden.");
            }
            if (!target.isFile() || target.length() < 100_000L) {
                throw new IllegalStateException("Gespeicherte celine.glb ist nach rename ungültig: " + target.length());
            }

            File failed = new File(parent, "celine.failed.glb");
            if (failed.exists()) failed.delete();
            getSharedPreferences("yahya_ai", MODE_PRIVATE).edit()
                    .putBoolean("celine_3d_load_in_progress", false)
                    .putBoolean("celine_3d_facial_morphs", facial)
                    .commit();

            ok = true;
            Celine3DDiagnostics.record(this, "IMP-250", "Import ERFOLGREICH",
                    "celine.glb=" + target.length() + " Bytes · " + (facial ? "Facial" : "Body-Rig"));
            if (facial) {
                message = "Celines vollständiger 3D-Gesichtsavatar wurde geprüft und importiert. Yahya AI startet jetzt neu.";
            } else {
                message = "Celines Meshy-3D-Modell wurde importiert. Körper- und Kopfbewegungen sind jetzt aktiv. Für echte Lippen- und Blinzelanimationen kann später zusätzlich ein Facial-Rig verwendet werden.";
            }
        } catch (Throwable e) {
            temp.delete();
            bodyFallback.delete();
            Celine3DDiagnostics.error(this, "IMP-299", "Import FEHLER", e);
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

    /** Scans every GLB in a ZIP and keeps the best compatible Celine model. */
    private boolean extractBestCelineGlb(InputStream input, File target, File bodyFallback) throws Exception {
        String lastReason = null;
        boolean sawGlb = false;
        boolean haveBodyFallback = false;
        int glbIndex = 0;
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName().toLowerCase();
                if (!name.endsWith(".glb")) continue;
                sawGlb = true;
                glbIndex++;
                Celine3DDiagnostics.record(this, "IMP-221", "ZIP-GLB gefunden", "#" + glbIndex + " " + entry.getName());
                target.delete();
                try (FileOutputStream out = new FileOutputStream(target)) {
                    copyLimited(zip, out, MAX_GLB_BYTES);
                }
                try {
                    boolean facial = CelineGlbValidator.requireProductionCeline(target);
                    Celine3DDiagnostics.record(this, "IMP-222", "ZIP-GLB kompatibel",
                            entry.getName() + " · " + target.length() + " Bytes · facial=" + facial);
                    if (facial) {
                        bodyFallback.delete();
                        return true;
                    }
                    if (!haveBodyFallback) {
                        bodyFallback.delete();
                        if (!target.renameTo(bodyFallback)) {
                            throw new IllegalStateException("Kompatibles Meshy-Modell konnte nicht zwischengespeichert werden.");
                        }
                        haveBodyFallback = true;
                    } else {
                        target.delete();
                    }
                } catch (Throwable incompatible) {
                    lastReason = incompatible.getMessage();
                    Celine3DDiagnostics.error(this, "IMP-229", "ZIP-GLB verworfen: " + entry.getName(), incompatible);
                    target.delete();
                }
            }
        }

        if (haveBodyFallback && bodyFallback.isFile()) {
            target.delete();
            if (!bodyFallback.renameTo(target)) {
                throw new IllegalStateException("Kompatibles Meshy-Modell konnte nicht übernommen werden.");
            }
            return false;
        }
        if (!sawGlb) throw new IllegalArgumentException("Im ZIP wurde keine GLB-Datei gefunden.");
        if (lastReason != null && !lastReason.trim().isEmpty()) throw new IllegalArgumentException(lastReason);
        throw new IllegalArgumentException("Im ZIP wurde kein kompatibles Celine-Modell gefunden.");
    }

    private static void copyLimited(InputStream in, FileOutputStream out, long maxBytes) throws Exception {
        byte[] buf = new byte[64 * 1024];
        long total = 0L;
        int n;
        while ((n = in.read(buf)) >= 0) {
            total += n;
            if (total > maxBytes) throw new IllegalArgumentException("Die 3D-Datei ist zu groß. Maximal 220 MB werden unterstützt.");
            out.write(buf, 0, n);
        }
        out.flush();
    }

    private void finishToMain(boolean ok, String message) {
        Celine3DDiagnostics.record(this, ok ? "IMP-260" : "IMP-298",
                ok ? "App-Neustart nach Import" : "Zurück zur App nach Importfehler", message);
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
