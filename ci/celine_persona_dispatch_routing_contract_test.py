#!/usr/bin/env python3
"""Deterministic command-consumption routing contract for the Celine persona seam.

The canonical commands and expected states come from the existing issue #112
dispatch acceptance vector file. This test adds no Android/runtime wiring. It proves the
integration invariant that the existing submit flow continues exactly when the
persona parser did not consume the direct-user command.
"""

from __future__ import annotations

import base64
import json
import pathlib
import subprocess
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
MODE_SOURCE = ROOT / "ci/prototypes/CelinePersonaMode.java"
CASES = ROOT / "ci/evidence/CELINE_ADULT_PERSONA_DISPATCH_ACCEPTANCE.json"
EXPECTED_SCHEMA = "celine-adult-persona-dispatch-acceptance/v1"

REQUIRED_CASE_IDS = {
    "activate-default-short-circuits",
    "activate-intensity-short-circuits",
    "deactivate-short-circuits",
    "normalmode-short-circuits",
    "invalid-intensity-short-circuits-preserves-state",
    "normal-text-passes-through-inactive",
    "normal-text-passes-through-active",
    "substring-passes-through",
    "blank-passes-through",
}

PROBE = r"""
import de.yahya.ai.CelinePersonaMode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class CelinePersonaDispatchProbe {
    public static void main(String[] args) {
        if (args.length != 3) {
            throw new IllegalArgumentException("expected: initialActive initialIntensity inputBase64");
        }

        boolean initialActive = Boolean.parseBoolean(args[0]);
        int initialIntensity = Integer.parseInt(args[1]);
        String input = new String(Base64.getDecoder().decode(args[2]), StandardCharsets.UTF_8);

        CelinePersonaMode mode = new CelinePersonaMode();
        if (initialActive || initialIntensity != 0) {
            if (!mode.restorePersistedState(initialActive, initialIntensity)) {
                throw new AssertionError("invalid test setup");
            }
        }

        CelinePersonaMode.CommandResult result =
                mode.apply(CelinePersonaMode.InputChannel.DIRECT_USER, input);
        boolean continueNormalFlow = !result.commandConsumed();

        System.out.println(
                result.commandConsumed() + "|" +
                result.invalidIntensity() + "|" +
                result.active() + "|" +
                result.intensity() + "|" +
                continueNormalFlow
        );
    }
}
"""


def as_bool(value: object) -> bool:
    if type(value) is not bool:
        raise ValueError(f"expected JSON boolean, got {value!r}")
    return value


def main() -> int:
    if not MODE_SOURCE.is_file():
        raise SystemExit(f"missing prototype source: {MODE_SOURCE}")
    if not CASES.is_file():
        raise SystemExit(f"missing acceptance vectors: {CASES}")

    data = json.loads(CASES.read_text(encoding="utf-8"))
    if data.get("schema") != EXPECTED_SCHEMA or data.get("issue") != 112:
        raise SystemExit("dispatch acceptance schema or issue drift")
    cases = data.get("cases")
    if not isinstance(cases, list):
        raise SystemExit("dispatch acceptance cases must be a list")
    by_id = {case["id"]: case for case in cases}
    if len(by_id) != len(cases):
        raise SystemExit("duplicate dispatch acceptance case ids")
    missing = sorted(REQUIRED_CASE_IDS - by_id.keys())
    if missing:
        raise SystemExit(f"dispatch acceptance vector drift; missing cases: {missing}")

    with tempfile.TemporaryDirectory(prefix="celine-persona-dispatch-") as tmp:
        tmp_path = pathlib.Path(tmp)
        probe = tmp_path / "CelinePersonaDispatchProbe.java"
        classes = tmp_path / "classes"
        classes.mkdir()
        probe.write_text(PROBE, encoding="utf-8")

        subprocess.run(
            ["javac", "-d", str(classes), str(MODE_SOURCE), str(probe)],
            check=True,
            cwd=ROOT,
        )

        for case_id in sorted(by_id):
            case = by_id[case_id]
            expected = case["expected"]
            initial = case.get("initial", {"active": False, "intensity": 0})
            encoded = base64.b64encode(case["input"].encode("utf-8")).decode("ascii")

            proc = subprocess.run(
                [
                    "java",
                    "-cp",
                    str(classes),
                    "CelinePersonaDispatchProbe",
                    str(as_bool(initial.get("active", False))).lower(),
                    str(int(initial.get("intensity", 0))),
                    encoded,
                ],
                check=True,
                cwd=ROOT,
                text=True,
                capture_output=True,
            )

            consumed_s, invalid_s, active_s, intensity_s, continue_s = proc.stdout.strip().split("|")
            consumed = consumed_s == "true"
            invalid = invalid_s == "true"
            active = active_s == "true"
            intensity = int(intensity_s)
            continue_normal_flow = continue_s == "true"

            if consumed != as_bool(expected.get("command_consumed", False)):
                raise AssertionError(f"{case_id}: command_consumed mismatch")
            if invalid != as_bool(expected.get("invalid_intensity", False)):
                raise AssertionError(f"{case_id}: invalid_intensity mismatch")
            if active != as_bool(expected.get("active", False)):
                raise AssertionError(f"{case_id}: active mismatch")
            if intensity != int(expected.get("intensity", 0)):
                raise AssertionError(f"{case_id}: intensity mismatch")
            if continue_normal_flow != as_bool(expected["continue_normal_flow"]):
                raise AssertionError(f"{case_id}: continue_normal_flow mismatch")
            if continue_normal_flow == consumed:
                raise AssertionError(f"{case_id}: submit-flow gate must be exact inverse of command_consumed")

    print(f"CelinePersona dispatch routing contract: PASS ({len(by_id)} dedicated vectors)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
