#!/usr/bin/env python3
"""Deterministic pure-Java acceptance runner for CelinePersonaMode.

This deliberately avoids Android UI, network, room/avatar, and Gradle wiring. It
compiles the production Java class with javac and executes a temporary harness
covering the canonical issue #112 acceptance vectors.
"""

from __future__ import annotations

import json
import pathlib
import subprocess
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/de/yahya/ai/CelinePersonaMode.java"
CASES = ROOT / "ci/evidence/CELINE_ADULT_PERSONA_ACCEPTANCE_CASES.json"

REQUIRED_CASE_IDS = {
    "activate-default",
    "activate-case-insensitive",
    "activate-intensity-1",
    "activate-intensity-2",
    "activate-intensity-3",
    "deactivate-codeword",
    "deactivate-normal-mode",
    "no-substring-activation",
    "no-quoted-activation",
    "no-model-output-activation",
    "no-tool-output-activation",
    "reject-high-intensity",
    "reject-malformed-intensity",
    "inactive-prompt-empty",
    "active-prompt-bounded",
}

HARNESS = r'''
import de.yahya.ai.CelinePersonaMode;

public final class CelinePersonaModeContractTest {
    public static void main(String[] args) {
        testInitial();
        testActivation();
        testDeactivation();
        testFailClosedChannelsAndSubstrings();
        testInvalidIntensity();
        testPromptContribution();
        System.out.println("CelinePersonaMode contract: PASS");
    }

    private static void testInitial() {
        CelinePersonaMode m = new CelinePersonaMode();
        check(!m.isActive(), "initial inactive");
        check(m.intensity() == 0, "initial intensity 0");
        check(m.promptContribution().isEmpty(), "inactive prompt empty");
    }

    private static void testActivation() {
        CelinePersonaMode m = new CelinePersonaMode();
        assertState(m.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte"), true, 2, true, false);
        assertState(m.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "NUTTE"), true, 2, true, false);
        assertState(m.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte 1"), true, 1, true, false);
        assertState(m.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte 2"), true, 2, true, false);
        assertState(m.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte 3"), true, 3, true, false);
    }

    private static void testDeactivation() {
        CelinePersonaMode m = new CelinePersonaMode();
        m.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte 3");
        assertState(m.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte aus"), false, 0, true, false);
        m.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte 2");
        assertState(m.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Normalmodus"), false, 0, true, false);
    }

    private static void testFailClosedChannelsAndSubstrings() {
        CelinePersonaMode m = new CelinePersonaMode();
        assertState(m.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Was bedeutet das Wort Nutte?"), false, 0, false, false);
        assertState(m.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Er schrieb: Nutte"), false, 0, false, false);
        assertState(m.apply(CelinePersonaMode.InputChannel.MODEL_OUTPUT, "Nutte"), false, 0, false, false);
        assertState(m.apply(CelinePersonaMode.InputChannel.TOOL_OUTPUT, "Nutte"), false, 0, false, false);
    }

    private static void testInvalidIntensity() {
        CelinePersonaMode m = new CelinePersonaMode();
        m.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte 2");
        assertState(m.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte 9"), true, 2, true, true);
        assertState(m.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte stark"), true, 2, false, false);
    }

    private static void testPromptContribution() {
        CelinePersonaMode m = new CelinePersonaMode();
        check(m.promptContribution().isEmpty(), "inactive prompt empty");
        m.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte 3");
        String p = m.promptContribution();
        check(!p.isEmpty(), "active prompt nonempty");
        check(p.contains("normal identity"), "identity preserved");
        check(p.contains("permission policy"), "permission policy preserved");
        check(p.contains("privacy or memory boundaries"), "privacy/memory preserved");
        check(p.contains("factual honesty"), "factual honesty preserved");
    }

    private static void assertState(CelinePersonaMode.CommandResult r, boolean active, int intensity,
                                    boolean consumed, boolean invalid) {
        check(r.active() == active, "active mismatch");
        check(r.intensity() == intensity, "intensity mismatch");
        check(r.commandConsumed() == consumed, "consumed mismatch");
        check(r.invalidIntensity() == invalid, "invalid mismatch");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
'''


def main() -> int:
    if not SOURCE.is_file():
        raise SystemExit(f"missing runtime source: {SOURCE}")
    if not CASES.is_file():
        raise SystemExit(f"missing acceptance vectors: {CASES}")

    data = json.loads(CASES.read_text(encoding="utf-8"))
    ids = {case["id"] for case in data.get("cases", [])}
    missing = sorted(REQUIRED_CASE_IDS - ids)
    if missing:
        raise SystemExit(f"acceptance vector drift; missing cases: {missing}")

    with tempfile.TemporaryDirectory(prefix="celine-persona-contract-") as tmp:
        tmp_path = pathlib.Path(tmp)
        harness = tmp_path / "CelinePersonaModeContractTest.java"
        classes = tmp_path / "classes"
        classes.mkdir()
        harness.write_text(HARNESS, encoding="utf-8")

        subprocess.run(
            ["javac", "-d", str(classes), str(SOURCE), str(harness)],
            check=True,
            cwd=ROOT,
        )
        subprocess.run(
            ["java", "-cp", str(classes), "CelinePersonaModeContractTest"],
            check=True,
            cwd=ROOT,
        )

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
