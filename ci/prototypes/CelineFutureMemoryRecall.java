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

    /**
     * FUTURE-ONLY, read-only MANUAL recovery gate, NOT a recovery operation.
     * The pinned Ed25519 key and nonrollbackable high-water value MUST come
     * from an independent trusted G1 owner, never chat, the caller or local
     * snapshot/temp files. This Java prototype cannot provide either owner.
     * Even a valid proof permits only quarantine of a dangling transaction
     * while preserving an already revoked exact ID; ACTIVE is never restored.
     */
    public enum RecoveryDisposition { BLOCKED, QUARANTINE_REVOKED_ONLY }

    public static byte[] revokedRecoveryPayload(String id, long revision,
            String orphanSha256, String nonce) {
        if (!validId(id, false) || revision < 2 || orphanSha256 == null
                || !orphanSha256.matches("[0-9a-f]{64}") || nonce == null
                || !nonce.matches("[A-Za-z0-9_-]{16,64}")) return new byte[0];
        return ("future-g1/revoked-recovery/v1\n" + id + "\n" + revision
                + "\nREVOKED\n" + orphanSha256 + "\n" + nonce + "\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    public static RecoveryDisposition attestRevokedOrphan(String exactId,
            long sourceRevision, String sourceStatus, long independentHighWater,
            String orphanSha256, String nonce, byte[] ownerSignature,
            java.security.PublicKey pinnedOwnerKey) {
        if (!"REVOKED".equals(sourceStatus) || sourceRevision < 2
                || sourceRevision != independentHighWater || pinnedOwnerKey == null
                || !"EdDSA".equalsIgnoreCase(pinnedOwnerKey.getAlgorithm())
                        && !"Ed25519".equalsIgnoreCase(pinnedOwnerKey.getAlgorithm())
                || ownerSignature == null || ownerSignature.length != 64)
            return RecoveryDisposition.BLOCKED;
        byte[] payload = revokedRecoveryPayload(exactId, sourceRevision,
                orphanSha256, nonce);
        if (payload.length == 0) return RecoveryDisposition.BLOCKED;
        try {
            java.security.Signature verifier = java.security.Signature.getInstance("Ed25519");
            verifier.initVerify(pinnedOwnerKey);
            verifier.update(payload);
            return verifier.verify(ownerSignature)
                    ? RecoveryDisposition.QUARANTINE_REVOKED_ONLY
                    : RecoveryDisposition.BLOCKED;
        } catch (GeneralSecurityException e) {
            return RecoveryDisposition.BLOCKED;
        }
    }

    /**
     * FUTURE-ONLY test contract: an INDEPENDENT owner supplies the public key,
     * unrollbackable revision, revocation flag and challenge. The owner MUST
     * atomically compare AND durably consume the one-use challenge in its own
     * security domain. File-backed test owners below are NOT real protection.
     */
    public static final class RecoveryOwnerSnapshot {
        public final long highWater;
        public final String challenge;
        public final java.security.PublicKey pinnedKey;
        public final boolean revoked;
        public RecoveryOwnerSnapshot(long highWater, String challenge,
                java.security.PublicKey pinnedKey, boolean revoked) {
            this.highWater = highWater;
            this.challenge = challenge;
            this.pinnedKey = pinnedKey;
            this.revoked = revoked;
        }
    }

    public interface OneShotRecoveryOwner {
        RecoveryOwnerSnapshot snapshot(String exactId);
        /** Atomic CAS, permanent across process restarts and owner-key rotation. */
        boolean claimIfCurrent(String exactId, long highWater, String challenge,
                byte[] exactPinnedKeyEncoding);
    }

    /** A valid signature alone is replayable; a trustworthy one-use owner is required. */
    public static RecoveryDisposition attestRevokedOrphanOnce(String exactId,
            long sourceRevision, String sourceStatus, String orphanSha256,
            String challenge, byte[] ownerSignature, OneShotRecoveryOwner owner) {
        if (owner == null || challenge == null) return RecoveryDisposition.BLOCKED;
        try {
            RecoveryOwnerSnapshot state = owner.snapshot(exactId);
            if (state == null || !state.revoked || state.pinnedKey == null
                    || !challenge.equals(state.challenge)) return RecoveryDisposition.BLOCKED;
            if (attestRevokedOrphan(exactId, sourceRevision, sourceStatus,
                    state.highWater, orphanSha256, challenge, ownerSignature,
                    state.pinnedKey) != RecoveryDisposition.QUARANTINE_REVOKED_ONLY)
                return RecoveryDisposition.BLOCKED;
            byte[] exactKey = state.pinnedKey.getEncoded();
            if (exactKey == null || exactKey.length == 0) return RecoveryDisposition.BLOCKED;
            return owner.claimIfCurrent(exactId, state.highWater, challenge, exactKey)
                    ? RecoveryDisposition.QUARANTINE_REVOKED_ONLY
                    : RecoveryDisposition.BLOCKED;
        } catch (RuntimeException e) {
            // Missing/corrupt external authority never authorizes recovery.
            return RecoveryDisposition.BLOCKED;
        }
    }

    /**
     * FUTURE ONLY: owner-held atomic recovery capability, NOT a trusted Android
     * implementation. A real product composition root must supply a pinned,
     * independently authenticated, nonrollbackable owner handle. This handle
     * must NEVER be built from a provider/chat-selected owner path or key.
     *
     * Unlike snapshot() followed by claimIfCurrent(), validation of identity,
     * key, revision, nonce, signature, revoked status and single-use consumption
     * MUST happen inside one owner-side atomic operation. Returning a successful
     * value from a caller-supplied implementation is not a security guarantee.
     * A lost/unavailable authority must BLOCK; it must not reload a file-based
     * owner and re-issue or replay a spent challenge.
     */
    public interface OwnerHeldRecoveryCapability {
        RecoveryDisposition validateAndConsume(String exactId, long sourceRevision,
                String sourceStatus, String orphanSha256, String challenge,
                byte[] ownerSignature);
    }

    /** Conditional, fail-closed adapter: this class provides NO trusted owner. */
    public static RecoveryDisposition attestRevokedOrphanWithOwnerHeldClaim(
            String exactId, long sourceRevision, String sourceStatus,
            String orphanSha256, String challenge, byte[] ownerSignature,
            OwnerHeldRecoveryCapability capability) {
        if (capability == null || !"REVOKED".equals(sourceStatus)
                || sourceRevision < 2 || revokedRecoveryPayload(exactId,
                        sourceRevision, orphanSha256, challenge).length == 0
                || ownerSignature == null || ownerSignature.length != 64)
            return RecoveryDisposition.BLOCKED;
        try {
            RecoveryDisposition result = capability.validateAndConsume(exactId,
                    sourceRevision, sourceStatus, orphanSha256, challenge,
                    ownerSignature.clone());
            return result == RecoveryDisposition.QUARANTINE_REVOKED_ONLY
                    ? result : RecoveryDisposition.BLOCKED;
        } catch (RuntimeException e) {
            return RecoveryDisposition.BLOCKED;
        }
    }

    /**
     * FUTURE-ONLY conditional external witness boundary, NOT durable custody.
     * NO_AUTHORITY and UNCERTAIN are non-authorizing, distinct from a proven
     * negative BLOCKED decision. This prototype does not provision a witness,
     * secure its signing key, protect its epoch/ledger or perform Android IPC.
     * Only an independent operator/G1 owner may install the capability. Never
     * accept a chat/provider/caller-selected capability or a fresh self-pin.
     */
    public enum WitnessClaimOutcome {
        NO_AUTHORITY, UNCERTAIN, BLOCKED, QUARANTINE_REVOKED_ONLY
    }

    public interface OperatorProvisionedWitnessEpochAuthority {
        /**
         * Atomically bind verified source signature, exact revoked ID, epoch,
         * challenge and a permanently consumed claim in protected storage.
         * A positive value is trustworthy ONLY when the actual implementation
         * has independently anchored identity and rollback-resistant custody.
         */
        WitnessClaimOutcome claimRevokedOnce(String exactId, long sourceRevision,
                String sourceStatus, String orphanSha256, String challenge,
                byte[] ownerSignature, long minimumWitnessEpoch);
    }

    /** No alternative local or caller-chosen recovery on missing/uncertain root. */
    public static WitnessClaimOutcome attestRevokedOrphanWithOperatorWitness(
            String exactId, long sourceRevision, String sourceStatus,
            String orphanSha256, String challenge, byte[] ownerSignature,
            long minimumWitnessEpoch, OperatorProvisionedWitnessEpochAuthority authority) {
        if (authority == null) return WitnessClaimOutcome.NO_AUTHORITY;
        if (minimumWitnessEpoch <= 0 || !"REVOKED".equals(sourceStatus)
                || sourceRevision < 2 || revokedRecoveryPayload(exactId,
                        sourceRevision, orphanSha256, challenge).length == 0
                || ownerSignature == null || ownerSignature.length != 64)
            return WitnessClaimOutcome.BLOCKED;
        try {
            WitnessClaimOutcome result = authority.claimRevokedOnce(exactId,
                    sourceRevision, sourceStatus, orphanSha256, challenge,
                    ownerSignature.clone(), minimumWitnessEpoch);
            return result == null ? WitnessClaimOutcome.UNCERTAIN : result;
        } catch (RuntimeException e) {
            return WitnessClaimOutcome.UNCERTAIN;
        }
    }

    /**
     * FUTURE-ONLY two-authority experiment: the ordinary witness is NOT the
     * rollback root. Its conditional admission is also gated by a separately
     * operator-provisioned single-use claim ledger. The second authority MUST
     * hold its pinned identity, epoch and spent claims independently, atomically
     * and nonrollbackably; a caller callback alone does NOT supply that trust.
     *
     * Fail closed if EITHER authority is unavailable/uncertain/denies. A
     * successful double decision permits only REVOKED quarantine, not restore.
     * This does not provide a distributed transaction: a ledger consume may
     * succeed when the outer operation fails; safely retrying requires an
     * independently designed receipt/ack protocol. No Android implementation.
     */
    public static WitnessClaimOutcome attestRevokedWithIndependentOnceLedger(
            String exactId, long sourceRevision, String sourceStatus,
            String orphanSha256, String challenge, byte[] ownerSignature,
            long minimumWitnessEpoch,
            OperatorProvisionedWitnessEpochAuthority ordinaryWitness,
            OperatorProvisionedWitnessEpochAuthority independentlyPinnedLedger) {
        if (ordinaryWitness == null || independentlyPinnedLedger == null)
            return WitnessClaimOutcome.NO_AUTHORITY;
        WitnessClaimOutcome ordinary = attestRevokedOrphanWithOperatorWitness(
                exactId, sourceRevision, sourceStatus, orphanSha256, challenge,
                ownerSignature, minimumWitnessEpoch, ordinaryWitness);
        if (ordinary != WitnessClaimOutcome.QUARANTINE_REVOKED_ONLY)
            return ordinary;
        return attestRevokedOrphanWithOperatorWitness(exactId, sourceRevision,
                sourceStatus, orphanSha256, challenge, ownerSignature,
                minimumWitnessEpoch, independentlyPinnedLedger);
    }

    /**
     * FUTURE-ONLY/TEST-ONLY: a third *independently provisioned* receipt guard
     * can fence a rollback of both the original witness and its once-ledger,
     * conditional on the guard retaining its own nonrollbackable spent state.
     *
     * This is NOT a production trust root or an atomic three-party commit.
     * Earlier claims may be consumed when a later authority refuses/vanishes.
     * An untrusted caller can substitute every interface; real G1 integration
     * requires authenticated, operator-custodied, rollback-resistant storage.
     * Only REVOKED quarantine is ever authorized; ACTIVE never is.
     */
    public static WitnessClaimOutcome attestRevokedWithIndependentReceiptFence(
            String exactId, long sourceRevision, String sourceStatus,
            String orphanSha256, String challenge, byte[] ownerSignature,
            long minimumWitnessEpoch,
            OperatorProvisionedWitnessEpochAuthority ordinaryWitness,
            OperatorProvisionedWitnessEpochAuthority onceLedger,
            OperatorProvisionedWitnessEpochAuthority independentlyPinnedReceiptGuard) {
        if (ordinaryWitness == null || onceLedger == null || independentlyPinnedReceiptGuard == null)
            return WitnessClaimOutcome.NO_AUTHORITY;
        WitnessClaimOutcome pair = attestRevokedWithIndependentOnceLedger(
                exactId, sourceRevision, sourceStatus, orphanSha256, challenge,
                ownerSignature, minimumWitnessEpoch, ordinaryWitness, onceLedger);
        if (pair != WitnessClaimOutcome.QUARANTINE_REVOKED_ONLY)
            return pair;
        return attestRevokedOrphanWithOperatorWitness(exactId,
                sourceRevision, sourceStatus, orphanSha256, challenge,
                ownerSignature, minimumWitnessEpoch, independentlyPinnedReceiptGuard);
    }

    /**
     * FUTURE ONLY: caller-independent G1-owned receipt-commit authority.
     * The coordinator must atomically verify all original witness/once/guard
     * evidence, durably record the one-shot claim AND its acknowledgement,
     * and never return COMMITTED after owner revision/receipt rollback.
     * This method merely validates the request and preserves uncertainty;
     * it does NOT implement a trusted coordinator, durable storage or Android.
     * A caller-chosen coordinator or request ID is never production authority.
     */
    public enum AtomicReceiptOutcome {
        REJECTED_BEFORE_START, NO_AUTHORITY, STARTED_INDETERMINATE, COMMITTED
    }

    public interface OwnerAtomicReceiptCoordinator {
        AtomicReceiptOutcome commitRevokedReceipt(String ownerIssuedReceiptId,
                String exactId, long sourceRevision, String sourceStatus,
                String orphanSha256, String challenge, byte[] ownerSignature,
                long minimumWitnessEpoch);
    }

    public static AtomicReceiptOutcome attestRevokedWithAtomicReceiptCoordinator(
            String ownerIssuedReceiptId, String exactId, long sourceRevision,
            String sourceStatus, String orphanSha256, String challenge,
            byte[] ownerSignature, long minimumWitnessEpoch,
            OwnerAtomicReceiptCoordinator coordinator) {
        if (coordinator == null) return AtomicReceiptOutcome.NO_AUTHORITY;
        if (ownerIssuedReceiptId == null || ownerIssuedReceiptId.isBlank()
                || ownerIssuedReceiptId.length() > 128
                || minimumWitnessEpoch <= 0 || !"REVOKED".equals(sourceStatus)
                || sourceRevision < 2 || revokedRecoveryPayload(exactId,
                        sourceRevision, orphanSha256, challenge).length == 0
                || ownerSignature == null || ownerSignature.length != 64)
            return AtomicReceiptOutcome.REJECTED_BEFORE_START;
        try {
            AtomicReceiptOutcome outcome = coordinator.commitRevokedReceipt(
                    ownerIssuedReceiptId, exactId, sourceRevision, sourceStatus,
                    orphanSha256, challenge, ownerSignature.clone(), minimumWitnessEpoch);
            // After dispatch, a missing/throwing acknowledgement is not
            // retryable denial: an irreversible claim may have happened.
            return outcome == null ? AtomicReceiptOutcome.STARTED_INDETERMINATE : outcome;
        } catch (RuntimeException e) {
            return AtomicReceiptOutcome.STARTED_INDETERMINATE;
        }
    }

    /**
     * FUTURE-ONLY conditionally authenticated journal receipt. This is NOT
     * persistent, Android-backed or a way to bootstrap trust from a new key.
     * The authentic pinned keys, owner high-water, and external journal floor
     * MUST be supplied by a separately controlled, rollback-resistant custodian.
     * The custodian MUST recheck and advance its floor atomically in consumeOnce;
     * a signed receipt and an ordinary restored file alone do NOT stop replay.
     */
    public interface ExternallyPinnedMonotonicJournal {
        java.security.PublicKey pinnedOriginalOwnerKey();
        java.security.PublicKey pinnedJournalKey();
        long trustedOwnerRevision();
        long trustedConsumedSequence();
        AtomicReceiptOutcome consumeOnce(String claimId, long sequence,
                byte[] exactSignedPayload, byte[] journalSignature,
                byte[] originalOwnerSignature);
    }

    public static byte[] independentJournalReceiptPayload(String claimId,
            String exactId, long sourceRevision, String sourceStatus,
            String orphanSha256, String challenge, byte[] originalOwnerSignature,
            long witnessEpoch, long journalSequence) {
        if (claimId == null || !claimId.matches("[A-Za-z0-9_-]{8,128}")
                || witnessEpoch <= 0 || journalSequence <= 0
                || originalOwnerSignature == null || originalOwnerSignature.length != 64
                || !"REVOKED".equals(sourceStatus)
                || revokedRecoveryPayload(exactId, sourceRevision,
                        orphanSha256, challenge).length == 0) return new byte[0];
        // Fixed-format ASCII, bounded G1 IDs/claim IDs, and Base64 owner proof
        // avoid delimiter/encoding ambiguity across independent JVM processes.
        return ("future-g1/pinned-monotonic-journal/v1\n" + claimId + "\n"
                + exactId + "\n" + sourceRevision + "\nREVOKED\n"
                + orphanSha256 + "\n" + challenge + "\n"
                + java.util.Base64.getEncoder().encodeToString(originalOwnerSignature)
                + "\n" + witnessEpoch + "\n" + journalSequence + "\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Source and receipt signatures are checked BEFORE handing the claim to
     * the external custodian. A dispatch failure is uncertain, never retryable
     * negative proof. A COMMITTED answer is only conditional on the caller's
     * independent provision and the custodian's *real* nonrollbackable CAS.
     */
    public static AtomicReceiptOutcome attestRevokedWithPinnedMonotonicJournal(
            String claimId, String exactId, long sourceRevision,
            String sourceStatus, String orphanSha256, String challenge,
            byte[] originalOwnerSignature, long witnessEpoch,
            long journalSequence, byte[] journalSignature,
            ExternallyPinnedMonotonicJournal journal) {
        if (journal == null) return AtomicReceiptOutcome.NO_AUTHORITY;
        byte[] payload = independentJournalReceiptPayload(claimId, exactId,
                sourceRevision, sourceStatus, orphanSha256, challenge,
                originalOwnerSignature, witnessEpoch, journalSequence);
        if (payload.length == 0 || journalSignature == null
                || journalSignature.length != 64) return AtomicReceiptOutcome.REJECTED_BEFORE_START;
        try {
            java.security.PublicKey ownerKey = journal.pinnedOriginalOwnerKey();
            java.security.PublicKey journalKey = journal.pinnedJournalKey();
            long revision = journal.trustedOwnerRevision();
            long consumed = journal.trustedConsumedSequence();
            if (ownerKey == null || journalKey == null || revision < 2
                    || consumed < 0 || consumed == Long.MAX_VALUE)
                return AtomicReceiptOutcome.NO_AUTHORITY;
            // A caller may accidentally (or maliciously) provision the very
            // same Ed25519 identity as both owner and "independent" journal.
            // Two signatures made with one key are NOT independent custody.
            byte[] ownerPin = ownerKey.getEncoded();
            byte[] journalPin = journalKey.getEncoded();
            if (ownerPin == null || journalPin == null || ownerPin.length == 0
                    || journalPin.length == 0
                    || java.util.Arrays.equals(ownerPin, journalPin))
                return AtomicReceiptOutcome.NO_AUTHORITY;
            if (journalSequence != consumed + 1
                    || attestRevokedOrphan(exactId, sourceRevision, sourceStatus,
                            revision, orphanSha256, challenge,
                            originalOwnerSignature, ownerKey)
                    != RecoveryDisposition.QUARANTINE_REVOKED_ONLY)
                return AtomicReceiptOutcome.REJECTED_BEFORE_START;
            java.security.Signature verify = java.security.Signature.getInstance("Ed25519");
            verify.initVerify(journalKey);
            verify.update(payload);
            if (!verify.verify(journalSignature))
                return AtomicReceiptOutcome.REJECTED_BEFORE_START;
        } catch (java.security.SignatureException e) {
            // Malformed/forged signature is a proven pre-dispatch rejection,
            // not an unavailable authority that might invite re-enrollment.
            return AtomicReceiptOutcome.REJECTED_BEFORE_START;
        } catch (GeneralSecurityException | RuntimeException e) {
            return AtomicReceiptOutcome.NO_AUTHORITY;
        }
        try {
            AtomicReceiptOutcome result = journal.consumeOnce(claimId,
                    journalSequence, payload.clone(), journalSignature.clone(),
                    originalOwnerSignature.clone());
            // A custodian that disappears AFTER dispatch cannot prove that
            // no irreversible journal consumption already occurred.
            return result == null || result == AtomicReceiptOutcome.NO_AUTHORITY
                    ? AtomicReceiptOutcome.STARTED_INDETERMINATE : result;
        } catch (RuntimeException e) {
            return AtomicReceiptOutcome.STARTED_INDETERMINATE;
        }
    }

    /**
     * FUTURE-ONLY reconciliation of an acknowledgement lost AFTER a one-shot
     * receipt may have been committed. No consume/retry is allowed here.
     * A NOT_FOUND proof is NOT a safe-to-retry authorization: a rolled-back
     * authority can sign false absence with its unchanged original key.
     * The original expected authority key must have been independently
     * provisioned before the first commit; a query response cannot enroll it.
     */
    public enum ReceiptStatusOutcome {
        CONSUMED_VERIFIED, ABSENT_UNSAFE_TO_RETRY, NO_AUTHORITY, UNVERIFIABLE, REMAIN_INDETERMINATE
    }

    public static final class SignedReceiptStatus {
        public final String claimId;
        public final long sequence;
        public final String status;
        private final byte[] signature;

        public SignedReceiptStatus(String claimId, long sequence,
                String status, byte[] signature) {
            this.claimId = claimId;
            this.sequence = sequence;
            this.status = status;
            this.signature = signature == null ? null : signature.clone();
        }

        public byte[] signature() {
            return signature == null ? null : signature.clone();
        }
    }

    public interface ReadOnlyReceiptStatusAuthority {
        java.security.PublicKey originalPinnedIdentity();
        // This method MUST only read original independent custody. It must
        // never call consumeOnce, retry the claim, or alter a ledger/floor.
        SignedReceiptStatus queryExistingClaim(String ownerIssuedClaim,
                String challengeNonce);
    }

    /**
     * FUTURE-ONLY source-owner consent for ONE exact revoked claim. It must
     * be signed by the independently enrolled original G1 owner key BEFORE
     * root/operator attestations. A caller-supplied 64-byte value is not proof.
     * The later query challenge belongs to the separate read-only receipts.
     */
    public static byte[] originalOwnerClaimPayload(String claimId,
            String exactId, long sourceRevision) {
        if (claimId == null || !claimId.matches("[A-Za-z0-9_-]{8,128}")
                || exactId == null || !exactId.matches("[A-Za-z0-9_-]{1,128}")
                || sourceRevision < 2) return new byte[0];
        return ("future-g1/source-owner-claim/v1\n" + claimId + "\n"
                + exactId + "\n" + sourceRevision + "\nREVOKED\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] signedReceiptStatusPayload(String claimId,
            String exactId, long sourceRevision, byte[] originalOwnerSignature,
            String queryNonce, long consumedSequence, String status) {
        if (claimId == null || !claimId.matches("[A-Za-z0-9_-]{8,128}")
                || exactId == null || !exactId.matches("[A-Za-z0-9_-]{1,128}")
                || sourceRevision < 2 || originalOwnerSignature == null
                || originalOwnerSignature.length != 64 || queryNonce == null
                || !queryNonce.matches("[A-Za-z0-9_-]{16,128}")
                || consumedSequence < 0
                || !("CONSUMED".equals(status) || "ABSENT".equals(status))
                || ("CONSUMED".equals(status) && consumedSequence == 0))
            return new byte[0];
        return ("future-g1/read-only-receipt-status/v1\n" + claimId + "\n"
                + exactId + "\n" + sourceRevision + "\n"
                + java.util.Base64.getEncoder().encodeToString(originalOwnerSignature)
                + "\n" + queryNonce + "\n" + consumedSequence + "\n"
                + status + "\n").getBytes(StandardCharsets.UTF_8);
    }

    public static ReceiptStatusOutcome reconcileIndeterminateReceiptReadOnly(
            String claimId, String exactId, long sourceRevision,
            byte[] originalOwnerSignature, String freshQueryNonce,
            byte[] independentlyEnrolledAuthorityKey,
            ReadOnlyReceiptStatusAuthority authority) {
        if (authority == null || independentlyEnrolledAuthorityKey == null
                || independentlyEnrolledAuthorityKey.length == 0)
            return ReceiptStatusOutcome.NO_AUTHORITY;
        if (signedReceiptStatusPayload(claimId, exactId, sourceRevision,
                originalOwnerSignature, freshQueryNonce, 0, "ABSENT").length == 0)
            return ReceiptStatusOutcome.UNVERIFIABLE;
        final java.security.PublicKey expected;
        try {
            java.security.PublicKey claimed = authority.originalPinnedIdentity();
            expected = java.security.KeyFactory.getInstance("Ed25519")
                    .generatePublic(new java.security.spec.X509EncodedKeySpec(
                            independentlyEnrolledAuthorityKey.clone()));
            if (claimed == null || claimed.getEncoded() == null
                    || !java.util.Arrays.equals(expected.getEncoded(), claimed.getEncoded()))
                return ReceiptStatusOutcome.NO_AUTHORITY;
        } catch (GeneralSecurityException | RuntimeException error) {
            return ReceiptStatusOutcome.NO_AUTHORITY;
        }
        final SignedReceiptStatus response;
        try {
            response = authority.queryExistingClaim(claimId, freshQueryNonce);
        } catch (RuntimeException error) {
            return ReceiptStatusOutcome.REMAIN_INDETERMINATE;
        }
        if (response == null) return ReceiptStatusOutcome.REMAIN_INDETERMINATE;
        if (!claimId.equals(response.claimId)) return ReceiptStatusOutcome.UNVERIFIABLE;
        byte[] payload = signedReceiptStatusPayload(claimId, exactId,
                sourceRevision, originalOwnerSignature, freshQueryNonce,
                response.sequence, response.status);
        byte[] signature = response.signature();
        if (payload.length == 0 || signature == null || signature.length != 64)
            return ReceiptStatusOutcome.UNVERIFIABLE;
        try {
            java.security.Signature verify = java.security.Signature.getInstance("Ed25519");
            verify.initVerify(expected);
            verify.update(payload);
            if (!verify.verify(signature)) return ReceiptStatusOutcome.UNVERIFIABLE;
            return "CONSUMED".equals(response.status)
                    ? ReceiptStatusOutcome.CONSUMED_VERIFIED
                    : ReceiptStatusOutcome.ABSENT_UNSAFE_TO_RETRY;
        } catch (GeneralSecurityException | RuntimeException error) {
            return ReceiptStatusOutcome.UNVERIFIABLE;
        }
    }

    /**
     * FUTURE-ONLY, READ-ONLY external high-water receipt reconciliation.
     * The independent authority must be enrolled BEFORE the original claim,
     * with a separately provisioned Ed25519 identity and a NONROLLBACKABLE,
     * atomically consumed claim-specific record. This code does NOT create or
     * prove such custody. An ordinary filesystem ledger, caller-supplied pin,
     * or resettable external authority is UNSAFE even with correct signatures.
     *
     * A positive result is CONDITIONAL on the externally provisioned custodian
     * actually preserving claim state across rollback, not a production claim.
     * The local minimum is only an optional previously trusted floor and is
     * NOT a replacement for external nonrollbackable custody. This method
     * never starts, retries or commits any operation.
     */
    public static ReceiptStatusOutcome reconcileReceiptWithIndependentHighWater(
            String claimId, String exactId, long sourceRevision,
            byte[] originalOwnerSignature, String freshQueryNonce,
            byte[] previouslyEnrolledOwnerKey, byte[] independentlyEnrolledRootKey,
            long previouslyTrustedMinimumSequence,
            ReadOnlyReceiptStatusAuthority independentlyProvisionedRoot) {
        if (independentlyProvisionedRoot == null || previouslyEnrolledOwnerKey == null
                || independentlyEnrolledRootKey == null)
            return ReceiptStatusOutcome.NO_AUTHORITY;
        if (previouslyTrustedMinimumSequence < 0
                || signedReceiptStatusPayload(claimId, exactId, sourceRevision,
                        originalOwnerSignature, freshQueryNonce, 0, "ABSENT").length == 0)
            return ReceiptStatusOutcome.UNVERIFIABLE;
        final java.security.PublicKey root;
        try {
            java.security.KeyFactory factory = java.security.KeyFactory.getInstance("Ed25519");
            java.security.PublicKey owner = factory.generatePublic(
                    new java.security.spec.X509EncodedKeySpec(previouslyEnrolledOwnerKey.clone()));
            root = factory.generatePublic(
                    new java.security.spec.X509EncodedKeySpec(independentlyEnrolledRootKey.clone()));
            java.security.PublicKey claimed = independentlyProvisionedRoot.originalPinnedIdentity();
            if (java.util.Arrays.equals(owner.getEncoded(), root.getEncoded())
                    || claimed == null || claimed.getEncoded() == null
                    || !java.util.Arrays.equals(root.getEncoded(), claimed.getEncoded()))
                return ReceiptStatusOutcome.NO_AUTHORITY;
        } catch (GeneralSecurityException | RuntimeException error) {
            return ReceiptStatusOutcome.NO_AUTHORITY;
        }
        final SignedReceiptStatus reply;
        try {
            reply = independentlyProvisionedRoot.queryExistingClaim(claimId, freshQueryNonce);
        } catch (RuntimeException error) {
            return ReceiptStatusOutcome.REMAIN_INDETERMINATE;
        }
        if (reply == null) return ReceiptStatusOutcome.REMAIN_INDETERMINATE;
        if (!claimId.equals(reply.claimId)
                || reply.sequence < previouslyTrustedMinimumSequence)
            return ReceiptStatusOutcome.UNVERIFIABLE;
        byte[] signed = signedReceiptStatusPayload(claimId, exactId, sourceRevision,
                originalOwnerSignature, freshQueryNonce, reply.sequence, reply.status);
        byte[] signature = reply.signature();
        if (signed.length == 0 || signature == null || signature.length != 64)
            return ReceiptStatusOutcome.UNVERIFIABLE;
        try {
            java.security.Signature verifier = java.security.Signature.getInstance("Ed25519");
            verifier.initVerify(root);
            verifier.update(signed);
            if (!verifier.verify(signature)) return ReceiptStatusOutcome.UNVERIFIABLE;
            return "CONSUMED".equals(reply.status)
                    ? ReceiptStatusOutcome.CONSUMED_VERIFIED
                    : ReceiptStatusOutcome.ABSENT_UNSAFE_TO_RETRY;
        } catch (GeneralSecurityException | RuntimeException error) {
            return ReceiptStatusOutcome.UNVERIFIABLE;
        }
    }

    /**
     * FUTURE ONLY / RELEASE-INDEPENDENT: a separate operator signs the exact
     * consumed claim, not just an aggregate high-water sequence. The caller
     * must have independently enrolled all THREE original Ed25519 identities
     * before the transaction, and the operator must keep the per-claim state
     * outside any rollbackable app/backup. Neither property is established by
     * this pure-Java verifier or its deliberately rollbackable JVM fixture.
     * A signed ABSENT is NEVER permission to retry an indeterminate claim.
     */
    public static final class SignedOperatorClaimCommitment {
        public final String claimId, exactId, queryNonce, status;
        public final long sourceRevision, epoch, consumedSequence;
        private final byte[] signature;

        public SignedOperatorClaimCommitment(String claimId, String exactId,
                long sourceRevision, String queryNonce, long epoch,
                long consumedSequence, String status, byte[] signature) {
            this.claimId = claimId;
            this.exactId = exactId;
            this.sourceRevision = sourceRevision;
            this.queryNonce = queryNonce;
            this.epoch = epoch;
            this.consumedSequence = consumedSequence;
            this.status = status;
            this.signature = signature == null ? null : signature.clone();
        }
        public byte[] signature() {
            return signature == null ? null : signature.clone();
        }
    }

    public static byte[] operatorClaimCommitmentPayload(String claimId,
            String exactId, long sourceRevision, byte[] originalOwnerSignature,
            byte[] originalOwnerPin, byte[] originalRootPin, String queryNonce,
            long operatorEpoch, long claimSequence, String status) {
        if (signedReceiptStatusPayload(claimId, exactId, sourceRevision,
                originalOwnerSignature, queryNonce, claimSequence, status).length == 0
                || originalOwnerPin == null || originalOwnerPin.length == 0
                || originalRootPin == null || originalRootPin.length == 0
                || operatorEpoch <= 0)
            return new byte[0];
        return ("future-g1/operator-claim-commitment/v1\n" + claimId + "\n"
                + exactId + "\n" + sourceRevision + "\n"
                + java.util.Base64.getEncoder().encodeToString(originalOwnerSignature)
                + "\n" + java.util.Base64.getEncoder().encodeToString(originalOwnerPin)
                + "\n" + java.util.Base64.getEncoder().encodeToString(originalRootPin)
                + "\n" + queryNonce + "\n" + operatorEpoch + "\n"
                + claimSequence + "\n" + status + "\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Pure verifier only. Independently trusted epoch and claim-specific
     * minimum MUST NOT be reconstructed from the queried operator's reply.
     * This API intentionally has no consumption, acknowledgement or retry.
     */
    public static ReceiptStatusOutcome verifyOperatorClaimCommitment(
            String claimId, String exactId, long sourceRevision,
            byte[] originalOwnerSignature, String freshQueryNonce,
            byte[] previouslyPinnedOwner, byte[] previouslyPinnedRoot,
            byte[] previouslyPinnedOperator, long trustedEpoch,
            long trustedClaimMinimumSequence,
            SignedOperatorClaimCommitment commitment,
            SignedReceiptStatus independentRootReceipt) {
        if (previouslyPinnedOwner == null || previouslyPinnedRoot == null
                || previouslyPinnedOperator == null || commitment == null
                || independentRootReceipt == null)
            return ReceiptStatusOutcome.NO_AUTHORITY;
        if (trustedEpoch <= 0 || trustedClaimMinimumSequence < 0
                || !java.util.Objects.equals(claimId, commitment.claimId)
                || !java.util.Objects.equals(exactId, commitment.exactId)
                || sourceRevision != commitment.sourceRevision
                || !java.util.Objects.equals(freshQueryNonce, commitment.queryNonce)
                || commitment.epoch < trustedEpoch
                || commitment.consumedSequence < trustedClaimMinimumSequence
                || !java.util.Objects.equals(claimId, independentRootReceipt.claimId)
                || independentRootReceipt.sequence != commitment.consumedSequence
                || !java.util.Objects.equals(commitment.status, independentRootReceipt.status))
            return ReceiptStatusOutcome.UNVERIFIABLE;
        byte[] rootBytes = signedReceiptStatusPayload(claimId, exactId,
                sourceRevision, originalOwnerSignature, freshQueryNonce,
                independentRootReceipt.sequence, independentRootReceipt.status);
        byte[] rootSignature = independentRootReceipt.signature();
        if (rootBytes.length == 0 || rootSignature == null || rootSignature.length != 64)
            return ReceiptStatusOutcome.UNVERIFIABLE;
        byte[] bytes = operatorClaimCommitmentPayload(commitment.claimId,
                commitment.exactId, commitment.sourceRevision,
                originalOwnerSignature, previouslyPinnedOwner, previouslyPinnedRoot,
                commitment.queryNonce, commitment.epoch,
                commitment.consumedSequence, commitment.status);
        byte[] signature = commitment.signature();
        if (bytes.length == 0 || signature == null || signature.length != 64)
            return ReceiptStatusOutcome.UNVERIFIABLE;
        try {
            java.security.KeyFactory keys = java.security.KeyFactory.getInstance("Ed25519");
            java.security.PublicKey owner = keys.generatePublic(
                    new java.security.spec.X509EncodedKeySpec(previouslyPinnedOwner.clone()));
            java.security.PublicKey root = keys.generatePublic(
                    new java.security.spec.X509EncodedKeySpec(previouslyPinnedRoot.clone()));
            java.security.PublicKey operator = keys.generatePublic(
                    new java.security.spec.X509EncodedKeySpec(previouslyPinnedOperator.clone()));
            byte[] o = owner.getEncoded(), r = root.getEncoded(), p = operator.getEncoded();
            if (!java.util.Arrays.equals(o, previouslyPinnedOwner)
                    || !java.util.Arrays.equals(r, previouslyPinnedRoot)
                    || !java.util.Arrays.equals(p, previouslyPinnedOperator)
                    || java.util.Arrays.equals(o, r)
                    || java.util.Arrays.equals(o, p)
                    || java.util.Arrays.equals(r, p))
                return ReceiptStatusOutcome.NO_AUTHORITY;
            // The independently pinned source owner must itself authenticate
            // the ORIGINAL claim, exact memory, revision and revocation.
            // Both other signers could otherwise endorse forged owner bytes.
            byte[] ownerBytes = originalOwnerClaimPayload(claimId, exactId,
                    sourceRevision);
            if (ownerBytes.length == 0 || originalOwnerSignature == null
                    || originalOwnerSignature.length != 64)
                return ReceiptStatusOutcome.UNVERIFIABLE;
            java.security.Signature verify = java.security.Signature.getInstance("Ed25519");
            verify.initVerify(owner);
            verify.update(ownerBytes);
            if (!verify.verify(originalOwnerSignature))
                return ReceiptStatusOutcome.UNVERIFIABLE;
            verify.initVerify(root);
            verify.update(rootBytes);
            if (!verify.verify(rootSignature)) return ReceiptStatusOutcome.UNVERIFIABLE;
            verify.initVerify(operator);
            verify.update(bytes);
            if (!verify.verify(signature)) return ReceiptStatusOutcome.UNVERIFIABLE;
            return "CONSUMED".equals(commitment.status)
                    ? ReceiptStatusOutcome.CONSUMED_VERIFIED
                    : ReceiptStatusOutcome.ABSENT_UNSAFE_TO_RETRY;
        } catch (GeneralSecurityException | RuntimeException error) {
            return ReceiptStatusOutcome.UNVERIFIABLE;
        }
    }

    /**
     * FUTURE-ONLY proof of a PRE-EXISTING consumed claim from a fourth,
     * separately enrolled checkpoint signer. The checkpoint MUST already be
     * in independently provisioned, nonrollbackable custody. This method
     * cannot create that custody and never writes, consumes or retries a claim.
     * It deliberately has NO signed-ABSENT success path: missing/lost custody
     * is NO_AUTHORITY, and disagreeing reset root/operator state is rejected.
     */
    public static final class SignedExternalConsumedCheckpoint {
        public final String claimId, exactId;
        public final long sourceRevision, anchorEpoch, consumedSequence;
        private final byte[] signature;

        public SignedExternalConsumedCheckpoint(String claimId, String exactId,
                long sourceRevision, long anchorEpoch, long consumedSequence,
                byte[] signature) {
            this.claimId = claimId;
            this.exactId = exactId;
            this.sourceRevision = sourceRevision;
            this.anchorEpoch = anchorEpoch;
            this.consumedSequence = consumedSequence;
            this.signature = signature == null ? null : signature.clone();
        }
        public byte[] signature() {
            return signature == null ? null : signature.clone();
        }
    }

    public static byte[] externalConsumedCheckpointPayload(String claimId,
            String exactId, long sourceRevision, byte[] originalOwnerSignature,
            byte[] previouslyPinnedOwner, byte[] previouslyPinnedRoot,
            byte[] previouslyPinnedOperator, long anchorEpoch, long consumedSequence) {
        if (originalOwnerClaimPayload(claimId, exactId, sourceRevision).length == 0
                || originalOwnerSignature == null || originalOwnerSignature.length != 64
                || previouslyPinnedOwner == null || previouslyPinnedOwner.length == 0
                || previouslyPinnedRoot == null || previouslyPinnedRoot.length == 0
                || previouslyPinnedOperator == null || previouslyPinnedOperator.length == 0
                || anchorEpoch <= 0 || consumedSequence <= 0)
            return new byte[0];
        java.util.Base64.Encoder base64 = java.util.Base64.getEncoder();
        return ("future-g1/external-consumed-checkpoint/v1\n" + claimId + "\n"
                + exactId + "\n" + sourceRevision + "\n"
                + base64.encodeToString(originalOwnerSignature) + "\n"
                + base64.encodeToString(previouslyPinnedOwner) + "\n"
                + base64.encodeToString(previouslyPinnedRoot) + "\n"
                + base64.encodeToString(previouslyPinnedOperator) + "\n"
                + anchorEpoch + "\n" + consumedSequence + "\nCONSUMED\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Verify a claim-specific positive checkpoint against the CURRENT signed
     * owner/root/operator statements. The anchor key and minimum epoch must
     * be independently pinned BEFORE the claim, not learned from replies.
     * Even a PASS proves signatures/consistency only, NOT external durability.
     */
    public static ReceiptStatusOutcome verifyWithExternalConsumedCheckpoint(
            String claimId, String exactId, long sourceRevision,
            byte[] originalOwnerSignature, String freshQueryNonce,
            byte[] previouslyPinnedOwner, byte[] previouslyPinnedRoot,
            byte[] previouslyPinnedOperator, byte[] previouslyPinnedExternalAnchor,
            long trustedOperatorEpoch, long trustedAnchorEpoch,
            long trustedClaimMinimumSequence,
            SignedExternalConsumedCheckpoint checkpoint,
            SignedOperatorClaimCommitment currentOperator,
            SignedReceiptStatus currentRoot) {
        if (checkpoint == null || previouslyPinnedExternalAnchor == null
                || previouslyPinnedOwner == null || previouslyPinnedRoot == null
                || previouslyPinnedOperator == null)
            return ReceiptStatusOutcome.NO_AUTHORITY;
        if (trustedAnchorEpoch <= 0 || trustedClaimMinimumSequence < 0
                || checkpoint.anchorEpoch < trustedAnchorEpoch
                || checkpoint.consumedSequence < trustedClaimMinimumSequence
                || checkpoint.consumedSequence <= 0
                || !java.util.Objects.equals(checkpoint.claimId, claimId)
                || !java.util.Objects.equals(checkpoint.exactId, exactId)
                || checkpoint.sourceRevision != sourceRevision
                || currentOperator == null || currentRoot == null
                || !"CONSUMED".equals(currentOperator.status)
                || !"CONSUMED".equals(currentRoot.status)
                || currentOperator.consumedSequence != checkpoint.consumedSequence)
            return ReceiptStatusOutcome.UNVERIFIABLE;
        byte[] payload = externalConsumedCheckpointPayload(claimId, exactId,
                sourceRevision, originalOwnerSignature, previouslyPinnedOwner,
                previouslyPinnedRoot, previouslyPinnedOperator,
                checkpoint.anchorEpoch, checkpoint.consumedSequence);
        byte[] signature = checkpoint.signature();
        if (payload.length == 0 || signature == null || signature.length != 64)
            return ReceiptStatusOutcome.UNVERIFIABLE;
        try {
            java.security.KeyFactory keys = java.security.KeyFactory.getInstance("Ed25519");
            byte[][] pins = {previouslyPinnedOwner, previouslyPinnedRoot,
                    previouslyPinnedOperator, previouslyPinnedExternalAnchor};
            java.security.PublicKey[] verified = new java.security.PublicKey[pins.length];
            for (int i = 0; i < pins.length; i++) {
                verified[i] = keys.generatePublic(
                        new java.security.spec.X509EncodedKeySpec(pins[i].clone()));
                if (!java.util.Arrays.equals(pins[i], verified[i].getEncoded()))
                    return ReceiptStatusOutcome.NO_AUTHORITY;
                for (int j = 0; j < i; j++) {
                    if (java.util.Arrays.equals(pins[i], pins[j]))
                        return ReceiptStatusOutcome.NO_AUTHORITY;
                }
            }
            java.security.Signature verify = java.security.Signature.getInstance("Ed25519");
            verify.initVerify(verified[3]);
            verify.update(payload);
            if (!verify.verify(signature)) return ReceiptStatusOutcome.UNVERIFIABLE;
        } catch (java.security.SignatureException badSignature) {
            return ReceiptStatusOutcome.UNVERIFIABLE;
        } catch (GeneralSecurityException | RuntimeException error) {
            return ReceiptStatusOutcome.NO_AUTHORITY;
        }
        ReceiptStatusOutcome consensus = verifyOperatorClaimCommitment(claimId,
                exactId, sourceRevision, originalOwnerSignature, freshQueryNonce,
                previouslyPinnedOwner, previouslyPinnedRoot,
                previouslyPinnedOperator, trustedOperatorEpoch,
                Math.max(trustedClaimMinimumSequence, checkpoint.consumedSequence),
                currentOperator, currentRoot);
        // A rollbackable signer's freshly signed ABSENT must NEVER override a
        // positive externally protected checkpoint or authorize a retry.
        return consensus == ReceiptStatusOutcome.CONSUMED_VERIFIED
                ? consensus : (consensus == ReceiptStatusOutcome.NO_AUTHORITY
                        ? consensus : ReceiptStatusOutcome.UNVERIFIABLE);
    }

    /**
     * FUTURE-ONLY atomic one-shot custodian acknowledgement contract.
     * This is a verification-only boundary: it does NOT perform consumption,
     * authorize a retry, create external storage, or prove nonrollbackability.
     * The consuming service must atomically and durably store an immutable
     * claim record BEFORE signing an acknowledgement; an interrupted request
     * without proof stays STARTED_INDETERMINATE at its owner.
     */
    public static final class SignedAtomicCustodianAck {
        public final String claimId, exactId, requestNonce, state;
        public final long sourceRevision, anchorEpoch, consumedSequence;
        private final byte[] signature;

        public SignedAtomicCustodianAck(String claimId, String exactId,
                long sourceRevision, String requestNonce, long anchorEpoch,
                long consumedSequence, String state, byte[] signature) {
            this.claimId = claimId;
            this.exactId = exactId;
            this.sourceRevision = sourceRevision;
            this.requestNonce = requestNonce;
            this.anchorEpoch = anchorEpoch;
            this.consumedSequence = consumedSequence;
            this.state = state;
            this.signature = signature == null ? null : signature.clone();
        }
        public byte[] signature() {
            return signature == null ? null : signature.clone();
        }
    }

    public static byte[] atomicCustodianAckPayload(String claimId,
            String exactId, long sourceRevision, byte[] originalOwnerSignature,
            String requestNonce, byte[] enrolledOwnerPin, byte[] enrolledRootPin,
            byte[] enrolledOperatorPin, long anchorEpoch, long consumedSequence,
            String state) {
        return atomicCustodianProofPayload(claimId, exactId, sourceRevision,
                originalOwnerSignature, requestNonce, enrolledOwnerPin,
                enrolledRootPin, enrolledOperatorPin, anchorEpoch,
                consumedSequence, state, "future-g1/external-atomic-consume-ack/v1");
    }

    /**
     * Strictly read-only query attestation. This signing domain can never be
     * supplied by a consume-path acknowledgement, even when its status is
     * ALREADY_CONSUMED and its claim/nonce/sequence otherwise match.
     * A query is evidence ONLY; it never authorizes a write or retry.
     */
    public static byte[] atomicCustodianReadOnlyQueryPayload(String claimId,
            String exactId, long sourceRevision, byte[] originalOwnerSignature,
            String requestNonce, byte[] enrolledOwnerPin, byte[] enrolledRootPin,
            byte[] enrolledOperatorPin, long anchorEpoch, long consumedSequence,
            String state) {
        if (!"ALREADY_CONSUMED".equals(state)) return new byte[0];
        return atomicCustodianProofPayload(claimId, exactId, sourceRevision,
                originalOwnerSignature, requestNonce, enrolledOwnerPin,
                enrolledRootPin, enrolledOperatorPin, anchorEpoch,
                consumedSequence, state,
                "future-g1/external-atomic-custodian-readonly-query/v1");
    }

    private static byte[] atomicCustodianProofPayload(String claimId,
            String exactId, long sourceRevision, byte[] originalOwnerSignature,
            String requestNonce, byte[] enrolledOwnerPin, byte[] enrolledRootPin,
            byte[] enrolledOperatorPin, long anchorEpoch, long consumedSequence,
            String state, String signingDomain) {
        if (signedReceiptStatusPayload(claimId, exactId, sourceRevision,
                originalOwnerSignature, requestNonce, consumedSequence, "CONSUMED").length == 0
                || enrolledOwnerPin == null || enrolledOwnerPin.length == 0
                || enrolledRootPin == null || enrolledRootPin.length == 0
                || enrolledOperatorPin == null || enrolledOperatorPin.length == 0
                || anchorEpoch <= 0
                || !("COMMITTED".equals(state) || "ALREADY_CONSUMED".equals(state)))
            return new byte[0];
        java.util.Base64.Encoder b64 = java.util.Base64.getEncoder();
        return (signingDomain + "\n" + claimId + "\n"
                + exactId + "\n" + sourceRevision + "\n"
                + b64.encodeToString(originalOwnerSignature) + "\n" + requestNonce + "\n"
                + b64.encodeToString(enrolledOwnerPin) + "\n"
                + b64.encodeToString(enrolledRootPin) + "\n"
                + b64.encodeToString(enrolledOperatorPin) + "\n"
                + anchorEpoch + "\n" + consumedSequence + "\n" + state + "\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Positive evidence only. A valid result means that an independently
     * enrolled key signed this exact claim/nonce; it DOES NOT establish that
     * the remote record cannot be restored or erased. The caller MUST NOT
     * convert a missing acknowledgement into a new consume or retry attempt.
     */
    public static ReceiptStatusOutcome verifyAtomicCustodianAck(
            String claimId, String exactId, long sourceRevision,
            byte[] originalOwnerSignature, String requestNonce,
            byte[] enrolledOwnerPin, byte[] enrolledRootPin,
            byte[] enrolledOperatorPin, byte[] enrolledCustodianPin,
            long trustedAnchorEpoch, long trustedClaimMinimumSequence,
            SignedAtomicCustodianAck ack) {
        return verifyAtomicCustodianProof(claimId, exactId, sourceRevision,
                originalOwnerSignature, requestNonce, enrolledOwnerPin,
                enrolledRootPin, enrolledOperatorPin, enrolledCustodianPin,
                trustedAnchorEpoch, trustedClaimMinimumSequence, ack, false);
    }

    public static ReceiptStatusOutcome verifyAtomicCustodianReadOnlyQuery(
            String claimId, String exactId, long sourceRevision,
            byte[] originalOwnerSignature, String requestNonce,
            byte[] enrolledOwnerPin, byte[] enrolledRootPin,
            byte[] enrolledOperatorPin, byte[] enrolledCustodianPin,
            long trustedAnchorEpoch, long trustedClaimMinimumSequence,
            SignedAtomicCustodianAck ack) {
        return verifyAtomicCustodianProof(claimId, exactId, sourceRevision,
                originalOwnerSignature, requestNonce, enrolledOwnerPin,
                enrolledRootPin, enrolledOperatorPin, enrolledCustodianPin,
                trustedAnchorEpoch, trustedClaimMinimumSequence, ack, true);
    }

    private static ReceiptStatusOutcome verifyAtomicCustodianProof(
            String claimId, String exactId, long sourceRevision,
            byte[] originalOwnerSignature, String requestNonce,
            byte[] enrolledOwnerPin, byte[] enrolledRootPin,
            byte[] enrolledOperatorPin, byte[] enrolledCustodianPin,
            long trustedAnchorEpoch, long trustedClaimMinimumSequence,
            SignedAtomicCustodianAck ack, boolean readOnlyQuery) {
        if (ack == null) return ReceiptStatusOutcome.REMAIN_INDETERMINATE;
        if (readOnlyQuery && !"ALREADY_CONSUMED".equals(ack.state))
            return ReceiptStatusOutcome.UNVERIFIABLE;
        if (enrolledOwnerPin == null || enrolledRootPin == null
                || enrolledOperatorPin == null || enrolledCustodianPin == null)
            return ReceiptStatusOutcome.NO_AUTHORITY;
        if (!java.util.Objects.equals(ack.claimId, claimId)
                || !java.util.Objects.equals(ack.exactId, exactId)
                || ack.sourceRevision != sourceRevision
                || !java.util.Objects.equals(ack.requestNonce, requestNonce)
                || trustedAnchorEpoch <= 0 || trustedClaimMinimumSequence < 0
                || ack.anchorEpoch < trustedAnchorEpoch
                || ack.consumedSequence <= 0
                || ack.consumedSequence < trustedClaimMinimumSequence)
            return ReceiptStatusOutcome.UNVERIFIABLE;
        byte[] message = (readOnlyQuery
                ? atomicCustodianReadOnlyQueryPayload(claimId, exactId,
                        sourceRevision, originalOwnerSignature, requestNonce,
                        enrolledOwnerPin, enrolledRootPin, enrolledOperatorPin,
                        ack.anchorEpoch, ack.consumedSequence, ack.state)
                : atomicCustodianAckPayload(claimId, exactId,
                        sourceRevision, originalOwnerSignature, requestNonce,
                        enrolledOwnerPin, enrolledRootPin, enrolledOperatorPin,
                        ack.anchorEpoch, ack.consumedSequence, ack.state));
        byte[] signature = ack.signature();
        if (message.length == 0 || signature == null || signature.length != 64)
            return ReceiptStatusOutcome.UNVERIFIABLE;
        try {
            java.security.KeyFactory factory = java.security.KeyFactory.getInstance("Ed25519");
            byte[][] pins = {enrolledOwnerPin, enrolledRootPin,
                    enrolledOperatorPin, enrolledCustodianPin};
            java.security.PublicKey[] keys = new java.security.PublicKey[pins.length];
            for (int i = 0; i < pins.length; i++) {
                keys[i] = factory.generatePublic(new java.security.spec.X509EncodedKeySpec(pins[i].clone()));
                if (!java.util.Arrays.equals(keys[i].getEncoded(), pins[i]))
                    return ReceiptStatusOutcome.NO_AUTHORITY;
                for (int j = 0; j < i; j++) {
                    if (java.util.Arrays.equals(pins[i], pins[j]))
                        return ReceiptStatusOutcome.NO_AUTHORITY;
                }
            }
            java.security.Signature verifier = java.security.Signature.getInstance("Ed25519");
            verifier.initVerify(keys[0]);
            verifier.update(originalOwnerClaimPayload(claimId, exactId, sourceRevision));
            if (!verifier.verify(originalOwnerSignature)) return ReceiptStatusOutcome.UNVERIFIABLE;
            verifier.initVerify(keys[3]);
            verifier.update(message);
            return verifier.verify(signature)
                    ? ReceiptStatusOutcome.CONSUMED_VERIFIED
                    : ReceiptStatusOutcome.UNVERIFIABLE;
        } catch (GeneralSecurityException | RuntimeException error) {
            return ReceiptStatusOutcome.UNVERIFIABLE;
        }
    }

    /**
     * FUTURE-ONLY independent claim-floor readback. A pre-enrolled fifth key
     * must be a separate authority, not chosen from the service reply. The
     * previously trusted epoch/sequence are inputs from OUTSIDE the custodian
     * and outside the signed response. Filesystem test fixtures below are
     * rollbackable, so conditional verification is NOT an antirollback proof.
     * No ABSENT response is ever interpreted as permission to consume/retry.
     */
    public static final class SignedExternalClaimFloor {
        public final String claimId, exactId, requestNonce, status;
        public final long sourceRevision, epoch, consumedSequence;
        private final byte[] custodianPin, signature;
        public SignedExternalClaimFloor(String claimId, String exactId,
                long sourceRevision, String requestNonce, long epoch,
                long consumedSequence, String status, byte[] custodianPin,
                byte[] signature) {
            this.claimId = claimId;
            this.exactId = exactId;
            this.sourceRevision = sourceRevision;
            this.requestNonce = requestNonce;
            this.epoch = epoch;
            this.consumedSequence = consumedSequence;
            this.status = status;
            this.custodianPin = custodianPin == null ? null : custodianPin.clone();
            this.signature = signature == null ? null : signature.clone();
        }
        public byte[] custodianPin() {
            return custodianPin == null ? null : custodianPin.clone();
        }
        public byte[] signature() {
            return signature == null ? null : signature.clone();
        }
    }

    public static byte[] externalClaimFloorPayload(String claimId,
            String exactId, long sourceRevision, byte[] ownerSignature,
            String freshQueryNonce, byte[] enrolledOwnerPin,
            byte[] enrolledRootPin, byte[] enrolledOperatorPin,
            byte[] enrolledCustodianPin, long epoch, long consumedSequence,
            String status) {
        if (signedReceiptStatusPayload(claimId, exactId, sourceRevision,
                ownerSignature, freshQueryNonce, consumedSequence,
                "CONSUMED".equals(status) ? "CONSUMED" : "ABSENT").length == 0
                || enrolledOwnerPin == null || enrolledOwnerPin.length == 0
                || enrolledRootPin == null || enrolledRootPin.length == 0
                || enrolledOperatorPin == null || enrolledOperatorPin.length == 0
                || enrolledCustodianPin == null || enrolledCustodianPin.length == 0
                || epoch <= 0 || consumedSequence < 0
                || !("CONSUMED".equals(status) && consumedSequence > 0
                    || "ABSENT".equals(status) && consumedSequence == 0))
            return new byte[0];
        java.util.Base64.Encoder b64 = java.util.Base64.getEncoder();
        return ("future-g1/independent-external-claim-floor/v1\n" + claimId + "\n"
                + exactId + "\n" + sourceRevision + "\n"
                + b64.encodeToString(ownerSignature) + "\n" + freshQueryNonce + "\n"
                + b64.encodeToString(enrolledOwnerPin) + "\n"
                + b64.encodeToString(enrolledRootPin) + "\n"
                + b64.encodeToString(enrolledOperatorPin) + "\n"
                + b64.encodeToString(enrolledCustodianPin) + "\n"
                + epoch + "\n" + consumedSequence + "\n" + status + "\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Authenticates a fresh fifth-party claim-specific floor BEFORE accepting
     * any custodian acknowledgement. A valid result is CONDITIONAL on trusted,
     * separately provisioned nonrollbackable storage that this code does not
     * implement. A signer with its original key can sign a rolled-back floor.
     * A missing/invalid floor never falls back to a custodian-only positive.
     */
    public static ReceiptStatusOutcome verifyAtomicAckWithIndependentClaimFloor(
            String claimId, String exactId, long sourceRevision,
            byte[] originalOwnerSignature, String requestNonce,
            byte[] pinnedOwner, byte[] pinnedRoot, byte[] pinnedOperator,
            byte[] pinnedCustodian, byte[] pinnedIndependentFloor,
            long previouslyTrustedFloorEpoch, long previouslyTrustedClaimSequence,
            SignedExternalClaimFloor floor, SignedAtomicCustodianAck ack) {
        if (floor == null || pinnedIndependentFloor == null || pinnedOwner == null
                || pinnedRoot == null || pinnedOperator == null
                || pinnedCustodian == null)
            return ReceiptStatusOutcome.NO_AUTHORITY;
        if (!java.util.Objects.equals(claimId, floor.claimId)
                || !java.util.Objects.equals(exactId, floor.exactId)
                || sourceRevision != floor.sourceRevision
                || !java.util.Objects.equals(requestNonce, floor.requestNonce)
                || previouslyTrustedFloorEpoch <= 0
                || previouslyTrustedClaimSequence < 0)
            return ReceiptStatusOutcome.UNVERIFIABLE;
        byte[] floorPin = floor.custodianPin();
        byte[] payload = externalClaimFloorPayload(claimId, exactId,
                sourceRevision, originalOwnerSignature, requestNonce,
                pinnedOwner, pinnedRoot, pinnedOperator, floorPin,
                floor.epoch, floor.consumedSequence, floor.status);
        byte[] signature = floor.signature();
        if (payload.length == 0 || signature == null || signature.length != 64)
            return ReceiptStatusOutcome.UNVERIFIABLE;
        try {
            java.security.KeyFactory factory = java.security.KeyFactory.getInstance("Ed25519");
            byte[][] pins = {pinnedOwner, pinnedRoot, pinnedOperator,
                    pinnedCustodian, pinnedIndependentFloor};
            java.security.PublicKey[] keys = new java.security.PublicKey[pins.length];
            for (int i = 0; i < pins.length; i++) {
                keys[i] = factory.generatePublic(
                        new java.security.spec.X509EncodedKeySpec(pins[i].clone()));
                if (!java.util.Arrays.equals(keys[i].getEncoded(), pins[i]))
                    return ReceiptStatusOutcome.NO_AUTHORITY;
                for (int j = 0; j < i; j++) {
                    if (java.util.Arrays.equals(pins[i], pins[j]))
                        return ReceiptStatusOutcome.NO_AUTHORITY;
                }
            }
            if (!java.util.Arrays.equals(floorPin, pinnedCustodian))
                return ReceiptStatusOutcome.NO_AUTHORITY;
            java.security.Signature verifier = java.security.Signature.getInstance("Ed25519");
            verifier.initVerify(keys[0]);
            verifier.update(originalOwnerClaimPayload(claimId, exactId, sourceRevision));
            if (!verifier.verify(originalOwnerSignature))
                return ReceiptStatusOutcome.UNVERIFIABLE;
            verifier.initVerify(keys[4]);
            verifier.update(payload);
            if (!verifier.verify(signature)) return ReceiptStatusOutcome.UNVERIFIABLE;
        } catch (GeneralSecurityException | RuntimeException error) {
            return ReceiptStatusOutcome.UNVERIFIABLE;
        }
        // Only AFTER authenticating original fifth-party signature compare the
        // caller's independently held prior floor. Never learn a minimum from
        // a fresh response. In particular, ABSENT never grants a retry.
        if (floor.epoch < previouslyTrustedFloorEpoch
                || floor.consumedSequence < previouslyTrustedClaimSequence)
            return ReceiptStatusOutcome.UNVERIFIABLE;
        if (!"CONSUMED".equals(floor.status))
            return ReceiptStatusOutcome.NO_AUTHORITY;
        if (ack == null) return ReceiptStatusOutcome.REMAIN_INDETERMINATE;
        if (ack.anchorEpoch != floor.epoch
                || ack.consumedSequence != floor.consumedSequence
                || ("COMMITTED".equals(ack.state)
                    && previouslyTrustedClaimSequence > 0))
            return ReceiptStatusOutcome.UNVERIFIABLE;
        return verifyAtomicCustodianAck(claimId, exactId, sourceRevision,
                originalOwnerSignature, requestNonce, pinnedOwner, pinnedRoot,
                pinnedOperator, pinnedCustodian,
                Math.max(previouslyTrustedFloorEpoch, floor.epoch),
                Math.max(previouslyTrustedClaimSequence, floor.consumedSequence), ack);
    }

    /**
     * FUTURE-ONLY, READ-ONLY live claim-floor capability. The eventual G1
     * integrator must provision this from a separate administrative trust
     * domain, NOT from a file, mock, or service selected by the app/caller.
     * Its backing record must be nonrollbackable and must survive application,
     * provider and custodian snapshot restoration. This interface cannot
     * authenticate the provisioning or implement such storage by itself.
     *
     * Contract: the implementation MUST NOT reserve, consume, retry, alter a
     * watermark or write a file while servicing queryExisting. It must return
     * a separately signed fifth-party claim floor for the fresh nonce.
     */
    public interface IndependentClaimFloorReadOnlyAuthority {
        SignedExternalClaimFloor queryExisting(String claimId, String exactId,
                long sourceRevision, byte[] ownerSignature,
                String freshQueryNonce) throws Exception;
    }

    /**
     * Performs exactly one read-only independent floor query and then invokes
     * the existing five-pin Ed25519 verifier. The caller cannot supply the
     * floor result directly. This is a conditional CHECK, not proof that the
     * source is nonrollbackable, and never authorizes any first reservation.
     * Any missing, stale, forged or unavailable authority fails closed.
     * A crash/timeout is indeterminate and MUST NOT be followed by reserveOnce.
     */
    public static ReceiptStatusOutcome reconcileAtomicAckWithLiveClaimFloor(
            String claimId, String exactId, long sourceRevision,
            byte[] originalOwnerSignature, String freshQueryNonce,
            byte[] pinnedOwner, byte[] pinnedRoot, byte[] pinnedOperator,
            byte[] pinnedCustodian, byte[] pinnedIndependentFloor,
            long previouslyTrustedFloorEpoch, long previouslyTrustedClaimSequence,
            IndependentClaimFloorReadOnlyAuthority readOnlyFloor,
            SignedAtomicCustodianAck custodianAck) {
        if (readOnlyFloor == null || pinnedIndependentFloor == null
                || pinnedOwner == null || pinnedRoot == null
                || pinnedOperator == null || pinnedCustodian == null)
            return ReceiptStatusOutcome.NO_AUTHORITY;
        byte[] originalClaim = originalOwnerClaimPayload(claimId, exactId, sourceRevision);
        if (originalOwnerSignature == null || originalOwnerSignature.length != 64
                || originalClaim.length == 0 || freshQueryNonce == null
                || !freshQueryNonce.matches("[A-Za-z0-9_-]{16,128}")
                || previouslyTrustedFloorEpoch <= 0
                || previouslyTrustedClaimSequence < 0)
            return ReceiptStatusOutcome.UNVERIFIABLE;
        // Never dispatch an owner claim or signature to a remote callback until
        // the ORIGINAL consent and all FIVE separately enrolled identities
        // have been validated. The later response verifier cannot undo an
        // earlier privacy leak / unauthenticated external read-only query.
        // This preflight does NOT establish independent, nonrollbackable custody.
        try {
            java.security.KeyFactory factory = java.security.KeyFactory.getInstance("Ed25519");
            byte[][] pins = {pinnedOwner, pinnedRoot, pinnedOperator,
                    pinnedCustodian, pinnedIndependentFloor};
            java.security.PublicKey[] keys = new java.security.PublicKey[pins.length];
            for (int i = 0; i < pins.length; i++) {
                keys[i] = factory.generatePublic(
                        new java.security.spec.X509EncodedKeySpec(pins[i].clone()));
                if (!java.util.Arrays.equals(keys[i].getEncoded(), pins[i]))
                    return ReceiptStatusOutcome.NO_AUTHORITY;
                for (int j = 0; j < i; j++) {
                    if (java.util.Arrays.equals(pins[i], pins[j]))
                        return ReceiptStatusOutcome.NO_AUTHORITY;
                }
            }
            java.security.Signature verifier = java.security.Signature.getInstance("Ed25519");
            verifier.initVerify(keys[0]);
            verifier.update(originalClaim);
            if (!verifier.verify(originalOwnerSignature))
                return ReceiptStatusOutcome.UNVERIFIABLE;
        } catch (GeneralSecurityException | RuntimeException invalid) {
            return ReceiptStatusOutcome.NO_AUTHORITY;
        }
        final SignedExternalClaimFloor authoritativeResponse;
        try {
            authoritativeResponse = readOnlyFloor.queryExisting(claimId, exactId,
                    sourceRevision, originalOwnerSignature.clone(), freshQueryNonce);
        } catch (Exception unavailable) {
            return ReceiptStatusOutcome.REMAIN_INDETERMINATE;
        }
        if (authoritativeResponse == null) return ReceiptStatusOutcome.NO_AUTHORITY;
        return verifyAtomicAckWithIndependentClaimFloor(claimId, exactId,
                sourceRevision, originalOwnerSignature, freshQueryNonce,
                pinnedOwner, pinnedRoot, pinnedOperator, pinnedCustodian,
                pinnedIndependentFloor, previouslyTrustedFloorEpoch,
                previouslyTrustedClaimSequence, authoritativeResponse, custodianAck);
    }

    /**
     * FUTURE-ONLY signed enrollment: only the independently administered
     * bootstrap may bind these five pinned identities to one authority ID.
     * The root signature authenticates enrollment, NOT nonrollbackable custody.
     * The epoch minimum must be kept outside any rollbackable client snapshot.
     */
    public static byte[] oneShotEnrollmentPayload(String authorityId, long epoch,
            byte[] ownerPin, byte[] rootPin, byte[] operatorPin,
            byte[] custodianPin, byte[] providerPin) {
        if (authorityId == null || !authorityId.matches("[A-Za-z0-9_-]{8,96}")
                || epoch <= 0 || ownerPin == null || rootPin == null
                || operatorPin == null || custodianPin == null || providerPin == null)
            return new byte[0];
        for (byte[] pin : new byte[][] {ownerPin, rootPin, operatorPin,
                custodianPin, providerPin}) if (pin.length == 0) return new byte[0];
        java.util.Base64.Encoder b64 = java.util.Base64.getEncoder();
        return ("future-g1/independent-one-shot-enrollment/v1\n" + authorityId
                + "\n" + epoch + "\n" + b64.encodeToString(ownerPin) + "\n"
                + b64.encodeToString(rootPin) + "\n"
                + b64.encodeToString(operatorPin) + "\n"
                + b64.encodeToString(custodianPin) + "\n"
                + b64.encodeToString(providerPin) + "\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    public static final class SignedOneShotEnrollment {
        public final String authorityId;
        public final long epoch;
        private final byte[][] pins;
        private final byte[] signature;
        public SignedOneShotEnrollment(String authorityId, long epoch,
                byte[][] pins, byte[] signature) {
            this.authorityId = authorityId;
            this.epoch = epoch;
            this.pins = pins == null ? null : new byte[pins.length][];
            if (pins != null) for (int i = 0; i < pins.length; i++)
                this.pins[i] = pins[i] == null ? null : pins[i].clone();
            this.signature = signature == null ? null : signature.clone();
        }
        public byte[] signature() {
            return signature == null ? null : signature.clone();
        }
        public byte[][] pins() {
            if (pins == null) return null;
            byte[][] copy = new byte[pins.length][];
            for (int i = 0; i < pins.length; i++)
                copy[i] = pins[i] == null ? null : pins[i].clone();
            return copy;
        }
    }

    /**
     * FUTURE-ONLY bootstrap challenge signed by the separately provisioned
     * provider Ed25519 key (identity #5), not merely by the enrollment root.
     * This proves key possession for the fresh challenge, NOT independent
     * administration, persistence, anti-rollback hardware, or exclusivity.
     */
    public static byte[] oneShotProviderAttestationPayload(String authorityId,
            long epoch, byte[] enrollmentPayload, byte[] rootSignature,
            String freshChallenge) {
        if (authorityId == null || !authorityId.matches("[A-Za-z0-9_-]{8,96}")
                || epoch <= 0 || enrollmentPayload == null || enrollmentPayload.length == 0
                || rootSignature == null || rootSignature.length != 64
                || freshChallenge == null
                || !freshChallenge.matches("[A-Za-z0-9_-]{24,128}"))
            return new byte[0];
        try {
            java.security.MessageDigest sha = java.security.MessageDigest.getInstance("SHA-256");
            java.util.Base64.Encoder b64 = java.util.Base64.getEncoder();
            return ("future-g1/one-shot-provider-attest-enrollment/v1\n"
                    + authorityId + "\n" + epoch + "\n"
                    + b64.encodeToString(sha.digest(enrollmentPayload)) + "\n"
                    + b64.encodeToString(rootSignature) + "\n"
                    + freshChallenge + "\n").getBytes(StandardCharsets.UTF_8);
        } catch (GeneralSecurityException invalid) {
            return new byte[0];
        }
    }

    public static final class SignedOneShotProviderAttestation {
        public final String authorityId, freshChallenge;
        public final long epoch;
        private final byte[] providerPin, signature;
        public SignedOneShotProviderAttestation(String authorityId, long epoch,
                String freshChallenge, byte[] providerPin, byte[] signature) {
            this.authorityId = authorityId;
            this.epoch = epoch;
            this.freshChallenge = freshChallenge;
            this.providerPin = providerPin == null ? null : providerPin.clone();
            this.signature = signature == null ? null : signature.clone();
        }
        public byte[] providerPin() {
            return providerPin == null ? null : providerPin.clone();
        }
        public byte[] signature() {
            return signature == null ? null : signature.clone();
        }
    }

    /**
     * Scope every downstream v1 receipt nonce to the root-enrolled authority.
     * The nonce is signed in the existing reserve/query receipts. An unchanged
     * original caller nonce cannot be replayed between authority A and B even
     * if both are legitimately enrolled with the SAME five Ed25519 keys.
     * This is NOT a guarantee against a compromised provider signing a new B
     * receipt or a rollback of every independently trusted claim-floor state.
     */
    public static String oneShotBoundNonce(String authorityId, long epoch,
            String originalNonce) {
        if (authorityId == null || !authorityId.matches("[A-Za-z0-9_-]{8,96}")
                || epoch <= 0 || originalNonce == null
                || !originalNonce.matches("[A-Za-z0-9_-]{16,128}"))
            return "";
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(("future-g1/one-shot-authority-nonce/v1\n"
                            + authorityId + "\n" + epoch + "\n"
                            + originalNonce + "\n").getBytes(StandardCharsets.UTF_8));
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException invalid) {
            return "";
        }
    }

    /**
     * POST-v83 simulation contract only. A migration acknowledgement authenticates
     * an epoch and the complete expected ledger digest, but it CANNOT establish
     * that the provider truly preserved every spent claim or resists rollback.
     * The expected digest/count MUST come from separately retained trustworthy
     * custody; allowing the migrating provider to select them is unsafe.
     */
    public enum OneShotEpochMigrationOutcome {
        NO_AUTHORITY, UNVERIFIABLE, STARTED_INDETERMINATE, MIGRATION_ATTESTED
    }

    public static final class SignedOneShotEpochMigrationReceipt {
        public final String authorityId, freshNonce;
        public final long fromEpoch, toEpoch, claimCount;
        private final byte[] ledgerDigest, signature;
        public SignedOneShotEpochMigrationReceipt(String authorityId,
                long fromEpoch, long toEpoch, long claimCount,
                byte[] ledgerDigest, String freshNonce, byte[] signature) {
            this.authorityId = authorityId;
            this.fromEpoch = fromEpoch;
            this.toEpoch = toEpoch;
            this.claimCount = claimCount;
            this.ledgerDigest = ledgerDigest == null ? null : ledgerDigest.clone();
            this.freshNonce = freshNonce;
            this.signature = signature == null ? null : signature.clone();
        }
        public byte[] ledgerDigest() {
            return ledgerDigest == null ? null : ledgerDigest.clone();
        }
        public byte[] signature() {
            return signature == null ? null : signature.clone();
        }
    }

    public static byte[] oneShotEpochMigrationPayload(String authorityId,
            long fromEpoch, long toEpoch, long claimCount,
            byte[] expectedLedgerDigest, String freshNonce,
            byte[] targetRootEnrollmentSignature) {
        if (authorityId == null || !authorityId.matches("[A-Za-z0-9_-]{8,96}")
                || fromEpoch <= 0 || fromEpoch == Long.MAX_VALUE
                || toEpoch <= fromEpoch || toEpoch != fromEpoch + 1
                || claimCount < 0 || expectedLedgerDigest == null
                || expectedLedgerDigest.length != 32
                || freshNonce == null || !freshNonce.matches("[A-Za-z0-9_-]{24,128}")
                || targetRootEnrollmentSignature == null
                || targetRootEnrollmentSignature.length != 64)
            return new byte[0];
        java.util.Base64.Encoder b64 = java.util.Base64.getEncoder();
        return ("future-g1/one-shot-atomic-epoch-ledger-migration/v1\n"
                + authorityId + "\n" + fromEpoch + "\n" + toEpoch + "\n"
                + claimCount + "\n" + b64.encodeToString(expectedLedgerDigest)
                + "\n" + freshNonce + "\n"
                + b64.encodeToString(targetRootEnrollmentSignature) + "\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    /**
     * A single provisioned capability, not a caller-selected per-request
     * provider. Only the separate administrative bootstrap may call bind;
     * callers receive this already-bound capability without backend access.
     * This pure Java prototype CANNOT prove who called bind, protect the
     * remote ledger, or make an in-process fixture rollback-resistant.
     * Both enrolled identities AND independently held minimum epoch are required.
     */
    public static final class ProvisionedOneShotGateway {
        private final IndependentMonotonicClaimFloorProvider authority;
        private final byte[][] pins;
        private final long epoch;
        private final String authorityId;
        private ProvisionedOneShotGateway(IndependentMonotonicClaimFloorProvider backend,
                byte[][] pinned, long enrolledEpoch, String enrolledAuthorityId) {
            authority = backend;
            pins = new byte[pinned.length][];
            for (int i = 0; i < pinned.length; i++) pins[i] = pinned[i].clone();
            epoch = enrolledEpoch;
            authorityId = enrolledAuthorityId;
        }

        public static ProvisionedOneShotGateway bindFromIndependentBootstrap(
                byte[][] externallyPinnedIdentities,
                SignedOneShotEnrollment enrollment, long independentlyTrustedEnrollmentEpoch,
                IndependentMonotonicClaimFloorProvider externallyProvisionedBackend) {
            if (enrollment == null || externallyProvisionedBackend == null
                    || externallyPinnedIdentities == null
                    || externallyPinnedIdentities.length != 5 || enrollment.pins == null
                    || enrollment.pins.length != 5 || enrollment.signature == null
                    || enrollment.signature.length != 64
                    || independentlyTrustedEnrollmentEpoch <= 0
                    || enrollment.epoch < independentlyTrustedEnrollmentEpoch) return null;
            try {
                java.security.KeyFactory factory = java.security.KeyFactory.getInstance("Ed25519");
                java.security.PublicKey[] keys = new java.security.PublicKey[5];
                for (int i = 0; i < 5; i++) {
                    byte[] pin = externallyPinnedIdentities[i];
                    if (pin == null || !java.util.Arrays.equals(pin, enrollment.pins[i]))
                        return null;
                    keys[i] = factory.generatePublic(
                            new java.security.spec.X509EncodedKeySpec(pin.clone()));
                    if (!java.util.Arrays.equals(pin, keys[i].getEncoded())) return null;
                    for (int j = 0; j < i; j++)
                        if (java.util.Arrays.equals(pin, externallyPinnedIdentities[j]))
                            return null;
                }
                byte[] payload = oneShotEnrollmentPayload(enrollment.authorityId,
                        enrollment.epoch, enrollment.pins[0], enrollment.pins[1],
                        enrollment.pins[2], enrollment.pins[3], enrollment.pins[4]);
                if (payload.length == 0) return null;
                java.security.Signature check = java.security.Signature.getInstance("Ed25519");
                check.initVerify(keys[1]); // PRE-PINNED independent enrollment root
                check.update(payload);
                if (!check.verify(enrollment.signature)) return null;
                // A root-signed identity list alone cannot authenticate the
                // backend currently bound to that enrollment. Require a fresh
                // provider-side challenge, validated with the fifth pinned key.
                byte[] nonceBytes = new byte[24];
                new java.security.SecureRandom().nextBytes(nonceBytes);
                String challenge = java.util.Base64.getUrlEncoder()
                        .withoutPadding().encodeToString(nonceBytes);
                final SignedOneShotProviderAttestation attestation;
                try {
                    attestation = externallyProvisionedBackend.attestEnrollment(
                            enrollment, challenge);
                } catch (Exception unavailable) {
                    return null;
                }
                if (attestation == null
                        || !java.util.Objects.equals(enrollment.authorityId, attestation.authorityId)
                        || enrollment.epoch != attestation.epoch
                        || !challenge.equals(attestation.freshChallenge)
                        || !java.util.Arrays.equals(externallyPinnedIdentities[4],
                                attestation.providerPin())
                        || attestation.signature() == null
                        || attestation.signature().length != 64) return null;
                byte[] message = oneShotProviderAttestationPayload(
                        enrollment.authorityId, enrollment.epoch, payload,
                        enrollment.signature, challenge);
                if (message.length == 0) return null;
                check.initVerify(keys[4]);
                check.update(message);
                if (!check.verify(attestation.signature())) return null;
                return new ProvisionedOneShotGateway(externallyProvisionedBackend,
                        externallyPinnedIdentities, enrollment.epoch, enrollment.authorityId);
            } catch (GeneralSecurityException | RuntimeException invalid) {
                return null;
            }
        }

        /**
         * Make at most ONE remotely provisioned provider migration attempt.
         * Never treat absent/late acknowledgement as safe to retry. The signed
         * response authenticates an expected full-ledger snapshot, NOT whether
         * the injected provider is independently administered or nonrollbackable.
         * New-epoch binding must be separately reauthenticated afterwards.
         */
        public OneShotEpochMigrationOutcome migrateLedgerOnce(
                SignedOneShotEnrollment targetEnrollment,
                byte[] independentlyRetainedSourceLedgerDigest,
                long independentlyRetainedClaimCount, String freshNonce) {
            if (targetEnrollment == null || targetEnrollment.pins == null
                    || targetEnrollment.pins.length != 5
                    || targetEnrollment.signature == null
                    || targetEnrollment.signature.length != 64
                    || !java.util.Objects.equals(authorityId, targetEnrollment.authorityId)
                    || targetEnrollment.epoch != epoch + 1
                    || independentlyRetainedSourceLedgerDigest == null
                    || independentlyRetainedSourceLedgerDigest.length != 32
                    || independentlyRetainedClaimCount < 0)
                return OneShotEpochMigrationOutcome.UNVERIFIABLE;
            for (int i = 0; i < 5; i++)
                if (!java.util.Arrays.equals(pins[i], targetEnrollment.pins[i]))
                    return OneShotEpochMigrationOutcome.NO_AUTHORITY;
            byte[] message = oneShotEpochMigrationPayload(authorityId, epoch,
                    targetEnrollment.epoch, independentlyRetainedClaimCount,
                    independentlyRetainedSourceLedgerDigest, freshNonce,
                    targetEnrollment.signature);
            if (message.length == 0) return OneShotEpochMigrationOutcome.UNVERIFIABLE;
            try {
                java.security.KeyFactory factory = java.security.KeyFactory.getInstance("Ed25519");
                java.security.Signature verifier = java.security.Signature.getInstance("Ed25519");
                verifier.initVerify(factory.generatePublic(
                        new java.security.spec.X509EncodedKeySpec(pins[1])));
                verifier.update(oneShotEnrollmentPayload(authorityId,
                        targetEnrollment.epoch, pins[0], pins[1], pins[2],
                        pins[3], pins[4]));
                if (!verifier.verify(targetEnrollment.signature))
                    return OneShotEpochMigrationOutcome.UNVERIFIABLE;
                final SignedOneShotEpochMigrationReceipt receipt;
                try {
                    receipt = authority.advanceEpochAtomically(targetEnrollment,
                            epoch, independentlyRetainedSourceLedgerDigest.clone(),
                            independentlyRetainedClaimCount, freshNonce);
                } catch (Exception uncertain) {
                    return OneShotEpochMigrationOutcome.STARTED_INDETERMINATE;
                }
                if (receipt == null) return OneShotEpochMigrationOutcome.NO_AUTHORITY;
                if (!java.util.Objects.equals(authorityId, receipt.authorityId)
                        || receipt.fromEpoch != epoch
                        || receipt.toEpoch != targetEnrollment.epoch
                        || receipt.claimCount != independentlyRetainedClaimCount
                        || !java.util.Arrays.equals(independentlyRetainedSourceLedgerDigest,
                                receipt.ledgerDigest())
                        || !java.util.Objects.equals(freshNonce, receipt.freshNonce)
                        || receipt.signature() == null || receipt.signature().length != 64)
                    return OneShotEpochMigrationOutcome.UNVERIFIABLE;
                verifier.initVerify(factory.generatePublic(
                        new java.security.spec.X509EncodedKeySpec(pins[4])));
                verifier.update(message);
                return verifier.verify(receipt.signature())
                        ? OneShotEpochMigrationOutcome.MIGRATION_ATTESTED
                        : OneShotEpochMigrationOutcome.UNVERIFIABLE;
            } catch (GeneralSecurityException | RuntimeException invalid) {
                return OneShotEpochMigrationOutcome.UNVERIFIABLE;
            }
        }

        /** Exactly one remote atomic CAS call, never an automatic retry. */
        public MonotonicReservationOutcome reserveOnce(String claimId, String exactId,
                long revision, byte[] ownerSignature, String freshNonce,
                long previouslyTrustedSequence) {
            if (!validOwnerClaim(claimId, exactId, revision, ownerSignature)
                    || freshNonce == null
                    || !freshNonce.matches("[A-Za-z0-9_-]{16,128}")
                    || previouslyTrustedSequence < 0)
                return MonotonicReservationOutcome.UNVERIFIABLE;
            String boundNonce = oneShotBoundNonce(authorityId, epoch, freshNonce);
            if (boundNonce.isEmpty()) return MonotonicReservationOutcome.UNVERIFIABLE;
            final SignedMonotonicReservation result;
            try {
                result = authority.reserveOnce(claimId, exactId, revision,
                        ownerSignature.clone(), boundNonce);
            } catch (Exception unavailableOrLostAck) {
                // Could have committed before the exception: NEVER retry here.
                return MonotonicReservationOutcome.NO_AUTHORITY;
            }
            return verifyMonotonicReservation(claimId, exactId, revision,
                    ownerSignature, boundNonce, pins[0], pins[1], pins[2],
                    pins[3], pins[4], epoch, previouslyTrustedSequence, result);
        }

        /** A lost reserve acknowledgement is recoverable ONLY by query. */
        public MonotonicQueryOutcome queryExisting(String claimId, String exactId,
                long revision, byte[] ownerSignature, String freshNonce,
                long previouslyTrustedSequence) {
            if (!validOwnerClaim(claimId, exactId, revision, ownerSignature)
                    || freshNonce == null
                    || !freshNonce.matches("[A-Za-z0-9_-]{16,128}")
                    || previouslyTrustedSequence < 0)
                return MonotonicQueryOutcome.UNVERIFIABLE;
            String boundNonce = oneShotBoundNonce(authorityId, epoch, freshNonce);
            if (boundNonce.isEmpty()) return MonotonicQueryOutcome.UNVERIFIABLE;
            final SignedMonotonicReservationStatus status;
            try {
                status = authority.queryExisting(claimId, exactId, revision,
                        ownerSignature.clone(), boundNonce);
            } catch (Exception unavailable) {
                return MonotonicQueryOutcome.REMAIN_INDETERMINATE;
            }
            return verifyMonotonicQueryExisting(claimId, exactId, revision,
                    ownerSignature, boundNonce, pins[0], pins[1], pins[2],
                    pins[3], pins[4], epoch, previouslyTrustedSequence, status);
        }

        private boolean validOwnerClaim(String claim, String id, long revision, byte[] sig) {
            if (sig == null || sig.length != 64
                    || originalOwnerClaimPayload(claim, id, revision).length == 0)
                return false;
            try {
                java.security.PublicKey owner = java.security.KeyFactory
                        .getInstance("Ed25519").generatePublic(
                          new java.security.spec.X509EncodedKeySpec(pins[0]));
                java.security.Signature check = java.security.Signature.getInstance("Ed25519");
                check.initVerify(owner);
                check.update(originalOwnerClaimPayload(claim, id, revision));
                return check.verify(sig);
            } catch (GeneralSecurityException | RuntimeException invalid) {
                return false;
            }
        }
    }

    /**
     * FUTURE-ONLY external provider boundary, not a local trust implementation.
     * The separately administered provider MUST first authenticate the owner,
     * then atomically record a claim reservation in non-caller-replaceable,
     * nonrollbackable storage BEFORE returning its signed decision. The client
     * MUST NOT treat silence, ABSENT, or a lost response as permission to retry.
     * Signing a rollbackable file with the original private key is insufficient.
     */
    public interface IndependentMonotonicClaimFloorProvider {
        /**
         * Read-only signed provider-side enrollment challenge. Default denial
         * preserves fail-closed behavior for old mock/provider implementations.
         * The externally controlled provider must check the root enrollment;
         * this interface alone cannot prove it really does so.
         */
        default SignedOneShotProviderAttestation attestEnrollment(
                SignedOneShotEnrollment enrollment, String freshChallenge)
                throws Exception { return null; }

        /**
         * Single atomic provider transaction: check the source ledger hash,
         * preserve ALL claimed IDs, advance epoch, durably commit, THEN sign.
         * Default denial; a Java callback alone does not provide durable CAS.
         */
        default SignedOneShotEpochMigrationReceipt advanceEpochAtomically(
                SignedOneShotEnrollment targetEnrollment, long fromEpoch,
                byte[] expectedFullLedgerDigest, long expectedClaimCount,
                String freshNonce) throws Exception { return null; }

        SignedMonotonicReservation reserveOnce(String claimId, String exactId,
                long sourceRevision, byte[] ownerSignature,
                String requestNonce) throws Exception;

        /** READ ONLY. Never call reserveOnce to recover a lost acknowledgement. */
        SignedMonotonicReservationStatus queryExisting(String claimId, String exactId,
                long sourceRevision, byte[] ownerSignature,
                String freshQueryNonce) throws Exception;
    }

    public enum MonotonicReservationOutcome {
        NO_AUTHORITY, UNVERIFIABLE, FIRST_RESERVED_PENDING_ACK,
        ALREADY_RESERVED_NO_RETRY
    }

    /** Query results never mean a caller may reserve/consume/retry a claim. */
    public enum MonotonicQueryOutcome {
        NO_AUTHORITY, UNVERIFIABLE, REMAIN_INDETERMINATE,
        ALREADY_RESERVED_NO_RETRY
    }

    public static final class SignedMonotonicReservationStatus {
        public final String claimId, exactId, requestNonce, status;
        public final long sourceRevision, epoch, sequence;
        private final byte[] custodianPin, signature;
        public SignedMonotonicReservationStatus(String claimId, String exactId,
                long sourceRevision, String requestNonce, long epoch,
                long sequence, String status, byte[] custodianPin,
                byte[] signature) {
            this.claimId = claimId;
            this.exactId = exactId;
            this.sourceRevision = sourceRevision;
            this.requestNonce = requestNonce;
            this.epoch = epoch;
            this.sequence = sequence;
            this.status = status;
            this.custodianPin = custodianPin == null ? null : custodianPin.clone();
            this.signature = signature == null ? null : signature.clone();
        }
        public byte[] custodianPin() {
            return custodianPin == null ? null : custodianPin.clone();
        }
        public byte[] signature() {
            return signature == null ? null : signature.clone();
        }
    }

    public static final class SignedMonotonicReservation {
        public final String claimId, exactId, requestNonce, decision;
        public final long sourceRevision, epoch, sequence;
        private final byte[] custodianPin, signature;
        public SignedMonotonicReservation(String claimId, String exactId,
                long sourceRevision, String requestNonce, long epoch,
                long sequence, String decision, byte[] custodianPin,
                byte[] signature) {
            this.claimId = claimId;
            this.exactId = exactId;
            this.sourceRevision = sourceRevision;
            this.requestNonce = requestNonce;
            this.epoch = epoch;
            this.sequence = sequence;
            this.decision = decision;
            this.custodianPin = custodianPin == null ? null : custodianPin.clone();
            this.signature = signature == null ? null : signature.clone();
        }
        public byte[] custodianPin() {
            return custodianPin == null ? null : custodianPin.clone();
        }
        public byte[] signature() {
            return signature == null ? null : signature.clone();
        }
    }

    public static byte[] monotonicReservationPayload(String claimId,
            String exactId, long sourceRevision, byte[] originalOwnerSignature,
            String requestNonce, byte[] ownerPin, byte[] rootPin,
            byte[] operatorPin, byte[] custodianPin, long epoch,
            long sequence, String decision) {
        if (signedReceiptStatusPayload(claimId, exactId, sourceRevision,
                originalOwnerSignature, requestNonce, sequence, "CONSUMED").length == 0
                || ownerPin == null || rootPin == null || operatorPin == null
                || custodianPin == null || ownerPin.length == 0
                || rootPin.length == 0 || operatorPin.length == 0
                || custodianPin.length == 0 || epoch <= 0 || sequence <= 0
                || !("FIRST_RESERVED".equals(decision)
                    || "ALREADY_RESERVED".equals(decision)))
            return new byte[0];
        java.util.Base64.Encoder b64 = java.util.Base64.getEncoder();
        return ("future-g1/independent-monotonic-reservation/v1\n" + claimId + "\n"
                + exactId + "\n" + sourceRevision + "\n"
                + b64.encodeToString(originalOwnerSignature) + "\n"
                + requestNonce + "\n" + b64.encodeToString(ownerPin) + "\n"
                + b64.encodeToString(rootPin) + "\n"
                + b64.encodeToString(operatorPin) + "\n"
                + b64.encodeToString(custodianPin) + "\n"
                + epoch + "\n" + sequence + "\n" + decision + "\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    /**
     * A distinct domain from reserveOnce: a signed status may be queried
     * repeatedly but NEVER authorizes a new reservation. "ABSENT" is only
     * insufficient evidence; even an authentic ABSENT cannot imply retry.
     */
    public static byte[] monotonicQueryExistingPayload(String claimId,
            String exactId, long sourceRevision, byte[] originalOwnerSignature,
            String freshQueryNonce, byte[] ownerPin, byte[] rootPin,
            byte[] operatorPin, byte[] custodianPin, long epoch,
            long sequence, String status) {
        if (originalOwnerClaimPayload(claimId, exactId, sourceRevision).length == 0
                || originalOwnerSignature == null || originalOwnerSignature.length != 64
                || freshQueryNonce == null
                || !freshQueryNonce.matches("[A-Za-z0-9_-]{16,128}")
                || ownerPin == null || rootPin == null || operatorPin == null
                || custodianPin == null || ownerPin.length == 0
                || rootPin.length == 0 || operatorPin.length == 0
                || custodianPin.length == 0 || epoch <= 0 || sequence < 0
                || !("RESERVED".equals(status) && sequence > 0
                    || "ABSENT".equals(status) && sequence == 0))
            return new byte[0];
        java.util.Base64.Encoder b64 = java.util.Base64.getEncoder();
        return ("future-g1/independent-monotonic-query-existing/v1\n"
                + claimId + "\n" + exactId + "\n" + sourceRevision + "\n"
                + b64.encodeToString(originalOwnerSignature) + "\n"
                + freshQueryNonce + "\n" + b64.encodeToString(ownerPin) + "\n"
                + b64.encodeToString(rootPin) + "\n"
                + b64.encodeToString(operatorPin) + "\n"
                + b64.encodeToString(custodianPin) + "\n"
                + epoch + "\n" + sequence + "\n" + status + "\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Verifies an authenticated read-only provider status with five pre-pinned
     * distinct Ed25519 keys. This method is side-effect-free and cannot call
     * reserveOnce. Provider identity and previous minimum MUST be provisioned
     * outside the caller's rollbackable storage; this prototype cannot prove
     * such custody. Missing or signed ABSENT is NOT retry authorization.
     */
    public static MonotonicQueryOutcome verifyMonotonicQueryExisting(
            String claimId, String exactId, long sourceRevision,
            byte[] originalOwnerSignature, String freshQueryNonce,
            byte[] pinnedOwner, byte[] pinnedRoot, byte[] pinnedOperator,
            byte[] pinnedCustodian, byte[] pinnedIndependentProvider,
            long previouslyTrustedEpoch, long previouslyTrustedClaimSequence,
            SignedMonotonicReservationStatus receipt) {
        if (receipt == null || pinnedIndependentProvider == null
                || pinnedOwner == null || pinnedRoot == null
                || pinnedOperator == null || pinnedCustodian == null)
            return MonotonicQueryOutcome.NO_AUTHORITY;
        if (!java.util.Objects.equals(claimId, receipt.claimId)
                || !java.util.Objects.equals(exactId, receipt.exactId)
                || sourceRevision != receipt.sourceRevision
                || !java.util.Objects.equals(freshQueryNonce, receipt.requestNonce)
                || previouslyTrustedEpoch <= 0 || previouslyTrustedClaimSequence < 0
                || receipt.epoch < previouslyTrustedEpoch
                || receipt.sequence < previouslyTrustedClaimSequence)
            return MonotonicQueryOutcome.UNVERIFIABLE;
        if (!java.util.Arrays.equals(receipt.custodianPin(), pinnedCustodian))
            return MonotonicQueryOutcome.NO_AUTHORITY;
        byte[] payload = monotonicQueryExistingPayload(claimId, exactId,
                sourceRevision, originalOwnerSignature, freshQueryNonce,
                pinnedOwner, pinnedRoot, pinnedOperator, pinnedCustodian,
                receipt.epoch, receipt.sequence, receipt.status);
        byte[] signature = receipt.signature();
        if (payload.length == 0 || signature == null || signature.length != 64)
            return MonotonicQueryOutcome.UNVERIFIABLE;
        try {
            java.security.KeyFactory factory = java.security.KeyFactory.getInstance("Ed25519");
            byte[][] pins = {pinnedOwner, pinnedRoot, pinnedOperator,
                    pinnedCustodian, pinnedIndependentProvider};
            java.security.PublicKey[] keys = new java.security.PublicKey[pins.length];
            for (int i = 0; i < pins.length; i++) {
                keys[i] = factory.generatePublic(
                        new java.security.spec.X509EncodedKeySpec(pins[i].clone()));
                if (!java.util.Arrays.equals(keys[i].getEncoded(), pins[i]))
                    return MonotonicQueryOutcome.NO_AUTHORITY;
                for (int j = 0; j < i; j++)
                    if (java.util.Arrays.equals(pins[i], pins[j]))
                        return MonotonicQueryOutcome.NO_AUTHORITY;
            }
            java.security.Signature verifier = java.security.Signature.getInstance("Ed25519");
            verifier.initVerify(keys[0]);
            verifier.update(originalOwnerClaimPayload(claimId, exactId, sourceRevision));
            if (!verifier.verify(originalOwnerSignature))
                return MonotonicQueryOutcome.UNVERIFIABLE;
            verifier.initVerify(keys[4]);
            verifier.update(payload);
            if (!verifier.verify(signature))
                return MonotonicQueryOutcome.UNVERIFIABLE;
        } catch (GeneralSecurityException | RuntimeException badProof) {
            return MonotonicQueryOutcome.UNVERIFIABLE;
        }
        if ("RESERVED".equals(receipt.status))
            return MonotonicQueryOutcome.ALREADY_RESERVED_NO_RETRY;
        // Even a verified "ABSENT" is never authority to issue reserveOnce.
        return MonotonicQueryOutcome.REMAIN_INDETERMINATE;
    }

    /**
     * FUTURE-ONLY recovery after a lost acknowledgement. Both replies MUST be
     * freshly signed over the same original owner claim and query nonce by
     * independently pre-enrolled provider and custodian identities. A provider
     * reservation alone is NOT a completed memory operation. Only a separate
     * read-only ALREADY_CONSUMED custodian acknowledgement can complete proof.
     *
     * This PURE verifier performs no reserveOnce/consume/query calls or disk
     * writes. Missing, ABSENT, conflicting or untrusted evidence NEVER grants
     * retry permission. The trusted minima MUST themselves be independently
     * held outside rollbackable caller/provider/custodian snapshots. This
     * method cannot implement or prove such external nonrollbackable custody.
     */
    public static ReceiptStatusOutcome reconcileLostAckAcrossAuthoritiesReadOnly(
            String claimId, String exactId, long sourceRevision,
            byte[] originalOwnerSignature, String freshQueryNonce,
            byte[] pinnedOwner, byte[] pinnedRoot, byte[] pinnedOperator,
            byte[] pinnedCustodian, byte[] pinnedIndependentProvider,
            long trustedProviderEpoch, long trustedReservationSequence,
            long trustedCustodianEpoch, long trustedConsumedSequence,
            SignedMonotonicReservationStatus providerStatus,
            SignedAtomicCustodianAck custodianReadOnlyAck) {
        if (providerStatus == null || custodianReadOnlyAck == null)
            return ReceiptStatusOutcome.REMAIN_INDETERMINATE;
        // The initial COMMITTED acknowledgement is not a recovered read-only
        // status. The status signer must attest ALREADY_CONSUMED for this query.
        if (!"ALREADY_CONSUMED".equals(custodianReadOnlyAck.state))
            return ReceiptStatusOutcome.UNVERIFIABLE;
        MonotonicQueryOutcome reserved = verifyMonotonicQueryExisting(
                claimId, exactId, sourceRevision, originalOwnerSignature,
                freshQueryNonce, pinnedOwner, pinnedRoot, pinnedOperator,
                pinnedCustodian, pinnedIndependentProvider,
                trustedProviderEpoch, trustedReservationSequence, providerStatus);
        if (reserved == MonotonicQueryOutcome.NO_AUTHORITY)
            return ReceiptStatusOutcome.NO_AUTHORITY;
        if (reserved == MonotonicQueryOutcome.UNVERIFIABLE)
            return ReceiptStatusOutcome.UNVERIFIABLE;
        if (reserved != MonotonicQueryOutcome.ALREADY_RESERVED_NO_RETRY)
            return ReceiptStatusOutcome.REMAIN_INDETERMINATE;
        ReceiptStatusOutcome consumed = verifyAtomicCustodianReadOnlyQuery(
                claimId, exactId, sourceRevision, originalOwnerSignature,
                freshQueryNonce, pinnedOwner, pinnedRoot, pinnedOperator,
                pinnedCustodian, trustedCustodianEpoch, trustedConsumedSequence,
                custodianReadOnlyAck);
        // No provider or caller status may substitute for an authenticated
        // independently custodied positive memory-commit receipt.
        return consumed == ReceiptStatusOutcome.CONSUMED_VERIFIED
                ? consumed : (consumed == ReceiptStatusOutcome.NO_AUTHORITY
                        ? consumed : ReceiptStatusOutcome.UNVERIFIABLE);
    }

    /**
     * Verifies a pre-consumption provider decision, not a memory commit.
     * FIRST_RESERVED_PENDING_ACK is NOT a success receipt and never triggers
     * an automatic custodian retry. ALREADY_RESERVED means no new attempt;
     * the caller must only perform independent read-only recovery. A previous
     * protected minimum from outside the caller is REQUIRED to detect rollback.
     * Complete external-provider rollback remains unprovable with local files.
     */
    public static MonotonicReservationOutcome verifyMonotonicReservation(
            String claimId, String exactId, long sourceRevision,
            byte[] originalOwnerSignature, String requestNonce,
            byte[] pinnedOwner, byte[] pinnedRoot, byte[] pinnedOperator,
            byte[] pinnedCustodian, byte[] pinnedIndependentProvider,
            long previouslyTrustedEpoch, long previouslyTrustedClaimSequence,
            SignedMonotonicReservation receipt) {
        if (receipt == null || pinnedIndependentProvider == null
                || pinnedOwner == null || pinnedRoot == null
                || pinnedOperator == null || pinnedCustodian == null)
            return MonotonicReservationOutcome.NO_AUTHORITY;
        if (!java.util.Objects.equals(claimId, receipt.claimId)
                || !java.util.Objects.equals(exactId, receipt.exactId)
                || sourceRevision != receipt.sourceRevision
                || !java.util.Objects.equals(requestNonce, receipt.requestNonce)
                || previouslyTrustedEpoch <= 0 || previouslyTrustedClaimSequence < 0
                || receipt.epoch < previouslyTrustedEpoch
                || receipt.sequence < previouslyTrustedClaimSequence)
            return MonotonicReservationOutcome.UNVERIFIABLE;
        if (!java.util.Arrays.equals(receipt.custodianPin(), pinnedCustodian))
            return MonotonicReservationOutcome.NO_AUTHORITY;
        byte[] payload = monotonicReservationPayload(claimId, exactId,
                sourceRevision, originalOwnerSignature, requestNonce,
                pinnedOwner, pinnedRoot, pinnedOperator, pinnedCustodian,
                receipt.epoch, receipt.sequence, receipt.decision);
        byte[] signature = receipt.signature();
        if (payload.length == 0 || signature == null || signature.length != 64)
            return MonotonicReservationOutcome.UNVERIFIABLE;
        try {
            java.security.KeyFactory factory = java.security.KeyFactory.getInstance("Ed25519");
            byte[][] pins = {pinnedOwner, pinnedRoot, pinnedOperator,
                    pinnedCustodian, pinnedIndependentProvider};
            java.security.PublicKey[] keys = new java.security.PublicKey[pins.length];
            for (int i = 0; i < pins.length; i++) {
                keys[i] = factory.generatePublic(
                        new java.security.spec.X509EncodedKeySpec(pins[i].clone()));
                if (!java.util.Arrays.equals(keys[i].getEncoded(), pins[i]))
                    return MonotonicReservationOutcome.NO_AUTHORITY;
                for (int j = 0; j < i; j++)
                    if (java.util.Arrays.equals(pins[i], pins[j]))
                        return MonotonicReservationOutcome.NO_AUTHORITY;
            }
            java.security.Signature verifier = java.security.Signature.getInstance("Ed25519");
            verifier.initVerify(keys[0]);
            verifier.update(originalOwnerClaimPayload(claimId, exactId, sourceRevision));
            if (!verifier.verify(originalOwnerSignature))
                return MonotonicReservationOutcome.UNVERIFIABLE;
            verifier.initVerify(keys[4]);
            verifier.update(payload);
            if (!verifier.verify(signature))
                return MonotonicReservationOutcome.UNVERIFIABLE;
        } catch (GeneralSecurityException | RuntimeException badProof) {
            return MonotonicReservationOutcome.UNVERIFIABLE;
        }
        if ("FIRST_RESERVED".equals(receipt.decision)) {
            // An already-spent claim cannot be reclassified as a first grant.
            return previouslyTrustedClaimSequence == 0
                    ? MonotonicReservationOutcome.FIRST_RESERVED_PENDING_ACK
                    : MonotonicReservationOutcome.UNVERIFIABLE;
        }
        return MonotonicReservationOutcome.ALREADY_RESERVED_NO_RETRY;
    }

    /**
     * FUTURE-ONLY contract for a separately enrolled anti-reset custodian.
     * The operator key, owner/root pins, and trusted epoch/floor MUST be held
     * outside this process in genuinely rollback-resistant custody. Supplying
     * those values in ordinary caller-controlled storage provides NO security.
     * A valid operator signature alone is NOT proof of nonrollbackable storage.
     */
    public enum CustodyEnrollmentVerdict {
        VERIFIED_CONDITIONALLY, ROLLBACK_DETECTED, NO_AUTHORITY, UNVERIFIABLE
    }

    public static final class SignedCustodyEnrollment {
        public final String claimId, nonce;
        public final long epoch, consumedFloor;
        private final byte[] rootPin, signature;

        public SignedCustodyEnrollment(String claimId, String nonce, long epoch,
                long consumedFloor, byte[] rootPin, byte[] signature) {
            this.claimId = claimId;
            this.nonce = nonce;
            this.epoch = epoch;
            this.consumedFloor = consumedFloor;
            this.rootPin = rootPin == null ? null : rootPin.clone();
            this.signature = signature == null ? null : signature.clone();
        }
        public byte[] rootPin() { return rootPin == null ? null : rootPin.clone(); }
        public byte[] signature() { return signature == null ? null : signature.clone(); }
    }

    public static byte[] custodyEnrollmentPayload(String claimId,
            byte[] ownerPin, byte[] rootPin, long epoch, long consumedFloor,
            String nonce) {
        if (claimId == null || !claimId.matches("[A-Za-z0-9_-]{8,128}")
                || nonce == null || !nonce.matches("[A-Za-z0-9_-]{16,128}")
                || epoch <= 0 || consumedFloor < 0 || ownerPin == null
                || rootPin == null || ownerPin.length == 0 || rootPin.length == 0)
            return new byte[0];
        return ("future-g1/operator-anchored-enrollment/v1\n" + claimId + "\n"
                + java.util.Base64.getEncoder().encodeToString(ownerPin) + "\n"
                + java.util.Base64.getEncoder().encodeToString(rootPin) + "\n"
                + epoch + "\n" + consumedFloor + "\n" + nonce + "\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    public static CustodyEnrollmentVerdict verifyOperatorCustodyEnrollment(
            String claimId, String freshNonce, byte[] previouslyPinnedOwner,
            byte[] previouslyPinnedRoot, byte[] previouslyPinnedOperator,
            long previouslyTrustedEpoch, long previouslyTrustedFloor,
            SignedCustodyEnrollment enrollment) {
        if (previouslyPinnedOwner == null || previouslyPinnedRoot == null
                || previouslyPinnedOperator == null || enrollment == null)
            return CustodyEnrollmentVerdict.NO_AUTHORITY;
        if (previouslyTrustedEpoch <= 0 || previouslyTrustedFloor < 0
                || !java.util.Objects.equals(claimId, enrollment.claimId)
                || !java.util.Objects.equals(freshNonce, enrollment.nonce))
            return CustodyEnrollmentVerdict.UNVERIFIABLE;
        byte[] signed = custodyEnrollmentPayload(enrollment.claimId,
                previouslyPinnedOwner, enrollment.rootPin(), enrollment.epoch,
                enrollment.consumedFloor, enrollment.nonce);
        if (signed.length == 0 || enrollment.signature() == null
                || enrollment.signature().length != 64)
            return CustodyEnrollmentVerdict.UNVERIFIABLE;
        try {
            java.security.KeyFactory factory = java.security.KeyFactory.getInstance("Ed25519");
            java.security.PublicKey owner = factory.generatePublic(
                    new java.security.spec.X509EncodedKeySpec(previouslyPinnedOwner.clone()));
            java.security.PublicKey root = factory.generatePublic(
                    new java.security.spec.X509EncodedKeySpec(previouslyPinnedRoot.clone()));
            java.security.PublicKey operator = factory.generatePublic(
                    new java.security.spec.X509EncodedKeySpec(previouslyPinnedOperator.clone()));
            if (!java.util.Arrays.equals(owner.getEncoded(), previouslyPinnedOwner)
                    || !java.util.Arrays.equals(root.getEncoded(), previouslyPinnedRoot)
                    || !java.util.Arrays.equals(operator.getEncoded(), previouslyPinnedOperator)
                    || java.util.Arrays.equals(owner.getEncoded(), root.getEncoded())
                    || java.util.Arrays.equals(owner.getEncoded(), operator.getEncoded())
                    || java.util.Arrays.equals(root.getEncoded(), operator.getEncoded())
                    || !java.util.Arrays.equals(root.getEncoded(), enrollment.rootPin()))
                return CustodyEnrollmentVerdict.NO_AUTHORITY;
            java.security.Signature verifier = java.security.Signature.getInstance("Ed25519");
            verifier.initVerify(operator);
            verifier.update(signed);
            if (!verifier.verify(enrollment.signature()))
                return CustodyEnrollmentVerdict.UNVERIFIABLE;
        } catch (GeneralSecurityException | RuntimeException error) {
            return CustodyEnrollmentVerdict.UNVERIFIABLE;
        }
        // Check the floor only AFTER authenticating operator-signed bytes.
        // This catches original-key reprovision when an independent trusted
        // floor survives, but cannot protect a simultaneously rolled-back pin.
        if (enrollment.epoch < previouslyTrustedEpoch
                || enrollment.consumedFloor < previouslyTrustedFloor)
            return CustodyEnrollmentVerdict.ROLLBACK_DETECTED;
        return CustodyEnrollmentVerdict.VERIFIED_CONDITIONALLY;
    }

    /**
     * READ ONLY: an invalid/reprovisioned enrollment MUST NOT query the root.
     * A positive answer remains CONDITIONAL on external nonrollbackable custody;
     * this adapter never creates, retries, or consumes a claim.
     */
    public static ReceiptStatusOutcome reconcileReceiptWithCustodyEnrollment(
            String claimId, String exactId, long sourceRevision,
            byte[] originalOwnerSignature, String freshNonce,
            byte[] previouslyPinnedOwner, byte[] previouslyPinnedRoot,
            byte[] previouslyPinnedOperator, long trustedEpoch, long trustedFloor,
            SignedCustodyEnrollment enrollment,
            ReadOnlyReceiptStatusAuthority originalRoot) {
        CustodyEnrollmentVerdict gate = verifyOperatorCustodyEnrollment(
                claimId, freshNonce, previouslyPinnedOwner, previouslyPinnedRoot,
                previouslyPinnedOperator, trustedEpoch, trustedFloor, enrollment);
        if (gate == CustodyEnrollmentVerdict.NO_AUTHORITY)
            return ReceiptStatusOutcome.NO_AUTHORITY;
        if (gate != CustodyEnrollmentVerdict.VERIFIED_CONDITIONALLY)
            return ReceiptStatusOutcome.UNVERIFIABLE;
        // Never accept the root's status below the authenticated enrollment.
        long minimum = Math.max(trustedFloor, enrollment.consumedFloor);
        return reconcileReceiptWithIndependentHighWater(claimId, exactId,
                sourceRevision, originalOwnerSignature, freshNonce,
                previouslyPinnedOwner, previouslyPinnedRoot, minimum, originalRoot);
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

    /**
     * FUTURE-ONLY required capability for a separate, protected G1 high-water
     * source. The protected epoch MUST remain advanced after a complete restore
     * of ordinary G1 snapshots, journals, anchor files and backups. A normal
     * file, AndroidKeyStore AES-GCM key or caller-supplied string does NOT prove
     * this requirement. Only a trusted source owner can install this adapter.
     *
     * An owner MUST recheck the protected epoch inside the same serialized
     * dispatchUnderOwnerLock transaction, including an ALREADY_COMPLETED read.
     * The outer checks below are fail-closed guards, not a replacement for an
     * atomic, nonrollbackable Android G1 implementation.
     */
    public enum OwnerHighWaterStatus {
        CURRENT, ROLLED_BACK, MISSING, COMPROMISED
    }

    public interface OwnerHighWaterDispatchAuthority extends OwnerAtomicDispatchAuthority {
        OwnerHighWaterStatus protectedGenerationStatus(String certifiedGeneration);
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
            List<Candidate> sealed = frozenCandidateSnapshot(candidates);
            if (sealed == null || !certificate.generation.equals(held.generation())
                    || !trustedG1Verifier.verifies(certificate, sealed))
                return OwnerPublicationOutcome.REJECTED_BEFORE_START;
            List<Match> matches = retrieveAssumingOwnerVerified(query, sealed,
                    certificate.retiredIds, certificate.confirmedRetiredParentIds,
                    maxResults, now);
            if (!certificate.generation.equals(held.generation())
                    || !trustedG1Verifier.isStillCurrent(certificate))
                return OwnerPublicationOutcome.REJECTED_BEFORE_START;
            ArrayList<String> summaries = new ArrayList<>(matches.size());
            for (Match match : matches) summaries.add(match.candidate.summary);
            byte[] intent = ownerDispatchIntent(ownerIssuedRequestId, query,
                    certificate, sealed, maxResults, now, summaries);
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
            List<Candidate> sealed = frozenCandidateSnapshot(candidates);
            if (sealed == null || !certificate.generation.equals(held.generation())
                    || !trustedG1Verifier.verifies(certificate, sealed))
                return OwnerPublicationOutcome.REJECTED_BEFORE_START;
            List<Match> matches = retrieveAssumingOwnerVerified(query, sealed,
                    certificate.retiredIds, certificate.confirmedRetiredParentIds,
                    maxResults, now);
            if (!certificate.generation.equals(held.generation())
                    || !trustedG1Verifier.isStillCurrent(certificate))
                return OwnerPublicationOutcome.REJECTED_BEFORE_START;
            ArrayList<String> summaries = new ArrayList<>(matches.size());
            for (Match match : matches) summaries.add(match.candidate.summary);
            byte[] intent = ownerDispatchIntent(ownerIssuedRequestId, query,
                    certificate, sealed, maxResults, now, summaries);
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

    /**
     * Post-v83 high-water-gated strict dispatch. Legacy strict owners without
     * independent antirollback capability are rejected BEFORE publication.
     * Missing, stale, corrupt, null or throwing attestations fail closed; no
     * attempt is made to reconstruct or reset authority from local files.
     * After a transaction was entered, loss of the witness is INDETERMINATE,
     * never a signal to retry a possibly delivered external side effect.
     *
     * This is an INTERFACE AND JVM POLICY ONLY. The injected owner can lie;
     * there is no real protected counter, Android G1 store, or provider sink.
     */
    public static OwnerPublicationOutcome publishCertifiedOwnerContextHighWater(
            String ownerIssuedRequestId, String query, List<Candidate> candidates,
            OwnerLineageCertificate certificate, OwnerLineageVerifier trustedG1Verifier,
            int maxResults, long now) {
        if (certificate == null || certificate.generation == null
                || trustedG1Verifier == null
                || !(trustedG1Verifier.ownerAtomicDispatchAuthority
                     instanceof OwnerHighWaterDispatchAuthority))
            return OwnerPublicationOutcome.REJECTED_BEFORE_START;
        OwnerHighWaterDispatchAuthority owner = (OwnerHighWaterDispatchAuthority)
                trustedG1Verifier.ownerAtomicDispatchAuthority;
        try {
            if (owner.protectedGenerationStatus(certificate.generation)
                    != OwnerHighWaterStatus.CURRENT)
                return OwnerPublicationOutcome.REJECTED_BEFORE_START;
        } catch (RuntimeException ex) {
            return OwnerPublicationOutcome.REJECTED_BEFORE_START;
        }
        OwnerPublicationOutcome result = publishCertifiedOwnerContextAtomic(
                ownerIssuedRequestId, query, candidates, certificate,
                trustedG1Verifier, maxResults, now);
        try {
            if (owner.protectedGenerationStatus(certificate.generation)
                    != OwnerHighWaterStatus.CURRENT)
                return result == OwnerPublicationOutcome.REJECTED_BEFORE_START
                        ? result : OwnerPublicationOutcome.STARTED_INDETERMINATE;
        } catch (RuntimeException ex) {
            return result == OwnerPublicationOutcome.REJECTED_BEFORE_START
                    ? result : OwnerPublicationOutcome.STARTED_INDETERMINATE;
        }
        return result;
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
            List<Candidate> sealed = frozenCandidateSnapshot(candidates);
            if (sealed == null || !certificate.generation.equals(held.generation())
                    || !trustedG1Verifier.verifies(certificate, sealed))
                return false;
            List<Match> matches = retrieveAssumingOwnerVerified(query, sealed,
                    certificate.retiredIds, certificate.confirmedRetiredParentIds,
                    maxResults, now);
            // The atomic owner lease must prevent all intervening revision
            // changes; these checks also reject a broken/stale lease witness.
            if (!certificate.generation.equals(held.generation())
                    || !trustedG1Verifier.isStillCurrent(certificate))
                return false;
            callbackStarted = true;
            consumer.consume(Collections.unmodifiableList(new ArrayList<>(matches)));
            // A faulty owner lease may allow a correction/revocation while the
            // synchronous publisher is executing. Do not certify completion
            // against a now-stale source even if the callback returned cleanly.
            // It is already too late to label this a retryable non-delivery:
            // catch below reports INDETERMINATE, requiring manual recovery.
            if (!certificate.generation.equals(held.generation())
                    || !trustedG1Verifier.isStillCurrent(certificate))
                throw new IllegalStateException(
                    "G1 source revision advanced during provider callback");
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

    /**
     * Freeze the exact G1 projection BEFORE its MAC is checked. A mutable
     * caller-owned List can swap elements during certificate serialization;
     * authenticating one view and evaluating a later view would expose memory
     * that was never signed by the source owner. Candidate itself is immutable.
     * This is not a substitute for the G1 owner's atomic generation/lease.
     */
    private static List<Candidate> frozenCandidateSnapshot(List<Candidate> records) {
        if (records == null || records.isEmpty() || records.size() > MAX_CANDIDATES)
            return null;
        try {
            return Collections.unmodifiableList(new ArrayList<>(records));
        } catch (RuntimeException ex) {
            return null; // Torn or concurrently-invalid projection: fail closed.
        }
    }

    /** Certified path: without a trusted owner-pinned verifier, no unlock. */
    public static List<Match> retrieveCertified(String query, List<Candidate> candidates,
            OwnerLineageCertificate certificate, OwnerLineageVerifier trustedG1Verifier,
            int maxResults, long now) {
        List<Candidate> sealed = frozenCandidateSnapshot(candidates);
        if (sealed == null || trustedG1Verifier == null
                || !trustedG1Verifier.verifies(certificate, sealed))
            return Collections.emptyList();
        List<Match> result = retrieveAssumingOwnerVerified(query, sealed,
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
        List<Candidate> sealed = frozenCandidateSnapshot(candidates);
        if (sealed == null || trustedG1Verifier == null
                || !trustedG1Verifier.verifies(certificate, sealed))
            return "";
        String target = correctionAssumingOwnerVerified(correctionText, sealed,
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
        return retrieveAssumingOwnerVerified(query, candidates, retiredIds,
                confirmedRetiredParentIds, maxResults, now, true);
    }

    // A negated fact must not be returned as an affirmative answer (or vice
    // versa). Correction suggestions are different: a user saying "nicht Luna"
    // may intentionally be correcting an old positive "Luna" memory, so the
    // correction-ID path explicitly opts out of this retrieval polarity filter.
    private static List<Match> retrieveAssumingOwnerVerified(String query, List<Candidate> candidates,
            Set<String> retiredIds, Set<String> confirmedRetiredParentIds,
            int maxResults, long now, boolean requireMatchingNegation) {
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
        // Interior pictographs can distinguish the subject yet vanish from the
        // Latin tokenizer. Trailing decorations preserve the prior contract.
        // Explicit [REDACTED: field] / <hidden> labels in a user query mean
        // the requested subject is incomplete. Their surviving topic words
        // must not prove a different record. Preserve the established provider
        // handling of bare stars/blocks and the stricter correction-only gate.
        if (hasExplicitTaggedRedactionMarker(query)
                || hasImportedQuoteFrame(query)
                || hasMarkdownBlockquoteFrame(query)
                || hasImportedTranscriptFrame(query)
                || hasSemanticLeadingOrInfixPictograph(query))
            return Collections.emptyList();
        Set<String> wanted = terms(query);
        if (wanted.isEmpty()) return Collections.emptyList();
        // Numeric facts (age, year, device model, amount) must not match a
        // different value merely because several topical words coincide.
        // A broad question without a number may still retrieve a dated fact.
        Set<String> requestedNumericAnchors = numericAnchors(query);
        if (requestedNumericAnchors == null) return Collections.emptyList();
        // An explicit sign is part of a factual numeric value. In particular,
        // -5, +5 and unsigned 5 are not interchangeable evidence.
        Set<String> requestedSignedNumbers = signedNumberAnchors(query);
        if (requestedSignedNumbers == null) return Collections.emptyList();
        // A matching number does not license a conflicting physical unit:
        // 5 years != 5 months; 5 kg != 5 g. Pure conservative recall only.
        Set<String> requestedMeasures = measuredNumberUnits(query);
        if (requestedMeasures == null) return Collections.emptyList();
        // A stated year boundary is a fact, not a stopword: "vor 2024"
        // cannot authorize a memory asserting "nach 2024" merely because
        // the subject and year agree. This also protects suggested IDs.
        Set<String> requestedYearRelations = explicitYearRelations(query);
        if (requestedYearRelations == null) return Collections.emptyList();
        // An explicit numeric inequality is a semantic qualifier: at least
        // 500 is not at most 500, and strict >5 is not >=5.
        Set<String> requestedQuantityBounds = explicitQuantityBounds(query);
        if (requestedQuantityBounds == null) return Collections.emptyList();
        // Preserve numeric relative-age direction for provider-safe recall
        // and read-only correction-ID suggestions alike.
        Set<String> requestedRelativeAges = explicitRelativeAges(query);
        if (requestedRelativeAges == null) return Collections.emptyList();
        // "Anna ist 2 Jahre alt" is an absolute age, NOT the relation
        // "Anna ist 2 Jahre älter". Numeric/topic overlap is insufficient.
        Set<String> requestedAbsoluteAges = explicitAbsoluteAges(query);
        if (requestedAbsoluteAges == null) return Collections.emptyList();
        // A hedged/disjunctive age is not a definite memory fact, and a
        // correction request containing an unresolved alternative cannot
        // safely identify one source record by lexical overlap.
        if (hasUncertainAbsoluteAge(query)) return Collections.emptyList();
        Set<String> requestedAdditionalAgeMeasures =
                additionalExplicitAgeMeasures(query);
        if (requestedAdditionalAgeMeasures == null) return Collections.emptyList();
        // Verified past possession cannot prove current possession, or vice versa.
        String requestedHaveTime = explicitFirstPersonHaveTime(query);
        if (requestedHaveTime == null) return Collections.emptyList();
        Set<String> queryNegationCues = explicitNegationCues(query);
        Set<String> queryNegatedPredicates = negatedPredicateAnchors(query);
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
            // A partially redacted G1 projection is incomplete evidence even
            // when its surviving words match. Do not rank it for provider recall;
            // its ID still participates in lineage/supersession quarantine.
            if (isExplicitRedactionMarker(c.summary)
                    || hasImportedQuoteFrame(c.summary)
                || hasMarkdownBlockquoteFrame(c.summary)
                || hasImportedTranscriptFrame(c.summary)
                    || hasSemanticLeadingOrInfixPictograph(c.summary)
                    || hasUncertainAbsoluteAge(c.summary)) continue;
            String storedHaveTime = explicitFirstPersonHaveTime(c.summary);
            if (storedHaveTime == null
                    || (!requestedHaveTime.isEmpty() && !storedHaveTime.isEmpty()
                    && !requestedHaveTime.equals(storedHaveTime))) continue;
            Set<String> candidateNegationCues = explicitNegationCues(c.summary);
            if (requireMatchingNegation
                    && !queryNegationCues.equals(candidateNegationCues)) continue;
            // Equal negative cues with different objects are different facts.
            // For correction, retain the deliberate positive->negative edit
            // exception, but never guess an ID from two mismatched negations.
            if (!queryNegationCues.isEmpty() && !candidateNegationCues.isEmpty()) {
                Set<String> candidatePredicates = negatedPredicateAnchors(c.summary);
                if (queryNegatedPredicates.isEmpty() || candidatePredicates.isEmpty()
                        || !queryNegatedPredicates.equals(candidatePredicates)) continue;
            }
            if (!requestedNumericAnchors.isEmpty()) {
                Set<String> storedNumbers = numericAnchors(c.summary);
                // Written-out German ages may express the very same explicit
                // absolute-age signature as a digit query. Accept that one
                // narrow spelling equivalence only if ALL numeric query
                // anchors belong to the age and the stored fact has NO
                // additional numeric anchors to hide an unrelated mismatch.
                Set<String> storedAge = explicitAbsoluteAges(c.summary);
                boolean wordAgeEquivalent = storedNumbers != null
                        && storedNumbers.isEmpty() && !requestedAbsoluteAges.isEmpty()
                        && storedAge != null && storedAge.containsAll(requestedAbsoluteAges)
                        && requestedNumericAnchors.equals(absoluteAgeNumericAnchors(query))
                        && requestedMeasures.equals(absoluteAgeDigitMeasures(query));
                if (storedNumbers == null || (!storedNumbers.containsAll(requestedNumericAnchors)
                        && !wordAgeEquivalent))
                    continue;
                Set<String> storedSignedNumbers = signedNumberAnchors(c.summary);
                if (storedSignedNumbers == null
                        || !requestedSignedNumbers.equals(storedSignedNumbers)) continue;
                if (!requestedMeasures.isEmpty()) {
                    Set<String> storedMeasures = measuredNumberUnits(c.summary);
                    if (storedMeasures == null || (!storedMeasures.containsAll(requestedMeasures)
                            && !wordAgeEquivalent))
                        continue;
                }
                if (!requestedYearRelations.isEmpty()) {
                    Set<String> storedYearRelations = explicitYearRelations(c.summary);
                    if (storedYearRelations == null
                            || !storedYearRelations.containsAll(requestedYearRelations)) continue;
                }
                if (!requestedQuantityBounds.isEmpty()) {
                    Set<String> storedQuantityBounds = explicitQuantityBounds(c.summary);
                    if (storedQuantityBounds == null
                            || !storedQuantityBounds.containsAll(requestedQuantityBounds)) continue;
                }
            }
            // Word-number ages have no digit anchors. The age-specific
            // identity/direction/quantity guard must still run for them.
            if (!requestedRelativeAges.isEmpty()) {
                Set<String> storedRelativeAges = explicitRelativeAges(c.summary);
                if (storedRelativeAges == null
                        || !storedRelativeAges.containsAll(requestedRelativeAges)) continue;
            }
            if (!requestedAbsoluteAges.isEmpty()) {
                Set<String> storedAbsoluteAges = explicitAbsoluteAges(c.summary);
                if (storedAbsoluteAges == null
                        || !storedAbsoluteAges.containsAll(requestedAbsoluteAges)) continue;
            }
            if ((!requestedRelativeAges.isEmpty() || !requestedAbsoluteAges.isEmpty())
                    && !requestedAdditionalAgeMeasures.isEmpty()) {
                Set<String> storedAdditionalAgeMeasures =
                        additionalExplicitAgeMeasures(c.summary);
                if (storedAdditionalAgeMeasures == null
                        || !storedAdditionalAgeMeasures.containsAll(requestedAdditionalAgeMeasures))
                    continue;
            }
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
        // A partially hidden correction request is not an intact user subject.
        // Remaining visible terms must never authorize a guessed memory ID;
        // the owner-provided exact-ID correction UI is the safe fallback.
        if (isExplicitRedactionMarker(correctionText)
                || hasImportedQuoteFrame(correctionText)
                || hasMarkdownBlockquoteFrame(correctionText)
                || hasImportedTranscriptFrame(correctionText)
                || hasUnrepresentedPictograph(correctionText)
                // A past/present possession question is read-only. Never
                // guess a memory ID for mutation from an inverted question.
                || FIRST_PERSON_HAVE_QUESTION.matcher(safe(correctionText)).find()) return "";
        Set<String> query = terms(correctionText);
        if (query.size() < 2) return "";
        List<Match> matches = retrieveAssumingOwnerVerified(correctionText, candidates,
                retiredIds, confirmedRetiredParentIds, 8, now, false);
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
            // A source update stamped in the future may be clock skew or a
            // reordered G1 owner snapshot. Do not infer absence when its
            // text independently matches the user's correction subject.
            // Preserve the original rule for unrelated or redacted future
            // records: no lexical evidence alone is not a current rival.
            boolean futureSubjectRival = rival.updatedAtMs > now
                    && overlap(query, terms(rival.summary)) >= 2;
            boolean potentiallyCurrent = now >= 0 && (corruptTime || futureSubjectRival
                    || (rival.updatedAtMs <= now
                        && (rival.expiresAtMs == 0 || rival.expiresAtMs > now)));
            if (!potentiallyCurrent) continue;
            if (retiredIds.contains(rival.id)) continue;
            if (rival.summary.isEmpty() || isExplicitRedactionMarker(rival.summary)) return "";
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
                    || corruptTime || futureSubjectRival || !Double.isFinite(rival.importance);
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

    // Preserve even 1- and 2-digit numbers (and model identifiers such as S25)
    // separately from topical terms, which deliberately discard short words.
    // This is a conservative lexical guard, not numeric semantic equivalence.
    private static Set<String> numericAnchors(String text) {
        Set<String> result = new HashSet<>();
        if (text == null || text.length() > MAX_INPUT || !wellFormedUtf16(text)
                || hasNonTextControl(text) || hasUnrepresentableLexicalChars(text))
            return result;
        String normalized = Normalizer.normalize(safe(text).toLowerCase(Locale.GERMANY),
                Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .replaceAll("[^a-z0-9]+", " ").trim();
        if (normalized.isEmpty()) return result;
        for (String word : normalized.split("\\s+")) {
            if (word.matches(".*[0-9].*")) result.add(word);
            if (result.size() > MAX_QUERY_TOKENS) return null;
        }
        // Adjacent numeric segments carry meaning: 1,5 and 1.5 denote the
        // same written decimal, but 1/5 is a fraction, not that decimal;
        // 10:30 is a clock time, not 10.30. A vanished separator must not
        // silently turn two different measurements into matching evidence.
        String numberStructure = Normalizer.normalize(safe(text).toLowerCase(Locale.GERMANY),
                Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .replaceAll("(?<=[0-9]),(?=[0-9])", ".");
        java.util.regex.Matcher sequence = java.util.regex.Pattern.compile(
                "[0-9]+(?:[./:-][0-9]+)+").matcher(numberStructure);
        while (sequence.find()) {
            result.add("sequence:" + sequence.group());
            if (result.size() > MAX_QUERY_TOKENS) return null;
        }
        return result;
    }

    // The leading relation changes what a dated claim actually asserts.
    // Stay narrow: only explicit standalone prepositions directly before
    // a four-digit year/date, optionally with one German date article (e.g.
    // "bis zum 31.12.2027", "seit dem 31.12.2027"). Never infer tense,
    // vague dates or relative time. Generic dated queries still match.
    private static final java.util.regex.Pattern EXPLICIT_YEAR_RELATION =
            java.util.regex.Pattern.compile(
                "(?<![\\p{L}\\p{N}])(vor|nach|seit|bis|ab)\\s+(?:(?:dem|der|zum)\\s+)?((?:[0-3]?[0-9][./][0-1]?[0-9][./])?[12][0-9]{3})(?![\\p{L}\\p{N}])");

    private static Set<String> explicitYearRelations(String text) {
        if (text == null || text.length() > MAX_INPUT || !wellFormedUtf16(text)
                || hasNonTextControl(text) || hasUnrepresentableLexicalChars(text))
            return null;
        String normalized = Normalizer.normalize(text.toLowerCase(Locale.GERMANY),
                Normalizer.Form.NFKC);
        Set<String> result = new HashSet<>();
        java.util.regex.Matcher m = EXPLICIT_YEAR_RELATION.matcher(normalized);
        while (m.find()) {
            result.add(m.group(1) + ":" + m.group(2));
            if (result.size() > MAX_QUERY_TOKENS) return null;
        }
        return result;
    }

    // Whitelist only standalone, digit-anchored German quantity comparisons.
    // These are lexical exact-bound safeguards, not arithmetic reasoning,
    // fuzzy quantity interpretation, or an autonomous memory update.
    private static final java.util.regex.Pattern EXPLICIT_QUANTITY_BOUND =
            java.util.regex.Pattern.compile(
                "(?<![\\p{L}\\p{N}])(mindestens|wenigstens|minimal|höchstens|hoechstens|maximal|über|ueber|unter|mehr\\s+als|weniger\\s+als|genau|exakt)\\s+(?:(?:[€$]|eur|usd|euro|dollar)\\s*)?([+-]?[0-9]+(?:[.,][0-9]+)?)(?![\\p{L}\\p{N}])");

    // Symbolic comparisons carry the same inclusive/exclusive distinction.
    // Normalize >= and ≥ (or <= and ≤) to one bound family, not to equality.
    private static final java.util.regex.Pattern SYMBOLIC_QUANTITY_BOUND =
            java.util.regex.Pattern.compile(
                "(?<![\\p{L}\\p{N}])(>=|<=|≥|≤|>|<)\\s*(?:(?:[€$]|eur|usd|euro|dollar)\\s*)?([+-]?[0-9]+(?:[.,][0-9]+)?)(?![\\p{L}\\p{N}])");

    // German monetary range prepositions: "ab €500" is a lower bound,
    // "bis 500 Euro", "bis zu 500 Euro" and "bis einschließlich 500 Euro"
    // are upper bounds. Require an EXPLICIT currency token;
    // bare dates ("bis 2027") or arbitrary numeric words never become
    // guessed quantities. No currency conversion or domain fact inference.
    private static final java.util.regex.Pattern CURRENCY_PREPOSITION_BOUND =
            java.util.regex.Pattern.compile(
                "(?<![\\p{L}\\p{N}])(ab|bis(?:\\s+(?:zu|einschließlich|einschliesslich|inklusive))?)\\s+(?:(?:[€$]|eur|usd|euro|dollar)\\s*([+-]?[0-9]+(?:[.,][0-9]+)?)|([+-]?[0-9]+(?:[.,][0-9]+)?)\\s*(?:[€$]|eur|usd|euro|dollar))(?![\\p{L}\\p{N}])");

    private static Set<String> explicitQuantityBounds(String text) {
        if (text == null || text.length() > MAX_INPUT || !wellFormedUtf16(text)
                || hasNonTextControl(text) || hasUnrepresentableLexicalChars(text))
            return null;
        String normalized = Normalizer.normalize(text.toLowerCase(Locale.GERMANY),
                Normalizer.Form.NFKC).replace('\u2212', '-');
        Set<String> bounds = new HashSet<>();
        java.util.regex.Matcher match = EXPLICIT_QUANTITY_BOUND.matcher(normalized);
        while (match.find()) {
            String family = match.group(1).replaceAll("\\s+", " ");
            String op = family.equals("mindestens") || family.equals("wenigstens")
                    || family.equals("minimal") ? "GE"
                    : family.equals("höchstens") || family.equals("hoechstens") || family.equals("maximal") ? "LE"
                    : family.equals("genau") || family.equals("exakt") ? "EQ"
                    : family.equals("mehr als") || family.equals("über") || family.equals("ueber") ? "GT" : "LT";
            bounds.add(op + ":" + match.group(2).replace(',', '.'));
            if (bounds.size() > MAX_QUERY_TOKENS) return null;
        }
        java.util.regex.Matcher symbolic = SYMBOLIC_QUANTITY_BOUND.matcher(normalized);
        while (symbolic.find()) {
            String relation = symbolic.group(1);
            String op = relation.equals(">=") || relation.equals("≥") ? "GE"
                    : relation.equals("<=") || relation.equals("≤") ? "LE"
                    : relation.equals(">") ? "GT" : "LT";
            bounds.add(op + ":" + symbolic.group(2).replace(',', '.'));
            if (bounds.size() > MAX_QUERY_TOKENS) return null;
        }
        java.util.regex.Matcher currencyPreposition = CURRENCY_PREPOSITION_BOUND.matcher(normalized);
        while (currencyPreposition.find()) {
            String value = currencyPreposition.group(2) != null
                    ? currencyPreposition.group(2) : currencyPreposition.group(3);
            bounds.add((currencyPreposition.group(1).equals("ab") ? "GE:" : "LE:")
                    + value.replace(',', '.'));
            if (bounds.size() > MAX_QUERY_TOKENS) return null;
        }
        return bounds;
    }

    // Numeric relative age is a directed fact with an optional explicit
    // comparator. "2 Jahre älter als ich" must never authorize "als du"
    // or "als mein Bruder" merely because age/direction/topic are shared.
    // This is bounded lexical evidence, not subject inversion or grammar.
    private static final java.util.regex.Pattern EXPLICIT_RELATIVE_AGE =
            java.util.regex.Pattern.compile(
                "(?<![\\p{L}\\p{N}])([0-9]+(?:[.,][0-9]+)?|ein|eins|zwei|drei|vier|fünf|fuenf|sechs|sieben|acht|neun|zehn|elf|zwölf|zwoelf)\\s+"
                + "(jahr|jahre|jahren|monat|monate|monaten)\\s+"
                + "(älter|aelter|jünger|juenger)(?![\\p{L}\\p{N}])"
                + "(?:\\s+als\\s+([\\p{L}]+(?:\\s+[\\p{L}]+){0,6}))?");

    // A stated age relationship binds to its named subject, not merely to the
    // shared duration/direction/comparator. This only recognizes a narrow,
    // explicit German possessive/article + name + "ist/war" form immediately
    // before the age value. Ambiguous or overlong explicit subject phrases
    // abstain; a genuinely broad query without a named subject remains broad.
    private static final java.util.regex.Pattern EXPLICIT_RELATIVE_AGE_SUBJECT =
            java.util.regex.Pattern.compile(
                "(?<![\\p{L}\\p{N}])"
                + "(mein|meine|meiner|meinen|meinem|meines|"
                + "dein|deine|deiner|deinen|deinem|deines|"
                + "unser|unsere|unserer|unseren|unserem|unseres|"
                + "euer|eure|eurer|euren|eurem|eures|"
                + "sein|seine|seiner|seinen|seinem|seines|"
                + "ihr|ihre|ihrer|ihren|ihrem|ihres|"
                + "deren|dessen|der|die|das|ein|eine|einer|einem|einen|eines)"
                + "\\s+([\\p{L}]+(?:\\s+[\\p{L}]+)*)\\s+(?:ist|war)\\s*$");

    // A bare explicit subject ("Anna ist ...", "Peter Schmidt war ...")
    // also names a distinct person. Without this key the shared numeral and
    // comparator could suggest another person's memory as a correction target.
    // Anchor at the entire prefix: Matcher.find() must never truncate a
    // three/four-part name to its matching last one or two words.
    // Three components are supported; longer/unsupported prefixes abstain.
    private static final java.util.regex.Pattern EXPLICIT_RELATIVE_AGE_BARE_SUBJECT =
            java.util.regex.Pattern.compile(
                "^\\s*([\\p{L}]{2,}(?:\\s+[\\p{L}]{2,}){0,2})"
                + "\\s+(?:ist|war)\\s*$");

    // A textual age quantity is still an explicit numeric fact. Unknown
    // spelled quantities must abstain, rather than falling back to topical
    // word overlap and retrieving an opposing direction or person.
    private static final java.util.regex.Pattern AGE_WORD_NUMBER_CONTEXT =
            java.util.regex.Pattern.compile(
                "(?<![\\p{L}\\p{N}])([\\p{L}]+)\\s+"
                + "(?:jahr|jahre|jahren|monat|monate|monaten)\\s+"
                + "(?:älter|aelter|jünger|juenger)(?![\\p{L}\\p{N}])");

    private static String canonicalRelativeAgeValue(String value) {
        switch (value) {
            case "ein": case "eins": return "1";
            case "zwei": return "2";
            case "drei": return "3";
            case "vier": return "4";
            case "fünf": case "fuenf": return "5";
            case "sechs": return "6";
            case "sieben": return "7";
            case "acht": return "8";
            case "neun": return "9";
            case "zehn": return "10";
            case "elf": return "11";
            case "zwölf": case "zwoelf": return "12";
            default: return value.replace(',', '.');
        }
    }

    // "ist" and "war" do not name interchangeable facts. This deliberately
    // tiny tense cue is used only after a complete explicit subject matched;
    // a broad subject-free age query carries no inferred tense requirement.
    private static String explicitAgeTense(String completeSubjectPrefix) {
        String prefix = completeSubjectPrefix.trim();
        if (prefix.endsWith(" war")) return "PAST";
        if (prefix.endsWith(" ist")) return "PRESENT";
        return "";
    }

    private static Set<String> explicitRelativeAges(String text) {
        if (text == null || text.length() > MAX_INPUT || !wellFormedUtf16(text)
                || hasNonTextControl(text) || hasUnrepresentableLexicalChars(text))
            return null;
        String normalized = Normalizer.normalize(text.toLowerCase(Locale.GERMANY),
                Normalizer.Form.NFKC);
        Set<String> relations = new HashSet<>();
        java.util.regex.Matcher wordQuantity = AGE_WORD_NUMBER_CONTEXT.matcher(normalized);
        while (wordQuantity.find()) {
            String number = wordQuantity.group(1);
            if (canonicalRelativeAgeValue(number).equals(number)) return null;
        }
        java.util.regex.Matcher match = EXPLICIT_RELATIVE_AGE.matcher(normalized);
        while (match.find()) {
            String direction = match.group(3).equals("älter")
                    || match.group(3).equals("aelter") ? "OLDER" : "YOUNGER";
            String unit = match.group(2).startsWith("jahr") ? "YEAR" : "MONTH";
            String base = direction + ":" + canonicalRelativeAgeValue(match.group(1)) + ":" + unit;
            relations.add(base);
            String subjectPrefix = normalized.substring(0, match.start());
            java.util.regex.Matcher subject = EXPLICIT_RELATIVE_AGE_SUBJECT.matcher(subjectPrefix);
            if (subject.matches()) {
                String owner = subject.group(1);
                // German possessive inflection is not a new person. Different
                // owners (my/your/our/his/her) are NOT interchangeable, even
                // if the noun, age, direction and referent are identical.
                // This is only a lexical identity guard, not co-reference
                // resolution: "ihr" still cannot distinguish her/their/Your.
                if (owner.startsWith("mein")) owner = "mein";
                else if (owner.startsWith("dein")) owner = "dein";
                else if (owner.startsWith("unser")) owner = "unser";
                else if (owner.startsWith("eur") || owner.equals("euer")) owner = "euer";
                else if (owner.startsWith("sein")) owner = "sein";
                else if (owner.startsWith("ihr")) owner = "ihr";
                else if (owner.startsWith("ein")) owner = "ein";
                String subjectKey = subject.group(2).trim().replaceAll("\\s+", " ");
                // Never silently truncate a person's complete name/descriptor.
                if (subjectKey.split(" ").length > 8) return null;
                relations.add(base + ":SUB:" + owner + ":" + subjectKey);
                relations.add(base + ":TENSE:" + explicitAgeTense(subjectPrefix));
            } else {
                // A short bare name is explicit identity evidence too. Do not
                // treat "Anna ist 2 Jahre älter" as "Eva ist 2 Jahre älter".
                String prefix = normalized.substring(0, match.start());
                java.util.regex.Matcher bare = EXPLICIT_RELATIVE_AGE_BARE_SUBJECT.matcher(prefix);
                if (bare.matches()) {
                    String name = bare.group(1).trim().replaceAll("\\s+", " ");
                    relations.add(base + ":SUB:bare:" + name);
                    relations.add(base + ":TENSE:" + explicitAgeTense(prefix));
                } else if (!prefix.trim().isEmpty()) {
                    // There is an explicit leading phrase but no supported
                    // complete subject identity. Treating it as a broad age
                    // query could target a different person's saved memory.
                    return null;
                }
            }
            // Add a second, referent-specific evidence key. A broad query
            // without "als ..." may match a precise stored relation, but an
            // explicit "als ich" may not match another or missing referent.
            if (match.group(4) != null) {
                // A truncated comparator must not match a different person
                // because its first seven words happen to be identical.
                java.util.regex.Matcher continuation = java.util.regex.Pattern
                        .compile("\\s+[\\p{L}]").matcher(normalized);
                continuation.region(match.end(), normalized.length());
                if (continuation.lookingAt()) return null;
                String referent = match.group(4).trim().replaceAll("\\s+", " ");
                if (referent.isEmpty()) return null;
                relations.add(base + ":AS:" + referent);
            }
            if (relations.size() > MAX_QUERY_TOKENS) return null;
        }
        return relations;
    }

    // Absolute age is not a directed age comparison. Separate evidence is
    // required before a provider recall or a guessed correction ID is allowed.
    // Bounded direct German "[subject] ist/war N Jahre/Monate alt" only; no
    // inferred birthdays, age arithmetic, or cross-identity name resolution.
    private static final java.util.regex.Pattern EXPLICIT_ABSOLUTE_AGE =
            java.util.regex.Pattern.compile(
                "(?<![\\p{L}\\p{N}])([0-9]+(?:[.,][0-9]+)?|ein|eins|zwei|drei|vier|fünf|fuenf|sechs|sieben|acht|neun|zehn|elf|zwölf|zwoelf)\\s+"
                + "(jahr|jahre|jahren|monat|monate|monaten)"
                + "(?:\\s+und\\s+([0-9]+(?:[.,][0-9]+)?|ein|eins|zwei|drei|vier|fünf|fuenf|sechs|sieben|acht|neun|zehn|elf|zwölf|zwoelf)\\s+"
                + "(jahr|jahre|jahren|monat|monate|monaten))?"
                + "\\s+alt(?![\\p{L}\\p{N}])");
    private static final java.util.regex.Pattern ABSOLUTE_AGE_WORD_CONTEXT =
            java.util.regex.Pattern.compile(
                "(?<![\\p{L}\\p{N}])([\\p{L}]+)\\s+"
                + "(?:jahr|jahre|jahren|monat|monate|monaten)\\s+alt(?![\\p{L}\\p{N}])");

    // FUTURE ONLY: a stated age (absolute OR relative) cannot authorize a
    // provider recall/correction ID when an immediately following uncertainty,
    // disjunction or alternative makes that age fact non-definite. Full stops,
    // question/exclamation marks and common comma hedges are delimiters too.
    // Keep this deliberately narrow: do not infer an unrelated later clause.
    private static final java.util.regex.Pattern UNCERTAIN_AGE_SUFFIX =
            java.util.regex.Pattern.compile(
                "^[\\s.,!?;:()\\[\\]-]*(?:oder\\b|vielleicht\\b|vermutlich\\b|"
                + "wahrscheinlich\\b|möglicherweise\\b|glaube\\s+ich\\b|"
                + "denke\\s+ich\\b|ich\\s+glaube\\b|ich\\s+denke\\b)");

    private static boolean hasUncertainAbsoluteAge(String text) {
        if (text == null || text.length() > MAX_INPUT || !wellFormedUtf16(text)
                || hasNonTextControl(text) || hasUnrepresentableLexicalChars(text))
            return true;
        String normalized = Normalizer.normalize(text.toLowerCase(Locale.GERMANY),
                Normalizer.Form.NFKC);
        java.util.regex.Matcher age = EXPLICIT_ABSOLUTE_AGE.matcher(normalized);
        while (age.find()) {
            if (UNCERTAIN_AGE_SUFFIX.matcher(normalized.substring(age.end())).find())
                return true;
        }
        // An explicitly directed relation can be hedged as well. It must not
        // select a definite memory or guess its correction ID merely because
        // the direction and duration happen to be identical.
        java.util.regex.Matcher relation = EXPLICIT_RELATIVE_AGE.matcher(normalized);
        while (relation.find()) {
            if (UNCERTAIN_AGE_SUFFIX.matcher(normalized.substring(relation.end())).find())
                return true;
        }
        return false;
    }

    private static Set<String> absoluteAgeNumericAnchors(String text) {
        Set<String> anchors = new HashSet<>();
        String normalized = Normalizer.normalize(text.toLowerCase(Locale.GERMANY),
                Normalizer.Form.NFKC);
        java.util.regex.Matcher match = EXPLICIT_ABSOLUTE_AGE.matcher(normalized);
        while (match.find()) {
            if (match.group(1).matches("[0-9]+")) anchors.add(match.group(1));
            if (match.group(3) != null && match.group(3).matches("[0-9]+"))
                anchors.add(match.group(3));
        }
        return anchors;
    }

    private static Set<String> absoluteAgeDigitMeasures(String text) {
        Set<String> result = new HashSet<>();
        String normalized = Normalizer.normalize(text.toLowerCase(Locale.GERMANY),
                Normalizer.Form.NFKC);
        java.util.regex.Matcher match = EXPLICIT_ABSOLUTE_AGE.matcher(normalized);
        while (match.find()) {
            if (match.group(1).matches("[0-9]+"))
                result.add(match.group(1) + ":" + (match.group(2).startsWith("jahr") ? "jahr" : "monat"));
            if (match.group(3) != null && match.group(3).matches("[0-9]+"))
                result.add(match.group(3) + ":" + (match.group(4).startsWith("jahr") ? "jahr" : "monat"));
        }
        return result;
    }

    private static Set<String> explicitAbsoluteAges(String text) {
        if (text == null || text.length() > MAX_INPUT || !wellFormedUtf16(text)
                || hasNonTextControl(text) || hasUnrepresentableLexicalChars(text))
            return null;
        String normalized = Normalizer.normalize(text.toLowerCase(Locale.GERMANY),
                Normalizer.Form.NFKC);
        Set<String> ages = new HashSet<>();
        java.util.regex.Matcher words = ABSOLUTE_AGE_WORD_CONTEXT.matcher(normalized);
        while (words.find()) {
            if (canonicalRelativeAgeValue(words.group(1)).equals(words.group(1))) return null;
        }
        java.util.regex.Matcher match = EXPLICIT_ABSOLUTE_AGE.matcher(normalized);
        while (match.find()) {
            String unit = match.group(2).startsWith("jahr") ? "YEAR" : "MONTH";
            String base = "ABS:" + canonicalRelativeAgeValue(match.group(1)) + ":" + unit;
            if (match.group(3) != null) {
                // Two explicit units form ONE compound age. Reject repeated
                // units or reversed/unsupported composition rather than
                // selecting an ambiguous inner fragment as a correction ID.
                String secondUnit = match.group(4).startsWith("jahr") ? "YEAR" : "MONTH";
                if (!unit.equals("YEAR") || !secondUnit.equals("MONTH")) return null;
                base += "+" + canonicalRelativeAgeValue(match.group(3)) + ":" + secondUnit;
            }
            ages.add(base);
            String prefix = normalized.substring(0, match.start());
            java.util.regex.Matcher named = EXPLICIT_RELATIVE_AGE_SUBJECT.matcher(prefix);
            if (named.matches()) {
                String owner = named.group(1);
                if (owner.startsWith("mein")) owner = "mein";
                else if (owner.startsWith("dein")) owner = "dein";
                else if (owner.startsWith("unser")) owner = "unser";
                else if (owner.startsWith("eur") || owner.equals("euer")) owner = "euer";
                else if (owner.startsWith("sein")) owner = "sein";
                else if (owner.startsWith("ihr")) owner = "ihr";
                else if (owner.startsWith("ein")) owner = "ein";
                String descriptor = named.group(2).trim().replaceAll("\\s+", " ");
                if (descriptor.split(" ").length > 8) return null;
                ages.add(base + ":SUB:" + owner + ":" + descriptor);
                ages.add(base + ":TENSE:" + explicitAgeTense(prefix));
            } else {
                java.util.regex.Matcher bare = EXPLICIT_RELATIVE_AGE_BARE_SUBJECT.matcher(prefix);
                if (bare.matches()) {
                    ages.add(base + ":SUB:bare:" + bare.group(1).trim().replaceAll("\\s+", " "));
                    ages.add(base + ":TENSE:" + explicitAgeTense(prefix));
                } else if (!prefix.trim().isEmpty()) {
                    return null; // Unsupported/ambiguous prefix must not become a broad query.
                }
            }
            if (ages.size() > MAX_QUERY_TOKENS) return null;
        }
        return ages;
    }

    // FUTURE ONLY: a compound fact can name an age AND a second, distinct
    // measurement. The age signature alone must not authorize a shorter
    // age-only memory as a provider recall or correction-ID suggestion.
    // Unlike the numeric-only general unit extractor, this recognizes a
    // tiny, explicit German 1..12 spelling allowlist. Never infer amounts.
    private static final java.util.regex.Pattern AGE_ADDITIONAL_MEASURE =
            java.util.regex.Pattern.compile(
                "(?<![\\p{L}\\p{N}])([0-9]+(?:[.,][0-9]+)?|ein|eins|zwei|drei|vier|fünf|fuenf|sechs|sieben|acht|neun|zehn|elf|zwölf|zwoelf)"
                + "\\s+(km/h|m/s|°c|°f|[\\p{L}]+|€|\\$|%)(?![\\p{L}\\p{N}])");

    private static Set<String> additionalExplicitAgeMeasures(String text) {
        if (text == null || text.length() > MAX_INPUT || !wellFormedUtf16(text)
                || hasNonTextControl(text) || hasUnrepresentableLexicalChars(text))
            return null;
        String normalized = Normalizer.normalize(text.toLowerCase(Locale.GERMANY),
                Normalizer.Form.NFKC);
        java.util.ArrayList<int[]> ageSpans = new java.util.ArrayList<>();
        java.util.regex.Matcher absolute = EXPLICIT_ABSOLUTE_AGE.matcher(normalized);
        while (absolute.find()) ageSpans.add(new int[] {absolute.start(), absolute.end()});
        java.util.regex.Matcher relative = EXPLICIT_RELATIVE_AGE.matcher(normalized);
        while (relative.find()) ageSpans.add(new int[] {relative.start(), relative.end()});
        Set<String> extra = new HashSet<>();
        java.util.regex.Matcher measure = AGE_ADDITIONAL_MEASURE.matcher(normalized);
        while (measure.find()) {
            boolean withinAge = false;
            for (int[] span : ageSpans) {
                if (measure.start() >= span[0] && measure.end() <= span[1]) {
                    withinAge = true;
                    break;
                }
            }
            if (withinAge) continue;
            String unit = canonicalMeasurementUnit(measure.group(2));
            if (unit.isEmpty()) continue;
            extra.add(canonicalRelativeAgeValue(measure.group(1)) + ":" + unit);
            if (extra.size() > MAX_QUERY_TOKENS) return null;
        }
        return extra;
    }

    // Returns only explicitly signed numeric literals, preserving minus/plus
    // and decimal punctuation. The negative/positive/unsigned distinction is
    // deliberately strict because a stale fact must never win by topic overlap.
    // Sign recognition requires a word boundary: S-25 model names and 3-5
    // ranges are not implicitly negative measurements.
    private static Set<String> signedNumberAnchors(String text) {
        if (text == null || text.length() > MAX_INPUT || !wellFormedUtf16(text)
                || hasNonTextControl(text) || hasUnrepresentableLexicalChars(text))
            return null;
        Set<String> signs = new HashSet<>();
        String normalized = Normalizer.normalize(text.toLowerCase(Locale.GERMANY),
                Normalizer.Form.NFKC).replace('\u2212', '-')
                .replaceAll("\\p{M}+", "")
                .replaceAll("(?<=[0-9]),(?=[0-9])", ".");
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "(?<![\\p{L}\\p{N}])[-+][0-9]+(?:\\.[0-9]+)*").matcher(normalized);
        while (matcher.find()) {
            signs.add(matcher.group());
            if (signs.size() > MAX_QUERY_TOKENS) return null;
        }
        return signs;
    }

    // Whitelisted *explicit* measurement/currency/time units only. A literal
    // numerical match is insufficient when the requested unit differs. Do not
    // infer physical conversions or guess unknown units: the G1 owner still
    // owns actual facts and explicit selected-ID correction.
    private static final java.util.regex.Pattern MEASUREMENT_VALUE =
            java.util.regex.Pattern.compile(
                "(?<![\\p{L}\\p{N}.,])([+-]?[0-9]+(?:\\.[0-9]+)?)\\s*(km/h|m/s|°c|°f|[\\p{L}]+|€|\\$|%)");

    // Currency signs may precede the amount ("€5", "$ 5") instead of
    // following it ("5 €"). They must not vanish from numeric evidence:
    // two identically written amounts in different currencies are different.
    private static final java.util.regex.Pattern PREFIX_CURRENCY_VALUE =
            java.util.regex.Pattern.compile(
                "(?<![\\p{L}\\p{N}])([€$]|eur|usd|euro|dollar)\\s*([+-]?[0-9]+(?:\\.[0-9]+)?)(?![\\p{L}\\p{N}])");

    // A named temperature scale following "Grad" belongs to the numeric fact.
    // The generic measurement parser sees only "5 Grad" and otherwise treats
    // "Celsius"/"Fahrenheit" as unbound topic words, allowing false recall.
    // Add an explicit scale anchor without claiming unit conversions.
    private static final java.util.regex.Pattern NAMED_TEMPERATURE_SCALE =
            java.util.regex.Pattern.compile(
                "(?<![\\p{L}\\p{N}.,])([+-]?[0-9]+(?:\\.[0-9]+)?)\\s+grad\\s+(celsius|fahrenheit|kelvin)(?![\\p{L}])");

    private static String canonicalMeasurementUnit(String unit) {
        switch (unit) {
            case "l": case "liter": case "litern": return "liter";
            case "ml": case "milliliter": case "millilitern": return "milliliter";
            case "g": case "gramm": return "gramm";
            case "kg": case "kilogramm": return "kilogramm";
            case "t": case "tonne": case "tonnen": return "tonne";
            case "mm": case "millimeter": return "millimeter";
            case "cm": case "zentimeter": return "zentimeter";
            case "m": case "meter": case "metern": return "meter";
            case "km": case "kilometer": case "kilometern": return "kilometer";
            case "km/h": case "kmh": return "kilometer_pro_stunde";
            case "m/s": return "meter_pro_sekunde";
            case "gallone": case "gallonen": return "gallone";
            case "jahr": case "jahre": case "jahren": return "jahr";
            case "monat": case "monate": case "monaten": return "monat";
            case "woche": case "wochen": return "woche";
            case "tag": case "tage": case "tagen": return "tag";
            case "stunde": case "stunden": return "stunde";
            case "minute": case "minuten": case "min": return "minute";
            case "sekunde": case "sekunden": case "sek": return "sekunde";
            case "eur": case "euro": case "€": return "euro";
            case "usd": case "dollar": case "$": return "dollar";
            case "ps": return "pferdestaerke";
            case "kw": case "kilowatt": return "kilowatt";
            case "grad": return "grad";
            case "°c": case "celsius": return "celsius";
            case "°f": case "fahrenheit": return "fahrenheit";
            case "k": case "kelvin": return "kelvin";
            case "prozent": case "%": return "prozent";
            default: return "";
        }
    }

    private static Set<String> measuredNumberUnits(String text) {
        if (text == null || text.length() > MAX_INPUT || !wellFormedUtf16(text)
                || hasNonTextControl(text) || hasUnrepresentableLexicalChars(text))
            return null;
        String normalized = Normalizer.normalize(text.toLowerCase(Locale.GERMANY),
                Normalizer.Form.NFKC).replace('\u2212', '-')
                .replaceAll("(?<=[0-9]),(?=[0-9])", ".");
        Set<String> units = new HashSet<>();
        java.util.regex.Matcher match = MEASUREMENT_VALUE.matcher(normalized);
        while (match.find()) {
            String unit = canonicalMeasurementUnit(match.group(2));
            if (unit.isEmpty()) continue;
            units.add(match.group(1) + ":" + unit);
            if (units.size() > MAX_QUERY_TOKENS) return null;
        }
        // Prefix currency (symbol or recognized EUR/USD name) is the same
        // explicit amount/unit contract as a
        // recognized suffix; unknown or adjacent model text is not inferred.
        java.util.regex.Matcher prefixCurrency = PREFIX_CURRENCY_VALUE.matcher(normalized);
        while (prefixCurrency.find()) {
            units.add(prefixCurrency.group(2) + ":"
                    + canonicalMeasurementUnit(prefixCurrency.group(1)));
            if (units.size() > MAX_QUERY_TOKENS) return null;
        }
        // The generic matcher captures "5 Grad" but not the trailing named
        // Celsius/Fahrenheit scale. Require both when the scale is explicit.
        java.util.regex.Matcher namedScale = NAMED_TEMPERATURE_SCALE.matcher(normalized);
        while (namedScale.find()) {
            units.add(namedScale.group(1) + ":" + canonicalMeasurementUnit(namedScale.group(2)));
            if (units.size() > MAX_QUERY_TOKENS) return null;
        }
        return units;
    }

    // FUTURE ONLY: explicit first-person "have/had" time. A confirmed past
    // possession is not a current possession (or vice versa), including in
    // inverted read-only questions. This is a bounded grammatical gate, not
    // general past-tense or perfect-aspect understanding.
    private static final java.util.regex.Pattern FIRST_PERSON_HAVE_TIME =
            java.util.regex.Pattern.compile(
                "(?iu)(?<![\\p{L}\\p{N}])(?:ich\\s+(habe|hab|hatte)"
                + "|(habe|hab|hatte)\\s+ich)(?![\\p{L}\\p{N}])");

    private static final java.util.regex.Pattern FIRST_PERSON_HAVE_QUESTION =
            java.util.regex.Pattern.compile("(?iu)\\A\\s*(?:habe|hab|hatte)\\s+ich(?=\\s|[?？]|$)");

    private static final java.util.regex.Pattern FIRST_PERSON_HAVE_PARTICIPLE =
            java.util.regex.Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])(?:gehabt|besessen)(?![\\p{L}\\p{N}])");

    private static String explicitFirstPersonHaveTime(String text) {
        if (text == null || text.length() > MAX_INPUT || !wellFormedUtf16(text)
                || hasNonTextControl(text) || hasUnrepresentableLexicalChars(text)) return null;
        java.util.regex.Matcher m = FIRST_PERSON_HAVE_TIME.matcher(text);
        // German "habe ... gehabt/besessen" is past possession, despite its present
        // auxiliary. Do not use a present-perfect fact to prove possession now.
        boolean pastPerfect = FIRST_PERSON_HAVE_PARTICIPLE.matcher(text).find();
        String time = "";
        int haveForms = 0;
        while (m.find()) {
            haveForms++;
            // More than one have-clause with "gehabt" needs clause parsing;
            // abstain rather than wrongly assign the participle to both.
            if (pastPerfect && haveForms > 1) return null;
            String verb = m.group(1) != null ? m.group(1) : m.group(2);
            String next = ("hatte".equalsIgnoreCase(verb) || pastPerfect)
                    ? "PAST" : "PRESENT";
            if (!time.isEmpty() && !time.equals(next)) return null;
            time = next;
        }
        return time;
    }

    // The German stopword list removes "nicht". That is appropriate for broad
    // matching, but would make opposite-polarity source facts indistinguishable
    // during provider recall. Match only standalone negative markers; e.g.
    // "nichtblau" is one different lexical word, not the marker "nicht".
    private static final java.util.regex.Pattern GERMAN_NEGATION_CUE =
            java.util.regex.Pattern.compile(
                    "(?<![\\p{L}\\p{N}])(?:nicht|kein|keine|keinen|keinem|keiner|keines|nie|niemals|ohne)(?![\\p{L}\\p{N}])");

    // A boolean "any negation" conflates different factual predicates:
    // "Mein Golf Diesel ist nicht blau" must not retrieve "... ohne Dach"
    // solely because both contain some negative cue and two shared anchors.
    // Keep normalized cue families separate; this is deliberately conservative,
    // not a full German clause-scope or logical-negation parser.
    private static Set<String> explicitNegationCues(String sourceText) {
        if (sourceText == null) return Collections.emptySet();
        Set<String> cues = new HashSet<>();
        java.util.regex.Matcher matcher = GERMAN_NEGATION_CUE.matcher(
                sourceText.toLowerCase(Locale.GERMANY));
        while (matcher.find()) {
            String cue = matcher.group();
            cues.add(cue.startsWith("kein") ? "kein" : cue);
        }
        return cues;
    }

    // A negation prefix alone is not an identifiable proposition. In particular,
    // the *first* post-cue content word can be a shared adverb ("wirklich") or
    // the beginning of a different compound clause. Preserve the ordered,
    // representable content of each bounded negated clause instead of choosing
    // just that first word. Extra/mismatching content must abstain rather than
    // turn a shared subject into a false factual match or correction-ID guess.
    // This is a conservative lexical fingerprint, NOT a German syntax parser.
    private static Set<String> negatedPredicateAnchors(String sourceText) {
        if (sourceText == null || sourceText.length() > MAX_INPUT
                || !wellFormedUtf16(sourceText) || hasNonTextControl(sourceText)
                || hasUnrepresentableLexicalChars(sourceText)) return Collections.emptySet();
        Set<String> predicates = new HashSet<>();
        java.util.regex.Matcher cues = GERMAN_NEGATION_CUE.matcher(
                sourceText.toLowerCase(Locale.GERMANY));
        String previousFamily = null;
        int previousEnd = -1;
        while (cues.find()) {
            if (previousEnd >= 0) {
                String signature = negatedClauseSignature(sourceText, previousEnd, cues.start());
                if (signature.isEmpty()) return Collections.emptySet();
                predicates.add(previousFamily + ":" + signature);
            }
            previousFamily = cues.group().startsWith("kein") ? "kein" : cues.group();
            previousEnd = cues.end();
        }
        if (previousEnd >= 0) {
            String signature = negatedClauseSignature(sourceText, previousEnd, sourceText.length());
            if (signature.isEmpty()) return Collections.emptySet();
            predicates.add(previousFamily + ":" + signature);
        }
        return predicates;
    }

    private static String negatedClauseSignature(String text, int begin, int end) {
        java.util.regex.Matcher after = java.util.regex.Pattern
                .compile("[\\p{L}\\p{N}]+").matcher(text);
        after.region(begin, end);
        StringBuilder signature = new StringBuilder();
        boolean contentSeen = false;
        while (after.find()) {
            String word = after.group().toLowerCase(Locale.GERMANY);
            // ``aber``, ``oder`` and ``und`` are discarded by the broad
            // topical tokenizer, but they distinguish different propositions
            // within an otherwise identical negated clause. Preserve their
            // identity only in this narrow negative-clause fingerprint.
            // A bare connector is not a factual predicate.
            if ("aber".equals(word) || "oder".equals(word) || "und".equals(word)) {
                if (!contentSeen) return "";
                signature.append("/join:").append(word);
                continue;
            }
            Set<String> normalized = terms(after.group());
            if (normalized.isEmpty()) continue; // G1/German stopword or small word
            if (signature.length() != 0) signature.append('/');
            signature.append(normalized.iterator().next());
            contentSeen = true;
        }
        return contentSeen ? signature.toString() : "";
    }

    // The German lexical tokenizer discards emoji/pictographs entirely. They
    // can be semantic correction subjects (cat vs dog) rather than decoration.
    // Never infer an automatic correction ID from the remaining visible words;
    // normal provider retrieval remains on its separate read-only path.
    private static boolean hasUnrepresentedPictograph(String value) {
        if (value == null) return false;
        for (int i = 0; i < value.length();) {
            int cp = value.codePointAt(i);
            if ((cp >= 0x1F000 && cp <= 0x1FAFF)
                    || (cp >= 0x2600 && cp <= 0x27BF)) return true;
            i += Character.charCount(cp);
        }
        return false;
    }

    // A pictograph BEFORE the first lexical subject word may identify that
    // subject (e.g. cat versus dog), not merely decorate it. Preserve the
    // existing narrower infix rule and terminal/sentence-end decorations,
    // including "Luna ❤️, heute" which must remain searchable. The first
    // lexical position is computed once so even long pictograph prefixes are
    // checked in linear time.
    // FUTURE ONLY: top-level quoted/code imports are external text frames,
    // not verified first-party G1 facts. This is a conservative lexical
    // rejection, not authenticated provenance or a trust decision.
    private static final java.util.regex.Pattern IMPORTED_QUOTE_FRAME =
            java.util.regex.Pattern.compile("(?im)^[ \\t]*(?:`{1,3}|~{3,}|<(?:blockquote|q|pre|code)(?=[\\s>/]))");

    // CommonMark-style indented code blocks (four spaces or a tab) are also
    // copied/untrusted text, not independently verified user-origin G1 facts.
    // MAX_INPUT bounds the scan; ordinary three-space and inline spacing are
    // deliberately not treated as fenced or indented code.
    private static final java.util.regex.Pattern INDENTED_CODE_FRAME =
            java.util.regex.Pattern.compile("(?m)^(?: {4,}|\\t+)[ \t]*\\S");

    private static boolean hasImportedQuoteFrame(String value) {
        return value != null && value.length() <= MAX_INPUT
                && (IMPORTED_QUOTE_FRAME.matcher(value).find()
                    || INDENTED_CODE_FRAME.matcher(value).find());
    }

    // FUTURE ONLY: quoted/imported Markdown lines are not first-party G1
    // assertions. Detect compact and spaced blockquotes, including nested
    // levels with punctuation or leading numbers. Ordinary
    // greater-than signs inside prose are not line-anchored and remain valid.
    // Also recognize line-leading HTML-escaped/numeric '>' quote markers
    // in pasted exports; inline &gt; relations remain ordinary prose.
    // This lexical guard is not proof of authenticated source provenance.
    private static final java.util.regex.Pattern MARKDOWN_BLOCKQUOTE_FRAME =
            java.util.regex.Pattern.compile(
                "(?im)^[ \t]*(?:>{1,4}|&(?:amp;)?gt;|&(?:amp;)?#(?:0*62|x0*3e);)[ \t]*(?=\\S|\\R)");

    private static boolean hasMarkdownBlockquoteFrame(String value) {
        return value != null && value.length() <= MAX_INPUT
                && MARKDOWN_BLOCKQUOTE_FRAME.matcher(value).find();
    }


    // Imported conversation role headers are not proof of first-party G1
    // origin, even when a copied speaker calls themselves "user". A genuine
    // transcript role may follow an import/export note, so check every role
    // line start, not just the beginning of the whole text. Role labels
    // alone on a line may introduce the copied message on the next line.
    // MAX_INPUT (512 chars) bounds separator scanning instead of an arbitrary
    // blank-line count. This is lexical abstention, not provenance proof.
    private static final java.util.regex.Pattern IMPORTED_TRANSCRIPT_FRAME =
            java.util.regex.Pattern.compile(
                "(?imu)^[ \t]*(?:\\[(?:assistant|user|system|developer|tool)\\]\\s*:?\\s*\\S"
                + "|#{1,6}[ \\t]+(?:assistant|user|system|developer|tool)\\s*(?::[ \\t]*\\S|\\R)"
                + "|<\\|im_start\\|>\\s*(?:assistant|user|system|developer|tool)(?=\\s|$)"
                + "|\\*\\*(?:assistant|user|system|developer|tool):?\\*\\*(?:[ \\t]*\\S|[ \\t]*\\R(?:[ \\t]*\\R)*[ \\t]*\\S)"
                + "|\\[INST\\]\\s*\\S|<<SYS>>\\s*\\S"
                + "|(?:assistant|user|system|developer|tool):(?:[ \\t]*\\S|[ \\t]*\\R(?:[ \\t]*\\R)*[ \\t]*\\S))");

    // A copied conversation line may have a timestamp before its speaker.
    // Recognize only narrow, line-anchored clock/ISO-date prefixes followed
    // by an actual role token. The existing 512-character input cap bounds
    // scanning; matching text is never considered authenticated provenance.
    private static final java.util.regex.Pattern TIMESTAMPED_TRANSCRIPT_FRAME =
            java.util.regex.Pattern.compile(
                "(?imu)^[ \t]*(?:\\[(?:\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}(?::\\d{2})?(?:[.,]\\d{1,9})?(?:Z|[+-]\\d{2}:?\\d{2})?"
                + "|\\d{1,2}:\\d{2}(?::\\d{2})?(?:[.,]\\d{1,9})?(?:,[ \t]*\\d{1,2}\\.\\d{1,2}\\.\\d{4})?"
                + "|\\d{1,2}\\.\\d{1,2}\\.\\d{4}(?:[ \t]*,[ \t]*|[ \t]+)\\d{1,2}:\\d{2}(?::\\d{2})?(?:[.,]\\d{1,9})?)\\]"
                + "|\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}(?::\\d{2})?(?:[.,]\\d{1,9})?(?:Z|[+-]\\d{2}:?\\d{2})?"
                + "|\\d{1,2}:\\d{2}(?::\\d{2})?(?:[.,]\\d{1,9})?)"
                + "[ \t]*(?:[-|][ \t]*)?(?:assistant|user|system|developer|tool):"
                + "(?:[ \t]*\\S|[ \t]*\\R(?:[ \t]*\\R)*[ \t]*\\S)");

    // JSON chat exports may use role/text, role/message, speaker/content,
    // role/parts or streamed role/delta as well as role/content. Recognized
    // role plus a message-body field indicates a copied transcript,
    // never authenticated first-party G1 provenance.
    // Unlabelled JSON facts and unknown role values remain searchable.
    private static final java.util.regex.Pattern JSON_CHAT_ROLE_FIELD =
            java.util.regex.Pattern.compile(
                "(?i)[\\[{,][ \\t\\r\\n]*\\\"(?:role|speaker)\\\"\\s*:\\s*\\\"(?:assistant|user|system|developer|tool)\\\"\\s*[,}]");
    private static final java.util.regex.Pattern JSON_CHAT_CONTENT_FIELD =
            java.util.regex.Pattern.compile("(?i)\\\"(?:content|text|message|parts|delta)\\\"\\s*:");

    // JSON 1.0 permits an ASCII letter in a quoted key or role value to be
    // spelled as backslash-uXXXX. Decode only those letters inside double-quoted JSON
    // strings before checking the existing narrow role/body markers. Do NOT
    // decode JSON punctuation, quotes or slashes: that could invent structure,
    // and a double-escaped backslash must stay a literal key character.
    // This is bounded lexical import abstention, NOT a full JSON parser or
    // a substitute for authenticated first-party G1 provenance.
    private static String normalizedQuotedChatAsciiAliases(String value, boolean yaml) {
        // JSON supports four-digit Unicode escapes; YAML 1.2 double-quoted
        // scalars additionally support two- and eight-digit forms. Neither
        // language decodes escapes in single-quoted YAML strings.
        if (value.indexOf('\\') < 0) return value;
        StringBuilder out = new StringBuilder(value.length());
        char quote = 0;
        boolean yamlComment = false;
        for (int i = 0; i < value.length();) {
            char c = value.charAt(i);
            // A YAML comment is opaque, including its quotes and escapes.
            // It cannot open a quoted key on the following mapping line.
            if (yaml && yamlComment) {
                out.append(c);
                if (c == '\n') yamlComment = false;
                i++;
                continue;
            }
            if (yaml && quote == 0 && c == '#'
                    && (i == 0 || Character.isWhitespace(value.charAt(i - 1)))) {
                yamlComment = true;
                out.append(c);
                i++;
                continue;
            }
            // Escapes in single-quoted YAML scalars are literal; doubled
            // apostrophes are one scalar quote, not a field delimiter.
            if (yaml && quote == '\'' && c == '\''
                    && i + 1 < value.length() && value.charAt(i + 1) == '\'') {
                out.append("''");
                i += 2;
                continue;
            }
            if (quote == '"' && c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(i + 1);
                int digits = next == 'u' ? 4
                        : yaml && next == 'x' ? 2
                        : yaml && next == 'U' ? 8 : 0;
                if (digits > 0 && i + 2 + digits <= value.length()) {
                    int cp = 0;
                    boolean good = true;
                    for (int k = i + 2; k < i + 2 + digits; k++) {
                        int digit = Character.digit(value.charAt(k), 16);
                        if (digit < 0) { good = false; break; }
                        cp = cp * 16 + digit;
                    }
                    if (good && (cp >= 'a' && cp <= 'z'
                            || cp >= 'A' && cp <= 'Z')) {
                        out.append((char) cp);
                        i += 2 + digits;
                        continue;
                    }
                }
                // An escaped slash is NOT a Unicode prefix. Preserve the
                // quoted lexical structure and do not infer real role keys.
                out.append(c).append(next);
                i += 2;
            } else {
                out.append(c);
                if (c == '"' && (quote == 0 || quote == '"'))
                    quote = quote == 0 ? '"' : 0;
                else if (yaml && c == '\'' && quote == '\'') quote = 0;
                else if (yaml && c == '\'' && quote == 0 && (i == 0
                        || !Character.isLetterOrDigit(value.charAt(i - 1))))
                    quote = '\'';
                i++;
            }
        }
        return out.toString();
    }

    private static boolean hasJsonChatFrame(String value) {
        String normalized = normalizedQuotedChatAsciiAliases(value, false);
        return JSON_CHAT_ROLE_FIELD.matcher(normalized).find()
                && JSON_CHAT_CONTENT_FIELD.matcher(normalized).find();
    }

    // YAML-style copied chat records have plain role/speaker lines followed
    // by content/text/message/parts/delta. Both line-anchored fields must
    // occur; an ordinary note with only one field remains eligible. This is
    // conservative import abstention, not authenticated source provenance.
    // YAML 1.2 permits a scalar role to have an anchor and/or the core
    // string tag before its actual actor value. Bound this to two known
    // properties; arbitrary tags or resolved aliases confer no provenance.
    private static final String YAML_ROLE_PROPERTIES =
            "(?:(?:&[A-Za-z0-9_-]{1,64}|!!str|!<tag:yaml\\.org,2002:str>)[ \\t]+){0,2}";
    private static final java.util.regex.Pattern YAML_CHAT_ROLE_LINE =
            java.util.regex.Pattern.compile(
                "(?im)^[ \\t]*(?:-[ \\t]+)?(?:role|speaker|\"(?:role|speaker)\"|'(?:role|speaker)')[ \\t]*:[ \\t]*" + YAML_ROLE_PROPERTIES + "(?:(?:assistant|user|system|developer|tool)|(['\"])(?:assistant|user|system|developer|tool)\\1)(?:[ \\t]+#[^\\r\\n]*)?[ \\t]*(?=\\r?$)");
    private static final java.util.regex.Pattern YAML_CHAT_BODY_LINE =
            java.util.regex.Pattern.compile(
                "(?im)^[ \\t]*(?:-[ \\t]+)?(?:content|text|message|parts|delta|\"(?:content|text|message|parts|delta)\"|'(?:content|text|message|parts|delta)')[ \\t]*:[ \\t]*\\S");

    // A YAML alias (role: *actor) is a reference, not an authenticated
    // first-party statement. Abstain only for a source-proven, allowlisted
    // role anchor immediately followed by its SAME-NAME alias and a body
    // field in one contiguous block. Do not resolve aliases globally: a
    // different YAML document, unknown actor, missing body, quoted note or
    // unrelated anchor cannot authorize cross-block role inference.
    // This narrow copy-transcript classifier is not a YAML parser.
    private static final java.util.regex.Pattern YAML_CHAT_ANCHORED_ALIAS_BLOCK =
            java.util.regex.Pattern.compile(
                "(?im)^[ \\t]*(?:-[ \\t]+)?[A-Za-z_][A-Za-z0-9_-]{0,63}[ \\t]*:[ \\t]*&([A-Za-z0-9_-]{1,64})[ \\t]+"
                + "(?:(?:!!str|!<tag:yaml\\.org,2002:str>)[ \\t]+)?"
                + "(?:(?:assistant|user|system|developer|tool)|(['\\\"])(?:assistant|user|system|developer|tool)\\2)"
                + "(?:[ \\t]+#[^\\r\\n]*)?[ \\t]*\\r?\\n"
                + "(?:[ \\t]*#[^\\r\\n]*\\r?\\n)*"
                + "[ \\t]*(?:-[ \\t]+)?(?:role|speaker)[ \\t]*:[ \\t]*\\*\\1(?:[ \\t]+#[^\\r\\n]*)?[ \\t]*\\r?\\n"
                + "(?:[ \\t]*#[^\\r\\n]*\\r?\\n)*"
                + "[ \\t]*(?:-[ \\t]+)?(?:content|text|message|parts|delta)[ \\t]*:[ \\t]*\\S");

    // YAML flow maps are a one-line alternative to block records:
    // `{role: assistant, content: ...}` or `{content: ..., speaker: user}`.
    // Require BOTH fields inside the SAME bounded, non-nested flow mapping;
    // unrelated notes or an unknown role are not transcript evidence.
    private static final java.util.regex.Pattern YAML_CHAT_FLOW_MAP =
            java.util.regex.Pattern.compile("(?m)^[ \t]*(?:-[ \t]+)?(?:(?:message|frame|record|entry|chat)[ \t]*:[ \t]*)?\\{[^\\r\\n{}]*\\}[ \t]*(?=\\r?$)");
    private static final java.util.regex.Pattern YAML_CHAT_FLOW_ROLE =
            java.util.regex.Pattern.compile(
                "(?i)(?:\\{|,)[ \t]*(?:role|speaker|\"(?:role|speaker)\"|'(?:role|speaker)')\\s*:\\s*" + YAML_ROLE_PROPERTIES + "(?:(?:assistant|user|system|developer|tool)|(['\"])(?:assistant|user|system|developer|tool)\\1)(?=[ \t]*[,}])");
    private static final java.util.regex.Pattern YAML_CHAT_FLOW_BODY =
            java.util.regex.Pattern.compile(
                "(?i)(?:\\{|,)[ \\t\\r\\n]*(?:content|text|message|parts|delta|\"(?:content|text|message|parts|delta)\"|'(?:content|text|message|parts|delta)')\\s*:[ \t]*\\S");

    // YAML may put an alias role and its message body in the SAME flow
    // mapping, with an explicit anchor on the immediately preceding line:
    // actor: &who assistant / message: {role: *who, content: ...}
    // Bound this to adjacent, line-anchored fields and one non-nested map;
    // alias identity must exactly match the source anchor name. Unrelated
    // anchors, literal '*who' strings and body fields in another map do
    // not prove a transcript. No generic YAML alias graph or G1 write.
    private static final java.util.regex.Pattern YAML_CHAT_ANCHORED_ALIAS_FLOW_PAIR =
            java.util.regex.Pattern.compile(
                "(?im)^[ \\t]*(?:-[ \\t]+)?[A-Za-z_][A-Za-z0-9_-]{0,63}[ \\t]*:[ \\t]*&([A-Za-z0-9_-]{1,64})[ \\t]+"
                + "(?:(?:!!str|!<tag:yaml\\.org,2002:str>)[ \\t]+)?"
                + "(?:(?:assistant|user|system|developer|tool)|(['\\\"])(?:assistant|user|system|developer|tool)\\2)"
                + "(?:[ \\t]+#[^\\r\\n]*)?[ \\t]*\\r?\\n"
                + "(?:[ \\t]*#[^\\r\\n]*\\r?\\n)*"
                + "[ \\t]*(?:-[ \\t]+)?(?:message|frame|record|entry|chat)[ \\t]*:[ \\t]*\\{((?:(?:[^{}\\r\\n]|\\{[^{}\\r\\n]{0,256}\\})*\\r?\\n){0,4}(?:[^{}\\r\\n]|\\{[^{}\\r\\n]{0,256}\\})*)\\}[ \\t]*(?=\\r?$)");
    private static final java.util.regex.Pattern YAML_CHAT_FLOW_ALIAS_ROLE =
            java.util.regex.Pattern.compile(
                "(?i)(?:\\{|,)[ \\t\\r\\n]*(?:role|speaker|\\\"(?:role|speaker)\\\"|'(?:role|speaker)')[ \\t]*:[ \\t]*\\*([A-Za-z0-9_-]{1,64})(?=[ \\t\\r\\n]*[,}])");

    // Only for conservative copied-YAML flow frame classification: braces
    // inside quoted scalar values are DATA, not nested mapping delimiters.
    // Keep ordinary keys, values, and quote boundaries intact; never mutate
    // the memory value or trust source metadata. Input is MAX_INPUT bounded.
    private static String maskedYamlQuotedFlowBraces(String value) {
        StringBuilder masked = new StringBuilder(value);
        char quote = 0;
        boolean inlineComment = false;
        int flowDepth = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\n') { inlineComment = false; continue; }
            if (inlineComment) {
                // Inside a flow map, inline comments cannot contain fields.
                // Outside the map, retain comment-only rows because the
                // anchored-alias recognizer uses them as bounded separators.
                if (flowDepth > 0) {
                    if (c != '\r') masked.setCharAt(i, ' ');
                } else if (c == '{' || c == '}' || c == '\'' || c == '"') {
                    masked.setCharAt(i, '_');
                }
                continue;
            }
            if (quote == 0) {
                if (c == '#' && (i == 0 || value.charAt(i - 1) == ' '
                        || value.charAt(i - 1) == '\t')) {
                    inlineComment = true;
                    if (flowDepth > 0) masked.setCharAt(i, ' ');
                } else if (c == '{') {
                    flowDepth++;
                } else if (c == '}' && flowDepth > 0) {
                    flowDepth--;
                } else if ((c == '"' || c == '\'') && (i == 0
                        || !Character.isLetterOrDigit(value.charAt(i - 1)))) {
                    quote = c;
                }
            } else if (c == quote) {
                if (quote == '\'' && i + 1 < value.length()
                        && value.charAt(i + 1) == '\'') {
                    i++;
                } else quote = 0;
            } else if (quote == '"' && c == '\\' && i + 1 < value.length()) {
                i++;
            } else if (c == '{' || c == '}') {
                masked.setCharAt(i, '_');
            }
        }
        return masked.toString();
    }

    // Preserve a quoted actor scalar ONLY when it is an actual top-level
    // role/speaker mapping value, never when role-like words appear in notes.
    private static final java.util.regex.Pattern YAML_FLOW_ACTOR_VALUE_PREFIX =
            java.util.regex.Pattern.compile(
                "(?i)(?:\\{|,)[ \t\\r\\n]*(?:role|speaker|\\x22(?:role|speaker)\\x22|'(?:role|speaker)')[ \t]*:[ \t\\r\\n]*$");

    // Only classify top-level keys in a bounded YAML flow map. Quoted scalar
    // VALUES may contain literal `, role:` or `, content:` text; these are
    // not YAML mapping keys. Preserve actual quoted keys (followed by `:`)
    // while masking nonstructural scalar interiors. This is not a YAML parser.
    private static String maskedYamlFlowQuotedScalarValues(String value) {
        StringBuilder masked = new StringBuilder(value);
        for (int i = 0; i < value.length(); i++) {
            char quote = value.charAt(i);
            if ((quote != '\'' && quote != '"') ||
                    (i > 0 && Character.isLetterOrDigit(value.charAt(i - 1)))) continue;
            int close = -1;
            for (int j = i + 1; j < value.length(); j++) {
                char c = value.charAt(j);
                if (quote == '"' && c == '\\' && j + 1 < value.length()) {
                    j++;
                } else if (c == quote) {
                    if (quote == '\'' && j + 1 < value.length()
                            && value.charAt(j + 1) == '\'') {
                        j++;
                    } else {
                        close = j;
                        break;
                    }
                }
            }
            if (close < 0) continue;
            int next = close + 1;
            while (next < value.length() && Character.isWhitespace(value.charAt(next))) next++;
            boolean actorValue = YAML_FLOW_ACTOR_VALUE_PREFIX.matcher(
                    value.substring(0, i)).find();
            if ((next >= value.length() || value.charAt(next) != ':')
                    && !actorValue) {
                for (int j = i + 1; j < close; j++) {
                    char c = value.charAt(j);
                    if (c != '\n' && c != '\r') masked.setCharAt(j, '_');
                }
            }
            i = close;
        }
        return masked.toString();
    }

    private static boolean hasYamlAnchoredAliasFlowFrame(String value) {
        java.util.regex.Matcher pairs = YAML_CHAT_ANCHORED_ALIAS_FLOW_PAIR.matcher(
                maskedYamlQuotedFlowBraces(value));
        while (pairs.find()) {
            // Comments inside a bounded YAML flow frame are not content/role
            // fields; remove comment-only rows before matching existing keys.
            // A copied message can have a one-level nested content map.
            // Mask nested fields before checking outer role/body, otherwise a
            // nested notes.role could be mistaken for the message actor.
            String flow = pairs.group(3).replaceAll(
                    "(?m)^[ \\t]*#[^\\r\\n]*(?:\\r?\\n|$)", "");
            flow = flow.replaceAll("\\{[^{}\\r\\n]{0,256}\\}", "NESTED");
            flow = maskedYamlFlowQuotedScalarValues(flow);
            flow = "{" + flow + "}";
            java.util.regex.Matcher alias = YAML_CHAT_FLOW_ALIAS_ROLE.matcher(flow);
            if (alias.find() && pairs.group(1).equals(alias.group(1))
                    && YAML_CHAT_FLOW_BODY.matcher(flow).find()) return true;
        }
        return false;
    }

    private static boolean hasYamlFlowChatFrame(String value) {
        // YAML permits named single-line chat maps: `message: {role: ...}`.
        // Quoted scalar braces and comma-delimited note strings are data,
        // not frame structure. This remains a bounded lexical classifier.
        java.util.regex.Matcher maps = YAML_CHAT_FLOW_MAP.matcher(
                maskedYamlQuotedFlowBraces(value));
        while (maps.find()) {
            String flow = maskedYamlFlowQuotedScalarValues(maps.group());
            if (YAML_CHAT_FLOW_ROLE.matcher(flow).find()
                    && YAML_CHAT_FLOW_BODY.matcher(flow).find()) return true;
        }
        return false;
    }

    // YAML may keep copied chat messages in a compact one-line sequence:
    // `messages: [{role: assistant, content: ...}, {role: user, ...}]`.
    // Inspect only bounded top-level flow-map members in known chat arrays.
    // Quoted map-looking strings and fake role/body fields within note scalar
    // values are data, never evidence of authenticated G1 provenance.
    private static final java.util.regex.Pattern YAML_CHAT_FLOW_SEQUENCE =
            java.util.regex.Pattern.compile(
                "(?im)^[ \\t]*(?:messages|chat|history|conversation)[ \\t]*:[ \\t]*\\[([^\\r\\n\\[\\]]{0,512})\\][ \\t]*(?:#[^\\r\\n]*)?(?=\\r?$)");
    // The array itself is matched only on known chat keys. Within its bounded
    // payload, parse matching top-level braces rather than finding any inner
    // `{...}`: an inner note can contain fake role/content that must NEVER
    // become the actor/body of a copied message.
    private static boolean hasYamlFlowSequenceChatFrame(String value) {
        java.util.regex.Matcher sequences = YAML_CHAT_FLOW_SEQUENCE.matcher(
                maskedYamlQuotedFlowBraces(value));
        while (sequences.find()) {
            String items = sequences.group(1);
            int depth = 0;
            int start = -1;
            for (int i = 0; i < items.length(); i++) {
                char c = items.charAt(i);
                if (c == '{') {
                    if (depth == 0) start = i;
                    depth++;
                    if (depth > 8) break; // bounded, unknown syntax abstains
                } else if (c == '}') {
                    if (depth == 0) break;
                    depth--;
                    if (depth == 0 && start >= 0) {
                        String entry = items.substring(start, i + 1);
                        if (entry.length() > 512) break;
                        // Keep outer role/content keys; mask the full contents
                        // of any nested map before running top-level tests.
                        StringBuilder top = new StringBuilder(entry);
                        int inner = 0;
                        for (int j = 1; j < entry.length() - 1; j++) {
                            char innerChar = entry.charAt(j);
                            if (innerChar == '{') inner++;
                            if (inner > 0) top.setCharAt(j, '_');
                            if (innerChar == '}' && inner > 0) inner--;
                        }
                        String flow = maskedYamlFlowQuotedScalarValues(top.toString());
                        if (YAML_CHAT_FLOW_ROLE.matcher(flow).find()
                                && YAML_CHAT_FLOW_BODY.matcher(flow).find()) return true;
                        start = -1;
                    }
                }
            }
        }
        return false;
    }

    // A TOML array-of-tables conversation export may serialize each message as
    // [[messages]] or [[chat.messages]] plus `role`/`content` assignments.
    // Recognize exact known actor and body keys INSIDE ONE table, never across
    // section boundaries. This is lexical abstention, not origin attestation.
    private static final java.util.regex.Pattern TOML_CHAT_MESSAGE_HEADER =
            java.util.regex.Pattern.compile(
                "(?im)^[ \t]*\\[\\[[ \t]*(?:(?:messages?|\"messages?\"|'messages?')|" +
                "(?:chat|\"chat\"|'chat')[ \t]*\\.[ \t]*(?:messages?|\"messages?\"|'messages?'))" +
                "[ \t]*\\]\\][ \t]*(?:#[^\\r\\n]*)?(?=\\r?$)");
    private static final java.util.regex.Pattern TOML_SECTION_HEADER =
            java.util.regex.Pattern.compile(
                "(?m)^[ \t]*\\[[^\\r\\n]*\\][ \t]*(?:#[^\\r\\n]*)?(?=\\r?$)");
    // TOML basic quoted table-key segments permit Unicode escape sequences:
    // [[ "mes\u0073ages" ]] denotes [[ "messages" ]]. Decode only inside
    // double-quoted section keys (not arbitrary notes or quoted values), and
    // never interpret an escaped backslash as the start of a Unicode escape.
    // The enclosing transcript input is MAX_INPUT-bounded.
    private static String normalizedTomlSectionHeader(String header) {
        StringBuilder decoded = new StringBuilder(header.length());
        char quoted = 0;
        for (int i = 0; i < header.length();) {
            char c = header.charAt(i);
            if (quoted == 0) {
                decoded.append(c);
                if (c == '"' || c == '\'') quoted = c;
                i++;
            } else if (quoted == '\'') {
                decoded.append(c);
                if (c == '\'') quoted = 0;
                i++;
            } else if (c == '\\' && i + 1 < header.length()) {
                char escaped = header.charAt(i + 1);
                if (escaped == '\\' || escaped == '"') {
                    decoded.append(c).append(escaped);
                    i += 2;
                    continue;
                }
                int digits = escaped == 'u' ? 4 : escaped == 'U' ? 8 : 0;
                if (digits != 0 && i + 2 + digits <= header.length()) {
                    int codePoint = 0;
                    boolean valid = true;
                    for (int k = i + 2; k < i + 2 + digits; k++) {
                        int digit = Character.digit(header.charAt(k), 16);
                        if (digit < 0) { valid = false; break; }
                        codePoint = (codePoint << 4) | digit;
                    }
                    if (valid && Character.isValidCodePoint(codePoint)
                            && (codePoint < 0xD800 || codePoint > 0xDFFF)) {
                        decoded.appendCodePoint(codePoint);
                        i += 2 + digits;
                        continue;
                    }
                }
                decoded.append(c);
                i++;
            } else {
                decoded.append(c);
                if (c == '"') quoted = 0;
                i++;
            }
        }
        return decoded.toString();
    }

    // TOML basic-quoted assignment names can use the same Unicode escapes as
    // table headers. Decode ONLY full, top-level, double-quoted assignment
    // keys and ONLY if the decoded key is one of the known chat role/body keys.
    // Never normalize values, comments, arbitrary notes, or double-escaped \\u.
    private static final java.util.regex.Pattern TOML_QUOTED_CHAT_ASSIGNMENT_KEY =
            java.util.regex.Pattern.compile(
                "(?m)^([ \\t]*)(\\\"(?:\\\\.|[^\\\"\\\\\\r\\n])*\\\")([ \\t]*=)");

    private static String normalizedTomlChatAssignmentKeys(String fields) {
        java.util.regex.Matcher keys = TOML_QUOTED_CHAT_ASSIGNMENT_KEY.matcher(fields);
        StringBuffer out = new StringBuffer(fields.length());
        while (keys.find()) {
            String candidate = normalizedTomlSectionHeader(keys.group(2));
            if (!candidate.matches("(?i)\\\"(?:role|speaker|content|text|message|parts|delta)\\\""))
                continue;
            keys.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(
                    keys.group(1) + candidate + keys.group(3)));
        }
        keys.appendTail(out);
        return out.toString();
    }

    private static final java.util.regex.Pattern TOML_CHAT_ROLE =
            java.util.regex.Pattern.compile(
                "(?im)^[ \t]*(?:role|speaker|\"(?:role|speaker)\"|'(?:role|speaker)')[ \t]*=[ \t]*(['\"])(?:assistant|user|system|developer|tool)\\1(?:[ \t]*#[^\\r\\n]*)?[ \t]*(?=\\r?$)");
    private static final java.util.regex.Pattern TOML_CHAT_BODY =
            java.util.regex.Pattern.compile(
                "(?im)^[ \t]*(?:content|text|message|parts|delta|\"(?:content|text|message|parts|delta)\"|'(?:content|text|message|parts|delta)')[ \t]*=[ \t]*\\S");

    // Only normalize an explicitly line-anchored TOML messages-array LHS.
    // Quoted table keys may encode letters as \\uXXXX / \\UXXXXXXXX; values,
    // inline metadata, other arrays and double-escaped keys stay untouched.
    private static final java.util.regex.Pattern TOML_CHAT_ARRAY_ASSIGNMENT_NAME =
            java.util.regex.Pattern.compile(
                "(?m)^([ \\t]*)((?:[A-Za-z]+|\\\"[^\\\"\\r\\n]*\\\"|'[^'\\r\\n]*')(?:[ \\t]*\\.[ \\t]*(?:[A-Za-z]+|\\\"[^\\\"\\r\\n]*\\\"|'[^'\\r\\n]*'))?)([ \\t]*=[ \\t]*\\[)");

    private static String tomlChatKeySegment(String segment) {
        String decoded = normalizedTomlSectionHeader(segment.trim());
        if (decoded.length() >= 2 && (decoded.charAt(0) == '\"' || decoded.charAt(0) == '\'')) {
            char quote = decoded.charAt(0);
            if (decoded.charAt(decoded.length() - 1) == quote)
                return decoded.substring(1, decoded.length() - 1);
        }
        return decoded;
    }

    private static int tomlArrayDottedKeySeparator(String key) {
        char quote = 0;
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (quote != 0) {
                if (quote == '\"' && c == '\\' && i + 1 < key.length()) i++;
                else if (c == quote) quote = 0;
            } else if (c == '\"' || c == '\'') quote = c;
            else if (c == '.') return i;
        }
        return -1;
    }

    private static String normalizedTomlChatArrayAssignmentNames(String value) {
        java.util.regex.Matcher names = TOML_CHAT_ARRAY_ASSIGNMENT_NAME.matcher(value);
        StringBuffer out = new StringBuffer(value.length());
        while (names.find()) {
            String raw = names.group(2);
            int dot = tomlArrayDottedKeySeparator(raw);
            boolean known = dot < 0
                    ? tomlChatKeySegment(raw).matches("(?i)messages?")
                    : tomlChatKeySegment(raw.substring(0, dot)).equalsIgnoreCase("chat")
                    && tomlChatKeySegment(raw.substring(dot + 1)).matches("(?i)messages?");
            if (!known) continue;
            names.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(
                    names.group(1) + "messages" + names.group(3)));
        }
        names.appendTail(out);
        return out.toString();
    }

    // TOML also permits array values containing inline message tables, e.g.
    // `messages = [{ role = "assistant", content = "..." }]`.
    // Require an anchored messages assignment and an actor/body pair within
    // the SAME bounded inline table; never conflate two separate entries.
    private static final java.util.regex.Pattern TOML_INLINE_CHAT_ARRAY =
            java.util.regex.Pattern.compile(
                "(?im)^[ \\t]*(?:messages?|chat\\.messages?)[ \\t]*=[ \\t]*\\[([^\\r\\n]*)\\][ \\t]*(?:#[^\\r\\n]*)?(?=\\r?$)");
    // Braces in quoted values are literal; nested inline metadata is real
    // TOML syntax. A bounded scanner isolates complete outer records without
    // pairing a nested metadata key with a top-level message field.
    private static final java.util.regex.Pattern TOML_INLINE_CHAT_ROLE =
            java.util.regex.Pattern.compile(
                "(?i)(?:\\{|,)[ \\t]*(?:role|speaker|\\\"(?:role|speaker)\\\"|'(?:role|speaker)')[ \\t]*=[ \\t]*(['\\\"])(?:assistant|user|system|developer|tool)\\1(?=[ \\t]*[,}])");
    private static final java.util.regex.Pattern TOML_INLINE_CHAT_BODY =
            java.util.regex.Pattern.compile(
                "(?i)(?:\\{|,)[ \\t]*(?:content|text|message|parts|delta|\\\"(?:content|text|message|parts|delta)\\\"|'(?:content|text|message|parts|delta)')[ \\t]*=[ \\t]*\\S");

    private static boolean isTomlTopLevelChatField(String field, boolean actor) {
        // Inline table fields are bounded top-level assignments, so reuse the
        // quoted TOML key decoder without touching string values or metadata.
        String isolated = "{" + normalizedTomlChatAssignmentKeys(field.trim()) + "}";
        return (actor ? TOML_INLINE_CHAT_ROLE : TOML_INLINE_CHAT_BODY)
                .matcher(isolated).lookingAt();
    }

    private static boolean isTomlInlineChatRecord(String record) {
        boolean actor = false, body = false;
        int nested = 0, start = 1;
        char quote = 0;
        for (int i = 1; i < record.length() - 1; i++) {
            char c = record.charAt(i);
            if (quote != 0) {
                if (quote == '"' && c == '\\' && i + 1 < record.length() - 1) i++;
                else if (c == quote) quote = 0;
                continue;
            }
            if (c == '"' || c == '\'') quote = c;
            else if (c == '{') nested++;
            else if (c == '}') nested--;
            else if (c == ',' && nested == 0) {
                String field = record.substring(start, i);
                actor |= isTomlTopLevelChatField(field, true);
                body |= isTomlTopLevelChatField(field, false);
                start = i + 1;
            }
        }
        String last = record.substring(start, record.length() - 1);
        return (actor || isTomlTopLevelChatField(last, true))
                && (body || isTomlTopLevelChatField(last, false));
    }

    // The enclosing input is MAX_INPUT-bounded (512 chars). Quote-aware
    // balancing identifies each whole inline message, not nested metadata
    // tables or unrelated array entries; no backtracking regex is needed.
    private static boolean hasTomlInlineChatRows(String rows) {
        char quote = 0;
        int depth = 0, start = -1;
        for (int i = 0; i < rows.length(); i++) {
            char c = rows.charAt(i);
            if (quote != 0) {
                if (quote == '"' && c == '\\' && i + 1 < rows.length()) i++;
                else if (c == quote) quote = 0;
                continue;
            }
            if (c == '"' || c == '\'') quote = c;
            else if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}' && depth > 0) {
                depth--;
                if (depth == 0 && start >= 0
                        && isTomlInlineChatRecord(rows.substring(start, i + 1)))
                    return true;
            }
        }
        // A clipped export can retain an explicit messages array and its
        // top-level actor/body pair while losing the closing table brace.
        // Abstain instead of authenticating that copied text as G1 memory.
        // Never combine fields across complete or partial sibling records.
        if (depth == 1 && start >= 0 && quote == 0) {
            String partial = rows.substring(start).trim();
            if (partial.endsWith("]")) partial = partial.substring(0, partial.length() - 1).trim();
            return isTomlInlineChatRecord(partial + "}");
        }
        return false;
    }

    // A one-line imported messages array may itself be clipped before `]`.
    // Keep this fallback separate from the strict complete-array matcher.
    private static final java.util.regex.Pattern TOML_CLIPPED_INLINE_CHAT_ARRAY =
            java.util.regex.Pattern.compile(
                "(?im)^[ \t]*(?:messages?|chat\\.messages?)[ \t]*=[ \t]*\\[([^\\r\\n]*)$");

    private static boolean hasTomlInlineChatFrame(String value) {
        String normalized = normalizedTomlChatArrayAssignmentNames(value);
        java.util.regex.Matcher arrays = TOML_INLINE_CHAT_ARRAY.matcher(normalized);
        while (arrays.find()) {
            if (hasTomlInlineChatRows(arrays.group(1))) return true;
        }
        java.util.regex.Matcher clipped = TOML_CLIPPED_INLINE_CHAT_ARRAY.matcher(normalized);
        while (clipped.find()) {
            if (hasTomlInlineChatRows(clipped.group(1))) return true;
        }
        return false;
    }

    // TOML multiline `messages = [ ... ]` arrays may contain inline tables
    // on separate lines. Stop at the closing array bracket, and never cross
    // a new TOML table section. Only a single map with both keys is a frame.
    private static final java.util.regex.Pattern TOML_MULTILINE_CHAT_HEADER =
            java.util.regex.Pattern.compile(
                "(?im)^[ \\t]*(?:messages?|chat\\.messages?)[ \\t]*=[ \\t]*\\[[ \\t]*(?:#[^\\r\\n]*)?(?=\\r?$)");
    private static final java.util.regex.Pattern TOML_MULTILINE_CHAT_END =
            java.util.regex.Pattern.compile(
                "(?m)^[ \\t]*\\][ \\t]*(?:#[^\\r\\n]*)?(?=\\r?$)");

    private static boolean hasTomlMultilineChatFrame(String value) {
        String normalized = normalizedTomlChatArrayAssignmentNames(value);
        java.util.regex.Matcher arrays = TOML_MULTILINE_CHAT_HEADER.matcher(normalized);
        while (arrays.find()) {
            // A copied export may be clipped before its final array bracket.
            // Inspect only the current assignment, ending at the first array
            // close, TOML section or new messages assignment. A role and body
            // must still belong to ONE complete or partial inline record.
            int start = arrays.end();
            int end = normalized.length();
            java.util.regex.Matcher ends = TOML_MULTILINE_CHAT_END.matcher(normalized);
            if (ends.find(start)) end = ends.start();
            java.util.regex.Matcher section = TOML_SECTION_HEADER.matcher(normalized);
            if (section.find(start) && section.start() < end)
                end = section.start();
            java.util.regex.Matcher nextArray = TOML_MULTILINE_CHAT_HEADER.matcher(normalized);
            if (nextArray.find(start) && nextArray.start() < end)
                end = nextArray.start();
            if (hasTomlInlineChatRows(normalized.substring(start, end))) return true;
        }
        return false;
    }

    private static boolean hasTomlChatFrame(String value) {
        java.util.regex.Matcher table = TOML_SECTION_HEADER.matcher(value);
        while (table.find()) {
            if (!TOML_CHAT_MESSAGE_HEADER.matcher(
                    normalizedTomlSectionHeader(table.group())).matches()) continue;
            int start = table.end();
            java.util.regex.Matcher following = TOML_SECTION_HEADER.matcher(value);
            int end = following.find(start) ? following.start() : value.length();
            String fields = normalizedTomlChatAssignmentKeys(value.substring(start, end));
            if (TOML_CHAT_ROLE.matcher(fields).find()
                    && TOML_CHAT_BODY.matcher(fields).find()) return true;
        }
        return false;
    }

    private static boolean hasImportedTranscriptFrame(String value) {
        if (value == null || value.length() > MAX_INPUT) return false;
        // YAML 1.2 double-quoted scalar keys/roles can encode ASCII
        // letters using hexadecimal escapes. The decoder preserves escaped
        // backslashes and single-quoted literals. Normalize only the YAML
        // lexical classifier; never change stored G1 projections.
        String yamlAliases = normalizedQuotedChatAsciiAliases(value, true);
        return IMPORTED_TRANSCRIPT_FRAME.matcher(value).find()
                || TIMESTAMPED_TRANSCRIPT_FRAME.matcher(value).find()
                || hasJsonChatFrame(value)
                || (YAML_CHAT_ROLE_LINE.matcher(yamlAliases).find()
                    && YAML_CHAT_BODY_LINE.matcher(yamlAliases).find())
                || YAML_CHAT_ANCHORED_ALIAS_BLOCK.matcher(yamlAliases).find()
                || hasYamlAnchoredAliasFlowFrame(yamlAliases)
                || hasYamlFlowChatFrame(yamlAliases)
                || hasYamlFlowSequenceChatFrame(yamlAliases)
                || hasTomlChatFrame(value)
                || hasTomlInlineChatFrame(value)
                || hasTomlMultilineChatFrame(value);
    }

    private static boolean hasSemanticLeadingOrInfixPictograph(String value) {
        if (value == null) return false;
        int firstLexical = -1;
        for (int at = 0; at < value.length();) {
            int cp = value.codePointAt(at);
            if (Character.isLetterOrDigit(cp)) {
                firstLexical = at;
                break;
            }
            at += Character.charCount(cp);
        }
        for (int i = 0; i < value.length();) {
            int cp = value.codePointAt(i);
            int start = i;
            i += Character.charCount(cp);
            if (!((cp >= 0x1F000 && cp <= 0x1FAFF)
                    || (cp >= 0x2600 && cp <= 0x27BF))) continue;
            if (firstLexical > start) return true;
            int prev = start;
            while (prev > 0 && Character.isWhitespace(value.codePointBefore(prev)))
                prev -= Character.charCount(value.codePointBefore(prev));
            if (prev == 0 || !Character.isLetterOrDigit(value.codePointBefore(prev)))
                continue;
            int next = i;
            while (next < value.length()) {
                int nextCp = value.codePointAt(next);
                if (nextCp != 0xFE0F && !Character.isWhitespace(nextCp)) break;
                next += Character.charCount(nextCp);
            }
            if (next < value.length() && Character.isLetterOrDigit(value.codePointAt(next)))
                return true;
        }
        return false;
    }

    // A sanitized G1 projection may carry a redacted fragment alongside
    // otherwise unrelated readable text. The visible fragment is not proof
    // that its unknown remainder differs from the requested correction.
    private static boolean hasExplicitTaggedRedactionMarker(String summary) {
        String marker = safe(summary).toLowerCase(Locale.ROOT);
        // Fullwidth punctuation is a common presentation/export variant of
        // explicit privacy tags. Normalize ONLY delimiters and colon here;
        // the explicit deletion/redaction label allowlist stays unchanged.
        marker = marker.replace('［', '[').replace('］', ']')
                .replace('＜', '<').replace('＞', '>').replace('：', ':');
        // G1 may replace private text with a bracketed label that includes
        // its field type (e.g. [REDACTED: name]). Those labels and common
        // deletion variants still conceal the correction subject. Restrict
        // matching to explicit whole bracket tags to avoid treating ordinary
        // prose mentioning "redacted" as an opaque source.
        if (java.util.regex.Pattern.compile(
                "\\[\\s*(?:redacted|hidden|ausgeblendet|gelöscht|removed|deleted|masked|maskiert|zensiert|entfernt)"
                + "(?:\\s*[:=-]\\s*[^\\]<>]{0,64})?\\s*\\]")
                .matcher(marker).find()) return true;
        // Some external projections use explicit angle-delimited redaction
        // labels; ordinary HTML-like tags must remain harmless.
        if (java.util.regex.Pattern.compile(
                "<\\s*(?:redacted|hidden|ausgeblendet|gelöscht|removed|deleted|masked|maskiert|zensiert|entfernt)"
                + "(?:\\s*[:=-]\\s*[^<>]{0,64})?\\s*>")
                .matcher(marker).find()) return true;
        return false;
    }

    private static boolean isExplicitRedactionMarker(String summary) {
        String marker = safe(summary).toLowerCase(Locale.ROOT);
        if (hasExplicitTaggedRedactionMarker(summary)) return true;
        // A lossy projection can separate each redaction glyph by whitespace:
        // "und * * * oder" must not be mistaken for intact stopwords. This
        // conservative abstention applies ONLY to correction-ID suggestions;
        // normal memory recall and its provider authorization stay unchanged.
        // Require three or more identical *isolated* glyphs, not ordinary
        // punctuation such as "?! --- ... ;:" or a single multiplication sign.
        if (java.util.regex.Pattern.compile(
                "(?<!\\S)([.*_-])(?:\\s+\\1){2,}(?!\\S)")
                .matcher(marker).find()) return true;
        // UI/G1 privacy projections may mask a span with solid/shaded Unicode
        // blocks instead of asterisks. The Latin tokenizer discards those
        // symbols, so an otherwise matching *correction request* could select
        // an unrelated ID. Two visible blocks (also space-separated) mean
        // unknown source evidence; a lone decorative square is not enough.
        int blockGlyphs = 0;
        for (int i = 0; i < marker.length(); i++) {
            char glyph = marker.charAt(i);
            if ((glyph >= '▀' && glyph <= '▟') || glyph == '■' || glyph == '▪') {
                if (++blockGlyphs >= 2) return true;
            } else if (!Character.isWhitespace(glyph)) {
                blockGlyphs = 0;
            }
        }
        // Stars/underscores are explicit mask runs even next to readable text.
        // Ellipses/dashes can mark omitted material when a readable fragment
        // accompanies them. A truly punctuation-only rival retains the
        // established benign behavior via hasOnlyKnownDiscardedLexemes().
        boolean readable = false, ellipsisOrDashes = false;
        for (int i = 0; i < marker.length();) {
            char ch = marker.charAt(i);
            if (Character.isLetterOrDigit(ch)) readable = true;
            if (ch != '*' && ch != '_' && ch != '.' && ch != '-') {
                i++;
                continue;
            }
            int end = i + 1;
            while (end < marker.length() && marker.charAt(end) == ch) end++;
            if (end - i >= 3) {
                if (ch == '*' || ch == '_') return true;
                ellipsisOrDashes = true;
            }
            i = end;
        }
        return readable && ellipsisOrDashes;
    }

    // This is ONLY a correction-target abstention exception, never a recall
    // authorization rule. Use an intentionally small, inspectable alphabet;
    // Unicode-only content (including emoji) cannot silently be called irrelevant.
    // An empty/redacted summary and invalid/over-budget text remain UNKNOWN.
    private static boolean hasOnlyKnownDiscardedLexemes(String summary) {
        if (summary == null || summary.isEmpty() || summary.length() > MAX_INPUT
                || !wellFormedUtf16(summary) || hasNonTextControl(summary)
                || hasUnrepresentableLexicalChars(summary)) return false;
        // A whole summary of repeated masking glyphs can mean redacted G1
        // evidence, not a genuinely irrelevant punctuation-only memory.
        // Abstain from automatic correction-ID selection for that live rival.
        // Splitting a masked projection into two or more whitespace-separated
        // mask runs must not turn it into harmless punctuation. Keep mixed
        // ordinary punctuation sentences (e.g. "?! --- ... ;:") distinct.
        String[] tokens = summary.trim().split("\\s+");
        boolean allMaskTokens = tokens.length > 0;
        for (String token : tokens) {
            if (!token.matches("(?:[.]{3,}|[*]{3,}|[-]{3,}|[_]{3,})")) {
                allMaskTokens = false;
                break;
            }
        }
        if (allMaskTokens) return false;
        // Some sanitizer projections remove spaces around the redacted span.
        // For example `und***oder` is NOT an intact German stopword phrase.
        // Never normalize a >=3-character masking run adjacent to readable
        // letters/digits into a separator, then auto-pick another memory ID.
        // Pure punctuation (`?! --- ... ;:`) remains a known-empty non-rival.
        for (int i = 0; i < summary.length();) {
            char ch = summary.charAt(i);
            if (ch != '*' && ch != '_' && ch != '.' && ch != '-') {
                i++;
                continue;
            }
            int end = i + 1;
            while (end < summary.length() && summary.charAt(end) == ch) end++;
            if (end - i >= 3 && (ch == '*' || ch == '_'
                    || (i > 0 && Character.isLetterOrDigit(summary.charAt(i - 1)))
                    || (end < summary.length() && Character.isLetterOrDigit(summary.charAt(end)))))
                return false;
            i = end;
        }
        // A masked word can be surrounded by ordinary stopwords or punctuation.
        // Dropping those terms would otherwise make a hidden rival appear
        // harmless and could wrongly select an unrelated visible memory ID.
        // Only distinct whole-token redaction glyphs are treated as masks;
        // ordinary embedded punctuation and the legacy "?! --- ... ;:"
        // contract remain unaffected.
        boolean wordAdjacentEllipsisOrDashes = false;
        for (String token : tokens) {
            if (token.matches("(?:\\*{3,}|_{3,})")) return false;
            if (token.matches("(?:[.]{3,}|-{3,})"))
                wordAdjacentEllipsisOrDashes = true;
        }
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
        // Standalone ellipsis/dash runs surrounded by readable words can
        // represent withheld G1 content, despite all those words being stopwords.
        // A purely punctuation-only summary remains known-empty and benign.
        if (wordAdjacentEllipsisOrDashes && !value.isEmpty()) return false;
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
        // These are polarity cues, NEVER independent topical recall/correction anchors.
        "kein","keine","keinen","keinem","keiner","keines","nie","niemals","ohne",
        "noch","oder","sie","so","und","von","war","was","wie","wir","wird",
        "zu","zum","zur","bitte","kannst","koenntest","erinnerst"));
    private static final Map<String,String> ALIASES = new HashMap<>();
    static {
        for (String w : Arrays.asList("name","heisse","heisst","hiess")) ALIASES.put(w,"name");
        for (String w : Arrays.asList("lieblingsgetraenk","getraenk","getraenke","getraenken"))
            ALIASES.put(w,"getraenk");
        for (String w : Arrays.asList("bevorzuge","bevorzugen","lieber","mag"))
            ALIASES.put(w,"praeferenz");
        ALIASES.put("exakt", "genau"); // precise numerical fact, not a range
    }
    /**
     * POST-v83 ONLY. A separately administered append-only claim set must make
     * a durable global reservation BEFORE the epoch-specific provider acts.
     * This interface deliberately does not accept a caller-supplied ledger
     * snapshot, reset flag, or epoch rotation command. It is NOT an implementation
     * of external custody: a local mock and a caller-chosen sixth key remain
     * rollbackable and cannot be used for production security approval.
     */
    public interface IndependentGlobalClaimSetAuthority {
        SignedGlobalClaimDecision reserveOnce(String authorityId, long providerEpoch,
                String claimId, String exactId, long sourceRevision,
                byte[] ownerSignature, String freshNonce) throws Exception;
        /** Absolutely read-only; a lost reserve ACK never permits a retry. */
        SignedGlobalClaimDecision queryExisting(String authorityId, long providerEpoch,
                String claimId, String exactId, long sourceRevision,
                byte[] ownerSignature, String freshNonce) throws Exception;
    }

    public enum GlobalClaimGateOutcome {
        NO_AUTHORITY, UNVERIFIABLE, STARTED_INDETERMINATE,
        FIRST_RESERVED_PENDING_ACK, ALREADY_RESERVED_NO_RETRY,
        REMAIN_INDETERMINATE
    }

    public static final class SignedGlobalClaimDecision {
        public final String authorityId, claimId, exactId, freshNonce, operation, state;
        public final long providerEpoch, sourceRevision, sequence;
        private final byte[] signature;
        public SignedGlobalClaimDecision(String authorityId, long providerEpoch,
                String claimId, String exactId, long sourceRevision,
                String freshNonce, String operation, String state,
                long sequence, byte[] signature) {
            this.authorityId = authorityId;
            this.providerEpoch = providerEpoch;
            this.claimId = claimId;
            this.exactId = exactId;
            this.sourceRevision = sourceRevision;
            this.freshNonce = freshNonce;
            this.operation = operation;
            this.state = state;
            this.sequence = sequence;
            this.signature = signature == null ? null : signature.clone();
        }
        public byte[] signature() {
            return signature == null ? null : signature.clone();
        }
    }

    /**
     * Domain-separated sixth-party proof. The claim key intentionally omits
     * providerEpoch in the EXTERNAL authority's durable index: the same claim
     * must stay reserved across provider rotations. Epoch is included in each
     * reply signature so old replies cannot be replayed at a new gateway.
     */
    public static byte[] globalClaimDecisionPayload(String authorityId,
            long providerEpoch, String claimId, String exactId, long revision,
            byte[] ownerSignature, String nonce, String operation,
            String state, long sequence) {
        if (authorityId == null || !authorityId.matches("[A-Za-z0-9_-]{8,96}")
                || providerEpoch <= 0 || claimId == null
                || !claimId.matches("[A-Za-z0-9_-]{8,128}")
                || exactId == null || exactId.isBlank() || exactId.length() > 256
                || revision <= 0 || ownerSignature == null || ownerSignature.length != 64
                || nonce == null || !nonce.matches("[A-Za-z0-9_-]{24,128}")
                || !("RESERVE".equals(operation) || "QUERY".equals(operation))
                || !("FIRST_COMMITTED".equals(state)
                    || "ALREADY_COMMITTED".equals(state)
                    || "COMMITTED".equals(state) || "ABSENT".equals(state))
                || sequence < 0 || (sequence == 0 && !"ABSENT".equals(state)))
            return new byte[0];
        return ("future-g1/global-monotonic-claim-set/v1\n" + authorityId
                + "\n" + providerEpoch + "\n" + claimId + "\n"
                + java.util.Base64.getEncoder().encodeToString(exactId.getBytes(StandardCharsets.UTF_8))
                + "\n" + revision + "\n"
                + java.util.Base64.getEncoder().encodeToString(ownerSignature)
                + "\n" + nonce + "\n" + operation + "\n" + state
                + "\n" + sequence + "\n").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * FUTURE-ONLY read-only live high-water authority. The implementation must
     * live outside the app/provider snapshot, and must NOT be supplied by the
     * caller as a resettable local preference. This is a testable protocol,
     * NOT evidence that such a production service has been provisioned.
     */
    public interface IndependentGlobalFloorWitnessAuthority {
        SignedGlobalFloorWitness readMinimum(String authorityId,
                String freshChallenge) throws Exception;
    }

    public static final class SignedGlobalFloorWitness {
        public final String authorityId, challenge;
        public final long minimumSequence;
        private final byte[] signature;
        public SignedGlobalFloorWitness(String authorityId, String challenge,
                long minimumSequence, byte[] signature) {
            this.authorityId = authorityId;
            this.challenge = challenge;
            this.minimumSequence = minimumSequence;
            this.signature = signature == null ? null : signature.clone();
        }
        public byte[] signature() {
            return signature == null ? null : signature.clone();
        }
    }

    /** The witness can never be reused as a reserve/claim signature. */
    public static byte[] globalFloorWitnessPayload(String authorityId,
            String challenge, long minimumSequence) {
        if (authorityId == null || !authorityId.matches("[A-Za-z0-9_-]{8,96}")
                || challenge == null || !challenge.matches("[A-Za-z0-9_-]{24,128}")
                || minimumSequence < 0) return new byte[0];
        return ("future-g1/independent-global-floor-witness/v1\n" + authorityId
                + "\n" + challenge + "\n" + minimumSequence + "\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Re-reads and verifies an independent signed floor for EACH operation;
     * never accepts a caller-restored numeric floor as a production proof.
     * A disconnected, forged or replayed witness stops BEFORE global CAS and
     * BEFORE the epoch provider. Even this cannot detect a simultaneous total
     * rollback of the external ledger AND its minimum with the original keys.
     */
    public static final class ExternallyWitnessedGlobalClaimGate {
        private final ProvisionedOneShotGateway provider;
        private final IndependentGlobalClaimSetAuthority claimSet;
        private final IndependentGlobalFloorWitnessAuthority witness;
        private final byte[] pin;
        private final java.security.SecureRandom random = new java.security.SecureRandom();
        // Session-only defense-in-depth. It cannot substitute for the signed,
        // separately retained nonrollbackable floor after an app/process restart.
        private final java.util.concurrent.atomic.AtomicLong observedMinimum =
                new java.util.concurrent.atomic.AtomicLong(-1L);

        private ExternallyWitnessedGlobalClaimGate(ProvisionedOneShotGateway provider,
                IndependentGlobalClaimSetAuthority claimSet,
                IndependentGlobalFloorWitnessAuthority witness, byte[] pin) {
            this.provider = provider;
            this.claimSet = claimSet;
            this.witness = witness;
            this.pin = pin.clone();
        }

        public static ExternallyWitnessedGlobalClaimGate bind(
                ProvisionedOneShotGateway provider,
                IndependentGlobalClaimSetAuthority preprovisionedClaimSet,
                IndependentGlobalFloorWitnessAuthority preprovisionedWitness,
                byte[] externallyPinnedSixthIdentity) {
            if (preprovisionedWitness == null
                    || ProvisionedGlobalClaimGate.bindFromExternalAdministrator(
                            provider, preprovisionedClaimSet,
                            externallyPinnedSixthIdentity, 0) == null) return null;
            return new ExternallyWitnessedGlobalClaimGate(provider,
                    preprovisionedClaimSet, preprovisionedWitness,
                    externallyPinnedSixthIdentity);
        }

        private ProvisionedGlobalClaimGate verifyLiveFloor() {
            byte[] nonce = new byte[24];
            random.nextBytes(nonce);
            String challenge = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(nonce);
            try {
                SignedGlobalFloorWitness reply = witness.readMinimum(
                        provider.authorityId, challenge);
                if (reply == null || reply.signature() == null
                        || reply.signature().length != 64
                        || !provider.authorityId.equals(reply.authorityId)
                        || !challenge.equals(reply.challenge)) return null;
                byte[] payload = globalFloorWitnessPayload(reply.authorityId,
                        challenge, reply.minimumSequence);
                if (payload.length == 0) return null;
                java.security.Signature verifier = java.security.Signature
                        .getInstance("Ed25519");
                verifier.initVerify(java.security.KeyFactory.getInstance("Ed25519")
                        .generatePublic(new java.security.spec.X509EncodedKeySpec(pin)));
                verifier.update(payload);
                if (!verifier.verify(reply.signature())) return null;
                ProvisionedGlobalClaimGate authenticated =
                        ProvisionedGlobalClaimGate.bindFromExternalAdministrator(
                                provider, claimSet, pin, reply.minimumSequence);
                if (authenticated == null) return null;
                // A correctly signed but decreasing floor indicates rollback
                // or a split-brain authority. Reject it before ANY CAS/query.
                // Compare-and-set prevents concurrent calls from accepting a
                // stale lower floor after a newer one was observed.
                while (true) {
                    long previous = observedMinimum.get();
                    if (reply.minimumSequence < previous) return null;
                    if (observedMinimum.compareAndSet(previous,
                            reply.minimumSequence)) break;
                }
                return authenticated;
            } catch (Exception unavailableOrForged) {
                return null;
            }
        }

        public GlobalClaimGateOutcome reserveOnce(String claim, String exactId,
                long revision, byte[] ownerSignature, String freshNonce) {
            // An invalid owner claim must never trigger a witness/provider call.
            if (!provider.validOwnerClaim(claim, exactId, revision, ownerSignature)
                    || freshNonce == null
                    || !freshNonce.matches("[A-Za-z0-9_-]{24,128}"))
                return GlobalClaimGateOutcome.UNVERIFIABLE;
            ProvisionedGlobalClaimGate live = verifyLiveFloor();
            return live == null ? GlobalClaimGateOutcome.NO_AUTHORITY
                    : live.reserveOnce(claim, exactId, revision,
                            ownerSignature, freshNonce);
        }

        public GlobalClaimGateOutcome queryExisting(String claim, String exactId,
                long revision, byte[] ownerSignature, String freshNonce) {
            if (!provider.validOwnerClaim(claim, exactId, revision, ownerSignature)
                    || freshNonce == null
                    || !freshNonce.matches("[A-Za-z0-9_-]{24,128}"))
                return GlobalClaimGateOutcome.UNVERIFIABLE;
            ProvisionedGlobalClaimGate live = verifyLiveFloor();
            return live == null ? GlobalClaimGateOutcome.NO_AUTHORITY
                    : live.queryExisting(claim, exactId, revision,
                            ownerSignature, freshNonce);
        }
    }

    /**
     * FUTURE ONLY: an independently custodied *per-claim* spent register. The
     * register must atomically retain each claim ID before any global or epoch
     * provider is allowed to act. A numeric global high-water mark is not
     * sufficient: selective deletion can preserve the same numeric minimum.
     *
     * The caller cannot supply a ledger image, reset command or replacement
     * signing key through this interface. Java callbacks and a test fixture do
     * NOT prove independent, rollback-resistant production custody.
     */
    public interface IndependentPerClaimSpentAuthority {
        SignedPerClaimSpentReceipt spendOnce(String authorityId, long providerEpoch,
                String claimId, String exactId, long sourceRevision,
                byte[] ownerSignature, String freshNonce) throws Exception;
        /** Read-only: after a lost spend acknowledgement NEVER call spend again. */
        SignedPerClaimSpentReceipt queryExisting(String authorityId, long providerEpoch,
                String claimId, String exactId, long sourceRevision,
                byte[] ownerSignature, String freshNonce) throws Exception;
    }

    public static final class SignedPerClaimSpentReceipt {
        public final String authorityId, claimId, exactId, freshNonce, operation, state;
        public final long providerEpoch, sourceRevision, sequence;
        private final byte[] signature;
        public SignedPerClaimSpentReceipt(String authorityId, long providerEpoch,
                String claimId, String exactId, long sourceRevision,
                String freshNonce, String operation, String state,
                long sequence, byte[] signature) {
            this.authorityId = authorityId; this.providerEpoch = providerEpoch;
            this.claimId = claimId; this.exactId = exactId;
            this.sourceRevision = sourceRevision; this.freshNonce = freshNonce;
            this.operation = operation; this.state = state;
            this.sequence = sequence;
            this.signature = signature == null ? null : signature.clone();
        }
        public byte[] signature() { return signature == null ? null : signature.clone(); }
    }

    /** Domain separated from all existing global, floor and provider proofs. */
    public static byte[] perClaimSpentReceiptPayload(String authorityId,
            long providerEpoch, String claimId, String exactId, long revision,
            byte[] ownerSignature, String nonce, String operation,
            String state, long sequence) {
        if (authorityId == null || !authorityId.matches("[A-Za-z0-9_-]{8,96}")
                || providerEpoch <= 0 || claimId == null
                || !claimId.matches("[A-Za-z0-9_-]{8,128}")
                || exactId == null || exactId.isBlank() || exactId.length() > 256
                || revision <= 0 || ownerSignature == null || ownerSignature.length != 64
                || nonce == null || !nonce.matches("[A-Za-z0-9_-]{24,128}")
                || !("SPEND".equals(operation) || "QUERY".equals(operation))
                || !("FIRST_SPENT".equals(state) || "ALREADY_SPENT".equals(state)
                    || "SPENT".equals(state) || "ABSENT".equals(state))
                || sequence < 0 || (sequence == 0 && !"ABSENT".equals(state)))
            return new byte[0];
        return ("future-g1/independent-per-claim-spent/v1\n" + authorityId
                + "\n" + providerEpoch + "\n" + claimId + "\n"
                + java.util.Base64.getEncoder().encodeToString(exactId.getBytes(StandardCharsets.UTF_8))
                + "\n" + revision + "\n"
                + java.util.Base64.getEncoder().encodeToString(ownerSignature)
                + "\n" + nonce + "\n" + operation + "\n" + state
                + "\n" + sequence + "\n").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Conditional defense for the known selective global-ledger rollback.
     * The seventh identity and spent register MUST be provisioned independently
     * of the provider, sixth-key global ledger, app and restored snapshots.
     * A first spent receipt is recorded BEFORE calling the older global gate.
     * If any later operation fails, the claim stays indeterminate and cannot
     * be retried automatically. A reset of ALL independent authorities still
     * defeats this prototype; no production guarantee is implied.
     */
    public static final class PerClaimSpentWitnessGate {
        private final ExternallyWitnessedGlobalClaimGate global;
        private final IndependentPerClaimSpentAuthority spent;
        private final byte[] pin;
        private PerClaimSpentWitnessGate(ExternallyWitnessedGlobalClaimGate global,
                IndependentPerClaimSpentAuthority spent, byte[] pin) {
            this.global = global; this.spent = spent; this.pin = pin.clone();
        }
        public static PerClaimSpentWitnessGate bind(
                ExternallyWitnessedGlobalClaimGate global,
                IndependentPerClaimSpentAuthority externallyProvisionedSpent,
                byte[] externallyPinnedSeventhIdentity) {
            if (global == null || externallyProvisionedSpent == null
                    || externallyPinnedSeventhIdentity == null) return null;
            try {
                byte[] pin = externallyPinnedSeventhIdentity.clone();
                java.security.PublicKey key = java.security.KeyFactory.getInstance("Ed25519")
                        .generatePublic(new java.security.spec.X509EncodedKeySpec(pin));
                if (!java.util.Arrays.equals(pin, key.getEncoded())
                        || java.util.Arrays.equals(pin, global.pin)) return null;
                for (byte[] existing : global.provider.pins)
                    if (java.util.Arrays.equals(pin, existing)) return null;
                return new PerClaimSpentWitnessGate(global, externallyProvisionedSpent, pin);
            } catch (GeneralSecurityException | RuntimeException invalid) {
                return null;
            }
        }
        private boolean validInput(String claim, String id, long revision,
                byte[] ownerSignature, String nonce) {
            return global.provider.validOwnerClaim(claim, id, revision, ownerSignature)
                    && nonce != null && nonce.matches("[A-Za-z0-9_-]{24,128}");
        }
        private String checkedState(SignedPerClaimSpentReceipt reply,
                String operation, String claim, String id, long revision,
                byte[] ownerSignature, String nonce) {
            if (reply == null || !java.util.Objects.equals(reply.authorityId,
                        global.provider.authorityId)
                    || reply.providerEpoch != global.provider.epoch
                    || !java.util.Objects.equals(reply.claimId, claim)
                    || !java.util.Objects.equals(reply.exactId, id)
                    || reply.sourceRevision != revision
                    || !java.util.Objects.equals(reply.freshNonce, nonce)
                    || !java.util.Objects.equals(reply.operation, operation)
                    || reply.signature() == null || reply.signature().length != 64
                    || ("SPEND".equals(operation)
                        && !("FIRST_SPENT".equals(reply.state)
                            || "ALREADY_SPENT".equals(reply.state)))
                    || ("QUERY".equals(operation)
                        && !("SPENT".equals(reply.state)
                            || "ABSENT".equals(reply.state)))) return null;
            byte[] payload = perClaimSpentReceiptPayload(reply.authorityId,
                    reply.providerEpoch, claim, id, revision, ownerSignature,
                    nonce, operation, reply.state, reply.sequence);
            if (payload.length == 0) return null;
            try {
                java.security.Signature verifier = java.security.Signature.getInstance("Ed25519");
                verifier.initVerify(java.security.KeyFactory.getInstance("Ed25519")
                        .generatePublic(new java.security.spec.X509EncodedKeySpec(pin)));
                verifier.update(payload);
                return verifier.verify(reply.signature()) ? reply.state : null;
            } catch (GeneralSecurityException | RuntimeException invalid) {
                return null;
            }
        }
        /**
         * A signed seventh-key SPENT receipt means only that the pre-commit
         * fence fired. It does NOT prove the later sixth-key global CAS or
         * original epoch-provider reservation completed. Recover strictly by
         * independently signed read-only checks at BOTH later stages.
         * Neither an ABSENT result nor a disconnected authority permits retry.
         * This still cannot detect a complete same-key rollback of all stores.
         */
        private GlobalClaimGateOutcome confirmedReservationReadOnly(
                String claim, String id, long revision, byte[] ownerSignature,
                String freshNonce) {
            GlobalClaimGateOutcome globalStatus = global.queryExisting(
                    claim, id, revision, ownerSignature, freshNonce);
            if (globalStatus != GlobalClaimGateOutcome.ALREADY_RESERVED_NO_RETRY)
                return GlobalClaimGateOutcome.REMAIN_INDETERMINATE;
            MonotonicQueryOutcome providerStatus = global.provider.queryExisting(
                    claim, id, revision, ownerSignature, freshNonce, 0);
            return providerStatus == MonotonicQueryOutcome.ALREADY_RESERVED_NO_RETRY
                    ? GlobalClaimGateOutcome.ALREADY_RESERVED_NO_RETRY
                    : GlobalClaimGateOutcome.REMAIN_INDETERMINATE;
        }

        public GlobalClaimGateOutcome reserveOnce(String claim, String id, long revision,
                byte[] ownerSignature, String freshNonce) {
            if (!validInput(claim, id, revision, ownerSignature, freshNonce))
                return GlobalClaimGateOutcome.UNVERIFIABLE;
            final SignedPerClaimSpentReceipt receipt;
            try {
                receipt = spent.spendOnce(global.provider.authorityId,
                        global.provider.epoch, claim, id, revision,
                        ownerSignature.clone(), freshNonce);
            } catch (Exception uncertain) {
                // The independent atomic write might already have happened.
                return GlobalClaimGateOutcome.STARTED_INDETERMINATE;
            }
            String state = checkedState(receipt, "SPEND", claim, id, revision,
                    ownerSignature, freshNonce);
            if (state == null) return GlobalClaimGateOutcome.STARTED_INDETERMINATE;
            if ("ALREADY_SPENT".equals(state)) {
                // No write/retry after a spent fence. Only a verified global
                // AND provider status can assert that reservation completed.
                GlobalClaimGateOutcome recovered = confirmedReservationReadOnly(
                        claim, id, revision, ownerSignature, freshNonce);
                return recovered == GlobalClaimGateOutcome.ALREADY_RESERVED_NO_RETRY
                        ? recovered : GlobalClaimGateOutcome.STARTED_INDETERMINATE;
            }
            GlobalClaimGateOutcome outcome = global.reserveOnce(claim, id, revision,
                    ownerSignature, freshNonce);
            return outcome == GlobalClaimGateOutcome.FIRST_RESERVED_PENDING_ACK
                    || outcome == GlobalClaimGateOutcome.ALREADY_RESERVED_NO_RETRY
                    ? outcome : GlobalClaimGateOutcome.STARTED_INDETERMINATE;
        }
        /** Read-only; an ABSENT reply never authorizes a new reservation. */
        public GlobalClaimGateOutcome queryExisting(String claim, String id, long revision,
                byte[] ownerSignature, String freshNonce) {
            if (!validInput(claim, id, revision, ownerSignature, freshNonce))
                return GlobalClaimGateOutcome.UNVERIFIABLE;
            final SignedPerClaimSpentReceipt receipt;
            try {
                receipt = spent.queryExisting(global.provider.authorityId,
                        global.provider.epoch, claim, id, revision,
                        ownerSignature.clone(), freshNonce);
            } catch (Exception unavailable) {
                return GlobalClaimGateOutcome.REMAIN_INDETERMINATE;
            }
            String state = checkedState(receipt, "QUERY", claim, id, revision,
                    ownerSignature, freshNonce);
            return "SPENT".equals(state) ? confirmedReservationReadOnly(
                    claim, id, revision, ownerSignature, freshNonce)
                    : GlobalClaimGateOutcome.REMAIN_INDETERMINATE;
        }
    }

    /**
     * POST-v83 ONLY — external per-claim atomic-CAS service boundary.
     * The private identity and per-claim history MUST be controlled by a
     * separate administrator outside the seven earlier signers, caller,
     * application backups, and disaster-recovery images. No implementation
     * of this service, hardware trust, or remote transport is supplied here.
     * Merely implementing this Java interface with another filesystem mock
     * does NOT make state nonrollbackable or establish production readiness.
     *
     * A real operator must atomically and durably commit a claim BEFORE it
     * signs FIRST_SPENT; restoration must preserve that claim or stay in
     * RECOVERY_REQUIRED until independent continuity is established. It must
     * never issue a fresh READY attestation after restoring an older image.
     */
    public interface IndependentExternalClaimCAS {
        SignedExternalCASAttestation attest(String authorityId,
                long providerEpoch, String freshChallenge) throws Exception;
        SignedExternalCASReceipt spendOnce(String authorityId, long providerEpoch,
                String claimId, String exactId, long sourceRevision,
                byte[] ownerSignature, String freshNonce) throws Exception;
        /** Read-only; ABSENT is never a grant to retry a failed spend. */
        SignedExternalCASReceipt queryExisting(String authorityId, long providerEpoch,
                String claimId, String exactId, long sourceRevision,
                byte[] ownerSignature, String freshNonce) throws Exception;
    }

    public static final class SignedExternalCASAttestation {
        public final String authorityId, challenge, state;
        public final long providerEpoch, recoveryGeneration, retainedMinimumSequence;
        private final byte[] signature;
        public SignedExternalCASAttestation(String authorityId, long providerEpoch,
                long recoveryGeneration, String challenge, long retainedMinimumSequence,
                String state, byte[] signature) {
            this.authorityId = authorityId; this.providerEpoch = providerEpoch;
            this.recoveryGeneration = recoveryGeneration;
            this.challenge = challenge; this.retainedMinimumSequence = retainedMinimumSequence;
            this.state = state;
            this.signature = signature == null ? null : signature.clone();
        }
        public byte[] signature() { return signature == null ? null : signature.clone(); }
    }

    public static final class SignedExternalCASReceipt {
        public final String authorityId, claimId, exactId, freshNonce, operation, state;
        public final long providerEpoch, recoveryGeneration, sourceRevision, sequence;
        private final byte[] signature;
        public SignedExternalCASReceipt(String authorityId, long providerEpoch,
                long recoveryGeneration, String claimId, String exactId,
                long sourceRevision, String freshNonce, String operation,
                String state, long sequence, byte[] signature) {
            this.authorityId = authorityId; this.providerEpoch = providerEpoch;
            this.recoveryGeneration = recoveryGeneration;
            this.claimId = claimId; this.exactId = exactId;
            this.sourceRevision = sourceRevision; this.freshNonce = freshNonce;
            this.operation = operation; this.state = state; this.sequence = sequence;
            this.signature = signature == null ? null : signature.clone();
        }
        public byte[] signature() { return signature == null ? null : signature.clone(); }
    }

    public static byte[] externalCASAttestationPayload(String authorityId,
            long providerEpoch, long recoveryGeneration, String challenge,
            long retainedMinimumSequence, String state) {
        if (authorityId == null || !authorityId.matches("[A-Za-z0-9_-]{8,96}")
                || providerEpoch <= 0 || recoveryGeneration <= 0
                || challenge == null || !challenge.matches("[A-Za-z0-9_-]{24,128}")
                || retainedMinimumSequence < 0
                || !("READY".equals(state) || "RECOVERY_REQUIRED".equals(state)))
            return new byte[0];
        return ("future-g1/administered-external-claim-cas/attest-v1\n"
                + authorityId + "\n" + providerEpoch + "\n" + recoveryGeneration
                + "\n" + challenge + "\n" + retainedMinimumSequence + "\n"
                + state + "\n").getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] externalCASReceiptPayload(String authorityId,
            long providerEpoch, long recoveryGeneration, String claimId,
            String exactId, long revision, byte[] ownerSignature,
            String nonce, String operation, String state, long sequence) {
        if (authorityId == null || !authorityId.matches("[A-Za-z0-9_-]{8,96}")
                || providerEpoch <= 0 || recoveryGeneration <= 0
                || claimId == null || !claimId.matches("[A-Za-z0-9_-]{8,128}")
                || exactId == null || exactId.isBlank() || exactId.length() > 256
                || revision <= 0 || ownerSignature == null || ownerSignature.length != 64
                || nonce == null || !nonce.matches("[A-Za-z0-9_-]{24,128}")
                || !("SPEND".equals(operation) || "QUERY".equals(operation))
                || !("SPEND".equals(operation)
                       ? ("FIRST_SPENT".equals(state) || "ALREADY_SPENT".equals(state))
                       : ("SPENT".equals(state) || "ABSENT".equals(state)))
                || sequence < 0 || (sequence == 0 && !"ABSENT".equals(state)))
            return new byte[0];
        return ("future-g1/administered-external-claim-cas/receipt-v1\n"
                + authorityId + "\n" + providerEpoch + "\n" + recoveryGeneration
                + "\n" + claimId + "\n"
                + java.util.Base64.getEncoder().encodeToString(
                        exactId.getBytes(StandardCharsets.UTF_8))
                + "\n" + revision + "\n"
                + java.util.Base64.getEncoder().encodeToString(ownerSignature)
                + "\n" + nonce + "\n" + operation + "\n" + state
                + "\n" + sequence + "\n").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Strict client-side contract for an independently administered CAS.
     * This wraps the existing seventh-key no-retry gate: the remote operator
     * must commit FIRST before ANY prior mock/epoch provider writes. A remote
     * ALREADY_SPENT is checked only with downstream read-only queries and
     * cannot be confused with completed multi-stage reservation.
     *
     * Pins, generation and minimum are an OUT-OF-BAND admin provision, not a
     * runtime argument chosen by an end user or restored from an app snapshot.
     * A changed generation/disaster incident REQUIRES manual re-provisioning.
     * The Java contract cannot independently prove deployment/custody.
     */
    public static final class ExternallyAnchoredClaimGate {
        private final PerClaimSpentWitnessGate downstream;
        private final IndependentExternalClaimCAS external;
        private final byte[] pin;
        private final String authorityId;
        private final long providerEpoch, recoveryGeneration, pinnedMinimumSequence;
        private final java.util.concurrent.atomic.AtomicLong observedMinimum;
        private final java.util.Set<String> spentAttempts =
                java.util.concurrent.ConcurrentHashMap.newKeySet();
        // Runtime-only continuity observation. An authoritative SPENT may not
        // later become ABSENT in the same bound session, even with a higher
        // correctly signed numeric floor. This does NOT survive a new process;
        // production requires an independent immutable per-claim audit root.
        private final java.util.Set<String> observedSpentClaims =
                java.util.concurrent.ConcurrentHashMap.newKeySet();
        private final java.util.concurrent.atomic.AtomicBoolean quarantined =
                new java.util.concurrent.atomic.AtomicBoolean(false);

        private ExternallyAnchoredClaimGate(PerClaimSpentWitnessGate downstream,
                IndependentExternalClaimCAS external, byte[] pin, String authorityId,
                long providerEpoch, long recoveryGeneration, long pinnedMinimumSequence) {
            this.downstream = downstream; this.external = external; this.pin = pin.clone();
            this.authorityId = authorityId; this.providerEpoch = providerEpoch;
            this.recoveryGeneration = recoveryGeneration;
            this.pinnedMinimumSequence = pinnedMinimumSequence;
            this.observedMinimum = new java.util.concurrent.atomic.AtomicLong(pinnedMinimumSequence);
        }
        public static ExternallyAnchoredClaimGate bind(
                PerClaimSpentWitnessGate downstream,
                IndependentExternalClaimCAS separatelyAdministeredRemote,
                byte[] externallyPinnedOperatorIdentity,
                String expectedAuthorityId, long providerEpoch,
                long outOfBandRecoveryGeneration, long outOfBandMinimum) {
            if (downstream == null || separatelyAdministeredRemote == null
                    || externallyPinnedOperatorIdentity == null
                    || expectedAuthorityId == null
                    || !expectedAuthorityId.equals(downstream.global.provider.authorityId)
                    || providerEpoch != downstream.global.provider.epoch
                    || outOfBandRecoveryGeneration <= 0 || outOfBandMinimum < 0)
                return null;
            try {
                byte[] pin = externallyPinnedOperatorIdentity.clone();
                java.security.PublicKey k = java.security.KeyFactory.getInstance("Ed25519")
                        .generatePublic(new java.security.spec.X509EncodedKeySpec(pin));
                if (!java.util.Arrays.equals(pin, k.getEncoded())
                        || java.util.Arrays.equals(pin, downstream.pin)
                        || java.util.Arrays.equals(pin, downstream.global.pin)) return null;
                for (byte[] old : downstream.global.provider.pins)
                    if (java.util.Arrays.equals(pin, old)) return null;
                return new ExternallyAnchoredClaimGate(downstream,
                        separatelyAdministeredRemote, pin, expectedAuthorityId,
                        providerEpoch, outOfBandRecoveryGeneration, outOfBandMinimum);
            } catch (GeneralSecurityException | RuntimeException malformedPin) {
                return null;
            }
        }
        private boolean signed(byte[] payload, byte[] signature) {
            if (payload.length == 0 || signature == null || signature.length != 64) return false;
            try {
                java.security.Signature verifier = java.security.Signature.getInstance("Ed25519");
                verifier.initVerify(java.security.KeyFactory.getInstance("Ed25519")
                        .generatePublic(new java.security.spec.X509EncodedKeySpec(pin)));
                verifier.update(payload);
                return verifier.verify(signature);
            } catch (GeneralSecurityException | RuntimeException invalid) {
                return false;
            }
        }
        private boolean liveAttested() {
            if (quarantined.get()) return false;
            byte[] nonce = new byte[24];
            new java.security.SecureRandom().nextBytes(nonce);
            String challenge = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(nonce);
            try {
                SignedExternalCASAttestation proof = external.attest(
                        authorityId, providerEpoch, challenge);
                if (proof == null || !authorityId.equals(proof.authorityId)
                        || proof.providerEpoch != providerEpoch
                        || proof.recoveryGeneration != recoveryGeneration
                        || !challenge.equals(proof.challenge)
                        || !"READY".equals(proof.state)
                        || !signed(externalCASAttestationPayload(authorityId,
                                providerEpoch, recoveryGeneration, challenge,
                                proof.retainedMinimumSequence, proof.state), proof.signature()))
                    return false;
                while (true) {
                    long old = observedMinimum.get();
                    if (proof.retainedMinimumSequence < old
                            || proof.retainedMinimumSequence < pinnedMinimumSequence) {
                        quarantined.set(true); // NO silent recovery after rollback.
                        return false;
                    }
                    if (observedMinimum.compareAndSet(old,
                            proof.retainedMinimumSequence)) return !quarantined.get();
                }
            } catch (Exception disconnected) {
                return false;
            }
        }
        private String checked(String operation, String claim, String exactId,
                long revision, byte[] owner, String nonce, SignedExternalCASReceipt receipt) {
            if (receipt == null || !authorityId.equals(receipt.authorityId)
                    || receipt.providerEpoch != providerEpoch
                    || receipt.recoveryGeneration != recoveryGeneration
                    || !claim.equals(receipt.claimId) || !exactId.equals(receipt.exactId)
                    || revision != receipt.sourceRevision
                    || !nonce.equals(receipt.freshNonce)
                    || !operation.equals(receipt.operation)) return null;
            return signed(externalCASReceiptPayload(authorityId, providerEpoch,
                    recoveryGeneration, claim, exactId, revision, owner, nonce,
                    operation, receipt.state, receipt.sequence), receipt.signature())
                    ? receipt.state : null;
        }
        private void observeSignedSpend(String claim, long sequence) {
            observedSpentClaims.add(claim);
            observedMinimum.accumulateAndGet(sequence, Math::max);
        }
        public GlobalClaimGateOutcome reserveOnce(String claim, String exactId,
                long revision, byte[] ownerSignature, String freshNonce) {
            if (!downstream.validInput(claim, exactId, revision,
                    ownerSignature, freshNonce)) return GlobalClaimGateOutcome.UNVERIFIABLE;
            if (!liveAttested()) return GlobalClaimGateOutcome.NO_AUTHORITY;
            final long floorBeforeRemoteSpend = observedMinimum.get();
            try {
                // A lost or forged spend ACK is NOT permission to send SPEND
                // again. First do an authenticated READ-ONLY membership check.
                // This query must not create or resurrect an external record.
                SignedExternalCASReceipt before = external.queryExisting(authorityId,
                        providerEpoch, claim, exactId, revision,
                        ownerSignature.clone(), freshNonce);
                String prior = checked("QUERY", claim, exactId, revision,
                        ownerSignature, freshNonce, before);
                if ("SPENT".equals(prior)) {
                    observeSignedSpend(claim, before.sequence);
                    return downstream.queryExisting(claim, exactId, revision,
                            ownerSignature, freshNonce)
                            == GlobalClaimGateOutcome.ALREADY_RESERVED_NO_RETRY
                            ? GlobalClaimGateOutcome.ALREADY_RESERVED_NO_RETRY
                            : GlobalClaimGateOutcome.STARTED_INDETERMINATE;
                }
                if ("ABSENT".equals(prior) && observedSpentClaims.contains(claim)) {
                    quarantined.set(true);
                    return GlobalClaimGateOutcome.STARTED_INDETERMINATE;
                }
                if (!"ABSENT".equals(prior) || !spentAttempts.add(claim))
                    return GlobalClaimGateOutcome.STARTED_INDETERMINATE;
                SignedExternalCASReceipt receipt = external.spendOnce(authorityId,
                        providerEpoch, claim, exactId, revision,
                        ownerSignature.clone(), freshNonce);
                String result = checked("SPEND", claim, exactId, revision,
                        ownerSignature, freshNonce, receipt);
                if ("FIRST_SPENT".equals(result)) {
                    // FIRST is only fresh if the signed receipt advances the
                    // verified pre-attempt floor. A conflicting signed response
                    // could describe a rolled-back operator; it MUST NOT
                    // authorize earlier downstream commits or automatic retry.
                    if (receipt.sequence <= floorBeforeRemoteSpend) {
                        quarantined.set(true);
                        return GlobalClaimGateOutcome.STARTED_INDETERMINATE;
                    }
                    observeSignedSpend(claim, receipt.sequence);
                    GlobalClaimGateOutcome downstreamStatus = downstream.reserveOnce(
                            claim, exactId, revision, ownerSignature, freshNonce);
                    if (downstreamStatus == GlobalClaimGateOutcome.ALREADY_RESERVED_NO_RETRY)
                        return downstream.queryExisting(claim, exactId, revision,
                                ownerSignature, freshNonce)
                                == GlobalClaimGateOutcome.ALREADY_RESERVED_NO_RETRY
                                ? GlobalClaimGateOutcome.ALREADY_RESERVED_NO_RETRY
                                : GlobalClaimGateOutcome.STARTED_INDETERMINATE;
                    return downstreamStatus == GlobalClaimGateOutcome.FIRST_RESERVED_PENDING_ACK
                            ? downstreamStatus : GlobalClaimGateOutcome.STARTED_INDETERMINATE;
                }
                if ("ALREADY_SPENT".equals(result)) {
                    observeSignedSpend(claim, receipt.sequence);
                    return downstream.queryExisting(claim, exactId, revision,
                            ownerSignature, freshNonce)
                            == GlobalClaimGateOutcome.ALREADY_RESERVED_NO_RETRY
                            ? GlobalClaimGateOutcome.ALREADY_RESERVED_NO_RETRY
                            : GlobalClaimGateOutcome.STARTED_INDETERMINATE;
                }
                return GlobalClaimGateOutcome.STARTED_INDETERMINATE;
            } catch (Exception maybeCommitted) {
                return GlobalClaimGateOutcome.STARTED_INDETERMINATE;
            }
        }
        public GlobalClaimGateOutcome queryExisting(String claim, String exactId,
                long revision, byte[] ownerSignature, String freshNonce) {
            if (!downstream.validInput(claim, exactId, revision,
                    ownerSignature, freshNonce)) return GlobalClaimGateOutcome.UNVERIFIABLE;
            if (!liveAttested()) return GlobalClaimGateOutcome.NO_AUTHORITY;
            try {
                SignedExternalCASReceipt receipt = external.queryExisting(authorityId,
                        providerEpoch, claim, exactId, revision,
                        ownerSignature.clone(), freshNonce);
                String status = checked("QUERY", claim, exactId, revision,
                        ownerSignature, freshNonce, receipt);
                if ("SPENT".equals(status)) {
                    observeSignedSpend(claim, receipt.sequence);
                    return downstream.queryExisting(claim, exactId, revision,
                            ownerSignature, freshNonce);
                }
                if ("ABSENT".equals(status) && observedSpentClaims.contains(claim))
                    quarantined.set(true);
                return GlobalClaimGateOutcome.REMAIN_INDETERMINATE;
            } catch (Exception disconnected) {
                return GlobalClaimGateOutcome.REMAIN_INDETERMINATE;
            }
        }
    }

    /**
     * FUTURE ONLY — separately administered, write-ahead per-claim audit seam.
     * The audit SEALS a claim before any existing CAS/provider continuation.
     * This is a *protocol*, not a storage implementation or proof of independent
     * immutable custody. A same-signer audit rollback remains an explicit FAIL.
     */
    public interface IndependentImmutableClaimAudit {
        SignedImmutableClaimAuditReply query(String authorityId, long epoch,
                String claim, String exactId, long revision, byte[] owner,
                String freshNonce) throws Exception;
        /** Must persist an immutable claim tombstone BEFORE signing FIRST_SEALED. */
        SignedImmutableClaimAuditReply sealOnce(String authorityId, long epoch,
                String claim, String exactId, long revision, byte[] owner,
                String freshNonce) throws Exception;
    }

    public static final class SignedImmutableClaimAuditReply {
        public final String authorityId, claim, exactId, freshNonce, operation, state;
        public final long epoch, generation, revision, sequence;
        private final byte[] signature;
        public SignedImmutableClaimAuditReply(String authorityId, long epoch,
                long generation, String claim, String exactId, long revision,
                String freshNonce, String operation, String state, long sequence,
                byte[] signature) {
            this.authorityId = authorityId; this.epoch = epoch;
            this.generation = generation; this.claim = claim;
            this.exactId = exactId; this.revision = revision;
            this.freshNonce = freshNonce; this.operation = operation;
            this.state = state; this.sequence = sequence;
            this.signature = signature == null ? null : signature.clone();
        }
        public byte[] signature() {
            return signature == null ? null : signature.clone();
        }
    }

    public static byte[] immutableClaimAuditPayload(String authorityId,
            long epoch, long generation, String claim, String exactId,
            long revision, byte[] owner, String freshNonce,
            String operation, String state, long sequence) {
        if (authorityId == null || !authorityId.matches("[A-Za-z0-9_-]{8,96}")
                || epoch <= 0 || generation <= 0
                || claim == null || !claim.matches("[A-Za-z0-9_-]{8,128}")
                || exactId == null || exactId.isBlank() || exactId.length() > 256
                || revision <= 0 || owner == null || owner.length != 64
                || freshNonce == null || !freshNonce.matches("[A-Za-z0-9_-]{24,128}")
                || !("QUERY".equals(operation) || "SEAL".equals(operation))
                || !("QUERY".equals(operation)
                    ? ("ABSENT".equals(state) || "SEALED".equals(state))
                    : ("FIRST_SEALED".equals(state) || "ALREADY_SEALED".equals(state)))
                || sequence < 0 || (sequence == 0 && !"ABSENT".equals(state)))
            return new byte[0];
        return ("future-g1/independent-immutable-claim-audit/v1\n"
                + authorityId + "\n" + epoch + "\n" + generation + "\n"
                + claim + "\n" + java.util.Base64.getEncoder().encodeToString(
                        exactId.getBytes(StandardCharsets.UTF_8)) + "\n"
                + revision + "\n" + java.util.Base64.getEncoder().encodeToString(owner)
                + "\n" + freshNonce + "\n" + operation + "\n" + state
                + "\n" + sequence + "\n").getBytes(StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    public interface ClaimAfterAuditSeal {
        GlobalClaimGateOutcome continueOnce(String claim, String exactId,
                long revision, byte[] ownerSignature, String freshNonce) throws Exception;
    }

    /**
     * A safety wrapper for a *separately provided* downstream CAS gate.
     * FIRST must be sealed in the independent audit before any downstream
     * mutation. An unknown ACK burns the attempt: never retry automatically.
     * A previously SEALED claim cannot be reauthorized by a selectively
     * erased downstream CAS after process restart, provided this audit truly
     * survived outside the restore domain. This condition is not proven here.
     */
    public static final class ImmutableAuditFirstGate {
        private final IndependentImmutableClaimAudit audit;
        private final ClaimAfterAuditSeal continuation;
        private final byte[] auditPin;
        private final String authorityId;
        private final long epoch, generation, externalMinimum;
        // Optional FUTURE-ONLY opt-in barrier. The old unanchored protocol stays
        // available only as a research baseline and is NOT production safe.
        private final ImmutableAuditCheckpointAdvanceGate rootAdvancement;
        private final java.util.Set<String> attempted =
                java.util.concurrent.ConcurrentHashMap.newKeySet();
        private final java.util.concurrent.atomic.AtomicBoolean quarantined =
                new java.util.concurrent.atomic.AtomicBoolean(false);

        private ImmutableAuditFirstGate(IndependentImmutableClaimAudit audit,
                ClaimAfterAuditSeal continuation, byte[] pin,
                String authorityId, long epoch, long generation, long minimum,
                ImmutableAuditCheckpointAdvanceGate rootAdvancement) {
            this.audit = audit; this.continuation = continuation;
            this.auditPin = pin.clone(); this.authorityId = authorityId;
            this.epoch = epoch; this.generation = generation;
            this.externalMinimum = minimum;
            this.rootAdvancement = rootAdvancement;
        }
        public static ImmutableAuditFirstGate bind(
                IndependentImmutableClaimAudit independentlyAdministeredAudit,
                ClaimAfterAuditSeal downstreamOnlyAfterSeal,
                byte[] pinnedAuditSigner, byte[] existingCasSigner,
                String authorityId, long epoch, long generation, long minimum) {
            if (independentlyAdministeredAudit == null || downstreamOnlyAfterSeal == null
                    || pinnedAuditSigner == null || existingCasSigner == null
                    || java.util.Arrays.equals(pinnedAuditSigner, existingCasSigner)
                    || authorityId == null || !authorityId.matches("[A-Za-z0-9_-]{8,96}")
                    || epoch <= 0 || generation <= 0 || minimum < 0)
                return null;
            try {
                byte[] pin = pinnedAuditSigner.clone();
                java.security.PublicKey key = java.security.KeyFactory.getInstance("Ed25519")
                        .generatePublic(new java.security.spec.X509EncodedKeySpec(pin));
                if (!java.util.Arrays.equals(pin, key.getEncoded())) return null;
                return new ImmutableAuditFirstGate(independentlyAdministeredAudit,
                        downstreamOnlyAfterSeal, pin, authorityId, epoch, generation,
                        minimum, null);
            } catch (GeneralSecurityException | RuntimeException badPin) {
                return null;
            }
        }
        /**
         * Optional gated prototype: independent root advance must be signed and
         * checked AFTER an authenticated write-ahead seal, BEFORE downstream CAS.
         * External provisioned root/sequence must never be enrolled from an app
         * backup. This binder cannot itself certify operator independence.
         */
        public static ImmutableAuditFirstGate bindWithCheckpoint(
                IndependentImmutableClaimAudit audit,
                ClaimAfterAuditSeal downstream, byte[] pinnedAuditSigner,
                byte[] existingCasSigner, String authorityId, long epoch,
                long generation, long auditMinimum,
                IndependentImmutableAuditRootAdvancer independentlyManagedRoot,
                byte[] independentRootSigner, byte[] externallyTrustedRoot,
                long externallyTrustedCheckpointSequence) {
            ImmutableAuditFirstGate base = bind(audit, downstream, pinnedAuditSigner,
                    existingCasSigner, authorityId, epoch, generation, auditMinimum);
            if (base == null || independentRootSigner == null
                    || java.util.Arrays.equals(independentRootSigner, pinnedAuditSigner)
                    || java.util.Arrays.equals(independentRootSigner, existingCasSigner))
                return null;
            ImmutableAuditCheckpointAdvanceGate barrier =
                    ImmutableAuditCheckpointAdvanceGate.bind(independentlyManagedRoot,
                            independentRootSigner, externallyTrustedRoot, authorityId,
                            epoch, generation, externallyTrustedCheckpointSequence);
            if (barrier == null) return null;
            return new ImmutableAuditFirstGate(audit, downstream, pinnedAuditSigner,
                    authorityId, epoch, generation, auditMinimum, barrier);
        }
        private String checked(String operation, String claim, String exactId,
                long revision, byte[] owner, String nonce,
                SignedImmutableClaimAuditReply receipt) {
            if (receipt == null || !authorityId.equals(receipt.authorityId)
                    || epoch != receipt.epoch || generation != receipt.generation
                    || !claim.equals(receipt.claim) || !exactId.equals(receipt.exactId)
                    || revision != receipt.revision || !nonce.equals(receipt.freshNonce)
                    || !operation.equals(receipt.operation)
                    || (receipt.sequence < externalMinimum
                        && !"ABSENT".equals(receipt.state))) return null;
            byte[] payload = immutableClaimAuditPayload(authorityId, epoch,
                    generation, claim, exactId, revision, owner, nonce,
                    operation, receipt.state, receipt.sequence);
            if (payload.length == 0) return null;
            try {
                java.security.Signature verify = java.security.Signature.getInstance("Ed25519");
                verify.initVerify(java.security.KeyFactory.getInstance("Ed25519")
                        .generatePublic(new java.security.spec.X509EncodedKeySpec(auditPin)));
                verify.update(payload);
                return verify.verify(receipt.signature()) ? receipt.state : null;
            } catch (GeneralSecurityException | RuntimeException invalid) {
                return null;
            }
        }
        public GlobalClaimGateOutcome reserveOnce(String claim, String exactId,
                long revision, byte[] owner, String nonce) {
            if (immutableClaimAuditPayload(authorityId, epoch, generation,
                    claim, exactId, revision, owner, nonce,
                    "QUERY", "ABSENT", 0).length == 0)
                return GlobalClaimGateOutcome.UNVERIFIABLE;
            if (quarantined.get()) return GlobalClaimGateOutcome.NO_AUTHORITY;
            if (!attempted.add(claim)) return GlobalClaimGateOutcome.STARTED_INDETERMINATE;
            try {
                // Audit challenge is generated inside this gate, never supplied
                // by a caller or reused from a restored application snapshot.
                byte[] random = new byte[24];
                new java.security.SecureRandom().nextBytes(random);
                String auditNonce = java.util.Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(random);
                SignedImmutableClaimAuditReply query = audit.query(authorityId, epoch,
                        claim, exactId, revision, owner.clone(), auditNonce);
                String before = checked("QUERY", claim, exactId, revision, owner,
                        auditNonce, query);
                if ("SEALED".equals(before)) return GlobalClaimGateOutcome.STARTED_INDETERMINATE;
                if (!"ABSENT".equals(before)) return GlobalClaimGateOutcome.NO_AUTHORITY;
                SignedImmutableClaimAuditReply sealed = audit.sealOnce(authorityId, epoch,
                        claim, exactId, revision, owner.clone(), auditNonce);
                String status = checked("SEAL", claim, exactId, revision, owner,
                        auditNonce, sealed);
                if (!"FIRST_SEALED".equals(status))
                    return GlobalClaimGateOutcome.STARTED_INDETERMINATE;
                // FIRST must advance BOTH the external minimum and the newly
                // authenticated QUERY high-water; a signer may have rolled back
                // between the two requests even while each proof is valid.
                if (sealed.sequence <= externalMinimum
                        || sealed.sequence <= query.sequence) {
                    quarantined.set(true);
                    return GlobalClaimGateOutcome.STARTED_INDETERMINATE;
                }
                // A sealed claim alone is not authorization: the separately
                // pinned root MUST advance and prove membership before CAS.
                // Crash or missing signed ACK: no downstream and no retry.
                if (rootAdvancement != null && !rootAdvancement.advanceAfterSeal(
                        claim, exactId, revision, owner, sealed.sequence))
                    return GlobalClaimGateOutcome.STARTED_INDETERMINATE;
                GlobalClaimGateOutcome downstream = continuation.continueOnce(claim,
                        exactId, revision, owner.clone(), nonce);
                return downstream == GlobalClaimGateOutcome.FIRST_RESERVED_PENDING_ACK
                        ? downstream : GlobalClaimGateOutcome.STARTED_INDETERMINATE;
            } catch (Exception interruptedOrUnknown) {
                return GlobalClaimGateOutcome.STARTED_INDETERMINATE;
            }
        }
    }

    /**
     * FUTURE ONLY — bounded snapshot-root continuity proof, NOT a storage
     * service. The 32-byte trustedRoot MUST be provisioned and advanced by an
     * administrator outside caller, audit-file and signing-key backup domains.
     * A caller-controlled pin makes same-key total rollback undetectable.
     * A consistent ABSENT snapshot is NEVER authorization to write/spend.
     */
    public static final class AuditRootRecord {
        public final String claim, exactId;
        public final long revision, sequence;
        private final byte[] owner;
        public AuditRootRecord(String claim, String exactId, long revision,
                byte[] owner, long sequence) {
            this.claim = claim; this.exactId = exactId;
            this.revision = revision; this.sequence = sequence;
            this.owner = owner == null ? null : owner.clone();
        }
        public byte[] owner() { return owner == null ? null : owner.clone(); }
    }

    public static byte[] immutableAuditRootHash(List<AuditRootRecord> orderedRecords) {
        if (orderedRecords == null || orderedRecords.size() > 256)
            return new byte[0];
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.write("future-g1/immutable-audit-root/v1\n".getBytes(StandardCharsets.UTF_8));
            out.writeInt(orderedRecords.size());
            String previous = null;
            for (AuditRootRecord r : orderedRecords) {
                if (r == null || r.claim == null
                        || !r.claim.matches("[A-Za-z0-9_-]{8,128}")
                        || (previous != null && previous.compareTo(r.claim) >= 0)
                        || r.exactId == null || r.exactId.isBlank()
                        || r.exactId.length() > 256 || r.revision <= 0
                        || r.sequence <= 0 || r.owner == null || r.owner.length != 64)
                    return new byte[0];
                for (byte[] field : new byte[][] {
                        r.claim.getBytes(StandardCharsets.UTF_8),
                        r.exactId.getBytes(StandardCharsets.UTF_8), r.owner }) {
                    out.writeInt(field.length);
                    out.write(field);
                }
                out.writeLong(r.revision);
                out.writeLong(r.sequence);
                previous = r.claim;
            }
            out.flush();
            return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());
        } catch (GeneralSecurityException | IOException | RuntimeException malformed) {
            return new byte[0];
        }
    }

    public static byte[] immutableAuditRootSnapshotPayload(String authorityId,
            long epoch, long generation, long sequence, byte[] root,
            String freshNonce) {
        if (authorityId == null || !authorityId.matches("[A-Za-z0-9_-]{8,96}")
                || epoch <= 0 || generation <= 0 || sequence <= 0
                || root == null || root.length != 32
                || freshNonce == null || !freshNonce.matches("[A-Za-z0-9_-]{24,128}"))
            return new byte[0];
        return ("future-g1/immutable-audit-root-snapshot/v1\n" + authorityId
                + "\n" + epoch + "\n" + generation + "\n" + sequence
                + "\n" + java.util.Base64.getEncoder().encodeToString(root)
                + "\n" + freshNonce + "\n").getBytes(StandardCharsets.UTF_8);
    }

    public static final class SignedImmutableAuditRootSnapshot {
        public final String authorityId, nonce;
        public final long epoch, generation, sequence;
        private final byte[] root, signature;
        public final List<AuditRootRecord> records;
        public SignedImmutableAuditRootSnapshot(String authorityId, long epoch,
                long generation, long sequence, String nonce, byte[] root,
                List<AuditRootRecord> records, byte[] signature) {
            this.authorityId = authorityId; this.epoch = epoch;
            this.generation = generation; this.sequence = sequence;
            this.nonce = nonce; this.root = root == null ? null : root.clone();
            this.signature = signature == null ? null : signature.clone();
            this.records = records == null ? null : List.copyOf(records);
        }
        public byte[] root() { return root == null ? null : root.clone(); }
        public byte[] signature() {
            return signature == null ? null : signature.clone();
        }
    }

    public interface IndependentImmutableAuditRootView {
        SignedImmutableAuditRootSnapshot readRoot(String authorityId,
                long epoch, long generation, String freshNonce) throws Exception;
    }

    public enum AuditRootMembership {
        CONFIRMED_SPENT, NOT_IN_CHECKPOINT_NO_AUTHORIZATION, UNVERIFIABLE
    }

    public static final class ImmutableAuditRootCheckpointGate {
        private final IndependentImmutableAuditRootView authority;
        private final byte[] signerPin, trustedRoot;
        private final String authorityId;
        private final long epoch, generation, headSequence;
        private ImmutableAuditRootCheckpointGate(IndependentImmutableAuditRootView authority,
                byte[] signerPin, byte[] trustedRoot, String authorityId,
                long epoch, long generation, long headSequence) {
            this.authority = authority; this.signerPin = signerPin.clone();
            this.trustedRoot = trustedRoot.clone(); this.authorityId = authorityId;
            this.epoch = epoch; this.generation = generation;
            this.headSequence = headSequence;
        }
        public static ImmutableAuditRootCheckpointGate bind(
                IndependentImmutableAuditRootView independentlyManagedRoot,
                byte[] pinnedSigner, byte[] externallyTrustedRoot,
                String authorityId, long epoch, long generation,
                long externallyTrustedSequence) {
            if (independentlyManagedRoot == null || pinnedSigner == null
                    || externallyTrustedRoot == null || externallyTrustedRoot.length != 32
                    || immutableAuditRootSnapshotPayload(authorityId, epoch,
                            generation, externallyTrustedSequence,
                            externallyTrustedRoot, "synthetic-nonce-at-least-24").length == 0)
                return null;
            try {
                byte[] pin = pinnedSigner.clone();
                java.security.PublicKey key = java.security.KeyFactory.getInstance("Ed25519")
                        .generatePublic(new java.security.spec.X509EncodedKeySpec(pin));
                if (!Arrays.equals(pin, key.getEncoded())) return null;
                return new ImmutableAuditRootCheckpointGate(independentlyManagedRoot,
                        pin, externallyTrustedRoot, authorityId,
                        epoch, generation, externallyTrustedSequence);
            } catch (GeneralSecurityException | RuntimeException invalid) {
                return null;
            }
        }
        public AuditRootMembership readMembership(String claim, String exactId,
                long revision, byte[] owner) {
            if (claim == null || !claim.matches("[A-Za-z0-9_-]{8,128}")
                    || exactId == null || exactId.isBlank() || exactId.length() > 256
                    || revision <= 0 || owner == null || owner.length != 64)
                return AuditRootMembership.UNVERIFIABLE;
            try {
                byte[] rnd = new byte[24];
                new java.security.SecureRandom().nextBytes(rnd);
                String nonce = java.util.Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(rnd);
                SignedImmutableAuditRootSnapshot snapshot = authority.readRoot(
                        authorityId, epoch, generation, nonce);
                if (snapshot == null || !authorityId.equals(snapshot.authorityId)
                        || epoch != snapshot.epoch || generation != snapshot.generation
                        || headSequence != snapshot.sequence || !nonce.equals(snapshot.nonce))
                    return AuditRootMembership.UNVERIFIABLE;
                byte[] returnedRoot = snapshot.root();
                byte[] recalculated = immutableAuditRootHash(snapshot.records);
                if (recalculated.length != 32
                        || !MessageDigest.isEqual(trustedRoot, returnedRoot)
                        || !MessageDigest.isEqual(recalculated, returnedRoot))
                    return AuditRootMembership.UNVERIFIABLE;
                byte[] payload = immutableAuditRootSnapshotPayload(authorityId,
                        epoch, generation, snapshot.sequence, returnedRoot, nonce);
                java.security.Signature verifier = java.security.Signature.getInstance("Ed25519");
                verifier.initVerify(java.security.KeyFactory.getInstance("Ed25519")
                        .generatePublic(new java.security.spec.X509EncodedKeySpec(signerPin)));
                verifier.update(payload);
                if (!verifier.verify(snapshot.signature()))
                    return AuditRootMembership.UNVERIFIABLE;
                for (AuditRootRecord record : snapshot.records) {
                    if (record.claim.equals(claim))
                        return (record.exactId.equals(exactId)
                                && record.revision == revision
                                && MessageDigest.isEqual(record.owner(), owner))
                            ? AuditRootMembership.CONFIRMED_SPENT
                            : AuditRootMembership.UNVERIFIABLE;
                }
                // Snapshot absence is NOT an atomic 'FIRST' authorization.
                return AuditRootMembership.NOT_IN_CHECKPOINT_NO_AUTHORIZATION;
            } catch (Exception failure) {
                return AuditRootMembership.UNVERIFIABLE;
            }
        }
    }

    /**
     * FUTURE ONLY — opt-in signed checkpoint ADVANCE/COMMIT contract. An audit
     * tombstone is written first; only an independently signed, freshly
     * challenged new checkpoint containing that EXACT tombstone may release
     * the downstream callback. Absence, timeouts, lost ACK and crash burn the
     * attempt; no re-enrollment or retry. This checks cryptographic consistency
     * conditional on external custody, NOT real-world nonrollbackability.
     */
    public interface IndependentImmutableAuditRootAdvancer {
        SignedImmutableAuditRootAdvance advance(String authorityId, long epoch,
                long generation, String claim, String exactId, long revision,
                byte[] owner, long sealedSequence, long expectedSequence,
                byte[] expectedRoot, String freshChallenge) throws Exception;
    }

    public static final class SignedImmutableAuditRootAdvance {
        public final String authorityId, claim, exactId, challenge;
        public final long epoch, generation, revision, sealedSequence;
        public final long previousSequence, nextSequence;
        private final byte[] previousRoot, nextRoot, signature;
        public final List<AuditRootRecord> records;
        public SignedImmutableAuditRootAdvance(String authorityId, long epoch,
                long generation, String claim, String exactId, long revision,
                long sealedSequence, long previousSequence, long nextSequence,
                String challenge, byte[] previousRoot, byte[] nextRoot,
                List<AuditRootRecord> records, byte[] signature) {
            this.authorityId = authorityId; this.epoch = epoch;
            this.generation = generation; this.claim = claim;
            this.exactId = exactId; this.revision = revision;
            this.sealedSequence = sealedSequence;
            this.previousSequence = previousSequence;
            this.nextSequence = nextSequence; this.challenge = challenge;
            this.previousRoot = previousRoot == null ? null : previousRoot.clone();
            this.nextRoot = nextRoot == null ? null : nextRoot.clone();
            this.signature = signature == null ? null : signature.clone();
            this.records = records == null ? null : List.copyOf(records);
        }
        public byte[] previousRoot() {
            return previousRoot == null ? null : previousRoot.clone();
        }
        public byte[] nextRoot() { return nextRoot == null ? null : nextRoot.clone(); }
        public byte[] signature() { return signature == null ? null : signature.clone(); }
    }

    public static byte[] immutableAuditRootAdvancePayload(String authorityId,
            long epoch, long generation, String claim, String exactId,
            long revision, byte[] owner, long sealedSequence,
            long previousSequence, byte[] previousRoot,
            long nextSequence, byte[] nextRoot, String challenge) {
        if (authorityId == null || !authorityId.matches("[A-Za-z0-9_-]{8,96}")
                || epoch <= 0 || generation <= 0 || claim == null
                || !claim.matches("[A-Za-z0-9_-]{8,128}")
                || exactId == null || exactId.isBlank() || exactId.length() > 256
                || revision <= 0 || owner == null || owner.length != 64
                || sealedSequence <= previousSequence || previousSequence <= 0
                || nextSequence < sealedSequence || nextSequence <= previousSequence
                || previousRoot == null || previousRoot.length != 32
                || nextRoot == null || nextRoot.length != 32
                || challenge == null || !challenge.matches("[A-Za-z0-9_-]{24,128}"))
            return new byte[0];
        return ("future-g1/immutable-root-checkpoint-advance/v1\n"
                + authorityId + "\n" + epoch + "\n" + generation + "\n"
                + claim + "\n" + java.util.Base64.getEncoder().encodeToString(
                        exactId.getBytes(StandardCharsets.UTF_8)) + "\n"
                + revision + "\n" + java.util.Base64.getEncoder().encodeToString(owner)
                + "\n" + sealedSequence + "\n" + previousSequence
                + "\n" + java.util.Base64.getEncoder().encodeToString(previousRoot)
                + "\n" + nextSequence + "\n"
                + java.util.Base64.getEncoder().encodeToString(nextRoot)
                + "\n" + challenge + "\n").getBytes(StandardCharsets.UTF_8);
    }

    public static final class ImmutableAuditCheckpointAdvanceGate {
        private final IndependentImmutableAuditRootAdvancer authority;
        private final byte[] pin, trustedRoot;
        private final String authorityId;
        private final long epoch, generation, trustedSequence;
        private ImmutableAuditCheckpointAdvanceGate(
                IndependentImmutableAuditRootAdvancer authority, byte[] pin,
                byte[] root, String authorityId, long epoch, long generation,
                long trustedSequence) {
            this.authority = authority; this.pin = pin.clone();
            this.trustedRoot = root.clone(); this.authorityId = authorityId;
            this.epoch = epoch; this.generation = generation;
            this.trustedSequence = trustedSequence;
        }
        public static ImmutableAuditCheckpointAdvanceGate bind(
                IndependentImmutableAuditRootAdvancer independentAuthority,
                byte[] pinnedSigner, byte[] trustedRoot,
                String authorityId, long epoch, long generation,
                long trustedSequence) {
            if (independentAuthority == null || pinnedSigner == null
                    || trustedRoot == null || trustedRoot.length != 32
                    || immutableAuditRootSnapshotPayload(authorityId, epoch,
                            generation, trustedSequence, trustedRoot,
                            "synthetic-nonce-at-least-24").length == 0)
                return null;
            try {
                byte[] pin = pinnedSigner.clone();
                java.security.PublicKey key = java.security.KeyFactory.getInstance("Ed25519")
                        .generatePublic(new java.security.spec.X509EncodedKeySpec(pin));
                if (!Arrays.equals(key.getEncoded(), pin)) return null;
                return new ImmutableAuditCheckpointAdvanceGate(independentAuthority,
                        pin, trustedRoot, authorityId, epoch, generation,
                        trustedSequence);
            } catch (GeneralSecurityException | RuntimeException badPin) {
                return null;
            }
        }
        public boolean advanceAfterSeal(String claim, String exactId,
                long revision, byte[] owner, long sealedSequence) {
            if (immutableAuditRootAdvancePayload(authorityId, epoch, generation,
                    claim, exactId, revision, owner, sealedSequence,
                    trustedSequence, trustedRoot, sealedSequence, trustedRoot,
                    "synthetic-nonce-at-least-24").length == 0)
                return false;
            try {
                byte[] random = new byte[24];
                new java.security.SecureRandom().nextBytes(random);
                String challenge = java.util.Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(random);
                SignedImmutableAuditRootAdvance result = authority.advance(
                        authorityId, epoch, generation, claim, exactId, revision,
                        owner.clone(), sealedSequence, trustedSequence,
                        trustedRoot.clone(), challenge);
                if (result == null || !authorityId.equals(result.authorityId)
                        || epoch != result.epoch || generation != result.generation
                        || !claim.equals(result.claim) || !exactId.equals(result.exactId)
                        || revision != result.revision
                        || sealedSequence != result.sealedSequence
                        || trustedSequence != result.previousSequence
                        || result.nextSequence <= trustedSequence
                        || result.nextSequence < sealedSequence
                        || !challenge.equals(result.challenge)
                        || !MessageDigest.isEqual(trustedRoot, result.previousRoot()))
                    return false;
                byte[] recalculated = immutableAuditRootHash(result.records);
                byte[] newRoot = result.nextRoot();
                if (recalculated.length != 32
                        || !MessageDigest.isEqual(recalculated, newRoot)) return false;
                boolean present = false;
                for (AuditRootRecord r : result.records) {
                    if (r.claim.equals(claim)) {
                        if (!r.exactId.equals(exactId) || r.revision != revision
                                || r.sequence != sealedSequence
                                || !MessageDigest.isEqual(owner, r.owner()))
                            return false;
                        present = true;
                    }
                }
                if (!present) return false;
                byte[] payload = immutableAuditRootAdvancePayload(authorityId,
                        epoch, generation, claim, exactId, revision, owner,
                        sealedSequence, trustedSequence, trustedRoot,
                        result.nextSequence, newRoot, challenge);
                if (payload.length == 0) return false;
                java.security.Signature verifier = java.security.Signature.getInstance("Ed25519");
                verifier.initVerify(java.security.KeyFactory.getInstance("Ed25519")
                        .generatePublic(new java.security.spec.X509EncodedKeySpec(pin)));
                verifier.update(payload);
                return verifier.verify(result.signature());
            } catch (Exception unknownOrCrashed) {
                return false;
            }
        }
    }

    /**
     * FUTURE-ONLY read-only lost-ACK recovery contract. The caller MUST supply
     * an externally retained checkpoint root and sequence from BEFORE the
     * interrupted advance. Neither caller snapshots nor signed replies alone
     * establish actual immutable or nonrollbackable external custody.
     * A verified NOT_YET_ANCHORED result NEVER authorizes SEAL/FIRST retry.
     */
    public interface IndependentImmutableAuditRootRecoveryView {
        SignedImmutableAuditRootRecovery readRecovery(String authorityId,
                long epoch, long generation, String claim, String exactId,
                long revision, byte[] owner, long sealedSequence,
                long trustedSequence, byte[] trustedRoot, String freshChallenge)
                throws Exception;
    }

    public enum ImmutableAuditRecoveryState {
        CLAIM_PRESENT_ANCHORED_NO_RETRY,
        NOT_YET_ANCHORED_NO_RETRY,
        UNVERIFIABLE_NO_RETRY
    }

    public static final class SignedImmutableAuditRootRecovery {
        public final String authorityId, claim, exactId, challenge;
        public final long epoch, generation, revision, sealedSequence;
        public final long previousSequence, currentSequence;
        private final byte[] previousRoot, currentRoot, signature;
        public final List<AuditRootRecord> records;
        public SignedImmutableAuditRootRecovery(String authorityId, long epoch,
                long generation, String claim, String exactId, long revision,
                long sealedSequence, long previousSequence, long currentSequence,
                String challenge, byte[] previousRoot, byte[] currentRoot,
                List<AuditRootRecord> records, byte[] signature) {
            this.authorityId = authorityId; this.epoch = epoch;
            this.generation = generation; this.claim = claim;
            this.exactId = exactId; this.revision = revision;
            this.sealedSequence = sealedSequence;
            this.previousSequence = previousSequence;
            this.currentSequence = currentSequence;
            this.challenge = challenge;
            this.previousRoot = previousRoot == null ? null : previousRoot.clone();
            this.currentRoot = currentRoot == null ? null : currentRoot.clone();
            this.signature = signature == null ? null : signature.clone();
            this.records = records == null ? null : List.copyOf(records);
        }
        public byte[] previousRoot() { return previousRoot == null ? null : previousRoot.clone(); }
        public byte[] currentRoot() { return currentRoot == null ? null : currentRoot.clone(); }
        public byte[] signature() { return signature == null ? null : signature.clone(); }
    }

    public static byte[] immutableAuditRecoveryPayload(String authorityId,
            long epoch, long generation, String claim, String exactId,
            long revision, byte[] owner, long sealedSequence,
            long previousSequence, byte[] previousRoot, long currentSequence,
            byte[] currentRoot, String challenge) {
        if (authorityId == null || !authorityId.matches("[A-Za-z0-9_-]{8,96}")
                || epoch <= 0 || generation <= 0 || claim == null
                || !claim.matches("[A-Za-z0-9_-]{8,128}")
                || exactId == null || exactId.isBlank() || exactId.length() > 256
                || revision <= 0 || owner == null || owner.length != 64
                || previousSequence <= 0 || sealedSequence <= previousSequence
                || currentSequence < previousSequence
                || previousRoot == null || previousRoot.length != 32
                || currentRoot == null || currentRoot.length != 32
                || challenge == null || !challenge.matches("[A-Za-z0-9_-]{24,128}"))
            return new byte[0];
        return ("future-g1/immutable-audit-root-recovery-readonly/v1\n"
                + authorityId + "\n" + epoch + "\n" + generation + "\n"
                + claim + "\n" + java.util.Base64.getEncoder().encodeToString(
                        exactId.getBytes(StandardCharsets.UTF_8)) + "\n"
                + revision + "\n" + java.util.Base64.getEncoder().encodeToString(owner)
                + "\n" + sealedSequence + "\n" + previousSequence + "\n"
                + java.util.Base64.getEncoder().encodeToString(previousRoot) + "\n"
                + currentSequence + "\n"
                + java.util.Base64.getEncoder().encodeToString(currentRoot)
                + "\n" + challenge + "\n").getBytes(StandardCharsets.UTF_8);
    }

    public static final class ImmutableAuditCheckpointRecoveryGate {
        private final IndependentImmutableAuditRootRecoveryView authority;
        private final byte[] pin, trustedRoot;
        private final String authorityId;
        private final long epoch, generation, trustedSequence;
        // Optional stricter opt-in. Caller must obtain this snapshot from a
        // separate, nonrollbackable administrator, NOT restored app state.
        private final List<AuditRootRecord> externallyTrustedMembers;
        private ImmutableAuditCheckpointRecoveryGate(
                IndependentImmutableAuditRootRecoveryView authority, byte[] pin,
                byte[] root, String authorityId, long epoch, long generation,
                long sequence) {
            this(authority, pin, root, authorityId, epoch, generation, sequence, null);
        }
        private ImmutableAuditCheckpointRecoveryGate(
                IndependentImmutableAuditRootRecoveryView authority, byte[] pin,
                byte[] root, String authorityId, long epoch, long generation,
                long sequence, List<AuditRootRecord> trustedMembers) {
            this.authority = authority; this.pin = pin.clone();
            this.trustedRoot = root.clone(); this.authorityId = authorityId;
            this.epoch = epoch; this.generation = generation;
            this.trustedSequence = sequence;
            this.externallyTrustedMembers = trustedMembers;
        }
        public static ImmutableAuditCheckpointRecoveryGate bind(
                IndependentImmutableAuditRootRecoveryView independentReadOnlyAuthority,
                byte[] pinnedRootSigner, byte[] auditSigner, byte[] casSigner,
                byte[] externallyTrustedRoot, String authorityId,
                long epoch, long generation, long externallyTrustedSequence) {
            if (independentReadOnlyAuthority == null || pinnedRootSigner == null
                    || auditSigner == null || casSigner == null
                    || Arrays.equals(pinnedRootSigner, auditSigner)
                    || Arrays.equals(pinnedRootSigner, casSigner)
                    || externallyTrustedRoot == null || externallyTrustedRoot.length != 32
                    || immutableAuditRootSnapshotPayload(authorityId, epoch, generation,
                            externallyTrustedSequence, externallyTrustedRoot,
                            "synthetic-nonce-at-least-24").length == 0)
                return null;
            try {
                byte[] pin = pinnedRootSigner.clone();
                java.security.PublicKey key = java.security.KeyFactory.getInstance("Ed25519")
                        .generatePublic(new java.security.spec.X509EncodedKeySpec(pin));
                if (!Arrays.equals(key.getEncoded(), pin)) return null;
                return new ImmutableAuditCheckpointRecoveryGate(independentReadOnlyAuthority,
                        pin, externallyTrustedRoot, authorityId, epoch, generation,
                        externallyTrustedSequence);
            } catch (GeneralSecurityException | RuntimeException invalid) {
                return null;
            }
        }
        /**
         * Opt-in fail-closed continuity check: a root hash alone cannot prove
         * that a newer signed checkpoint retained earlier spent claims. The
         * trusted member set and matching root must both originate outside
         * the app/backup/signing-key reset domains. Empty/missing/inconsistent
         * retained history is not automatically enrolled from the reply.
         * This is a cryptographic fixture, NOT proof of a real external store.
         */
        public static ImmutableAuditCheckpointRecoveryGate bindWithTrustedRecords(
                IndependentImmutableAuditRootRecoveryView independentReadOnlyAuthority,
                byte[] pinnedRootSigner, byte[] auditSigner, byte[] casSigner,
                byte[] externallyTrustedRoot, List<AuditRootRecord> trustedMembers,
                String authorityId, long epoch, long generation,
                long externallyTrustedSequence) {
            ImmutableAuditCheckpointRecoveryGate base = bind(
                    independentReadOnlyAuthority, pinnedRootSigner, auditSigner,
                    casSigner, externallyTrustedRoot, authorityId, epoch, generation,
                    externallyTrustedSequence);
            if (base == null || trustedMembers == null || trustedMembers.isEmpty()
                    || !MessageDigest.isEqual(externallyTrustedRoot,
                            immutableAuditRootHash(trustedMembers))) return null;
            try {
                List<AuditRootRecord> snapshot = new ArrayList<>();
                for (AuditRootRecord r : trustedMembers) {
                    snapshot.add(new AuditRootRecord(r.claim, r.exactId,
                            r.revision, r.owner(), r.sequence));
                }
                return new ImmutableAuditCheckpointRecoveryGate(
                        independentReadOnlyAuthority, base.pin, base.trustedRoot,
                        authorityId, epoch, generation, externallyTrustedSequence,
                        List.copyOf(snapshot));
            } catch (RuntimeException malformed) {
                return null;
            }
        }
        private boolean retainsEveryTrustedMember(List<AuditRootRecord> records) {
            if (externallyTrustedMembers == null) return true; // legacy-only
            if (records == null || records.size() < externallyTrustedMembers.size())
                return false;
            int position = 0;
            for (AuditRootRecord prior : externallyTrustedMembers) {
                while (position < records.size()
                        && records.get(position).claim.compareTo(prior.claim) < 0)
                    position++;
                if (position >= records.size()) return false;
                AuditRootRecord actual = records.get(position);
                if (!prior.claim.equals(actual.claim)
                        || !prior.exactId.equals(actual.exactId)
                        || prior.revision != actual.revision
                        || prior.sequence != actual.sequence
                        || !MessageDigest.isEqual(prior.owner(), actual.owner()))
                    return false;
                position++;
            }
            return true;
        }
        public ImmutableAuditRecoveryState recoverReadOnly(String claim,
                String exactId, long revision, byte[] owner, long sealedSequence) {
            final ImmutableAuditRecoveryState UNKNOWN =
                    ImmutableAuditRecoveryState.UNVERIFIABLE_NO_RETRY;
            if (immutableAuditRecoveryPayload(authorityId, epoch, generation,
                    claim, exactId, revision, owner, sealedSequence,
                    trustedSequence, trustedRoot, trustedSequence, trustedRoot,
                    "synthetic-nonce-at-least-24").length == 0)
                return UNKNOWN;
            try {
                byte[] random = new byte[24];
                new java.security.SecureRandom().nextBytes(random);
                String nonce = java.util.Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(random);
                SignedImmutableAuditRootRecovery response = authority.readRecovery(
                        authorityId, epoch, generation, claim, exactId, revision,
                        owner.clone(), sealedSequence, trustedSequence,
                        trustedRoot.clone(), nonce);
                if (response == null || !authorityId.equals(response.authorityId)
                        || epoch != response.epoch || generation != response.generation
                        || !claim.equals(response.claim) || !exactId.equals(response.exactId)
                        || revision != response.revision
                        || sealedSequence != response.sealedSequence
                        || trustedSequence != response.previousSequence
                        || response.currentSequence < trustedSequence
                        || !nonce.equals(response.challenge)
                        || !MessageDigest.isEqual(trustedRoot, response.previousRoot()))
                    return UNKNOWN;
                byte[] currentRoot = response.currentRoot();
                byte[] hash = immutableAuditRootHash(response.records);
                if (hash.length != 32 || !MessageDigest.isEqual(hash, currentRoot))
                    return UNKNOWN;
                byte[] payload = immutableAuditRecoveryPayload(authorityId,
                        epoch, generation, claim, exactId, revision, owner,
                        sealedSequence, trustedSequence, trustedRoot,
                        response.currentSequence, currentRoot, nonce);
                if (payload.length == 0) return UNKNOWN;
                java.security.Signature verify = java.security.Signature.getInstance("Ed25519");
                verify.initVerify(java.security.KeyFactory.getInstance("Ed25519")
                        .generatePublic(new java.security.spec.X509EncodedKeySpec(pin)));
                verify.update(payload);
                if (!verify.verify(response.signature())) return UNKNOWN;
                // Signed latest root alone is insufficient: a legitimate signer
                // could have pruned an older claim while advancing sequence.
                if (!retainsEveryTrustedMember(response.records)) return UNKNOWN;
                boolean member = false;
                for (AuditRootRecord record : response.records) {
                    if (!record.claim.equals(claim)) continue;
                    if (member || !record.exactId.equals(exactId)
                            || record.revision != revision
                            || record.sequence != sealedSequence
                            || !MessageDigest.isEqual(owner, record.owner()))
                        return UNKNOWN;
                    member = true;
                }
                if (response.currentSequence == trustedSequence) {
                    return !member && MessageDigest.isEqual(trustedRoot, currentRoot)
                            ? ImmutableAuditRecoveryState.NOT_YET_ANCHORED_NO_RETRY
                            : UNKNOWN;
                }
                return member && response.currentSequence >= sealedSequence
                        ? ImmutableAuditRecoveryState.CLAIM_PRESENT_ANCHORED_NO_RETRY
                        : UNKNOWN;
            } catch (Exception offlineOrMalformed) {
                return UNKNOWN;
            }
        }
    }

    /**
     * FUTURE ONLY — read-only, administrator-pinned enrollment verifier.
     * The registry signing key, enrollment digest and lower bound on its
     * monotonic counter MUST be supplied from a separate nonrollbackable
     * administration domain, never from the app, the operator's backup, or
     * the registry reply itself.  This class is NOT such a service.
     * No method on this interface provisions/enrolls/rotates a root.
     */
    public interface ExternalGenesisRegistry {
        SignedExternalGenesis readEnrollment(String authorityId, long epoch,
                long generation, String freshChallenge) throws Exception;
    }

    public static final class SignedExternalGenesis {
        public final String authorityId, enrollmentId, challenge;
        public final long epoch, generation, registrySequence, genesisSequence;
        public final List<AuditRootRecord> genesisMembers;
        private final byte[] rootSigner, auditSigner, casSigner, genesisRoot, signature;
        public SignedExternalGenesis(String authorityId, String enrollmentId,
                long epoch, long generation, long registrySequence,
                long genesisSequence, List<AuditRootRecord> genesisMembers,
                byte[] rootSigner, byte[] auditSigner, byte[] casSigner,
                byte[] genesisRoot, String challenge, byte[] signature) {
            this.authorityId = authorityId; this.enrollmentId = enrollmentId;
            this.epoch = epoch; this.generation = generation;
            this.registrySequence = registrySequence;
            this.genesisSequence = genesisSequence;
            this.genesisMembers = genesisMembers == null ? null : List.copyOf(genesisMembers);
            this.rootSigner = rootSigner == null ? null : rootSigner.clone();
            this.auditSigner = auditSigner == null ? null : auditSigner.clone();
            this.casSigner = casSigner == null ? null : casSigner.clone();
            this.genesisRoot = genesisRoot == null ? null : genesisRoot.clone();
            this.challenge = challenge;
            this.signature = signature == null ? null : signature.clone();
        }
        public byte[] rootSigner() { return rootSigner == null ? null : rootSigner.clone(); }
        public byte[] auditSigner() { return auditSigner == null ? null : auditSigner.clone(); }
        public byte[] casSigner() { return casSigner == null ? null : casSigner.clone(); }
        public byte[] genesisRoot() { return genesisRoot == null ? null : genesisRoot.clone(); }
        public byte[] signature() { return signature == null ? null : signature.clone(); }
    }

    /** Stable digest to be physically pinned by an external registrar. */
    public static byte[] externalGenesisIdentityDigest(SignedExternalGenesis receipt) {
        byte[] bytes = externalGenesisPayload(receipt, false);
        if (bytes.length == 0) return new byte[0];
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (GeneralSecurityException impossible) { return new byte[0]; }
    }

    /** Signed challenge response; receipt construction is not enrollment. */
    public static byte[] externalGenesisSignedPayload(SignedExternalGenesis receipt) {
        return externalGenesisPayload(receipt, true);
    }

    private static byte[] externalGenesisPayload(SignedExternalGenesis r,
            boolean includeChallenge) {
        if (r == null || r.authorityId == null
                || !r.authorityId.matches("[A-Za-z0-9_-]{8,96}")
                || r.enrollmentId == null
                || !r.enrollmentId.matches("[A-Za-z0-9_-]{16,128}")
                || r.epoch <= 0 || r.generation <= 0
                || r.registrySequence <= 0 || r.genesisSequence <= 0
                || r.genesisMembers == null || r.genesisMembers.isEmpty()
                || r.genesisRoot == null || r.genesisRoot.length != 32
                || !MessageDigest.isEqual(r.genesisRoot,
                        immutableAuditRootHash(r.genesisMembers))
                || r.rootSigner == null || r.auditSigner == null || r.casSigner == null
                || Arrays.equals(r.rootSigner, r.auditSigner)
                || Arrays.equals(r.rootSigner, r.casSigner)
                || Arrays.equals(r.auditSigner, r.casSigner)
                || (includeChallenge && (r.challenge == null
                        || !r.challenge.matches("[A-Za-z0-9_-]{24,128}"))))
            return new byte[0];
        String base = "future-g1/external-enrollment-identity/v1\n"
                + r.authorityId + "\n" + r.enrollmentId + "\n"
                + r.epoch + "\n" + r.generation + "\n"
                + r.registrySequence + "\n" + r.genesisSequence + "\n"
                + java.util.Base64.getEncoder().encodeToString(r.genesisRoot) + "\n"
                + java.util.Base64.getEncoder().encodeToString(r.rootSigner) + "\n"
                + java.util.Base64.getEncoder().encodeToString(r.auditSigner) + "\n"
                + java.util.Base64.getEncoder().encodeToString(r.casSigner) + "\n";
        return (includeChallenge
                ? "future-g1/external-enrollment-proof/v1\n" + base + r.challenge + "\n"
                : base).getBytes(StandardCharsets.UTF_8);
    }

    public static TwoRotationContinuity verifyTwoRotationsWithExternalGenesis(
            ExternalGenesisRegistry registry,
            byte[] externallyPinnedRegistrySigner,
            byte[] externallyPinnedEnrollmentDigest,
            long externallyPinnedMinRegistrySequence,
            String authorityId, long epoch, long generation,
            SignedImmutableAuditRootRecovery first, byte[] firstOwner,
            String firstChallenge, SignedImmutableAuditRootRecovery second,
            byte[] secondOwner, String secondChallenge) {
        final TwoRotationContinuity FAIL = TwoRotationContinuity.UNVERIFIABLE_NO_RETRY;
        if (registry == null || externallyPinnedRegistrySigner == null
                || externallyPinnedEnrollmentDigest == null
                || externallyPinnedEnrollmentDigest.length != 32
                || externallyPinnedMinRegistrySequence <= 0)
            return FAIL;
        try {
            byte[] pin = externallyPinnedRegistrySigner.clone();
            java.security.PublicKey registryKey = java.security.KeyFactory.getInstance("Ed25519")
                    .generatePublic(new java.security.spec.X509EncodedKeySpec(pin));
            if (!Arrays.equals(pin, registryKey.getEncoded())) return FAIL;
            byte[] random = new byte[24];
            new java.security.SecureRandom().nextBytes(random);
            String nonce = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(random);
            SignedExternalGenesis r = registry.readEnrollment(
                    authorityId, epoch, generation, nonce);
            if (r == null || !java.util.Objects.equals(authorityId, r.authorityId)
                    || epoch != r.epoch || generation != r.generation
                    || !nonce.equals(r.challenge)
                    || r.registrySequence < externallyPinnedMinRegistrySequence
                    || Arrays.equals(pin, r.rootSigner)
                    || Arrays.equals(pin, r.auditSigner)
                    || Arrays.equals(pin, r.casSigner)
                    || !MessageDigest.isEqual(externallyPinnedEnrollmentDigest,
                            externalGenesisIdentityDigest(r)))
                return FAIL;
            byte[] payload = externalGenesisSignedPayload(r);
            if (payload.length == 0 || r.signature == null) return FAIL;
            java.security.Signature verifier = java.security.Signature.getInstance("Ed25519");
            verifier.initVerify(registryKey);
            verifier.update(payload);
            if (!verifier.verify(r.signature)) return FAIL;
            return verifyTwoSignedCheckpointRotations(r.rootSigner, r.auditSigner,
                    r.casSigner, authorityId, epoch, generation,
                    r.genesisSequence, r.genesisRoot, r.genesisMembers,
                    first, firstOwner, firstChallenge,
                    second, secondOwner, secondChallenge);
        } catch (Exception missingOrInvalidExternalAuthority) {
            return FAIL;
        }
    }

    /**
     * FUTURE-ONLY opt-in TWO-ROTATION, read-only verification. Trust is anchored
     * to an externally retained root + complete member set, NOT a caller's
     * current disk image or an attestation's self-reported initial root.
     * Both rotations MUST be separately challenged and signed by the SAME
     * pre-pinned independent root signer. Every prior spent claim survives
     * both root transitions with identical ID, revision, owner and sequence.
     * This cryptographic check cannot establish external custody or detect a
     * simultaneous rollback of the externally trusted root/members and keys.
     * A positive verdict grants NO permission to reserve, retry or publish.
     */
    /**
     * FUTURE ONLY — a cryptographic handoff proof, NOT an enrollment service.
     * Old and new registrar keys both consent to exactly the same transition;
     * the old immutable digest, signer and floor must come from an independently
     * custodied trust domain. An app backup, same-key file store, or caller can
     * NOT establish those prerequisites. No method persists or rotates trust.
     */
    public enum ExternalRegistrarRotationVerdict {
        VERIFIED_ROTATION_NO_RETRY, UNVERIFIABLE_NO_RETRY
    }

    public static final class SignedExternalRegistrarRotation {
        public final String authorityId, fromEnrollmentId, toEnrollmentId, challenge;
        public final long fromSequence, toSequence;
        public final boolean revoked;
        private final byte[] fromDigest, toDigest, nextSigner, oldSignature, newSignature;

        public SignedExternalRegistrarRotation(String authorityId,
                String fromEnrollmentId, String toEnrollmentId,
                long fromSequence, long toSequence, boolean revoked,
                byte[] fromDigest, byte[] toDigest, byte[] nextSigner,
                String challenge, byte[] oldSignature, byte[] newSignature) {
            this.authorityId = authorityId;
            this.fromEnrollmentId = fromEnrollmentId;
            this.toEnrollmentId = toEnrollmentId;
            this.fromSequence = fromSequence;
            this.toSequence = toSequence;
            this.revoked = revoked;
            this.fromDigest = fromDigest == null ? null : fromDigest.clone();
            this.toDigest = toDigest == null ? null : toDigest.clone();
            this.nextSigner = nextSigner == null ? null : nextSigner.clone();
            this.challenge = challenge;
            this.oldSignature = oldSignature == null ? null : oldSignature.clone();
            this.newSignature = newSignature == null ? null : newSignature.clone();
        }
        public byte[] fromDigest() { return fromDigest == null ? null : fromDigest.clone(); }
        public byte[] toDigest() { return toDigest == null ? null : toDigest.clone(); }
        public byte[] nextSigner() { return nextSigner == null ? null : nextSigner.clone(); }
        public byte[] oldSignature() { return oldSignature == null ? null : oldSignature.clone(); }
        public byte[] newSignature() { return newSignature == null ? null : newSignature.clone(); }
    }

    /** Canonical, domain-separated, deliberately NON-self-enrolling payload. */
    public static byte[] externalRegistrarRotationPayload(
            SignedExternalRegistrarRotation r) {
        if (r == null || r.revoked
                || r.authorityId == null || !r.authorityId.matches("[A-Za-z0-9_-]{8,96}")
                || r.fromEnrollmentId == null
                || !r.fromEnrollmentId.matches("[A-Za-z0-9_-]{16,128}")
                || r.toEnrollmentId == null
                || !r.toEnrollmentId.matches("[A-Za-z0-9_-]{16,128}")
                || r.fromEnrollmentId.equals(r.toEnrollmentId)
                || r.fromSequence <= 0 || r.toSequence <= r.fromSequence
                || r.fromDigest == null || r.fromDigest.length != 32
                || r.toDigest == null || r.toDigest.length != 32
                || r.nextSigner == null || r.nextSigner.length == 0
                || r.challenge == null
                || !r.challenge.matches("[A-Za-z0-9_-]{24,128}"))
            return new byte[0];
        java.util.Base64.Encoder b64 = java.util.Base64.getEncoder();
        return ("future-g1/external-registrar-rotation/v1\n"
                + r.authorityId + "\n" + r.fromEnrollmentId + "\n"
                + r.toEnrollmentId + "\n" + r.fromSequence + "\n"
                + r.toSequence + "\n"
                + b64.encodeToString(r.fromDigest) + "\n"
                + b64.encodeToString(r.toDigest) + "\n"
                + b64.encodeToString(r.nextSigner) + "\n"
                + r.challenge + "\n").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Read-only two-registrar consent check. Revocation, an unknown successor,
     * missing external anchor, absent signatures and all malformed recovery
     * states fail closed without automatic retry, re-enrollment or publication.
     * A positive result is conditional on the protected old pin/digest/floor:
     * rotating a backup-controlled pin is NEVER a security proof.
     */
    public static ExternalRegistrarRotationVerdict verifyExternalRegistrarRotation(
            byte[] externallyPinnedOldRegistrar, byte[] externallyPinnedOldDigest,
            long externallyPinnedMinSequence, SignedExternalGenesis previous,
            SignedExternalGenesis successor, SignedExternalRegistrarRotation transition,
            String freshChallenge) {
        final ExternalRegistrarRotationVerdict FAIL =
                ExternalRegistrarRotationVerdict.UNVERIFIABLE_NO_RETRY;
        if (externallyPinnedOldRegistrar == null || externallyPinnedOldDigest == null
                || externallyPinnedOldDigest.length != 32
                || externallyPinnedMinSequence <= 0 || previous == null
                || successor == null || transition == null || freshChallenge == null
                || transition.revoked || !freshChallenge.equals(transition.challenge)
                || !freshChallenge.equals(previous.challenge)
                || !freshChallenge.equals(successor.challenge)
                || !java.util.Objects.equals(previous.authorityId, successor.authorityId)
                || !java.util.Objects.equals(previous.authorityId, transition.authorityId)
                || !java.util.Objects.equals(previous.enrollmentId, transition.fromEnrollmentId)
                || !java.util.Objects.equals(successor.enrollmentId, transition.toEnrollmentId)
                || previous.epoch != successor.epoch
                || previous.generation != successor.generation
                || previous.registrySequence != transition.fromSequence
                || successor.registrySequence != transition.toSequence
                || previous.registrySequence < externallyPinnedMinSequence
                || successor.genesisSequence < previous.genesisSequence
                || !MessageDigest.isEqual(externallyPinnedOldDigest,
                        externalGenesisIdentityDigest(previous))
                || !MessageDigest.isEqual(transition.fromDigest,
                        externallyPinnedOldDigest)
                || !MessageDigest.isEqual(transition.toDigest,
                        externalGenesisIdentityDigest(successor))
                || !retainsAuditMembers(previous.genesisMembers, successor.genesisMembers))
            return FAIL;
        try {
            byte[] oldPin = externallyPinnedOldRegistrar.clone();
            byte[] newPin = transition.nextSigner();
            java.security.KeyFactory factory = java.security.KeyFactory.getInstance("Ed25519");
            java.security.PublicKey oldKey = factory.generatePublic(
                    new java.security.spec.X509EncodedKeySpec(oldPin));
            java.security.PublicKey newKey = factory.generatePublic(
                    new java.security.spec.X509EncodedKeySpec(newPin));
            if (!Arrays.equals(oldKey.getEncoded(), oldPin)
                    || !Arrays.equals(newKey.getEncoded(), newPin)
                    || Arrays.equals(oldPin, newPin)
                    || Arrays.equals(oldPin, previous.rootSigner())
                    || Arrays.equals(oldPin, previous.auditSigner())
                    || Arrays.equals(oldPin, previous.casSigner())
                    || Arrays.equals(newPin, successor.rootSigner())
                    || Arrays.equals(newPin, successor.auditSigner())
                    || Arrays.equals(newPin, successor.casSigner()))
                return FAIL;
            byte[] oldPayload = externalGenesisSignedPayload(previous);
            byte[] newPayload = externalGenesisSignedPayload(successor);
            byte[] transitionPayload = externalRegistrarRotationPayload(transition);
            if (oldPayload.length == 0 || newPayload.length == 0
                    || transitionPayload.length == 0) return FAIL;
            java.security.Signature verify = java.security.Signature.getInstance("Ed25519");
            verify.initVerify(oldKey);
            verify.update(oldPayload);
            if (!verify.verify(previous.signature())) return FAIL;
            verify.initVerify(newKey);
            verify.update(newPayload);
            if (!verify.verify(successor.signature())) return FAIL;
            verify.initVerify(oldKey);
            verify.update(transitionPayload);
            if (!verify.verify(transition.oldSignature())) return FAIL;
            verify.initVerify(newKey);
            verify.update(transitionPayload);
            return verify.verify(transition.newSignature())
                    ? ExternalRegistrarRotationVerdict.VERIFIED_ROTATION_NO_RETRY : FAIL;
        } catch (Exception unverifiedOrUnavailable) {
            return FAIL;
        }
    }

    /**
     * FUTURE-ONLY: read-only status from a separately pre-pinned revocation
     * authority. The caller MUST obtain its key and minimum sequence from a
     * nonrollbackable administrative domain; this code cannot provide custody.
     * Availability failure is indeterminate, never implicit ACTIVE status.
     */
    public interface ExternalRegistrarStatusRegistry {
        SignedExternalRegistrarStatus readStatus(String authorityId,
                String fromEnrollmentId, String toEnrollmentId,
                String freshChallenge) throws Exception;
    }

    public static final class SignedExternalRegistrarStatus {
        public final String authorityId, fromEnrollmentId, toEnrollmentId, challenge;
        public final long statusSequence;
        public final boolean revoked;
        private final byte[] transitionDigest, signature;
        public SignedExternalRegistrarStatus(String authorityId,
                String fromEnrollmentId, String toEnrollmentId, long statusSequence,
                boolean revoked, byte[] transitionDigest, String challenge,
                byte[] signature) {
            this.authorityId = authorityId;
            this.fromEnrollmentId = fromEnrollmentId;
            this.toEnrollmentId = toEnrollmentId;
            this.statusSequence = statusSequence;
            this.revoked = revoked;
            this.transitionDigest = transitionDigest == null ? null : transitionDigest.clone();
            this.challenge = challenge;
            this.signature = signature == null ? null : signature.clone();
        }
        public byte[] transitionDigest() {
            return transitionDigest == null ? null : transitionDigest.clone();
        }
        public byte[] signature() {
            return signature == null ? null : signature.clone();
        }
    }

    /** Canonical signed ACTIVE/REVOKED proof, bound to the exact rotation. */
    public static byte[] externalRegistrarStatusPayload(SignedExternalRegistrarStatus r) {
        if (r == null || r.authorityId == null
                || !r.authorityId.matches("[A-Za-z0-9_-]{8,96}")
                || r.fromEnrollmentId == null
                || !r.fromEnrollmentId.matches("[A-Za-z0-9_-]{16,128}")
                || r.toEnrollmentId == null
                || !r.toEnrollmentId.matches("[A-Za-z0-9_-]{16,128}")
                || r.fromEnrollmentId.equals(r.toEnrollmentId)
                || r.statusSequence <= 0 || r.transitionDigest == null
                || r.transitionDigest.length != 32 || r.challenge == null
                || !r.challenge.matches("[A-Za-z0-9_-]{24,128}"))
            return new byte[0];
        return ("future-g1/independent-registrar-status/v1\n"
                + r.authorityId + "\n" + r.fromEnrollmentId + "\n"
                + r.toEnrollmentId + "\n" + r.statusSequence + "\n"
                + (r.revoked ? "REVOKED" : "ACTIVE") + "\n"
                + java.util.Base64.getEncoder().encodeToString(r.transitionDigest) + "\n"
                + r.challenge + "\n").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Fail-closed, read-only rotation status check. The third-party status
     * signer is distinct from BOTH registrar signers and all audit signers.
     * A fresh per-query nonce, exact transition digest and independently
     * pinned counter are mandatory. A VERIFIED verdict NEVER authorizes an
     * enrollment/write/retry; a mock responder is NOT durable external proof.
     */
    public static ExternalRegistrarRotationVerdict verifyExternalRegistrarRotationWithStatus(
            ExternalRegistrarStatusRegistry registry, byte[] pinnedStatusSigner,
            long pinnedMinimumStatusSequence, byte[] pinnedOldRegistrar,
            byte[] pinnedOldDigest, long pinnedMinimumRegistrySequence,
            SignedExternalGenesis previous, SignedExternalGenesis successor,
            SignedExternalRegistrarRotation transition) {
        final ExternalRegistrarRotationVerdict FAIL =
                ExternalRegistrarRotationVerdict.UNVERIFIABLE_NO_RETRY;
        if (registry == null || pinnedStatusSigner == null
                || pinnedMinimumStatusSequence <= 0 || transition == null
                || verifyExternalRegistrarRotation(pinnedOldRegistrar, pinnedOldDigest,
                        pinnedMinimumRegistrySequence, previous, successor,
                        transition, transition.challenge)
                        != ExternalRegistrarRotationVerdict.VERIFIED_ROTATION_NO_RETRY)
            return FAIL;
        try {
            java.security.PublicKey key = java.security.KeyFactory.getInstance("Ed25519")
                    .generatePublic(new java.security.spec.X509EncodedKeySpec(pinnedStatusSigner));
            byte[] pin = pinnedStatusSigner.clone();
            if (!Arrays.equals(key.getEncoded(), pin)
                    || Arrays.equals(pin, pinnedOldRegistrar)
                    || Arrays.equals(pin, transition.nextSigner())
                    || Arrays.equals(pin, previous.rootSigner())
                    || Arrays.equals(pin, previous.auditSigner())
                    || Arrays.equals(pin, previous.casSigner())
                    || Arrays.equals(pin, successor.rootSigner())
                    || Arrays.equals(pin, successor.auditSigner())
                    || Arrays.equals(pin, successor.casSigner())) return FAIL;
            byte[] transitionPayload = externalRegistrarRotationPayload(transition);
            if (transitionPayload.length == 0) return FAIL;
            byte[] expectedDigest = MessageDigest.getInstance("SHA-256")
                    .digest(transitionPayload);
            byte[] random = new byte[24];
            new java.security.SecureRandom().nextBytes(random);
            String nonce = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(random);
            SignedExternalRegistrarStatus status = registry.readStatus(
                    transition.authorityId, transition.fromEnrollmentId,
                    transition.toEnrollmentId, nonce);
            if (status == null || status.revoked
                    || status.statusSequence < pinnedMinimumStatusSequence
                    || !java.util.Objects.equals(status.authorityId, transition.authorityId)
                    || !java.util.Objects.equals(status.fromEnrollmentId,
                            transition.fromEnrollmentId)
                    || !java.util.Objects.equals(status.toEnrollmentId,
                            transition.toEnrollmentId)
                    || !nonce.equals(status.challenge)
                    || !MessageDigest.isEqual(expectedDigest, status.transitionDigest()))
                return FAIL;
            byte[] payload = externalRegistrarStatusPayload(status);
            if (payload.length == 0 || status.signature() == null) return FAIL;
            java.security.Signature signature = java.security.Signature.getInstance("Ed25519");
            signature.initVerify(key);
            signature.update(payload);
            return signature.verify(status.signature())
                    ? ExternalRegistrarRotationVerdict.VERIFIED_ROTATION_NO_RETRY : FAIL;
        } catch (Exception offlineOrForged) {
            return FAIL;
        }
    }

    /**
     * FUTURE-ONLY: independent, monotonic, read-only revocation witness.
     * The witness key, its high-water counter AND the sticky revocation bit
     * must be provisioned/retained outside all caller-restorable domains.
     * Signed local files do not supply that custody. This contract NEVER
     * grants enrollment, reservation, retry, tool execution or app release.
     */
    public static final class SignedExternalRegistrarWitness {
        public final String authorityId, fromEnrollmentId, toEnrollmentId, challenge;
        public final long witnessSequence, observedStatusFloor;
        public final boolean revokedEver;
        private final byte[] transitionDigest, statusDigest, signature;

        public SignedExternalRegistrarWitness(String authorityId, String fromEnrollmentId,
                String toEnrollmentId, long witnessSequence, long observedStatusFloor,
                boolean revokedEver, byte[] transitionDigest, byte[] statusDigest,
                String challenge, byte[] signature) {
            this.authorityId = authorityId;
            this.fromEnrollmentId = fromEnrollmentId;
            this.toEnrollmentId = toEnrollmentId;
            this.witnessSequence = witnessSequence;
            this.observedStatusFloor = observedStatusFloor;
            this.revokedEver = revokedEver;
            this.transitionDigest = transitionDigest == null ? null : transitionDigest.clone();
            this.statusDigest = statusDigest == null ? null : statusDigest.clone();
            this.challenge = challenge;
            this.signature = signature == null ? null : signature.clone();
        }
        public byte[] transitionDigest() { return transitionDigest == null ? null : transitionDigest.clone(); }
        public byte[] statusDigest() { return statusDigest == null ? null : statusDigest.clone(); }
        public byte[] signature() { return signature == null ? null : signature.clone(); }
    }

    public static byte[] externalRegistrarWitnessPayload(SignedExternalRegistrarWitness r) {
        if (r == null || r.authorityId == null
                || !r.authorityId.matches("[A-Za-z0-9_-]{8,96}")
                || r.fromEnrollmentId == null
                || !r.fromEnrollmentId.matches("[A-Za-z0-9_-]{16,128}")
                || r.toEnrollmentId == null
                || !r.toEnrollmentId.matches("[A-Za-z0-9_-]{16,128}")
                || r.fromEnrollmentId.equals(r.toEnrollmentId)
                || r.witnessSequence <= 0 || r.observedStatusFloor <= 0
                || r.transitionDigest == null || r.transitionDigest.length != 32
                || r.statusDigest == null || r.statusDigest.length != 32
                || r.challenge == null
                || !r.challenge.matches("[A-Za-z0-9_-]{24,128}"))
            return new byte[0];
        java.util.Base64.Encoder encoder = java.util.Base64.getEncoder();
        return ("future-g1/independent-registrar-revocation-witness/v1\n"
                + r.authorityId + "\n" + r.fromEnrollmentId + "\n"
                + r.toEnrollmentId + "\n" + r.witnessSequence + "\n"
                + r.observedStatusFloor + "\n"
                + (r.revokedEver ? "REVOKED_EVER" : "NOT_REVOKED") + "\n"
                + encoder.encodeToString(r.transitionDigest) + "\n"
                + encoder.encodeToString(r.statusDigest) + "\n"
                + r.challenge + "\n").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Status and witness must both sign the SAME unpredictable read challenge.
     * A witness observing a later revoked event defeats an earlier signed
     * ACTIVE response. This protects only as long as a real independent actor
     * retains its nonrollbackable witness key, sticky revoke state and floor.
     * The entire trust domain may still be reset in the local test mock.
     */
    public static ExternalRegistrarRotationVerdict verifyWitnessedRegistrarStatus(
            byte[] oldRegistrarPin, byte[] oldEnrollmentDigest, long oldRegistryFloor,
            SignedExternalGenesis previous, SignedExternalGenesis successor,
            SignedExternalRegistrarRotation rotation,
            SignedExternalRegistrarStatus status, byte[] statusSignerPin,
            long statusFloor, SignedExternalRegistrarWitness witness,
            byte[] witnessSignerPin, long witnessFloor, String freshStatusChallenge) {
        final ExternalRegistrarRotationVerdict FAIL =
                ExternalRegistrarRotationVerdict.UNVERIFIABLE_NO_RETRY;
        if (freshStatusChallenge == null || status == null || witness == null
                || statusSignerPin == null || witnessSignerPin == null
                || statusFloor <= 0 || witnessFloor <= 0
                || previous == null || successor == null || rotation == null
                || verifyExternalRegistrarRotation(oldRegistrarPin, oldEnrollmentDigest,
                        oldRegistryFloor, previous, successor, rotation, rotation.challenge)
                        != ExternalRegistrarRotationVerdict.VERIFIED_ROTATION_NO_RETRY)
            return FAIL;
        try {
            byte[] transitionPayload = externalRegistrarRotationPayload(rotation);
            byte[] statusPayload = externalRegistrarStatusPayload(status);
            byte[] witnessPayload = externalRegistrarWitnessPayload(witness);
            if (transitionPayload.length == 0 || statusPayload.length == 0
                    || witnessPayload.length == 0 || status.revoked || witness.revokedEver
                    || status.statusSequence < statusFloor
                    || status.statusSequence < witness.observedStatusFloor
                    || witness.witnessSequence < witnessFloor
                    || !freshStatusChallenge.equals(status.challenge)
                    || !freshStatusChallenge.equals(witness.challenge)
                    || !java.util.Objects.equals(status.authorityId, rotation.authorityId)
                    || !java.util.Objects.equals(witness.authorityId, rotation.authorityId)
                    || !java.util.Objects.equals(status.fromEnrollmentId,
                            rotation.fromEnrollmentId)
                    || !java.util.Objects.equals(status.toEnrollmentId, rotation.toEnrollmentId)
                    || !java.util.Objects.equals(witness.fromEnrollmentId,
                            rotation.fromEnrollmentId)
                    || !java.util.Objects.equals(witness.toEnrollmentId,
                            rotation.toEnrollmentId)) return FAIL;
            byte[] transitionDigest = MessageDigest.getInstance("SHA-256")
                    .digest(transitionPayload);
            byte[] statusDigest = MessageDigest.getInstance("SHA-256")
                    .digest(statusPayload);
            if (!MessageDigest.isEqual(transitionDigest, status.transitionDigest())
                    || !MessageDigest.isEqual(transitionDigest, witness.transitionDigest())
                    || !MessageDigest.isEqual(statusDigest, witness.statusDigest()))
                return FAIL;
            java.security.KeyFactory f = java.security.KeyFactory.getInstance("Ed25519");
            java.security.PublicKey sKey = f.generatePublic(
                    new java.security.spec.X509EncodedKeySpec(statusSignerPin));
            java.security.PublicKey wKey = f.generatePublic(
                    new java.security.spec.X509EncodedKeySpec(witnessSignerPin));
            byte[][] forbidden = {oldRegistrarPin, rotation.nextSigner(),
                    previous.rootSigner(), previous.auditSigner(), previous.casSigner(),
                    successor.rootSigner(), successor.auditSigner(), successor.casSigner()};
            if (!Arrays.equals(sKey.getEncoded(), statusSignerPin)
                    || !Arrays.equals(wKey.getEncoded(), witnessSignerPin)
                    || Arrays.equals(statusSignerPin, witnessSignerPin)) return FAIL;
            for (byte[] x : forbidden)
                if (Arrays.equals(x, statusSignerPin) || Arrays.equals(x, witnessSignerPin))
                    return FAIL;
            java.security.Signature sig = java.security.Signature.getInstance("Ed25519");
            sig.initVerify(sKey);
            sig.update(statusPayload);
            if (status.signature() == null || !sig.verify(status.signature())) return FAIL;
            sig.initVerify(wKey);
            sig.update(witnessPayload);
            return witness.signature() != null && sig.verify(witness.signature())
                    ? ExternalRegistrarRotationVerdict.VERIFIED_ROTATION_NO_RETRY : FAIL;
        } catch (Exception unavailableOrInvalid) {
            return FAIL;
        }
    }

    /**
     * FUTURE-ONLY read-only witness lookup interface. The implementation MUST
     * be a separately operated authority; this interface cannot prove that
     * the signer, monotonic floor, or history survive a full-domain reset.
     * Never obtain pins/floors from response contents or app-restorable state.
     */
    public interface ExternalRegistrarRevocationWitnessRegistry {
        SignedExternalRegistrarWitness readWitness(String authorityId,
                String fromEnrollmentId, String toEnrollmentId,
                String freshChallenge) throws Exception;
    }

    /**
     * Fresh live challenge before BOTH independent status and witness reads.
     * The low-level verifyWitnessedRegistrarStatus accepts a caller-provided
     * challenge; replaying two previously signed responses with that same
     * caller-chosen challenge is not proof of current non-revocation.
     * This wrapper generates an unpredictable 256-bit challenge internally,
     * obtains two matching signed responses, and fails CLOSED on any missing,
     * replayed, contradictory or offline read. Read-only; VERIFIED is no write,
     * retry, enrollment, provider, tool, memory or release permission.
     * It does NOT solve a full reset of all truly trusted remote domains.
     */
    public static ExternalRegistrarRotationVerdict verifyLiveWitnessedRegistrarStatus(
            ExternalRegistrarStatusRegistry statusRegistry,
            ExternalRegistrarRevocationWitnessRegistry witnessRegistry,
            byte[] oldRegistrarPin, byte[] oldEnrollmentDigest, long oldRegistryFloor,
            SignedExternalGenesis previous, SignedExternalGenesis successor,
            SignedExternalRegistrarRotation rotation,
            byte[] statusSignerPin, long statusFloor,
            byte[] witnessSignerPin, long witnessFloor) {
        final ExternalRegistrarRotationVerdict FAIL =
                ExternalRegistrarRotationVerdict.UNVERIFIABLE_NO_RETRY;
        if (statusRegistry == null || witnessRegistry == null
                || previous == null || successor == null || rotation == null
                || statusSignerPin == null || witnessSignerPin == null
                || statusFloor <= 0 || witnessFloor <= 0) return FAIL;
        try {
            byte[] random = new byte[32];
            new java.security.SecureRandom().nextBytes(random);
            String freshChallenge = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(random);
            SignedExternalRegistrarStatus status = statusRegistry.readStatus(
                    rotation.authorityId, rotation.fromEnrollmentId,
                    rotation.toEnrollmentId, freshChallenge);
            if (status == null) return FAIL;
            SignedExternalRegistrarWitness witness = witnessRegistry.readWitness(
                    rotation.authorityId, rotation.fromEnrollmentId,
                    rotation.toEnrollmentId, freshChallenge);
            return verifyWitnessedRegistrarStatus(oldRegistrarPin,
                    oldEnrollmentDigest, oldRegistryFloor, previous, successor,
                    rotation, status, statusSignerPin, statusFloor,
                    witness, witnessSignerPin, witnessFloor, freshChallenge);
        } catch (Exception unavailableOrInconsistent) {
            return FAIL;
        }
    }

    /**
     * FUTURE ONLY — bounded two-snapshot read-only consistency check.
     * Two independently challenged status/witness pairs detect a revocation
     * or a signed-counter rollback that happens BETWEEN the two reads. Each
     * pair still passes the original pinned-key/floor/nonce verifier.
     * This is NOT an atomic revocation query, cannot protect after the final
     * read, and cannot prove externally independent, nonrollbackable custody.
     * No retry, enrollment, reservation, memory write or provider permission.
     */
    public static ExternalRegistrarRotationVerdict verifyTwoLiveWitnessedRegistrarStatus(
            ExternalRegistrarStatusRegistry statusRegistry,
            ExternalRegistrarRevocationWitnessRegistry witnessRegistry,
            byte[] oldRegistrarPin, byte[] oldEnrollmentDigest, long oldRegistryFloor,
            SignedExternalGenesis previous, SignedExternalGenesis successor,
            SignedExternalRegistrarRotation rotation,
            byte[] statusSignerPin, long statusFloor,
            byte[] witnessSignerPin, long witnessFloor) {
        final ExternalRegistrarRotationVerdict FAIL =
                ExternalRegistrarRotationVerdict.UNVERIFIABLE_NO_RETRY;
        if (statusRegistry == null || witnessRegistry == null) return FAIL;
        try {
            final SignedExternalRegistrarStatus[] statuses =
                    new SignedExternalRegistrarStatus[2];
            final SignedExternalRegistrarWitness[] witnesses =
                    new SignedExternalRegistrarWitness[2];
            for (int i = 0; i < 2; i++) {
                final int round = i;
                ExternalRegistrarStatusRegistry statusRead = (authority, from, to, challenge) -> {
                    statuses[round] = statusRegistry.readStatus(authority, from, to, challenge);
                    return statuses[round];
                };
                ExternalRegistrarRevocationWitnessRegistry witnessRead =
                        (authority, from, to, challenge) -> {
                            witnesses[round] = witnessRegistry.readWitness(authority, from, to, challenge);
                            return witnesses[round];
                        };
                if (verifyLiveWitnessedRegistrarStatus(statusRead, witnessRead,
                        oldRegistrarPin, oldEnrollmentDigest, oldRegistryFloor,
                        previous, successor, rotation,
                        statusSignerPin, statusFloor, witnessSignerPin, witnessFloor)
                        != ExternalRegistrarRotationVerdict.VERIFIED_ROTATION_NO_RETRY)
                    return FAIL;
            }
            if (statuses[0] == null || statuses[1] == null
                    || witnesses[0] == null || witnesses[1] == null
                    || statuses[0].challenge.equals(statuses[1].challenge)
                    || witnesses[0].challenge.equals(witnesses[1].challenge)
                    || statuses[1].statusSequence < statuses[0].statusSequence
                    || witnesses[1].witnessSequence < witnesses[0].witnessSequence
                    || witnesses[1].observedStatusFloor < witnesses[0].observedStatusFloor)
                return FAIL;
            return ExternalRegistrarRotationVerdict.VERIFIED_ROTATION_NO_RETRY;
        } catch (Exception unavailableOrInvalid) {
            return FAIL;
        }
    }

    /**
     * FUTURE ONLY / NOT A PRODUCTION SECURITY BOUNDARY. The app must never
     * provision this authority, pick its pin, or control its monotonic state.
     * Its service and signing key require independently administered custody.
     * A caller-supplied Java implementation does NOT prove that custody.
     */
    public interface IndependentRegistrarFloorRegistry {
        SignedRegistrarFloor readFloor(String authorityId, String fromEnrollmentId,
                String toEnrollmentId, String freshChallenge) throws Exception;
    }

    /** A fresh, signed, read-only view of the operator's monotonic history. */
    public static final class SignedRegistrarFloor {
        public final String authorityId, fromEnrollmentId, toEnrollmentId, challenge;
        public final long floorSequence, minStatusSequence, minWitnessSequence;
        public final boolean revokedEver;
        private final byte[] transitionDigest, signature;
        public SignedRegistrarFloor(String authorityId, String fromEnrollmentId,
                String toEnrollmentId, long floorSequence, long minStatusSequence,
                long minWitnessSequence, boolean revokedEver,
                byte[] transitionDigest, String challenge, byte[] signature) {
            this.authorityId = authorityId;
            this.fromEnrollmentId = fromEnrollmentId;
            this.toEnrollmentId = toEnrollmentId;
            this.floorSequence = floorSequence;
            this.minStatusSequence = minStatusSequence;
            this.minWitnessSequence = minWitnessSequence;
            this.revokedEver = revokedEver;
            this.transitionDigest = transitionDigest == null ? null : transitionDigest.clone();
            this.challenge = challenge;
            this.signature = signature == null ? null : signature.clone();
        }
        public byte[] transitionDigest() {
            return transitionDigest == null ? null : transitionDigest.clone();
        }
        public byte[] signature() {
            return signature == null ? null : signature.clone();
        }
    }

    public static byte[] independentRegistrarFloorPayload(SignedRegistrarFloor r) {
        if (r == null || r.authorityId == null || r.fromEnrollmentId == null
                || r.toEnrollmentId == null || r.challenge == null
                || !r.authorityId.matches("[A-Za-z0-9_-]{8,96}")
                || !r.fromEnrollmentId.matches("[A-Za-z0-9_-]{16,128}")
                || !r.toEnrollmentId.matches("[A-Za-z0-9_-]{16,128}")
                || !r.challenge.matches("[A-Za-z0-9_-]{24,128}")
                || r.floorSequence <= 0 || r.minStatusSequence <= 0
                || r.minWitnessSequence <= 0 || r.transitionDigest == null
                || r.transitionDigest.length != 32) return new byte[0];
        String s = "future-g1/independent-registrar-floor/v1\n"
                + r.authorityId + "\n" + r.fromEnrollmentId + "\n"
                + r.toEnrollmentId + "\n" + r.floorSequence + "\n"
                + r.minStatusSequence + "\n" + r.minWitnessSequence + "\n"
                + (r.revokedEver ? "REVOKED" : "ACTIVE") + "\n"
                + java.util.Base64.getEncoder().encodeToString(r.transitionDigest) + "\n"
                + r.challenge + "\n";
        return s.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Read-only operator-floor check AFTER the bounded two-pair verifier.
     * Accepts no caller-selected floor response or nonce. Externally pinned
     * signer and trusted floor must come from outside all app-restorable domains.
     * A surviving floor detects rollback of only the status/witness domains.
     * Full rollback of the operator floor AND its original private key remains
     * an EXPECTED SECURITY FAIL; two reads do not eliminate TOCTOU afterwards.
     * A positive result is NEVER authorization to mutate/learn/retry/publish.
     */
    public static ExternalRegistrarRotationVerdict verifyTwoLiveWithIndependentFloor(
            ExternalRegistrarStatusRegistry statusRegistry,
            ExternalRegistrarRevocationWitnessRegistry witnessRegistry,
            IndependentRegistrarFloorRegistry floorRegistry,
            byte[] oldRegistrarPin, byte[] oldEnrollmentDigest, long oldRegistryFloor,
            SignedExternalGenesis previous, SignedExternalGenesis successor,
            SignedExternalRegistrarRotation rotation,
            byte[] statusSignerPin, long statusFloor,
            byte[] witnessSignerPin, long witnessFloor,
            byte[] independentlyProvisionedFloorSigner, long trustedFloorSequence) {
        final ExternalRegistrarRotationVerdict FAIL =
                ExternalRegistrarRotationVerdict.UNVERIFIABLE_NO_RETRY;
        if (statusRegistry == null || witnessRegistry == null || floorRegistry == null
                || rotation == null || previous == null || successor == null
                || independentlyProvisionedFloorSigner == null
                || trustedFloorSequence <= 0 || oldRegistrarPin == null
                || statusSignerPin == null || witnessSignerPin == null
                || Arrays.equals(independentlyProvisionedFloorSigner, oldRegistrarPin)
                || Arrays.equals(independentlyProvisionedFloorSigner, rotation.nextSigner())
                || Arrays.equals(independentlyProvisionedFloorSigner, statusSignerPin)
                || Arrays.equals(independentlyProvisionedFloorSigner, witnessSignerPin)
                // A separate process is not a separate trust domain if its
                // signing key is any G1-owned root, audit or CAS identity.
                || Arrays.equals(independentlyProvisionedFloorSigner, previous.rootSigner())
                || Arrays.equals(independentlyProvisionedFloorSigner, previous.auditSigner())
                || Arrays.equals(independentlyProvisionedFloorSigner, previous.casSigner())
                || Arrays.equals(independentlyProvisionedFloorSigner, successor.rootSigner())
                || Arrays.equals(independentlyProvisionedFloorSigner, successor.auditSigner())
                || Arrays.equals(independentlyProvisionedFloorSigner, successor.casSigner()))
            return FAIL;
        try {
            final SignedExternalRegistrarStatus[] lastStatus = {null};
            final SignedExternalRegistrarWitness[] lastWitness = {null};
            final int[] statusCalls = {0}, witnessCalls = {0};
            ExternalRegistrarStatusRegistry statusReader = (a, f, t, challenge) -> {
                if (++statusCalls[0] > 2) throw new IllegalStateException("status limit");
                lastStatus[0] = statusRegistry.readStatus(a, f, t, challenge);
                return lastStatus[0];
            };
            ExternalRegistrarRevocationWitnessRegistry witnessReader = (a, f, t, challenge) -> {
                if (++witnessCalls[0] > 2) throw new IllegalStateException("witness limit");
                lastWitness[0] = witnessRegistry.readWitness(a, f, t, challenge);
                return lastWitness[0];
            };
            if (verifyTwoLiveWitnessedRegistrarStatus(statusReader, witnessReader,
                    oldRegistrarPin, oldEnrollmentDigest, oldRegistryFloor,
                    previous, successor, rotation, statusSignerPin, statusFloor,
                    witnessSignerPin, witnessFloor)
                    != ExternalRegistrarRotationVerdict.VERIFIED_ROTATION_NO_RETRY
                    || statusCalls[0] != 2 || witnessCalls[0] != 2
                    || lastStatus[0] == null || lastWitness[0] == null)
                return FAIL;
            byte[] challengeBytes = new byte[32];
            new java.security.SecureRandom().nextBytes(challengeBytes);
            String challenge = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(challengeBytes);
            SignedRegistrarFloor floor = floorRegistry.readFloor(rotation.authorityId,
                    rotation.fromEnrollmentId, rotation.toEnrollmentId, challenge);
            if (floor == null || !rotation.authorityId.equals(floor.authorityId)
                    || !rotation.fromEnrollmentId.equals(floor.fromEnrollmentId)
                    || !rotation.toEnrollmentId.equals(floor.toEnrollmentId)
                    || !challenge.equals(floor.challenge) || floor.revokedEver
                    || floor.floorSequence < trustedFloorSequence
                    || lastStatus[0].statusSequence < floor.minStatusSequence
                    || lastWitness[0].witnessSequence < floor.minWitnessSequence
                    || lastWitness[0].observedStatusFloor < floor.minStatusSequence)
                return FAIL;
            byte[] expectedTransition = MessageDigest.getInstance("SHA-256")
                    .digest(externalRegistrarRotationPayload(rotation));
            if (expectedTransition.length != 32
                    || !MessageDigest.isEqual(expectedTransition, floor.transitionDigest()))
                return FAIL;
            byte[] payload = independentRegistrarFloorPayload(floor);
            if (payload.length == 0 || floor.signature() == null) return FAIL;
            java.security.PublicKey key = java.security.KeyFactory.getInstance("Ed25519")
                    .generatePublic(new java.security.spec.X509EncodedKeySpec(
                            independentlyProvisionedFloorSigner));
            if (!Arrays.equals(key.getEncoded(), independentlyProvisionedFloorSigner))
                return FAIL;
            java.security.Signature signature = java.security.Signature.getInstance("Ed25519");
            signature.initVerify(key);
            signature.update(payload);
            return signature.verify(floor.signature())
                    ? ExternalRegistrarRotationVerdict.VERIFIED_ROTATION_NO_RETRY : FAIL;
        } catch (Exception untrustedOrUnavailable) {
            return FAIL;
        }
    }

    public enum TwoRotationContinuity {
        VERIFIED_TWO_ROTATIONS_NO_RETRY, UNVERIFIABLE_NO_RETRY
    }

    public static TwoRotationContinuity verifyTwoSignedCheckpointRotations(
            byte[] pinnedRootSigner, byte[] auditSigner, byte[] casSigner,
            String authorityId, long epoch, long generation,
            long externallyTrustedSequence, byte[] externallyTrustedRoot,
            List<AuditRootRecord> externallyTrustedMembers,
            SignedImmutableAuditRootRecovery first, byte[] firstOwner,
            String firstChallenge, SignedImmutableAuditRootRecovery second,
            byte[] secondOwner, String secondChallenge) {
        final TwoRotationContinuity FAIL = TwoRotationContinuity.UNVERIFIABLE_NO_RETRY;
        if (pinnedRootSigner == null || auditSigner == null || casSigner == null
                || Arrays.equals(pinnedRootSigner, auditSigner)
                || Arrays.equals(pinnedRootSigner, casSigner)
                || first == null || second == null || firstOwner == null
                || secondOwner == null || firstChallenge == null
                || secondChallenge == null || firstChallenge.equals(secondChallenge)
                || externallyTrustedMembers == null || externallyTrustedMembers.isEmpty()
                || externallyTrustedRoot == null || externallyTrustedRoot.length != 32
                || !MessageDigest.isEqual(externallyTrustedRoot,
                        immutableAuditRootHash(externallyTrustedMembers)))
            return FAIL;
        try {
            byte[] pin = pinnedRootSigner.clone();
            java.security.PublicKey key = java.security.KeyFactory.getInstance("Ed25519")
                    .generatePublic(new java.security.spec.X509EncodedKeySpec(pin));
            if (!Arrays.equals(pin, key.getEncoded())) return FAIL;
            if (!verifyRotationLink(key, authorityId, epoch, generation,
                    externallyTrustedSequence, externallyTrustedRoot,
                    externallyTrustedMembers, first, firstOwner, firstChallenge))
                return FAIL;
            return verifyRotationLink(key, authorityId, epoch, generation,
                    first.currentSequence, first.currentRoot(), first.records,
                    second, secondOwner, secondChallenge)
                    ? TwoRotationContinuity.VERIFIED_TWO_ROTATIONS_NO_RETRY : FAIL;
        } catch (GeneralSecurityException | RuntimeException invalid) {
            return FAIL;
        }
    }

    private static boolean verifyRotationLink(java.security.PublicKey key,
            String authorityId, long epoch, long generation,
            long priorSequence, byte[] priorRoot, List<AuditRootRecord> prior,
            SignedImmutableAuditRootRecovery current, byte[] owner,
            String challenge) throws GeneralSecurityException {
        if (current == null || prior == null || priorRoot == null
                || !java.util.Objects.equals(authorityId, current.authorityId)
                || epoch != current.epoch || generation != current.generation
                || !java.util.Objects.equals(challenge, current.challenge)
                || current.previousSequence != priorSequence
                || current.sealedSequence <= priorSequence
                || current.currentSequence < current.sealedSequence
                || current.currentSequence <= priorSequence
                || !MessageDigest.isEqual(priorRoot, current.previousRoot()))
            return false;
        byte[] newRoot = current.currentRoot();
        if (newRoot == null || !MessageDigest.isEqual(newRoot,
                immutableAuditRootHash(current.records))
                || !retainsAuditMembers(prior, current.records))
            return false;
        byte[] payload = immutableAuditRecoveryPayload(authorityId,
                epoch, generation, current.claim, current.exactId,
                current.revision, owner, current.sealedSequence,
                priorSequence, priorRoot, current.currentSequence,
                newRoot, challenge);
        if (payload.length == 0 || current.signature() == null) return false;
        java.security.Signature verifier = java.security.Signature.getInstance("Ed25519");
        verifier.initVerify(key);
        verifier.update(payload);
        if (!verifier.verify(current.signature())) return false;
        int found = 0;
        for (AuditRootRecord r : current.records) {
            if (r.claim.equals(current.claim)) {
                if (!r.exactId.equals(current.exactId)
                        || r.revision != current.revision
                        || r.sequence != current.sealedSequence
                        || !MessageDigest.isEqual(r.owner(), owner)) return false;
                found++;
            }
        }
        return found == 1;
    }

    private static boolean retainsAuditMembers(List<AuditRootRecord> before,
            List<AuditRootRecord> after) {
        if (before == null || after == null || after.size() < before.size()
                || immutableAuditRootHash(before).length != 32
                || immutableAuditRootHash(after).length != 32)
            return false;
        int position = 0;
        for (AuditRootRecord previous : before) {
            while (position < after.size()
                    && after.get(position).claim.compareTo(previous.claim) < 0)
                position++;
            if (position >= after.size()) return false;
            AuditRootRecord actual = after.get(position++);
            if (!previous.claim.equals(actual.claim)
                    || !previous.exactId.equals(actual.exactId)
                    || previous.revision != actual.revision
                    || previous.sequence != actual.sequence
                    || !MessageDigest.isEqual(previous.owner(), actual.owner()))
                return false;
        }
        return true;
    }

    /**
     * Independent administrator is responsible for provisioning this already
     * bound capability, its sixth Ed25519 pin and a trusted nonrollbackable
     * sequence minimum. The constructor cannot attest real-world independence
     * of the backing service or that the admin retained the minimum safely.
     * No application/provider/epoch snapshot may replace that authority.
     */
    public static final class ProvisionedGlobalClaimGate {
        private final ProvisionedOneShotGateway provider;
        private final IndependentGlobalClaimSetAuthority claimSet;
        private final byte[] independentPin;
        private final long trustedMinimumSequence;

        private ProvisionedGlobalClaimGate(ProvisionedOneShotGateway provider,
                IndependentGlobalClaimSetAuthority claimSet, byte[] pin,
                long minimum) {
            this.provider = provider;
            this.claimSet = claimSet;
            this.independentPin = pin.clone();
            this.trustedMinimumSequence = minimum;
        }

        public static ProvisionedGlobalClaimGate bindFromExternalAdministrator(
                ProvisionedOneShotGateway provider,
                IndependentGlobalClaimSetAuthority preprovisionedClaimSet,
                byte[] externallyPinnedSixthIdentity,
                long externallyRetainedMinimumSequence) {
            if (provider == null || preprovisionedClaimSet == null
                    || externallyPinnedSixthIdentity == null
                    || externallyRetainedMinimumSequence < 0) return null;
            try {
                byte[] pin = externallyPinnedSixthIdentity.clone();
                java.security.PublicKey key = java.security.KeyFactory
                        .getInstance("Ed25519").generatePublic(
                                new java.security.spec.X509EncodedKeySpec(pin));
                if (!java.util.Arrays.equals(pin, key.getEncoded())) return null;
                for (byte[] existing : provider.pins)
                    if (java.util.Arrays.equals(pin, existing)) return null;
                return new ProvisionedGlobalClaimGate(provider,
                        preprovisionedClaimSet, pin,
                        externallyRetainedMinimumSequence);
            } catch (GeneralSecurityException | RuntimeException invalid) {
                return null;
            }
        }

        private GlobalClaimGateOutcome check(SignedGlobalClaimDecision reply,
                String operation, String claim, String id, long revision,
                byte[] ownerSignature, String nonce) {
            if (reply == null) return GlobalClaimGateOutcome.NO_AUTHORITY;
            if (!java.util.Objects.equals(provider.authorityId, reply.authorityId)
                    || reply.providerEpoch != provider.epoch
                    || !java.util.Objects.equals(claim, reply.claimId)
                    || !java.util.Objects.equals(id, reply.exactId)
                    || reply.sourceRevision != revision
                    || !java.util.Objects.equals(nonce, reply.freshNonce)
                    || !java.util.Objects.equals(operation, reply.operation)
                    || reply.signature() == null || reply.signature().length != 64)
                return GlobalClaimGateOutcome.UNVERIFIABLE;
            if ("RESERVE".equals(operation)
                    && !("FIRST_COMMITTED".equals(reply.state)
                        || "ALREADY_COMMITTED".equals(reply.state)))
                return GlobalClaimGateOutcome.UNVERIFIABLE;
            if ("QUERY".equals(operation)
                    && !("COMMITTED".equals(reply.state)
                        || "ABSENT".equals(reply.state)))
                return GlobalClaimGateOutcome.UNVERIFIABLE;
            byte[] payload = globalClaimDecisionPayload(provider.authorityId,
                    provider.epoch, claim, id, revision, ownerSignature,
                    nonce, operation, reply.state, reply.sequence);
            if (payload.length == 0) return GlobalClaimGateOutcome.UNVERIFIABLE;
            try {
                java.security.Signature signature = java.security.Signature.getInstance("Ed25519");
                signature.initVerify(java.security.KeyFactory.getInstance("Ed25519")
                        .generatePublic(new java.security.spec.X509EncodedKeySpec(independentPin)));
                signature.update(payload);
                if (!signature.verify(reply.signature()))
                    return GlobalClaimGateOutcome.UNVERIFIABLE;
            } catch (GeneralSecurityException | RuntimeException invalid) {
                return GlobalClaimGateOutcome.UNVERIFIABLE;
            }
            // A restored floor may reuse an old sequence. A NEW reservation
            // must strictly advance the separately retained high-water mark.
            if (reply.sequence < trustedMinimumSequence
                    || ("FIRST_COMMITTED".equals(reply.state)
                        && reply.sequence <= trustedMinimumSequence))
                return GlobalClaimGateOutcome.UNVERIFIABLE;
            if ("ABSENT".equals(reply.state))
                return GlobalClaimGateOutcome.REMAIN_INDETERMINATE;
            return "FIRST_COMMITTED".equals(reply.state)
                    ? GlobalClaimGateOutcome.FIRST_RESERVED_PENDING_ACK
                    : GlobalClaimGateOutcome.ALREADY_RESERVED_NO_RETRY;
        }

        /**
         * Exactly one global CAS BEFORE the original provider transaction.
         * After the global CAS begins, a timeout is INDETERMINATE; NEVER retry.
         * An already committed global claim never invokes the provider.
         * Only the separate administrator may advance the trusted minimum.
         */
        public GlobalClaimGateOutcome reserveOnce(String claim, String id,
                long revision, byte[] ownerSignature, String freshNonce) {
            if (!provider.validOwnerClaim(claim, id, revision, ownerSignature)
                    || freshNonce == null
                    || !freshNonce.matches("[A-Za-z0-9_-]{24,128}"))
                return GlobalClaimGateOutcome.UNVERIFIABLE;
            final SignedGlobalClaimDecision reply;
            try {
                reply = claimSet.reserveOnce(provider.authorityId,
                        provider.epoch, claim, id, revision,
                        ownerSignature.clone(), freshNonce);
            } catch (Exception uncertain) {
                return GlobalClaimGateOutcome.STARTED_INDETERMINATE;
            }
            GlobalClaimGateOutcome decision = check(reply, "RESERVE", claim, id,
                    revision, ownerSignature, freshNonce);
            if (decision != GlobalClaimGateOutcome.FIRST_RESERVED_PENDING_ACK)
                return decision;
            // Do not mutate the provider if the global side is unverifiable.
            // Its one-shot write starts only AFTER a validated global first.
            MonotonicReservationOutcome providerOutcome = provider.reserveOnce(
                    claim, id, revision, ownerSignature, freshNonce,
                    0);
            if (providerOutcome == MonotonicReservationOutcome.FIRST_RESERVED_PENDING_ACK)
                return GlobalClaimGateOutcome.FIRST_RESERVED_PENDING_ACK;
            if (providerOutcome == MonotonicReservationOutcome.ALREADY_RESERVED_NO_RETRY)
                return GlobalClaimGateOutcome.ALREADY_RESERVED_NO_RETRY;
            return GlobalClaimGateOutcome.STARTED_INDETERMINATE;
        }

        /** Does not write to either side; never authorizes a new reservation. */
        public GlobalClaimGateOutcome queryExisting(String claim, String id,
                long revision, byte[] ownerSignature, String freshNonce) {
            if (!provider.validOwnerClaim(claim, id, revision, ownerSignature)
                    || freshNonce == null
                    || !freshNonce.matches("[A-Za-z0-9_-]{24,128}"))
                return GlobalClaimGateOutcome.UNVERIFIABLE;
            final SignedGlobalClaimDecision reply;
            try {
                reply = claimSet.queryExisting(provider.authorityId,
                        provider.epoch, claim, id, revision,
                        ownerSignature.clone(), freshNonce);
            } catch (Exception unavailable) {
                return GlobalClaimGateOutcome.REMAIN_INDETERMINATE;
            }
            return check(reply, "QUERY", claim, id, revision,
                    ownerSignature, freshNonce);
        }
    }
}
