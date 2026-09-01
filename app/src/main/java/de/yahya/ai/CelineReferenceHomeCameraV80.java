package de.yahya.ai;

import com.google.android.filament.Camera;

import java.lang.reflect.Field;
import java.util.WeakHashMap;

/**
 * M1 measured HOME camera owner for the reference-room reconstruction.
 *
 * M0 measured the HOME wall/floor boundary near normalized y~0.81 while /Refernzbild.png requires
 * y~0.49. A global room/root Y lift was disproven by direct proof. The first measured-camera solve
 * then placed the eye above the accepted room ceiling; proof #80 therefore looked through the shell.
 * Proof #81 on the shell-safe eye y=1.05 / target y=-1.33 removed that occlusion, but over-corrected
 * the composition: the wall/floor boundary moved to roughly y~0.25 and Celine's head was clipped out
 * of HOME. Keep the proven shell-safe eye fixed and reduce only the downward pitch by raising the HOME
 * target to y=-0.30. This is the single bounded M1 camera correction supported by proof #81; CALL,
 * room-root, furniture, materials, lighting and canonical Celine transforms remain untouched.
 *
 * It is invoked from the final pre-render hook, after Celine3DView's legacy camera update, so the
 * frame that is actually rendered has one deterministic measured HOME camera. Existing pinch zoom
 * remains a real dolly and existing bounded pan remains additive.
 */
final class CelineReferenceHomeCameraV80 {
    static final float HOME_TARGET_Y = -0.30f;
    static final float HOME_TARGET_Z = -4.0f;
    static final float HOME_EYE_Y_OFFSET_M = 1.05f;
    static final float HOME_EYE_Z_OFFSET_M = 4.35f;
    private static final float ZOOM_MIN = 0.55f;
    private static final float ZOOM_MAX = 4.60f;

    private static final WeakHashMap<Celine3DView, Driver> DRIVERS = new WeakHashMap<>();

    private CelineReferenceHomeCameraV80() {}

    static void apply(Celine3DView view) {
        if (view == null || CelineCallUpperBodyPresenceV55.isCallStage(view)) return;
        try {
            Driver driver;
            synchronized (DRIVERS) {
                driver = DRIVERS.get(view);
                if (driver == null) {
                    driver = new Driver(view);
                    DRIVERS.put(view, driver);
                }
            }
            driver.apply();
        } catch (Throwable error) {
            Celine3DDiagnostics.error(view.getContext(), "ROOM-169",
                    "M1 Referenz-HOME-Kamera FEHLER", error);
        }
    }

    private static final class Driver {
        final Celine3DView view;
        final Camera camera;
        final Field zoomField;
        final Field panXField;
        final Field panYField;
        boolean logged;

        Driver(Celine3DView view) throws Exception {
            this.view = view;
            camera = (Camera) field(view, "camera");
            zoomField = declaredField("cameraZoom");
            panXField = declaredField("cameraPanX");
            panYField = declaredField("cameraPanY");
        }

        void apply() throws Exception {
            float zoom = clamp(zoomField.getFloat(view), ZOOM_MIN, ZOOM_MAX);
            float panX = panXField.getFloat(view);
            float panY = panYField.getFloat(view);
            float dollyScale = 1.0f / zoom;

            CelineProductionPresenceV80.HomeFrame motion =
                    CelineProductionPresenceV80.homeFrame(view);

            double targetX = motion.x * 0.48 + panX;
            double targetY = HOME_TARGET_Y + panY;
            double targetZ = HOME_TARGET_Z;
            double eyeX = motion.x * 0.16 + panX * 0.28;
            double eyeY = HOME_TARGET_Y
                    + (HOME_EYE_Y_OFFSET_M - HOME_TARGET_Y) * dollyScale
                    + panY * 0.28;
            double eyeZ = HOME_TARGET_Z + HOME_EYE_Z_OFFSET_M * dollyScale;

            camera.lookAt(eyeX, eyeY, eyeZ,
                    targetX, targetY, targetZ,
                    0.0, 1.0, 0.0);

            if (!logged) {
                logged = true;
                Celine3DDiagnostics.record(view.getContext(), "ROOM-160",
                        "M1 gemessene HOME-Kamera · Pitch-Korrektur aktiv",
                        "eye=0," + HOME_EYE_Y_OFFSET_M + ","
                                + (HOME_TARGET_Z + HOME_EYE_Z_OFFSET_M)
                                + " target=0," + HOME_TARGET_Y + "," + HOME_TARGET_Z
                                + " zoom=" + zoom
                                + " ceilingY=1.25 floorY=-1.55"
                                + " source=proof81 horizon-overcorrection"
                                + " CALL=untouched roomRootLift=false");
            }
        }
    }

    private static Field declaredField(String name) throws Exception {
        Field field = Celine3DView.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
