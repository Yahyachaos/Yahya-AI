#!/usr/bin/env python3
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
brain = root / "app/src/main/java/de/yahya/ai/CelineBrain.java"
graph = root / "app/src/main/java/de/yahya/ai/CelineGoalTaskGraph.java"

harness = r"""
package de.yahya.ai;
import java.util.*;

public final class GoalHarness {
    static final class Store implements CelineGoalTaskGraph.StateStore {
        String raw = "";
        @Override public String read() { return raw; }
        @Override public void write(String value) { raw = value; }
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Store store = new Store();
        CelineGoalTaskGraph g1 = new CelineGoalTaskGraph(store);

        CelineGoalTaskGraph.ResumeContext started =
                g1.observeUserRequest("Arbeite am Projekt Alpha weiter", 1000L);
        check(!started.continuation && started.hasActiveTask, "new goal");
        String goalId = started.goalId;
        String rootTask = started.taskId;
        check(!goalId.isEmpty() && !rootTask.isEmpty(), "stable ids created");

        String project = g1.upsertEntity("project", "Yahya AI", 1100L);
        String projectAgain = g1.upsertEntity("project", "Yahya AI", 1200L);
        check(project.equals(projectAgain), "entity id must be stable");
        String device = g1.upsertEntity("device", "S25 Ultra", 1300L);
        g1.linkEntities(project, "runs_on", device, 1400L);

        String child = g1.addTask(
                goalId, rootTask, Collections.singletonList(rootTask),
                "Nächsten sicheren Schritt ausführen", 1500L);
        g1.checkpointTask(
                child, CelineGoalTaskGraph.Status.BLOCKED,
                "Baseline bestätigt", "warte auf Build",
                "Build prüfen", "run-42", 1600L);

        check(store.raw.contains("V1"), "state serialized");

        CelineGoalTaskGraph g2 = new CelineGoalTaskGraph(store);
        check(g2.activeGoal().goalId.equals(goalId), "goal survives restart");
        check(g2.snapshot().activeTaskId.equals(child), "task survives restart");
        check(g2.snapshot().blocker.equals("warte auf Build"), "blocker survives restart");
        check(g2.entityLinks().size() == 1, "knowledge link survives restart");

        CelineGoalTaskGraph.ResumeContext resumed =
                g2.observeUserRequest("Dann mach weiter", 2000L);
        check(resumed.continuation && resumed.hasActiveTask, "continuation resumes");
        check(resumed.goalId.equals(goalId) && resumed.taskId.equals(child), "same ids after restart");
        check(resumed.promptContext().contains("Projekt Alpha"), "objective restored");
        check(resumed.promptContext().contains("warte auf Build"), "blocker restored");
        check(g2.promptContextFor("Wie geht es dir?").isEmpty(), "no unrelated task injection");
        check(g2.promptContextFor("weiter").contains("Nächste Aktion"), "continuation prompt");

        g2.checkpointTask(
                child, CelineGoalTaskGraph.Status.COMPLETED,
                "Build grün", "", "", "run-43", 2100L);
        CelineGoalTaskGraph g3 = new CelineGoalTaskGraph(store);
        check(g3.resumeContext().hasActiveTask
                && g3.resumeContext().taskId.equals(rootTask),
                "parent/root task resumes");

        g3.clear();
        CelineGoalTaskGraph g4 = new CelineGoalTaskGraph(store);
        check(g4.activeGoal().goalId.isEmpty()
                && g4.snapshot().activeTaskId.isEmpty(),
                "clear removes active state");

        System.out.println("celine-g1-goal-task-state PASS");
    }
}
"""

with tempfile.TemporaryDirectory(prefix="celine-g1-goal-") as td:
    td_path = Path(td)
    harness_file = td_path / "GoalHarness.java"
    harness_file.write_text(harness, encoding="utf-8")
    subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", td, str(brain), str(graph), str(harness_file)],
        check=True,
    )
    result = subprocess.run(
        ["java", "-cp", td, "de.yahya.ai.GoalHarness"],
        check=True,
        capture_output=True,
        text=True,
    )
    assert "celine-g1-goal-task-state PASS" in result.stdout

print("celine-g1-goal-task-state PASS")
