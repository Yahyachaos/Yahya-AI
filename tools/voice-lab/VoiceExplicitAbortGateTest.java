public final class VoiceExplicitAbortGateTest {
  private static int checks;
  private static void ok(boolean b,String why){if(!b)throw new AssertionError(why);checks++;}
  public static void main(String[] args){
    var user=VoiceExplicitAbortGate.UserAction.ABANDON_UNKNOWN_SPEECH_AND_START_NEW_TEXT;
    var stop=VoiceExplicitAbortGate.OldOutputStop.EXTERNALLY_CONFIRMED_STOPPED;
    for(var p:VoiceExplicitAbortGate.PriorState.values())for(long id=1;id<=100;id++){
      boolean expected=p!=VoiceExplicitAbortGate.PriorState.OTHER;
      ok(VoiceExplicitAbortGate.mayAbandon(id,id,false,p,user,stop)==expected,"allowed "+id);
      ok(!VoiceExplicitAbortGate.mayAbandon(id,id,true,p,user,stop),"active");
      ok(!VoiceExplicitAbortGate.mayAbandon(id,id,false,p,null,stop),"user");
      ok(!VoiceExplicitAbortGate.mayAbandon(id,id,false,p,user,null),"stop");
      ok(!VoiceExplicitAbortGate.mayAbandon(id+1,id,false,p,user,stop),"future");
      ok(!VoiceExplicitAbortGate.mayAbandon(id-1,id,false,p,user,stop),"stale");
    }
    ok(!VoiceExplicitAbortGate.mayAbandon(0,0,false,VoiceExplicitAbortGate.PriorState.INTERRUPTED_UNCERTAIN,user,stop),"zero");
    ok(!VoiceExplicitAbortGate.mayAbandon(-1,-1,false,VoiceExplicitAbortGate.PriorState.INTERRUPTED_UNCERTAIN,user,stop),"negative");
    ok(!VoiceExplicitAbortGate.mayAbandon(12,12,false,null,user,stop),"null");
    System.out.println("VOICE_EXPLICIT_ABORT_GATE_PASS checks="+checks);
  }
}