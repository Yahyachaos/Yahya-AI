package de.yahya.ai;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

/**
 * v29 controller: keep the path deliberately small until the imported GLB is visibly rendered.
 * There is one 3D renderer, one model, and no automatic model quarantine / renderer switching.
 */
public final class CelineAvatarController implements SpeechAudioBus.Listener {
    public enum State { IDLE, LISTENING, THINKING, SPEAKING }

    private final View motionView;
    private final ImageView avatar;
    private final CelineFaceOverlayView face;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Celine3DView threeD;
    private Celine3DView pending3D;
    private boolean using3D;
    private boolean released;
    private State state = State.IDLE;

    public CelineAvatarController(View motionView, ImageView avatar,
                                  CelineFaceOverlayView face, float density) {
        this.motionView = motionView;
        this.avatar = avatar;
        this.face = face;

        if (face != null) face.start();
        SpeechAudioBus.setListener(this);

        if (motionView instanceof ViewGroup && avatar != null && Celine3DView.hasModel(avatar.getContext())) {
            motionView.post(this::startSingle3DBaseline);
        }
    }

    private void startSingle3DBaseline() {
        if (released || using3D || pending3D != null || !(motionView instanceof ViewGroup) || avatar == null) {
            return;
        }
        if (!Celine3DView.hasModel(avatar.getContext())) return;

        final ViewGroup host = (ViewGroup) motionView;
        try {
            Toast.makeText(avatar.getContext(), "Celines 3D-Modell wird geladen …", Toast.LENGTH_SHORT).show();
            final Celine3DView candidate = new Celine3DView(avatar.getContext(), true);
            pending3D = candidate;
            candidate.setAvatarState(state);

            // Add the real 3D surface as the top child. No second renderer is created behind it.
            host.addView(candidate, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));

            candidate.verifyVisibleFrame(handler, visible -> {
                if (released || pending3D != candidate) {
                    removeCandidate(host, candidate);
                    return;
                }

                if (visible) {
                    pending3D = null;
                    threeD = candidate;
                    using3D = true;
                    avatar.setVisibility(View.GONE);
                    if (face != null) {
                        face.stop();
                        face.setVisibility(View.GONE);
                    }
                    Toast.makeText(avatar.getContext(),
                            "3D-Celine ist sichtbar geladen.", Toast.LENGTH_LONG).show();
                } else {
                    pending3D = null;
                    String reason = candidate.getRenderFailureReason();
                    removeCandidate(host, candidate);
                    avatar.setVisibility(View.VISIBLE);
                    if (face != null) {
                        face.setVisibility(View.VISIBLE);
                        face.start();
                    }
                    String suffix = reason == null ? "" : "\n" + reason;
                    Toast.makeText(avatar.getContext(),
                            "3D-Standbild wurde nicht sichtbar gerendert." + suffix,
                            Toast.LENGTH_LONG).show();
                }
            });
        } catch (Throwable e) {
            pending3D = null;
            using3D = false;
            avatar.setVisibility(View.VISIBLE);
            if (face != null) {
                face.setVisibility(View.VISIBLE);
                face.start();
            }
            String reason = e.getMessage();
            if (reason == null || reason.trim().isEmpty()) reason = e.getClass().getSimpleName();
            Toast.makeText(avatar.getContext(),
                    "3D-Renderer konnte nicht starten: " + reason,
                    Toast.LENGTH_LONG).show();
        }
    }

    private static void removeCandidate(ViewGroup host, Celine3DView candidate) {
        try { candidate.stopRendering(); } catch (Throwable ignored) {}
        try { host.removeView(candidate); } catch (Throwable ignored) {}
    }

    public State getState() { return state; }
    public boolean isUsing3D() { return using3D; }

    public void setState(State next) {
        state = next == null ? State.IDLE : next;
        if (pending3D != null) pending3D.setAvatarState(state);
        if (threeD != null) threeD.setAvatarState(state);

        // Only keep the old face overlay coherent while no 3D model is visible.
        if (!using3D && face != null) {
            switch (state) {
                case LISTENING: face.setActivity(CelineFaceOverlayView.Activity.LISTENING); break;
                case THINKING: face.setActivity(CelineFaceOverlayView.Activity.THINKING); break;
                case SPEAKING: face.setActivity(CelineFaceOverlayView.Activity.SPEAKING); break;
                default: face.setActivity(CelineFaceOverlayView.Activity.IDLE); break;
            }
        }
    }

    @Override public void onSpeechAudioLevel(float level) {
        if (threeD != null) threeD.setSpeechEnergy(level);
        if (!using3D && face != null) face.setMouthLevel(state == State.SPEAKING ? level : 0f);
    }

    @Override public void onSpeechViseme(SpeechVisemeAnalyzer.Cue cue) {
        if (threeD != null) threeD.setViseme(cue);
        if (!using3D && face != null) {
            face.setViseme(state == State.SPEAKING ? cue : SpeechVisemeAnalyzer.silent());
        }
    }

    public void lookToward(float nx, float ny) {
        if (threeD != null) threeD.setLook(nx, ny);
    }

    public void releaseLook() {
        if (threeD != null) threeD.releaseLook();
    }

    public void blink() {
        if (!using3D && face != null) face.blinkNow(false);
    }

    public void release() {
        released = true;
        handler.removeCallbacksAndMessages(null);
        SpeechAudioBus.clearListener(this);
        if (pending3D != null) {
            try { pending3D.stopRendering(); } catch (Throwable ignored) {}
        }
        if (threeD != null) {
            try { threeD.stopRendering(); } catch (Throwable ignored) {}
        }
        if (face != null) face.stop();
    }
}
