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
 * HOME intentionally retains composer focus when the keyboard is dismissed. Android may restore
 * that focus again when the CALL overlay is removed. Focus-change callbacks proved unreliable for
 * this overlay lifecycle, so this guard observes the actual CALL layout instead: on CALL entry it
 * clears any inherited HOME composer focus and hides the IME; while CALL remains visible it keeps
 * that focus from being restored by later layout passes; on CALL exit it repeats that cleanup
 * immediately and on the next UI turns to catch Android's automatic focus restoration.
 *
 * The helper owns no camera, pose or CALL layout state.
 */
final class CelineCallImeGuardV70 {
    private static final String HOME_COMPOSER_DESC = "Celin Nachricht schreiben";
    private static final String CALL_TITLE = "●  Live mit Celin";
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

    private static final class Controller implements ViewTreeObserver.OnGlobalLayoutListener {
        final Activity activity;
        final View decor;
        boolean installed;
        boolean callVisible;
        int returnGeneration;

        Controller(Activity activity, View decor) {
            this.activity = activity;
            this.decor = decor;
        }

        void install() {
            if (installed) return;
            ViewTreeObserver observer = decor.getViewTreeObserver();
            if (!observer.isAlive()) return;
            observer.addOnGlobalLayoutListener(this);
            installed = true;
            callVisible = containsText(decor, CALL_TITLE);
            decor.post(this::evaluate);
        }

        void destroy() {
            returnGeneration++;
            EditText composer = findHomeComposer(decor);
            if (composer != null) composer.setFocusableInTouchMode(true);
            if (installed) {
                ViewTreeObserver observer = decor.getViewTreeObserver();
                if (observer.isAlive()) observer.removeOnGlobalLayoutListener(this);
            }
            installed = false;
            callVisible = false;
        }

        @Override public void onGlobalLayout() {
            evaluate();
        }

        void evaluate() {
            if (!installed || activity.isFinishing() || activity.isDestroyed()) return;
            boolean callNow = containsText(decor, CALL_TITLE);
            if (callNow == callVisible) {
                if (callNow) suppressComposerDuringCall();
                return;
            }

            callVisible = callNow;
            if (callNow) {
                returnGeneration++;
                boolean focused = suppressComposerDuringCall();
                decor.postDelayed(this::suppressComposerDuringCall, 80L);
                Celine3DDiagnostics.record(activity, "V70-151", "CALL uebernimmt HOME Eingabefokus",
                        "composerFocused=" + focused + " · layoutConfirmed=true · imeHideRequested=true");
                return;
            }

            final int generation = ++returnGeneration;
            restoreComposerAfterCall();
            boolean focused = clearComposerFocusAndHideIme();
            decor.post(() -> cleanupReturn(generation));
            decor.postDelayed(() -> cleanupReturn(generation), 80L);
            decor.postDelayed(() -> cleanupReturn(generation), 180L);
            Celine3DDiagnostics.record(activity, "V70-152", "HOME Fokus nach CALL bereinigt",
                    "focusedAtOverlayRemoval=" + focused + " · focusAnchoredAwayFromComposer=true · imeKeptHidden=true");
        }

        void cleanupReturn(int generation) {
            if (!installed || generation != returnGeneration || callVisible) return;
            restoreComposerAfterCall();
            clearComposerFocusAndHideIme();
        }

        boolean suppressComposerDuringCall() {
            EditText composer = findHomeComposer(decor);
            if (composer == null) return false;
            boolean focused = composer.isFocused();
            hideIme(composer);
            if (focused) composer.clearFocus();
            // The HOME editor remains in the same hierarchy underneath the CALL overlay. On the
            // emulator Android re-selected it on a later layout pass even after clearFocus(). While
            // CALL is visible, temporarily make only that hidden editor ineligible for touch focus.
            // This prevents it from owning CALL focus without changing HOME layout or editor state.
            composer.setFocusableInTouchMode(false);
            decor.setFocusableInTouchMode(true);
            decor.requestFocus();
            return focused;
        }

        void restoreComposerAfterCall() {
            EditText composer = findHomeComposer(decor);
            if (composer != null) composer.setFocusableInTouchMode(true);
        }

        boolean clearComposerFocusAndHideIme() {
            EditText composer = findHomeComposer(decor);
            if (composer == null) return false;
            boolean focused = composer.isFocused();
            hideIme(composer);
            if (focused) composer.clearFocus();
            // clearFocus() alone lets Android restore the only focusable editor after the CALL
            // overlay disappears. Give the window a neutral focus owner so HOME stays unedited and
            // the keyboard remains hidden until the user explicitly taps the composer again.
            decor.setFocusableInTouchMode(true);
            decor.requestFocus();
            return focused;
        }

        void hideIme(View tokenView) {
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

    private static EditText findHomeComposer(View root) {
        if (root instanceof EditText) {
            CharSequence description = root.getContentDescription();
            if (description != null && HOME_COMPOSER_DESC.contentEquals(description)) {
                return (EditText) root;
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                EditText found = findHomeComposer(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
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
