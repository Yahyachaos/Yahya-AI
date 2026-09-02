package de.yahya.ai;

/**
 * G2.2 central deny-by-default permission policy for typed Celine tools.
 *
 * Policy decisions are deterministic and provider-independent. Model confidence never
 * grants permissions. L3 confirmations are short-lived and bound to the exact action.
 */
public final class CelinePermissionPolicyG22 implements CelinePermissionPolicy {
    public static final long FRESH_CONFIRMATION_WINDOW_MS = 120_000L;

    public static final class AuthorizationContext {
        public final boolean explicitUserIntent;
        public final boolean freshConfirmation;
        public final long confirmationAtEpochMs;
        public final long nowEpochMs;
        public final String approvedToolId;
        public final String approvedTarget;
        public final String approvedPayload;

        private AuthorizationContext(boolean explicitUserIntent,
                                     boolean freshConfirmation,
                                     long confirmationAtEpochMs,
                                     long nowEpochMs,
                                     String approvedToolId,
                                     String approvedTarget,
                                     String approvedPayload) {
            this.explicitUserIntent = explicitUserIntent;
            this.freshConfirmation = freshConfirmation;
            this.confirmationAtEpochMs = Math.max(0L, confirmationAtEpochMs);
            this.nowEpochMs = Math.max(0L, nowEpochMs);
            this.approvedToolId = clean(approvedToolId);
            this.approvedTarget = clean(approvedTarget);
            this.approvedPayload = clean(approvedPayload);
        }

        public static AuthorizationContext none(long nowEpochMs) {
            return new AuthorizationContext(false, false, 0L, nowEpochMs, "", "", "");
        }

        public static AuthorizationContext explicitUserIntent(long nowEpochMs) {
            return new AuthorizationContext(true, false, 0L, nowEpochMs, "", "", "");
        }

        public static AuthorizationContext confirmed(CelineToolIntent intent,
                                                     long confirmationAtEpochMs,
                                                     long nowEpochMs) {
            if (intent == null) return none(nowEpochMs);
            return new AuthorizationContext(
                    true,
                    true,
                    confirmationAtEpochMs,
                    nowEpochMs,
                    intent.toolId,
                    intent.target,
                    intent.payload);
        }
    }

    @Override
    public CelinePermissionDecision evaluate(CelineToolIntent intent, CelineRiskClass riskClass) {
        return evaluate(intent, riskClass, AuthorizationContext.none(System.currentTimeMillis()));
    }

    public CelinePermissionDecision evaluate(CelineToolCortexG21.TypedToolDescriptor descriptor,
                                             CelineToolIntent intent,
                                             AuthorizationContext context) {
        if (descriptor == null) return deny("UNKNOWN_TOOL");
        if (intent == null) return deny("MISSING_INTENT");
        if (!clean(descriptor.id).equals(clean(intent.toolId))) return deny("TOOL_DESCRIPTOR_MISMATCH");
        return evaluate(intent, descriptor.riskClass, context);
    }

    public CelinePermissionDecision evaluate(CelineToolIntent intent,
                                             CelineRiskClass riskClass,
                                             AuthorizationContext context) {
        if (intent == null) return deny("MISSING_INTENT");
        if (riskClass == null) return deny("MISSING_RISK_CLASS");
        String toolId = clean(intent.toolId);
        if (toolId.isEmpty()) return deny("MISSING_TOOL_ID");
        AuthorizationContext auth = context == null
                ? AuthorizationContext.none(System.currentTimeMillis())
                : context;

        switch (riskClass) {
            case L0_READ_ONLY:
                return allow("L0_READ_ONLY");

            case L1_REVERSIBLE_LOCAL:
                return allow("L1_REVERSIBLE_LOCAL");

            case L2_EXTERNAL_STATE_CHANGE:
                if (clean(intent.target).isEmpty()) return deny("L2_EXPLICIT_TARGET_REQUIRED");
                if (!auth.explicitUserIntent) {
                    return confirm("L2_EXPLICIT_USER_INTENT_REQUIRED");
                }
                return allow("L2_EXPLICIT_INTENT_BOUND");

            case L3_HIGH_IMPACT:
                if (!auth.explicitUserIntent) {
                    return confirm("L3_EXPLICIT_USER_INTENT_REQUIRED");
                }
                if (!auth.freshConfirmation) {
                    return confirm("L3_FRESH_CONFIRMATION_REQUIRED");
                }
                if (!isFresh(auth)) {
                    return confirm("L3_CONFIRMATION_STALE");
                }
                if (!scopeMatches(intent, auth)) {
                    return deny("L3_CONFIRMATION_SCOPE_MISMATCH");
                }
                return allow("L3_FRESH_EXACT_CONFIRMATION");

            default:
                return deny("UNSUPPORTED_RISK_CLASS");
        }
    }

    private static boolean isFresh(AuthorizationContext auth) {
        if (auth.confirmationAtEpochMs <= 0L || auth.nowEpochMs <= 0L) return false;
        if (auth.confirmationAtEpochMs > auth.nowEpochMs) return false;
        return auth.nowEpochMs - auth.confirmationAtEpochMs <= FRESH_CONFIRMATION_WINDOW_MS;
    }

    private static boolean scopeMatches(CelineToolIntent intent, AuthorizationContext auth) {
        return clean(intent.toolId).equals(auth.approvedToolId)
                && clean(intent.target).equals(auth.approvedTarget)
                && clean(intent.payload).equals(auth.approvedPayload);
    }

    private static CelinePermissionDecision allow(String reason) {
        return new CelinePermissionDecision(CelinePermissionDecision.State.ALLOW, reason);
    }

    private static CelinePermissionDecision confirm(String reason) {
        return new CelinePermissionDecision(CelinePermissionDecision.State.REQUIRE_CONFIRMATION, reason);
    }

    private static CelinePermissionDecision deny(String reason) {
        return new CelinePermissionDecision(CelinePermissionDecision.State.DENY, reason);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
