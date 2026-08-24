package de.yahya.ai;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** v36 controller with persistent, tappable 3D diagnostics on the avatar stage. */
public final class CelineAvatarController implements SpeechAudioBus.Listener {
    public enum State { IDLE, LISTENING, THINKING, SPEAKING }

    private final View motionView;
    private final ImageView avatar;
    private final CelineFaceOverlayView face;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float density;
    private final TextView diagnosticsBadge;

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
        this.density = density <= 0f ? 1f : density;

        Context context = avatar != null ? avatar.getContext() : motionView.getContext();
        Celine3DDiagnostics.record(context, "CTL-100", "AvatarController gestartet",
                Celine3DDiagnostics.modelSnapshot(context));

        if (face != null) face.start();
        SpeechAudioBus.setListener(this);
        diagnosticsBadge = installDiagnosticsBadge(context);
        refreshDiagnosticsBadge();
        tickDiagnostics(60);

        boolean materialRepaired = false;
        if (avatar != null) {
            try {
                materialRepaired = CelineGlbMaterialRepair.repairImportedModel(avatar.getContext());
                Celine3DDiagnostics.record(context, "CTL-110", "Material-Reparatur geprüft",
                        "changed=" + materialRepaired);
            } catch (Throwable e) {
                Celine3DDiagnostics.error(context, "CTL-119", "Material-Reparatur FEHLER", e);
            }
        }
        if (materialRepaired && avatar != null) {
            Toast.makeText(avatar.getContext(),
                    "Celines 3D-Textur wurde repariert.", Toast.LENGTH_SHORT).show();
        }

        boolean hasModel = avatar != null && Celine3DView.hasModel(avatar.getContext());
        if (motionView instanceof ViewGroup && avatar != null && hasModel) {
            Celine3DDiagnostics.record(context, "CTL-200", "3D-Start freigegeben", "hasModel=true");
            refreshDiagnosticsBadge();
            motionView.post(this::startSingle3DBaseline);
        } else {
            Celine3DDiagnostics.record(context, "CTL-199", "3D-Start NICHT freigegeben",
                    "hostViewGroup=" + (motionView instanceof ViewGroup) + " avatar=" + (avatar != null) + " hasModel=" + hasModel);
            refreshDiagnosticsBadge();
        }
    }

    private TextView installDiagnosticsBadge(Context context) {
        if (!(motionView instanceof ViewGroup)) return null;
        TextView badge = new TextView(context);
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(10.5f);
        badge.setGravity(Gravity.CENTER_VERTICAL);
        badge.setBackgroundColor(Color.argb(225, 12, 16, 23));
        int p = dp(7);
        badge.setPadding(p, dp(5), p, dp(5));
        badge.setSingleLine(false);
        badge.setMaxLines(2);
        badge.setOnClickListener(v -> showDiagnosticsDialog(context));

        ViewGroup host = (ViewGroup) motionView;
        if (host instanceof FrameLayout) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
            lp.leftMargin = dp(6);
            lp.rightMargin = dp(6);
            lp.bottomMargin = dp(6);
            host.addView(badge, lp);
        } else {
            host.addView(badge, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        badge.bringToFront();
        return badge;
    }

    private void tickDiagnostics(int remaining) {
        if (released || remaining <= 0) return;
        refreshDiagnosticsBadge();
        handler.postDelayed(() -> tickDiagnostics(remaining - 1), 250L);
    }

    private void refreshDiagnosticsBadge() {
        if (diagnosticsBadge == null) return;
        Context context = diagnosticsBadge.getContext();
        diagnosticsBadge.setText("3D-DIAG  " + Celine3DDiagnostics.shortStatus(context) + "\nAntippen für kompletten Ablauf");
        diagnosticsBadge.bringToFront();
    }

    private void showDiagnosticsDialog(Context context) {
        ScrollView scroll = new ScrollView(context);
        TextView report = new TextView(context);
        report.setText(Celine3DDiagnostics.report(context));
        report.setTextColor(Color.rgb(235, 238, 244));
        report.setTextSize(12f);
        report.setTextIsSelectable(true);
        report.setPadding(dp(16), dp(12), dp(16), dp(12));
        scroll.addView(report, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(context)
                .setTitle("Celine 3D Diagnose")
                .setView(scroll)
                .setPositiveButton("Kopieren", (d, w) -> {
                    ClipboardManager cb = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cb != null) cb.setPrimaryClip(ClipData.newPlainText("Celine 3D Diagnose", Celine3DDiagnostics.report(context)));
                    Toast.makeText(context, "3D-Diagnose kopiert.", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("Zurücksetzen", (d, w) -> {
                    Celine3DDiagnostics.clear(context);
                    refreshDiagnosticsBadge();
                    Toast.makeText(context, "3D-Diagnose zurückgesetzt.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Schließen", null)
                .show();
    }

    private int dp(int value) { return Math.max(1, Math.round(value * density)); }

    private void startSingle3DBaseline() {
        Context context = avatar != null ? avatar.getContext() : motionView.getContext();
        if (released || using3D || pending3D != null || !(motionView instanceof ViewGroup) || avatar == null) {
            Celine3DDiagnostics.record(context, "CTL-298", "3D-Start durch Guard blockiert",
                    "released=" + released + " using3D=" + using3D + " pending=" + (pending3D != null) +
                            " host=" + (motionView instanceof ViewGroup) + " avatar=" + (avatar != null));
            refreshDiagnosticsBadge();
            return;
        }
        if (!Celine3DView.hasModel(avatar.getContext())) {
            Celine3DDiagnostics.record(context, "CTL-299", "3D-Start abgebrochen", "hasModel() wurde false");
            refreshDiagnosticsBadge();
            return;
        }

        final ViewGroup host = (ViewGroup) motionView;
        try {
            Celine3DDiagnostics.record(context, "CTL-300", "Celine3DView wird erzeugt", "Filament Start");
            refreshDiagnosticsBadge();
            Toast.makeText(avatar.getContext(),
                    "3D-Modell gefunden – Filament wird gestartet …", Toast.LENGTH_SHORT).show();

            final Celine3DView candidate = new Celine3DView(avatar.getContext(), true);
            pending3D = candidate;
            candidate.setAvatarState(state);
            Celine3DDiagnostics.record(context, "CTL-310", "Celine3DView erzeugt", candidate.getRendererName());

            candidate.setAlpha(1.0f);
            avatar.setVisibility(View.VISIBLE);
            if (face != null) {
                face.setVisibility(View.VISIBLE);
                face.start();
            }

            host.addView(candidate, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            candidate.bringToFront();
            if (diagnosticsBadge != null) diagnosticsBadge.bringToFront();
            Celine3DDiagnostics.record(context, "CTL-320", "3D-View in Avatar-Host eingefügt",
                    "host=" + host.getWidth() + "x" + host.getHeight());
            refreshDiagnosticsBadge();

            candidate.verifyVisibleFrame(handler, visible -> {
                if (released || pending3D != candidate) {
                    Celine3DDiagnostics.record(context, "CTL-398", "3D-Kandidat verworfen",
                            "released=" + released + " pendingMatch=" + (pending3D == candidate));
                    removeCandidate(host, candidate);
                    refreshDiagnosticsBadge();
                    return;
                }

                String reason = candidate.getRenderFailureReason();
                Celine3DDiagnostics.record(context, "CTL-340", "Sichtbarkeitstest zurück",
                        "visible=" + visible + " rendererError=" + reason);
                if (visible || reason == null) {
                    pending3D = null;
                    threeD = candidate;
                    using3D = true;

                    candidate.setAlpha(1.0f);
                    candidate.bringToFront();
                    avatar.setVisibility(View.GONE);
                    if (face != null) {
                        face.stop();
                        face.setVisibility(View.GONE);
                    }
                    Celine3DDiagnostics.record(context, "CTL-350", "3D-CELINE AKTIV",
                            "visibleProbe=" + visible + " · 2D ausgeblendet");
                    if (diagnosticsBadge != null) diagnosticsBadge.bringToFront();
                    refreshDiagnosticsBadge();

                    Toast.makeText(avatar.getContext(),
                            visible
                                    ? "3D-Celine ist sichtbar geladen."
                                    : "3D-Celine wurde geladen; Pixel-Test negativ, Renderer bleibt sichtbar.",
                            Toast.LENGTH_LONG).show();
                } else {
                    pending3D = null;
                    removeCandidate(host, candidate);
                    using3D = false;
                    avatar.setVisibility(View.VISIBLE);
                    if (face != null) {
                        face.setVisibility(View.VISIBLE);
                        face.start();
                    }
                    Celine3DDiagnostics.record(context, "CTL-399", "3D-RENDERER FEHLER – 2D aktiv", reason);
                    refreshDiagnosticsBadge();
                    Toast.makeText(avatar.getContext(),
                            "3D-Rendererfehler: " + reason + " – 2D-Celine bleibt aktiv.",
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
            Celine3DDiagnostics.error(context, "CTL-397", "Celine3DView Start FEHLER", e);
            refreshDiagnosticsBadge();
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
