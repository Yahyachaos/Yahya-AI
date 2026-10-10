#!/usr/bin/env python3
"""Deterministic pure-Java acceptance runner for the CelinePersonaMode prototype.

This deliberately avoids Android UI, network, room/avatar, and Gradle wiring. It
compiles the ownership-safe prototype with javac and executes a temporary harness
covering the canonical issue #112 acceptance vectors. Promotion into app/src is
blocked until shared runtime/version ownership is explicitly handed off.
"""

from __future__ import annotations

import json
import pathlib
import subprocess
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCE = ROOT / "ci/prototypes/CelinePersonaMode.java"
CASES = ROOT / "ci/evidence/CELINE_ADULT_PERSONA_ACCEPTANCE_CASES.json"

REQUIRED_CASE_IDS = {
    "activate-default",
    "activate-case-insensitive",
    "activate-intensity-1",
    "activate-intensity-2",
    "activate-intensity-3",
    "deactivate-codeword",
    "deactivate-normal-mode",
    "deactivate-removes-prompt-next-request",
    "no-substring-activation",
    "no-quoted-activation",
    "no-model-output-activation",
    "no-tool-output-activation",
    "non-user-input-preserves-active-state",
    "reject-high-intensity",
    "reject-malformed-intensity",
    "inactive-prompt-empty",
    "active-prompt-bounded",
    "prompt-varies-by-intensity",
    "persist-roundtrip-active",
    "persist-roundtrip-inactive",
    "persist-corrupt-active-fails-closed",
    "persist-corrupt-inactive-fails-closed",
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
        testPersistenceSeam();
        System.out.println("CelinePersonaMode contract: PASS");
    }

    private static void testInitial() {
        CelinePersonaMode m = new CelinePersonaMode();
        check(!m.isActive(), "initial inactive");
        check(m.intensity() == 0, "initial intensity 0");
        check(m.activationSource() == CelinePersonaMode.ActivationSource.NONE, "initial source none");
        check(m.promptContribution().isEmpty(), "inactive prompt empty");
        CelinePersonaMode.State snapshot = m.snapshot();
        check(snapshot.activationSource() == CelinePersonaMode.ActivationSource.NONE, "initial snapshot source none");
    }

    private static void testActivation() {
        CelinePersonaMode m = new CelinePersonaMode();
        assertState(m.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte"), true, 2, true, false,
                CelinePersonaMode.ActivationSource.DIRECT_USER_COMMAND);
        assertState(m.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "NUTTE"), true, 2, true, false,
                CelinePersonaMode.ActivationSource.DIRECT_USER_COMMAND);
        assertState(m.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte 1"), true, 1, true, false,
                CelinePersonaMode.ActivationSource.DIRECT_USER_COMMAND);
        assertState(m.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte 2"), true, 2, true, false,
                CelinePersonaMode.ActivationSource.DIRECT_USER_COMMAND);
        assertState(m.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte 3"), true, 3, true, false,
                CelinePersonaMode.ActivationSource.DIRECT_USER_COMMAND);
        check(m.activationSource() == CelinePersonaMode.ActivationSource.DIRECT_USER_COMMAND,
                "direct activation source retained");
    }

    private static void testDeactivation() {
        CelinePersonaMode m = new CelinePersonaMode();
        m.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte 3");
        assertState(m.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte aus"), false, 0, true, false,
                CelinePersonaMode.ActivationSource.NONE);
        check(m.promptContribution().isEmpty(), "deactivation removes prompt immediately");
        m.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte 2");
        assertState(m.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Normalmodus"), false, 0, true, false,
                CelinePersonaMode.ActivationSource.NONE);
        check(m.promptContribution().isEmpty(), "normal mode removes prompt immediately");
        check(m.activationSource() == CelinePersonaMode.ActivationSource.NONE, "deactivation clears source");
    }

    private static void testFailClosedChannelsAndSubstrings() {
        CelinePersonaMode m = new CelinePersonaMode();
        assertState(m.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Was bedeutet das Wort Nutte?"), false, 0, false, false,
                CelinePersonaMode.ActivationSource.NONE);
        assertState(m.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Er schrieb: Nutte"), false, 0, false, false,
                CelinePersonaMode.ActivationSource.NONE);
        assertState(m.apply(CelinePersonaMode.InputChannel.MODEL_OUTPUT, "Nutte"), false, 0, false, false,
                CelinePersonaMode.ActivationSource.NONE);
        assertState(m.apply(CelinePersonaMode.InputChannel.TOOL_OUTPUT, "Nutte"), false, 0, false, false,
                CelinePersonaMode.ActivationSource.NONE);

        m.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte 1");
        assertState(m.apply(CelinePersonaMode.InputChannel.MODEL_OUTPUT, "Nutte 3"), true, 1, false, false,
                CelinePersonaMode.ActivationSource.DIRECT_USER_COMMAND);
        check(m.activationSource() == CelinePersonaMode.ActivationSource.DIRECT_USER_COMMAND,
                "non-user input cannot rewrite activation source");
    }

    private static void testInvalidIntensity() {
        CelinePersonaMode m = new CelinePersonaMode();
        m.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte 2");
        assertState(m.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte 9"), true, 2, true, true,
                CelinePersonaMode.ActivationSource.DIRECT_USER_COMMAND);
        assertState(m.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte stark"), true, 2, false, false,
                CelinePersonaMode.ActivationSource.DIRECT_USER_COMMAND);
    }

    private static void testPromptContribution() {
        CelinePersonaMode m = new CelinePersonaMode();
        check(m.promptContribution().isEmpty(), "inactive prompt empty");

        m.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte 1");
        String p1 = m.promptContribution();
        assertBoundedPrompt(p1);

        m.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte 2");
        String p2 = m.promptContribution();
        assertBoundedPrompt(p2);

        m.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte 3");
        String p3 = m.promptContribution();
        assertBoundedPrompt(p3);

        check(!p1.equals(p2), "intensity 1 and 2 prompt differ");
        check(!p2.equals(p3), "intensity 2 and 3 prompt differ");
        check(!p1.equals(p3), "intensity 1 and 3 prompt differ");
    }

    private static void testPersistenceSeam() {
        CelinePersonaMode source = new CelinePersonaMode();
        source.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte 2");
        CelinePersonaMode.State snapshot = source.snapshot();
        check(snapshot.active(), "snapshot active");
        check(snapshot.intensity() == 2, "snapshot intensity");
        check(snapshot.activationSource() == CelinePersonaMode.ActivationSource.DIRECT_USER_COMMAND,
                "snapshot records direct activation source");

        CelinePersonaMode restored = new CelinePersonaMode();
        check(restored.restorePersistedState(snapshot.active(), snapshot.intensity()), "valid active restore accepted");
        check(restored.isActive(), "active restore state");
        check(restored.intensity() == 2, "active restore intensity");
        check(restored.activationSource() == CelinePersonaMode.ActivationSource.PERSISTED_STATE,
                "active restore marks persisted source");
        check(restored.snapshot().activationSource() == CelinePersonaMode.ActivationSource.PERSISTED_STATE,
                "restored snapshot retains persisted source");

        check(restored.restorePersistedState(false, 0), "valid inactive restore accepted");
        check(!restored.isActive(), "inactive restore state");
        check(restored.intensity() == 0, "inactive restore intensity");
        check(restored.activationSource() == CelinePersonaMode.ActivationSource.NONE, "inactive restore source none");
        check(restored.promptContribution().isEmpty(), "inactive restore prompt empty");

        restored.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte 3");
        check(!restored.restorePersistedState(true, 9), "corrupt active restore rejected");
        check(!restored.isActive(), "corrupt active restore fails closed");
        check(restored.intensity() == 0, "corrupt active restore clears intensity");
        check(restored.activationSource() == CelinePersonaMode.ActivationSource.NONE,
                "corrupt active restore clears source");

        restored.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte 3");
        check(!restored.restorePersistedState(false, 2), "corrupt inactive restore rejected");
        check(!restored.isActive(), "corrupt inactive restore fails closed");
        check(restored.intensity() == 0, "corrupt inactive restore clears intensity");
        check(restored.activationSource() == CelinePersonaMode.ActivationSource.NONE,
                "corrupt inactive restore clears source");
    }

    private static void assertBoundedPrompt(String p) {
        check(!p.isEmpty(), "active prompt nonempty");
        check(p.contains("normal identity"), "identity preserved");
        check(p.contains("permission policy"), "permission policy preserved");
        check(p.contains("privacy or memory boundaries"), "privacy/memory preserved");
        check(p.contains("factual honesty"), "factual honesty preserved");
    }

    private static void assertState(CelinePersonaMode.CommandResult r, boolean active, int intensity,
                                    boolean consumed, boolean invalid,
                                    CelinePersonaMode.ActivationSource source) {
        check(r.active() == active, "active mismatch");
        check(r.intensity() == intensity, "intensity mismatch");
        check(r.commandConsumed() == consumed, "consumed mismatch");
        check(r.invalidIntensity() == invalid, "invalid mismatch");
        check(r.activationSource() == source, "activation source mismatch");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
'''


def main() -> int:
    if not SOURCE.is_file():
        raise SystemExit(f"missing prototype source: {SOURCE}")
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
