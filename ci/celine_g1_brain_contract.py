#!/usr/bin/env python3
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
brain = root / "app/src/main/java/de/yahya/ai/CelineBrain.java"
assert brain.is_file(), "missing CelineBrain.java"
source = brain.read_text(encoding="utf-8")

required = [
    "public final class CelineBrain",
    "interface CelineMemory",
    "interface CelineWorkingState",
    "interface CelineGoalGraph",
    "interface CelineContextBroker",
    "interface CelineToolRegistry",
    "interface CelinePermissionPolicy",
    "interface CelineReasoningProvider",
    "interface CelineVerifier",
    "interface CelineLearningEngine",
    "interface CelineAffectState",
    "interface CelineResourcePolicy",
    "CelineMemorySlice retrieve(CelineBrainRequest request, int maxItems)",
    "UPSERT",
    "SUPERSEDE",
    "FORGET",
    "OBSERVED",
    "EXPLICIT",
    "INFERRED",
    "UNKNOWN",
    "L0_READ_ONLY",
    "L1_REVERSIBLE_LOCAL",
    "L2_EXTERNAL_STATE_CHANGE",
    "L3_HIGH_IMPACT",
    "DETERMINISTIC",
    "LOCAL_FAST",
    "LOCAL_DEEP",
    "EXTERNAL_OPTIONAL",
    "UNAVAILABLE",
]
for token in required:
    assert token in source, f"missing G1.1 contract token: {token}"

for forbidden in [
    "import android.",
    "android.content.",
    "SharedPreferences",
    "HttpURLConnection",
    "api.openai.com",
    "gpt-",
]:
    assert forbidden not in source, f"provider/UI coupling leaked into CelineBrain contract: {forbidden}"

# Compile the contract independently from Android/UI/runtime owners. This proves the
# central brain boundary is plain Java and can survive future provider/model swaps.
with tempfile.TemporaryDirectory(prefix="celine-g1-brain-") as out:
    completed = subprocess.run(
        ["javac", "-source", "8", "-target", "8", "-d", out, str(brain)],
        cwd=root,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    )
    if completed.returncode != 0:
        raise AssertionError("CelineBrain standalone javac failed:\n" + completed.stdout)

print("celine-g1-brain-contract PASS")
