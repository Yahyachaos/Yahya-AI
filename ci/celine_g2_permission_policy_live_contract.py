#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEVICE = ROOT / "app/src/main/java/de/yahya/ai/DeviceBridge.java"
POLICY = ROOT / "app/src/main/java/de/yahya/ai/CelinePermissionPolicyG22.java"
GUARDED = ROOT / "app/src/main/java/de/yahya/ai/CelinePermissionedToolRegistryG22.java"


def require(text: str, token: str, label: str) -> None:
    if token not in text:
        raise SystemExit(f"missing {label}: {token}")


def main() -> int:
    device = DEVICE.read_text(encoding="utf-8")
    policy = POLICY.read_text(encoding="utf-8")
    guarded = GUARDED.read_text(encoding="utf-8")

    for token, label in (
        ("private final CelinePermissionPolicyG22 permissionPolicy", "central policy field"),
        ("private final CelinePermissionedToolRegistryG22 permissionedTools", "permissioned registry field"),
        ("new CelinePermissionPolicyG22()", "policy construction"),
        ("new CelinePermissionedToolRegistryG22(toolCortex, permissionPolicy)", "registry binding"),
        ("public CelineToolRegistry toolRegistry() { return permissionedTools; }", "brain registry protected path"),
        ("public CelineToolCortexG21 typedToolCortex()", "raw diagnostics path retained"),
    ):
        require(device, token, label)

    if "public CelineToolRegistry toolRegistry() { return toolCortex; }" in device:
        raise SystemExit("DeviceBridge brain registry bypasses G2.2 permission owner")

    for token, label in (
        ("FRESH_CONFIRMATION_WINDOW_MS", "fresh confirmation window"),
        ("L2_EXPLICIT_TARGET_REQUIRED", "L2 target gate"),
        ("L2_EXPLICIT_USER_INTENT_REQUIRED", "L2 explicit intent gate"),
        ("L3_FRESH_CONFIRMATION_REQUIRED", "L3 fresh confirmation gate"),
        ("L3_CONFIRMATION_SCOPE_MISMATCH", "L3 exact-scope binding"),
        ("State.DENY", "deny state"),
        ("State.REQUIRE_CONFIRMATION", "confirmation state"),
    ):
        require(policy, token, label)

    for token, label in (
        ("PERMISSION_UNKNOWN_TOOL", "deny-by-default unknown tool"),
        ("PERMISSION_DENIED:", "explicit deny failure"),
        ("PERMISSION_CONFIRMATION_REQUIRED:", "confirmation failure"),
        ("return cortex.execute(intent);", "allowed typed execution"),
    ):
        require(guarded, token, label)

    for name, text in (("policy", policy), ("permissioned registry", guarded)):
        if "import android." in text:
            raise SystemExit(f"{name} must remain Android-free")
        for forbidden in ("Runtime.getRuntime().exec", "ProcessBuilder", "Class.forName", "getDeclaredMethod"):
            if forbidden in text:
                raise SystemExit(f"{name} contains forbidden dynamic execution path: {forbidden}")

    print("celine-g2-permission-policy-live-contract PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
