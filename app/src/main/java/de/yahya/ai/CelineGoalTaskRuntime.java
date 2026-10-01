package de.yahya.ai;

import android.content.SharedPreferences;

/**
 * Android-facing G1.3 bridge between conversation input and the app-owned Goal/Task graph.
 *
 * Persists only structured goal/task state. It never stores private free-form hidden reasoning.
 */
public final class CelineGoalTaskRuntime {
    static final String KEY_STATE = "celine_goal_task_g1_3_state";

    private final CelineGoalTaskGraph graph;

    public CelineGoalTaskRuntime(final SharedPreferences prefs) {
        if (prefs == null) throw new IllegalArgumentException("prefs must not be null");
        this.graph = new CelineGoalTaskGraph(new CelineGoalTaskGraph.StateStore() {
            @Override public String read() {
                String value = prefs.getString(KEY_STATE, "");
                return value == null ? "" : value;
            }

            @Override public void write(String value) {
                prefs.edit().putString(KEY_STATE, value == null ? "" : value).apply();
            }
        });
    }

    /**
     * Observe a non-local user turn before reasoning.
     *
     * A continuation resumes the existing stable goal/task. A correction keeps that
     * identity and promotes the user's newest correction to the next-action authority.
     * Otherwise the graph may create a new goal when the request is task-like.
     */
    public synchronized CelineGoalTaskGraph.ResumeContext observeUserText(String userText) {
        long now = System.currentTimeMillis();
        CelineGoalTaskGraph.ResumeContext current = graph.resumeContext();
        boolean correction = ConversationIntelligenceV78.looksLikeCorrection(userText);
        boolean continuation = CelineGoalTaskGraph.isContinuationRequest(userText)
                || ConversationIntelligenceV78.looksLikeFollowUp(userText);

        if (correction && current.hasActiveTask) {
            graph.checkpointTask(
                    current.taskId,
                    CelineGoalTaskGraph.Status.ACTIVE,
                    current.lastConfirmedStep,
                    "",
                    userText,
                    "",
                    now);
            return graph.resumeContext();
        }

        if (continuation && current.hasActiveTask) return current;
        return graph.observeUserRequest(userText, now);
    }

    /** Inject durable task state only for a true continuation/correction, never unrelated chat. */
    public synchronized String promptContext(String userText) {
        CelineGoalTaskGraph.ResumeContext current = graph.resumeContext();
        if (!current.hasActiveTask) return "";
        boolean continuation = CelineGoalTaskGraph.isContinuationRequest(userText)
                || ConversationIntelligenceV78.looksLikeFollowUp(userText)
                || ConversationIntelligenceV78.looksLikeCorrection(userText);
        return continuation ? current.promptContext() : "";
    }

    public synchronized CelineGoalTaskGraph.ResumeContext resumeContext() {
        return graph.resumeContext();
    }

    public synchronized CelineGoalTaskGraph graph() {
        return graph;
    }
}
