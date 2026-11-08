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
