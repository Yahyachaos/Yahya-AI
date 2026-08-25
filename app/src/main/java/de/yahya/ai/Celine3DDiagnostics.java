package de.yahya.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Persistent device-side trace for Celine's import and Filament pipeline. */
public final class Celine3DDiagnostics {
    private static final String PREFS = "celine_3d_diagnostics";
    private static final String TAG = "Celine3D";
    private static final String K_CODE = "code";
    private static final String K_STAGE = "stage";
    private static final String K_DETAIL = "detail";
    private static final String K_TIME = "time";
    private static final String K_LOG = "log";
    private static final int MAX_LOG_CHARS = 30000;

    private Celine3DDiagnostics() {}

    public static synchronized void record(Context context, String code, String stage, String detail) {
        if (context == null) return;
        try {
            Context app = context.getApplicationContext();
            SharedPreferences p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String safeCode = clean(code, "D3D-???");
            String safeStage = clean(stage, "Unbekannter Schritt");
            String safeDetail = clean(detail, "-");
            String time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.GERMANY).format(new Date());
            String line = time + "  " + safeCode + "  " + safeStage + "  |  " + safeDetail;
            String old = p.getString(K_LOG, "");
            String next = old == null || old.isEmpty() ? line : old + "\n" + line;
            if (next.length() > MAX_LOG_CHARS) next = next.substring(next.length() - MAX_LOG_CHARS);
            p.edit()
                    .putString(K_CODE, safeCode)
                    .putString(K_STAGE, safeStage)
                    .putString(K_DETAIL, safeDetail)
                    .putString(K_TIME, time)
                    .putString(K_LOG, next)
                    .commit();
            Log.i(TAG, safeCode + "  " + safeStage + "  |  " + safeDetail);
        } catch (Throwable ignored) {}
    }

    public static void error(Context context, String code, String stage, Throwable error) {
        String detail;
        if (error == null) detail = "unbekannter Fehler";
        else {
            String m = error.getMessage();
            if (m == null || m.trim().isEmpty()) m = error.getClass().getSimpleName();
            detail = error.getClass().getSimpleName() + ": " + m;
        }
        record(context, code, stage, detail);
    }

    public static String shortStatus(Context context) {
        SharedPreferences p = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String code = p.getString(K_CODE, "D3D-000");
        String stage = p.getString(K_STAGE, "Noch keine Diagnose");
        return code + " · " + stage;
    }

    public static String report(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        StringBuilder out = new StringBuilder();
        out.append("CELINE 3D DIAGNOSE v60\n\n");
        out.append("Letzter Code: ").append(p.getString(K_CODE, "D3D-000")).append('\n');
        out.append("Schritt: ").append(p.getString(K_STAGE, "Noch keine Diagnose")).append('\n');
        out.append("Detail: ").append(p.getString(K_DETAIL, "-")).append('\n');
        out.append("Zeit: ").append(p.getString(K_TIME, "-")).append("\n\n");
        out.append(modelSnapshot(app)).append("\n\n");
        out.append("ABLAUF\n");
        String log = p.getString(K_LOG, "");
        out.append(log == null || log.isEmpty() ? "Noch kein Ablauf gespeichert." : log);
        return out.toString();
    }

    public static String modelSnapshot(Context context) {
        StringBuilder b = new StringBuilder("MODELLSTATUS\n");
        try {
            File imported = new File(new File(context.getFilesDir(), "models"), "celine.glb");
            b.append("Privatdatei: ")
                    .append(imported.isFile() ? "JA" : "NEIN")
                    .append(" · ").append(imported.isFile() ? imported.length() : 0L).append(" Bytes\n");
        } catch (Throwable e) {
            b.append("Privatdatei: FEHLER ").append(e.getClass().getSimpleName()).append('\n');
        }
        try (InputStream in = context.getAssets().open("models/celine.glb")) {
            b.append("APK-Asset: JA · verfügbar≈").append(in.available()).append(" Bytes");
        } catch (Throwable e) {
            b.append("APK-Asset: NEIN");
        }
        return b.toString();
    }

    public static synchronized void clear(Context context) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit();
        record(context, "D3D-000", "Diagnose zurückgesetzt", modelSnapshot(context));
    }

    private static String clean(String value, String fallback) {
        if (value == null) return fallback;
        String s = value.replace('\n', ' ').replace('\r', ' ').trim();
        return s.isEmpty() ? fallback : s;
    }
}
