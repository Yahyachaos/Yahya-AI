package de.yahya.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * v47 in-app updater.
 *
 * Stable builds are published as GitHub Releases by CI after build/smoke checks. The app checks
 * releases/latest, shows the release notes, downloads the APK through Android DownloadManager,
 * and hands the resulting content:// URI to the system package installer. Android still requires
 * the user's normal install confirmation; no silent or privileged installation is attempted.
 */
final class CelineUpdaterV47 {
    private static final String RELEASES_LATEST =
            "https://api.github.com/repos/Yahyachaos/Yahya-AI/releases/latest";
    private static final String PREFS = "yahya_updater_v47";
    private static final String K_DOWNLOAD_ID = "pending_download_id";
    private static final String K_VERSION = "pending_version";
    private static final String K_INSTALL_LAUNCHED = "install_launched";
    private static final String BUTTON_TAG = "v47-update-button";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private CelineUpdaterV47() {}

    static void install(Activity activity, View decor) {
        if (activity == null || decor == null) return;
        addButtonIfNeeded(activity, decor);
        resumePendingInstall(activity, decor);
    }

    private static void addButtonIfNeeded(Activity activity, View decor) {
        if (findTagged(decor, BUTTON_TAG) != null) return;
        Button call = findCallButton(decor);
        if (call == null || !(call.getParent() instanceof LinearLayout)) return;

        LinearLayout parent = (LinearLayout) call.getParent();
        Button update = new Button(activity);
        update.setTag(BUTTON_TAG);
        update.setText("⬆  Update prüfen");
        update.setTextColor(Color.WHITE);
        update.setTextSize(15);
        update.setAllCaps(false);
        update.setBackground(round(activity, Color.rgb(55, 59, 70), 24));
        update.setOnClickListener(v -> checkForUpdate(activity, update));

        int index = parent.indexOfChild(call);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 52));
        lp.topMargin = dp(activity, 7);
        parent.addView(update, Math.min(parent.getChildCount(), index + 1), lp);
    }

    private static void checkForUpdate(Activity activity, Button updateButton) {
        updateButton.setEnabled(false);
        updateButton.setText("Update wird geprüft …");
        new Thread(() -> {
            try {
                ReleaseInfo info = fetchLatestRelease();
                long current = currentVersion(activity);
                activity.runOnUiThread(() -> {
                    updateButton.setEnabled(true);
                    updateButton.setText("⬆  Update prüfen");
                    showReleaseDialog(activity, updateButton, current, info);
                });
            } catch (Throwable e) {
                activity.runOnUiThread(() -> {
                    updateButton.setEnabled(true);
                    updateButton.setText("⬆  Update prüfen");
                    new AlertDialog.Builder(activity)
                            .setTitle("Update konnte nicht geprüft werden")
                            .setMessage(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
                            .setPositiveButton("OK", null)
                            .show();
                });
            }
        }, "yahya-updater-check").start();
    }

    private static ReleaseInfo fetchLatestRelease() throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(RELEASES_LATEST).openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(12000);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", "Yahya-AI-Android-Updater");
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        int status = c.getResponseCode();
        if (status == 404) throw new IllegalStateException("Noch kein freigegebenes App-Update vorhanden.");
        if (status < 200 || status >= 300) throw new IllegalStateException("GitHub antwortet mit HTTP " + status + ".");

        StringBuilder text = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) text.append(line).append('\n');
        } finally {
            c.disconnect();
        }

        JSONObject root = new JSONObject(text.toString());
        String tag = root.optString("tag_name", "");
        long version = parseVersion(tag);
        String title = root.optString("name", tag);
        String body = root.optString("body", "");
        JSONArray assets = root.optJSONArray("assets");
        String apkUrl = null;
        String apkName = null;
        long apkSize = 0L;
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.optJSONObject(i);
                if (a == null) continue;
                String name = a.optString("name", "");
                if (name.toLowerCase(Locale.ROOT).endsWith(".apk")) {
                    apkUrl = a.optString("browser_download_url", null);
                    apkName = name;
                    apkSize = a.optLong("size", 0L);
                    break;
                }
            }
        }
        if (version <= 0) throw new IllegalStateException("Release-Version konnte nicht gelesen werden: " + tag);
        if (apkUrl == null || apkUrl.trim().isEmpty()) throw new IllegalStateException("Im neuesten Release wurde keine APK gefunden.");
        return new ReleaseInfo(version, title, body, apkUrl, apkName, apkSize);
    }

    private static void showReleaseDialog(Activity activity, Button button, long current, ReleaseInfo info) {
        StringBuilder msg = new StringBuilder();
        msg.append("Installiert: v").append(current)
                .append("\nVerfügbar: v").append(info.version);
        if (info.size > 0) msg.append(" · ").append(String.format(Locale.GERMANY, "%.1f MB", info.size / 1048576.0));
        if (!info.body.trim().isEmpty()) {
            String notes = info.body.trim();
            if (notes.length() > 2600) notes = notes.substring(0, 2600) + "…";
            msg.append("\n\nEntwicklungsstand:\n").append(notes);
        }

        AlertDialog.Builder b = new AlertDialog.Builder(activity)
                .setTitle(info.version > current ? "Yahya AI Update verfügbar" : "Yahya AI ist aktuell")
                .setMessage(msg.toString())
                .setNegativeButton("Schließen", null);

        if (info.version > current) {
            b.setPositiveButton("Update herunterladen", (d, w) ->
                    beginDownload(activity, button, info));
        }
        b.show();
    }

    private static void beginDownload(Activity activity, Button button, ReleaseInfo info) {
        try {
            DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager == null) throw new IllegalStateException("Android DownloadManager ist nicht verfügbar.");

            String safeName = (info.apkName == null || info.apkName.trim().isEmpty())
                    ? ("Yahya-AI-v" + info.version + ".apk") : info.apkName;
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(info.url));
            req.setTitle("Yahya AI v" + info.version);
            req.setDescription("App-Update wird heruntergeladen");
            req.setMimeType("application/vnd.android.package-archive");
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setAllowedOverMetered(true);
            req.setAllowedOverRoaming(false);
            req.setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, safeName);
            long id = manager.enqueue(req);

            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putLong(K_DOWNLOAD_ID, id)
                    .putLong(K_VERSION, info.version)
                    .putBoolean(K_INSTALL_LAUNCHED, false)
                    .apply();
            button.setText("Update wird geladen …");
            Toast.makeText(activity, "Update-Download gestartet.", Toast.LENGTH_SHORT).show();
            pollDownload(activity, button, id, info.version);
        } catch (Throwable e) {
            button.setText("⬆  Update prüfen");
            new AlertDialog.Builder(activity)
                    .setTitle("Download konnte nicht gestartet werden")
                    .setMessage(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private static void pollDownload(Activity activity, Button button, long id, long version) {
        MAIN.postDelayed(new Runnable() {
            @Override public void run() {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                DownloadState s = query(activity, id);
                if (s == null) {
                    button.setText("⬆  Update prüfen");
                    return;
                }
                if (s.status == DownloadManager.STATUS_SUCCESSFUL) {
                    button.setText("Update installieren");
                    installDownloaded(activity, id, version);
                    return;
                }
                if (s.status == DownloadManager.STATUS_FAILED) {
                    button.setText("⬆  Update prüfen");
                    Toast.makeText(activity, "Update-Download fehlgeschlagen.", Toast.LENGTH_LONG).show();
                    clearPending(activity);
                    return;
                }
                if (s.total > 0 && s.downloaded >= 0) {
                    int p = (int) Math.max(0, Math.min(100, (s.downloaded * 100L) / s.total));
                    button.setText("Update lädt · " + p + " %");
                } else {
                    button.setText("Update wird geladen …");
                }
                MAIN.postDelayed(this, 900L);
            }
        }, 700L);
    }

    private static void resumePendingInstall(Activity activity, View decor) {
        SharedPreferences p = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long id = p.getLong(K_DOWNLOAD_ID, -1L);
        long version = p.getLong(K_VERSION, -1L);
        if (id < 0 || version < 0) return;

        try {
            if (currentVersion(activity) >= version) {
                clearPending(activity);
                return;
            }
        } catch (Throwable ignored) {}

        DownloadState state = query(activity, id);
        if (state == null) return;
        Button button = findUpdateButton(decor);
        if (state.status == DownloadManager.STATUS_SUCCESSFUL) {
            if (button != null) button.setText("Update installieren");
            if (!p.getBoolean(K_INSTALL_LAUNCHED, false)) installDownloaded(activity, id, version);
        } else if (state.status == DownloadManager.STATUS_RUNNING || state.status == DownloadManager.STATUS_PENDING || state.status == DownloadManager.STATUS_PAUSED) {
            if (button != null) pollDownload(activity, button, id, version);
        } else if (state.status == DownloadManager.STATUS_FAILED) {
            clearPending(activity);
            if (button != null) button.setText("⬆  Update prüfen");
        }
    }

    private static void installDownloaded(Activity activity, long id, long version) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    !activity.getPackageManager().canRequestPackageInstalls()) {
                activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putBoolean(K_INSTALL_LAUNCHED, false).apply();
                new AlertDialog.Builder(activity)
                        .setTitle("Einmalige Android-Freigabe")
                        .setMessage("Damit Yahya AI seine eigenen Updates installieren darf, muss Android diese App einmal als Installationsquelle erlauben. Danach funktioniert der Update-Button direkt.")
                        .setPositiveButton("Freigabe öffnen", (d, w) -> {
                            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + activity.getPackageName()));
                            activity.startActivity(settings);
                        })
                        .setNegativeButton("Später", null)
                        .show();
                return;
            }

            DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            Uri uri = manager == null ? null : manager.getUriForDownloadedFile(id);
            if (uri == null) throw new IllegalStateException("Die heruntergeladene APK konnte nicht geöffnet werden.");

            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(uri, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putBoolean(K_INSTALL_LAUNCHED, true)
                    .putLong(K_VERSION, version)
                    .apply();
            activity.startActivity(install);
        } catch (Throwable e) {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putBoolean(K_INSTALL_LAUNCHED, false).apply();
            new AlertDialog.Builder(activity)
                    .setTitle("Update kann noch nicht installiert werden")
                    .setMessage(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private static DownloadState query(Activity activity, long id) {
        DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager == null) return null;
        android.database.Cursor c = null;
        try {
            c = manager.query(new DownloadManager.Query().setFilterById(id));
            if (c == null || !c.moveToFirst()) return null;
            int status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            long downloaded = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            long total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            return new DownloadState(status, downloaded, total);
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (c != null) c.close();
        }
    }

    private static long currentVersion(Activity activity) throws Exception {
        PackageInfo p = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return p.getLongVersionCode();
        return p.versionCode;
    }

    private static long parseVersion(String tag) {
        if (tag == null) return -1L;
        String digits = tag.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return -1L;
        try { return Long.parseLong(digits); } catch (Throwable ignored) { return -1L; }
    }

    private static void clearPending(Activity activity) {
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    private static Button findUpdateButton(View root) {
        View v = findTagged(root, BUTTON_TAG);
        return v instanceof Button ? (Button) v : null;
    }

    private static Button findCallButton(View root) {
        if (root instanceof Button) {
            CharSequence t = ((Button) root).getText();
            if (t != null && t.toString().contains("Mit Celin")) return (Button) root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                Button b = findCallButton(g.getChildAt(i));
                if (b != null) return b;
            }
        }
        return null;
    }

    private static View findTagged(View root, String tag) {
        if (tag.equals(root.getTag())) return root;
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                View v = findTagged(g.getChildAt(i), tag);
                if (v != null) return v;
            }
        }
        return null;
    }

    private static GradientDrawable round(Activity a, int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(a, radiusDp));
        return g;
    }

    private static int dp(Activity a, float value) {
        return Math.round(value * Math.max(1f, a.getResources().getDisplayMetrics().density));
    }

    private static final class ReleaseInfo {
        final long version;
        final String title;
        final String body;
        final String url;
        final String apkName;
        final long size;
        ReleaseInfo(long version, String title, String body, String url, String apkName, long size) {
            this.version = version;
            this.title = title;
            this.body = body == null ? "" : body;
            this.url = url;
            this.apkName = apkName;
            this.size = size;
        }
    }

    private static final class DownloadState {
        final int status;
        final long downloaded;
        final long total;
        DownloadState(int status, long downloaded, long total) {
            this.status = status;
            this.downloaded = downloaded;
            this.total = total;
        }
    }
}
