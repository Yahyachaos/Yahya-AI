package de.yahya.ai;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
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

/** Facial rig calibrated for the approved Celin live-call portraits. */
public final class CelineFaceOverlayView extends View {
    public enum Activity { IDLE, LISTENING, THINKING, SPEAKING }

    private static final float LEFT_L=.425f, LEFT_R=.495f;
    private static final float RIGHT_L=.515f, RIGHT_R=.590f;
    private static final float EYE_T=.255f, EYE_B=.335f;
    private static final float MOUTH_L=.455f, MOUTH_R=.590f;
    private static final float MOUTH_T=.405f, MOUTH_B=.495f;

    private Bitmap source;
    private CelineLivePortrait.Pose pose=CelineLivePortrait.Pose.NEUTRAL;
    private final Paint bitmapPaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Paint lidPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cavityPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Random random=new Random();

    private boolean running;
    private Activity activity=Activity.IDLE;
    private float blink,mouthLevel,visemeOpen,visemeWide,visemeRound,gazeX,gazeY;
    private SpeechVisemeAnalyzer.Shape visemeShape=SpeechVisemeAnalyzer.Shape.CLOSED;
    private ValueAnimator blinkAnimator;

    private final Runnable blinkTask=new Runnable(){
        @Override public void run(){ if(running) blinkNow(random.nextInt(10)==0); }
    };

    public CelineFaceOverlayView(Context context){
        super(context);
        setWillNotDraw(false);
        source=CelineLivePortrait.load(context,pose);
        if(source==null)source=android.graphics.BitmapFactory.decodeResource(getResources(),de.yahya.ai.R.drawable.celine_avatar);
        lidPaint.setStyle(Paint.Style.STROKE);
        lidPaint.setStrokeCap(Paint.Cap.ROUND);
        lidPaint.setColor(0xB92B1D20);
        cavityPaint.setColor(0xD11B080D);
    }

    public void start(){ if(!running){ running=true; scheduleNext(); } }
    public void stop(){ running=false; handler.removeCallbacks(blinkTask); if(blinkAnimator!=null)blinkAnimator.cancel(); blink=mouthLevel=visemeOpen=visemeWide=visemeRound=gazeX=gazeY=0f; invalidate(); }

    public void setPose(CelineLivePortrait.Pose next){
        if(next==null)next=CelineLivePortrait.Pose.NEUTRAL;
        if(next==pose&&source!=null&&!source.isRecycled())return;
        Bitmap b=CelineLivePortrait.load(getContext(),next);
        if(b!=null){pose=next;source=b;blink=0f;gazeX=gazeY=0f;invalidate();}
    }

    public void setActivity(Activity next){
        activity=next==null?Activity.IDLE:next;
        if(activity!=Activity.SPEAKING){ mouthLevel=visemeOpen=visemeWide=visemeRound=0f; visemeShape=SpeechVisemeAnalyzer.Shape.CLOSED; }
        if(running){ handler.removeCallbacks(blinkTask); scheduleNext(); }
        invalidate();
    }

    public void setGaze(float x,float y){ gazeX=clampSigned(x)*.48f; gazeY=clampSigned(y)*.30f; invalidate(); }
    public void releaseGaze(){
        final float sx=gazeX,sy=gazeY;
        ValueAnimator a=ValueAnimator.ofFloat(1f,0f); a.setDuration(360); a.setInterpolator(new AccelerateDecelerateInterpolator());
        a.addUpdateListener(v->{float f=(Float)v.getAnimatedValue();gazeX=sx*f;gazeY=sy*f;invalidate();}); a.start();
    }

    public void setMouthLevel(float level){
        float next=activity==Activity.SPEAKING?clamp(level):0f;
        float attack=next>mouthLevel?.82f:.42f;
        mouthLevel=mouthLevel*(1f-attack)+next*attack;
        if(mouthLevel<.01f)mouthLevel=0f;
        invalidate();
    }

    public void setViseme(SpeechVisemeAnalyzer.Cue cue){
        if(cue==null||activity!=Activity.SPEAKING)cue=SpeechVisemeAnalyzer.silent();
        visemeShape=cue.shape;
        visemeOpen=visemeOpen*.18f+cue.openness*.82f;
        visemeWide=visemeWide*.28f+cue.width*.72f;
        visemeRound=visemeRound*.28f+cue.roundness*.72f;
        invalidate();
    }

    public void blinkNow(boolean doubleBlink){
        if(blinkAnimator!=null&&blinkAnimator.isRunning())return;
        float[] values=doubleBlink?new float[]{0,1,0,0,1,0}:new float[]{0,1,0};
        blinkAnimator=ValueAnimator.ofFloat(values); blinkAnimator.setDuration(doubleBlink?420:178); blinkAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        blinkAnimator.addUpdateListener(a->{blink=(Float)a.getAnimatedValue();invalidate();});
        blinkAnimator.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator animation){blink=0;invalidate();if(running)scheduleNext();}});
        handler.removeCallbacks(blinkTask); blinkAnimator.start();
    }

    private void scheduleNext(){ if(!running)return; long base=activity==Activity.LISTENING?1500:activity==Activity.SPEAKING?1850:2600; handler.postDelayed(blinkTask,base+random.nextInt(2500)); }

    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        if(source==null||source.isRecycled()||getWidth()<=0||getHeight()<=0)return;
        if(Math.abs(gazeX)>.01f||Math.abs(gazeY)>.01f){drawEyePatch(canvas,LEFT_L,LEFT_R);drawEyePatch(canvas,RIGHT_L,RIGHT_R);}
        if(activity==Activity.SPEAKING&&(mouthLevel>.01f||visemeOpen>.01f))drawMouth(canvas);
        if(blink>.01f){drawBlink(canvas,LEFT_L,LEFT_R);drawBlink(canvas,RIGHT_L,RIGHT_R);}
    }

    private float[] transform(){float bw=source.getWidth(),bh=source.getHeight(),vw=getWidth(),vh=getHeight();float scale=Math.max(vw/bw,vh/bh);return new float[]{bw,bh,scale,(vw-bw*scale)*.5f,(vh-bh*scale)*.5f};}

    private void drawMouth(Canvas canvas){
        float[] t=transform();float bw=t[0],bh=t[1],s=t[2],dx=t[3],dy=t[4];
        float sl=MOUTH_L*bw,sr=MOUTH_R*bw,st=MOUTH_T*bh,sb=MOUTH_B*bh,sm=(st+sb)*.5f;
        Rect up=new Rect((int)sl,(int)st,(int)sr,(int)sm),lo=new Rect((int)sl,(int)sm,(int)sr,(int)sb);
        RectF m=new RectF(dx+sl*s,dy+st*s,dx+sr*s,dy+sb*s);
        float open=clamp(Math.max(mouthLevel*.76f,visemeOpen*.88f)); if(visemeShape==SpeechVisemeAnalyzer.Shape.CLOSED)open*=.08f;
        float width=1f+(visemeShape==SpeechVisemeAnalyzer.Shape.WIDE?.08f*visemeWide:0f)-(visemeShape==SpeechVisemeAnalyzer.Shape.ROUND?.07f*visemeRound:0f);
        float gap=m.height()*(.025f+.27f*open),cx=m.centerX(),half=m.width()*.5f*width,left=cx-half,right=cx+half,mid=m.centerY();
        if(open>.06f){RectF cavity=new RectF(left+m.width()*.12f,mid-gap*.10f,right-m.width()*.12f,mid+gap*.62f);cavityPaint.setAlpha((int)(90+90*open));canvas.drawOval(cavity,cavityPaint);}
        RectF ud=new RectF(left,m.top-gap*.08f,right,mid-gap*.20f),ld=new RectF(left,mid+gap*.27f,right,m.bottom+gap*.16f);
        canvas.save();canvas.clipRect(new RectF(left,m.top-m.height()*.15f,right,m.bottom+m.height()*.22f));canvas.drawBitmap(source,up,ud,bitmapPaint);canvas.drawBitmap(source,lo,ld,bitmapPaint);canvas.restore();
    }

    private void drawEyePatch(Canvas canvas,float ln,float rn){
        float[] t=transform();float bw=t[0],bh=t[1],s=t[2],dx=t[3],dy=t[4];int sl=ci((int)(ln*bw),0,source.getWidth()-2),sr=ci((int)(rn*bw),sl+1,source.getWidth()),st=ci((int)(EYE_T*bh),0,source.getHeight()-2),sb=ci((int)(EYE_B*bh),st+1,source.getHeight());
        Rect src=new Rect(sl,st,sr,sb);RectF dst=new RectF(dx+sl*s,dy+st*s,dx+sr*s,dy+sb*s);RectF clip=new RectF(dst);dst.offset(gazeX*dst.width()*.055f,gazeY*dst.height()*.075f);canvas.save();canvas.clipRect(clip);bitmapPaint.setAlpha(245);canvas.drawBitmap(source,src,dst,bitmapPaint);canvas.restore();bitmapPaint.setAlpha(255);
    }

    private void drawBlink(Canvas canvas,float ln,float rn){
        float[] t=transform();float bw=t[0],bh=t[1],s=t[2],dx=t[3],dy=t[4];float sx0=ln*bw,sx1=rn*bw,sy0=EYE_T*bh,sy1=EYE_B*bh;RectF eye=new RectF(dx+sx0*s,dy+sy0*s,dx+sx1*s,dy+sy1*s);
        int sl=ci((int)sx0,0,source.getWidth()-1),sr=ci((int)sx1,sl+1,source.getWidth()),h=Math.max(2,(int)(sy1-sy0)),sb=ci((int)sy0,2,source.getHeight()),st=ci(sb-h,0,sb-1);Rect skin=new Rect(sl,st,sr,sb);float close=Math.min(1f,blink*1.16f);RectF cover=new RectF(eye.left,eye.top,eye.right,eye.top+eye.height()*close);bitmapPaint.setAlpha((int)(255*close));canvas.save();canvas.clipRect(eye);canvas.drawBitmap(source,skin,cover,bitmapPaint);canvas.restore();bitmapPaint.setAlpha(255);
        if(blink>.43f){float y=eye.centerY()+eye.height()*.06f,inset=eye.width()*.12f;Path p=new Path();p.moveTo(eye.left+inset,y);p.quadTo(eye.centerX(),y+eye.height()*.09f,eye.right-inset,y);lidPaint.setStrokeWidth(Math.max(1.2f,eye.height()*.04f));lidPaint.setAlpha((int)(200*Math.min(1f,(blink-.43f)/.57f)));canvas.drawPath(p,lidPaint);}
    }

    private static float clamp(float v){return Math.max(0f,Math.min(1f,v));}
    private static float clampSigned(float v){return Math.max(-1f,Math.min(1f,v));}
    private static int ci(int v,int min,int max){return Math.max(min,Math.min(max,v));}
}
