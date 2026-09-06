/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActorExecutionDomain;
import com.guillermomolina.protos.runtime.ProtosActorModuleState;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosTask;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ProtosStandardFutureProtocolTest {
    private static ProtosPrelude core() throws Exception {
        return new ProtosCoreBootstrap().bootstrap(Path.of("protos", "lib", "core"));
    }

    private static Object eval(
            ProtosPrelude prelude, ProtosActivation activation, String source) {
        return new ProtosSourceCompiler().compile(source).call(activation);
    }

    // Deliberately Java-side: verifies the actual evaluator suspension bridge,
    // peer progress, and exact-once resume rather than only eventual Future value.
    @Test
    void valueSuspendsRealEvaluatorLetsPeerProgressAndResumesExactlyOnce() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        ProtosActivation activation =
                prelude.newModuleActivation(
                        new ProtosActorModuleState(),
                        null,
                        prelude.newExecutionContext(),
                        domain);
        AtomicInteger before = new AtomicInteger();
        AtomicInteger after = new AtomicInteger();
        AtomicInteger peer = new AtomicInteger();

        activation.context()
                .createLocalSlot(
                        "before",
                        ProtosClosureValue.nativeClosure(
                                (current, supplied) -> {
                                    before.incrementAndGet();
                                    return ProtosNullValue.INSTANCE;
                                }));
        activation.context()
                .createLocalSlot(
                        "after",
                        ProtosClosureValue.nativeClosure(
                                (current, supplied) -> {
                                    after.incrementAndGet();
                                    return supplied.get(0);
                                }));

        ProtosFutureValue pending =
                new ProtosFutureValue(prelude.futurePrototype(), domain);
        activation.context().createLocalSlot("f", pending);

        var target =
                new ProtosSourceCompiler()
                        .compile("before()\nx: f.value()\nafter(x)");

        ProtosTask consumer =
                domain.createTask(null, task -> task.executeProtos(target, activation));
        domain.createTask(
                null,
                task -> {
                    peer.incrementAndGet();
                    pending.resolve(
                            new ProtosIntegerValue(BigInteger.valueOf(7)), activation);
                    task.complete(ProtosNullValue.INSTANCE);
                });

        domain.dispatchOne();
        assertEquals(ProtosTask.State.SUSPENDED, consumer.state());
        assertEquals(1, before.get());

        domain.dispatchOne();
        assertEquals(1, peer.get());

        domain.dispatchOne();
        assertEquals(ProtosTask.State.COMPLETED, consumer.state());
        assertEquals(1, before.get());
        assertEquals(1, after.get());
    }

    // Deliberately Java-side: task cancellation while suspended is scheduler state
    // that the ordinary conformance runner cannot manufacture or inspect.
    @Test
    void cancellationOfWaitingTaskDoesNotCancelObservedFuture() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        ProtosActivation activation =
                prelude.newModuleActivation(
                        new ProtosActorModuleState(),
                        null,
                        prelude.newExecutionContext(),
                        domain);

        ProtosFutureValue future =
                new ProtosFutureValue(prelude.futurePrototype(), domain);
        activation.context().createLocalSlot("f", future);

        var target = new ProtosSourceCompiler().compile("f.value()");
        ProtosTask task =
                domain.createTask(
                        null, current -> current.executeProtos(target, activation));

        domain.dispatchOne();
        assertEquals(ProtosTask.State.SUSPENDED, task.state());

        assertTrue(task.requestCancellation());
        domain.dispatchOne();

        assertEquals(ProtosTask.State.CANCELLED, task.state());
        assertEquals(ProtosFutureValue.State.PENDING, future.state());

        future.resolve(ProtosNullValue.INSTANCE, activation);
        assertFalse(domain.dispatchOne());
    }

    // Deliberately Java-side: the generic source runner can observe a signal but
    // cannot assert the exact stored Error identity or freshness of repeated
    // Cancelled observations.
    @Test
    void failedAndCancelledObservationHaveRequiredIdentity() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        ProtosActivation activation =
                prelude.newModuleActivation(
                        new ProtosActorModuleState(),
                        null,
                        prelude.newExecutionContext(),
                        domain);

        ProtosFutureValue failed =
                new ProtosFutureValue(prelude.futurePrototype(), domain);
        ProtosObjectValue error = ProtosCoreErrors.newError(activation);
        failed.fail(error);
        activation.context().createLocalSlot("f", failed);

        ProtosSignalException failedSignal =
                assertThrows(
                        ProtosSignalException.class,
                        () -> eval(prelude, activation, "f.value()"));
        assertSame(error, failedSignal.error());

        ProtosFutureValue cancelled =
                new ProtosFutureValue(prelude.futurePrototype(), domain);
        cancelled.cancelTerminal();
        activation.context().createLocalSlot("c", cancelled);

        ProtosObjectValue first =
                assertThrows(
                                ProtosSignalException.class,
                                () -> eval(prelude, activation, "c.value()"))
                        .error();
        ProtosObjectValue second =
                assertThrows(
                                ProtosSignalException.class,
                                () -> eval(prelude, activation, "c.value()"))
                        .error();
        assertNotSame(first, second);
    }

    // Success ordering and empty-input behavior already have executable Protos
    // conformance. Retain only deterministic failure-frontier and exact Error identity.
    @Test
    void futureAllFailureUsesAscendingFrontierAndExactError() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        ProtosActivation activation =
                prelude.newModuleActivation(
                        new ProtosActorModuleState(),
                        null,
                        prelude.newExecutionContext(),
                        domain);

        ProtosFutureValue first =
                new ProtosFutureValue(prelude.futurePrototype(), domain);
        ProtosFutureValue second =
                new ProtosFutureValue(prelude.futurePrototype(), domain);
        activation.context().createLocalSlot("f0", first);
        activation.context().createLocalSlot("f1", second);

        ProtosFutureValue aggregate =
                (ProtosFutureValue)
                        eval(prelude, activation, "Future.all(f0, f1)");

        ProtosObjectValue laterError = ProtosCoreErrors.newError(activation);
        second.fail(laterError);

        assertEquals(
                ProtosFutureValue.State.PENDING,
                aggregate.state(),
                "later failure must not overtake unresolved lower index");

        first.resolve(new ProtosIntegerValue(BigInteger.TEN), activation);

        assertEquals(ProtosFutureValue.State.FAILED, aggregate.state());
        assertSame(laterError, aggregate.failedError().orElseThrow());
    }

    // Deliberately Java-side: real host-thread contention validates the internal
    // first-terminal-wins transition without exposing host timing to Protos.
    @Test
    void firstTerminalWinsUnderRace() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosFutureValue future =
                new ProtosFutureValue(
                        prelude.futurePrototype(), activation.executionDomain());
        CountDownLatch go = new CountDownLatch(1);
        Object value = new ProtosObjectValue(ProtosObjectValue.rootObject());

        Thread resolver =
                new Thread(
                        () ->
                                await(
                                        go,
                                        () -> future.resolve(value, activation)));
        Thread canceller =
                new Thread(() -> await(go, future::cancelTerminal));

        resolver.start();
        canceller.start();
        go.countDown();
        resolver.join();
        canceller.join();

        assertNotEquals(ProtosFutureValue.State.PENDING, future.state());
    }

    private static void await(CountDownLatch latch, Runnable action) {
        try {
            latch.await();
            action.run();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }
}
