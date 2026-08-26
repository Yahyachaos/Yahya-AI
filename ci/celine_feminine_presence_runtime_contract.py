#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/de/yahya/ai/CelineFemininePresenceV71.java"
APPLICATION = ROOT / "app/src/main/java/de/yahya/ai/YahyaApplication.java"
BUILD = ROOT / "app/build.gradle"

source = SOURCE.read_text(encoding="utf-8")
application = APPLICATION.read_text(encoding="utf-8")
build = BUILD.read_text(encoding="utf-8")

if "versionCode 71" not in build:
    raise SystemExit("v71 versionCode gate missing")

bound_joints = set(re.findall(
    r'feminineBone\(view, asset, transforms, "[^"]+", "([^"]+)"\)', source
))
if bound_joints != {"Hips"}:
    raise SystemExit(f"v71 ownership must remain Hips-only, actual={sorted(bound_joints)}")

required_source = {
    "HOME_HIPS_PITCH = -1.2f": "bounded feminine pelvis pitch",
    "HOME_HIPS_YAW = -2.0f": "bounded feminine pelvis yaw",
    "HOME_HIPS_ROLL = 4.0f": "bounded feminine pelvis roll",
    "CelineCallUpperBodyPresenceV55.isCallStage(view)": "HOME-only/CALL handoff detection",
    "CelineSkinningProbe": "synthetic fixture capability check",
    "disabled = true": "automatic failure disable",
    "restore(true)": "exact CALL handoff rollback",
    "animator.updateBoneMatrices()": "skinned-mesh matrix update",
    "V71-100": "production Hips bind evidence",
    "V71-110": "HOME posture evidence",
    "V71-120": "CALL handoff evidence",
    "V71-198": "frame failure evidence",
    "V71-199": "initialization failure evidence",
}
for needle, purpose in required_source.items():
    if needle not in source:
        raise SystemExit(f"missing {purpose}: {needle}")

for lifecycle in ("install", "onPaused", "onDestroyed"):
    if f"CelineFemininePresenceV71.{lifecycle}(activity" not in application:
        raise SystemExit(f"YahyaApplication missing CelineFemininePresenceV71.{lifecycle} lifecycle wiring")

# v58's production regression came from shoulders. They stay explicitly banned in v71, along
# with every other joint owner and every external layout/camera surface.
for forbidden_joint in (
    "LeftShoulder", "RightShoulder",
    "LeftArm", "RightArm", "LeftForeArm", "RightForeArm",
    "LeftUpLeg", "RightUpLeg", "LeftLeg", "RightLeg", "LeftFoot", "RightFoot",
    "neck", "Head", "Spine", "Spine01", "Spine02",
):
    if forbidden_joint in bound_joints:
        raise SystemExit(f"v71 unexpectedly owns protected joint: {forbidden_joint}")

for forbidden_geometry in (
    "addView(", "removeView(", "setLayoutParams(", "setTranslation",
    "requestLayout(", "scrollTo(", "setLensProjection(", "lookAt(",
):
    if forbidden_geometry in source:
        raise SystemExit(f"v71 unexpectedly changes geometry/camera: {forbidden_geometry}")

if "v58 shoulder path stays quarantined" not in source:
    raise SystemExit("v71 source must document preserved v58 shoulder quarantine")

print("v71 feminine HOME Hips-only ownership contract: PASS")
