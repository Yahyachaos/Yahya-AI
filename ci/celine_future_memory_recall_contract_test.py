#!/usr/bin/env python3
"""FUTURE ONLY pure Java source contract, no v83 application/CI dependencies."""
from pathlib import Path
import subprocess
import tempfile
import hashlib

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


    /** FUTURE ONLY: disk-backed *model* of one G1 owner transaction. Revision,
     * tombstone, request intent and receipt live in ONE atomically replaced frame.
     * JVM AtomicLong/hash stand in for separately protected authority. They are
     * NOT persistent across process/device restore or a real G1 implementation.
     * The permanent .lock inode plus JVM path mutex serialize test claimants. */
    static final class FileOwnerLedger implements CelineFutureMemoryRecall.OwnerPublicationJournal {
        static final java.util.concurrent.ConcurrentHashMap<String,Object> MUTEX =
            new java.util.concurrent.ConcurrentHashMap<>();
        final java.nio.file.Path directory, frame, lockFile;
        final java.util.concurrent.atomic.AtomicLong protectedEpoch;
        final java.util.concurrent.atomic.AtomicReference<String> protectedDigest;
        enum Fault { NONE, AFTER_FRAME_BEFORE_AUTHORITY, AFTER_AUTHORITY_BEFORE_RETURN }
        Fault reserveFault=Fault.NONE, completionFault=Fault.NONE, revokeFault=Fault.NONE;
        interface Operation<T> { T run() throws Exception; }
        static final class State {
            final long epoch;
            final String revision, request, intent, receipt;
            final boolean retracted;
            State(long epoch, String revision, boolean retracted, String request,
                    String intent, String receipt) {
                this.epoch=epoch; this.revision=revision; this.retracted=retracted;
                this.request=request; this.intent=intent; this.receipt=receipt;
            }
            String encode() { return epoch+"|"+revision+"|"+(retracted?"1":"0")+"|"+
                request+"|"+intent+"|"+receipt; }
            static State decode(String raw) {
                String[] v=raw.split("\\|",-1);
                if(v.length!=6 || !v[0].matches("[1-9][0-9]*")
                        || !v[1].matches("[a-zA-Z0-9_-]{1,100}")
                        || !(v[2].equals("0") || v[2].equals("1"))
                        || !v[3].matches("-|[0-9a-f]{64}")
                        || !v[4].matches("-|[0-9a-f]{64}")
                        || !(v[5].equals("NONE") || v[5].equals("STARTED")
                            || v[5].equals("COMPLETED"))
                        || (v[3].equals("-") != v[4].equals("-"))
                        || (v[5].equals("NONE") != v[3].equals("-")))
                    throw new IllegalStateException("invalid owner frame");
                return new State(Long.parseLong(v[0]),v[1],v[2].equals("1"),v[3],v[4],v[5]);
            }
        }
        FileOwnerLedger(java.nio.file.Path root,
                java.util.concurrent.atomic.AtomicLong trustedEpoch,
                java.util.concurrent.atomic.AtomicReference<String> trustedDigest,
                String initialRevision) throws Exception {
            directory=root.toAbsolutePath().normalize();
            java.nio.file.Files.createDirectories(directory);
            frame=directory.resolve("owner.frame");
            lockFile=directory.resolve("owner.lock");
            protectedEpoch=trustedEpoch; protectedDigest=trustedDigest;
            withOwnerLock(() -> {
                if (!java.nio.file.Files.exists(frame)
                        && protectedEpoch.get()==0 && protectedDigest.get()==null) {
                    State init=new State(1,initialRevision,false,"-","-","NONE");
                    replaceFrame(init); seal(init);
                }
                return null;
            });
        }
        private <T> T withOwnerLock(Operation<T> operation) throws Exception {
            Object monitor=MUTEX.computeIfAbsent(lockFile.toString(),unused -> new Object());
            synchronized(monitor) {
                try (java.nio.channels.FileChannel ch=java.nio.channels.FileChannel.open(lockFile,
                        java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.WRITE);
                     java.nio.channels.FileLock lock=ch.lock()) {
                    return operation.run();
                }
            }
        }
        private static String digest(String text) throws Exception {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        private static String hex(byte[] bytes) { return java.util.HexFormat.of().formatHex(bytes); }
        private State verified() throws Exception {
            if (!java.nio.file.Files.isRegularFile(frame)) throw new IllegalStateException("lost owner frame");
            String raw=java.nio.file.Files.readString(frame);
            State state=State.decode(raw);
            if (state.epoch!=protectedEpoch.get()
                || !java.util.Objects.equals(digest(raw),protectedDigest.get()))
                throw new IllegalStateException("untrusted owner frame/authority mismatch");
            return state;
        }
        private void replaceFrame(State next) throws Exception {
            java.nio.file.Path pending=directory.resolve("owner.pending-"+java.util.UUID.randomUUID());
            try (java.nio.channels.FileChannel ch=java.nio.channels.FileChannel.open(pending,
                    java.nio.file.StandardOpenOption.CREATE_NEW,java.nio.file.StandardOpenOption.WRITE)) {
                java.nio.ByteBuffer b=java.nio.charset.StandardCharsets.UTF_8.encode(next.encode());
                while(b.hasRemaining()) ch.write(b);
                ch.force(true);
            }
            java.nio.file.Files.move(pending,frame,java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            // Best-effort directory metadata durability on this JVM/filesystem;
            // this is not a power-cut proof or Android storage contract.
            try (java.nio.channels.FileChannel dir=java.nio.channels.FileChannel.open(directory,
                    java.nio.file.StandardOpenOption.READ)) { dir.force(true); }
        }
        private void seal(State next) throws Exception {
            protectedEpoch.set(next.epoch);
            protectedDigest.set(digest(next.encode()));
        }
        private void commit(State next,Fault fault) throws Exception {
            replaceFrame(next);
            if(fault==Fault.AFTER_FRAME_BEFORE_AUTHORITY)
                throw new IllegalStateException("simulated crash before owner authority commit");
            seal(next);
            if(fault==Fault.AFTER_AUTHORITY_BEFORE_RETURN)
                throw new IllegalStateException("simulated crash after owner authority commit");
        }
        String revision() {
            try { return withOwnerLock(() -> {
                State s=verified(); return s.retracted ? "" : s.revision;
            }); } catch (Exception failure) { return ""; } // fail-closed untrusted revision
        }
        void revoke(String laterGeneration) throws Exception {
            withOwnerLock(() -> {
                State s=verified();
                if(s.retracted) throw new IllegalStateException("already retracted");
                commit(new State(Math.addExact(s.epoch,1),laterGeneration,true,
                    s.request,s.intent,s.receipt),revokeFault);
                return null;
            });
        }
        @Override public Reservation reserve(String id,byte[] intent) {
            try { return withOwnerLock(() -> {
                State s=verified();
                if(s.retracted) return Reservation.INTENT_CONFLICT;
                String key=digest(id), payload=hex(intent);
                if(!s.receipt.equals("NONE")) {
                    if(!s.request.equals(key) || !s.intent.equals(payload))
                        return Reservation.INTENT_CONFLICT;
                    return s.receipt.equals("COMPLETED")
                        ? Reservation.ALREADY_COMPLETED : Reservation.ALREADY_STARTED;
                }
                commit(new State(Math.addExact(s.epoch,1),s.revision,false,key,payload,
                    "STARTED"),reserveFault);
                return Reservation.NEW_STARTED;
            }); } catch (Exception failure) { return Reservation.INTENT_CONFLICT; }
        }
        @Override public boolean acknowledgeCompleted(String id,byte[] intent) {
            throw new IllegalStateException("legacy ack lacks atomic revision fencing");
        }
        @Override public boolean acknowledgeCompletedIfCurrent(String id,byte[] intent,String generation) {
            try { return withOwnerLock(() -> {
                State s=verified();
                if(s.retracted || !s.revision.equals(generation)
                    || !s.receipt.equals("STARTED") || !s.request.equals(digest(id))
                    || !s.intent.equals(hex(intent))) return false;
                commit(new State(Math.addExact(s.epoch,1),s.revision,false,
                    s.request,s.intent,"COMPLETED"),completionFault);
                return true;
            }); } catch (Exception failure) { throw new IllegalStateException("atomic owner ack uncertain",failure); }
        }
        String receipt() {
            try { return withOwnerLock(() -> verified().receipt); }
            catch (Exception failure) { return "UNTRUSTED"; }
        }
        // TEST MODEL ONLY: revision + receipt must be inspected in ONE owner
        // critical section. A previously completed but now retracted record is
        // never a *current* successful publication.
        boolean currentCompleted(String expectedGeneration) {
            try { return withOwnerLock(() -> {
                State s=verified();
                return !s.retracted && s.revision.equals(expectedGeneration)
                    && s.receipt.equals("COMPLETED");
            }); } catch (Exception failure) { return false; }
        }
    }

    /** FUTURE-ONLY JVM TEST DOUBLE; NOT durable, Android or rollback-resistant. */
    static final class StrictAtomicFixture
            implements CelineFutureMemoryRecall.OwnerAtomicDispatchAuthority {
        final Object transactionLock = new Object();
        volatile String revision = Fixture.TEST_ONLY_GENERATION;
        final Map<String,String> receipts = new HashMap<>();
        final Map<String,String> digests = new HashMap<>();
        int completedSealCount, revokeCount, reserveCount;
        volatile boolean failAfterPublication;
        @Override public String currentGeneration() { return revision; }
        @Override public CelineFutureMemoryRecall.OwnerSnapshotReadLease acquireReadLease() {
            return new CelineFutureMemoryRecall.OwnerSnapshotReadLease() {
                @Override public String generation() { return revision; }
                @Override public void close() {} // intentionally weak in-memory fixture
            };
        }
        void revoke() {
            synchronized(transactionLock) {
                revision = "g1-revision-revoked";
                revokeCount++;
            }
        }
        @Override public CelineFutureMemoryRecall.OwnerPublicationOutcome
                dispatchUnderOwnerLock(String generation, String id, byte[] digest,
                        Runnable publisher) {
            synchronized(transactionLock) {
                if (!revision.equals(generation))
                    return CelineFutureMemoryRecall.OwnerPublicationOutcome.REJECTED_BEFORE_START;
                String payload = java.util.HexFormat.of().formatHex(digest);
                String receipt = receipts.get(id);
                if (receipt != null) {
                    if (!payload.equals(digests.get(id)))
                        return CelineFutureMemoryRecall.OwnerPublicationOutcome.STARTED_INDETERMINATE;
                    return "COMPLETED".equals(receipt)
                            ? CelineFutureMemoryRecall.OwnerPublicationOutcome.COMPLETED
                            : CelineFutureMemoryRecall.OwnerPublicationOutcome.STARTED_INDETERMINATE;
                }
                // Reservation is model-atomic against revocation and claimants.
                receipts.put(id,"STARTED");
                digests.put(id,payload);
                reserveCount++;
                try {
                    publisher.run(); // synchronous: never escape trusted critical section
                    if (failAfterPublication)
                        throw new IllegalStateException("simulated receipt seal fault");
                    // Mandatory conditional check AND receipt seal under one lock.
                    if (!revision.equals(generation))
                        return CelineFutureMemoryRecall.OwnerPublicationOutcome.STARTED_INDETERMINATE;
                    receipts.put(id,"COMPLETED");
                    completedSealCount++;
                    return CelineFutureMemoryRecall.OwnerPublicationOutcome.COMPLETED;
                } catch (RuntimeException e) {
                    return CelineFutureMemoryRecall.OwnerPublicationOutcome.STARTED_INDETERMINATE;
                }
            }
        }
        String receipt(String id) { synchronized(transactionLock) {return receipts.get(id);} }
    }

    /** Simulated independent checkpoint; NOT actually durable/antirollback. */
    static final class HighWaterFixture
            implements CelineFutureMemoryRecall.OwnerHighWaterDispatchAuthority {
        final StrictAtomicFixture local = new StrictAtomicFixture();
        volatile String protectedGeneration = Fixture.TEST_ONLY_GENERATION;
        volatile boolean missing, compromised, throwOnRead;
        @Override public CelineFutureMemoryRecall.OwnerHighWaterStatus
                protectedGenerationStatus(String expected) {
            if (throwOnRead) throw new IllegalStateException("test-only witness I/O fault");
            if (missing) return CelineFutureMemoryRecall.OwnerHighWaterStatus.MISSING;
            if (compromised) return CelineFutureMemoryRecall.OwnerHighWaterStatus.COMPROMISED;
            return expected != null && expected.equals(protectedGeneration)
                    && expected.equals(local.revision)
                    ? CelineFutureMemoryRecall.OwnerHighWaterStatus.CURRENT
                    : CelineFutureMemoryRecall.OwnerHighWaterStatus.ROLLED_BACK;
        }
        @Override public String currentGeneration() { return local.currentGeneration(); }
        @Override public CelineFutureMemoryRecall.OwnerSnapshotReadLease acquireReadLease() {
            return local.acquireReadLease();
        }
        @Override public CelineFutureMemoryRecall.OwnerPublicationOutcome dispatchUnderOwnerLock(
                String gen, String id, byte[] digest, Runnable publisher) {
            synchronized (local.transactionLock) {
                if (protectedGenerationStatus(gen)
                        != CelineFutureMemoryRecall.OwnerHighWaterStatus.CURRENT)
                    return CelineFutureMemoryRecall.OwnerPublicationOutcome.REJECTED_BEFORE_START;
                return local.dispatchUnderOwnerLock(gen,id,digest,publisher);
            }
        }
        void revokeAndKeepHighWater() {
            local.revoke();
            protectedGeneration = local.revision;
        }
    }
    /**
     * FUTURE ONLY: deterministic competing JVM-thread fixture.  The lease
     * acquisition pauses AFTER the public high-water preflight returned CURRENT
     * but BEFORE the owner transaction can reserve or reuse a completed receipt.
     * This is deliberately an in-memory model, not Android antirollback proof.
     */
    static final class PausedHighWaterOwner
            implements CelineFutureMemoryRecall.OwnerHighWaterDispatchAuthority {
        final HighWaterFixture delegate = new HighWaterFixture();
        final java.util.concurrent.CountDownLatch leaseEntered;
        final java.util.concurrent.CountDownLatch releaseLease =
                new java.util.concurrent.CountDownLatch(1);
        volatile boolean gateLease;
        PausedHighWaterOwner(int simultaneousCallers) {
            leaseEntered = new java.util.concurrent.CountDownLatch(simultaneousCallers);
        }
        @Override public CelineFutureMemoryRecall.OwnerHighWaterStatus
                protectedGenerationStatus(String generation) {
            return delegate.protectedGenerationStatus(generation);
        }
        @Override public String currentGeneration() { return delegate.currentGeneration(); }
        @Override public CelineFutureMemoryRecall.OwnerSnapshotReadLease acquireReadLease() {
            if (gateLease) {
                leaseEntered.countDown();
                try {
                    if (!releaseLease.await(10, java.util.concurrent.TimeUnit.SECONDS))
                        throw new IllegalStateException("race fixture timed out at lease barrier");
                } catch (InterruptedException interruption) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("race fixture interrupted",interruption);
                }
            }
            return delegate.acquireReadLease();
        }
        @Override public CelineFutureMemoryRecall.OwnerPublicationOutcome
                dispatchUnderOwnerLock(String generation,String requestId,byte[] digest,
                        Runnable publisher) {
            return delegate.dispatchUnderOwnerLock(generation,requestId,digest,publisher);
        }
    }
    private static void highWaterMidflightRaceTests() throws Exception {
        var reject=CelineFutureMemoryRecall.OwnerPublicationOutcome.REJECTED_BEFORE_START;
        var done=CelineFutureMemoryRecall.OwnerPublicationOutcome.COMPLETED;
        var projection=Arrays.asList(c("race-highwater-cat","Meine Katze Luna"));
        var certificate=Fixture.sourceFixture(projection,Collections.emptySet(),Collections.emptySet());
        // Two independent callers each perform a real preflight before an
        // external high-water authority flips.  Include ALREADY_COMPLETED,
        // whose prior receipt must never turn stale authority into success.
        for (int seeded=0;seeded<2;seeded++) {
            for (int change=0;change<4;change++) {
                PausedHighWaterOwner owner=new PausedHighWaterOwner(2);
                java.util.concurrent.atomic.AtomicInteger sent=new java.util.concurrent.atomic.AtomicInteger();
                var verifier=new CelineFutureMemoryRecall.OwnerLineageVerifier(
                        Fixture.TEST_ONLY_SECRET,owner,(gen,summaries)->sent.incrementAndGet());
                String id="race-highwater-"+seeded+"-"+change;
                if (seeded==1) {
                    eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextHighWater(
                            id,"Meine Katze Luna",projection,certificate,verifier,8,NOW),done,
                            "seed a completed receipt before protected high-water race");
                }
                owner.gateLease=true;
                java.util.concurrent.atomic.AtomicReference<
                        CelineFutureMemoryRecall.OwnerPublicationOutcome> first=
                        new java.util.concurrent.atomic.AtomicReference<>();
                java.util.concurrent.atomic.AtomicReference<
                        CelineFutureMemoryRecall.OwnerPublicationOutcome> second=
                        new java.util.concurrent.atomic.AtomicReference<>();
                Thread a=new Thread(()->first.set(
                        CelineFutureMemoryRecall.publishCertifiedOwnerContextHighWater(
                                id,"Meine Katze Luna",projection,certificate,verifier,8,NOW)),
                        "future-highwater-race-a");
                Thread b=new Thread(()->second.set(
                        CelineFutureMemoryRecall.publishCertifiedOwnerContextHighWater(
                                id,"Meine Katze Luna",projection,certificate,verifier,8,NOW)),
                        "future-highwater-race-b");
                a.start();b.start();
                boolean bothReached=owner.leaseEntered.await(10,java.util.concurrent.TimeUnit.SECONDS);
                try {
                    eq(bothReached,true,"both claimants reached post-preflight barrier");
                    // Independent witness changes while the ordinary G1 local
                    // revision and previously completed receipt stay unchanged.
                    switch(change) {
                        case 0 -> owner.delegate.protectedGeneration="new-protected-generation";
                        case 1 -> owner.delegate.missing=true;
                        case 2 -> owner.delegate.compromised=true;
                        case 3 -> owner.delegate.throwOnRead=true;
                        default -> throw new AssertionError("bad race mode");
                    }
                } finally { owner.releaseLease.countDown(); }
                a.join(10000);b.join(10000);
                eq(a.isAlive()||b.isAlive(),false,"two racing claimants finish in bounded time");
                // A transaction may conservatively return INDETERMINATE when
                // the owner denied dispatch before reserve; never COMPLETED.
                eq(first.get()!=null&&first.get()!=done,true,
                        "first stale protected witness cannot complete");
                eq(second.get()!=null&&second.get()!=done,true,
                        "second stale protected witness cannot complete");
                eq(sent.get(),seeded,"no stale external callback after high-water advance");
                eq(owner.delegate.local.receipt(id),seeded==1?"COMPLETED":null,
                        "stale request does not create a fresh reservation");
                eq(owner.delegate.local.completedSealCount,seeded,
                        "old completed receipt is not re-sealed on stale authority");
                eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextHighWater(
                        id,"Meine Katze Luna",projection,certificate,verifier,8,NOW),reject,
                        "later old-generation retry rejected by protected authority");
            }
        }
        System.out.println("FUTURE_G1_HIGH_WATER_TOCTOU_RACE_PASS=JVM_FIXTURE_ONLY");
    }
    private static void highWaterOwnerTests() throws Exception {
        var reject=CelineFutureMemoryRecall.OwnerPublicationOutcome.REJECTED_BEFORE_START;
        var uncertain=CelineFutureMemoryRecall.OwnerPublicationOutcome.STARTED_INDETERMINATE;
        var done=CelineFutureMemoryRecall.OwnerPublicationOutcome.COMPLETED;
        var projection=Arrays.asList(c("highwater-cat","Meine Katze Luna"));
        var certificate=Fixture.sourceFixture(projection,Collections.emptySet(),Collections.emptySet());
        var sent=new java.util.concurrent.atomic.AtomicInteger();
        HighWaterFixture owner=new HighWaterFixture();
        var verifier=new CelineFutureMemoryRecall.OwnerLineageVerifier(
                Fixture.TEST_ONLY_SECRET,owner,(gen,text)->sent.incrementAndGet());
        String query="Meine Katze Luna";
        eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextHighWater(
                "hw1",query,projection,certificate,verifier,8,NOW),done,
                "protected current epoch permits one strict receipt");
        eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextHighWater(
                "hw1",query,projection,certificate,verifier,8,NOW),done,
                "current repeated receipt does not republish");
        eq(sent.get(),1,"current protected receipt sends once");
        // A full rollback of BOTH local frame+versioned anchor is modeled by
        // restoring local revision/receipts; only the independent witness stays new.
        owner.revokeAndKeepHighWater();
        owner.local.revision=Fixture.TEST_ONLY_GENERATION;
        eq(owner.protectedGenerationStatus(certificate.generation),
                CelineFutureMemoryRecall.OwnerHighWaterStatus.ROLLED_BACK,
                "external high-water detects full rollback of local source");
        eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextHighWater(
                "hw1",query,projection,certificate,verifier,8,NOW),reject,
                "old-generation completed receipt rejected after full local restore");
        eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextHighWater(
                "hw2",query,projection,certificate,verifier,8,NOW),reject,
                "restored local files cannot start new stale request");
        eq(sent.get(),1,"rollback never redispatches published plaintext");
        owner.missing=true;
        eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextHighWater(
                "hw3",query,projection,certificate,verifier,8,NOW),reject,
                "missing high-water authority fails closed");
        owner.missing=false;owner.compromised=true;
        eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextHighWater(
                "hw4",query,projection,certificate,verifier,8,NOW),reject,
                "compromised high-water authority fails closed");
        owner.compromised=false;owner.throwOnRead=true;
        eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextHighWater(
                "hw5",query,projection,certificate,verifier,8,NOW),reject,
                "throwing high-water authority fails closed without reset");
        owner.throwOnRead=false;
        eq(owner.protectedGenerationStatus(certificate.generation),
                CelineFutureMemoryRecall.OwnerHighWaterStatus.ROLLED_BACK,
                "failed witness reads cannot silently repair historical rollback");
        StrictAtomicFixture oldOwner=new StrictAtomicFixture();
        var oldVerifier=new CelineFutureMemoryRecall.OwnerLineageVerifier(
                Fixture.TEST_ONLY_SECRET,oldOwner,(gen,text)->sent.incrementAndGet());
        eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextHighWater(
                "legacy",query,projection,certificate,oldVerifier,8,NOW),reject,
                "legacy strict owner cannot bypass mandatory protected witness");
        eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextHighWater(
                "null",query,projection,null,verifier,8,NOW),reject,
                "missing certificate cannot reach high-water dispatch");
        eq(sent.get(),1,"all failed attestations prevent further publications");
        // Missing/compromised source is independently denied even when the
        // local snapshot and ordinary strict journal are otherwise CURRENT.
        HighWaterFixture unavailable=new HighWaterFixture();
        var unavailableSent=new java.util.concurrent.atomic.AtomicInteger();
        var unavailableVerifier=new CelineFutureMemoryRecall.OwnerLineageVerifier(
                Fixture.TEST_ONLY_SECRET,unavailable,(gen,text)->unavailableSent.incrementAndGet());
        unavailable.missing=true;
        eq(unavailable.protectedGenerationStatus(certificate.generation),
                CelineFutureMemoryRecall.OwnerHighWaterStatus.MISSING,
                "missing protected witness distinct from ordinary snapshot rollback");
        eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextHighWater(
                "unavailable",query,projection,certificate,unavailableVerifier,8,NOW),reject,
                "current local snapshot cannot bypass missing witness");
        unavailable.missing=false;unavailable.compromised=true;
        eq(unavailable.protectedGenerationStatus(certificate.generation),
                CelineFutureMemoryRecall.OwnerHighWaterStatus.COMPROMISED,
                "compromised witness distinct from missing witness");
        eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextHighWater(
                "unavailable",query,projection,certificate,unavailableVerifier,8,NOW),reject,
                "current local snapshot cannot bypass corrupt witness");
        eq(unavailableSent.get(),0,"missing or compromised witness causes zero callbacks");
        eq(unavailable.local.receipt("unavailable"),null,
                "failure before strict transaction does not create false STARTED receipt");
        unavailable.compromised=false;
        eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextHighWater(
                "unavailable",query,projection,certificate,unavailableVerifier,8,NOW),done,
                "explicit restoration of trusted witness permits fresh normal transaction");
        eq(unavailableSent.get(),1,"restored trusted model only publishes once");
        // A post-publish authority loss is uncertain, never retryable success.
        HighWaterFixture changed=new HighWaterFixture();
        var changedSent=new java.util.concurrent.atomic.AtomicInteger();
        var changedVerifier=new CelineFutureMemoryRecall.OwnerLineageVerifier(
                Fixture.TEST_ONLY_SECRET,changed,(gen,text)->{
                    changedSent.incrementAndGet();
                    changed.protectedGeneration="generation-2";
                });
        eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextHighWater(
                "race",query,projection,certificate,changedVerifier,8,NOW),uncertain,
                "witness changed during delivery must not report COMPLETED");
        eq(changedSent.get(),1,"post-start witness change cannot undo delivery");
        eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextHighWater(
                "race",query,projection,certificate,changedVerifier,8,NOW),reject,
                "uncertain old request cannot be auto-retried");
        eq(changedSent.get(),1,"failed reopen cannot publish twice");
        System.out.println("FUTURE_G1_HIGH_WATER_MODEL_PASS=UNTRUSTED_JVM_WITNESS_ONLY");
        System.out.println("FUTURE_G1_REAL_NONROLLBACKABLE_AUTHORITY=UNIMPLEMENTED");
    }

    private static void strictAtomicOwnerTests() throws Exception {
        var done = CelineFutureMemoryRecall.OwnerPublicationOutcome.COMPLETED;
        var uncertain = CelineFutureMemoryRecall.OwnerPublicationOutcome.STARTED_INDETERMINATE;
        var reject = CelineFutureMemoryRecall.OwnerPublicationOutcome.REJECTED_BEFORE_START;
        List<CelineFutureMemoryRecall.Candidate> projection =
                Arrays.asList(c("atomic-cat", "Meine Katze Luna"));
        var certificate = Fixture.sourceFixture(projection,
                Collections.emptySet(),Collections.emptySet());
        String query="Meine Katze Luna";
        java.util.concurrent.atomic.AtomicInteger sent=new java.util.concurrent.atomic.AtomicInteger();
        StrictAtomicFixture owner=new StrictAtomicFixture();
        var verifier=new CelineFutureMemoryRecall.OwnerLineageVerifier(
                Fixture.TEST_ONLY_SECRET,owner,(gen,text)->sent.incrementAndGet());
        eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextAtomic("strict-id",query,
                projection,certificate,verifier,8,NOW),done,
                "strict atomic authority seals correct generation and receipt");
        eq(owner.receipt("strict-id"),"COMPLETED","strict receipt sealed in owner transaction");
        eq(owner.completedSealCount,1,"owner sealed checkpoint only once");
        eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextAtomic("strict-id",query,
                projection,certificate,verifier,8,NOW),done,
                "completed retry reads receipt without redispatch");
        eq(sent.get(),1,"exactly one synchronous downstream publication");
        owner.revoke();
        eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextAtomic("strict-id",query,
                projection,certificate,verifier,8,NOW),reject,
                "revoked generation cannot reuse old completed receipt");
        eq(sent.get(),1,"revoked receipt cannot repeat delivery");
        // The old disconnected owner-reader/journal constructor cannot enter
        // the mandatory strict API, even if the certificate MAC is otherwise valid.
        var legacy = new CelineFutureMemoryRecall.OwnerLineageVerifier(
                Fixture.TEST_ONLY_SECRET,owner::currentGeneration,owner,
                (gen,text)->sent.incrementAndGet(),null);
        eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextAtomic("legacy-id",query,
                projection,certificate,legacy,8,NOW),reject,
                "disconnected journal/reader composition cannot masquerade as atomic authority");
        // Re-entrant source revocation during callback: a check performed ONLY
        // before publication would falsely mark COMPLETED. Conditional seal
        // after synchronous callback is mandatory, still under the same lock.
        StrictAtomicFixture reentrant=new StrictAtomicFixture();
        var reentrantVerifier=new CelineFutureMemoryRecall.OwnerLineageVerifier(
                Fixture.TEST_ONLY_SECRET,reentrant,(gen,text)->reentrant.revoke());
        eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextAtomic("inside-revoke",query,
                projection,certificate,reentrantVerifier,8,NOW),uncertain,
                "revocation during callback blocks false completed receipt");
        eq(reentrant.receipt("inside-revoke"),"STARTED",
                "re-entrant revoke leaves non-retryable STARTED");
        eq(reentrant.completedSealCount,0,"no stale seal after source revocation");
        // Two REAL Java threads: the publisher holds the owner transaction,
        // while a concurrent revoker tries to acquire the SAME owner lock.
        StrictAtomicFixture racing=new StrictAtomicFixture();
        var entered=new java.util.concurrent.CountDownLatch(1);
        var allowPublisher=new java.util.concurrent.CountDownLatch(1);
        var revokeAttempt=new java.util.concurrent.CountDownLatch(1);
        var raceSent=new java.util.concurrent.atomic.AtomicInteger();
        var raceResult=new java.util.concurrent.atomic.AtomicReference<CelineFutureMemoryRecall.OwnerPublicationOutcome>();
        var raceVerifier=new CelineFutureMemoryRecall.OwnerLineageVerifier(
                Fixture.TEST_ONLY_SECRET,racing,(gen,text)->{
                    entered.countDown();
                    try { if(!allowPublisher.await(5,java.util.concurrent.TimeUnit.SECONDS))
                        throw new IllegalStateException("publisher timed out"); }
                    catch(InterruptedException e) {Thread.currentThread().interrupt();throw new IllegalStateException(e);}
                    raceSent.incrementAndGet();
                });
        Thread publisher=new Thread(()->raceResult.set(
                CelineFutureMemoryRecall.publishCertifiedOwnerContextAtomic("race-complete",query,
                        projection,certificate,raceVerifier,8,NOW)),"future-atomic-publisher");
        Thread revoker=new Thread(()->{revokeAttempt.countDown();racing.revoke();},
                "future-atomic-revoker");
        publisher.start();
        eq(entered.await(5,java.util.concurrent.TimeUnit.SECONDS),true,
                "publisher entered owner serialization region");
        revoker.start();
        eq(revokeAttempt.await(5,java.util.concurrent.TimeUnit.SECONDS),true,
                "second Java thread attempted source revocation");
        eq(racing.revokeCount,0,"concurrent revoker blocked until receipt commit");
        allowPublisher.countDown();
        publisher.join(5000);revoker.join(5000);
        eq(!publisher.isAlive() && !revoker.isAlive(),true,
                "two real Java threads complete without deadlock");
        eq(racing.receipt("race-complete"),"COMPLETED",
                "receipt linearizes before a competing revocation");
        eq(racing.completedSealCount,1,"only one current-generation completion seal");
        eq(racing.revokeCount,1,"revocation serialized after the completion seal");
        eq(raceSent.get(),1,"race publishes exactly once, never duplicated");
        eq(raceResult.get()==done || raceResult.get()==uncertain,true,
                "post-commit revoke may conservatively report uncertain, never forged success");
        eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextAtomic("race-complete",query,
                projection,certificate,raceVerifier,8,NOW),reject,
                "after race revoked source cannot authorize completed replay");
        // Publication has started and thrown: future calls with same request ID
        // may NOT retry because plaintext might already be in a remote sink.
        StrictAtomicFixture failing=new StrictAtomicFixture();
        failing.failAfterPublication=true;
        var failsSent=new java.util.concurrent.atomic.AtomicInteger();
        var failsVerifier=new CelineFutureMemoryRecall.OwnerLineageVerifier(
                Fixture.TEST_ONLY_SECRET,failing,(gen,text)->failsSent.incrementAndGet());
        eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextAtomic("fail-seal",query,
                projection,certificate,failsVerifier,8,NOW),uncertain,
                "injected post-publication seal failure is nonretryable");
        eq(CelineFutureMemoryRecall.publishCertifiedOwnerContextAtomic("fail-seal",query,
                projection,certificate,failsVerifier,8,NOW),uncertain,
                "STARTED after incomplete seal remains indeterminate");
        eq(failsSent.get(),1,"exception recovery never republishes old text");
        System.out.println("FUTURE_G1_ATOMIC_OWNER_JVM_FIXTURE=MODEL_ONLY");
        System.out.println("FUTURE_G1_NONROLLBACKABLE_AUTHORITY=UNIMPLEMENTED");
    }

    public static void main(String[] args) throws Exception {
        // A trusted owner must pin the verifier OUTSIDE the snapshot; this
        // deterministic fixture proves signatures only, not G1/Android custody.
        java.security.KeyPair owner=java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        java.security.KeyPair intruder=java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String orphanHash="a".repeat(64), recoveryNonce="owner-manual-approval-0001";
        byte[] recoveryApprovedPayload=CelineFutureMemoryRecall.revokedRecoveryPayload(
                "cat-private",2,orphanHash,recoveryNonce);
        java.security.Signature ownerSigner=java.security.Signature.getInstance("Ed25519");
        ownerSigner.initSign(owner.getPrivate());ownerSigner.update(recoveryApprovedPayload);
        byte[] signature=ownerSigner.sign();
        var blocked=CelineFutureMemoryRecall.RecoveryDisposition.BLOCKED;
        var quarantine=CelineFutureMemoryRecall.RecoveryDisposition.QUARANTINE_REVOKED_ONLY;
        eq(CelineFutureMemoryRecall.attestRevokedOrphan("cat-private",2,"REVOKED",2,
                orphanHash,recoveryNonce,signature,owner.getPublic()),quarantine,
                "owner Ed25519 attests quarantining already revoked ID only");
        eq(CelineFutureMemoryRecall.attestRevokedOrphan("cat-private",2,"ACTIVE",2,
                orphanHash,recoveryNonce,signature,owner.getPublic()),blocked,
                "never restore ACTIVE even with valid signed revoked proof");
        eq(CelineFutureMemoryRecall.attestRevokedOrphan("cat-private",1,"REVOKED",1,
                orphanHash,recoveryNonce,signature,owner.getPublic()),blocked,
                "old snapshot revision denied");
        eq(CelineFutureMemoryRecall.attestRevokedOrphan("cat-private",2,"REVOKED",3,
                orphanHash,recoveryNonce,signature,owner.getPublic()),blocked,
                "independent highwater advancement denies stale snapshot");
        eq(CelineFutureMemoryRecall.attestRevokedOrphan("cat-private",2,"REVOKED",2,
                "b".repeat(64),recoveryNonce,signature,owner.getPublic()),blocked,
                "changed orphan fingerprint denies old attestation");
        eq(CelineFutureMemoryRecall.attestRevokedOrphan("cat-private",2,"REVOKED",2,
                orphanHash,"new-recovery-nonce-999",signature,owner.getPublic()),blocked,
                "stale nonce denies replayed attestation");
        eq(CelineFutureMemoryRecall.attestRevokedOrphan("cat-private",2,"REVOKED",2,
                orphanHash,recoveryNonce,null,owner.getPublic()),blocked,
                "missing owner attestation denied");
        eq(CelineFutureMemoryRecall.attestRevokedOrphan("cat-private",2,"REVOKED",2,
                orphanHash,recoveryNonce,signature,intruder.getPublic()),blocked,
                "forged verifier identity denied when owner key pinned");
        eq(CelineFutureMemoryRecall.attestRevokedOrphan("cat-private",2,"REVOKED",2,
                orphanHash,recoveryNonce,signature,null),blocked,
                "caller-owned ordinary file never supplies trust root");
        eq(CelineFutureMemoryRecall.attestRevokedOrphan("cat-private",2,"REVOKED",2,
                "corrupt",recoveryNonce,signature,owner.getPublic()),blocked,
                "malformed orphan digest denied");
        eq(CelineFutureMemoryRecall.attestRevokedOrphan("cat-private",2,"REVOKED",2,
                orphanHash,"short",signature,owner.getPublic()),blocked,
                "missing durable challenge rejected");
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

        // File-backed G1-owner TRANSACTION MODEL: unlike the older separate
        // journal+witness fixtures, the revision, deletion tombstone, request
        // and completion receipt are updated together as one disk frame.
        // JVM-only trusted anchor survives wrapper re-instantiation but NOT a
        // real process death; no Android, power-cut or anti-rollback proof.
        {
            final java.lang.String requestQuery="Meine Katze heisst Momo";
            final java.util.concurrent.atomic.AtomicInteger fileDeliveries=new java.util.concurrent.atomic.AtomicInteger();
            final CelineFutureMemoryRecall.OwnerContextPublisher fileSink=(g,values) ->
                fileDeliveries.incrementAndGet();
            final CelineFutureMemoryRecall.OwnerPublicationOutcome complete =
                CelineFutureMemoryRecall.OwnerPublicationOutcome.COMPLETED;
            final CelineFutureMemoryRecall.OwnerPublicationOutcome started =
                CelineFutureMemoryRecall.OwnerPublicationOutcome.STARTED_INDETERMINATE;
            final CelineFutureMemoryRecall.OwnerPublicationOutcome rejected =
                CelineFutureMemoryRecall.OwnerPublicationOutcome.REJECTED_BEFORE_START;
            class Scenario {
                final java.nio.file.Path root;
                final java.util.concurrent.atomic.AtomicLong anchor=new java.util.concurrent.atomic.AtomicLong();
                final java.util.concurrent.atomic.AtomicReference<String> commitment =
                    new java.util.concurrent.atomic.AtomicReference<>();
                Scenario(String label) throws Exception {
                    root=java.nio.file.Files.createTempDirectory("future-g1-file-owner-"+label);
                }
                FileOwnerLedger open() throws Exception {
                    return new FileOwnerLedger(root,anchor,commitment,revisionNew);
                }
                CelineFutureMemoryRecall.OwnerLineageVerifier verifier(FileOwnerLedger ledger) {
                    CelineFutureMemoryRecall.OwnerSnapshotLeaseAuthority source=() ->
                        new CelineFutureMemoryRecall.OwnerSnapshotReadLease() {
                            public String generation() { return ledger.revision(); }
                            public void close() { }
                        };
                    return new CelineFutureMemoryRecall.OwnerLineageVerifier(replayKey,
                        ledger::revision,source,fileSink,ledger);
                }
                CelineFutureMemoryRecall.OwnerPublicationOutcome send(String id,
                        FileOwnerLedger ledger) {
                    return CelineFutureMemoryRecall.publishCertifiedOwnerContextOnce(
                        id,requestQuery,newProjection,newRevisionProof,verifier(ledger),8,NOW);
                }
            }
            Scenario healthy=new Scenario("healthy");
            FileOwnerLedger h=healthy.open();
            int before=fileDeliveries.get();
            eq(healthy.send("file-success",h),complete,"file-owned transaction first completion");
            eq(h.receipt(),"COMPLETED","file-backed single-frame completion persisted");
            eq(healthy.send("file-success",healthy.open()),complete,"new wrapper reads sealed completion");
            eq(fileDeliveries.get(),before+1,"file owner never replays completed callback");
            eq(healthy.anchor.get(),3L,"file owner initial + STARTED + COMPLETED share one anchor");
            // Two independently reconstructed wrappers compete for a VALID
            // fresh file-owner claim. A JVM-path monitor and permanent lock
            // inode protect the exact on-disk transaction, not two private
            // instance-level synchronized methods.
            Scenario cleanRace=new Scenario("valid-race");
            final java.util.concurrent.CountDownLatch freshReady=new java.util.concurrent.CountDownLatch(2);
            final java.util.concurrent.CountDownLatch freshGo=new java.util.concurrent.CountDownLatch(1);
            final java.util.concurrent.atomic.AtomicReference<CelineFutureMemoryRecall.OwnerPublicationOutcome>
                goodA=new java.util.concurrent.atomic.AtomicReference<>(),
                goodB=new java.util.concurrent.atomic.AtomicReference<>();
            final java.util.concurrent.atomic.AtomicReference<Throwable> raceError=
                new java.util.concurrent.atomic.AtomicReference<>();
            before=fileDeliveries.get();
            Thread fresh1=new Thread(() -> {try {FileOwnerLedger each=cleanRace.open();
                freshReady.countDown();freshGo.await();goodA.set(cleanRace.send("file-concurrent",each));}
                catch(Throwable ex){raceError.compareAndSet(null,ex);} });
            Thread fresh2=new Thread(() -> {try {FileOwnerLedger each=cleanRace.open();
                freshReady.countDown();freshGo.await();goodB.set(cleanRace.send("file-concurrent",each));}
                catch(Throwable ex){raceError.compareAndSet(null,ex);} });
            fresh1.start();fresh2.start();
            eq(freshReady.await(5,java.util.concurrent.TimeUnit.SECONDS),true,
                "fresh file-owner contenders constructed independent wrappers");
            freshGo.countDown();fresh1.join(5000);fresh2.join(5000);
            eq(fresh1.isAlive() || fresh2.isAlive(),false,"fresh file-owner race terminates");
            eq(raceError.get(),null,"file lock and JVM monitor serialize clean concurrent claim");
            eq(goodA.get()==complete || goodA.get()==started,true,
                "first valid claimant sees completed or already-started receipt");
            eq(goodB.get()==complete || goodB.get()==started,true,
                "second valid claimant sees completed or already-started receipt");
            eq(cleanRace.open().receipt(),"COMPLETED",
                "fresh two-thread race leaves one sealed completed receipt");
            eq(fileDeliveries.get(),before+1,"two file-backed contenders call downstream once");
            // A reservation may reach the protected anchor and then throw.
            // No replay is permitted even though the first callback was never
            // attempted and the sealed disk state remains only STARTED.
            Scenario reserveCommitted=new Scenario("start-then-crash");
            FileOwnerLedger start=reserveCommitted.open();
            start.reserveFault=FileOwnerLedger.Fault.AFTER_AUTHORITY_BEFORE_RETURN;
            before=fileDeliveries.get();
            eq(reserveCommitted.send("file-start-commit-crash",start),started,
                "reservation crash after atomic authority commit is indeterminate");
            eq(reserveCommitted.open().receipt(),"STARTED",
                "new wrapper sees protected STARTED despite exception before callback");
            eq(reserveCommitted.send("file-start-commit-crash",reserveCommitted.open()),started,
                "sealed STARTED never authorizes a restart retry");
            eq(fileDeliveries.get(),before,"post-reserve crash never invokes callback");
            Scenario tornReserve=new Scenario("torn-reserve");
            FileOwnerLedger reserve=tornReserve.open();
            reserve.reserveFault=FileOwnerLedger.Fault.AFTER_FRAME_BEFORE_AUTHORITY;
            before=fileDeliveries.get();
            eq(tornReserve.send("file-torn-reserve",reserve),started,
                "crash before owner authority prevents unsafe first publication");
            eq(tornReserve.open().receipt(),"UNTRUSTED",
                "torn reservation disk frame is never accepted as valid authority");
            eq(fileDeliveries.get(),before,"torn reservation invoked no callback");
            Scenario tornAck=new Scenario("torn-ack");
            FileOwnerLedger ack=tornAck.open();
            ack.completionFault=FileOwnerLedger.Fault.AFTER_FRAME_BEFORE_AUTHORITY;
            before=fileDeliveries.get();
            eq(tornAck.send("file-torn-ack",ack),started,
                "crash between receipt file and authority refuses false completed");
            eq(tornAck.open().receipt(),"UNTRUSTED",
                "post-crash completed frame without authority is never trusted");
            eq(fileDeliveries.get(),before+1,"pre-crash fileSink may have received one publication");
            final java.util.concurrent.CountDownLatch ready=new java.util.concurrent.CountDownLatch(2);
            final java.util.concurrent.CountDownLatch go=new java.util.concurrent.CountDownLatch(1);
            final java.util.concurrent.atomic.AtomicReference<CelineFutureMemoryRecall.OwnerPublicationOutcome>
                first=new java.util.concurrent.atomic.AtomicReference<>(),
                second=new java.util.concurrent.atomic.AtomicReference<>();
            Thread a=new Thread(() -> {try { ready.countDown();go.await();first.set(tornAck.send(
                "file-torn-ack",tornAck.open()));} catch(Exception ex){throw new AssertionError(ex);} });
            Thread b=new Thread(() -> {try { ready.countDown();go.await();second.set(tornAck.send(
                "file-torn-ack",tornAck.open()));} catch(Exception ex){throw new AssertionError(ex);} });
            a.start();b.start();
            eq(ready.await(5,java.util.concurrent.TimeUnit.SECONDS),true,"both post-crash contenders ready");
            go.countDown(); a.join(5000); b.join(5000);
            eq(a.isAlive() || b.isAlive(),false,"post-crash race terminates");
            eq(first.get()==complete || second.get()==complete,false,
                "no contender falsely reports COMPLETED from torn disk receipt");
            eq(first.get(),rejected,"first restarted owner fails closed on corrupt authority");
            eq(second.get(),rejected,"second restarted owner fails closed on corrupt authority");
            eq(fileDeliveries.get(),before+1,"two restarted contenders never duplicate publication");
            Scenario committedAck=new Scenario("ack-then-crash");
            FileOwnerLedger committed=committedAck.open();
            committed.completionFault=FileOwnerLedger.Fault.AFTER_AUTHORITY_BEFORE_RETURN;
            before=fileDeliveries.get();
            eq(committedAck.send("file-ack-crash",committed),started,
                "exception after atomic acknowledgement remains indeterminate to first caller");
            eq(committedAck.open().receipt(),"COMPLETED",
                "sealed completion survives model wrapper reconstruction");
            eq(committedAck.send("file-ack-crash",committedAck.open()),complete,
                "restarted owner may read valid committed receipt without another callback");
            eq(fileDeliveries.get(),before+1,"atomic committed receipt is never republished");
            Scenario revoked=new Scenario("retracted");
            FileOwnerLedger r=revoked.open();
            before=fileDeliveries.get();
            eq(revoked.send("file-before-revoke",r),complete,"pre-revocation owner can publish");
            r.revoke("g1-revision-0000000009");
            eq(revoked.open().receipt(),"COMPLETED","revocation keeps historical receipt for audit");
            eq(revoked.send("file-before-revoke",revoked.open()),rejected,
                "revoked generation never exposes formerly completed context");
            eq(revoked.send("file-after-revoke",revoked.open()),rejected,
                "persisted owner tombstone blocks all new publications");
            eq(fileDeliveries.get(),before+1,"revocation does not replay callback");
            Scenario tornRevoke=new Scenario("torn-revoke");
            FileOwnerLedger tr=tornRevoke.open();
            tr.revokeFault=FileOwnerLedger.Fault.AFTER_FRAME_BEFORE_AUTHORITY;
            try {tr.revoke("g1-revision-0000000009");throw new AssertionError("expected crash");}
            catch (IllegalStateException expected) {eq(expected.getMessage().contains("simulated crash"),true,
                "torn revocation fault injected after single frame replacement");}
            eq(tornRevoke.open().receipt(),"UNTRUSTED","torn tombstone frame refuses old revision");
            eq(tornRevoke.send("file-revoked-crash",tornRevoke.open()),rejected,
                "uncertain source revocation blocks publication after restart");
            // Corruption and restore of the SINGLE atomic frame cannot forge
            // a current tombstone/COMPLETED state while the simulated protected
            // owner commitment survives the wrapper restart.
            Scenario tampered=new Scenario("frame-rollback");
            FileOwnerLedger t=tampered.open();
            eq(tampered.send("file-tamper",t),complete,"setup protected receipt before disk attack");
            java.nio.file.Path persistent=t.frame;
            String realFrame=java.nio.file.Files.readString(persistent);
            java.nio.file.Files.writeString(persistent,realFrame.replace("|COMPLETED","|STARTED"));
            eq(tampered.open().receipt(),"UNTRUSTED","disk-only receipt rollback is detected");
            eq(tampered.send("file-tamper",tampered.open()),rejected,
                "mismatched frame never republishes or reports completed");
            java.nio.file.Files.writeString(persistent,realFrame);
            eq(tampered.open().receipt(),"COMPLETED","valid frame restore reopens without dispatch");
            java.nio.file.Files.delete(persistent);
            eq(tampered.open().receipt(),"UNTRUSTED","deleted frame with live owner anchor fails closed");
            eq(tampered.send("file-tamper",tampered.open()),rejected,
                "missing frame cannot reset journal into new publication");
            System.out.println("FUTURE_G1_FILE_OWNER_RESTART_RACE=JVM_ONLY_SINGLE_FRAME_SIMULATION");
        }
        if (Boolean.getBoolean("celine.highwater.race.only")) {
            highWaterMidflightRaceTests();
            System.out.println("FUTURE_MEMORY_HIGH_WATER_RACE_FOCUSED_PASS cases="+passed);
            return;
        }
        strictAtomicOwnerTests();
        highWaterOwnerTests();
        highWaterMidflightRaceTests();
        System.out.println("FUTURE_G1_OWNER_CONTEXT_PUBLISHER=SIMULATED_ONLY");
        System.out.println("FUTURE_G1_PROVIDER_CONTEXT_SNAPSHOT_LEASE=PROTOTYPE_ONLY");
        System.out.println("FUTURE_G1_DURABLE_ANTI_ROLLBACK_GATE=UNIMPLEMENTED");
        System.out.println("FUTURE_MEMORY_RECALL_CONTRACT_PASS cases="+passed);
    }
}
'''
# Separate JVM process fixture: test-only disk authority, not Android G1 secure storage.
child_harness = r'''
package de.yahya.ai;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.concurrent.atomic.*;

/** FUTURE-ONLY real process boundary; witness file is deliberately rollbackable. */
public final class FutureProcessOwnerChild {
    static final String REV="g1-revision-0000000001";
    static final String REQUEST="same-memory-intent";
    static final byte[] INTENT;
    static {
        try { INTENT=MessageDigest.getInstance("SHA-256")
            .digest("verified-memory-text".getBytes(StandardCharsets.UTF_8)); }
        catch(Exception e) {throw new ExceptionInInitializerError(e);}
    }
    static void witnessWrite(Path witness,AtomicLong epoch,AtomicReference<String> digest)
            throws Exception {
        Files.createDirectories(witness.getParent());
        Path temp=Files.createTempFile(witness.getParent(),"owner-witness-",".pending");
        try(FileChannel ch=FileChannel.open(temp,StandardOpenOption.WRITE)) {
            ByteBuffer b=StandardCharsets.UTF_8.encode(epoch.get()+"|"+digest.get());
            while(b.hasRemaining()) ch.write(b);
            ch.force(true);
        }
        Files.move(temp,witness,StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
        try(FileChannel ch=FileChannel.open(witness.getParent(),StandardOpenOption.READ)) {
            ch.force(true);
        }
    }
    static void report(String status,boolean reserved,
            FutureMemoryRecallContract.FileOwnerLedger ledger) {
        System.out.println("RESULT="+status+";CLAIM_NEW="+(reserved?1:0)+
            ";RECEIPT="+ledger.receipt()+
            ";CURRENT_COMPLETED="+(ledger.currentCompleted(REV)?1:0)+
            ";REVISION="+ledger.revision());
        System.out.flush();
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=3 && args.length!=4)
            throw new IllegalArgumentException("root witness action [external owner]");
        Path root=Path.of(args[0]).toAbsolutePath();
        Path witness=Path.of(args[1]).toAbsolutePath();
        String action=args[2];
        // Explicitly provisioned independent owner checkpoint, test double only.
        // This checks the authority boundary BEFORE any journal mutation; the
        // caller must never silently reinitialize a missing owner authority.
        // The ordinary test file is neither tamper-proof nor an Android/KeyStore
        // monotonic counter. Only a real G1 transaction may close the TOCTOU gap.
        if(args.length==4) {
            Path externalOwner=Path.of(args[3]).toAbsolutePath();
            if(!Files.isRegularFile(externalOwner))
                throw new IllegalStateException("external owner authority missing");
            if(!Files.isRegularFile(witness) ||
                    !MessageDigest.isEqual(Files.readAllBytes(externalOwner),
                                           Files.readAllBytes(witness)))
                throw new IllegalStateException("external owner authority mismatch");
        }
        AtomicLong epoch=new AtomicLong();
        AtomicReference<String> commitment=new AtomicReference<>();
        if(Files.exists(witness)) {
            String[] parts=Files.readString(witness).split("\\|",-1);
            if(parts.length!=2 || !parts[0].matches("[1-9][0-9]*")
                    || !parts[1].matches("[0-9a-f]{64}"))
                throw new IllegalStateException("witness corrupt");
            epoch.set(Long.parseLong(parts[0]));
            commitment.set(parts[1]);
        } else if(Files.exists(root.resolve("owner.frame"))) {
            throw new IllegalStateException("lost independent witness: FAIL CLOSED");
        }
        FutureMemoryRecallContract.FileOwnerLedger ledger=new
            FutureMemoryRecallContract.FileOwnerLedger(root,epoch,commitment,REV);
        if(!Files.exists(witness)) witnessWrite(witness,epoch,commitment);
        if(action.equals("init") || action.equals("check")) {
            report(action,false,ledger);
            return;
        }
        if(action.equals("revoke")) {
            ledger.revoke("g1-revision-0000000009");
            witnessWrite(witness,epoch,commitment);
            report("revoke",false,ledger);
            return;
        }
        if(action.equals("reserve-torn"))
            ledger.reserveFault=FutureMemoryRecallContract.FileOwnerLedger.Fault.AFTER_FRAME_BEFORE_AUTHORITY;
        var claim=ledger.reserve(REQUEST,INTENT);
        if(action.equals("reserve-torn")) {
            report("HALT_AFTER_TORN_RESERVE",false,ledger);
            Runtime.getRuntime().halt(41); // genuine JVM termination: frame updated, witness not
        }
        if(claim!=CelineFutureMemoryRecall.OwnerPublicationJournal.Reservation.NEW_STARTED) {
            report("no-new-claim-"+claim,false,ledger);
            return;
        }
        // Each successful claim seals the separately held *test* witness;
        // two files cannot be made atomic across a real power loss.
        witnessWrite(witness,epoch,commitment);
        // FUTURE-ONLY *actual OS-process* cancellation/claim-vs-revocation
        // harness. These deliberate side effects are local TEST markers, never
        // Android provider dispatch. STARTED is durable before the marker.
        if(action.equals("pause-after-dispatch") || action.equals("reentrant-revoke")) {
            Files.writeString(root.resolve("test-only-sink-event"),
                    "published-once\n",StandardOpenOption.CREATE_NEW);
            if(action.equals("pause-after-dispatch")) {
                report("WAITING_AFTER_CALLBACK",true,ledger);
                Path allow=root.resolve("allow-finish");
                long deadline=System.nanoTime()+30_000_000_000L;
                while(!Files.exists(allow) && System.nanoTime()<deadline)
                    Thread.sleep(20);
                if(!Files.exists(allow))
                    throw new IllegalStateException("test-only callback gate timed out");
            } else {
                // A source mutation re-entering from a synchronous callback
                // invalidates completion; do not claim CURRENT_COMPLETED.
                ledger.revoke("g1-revision-0000000009");
                witnessWrite(witness,epoch,commitment);
            }
        }
        if(action.equals("ack-torn"))
            ledger.completionFault=FutureMemoryRecallContract.FileOwnerLedger.Fault.AFTER_FRAME_BEFORE_AUTHORITY;
        if(action.equals("ack-sealed-halt"))
            ledger.completionFault=FutureMemoryRecallContract.FileOwnerLedger.Fault.AFTER_AUTHORITY_BEFORE_RETURN;
        try {
            boolean ack=ledger.acknowledgeCompletedIfCurrent(REQUEST,INTENT,REV);
            if(ack) witnessWrite(witness,epoch,commitment);
            report("conditional-ack="+ack,true,ledger);
        } catch(IllegalStateException e) {
            // A separate process may revoke and advance the trusted witness
            // while this publisher is paused. Its stale in-memory witness must
            // NEVER be written back over the newer authority on cancellation.
            if(action.equals("pause-after-dispatch")) {
                report("ACK_UNCERTAIN",true,ledger);
                return;
            }
            if(action.equals("ack-torn")) {
                report("HALT_AFTER_TORN_ACK",true,ledger);
                Runtime.getRuntime().halt(42); // verified later by independent JVM
            }
            if(action.equals("ack-sealed-halt")) {
                witnessWrite(witness,epoch,commitment);
                report("HALT_AFTER_SEALED_ACK",true,ledger);
                Runtime.getRuntime().halt(43);
            }
            throw e;
        }
    }
}
'''

strict_child_harness = r'''
package de.yahya.ai;
import java.nio.file.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** FUTURE-ONLY Java process simulation of STRICT owner API. NOT Android/antirollback. */
public final class FutureStrictOwnerProcessChild {
    static final String GEN="fixture-source-snapshot-1";
    static final byte[] KEY=new byte[32];
    static { Arrays.fill(KEY,(byte)0x29); }
    interface Tx<T> { T run() throws Exception; }
    static String sha(String value) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
    static void atomic(Path file,String value,boolean crashMidStagingWrite,
                       boolean crashAfterRenameBeforeDirectoryForce) throws Exception {
        Path pending=Files.createTempFile(file.getParent(),"strict-owner-",".pending");
        try(FileChannel ch=FileChannel.open(pending,StandardOpenOption.WRITE)) {
            java.nio.ByteBuffer buf=StandardCharsets.UTF_8.encode(value);
            if(crashMidStagingWrite) {
                // Test-only: force a partial write in an actual JVM before
                // rename; never pass this option in normal owner operations.
                boolean isAnchor=file.getFileName().toString().endsWith("versioned-anchor");
                buf.limit(Math.min(isAnchor?7:6,buf.limit()));
                while(buf.hasRemaining()) ch.write(buf);
                ch.force(true);
                System.out.println(isAnchor?"STRICT_FAULT_PARTIAL_ANCHOR_STAGE"
                                           :"STRICT_FAULT_PARTIAL_FRAME_STAGE");
                System.out.flush();
                Runtime.getRuntime().halt(isAnchor?94:95);
            }
            while(buf.hasRemaining()) ch.write(buf);
            ch.force(true);
        }
        Files.move(pending,file,StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
        if(crashAfterRenameBeforeDirectoryForce) {
            // A real JVM abort between atomic move and directory force. The
            // test does NOT claim this reproduces a true power-cut loss of the
            // directory entry: a separate explicit rollback model tests it.
            System.out.println("STRICT_FAULT_RENAMED_BEFORE_DIR_FORCE:"+file.getFileName());
            System.out.flush();
            Runtime.getRuntime().halt(96);
        }
        // Force the parent directory entry on POSIX-capable test hosts. This
        // is a Java fixture only; Android filesystem support and hardware
        // anti-rollback still require independent real-device evidence.
        try(FileChannel directory=FileChannel.open(file.getParent(),
                StandardOpenOption.READ)) {
            directory.force(true);
        }
    }
    static void atomic(Path file,String value,boolean crashMidStagingWrite) throws Exception {
        atomic(file,value,crashMidStagingWrite,false);
    }
    static void atomic(Path file,String value) throws Exception {
        atomic(file,value,false,false);
    }
    static final class State {
        final long epoch;
        final String generation, receipt, id, intent;
        State(long epoch,String generation,String receipt,String id,String intent) {
            this.epoch=epoch; this.generation=generation;
            this.receipt=receipt; this.id=id; this.intent=intent;
        }
        String encode(){return epoch+"|"+generation+"|"+receipt+"|"+id+"|"+intent;}
        static State decode(String text) {
            String[] fields=text.split("\\|",-1);
            if(fields.length!=5 || !fields[0].matches("[1-9][0-9]*")
                || !fields[1].matches("[a-zA-Z0-9_-]{1,100}")
                || !(fields[2].equals("NONE") || fields[2].equals("STARTED")
                    || fields[2].equals("COMPLETED"))
                || !fields[3].matches("-|[a-zA-Z0-9_-]{1,100}")
                || !fields[4].matches("-|[0-9a-f]{64}")
                || (fields[2].equals("NONE") != fields[3].equals("-")))
                throw new IllegalStateException("strict fixture invalid state");
            return new State(Long.parseLong(fields[0]),fields[1],fields[2],fields[3],fields[4]);
        }
    }
    static final class DiskOwner implements CelineFutureMemoryRecall.OwnerAtomicDispatchAuthority {
        final Path root, frame, anchor, lock;
        // Test-only crash injection, never implemented in the production owner.
        String crashAction="";
        final ThreadLocal<Boolean> ownsTransaction=ThreadLocal.withInitial(()->false);
        DiskOwner(Path directory) throws Exception {
            root=directory.toAbsolutePath(); Files.createDirectories(root);
            frame=root.resolve("strict-owner.frame");
            anchor=root.resolve("strict-owner.versioned-anchor");
            lock=root.resolve("strict-owner.lock");
            withLock(()->{
                if(!Files.exists(frame) && !Files.exists(anchor))
                    seal(new State(1,GEN,"NONE","-","-"));
                verified(); return null;
            });
        }
        <T> T withLock(Tx<T> operation) throws Exception {
            try(FileChannel ch=FileChannel.open(lock,StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);FileLock exclusive=ch.lock()) {
                ownsTransaction.set(true);
                try {return operation.run();}
                finally {ownsTransaction.remove();}
            }
        }
        State verified() throws Exception {
            if(!Files.isRegularFile(frame) || !Files.isRegularFile(anchor))
                throw new IllegalStateException("missing owner state or anchor");
            String frameText=Files.readString(frame);
            State s=State.decode(frameText);
            if(!(s.epoch+"|"+sha(frameText)).equals(Files.readString(anchor)))
                throw new IllegalStateException("rollback or torn owner seal");
            return s;
        }
        void seal(State next) throws Exception {
            // A single process lock serializes callers; TWO files are NOT
            // power-cut atomic or rollback-proof even with forced writes.
            String value=next.encode();
            boolean crashMidFrameWrite =
                (crashAction.equals("fault-partial-frame-reserve")
                    && next.receipt.equals("STARTED"))
                || (crashAction.equals("fault-partial-frame-ack")
                    && next.receipt.equals("COMPLETED"))
                || (crashAction.equals("fault-partial-frame-revoke")
                    && next.generation.equals("revoked-generation"));
            boolean crashAfterFrameRename =
                (crashAction.equals("fault-dir-frame-reserve")
                    && next.receipt.equals("STARTED"))
                || (crashAction.equals("fault-dir-frame-ack")
                    && next.receipt.equals("COMPLETED"))
                || (crashAction.equals("fault-dir-frame-revoke")
                    && next.generation.equals("revoked-generation"));
            atomic(frame,value,crashMidFrameWrite,crashAfterFrameRename);
            boolean crash = (crashAction.equals("fault-reserve")
                    && next.receipt.equals("STARTED"))
                || (crashAction.equals("fault-ack")
                    && next.receipt.equals("COMPLETED"))
                || (crashAction.equals("fault-revoke")
                    && next.generation.equals("revoked-generation"));
            if(crash) {
                // HALT is an actual abrupt child JVM death, not an exception
                // that the caller could catch and mistakenly report as success.
                System.out.println("STRICT_FAULT_FRAME_REPLACED");System.out.flush();
                Runtime.getRuntime().halt(93);
            }
            boolean crashMidAnchorWrite =
                (crashAction.equals("fault-partial-anchor-reserve")
                    && next.receipt.equals("STARTED"))
                || (crashAction.equals("fault-partial-anchor-ack")
                    && next.receipt.equals("COMPLETED"))
                || (crashAction.equals("fault-partial-anchor-revoke")
                    && next.generation.equals("revoked-generation"));
            boolean crashAfterAnchorRename =
                (crashAction.equals("fault-dir-anchor-reserve")
                    && next.receipt.equals("STARTED"))
                || (crashAction.equals("fault-dir-anchor-ack")
                    && next.receipt.equals("COMPLETED"))
                || (crashAction.equals("fault-dir-anchor-revoke")
                    && next.generation.equals("revoked-generation"));
            atomic(anchor,next.epoch+"|"+sha(value),crashMidAnchorWrite,
                crashAfterAnchorRename);
        }
        @Override public String currentGeneration() {
            try{return withLock(()->verified().generation);}
            catch(Exception unsafe){return "";}
        }
        @Override public CelineFutureMemoryRecall.OwnerSnapshotReadLease acquireReadLease() {
            String generation=currentGeneration();
            if(generation.isEmpty()) return null;
            return new CelineFutureMemoryRecall.OwnerSnapshotReadLease() {
                @Override public String generation(){return generation;}
                @Override public void close(){} // not a retained OS lock
            };
        }
        @Override public CelineFutureMemoryRecall.OwnerPublicationOutcome
                dispatchUnderOwnerLock(String generation,String id,byte[] digest,Runnable publisher) {
            var uncertain=CelineFutureMemoryRecall.OwnerPublicationOutcome.STARTED_INDETERMINATE;
            try{return withLock(()->{
                State before=verified();
                if(!before.generation.equals(generation))
                    return CelineFutureMemoryRecall.OwnerPublicationOutcome.REJECTED_BEFORE_START;
                String intent=java.util.HexFormat.of().formatHex(digest);
                if(!before.receipt.equals("NONE")) {
                    if(!before.id.equals(id)||!before.intent.equals(intent))return uncertain;
                    return before.receipt.equals("COMPLETED")
                        ? CelineFutureMemoryRecall.OwnerPublicationOutcome.COMPLETED : uncertain;
                }
                seal(new State(before.epoch+1,generation,"STARTED",id,intent));
                try{publisher.run();}catch(RuntimeException interrupted){return uncertain;}
                State after=verified(); // reentrant owner source change must be seen
                if(!after.generation.equals(generation)||!after.receipt.equals("STARTED")
                    ||!after.id.equals(id)||!after.intent.equals(intent))return uncertain;
                seal(new State(after.epoch+1,generation,"COMPLETED",id,intent));
                return CelineFutureMemoryRecall.OwnerPublicationOutcome.COMPLETED;
            });}catch(Exception unsafe){return uncertain;}
        }
        void revoke() throws Exception {
            withLock(()->{reentrantRevoke();return null;});
        }
        void reentrantRevoke() throws Exception {
            if(!ownsTransaction.get())throw new IllegalStateException("must own owner transaction");
            State s=verified(); seal(new State(s.epoch+1,"revoked-generation",s.receipt,s.id,s.intent));
        }
        String status() {
            try{return withLock(()->{
                State s=verified();
                return "STATE="+s.receipt+";EPOCH="+s.epoch+";GEN="+s.generation+
                  ";CURRENT_COMPLETED="+((s.generation.equals(GEN)&&s.receipt.equals("COMPLETED"))?1:0);
            });} catch(Exception ex){return "STATE=UNTRUSTED;CURRENT_COMPLETED=0";}
        }
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("root action");
        Path root=Path.of(args[0]); String action=args[1];
        DiskOwner owner=new DiskOwner(root);
        owner.crashAction=action;
        if(action.equals("init")||action.equals("check")) {
            System.out.println("RESULT="+action+";"+owner.status()); return;
        }
        if(action.equals("revoke")||action.equals("fault-revoke")
           ||action.equals("fault-partial-anchor-revoke")
           ||action.equals("fault-partial-frame-revoke")
           ||action.equals("fault-dir-frame-revoke")
           ||action.equals("fault-dir-anchor-revoke")) {
            owner.revoke();System.out.println("RESULT=revoke;"+owner.status()); return;
        }
        var candidate=new CelineFutureMemoryRecall.Candidate("strict-cat","Meine Katze Luna","",
            CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,true,true,1_900_000_000_000L-100,0,0.5);
        var entries=List.of(candidate);
        byte[] payload=CelineFutureMemoryRecall.certificatePayload(GEN,entries,Set.of(),Set.of());
        Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(KEY,"HmacSHA256"));
        var cert=new CelineFutureMemoryRecall.OwnerLineageCertificate(GEN,Set.of(),Set.of(),mac.doFinal(payload));
        var verifier=new CelineFutureMemoryRecall.OwnerLineageVerifier(KEY,owner,(gen,summaries)->{
            try {
                // A provider may be non-idempotent. An append-only local marker
                // must reveal repeated deliveries, not hide them via CREATE_NEW.
                Files.writeString(root.resolve("test-only-strict-sink-event"),"once\\n",
                                  StandardOpenOption.CREATE,StandardOpenOption.APPEND);
                if(action.equals("pause")) {
                    System.out.println("CALLBACK_REACHED");
                    System.out.flush();
                    long until=System.nanoTime()+30_000_000_000L;
                    while(!Files.exists(root.resolve("allow-finish")) && System.nanoTime()<until)
                        Thread.sleep(20);
                    if(!Files.exists(root.resolve("allow-finish")))
                        throw new IllegalStateException("fixture callback timed out");
                }
                if(action.equals("reentrant"))owner.reentrantRevoke();
            } catch (Exception failure) {throw new IllegalStateException(failure);}
        });
        var result=CelineFutureMemoryRecall.publishCertifiedOwnerContextAtomic("strict-request",
             "Meine Katze Luna",entries,cert,verifier,8,1_900_000_000_000L);
        System.out.println("RESULT="+result+";"+owner.status());
        System.out.flush();
    }
}
'''

# Two independent JVMs race a third JVM changing a separately maintained
# high-water witness AFTER public preflight, BEFORE the owner file-lock txn.
# This is a rollbackable test fixture, NEVER an Android antirollback claim.
high_water_process_harness = r'''
package de.yahya.ai;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Isolated post-v83 strict-owner high-water TOCTOU contract, JVM-only. */
public final class FutureHighWaterProcessChild {
    static final String GEN=FutureStrictOwnerProcessChild.GEN;
    static final byte[] KEY=FutureStrictOwnerProcessChild.KEY;
    static final String REQUEST="high-water-process-request";
    static final String WITNESS_CURRENT=GEN+"|1";
    static final String WITNESS_ADVANCED="future-owner-generation-2|2";
    static final class WitnessOwner
            implements CelineFutureMemoryRecall.OwnerHighWaterDispatchAuthority {
        final Path root, witness;
        final FutureStrictOwnerProcessChild.DiskOwner local;
        final boolean gate;
        WitnessOwner(Path root,Path witness,boolean gate) throws Exception {
            this.root=root;
            this.witness=witness;
            this.local=new FutureStrictOwnerProcessChild.DiskOwner(root);
            this.gate=gate;
        }
        @Override public CelineFutureMemoryRecall.OwnerHighWaterStatus
                protectedGenerationStatus(String expected) {
            if(!Files.isRegularFile(witness))
                return CelineFutureMemoryRecall.OwnerHighWaterStatus.MISSING;
            try {
                String value=Files.readString(witness);
                String[] fields=value.split("\\|",-1);
                if(fields.length!=2 || !fields[0].matches("[a-zA-Z0-9_-]{1,100}")
                        || !fields[1].matches("[1-9][0-9]*"))
                    return CelineFutureMemoryRecall.OwnerHighWaterStatus.COMPROMISED;
                return expected.equals(fields[0])
                    ? CelineFutureMemoryRecall.OwnerHighWaterStatus.CURRENT
                    : CelineFutureMemoryRecall.OwnerHighWaterStatus.ROLLED_BACK;
            } catch (Exception failure) {
                return CelineFutureMemoryRecall.OwnerHighWaterStatus.COMPROMISED;
            }
        }
        @Override public String currentGeneration() {return local.currentGeneration();}
        @Override public CelineFutureMemoryRecall.OwnerSnapshotReadLease acquireReadLease() {
            if(gate) {
                try {
                    Files.createFile(root.resolve("ready-"+ProcessHandle.current().pid()));
                    long until=System.nanoTime()+15_000_000_000L;
                    while(!Files.exists(root.resolve("release-high-water-race"))
                            && System.nanoTime()<until)Thread.sleep(15);
                    if(!Files.exists(root.resolve("release-high-water-race")))
                        throw new IllegalStateException("test-only preflight barrier timeout");
                } catch (Exception failure) {throw new IllegalStateException(failure);}
            }
            return local.acquireReadLease();
        }
        @Override public CelineFutureMemoryRecall.OwnerPublicationOutcome dispatchUnderOwnerLock(
                String generation,String requestId,byte[] digest,Runnable publisher) {
            var reject=CelineFutureMemoryRecall.OwnerPublicationOutcome.REJECTED_BEFORE_START;
            var uncertain=CelineFutureMemoryRecall.OwnerPublicationOutcome.STARTED_INDETERMINATE;
            try {
                return local.withLock(()->{
                    var before=local.verified();
                    if(!before.generation.equals(generation))return reject;
                    // INTERNAL_AUTHORIZATION_GUARD: source-lock and witness-check
                    // MUST be serialized, including for an old COMPLETED receipt.
                    // A missing/mismatched witness must not reach the callback.
                    if (!Boolean.getBoolean("celine.fixture.skipInnerHighWater")
                            && protectedGenerationStatus(generation)
                               != CelineFutureMemoryRecall.OwnerHighWaterStatus.CURRENT)
                        return reject;
                    String intent=java.util.HexFormat.of().formatHex(digest);
                    if(!before.receipt.equals("NONE")) {
                        if(!before.id.equals(requestId)||!before.intent.equals(intent))return uncertain;
                        return before.receipt.equals("COMPLETED")
                            ? CelineFutureMemoryRecall.OwnerPublicationOutcome.COMPLETED : uncertain;
                    }
                    local.seal(new FutureStrictOwnerProcessChild.State(before.epoch+1,
                        generation,"STARTED",requestId,intent));
                    try {publisher.run();}catch(RuntimeException e){return uncertain;}
                    var after=local.verified();
                    if(protectedGenerationStatus(generation)
                            != CelineFutureMemoryRecall.OwnerHighWaterStatus.CURRENT
                        || !after.generation.equals(generation)
                        || !after.receipt.equals("STARTED")
                        || !after.id.equals(requestId)||!after.intent.equals(intent))return uncertain;
                    local.seal(new FutureStrictOwnerProcessChild.State(after.epoch+1,
                        generation,"COMPLETED",requestId,intent));
                    return CelineFutureMemoryRecall.OwnerPublicationOutcome.COMPLETED;
                });
            } catch (Exception failure) {return uncertain;}
        }
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=3)throw new IllegalArgumentException("root witness action");
        Path root=Path.of(args[0]), witness=Path.of(args[1]);
        String action=args[2];
        Files.createDirectories(root);
        Files.createDirectories(witness.getParent());
        // Mark attempted write BEFORE constructing DiskOwner: its constructor
        // itself acquires the same OS file lock and may be blocked by callback.
        if(action.equals("advance-serialized"))
            Files.createFile(root.resolve("writer-requested"));
        // NEGATIVE CONTROL ONLY: a witness writer outside the source lock can
        // change authority while an external callback is already in flight.
        if(action.equals("advance-unlocked-testonly")) {
            FutureStrictOwnerProcessChild.atomic(witness,WITNESS_ADVANCED);
            System.out.println("WITNESS_MUTATED=unsafe-unlocked");return;
        }
        WitnessOwner owner=new WitnessOwner(root,witness,action.equals("pause-claim"));
        if(action.equals("init")) {
            if(Files.exists(witness))throw new IllegalStateException("witness already exists");
            FutureStrictOwnerProcessChild.atomic(witness,WITNESS_CURRENT);
            System.out.println("INIT_OWNER_AND_WITNESS");return;
        }
        if(action.equals("advance")||action.equals("advance-serialized")
                ||action.equals("erase")||action.equals("corrupt")) {
            owner.local.withLock(()->{
                if(action.equals("advance")||action.equals("advance-serialized"))
                    FutureStrictOwnerProcessChild.atomic(witness,WITNESS_ADVANCED);
                else if(action.equals("erase"))Files.delete(witness);
                else FutureStrictOwnerProcessChild.atomic(witness,"malformed-epoch");
                return null;
            });
            if(action.equals("advance-serialized"))
                Files.createFile(root.resolve("writer-finished"));
            System.out.println("WITNESS_MUTATED="+action);return;
        }
        var candidate=new CelineFutureMemoryRecall.Candidate("strict-cat","Meine Katze Luna","",
            CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,true,true,1_900_000_000_000L-100,0,0.5);
        var entries=List.of(candidate);
        byte[] data=CelineFutureMemoryRecall.certificatePayload(GEN,entries,Set.of(),Set.of());
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(KEY,"HmacSHA256"));
        var cert=new CelineFutureMemoryRecall.OwnerLineageCertificate(GEN,Set.of(),Set.of(),mac.doFinal(data));
        var verifier=new CelineFutureMemoryRecall.OwnerLineageVerifier(KEY,owner,(gen,contexts)->{
            try {
                if(action.equals("hold-claim")) {
                    Files.createFile(root.resolve("callback-inside-source-lock"));
                    long until=System.nanoTime()+15_000_000_000L;
                    while(!Files.exists(root.resolve("release-callback"))
                            && System.nanoTime()<until) Thread.sleep(15);
                    if(!Files.exists(root.resolve("release-callback")))
                        throw new IllegalStateException("callback lock hold timed out");
                }
                Files.writeString(root.resolve("test-only-high-water-sink"),"sent\\n",
                    StandardOpenOption.CREATE,StandardOpenOption.APPEND);
                if(action.equals("hold-after-sink")) {
                    Files.createFile(root.resolve("callback-after-sink-source-lock"));
                    long until=System.nanoTime()+15_000_000_000L;
                    while(!Files.exists(root.resolve("release-after-sink"))
                            && System.nanoTime()<until) Thread.sleep(15);
                    if(!Files.exists(root.resolve("release-after-sink")))
                        throw new IllegalStateException("post-sink callback gate timeout");
                }
            }
            catch(Exception failure){throw new IllegalStateException(failure);}
        });
        var result=CelineFutureMemoryRecall.publishCertifiedOwnerContextHighWater(
            REQUEST,"Meine Katze Luna",entries,cert,verifier,8,1_900_000_000_000L);
        System.out.println("RESULT="+result+";HIGH_WATER="+owner.protectedGenerationStatus(GEN)
            +";"+owner.local.status());
        System.out.flush();
    }
}
'''

def process_owner_proof(temp):
    """Isolated JVM boundary checks, not an Android/anti-rollback guarantee."""
    checks = 0
    cls = 'de.yahya.ai.FutureProcessOwnerChild'
    def case(condition, name):
        nonlocal checks
        if not condition:
            raise AssertionError('PROCESS_OWNER_FAIL: ' + name)
        checks += 1
    def new_scenario(label):
        root=Path(temp)/('process-'+label)
        (root/'journal').mkdir(parents=True)
        (root/'witness').mkdir()
        return (root/'journal',root/'witness'/'authority.txt')
    def command(journal,witness,action):
        return ['java','-cp',temp,cls,str(journal),str(witness),action]
    def call(journal,witness,action,code=0):
        result=subprocess.run(command(journal,witness,action),text=True,
                              stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=20)
        case(result.returncode==code, action+' exit '+str(result.returncode)+
             ' expected '+str(code)+': '+result.stderr[-500:])
        return result.stdout
    def claimed(output): return 'CLAIM_NEW=1' in output
    # Real JVM #1 creates owner frame + *separate* rollbackable test witness.
    journal,witness=new_scenario('healthy')
    case('RECEIPT=NONE' in call(journal,witness,'init'),'JVM init starts NONE')
    case(claimed(call(journal,witness,'publish')),'JVM claim/publish once')
    case('RECEIPT=COMPLETED' in call(journal,witness,'check'),'new JVM reads sealed completion')
    case(not claimed(call(journal,witness,'publish')),'new JVM suppresses duplicate publish')
    # Interrupted reserve: child hard-terminates after atomic frame write.
    journal,witness=new_scenario('reserve-torn')
    call(journal,witness,'init')
    case('HALT_AFTER_TORN_RESERVE' in call(journal,witness,'reserve-torn',41),
         'actual JVM halt at reserve fault point')
    case('RECEIPT=UNTRUSTED' in call(journal,witness,'check'),
         'next JVM refuses torn reservation frame')
    case(not claimed(call(journal,witness,'publish')),
         'next JVM does not publish torn reservation')
    # Interrupted ACK: earlier STARTED authority survives, false durable COMPLETED rejected.
    journal,witness=new_scenario('ack-torn')
    call(journal,witness,'init')
    case('HALT_AFTER_TORN_ACK' in call(journal,witness,'ack-torn',42),
         'actual JVM halt after completion frame before witness')
    case('RECEIPT=UNTRUSTED' in call(journal,witness,'check'),
         'next JVM does not trust unsealed completed receipt')
    case(not claimed(call(journal,witness,'publish')),
         'uncertain ACK never retries publication')
    # Commit ACK authority, then genuinely halt before returning to caller.
    journal,witness=new_scenario('ack-sealed')
    call(journal,witness,'init')
    case('HALT_AFTER_SEALED_ACK' in call(journal,witness,'ack-sealed-halt',43),
         'actual JVM halt after durable fixture authority')
    case('RECEIPT=COMPLETED' in call(journal,witness,'check'),
         'new JVM recovers sealed receipt')
    case(not claimed(call(journal,witness,'publish')),
         'sealed ACK not re-published after JVM death')
    # Real distinct JVM contenders, not just in-process Java threads.
    journal,witness=new_scenario('cross-process')
    call(journal,witness,'init')
    p1=subprocess.Popen(command(journal,witness,'publish'),stdout=subprocess.PIPE,
                        stderr=subprocess.PIPE,text=True)
    p2=subprocess.Popen(command(journal,witness,'publish'),stdout=subprocess.PIPE,
                        stderr=subprocess.PIPE,text=True)
    out1,err1=p1.communicate(timeout=20)
    out2,err2=p2.communicate(timeout=20)
    case(p1.returncode==0 and p2.returncode==0,
         'independent JVM contenders terminate '+err1[-300:]+' '+err2[-300:])
    case(int(claimed(out1))+int(claimed(out2))==1,
         'two JVMs cannot both acquire first claim: '+out1+' / '+out2)
    case('RECEIPT=COMPLETED' in call(journal,witness,'check'),
         'cross-process completed receipt readback')
    # Orphan atomic-rename temp file must not itself count as a committed state.
    (journal/'owner.pending-uncommitted').write_text('garbled-transaction',encoding='utf-8')
    case('RECEIPT=COMPLETED' in call(journal,witness,'check'),
         'orphan pending file is ignored, committed frame retained')
    # Missing separately held witness must fail closed if journal exists.
    journal,witness=new_scenario('missing-witness')
    call(journal,witness,'init')
    witness.unlink()
    missing=subprocess.run(command(journal,witness,'publish'),text=True,capture_output=True,timeout=20)
    case(missing.returncode!=0 and 'lost independent witness' in missing.stderr,
         'witness loss with frame present fails closed')
    # Tombstone survives a REAL process exit, blocks new claim and old revision.
    journal,witness=new_scenario('revoke')
    call(journal,witness,'init')
    call(journal,witness,'publish')
    case('REVISION=' in call(journal,witness,'revoke'),'revoke writes tombstone')
    case('REVISION=\n' in call(journal,witness,'check'),
         'fresh JVM sees revoked revision as empty')
    case(not claimed(call(journal,witness,'publish')),
         'retraction blocks a fresh JVM publication')
    # KNOWN VULNERABILITY: both authority and journal rolled back, no independent
    # non-rollbackable trust source can distinguish the forged old state.
    journal,witness=new_scenario('joint-rollback')
    call(journal,witness,'init')
    old_frame=(journal/'owner.frame').read_bytes()
    old_witness=witness.read_bytes()
    case(claimed(call(journal,witness,'publish')),
         'original publication before rollback')
    (journal/'owner.frame').write_bytes(old_frame)
    witness.write_bytes(old_witness)
    case('RECEIPT=NONE' in call(journal,witness,'check'),
         'joint rollback impersonates a fresh trusted state')
    case(claimed(call(journal,witness,'publish')),
         'KNOWN FAIL: joint disk + witness rollback allows duplicate publication')
    # FUTURE-ONLY owner authority contract: this OUT-OF-BAND anchor is a test
    # double, deliberately stored as a plain rollbackable file by the host.
    # A legitimate G1 owner would have to provide a genuinely independent,
    # authenticated, nonrollbackable authority plus crash-atomic sealing.
    journal,witness=new_scenario('external-authority-contract')
    call(journal,witness,'init')
    owner_anchor=journal.parent/'independent-test-only-owner-anchor.txt'
    owner_anchor.write_bytes(witness.read_bytes())
    frame_before=(journal/'owner.frame').read_bytes()
    witness_before=witness.read_bytes()
    def anchored(action):
        return subprocess.run(command(journal,witness,action)+[str(owner_anchor)],
                              capture_output=True,text=True,timeout=20)
    ok=anchored('check')
    case(ok.returncode==0 and 'RECEIPT=NONE' in ok.stdout,
         'separately provisioned matching authority permits initial read')
    ok=anchored('publish')
    case(ok.returncode==0 and claimed(ok.stdout),
         'separately authorized initial request can claim once')
    stale=anchored('check')
    case(stale.returncode!=0 and 'external owner authority mismatch' in stale.stderr,
         'owner checkpoint still old must fail closed until committed externally')
    # The Python host explicitly represents the separate owner, not Android.
    owner_anchor.write_bytes(witness.read_bytes())
    ok=anchored('check')
    case(ok.returncode==0 and 'RECEIPT=COMPLETED' in ok.stdout,
         'matching separately held owner checkpoint reads durable completion')
    ok=anchored('publish')
    case(ok.returncode==0 and not claimed(ok.stdout),
         'completed owner checkpoint suppresses second publication')
    # A snapshot attacker restores BOTH old application files but cannot alter
    # the separately held owner checkpoint in this restricted threat model.
    (journal/'owner.frame').write_bytes(frame_before)
    witness.write_bytes(witness_before)
    mismatch=anchored('check')
    case(mismatch.returncode!=0 and 'external owner authority mismatch' in mismatch.stderr,
         'joint app-file rollback detected with surviving external authority')
    contenders=[subprocess.Popen(command(journal,witness,'publish')+[str(owner_anchor)],
                stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
                for _ in range(2)]
    outcomes=[p.communicate(timeout=20) for p in contenders]
    case(all(p.returncode!=0 for p in contenders) and
         all('external owner authority mismatch' in stderr for _,stderr in outcomes) and
         all(not claimed(stdout) for stdout,_ in outcomes),
         'two fresh JVMs cannot claim after app rollback with surviving owner')
    # Fail closed on unprovisioned/malformed independent authority.
    owner_anchor.unlink()
    missing=anchored('publish')
    case(missing.returncode!=0 and 'external owner authority missing' in missing.stderr,
         'missing independent authority rejects publication')
    owner_anchor.write_text('invalid-owner-record',encoding='utf-8')
    corrupt=anchored('publish')
    case(corrupt.returncode!=0 and 'external owner authority mismatch' in corrupt.stderr,
         'corrupt independently held authority rejects publication')
    # This file is NOT an anti-rollback primitive: a malicious joint rollback
    # of all THREE plain test files still defeats the proof by construction.
    owner_anchor.write_bytes(witness_before)
    forged=anchored('check')
    case(forged.returncode==0 and 'RECEIPT=NONE' in forged.stdout,
         'KNOWN VULNERABILITY: rollback of test anchor as well impersonates old owner')
    forged=anchored('publish')
    case(forged.returncode==0 and claimed(forged.stdout),
         'KNOWN VULNERABILITY: all-file rollback replays already published memory')
    # A separate owner has to fence retractions as well as completions:
    # a restore of pre-tombstone app state may not resurrect old generation.
    owner_anchor.write_bytes(witness.read_bytes())
    before_revoke_frame=(journal/'owner.frame').read_bytes()
    before_revoke_witness=witness.read_bytes()
    revoked=anchored('revoke')
    case(revoked.returncode==0 and 'REVISION=' in revoked.stdout,
         'separately authorized owner revocation records a tombstone')
    owner_anchor.write_bytes(witness.read_bytes())
    revoked=anchored('check')
    case(revoked.returncode==0 and 'REVISION=\n' in revoked.stdout,
         'new JVM sees current tombstone after owner checkpoint')
    revoked=anchored('publish')
    case(revoked.returncode==0 and not claimed(revoked.stdout),
         'fresh JVM cannot republish a revoked generation')
    (journal/'owner.frame').write_bytes(before_revoke_frame)
    witness.write_bytes(before_revoke_witness)
    replay=anchored('publish')
    case(replay.returncode!=0 and 'external owner authority mismatch' in replay.stderr,
         'old pre-tombstone app snapshot fails against surviving owner checkpoint')
    # Genuine JVM cancellation after simulated downstream side effect but
    # before receipt sealing. A STARTED receipt MUST survive a process kill.
    journal,witness=new_scenario('process-cancel-after-callback')
    call(journal,witness,'init')
    owner_anchor=journal.parent/'owner-checkpoint-versioned.txt'
    owner_anchor.write_bytes(witness.read_bytes())
    def spawn_waiter():
        p=subprocess.Popen(command(journal,witness,'pause-after-dispatch')+[str(owner_anchor)],
                           stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,bufsize=1)
        line=p.stdout.readline().strip()
        case('RESULT=WAITING_AFTER_CALLBACK' in line and 'RECEIPT=STARTED' in line,
             'independent Java process reached durable STARTED after callback: '+line)
        return p
    waiter=spawn_waiter()
    case((journal/'test-only-sink-event').read_text()=='published-once\n',
         'callback side effect exists before process cancellation')
    blocked=subprocess.run(command(journal,witness,'publish'),
                           capture_output=True,text=True,timeout=20)
    case(blocked.returncode==0 and not claimed(blocked.stdout)
         and 'CURRENT_COMPLETED=0' in blocked.stdout,
         'second independent JVM sees STARTED and cannot republish during callback')
    waiter.kill()  # actual abrupt OS-process kill, no cooperative Java cleanup
    try: waiter.communicate(timeout=5)
    except subprocess.TimeoutExpired:
        waiter.kill();waiter.communicate(timeout=5)
    case(waiter.returncode!=0,'terminated JVM cannot seal a fake receipt')
    owner_anchor.write_bytes(witness.read_bytes())  # test-host G1 version update
    cancelled=subprocess.run(command(journal,witness,'check')+[str(owner_anchor)],
                             capture_output=True,text=True,timeout=20)
    case(cancelled.returncode==0 and 'RECEIPT=STARTED' in cancelled.stdout
         and 'CURRENT_COMPLETED=0' in cancelled.stdout,
         'fresh JVM reports interrupted publication as non-current STARTED')
    retry=subprocess.run(command(journal,witness,'publish')+[str(owner_anchor)],
                         capture_output=True,text=True,timeout=20)
    case(retry.returncode==0 and not claimed(retry.stdout)
         and 'CURRENT_COMPLETED=0' in retry.stdout,
         'new JVM may not republish a cancelled callback')
    case((journal/'test-only-sink-event').read_text()=='published-once\n',
         'cancelled request did not dispatch a second side effect')

    # A second genuine JVM revokes while the first JVM is paused after a
    # callback. The old process may never re-seal an obsolete G1 generation.
    journal,witness=new_scenario('two-process-revoke-vs-ack')
    call(journal,witness,'init')
    owner_anchor=journal.parent/'owner-checkpoint-versioned.txt'
    owner_anchor.write_bytes(witness.read_bytes())
    waiter=spawn_waiter()
    owner_anchor.write_bytes(witness.read_bytes())
    revoked=subprocess.run(command(journal,witness,'revoke')+[str(owner_anchor)],
                           capture_output=True,text=True,timeout=20)
    case(revoked.returncode==0 and 'REVISION=' in revoked.stdout,
         'second independent JVM committed a revoke while publisher paused')
    newer_authority=witness.read_bytes()
    case(int(newer_authority.decode().split('|',1)[0]) >
         int(owner_anchor.read_text().split('|',1)[0]),
         'revoke advances the separately versioned simulated owner checkpoint')
    owner_anchor.write_bytes(newer_authority)
    (journal/'allow-finish').touch()
    resumed_out,resumed_err=waiter.communicate(timeout=20)
    case(waiter.returncode==0 and 'ACK_UNCERTAIN' in resumed_out,
         'old publisher cannot seal after another process revoked: '+resumed_err[-250:])
    checked=subprocess.run(command(journal,witness,'check')+[str(owner_anchor)],
                           capture_output=True,text=True,timeout=20)
    case(checked.returncode==0 and 'REVISION=\n' in checked.stdout
         and 'CURRENT_COMPLETED=0' in checked.stdout,
         'post-revoke state cannot masquerade as current completion')
    retry=subprocess.run(command(journal,witness,'publish')+[str(owner_anchor)],
                         capture_output=True,text=True,timeout=20)
    case(retry.returncode==0 and not claimed(retry.stdout)
         and 'CURRENT_COMPLETED=0' in retry.stdout,
         'third JVM cannot resurrect withdrawn generation')
    case((journal/'test-only-sink-event').read_text()=='published-once\n',
         'revoke race never repeats simulated provider side effect')

    # Reentrant source revocation is a separate JVM callback scenario, not a
    # proof that actual Android/G1 provider callbacks are transaction-safe.
    journal,witness=new_scenario('reentrant-revocation-process')
    call(journal,witness,'init')
    result=call(journal,witness,'reentrant-revoke')
    case('conditional-ack=false' in result and 'CURRENT_COMPLETED=0' in result,
         'same process synchronous callback revokes before conditional receipt seal')
    case('CURRENT_COMPLETED=0' in call(journal,witness,'check'),
         'fresh JVM refuses completion after reentrant revocation')
    case(not claimed(call(journal,witness,'publish')),
         'fresh JVM cannot publish a reentrantly revoked generation')
    print('FUTURE_G1_JVM_PROCESS_RESTART_PROOF_PASS cases='+str(checks))
    print('FUTURE_G1_JOINT_DISK_AUTHORITY_ROLLBACK=VULNERABILITY_REPRODUCED_NOT_FIXED')

def process_strict_owner_proof(temp):
    """Source-API strict owner across JVMs, still rollbackable test files."""
    checks=0
    cls='de.yahya.ai.FutureStrictOwnerProcessChild'
    def case(ok,name):
        nonlocal checks
        if not ok: raise AssertionError('STRICT_PROCESS_OWNER_FAIL: '+name)
        checks+=1
    def cmd(root,action):
        return ['java','-cp',temp,cls,str(root),action]
    def run(root,action):
        result=subprocess.run(cmd(root,action),capture_output=True,text=True,timeout=20)
        case(result.returncode==0,action+' failed: '+result.stderr[-300:])
        return result.stdout
    def scenario(name):
        root=Path(temp)/('strict-'+name)
        root.mkdir()
        return root
    # ACTUAL source publishCertifiedOwnerContextAtomic using a JVM disk owner.
    root=scenario('healthy')
    case('STATE=NONE' in run(root,'init'),'initial state none')
    first=run(root,'publish')
    case('RESULT=COMPLETED' in first and 'CURRENT_COMPLETED=1' in first,
         'strict API owner sealed first genuine JVM dispatch')
    second=run(root,'publish')
    case('RESULT=COMPLETED' in second,'fresh JVM reused sealed receipt')
    case((root/'test-only-strict-sink-event').read_text()=='once\\n',
         'duplicate strict invocation never re-published provider test marker')
    run(root,'revoke')
    case('CURRENT_COMPLETED=0' in run(root,'check'),
         'revoked completed receipt cannot masquerade as current')
    case('RESULT=REJECTED_BEFORE_START' in run(root,'publish'),
         'revoked strict owner rejects fresh publication')
    # Actual hard-kill during callback *while strict owner holds a file lock*.
    root=scenario('cancel')
    run(root,'init')
    def paused(root):
        p=subprocess.Popen(cmd(root,'pause'),stdout=subprocess.PIPE,
                           stderr=subprocess.PIPE,text=True,bufsize=1)
        line=p.stdout.readline().strip()
        case('CALLBACK_REACHED' in line,
             'strict callback began inside owner lock: '+line)
        case('|STARTED|' in (root/'strict-owner.frame').read_text(),
             'strict STARTED frame persisted before callback side effect')
        return p
    waiter=paused(root)
    case((root/'test-only-strict-sink-event').read_text()=='once\\n',
         'callback effect visible before abrupt process death')
    waiter.kill()
    waiter.communicate(timeout=5)
    case(waiter.returncode!=0,'actual OS process canceled before ack')
    case('STATE=STARTED' in run(root,'check'),'fresh JVM recovered STARTED')
    case('RESULT=STARTED_INDETERMINATE' in run(root,'publish'),
         'new process cannot replay canceled strict callback')
    case('CURRENT_COMPLETED=0' in run(root,'check'),
         'interrupted strict receipt is never falsely current COMPLETED')
    case((root/'test-only-strict-sink-event').read_text()=='once\\n',
         'killed strict callback is delivered only once in test model')
    # New independent JVM revoker must wait until publisher's transaction ends.
    root=scenario('two-process-lock-serialization')
    run(root,'init')
    waiter=paused(root)
    revoker=subprocess.Popen(cmd(root,'revoke'),stdout=subprocess.PIPE,
                             stderr=subprocess.PIPE,text=True)
    import time
    time.sleep(0.2)
    case(revoker.poll() is None,'concurrent independent JVM revoke blocked by strict owner lock')
    (root/'allow-finish').touch()
    out,err=waiter.communicate(timeout=20)
    revout,reverr=revoker.communicate(timeout=20)
    case(waiter.returncode==0 and revoker.returncode==0,
         'both strict owner JVM contenders finished: '+err[-100:]+' '+reverr[-100:])
    case('RESULT=COMPLETED' in out or 'RESULT=STARTED_INDETERMINATE' in out,
         'race cannot forge retryable rejection after attempted provider callback')
    case('STATE=STARTED' not in revout and 'GEN=revoked-generation' in revout,
         'revocation serialized after pending owner receipt')
    case('CURRENT_COMPLETED=0' in run(root,'check'),
         'post-revoke strict receipt not current')
    case('RESULT=REJECTED_BEFORE_START' in run(root,'publish'),
         'new JVM does not redispatch revoked generation')
    # Reentrant callback changes source generation before seal.
    root=scenario('reentrant')
    run(root,'init')
    result=run(root,'reentrant')
    case('RESULT=STARTED_INDETERMINATE' in result,
         'strict owner reports uncertain after synchronous reentrant revocation')
    case('CURRENT_COMPLETED=0' in run(root,'check'),
         'reentrant source change prevents current completion')
    case('RESULT=REJECTED_BEFORE_START' in run(root,'publish'),
         'fresh JVM cannot resurrect reentrant-revoked state')
    # Confirm expected unsolved threat: both ordinary rollbackable files are
    # attacker-restorable; preserving source state after a restore is impossible.
    root=scenario('joint-rollback-known-fail')
    run(root,'init')
    old_frame=(root/'strict-owner.frame').read_bytes()
    old_anchor=(root/'strict-owner.versioned-anchor').read_bytes()
    case('RESULT=COMPLETED' in run(root,'publish'),'prior strict publication completed')
    (root/'strict-owner.frame').write_bytes(old_frame)
    (root/'strict-owner.versioned-anchor').write_bytes(old_anchor)
    case('STATE=NONE' in run(root,'check'),
         'KNOWN SECURITY FAIL: joint restore of both files impersonates fresh state')
    replay=run(root,'publish')
    case('RESULT=COMPLETED' in replay and
         (root/'test-only-strict-sink-event').read_text()=='once\\n'*2,
         'KNOWN SECURITY FAIL: joint rollback repeats non-idempotent provider delivery')
    # A REAL independently launched JVM must die after moving a new frame,
    # before sealing the separate versioned authority checkpoint. Recovery
    # must fail closed; cannot decide which side of torn commit to trust.
    root=scenario('torn-reservation-killed-jvm')
    run(root,'init')
    old_anchor=(root/'strict-owner.versioned-anchor').read_bytes()
    crashed=subprocess.run(cmd(root,'fault-reserve'),capture_output=True,text=True,timeout=20)
    case(crashed.returncode==93 and 'STRICT_FAULT_FRAME_REPLACED' in crashed.stdout,
         'actual JVM stopped between reservation frame replacement and authority seal')
    case('|STARTED|' in (root/'strict-owner.frame').read_text()
         and (root/'strict-owner.versioned-anchor').read_bytes()==old_anchor,
         'partial STARTED frame cannot match old separately versioned checkpoint')
    case(not (root/'test-only-strict-sink-event').exists(),
         'reservation fault occurs before invoking any simulated provider')
    claim=subprocess.run(cmd(root,'publish'),capture_output=True,text=True,timeout=20)
    case(claim.returncode!=0 and 'rollback or torn owner seal' in claim.stderr,
         'fresh JVM must not auto-reinitialize or republish after torn reservation')
    case(not (root/'test-only-strict-sink-event').exists(),
         'failed recovery cannot call provider')

    root=scenario('torn-completion-killed-jvm')
    run(root,'init')
    crashed=subprocess.run(cmd(root,'fault-ack'),capture_output=True,text=True,timeout=20)
    case(crashed.returncode==93 and 'STRICT_FAULT_FRAME_REPLACED' in crashed.stdout,
         'actual JVM stopped between COMPLETED frame replacement and authority seal')
    case('|COMPLETED|' in (root/'strict-owner.frame').read_text(),
         'new COMPlETED frame was moved to disk before abrupt process halt')
    case((root/'test-only-strict-sink-event').read_text()=='once\\n',
         'callback made exactly one non-idempotent test side effect before torn ACK')
    retries=[subprocess.Popen(cmd(root,'publish'),stdout=subprocess.PIPE,
                              stderr=subprocess.PIPE,text=True) for _ in range(2)]
    for proc in retries:
        out,err=proc.communicate(timeout=20)
        case(proc.returncode!=0 and 'rollback or torn owner seal' in err,
             'concurrent fresh-JVM claimant cannot auto-recover torn ACK or republish')
    revoker=subprocess.run(cmd(root,'revoke'),capture_output=True,text=True,timeout=20)
    case(revoker.returncode!=0 and 'rollback or torn owner seal' in revoker.stderr,
         'concurrent independent revoker also fails closed on torn commit')
    case((root/'test-only-strict-sink-event').read_text()=='once\\n',
         'neither claimant nor revoker duplicates post-callback side effect')

    root=scenario('torn-revocation-killed-jvm')
    run(root,'init')
    run(root,'publish')
    old_anchor=(root/'strict-owner.versioned-anchor').read_bytes()
    crash_revoke=subprocess.run(cmd(root,'fault-revoke'),capture_output=True,
                                text=True,timeout=20)
    case(crash_revoke.returncode==93,
         'real separate JVM died between revocation frame replacement and anchor seal')
    case('revoked-generation' in (root/'strict-owner.frame').read_text()
         and (root/'strict-owner.versioned-anchor').read_bytes()==old_anchor,
         'revoked frame mismatch never silently accepted as older generation')
    fresh=subprocess.run(cmd(root,'publish'),capture_output=True,text=True,timeout=20)
    case(fresh.returncode!=0 and 'rollback or torn owner seal' in fresh.stderr,
         'restart fails closed on torn revocation rather than resurrecting generation')
    case((root/'test-only-strict-sink-event').read_text()=='once\\n',
         'old completed publication is never repeated after torn revocation')
    # Rename-only staging: a JVM killed BEFORE the atomic frame move leaves
    # an orphan temporary file. The protected pair must remain authoritative.
    root=scenario('orphan-before-rename')
    run(root,'init')
    before_frame=(root/'strict-owner.frame').read_bytes()
    before_anchor=(root/'strict-owner.versioned-anchor').read_bytes()
    orphan=root/'strict-owner-aborted-write.pending'
    orphan.write_text('untrusted-stray-data')
    case(orphan.is_file() and orphan.read_text()=='untrusted-stray-data',
         'uncommitted temporary file exists on restart')
    case('STATE=NONE' in run(root,'check'),
         'orphaned pending frame is ignored; committed owner state remains authoritative')
    case((root/'strict-owner.frame').read_bytes()==before_frame
         and (root/'strict-owner.versioned-anchor').read_bytes()==before_anchor,
         'restart does not adopt or overwrite the committed pair from orphan staging')
    case('RESULT=COMPLETED' in run(root,'publish'),
         'valid complete checkpoint allows one normal dispatch despite orphan staging')
    case((root/'test-only-strict-sink-event').read_text()=='once\\n',
         'orphan staging never duplicates simulated provider delivery')

    # A corrupted versioned anchor must NOT be guessed from its frame. This
    # fixture supports no automatic counter reset or data-loss repair path.
    root=scenario('corrupt-anchor-after-completion')
    run(root,'init')
    run(root,'publish')
    (root/'strict-owner.versioned-anchor').write_text('truncated|untrusted')
    for action in ('check','publish','revoke'):
        refusal=subprocess.run(cmd(root,action),capture_output=True,text=True,timeout=20)
        case(refusal.returncode!=0 and 'rollback or torn owner seal' in refusal.stderr,
             'corrupt authority refuses fresh independent JVM action '+action)
    case((root/'test-only-strict-sink-event').read_text()=='once\\n',
         'corrupt-anchor recovery never repeats external test side effect')
    # Test a real abrupt JVM death AFTER it has persisted a truncated
    # temporary authority checkpoint but BEFORE an atomic rename. Unlike
    # direct corruption, this injects the fault inside the anchor write path.
    # An old intact anchor does not make the newer committed FRAME trusted.
    for action,expected_receipt,prior_delivery in (
        ('fault-partial-anchor-reserve','STARTED',0),
        ('fault-partial-anchor-ack','COMPLETED',1),
        ('fault-partial-anchor-revoke','COMPLETED',1),
    ):
        root=scenario('partial-anchor-stage-'+action)
        run(root,'init')
        if action.endswith('revoke'):
            case('RESULT=COMPLETED' in run(root,'publish'),
                 'pre-revocation provider delivery completed')
        original_anchor=(root/'strict-owner.versioned-anchor').read_bytes()
        crashed=subprocess.run(cmd(root,action),capture_output=True,text=True,timeout=20)
        case(crashed.returncode==94 and 'STRICT_FAULT_PARTIAL_ANCHOR_STAGE' in crashed.stdout,
             'real JVM halted during partially written authority checkpoint '+action)
        pending=list(root.glob('strict-owner-*.pending'))
        case(len(pending)==1 and 0<len(pending[0].read_bytes())<65,
             'hard-killed JVM leaves a truncated, fsynced anchor staging file '+action)
        frame_text=(root/'strict-owner.frame').read_text()
        expected_anchor=original_anchor.decode()
        if action.endswith('ack'):
            # Its STARTED reservation was sealed earlier in the same JVM;
            # only the later COMPLETED anchor write was interrupted.
            previous=frame_text.split('|')
            previous[0]=str(int(previous[0])-1)
            previous[2]='STARTED'
            previous_text='|'.join(previous)
            expected_anchor=previous[0]+'|'+hashlib.sha256(
                previous_text.encode('utf-8')).hexdigest()
        case((root/'strict-owner.versioned-anchor').read_text()==expected_anchor,
             'atomic rename preserves last fully sealed owner revision '+action)
        case('|'+expected_receipt+'|' in (root/'strict-owner.frame').read_text(),
             'source state frame moved before partial authority checkpoint '+action)
        contenders=[subprocess.Popen(cmd(root,op),stdout=subprocess.PIPE,
                                     stderr=subprocess.PIPE,text=True)
                    for op in ('publish','publish','revoke')]
        for contender in contenders:
            output,error=contender.communicate(timeout=20)
            case(contender.returncode!=0 and 'rollback or torn owner seal' in error,
                 'parallel fresh-JVM action refuses partial checkpoint '+action)
        deliveries=(root/'test-only-strict-sink-event')
        case((deliveries.read_text().count('once\\n') if deliveries.exists() else 0)
             ==prior_delivery,
             'partial checkpoint cannot cause a second provider delivery '+action)
        case(len(list(root.glob('strict-owner-*.pending')))==1,
             'no new contender attempts to repair, consume or overwrite orphan seal '+action)

    # The opposite crash point matters too: if a JVM dies while staging
    # the state FRAME (before atomic rename), the original frame + anchor
    # must still agree. Resume is allowed for a never-started reservation,
    # but forbidden to repeat a callback after durable STARTED.
    for action,stage in (
        ('fault-partial-frame-reserve','reserve'),
        ('fault-partial-frame-ack','ack'),
        ('fault-partial-frame-revoke','revoke'),
    ):
        root=scenario('partial-frame-stage-'+stage)
        run(root,'init')
        if stage=='revoke':
            run(root,'publish')
        old_frame=(root/'strict-owner.frame').read_bytes()
        old_anchor=(root/'strict-owner.versioned-anchor').read_bytes()
        crashed=subprocess.run(cmd(root,action),capture_output=True,text=True,timeout=20)
        case(crashed.returncode==95 and 'STRICT_FAULT_PARTIAL_FRAME_STAGE' in crashed.stdout,
             'actual JVM killed while staging incomplete owner frame '+stage)
        pending=list(root.glob('strict-owner-*.pending'))
        case(len(pending)==1 and 0<len(pending[0].read_bytes())<20,
             'partial owner frame was force-written but not renamed '+stage)
        after_frame=(root/'strict-owner.frame').read_bytes()
        after_anchor=(root/'strict-owner.versioned-anchor').read_bytes()
        if stage=='reserve':
            case(after_frame==old_frame and after_anchor==old_anchor,
                 'pre-reservation crash leaves consistent NONE state')
            contenders=[subprocess.Popen(cmd(root,'publish'),stdout=subprocess.PIPE,
                                        stderr=subprocess.PIPE,text=True) for _ in range(2)]
            completed=0
            for contender in contenders:
                stdout,stderr=contender.communicate(timeout=20)
                case(contender.returncode==0 and 'RESULT=COMPLETED' in stdout,
                     'post-preclaim-crash contender safely observes completion')
                completed+=('RESULT=COMPLETED' in stdout)
            case(completed==2 and
                 (root/'test-only-strict-sink-event').read_text()=='once\\n',
                 'two fresh JVMs share one first real delivery after safe pre-STARTED crash')
        elif stage=='ack':
            case(after_anchor!=old_anchor and after_frame!=old_frame and
                 '|STARTED|' in after_frame.decode(),
                 'ack-stage crash preserves already sealed STARTED reservation')
            results=[subprocess.run(cmd(root,'publish'),capture_output=True,text=True,timeout=20)
                     for _ in range(2)]
            for result in results:
                case(result.returncode==0 and 'RESULT=STARTED_INDETERMINATE' in result.stdout,
                     'fresh JVM never retries callback after interrupted ACK staging')
            case((root/'test-only-strict-sink-event').read_text()=='once\\n',
                 'interrupted ACK frame staging cannot duplicate provider delivery')
        else:
            case(after_frame==old_frame and after_anchor==old_anchor,
                 'pre-rename revocation crash retains prior sealed state (not false revoke)')
            case('CURRENT_COMPLETED=1' in run(root,'check'),
                 'interrupted revoke is not falsely reported as committed')
            case('RESULT=COMPLETED' in run(root,'publish'),
                 'pre-commit revoke crash reuses existing receipt without callback')
            case((root/'test-only-strict-sink-event').read_text()=='once\\n',
                 'previously completed delivery remains exactly once')
            run(root,'revoke')
            case('CURRENT_COMPLETED=0' in run(root,'check'),
                 'later explicit retry of revocation commits under owner lock')

    # Non-hardware directory durability model. The Java fixture now forces
    # parent directory metadata AFTER every atomic rename. A real child JVM
    # aborts immediately BEFORE that force, at each frame and anchor boundary.
    # Normal process death usually leaves the rename visible, but does NOT
    # establish the semantics of power failure on an Android filesystem.
    for surface in ('frame','anchor'):
        for stage in ('reserve','ack','revoke'):
            action='fault-dir-'+surface+'-'+stage
            root=scenario('directory-force-'+surface+'-'+stage)
            run(root,'init')
            if stage=='revoke':
                case('RESULT=COMPLETED' in run(root,'publish'),
                     'setup persisted receipt before directory revoke crash')
            old_frame=(root/'strict-owner.frame').read_bytes()
            old_anchor=(root/'strict-owner.versioned-anchor').read_bytes()
            crashed=subprocess.run(cmd(root,action),capture_output=True,text=True,timeout=20)
            case(crashed.returncode==96 and
                 'STRICT_FAULT_RENAMED_BEFORE_DIR_FORCE:strict-owner.'+(
                     'frame' if surface=='frame' else 'versioned-anchor') in crashed.stdout,
                 'actual JVM died after rename before parent directory force '+action)
            frame=(root/'strict-owner.frame').read_bytes()
            anchor=(root/'strict-owner.versioned-anchor').read_bytes()
            if surface=='frame':
                case(frame!=old_frame and (stage=='ack' or anchor==old_anchor),
                     'frame rename was visible at process-abort checkpoint '+action)
                # A completed ACK's prior STARTED anchor was sealed inside
                # the same aborted process, so pre-action anchor comparison
                # is intentionally not meaningful on that path.
                readback=subprocess.run(cmd(root,'check'),capture_output=True,
                                        text=True,timeout=20)
                case(readback.returncode!=0 and
                     'rollback or torn owner seal' in readback.stderr,
                     'frame-only rename mismatch fails closed on restart '+action)
                challengers=[subprocess.Popen(cmd(root,op),stdout=subprocess.PIPE,
                              stderr=subprocess.PIPE,text=True)
                             for op in ('publish','revoke')]
                for contender in challengers:
                    out,err=contender.communicate(timeout=20)
                    case(contender.returncode!=0 and 'rollback or torn owner seal' in err,
                         'competing JVM may not repair frame-only rename '+action)
            else:
                case(frame!=old_frame and anchor!=old_anchor,
                     'anchor rename is visible after ordinary JVM death '+action)
                outcome=run(root,'check')
                expected={'reserve':'STATE=STARTED',
                          'ack':'STATE=COMPLETED',
                          'revoke':'GEN=revoked-generation'}[stage]
                case(expected in outcome,
                     'full pair remains consistent after ordinary JVM abort '+action)
                fresh=run(root,'publish')
                expected_result={'reserve':'RESULT=STARTED_INDETERMINATE',
                                 'ack':'RESULT=COMPLETED',
                                 'revoke':'RESULT=REJECTED_BEFORE_START'}[stage]
                case(expected_result in fresh,
                     'independent JVM honors sealed receipt after crash '+action)
                # Explicitly model a power loss discarding the most recent
                # renamed anchor directory entry, NOT a claimed real cut.
                anchor_path=root/'strict-owner.versioned-anchor'
                previous_frame=frame.decode().split('|')
                previous_frame[0]=str(int(previous_frame[0])-1)
                if stage=='ack':
                    previous_frame[2]='STARTED'
                if stage=='revoke':
                    previous_frame[1]='fixture-source-snapshot-1'
                prior='|'.join(previous_frame)
                previous_anchor=previous_frame[0]+'|'+hashlib.sha256(
                    prior.encode('utf-8')).hexdigest()
                anchor_path.write_text(previous_anchor)
                readback=subprocess.run(cmd(root,'check'),capture_output=True,
                                        text=True,timeout=20)
                case(readback.returncode!=0 and
                     'rollback or torn owner seal' in readback.stderr,
                     'simulated lost anchor rename is rejected '+action)
                blocked=subprocess.run(cmd(root,'publish'),capture_output=True,
                                       text=True,timeout=20)
                case(blocked.returncode!=0 and 'rollback or torn owner seal' in blocked.stderr,
                     'simulated lost anchor is not repaired/re-dispatched '+action)
            events=root/'test-only-strict-sink-event'
            case((events.read_text().count('once\\n') if events.exists() else 0)
                 ==(0 if stage=='reserve' else 1),
                 'crash/rollback model does not double-deliver '+action)
    # Failure boundary, NOT a security PASS: if a simulated power cut loses
    # BOTH revocation directory entries together, ordinary file-only owners
    # cannot distinguish the old valid completed state from new authority.
    root=scenario('lost-entire-revoke-rename-pair-model')
    run(root,'init')
    run(root,'publish')
    completed_frame=(root/'strict-owner.frame').read_bytes()
    completed_anchor=(root/'strict-owner.versioned-anchor').read_bytes()
    run(root,'revoke')
    case('CURRENT_COMPLETED=0' in run(root,'check'),
         'source revocation was observed before simulated power loss')
    (root/'strict-owner.frame').write_bytes(completed_frame)
    (root/'strict-owner.versioned-anchor').write_bytes(completed_anchor)
    case('CURRENT_COMPLETED=1' in run(root,'check'),
         'KNOWN SECURITY FAIL: joint lost revocation renames resurrect old receipt')
    case('RESULT=COMPLETED' in run(root,'publish'),
         'restored old receipt cannot prove revocation happened')
    case((root/'test-only-strict-sink-event').read_text()=='once\\n',
         'joint restored receipt is reused without a second callback on this ID')
    print('FUTURE_G1_DIRECTORY_FORCE_JVM_ONLY=NOT_ANDROID_POWER_CUT_PROOF')
    print('FUTURE_G1_JOINT_LOST_REVOCATION=KNOWN_SECURITY_FAIL')

    print('FUTURE_G1_STRICT_OWNER_CROSS_PROCESS_PROOF_PASS cases='+str(checks))
    print('FUTURE_G1_STRICT_OWNER_JOINT_ROLLBACK=VULNERABILITY_REPRODUCED_NOT_FIXED')

def process_high_water_proof(temp):
    """Two REAL JVM claimants vs independent JVM epoch writer; test-double ONLY."""
    import time
    checks=0
    cls='de.yahya.ai.FutureHighWaterProcessChild'
    def case(condition,why):
        nonlocal checks
        if not condition: raise AssertionError('HIGH_WATER_PROCESS_FAIL: '+why)
        checks+=1
    def cmd(root,witness,action,broken=False):
        return ['java']+(['-Dceline.fixture.skipInnerHighWater=true'] if broken else [])+[
            '-cp',temp,cls,str(root),str(witness),action]
    def call(root,witness,action,broken=False):
        p=subprocess.run(cmd(root,witness,action,broken),capture_output=True,
                         text=True,timeout=25)
        case(p.returncode==0,'JVM '+action+' failed '+p.stderr[-350:])
        return p.stdout
    def scenario(label):
        root=Path(temp)/('epoch-race-'+label)
        witness=Path(temp)/('separate-high-water-'+label)/'epoch.txt'
        root.mkdir()
        case('INIT_OWNER_AND_WITNESS' in call(root,witness,'init'),'independent fixture provisioned')
        return root,witness
    for seeded in (False,True):
        for mode in ('advance','erase','corrupt'):
            root,witness=scenario(('completed' if seeded else 'new')+'-'+mode)
            if seeded:
                case('RESULT=COMPLETED' in call(root,witness,'claim'),
                     'initial completed receipt sealed before independent epoch race')
            before_frame=(root/'strict-owner.frame').read_bytes()
            before_anchor=(root/'strict-owner.versioned-anchor').read_bytes()
            previous_events=(root/'test-only-high-water-sink').read_text().count('sent\\n') \
                if (root/'test-only-high-water-sink').exists() else 0
            contenders=[subprocess.Popen(cmd(root,witness,'pause-claim'),
                        stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
                        for _ in range(2)]
            try:
                deadline=time.monotonic()+12
                while len(list(root.glob('ready-*')))<2 and time.monotonic()<deadline:
                    if any(p.poll() is not None for p in contenders):break
                    time.sleep(.02)
                case(len(list(root.glob('ready-*')))==2,
                     'both independent JVMs reached AFTER preflight BEFORE owner lock')
                case('WITNESS_MUTATED='+mode in call(root,witness,mode),
                     'third JVM independently mutated simulated epoch authority')
            finally:
                (root/'release-high-water-race').touch()
            outcomes=[p.communicate(timeout=25) for p in contenders]
            case(all(p.returncode==0 for p in contenders),
                 'two independent JVM claimants returned after epoch mutation')
            case(all('RESULT=COMPLETED' not in output[0] for output in outcomes),
                 'old COMPLETED receipt cannot be claimed current after epoch change')
            case(all(('RESULT=STARTED_INDETERMINATE' in output[0]
                      or 'RESULT=REJECTED_BEFORE_START' in output[0])
                     for output in outcomes),
                 'race returns fail-closed non-success outcomes')
            case((root/'strict-owner.frame').read_bytes()==before_frame and
                 (root/'strict-owner.versioned-anchor').read_bytes()==before_anchor,
                 'external witness race cannot reserve or overwrite local receipts')
            after_events=(root/'test-only-high-water-sink').read_text().count('sent\\n') \
                if (root/'test-only-high-water-sink').exists() else 0
            case(after_events==previous_events,
                 'no plaintext context dispatched after independent witness change')
            case('RESULT=COMPLETED' not in call(root,witness,'claim'),
                 'fourth fresh JVM cannot revive stale certified intent')
    # A truly defective owner that skips its inside-lock check can publish
    # AFTER public preflight. The outer check reports uncertainty too late:
    # side effects already happened. This is a targeted negative control.
    root,witness=scenario('deliberately-broken-inner-check')
    paused=subprocess.Popen(cmd(root,witness,'pause-claim',True),stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE,text=True)
    try:
        deadline=time.monotonic()+10
        while not list(root.glob('ready-*')) and time.monotonic()<deadline:
            time.sleep(.02)
        case(bool(list(root.glob('ready-*'))),'broken owner reached post-preflight barrier')
        call(root,witness,'advance')
    finally:(root/'release-high-water-race').touch()
    brokenout,brokenerr=paused.communicate(timeout=20)
    case(paused.returncode==0,'negative control JVM did not terminate normally '+brokenerr[-150:])
    case('RESULT=COMPLETED' not in brokenout and
         (root/'test-only-high-water-sink').read_text().count('sent\\n')==1,
         'negative-control: public postflight alone cannot undo leaked callback')
    print('FUTURE_G1_HIGH_WATER_PROCESS_BROKEN_INNER_GUARD=STALE_CALLBACK_REPRODUCED')
    # Explicit joint rollback FAIL remains when the supposedly independent
    # authority is also ordinary restorable filesystem data.
    root,witness=scenario('all-files-rollback-known-failure')
    old_frame=(root/'strict-owner.frame').read_bytes()
    old_anchor=(root/'strict-owner.versioned-anchor').read_bytes()
    old_witness=witness.read_bytes()
    case('RESULT=COMPLETED' in call(root,witness,'claim'),
         'original authorized publication completed')
    call(root,witness,'advance')
    case('RESULT=COMPLETED' not in call(root,witness,'claim'),
         'advanced witness rejects old memory snapshot')
    (root/'strict-owner.frame').write_bytes(old_frame)
    (root/'strict-owner.versioned-anchor').write_bytes(old_anchor)
    witness.write_bytes(old_witness)
    case('RESULT=COMPLETED' in call(root,witness,'claim'),
         'KNOWN SECURITY FAIL: restoring ordinary witness + owner files enables stale delivery')
    case((root/'test-only-high-water-sink').read_text().count('sent\\n')==2,
         'KNOWN SECURITY FAIL: joint rollback actually duplicated provider marker')
    print('FUTURE_G1_JOINT_WITNESS_AND_SNAPSHOT_ROLLBACK=KNOWN_SECURITY_FAIL')
    print('FUTURE_G1_HIGH_WATER_JVM_PROCESS_PROOF_PASS cases='+str(checks))


def process_high_water_locked_callback_proof(temp):
    """Real interprocess OS-lock serialization WHILE provider callback is held.

    DiskOwner and ordinary witness are JVM test doubles; this deliberately
    does NOT establish Android/provider exactly-once or anti-rollback.
    """
    import time
    cls='de.yahya.ai.FutureHighWaterProcessChild'
    checks=0
    def case(condition, description):
        nonlocal checks
        if not condition:
            raise AssertionError('HIGH_WATER_CALLBACK_LOCK_FAIL: '+description)
        checks+=1
    def cmd(root,witness,action):
        return ['java','-cp',temp,cls,str(root),str(witness),action]
    def invoke(root,witness,action):
        p=subprocess.run(cmd(root,witness,action),capture_output=True,text=True,timeout=20)
        case(p.returncode==0,action+' child process exited: '+p.stderr[-450:])
        return p.stdout
    def until_file(file, process, title):
        deadline=time.monotonic()+12
        while not file.exists() and time.monotonic()<deadline:
            if process.poll() is not None: break
            time.sleep(.015)
        case(file.is_file(),title+' actually reached in child JVM')
    root=Path(temp)/'locked-callback-source-os-lock'
    witness=Path(temp)/'independent-locked-callback-authority'/'epoch.txt'
    root.mkdir()
    case('INIT_OWNER_AND_WITNESS' in invoke(root,witness,'init'), 'new OS-lock fixture')
    old=witness.read_bytes()
    claimant=subprocess.Popen(cmd(root,witness,'hold-claim'),stdout=subprocess.PIPE,
                             stderr=subprocess.PIPE,text=True)
    writer=None
    try:
        until_file(root/'callback-inside-source-lock',claimant,
                   'published callback is executing inside owner source lock')
        case(not (root/'test-only-high-water-sink').exists(),
             'callback has not written external marker before hold is released')
        writer=subprocess.Popen(cmd(root,witness,'advance-serialized'),
                                stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
        until_file(root/'writer-requested',writer,
                   'independent JVM announced source-locked generation advance')
        # Absence is observed over a deliberate interval, not just one tick.
        time.sleep(.30)
        case(writer.poll() is None,'independent generation writer is still blocked')
        case(not (root/'writer-finished').exists(),
             'generation writer did not complete while callback owns OS lock')
        case(witness.read_bytes()==old,
             'witness cannot change before callback owner transaction releases')
    finally:
        (root/'release-callback').touch()
    claim_out,claim_err=claimant.communicate(timeout=25)
    writer_out,writer_err=writer.communicate(timeout=25)
    case(claimant.returncode==0,'claimant finished after callback release '+claim_err[-300:])
    case(writer.returncode==0,'source-serialized writer finished '+writer_err[-300:])
    case('WITNESS_MUTATED=advance-serialized' in writer_out
         and (root/'writer-finished').exists(),'OS-lock writer eventually advanced generation')
    case(witness.read_bytes()!=old,'writer update became visible only after owner released')
    case('STATE=COMPLETED' in claim_out,
         'callback transaction committed completed receipt before generation advance')
    case((root/'test-only-high-water-sink').read_text()=='sent\\n',
         'exactly one local simulated sink event, no duplicate callback')
    stale=invoke(root,witness,'claim')
    case('RESULT=COMPLETED' not in stale,
         'stale completed receipt cannot be accepted after writer advances')
    case((root/'test-only-high-water-sink').read_text()=='sent\\n',
         'fresh post-advance JVM did not redeliver stale context')
    # Deliberate negative control proves why the *independent authority writer*
    # MUST participate in the source lock rather than just the claimants.
    root=Path(temp)/'unsafe-unlocked-authority-negative-control'
    witness=Path(temp)/'unsafe-unlocked-authority'/'epoch.txt'
    root.mkdir()
    invoke(root,witness,'init')
    held=subprocess.Popen(cmd(root,witness,'hold-claim'),stdout=subprocess.PIPE,
                          stderr=subprocess.PIPE,text=True)
    try:
        until_file(root/'callback-inside-source-lock',held,
                   'negative-control callback held inside source OS lock')
        case('WITNESS_MUTATED=unsafe-unlocked' in invoke(root,witness,
                'advance-unlocked-testonly'),
             'deliberately broken independent authority writer bypassed source lock')
        case(witness.read_text().startswith('future-owner-generation-2'),
             'unsafe writer genuinely changed witness before callback finished')
    finally:
        (root/'release-callback').touch()
    negative_out,negative_err=held.communicate(timeout=25)
    case(held.returncode==0,'negative-control claimant finished '+negative_err[-300:])
    case('RESULT=STARTED_INDETERMINATE' in negative_out,
         'post-callback attestation refuses false success after unsafe witness change')
    case((root/'test-only-high-water-sink').read_text()=='sent\\n',
         'KNOWN unsafe-owner exposure: postflight cannot undo callback plaintext')
    case('STATE=STARTED' in negative_out,
         'unsafe external generation update cannot be automatically acknowledged')
    case('RESULT=COMPLETED' not in invoke(root,witness,'claim'),
         'unsafe owner cannot automatically retry stale interrupted delivery')
    print('FUTURE_G1_INDEPENDENT_CALLBACK_WRITER_OS_LOCK_PROOF_PASS cases='+str(checks))
    print('FUTURE_G1_UNLOCKED_WITNESS_WRITER=KNOWN_UNSAFE_CALLBACK_EXPOSURE')


def process_high_water_killed_callback_proof(temp):
    """Independent JVM killed under source OS lock, before/after sink marker.

    A provider marker stands in for a non-idempotent external action. Killing
    its JVM cannot establish that a real provider did (or did not) deliver.
    An uncertain STARTED intent must not auto-retry under any witness state.
    """
    import time
    cls='de.yahya.ai.FutureHighWaterProcessChild'
    checks=0
    def case(ok,reason):
        nonlocal checks
        if not ok: raise AssertionError('HIGH_WATER_CRASH_LOCK_FAIL: '+reason)
        checks+=1
    def cmd(root,witness,action):
        return ['java','-cp',temp,cls,str(root),str(witness),action]
    def invoke(root,witness,action):
        process=subprocess.run(cmd(root,witness,action),text=True,
                capture_output=True,timeout=20)
        case(process.returncode==0,action+' JVM failed '+process.stderr[-350:])
        return process.stdout
    def ready(file,child,label):
        deadline=time.monotonic()+12
        while not file.is_file() and time.monotonic()<deadline:
            if child.poll() is not None: break
            time.sleep(.015)
        case(file.is_file(),label+' reached by independent JVM')
    for after_sink in (False,True):
        label='after-sink' if after_sink else 'before-sink'
        root=Path(temp)/('killed-locked-callback-'+label)
        witness=Path(temp)/('independent-kill-authority-'+label)/'epoch.txt'
        root.mkdir()
        case('INIT_OWNER_AND_WITNESS' in invoke(root,witness,'init'),
             label+' owner initialized')
        claimant=subprocess.Popen(cmd(root,witness,
                                     'hold-after-sink' if after_sink else 'hold-claim'),
                stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
        writer=None
        try:
            ready(root/('callback-after-sink-source-lock' if after_sink else
                        'callback-inside-source-lock'),claimant,label+' callback held')
            case((root/'test-only-high-water-sink').is_file()==after_sink,
                 label+' sink event observed exactly at required crash boundary')
            writer=subprocess.Popen(cmd(root,witness,'advance-serialized'),
                    stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
            ready(root/'writer-requested',writer,label+' independent writer waiting')
            time.sleep(.20)
            case(writer.poll() is None and not (root/'writer-finished').exists(),
                 label+' writer blocked by still-live callback OS lock')
        finally:
            # A true OS kill, not a Java throw caught by the publisher.
            claimant.kill()
        killed_out,killed_err=claimant.communicate(timeout=20)
        case(claimant.returncode!=0,label+' caller actually died in external process')
        writer_out,writer_err=writer.communicate(timeout=20)
        case(writer.returncode==0 and 'WITNESS_MUTATED=advance-serialized' in writer_out,
             label+' writer acquired kernel-released OS lock '+writer_err[-200:])
        case((root/'writer-finished').is_file() and
             witness.read_text().startswith('future-owner-generation-2'),
             label+' generation advanced after process death')
        frame=(root/'strict-owner.frame').read_text()
        case('|STARTED|' in frame and '|COMPLETED|' not in frame,
             label+' ambiguous durable intent never falsely sealed COMPLETED')
        sink=root/'test-only-high-water-sink'
        expected='sent\\n' if after_sink else ''
        case((sink.read_text() if sink.exists() else '')==expected,
             label+' external non-idempotent marker preserved exactly')
        retry=invoke(root,witness,'claim')
        case('RESULT=COMPLETED' not in retry,
             label+' fresh JVM cannot silently reuse interrupted receipt')
        case((sink.read_text() if sink.exists() else '')==expected,
             label+' no second provider marker on restart')
    print('FUTURE_G1_KILLED_CALLBACK_OS_LOCK_RECOVERY_PASS cases='+str(checks))
    print('FUTURE_G1_POST_SINK_PROCESS_KILL=EXTERNAL_DELIVERY_UNCERTAIN_NO_RETRY')

# FUTURE-ONLY: real independent-JVM selected-ID correction versus revoke fixture.
# This disk-state test double is NOT authoritative G1 persistence or Android proof.
memory_id_process_harness = r'''
package de.yahya.ai;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class FutureMemoryIdProcessChild {
    static final long NOW=1900000000000L;
    static final String PRIVATE_ID="cat-private", PUBLIC_ID="cat-public";
    static final class Snapshot {
        final int revision;
        final String status;
        Snapshot(int revision,String status){this.revision=revision;this.status=status;}
    }
    static Snapshot read(Path file) throws Exception {
        String[] bits=Files.readString(file).trim().split("\\|",-1);
        if(bits.length!=2 || !bits[0].matches("[1-9][0-9]*")
                || !List.of("ACTIVE","REVOKED","CORRECTED").contains(bits[1]))
            throw new IllegalStateException("untrusted selected-ID state");
        return new Snapshot(Integer.parseInt(bits[0]),bits[1]);
    }
    static void store(Path file,String value) throws Exception {
        Path tmp=file.resolveSibling(file.getFileName()+".tmp."+ProcessHandle.current().pid());
        Files.writeString(tmp,value,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        try(FileChannel fd=FileChannel.open(tmp,StandardOpenOption.WRITE)){fd.force(true);}
        Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    static String suggested(Snapshot state) {
        var scope=CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE;
        var privateScope=CelineFutureMemoryRecall.Privacy.LOCAL_SENSITIVE;
        var publicCat=new CelineFutureMemoryRecall.Candidate(PUBLIC_ID,
            "Meine Katze Luna schwarz","",scope,true,true,NOW-100,0,0.6);
        var privateCat=new CelineFutureMemoryRecall.Candidate(PRIVATE_ID,
            "Meine Katze Luna grau","",privateScope,false,true,NOW-100,0,0.5);
        var records=new ArrayList<CelineFutureMemoryRecall.Candidate>();
        records.add(publicCat);records.add(privateCat);
        var retired=new HashSet<String>();
        if(!state.status.equals("ACTIVE"))retired.add(PRIVATE_ID);
        if(state.status.equals("CORRECTED"))records.add(
            new CelineFutureMemoryRecall.Candidate("cat-private-replacement",
                "Meine Katze Luna weiß",PRIVATE_ID,privateScope,false,true,NOW-10,0,0.8));
        return CelineFutureMemoryRecall.unambiguousCorrectionTarget(
            "Meine Katze Luna",records,retired,NOW);
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("root action");
        Path root=Path.of(args[0]),state=root.resolve("memory-id-state.txt"),
                witness=root.resolve("memory-id-highwater.txt");
        String action=args[1];
        Files.createDirectories(root);
        Path lockPath=root.resolve("memory-id-owner.lock");
        try(FileChannel channel=FileChannel.open(lockPath,
                StandardOpenOption.CREATE,StandardOpenOption.WRITE);
            FileLock lock=channel.lock()) {
            if(action.equals("init")) {
                if(Files.exists(state)||Files.exists(witness))
                    throw new IllegalStateException("already initialized");
                // Simulated independent witness first: a crash here denies future
                // use rather than accepting an old active selected-ID snapshot.
                store(witness,"1");
                store(state,"1|ACTIVE");
                System.out.println("STATE=ACTIVE;REV=1;SUGGEST="+suggested(read(state)));return;
            }
            // Test-only independent-JVM crash injection under the exact-ID
            // owner lock. These are simulated file-corruption boundaries,
            // NOT genuine Android/fsync power-loss guarantees.
            if(action.startsWith("hold-fault-witness-")) {
                Files.createFile(root.resolve("witness-owner-held"));
                long deadline=System.nanoTime()+12_000_000_000L;
                while(!Files.exists(root.resolve("release-witness-owner"))
                        && System.nanoTime()<deadline)Thread.sleep(10);
                if(!Files.exists(root.resolve("release-witness-owner")))
                    throw new IllegalStateException("witness-owner release timeout");
                if(action.equals("hold-fault-witness-torn-temp")) {
                    Path pending=witness.resolveSibling(witness.getFileName()
                        +".tmp."+ProcessHandle.current().pid());
                    Files.writeString(pending,"2|INCOMPLETE",StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW);
                    try(FileChannel fd=FileChannel.open(pending,StandardOpenOption.WRITE)) {
                        fd.force(true);
                    }
                    Runtime.getRuntime().halt(91);
                }
                if(action.equals("hold-fault-witness-torn-commit")) {
                    store(witness,"2|INCOMPLETE");
                    Runtime.getRuntime().halt(92);
                }
                if(action.equals("hold-fault-witness-advanced")) {
                    store(witness,"2");
                    Runtime.getRuntime().halt(93);
                }
                throw new IllegalArgumentException("unrecognized witness fault");
            }
            // A process may die before an atomic witness OR state rename.
            // Matching old visible files cannot certify an unresolved temp.
            // Never silently delete or recover either half of a transaction.
            for(String pendingPattern:List.of("memory-id-highwater.txt.tmp.*",
                    "memory-id-state.txt.tmp.*")) {
                try(DirectoryStream<Path> pending=Files.newDirectoryStream(root,
                        pendingPattern)) {
                    if(pending.iterator().hasNext())
                        throw new IllegalStateException(
                            "untrusted selected-ID witness/state pending transaction");
                }
            }
            Snapshot current=read(state); // Fail closed if missing, partial or corrupt.
            // Toy high-water authority: separate ordinary local file, not G1
            // antirollback hardware. Match exact bytes, not parsed/truncated input.
            String certified=Files.exists(witness)?Files.readString(witness):"";
            if(!String.valueOf(current.revision).equals(certified))
                throw new IllegalStateException("untrusted selected-ID witness");
            if(action.equals("inspect")) {
                System.out.println("STATE="+current.status+";REV="+current.revision
                    +";SUGGEST="+suggested(current));return;
            }
            if(action.equals("suggest-without-id")) {
                System.out.println("NO_ID_SUGGEST="+suggested(current));return;
            }
            if(action.equals("hold-revoke") || action.equals("hold-correct")) {
                Files.createFile(root.resolve("first-source-owner-held"));
                long deadline=System.nanoTime()+12_000_000_000L;
                while(!Files.exists(root.resolve("release-first-source-owner"))
                        && System.nanoTime()<deadline)Thread.sleep(10);
                if(!Files.exists(root.resolve("release-first-source-owner")))
                    throw new IllegalStateException("owner-release timeout");
            }
            boolean revoke=action.equals("revoke")||action.equals("hold-revoke")
                ||action.equals("fault-revoke-after-commit")
                ||action.equals("fault-revoke-after-witness");
            boolean correct=action.equals("correct")||action.equals("hold-correct");
            if(!revoke&&!correct)throw new IllegalArgumentException("bad selected-ID action");
            // Both callers hold an explicit old revision 1 and EXACT source ID;
            // no natural-language suggestion authorizes this mutation.
            if(!Boolean.getBoolean("celine.fixture.skipRevisionCheck")
                    && (current.revision!=1 || !current.status.equals("ACTIVE"))) {
                System.out.println("RESULT=STALE_EXPECTED_REVISION;ACTUAL="+current.revision);return;
            }
            String updated=revoke?"REVOKED":"CORRECTED";
            // Witness advance BEFORE snapshot write: partial commit fails closed.
            store(witness,String.valueOf(current.revision+1));
            if(action.equals("fault-revoke-after-witness")) {
                System.out.flush();Runtime.getRuntime().halt(97);
            }
            store(state,(current.revision+1)+"|"+updated);
            if(action.equals("fault-revoke-after-commit")) {
                System.out.flush();Runtime.getRuntime().halt(96);
            }
            System.out.println("RESULT=APPLIED;SELECTED_ID="+PRIVATE_ID
                +";STATUS="+updated+";REV="+(current.revision+1));
        }
    }
}
'''

def process_memory_id_revoke_correction_proof(temp):
    """Different JVMs compete on the exact private memory ID and revision.

    A Java file lock and a plain-text toy snapshot are not authentic G1
    source-authority, permission, durable tombstone or power-loss protection.
    """
    import time
    tests=0
    def case(condition,description):
        nonlocal tests
        if not condition: raise AssertionError('MEMORY_ID_PROCESS_FAIL: '+description)
        tests+=1
    def cmd(root,action,broken=False):
        return ['java']+(['-Dceline.fixture.skipRevisionCheck=true'] if broken else [])+[
            '-cp',temp,'de.yahya.ai.FutureMemoryIdProcessChild',str(root),action]
    def run(root,action,broken=False):
        proc=subprocess.run(cmd(root,action,broken),text=True,capture_output=True,timeout=20)
        case(proc.returncode==0,'child exited cleanly '+action+' '+proc.stderr[-200:])
        return proc.stdout.strip()
    for first,second in (('hold-revoke','correct'),('hold-correct','revoke')):
        label=first+'-before-'+second
        root=Path(temp)/('memory-id-'+label)
        root.mkdir()
        case('STATE=ACTIVE;REV=1;SUGGEST=' in run(root,'init'),
             label+' initialized two exact IDs')
        case(run(root,'suggest-without-id')=='NO_ID_SUGGEST=',
             label+' ambiguous private/public correction demands selected ID')
        holder=subprocess.Popen(cmd(root,first),stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE,text=True)
        contender=None
        try:
            deadline=time.monotonic()+10
            while not (root/'first-source-owner-held').exists() and time.monotonic()<deadline:
                if holder.poll() is not None:break
                time.sleep(.01)
            case((root/'first-source-owner-held').exists(),
                 label+' first JVM acquired source file lock')
            contender=subprocess.Popen(cmd(root,second),stdout=subprocess.PIPE,
                                       stderr=subprocess.PIPE,text=True)
            time.sleep(.22)
            case(contender.poll() is None,
                 label+' second JVM blocked pending first owner release')
            case((root/'memory-id-state.txt').read_text()=='1|ACTIVE',
                 label+' source unchanged while selected-ID owner lock held')
        finally:
            (root/'release-first-source-owner').touch()
        firstout,firsterr=holder.communicate(timeout=20)
        secondout,seconderr=contender.communicate(timeout=20)
        case(holder.returncode==0 and 'RESULT=APPLIED' in firstout,
             label+' first JVM committed '+firsterr[-200:])
        case(contender.returncode==0 and 'RESULT=STALE_EXPECTED_REVISION' in secondout,
             label+' stale second JVM rejected '+seconderr[-200:])
        status='REVOKED' if first=='hold-revoke' else 'CORRECTED'
        state=run(root,'inspect')
        case('STATE='+status+';REV=2;' in state,
             label+' final authoritative toy snapshot has one revision only')
        case('SUGGEST=cat-private' not in state,
             label+' old selected private ID never suggested after correction/revocation')
        case('RESULT=STALE_EXPECTED_REVISION' in run(root,second),
             label+' rebooted independent JVM cannot replay stale selected-ID action')
    root=Path(temp)/'memory-id-process-killed-after-revocation'
    root.mkdir()
    run(root,'init')
    killed=subprocess.run(cmd(root,'fault-revoke-after-commit'),
                          text=True,capture_output=True,timeout=20)
    case(killed.returncode==96,'actual JVM terminated after persisted revoke')
    case((root/'memory-id-state.txt').read_text()=='2|REVOKED',
         'committed selected-ID tombstone survives ordinary JVM termination')
    case('RESULT=STALE_EXPECTED_REVISION' in run(root,'correct'),
         'after process death stale correction cannot restore revoked ID')
    # A real faulty revision guard permits a second independent JVM to rewrite
    # the already-revoked memory. This negative control must remain observed FAIL.
    case('RESULT=APPLIED' in run(root,'correct',True),
         'KNOWN UNSAFE NEGATIVE CONTROL: skipping revision CAS resurrects a correction')
    root=Path(temp)/'memory-id-malformed-snapshot'
    root.mkdir()
    run(root,'init')
    corrupt='1|ACTIVE\n2|CORRECTED'
    (root/'memory-id-state.txt').write_text(corrupt)
    for command in ('correct','revoke','suggest-without-id'):
        stopped=subprocess.run(cmd(root,command),text=True,capture_output=True,timeout=20)
        case(stopped.returncode!=0 and 'untrusted selected-ID state' in stopped.stderr,
             'malformed owner snapshot rejects '+command+' in independent JVM')
        case((root/'memory-id-state.txt').read_text()==corrupt,
             'malformed owner state not implicitly repaired after '+command)
    print('FUTURE_G1_MEMORY_ID_PROCESS_SERIALIZATION_PASS cases='+str(tests))
    print('FUTURE_G1_MEMORY_ID_SOURCE_OWNER=SIMULATED_FILE_LOCK_NOT_ANDROID')
    print('FUTURE_G1_MEMORY_ID_REVISION_CAS_BYPASS=KNOWN_UNSAFE_NEGATIVE_CONTROL')


# FUTURE-ONLY: a *simulated* independent high-water witness for exact-ID mutations.
# Ordinary local files are NOT a hardware monotonic counter; joint rollback is unsafe.
def process_memory_id_witness_proof(temp):
    tests = 0
    def case(ok, description):
        nonlocal tests
        if not ok: raise AssertionError('MEMORY_ID_WITNESS_FAIL: '+description)
        tests += 1
    def run(root, action):
        return subprocess.run(['java','-cp',temp,
            'de.yahya.ai.FutureMemoryIdProcessChild',str(root),action],
            text=True,capture_output=True,timeout=20)
    def valid(root, action):
        result=run(root,action)
        case(result.returncode==0,'successful '+action+': '+result.stderr[-200:])
        return result.stdout.strip()
    def rejected(root, action, expected='untrusted selected-ID witness'):
        result=run(root,action)
        case(result.returncode!=0 and expected in result.stderr,
            'reject '+action+' with witness mismatch/missing/corruption: '+result.stderr[-200:])

    root=Path(temp)/'memory-id-witness-rollback'
    root.mkdir()
    witness=root/'memory-id-highwater.txt'
    state=root/'memory-id-state.txt'
    valid(root,'init')
    case(witness.exists() and witness.read_text()=='1','initial independently tracked revision')
    case('RESULT=APPLIED' in valid(root,'revoke'),'exact ID revoke applied once')
    case(witness.read_text()=='2','witness advanced before publication is accepted')
    case(state.read_text()=='2|REVOKED','snapshot updated to source revision')
    # Restore ONLY the ordinary owner snapshot; external witness stays at revision 2.
    state.write_text('1|ACTIVE')
    for action in ('inspect','correct','revoke','suggest-without-id'):
        rejected(root,action)
        case(state.read_text()=='1|ACTIVE' and witness.read_text()=='2',
             'no stale snapshot repair or stale ID mutation after '+action)
    state.write_text('2|REVOKED')  # Manual test fixture repair, not an app recovery API.
    case('STATE=REVOKED;REV=2' in valid(root,'inspect'),
         'externally revalidated aligned state available for read')
    case('STALE_EXPECTED_REVISION' in valid(root,'correct'),
         'stale selected-ID correction still refused after restart')

    for mode in ('missing','malformed','decreased','advanced'):
        r=Path(temp)/('memory-id-witness-'+mode)
        r.mkdir()
        valid(r,'init')
        w=r/'memory-id-highwater.txt'
        before=(r/'memory-id-state.txt').read_text()
        if mode=='missing': w.unlink()
        elif mode=='malformed': w.write_text('2|invalid')
        elif mode=='decreased': w.write_text('0')
        else: w.write_text('2')
        for action in ('inspect','revoke','correct'):
            rejected(r,action)
            case((r/'memory-id-state.txt').read_text()==before,
                 mode+' leaves exact memory ID unchanged after '+action)

    root=Path(temp)/'memory-id-witness-interrupted-commit'
    root.mkdir()
    valid(root,'init')
    stopped=run(root,'fault-revoke-after-witness')
    case(stopped.returncode==97,'real process killed after witness advance before snapshot write')
    case((root/'memory-id-highwater.txt').read_text()=='2',
         'witness retains advance across abrupt process exit')
    case((root/'memory-id-state.txt').read_text()=='1|ACTIVE',
         'snapshot remains old after killed process')
    for action in ('inspect','correct','revoke','suggest-without-id'):
        rejected(root,action)
    # The limitation is deliberately witnessed, not suppressed or called solved.
    root=Path(temp)/'memory-id-witness-joint-rollback-negative-control'
    root.mkdir()
    valid(root,'init')
    valid(root,'revoke')
    (root/'memory-id-state.txt').write_text('1|ACTIVE')
    (root/'memory-id-highwater.txt').write_text('1')
    case('RESULT=APPLIED' in valid(root,'correct'),
         'KNOWN UNSAFE NEGATIVE CONTROL: resetting all ordinary files replays old ID')
    print('FUTURE_G1_MEMORY_ID_WITNESS_JVM_PASS cases='+str(tests))
    print('FUTURE_G1_MEMORY_ID_JOINT_ROLLBACK=KNOWN_SECURITY_FAIL_UNFIXED')
    print('FUTURE_G1_MEMORY_ID_WITNESS=UNTRUSTED_FILE_SIMULATION_NOT_ANDROID')

# FUTURE-ONLY: real process deaths at three simulated witness-commit cuts.
# The source owner must NOT ignore a left-behind incomplete temp transaction,
# even when the old state and old witness still appear to match. A plain
# witness file cannot guarantee real anti-rollback or power-loss durability.
def process_memory_id_torn_witness_proof(temp):
    import time
    count=0
    def case(ok, description):
        nonlocal count
        if not ok: raise AssertionError('MEMORY_ID_TORN_WITNESS_FAIL: '+description)
        count+=1
    def cmd(root, action):
        return ['java','-cp',temp,
            'de.yahya.ai.FutureMemoryIdProcessChild',str(root),action]
    def invoke(root,action):
        return subprocess.run(cmd(root,action),capture_output=True,text=True,timeout=20)

    for action,halt_status in (
            ('hold-fault-witness-torn-temp',91),
            ('hold-fault-witness-torn-commit',92),
            ('hold-fault-witness-advanced',93)):
        root=Path(temp)/('memory-id-interrupted-'+action)
        root.mkdir()
        init=invoke(root,'init')
        case(init.returncode==0,'independent source initialized '+action)
        hold=subprocess.Popen(cmd(root,action),stdout=subprocess.PIPE,
                              stderr=subprocess.PIPE,text=True)
        contender=None
        try:
            deadline=time.monotonic()+10
            while not (root/'witness-owner-held').exists() and time.monotonic()<deadline:
                if hold.poll() is not None:break
                time.sleep(.01)
            case((root/'witness-owner-held').exists(),
                 'first independent JVM holds owner lock '+action)
            contender=subprocess.Popen(cmd(root,'inspect'),stdout=subprocess.PIPE,
                                       stderr=subprocess.PIPE,text=True)
            time.sleep(.15)
            case(contender.poll() is None,
                 'second independent JVM remains blocked on source lock '+action)
        finally:
            (root/'release-witness-owner').touch()
        out,err=hold.communicate(timeout=20)
        case(hold.returncode==halt_status,
             'actual first JVM death at '+action+': '+err[-200:])
        second_out,second_err=contender.communicate(timeout=20)
        case(contender.returncode!=0 and
             'untrusted selected-ID witness' in second_err,
             'concurrent reader fails closed after killed first JVM '+action
             +': '+second_out[-150:]+' '+second_err[-150:])
        state=(root/'memory-id-state.txt')
        witness=(root/'memory-id-highwater.txt')
        case(state.read_text()=='1|ACTIVE',
             'uncommitted selected-ID snapshot stays at revision one '+action)
        if action.endswith('torn-temp'):
            case(witness.read_text()=='1',
                 'pre-rename witness remains old but pending transaction is suspicious')
            case(len(list(root.glob('memory-id-highwater.txt.tmp.*')))==1,
                 'aborted witness write leaves authentic pending temp file')
        elif action.endswith('torn-commit'):
            case(witness.read_text()=='2|INCOMPLETE',
                 'incomplete replacement witness is corrupt and never trusted')
        else:
            case(witness.read_text()=='2',
                 'advanced witness survives while selected-ID snapshot stays old')
        for restart_action in ('inspect','suggest-without-id','correct','revoke'):
            check=invoke(root,restart_action)
            case(check.returncode!=0 and
                 'untrusted selected-ID witness' in check.stderr,
                 'independent restarted JVM refuses '+restart_action+' after '+action
                 +': '+check.stdout[-120:]+' '+check.stderr[-120:])
            case(state.read_text()=='1|ACTIVE',
                 'no auto-repair, revoke or correction after '+restart_action+' '+action)
    # An abandoned owner-state rename also makes a supposedly complete source
    # pair untrustworthy: fail closed instead of silently discarding temp data.
    root=Path(temp)/'memory-id-unresolved-snapshot-temp'
    root.mkdir()
    case(invoke(root,'init').returncode==0,'owner-state temp fixture initialized')
    orphan=root/'memory-id-state.txt.tmp.aborted'
    orphan.write_text('2|REVOKED|TRUNCATED')
    for restart_action in ('inspect','suggest-without-id','correct','revoke'):
        stopped=invoke(root,restart_action)
        case(stopped.returncode!=0 and
             'untrusted selected-ID witness' in stopped.stderr,
             'orphan state temp blocks separate JVM '+restart_action)
        case((root/'memory-id-state.txt').read_text()=='1|ACTIVE',
             'orphan state temp cannot auto-repair source '+restart_action)
    # Negative control: ordinary temp files have no external monotonic custody.
    # An unauthorized actor who removes the orphan can defeat this local guard.
    orphan.unlink()
    replay=invoke(root,'correct')
    case(replay.returncode==0 and 'RESULT=APPLIED' in replay.stdout,
         'KNOWN UNSAFE: local temp erasure bypasses simulated crash marker')
    print('FUTURE_G1_MEMORY_ID_ORPHAN_ERASURE=KNOWN_SECURITY_FAIL_UNFIXED')
    print('FUTURE_G1_MEMORY_ID_TORN_WITNESS_2JVM_PASS cases='+str(count))
    print('FUTURE_G1_MEMORY_ID_TORN_WITNESS=TEST_FIXTURE_NOT_POWER_LOSS_PROOF')
    print('FUTURE_G1_MEMORY_ID_RECOVERY=MANUAL_OWNER_VERIFICATION_REQUIRED')

# Two distinct operating-system JVM processes. The signer keeps its key in an
# operator-owned model directory; the verifier pins ONLY that independent
# directory's key, never a consumer-owned replacement key. This is NOT Android
# trusted custody: the test operator could still roll back the entire directory.
owner_process_harness = r'''
package de.yahya.ai;
import java.nio.file.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.util.*;
public final class FutureOwnerAttestationProcess {
    static final String REVOCATION_ID="cat-private";
    static final String HASH="a".repeat(64);
    static final String CHALLENGE="owner-independent-nonce-0001";
    static String[] state(Path owner) throws Exception {
        String[] s=Files.readString(owner.resolve("owner-state.txt")).trim().split("\\|",-1);
        if(s.length!=4 || !s[0].matches("[0-9]+") || !s[1].matches("[A-Za-z0-9_-]{16,64}")
                || !"REVOKED".equals(s[2]) || !("0".equals(s[3]) || "1".equals(s[3])))
            throw new IllegalStateException("untrusted owner state");
        return s;
    }
    static PublicKey pinned(Path owner) throws Exception {
        byte[] bytes=Files.readAllBytes(owner.resolve("owner.pub"));
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(bytes));
    }
    static void durableReplace(Path owner,String content) throws Exception {
        Path target=owner.resolve("owner-state.txt");
        Path tmp=owner.resolve("owner-state.txt.tmp");
        Files.writeString(tmp,content,StandardCharsets.UTF_8);
        try(FileChannel ch=FileChannel.open(tmp,StandardOpenOption.WRITE)){ch.force(true);}
        Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        // Model-only file force; this does NOT establish Android power-cut proof.
    }
    static class Owner implements CelineFutureMemoryRecall.OneShotRecoveryOwner {
        final Path owner;
        Owner(Path dir){owner=dir;}
        public CelineFutureMemoryRecall.RecoveryOwnerSnapshot snapshot(String id) {
            try {
                if(!REVOCATION_ID.equals(id)) return null;
                String[] s=state(owner);
                return new CelineFutureMemoryRecall.RecoveryOwnerSnapshot(
                    Long.parseLong(s[0]),s[1],pinned(owner),"REVOKED".equals(s[2]));
            } catch(Exception ex){return null;}
        }
        public boolean claimIfCurrent(String id,long rev,String nonce,byte[] exactKey) {
            try(FileChannel ch=FileChannel.open(owner.resolve("owner-claim.lock"),
                    StandardOpenOption.CREATE,StandardOpenOption.WRITE);
                FileLock ignored=ch.lock()) {
                String[] s=state(owner);
                boolean ok=REVOCATION_ID.equals(id)&&Long.parseLong(s[0])==rev
                    && nonce.equals(s[1])&&"REVOKED".equals(s[2])&&"0".equals(s[3])
                    && MessageDigest.isEqual(pinned(owner).getEncoded(),exactKey);
                if(!ok)return false;
                durableReplace(owner,rev+"|"+nonce+"|REVOKED|1\n");
                return true;
            } catch(Exception ex){return false;}
        }
    }
    public static void main(String[] args) throws Exception {
        String cmd=args[0];
        Path owner=Paths.get(args[1]);
        if("issue".equals(cmd)) {
            Files.createDirectories(owner);
            KeyPair kp=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            Files.write(owner.resolve("owner.pub"),kp.getPublic().getEncoded());
            Files.write(owner.resolve("owner.private"),kp.getPrivate().getEncoded());
            durableReplace(owner,"2|"+CHALLENGE+"|REVOKED|0\n");
            Signature sig=Signature.getInstance("Ed25519");
            sig.initSign(kp.getPrivate());
            sig.update(CelineFutureMemoryRecall.revokedRecoveryPayload(
                REVOCATION_ID,2,HASH,CHALLENGE));
            System.out.println("TOKEN="+Base64.getEncoder().encodeToString(sig.sign()));
            return;
        }
        if("issue-forgery".equals(cmd)) {
            KeyPair kp=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            Files.createDirectories(owner);
            Files.write(owner.resolve("owner.pub"),kp.getPublic().getEncoded());
            Signature sig=Signature.getInstance("Ed25519");
            sig.initSign(kp.getPrivate());
            sig.update(CelineFutureMemoryRecall.revokedRecoveryPayload(
                REVOCATION_ID,2,HASH,CHALLENGE));
            System.out.println("TOKEN="+Base64.getEncoder().encodeToString(sig.sign()));
            return;
        }
        if("swap-path-after-preflight".equals(cmd)) {
            // The verifier previously resolved a normal direct owner path.
            // A separate JVM now moves it and installs a symlink to a forged
            // owner. No direct-path preflight is repeated at claim time.
            Path attacker=Paths.get(args[2]);
            Path consumer=Paths.get(args[3]);
            if(!Files.exists(consumer.resolve("preflight-done.flag")))
                throw new IllegalStateException("no observed verifier preflight");
            Files.move(owner,owner.resolveSibling(owner.getFileName()+".saved"));
            Files.createSymbolicLink(owner,attacker.toAbsolutePath());
            Files.writeString(consumer.resolve("resume-claim.flag"),"go\n");
            System.out.println("ATTACK=OWNER_PATH_REDIRECTED_AFTER_PREFLIGHT");
            return;
        }
        if("swap-after-preflight".equals(cmd)) {
            // An independent attacker JVM races the file-backed authority.
            // Only disposable test directories are touched, never real G1.
            Path adversary=Paths.get(args[2]);
            Path consumer=Paths.get(args[3]);
            if(!Files.exists(consumer.resolve("preflight-done.flag")))
                throw new IllegalStateException("no observed verifier preflight");
            Files.write(owner.resolve("owner.pub"),
                    Files.readAllBytes(adversary.resolve("owner.pub")));
            durableReplace(owner,Files.readString(adversary.resolve("owner-state.txt")));
            Files.writeString(consumer.resolve("resume-claim.flag"),"go\n");
            System.out.println("ATTACK=REPLACED_AFTER_PREFLIGHT");
            return;
        }
        if("verify-bound".equals(cmd) || "verify-bound-paused".equals(cmd)) {
            // Model-only: a pre-provisioned app/owner-owned registry must be
            // selected OUTSIDE the caller's authority. Supplying this path as
            // an argv value is only a test seam, NOT a secure Android design.
            try {
                Path registry=Paths.get(args[8]);
                Path selected=owner.toRealPath();
                Path registryCanonical=registry.toRealPath();
                // Refuse symlink/path indirection and mutable entry aliases in
                // this isolated test fixture. This is only a best-effort path
                // check: not an atomic filesystem or Android trust boundary.
                boolean directPaths=owner.toAbsolutePath().normalize().equals(selected)
                    && registry.toAbsolutePath().normalize().equals(registryCanonical)
                    && !Files.isSymbolicLink(selected.resolve("owner.pub"))
                    && !Files.isSymbolicLink(selected.resolve("owner-state.txt"))
                    && !Files.isSymbolicLink(registry.resolve("owner-realpath.txt"))
                    && !Files.isSymbolicLink(registry.resolve("pinned-owner.pub"))
                    && !Files.isSymbolicLink(registry.resolve("owner-epoch.txt"));
                if(!directPaths) {
                    System.out.println("RESULT=BLOCKED");
                    return;
                }
                String registered=Files.readString(registry.resolve("owner-realpath.txt"))
                    .trim();
                Path expected=Paths.get(registered).toRealPath();
                byte[] pinnedKey=Files.readAllBytes(registry.resolve("pinned-owner.pub"));
                byte[] actualKey=Files.readAllBytes(selected.resolve("owner.pub"));
                String[] external=Files.readString(registry.resolve("owner-epoch.txt"))
                    .trim().split("\\|",-1);
                String[] onDisk=state(selected);
                boolean identityPinned=selected.equals(expected)
                    && pinnedKey.length>0 && MessageDigest.isEqual(pinnedKey,actualKey);
                boolean revisionPinned=external.length==2
                    && external[0].matches("[0-9]+")
                    && external[1].matches("[A-Za-z0-9_-]{16,64}")
                    && external[0].equals(args[4])&&external[1].equals(args[6])
                    && external[0].equals(onDisk[0])&&external[1].equals(onDisk[1]);
                if(!identityPinned || !revisionPinned) {
                    System.out.println("RESULT=BLOCKED");
                    return;
                }
            } catch(Exception ex) {
                System.out.println("RESULT=BLOCKED");
                return;
            }
            if("verify-bound-paused".equals(cmd)) {
                // Deterministic test interlock, not a production pause/permission.
                // The owner path and pinned bytes passed the check above; an
                // independent process now gets a chance to replace them.
                Path consumer=Paths.get(args[2]);
                Files.writeString(consumer.resolve("preflight-done.flag"),"done\n");
                long deadline=System.nanoTime()+8_000_000_000L;
                while(!Files.exists(consumer.resolve("resume-claim.flag"))
                        && System.nanoTime()<deadline) Thread.sleep(10L);
                if(!Files.exists(consumer.resolve("resume-claim.flag"))) {
                    System.out.println("RESULT=BLOCKED");
                    return;
                }
            }
            // The same owner API below still has no authentic hardware trust
            // root or rollback-resistant revision. This proves a boundary,
            // not production authentication.
        }
        if("verify".equals(cmd) || "verify-bound".equals(cmd)
                || "verify-bound-paused".equals(cmd)) {
            // args[2] is attacker/consumer-owned. It is intentionally NEVER
            // consulted as authority or as the Ed25519 pinned-key source.
            Path consumer=Paths.get(args[2]);
            if(!Files.isDirectory(consumer))throw new IllegalArgumentException("consumer missing");
            byte[] signature;
            try{signature=Base64.getDecoder().decode(args[7]);}
            catch(IllegalArgumentException ex){signature=new byte[0];}
            var result=CelineFutureMemoryRecall.attestRevokedOrphanOnce(
                REVOCATION_ID,Long.parseLong(args[4]),args[3],args[5],args[6],
                signature,new Owner(owner));
            System.out.println("RESULT="+result.name());
            return;
        }
        throw new IllegalArgumentException(cmd);
    }
}
'''

def process_independent_owner_attestation_proof(temp):
    import base64
    from pathlib import Path
    from tempfile import TemporaryDirectory
    import concurrent.futures
    count=0
    def case(ok,label):
        nonlocal count
        if not ok: raise AssertionError('independent owner attest FAIL: '+label)
        count+=1
    with TemporaryDirectory(prefix='future_owner_process_') as t:
        root=Path(t)
        consumer=root/'consumer-controlled'; consumer.mkdir()
        trusted=root/'trusted-owner-model'
        invalid=root/'untrusted-attacker'
        command=['java','-cp',temp,'de.yahya.ai.FutureOwnerAttestationProcess']
        def issue(dir,mode='issue'):
            p=subprocess.run(command+[mode,str(dir)],capture_output=True,text=True,timeout=15)
            case(p.returncode==0 and p.stdout.startswith('TOKEN='),mode+' signer JVM completed')
            return p.stdout.strip().split('=',1)[1]
        def verify(token,source='REVOKED',rev='2',orphan='a'*64,
                   challenge='owner-independent-nonce-0001',owner=None):
            p=subprocess.run(command+['verify',str(owner or trusted),str(consumer),
                        source,rev,orphan,challenge,token],capture_output=True,
                        text=True,timeout=15)
            case(p.returncode==0 and 'RESULT=' in p.stdout,'independent verifier JVM launched')
            return p.stdout.strip().split('RESULT=',1)[1]
        token=issue(trusted)
        spoof=issue(invalid,'issue-forgery')
        # The consumer can replace its key, but not the key pinned by the owner.
        (consumer/'owner.pub').write_bytes((invalid/'owner.pub').read_bytes())
        case(verify(spoof)=='BLOCKED','untrusted consumer key substitution rejected')
        case(verify(token,source='ACTIVE')=='BLOCKED','signed revoked proof cannot restore ACTIVE')
        case(verify(token,rev='3')=='BLOCKED','different source revision blocked')
        case(verify(token,orphan='b'*64)=='BLOCKED','different orphan fingerprint blocked')
        case(verify(token,challenge='stale-independent-nonce-999')=='BLOCKED',
             'changed challenge rejects token reuse')
        case(verify('AAAA')=='BLOCKED','malformed signature blocked')
        case(verify(token,owner=root/'missing-owner')=='BLOCKED',
             'no independent owner means closed')
        case(verify(token)=='QUARANTINE_REVOKED_ONLY',
             'one genuine signer JVM token consumed by independent verifier')
        case((trusted/'owner-state.txt').read_text().strip().endswith('|1'),
             'the independent owner permanently marks claim as spent in model')
        case(verify(token)=='BLOCKED','new verifier JVM refuses identical replay')
        # Known limitation: the independent *file-backed* owner is itself
        # rollbackable. Rolling back its consumed-claim checkpoint permits
        # replay. This must remain a visible security FAIL, not a PASS claim.
        (trusted/'owner-state.txt').write_text(
            '2|owner-independent-nonce-0001|REVOKED|0\n')
        case(verify(token)=='QUARANTINE_REVOKED_ONLY',
             'KNOWN UNSAFE: jointly rolled-back owner file reaccepts signed token')
        case((trusted/'owner-state.txt').read_text().strip().endswith('|1'),
             'replayed token is consumed again in untrusted file-backed model')
        print('FUTURE_G1_JOINT_OWNER_ROLLBACK=KNOWN_SECURITY_FAIL_UNFIXED')
        # A fresh owner challenge simulates a revision advance outside consumer scope.
        newer=root/'advanced-owner'; newer_token=issue(newer)
        (newer/'owner-state.txt').write_text('3|owner-independent-nonce-0002|REVOKED|0\n')
        case(verify(newer_token,owner=newer)=='BLOCKED',
             'advanced monotonic revision refuses old token')
        # Simultaneous independently launched consumers: owner CAS permits one.
        race=root/'race-owner'; race_token=issue(race)
        def race_verify(_):
            return verify(race_token,owner=race)
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            replies=list(pool.map(race_verify,range(2)))
        case(sorted(replies)==['BLOCKED','QUARANTINE_REVOKED_ONLY'],
             'two concurrent verifier JVMs cannot both consume the one-use token')
        case((race/'owner-state.txt').read_text().strip().endswith('|1'),
             'atomic owner claim persists across JVM race')
        print('FUTURE_G1_OWNER_ATTESTATION_2JVM_PASS cases='+str(count))
        print('FUTURE_G1_EXTERNAL_OWNER_CUSTODY=TEST_MODEL_ONLY')
        print('FUTURE_G1_REAL_HARDWARE_MONOTONIC_ROLLBACK=UNPROVEN')


def process_caller_selected_owner_adversary_proof(temp):
    """Two actual JVMs: a self-appointed issuer can fool a caller-chosen owner.

    The opt-in registry here is a *fixture* representing a pinned external trust
    root, NOT a secure Android authority; caller substitution of that registry
    itself must be reported as a known security failure, not silently passed.
    """
    from pathlib import Path
    from tempfile import TemporaryDirectory
    count=0
    def case(ok,label):
        nonlocal count
        if not ok: raise AssertionError('caller-owner adversary FAIL: '+label)
        count+=1
    with TemporaryDirectory(prefix='future_forged_owner_') as t:
        root=Path(t)
        trusted=root/'registered-owner'; attacker=root/'caller-selected-owner'
        consumer=root/'consumer'; consumer.mkdir()
        cmd=['java','-cp',temp,'de.yahya.ai.FutureOwnerAttestationProcess']
        def issue(owner):
            p=subprocess.run(cmd+['issue',str(owner)],capture_output=True,text=True,timeout=15)
            case(p.returncode==0 and p.stdout.startswith('TOKEN='),
                 'independent issuer JVM generated signed token')
            return p.stdout.strip().split('=',1)[1]
        def verify(mode,owner,token,registry=None,revision='2',
                   nonce='owner-independent-nonce-0001',status='REVOKED'):
            args=cmd+[mode,str(owner),str(consumer),status,revision,'a'*64,nonce,token]
            if registry is not None: args.append(str(registry))
            p=subprocess.run(args,capture_output=True,text=True,timeout=15)
            case(p.returncode==0 and p.stdout.startswith('RESULT='),
                 'independent verifier JVM returns decision: '+mode)
            return p.stdout.strip().split('RESULT=',1)[1]
        def pin(path,owner,rev='2',nonce='owner-independent-nonce-0001'):
            path.mkdir()
            (path/'owner-realpath.txt').write_text(str(owner.resolve())+'\n')
            (path/'pinned-owner.pub').write_bytes((owner/'owner.pub').read_bytes())
            (path/'owner-epoch.txt').write_text(rev+'|'+nonce+'\n')
        legit=issue(trusted)
        forged=issue(attacker)  # distinct JVM / unrelated signing key
        # NEGATIVE CONTROL: current owner-object API trusts its caller. There is
        # no product-owned registry here, so a self-appointed owner is accepted.
        case(verify('verify',attacker,forged)=='QUARANTINE_REVOKED_ONLY',
             'KNOWN UNSAFE: caller-selected bogus owner is accepted unbound')
        print('FUTURE_G1_CALLER_SELECTED_OWNER=KNOWN_SECURITY_FAIL_UNFIXED')
        rootpin=root/'operator-pinned-model'
        pin(rootpin,trusted)
        # Independent pinned identity *fixture* rejects the exact forged actor,
        # even when its own key and signed nonce form a self-consistent pair.
        before_bound=(attacker/'owner-state.txt').read_bytes()
        case(verify('verify-bound',attacker,forged,rootpin)=='BLOCKED',
             'pinned registered owner path prevents attacker key substitution')
        case((attacker/'owner-state.txt').read_bytes()==before_bound,
             'untrusted owner claim was not modified by bound rejection')
        case(verify('verify-bound',trusted,legit,rootpin)=='QUARANTINE_REVOKED_ONLY',
             'genuine pinned registered owner can quarantine revoked ID once')
        case(verify('verify-bound',trusted,legit,rootpin)=='BLOCKED',
             'restart of verifier cannot replay previously consumed challenge')
        case(verify('verify-bound',trusted,legit,root/'absent-registry')=='BLOCKED',
             'missing independent registration fails closed')
        case(verify('verify-bound',trusted,legit,rootpin,revision='3')=='BLOCKED',
             'changed monotonic revision denied')
        case(verify('verify-bound',trusted,legit,rootpin,nonce='stale-nonce-00000000')=='BLOCKED',
             'changed challenge denied')
        case(verify('verify-bound',trusted,legit,rootpin,status='ACTIVE')=='BLOCKED',
             'attestation can never restore a revoked record to ACTIVE')
        # The model registry itself is only an ordinary file fixture: giving
        # the caller power to choose the registry reintroduces the vulnerability.
        fresh=root/'fresh-attacker'; forged_fresh=issue(fresh)
        fakepin=root/'attacker-chosen-registry'; pin(fakepin,fresh)
        case(verify('verify-bound',fresh,forged_fresh,fakepin)=='QUARANTINE_REVOKED_ONLY',
             'KNOWN UNSAFE: caller-substituted registry self-authorizes')
        print('FUTURE_G1_CALLER_CHOSEN_TRUST_ROOT=KNOWN_SECURITY_FAIL_UNFIXED')
        # Registered root tamper before a fresh transaction must reject it.
        honest2=root/'another-registered-owner'; signed2=issue(honest2)
        fixed=root/'different-operator-pin'; pin(fixed,honest2)
        (fixed/'pinned-owner.pub').write_bytes((fresh/'owner.pub').read_bytes())
        case(verify('verify-bound',honest2,signed2,fixed)=='BLOCKED',
             'pinned-key mismatch blocked, independent of signed payload')
        (fixed/'pinned-owner.pub').unlink()
        case(verify('verify-bound',honest2,signed2,fixed)=='BLOCKED',
             'missing registered public key blocked')
        print('FUTURE_G1_ADVERSARIAL_OWNER_JVM_PASS cases='+str(count))
        print('FUTURE_G1_GENUINE_EXTERNAL_OWNER_IDENTITY_NONCE=UNPROVEN')

def process_owner_root_matrix_proof(temp):
    """Independent JVM pinned-root matrix; ordinary-file trust is only a mock.

    A test PASS means this Java *fixture* rejected the individual attack.
    Rewinding every ordinary authority file is explicitly still vulnerable.
    """
    from pathlib import Path
    from tempfile import TemporaryDirectory
    count = 0
    def case(ok,label):
        nonlocal count
        if not ok: raise AssertionError('owner-root matrix FAIL: '+label)
        count += 1
    with TemporaryDirectory(prefix='future_owner_root_matrix_') as t:
        root=Path(t)
        command=['java','-cp',temp,'de.yahya.ai.FutureOwnerAttestationProcess']
        consumer=root/'consumer'; consumer.mkdir()
        def issue(label):
            owner=root/(label+'-owner')
            p=subprocess.run(command+['issue',str(owner)],capture_output=True,
                             text=True,timeout=15)
            case(p.returncode==0 and p.stdout.startswith('TOKEN='),
                 label+': new JVM signs revocation')
            token=p.stdout.strip().split('=',1)[1]
            registry=root/(label+'-root'); registry.mkdir()
            (registry/'owner-realpath.txt').write_text(str(owner.resolve())+'\n')
            (registry/'pinned-owner.pub').write_bytes((owner/'owner.pub').read_bytes())
            (registry/'owner-epoch.txt').write_text('2|owner-independent-nonce-0001\n')
            return owner,registry,token
        def verify(owner,registry,token,rev='2',nonce='owner-independent-nonce-0001',
                   status='REVOKED'):
            p=subprocess.run(command+['verify-bound',str(owner),str(consumer),status,
                rev,'a'*64,nonce,token,str(registry)],capture_output=True,
                text=True,timeout=15)
            case(p.returncode==0 and p.stdout.startswith('RESULT='),
                 'independent consumer JVM produced decision')
            return p.stdout.strip().split('RESULT=',1)[1]
        def blocked(label,make_attack):
            owner,registry,token=issue(label)
            make_attack(owner,registry)
            before=(owner/'owner-state.txt').read_bytes()
            case(verify(owner,registry,token)=='BLOCKED',label+': fail closed')
            case((owner/'owner-state.txt').read_bytes()==before,
                 label+': did not consume rejected challenge')
        owner,registry,token=issue('control')
        case(verify(owner,registry,token)=='QUARANTINE_REVOKED_ONLY',
             'pinned direct owner can quarantine a revoked ID once')
        case(verify(owner,registry,token)=='BLOCKED',
             'second JVM refuses previously consumed challenge')
        blocked('pinned-key-changed',lambda o,r:(r/'pinned-owner.pub').write_bytes(b'not-ed25519'))
        blocked('owner-key-changed',lambda o,r:(o/'owner.pub').write_bytes(b'attacker-key'))
        blocked('epoch-removed',lambda o,r:(r/'owner-epoch.txt').unlink())
        blocked('key-removed',lambda o,r:(r/'pinned-owner.pub').unlink())
        blocked('registered-owner-missing',lambda o,r:(r/'owner-realpath.txt').unlink())
        blocked('epoch-malformed',lambda o,r:(r/'owner-epoch.txt').write_text('not-a-version\n'))
        # Path substitutions are invalid even when their symlink target currently
        # contains the right bytes. Using toRealPath() alone misses that fact.
        def alias_owner(o,r):
            alias=root/'owner-alias'; alias.symlink_to(o, target_is_directory=True)
            return alias
        owner,registry,token=issue('owner-alias')
        alias=alias_owner(owner,registry)
        before=(owner/'owner-state.txt').read_bytes()
        case(verify(alias,registry,token)=='BLOCKED',
             'symbolic owner alias denied even if it points at trusted owner')
        case((owner/'owner-state.txt').read_bytes()==before,
             'alias cannot consume owner claim')
        owner,registry,token=issue('registry-alias')
        regalias=root/'registry-alias-link'; regalias.symlink_to(registry,target_is_directory=True)
        case(verify(owner,regalias,token)=='BLOCKED',
             'symbolic registry root forbidden despite matching bytes')
        def symlink_entry(entry):
            def attack(o,r):
                original=(r/entry).read_bytes()
                target=root/(o.name+'-'+entry+'-external')
                target.write_bytes(original)
                (r/entry).unlink()
                (r/entry).symlink_to(target)
            return attack
        blocked('key-symlink',symlink_entry('pinned-owner.pub'))
        blocked('epoch-symlink',symlink_entry('owner-epoch.txt'))
        blocked('owner-path-symlink',symlink_entry('owner-realpath.txt'))
        blocked('owner-state-symlink',lambda o,r: (
            (o/'owner-state.txt').rename(o/'state-original.txt'),
            (o/'owner-state.txt').symlink_to(o/'state-original.txt')))
        blocked('owner-public-symlink',lambda o,r: (
            (o/'owner.pub').rename(o/'public-original.pub'),
            (o/'owner.pub').symlink_to(o/'public-original.pub')))
        owner,registry,token=issue('nonce-rotation')
        (registry/'owner-epoch.txt').write_text('2|rotated-independent-nonce-999\n')
        case(verify(owner,registry,token)=='BLOCKED',
             'registry nonce rotated ahead of owner denies stale signed challenge')
        (owner/'owner-state.txt').write_text('2|rotated-independent-nonce-999|REVOKED|0\n')
        case(verify(owner,registry,token,nonce='rotated-independent-nonce-999')=='BLOCKED',
             'independent owner nonce rotation invalidates old signature')
        owner,registry,token=issue('epoch-advance')
        (owner/'owner-state.txt').write_text('3|new-owner-nonce-00000001|REVOKED|0\n')
        (registry/'owner-epoch.txt').write_text('3|new-owner-nonce-00000001\n')
        case(verify(owner,registry,token,rev='3',nonce='new-owner-nonce-00000001')=='BLOCKED',
             'epoch advance cannot revive old revision-2 signature')
        # IMPORTANT NEGATIVE CONTROL: the ordinary-file registry and state are
        # rollbackable TOGETHER. A fake PIN is NOT a genuine external authority.
        owner,registry,token=issue('joint-rollback')
        case(verify(owner,registry,token)=='QUARANTINE_REVOKED_ONLY',
             'initial pinned challenge consumed before rollback')
        (owner/'owner-state.txt').write_text('2|owner-independent-nonce-0001|REVOKED|0\n')
        (registry/'owner-epoch.txt').write_text('2|owner-independent-nonce-0001\n')
        case(verify(owner,registry,token)=='QUARANTINE_REVOKED_ONLY',
             'KNOWN UNSAFE: ordinary owner and root rollback replays spent token')
        print('FUTURE_G1_OWNER_ROOT_JOINT_ROLLBACK=KNOWN_SECURITY_FAIL_UNFIXED')
        print('FUTURE_G1_OWNER_ROOT_MATRIX_PASS cases='+str(count))
        print('FUTURE_G1_EXTERNAL_NONROLLBACKABLE_PIN_AND_CAS=UNIMPLEMENTED')


def process_owner_preflight_to_claim_race_proof(temp):
    """Three OS JVMs expose the pinned-root preflight -> claim TOCTOU.

    Passing here means the attack is *observed*, not that it is prevented.
    The only files involved are temporary, entirely untrusted test fixtures.
    """
    from pathlib import Path
    from tempfile import TemporaryDirectory
    import time
    count=0
    def case(ok,label):
        nonlocal count
        if not ok: raise AssertionError('owner-root preflight TOCTOU FAIL: '+label)
        count+=1
    with TemporaryDirectory(prefix='future_owner_path_race_') as t:
        root=Path(t)
        command=['java','-cp',temp,'de.yahya.ai.FutureOwnerAttestationProcess']
        consumer=root/'consumer'; consumer.mkdir()
        def issue(name):
            owner=root/name
            p=subprocess.run(command+['issue',str(owner)],capture_output=True,
                             text=True,timeout=15)
            case(p.returncode==0 and p.stdout.startswith('TOKEN='),
                 name+' signer JVM issued token')
            return owner,p.stdout.strip().split('=',1)[1]
        def registry_for(owner):
            registry=root/'pinned-registry'; registry.mkdir()
            (registry/'owner-realpath.txt').write_text(str(owner.resolve())+'\n')
            (registry/'pinned-owner.pub').write_bytes((owner/'owner.pub').read_bytes())
            (registry/'owner-epoch.txt').write_text(
                '2|owner-independent-nonce-0001\n')
            return registry
        def verify(mode,owner,token,registry):
            return command+[mode,str(owner),str(consumer),'REVOKED',
                '2','a'*64,'owner-independent-nonce-0001',token,str(registry)]
        owner,legit=issue('registered-owner')
        attacker,forged=issue('forged-owner')
        registry=registry_for(owner)
        control=subprocess.run(verify('verify-bound',owner,forged,registry),
                               capture_output=True,text=True,timeout=15)
        case(control.returncode==0 and 'RESULT=BLOCKED' in control.stdout,
             'unraced pinned-root verifier rejects forged issuer')
        case((owner/'owner-state.txt').read_text().strip().endswith('|0'),
             'rejected token leaves owner unclaimed')
        verifier=subprocess.Popen(verify('verify-bound-paused',owner,forged,registry),
                                  stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
        try:
            deadline=time.monotonic()+10
            while not (consumer/'preflight-done.flag').is_file() \
                    and verifier.poll() is None and time.monotonic()<deadline:
                time.sleep(0.01)
            case((consumer/'preflight-done.flag').is_file()
                 and verifier.poll() is None,
                 'independent verifier JVM paused after successful pinned preflight')
            attack=subprocess.run(command+['swap-after-preflight',str(owner),
                str(attacker),str(consumer)],capture_output=True,text=True,timeout=15)
            case(attack.returncode==0 and
                 'ATTACK=REPLACED_AFTER_PREFLIGHT' in attack.stdout,
                 'independent attacker JVM replaced owner key/state and resumed claim')
            out,err=verifier.communicate(timeout=15)
            case(verifier.returncode==0 and
                 'RESULT=QUARANTINE_REVOKED_ONLY' in out,
                 'KNOWN UNSAFE: stale preflight trusts attacker-swapped owner at claim: '+err)
        finally:
            if verifier.poll() is None:
                verifier.kill(); verifier.communicate(timeout=5)
        case((owner/'owner-state.txt').read_text().strip().endswith('|1'),
             'forged claimant consumed the substituted owner state')
        case((registry/'pinned-owner.pub').read_bytes()!=
             (owner/'owner.pub').read_bytes(),
             'accepted forged authority differs from independently pinned key')
        after=subprocess.run(verify('verify-bound',owner,legit,registry),
                             capture_output=True,text=True,timeout=15)
        case(after.returncode==0 and 'RESULT=BLOCKED' in after.stdout,
             'fresh preflight detects key substitution too late for prior claim')
        # A stronger variant replaces the DIRECTORY PATH itself after the
        # preflight check, instead of merely swapping files within it.
        # The verifier's Owner object follows the new link at claim time.
        consumer2=root/'consumer-path'; consumer2.mkdir()
        trusted2,legit2=issue('direct-path-owner')
        forged2,forged_token2=issue('forged-path-owner')
        root2=root/'path-registry'; root2.mkdir()
        (root2/'owner-realpath.txt').write_text(str(trusted2.resolve())+'\n')
        (root2/'pinned-owner.pub').write_bytes((trusted2/'owner.pub').read_bytes())
        (root2/'owner-epoch.txt').write_text('2|owner-independent-nonce-0001\n')
        def v2(mode,token):
            return command+[mode,str(trusted2),str(consumer2),'REVOKED',
                '2','a'*64,'owner-independent-nonce-0001',token,str(root2)]
        un_raced=subprocess.run(v2('verify-bound',forged_token2),
                               capture_output=True,text=True,timeout=15)
        case(un_raced.returncode==0 and 'RESULT=BLOCKED' in un_raced.stdout,
             'direct-path preflight blocks alternate-owner signature before race')
        verifier2=subprocess.Popen(v2('verify-bound-paused',forged_token2),
                                   stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
        try:
            deadline=time.monotonic()+10
            while not (consumer2/'preflight-done.flag').is_file() \
                    and verifier2.poll() is None and time.monotonic()<deadline:
                time.sleep(0.01)
            case((consumer2/'preflight-done.flag').is_file()
                 and verifier2.poll() is None,
                 'second independent verifier paused with a canonical direct owner')
            attacker2=subprocess.run(command+['swap-path-after-preflight',
                str(trusted2),str(forged2),str(consumer2)],capture_output=True,
                text=True,timeout=15)
            case(attacker2.returncode==0 and
                 'ATTACK=OWNER_PATH_REDIRECTED_AFTER_PREFLIGHT' in attacker2.stdout,
                 'independent attacker JVM changed canonical owner to symlink')
            out2,err2=verifier2.communicate(timeout=15)
            case(verifier2.returncode==0 and
                 'RESULT=QUARANTINE_REVOKED_ONLY' in out2,
                 'KNOWN UNSAFE: same argument path resolves attacker at claim: '+err2)
        finally:
            if verifier2.poll() is None:
                verifier2.kill(); verifier2.communicate(timeout=5)
        case(trusted2.is_symlink() and
             trusted2.resolve()==forged2.resolve(),
             'owner directory path now points to a different forged root')
        case((root2/'pinned-owner.pub').read_bytes()!=
             (trusted2/'owner.pub').read_bytes(),
             'registry still pins genuine key, despite completed forged claim')
        after2=subprocess.run(v2('verify-bound',legit2),
                              capture_output=True,text=True,timeout=15)
        case(after2.returncode==0 and 'RESULT=BLOCKED' in after2.stdout,
             'new verifier correctly blocks symlink but cannot undo prior claim')
        print('FUTURE_G1_PINNED_ROOT_PREFLIGHT_TO_CLAIM_TOCTOU=KNOWN_SECURITY_FAIL_UNFIXED')
        print('FUTURE_G1_OWNER_PREFLIGHT_RACE_JVM_PASS cases='+str(count))
        print('FUTURE_G1_ATOMIC_EXTERNAL_OWNER_KEY_AND_CAS=UNIMPLEMENTED')

with tempfile.TemporaryDirectory(prefix='celine_future_recall_') as temp:
    p = Path(temp) / 'FutureMemoryRecallContract.java'
    p.write_text(harness, encoding='utf-8')
    child = Path(temp) / 'FutureProcessOwnerChild.java'
    child.write_text(child_harness, encoding='utf-8')
    strict_child = Path(temp) / 'FutureStrictOwnerProcessChild.java'
    strict_child.write_text(strict_child_harness, encoding='utf-8')
    highwater_child = Path(temp) / 'FutureHighWaterProcessChild.java'
    memory_id_child = Path(temp) / 'FutureMemoryIdProcessChild.java'
    highwater_child.write_text(high_water_process_harness, encoding='utf-8')
    memory_id_child.write_text(memory_id_process_harness, encoding='utf-8')
    owner_child = Path(temp) / 'FutureOwnerAttestationProcess.java'
    owner_child.write_text(owner_process_harness, encoding='utf-8')
    subprocess.run(['javac','-encoding','UTF-8','-d',temp,str(source),str(p),str(child),str(strict_child),str(highwater_child),str(memory_id_child),str(owner_child)], check=True)
    import sys
    if '--focused-owner-race' in sys.argv:
        process_owner_preflight_to_claim_race_proof(temp)
        raise SystemExit(0)
    if '--focused-owner-root' in sys.argv:
        process_owner_root_matrix_proof(temp)
        raise SystemExit(0)
    if '--focused-owner-forgery' in sys.argv:
        process_caller_selected_owner_adversary_proof(temp)
        raise SystemExit(0)
    if '--focused-owner-2jvm' in sys.argv:
        process_independent_owner_attestation_proof(temp)
        raise SystemExit(0)
    if '--focused-attestation' in sys.argv:
        subprocess.run(['java','-cp',temp,'de.yahya.ai.FutureMemoryRecallContract'], check=True)
        raise SystemExit(0)
    if '--focused-torn' in sys.argv:
        process_memory_id_torn_witness_proof(temp)
        raise SystemExit(0)
    if '--focused-id-race' in sys.argv:
        process_memory_id_revoke_correction_proof(temp)
        process_memory_id_witness_proof(temp)
        raise SystemExit(0)
    subprocess.run(['java','-cp',temp,'de.yahya.ai.FutureMemoryRecallContract'], check=True)
    process_owner_proof(temp)
    process_strict_owner_proof(temp)
    process_high_water_proof(temp)
    process_high_water_locked_callback_proof(temp)
    process_high_water_killed_callback_proof(temp)
    process_memory_id_revoke_correction_proof(temp)
    process_memory_id_witness_proof(temp)
    process_memory_id_torn_witness_proof(temp)
    process_independent_owner_attestation_proof(temp)
    process_caller_selected_owner_adversary_proof(temp)
    process_owner_root_matrix_proof(temp)
    process_owner_preflight_to_claim_race_proof(temp)
