package de.yahya.ai;

import com.google.android.filament.Camera;

import java.lang.reflect.Field;
import java.util.WeakHashMap;

/**
 * M1 measured HOME camera owner for the reference-room reconstruction.
 *
 * M0 measured the HOME wall/floor boundary near normalized y~0.81 while /Refernzbild.png requires
 * y~0.49. A global room/root Y lift was disproven by direct proof. Proof #80 isolated a camera eye
 * above the room ceiling; proof #81 established a shell-safe eye; proof #82 reduced the horizon error
 * but left Celine oversized/clipped. Proof #83 with Z distance 5.15 m restores Celine fully and puts
 * her apparent width near the reference, while the remaining wall/floor boundary is roughly y~0.59.
 * Keep the proven eye height and 5.15 m apparent-scale solution, and lower only HOME target Y from
 * -0.30 to -0.45 as the smallest measured pitch step toward target y~0.49. CALL, room-root, furniture
 * transforms, materials, lighting and canonical Celine transforms remain untouched.
 */
final class CelineReferenceHomeCameraV80 {
    static final float HOME_TARGET_Y = -0.45f;
    static final float HOME_TARGET_Z = -4.0f;
    static final float HOME_EYE_Y_OFFSET_M = 1.05f;
    static final float HOME_EYE_Z_OFFSET_M = 5.15f;
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
                        "M1 gemessene HOME-Kamera · Horizont-Korrektur aktiv",
                        "eye=0," + HOME_EYE_Y_OFFSET_M + ","
                                + (HOME_TARGET_Z + HOME_EYE_Z_OFFSET_M)
                                + " target=0," + HOME_TARGET_Y + "," + HOME_TARGET_Z
                                + " zoom=" + zoom
                                + " ceilingY=1.25 floorY=-1.55"
                                + " source=proof83 horizon-delta"
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
