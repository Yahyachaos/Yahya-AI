#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEVICE = ROOT / "app/src/main/java/de/yahya/ai/DeviceBridge.java"
BACKEND = ROOT / "app/src/main/java/de/yahya/ai/CelineAndroidToolBackend.java"
CORTEX = ROOT / "app/src/main/java/de/yahya/ai/CelineToolCortexG21.java"


def require(text: str, token: str, label: str) -> None:
    if token not in text:
        raise SystemExit(f"missing {label}: {token}")


def main() -> int:
    device = DEVICE.read_text(encoding="utf-8")
    backend = BACKEND.read_text(encoding="utf-8")
    cortex = CORTEX.read_text(encoding="utf-8")

    for token, label in (
        ("private final CelineToolCortexG21 toolCortex", "bound cortex field"),
        ("new CelineToolCortexG21(new CelineAndroidToolBackend(this))", "Android backend binding"),
        ("public CelineToolRegistry toolRegistry()", "brain registry access"),
        ("public CelineToolCortexG21 typedToolCortex()", "typed registry access"),
    ):
        require(device, token, label)

    for token in (
        "device.status()",
        "PrincessAccessibilityService.isRunning()",
        "PrincessNotificationService.isRunning()",
        "PrincessNotificationService.recent()",
        "PrincessAccessibilityService.screenSummary()",
        "PrincessAccessibilityService.goHome()",
        "PrincessAccessibilityService.goBack()",
        "PrincessAccessibilityService.openRecents()",
        "device.openApp(query)",
        "PrincessAccessibilityService.clickText(text)",
        "PrincessAccessibilityService.setText(text)",
        "PrincessAccessibilityService.tap(x, y)",
        "device.openAccessibilitySettings()",
        "device.openNotificationSettings()",
        "device.openAllFilesSettings()",
    ):
        require(backend, token, "backend mapping")

    for token in (
        "LinkedHashMap<String, TypedToolDescriptor> allowlist",
        "TOOL_NOT_ALLOWLISTED",
        "PRECONDITION_FAILED",
        "INVALID_ARGUMENT",
        "ACTION_REJECTED",
        "BACKEND_ERROR",
        "L2_EXTERNAL_STATE_CHANGE",
        "ACCESSIBILITY_ACTIVE",
        "NOTIFICATION_LISTENER_ACTIVE",
    ):
        require(cortex, token, "typed cortex contract")

    if "import android." in cortex:
        raise SystemExit("pure Tool Cortex must not import Android")
    for forbidden in ("Class.forName", "getDeclaredMethod", "Runtime.getRuntime().exec", "ProcessBuilder"):
        if forbidden in cortex:
            raise SystemExit(f"dynamic/unallowlisted execution path found: {forbidden}")

    print("celine-g2-typed-tool-live-contract PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
