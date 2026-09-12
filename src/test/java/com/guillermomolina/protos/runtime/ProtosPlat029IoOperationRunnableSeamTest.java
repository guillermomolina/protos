/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class ProtosPlat029IoOperationRunnableSeamTest {
    @Test
    void backendReadinessOnlyQueuesAndActorDispatchRunsOperationWithoutHiddenTask() throws Exception {
        Fixture fixture = fixture();
        ProtosIoOperation operation = fixture.lifecycle.beginOperation(fixture.activation);
        AtomicBoolean ran = new AtomicBoolean();
        AtomicReference<Thread> executionThread = new AtomicReference<>();
        operation.installDeferredCPrimeExecutionForRuntime(current -> {
            assertSame(operation, current);
            ran.set(true);
            executionThread.set(Thread.currentThread());
        });

        AtomicReference<Throwable> backendFailure = new AtomicReference<>();
        Thread backend = new Thread(() -> {
            try {
                assertTrue(operation.requestDeferredCPrimeRunForRuntime());
                assertFalse(ran.get(), "readiness publication must not execute operation work inline");
            } catch (Throwable failure) {
                backendFailure.set(failure);
            }
        }, "plat029-readiness-producer");
        backend.start();
        backend.join();
        if (backendFailure.get() != null) {
            throw new AssertionError("backend readiness thread failed", backendFailure.get());
        }

        assertFalse(ran.get());
        assertEquals(1, fixture.domain.runnableCount());
        assertEquals(0, fixture.domain.liveTaskCount());
        Thread actorCarrier = Thread.currentThread();
        assertTrue(fixture.domain.dispatchOne());
        assertTrue(ran.get());
        assertSame(actorCarrier, executionThread.get());
        assertEquals(0, fixture.domain.liveTaskCount());
        assertFalse(fixture.domain.dispatchOne());

        assertTrue(operation.commit());
        assertTrue(operation.resolve(ProtosNullValue.INSTANCE));
        System.out.println("PLAT029_BACKEND_THREAD_GUEST_REENTRY=NO");
        System.out.println("PLAT029_HIDDEN_TASK_TOPOLOGY=NO");
    }

    @Test
    void runningOperationCoalescesRepeatedReadinessIntoOneLaterSegment() throws Exception {
        Fixture fixture = fixture();
        ProtosIoOperation operation = fixture.lifecycle.beginOperation(fixture.activation);
        AtomicInteger runs = new AtomicInteger();
        operation.installDeferredCPrimeExecutionForRuntime(current -> {
            int run = runs.incrementAndGet();
            if (run == 1) {
                assertTrue(current.deferredCPrimeRunningForTesting());
                assertTrue(current.requestDeferredCPrimeRunForRuntime());
                assertFalse(current.requestDeferredCPrimeRunForRuntime());
            }
        });

        assertTrue(operation.requestDeferredCPrimeRunForRuntime());
        assertFalse(operation.requestDeferredCPrimeRunForRuntime());
        assertTrue(operation.deferredCPrimeQueuedForTesting());
        assertTrue(fixture.domain.dispatchOne());
        assertEquals(1, runs.get());
        assertTrue(operation.deferredCPrimeQueuedForTesting());
        assertEquals(1, fixture.domain.runnableCount());
        assertTrue(fixture.domain.dispatchOne());
        assertEquals(2, runs.get());
        assertFalse(operation.deferredCPrimeQueuedForTesting());
        assertFalse(operation.deferredCPrimeRunningForTesting());
        assertFalse(fixture.domain.dispatchOne());

        assertTrue(operation.commit());
        assertTrue(operation.resolve(ProtosNullValue.INSTANCE));
        System.out.println("PLAT029_OPERATION_RESCHEDULE_COALESCING=PASS");
    }

    @Test
    void terminalOperationLeavesNoRunnableExecutionEvenWhenAlreadyQueued() throws Exception {
        Fixture fixture = fixture();
        ProtosIoOperation operation = fixture.lifecycle.beginOperation(fixture.activation);
        AtomicInteger runs = new AtomicInteger();
        operation.installDeferredCPrimeExecutionForRuntime(current -> runs.incrementAndGet());

        assertTrue(operation.requestDeferredCPrimeRunForRuntime());
        assertEquals(1, fixture.domain.runnableCount());
        assertTrue(operation.future().cancelRequest());
        assertTrue(operation.terminal());
        assertEquals(ProtosFutureValue.State.CANCELLED, operation.future().state());
        assertFalse(fixture.domain.dispatchOne());
        assertEquals(0, runs.get());
        assertEquals(0, fixture.domain.runnableCount());
        assertEquals(0, fixture.domain.actorIoOperationCountForTesting());
        System.out.println("PLAT029_TERMINAL_QUEUED_OPERATION_EXECUTION=SKIPPED");
    }

    @Test
    void taskAndIoOperationShareActorFifoWithoutChangingTaskOwnership() throws Exception {
        Fixture fixture = fixture();
        List<String> order = new ArrayList<>();
        ProtosTask task = fixture.domain.createTask(
                null,
                current -> {
                    order.add("task");
                    current.complete(ProtosNullValue.INSTANCE);
                });
        ProtosIoOperation operation = fixture.lifecycle.beginOperation(fixture.activation);
        operation.installDeferredCPrimeExecutionForRuntime(current -> order.add("io"));
        assertTrue(operation.requestDeferredCPrimeRunForRuntime());

        assertEquals(1, fixture.domain.liveTaskCount());
        fixture.domain.dispatchUntilIdle();
        assertEquals(List.of("task", "io"), order);
        assertEquals(ProtosTask.State.COMPLETED, task.state());
        assertEquals(0, fixture.domain.liveTaskCount());
        assertFalse(operation.terminal());

        assertTrue(operation.commit());
        assertTrue(operation.resolve(ProtosNullValue.INSTANCE));
        System.out.println("PLAT029_TASK_OPERATION_FIFO_QUEUE=PASS");
        System.out.println("PLAT029_OPERATION_TASK_IDENTITY=NONE");
    }

    private static Fixture fixture() throws Exception {
        ProtosPrelude prelude =
                new ProtosCoreBootstrap().bootstrap(Path.of("protos", "lib", "core"));
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        ProtosActivation activation =
                prelude.newModuleActivation(
                        new ProtosActorModuleState(),
                        null,
                        prelude.newExecutionContext(),
                        domain);
        ProtosObjectValue receiver = new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosIoLifecycle lifecycle =
                new ProtosIoLifecycle(
                        receiver,
                        prelude.futurePrototype(),
                        domain,
                        completion -> completion.succeeded());
        return new Fixture(prelude, domain, activation, lifecycle);
    }

    private record Fixture(
            ProtosPrelude prelude,
            ProtosActorExecutionDomain domain,
            ProtosActivation activation,
            ProtosIoLifecycle lifecycle) {}
}
