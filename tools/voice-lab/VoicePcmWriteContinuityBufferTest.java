import java.util.ArrayDeque;
import java.util.Random;

/** Real executable JVM model checks, NOT an Android AudioTrack/hardware test. */
public final class VoicePcmWriteContinuityBufferTest {
    private static long checks;
    private static void yes(boolean truth, String reason) {
        checks++;
        if (!truth) throw new AssertionError(reason + " check=" + checks);
    }
    private static void equal(float actual, float expected, String reason) {
        checks++;
        if (Float.floatToIntBits(actual) != Float.floatToIntBits(expected))
            throw new AssertionError(reason + " expected=" + expected + " got=" + actual);
    }
    private static void partialWrite() {
        var b = new VoicePcmWriteContinuityBuffer(1, 16, 6, 1000);
        long id = b.begin(1);
        float[] all = new float[12];
        for (int i=0; i<all.length; i++) all[i] = i + .5f;
        yes(b.offer(id, all, 0, 12, 2) == 12, "initial queue");
        var first = b.lease(id, 8, 3);
        yes(first != null && first.frames == 8, "first lease");
        yes(b.queuedFrames() == 12, "lease may not drop frames");
        yes(b.commitWritten(first, 3, 4), "partial AudioTrack write");
        yes(!b.commitWritten(first, 1, 5), "duplicate return must not double-consume");
        yes(b.synthesisEnded(id, 6), "synthesis before hardware write-drain");
        var last = b.lease(id, 15, 7);
        yes(last != null && last.frames == 9, "replay all unwritten frames");
        for (int i=0; i<9; i++) equal(last.interleavedPcm[i], all[i+3], "ordered PCM " + i);
        yes(b.commitWritten(last, 9, 8), "last PCM accepted");
        yes(b.state() == VoicePcmWriteContinuityBuffer.State.COMPLETED,
            "COMPLETED means AudioTrack write accepted only, not audible tail");
    }
    private static void shortAndStale() {
        var b = new VoicePcmWriteContinuityBuffer(2, 8, 5, 10);
        long old = b.begin(0);
        float[] stereo = {1,2,3,4,5,6};
        yes(b.offer(old, stereo, 0, 3, 1) == 3, "short under-prime speech");
        yes(b.lease(old, 8, 2) == null, "must prime or finish before audio write");
        yes(b.synthesisEnded(old, 3), "short utterance complete synthesis");
        var d = b.lease(old, 8, 4);
        yes(d != null && d.frames == 3 && d.interleavedPcm.length == 6,
            "drain subthreshold utterance without padding");
        yes(b.interrupt(old), "interrupt clears lease");
        long current = b.begin(20);
        yes(current > old && b.queuedFrames() == 0, "new generation empty");
        yes(!b.commitWritten(d, 3, 21), "old write result rejected");
        yes(b.offer(old, stereo, 0, 1, 22) == -1, "old PCM callback rejected");
        yes(b.offer(current, stereo, 0, 3, 22) == 3, "new generation valid");
        yes(!b.tick(current, 21), "clock reversal poisons current generation");
        yes(b.state() == VoicePcmWriteContinuityBuffer.State.FAILED, "clock fail closed");
        long next = b.begin(1);
        yes(b.offer(next, stereo, 0, 1, 2) == 1, "post-failure new session");
        yes(!b.tick(next, 13), "idle timestamp expires");
        yes(b.queuedFrames() == 0, "timed out audio flushed");
    }
    private static void backpressureAndBadPcm() {
        var b = new VoicePcmWriteContinuityBuffer(1, 6, 3, 100);
        long id = b.begin(0);
        float[] a={0,1,2,3,4,5,6,7,8,9};
        yes(b.offer(id,a,0,10,1)==6,"capacity must cap without dropping unknown frames");
        yes(b.offer(id,a,6,4,2)==0,"return backpressure explicitly");
        var w=b.lease(id,4,3);
        yes(w!=null && w.frames==4,"first partial lease");
        yes(b.commitWritten(w,2,4),"accept only actual written count");
        yes(b.offer(id,a,6,4,5)==2,"append only two available slots");
        var w2=b.lease(id,6,6);
        yes(w2!=null && w2.frames==6,"precise ring wrap lease");
        float[] expected={2,3,4,5,6,7};
        for(int j=0;j<6;j++)equal(w2.interleavedPcm[j],expected[j],"ring ordering");
        yes(b.commitWritten(w2,6,7),"drain");
        float[] bad={1,Float.NaN,2};
        yes(b.offer(id,bad,0,3,8)==-1,"NaN is not valid PCM");
        yes(b.state()==VoicePcmWriteContinuityBuffer.State.FAILED,"invalid PCM fails closed");
    }
    private static void activeResetAndInvalidReturn() {
        var b = new VoicePcmWriteContinuityBuffer(1, 8, 2, 100);
        long first=b.begin(0);
        float[] pcm={10,11,12,13};
        yes(b.offer(first,pcm,0,4,1)==4,"initial active PCM");
        boolean denied=false;
        try { b.begin(2); } catch (IllegalStateException expected) { denied=true; }
        yes(denied,"cannot overwrite active unverified sound with new epoch");
        yes(b.epoch()==first && b.queuedFrames()==4,"rejected reset keeps old audio");
        var lease=b.lease(first,3,3);
        yes(lease!=null,"active lease exists");
        yes(!b.commitWritten(lease,4,4),"impossible driver write count is not accepted");
        yes(b.state()==VoicePcmWriteContinuityBuffer.State.FAILED,
                "invalid driver returned write count must poison active stream");
        yes(b.queuedFrames()==0,"no unverified buffer reuse");
        long next=b.begin(5);
        yes(next>first,"explicit restart after failure");
        yes(!b.commitWritten(lease,3,6),"late first-generation write cannot affect successor");
        yes(b.engineDied(next),"engine loss discards second buffer");
        yes(b.state()==VoicePcmWriteContinuityBuffer.State.FAILED,"engine fail closed");
    }
    private static void seededOracle() {
        Random rnd = new Random(0xBC714A10L);
        for (int trial=0; trial<160; trial++) {
            var b = new VoicePcmWriteContinuityBuffer(1, 31, 7, 1000000);
            long epoch=b.begin(0);
            ArrayDeque<Float> oracle=new ArrayDeque<>();
            long clock=0;
            int value=trial*10000;
            for (int i=0; i<420; i++) {
                clock++;
                if (rnd.nextBoolean()) {
                    int n=1+rnd.nextInt(23);
                    float[] add=new float[n];
                    for(int j=0;j<n;j++)add[j]=value++;
                    int a=b.offer(epoch,add,0,n,clock);
                    yes(a==Math.min(n,31-oracle.size()),"oracle input capacity");
                    for(int j=0;j<a;j++)oracle.addLast(add[j]);
                } else {
                    var lease=b.lease(epoch,1+rnd.nextInt(26),clock);
                    if (lease != null) {
                        int n=Math.min(lease.frames,rnd.nextInt(lease.frames+1));
                        var it=oracle.iterator();
                        for(int j=0;j<lease.frames;j++) {
                            yes(it.hasNext(),"oracle sample exists");
                            equal(lease.interleavedPcm[j],it.next(),"FIFO no duplicated/dropped samples");
                        }
                        clock++;
                        yes(b.commitWritten(lease,n,clock),"partial source write commit");
                        for(int j=0;j<n;j++)oracle.removeFirst();
                    }
                }
                yes(b.queuedFrames()==oracle.size(),"exact outstanding frame count");
            }
            clock++;
            yes(b.synthesisEnded(epoch,clock),"finish stream");
            while(!oracle.isEmpty()) {
                var lease=b.lease(epoch,19,++clock);
                yes(lease != null,"end flush lease");
                for(float f:lease.interleavedPcm)equal(f,oracle.removeFirst(),"end flush FIFO");
                yes(b.commitWritten(lease,lease.frames,++clock),"end flush commit");
            }
            yes(b.state()==VoicePcmWriteContinuityBuffer.State.COMPLETED,"all accepted writes completed");
        }
    }
    public static void main(String[] args) {
        partialWrite(); shortAndStale(); backpressureAndBadPcm(); activeResetAndInvalidReturn(); seededOracle();
        System.out.println("VOICE_PCM_WRITE_CONTINUITY_PASS checks="+checks);
    }
}
