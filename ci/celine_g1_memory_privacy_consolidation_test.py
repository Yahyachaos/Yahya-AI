#!/usr/bin/env python3
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
brain = root / "app/src/main/java/de/yahya/ai/CelineBrain.java"
engine = root / "app/src/main/java/de/yahya/ai/CelineMemoryEngine.java"
store = root / "app/src/main/java/de/yahya/ai/CelineStructuredMemory.java"
protected = root / "app/src/main/java/de/yahya/ai/CelineProtectedMemoryStorage.java"

for path in (brain, engine, store, protected):
    assert path.is_file(), f"missing G1.5 file: {path}"

engine_text = engine.read_text(encoding="utf-8")
store_text = store.read_text(encoding="utf-8")
protected_text = protected.read_text(encoding="utf-8")

for token in [
    "ConsolidationReport", "consolidate(long nowEpochMs)", "findCorrectionTarget",
    "duplicateRemoved", "conflictRemoved", "supersededRemoved", "expiredRemoved",
]:
    assert token in engine_text, f"missing G1.5 consolidation behavior: {token}"

for token in [
    "KEY_PROTECTED_STORE", "KEY_PROTECTED_ROLLBACK", "migratePlaintextStructuredStore",
    "migratePlaintextBackups", "inspectItems", "correct(String memoryId",
    "protectedStorageAvailable", "destroyKey", "consolidateAndPersist",
]:
    assert token in store_text, f"missing G1.5 store/privacy behavior: {token}"

for token in [
    'ANDROID_KEYSTORE = "AndroidKeyStore"',
    'TRANSFORMATION = "AES/GCM/NoPadding"',
    "KeyGenParameterSpec.Builder", "setRandomizedEncryptionRequired(true)",
    "updateAAD", "Fail closed for privacy",
]:
    assert token in protected_text, f"missing protected-storage behavior: {token}"

assert ".putString(KEY_STORE, serialize())" not in store_text, "new memory still written to plaintext store"
assert "prefs.edit().putString(KEY_STORE" not in store_text, "plaintext structured-memory write leaked"

probe = r"""
package de.yahya.ai;

import java.util.Arrays;
import java.util.List;

public final class G15ConsolidationProbe {
    private static CelineMemoryItem item(
            String id, CelineMemoryType type, String summary,
            CelineMemoryItem.KnowledgeState state, double confidence,
            long updated, long expires, String supersedes, String conflict) {
        return new CelineMemoryItem(
                id, type, summary, "g15-test", state, confidence, 0.8d,
                CelineMemoryPrivacy.LOCAL_PRIVATE,
                Math.max(1L, updated - 100L), updated, expires, supersedes, conflict);
    }

    private static boolean has(List<CelineMemoryItem> items, String id) {
        for (CelineMemoryItem item : items) if (item != null && id.equals(item.id)) return true;
        return false;
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        long now = System.currentTimeMillis();
        CelineMemoryItem duplicateWeak = item(
                "dup-weak", CelineMemoryType.PREFERENCE, "Ich mag starken Kaffee",
                CelineMemoryItem.KnowledgeState.INFERRED, 0.55d, now - 100L, 0L, "", "");
        CelineMemoryItem duplicateStrong = item(
                "dup-strong", CelineMemoryType.PREFERENCE, "Ich mag starken Kaffee",
                CelineMemoryItem.KnowledgeState.EXPLICIT, 1.0d, now, 0L, "", "");
        CelineMemoryItem conflictWeak = item(
                "conflict-weak", CelineMemoryType.SEMANTIC, "Projekt Alpha Status alt",
                CelineMemoryItem.KnowledgeState.INFERRED, 0.5d, now - 50L, 0L, "", "");
        CelineMemoryItem conflictStrong = item(
                "conflict-strong", CelineMemoryType.SEMANTIC, "Projekt Alpha Status neu",
                CelineMemoryItem.KnowledgeState.EXPLICIT, 1.0d, now, 0L, "", "conflict-weak");
        CelineMemoryItem supersededOld = item(
                "sup-old", CelineMemoryType.PROFILE, "Mein Name ist Alt",
                CelineMemoryItem.KnowledgeState.INFERRED, 0.5d, now - 100L, 0L, "", "");
        CelineMemoryItem superseding = item(
                "sup-new", CelineMemoryType.PROFILE, "Mein Name ist Neu",
                CelineMemoryItem.KnowledgeState.EXPLICIT, 1.0d, now, 0L, "sup-old", "");
        CelineMemoryItem expired = item(
                "expired", CelineMemoryType.TEMPORARY, "Temporär abgelaufen",
                CelineMemoryItem.KnowledgeState.OBSERVED, 0.9d, now - 100L, now - 1L, "", "");

        CelineMemoryEngine memory = new CelineMemoryEngine(Arrays.asList(
                duplicateWeak, duplicateStrong, conflictWeak, conflictStrong,
                supersededOld, superseding, expired));
        CelineMemoryEngine.ConsolidationReport report = memory.consolidate(now);
        List<CelineMemoryItem> active = memory.activeSnapshot();

        check(!has(active, "expired"), "expired record survived");
        check(!has(active, "dup-weak") && has(active, "dup-strong"), "dedup winner wrong");
        check(!has(active, "conflict-weak") && has(active, "conflict-strong"), "conflict winner wrong");
        check(!has(active, "sup-old") && has(active, "sup-new"), "superseded record survived");
        check(report.totalRemoved() >= 4, "consolidation did not remove expected records");

        CelineMemoryItem correction = item(
                "corr", CelineMemoryType.SEMANTIC, "Projekt Alpha Status erledigt",
                CelineMemoryItem.KnowledgeState.EXPLICIT, 1.0d, now + 1L, 0L, "", "");
        String target = memory.findCorrectionTarget(correction);
        check("conflict-strong".equals(target), "explicit correction target=" + target);

        System.out.println("celine-g1-memory-privacy-consolidation PASS");
    }
}
"""

with tempfile.TemporaryDirectory(prefix="celine-g15-memory-") as tmp:
    probe_path = Path(tmp) / "G15ConsolidationProbe.java"
    probe_path.write_text(probe, encoding="utf-8")
    classes = Path(tmp) / "classes"
    classes.mkdir()
    compile_result = subprocess.run(
        ["javac", "-source", "8", "-target", "8", "-d", str(classes),
         str(brain), str(engine), str(probe_path)],
        cwd=root, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if compile_result.returncode != 0:
        raise AssertionError("G1.5 consolidation javac failed:\n" + compile_result.stdout)
    run_result = subprocess.run(
        ["java", "-cp", str(classes), "de.yahya.ai.G15ConsolidationProbe"],
        cwd=root, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if run_result.returncode != 0 or "PASS" not in run_result.stdout:
        raise AssertionError("G1.5 consolidation probe failed:\n" + run_result.stdout)

print("celine-g1-memory-privacy-consolidation-contract PASS")
