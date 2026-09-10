package de.yahya.ai;

import java.util.ArrayList;
import java.util.List;

/** Android-only adapter for the pure-Java G2.1 Tool Cortex. */
final class CelineAndroidToolBackend implements CelineToolCortexG21.Backend {
    private final DeviceBridge device;

    CelineAndroidToolBackend(DeviceBridge device) {
        if (device == null) throw new IllegalArgumentException("device must not be null");
        this.device = device;
    }

    @Override public String deviceStatus() { return device.status(); }
    @Override public boolean accessibilityActive() { return PrincessAccessibilityService.isRunning(); }
    @Override public boolean notificationListenerActive() { return PrincessNotificationService.isRunning(); }
    @Override public List<String> recentNotifications() {
        return new ArrayList<>(PrincessNotificationService.recent());
    }
    @Override public String screenSummary() { return PrincessAccessibilityService.screenSummary(); }
    @Override public boolean goHome() { return PrincessAccessibilityService.goHome(); }
    @Override public boolean goBack() { return PrincessAccessibilityService.goBack(); }
    @Override public boolean openRecents() { return PrincessAccessibilityService.openRecents(); }
    @Override public boolean openApp(String query) { return device.openApp(query); }
    @Override public boolean clickText(String text) { return PrincessAccessibilityService.clickText(text); }
    @Override public boolean setText(String text) { return PrincessAccessibilityService.setText(text); }
    @Override public boolean tap(float x, float y) { return PrincessAccessibilityService.tap(x, y); }
    @Override public void openAccessibilitySettings() { device.openAccessibilitySettings(); }
    @Override public void openNotificationSettings() { device.openNotificationSettings(); }
    @Override public void openAllFilesSettings() { device.openAllFilesSettings(); }
}
