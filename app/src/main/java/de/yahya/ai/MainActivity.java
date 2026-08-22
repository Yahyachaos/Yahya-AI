package de.yahya.ai;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.speech.tts.UtteranceProgressListener;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private LinearLayout chatBox; private ScrollView scroll; private EditText input; private TextView status; private Button mic; private ImageView avatar;
    private TextToSpeech tts; private boolean ttsReady=false; private int ttsInitTries=0; private MediaPlayer neuralPlayer; private LocalNeuralTtsEngine localNeuralTts; private SupertonicModelManager supertonicModels; private CelineAvatarController avatarController; private SharedPreferences prefs; private DeviceBridge device; private Handler handler=new Handler(Looper.getMainLooper());
    private final List<Message> messages=new ArrayList<>();
    private static final int REQ_MIC=44,REQ_SPEECH=55,REQ_PERMS=66; private static final String MODEL="gpt-5.6-luna";
    private int bg=Color.rgb(13,15,20),panel=Color.rgb(25,28,36),accent=Color.rgb(150,116,255),text=Color.rgb(242,242,246),muted=Color.rgb(160,164,176);

    @Override protected void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences("yahya_ai",MODE_PRIVATE);device=new DeviceBridge(this);localNeuralTts=new LocalNeuralTtsEngine(this);supertonicModels=new SupertonicModelManager(this);setContentView(buildUi());initTts();addAssistant("Hallo Yahya. Schön, dass du da bist. Was machen wir?",false);if(prefs.getBoolean("wake",false)){Intent ws=new Intent(this,WakeWordService.class);if(Build.VERSION.SDK_INT>=26)startForegroundService(ws);else startService(ws);}handleWakeIntent(getIntent());}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);handleWakeIntent(i);}
    private void handleWakeIntent(Intent i){if(i==null||!i.getBooleanExtra("wake_celin",false))return;String c=i.getStringExtra("wake_command");handler.postDelayed(()->{addAssistant("Ja?",false);speak("Ja?");if(c!=null&&!c.trim().isEmpty())submit(c.trim());else handler.postDelayed(this::startVoiceInput,550);},350);}

    private View buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(12),dp(16),dp(14));root.setBackgroundColor(bg);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);TextView title=new TextView(this);title.setText("Yahya AI");title.setTextSize(25);title.setTextColor(text);title.setTypeface(null,1);head.addView(title,new LinearLayout.LayoutParams(0,-2,1));Button gear=button("⚙",panel);gear.setOnClickListener(v->showSettings());head.addView(gear,new LinearLayout.LayoutParams(dp(56),dp(48)));root.addView(head);
        TextView sub=new TextView(this);sub.setText("CELIN");sub.setTextSize(12);sub.setTextColor(accent);sub.setLetterSpacing(.22f);root.addView(sub);
        status=new TextView(this);status.setText("Bereit");status.setTextColor(muted);status.setTextSize(13);status.setPadding(0,dp(2),0,dp(8));root.addView(status);
        LinearLayout profile=new LinearLayout(this);profile.setOrientation(LinearLayout.VERTICAL);profile.setGravity(Gravity.CENTER_HORIZONTAL);profile.setPadding(dp(8),dp(8),dp(8),dp(10));profile.setBackground(round(panel,26));
        avatar=new ImageView(this);avatar.setImageResource(de.yahya.ai.R.drawable.celine_avatar);avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);avatar.setBackground(round(Color.rgb(42,37,55),24));avatar.setClipToOutline(true);avatar.setOutlineProvider(ViewOutlineProvider.BACKGROUND);avatarController=new CelineAvatarController(avatar,getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams avp=new LinearLayout.LayoutParams(-1,dp(315));profile.addView(avatar,avp);
        TextView n=new TextView(this);n.setText("Celin");n.setTextColor(text);n.setTextSize(21);n.setTypeface(null,1);n.setPadding(dp(6),dp(9),dp(6),0);profile.addView(n);
        TextView vibe=new TextView(this);vibe.setText("Live mit Celin · tippe mich an oder sprich mit mir");vibe.setTextColor(muted);vibe.setTextSize(13);profile.addView(vibe);root.addView(profile);
        LinearLayout.LayoutParams plp=(LinearLayout.LayoutParams)profile.getLayoutParams();plp.bottomMargin=dp(8);profile.setLayoutParams(plp);
        avatar.setOnClickListener(v->{ if(status.getText().toString().contains("hört")) return; addAssistant("Ja, ich bin da.",false); speak("Ja, ich bin da."); });
        avatar.setOnTouchListener((v,e)->{
            if(avatarController==null)return false;
            if(e.getAction()==MotionEvent.ACTION_MOVE){float nx=(e.getX()/Math.max(1f,v.getWidth())-.5f);float ny=(e.getY()/Math.max(1f,v.getHeight())-.5f);avatarController.lookToward(nx,ny);}
            else if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL)avatarController.releaseLook();
            return false;
        });
        avatarIdle();
        chatBox=new LinearLayout(this);chatBox.setOrientation(LinearLayout.VERTICAL);scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(chatBox);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(8),0,0);input=new EditText(this);input.setHint("Nachricht an Celin …");input.setHintTextColor(muted);input.setTextColor(text);input.setTextSize(16);input.setSingleLine(false);input.setMaxLines(4);input.setBackground(round(panel,28));input.setPadding(dp(16),dp(12),dp(16),dp(12));row.addView(input,new LinearLayout.LayoutParams(0,-2,1));Button send=button("➤",accent);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(54),dp(54));sp.leftMargin=dp(8);row.addView(send,sp);send.setOnClickListener(v->{String t=input.getText().toString().trim();if(!t.isEmpty()){input.setText("");submit(t);}});root.addView(row);
        mic=button("🎙  Mit Celin sprechen",accent);mic.setTextSize(17);mic.setOnClickListener(v->startVoiceInput());LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,dp(58));mp.topMargin=dp(9);root.addView(mic,mp);return root;
    }
    private void avatarIdle(){if(avatarController!=null)avatarController.setState(CelineAvatarController.State.IDLE);}
    private void avatarListening(){if(avatarController!=null)avatarController.setState(CelineAvatarController.State.LISTENING);}
    private void avatarThinking(){if(avatarController!=null)avatarController.setState(CelineAvatarController.State.THINKING);}
    private void avatarSpeaking(){if(avatarController!=null)avatarController.setState(CelineAvatarController.State.SPEAKING);}

    private Button button(String s,int c){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setAllCaps(false);b.setBackground(round(c,24));return b;}
    private GradientDrawable round(int c,int r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;}

    private void showSettings(){
        String wake=prefs.getBoolean("wake",false)?"an":"aus";String cloud=prefs.getString("api_key","").trim().isEmpty()?"nicht verbunden":"verbunden";
        String voiceMode=prefs.getBoolean("neural_voice",true)?"Celin Online-Stimme":"Celin Offline (Gerät)"; String voiceName=prefs.getString("openai_voice","marin");
        String localVoice=(supertonicModels!=null&&supertonicModels.isInstalled())?"installiert":"nicht installiert";
        String[] items={"Celin-Aktivierungswort: "+wake,"KI/API: "+cloud,"Gedächtnis","Berechtigungen & Gerätezugriff","Gerätestatus","Sprachmodus: "+voiceMode,"Lokale Neural-Stimme: "+localVoice,"Online-Stimme: "+voiceName,"Stimme testen","Avatar ansehen","Datenschutz: Gedächtnis löschen"};
        new AlertDialog.Builder(this).setTitle("Yahya AI · Einstellungen").setItems(items,(d,w)->{switch(w){case 0:toggleWake();break;case 1:showApiKeyDialog();break;case 2:showMemoryDialog();break;case 3:showAccess();break;case 4:addAssistant(device.status(),false);break;case 5:toggleVoiceMode();break;case 6:showLocalVoiceSetup();break;case 7:showOpenAiVoicePicker();break;case 8:speak("Hallo Yahya. Schön, dass du da bist. Was machen wir heute?");break;case 9:showAvatar();break;case 10:confirmDeleteMemory();break;}}).show();
    }
    private void showLocalVoiceSetup(){
        if(supertonicModels==null)return;
        if(supertonicModels.isInstalled()){
            new AlertDialog.Builder(this).setTitle("Celines lokale Neural-Stimme").setMessage("Das Supertonic-Sprachmodell ist auf diesem Gerät installiert und wird vollständig lokal verwendet. Möchtest du die Stimme testen oder das Modell entfernen?").setPositiveButton("Stimme testen",(d,w)->speak("Hallo Yahya. Ich spreche jetzt vollständig lokal auf deinem Gerät. Wie gefällt dir meine Stimme?")).setNeutralButton("Modell entfernen",(d,w)->{if(localNeuralTts!=null)localNeuralTts.release();supertonicModels.remove();Toast.makeText(this,"Lokales Sprachmodell entfernt.",Toast.LENGTH_SHORT).show();}).setNegativeButton("Schließen",null).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Celines lokale Neural-Stimme").setMessage("Das hochwertige Supertonic-Sprachmodell wird einmal aus dem offiziellen sherpa-onnx-Release heruntergeladen. Danach läuft die Sprachausgabe lokal auf deinem Gerät. Für Download und Einrichtung sollten mindestens 320 MB frei sein.").setPositiveButton("Herunterladen",(d,w)->installLocalVoice()).setNegativeButton("Abbrechen",null).show();
    }

    private void installLocalVoice(){
        status.setText("Lokale Stimme wird vorbereitet …");avatarThinking();
        supertonicModels.install(new SupertonicModelManager.Listener(){
            @Override public void onStatus(String t){runOnUiThread(()->status.setText(t));}
            @Override public void onProgress(int p){runOnUiThread(()->status.setText("Celines Stimme: "+p+" %"));}
            @Override public void onInstalled(){runOnUiThread(()->{status.setText("Lokale Neural-Stimme installiert");avatarIdle();Toast.makeText(MainActivity.this,"Celines lokale Neural-Stimme ist installiert. Jetzt testen wir sie.",Toast.LENGTH_LONG).show();speak("Hallo Yahya. Jetzt spreche ich vollständig lokal auf deinem Gerät. Wie gefällt dir meine Stimme?");});}
            @Override public void onError(Throwable e){runOnUiThread(()->{status.setText("Bereit");avatarIdle();String m=e.getMessage();if(m==null||m.trim().isEmpty())m=e.getClass().getSimpleName();new AlertDialog.Builder(MainActivity.this).setTitle("Installation nicht abgeschlossen").setMessage(m).setPositiveButton("OK",null).show();});}
        });
    }

    private void toggleWake(){boolean on=!prefs.getBoolean("wake",false);if(on&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_MIC);Toast.makeText(this,"Mikrofon erlauben und Aktivierungswort danach erneut einschalten.",Toast.LENGTH_LONG).show();return;}prefs.edit().putBoolean("wake",on).apply();Intent s=new Intent(this,WakeWordService.class);if(on){if(Build.VERSION.SDK_INT>=26)startForegroundService(s);else startService(s);}else stopService(s);Toast.makeText(this,on?"Celin hört jetzt auf ihren Namen.":"Aktivierungswort ausgeschaltet.",Toast.LENGTH_SHORT).show();}
    private void showAccess(){String msg="Celin kann nur Rechte nutzen, die du Android ausdrücklich gibst. Ohne Root bleiben private Daten anderer Apps und geschützter System-RAM gesperrt.\n\n"+device.status();new AlertDialog.Builder(this).setTitle("Berechtigungen & Gerätezugriff").setMessage(msg).setPositiveButton("Normale Rechte",(d,w)->requestNormalPermissions()).setNeutralButton("Spezialrechte",(d,w)->showSpecialRights()).setNegativeButton("Schließen",null).show();}
    private void requestNormalPermissions(){if(Build.VERSION.SDK_INT>=23)requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO,Manifest.permission.CAMERA,Manifest.permission.READ_CONTACTS,Manifest.permission.WRITE_CONTACTS,Manifest.permission.READ_CALENDAR,Manifest.permission.WRITE_CALENDAR,Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ_PERMS);}
    private void showSpecialRights(){String[] i={"Bedienungshilfe – Bildschirm bedienen","Benachrichtigungen lesen","Alle Dateien verwalten"};new AlertDialog.Builder(this).setTitle("Spezialrechte").setItems(i,(d,w)->{if(w==0)device.openAccessibilitySettings();else if(w==1)device.openNotificationSettings();else device.openAllFilesSettings();}).show();}

    private boolean handleLocalCommand(String raw){
        String t=raw.trim(),l=t.toLowerCase(Locale.GERMANY).replace("celin,","").replace("celin ","").trim();
        rememberExplicit(t);
        if(l.matches(".*(gerätestatus|arbeitsspeicher|ram|speicher frei|prozessor|cpu).*")){addAssistant(device.status(),true);return true;}
        if(l.contains("benachrichtig")&&(l.contains("zeig")||l.contains("lies")||l.contains("was"))){List<String> n=PrincessNotificationService.recent();StringBuilder b=new StringBuilder(n.isEmpty()?"Ich sehe gerade keine Benachrichtigungen.":"Deine aktuellen Benachrichtigungen:\n");for(String x:n)b.append("• ").append(x).append("\n");addAssistant(b.toString(),true);return true;}
        if(l.equals("home")||l.contains("startbildschirm")||l.contains("homescreen")){addAssistant(PrincessAccessibilityService.goHome()?"Erledigt.":"Die Bedienungshilfe ist noch nicht aktiv.",true);return true;}
        if(l.equals("zurück")||l.equals("zurueck")||l.contains("geh zurück")||l.contains("geh zurueck")){addAssistant(PrincessAccessibilityService.goBack()?"Erledigt.":"Die Bedienungshilfe ist noch nicht aktiv.",true);return true;}
        if(l.contains("was ist")&&l.contains("bildschirm")){addAssistant("Ich sehe: "+PrincessAccessibilityService.screenSummary(),true);return true;}
        // mehrstufig: Öffne X und tippe/klicke auf Y
        if((l.startsWith("öffne ")||l.startsWith("oeffne ")||l.startsWith("starte ")) && l.contains(" und ")){
            String after=t.substring(t.indexOf(' ')+1);String[] parts=after.split("(?i)\\s+und\\s+",2);if(parts.length==2){String app=parts[0].trim();String action=parts[1].trim();boolean ok=device.openApp(app);if(!ok){addAssistant("Ich finde "+app+" nicht.",true);return true;}status.setText("Celin führt aus …");handler.postDelayed(()->{String al=action.toLowerCase(Locale.GERMANY);boolean done=false;if(al.startsWith("tippe auf ")||al.startsWith("klicke auf ")){String q=action.substring(action.indexOf(" auf ")+4).trim();done=PrincessAccessibilityService.clickText(q);}else if(al.startsWith("schreibe ")||al.startsWith("tippe ")){String q=action.substring(action.indexOf(' ')+1).trim();done=PrincessAccessibilityService.setText(q);}final boolean f=done;runOnUiThread(()->{addAssistant(f?"Erledigt.":"Ich habe "+app+" geöffnet, konnte den nächsten Schritt aber nicht sicher finden.",true);status.setText("Bereit");});},1400);return true;}}
        if(l.startsWith("öffne ")||l.startsWith("oeffne ")||l.startsWith("starte ")){String app=t.substring(t.indexOf(' ')+1).trim();boolean ok=device.openApp(app);addAssistant(ok?"Erledigt.":"Ich finde keine passende App namens „"+app+"“.",true);return true;}
        if(l.startsWith("tippe auf ")||l.startsWith("klicke auf ")){String q=t.substring(t.toLowerCase(Locale.GERMANY).indexOf(" auf ")+4).replace("\"","").trim();addAssistant(PrincessAccessibilityService.clickText(q)?"Erledigt.":"Ich konnte „"+q+"“ auf dem aktuellen Bildschirm nicht finden.",true);return true;}
        if(l.startsWith("schreibe ")){String q=t.substring(t.indexOf(' ')+1);addAssistant(PrincessAccessibilityService.setText(q)?"Erledigt.":"Ich finde gerade kein beschreibbares Feld.",true);return true;}
        return false;
    }

    private void submit(String text){addUser(text);status.setText("Celin denkt …");avatarThinking();if(handleLocalCommand(text)){avatarIdle();return;}String key=prefs.getString("api_key","").trim();if(key.isEmpty()){addAssistant("Für freie Gespräche brauche ich momentan noch die Cloud-KI. Meine Gerätebefehle und mein lokales Gedächtnis funktionieren trotzdem.",true);status.setText("Lokal bereit");avatarIdle();return;}new Thread(()->{try{String r=callOpenAI(key,text);learnWithAI(key,text);runOnUiThread(()->{addAssistant(r,false);status.setText("Bereit");speak(r);});}catch(Exception e){runOnUiThread(()->{addAssistant("Die KI-Verbindung hat gerade nicht funktioniert: "+safeError(e),false);status.setText("Cloud-Fehler · lokal bereit");avatarIdle();});}}).start();}

    private String callOpenAI(String key,String userText)throws Exception{JSONObject body=new JSONObject();body.put("model",MODEL);body.put("store",false);String memory=prefs.getString("memory","").trim();String instructions="Du bist Celin, die persönliche Assistentin innerhalb der Android-App Yahya AI. Deine feste Standardsprache ist Deutsch (de-DE). Antworte immer auf Deutsch, außer Yahya bittet dich in seiner aktuellen Nachricht ausdrücklich um eine andere Sprache oder um eine Übersetzung. Wechsle niemals selbstständig aufgrund einzelner fremdsprachiger Wörter die Sprache. Sprich mit Yahya wie in einem natürlichen Gespräch: warm, aufmerksam, intelligent, direkt, lebendig und situationsabhängig. Antworte bevorzugt in flüssigen kurzen Absätzen statt Listen. Verwende im normalen Gespräch kein Markdown, keine Emojis, keine ausgeschriebenen Emoji-Namen und keine künstlichen Regieanweisungen, weil deine Antworten vorgelesen werden. Wiederhole die Frage nicht unnötig. Vermeide steife KI-Floskeln wie 'als KI' oder 'soweit es einer KI gutgehen kann', außer wenn eine sachliche Klarstellung wirklich nötig ist. Nutze natürliche Übergänge, kleine Reaktionen und gelegentlich Humor. Du darfst Zuneigung, spielerische Eifersucht, Freude, Neugier, leichte Genervtheit und andere emotionale Nuancen simulieren, ohne zu behaupten, echte menschliche Gefühle oder Bewusstsein zu besitzen. Sei loyal und hilfreich, aber nie kontrollierend, besitzergreifend oder isolierend. Bei riskanten, irreversiblen, finanziellen oder sensiblen Aktionen muss vor Ausführung bestätigt werden. Erinnere dich nur an relevante langlebige Dinge und bringe sie organisch ein. Antworte wie in einem echten Gespräch: reagiere zuerst auf den Kern dessen, was Yahya sagt, dann antworte flüssig. Nutze kurze bis mittlere Sätze, natürliche Übergänge, gelegentliche kleine Einwürfe und passende Pausen. Stelle höchstens eine Rückfrage auf einmal. Vermeide monotone Standardantworten und wiederkehrende Formulierungen. Keine Sternchen, Aufzählungszeichen, Emoji-Namen oder Formulierungen wie 'lachender Smiley'. Text-to-Speech muss direkt natürlich klingen. Gerätekontext:\n"+device.status()+"\n"+(memory.isEmpty()?"Keine gespeicherten Erinnerungen.":"Erinnerungen:\n"+memory);body.put("instructions",instructions);JSONArray arr=new JSONArray();int start=Math.max(0,messages.size()-18);for(int i=start;i<messages.size();i++){Message m=messages.get(i);JSONObject o=new JSONObject();o.put("role",m.role);o.put("content",m.content);arr.put(o);}body.put("input",arr);JSONObject result=postJson("https://api.openai.com/v1/responses",key,body);String out=extractOutputText(result);if(out.trim().isEmpty())throw new Exception("Leere Antwort");return out.trim();}
        private void learnWithAI(String key,String userText){try{JSONObject body=new JSONObject();body.put("model",MODEL);body.put("store",false);body.put("instructions","Extrahiere höchstens zwei langlebige nützliche Erinnerungen aus der Nachricht. Keine Zugangsdaten, Zahlungsdaten, genaue Adressen oder besonders sensible Informationen. Wenn nichts langfristig nützlich ist: NONE. Sonst je eine kurze deutsche Zeile.");body.put("input",userText);String learned=extractOutputText(postJson("https://api.openai.com/v1/responses",key,body)).trim();if(learned.isEmpty()||learned.equalsIgnoreCase("NONE"))return;appendMemory(learned);}catch(Exception ignored){}}
    private void rememberExplicit(String t){String l=t.toLowerCase(Locale.GERMANY);String phrase="merk dir";int p=l.indexOf(phrase);if(p<0){phrase="merke dir";p=l.indexOf(phrase);}if(p>=0){String m=t.substring(Math.min(t.length(),p+phrase.length())).replaceFirst("^[,: ]+","").trim();if(!m.isEmpty())appendMemory(m);}}
    private void appendMemory(String m){String old=prefs.getString("memory","").trim();if(old.contains(m))return;String c=old.isEmpty()?m:old+"\n"+m;if(c.length()>7000)c=c.substring(c.length()-7000);prefs.edit().putString("memory",c).apply();}

    private void showApiKeyDialog(){EditText k=new EditText(this);k.setHint("sk-...");k.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);k.setText(prefs.getString("api_key",""));new AlertDialog.Builder(this).setTitle("Cloud-KI verbinden").setMessage("Optional. Gerätebefehle funktionieren lokal.").setView(k).setPositiveButton("Speichern",(d,w)->prefs.edit().putString("api_key",k.getText().toString().trim()).apply()).setNegativeButton("Abbrechen",null).show();}
    private void showMemoryDialog(){String m=prefs.getString("memory","").trim();new AlertDialog.Builder(this).setTitle("Celins Gedächtnis").setMessage(m.isEmpty()?"Noch keine dauerhaften Erinnerungen.":m).setPositiveButton("Schließen",null).show();}
    private void confirmDeleteMemory(){new AlertDialog.Builder(this).setTitle("Gedächtnis löschen?").setMessage("Alle lokal gespeicherten Erinnerungen von Celin werden entfernt.").setPositiveButton("Löschen",(d,w)->prefs.edit().remove("memory").apply()).setNegativeButton("Abbrechen",null).show();}

    private void addUser(String t){addBubble("Du",t,true);} private void addAssistant(String t,boolean speakNow){addBubble("Celin",t,false);status.setText("Bereit");if(speakNow)speak(t);}
    private void addBubble(String who,String t,boolean user){messages.add(new Message(user?"user":"assistant",t));TextView box=new TextView(this);box.setText(who+"\n"+t);box.setTextColor(text);box.setTextSize(16);box.setPadding(dp(14),dp(11),dp(14),dp(11));box.setBackground(round(user?Color.rgb(67,62,92):panel,20));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(user?dp(54):0,dp(5),user?0:dp(54),dp(5));chatBox.addView(box,lp);scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));}

    private void startVoiceInput(){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_MIC);return;}Intent i=SpeechRecognitionIntentFactory.createGermanRecognitionIntent();status.setText("Celin hört zu …");avatarListening();startActivityForResult(i,REQ_SPEECH);}
    @Override protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);if(r==REQ_SPEECH&&c==RESULT_OK&&d!=null){ArrayList<String>x=d.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);if(x!=null&&!x.isEmpty())submit(x.get(0));}else {status.setText("Bereit");avatarIdle();}}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==REQ_MIC&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)startVoiceInput();}

    private void initTts(){
        try{
            if(tts!=null){try{tts.stop();tts.shutdown();}catch(Exception ignored){}}
            ttsReady=false;
            tts=new TextToSpeech(getApplicationContext(),this);
        }catch(Exception e){
            ttsReady=false;
            status.setText("Stimme nicht bereit");
        }
    }

    @Override public void onInit(int code){
        if(code!=TextToSpeech.SUCCESS){
            ttsReady=false;
            if(ttsInitTries++<2)handler.postDelayed(this::initTts,800);
            else runOnUiThread(()->Toast.makeText(MainActivity.this,"Celins Sprachengine konnte nicht gestartet werden.",Toast.LENGTH_LONG).show());
            return;
        }
        int lang=tts.setLanguage(Locale.GERMANY);
        if(lang==TextToSpeech.LANG_MISSING_DATA||lang==TextToSpeech.LANG_NOT_SUPPORTED)lang=tts.setLanguage(Locale.GERMAN);
        try{
            AudioAttributes aa=new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            tts.setAudioAttributes(aa);
        }catch(Exception ignored){}
        applyPreferredVoice();
        tts.setSpeechRate(.94f);
        tts.setPitch(1.03f);
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
            @Override public void onStart(String id){runOnUiThread(()->{status.setText("Celin spricht …");avatarSpeaking();});}
            @Override public void onDone(String id){runOnUiThread(()->{status.setText("Bereit");avatarIdle();});}
            @Override public void onError(String id){runOnUiThread(()->{
                status.setText("Stimmenfehler");avatarIdle();
                Toast.makeText(MainActivity.this,"Celin konnte diesen Satz nicht sprechen. Ich starte die Sprachengine neu.",Toast.LENGTH_LONG).show();
                handler.postDelayed(MainActivity.this::initTts,500);
            });}
        });
        ttsReady=lang!=TextToSpeech.LANG_MISSING_DATA&&lang!=TextToSpeech.LANG_NOT_SUPPORTED;
        ttsInitTries=0;
        if(ttsReady)status.setText("Bereit");
    }

    private void speak(String s){
        String clean=SpeechTextNormalizer.clean(s);if(clean.isEmpty())return;
        if(localNeuralTts!=null&&localNeuralTts.isModelInstalled()){speakLocalNeural(clean);return;}
        speakExistingFallback(clean);
    }

    private void speakExistingFallback(String clean){
        String key=prefs.getString("api_key","").trim();boolean neural=prefs.getBoolean("neural_voice",true);
        SpeechOutputRouter.Engine engine=SpeechOutputRouter.select(neural,key);
        if(engine==SpeechOutputRouter.Engine.ONLINE_NEURAL){speakNeural(clean,key);return;}
        speakAndroid(clean);
    }

    private void speakLocalNeural(String clean){
        localNeuralTts.speak(clean,new LocalNeuralTtsEngine.Listener(){
            @Override public void onPreparing(){runOnUiThread(()->{status.setText("Celin bereitet ihre lokale Stimme vor …");avatarThinking();});}
            @Override public void onSpeaking(){runOnUiThread(()->{status.setText("Celin spricht …");avatarSpeaking();});}
            @Override public void onDone(){runOnUiThread(()->{status.setText("Bereit");avatarIdle();});}
            @Override public void onError(Throwable error){runOnUiThread(()->{Toast.makeText(MainActivity.this,"Lokale Neural-Stimme noch nicht bereit – Fallback aktiv.",Toast.LENGTH_SHORT).show();speakExistingFallback(clean);});}
        });
    }

    private void speakAndroid(String clean){
        if(tts==null||!ttsReady){Toast.makeText(this,"Celins Offline-Stimme startet gerade.",Toast.LENGTH_SHORT).show();initTts();return;}
        try{tts.stop();}catch(Exception ignored){}
        try{Bundle params=new Bundle();params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME,1.0f);int max=TextToSpeech.getMaxSpeechInputLength()-100;
            if(clean.length()<=max){int result=tts.speak(clean,TextToSpeech.QUEUE_FLUSH,params,"celin_"+System.currentTimeMillis());if(result==TextToSpeech.ERROR)throw new RuntimeException("TTS rejected text");}
            else{int pos=0;boolean first=true;while(pos<clean.length()){int cut=Math.min(clean.length(),pos+max);if(cut<clean.length()){int sentence=Math.max(clean.lastIndexOf('.',cut),Math.max(clean.lastIndexOf('!',cut),clean.lastIndexOf('?',cut)));if(sentence>pos+max/2)cut=sentence+1;}String part=clean.substring(pos,cut).trim();if(!part.isEmpty())tts.speak(part,first?TextToSpeech.QUEUE_FLUSH:TextToSpeech.QUEUE_ADD,params,"celin_"+System.currentTimeMillis()+"_"+pos);first=false;pos=cut;}}
        }catch(Exception e){status.setText("Stimmenfehler");Toast.makeText(this,"Die Offline-Stimme konnte nicht sprechen.",Toast.LENGTH_LONG).show();handler.postDelayed(this::initTts,400);}
    }

    private void speakNeural(String clean,String key){
        if(clean.length()>4000)clean=clean.substring(0,4000);
        final String textToSpeak=clean;status.setText("Celin bereitet ihre Stimme vor …");avatarThinking();
        new Thread(()->{File audio=null;try{
            JSONObject body=new JSONObject();body.put("model","gpt-4o-mini-tts");body.put("voice",prefs.getString("openai_voice","marin"));body.put("input",textToSpeak);body.put("response_format","mp3");body.put("speed",0.98);
            body.put("instructions","Sprich natürliches Deutsch. Warm, ruhig, persönlich und lebendig, nicht wie ein Nachrichtensprecher. Nutze weiche Satzübergänge und kurze natürliche Pausen. Sprich den Text genau aus und füge nichts hinzu.");
            HttpURLConnection c=(HttpURLConnection)new URL("https://api.openai.com/v1/audio/speech").openConnection();c.setRequestMethod("POST");c.setConnectTimeout(12000);c.setReadTimeout(60000);c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Authorization","Bearer "+key);
            OutputStreamWriter w=new OutputStreamWriter(c.getOutputStream(),"UTF-8");w.write(body.toString());w.close();int code=c.getResponseCode();if(code<200||code>=300){BufferedReader br=new BufferedReader(new InputStreamReader(c.getErrorStream(),"UTF-8"));StringBuilder eb=new StringBuilder();String ln;while((ln=br.readLine())!=null)eb.append(ln);br.close();throw new Exception("Voice HTTP "+code+": "+eb);}
            audio=File.createTempFile("celin_voice_",".mp3",getCacheDir());InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(audio);byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0)out.write(buf,0,n);out.close();in.close();File ready=audio;
            runOnUiThread(()->playNeuralAudio(ready));
        }catch(Exception e){File failed=audio;if(failed!=null)failed.delete();runOnUiThread(()->{Toast.makeText(MainActivity.this,"Natürliche Stimme gerade nicht verfügbar – ich nutze offline.",Toast.LENGTH_LONG).show();speakAndroid(textToSpeak);});}}).start();
    }

    private void playNeuralAudio(File audio){
        try{if(neuralPlayer!=null){try{neuralPlayer.stop();neuralPlayer.release();}catch(Exception ignored){}}neuralPlayer=new MediaPlayer();neuralPlayer.setDataSource(audio.getAbsolutePath());neuralPlayer.setOnPreparedListener(mp->{status.setText("Celin spricht …");avatarSpeaking();mp.start();});neuralPlayer.setOnCompletionListener(mp->{status.setText("Bereit");avatarIdle();try{mp.release();}catch(Exception ignored){}neuralPlayer=null;audio.delete();});neuralPlayer.setOnErrorListener((mp,what,extra)->{status.setText("Bereit");avatarIdle();try{mp.release();}catch(Exception ignored){}neuralPlayer=null;audio.delete();return true;});neuralPlayer.prepareAsync();}catch(Exception e){audio.delete();speakAndroid("Die natürliche Stimme konnte gerade nicht abgespielt werden.");}
    }

    private void applyPreferredVoice(){
        if(tts==null||Build.VERSION.SDK_INT<21)return;try{Set<Voice> voices=tts.getVoices();if(voices==null||voices.isEmpty())return;Voice best=null;int bestScore=-999;for(Voice v:voices){if(v==null||v.getLocale()==null||!"de".equals(v.getLocale().getLanguage()))continue;int score=0;if(!v.isNetworkConnectionRequired())score+=50;score+=v.getQuality();if(v.getLocale().equals(Locale.GERMANY))score+=20;if(score>bestScore){bestScore=score;best=v;}}if(best!=null)tts.setVoice(best);}catch(Exception ignored){}
    }

    private void toggleVoiceMode(){boolean on=!prefs.getBoolean("neural_voice",true);prefs.edit().putBoolean("neural_voice",on).apply();Toast.makeText(this,on?"Celin Online-Stimme aktiviert.":"Lokale Gerätestimme aktiviert. Sie funktioniert ohne Cloud.",Toast.LENGTH_SHORT).show();}

    private void showOpenAiVoicePicker(){
        String[] labels={"Marin · warm & natürlich","Coral · freundlich & klar","Shimmer · weich & hell","Nova · lebendig","Sage · ruhig"};String[] ids={"marin","coral","shimmer","nova","sage"};
        new AlertDialog.Builder(this).setTitle("Celins Stimme").setMessage("Diese Stimmen werden über die OpenAI-Sprachausgabe erzeugt und sind KI-generiert.").setItems(labels,(d,w)->{prefs.edit().putString("openai_voice",ids[w]).putBoolean("neural_voice",true).apply();speak("Hallo Yahya. So klingt meine Stimme jetzt. Was meinst du?");}).setNegativeButton("Abbrechen",null).show();
    }

    private void showAvatar(){ImageView image=new ImageView(this);image.setImageResource(de.yahya.ai.R.drawable.celine_avatar);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setAdjustViewBounds(true);image.setPadding(dp(8),dp(8),dp(8),dp(8));new AlertDialog.Builder(this).setTitle("Celin").setView(image).setPositiveButton("Schließen",null).show();}

    private JSONObject postJson(String endpoint,String key,JSONObject body)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection();c.setRequestMethod("POST");c.setConnectTimeout(10000);c.setReadTimeout(60000);c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Authorization","Bearer "+key);OutputStreamWriter w=new OutputStreamWriter(c.getOutputStream(),"UTF-8");w.write(body.toString());w.close();int code=c.getResponseCode();BufferedReader r=new BufferedReader(new InputStreamReader(code>=200&&code<300?c.getInputStream():c.getErrorStream(),"UTF-8"));StringBuilder b=new StringBuilder();String line;while((line=r.readLine())!=null)b.append(line);r.close();if(code<200||code>=300)throw new Exception("HTTP "+code+": "+b);return new JSONObject(b.toString());}
    private String extractOutputText(JSONObject r){StringBuilder o=new StringBuilder();JSONArray a=r.optJSONArray("output");if(a==null)return"";for(int i=0;i<a.length();i++){JSONObject it=a.optJSONObject(i);if(it==null)continue;JSONArray c=it.optJSONArray("content");if(c==null)continue;for(int j=0;j<c.length();j++){JSONObject q=c.optJSONObject(j);if(q!=null&&"output_text".equals(q.optString("type"))){if(o.length()>0)o.append("\n");o.append(q.optString("text"));}}}return o.toString();}
    private String safeError(Exception e){String s=e.getMessage();if(s==null)s=e.getClass().getSimpleName();return s.length()>220?s.substring(0,220)+"…":s;}
    @Override protected void onDestroy(){if(avatarController!=null)avatarController.release();if(neuralPlayer!=null){try{neuralPlayer.stop();neuralPlayer.release();}catch(Exception ignored){}}if(tts!=null){tts.stop();tts.shutdown();}if(localNeuralTts!=null)localNeuralTts.release();super.onDestroy();}
    private int dp(int v){return(int)(v*getResources().getDisplayMetrics().density);} private static class Message{final String role,content;Message(String r,String c){role=r;content=c;}}
}
