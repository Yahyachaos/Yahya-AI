#!/usr/bin/env python3
"""Run the existing Core persona contracts against exact promoted Android sources.

This is a focused Core-only check, not proof of MainActivity/AI provider wiring,
app-owned persistence, device UI behavior, memory integration or room visuals.
"""
from __future__ import annotations

import importlib.util
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[1]
RUNTIME = ROOT / "app/src/main/java/de/yahya/ai"
PROTOTYPES = ROOT / "ci/prototypes"
SOURCES = ("CelinePersonaMode.java", "CelinePersonaPromptAdapter.java")

# Run the actual canonical Java harnesses, including JSON-driven acceptance vectors
# and fail-closed control/dispatch cases, against the promoted runtime Java sources.
# The standalone codec contract is deliberately excluded: that codec is still an
# unpromoted prototype and testing it would not prove Android storage integration.
CONTRACTS = (
    ("celine_persona_mode_contract_test.py", {"SOURCE": SOURCES[0]}),
    ("celine_persona_prompt_adapter_contract_test.py", {
        "MODE_SOURCE": SOURCES[0], "ADAPTER_SOURCE": SOURCES[1]}),
    ("celine_persona_control_character_contract_test.py", {"MODE": SOURCES[0]}),
    ("celine_persona_dispatch_routing_contract_test.py", {"MODE_SOURCE": SOURCES[0]}),
    ("celine_persona_operation_vector_contract_test.py", {"SOURCE": SOURCES[0]}),
    ("celine_persona_prompt_vector_execution_test.py", {
        "MODE": SOURCES[0], "ADAPTER": SOURCES[1]}),
    ("celine_persona_vector_execution_contract_test.py", {"SOURCE": SOURCES[0]}),
    ("celine_persona_sequence_contract_test.py", {
        "MODE_SOURCE": SOURCES[0], "ADAPTER_SOURCE": SOURCES[1]}),
    ("celine_persona_concurrency_contract_test.py", {
        "MODE_SOURCE": SOURCES[0], "ADAPTER_SOURCE": SOURCES[1]}),
    ("celine_persona_untrusted_channel_isolation_contract_test.py", {
        "MODE_SOURCE": SOURCES[0], "ADAPTER_SOURCE": SOURCES[1]}),
)


def load_contract(name: str, path: pathlib.Path):
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"cannot load canonical contract: {path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def assert_live_persona_integration_source_contract() -> None:
    """Read-only source gate for the already-borrowed G1 MainActivity integration.

    Pure persona Java tests cannot prove that the actual app routes direct
    commands before G1/tools or includes the persona contribution in the
    provider request. This catches silent source-wiring regressions; it is NOT
    evidence of a live cloud model response or physical-device acceptance.
    No ownership of MainActivity or G1 sources is acquired by this read.
    """
    source = (RUNTIME / "MainActivity.java").read_text(encoding="utf-8")
    compact = "".join(source.split())
    required = {
        "direct user channel only":
            "personaMode.apply(CelinePersonaMode.InputChannel.DIRECT_USER,text)",
        "encrypted persona restore at launch":
            "personaStateStore.restoreInto(personaMode)",
        "durable activation save":
            "personaStateStore.save(personaMode)",
        "explicit persisted-state clear":
            "personaStateStore.clear()",
        "actual provider instructions composed with app-owned persona":
            'body.put("instructions",CelinePersonaPromptAdapter.compose(instructions,personaMode))',
    }
    for description, expression in required.items():
        if expression not in compact:
            raise SystemExit(f"MainActivity persona source-wiring FAIL: {description}")
    try:
        submit = compact.split("privatevoidsubmit(Stringtext)", 1)[1].split(
            "privateStringcallOpenAI(", 1)[0]
        learner = compact.split("privatevoidlearnWithAI(", 1)[1].split(
            "privatevoidrememberExplicit(", 1)[0]
    except IndexError as exc:
        raise SystemExit("MainActivity source-wiring FAIL: required method boundary missing") from exc
    persona_first = submit.find("if(handlePersonaCommand(text,true))return;")
    tool_next = submit.find("if(handleLocalCommand(text))")
    goal_next = submit.find("goalTaskRuntime.observeUserText(text)")
    if persona_first < 0 or tool_next < persona_first or goal_next < persona_first:
        raise SystemExit("MainActivity source-wiring FAIL: persona dispatch no longer precedes tools/G1")
    if "CelinePersonaPromptAdapter" in learner or "personaMode.promptContribution()" in learner:
        raise SystemExit("MainActivity source-wiring FAIL: model style leaked into memory extractor")
    print("CORE_PERSONA_LIVE_SOURCE_WIRING_PASS: direct-only early dispatch, encrypted state, provider instructions, memory-extractor isolation (source-level only)")


def assert_settings_only_exit_source_contract() -> None:
    """Read-only guard for the explicitly exposed Settings-only normal-mode exit.

    A Settings tap must not forge a submitted direct-user command turn; the
    encrypted store must be cleared and only the genuine app ACK may appear in
    visible chat (and remain excluded from provider-history serialization).
    This is a source contract, NOT a physical Android Settings UI test.
    """
    source = (RUNTIME / "MainActivity.java").read_text(encoding="utf-8")
    compact = "".join(source.split())

    def section(start: str, end: str) -> str:
        if compact.count(start) != 1 or compact.count(end) != 1:
            raise AssertionError("Settings-only persona source boundary missing")
        return compact.split(start, 1)[1].split(end, 1)[0]

    def verify(current: str) -> None:
        blocks = {
            "settings": current.split("privatevoidshowSettings()", 1)[1].split(
                "privatevoidshowPersonaSettings()", 1)[0],
            "persona": current.split("privatevoidshowPersonaSettings()", 1)[1].split(
                "privatevoidshowAbout()", 1)[0],
            "wrapper": current.split("privatebooleanhandlePersonaCommand(Stringtext)", 1)[1].split(
                "privatebooleanhandlePersonaCommand(Stringtext,booleanmarkSubmittedUser)", 1)[0],
            "dispatch": current.split("privatebooleanhandlePersonaCommand(Stringtext,booleanmarkSubmittedUser)", 1)[1].split(
                "privatevoidsubmit(Stringtext)", 1)[0],
        }
        required = (
            ("settings", "case12:showPersonaSettings();"),
            ("persona", "if(!personaMode.isActive())"),
            ("persona", '.setPositiveButton("Normalmodus",(d,w)->handlePersonaCommand("Normalmodus"))'),
            ("wrapper", "returnhandlePersonaCommand(text,false);"),
            ("dispatch", "if(markSubmittedUser)"),
            ("dispatch", "if(!command.commandConsumed())returnfalse;"),
            ("dispatch", "personaStateStore.clear()"),
            ("dispatch", "addPersonaAssistant(cleared?"),
        )
        for block, token in required:
            if token not in blocks[block]:
                raise AssertionError(f"Settings-only exit lost {block}: {token}")
        if any(token in blocks["persona"] for token in ("submit(", "addUser(", "messages.add(")):
            raise AssertionError("Settings path fabricated a submitted direct-user turn")
        for token in (
            'addBubble("Celin",t,false,true)',
            "if(!m.personaControl)providerHistory.add(m)",
            "messages.set(last,newMessage(visible.role,visible.content,true))",
        ):
            if token not in current:
                raise AssertionError("Settings ACK/provider exclusion contract lost: " + token)

    verify(compact)
    # Prove the guard rejects two plausible mistakes in the protected Java
    # integration without writing or patching that Room-owned source.
    mutations = (
        ("settings-created direct control turn",
         "returnhandlePersonaCommand(text,false);",
         "returnhandlePersonaCommand(text,true);"),
        ("Settings button bypasses normal-mode handler",
         '.setPositiveButton("Normalmodus",(d,w)->handlePersonaCommand("Normalmodus"))',
         '.setPositiveButton("Normalmodus",(d,w)->personaMode.reset())'),
    )
    for description, before, after in mutations:
        if compact.count(before) != 1:
            raise AssertionError("Cannot anchor Settings mutation: " + description)
        try:
            verify(compact.replace(before, after, 1))
        except AssertionError:
            continue
        raise AssertionError("Settings guard incorrectly accepted mutation: " + description)
    print("CORE_PERSONA_SETTINGS_ONLY_EXIT_SOURCE_PASS: Settings route, non-forged user turn, encrypted exit, marked ACK/provider exclusion, mutation rejected (source-only)")

def main() -> int:
    for name in SOURCES:
        prototype = PROTOTYPES / name
        runtime = RUNTIME / name
        if not prototype.is_file() or not runtime.is_file():
            raise SystemExit(f"missing promoted/prototype source for {name}")
        if prototype.read_bytes() != runtime.read_bytes():
            raise SystemExit(f"promoted source drift for {name}; review the approved integration contract")

    assert_live_persona_integration_source_contract()
    assert_settings_only_exit_source_contract()

    for index, (filename, bindings) in enumerate(CONTRACTS):
        name = f"celine_promoted_contract_{index}"
        contract = load_contract(name, ROOT / "ci" / filename)
        for variable, source in bindings.items():
            if not hasattr(contract, variable):
                raise SystemExit(f"canonical contract lost {variable}: {filename}")
            setattr(contract, variable, RUNTIME / source)
        if contract.main() != 0:
            raise SystemExit(f"promoted contract failed: {filename}")

    # The Android-key provision path still needs a real device/emulator test.
    # This independent JVM harness exercises actual JCA AES-GCM and the promoted
    # Android store class with narrow framework stubs and an injected test key.
    store_contract = load_contract(
        "celine_persona_state_store_contract",
        ROOT / "ci" / "celine_persona_state_store_contract_test.py",
    )
    if store_contract.main() != 0:
        raise SystemExit("promoted state-store contract failed")

    print(f"Promoted Celine persona runtime contracts: PASS (byte parity + {len(CONTRACTS)} canonical Java harnesses + encrypted state-store contract)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
