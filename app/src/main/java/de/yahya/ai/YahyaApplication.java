package de.yahya.ai;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/** Adds the in-app avatar import control to Yahya AI's main screen. */
public final class YahyaApplication extends Application implements Application.ActivityLifecycleCallbacks {
    private static final int IMPORT_BUTTON_ID = 0x71A11;

    @Override public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
    }

    @Override public void onActivityResumed(Activity activity) {
        if (!(activity instanceof MainActivity)) return;
        activity.getWindow().getDecorView().post(() -> ensureImportButton(activity));
    }

    private void ensureImportButton(Activity activity) {
        FrameLayout content = activity.findViewById(android.R.id.content);
        if (content == null || content.findViewById(IMPORT_BUTTON_ID) != null || content.getChildCount() == 0) return;
        View child = content.getChildAt(0);
        if (!(child instanceof LinearLayout)) return;
        LinearLayout root = (LinearLayout) child;

        Button button = new Button(activity);
        button.setId(IMPORT_BUTTON_ID);
        button.setText("⬆  3D-Avatar importieren");
        button.setAllCaps(false);
        button.setTextSize(15f);
        button.setOnClickListener(v -> activity.startActivity(new android.content.Intent(activity, AvatarPickerActivity.class)));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        int d = (int) activity.getResources().getDisplayMetrics().density;
        lp.setMargins(0, 0, 0, Math.max(6, 8 * d));

        // Main screen order: title, CELIN label, status, then avatar card.
        // Insert directly before the avatar card so the control is easy to find.
        int index = Math.min(3, root.getChildCount());
        root.addView(button, index, lp);
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) {}
    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityPaused(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
    @Override public void onActivityDestroyed(Activity activity) {}
}
