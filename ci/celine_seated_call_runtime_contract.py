#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/de/yahya/ai/CelineSeatedCallV70.java"
APPLICATION = ROOT / "app/src/main/java/de/yahya/ai/YahyaApplication.java"
BUILD = ROOT / "app/build.gradle"

source = SOURCE.read_text(encoding="utf-8")
application = APPLICATION.read_text(encoding="utf-8")
build = BUILD.read_text(encoding="utf-8")

if "versionCode 70" not in build:
    raise SystemExit("v70 versionCode gate missing")

expected_joints = {
    "Hips",
    "LeftUpLeg", "RightUpLeg",
    "LeftLeg", "RightLeg",
    "LeftFoot", "RightFoot",
}
bound_joints = set(re.findall(
    r'seatedBone\(view, asset, transforms, "[^"]+", "([^"]+)"\)', source
))
if bound_joints != expected_joints:
    raise SystemExit(
        f"v70 seated joint ownership changed: "
        f"expected={sorted(expected_joints)} actual={sorted(bound_joints)}"
    )

required_source = {
    "ROOT_DOWN = -0.16f": "bounded seated root lowering",
    "ROOT_FORWARD = 0.08f": "bounded seated root depth",
    "HIPS_PITCH = -1.0f": "proven v56-scale pelvis adjustment",
    "UPPER_LEG_PITCH = -70.0f": "seated thigh angle",
    "LOWER_LEG_PITCH = 90.0f": "seated knee angle",
    "FOOT_PITCH = -15.0f": "seated foot angle",
    "CALL_LENS_MM = 58.0": "seated upper-body framing",
    "CelineSkinningProbe": "synthetic fixture capability check",
    "CelineCallUpperBodyPresenceV55.isCallStage(view)": "CALL-only activation",
    "disabled = true": "automatic failure disable",
    "restoreForHome": "exact HOME rollback",
    "animator.updateBoneMatrices()": "skinned-mesh matrix update",
    "V70-100": "production lower-body bind evidence",
    "V70-110": "CALL entry evidence",
    "V70-120": "active seated matrices evidence",
    "V70-130": "HOME restoration evidence",
    "V70-198": "frame failure evidence",
    "V70-199": "initialization failure evidence",
}
for needle, purpose in required_source.items():
    if needle not in source:
        raise SystemExit(f"missing {purpose}: {needle}")

for lifecycle in ("install", "onPaused", "onDestroyed"):
    if f"CelineSeatedCallV70.{lifecycle}(activity" not in application:
        raise SystemExit(f"YahyaApplication missing CelineSeatedCallV70.{lifecycle} lifecycle wiring")

# The quarantined deformation path must not return.
for forbidden_joint in (
    "LeftShoulder", "RightShoulder",
    "LeftArm", "RightArm", "LeftForeArm", "RightForeArm",
    "neck", "Head", "Spine", "Spine01", "Spine02",
):
    if forbidden_joint in bound_joints:
        raise SystemExit(f"v70 unexpectedly owns protected joint: {forbidden_joint}")

# The seated owner may never add views or reposition/resize the HOME/CALL stage, composer,
# videochat button or any ancestor.
for forbidden_geometry in (
    "addView(", "removeView(", "setLayoutParams(", "setTranslation",
    "requestLayout(", "scrollTo(",
):
    if forbidden_geometry in source:
        raise SystemExit(f"v70 unexpectedly changes external UI geometry: {forbidden_geometry}")

print("v70 CALL-only seated lower-body contract: PASS")
