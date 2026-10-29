package de.yahya.ai;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * FUTURE ONLY (post-v83), pure Java experiment for G1 structured memory retrieval.
 *
 * This policy does NOT persist, collect, infer or modify memories. It consumes an
 * explicitly supplied, bounded projection of existing CelineMemoryItem records.
 * AndroidKeyStore, correction-by-ID, forgetting, and access permission remain owned
 * by G1. This class is not wired into v83 or into any provider call.
 */
public final class CelineFutureMemoryRecall {
    private CelineFutureMemoryRecall() {}

    public enum Privacy { LOCAL_PRIVATE, LOCAL_SENSITIVE, SHAREABLE_ON_REQUEST }

    public static final class Candidate {
        public final String id, summary, supersedesId, conflictWithId;
        public final boolean authorizedForProvider, userExplicit;
        public final Privacy privacy;
        public final long updatedAtMs, expiresAtMs;
        public final double importance;

        public Candidate(String id, String summary, String supersedesId,
                Privacy privacy, boolean authorizedForProvider, boolean userExplicit,
                long updatedAtMs, long expiresAtMs, double importance) {
            this(id, summary, supersedesId, "", privacy, authorizedForProvider,
                    userExplicit, updatedAtMs, expiresAtMs, importance);
        }

        public Candidate(String id, String summary, String supersedesId,
                String conflictWithId, Privacy privacy, boolean authorizedForProvider,
                boolean userExplicit, long updatedAtMs, long expiresAtMs, double importance) {
            this.id = rawId(id);
            this.summary = summary == null || summary.length() > MAX_INPUT || hasNonTextControl(summary) ? "" : safe(summary);
            this.supersedesId = rawId(supersedesId);
            this.conflictWithId = rawId(conflictWithId);
            this.privacy = privacy;
            this.authorizedForProvider = authorizedForProvider;
            this.userExplicit = userExplicit;
            // Preserve corrupt negative source timestamps. Converting negative
            // expiry to the G1 zero/unbounded sentinel resurrects stale facts.
            this.updatedAtMs = updatedAtMs;
            this.expiresAtMs = expiresAtMs;
            // Non-finite relevance metadata cannot authorize a recalled fact.
            // Keep finite out-of-range weights bounded as before.
            this.importance = Double.isFinite(importance)
                    ? Math.max(0d, Math.min(1d, importance)) : Double.NaN;
        }
        /**
         * Bounded, scalar-only projection of the exact G1 privacy enum values.
         * Unknown scopes remain in the lineage graph but are never exportable.
         * The permission bit must be supplied by the owning app, never inferred.
         */
        /**
         * Legacy metadata-free adapter: missing provenance/state never grants
         * provider exposure, even when a permission bit is supplied.
         */
        public static Candidate fromG1Projection(String id, String summary,
                String supersedesId, String privacyScope, boolean providerPermission,
                boolean userExplicit, long updatedAtMs, long expiresAtMs, double importance) {
            return fromG1Projection(id, summary, supersedesId, privacyScope,
                    null, null, providerPermission, userExplicit,
                    updatedAtMs, expiresAtMs, importance);
        }

        /**
         * Exact G1 state/provenance projection, with conservative source trust.
         * A trusted user-origin state still requires separate provider permission.
         * Unknown or contradictory sources remain in correction lineage only.
         */
        public static Candidate fromG1Projection(String id, String summary,
                String supersedesId, String privacyScope, String knowledgeState,
                String provenance, boolean providerPermission, boolean userExplicit,
                long updatedAtMs, long expiresAtMs, double importance) {
            return fromG1Projection(id, summary, supersedesId, "", privacyScope,
                    knowledgeState, provenance, providerPermission, userExplicit,
                    updatedAtMs, expiresAtMs, importance);
        }

        /** A conflict is G1 metadata, not proof that either side is resolved. */
        public static Candidate fromG1Projection(String id, String summary,
                String supersedesId, String conflictWithId, String privacyScope,
                String knowledgeState, String provenance, boolean providerPermission,
                boolean userExplicit, long updatedAtMs, long expiresAtMs, double importance) {
            Privacy scope = null;
            if ("LOCAL_PRIVATE".equals(privacyScope)) scope = Privacy.LOCAL_PRIVATE;
            else if ("LOCAL_SENSITIVE".equals(privacyScope)) scope = Privacy.LOCAL_SENSITIVE;
            else if ("SHAREABLE_ON_REQUEST".equals(privacyScope)) scope = Privacy.SHAREABLE_ON_REQUEST;
            boolean trustedUserOrigin = "EXPLICIT".equals(knowledgeState) && userExplicit
                    && ("user_explicit".equals(provenance) || "user_correction".equals(provenance)
                        || "user_memory_control".equals(provenance));
            return new Candidate(id, summary, supersedesId, conflictWithId, scope,
                    scope == Privacy.LOCAL_PRIVATE && providerPermission && trustedUserOrigin,
                    userExplicit, updatedAtMs, expiresAtMs, importance);
        }

        boolean live(long now) { return !id.isEmpty() && !summary.isEmpty()
                && now >= 0L && updatedAtMs >= 0L && updatedAtMs <= now
                && expiresAtMs >= 0L && Double.isFinite(importance)
                && (expiresAtMs == 0L || (expiresAtMs > now && expiresAtMs > updatedAtMs)); }
    }

    public static final class Match {
        public final Candidate candidate;
        public final int sharedTerms, queryTerms;
        public final int score;
        Match(Candidate candidate, int sharedTerms, int queryTerms, int score) {
            this.candidate = candidate;
            this.sharedTerms = sharedTerms;
            this.queryTerms = queryTerms;
            this.score = score;
        }
    }

    private static final int MAX_ID_CHARS = 256;
    private static final int MAX_INPUT = 512;
    private static final int MAX_QUERY_TOKENS = 32;
    private static final int MAX_CANDIDATES = 2048;
    private static final int MAX_RESULTS = 8;

    /**
     * Legacy prototype entry point: only safe if the owner supplies complete
     * lineage records. Use the retraction-aware overload after G1 compaction.
     */
    public static List<Match> retrieve(String query, List<Candidate> candidates, int maxResults, long now) {
        return retrieve(query, candidates, Collections.<String>emptySet(), maxResults, now);
    }

    /**
     * Deterministic provider-safe projection. Retired IDs are supplied by the
     * owning memory store, not inferred or persisted by this policy.
     */
    public static List<Match> retrieve(String query, List<Candidate> candidates,
            Set<String> retiredIds, int maxResults, long now) {
        // A tombstone alone does not attest pruned conflict/fork resolution.
        return retrieve(query, candidates, retiredIds,
                Collections.<String>emptySet(), maxResults, now);
    }

    /**
     * Future-only explicit G1 owner attestation of resolved/complete history
     * for pruned parent IDs. This prototype does not mint that evidence.
     */
    public static List<Match> retrieve(String query, List<Candidate> candidates,
            Set<String> retiredIds, Set<String> confirmedRetiredParentIds,
            int maxResults, long now) {
        if (maxResults <= 0 || candidates == null || candidates.isEmpty()
                || retiredIds == null || retiredIds.size() > MAX_CANDIDATES
                || confirmedRetiredParentIds == null
                || confirmedRetiredParentIds.size() > MAX_CANDIDATES)
            return Collections.emptyList();
        // Never silently truncate the supersession graph: a later sensitive
        // replacement must not let an earlier eligible record leak through.
        if (candidates.size() > MAX_CANDIDATES) return Collections.emptyList();
        // Contradictory projections (e.g. the same ID first public, then private)
        // must never be settled by input order. Require the owning store to resolve
        // duplicate IDs before this policy can return any candidate.
        Set<String> ids = new HashSet<>();
        for (Candidate c : candidates) {
            // A missing/invalid entry could be the only evidence of a private
            // successor or conflict. Never proceed with a partial lineage graph.
            if (c == null || !validId(c.id, false)
                    || !validId(c.supersedesId, true)
                    || !validId(c.conflictWithId, true) || !ids.add(c.id))
                return Collections.emptyList();
        }
        Set<String> wanted = terms(query);
        if (wanted.isEmpty()) return Collections.emptyList();
        // Build the correction lineage from ALL supplied records, including expired
        // or private successors. A successor's expiry must not make an obsolete
        // ancestor eligible again. The owning G1 store must retain lineage data;
        // a removed successor cannot be reconstructed by this stateless policy.
        Set<String> replaced = new HashSet<>();
        for (String retiredId : retiredIds) {
            String id = rawId(retiredId);
            if (!validId(id, false)) return Collections.emptyList();
            replaced.add(id);
        }
        // Only the source owner can certify complete and adjudicated history.
        // A purported certificate outside the authoritative retired set is invalid.
        for (String confirmed : confirmedRetiredParentIds) {
            if (!validId(rawId(confirmed), false) || !retiredIds.contains(confirmed))
                return Collections.emptyList();
        }
        // G1 conflictWithId records an unresolved contradiction, NOT a vetted
        // supersession. Without the owning G1 resolver's durable winner proof,
        // neither endpoint may be silently presented as an authoritative fact.
        // Include expired/private/withheld records to prevent stale resurrection.
        Set<String> conflicted = new HashSet<>();
        Map<String, List<String>> successorsByParent = new HashMap<>();
        for (Candidate c : candidates) {
            if (!c.supersedesId.isEmpty()) {
                replaced.add(c.supersedesId);
                successorsByParent.computeIfAbsent(c.supersedesId, k -> new ArrayList<>()).add(c.id);
            }
            if (!c.conflictWithId.isEmpty()) {
                conflicted.add(c.id);
                conflicted.add(c.conflictWithId);
            }
        }
        // A missing parent requires a distinct G1 owner attestation of full
        // historical conflict/fork resolution, not just a retired-ID tombstone.
        // Orphans and their descendants are quarantined without such evidence.
        Set<String> orphaned = new HashSet<>();
        for (Candidate c : candidates) {
            if (!c.supersedesId.isEmpty() && !ids.contains(c.supersedesId)
                    && !confirmedRetiredParentIds.contains(c.supersedesId))
                orphaned.add(c.id);
        }
        // Compaction/reimport can preserve competing corrections without a G1
        // winner decision. A conflict endpoint is also not proof that a later
        // child is trustworthy. Quarantine BOTH unresolved fork descendants and
        // every descendant of a conflicted ID, even if its parent was pruned,
        // private or expired. Only the owning G1 resolver can clear that history.
        Set<String> unresolvedLineage = new HashSet<>(conflicted);
        unresolvedLineage.addAll(orphaned);
        List<String> pending = new ArrayList<>(unresolvedLineage);
        for (List<String> siblings : successorsByParent.values()) {
            if (siblings.size() < 2) continue;
            for (String child : siblings) {
                if (unresolvedLineage.add(child)) pending.add(child);
            }
        }
        for (int i = 0; i < pending.size(); i++) {
            List<String> children = successorsByParent.get(pending.get(i));
            if (children == null) continue;
            for (String child : children) {
                if (unresolvedLineage.add(child)) pending.add(child);
            }
        }
        List<Candidate> live = liveCandidates(candidates, now);
        List<Match> matches = new ArrayList<>();
        for (Candidate c : live) {
            if (replaced.contains(c.id) || unresolvedLineage.contains(c.id) || !c.authorizedForProvider || !c.userExplicit
                    || c.privacy != Privacy.LOCAL_PRIVATE) continue;
            Set<String> stored = terms(c.summary);
            int overlap = overlap(wanted, stored);
            // Do not return a coincidental one-word hit for a multi-topic query.
            if (overlap == 0 || (wanted.size() >= 2 && overlap < 2)) continue;
            // Query coverage dominates importance; long generic records cannot win on length.
            int score = overlap * 100 + (overlap * 50 / wanted.size());
            score += c.userExplicit ? 12 : 0;
            score += (int) Math.round(c.importance * 10);
            matches.add(new Match(c, overlap, wanted.size(), score));
        }
        matches.sort(Comparator
            .comparingInt((Match m) -> m.score).reversed()
            .thenComparing((Match a, Match b) -> Long.compare(b.candidate.updatedAtMs, a.candidate.updatedAtMs))
            .thenComparing(m -> m.candidate.id));
        return Collections.unmodifiableList(new ArrayList<>(
                matches.subList(0, Math.min(Math.min(MAX_RESULTS, maxResults), matches.size()))));
    }

    /**
     * Natural-language correction target suggestion. NEVER mutates a memory.
     * Requires two specific shared tokens and an unambiguous winner; otherwise
     * callers must use the existing explicit memory-ID correction UI.
     */
    public static String unambiguousCorrectionTarget(String correctionText,
            List<Candidate> candidates, long now) {
        return unambiguousCorrectionTarget(correctionText, candidates,
                Collections.<String>emptySet(), now);
    }

    public static String unambiguousCorrectionTarget(String correctionText,
            List<Candidate> candidates, Set<String> retiredIds, long now) {
        return unambiguousCorrectionTarget(correctionText, candidates, retiredIds,
                Collections.<String>emptySet(), now);
    }

    public static String unambiguousCorrectionTarget(String correctionText,
            List<Candidate> candidates, Set<String> retiredIds,
            Set<String> confirmedRetiredParentIds, long now) {
        Set<String> query = terms(correctionText);
        if (query.size() < 2) return "";
        List<Match> matches = retrieve(correctionText, candidates,
                retiredIds, confirmedRetiredParentIds, 8, now);
        if (matches.isEmpty()) return "";
        Match best = matches.get(0);
        if (best.sharedTerms < 2 || best.sharedTerms * 2 < query.size()) return "";
        if (matches.size() > 1) {
            Match next = matches.get(1);
            // Actual distinct lexical evidence, not a tie broken by recency/importance.
            if (best.sharedTerms <= next.sharedTerms) return "";
        }
        return best.candidate.id;
    }

    private static List<Candidate> liveCandidates(List<Candidate> candidates, long now) {
        List<Candidate> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Candidate c : candidates) {
            if (result.size() >= MAX_CANDIDATES) break;
            if (c == null || !c.live(now) || !seen.add(c.id)) continue;
            result.add(c);
        }
        return result;
    }

    private static int overlap(Set<String> a, Set<String> b) {
        int count = 0;
        for (String s : a) if (b.contains(s)) count++;
        return count;
    }

    private static Set<String> terms(String text) {
        Set<String> result = new HashSet<>();
        if (text == null || text.length() > MAX_INPUT || !wellFormedUtf16(text)
                || hasNonTextControl(text) || hasUnrepresentableLexicalChars(text)) return result;
        String value = safe(text);
        if (value.isEmpty()) return result;
        if (value.length() > MAX_INPUT) return result;
        value = value.toLowerCase(Locale.GERMANY)
            .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        value = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .replaceAll("[^a-z0-9]+", " ").trim();
        if (value.isEmpty()) return result;
        for (String word : value.split("\\s+")) {
            if (word.length() < 3 || STOP.contains(word)) continue;
            String term = ALIASES.containsKey(word) ? ALIASES.get(word) : word;
            result.add(term);
            if (result.size() > MAX_QUERY_TOKENS) return Collections.emptySet();
        }
        return result;
    }

    // A query or memory containing a lexical script that this Latin/German
    // tokenizer cannot represent must not be partially matched as if the
    // unsupported word/digit had never been present. Latin accent decomposition
    // and the existing German aliases remain supported.
    private static boolean hasUnrepresentableLexicalChars(String value) {
        for (int i = 0; i < value.length();) {
            int cp = value.codePointAt(i);
            if (Character.isLetterOrDigit(cp)) {
                String scalar = new String(Character.toChars(cp))
                    .toLowerCase(Locale.GERMANY)
                    .replace("ä", "ae").replace("ö", "oe")
                    .replace("ü", "ue").replace("ß", "ss");
                scalar = Normalizer.normalize(scalar, Normalizer.Form.NFD)
                    .replaceAll("\\p{M}+", "");
                if (!scalar.matches("[a-z0-9]+")) return true;
            }
            i += Character.charCount(cp);
        }
        return false;
    }

    // IDs originate from G1 prefix+UUID; exceptionally large or control-laden
    // lineage keys are corrupt metadata, not authoritative correction evidence.
    private static boolean validId(String id, boolean allowEmpty) {
        if (id.isEmpty()) return allowEmpty;
        if (id.length() > MAX_ID_CHARS) return false;
        // Source-owned G1 IDs are ASCII prefix + UUID. Do not accept
        // invisible combining marks, private-use or noncharacter code points:
        // those can look identical while naming a different lineage record.
        // The remaining punctuation supports existing synthetic contract IDs.
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_'
                    || c == '.' || c == ':')) return false;
        }
        return true;
    }

    // A broken UTF-16 scalar can turn into a separator during normalization,
    // creating false partial evidence. Only complete, well-formed user text
    // may contribute recall terms; valid supplementary characters are allowed.
    private static boolean wellFormedUtf16(String value) {
        if (value == null) return false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isHighSurrogate(ch)) {
                if (i + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(i + 1))) return false;
                i++;
            } else if (Character.isLowSurrogate(ch)) return false;
        }
        return true;
    }

    // Never normalize invisible Unicode FORMAT marks (zero-width/bidi)
    // into separators: this can turn a partially corrupted statement into
    // seemingly complete lexical evidence. Normal CR/LF/tab stay permitted.
    // Traverse code points so supplementary FORMAT characters are covered.
    private static boolean hasNonTextControl(String value) {
        if (value == null) return false;
        for (int i = 0; i < value.length();) {
            int cp = value.codePointAt(i);
            if ((Character.isISOControl(cp) && cp != '\n' && cp != '\r' && cp != '\t')
                    || Character.getType(cp) == Character.FORMAT
                    // Enclosing keycap marks and combining strike overlays alter
                    // visible evidence but NFD + \\p{M} would erase them.
                    // Preserve normal Latin accent/umlaut decomposition.
                    || cp == 0xFFFD // Replacement character signals lost decoding evidence
                    || (cp >= 0xFDD0 && cp <= 0xFDEF) // Permanent Unicode noncharacters
                    || ((cp & 0xFFFE) == 0xFFFE && cp <= 0x10FFFF)
                    || Character.getType(cp) == Character.ENCLOSING_MARK
                    || (cp >= 0x0334 && cp <= 0x0338)
                    // Nonspacing overlay modifiers also alter visible lexical evidence;
                    // NFD + \\p{M} would otherwise erase these modifiers.
                    || cp == 0x20D2 || cp == 0x20D3
                    || cp == 0x20E5 || cp == 0x20E6 || cp == 0x20EA
                    // NFD plus \\p{M} silently drops default-ignorable MARKs.
                    || cp == 0x034F // Combining Grapheme Joiner
                    || (cp >= 0x180B && cp <= 0x180F) // Mongolian selectors
                    || ((cp >= 0xFE00 && cp <= 0xFE0F)
                        && !(cp == 0xFE0F && isStandaloneEmojiVs16(value, i)))
                        // All other variation selectors remain denied.
                    || (cp >= 0xE0100 && cp <= 0xE01EF)) // Supplementary selectors
                return true;
            i += Character.charCount(cp);
        }
        return false;
    }

    // Unicode VS16 is a presentation marker for some visibly separate emoji.
    // A deliberately tiny, auditable allowlist avoids silently changing lexical
    // evidence: a selector inside/next to a word or punctuation fails closed.
    // Never use Character.isEmoji (Android API compatibility is not established).
    private static boolean isStandaloneEmojiVs16(String text, int selectorIndex) {
        if (selectorIndex == 0) return false;
        int base = text.codePointBefore(selectorIndex);
        if (base != 0x2764 && base != 0x2600 && base != 0x2708) return false;
        int baseStart = selectorIndex - Character.charCount(base);
        int nextIndex = selectorIndex + 1; // VS16 occupies one UTF-16 code unit.
        if (baseStart != 0 && !Character.isWhitespace(text.codePointBefore(baseStart)))
            return false;
        if (nextIndex == text.length() || Character.isWhitespace(text.codePointAt(nextIndex)))
            return true;
        // Punctuation may terminate a standalone visible emoji, but never
        // silently glue a variation selector to the following lexical word.
        int punctuation = text.codePointAt(nextIndex);
        return (punctuation == ',' || punctuation == '.' || punctuation == '!'
                || punctuation == '?' || punctuation == ';' || punctuation == ':')
                && (nextIndex + Character.charCount(punctuation) == text.length()
                    || Character.isWhitespace(text.codePointAt(
                        nextIndex + Character.charCount(punctuation))));
    }

    // Never trim identity/lineage keys: normalization can launder a corrupt G1 ID.
    private static String rawId(String v) { return v == null ? "" : v; }
    private static String safe(String v) { return v == null ? "" : v.trim(); }

    private static final Set<String> STOP = new HashSet<>(Arrays.asList(
        "aber","also","auch","das","dass","dein","deine","dem","den","der","des",
        "die","doch","du","ein","eine","einer","es","fuer","fur","hier","ich",
        "im","in","ist","ja","mein","meine","meinem","meinen","mit","nicht",
        "noch","oder","sie","so","und","von","war","was","wie","wir","wird",
        "zu","zum","zur","bitte","kannst","koenntest","erinnerst"));
    private static final Map<String,String> ALIASES = new HashMap<>();
    static {
        for (String w : Arrays.asList("name","heisse","heisst","hiess")) ALIASES.put(w,"name");
        for (String w : Arrays.asList("lieblingsgetraenk","getraenk","getraenke","getraenken"))
            ALIASES.put(w,"getraenk");
        for (String w : Arrays.asList("bevorzuge","bevorzugen","lieber","mag"))
            ALIASES.put(w,"praeferenz");
    }
}
