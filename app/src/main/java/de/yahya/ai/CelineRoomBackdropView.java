package de.yahya.ai;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** Lightweight drawn room behind the transparent Filament avatar surface. */
final class CelineRoomBackdropView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    CelineRoomBackdropView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        paint.setShader(new LinearGradient(0, 0, 0, h * 0.72f,
                Color.rgb(34, 37, 48), Color.rgb(21, 24, 32), Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h * 0.76f, paint);
        paint.setShader(null);

        float wx0 = w * 0.08f, wy0 = h * 0.10f, wx1 = w * 0.37f, wy1 = h * 0.53f;
        paint.setColor(Color.rgb(14, 17, 24));
        canvas.drawRoundRect(new RectF(wx0 - 6, wy0 - 6, wx1 + 6, wy1 + 6), 14, 14, paint);
        paint.setShader(new LinearGradient(wx0, wy0, wx1, wy1,
                Color.rgb(67, 77, 104), Color.rgb(145, 105, 97), Shader.TileMode.CLAMP));
        canvas.drawRoundRect(new RectF(wx0, wy0, wx1, wy1), 10, 10, paint);
        paint.setShader(null);
        paint.setColor(Color.argb(110, 235, 224, 204));
        paint.setStrokeWidth(Math.max(2f, w * 0.004f));
        canvas.drawLine((wx0 + wx1) * 0.5f, wy0, (wx0 + wx1) * 0.5f, wy1, paint);
        canvas.drawLine(wx0, (wy0 + wy1) * 0.54f, wx1, (wy0 + wy1) * 0.54f, paint);

        paint.setShader(new LinearGradient(0, h * 0.72f, 0, h,
                Color.rgb(40, 34, 33), Color.rgb(20, 18, 20), Shader.TileMode.CLAMP));
        canvas.drawRect(0, h * 0.72f, w, h, paint);
        paint.setShader(null);
        paint.setColor(Color.argb(48, 235, 216, 194));
        paint.setStrokeWidth(1.5f);
        for (int i = -3; i <= 5; i++) {
            float topX = w * 0.5f + i * w * 0.085f;
            float bottomX = w * 0.5f + i * w * 0.19f;
            canvas.drawLine(topX, h * 0.72f, bottomX, h, paint);
        }
        for (int i = 1; i <= 4; i++) {
            float y = h * (0.72f + i * i * 0.017f);
            canvas.drawLine(0, y, w, y, paint);
        }

        paint.setColor(Color.rgb(49, 46, 55));
        canvas.drawRoundRect(new RectF(w * 0.69f, h * 0.56f, w * 0.98f, h * 0.75f), 24, 24, paint);
        paint.setColor(Color.rgb(64, 58, 69));
        canvas.drawRoundRect(new RectF(w * 0.72f, h * 0.52f, w * 0.98f, h * 0.64f), 22, 22, paint);

        paint.setColor(Color.rgb(176, 154, 125));
        canvas.drawRect(w * 0.61f, h * 0.28f, w * 0.615f, h * 0.69f, paint);
        paint.setColor(Color.rgb(222, 194, 155));
        path.reset();
        path.moveTo(w * 0.555f, h * 0.27f);
        path.lineTo(w * 0.67f, h * 0.27f);
        path.lineTo(w * 0.645f, h * 0.39f);
        path.lineTo(w * 0.58f, h * 0.39f);
        path.close();
        canvas.drawPath(path, paint);
        paint.setColor(Color.argb(32, 255, 220, 170));
        canvas.drawCircle(w * 0.612f, h * 0.34f, w * 0.15f, paint);

        paint.setColor(Color.rgb(55, 70, 56));
        for (int i = 0; i < 6; i++) {
            float x = w * (0.88f + (i - 3) * 0.012f);
            canvas.drawOval(new RectF(x - 12, h * 0.42f + i * 4, x + 12, h * 0.57f), paint);
        }
        paint.setColor(Color.rgb(91, 69, 58));
        canvas.drawRoundRect(new RectF(w * 0.84f, h * 0.56f, w * 0.93f, h * 0.70f), 10, 10, paint);

        paint.setShader(new LinearGradient(0, 0, w, 0,
                Color.argb(65, 0, 0, 0), Color.argb(0, 0, 0, 0), Shader.TileMode.MIRROR));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
    }
}
