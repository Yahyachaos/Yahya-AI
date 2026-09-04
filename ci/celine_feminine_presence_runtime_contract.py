#!/usr/bin/env python3
from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/de/yahya/ai/CelineNaturalBodyMotionV73.java"
OWNER = ROOT / "app/src/main/java/de/yahya/ai/CelineProductionPresenceV80.java"
APPLICATION = ROOT / "app/src/main/java/de/yahya/ai/YahyaApplication.java"
BUILD = ROOT / "app/build.gradle"
MANIFEST = ROOT / "ci/celine_v73_blender_motion_manifest.json"

source = SOURCE.read_text(encoding="utf-8")
owner = OWNER.read_text(encoding="utf-8")
application = APPLICATION.read_text(encoding="utf-8")
build = BUILD.read_text(encoding="utf-8")
manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))

version_match = re.search(r"versionCode\s+(\d+)", build)
if not version_match or int(version_match.group(1)) < 74:
    raise SystemExit("v74+ versionCode gate missing")

bindings = set(re.findall(
    r'feminineBone\(view, asset, transforms, "([^"]+)", "([^"]+)"\)', source
))
expected = {
    ("hips", "Hips"),
    ("leftShoulder", "LeftShoulder"),
    ("rightShoulder", "RightShoulder"),
}
if bindings != expected:
    raise SystemExit(f"v73 ownership/base mapping mismatch: {sorted(bindings)}")

required_source = {
    "HOME_HIPS_PITCH = -2.0f": "Blender-selected pelvis pitch",
    "HOME_HIPS_YAW = -3.5f": "Blender-selected pelvis yaw",
    "HOME_HIPS_ROLL = 6.0f": "Blender-selected pelvis roll",
    "HOME_LEFT_SHOULDER_PITCH = -1.2f": "left shoulder pitch",
    "HOME_LEFT_SHOULDER_ROLL = -0.7f": "left shoulder roll",
    "HOME_RIGHT_SHOULDER_PITCH = -0.6f": "right shoulder pitch",
    "HOME_RIGHT_SHOULDER_ROLL = 0.5f": "right shoulder roll",
    "LOOP_DURATION_NANOS = 4_000_000_000L": "exact four-second loop",
    "HIPS_PITCH_AMPLITUDE = 0.10f": "bounded pelvis breathing pitch",
    "HIPS_YAW_AMPLITUDE = 0.12f": "bounded pelvis weight-shift yaw",
    "HIPS_ROLL_AMPLITUDE = 0.18f": "bounded pelvis weight-shift roll",
    "LEFT_SHOULDER_PITCH_WAVE = -0.10f": "bounded left shoulder breath",
    "LEFT_SHOULDER_PITCH_SECOND = -0.03f": "asymmetric left second harmonic",
    "LEFT_SHOULDER_ROLL_AMPLITUDE = -0.05f": "bounded left shoulder roll",
    "RIGHT_SHOULDER_PITCH_WAVE = -0.08f": "bounded right shoulder breath",
    "RIGHT_SHOULDER_PITCH_SECOND = 0.02f": "asymmetric right second harmonic",
    "RIGHT_SHOULDER_ROLL_AMPLITUDE = 0.04f": "bounded right shoulder roll",
    "elapsed % LOOP_DURATION_NANOS": "continuous exact-period phase",
    "loopStartNanos = 0L": "HOME/CALL seam restart at exact base",
    "if (base == null) return null;": "fail-closed v44 base requirement",
    "CelineCallUpperBodyPresenceV55.isCallStage(view)": "HOME-only/CALL handoff",
    "transforms.setTransform(leftShoulder.instance, leftShoulder.base)": "left shoulder rollback",
    "transforms.setTransform(rightShoulder.instance, rightShoulder.base)": "right shoulder rollback",
    "CelineSkinningProbe": "synthetic fixture capability check",
    "disabled = true": "automatic failure disable",
    "animator.updateBoneMatrices()": "skinned-mesh matrix update",
    "V73-100": "production bind evidence",
    "V73-110": "HOME posture evidence",
    "V73-120": "CALL handoff evidence",
    "V73-198": "frame failure evidence",
    "V73-199": "initialization failure evidence",
}
for needle, purpose in required_source.items():
    if needle not in source:
        raise SystemExit(f"missing {purpose}: {needle}")

for lifecycle in ("install", "onPaused", "onDestroyed"):
    if f"CelineNaturalBodyMotionV73.{lifecycle}(activity" in application:
        raise SystemExit(f"v73 posture writer still competes with v80: {lifecycle}")
    if f"CelineFemininePresenceV72.{lifecycle}(activity" in application:
        raise SystemExit(f"v72 static writer still installed beside v73: {lifecycle}")

# The sole v80 owner preserves the accepted v73 HOME posture through homeProcedural,
# which fades that same envelope during walking/bed activity so those layers do not fight.
for token in (
    "homeProcedural * (-2.0f + 0.10f * second)",
    "homeProcedural * (-3.5f + 0.12f * wave)",
    "homeProcedural * (6.0f + 0.18f * wave)",
    "homeProcedural * (-1.2f - 0.10f * wave - 0.03f * second)",
    "homeProcedural * (-0.6f - 0.08f * wave + 0.02f * second)",
):
    if token not in owner:
        raise SystemExit(f"v80 central owner does not preserve accepted v73 posture: {token}")
if "CelineProductionPresenceV80.install(activity, decor)" not in application:
    raise SystemExit("v80 central posture owner is not installed")

for forbidden_joint in (
    "LeftArm", "RightArm", "LeftForeArm", "RightForeArm",
    "LeftUpLeg", "RightUpLeg", "LeftLeg", "RightLeg", "LeftFoot", "RightFoot",
    "neck", "Head", "Spine", "Spine01", "Spine02",
):
    if any(entity == forbidden_joint for _, entity in bindings):
        raise SystemExit(f"v73 unexpectedly owns protected joint: {forbidden_joint}")

for forbidden_geometry in (
    "addView(", "removeView(", "setLayoutParams(", "setTranslation",
    "requestLayout(", "scrollTo(", "setLensProjection(", "lookAt(",
):
    if forbidden_geometry in source:
        raise SystemExit(f"v73 unexpectedly changes geometry/camera: {forbidden_geometry}")

if manifest["selected_candidate"] != "v73-bounded-four-second-home-loop":
    raise SystemExit("unexpected Blender motion candidate selection")
if manifest["seam_proof"]["result"] != "PASS_EXACT_FRAME_1_FRAME_97_SEAM":
    raise SystemExit("v73 exact Blender loop seam proof missing")
if manifest["manual_review"] != "PASS_FRONT_RIGHT_BACK_NO_DEFORMATION":
    raise SystemExit("v73 Blender deformation review gate missing")
if manifest["animated_joints"] != ["Hips", "LeftShoulder", "RightShoulder"]:
    raise SystemExit("v73 Blender manifest ownership mismatch")
if manifest["canonical_source_sha256"] != "0c9fa09f898fbc8c0503be252c8fec1ee815a3a4990422e5c302e3113d7c1b55":
    raise SystemExit("Blender manifest canonical source mismatch")
print("v73 Blender-selected HOME posture is preserved inside the sole v80 production owner: PASS")
