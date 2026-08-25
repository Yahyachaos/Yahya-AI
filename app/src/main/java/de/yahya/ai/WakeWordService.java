package de.yahya.ai;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.*;
import android.speech.*;
import java.util.*;

public class WakeWordService extends Service implements RecognitionListener {
    public static final String CHANNEL="celin_wake";
    private SpeechRecognizer recognizer;
    private Intent listenIntent;
    private Handler handler;
    private AudioManager audioManager;
    private boolean listening=false;
    private boolean mediaSuspended=false;
    private boolean destroyed=false;

    private final Runnable restartRunnable=()->{
        if(destroyed||mediaSuspended||recognizer==null)return;
        try{recognizer.startListening(listenIntent);listening=true;}
        catch(Exception e){listening=false;restart(1500);}
    };

    // Samsung and other Android devices can route/interrupt media while SpeechRecognizer owns
    // the microphone continuously. Yield immediately whenever another app is actively playing
    // media (Deezer, WhatsApp voice notes, etc.) and resume only after that playback is finished.
    private final Runnable mediaGuard=new Runnable(){
        @Override public void run(){
            if(destroyed)return;
            boolean mediaActive=false;
            try{mediaActive=audioManager!=null&&audioManager.isMusicActive();}catch(Throwable ignored){}
            if(mediaActive){
                if(!mediaSuspended){
                    mediaSuspended=true;
                    handler.removeCallbacks(restartRunnable);
                    if(recognizer!=null){try{recognizer.cancel();}catch(Exception ignored){}}
                    listening=false;
                }
            }else if(mediaSuspended){
                mediaSuspended=false;
                restart(700);
            }
            handler.postDelayed(this,300);
        }
    };

    @Override public void onCreate(){
        super.onCreate();
        handler=new Handler(Looper.getMainLooper());
        audioManager=(AudioManager)getSystemService(Context.AUDIO_SERVICE);
        createChannel();
        Notification n=new Notification.Builder(this,CHANNEL)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Yahya AI · Celin hört zu")
                .setContentText("Sag „Celin“, um die Assistentin zu aktivieren.")
                .setOngoing(true).build();
        startForeground(501,n);
        startRecognizer();
        handler.post(mediaGuard);
    }

    private void startRecognizer(){
        if(!SpeechRecognizer.isRecognitionAvailable(this))return;
        if(recognizer!=null){try{recognizer.destroy();}catch(Exception ignored){}}
        recognizer=SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(this);
        listenIntent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        listenIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        listenIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"de-DE");
        listenIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
        listenIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3);
        restart(300);
    }

    private void restart(long delay){
        if(destroyed||mediaSuspended||handler==null)return;
        handler.removeCallbacks(restartRunnable);
        handler.postDelayed(restartRunnable,delay);
    }

    private void inspect(Bundle b){
        if(b==null||mediaSuspended)return;
        ArrayList<String> list=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if(list==null)return;
        for(String s:list){
            if(s==null)continue;
            String l=s.toLowerCase(Locale.GERMANY);
            int p=l.indexOf("celin");if(p<0)p=l.indexOf("selin");
            if(p>=0){
                String command=s.substring(Math.min(s.length(),p+5)).replaceFirst("^[, .:;-]+","").trim();
                Intent i=new Intent(this,MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
                i.putExtra("wake_celin",true);i.putExtra("wake_command",command);startActivity(i);
                try{recognizer.cancel();}catch(Exception ignored){}
                listening=false;restart(1800);return;
            }
        }
    }

    @Override public void onReadyForSpeech(Bundle params){}
    @Override public void onBeginningOfSpeech(){}
    @Override public void onRmsChanged(float rmsdB){}
    @Override public void onBufferReceived(byte[] buffer){}
    @Override public void onEndOfSpeech(){listening=false;restart(500);}
    @Override public void onError(int error){listening=false;restart(error==SpeechRecognizer.ERROR_RECOGNIZER_BUSY?1200:600);}
    @Override public void onResults(Bundle results){inspect(results);listening=false;restart(600);}
    @Override public void onPartialResults(Bundle partialResults){inspect(partialResults);}
    @Override public void onEvent(int eventType,Bundle params){}

    private void createChannel(){
        if(Build.VERSION.SDK_INT>=26){
            NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Celin Aktivierungswort",NotificationManager.IMPORTANCE_LOW));
        }
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(!listening&&!mediaSuspended)restart(200);
        return START_STICKY;
    }

    @Override public void onDestroy(){
        destroyed=true;
        if(handler!=null){handler.removeCallbacks(restartRunnable);handler.removeCallbacks(mediaGuard);}
        if(recognizer!=null){try{recognizer.cancel();}catch(Exception ignored){}try{recognizer.destroy();}catch(Exception ignored){}}
        recognizer=null;listening=false;
        super.onDestroy();
    }

    @Override public android.os.IBinder onBind(Intent intent){return null;}
}
