package de.yahya.ai;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import java.util.Random;

/** Lightweight 2.5D facial rig drawn over Celine's approved portrait. */
public final class CelineFaceOverlayView extends View {
    public enum Activity { IDLE, LISTENING, THINKING, SPEAKING }

    // Calibrated from the real S25 Ultra screen recording supplied on 2026-08-22.
    private static final float LEFT_L = 0.365f, LEFT_R = 0.500f;
    private static final float RIGHT_L = 0.545f, RIGHT_R = 0.690f;
    private static final float EYE_T = 0.345f, EYE_B = 0.420f;
    private static final float MOUTH_L = 0.480f, MOUTH_R = 0.690f;
    private static final float MOUTH_T = 0.580f, MOUTH_B = 0.650f;

    private final Bitmap source;
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint lidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cavityPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tonguePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private float blink = 0f;
    private float mouthLevel = 0f;
    private float visemeOpen = 0f, visemeWide = 0f, visemeRound = 0f;
    private float gazeX = 0f, gazeY = 0f;
    private boolean running;
    private Activity activity = Activity.IDLE;
    private SpeechVisemeAnalyzer.Shape visemeShape = SpeechVisemeAnalyzer.Shape.CLOSED;
    private ValueAnimator blinkAnimator;

    private final Runnable blinkTask = new Runnable() {
        @Override public void run() { if (running) blinkNow(random.nextInt(9) == 0); }
    };

    public CelineFaceOverlayView(Context context) {
        super(context);
        setWillNotDraw(false);
        source = BitmapFactory.decodeResource(getResources(), de.yahya.ai.R.drawable.celine_avatar);
        lidPaint.setStyle(Paint.Style.STROKE);
        lidPaint.setStrokeCap(Paint.Cap.ROUND);
        lidPaint.setColor(0xC72E2025);
        cavityPaint.setColor(0xC9240C12);
        tonguePaint.setColor(0x9C6F3340);
    }

    public void start() { if (!running) { running = true; scheduleNext(); } }

    public void stop() {
        running = false;
        handler.removeCallbacks(blinkTask);
        if (blinkAnimator != null) blinkAnimator.cancel();
        blink = mouthLevel = visemeOpen = visemeWide = visemeRound = gazeX = gazeY = 0f;
        invalidate();
    }

    public void setActivity(Activity next) {
        activity = next == null ? Activity.IDLE : next;
        if (activity != Activity.SPEAKING) {
            mouthLevel = visemeOpen = visemeWide = visemeRound = 0f;
            visemeShape = SpeechVisemeAnalyzer.Shape.CLOSED;
        }
        if (running) { handler.removeCallbacks(blinkTask); scheduleNext(); }
        invalidate();
    }

    public void setGaze(float x, float y) {
        gazeX = clampSigned(x) * 0.45f;
        gazeY = clampSigned(y) * 0.28f;
        invalidate();
    }

    public void releaseGaze() {
        final float sx = gazeX, sy = gazeY;
        ValueAnimator a = ValueAnimator.ofFloat(1f, 0f);
        a.setDuration(360L);
        a.setInterpolator(new AccelerateDecelerateInterpolator());
        a.addUpdateListener(v -> { float f=(Float)v.getAnimatedValue(); gazeX=sx*f; gazeY=sy*f; invalidate(); });
        a.start();
    }

    public void setMouthLevel(float level) {
        float next = activity == Activity.SPEAKING ? clamp(level) : 0f;
        float attack = next > mouthLevel ? 0.88f : 0.50f;
        mouthLevel = mouthLevel * (1f - attack) + next * attack;
        if (mouthLevel < 0.012f) mouthLevel = 0f;
        invalidate();
    }

    public void setViseme(SpeechVisemeAnalyzer.Cue cue) {
        if (cue == null || activity != Activity.SPEAKING) cue = SpeechVisemeAnalyzer.silent();
        visemeShape = cue.shape;
        visemeOpen = visemeOpen * 0.18f + cue.openness * 0.82f;
        visemeWide = visemeWide * 0.30f + cue.width * 0.70f;
        visemeRound = visemeRound * 0.30f + cue.roundness * 0.70f;
        invalidate();
    }

    public void blinkNow(boolean doubleBlink) {
        if (blinkAnimator != null && blinkAnimator.isRunning()) return;
        float[] values = doubleBlink ? new float[]{0f,1f,0f,0f,1f,0f} : new float[]{0f,1f,0f};
        blinkAnimator = ValueAnimator.ofFloat(values);
        blinkAnimator.setDuration(doubleBlink ? 430L : 185L);
        blinkAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        blinkAnimator.addUpdateListener(a -> { blink=(Float)a.getAnimatedValue(); invalidate(); });
        blinkAnimator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) { blink=0f; invalidate(); if(running)scheduleNext(); }
        });
        handler.removeCallbacks(blinkTask);
        blinkAnimator.start();
    }

    private void scheduleNext() {
        if (!running) return;
        long base = activity == Activity.LISTENING ? 1500L : activity == Activity.SPEAKING ? 1800L : 2500L;
        handler.postDelayed(blinkTask, base + random.nextInt(2600));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (source == null || source.isRecycled() || getWidth() <= 0 || getHeight() <= 0) return;
        if (Math.abs(gazeX) > .01f || Math.abs(gazeY) > .01f) {
            drawEyePatch(canvas, LEFT_L, LEFT_R);
            drawEyePatch(canvas, RIGHT_L, RIGHT_R);
        }
        if (activity == Activity.SPEAKING && (mouthLevel > .01f || visemeOpen > .01f)) drawMouth(canvas);
        if (blink > .01f) {
            drawBlink(canvas, LEFT_L, LEFT_R);
            drawBlink(canvas, RIGHT_L, RIGHT_R);
        }
    }

    private void drawMouth(Canvas canvas) {
        float bw=source.getWidth(), bh=source.getHeight(), vw=getWidth(), vh=getHeight();
        float scale=Math.max(vw/bw,vh/bh), dx=(vw-bw*scale)*.5f, dy=(vh-bh*scale)*.5f;
        float sl=MOUTH_L*bw, sr=MOUTH_R*bw, st=MOUTH_T*bh, sb=MOUTH_B*bh, sm=(st+sb)*.5f;
        Rect upperSrc=new Rect((int)sl,(int)st,(int)sr,(int)sm);
        Rect lowerSrc=new Rect((int)sl,(int)sm,(int)sr,(int)sb);
        RectF mouth=new RectF(dx+sl*scale,dy+st*scale,dx+sr*scale,dy+sb*scale);

        float open=clamp(Math.max(mouthLevel*.78f,visemeOpen*.86f));
        if(visemeShape==SpeechVisemeAnalyzer.Shape.CLOSED) open*=.10f;
        float width=1f + (visemeShape==SpeechVisemeAnalyzer.Shape.WIDE ? .08f*visemeWide : 0f)
                - (visemeShape==SpeechVisemeAnalyzer.Shape.ROUND ? .08f*visemeRound : 0f);
        float gap=mouth.height()*(.035f+.30f*open);
        float cx=mouth.centerX(), half=mouth.width()*.5f*width, left=cx-half, right=cx+half, mid=mouth.centerY();

        if(open>.07f){
            RectF cavity=new RectF(left+mouth.width()*.10f,mid-gap*.10f,right-mouth.width()*.10f,mid+gap*.66f);
            cavityPaint.setAlpha((int)(95+85*open));
            canvas.drawOval(cavity,cavityPaint);
            if(open>.55f){
                RectF tongue=new RectF(cavity.left+cavity.width()*.24f,cavity.centerY()+cavity.height()*.16f,
                        cavity.right-cavity.width()*.24f,cavity.bottom-cavity.height()*.07f);
                tonguePaint.setAlpha((int)(55+55*open));
                canvas.drawOval(tongue,tonguePaint);
            }
        }

        RectF upperDst=new RectF(left,mouth.top-gap*.12f,right,mid-gap*.22f);
        RectF lowerDst=new RectF(left,mid+gap*.28f,right,mouth.bottom+gap*.18f);
        canvas.save();
        canvas.clipRect(new RectF(left,mouth.top-mouth.height()*.18f,right,mouth.bottom+mouth.height()*.26f));
        bitmapPaint.setAlpha(255);
        canvas.drawBitmap(source,upperSrc,upperDst,bitmapPaint);
        canvas.drawBitmap(source,lowerSrc,lowerDst,bitmapPaint);
        canvas.restore();
    }

    private void drawEyePatch(Canvas canvas,float leftN,float rightN){
        float bw=source.getWidth(), bh=source.getHeight(), vw=getWidth(), vh=getHeight();
        float scale=Math.max(vw/bw,vh/bh), dx=(vw-bw*scale)*.5f, dy=(vh-bh*scale)*.5f;
        int sl=clampInt((int)(leftN*bw),0,source.getWidth()-2), sr=clampInt((int)(rightN*bw),sl+1,source.getWidth());
        int st=clampInt((int)(EYE_T*bh),0,source.getHeight()-2), sb=clampInt((int)(EYE_B*bh),st+1,source.getHeight());
        Rect src=new Rect(sl,st,sr,sb);
        RectF dst=new RectF(dx+sl*scale,dy+st*scale,dx+sr*scale,dy+sb*scale);
        dst.offset(gazeX*dst.width()*.035f,gazeY*dst.height()*.060f);
        canvas.save(); canvas.clipRect(new RectF(dx+sl*scale,dy+st*scale,dx+sr*scale,dy+sb*scale));
        bitmapPaint.setAlpha(238); canvas.drawBitmap(source,src,dst,bitmapPaint); canvas.restore();
    }

    private void drawBlink(Canvas canvas,float leftN,float rightN){
        float bw=source.getWidth(), bh=source.getHeight(), vw=getWidth(), vh=getHeight();
        float scale=Math.max(vw/bw,vh/bh), dx=(vw-bw*scale)*.5f, dy=(vh-bh*scale)*.5f;
        float sx0=leftN*bw,sx1=rightN*bw,sy0=EYE_T*bh,sy1=EYE_B*bh;
        RectF eye=new RectF(dx+sx0*scale,dy+sy0*scale,dx+sx1*scale,dy+sy1*scale);
        int sl=clampInt((int)sx0,0,source.getWidth()-1), sr=clampInt((int)sx1,sl+1,source.getWidth());
        int h=Math.max(2,(int)(sy1-sy0)), sb=clampInt((int)sy0,2,source.getHeight()), st=clampInt(sb-h,0,sb-1);
        Rect skin=new Rect(sl,st,sr,sb);
        float close=Math.min(1f,blink*1.18f);
        RectF cover=new RectF(eye.left,eye.top,eye.right,eye.top+eye.height()*close);
        bitmapPaint.setAlpha((int)(255*close)); canvas.save(); canvas.clipRect(eye); canvas.drawBitmap(source,skin,cover,bitmapPaint); canvas.restore();
        if(blink>.42f){
            float y=eye.centerY()+eye.height()*.08f,inset=eye.width()*.12f; Path p=new Path(); p.moveTo(eye.left+inset,y); p.quadTo(eye.centerX(),y+eye.height()*.10f,eye.right-inset,y);
            lidPaint.setStrokeWidth(Math.max(1.4f,eye.height()*.045f)); lidPaint.setAlpha((int)(205*Math.min(1f,(blink-.42f)/.58f))); canvas.drawPath(p,lidPaint);
        }
    }

    private static float clamp(float v){return Math.max(0f,Math.min(1f,v));}
    private static float clampSigned(float v){return Math.max(-1f,Math.min(1f,v));}
    private static int clampInt(int v,int min,int max){return Math.max(min,Math.min(max,v));}
}
