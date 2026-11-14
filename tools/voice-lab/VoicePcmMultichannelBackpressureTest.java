import java.util.ArrayDeque;
import java.util.Random;

/** FUTURE ONLY: deterministic pure-JVM multichannel PCM FIFO / partial-write oracle.
 * No Android AudioTrack, synthesized speech or acoustic playback is exercised. */
public final class VoicePcmMultichannelBackpressureTest {
    private static long checks;
    private static void check(boolean value, String what) {
        checks++;
        if (!value) throw new AssertionError(what + " check=" + checks);
    }
    private static void expect(float actual, float expected, String what) {
        checks++;
        if (Float.floatToIntBits(actual) != Float.floatToIntBits(expected)) {
            throw new AssertionError(what + " check=" + checks + " expected=" + expected + " got=" + actual);
        }
    }
    private static void fuzzChannels() {
        Random r = new Random(0x513CA770L);
        for (int channels : new int[] {2, 3, 4, 6, 8}) {
            for (int trial = 0; trial < 80; trial++) {
                int capacity = 4 + r.nextInt(45);
                int prime = 1 + r.nextInt(capacity);
                var b = new VoicePcmWriteContinuityBuffer(channels, capacity, prime, 1_000_000);
                long clock = 0;
                long epoch = b.begin(clock);
                ArrayDeque<float[]> oracle = new ArrayDeque<>();
                int base = trial * 1_000_000 + channels * 100_000;
                int value = 1;
                for (int i = 0; i < 260; i++) {
                    clock++;
                    boolean producer = r.nextInt(3) != 0;
                    if (producer) {
                        int n = r.nextInt(40);
                        float[] data = new float[n * channels + channels * 2];
                        for (int f = 0; f < n + 2; f++) {
                            for (int c = 0; c < channels; c++) {
                                // Numbers are unique frame/channel tokens, not physical float PCM amplitudes.
                                data[f * channels + c] = base + value++;
                            }
                        }
                        int expected = Math.min(n, capacity - oracle.size());
                        int actual = b.offer(epoch, data, 1, n, clock);
                        check(actual == expected, "accepted frames must equal remaining capacity");
                        for (int f = 0; f < expected; f++) {
                            float[] frame = new float[channels];
                            System.arraycopy(data, (1 + f) * channels, frame, 0, channels);
                            oracle.addLast(frame);
                        }
                        // Source buffer mutation after offer cannot affect queued audio.
                        for (int j = 0; j < data.length; j++) data[j] = -999f;
                    } else {
                        var lease = b.lease(epoch, 1 + r.nextInt(capacity + 3), clock);
                        if (lease != null) {
                            check(lease.frames <= oracle.size(), "lease cannot exceed queued oracle");
                            int f = 0;
                            for (float[] frame : oracle) {
                                if (f == lease.frames) break;
                                for (int c = 0; c < channels; c++) {
                                    expect(lease.interleavedPcm[f * channels + c], frame[c],
                                            "interleaved FIFO frame=" + f + " ch=" + c);
                                }
                                f++;
                            }
                            check(f == lease.frames, "full lease sampled");
                            int used = r.nextInt(lease.frames + 1);
                            clock++;
                            check(b.commitWritten(lease, used, clock), "partial write accepted");
                            check(!b.commitWritten(lease, used, clock), "duplicate commit rejected");
                            for (int k = 0; k < used; k++) oracle.removeFirst();
                        }
                    }
                    check(b.queuedFrames() == oracle.size(), "buffer occupancy matches oracle");
                }
                check(b.synthesisEnded(epoch, ++clock), "synthesis ended");
                while (!oracle.isEmpty()) {
                    var lease = b.lease(epoch, capacity, ++clock);
                    check(lease != null, "tail lease exists");
                    for (int f = 0; f < lease.frames; f++) {
                        float[] frame = oracle.removeFirst();
                        for (int c = 0; c < channels; c++) {
                            expect(lease.interleavedPcm[f * channels + c], frame[c], "tail frame ordering");
                        }
                    }
                    check(b.commitWritten(lease, lease.frames, ++clock), "tail write commits");
                }
                check(b.state() == VoicePcmWriteContinuityBuffer.State.COMPLETED,
                        "completion means accepted writes, not speaker played tail");
            }
        }
    }
    private static void pendingAndStop() {
        var b = new VoicePcmWriteContinuityBuffer(8, 10, 4, 30);
        long old = b.begin(0);
        float[] pcm = new float[8 * 10];
        for (int i = 0; i < pcm.length; i++) pcm[i] = i + 1;
        check(b.offer(old, pcm, 0, 10, 1) == 10, "8ch primed");
        var pending = b.lease(old, 10, 2);
        check(pending != null && pending.interleavedPcm.length == 80, "8ch lease");
        check(b.synthesisEnded(old, 3), "synthesis ends during in-flight write");
        check(b.state() == VoicePcmWriteContinuityBuffer.State.DRAINING, "pending write drain");
        check(b.interrupt(old), "interrupt before driver callback");
        check(!b.commitWritten(pending, 7, 4), "old in-flight return invalidated");
        long next = b.begin(5);
        check(next > old && b.queuedFrames() == 0, "restart fresh epoch");
        check(!b.tick(old, 6), "old periodic callback must not poison new epoch");
        check(b.offer(next, pcm, 0, 10, 6) == 10, "successor receives new data");
        var fresh = b.lease(next, 10, 7);
        check(fresh != null, "fresh lease");
        check(!b.commitWritten(pending, 10, 8), "stale lease denied even if counts plausible");
        check(b.queuedFrames() == 10, "new audio remains intact");
        check(b.commitWritten(fresh, 4, 9), "fresh partial accepted");
        check(!b.tick(next, 40), "writer progress timeout quarantines queue");
        check(b.state() == VoicePcmWriteContinuityBuffer.State.FAILED, "timeout fail closed");
        check(b.queuedFrames() == 0, "timeout discards unverified queued frames");
    }
    public static void main(String[] args) {
        fuzzChannels();
        pendingAndStop();
        System.out.println("VOICE_PCM_MULTICHANNEL_BACKPRESSURE_PASS checks=" + checks);
    }
}
