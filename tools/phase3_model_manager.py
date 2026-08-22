from pathlib import Path
import hashlib

MAIN=Path('app/src/main/java/de/yahya/ai/MainActivity.java')
VERIFY=Path('VERIFY_PROJECT.sh')
CHECK=Path('PROJECT_CHECKSUMS.txt')
WORKFLOW=Path('.github/workflows/phase3-model-manager.yml')
SELF=Path('tools/phase3_model_manager.py')

text=MAIN.read_text(encoding='utf-8')
old='private TextToSpeech tts; private boolean ttsReady=false; private int ttsInitTries=0; private MediaPlayer neuralPlayer; private LocalNeuralTtsEngine localNeuralTts;'
new='private TextToSpeech tts; private boolean ttsReady=false; private int ttsInitTries=0; private MediaPlayer neuralPlayer; private LocalNeuralTtsEngine localNeuralTts; private SupertonicModelManager supertonicModels;'
if text.count(old)!=1: raise SystemExit('field anchor mismatch')
text=text.replace(old,new,1)
old='prefs=getSharedPreferences("yahya_ai",MODE_PRIVATE);device=new DeviceBridge(this);localNeuralTts=new LocalNeuralTtsEngine(this);setContentView(buildUi());'
new='prefs=getSharedPreferences("yahya_ai",MODE_PRIVATE);device=new DeviceBridge(this);localNeuralTts=new LocalNeuralTtsEngine(this);supertonicModels=new SupertonicModelManager(this);setContentView(buildUi());'
if text.count(old)!=1: raise SystemExit('onCreate anchor mismatch')
text=text.replace(old,new,1)
old='''        String voiceMode=prefs.getBoolean("neural_voice",true)?"Celin Online-Stimme":"Celin Offline (Gerät)"; String voiceName=prefs.getString("openai_voice","marin");\n        String[] items={"Celin-Aktivierungswort: "+wake,"KI/API: "+cloud,"Gedächtnis","Berechtigungen & Gerätezugriff","Gerätestatus","Sprachmodus: "+voiceMode,"Online-Stimme: "+voiceName,"Stimme testen","Avatar ansehen","Datenschutz: Gedächtnis löschen"};\n        new AlertDialog.Builder(this).setTitle("Yahya AI · Einstellungen").setItems(items,(d,w)->{switch(w){case 0:toggleWake();break;case 1:showApiKeyDialog();break;case 2:showMemoryDialog();break;case 3:showAccess();break;case 4:addAssistant(device.status(),false);break;case 5:toggleVoiceMode();break;case 6:showOpenAiVoicePicker();break;case 7:speak("Hallo Yahya. Schön, dass du da bist. Was machen wir heute?");break;case 8:showAvatar();break;case 9:confirmDeleteMemory();break;}}).show();\n'''
new='''        String voiceMode=prefs.getBoolean("neural_voice",true)?"Celin Online-Stimme":"Celin Offline (Gerät)"; String voiceName=prefs.getString("openai_voice","marin");\n        String localVoice=(supertonicModels!=null&&supertonicModels.isInstalled())?"installiert":"nicht installiert";\n        String[] items={"Celin-Aktivierungswort: "+wake,"KI/API: "+cloud,"Gedächtnis","Berechtigungen & Gerätezugriff","Gerätestatus","Sprachmodus: "+voiceMode,"Lokale Neural-Stimme: "+localVoice,"Online-Stimme: "+voiceName,"Stimme testen","Avatar ansehen","Datenschutz: Gedächtnis löschen"};\n        new AlertDialog.Builder(this).setTitle("Yahya AI · Einstellungen").setItems(items,(d,w)->{switch(w){case 0:toggleWake();break;case 1:showApiKeyDialog();break;case 2:showMemoryDialog();break;case 3:showAccess();break;case 4:addAssistant(device.status(),false);break;case 5:toggleVoiceMode();break;case 6:showLocalVoiceSetup();break;case 7:showOpenAiVoicePicker();break;case 8:speak("Hallo Yahya. Schön, dass du da bist. Was machen wir heute?");break;case 9:showAvatar();break;case 10:confirmDeleteMemory();break;}}).show();\n'''
if text.count(old)!=1: raise SystemExit('settings anchor mismatch')
text=text.replace(old,new,1)
anchor='''    private void toggleWake(){boolean on=!prefs.getBoolean("wake",false);'''
method='''    private void showLocalVoiceSetup(){\n        if(supertonicModels==null)return;\n        if(supertonicModels.isInstalled()){\n            new AlertDialog.Builder(this).setTitle("Celines lokale Neural-Stimme").setMessage("Das Supertonic-Sprachmodell ist auf diesem Gerät installiert und wird vollständig lokal verwendet. Möchtest du die Stimme testen oder das Modell entfernen?").setPositiveButton("Stimme testen",(d,w)->speak("Hallo Yahya. Ich spreche jetzt vollständig lokal auf deinem Gerät. Wie gefällt dir meine Stimme?")).setNeutralButton("Modell entfernen",(d,w)->{if(localNeuralTts!=null)localNeuralTts.release();supertonicModels.remove();Toast.makeText(this,"Lokales Sprachmodell entfernt.",Toast.LENGTH_SHORT).show();}).setNegativeButton("Schließen",null).show();\n            return;\n        }\n        new AlertDialog.Builder(this).setTitle("Celines lokale Neural-Stimme").setMessage("Das hochwertige Supertonic-Sprachmodell wird einmal aus dem offiziellen sherpa-onnx-Release heruntergeladen. Danach läuft die Sprachausgabe lokal auf deinem Gerät. Für Download und Einrichtung sollten mindestens 320 MB frei sein.").setPositiveButton("Herunterladen",(d,w)->installLocalVoice()).setNegativeButton("Abbrechen",null).show();\n    }\n\n    private void installLocalVoice(){\n        status.setText("Lokale Stimme wird vorbereitet …");avatarThinking();\n        supertonicModels.install(new SupertonicModelManager.Listener(){\n            @Override public void onStatus(String t){runOnUiThread(()->status.setText(t));}\n            @Override public void onProgress(int p){runOnUiThread(()->status.setText("Celines Stimme: "+p+" %"));}\n            @Override public void onInstalled(){runOnUiThread(()->{status.setText("Lokale Neural-Stimme installiert");avatarIdle();Toast.makeText(MainActivity.this,"Celines lokale Neural-Stimme ist installiert. Jetzt testen wir sie.",Toast.LENGTH_LONG).show();speak("Hallo Yahya. Jetzt spreche ich vollständig lokal auf deinem Gerät. Wie gefällt dir meine Stimme?");});}\n            @Override public void onError(Throwable e){runOnUiThread(()->{status.setText("Bereit");avatarIdle();String m=e.getMessage();if(m==null||m.trim().isEmpty())m=e.getClass().getSimpleName();new AlertDialog.Builder(MainActivity.this).setTitle("Installation nicht abgeschlossen").setMessage(m).setPositiveButton("OK",null).show();});}\n        });\n    }\n\n'''
if anchor not in text: raise SystemExit('method insertion anchor mismatch')
text=text.replace(anchor,method+anchor,1)
MAIN.write_text(text,encoding='utf-8')

v=VERIFY.read_text(encoding='utf-8')
anchor='[ -f app/src/main/java/de/yahya/ai/LocalNeuralTtsEngine.java ] || { echo "FEHLT: LocalNeuralTtsEngine.java"; exit 1; }\n'
extra=anchor+'[ -f app/src/main/java/de/yahya/ai/SupertonicModelManager.java ] || { echo "FEHLT: SupertonicModelManager.java"; exit 1; }\n'
if 'SupertonicModelManager.java' not in v:
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
    p=Path(path)
    if p.exists(): entries.append(path)
new_path='app/src/main/java/de/yahya/ai/SupertonicModelManager.java'
if new_path not in entries: entries.append(new_path)
# Preserve stable order and regenerate every checksum from actual branch contents.
CHECK.write_text(''.join(f'{hashlib.sha256(Path(p).read_bytes()).hexdigest()}  {p}\n' for p in entries),encoding='utf-8')
print('Supertonic model manager UI wired.')
