package de.yahya.ai;

/**
 * Room-owned, synchronous, proof-only scene-membership bracket.
 * It neither changes a GLB transform nor grants Android raster acceptance.
 * The caller must keep the render/capture operation synchronous on the
 * Filament engine owner and independently prove the resulting frame.
 */
final class CelineRoomProofCeilingScopeV80 {
    interface CeilingScene {
        boolean isAttached();
        void detach();
        void restore();
    }

    private CelineRoomProofCeilingScopeV80() {}

    /**
     * A roof/frame postcondition failure must invalidate the built Room, not
     * merely throw while a roofless Scene is still classified as active.
     * Caller owns the Engine-thread teardown and UI Canvas fallback redraw.
     * No raster/Android acceptance is inferred from this control-flow guard.
     */
    static void verifyOrFailClosed(Runnable verifier, Runnable failClosed) {
        if (verifier == null || failClosed == null) {
            throw new IllegalArgumentException("S25 finalization requires verifier and cleanup");
        }
        try {
            verifier.run();
        } catch (Throwable verificationFailure) {
            try { failClosed.run(); }
            catch (Throwable cleanupFailure) {
                if (cleanupFailure != verificationFailure) {
                    verificationFailure.addSuppressed(cleanupFailure);
                }
            }
            throw verificationFailure;
        }
    }

    static void run(CeilingScene ceiling, Runnable synchronousFrame) {
        if (ceiling == null || synchronousFrame == null) {
            throw new IllegalArgumentException("S25 proof requires a ceiling and synchronous frame");
        }
        if (!ceiling.isAttached()) {
            throw new IllegalStateException("S25 ceiling already absent before diagnostic frame");
        }
        try {
            ceiling.detach();
            if (ceiling.isAttached()) {
                throw new IllegalStateException("S25 ceiling remained attached during diagnostic frame");
            }
            synchronousFrame.run();
            if (ceiling.isAttached()) {
                throw new IllegalStateException("S25 ceiling reattached during diagnostic frame");
            }
        } finally {
            // Even an exception in the renderer must not leave a roofless
            // normal HOME/CALL Scene. A failed restore fails closed.
            if (!ceiling.isAttached()) ceiling.restore();
            if (!ceiling.isAttached()) {
                throw new IllegalStateException("S25 ceiling restoration failed after diagnostic frame");
            }
        }
    }
}
