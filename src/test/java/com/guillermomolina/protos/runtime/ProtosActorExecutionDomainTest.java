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
