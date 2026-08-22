package de.yahya.ai;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import java.util.Random;

/**
 * Non-destructive facial overlay for the approved Celine portrait.
 *
 * At rest this view draws nothing, so the original artwork is shown exactly as
 * stored. During a blink it briefly covers only the eye areas using tiny skin
 * samples from the same source bitmap, then draws a soft eyelid line. No new
 * face asset is generated and the original PNG is never modified.
 *
 * Eye boxes are normalized to the 768x768 reference image and intentionally
 * kept in one place so they can be fine-tuned on the target device later.
 */
public final class CelineFaceOverlayView extends View {
    public enum Activity { IDLE, LISTENING, THINKING, SPEAKING }

    // Normalized source-image boxes. These are calibration values, not new art.
    private static final float LEFT_L = 0.305f, LEFT_R = 0.470f;
    private static final float RIGHT_L = 0.530f, RIGHT_R = 0.695f;
    private static final float EYE_T = 0.345f, EYE_B = 0.430f;

    private final Bitmap source;
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint lidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private float blink = 0f;
    private boolean running = false;
    private Activity activity = Activity.IDLE;
    private ValueAnimator blinkAnimator;

    private final Runnable blinkTask = new Runnable() {
        @Override public void run() {
            if (!running) return;
            blinkNow(random.nextInt(7) == 0);
        }
    };

    public CelineFaceOverlayView(Context context) {
        super(context);
        setWillNotDraw(false);
        source = BitmapFactory.decodeResource(getResources(), de.yahya.ai.R.drawable.celine_avatar);
        lidPaint.setStyle(Paint.Style.STROKE);
        lidPaint.setStrokeCap(Paint.Cap.ROUND);
        lidPaint.setColor(0xCC2B2024);
    }

    public void start() {
        if (running) return;
        running = true;
        scheduleNext();
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(blinkTask);
        if (blinkAnimator != null) blinkAnimator.cancel();
        blink = 0f;
        invalidate();
    }

    public void setActivity(Activity next) {
        activity = next == null ? Activity.IDLE : next;
        if (running) {
            handler.removeCallbacks(blinkTask);
            scheduleNext();
        }
    }

    /** Manual blink hook for future emotional reactions/tests. */
    public void blinkNow(boolean doubleBlink) {
        if (blinkAnimator != null && blinkAnimator.isRunning()) return;
        float[] values = doubleBlink
                ? new float[]{0f, 1f, 0f, 0f, 1f, 0f}
                : new float[]{0f, 1f, 0f};
        blinkAnimator = ValueAnimator.ofFloat(values);
        blinkAnimator.setDuration(doubleBlink ? 430L : 190L);
        blinkAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        blinkAnimator.addUpdateListener(a -> {
            blink = (Float) a.getAnimatedValue();
            invalidate();
        });
        blinkAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                blink = 0f;
                invalidate();
                if (running) scheduleNext();
            }
        });
        handler.removeCallbacks(blinkTask);
        blinkAnimator.start();
    }

    private void scheduleNext() {
        if (!running) return;
        long base;
        switch (activity) {
            case LISTENING: base = 2200L; break;
            case THINKING: base = 3000L; break;
            case SPEAKING: base = 2600L; break;
            case IDLE:
            default: base = 3400L; break;
        }
        long jitter = 900L + random.nextInt(2800);
        handler.postDelayed(blinkTask, base + jitter);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (source == null || source.isRecycled() || blink <= 0.01f || getWidth() <= 0 || getHeight() <= 0) return;
        drawBlinkEye(canvas, LEFT_L, LEFT_R);
        drawBlinkEye(canvas, RIGHT_L, RIGHT_R);
    }

    private void drawBlinkEye(Canvas canvas, float leftN, float rightN) {
        float bw = source.getWidth(), bh = source.getHeight();
        float vw = getWidth(), vh = getHeight();
        float scale = Math.max(vw / bw, vh / bh); // mirrors ImageView CENTER_CROP
        float dx = (vw - bw * scale) * 0.5f;
        float dy = (vh - bh * scale) * 0.5f;

        float sx0 = leftN * bw, sx1 = rightN * bw;
        float sy0 = EYE_T * bh, sy1 = EYE_B * bh;

        RectF eye = new RectF(dx + sx0 * scale, dy + sy0 * scale,
                dx + sx1 * scale, dy + sy1 * scale);

        // Sample skin immediately above the eye from Celine's own source image.
        int srcL = clampInt((int) sx0, 0, source.getWidth() - 1);
        int srcR = clampInt((int) sx1, srcL + 1, source.getWidth());
        int eyeH = Math.max(2, (int) (sy1 - sy0));
        int srcB = clampInt((int) sy0, 2, source.getHeight());
        int srcT = clampInt(srcB - eyeH, 0, srcB - 1);
        Rect skinSource = new Rect(srcL, srcT, srcR, srcB);

        float close = Math.min(1f, blink * 1.18f);
        RectF cover = new RectF(eye.left, eye.top,
                eye.right, eye.top + eye.height() * close);
        bitmapPaint.setAlpha((int) (255f * close));
        canvas.save();
        canvas.clipRect(eye);
        canvas.drawBitmap(source, skinSource, cover, bitmapPaint);
        canvas.restore();

        if (blink > 0.42f) {
            float lineY = eye.centerY() + eye.height() * 0.08f;
            float inset = eye.width() * 0.12f;
            Path p = new Path();
            p.moveTo(eye.left + inset, lineY);
            p.quadTo(eye.centerX(), lineY + eye.height() * 0.12f,
                    eye.right - inset, lineY);
            lidPaint.setStrokeWidth(Math.max(1.4f, eye.height() * 0.045f));
            lidPaint.setAlpha((int) (220f * Math.min(1f, (blink - 0.42f) / 0.58f)));
            canvas.drawPath(p, lidPaint);
        }
    }

    private static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
