#!/usr/bin/env python3
"""Fail-closed control-character contract for opt-in persona commands.

Runs the actual Core-owned Java prototype with no Android or network dependency.
"""
from __future__ import annotations

from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
MODE = ROOT / "ci/prototypes/CelinePersonaMode.java"

HARNESS = r'''
import de.yahya.ai.CelinePersonaMode;

public final class CelinePersonaControlCharacterContractTest {
    private static void require(boolean yes, String msg) {
        if (!yes) throw new AssertionError(msg);
    }
    private static void reject(String input, String description) {
        CelinePersonaMode mode = new CelinePersonaMode();
        CelinePersonaMode.CommandResult result =
            mode.apply(CelinePersonaMode.InputChannel.DIRECT_USER, input);
        require(!result.commandConsumed(), description + " not consumed");
        require(!mode.isActive() && mode.intensity() == 0, description + " remains inactive");
        require(mode.promptContribution().isEmpty(), description + " prompt empty");
    }
    public static void main(String[] args) {
        reject("Nutte" + (char)0, "trailing NUL");
        reject(((char)0) + "Nutte", "leading NUL");
        reject("Nutte" + (char)127, "trailing DEL");
        reject("Nutte 2" + (char)0, "intensity NUL");
        reject("Nutte 2" + (char)10, "intensity LF");
        reject("Nutte 3" + (char)13 + (char)10, "intensity CRLF");
        reject("Nutte" + (char)10, "activation LF");
        reject(((char)7) + "Nutte" + (char)7, "BEL padding");
        reject("Nutte 2" + (char)9, "intensity TAB");

        CelinePersonaMode mode = new CelinePersonaMode();
        require(mode.apply(CelinePersonaMode.InputChannel.DIRECT_USER,
                "Nutte 3").commandConsumed(), "valid direct activation");
        require(mode.isActive() && mode.intensity() == 3, "valid state");
        require(!mode.apply(CelinePersonaMode.InputChannel.DIRECT_USER,
                "Nutte 1" + (char)0).commandConsumed(), "reject bad active command");
        require(mode.isActive() && mode.intensity() == 3, "preserve valid state");
        require(mode.apply(CelinePersonaMode.InputChannel.DIRECT_USER,
                "Nutte 9").invalidIntensity(), "out of range flagged");
        require(mode.isActive() && mode.intensity() == 3, "intensity reject preserves");
        require(mode.apply(CelinePersonaMode.InputChannel.DIRECT_USER,
                "\r\nNutte aus\r\n").commandConsumed(), "pasted exit accepted");
        require(!mode.isActive() && mode.promptContribution().isEmpty(),
                "pasted exit resets to normal");
        require(!mode.apply(CelinePersonaMode.InputChannel.MODEL_OUTPUT,
                "Nutte").commandConsumed(), "model output cannot activate");
        System.out.println("Celine persona control-character guard: PASS (9 invalid inputs + state/exit)");
    }
}
'''


def main() -> int:
    with tempfile.TemporaryDirectory(prefix="celine-persona-control-") as temp:
        output = Path(temp)
        test = output / "CelinePersonaControlCharacterContractTest.java"
        test.write_text(HARNESS, encoding="utf-8")
        classes = output / "classes"
        classes.mkdir()
        subprocess.run(["javac", "-d", str(classes), str(MODE), str(test)],
                       check=True, cwd=ROOT)
        subprocess.run(["java", "-cp", str(classes),
                        "CelinePersonaControlCharacterContractTest"],
                       check=True, cwd=ROOT)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
