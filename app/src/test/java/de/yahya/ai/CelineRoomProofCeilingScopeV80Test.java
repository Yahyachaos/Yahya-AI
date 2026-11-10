package de.yahya.ai;

/** Android-free, executable regression harness: javac both classes, then run this main. */
public final class CelineRoomProofCeilingScopeV80Test {
    private static final class FakeScene implements CelineRoomProofCeilingScopeV80.CeilingScene {
        boolean attached = true;
        boolean refuseDetach;
        boolean refuseRestore;
        int detaches;
        int restores;

        @Override public boolean isAttached() { return attached; }
        @Override public void detach() { detaches++; if (!refuseDetach) attached = false; }
        @Override public void restore() { restores++; if (!refuseRestore) attached = true; }
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static void expect(Class<? extends Throwable> type, Runnable task) {
        try { task.run(); }
        catch (Throwable error) {
            if (type.isInstance(error)) return;
            throw new AssertionError("Expected " + type.getSimpleName() + " but got " + error, error);
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }

    public static void main(String[] args) {
        FakeScene happy = new FakeScene();
        CelineRoomProofCeilingScopeV80.run(happy,
                () -> check(!happy.attached, "Diagnostic callback saw opaque ceiling"));
        check(happy.attached && happy.detaches == 1 && happy.restores == 1,
                "Normal Scene was not restored once");

        FakeScene throwsDuringFrame = new FakeScene();
        expect(IllegalArgumentException.class, () -> CelineRoomProofCeilingScopeV80.run(
                throwsDuringFrame, () -> { throw new IllegalArgumentException("capture failure"); }));
        check(throwsDuringFrame.attached && throwsDuringFrame.restores == 1,
                "Failure left the ceiling detached");

        FakeScene absent = new FakeScene();
        absent.attached = false;
        expect(IllegalStateException.class, () -> CelineRoomProofCeilingScopeV80.run(
                absent, () -> { throw new AssertionError("must not capture without ceiling"); }));
        check(absent.detaches == 0 && absent.restores == 0, "Unowned state mutated");

        FakeScene refuseDetach = new FakeScene();
        refuseDetach.refuseDetach = true;
        expect(IllegalStateException.class, () -> CelineRoomProofCeilingScopeV80.run(
                refuseDetach, () -> { throw new AssertionError("callback must not run"); }));
        check(refuseDetach.attached, "Refused detach broke original Scene");

        FakeScene refuseRestore = new FakeScene();
        refuseRestore.refuseRestore = true;
        expect(IllegalStateException.class, () -> CelineRoomProofCeilingScopeV80.run(
                refuseRestore, () -> {}));
        check(!refuseRestore.attached && refuseRestore.restores == 1,
                "Failed restore incorrectly reported success");

        FakeScene nested = new FakeScene();
        CelineRoomProofCeilingScopeV80.run(nested, () -> expect(
                IllegalStateException.class, () -> CelineRoomProofCeilingScopeV80.run(nested, () -> {})));
        check(nested.attached && nested.detaches == 1 && nested.restores == 1,
                "Nested diagnostic changed shell membership");

        FakeScene reattachDuringFrame = new FakeScene();
        expect(IllegalStateException.class, () -> CelineRoomProofCeilingScopeV80.run(
                reattachDuringFrame, () -> reattachDuringFrame.attached = true));
        check(reattachDuringFrame.attached, "Reattached scene not restored");

        // A capture initiated after the roof bracket is NOT a roof-hidden frame.
        // This intentionally failing synthetic probe guards against deferring
        // renderer.render()/readPixels() submission to a later frame callback.
        FakeScene deferredRender = new FakeScene();
        final Runnable[] unsafeDeferredRender = new Runnable[1];
        CelineRoomProofCeilingScopeV80.run(deferredRender, () -> {
            check(!deferredRender.attached, "Submission should be inside the bracket");
            unsafeDeferredRender[0] = () -> check(!deferredRender.attached,
                    "Deferred render was wrongly treated as roof-hidden");
        });
        check(deferredRender.attached && unsafeDeferredRender[0] != null,
                "Roof was not restored before the delayed render");
        expect(AssertionError.class, unsafeDeferredRender[0]);

        // Asynchronous GPU readback COMPLETION is allowed after the roof is
        // restored, provided render()+readPixels() SUBMISSION happened inside
        // one real beginFrame/endFrame epoch. No synthetic test proves pixels.
        FakeScene delayedReadback = new FakeScene();
        final boolean[] readPixelsIssued = {false};
        final Runnable[] onReadback = new Runnable[1];
        CelineRoomProofCeilingScopeV80.run(delayedReadback, () -> {
            check(!delayedReadback.attached, "Readback must be issued roof-hidden");
            readPixelsIssued[0] = true;
            onReadback[0] = () -> check(delayedReadback.attached && readPixelsIssued[0],
                    "Readback completion arrived before submission/roof restore");
        });
        check(onReadback[0] != null, "No asynchronous completion registered");
        onReadback[0].run();

        // Room post-frame verification must keep the active scene only when
        // both ceiling and 13/13 mesh/shell guards really pass.
        final int[] validFinalization = {0, 0};
        CelineRoomProofCeilingScopeV80.verifyOrFailClosed(
                () -> validFinalization[0]++,
                () -> validFinalization[1]++);
        check(validFinalization[0] == 1 && validFinalization[1] == 0,
                "Clean post-frame Room must not be destroyed");

        // If the ceiling cannot be restored or a cached native shell/mesh
        // check fails, the caller must perform Room destruction/fallback.
        final boolean[] failedRoomActive = {true};
        final IllegalStateException roofMissing =
                new IllegalStateException("missing post-frame ceiling");
        try {
            CelineRoomProofCeilingScopeV80.verifyOrFailClosed(
                    () -> { throw roofMissing; },
                    () -> failedRoomActive[0] = false);
            throw new AssertionError("Missing roof was accepted");
        } catch (IllegalStateException failure) {
            check(failure == roofMissing && !failedRoomActive[0],
                    "Roof guard error not preserved or Room remained active");
        }

        // Even when teardown fails, preserve the original roof/geometry fault
        // and attach the cleanup failure as suppressed diagnostic evidence.
        final IllegalArgumentException badMesh =
                new IllegalArgumentException("S25 mesh drift");
        try {
            CelineRoomProofCeilingScopeV80.verifyOrFailClosed(
                    () -> { throw badMesh; },
                    () -> { throw new IllegalStateException("cleanup failed"); });
            throw new AssertionError("Post-frame mesh drift was accepted");
        } catch (IllegalArgumentException failure) {
            check(failure == badMesh && failure.getSuppressed().length == 1
                            && "cleanup failed".equals(
                                    failure.getSuppressed()[0].getMessage()),
                    "Cleanup exception masked original S25 failure");
        }

        System.out.println("S25 CEILING PROOF BRACKET: 12/12 tests PASS (pure Java, NOT Android visual proof)");
    }
}
