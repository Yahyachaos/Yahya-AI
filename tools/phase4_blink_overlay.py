from pathlib import Path
import hashlib

MAIN=Path('app/src/main/java/de/yahya/ai/MainActivity.java')
VERIFY=Path('VERIFY_PROJECT.sh')
CHECK=Path('PROJECT_CHECKSUMS.txt')
WORKFLOW=Path('.github/workflows/phase4-blink-overlay.yml')
SELF=Path('tools/phase4_blink_overlay.py')

text=MAIN.read_text(encoding='utf-8')
old='''        avatar=new ImageView(this);avatar.setImageResource(de.yahya.ai.R.drawable.celine_avatar);avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);avatar.setBackground(round(Color.rgb(42,37,55),24));avatar.setClipToOutline(true);avatar.setOutlineProvider(ViewOutlineProvider.BACKGROUND);avatarController=new CelineAvatarController(avatar,getResources().getDisplayMetrics().density);\n        LinearLayout.LayoutParams avp=new LinearLayout.LayoutParams(-1,dp(315));profile.addView(avatar,avp);\n'''
new='''        FrameLayout avatarStage=new FrameLayout(this);avatarStage.setBackground(round(Color.rgb(42,37,55),24));avatarStage.setClipToOutline(true);avatarStage.setOutlineProvider(ViewOutlineProvider.BACKGROUND);\n        avatar=new ImageView(this);avatar.setImageResource(de.yahya.ai.R.drawable.celine_avatar);avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);avatarStage.addView(avatar,new FrameLayout.LayoutParams(-1,-1));\n        CelineFaceOverlayView faceOverlay=new CelineFaceOverlayView(this);faceOverlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);avatarStage.addView(faceOverlay,new FrameLayout.LayoutParams(-1,-1));\n        avatarController=new CelineAvatarController(avatarStage,avatar,faceOverlay,getResources().getDisplayMetrics().density);\n        LinearLayout.LayoutParams avp=new LinearLayout.LayoutParams(-1,dp(315));profile.addView(avatarStage,avp);\n'''
if text.count(old)!=1: raise SystemExit('avatar stage anchor mismatch')
text=text.replace(old,new,1)
MAIN.write_text(text,encoding='utf-8')

v=VERIFY.read_text(encoding='utf-8')
anchor='[ -f app/src/main/java/de/yahya/ai/CelineAvatarController.java ] || { echo "FEHLT: CelineAvatarController.java"; exit 1; }\n'
if anchor not in v:
    # Older verify may not yet have controller check; insert after Supertonic manager.
    base='[ -f app/src/main/java/de/yahya/ai/SupertonicModelManager.java ] || { echo "FEHLT: SupertonicModelManager.java"; exit 1; }\n'
    if base not in v: raise SystemExit('verify insertion anchor mismatch')
    v=v.replace(base,base+anchor,1)
face='[ -f app/src/main/java/de/yahya/ai/CelineFaceOverlayView.java ] || { echo "FEHLT: CelineFaceOverlayView.java"; exit 1; }\n'
if 'CelineFaceOverlayView.java' not in v:
    v=v.replace(anchor,anchor+face,1)
VERIFY.write_text(v,encoding='utf-8')

# Remove one-shot workflow and script before checksums are regenerated.
if WORKFLOW.exists(): WORKFLOW.unlink()
if SELF.exists(): SELF.unlink()
try: SELF.parent.rmdir()
except OSError: pass

entries=[]
for line in CHECK.read_text(encoding='utf-8').splitlines():
    if not line.strip(): continue
    _,path=line.split('  ',1)
    if Path(path).exists(): entries.append(path)
for path in [
    'app/src/main/java/de/yahya/ai/CelineAvatarController.java',
    'app/src/main/java/de/yahya/ai/CelineFaceOverlayView.java']:
    if path not in entries: entries.append(path)
CHECK.write_text(''.join(f'{hashlib.sha256(Path(p).read_bytes()).hexdigest()}  {p}\n' for p in entries),encoding='utf-8')
print('Celine blink overlay wired into avatar stage.')
