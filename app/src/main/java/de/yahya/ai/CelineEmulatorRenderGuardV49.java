package de.yahya.ai;

import android.os.Build;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.util.WeakHashMap;

/**
 * API-30 SwiftShader in GitHub Actions cannot compile Filament 1.72's FXAA shader and aborts the
 * native FEngine thread. Real Samsung/ARM devices are not affected. This guard is intentionally
 * emulator-only and disables post-processing solely so CI can prove that a real Filament mesh is
 * present in HOME and CALL screenshots.
 */
final class CelineEmulatorRenderGuardV49 {
    private static final WeakHashMap<Celine3DView, Boolean> APPLIED = new WeakHashMap<>();

    private CelineEmulatorRenderGuardV49() {}

    static void apply(android.view.View decor) {
        if (!isEmulator() || decor == null) return;
        Celine3DView threeD = find3D(decor);
        if (threeD == null) return;
        synchronized (APPLIED) {
            if (Boolean.TRUE.equals(APPLIED.get(threeD))) return;
        }
        try {
            Field field = Celine3DView.class.getDeclaredField("filamentView");
            field.setAccessible(true);
            com.google.android.filament.View filamentView =
                    (com.google.android.filament.View) field.get(threeD);
            if (filamentView == null) return;
            filamentView.setPostProcessingEnabled(false);
            synchronized (APPLIED) { APPLIED.put(threeD, Boolean.TRUE); }
            Celine3DDiagnostics.record(threeD.getContext(), "V49-080",
                    "Emulator-Renderguard aktiv",
                    "postProcessing=OFF · SwiftShader FXAA bypass · productionDevice=unchanged");
        } catch (Throwable e) {
            Celine3DDiagnostics.error(threeD.getContext(), "V49-089",
                    "Emulator-Renderguard FEHLER", e);
        }
    }

    private static boolean isEmulator() {
        String fingerprint = lower(Build.FINGERPRINT);
        String model = lower(Build.MODEL);
        String product = lower(Build.PRODUCT);
        String hardware = lower(Build.HARDWARE);
        return fingerprint.contains("generic") || fingerprint.contains("emulator") ||
                model.contains("emulator") || model.contains("android sdk built for") ||
                product.contains("sdk") || hardware.contains("ranchu") || hardware.contains("goldfish");
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(java.util.Locale.US);
    }

    private static Celine3DView find3D(android.view.View root) {
        if (root instanceof Celine3DView) return (Celine3DView) root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                Celine3DView found = find3D(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
}
