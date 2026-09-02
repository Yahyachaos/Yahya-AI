package de.yahya.ai;

import com.google.android.filament.Camera;

import java.lang.reflect.Field;
import java.util.WeakHashMap;

/**
 * Reference HOME camera owner for the room reconstruction.
 *
 * The latest real HOME/CALL proof was compared again against the canonical bedroom reference.
 * The viewer still sat too close to the room and compressed its depth. Keep the accepted target,
 * pitch and 32 mm projection, but move the HOME eye farther toward the laptop/viewer side.
 * CALL receives the matching distance in CelineCameraZoomV70.
 */
final class CelineReferenceHomeCameraV80 {
    static final float HOME_TARGET_Y = -0.60f;
    static final float HOME_TARGET_Z = -4.0f;
    static final float HOME_EYE_Y_OFFSET_M = 1.05f;
    static final float HOME_EYE_Z_OFFSET_M = 7.10f;
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
                    "Referenz-HOME-Kamera FEHLER", error);
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
                        "Referenz-HOME-Kamera weiter aus dem Raum gezogen",
                        "eye=0," + HOME_EYE_Y_OFFSET_M + ","
                                + (HOME_TARGET_Z + HOME_EYE_Z_OFFSET_M)
                                + " target=0," + HOME_TARGET_Y + "," + HOME_TARGET_Z
                                + " zoom=" + zoom
                                + " eyeOffsetZ=" + HOME_EYE_Z_OFFSET_M
                                + " reason=reference-recheck-more-room-depth"
                                + " CALL=matched-separately roomRoot=false");
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
