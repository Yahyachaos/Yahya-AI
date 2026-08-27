#!/usr/bin/env python3
from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/de/yahya/ai/CelineArmHandPresenceV79.java"
APPLICATION = ROOT / "app/src/main/java/de/yahya/ai/YahyaApplication.java"
BUILD = ROOT / "app/build.gradle"
MANIFEST = ROOT / "ci/celine_v74_blender_arm_hand_manifest.json"

source = SOURCE.read_text(encoding="utf-8")
application = APPLICATION.read_text(encoding="utf-8")
build = BUILD.read_text(encoding="utf-8")
manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))

version_match = re.search(r"versionCode\s+(\d+)", build)
if not version_match or int(version_match.group(1)) < 79:
    raise SystemExit("v79+ versionCode gate missing for current arm/hand owner")

expected_joints = {
    "LeftArm", "RightArm", "LeftForeArm", "RightForeArm", "LeftHand", "RightHand"
}
bound_joints = set(re.findall(r'bone\(asset, "([^"]+)"\)', source))
if bound_joints != expected_joints:
    raise SystemExit(f"v79 joint ownership changed: expected={sorted(expected_joints)} actual={sorted(bound_joints)}")

required_source = {
    "HOME_LEFT_ARM_ROLL = 29.5f": "proven HOME left-arm base",
    "HOME_RIGHT_ARM_ROLL = -29.5f": "proven HOME right-arm base",
    "HOME_FOREARM_PITCH = -6.0f": "proven HOME forearm base",
    "CALL_LEFT_ARM_ROLL = 30.5f": "proven CALL left-arm base",
    "CALL_RIGHT_ARM_ROLL = -30.5f": "proven CALL right-arm base",
    "CALL_FOREARM_PITCH = -14.0f": "proven CALL forearm base",
    "HOME_LOOP_NANOS = 5_200_000_000L": "bounded HOME loop",
    "CALL_LOOP_NANOS = 6_100_000_000L": "bounded CALL loop",
    "elapsed % duration": "continuous exact-period phase",
    "CelineSkinningProbe": "synthetic fixture capability check",
    "disabled = true": "automatic failure disable",
    "restoreAll()": "exact base-transform rollback",
    "animator.updateBoneMatrices()": "skinned-mesh matrix update",
    "V79-400": "six-joint bind evidence",
    "V79-410": "HOME presence evidence",
    "V79-420": "CALL presence evidence",
    "V79-428": "frame failure evidence",
    "V79-429": "initialization failure evidence",
    "noFingerBones=true": "no-finger safety evidence",
}
for needle, purpose in required_source.items():
    if needle not in source:
        raise SystemExit(f"missing {purpose}: {needle}")

for lifecycle in ("install", "onPaused", "onDestroyed"):
    if f"CelineArmHandPresenceV79.{lifecycle}(activity" not in application:
        raise SystemExit(f"YahyaApplication missing CelineArmHandPresenceV79.{lifecycle} lifecycle wiring")
    for stale in ("CelineArmPoseV69", "CelineArmHandPresenceV74"):
        if f"{stale}.{lifecycle}(activity" in application:
            raise SystemExit(f"stale arm writer still installed beside v79: {stale}.{lifecycle}")

# This owner is arm/forearm/hand motion only. It must never write UI/videochat geometry,
# camera framing, root/hip/leg transforms, shoulder ownership, or unsupported finger bones.
for forbidden in (
    "LayoutParams", "setTranslation", "scrollTo(", "setLensProjection", "lookAt(",
    'bone(asset, "Hips")', 'bone(asset, "LeftShoulder")', 'bone(asset, "RightShoulder")',
    'bone(asset, "Root")', 'bone(asset, "LeftUpLeg")', 'bone(asset, "RightUpLeg")',
    'bone(asset, "LeftLeg")', 'bone(asset, "RightLeg")', 'bone(asset, "LeftFoot")',
    'bone(asset, "RightFoot")', 'bone(asset, "LeftHandIndex")', 'bone(asset, "RightHandIndex")',
):
    if forbidden in source:
        raise SystemExit(f"v79 arm/hand owner exceeds six-joint safety boundary: {forbidden}")

# v74's Blender review remains the accepted static pose/seam provenance underneath v79.
if manifest["finger_bones"] != [] or manifest["finger_motion"] != "UNSUPPORTED_NO_FINGER_BONES":
    raise SystemExit("arm/hand baseline must not claim unsupported finger animation")
if manifest["selected_candidate"] != "b-arm-wrist-presence":
    raise SystemExit("unexpected bounded arm/hand Blender baseline candidate")
if manifest["seam_proof"]["result"] != "PASS_EXACT_FRAME_1_FRAME_97_SEAM":
    raise SystemExit("accepted Blender loop seam proof missing")
if manifest["manual_review"] != "PASS_FRONT_RIGHT_BACK_NO_DEFORMATION":
    raise SystemExit("accepted Blender deformation review missing")

print("v79 six-joint arm/hand owner preserves bounded safety, exact restore, and HOME/CALL presence: PASS")
