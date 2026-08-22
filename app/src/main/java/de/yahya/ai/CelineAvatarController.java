package de.yahya.ai;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

/**
 * Conversation-to-avatar bridge for Celin's real-time 3D renderer.
 *
 * The legacy portrait and face overlay are deliberately hidden. Speech energy,
 * listening/thinking/speaking state and gaze are forwarded to Celine3DView,
 * where they drive an actual GLB model in 3D space.
 */
public final class CelineAvatarController implements SpeechAudioBus.Listener {
    public enum State { IDLE, LISTENING, THINKING, SPEAKING }

    private final View motionView;
    private final ImageView legacyAvatar;
    private final CelineFaceOverlayView legacyFace;
    private final Celine3DView avatar3d;
    private State state = State.IDLE;
    private boolean released;

    public CelineAvatarController(View motionView, ImageView avatar, CelineFaceOverlayView face, float density) {
        this.motionView = motionView;
        this.legacyAvatar = avatar;
        this.legacyFace = face;

        if (legacyAvatar != null) legacyAvatar.setVisibility(View.GONE);
        if (legacyFace != null) {
            legacyFace.stop();
            legacyFace.setVisibility(View.GONE);
        }

        Celine3DView created = null;
        if (motionView instanceof ViewGroup) {
            ViewGroup host = (ViewGroup) motionView;
            created = new Celine3DView(host.getContext());
            host.addView(created, 0, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
        avatar3d = created;
        SpeechAudioBus.setListener(this);
        setState(State.IDLE);
    }

    public State getState() { return state; }

    public void setState(State next) {
        if (released) return;
        state = next == null ? State.IDLE : next;
        if (avatar3d == null) return;
        switch (state) {
            case LISTENING:
                avatar3d.setState(Celine3DView.State.LISTENING);
                break;
            case THINKING:
                avatar3d.setState(Celine3DView.State.THINKING);
                break;
            case SPEAKING:
                avatar3d.setState(Celine3DView.State.SPEAKING);
                break;
            case IDLE:
            default:
                avatar3d.setState(Celine3DView.State.IDLE);
                break;
        }
    }

    @Override public void onSpeechAudioLevel(float level) {
        if (!released && avatar3d != null) avatar3d.setSpeechLevel(level);
    }

    @Override public void onSpeechViseme(SpeechVisemeAnalyzer.Cue cue) {
        // The 3D path deliberately does not draw a 2D mouth overlay. A rigged Celin
        // GLB can map these cues to facial morph targets once the final face rig is present.
    }

    public void lookToward(float nx, float ny) {
        if (!released && avatar3d != null) avatar3d.setGaze(nx * 2f, ny * 2f);
    }

    public void releaseLook() {
        if (!released && avatar3d != null) avatar3d.releaseGaze();
    }

    public void blink() {
        // Blink will be a morph-target command on the final rig; no fake 2D blink is drawn.
    }

    public void release() {
        if (released) return;
        released = true;
        SpeechAudioBus.clearListener(this);
        if (avatar3d != null) avatar3d.destroy();
    }
}
