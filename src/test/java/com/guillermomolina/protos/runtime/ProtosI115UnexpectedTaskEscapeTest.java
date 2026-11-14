/*
 * I115: unexpected Java failures must not strand Actor-local Tasks.
 */
package com.guillermomolina.protos.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class ProtosI115UnexpectedTaskEscapeTest {

    @Test
    void directRootTaskPreservesUnexpectedExceptionWithoutLeakingTask() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        IllegalStateException original =
                new IllegalStateException("I115 unexpected root failure");

        IllegalStateException observed = assertThrows(
                IllegalStateException.class,
                () -> domain.runFreshRootTaskDirectly(task -> {
                    throw original;
                }));

        assertSame(original, observed);
        assertEquals(0, domain.liveTaskCount(),
                "unexpected failure must not strand the root Task");
    }

    @Test
    void queuedTaskPreservesHostErrorAndReachesTerminalState() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        AssertionError original =
                new AssertionError("I115 unexpected queued failure");

        ProtosTask task = domain.createTask(null, current -> {
            throw original;
        });

        AssertionError observed =
                assertThrows(AssertionError.class, domain::dispatchOne);

        assertSame(original, observed);
        assertEquals(ProtosTask.State.FAILED, task.state());
        assertEquals(0, domain.liveTaskCount(),
                "unexpected failure must release the queued Task");
    }

    @Test
    void unexpectedFailureDrainsChildrenBeforePublishingParentTerminal() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        IllegalStateException original = new IllegalStateException("parent failure");
        java.util.concurrent.atomic.AtomicInteger finalizers =
                new java.util.concurrent.atomic.AtomicInteger();

        ProtosTask parent = domain.createTask(null, current -> {
            current.installTerminalLifecycleForRuntime(
                    (owned, terminal, outcome) -> {
                        assertEquals(ProtosTask.State.FAILED, terminal);
                        assertSame(original, outcome);
                        assertEquals(0, domain.liveTaskCount());
                        finalizers.incrementAndGet();
                    });
            throw original;
        });

        ProtosTask child = domain.createTask(
                parent, null, current -> current.complete("unexpected"));

        assertSame(original, assertThrows(
                IllegalStateException.class, domain::dispatchOne));

        assertEquals(ProtosTask.State.SUSPENDED, parent.state());
        assertEquals(2, domain.liveTaskCount());
        assertEquals(0, finalizers.get());

        domain.dispatchOne();
        assertEquals(ProtosTask.State.CANCELLED, child.state());
        assertEquals(0, finalizers.get());

        domain.dispatchOne();
        assertEquals(ProtosTask.State.FAILED, parent.state());
        assertEquals(0, domain.liveTaskCount());
        assertEquals(1, finalizers.get());
    }

    @Test
    void unexpectedFailureTerminalizesAssociatedFutureWithoutGuestError() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        ProtosFutureValue future = new ProtosFutureValue(
                new ProtosObjectValue(ProtosObjectValue.rootObject()), domain);
        IllegalStateException original = new IllegalStateException("future failure");

        ProtosTask task = domain.createTask(future, current -> {
            throw original;
        });

        assertSame(original, assertThrows(
                IllegalStateException.class, domain::dispatchOne));

        assertEquals(ProtosTask.State.FAILED, task.state());
        assertEquals(ProtosFutureValue.State.CANCELLED, future.state());
        assertEquals(0, domain.liveTaskCount());
    }


    @Test
    void suspendedChildDrainsBeforeUnexpectedParentFailurePublishesFuture() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();

        ProtosTask.WaitDependency parentWait = new ProtosTask.WaitDependency() {};
        ProtosTask.WaitDependency childWait = new ProtosTask.WaitDependency() {};

        java.util.concurrent.atomic.AtomicInteger parentRuns =
                new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger childRuns =
                new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger finalizers =
                new java.util.concurrent.atomic.AtomicInteger();

        ProtosFutureValue future = new ProtosFutureValue(
                new ProtosObjectValue(ProtosObjectValue.rootObject()), domain);
        IllegalStateException original =
                new IllegalStateException("resumed parent failure");

        ProtosTask parent = domain.createTask(future, current -> {
            if (parentRuns.getAndIncrement() == 0) {
                current.suspend(parentWait);
                return;
            }

            current.installTerminalLifecycleForRuntime(
                    (owned, terminal, outcome) -> {
                        assertEquals(ProtosTask.State.FAILED, terminal);
                        assertSame(original, outcome);
                        assertEquals(ProtosFutureValue.State.CANCELLED, future.state());
                        assertEquals(0, domain.liveTaskCount());
                        finalizers.incrementAndGet();
                    });
            throw original;
        });

        ProtosTask child = domain.createTask(parent, null, current -> {
            if (childRuns.getAndIncrement() == 0) {
                current.suspend(childWait);
            } else {
                org.junit.jupiter.api.Assertions.assertTrue(
                        current.cancellationRequested());
                current.observeCancellation();
            }
        });

        domain.dispatchOne();
        assertEquals(ProtosTask.State.SUSPENDED, parent.state());

        domain.dispatchOne();
        assertEquals(ProtosTask.State.SUSPENDED, child.state());

        org.junit.jupiter.api.Assertions.assertTrue(parent.resume(parentWait));

        assertSame(original, assertThrows(
                IllegalStateException.class, domain::dispatchOne));

        assertEquals(ProtosTask.State.SUSPENDED, parent.state());
        assertEquals(ProtosTask.State.RUNNABLE, child.state());
        assertEquals(ProtosFutureValue.State.PENDING, future.state());
        assertEquals(0, finalizers.get());

        domain.dispatchOne();
        assertEquals(ProtosTask.State.CANCELLED, child.state());
        assertEquals(ProtosTask.State.RUNNABLE, parent.state());

        domain.dispatchOne();
        assertEquals(ProtosTask.State.FAILED, parent.state());
        assertSame(original, parent.failure().orElseThrow());
        assertEquals(ProtosFutureValue.State.CANCELLED, future.state());
        assertEquals(0, domain.liveTaskCount());
        assertEquals(1, finalizers.get());
    }

    @Test
    void unexpectedExceptionAfterTerminalDoesNotPublishTerminalTwice() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        java.util.concurrent.atomic.AtomicInteger finalizers =
                new java.util.concurrent.atomic.AtomicInteger();
        IllegalStateException original =
                new IllegalStateException("failure after completion");

        assertSame(original, assertThrows(
                IllegalStateException.class,
                () -> domain.runFreshRootTaskDirectly(current -> {
                    current.installTerminalLifecycleForRuntime(
                            (owned, terminal, outcome) -> {
                                assertEquals(ProtosTask.State.COMPLETED, terminal);
                                assertEquals("done", outcome);
                                finalizers.incrementAndGet();
                            });
                    current.complete("done");
                    throw original;
                })));

        assertEquals(1, finalizers.get());
        assertEquals(0, domain.liveTaskCount());
    }


    @Test
    void cancellationCannotBypassUnexpectedFailureChildDrain() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        ProtosTask.WaitDependency parentWait = new ProtosTask.WaitDependency() {};
        ProtosTask.WaitDependency childWait = new ProtosTask.WaitDependency() {};
        ProtosTask.WaitDependency cleanupWait = new ProtosTask.WaitDependency() {};

        java.util.concurrent.atomic.AtomicInteger parentRuns =
                new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger childRuns =
                new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger finalizers =
                new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<ProtosDynamicControlState.Frame>
                ensureFrame = new java.util.concurrent.atomic.AtomicReference<>();

        IllegalStateException original =
                new IllegalStateException("unexpected parent failure");

        ProtosTask parent = domain.createTask(null, current -> {
            if (parentRuns.getAndIncrement() == 0) {
                current.installTerminalLifecycleForRuntime(
                        (owned, terminal, outcome) -> {
                            assertEquals(ProtosTask.State.FAILED, terminal);
                            assertSame(original, outcome);
                            assertEquals(0, domain.liveTaskCount());
                            finalizers.incrementAndGet();
                        });
                current.suspend(parentWait);
                return;
            }
            throw original;
        });

        ProtosTask child = domain.createTask(parent, null, current -> {
            int segment = childRuns.getAndIncrement();
            if (segment == 0) {
                ensureFrame.set(current.dynamicControlState().enterFrame(
                        new Object(),
                        ProtosDynamicControlState.FrameKind.ENSURE));
                current.suspend(childWait);
            } else if (segment == 1) {
                org.junit.jupiter.api.Assertions.assertTrue(
                        current.cancellationRequested());
                org.junit.jupiter.api.Assertions.assertTrue(
                        current.observeCancellation());
                current.suspend(cleanupWait);
            } else {
                current.dynamicControlState().leaveFrame(ensureFrame.get());
                org.junit.jupiter.api.Assertions.assertTrue(
                        current.finishCancellationUnwind());
            }
        });

        domain.dispatchOne();
        domain.dispatchOne();

        assertEquals(ProtosTask.State.SUSPENDED, parent.state());
        assertEquals(ProtosTask.State.SUSPENDED, child.state());

        org.junit.jupiter.api.Assertions.assertTrue(parent.resume(parentWait));

        assertSame(original, assertThrows(
                IllegalStateException.class, domain::dispatchOne));

        assertEquals(ProtosTask.State.SUSPENDED, parent.state());
        assertEquals(0, finalizers.get());

        // A second cancellation must not make a failed parent runnable
        // while its child still owns suspended ensure cleanup.
        parent.requestCancellation();

        domain.dispatchOne();

        assertEquals(ProtosTask.State.SUSPENDED, child.state());
        assertEquals(ProtosTask.State.SUSPENDED, parent.state(),
                "parent must remain in child drain during cleanup");
        assertEquals(0, finalizers.get());

        org.junit.jupiter.api.Assertions.assertFalse(domain.dispatchOne());

        org.junit.jupiter.api.Assertions.assertTrue(child.resume(cleanupWait));
        domain.dispatchOne();

        assertEquals(ProtosTask.State.CANCELLED, child.state());
        assertEquals(ProtosTask.State.RUNNABLE, parent.state());
        assertEquals(0, finalizers.get());

        domain.dispatchOne();

        assertEquals(ProtosTask.State.FAILED, parent.state());
        assertSame(original, parent.failure().orElseThrow());
        assertEquals(0, domain.liveTaskCount());
        assertEquals(1, finalizers.get());
    }


    @Test
    void unexpectedRootTaskFailureReachesProcessFatalAuthority() {
        ProtosObjectValue prototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze();
        ProtosProcessRuntime process = new ProtosProcessRuntime(prototype);
        ProtosActor root = process.rootActorForRuntime();
        ProtosActorExecutionDomain domain = root.executionDomain();

        IllegalStateException original =
                new IllegalStateException("fatal root Task");

        ProtosTask task = domain.createTask(null, current -> {
            throw original;
        });

        assertSame(original, assertThrows(
                IllegalStateException.class, domain::dispatchOne));

        assertEquals(ProtosTask.State.FAILED, task.state());
        assertEquals(0, domain.liveTaskCount());

        org.junit.jupiter.api.Assertions.assertTrue(
                process.failedFromRootForRuntime(),
                "an internal root failure must reach Process failure authority");

        assertSame(original,
                process.rootFailureCauseForTesting().orElseThrow());

        assertEquals(ProtosProcessRuntime.LifecycleState.TERMINATED,
                process.lifecycleState());
    }

    @Test
    void unexpectedHostedTaskFailureDoesNotKillRootProcess() {
        ProtosObjectValue prototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze();
        ProtosProcessRuntime process = new ProtosProcessRuntime(prototype);
        ProtosActor root = process.rootActorForRuntime();
        ProtosActor hosted = process.createHostedActorForRuntime(prototype);
        ProtosActorExecutionDomain domain = hosted.executionDomain();

        IllegalStateException original =
                new IllegalStateException("fatal hosted Task");

        ProtosTask task = domain.createTask(null, current -> {
            throw original;
        });

        assertSame(original, assertThrows(
                IllegalStateException.class, domain::dispatchOne));

        assertEquals(ProtosTask.State.FAILED, task.state());
        assertEquals(0, domain.liveTaskCount());

        assertEquals(ProtosActor.LifecycleState.TERMINATED,
                hosted.lifecycleState());

        assertEquals(ProtosProcessRuntime.LifecycleState.RUNNING,
                process.lifecycleState());

        org.junit.jupiter.api.Assertions.assertFalse(
                process.failedFromRootForRuntime());

        assertEquals(ProtosActor.LifecycleState.INITIALIZING,
                root.lifecycleState());
    }


    @Test
    void exceptionalFuturePublicationRequiresFatalActorCutover() {
        ProtosObjectValue prototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze();
        ProtosProcessRuntime process = new ProtosProcessRuntime(prototype);
        ProtosActor root = process.rootActorForRuntime();
        ProtosActorExecutionDomain domain = root.executionDomain();

        ProtosFutureValue future = new ProtosFutureValue(
                new ProtosObjectValue(ProtosObjectValue.rootObject()),
                domain);

        java.util.concurrent.atomic.AtomicReference<
                ProtosProcessRuntime.LifecycleState> lifecycleAtPublication =
                new java.util.concurrent.atomic.AtomicReference<>();

        java.util.concurrent.atomic.AtomicReference<Boolean>
                fatalAtPublication =
                new java.util.concurrent.atomic.AtomicReference<>();

        future.observe(terminal -> {
            lifecycleAtPublication.set(process.lifecycleState());
            fatalAtPublication.set(process.failedFromRootForRuntime());
        });

        IllegalStateException original =
                new IllegalStateException("fatal producer failure");

        ProtosTask task = domain.createTask(future, current -> {
            throw original;
        });

        assertSame(original, assertThrows(
                IllegalStateException.class, domain::dispatchOne));

        assertEquals(ProtosTask.State.FAILED, task.state());
        assertEquals(0, domain.liveTaskCount());

        org.junit.jupiter.api.Assertions.assertFalse(
                future.isPending(),
                "aborted producer must not retain a pending Future");

        assertEquals(Boolean.TRUE, fatalAtPublication.get(),
                "Future must not publish before fatal failure authority");

        org.junit.jupiter.api.Assertions.assertNotEquals(
                ProtosProcessRuntime.LifecycleState.RUNNING,
                lifecycleAtPublication.get(),
                "Future publication must follow Process termination cutover");

        assertSame(original,
                process.rootFailureCauseForTesting().orElseThrow());
    }


    @Test
    void fatalActorFutureWaitsForStructuredChildDrain() {
        ProtosObjectValue prototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze();
        ProtosProcessRuntime process = new ProtosProcessRuntime(prototype);
        ProtosActor root = process.rootActorForRuntime();
        ProtosActorExecutionDomain domain = root.executionDomain();

        ProtosFutureValue future = new ProtosFutureValue(
                new ProtosObjectValue(ProtosObjectValue.rootObject()),
                domain);

        java.util.concurrent.atomic.AtomicReference<
                ProtosProcessRuntime.LifecycleState> processAtPublication =
                new java.util.concurrent.atomic.AtomicReference<>();

        java.util.concurrent.atomic.AtomicReference<Boolean>
                fatalAtPublication =
                new java.util.concurrent.atomic.AtomicReference<>();

        future.observe(terminal -> {
            processAtPublication.set(process.lifecycleState());
            fatalAtPublication.set(process.failedFromRootForRuntime());
        });

        IllegalStateException original =
                new IllegalStateException("fatal producer with child");

        ProtosTask parent = domain.createTask(future, current -> {
            throw original;
        });

        ProtosTask child = domain.createTask(
                parent, null, current -> {
                    throw new AssertionError(
                            "cancelled child must not enter ordinary body");
                });

        assertSame(original, assertThrows(
                IllegalStateException.class, domain::dispatchOne));

        assertEquals(ProtosProcessRuntime.LifecycleState.TERMINATING,
                process.lifecycleState());
        assertSame(original,
                process.rootFailureCauseForTesting().orElseThrow());
        assertEquals(ProtosTask.State.SUSPENDED, parent.state());
        assertEquals(ProtosFutureValue.State.PENDING, future.state());
        org.junit.jupiter.api.Assertions.assertNull(processAtPublication.get());

        org.junit.jupiter.api.Assertions.assertTrue(domain.dispatchOne());

        assertEquals(ProtosTask.State.CANCELLED, child.state());
        assertEquals(ProtosTask.State.RUNNABLE, parent.state());
        assertEquals(ProtosFutureValue.State.PENDING, future.state());

        org.junit.jupiter.api.Assertions.assertTrue(domain.dispatchOne());

        assertEquals(ProtosTask.State.FAILED, parent.state());
        assertSame(original, parent.failure().orElseThrow());
        assertEquals(0, domain.liveTaskCount());
        assertEquals(ProtosProcessRuntime.LifecycleState.TERMINATED,
                process.lifecycleState());

        assertEquals(ProtosFutureValue.State.CANCELLED, future.state());
        assertEquals(Boolean.TRUE, fatalAtPublication.get());
        assertEquals(ProtosProcessRuntime.LifecycleState.TERMINATED,
                processAtPublication.get());
    }

}
