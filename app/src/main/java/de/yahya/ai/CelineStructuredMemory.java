package de.yahya.ai;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Android persistence owner for structured Celine memory.
 *
 * G1.5 migrates legacy/plain structured memory into AndroidKeyStore-backed AES-GCM
 * storage. New writes fail closed instead of falling back to plaintext persistence.
 */
public final class CelineStructuredMemory implements CelineMemory {
    static final int STORE_SCHEMA = 1;

    // Historical plaintext keys are read only for one-time migration compatibility.
    static final String KEY_STORE = "celine_memory_v2_json";
    static final String KEY_MIGRATED = "celine_memory_v2_migrated";
    static final String KEY_LEGACY = "memory";
    static final String KEY_LEGACY_BACKUP = "celine_memory_v2_legacy_backup";
    static final String KEY_CORRUPT_BACKUP = "celine_memory_v2_corrupt_backup";

    // G1.5 protected values. All payload-bearing values under these keys are AES-GCM ciphertext.
    static final String KEY_PROTECTED_STORE = "celine_memory_g1_5_protected";
    static final String KEY_PROTECTED_ROLLBACK = "celine_memory_g1_5_rollback";
    static final String KEY_PROTECTED_LEGACY_BACKUP = "celine_memory_g1_5_legacy_backup";
    static final String KEY_PROTECTED_CORRUPT_BACKUP = "celine_memory_g1_5_corrupt_backup";
    static final String KEY_PROTECTED_CIPHERTEXT_BACKUP = "celine_memory_g1_5_ciphertext_backup";

    private final SharedPreferences prefs;
    private final CelineProtectedMemoryStorage protectedStorage;
    private final CelineMemoryEngine engine;

    public CelineStructuredMemory(SharedPreferences prefs) {
        if (prefs == null) throw new IllegalArgumentException("prefs must not be null");
        this.prefs = prefs;
        this.protectedStorage = new CelineProtectedMemoryStorage(prefs);
        this.engine = new CelineMemoryEngine(loadRecords());
        migratePlaintextStructuredStore();
        migrateLegacyOnce();
        migratePlaintextBackups();
        CelineMemoryEngine.ConsolidationReport report = engine.consolidate(System.currentTimeMillis());
        if (report.totalRemoved() > 0) persistAsync();
    }

    @Override
    public synchronized CelineMemorySlice retrieve(CelineBrainRequest request, int maxItems) {
        return engine.retrieve(request, maxItems);
    }

    @Override
    public synchronized void remember(CelineMemoryMutation mutation) {
        engine.remember(mutation);
        consolidateAndPersist();
    }

    public synchronized void rememberExplicit(String text) {
        String clean = cleanLine(text);
        if (clean.isEmpty() || looksSensitive(clean)) return;
        long now = System.currentTimeMillis();
        CelineMemoryItem item = new CelineMemoryItem(
                newId("explicit"),
                classify(clean),
                clean,
                "user_explicit",
                CelineMemoryItem.KnowledgeState.EXPLICIT,
                1.0d,
                0.85d,
                CelineMemoryPrivacy.LOCAL_PRIVATE,
                now,
                now,
                0L,
                "",
                "");
        String target = engine.findSupersessionTarget(item);
        engine.remember(new CelineMemoryMutation(
                target.isEmpty() ? CelineMemoryMutation.Operation.UPSERT : CelineMemoryMutation.Operation.SUPERSEDE,
                target,
                item));
        consolidateAndPersist();
    }

    public synchronized void rememberCorrection(String text, String provenance) {
        String clean = cleanLine(text);
        if (clean.isEmpty() || looksSensitive(clean)) return;
        long now = System.currentTimeMillis();
        CelineMemoryItem item = new CelineMemoryItem(
                newId("correction"),
                CelineMemoryType.DECISION_CORRECTION,
                clean,
                cleanLine(provenance).isEmpty() ? "user_correction" : cleanLine(provenance),
                CelineMemoryItem.KnowledgeState.EXPLICIT,
                1.0d,
                0.95d,
                CelineMemoryPrivacy.LOCAL_PRIVATE,
                now,
                now,
                0L,
                "",
                "");
        String target = engine.findCorrectionTarget(item);
        engine.remember(new CelineMemoryMutation(
                target.isEmpty() ? CelineMemoryMutation.Operation.UPSERT : CelineMemoryMutation.Operation.SUPERSEDE,
                target,
                item));
        consolidateAndPersist();
    }

    public synchronized void rememberInferred(String text, String provenance) {
        if (text == null) return;
        boolean changed = false;
        for (String line : text.split("\\r?\\n")) {
            String clean = cleanLine(line);
            if (clean.isEmpty() || looksSensitive(clean) || engine.containsSummary(clean)) continue;
            long now = System.currentTimeMillis();
            CelineMemoryItem item = new CelineMemoryItem(
                    newId("inferred"),
                    classify(clean),
                    clean,
                    cleanLine(provenance).isEmpty() ? "inferred" : cleanLine(provenance),
                    CelineMemoryItem.KnowledgeState.INFERRED,
                    0.60d,
                    0.55d,
                    CelineMemoryPrivacy.LOCAL_PRIVATE,
                    now,
                    now,
                    0L,
                    "",
                    "");
            engine.remember(new CelineMemoryMutation(CelineMemoryMutation.Operation.UPSERT, "", item));
            changed = true;
        }
        if (changed) consolidateAndPersist();
    }

    public synchronized String promptMemory(String userText, int maxItems) {
        String clean = cleanLine(userText);
        if (clean.isEmpty() || maxItems <= 0) return "";
        CelineMemorySlice slice = engine.retrieve(
                new CelineBrainRequest("memory-prompt-" + System.nanoTime(), clean, System.currentTimeMillis()),
                Math.min(8, maxItems));
        StringBuilder out = new StringBuilder();
        for (CelineMemoryItem item : slice.items) {
            if (item.privacyScope == CelineMemoryPrivacy.LOCAL_SENSITIVE) continue;
            if (out.length() > 0) out.append('\n');
            out.append(item.type.name()).append(": ").append(item.summary);
            if (out.length() >= 1600) break;
        }
        if (out.length() > 1600) out.setLength(1600);
        return out.toString();
    }

    public synchronized String inspect() {
        List<CelineMemoryItem> active = engine.activeSnapshot();
        if (active.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        for (CelineMemoryItem item : active) {
            if (out.length() > 0) out.append('\n');
            out.append(item.type.name())
                    .append(" · ")
                    .append(item.knowledgeState.name())
                    .append(" · ")
                    .append(item.summary);
        }
        return out.toString();
    }

    /** Active records for user-facing inspect/correct/forget controls. */
    public synchronized List<CelineMemoryItem> inspectItems() {
        return engine.activeSnapshot();
    }

    /** Correct exactly one selected memory without relying on fuzzy free-text replacement. */
    public synchronized boolean correct(String memoryId, String replacementText) {
        String id = cleanLine(memoryId);
        String clean = cleanLine(replacementText);
        if (id.isEmpty() || clean.isEmpty() || looksSensitive(clean)) return false;
        CelineMemoryItem existing = findActiveById(id);
        if (existing == null) return false;
        long now = System.currentTimeMillis();
        CelineMemoryItem replacement = new CelineMemoryItem(
                newId("control-correction"),
                existing.type,
                clean,
                "user_memory_control",
                CelineMemoryItem.KnowledgeState.EXPLICIT,
                1.0d,
                Math.max(0.95d, existing.importance),
                existing.privacyScope,
                now,
                now,
                existing.expiresAtEpochMs,
                id,
                "");
        engine.remember(new CelineMemoryMutation(CelineMemoryMutation.Operation.SUPERSEDE, id, replacement));
        consolidateAndPersist();
        return true;
    }

    public synchronized void forget(String memoryId) {
        String id = cleanLine(memoryId);
        if (id.isEmpty()) return;
        engine.remember(new CelineMemoryMutation(CelineMemoryMutation.Operation.FORGET, id, null));
        consolidateAndPersist();
    }

    public synchronized CelineMemoryEngine.ConsolidationReport consolidateNow() {
        CelineMemoryEngine.ConsolidationReport report = engine.consolidate(System.currentTimeMillis());
        if (report.totalRemoved() > 0) persistAsync();
        return report;
    }

    public boolean protectedStorageAvailable() {
        return protectedStorage.available();
    }

    public synchronized void forgetAll() {
        prefs.edit()
                .remove(KEY_PROTECTED_STORE)
                .remove(KEY_PROTECTED_ROLLBACK)
                .remove(KEY_PROTECTED_LEGACY_BACKUP)
                .remove(KEY_PROTECTED_CORRUPT_BACKUP)
                .remove(KEY_PROTECTED_CIPHERTEXT_BACKUP)
                .remove(KEY_STORE)
                .remove(KEY_MIGRATED)
                .remove(KEY_LEGACY)
                .remove(KEY_LEGACY_BACKUP)
                .remove(KEY_CORRUPT_BACKUP)
                .commit();
        protectedStorage.destroyKey();
        for (CelineMemoryItem item : new ArrayList<>(engine.snapshot())) {
            engine.remember(new CelineMemoryMutation(CelineMemoryMutation.Operation.FORGET, item.id, null));
        }
    }

    private CelineMemoryItem findActiveById(String id) {
        for (CelineMemoryItem item : engine.activeSnapshot()) {
            if (item != null && id.equals(item.id)) return item;
        }
        return null;
    }

    private List<CelineMemoryItem> loadRecords() {
        String raw = "";
        if (protectedStorage.hasCiphertext(KEY_PROTECTED_STORE)) {
            try {
                raw = protectedStorage.read(KEY_PROTECTED_STORE);
            } catch (Exception invalidCiphertext) {
                String ciphertext = protectedStorage.rawCiphertext(KEY_PROTECTED_STORE);
                if (!ciphertext.isEmpty()) {
                    prefs.edit().putString(KEY_PROTECTED_CIPHERTEXT_BACKUP, ciphertext)
                            .remove(KEY_PROTECTED_STORE).commit();
                }
            }
        }
        if (raw.trim().isEmpty()) {
            String legacyStructured = prefs.getString(KEY_STORE, "");
            raw = legacyStructured == null ? "" : legacyStructured;
        }
        if (raw.trim().isEmpty()) return Collections.emptyList();
        try {
            JSONObject root = new JSONObject(raw);
            if (root.optInt("schema", -1) != STORE_SCHEMA) throw new IllegalStateException("unsupported memory schema");
            JSONArray array = root.optJSONArray("records");
            if (array == null) return Collections.emptyList();
            List<CelineMemoryItem> out = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject value = array.optJSONObject(i);
                if (value == null) continue;
                CelineMemoryItem item = fromJson(value);
                if (item != null && !item.id.isEmpty() && !item.summary.isEmpty()) out.add(item);
            }
            return out;
        } catch (Exception invalid) {
            protectedStorage.write(KEY_PROTECTED_CORRUPT_BACKUP, raw, true);
            return Collections.emptyList();
        }
    }

    /** Move the G1.2 plaintext JSON store to protected storage and retain an encrypted rollback copy. */
    private void migratePlaintextStructuredStore() {
        String plaintext = prefs.getString(KEY_STORE, "");
        if (plaintext == null || plaintext.trim().isEmpty()) return;
        boolean rollback = protectedStorage.write(KEY_PROTECTED_ROLLBACK, plaintext, true);
        boolean active = protectedStorage.hasCiphertext(KEY_PROTECTED_STORE)
                || protectedStorage.write(KEY_PROTECTED_STORE, serialize(), true);
        if (rollback && active) prefs.edit().remove(KEY_STORE).commit();
    }

    private void migrateLegacyOnce() {
        if (prefs.getBoolean(KEY_MIGRATED, false)) return;
        String legacy = prefs.getString(KEY_LEGACY, "");
        if (legacy == null) legacy = "";
        long now = System.currentTimeMillis();
        int index = 0;
        for (String line : legacy.split("\\r?\\n")) {
            String clean = cleanLine(line);
            if (clean.isEmpty() || looksSensitive(clean) || engine.containsSummary(clean)) continue;
            CelineMemoryItem item = new CelineMemoryItem(
                    "legacy-" + index + "-" + Integer.toHexString(clean.hashCode()),
                    CelineMemoryType.LEGACY,
                    clean,
                    "legacy_shared_preferences",
                    CelineMemoryItem.KnowledgeState.UNKNOWN,
                    0.50d,
                    0.50d,
                    CelineMemoryPrivacy.LOCAL_PRIVATE,
                    now,
                    now,
                    0L,
                    "",
                    "");
            engine.remember(new CelineMemoryMutation(CelineMemoryMutation.Operation.UPSERT, "", item));
            index++;
        }

        boolean backupOk = legacy.trim().isEmpty()
                || protectedStorage.write(KEY_PROTECTED_LEGACY_BACKUP, legacy, true);
        boolean storeOk = protectedStorage.write(KEY_PROTECTED_STORE, serialize(), true);
        if (backupOk && storeOk) {
            prefs.edit()
                    .putBoolean(KEY_MIGRATED, true)
                    .remove(KEY_LEGACY)
                    .remove(KEY_STORE)
                    .remove(KEY_LEGACY_BACKUP)
                    .remove(KEY_CORRUPT_BACKUP)
                    .commit();
        }
    }

    /** Protect any historical plaintext backups left by G1.2 before removing them. */
    private void migratePlaintextBackups() {
        migratePlaintextBackup(KEY_LEGACY_BACKUP, KEY_PROTECTED_LEGACY_BACKUP);
        migratePlaintextBackup(KEY_CORRUPT_BACKUP, KEY_PROTECTED_CORRUPT_BACKUP);
    }

    private void migratePlaintextBackup(String plaintextKey, String protectedKey) {
        String raw = prefs.getString(plaintextKey, "");
        if (raw == null || raw.trim().isEmpty()) return;
        if (protectedStorage.write(protectedKey, raw, true)) {
            prefs.edit().remove(plaintextKey).commit();
        }
    }

    private void consolidateAndPersist() {
        engine.consolidate(System.currentTimeMillis());
        persistAsync();
    }

    private void persistAsync() {
        // Privacy fail-closed: protectedStorage never falls back to plaintext writes.
        protectedStorage.write(KEY_PROTECTED_STORE, serialize(), false);
    }

    private String serialize() {
        try {
            JSONObject root = new JSONObject();
            root.put("schema", STORE_SCHEMA);
            JSONArray records = new JSONArray();
            for (CelineMemoryItem item : engine.snapshot()) records.put(toJson(item));
            root.put("records", records);
            return root.toString();
        } catch (Exception impossible) {
            throw new IllegalStateException("memory serialization failed", impossible);
        }
    }

    private static JSONObject toJson(CelineMemoryItem item) throws Exception {
        JSONObject out = new JSONObject();
        out.put("id", item.id);
        out.put("type", item.type.name());
        out.put("summary", item.summary);
        out.put("provenance", item.provenance);
        out.put("knowledgeState", item.knowledgeState.name());
        out.put("confidence", item.confidence);
        out.put("importance", item.importance);
        out.put("privacyScope", item.privacyScope.name());
        out.put("createdAtEpochMs", item.createdAtEpochMs);
        out.put("updatedAtEpochMs", item.updatedAtEpochMs);
        out.put("expiresAtEpochMs", item.expiresAtEpochMs);
        out.put("supersedesId", item.supersedesId);
        out.put("conflictWithId", item.conflictWithId);
        return out;
    }

    private static CelineMemoryItem fromJson(JSONObject value) {
        try {
            return new CelineMemoryItem(
                    value.optString("id", ""),
                    enumValue(CelineMemoryType.class, value.optString("type", ""), CelineMemoryType.LEGACY),
                    value.optString("summary", ""),
                    value.optString("provenance", ""),
                    enumValue(CelineMemoryItem.KnowledgeState.class, value.optString("knowledgeState", ""),
                            CelineMemoryItem.KnowledgeState.UNKNOWN),
                    value.optDouble("confidence", 0.5d),
                    value.optDouble("importance", 0.5d),
                    enumValue(CelineMemoryPrivacy.class, value.optString("privacyScope", ""),
                            CelineMemoryPrivacy.LOCAL_PRIVATE),
                    value.optLong("createdAtEpochMs", 0L),
                    value.optLong("updatedAtEpochMs", 0L),
                    value.optLong("expiresAtEpochMs", 0L),
                    value.optString("supersedesId", ""),
                    value.optString("conflictWithId", ""));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String raw, T fallback) {
        try {
            return Enum.valueOf(type, raw);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static CelineMemoryType classify(String text) {
        String value = text.toLowerCase(Locale.GERMANY);
        if (value.contains("lieber") || value.contains("bevorzug") || value.contains("mag ")
                || value.startsWith("ich mag")) return CelineMemoryType.PREFERENCE;
        if (value.contains("mein name") || value.contains("ich heiße") || value.contains("ich heisse")
                || value.contains("ich bin ")) return CelineMemoryType.PROFILE;
        if (value.contains("entschieden") || value.contains("korrektur") || value.contains("stattdessen"))
            return CelineMemoryType.DECISION_CORRECTION;
        return CelineMemoryType.SEMANTIC;
    }

    private static boolean looksSensitive(String text) {
        String value = text.toLowerCase(Locale.ROOT);
        return value.contains("passwort")
                || value.contains("password")
                || value.matches(".*\\bpin\\b.*")
                || value.contains("api key")
                || value.contains("api-key")
                || value.contains("kreditkarte")
                || value.contains("iban")
                || value.contains("sk-");
    }

    private static String cleanLine(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static String newId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString();
    }
}
