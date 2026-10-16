#!/usr/bin/env python3
"""Pure-Java deterministic contract for the isolated versioned persona-state codec.

No Android storage, app wiring, Room, model provider or network access is used.
This is only a future app-owned persistence integration seam under issue #112.
"""
from __future__ import annotations

from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
MODE = ROOT / "ci/prototypes/CelinePersonaMode.java"
CODEC = ROOT / "ci/prototypes/CelinePersonaStateCodec.java"

HARNESS = r'''
import de.yahya.ai.CelinePersonaMode;
import de.yahya.ai.CelinePersonaStateCodec;
public final class CelinePersonaStateCodecProbe {
    static void require(boolean value, String description) {
        if (!value) throw new AssertionError(description);
    }
    static void activeLevel(int level) {
        CelinePersonaMode original = new CelinePersonaMode();
        require(original.restorePersistedState(true, level), "valid setup");
        String serialized = CelinePersonaStateCodec.encode(original);
        require(serialized.equals("cpm1|1|"+level), "serialized active level " + level);
        CelinePersonaMode restored = new CelinePersonaMode();
        require(CelinePersonaStateCodec.decodeInto(restored, serialized), "decode active " + level);
        require(restored.isActive() && restored.intensity() == level, "active state " + level);
        require(restored.activationSource()==CelinePersonaMode.ActivationSource.PERSISTED_STATE, "restored source " + level);
        require(!restored.promptContribution().isEmpty(), "active contribution " + level);
    }
    public static void main(String[] args) {
        CelinePersonaMode mode = new CelinePersonaMode();
        require(CelinePersonaStateCodec.encode(mode).equals("cpm1|0|0"), "initial off");
        require(CelinePersonaStateCodec.decodeInto(mode, "cpm1|0|0"), "valid inactive decode");
        require(!mode.isActive() && mode.promptContribution().isEmpty(), "off remains off");
        for(int i=1;i<=3;i++) activeLevel(i);
        String[] invalid={null,"","cpm0|1|2","cpm2|1|2","cpm1|1|0","cpm1|1|4",
                          "cpm1|1|9","cpm1|1|02","cpm1|1|2|extra","cpm1|1|+2",
                          "cpm1|true|2","cpm1|0|2","cpm1|1|\n", "cpm1|1|2 ",
                          " cpm1|1|2","cpm1|1|２"};
        for(int i=0;i<invalid.length;i++) {
            require(mode.restorePersistedState(true, 3),"setup " + i);
            require(!CelinePersonaStateCodec.decodeInto(mode, invalid[i]), "invalid must reject #"+i);
            require(!mode.isActive() && mode.intensity()==0 && mode.promptContribution().isEmpty(),"invalid must reset #"+i);
            require(mode.activationSource()==CelinePersonaMode.ActivationSource.NONE,"invalid source #"+i);
            require(CelinePersonaStateCodec.encode(mode).equals("cpm1|0|0"),"off after reject #"+i);
        }
        System.out.println("Celine persona persistence codec: PASS (3 roundtrips, 16 corrupt payloads)");
    }
}
'''


def main() -> int:
    with tempfile.TemporaryDirectory(prefix="celine-persona-codec-") as tmp:
        directory = Path(tmp)
        source = directory / "CelinePersonaStateCodecProbe.java"
        source.write_text(HARNESS, encoding="utf-8")
        classes = directory / "classes"
        classes.mkdir()
        subprocess.run(["javac", "-d", str(classes), str(MODE), str(CODEC), str(source)],
                       check=True, cwd=ROOT)
        subprocess.run(["java", "-cp", str(classes), "CelinePersonaStateCodecProbe"],
                       check=True, cwd=ROOT)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
