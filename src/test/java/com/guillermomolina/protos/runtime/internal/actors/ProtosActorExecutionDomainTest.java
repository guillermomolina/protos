/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
 */
package com.guillermomolina.protos.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ProtosActorExecutionDomainTest {
    private static final class Dependency implements ProtosTask.WaitDependency {
        private final AtomicInteger cancellationCalls = new AtomicInteger();

        int cancellationCalls() {
            return cancellationCalls.get();
        }
    }

    @Test
    void actorDomainsRegisterAndDispatchIndependently() {
        ProtosActorExecutionDomain a = new ProtosActorExecutionDomain();
        ProtosActorExecutionDomain b = new ProtosActorExecutionDomain();
        AtomicInteger ranA = new AtomicInteger();
        AtomicInteger ranB = new AtomicInteger();

        ProtosTask ta = a.createTask(null, task -> {
            ranA.incrementAndGet();
            task.complete("a");
        });
        ProtosTask tb = b.createTask(null, task -> {
            ranB.incrementAndGet();
            task.complete("b");
        });

        assertSame(a, ta.owner());
        assertSame(b, tb.owner());
        assertEquals(1, a.runnableCount());
        assertEquals(1, b.runnableCount());

        assertTrue(a.dispatchOne());
        assertEquals(1, ranA.get());
        assertEquals(0, ranB.get());
        assertEquals(ProtosTask.State.COMPLETED, ta.state());
        assertEquals(ProtosTask.State.RUNNABLE, tb.state());
    }

    @Test
    void runningTaskSuspendsAndResumesExactlyOnce() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        Dependency dependency = new Dependency();
        AtomicInteger segments = new AtomicInteger();

        ProtosTask task = domain.createTask(null, current -> {
            if (segments.getAndIncrement() == 0) {
                current.suspend(dependency);
            } else {
                current.complete("done");
            }
        });

        assertTrue(domain.dispatchOne());
        assertEquals(ProtosTask.State.SUSPENDED, task.state());
        assertEquals(0, domain.runnableCount());

        assertTrue(task.resume(dependency));
        assertFalse(task.resume(dependency));
        assertEquals(1, domain.runnableCount());

        assertTrue(domain.dispatchOne());
        assertEquals(ProtosTask.State.COMPLETED, task.state());
        assertEquals(2, segments.get());
    }

    @Test
    void cancellingSuspendedTaskWakesItWithoutTouchingObservedDependency() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        Dependency pendingFutureLikeDependency = new Dependency();
        AtomicInteger segments = new AtomicInteger();

        ProtosTask task = domain.createTask(new Object(), current -> {
            segments.incrementAndGet();
            if (current.cancellationRequested()) {
                assertTrue(current.observeCancellation());
            } else {
                current.suspend(pendingFutureLikeDependency);
            }
        });

        domain.dispatchOne();
        assertEquals(ProtosTask.State.SUSPENDED, task.state());

        assertTrue(task.requestCancellation());
        assertEquals(ProtosTask.State.RUNNABLE, task.state());
        assertTrue(task.waitDependency().isEmpty());
        assertEquals(0, pendingFutureLikeDependency.cancellationCalls());

        domain.dispatchOne();
        assertEquals(ProtosTask.State.CANCELLED, task.state());
        assertEquals(2, segments.get());
        assertEquals(0, pendingFutureLikeDependency.cancellationCalls());
    }

    @Test
    void cancellationRequestedWhileRunningIsObservedCooperatively() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        ProtosTask task = domain.createTask(null, current -> {
            assertTrue(current.requestCancellation());
            assertEquals(ProtosTask.State.RUNNING, current.state());
            assertTrue(current.observeCancellation());
        });

        domain.dispatchOne();
        assertEquals(ProtosTask.State.CANCELLED, task.state());
    }

    @Test
    void cancellationAlreadyPendingWinsAtSuspensionBoundary() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        Dependency dependency = new Dependency();
        ProtosTask task = domain.createTask(null, current -> {
            assertTrue(current.requestCancellation());
            current.suspend(dependency);
        });

        domain.dispatchOne();
        assertEquals(ProtosTask.State.RUNNABLE, task.state());
        assertTrue(task.waitDependency().isEmpty());
        assertEquals(1, domain.runnableCount());
    }

    @Test
    void cancellationLifecycleSeparatesPendingRequestFromTerminalOutcome() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();

        ProtosTask task = domain.createTask(null, current -> {
            assertEquals(ProtosTask.CancellationPhase.NONE, current.cancellationPhase());

            assertTrue(current.requestCancellation());
            assertTrue(current.cancellationRequested());
            assertEquals(
                    ProtosTask.CancellationPhase.REQUESTED,
                    current.cancellationPhase());

            assertFalse(current.requestCancellation(), "one task records one idempotent request");

            assertTrue(current.observeCancellation());
            assertFalse(
                    current.cancellationRequested(),
                    "an observed request is no longer pending for redelivery");
            assertEquals(
                    ProtosTask.CancellationPhase.TERMINAL,
                    current.cancellationPhase());
        });

        assertTrue(domain.dispatchOne());
        assertEquals(ProtosTask.State.CANCELLED, task.state());
        assertEquals(ProtosTask.CancellationPhase.TERMINAL, task.cancellationPhase());
    }

    @Test
    void cancellationUnwindDrainsStructuredChildWithoutReenteringParentContinuation() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        AtomicInteger parentSegments = new AtomicInteger();
        AtomicInteger childSegments = new AtomicInteger();

        ProtosTask parent = domain.createTask(null, current -> {
            parentSegments.incrementAndGet();

            assertTrue(current.requestCancellation());
            assertEquals(
                    ProtosTask.CancellationPhase.REQUESTED,
                    current.cancellationPhase());

            assertTrue(current.observeCancellation());
            assertEquals(ProtosTask.State.SUSPENDED, current.state());
            assertEquals(
                    ProtosTask.CancellationPhase.UNWINDING,
                    current.cancellationPhase());
            assertFalse(
                    current.cancellationRequested(),
                    "the delivered request is not pending during unwind");
            assertFalse(
                    current.requestCancellation(),
                    "the same recorded request remains idempotent during unwind");
        });

        ProtosTask child =
                domain.createTask(
                        parent,
                        null,
                        current -> {
                            childSegments.incrementAndGet();
                            current.complete("unexpected");
                        });

        assertTrue(domain.dispatchOne());
        assertEquals(1, parentSegments.get());
        assertEquals(ProtosTask.State.SUSPENDED, parent.state());
        assertEquals(ProtosTask.CancellationPhase.UNWINDING, parent.cancellationPhase());
        assertEquals(ProtosTask.CancellationPhase.REQUESTED, child.cancellationPhase());

        assertTrue(domain.dispatchOne());
        assertEquals(
                0,
                childSegments.get(),
                "pre-start cancellation must win before child ordinary code");
        assertEquals(ProtosTask.State.CANCELLED, child.state());
        assertEquals(ProtosTask.State.RUNNABLE, parent.state());

        assertTrue(domain.dispatchOne());
        assertEquals(
                1,
                parentSegments.get(),
                "child-drain completion must not re-enter parent ordinary continuation");
        assertEquals(ProtosTask.State.CANCELLED, parent.state());
        assertEquals(ProtosTask.CancellationPhase.TERMINAL, parent.cancellationPhase());
        assertTrue(parent.children().isEmpty());
    }

    @Test
    void activeEnsureDefersCancellationTerminalizationUntilCleanupUnwindFinishes() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();

        ProtosTask task = domain.createTask(null, current -> {
            ProtosDynamicControlState state = current.dynamicControlState();
            ProtosDynamicControlState.Frame ensure =
                    state.enterFrame(new Object(), ProtosDynamicControlState.FrameKind.ENSURE);

            assertTrue(current.requestCancellation());
            assertTrue(current.observeCancellation());

            assertEquals(ProtosTask.State.RUNNING, current.state());
            assertEquals(
                    ProtosTask.CancellationPhase.UNWINDING,
                    current.cancellationPhase());
            assertFalse(current.cancellationRequested());

            state.leaveFrame(ensure);
            assertTrue(current.finishCancellationUnwind());
            assertEquals(ProtosTask.State.CANCELLED, current.state());
            assertEquals(
                    ProtosTask.CancellationPhase.TERMINAL,
                    current.cancellationPhase());
        });

        assertTrue(domain.dispatchOne());
        assertEquals(ProtosTask.State.CANCELLED, task.state());
    }

    @Test
    void laterCleanupTransferSupersedesDeliveredCancellation() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();

        ProtosTask task = domain.createTask(null, current -> {
            ProtosDynamicControlState state = current.dynamicControlState();
            ProtosDynamicControlState.Frame ensure =
                    state.enterFrame(new Object(), ProtosDynamicControlState.FrameKind.ENSURE);

            assertTrue(current.requestCancellation());
            assertTrue(current.observeCancellation());
            assertEquals(
                    ProtosTask.CancellationPhase.UNWINDING,
                    current.cancellationPhase());

            assertTrue(current.supersedeCancellationUnwind());
            assertEquals(
                    ProtosTask.CancellationPhase.SUPERSEDED,
                    current.cancellationPhase());
            assertFalse(current.cancellationRequested());
            assertFalse(current.requestCancellation());

            state.leaveFrame(ensure);
            current.complete("replacement");
        });

        assertTrue(domain.dispatchOne());
        assertEquals(ProtosTask.State.COMPLETED, task.state());
        assertEquals(ProtosTask.CancellationPhase.TERMINAL, task.cancellationPhase());
        assertEquals("replacement", task.result().orElseThrow());
    }

    @Test
    void fifoQueueProvidesDeterministicWeakFairDispatch() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        List<Integer> order = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            int value = i;
            domain.createTask(null, task -> {
                order.add(value);
                task.complete(value);
            });
        }

        domain.dispatchUntilIdle();
        assertEquals(List.of(0, 1, 2, 3), order);
    }

    @Test
    void structuredOwnershipRegistersAndCleansUpChild() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        ProtosTask parent = domain.createTask(null, task -> task.suspend(new Dependency()));
        domain.dispatchOne();

        ProtosTask child = domain.createTask(parent, null, task -> task.complete("child"));
        assertEquals(1, parent.children().size());
        assertSame(parent, child.parent().orElseThrow());

        domain.dispatchOne();
        assertTrue(parent.children().isEmpty());
        assertEquals(ProtosTask.State.COMPLETED, child.state());
    }

    @Test
    void crossActorStructuredOwnershipIsRejected() {
        ProtosActorExecutionDomain a = new ProtosActorExecutionDomain();
        ProtosActorExecutionDomain b = new ProtosActorExecutionDomain();
        ProtosTask parent = a.createTask(null, task -> task.suspend(new Dependency()));
        a.dispatchOne();

        assertThrows(
                IllegalArgumentException.class,
                () -> b.createTask(parent, null, task -> task.complete(null)));
    }

    @Test
    void terminalAndIllegalTransitionsCannotReexecuteOrResume() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        Dependency dependency = new Dependency();
        ProtosTask task = domain.createTask(null, current -> current.complete("done"));

        domain.dispatchOne();
        assertEquals(ProtosTask.State.COMPLETED, task.state());
        assertFalse(task.requestCancellation());
        assertFalse(task.resume(dependency));
        assertFalse(domain.dispatchOne());
        assertThrows(IllegalStateException.class, () -> task.suspend(dependency));
    }

    @Test
    void cancellationAndResumeRaceProducesSingleRunnableEntry() throws Exception {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        Dependency dependency = new Dependency();
        ProtosTask task = domain.createTask(null, current -> current.suspend(dependency));
        domain.dispatchOne();

        CountDownLatch start = new CountDownLatch(1);
        Thread cancel = new Thread(() -> await(start, task::requestCancellation));
        Thread resume = new Thread(() -> await(start, () -> task.resume(dependency)));
        cancel.start();
        resume.start();
        start.countDown();
        cancel.join();
        resume.join();

        assertEquals(1, domain.runnableCount());
        assertEquals(ProtosTask.State.RUNNABLE, task.state());
    }

    @Test
    void freshRootTaskRunsFirstSegmentDirectlyWithoutQueueRoundTrip() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        AtomicInteger wakeups = new AtomicInteger();
        domain.bindSchedulerWakeup(wakeups::incrementAndGet);
        AtomicInteger queuedDuringSegment = new AtomicInteger(-1);

        ProtosTask task = domain.runFreshRootTaskDirectly(current -> {
            queuedDuringSegment.set(domain.runnableCount());
            assertEquals(ProtosTask.State.RUNNING, current.state());
            assertEquals(1, domain.liveTaskCount());
            current.complete("done");
        });

        assertEquals(0, queuedDuringSegment.get());
        assertEquals(0, wakeups.get());
        assertEquals(0, domain.runnableCount());
        assertEquals(0, domain.liveTaskCount());
        assertSame(domain, task.owner());
        assertEquals(ProtosTask.State.COMPLETED, task.state());
        assertEquals("done", task.result().orElseThrow());
    }

    @Test
    void directRootTaskCancellationReachesTerminalState() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();

        ProtosTask cancelled = domain.runFreshRootTaskDirectly(current -> {
            assertTrue(current.requestCancellation());
            assertTrue(current.observeCancellation());
        });

        assertEquals(ProtosTask.State.CANCELLED, cancelled.state());
        assertEquals(0, domain.liveTaskCount());
        assertEquals(0, domain.runnableCount());
    }

    @Test
    void directRootTaskSuspensionResumesThroughOrdinaryQueue() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        Dependency dependency = new Dependency();
        AtomicInteger segments = new AtomicInteger();

        ProtosTask task = domain.runFreshRootTaskDirectly(current -> {
            if (segments.getAndIncrement() == 0) {
                current.suspend(dependency);
            } else {
                current.complete("resumed");
            }
        });

        assertEquals(ProtosTask.State.SUSPENDED, task.state());
        assertEquals(1, domain.liveTaskCount());
        assertEquals(0, domain.runnableCount());

        assertTrue(task.resume(dependency));
        assertEquals(1, domain.runnableCount());

        domain.dispatchUntilTerminal(task, () -> false);
        assertEquals(ProtosTask.State.COMPLETED, task.state());
        assertEquals("resumed", task.result().orElseThrow());
        assertEquals(2, segments.get());
    }

    @Test
    void directRootTaskStartedOnTerminatingActorIsCancelledBeforeFirstInstruction() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        ProtosActor actor =
                new ProtosActor(
                        new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze(),
                        domain,
                        new ProtosActorModuleState(),
                        1);
        // A live suspended Task keeps the Actor TERMINATING instead of completing termination.
        ProtosTask holder = domain.createTask(null, current -> current.suspend(new Dependency()));
        domain.dispatchOne();
        assertTrue(actor.beginTermination());
        AtomicInteger ordinarySegments = new AtomicInteger();

        ProtosTask root =
                domain.runFreshRootTaskDirectly(current -> ordinarySegments.incrementAndGet());

        assertEquals(0, ordinarySegments.get());
        assertEquals(ProtosTask.State.CANCELLED, root.state());
        assertEquals(ProtosTask.CancellationPhase.TERMINAL, root.cancellationPhase());
        // Only the woken holder is queued; the root Task never entered the runnable queue.
        assertEquals(1, domain.runnableCount());
        assertSame(holder, domain.nextRunnableForTesting().orElseThrow());
    }

    @Test
    void directRootTaskIsRejectedOnTerminatedActor() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        ProtosActor actor =
                new ProtosActor(
                        new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze(),
                        domain,
                        new ProtosActorModuleState(),
                        1);
        actor.requestTerminationForRuntime();
        assertEquals(ProtosActor.LifecycleState.TERMINATED, actor.lifecycleState());

        assertThrows(
                IllegalStateException.class,
                () -> domain.runFreshRootTaskDirectly(current -> current.complete(null)));
        assertEquals(0, domain.liveTaskCount());
    }

    private static ProtosFutureValue pendingFuture(ProtosActorExecutionDomain domain) {
        return new ProtosFutureValue(
                new ProtosObjectValue(ProtosObjectValue.rootObject()), domain);
    }

    @Test
    void nonTaskFutureRegistryIsIdempotentByIdentityAndRetiresOnTerminal() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        ProtosFutureValue first = pendingFuture(domain);
        ProtosFutureValue second = pendingFuture(domain);

        domain.registerActorNonTaskFuture(first);
        domain.registerActorNonTaskFuture(first);
        domain.registerActorNonTaskFuture(second);
        assertEquals(2, domain.actorNonTaskFutureCountForTesting());

        // Removing one identity leaves the other registered; repeated removal is a no-op.
        domain.terminalActorNonTaskFuture(first);
        domain.terminalActorNonTaskFuture(first);
        assertEquals(1, domain.actorNonTaskFutureCountForTesting());

        // A terminal transition retires the registration through its observer.
        assertTrue(second.cancelRequest());
        assertEquals(ProtosFutureValue.State.CANCELLED, second.state());
        assertEquals(0, domain.actorNonTaskFutureCountForTesting());

        // A non-pending Future is never registered.
        domain.registerActorNonTaskFuture(second);
        assertEquals(0, domain.actorNonTaskFutureCountForTesting());
    }

    @Test
    void releaseAndLifecycleCleanupRegistriesAreIdempotentAndGateTerminationCleanup()
            throws Exception {
        ProtosPrelude prelude =
                new com.guillermomolina.protos.execution.ProtosCoreBootstrap()
                        .bootstrap(java.nio.file.Path.of("protos", "lib", "core"));
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        ProtosActivation activation =
                prelude.newModuleActivation(
                        new ProtosActorModuleState(), null, prelude.newExecutionContext(), domain);
        ProtosIoLifecycle lifecycle =
                new ProtosIoLifecycle(
                        new ProtosObjectValue(ProtosObjectValue.rootObject()),
                        prelude.futurePrototype(),
                        domain,
                        ignored -> {});
        ProtosIoLifecycle.ReleaseCompletion completion =
                new ProtosIoLifecycle.ReleaseCompletion() {
                    @Override
                    public void succeeded() {}

                    @Override
                    public void failed(ProtosObjectValue error) {}
                };
        ProtosIoReleaseExecution release =
                new ProtosIoReleaseExecution(lifecycle, activation, completion);
        ProtosIoReleaseExecution unregistered =
                new ProtosIoReleaseExecution(lifecycle, activation, completion);

        assertFalse(domain.hasTerminationCleanupForRuntime());
        domain.registerActorIoReleaseForRuntime(release);
        domain.registerActorIoReleaseForRuntime(release);
        assertEquals(1, domain.actorIoReleaseCountForTesting());
        assertTrue(domain.hasTerminationCleanupForRuntime());

        // Only a registered release can be published as runnable.
        int runnableBefore = domain.runnableCount();
        domain.enqueueActorIoReleaseForRuntime(unregistered);
        assertEquals(runnableBefore, domain.runnableCount());

        domain.terminalActorIoReleaseForRuntime(unregistered);
        assertEquals(1, domain.actorIoReleaseCountForTesting());
        domain.terminalActorIoReleaseForRuntime(release);
        domain.terminalActorIoReleaseForRuntime(release);
        assertEquals(0, domain.actorIoReleaseCountForTesting());
        assertFalse(domain.hasTerminationCleanupForRuntime());

        domain.registerActorIoLifecycleCleanupForRuntime(lifecycle);
        domain.registerActorIoLifecycleCleanupForRuntime(lifecycle);
        assertEquals(1, domain.actorIoLifecycleCleanupCountForTesting());
        assertTrue(domain.hasTerminationCleanupForRuntime());
        domain.terminalActorIoLifecycleCleanupForRuntime(lifecycle);
        domain.terminalActorIoLifecycleCleanupForRuntime(lifecycle);
        assertEquals(0, domain.actorIoLifecycleCleanupCountForTesting());
        assertFalse(domain.hasTerminationCleanupForRuntime());

        // Operations: one entry per identity, retired exactly by identity.
        ProtosIoOperation operation = lifecycle.beginOperation(activation);
        assertEquals(1, domain.actorIoOperationCountForTesting());
        domain.registerActorIoOperation(operation);
        assertEquals(1, domain.actorIoOperationCountForTesting());
        assertTrue(operation.commit());
        assertTrue(operation.resolve(ProtosNullValue.INSTANCE));
        assertEquals(0, domain.actorIoOperationCountForTesting());
        domain.terminalActorIoOperation(operation);
        assertEquals(0, domain.actorIoOperationCountForTesting());
    }

    @Test
    void terminationSnapshotCancelsEveryRegisteredTaskAndFutureExactlyOnce() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        Dependency dependency = new Dependency();
        List<ProtosTask> tasks = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            tasks.add(domain.createTask(null, current -> current.suspend(dependency)));
        }
        while (domain.dispatchOne()) {
            // Suspend every Task.
        }
        List<ProtosFutureValue> futures = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            ProtosFutureValue future = pendingFuture(domain);
            domain.registerActorNonTaskFuture(future);
            futures.add(future);
        }
        assertEquals(3, domain.liveTaskCount());
        assertEquals(3, domain.actorNonTaskFutureCountForTesting());

        domain.actorTerminationBegun();

        for (ProtosTask task : tasks) {
            assertTrue(task.cancellationRequested());
            assertEquals(ProtosTask.State.RUNNABLE, task.state());
        }
        for (ProtosFutureValue future : futures) {
            assertEquals(ProtosFutureValue.State.CANCELLED, future.state());
        }
        // Futures retired from the live registry while termination iterated its snapshot.
        assertEquals(0, domain.actorNonTaskFutureCountForTesting());
        assertEquals(0, dependency.cancellationCalls());
        assertTrue(domain.actorTerminationBegunForRuntime());
    }

    @Test
    void terminationIteratesSnapshotsIndependentOfRegistriesMutatedByCallbacks() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        List<ProtosFutureValue> lateFutures = new ArrayList<>();
        List<ProtosFutureValue> futures = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            ProtosFutureValue future = pendingFuture(domain);
            // Each cancellation callback mutates the live registry while termination iterates.
            future.attachCancellationProducer(
                    () -> {
                        ProtosFutureValue late = pendingFuture(domain);
                        lateFutures.add(late);
                        domain.registerActorNonTaskFuture(late);
                        future.cancelTerminal();
                    });
            domain.registerActorNonTaskFuture(future);
            futures.add(future);
        }

        domain.actorTerminationBegun();

        for (ProtosFutureValue future : futures) {
            assertEquals(ProtosFutureValue.State.CANCELLED, future.state());
        }
        // Futures registered after the snapshot are not part of this termination pass.
        assertEquals(3, lateFutures.size());
        for (ProtosFutureValue late : lateFutures) {
            assertEquals(ProtosFutureValue.State.PENDING, late.state());
        }
        assertEquals(3, domain.actorNonTaskFutureCountForTesting());
    }

    @Test
    void concurrentRegistrationRacingTerminationLeavesNoStaleOrDuplicateEntries()
            throws Exception {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        int perThread = 200;
        int threads = 4;
        List<List<ProtosFutureValue>> registered = new ArrayList<>();
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> workers = new ArrayList<>();
        for (int worker = 0; worker < threads; worker++) {
            List<ProtosFutureValue> own = new ArrayList<>();
            registered.add(own);
            Thread thread =
                    new Thread(
                            () ->
                                    await(
                                            start,
                                            () -> {
                                                for (int index = 0; index < perThread; index++) {
                                                    ProtosFutureValue future =
                                                            pendingFuture(domain);
                                                    own.add(future);
                                                    domain.registerActorNonTaskFuture(future);
                                                    domain.registerActorNonTaskFuture(future);
                                                }
                                            }));
            workers.add(thread);
            thread.start();
        }
        start.countDown();
        domain.actorTerminationBegun();
        for (Thread thread : workers) {
            thread.join();
        }

        // Each Future is either cancelled by the termination snapshot (and retired) or was
        // registered after it and remains registered exactly once.
        int stillPending = 0;
        for (List<ProtosFutureValue> own : registered) {
            assertEquals(perThread, own.size());
            for (ProtosFutureValue future : own) {
                ProtosFutureValue.State state = future.state();
                assertTrue(
                        state == ProtosFutureValue.State.CANCELLED
                                || state == ProtosFutureValue.State.PENDING);
                if (state == ProtosFutureValue.State.PENDING) {
                    stillPending++;
                }
            }
        }
        assertEquals(stillPending, domain.actorNonTaskFutureCountForTesting());
    }

    private static void await(CountDownLatch latch, Runnable action) {
        try {
            latch.await();
            action.run();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }
}
