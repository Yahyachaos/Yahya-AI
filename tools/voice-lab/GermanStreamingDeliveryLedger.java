import java.util.List;
public final class GermanStreamingDeliveryLedger {
    public enum Phase { IDLE, READY, IN_FLIGHT, SUSPENDED, UNCERTAIN, COMPLETE }
    public enum Recovery {
        CONTINUE_WITH_NO_IN_FLIGHT,
        REPLAY_ONLY_IF_EXTERNALLY_PROVEN_SILENT,
        ADVANCE_ONLY_IF_EXTERNALLY_PROVEN_FULL_DRAIN
    }
    public static final class Ticket {
        public final long generation;
        public final int chunkIndex;
        public final String text;
        private Ticket(long generation, int chunkIndex, String text) {
            this.generation = generation;
            this.chunkIndex = chunkIndex;
            this.text = text;
        }
    }
    private long generation;
    private List<String> chunks = List.of();
    private int confirmed;
    private Ticket inFlight;
    private boolean synthesisFinished;
    private Phase phase = Phase.IDLE;
    public synchronized long begin(String text) {
        if (generation == Long.MAX_VALUE) throw new IllegalStateException("generation exhausted");
        List<String> newChunks = GermanSpeechChunkPlanner.split(text);
        String normalized = text == null ? "" : text.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
        if (!normalized.equals(String.join(" ", newChunks)))
            throw new IllegalStateException("German planner lost or rewrote text");
        generation++;
        chunks = newChunks;
        confirmed = 0;
        inFlight = null;
        synthesisFinished = false;
        phase = chunks.isEmpty() ? Phase.COMPLETE : Phase.READY;
        return generation;
    }
    public synchronized Ticket leaseNext(long id) {
        if (id != generation || phase != Phase.READY) return null;
        inFlight = new Ticket(id, confirmed, chunks.get(confirmed));
        synthesisFinished = false;
        phase = Phase.IN_FLIGHT;
        return inFlight;
    }
    public synchronized boolean synthesisEnded(Ticket ticket) {
        if (!isCurrentFlight(ticket)) return false;
        if (synthesisFinished) return false;
        synthesisFinished = true;
        return true;
    }
    public synchronized boolean playbackDrained(Ticket ticket) {
        if (!isCurrentFlight(ticket) || !synthesisFinished) return false;
        confirmed++;
        inFlight = null;
        synthesisFinished = false;
        phase = confirmed == chunks.size() ? Phase.COMPLETE : Phase.READY;
        return true;
    }
    public synchronized boolean stop(long id) {
        if (id != generation || !(phase == Phase.READY || phase == Phase.IN_FLIGHT)) return false;
        phase = inFlight == null ? Phase.SUSPENDED : Phase.UNCERTAIN;
        inFlight = null;
        synthesisFinished = false;
        return true;
    }
    public synchronized long restart(long id, Recovery decision) {
        if (id != generation || decision == null || generation == Long.MAX_VALUE)
            return -1L;
        if (phase == Phase.SUSPENDED) {
            if (decision != Recovery.CONTINUE_WITH_NO_IN_FLIGHT) return -1L;
        } else if (phase == Phase.UNCERTAIN) {
            if (decision == Recovery.CONTINUE_WITH_NO_IN_FLIGHT) return -1L;
            if (decision == Recovery.ADVANCE_ONLY_IF_EXTERNALLY_PROVEN_FULL_DRAIN)
                confirmed++;
        } else return -1L;
        generation++;
        inFlight = null;
        synthesisFinished = false;
        phase = confirmed == chunks.size() ? Phase.COMPLETE : Phase.READY;
        return generation;
    }
    private boolean isCurrentFlight(Ticket ticket) {
        return ticket != null && phase == Phase.IN_FLIGHT && inFlight == ticket
                && ticket.generation == generation && ticket.chunkIndex == confirmed;
    }
    public synchronized Phase phase() { return phase; }
    public synchronized long generation() { return generation; }
    public synchronized int confirmedChunks() { return confirmed; }
    public synchronized int chunkCount() { return chunks.size(); }
    public synchronized String normalizedPlanText() { return String.join(" ", chunks); }
    public synchronized String confirmedText() { return String.join(" ", chunks.subList(0, confirmed)); }
}
