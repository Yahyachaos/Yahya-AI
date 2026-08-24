package de.yahya.ai;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

/**
 * v37 device-side reveal pass. Once the controller has already hidden the 2D fallback, physically
 * remove that fallback from the avatar stage, brighten the real Filament view, and perform a short
 * unmistakable head-look sequence. This does not decide whether 3D is valid; v36 diagnostics and
 * CelineAvatarController already do that. It only makes a confirmed active 3D view impossible to
 * confuse with the old PNG.
 */
final class Celine3DForceReveal {
    private static boolean revealSequenceStarted;

    private Celine3DForceReveal() {}

    static void ensure(View root) {
        if (root == null) return;
        Celine3DView threeD = find3D(root);
        if (threeD == null) return;

        String tuning = Celine3DVisualTuning.apply(threeD);
        Celine3DDiagnostics.record(root.getContext(), "V37-120", "3D-Sichtbarkeit nachgeschärft", tuning);
        threeD.setAlpha(1.0f);
        threeD.setVisibility(View.VISIBLE);

        ImageView avatar = findLargestImage(root, null);
        // Controller sets the real fallback to GONE only after CTL-350. Remove it physically only
        // after that point; if 3D failed, the visible 2D safety fallback remains untouched.
        if (avatar != null && avatar.getVisibility() == View.GONE && avatar.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) avatar.getParent();
            parent.removeView(avatar);
            Celine3DDiagnostics.record(root.getContext(), "V37-130", "2D-Fallback physisch entfernt",
                    "parent=" + parent.getClass().getSimpleName());
        }

        CelineFaceOverlayView face = findFace(root);
        if (face != null && face.getVisibility() == View.GONE && face.getParent() instanceof ViewGroup) {
            try { face.stop(); } catch (Throwable ignored) {}
            ((ViewGroup) face.getParent()).removeView(face);
            Celine3DDiagnostics.record(root.getContext(), "V37-131", "2D-Gesichts-Overlay entfernt", "3D aktiv");
        }

        if (!revealSequenceStarted) {
            revealSequenceStarted = true;
            Celine3DDiagnostics.record(root.getContext(), "V37-140", "3D-Reveal-Bewegung gestartet",
                    "Kopf rechts/links/mitte");
            Handler h = new Handler(Looper.getMainLooper());
            threeD.setLook(1.0f, -0.25f);
            h.postDelayed(() -> threeD.setLook(-1.0f, 0.15f), 700L);
            h.postDelayed(() -> threeD.setLook(0.75f, 0.30f), 1400L);
            h.postDelayed(() -> {
                threeD.releaseLook();
                Celine3DDiagnostics.record(root.getContext(), "V37-150", "3D-Reveal-Bewegung abgeschlossen",
                        "Idle-Bewegung läuft weiter");
            }, 2200L);
        }
    }

    private static Celine3DView find3D(View view) {
        if (view instanceof Celine3DView) return (Celine3DView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Celine3DView found = find3D(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static CelineFaceOverlayView findFace(View view) {
        if (view instanceof CelineFaceOverlayView) return (CelineFaceOverlayView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                CelineFaceOverlayView found = findFace(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static ImageView findLargestImage(View view, ImageView best) {
        if (view instanceof ImageView) {
            ImageView image = (ImageView) view;
            long area = (long) Math.max(0, image.getWidth()) * Math.max(0, image.getHeight());
            long bestArea = best == null ? -1L :
                    (long) Math.max(0, best.getWidth()) * Math.max(0, best.getHeight());
            if (area > bestArea) best = image;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                best = findLargestImage(group.getChildAt(i), best);
            }
        }
        return best;
    }
}
