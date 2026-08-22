package de.yahya.ai;

import android.animation.ObjectAnimator;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;

import java.util.Random;

/** Drives Celin like a live video-call portrait instead of a periodically moving still. */
public final class CelineAvatarController implements SpeechAudioBus.Listener {
    public enum State { IDLE, LISTENING, THINKING, SPEAKING }

    private final View motionView;
    private final ImageView avatar;
    private final CelineFaceOverlayView face;
    private final float density;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Random random=new Random();

    private ObjectAnimator breath,lift;
    private State state=State.IDLE;
    private boolean released,userLooking;
    private float speechEnergy;
    private CelineLivePortrait.Pose pose=CelineLivePortrait.Pose.NEUTRAL;

    private final Runnable microMotionTask=new Runnable(){
        @Override public void run(){if(released||motionView==null)return;if(!userLooking)playMicroMotion();scheduleMicroMotion();}
    };
    private final Runnable gazeTask=new Runnable(){
        @Override public void run(){if(released||face==null)return;if(!userLooking)playNaturalGaze();scheduleGaze();}
    };
    private final Runnable settleSmileTask=new Runnable(){
        @Override public void run(){if(!released&&state==State.IDLE)applyPose(CelineLivePortrait.Pose.NEUTRAL);}
    };

    public CelineAvatarController(View motionView,ImageView avatar,CelineFaceOverlayView face,float density){
        this.motionView=motionView;this.avatar=avatar;this.face=face;this.density=density;
        applyPose(CelineLivePortrait.Pose.NEUTRAL);
        if(face!=null)face.start();
        if(motionView!=null)motionView.post(()->{motionView.setPivotX(motionView.getWidth()*.50f);motionView.setPivotY(motionView.getHeight()*.78f);});
        SpeechAudioBus.setListener(this);scheduleMicroMotion();scheduleGaze();
    }

    public State getState(){return state;}

    public void setState(State next){
        if(next==null)next=State.IDLE;
        State previous=state;
        state=next;
        stopLoopsOnly();
        handler.removeCallbacks(settleSmileTask);

        switch(next){
            case LISTENING: applyPose(CelineLivePortrait.Pose.LISTENING); break;
            case THINKING: applyPose(CelineLivePortrait.Pose.LISTENING); break;
            case SPEAKING: applyPose(CelineLivePortrait.Pose.SPEAKING); break;
            case IDLE:
            default:
                if(previous==State.SPEAKING){
                    applyPose(CelineLivePortrait.Pose.SMILE);
                    handler.postDelayed(settleSmileTask,1200L);
                }else applyPose(CelineLivePortrait.Pose.NEUTRAL);
                break;
        }
        syncFaceState(next);

        switch(next){
            case LISTENING:startBreath(.998f,1.010f,3200);startLift(dp(.2f),-dp(1.4f),3600);break;
            case THINKING:startBreath(.998f,1.009f,4000);startLift(dp(.2f),-dp(1.1f),4400);break;
            case SPEAKING:startBreath(.998f,1.013f,2600);startLift(dp(.2f),-dp(1.5f),3100);break;
            default:startBreath(.999f,1.008f,4800);startLift(dp(.1f),-dp(1.0f),5400);break;
        }
    }

    @Override public void onSpeechAudioLevel(float level){
        float c=clamp(level,0f,1f);speechEnergy=speechEnergy*.70f+c*.30f;
        if(face!=null)face.setMouthLevel(state==State.SPEAKING?c:0f);
        if(avatar!=null&&state==State.SPEAKING&&!userLooking){
            avatar.setTranslationX((speechEnergy-.35f)*dp(.30f));
            avatar.setTranslationY(-speechEnergy*dp(.24f));
        }
    }

    @Override public void onSpeechViseme(SpeechVisemeAnalyzer.Cue cue){if(face!=null)face.setViseme(state==State.SPEAKING?cue:SpeechVisemeAnalyzer.silent());}

    public void lookToward(float nx,float ny){
        userLooking=true;if(motionView==null)return;float x=clamp(nx,-.5f,.5f),y=clamp(ny,-.5f,.5f);
        motionView.animate().cancel();motionView.setTranslationX(x*dp(4.5f));motionView.setTranslationY(y*dp(2.4f));motionView.setRotation(x*.55f);
        if(face!=null)face.setGaze(x*2f,y*2f);
    }

    public void releaseLook(){
        userLooking=false;if(motionView!=null)motionView.animate().translationX(0).translationY(0).rotation(0).setDuration(500).setInterpolator(new AccelerateDecelerateInterpolator()).start();
        if(face!=null)face.releaseGaze();
    }

    public void blink(){if(face!=null)face.blinkNow(false);}

    public void release(){released=true;handler.removeCallbacksAndMessages(null);SpeechAudioBus.clearListener(this);stopMotion();if(face!=null)face.stop();}

    private void applyPose(CelineLivePortrait.Pose next){
        if(avatar==null)return;
        if(next==null)next=CelineLivePortrait.Pose.NEUTRAL;
        Bitmap bitmap=CelineLivePortrait.load(avatar.getContext(),next);
        if(bitmap==null&&next!=CelineLivePortrait.Pose.NEUTRAL)bitmap=CelineLivePortrait.load(avatar.getContext(),CelineLivePortrait.Pose.NEUTRAL);
        if(bitmap!=null&&!bitmap.isRecycled()){
            pose=next;
            avatar.animate().cancel();
            avatar.setAlpha(.94f);
            avatar.setImageBitmap(bitmap);
            avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            avatar.animate().alpha(1f).setDuration(180L).start();
            if(face!=null)face.setPose(next);
        }else avatar.setImageResource(de.yahya.ai.R.drawable.celine_avatar);
    }

    private void playMicroMotion(){
        if(motionView==null)return;float rot,x,y;long d;
        switch(state){
            case LISTENING:rot=r(-.28f,.28f);x=r(-dp(.8f),dp(.8f));y=r(-dp(1.8f),-dp(.3f));d=700+random.nextInt(500);break;
            case THINKING:rot=r(-.62f,.62f);x=r(-dp(1.3f),dp(1.3f));y=r(-dp(1.2f),dp(.5f));d=900+random.nextInt(800);break;
            case SPEAKING:rot=r(-.42f,.42f);x=r(-dp(1.0f),dp(1.0f));y=r(-dp(1.3f),dp(.2f));d=600+random.nextInt(600);break;
            default:rot=r(-.32f,.32f);x=r(-dp(.7f),dp(.7f));y=r(-dp(.8f),dp(.5f));d=1200+random.nextInt(900);break;
        }
        motionView.animate().cancel();motionView.animate().rotation(rot).translationX(x).translationY(y).setDuration(d).setInterpolator(new DecelerateInterpolator()).withEndAction(()->{
            if(released||userLooking||motionView==null)return;motionView.animate().rotation(r(-.08f,.08f)).translationX(0).translationY(0).setDuration(650+random.nextInt(500)).setInterpolator(new AccelerateDecelerateInterpolator()).start();
        }).start();
        if(state==State.LISTENING&&random.nextInt(3)==0&&face!=null)face.blinkNow(false);
    }

    private void playNaturalGaze(){
        if(face==null)return;float rx=state==State.THINKING?.50f:.27f,ry=state==State.THINKING?.31f:.18f;float gx=r(-rx,rx),gy=r(-ry,ry);if(random.nextInt(5)!=0){gx*=.42f;gy*=.42f;}face.setGaze(gx,gy);
        handler.postDelayed(()->{if(!released&&!userLooking&&face!=null)face.releaseGaze();},450+random.nextInt(750));
    }

    private void scheduleMicroMotion(){
        if(released)return;long delay;switch(state){case SPEAKING:delay=1300+random.nextInt(1700);break;case LISTENING:delay=1800+random.nextInt(2400);break;case THINKING:delay=2200+random.nextInt(2900);break;default:delay=3000+random.nextInt(3900);break;}handler.removeCallbacks(microMotionTask);handler.postDelayed(microMotionTask,delay);
    }
    private void scheduleGaze(){if(released)return;long delay=state==State.THINKING?1700+random.nextInt(2400):2700+random.nextInt(4000);handler.removeCallbacks(gazeTask);handler.postDelayed(gazeTask,delay);}

    private void syncFaceState(State next){
        if(face!=null){switch(next){case LISTENING:face.setActivity(CelineFaceOverlayView.Activity.LISTENING);break;case THINKING:face.setActivity(CelineFaceOverlayView.Activity.THINKING);break;case SPEAKING:face.setActivity(CelineFaceOverlayView.Activity.SPEAKING);break;default:face.setActivity(CelineFaceOverlayView.Activity.IDLE);break;}}
        scheduleMicroMotion();scheduleGaze();
    }

    private void startBreath(float from,float to,long duration){
        if(motionView==null)return;breath=ObjectAnimator.ofFloat(motionView,"scaleY",from,to,from);breath.setDuration(duration);breath.setRepeatCount(ObjectAnimator.INFINITE);breath.setInterpolator(new AccelerateDecelerateInterpolator());breath.addUpdateListener(a->{float sy=(Float)a.getAnimatedValue();motionView.setScaleY(sy);motionView.setScaleX(1f+(sy-1f)*.45f);});breath.start();
    }
    private void startLift(float from,float to,long duration){if(avatar==null)return;lift=ObjectAnimator.ofFloat(avatar,"translationY",from,to,from);lift.setDuration(duration);lift.setRepeatCount(ObjectAnimator.INFINITE);lift.setInterpolator(new AccelerateDecelerateInterpolator());lift.start();}

    private void stopLoopsOnly(){
        try{if(breath!=null)breath.cancel();}catch(Exception ignored){}try{if(lift!=null)lift.cancel();}catch(Exception ignored){}breath=null;lift=null;speechEnergy=0;
        if(motionView!=null){motionView.setScaleX(1);motionView.setScaleY(1);}if(avatar!=null){avatar.setTranslationX(0);avatar.setTranslationY(0);}
    }
    private void stopMotion(){stopLoopsOnly();if(motionView!=null){motionView.animate().cancel();motionView.setAlpha(1);motionView.setRotation(0);motionView.setTranslationX(0);motionView.setTranslationY(0);}}

    private float dp(float v){return v*density;}private float r(float min,float max){return min+random.nextFloat()*(max-min);}private static float clamp(float v,float min,float max){return Math.max(min,Math.min(max,v));}
}
