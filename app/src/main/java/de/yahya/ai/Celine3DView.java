package de.yahya.ai;

import android.content.Context;
import android.opengl.Matrix;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.widget.FrameLayout;

import com.google.android.filament.Engine;
import com.google.android.filament.TransformManager;
import com.google.android.filament.android.UiHelper;
import com.google.android.filament.gltfio.Animator;
import com.google.android.filament.gltfio.FilamentAsset;
import com.google.android.filament.utils.Float3;
import com.google.android.filament.utils.ModelViewer;
import com.google.android.filament.utils.Utils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

/** Real-time renderer for Celine's rigged GLB avatar. */
public final class Celine3DView extends FrameLayout {
    private static final String MODEL_PATH = "models/celine.glb";
    static { Utils.INSTANCE.init(); }

    private final SurfaceView surface;
    private final Choreographer choreographer;
    private final ModelViewer viewer;
    private final long startedAtNanos = System.nanoTime();

    private boolean running;
    private CelineAvatarController.State state = CelineAvatarController.State.IDLE;
    private int activeAnimation = -1;
    private float speechEnergy;
    private float lookX, lookY, targetLookX, targetLookY;
    private BonePose head, neck, spine, spine01, spine02;

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override public void doFrame(long frameTimeNanos) {
            if (!running) return;
            choreographer.postFrameCallback(this);
            final float seconds = (frameTimeNanos - startedAtNanos) / 1_000_000_000f;
            Animator animator = viewer.getAnimator();
            if (animator != null && activeAnimation >= 0 && activeAnimation < animator.getAnimationCount()) {
                float duration = animator.getAnimationDuration(activeAnimation);
                float t = duration > 0.001f ? seconds % duration : seconds;
                animator.applyAnimation(activeAnimation, t);
            }
            lookX += (targetLookX - lookX) * 0.14f;
            lookY += (targetLookY - lookY) * 0.14f;
            applyProceduralPose(seconds);
            if (animator != null) animator.updateBoneMatrices();
            viewer.render(frameTimeNanos);
        }
    };

    public Celine3DView(Context context) throws Exception {
        super(context);
        setClipChildren(true); setClipToPadding(true);
        surface = new SurfaceView(context);
        addView(surface, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        choreographer = Choreographer.getInstance();
        viewer = new ModelViewer(surface, Engine.create(), new UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK), null);
        surface.setOnTouchListener((v,e)->{
            if(e.getAction()==MotionEvent.ACTION_DOWN||e.getAction()==MotionEvent.ACTION_MOVE){
                float nx=(e.getX()/Math.max(1f,v.getWidth())-.5f)*2f;
                float ny=(e.getY()/Math.max(1f,v.getHeight())-.5f)*2f;
                setLook(nx,ny);
            }else if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){releaseLook();}
            return true;
        });
        viewer.loadModelGlb(readAsset(context, MODEL_PATH));
        viewer.transformToUnitCube(new Float3(0f, 0f, -3.1f));
        captureMeshyRig(); chooseAnimation();
    }

    public static boolean hasModel(Context context) {
        try (InputStream in = context.getAssets().open(MODEL_PATH)) { return in.available() > 32; }
        catch (Exception ignored) { return false; }
    }

    public void setAvatarState(CelineAvatarController.State next) { state = next == null ? CelineAvatarController.State.IDLE : next; chooseAnimation(); }
    public void setSpeechEnergy(float level) { speechEnergy = clamp(level); }
    public void setLook(float x,float y) { targetLookX=clampSigned(x); targetLookY=clampSigned(y); }
    public void releaseLook() { targetLookX=targetLookY=0f; }
    public void setViseme(SpeechVisemeAnalyzer.Cue cue) { /* Current Meshy export has no facial morph targets. */ }

    public void startRendering() { if (!running) { running=true; choreographer.postFrameCallback(frameCallback); } }
    public void stopRendering() { running=false; choreographer.removeFrameCallback(frameCallback); }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();startRendering();}
    @Override protected void onDetachedFromWindow(){stopRendering();super.onDetachedFromWindow();}

    private void captureMeshyRig(){
        FilamentAsset asset=viewer.getAsset(); if(asset==null)return;
        head=capture(asset,"Head"); neck=capture(asset,"neck"); spine=capture(asset,"Spine"); spine01=capture(asset,"Spine01"); spine02=capture(asset,"Spine02");
    }
    private BonePose capture(FilamentAsset asset,String name){
        int entity=asset.getFirstEntityByName(name); if(entity==0)return null;
        TransformManager tm=viewer.getEngine().getTransformManager(); int instance=tm.getInstance(entity); if(instance==0)return null;
        float[] base=new float[16]; tm.getTransform(instance,base); return new BonePose(instance,base);
    }

    private void applyProceduralPose(float t){
        float breath=(float)Math.sin(t*1.65f),slow=(float)Math.sin(t*.72f+.8f),talk=state==CelineAvatarController.State.SPEAKING?speechEnergy:0f;
        float hp=0,hy=0,hr=0,cp=0,cr=0;
        switch(state){
            case LISTENING:hp=1.4f+slow;hy=(float)Math.sin(t*.45f)*1.7f;hr=(float)Math.sin(t*.31f)*.8f;cp=breath*.45f;break;
            case THINKING:hp=-1.2f+slow*1.4f;hy=3.2f+(float)Math.sin(t*.38f)*2.1f;hr=-2f+(float)Math.sin(t*.29f)*.7f;cp=breath*.35f;cr=(float)Math.sin(t*.33f)*.55f;break;
            case SPEAKING:hp=(float)Math.sin(t*2.15f)*(.8f+talk*1.9f);hy=(float)Math.sin(t*.83f)*(1.4f+talk*1.8f);hr=(float)Math.sin(t*.61f+1.1f)*.8f;cp=breath*.55f+talk*.45f;cr=(float)Math.sin(t*1.07f)*talk*.7f;break;
            default:hp=slow*.65f;hy=(float)Math.sin(t*.34f)*.9f;hr=(float)Math.sin(t*.27f+1.4f)*.45f;cp=breath*.38f;break;
        }
        hy+=lookX*12f;hp+=lookY*7f;
        applyRotation(spine,cp*.35f,0,cr*.25f);applyRotation(spine01,cp*.45f,0,cr*.45f);applyRotation(spine02,cp*.60f,0,cr*.65f);
        applyRotation(neck,hp*.30f,hy*.25f,hr*.25f);applyRotation(head,hp*.70f,hy*.75f,hr*.75f);
    }

    private void applyRotation(BonePose bone,float x,float y,float z){
        if(bone==null)return;float[] rx=new float[16],ry=new float[16],rz=new float[16],tmp=new float[16],rot=new float[16],out=new float[16];
        Matrix.setRotateM(rx,0,x,1,0,0);Matrix.setRotateM(ry,0,y,0,1,0);Matrix.setRotateM(rz,0,z,0,0,1);
        Matrix.multiplyMM(tmp,0,ry,0,rx,0);Matrix.multiplyMM(rot,0,rz,0,tmp,0);Matrix.multiplyMM(out,0,bone.base,0,rot,0);
        viewer.getEngine().getTransformManager().setTransform(bone.instance,out);
    }

    private void chooseAnimation(){
        Animator a=viewer.getAnimator();activeAnimation=-1;if(a==null||a.getAnimationCount()==0)return;
        String[] wanted;switch(state){case LISTENING:wanted=new String[]{"listen","attentive"};break;case THINKING:wanted=new String[]{"think","ponder"};break;case SPEAKING:wanted=new String[]{"talk","speak","conversation"};break;default:wanted=new String[]{"idle","breath","stand"};}
        for(String key:wanted)for(int i=0;i<a.getAnimationCount();i++){String n=a.getAnimationName(i);if(n!=null&&n.toLowerCase(Locale.ROOT).contains(key)){activeAnimation=i;return;}}
    }

    private static ByteBuffer readAsset(Context c,String path)throws Exception{
        try(InputStream in=c.getAssets().open(path);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[32768];int n;while((n=in.read(b))>=0)out.write(b,0,n);byte[] bytes=out.toByteArray();ByteBuffer d=ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.nativeOrder());d.put(bytes);d.rewind();return d;}
    }
    private static float clamp(float v){return Math.max(0f,Math.min(1f,v));}private static float clampSigned(float v){return Math.max(-1f,Math.min(1f,v));}
    private static final class BonePose{final int instance;final float[] base;BonePose(int instance,float[] base){this.instance=instance;this.base=base;}}
}
