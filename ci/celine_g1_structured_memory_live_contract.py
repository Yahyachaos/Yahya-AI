#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
main = root / "app/src/main/java/de/yahya/ai/MainActivity.java"
text = main.read_text(encoding="utf-8")

required = [
    "CelineStructuredMemory memory",
    "memory=new CelineStructuredMemory(prefs)",
    "memory.promptMemory(userText,6)",
    "memory.rememberInferred(learned,\"cloud_memory_extractor\")",
    "memory.rememberExplicit(m)",
    "memory.inspect()",
    "memory.forgetAll()",
]
for token in required:
    assert token in text, f"missing G1.2 live-memory integration: {token}"

for forbidden in [
    'prefs.getString("memory"',
    'prefs.edit().remove("memory")',
    "if(c.length()>7000)",
    "appendMemory(learned)",
]:
    assert forbidden not in text, f"legacy flat memory path still active in MainActivity: {forbidden}"

print("celine-g1-structured-memory-live-contract PASS")
