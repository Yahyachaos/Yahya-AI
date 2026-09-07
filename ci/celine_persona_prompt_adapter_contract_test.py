#!/usr/bin/env python3
"""Deterministic pure-Java contract for the adult persona prompt-composition seam.

The test deliberately avoids Android UI, model providers, memory stores, tools, room/avatar,
network, Gradle, and central app wiring. It validates the ownership-safe adapter against the
same isolated CelinePersonaMode prototype used by issue #112.
"""

from __future__ import annotations

import json
import pathlib
import subprocess
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
MODE_SOURCE = ROOT / "ci/prototypes/CelinePersonaMode.java"
ADAPTER_SOURCE = ROOT / "ci/prototypes/CelinePersonaPromptAdapter.java"
CASES = ROOT / "ci/evidence/CELINE_ADULT_PERSONA_PROMPT_ADAPTER_ACCEPTANCE.json"

REQUIRED_CASE_IDS = {
    "inactive-normal-prompt-transparent",
    "active-style-layer-appended",
    "deactivate-next-prompt-transparent",
    "empty-base-active-contribution-only",
}

HARNESS = r'''
import de.yahya.ai.CelinePersonaMode;
import de.yahya.ai.CelinePersonaPromptAdapter;

public final class CelinePersonaPromptAdapterContractTest {
    public static void main(String[] args) {
        testInactiveTransparency();
        testActiveLayering();
        testDeactivationTransparency();
        testEmptyBase();
        System.out.println("CelinePersonaPromptAdapter contract: PASS");
    }

    private static void testInactiveTransparency() {
        String normal = new String("NORMAL-CELINE-PROMPT\nmemory=context-owned\nconversation=unchanged");
        CelinePersonaMode mode = new CelinePersonaMode();
        String composed = CelinePersonaPromptAdapter.compose(normal, mode);
        check(mode.promptContribution().isEmpty(), "inactive persona contribution must be empty");
        check(composed == normal, "inactive compose must return exact base object");
        check(composed.equals("NORMAL-CELINE-PROMPT\nmemory=context-owned\nconversation=unchanged"),
                "inactive compose must preserve exact base content");
    }

    private static void testActiveLayering() {
        String normal = new String("NORMAL-CELINE-PROMPT\nmemory=context-owned\nconversation=unchanged");
        String original = normal;
        CelinePersonaMode mode = new CelinePersonaMode();
        mode.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte 3");
        String contribution = mode.promptContribution();
        String composed = CelinePersonaPromptAdapter.compose(normal, mode);
        check(!contribution.isEmpty(), "active contribution must be nonempty");
        check(composed != normal, "active compose must produce layered output");
        check(composed.startsWith(normal + "\n\n"), "active compose must preserve base prefix");
        check(composed.endsWith(contribution), "active compose suffix must be exact persona contribution");
        check(normal == original, "adapter must not replace base input reference");
        check(normal.equals("NORMAL-CELINE-PROMPT\nmemory=context-owned\nconversation=unchanged"),
                "adapter must not mutate base input content");
    }

    private static void testDeactivationTransparency() {
        String normal = new String("NORMAL-CELINE-PROMPT\nconversation=unchanged");
        CelinePersonaMode mode = new CelinePersonaMode();
        mode.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte 2");
        String active = CelinePersonaPromptAdapter.compose(normal, mode);
        check(active != normal, "active compose should be layered before deactivation");
        mode.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte aus");
        String after = CelinePersonaPromptAdapter.compose(normal, mode);
        check(mode.promptContribution().isEmpty(), "deactivation must clear contribution immediately");
        check(after == normal, "next compose after deactivation must return exact base object");
        check(after.equals("NORMAL-CELINE-PROMPT\nconversation=unchanged"),
                "next compose after deactivation must preserve exact base content");
    }

    private static void testEmptyBase() {
        CelinePersonaMode mode = new CelinePersonaMode();
        mode.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte 1");
        String contribution = mode.promptContribution();
        String composed = CelinePersonaPromptAdapter.compose("", mode);
        check(composed.equals(contribution), "empty base active output must equal contribution");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
'''


def main() -> int:
    for path in (MODE_SOURCE, ADAPTER_SOURCE, CASES):
        if not path.is_file():
            raise SystemExit(f"missing contract input: {path}")

    data = json.loads(CASES.read_text(encoding="utf-8"))
    ids = {case["id"] for case in data.get("cases", [])}
    missing = sorted(REQUIRED_CASE_IDS - ids)
    if missing:
        raise SystemExit(f"prompt-adapter acceptance vector drift; missing cases: {missing}")

    with tempfile.TemporaryDirectory(prefix="celine-persona-prompt-adapter-") as tmp:
        tmp_path = pathlib.Path(tmp)
        harness = tmp_path / "CelinePersonaPromptAdapterContractTest.java"
        classes = tmp_path / "classes"
        classes.mkdir()
        harness.write_text(HARNESS, encoding="utf-8")

        subprocess.run(
            ["javac", "-d", str(classes), str(MODE_SOURCE), str(ADAPTER_SOURCE), str(harness)],
            check=True,
            cwd=ROOT,
        )
        subprocess.run(
            ["java", "-cp", str(classes), "CelinePersonaPromptAdapterContractTest"],
            check=True,
            cwd=ROOT,
        )

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
