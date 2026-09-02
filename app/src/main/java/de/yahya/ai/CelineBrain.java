package de.yahya.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * App-owned cognitive boundary for Celine.
 *
 * G1 defines ownership and typed contracts before all live behavior is migrated
 * out of MainActivity. No cloud vendor, Android UI class or concrete model belongs
 * in this contract.
 */
public final class CelineBrain {
    private final Dependencies dependencies;

    public CelineBrain(Dependencies dependencies) {
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
    }

    public Dependencies dependencies() {
        return dependencies;
    }

    public static final class Dependencies {
        public final CelineMemory memory;
        public final CelineWorkingState workingState;
        public final CelineGoalGraph goalGraph;
        public final CelineContextBroker contextBroker;
        public final CelineToolRegistry toolRegistry;
        public final CelinePermissionPolicy permissionPolicy;
        public final CelineReasoningProvider reasoningProvider;
        public final CelineVerifier verifier;
        public final CelineLearningEngine learningEngine;
        public final CelineAffectState affectState;
        public final CelineResourcePolicy resourcePolicy;

        public Dependencies(
                CelineMemory memory,
                CelineWorkingState workingState,
                CelineGoalGraph goalGraph,
                CelineContextBroker contextBroker,
                CelineToolRegistry toolRegistry,
                CelinePermissionPolicy permissionPolicy,
                CelineReasoningProvider reasoningProvider,
                CelineVerifier verifier,
                CelineLearningEngine learningEngine,
                CelineAffectState affectState,
                CelineResourcePolicy resourcePolicy) {
            this.memory = Objects.requireNonNull(memory, "memory");
            this.workingState = Objects.requireNonNull(workingState, "workingState");
            this.goalGraph = Objects.requireNonNull(goalGraph, "goalGraph");
            this.contextBroker = Objects.requireNonNull(contextBroker, "contextBroker");
            this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
            this.permissionPolicy = Objects.requireNonNull(permissionPolicy, "permissionPolicy");
            this.reasoningProvider = Objects.requireNonNull(reasoningProvider, "reasoningProvider");
            this.verifier = Objects.requireNonNull(verifier, "verifier");
            this.learningEngine = Objects.requireNonNull(learningEngine, "learningEngine");
            this.affectState = Objects.requireNonNull(affectState, "affectState");
            this.resourcePolicy = Objects.requireNonNull(resourcePolicy, "resourcePolicy");
        }
    }
}

final class CelineBrainRequest {
    final String requestId;
    final String userText;
    final long createdAtEpochMs;

    CelineBrainRequest(String requestId, String userText, long createdAtEpochMs) {
        this.requestId = requireText(requestId, "requestId");
        this.userText = requireText(userText, "userText");
        this.createdAtEpochMs = createdAtEpochMs;
    }

    private static String requireText(String value, String name) {
        String out = value == null ? "" : value.trim();
        if (out.isEmpty()) throw new IllegalArgumentException(name + " must not be empty");
        return out;
    }
}

final class CelineBrainFrame {
    final CelineBrainRequest request;
    final CelineMemorySlice memory;
    final CelineWorkingSnapshot workingState;
    final CelineGoalSnapshot activeGoal;
    final CelineContextSnapshot context;
    final CelineAffectSnapshot affect;
    final CelineResourceDecision resources;
    final List<CelineToolDescriptor> tools;

    CelineBrainFrame(
            CelineBrainRequest request,
            CelineMemorySlice memory,
            CelineWorkingSnapshot workingState,
            CelineGoalSnapshot activeGoal,
            CelineContextSnapshot context,
            CelineAffectSnapshot affect,
            CelineResourceDecision resources,
            List<CelineToolDescriptor> tools) {
        this.request = Objects.requireNonNull(request, "request");
        this.memory = Objects.requireNonNull(memory, "memory");
        this.workingState = Objects.requireNonNull(workingState, "workingState");
        this.activeGoal = Objects.requireNonNull(activeGoal, "activeGoal");
        this.context = Objects.requireNonNull(context, "context");
        this.affect = Objects.requireNonNull(affect, "affect");
        this.resources = Objects.requireNonNull(resources, "resources");
        this.tools = immutableCopy(tools);
    }

    private static <T> List<T> immutableCopy(List<T> source) {
        if (source == null || source.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(source));
    }
}

final class CelineBrainResult {
    enum Kind { ANSWER, CLARIFICATION_REQUIRED, TOOL_PLAN, BLOCKED, FAILED }
    final Kind kind;
    final String responseText;
    final double confidence;
    final String providerId;

    CelineBrainResult(Kind kind, String responseText, double confidence, String providerId) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.responseText = responseText == null ? "" : responseText;
        this.confidence = Math.max(0.0d, Math.min(1.0d, confidence));
        this.providerId = providerId == null ? "" : providerId.trim();
    }
}

interface CelineMemory {
    CelineMemorySlice retrieve(CelineBrainRequest request, int maxItems);
    void remember(CelineMemoryMutation mutation);
}

enum CelineMemoryType {
    SEMANTIC, PROFILE, PREFERENCE, EPISODIC, DECISION_CORRECTION,
    OPEN_GOAL_TASK, PROCEDURAL_SKILL, TEMPORARY, LEGACY
}

enum CelineMemoryPrivacy {
    LOCAL_PRIVATE, LOCAL_SENSITIVE, SHAREABLE_ON_REQUEST
}

final class CelineMemorySlice {
    final List<CelineMemoryItem> items;
    CelineMemorySlice(List<CelineMemoryItem> items) {
        this.items = items == null || items.isEmpty()
                ? Collections.<CelineMemoryItem>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(items));
    }
    static CelineMemorySlice empty() {
        return new CelineMemorySlice(Collections.<CelineMemoryItem>emptyList());
    }
}

final class CelineMemoryItem {
    enum KnowledgeState { OBSERVED, EXPLICIT, INFERRED, UNKNOWN }

    final String id;
    final CelineMemoryType type;
    final String summary;
    final String provenance;
    final KnowledgeState knowledgeState;
    final double confidence;
    final double importance;
    final CelineMemoryPrivacy privacyScope;
    final long createdAtEpochMs;
    final long updatedAtEpochMs;
    final long expiresAtEpochMs;
    final String supersedesId;
    final String conflictWithId;

    CelineMemoryItem(
            String id, CelineMemoryType type, String summary, String provenance,
            KnowledgeState knowledgeState, double confidence, double importance,
            CelineMemoryPrivacy privacyScope, long createdAtEpochMs, long updatedAtEpochMs,
            long expiresAtEpochMs, String supersedesId, String conflictWithId) {
        this.id = clean(id);
        this.type = Objects.requireNonNull(type, "type");
        this.summary = clean(summary);
        this.provenance = clean(provenance);
        this.knowledgeState = Objects.requireNonNull(knowledgeState, "knowledgeState");
        this.confidence = clampUnit(confidence);
        this.importance = clampUnit(importance);
        this.privacyScope = Objects.requireNonNull(privacyScope, "privacyScope");
        this.createdAtEpochMs = createdAtEpochMs;
        this.updatedAtEpochMs = updatedAtEpochMs;
        this.expiresAtEpochMs = Math.max(0L, expiresAtEpochMs);
        this.supersedesId = clean(supersedesId);
        this.conflictWithId = clean(conflictWithId);
    }

    boolean isExpired(long nowEpochMs) {
        return expiresAtEpochMs > 0L && expiresAtEpochMs <= nowEpochMs;
    }

    CelineMemoryItem withSupersedes(String targetId) {
        return new CelineMemoryItem(
                id, type, summary, provenance, knowledgeState, confidence, importance,
                privacyScope, createdAtEpochMs, updatedAtEpochMs, expiresAtEpochMs,
                targetId, conflictWithId);
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static double clampUnit(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }
}

final class CelineMemoryMutation {
    enum Operation { UPSERT, SUPERSEDE, FORGET }
    final Operation operation;
    final String targetId;
    final CelineMemoryItem item;
    CelineMemoryMutation(Operation operation, String targetId, CelineMemoryItem item) {
        this.operation = Objects.requireNonNull(operation, "operation");
        this.targetId = targetId == null ? "" : targetId.trim();
        this.item = item;
    }
}

interface CelineWorkingState {
    CelineWorkingSnapshot snapshot();
    void checkpoint(CelineWorkingSnapshot snapshot);
}

final class CelineWorkingSnapshot {
    final String activeTaskId;
    final String lastConfirmedStep;
    final String blocker;
    final long updatedAtEpochMs;
    CelineWorkingSnapshot(String activeTaskId, String lastConfirmedStep, String blocker, long updatedAtEpochMs) {
        this.activeTaskId = activeTaskId == null ? "" : activeTaskId;
        this.lastConfirmedStep = lastConfirmedStep == null ? "" : lastConfirmedStep;
        this.blocker = blocker == null ? "" : blocker;
        this.updatedAtEpochMs = updatedAtEpochMs;
    }
    static CelineWorkingSnapshot empty() { return new CelineWorkingSnapshot("", "", "", 0L); }
}

interface CelineGoalGraph { CelineGoalSnapshot activeGoal(); }

final class CelineGoalSnapshot {
    final String goalId;
    final String objective;
    final String status;
    final String nextAction;
    CelineGoalSnapshot(String goalId, String objective, String status, String nextAction) {
        this.goalId = goalId == null ? "" : goalId;
        this.objective = objective == null ? "" : objective;
        this.status = status == null ? "" : status;
        this.nextAction = nextAction == null ? "" : nextAction;
    }
    static CelineGoalSnapshot none() { return new CelineGoalSnapshot("", "", "NONE", ""); }
}

interface CelineContextBroker { CelineContextSnapshot collect(CelineBrainRequest request); }

final class CelineContextSnapshot {
    final List<CelineContextFact> facts;
    CelineContextSnapshot(List<CelineContextFact> facts) {
        this.facts = facts == null || facts.isEmpty()
                ? Collections.<CelineContextFact>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(facts));
    }
    static CelineContextSnapshot empty() {
        return new CelineContextSnapshot(Collections.<CelineContextFact>emptyList());
    }
}

final class CelineContextFact {
    final String source;
    final String value;
    final long observedAtEpochMs;
    final boolean fresh;
    CelineContextFact(String source, String value, long observedAtEpochMs, boolean fresh) {
        this.source = source == null ? "" : source;
        this.value = value == null ? "" : value;
        this.observedAtEpochMs = observedAtEpochMs;
        this.fresh = fresh;
    }
}

interface CelineToolRegistry {
    List<CelineToolDescriptor> availableTools();
    CelineToolResult execute(CelineToolIntent intent);
}

final class CelineToolDescriptor {
    final String id;
    final String description;
    final CelineRiskClass riskClass;
    CelineToolDescriptor(String id, String description, CelineRiskClass riskClass) {
        this.id = id == null ? "" : id;
        this.description = description == null ? "" : description;
        this.riskClass = Objects.requireNonNull(riskClass, "riskClass");
    }
}

final class CelineToolIntent {
    final String toolId;
    final String target;
    final String payload;
    CelineToolIntent(String toolId, String target, String payload) {
        this.toolId = toolId == null ? "" : toolId;
        this.target = target == null ? "" : target;
        this.payload = payload == null ? "" : payload;
    }
}

final class CelineToolResult {
    final boolean success;
    final String observedResult;
    final String errorCode;
    CelineToolResult(boolean success, String observedResult, String errorCode) {
        this.success = success;
        this.observedResult = observedResult == null ? "" : observedResult;
        this.errorCode = errorCode == null ? "" : errorCode;
    }
}

enum CelineRiskClass { L0_READ_ONLY, L1_REVERSIBLE_LOCAL, L2_EXTERNAL_STATE_CHANGE, L3_HIGH_IMPACT }

interface CelinePermissionPolicy {
    CelinePermissionDecision evaluate(CelineToolIntent intent, CelineRiskClass riskClass);
}

final class CelinePermissionDecision {
    enum State { ALLOW, REQUIRE_CONFIRMATION, DENY }
    final State state;
    final String reason;
    CelinePermissionDecision(State state, String reason) {
        this.state = Objects.requireNonNull(state, "state");
        this.reason = reason == null ? "" : reason;
    }
}

interface CelineReasoningProvider {
    String providerId();
    CelineBrainResult reason(CelineBrainFrame frame) throws Exception;
}

interface CelineVerifier {
    CelineVerification verify(CelineBrainFrame frame, CelineBrainResult candidate);
}

final class CelineVerification {
    final boolean accepted;
    final String evidence;
    final String requiredRecovery;
    CelineVerification(boolean accepted, String evidence, String requiredRecovery) {
        this.accepted = accepted;
        this.evidence = evidence == null ? "" : evidence;
        this.requiredRecovery = requiredRecovery == null ? "" : requiredRecovery;
    }
}

interface CelineLearningEngine { void observe(CelineLearningEvent event); }

final class CelineLearningEvent {
    final String requestId;
    final String outcome;
    final boolean verified;
    final String correction;
    CelineLearningEvent(String requestId, String outcome, boolean verified, String correction) {
        this.requestId = requestId == null ? "" : requestId;
        this.outcome = outcome == null ? "" : outcome;
        this.verified = verified;
        this.correction = correction == null ? "" : correction;
    }
}

interface CelineAffectState { CelineAffectSnapshot snapshot(); }

final class CelineAffectSnapshot {
    final double valence;
    final double arousal;
    final double warmth;
    final double confidence;
    CelineAffectSnapshot(double valence, double arousal, double warmth, double confidence) {
        this.valence = Math.max(-1.0d, Math.min(1.0d, valence));
        this.arousal = Math.max(0.0d, Math.min(1.0d, arousal));
        this.warmth = Math.max(0.0d, Math.min(1.0d, warmth));
        this.confidence = Math.max(0.0d, Math.min(1.0d, confidence));
    }
}

interface CelineResourcePolicy { CelineResourceDecision route(CelineBrainRequest request); }

final class CelineResourceDecision {
    enum Route { DETERMINISTIC, LOCAL_FAST, LOCAL_DEEP, EXTERNAL_OPTIONAL, UNAVAILABLE }
    final Route route;
    final String reason;
    CelineResourceDecision(Route route, String reason) {
        this.route = Objects.requireNonNull(route, "route");
        this.reason = reason == null ? "" : reason;
    }
}
