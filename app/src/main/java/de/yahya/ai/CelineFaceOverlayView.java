package de.yahya.ai;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import java.util.Random;

/** Non-destructive facial overlay for the approved Celine portrait. */
public final class CelineFaceOverlayView extends View {
    public enum Activity { IDLE, LISTENING, THINKING, SPEAKING }

    private static final float LEFT_L = 0.305f, LEFT_R = 0.470f;
    private static final float RIGHT_L = 0.530f, RIGHT_R = 0.695f;
    private static final float EYE_T = 0.345f, EYE_B = 0.430f;
    private static final float MOUTH_L = 0.385f, MOUTH_R = 0.615f;
    private static final float MOUTH_T = 0.545f, MOUTH_B = 0.635f;

    private final Bitmap source;
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint lidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private float blink = 0f;
    private float mouthLevel = 0f;
    private float visemeOpen = 0f, visemeWide = 0f, visemeRound = 0f;
    private SpeechVisemeAnalyzer.Shape visemeShape = SpeechVisemeAnalyzer.Shape.CLOSED;
    private boolean running = false;
    private Activity activity = Activity.IDLE;
    private ValueAnimator blinkAnimator;

    private final Runnable blinkTask = new Runnable() {
        @Override public void run() { if (running) blinkNow(random.nextInt(7) == 0); }
    };

    public CelineFaceOverlayView(Context context) {
        super(context);
        setWillNotDraw(false);
        source = BitmapFactory.decodeResource(getResources(), de.yahya.ai.R.drawable.celine_avatar);
        lidPaint.setStyle(Paint.Style.STROKE); lidPaint.setStrokeCap(Paint.Cap.ROUND); lidPaint.setColor(0xCC2B2024);
    }

    public void start() { if (!running) { running = true; scheduleNext(); } }
    public void stop() {
        running = false; handler.removeCallbacks(blinkTask);
        if (blinkAnimator != null) blinkAnimator.cancel();
        blink = mouthLevel = visemeOpen = visemeWide = visemeRound = 0f;
        visemeShape = SpeechVisemeAnalyzer.Shape.CLOSED; invalidate();
    }

    public void setActivity(Activity next) {
        activity = next == null ? Activity.IDLE : next;
        if (activity != Activity.SPEAKING) { setMouthLevel(0f); setViseme(SpeechVisemeAnalyzer.silent()); }
        if (running) { handler.removeCallbacks(blinkTask); scheduleNext(); }
    }

    public void setMouthLevel(float level) {
        float next = activity == Activity.SPEAKING ? clamp(level) : 0f;
        mouthLevel = mouthLevel * 0.30f + next * 0.70f;
        if (mouthLevel < 0.025f) mouthLevel = 0f;
        invalidate();
    }

    public void setViseme(SpeechVisemeAnalyzer.Cue cue) {
        if (cue == null || activity != Activity.SPEAKING) cue = SpeechVisemeAnalyzer.silent();
        visemeShape = cue.shape;
        visemeOpen = visemeOpen * 0.30f + cue.openness * 0.70f;
        visemeWide = visemeWide * 0.35f + cue.width * 0.65f;
        visemeRound = visemeRound * 0.35f + cue.roundness * 0.65f;
        invalidate();
    }

    public void blinkNow(boolean doubleBlink) {
        if (blinkAnimator != null && blinkAnimator.isRunning()) return;
        float[] values = doubleBlink ? new float[]{0f,1f,0f,0f,1f,0f} : new float[]{0f,1f,0f};
        blinkAnimator = ValueAnimator.ofFloat(values);
        blinkAnimator.setDuration(doubleBlink ? 430L : 190L);
        blinkAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        blinkAnimator.addUpdateListener(a -> { blink = (Float)a.getAnimatedValue(); invalidate(); });
        blinkAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                blink = 0f; invalidate(); if (running) scheduleNext();
            }
        });
        handler.removeCallbacks(blinkTask); blinkAnimator.start();
    }

    private void scheduleNext() {
        if (!running) return;
        long base = activity == Activity.LISTENING ? 2200L : activity == Activity.THINKING ? 3000L : activity == Activity.SPEAKING ? 2600L : 3400L;
        handler.postDelayed(blinkTask, base + 900L + random.nextInt(2800));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (source == null || source.isRecycled() || getWidth() <= 0 || getHeight() <= 0) return;
        if (mouthLevel > 0.01f || visemeOpen > 0.01f) drawVisemeMouth(canvas);
        if (blink > 0.01f) { drawBlinkEye(canvas, LEFT_L, LEFT_R); drawBlinkEye(canvas, RIGHT_L, RIGHT_R); }
    }

    private void drawVisemeMouth(Canvas canvas) {
        float bw = source.getWidth(), bh = source.getHeight(), vw = getWidth(), vh = getHeight();
        float imageScale = Math.max(vw / bw, vh / bh);
        float dx = (vw - bw * imageScale) * 0.5f, dy = (vh - bh * imageScale) * 0.5f;
        RectF imageRect = new RectF(dx, dy, dx + bw * imageScale, dy + bh * imageScale);
        RectF mouth = new RectF(dx + MOUTH_L*bw*imageScale, dy + MOUTH_T*bh*imageScale,
                dx + MOUTH_R*bw*imageScale, dy + MOUTH_B*bh*imageScale);

        float openSignal = Math.max(mouthLevel, visemeOpen);
        float sx = 1f, sy = 1f + 0.16f * openSignal;
        switch (visemeShape) {
            case WIDE: sx += 0.055f * visemeWide; sy += 0.035f * openSignal; break;
            case ROUND: sx -= 0.045f * visemeRound; sy += 0.060f * openSignal; break;
            case OPEN: sy += 0.055f * openSignal; break;
            case CLOSED:
            default: sy = 1f + 0.025f * mouthLevel; break;
        }
        float cx = mouth.centerX(), cy = mouth.centerY();
        bitmapPaint.setAlpha(255);
        canvas.save(); canvas.clipRect(mouth);
        canvas.translate(cx, cy); canvas.scale(sx, sy); canvas.translate(-cx, -cy);
        canvas.drawBitmap(source, null, imageRect, bitmapPaint); canvas.restore();
    }

    private void drawBlinkEye(Canvas canvas, float leftN, float rightN) {
        float bw=source.getWidth(), bh=source.getHeight(), vw=getWidth(), vh=getHeight();
        float scale=Math.max(vw/bw,vh/bh), dx=(vw-bw*scale)*0.5f, dy=(vh-bh*scale)*0.5f;
        float sx0=leftN*bw,sx1=rightN*bw,sy0=EYE_T*bh,sy1=EYE_B*bh;
        RectF eye=new RectF(dx+sx0*scale,dy+sy0*scale,dx+sx1*scale,dy+sy1*scale);
        int srcL=clampInt((int)sx0,0,source.getWidth()-1), srcR=clampInt((int)sx1,srcL+1,source.getWidth());
        int eyeH=Math.max(2,(int)(sy1-sy0)), srcB=clampInt((int)sy0,2,source.getHeight()), srcT=clampInt(srcB-eyeH,0,srcB-1);
        Rect skinSource=new Rect(srcL,srcT,srcR,srcB);
        float close=Math.min(1f,blink*1.18f);
        RectF cover=new RectF(eye.left,eye.top,eye.right,eye.top+eye.height()*close);
        bitmapPaint.setAlpha((int)(255f*close)); canvas.save(); canvas.clipRect(eye); canvas.drawBitmap(source,skinSource,cover,bitmapPaint); canvas.restore();
        if(blink>0.42f){
            float lineY=eye.centerY()+eye.height()*0.08f,inset=eye.width()*0.12f;
            Path p=new Path();p.moveTo(eye.left+inset,lineY);p.quadTo(eye.centerX(),lineY+eye.height()*0.12f,eye.right-inset,lineY);
            lidPaint.setStrokeWidth(Math.max(1.4f,eye.height()*0.045f));lidPaint.setAlpha((int)(220f*Math.min(1f,(blink-0.42f)/0.58f)));canvas.drawPath(p,lidPaint);
        }
    }

    private static float clamp(float v){return Math.max(0f,Math.min(1f,v));}
    private static int clampInt(int v,int min,int max){return Math.max(min,Math.min(max,v));}
}
