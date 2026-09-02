#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
store = root / "app/src/main/java/de/yahya/ai/CelineStructuredMemory.java"
main = root / "app/src/main/java/de/yahya/ai/MainActivity.java"
text = store.read_text(encoding="utf-8")
main_text = main.read_text(encoding="utf-8")

required = [
    'KEY_GOAL_TASK_STATE = "celine_goal_task_state_v1"',
    "private final CelineGoalTaskGraph goalTaskGraph",
    "new CelineGoalTaskGraph(new CelineGoalTaskGraph.StateStore()",
    "putString(KEY_GOAL_TASK_STATE",
    "if (isContinuationIntent(clean)) return;",
    "if (looksLikeDurableGoal(clean)) goalTaskGraph.observeUserRequest(clean, now);",
    "String workingContext = goalTaskGraph.resumeContext().promptContext();",
    'out.append("AKTIVER_ARBEITSSTAND:\\n")',
    "public synchronized CelineGoalTaskGraph goalTaskGraph()",
    "goalTaskGraph.clear();",
    ".remove(KEY_GOAL_TASK_STATE)",
]
for token in required:
    assert token in text, f"missing G1.3 live goal/task integration: {token}"

# Working-state context must stay out of unrelated turns and out of MainActivity ownership.
assert "if (isContinuationIntent(clean)) {" in text
assert "AKTIVER_ARBEITSSTAND" not in main_text, "MainActivity must not own G1.3 working context"
assert "CelineGoalTaskGraph" not in main_text, "MainActivity must not directly own G1.3 graph"

# Conservative live goal capture: short utility/chat imperatives must not become durable goals.
durable = text.split("private static boolean looksLikeDurableGoal", 1)[1].split("private static boolean looksSensitive", 1)[0]
for noisy_prefix in ['value.startsWith("schreib ")', 'value.startsWith("schreibe ")',
                     'value.startsWith("such ")', 'value.startsWith("suche ")',
                     'value.startsWith("find ")', 'value.startsWith("finde ")',
                     'value.startsWith("mach ")', 'value.startsWith("mache ")']:
    assert noisy_prefix not in durable, f"over-broad durable goal trigger: {noisy_prefix}"

# Existing G1.2 structured-memory path remains the only live memory owner.
for forbidden in ['prefs.getString("memory"', 'prefs.edit().remove("memory")', "if(c.length()>7000)"]:
    assert forbidden not in main_text, f"legacy memory path returned: {forbidden}"

print("celine-g1-goal-task-live-contract PASS")
