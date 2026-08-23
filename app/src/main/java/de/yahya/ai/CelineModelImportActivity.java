package de.yahya.ai;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Imports a user-selected GLB as Celine's private on-device production model. */
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
        boolean ok = false;
        String message;
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(temp)) {
            if (in == null) throw new IllegalStateException("Datei kann nicht geöffnet werden.");
            byte[] header = new byte[4];
            int h = in.read(header);
            if (h != 4 || header[0] != 'g' || header[1] != 'l' || header[2] != 'T' || header[3] != 'F') {
                throw new IllegalArgumentException("Das ist keine gültige GLB-Datei.");
            }
            out.write(header);
            byte[] buf = new byte[64 * 1024];
            int n;
            long total = 4;
            while ((n = in.read(buf)) >= 0) { out.write(buf, 0, n); total += n; }
            out.flush();
            if (total < 100_000) throw new IllegalArgumentException("Die GLB-Datei ist unerwartet klein.");
            if (target.exists() && !target.delete()) throw new IllegalStateException("Altes Celine-Modell konnte nicht ersetzt werden.");
            if (!temp.renameTo(target)) throw new IllegalStateException("Celine-Modell konnte nicht gespeichert werden.");
            ok = true;
            message = "3D-Celine importiert. Yahya AI startet jetzt mit dem neuen Modell.";
        } catch (Throwable e) {
            temp.delete();
            message = "Import fehlgeschlagen: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        final boolean result = ok;
        final String text = message;
        runOnUiThread(() -> finishToMain(result, text));
    }

    private void finishToMain(boolean ok, String message) {
        Toast.makeText(this, message, ok ? Toast.LENGTH_LONG : Toast.LENGTH_LONG).show();
        Intent main = new Intent(this, MainActivity.class);
        main.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(main);
        finish();
    }
}
