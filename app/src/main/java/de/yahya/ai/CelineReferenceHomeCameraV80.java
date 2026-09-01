package de.yahya.ai;

import com.google.android.filament.Camera;

import java.lang.reflect.Field;
import java.util.WeakHashMap;

/**
 * M1 measured HOME camera owner for the reference-room reconstruction.
 *
 * The exact HOME proof at e1425b5 measured the room wall/floor boundary near normalized y~0.81,
 * while /Refernzbild.png requires y~0.49. The same proof already places Celine's head near the
 * target height, so a global room/root Y lift cannot solve the perspective mismatch. Solving the
 * current 38 mm projection against the measured head, feet and back-wall/floor points yields a
 * HOME eye approximately 2.38 m above the model center and 4.35 m in front of z=-4, still looking
 * at the existing Celine/room center. This owner runs only in HOME and never writes CALL framing.
 *
 * It is invoked from the final pre-render hook, after Celine3DView's legacy camera update, so the
 * frame that is actually rendered has one deterministic measured HOME camera. Existing pinch zoom
 * remains a real dolly along the same measured viewing vector and existing bounded pan remains
 * additive. No Celine transform, source room GLB, furniture GLB or CALL camera contract is changed.
 */
final class CelineReferenceHomeCameraV80 {
    static final float HOME_TARGET_Z = -4.0f;
    static final float HOME_EYE_Y_OFFSET_M = 2.38f;
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
            double targetY = panY;
            double targetZ = HOME_TARGET_Z;
            double eyeX = motion.x * 0.16 + panX * 0.28;
            double eyeY = HOME_EYE_Y_OFFSET_M * dollyScale + panY * 0.28;
            double eyeZ = HOME_TARGET_Z + HOME_EYE_Z_OFFSET_M * dollyScale;

            camera.lookAt(eyeX, eyeY, eyeZ,
                    targetX, targetY, targetZ,
                    0.0, 1.0, 0.0);

            if (!logged) {
                logged = true;
                Celine3DDiagnostics.record(view.getContext(), "ROOM-160",
                        "M1 gemessene Referenz-HOME-Kamera aktiv",
                        "eye=0," + HOME_EYE_Y_OFFSET_M + ","
                                + (HOME_TARGET_Z + HOME_EYE_Z_OFFSET_M)
                                + " target=0,0," + HOME_TARGET_Z
                                + " zoom=" + zoom
                                + " source=head+feet+wallFloor pixel solve"
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
