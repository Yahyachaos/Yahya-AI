#!/usr/bin/env python3
from __future__ import annotations

import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BRAIN = ROOT / "app/src/main/java/de/yahya/ai/CelineBrain.java"
CORTEX = ROOT / "app/src/main/java/de/yahya/ai/CelineToolCortexG21.java"


def fail(message: str) -> None:
    raise SystemExit(message)


def main() -> int:
    source = CORTEX.read_text(encoding="utf-8")
    if "import android." in source:
        fail("G2.1 cortex must remain Android-free")
    for forbidden in ("Class.forName", "getDeclaredMethod", "Runtime.getRuntime().exec", "ProcessBuilder"):
        if forbidden in source:
            fail(f"forbidden dynamic execution/reflection in Tool Cortex: {forbidden}")
    for tool_id in (
        "device.status", "notifications.recent", "screen.read",
        "navigation.home", "navigation.back", "navigation.recents",
        "app.open", "ui.click_text", "ui.set_text", "ui.tap",
        "settings.accessibility", "settings.notifications", "settings.all_files",
    ):
        if f'"{tool_id}"' not in source:
            fail(f"missing allowlisted tool: {tool_id}")

    probe = r'''package de.yahya.ai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CelineG21ToolProbe {
    static final class FakeBackend implements CelineToolCortexG21.Backend {
        boolean accessibility;
        boolean notifications;
        boolean openApp;
        boolean click;
        boolean setText;
        boolean tap;
        boolean throwStatus;
        int settingsOpened;
        String screen = "Inbox | Nachricht";
        List<String> notificationRows = new ArrayList<>();

        @Override public String deviceStatus() {
            if (throwStatus) throw new IllegalStateException("boom");
            return "RAM frei: 4 GB";
        }
        @Override public boolean accessibilityActive() { return accessibility; }
        @Override public boolean notificationListenerActive() { return notifications; }
        @Override public List<String> recentNotifications() { return notificationRows; }
        @Override public String screenSummary() { return screen; }
        @Override public boolean goHome() { return true; }
        @Override public boolean goBack() { return true; }
        @Override public boolean openRecents() { return true; }
        @Override public boolean openApp(String query) { return openApp; }
        @Override public boolean clickText(String text) { return click; }
        @Override public boolean setText(String text) { return setText; }
        @Override public boolean tap(float x, float y) { return tap; }
        @Override public void openAccessibilitySettings() { settingsOpened++; }
        @Override public void openNotificationSettings() { settingsOpened++; }
        @Override public void openAllFilesSettings() { settingsOpened++; }
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        FakeBackend backend = new FakeBackend();
        CelineToolCortexG21 cortex = new CelineToolCortexG21(backend);

        List<CelineToolCortexG21.TypedToolDescriptor> tools = cortex.typedTools();
        check(tools.size() == 13, "expected 13 allowlisted tools, got " + tools.size());
        Set<String> ids = new HashSet<>();
        for (CelineToolCortexG21.TypedToolDescriptor tool : tools) {
            check(ids.add(tool.id), "duplicate tool id: " + tool.id);
        }

        CelineToolCortexG21.TypedToolDescriptor app = cortex.descriptor("app.open");
        check(app != null, "app.open descriptor missing");
        check(app.riskClass == CelineRiskClass.L1_REVERSIBLE_LOCAL, "app.open risk wrong");
        check(app.parameters.size() == 1, "app.open schema missing");
        check(app.parameters.get(0).slot == CelineToolCortexG21.ArgumentSlot.TARGET, "app.open target slot wrong");
        check(app.parameters.get(0).required, "app.open must require app_name");

        CelineToolCortexG21.TypedToolDescriptor click = cortex.descriptor("ui.click_text");
        check(click.riskClass == CelineRiskClass.L2_EXTERNAL_STATE_CHANGE, "click risk must be L2 metadata");
        check(click.preconditions.contains(CelineToolCortexG21.Precondition.ACCESSIBILITY_ACTIVE),
                "click must require accessibility");

        CelineToolCortexG21.ExecutionResult unknown = cortex.executeTyped(new CelineToolIntent("shell.exec", "", ""));
        check(unknown.status == CelineToolCortexG21.ExecutionStatus.TOOL_NOT_ALLOWLISTED, "unknown tool not rejected");
        check("TOOL_NOT_ALLOWLISTED".equals(unknown.errorCode), "unknown tool error code wrong");

        CelineToolCortexG21.ExecutionResult missingApp = cortex.executeTyped(new CelineToolIntent("app.open", "", ""));
        check(missingApp.status == CelineToolCortexG21.ExecutionStatus.INVALID_ARGUMENT, "missing app target not rejected");

        CelineToolCortexG21.ExecutionResult screenBlocked = cortex.executeTyped(new CelineToolIntent("screen.read", "", ""));
        check(screenBlocked.status == CelineToolCortexG21.ExecutionStatus.PRECONDITION_FAILED, "inactive accessibility not blocked");
        check("ACCESSIBILITY_INACTIVE".equals(screenBlocked.errorCode), "accessibility failure code wrong");

        backend.accessibility = true;
        CelineToolCortexG21.ExecutionResult screen = cortex.executeTyped(new CelineToolIntent("screen.read", "", ""));
        check(screen.success(), "screen read should succeed");
        check(screen.observedResult.contains("Inbox"), "screen observation lost");

        CelineToolCortexG21.ExecutionResult notificationsBlocked = cortex.executeTyped(new CelineToolIntent("notifications.recent", "", ""));
        check(notificationsBlocked.status == CelineToolCortexG21.ExecutionStatus.PRECONDITION_FAILED,
                "inactive notification listener not blocked");
        backend.notifications = true;
        backend.notificationRows.add("mail: Test — Hallo");
        CelineToolCortexG21.ExecutionResult notifications = cortex.executeTyped(new CelineToolIntent("notifications.recent", "", ""));
        check(notifications.success() && notifications.observedResult.contains("mail:"), "notification observation missing");

        backend.openApp = false;
        CelineToolCortexG21.ExecutionResult notFound = cortex.executeTyped(new CelineToolIntent("app.open", "YouTube", ""));
        check(notFound.status == CelineToolCortexG21.ExecutionStatus.NOT_FOUND, "missing app must be NOT_FOUND");
        backend.openApp = true;
        check(cortex.executeTyped(new CelineToolIntent("app.open", "YouTube", "")).success(), "app.open success lost");

        backend.click = false;
        CelineToolCortexG21.ExecutionResult clickRejected = cortex.executeTyped(new CelineToolIntent("ui.click_text", "Senden", ""));
        check(clickRejected.status == CelineToolCortexG21.ExecutionStatus.ACTION_REJECTED, "failed click must be explicit failure");
        backend.click = true;
        check(cortex.executeTyped(new CelineToolIntent("ui.click_text", "Senden", "")).success(), "click success lost");

        backend.setText = true;
        check(cortex.executeTyped(new CelineToolIntent("ui.set_text", "", "Hallo")).success(), "set_text success lost");
        CelineToolCortexG21.ExecutionResult invalidTap = cortex.executeTyped(new CelineToolIntent("ui.tap", "abc", "10"));
        check(invalidTap.status == CelineToolCortexG21.ExecutionStatus.INVALID_ARGUMENT, "invalid coordinates not rejected");

        backend.throwStatus = true;
        CelineToolCortexG21.ExecutionResult backendError = cortex.executeTyped(new CelineToolIntent("device.status", "", ""));
        check(backendError.status == CelineToolCortexG21.ExecutionStatus.BACKEND_ERROR, "backend exception not surfaced");
        backend.throwStatus = false;

        CelineToolResult legacy = cortex.execute(new CelineToolIntent("shell.exec", "", ""));
        check(!legacy.success && "TOOL_NOT_ALLOWLISTED".equals(legacy.errorCode), "brain registry compatibility failure");

        cortex.executeTyped(new CelineToolIntent("settings.accessibility", "", ""));
        check(backend.settingsOpened == 1, "settings adapter not invoked");
        System.out.println("celine-g2-typed-tool-cortex PASS");
    }
}
'''

    with tempfile.TemporaryDirectory(prefix="celine-g2-tools-") as td:
        temp = Path(td)
        probe_file = temp / "CelineG21ToolProbe.java"
        classes = temp / "classes"
        classes.mkdir()
        probe_file.write_text(probe, encoding="utf-8")
        compile_run = subprocess.run(
            ["javac", "-encoding", "UTF-8", "-d", str(classes), str(BRAIN), str(CORTEX), str(probe_file)],
            cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        )
        if compile_run.returncode != 0:
            print(compile_run.stdout)
            fail("G2.1 pure-Java compile failed")
        run = subprocess.run(
            ["java", "-cp", str(classes), "de.yahya.ai.CelineG21ToolProbe"],
            cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        )
        print(run.stdout.strip())
        if run.returncode != 0 or "celine-g2-typed-tool-cortex PASS" not in run.stdout:
            fail("G2.1 deterministic tool probe failed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
