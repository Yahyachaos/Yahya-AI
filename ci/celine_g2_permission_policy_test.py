#!/usr/bin/env python3
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/de/yahya/ai"
SOURCES = [
    JAVA / "CelineBrain.java",
    JAVA / "CelineToolCortexG21.java",
    JAVA / "CelinePermissionPolicyG22.java",
    JAVA / "CelinePermissionedToolRegistryG22.java",
]

HARNESS = r'''package de.yahya.ai;

import java.util.Collections;
import java.util.List;

public final class CelineG22Harness {
    private static final class FakeBackend implements CelineToolCortexG21.Backend {
        public String deviceStatus() { return "OK"; }
        public boolean accessibilityActive() { return true; }
        public boolean notificationListenerActive() { return true; }
        public List<String> recentNotifications() { return Collections.singletonList("n"); }
        public String screenSummary() { return "screen"; }
        public boolean goHome() { return true; }
        public boolean goBack() { return true; }
        public boolean openRecents() { return true; }
        public boolean openApp(String query) { return !query.isEmpty(); }
        public boolean clickText(String text) { return !text.isEmpty(); }
        public boolean setText(String text) { return true; }
        public boolean tap(float x, float y) { return x >= 0 && y >= 0; }
        public void openAccessibilitySettings() {}
        public void openNotificationSettings() {}
        public void openAllFilesSettings() {}
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        long now = 1_800_000_000_000L;
        CelineToolCortexG21 cortex = new CelineToolCortexG21(new FakeBackend());
        CelinePermissionPolicyG22 policy = new CelinePermissionPolicyG22();
        CelinePermissionedToolRegistryG22 guarded = new CelinePermissionedToolRegistryG22(cortex, policy);

        require(guarded.availableTools().size() == 13, "tool allowlist changed");

        CelineToolResult l0 = guarded.execute(new CelineToolIntent("device.status", "", ""));
        require(l0.success, "L0 read-only should be allowed");

        CelineToolResult l1 = guarded.execute(new CelineToolIntent("app.open", "YouTube", ""));
        require(l1.success, "L1 reversible local should be allowed");

        CelineToolIntent l2Intent = new CelineToolIntent("ui.click_text", "Senden", "");
        CelineToolResult l2Blocked = guarded.execute(l2Intent);
        require(!l2Blocked.success && l2Blocked.errorCode.contains("L2_EXPLICIT_USER_INTENT_REQUIRED"),
                "L2 must fail closed without explicit user intent");

        CelineToolResult l2Allowed = guarded.executeAuthorized(
                l2Intent, CelinePermissionPolicyG22.AuthorizationContext.explicitUserIntent(now));
        require(l2Allowed.success, "L2 with explicit user intent should pass");

        CelinePermissionDecision l2MissingTarget = policy.evaluate(
                new CelineToolIntent("ui.click_text", "", ""),
                CelineRiskClass.L2_EXTERNAL_STATE_CHANGE,
                CelinePermissionPolicyG22.AuthorizationContext.explicitUserIntent(now));
        require(l2MissingTarget.state == CelinePermissionDecision.State.DENY,
                "L2 without explicit target must deny");

        CelineToolResult unknown = guarded.execute(new CelineToolIntent("shell.exec", "rm", ""));
        require(!unknown.success && "PERMISSION_UNKNOWN_TOOL".equals(unknown.errorCode),
                "unknown tools must deny by default");

        CelineToolIntent l3 = new CelineToolIntent("account.delete", "account:42", "confirm-delete");
        CelinePermissionDecision l3NoConfirm = policy.evaluate(
                l3, CelineRiskClass.L3_HIGH_IMPACT,
                CelinePermissionPolicyG22.AuthorizationContext.explicitUserIntent(now));
        require(l3NoConfirm.state == CelinePermissionDecision.State.REQUIRE_CONFIRMATION,
                "L3 requires fresh confirmation");

        CelinePermissionDecision l3Fresh = policy.evaluate(
                l3, CelineRiskClass.L3_HIGH_IMPACT,
                CelinePermissionPolicyG22.AuthorizationContext.confirmed(l3, now - 1_000L, now));
        require(l3Fresh.state == CelinePermissionDecision.State.ALLOW,
                "fresh exact L3 confirmation should allow");

        CelinePermissionDecision l3Stale = policy.evaluate(
                l3, CelineRiskClass.L3_HIGH_IMPACT,
                CelinePermissionPolicyG22.AuthorizationContext.confirmed(
                        l3, now - CelinePermissionPolicyG22.FRESH_CONFIRMATION_WINDOW_MS - 1L, now));
        require(l3Stale.state == CelinePermissionDecision.State.REQUIRE_CONFIRMATION,
                "stale L3 confirmation must not allow");

        CelineToolIntent otherTarget = new CelineToolIntent("account.delete", "account:99", "confirm-delete");
        CelinePermissionDecision l3WrongScope = policy.evaluate(
                otherTarget, CelineRiskClass.L3_HIGH_IMPACT,
                CelinePermissionPolicyG22.AuthorizationContext.confirmed(l3, now - 1_000L, now));
        require(l3WrongScope.state == CelinePermissionDecision.State.DENY,
                "L3 confirmation must be exact-scope bound");

        System.out.println("celine-g2-permission-policy-contract PASS");
    }
}
'''


def main() -> int:
    for source in SOURCES:
        if not source.is_file():
            raise SystemExit(f"missing source: {source}")
    if shutil.which("javac") is None or shutil.which("java") is None:
        raise SystemExit("javac/java required for G2.2 contract")

    with tempfile.TemporaryDirectory(prefix="celine-g22-") as tmp:
        tmp_path = Path(tmp)
        harness_dir = tmp_path / "de/yahya/ai"
        harness_dir.mkdir(parents=True)
        harness = harness_dir / "CelineG22Harness.java"
        harness.write_text(HARNESS, encoding="utf-8")
        classes = tmp_path / "classes"
        classes.mkdir()
        subprocess.run(
            ["javac", "-encoding", "UTF-8", "-d", str(classes),
             *[str(source) for source in SOURCES], str(harness)],
            check=True,
        )
        result = subprocess.run(
            ["java", "-cp", str(classes), "de.yahya.ai.CelineG22Harness"],
            check=True,
            text=True,
            capture_output=True,
        )
        print(result.stdout.strip())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
