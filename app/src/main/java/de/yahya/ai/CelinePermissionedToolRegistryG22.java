package de.yahya.ai;

import java.util.List;

/**
 * G2.2 permission-enforcing registry. All brain/planner tool execution must pass here.
 * The underlying G2.1 cortex remains the typed deterministic executor only.
 */
public final class CelinePermissionedToolRegistryG22 implements CelineToolRegistry {
    private final CelineToolCortexG21 cortex;
    private final CelinePermissionPolicyG22 policy;

    public CelinePermissionedToolRegistryG22(CelineToolCortexG21 cortex,
                                             CelinePermissionPolicyG22 policy) {
        if (cortex == null) throw new IllegalArgumentException("cortex must not be null");
        if (policy == null) throw new IllegalArgumentException("policy must not be null");
        this.cortex = cortex;
        this.policy = policy;
    }

    @Override
    public List<CelineToolDescriptor> availableTools() {
        return cortex.availableTools();
    }

    /**
     * Default execution has no implied user authorization. L0/L1 may pass; L2/L3 fail closed.
     */
    @Override
    public CelineToolResult execute(CelineToolIntent intent) {
        return executeAuthorized(intent,
                CelinePermissionPolicyG22.AuthorizationContext.none(System.currentTimeMillis()));
    }

    public CelineToolResult executeAuthorized(
            CelineToolIntent intent,
            CelinePermissionPolicyG22.AuthorizationContext authorization) {
        if (intent == null) return failure("PERMISSION_MISSING_INTENT");
        CelineToolCortexG21.TypedToolDescriptor descriptor = cortex.descriptor(intent.toolId);
        if (descriptor == null) return failure("PERMISSION_UNKNOWN_TOOL");

        CelinePermissionDecision decision = policy.evaluate(descriptor, intent, authorization);
        if (decision.state == CelinePermissionDecision.State.DENY) {
            return failure("PERMISSION_DENIED:" + clean(decision.reason));
        }
        if (decision.state == CelinePermissionDecision.State.REQUIRE_CONFIRMATION) {
            return failure("PERMISSION_CONFIRMATION_REQUIRED:" + clean(decision.reason));
        }
        return cortex.execute(intent);
    }

    public CelinePermissionDecision decide(
            CelineToolIntent intent,
            CelinePermissionPolicyG22.AuthorizationContext authorization) {
        if (intent == null) {
            return new CelinePermissionDecision(CelinePermissionDecision.State.DENY, "MISSING_INTENT");
        }
        CelineToolCortexG21.TypedToolDescriptor descriptor = cortex.descriptor(intent.toolId);
        return policy.evaluate(descriptor, intent, authorization);
    }

    public CelinePermissionPolicyG22 policy() {
        return policy;
    }

    private static CelineToolResult failure(String code) {
        return new CelineToolResult(false, "", code);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
