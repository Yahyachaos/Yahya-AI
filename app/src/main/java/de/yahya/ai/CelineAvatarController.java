package de.yahya.ai;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.util.Random;

/** Drives Celin as a live avatar. Uses the rigged GLB renderer whenever models/celine.glb exists. */
public final class CelineAvatarController implements SpeechAudioBus.Listener {
    public enum State { IDLE, LISTENING, THINKING, SPEAKING }

    private static final String PREF_3D_LOADING = "celine_3d_load_in_progress";

    private final View motionView;
    private final ImageView avatar;
    private final CelineFaceOverlayView face;
    private final float density;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Random random=new Random();

    private Celine3DView threeD;
    private Celine3DView pending3D;
    private boolean using3D;
    private ObjectAnimator breath,lift;
    private State state=State.IDLE;
    private boolean released,userLooking,gestureRunning;
    private float speechEnergy;
    private CelineLivePortrait.Pose pose=CelineLivePortrait.Pose.NEUTRAL;

    private final Runnable microMotionTask=new Runnable(){
        @Override public void run(){if(released||motionView==null||using3D)return;if(!userLooking&&!gestureRunning)playMicroMotion();scheduleMicroMotion();}
    };
    private final Runnable gazeTask=new Runnable(){
        @Override public void run(){if(released||face==null||using3D)return;if(!userLooking)playNaturalGaze();scheduleGaze();}
    };
    private final Runnable gestureTask=new Runnable(){
        @Override public void run(){if(released||motionView==null||using3D)return;if(!userLooking)playBodyGesture();scheduleGesture();}
    };
    private final Runnable settleSmileTask=new Runnable(){
        @Override public void run(){if(!released&&state==State.IDLE&&!using3D)applyPose(CelineLivePortrait.Pose.NEUTRAL);}
    };

    public CelineAvatarController(View motionView,ImageView avatar,CelineFaceOverlayView face,float density){
        this.motionView=motionView;this.avatar=avatar;this.face=face;this.density=density;
        applyPose(CelineLivePortrait.Pose.NEUTRAL);
        if(face!=null)face.start();
        if(motionView!=null)motionView.post(()->{motionView.setPivotX(motionView.getWidth()*.50f);motionView.setPivotY(motionView.getHeight()*.82f);});
        scheduleMicroMotion();scheduleGaze();scheduleGesture();
        schedule3DEnable();
        SpeechAudioBus.setListener(this);
    }

    private void schedule3DEnable(){
        if(!(motionView instanceof ViewGroup)||avatar==null)return;
        final Context context=avatar.getContext();
        if(!Celine3DView.hasModel(context))return;

        final SharedPreferences prefs=context.getSharedPreferences("yahya_ai",Context.MODE_PRIVATE);
        if(prefs.getBoolean(PREF_3D_LOADING,false)){
            prefs.edit().putBoolean(PREF_3D_LOADING,false).commit();
            File imported=Celine3DView.importedModelFile(context);
            if(imported.isFile()){
                File failed=new File(imported.getParentFile(),"celine.failed.glb");
                if(failed.exists())failed.delete();
                imported.renameTo(failed);
            }
            Toast.makeText(context,"Der zuletzt importierte 3D-Avatar wurde nach einem Absturz sicher deaktiviert. Yahya AI bleibt benutzbar.",Toast.LENGTH_LONG).show();
            return;
        }
        motionView.post(()->tryEnable3D(prefs));
    }

    private void tryEnable3D(SharedPreferences prefs){
        if(released||using3D||pending3D!=null||!(motionView instanceof ViewGroup)||avatar==null)return;
        Context context=avatar.getContext();
        if(!Celine3DView.hasModel(context))return;
        prefs.edit().putBoolean(PREF_3D_LOADING,true).commit();

        // Freeze only the parent micro-motion while the renderer is being tested. The normal 2D
        // Celine image remains visible until a real 3D frame has been verified.
        stopLoopsOnly();
        handler.removeCallbacks(microMotionTask);
        handler.removeCallbacks(gazeTask);
        handler.removeCallbacks(gestureTask);
        handler.removeCallbacks(settleSmileTask);
        tryRenderer(prefs,true);
    }

    private void tryRenderer(SharedPreferences prefs,boolean surfaceFirst){
        if(released||using3D||!(motionView instanceof ViewGroup)||avatar==null){prefs.edit().putBoolean(PREF_3D_LOADING,false).commit();return;}
        Context context=avatar.getContext();
        try{
            ViewGroup host=(ViewGroup)motionView;
            Celine3DView candidate=new Celine3DView(context,surfaceFirst);
            if(released){candidate.stopRendering();prefs.edit().putBoolean(PREF_3D_LOADING,false).commit();return;}
            pending3D=candidate;
            candidate.setAvatarState(state);
            host.addView(candidate,0,new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));

            // Do NOT hide the 2D avatar yet. v26 hid it immediately, which exposed a completely
            // black TextureView even though no visible 3D frame had ever been produced.
            avatar.setVisibility(View.VISIBLE);
            if(face!=null){face.setVisibility(View.VISIBLE);face.start();}

            candidate.verifyVisibleFrame(handler,visible->{
                if(released||pending3D!=candidate){removeCandidate(host,candidate);return;}
                if(visible){activateVerified3D(prefs,host,candidate);return;}

                String failedRenderer=candidate.getRendererName();
                String reason=candidate.getRenderFailureReason();
                pending3D=null;
                removeCandidate(host,candidate);

                if(surfaceFirst){
                    // If SurfaceView is not usable on this exact device/driver, probe TextureView
                    // once as an alternate path. The 2D avatar stays on-screen during this test.
                    handler.postDelayed(()->tryRenderer(prefs,false),180L);
                }else{
                    prefs.edit().putBoolean(PREF_3D_LOADING,false).commit();
                    threeD=null;using3D=false;
                    avatar.setVisibility(View.VISIBLE);
                    if(face!=null){face.setVisibility(View.VISIBLE);face.start();}
                    setState(state);
                    String extra=reason==null?"":" ("+reason+")";
                    Toast.makeText(context,"3D-Celin blieb im "+failedRenderer+"-Renderer schwarz"+extra+". Das normale Celine-Bild bleibt deshalb aktiv.",Toast.LENGTH_LONG).show();
                }
            });
        }catch(Throwable e){
            pending3D=null;
            if(surfaceFirst){
                handler.postDelayed(()->tryRenderer(prefs,false),180L);
                return;
            }
            prefs.edit().putBoolean(PREF_3D_LOADING,false).commit();
            threeD=null;using3D=false;
            avatar.setVisibility(View.VISIBLE);
            if(face!=null){face.setVisibility(View.VISIBLE);face.start();}
            String reason=e.getMessage();if(reason==null||reason.trim().isEmpty())reason=e.getClass().getSimpleName();
            Toast.makeText(context,"3D-Avatar konnte nicht sichtbar gestartet werden: "+reason,Toast.LENGTH_LONG).show();
            setState(state);
        }
    }

    private void activateVerified3D(SharedPreferences prefs,ViewGroup host,Celine3DView candidate){
        if(released||pending3D!=candidate){removeCandidate(host,candidate);return;}
        pending3D=null;
        threeD=candidate;
        using3D=true;
        threeD.setAvatarState(state);
        avatar.setVisibility(View.GONE);
        if(face!=null){face.stop();face.setVisibility(View.GONE);}
        prefs.edit().putBoolean(PREF_3D_LOADING,false).commit();
        Toast.makeText(avatar.getContext(),"3D-Celin ist sichtbar geladen ("+candidate.getRendererName()+").",Toast.LENGTH_SHORT).show();
    }

    private static void removeCandidate(ViewGroup host,Celine3DView candidate){
        try{candidate.stopRendering();}catch(Throwable ignored){}
        try{host.removeView(candidate);}catch(Throwable ignored){}
    }

    public State getState(){return state;}
    public boolean isUsing3D(){return using3D;}

    public void setState(State next){
        if(next==null)next=State.IDLE;
        State previous=state;state=next;
        if(pending3D!=null)pending3D.setAvatarState(next);
        if(using3D&&threeD!=null){threeD.setAvatarState(next);return;}
        stopLoopsOnly();handler.removeCallbacks(settleSmileTask);
        switch(next){
            case LISTENING:applyPose(CelineLivePortrait.Pose.LISTENING);break;
            case THINKING:applyPose(CelineLivePortrait.Pose.LISTENING);break;
            case SPEAKING:applyPose(CelineLivePortrait.Pose.SPEAKING);break;
            case IDLE:
            default:if(previous==State.SPEAKING){applyPose(CelineLivePortrait.Pose.SMILE);handler.postDelayed(settleSmileTask,1200L);}else applyPose(CelineLivePortrait.Pose.NEUTRAL);break;
        }
        syncFaceState(next);
        switch(next){
            case LISTENING:startBreath(.998f,1.010f,3200);startLift(dp(.2f),-dp(1.4f),3600);break;
            case THINKING:startBreath(.998f,1.009f,4000);startLift(dp(.2f),-dp(1.1f),4400);break;
            case SPEAKING:startBreath(.998f,1.013f,2600);startLift(dp(.2f),-dp(1.5f),3100);break;
            default:startBreath(.999f,1.008f,4800);startLift(dp(.1f),-dp(1.0f),5400);break;
        }
        scheduleGesture();
    }

    @Override public void onSpeechAudioLevel(float level){
        float c=clamp(level,0f,1f);speechEnergy=speechEnergy*.76f+c*.24f;
        if(pending3D!=null)pending3D.setSpeechEnergy(state==State.SPEAKING?speechEnergy:0f);
        if(using3D&&threeD!=null){threeD.setSpeechEnergy(state==State.SPEAKING?speechEnergy:0f);return;}
        if(face!=null)face.setMouthLevel(state==State.SPEAKING?speechEnergy:0f);
    }

    @Override public void onSpeechViseme(SpeechVisemeAnalyzer.Cue cue){
        SpeechVisemeAnalyzer.Cue value=state==State.SPEAKING?cue:SpeechVisemeAnalyzer.silent();
        if(pending3D!=null)pending3D.setViseme(value);
        if(using3D&&threeD!=null){threeD.setViseme(value);return;}
        if(face!=null)face.setViseme(value);
    }

    public void lookToward(float nx,float ny){
        if(using3D&&threeD!=null){threeD.setLook(nx*2f,ny*2f);return;}
        userLooking=true;if(motionView==null)return;float x=clamp(nx,-.5f,.5f),y=clamp(ny,-.5f,.5f);motionView.animate().cancel();motionView.setTranslationX(x*dp(4.5f));motionView.setTranslationY(y*dp(2.4f));motionView.setRotation(x*.55f);if(face!=null)face.setGaze(x*2f,y*2f);
    }
    public void releaseLook(){if(using3D&&threeD!=null){threeD.releaseLook();return;}userLooking=false;if(motionView!=null)motionView.animate().translationX(0).translationY(0).rotation(0).scaleX(1).scaleY(1).setDuration(500).setInterpolator(new AccelerateDecelerateInterpolator()).start();if(face!=null)face.releaseGaze();}
    public void blink(){if(!using3D&&face!=null)face.blinkNow(false);}
    public void release(){released=true;handler.removeCallbacksAndMessages(null);SpeechAudioBus.clearListener(this);if(pending3D!=null)pending3D.stopRendering();if(threeD!=null)threeD.stopRendering();stopMotion();if(face!=null)face.stop();}

    private void applyPose(CelineLivePortrait.Pose next){
        if(using3D||avatar==null)return;if(next==null)next=CelineLivePortrait.Pose.NEUTRAL;
        Bitmap bitmap=CelineLivePortrait.load(avatar.getContext(),next);if(bitmap==null&&next!=CelineLivePortrait.Pose.NEUTRAL)bitmap=CelineLivePortrait.load(avatar.getContext(),CelineLivePortrait.Pose.NEUTRAL);
        if(bitmap!=null&&!bitmap.isRecycled()){pose=next;avatar.animate().cancel();avatar.setAlpha(.94f);avatar.setImageBitmap(bitmap);avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);avatar.animate().alpha(1f).setDuration(180L).start();if(face!=null)face.setPose(next);}else avatar.setImageResource(de.yahya.ai.R.drawable.celine_avatar);
    }

    private void playBodyGesture(){
        if(using3D||motionView==null||gestureRunning)return;gestureRunning=true;motionView.animate().cancel();
        float tx=0f,ty=0f,rot=0f,sx=1f,sy=1f;long out=520L,back=760L;
        switch(state){
            case LISTENING:tx=r(-dp(1.2f),dp(1.2f));ty=-dp(3.0f);rot=r(-.45f,.45f);sx=1.006f;sy=1.008f;out=430L;back=620L;break;
            case THINKING:tx=r(-dp(2.0f),dp(2.0f));ty=r(-dp(1.4f),dp(.8f));rot=r(-.9f,.9f);sx=.997f;sy=1.002f;out=700L;back=880L;break;
            case SPEAKING:tx=r(-dp(1.8f),dp(1.8f));ty=-dp(2.2f);rot=r(-.65f,.65f);sx=1.008f;sy=1.010f;out=360L+random.nextInt(220);back=560L;break;
            case IDLE:
            default:tx=r(-dp(1.2f),dp(1.2f));ty=r(-dp(.7f),dp(.5f));rot=r(-.35f,.35f);sx=1.002f;sy=1.003f;out=850L;back=1100L;break;
        }
        final long returnDuration=back;
        motionView.animate().translationX(tx).translationY(ty).rotation(rot).scaleX(sx).scaleY(sy).setDuration(out).setInterpolator(new DecelerateInterpolator()).withEndAction(()->{
            if(released||motionView==null){gestureRunning=false;return;}
            motionView.animate().translationX(0).translationY(0).rotation(0).scaleX(1).scaleY(1).setDuration(returnDuration).setInterpolator(new AccelerateDecelerateInterpolator()).withEndAction(()->gestureRunning=false).start();
        }).start();
        if(face!=null&&state==State.LISTENING&&random.nextInt(2)==0)face.blinkNow(false);
    }

    private void playMicroMotion(){
        if(using3D||motionView==null)return;float rot,x,y;long d;
        switch(state){case LISTENING:rot=r(-.28f,.28f);x=r(-dp(.8f),dp(.8f));y=r(-dp(1.8f),-dp(.3f));d=700+random.nextInt(500);break;case THINKING:rot=r(-.62f,.62f);x=r(-dp(1.3f),dp(1.3f));y=r(-dp(1.2f),dp(.5f));d=900+random.nextInt(800);break;case SPEAKING:rot=r(-.42f,.42f);x=r(-dp(1.0f),dp(1.0f));y=r(-dp(1.3f),dp(.2f));d=600+random.nextInt(600);break;default:rot=r(-.32f,.32f);x=r(-dp(.7f),dp(.7f));y=r(-dp(.8f),dp(.5f));d=1200+random.nextInt(900);break;}
        motionView.animate().cancel();motionView.animate().rotation(rot).translationX(x).translationY(y).setDuration(d).setInterpolator(new DecelerateInterpolator()).withEndAction(()->{if(released||userLooking||gestureRunning||motionView==null)return;motionView.animate().rotation(r(-.08f,.08f)).translationX(0).translationY(0).setDuration(650+random.nextInt(500)).setInterpolator(new AccelerateDecelerateInterpolator()).start();}).start();
    }

    private void playNaturalGaze(){if(using3D||face==null)return;float rx=state==State.THINKING?.50f:.27f,ry=state==State.THINKING?.31f:.18f;float gx=r(-rx,rx),gy=r(-ry,ry);if(random.nextInt(5)!=0){gx*=.42f;gy*=.42f;}face.setGaze(gx,gy);handler.postDelayed(()->{if(!released&&!userLooking&&face!=null)face.releaseGaze();},450+random.nextInt(750));}
    private void scheduleMicroMotion(){if(released||using3D)return;long delay;switch(state){case SPEAKING:delay=1300+random.nextInt(1700);break;case LISTENING:delay=1800+random.nextInt(2400);break;case THINKING:delay=2200+random.nextInt(2900);break;default:delay=3000+random.nextInt(3900);break;}handler.removeCallbacks(microMotionTask);handler.postDelayed(microMotionTask,delay);}
    private void scheduleGaze(){if(released||using3D)return;long delay=state==State.THINKING?1700+random.nextInt(2400):2700+random.nextInt(4000);handler.removeCallbacks(gazeTask);handler.postDelayed(gazeTask,delay);}
    private void scheduleGesture(){if(released||using3D)return;long delay;switch(state){case SPEAKING:delay=3200+random.nextInt(4200);break;case LISTENING:delay=4200+random.nextInt(5000);break;case THINKING:delay=5200+random.nextInt(5600);break;default:delay=7000+random.nextInt(9000);break;}handler.removeCallbacks(gestureTask);handler.postDelayed(gestureTask,delay);}

    private void syncFaceState(State next){if(using3D)return;if(face!=null){switch(next){case LISTENING:face.setActivity(CelineFaceOverlayView.Activity.LISTENING);break;case THINKING:face.setActivity(CelineFaceOverlayView.Activity.THINKING);break;case SPEAKING:face.setActivity(CelineFaceOverlayView.Activity.SPEAKING);break;default:face.setActivity(CelineFaceOverlayView.Activity.IDLE);break;}}scheduleMicroMotion();scheduleGaze();}
    private void startBreath(float from,float to,long duration){if(using3D||motionView==null)return;breath=ObjectAnimator.ofFloat(motionView,"scaleY",from,to,from);breath.setDuration(duration);breath.setRepeatCount(ObjectAnimator.INFINITE);breath.setInterpolator(new AccelerateDecelerateInterpolator());breath.addUpdateListener(a->{if(gestureRunning)return;float sy=(Float)a.getAnimatedValue();motionView.setScaleY(sy);motionView.setScaleX(1f+(sy-1f)*.45f);});breath.start();}
    private void startLift(float from,float to,long duration){if(using3D||avatar==null)return;lift=ObjectAnimator.ofFloat(avatar,"translationY",from,to,from);lift.setDuration(duration);lift.setRepeatCount(ObjectAnimator.INFINITE);lift.setInterpolator(new AccelerateDecelerateInterpolator());lift.start();}
    private void stopLoopsOnly(){try{if(breath!=null)breath.cancel();}catch(Exception ignored){}try{if(lift!=null)lift.cancel();}catch(Exception ignored){}breath=null;lift=null;speechEnergy=0;gestureRunning=false;if(motionView!=null&&!using3D){motionView.animate().cancel();motionView.setScaleX(1);motionView.setScaleY(1);motionView.setRotation(0);motionView.setTranslationX(0);motionView.setTranslationY(0);}if(avatar!=null&&!using3D){avatar.setTranslationX(0);avatar.setTranslationY(0);}}
    private void stopMotion(){stopLoopsOnly();if(motionView!=null&&!using3D){motionView.animate().cancel();motionView.setAlpha(1);motionView.setRotation(0);motionView.setTranslationX(0);motionView.setTranslationY(0);}}
    private float dp(float v){return v*density;}
    private float r(float min,float max){return min+random.nextFloat()*(max-min);}
    private static float clamp(float v,float min,float max){return Math.max(min,Math.min(max,v));}
}
