#!/usr/bin/env python3
"""Validate promoted Android persona classes with the canonical pure-Java contracts.

This is a focused Core-only test. It does not exercise MainActivity, provider wiring,
persistence, Android UI, memory integration, or room rendering.
"""
from __future__ import annotations

import importlib.util
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[1]
RUNTIME = ROOT / "app/src/main/java/de/yahya/ai"
PROTOTYPES = ROOT / "ci/prototypes"
SOURCES = ("CelinePersonaMode.java", "CelinePersonaPromptAdapter.java")


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

    mode = load_contract("celine_mode_contract", ROOT / "ci/celine_persona_mode_contract_test.py")
    mode.SOURCE = RUNTIME / SOURCES[0]
    if mode.main() != 0:
        raise SystemExit("promoted CelinePersonaMode contract failed")

    adapter = load_contract("celine_prompt_adapter_contract", ROOT / "ci/celine_persona_prompt_adapter_contract_test.py")
    adapter.MODE_SOURCE = RUNTIME / SOURCES[0]
    adapter.ADAPTER_SOURCE = RUNTIME / SOURCES[1]
    if adapter.main() != 0:
        raise SystemExit("promoted CelinePersonaPromptAdapter contract failed")

    print("Promoted Celine persona runtime contracts: PASS (parity + 2 canonical harnesses)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
