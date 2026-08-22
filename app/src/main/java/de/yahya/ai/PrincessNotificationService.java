package de.yahya.ai;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.List;

public class PrincessNotificationService extends NotificationListenerService {
    private static PrincessNotificationService instance;
    @Override public void onListenerConnected() { instance = this; }
    @Override public void onListenerDisconnected() { instance = null; }
    @Override public void onDestroy() { instance = null; super.onDestroy(); }
    public static boolean isRunning() { return instance != null; }

    public static List<String> recent() {
        List<String> out = new ArrayList<>();
        if (instance == null) return out;
        try {
            StatusBarNotification[] list = instance.getActiveNotifications();
            if (list == null) return out;
            for (StatusBarNotification n : list) {
                Bundle e = n.getNotification().extras;
                String title = String.valueOf(e.getCharSequence("android.title", ""));
                String text = String.valueOf(e.getCharSequence("android.text", ""));
                if (!title.trim().isEmpty() || !text.trim().isEmpty()) out.add(n.getPackageName()+": "+title+" — "+text);
                if (out.size() >= 15) break;
            }
        } catch (Exception ignored) { }
        return out;
    }
}
