#!/usr/bin/env python3
"""Execute the actual MainActivity cloud body.instructions composition as Java.

Read-only extraction of the existing provider request statement; no Android,
Room, memory, networking or production source changes. The standalone prompt
adapter vectors already pass, but this guards the actual app-owned JSON seam.
"""
from __future__ import annotations

import pathlib
import subprocess
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/de/yahya/ai/MainActivity.java"
PERSONA = ROOT / "app/src/main/java/de/yahya/ai/CelinePersonaMode.java"
ADAPTER = ROOT / "app/src/main/java/de/yahya/ai/CelinePersonaPromptAdapter.java"
COMPOSE = "CelinePersonaPromptAdapter.compose(instructions,personaMode)"
STATEMENT = 'body.put("instructions",' + COMPOSE + ');'


def live_provider_statement() -> str:
    source = MAIN.read_text(encoding="utf-8")
    start = "private String callOpenAI("
    end = "private void learnWithAI("
    if source.count(start) != 1 or source.count(end) != 1:
        raise AssertionError("Cannot isolate live provider request method")
    method = source.split(start, 1)[1].split(end, 1)[0]
    if method.count(STATEMENT) != 1:
        raise AssertionError("Actual MainActivity instructions seam missing/ambiguous")
    if method.index(STATEMENT) > method.index('body.put("input",arr)'):
        raise AssertionError("Instructions composed after the provider input is sent")
    memory = source.split(end, 1)[1].split("private void rememberExplicit(", 1)[0]
    if "CelinePersonaPromptAdapter" in memory:
        raise AssertionError("Persona prompt must not leak into memory extractor")
    return STATEMENT


JAVA = r'''package de.yahya.ai;
public final class ProviderInstructionsHarness {
    private static final class JSONObject {
        String instructions;
        JSONObject put(String key,Object value){
            if(!"instructions".equals(key) || !(value instanceof String))
                throw new AssertionError("Unexpected JSON provider instruction payload");
            instructions=(String)value;
            return this;
        }
    }
    private static String serialize(CelinePersonaMode personaMode,String instructions){
        JSONObject body=new JSONObject();
        /* ACTUAL_PROVIDER_STATEMENT */
        return body.instructions;
    }
    private static void verify(boolean value,String why){
        if(!value)throw new AssertionError(why);
    }
    public static void main(String[] ignored){
        int passed=0;
        CelinePersonaMode mode=new CelinePersonaMode();
        String baseline=new String("CELINE STANDARD: Deutsch.\nG1-Kontext: unveraendert");
        String unicode=new String("Deutsch: ÄÖÜß\nKontext: vertraulich");
        verify(serialize(mode,baseline)==baseline,"OFF must preserve exact source prompt object");
        passed++;
        verify(serialize(mode,unicode)==unicode,"OFF Unicode prompt identity changed");
        passed++;
        mode.apply(CelinePersonaMode.InputChannel.DIRECT_USER,"Nutte 1");
        String one=serialize(mode,baseline);
        verify(mode.isActive() && mode.intensity()==1 &&
               one.startsWith(baseline+"\n\n") &&
               one.endsWith(mode.promptContribution()) && !one.equals(baseline),
               "Active level one must append only bounded persona suffix");
        passed++;
        mode.apply(CelinePersonaMode.InputChannel.DIRECT_USER,"Nutte 3");
        String three=serialize(mode,baseline);
        verify(mode.intensity()==3 && !three.equals(one) &&
               three.startsWith(baseline+"\n\n") &&
               three.endsWith(mode.promptContribution()),
               "Active level three must preserve normal prefix and use current suffix");
        passed++;
        mode.apply(CelinePersonaMode.InputChannel.MODEL_OUTPUT,"Normalmodus");
        verify(mode.isActive() && serialize(mode,baseline).equals(three),
               "Model output may not turn off app-owned persona state");
        passed++;
        mode.apply(CelinePersonaMode.InputChannel.DIRECT_USER,"Normalmodus");
        verify(!mode.isActive() && serialize(mode,baseline)==baseline,
               "Immediate OFF prompt must be exact original normal prompt");
        passed++;
        mode.apply(CelinePersonaMode.InputChannel.DIRECT_USER,"Nutte 2");
        String two=serialize(mode,unicode);
        verify(mode.isActive() && mode.intensity()==2 &&
               two.startsWith(unicode+"\n\n") &&
               two.endsWith(mode.promptContribution()),
               "Active Unicode/G1 normal prompt must remain unchanged");
        passed++;
        mode.reset();
        verify(!mode.isActive() && serialize(mode,unicode)==unicode,
               "App-owned Settings-style reset must restore normal prompt identity");
        passed++;
        mode.apply(CelinePersonaMode.InputChannel.TOOL_OUTPUT,"Nutte 3");
        verify(!mode.isActive() && serialize(mode,baseline)==baseline,
               "Tool output must not activate provider persona");
        passed++;
        System.out.println("CORE_PROVIDER_INSTRUCTIONS_ACTUAL_JAVA_PASS cases="+passed);
    }
}
'''


def execute(folder: pathlib.Path, statement: str) -> subprocess.CompletedProcess[str]:
    folder.mkdir(parents=True, exist_ok=True)
    harness = folder / "ProviderInstructionsHarness.java"
    harness.write_text(JAVA.replace("/* ACTUAL_PROVIDER_STATEMENT */", statement),
                       encoding="utf-8")
    subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(folder),
         str(PERSONA), str(ADAPTER), str(harness)],
        check=True,
    )
    return subprocess.run(
        ["java", "-cp", str(folder), "de.yahya.ai.ProviderInstructionsHarness"],
        text=True, capture_output=True, check=False,
    )


def main() -> int:
    statement = live_provider_statement()
    with tempfile.TemporaryDirectory(prefix="celine-provider-instructions-") as temp:
        folder = pathlib.Path(temp)
        good = execute(folder / "real-source", statement)
        if good.returncode or "CORE_PROVIDER_INSTRUCTIONS_ACTUAL_JAVA_PASS cases=9" not in good.stdout:
            raise AssertionError("Actual body.instructions Java test failed: "
                                 + good.stderr[-1600:] + good.stdout[-800:])
        print(good.stdout.strip(), flush=True)

        bypass = statement.replace(COMPOSE, "instructions")
        altered = statement.replace(COMPOSE,
                                    'CelinePersonaPromptAdapter.compose("MUTATED",personaMode)')
        if bypass == statement or altered == statement:
            raise AssertionError("Required negative mutations did not change Java")
        for name, mutant in (("bypass", bypass), ("base-overwrite", altered)):
            bad = execute(folder / name, mutant)
            if bad.returncode == 0 or "AssertionError" not in bad.stderr:
                raise AssertionError(f"Provider instructions mutant {name} was not rejected")
        print("CORE_PROVIDER_INSTRUCTIONS_MUTATION_REJECTED_PASS variants=2", flush=True)
        print("SCOPE: actual request body.instructions Java, not cloud/model/physical device", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
