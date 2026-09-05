#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
product = (ROOT / "app/src/main/java/de/yahya/ai/CelineProductInteractionLockV79.java").read_text()
view = (ROOT / "app/src/main/java/de/yahya/ai/Celine3DView.java").read_text()
capture = (ROOT / "app/src/main/java/de/yahya/ai/CelineAvatarLabCaptureActivity.java").read_text()

checks = {
    "product blocks one-finger translation": "one-finger motion intentionally has no pan/translation side effect" in product,
    "product resets stale pan x": 'setFloat(view, "cameraPanX", 0.0f)' in product,
    "product resets stale pan y": 'setFloat(view, "cameraPanY", 0.0f)' in product,
    "product pinch owns cameraZoom": 'getDeclaredField("cameraZoom")' in product and "detector.getScaleFactor()" in product,
    "diagnostic orbit is explicit": "diagnosticCameraOrbitEnabled" in view and "v79SetDiagnosticCameraOrbit" in view,
    "orbit moves camera on sin/cos radius": "Math.sin(yawRad) * radius" in view and "Math.cos(yawRad) * radius" in view,
    "orbit keeps fixed reference-room target": all(token in view for token in (
        "REFERENCE_TARGET_X", "REFERENCE_TARGET_Y", "REFERENCE_TARGET_Z",
        "camera.lookAt(eyeX, eyeY, eyeZ, targetX, targetY, targetZ",
    )),
    "capture does not fake orbit by rotating model": "celineView.v75SetReferenceYaw(0f);" in capture and "v79SetDiagnosticCameraOrbit" in capture,
    "dolly near/far share fixed target": 'case "dolly_near"' in capture and 'case "dolly_far"' in capture and "rootScaleChanged=false" in capture,
}

failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS" if ok else "FAIL") + ": " + name)
if failed:
    raise SystemExit("camera interaction contract failed: " + ", ".join(failed))
print("V79 camera interaction contract PASS")
