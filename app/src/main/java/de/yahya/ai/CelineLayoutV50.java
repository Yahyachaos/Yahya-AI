package de.yahya.ai;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.WeakHashMap;

/**
 * v50 layout guard.
 *
 * Keeps the conversation/composer usable on HOME without touching Filament, materials or skin
 * matrices. When the existing v45 call reparents the avatar stage, the same guard lets the stage
 * fill the available call slot instead of keeping the large fixed HOME height.
 */
final class CelineLayoutV50 {
    private static final WeakHashMap<FrameLayout, Boolean> WATCHED_STAGES = new WeakHashMap<>();

    private CelineLayoutV50() {}

    static void install(Activity activity, View decor) {
        if (activity == null || decor == null) return;
        activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        Celine3DView threeD = find3D(decor);
        if (threeD == null || !(threeD.getParent() instanceof FrameLayout)) return;
        FrameLayout stage = (FrameLayout) threeD.getParent();
        stage.setContentDescription("Celin 3D Ansicht");

        synchronized (WATCHED_STAGES) {
            if (!WATCHED_STAGES.containsKey(stage)) {
                WATCHED_STAGES.put(stage, Boolean.TRUE);
                stage.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                        v.post(() -> applyStage(activity, stage)));
            }
        }

        compactHomeChrome(stage);
        prepareConversationArea(decor);
        applyStage(activity, stage);
    }

    private static void applyStage(Activity activity, FrameLayout stage) {
        if (stage == null || stage.getLayoutParams() == null) return;
        View parent = stage.getParent() instanceof View ? (View) stage.getParent() : null;
        Object parentTag = parent == null ? null : parent.getTag();
        boolean inLiveCall = parentTag instanceof String && ((String) parentTag).contains("v45-stage-slot");

        ViewGroup.LayoutParams lp = stage.getLayoutParams();
        int wanted;
        if (inLiveCall) {
            wanted = ViewGroup.LayoutParams.MATCH_PARENT;
        } else {
            float density = Math.max(1f, activity.getResources().getDisplayMetrics().density);
            int screenH = activity.getResources().getDisplayMetrics().heightPixels;
            int max = Math.round(320f * density);
            int min = Math.round(290f * density);
            int proportional = Math.round(screenH * 0.40f);
            wanted = Math.max(min, Math.min(max, proportional));
        }

        if (lp.height != wanted) {
            lp.height = wanted;
            stage.setLayoutParams(lp);
        }
    }

    private static void compactHomeChrome(FrameLayout stage) {
        if (!(stage.getParent() instanceof LinearLayout)) return;
        LinearLayout profile = (LinearLayout) stage.getParent();
        int d = dp(stage.getContext(), 6);
        profile.setPadding(d, d, d, d);

        for (int i = 0; i < profile.getChildCount(); i++) {
            View child = profile.getChildAt(i);
            if (!(child instanceof TextView)) continue;
            TextView tv = (TextView) child;
            CharSequence value = tv.getText();
            String text = value == null ? "" : value.toString();
            if ("Celin".equals(text)) {
                tv.setTextSize(19f);
                tv.setPadding(dp(stage.getContext(), 6), dp(stage.getContext(), 4), dp(stage.getContext(), 6), 0);
            } else if (text.startsWith("Live mit Celin")) {
                tv.setTextSize(12f);
                tv.setPadding(0, 0, 0, dp(stage.getContext(), 2));
            }
        }
    }

    private static void prepareConversationArea(View root) {
        EditText composer = findEditText(root);
        if (composer != null) {
            composer.setContentDescription("Celin Nachricht schreiben");
            composer.setMinHeight(dp(composer.getContext(), 52));
        }

        ScrollView conversation = findScrollView(root);
        if (conversation != null) {
            conversation.setContentDescription("Celin Gesprächsverlauf");
            conversation.setMinimumHeight(dp(conversation.getContext(), 92));
            conversation.setClipToPadding(false);
        }
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

    private static EditText findEditText(View view) {
        if (view instanceof EditText) return (EditText) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                EditText found = findEditText(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static ScrollView findScrollView(View view) {
        if (view instanceof ScrollView) return (ScrollView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                ScrollView found = findScrollView(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static int dp(Context context, int value) {
        float density = Math.max(1f, context.getResources().getDisplayMetrics().density);
        return Math.round(value * density);
    }
}
