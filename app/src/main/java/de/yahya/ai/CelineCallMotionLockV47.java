package de.yahya.ai;

import android.app.Activity;
import android.view.Choreographer;
import android.view.View;
import android.view.ViewGroup;

import java.util.WeakHashMap;

/**
 * Owns the v44 locomotion lock during a v45/v46 live call. This closes the v46 race where the
 * seated layer stopped v44, but CelineVideoCallV45 called CelineVideoChatV44.ensure() shortly
 * afterwards and recreated the walking MotionState. The lock is visible to v44 itself, so every
 * later ensure() becomes harmless until the call overlay is gone.
 */
final class CelineCallMotionLockV47 {
    private static final WeakHashMap<Activity, State> STATES = new WeakHashMap<>();

    private CelineCallMotionLockV47() {}

    static void install(Activity activity, View decor) {
        if (!(activity instanceof MainActivity) || decor == null) return;
        State s;
        synchronized (STATES) {
            s = STATES.get(activity);
            if (s == null) {
                s = new State(activity, decor);
                STATES.put(activity, s);
            }
        }
        s.resume();
    }

    static void onPaused(Activity activity) {
        State s;
        synchronized (STATES) { s = STATES.get(activity); }
        if (s != null) s.paused = true;
    }

    static void onDestroyed(Activity activity) {
        State s;
        synchronized (STATES) { s = STATES.remove(activity); }
        if (s != null) s.destroy();
    }

    private static final class State implements Choreographer.FrameCallback {
        final Activity activity;
        final View decor;
        final Choreographer choreographer = Choreographer.getInstance();
        boolean running;
        boolean paused;
        boolean locked;
        Celine3DView lockedView;

        State(Activity activity, View decor) {
            this.activity = activity;
            this.decor = decor;
        }

        void resume() {
            paused = false;
            if (running) return;
            running = true;
            choreographer.postFrameCallback(this);
        }

        void destroy() {
            running = false;
            choreographer.removeFrameCallback(this);
            if (locked) {
                locked = false;
                CelineVideoChatV44.resumeAfterCall(activity, decor);
            }
        }

        @Override public void doFrame(long frameTimeNanos) {
            if (!running) return;
            choreographer.postFrameCallback(this);
            if (paused) return;

            boolean callNow = findTagged(decor, "v45-stage-slot") != null;
            Celine3DView view = find3D(decor);
            if (callNow && !locked && view != null) {
                locked = true;
                lockedView = view;
                boolean stopped = CelineVideoChatV44.pauseForCall(view);
                Celine3DDiagnostics.record(activity, "V47-110", "Call-Bewegungslock aktiv",
                        "v44RestartBlock=true · previousMotionStopped=" + stopped);
            } else if (!callNow && locked) {
                locked = false;
                lockedView = null;
                CelineVideoChatV44.resumeAfterCall(activity, decor);
                Celine3DDiagnostics.record(activity, "V47-120", "Call-Bewegungslock gelöst",
                        "v44 room motion may resume");
            } else if (callNow && locked && view != null && view != lockedView) {
                lockedView = view;
                CelineVideoChatV44.pauseForCall(view);
            }
        }
    }

    private static Celine3DView find3D(View root) {
        if (root instanceof Celine3DView) return (Celine3DView) root;
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                Celine3DView v = find3D(g.getChildAt(i));
                if (v != null) return v;
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
}
