#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
PLANNER = ROOT / "app/src/main/java/de/yahya/ai/CelineFacialMotionPlanner.java"
VIEW = ROOT / "app/src/main/java/de/yahya/ai/Celine3DView.java"
PROD = ROOT / "app/src/main/assets/models/celine.glb"

text = PLANNER.read_text(encoding="utf-8")
view = VIEW.read_text(encoding="utf-8")

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

# Until a renderable morph-enabled candidate has passed identity/HOME/CALL/lifecycle gates,
# the production renderer hook must remain dormant. This prevents accidental activation on
# the current body-only private production GLB.
if not re.search(r"public\s+void\s+setViseme\s*\([^)]*\)\s*\{\s*\}", view):
    raise SystemExit("setViseme is no longer dormant before candidate gates passed")

# The repository intentionally does not gain or replace the production GLB in this scaffold.
if PROD.exists():
    raise SystemExit("production celine.glb unexpectedly present in repository")

print("true-face runtime scaffold contract: PASS")
