#!/usr/bin/env python3
"""Execute issue #112 JSON command vectors against the real pure-Java persona prototype.

Unlike the older fixed Java harness, this test compiles assertions *from the JSON
payloads themselves*. Editing a vector's input or expected result therefore changes
what the test executes; checking the mere presence of IDs is not enough.
No Android, network, persistence store, Room or runtime wiring is involved.
"""

from __future__ import annotations

import json
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "ci/prototypes/CelinePersonaMode.java"
VECTORS = ROOT / "ci/evidence/CELINE_ADULT_PERSONA_ACCEPTANCE_CASES.json"
SCHEMA = "celine-adult-persona-acceptance/v3"
CHANNELS = {
    "direct_user": "DIRECT_USER",
    "model_output": "MODEL_OUTPUT",
    "tool_output": "TOOL_OUTPUT",
    "other": "OTHER",
}
SOURCES = {
    "none": "NONE",
    "direct_user_command": "DIRECT_USER_COMMAND",
    "persisted_state": "PERSISTED_STATE",
}
REQUIRED_IDS = {
    "activate-default", "activate-case-insensitive", "activate-intensity-1",
    "activate-intensity-2", "activate-intensity-3", "deactivate-codeword",
    "deactivate-normal-mode", "deactivate-removes-prompt-next-request",
    "no-substring-activation", "no-quoted-activation", "no-model-output-activation",
    "no-tool-output-activation", "non-user-input-preserves-active-state",
    "reject-high-intensity", "reject-malformed-intensity",
}


def quoted(value: str) -> str:
    if not isinstance(value, str):
        raise ValueError("expected a string in persona acceptance JSON")
    return json.dumps(value, ensure_ascii=True)


def boolean(value: object) -> str:
    if type(value) is not bool:
        raise ValueError(f"expected a JSON boolean, got {value!r}")
    return "true" if value else "false"


def check(lines: list[str], expression: str, label: str) -> None:
    lines.append("check(" + expression + ", " + quoted(label) + ");")


def generate(cases: list[dict]) -> str:
    lines = [
        "import de.yahya.ai.CelinePersonaMode;",
        "public final class CelinePersonaVectorExecutionContractTest {",
        "  private static void check(boolean ok, String label) {",
        "    if (!ok) throw new AssertionError(label);",
        "  }",
        "  public static void main(String[] args) {",
    ]
    tested = 0
    for case in cases:
        if "operation" in case or "input" not in case:
            continue
        case_id = case["id"]
        if not isinstance(case_id, str):
            raise ValueError("case id must be a string")
        channel = CHANNELS[case.get("channel", "direct_user")]
        initial = case.get("initial", {})
        expected = case["expected"]
        if not isinstance(initial, dict) or not isinstance(expected, dict):
            raise ValueError(f"{case_id}: invalid initial/expected object")
        active = initial.get("active", False)
        intensity = initial.get("intensity", 0)
        if type(active) is not bool or type(intensity) is not int:
            raise ValueError(f"{case_id}: invalid initial active/intensity types")
        lines.extend(["{", "CelinePersonaMode mode = new CelinePersonaMode();"])
        if active:
            if not 1 <= intensity <= 3:
                raise ValueError(f"{case_id}: invalid initial active intensity")
            if initial.get("activation_source", "direct_user_command") == "persisted_state":
                check(lines, "mode.restorePersistedState(true, " + str(intensity) + ")", case_id + ": persisted setup")
            else:
                if initial.get("activation_source", "direct_user_command") != "direct_user_command":
                    raise ValueError(f"{case_id}: invalid activation source setup")
                lines.append(
                    "mode.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "
                    + quoted("Nutte " + str(intensity)) + ");"
                )
        elif intensity != 0:
            raise ValueError(f"{case_id}: inactive initial intensity must be zero")
        lines.append(
            "CelinePersonaMode.CommandResult result = mode.apply("
            + "CelinePersonaMode.InputChannel." + channel + ", "
            + quoted(case["input"]) + ");"
        )
        properties = {
            "active": "result.active()",
            "command_consumed": "result.commandConsumed()",
            "invalid_intensity": "result.invalidIntensity()",
        }
        for key, accessor in properties.items():
            if key in expected:
                check(lines, accessor + " == " + boolean(expected[key]), case_id + ": " + key)
        if "intensity" in expected:
            value = expected["intensity"]
            if type(value) is not int or not 0 <= value <= 3:
                raise ValueError(f"{case_id}: invalid expected intensity")
            check(lines, "result.intensity() == " + str(value), case_id + ": intensity")
        if "activation_source" in expected:
            source = SOURCES[expected["activation_source"]]
            check(
                lines,
                "result.activationSource() == CelinePersonaMode.ActivationSource." + source,
                case_id + ": activation source",
            )
        if "prompt_contribution" in expected:
            check(
                lines,
                "mode.promptContribution().equals(" + quoted(expected["prompt_contribution"]) + ")",
                case_id + ": prompt contribution",
            )
        lines.append("}")
        tested += 1

    if tested != len(REQUIRED_IDS):
        raise ValueError(f"expected {len(REQUIRED_IDS)} executable command vectors, got {tested}")
    lines.extend([
        'System.out.println("CelinePersona JSON command vectors: PASS (' + str(tested) + ')");',
        "}",
        "}",
    ])
    return "\n".join(lines) + "\n"


def main() -> int:
    data = json.loads(VECTORS.read_text(encoding="utf-8"))
    if data.get("schema") != SCHEMA or data.get("issue") != 112:
        raise ValueError("persona acceptance schema or issue drift")
    cases = data.get("cases")
    if not isinstance(cases, list) or not all(isinstance(c, dict) for c in cases):
        raise ValueError("persona acceptance cases must be objects")
    ids = [case["id"] for case in cases]
    if len(ids) != len(set(ids)):
        raise ValueError("duplicate persona acceptance IDs")
    executable = {case["id"] for case in cases if "input" in case and "operation" not in case}
    if executable != REQUIRED_IDS:
        raise ValueError(
            f"persona executable vector set drift; missing={sorted(REQUIRED_IDS-executable)}, "
            f"unexpected={sorted(executable-REQUIRED_IDS)}"
        )
    with tempfile.TemporaryDirectory(prefix="celine-persona-vector-execution-") as tmp:
        directory = Path(tmp)
        source = directory / "CelinePersonaVectorExecutionContractTest.java"
        output = directory / "classes"
        output.mkdir()
        source.write_text(generate(cases), encoding="utf-8")
        subprocess.run(["javac", "-d", str(output), str(SOURCE), str(source)],
                       cwd=ROOT, check=True)
        subprocess.run(["java", "-cp", str(output),
                        "CelinePersonaVectorExecutionContractTest"], cwd=ROOT, check=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
