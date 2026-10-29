/** Synthetic JVM-only poisoned speaker-tail and explicit user abort regression. */
public final class GermanStreamingExplicitAbortIntegrationTest {
  private static int checks;
  private static void ok(boolean b,String why){if(!b)throw new AssertionError(why);checks++;}
  private static void no(boolean b,String why){ok(!b,why);}
  private static final VoiceExplicitAbortGate.UserAction USER =
      VoiceExplicitAbortGate.UserAction.ABANDON_UNKNOWN_SPEECH_AND_START_NEW_TEXT;
  private static final VoiceExplicitAbortGate.OldOutputStop STOP =
      VoiceExplicitAbortGate.OldOutputStop.EXTERNALLY_CONFIRMED_STOPPED;
  private static void blocked(GermanStreamingSpeakerGatedSession s){
    boolean threw=false;
    try{s.begin("No silent continuation.");}catch(IllegalStateException e){threw=true;}
    ok(threw,"implicit new plan blocked");
  }
  private static void poison(boolean finalChunk){
    var s=new GermanStreamingSpeakerGatedSession(48000);
    long plan=s.begin(finalChunk?"Erster.":"Erster. Zweiter."),ms=100,ns=1_000_000_000L;
    var d=s.lease(plan,++ms,ns);ok(d!=null,"first lease");
    ok(s.prepared(d,++ms),"prepare");
    ok(s.acceptedPcm(d,100,VoiceLifecycleContract.Mouth.OPEN,++ms),"PCM");
    ok(s.synthesisEnded(d,++ms),"synth");
    ok(s.playbackHead(d,100,++ms),"raw drain");
    ok(s.hasUnprovenSpeakerTail(),"raw-only is pending");
    ok(s.observeSpeakerTail(d.audioGeneration,
      VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
      100,100,100,ns+10_000_000L,ns+11_000_000L),"first capture claim");
    no(s.observeSpeakerTail(d.audioGeneration,
      VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
      99,100,100,ns+20_000_000L,ns+21_000_000L),"rollback");
    no(s.observeSpeakerTail(d.audioGeneration,
      VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
      100,100,100,ns+30_000_000L,ns+31_000_000L),"poison cannot heal");
    if(finalChunk)no(s.confirmFinalTail(ns+32_000_000L),"no final permit");
    else ok(s.lease(plan,++ms,ns+32_000_000L)==null,"no next-chunk permit");
    blocked(s);
    ok(s.abortAndBeginNewSession(plan,null,STOP,"Neu.")==null,"user choice required");
    ok(s.abortAndBeginNewSession(plan,USER,null,"Neu.")==null,"external stop required");
    ok(s.abortAndBeginNewSession(plan+1,USER,STOP,"Neu.")==null,"no wrong epoch");
    var receipt=s.abortAndBeginNewSession(plan,USER,STOP,"Neu. Fertig.");
    ok(receipt!=null,"explicit abort accepted");
    ok(receipt.previousSpeechAudibilityUnknown,"never promote RAW to heard");
    ok(receipt.previousRawDrainedChunks==1,"diagnostic old raw count");
    ok("Erster.".equals(receipt.previousRawDrainedText),"diagnostic old raw text");
    ok(receipt.unresolvedSpeakerAudioGeneration==d.audioGeneration,"old audio epoch");
    ok(receipt.newPlanGeneration>plan,"plan generation monotonic");
    ok(s.confirmedChunks()==0,"new plan has zero confirmed chunks");
    no(s.hasUnprovenSpeakerTail(),"old tail discarded by explicit action");
    no(s.observeSpeakerTail(d.audioGeneration,
      VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
      100,100,100,ns+50_000_000L,ns+51_000_000L),"old speaker callback stale");
    no(s.playbackHead(d,100,++ms),"old raw callback stale");
    ok(s.abortAndBeginNewSession(plan,USER,STOP,"Not authorized.")==null,"old action one shot");
    var fresh=s.lease(receipt.newPlanGeneration,++ms,ns+60_000_000L);
    ok(fresh!=null,"new independent plan");
    ok("Neu.".equals(fresh.text),"no automatic replay/skip");
    ok(fresh.audioGeneration>d.audioGeneration,"new audio epoch");
    ok(s.abortAndBeginNewSession(receipt.newPlanGeneration,USER,STOP,"Busy.")==null,
      "active track cannot be abandoned");
  }
  private static void engineDeath(){
    var s=new GermanStreamingSpeakerGatedSession(48000);
    long plan=s.begin("Ungewiss. Nicht wiederholen."),ms=100;
    var d=s.lease(plan,++ms,2_000_000_000L);ok(d!=null,"lease");
    ok(s.prepared(d,++ms),"prepared");
    ok(s.acceptedPcm(d,30,VoiceLifecycleContract.Mouth.LABIAL,++ms),"PCM");
    ok(s.engineDied(d,++ms),"TTS engine killed");
    ok(s.phase()==GermanStreamingDeliveryLedger.Phase.UNCERTAIN,"uncertain not confirmed");
    blocked(s);
    ok(s.abortAndBeginNewSession(plan,null,STOP,"Neu.")==null,"must choose abort");
    var receipt=s.abortAndBeginNewSession(plan,USER,STOP,"Ganz neuer Satz.");
    ok(receipt!=null,"explicit old-uncertain abort");
    ok(receipt.previousRawDrainedChunks==0,"no old chunk falsely confirmed");
    ok(receipt.unresolvedSpeakerAudioGeneration==-1,"no drained speaker tail");
    var next=s.lease(receipt.newPlanGeneration,++ms,2_100_000_000L);
    ok(next!=null,"fresh lease");
    ok("Ganz neuer Satz.".equals(next.text),"old uncertain text not replayed");
    ok(next.audioGeneration>d.audioGeneration,"epoch monotonic");
    no(s.prepared(d,++ms),"old engine callback rejected");
  }
  public static void main(String[] args){
    poison(false);poison(true);engineDeath();
    System.out.println("VOICE_GERMAN_EXPLICIT_ABORT_INTEGRATION_PASS checks="+checks);
  }
}
