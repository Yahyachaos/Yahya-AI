/** Independent source-exact time-poison regression; no Samsung hardware evidence. */
public final class VoiceOutputStopDuplicateClockTest {
    private static int checks;
    private static void check(boolean b,String s){if(!b)throw new AssertionError(s);checks++;}
    public static void main(String[] args){
        {
            var g=new VoiceOutputStopReceiptGate(500);
            var r=g.request(1,1,100);
            check(r!=null,"request");
            check(g.acknowledgeStopped(r,1,1,120),"first ACK");
            check(!g.acknowledgeStopped(r,1,1,700),"expired duplicate ACK rejected");
            check(!g.consumeForExplicitAbort(r,1,1,130),"cannot backdate consume after expired duplicate");
            check(g.request(2,2,699)==null,"expired duplicate sets monotonic clock");
        }
        {
            var g=new VoiceOutputStopReceiptGate(500);
            var r=g.request(1,1,100);
            check(r!=null,"request2");
            check(g.acknowledgeStopped(r,1,1,120),"ack2");
            check(!g.acknowledgeStopped(r,1,1,180),"duplicate inside TTL rejected");
            check(!g.consumeForExplicitAbort(r,1,1,160),"backdated consume after later duplicate denied");
            check(g.request(2,2,179)==null,"backdated retry denied");
            check(g.request(2,2,190)!=null,"forward retry allowed");
        }
        System.out.println("VOICE_OUTPUT_DUPLICATE_CLOCK_PASS checks="+checks);
    }
}
