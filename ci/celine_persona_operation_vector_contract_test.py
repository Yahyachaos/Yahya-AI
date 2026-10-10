#!/usr/bin/env python3
"""Execute all issue #112 non-command persona acceptance vectors against Java.

Compile assertions from the canonical JSON operation payloads. This is an isolated
prototype test: no Android, model provider, memory store, Room or app wiring.
"""
from __future__ import annotations

import json
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "ci/prototypes/CelinePersonaMode.java"
VECTORS = ROOT / "ci/evidence/CELINE_ADULT_PERSONA_ACCEPTANCE_CASES.json"
OPS = {
    "inactive-prompt-empty": "prompt_contribution",
    "active-prompt-bounded": "prompt_contribution",
    "prompt-varies-by-intensity": "prompt_contribution_compare",
    "persist-roundtrip-active": "snapshot_restore",
    "persist-roundtrip-inactive": "restore_persisted_state",
    "persist-corrupt-active-fails-closed": "restore_persisted_state",
    "persist-corrupt-inactive-fails-closed": "restore_persisted_state",
}
SOURCES = {
    "none": "NONE", "direct_user_command": "DIRECT_USER_COMMAND",
    "persisted_state": "PERSISTED_STATE",
}
PRESERVE = {
    "must_preserve_normal_identity": "normal identity",
    "must_preserve_permission_policy": "permission policy",
    "must_preserve_privacy_and_memory_policy": "privacy or memory boundaries",
    "must_preserve_factual_honesty": "factual honesty",
}


def literal(v: str) -> str:
    if type(v) is not str:
        raise ValueError(f"expected string, got {v!r}")
    return json.dumps(v, ensure_ascii=True)


def bool_lit(v: bool) -> str:
    if type(v) is not bool:
        raise ValueError(f"expected bool, got {v!r}")
    return "true" if v else "false"


def integer(v: int) -> str:
    if type(v) is not int:
        raise ValueError(f"expected int, got {v!r}")
    return str(v)


def check(lines: list[str], condition: str, label: str) -> None:
    lines.append(f"check({condition}, {literal(label)});")


def setup(lines: list[str], initial: dict) -> None:
    active = initial.get("active", False)
    level = initial.get("intensity", 0)
    if type(active) is not bool or type(level) is not int:
        raise ValueError("invalid initial state types")
    if active:
        if not 1 <= level <= 3:
            raise ValueError("invalid active intensity")
        origin = initial.get("activation_source", "direct_user_command")
        if origin == "persisted_state":
            check(lines, f"mode.restorePersistedState(true, {level})", "persisted setup")
        elif origin == "direct_user_command":
            lines.append("mode.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "
                         + literal(f"Nutte {level}") + ");")
        else:
            raise ValueError("invalid initial active provenance")
    elif level != 0:
        raise ValueError("inactive initial state must have intensity zero")


def state_checks(lines: list[str], expected: dict, label: str, state: str) -> None:
    fields = {
        "active": f"{state}.isActive()" if state == "mode" else f"{state}.active()",
        "intensity": f"{state}.intensity()",
        "activation_source": f"{state}.activationSource()",
    }
    for key, accessor in fields.items():
        if key not in expected:
            continue
        val = expected[key]
        if key == "active":
            expr = bool_lit(val)
        elif key == "intensity":
            expr = integer(val)
        else:
            expr = "CelinePersonaMode.ActivationSource." + SOURCES[val]
        check(lines, f"{accessor} == {expr}", f"{label}: {key}")


def generate(cases: list[dict]) -> str:
    lines = ["import de.yahya.ai.CelinePersonaMode;",
             "public final class CelinePersonaOperationVectorTest {",
             "private static void check(boolean ok, String label) {",
             "  if (!ok) throw new AssertionError(label);", "}",
             "public static void main(String[] args) {"]
    count = 0
    for case in cases:
        if "operation" not in case:
            continue
        case_id = case["id"]
        op = case["operation"]
        if OPS.get(case_id) != op:
            raise ValueError(f"unexpected operation vector {case_id}/{op}")
        expected = case["expected"]
        if not isinstance(expected, dict):
            raise ValueError(f"{case_id}: invalid expected object")
        lines += ["{", "CelinePersonaMode mode = new CelinePersonaMode();"]
        if op == "prompt_contribution":
            setup(lines, case.get("initial", {}))
            lines.append("String contribution = mode.promptContribution();")
            allowed = {"prompt_contribution", "prompt_contribution_nonempty", *PRESERVE}
            if set(expected) - allowed:
                raise ValueError(f"{case_id}: unsupported prompt expectations")
            if "prompt_contribution" in expected:
                check(lines, "contribution.equals(" + literal(expected["prompt_contribution"]) + ")", case_id + ": exact prompt")
            if "prompt_contribution_nonempty" in expected:
                check(lines, "!contribution.isEmpty() == " + bool_lit(expected["prompt_contribution_nonempty"]), case_id + ": nonempty")
            for key, required in PRESERVE.items():
                if key in expected:
                    check(lines, "contribution.contains(" + literal(required) + ") == " + bool_lit(expected[key]), case_id + ": " + key)
        elif op == "prompt_contribution_compare":
            if set(expected) != {"intensity_1_2_3_are_distinct"}:
                raise ValueError("prompt compare expectation schema drift")
            for level in (1, 2, 3):
                lines.append("mode.apply(CelinePersonaMode.InputChannel.DIRECT_USER, " + literal(f"Nutte {level}") + ");")
                lines.append(f"String p{level} = mode.promptContribution();")
            check(lines, "(!p1.equals(p2) && !p1.equals(p3) && !p2.equals(p3)) == "
                  + bool_lit(expected["intensity_1_2_3_are_distinct"]), case_id + ": prompts differ")
        elif op == "snapshot_restore":
            setup(lines, case.get("initial", {}))
            lines.append("CelinePersonaMode.State snap = mode.snapshot();")
            state_checks(lines, {"active": expected["active"], "intensity": expected["intensity"]}, case_id + ": snapshot", "snap")
            check(lines, "snap.activationSource() == CelinePersonaMode.ActivationSource." + SOURCES[expected["snapshot_activation_source"]], case_id + ": snapshot provenance")
            lines.append("CelinePersonaMode copy = new CelinePersonaMode();")
            lines.append("boolean accepted = copy.restorePersistedState(snap.active(), snap.intensity());")
            check(lines, "accepted == " + bool_lit(expected["restore_accepted"]), case_id + ": restore acceptance")
            check(lines, "copy.isActive() == " + bool_lit(expected["active"]), case_id + ": restored active")
            check(lines, "copy.intensity() == " + integer(expected["intensity"]), case_id + ": restored intensity")
            check(lines, "copy.activationSource() == CelinePersonaMode.ActivationSource." + SOURCES[expected["restore_activation_source"]], case_id + ": restored provenance")
            if set(expected) != {"restore_accepted", "active", "intensity", "snapshot_activation_source", "restore_activation_source"}:
                raise ValueError("snapshot restore expectation schema drift")
        elif op == "restore_persisted_state":
            persisted = case["persisted"]
            if set(persisted) != {"active", "intensity"}:
                raise ValueError("persistence payload schema drift")
            lines.append('mode.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte 3");')
            lines.append("boolean accepted = mode.restorePersistedState(" + bool_lit(persisted["active"]) + ", " + integer(persisted["intensity"]) + ");")
            check(lines, "accepted == " + bool_lit(expected["restore_accepted"]), case_id + ": restore accepted")
            state_checks(lines, expected, case_id, "mode")
            if set(expected) != {"restore_accepted", "active", "intensity", "activation_source"}:
                raise ValueError("persistence expectation schema drift")
        else:
            raise ValueError(f"unsupported operation {op}")
        lines.append("}")
        count += 1
    if count != len(OPS):
        raise ValueError(f"expected {len(OPS)} operation vectors, got {count}")
    lines += ['System.out.println("CelinePersona JSON operation vectors: PASS (' + str(count) + ')");', "}", "}"]
    return "\n".join(lines) + "\n"


def main() -> int:
    data = json.loads(VECTORS.read_text(encoding="utf-8"))
    if data.get("schema") != "celine-adult-persona-acceptance/v3" or data.get("issue") != 112:
        raise ValueError("acceptance document schema/issue drift")
    cases = data["cases"]
    if not isinstance(cases, list) or not all(isinstance(c, dict) for c in cases):
        raise ValueError("invalid acceptance vectors")
    ids = [c["id"] for c in cases]
    if len(ids) != len(set(ids)) or {c["id"] for c in cases if "operation" in c} != set(OPS):
        raise ValueError("duplicate or unexpected operation IDs")
    with tempfile.TemporaryDirectory(prefix="celine-persona-operations-") as tmp:
        root = Path(tmp)
        harness = root / "CelinePersonaOperationVectorTest.java"
        classes = root / "classes"
        classes.mkdir()
        harness.write_text(generate(cases), encoding="utf-8")
        subprocess.run(["javac", "-d", str(classes), str(SOURCE), str(harness)], cwd=ROOT, check=True)
        subprocess.run(["java", "-cp", str(classes), "CelinePersonaOperationVectorTest"], cwd=ROOT, check=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
