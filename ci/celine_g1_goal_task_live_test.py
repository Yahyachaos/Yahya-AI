#!/usr/bin/env python3
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
brain = root / "app/src/main/java/de/yahya/ai/CelineBrain.java"
conversation = root / "app/src/main/java/de/yahya/ai/ConversationIntelligenceV78.java"
graph = root / "app/src/main/java/de/yahya/ai/CelineGoalTaskGraph.java"
runtime = root / "app/src/main/java/de/yahya/ai/CelineGoalTaskRuntime.java"
main = root / "app/src/main/java/de/yahya/ai/MainActivity.java"

for path in (brain, conversation, graph, runtime, main):
    assert path.is_file(), f"missing G1.3 live file: {path}"

runtime_text = runtime.read_text(encoding="utf-8")
main_text = main.read_text(encoding="utf-8")

for token in [
    'KEY_STATE = "celine_goal_task_g1_3_state"',
    "CelineGoalTaskGraph.StateStore",
    "observeUserText",
    "promptContext",
    "looksLikeCorrection",
    "looksLikeFollowUp",
]:
    assert token in runtime_text, f"missing G1.3 runtime behavior: {token}"

# G1.4 moved reasoning-context assembly into CelineContextBrokerG14. G1.3 is
# still live when MainActivity observes every non-local turn and passes the same
# persistent graph to the broker as working-state and goal authorities.
for token in [
    "CelineGoalTaskRuntime goalTaskRuntime",
    "goalTaskRuntime=new CelineGoalTaskRuntime(prefs)",
    "goalTaskRuntime.observeUserText(text)",
    "new CelineContextBrokerG14(memory,goalTaskRuntime.graph(),goalTaskRuntime.graph())",
    "contextBroker.promptContext(brainRequest,device.status(),!key.isEmpty())",
]:
    assert token in main_text, f"missing G1.3 live integration through G1.4 broker: {token}"

assert "goalTaskRuntime.promptContext(userText)" not in main_text, \
    "MainActivity bypasses G1.4 broker with direct task prompt assembly"

for forbidden in [
    "chainofthought",
    "chain_of_thought",
    "hidden_reasoning",
    "private_reasoning",
]:
    assert forbidden not in runtime_text.lower(), f"private reasoning persistence token leaked: {forbidden}"

shared_preferences_stub = r'''
package android.content;
public interface SharedPreferences {
    String getString(String key, String defValue);
    Editor edit();
    interface Editor {
        Editor putString(String key, String value);
        void apply();
    }
}
'''

harness = r'''
package de.yahya.ai;

import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.Map;

public final class GoalLiveHarness {
    static final class Prefs implements SharedPreferences {
        final Map<String,String> values = new HashMap<>();
        @Override public String getString(String key, String defValue) {
            String value = values.get(key);
            return value == null ? defValue : value;
        }
        @Override public Editor edit() {
            return new Editor() {
                final Map<String,String> pending = new HashMap<>();
                @Override public Editor putString(String key, String value) {
                    pending.put(key, value);
                    return this;
                }
                @Override public void apply() { values.putAll(pending); }
            };
        }
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Prefs prefs = new Prefs();
        CelineGoalTaskRuntime first = new CelineGoalTaskRuntime(prefs);
        CelineGoalTaskGraph.ResumeContext started =
                first.observeUserText("Arbeite am Projekt Alpha weiter");
        check(started.hasActiveTask, "new goal not captured");
        String goalId = started.goalId;
        String taskId = started.taskId;

        CelineGoalTaskRuntime afterRestart = new CelineGoalTaskRuntime(prefs);
        String resumedPrompt = afterRestart.promptContext("weiter");
        check(resumedPrompt.contains("Projekt Alpha"), "goal missing after restart");
        CelineGoalTaskGraph.ResumeContext resumed = afterRestart.observeUserText("weiter");
        check(resumed.goalId.equals(goalId), "goal id changed after restart");
        check(resumed.taskId.equals(taskId), "task id changed after restart");
        check(afterRestart.promptContext("Wie geht es dir?").isEmpty(),
                "unrelated chat received task context");

        CelineGoalTaskGraph.ResumeContext corrected =
                afterRestart.observeUserText("Nein, prüfe zuerst den Build");
        check(corrected.goalId.equals(goalId) && corrected.taskId.equals(taskId),
                "correction replaced active goal/task");
        check(corrected.nextAction.contains("prüfe zuerst den Build"),
                "correction not promoted to next action");
        check(afterRestart.promptContext("Und jetzt?").contains("prüfe zuerst den Build"),
                "follow-up did not recover corrected next action");

        System.out.println("celine-g1-goal-task-live-contract PASS");
    }
}
'''

with tempfile.TemporaryDirectory(prefix="celine-g13-live-") as td:
    td_path = Path(td)
    stub_dir = td_path / "android/content"
    stub_dir.mkdir(parents=True)
    stub = stub_dir / "SharedPreferences.java"
    stub.write_text(shared_preferences_stub, encoding="utf-8")
    harness_file = td_path / "GoalLiveHarness.java"
    harness_file.write_text(harness, encoding="utf-8")

    subprocess.run([
        "javac", "-encoding", "UTF-8", "-d", td,
        str(stub), str(brain), str(conversation), str(graph), str(runtime), str(harness_file),
    ], check=True)
    result = subprocess.run(
        ["java", "-cp", td, "de.yahya.ai.GoalLiveHarness"],
        check=True, capture_output=True, text=True)
    assert "celine-g1-goal-task-live-contract PASS" in result.stdout

print("celine-g1-goal-task-live-contract PASS")
