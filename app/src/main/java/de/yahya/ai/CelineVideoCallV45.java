package de.yahya.ai;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.filament.Camera;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Locale;
import java.util.WeakHashMap;

/**
 * v45 turns the existing v44 room into a real in-app voice/video-call experience.
 * It deliberately reuses MainActivity's established AI, memory and TTS pipeline.
 * Speech recognition stays inside Yahya AI, so the conversation can loop:
 * listen -> think -> speak -> listen without opening Android's external speech UI.
 */
final class CelineVideoCallV45 {
    private static final WeakHashMap<Activity, Session> SESSIONS = new WeakHashMap<>();
    private static final int REQ_CALL_MIC = 145;

    private CelineVideoCallV45() {}

    static void install(Activity activity, View decor) {
        if (!(activity instanceof MainActivity) || decor == null) return;
        Session session;
        synchronized (SESSIONS) {
            session = SESSIONS.get(activity);
            if (session == null) {
                session = new Session((MainActivity) activity, decor);
                SESSIONS.put(activity, session);
            }
        }
        session.installEntryButton();
        session.resumeIfNeeded();
    }

    static void onPaused(Activity activity) {
        Session s;
        synchronized (SESSIONS) { s = SESSIONS.get(activity); }
        if (s != null) s.pauseListening();
    }

    static void onDestroyed(Activity activity) {
        Session s;
        synchronized (SESSIONS) { s = SESSIONS.remove(activity); }
        if (s != null) s.destroy();
    }

    private static final class Session implements RecognitionListener {
        final MainActivity activity;
        final View decor;
        final Handler main = new Handler(Looper.getMainLooper());
        final float density;

        SpeechRecognizer recognizer;
        boolean callActive;
        boolean muted;
        boolean listening;
        boolean awaitingReply;
        boolean sawSpeaking;
        boolean paused;
        long awaitingSince;
        long callStartedAt;
        long callTransitionReadyStartedAt;

        ViewGroup originalStageParent;
        int originalStageIndex = -1;
        ViewGroup.LayoutParams originalStageLayout;
        FrameLayout avatarStage;
        FrameLayout overlay;
        ImageView transitionCover;
        Bitmap transitionBitmap;
        TextView callStatus;
        TextView caption;
        TextView timer;
        Button micButton;

        final Runnable monitor = new Runnable() {
            @Override public void run() {
                if (!callActive) return;
                updateTimer();
                String state = mainStatusText();
                String lower = state.toLowerCase(Locale.GERMANY);

                if (lower.contains("spricht") || lower.contains("bereitet ihre stimme") || lower.contains("bereitet ihre lokale stimme")) {
                    sawSpeaking = true;
                    setCallStatus(lower.contains("bereitet") ? "Celin antwortet gleich …" : "Celin spricht …");
                    stopListeningOnly();
                } else if (lower.contains("denkt") || lower.contains("führt aus")) {
                    setCallStatus("Celin denkt …");
                    stopListeningOnly();
                } else if (awaitingReply && sawSpeaking && isReadyState(lower)) {
                    awaitingReply = false;
                    sawSpeaking = false;
                    setCallStatus("Celin hört zu …");
                    scheduleListen(420L);
                } else if (awaitingReply && (lower.contains("cloud-fehler") || lower.contains("stimmenfehler"))
                        && SystemClock.elapsedRealtime() - awaitingSince > 1200L) {
                    awaitingReply = false;
                    sawSpeaking = false;
                    setCallStatus("Ich höre weiter zu …");
                    scheduleListen(650L);
                } else if (awaitingReply && SystemClock.elapsedRealtime() - awaitingSince > 30000L) {
                    awaitingReply = false;
                    sawSpeaking = false;
                    setCallStatus("Celin hört wieder zu …");
                    scheduleListen(500L);
                }

                main.postDelayed(this, 120L);
            }
        };

        Session(MainActivity activity, View decor) {
            this.activity = activity;
            this.decor = decor;
            density = Math.max(1f, activity.getResources().getDisplayMetrics().density);
        }

        void installEntryButton() {
            Button entry = findButtonByText(decor, "Mit Celin sprechen");
            if (entry == null) entry = findButtonByText(decor, "Mit Celin videochatten");
            if (entry == null) return;
            entry.setText("📞  Mit Celin videochatten");
            entry.setOnClickListener(v -> {
                if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_CALL_MIC);
                    Toast.makeText(activity, "Bitte Mikrofon erlauben und danach den Videochat erneut starten.", Toast.LENGTH_LONG).show();
                    return;
                }
                startCall();
            });
        }

        void startCall() {
            if (callActive) return;
            avatarStage = findAvatarStage(decor);
            if (avatarStage == null) {
                Toast.makeText(activity, "Celines 3D-Ansicht ist noch nicht bereit.", Toast.LENGTH_SHORT).show();
                return;
            }

            callActive = true;
            paused = false;
            muted = false;
            awaitingReply = false;
            sawSpeaking = false;
            callStartedAt = SystemClock.elapsedRealtime();
            captureTransitionCover(3);
        }

        void captureTransitionCover(int remaining) {
            if (!callActive) return;
            ViewGroup content = activity.findViewById(android.R.id.content);
            if (content == null || content.getWidth() <= 1 || content.getHeight() <= 1) {
                if (remaining > 1) {
                    main.postDelayed(() -> captureTransitionCover(remaining - 1), 90L);
                } else {
                    abortCallBeforeOverlay("HOME content not ready for transition capture");
                }
                return;
            }

            int[] location = new int[2];
            content.getLocationInWindow(location);
            Rect source = new Rect(location[0], location[1],
                    location[0] + content.getWidth(), location[1] + content.getHeight());
            final Bitmap bitmap;
            try {
                bitmap = Bitmap.createBitmap(source.width(), source.height(), Bitmap.Config.ARGB_8888);
            } catch (Throwable e) {
                abortCallBeforeOverlay("transition bitmap allocation failed: " + e.getClass().getSimpleName());
                return;
            }

            try {
                PixelCopy.request(activity.getWindow(), source, bitmap, result -> {
                    if (!callActive) {
                        bitmap.recycle();
                        return;
                    }
                    if (result == PixelCopy.SUCCESS) {
                        installTransitionCover(content, bitmap);
                        beginCallAfterTransitionCover();
                        return;
                    }
                    bitmap.recycle();
                    if (remaining > 1) {
                        main.postDelayed(() -> captureTransitionCover(remaining - 1), 90L);
                    } else {
                        abortCallBeforeOverlay("PixelCopy failed result=" + result);
                    }
                }, main);
            } catch (Throwable e) {
                bitmap.recycle();
                if (remaining > 1) {
                    main.postDelayed(() -> captureTransitionCover(remaining - 1), 90L);
                } else {
                    abortCallBeforeOverlay("PixelCopy exception=" + e.getClass().getSimpleName());
                }
            }
        }

        void installTransitionCover(ViewGroup content, Bitmap bitmap) {
            clearTransitionCover();
            transitionBitmap = bitmap;
            transitionCover = new ImageView(activity);
            transitionCover.setScaleType(ImageView.ScaleType.FIT_XY);
            transitionCover.setImageBitmap(bitmap);
            transitionCover.setClickable(true);
            transitionCover.setFocusable(true);
            content.addView(transitionCover, new ViewGroup.LayoutParams(-1, -1));
            transitionCover.bringToFront();
            Celine3DDiagnostics.record(activity, "V45-120", "CALL Übergangsframe gesichert",
                    "content=" + content.getWidth() + "x" + content.getHeight() + " · PixelCopy window content");
        }

        void beginCallAfterTransitionCover() {
            if (!callActive) return;
            buildOverlay();
            reparentStageIntoCall();
            if (transitionCover != null) transitionCover.bringToFront();
            ensureRecognizer();
            setAvatarState("LISTENING");
            setCallStatus("Celin hört zu …");
            Celine3DDiagnostics.record(activity, "V45-100", "Live-Videochat gestartet",
                    "inAppSpeechRecognizer=on · autoConversationLoop=on · v43 texture/unlit unchanged");

            main.removeCallbacks(monitor);
            main.post(monitor);
            main.postDelayed(() -> {
                if (!callActive) return;
                CelineVideoChatV44.ensure(activity, activity.getWindow().getDecorView());
                applyCallLens();
                callTransitionReadyStartedAt = SystemClock.elapsedRealtime();
                waitForStableCallStage(-1, -1, 0, 0);
            }, 280L);
        }

        void waitForStableCallStage(int lastWidth, int lastHeight, int stablePasses, int attempts) {
            if (!callActive) {
                clearTransitionCover();
                return;
            }
            Celine3DView threeD = find3D(avatarStage);
            if (threeD == null) {
                if (attempts >= 40) {
                    abortCallAfterOverlay("Celine3DView missing while waiting for CALL stage");
                } else {
                    main.postDelayed(() -> waitForStableCallStage(lastWidth, lastHeight, 0, attempts + 1), 180L);
                }
                return;
            }

            int width = threeD.getWidth();
            int height = threeD.getHeight();
            int nextStable = width > 0 && height > 0 && width == lastWidth && height == lastHeight
                    ? stablePasses + 1 : 0;
            long elapsed = SystemClock.elapsedRealtime() - callTransitionReadyStartedAt;

            if (attempts >= 40) {
                abortCallAfterOverlay("CALL stage did not stabilize: " + width + "x" + height + " elapsed=" + elapsed);
                return;
            }
            if (elapsed < 2800L || nextStable < 4) {
                main.postDelayed(() -> waitForStableCallStage(width, height, nextStable, attempts + 1), 180L);
                return;
            }

            applyCallLens();
            final int expectedWidth = width;
            final int expectedHeight = height;
            threeD.verifyVisibleFrame(main, visible -> {
                if (!callActive) return;
                if (!visible) {
                    abortCallAfterOverlay("CALL production frame not visibly rendered");
                    return;
                }
                main.postDelayed(() -> {
                    if (!callActive) return;
                    int finalWidth = threeD.getWidth();
                    int finalHeight = threeD.getHeight();
                    if (finalWidth != expectedWidth || finalHeight != expectedHeight) {
                        waitForStableCallStage(finalWidth, finalHeight, 0, attempts + 1);
                        return;
                    }
                    revealReadyCall();
                }, 220L);
            });
        }

        void revealReadyCall() {
            if (!callActive) return;
            Celine3DDiagnostics.record(activity, "V45-121", "CALL Übergang visuell bereit",
                    "stableStage=true · productionFrameVisible=true · cameraLayoutSettled=true");
            ImageView cover = transitionCover;
            if (cover == null) {
                scheduleListen(300L);
                return;
            }
            cover.animate().alpha(0f).setDuration(180L).withEndAction(() -> {
                clearTransitionCover();
                if (callActive) scheduleListen(300L);
            }).start();
        }

        void abortCallBeforeOverlay(String reason) {
            callActive = false;
            paused = false;
            Celine3DDiagnostics.record(activity, "V45-128", "CALL Start vor Overlay abgebrochen", reason);
            clearTransitionCover();
            Toast.makeText(activity, "Videochat konnte nicht sauber starten. Bitte erneut versuchen.", Toast.LENGTH_SHORT).show();
        }

        void abortCallAfterOverlay(String reason) {
            Celine3DDiagnostics.record(activity, "V45-129", "CALL Übergang sicher abgebrochen", reason);
            Toast.makeText(activity, "Videochat wird sicher zurückgesetzt. Bitte erneut versuchen.", Toast.LENGTH_SHORT).show();
            endCall();
            Celine3DView home = find3D(avatarStage);
            if (home != null) {
                home.verifyVisibleFrame(main, visible -> main.postDelayed(this::clearTransitionCover, visible ? 120L : 650L));
            } else {
                main.postDelayed(this::clearTransitionCover, 650L);
            }
        }

        void clearTransitionCover() {
            if (transitionCover != null) {
                transitionCover.animate().cancel();
                transitionCover.setImageDrawable(null);
                if (transitionCover.getParent() instanceof ViewGroup) {
                    ((ViewGroup) transitionCover.getParent()).removeView(transitionCover);
                }
                transitionCover = null;
            }
            if (transitionBitmap != null) {
                if (!transitionBitmap.isRecycled()) transitionBitmap.recycle();
                transitionBitmap = null;
            }
        }

        void buildOverlay() {
            ViewGroup content = activity.findViewById(android.R.id.content);
            if (content == null) return;

            overlay = new FrameLayout(activity);
            overlay.setBackgroundColor(Color.rgb(8, 10, 15));
            overlay.setFocusableInTouchMode(true);
            overlay.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                    endCall();
                    return true;
                }
                return false;
            });

            LinearLayout column = new LinearLayout(activity);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setPadding(dp(12), dp(10), dp(12), dp(14));
            overlay.addView(column, new FrameLayout.LayoutParams(-1, -1));

            LinearLayout top = new LinearLayout(activity);
            top.setGravity(Gravity.CENTER_VERTICAL);
            TextView live = new TextView(activity);
            live.setText("●  Live mit Celin");
            live.setTextSize(19);
            live.setTextColor(Color.rgb(235, 236, 242));
            live.setTypeface(null, 1);
            top.addView(live, new LinearLayout.LayoutParams(0, dp(48), 1f));
            timer = new TextView(activity);
            timer.setText("00:00");
            timer.setTextSize(15);
            timer.setTextColor(Color.rgb(170, 174, 186));
            timer.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
            top.addView(timer, new LinearLayout.LayoutParams(dp(82), dp(48)));
            column.addView(top);

            callStatus = new TextView(activity);
            callStatus.setText("Verbinde mit Celin …");
            callStatus.setTextColor(Color.rgb(188, 192, 204));
            callStatus.setTextSize(14);
            callStatus.setGravity(Gravity.CENTER);
            callStatus.setPadding(dp(10), dp(7), dp(10), dp(7));
            column.addView(callStatus, new LinearLayout.LayoutParams(-1, dp(40)));

            caption = new TextView(activity);
            caption.setText("Sag einfach etwas – Celin hört dir zu.");
            caption.setTextColor(Color.WHITE);
            caption.setTextSize(15);
            caption.setGravity(Gravity.CENTER);
            caption.setPadding(dp(14), dp(9), dp(14), dp(9));
            caption.setBackground(round(Color.argb(190, 21, 24, 32), 18));

            LinearLayout controls = new LinearLayout(activity);
            controls.setGravity(Gravity.CENTER);
            controls.setPadding(0, dp(9), 0, 0);
            micButton = callButton("🎙  Mikrofon", Color.rgb(55, 59, 70));
            micButton.setOnClickListener(v -> toggleMute());
            Button endButton = callButton("✕  Auflegen", Color.rgb(190, 55, 66));
            endButton.setOnClickListener(v -> endCall());
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(58), 1f);
            cp.setMargins(dp(5), 0, dp(5), 0);
            controls.addView(micButton, cp);
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(0, dp(58), 1f);
            ep.setMargins(dp(5), 0, dp(5), 0);
            controls.addView(endButton, ep);

            FrameLayout stageSlot = new FrameLayout(activity);
            stageSlot.setTag("v45-stage-slot");
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, 0, 1f);
            slp.setMargins(0, dp(5), 0, dp(7));
            column.addView(stageSlot, slp);
            column.addView(caption, new LinearLayout.LayoutParams(-1, dp(52)));
            column.addView(controls, new LinearLayout.LayoutParams(-1, dp(72)));

            content.addView(overlay, new ViewGroup.LayoutParams(-1, -1));
            overlay.requestFocus();
        }

        void reparentStageIntoCall() {
            if (avatarStage == null || overlay == null) return;
            View slot = findViewWithTag(overlay, "v45-stage-slot");
            if (!(slot instanceof FrameLayout)) return;
            FrameLayout target = (FrameLayout) slot;

            if (avatarStage.getParent() instanceof ViewGroup) {
                originalStageParent = (ViewGroup) avatarStage.getParent();
                originalStageIndex = originalStageParent.indexOfChild(avatarStage);
                originalStageLayout = avatarStage.getLayoutParams();
                originalStageParent.removeView(avatarStage);
            }
            target.addView(avatarStage, new FrameLayout.LayoutParams(-1, -1));
        }

        void applyCallLens() {
            try {
                Celine3DView threeD = find3D(avatarStage);
                if (threeD == null) return;
                Field f = Celine3DView.class.getDeclaredField("camera");
                f.setAccessible(true);
                Camera camera = (Camera) f.get(threeD);
                int w = Math.max(1, threeD.getWidth());
                int h = Math.max(1, threeD.getHeight());
                camera.setLensProjection(50.0, (double) w / (double) h, 0.05, 1000.0);
                Celine3DDiagnostics.record(activity, "V45-110", "Videochat-Nahkamera aktiv",
                        "lens=50mm · stage=" + w + "x" + h);
            } catch (Throwable e) {
                Celine3DDiagnostics.error(activity, "V45-119", "Videochat-Nahkamera FEHLER", e);
            }
        }

        void ensureRecognizer() {
            if (recognizer != null) return;
            if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
                setCallStatus("Spracherkennung ist auf diesem Gerät nicht verfügbar.");
                return;
            }
            recognizer = SpeechRecognizer.createSpeechRecognizer(activity);
            recognizer.setRecognitionListener(this);
        }

        Intent recognitionIntent() {
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE");
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "de-DE");
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 650L);
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 420L);
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 450L);
            return i;
        }

        void scheduleListen(long delay) {
            if (!callActive || paused || muted || awaitingReply) return;
            main.postDelayed(this::startListeningNow, delay);
        }

        void startListeningNow() {
            if (!callActive || paused || muted || awaitingReply || listening) return;
            ensureRecognizer();
            if (recognizer == null) return;
            String state = mainStatusText().toLowerCase(Locale.GERMANY);
            if (state.contains("spricht") || state.contains("denkt") || state.contains("bereitet")) {
                scheduleListen(450L);
                return;
            }
            try {
                recognizer.startListening(recognitionIntent());
                listening = true;
                setAvatarState("LISTENING");
                setCallStatus("Celin hört zu …");
            } catch (Throwable e) {
                listening = false;
                setCallStatus("Mikrofon startet neu …");
                main.postDelayed(() -> scheduleListen(0L), 850L);
            }
        }

        void toggleMute() {
            muted = !muted;
            if (muted) {
                stopListeningOnly();
                setAvatarState("IDLE");
                setCallStatus("Mikrofon aus");
                if (micButton != null) micButton.setText("🔇  Mikrofon aus");
            } else {
                if (micButton != null) micButton.setText("🎙  Mikrofon");
                setCallStatus("Celin hört zu …");
                scheduleListen(200L);
            }
        }

        void pauseListening() {
            paused = true;
            stopListeningOnly();
        }

        void resumeIfNeeded() {
            if (!callActive) return;
            paused = false;
            main.postDelayed(() -> {
                CelineVideoChatV44.ensure(activity, activity.getWindow().getDecorView());
                applyCallLens();
                if (!muted && !awaitingReply) scheduleListen(400L);
            }, 350L);
        }

        void stopListeningOnly() {
            if (recognizer != null && listening) {
                try { recognizer.cancel(); } catch (Throwable ignored) {}
            }
            listening = false;
        }

        void endCall() {
            if (!callActive) return;
            callActive = false;
            paused = false;
            muted = false;
            awaitingReply = false;
            sawSpeaking = false;
            main.removeCallbacks(monitor);
            stopListeningOnly();
            stopCurrentSpeech();
            setAvatarState("IDLE");

            if (avatarStage != null && avatarStage.getParent() instanceof ViewGroup) {
                ((ViewGroup) avatarStage.getParent()).removeView(avatarStage);
            }
            if (originalStageParent != null && avatarStage != null) {
                int index = Math.max(0, Math.min(originalStageIndex, originalStageParent.getChildCount()));
                if (originalStageLayout != null) originalStageParent.addView(avatarStage, index, originalStageLayout);
                else originalStageParent.addView(avatarStage, index);
            }
            if (overlay != null && overlay.getParent() instanceof ViewGroup) {
                ((ViewGroup) overlay.getParent()).removeView(overlay);
            }
            overlay = null;
            callStatus = null;
            caption = null;
            timer = null;
            micButton = null;

            Celine3DDiagnostics.record(activity, "V45-190", "Live-Videochat beendet", "UI und Mikrofon sauber freigegeben");
            main.postDelayed(() -> CelineVideoChatV44.ensure(activity, activity.getWindow().getDecorView()), 350L);
        }

        void destroy() {
            callActive = false;
            main.removeCallbacks(monitor);
            stopListeningOnly();
            clearTransitionCover();
            if (recognizer != null) {
                try { recognizer.destroy(); } catch (Throwable ignored) {}
                recognizer = null;
            }
        }

        void submitRecognized(String text) {
            if (text == null) return;
            text = text.trim();
            if (text.isEmpty()) { scheduleListen(250L); return; }
            if (caption != null) caption.setText("Du: " + text);
            setCallStatus("Celin denkt …");
            setAvatarState("THINKING");
            awaitingReply = true;
            sawSpeaking = false;
            awaitingSince = SystemClock.elapsedRealtime();
            listening = false;
            try {
                Method submit = MainActivity.class.getDeclaredMethod("submit", String.class);
                submit.setAccessible(true);
                submit.invoke(activity, text);
                Celine3DDiagnostics.record(activity, "V45-130", "Gesprochener Satz an Celin übergeben",
                        "chars=" + text.length());
            } catch (Throwable e) {
                awaitingReply = false;
                setCallStatus("Gespräch konnte nicht übergeben werden.");
                Celine3DDiagnostics.error(activity, "V45-139", "Sprachsatz-Übergabe FEHLER", e);
                scheduleListen(900L);
            }
        }

        void stopCurrentSpeech() {
            try {
                Field f = MainActivity.class.getDeclaredField("tts");
                f.setAccessible(true);
                TextToSpeech tts = (TextToSpeech) f.get(activity);
                if (tts != null) tts.stop();
            } catch (Throwable ignored) {}
            try {
                Field f = MainActivity.class.getDeclaredField("neuralPlayer");
                f.setAccessible(true);
                MediaPlayer p = (MediaPlayer) f.get(activity);
                if (p != null) {
                    try { p.stop(); } catch (Throwable ignored) {}
                    try { p.release(); } catch (Throwable ignored) {}
                    f.set(activity, null);
                }
            } catch (Throwable ignored) {}
            try {
                Field f = MainActivity.class.getDeclaredField("localNeuralTts");
                f.setAccessible(true);
                Object local = f.get(activity);
                if (local instanceof LocalNeuralTtsEngine) ((LocalNeuralTtsEngine) local).release();
            } catch (Throwable ignored) {}
        }

        String mainStatusText() {
            try {
                Field f = MainActivity.class.getDeclaredField("status");
                f.setAccessible(true);
                Object v = f.get(activity);
                if (v instanceof TextView) {
                    CharSequence s = ((TextView) v).getText();
                    return s == null ? "" : s.toString();
                }
            } catch (Throwable ignored) {}
            return "";
        }

        void setAvatarState(String name) {
            try {
                Field f = MainActivity.class.getDeclaredField("avatarController");
                f.setAccessible(true);
                Object controller = f.get(activity);
                if (controller == null) return;
                Class<?> stateClass = Class.forName("de.yahya.ai.CelineAvatarController$State");
                @SuppressWarnings({"unchecked", "rawtypes"})
                Object state = Enum.valueOf((Class<? extends Enum>) stateClass, name);
                Method m = controller.getClass().getDeclaredMethod("setState", stateClass);
                m.setAccessible(true);
                m.invoke(controller, state);
            } catch (Throwable ignored) {}
        }

        void setCallStatus(String text) {
            if (callStatus != null) callStatus.setText(text);
        }

        void updateTimer() {
            if (timer == null || callStartedAt <= 0) return;
            long seconds = Math.max(0L, (SystemClock.elapsedRealtime() - callStartedAt) / 1000L);
            timer.setText(String.format(Locale.GERMANY, "%02d:%02d", seconds / 60L, seconds % 60L));
        }

        boolean isReadyState(String lower) {
            return lower.equals("bereit") || lower.contains("lokal bereit") || lower.contains("bereit ·");
        }

        @Override public void onReadyForSpeech(android.os.Bundle params) {
            listening = true;
            setCallStatus("Celin hört zu …");
        }
        @Override public void onBeginningOfSpeech() { setCallStatus("Ich höre dich …"); }
        @Override public void onRmsChanged(float rmsdB) {
            if (micButton != null && !muted) {
                float a = Math.max(0.72f, Math.min(1.0f, 0.78f + Math.max(0f, rmsdB) / 35f));
                micButton.setAlpha(a);
            }
        }
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onEndOfSpeech() {
            listening = false;
            setCallStatus("Einen Moment …");
        }
        @Override public void onError(int error) {
            listening = false;
            if (!callActive || paused || muted || awaitingReply) return;
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                setCallStatus("Mikrofon-Berechtigung fehlt.");
                return;
            }
            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                setCallStatus("Mikrofon startet neu …");
                scheduleListen(850L);
                return;
            }
            if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    || error == SpeechRecognizer.ERROR_CLIENT) {
                setCallStatus("Celin hört zu …");
                scheduleListen(350L);
                return;
            }
            setCallStatus("Mikrofon verbindet neu …");
            scheduleListen(900L);
        }
        @Override public void onResults(android.os.Bundle results) {
            listening = false;
            ArrayList<String> list = results == null ? null : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (list == null || list.isEmpty()) { scheduleListen(300L); return; }
            submitRecognized(list.get(0));
        }
        @Override public void onPartialResults(android.os.Bundle partialResults) {
            ArrayList<String> list = partialResults == null ? null : partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (list != null && !list.isEmpty() && caption != null) caption.setText("Du: " + list.get(0));
        }
        @Override public void onEvent(int eventType, android.os.Bundle params) {}

        Button callButton(String text, int color) {
            Button b = new Button(activity);
            b.setText(text);
            b.setTextColor(Color.WHITE);
            b.setTextSize(15);
            b.setAllCaps(false);
            b.setBackground(round(color, 28));
            return b;
        }

        GradientDrawable round(int color, int radiusDp) {
            GradientDrawable g = new GradientDrawable();
            g.setColor(color);
            g.setCornerRadius(dp(radiusDp));
            return g;
        }

        int dp(float value) { return Math.round(value * density); }
    }

    private static FrameLayout findAvatarStage(View root) {
        Celine3DView threeD = find3D(root);
        if (threeD != null && threeD.getParent() instanceof FrameLayout) return (FrameLayout) threeD.getParent();
        return null;
    }

    private static Celine3DView find3D(View view) {
        if (view instanceof Celine3DView) return (Celine3DView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Celine3DView found = find3D(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static Button findButtonByText(View root, String contains) {
        if (root instanceof Button) {
            CharSequence t = ((Button) root).getText();
            if (t != null && t.toString().contains(contains)) return (Button) root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                Button found = findButtonByText(g.getChildAt(i), contains);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static View findViewWithTag(View root, Object tag) {
        if (tag != null && tag.equals(root.getTag())) return root;
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                View found = findViewWithTag(g.getChildAt(i), tag);
                if (found != null) return found;
            }
        }
        return null;
    }
}
