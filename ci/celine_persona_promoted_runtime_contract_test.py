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


def main() -> int:
    for name in SOURCES:
        prototype = PROTOTYPES / name
        runtime = RUNTIME / name
        if not prototype.is_file() or not runtime.is_file():
            raise SystemExit(f"missing promoted/prototype source for {name}")
        if prototype.read_bytes() != runtime.read_bytes():
            raise SystemExit(f"promoted source drift for {name}; review the approved integration contract")

    for index, (filename, bindings) in enumerate(CONTRACTS):
        name = f"celine_promoted_contract_{index}"
        contract = load_contract(name, ROOT / "ci" / filename)
        for variable, source in bindings.items():
            if not hasattr(contract, variable):
                raise SystemExit(f"canonical contract lost {variable}: {filename}")
            setattr(contract, variable, RUNTIME / source)
        if contract.main() != 0:
            raise SystemExit(f"promoted contract failed: {filename}")

    print(f"Promoted Celine persona runtime contracts: PASS (byte parity + {len(CONTRACTS)} canonical Java harnesses)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
