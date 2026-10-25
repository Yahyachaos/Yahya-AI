#!/usr/bin/env python3
"""Quiescent authorization-isolation stress test for issue #112.

The existing mixed-writer concurrency test cannot attribute unauthorized state
changes to model/tool input while direct-user writers legitimately mutate the
same state. This contract runs only untrusted writers in parallel in three
stable phases: inactive, directly activated, and restored/persisted persona.
No Android, durable storage, central Conversation or device behavior is claimed.
"""
from __future__ import annotations

import pathlib
import subprocess
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
MODE_SOURCE = ROOT / "ci/prototypes/CelinePersonaMode.java"
ADAPTER_SOURCE = ROOT / "ci/prototypes/CelinePersonaPromptAdapter.java"

HARNESS = r'''
import de.yahya.ai.CelinePersonaMode;
import de.yahya.ai.CelinePersonaPromptAdapter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class CelinePersonaUntrustedChannelIsolationContractTest {
    private static final int THREADS = 8;
    private static final int PER_THREAD = 1024;
    private static final CelinePersonaMode.InputChannel[] UNTRUSTED = {
        CelinePersonaMode.InputChannel.MODEL_OUTPUT,
        CelinePersonaMode.InputChannel.TOOL_OUTPUT,
        CelinePersonaMode.InputChannel.OTHER,
        null
    };
    private static final String[] PAYLOADS = {
        "Nutte", "NUTTE", "Nutte 1", "Nutte 2", "Nutte 3",
        "Nutte 999", "Nutte aus", "Normalmodus",
        "Nutte\\n", "\"Nutte 3\"", "normalmodus", null
    };

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void checkState(CelinePersonaMode mode, boolean active, int intensity,
                                   CelinePersonaMode.ActivationSource provenance,
                                   String base, String expectedActivePrompt) {
        CelinePersonaMode.State snapshot = mode.snapshot();
        check(snapshot.active() == active && snapshot.intensity() == intensity
                && snapshot.activationSource() == provenance, "immutable state invariant");
        String prompt = CelinePersonaPromptAdapter.compose(base, mode);
        if (active) {
            check(prompt.equals(expectedActivePrompt), "active prompt unchanged");
        } else {
            check(prompt == base, "inactive prompt identity unchanged");
            check(mode.promptContribution().isEmpty(), "inactive contribution empty");
        }
    }

    private static int stress(final CelinePersonaMode mode, final boolean active,
                              final int intensity, final CelinePersonaMode.ActivationSource provenance,
                              final String base) throws Exception {
        final String expectedActivePrompt = active
                ? base + "\n\n" + mode.promptContribution() : null;
        final CountDownLatch begin = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final AtomicInteger operations = new AtomicInteger();
        Thread[] workers = new Thread[THREADS];

        for (int t = 0; t < THREADS; t++) {
            final int index = t;
            workers[t] = new Thread(() -> {
                try {
                    begin.await();
                    for (int n = 0; n < PER_THREAD && failure.get() == null; n++) {
                        CelinePersonaMode.InputChannel channel = UNTRUSTED[(index + n) % UNTRUSTED.length];
                        String payload = PAYLOADS[(index * 13 + n) % PAYLOADS.length];
                        CelinePersonaMode.CommandResult result = mode.apply(channel, payload);
                        check(!result.commandConsumed() && !result.invalidIntensity(),
                                "untrusted command must not be consumed or flagged as user action");
                        check(result.active() == active && result.intensity() == intensity
                                && result.activationSource() == provenance,
                                "untrusted command must not alter its result snapshot");
                        checkState(mode, active, intensity, provenance, base, expectedActivePrompt);
                        operations.incrementAndGet();
                    }
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                }
            }, "celine-untrusted-" + index);
            workers[t].start();
        }
        begin.countDown();
        for (Thread thread : workers) thread.join(20000);
        for (Thread thread : workers) check(!thread.isAlive(), "worker timeout/deadlock");
        if (failure.get() != null) throw new AssertionError("untrusted channel mutation detected", failure.get());
        check(operations.get() == THREADS * PER_THREAD, "all adversarial operations executed");
        checkState(mode, active, intensity, provenance, base, expectedActivePrompt);
        return operations.get();
    }

    public static void main(String[] args) throws Exception {
        CelinePersonaMode mode = new CelinePersonaMode();
        String base = new String("CELINE normal prompt: private G1 memory and device-tool permissions unchanged");
        int count = stress(mode, false, 0, CelinePersonaMode.ActivationSource.NONE, base);

        CelinePersonaMode.CommandResult activated =
                mode.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte 3");
        check(activated.commandConsumed() && activated.active() && activated.intensity() == 3,
                "only an actual direct-user command activates persona");
        count += stress(mode, true, 3, CelinePersonaMode.ActivationSource.DIRECT_USER_COMMAND, base);

        check(mode.restorePersistedState(true, 1), "valid app-owned persisted state accepted");
        count += stress(mode, true, 1, CelinePersonaMode.ActivationSource.PERSISTED_STATE, base);

        CelinePersonaMode.CommandResult exited =
                mode.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte aus");
        check(exited.commandConsumed() && !exited.active() && exited.intensity() == 0,
                "real direct-user exit works after untrusted flood");
        check(CelinePersonaPromptAdapter.compose(base, mode) == base,
                "first prompt after exit is exact normal prompt object");
        System.out.println("Celine persona untrusted channel isolation: PASS (" + count
                + " parallel attempts; inactive/direct/persisted)");
    }
}
'''


def main() -> int:
    for source in (MODE_SOURCE, ADAPTER_SOURCE):
        if not source.is_file():
            raise SystemExit(f"missing Core persona source: {source}")
    with tempfile.TemporaryDirectory(prefix="celine-untrusted-persona-") as tmp:
        temp = pathlib.Path(tmp)
        java = temp / "CelinePersonaUntrustedChannelIsolationContractTest.java"
        classes = temp / "classes"
        classes.mkdir()
        java.write_text(HARNESS, encoding="utf-8")
        subprocess.run(["javac", "-d", str(classes), str(MODE_SOURCE),
                        str(ADAPTER_SOURCE), str(java)], check=True, cwd=ROOT)
        subprocess.run(["java", "-cp", str(classes),
                        "CelinePersonaUntrustedChannelIsolationContractTest"],
                       check=True, cwd=ROOT)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
