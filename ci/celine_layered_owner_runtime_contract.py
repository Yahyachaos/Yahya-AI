#!/usr/bin/env python3
"""Fail-closed structural contract for v80 Block 4 central production ownership."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL v80 layered owner contract: {message}")


owner = read("app/src/main/java/de/yahya/ai/CelineProductionPresenceV80.java")
view = read("app/src/main/java/de/yahya/ai/Celine3DView.java")
app = read("app/src/main/java/de/yahya/ai/YahyaApplication.java")
home_camera = read("app/src/main/java/de/yahya/ai/CelineVideoChatV44.java")
lab_driver = read("app/src/main/java/de/yahya/ai/CelineAvatarLabPoseDriverV79.java")
lab_activity = read("app/src/main/java/de/yahya/ai/CelineAvatarLabActivity.java")
lab_capture = read("app/src/main/java/de/yahya/ai/CelineAvatarLabCaptureActivity.java")
morph = read("app/src/main/java/de/yahya/ai/CelineMorphRuntimeV62.java")
planner = read("app/src/main/java/de/yahya/ai/CelineFacialMotionPlanner.java")
tts = read("app/src/main/java/de/yahya/ai/LocalNeuralTtsEngine.java")
controller = read("app/src/main/java/de/yahya/ai/CelineAvatarController.java")
gradle = read("app/build.gradle")
room = read("app/src/main/java/de/yahya/ai/CelineRoomEnvironmentV80.java")

for token in (
    "enum Stage { AUTO, HOME, CALL }",
    "enum LayerView { COMBINED, BASE_ONLY, BREATHING_POSTURE, CONVERSATION, GAZE_HEAD }",
    "applyBaseLayer(home, call)",
    "applyPostureLayer(t, home, call)",
    "applyConversationLayer(frameTimeNanos, home, call)",
    "applyGazeLayer(frameTimeNanos, deltaSeconds, t, home, call)",
    "CelineMorphRuntimeV62.onFrame(view, frameTimeNanos)",
    "oneTransaction=true oneSkinUpdate=true",
    "CelineSkinningProbe",
    '" probe=" + probeModel',
):
    require(token in owner, f"owner token missing: {token}")

body = owner[owner.index("void applyBody(long frameTimeNanos)") : owner.index("private void updateHomeFrame")]
require(body.count("openLocalTransformTransaction()") == 1,
        "production frame must open exactly one transform transaction")
require(body.count("animator.updateBoneMatrices();") == 1,
        "production frame must update skin matrices exactly once")
on_frame = owner[owner.index("static void onFrame") : owner.index("static void setDiagnostic")]
require(on_frame.index("applyBody") < on_frame.index("CelineMorphRuntimeV62.onFrame"),
        "face/viseme layer must run after body/gaze layers")

require("CelineProductionPresenceV80.onFrame(this, frameTimeNanos);" in view,
        "renderer does not delegate its live frame to the central owner")
live_pose = view[view.index("private void updateLivePose") : view.index("private static float clamp")]
require("setTransform(" not in live_pose and "applyBone(" not in live_pose,
        "renderer retains an independent live bone writer")
require("CelineMorphRuntimeV62.onFrame(Celine3DView.this" not in gradle,
        "Gradle still injects an independent face frame callback")

legacy_owners = (
    "CelineSingleBonePresenceV54",
    "CelineCallUpperBodyPresenceV55",
    "CelineArmHandPresenceV79",
    "CelineSeatedCallV70",
    "CelineNaturalBodyMotionV73",
)
for legacy in legacy_owners:
    require(f"{legacy}.install" not in app, f"legacy owner still installed: {legacy}")
    require(f"{legacy}.onPaused" not in app, f"legacy owner still owns pause: {legacy}")
    require(f"{legacy}.onDestroyed" not in app, f"legacy owner still owns destroy: {legacy}")
require("CelineProductionPresenceV80.install(activity, decor);" in app,
        "central owner is not installed in MainActivity lifecycle")

require("setTransform(" not in home_camera,
        "v44 HOME presentation/camera layer still writes root or bones")
require("CelineProductionPresenceV80.homeFrame(view)" in home_camera,
        "v44 camera does not consume the central owner's HOME motion snapshot")

for token in (
    "PRODUCTION_HOME",
    "PRODUCTION_CALL",
    "LAYER_BASE",
    "LAYER_BREATHING_POSTURE",
    "LAYER_CONVERSATION",
    "LAYER_GAZE_HEAD",
    "if (isProductionMode(current) && !headOverride) return;",
):
    require(token in lab_driver, f"Avatar Lab driver token missing: {token}")
for token in ("Produktion HOME", "Produktion CALL", "Layer Basis", "Layer Atmung",
              "Layer Körper/Arme", "Layer Blick/Kopf"):
    require(token in lab_activity, f"Avatar Lab control missing: {token}")
for token in ("production_home", "production_call", "layer_base", "layer_breathing",
              "layer_conversation", "layer_gaze"):
    require(token in lab_capture, f"Avatar Lab capture mode missing: {token}")
require("disableRendererLivePoseForDeterministicCapture" not in lab_capture,
        "Avatar Lab still nulls renderer bones instead of using owner arbitration")

require("manager.setMorphWeights(instance, output, 0);" in morph,
        "guarded final face output is no longer applied")
require("SpeechLipSyncV77 lipSync = new SpeechLipSyncV77();" in tts
        and "lipSync.analyze(samples, pos, count, sampleRate)" in tts,
        "v77 is no longer driven from the AudioTrack PCM playback loop")
require("threeD.setViseme(cue);" in controller,
        "PCM viseme cue no longer reaches the production 3D controller")
require("CelineMorphRuntimeV62.onViseme(this, cue)" in gradle,
        "generated 3D viseme hook no longer reaches the guarded morph owner")
planner_order = [planner.index(name) for name in (
    "applyBlink(nowMs", "applySpeech(safeState", "applyExpression(nowMs", "applyGaze(nowMs"
)]
require(planner_order == sorted(planner_order),
        "blink/expression/viseme/gaze planner order changed unexpectedly")

# The sole v80 owner deliberately refined the accepted seated CALL leg pose in
# 566f106 (thigh pitch -88°, inward roll ±10°) while preserving root/knee/foot
# and the accepted CALL arm envelope. Keep this aggregate contract aligned with
# that canonical owner instead of the superseded -82° v70 literal.
for token in ("CALL_ROOT_DOWN = -0.30f", "CALL_ROOT_FORWARD = 0.12f",
              "call * -88.0f", "call * 92.0f", "call * -8.0f",
              "call * (30.5f", "call * (-30.5f"):
    require(token in owner, f"accepted CALL seat/arm constant missing: {token}")

for token in (
    "MAX_SOCIAL_GAZE_X = 0.12f",
    "MAX_SOCIAL_GAZE_Y = 0.08f",
    "updateSocialGaze(frameTimeNanos, deltaSeconds, state)",
    "deterministicSigned(gazeShiftSerial",
    "constantBobbing=false independentWriter=false",
    "CelineProductionPresenceV80.socialLookX(view)",
    "CelineProductionPresenceV80.socialLookY(view)",
):
    require(token in owner or token in morph, f"Block-6 social-presence token missing: {token}")
require("CelineHumanPresenceV48.install" not in app,
        "historical independent gaze scheduler was reactivated")

require("never writes Celine's root" in room,
        "Block-3 room ownership boundary is no longer explicit")

print("PASS v80 layered owner structural contract: one production frame owner, ordered layers, "
      "shared HOME/CALL/Avatar-Lab production path, guarded face/PCM layer retained")
