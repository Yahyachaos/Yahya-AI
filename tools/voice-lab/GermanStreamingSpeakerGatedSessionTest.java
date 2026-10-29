/** Synthetic adapter-style sequencing regression. NOT captured device audio. */
public final class GermanStreamingSpeakerGatedSessionTest {
    private static int checks;
    private static void ok(boolean b, String label) { if (!b) throw new AssertionError("FAIL "+label); checks++; }
    private static void no(boolean b, String label) { ok(!b,label); }
    private static void eq(Object a,Object b,String label) { ok(java.util.Objects.equals(a,b),label+" "+a+" vs "+b); }
    public static void main(String[] args) {
        GermanStreamingSpeakerGatedSession s = new GermanStreamingSpeakerGatedSession(48000);
        long id=s.begin("Erstens. Zweitens.");
        long ms=100;
        long ns=1_000_000_000L;
        GermanStreamingRawDrainCoordinator.Dispatch a=s.lease(id,++ms,ns);
        ok(a!=null,"first lease without predecessor");
        ok(s.prepared(a,++ms),"prepared first");
        ok(s.acceptedPcm(a,100,VoiceLifecycleContract.Mouth.OPEN,++ms),"accepted first");
        ok(s.synthesisEnded(a,++ms),"first synth ended");
        no(s.lease(id,++ms,ns)!=null,"cannot lease while raw drain not complete");
        ok(s.playbackHead(a,100,++ms),"raw drained first");
        eq(1,s.confirmedChunks(),"first raw drained");
        ok(s.hasUnprovenSpeakerTail(),"first speaker tail pending");
        no(s.lease(id,++ms,ns)!=null,"raw head without capture must NOT lease next");
        no(s.observeSpeakerTail(a.audioGeneration,VoiceVerifiedPresentationClock.EvidenceKind.RAW_HEAD_ONLY,
                100,100,100,ns,ns+1_000_000L),"raw-only rejected");
        no(s.lease(id,++ms,ns+2_000_000L)!=null,"raw-only still blocks");
        ok(s.observeSpeakerTail(a.audioGeneration,VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                80,100,100,ns+10_000_000L,ns+11_000_000L),"partial external capture");
        no(s.lease(id,++ms,ns+12_000_000L)!=null,"partial proof still blocks");
        ok(s.observeSpeakerTail(a.audioGeneration,VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                100,100,100,ns+40_000_000L,ns+41_000_000L),"fully presented claim");
        GermanStreamingRawDrainCoordinator.Dispatch b=s.lease(id,++ms,ns+42_000_000L);
        ok(b!=null,"fresh capture releases next only once");
        eq("Zweitens.",b.text,"second chunk text");
        no(s.playbackHead(a,100,++ms),"old playback head rejected");
        no(s.observeSpeakerTail(a.audioGeneration,VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                100,100,100,ns+50_000_000L,ns+51_000_000L),"old speaker tail rejected");
        ok(s.prepared(b,++ms),"second prepare");
        ok(s.acceptedPcm(b,50,VoiceLifecycleContract.Mouth.LABIAL,++ms),"second PCM");
        ok(s.playbackHead(b,50,++ms),"second raw played before synth");
        ok(s.synthesisEnded(b,++ms),"second synth done");
        eq(GermanStreamingDeliveryLedger.Phase.COMPLETE,s.phase(),"raw plan complete");
        ok(s.hasUnprovenSpeakerTail(),"final acoustic tail remains pending");
        boolean rejected=false;
        try { s.begin("Neuer Satz."); } catch(IllegalStateException e) { rejected=true; }
        ok(rejected,"new utterance cannot discard final captured tail");
        no(s.confirmFinalTail(ns+50_000_000L),"no final capture no permit");
        ok(s.observeSpeakerTail(b.audioGeneration,VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                50,50,50,ns+60_000_000L,ns+61_000_000L),"final capture claim");
        ok(s.confirmFinalTail(ns+62_000_000L),"confirmed final external tail");
        no(s.confirmFinalTail(ns+63_000_000L),"final permit is single-use");
        id=s.begin("Neuer Satz. Noch einer.");
        GermanStreamingRawDrainCoordinator.Dispatch c=s.lease(id,++ms,ns+100_000_000L);
        ok(c!=null,"new plan after full acoustic claim");
        ok(s.prepared(c,++ms),"new plan prepare");
        ok(s.acceptedPcm(c,80,VoiceLifecycleContract.Mouth.OPEN,++ms),"new PCM");
        ok(s.engineDied(c,++ms),"engine death stops track");
        eq(GermanStreamingDeliveryLedger.Phase.UNCERTAIN,s.phase(),"audible status uncertain");
        no(s.lease(id,++ms,ns+102_000_000L)!=null,"uncertain chunk cannot silently continue");
        long fresh=s.restartProvenSilent(id);
        ok(fresh>id,"only explicitly externally proven-silent retry");
        GermanStreamingRawDrainCoordinator.Dispatch d=s.lease(fresh,++ms,ns+105_000_000L);
        ok(d!=null,"fresh retry after external policy claim");
        eq(c.text,d.text,"same chunk replay after asserted silence");
        no(s.prepared(c,++ms),"old TTS callback stale");
        ok(s.prepared(d,++ms),"retry prepared");
        ok(s.acceptedPcm(d,25,VoiceLifecycleContract.Mouth.LABIAL,++ms),"retry PCM accepted");
        ok(s.interrupt(d,++ms),"interruption stops retry");
        eq(GermanStreamingDeliveryLedger.Phase.UNCERTAIN,s.phase(),"retry interruption uncertain");
        GermanStreamingSpeakerGatedSession watchdog = new GermanStreamingSpeakerGatedSession(48000);
        long wg=watchdog.begin("Erster. Zweiter.");
        GermanStreamingRawDrainCoordinator.Dispatch wt=watchdog.lease(wg,10,3_000_000_000L);
        ok(wt!=null,"watchdog first lease");
        ok(watchdog.tick(6011),"preparation watchdog stops unresponsive engine");
        eq(GermanStreamingDeliveryLedger.Phase.UNCERTAIN,watchdog.phase(),"watchdog makes audio uncertain");
        long recovered=watchdog.restartProvenSilent(wg);
        ok(recovered>wg,"explicit proof policy after watchdog");
        GermanStreamingRawDrainCoordinator.Dispatch wd=watchdog.lease(recovered,6012,3_100_000_000L);
        ok(wd!=null,"watchdog recovery must reset speaker-clock epoch");
        System.out.println("VOICE_GERMAN_GATED_STREAM_SESSION_PASS checks="+checks);
    }
}
