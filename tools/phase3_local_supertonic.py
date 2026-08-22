from pathlib import Path
import hashlib

MAIN=Path('app/src/main/java/de/yahya/ai/MainActivity.java')
VERIFY=Path('VERIFY_PROJECT.sh')
CHECK=Path('PROJECT_CHECKSUMS.txt')
WORKFLOW=Path('.github/workflows/phase3-local-supertonic.yml')
SELF=Path('tools/phase3_local_supertonic.py')

text=MAIN.read_text(encoding='utf-8')
old='private TextToSpeech tts; private boolean ttsReady=false; private int ttsInitTries=0; private MediaPlayer neuralPlayer;'
new='private TextToSpeech tts; private boolean ttsReady=false; private int ttsInitTries=0; private MediaPlayer neuralPlayer; private LocalNeuralTtsEngine localNeuralTts;'
if text.count(old)!=1: raise SystemExit('TTS field anchor mismatch')
text=text.replace(old,new,1)
old='prefs=getSharedPreferences("yahya_ai",MODE_PRIVATE);device=new DeviceBridge(this);setContentView(buildUi());initTts();'
new='prefs=getSharedPreferences("yahya_ai",MODE_PRIVATE);device=new DeviceBridge(this);localNeuralTts=new LocalNeuralTtsEngine(this);setContentView(buildUi());initTts();'
if text.count(old)!=1: raise SystemExit('onCreate anchor mismatch')
text=text.replace(old,new,1)
old='''    private void speak(String s){\n        String clean=SpeechTextNormalizer.clean(s);if(clean.isEmpty())return;\n        String key=prefs.getString("api_key","").trim();boolean neural=prefs.getBoolean("neural_voice",true);\n        SpeechOutputRouter.Engine engine=SpeechOutputRouter.select(neural,key);\n        if(engine==SpeechOutputRouter.Engine.ONLINE_NEURAL){speakNeural(clean,key);return;}\n        speakAndroid(clean);\n    }\n'''
new='''    private void speak(String s){\n        String clean=SpeechTextNormalizer.clean(s);if(clean.isEmpty())return;\n        if(localNeuralTts!=null&&localNeuralTts.isModelInstalled()){speakLocalNeural(clean);return;}\n        speakExistingFallback(clean);\n    }\n\n    private void speakExistingFallback(String clean){\n        String key=prefs.getString("api_key","").trim();boolean neural=prefs.getBoolean("neural_voice",true);\n        SpeechOutputRouter.Engine engine=SpeechOutputRouter.select(neural,key);\n        if(engine==SpeechOutputRouter.Engine.ONLINE_NEURAL){speakNeural(clean,key);return;}\n        speakAndroid(clean);\n    }\n\n    private void speakLocalNeural(String clean){\n        localNeuralTts.speak(clean,new LocalNeuralTtsEngine.Listener(){\n            @Override public void onPreparing(){runOnUiThread(()->{status.setText("Celin bereitet ihre lokale Stimme vor …");avatarThinking();});}\n            @Override public void onSpeaking(){runOnUiThread(()->{status.setText("Celin spricht …");avatarSpeaking();});}\n            @Override public void onDone(){runOnUiThread(()->{status.setText("Bereit");avatarIdle();});}\n            @Override public void onError(Throwable error){runOnUiThread(()->{Toast.makeText(MainActivity.this,"Lokale Neural-Stimme noch nicht bereit – Fallback aktiv.",Toast.LENGTH_SHORT).show();speakExistingFallback(clean);});}\n        });\n    }\n'''
if text.count(old)!=1: raise SystemExit('speak method anchor mismatch')
text=text.replace(old,new,1)
old='if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}'
new='if(tts!=null){tts.stop();tts.shutdown();}if(localNeuralTts!=null)localNeuralTts.release();super.onDestroy();}'
if text.count(old)!=1: raise SystemExit('onDestroy anchor mismatch')
text=text.replace(old,new,1)
MAIN.write_text(text,encoding='utf-8')

v=VERIFY.read_text(encoding='utf-8')
anchor='[ -f app/src/main/java/de/yahya/ai/SpeechOutputRouter.java ] || { echo "FEHLT: SpeechOutputRouter.java"; exit 1; }\n'
extra=anchor+'[ -f app/src/main/java/de/yahya/ai/LocalNeuralTtsEngine.java ] || { echo "FEHLT: LocalNeuralTtsEngine.java"; exit 1; }\n'
if 'LocalNeuralTtsEngine.java' not in v:
    if anchor not in v: raise SystemExit('verify anchor mismatch')
    v=v.replace(anchor,extra,1)
VERIFY.write_text(v,encoding='utf-8')

if WORKFLOW.exists(): WORKFLOW.unlink()
if SELF.exists(): SELF.unlink()
try: SELF.parent.rmdir()
except OSError: pass

lines=[]
seen=set()
for line in CHECK.read_text(encoding='utf-8').splitlines():
    if not line.strip(): continue
    _,path=line.split('  ',1)
    p=Path(path)
    if p.exists():
        lines.append(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {path}')
        seen.add(path)
new_path='app/src/main/java/de/yahya/ai/LocalNeuralTtsEngine.java'
if new_path not in seen:
    p=Path(new_path); lines.append(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {new_path}')
CHECK.write_text('\n'.join(lines)+'\n',encoding='utf-8')
print('Local Supertonic engine wired with truthful fallback.')
