package de.yahya.ai;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.widget.FrameLayout;

import com.google.android.filament.Camera;

import java.lang.reflect.Field;

/**
 * Lab-only CALL scene adapter for deterministic seat-contact evidence.
 *
 * Production draws the CALL chair in CelineRoomBackdropView behind a transparent SurfaceView. ADB
 * screencap on the software-emulator compositor cannot be trusted to preserve that sibling-surface
 * composition: Proof #35 showed the avatar over black even though the transparent swap-chain logs
 * were present, and the swap-chain replacement also made face-state screenshots one frame stale.
 *
 * The Lab therefore leaves Celine3DView's proven renderer/compositor path untouched and overlays a
 * diagnostic guide for the exact production chair cushion plane (x=0.35..0.65, y=0.61..0.675 of
 * the stage). The guide is CI/Lab-only, never installed in HOME/CALL, never translates/scales the
 * avatar and makes floating/sinking relative to the intended production seat plane unambiguous.
 * The `call` preset still uses the exact 50 mm projection selected by CelineVideoCallV45.
 */
final class CelineAvatarLabSceneV79 {
    private static final float CALL_SEAT_LEFT = 0.35f;
    private static final float CALL_SEAT_RIGHT = 0.65f;
    private static final float CALL_SEAT_TOP = 0.61f;
    private static final float CALL_SEAT_BOTTOM = 0.675f;

    private final Activity activity;
    private final Celine3DView view;
    private final Camera camera;
    private final SeatPlaneGuideView seatGuide;

    static CelineAvatarLabSceneV79 install(Activity activity, FrameLayout root,
                                           Celine3DView view) throws Exception {
        SeatPlaneGuideView guide = new SeatPlaneGuideView(activity);
        root.addView(guide, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        return new CelineAvatarLabSceneV79(activity, view, guide);
    }

    private CelineAvatarLabSceneV79(Activity activity, Celine3DView view,
                                    SeatPlaneGuideView seatGuide) throws Exception {
        this.activity = activity;
        this.view = view;
        this.seatGuide = seatGuide;
        camera = (Camera) field(view, "camera");
    }

    void apply(String pose, String cameraPreset) {
        boolean call = "call".equalsIgnoreCase(cameraPreset);
        boolean seated = "seated".equalsIgnoreCase(pose);
        boolean showSeatGuide = call && seated;
        seatGuide.setSeatPlaneVisible(showSeatGuide);

        int w = Math.max(1, view.getWidth());
        int h = Math.max(1, view.getHeight());
        camera.setLensProjection(call ? 50.0 : 32.0,
                (double) w / (double) h, 0.05, 1000.0);
        Celine3DDiagnostics.record(activity, "V79-530", "Avatar Lab Produktionsszene gesetzt",
                "scene=" + (call ? "CALL" : "diagnostic")
                        + " seated=" + seated
                        + " lensMm=" + (call ? 50 : 32)
                        + " rootScaleChanged=false");
        if (showSeatGuide) {
            Celine3DDiagnostics.record(activity, "V79-531", "Avatar Lab CALL-Sitzebene markiert",
                    "productionSeatTop=0.61 productionSeatBottom=0.675"
                            + " · screenSpaceGuide=true · rendererCompositionUnchanged=true");
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static final class SeatPlaneGuideView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean visible;

        SeatPlaneGuideView(Activity activity) {
            super(activity);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            setClickable(false);
            setFocusable(false);
            setVisibility(INVISIBLE);
        }

        void setSeatPlaneVisible(boolean value) {
            visible = value;
            setVisibility(value ? VISIBLE : INVISIBLE);
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (!visible) return;
            float w = getWidth();
            float h = getHeight();
            if (w <= 0f || h <= 0f) return;

            float left = w * CALL_SEAT_LEFT;
            float right = w * CALL_SEAT_RIGHT;
            float top = h * CALL_SEAT_TOP;
            float bottom = h * CALL_SEAT_BOTTOM;

            // Very light cushion band plus a crisp top-plane line. The overlay is diagnostic only;
            // it intentionally crosses Celine so penetration/floating is directly visible.
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(28, 95, 215, 255));
            canvas.drawRect(left, top, right, bottom, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(3f, w * 0.004f));
            paint.setColor(Color.argb(235, 110, 225, 255));
            canvas.drawLine(left, top, right, top, paint);
            canvas.drawLine(left, top - 12f, left, top + 12f, paint);
            canvas.drawLine(right, top - 12f, right, top + 12f, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(Math.max(18f, w * 0.024f));
            paint.setColor(Color.argb(235, 210, 245, 255));
            canvas.drawText("CALL SEAT PLANE", left + 8f, Math.max(24f, top - 14f), paint);
        }
    }
}
