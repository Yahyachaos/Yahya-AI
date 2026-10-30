#!/usr/bin/env python3
"""FUTURE ONLY pure Java source contract, no v83 application/CI dependencies."""
from pathlib import Path
import subprocess
import tempfile

source = Path(__file__).with_name('prototypes') / 'CelineFutureMemoryRecall.java'
assert source.is_file(), source
harness = r'''
package de.yahya.ai;
import java.util.*;
public final class FutureMemoryRecallContract {
    static final long NOW = 1_900_000_000_000L;
    static int passed;
    static CelineFutureMemoryRecall.Candidate c(String id, String text) {
        return new CelineFutureMemoryRecall.Candidate(id,text,"",CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,true,true,NOW-100,0,0.5);
    }
    static CelineFutureMemoryRecall.Candidate x(String id, String text,
            String supersedes, boolean allowed, long expiry) {
        return new CelineFutureMemoryRecall.Candidate(id,text,supersedes,CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,allowed,true,NOW-100,expiry,0.5);
    }
    static CelineFutureMemoryRecall.Candidate g1(
            String state, String provenance, boolean userExplicit) {
        return CelineFutureMemoryRecall.Candidate.fromG1Projection(
            "g1-check", "Meine Katze heißt Luna", "", "LOCAL_PRIVATE",
            state, provenance, true, userExplicit, NOW, 0, 1);
    }
    static void eq(Object a, Object b, String label) {
        if (!Objects.equals(a,b)) throw new AssertionError(label+": expected "+b+", got "+a);
        passed++;
    }
    static String top(String query, List<CelineFutureMemoryRecall.Candidate> entries) {
        List<CelineFutureMemoryRecall.Match> result = Fixture.retrieve(query,entries,8,NOW);
        return result.isEmpty()?"":result.get(0).candidate.id;
    }
    // The historical 372 contract cases test LINEAGE POLICY under a hypothetical
    // trusted owner. This is deliberately NOT an authentication guarantee.
    static final class Fixture {
        private static final byte[] TEST_ONLY_SECRET = new byte[32];
        private static final String TEST_ONLY_GENERATION = "fixture-source-snapshot-1";
        private static final CelineFutureMemoryRecall.OwnerLineageVerifier VERIFIED;
        static {
            Arrays.fill(TEST_ONLY_SECRET, (byte)0x29);
            VERIFIED = new CelineFutureMemoryRecall.OwnerLineageVerifier(
                TEST_ONLY_SECRET, TEST_ONLY_GENERATION);
        }
        private static CelineFutureMemoryRecall.OwnerLineageCertificate sourceFixture(
                List<CelineFutureMemoryRecall.Candidate> c,
                Set<String> retired, Set<String> confirmed) {
            byte[] payload = CelineFutureMemoryRecall.certificatePayload(
                TEST_ONLY_GENERATION,c,retired,confirmed);
            if (payload.length == 0) return null;
            try {
                javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
                mac.init(new javax.crypto.spec.SecretKeySpec(TEST_ONLY_SECRET,"HmacSHA256"));
                return new CelineFutureMemoryRecall.OwnerLineageCertificate(
                    TEST_ONLY_GENERATION,retired,confirmed,mac.doFinal(payload));
            } catch (java.security.GeneralSecurityException ex) {
                return null;
            }
        }
        static List<CelineFutureMemoryRecall.Match> retrieve(String q,
                List<CelineFutureMemoryRecall.Candidate> c, int max, long now) {
            return CelineFutureMemoryRecall.retrieve(q,c,max,now);
        }
        static List<CelineFutureMemoryRecall.Match> retrieve(String q,
                List<CelineFutureMemoryRecall.Candidate> c, Set<String> retired,
                int max, long now) {
            return CelineFutureMemoryRecall.retrieve(q,c,retired,max,now);
        }
        static List<CelineFutureMemoryRecall.Match> retrieve(String q,
                List<CelineFutureMemoryRecall.Candidate> c, Set<String> retired,
                Set<String> certified, int max, long now) {
            return CelineFutureMemoryRecall.retrieveCertified(q,c,
                sourceFixture(c,retired,certified),VERIFIED,max,now);
        }
        static String correct(String q, List<CelineFutureMemoryRecall.Candidate> c, long now) {
            return CelineFutureMemoryRecall.unambiguousCorrectionTarget(q,c,now);
        }
        static String correct(String q, List<CelineFutureMemoryRecall.Candidate> c,
                Set<String> retired, long now) {
            return CelineFutureMemoryRecall.unambiguousCorrectionTarget(q,c,retired,now);
        }
        static String correct(String q, List<CelineFutureMemoryRecall.Candidate> c,
                Set<String> retired, Set<String> certified, long now) {
            return CelineFutureMemoryRecall.unambiguousCorrectionTargetCertified(q,c,
                sourceFixture(c,retired,certified),VERIFIED,now);
        }
    }
    /** Disk-backed TEST FIXTURE, not G1/Android secure storage or crash-safe production I/O. */
    static final class DiskJournal implements CelineFutureMemoryRecall.OwnerPublicationJournal {
        final java.nio.file.Path directory;
        boolean crashAfterReserve, crashBeforeReserve, crashBeforeAck;
        // Deterministic TEST-ONLY race barrier after two independent instances
        // observed no existing reservation. Never part of a product owner.
        static volatile java.util.concurrent.CountDownLatch bothMissing, releaseMissing;
        int actualReserves, actualAcknowledgements;
        DiskJournal(java.nio.file.Path directory) throws Exception {
            this.directory = directory;
            java.nio.file.Files.createDirectories(directory);
        }
        private java.nio.file.Path entry(String id) {
            try {
                byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                        .digest(id.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                return directory.resolve(java.util.HexFormat.of().formatHex(digest) + ".journal");
            } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        }
        private static String digest(byte[] bytes) {
            return java.util.HexFormat.of().formatHex(bytes);
        }
        private static void persist(java.nio.file.Path to, String text) throws Exception {
            java.nio.file.Path tmp = to.resolveSibling(to.getFileName().toString() + ".pending-" +
                    java.util.UUID.randomUUID());
            try (java.nio.channels.FileChannel ch = java.nio.channels.FileChannel.open(tmp,
                    java.nio.file.StandardOpenOption.CREATE_NEW,
                    java.nio.file.StandardOpenOption.WRITE)) {
                java.nio.ByteBuffer buf = java.nio.charset.StandardCharsets.UTF_8.encode(text);
                while (buf.hasRemaining()) ch.write(buf);
                ch.force(true);
            }
            java.nio.file.Files.move(tmp,to,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        @Override public synchronized Reservation reserve(String id, byte[] sha256) {
            if (crashBeforeReserve) throw new IllegalStateException("journal unavailable");
            try {
                java.nio.file.Path entry = entry(id);
                String intended = digest(sha256);
                if (java.nio.file.Files.exists(entry)) {
                    String[] fields = java.nio.file.Files.readString(entry).split(" ",2);
                    if (fields.length != 2 || !fields[1].equals(intended)
                            || !(fields[0].equals("STARTED") || fields[0].equals("COMPLETED")))
                        return Reservation.INTENT_CONFLICT;
                    return fields[0].equals("COMPLETED")
                        ? Reservation.ALREADY_COMPLETED : Reservation.ALREADY_STARTED;
                }
                if (bothMissing != null) {
                    bothMissing.countDown();
                    if (!releaseMissing.await(5,java.util.concurrent.TimeUnit.SECONDS))
                        throw new IllegalStateException("test-only claim barrier timed out");
                }
                // Atomic exclusive creation is the journal's reservation
                // linearization point. REPLACE_EXISTING could otherwise let two
                // separate DiskJournal instances both claim NEW_STARTED.
                // A crash leaving a partial created file fails closed on read.
                try (java.nio.channels.FileChannel ch = java.nio.channels.FileChannel.open(entry,
                        java.nio.file.StandardOpenOption.CREATE_NEW,
                        java.nio.file.StandardOpenOption.WRITE)) {
                    java.nio.ByteBuffer buf = java.nio.charset.StandardCharsets.UTF_8.encode(
                            "STARTED " + intended);
                    while (buf.hasRemaining()) ch.write(buf);
                    ch.force(true);
                }
                actualReserves++;
                if (crashAfterReserve) throw new IllegalStateException("crash after durable STARTED");
                return Reservation.NEW_STARTED;
            } catch (Exception ex) { throw new IllegalStateException(ex); }
        }
        @Override public synchronized boolean acknowledgeCompleted(String id, byte[] sha256) {
            try {
                java.nio.file.Path entry = entry(id);
                if (!java.nio.file.Files.exists(entry)) return false;
                String intended = digest(sha256);
                String before = java.nio.file.Files.readString(entry);
                if (!before.equals("STARTED " + intended)) return false;
                if (crashBeforeAck) throw new IllegalStateException("crash after publisher before ack");
                persist(entry,"COMPLETED " + intended);
                actualAcknowledgements++;
                return true;
            } catch (Exception ex) { throw new IllegalStateException(ex); }
        }
        @Override public synchronized boolean acknowledgeCompletedIfCurrent(
                String id, byte[] sha256, String certifiedGeneration) {
            // JVM FIXTURE ONLY: the tests' ordinary valid-revision scenarios
            // assume a true owner-held read lease. This disk fixture itself
            // cannot enforce an atomic G1 revision + journal transaction.
            return certifiedGeneration != null && !certifiedGeneration.isEmpty()
                    && acknowledgeCompleted(id,sha256);
        }
    }

    /** FUTURE-ONLY test fixture. The AtomicLong stands in for an independently
     * protected monotonic owner anchor; it is NOT an AndroidKeyStore or G1 proof.
     * The separate witness file cannot prevent rollback if the anchor itself
     * is restored. Serialization is scoped to one simulated owner anchor. */
    static final class WitnessedDiskJournal implements CelineFutureMemoryRecall.OwnerPublicationJournal {
        final DiskJournal disk;
        final java.nio.file.Path witness;
        final java.util.concurrent.atomic.AtomicLong anchor;
        final java.util.concurrent.atomic.AtomicReference<String> protectedCommitment;
        // Deterministic JVM failpoints only: model an abrupt interruption at each
        // on-disk/anchor boundary; these do NOT establish real power-loss durability.
        enum FaultPoint { NONE, RESERVE_WITNESS_FILE, RESERVE_COMMITMENT,
            RESERVE_ANCHOR, ACK_JOURNAL, ACK_WITNESS_FILE,
            ACK_COMMITMENT, ACK_ANCHOR }
        FaultPoint faultPoint = FaultPoint.NONE;
        void trip(FaultPoint at) {
            if (faultPoint == at) throw new IllegalStateException("simulated crash at "+at);
        }
        WitnessedDiskJournal(java.nio.file.Path journalDir, java.nio.file.Path witnessDir,
                java.util.concurrent.atomic.AtomicLong protectedAnchor,
                java.util.concurrent.atomic.AtomicReference<String> protectedStateHash) throws Exception {
            disk = new DiskJournal(journalDir);
            java.nio.file.Files.createDirectories(witnessDir);
            witness = witnessDir.resolve("owner.witness");
            anchor = protectedAnchor;
            protectedCommitment = protectedStateHash;
        }
        static String hash(String text) throws Exception {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        String[] read() throws Exception {
            if (!java.nio.file.Files.exists(witness)) return null;
            String raw = java.nio.file.Files.readString(witness);
            if (!java.util.Objects.equals(hash(raw),protectedCommitment.get()))
                throw new IllegalStateException("witness disagrees with independent owner commitment");
            String[] fields = raw.split(" ",-1);
            if (fields.length != 4 || !(fields[1].equals("STARTED") || fields[1].equals("COMPLETED"))
                    || !fields[2].matches("[0-9a-f]{64}") || !fields[3].matches("[0-9a-f]{64}"))
                throw new IllegalStateException("invalid independent witness");
            return fields;
        }
        void write(long epoch, String state, String requestId, byte[] digest) throws Exception {
            String raw = epoch+" "+state+" "+hash(requestId)+" "+
                java.util.HexFormat.of().formatHex(digest);
            DiskJournal.persist(witness,raw);
            trip(state.equals("STARTED") ? FaultPoint.RESERVE_WITNESS_FILE
                : FaultPoint.ACK_WITNESS_FILE);
            // This is a TEST-ONLY stand-in for independent authenticated state.
            // A plain counter alone cannot detect same-epoch completion forgery.
            protectedCommitment.set(hash(raw));
            trip(state.equals("STARTED") ? FaultPoint.RESERVE_COMMITMENT
                : FaultPoint.ACK_COMMITMENT);
        }
        @Override public Reservation reserve(String id, byte[] digest) {
            synchronized (anchor) {
                try {
                    String[] fields = read();
                    long anchored = anchor.get();
                    if (fields == null) {
                        if (anchored != 0 || java.nio.file.Files.exists(disk.entry(id)))
                            return Reservation.INTENT_CONFLICT;
                        // Witness FIRST; the journal may fail afterwards. In that
                        // case a retry is indeterminate, never a new publication.
                        write(1,"STARTED",id,digest);
                        anchor.set(1);
                        trip(FaultPoint.RESERVE_ANCHOR);
                        return disk.reserve(id,digest);
                    }
                    if (Long.parseLong(fields[0]) != anchored || anchored <= 0
                        || !fields[2].equals(hash(id))
                        || !fields[3].equals(java.util.HexFormat.of().formatHex(digest)))
                        return Reservation.INTENT_CONFLICT;
                    // A deleted journal does not authorize a fresh reservation.
                    if (!java.nio.file.Files.exists(disk.entry(id))) return Reservation.ALREADY_STARTED;
                    Reservation stored = disk.reserve(id,digest);
                    if (stored == Reservation.INTENT_CONFLICT || stored == Reservation.NEW_STARTED)
                        return Reservation.INTENT_CONFLICT;
                    return fields[1].equals("COMPLETED") && stored == Reservation.ALREADY_COMPLETED
                        ? Reservation.ALREADY_COMPLETED : Reservation.ALREADY_STARTED;
                } catch (Exception ex) { return Reservation.INTENT_CONFLICT; }
            }
        }
        @Override public boolean acknowledgeCompleted(String id, byte[] digest) {
            synchronized (anchor) {
                try {
                    String[] fields = read();
                    if (fields == null || !fields[1].equals("STARTED")
                        || Long.parseLong(fields[0]) != anchor.get()
                        || !fields[2].equals(hash(id))
                        || !fields[3].equals(java.util.HexFormat.of().formatHex(digest))) return false;
                    if (!disk.acknowledgeCompleted(id,digest)) return false;
                    trip(FaultPoint.ACK_JOURNAL);
                    long next = Math.addExact(anchor.get(),1);
                    write(next,"COMPLETED",id,digest);
                    anchor.set(next);
                    trip(FaultPoint.ACK_ANCHOR);
                    return true;
                } catch (Exception ex) { throw new IllegalStateException("witness ack indeterminate",ex); }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        List<CelineFutureMemoryRecall.Candidate> data = Arrays.asList(
            c("beverage","Mein Lieblingsgetränk ist Tee"),
            c("cat","Meine Katze heißt Luna"),
            c("work","Der Damu Markt hat Getränke"));
        eq(top("Was ist mein Lieblingsgetränk?",data),"beverage","german umlaut retrieval");
        eq(top("Wie heißt meine Katze?",data),"cat","name normalization");
        eq(top("Damu Markt Getränke",data),"work","plural alias with store anchor");
        eq(top("Wo bekommt man Getränke?",data),"","abstain on insufficient anchor");
        eq(top("Darf ich erfahren was du über mich weißt?",data),"","no irrelevant query hits");
        eq(top("",data),"","empty query");
        eq(top("  und   der    zum",data),"","stopword-only query");
        eq(top("PASSWORT",Arrays.asList(x("secret","Mein Passwort ist xyz","",false,0))),"","permission gate");
        eq(top("Passwort",Arrays.asList(new CelineFutureMemoryRecall.Candidate("sensitive",
            "Mein Passwort ist xyz","",CelineFutureMemoryRecall.Privacy.LOCAL_SENSITIVE,true,true,NOW,0,1))),
            "","sensitive never returned even if caller marked authorized");
        eq(top("Katze",Arrays.asList(new CelineFutureMemoryRecall.Candidate("share",
            "Meine Katze Luna","",CelineFutureMemoryRecall.Privacy.SHAREABLE_ON_REQUEST,true,true,NOW,0,1))),
            "","shareable memory excluded until separate consent implementation");
        eq(top("Katze",Arrays.asList(x("old","Meine Katze heißt Paul","",true,0),
            new CelineFutureMemoryRecall.Candidate("sensitive-new","Meine Katze heißt Luna","old",
            CelineFutureMemoryRecall.Privacy.LOCAL_SENSITIVE,true,true,NOW,0,1))),
            "","sensitive successor must suppress old public memory");
        eq(top("Meine Katze",Arrays.asList(x("expired","Meine Katze ist alt","",true,NOW-1))),"","expiry");
        eq(top("Meine Katze",Arrays.asList(x("old","Meine Katze heißt Paul","",true,0),
                                            x("new","Meine Katze heißt Luna","old",true,0))),"new","supersession");
        eq(Fixture.correct("Meine Katze heißt nicht Luna",data,NOW),
            "cat","unique correction suggestion");
        eq(Fixture.correct("Meine Katze",Arrays.asList(
            c("a","Meine Katze frisst gerne"),c("b","Meine Katze heißt Luna")),NOW),
            "","ambiguous correction fail closed");
        // FUTURE-ONLY: an inaccessible or untrusted memory can still be the
        // object the user wants to correct. Provider-safe retrieval must not
        // treat invisibility as proof that another record is unambiguous.
        CelineFutureMemoryRecall.Candidate visibleCorrection = c("correct-visible", "Meine Katze heisst Luna");
        CelineFutureMemoryRecall.Candidate hiddenSensitive = new CelineFutureMemoryRecall.Candidate(
            "correct-sensitive", "Meine Katze heisst Luna", "",
            CelineFutureMemoryRecall.Privacy.LOCAL_SENSITIVE, false, true, NOW-100, 0, 0.8);
        CelineFutureMemoryRecall.Candidate hiddenDenied = new CelineFutureMemoryRecall.Candidate(
            "correct-denied", "Meine Katze heisst Luna", "",
            CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE, false, true, NOW-100, 0, 0.8);
        CelineFutureMemoryRecall.Candidate hiddenInferred = new CelineFutureMemoryRecall.Candidate(
            "correct-inferred", "Meine Katze heisst Luna", "",
            CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE, true, false, NOW-100, 0, 0.8);
        CelineFutureMemoryRecall.Candidate hiddenShareable = new CelineFutureMemoryRecall.Candidate(
            "correct-shareable", "Meine Katze heisst Luna", "",
            CelineFutureMemoryRecall.Privacy.SHAREABLE_ON_REQUEST, true, true, NOW-100, 0, 0.8);
        String fullCorrection = "Meine Katze heisst Luna";
        eq(Fixture.correct(fullCorrection,
            Arrays.asList(visibleCorrection,hiddenSensitive),NOW), "",
            "hidden sensitive equally relevant correction rival must abstain");
        eq(Fixture.correct(fullCorrection,
            Arrays.asList(visibleCorrection,hiddenDenied),NOW), "",
            "permission-withheld equally relevant correction rival must abstain");
        eq(Fixture.correct(fullCorrection,
            Arrays.asList(visibleCorrection,hiddenInferred),NOW), "",
            "untrusted equally relevant correction rival must abstain");
        eq(Fixture.correct(fullCorrection,
            Arrays.asList(visibleCorrection,hiddenShareable),NOW), "",
            "shareable-on-request equally relevant correction rival must abstain");
        eq(Fixture.correct(fullCorrection,
            Arrays.asList(hiddenSensitive,visibleCorrection),NOW), "",
            "inaccessible ambiguity independent of projection order");
        eq(Fixture.correct(fullCorrection,
            Arrays.asList(visibleCorrection, new CelineFutureMemoryRecall.Candidate(
                "long-hidden", "Meine Katze heisst Luna und mag Milch", "",
                CelineFutureMemoryRecall.Privacy.LOCAL_SENSITIVE,false,true,NOW-100,0,0.8)),NOW), "",
            "hidden rival with all three query anchors prevents auto-selection");
        eq(Fixture.correct(fullCorrection,
            Arrays.asList(visibleCorrection, new CelineFutureMemoryRecall.Candidate(
                "partial-hidden", "Meine Katze", "",
                CelineFutureMemoryRecall.Privacy.LOCAL_SENSITIVE,false,true,NOW-100,0,0.8)),NOW),
            "correct-visible", "weak two-token hidden rival does not defeat three-token evidence");
        eq(Fixture.correct(fullCorrection,
            Arrays.asList(visibleCorrection, new CelineFutureMemoryRecall.Candidate(
                "unrelated-hidden", "Damu Markt verkauft Getränke", "",
                CelineFutureMemoryRecall.Privacy.LOCAL_SENSITIVE,false,true,NOW-100,0,0.8)),NOW),
            "correct-visible", "unrelated confidential record does not suppress a correction");
        eq(Fixture.correct(fullCorrection,
            Arrays.asList(visibleCorrection, new CelineFutureMemoryRecall.Candidate(
                "expired-hidden", "Meine Katze heisst Luna", "",
                CelineFutureMemoryRecall.Privacy.LOCAL_SENSITIVE,false,true,NOW-100,NOW-1,0.8)),NOW),
            "correct-visible", "expired inaccessible rival does not defeat live correction");
        eq(top(fullCorrection,Arrays.asList(visibleCorrection,hiddenSensitive)),
            "correct-visible", "ordinary provider-safe retrieval stays permission-filtered");
        eq(Fixture.correct(fullCorrection,
            Arrays.asList(visibleCorrection),NOW), "correct-visible",
            "isolated three-token fact still yields an unambiguous correction ID");
        // A G1 record may be privacy-redacted or malformed before projection.
        // The correction selector cannot prove its topic is unrelated merely
        // because no lexical summary is available. Retain source-ID UI fallback.
        CelineFutureMemoryRecall.Candidate absentSensitiveSummary =
            new CelineFutureMemoryRecall.Candidate("redacted-live", "", "",
                CelineFutureMemoryRecall.Privacy.LOCAL_SENSITIVE, false, true,
                NOW-100, 0, 0.8);
        CelineFutureMemoryRecall.Candidate corruptSensitiveSummary =
            new CelineFutureMemoryRecall.Candidate("corrupt-live", "Meine Katze heisst Lu\u200Bna", "",
                CelineFutureMemoryRecall.Privacy.LOCAL_SENSITIVE, false, true,
                NOW-100, 0, 0.8);
        CelineFutureMemoryRecall.Candidate expiredAbsentSummary =
            new CelineFutureMemoryRecall.Candidate("redacted-expired", "", "",
                CelineFutureMemoryRecall.Privacy.LOCAL_SENSITIVE, false, true,
                NOW-100, NOW-1, 0.8);
        CelineFutureMemoryRecall.Candidate futureAbsentSummary =
            new CelineFutureMemoryRecall.Candidate("redacted-future", "", "",
                CelineFutureMemoryRecall.Privacy.LOCAL_SENSITIVE, false, true,
                NOW+1000, 0, 0.8);
        CelineFutureMemoryRecall.Candidate invalidImportance =
            new CelineFutureMemoryRecall.Candidate("invalid-importance", fullCorrection, "",
                CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE, false, true,
                NOW-100, 0, Double.NaN);
        eq(Fixture.correct(fullCorrection,
            Arrays.asList(visibleCorrection,absentSensitiveSummary),NOW), "",
            "redacted live memory with missing summary prevents speculative correction");
        eq(Fixture.correct(fullCorrection,
            Arrays.asList(visibleCorrection,corruptSensitiveSummary),NOW), "",
            "corrupted live hidden memory prevents speculative correction");
        eq(Fixture.correct(fullCorrection,
            Arrays.asList(visibleCorrection,invalidImportance),NOW), "",
            "malformed importance must not make a competing correction target disappear");
        eq(Fixture.correct(fullCorrection,
            Arrays.asList(visibleCorrection,expiredAbsentSummary),NOW), "correct-visible",
            "expired redacted memory does not suppress active correction evidence");
        eq(Fixture.correct(fullCorrection,
            Arrays.asList(visibleCorrection,futureAbsentSummary),NOW), "correct-visible",
            "not-yet-created redacted memory does not suppress correction evidence");
        eq(top(fullCorrection,Arrays.asList(visibleCorrection,absentSensitiveSummary)),
            "correct-visible", "privacy-filtered retrieval unchanged by absent-summary correction guard");
        eq(Fixture.correct("Tee",data,NOW),"","single-token correction fail closed");
        eq(Fixture.correct("Mein Lieblingsgetränk ist Kaffee",data,NOW),
            "","correction without discriminating target must request ID");
        eq(Fixture.retrieve("Katze",data,0,NOW).size(),0,"zero result bound");
        eq(top("Katze",Arrays.asList(c("b","Katze B"),c("a","Katze A"))),"a","deterministic tie");
        eq(Fixture.retrieve("Katze",data,100,NOW).size(),1,"bounded result");
        List<CelineFutureMemoryRecall.Candidate> capacity = new ArrayList<>();
        capacity.add(x("old-capacity", "Meine Katze heißt Paul", "", true, 0));
        for (int i=1; i<2048; i++) capacity.add(c("noise-"+i, "Anderes Thema Nummer "+i));
        eq(top("Meine Katze", capacity),"old-capacity","maximum allowed capacity remains searchable");
        capacity.add(new CelineFutureMemoryRecall.Candidate("late-private-successor",
                "Meine Katze heißt Luna", "old-capacity",
                CelineFutureMemoryRecall.Privacy.LOCAL_SENSITIVE, true, true, NOW, 0, 1));
        eq(top("Meine Katze", capacity),"", "overflow privacy must fail closed");
        eq(Fixture.correct("Meine Katze heißt Luna", capacity, NOW),
                "", "overflow correction suggestion must fail closed");
        // Future-only monotonic correction policy: a supplied expired successor still
        // suppresses its predecessor; expiration must never resurrect obsolete facts.
        List<CelineFutureMemoryRecall.Candidate> expiredSuccessor = Arrays.asList(
            x("old-cat", "Meine Katze heißt Paul", "", true, 0),
            x("expired-new-cat", "Meine Katze heißt Luna", "old-cat", true, NOW-1));
        eq(top("Meine Katze heißt", expiredSuccessor), "", "expired successor cannot resurrect old");
        eq(Fixture.correct("Meine Katze heißt Paul",expiredSuccessor,NOW),
            "", "correction target cannot select stale ancestor");
        List<CelineFutureMemoryRecall.Candidate> expiredSensitive = Arrays.asList(
            x("old-secret", "Mein Passwort lautet frei", "", true, 0),
            new CelineFutureMemoryRecall.Candidate("private-new", "Mein Passwort lautet geheim",
                "old-secret", CelineFutureMemoryRecall.Privacy.LOCAL_SENSITIVE, false, true, NOW, NOW-1, 1));
        eq(top("Passwort lautet",expiredSensitive),"","expired private successor still suppresses old");
        // Only the information actually supplied by the owner may be used for suppression.
        eq(top("Meine Katze heißt",Arrays.asList(x("old-cat","Meine Katze heißt Paul","",true,0))),
            "old-cat","no inferred tombstone if projection lacks successor");
        // Duplicate IDs from a contradictory projection must not select whichever
        // entry happened to appear first, especially when privacy differs.
        List<CelineFutureMemoryRecall.Candidate> duplicateId = Arrays.asList(
            x("same-id", "Meine Katze heißt Paul", "", true, 0),
            new CelineFutureMemoryRecall.Candidate("same-id", "Meine Katze heißt Luna", "",
                CelineFutureMemoryRecall.Privacy.LOCAL_SENSITIVE, false, true, NOW, 0, 1));
        eq(top("Meine Katze heißt",duplicateId),"","duplicate ID cannot expose earlier public projection");
        eq(Fixture.correct("Meine Katze heißt Paul",duplicateId,NOW),
                "","duplicate ID correction must abstain");
        eq(top("Meine Katze heißt",Arrays.asList(
            x("same-id", "Meine Katze heißt Paul", "", true, 0),
            x("same-id", "Meine Katze heißt Luna", "", true, NOW-1))),
            "", "expired duplicate ID also fails closed");
        // G1 may consolidate an expired successor away. An authoritative,
        // separately retained retraction ID prevents stale-memory resurrection.
        List<CelineFutureMemoryRecall.Candidate> prunedProjection = Arrays.asList(
            x("retired-cat", "Meine Katze heißt Paul", "", true, 0),
            c("valid-work", "Damu Markt verkauft Getränke"));
        Set<String> retired = Collections.singleton("retired-cat");
        eq(Fixture.retrieve("Meine Katze heißt",prunedProjection,retired,8,NOW).size(),
            0,"explicit retraction suppresses old record without successor projection");
        eq(Fixture.correct(
            "Meine Katze heißt Paul",prunedProjection,retired,NOW),
            "","explicit retraction prevents stale correction suggestion");
        eq(Fixture.retrieve("Damu Markt Getränke",prunedProjection,retired,8,NOW)
            .get(0).candidate.id,"valid-work","unrelated candidate remains available");
        eq(Fixture.retrieve("Damu Markt Getränke",prunedProjection,null,8,NOW).size(),
            0,"missing authoritative retraction set fails closed");
        // Exact scope strings verified against v83 Room CelineBrain.java; this
        // fixture stays FUTURE ONLY and never reads protected Android classes.
        CelineFutureMemoryRecall.Candidate mappedPrivate =
            CelineFutureMemoryRecall.Candidate.fromG1Projection(
                "g1-private", "Meine Katze heißt Luna", "", "LOCAL_PRIVATE", "EXPLICIT", "user_explicit", true,
                true, NOW, 0, 1);
        eq(top("Meine Katze heißt",Arrays.asList(mappedPrivate)),"g1-private",
                "G1 LOCAL_PRIVATE explicit provider authorization");
        eq(top("Meine Katze heißt",Arrays.asList(
            CelineFutureMemoryRecall.Candidate.fromG1Projection(
                "g1-unapproved", "Meine Katze heißt Luna", "", "LOCAL_PRIVATE", "EXPLICIT", "user_explicit", false,
                true, NOW, 0, 1))),"","G1 private without permission blocked");
        eq(top("Meine Katze heißt",Arrays.asList(
            CelineFutureMemoryRecall.Candidate.fromG1Projection(
                "g1-sensitive", "Meine Katze heißt Luna", "", "LOCAL_SENSITIVE", "EXPLICIT", "user_explicit", true,
                true, NOW, 0, 1))),"","G1 LOCAL_SENSITIVE never exported");
        eq(top("Meine Katze heißt",Arrays.asList(
            CelineFutureMemoryRecall.Candidate.fromG1Projection(
                "g1-shareable", "Meine Katze heißt Luna", "", "SHAREABLE_ON_REQUEST", "EXPLICIT", "user_explicit", true,
                true, NOW, 0, 1))),"","G1 SHAREABLE requires future explicit adapter");
        eq(top("Meine Katze heißt",Arrays.asList(
            x("g1-old", "Meine Katze heißt Paul", "", true, 0),
            CelineFutureMemoryRecall.Candidate.fromG1Projection(
                "g1-unknown-new", "Meine Katze heißt Luna", "g1-old", "UNRECOGNIZED", "EXPLICIT", "user_explicit", true,
                true, NOW, 0, 1))),"","unknown G1 privacy still suppresses old fact");
        eq(top("Meine Katze heißt",Arrays.asList(
            CelineFutureMemoryRecall.Candidate.fromG1Projection(
                "g1-lowercase", "Meine Katze heißt Luna", "", "local_private", "EXPLICIT", "user_explicit", true,
                true, NOW, 0, 1))),"","privacy enum mapping case-exact and fail closed");
        // Source-grounded G1 states EXPLICIT/OBSERVED/INFERRED/UNKNOWN.
        // Provenance is an open string; only two first-party direct-user
        // source tags are trusted for this FUTURE-ONLY experiment.
        eq(top("Meine Katze heißt",Arrays.asList(
            CelineFutureMemoryRecall.Candidate.fromG1Projection(
                "g1-corrected","Meine Katze heißt Luna","","LOCAL_PRIVATE",
                "EXPLICIT","user_correction",true,true,NOW,0,1))),"g1-corrected",
                "explicit correction provenance with independent permission");
        eq(top("Meine Katze heißt",Arrays.asList(
            CelineFutureMemoryRecall.Candidate.fromG1Projection(
                "g1-legacy","Meine Katze heißt Luna","","LOCAL_PRIVATE",
                true,true,NOW,0,1))),"","metadata-free adapter never authorizes");
        eq(top("Meine Katze heißt",Arrays.asList(g1("INFERRED","inferred",true))),"",
                "G1 inferred never asserted as verified user fact");
        eq(top("Meine Katze heißt",Arrays.asList(g1("UNKNOWN","user_explicit",true))),"",
                "unknown knowledge state blocked");
        eq(top("Meine Katze heißt",Arrays.asList(g1("OBSERVED","user_explicit",true))),"",
                "observed not equivalent to explicit user origin");
        eq(top("Meine Katze heißt",Arrays.asList(g1("EXPLICIT","inferred",true))),"",
                "contradictory explicit inferred provenance");
        eq(top("Meine Katze heißt",Arrays.asList(g1("EXPLICIT","external_model",true))),"",
                "untrusted external provenance");
        eq(top("Meine Katze heißt",Arrays.asList(g1("EXPLICIT","User_Explicit",true))),"",
                "provenance exact-case trust");
        eq(top("Meine Katze heißt",Arrays.asList(g1("EXPLICIT","",true))),"",
                "empty provenance blocked");
        eq(top("Meine Katze heißt",Arrays.asList(g1("EXPLICIT",null,true))),"",
                "null provenance blocked");
        eq(top("Meine Katze heißt",Arrays.asList(g1(null,"user_explicit",true))),"",
                "null state blocked");
        eq(top("Meine Katze heißt",Arrays.asList(g1("explicit","user_explicit",true))),"",
                "lowercase state unrecognized");
        eq(top("Meine Katze heißt",Arrays.asList(g1("EXPLICIT","user_explicit",false))),"",
                "explicit origin flag is required");
        List<CelineFutureMemoryRecall.Candidate> uncertainSuccessor = Arrays.asList(
            x("old-verified","Meine Katze heißt Paul","",true,0),
            CelineFutureMemoryRecall.Candidate.fromG1Projection(
                "uncertain-new","Meine Katze heißt Luna","old-verified",
                "LOCAL_PRIVATE","INFERRED","inferred",true,false,NOW,0,1));
        eq(top("Meine Katze heißt",uncertainSuccessor),"",
                "withheld inferred successor still suppresses old fact");
        eq(Fixture.correct(
            "Meine Katze heißt Paul",uncertainSuccessor,NOW),"",
                "withheld successor cannot select old correction target");
        // The actual G1 item has conflictWithId in addition to supersedesId.
        // Unlike a confirmed supersession, an unresolved conflict does not
        // authorize choosing a winner based on timestamp or importance.
        CelineFutureMemoryRecall.Candidate trustedConflict =
            new CelineFutureMemoryRecall.Candidate("new-conflict", "Meine Katze heißt Luna", "",
                "old-conflict", CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,
                true, true, NOW, 0, 1);
        CelineFutureMemoryRecall.Candidate trustedOld =
            c("old-conflict", "Meine Katze heißt Paul");
        eq(top("Meine Katze heißt", Arrays.asList(trustedOld, trustedConflict)),"",
                "unresolved conflict withholds both candidate values");
        eq(top("Meine Katze heißt", Arrays.asList(trustedConflict, trustedOld)),"",
                "conflict resolution independent of projection order");
        eq(Fixture.correct("Meine Katze heißt Paul",
                Arrays.asList(trustedOld, trustedConflict), NOW),"",
                "no unreviewed conflict auto-correction");
        eq(top("Meine Katze heißt", Arrays.asList(trustedConflict)),"",
                "missing conflict opponent means fail closed");
        eq(top("Meine Katze heißt", Arrays.asList(trustedOld,
                new CelineFutureMemoryRecall.Candidate("expired-conflict", "Meine Katze heißt Luna", "",
                    "old-conflict", CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,
                    true, true, NOW, NOW-1, 1))),"",
                "expired conflict metadata still withholds old fact");
        eq(top("Meine Katze heißt", Arrays.asList(trustedOld,
                new CelineFutureMemoryRecall.Candidate("private-conflict", "Meine Katze heißt Luna", "",
                    "old-conflict", CelineFutureMemoryRecall.Privacy.LOCAL_SENSITIVE,
                    false, true, NOW, 0, 1))),"",
                "sensitive conflicting record still withholds old fact");
        eq(top("Damu Markt Getränke", Arrays.asList(trustedOld, trustedConflict,
                c("unrelated-store", "Damu Markt verkauft Getränke"))),"unrelated-store",
                "unrelated trusted memory remains available during conflict");
        eq(top("Meine Katze heißt", Arrays.asList(
                new CelineFutureMemoryRecall.Candidate("self-conflict", "Meine Katze heißt Paul", "",
                    "self-conflict", CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,
                    true, true, NOW, 0, 1))),"",
                "self-referential conflict withheld");
        eq(top("Meine Katze heißt", Arrays.asList(trustedOld,
                CelineFutureMemoryRecall.Candidate.fromG1Projection(
                    "untrusted-conflict", "Meine Katze heißt Luna", "", "old-conflict",
                    "LOCAL_PRIVATE", "INFERRED", "inferred", true, false, NOW, 0, 1))),"",
                "G1 unknown-trust conflict still withholds prior record");
        eq(top("Damu Markt Getränke", Arrays.asList(
                CelineFutureMemoryRecall.Candidate.fromG1Projection(
                    "independent-g1", "Damu Markt verkauft Getränke", "", "",
                    "LOCAL_PRIVATE", "EXPLICIT", "user_explicit", true, true, NOW, 0, 1))),
                "independent-g1","empty G1 conflict metadata preserves trusted record");
        // G1's selected-ID memory correction emits the first-party
        // user_memory_control provenance, independently of user_correction.
        eq(top("Meine Katze heißt", Arrays.asList(
                CelineFutureMemoryRecall.Candidate.fromG1Projection(
                    "control-edit", "Meine Katze heißt Luna", "", "LOCAL_PRIVATE",
                    "EXPLICIT", "user_memory_control", true, true, NOW, 0, 1))),
                "control-edit", "selected-ID UI correction provenance retained");
        eq(top("Meine Katze heißt", Arrays.asList(
                CelineFutureMemoryRecall.Candidate.fromG1Projection(
                    "forged-control", "Meine Katze heißt Luna", "", "LOCAL_PRIVATE",
                    "EXPLICIT", "user_memory_control", true, false, NOW, 0, 1))),
                "", "UI correction tag alone cannot bypass user-origin flag");
        eq(top("Meine Katze heißt", Arrays.asList(
                CelineFutureMemoryRecall.Candidate.fromG1Projection(
                    "inferred-control", "Meine Katze heißt Luna", "", "LOCAL_PRIVATE",
                    "INFERRED", "user_memory_control", true, true, NOW, 0, 1))),
                "", "UI correction tag cannot bypass EXPLICIT knowledge state");
        eq(top("Meine Katze heißt", Arrays.asList(
                CelineFutureMemoryRecall.Candidate.fromG1Projection(
                    "unapproved-control", "Meine Katze heißt Luna", "", "LOCAL_PRIVATE",
                    "EXPLICIT", "user_memory_control", false, true, NOW, 0, 1))),
                "", "UI correction still needs separate provider authorization");
        eq(top("Meine Katze heißt", Arrays.asList(
                c("ui-old", "Meine Katze heißt Paul"),
                CelineFutureMemoryRecall.Candidate.fromG1Projection(
                    "ui-new", "Meine Katze heißt Luna", "ui-old", "LOCAL_PRIVATE",
                    "EXPLICIT", "user_memory_control", true, true, NOW, 0, 1))),
                "ui-new", "selected-ID correction supersedes stale fact");
        // A malformed projection record may conceal a confidential successor
        // or unresolved conflict. Do not silently ignore it and expose the old fact.
        eq(top("Meine Katze heißt", Arrays.asList(
                c("good-old", "Meine Katze heißt Paul"),
                new CelineFutureMemoryRecall.Candidate("", "Meine Katze heißt Luna", "good-old",
                    CelineFutureMemoryRecall.Privacy.LOCAL_SENSITIVE, false, true, NOW, 0, 1))),
                "", "missing successor ID fails entire projection closed");
        eq(top("Meine Katze heißt", Arrays.asList(
                c("good-old", "Meine Katze heißt Paul"),
                new CelineFutureMemoryRecall.Candidate("", "Meine Katze heißt Luna", "",
                    "good-old", CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,
                    false, false, NOW, 0, 1))),
                "", "missing conflict-holder ID cannot expose old record");
        eq(top("Meine Katze heißt", Arrays.asList(
                c("good-old", "Meine Katze heißt Paul"), null)),
                "", "null G1 projection cannot be silently ignored");
        // Direct scalar records also require first-party origin evidence.
        eq(top("Meine Katze heißt", Arrays.asList(
                new CelineFutureMemoryRecall.Candidate(
                    "raw-inferred", "Meine Katze heißt Luna", "",
                    CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,
                    true, false, NOW, 0, 1))),
                "", "direct record missing user origin is unavailable");
        eq(top("Meine Katze heißt", Arrays.asList(
                c("raw-old", "Meine Katze heißt Paul"),
                new CelineFutureMemoryRecall.Candidate(
                    "raw-new", "Meine Katze heißt Luna", "raw-old",
                    CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,
                    true, false, NOW, 0, 1))),
                "", "untrusted successor leaves earlier record unavailable");
        // A G1 snapshot can contain two competing corrections to the same
        // historical ID after import/compaction. Until the owner proves a
        // winner, neither correction nor its descendants is a safe recall fact.
        List<CelineFutureMemoryRecall.Candidate> fork = Arrays.asList(
            x("fork-old", "Meine Katze heißt Paul", "", true, 0),
            x("fork-luna", "Meine Katze heißt Luna", "fork-old", true, 0),
            x("fork-max", "Meine Katze heißt Max", "fork-old", true, 0),
            c("fork-unrelated", "Damu Markt verkauft Getränke"));
        eq(top("Meine Katze heißt", fork), "", "two correction children abstain");
        eq(Fixture.correct(
            "Meine Katze heißt Luna", fork, NOW), "", "fork cannot auto-select correction");
        eq(top("Damu Markt Getränke", fork), "fork-unrelated",
            "unrelated fact available despite correction fork");
        eq(top("Meine Katze heißt", Arrays.asList(
            x("fork-old", "Meine Katze heißt Paul", "", true, 0),
            x("fork-luna", "Meine Katze heißt Luna", "fork-old", true, 0),
            x("fork-expired", "Meine Katze heißt Max", "fork-old", true, NOW-1))), "",
            "expired sibling still leaves winner unresolved");
        eq(top("Meine Katze heißt", Arrays.asList(
            x("fork-old", "Meine Katze heißt Paul", "", true, 0),
            x("fork-luna", "Meine Katze heißt Luna", "fork-old", true, 0),
            new CelineFutureMemoryRecall.Candidate("fork-sensitive",
                "Meine Katze heißt Max", "fork-old",
                CelineFutureMemoryRecall.Privacy.LOCAL_SENSITIVE,
                false, true, NOW, 0, 1))), "",
            "private sibling still blocks unsupported winner");
        eq(top("Meine Katze heißt", Arrays.asList(
            x("fork-old", "Meine Katze heißt Paul", "", true, 0),
            x("fork-luna", "Meine Katze heißt Luna", "fork-old", true, 0),
            x("fork-max", "Meine Katze heißt Max", "fork-old", true, 0),
            x("fork-luna-child", "Meine Katze heißt Luna heute", "fork-luna", true, 0))), "",
            "descendant of unresolved correction also withheld");
        // A reimported stale record needs source-owned durable retraction ID.
        List<CelineFutureMemoryRecall.Candidate> reimport = Arrays.asList(
            x("reimport-old", "Meine Katze heißt Paul", "", true, 0),
            c("reimport-winner", "Meine Katze heißt Luna"));
        eq(Fixture.retrieve("Meine Katze heißt", reimport,
                Collections.singleton("reimport-old"), 8, NOW).get(0).candidate.id,
            "reimport-winner", "authoritative tombstone blocks stale reimport");
        // A retired opponent ID alone does not prove an unresolved G1 conflict
        // was adjudicated; only cleared conflict metadata can make a winner
        // eligible, with independent provenance and permission checks.
        CelineFutureMemoryRecall.Candidate unresolvedReimport =
            new CelineFutureMemoryRecall.Candidate("unresolved-reimport",
                "Meine Katze heißt Luna", "", "reimport-old",
                CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE, true, true, NOW, 0, 1);
        eq(Fixture.retrieve("Meine Katze heißt",
                Arrays.asList(unresolvedReimport), Collections.singleton("reimport-old"),
                8, NOW).size(), 0, "retired opponent does not clear unresolved conflict");
        // A conflict at an ancestor cannot be laundered into a trusted fact
        // by a subsequent supersession, including after reimport/compaction.
        CelineFutureMemoryRecall.Candidate disputedRoot =
            new CelineFutureMemoryRecall.Candidate("disputed-root", "Meine Katze heißt Paul", "",
                "absent-opponent", CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,
                true, true, NOW, 0, 1);
        CelineFutureMemoryRecall.Candidate disputedChild =
            x("disputed-child", "Meine Katze heißt Luna", "disputed-root", true, 0);
        CelineFutureMemoryRecall.Candidate disputedGrandchild =
            x("disputed-grandchild", "Meine Katze heißt Max", "disputed-child", true, 0);
        List<CelineFutureMemoryRecall.Candidate> descendantConflict = Arrays.asList(
            disputedRoot, disputedChild, disputedGrandchild);
        eq(top("Meine Katze heißt", Arrays.asList(disputedRoot, disputedChild)), "",
            "unresolved conflict quarantines direct successor");
        eq(top("Meine Katze heißt", descendantConflict), "",
            "unresolved conflict quarantines transitive successor");
        eq(Fixture.correct(
            "Meine Katze heißt Max", descendantConflict, NOW), "",
            "unresolved conflict descendants never auto-select correction target");
        eq(top("Meine Katze heißt", Arrays.asList(disputedGrandchild,
            disputedChild, disputedRoot)), "", "conflict quarantine independent of projection order");
        eq(top("Damu Markt Getränke", Arrays.asList(disputedRoot, disputedChild,
            disputedGrandchild, c("safe-store", "Damu Markt verkauft Getränke"))),
            "safe-store", "unrelated memory remains eligible after descendant quarantine");
        CelineFutureMemoryRecall.Candidate missingOppositeMarker =
            new CelineFutureMemoryRecall.Candidate("marker", "Anderes Thema", "",
                "missing-old", CelineFutureMemoryRecall.Privacy.LOCAL_SENSITIVE,
                false, false, NOW, NOW-1, 1);
        eq(top("Meine Katze heißt", Arrays.asList(missingOppositeMarker,
            x("child-of-missing-old", "Meine Katze heißt Luna", "missing-old", true, 0))),
            "", "expired sensitive conflict with missing opponent quarantines its successors");
        // Reject inputs that would be silently truncated into a different query
        // or memory fact. Partial text is not sufficient retrieval evidence.
        StringBuilder oversized = new StringBuilder("Meine Katze heißt Luna ");
        while (oversized.length() <= 512) oversized.append(" und Anderes Thema");
        eq(top(oversized.toString(), Arrays.asList(c("normal-cat", "Meine Katze heißt Luna"))),
            "", "overlong query abstains rather than using truncated prefix");
        eq(top("Meine Katze heißt", Arrays.asList(c("oversized-memory", oversized.toString()))),
            "", "overlong summary not partially matched");
        eq(top("Meine Katze heißt", Arrays.asList(
            c("older", "Meine Katze heißt Paul"),
            new CelineFutureMemoryRecall.Candidate("oversized-successor", oversized.toString(), "older",
                CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE, true, true, NOW, 0, 1))),
            "", "oversized withheld successor still suppresses older fact");
        // A bounded-length input can still exceed the distinct token cap.
        // Never convert it to an apparently complete 32-term query silently.
        StringBuilder manyTerms = new StringBuilder("Meine Katze heißt Luna");
        for (int i = 0; i < 35; i++) manyTerms.append(" wort").append(i);
        eq(top(manyTerms.toString(), Arrays.asList(c("normal-cat", "Meine Katze heißt Luna"))),
            "", "excess query token count abstains instead of prefix matching");
        eq(top("Meine Katze heißt", Arrays.asList(c("over-token-memory", manyTerms.toString()))),
            "", "excess summary token count abstains instead of partial matching");
        eq(top("Meine Katze heißt", Arrays.asList(
            c("token-old", "Meine Katze heißt Paul"),
            new CelineFutureMemoryRecall.Candidate("token-new", manyTerms.toString(), "token-old",
                CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE, true, true, NOW, 0, 1))),
            "", "over-token replacement still withholds superseded memory");
        // FUTURE ONLY: malformed temporal metadata must never be rewritten to
        // the special G1 expiry=0 (unbounded) sentinel, even if provider-approved.
        eq(top("Meine Katze heißt", Arrays.asList(
            new CelineFutureMemoryRecall.Candidate("negative-expiry",
                "Meine Katze heißt Luna", "", CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,
                true, true, NOW-100, -1, 1))), "",
                "negative expiry is corrupt, not an unlimited lifetime");
        eq(top("Meine Katze heißt", Arrays.asList(
            new CelineFutureMemoryRecall.Candidate("minimum-expiry",
                "Meine Katze heißt Luna", "", CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,
                true, true, NOW-100, Long.MIN_VALUE, 1))), "",
                "minimum-long expiry is never converted to unlimited lifetime");
        eq(top("Meine Katze heißt", Arrays.asList(
            new CelineFutureMemoryRecall.Candidate("negative-updated",
                "Meine Katze heißt Luna", "", CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,
                true, true, -1, 0, 1))), "", "negative updated timestamp withheld");
        eq(top("Meine Katze heißt", Arrays.asList(
            new CelineFutureMemoryRecall.Candidate("minimum-updated",
                "Meine Katze heißt Luna", "", CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,
                true, true, Long.MIN_VALUE, 0, 1))), "", "minimum-long updated timestamp withheld");
        eq(top("Meine Katze heißt", Arrays.asList(
            new CelineFutureMemoryRecall.Candidate("future-updated",
                "Meine Katze heißt Luna", "", CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,
                true, true, NOW+1, 0, 1))), "", "future-dated update is not yet authoritative");
        eq(top("Meine Katze heißt", Arrays.asList(
            new CelineFutureMemoryRecall.Candidate("future-updated-expiry",
                "Meine Katze heißt Luna", "", CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,
                true, true, NOW+1, NOW+1000, 1))), "", "future update with later expiry withheld");
        eq(top("Meine Katze heißt", Arrays.asList(
            new CelineFutureMemoryRecall.Candidate("expiry-before-update",
                "Meine Katze heißt Luna", "", CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,
                true, true, NOW+2000, NOW+1000, 1))), "", "expiry before changed-at is corrupt");
        eq(top("Meine Katze heißt", Arrays.asList(
            new CelineFutureMemoryRecall.Candidate("valid-no-expiry",
                "Meine Katze heißt Luna", "", CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,
                true, true, NOW-100, 0, 1))), "valid-no-expiry",
                "valid unbounded lifetime remains available");
        eq(top("Meine Katze heißt", Arrays.asList(
            new CelineFutureMemoryRecall.Candidate("valid-future-expiry",
                "Meine Katze heißt Luna", "", CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,
                true, true, NOW-100, NOW+1000, 1))), "valid-future-expiry",
                "valid future expiry remains available");
        eq(top("Meine Katze heißt", Arrays.asList(
            new CelineFutureMemoryRecall.Candidate("zero-update",
                "Meine Katze heißt Luna", "", CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,
                true, true, 0, 0, 1))), "zero-update",
                "G1 zero timestamp historical record remains available");
        eq(top("Meine Katze heißt", Arrays.asList(
            x("negative-old", "Meine Katze heißt Paul", "", true, 0),
            new CelineFutureMemoryRecall.Candidate("negative-successor",
                "Meine Katze heißt Luna", "negative-old",
                CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE, true, true,
                NOW-100, -1, 1))), "", "invalid successor still suppresses obsolete parent");
        eq(top("Meine Katze heißt", Arrays.asList(
            x("future-old", "Meine Katze heißt Paul", "", true, 0),
            new CelineFutureMemoryRecall.Candidate("future-successor",
                "Meine Katze heißt Luna", "future-old",
                CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE, true, true,
                NOW+1, 0, 1))), "", "future successor still suppresses old fact");
        eq(top("Meine Katze heißt", Arrays.asList(
            c("negative-conflicted", "Meine Katze heißt Paul"),
            new CelineFutureMemoryRecall.Candidate("negative-conflict",
                "Meine Katze heißt Luna", "", "negative-conflicted",
                CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE, true, true,
                NOW-100, -1, 1))), "", "invalid conflict holder still quarantines opponent");
        eq(Fixture.correct("Meine Katze heißt Luna",
            Arrays.asList(new CelineFutureMemoryRecall.Candidate("negative-correction",
                "Meine Katze heißt Luna", "", CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,
                true, true, NOW-100, -1, 1)), NOW), "",
                "invalid memory must never become automatic correction target");
        eq(top("Meine Katze heißt", Arrays.asList(
            CelineFutureMemoryRecall.Candidate.fromG1Projection(
                "g1-negative-expiry", "Meine Katze heißt Luna", "", "LOCAL_PRIVATE",
                "EXPLICIT", "user_explicit", true, true, NOW-100, -1, 1))), "",
                "source-grounded G1 negative expiry remains invalid");
        eq(Fixture.retrieve("Meine Katze heißt",
            Arrays.asList(c("negative-query-now", "Meine Katze heißt Luna")), 8, -1).size(),
            0, "negative evaluation clock must not expose any candidate");
        // FUTURE ONLY: corrupt non-finite importance is not an authorized
        // evidence weight, even when flags claim provider and user permission.
        eq(top("Meine Katze heisst", Arrays.asList(
            new CelineFutureMemoryRecall.Candidate("nan-importance",
                "Meine Katze heisst Luna", "", CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,
                true, true, NOW-100, 0, Double.NaN))), "",
                "NaN importance must not expose memory");
        eq(top("Meine Katze heisst", Arrays.asList(
            new CelineFutureMemoryRecall.Candidate("positive-infinity",
                "Meine Katze heisst Luna", "", CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,
                true, true, NOW-100, 0, Double.POSITIVE_INFINITY))), "",
                "positive infinity importance withheld");
        eq(top("Meine Katze heisst", Arrays.asList(
            new CelineFutureMemoryRecall.Candidate("negative-infinity",
                "Meine Katze heisst Luna", "", CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,
                true, true, NOW-100, 0, Double.NEGATIVE_INFINITY))), "",
                "negative infinity importance withheld");
        eq(top("Meine Katze heisst", Arrays.asList(
            new CelineFutureMemoryRecall.Candidate("valid-zero-importance",
                "Meine Katze heisst Luna", "", CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,
                true, true, NOW-100, 0, 0))), "valid-zero-importance",
                "valid zero importance remains retrievable");
        eq(top("Meine Katze heisst", Arrays.asList(
            new CelineFutureMemoryRecall.Candidate("valid-one-importance",
                "Meine Katze heisst Luna", "", CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,
                true, true, NOW-100, 0, 1))), "valid-one-importance",
                "valid unit importance remains retrievable");
        eq(top("Meine Katze heisst", Arrays.asList(
            new CelineFutureMemoryRecall.Candidate("old-finite",
                "Meine Katze heisst Paul", "", CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,
                true, true, NOW-100, 0, 0.5),
            new CelineFutureMemoryRecall.Candidate("bad-successor",
                "Meine Katze heisst Luna", "old-finite", CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,
                true, true, NOW-100, 0, Double.NaN))), "",
                "invalid relevance successor still suppresses obsolete fact");
        eq(top("Meine Katze heisst", Arrays.asList(
            CelineFutureMemoryRecall.Candidate.fromG1Projection(
                "g1-bad-weight", "Meine Katze heisst Luna", "", "LOCAL_PRIVATE",
                "EXPLICIT", "user_explicit", true, true, NOW-100, 0, Double.POSITIVE_INFINITY))),
                "", "G1 projection cannot bypass non-finite relevance validation");
        eq(top("Meine Katze heisst", Arrays.asList(
            new CelineFutureMemoryRecall.Candidate("clamped-high",
                "Meine Katze heisst Luna", "", CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,
                true, true, NOW-100, 0, 5.0))), "clamped-high",
                "finite high importance retains existing normalization");
        eq(top("Meine Katze heisst", Arrays.asList(
            new CelineFutureMemoryRecall.Candidate("clamped-low",
                "Meine Katze heisst Luna", "", CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,
                true, true, NOW-100, 0, -1.0))), "clamped-low",
                "finite low importance retains existing normalization");
        // FUTURE ONLY: G1 uses prefix+UUID identifiers. Malformed, oversized
        // or control-laden correction keys cannot authorize a projection.
        StringBuilder tooLongIdBuilder = new StringBuilder();
        for (int i=0; i<257; i++) tooLongIdBuilder.append('a');
        String tooLongId = tooLongIdBuilder.toString();
        String normalG1Id = "control-correction-12345678-1234-1234-1234-123456789abc";
        eq(top("Meine Katze heisst", Arrays.asList(x(normalG1Id,
            "Meine Katze heisst Luna", "", true, 0))), normalG1Id,
            "normal G1 prefixed UUID remains valid");
        eq(top("Meine Katze heisst", Arrays.asList(x(tooLongId,
            "Meine Katze heisst Luna", "", true, 0))), "",
            "oversized candidate ID fails closed");
        eq(top("Meine Katze heisst", Arrays.asList(
            x("old-id", "Meine Katze heisst Paul", "", true, 0),
            x(tooLongId, "Meine Katze heisst Luna", "old-id", true, 0))), "",
            "oversized correction ID withholds old and invalid successor");
        eq(top("Meine Katze heisst", Arrays.asList(
            x("safe-id", "Meine Katze heisst Paul", "", true, 0),
            x("bad-parent", "Anderes Thema", tooLongId, true, 0))), "",
            "oversized supersedes pointer rejects complete projection");
        eq(top("Meine Katze heisst", Arrays.asList(
            x("safe-id", "Meine Katze heisst Paul", "", true, 0),
            new CelineFutureMemoryRecall.Candidate("bad-conflict", "Anderes Thema",
                "", tooLongId, CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,
                true, true, NOW-100, 0, 0.5))), "",
            "oversized conflict key rejects complete projection");
        eq(Fixture.retrieve("Meine Katze heisst",
            Arrays.asList(x("safe-id", "Meine Katze heisst Paul", "", true, 0)),
            Collections.singleton(tooLongId), 8, NOW).size(), 0,
            "oversized retired ID rejects complete projection");
        eq(top("Meine Katze heisst", Arrays.asList(
            x("broken\nkey", "Meine Katze heisst Luna", "", true, 0))), "",
            "internal control character in ID fails closed");
        eq(top("Meine Katze heisst", Arrays.asList(
            x("safe-id", "Meine Katze heisst Paul", "", true, 0),
            x("broken-link", "Anderes Thema", "safe-id\nnoise", true, 0))), "",
            "control character in correction pointer rejects projection");
        // FUTURE ONLY: G1 prefix+UUID identity keys are not free text.
        // Trimming source IDs or retired keys would erase corrupt edge bytes.
        eq(top("Meine Katze heisst", Arrays.asList(
            x("trailing-id\n", "Meine Katze heisst Luna", "", true, 0))), "",
            "trailing newline in source ID must not normalize to a valid ID");
        eq(top("Meine Katze heisst", Arrays.asList(
            x("\tleading-id", "Meine Katze heisst Luna", "", true, 0))), "",
            "leading tab in source ID must fail closed");
        eq(top("Meine Katze heisst", Arrays.asList(
            x("trailing-space ", "Meine Katze heisst Luna", "", true, 0))), "",
            "trailing ASCII space in source ID must fail closed");
        eq(top("Meine Katze heisst", Arrays.asList(
            x("format\u200Bid", "Meine Katze heisst Luna", "", true, 0))), "",
            "Unicode zero-width format in source ID must fail closed");
        eq(top("Meine Katze heisst", Arrays.asList(
            x("valid-cat", "Meine Katze heisst Paul", "", true, 0),
            x("bad-supersedes", "Anderes Thema", "unknown\n", false, 0))), "",
            "trailing control in source supersedes key rejects entire projection");
        eq(top("Meine Katze heisst", Arrays.asList(
            x("valid-cat", "Meine Katze heisst Paul", "", true, 0),
            new CelineFutureMemoryRecall.Candidate("bad-conflict", "Anderes Thema",
                "", "unknown\u200B", CelineFutureMemoryRecall.Privacy.LOCAL_SENSITIVE,
                false, true, NOW-100, 0, 0.5))), "",
            "Unicode format in conflict key rejects entire projection");
        eq(Fixture.retrieve("Meine Katze heisst",
            Arrays.asList(x("valid-cat", "Meine Katze heisst Paul", "", true, 0)),
            Collections.singleton("unknown\n"), 8, NOW).size(), 0,
            "source retired-ID with trailing newline rejects full projection");
        eq(Fixture.retrieve("Meine Katze heisst",
            Arrays.asList(x("valid-cat", "Meine Katze heisst Paul", "", true, 0)),
            Collections.singleton("unknown\u200B"), 8, NOW).size(), 0,
            "source retired-ID with zero-width format rejects full projection");
        eq(top("Meine Katze heisst", Arrays.asList(
            x("control-correction-12345678-1234-1234-1234-123456789abc",
                "Meine Katze heisst Luna", "", true, 0))),
            "control-correction-12345678-1234-1234-1234-123456789abc",
            "valid owner-created prefix UUID retains retrieval");
        eq(top("Meine Katze heisst", Arrays.asList(
            x("valid-id", "  Meine Katze heisst Luna  ", "", true, 0))),
            "valid-id", "summary-only whitespace cleanup remains supported");
        // FUTURE ONLY: G1 creates ASCII prefix+UUID owner keys.
        // Invisible combining marks/private-use/noncharacters and non-G1
        // symbols must never become distinct trusted lineage identities.
        eq(top("Meine Katze heisst", Arrays.asList(
            x("cat\u0301", "Meine Katze heisst Luna", "", true, 0))), "",
            "combining-mark candidate ID fails closed");
        eq(top("Meine Katze heisst", Arrays.asList(
            x("valid-cat", "Meine Katze heisst Paul", "", true, 0),
            x("bad-link-mark", "Anderes Thema", "missing\u0301", false, 0))), "",
            "combining-mark supersession key fails complete projection");
        eq(top("Meine Katze heisst", Arrays.asList(
            x("valid-cat", "Meine Katze heisst Paul", "", true, 0),
            new CelineFutureMemoryRecall.Candidate("conflict-private-use", "Anderes Thema",
                "", "missing\uE000", CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,
                true, true, NOW-100, 0, 0.5))), "",
            "private-use conflict key fails complete projection");
        eq(Fixture.retrieve("Meine Katze heisst",
            Arrays.asList(x("valid-cat", "Meine Katze heisst Paul", "", true, 0)),
            Collections.singleton("retired\uFDD0"), 8, NOW).size(), 0,
            "noncharacter retired-ID fails complete projection");
        eq(top("Meine Katze heisst", Arrays.asList(
            x("caf\u00E9", "Meine Katze heisst Luna", "", true, 0))), "",
            "non-ASCII confusable source key rejected");
        eq(top("Meine Katze heisst", Arrays.asList(
            x("cat@external", "Meine Katze heisst Luna", "", true, 0))), "",
            "non-G1 symbol in source key rejected");
        eq(top("Meine Katze heisst", Arrays.asList(
            x("cat\uFDD0", "Meine Katze heisst Luna", "", true, 0))), "",
            "noncharacter candidate ID rejected");
        eq(top("Meine Katze heisst", Arrays.asList(
            x("fixture_id:v1.2", "Meine Katze heisst Luna", "", true, 0))),
            "fixture_id:v1.2", "valid ASCII contract fixture retained");
        // FUTURE ONLY: Java strings may contain unpaired UTF-16 surrogates.
        // Normalizer would otherwise silently erase malformed evidence, letting
        // a partial recall query or source memory produce a false trusted match.
        String unpairedHigh = Character.toString((char)0xD800);
        String unpairedLow = Character.toString((char)0xDC00);
        String validSupplementary = new String(Character.toChars(0x1F600));
        eq(top("Meine Katze heisst Luna" + unpairedHigh,
            Arrays.asList(c("utf16-valid", "Meine Katze heisst Luna"))), "",
            "malformed high surrogate in query abstains");
        eq(top("Meine Katze heisst Luna" + unpairedLow,
            Arrays.asList(c("utf16-valid", "Meine Katze heisst Luna"))), "",
            "malformed low surrogate in query abstains");
        eq(top("Meine Katze heisst Luna",
            Arrays.asList(c("utf16-malformed", "Meine Katze heisst Luna" + unpairedHigh))), "",
            "malformed stored memory text cannot provide recall evidence");
        eq(top("Meine Katze heisst Luna" + validSupplementary,
            Arrays.asList(c("utf16-valid", "Meine Katze heisst Luna"))), "utf16-valid",
            "well-formed supplementary query remains supported");
        eq(top("Meine Katze heisst Luna",
            Arrays.asList(c("utf16-valid", "Meine Katze heisst Luna" + validSupplementary))),
            "utf16-valid", "well-formed supplementary stored text remains supported");
        // FUTURE ONLY: G1 source text with non-text control characters
        // cannot silently normalize into a trusted partial memory. Lineage
        // remains active to suppress superseded facts even if text is rejected.
        String nul = Character.toString((char)0);
        eq(top("Meine Katze" + nul + " heisst Luna", Arrays.asList(
            c("nul-query-valid", "Meine Katze heisst Luna"))), "",
            "NUL source query must not normalize into partial evidence");
        eq(top("Meine Katze heisst Luna", Arrays.asList(
            c("nul-summary", "Meine Katze" + nul + " heisst Luna"))), "",
            "NUL in stored summary cannot produce provider recall evidence");
        eq(top("Meine Katze heisst Luna", Arrays.asList(
            c("trailing-nul-summary", "Meine Katze heisst Luna" + nul))), "",
            "trailing NUL must be rejected before summary.trim()");
        eq(top("Meine Katze heisst Luna", Arrays.asList(
            c("valid-multiline", "Meine Katze\nheisst Luna"))), "valid-multiline",
            "normal multiline German text remains retrievable");
        eq(top("Meine Katze heisst Paul", Arrays.asList(
            c("valid-old", "Meine Katze heisst Paul"),
            x("nul-successor", "Meine Katze heisst Luna" + nul, "valid-old", true, 0))), "",
            "invalid control successor still supersedes and suppresses old fact");
        // FUTURE ONLY: after pruning a historical G1 record, the correction
        // edge must not be interpreted as authoritative without parent state
        // or an owner-provided retired-ID marker. Keep unrelated memories.
        CelineFutureMemoryRecall.Candidate orphan =
            x("orphan-new", "Meine Katze heisst Luna", "unknown-parent", true, 0);
        CelineFutureMemoryRecall.Candidate orphanDescendant =
            x("orphan-grandchild", "Meine Katze heisst Max", "orphan-new", true, 0);
        eq(top("Meine Katze heisst Luna", Arrays.asList(orphan)), "",
            "orphan correction without parent or tombstone withheld");
        eq(top("Meine Katze heisst Max", Arrays.asList(orphan, orphanDescendant)), "",
            "descendant of orphan correction withheld even with complete direct edge");
        eq(top("Damu Markt Getränke", Arrays.asList(orphan,
            c("orphan-unrelated-work", "Damu Markt verkauft Getränke"))),
            "orphan-unrelated-work", "independent trusted memory survives orphan quarantine");
        eq(Fixture.retrieve("Meine Katze heisst Luna",
            Arrays.asList(orphan), Collections.singleton("unknown-parent"),
            Collections.singleton("unknown-parent"), 8, NOW)
            .get(0).candidate.id, "orphan-new",
            "separately confirmed retired-parent history permits correction chain");
        eq(Fixture.correct("Meine Katze heisst Luna",
            Arrays.asList(orphan), NOW), "",
            "orphan is never suggested as natural-language correction target");

        // Bare tombstone is not reliable proof of a pruned conflict winner.
        Set<String> retiredParent = Collections.singleton("unknown-parent");
        eq(Fixture.retrieve("Meine Katze heisst Luna",
            Arrays.asList(orphan), retiredParent, 8, NOW).size(), 0,
            "tombstone-only pruned parent must abstain");
        eq(Fixture.correct(
            "Meine Katze heisst Luna", Arrays.asList(orphan), retiredParent, NOW), "",
            "tombstone-only correction target must abstain");
        eq(Fixture.correct(
            "Meine Katze heisst Luna", Arrays.asList(orphan),
            retiredParent, retiredParent, NOW), "orphan-new",
            "source-confirmed lineage permits precise correction target");
        eq(Fixture.retrieve("Meine Katze heisst Max",
            Arrays.asList(orphan, orphanDescendant), retiredParent, retiredParent, 8, NOW)
            .get(0).candidate.id, "orphan-grandchild",
            "source-confirmed ancestor permits safe descendant");
        eq(Fixture.retrieve("Damu Markt Getränke",
            Arrays.asList(orphan, c("safe-independent", "Damu Markt verkauft Getränke")),
            retiredParent, 8, NOW).get(0).candidate.id, "safe-independent",
            "independent memory survives unconfirmed compacted ancestor");
        eq(Fixture.retrieve("Damu Markt Getränke",
            Arrays.asList(c("independent-only", "Damu Markt verkauft Getränke")),
            Collections.<String>emptySet(), retiredParent, 8, NOW).size(), 0,
            "unmatched confirmation fails complete projection");
        eq(Fixture.retrieve("Meine Katze heisst Luna",
            Arrays.asList(orphan), retiredParent, null, 8, NOW).size(), 0,
            "missing confirmation set fails complete projection");
        eq(Fixture.retrieve("Meine Katze heisst Luna",
            Arrays.asList(orphan), retiredParent,
            Collections.singleton("invalid!proof"), 8, NOW).size(), 0,
            "invalid confirmation key fails complete projection");
        CelineFutureMemoryRecall.Candidate unresolvedCompactedConflict =
            new CelineFutureMemoryRecall.Candidate("conflicted-compacted",
                "Meine Katze heisst Luna", "unknown-parent", "missing-opponent",
                CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE, true, true, NOW-100, 0, 0.5);
        eq(Fixture.retrieve("Meine Katze heisst Luna",
            Arrays.asList(unresolvedCompactedConflict), retiredParent, retiredParent,
            8, NOW).size(), 0,
            "visible unresolved conflict still wins over owner attestation");
        eq(Fixture.retrieve("Meine Katze heisst Luna",
            Arrays.asList(
                x("retired-fork-luna", "Meine Katze heisst Luna", "unknown-parent", true, 0),
                x("retired-fork-max", "Meine Katze heisst Max", "unknown-parent", true, 0)),
            retiredParent, retiredParent, 8, NOW).size(), 0,
            "visible correction siblings remain unresolved despite attestation");
        // Post-compaction correction ambiguity: a caller cannot treat a live
        // *export-authorized* but quarantined record as an irrelevant rival.
        // Provider recall excludes disputed/orphaned memories by design;
        // selected-ID correction must not guess while their lineage is unknown.
        CelineFutureMemoryRecall.Candidate cleanCorrection =
            c("clean-correction", "Meine Katze heisst Luna");
        CelineFutureMemoryRecall.Candidate disputedLive =
            new CelineFutureMemoryRecall.Candidate("disputed-live",
                "Meine Katze heisst Luna", "", "missing-conflict-end",
                CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE, true, true,
                NOW-100, 0, 0.5);
        eq(Fixture.correct(
            "Meine Katze heisst Luna", Arrays.asList(cleanCorrection, disputedLive), NOW), "",
            "live conflicted authorized rival must block speculative correction");
        eq(Fixture.correct(
            "Meine Katze heisst Luna", Arrays.asList(disputedLive, cleanCorrection), NOW), "",
            "conflict rival correction independent of order");
        eq(Fixture.correct(
            "Meine Katze heisst Luna", Arrays.asList(cleanCorrection, orphan), retiredParent, NOW), "",
            "bare retired-parent tombstone does not justify correction while orphan rival remains");
        eq(Fixture.correct(
            "Meine Katze heisst Luna", Arrays.asList(orphan, cleanCorrection), retiredParent, NOW), "",
            "bare retired lineage ambiguity independent of input order");
        CelineFutureMemoryRecall.Candidate quarantineChild =
            x("quarantined-child", "Meine Katze heisst Luna", "disputed-live", true, 0);
        eq(Fixture.correct(
            "Meine Katze heisst Luna", Arrays.asList(cleanCorrection,
                disputedLive, quarantineChild), NOW), "",
            "descendant of conflicted rival must not disappear from correction ambiguity");
        eq(Fixture.correct(
            "Meine Katze heisst Luna", Arrays.asList(cleanCorrection,
                c("normal-independent", "Damu Markt verkauft Getränke")), NOW),
            "clean-correction", "independent unrelated memory still permits correction");
        eq(Fixture.correct(
            "Meine Katze heisst Luna", Arrays.asList(cleanCorrection,
                x("expired-orphan", "Meine Katze heisst Luna", "unknown-parent", true, NOW-1)),
                retiredParent, NOW), "clean-correction",
            "genuinely expired orphan is not a live correction rival");
        eq(Fixture.correct(
            "Meine Katze heisst Luna", Arrays.asList(cleanCorrection,
                x("retired-candidate", "Meine Katze heisst Luna", "", true, 0)),
                Collections.singleton("retired-candidate"), NOW), "clean-correction",
            "source-retired record is not a live correction rival");
        eq(Fixture.correct(
            "Meine Katze heisst Luna", Arrays.asList(cleanCorrection), NOW),
            "clean-correction", "unique eligible correction remains usable");
        // If G1 timestamp metadata is corrupt, it cannot prove that a competing
        // correction candidate no longer exists. Provider retrieval can reject
        // it, but correction-by-guess must fall back to explicit selected ID.
        eq(Fixture.correct(
            "Meine Katze heisst Luna", Arrays.asList(cleanCorrection,
                x("negative-expiry-rival", "Meine Katze heisst Luna", "unknown-parent", true, -1)),
                retiredParent, NOW), "",
            "negative expiry on pruned rival must block guessed correction");
        eq(Fixture.correct(
            "Meine Katze heisst Luna", Arrays.asList(cleanCorrection,
                new CelineFutureMemoryRecall.Candidate("negative-updated-rival",
                    "Meine Katze heisst Luna", "", "missing-conflict-end",
                    CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE, true, true, -1, 0, 0.5)), NOW), "",
            "negative updatedAt on conflicted rival cannot prove absence");
        eq(Fixture.correct(
            "Meine Katze heisst Luna", Arrays.asList(cleanCorrection,
                new CelineFutureMemoryRecall.Candidate("reversed-expiry-rival",
                    "Meine Katze heisst Luna", "", CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,
                    true, true, NOW-100, NOW-200, 0.5)), NOW), "",
            "expiry before last update is corrupt lineage, not proof of retirement");
        eq(Fixture.correct(
            "Meine Katze heisst Luna", Arrays.asList(cleanCorrection,
                new CelineFutureMemoryRecall.Candidate("corrupt-time-redacted",
                    "", "", CelineFutureMemoryRecall.Privacy.LOCAL_SENSITIVE,
                    false, true, NOW-100, -1, 0.5)), NOW), "",
            "corrupt timestamp and redacted summary must block speculative correction");
        eq(Fixture.correct(
            "Meine Katze heisst Luna", Arrays.asList(cleanCorrection,
                x("truly-expired-rival", "Meine Katze heisst Luna", "", true, NOW-1)), NOW),
            "clean-correction", "valid expired rival does not block correction");
        // FUTURE ONLY: invisible Unicode FORMAT code points must not be
        // normalized away into a seemingly complete lexical query/summary.
        eq(top("Meine Katze heisst Lu\u200Bna",Arrays.asList(
            c("format-query","Meine Katze heisst Luna"))),"",
            "zero-width query corruption must abstain");
        eq(top("Meine Katze heisst Luna",Arrays.asList(
            c("format-summary","Meine Katze heisst Lu\u200Bna"))),"",
            "zero-width source corruption must abstain");
        eq(top("Meine Katze heisst Lu\u202Ena",Arrays.asList(
            c("bidi-query","Meine Katze heisst Luna"))),"",
            "bidi override in query must abstain");
        eq(top("Meine Katze heisst Luna",Arrays.asList(
            c("word-joiner-summary","Meine Katze heisst Lu\u2060na"))),"",
            "word joiner in source summary must abstain");
        eq(top("Meine Katze heisst Lu\uDB40\uDC01na",Arrays.asList(
            c("supplementary-format","Meine Katze heisst Luna"))),"",
            "supplementary Unicode FORMAT query must abstain");
        eq(top("Meine Katze heisst",Arrays.asList(
            x("old-format-ancestor","Meine Katze heisst Paul","",true,0),
            x("hidden-format-successor","Meine Katze heisst Lu\u200Bna",
                "old-format-ancestor",true,0))),"",
            "invalid format successor still retires ancestor");
        eq(Fixture.correct(
            "Meine Katze heisst Lu\u200Bna",Arrays.asList(
                c("format-correction","Meine Katze heisst Luna")),NOW),"",
            "format-corrupted correction must not select memory ID");
        eq(top("Meine Katze\nheisst Luna",Arrays.asList(
            c("multiline-valid","Meine Katze heisst Luna"))),"multiline-valid",
            "ordinary multiline German query still accepted");
        eq(top("Meine Katze heisst Luna\uD83D\uDC08",Arrays.asList(
            c("emoji-valid","Meine Katze heisst Luna"))),"emoji-valid",
            "valid supplementary emoji must remain harmless");
        // Default-ignorable Unicode marks are MARK (not FORMAT) in Java.
        // NFD/mark-stripping otherwise launders corrupted lexical evidence.
        String cgj = "\u034F";
        String vs16 = "\uFE0F";
        String mongolianVs = "\u180B";
        String supVs = new String(Character.toChars(0xE0100));
        eq(top("Meine Katze heisst Lu"+cgj+"na",
            Arrays.asList(c("ignorable-valid", "Meine Katze heisst Luna"))), "",
            "CGJ invisible query mark must abstain");
        eq(top("Meine Katze heisst Luna",
            Arrays.asList(c("ignorable-stored", "Meine Katze heisst Lu"+cgj+"na"))), "",
            "CGJ invisible stored evidence must abstain");
        eq(top("Meine Katze heisst Lu"+vs16+"na",
            Arrays.asList(c("variation-valid", "Meine Katze heisst Luna"))), "",
            "variation selector U+FE0F query mark must abstain");
        eq(top("Meine Katze heisst Lu"+supVs+"na",
            Arrays.asList(c("supp-variation-valid", "Meine Katze heisst Luna"))), "",
            "supplementary variation-selector query must abstain");
        eq(top("Meine Katze heisst Lu"+mongolianVs+"na",
            Arrays.asList(c("mongolian-variation-valid", "Meine Katze heisst Luna"))), "",
            "Mongolian selector embedded in Latin query must abstain");
        eq(top("Meine Katze heisst Paul", Arrays.asList(
            c("ignorable-predecessor", "Meine Katze heisst Paul"),
            x("ignorable-successor", "Meine Katze heisst Lu"+cgj+"na",
                "ignorable-predecessor", true, 0))), "",
            "withheld corrupt successor still suppresses predecessor");
        eq(top("Cafe\u0301", Arrays.asList(
            c("valid-decomposed-accent", "Ich mag Café"))),
            "valid-decomposed-accent", "ordinary combining accent remains supported");
        eq(top("Meine Katze heisst Luna" + new String(Character.toChars(0x1F600)),
            Arrays.asList(c("valid-emoji", "Meine Katze heisst Luna"))),
            "valid-emoji", "well-formed ordinary emoji remains supported");
        // FUTURE ONLY: VS16 has a narrow standalone emoji exception; never
        // allow invisible selectors to launder letters inside a lexical word.
        String heartEmoji = "\u2764\uFE0F";
        String sunEmoji = "\u2600\uFE0F";
        String flightEmoji = "\u2708\uFE0F";
        eq(top("Meine Katze heisst Luna " + heartEmoji,
            Arrays.asList(c("heart-query", "Meine Katze heisst Luna"))),
            "heart-query", "standalone heart emoji query remains readable");
        eq(top("Meine Katze heisst Luna",
            Arrays.asList(c("heart-summary", "Meine Katze heisst Luna " + heartEmoji))),
            "heart-summary", "standalone heart emoji memory remains readable");
        eq(top("Meine Katze heisst Luna " + sunEmoji,
            Arrays.asList(c("sun-query", "Meine Katze heisst Luna"))),
            "sun-query", "standalone sun emoji query remains readable");
        eq(top("Meine Katze heisst Luna " + flightEmoji,
            Arrays.asList(c("plane-query", "Meine Katze heisst Luna"))),
            "plane-query", "standalone airplane emoji query remains readable");
        eq(top("Meine Katze heisst Lu" + heartEmoji + "na",
            Arrays.asList(c("heart-inword", "Meine Katze heisst Luna"))),
            "", "heart selector inside a Latin word must abstain");
        eq(top("Meine Katze heisst Luna" + heartEmoji,
            Arrays.asList(c("heart-attached", "Meine Katze heisst Luna"))),
            "", "heart selector directly attached to word must abstain");
        eq(top("Meine Katze heisst Luna " + heartEmoji + "X",
            Arrays.asList(c("heart-beforeword", "Meine Katze heisst Luna"))),
            "", "emoji selector immediately before Latin word must abstain");
        eq(top("Meine Katze heisst Luna " + heartEmoji + "\uFE0F",
            Arrays.asList(c("heart-double", "Meine Katze heisst Luna"))),
            "", "duplicated variation selector must abstain");
        eq(top("Meine Katze heisst Luna " + "\u25C6\uFE0F",
            Arrays.asList(c("heart-unsupported", "Meine Katze heisst Luna"))),
            "", "unrecognized symbol-selector pair remains fail closed");
        eq(top("Meine Katze heisst", Arrays.asList(
            c("old-heart-ancestor", "Meine Katze heisst Paul"),
            x("heart-corrupt-successor", "Meine Katze heisst Luna" + heartEmoji,
                "old-heart-ancestor", true, 0))), "",
            "corrupt emoji successor still suppresses obsolete ancestor");
        // FUTURE ONLY: allow a tiny safe punctuation separator after a
        // standalone VS16 emoji; do not normalize glued words or bad marks.
        eq(top("Meine Katze heisst Luna " + heartEmoji + "!",
            Arrays.asList(c("heart-bang", "Meine Katze heisst Luna"))),
            "heart-bang", "standalone heart at sentence end remains searchable");
        eq(top("Meine Katze heisst Luna " + heartEmoji + ", heute",
            Arrays.asList(c("heart-comma", "Meine Katze heisst Luna"))),
            "heart-comma", "standalone heart with comma and whitespace searchable");
        eq(top("Kedi Luna " + sunEmoji + "?",
            Arrays.asList(c("turkish-sun", "Kedi Luna"))),
            "turkish-sun", "Latin-script Turkish query with sun question mark");
        eq(top("Meine Katze heisst Luna " + flightEmoji + ". Heute",
            Arrays.asList(c("flight-period", "Meine Katze heisst Luna"))),
            "flight-period", "standalone airplane followed by sentence boundary");
        eq(top("Meine Katze heisst Luna",
            Arrays.asList(c("heart-stored-bang",
                "Meine Katze heisst Luna " + heartEmoji + "!"))),
            "heart-stored-bang", "memory summary with terminal heart punctuation");
        eq(top("Kedi Luna", Arrays.asList(c("sun-stored-question",
            "Kedi Luna " + sunEmoji + "?"))),
            "sun-stored-question", "memory summary with sun question punctuation");
        eq(top("Meine Katze heisst Luna" + heartEmoji + "!",
            Arrays.asList(c("heart-glued-prev", "Meine Katze heisst Luna"))),
            "", "heart attached before punctuation to a word remains denied");
        eq(top("Meine Katze heisst Luna " + heartEmoji + ",Na",
            Arrays.asList(c("heart-glued-next-comma", "Meine Katze heisst Luna"))),
            "", "punctuation cannot launder immediately attached next word");
        eq(top("Meine Katze heisst Luna " + heartEmoji + "!Na",
            Arrays.asList(c("heart-glued-next-bang", "Meine Katze heisst Luna"))),
            "", "exclamation cannot launder immediately attached next word");
        eq(top("Meine Katze heisst Luna " + heartEmoji + "!!",
            Arrays.asList(c("heart-double-punct", "Meine Katze heisst Luna"))),
            "", "multiple punctuation conservative boundary denial");
        eq(top("Meine Katze heisst Luna " + heartEmoji + "\uFE0F!",
            Arrays.asList(c("heart-double-selector-punct", "Meine Katze heisst Luna"))),
            "", "double variation selector still denied with punctuation");
        eq(top("Meine Katze heisst Luna " + "\u25C6\uFE0F!",
            Arrays.asList(c("unsupported-selector-punct", "Meine Katze heisst Luna"))),
            "", "unsupported variation selector still denied with punctuation");
        // FUTURE ONLY: non-Latin / unrepresentable letters and digits must
        // never be silently discarded while other query terms still match.
        eq(top("Meine Katze heisst Luna مرحبا",
            Arrays.asList(c("arabic-ignored", "Meine Katze heisst Luna"))),
            "", "mixed Arabic query must not match a truncated Latin prefix");
        eq(top("Meine Katze heisst Luna Привет",
            Arrays.asList(c("cyrillic-ignored", "Meine Katze heisst Luna"))),
            "", "mixed Cyrillic query must abstain");
        eq(top("Meine Katze heisst Luna 你好",
            Arrays.asList(c("han-ignored", "Meine Katze heisst Luna"))),
            "", "mixed Han query must abstain");
        eq(top("Meine Katze heisst Luna \u0661",
            Arrays.asList(c("arabic-digit", "Meine Katze heisst Luna"))),
            "", "Arabic-Indic digit must not silently disappear");
        eq(top("Meine Katze heisst Luna \u0131",
            Arrays.asList(c("unmapped-dotless-i", "Meine Katze heisst Luna"))),
            "", "unmapped dotless Latin i must not silently disappear");
        eq(top("Meine Katze heisst Luna \uFF11",
            Arrays.asList(c("fullwidth-digit", "Meine Katze heisst Luna"))),
            "", "fullwidth digit must not silently disappear");
        eq(top("Meine Katze heisst Luna",
            Arrays.asList(c("arabic-stored", "Meine Katze heisst Luna مرحبا"))),
            "", "stored mixed-script memory is not partially matched");
        eq(top("Meine Katze heisst", Arrays.asList(
            c("old-arabic-lineage", "Meine Katze heisst Paul"),
            x("unrepresentable-successor", "Meine Katze heisst Luna مرحبا",
                "old-arabic-lineage", true, 0))), "",
            "unrepresentable successor still suppresses predecessor");
        eq(top("Kedi küçük köpek", Arrays.asList(
            c("valid-turkish-latin", "Kedi küçük köpek"))),
            "valid-turkish-latin", "Turkish Latin accent decomposition retained");
        eq(top("München Café", Arrays.asList(c("valid-cafe", "München Café"))),
            "valid-cafe", "Latin accented German/French remains searchable");
        eq(top("\u0130stanbul Köln", Arrays.asList(
            c("valid-capital-dotted-i", "\u0130stanbul Köln"))),
            "valid-capital-dotted-i", "normalizable Turkish dotted I retained");
        eq(top("Kedi Luna " + sunEmoji + "?",
            Arrays.asList(c("sun-script-positive", "Kedi Luna"))),
            "sun-script-positive", "punctuation emoji remains supported after script gate");
        // FUTURE ONLY: raw 512-code-unit input bounds must apply BEFORE trim.
        StringBuilder padding513 = new StringBuilder();
        for (int i=0; i<513; i++) padding513.append(' ');
        String oversizedLeading = padding513 + "Meine Katze heisst Luna";
        String oversizedTrailing = "Meine Katze heisst Luna" + padding513;
        StringBuilder exactly512 = new StringBuilder("Meine Katze heisst Luna");
        while (exactly512.length() < 512) exactly512.append(' ');
        String exactly513 = exactly512.toString() + " ";
        eq(top(oversizedLeading, Arrays.asList(c("over-query-leading", "Meine Katze heisst Luna"))),
            "", "oversized raw leading-pad query must abstain");
        eq(top(oversizedTrailing, Arrays.asList(c("over-query-trailing", "Meine Katze heisst Luna"))),
            "", "oversized raw trailing-pad query must abstain");
        eq(top("Meine Katze heisst Luna", Arrays.asList(c("over-summary-leading", oversizedLeading))),
            "", "oversized raw leading-pad summary must be withheld");
        eq(top("Meine Katze heisst Luna", Arrays.asList(c("over-summary-trailing", oversizedTrailing))),
            "", "oversized raw trailing-pad summary must be withheld");
        eq(top(exactly512.toString(), Arrays.asList(c("exact512-query", "Meine Katze heisst Luna"))),
            "exact512-query", "512-unit bounded query remains searchable");
        eq(top("Meine Katze heisst Luna", Arrays.asList(c("exact512-summary", exactly512.toString()))),
            "exact512-summary", "512-unit bounded memory remains searchable");
        eq(top(exactly513, Arrays.asList(c("exact513-query", "Meine Katze heisst Luna"))),
            "", "513-unit padded query fails closed");
        eq(top("Meine Katze heisst Luna", Arrays.asList(c("exact513-summary", exactly513))),
            "", "513-unit padded memory fails closed");
        eq(top("Meine Katze heisst", Arrays.asList(
            c("old-raw-bound", "Meine Katze heisst Paul"),
            x("oversized-successor", oversizedLeading, "old-raw-bound", true, 0))),
            "", "invalid oversized successor still retires ancestor");
        eq(Fixture.correct(oversizedLeading,
            Arrays.asList(c("over-correction", "Meine Katze heisst Luna")), NOW),
            "", "oversized padded text cannot auto-select a correction ID");
        // FUTURE ONLY: keycap/enclosing marks and strike overlays must not
        // become false lexical facts when NFD strips Unicode combining MARKs.
        eq(top("Meine Katze heisst Luna\u20E3",
            Arrays.asList(c("keycap-query", "Meine Katze heisst Luna"))),
            "", "enclosing keycap query must abstain");
        eq(top("Meine Katze heisst Luna",
            Arrays.asList(c("keycap-stored", "Meine Katze heisst Luna\u20E3"))),
            "", "enclosing keycap memory must abstain");
        eq(top("Meine Katze heisst Luna\u20DD",
            Arrays.asList(c("enclosed-query", "Meine Katze heisst Luna"))),
            "", "enclosing circle query must abstain");
        eq(top("Meine Katze heisst Luna",
            Arrays.asList(c("enclosed-stored", "Meine Katze heisst Luna\u20DD"))),
            "", "enclosing circle memory must abstain");
        eq(top("Meine Katze heisst Luna\u0338",
            Arrays.asList(c("overlay-query", "Meine Katze heisst Luna"))),
            "", "strike overlay query must abstain");
        eq(top("Meine Katze heisst Luna",
            Arrays.asList(c("overlay-stored", "Meine Katze heisst Luna\u0338"))),
            "", "strike overlay memory must abstain");
        eq(top("Meine Katze heisst Luna\u0334",
            Arrays.asList(c("tilde-overlay-query", "Meine Katze heisst Luna"))),
            "", "tilde overlay query must abstain");
        eq(top("Meine Katze heisst Luna",
            Arrays.asList(c("tilde-overlay-stored", "Meine Katze heisst Luna\u0334"))),
            "", "tilde overlay memory must abstain");
        eq(top("Meine Katze heisst", Arrays.asList(
            c("old-overlaid-lineage", "Meine Katze heisst Paul"),
            x("struck-successor", "Meine Katze heisst Luna\u0338",
                "old-overlaid-lineage", true, 0))),
            "", "invalid struck successor still retires ancestor");
        eq(top("Meine Katze heisst Lu\u0301na",
            Arrays.asList(c("accent-query", "Meine Katze heisst Luna"))),
            "accent-query", "normal Latin decomposed accent remains searchable");
        eq(top("Meine Katze heisst Luna",
            Arrays.asList(c("accent-stored", "Meine Katze heisst Lu\u0301na"))),
            "accent-stored", "normal Latin accent in stored record is searchable");
        // FUTURE ONLY: malformed decoding evidence must never silently
        // become partial lexical matches after punctuation normalization.
        String sourceFact = "Meine Katze heisst Luna";
        String highPlaneNoncharacter = new String(Character.toChars(0x1FFFE));
        eq(top("Meine Katze heisst Lu\uFFFDna",
            Arrays.asList(c("bad-replacement-query", sourceFact))),
            "", "replacement in query must abstain");
        eq(top(sourceFact + "\uFFFD",
            Arrays.asList(c("bad-replacement-tail", sourceFact))),
            "", "trailing replacement query must abstain");
        eq(top(sourceFact,
            Arrays.asList(c("bad-replacement-stored", "Meine Katze heisst Lu\uFFFDna"))),
            "", "replacement in stored summary must abstain");
        eq(top(sourceFact,
            Arrays.asList(c("bad-replacement-tail-stored", sourceFact + "\uFFFD"))),
            "", "trailing replacement stored summary must abstain");
        eq(top(sourceFact + "\uFDD0",
            Arrays.asList(c("bad-fdd0-query", sourceFact))),
            "", "U+FDD0 query noncharacter must abstain");
        eq(top(sourceFact,
            Arrays.asList(c("bad-fdd0-stored", sourceFact + "\uFDD0"))),
            "", "U+FDD0 stored noncharacter must abstain");
        eq(top(sourceFact + "\uFFFF",
            Arrays.asList(c("bad-ffff-query", sourceFact))),
            "", "U+FFFF query noncharacter must abstain");
        eq(top(sourceFact,
            Arrays.asList(c("bad-ffff-stored", sourceFact + "\uFFFF"))),
            "", "U+FFFF stored noncharacter must abstain");
        eq(top(sourceFact + highPlaneNoncharacter,
            Arrays.asList(c("bad-plane-query", sourceFact))),
            "", "supplementary U+1FFFE query noncharacter must abstain");
        eq(top(sourceFact,
            Arrays.asList(c("bad-plane-stored", sourceFact + highPlaneNoncharacter))),
            "", "supplementary U+1FFFE stored noncharacter must abstain");
        eq(top("Meine Katze heisst", Arrays.asList(
            c("old-bad-replacement", "Meine Katze heisst Paul"),
            x("replacement-successor", sourceFact + "\uFFFD",
                "old-bad-replacement", true, 0))),
            "", "withheld corrupt successor still retires prior memory");
        eq(Fixture.correct(
            sourceFact + "\uFFFD", Arrays.asList(c("bad-correction", sourceFact)), NOW),
            "", "corrupt correction text must not select memory ID");
        eq(top(sourceFact + " 🙂",
            Arrays.asList(c("valid-visible-emoji", sourceFact))),
            "valid-visible-emoji", "ordinary separate emoji retained");

        // Source-origin overlay marks must never normalize into false evidence.
        eq(top(sourceFact + "\u20D2",
            Arrays.asList(c("overlay-q-20D2", sourceFact))),
            "", "overlay U+20D2 query cannot match plain memory");
        eq(top(sourceFact,
            Arrays.asList(c("overlay-s-20D2", sourceFact + "\u20D2"))),
            "", "overlay U+20D2 stored fact must abstain");
        eq(top(sourceFact + "\u20D3",
            Arrays.asList(c("overlay-q-20D3", sourceFact))),
            "", "overlay U+20D3 query cannot match plain memory");
        eq(top(sourceFact,
            Arrays.asList(c("overlay-s-20D3", sourceFact + "\u20D3"))),
            "", "overlay U+20D3 stored fact must abstain");
        eq(top(sourceFact + "\u20E5",
            Arrays.asList(c("overlay-q-20E5", sourceFact))),
            "", "overlay U+20E5 query cannot match plain memory");
        eq(top(sourceFact,
            Arrays.asList(c("overlay-s-20E5", sourceFact + "\u20E5"))),
            "", "overlay U+20E5 stored fact must abstain");
        eq(top(sourceFact + "\u20E6",
            Arrays.asList(c("overlay-q-20E6", sourceFact))),
            "", "overlay U+20E6 query cannot match plain memory");
        eq(top(sourceFact,
            Arrays.asList(c("overlay-s-20E6", sourceFact + "\u20E6"))),
            "", "overlay U+20E6 stored fact must abstain");
        eq(top(sourceFact + "\u20EA",
            Arrays.asList(c("overlay-q-20EA", sourceFact))),
            "", "overlay U+20EA query cannot match plain memory");
        eq(top(sourceFact,
            Arrays.asList(c("overlay-s-20EA", sourceFact + "\u20EA"))),
            "", "overlay U+20EA stored fact must abstain");
        eq(top("Meine Katze", Arrays.asList(
            x("overlay-old", "Meine Katze heisst Paul", "", true, 0),
            x("overlay-next", sourceFact + "\u20D2", "overlay-old", true, 0))),
            "", "overlay successor still retires old fact");
        eq(Fixture.correct(
            sourceFact + "\u20D3",
            Arrays.asList(c("overlay-correction", sourceFact)), NOW),
            "", "overlay-corruped correction cannot select ID");
        eq(top("Luná", Arrays.asList(c("overlay-accent-ok", "Luna"))),
            "overlay-accent-ok", "legitimate Latin accent search survives overlay guard");

        // FUTURE ONLY: Unicode PRIVATE_USE codepoints must not be silently
        // discarded into seemingly complete lexical evidence. Some Android/
        // provider transcripts contain icon-font or app-internal PUA glyphs;
        // their semantics are not present in this Latin-only memory policy.
        String puaBmp = "\uE000";
        String puaPlane15 = new String(Character.toChars(0xF0000));
        String puaPlane16 = new String(Character.toChars(0x100000));
        eq(top(sourceFact + puaBmp, Arrays.asList(c("pua-bmp-query", sourceFact))),
            "", "BMP private-use query must not normalize to plain fact");
        eq(top(sourceFact, Arrays.asList(c("pua-bmp-stored", sourceFact + puaBmp))),
            "", "BMP private-use stored summary must not normalize to plain fact");
        eq(top(sourceFact + puaPlane15, Arrays.asList(c("pua-15-query", sourceFact))),
            "", "plane-15 private-use query must abstain");
        eq(top(sourceFact, Arrays.asList(c("pua-15-stored", sourceFact + puaPlane15))),
            "", "plane-15 private-use memory must abstain");
        eq(top(sourceFact + puaPlane16, Arrays.asList(c("pua-16-query", sourceFact))),
            "", "plane-16 private-use query must abstain");
        eq(top(sourceFact, Arrays.asList(c("pua-16-stored", sourceFact + puaPlane16))),
            "", "plane-16 private-use memory must abstain");
        eq(top("Meine Katze heisst", Arrays.asList(
            x("pua-old", "Meine Katze heisst Paul", "", true, 0),
            x("pua-new", sourceFact + puaBmp, "pua-old", true, 0))),
            "", "withheld PUA successor still retires old fact");
        eq(Fixture.correct(
            sourceFact + puaPlane15, Arrays.asList(c("pua-correction", sourceFact)), NOW),
            "", "PUA-carrying correction cannot auto-select a memory ID");
        eq(top("Meine Katze heisst Lu" + puaBmp + "na",
            Arrays.asList(c("pua-within-word", sourceFact))),
            "", "private-use inserted inside a lexical word must abstain");
        eq(top(sourceFact, Arrays.asList(c("pua-clean", sourceFact))),
            "pua-clean", "ordinary Unicode-safe fact remains searchable");
        eq(top(sourceFact + " 🙂", Arrays.asList(c("pua-emoji-allowed", sourceFact))),
            "pua-emoji-allowed", "ordinary visible supplementary emoji unaffected");


        // FUTURE ONLY: unassigned Unicode scalars have no known lexical
        // semantics. They must not vanish into a false exact recall match.
        String unassignedGreek = "\u0378";
        String unassignedGreekNeighbor = "\u0379";
        String unassignedSupplementary = new String(Character.toChars(0xE0002));
        eq(top(sourceFact + unassignedGreek, Arrays.asList(c("cn-0378-q", sourceFact))),
            "", "unassigned Greek codepoint in query must abstain");
        eq(top(sourceFact, Arrays.asList(c("cn-0378-s", sourceFact + unassignedGreek))),
            "", "unassigned Greek codepoint in stored fact must abstain");
        eq(top(sourceFact + unassignedGreekNeighbor, Arrays.asList(c("cn-0379-q", sourceFact))),
            "", "second unassigned Greek scalar in query must abstain");
        eq(top(sourceFact, Arrays.asList(c("cn-0379-s", sourceFact + unassignedGreekNeighbor))),
            "", "second unassigned Greek scalar in memory must abstain");
        eq(top(sourceFact + unassignedSupplementary, Arrays.asList(c("cn-supp-q", sourceFact))),
            "", "unassigned supplementary scalar in query must abstain");
        eq(top(sourceFact, Arrays.asList(c("cn-supp-s", sourceFact + unassignedSupplementary))),
            "", "unassigned supplementary scalar in memory must abstain");
        eq(top("Meine Katze heisst", Arrays.asList(
            x("cn-old", "Meine Katze heisst Paul", "", true, 0),
            x("cn-new", sourceFact + unassignedGreek, "cn-old", true, 0))),
            "", "unassigned successor cannot resurrect obsolete ancestor");
        eq(Fixture.correct(
            sourceFact + unassignedSupplementary, Arrays.asList(c("cn-correction", sourceFact)), NOW),
            "", "unassigned correction content cannot select ID");
        eq(top("Meine Katze heisst Lu" + unassignedGreek + "na",
            Arrays.asList(c("cn-midword", sourceFact))),
            "", "unassigned scalar inside lexical word must abstain");
        eq(top(sourceFact + " 🙂", Arrays.asList(c("cn-visible-emoji-ok", sourceFact))),
            "cn-visible-emoji-ok", "assigned visible emoji remains searchable");


        // FUTURE-ONLY: a purported certificate for an absent/pruned parent
        // contradicts a source projection that still carries the same ID.
        // Never infer certified resolved ancestry from that contradictory batch.
        Set<String> collisionRetired = Collections.singleton("collision-parent");
        CelineFutureMemoryRecall.Candidate collisionParent =
            c("collision-parent", "Meine Katze heisst Paul");
        CelineFutureMemoryRecall.Candidate collisionChild =
            x("collision-child", "Meine Katze heisst Luna", "collision-parent", true, 0);
        List<CelineFutureMemoryRecall.Candidate> collisionProjection =
            Arrays.asList(collisionParent,collisionChild);
        eq(Fixture.retrieve("Meine Katze heisst Luna", collisionProjection,
            collisionRetired,collisionRetired,8,NOW).size(), 0,
            "source-present parent cannot be simultaneously certified pruned");
        eq(Fixture.retrieve("Meine Katze heisst Luna",
            Arrays.asList(collisionChild,collisionParent),collisionRetired,collisionRetired,8,NOW).size(), 0,
            "certificate and source record collision must be order independent");
        eq(Fixture.correct("Meine Katze heisst Luna",
            collisionProjection,collisionRetired,collisionRetired,NOW), "",
            "conflicting pruned certificate cannot authorize correction target");
        eq(Fixture.retrieve("Meine Katze heisst Luna",
            Arrays.asList(collisionParent,collisionChild,
                c("unrelated-safe", "Damu Markt verkauft Getraenke")),
            collisionRetired,collisionRetired,8,NOW).size(), 0,
            "a contradictory certificate poisons the entire projection");
        eq(Fixture.retrieve("Meine Katze heisst Luna",
            Arrays.asList(collisionChild),collisionRetired,collisionRetired,8,NOW)
                .get(0).candidate.id, "collision-child",
            "actually pruned missing parent retains explicitly attested child");
        eq(Fixture.retrieve("Meine Katze heisst Luna",
            collisionProjection,collisionRetired,8,NOW).get(0).candidate.id,
            "collision-child", "legacy complete lineage without false prune certificate remains available");
        // Eight-result cap is not permission to ignore a ninth hidden rival.
        List<CelineFutureMemoryRecall.Candidate> capped = new ArrayList<>();
        capped.add(c("cap-unique", "Meine Katze heisst Luna"));
        for (int k = 0; k < 7; k++) capped.add(c("cap-weak-" + k,
            "Meine Katze heisst Paul"));
        CelineFutureMemoryRecall.Candidate cappedHidden = new CelineFutureMemoryRecall.Candidate(
            "cap-hidden", "Meine Katze heisst Luna", "",
            CelineFutureMemoryRecall.Privacy.LOCAL_SENSITIVE, false, true,NOW-100,0,0.9);
        capped.add(cappedHidden);
        eq(Fixture.retrieve("Meine Katze heisst Luna",capped,8,NOW).size(), 8,
            "eight-result bound stays deterministic");
        eq(Fixture.correct("Meine Katze heisst Luna",
            capped,NOW), "", "ninth hidden equal rival must prevent speculative ID selection");
        Collections.reverse(capped);
        eq(Fixture.correct("Meine Katze heisst Luna",
            capped,NOW), "", "capped hidden rival must be input-order independent");

        // FUTURE-ONLY: a redacted/unindexable live rival can have the same
        // correction topic. A zero-term projection is UNKNOWN, not unrelated.
        CelineFutureMemoryRecall.Candidate aVisible =
            c("script-visible", "Meine Katze heisst Luna");
        CelineFutureMemoryRecall.Candidate mixedArabic =
            c("script-arabic", "Meine Katze heisst Luna عربي");
        CelineFutureMemoryRecall.Candidate mixedCjk =
            c("script-cjk", "Meine Katze heisst Luna 猫");
        CelineFutureMemoryRecall.Candidate brokenLong =
            c("script-many", "Meine Katze heisst Luna eins zwei drei vier fuenf sechs sieben acht neun zehn elf zwoelf dreizehn vierzehn fuenfzehn sechzehn siebzehn achtzehn neunzehn zwanzig einundzwanzig zweiundzwanzig dreiundzwanzig vierundzwanzig fuenfundzwanzig sechsundzwanzig siebenundzwanzig achtundzwanzig neunundzwanzig dreissig einunddreissig zweiunddreissig dreiunddreissig");
        eq(Fixture.correct("Meine Katze heisst Luna",
            Arrays.asList(aVisible,mixedArabic),NOW), "",
            "mixed Arabic and German rival cannot be dismissed as unrelated");
        eq(Fixture.correct("Meine Katze heisst Luna",
            Arrays.asList(mixedCjk,aVisible),NOW), "",
            "mixed CJK and German rival correction must abstain independent of order");
        eq(Fixture.correct("Meine Katze heisst Luna",
            Arrays.asList(aVisible,brokenLong),NOW), "",
            "oversized tokenizer vocabulary on source rival is unknown, not irrelevant");
        eq(top("Meine Katze heisst Luna",Arrays.asList(aVisible,mixedArabic)),
            "script-visible", "provider retrieval remains strict while correction abstains");
        eq(Fixture.correct("Meine Katze heisst Luna",
            Arrays.asList(aVisible,new CelineFutureMemoryRecall.Candidate(
                "script-expired", "Meine Katze heisst Luna عربي", "",
                CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,true,true,NOW-100,NOW-1,0.5)),NOW),
            "script-visible", "expired unindexable rival does not block correction");
        eq(Fixture.correct("Meine Katze heisst Luna",
            Arrays.asList(aVisible,mixedCjk),Collections.singleton("script-cjk"),NOW),
            "script-visible", "owner-retired rival cannot be auto-selected");
        eq(Fixture.correct("Meine Katze heisst Luna",
            Arrays.asList(aVisible,c("script-unrelated", "Damu Markt verkauft Getraenke")),NOW),
            "script-visible", "valid unrelated indexed rival does not block correction");

        // FUTURE-ONLY correction selection: zero tokenizer terms can either
        // mean a benign known stopword/punctuation-only rival, or UNKNOWN.
        // Never treat incomplete Unicode/over-budget/redacted evidence as benign.
        CelineFutureMemoryRecall.Candidate safeEmptyStopwords =
            c("plain-stopwords", "und oder aber die der ist ja wir nicht");
        CelineFutureMemoryRecall.Candidate safeEmptyPunctuation =
            c("plain-punctuation", "?! --- ... ;: ()");
        CelineFutureMemoryRecall.Candidate safeEmptyShortWords =
            c("plain-shortwords", "ab xy zu und der");
        eq(Fixture.correct("Meine Katze heisst Luna",
            Arrays.asList(aVisible,safeEmptyStopwords),NOW), "script-visible",
            "safe stopwords-only rival does not block unique correction");
        eq(Fixture.correct("Meine Katze heisst Luna",
            Arrays.asList(safeEmptyPunctuation,aVisible),NOW), "script-visible",
            "ASCII punctuation-only rival does not block unique correction independent of order");
        eq(Fixture.correct("Meine Katze heisst Luna",
            Arrays.asList(aVisible,safeEmptyShortWords),NOW), "script-visible",
            "known short-word-only rival does not block correction");
        eq(Fixture.correct("Meine Katze heisst Luna",
            Arrays.asList(aVisible,c("opaque-emoji", "❤️")),NOW), "",
            "emoji-only rival stays opaque rather than assumed unrelated");
        eq(Fixture.correct("Meine Katze heisst Luna",
            Arrays.asList(aVisible,c("opaque-cjk", "猫")),NOW), "",
            "unrepresentable script-only rival still blocks speculative correction");
        eq(Fixture.correct("Meine Katze heisst Luna",
            Arrays.asList(aVisible,c("opaque-invisible", "und oder\u200B")),NOW), "",
            "hidden control mark invalidates an otherwise stopword-only rival");
        eq(Fixture.correct("Meine Katze heisst Luna",
            Arrays.asList(aVisible,c("opaque-interleaved", "und Katze oder Luna")),NOW), "script-visible",
            "weak rival with two anchors cannot override three-anchor correction");
        eq(top("Meine Katze heisst Luna",Arrays.asList(aVisible,safeEmptyStopwords)),
            "script-visible", "provider retrieval remains unchanged with stopword-only rival");
        // A lexical-empty summary is not permission to disregard corrupt G1
        // metadata; malformed clocks and non-finite importance remain unknown.
        eq(Fixture.correct("Meine Katze heisst Luna",
            Arrays.asList(aVisible,new CelineFutureMemoryRecall.Candidate(
                "bad-clock-stops", "und der oder", "",
                CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,true,true,
                -5,0,0.5)),NOW), "",
            "negative G1 update timestamp must not be bypassed by stopword exception");
        eq(Fixture.correct("Meine Katze heisst Luna",
            Arrays.asList(aVisible,new CelineFutureMemoryRecall.Candidate(
                "bad-weight-stops", "und der oder", "",
                CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,true,true,
                NOW-100,0,Double.NaN)),NOW), "",
            "nonfinite G1 importance must not be bypassed by stopword exception");
        eq(Fixture.correct("Meine Katze heisst Luna",
            Arrays.asList(aVisible,c("safe-stop-with-umlaut", "für und zum bitte")),NOW),
            "script-visible", "German umlaut-normalized stopword remains known empty evidence");
        eq(Fixture.correct("Meine Katze heisst Luna",
            Arrays.asList(aVisible,c("opaque-non-german-accent", "und oder dïe")),NOW),
            "", "non-allowlisted Unicode cannot be dismissed as stopwords");
        eq(Fixture.correct("Meine Katze heisst Luna",
            Arrays.asList(aVisible,c("opaque-redacted", "\u200B")),NOW),
            "", "redacted or invalid mark-only rival never becomes benign punctuation");
        // A G1-owner-confirmed pruned parent permits the precise winner to be
        // recalled. It does NOT certify unrelated orphan lineage as resolved.
        // Correction by natural language needs stronger protection than recall:
        // a weaker but still on-topic quarantined rival may be the intended ID.
        Set<String> certifiedRetired = Collections.singleton("owner-pruned-old");
        CelineFutureMemoryRecall.Candidate certifiedWinner =
            x("owner-certified-winner", "Meine Katze heisst Luna",
                "owner-pruned-old", true, 0);
        CelineFutureMemoryRecall.Candidate unverifiedOrphan =
            x("other-live-orphan", "Katze heisst Max",
                "other-missing-ancestor", true, 0);
        eq(Fixture.retrieve("Meine Katze heisst Luna",
            Arrays.asList(certifiedWinner,unverifiedOrphan),
            certifiedRetired,certifiedRetired,8,NOW).get(0).candidate.id,
            "owner-certified-winner", "owner certified parent allows only safe winner retrieval");
        eq(Fixture.correct("Meine Katze heisst Luna",
            Arrays.asList(certifiedWinner,unverifiedOrphan),
            certifiedRetired,certifiedRetired,NOW), "",
            "withheld weaker orphan on same subject makes correction ID ambiguous");
        eq(Fixture.correct("Meine Katze heisst Luna",
            Arrays.asList(unverifiedOrphan,certifiedWinner),
            certifiedRetired,certifiedRetired,NOW), "",
            "withheld weaker orphan ambiguity is order independent");
        CelineFutureMemoryRecall.Candidate malformedClockWeakRival =
            new CelineFutureMemoryRecall.Candidate("weak-corrupt-clock", "Katze heisst Max", "",
                CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,true,true,-1,0,0.5);
        eq(Fixture.correct("Meine Katze heisst Luna",
            Arrays.asList(certifiedWinner,malformedClockWeakRival),
            certifiedRetired,certifiedRetired,NOW), "",
            "on-topic malformed timestamp may still be correction target");
        CelineFutureMemoryRecall.Candidate malformedWeightWeakRival =
            new CelineFutureMemoryRecall.Candidate("weak-corrupt-weight", "Katze heisst Max", "",
                CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,true,true,NOW-100,0,Double.NaN);
        eq(Fixture.correct("Meine Katze heisst Luna",
            Arrays.asList(certifiedWinner,malformedWeightWeakRival),
            certifiedRetired,certifiedRetired,NOW), "",
            "on-topic malformed relevance may still be correction target");
        eq(Fixture.correct("Meine Katze heisst Luna",
            Arrays.asList(certifiedWinner,x("unrelated-orphan", "Damu Markt verkauft Getraenke",
                "other-missing-ancestor",true,0)), certifiedRetired,certifiedRetired,NOW),
            "owner-certified-winner", "clearly unrelated orphan does not block unique correction");
        eq(Fixture.correct("Meine Katze heisst Luna",
            Arrays.asList(certifiedWinner, new CelineFutureMemoryRecall.Candidate(
                "expired-orphan", "Katze heisst Max", "other-missing-ancestor",
                CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,true,true,NOW-100,NOW-1,0.5)),
            certifiedRetired,certifiedRetired,NOW), "owner-certified-winner",
            "truly expired weaker orphan cannot be a current correction target");
        eq(Fixture.correct("Meine Katze heisst Luna",
            Arrays.asList(certifiedWinner,unverifiedOrphan),
            new HashSet<>(Arrays.asList("owner-pruned-old","other-live-orphan")),
            certifiedRetired,NOW), "owner-certified-winner",
            "source-retired orphan is not a candidate correction target");
        // A quarantined ancestor, unresolved conflict endpoint, or fork can
        // hide a correction rival even when its own parent ID looks present.
        CelineFutureMemoryRecall.Candidate orphanGrandparent =
            x("other-orphan-mid", "Anderes Thema", "unseen-unverified-root", true, 0);
        CelineFutureMemoryRecall.Candidate orphanGrandchild =
            x("other-orphan-descendant", "Katze heisst Max", "other-orphan-mid", true, 0);
        eq(Fixture.correct("Meine Katze heisst Luna",
            Arrays.asList(certifiedWinner,orphanGrandparent,orphanGrandchild),
            certifiedRetired,certifiedRetired,NOW), "",
            "transitive orphan descendant with two anchors blocks guessed correction");
        CelineFutureMemoryRecall.Candidate conflictWeakRival =
            new CelineFutureMemoryRecall.Candidate("other-conflict", "Katze heisst Max", "",
                "absent-opponent", CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,
                true,true,NOW-100,0,0.5);
        eq(Fixture.correct("Meine Katze heisst Luna",
            Arrays.asList(certifiedWinner,conflictWeakRival),
            certifiedRetired,certifiedRetired,NOW), "",
            "unresolved on-topic conflict is not dismissed by higher lexical score");
        CelineFutureMemoryRecall.Candidate forkParent =
            c("other-fork-parent", "Anderes Thema");
        CelineFutureMemoryRecall.Candidate forkWeakRival =
            x("other-fork-rival", "Katze heisst Max", "other-fork-parent",true,0);
        CelineFutureMemoryRecall.Candidate forkSibling =
            x("other-fork-sibling", "Damu Markt verkauft Getraenke", "other-fork-parent",true,0);
        eq(Fixture.correct("Meine Katze heisst Luna",
            Arrays.asList(certifiedWinner,forkParent,forkWeakRival,forkSibling),
            certifiedRetired,certifiedRetired,NOW), "",
            "quarantined fork branch with two anchors blocks speculative ID");
        eq(Fixture.correct("Meine Katze heisst Luna",
            Arrays.asList(certifiedWinner,c("safe-cat-extra", "Meine Katze")),
            certifiedRetired,certifiedRetired,NOW), "owner-certified-winner",
            "ordinary weaker independent relevant record keeps existing ranking rule");
        // Corrupt cyclic G1 supersession is not authoritative retirement:
        // retrieval hides both cycle nodes, but natural-language correction
        // must not use their invisibility to guess a different memory ID.
        CelineFutureMemoryRecall.Candidate cycleA =
            x("unknown-cycle-a", "Katze heisst Max", "unknown-cycle-b",true,0);
        CelineFutureMemoryRecall.Candidate cycleB =
            x("unknown-cycle-b", "Anderes Thema", "unknown-cycle-a",true,0);
        eq(Fixture.correct("Meine Katze heisst Luna",
            Arrays.asList(certifiedWinner,cycleA,cycleB),
            certifiedRetired,certifiedRetired,NOW), "",
            "source metadata cycle with on-topic suppressed rival blocks automatic correction");
        CelineFutureMemoryRecall.Candidate selfLoop =
            x("unknown-self-loop", "Katze heisst Max", "unknown-self-loop",true,0);
        eq(Fixture.correct("Meine Katze heisst Luna",
            Arrays.asList(certifiedWinner,selfLoop),
            certifiedRetired,certifiedRetired,NOW), "",
            "self-referential retraction cannot authorize correction of other fact");
        eq(Fixture.correct("Meine Katze heisst Luna",
            Arrays.asList(certifiedWinner,x("unrelated-self-loop", "Damu Markt verkauft Getraenke",
                "unrelated-self-loop",true,0)),certifiedRetired,certifiedRetired,NOW),
            "owner-certified-winner", "unrelated corrupt retraction does not suppress well-grounded match");
        // A successor that looks freshly authoritative is NOT a clean winner
        // when its lineage descends from a cycle. Quarantine is transitive.
        CelineFutureMemoryRecall.Candidate cycleChild =
            x("unknown-cycle-child", "Katze heisst Max", "unknown-cycle-a", true, 0);
        eq(Fixture.correct("Meine Katze heisst Luna",
            Arrays.asList(certifiedWinner,cycleA,cycleB,cycleChild),
            certifiedRetired,certifiedRetired,NOW), "",
            "on-topic cycle descendant cannot be guessed past unresolved parent cycle");
        eq(Fixture.correct("Meine Katze heisst Luna",
            Arrays.asList(cycleChild,cycleB,certifiedWinner,cycleA),
            certifiedRetired,certifiedRetired,NOW), "",
            "on-topic cycle descendant quarantine independent of source ordering");
        eq(Fixture.retrieve("Katze heisst Max",
            Arrays.asList(cycleA,cycleB,cycleChild),
            Collections.<String>emptySet(), Collections.<String>emptySet(),8,NOW).size(),0,
            "provider retrieval never exposes descendant from unresolved cyclic ancestry");
        CelineFutureMemoryRecall.Candidate unrelatedCycleChild =
            x("unrelated-cycle-child", "Damu Markt verkauft Getraenke", "unknown-cycle-a", true, 0);
        eq(Fixture.correct("Meine Katze heisst Luna",
            Arrays.asList(certifiedWinner,cycleA,cycleB,unrelatedCycleChild),
            certifiedRetired,certifiedRetired,NOW), "",
            "cycle ancestor remains an on-topic unknown rival even when descendant is unrelated");
        CelineFutureMemoryRecall.Candidate cycleGrandchild =
            x("unknown-cycle-grandchild", "Katze heisst Felix", "unknown-cycle-child", true, 0);
        eq(Fixture.correct("Meine Katze heisst Luna",
            Arrays.asList(certifiedWinner,cycleA,cycleB,cycleChild,cycleGrandchild),
            certifiedRetired,certifiedRetired,NOW), "",
            "two-generation cycle descendant remains a withheld ambiguous correction target");
        CelineFutureMemoryRecall.Candidate unrelatedCycleA =
            x("unrelated-cycle-a", "Damu Markt verkauft Getraenke", "unrelated-cycle-b", true, 0);
        CelineFutureMemoryRecall.Candidate unrelatedCycleB =
            x("unrelated-cycle-b", "Damu Markt hat Snacks", "unrelated-cycle-a", true, 0);
        CelineFutureMemoryRecall.Candidate unrelatedCycleTail =
            x("unrelated-cycle-tail", "Damu Markt hat Brot", "unrelated-cycle-a", true, 0);
        eq(Fixture.correct("Meine Katze heisst Luna",
            Arrays.asList(certifiedWinner,unrelatedCycleA,unrelatedCycleB,unrelatedCycleTail),
            certifiedRetired,certifiedRetired,NOW), "owner-certified-winner",
            "unrelated cyclic lineage does not spuriously suppress unique cat correction");
        eq(Fixture.retrieve("Meine Katze heisst Luna",
            Arrays.asList(certifiedWinner,unrelatedCycleA,unrelatedCycleB,unrelatedCycleTail),
            certifiedRetired,certifiedRetired,8,NOW).get(0).candidate.id,
            "owner-certified-winner", "unrelated cyclic lineage leaves provider-safe winner visible");
        // Security boundary: caller-claimed confirmedRetiredParentIds can no
        // longer unlock a pruned ancestor via the PUBLIC scalar-set overloads.
        List<CelineFutureMemoryRecall.Candidate> signedProjection = Arrays.asList(
            x("signed-kitten", "Meine Katze heisst Luna", "signed-retired", true, 0));
        Set<String> signedRetired = Collections.singleton("signed-retired");
        eq(CelineFutureMemoryRecall.retrieve("Meine Katze heisst Luna", signedProjection,
            signedRetired,signedRetired,8,NOW).size(),0,
            "unauthenticated confirmed ID is not a provider access certificate");
        eq(CelineFutureMemoryRecall.unambiguousCorrectionTarget("Meine Katze heisst Luna",
            signedProjection,signedRetired,signedRetired,NOW),"",
            "unauthenticated confirmed ID cannot authorize auto-correction");
        eq(CelineFutureMemoryRecall.retrieve("Meine Katze heisst Luna", signedProjection,
            signedRetired,Collections.<String>emptySet(),8,NOW).size(),0,
            "bare tombstone still not a resolved-parent proof");
        byte[] ownerSecret = new byte[32];
        Arrays.fill(ownerSecret,(byte)0x37); // deterministic TEST-ONLY fixture secret
        String generation = "owner-snapshot-7";
        byte[] signedBytes = CelineFutureMemoryRecall.certificatePayload(
            generation,signedProjection,signedRetired,signedRetired);
        javax.crypto.Mac signer = javax.crypto.Mac.getInstance("HmacSHA256");
        signer.init(new javax.crypto.spec.SecretKeySpec(ownerSecret,"HmacSHA256"));
        byte[] tag = signer.doFinal(signedBytes);
        CelineFutureMemoryRecall.OwnerLineageCertificate proof =
            new CelineFutureMemoryRecall.OwnerLineageCertificate(
                generation,signedRetired,signedRetired,tag);
        CelineFutureMemoryRecall.OwnerLineageVerifier trustedVerifier =
            new CelineFutureMemoryRecall.OwnerLineageVerifier(ownerSecret,generation);
        eq(CelineFutureMemoryRecall.retrieveCertified("Meine Katze heisst Luna",
            signedProjection,proof,trustedVerifier,8,NOW).get(0).candidate.id,
            "signed-kitten","verified source-pinned snapshot can recall resolved child");
        eq(CelineFutureMemoryRecall.unambiguousCorrectionTargetCertified(
            "Meine Katze heisst Luna",signedProjection,proof,trustedVerifier,NOW),
            "signed-kitten","verified snapshot permits precise ID suggestion");
        byte[] wrongKey = new byte[32];
        Arrays.fill(wrongKey,(byte)0x36);
        CelineFutureMemoryRecall.OwnerLineageVerifier wrongVerifier =
            new CelineFutureMemoryRecall.OwnerLineageVerifier(wrongKey,generation);
        eq(CelineFutureMemoryRecall.retrieveCertified("Meine Katze heisst Luna",
            signedProjection,proof,wrongVerifier,8,NOW).size(),0,
            "different owner key rejects forged/foreign certificate");
        eq(CelineFutureMemoryRecall.retrieveCertified("Meine Katze heisst Luna",
            signedProjection,proof,new CelineFutureMemoryRecall.OwnerLineageVerifier(
                ownerSecret,"owner-snapshot-8"),8,NOW).size(),0,
            "trusted generation mismatch rejects stale replay");
        eq(CelineFutureMemoryRecall.retrieveCertified("Meine Katze heisst Luna",
            Arrays.asList(x("signed-kitten", "Meine Katze heisst Max", "signed-retired",true,0)),
            proof,trustedVerifier,8,NOW).size(),0,
            "changed memory body is not authenticated by stale certificate");
        eq(CelineFutureMemoryRecall.retrieveCertified("Meine Katze heisst Luna",
            Arrays.asList(x("signed-kitten", "Meine Katze heisst Luna", "other-parent",true,0)),
            proof,trustedVerifier,8,NOW).size(),0,
            "tampered predecessor edge rejected");
        byte[] tamperedTag=tag.clone(); tamperedTag[0]^=1;
        eq(CelineFutureMemoryRecall.retrieveCertified("Meine Katze heisst Luna",
            signedProjection,new CelineFutureMemoryRecall.OwnerLineageCertificate(
                generation,signedRetired,signedRetired,tamperedTag),trustedVerifier,8,NOW).size(),0,
            "tampered MAC rejected without exceptions");
        eq(CelineFutureMemoryRecall.unambiguousCorrectionTargetCertified(
            "Meine Katze heisst Luna",signedProjection,proof,null,NOW),"",
            "missing independent verifier always abstains");
        eq(CelineFutureMemoryRecall.retrieveCertified("Meine Katze heisst Luna",
            signedProjection,proof,null,8,NOW).size(),0,
            "provider does not accept proof without source verifier");
        eq(CelineFutureMemoryRecall.retrieveCertified("Meine Katze heisst Luna",
            signedProjection,new CelineFutureMemoryRecall.OwnerLineageCertificate(
                generation,signedRetired,Collections.<String>emptySet(),tag),
            trustedVerifier,8,NOW).size(),0,
            "certificate cannot substitute modified retired-parent claims");
        // Certificate object and owner secret are defensively copied.
        byte[] mutationTag=tag.clone();
        CelineFutureMemoryRecall.OwnerLineageCertificate immutableProof =
            new CelineFutureMemoryRecall.OwnerLineageCertificate(
                generation,signedRetired,signedRetired,mutationTag);
        mutationTag[0]^=1;
        ownerSecret[0]^=1;
        eq(CelineFutureMemoryRecall.retrieveCertified("Meine Katze heisst Luna",
            signedProjection,immutableProof,trustedVerifier,8,NOW).get(0).candidate.id,
            "signed-kitten","caller byte-array mutation does not change pinned proof/key");
        eq(CelineFutureMemoryRecall.certificatePayload(generation, Arrays.asList(
            x("a", "Katze Luna", "signed-retired",true,0),
            x("a", "Katze Max", "signed-retired",true,0)),
            signedRetired,signedRetired).length,0,
            "certificate encoding rejects duplicate source IDs");
        // Replay challenge (POST-v83, NO G1 production anti-rollback integration):
        // A deleted/rewritten fact's originally valid HMAC is NOT revoked just
        // because a newer owner snapshot exists elsewhere. An obsolete pinned
        // verifier can replay that old fact. The REAL G1 adapter must atomically
        // protect its current generation across crash/restart/device restore,
        // then pin every verifier to that protected value, NOT to caller input.
        byte[] replayKey = new byte[32];
        Arrays.fill(replayKey,(byte)0x4a); // synthetic TEST-ONLY signing material
        Set<String> replayRetired = Collections.singleton("replay-root");
        String revisionOld = "g1-revision-0000000007";
        String revisionNew = "g1-revision-0000000008";
        List<CelineFutureMemoryRecall.Candidate> oldProjection = Arrays.asList(
            x("replay-luna", "Meine Katze heisst Luna", "replay-root",true,0));
        List<CelineFutureMemoryRecall.Candidate> newProjection = Arrays.asList(
            x("replay-momo", "Meine Katze heisst Momo", "replay-root",true,0));
        javax.crypto.Mac replaySigner = javax.crypto.Mac.getInstance("HmacSHA256");
        replaySigner.init(new javax.crypto.spec.SecretKeySpec(replayKey,"HmacSHA256"));
        CelineFutureMemoryRecall.OwnerLineageCertificate oldRevisionProof =
            new CelineFutureMemoryRecall.OwnerLineageCertificate(revisionOld,
                replayRetired,replayRetired,replaySigner.doFinal(
                    CelineFutureMemoryRecall.certificatePayload(revisionOld,
                        oldProjection,replayRetired,replayRetired)));
        CelineFutureMemoryRecall.OwnerLineageCertificate newRevisionProof =
            new CelineFutureMemoryRecall.OwnerLineageCertificate(revisionNew,
                replayRetired,replayRetired,replaySigner.doFinal(
                    CelineFutureMemoryRecall.certificatePayload(revisionNew,
                        newProjection,replayRetired,replayRetired)));
        CelineFutureMemoryRecall.OwnerLineageVerifier stalePinnedOwner =
            new CelineFutureMemoryRecall.OwnerLineageVerifier(replayKey,revisionOld);
        CelineFutureMemoryRecall.OwnerLineageVerifier refreshedPinnedOwner =
            new CelineFutureMemoryRecall.OwnerLineageVerifier(replayKey,revisionNew);
        // EXPECTED KNOWN RISK, not a security PASS: obsolete trust state is
        // itself stale; this deliberately demonstrates the replay vulnerability.
        eq(CelineFutureMemoryRecall.retrieveCertified("Meine Katze heisst Luna",
            oldProjection,oldRevisionProof,stalePinnedOwner,8,NOW).size(),1,
            "KNOWN RISK: old authentically signed memory can replay under stale owner pin");
        eq(CelineFutureMemoryRecall.unambiguousCorrectionTargetCertified(
            "Meine Katze heisst Luna",oldProjection,oldRevisionProof,stalePinnedOwner,NOW),
            "replay-luna","KNOWN RISK: old pinned owner can suggest deleted ID");
        eq(CelineFutureMemoryRecall.retrieveCertified("Meine Katze heisst Luna",
            oldProjection,oldRevisionProof,refreshedPinnedOwner,8,NOW).size(),0,
            "owner-refreshed revision rejects authenticated old retrieval");
        eq(CelineFutureMemoryRecall.unambiguousCorrectionTargetCertified(
            "Meine Katze heisst Luna",oldProjection,oldRevisionProof,refreshedPinnedOwner,NOW),
            "","owner-refreshed revision rejects stale correction target");
        eq(CelineFutureMemoryRecall.retrieveCertified("Meine Katze heisst Momo",
            newProjection,newRevisionProof,refreshedPinnedOwner,8,NOW).get(0).candidate.id,
            "replay-momo","current owner snapshot still retrieves valid correction");
        eq(CelineFutureMemoryRecall.unambiguousCorrectionTargetCertified(
            "Meine Katze heisst Momo",newProjection,newRevisionProof,refreshedPinnedOwner,NOW),
            "replay-momo","current owner snapshot still selects verified ID");
        eq(CelineFutureMemoryRecall.retrieveCertified("Meine Katze heisst Momo",
            newProjection,newRevisionProof,stalePinnedOwner,8,NOW).size(),0,
            "old generation verifier cannot authenticate current generation");
        eq(CelineFutureMemoryRecall.retrieveCertified("Meine Katze heisst Luna",
            oldProjection,newRevisionProof,refreshedPinnedOwner,8,NOW).size(),0,
            "new revision signature cannot certify old projection contents");
        eq(CelineFutureMemoryRecall.retrieveCertified("Meine Katze heisst Momo",
            newProjection,oldRevisionProof,refreshedPinnedOwner,8,NOW).size(),0,
            "old revision signature cannot certify current projection contents");
        byte[] forgedReplayKey = replayKey.clone();
        forgedReplayKey[0] ^= 1;
        eq(CelineFutureMemoryRecall.retrieveCertified("Meine Katze heisst Luna",
            oldProjection,oldRevisionProof,new CelineFutureMemoryRecall.OwnerLineageVerifier(
                forgedReplayKey,revisionOld),8,NOW).size(),0,
            "changed owner key rejects previously valid snapshot MAC");
        // A long-lived verifier must consult the OWNER'S CURRENT revision on
        // each lookup, not a revision cached when the Activity was created.
        // This remains a PROTOTYPE hook: the future G1 adapter must supply a
        // truly protected monotonic counter + consistent snapshot lease.
        final java.util.concurrent.atomic.AtomicReference<String> g1Revision =
            new java.util.concurrent.atomic.AtomicReference<>(revisionOld);
        CelineFutureMemoryRecall.OwnerLineageVerifier livePinnedOwner =
            new CelineFutureMemoryRecall.OwnerLineageVerifier(replayKey,
                new CelineFutureMemoryRecall.OwnerGenerationReader() {
                    public String currentGeneration() { return g1Revision.get(); }
                });
        eq(CelineFutureMemoryRecall.retrieveCertified("Meine Katze heisst Luna",
            oldProjection,oldRevisionProof,livePinnedOwner,8,NOW).size(),1,
            "live owner-reader permits original snapshot before legitimate correction");
        g1Revision.set(revisionNew);
        eq(CelineFutureMemoryRecall.retrieveCertified("Meine Katze heisst Luna",
            oldProjection,oldRevisionProof,livePinnedOwner,8,NOW).size(),0,
            "same long-lived verifier now rejects old proof after owner advances");
        eq(CelineFutureMemoryRecall.unambiguousCorrectionTargetCertified(
            "Meine Katze heisst Luna",oldProjection,oldRevisionProof,livePinnedOwner,NOW),
            "","same long-lived verifier rejects outdated correction suggestion");
        eq(CelineFutureMemoryRecall.retrieveCertified("Meine Katze heisst Momo",
            newProjection,newRevisionProof,livePinnedOwner,8,NOW).get(0).candidate.id,
            "replay-momo","live reader now accepts corrected G1 revision");
        eq(CelineFutureMemoryRecall.unambiguousCorrectionTargetCertified(
            "Meine Katze heisst Momo",newProjection,newRevisionProof,livePinnedOwner,NOW),
            "replay-momo","live reader permits corrected memory ID only");
        g1Revision.set(null);
        eq(CelineFutureMemoryRecall.retrieveCertified("Meine Katze heisst Momo",
            newProjection,newRevisionProof,livePinnedOwner,8,NOW).size(),0,
            "unavailable G1 generation source fails closed");
        eq(CelineFutureMemoryRecall.retrieveCertified("Meine Katze heisst Momo",
            newProjection,newRevisionProof,
            new CelineFutureMemoryRecall.OwnerLineageVerifier(replayKey,
                new CelineFutureMemoryRecall.OwnerGenerationReader() {
                    public String currentGeneration() { throw new IllegalStateException("storage offline"); }
                }),8,NOW).size(),0,
            "owner generation storage failure fails closed");
        final java.util.concurrent.atomic.AtomicInteger reads =
            new java.util.concurrent.atomic.AtomicInteger();
        eq(CelineFutureMemoryRecall.retrieveCertified("Meine Katze heisst Momo",
            newProjection,newRevisionProof,
            new CelineFutureMemoryRecall.OwnerLineageVerifier(replayKey,
                new CelineFutureMemoryRecall.OwnerGenerationReader() {
                    public String currentGeneration() {
                        return reads.incrementAndGet() == 1 ? revisionNew : revisionOld;
                    }
                }),8,NOW).size(),0,
            "generation changed during MAC verification must fail closed");
        g1Revision.set(revisionNew);
        eq(CelineFutureMemoryRecall.retrieveCertified("Meine Katze heisst Luna",
            oldProjection,oldRevisionProof,livePinnedOwner,8,NOW).size(),0,
            "recovered owner generation rejects replay after transient reader outage");
        g1Revision.set(revisionOld); // Simulated restore of an OLD G1 counter.
        eq(CelineFutureMemoryRecall.retrieveCertified("Meine Katze heisst Luna",
            oldProjection,oldRevisionProof,livePinnedOwner,8,NOW).size(),1,
            "KNOWN RISK: unprotected owner counter rollback still permits old signed proof");
        // RED/GREEN: owner revision may advance AFTER the MAC has been
        // verified but BEFORE the expensive policy has finished using records.
        // A final revision read after policy avoids knowingly returning a
        // snapshot already withdrawn at that point (not an atomic G1 lease).
        final java.util.concurrent.atomic.AtomicInteger readPasses =
            new java.util.concurrent.atomic.AtomicInteger();
        CelineFutureMemoryRecall.OwnerLineageVerifier changesAfterVerify =
            new CelineFutureMemoryRecall.OwnerLineageVerifier(replayKey,
                new CelineFutureMemoryRecall.OwnerGenerationReader() {
                    public String currentGeneration() {
                        return readPasses.incrementAndGet() <= 2 ? revisionNew : revisionOld;
                    }
                });
        eq(CelineFutureMemoryRecall.retrieveCertified("Meine Katze heisst Momo",
            newProjection,newRevisionProof,changesAfterVerify,8,NOW).size(),0,
            "owner revision advance after MAC must discard stale recall result");
        final java.util.concurrent.atomic.AtomicInteger correctionPasses =
            new java.util.concurrent.atomic.AtomicInteger();
        CelineFutureMemoryRecall.OwnerLineageVerifier changesBeforeCorrectionReturn =
            new CelineFutureMemoryRecall.OwnerLineageVerifier(replayKey,
                new CelineFutureMemoryRecall.OwnerGenerationReader() {
                    public String currentGeneration() {
                        return correctionPasses.incrementAndGet() <= 2 ? revisionNew : revisionOld;
                    }
                });
        eq(CelineFutureMemoryRecall.unambiguousCorrectionTargetCertified(
            "Meine Katze heisst Momo",newProjection,newRevisionProof,
            changesBeforeCorrectionReturn,NOW),"",
            "owner revision advance after MAC must discard stale correction ID");
        // Lease consumer contract: unlike a returned List<Match>, G1 must keep
        // its authoritative read lock until provider-context consumption ends.
        final java.util.concurrent.locks.ReentrantReadWriteLock ownerLock =
            new java.util.concurrent.locks.ReentrantReadWriteLock(true);
        final java.util.concurrent.atomic.AtomicReference<String> leasedGeneration =
            new java.util.concurrent.atomic.AtomicReference<>(revisionNew);
        final java.util.concurrent.atomic.AtomicInteger opens =
            new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger closes =
            new java.util.concurrent.atomic.AtomicInteger();
        CelineFutureMemoryRecall.OwnerLineageVerifier leaseVerifier =
            new CelineFutureMemoryRecall.OwnerLineageVerifier(replayKey,
                new CelineFutureMemoryRecall.OwnerGenerationReader() {
                    public String currentGeneration() { return leasedGeneration.get(); }
                }, new CelineFutureMemoryRecall.OwnerSnapshotLeaseAuthority() {
                    public CelineFutureMemoryRecall.OwnerSnapshotReadLease acquireReadLease() {
                        ownerLock.readLock().lock();
                        opens.incrementAndGet();
                        return new CelineFutureMemoryRecall.OwnerSnapshotReadLease() {
                            public String generation() { return leasedGeneration.get(); }
                            public void close() {
                                closes.incrementAndGet();
                                ownerLock.readLock().unlock();
                            }
                        };
                    }
                });
        final java.util.concurrent.atomic.AtomicInteger deliveries =
            new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicReference<String> deliveredId =
            new java.util.concurrent.atomic.AtomicReference<>("");
        CelineFutureMemoryRecall.ProviderRecallConsumer sink =
            new CelineFutureMemoryRecall.ProviderRecallConsumer() {
                public void consume(List<CelineFutureMemoryRecall.Match> approved) {
                    deliveries.incrementAndGet();
                    deliveredId.set(approved.isEmpty() ? "" : approved.get(0).candidate.id);
                    // Even THIS thread cannot upgrade the G1 read lock into a
                    // writer while context is being synchronously assembled.
                    eq(ownerLock.writeLock().tryLock(),false,
                        "revocation cannot enter while certified provider consumer holds read lease");
                    try {
                        approved.clear();
                        throw new AssertionError("approved matches were mutable");
                    } catch (UnsupportedOperationException expected) {
                        passed++;
                    }
                }
            };
        eq(CelineFutureMemoryRecall.consumeCertifiedProviderContext(
            "Meine Katze heisst Momo",newProjection,newRevisionProof,leaseVerifier,8,NOW,sink),
            true,"owner lease allows current certified provider context consumption");
        eq(deliveredId.get(),"replay-momo","owner lease delivers only current exact memory");
        eq(closes.get(),opens.get(),"successful consumer releases owner lease");
        eq(ownerLock.writeLock().tryLock(),true,"owner can revoke after consumption returns");
        leasedGeneration.set("g1-revision-0000000009");
        ownerLock.writeLock().unlock();
        eq(CelineFutureMemoryRecall.consumeCertifiedProviderContext(
            "Meine Katze heisst Momo",newProjection,newRevisionProof,leaseVerifier,8,NOW,sink),
            false,"revocation before provider consumption denies stale certificate");
        eq(deliveries.get(),1,"revoked certificate never reaches provider consumer");
        eq(closes.get(),opens.get(),"failed certificate releases owner lease");
        leasedGeneration.set(revisionNew);
        eq(CelineFutureMemoryRecall.consumeCertifiedProviderContext(
            "Meine Katze heisst Momo",newProjection,newRevisionProof,livePinnedOwner,8,NOW,sink),
            false,"reader-only verifier cannot authorize provider-context lease");
        eq(CelineFutureMemoryRecall.consumeCertifiedProviderContext(
            "Meine Katze heisst Momo",newProjection,newRevisionProof,stalePinnedOwner,8,NOW,sink),
            false,"fixed-generation verifier cannot authorize provider-context lease");
        eq(CelineFutureMemoryRecall.consumeCertifiedProviderContext(
            "Meine Katze heisst Momo",newProjection,null,leaseVerifier,8,NOW,sink),
            false,"missing certificate denies provider-context consumption");
        eq(CelineFutureMemoryRecall.consumeCertifiedProviderContext(
            "Meine Katze heisst Momo",newProjection,newRevisionProof,leaseVerifier,8,NOW,null),
            false,"missing sink denies provider-context consumption");
        eq(CelineFutureMemoryRecall.consumeCertifiedProviderContext(
            "Meine Katze heisst Luna",oldProjection,oldRevisionProof,leaseVerifier,8,NOW,sink),
            false,"replayed signed old revision cannot be consumed on current G1 lease");
        eq(CelineFutureMemoryRecall.consumeCertifiedProviderContext(
            "Meine Katze heisst Momo",oldProjection,newRevisionProof,leaseVerifier,8,NOW,sink),
            false,"tampered signed projection cannot reach provider context");
        eq(deliveries.get(),1,"all negative cases kept callback uninvoked");
        eq(closes.get(),opens.get(),"all negative cases release G1 read lease");
        final java.util.concurrent.atomic.AtomicInteger mismatchCalls =
            new java.util.concurrent.atomic.AtomicInteger();
        CelineFutureMemoryRecall.OwnerLineageVerifier mismatchedLease =
            new CelineFutureMemoryRecall.OwnerLineageVerifier(replayKey,
                new CelineFutureMemoryRecall.OwnerGenerationReader() {
                    public String currentGeneration() { return revisionNew; }
                }, new CelineFutureMemoryRecall.OwnerSnapshotLeaseAuthority() {
                    public CelineFutureMemoryRecall.OwnerSnapshotReadLease acquireReadLease() {
                        return new CelineFutureMemoryRecall.OwnerSnapshotReadLease() {
                            public String generation() { return revisionOld; }
                            public void close() { mismatchCalls.incrementAndGet(); }
                        };
                    }
                });
        eq(CelineFutureMemoryRecall.consumeCertifiedProviderContext(
            "Meine Katze heisst Momo",newProjection,newRevisionProof,mismatchedLease,8,NOW,sink),
            false,"lease generation mismatch denies even valid HMAC");
        eq(mismatchCalls.get(),1,"rejected mismatched lease is closed");
        CelineFutureMemoryRecall.OwnerLineageVerifier brokenLease =
            new CelineFutureMemoryRecall.OwnerLineageVerifier(replayKey,
                new CelineFutureMemoryRecall.OwnerGenerationReader() {
                    public String currentGeneration() { return revisionNew; }
                }, new CelineFutureMemoryRecall.OwnerSnapshotLeaseAuthority() {
                    public CelineFutureMemoryRecall.OwnerSnapshotReadLease acquireReadLease() {
                        throw new IllegalStateException("G1 snapshot busy");
                    }
                });
        eq(CelineFutureMemoryRecall.consumeCertifiedProviderContext(
            "Meine Katze heisst Momo",newProjection,newRevisionProof,brokenLease,8,NOW,sink),
            false,"unavailable owner lease fails closed");
        eq(deliveries.get(),1,"lease authority failures do not invoke provider sink");
        // A callback may have already emitted data before it throws. Returning
        // false would falsely tell a retrying caller that NO callback ran.
        final java.util.concurrent.atomic.AtomicInteger brokenConsumerCalls =
            new java.util.concurrent.atomic.AtomicInteger();
        boolean consumerFailureSurfaced = false;
        try {
            CelineFutureMemoryRecall.consumeCertifiedProviderContext(
                "Meine Katze heisst Momo",newProjection,newRevisionProof,leaseVerifier,8,NOW,
                approved -> {
                    brokenConsumerCalls.incrementAndGet();
                    throw new IllegalStateException("downstream context compiler crash");
                });
        } catch (IllegalStateException expected) { consumerFailureSurfaced = true; }
        eq(consumerFailureSurfaced,true,"started callback failures must not return retryable false");
        eq(brokenConsumerCalls.get(),1,"started failing callback invoked exactly once");
        eq(closes.get(),opens.get(),"downstream failure closes read lease");

        // The same ambiguity exists if the callback completed but lease close
        // failed. Report an indeterminate, nonretryable exception, not false.
        final java.util.concurrent.atomic.AtomicInteger failingCloseCalls =
            new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger closeSinkCalls =
            new java.util.concurrent.atomic.AtomicInteger();
        CelineFutureMemoryRecall.OwnerLineageVerifier failingCloseVerifier =
            new CelineFutureMemoryRecall.OwnerLineageVerifier(replayKey,
                () -> revisionNew, () -> new CelineFutureMemoryRecall.OwnerSnapshotReadLease() {
                    public String generation() { return revisionNew; }
                    public void close() {
                        failingCloseCalls.incrementAndGet();
                        throw new IllegalStateException("owner release failed");
                    }
                });
        boolean closeFailureSurfaced = false;
        try {
            CelineFutureMemoryRecall.consumeCertifiedProviderContext(
                "Meine Katze heisst Momo",newProjection,newRevisionProof,failingCloseVerifier,8,NOW,
                approved -> closeSinkCalls.incrementAndGet());
        } catch (IllegalStateException expected) { closeFailureSurfaced = true; }
        eq(closeFailureSurfaced,true,"post-callback lease-close failure must not return retryable false");
        eq(closeSinkCalls.get(),1,"close failure may follow an already delivered context");
        eq(failingCloseCalls.get(),1,"failing close attempted exactly once");

        // KNOWN UNSAFE: an untrusted callback can retain Match objects after
        // the read lease. Only a trusted provider-context sink can rule this out.
        final java.util.concurrent.atomic.AtomicReference<List<CelineFutureMemoryRecall.Match>> escaped =
            new java.util.concurrent.atomic.AtomicReference<>();
        eq(CelineFutureMemoryRecall.consumeCertifiedProviderContext(
            "Meine Katze heisst Momo",newProjection,newRevisionProof,leaseVerifier,8,NOW,
            approved -> escaped.set(approved)),true,"fixture can retain callback list");
        ownerLock.writeLock().lock();
        try { leasedGeneration.set("g1-revision-0000000010"); }
        finally { ownerLock.writeLock().unlock(); }
        eq(escaped.get().get(0).candidate.id,"replay-momo",
            "KNOWN UNSAFE: previously delivered Match remains readable after lease closes");
        System.out.println("FUTURE_G1_POST_LEASE_RETENTION_RISK=REPRODUCED_NOT_SOLVED");
        // Actual two-thread owner-lock regression: the write lock cannot
        // retire a certified snapshot while its synchronous sink is active.
        {
            final java.util.concurrent.atomic.AtomicReference<String> leaseRevision =
                new java.util.concurrent.atomic.AtomicReference<>(revisionNew);
            final java.util.concurrent.locks.ReentrantReadWriteLock lock =
                new java.util.concurrent.locks.ReentrantReadWriteLock(true);
            final java.util.concurrent.atomic.AtomicInteger closeCount =
                new java.util.concurrent.atomic.AtomicInteger();
            CelineFutureMemoryRecall.OwnerLineageVerifier concurrentVerifier =
                new CelineFutureMemoryRecall.OwnerLineageVerifier(replayKey,leaseRevision::get,() -> {
                    lock.readLock().lock();
                    return new CelineFutureMemoryRecall.OwnerSnapshotReadLease() {
                        public String generation() { return leaseRevision.get(); }
                        public void close() { closeCount.incrementAndGet(); lock.readLock().unlock(); }
                    };
                });
            final java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
            final java.util.concurrent.CountDownLatch finish = new java.util.concurrent.CountDownLatch(1);
            final java.util.concurrent.CountDownLatch writerStarted = new java.util.concurrent.CountDownLatch(1);
            final java.util.concurrent.CountDownLatch writerDone = new java.util.concurrent.CountDownLatch(1);
            final java.util.concurrent.atomic.AtomicReference<Boolean> consumed =
                new java.util.concurrent.atomic.AtomicReference<>();
            final java.util.concurrent.atomic.AtomicReference<Throwable> error =
                new java.util.concurrent.atomic.AtomicReference<>();
            Thread reader = new Thread(() -> {
                try {
                    consumed.set(CelineFutureMemoryRecall.consumeCertifiedProviderContext(
                        "Meine Katze heisst Momo",newProjection,newRevisionProof,concurrentVerifier,8,NOW,
                        matches -> {
                            if (matches.size() != 1 || !"replay-momo".equals(matches.get(0).candidate.id))
                                throw new AssertionError("unexpected certified memory");
                            entered.countDown();
                            try {
                                if (!finish.await(4,java.util.concurrent.TimeUnit.SECONDS))
                                    throw new IllegalStateException("reader timeout");
                            } catch (InterruptedException ex) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(ex);
                            }
                        }));
                } catch (Throwable ex) { error.set(ex); }
            }, "future-g1-reader");
            Thread writer = new Thread(() -> {
                writerStarted.countDown();
                lock.writeLock().lock();
                try { leaseRevision.set("lease-revision-after-revoke"); }
                finally { lock.writeLock().unlock(); writerDone.countDown(); }
            }, "future-g1-revoker");
            reader.start();
            try {
                eq(entered.await(4,java.util.concurrent.TimeUnit.SECONDS),true,"concurrent certified sink entered");
                writer.start();
                eq(writerStarted.await(4,java.util.concurrent.TimeUnit.SECONDS),true,"competing G1 revoker started");
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(4);
                while (!lock.hasQueuedThread(writer) && System.nanoTime() < deadline) Thread.onSpinWait();
                eq(lock.hasQueuedThread(writer),true,"revoker queued on reader-held G1 snapshot");
                eq(writerDone.getCount() == 1 && revisionNew.equals(leaseRevision.get()),true,
                    "owner revision unchanged before consumer returns");
            } finally { finish.countDown(); reader.join(4000); writer.join(4000); }
            eq(!reader.isAlive() && !writer.isAlive(),true,"both G1 threads completed within bound");
            eq(Boolean.TRUE.equals(consumed.get()) && error.get() == null,true,
                "consumer succeeded inside its own generation lease");
            eq(closeCount.get() == 1 && "lease-revision-after-revoke".equals(leaseRevision.get()),true,
                "lease closed before writer committed new revision");
            final java.util.concurrent.atomic.AtomicInteger staleSink =
                new java.util.concurrent.atomic.AtomicInteger();
            eq(CelineFutureMemoryRecall.consumeCertifiedProviderContext(
                "Meine Katze heisst Momo",newProjection,newRevisionProof,concurrentVerifier,8,NOW,
                matches -> staleSink.incrementAndGet()),false,"stale signed proof denied after concurrent revocation");
            eq(staleSink.get(),0,"revoked snapshot never emitted to sink");
            javax.crypto.Mac currentMac = javax.crypto.Mac.getInstance("HmacSHA256");
            currentMac.init(new javax.crypto.spec.SecretKeySpec(replayKey,"HmacSHA256"));
            CelineFutureMemoryRecall.OwnerLineageCertificate currentProof =
                new CelineFutureMemoryRecall.OwnerLineageCertificate(
                    "lease-revision-after-revoke",replayRetired,replayRetired,
                    currentMac.doFinal(CelineFutureMemoryRecall.certificatePayload(
                        "lease-revision-after-revoke",newProjection,
                        replayRetired,replayRetired)));
            final java.util.concurrent.atomic.AtomicInteger currentSink =
                new java.util.concurrent.atomic.AtomicInteger();
            eq(CelineFutureMemoryRecall.consumeCertifiedProviderContext(
                "Meine Katze heisst Momo",newProjection,currentProof,concurrentVerifier,8,NOW,
                matches -> { if (matches.size() == 1) currentSink.incrementAndGet(); }),true,
                "new signed generation accepted after revocation");
            eq(currentSink.get(),1,"new generation context delivered exactly once");
        }
        // FUTURE-ONLY owner-bound publication boundary: the lookup caller must
        // not inject an arbitrary Match-retaining callback per request. Only a
        // publisher installed with the (hypothetical) G1 source authority may
        // receive a minimal list of values, while the same owner read lease is
        // still held. These checks DO NOT authenticate a caller-built verifier.
        {
            final java.util.concurrent.atomic.AtomicReference<String> pubRevision =
                new java.util.concurrent.atomic.AtomicReference<>(revisionNew);
            final java.util.concurrent.locks.ReentrantReadWriteLock pubLock =
                new java.util.concurrent.locks.ReentrantReadWriteLock(true);
            final java.util.concurrent.atomic.AtomicInteger pubInvocations =
                new java.util.concurrent.atomic.AtomicInteger();
            final java.util.concurrent.atomic.AtomicInteger pubCloses =
                new java.util.concurrent.atomic.AtomicInteger();
            CelineFutureMemoryRecall.OwnerSnapshotLeaseAuthority pubLease = () -> {
                pubLock.readLock().lock();
                return new CelineFutureMemoryRecall.OwnerSnapshotReadLease() {
                    public String generation() { return pubRevision.get(); }
                    public void close() { pubCloses.incrementAndGet(); pubLock.readLock().unlock(); }
                };
            };
            CelineFutureMemoryRecall.OwnerContextPublisher trustedPublisher = (pubGeneration, summaries) -> {
                pubInvocations.incrementAndGet();
                eq(pubGeneration,revisionNew,"owner-bound publisher sees certified revision");
                eq(summaries.size(),1,"publisher gets only one approved summary");
                eq(summaries.get(0),"Meine Katze heisst Momo","publisher gets summary not Candidate/Match metadata");
                eq(pubLock.writeLock().tryLock(),false,"publisher runs inside G1 read lease");
                try {
                    summaries.clear();
                    throw new AssertionError("publication list mutable");
                } catch (UnsupportedOperationException expected) { passed++; }
            };
            CelineFutureMemoryRecall.OwnerLineageVerifier pubVerifier =
                new CelineFutureMemoryRecall.OwnerLineageVerifier(
                    replayKey,pubRevision::get,pubLease,trustedPublisher);
            CelineFutureMemoryRecall.OwnerLineageVerifier noPublisher =
                new CelineFutureMemoryRecall.OwnerLineageVerifier(
                    replayKey,pubRevision::get,pubLease);
            eq(CelineFutureMemoryRecall.publishCertifiedOwnerContext(
                "Meine Katze heisst Momo",newProjection,newRevisionProof,noPublisher,8,NOW),false,
                "no registered owner publisher denies lookup, never arbitrary caller callback");
            eq(CelineFutureMemoryRecall.publishCertifiedOwnerContext(
                "Meine Katze heisst Momo",newProjection,newRevisionProof,pubVerifier,8,NOW),true,
                "valid source-owner publisher receives current context under lease");
            eq(pubInvocations.get(),1,"owner publisher runs exactly once per accepted invocation");
            eq(pubCloses.get(),1,"owner publication releases read lease");
            pubRevision.set(revisionOld);
            eq(CelineFutureMemoryRecall.publishCertifiedOwnerContext(
                "Meine Katze heisst Momo",newProjection,newRevisionProof,pubVerifier,8,NOW),false,
                "revoked revision cannot reach owner-bound publication");
            eq(pubInvocations.get(),1,"stale revision cannot invoke publisher");
            eq(pubCloses.get(),2,"failed owner publication still releases lease");
            pubRevision.set(revisionNew);
            eq(CelineFutureMemoryRecall.publishCertifiedOwnerContext(
                "Meine Katze heisst Momo",oldProjection,newRevisionProof,pubVerifier,8,NOW),false,
                "tampered projection cannot reach owner-bound publisher");
            eq(pubInvocations.get(),1,"tampered candidate data never published");
            eq(pubCloses.get(),3,"tampered candidate proof closes lease");
            CelineFutureMemoryRecall.OwnerLineageVerifier badPublisher =
                new CelineFutureMemoryRecall.OwnerLineageVerifier(
                    replayKey,pubRevision::get,pubLease,(pubGeneration,summaries) -> {
                        pubInvocations.incrementAndGet();
                        throw new IllegalArgumentException("publication partially committed");
                    });
            boolean ambiguousPublish = false;
            try {
                CelineFutureMemoryRecall.publishCertifiedOwnerContext(
                    "Meine Katze heisst Momo",newProjection,newRevisionProof,badPublisher,8,NOW);
            } catch (IllegalStateException ex) {
                ambiguousPublish = ex.getMessage().contains("do not retry");
            }
            eq(ambiguousPublish,true,"owner publication started: no retryable false after exception");
            eq(pubInvocations.get(),2,"failing publisher never automatically replayed");
            eq(pubCloses.get(),4,"ambiguous publication still attempts lease close");
            eq(CelineFutureMemoryRecall.publishCertifiedOwnerContext(
                "Meine Katze heisst Momo",newProjection,null,pubVerifier,8,NOW),false,
                "missing owner lineage certificate never publishes");
            eq(pubInvocations.get(),2,"missing certificate does not enter owner publisher");
            final java.util.concurrent.atomic.AtomicInteger emptyValues =
                new java.util.concurrent.atomic.AtomicInteger(-1);
            CelineFutureMemoryRecall.OwnerLineageVerifier emptyVerifier =
                new CelineFutureMemoryRecall.OwnerLineageVerifier(
                    replayKey,pubRevision::get,pubLease,(pubGeneration,summaries) ->
                        emptyValues.set(summaries.size()));
            eq(CelineFutureMemoryRecall.publishCertifiedOwnerContext(
                "fremdes thema ohne treffer",newProjection,newRevisionProof,emptyVerifier,8,NOW),true,
                "valid owner snapshot can publish empty context without guessing a memory");
            eq(emptyValues.get(),0,"irrelevant query cannot expose a candidate to provider");
            eq(pubCloses.get(),5,"owner closes lease after invalid and empty publication");
            final java.util.concurrent.atomic.AtomicInteger closePublisherCalls =
                new java.util.concurrent.atomic.AtomicInteger();
            final java.util.concurrent.atomic.AtomicInteger closeAttempts =
                new java.util.concurrent.atomic.AtomicInteger();
            CelineFutureMemoryRecall.OwnerLineageVerifier closeBad =
                new CelineFutureMemoryRecall.OwnerLineageVerifier(
                    replayKey,pubRevision::get,() -> new CelineFutureMemoryRecall.OwnerSnapshotReadLease() {
                        public String generation() { return pubRevision.get(); }
                        public void close() {
                            closeAttempts.incrementAndGet();
                            throw new IllegalStateException("G1 lease close uncertain");
                        }
                    },(pubGeneration,summaries) -> closePublisherCalls.incrementAndGet());
            boolean closeIndeterminate = false;
            try {
                CelineFutureMemoryRecall.publishCertifiedOwnerContext(
                    "Meine Katze heisst Momo",newProjection,newRevisionProof,closeBad,8,NOW);
            } catch (IllegalStateException ex) {
                closeIndeterminate = ex.getMessage().contains("do not retry");
            }
            eq(closeIndeterminate,true,"owner publisher completed but close failed: indeterminate not retryable");
            eq(closePublisherCalls.get(),1,"owner publisher was already invoked exactly once");
            eq(closeAttempts.get(),1,"lease close attempted once after publication");
            final java.util.concurrent.atomic.AtomicReference<List<String>> pubRetained =
                new java.util.concurrent.atomic.AtomicReference<>();
            CelineFutureMemoryRecall.OwnerLineageVerifier retainingOwner =
                new CelineFutureMemoryRecall.OwnerLineageVerifier(
                    replayKey,pubRevision::get,pubLease,(pubGeneration,summaries) ->
                        pubRetained.set(summaries));
            eq(CelineFutureMemoryRecall.publishCertifiedOwnerContext(
                "Meine Katze heisst Momo",newProjection,newRevisionProof,retainingOwner,8,NOW),true,
                "even a registered owner sink can cache immutable plain-text context");
            pubRevision.set(revisionOld);
            eq(pubRetained.get().get(0),"Meine Katze heisst Momo",
                "KNOWN UNSAFE: retained owner strings remain readable after revocation");
            eq(CelineFutureMemoryRecall.publishCertifiedOwnerContext(
                "Meine Katze heisst Momo",newProjection,newRevisionProof,retainingOwner,8,NOW),false,
                "revoked generation denies new publication but cannot retract prior strings");
            System.out.println("FUTURE_G1_OWNER_TEXT_RETENTION_RISK=REPRODUCED_NOT_SOLVED");
            // The hypothetical owner itself can still retain strings; real
            // durable journal/rollback proof and trusted G1 wiring remain absent.
        }
        // Source-owner publication journal: started is durably reserved BEFORE
        // callback; callback return must be durably acknowledged before status
        // is completed. The disk-backed fixture exercises process-like restarts
        // but DOES NOT prove power-cut durability, owner authentication or a
        // production remote provider delivery acknowledgement.
        {
            final String query = "Meine Katze heisst Momo";
            final java.nio.file.Path store = java.nio.file.Files.createTempDirectory("future-g1-journal-");
            final java.util.concurrent.atomic.AtomicInteger publications =
                new java.util.concurrent.atomic.AtomicInteger();
            final java.util.concurrent.atomic.AtomicReference<String> revision =
                new java.util.concurrent.atomic.AtomicReference<>(revisionNew);
            final java.util.concurrent.atomic.AtomicInteger leaseCloses =
                new java.util.concurrent.atomic.AtomicInteger();
            CelineFutureMemoryRecall.OwnerSnapshotLeaseAuthority journalLease = () ->
                new CelineFutureMemoryRecall.OwnerSnapshotReadLease() {
                    public String generation() { return revision.get(); }
                    public void close() { leaseCloses.incrementAndGet(); }
                };
            CelineFutureMemoryRecall.OwnerContextPublisher publisher = (publishedGeneration, summaries) -> {
                publications.incrementAndGet();
                eq(publishedGeneration,revisionNew,"journal publisher receives certified generation");
                eq(summaries,java.util.Collections.singletonList("Meine Katze heisst Momo"),
                    "journal publisher receives only signed context text");
            };
            DiskJournal ledger = new DiskJournal(store);
            CelineFutureMemoryRecall.OwnerLineageVerifier journalOwner =
                new CelineFutureMemoryRecall.OwnerLineageVerifier(
                    replayKey,revision::get,journalLease,publisher,ledger);
            CelineFutureMemoryRecall.OwnerPublicationOutcome denied =
                CelineFutureMemoryRecall.OwnerPublicationOutcome.REJECTED_BEFORE_START;
            CelineFutureMemoryRecall.OwnerPublicationOutcome uncertain =
                CelineFutureMemoryRecall.OwnerPublicationOutcome.STARTED_INDETERMINATE;
            CelineFutureMemoryRecall.OwnerPublicationOutcome finished =
                CelineFutureMemoryRecall.OwnerPublicationOutcome.COMPLETED;
            eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextOnce(
                "send-1",query,newProjection,newRevisionProof,journalOwner,8,NOW),finished,
                "first journaled dispatch receives acknowledged completion");
            eq(ledger.actualReserves,1,"dispatch STARTED persisted exactly once");
            eq(ledger.actualAcknowledgements,1,"completion acknowledgement persisted once");
            eq(publications.get(),1,"callback invoked once for accepted dispatch");
            eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextOnce(
                "send-1",query,newProjection,newRevisionProof,journalOwner,8,NOW),finished,
                "completed duplicate returns status without replaying publisher");
            eq(publications.get(),1,"completed duplicate never publishes twice");
            DiskJournal restarted = new DiskJournal(store);
            CelineFutureMemoryRecall.OwnerLineageVerifier afterRestart =
                new CelineFutureMemoryRecall.OwnerLineageVerifier(
                    replayKey,revision::get,journalLease,publisher,restarted);
            eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextOnce(
                "send-1",query,newProjection,newRevisionProof,afterRestart,8,NOW),finished,
                "fresh journal instance sees durable completed receipt");
            eq(publications.get(),1,"restart cannot replay completed request");
            eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextOnce(
                "send-1","anderes Thema",newProjection,newRevisionProof,afterRestart,8,NOW),uncertain,
                "same owner request ID with different intent denied without republish");
            eq(publications.get(),1,"intent collision cannot reach publisher");
            eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextOnce(
                "send-1",query,newProjection,newRevisionProof,afterRestart,1,NOW),uncertain,
                "changing result bound under same request ID is an intent collision");
            eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextOnce(
                "send-1",query,newProjection,newRevisionProof,afterRestart,8,NOW+1),uncertain,
                "changing time under same request ID is an intent collision");
            eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextOnce(
                "bad/id",query,newProjection,newRevisionProof,afterRestart,8,NOW),denied,
                "malformed ID rejected before any journal attempt");
            eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextOnce(
                "send-invalid",query,newProjection,oldRevisionProof,afterRestart,8,NOW),denied,
                "stale HMAC rejected before journal reservation");
            eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextOnce(
                "send-invalid",query,newProjection,newRevisionProof,livePinnedOwner,8,NOW),denied,
                "reader-only verifier cannot dispatch without owner journal or lease");
            revision.set(revisionOld);
            eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextOnce(
                "send-revoked",query,newProjection,newRevisionProof,afterRestart,8,NOW),denied,
                "revocation before reservation denies dispatch");
            revision.set(revisionNew);
            DiskJournal crashReserved = new DiskJournal(store);
            crashReserved.crashAfterReserve = true;
            CelineFutureMemoryRecall.OwnerLineageVerifier crashOwner =
                new CelineFutureMemoryRecall.OwnerLineageVerifier(
                    replayKey,revision::get,journalLease,publisher,crashReserved);
            eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextOnce(
                "send-crash",query,newProjection,newRevisionProof,crashOwner,8,NOW),uncertain,
                "crash after durable STARTED can never look retryable");
            eq(publications.get(),1,"reservation crash happens before any callback");
            eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextOnce(
                "send-crash",query,newProjection,newRevisionProof,afterRestart,8,NOW),uncertain,
                "restarted process refuses to dispatch uncleared STARTED reservation");
            eq(publications.get(),1,"restart after reservation crash never duplicates");
            DiskJournal crashAck = new DiskJournal(store);
            crashAck.crashBeforeAck = true;
            CelineFutureMemoryRecall.OwnerLineageVerifier crashAckOwner =
                new CelineFutureMemoryRecall.OwnerLineageVerifier(
                    replayKey,revision::get,journalLease,publisher,crashAck);
            eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextOnce(
                "send-ack-crash",query,newProjection,newRevisionProof,crashAckOwner,8,NOW),uncertain,
                "crash after publisher returns but before durable acknowledgement");
            eq(publications.get(),2,"publisher may already have received crash-window content");
            eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextOnce(
                "send-ack-crash",query,newProjection,newRevisionProof,afterRestart,8,NOW),uncertain,
                "restart after missing acknowledgement must never resend");
            eq(publications.get(),2,"started record keeps at-most-once after acknowledgement crash");
            DiskJournal failBeforeReserve = new DiskJournal(store);
            failBeforeReserve.crashBeforeReserve = true;
            CelineFutureMemoryRecall.OwnerLineageVerifier journalUnavailable =
                new CelineFutureMemoryRecall.OwnerLineageVerifier(
                    replayKey,revision::get,journalLease,publisher,failBeforeReserve);
            eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextOnce(
                "send-disk-error",query,newProjection,newRevisionProof,journalUnavailable,8,NOW),uncertain,
                "unknown journal write outcome must not authorize blind retry");
            eq(publications.get(),2,"storage error prevents dispatch");
            CelineFutureMemoryRecall.OwnerContextPublisher failingPublisher = (publishedGeneration, summaries) -> {
                publications.incrementAndGet();
                throw new IllegalStateException("provider could have been given text");
            };
            CelineFutureMemoryRecall.OwnerLineageVerifier publisherCrashOwner =
                new CelineFutureMemoryRecall.OwnerLineageVerifier(
                    replayKey,revision::get,journalLease,failingPublisher,new DiskJournal(store));
            eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextOnce(
                "send-publisher-error",query,newProjection,newRevisionProof,publisherCrashOwner,8,NOW),uncertain,
                "callback throws after publication started: uncertain not retryable");
            eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextOnce(
                "send-publisher-error",query,newProjection,newRevisionProof,afterRestart,8,NOW),uncertain,
                "publisher error survives journal reload without replay");
            eq(publications.get(),3,"throwing publisher only ran once");
            // Concurrency: both calls use the same owner journal; the second
            // must observe STARTED before the first publisher is acknowledged.
            final java.util.concurrent.CountDownLatch began = new java.util.concurrent.CountDownLatch(1);
            final java.util.concurrent.CountDownLatch finish = new java.util.concurrent.CountDownLatch(1);
            final java.util.concurrent.atomic.AtomicInteger concurrentCalls =
                new java.util.concurrent.atomic.AtomicInteger();
            final java.util.concurrent.atomic.AtomicReference<CelineFutureMemoryRecall.OwnerPublicationOutcome>
                firstOutcome = new java.util.concurrent.atomic.AtomicReference<>();
            CelineFutureMemoryRecall.OwnerLineageVerifier parallelOwner =
                new CelineFutureMemoryRecall.OwnerLineageVerifier(
                    replayKey,revision::get,journalLease,(publishedGeneration,summaries) -> {
                        concurrentCalls.incrementAndGet();
                        began.countDown();
                        try {
                            if (!finish.await(5,java.util.concurrent.TimeUnit.SECONDS))
                                throw new IllegalStateException("test fixture timed out");
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(ex);
                        }
                    },new DiskJournal(store));
            Thread first = new Thread(() -> firstOutcome.set(
                CelineFutureMemoryRecall.publishCertifiedOwnerContextOnce(
                    "send-parallel",query,newProjection,newRevisionProof,parallelOwner,8,NOW)));
            first.start();
            eq(began.await(5,java.util.concurrent.TimeUnit.SECONDS),true,
                "parallel first dispatch reached owner callback");
            eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextOnce(
                "send-parallel",query,newProjection,newRevisionProof,parallelOwner,8,NOW),uncertain,
                "concurrent same-ID duplicate refused while first dispatch active");
            eq(concurrentCalls.get(),1,"parallel callback count remains one");
            finish.countDown();
            first.join(5000);
            eq(first.isAlive(),false,"first dispatch completes after release");
            eq(firstOutcome.get(),finished,"first concurrent publisher persisted acknowledgement");
            eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextOnce(
                "send-parallel",query,newProjection,newRevisionProof,parallelOwner,8,NOW),finished,
                "after concurrent first completes subsequent calls read completed receipt");
            eq(concurrentCalls.get(),1,"later duplicate never republishes concurrently completed ID");
            eq(leaseCloses.get() > 0,true,"all journaled paths exercised owner lease closing");
            // Defensive post-publisher generation recheck: a misconfigured or
            // broken G1-style read lease must never mark a stale context as
            // COMPLETED if owner revision changed inside the sink callback.
            final java.util.concurrent.atomic.AtomicInteger revokedPublisherCalls =
                new java.util.concurrent.atomic.AtomicInteger();
            DiskJournal revokedMidPublisherJournal = new DiskJournal(store);
            CelineFutureMemoryRecall.OwnerLineageVerifier revokedMidPublisher =
                new CelineFutureMemoryRecall.OwnerLineageVerifier(
                    replayKey,revision::get,journalLease,(publishedGeneration,summaries) -> {
                        revokedPublisherCalls.incrementAndGet();
                        revision.set(revisionOld);
                    },revokedMidPublisherJournal);
            eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextOnce(
                "send-revoked-mid-publisher",query,newProjection,newRevisionProof,
                revokedMidPublisher,8,NOW),uncertain,
                "revoked revision inside publisher must never acknowledge completed context");
            eq(revokedPublisherCalls.get(),1,
                "revoked callback was invoked once, so its result is indeterminate");
            eq(revokedMidPublisherJournal.actualAcknowledgements,0,
                "revoked publication must not persist a false COMPLETED receipt");
            revision.set(revisionNew);
            eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextOnce(
                "send-revoked-mid-publisher",query,newProjection,newRevisionProof,
                revokedMidPublisher,8,NOW),uncertain,
                "restoring generation must not replay a previously started publication");
            eq(revokedPublisherCalls.get(),1,
                "no duplicate sink invocation after a revoked started reservation");
            // A defective owner lease can revoke its generation DURING a journal
            // read. Even a previous COMPLETED receipt must not be asserted as
            // valid for the newly revoked context without post-journal recheck.
            final java.util.concurrent.atomic.AtomicReference<String> raceRevision =
                new java.util.concurrent.atomic.AtomicReference<>(revisionNew);
            final java.util.concurrent.atomic.AtomicInteger racePublisherCalls =
                new java.util.concurrent.atomic.AtomicInteger();
            CelineFutureMemoryRecall.OwnerPublicationJournal revokingReceipt =
                new CelineFutureMemoryRecall.OwnerPublicationJournal() {
                    public Reservation reserve(String requestId, byte[] intent) {
                        raceRevision.set(revisionOld);
                        return Reservation.ALREADY_COMPLETED;
                    }
                    public boolean acknowledgeCompleted(String requestId, byte[] intent) {
                        throw new AssertionError("unexpected acknowledgement on historical receipt");
                    }
                };
            CelineFutureMemoryRecall.OwnerLineageVerifier revokingOwner =
                new CelineFutureMemoryRecall.OwnerLineageVerifier(
                    replayKey,raceRevision::get,() -> new CelineFutureMemoryRecall.OwnerSnapshotReadLease() {
                        public String generation() { return raceRevision.get(); }
                        public void close() {}
                    },(publishedGeneration,summaries) -> racePublisherCalls.incrementAndGet(),
                    revokingReceipt);
            eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextOnce(
                "receipt-revoked",query,newProjection,newRevisionProof,revokingOwner,8,NOW),uncertain,
                "revoked generation during already-completed receipt must remain indeterminate");
            eq(racePublisherCalls.get(),0,"journal receipt path does not re-publish revoked data");
            // A broken G1 owner lease may revoke while the durable journal
            // writes its completed acknowledgement, AFTER our earlier check.
            // A positive ack must not be reported as current if this happens.
            final java.util.concurrent.atomic.AtomicReference<String> duringAckRevision =
                new java.util.concurrent.atomic.AtomicReference<>(revisionNew);
            final java.util.concurrent.atomic.AtomicInteger duringAckPublications =
                new java.util.concurrent.atomic.AtomicInteger();
            final java.util.concurrent.atomic.AtomicInteger duringAckWrites =
                new java.util.concurrent.atomic.AtomicInteger();
            final java.util.concurrent.atomic.AtomicBoolean durablyCompletedByLegacy =
                new java.util.concurrent.atomic.AtomicBoolean(false);
            final java.util.concurrent.atomic.AtomicBoolean duringAckReserved =
                new java.util.concurrent.atomic.AtomicBoolean();
            CelineFutureMemoryRecall.OwnerPublicationJournal revokingAck =
                new CelineFutureMemoryRecall.OwnerPublicationJournal() {
                    public Reservation reserve(String id, byte[] digest) {
                        return duringAckReserved.compareAndSet(false,true)
                            ? Reservation.NEW_STARTED : Reservation.ALREADY_STARTED;
                    }
                    public boolean acknowledgeCompleted(String id, byte[] digest) {
                        duringAckWrites.incrementAndGet();
                        durablyCompletedByLegacy.set(true);
                        duringAckRevision.set(revisionOld);
                        return true; // deliberately buggy simulated G1 journal
                    }
                };
            CelineFutureMemoryRecall.OwnerLineageVerifier ownerRevokedInAck =
                new CelineFutureMemoryRecall.OwnerLineageVerifier(
                    replayKey,duringAckRevision::get,() -> new CelineFutureMemoryRecall.OwnerSnapshotReadLease() {
                        public String generation() { return duringAckRevision.get(); }
                        public void close() {}
                    }, (ackGeneration,summaries) -> duringAckPublications.incrementAndGet(),
                    revokingAck);
            eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextOnce(
                "receipt-revoked-in-ack",query,newProjection,newRevisionProof,
                ownerRevokedInAck,8,NOW),uncertain,
                "revision revoked during positive acknowledgement cannot report COMPLETED");
            eq(duringAckWrites.get(),0,"legacy unsafe acknowledgement must never be called");
            eq(durablyCompletedByLegacy.get(),false,
                "revoke-on-legacy-ack cannot durably persist a false completed receipt");
            eq(duringAckRevision.get(),revisionNew,"legacy unsafe ack cannot change owner generation");
            eq(duringAckPublications.get(),1,"source was already published before the gated acknowledgement");
            duringAckRevision.set(revisionNew);
            eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextOnce(
                "receipt-revoked-in-ack",query,newProjection,newRevisionProof,
                ownerRevokedInAck,8,NOW),uncertain,
                "restoring current source does not replay a prior STARTED reservation");
            eq(duringAckPublications.get(),1,"ack-race duplicate cannot publish twice");
            // A *persisted* false COMPLETED receipt cannot be repaired by a
            // caller's post-ack recheck. The new capability instead delegates
            // the generation check + receipt commit to ONE G1-owner boundary.
            // This monitor is a JVM transaction model, NOT durable Android I/O.
            final Object transactionalOwner = new Object();
            final java.util.concurrent.atomic.AtomicReference<String> txGeneration =
                new java.util.concurrent.atomic.AtomicReference<>(revisionNew);
            final java.util.Map<String,Boolean> txReceipts = new java.util.HashMap<>();
            final java.util.Map<String,String> txIntents = new java.util.HashMap<>();
            final java.util.concurrent.atomic.AtomicInteger txPublishes =
                new java.util.concurrent.atomic.AtomicInteger();
            final java.util.concurrent.atomic.AtomicInteger txLegacyCalls =
                new java.util.concurrent.atomic.AtomicInteger();
            final java.util.concurrent.atomic.AtomicInteger txConditionalCalls =
                new java.util.concurrent.atomic.AtomicInteger();
            final java.util.concurrent.atomic.AtomicBoolean revokeInsideConditional =
                new java.util.concurrent.atomic.AtomicBoolean(true);
            CelineFutureMemoryRecall.OwnerPublicationJournal txJournal =
                new CelineFutureMemoryRecall.OwnerPublicationJournal() {
                    public Reservation reserve(String id, byte[] digest) {
                        synchronized (transactionalOwner) {
                            String intended = java.util.HexFormat.of().formatHex(digest);
                            if (txReceipts.containsKey(id)) return
                                !intended.equals(txIntents.get(id)) ? Reservation.INTENT_CONFLICT :
                                (txReceipts.get(id) ? Reservation.ALREADY_COMPLETED : Reservation.ALREADY_STARTED);
                            txIntents.put(id,intended);
                            txReceipts.put(id,false);
                            return Reservation.NEW_STARTED;
                        }
                    }
                    public boolean acknowledgeCompleted(String id, byte[] digest) {
                        txLegacyCalls.incrementAndGet();
                        throw new AssertionError("unsafe legacy acknowledgement called");
                    }
                    public boolean acknowledgeCompletedIfCurrent(
                            String id, byte[] digest, String certifiedGeneration) {
                        synchronized (transactionalOwner) {
                            txConditionalCalls.incrementAndGet();
                            // Deterministically model a revocation that wins
                            // the transaction BEFORE journal completion.
                            if (revokeInsideConditional.getAndSet(false))
                                txGeneration.set(revisionOld);
                            if (!java.util.Objects.equals(txGeneration.get(),certifiedGeneration)
                                    || !Boolean.FALSE.equals(txReceipts.get(id))
                                    || !java.util.Objects.equals(txIntents.get(id),
                                        java.util.HexFormat.of().formatHex(digest))) return false;
                            txReceipts.put(id,true);
                            return true;
                        }
                    }
                };
            CelineFutureMemoryRecall.OwnerLineageVerifier transactionalVerifier =
                new CelineFutureMemoryRecall.OwnerLineageVerifier(
                    replayKey,txGeneration::get,() -> new CelineFutureMemoryRecall.OwnerSnapshotReadLease() {
                        // Deliberately broken lease: only transactional G1 ack
                        // enforces the revision/COMPLETED linearization point.
                        public String generation() { return txGeneration.get(); }
                        public void close() {}
                    },(gen,summaries) -> txPublishes.incrementAndGet(),txJournal);
            eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextOnce(
                "tx-owner-revoked",query,newProjection,newRevisionProof,
                transactionalVerifier,8,NOW),uncertain,
                "owner revocation before conditional journal commit must stay indeterminate");
            eq(txReceipts.get("tx-owner-revoked"),false,
                "transactional owner leaves a durable-model STARTED not false COMPLETED");
            eq(txConditionalCalls.get(),1,"exactly one conditional owner ack attempted");
            eq(txLegacyCalls.get(),0,"direct legacy ack never used");
            eq(txPublishes.get(),1,"publisher was invoked before revocation, no safe retry");
            synchronized(transactionalOwner) { txGeneration.set(revisionNew); }
            eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextOnce(
                "tx-owner-revoked",query,newProjection,newRevisionProof,
                transactionalVerifier,8,NOW),uncertain,
                "recovered owner generation never replays STARTED transaction");
            eq(txPublishes.get(),1,"uncertain started transaction not republished");
            eq(txReceipts.get("tx-owner-revoked"),false,
                "journal STARTED remains after restart-like retry");
            eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextOnce(
                "tx-owner-success",query,newProjection,newRevisionProof,
                transactionalVerifier,8,NOW),finished,
                "second valid owner transaction may complete under same revision gate");
            eq(txReceipts.get("tx-owner-success"),true,
                "owner transaction commits only matching current source revision");
            eq(txJournal.reserve("tx-owner-success",new byte[32]),
                CelineFutureMemoryRecall.OwnerPublicationJournal.Reservation.INTENT_CONFLICT,
                "atomic owner rejects completed same-ID receipt with forged intent");
            eq(txJournal.acknowledgeCompletedIfCurrent("tx-owner-revoked",new byte[32],revisionNew),
                false,"atomic owner cannot acknowledge mismatched request intent digest");
            eq(txReceipts.get("tx-owner-revoked"),false,
                "wrong digest cannot upgrade prior uncertain STARTED receipt");
            eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextOnce(
                "tx-owner-success",query,newProjection,newRevisionProof,
                transactionalVerifier,8,NOW),finished,
                "verified completed duplicate returns receipt without republish");
            eq(txPublishes.get(),2,"completed same-ID duplicate never publishes twice");
            eq(txConditionalCalls.get(),3,
                "two dispatch acknowledgements and one directly rejected wrong-digest attempt");
            synchronized(transactionalOwner) { txGeneration.set(revisionOld); }
            eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextOnce(
                "tx-owner-success",query,newProjection,newRevisionProof,
                transactionalVerifier,8,NOW),denied,
                "revoked historical completion must not be accepted as current");
            eq(txPublishes.get(),2,"revoked source cannot redispatch saved completion");
            // Future-only deterministic G1 owner transaction model. The JVM
            // monitor simulates (does not implement) atomic durable G1 state.
            {
                final Object ownerFence = new Object();
                final java.util.concurrent.atomic.AtomicReference<String> ownerEpoch =
                    new java.util.concurrent.atomic.AtomicReference<>(revisionNew);
                final java.util.Map<String,String> intentsById = new java.util.HashMap<>();
                final java.util.Map<String,Boolean> receiptsById = new java.util.HashMap<>();
                final java.util.concurrent.atomic.AtomicInteger raceDeliveries =
                    new java.util.concurrent.atomic.AtomicInteger();
                final java.util.concurrent.atomic.AtomicInteger legacyAcks =
                    new java.util.concurrent.atomic.AtomicInteger();
                final java.util.concurrent.CountDownLatch beforeFence = new java.util.concurrent.CountDownLatch(1);
                final java.util.concurrent.CountDownLatch proceedFence = new java.util.concurrent.CountDownLatch(1);
                final java.util.concurrent.CountDownLatch heldFence = new java.util.concurrent.CountDownLatch(1);
                final java.util.concurrent.CountDownLatch releaseFence = new java.util.concurrent.CountDownLatch(1);
                class OwnerModelJournal implements CelineFutureMemoryRecall.OwnerPublicationJournal {
                    public Reservation reserve(String id, byte[] intent) {
                        synchronized(ownerFence) {
                            String digest = java.util.HexFormat.of().formatHex(intent);
                            if (receiptsById.containsKey(id)) return
                                !digest.equals(intentsById.get(id)) ? Reservation.INTENT_CONFLICT :
                                (receiptsById.get(id) ? Reservation.ALREADY_COMPLETED : Reservation.ALREADY_STARTED);
                            intentsById.put(id,digest);
                            receiptsById.put(id,false);
                            return Reservation.NEW_STARTED;
                        }
                    }
                    public boolean acknowledgeCompleted(String id, byte[] intent) {
                        legacyAcks.incrementAndGet();
                        throw new AssertionError("unsafe legacy completion");
                    }
                    public boolean acknowledgeCompletedIfCurrent(String id, byte[] intent, String epoch) {
                        try {
                            if ("revoke-before-ack".equals(id)) {
                                beforeFence.countDown();
                                if (!proceedFence.await(5,java.util.concurrent.TimeUnit.SECONDS))
                                    throw new IllegalStateException("pre-ack timeout");
                            }
                            synchronized(ownerFence) {
                                if ("ack-before-revoke".equals(id)) {
                                    heldFence.countDown();
                                    if (!releaseFence.await(5,java.util.concurrent.TimeUnit.SECONDS))
                                        throw new IllegalStateException("held-ack timeout");
                                }
                                if (!java.util.Objects.equals(ownerEpoch.get(),epoch)
                                        || !Boolean.FALSE.equals(receiptsById.get(id))
                                        || !java.util.Objects.equals(intentsById.get(id),
                                            java.util.HexFormat.of().formatHex(intent))) return false;
                                receiptsById.put(id,true); // owner-serialized completion
                                if ("crash-post-commit".equals(id))
                                    throw new IllegalStateException("simulated post-commit interruption");
                                return true;
                            }
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("owner wait interrupted",ex);
                        }
                    }
                }
                final OwnerModelJournal ownerJournal = new OwnerModelJournal();
                final CelineFutureMemoryRecall.OwnerSnapshotLeaseAuthority leakyLease = () ->
                    new CelineFutureMemoryRecall.OwnerSnapshotReadLease() {
                        public String generation() { return ownerEpoch.get(); }
                        public void close() {}
                    };
                final java.util.function.Supplier<CelineFutureMemoryRecall.OwnerLineageVerifier> restart = () ->
                    new CelineFutureMemoryRecall.OwnerLineageVerifier(replayKey,ownerEpoch::get,
                        leakyLease,(epoch,approved) -> raceDeliveries.incrementAndGet(),ownerJournal);
                final java.util.concurrent.atomic.AtomicReference<CelineFutureMemoryRecall.OwnerPublicationOutcome>
                    revokedResult = new java.util.concurrent.atomic.AtomicReference<>();
                Thread revokedSender = new Thread(() -> revokedResult.set(
                    CelineFutureMemoryRecall.publishCertifiedOwnerContextOnce(
                    "revoke-before-ack",query,newProjection,newRevisionProof,restart.get(),8,NOW)));
                revokedSender.start();
                try {
                    eq(beforeFence.await(5,java.util.concurrent.TimeUnit.SECONDS),true,
                        "publisher reaches ack boundary before revoke-first owner transaction");
                    Thread revoker = new Thread(() -> { synchronized(ownerFence) {ownerEpoch.set(revisionOld);} });
                    revoker.start(); revoker.join(5000);
                    eq(revoker.isAlive(),false,"revoke-first owner transaction finishes before ack");
                } finally { proceedFence.countDown(); }
                revokedSender.join(5000);
                eq(revokedSender.isAlive(),false,"revoked dispatch terminates");
                eq(revokedResult.get(),uncertain,"revocation before ack cannot report completed");
                eq(receiptsById.get("revoke-before-ack"),false,
                    "atomic owner cannot record stale completion after prior revocation");
                eq(raceDeliveries.get(),1,"revoked publication was attempted once");
                synchronized(ownerFence) { ownerEpoch.set(revisionNew); }
                eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextOnce(
                    "revoke-before-ack",query,newProjection,newRevisionProof,restart.get(),8,NOW),uncertain,
                    "restart preserves STARTED after revoke-first transaction");
                eq(raceDeliveries.get(),1,"restart never redispatches uncertain STARTED");

                final java.util.concurrent.atomic.AtomicReference<CelineFutureMemoryRecall.OwnerPublicationOutcome>
                    ackResult = new java.util.concurrent.atomic.AtomicReference<>();
                Thread ackSender = new Thread(() -> ackResult.set(
                    CelineFutureMemoryRecall.publishCertifiedOwnerContextOnce(
                    "ack-before-revoke",query,newProjection,newRevisionProof,restart.get(),8,NOW)));
                Thread lateRevoker = new Thread(() -> { synchronized(ownerFence) {ownerEpoch.set(revisionOld);} });
                ackSender.start();
                try {
                    eq(heldFence.await(5,java.util.concurrent.TimeUnit.SECONDS),true,
                        "ack-first writer holds same owner transaction fence as revocation");
                    lateRevoker.start();
                    eq(ownerEpoch.get(),revisionNew,"ack-first source stays current until fence release");
                } finally { releaseFence.countDown(); }
                ackSender.join(5000); lateRevoker.join(5000);
                eq(ackSender.isAlive() || lateRevoker.isAlive(),false,"ack and revoker threads terminate");
                eq(receiptsById.get("ack-before-revoke"),true,
                    "valid ack commits before serialized revocation");
                eq(ownerEpoch.get(),revisionOld,"late revocation eventually takes effect");
                eq(ackResult.get()==finished || ackResult.get()==uncertain,true,
                    "ack-first result reflects real linearization or later revoked source");
                eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextOnce(
                    "ack-before-revoke",query,newProjection,newRevisionProof,restart.get(),8,NOW),denied,
                    "restart refuses historical completed receipt on revoked source");
                eq(raceDeliveries.get(),2,"late revocation does not replay already published context");

                synchronized(ownerFence) { ownerEpoch.set(revisionNew); }
                eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextOnce(
                    "crash-post-commit",query,newProjection,newRevisionProof,restart.get(),8,NOW),uncertain,
                    "post-commit exception returns indeterminate, not unsupported proof of completion");
                eq(receiptsById.get("crash-post-commit"),true,
                    "model receipt was written before simulated interruption");
                eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextOnce(
                    "crash-post-commit",query,newProjection,newRevisionProof,restart.get(),8,NOW),finished,
                    "restarted verifier reads committed receipt rather than republishing");
                eq(raceDeliveries.get(),3,"post-commit restart does not duplicate publication");
                eq(legacyAcks.get(),0,"owner dispatch never invokes legacy acknowledgement");
                synchronized(ownerFence) { ownerEpoch.set(revisionOld); }
                eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextOnce(
                    "crash-post-commit",query,newProjection,newRevisionProof,restart.get(),8,NOW),denied,
                    "revoked post-crash historical completion cannot be consumed");
                eq(raceDeliveries.get(),3,"revoked historical crash receipt does not republish");
                System.out.println("FUTURE_G1_TWO_THREAD_ACK_REVOCATION=JVM_OWNER_MODEL_ONLY");
                System.out.println("FUTURE_G1_POST_COMMIT_INTERRUPTION_RESTART=IN_MEMORY_MODEL_ONLY");
            }
            System.out.println("FUTURE_G1_ATOMIC_SOURCE_BOUND_ACK=JVM_OWNER_MODEL_ONLY");
            System.out.println("FUTURE_G1_ATOMIC_ACK_DURABLE_ANDROID_PROOF=MISSING");
            System.out.println("FUTURE_G1_ACK_REVOCATION_GATE=JVM_SIMULATION_ONLY");
            System.out.println("FUTURE_G1_DISPATCH_JOURNAL_RESTART_AND_RACE=TEST_FIXTURE_ONLY");
        }
        // Malformed/truncated receipts must never be treated as COMPLETED or
        // permit another publication. A same-digest unknown state is corrupt.
        {
            final java.nio.file.Path corruptRoot = java.nio.file.Files.createTempDirectory("future-g1-corrupt-");
            final DiskJournal corrupt = new DiskJournal(corruptRoot);
            final String id = "corrupt-fixture";
            final byte[] intent = new byte[32];
            java.util.Arrays.fill(intent,(byte)7);
            final String hex = java.util.HexFormat.of().formatHex(intent);
            final java.nio.file.Path record = corrupt.entry(id);
            java.nio.file.Files.writeString(record,"STARTED ");
            eq(corrupt.reserve(id,intent),CelineFutureMemoryRecall.OwnerPublicationJournal.Reservation.INTENT_CONFLICT,
                "truncated digest does not reserve again");
            java.nio.file.Files.writeString(record,"");
            eq(corrupt.reserve(id,intent),CelineFutureMemoryRecall.OwnerPublicationJournal.Reservation.INTENT_CONFLICT,
                "zero-byte reservation fails closed");
            java.nio.file.Files.writeString(record,"GARBAGE " + hex);
            eq(corrupt.reserve(id,intent),CelineFutureMemoryRecall.OwnerPublicationJournal.Reservation.INTENT_CONFLICT,
                "unknown state with valid digest is corrupt");
            java.nio.file.Files.writeString(record,"COMPLETED " + hex + "\n");
            eq(corrupt.reserve(id,intent),CelineFutureMemoryRecall.OwnerPublicationJournal.Reservation.INTENT_CONFLICT,
                "extra trailing data cannot certify completed receipt");
            java.nio.file.Files.writeString(record,"COMPLETED " + hex);
            eq(corrupt.reserve(id,intent),CelineFutureMemoryRecall.OwnerPublicationJournal.Reservation.ALREADY_COMPLETED,
                "valid matching completed receipt remains accepted");
            java.nio.file.Files.writeString(record,"STARTED " + hex);
            eq(corrupt.reserve(id,intent),CelineFutureMemoryRecall.OwnerPublicationJournal.Reservation.ALREADY_STARTED,
                "completed-to-started rollback stays uncertain without republishing");
            java.nio.file.Files.delete(record);
            eq(corrupt.reserve(id,intent),CelineFutureMemoryRecall.OwnerPublicationJournal.Reservation.NEW_STARTED,
                "DEMONSTRATED UNFIXED deletion rollback permits new reservation without independent watermark");
            System.out.println("FUTURE_G1_JOURNAL_DELETION_ROLLBACK_RISK=REPRODUCED_UNFIXED");
        }
        // Interrupted persistence matrix: validate that no partial journal/witness
        // or anchor state can independently authorize a fresh duplicate context
        // publication. The only externally trusted state here is the SAME in-memory
        // JVM anchor/commitment object shared by all restarted fixture instances.
        {
            final byte[] bytes = new byte[32];
            java.util.Arrays.fill(bytes,(byte)0x35);
            final String request = "fault-matrix-request";
            final CelineFutureMemoryRecall.OwnerPublicationJournal.Reservation NEW =
                CelineFutureMemoryRecall.OwnerPublicationJournal.Reservation.NEW_STARTED;
            final CelineFutureMemoryRecall.OwnerPublicationJournal.Reservation CONFLICT =
                CelineFutureMemoryRecall.OwnerPublicationJournal.Reservation.INTENT_CONFLICT;
            final CelineFutureMemoryRecall.OwnerPublicationJournal.Reservation STARTED =
                CelineFutureMemoryRecall.OwnerPublicationJournal.Reservation.ALREADY_STARTED;
            final CelineFutureMemoryRecall.OwnerPublicationJournal.Reservation COMPLETED =
                CelineFutureMemoryRecall.OwnerPublicationJournal.Reservation.ALREADY_COMPLETED;
            for (WitnessedDiskJournal.FaultPoint point : WitnessedDiskJournal.FaultPoint.values()) {
                if (point == WitnessedDiskJournal.FaultPoint.NONE) continue;
                java.nio.file.Path j = java.nio.file.Files.createTempDirectory("g1-interrupt-j-");
                java.nio.file.Path w = java.nio.file.Files.createTempDirectory("g1-interrupt-w-");
                java.util.concurrent.atomic.AtomicLong anchor = new java.util.concurrent.atomic.AtomicLong();
                java.util.concurrent.atomic.AtomicReference<String> commitment =
                    new java.util.concurrent.atomic.AtomicReference<>();
                WitnessedDiskJournal original = new WitnessedDiskJournal(j,w,anchor,commitment);
                boolean reservationCrash = point.toString().startsWith("RESERVE_");
                if (!reservationCrash) {
                    eq(original.reserve(request,bytes),NEW,"precondition: original reservation "+point);
                }
                original.faultPoint = point;
                if (reservationCrash) {
                    eq(original.reserve(request,bytes),CONFLICT,
                        "interrupted reservation never reports NEW_STARTED "+point);
                } else {
                    boolean threw = false;
                    try { original.acknowledgeCompleted(request,bytes); }
                    catch (IllegalStateException ex) { threw = true; }
                    eq(threw,true,"interrupted acknowledgement must not return normal success "+point);
                }
                WitnessedDiskJournal restarted = new WitnessedDiskJournal(j,w,anchor,commitment);
                CelineFutureMemoryRecall.OwnerPublicationJournal.Reservation receipt =
                    restarted.reserve(request,bytes);
                eq(receipt == NEW,false,"restart must not authorize duplicate publication "+point);
                eq(receipt == CONFLICT || receipt == STARTED || receipt == COMPLETED,true,
                    "restart outcome restricted to fail-closed states "+point);
                if (point == WitnessedDiskJournal.FaultPoint.ACK_ANCHOR) {
                    eq(receipt,COMPLETED,"post-anchor acknowledgement is fully committed "+point);
                } else if (reservationCrash && point == WitnessedDiskJournal.FaultPoint.RESERVE_ANCHOR) {
                    eq(receipt,STARTED,"reserved authority with absent journal stays uncertain "+point);
                } else if (point == WitnessedDiskJournal.FaultPoint.ACK_JOURNAL) {
                    eq(receipt,STARTED,"completed journal alone cannot grant completed witness "+point);
                } else {
                    eq(receipt,CONFLICT,"torn state fails closed without re-authorization "+point);
                }
                // Two separately created readers of the post-fault state race.
                // They must not be able to create a fresh reservation together.
                WitnessedDiskJournal a = new WitnessedDiskJournal(j,w,anchor,commitment);
                WitnessedDiskJournal b = new WitnessedDiskJournal(j,w,anchor,commitment);
                java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(2);
                java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
                java.util.concurrent.atomic.AtomicReference<CelineFutureMemoryRecall.OwnerPublicationJournal.Reservation>
                    ar = new java.util.concurrent.atomic.AtomicReference<>(),
                    br = new java.util.concurrent.atomic.AtomicReference<>();
                Thread ta = new Thread(() -> { ready.countDown(); try { release.await(); ar.set(a.reserve(request,bytes)); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
                Thread tb = new Thread(() -> { ready.countDown(); try { release.await(); br.set(b.reserve(request,bytes)); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
                ta.start(); tb.start();
                eq(ready.await(5,java.util.concurrent.TimeUnit.SECONDS),true,"interrupted race both ready "+point);
                release.countDown();
                ta.join(5000); tb.join(5000);
                eq(ta.isAlive() || tb.isAlive(),false,"interrupted race terminates "+point);
                eq(ar.get() == NEW || br.get() == NEW,false,"interrupted race cannot republish "+point);
                eq(ar.get() == receipt && br.get() == receipt,true,
                    "interrupted race agrees with serialized protected authority "+point);
            }
            System.out.println("FUTURE_G1_INTERRUPTED_JOURNAL_WITNESS_MATRIX=JVM_SIMULATION_ONLY");
        }
        // Independent high-watermark witness prototype: two separate directories
        // and an external in-memory monotonic anchor. Neither is an actual
        // authenticated G1 store; exercise rollback and lost-file semantics.
        {
            final java.nio.file.Path journalDir = java.nio.file.Files.createTempDirectory("future-g1-journal-");
            final java.nio.file.Path witnessDir = java.nio.file.Files.createTempDirectory("future-g1-witness-");
            final java.util.concurrent.atomic.AtomicLong anchor = new java.util.concurrent.atomic.AtomicLong();
            final java.util.concurrent.atomic.AtomicReference<String> commitment =
                new java.util.concurrent.atomic.AtomicReference<>();
            final byte[] intent = new byte[32];
            java.util.Arrays.fill(intent,(byte)0x41);
            final String request = "protected-request-1";
            final WitnessedDiskJournal initial = new WitnessedDiskJournal(journalDir,witnessDir,anchor,commitment);
            final CelineFutureMemoryRecall.OwnerPublicationJournal.Reservation NEW =
                CelineFutureMemoryRecall.OwnerPublicationJournal.Reservation.NEW_STARTED;
            final CelineFutureMemoryRecall.OwnerPublicationJournal.Reservation STARTED =
                CelineFutureMemoryRecall.OwnerPublicationJournal.Reservation.ALREADY_STARTED;
            final CelineFutureMemoryRecall.OwnerPublicationJournal.Reservation COMPLETED =
                CelineFutureMemoryRecall.OwnerPublicationJournal.Reservation.ALREADY_COMPLETED;
            final CelineFutureMemoryRecall.OwnerPublicationJournal.Reservation CONFLICT =
                CelineFutureMemoryRecall.OwnerPublicationJournal.Reservation.INTENT_CONFLICT;
            eq(initial.reserve(request,intent),NEW,"witnessed owner may start genuinely new request");
            eq(anchor.get(),1L,"independent anchor advances for first reservation");
            String beforeAck = java.nio.file.Files.readString(initial.witness);
            // Attacker with access to both ordinary files can forge a same-epoch
            // COMPLETED receipt unless independent owner anchor binds state.
            java.nio.file.Files.writeString(initial.witness,beforeAck.replace(" STARTED "," COMPLETED "));
            java.nio.file.Files.writeString(initial.disk.entry(request),"COMPLETED " +
                java.util.HexFormat.of().formatHex(intent));
            eq(initial.reserve(request,intent),CONFLICT,
                "same-epoch witness status forgery must not fake a completion");
            java.nio.file.Files.writeString(initial.witness,beforeAck);
            java.nio.file.Files.writeString(initial.disk.entry(request),"STARTED " +
                java.util.HexFormat.of().formatHex(intent));
            eq(initial.reserve(request,intent),STARTED,"witnessed duplicate STARTED cannot republish");
            eq(initial.acknowledgeCompleted(request,intent),true,"witnessed owner acknowledges once");
            eq(anchor.get(),2L,"independent anchor advances on completed acknowledgement");
            String completedWitness = java.nio.file.Files.readString(initial.witness);
            WitnessedDiskJournal restarted = new WitnessedDiskJournal(journalDir,witnessDir,anchor,commitment);
            eq(restarted.reserve(request,intent),COMPLETED,"new fixture instance reads matching completed receipt");
            java.nio.file.Files.delete(initial.disk.entry(request));
            eq(restarted.reserve(request,intent),STARTED,"missing journal with protected witness never starts again");
            eq(restarted.acknowledgeCompleted(request,intent),false,"deleted journal cannot be acknowledged again");
            java.nio.file.Files.writeString(initial.disk.entry(request),"STARTED " +
                java.util.HexFormat.of().formatHex(intent));
            eq(restarted.reserve(request,intent),STARTED,"rolled-back STARTED receipt does not count completed");
            java.nio.file.Files.writeString(initial.witness,beforeAck);
            eq(restarted.reserve(request,intent),CONFLICT,"witness rollback detected by independent monotonic anchor");
            java.nio.file.Files.writeString(initial.witness,completedWitness);
            java.nio.file.Files.writeString(initial.disk.entry(request),"COMPLETED " +
                java.util.HexFormat.of().formatHex(intent));
            eq(restarted.reserve(request,intent),COMPLETED,"valid restored journal and current witness accepted");
            byte[] other = java.util.Arrays.copyOf(intent,intent.length); other[0] ^= 0x55;
            eq(restarted.reserve(request,other),CONFLICT,"different intent never inherits protected completed receipt");
            eq(restarted.reserve("different-request",intent),CONFLICT,"single-slot witness rejects different request ID");
            java.nio.file.Files.writeString(initial.witness,"corrupt");
            eq(restarted.reserve(request,intent),CONFLICT,"malformed witness fails closed");
            java.nio.file.Files.delete(initial.witness);
            eq(restarted.reserve(request,intent),CONFLICT,"deleted witness with live independent anchor fails closed");
            // Resetting the supposed independent authority as well defeats the
            // entire fixture. This is deliberately NOT a production security PASS.
            java.nio.file.Files.delete(initial.disk.entry(request));
            WitnessedDiskJournal unprotected = new WitnessedDiskJournal(journalDir,witnessDir,
                new java.util.concurrent.atomic.AtomicLong(0),
                new java.util.concurrent.atomic.AtomicReference<>());
            eq(unprotected.reserve(request,intent),NEW,
                "UNFIXED joint rollback of journal, witness and untrusted anchor permits replay");
            // Two independent wrappers sharing the same protected owner anchor
            // must serialize a simultaneous request. JVM threads only.
            final java.nio.file.Path raceJournal = java.nio.file.Files.createTempDirectory("future-g1-witness-race-j-");
            final java.nio.file.Path raceWitness = java.nio.file.Files.createTempDirectory("future-g1-witness-race-w-");
            final java.util.concurrent.atomic.AtomicLong raceAnchor = new java.util.concurrent.atomic.AtomicLong();
            final java.util.concurrent.atomic.AtomicReference<String> raceCommitment =
                new java.util.concurrent.atomic.AtomicReference<>();
            final WitnessedDiskJournal a = new WitnessedDiskJournal(raceJournal,raceWitness,
                raceAnchor,raceCommitment);
            final WitnessedDiskJournal b = new WitnessedDiskJournal(raceJournal,raceWitness,
                raceAnchor,raceCommitment);
            final java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(2);
            final java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
            final java.util.concurrent.atomic.AtomicReference<CelineFutureMemoryRecall.OwnerPublicationJournal.Reservation>
                first = new java.util.concurrent.atomic.AtomicReference<>(),
                second = new java.util.concurrent.atomic.AtomicReference<>();
            Thread t1 = new Thread(() -> {ready.countDown(); try {go.await(); first.set(a.reserve(request,intent));}
                catch (InterruptedException ex) {Thread.currentThread().interrupt();}});
            Thread t2 = new Thread(() -> {ready.countDown(); try {go.await(); second.set(b.reserve(request,intent));}
                catch (InterruptedException ex) {Thread.currentThread().interrupt();}});
            t1.start(); t2.start();
            eq(ready.await(5,java.util.concurrent.TimeUnit.SECONDS),true,"both witnessed claimants reached start");
            go.countDown(); t1.join(5000); t2.join(5000);
            eq(t1.isAlive() || t2.isAlive(),false,"both witnessed concurrent claims terminate");
            eq((first.get()==NEW && second.get()==STARTED) || (second.get()==NEW && first.get()==STARTED),
                true,"independent witnessed instances issue exactly one new claim");
            eq(raceAnchor.get(),1L,"concurrent duplicate advances protected high-water mark only once");
            System.out.println("FUTURE_G1_WITNESS_PROTECTED_ROLLBACK=JVM_SIMULATION_ONLY");
            System.out.println("FUTURE_G1_WITNESS_ANCHOR_ROLLBACK_RISK=REPRODUCED_UNFIXED");
        }
        // Two independent fixture instances on one journal directory must not
        // BOTH reserve an absent same-ID record. Force both to observe absence
        // before allowing their atomic claim. Instance-level synchronized is
        // insufficient; this is a real two-thread duplicate-publication test.
        {
            final java.nio.file.Path raceRoot = java.nio.file.Files.createTempDirectory("future-g1-cross-instance-");
            final java.util.concurrent.CountDownLatch bothMissing = new java.util.concurrent.CountDownLatch(2);
            final java.util.concurrent.CountDownLatch releaseClaims = new java.util.concurrent.CountDownLatch(1);
            final java.util.concurrent.atomic.AtomicInteger publisherCalls = new java.util.concurrent.atomic.AtomicInteger();
            final java.util.concurrent.atomic.AtomicReference<CelineFutureMemoryRecall.OwnerPublicationOutcome>
                first = new java.util.concurrent.atomic.AtomicReference<>();
            final java.util.concurrent.atomic.AtomicReference<CelineFutureMemoryRecall.OwnerPublicationOutcome>
                second = new java.util.concurrent.atomic.AtomicReference<>();
            final CelineFutureMemoryRecall.OwnerContextPublisher publisher = (publishedGen,texts) ->
                publisherCalls.incrementAndGet();
            final CelineFutureMemoryRecall.OwnerSnapshotLeaseAuthority lease = () ->
                new CelineFutureMemoryRecall.OwnerSnapshotReadLease() {
                    public String generation() { return revisionNew; }
                    public void close() {}
                };
            final CelineFutureMemoryRecall.OwnerLineageVerifier a =
                new CelineFutureMemoryRecall.OwnerLineageVerifier(replayKey,() -> revisionNew,
                    lease,publisher,new DiskJournal(raceRoot));
            final CelineFutureMemoryRecall.OwnerLineageVerifier b =
                new CelineFutureMemoryRecall.OwnerLineageVerifier(replayKey,() -> revisionNew,
                    lease,publisher,new DiskJournal(raceRoot));
            DiskJournal.bothMissing = bothMissing;
            DiskJournal.releaseMissing = releaseClaims;
            Thread t1 = new Thread(() -> first.set(CelineFutureMemoryRecall.publishCertifiedOwnerContextOnce(
                "cross-instance-1","Meine Katze heisst Momo",newProjection,newRevisionProof,a,8,NOW)));
            Thread t2 = new Thread(() -> second.set(CelineFutureMemoryRecall.publishCertifiedOwnerContextOnce(
                "cross-instance-1","Meine Katze heisst Momo",newProjection,newRevisionProof,b,8,NOW)));
            try {
                t1.start(); t2.start();
                eq(bothMissing.await(5,java.util.concurrent.TimeUnit.SECONDS),true,
                    "two separate journals observed same absent request ID");
                releaseClaims.countDown();
                t1.join(5000); t2.join(5000);
                eq(t1.isAlive() || t2.isAlive(),false,"both concurrent dispatches terminate");
                eq(publisherCalls.get(),1,"cross-instance duplicate cannot invoke publisher twice");
                eq((first.get() == CelineFutureMemoryRecall.OwnerPublicationOutcome.COMPLETED
                    && second.get() == CelineFutureMemoryRecall.OwnerPublicationOutcome.STARTED_INDETERMINATE)
                    || (second.get() == CelineFutureMemoryRecall.OwnerPublicationOutcome.COMPLETED
                    && first.get() == CelineFutureMemoryRecall.OwnerPublicationOutcome.STARTED_INDETERMINATE),
                    true,"exactly one owner completed and the competing request is indeterminate");
            } finally {
                releaseClaims.countDown();
                t1.join(5000); t2.join(5000);
                DiskJournal.bothMissing = null;
                DiskJournal.releaseMissing = null;
            }
            System.out.println("FUTURE_G1_CROSS_INSTANCE_ATOMIC_CLAIM=JVM_FIXTURE_ONLY");
        }
        System.out.println("FUTURE_G1_OWNER_CONTEXT_PUBLISHER=SIMULATED_ONLY");
        System.out.println("FUTURE_G1_PROVIDER_CONTEXT_SNAPSHOT_LEASE=PROTOTYPE_ONLY");
        System.out.println("FUTURE_G1_DURABLE_ANTI_ROLLBACK_GATE=UNIMPLEMENTED");
        System.out.println("FUTURE_MEMORY_RECALL_CONTRACT_PASS cases="+passed);
    }
}
'''
with tempfile.TemporaryDirectory(prefix='celine_future_recall_') as temp:
    p = Path(temp) / 'FutureMemoryRecallContract.java'
    p.write_text(harness, encoding='utf-8')
    subprocess.run(['javac','-encoding','UTF-8','-d',temp,str(source),str(p)], check=True)
    subprocess.run(['java','-cp',temp,'de.yahya.ai.FutureMemoryRecallContract'], check=True)
