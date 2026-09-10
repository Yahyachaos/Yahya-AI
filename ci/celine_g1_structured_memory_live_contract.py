#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
main = root / "app/src/main/java/de/yahya/ai/MainActivity.java"
broker = root / "app/src/main/java/de/yahya/ai/CelineContextBrokerG14.java"
controls = root / "app/src/main/java/de/yahya/ai/CelineMemoryControls.java"
main_text = main.read_text(encoding="utf-8")
broker_text = broker.read_text(encoding="utf-8")
controls_text = controls.read_text(encoding="utf-8")

# G1.2 remains live through the later G1.4/G1.5 owners: MainActivity owns the
# structured-memory store, the broker owns reasoning-context retrieval, and the
# G1.5 controls own inspect/correct/forget UI. Do not require retired direct
# promptMemory()/inspect() assembly in MainActivity.
for token in [
    "CelineStructuredMemory memory",
    "memory=new CelineStructuredMemory(prefs)",
    "new CelineContextBrokerG14(memory,goalTaskRuntime.graph(),goalTaskRuntime.graph())",
    "memory.rememberInferred(learned,\"cloud_memory_extractor\")",
    "memory.rememberExplicit(m)",
    "CelineMemoryControls.show(this,memory)",
    "memory.forgetAll()",
]:
    assert token in main_text, f"missing G1.2 live-memory integration: {token}"

for token in [
    "memory.retrieve(request, MEMORY_CANDIDATES)",
    "item.isExpired(now)",
    "fromMemory(item.knowledgeState)",
]:
    assert token in broker_text, f"G1.4 broker no longer preserves G1.2 memory semantics: {token}"

for token in [
    "memory.inspectItems()",
    "memory.correct(item.id, replacement)",
    "memory.forget(item.id)",
]:
    assert token in controls_text, f"G1.5 controls no longer preserve inspect/correct/forget semantics: {token}"

for forbidden in [
    'prefs.getString("memory"',
    'prefs.edit().remove("memory")',
    "if(c.length()>7000)",
    "appendMemory(learned)",
    "memory.promptMemory(userText,6)",
]:
    assert forbidden not in main_text, f"legacy flat/direct memory path still active in MainActivity: {forbidden}"

print("celine-g1-structured-memory-live-contract PASS")
