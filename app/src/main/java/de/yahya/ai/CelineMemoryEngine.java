package de.yahya.ai;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Pure-Java structured memory policy. Persistence is supplied separately on Android. */
public final class CelineMemoryEngine implements CelineMemory {
    public static final class ConsolidationReport {
        public final int expiredRemoved;
        public final int conflictRemoved;
        public final int supersededRemoved;
        public final int duplicateRemoved;

        ConsolidationReport(int expiredRemoved, int conflictRemoved,
                            int supersededRemoved, int duplicateRemoved) {
            this.expiredRemoved = Math.max(0, expiredRemoved);
            this.conflictRemoved = Math.max(0, conflictRemoved);
            this.supersededRemoved = Math.max(0, supersededRemoved);
            this.duplicateRemoved = Math.max(0, duplicateRemoved);
        }

        public int totalRemoved() {
            return expiredRemoved + conflictRemoved + supersededRemoved + duplicateRemoved;
        }
    }

    private final List<CelineMemoryItem> records = new ArrayList<>();

    public CelineMemoryEngine(List<CelineMemoryItem> initial) {
        if (initial != null) records.addAll(initial);
    }

    @Override
    public synchronized CelineMemorySlice retrieve(CelineBrainRequest request, int maxItems) {
        if (request == null || maxItems <= 0) return CelineMemorySlice.empty();
        long now = System.currentTimeMillis();
        Set<String> query = terms(request.userText);
        if (query.isEmpty()) return CelineMemorySlice.empty();

        Set<String> superseded = new HashSet<>();
        for (CelineMemoryItem item : records) {
            if (item == null || item.isExpired(now)) continue;
            if (!item.supersedesId.isEmpty()) superseded.add(item.supersedesId);
        }

        List<Scored> scored = new ArrayList<>();
        for (CelineMemoryItem item : records) {
            if (item == null || item.id.isEmpty() || item.summary.isEmpty()) continue;
            if (item.isExpired(now) || superseded.contains(item.id)) continue;
            int score = relevanceScore(query, item);
            if (score > 0) scored.add(new Scored(item, score));
        }
        Collections.sort(scored, new Comparator<Scored>() {
            @Override public int compare(Scored a, Scored b) {
                int byScore = Integer.compare(b.score, a.score);
                if (byScore != 0) return byScore;
                return Long.compare(b.item.updatedAtEpochMs, a.item.updatedAtEpochMs);
            }
        });

        List<CelineMemoryItem> out = new ArrayList<>();
        for (Scored value : scored) {
            out.add(value.item);
            if (out.size() >= maxItems) break;
        }
        return new CelineMemorySlice(out);
    }

    @Override
    public synchronized void remember(CelineMemoryMutation mutation) {
        if (mutation == null) return;
        switch (mutation.operation) {
            case FORGET:
                if (!mutation.targetId.isEmpty()) forgetChain(mutation.targetId);
                return;
            case SUPERSEDE:
                if (mutation.item == null) return;
                CelineMemoryItem replacement = mutation.item;
                if (!mutation.targetId.isEmpty() && !mutation.targetId.equals(replacement.supersedesId)) {
                    replacement = replacement.withSupersedes(mutation.targetId);
                }
                upsert(replacement);
                return;
            case UPSERT:
            default:
                if (mutation.item != null) upsert(mutation.item);
        }
    }

    /**
     * Bounded deterministic consolidation. It runs only on explicit memory events/startup,
     * never as a permanent inference loop.
     */
    public synchronized ConsolidationReport consolidate(long nowEpochMs) {
        long now = nowEpochMs > 0L ? nowEpochMs : System.currentTimeMillis();
        int expired = 0;
        for (int i = records.size() - 1; i >= 0; i--) {
            CelineMemoryItem item = records.get(i);
            if (item == null || item.id.isEmpty() || item.summary.isEmpty() || item.isExpired(now)) {
                records.remove(i);
                expired++;
            }
        }

        int conflict = 0;
        Set<String> conflictLosers = new HashSet<>();
        Map<String,CelineMemoryItem> byId = byId();
        for (CelineMemoryItem item : new ArrayList<>(records)) {
            if (item.conflictWithId.isEmpty()) continue;
            CelineMemoryItem other = byId.get(item.conflictWithId);
            if (other == null || item.id.equals(other.id)) continue;
            CelineMemoryItem winner = stronger(item, other);
            conflictLosers.add(winner == item ? other.id : item.id);
        }
        for (String id : conflictLosers) if (removeById(id)) conflict++;

        int superseded = 0;
        Set<String> supersededIds = new HashSet<>();
        byId = byId();
        for (CelineMemoryItem item : records) {
            if (!item.supersedesId.isEmpty() && byId.containsKey(item.supersedesId)) {
                supersededIds.add(item.supersedesId);
            }
        }
        for (String id : supersededIds) if (removeById(id)) superseded++;

        int duplicates = 0;
        Map<String,CelineMemoryItem> winnerBySummary = new HashMap<>();
        Set<String> duplicateLosers = new HashSet<>();
        for (CelineMemoryItem item : records) {
            String key = normalize(item.summary);
            if (key.isEmpty()) continue;
            CelineMemoryItem prior = winnerBySummary.get(key);
            if (prior == null) {
                winnerBySummary.put(key, item);
                continue;
            }
            CelineMemoryItem winner = stronger(prior, item);
            CelineMemoryItem loser = winner == prior ? item : prior;
            duplicateLosers.add(loser.id);
            winnerBySummary.put(key, winner);
        }
        for (String id : duplicateLosers) if (removeById(id)) duplicates++;

        return new ConsolidationReport(expired, conflict, superseded, duplicates);
    }

    public synchronized List<CelineMemoryItem> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(records));
    }

    public synchronized List<CelineMemoryItem> activeSnapshot() {
        long now = System.currentTimeMillis();
        Set<String> superseded = new HashSet<>();
        for (CelineMemoryItem item : records) {
            if (item == null || item.isExpired(now)) continue;
            if (!item.supersedesId.isEmpty()) superseded.add(item.supersedesId);
        }
        List<CelineMemoryItem> out = new ArrayList<>();
        for (CelineMemoryItem item : records) {
            if (item == null || item.id.isEmpty() || item.summary.isEmpty()) continue;
            if (item.isExpired(now) || superseded.contains(item.id)) continue;
            out.add(item);
        }
        Collections.sort(out, new Comparator<CelineMemoryItem>() {
            @Override public int compare(CelineMemoryItem a, CelineMemoryItem b) {
                return Long.compare(b.updatedAtEpochMs, a.updatedAtEpochMs);
            }
        });
        return Collections.unmodifiableList(out);
    }

    public synchronized boolean containsSummary(String summary) {
        String needle = normalize(summary);
        if (needle.isEmpty()) return false;
        for (CelineMemoryItem item : records) {
            if (item != null && needle.equals(normalize(item.summary))) return true;
        }
        return false;
    }

    public synchronized String findSupersessionTarget(CelineMemoryItem incoming) {
        if (incoming == null || incoming.knowledgeState != CelineMemoryItem.KnowledgeState.EXPLICIT) return "";
        Set<String> incomingTerms = terms(incoming.summary);
        if (incomingTerms.isEmpty()) return "";
        long now = System.currentTimeMillis();
        String bestId = "";
        int bestScore = 0;
        for (CelineMemoryItem existing : records) {
            if (existing == null || existing.id.isEmpty() || existing.isExpired(now)) continue;
            if (existing.knowledgeState == CelineMemoryItem.KnowledgeState.EXPLICIT
                    || existing.knowledgeState == CelineMemoryItem.KnowledgeState.OBSERVED) continue;
            int score = overlap(incomingTerms, terms(existing.summary));
            if (score > bestScore) {
                bestScore = score;
                bestId = existing.id;
            }
        }
        return bestScore > 0 ? bestId : "";
    }

    /** Explicit user correction may replace an older explicit/observed/inferred record. */
    public synchronized String findCorrectionTarget(CelineMemoryItem incoming) {
        if (incoming == null) return "";
        Set<String> incomingTerms = terms(incoming.summary);
        if (incomingTerms.isEmpty()) return "";
        List<CelineMemoryItem> active = activeSnapshot();
        String bestId = "";
        int bestScore = 0;
        long bestUpdated = Long.MIN_VALUE;
        for (CelineMemoryItem existing : active) {
            int score = overlap(incomingTerms, terms(existing.summary));
            if (score <= 0) continue;
            if (existing.type == incoming.type) score += 2;
            if (score > bestScore || (score == bestScore && existing.updatedAtEpochMs > bestUpdated)) {
                bestScore = score;
                bestUpdated = existing.updatedAtEpochMs;
                bestId = existing.id;
            }
        }
        return bestScore > 0 ? bestId : "";
    }

    private Map<String,CelineMemoryItem> byId() {
        Map<String,CelineMemoryItem> out = new HashMap<>();
        for (CelineMemoryItem item : records) if (item != null && !item.id.isEmpty()) out.put(item.id, item);
        return out;
    }

    private static CelineMemoryItem stronger(CelineMemoryItem left, CelineMemoryItem right) {
        int leftRank = knowledgeRank(left.knowledgeState);
        int rightRank = knowledgeRank(right.knowledgeState);
        if (leftRank != rightRank) return leftRank > rightRank ? left : right;
        int confidence = Double.compare(left.confidence, right.confidence);
        if (confidence != 0) return confidence > 0 ? left : right;
        int updated = Long.compare(left.updatedAtEpochMs, right.updatedAtEpochMs);
        if (updated != 0) return updated > 0 ? left : right;
        int importance = Double.compare(left.importance, right.importance);
        if (importance != 0) return importance > 0 ? left : right;
        return left.id.compareTo(right.id) <= 0 ? left : right;
    }

    private static int knowledgeRank(CelineMemoryItem.KnowledgeState state) {
        if (state == CelineMemoryItem.KnowledgeState.EXPLICIT) return 4;
        if (state == CelineMemoryItem.KnowledgeState.OBSERVED) return 3;
        if (state == CelineMemoryItem.KnowledgeState.INFERRED) return 2;
        return 1;
    }

    private void upsert(CelineMemoryItem item) {
        if (item.id.isEmpty() || item.summary.isEmpty()) return;
        removeById(item.id);
        records.add(item);
    }

    private void forgetChain(String id) {
        String parent = "";
        for (CelineMemoryItem item : records) {
            if (item != null && id.equals(item.id)) {
                parent = item.supersedesId;
                break;
            }
        }
        removeById(id);
        if (!parent.isEmpty() && !parent.equals(id)) forgetChain(parent);
    }

    private boolean removeById(String id) {
        boolean removed = false;
        for (int i = records.size() - 1; i >= 0; i--) {
            CelineMemoryItem item = records.get(i);
            if (item != null && id.equals(item.id)) {
                records.remove(i);
                removed = true;
            }
        }
        return removed;
    }

    private static int relevanceScore(Set<String> query, CelineMemoryItem item) {
        int shared = overlap(query, terms(item.summary));
        if (shared <= 0) return 0;
        int score = shared * 100;
        score += (int) Math.round(item.importance * 20.0d);
        if (item.knowledgeState == CelineMemoryItem.KnowledgeState.EXPLICIT) score += 20;
        if (item.knowledgeState == CelineMemoryItem.KnowledgeState.OBSERVED) score += 12;
        if (item.type == CelineMemoryType.PREFERENCE || item.type == CelineMemoryType.PROFILE) score += 8;
        return score;
    }

    private static int overlap(Set<String> left, Set<String> right) {
        int count = 0;
        for (String value : left) if (right.contains(value)) count++;
        return count;
    }

    static Set<String> terms(String text) {
        Set<String> out = new HashSet<>();
        String normalized = normalize(text);
        if (normalized.isEmpty()) return out;
        for (String token : normalized.split("\\s+")) {
            if (token.length() < 3 || STOP_WORDS.contains(token)) continue;
            out.add(alias(token));
        }
        return out;
    }

    private static String alias(String token) {
        if ("heisse".equals(token) || "heisst".equals(token) || "name".equals(token)) return "name";
        if ("lieblingsgetraenk".equals(token) || "getraenk".equals(token)) return "getraenk";
        if ("mag".equals(token) || "lieber".equals(token) || "bevorzuge".equals(token)) return "praeferenz";
        return token;
    }

    private static String normalize(String text) {
        if (text == null) return "";
        return Normalizer.normalize(text.toLowerCase(Locale.GERMANY), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^a-z0-9äöüß]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static final Set<String> STOP_WORDS = new HashSet<>();
    static {
        String[] words = {
                "aber","also","auch","das","dass","dein","deine","dem","den","der","des","die",
                "doch","du","ein","eine","einer","es","für","hier","ich","im","in","ist","ja",
                "mein","meine","mit","nicht","noch","oder","sie","so","und","von","war","was",
                "wie","wir","wird","zu","zum","zur"
        };
        Collections.addAll(STOP_WORDS, words);
    }

    private static final class Scored {
        final CelineMemoryItem item;
        final int score;
        Scored(CelineMemoryItem item, int score) { this.item = item; this.score = score; }
    }
}
