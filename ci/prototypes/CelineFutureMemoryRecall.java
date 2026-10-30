package de.yahya.ai;

import java.text.Normalizer;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.GeneralSecurityException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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
     * FUTURE-ONLY read-only owner attestation. A caller-asserted retired-ID set
     * is NOT evidence of source history. In a future G1 adapter both the HMAC
     * key and the expected, monotonic snapshot generation MUST be supplied
     * exclusively by the trusted G1 owner, never by chat, a provider or a tool.
     * This prototype has NO real production owner key or Android integration.
     */
    public static final class OwnerLineageCertificate {
        public final String generation;
        public final Set<String> retiredIds, confirmedRetiredParentIds;
        private final byte[] mac;

        public OwnerLineageCertificate(String generation, Set<String> retiredIds,
                Set<String> confirmedRetiredParentIds, byte[] mac) {
            this.generation = generation;
            this.retiredIds = retiredIds == null ? null
                    : Collections.unmodifiableSet(new HashSet<>(retiredIds));
            this.confirmedRetiredParentIds = confirmedRetiredParentIds == null ? null
                    : Collections.unmodifiableSet(new HashSet<>(confirmedRetiredParentIds));
            this.mac = mac == null ? null : mac.clone();
        }
    }

    /**
     * Read-only revision hook for a FUTURE G1 integration. An arbitrary caller
     * implementation is NOT a trusted monotonic source. The actual G1 owner
     * must provide an atomic, rollback-resistant revision plus snapshot lease.
     */
    public interface OwnerGenerationReader {
        String currentGeneration();
    }

    /**
     * FUTURE ONLY. The actual G1 adapter must own a read lock or equivalent
     * consistent-snapshot lease. Once acquired, it must block revision/revoke,
     * compaction, deletion and tombstone changes until close(). A caller-created
     * lease is NOT proof of any of these guarantees.
     */
    public interface OwnerSnapshotReadLease extends AutoCloseable {
        String generation();
        @Override void close();
    }

    public interface OwnerSnapshotLeaseAuthority {
        OwnerSnapshotReadLease acquireReadLease();
    }

    /** Synchronous memory-to-provider-context consumption, never deferred. */
    public interface ProviderRecallConsumer {
        void consume(List<Match> approvedMatches);
    }

    /**
     * FUTURE ONLY: trusted G1-owned context publisher, installed alongside the
     * source key, generation reader and snapshot lease (not by a lookup caller).
     * Only minimal text projections are supplied; no Candidate, Match, record
     * ID or correction metadata object escapes through this API. A registered
     * publisher can still retain strings: only real G1 owner trust, journal
     * and provider-dispatch integration can make that boundary meaningful.
     */
    public interface OwnerContextPublisher {
        void publish(String generation, List<String> approvedSummaries);
    }

    /**
     * FUTURE-ONLY dispatch lifecycle. "COMPLETED" means a trusted synchronous
     * owner publisher returned and the owner journal acknowledged that return;
     * it is NOT proof of external provider delivery or durable Android storage.
     * STARTED_INDETERMINATE must NEVER be automatically retried with a new ID.
     */
    public enum OwnerPublicationOutcome {
        REJECTED_BEFORE_START, STARTED_INDETERMINATE, COMPLETED
    }

    /**
     * Prototype contract for a G1-owned durable, transactionally serialized
     * dispatch journal. reserve() MUST atomically bind ID to digest and durably
     * write STARTED before returning NEW_STARTED. A prior STARTED entry must
     * NEVER be reset to NEW_STARTED on crash/restart. acknowledgeCompleted()
     * MUST durably write COMPLETED only for the exact matching identity.
     * For provider-context dispatch, only acknowledgeCompletedIfCurrent()
     * may commit a completed receipt; the ordinary legacy ack is forbidden.
     * Journal/key/revision/lease/publisher must all be installed by the trusted
     * G1 owner, not by a provider, chat message, plugin, or lookup caller.
     * No real journal is shipped with this standalone experiment.
     */
    public interface OwnerPublicationJournal {
        enum Reservation {
            NEW_STARTED, ALREADY_STARTED, ALREADY_COMPLETED, INTENT_CONFLICT
        }
        Reservation reserve(String ownerIssuedRequestId, byte[] intentSha256);
        // Historical direct acknowledgement is retained ONLY for stand-alone
        // journal fixture diagnostics; the dispatch method never calls it.
        boolean acknowledgeCompleted(String ownerIssuedRequestId, byte[] intentSha256);

        /**
         * FUTURE-ONLY strict completion capability. G1 must verify the exact
         * authenticated source generation AND durably commit COMPLETED under
         * the SAME owner-controlled transaction/serialization boundary as
         * revision changes, retractions and rollback protection. Checking
         * currentGeneration() separately is NOT atomic. A journal lacking
         * this explicitly implemented capability must fail closed, leaving
         * an already-started request indeterminate and never auto-retryable.
         * This interface is a contract, NOT a shipped G1/Android authority.
         */
        default boolean acknowledgeCompletedIfCurrent(String ownerIssuedRequestId,
                byte[] intentSha256, String certifiedGeneration) {
            return false;
        }
    }

    /**
     * FUTURE-ONLY strict G1 owner transaction capability. A production owner
     * MUST implement all three operations against the SAME trusted storage and
     * serialization domain; they may not be unrelated injected callbacks.
     *
     * dispatchUnderOwnerLock MUST, without a release/reacquire gap:
     *  1. compare certifiedGeneration with the live source revision and its
     *     independently protected nonrollbackable epoch/content checkpoint;
     *  2. durably reserve (request ID, digest, STARTED) before calling publisher;
     *  3. call publisher synchronously at most once for a NEW reservation;
     *  4. revalidate the still-live revision/tombstones AND atomically seal the
     *     matching COMPLETED receipt with the protected checkpoint;
     *  5. serialize all of that against corrections, revoke, compaction and
     *     parallel requests, even across processes and after recovery.
     *
     * An earlier STARTED or uncertain commit may NEVER be automatically retried.
     * A matching COMPLETED receipt may be read without re-publishing, but only
     * while its source generation is current. A callback exception/uncertain
     * persistence is STARTED_INDETERMINATE, not retryable. The publication
     * callback MUST NOT escape this transaction or run asynchronously.
     *
     * Java interface conformance cannot prove a caller-supplied adapter really
     * implements nonrollbackable persistence, protects plaintext downstream,
     * or survives power loss. No production G1/Android implementation exists.
     */
    public interface OwnerAtomicDispatchAuthority
            extends OwnerGenerationReader, OwnerSnapshotLeaseAuthority {
        OwnerPublicationOutcome dispatchUnderOwnerLock(String certifiedGeneration,
                String ownerIssuedRequestId, byte[] intentSha256,
                Runnable synchronousPublisher);
    }

    public static final class OwnerLineageVerifier {
        private final byte[] ownerKey;
        private final String expectedGeneration;
        private final OwnerGenerationReader ownerGenerationReader;
        private final OwnerSnapshotLeaseAuthority ownerSnapshotLeaseAuthority;
        private final OwnerContextPublisher ownerContextPublisher;
        private final OwnerPublicationJournal ownerPublicationJournal;
        private final OwnerAtomicDispatchAuthority ownerAtomicDispatchAuthority;

        /**
         * Historical fixture-only pinned generation. A long-lived verifier
         * using this constructor CAN replay a valid old HMAC after G1 changes.
         * Never use as proof of current production G1 state.
         */
        public OwnerLineageVerifier(byte[] trustedG1Key, String trustedG1Generation) {
            if (trustedG1Key == null || trustedG1Key.length < 32
                    || trustedG1Generation == null || trustedG1Generation.isEmpty())
                throw new IllegalArgumentException("Missing pinned G1 owner authority");
            ownerKey = trustedG1Key.clone();
            expectedGeneration = trustedG1Generation;
            ownerGenerationReader = null;
            ownerSnapshotLeaseAuthority = null;
            ownerContextPublisher = null;
            ownerPublicationJournal = null;
            ownerAtomicDispatchAuthority = null;
        }

        /**
         * FUTURE ONLY: read the owner's CURRENT revision for each operation.
         * The reader and secret are injected, NOT authenticated by this class.
         * Caller must own an externally protected source that cannot rollback;
         * this method alone does not solve durable anti-rollback or races with
         * a concurrent post-verification owner snapshot update.
         */
        public OwnerLineageVerifier(byte[] trustedG1Key,
                OwnerGenerationReader ownerGenerationReader) {
            this(trustedG1Key, ownerGenerationReader, null);
        }

        /**
         * Prototype callback is deliberately owner-bound at verifier creation,
         * not supplied by the individual lookup caller. A real G1 adapter must
         * establish the key, revision reader and lease as ONE trusted authority.
         */
        public OwnerLineageVerifier(byte[] trustedG1Key,
                OwnerGenerationReader ownerGenerationReader,
                OwnerSnapshotLeaseAuthority ownerSnapshotLeaseAuthority) {
            this(trustedG1Key, ownerGenerationReader, ownerSnapshotLeaseAuthority, null);
        }

        /** A future production G1 owner must create this COMPLETE authority. */
        public OwnerLineageVerifier(byte[] trustedG1Key,
                OwnerGenerationReader ownerGenerationReader,
                OwnerSnapshotLeaseAuthority ownerSnapshotLeaseAuthority,
                OwnerContextPublisher ownerContextPublisher) {
            this(trustedG1Key, ownerGenerationReader, ownerSnapshotLeaseAuthority,
                    ownerContextPublisher, null);
        }

        /** Journal is owner-bound at construction; callers cannot substitute it per dispatch. */
        public OwnerLineageVerifier(byte[] trustedG1Key,
                OwnerGenerationReader ownerGenerationReader,
                OwnerSnapshotLeaseAuthority ownerSnapshotLeaseAuthority,
                OwnerContextPublisher ownerContextPublisher,
                OwnerPublicationJournal ownerPublicationJournal) {
            if (trustedG1Key == null || trustedG1Key.length < 32
                    || ownerGenerationReader == null)
                throw new IllegalArgumentException("Missing G1 revision authority");
            ownerKey = trustedG1Key.clone();
            expectedGeneration = null;
            this.ownerGenerationReader = ownerGenerationReader;
            this.ownerSnapshotLeaseAuthority = ownerSnapshotLeaseAuthority;
            this.ownerContextPublisher = ownerContextPublisher;
            this.ownerPublicationJournal = ownerPublicationJournal;
            this.ownerAtomicDispatchAuthority = null; // legacy draft journal API only
        }

        /** One owner object is mandatory for the STRICT future-only dispatch path. */
        public OwnerLineageVerifier(byte[] trustedG1Key,
                OwnerAtomicDispatchAuthority trustedG1Owner,
                OwnerContextPublisher ownerContextPublisher) {
            if (trustedG1Key == null || trustedG1Key.length < 32
                    || trustedG1Owner == null || ownerContextPublisher == null)
                throw new IllegalArgumentException("Missing atomic G1 owner authority");
            this.ownerKey = trustedG1Key.clone();
            this.expectedGeneration = null;
            this.ownerGenerationReader = trustedG1Owner;
            this.ownerSnapshotLeaseAuthority = trustedG1Owner;
            this.ownerContextPublisher = ownerContextPublisher;
            this.ownerPublicationJournal = null;
            this.ownerAtomicDispatchAuthority = trustedG1Owner;
        }

        private OwnerSnapshotReadLease openOwnerReadLease() {
            // Old fixed-generation and reader-only constructors must NEVER
            // authorize the provider-consumption API, even with a valid MAC.
            if (ownerGenerationReader == null || ownerSnapshotLeaseAuthority == null)
                return null;
            try { return ownerSnapshotLeaseAuthority.acquireReadLease(); }
            catch (RuntimeException ex) { return null; }
        }

        private String pinnedGenerationNow() {
            try {
                String current = ownerGenerationReader == null
                        ? expectedGeneration : ownerGenerationReader.currentGeneration();
                return current != null && !current.isEmpty()
                        && current.length() <= MAX_ID_CHARS ? current : null;
            } catch (RuntimeException ex) {
                // A crashed/restarting G1 store must not silently authorize
                // any stale memory or correction suggestion.
                return null;
            }
        }

        private boolean isStillCurrent(OwnerLineageCertificate proof) {
            return proof != null && proof.generation != null
                    && proof.generation.equals(pinnedGenerationNow());
        }

        private boolean verifies(OwnerLineageCertificate proof, List<Candidate> records) {
            if (proof == null || proof.mac == null || proof.mac.length != 32)
                return false;
            String generationBefore = pinnedGenerationNow();
            if (generationBefore == null || !generationBefore.equals(proof.generation))
                return false;
            byte[] payload = certificatePayload(proof.generation, records,
                    proof.retiredIds, proof.confirmedRetiredParentIds);
            if (payload.length == 0) return false;
            try {
                Mac hmac = Mac.getInstance("HmacSHA256");
                hmac.init(new SecretKeySpec(ownerKey, "HmacSHA256"));
                // Recheck after cryptography to reject a revision that advanced
                // DURING verification. G1 still needs a true snapshot lease for
                // a race-free production read after this function returns.
                return MessageDigest.isEqual(hmac.doFinal(payload), proof.mac)
                        && generationBefore.equals(pinnedGenerationNow());
            } catch (GeneralSecurityException e) {
                return false; // Missing cryptographic provider must never allow recall.
            }
        }
    }

    /**
     * Deterministic, length-delimited projection snapshot for G1-owner signing.
     * Canonical ordering means reordering a complete owner snapshot changes
     * neither authority nor recall. This method DOES NOT sign anything.
     */
    public static byte[] certificatePayload(String generation, List<Candidate> records,
            Set<String> retiredIds, Set<String> confirmedRetiredParentIds) {
        if (generation == null || generation.isEmpty() || generation.length() > MAX_ID_CHARS
                || records == null || records.isEmpty() || records.size() > MAX_CANDIDATES
                || retiredIds == null || confirmedRetiredParentIds == null
                || retiredIds.size() > MAX_CANDIDATES
                || confirmedRetiredParentIds.size() > MAX_CANDIDATES)
            return new byte[0];
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            writeProofString(out, "CELINE_G1_OWNER_LINEAGE_READ_ONLY_V1");
            writeProofString(out, generation);
            ArrayList<String> retired = new ArrayList<>(retiredIds);
            ArrayList<String> confirmed = new ArrayList<>(confirmedRetiredParentIds);
            Collections.sort(retired);
            Collections.sort(confirmed);
            out.writeInt(retired.size());
            for (String id : retired) {
                if (!validId(id, false)) return new byte[0];
                writeProofString(out, id);
            }
            out.writeInt(confirmed.size());
            for (String id : confirmed) {
                if (!validId(id, false) || !retiredIds.contains(id)) return new byte[0];
                writeProofString(out, id);
            }
            ArrayList<Candidate> sorted = new ArrayList<>(records);
            if (sorted.contains(null)) return new byte[0];
            sorted.sort(Comparator.comparing(c -> c.id));
            out.writeInt(sorted.size());
            String previous = null;
            for (Candidate c : sorted) {
                if (!validId(c.id, false) || !validId(c.supersedesId, true)
                        || !validId(c.conflictWithId, true) || c.id.equals(previous))
                    return new byte[0];
                previous = c.id;
                writeProofString(out, c.id);
                writeProofString(out, c.summary);
                writeProofString(out, c.supersedesId);
                writeProofString(out, c.conflictWithId);
                writeProofString(out, c.privacy == null ? "UNKNOWN" : c.privacy.name());
                out.writeBoolean(c.authorizedForProvider);
                out.writeBoolean(c.userExplicit);
                out.writeLong(c.updatedAtMs);
                out.writeLong(c.expiresAtMs);
                out.writeLong(Double.doubleToLongBits(c.importance));
            }
            out.flush();
            return bytes.toByteArray();
        } catch (IOException | RuntimeException e) {
            return new byte[0];
        }
    }

    private static void writeProofString(DataOutputStream out, String value) throws IOException {
        byte[] utf8 = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        out.writeInt(utf8.length);
        out.write(utf8);
    }

    /**
     * FUTURE ONLY: source-owner-controlled publication boundary. In contrast
     * to consumeCertifiedProviderContext(), callers cannot inject a per-request
     * callback that receives internal Match/Candidate objects. The publisher
     * is registered with the G1 owner authority, and the handoff occurs during
     * the same certified snapshot lease. A missing registered publisher fails
     * closed. The owner still must supply a durable idempotent publication
     * journal, rollback protection and trusted provider adapter: this method
     * alone guarantees none of those properties and must NOT be integrated
     * into v83 or exposed to tools/providers as a public callable capability.
     *
     * The boolean and non-retryable exception have precisely the same semantics
     * as consumeCertifiedProviderContext: true = publisher returned, false =
     * publisher never started, IllegalStateException = started/uncertain.
     */
    public static boolean publishCertifiedOwnerContext(String query,
            List<Candidate> candidates, OwnerLineageCertificate certificate,
            OwnerLineageVerifier trustedG1Verifier, int maxResults, long now) {
        if (trustedG1Verifier == null || trustedG1Verifier.ownerContextPublisher == null)
            return false;
        return consumeCertifiedProviderContext(query, candidates, certificate,
                trustedG1Verifier, maxResults, now, matches -> {
                    ArrayList<String> summaries = new ArrayList<>(matches.size());
                    for (Match m : matches) summaries.add(m.candidate.summary);
                    trustedG1Verifier.ownerContextPublisher.publish(
                            certificate.generation, Collections.unmodifiableList(summaries));
                });
    }

    /**
     * Owner-only FUTURE prototype of at-most-once context dispatch. The opaque
     * request ID MUST originate from the trusted G1 owner. It is bound to the
     * exact verified projection, query, time, result bound and generation, but
     * this pure-Java class cannot authenticate who issued that ID.
     *
     * The journal reserves STARTED durably BEFORE any publisher invocation.
     * If process death or any exception occurs after attempted reserve, the
     * safest answer is STARTED_INDETERMINATE, never a retryable rejection. A
     * duplicate STARTED request remains indeterminate even after restart; a
     * duplicate COMPLETED request returns completed WITHOUT republishing.
     * Only synchronous owner publisher return is acknowledged; this is NOT a
     * remote provider receipt and NOT a production Android G1 implementation.
     */
    public static OwnerPublicationOutcome publishCertifiedOwnerContextOnce(
            String ownerIssuedRequestId, String query, List<Candidate> candidates,
            OwnerLineageCertificate certificate, OwnerLineageVerifier trustedG1Verifier,
            int maxResults, long now) {
        if (!validId(ownerIssuedRequestId, false) || query == null
                || query.length() > MAX_INPUT || hasNonTextControl(query)
                || certificate == null || trustedG1Verifier == null
                || trustedG1Verifier.ownerPublicationJournal == null
                || trustedG1Verifier.ownerContextPublisher == null)
            return OwnerPublicationOutcome.REJECTED_BEFORE_START;
        OwnerSnapshotReadLease lease = trustedG1Verifier.openOwnerReadLease();
        if (lease == null) return OwnerPublicationOutcome.REJECTED_BEFORE_START;
        boolean journalAttempted = false;
        try (OwnerSnapshotReadLease held = lease) {
            if (!certificate.generation.equals(held.generation())
                    || !trustedG1Verifier.verifies(certificate, candidates))
                return OwnerPublicationOutcome.REJECTED_BEFORE_START;
            List<Match> matches = retrieveAssumingOwnerVerified(query, candidates,
                    certificate.retiredIds, certificate.confirmedRetiredParentIds,
                    maxResults, now);
            if (!certificate.generation.equals(held.generation())
                    || !trustedG1Verifier.isStillCurrent(certificate))
                return OwnerPublicationOutcome.REJECTED_BEFORE_START;
            ArrayList<String> summaries = new ArrayList<>(matches.size());
            for (Match match : matches) summaries.add(match.candidate.summary);
            byte[] intent = ownerDispatchIntent(ownerIssuedRequestId, query,
                    certificate, candidates, maxResults, now, summaries);
            if (intent.length != 32)
                return OwnerPublicationOutcome.REJECTED_BEFORE_START;
            journalAttempted = true;
            OwnerPublicationJournal.Reservation reservation =
                    trustedG1Verifier.ownerPublicationJournal.reserve(ownerIssuedRequestId, intent.clone());
            // A legacy COMPLETED receipt is still bound to the CURRENT source
            // generation. A broken lease or revocation during the journal read
            // cannot authorize stale completion, let alone new publication.
            if (!certificate.generation.equals(held.generation())
                    || !trustedG1Verifier.isStillCurrent(certificate))
                return OwnerPublicationOutcome.STARTED_INDETERMINATE;
            if (reservation == OwnerPublicationJournal.Reservation.ALREADY_COMPLETED)
                return OwnerPublicationOutcome.COMPLETED;
            if (reservation != OwnerPublicationJournal.Reservation.NEW_STARTED)
                return OwnerPublicationOutcome.STARTED_INDETERMINATE;
            trustedG1Verifier.ownerContextPublisher.publish(certificate.generation,
                    Collections.unmodifiableList(summaries));
            // A misbehaving owner lease cannot turn data already sent under a
            // revoked source generation into an authenticated COMPLETED receipt.
            // Publisher-side effects cannot be undone, so remain indeterminate.
            if (!certificate.generation.equals(held.generation())
                    || !trustedG1Verifier.isStillCurrent(certificate))
                return OwnerPublicationOutcome.STARTED_INDETERMINATE;
            boolean acknowledged = trustedG1Verifier.ownerPublicationJournal.acknowledgeCompletedIfCurrent(
                    ownerIssuedRequestId, intent.clone(), certificate.generation);
            // Journal I/O itself can outlive a broken owner read-lease. Never
            // claim CURRENT completion if revocation happened during that I/O.
            // A positive remote acknowledgement cannot undo prior sink effects.
            if (!certificate.generation.equals(held.generation())
                    || !trustedG1Verifier.isStillCurrent(certificate))
                return OwnerPublicationOutcome.STARTED_INDETERMINATE;
            return acknowledged ? OwnerPublicationOutcome.COMPLETED
                    : OwnerPublicationOutcome.STARTED_INDETERMINATE;
        } catch (RuntimeException ex) {
            return journalAttempted ? OwnerPublicationOutcome.STARTED_INDETERMINATE
                    : OwnerPublicationOutcome.REJECTED_BEFORE_START;
        }
    }

    /**
     * STRICT future-only dispatch API: only one G1-owner atomic authority can
     * authorize source revision, journal reservation, callback and sealed receipt.
     * Unlike the historical OwnerPublicationJournal variant, no disconnected
     * generation-reader/journal combination can pass this capability gate.
     * The trusted owner still MUST implement the contract; no Android/G1
     * backing store or nonrollbackable hardware is provided here.
     */
    public static OwnerPublicationOutcome publishCertifiedOwnerContextAtomic(
            String ownerIssuedRequestId, String query, List<Candidate> candidates,
            OwnerLineageCertificate certificate, OwnerLineageVerifier trustedG1Verifier,
            int maxResults, long now) {
        if (!validId(ownerIssuedRequestId, false) || query == null
                || query.length() > MAX_INPUT || hasNonTextControl(query)
                || certificate == null || trustedG1Verifier == null
                || trustedG1Verifier.ownerAtomicDispatchAuthority == null
                || trustedG1Verifier.ownerContextPublisher == null)
            return OwnerPublicationOutcome.REJECTED_BEFORE_START;
        OwnerSnapshotReadLease lease = trustedG1Verifier.openOwnerReadLease();
        if (lease == null) return OwnerPublicationOutcome.REJECTED_BEFORE_START;
        boolean transactionAttempted = false;
        try (OwnerSnapshotReadLease held = lease) {
            if (!certificate.generation.equals(held.generation())
                    || !trustedG1Verifier.verifies(certificate, candidates))
                return OwnerPublicationOutcome.REJECTED_BEFORE_START;
            List<Match> matches = retrieveAssumingOwnerVerified(query, candidates,
                    certificate.retiredIds, certificate.confirmedRetiredParentIds,
                    maxResults, now);
            if (!certificate.generation.equals(held.generation())
                    || !trustedG1Verifier.isStillCurrent(certificate))
                return OwnerPublicationOutcome.REJECTED_BEFORE_START;
            ArrayList<String> summaries = new ArrayList<>(matches.size());
            for (Match match : matches) summaries.add(match.candidate.summary);
            byte[] intent = ownerDispatchIntent(ownerIssuedRequestId, query,
                    certificate, candidates, maxResults, now, summaries);
            if (intent.length != 32) return OwnerPublicationOutcome.REJECTED_BEFORE_START;
            List<String> approved = Collections.unmodifiableList(summaries);
            transactionAttempted = true;
            OwnerPublicationOutcome result = trustedG1Verifier.ownerAtomicDispatchAuthority
                    .dispatchUnderOwnerLock(certificate.generation,
                            ownerIssuedRequestId, intent.clone(),
                            () -> trustedG1Verifier.ownerContextPublisher.publish(
                                    certificate.generation, approved));
            // Even a wrongly implemented authority cannot claim successful
            // completion if its source changed between callback and return.
            if (!certificate.generation.equals(held.generation())
                    || !trustedG1Verifier.isStillCurrent(certificate))
                return OwnerPublicationOutcome.STARTED_INDETERMINATE;
            // Once transaction entry was attempted, an unsupported/null result
            // cannot honestly be described as a safe-to-retry rejection.
            if (result == null || result == OwnerPublicationOutcome.REJECTED_BEFORE_START)
                return OwnerPublicationOutcome.STARTED_INDETERMINATE;
            return result;
        } catch (RuntimeException ex) {
            return transactionAttempted ? OwnerPublicationOutcome.STARTED_INDETERMINATE
                    : OwnerPublicationOutcome.REJECTED_BEFORE_START;
        }
    }

    private static byte[] ownerDispatchIntent(String requestId, String query,
            OwnerLineageCertificate certificate, List<Candidate> candidates,
            int maxResults, long now, List<String> summaries) {
        byte[] projection = certificatePayload(certificate.generation, candidates,
                certificate.retiredIds, certificate.confirmedRetiredParentIds);
        if (projection.length == 0) return new byte[0];
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            writeProofString(out, "CELINE_G1_OWNER_DISPATCH_INTENT_V1");
            writeProofString(out, requestId);
            writeProofString(out, query);
            out.writeInt(maxResults);
            out.writeLong(now);
            out.writeInt(projection.length);
            out.write(projection);
            out.writeInt(summaries.size());
            for (String summary : summaries) writeProofString(out, summary);
            out.flush();
            return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());
        } catch (IOException | GeneralSecurityException | RuntimeException ex) {
            return new byte[0];
        }
    }

    /**
     * FUTURE ONLY, synchronous consumption under an owner-supplied read lease.
     * The old retrieveCertified() returns a list which may become stale BEFORE
     * a provider uses it. This bounded prototype therefore supplies memory
     * records only inside a callback while the OWNER lease is held. The future
     * G1 adapter must implement an atomic snapshot lock, a non-rollback owner
     * generation and trusted key custody; this pure Java class cannot verify
     * those properties. NEVER cache/defer approvedMatches or dispatch remote
     * provider I/O after the lease closes; this contract does NOT grant that.
     *
     * Returns true if synchronous consumption completed, even for zero matches.
     * False means the consumer was never invoked. If the consumer started and
     * then threw (or lease.close failed), throw IllegalStateException instead:
     * the provider may already have seen data and an automatic retry would
     * duplicate potentially irreversible effects. Treat that exception as an
     * INDETERMINATE/DO-NOT-RETRY outcome, never as a clean rejection.
     * This cannot prevent a malicious or mistaken sink retaining Match objects.
     */
    public static boolean consumeCertifiedProviderContext(String query,
            List<Candidate> candidates, OwnerLineageCertificate certificate,
            OwnerLineageVerifier trustedG1Verifier, int maxResults, long now,
            ProviderRecallConsumer consumer) {
        if (consumer == null || trustedG1Verifier == null || certificate == null)
            return false;
        OwnerSnapshotReadLease lease = trustedG1Verifier.openOwnerReadLease();
        if (lease == null) return false;
        boolean callbackStarted = false;
        try (OwnerSnapshotReadLease held = lease) {
            if (!certificate.generation.equals(held.generation())
                    || !trustedG1Verifier.verifies(certificate, candidates))
                return false;
            List<Match> matches = retrieveAssumingOwnerVerified(query, candidates,
                    certificate.retiredIds, certificate.confirmedRetiredParentIds,
                    maxResults, now);
            // The atomic owner lease must prevent all intervening revision
            // changes; these checks also reject a broken/stale lease witness.
            if (!certificate.generation.equals(held.generation())
                    || !trustedG1Verifier.isStillCurrent(certificate))
                return false;
            callbackStarted = true;
            consumer.consume(Collections.unmodifiableList(new ArrayList<>(matches)));
            return true;
        } catch (RuntimeException ex) {
            // A callback may already have published data (even if its own body
            // then threw), and close() may fail after a successful callback.
            // Never report either case as a safe, retryable pre-delivery reject.
            if (callbackStarted)
                throw new IllegalStateException(
                    "G1 context callback started: outcome indeterminate; do not retry", ex);
            return false;
        }
    }

    /** Certified path: without a trusted owner-pinned verifier, no unlock. */
    public static List<Match> retrieveCertified(String query, List<Candidate> candidates,
            OwnerLineageCertificate certificate, OwnerLineageVerifier trustedG1Verifier,
            int maxResults, long now) {
        if (trustedG1Verifier == null || !trustedG1Verifier.verifies(certificate, candidates))
            return Collections.emptyList();
        List<Match> result = retrieveAssumingOwnerVerified(query, candidates,
                certificate.retiredIds, certificate.confirmedRetiredParentIds, maxResults, now);
        // An owner revision may be replaced during policy evaluation, after
        // HMAC verification. Recheck before exposing any result. Future G1
        // integration still needs an atomic owner snapshot lease at return.
        return trustedG1Verifier.isStillCurrent(certificate)
                ? result : Collections.<Match>emptyList();
    }

    public static String unambiguousCorrectionTargetCertified(String correctionText,
            List<Candidate> candidates, OwnerLineageCertificate certificate,
            OwnerLineageVerifier trustedG1Verifier, long now) {
        if (trustedG1Verifier == null || !trustedG1Verifier.verifies(certificate, candidates))
            return "";
        String target = correctionAssumingOwnerVerified(correctionText, candidates,
                certificate.retiredIds, certificate.confirmedRetiredParentIds, now);
        return trustedG1Verifier.isStillCurrent(certificate) ? target : "";
    }

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
        // Caller-asserted confirmations are NEVER proof. The only permissive
        // path requires a separately pinned G1 HMAC verifier above.
        if (confirmedRetiredParentIds == null || !confirmedRetiredParentIds.isEmpty())
            return Collections.emptyList();
        return retrieveAssumingOwnerVerified(query, candidates, retiredIds,
                confirmedRetiredParentIds, maxResults, now);
    }

    // Internal only: no caller-visible path bypasses the source proof verification.
    private static List<Match> retrieveAssumingOwnerVerified(String query, List<Candidate> candidates,
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
            // A prune certificate explicitly describes an ABSENT ancestor.
            // An entry with that same ID in the supplied live projection is
            // contradictory source evidence, not permission to select a child.
            if (!validId(rawId(confirmed), false) || !retiredIds.contains(confirmed)
                    || ids.contains(confirmed))
                return Collections.emptyList();
        }
        // Build the same lineage-quarantine graph used by correction targeting:
        // a pruned, disputed, orphaned or forked branch must not leak stale facts.
        for (Candidate c : candidates) {
            if (!c.supersedesId.isEmpty()) replaced.add(c.supersedesId);
        }
        Set<String> unresolvedLineage = unresolvedLineageIds(
                candidates, ids, confirmedRetiredParentIds);
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
        if (confirmedRetiredParentIds == null || !confirmedRetiredParentIds.isEmpty())
            return "";
        return correctionAssumingOwnerVerified(correctionText, candidates,
                retiredIds, confirmedRetiredParentIds, now);
    }

    // Internal only: the certified entry point validates owner proof before use.
    private static String correctionAssumingOwnerVerified(String correctionText,
            List<Candidate> candidates, Set<String> retiredIds,
            Set<String> confirmedRetiredParentIds, long now) {
        Set<String> query = terms(correctionText);
        if (query.size() < 2) return "";
        List<Match> matches = retrieveAssumingOwnerVerified(correctionText, candidates,
                retiredIds, confirmedRetiredParentIds, 8, now);
        if (matches.isEmpty()) return "";
        Match best = matches.get(0);
        if (best.sharedTerms < 2 || best.sharedTerms * 2 < query.size()) return "";
        // Only a record that actually survived the full lineage/privacy policy
        // may be treated as an ordinary visible match. Raw permission flags do
        // not prove a G1 conflict or pruned ancestor has been resolved.
        Set<String> eligibleMatchIds = new HashSet<>();
        for (Match match : matches) eligibleMatchIds.add(match.candidate.id);
        // Correction suggestions must distinguish an actual source-present G1
        // parent from a certified-pruned one. An independently missing parent
        // is still an unresolved lineage dependency, even if a different
        // candidate has stronger lexical evidence.
        Set<String> projectedSourceIds = new HashSet<>();
        for (Candidate c : candidates) projectedSourceIds.add(c.id);
        Set<String> quarantinedLineage = unresolvedLineageIds(
                candidates, projectedSourceIds, confirmedRetiredParentIds);
        // Provider-safe recall deliberately excludes sensitive, unconsented and
        // untrusted G1 records. Those records can nevertheless be what the user
        // intends to correct. Do not mistake a single exportable hit for an
        // unambiguous memory ID when an inaccessible live record has equally
        // strong lexical evidence. This never exposes the rival's contents or
        // selects it automatically; the existing selected-ID UI remains the
        // fallback. Tombstoned/expired records are not live correction targets.
        for (Candidate rival : candidates) {
            if (rival == null || rival.id.equals(best.candidate.id)) continue;
            // Compaction/redaction may strip lexical evidence before projection.
            // A current G1 record with no usable summary might be the intended
            // correction target; neither privacy filtering nor a malformed
            // importance weight proves it is unrelated. Fall back to exact ID.
            // Malformed source timestamps do not prove a record has expired.
            // Retrieval withholds it, but correction targeting must account
            // for the uncertain rival rather than guessing a visible ID.
            boolean corruptTime = rival.updatedAtMs < 0 || rival.expiresAtMs < 0
                    || (rival.expiresAtMs > 0 && rival.expiresAtMs <= rival.updatedAtMs);
            boolean potentiallyCurrent = now >= 0 && (corruptTime
                    || (rival.updatedAtMs <= now
                        && (rival.expiresAtMs == 0 || rival.expiresAtMs > now)));
            if (!potentiallyCurrent) continue;
            if (retiredIds.contains(rival.id)) continue;
            if (rival.summary.isEmpty()) return "";
            // A raw eligible record may be absent from retrieve because its
            // conflict, fork or retired-parent ancestry is unresolved. It is
            // still a plausible user correction target: abstain on equal text.
            if (eligibleMatchIds.contains(rival.id)) continue;
            // The tokenizer fails closed for mixed scripts, malformed or
            // over-budget lexical inputs. Its empty result is not evidence
            // that this still-current source record is unrelated to the edit.
            Set<String> rivalTerms = terms(rival.summary);
            if (rivalTerms.isEmpty()) {
                // A successfully parsed, plain German stopword/short-word or
                // punctuation-only source has no correction anchors. Its empty
                // term set is not the same as an unsupported script, an emoji,
                // damaged source text, or an over-budget token set.
                if (!corruptTime && Double.isFinite(rival.importance)
                        && hasOnlyKnownDiscardedLexemes(rival.summary)) continue;
                return "";
            }
            int rivalSharedTerms = overlap(query, rivalTerms);
            // Unlike an ordinary weaker (but trustworthy) memory, a
            // potentially-live orphan or corrupt metadata record cannot be
            // safely dismissed by ranking against a well-formed winner. Two
            // subject anchors already make the unknown record plausible.
            boolean unreliableEvidence = quarantinedLineage.contains(rival.id)
                    || corruptTime || !Double.isFinite(rival.importance);
            if (rivalSharedTerms >= 2 && (rivalSharedTerms >= best.sharedTerms
                    || unreliableEvidence)) return "";
        }
        if (matches.size() > 1) {
            Match next = matches.get(1);
            // Actual distinct lexical evidence, not a tie broken by recency/importance.
            if (best.sharedTerms <= next.sharedTerms) return "";
        }
        return best.candidate.id;
    }

    /**
     * Shared source-lineage quarantine for both provider-safe retrieval and
     * the non-mutating correction-ID suggestion. A higher lexical score can
     * never authenticate a competing unresolved G1 branch.
     */
    private static Set<String> unresolvedLineageIds(List<Candidate> candidates,
            Set<String> sourceIds, Set<String> confirmedRetiredParentIds) {
        // G1 conflictWithId records an unresolved contradiction, NOT a vetted
        // supersession. An orphan or sibling fork is likewise never evidence
        // of an authoritative winner; quarantine descendants transitively.
        Set<String> conflicted = new HashSet<>();
        Map<String, List<String>> successorsByParent = new HashMap<>();
        for (Candidate c : candidates) {
            if (!c.supersedesId.isEmpty()) {
                successorsByParent.computeIfAbsent(c.supersedesId, k -> new ArrayList<>()).add(c.id);
            }
            if (!c.conflictWithId.isEmpty()) {
                conflicted.add(c.id);
                conflicted.add(c.conflictWithId);
            }
        }
        Set<String> orphaned = new HashSet<>();
        for (Candidate c : candidates) {
            if (!c.supersedesId.isEmpty() && !sourceIds.contains(c.supersedesId)
                    && !confirmedRetiredParentIds.contains(c.supersedesId))
                orphaned.add(c.id);
        }
        Set<String> unresolvedLineage = new HashSet<>(conflicted);
        unresolvedLineage.addAll(orphaned);
        List<String> pending = new ArrayList<>(unresolvedLineage);
        for (List<String> siblings : successorsByParent.values()) {
            if (siblings.size() < 2) continue;
            for (String child : siblings) {
                if (unresolvedLineage.add(child)) pending.add(child);
            }
        }
        // Malformed cyclic retractions (A->B->A, A->A) do not attest
        // historical precedence. Seed one node per detected cycle; the same
        // bounded descendant walk below quarantines the entire cycle and all
        // its descendants. Previously these rivals were retrieval-invisible
        // but could be overlooked by the automatic correction target guard.
        Map<String, String> parentById = new HashMap<>();
        for (Candidate c : candidates) parentById.put(c.id, c.supersedesId);
        Set<String> cycleWalkDone = new HashSet<>();
        for (Candidate c : candidates) {
            if (cycleWalkDone.contains(c.id)) continue;
            Set<String> walked = new HashSet<>();
            String cursor = c.id;
            while (cursor != null && !cursor.isEmpty()
                    && !cycleWalkDone.contains(cursor) && walked.add(cursor)) {
                cursor = parentById.get(cursor);
            }
            if (cursor != null && walked.contains(cursor)
                    && unresolvedLineage.add(cursor)) pending.add(cursor);
            cycleWalkDone.addAll(walked);
        }
        for (int i = 0; i < pending.size(); i++) {
            List<String> children = successorsByParent.get(pending.get(i));
            if (children == null) continue;
            for (String child : children) {
                if (unresolvedLineage.add(child)) pending.add(child);
            }
        }
        return unresolvedLineage;
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

    // This is ONLY a correction-target abstention exception, never a recall
    // authorization rule. Use an intentionally small, inspectable alphabet;
    // Unicode-only content (including emoji) cannot silently be called irrelevant.
    // An empty/redacted summary and invalid/over-budget text remain UNKNOWN.
    private static boolean hasOnlyKnownDiscardedLexemes(String summary) {
        if (summary == null || summary.isEmpty() || summary.length() > MAX_INPUT
                || !wellFormedUtf16(summary) || hasNonTextControl(summary)
                || hasUnrepresentableLexicalChars(summary)) return false;
        for (int i = 0; i < summary.length(); i++) {
            char ch = summary.charAt(i);
            if (ch > 127 && ch != 'ä' && ch != 'ö' && ch != 'ü'
                    && ch != 'Ä' && ch != 'Ö' && ch != 'Ü' && ch != 'ß') return false;
        }
        String value = safe(summary).toLowerCase(Locale.GERMANY)
            .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        value = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .replaceAll("[^a-z0-9]+", " ").trim();
        if (value.isEmpty()) return true; // visible plain punctuation only
        for (String word : value.split("\\s+")) {
            if (word.length() >= 3 && !STOP.contains(word)) return false;
        }
        return true;
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
                    // Private-use glyphs have no supported lexical meaning here.
                    // NFD/tokenization would otherwise drop them as separators.
                    || Character.getType(cp) == Character.PRIVATE_USE
                    // Unknown/unassigned scalars cannot be lexical evidence.
                    || Character.getType(cp) == Character.UNASSIGNED
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
