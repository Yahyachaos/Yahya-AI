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
        List<CelineFutureMemoryRecall.Match> result = CelineFutureMemoryRecall.retrieve(query,entries,8,NOW);
        return result.isEmpty()?"":result.get(0).candidate.id;
    }
    public static void main(String[] args) {
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
        eq(CelineFutureMemoryRecall.unambiguousCorrectionTarget("Meine Katze heißt nicht Luna",data,NOW),
            "cat","unique correction suggestion");
        eq(CelineFutureMemoryRecall.unambiguousCorrectionTarget("Meine Katze",Arrays.asList(
            c("a","Meine Katze frisst gerne"),c("b","Meine Katze heißt Luna")),NOW),
            "","ambiguous correction fail closed");
        eq(CelineFutureMemoryRecall.unambiguousCorrectionTarget("Tee",data,NOW),"","single-token correction fail closed");
        eq(CelineFutureMemoryRecall.unambiguousCorrectionTarget("Mein Lieblingsgetränk ist Kaffee",data,NOW),
            "","correction without discriminating target must request ID");
        eq(CelineFutureMemoryRecall.retrieve("Katze",data,0,NOW).size(),0,"zero result bound");
        eq(top("Katze",Arrays.asList(c("b","Katze B"),c("a","Katze A"))),"a","deterministic tie");
        eq(CelineFutureMemoryRecall.retrieve("Katze",data,100,NOW).size(),1,"bounded result");
        List<CelineFutureMemoryRecall.Candidate> capacity = new ArrayList<>();
        capacity.add(x("old-capacity", "Meine Katze heißt Paul", "", true, 0));
        for (int i=1; i<2048; i++) capacity.add(c("noise-"+i, "Anderes Thema Nummer "+i));
        eq(top("Meine Katze", capacity),"old-capacity","maximum allowed capacity remains searchable");
        capacity.add(new CelineFutureMemoryRecall.Candidate("late-private-successor",
                "Meine Katze heißt Luna", "old-capacity",
                CelineFutureMemoryRecall.Privacy.LOCAL_SENSITIVE, true, true, NOW, 0, 1));
        eq(top("Meine Katze", capacity),"", "overflow privacy must fail closed");
        eq(CelineFutureMemoryRecall.unambiguousCorrectionTarget("Meine Katze heißt Luna", capacity, NOW),
                "", "overflow correction suggestion must fail closed");
        // Future-only monotonic correction policy: a supplied expired successor still
        // suppresses its predecessor; expiration must never resurrect obsolete facts.
        List<CelineFutureMemoryRecall.Candidate> expiredSuccessor = Arrays.asList(
            x("old-cat", "Meine Katze heißt Paul", "", true, 0),
            x("expired-new-cat", "Meine Katze heißt Luna", "old-cat", true, NOW-1));
        eq(top("Meine Katze heißt", expiredSuccessor), "", "expired successor cannot resurrect old");
        eq(CelineFutureMemoryRecall.unambiguousCorrectionTarget("Meine Katze heißt Paul",expiredSuccessor,NOW),
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
        eq(CelineFutureMemoryRecall.unambiguousCorrectionTarget("Meine Katze heißt Paul",duplicateId,NOW),
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
        eq(CelineFutureMemoryRecall.retrieve("Meine Katze heißt",prunedProjection,retired,8,NOW).size(),
            0,"explicit retraction suppresses old record without successor projection");
        eq(CelineFutureMemoryRecall.unambiguousCorrectionTarget(
            "Meine Katze heißt Paul",prunedProjection,retired,NOW),
            "","explicit retraction prevents stale correction suggestion");
        eq(CelineFutureMemoryRecall.retrieve("Damu Markt Getränke",prunedProjection,retired,8,NOW)
            .get(0).candidate.id,"valid-work","unrelated candidate remains available");
        eq(CelineFutureMemoryRecall.retrieve("Damu Markt Getränke",prunedProjection,null,8,NOW).size(),
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
        eq(CelineFutureMemoryRecall.unambiguousCorrectionTarget(
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
        eq(CelineFutureMemoryRecall.unambiguousCorrectionTarget("Meine Katze heißt Paul",
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
        eq(CelineFutureMemoryRecall.unambiguousCorrectionTarget(
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
        eq(CelineFutureMemoryRecall.retrieve("Meine Katze heißt", reimport,
                Collections.singleton("reimport-old"), 8, NOW).get(0).candidate.id,
            "reimport-winner", "authoritative tombstone blocks stale reimport");
        // A retired opponent ID alone does not prove an unresolved G1 conflict
        // was adjudicated; only cleared conflict metadata can make a winner
        // eligible, with independent provenance and permission checks.
        CelineFutureMemoryRecall.Candidate unresolvedReimport =
            new CelineFutureMemoryRecall.Candidate("unresolved-reimport",
                "Meine Katze heißt Luna", "", "reimport-old",
                CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE, true, true, NOW, 0, 1);
        eq(CelineFutureMemoryRecall.retrieve("Meine Katze heißt",
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
        eq(CelineFutureMemoryRecall.unambiguousCorrectionTarget(
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
        eq(CelineFutureMemoryRecall.unambiguousCorrectionTarget("Meine Katze heißt Luna",
            Arrays.asList(new CelineFutureMemoryRecall.Candidate("negative-correction",
                "Meine Katze heißt Luna", "", CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE,
                true, true, NOW-100, -1, 1)), NOW), "",
                "invalid memory must never become automatic correction target");
        eq(top("Meine Katze heißt", Arrays.asList(
            CelineFutureMemoryRecall.Candidate.fromG1Projection(
                "g1-negative-expiry", "Meine Katze heißt Luna", "", "LOCAL_PRIVATE",
                "EXPLICIT", "user_explicit", true, true, NOW-100, -1, 1))), "",
                "source-grounded G1 negative expiry remains invalid");
        eq(CelineFutureMemoryRecall.retrieve("Meine Katze heißt",
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
        eq(CelineFutureMemoryRecall.retrieve("Meine Katze heisst",
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
        eq(CelineFutureMemoryRecall.retrieve("Meine Katze heisst",
            Arrays.asList(x("valid-cat", "Meine Katze heisst Paul", "", true, 0)),
            Collections.singleton("unknown\n"), 8, NOW).size(), 0,
            "source retired-ID with trailing newline rejects full projection");
        eq(CelineFutureMemoryRecall.retrieve("Meine Katze heisst",
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
        eq(CelineFutureMemoryRecall.retrieve("Meine Katze heisst",
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
        eq(CelineFutureMemoryRecall.retrieve("Meine Katze heisst Luna",
            Arrays.asList(orphan), Collections.singleton("unknown-parent"),
            Collections.singleton("unknown-parent"), 8, NOW)
            .get(0).candidate.id, "orphan-new",
            "separately confirmed retired-parent history permits correction chain");
        eq(CelineFutureMemoryRecall.unambiguousCorrectionTarget("Meine Katze heisst Luna",
            Arrays.asList(orphan), NOW), "",
            "orphan is never suggested as natural-language correction target");

        // Bare tombstone is not reliable proof of a pruned conflict winner.
        Set<String> retiredParent = Collections.singleton("unknown-parent");
        eq(CelineFutureMemoryRecall.retrieve("Meine Katze heisst Luna",
            Arrays.asList(orphan), retiredParent, 8, NOW).size(), 0,
            "tombstone-only pruned parent must abstain");
        eq(CelineFutureMemoryRecall.unambiguousCorrectionTarget(
            "Meine Katze heisst Luna", Arrays.asList(orphan), retiredParent, NOW), "",
            "tombstone-only correction target must abstain");
        eq(CelineFutureMemoryRecall.unambiguousCorrectionTarget(
            "Meine Katze heisst Luna", Arrays.asList(orphan),
            retiredParent, retiredParent, NOW), "orphan-new",
            "source-confirmed lineage permits precise correction target");
        eq(CelineFutureMemoryRecall.retrieve("Meine Katze heisst Max",
            Arrays.asList(orphan, orphanDescendant), retiredParent, retiredParent, 8, NOW)
            .get(0).candidate.id, "orphan-grandchild",
            "source-confirmed ancestor permits safe descendant");
        eq(CelineFutureMemoryRecall.retrieve("Damu Markt Getränke",
            Arrays.asList(orphan, c("safe-independent", "Damu Markt verkauft Getränke")),
            retiredParent, 8, NOW).get(0).candidate.id, "safe-independent",
            "independent memory survives unconfirmed compacted ancestor");
        eq(CelineFutureMemoryRecall.retrieve("Damu Markt Getränke",
            Arrays.asList(c("independent-only", "Damu Markt verkauft Getränke")),
            Collections.<String>emptySet(), retiredParent, 8, NOW).size(), 0,
            "unmatched confirmation fails complete projection");
        eq(CelineFutureMemoryRecall.retrieve("Meine Katze heisst Luna",
            Arrays.asList(orphan), retiredParent, null, 8, NOW).size(), 0,
            "missing confirmation set fails complete projection");
        eq(CelineFutureMemoryRecall.retrieve("Meine Katze heisst Luna",
            Arrays.asList(orphan), retiredParent,
            Collections.singleton("invalid!proof"), 8, NOW).size(), 0,
            "invalid confirmation key fails complete projection");
        CelineFutureMemoryRecall.Candidate unresolvedCompactedConflict =
            new CelineFutureMemoryRecall.Candidate("conflicted-compacted",
                "Meine Katze heisst Luna", "unknown-parent", "missing-opponent",
                CelineFutureMemoryRecall.Privacy.LOCAL_PRIVATE, true, true, NOW-100, 0, 0.5);
        eq(CelineFutureMemoryRecall.retrieve("Meine Katze heisst Luna",
            Arrays.asList(unresolvedCompactedConflict), retiredParent, retiredParent,
            8, NOW).size(), 0,
            "visible unresolved conflict still wins over owner attestation");
        eq(CelineFutureMemoryRecall.retrieve("Meine Katze heisst Luna",
            Arrays.asList(
                x("retired-fork-luna", "Meine Katze heisst Luna", "unknown-parent", true, 0),
                x("retired-fork-max", "Meine Katze heisst Max", "unknown-parent", true, 0)),
            retiredParent, retiredParent, 8, NOW).size(), 0,
            "visible correction siblings remain unresolved despite attestation");
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
        eq(CelineFutureMemoryRecall.unambiguousCorrectionTarget(
            "Meine Katze heisst Lu\u200Bna",Arrays.asList(
                c("format-correction","Meine Katze heisst Luna")),NOW),"",
            "format-corrupted correction must not select memory ID");
        eq(top("Meine Katze\nheisst Luna",Arrays.asList(
            c("multiline-valid","Meine Katze heisst Luna"))),"multiline-valid",
            "ordinary multiline German query still accepted");
        eq(top("Meine Katze heisst Luna\uD83D\uDC08",Arrays.asList(
            c("emoji-valid","Meine Katze heisst Luna"))),"emoji-valid",
            "valid supplementary emoji must remain harmless");
        System.out.println("FUTURE_MEMORY_RECALL_CONTRACT_PASS cases="+passed);
    }
}
'''
with tempfile.TemporaryDirectory(prefix='celine_future_recall_') as temp:
    p = Path(temp) / 'FutureMemoryRecallContract.java'
    p.write_text(harness, encoding='utf-8')
    subprocess.run(['javac','-encoding','UTF-8','-d',temp,str(source),str(p)], check=True)
    subprocess.run(['java','-cp',temp,'de.yahya.ai.FutureMemoryRecallContract'], check=True)
