#!/usr/bin/env python3
"""Execute the four persona prompt-adapter acceptance JSON cases in pure Java.

The harness derives its intensity, deactivation command, and expected outputs
from the canonical acceptance payload. No Android/runtime/Room input is touched.
"""
from __future__ import annotations

import json
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
ACCEPTANCE = ROOT / "ci/evidence/CELINE_ADULT_PERSONA_PROMPT_ADAPTER_ACCEPTANCE.json"
MODE = ROOT / "ci/prototypes/CelinePersonaMode.java"
ADAPTER = ROOT / "ci/prototypes/CelinePersonaPromptAdapter.java"
REQUIRED = {
    "inactive-normal-prompt-transparent": {"same_object", "same_content", "persona_contribution"},
    "active-style-layer-appended": {"base_prefix_unchanged", "suffix_equals_persona_contribution", "base_input_unmodified"},
    "deactivate-next-prompt-transparent": {"next_compose_same_object", "next_compose_same_content", "persona_contribution"},
    "empty-base-active-contribution-only": {"output_equals_persona_contribution"},
}


def q(value: str) -> str:
    if type(value) is not str:
        raise ValueError("non-string prompt test value")
    return json.dumps(value, ensure_ascii=True)


def b(value: bool) -> str:
    if type(value) is not bool:
        raise ValueError("non-boolean prompt test expectation")
    return "true" if value else "false"


def append(lines: list[str], name: str, condition: str, expected: bool) -> None:
    lines.append("check((" + condition + ") == " + b(expected) + ", " + q(name) + ");")


def activate(lines: list[str], case: dict) -> None:
    initial = case.get("initial", {})
    if initial.get("active") is not True or type(initial.get("intensity")) is not int:
        raise ValueError("invalid active prompt case initial state")
    intensity = initial["intensity"]
    if not 1 <= intensity <= 3:
        raise ValueError("prompt intensity out of range")
    lines.append("mode.apply(CelinePersonaMode.InputChannel.DIRECT_USER, " + q(f"Nutte {intensity}") + ");")


def generate(cases: list[dict]) -> str:
    lines = ["import de.yahya.ai.CelinePersonaMode;",
             "import de.yahya.ai.CelinePersonaPromptAdapter;",
             "public final class CelinePersonaPromptVectorTest {",
             "  static void check(boolean ok, String id) { if (!ok) throw new AssertionError(id); }",
             "  public static void main(String[] args) {"]
    for case in cases:
        id = case["id"]
        expected = case["expected"]
        if id not in REQUIRED or set(expected) != REQUIRED[id]:
            raise ValueError(f"unexpected prompt case/schema: {id}")
        lines += ["{", "CelinePersonaMode mode = new CelinePersonaMode();",
                  "String normal = new String(\"NORMAL-CELINE-PROMPT\\nmemory=context-owned\\nconversation=unchanged\");",
                  "String before = new String(normal);"]
        if id == "inactive-normal-prompt-transparent":
            if case.get("initial"):
                raise ValueError("inactive test unexpectedly starts active")
            lines += ["String result = CelinePersonaPromptAdapter.compose(normal, mode);"]
            append(lines, id + ": same object", "result == normal", expected["same_object"])
            append(lines, id + ": same content", "result.equals(before)", expected["same_content"])
            lines += ["check(mode.promptContribution().equals(" + q(expected["persona_contribution"]) + "), " + q(id + ": persona contribution") + ");"]
        elif id == "active-style-layer-appended":
            activate(lines, case)
            lines += ["String extra = mode.promptContribution();",
                      "String result = CelinePersonaPromptAdapter.compose(normal, mode);"]
            append(lines, id + ": prefix", 'result.startsWith(normal + "\\n\\n")', expected["base_prefix_unchanged"])
            append(lines, id + ": suffix", "result.endsWith(extra) && !extra.isEmpty()", expected["suffix_equals_persona_contribution"])
            append(lines, id + ": base unchanged", "normal.equals(before)", expected["base_input_unmodified"])
        elif id == "deactivate-next-prompt-transparent":
            activate(lines, case)
            lines.append("check(CelinePersonaPromptAdapter.compose(normal, mode) != normal, " + q(id + ": activate first") + ");")
            lines.append("mode.apply(CelinePersonaMode.InputChannel.DIRECT_USER, " + q(case["command"]) + ");")
            lines.append("String after = CelinePersonaPromptAdapter.compose(normal, mode);")
            append(lines, id + ": same object", "after == normal", expected["next_compose_same_object"])
            append(lines, id + ": same content", "after.equals(before)", expected["next_compose_same_content"])
            lines.append("check(mode.promptContribution().equals(" + q(expected["persona_contribution"]) + "), " + q(id + ": persona contribution") + ");")
        elif id == "empty-base-active-contribution-only":
            activate(lines, case)
            lines.append("String result = CelinePersonaPromptAdapter.compose(\"\", mode);")
            append(lines, id + ": only contribution", "result.equals(mode.promptContribution()) && !result.isEmpty()", expected["output_equals_persona_contribution"])
        lines.append("}")
    lines += ['System.out.println("CelinePersona JSON prompt vectors: PASS (' + str(len(cases)) + ')");', "}", "}"]
    return "\n".join(lines) + "\n"


def main() -> int:
    data = json.loads(ACCEPTANCE.read_text(encoding="utf-8"))
    if data.get("schema") != "celine-adult-persona-prompt-adapter-acceptance/v1" or data.get("issue") != 112:
        raise ValueError("prompt acceptance schema or issue drift")
    cases = data["cases"]
    if len(cases) != len(REQUIRED) or len({c["id"] for c in cases}) != len(REQUIRED):
        raise ValueError("prompt vector set drift")
    with tempfile.TemporaryDirectory(prefix="celine-persona-prompt-vectors-") as tmp:
        root = Path(tmp)
        classes = root / "classes"
        classes.mkdir()
        java = root / "CelinePersonaPromptVectorTest.java"
        java.write_text(generate(cases), encoding="utf-8")
        subprocess.run(["javac", "-d", str(classes), str(MODE), str(ADAPTER), str(java)], check=True, cwd=ROOT)
        subprocess.run(["java", "-cp", str(classes), "CelinePersonaPromptVectorTest"], check=True, cwd=ROOT)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
