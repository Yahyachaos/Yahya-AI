#!/usr/bin/env python3
"""Core-only concurrent snapshot/command/prompt regression for issue #112.

No Android, persistent store, UI, model provider, or Room integration is claimed.
Concurrent schedules vary; every checked invariant is independent of their order.
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
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class CelinePersonaConcurrencyContractTest {
    private static final int THREADS = 8;
    private static final int STEPS = 2048;

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }

    private static void checkSnapshot(CelinePersonaMode.State snapshot) {
        if (snapshot.active()) {
            check(snapshot.intensity() >= 1 && snapshot.intensity() <= 3, "active intensity bounds");
            check(snapshot.activationSource() == CelinePersonaMode.ActivationSource.DIRECT_USER_COMMAND
                    || snapshot.activationSource() == CelinePersonaMode.ActivationSource.PERSISTED_STATE,
                    "active provenance");
        } else {
            check(snapshot.intensity() == 0, "inactive zero intensity");
            check(snapshot.activationSource() == CelinePersonaMode.ActivationSource.NONE,
                    "inactive no activation provenance");
        }
    }

    public static void main(String[] args) throws Exception {
        final CelinePersonaMode mode = new CelinePersonaMode();
        final String normal = new String("CELINE NORMAL\nG1 app-owned context remains intact");
        final String[] acceptedPrompts = new String[4];
        acceptedPrompts[0] = normal;
        for (int level = 1; level <= 3; level++) {
            CelinePersonaMode reference = new CelinePersonaMode();
            check(reference.restorePersistedState(true, level), "reference state setup");
            acceptedPrompts[level] = normal + "\n\n" + reference.promptContribution();
        }
        final CountDownLatch start = new CountDownLatch(1);
        final AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        final AtomicInteger observed = new AtomicInteger();
        final Thread[] workers = new Thread[THREADS];

        for (int index = 0; index < THREADS; index++) {
            final int workerIndex = index;
            workers[index] = new Thread(() -> {
                try {
                    Random random = new Random(112L + workerIndex * 997L);
                    start.await();
                    for (int step = 0; step < STEPS && firstFailure.get() == null; step++) {
                        int operation = random.nextInt(11);
                        CelinePersonaMode.CommandResult command = null;
                        switch (operation) {
                            case 0: case 1: case 2:
                                command = mode.apply(CelinePersonaMode.InputChannel.DIRECT_USER,
                                        "Nutte " + (operation + 1));
                                check(command.commandConsumed() && !command.invalidIntensity(),
                                        "explicit intensity command consumed");
                                break;
                            case 3:
                                command = mode.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte aus");
                                check(command.commandConsumed(), "deactivation command consumed");
                                break;
                            case 4:
                                command = mode.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Normalmodus");
                                check(command.commandConsumed(), "normal-mode command consumed");
                                break;
                            case 5:
                                command = mode.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte 999");
                                check(command.commandConsumed() && command.invalidIntensity(),
                                        "invalid numeric level is consumed and rejected");
                                break;
                            case 6:
                                command = mode.apply(CelinePersonaMode.InputChannel.MODEL_OUTPUT, "Nutte 3");
                                check(!command.commandConsumed(), "model output cannot activate");
                                break;
                            case 7:
                                command = mode.apply(CelinePersonaMode.InputChannel.TOOL_OUTPUT, "Normalmodus");
                                check(!command.commandConsumed(), "tool output cannot deactivate");
                                break;
                            case 8:
                                check(mode.restorePersistedState(true, random.nextInt(3) + 1),
                                        "valid persisted restore");
                                break;
                            case 9:
                                check(!mode.restorePersistedState(true, 99), "invalid persisted restore rejected");
                                break;
                            case 10:
                                check(mode.restorePersistedState(false, 0), "inactive persisted restore");
                                break;
                            default:
                                throw new AssertionError("unknown operation");
                        }
                        if (command != null) {
                            check(command.active() ? command.intensity() >= 1 && command.intensity() <= 3
                                            && command.activationSource() != CelinePersonaMode.ActivationSource.NONE
                                    : command.intensity() == 0
                                            && command.activationSource() == CelinePersonaMode.ActivationSource.NONE,
                                    "command result is one coherent state");
                        }
                        CelinePersonaMode.State immutable = mode.snapshot();
                        boolean frozenActive = immutable.active();
                        int frozenIntensity = immutable.intensity();
                        CelinePersonaMode.ActivationSource frozenSource = immutable.activationSource();
                        checkSnapshot(immutable);
                        String composed = CelinePersonaPromptAdapter.compose(normal, mode);
                        if (composed == normal) {
                            check(composed.equals(acceptedPrompts[0]), "inactive reference identity");
                        } else {
                            boolean known = false;
                            for (int level = 1; level <= 3; level++) {
                                if (composed.equals(acceptedPrompts[level])) known = true;
                            }
                            check(known, "active prompt must be exact bounded contribution");
                        }
                        check(immutable.active() == frozenActive
                                        && immutable.intensity() == frozenIntensity
                                        && immutable.activationSource() == frozenSource,
                                "snapshot is immutable across concurrent transitions");
                        observed.incrementAndGet();
                    }
                } catch (Throwable error) {
                    firstFailure.compareAndSet(null, error);
                }
            }, "celine-persona-contract-" + index);
            workers[index].start();
        }
        start.countDown();
        for (Thread worker : workers) worker.join(20000);
        for (Thread worker : workers) check(!worker.isAlive(), "worker timeout/deadlock");
        if (firstFailure.get() != null) throw new AssertionError("concurrent contract failure", firstFailure.get());
        check(observed.get() == THREADS * STEPS, "all concurrent operations completed");
        checkSnapshot(mode.snapshot());
        System.out.println("Celine persona concurrency contract: PASS (" + observed.get()
                + " operations, " + THREADS + " threads)");
    }
}
'''


def main() -> int:
    for source in (MODE_SOURCE, ADAPTER_SOURCE):
        if not source.is_file():
            raise SystemExit(f"missing persona source: {source}")
    with tempfile.TemporaryDirectory(prefix="celine-persona-concurrency-") as tmp:
        directory = pathlib.Path(tmp)
        java = directory / "CelinePersonaConcurrencyContractTest.java"
        classes = directory / "classes"
        classes.mkdir()
        java.write_text(HARNESS, encoding="utf-8")
        subprocess.run(["javac", "-d", str(classes), str(MODE_SOURCE),
                        str(ADAPTER_SOURCE), str(java)], check=True, cwd=ROOT)
        subprocess.run(["java", "-cp", str(classes),
                        "CelinePersonaConcurrencyContractTest"], check=True, cwd=ROOT)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
