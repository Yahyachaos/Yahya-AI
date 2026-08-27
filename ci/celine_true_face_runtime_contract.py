#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
PLANNER = ROOT / "app/src/main/java/de/yahya/ai/CelineFacialMotionPlanner.java"
VIEW = ROOT / "app/src/main/java/de/yahya/ai/Celine3DView.java"
RUNTIME = ROOT / "app/src/main/java/de/yahya/ai/CelineMorphRuntimeV62.java"
BUILD = ROOT / "app/build.gradle"
PROD = ROOT / "app/src/main/assets/models/celine.glb"
GENERATOR = ROOT / "ci/celine_production_morph_v65.py"
V75_GENERATOR = ROOT / "ci/celine_character_refresh_v75.py"
V76_GENERATOR = ROOT / "ci/celine_facial_rig_v76.py"
V76_VALIDATOR = ROOT / "ci/celine_facial_rig_validation_v76.py"

text = PLANNER.read_text(encoding="utf-8")
view = VIEW.read_text(encoding="utf-8")
runtime = RUNTIME.read_text(encoding="utf-8")
build = BUILD.read_text(encoding="utf-8")
generator = GENERATOR.read_text(encoding="utf-8")
v75_generator = V75_GENERATOR.read_text(encoding="utf-8")
v76_generator = V76_GENERATOR.read_text(encoding="utf-8")
v76_validator = V76_VALIDATOR.read_text(encoding="utf-8")

version_match = re.search(r"versionCode\s+(\d+)", build)
if not version_match or int(version_match.group(1)) < 76:
    raise SystemExit("v76+ final-geometry facial-rig version gate missing")

required = {
    "TARGET_COUNT = 15": "exact v76 target count",
    "BILABIAL_PRESS = 6": "bilabial speech channel",
    "LABIODENTAL = 7": "labiodental speech channel",
    "SMILE = 8": "smile expression channel",
    "THOUGHTFUL = 9": "thoughtful expression channel",
    "SURPRISED = 10": "surprised expression channel",
    "GAZE_LEFT = 11": "horizontal gaze channel",
    "GAZE_DOWN = 14": "vertical gaze channel",
    "MAX_BLINK = 0.94f": "bounded blink amplitude",
    "MAX_JAW = 0.66f": "bounded jaw amplitude",
    "MAX_VOWEL = 0.58f": "bounded vowel amplitude",
    "MAX_LIP_CONTACT = 0.72f": "bounded lip-contact amplitude",
    "MAX_EXPRESSION = 0.34f": "bounded expression amplitude",
    "MAX_GAZE = 0.32f": "bounded gaze amplitude",
    "case SPEAKING": "state-aware speech motion",
    "case LISTENING": "state-aware listening motion",
    "case THINKING": "state-aware thinking motion",
    "case IDLE": "state-aware idle motion",
    "blinkLeadLeft": "natural bounded blink asymmetry",
}
for needle, purpose in required.items():
    if needle not in text:
        raise SystemExit(f"missing {purpose}: {needle}")

# The checked-in view remains a neutral hook. The generated build source activates morph playback.
if not re.search(r"public\s+void\s+setViseme\s*\([^)]*\)\s*\{\s*\}", view):
    raise SystemExit("source setViseme hook no longer matches the guarded build transform")

build_required = {
    "generateCelineProductionMorphV65": "reproducible legacy candidate generation",
    "validateCelineProductionMorphV65": "legacy structural candidate validation",
    "generateCelineCharacterRefreshV75": "deterministic v75 character refresh",
    "validateCelineCharacterRefreshV75": "v75 structural and silhouette validation",
    "generateCelineFacialRigV76": "final-geometry facial-rig generation",
    "validateCelineFacialRigV76": "append-only v76 facial-rig validation",
    "assets.srcDir celineV79GeneratedAssetsDir": "v79 generated production asset packaging over the validated v76 facial rig",
    "CelineMorphRuntimeV62.onViseme(this, cue)": "generated runtime viseme activation",
    "6e507144afa22f0534be0419884932a0c6aaa16b8b2013580013ffe5056bb146": "exact validated v65 candidate hash",
    "e47c7105d14e740dfa89b26153bcb4e99ad40242583f210c4175dac4181ef14f": "exact validated v75 geometry hash",
    "46828b88dc7917def64881c6bc348b790a2bab445401e0ba3fac240327253923": "exact validated v76 candidate hash",
}
for needle, purpose in build_required.items():
    if needle not in build:
        raise SystemExit(f"missing {purpose}: {needle}")

generator_required = {
    "reproducible_generated_production_asset": "generated v65 production policy",
    "--expected-sha256": "v65 candidate hash fail-closed gate",
    "source POSITION accessor untouched": "legacy neutral identity contract",
}
for needle, purpose in generator_required.items():
    if needle not in generator:
        raise SystemExit(f"missing {purpose}: {needle}")

v75_generator_required = {
    "deterministic_master_reference_refresh": "v75 deterministic visual policy",
    "skin weights, indices, animations and facial morph deltas are intentionally untouched": "v75 protected runtime boundary",
    "SOURCE_SHA256": "canonical source hash gate",
    "V65_SHA256": "validated v65 intermediate gate",
}
for needle, purpose in v75_generator_required.items():
    if needle not in v75_generator:
        raise SystemExit(f"missing {purpose}: {needle}")

v76_generator_required = {
    "final_v75_geometry_rebind_append_only_fail_closed": "v76 final-geometry rebind policy",
    "5275eb5873a821b13a38d64d07be076feba98744d363cbcb9d5ed1895e7b366b": "exact final v75 material input",
    "Yahya-AI Celine v76 final-geometry facial rig": "v76 asset identity",
    "--expected-sha256": "v76 candidate hash fail-closed gate",
    "BilabialPress": "bilabial target generation",
    "Labiodental": "labiodental target generation",
    "GazeDown": "complete gaze target generation",
}
for needle, purpose in v76_generator_required.items():
    if needle not in v76_generator:
        raise SystemExit(f"missing {purpose}: {needle}")

v76_validator_required = {
    "v76_append_only_facial_contract_validation": "v76 append-only validation policy",
    "neutral_geometry_materials_rig_preserved": "neutral identity preservation",
    "BlinkBoth composition error": "bilateral blink invariant",
    "targets are not symmetric opposites": "gaze opposition invariant",
}
for needle, purpose in v76_validator_required.items():
    if needle not in v76_validator:
        raise SystemExit(f"missing {purpose}: {needle}")

runtime_required = {
    "CelineFacialMotionPlanner": "single pure facial planner",
    "count == TARGET_COUNT": "exact fifteen-target probe",
    "state.enabled = false": "automatic morph rollback",
    "setMorphWeights": "Filament morph playback",
    "neutrale v75 Baseline bleibt aktiv": "diagnostic fallback baseline",
}
for needle, purpose in runtime_required.items():
    if needle not in runtime:
        raise SystemExit(f"missing {purpose}: {needle}")

# Production stays reproducibly generated; no opaque binary may be committed at this path.
if PROD.exists():
    raise SystemExit("opaque production celine.glb unexpectedly committed instead of generated")

print("final-geometry Celine v76 facial runtime contract: PASS")
