package de.yahya.ai;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Pure-Java structured memory policy. Persistence is supplied separately on Android. */
public final class CelineMemoryEngine implements CelineMemory {
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

    private void removeById(String id) {
        for (int i = records.size() - 1; i >= 0; i--) {
            CelineMemoryItem item = records.get(i);
            if (item != null && id.equals(item.id)) records.remove(i);
        }
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
