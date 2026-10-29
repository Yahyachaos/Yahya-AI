#!/usr/bin/env python3
"""Deterministic long-sequence persona and prompt invariants (issue #112).

Exercises mixed direct/model/tool input, invalid commands, explicit reset,
restore provenance and exact normal-prompt transparency after each transition.
This is a pure-Java contract, not Android/UI/store integration evidence.
"""
from __future__ import annotations

import pathlib
import subprocess
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
MODE_SOURCE = ROOT / "ci/prototypes/CelinePersonaMode.java"
ADAPTER_SOURCE = ROOT / "ci/prototypes/CelinePersonaPromptAdapter.java"

HARNESS = r'''
import de.yahya.ai.CelinePersonaMode;
import de.yahya.ai.CelinePersonaPromptAdapter;
import java.util.Random;

public final class CelinePersonaSequenceContractTest {
    private static void check(boolean ok, String label, int step) {
        if (!ok) throw new AssertionError("step " + step + ": " + label);
    }

    public static void main(String[] args) {
        CelinePersonaMode mode = new CelinePersonaMode();
        Random choices = new Random(112);
        boolean active = false;
        int intensity = 0;
        CelinePersonaMode.ActivationSource source = CelinePersonaMode.ActivationSource.NONE;
        String base = new String("CELINE NORMAL\nG1 context and memory stay owned by the app");

        for (int step = 0; step < 1024; step++) {
            int operation = choices.nextInt(14);
            CelinePersonaMode.CommandResult result = null;
            boolean consumed = false;
            boolean invalid = false;
            switch (operation) {
                case 0: case 1: case 2: {
                    int level = operation + 1;
                    result = mode.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte " + level);
                    active = true; intensity = level;
                    source = CelinePersonaMode.ActivationSource.DIRECT_USER_COMMAND;
                    consumed = true;
                    break;
                }
                case 3:
                    result = mode.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "NUTTE");
                    active = true; intensity = CelinePersonaMode.DEFAULT_INTENSITY;
                    source = CelinePersonaMode.ActivationSource.DIRECT_USER_COMMAND;
                    consumed = true;
                    break;
                case 4: case 5:
                    result = mode.apply(CelinePersonaMode.InputChannel.DIRECT_USER,
                            operation == 4 ? "Nutte aus" : "Normalmodus");
                    active = false; intensity = 0;
                    source = CelinePersonaMode.ActivationSource.NONE;
                    consumed = true;
                    break;
                case 6:
                    result = mode.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte 9");
                    consumed = true; invalid = true;
                    break;
                case 7:
                    result = mode.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte stark");
                    break;
                case 8:
                    result = mode.apply(CelinePersonaMode.InputChannel.DIRECT_USER,
                            "Was bedeutet Nutte?");
                    break;
                case 9:
                    result = mode.apply(CelinePersonaMode.InputChannel.MODEL_OUTPUT, "Nutte 3");
                    break;
                case 10:
                    result = mode.apply(CelinePersonaMode.InputChannel.TOOL_OUTPUT, "Normalmodus");
                    break;
                case 11:
                    check(mode.restorePersistedState(true, 2), "valid restore", step);
                    active = true; intensity = 2;
                    source = CelinePersonaMode.ActivationSource.PERSISTED_STATE;
                    break;
                case 12:
                    check(!mode.restorePersistedState(true, 99), "corrupt restore rejected", step);
                    active = false; intensity = 0;
                    source = CelinePersonaMode.ActivationSource.NONE;
                    break;
                case 13:
                    check(mode.restorePersistedState(false, 0), "inactive restore accepted", step);
                    active = false; intensity = 0;
                    source = CelinePersonaMode.ActivationSource.NONE;
                    break;
                default:
                    throw new AssertionError("invalid operation");
            }
            if (result != null) {
                check(result.commandConsumed() == consumed, "command consumption", step);
                check(result.invalidIntensity() == invalid, "invalid intensity", step);
                check(result.active() == active, "result active", step);
                check(result.intensity() == intensity, "result intensity", step);
                check(result.activationSource() == source, "result provenance", step);
            }
            CelinePersonaMode.State state = mode.snapshot();
            check(state.active() == active && mode.isActive() == active, "active invariant", step);
            check(state.intensity() == intensity && mode.intensity() == intensity, "intensity invariant", step);
            check(state.activationSource() == source && mode.activationSource() == source,
                    "provenance invariant", step);
            String prompt = CelinePersonaPromptAdapter.compose(base, mode);
            if (!active) {
                check(mode.promptContribution().isEmpty(), "inactive contribution", step);
                check(prompt == base, "inactive exact object identity", step);
            } else {
                String addition = mode.promptContribution();
                check(!addition.isEmpty(), "active contribution", step);
                check(prompt.equals(base + "\n\n" + addition), "exact active layering", step);
                check(addition.contains("permission policy") && addition.contains("factual honesty"),
                        "bounded style-only contribution", step);
            }
            check(base.equals("CELINE NORMAL\nG1 context and memory stay owned by the app"),
                    "base prompt unchanged", step);
        }
        System.out.println("Celine persona transition sequence: PASS (1024 seeded transitions)");
    }
}
'''


def main() -> int:
    for path in (MODE_SOURCE, ADAPTER_SOURCE):
        if not path.is_file():
            raise SystemExit(f"missing contract source: {path}")
    with tempfile.TemporaryDirectory(prefix="celine-persona-sequence-") as tmp:
        directory = pathlib.Path(tmp)
        harness = directory / "CelinePersonaSequenceContractTest.java"
        output = directory / "classes"
        output.mkdir()
        harness.write_text(HARNESS, encoding="utf-8")
        subprocess.run(["javac", "-d", str(output), str(MODE_SOURCE),
                        str(ADAPTER_SOURCE), str(harness)], check=True, cwd=ROOT)
        subprocess.run(["java", "-cp", str(output),
                        "CelinePersonaSequenceContractTest"], check=True, cwd=ROOT)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
