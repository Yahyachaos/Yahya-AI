#!/usr/bin/env python3
from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/de/yahya/ai/CelineFemininePresenceV72.java"
APPLICATION = ROOT / "app/src/main/java/de/yahya/ai/YahyaApplication.java"
BUILD = ROOT / "app/build.gradle"
MANIFEST = ROOT / "ci/celine_v72_blender_pose_manifest.json"

source = SOURCE.read_text(encoding="utf-8")
application = APPLICATION.read_text(encoding="utf-8")
build = BUILD.read_text(encoding="utf-8")
manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))

if "versionCode 72" not in build:
    raise SystemExit("v72 versionCode gate missing")

bindings = set(re.findall(
    r'feminineBone\(view, asset, transforms, "([^"]+)", "([^"]+)"\)', source
))
expected = {
    ("hips", "Hips"),
    ("leftShoulder", "LeftShoulder"),
    ("rightShoulder", "RightShoulder"),
}
if bindings != expected:
    raise SystemExit(f"v72 ownership/base mapping mismatch: {sorted(bindings)}")

required_source = {
    "HOME_HIPS_PITCH = -2.0f": "Blender-selected pelvis pitch",
    "HOME_HIPS_YAW = -3.5f": "Blender-selected pelvis yaw",
    "HOME_HIPS_ROLL = 6.0f": "Blender-selected pelvis roll",
    "HOME_LEFT_SHOULDER_PITCH = -1.2f": "left shoulder pitch",
    "HOME_LEFT_SHOULDER_ROLL = -0.7f": "left shoulder roll",
    "HOME_RIGHT_SHOULDER_PITCH = -0.6f": "right shoulder pitch",
    "HOME_RIGHT_SHOULDER_ROLL = 0.5f": "right shoulder roll",
    "if (base == null) return null;": "fail-closed v44 base requirement",
    "CelineCallUpperBodyPresenceV55.isCallStage(view)": "HOME-only/CALL handoff",
    "transforms.setTransform(leftShoulder.instance, leftShoulder.base)": "left shoulder rollback",
    "transforms.setTransform(rightShoulder.instance, rightShoulder.base)": "right shoulder rollback",
    "CelineSkinningProbe": "synthetic fixture capability check",
    "disabled = true": "automatic failure disable",
    "animator.updateBoneMatrices()": "skinned-mesh matrix update",
    "V72-100": "production bind evidence",
    "V72-110": "HOME posture evidence",
    "V72-120": "CALL handoff evidence",
    "V72-198": "frame failure evidence",
    "V72-199": "initialization failure evidence",
}
for needle, purpose in required_source.items():
    if needle not in source:
        raise SystemExit(f"missing {purpose}: {needle}")

for lifecycle in ("install", "onPaused", "onDestroyed"):
    if f"CelineFemininePresenceV72.{lifecycle}(activity" not in application:
        raise SystemExit(f"YahyaApplication missing v72 {lifecycle} wiring")
    if f"CelineFemininePresenceV71.{lifecycle}(activity" in application:
        raise SystemExit(f"v71 writer still installed beside v72: {lifecycle}")

for forbidden_joint in (
    "LeftArm", "RightArm", "LeftForeArm", "RightForeArm",
    "LeftUpLeg", "RightUpLeg", "LeftLeg", "RightLeg", "LeftFoot", "RightFoot",
    "neck", "Head", "Spine", "Spine01", "Spine02",
):
    if any(entity == forbidden_joint for _, entity in bindings):
        raise SystemExit(f"v72 unexpectedly owns protected joint: {forbidden_joint}")

for forbidden_geometry in (
    "addView(", "removeView(", "setLayoutParams(", "setTranslation",
    "requestLayout(", "scrollTo(", "setLensProjection(", "lookAt(",
):
    if forbidden_geometry in source:
        raise SystemExit(f"v72 unexpectedly changes geometry/camera: {forbidden_geometry}")

if manifest["selected_candidate"] != "f-minimal-runtime-candidate":
    raise SystemExit("unexpected Blender candidate selection")
if manifest["canonical_source_sha256"] != "0c9fa09f898fbc8c0503be252c8fec1ee815a3a4990422e5c302e3113d7c1b55":
    raise SystemExit("Blender manifest canonical source mismatch")
if manifest["manual_review"] != "PASS_FRONT_RIGHT_BACK_NO_DEFORMATION":
    raise SystemExit("Blender manual review gate missing")

print("v72 Blender-selected HOME Hips + v44-base shoulders contract: PASS")
