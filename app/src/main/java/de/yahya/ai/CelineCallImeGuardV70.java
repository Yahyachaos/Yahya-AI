package de.yahya.ai;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import java.util.WeakHashMap;

/**
 * v70 CALL-only IME/focus guard.
 *
 * The normal v45 video-call code stays on the already proven camera/lifecycle path. This helper
 * activates only for the specific regression where HOME's composer still owns focus after the
 * software keyboard was dismissed and the user immediately opens CALL. In that one transition it
 * hides the inherited IME once the CALL overlay has actually taken focus. If Android restores the
 * composer focus when CALL is removed, the helper clears that restored focus and keeps the IME
 * hidden. Opening CALL from an unfocused HOME state does not touch focus or camera ownership.
 */
final class CelineCallImeGuardV70 {
    private static final String HOME_COMPOSER_DESC = "Celin Nachricht schreiben";
    private static final String CALL_TITLE = "Live mit Celin";
    private static final WeakHashMap<Activity, Controller> CONTROLLERS = new WeakHashMap<>();

    private CelineCallImeGuardV70() {}

    static void install(Activity activity, View decor) {
        if (!(activity instanceof MainActivity) || decor == null) return;
        Controller controller;
        synchronized (CONTROLLERS) {
            controller = CONTROLLERS.get(activity);
            if (controller == null) {
                controller = new Controller(activity, decor);
                CONTROLLERS.put(activity, controller);
            }
        }
        controller.install();
    }

    static void onDestroyed(Activity activity) {
        Controller controller;
        synchronized (CONTROLLERS) { controller = CONTROLLERS.remove(activity); }
        if (controller != null) controller.destroy();
    }

    private static final class Controller implements ViewTreeObserver.OnGlobalFocusChangeListener {
        final Activity activity;
        final View decor;
        boolean installed;
        boolean guardedCall;

        Controller(Activity activity, View decor) {
            this.activity = activity;
            this.decor = decor;
        }

        void install() {
            if (installed) return;
            ViewTreeObserver observer = decor.getViewTreeObserver();
            if (!observer.isAlive()) return;
            observer.addOnGlobalFocusChangeListener(this);
            installed = true;
        }

        void destroy() {
            if (!installed) return;
            ViewTreeObserver observer = decor.getViewTreeObserver();
            if (observer.isAlive()) observer.removeOnGlobalFocusChangeListener(this);
            installed = false;
            guardedCall = false;
        }

        @Override public void onGlobalFocusChanged(View oldFocus, View newFocus) {
            if (isHomeComposer(oldFocus) && isCallOverlayFocus(newFocus)) {
                guardedCall = true;
                hideIme(oldFocus);
                Celine3DDiagnostics.record(activity, "V70-151", "CALL uebernimmt HOME Eingabefokus",
                        "composerRetainedFocus=true · imeHideAfterOverlayFocus=true");
                return;
            }

            if (guardedCall && isHomeComposer(newFocus) && !containsText(decor, CALL_TITLE)) {
                guardedCall = false;
                hideIme(newFocus);
                final View restoredComposer = newFocus;
                restoredComposer.post(() -> {
                    if (restoredComposer.isFocused()) restoredComposer.clearFocus();
                });
                Celine3DDiagnostics.record(activity, "V70-152", "HOME Fokus nach CALL bereinigt",
                        "restoredComposerFocus=true · imeKeptHidden=true");
            }
        }

        private void hideIme(View tokenView) {
            try {
                InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm == null) return;
                View source = tokenView != null ? tokenView : decor;
                if (source.getWindowToken() != null) {
                    imm.hideSoftInputFromWindow(source.getWindowToken(), 0);
                }
            } catch (Throwable ignored) {}
        }
    }

    private static boolean isHomeComposer(View view) {
        if (!(view instanceof EditText)) return false;
        CharSequence description = view.getContentDescription();
        return description != null && HOME_COMPOSER_DESC.contentEquals(description);
    }

    private static boolean isCallOverlayFocus(View view) {
        return view != null && containsText(view, CALL_TITLE);
    }

    private static boolean containsText(View root, String needle) {
        if (root == null || needle == null) return false;
        if (root instanceof TextView) {
            CharSequence text = ((TextView) root).getText();
            if (text != null && text.toString().contains(needle)) return true;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (containsText(group.getChildAt(i), needle)) return true;
            }
        }
        return false;
    }
}
