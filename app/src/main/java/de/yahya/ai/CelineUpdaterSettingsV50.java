package de.yahya.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import java.lang.reflect.Method;

/**
 * Keeps the v47 updater logic intact while moving its entry point off the crowded HOME screen.
 * The original updater button is detached but retained as the tested action target, so download,
 * release checking and install behaviour stay unchanged. The gear now opens a compact settings
 * hub with the current app version and an obvious Update prüfen action; the existing full settings
 * dialog remains available via Weitere Einstellungen.
 */
final class CelineUpdaterSettingsV50 {
    private static final String UPDATE_TAG = "v47-update-button";
    private static final String GEAR_TAG = "v50-settings-gear";
    private static Button updaterAction;

    private CelineUpdaterSettingsV50() {}

    static void install(Activity activity, View decor) {
        if (activity == null || decor == null) return;

        View old = findTagged(decor, UPDATE_TAG);
        if (old instanceof Button) {
            updaterAction = (Button) old;
            if (old.getParent() instanceof ViewGroup) {
                ((ViewGroup) old.getParent()).removeView(old);
            }
        }

        Button gear = findButtonByText(decor, "⚙");
        if (gear == null) return;
        if (GEAR_TAG.equals(gear.getTag())) return;
        gear.setTag(GEAR_TAG);
        gear.setContentDescription("Einstellungen");
        gear.setOnClickListener(v -> showSettingsHub(activity));
    }

    private static void showSettingsHub(Activity activity) {
        long version = currentVersion(activity);
        String message = "App & Updates\nAktuelle Version: v" + version
                + "\n\nUpdates werden nur angeboten, wenn Build und Avatar-Sichtbarkeitstest bestanden sind.";

        new AlertDialog.Builder(activity)
                .setTitle("Yahya AI · Einstellungen")
                .setMessage(message)
                .setPositiveButton("Update prüfen", (d, w) -> {
                    Button action = updaterAction;
                    if (action != null) action.performClick();
                    else {
                        View decor = activity.getWindow().getDecorView();
                        CelineUpdaterV47.install(activity, decor);
                        View fresh = findTagged(decor, UPDATE_TAG);
                        if (fresh instanceof Button) {
                            updaterAction = (Button) fresh;
                            if (fresh.getParent() instanceof ViewGroup) ((ViewGroup) fresh.getParent()).removeView(fresh);
                            updaterAction.performClick();
                        }
                    }
                })
                .setNeutralButton("Weitere Einstellungen", (d, w) -> openOriginalSettings(activity))
                .setNegativeButton("Schließen", null)
                .show();
    }

    private static void openOriginalSettings(Activity activity) {
        try {
            Method m = activity.getClass().getDeclaredMethod("showSettings");
            m.setAccessible(true);
            m.invoke(activity);
        } catch (Throwable e) {
            new AlertDialog.Builder(activity)
                    .setTitle("Einstellungen")
                    .setMessage("Die erweiterten Einstellungen konnten nicht geöffnet werden.")
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private static long currentVersion(Activity activity) {
        try {
            PackageInfo p = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return p.getLongVersionCode();
            return p.versionCode;
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    private static Button findButtonByText(View root, String text) {
        if (root instanceof Button) {
            CharSequence t = ((Button) root).getText();
            if (t != null && text.equals(t.toString())) return (Button) root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                Button b = findButtonByText(g.getChildAt(i), text);
                if (b != null) return b;
            }
        }
        return null;
    }

    private static View findTagged(View root, String tag) {
        if (root == null) return null;
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
}
