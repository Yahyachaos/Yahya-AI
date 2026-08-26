#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/de/yahya/ai/CelineArmHandPresenceV74.java"
APPLICATION = ROOT / "app/src/main/java/de/yahya/ai/YahyaApplication.java"
BUILD = ROOT / "app/build.gradle"

source = SOURCE.read_text(encoding="utf-8")
application = APPLICATION.read_text(encoding="utf-8")
build = BUILD.read_text(encoding="utf-8")

if "versionCode 74" not in build:
    raise SystemExit("v74 versionCode gate missing while preserving v69 arm owner")

expected_joints = {
    "LeftArm", "RightArm", "LeftForeArm", "RightForeArm", "LeftHand", "RightHand"
}
bound_joints = set(re.findall(r'armBone\(asset, transforms, "([^"]+)"\)', source))
if bound_joints != expected_joints:
    raise SystemExit(f"v74 joint ownership changed: expected={sorted(expected_joints)} actual={sorted(bound_joints)}")

required_source = {
    "HOME_LEFT_ARM_ROLL = 29.5f": "bounded HOME left-arm release",
    "HOME_RIGHT_ARM_ROLL = -29.5f": "bounded HOME right-arm release",
    "HOME_FOREARM_PITCH = -6.0f": "bounded HOME elbow ease",
    "CALL_LEFT_ARM_ROLL = 30.5f": "bounded CALL left-arm release",
    "CALL_RIGHT_ARM_ROLL = -30.5f": "bounded CALL right-arm release",
    "CALL_FOREARM_PITCH = -14.0f": "bounded CALL elbow ease",
    "LOOP_DURATION_NANOS = 4_000_000_000L": "exact four-second loop",
    "HOME_LEFT_ARM_PITCH_AMPLITUDE = 0.12f": "bounded left arm pitch motion",
    "HOME_LEFT_ARM_ROLL_AMPLITUDE = 0.20f": "bounded left arm roll motion",
    "HOME_RIGHT_ARM_PITCH_AMPLITUDE = -0.10f": "bounded right arm pitch motion",
    "HOME_RIGHT_ARM_ROLL_AMPLITUDE = -0.17f": "bounded right arm roll motion",
    "HOME_LEFT_HAND_PITCH_WAVE = 0.38f": "bounded left wrist wave",
    "HOME_LEFT_HAND_PITCH_SECOND = 0.08f": "left wrist second harmonic",
    "HOME_RIGHT_HAND_PITCH_WAVE = 0.34f": "bounded right wrist wave",
    "HOME_RIGHT_HAND_PITCH_SECOND = -0.06f": "right wrist second harmonic",
    "elapsed % LOOP_DURATION_NANOS": "continuous exact-period phase",
    "loopStartNanos = 0L": "exact HOME/CALL seam restart",
    "CelineSkinningProbe": "synthetic fixture capability check",
    "disabled = true": "automatic failure disable",
    "restoreAll()": "exact base-transform rollback",
    "animator.updateBoneMatrices()": "skinned-mesh matrix update",
    "V74-100": "production four-joint bind evidence",
    "V74-110": "HOME pose evidence",
    "V74-120": "CALL pose evidence",
    "V74-130": "HOME-return evidence",
    "V74-198": "frame failure evidence",
    "V74-199": "initialization failure evidence",
}
for needle, purpose in required_source.items():
    if needle not in source:
        raise SystemExit(f"missing {purpose}: {needle}")

for lifecycle in ("install", "onPaused", "onDestroyed"):
    if f"CelineArmHandPresenceV74.{lifecycle}(activity" not in application:
        raise SystemExit(f"YahyaApplication missing CelineArmHandPresenceV74.{lifecycle} lifecycle wiring")
    if f"CelineArmPoseV69.{lifecycle}(activity" in application:
        raise SystemExit(f"v69 writer still installed beside v74: {lifecycle}")

# This owner is posture-only. It must never write UI/videochat geometry or camera framing.
for forbidden in ("LayoutParams", "setTranslation", "scrollTo(", "setLensProjection", "lookAt("):
    if forbidden in source:
        raise SystemExit(f"v69 arm owner unexpectedly touches geometry/camera: {forbidden}")

print("v69 four-joint A-pose removal contract preserved in v73: PASS")
