package de.yahya.ai;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import java.util.Random;

/**
 * Non-destructive facial rig for the approved Celine portrait.
 * The source bitmap is never modified; blinking and speech are rendered as transient overlays.
 */
public final class CelineFaceOverlayView extends View {
    public enum Activity { IDLE, LISTENING, THINKING, SPEAKING }

    private static final float LEFT_L = 0.305f, LEFT_R = 0.470f;
    private static final float RIGHT_L = 0.530f, RIGHT_R = 0.695f;
    private static final float EYE_T = 0.345f, EYE_B = 0.430f;
    private static final float MOUTH_L = 0.405f, MOUTH_R = 0.595f;
    private static final float MOUTH_T = 0.570f, MOUTH_B = 0.635f;

    private final Bitmap source;
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint lidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mouthInteriorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tonguePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private float blink = 0f;
    private float mouthLevel = 0f;
    private float visemeOpen = 0f, visemeWide = 0f, visemeRound = 0f;
    private float gazeX = 0f, gazeY = 0f;
    private SpeechVisemeAnalyzer.Shape visemeShape = SpeechVisemeAnalyzer.Shape.CLOSED;
    private boolean running = false;
    private Activity activity = Activity.IDLE;
    private ValueAnimator blinkAnimator;

    private final Runnable blinkTask = new Runnable() {
        @Override public void run() { if (running) blinkNow(random.nextInt(8) == 0); }
    };

    public CelineFaceOverlayView(Context context) {
        super(context);
        setWillNotDraw(false);
        source = BitmapFactory.decodeResource(getResources(), de.yahya.ai.R.drawable.celine_avatar);
        lidPaint.setStyle(Paint.Style.STROKE);
        lidPaint.setStrokeCap(Paint.Cap.ROUND);
        lidPaint.setColor(0xCC2B2024);
        mouthInteriorPaint.setColor(0xE82A1016);
        tonguePaint.setColor(0xB55B2633);
    }

    public void start() { if (!running) { running = true; scheduleNext(); } }

    public void stop() {
        running = false;
        handler.removeCallbacks(blinkTask);
        if (blinkAnimator != null) blinkAnimator.cancel();
        blink = mouthLevel = visemeOpen = visemeWide = visemeRound = gazeX = gazeY = 0f;
        visemeShape = SpeechVisemeAnalyzer.Shape.CLOSED;
        invalidate();
    }

    public void setActivity(Activity next) {
        activity = next == null ? Activity.IDLE : next;
        if (activity != Activity.SPEAKING) {
            setMouthLevel(0f);
            setViseme(SpeechVisemeAnalyzer.silent());
        }
        if (running) {
            handler.removeCallbacks(blinkTask);
            scheduleNext();
        }
    }

    public void setGaze(float x, float y) {
        gazeX = clampSigned(x) * 0.35f;
        gazeY = clampSigned(y) * 0.22f;
        invalidate();
    }

    public void releaseGaze() {
        ValueAnimator a = ValueAnimator.ofFloat(1f, 0f);
        final float startX = gazeX, startY = gazeY;
        a.setDuration(320L);
        a.setInterpolator(new AccelerateDecelerateInterpolator());
        a.addUpdateListener(v -> {
            float f = (Float) v.getAnimatedValue();
            gazeX = startX * f;
            gazeY = startY * f;
            invalidate();
        });
        a.start();
    }

    public void setMouthLevel(float level) {
        float next = activity == Activity.SPEAKING ? clamp(level) : 0f;
        // Fast attack, soft release: speech feels responsive but does not chatter.
        float smoothing = next > mouthLevel ? 0.82f : 0.42f;
        mouthLevel = mouthLevel * (1f - smoothing) + next * smoothing;
        if (mouthLevel < 0.015f) mouthLevel = 0f;
        invalidate();
    }

    public void setViseme(SpeechVisemeAnalyzer.Cue cue) {
        if (cue == null || activity != Activity.SPEAKING) cue = SpeechVisemeAnalyzer.silent();
        visemeShape = cue.shape;
        visemeOpen = visemeOpen * 0.22f + cue.openness * 0.78f;
        visemeWide = visemeWide * 0.32f + cue.width * 0.68f;
        visemeRound = visemeRound * 0.32f + cue.roundness * 0.68f;
        invalidate();
    }

    public void blinkNow(boolean doubleBlink) {
        if (blinkAnimator != null && blinkAnimator.isRunning()) return;
        float[] values = doubleBlink ? new float[]{0f,1f,0f,0f,1f,0f} : new float[]{0f,1f,0f};
        blinkAnimator = ValueAnimator.ofFloat(values);
        blinkAnimator.setDuration(doubleBlink ? 410L : 175L);
        blinkAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        blinkAnimator.addUpdateListener(a -> { blink = (Float)a.getAnimatedValue(); invalidate(); });
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
        long base = activity == Activity.LISTENING ? 1900L
                : activity == Activity.THINKING ? 2600L
                : activity == Activity.SPEAKING ? 2300L : 3000L;
        handler.postDelayed(blinkTask, base + 700L + random.nextInt(3200));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (source == null || source.isRecycled() || getWidth() <= 0 || getHeight() <= 0) return;
        if (Math.abs(gazeX) > 0.01f || Math.abs(gazeY) > 0.01f) {
            drawEyeMicroShift(canvas, LEFT_L, LEFT_R);
            drawEyeMicroShift(canvas, RIGHT_L, RIGHT_R);
        }
        if (mouthLevel > 0.008f || visemeOpen > 0.008f) drawRiggedMouth(canvas);
        if (blink > 0.01f) {
            drawBlinkEye(canvas, LEFT_L, LEFT_R);
            drawBlinkEye(canvas, RIGHT_L, RIGHT_R);
        }
    }

    /**
     * Splits the original mouth into upper/lower lip patches and opens them around a dark oral cavity.
     * This is much more visible than scaling a single clipped bitmap, while preserving Celine's real lips.
     */
    private void drawRiggedMouth(Canvas canvas) {
        float bw = source.getWidth(), bh = source.getHeight(), vw = getWidth(), vh = getHeight();
        float scale = Math.max(vw / bw, vh / bh);
        float dx = (vw - bw * scale) * 0.5f, dy = (vh - bh * scale) * 0.5f;

        float srcL = MOUTH_L * bw, srcR = MOUTH_R * bw;
        float srcT = MOUTH_T * bh, srcB = MOUTH_B * bh;
        float srcMid = (srcT + srcB) * 0.5f;
        Rect upperSrc = new Rect((int)srcL, (int)srcT, (int)srcR, (int)srcMid);
        Rect lowerSrc = new Rect((int)srcL, (int)srcMid, (int)srcR, (int)srcB);

        RectF mouth = new RectF(dx + srcL * scale, dy + srcT * scale,
                dx + srcR * scale, dy + srcB * scale);

        float open = clamp(Math.max(mouthLevel * 0.92f, visemeOpen));
        if (visemeShape == SpeechVisemeAnalyzer.Shape.CLOSED) open *= 0.22f;
        float widthFactor = 1f;
        if (visemeShape == SpeechVisemeAnalyzer.Shape.WIDE) widthFactor += 0.12f * Math.max(.35f, visemeWide);
        if (visemeShape == SpeechVisemeAnalyzer.Shape.ROUND) widthFactor -= 0.14f * Math.max(.35f, visemeRound);

        float gap = mouth.height() * (0.08f + 0.48f * open);
        float cx = mouth.centerX();
        float halfW = mouth.width() * 0.5f * widthFactor;
        float left = cx - halfW, right = cx + halfW;
        float mid = mouth.centerY();

        RectF cavity = new RectF(left + mouth.width()*0.055f, mid - gap*0.25f,
                right - mouth.width()*0.055f, mid + gap*0.75f);
        if (open > 0.08f) {
            mouthInteriorPaint.setAlpha((int)(150 + 100 * open));
            canvas.drawOval(cavity, mouthInteriorPaint);
            if (open > 0.48f) {
                RectF tongue = new RectF(cavity.left + cavity.width()*0.18f,
                        cavity.centerY() + cavity.height()*0.12f,
                        cavity.right - cavity.width()*0.18f,
                        cavity.bottom - cavity.height()*0.08f);
                tonguePaint.setAlpha((int)(80 + 80 * open));
                canvas.drawOval(tongue, tonguePaint);
            }
        }

        RectF upperDst = new RectF(left, mouth.top - gap*0.20f, right, mid - gap*0.35f);
        RectF lowerDst = new RectF(left, mid + gap*0.42f, right, mouth.bottom + gap*0.34f);

        bitmapPaint.setAlpha(255);
        canvas.save();
        canvas.clipRect(new RectF(left, mouth.top - mouth.height()*0.25f,
                right, mouth.bottom + mouth.height()*0.40f));
        canvas.drawBitmap(source, upperSrc, upperDst, bitmapPaint);
        canvas.drawBitmap(source, lowerSrc, lowerDst, bitmapPaint);
        canvas.restore();
    }

    private void drawEyeMicroShift(Canvas canvas, float leftN, float rightN) {
        float bw = source.getWidth(), bh = source.getHeight(), vw = getWidth(), vh = getHeight();
        float scale = Math.max(vw/bw, vh/bh), dx=(vw-bw*scale)*0.5f, dy=(vh-bh*scale)*0.5f;
        int srcL = clampInt((int)(leftN*bw),0,source.getWidth()-2);
        int srcR = clampInt((int)(rightN*bw),srcL+1,source.getWidth());
        int srcT = clampInt((int)(EYE_T*bh),0,source.getHeight()-2);
        int srcB = clampInt((int)(EYE_B*bh),srcT+1,source.getHeight());
        Rect src = new Rect(srcL,srcT,srcR,srcB);
        RectF dst = new RectF(dx+srcL*scale,dy+srcT*scale,dx+srcR*scale,dy+srcB*scale);
        float tx = gazeX * dst.width()*0.025f;
        float ty = gazeY * dst.height()*0.045f;
        canvas.save();
        canvas.clipRect(dst);
        dst.offset(tx,ty);
        bitmapPaint.setAlpha(235);
        canvas.drawBitmap(source,src,dst,bitmapPaint);
        canvas.restore();
    }

    private void drawBlinkEye(Canvas canvas, float leftN, float rightN) {
        float bw=source.getWidth(), bh=source.getHeight(), vw=getWidth(), vh=getHeight();
        float scale=Math.max(vw/bw,vh/bh), dx=(vw-bw*scale)*0.5f, dy=(vh-bh*scale)*0.5f;
        float sx0=leftN*bw,sx1=rightN*bw,sy0=EYE_T*bh,sy1=EYE_B*bh;
        RectF eye=new RectF(dx+sx0*scale,dy+sy0*scale,dx+sx1*scale,dy+sy1*scale);
        int srcL=clampInt((int)sx0,0,source.getWidth()-1), srcR=clampInt((int)sx1,srcL+1,source.getWidth());
        int eyeH=Math.max(2,(int)(sy1-sy0)), srcB=clampInt((int)sy0,2,source.getHeight()), srcT=clampInt(srcB-eyeH,0,srcB-1);
        Rect skinSource=new Rect(srcL,srcT,srcR,srcB);
        float close=Math.min(1f,blink*1.20f);
        RectF cover=new RectF(eye.left,eye.top,eye.right,eye.top+eye.height()*close);
        bitmapPaint.setAlpha((int)(255f*close));
        canvas.save();
        canvas.clipRect(eye);
        canvas.drawBitmap(source,skinSource,cover,bitmapPaint);
        canvas.restore();
        if(blink>0.40f){
            float lineY=eye.centerY()+eye.height()*0.08f,inset=eye.width()*0.12f;
            Path p=new Path();
            p.moveTo(eye.left+inset,lineY);
            p.quadTo(eye.centerX(),lineY+eye.height()*0.12f,eye.right-inset,lineY);
            lidPaint.setStrokeWidth(Math.max(1.4f,eye.height()*0.045f));
            lidPaint.setAlpha((int)(220f*Math.min(1f,(blink-0.40f)/0.60f)));
            canvas.drawPath(p,lidPaint);
        }
    }

    private static float clamp(float v){return Math.max(0f,Math.min(1f,v));}
    private static float clampSigned(float v){return Math.max(-1f,Math.min(1f,v));}
    private static int clampInt(int v,int min,int max){return Math.max(min,Math.min(max,v));}
}
