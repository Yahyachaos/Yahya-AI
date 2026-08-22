from pathlib import Path
import hashlib

MAIN=Path('app/src/main/java/de/yahya/ai/MainActivity.java')
VERIFY=Path('VERIFY_PROJECT.sh')
CHECK=Path('PROJECT_CHECKSUMS.txt')
WORKFLOW=Path('.github/workflows/phase4-avatar-foundation.yml')
SELF=Path('tools/phase4_avatar_foundation.py')

text=MAIN.read_text(encoding='utf-8')
text=text.replace('import android.animation.ObjectAnimator;\nimport android.animation.Animator;\n','')
old='private TextToSpeech tts; private boolean ttsReady=false; private int ttsInitTries=0; private MediaPlayer neuralPlayer; private LocalNeuralTtsEngine localNeuralTts; private SupertonicModelManager supertonicModels; private ObjectAnimator avatarAnimator; private ObjectAnimator avatarSway; private ObjectAnimator avatarLift;'
new='private TextToSpeech tts; private boolean ttsReady=false; private int ttsInitTries=0; private MediaPlayer neuralPlayer; private LocalNeuralTtsEngine localNeuralTts; private SupertonicModelManager supertonicModels; private CelineAvatarController avatarController;'
if text.count(old)!=1: raise SystemExit('field anchor mismatch')
text=text.replace(old,new,1)
old='avatar.setClipToOutline(true);avatar.setOutlineProvider(ViewOutlineProvider.BACKGROUND);'
new='avatar.setClipToOutline(true);avatar.setOutlineProvider(ViewOutlineProvider.BACKGROUND);avatarController=new CelineAvatarController(avatar,getResources().getDisplayMetrics().density);'
if text.count(old)!=1: raise SystemExit('avatar init anchor mismatch')
text=text.replace(old,new,1)
old='''        avatar.setOnTouchListener((v,e)->{\n            if(e.getAction()==MotionEvent.ACTION_MOVE){float nx=(e.getX()/Math.max(1f,v.getWidth())-.5f);float ny=(e.getY()/Math.max(1f,v.getHeight())-.5f);v.setTranslationX(nx*dp(5));v.setTranslationY(ny*dp(3));v.setRotation(nx*0.8f);}\n            else if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){v.animate().translationX(0).translationY(0).rotation(0).setDuration(260).start();}\n            return false;\n        });\n'''
new='''        avatar.setOnTouchListener((v,e)->{\n            if(avatarController==null)return false;\n            if(e.getAction()==MotionEvent.ACTION_MOVE){float nx=(e.getX()/Math.max(1f,v.getWidth())-.5f);float ny=(e.getY()/Math.max(1f,v.getHeight())-.5f);avatarController.lookToward(nx,ny);}\n            else if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL)avatarController.releaseLook();\n            return false;\n        });\n'''
if text.count(old)!=1: raise SystemExit('touch anchor mismatch')
text=text.replace(old,new,1)
start=text.index('    private void stopAvatarAnimation(){')
end=text.index('    private Button button(', start)
replacement='''    private void avatarIdle(){if(avatarController!=null)avatarController.setState(CelineAvatarController.State.IDLE);}\n    private void avatarListening(){if(avatarController!=null)avatarController.setState(CelineAvatarController.State.LISTENING);}\n    private void avatarThinking(){if(avatarController!=null)avatarController.setState(CelineAvatarController.State.THINKING);}\n    private void avatarSpeaking(){if(avatarController!=null)avatarController.setState(CelineAvatarController.State.SPEAKING);}\n\n'''
text=text[:start]+replacement+text[end:]
old='@Override protected void onDestroy(){stopAvatarAnimation();'
new='@Override protected void onDestroy(){if(avatarController!=null)avatarController.release();'
if text.count(old)!=1: raise SystemExit('destroy anchor mismatch')
text=text.replace(old,new,1)
MAIN.write_text(text,encoding='utf-8')

v=VERIFY.read_text(encoding='utf-8')
anchor='[ -f app/src/main/java/de/yahya/ai/SupertonicModelManager.java ] || { echo "FEHLT: SupertonicModelManager.java"; exit 1; }\n'
extra=anchor+'[ -f app/src/main/java/de/yahya/ai/CelineAvatarController.java ] || { echo "FEHLT: CelineAvatarController.java"; exit 1; }\n'
if 'CelineAvatarController.java' not in v:
    if anchor not in v: raise SystemExit('verify anchor mismatch')
    v=v.replace(anchor,extra,1)
VERIFY.write_text(v,encoding='utf-8')

if WORKFLOW.exists(): WORKFLOW.unlink()
if SELF.exists(): SELF.unlink()
try: SELF.parent.rmdir()
except OSError: pass

entries=[]
for line in CHECK.read_text(encoding='utf-8').splitlines():
    if not line.strip(): continue
    _,path=line.split('  ',1)
    if Path(path).exists(): entries.append(path)
new_path='app/src/main/java/de/yahya/ai/CelineAvatarController.java'
if new_path not in entries: entries.append(new_path)
CHECK.write_text(''.join(f'{hashlib.sha256(Path(p).read_bytes()).hexdigest()}  {p}\n' for p in entries),encoding='utf-8')
print('Avatar controller foundation applied.')
