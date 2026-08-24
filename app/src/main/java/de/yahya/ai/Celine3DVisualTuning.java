package de.yahya.ai;

import com.google.android.filament.Camera;
import com.google.android.filament.Engine;
import com.google.android.filament.LightManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** v37 visual tuning applied only after the real Filament view has loaded successfully. */
final class Celine3DVisualTuning {
    private Celine3DVisualTuning() {}

    static String apply(Celine3DView view) {
        if (view == null) return "view=null";
        StringBuilder result = new StringBuilder();

        try {
            Field cameraField = Celine3DView.class.getDeclaredField("camera");
            cameraField.setAccessible(true);
            Camera camera = (Camera) cameraField.get(view);
            if (camera != null) {
                // Significantly brighter than v36 so skin/hair/fabric texture is obvious on Samsung OLED.
                camera.setExposure(4.0f, 1.0f / 60.0f, 200.0f);
                result.append("camera=bright");
            }
        } catch (Throwable e) {
            result.append("camera=").append(e.getClass().getSimpleName());
        }

        try {
            Field engineField = Celine3DView.class.getDeclaredField("engine");
            Field lightField = Celine3DView.class.getDeclaredField("lightEntity");
            engineField.setAccessible(true);
            lightField.setAccessible(true);
            Engine engine = (Engine) engineField.get(view);
            int lightEntity = lightField.getInt(view);
            if (engine != null && lightEntity != 0) {
                LightManager manager = engine.getLightManager();
                int instance = manager.getInstance(lightEntity);
                Method setIntensity = LightManager.class.getMethod("setIntensity", int.class, float.class);
                setIntensity.invoke(manager, instance, 85000.0f);
                result.append(" · keyLight=85000");
            }
        } catch (Throwable e) {
            result.append(" · keyLight=").append(e.getClass().getSimpleName());
        }

        return result.toString();
    }
}
