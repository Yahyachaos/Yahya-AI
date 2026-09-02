#!/usr/bin/env python3
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
brain = root / "app/src/main/java/de/yahya/ai/CelineBrain.java"
engine = root / "app/src/main/java/de/yahya/ai/CelineMemoryEngine.java"
store = root / "app/src/main/java/de/yahya/ai/CelineStructuredMemory.java"

for path in (brain, engine, store):
    assert path.is_file(), f"missing G1.2 file: {path}"

brain_text = brain.read_text(encoding="utf-8")
engine_text = engine.read_text(encoding="utf-8")
store_text = store.read_text(encoding="utf-8")

for token in [
    "SEMANTIC", "PROFILE", "PREFERENCE", "EPISODIC", "DECISION_CORRECTION",
    "OPEN_GOAL_TASK", "PROCEDURAL_SKILL", "TEMPORARY", "LEGACY",
    "privacyScope", "createdAtEpochMs", "updatedAtEpochMs", "expiresAtEpochMs",
    "supersedesId", "conflictWithId",
]:
    assert token in brain_text, f"missing structured-memory contract token: {token}"

for token in [
    "findSupersessionTarget", "activeSnapshot", "isExpired", "relevanceScore",
    "case FORGET:",
]:
    assert token in engine_text, f"missing memory-engine behavior: {token}"

for token in [
    'STORE_SCHEMA = 1',
    'KEY_LEGACY = "memory"',
    'KEY_LEGACY_BACKUP',
    'legacy_shared_preferences',
    'migrateLegacyOnce',
    'rememberExplicit',
    'rememberCorrection',
    'rememberInferred',
    'promptMemory',
    'forgetAll',
]:
    assert token in store_text, f"missing structured-store behavior: {token}"

assert "7000" not in store_text, "legacy flat 7000-character truncation leaked into structured store"

probe = r"""
package de.yahya.ai;

import java.util.Arrays;

public final class G12MemoryProbe {
    private static CelineMemoryItem item(
            String id, CelineMemoryType type, String summary,
            CelineMemoryItem.KnowledgeState state, long expires, String supersedes) {
        long now = System.currentTimeMillis();
        return new CelineMemoryItem(
                id, type, summary, "test", state,
                state == CelineMemoryItem.KnowledgeState.EXPLICIT ? 1.0d : 0.6d,
                0.8d, CelineMemoryPrivacy.LOCAL_PRIVATE,
                now, now, expires, supersedes, "");
    }

    public static void main(String[] args) {
        CelineMemoryItem oldDrink = item(
                "old", CelineMemoryType.PREFERENCE, "Lieblingsgetränk Kaffee",
                CelineMemoryItem.KnowledgeState.INFERRED, 0L, "");
        CelineMemoryItem unrelated = item(
                "quad", CelineMemoryType.SEMANTIC, "Das Quad ist grün",
                CelineMemoryItem.KnowledgeState.EXPLICIT, 0L, "");
        CelineMemoryEngine memory = new CelineMemoryEngine(Arrays.asList(oldDrink, unrelated));

        CelineMemoryItem correction = item(
                "new", CelineMemoryType.DECISION_CORRECTION, "Lieblingsgetränk Tee",
                CelineMemoryItem.KnowledgeState.EXPLICIT, 0L, "");
        String target = memory.findSupersessionTarget(correction);
        if (!"old".equals(target)) throw new AssertionError("correction target=" + target);
        memory.remember(new CelineMemoryMutation(
                CelineMemoryMutation.Operation.SUPERSEDE, target, correction));

        CelineMemorySlice drink = memory.retrieve(
                new CelineBrainRequest("r1", "Was ist mein Lieblingsgetränk?", System.currentTimeMillis()), 4);
        if (drink.items.size() != 1 || !"new".equals(drink.items.get(0).id)) {
            throw new AssertionError("supersession/relevance failed");
        }

        CelineMemoryItem expired = item(
                "expired", CelineMemoryType.TEMPORARY, "Lieblingsgetränk Cola",
                CelineMemoryItem.KnowledgeState.INFERRED, System.currentTimeMillis() - 1L, "");
        memory.remember(new CelineMemoryMutation(CelineMemoryMutation.Operation.UPSERT, "", expired));
        if (memory.retrieve(
                new CelineBrainRequest("r2", "Lieblingsgetränk", System.currentTimeMillis()), 4).items.size() != 1) {
            throw new AssertionError("expired memory leaked");
        }

        memory.remember(new CelineMemoryMutation(CelineMemoryMutation.Operation.FORGET, "new", null));
        if (!memory.retrieve(
                new CelineBrainRequest("r3", "Lieblingsgetränk", System.currentTimeMillis()), 4).items.isEmpty()) {
            throw new AssertionError("forgotten/superseded chain resurrected");
        }

        if (memory.retrieve(
                new CelineBrainRequest("r4", "Quad Farbe", System.currentTimeMillis()), 2).items.isEmpty()) {
            throw new AssertionError("relevant record not retrievable");
        }

        System.out.println("celine-g1-structured-memory PASS");
    }
}
"""

with tempfile.TemporaryDirectory(prefix="celine-g12-memory-") as tmp:
    probe_path = Path(tmp) / "G12MemoryProbe.java"
    probe_path.write_text(probe, encoding="utf-8")
    classes = Path(tmp) / "classes"
    classes.mkdir()
    compile_result = subprocess.run(
        ["javac", "-source", "8", "-target", "8", "-d", str(classes),
         str(brain), str(engine), str(probe_path)],
        cwd=root, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if compile_result.returncode != 0:
        raise AssertionError("G1.2 memory javac failed:\n" + compile_result.stdout)

    run_result = subprocess.run(
        ["java", "-cp", str(classes), "de.yahya.ai.G12MemoryProbe"],
        cwd=root, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if run_result.returncode != 0 or "PASS" not in run_result.stdout:
        raise AssertionError("G1.2 memory probe failed:\n" + run_result.stdout)

print("celine-g1-structured-memory-contract PASS")
