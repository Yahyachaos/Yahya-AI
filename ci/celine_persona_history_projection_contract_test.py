#!/usr/bin/env python3
"""Core-only executable acceptance for event-marked persona transcript projection.

Compiles the *actual promoted* CelinePersonaMode.java and the ownership-safe,
non-runtime projection prototype in a fresh JDK sandbox. All vectors must PASS.
This does NOT establish that MainActivity is wired, or that the cloud/device works.
Use --audit-live for a separate fail-closed read-only diagnosis of current wiring.
"""
from __future__ import annotations

import argparse
import json
import pathlib
import subprocess
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
CASES = ROOT / "ci/evidence/CELINE_PERSONA_HISTORY_PROJECTION_ACCEPTANCE.json"
MODE = ROOT / "app/src/main/java/de/yahya/ai/CelinePersonaMode.java"
PROJECTION = ROOT / "ci/prototypes/CelinePersonaHistoryProjection.java"
MAIN = ROOT / "app/src/main/java/de/yahya/ai/MainActivity.java"


def lit(text: str) -> str:
    if not isinstance(text, str):
        raise ValueError("fixture content must be a string")
    return json.dumps(text, ensure_ascii=True)


def harness(cases: list[dict]) -> str:
    body: list[str] = []
    for case in cases:
        name = case["id"]
        body.extend([
            "        {",
            "            CelinePersonaMode mode = new CelinePersonaMode();",
            "            List<CelinePersonaHistoryProjection.Turn> visible = new ArrayList<>();",
            "            boolean precedingDirectCommand = false;",
        ])
        for event in case["events"]:
            role = event["role"]
            channel = event.get("channel", "DIRECT_USER" if role == "user" else "OTHER")
            if channel not in {"DIRECT_USER", "MODEL_OUTPUT", "TOOL_OUTPUT", "OTHER"}:
                raise ValueError(f"{name}: invalid input channel")
            if role not in {"user", "assistant", "tool"}:
                raise ValueError(f"{name}: invalid message role")
            if channel == "DIRECT_USER" and role != "user":
                raise ValueError(f"{name}: only direct-user turns may dispatch")
            text = lit(event["text"])
            expected = str(bool(event.get("consumed", False))).lower()
            invalid = str(bool(event.get("invalid", False))).lower()
            is_ack = str(bool(event.get("ack", False))).lower()
            body.extend([
                "            {",
                "                CelinePersonaMode.CommandResult result = mode.apply("
                + f"CelinePersonaMode.InputChannel.{channel}, {text});",
                f"                check(result.commandConsumed() == {expected}, {lit(name + ': parser consumed mismatch')});",
                f"                check(result.invalidIntensity() == {invalid}, {lit(name + ': invalid intensity mismatch')});",
                f"                if ({is_ack}) check(precedingDirectCommand, {lit(name + ': orphan control acknowledgement')});",
                f"                boolean marked = result.commandConsumed() || {is_ack};",
                f"                visible.add(new CelinePersonaHistoryProjection.Turn({lit(role)}, {text}, marked));",
                "                precedingDirectCommand = result.commandConsumed() && "
                + f"{'true' if role == 'user' else 'false'};",
                "            }",
            ])
        expected = case["expected_provider"]
        if any(not isinstance(row, list) or len(row) != 2 for row in expected):
            raise ValueError(f"{name}: malformed expected provider row")
        body.append("            List<CelinePersonaHistoryProjection.Turn> projected = "
                    "CelinePersonaHistoryProjection.providerTurns(visible);")
        body.append(f"            check(visible.size() == {len(case['events'])}, {lit(name + ': visible history modified')});")
        body.append(f"            check(projected.size() == {len(expected)}, {lit(name + ': provider count mismatch')});")
        for i, (role, text) in enumerate(expected):
            body.append(
                f"            check(projected.get({i}).role.equals({lit(role)}) && "
                f"projected.get({i}).content.equals({lit(text)}), {lit(name + ': projected order/content mismatch at ' + str(i))});"
            )
            body.append(
                f"            check(visible.contains(projected.get({i})), {lit(name + ': lost identity at ' + str(i))});"
            )
        active = str(bool(case["active"])).lower()
        body.append(f"            check(mode.isActive() == {active}, {lit(name + ': final active state mismatch')});")
        body.append(f"            check(mode.intensity() == {int(case['intensity'])}, {lit(name + ': final intensity mismatch')});")
        body.extend([
            "            try { projected.clear(); throw new AssertionError("
            + lit(name + ": mutable provider snapshot") + "); }",
            "            catch (UnsupportedOperationException expectedException) {}",
            "            passed++;",
            "        }",
        ])
    prelude = """package de.yahya.ai;
import java.util.ArrayList;
import java.util.List;
public final class PersonaHistoryHarness {
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        int passed = 0;
"""
    return prelude + "\n".join(body) + f'\n        System.out.println("CORE_HISTORY_PROJECTION_PROTOTYPE_PASS cases=" + passed);\n    }}\n}}\n'


def audit_live() -> int:
    """Fail closed: do not conflate successful prototype vectors with UI wiring."""
    compact = "".join(MAIN.read_text(encoding="utf-8").split())
    try:
        submit = compact.split("privatevoidsubmit(Stringtext)", 1)[1].split(
            "privateStringcallOpenAI(", 1)[0]
        call = compact.split("privateStringcallOpenAI(", 1)[1].split(
            "privatevoidlearnWithAI(", 1)[0]
    except IndexError:
        print("LIVE_PERSONA_HISTORY_AUDIT_FAIL: cannot establish source boundaries")
        return 2
    control_before = submit.find("addUser(text)") < submit.find("if(handlePersonaCommand(text))return;")
    raw_turns = "for(Messagem:messages)" in call and 'o.put("content",m.content)' in call
    if control_before and raw_turns:
        print("LIVE_PERSONA_HISTORY_GAP_CONFIRMED: consumed direct control/ack bubbles remain in provider input")
        return 2
    print("LIVE_PERSONA_HISTORY_AUDIT_REQUIRES_INTEGRATION_REVIEW: static pattern changed; no runtime PASS inferred")
    return 2


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--audit-live", action="store_true", help="read-only fail-closed live MainActivity gap check")
    opts = parser.parse_args()
    if opts.audit_live:
        return audit_live()
    doc = json.loads(CASES.read_text(encoding="utf-8"))
    if doc.get("schema") != "celine-persona-history-projection/v1":
        raise SystemExit("unsupported acceptance schema")
    cases = doc.get("cases")
    if not isinstance(cases, list) or len(cases) < 8:
        raise SystemExit("history projection acceptance vectors incomplete")
    ids = [item["id"] for item in cases]
    if len(set(ids)) != len(ids):
        raise SystemExit("duplicate acceptance case id")
    with tempfile.TemporaryDirectory(prefix="celine-persona-history-") as td:
        dest = pathlib.Path(td)
        generated = dest / "PersonaHistoryHarness.java"
        generated.write_text(harness(cases), encoding="utf-8")
        subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(dest),
                        str(MODE), str(PROJECTION), str(generated)], check=True)
        subprocess.run(["java", "-cp", str(dest), "de.yahya.ai.PersonaHistoryHarness"],
                       check=True)
    print("SCOPE: non-runtime Core prototype; current provider wiring NOT yet proved")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
