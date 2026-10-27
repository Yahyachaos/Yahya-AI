#!/usr/bin/env python3
"""Execute the actual MainActivity provider body.input serialization seam.

Compiles the exact Java body-input assembly statements lifted from the live
MainActivity with its nested Message model, actual ConversationIntelligenceV78,
and actual CelinePersonaMode. Minimal JSONObject/JSONArray recorders replace
only Android's unavailable JSON runtime, not the app's serialization logic.
Source assertions check UI-visible event marking, direct-only parser dispatch
and paired app-owned acknowledgements. Not a cloud-service or device proof.
"""
from __future__ import annotations

import json
import pathlib
import re
import subprocess
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/de/yahya/ai/MainActivity.java"
PERSONA = ROOT / "app/src/main/java/de/yahya/ai/CelinePersonaMode.java"
CONVERSATION = ROOT / "app/src/main/java/de/yahya/ai/ConversationIntelligenceV78.java"
VECTORS = ROOT / "ci/evidence/CELINE_PERSONA_HISTORY_PROJECTION_ACCEPTANCE.json"


def java_string(value: str) -> str:
    return json.dumps(value, ensure_ascii=True)


def check_wiring(source: str) -> tuple[str, str]:
    compact = "".join(source.split())
    required = (
        "if(handlePersonaCommand(text,true))return;",
        "personaMode.apply(CelinePersonaMode.InputChannel.DIRECT_USER,text)",
        "if(!command.commandConsumed())returnfalse;",
        "if(markSubmittedUser)",
        "messages.set(last,newMessage(visible.role,visible.content,true))",
        "addPersonaAssistant(",
        'addBubble("Celin",t,false,true)',
        "if(!m.personaControl)providerHistory.add(m)",
        "for(Messagem:providerHistory)contextTurns.add(",
        "for(inti=start;i<providerHistory.size();i++)",
        'o.put("role",m.role)',
        'o.put("content",m.content)',
        'body.put("input",arr)',
    )
    for needle in required:
        if needle not in compact:
            raise AssertionError("live provider-wiring gap: " + needle)
    submit = compact.split("privatevoidsubmit(Stringtext)", 1)[1].split(
        "privateStringcallOpenAI(", 1
    )[0]
    dispatch = submit.find("if(handlePersonaCommand(text,true))return;")
    if dispatch < 0 or submit.find("if(handleLocalCommand(text))") < dispatch:
        raise AssertionError("commands must be consumed before local tools")
    if submit.find("goalTaskRuntime.observeUserText(text)") < dispatch:
        raise AssertionError("commands must be consumed before G1 goals")
    handler = compact.split("privatebooleanhandlePersonaCommand(Stringtext,booleanmarkSubmittedUser)", 1)[1].split(
        "privatevoidsubmit(Stringtext)", 1
    )[0]
    if handler.find("if(!command.commandConsumed())returnfalse;") > handler.find(
        "messages.set(last,newMessage(visible.role,visible.content,true))"
    ):
        raise AssertionError("unconsumed text must never be marked as a control")
    if handler.count("addPersonaAssistant(") != 4 or "addAssistant(" in handler:
        raise AssertionError("persona acknowledgement must be control-marked")
    method = source.split("private String callOpenAI(", 1)[1].split(
        "private void learnWithAI(", 1
    )[0]
    start_marker, end_marker = "JSONArray arr=new JSONArray();", 'body.put("input",arr);'
    if method.count(start_marker) != 1 or method.count(end_marker) != 1:
        raise AssertionError("cannot identify actual cloud body.input code")
    snippet = method[method.index(start_marker) : method.index(end_marker) + len(end_marker)]
    model_start = source.find("private static class Message{")
    if model_start < 0:
        raise AssertionError("cannot locate actual MainActivity.Message model")
    model_end = source.find("\n    }", model_start)
    if model_end < 0:
        raise AssertionError("cannot locate complete MainActivity.Message model")
    return snippet, source[model_start:model_end + len("\n    }")]


def make_harness(snippet: str, message_model: str, cases: list[dict]) -> str:
    runs: list[str] = []
    for case in cases:
        name = java_string(case["id"])
        runs.extend([
            "{",
            "  Harness app=new Harness();",
            "  CelinePersonaMode mode=new CelinePersonaMode();",
            "  boolean priorConsumed=false;",
            "  String lastUser=\"\";",
        ])
        for event in case["events"]:
            role = event["role"]
            text = java_string(event["text"])
            channel = event.get("channel", "DIRECT_USER" if role == "user" else "OTHER")
            if channel not in {"DIRECT_USER", "MODEL_OUTPUT", "TOOL_OUTPUT", "OTHER"}:
                raise ValueError("invalid input channel")
            if role not in {"user", "assistant", "tool"}:
                raise ValueError("invalid provider role")
            if channel == "DIRECT_USER" and role != "user":
                raise ValueError("nonuser direct command")
            expected = str(bool(event.get("consumed", False))).lower()
            ack = str(bool(event.get("ack", False))).lower()
            runs.extend([
                "{",
                "  CelinePersonaMode.CommandResult result=mode.apply("
                f"CelinePersonaMode.InputChannel.{channel},{text});",
                f"  verify(result.commandConsumed()=={expected},{name}+\": dispatcher mismatch\");",
                f"  if({ack})verify(priorConsumed,{name}+\": orphan acknowledgement\");",
                f"  app.messages.add(new Message({java_string(role)},{text},result.commandConsumed()||{ack}));",
                f"  if({java_string(role)}.equals(\"user\"))lastUser={text};",
                f"  priorConsumed=result.commandConsumed()&&{java_string(role)}.equals(\"user\");",
                "}",
            ])
        expected_provider = case["expected_provider"]
        runs.extend([
            "  int visibleBefore=app.messages.size();",
            "  JSONArray actual=app.toProviderBodyInput(lastUser);",
            f"  verify(actual.items.size()=={len(expected_provider)},{name}+\": body.input length\");",
            "  verify(app.messages.size()==visibleBefore,"
            + name + "+\": visible chat mutated\");",
        ])
        for i, (role, text) in enumerate(expected_provider):
            runs.extend([
                f"  JSONObject row{i}=(JSONObject)actual.items.get({i});",
                f"  verify(row{i}.values.get(\"role\").equals({java_string(role)})&&"
                f"row{i}.values.get(\"content\").equals({java_string(text)}),"
                f"{name}+\": wrong serialized row {i}\");",
            ])
        runs.extend([
            f"  verify(mode.isActive()=={str(bool(case['active'])).lower()},{name}+\": persona active mismatch\");",
            f"  verify(mode.intensity()=={int(case['intensity'])},{name}+\": persona intensity mismatch\");",
            "  passed++;",
            "}",
        ])
    return (
        "package de.yahya.ai;\n"
        "import java.util.*;\n"
        "public final class Harness {\n"
        "  private final List<Message> messages=new ArrayList<>();\n"
        f"  {message_model}\n"
        "  static final class JSONObject {\n"
        "    final Map<String,Object> values=new LinkedHashMap<>();\n"
        "    JSONObject put(String key,Object value){values.put(key,value);return this;}\n"
        "  }\n"
        "  static final class JSONArray {\n"
        "    final List<Object> items=new ArrayList<>();\n"
        "    JSONArray put(Object value){items.add(value);return this;}\n"
        "  }\n"
        "  JSONArray toProviderBodyInput(String userText){\n"
        "    JSONObject body=new JSONObject();\n"
        f"    {snippet}\n"
        "    return (JSONArray)body.values.get(\"input\");\n"
        "  }\n"
        "  static void verify(boolean yes,String why){if(!yes)throw new AssertionError(why);}\n"
        "  public static void main(String[] args){int passed=0;\n"
        + "\n".join(runs)
        + "\nSystem.out.println(\"CORE_PROVIDER_BODY_INPUT_SERIALIZATION_PASS cases=\"+passed);\n"
        + "  }\n}\n"
    )



def stress_cases() -> list[dict]:
    """Long histories reveal context-window pollution masked by short vectors.

    Actual MainActivity body.input must trim AFTER excluding consumed direct
    commands and their own app acknowledgements. Ordinary texts are retained;
    no cloud, UI, Room or Android writer ownership is claimed.
    """
    specs = (
        ("long-normal-context-retains-twenty", 64, 20, False, False),
        ("long-followup-retains-twenty-eight", 64, 28, True, False),
        ("large-turns-keep-minimum-six", 12, 6, False, True),
    )
    out: list[dict] = []
    for name, count, keep, followup, large in specs:
        events: list[dict] = []
        ordinary: list[str] = []
        for index in range(count):
            # Churn control+ACK history repeatedly; none may consume cloud
            # context budget or evict normal user messages.
            if index % 7 == 0:
                events.extend((
                    {"role": "user", "text": "Nutte 3", "consumed": True},
                    {"role": "assistant", "text": "Persona-Modus aktiv", "ack": True},
                    {"role": "user", "text": "Normalmodus", "consumed": True},
                    {"role": "assistant", "text": "Normalmodus aktiv", "ack": True},
                ))
            value = (f"Normaler Kontextabschnitt {index:03d} mit Wert {index:03d}"
                     if not large
                     else f"Langtext {index:02d} " + "x" * 1900)
            events.append({"role": "user", "text": value})
            ordinary.append(value)
        if followup:
            events.append({"role": "user", "text": "weiter"})
            ordinary.append("weiter")
        out.append({
            "id": name, "events": events,
            "expected_provider": [["user", x] for x in ordinary[-keep:]],
            "active": False, "intensity": 0,
        })
    return out


def run_extra_harness(folder: pathlib.Path, snippet: str,
                      message_model: str, cases: list[dict]) -> subprocess.CompletedProcess:
    folder.mkdir(parents=True, exist_ok=True)
    harness = folder / "Harness.java"
    harness.write_text(make_harness(snippet, message_model, cases), encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(folder),
                    str(PERSONA), str(CONVERSATION), str(harness)], check=True)
    return subprocess.run(["java", "-cp", str(folder), "de.yahya.ai.Harness"],
                          text=True, capture_output=True, check=False)


def main() -> int:
    source = MAIN.read_text(encoding="utf-8")
    snippet, message_model = check_wiring(source)
    data = json.loads(VECTORS.read_text(encoding="utf-8"))
    if data.get("schema") != "celine-persona-history-projection/v1":
        raise ValueError("wrong acceptance schema")
    cases = data["cases"]
    if len(cases) != 10 or len(set(c["id"] for c in cases)) != len(cases):
        raise ValueError("canonical provider acceptance vectors incomplete")
    with tempfile.TemporaryDirectory(prefix="celine-live-provider-") as temp:
        folder = pathlib.Path(temp)
        harness = folder / "Harness.java"
        harness.write_text(make_harness(snippet, message_model, cases), encoding="utf-8")
        subprocess.run(
            ["javac", "-encoding", "UTF-8", "-d", str(folder),
             str(PERSONA), str(CONVERSATION), str(harness)],
            check=True,
        )
        subprocess.run(["java", "-cp", str(folder), "de.yahya.ai.Harness"], check=True)
    # Reuse exact MainActivity Java serialization, real V78 context selection and
    # the promoted parser under deliberately crowded conversation histories.
    stress = stress_cases()
    with tempfile.TemporaryDirectory(prefix="celine-provider-context-stress-") as temp:
        folder = pathlib.Path(temp)
        good = run_extra_harness(folder / "positive", snippet, message_model, stress)
        if good.returncode != 0:
            raise AssertionError("long-history provider isolation FAIL: " + good.stderr[-1800:])
        print(good.stdout.strip())
        # Negative control: deliberately remove ONLY the existing event-marker
        # filter. Our exact same executable assertions MUST reject the leak.
        guard = "if(!m.personaControl)providerHistory.add(m);"
        if snippet.count(guard) != 1:
            raise AssertionError("cannot isolate unique provider-history filter")
        mutant = snippet.replace(guard, "providerHistory.add(m);", 1)
        bad = run_extra_harness(folder / "filter-bypass", mutant, message_model, stress)
        if bad.returncode == 0 or not re.search(
                r"body\\.input length|wrong serialized row", bad.stderr):
            raise AssertionError("negative filter-bypass mutation was not rejected: "
                                 + bad.stderr[-1800:])
        print("CORE_PROVIDER_HISTORY_FILTER_MUTATION_REJECTED_PASS")
    print("SCOPE: actual MainActivity.body.input Java code with JSON recorders; no network/device assertions")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
