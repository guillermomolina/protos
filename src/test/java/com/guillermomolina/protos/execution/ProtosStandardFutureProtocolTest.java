/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
 * DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
 * DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
 * OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS OF
 * THE LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY
 * OF THE LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING
 * THE CONTENTS OF THIS FILE. IF A COPY OF THE LICENSE DOES NOT ACCOMPANY THIS
 * FILE, A COPY OF THE LICENSE MAY ALSO BE OBTAINED AT THE FOLLOWING WEB SITE:
 * https://github.com/guillermomolina/protos
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActor;
import com.guillermomolina.protos.runtime.ProtosActorExecutionDomain;
import com.guillermomolina.protos.runtime.ProtosActorModuleState;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
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

    // Deliberately Java-side: language conformance validates E3 outcomes, while this
    // test inspects the runtime-only intermediate state at the exact cleanup suspension point.
    @Test
    void cancellationFutureRemainsPendingWhileEnsureCleanupIsSuspended() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        ProtosActivation activation =
                prelude.newModuleActivation(
                        new ProtosActorModuleState(),
                        null,
                        prelude.newExecutionContext(),
                        domain);

        ProtosFutureValue cleanupGate =
                new ProtosFutureValue(prelude.futurePrototype(), domain);
        activation.context().createLocalSlot("cleanupGate", cleanupGate);

        ProtosFutureValue future =
                (ProtosFutureValue)
                        eval(
                                prelude,
                                activation,
                                "future: (() => {\n"
                                        + "    bodyDependency: Future.all().then((values) => 7).detach()\n"
                                        + "    (() => {\n"
                                        + "        future.cancel()\n"
                                        + "        bodyDependency.value()\n"
                                        + "        41\n"
                                        + "    }).ensure(() => {\n"
                                        + "        cleanupGate.value()\n"
                                        + "        null\n"
                                        + "    })\n"
                                        + "}).future()\n"
                                        + "future");

        ProtosTask producer = future.producerTask().orElseThrow();

        assertTrue(domain.dispatchOne());
        assertEquals(ProtosFutureValue.State.PENDING, future.state());
        assertEquals(ProtosTask.State.SUSPENDED, producer.state());
        assertEquals(
                ProtosTask.CancellationPhase.UNWINDING,
                producer.cancellationPhase());
        assertFalse(
                producer.cancellationRequested(),
                "already-honored cancellation must be shielded during cleanup suspension");

        cleanupGate.resolve(ProtosNullValue.INSTANCE, activation);
        domain.dispatchUntilIdle();

        assertEquals(ProtosFutureValue.State.CANCELLED, future.state());
        assertEquals(ProtosTask.State.CANCELLED, producer.state());
        assertEquals(
                ProtosTask.CancellationPhase.TERMINAL,
                producer.cancellationPhase());
    }

    // Deliberately Java-side: this verifies an intermediate Actor lifecycle state
    // while cancellation cleanup is suspended. The final cancellation/cleanup behavior itself
    // is covered by executable Protos conformance.
    @Test
    void actorTerminationWaitsForSuspendingCancellationCleanup() throws Exception {
        ProtosPrelude prelude = core();
        ProtosObjectValue actorRefPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosActor actor = new ProtosActor(actorRefPrototype);
        assertTrue(actor.markReady());

        ProtosActorExecutionDomain domain = actor.executionDomain();
        ProtosActivation activation =
                prelude.newModuleActivation(
                        actor.moduleState(),
                        null,
                        prelude.newExecutionContext(),
                        domain);

        ProtosFutureValue bodyGate =
                new ProtosFutureValue(prelude.futurePrototype(), domain);
        ProtosFutureValue cleanupGate =
                new ProtosFutureValue(prelude.futurePrototype(), domain);
        activation.context().createLocalSlot("bodyGate", bodyGate);
        activation.context().createLocalSlot("cleanupGate", cleanupGate);

        ProtosFutureValue producer =
                (ProtosFutureValue)
                        eval(
                                prelude,
                                activation,
                                "future: (() => {\n"
                                        + "    (() => {\n"
                                        + "        bodyGate.value()\n"
                                        + "        41\n"
                                        + "    }).ensure(() => {\n"
                                        + "        cleanupGate.value()\n"
                                        + "        null\n"
                                        + "    })\n"
                                        + "}).future()\n"
                                        + "future");

        assertTrue(domain.dispatchOne());
        assertEquals(ProtosFutureValue.State.PENDING, producer.state());
        assertEquals(1, domain.liveTaskCount());

        actor.requestTerminationForRuntime();
        assertEquals(ProtosActor.LifecycleState.TERMINATING, actor.lifecycleState());

        assertTrue(domain.dispatchOne());
        ProtosTask producerTask = producer.producerTask().orElseThrow();
        assertEquals(ProtosTask.State.SUSPENDED, producerTask.state());
        assertEquals(
                ProtosTask.CancellationPhase.UNWINDING,
                producerTask.cancellationPhase());
        assertEquals(ProtosFutureValue.State.PENDING, producer.state());
        assertEquals(
                ProtosActor.LifecycleState.TERMINATING,
                actor.lifecycleState(),
                "Actor termination must wait for cancellation cleanup");

        cleanupGate.resolve(ProtosNullValue.INSTANCE, activation);
        domain.dispatchUntilIdle();

        assertEquals(ProtosFutureValue.State.CANCELLED, producer.state());
        assertEquals(ProtosTask.State.CANCELLED, producerTask.state());
        assertEquals(ProtosActor.LifecycleState.TERMINATED, actor.lifecycleState());
        assertEquals(0, domain.liveTaskCount());
        assertEquals(
                ProtosFutureValue.State.PENDING,
                bodyGate.state(),
                "cancelling the waiter must not cancel its observed dependency");
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
