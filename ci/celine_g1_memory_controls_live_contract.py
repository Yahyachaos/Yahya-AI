#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
main = root / "app/src/main/java/de/yahya/ai/MainActivity.java"
controls = root / "app/src/main/java/de/yahya/ai/CelineMemoryControls.java"
store = root / "app/src/main/java/de/yahya/ai/CelineStructuredMemory.java"

for path in (main, controls, store):
    assert path.is_file(), f"missing G1.5 memory-control file: {path}"

main_text = main.read_text(encoding="utf-8")
controls_text = controls.read_text(encoding="utf-8")
store_text = store.read_text(encoding="utf-8")

assert "CelineMemoryControls.show(this,memory)" in main_text, "memory settings are not routed to G1.5 controls"

for token in [
    "memory.consolidateNow()", "memory.inspectItems()", "memory.protectedStorageAvailable()",
    "memory.correct(item.id, replacement)", "memory.forget(item.id)",
    'setPositiveButton("Korrigieren"', 'setNeutralButton("Löschen"',
    "Geschützter Speicher: aktiv",
]:
    assert token in controls_text, f"missing G1.5 user memory control: {token}"

for token in [
    "inspectItems()", "correct(String memoryId", "forget(String memoryId",
    "protectedStorageAvailable()",
]:
    assert token in store_text, f"store does not expose G1.5 control primitive: {token}"

assert "memory.inspect();new AlertDialog.Builder" not in main_text, "legacy read-only memory dialog still owns settings UI"

print("celine-g1-memory-controls-live-contract PASS")
