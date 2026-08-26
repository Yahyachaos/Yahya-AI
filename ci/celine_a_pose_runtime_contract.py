#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/de/yahya/ai/CelineArmPoseV69.java"
APPLICATION = ROOT / "app/src/main/java/de/yahya/ai/YahyaApplication.java"
BUILD = ROOT / "app/build.gradle"

source = SOURCE.read_text(encoding="utf-8")
application = APPLICATION.read_text(encoding="utf-8")
build = BUILD.read_text(encoding="utf-8")

if "versionCode 71" not in build:
    raise SystemExit("v71 versionCode gate missing while preserving v69 arm owner")

expected_joints = {"LeftArm", "RightArm", "LeftForeArm", "RightForeArm"}
bound_joints = set(re.findall(r'armBone\(asset, transforms, "([^"]+)"\)', source))
if bound_joints != expected_joints:
    raise SystemExit(f"v69 joint ownership changed: expected={sorted(expected_joints)} actual={sorted(bound_joints)}")

required_source = {
    "HOME_LEFT_ARM_ROLL = 29.5f": "bounded HOME left-arm release",
    "HOME_RIGHT_ARM_ROLL = -29.5f": "bounded HOME right-arm release",
    "HOME_FOREARM_PITCH = -6.0f": "bounded HOME elbow ease",
    "CALL_LEFT_ARM_ROLL = 30.5f": "bounded CALL left-arm release",
    "CALL_RIGHT_ARM_ROLL = -30.5f": "bounded CALL right-arm release",
    "CALL_FOREARM_PITCH = -14.0f": "bounded CALL elbow ease",
    "CelineSkinningProbe": "synthetic fixture capability check",
    "disabled = true": "automatic failure disable",
    "restoreAll()": "exact base-transform rollback",
    "animator.updateBoneMatrices()": "skinned-mesh matrix update",
    "V69-100": "production four-joint bind evidence",
    "V69-110": "HOME pose evidence",
    "V69-120": "CALL pose evidence",
    "V69-130": "HOME-return evidence",
    "V69-198": "frame failure evidence",
    "V69-199": "initialization failure evidence",
}
for needle, purpose in required_source.items():
    if needle not in source:
        raise SystemExit(f"missing {purpose}: {needle}")

for lifecycle in ("install", "onPaused", "onDestroyed"):
    if f"CelineArmPoseV69.{lifecycle}(activity" not in application:
        raise SystemExit(f"YahyaApplication missing CelineArmPoseV69.{lifecycle} lifecycle wiring")

# This owner is posture-only. It must never write UI/videochat geometry or camera framing.
for forbidden in ("LayoutParams", "setTranslation", "scrollTo(", "setLensProjection", "lookAt("):
    if forbidden in source:
        raise SystemExit(f"v69 arm owner unexpectedly touches geometry/camera: {forbidden}")

print("v69 four-joint A-pose removal contract preserved in v71: PASS")
