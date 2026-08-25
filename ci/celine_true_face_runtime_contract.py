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

text = PLANNER.read_text(encoding="utf-8")
view = VIEW.read_text(encoding="utf-8")
runtime = RUNTIME.read_text(encoding="utf-8")
build = BUILD.read_text(encoding="utf-8")
generator = GENERATOR.read_text(encoding="utf-8")

required = {
    "TARGET_COUNT = 6": "validated six-target mapping",
    "MAX_BLINK = 0.92f": "bounded blink amplitude",
    "MAX_JAW = 0.56f": "bounded jaw amplitude",
    "MAX_VOWEL = 0.42f": "bounded vowel amplitude",
    "MAX_MICRO = 0.055f": "bounded microexpression amplitude",
    "case SPEAKING": "state-aware speech motion",
    "case LISTENING": "state-aware listening motion",
    "case THINKING": "state-aware thinking motion",
    "case IDLE": "state-aware idle motion",
    "blinkLeadLeft": "asynchronous blink phase",
}
for needle, purpose in required.items():
    if needle not in text:
        raise SystemExit(f"missing {purpose}: {needle}")

# v65 keeps the source hook transformable and activates it only in the generated build source.
if not re.search(r"public\s+void\s+setViseme\s*\([^)]*\)\s*\{\s*\}", view):
    raise SystemExit("source setViseme hook no longer matches the guarded build transform")
build_required = {
    "versionCode 67": "v67 keyboard regression release version gate",
    "generateCelineProductionMorphV65": "reproducible candidate generation",
    "validateCelineProductionMorphV65": "structural candidate validation",
    "assets.srcDir celineV65GeneratedAssetsDir": "generated production asset packaging",
    "CelineMorphRuntimeV62.onViseme(this, cue)": "generated runtime viseme activation",
    "6e507144afa22f0534be0419884932a0c6aaa16b8b2013580013ffe5056bb146": "exact validated candidate hash",
}
for needle, purpose in build_required.items():
    if needle not in build:
        raise SystemExit(f"missing {purpose}: {needle}")

generator_required = {
    "reproducible_generated_production_asset": "generated production policy",
    "--expected-sha256": "candidate hash fail-closed gate",
    "source POSITION accessor untouched": "neutral identity contract",
}
for needle, purpose in generator_required.items():
    if needle not in generator:
        raise SystemExit(f"missing {purpose}: {needle}")

runtime_required = {
    "count >= TARGET_COUNT": "six-target probe",
    "state.enabled = false": "automatic morph rollback",
    "setMorphWeights": "Filament morph playback",
    "v61/v59 Baseline bleibt aktiv": "diagnostic fallback baseline",
}
for needle, purpose in runtime_required.items():
    if needle not in runtime:
        raise SystemExit(f"missing {purpose}: {needle}")

# Production is generated from the immutable LFS source; no opaque binary is committed here.
if PROD.exists():
    raise SystemExit("opaque production celine.glb unexpectedly committed instead of generated")

print("true-face v66 packaged-source recovery contract preserved in v67: PASS")
