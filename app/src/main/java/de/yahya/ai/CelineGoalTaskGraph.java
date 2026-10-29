package de.yahya.ai;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * G1.3 app-owned persistent working state, Goal/Task graph and minimal entity graph.
 *
 * The store persists structured state only. It never stores hidden free-form reasoning.
 */
public final class CelineGoalTaskGraph implements CelineWorkingState, CelineGoalGraph {
    public interface StateStore {
        String read();
        void write(String value);
    }

    public enum Status { ACTIVE, PAUSED, BLOCKED, COMPLETED, CANCELLED }

    public static final class ResumeContext {
        public final boolean continuation;
        public final boolean hasActiveTask;
        public final String goalId;
        public final String taskId;
        public final String objective;
        public final String taskObjective;
        public final String lastConfirmedStep;
        public final String blocker;
        public final String nextAction;

        private ResumeContext(
                boolean continuation,
                boolean hasActiveTask,
                String goalId,
                String taskId,
                String objective,
                String taskObjective,
                String lastConfirmedStep,
                String blocker,
                String nextAction) {
            this.continuation = continuation;
            this.hasActiveTask = hasActiveTask;
            this.goalId = clean(goalId);
            this.taskId = clean(taskId);
            this.objective = clean(objective);
            this.taskObjective = clean(taskObjective);
            this.lastConfirmedStep = clean(lastConfirmedStep);
            this.blocker = clean(blocker);
            this.nextAction = clean(nextAction);
        }

        public String promptContext() {
            if (!hasActiveTask) return "";
            StringBuilder out = new StringBuilder();
            out.append("Aktives Ziel: ").append(objective);
            if (!taskObjective.isEmpty() && !taskObjective.equals(objective)) {
                out.append("\nAktive Aufgabe: ").append(taskObjective);
            }
            if (!lastConfirmedStep.isEmpty()) out.append("\nLetzter bestätigter Schritt: ").append(lastConfirmedStep);
            if (!blocker.isEmpty()) out.append("\nBlocker: ").append(blocker);
            if (!nextAction.isEmpty()) out.append("\nNächste Aktion: ").append(nextAction);
            return out.toString();
        }

        static ResumeContext none(boolean continuation) {
            return new ResumeContext(continuation, false, "", "", "", "", "", "", "");
        }
    }

    public static final class GoalRecord {
        public final String id;
        public final String objective;
        public final Status status;
        public final String nextAction;
        public final String completionEvidence;
        public final long createdAtEpochMs;
        public final long updatedAtEpochMs;

        GoalRecord(String id, String objective, Status status, String nextAction,
                   String completionEvidence, long createdAtEpochMs, long updatedAtEpochMs) {
            this.id = clean(id);
            this.objective = bounded(objective, 320);
            this.status = status == null ? Status.ACTIVE : status;
            this.nextAction = bounded(nextAction, 320);
            this.completionEvidence = bounded(completionEvidence, 640);
            this.createdAtEpochMs = Math.max(0L, createdAtEpochMs);
            this.updatedAtEpochMs = Math.max(this.createdAtEpochMs, updatedAtEpochMs);
        }
    }

    public static final class TaskRecord {
        public final String id;
        public final String goalId;
        public final String parentTaskId;
        public final List<String> dependencyIds;
        public final String objective;
        public final Status status;
        public final String lastConfirmedStep;
        public final String blocker;
        public final String nextAction;
        public final String completionEvidence;
        public final long createdAtEpochMs;
        public final long updatedAtEpochMs;

        TaskRecord(String id, String goalId, String parentTaskId, List<String> dependencyIds,
                   String objective, Status status, String lastConfirmedStep, String blocker,
                   String nextAction, String completionEvidence,
                   long createdAtEpochMs, long updatedAtEpochMs) {
            this.id = clean(id);
            this.goalId = clean(goalId);
            this.parentTaskId = clean(parentTaskId);
            this.dependencyIds = immutableIds(dependencyIds);
            this.objective = bounded(objective, 320);
            this.status = status == null ? Status.ACTIVE : status;
            this.lastConfirmedStep = bounded(lastConfirmedStep, 320);
            this.blocker = bounded(blocker, 320);
            this.nextAction = bounded(nextAction, 320);
            this.completionEvidence = bounded(completionEvidence, 640);
            this.createdAtEpochMs = Math.max(0L, createdAtEpochMs);
            this.updatedAtEpochMs = Math.max(this.createdAtEpochMs, updatedAtEpochMs);
        }
    }

    public static final class EntityRecord {
        public final String id;
        public final String type;
        public final String label;
        public final long createdAtEpochMs;
        public final long updatedAtEpochMs;

        EntityRecord(String id, String type, String label, long createdAtEpochMs, long updatedAtEpochMs) {
            this.id = clean(id);
            this.type = bounded(type, 80);
            this.label = bounded(label, 240);
            this.createdAtEpochMs = Math.max(0L, createdAtEpochMs);
            this.updatedAtEpochMs = Math.max(this.createdAtEpochMs, updatedAtEpochMs);
        }
    }

    public static final class EntityLink {
        public final String sourceId;
        public final String relation;
        public final String targetId;
        public final long createdAtEpochMs;

        EntityLink(String sourceId, String relation, String targetId, long createdAtEpochMs) {
            this.sourceId = clean(sourceId);
            this.relation = bounded(relation, 80);
            this.targetId = clean(targetId);
            this.createdAtEpochMs = Math.max(0L, createdAtEpochMs);
        }
    }

    private static final int SCHEMA = 1;
    private final StateStore store;
    private State state;

    public CelineGoalTaskGraph(StateStore store) {
        if (store == null) throw new IllegalArgumentException("store must not be null");
        this.store = store;
        this.state = parse(store.read());
    }

    @Override
    public synchronized CelineWorkingSnapshot snapshot() {
        TaskRecord task = state.tasks.get(state.activeTaskId);
        if (task == null) return CelineWorkingSnapshot.empty();
        return new CelineWorkingSnapshot(
                task.id, task.lastConfirmedStep, task.blocker, task.updatedAtEpochMs);
    }

    @Override
    public synchronized void checkpoint(CelineWorkingSnapshot snapshot) {
        if (snapshot == null || clean(snapshot.activeTaskId).isEmpty()) return;
        TaskRecord current = state.tasks.get(snapshot.activeTaskId);
        if (current == null) return;
        long now = Math.max(current.updatedAtEpochMs, snapshot.updatedAtEpochMs);
        Status status = clean(snapshot.blocker).isEmpty()
                ? (current.status == Status.BLOCKED ? Status.ACTIVE : current.status)
                : Status.BLOCKED;
        TaskRecord updated = new TaskRecord(
                current.id, current.goalId, current.parentTaskId, current.dependencyIds,
                current.objective, status, snapshot.lastConfirmedStep, snapshot.blocker,
                current.nextAction, current.completionEvidence, current.createdAtEpochMs, now);
        state.tasks.put(updated.id, updated);
        touchGoalForTask(updated, now);
        persist();
    }

    @Override
    public synchronized CelineGoalSnapshot activeGoal() {
        GoalRecord goal = state.goals.get(state.activeGoalId);
        if (goal == null) return CelineGoalSnapshot.none();
        return new CelineGoalSnapshot(goal.id, goal.objective, goal.status.name(), goal.nextAction);
    }

    public synchronized ResumeContext observeUserRequest(String userText, long nowEpochMs) {
        String cleanText = bounded(userText, 320);
        if (cleanText.isEmpty()) return ResumeContext.none(false);
        boolean continuation = isContinuationRequest(cleanText);
        if (continuation) return resumeContext(true);
        if (!looksLikeGoalRequest(cleanText)) return ResumeContext.none(false);
        startGoal(cleanText, cleanText, nowEpochMs);
        return resumeContext(false);
    }

    public synchronized String startGoal(String objective, String firstTaskObjective, long nowEpochMs) {
        String cleanObjective = bounded(objective, 320);
        if (cleanObjective.isEmpty()) throw new IllegalArgumentException("objective must not be empty");
        pauseCurrent(nowEpochMs);
        String goalId = nextId("goal");
        String taskId = nextId("task");
        GoalRecord goal = new GoalRecord(
                goalId, cleanObjective, Status.ACTIVE, bounded(firstTaskObjective, 320),
                "", nowEpochMs, nowEpochMs);
        TaskRecord task = new TaskRecord(
                taskId, goalId, "", Collections.<String>emptyList(),
                bounded(firstTaskObjective, 320), Status.ACTIVE, "", "",
                bounded(firstTaskObjective, 320), "", nowEpochMs, nowEpochMs);
        state.goals.put(goalId, goal);
        state.tasks.put(taskId, task);
        state.activeGoalId = goalId;
        state.activeTaskId = taskId;
        persist();
        return goalId;
    }

    public synchronized String addTask(
            String goalId, String parentTaskId, List<String> dependencyIds,
            String objective, long nowEpochMs) {
        GoalRecord goal = state.goals.get(clean(goalId));
        if (goal == null) throw new IllegalArgumentException("unknown goalId");
        String cleanObjective = bounded(objective, 320);
        if (cleanObjective.isEmpty()) throw new IllegalArgumentException("objective must not be empty");
        for (String dep : immutableIds(dependencyIds)) {
            TaskRecord dependency = state.tasks.get(dep);
            if (dependency == null || !goal.id.equals(dependency.goalId)) {
                throw new IllegalArgumentException("dependency must belong to goal");
            }
        }
        String parent = clean(parentTaskId);
        if (!parent.isEmpty()) {
            TaskRecord parentTask = state.tasks.get(parent);
            if (parentTask == null || !goal.id.equals(parentTask.goalId)) {
                throw new IllegalArgumentException("parent task must belong to goal");
            }
        }
        String taskId = nextId("task");
        TaskRecord task = new TaskRecord(
                taskId, goal.id, parent, dependencyIds, cleanObjective,
                Status.ACTIVE, "", "", cleanObjective, "", nowEpochMs, nowEpochMs);
        state.tasks.put(taskId, task);
        state.activeGoalId = goal.id;
        state.activeTaskId = taskId;
        state.goals.put(goal.id, new GoalRecord(
                goal.id, goal.objective, Status.ACTIVE, cleanObjective,
                goal.completionEvidence, goal.createdAtEpochMs, nowEpochMs));
        persist();
        return taskId;
    }

    public synchronized void selectActiveTask(String taskId, long nowEpochMs) {
        TaskRecord task = state.tasks.get(clean(taskId));
        if (task == null) throw new IllegalArgumentException("unknown taskId");
        GoalRecord goal = state.goals.get(task.goalId);
        if (goal == null) throw new IllegalStateException("task has no goal");
        state.activeGoalId = goal.id;
        state.activeTaskId = task.id;
        if (goal.status == Status.PAUSED) {
            state.goals.put(goal.id, new GoalRecord(
                    goal.id, goal.objective, Status.ACTIVE, task.nextAction,
                    goal.completionEvidence, goal.createdAtEpochMs, nowEpochMs));
        }
        if (task.status == Status.PAUSED) {
            state.tasks.put(task.id, new TaskRecord(
                    task.id, task.goalId, task.parentTaskId, task.dependencyIds,
                    task.objective, Status.ACTIVE, task.lastConfirmedStep, task.blocker,
                    task.nextAction, task.completionEvidence, task.createdAtEpochMs, nowEpochMs));
        }
        persist();
    }

    public synchronized void checkpointTask(
            String taskId, Status status, String lastConfirmedStep, String blocker,
            String nextAction, String completionEvidence, long nowEpochMs) {
        TaskRecord current = state.tasks.get(clean(taskId));
        if (current == null) throw new IllegalArgumentException("unknown taskId");
        Status targetStatus = status == null ? current.status : status;
        TaskRecord updated = new TaskRecord(
                current.id, current.goalId, current.parentTaskId, current.dependencyIds,
                current.objective, targetStatus, lastConfirmedStep, blocker, nextAction,
                completionEvidence, current.createdAtEpochMs, nowEpochMs);
        state.tasks.put(updated.id, updated);
        touchGoalForTask(updated, nowEpochMs);
        if (targetStatus == Status.COMPLETED || targetStatus == Status.CANCELLED) {
            if (updated.id.equals(state.activeTaskId)) {
                TaskRecord replacement = newestRunnableTask(updated.goalId, updated.id);
                state.activeTaskId = replacement == null ? "" : replacement.id;
                if (replacement == null) {
                    GoalRecord goal = state.goals.get(updated.goalId);
                    if (goal != null) {
                        state.goals.put(goal.id, new GoalRecord(
                                goal.id, goal.objective,
                                targetStatus == Status.COMPLETED ? Status.COMPLETED : Status.CANCELLED,
                                "", completionEvidence, goal.createdAtEpochMs, nowEpochMs));
                        state.activeGoalId = "";
                    }
                }
            }
        }
        persist();
    }

    public synchronized ResumeContext resumeContext() {
        return resumeContext(false);
    }

    public synchronized String promptContextFor(String userText) {
        if (!isContinuationRequest(userText)) return "";
        return resumeContext(true).promptContext();
    }

    public synchronized GoalRecord goal(String goalId) {
        return state.goals.get(clean(goalId));
    }

    public synchronized TaskRecord task(String taskId) {
        return state.tasks.get(clean(taskId));
    }

    public synchronized String upsertEntity(String type, String label, long nowEpochMs) {
        String cleanType = bounded(type, 80);
        String cleanLabel = bounded(label, 240);
        if (cleanType.isEmpty() || cleanLabel.isEmpty()) throw new IllegalArgumentException("entity type/label required");
        String normalizedType = cleanType.toLowerCase(Locale.ROOT);
        String normalizedLabel = normalize(cleanLabel);
        for (EntityRecord entity : state.entities.values()) {
            if (entity.type.toLowerCase(Locale.ROOT).equals(normalizedType)
                    && normalize(entity.label).equals(normalizedLabel)) {
                return entity.id;
            }
        }
        String id = nextId("entity");
        state.entities.put(id, new EntityRecord(id, cleanType, cleanLabel, nowEpochMs, nowEpochMs));
        persist();
        return id;
    }

    public synchronized void linkEntities(
            String sourceId, String relation, String targetId, long nowEpochMs) {
        String source = clean(sourceId);
        String target = clean(targetId);
        String rel = bounded(relation, 80);
        if (!state.entities.containsKey(source) || !state.entities.containsKey(target)) {
            throw new IllegalArgumentException("entity link endpoint missing");
        }
        if (rel.isEmpty()) throw new IllegalArgumentException("relation required");
        for (EntityLink link : state.links) {
            if (link.sourceId.equals(source) && link.targetId.equals(target)
                    && link.relation.equalsIgnoreCase(rel)) return;
        }
        state.links.add(new EntityLink(source, rel, target, nowEpochMs));
        persist();
    }

    public synchronized List<EntityLink> entityLinks() {
        return Collections.unmodifiableList(new ArrayList<>(state.links));
    }

    public synchronized void clear() {
        state = new State();
        persist();
    }

    public static boolean isContinuationRequest(String text) {
        String n = normalize(text);
        if (n.isEmpty()) return false;
        return n.equals("weiter")
                || n.equals("mach weiter")
                || n.equals("mache weiter")
                || n.equals("dann weiter")
                || n.equals("dann mach weiter")
                || n.equals("weitermachen")
                || n.equals("weiter machen")
                || n.equals("fahr fort")
                || n.equals("fahre fort")
                || n.equals("mach da weiter")
                || n.equals("mache da weiter")
                || n.equals("mach dort weiter")
                || n.equals("mache dort weiter")
                || n.equals("setz fort")
                || n.equals("setze fort");
    }

    public static boolean looksLikeGoalRequest(String text) {
        String n = normalize(text);
        if (n.length() < 5 || isContinuationRequest(n)) return false;
        String[] prefixes = {
                "mach ", "mache ", "arbeite ", "bau ", "baue ", "pruf ", "pruef ",
                "prüf ", "erstelle ", "plane ", "find ", "finde ", "such ", "suche ",
                "reparier ", "repariere ", "schreib ", "schreibe ", "entwickel ",
                "entwickle ", "integrier ", "integriere ", "setz ", "setze ",
                "hilf mir ", "wir mussen ", "wir muessen ", "wir müssen ",
                "ich will ", "ich mochte ", "ich moechte ", "ich möchte ", "ziel "
        };
        for (String prefix : prefixes) if (n.startsWith(prefix)) return true;
        return false;
    }

    private ResumeContext resumeContext(boolean continuation) {
        GoalRecord goal = state.goals.get(state.activeGoalId);
        TaskRecord task = state.tasks.get(state.activeTaskId);
        if (goal == null || task == null) return ResumeContext.none(continuation);
        return new ResumeContext(
                continuation, true, goal.id, task.id, goal.objective, task.objective,
                task.lastConfirmedStep, task.blocker,
                task.nextAction.isEmpty() ? goal.nextAction : task.nextAction);
    }

    private void pauseCurrent(long nowEpochMs) {
        GoalRecord goal = state.goals.get(state.activeGoalId);
        if (goal != null && goal.status == Status.ACTIVE) {
            state.goals.put(goal.id, new GoalRecord(
                    goal.id, goal.objective, Status.PAUSED, goal.nextAction,
                    goal.completionEvidence, goal.createdAtEpochMs, nowEpochMs));
        }
        TaskRecord task = state.tasks.get(state.activeTaskId);
        if (task != null && task.status == Status.ACTIVE) {
            state.tasks.put(task.id, new TaskRecord(
                    task.id, task.goalId, task.parentTaskId, task.dependencyIds,
                    task.objective, Status.PAUSED, task.lastConfirmedStep, task.blocker,
                    task.nextAction, task.completionEvidence, task.createdAtEpochMs, nowEpochMs));
        }
    }

    private void touchGoalForTask(TaskRecord task, long nowEpochMs) {
        GoalRecord goal = state.goals.get(task.goalId);
        if (goal == null) return;
        Status goalStatus = task.status == Status.BLOCKED ? Status.BLOCKED
                : (goal.status == Status.BLOCKED && task.status == Status.ACTIVE ? Status.ACTIVE : goal.status);
        state.goals.put(goal.id, new GoalRecord(
                goal.id, goal.objective, goalStatus, task.nextAction,
                goal.completionEvidence, goal.createdAtEpochMs, nowEpochMs));
    }

    private TaskRecord newestRunnableTask(String goalId, String excludedTaskId) {
        TaskRecord best = null;
        for (TaskRecord task : state.tasks.values()) {
            if (!task.goalId.equals(goalId) || task.id.equals(excludedTaskId)) continue;
            if (task.status != Status.ACTIVE && task.status != Status.BLOCKED && task.status != Status.PAUSED) continue;
            if (best == null || task.updatedAtEpochMs > best.updatedAtEpochMs) best = task;
        }
        return best;
    }

    private String nextId(String prefix) {
        state.sequence++;
        return prefix + "-" + Long.toString(state.sequence, 36);
    }

    private void persist() {
        store.write(serialize(state));
    }

    private static final class State {
        long sequence = 0L;
        String activeGoalId = "";
        String activeTaskId = "";
        final LinkedHashMap<String, GoalRecord> goals = new LinkedHashMap<>();
        final LinkedHashMap<String, TaskRecord> tasks = new LinkedHashMap<>();
        final LinkedHashMap<String, EntityRecord> entities = new LinkedHashMap<>();
        final ArrayList<EntityLink> links = new ArrayList<>();
    }

    private static String serialize(State state) {
        StringBuilder out = new StringBuilder();
        out.append("V").append(SCHEMA).append('\t')
                .append(state.sequence).append('\t')
                .append(state.activeGoalId).append('\t')
                .append(state.activeTaskId).append('\n');
        for (GoalRecord goal : state.goals.values()) {
            out.append("G\t").append(goal.id).append('\t')
                    .append(enc(goal.objective)).append('\t')
                    .append(goal.status.name()).append('\t')
                    .append(enc(goal.nextAction)).append('\t')
                    .append(enc(goal.completionEvidence)).append('\t')
                    .append(goal.createdAtEpochMs).append('\t')
                    .append(goal.updatedAtEpochMs).append('\n');
        }
        for (TaskRecord task : state.tasks.values()) {
            out.append("T\t").append(task.id).append('\t')
                    .append(task.goalId).append('\t')
                    .append(task.parentTaskId).append('\t')
                    .append(enc(join(task.dependencyIds))).append('\t')
                    .append(enc(task.objective)).append('\t')
                    .append(task.status.name()).append('\t')
                    .append(enc(task.lastConfirmedStep)).append('\t')
                    .append(enc(task.blocker)).append('\t')
                    .append(enc(task.nextAction)).append('\t')
                    .append(enc(task.completionEvidence)).append('\t')
                    .append(task.createdAtEpochMs).append('\t')
                    .append(task.updatedAtEpochMs).append('\n');
        }
        for (EntityRecord entity : state.entities.values()) {
            out.append("E\t").append(entity.id).append('\t')
                    .append(enc(entity.type)).append('\t')
                    .append(enc(entity.label)).append('\t')
                    .append(entity.createdAtEpochMs).append('\t')
                    .append(entity.updatedAtEpochMs).append('\n');
        }
        for (EntityLink link : state.links) {
            out.append("L\t").append(link.sourceId).append('\t')
                    .append(enc(link.relation)).append('\t')
                    .append(link.targetId).append('\t')
                    .append(link.createdAtEpochMs).append('\n');
        }
        return out.toString();
    }

    private static State parse(String raw) {
        State state = new State();
        if (raw == null || raw.trim().isEmpty()) return state;
        try {
            String[] lines = raw.split("\\n");
            if (lines.length == 0) return state;
            String[] header = lines[0].split("\\t", -1);
            if (header.length < 4 || !("V" + SCHEMA).equals(header[0])) return new State();
            state.sequence = parseLong(header[1]);
            state.activeGoalId = clean(header[2]);
            state.activeTaskId = clean(header[3]);
            for (int i = 1; i < lines.length; i++) {
                String[] p = lines[i].split("\\t", -1);
                if (p.length == 0) continue;
                if ("G".equals(p[0]) && p.length >= 8) {
                    GoalRecord goal = new GoalRecord(
                            p[1], dec(p[2]), status(p[3]), dec(p[4]), dec(p[5]),
                            parseLong(p[6]), parseLong(p[7]));
                    if (!goal.id.isEmpty()) state.goals.put(goal.id, goal);
                } else if ("T".equals(p[0]) && p.length >= 13) {
                    TaskRecord task = new TaskRecord(
                            p[1], p[2], p[3], splitIds(dec(p[4])), dec(p[5]), status(p[6]),
                            dec(p[7]), dec(p[8]), dec(p[9]), dec(p[10]),
                            parseLong(p[11]), parseLong(p[12]));
                    if (!task.id.isEmpty()) state.tasks.put(task.id, task);
                } else if ("E".equals(p[0]) && p.length >= 6) {
                    EntityRecord entity = new EntityRecord(
                            p[1], dec(p[2]), dec(p[3]), parseLong(p[4]), parseLong(p[5]));
                    if (!entity.id.isEmpty()) state.entities.put(entity.id, entity);
                } else if ("L".equals(p[0]) && p.length >= 5) {
                    state.links.add(new EntityLink(p[1], dec(p[2]), p[3], parseLong(p[4])));
                }
            }
            if (!state.goals.containsKey(state.activeGoalId)) state.activeGoalId = "";
            if (!state.tasks.containsKey(state.activeTaskId)) state.activeTaskId = "";
            return state;
        } catch (Exception invalid) {
            return new State();
        }
    }

    private static Status status(String raw) {
        try { return Status.valueOf(raw); }
        catch (Exception ignored) { return Status.PAUSED; }
    }

    private static long parseLong(String raw) {
        try { return Math.max(0L, Long.parseLong(raw)); }
        catch (Exception ignored) { return 0L; }
    }

    private static List<String> immutableIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        ArrayList<String> out = new ArrayList<>();
        for (String id : ids) {
            String clean = clean(id);
            if (!clean.isEmpty() && !out.contains(clean)) out.add(clean);
        }
        return Collections.unmodifiableList(out);
    }

    private static List<String> splitIds(String raw) {
        if (raw == null || raw.isEmpty()) return Collections.emptyList();
        ArrayList<String> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String id = clean(part);
            if (!id.isEmpty()) out.add(id);
        }
        return out;
    }

    private static String join(List<String> values) {
        if (values == null || values.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (out.length() > 0) out.append(',');
            out.append(clean(value));
        }
        return out.toString();
    }

    private static String enc(String value) {
        byte[] bytes = clean(value).getBytes(StandardCharsets.UTF_8);
        char[] hex = new char[bytes.length * 2];
        final char[] digits = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xff;
            hex[i * 2] = digits[v >>> 4];
            hex[i * 2 + 1] = digits[v & 0x0f];
        }
        return new String(hex);
    }

    private static String dec(String value) {
        String hex = clean(value);
        if ((hex.length() & 1) != 0) return "";
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) return "";
            bytes[i] = (byte) ((hi << 4) | lo);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String normalize(String value) {
        String n = clean(value).toLowerCase(Locale.GERMANY)
                .replace('ä', 'a').replace('ö', 'o').replace('ü', 'u').replace('ß', 's');
        n = n.replaceAll("[^a-z0-9 ]+", " ").replaceAll("\\s+", " ").trim();
        return n;
    }

    private static String bounded(String value, int maxChars) {
        String out = clean(value);
        return out.length() <= maxChars ? out : out.substring(0, maxChars);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }
}
