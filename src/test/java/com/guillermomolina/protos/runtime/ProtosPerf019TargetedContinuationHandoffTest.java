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
package com.guillermomolina.protos.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

final class ProtosPerf019TargetedContinuationHandoffTest {
    @Test
    void targetedCompletionHandsOffOnlyItsExactContinuationAndPreservesUnrelatedFifo() {
        ProtosObjectValue context =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosActivation activation =
                ProtosTestPrelude.activation(context, List.of(), context);
        ProtosActorExecutionDomain domain = activation.executionDomain();
        ProtosFutureValue future =
                new ProtosFutureValue(
                        new ProtosObjectValue(ProtosObjectValue.rootObject()),
                        domain);

        List<String> order = new ArrayList<>();
        AtomicInteger targetSegments = new AtomicInteger();
        AtomicReference<ProtosTask.WaitDependency> dependency =
                new AtomicReference<>();
        AtomicReference<Supplier<Object>> resumer =
                new AtomicReference<>();

        ProtosTask target =
                domain.createTask(
                        null,
                        task -> {
                            if (targetSegments.getAndIncrement() == 0) {
                                activation.attachTask(task);
                                Object marker =
                                        future.observeValueForContinuationForRuntime(
                                                activation,
                                                (waiting, resume) -> {
                                                    dependency.set(waiting);
                                                    resumer.set(resume);
                                                    assertTrue(task.suspend(waiting));
                                                    return ProtosNullValue.INSTANCE;
                                                });
                                assertSame(ProtosNullValue.INSTANCE, marker);
                                return;
                            }

                            assertTrue(task.consumeResume(dependency.get()));
                            ProtosStringValue resolved =
                                    assertInstanceOf(
                                            ProtosStringValue.class,
                                            resumer.get().get());
                            assertEquals("done", resolved.value());
                            order.add("target");
                            task.complete("target");
                        });

        assertTrue(domain.dispatchOne());
        assertEquals(ProtosTask.State.SUSPENDED, target.state());

        ProtosTask first =
                domain.createTask(
                        null,
                        task -> {
                            order.add("first");
                            task.complete("first");
                        });
        ProtosTask second =
                domain.createTask(
                        null,
                        task -> {
                            order.add("second");
                            task.complete("second");
                        });

        domain.enqueueTargetedFutureCompletionForRuntime(
                future,
                () ->
                        future.resolve(
                                new ProtosStringValue("done"),
                                activation));

        assertEquals(3, domain.runnableCount());

        assertTrue(domain.dispatchOne());

        assertEquals(List.of("target"), order);
        assertEquals(ProtosTask.State.COMPLETED, target.state());
        assertEquals(ProtosTask.State.RUNNABLE, first.state());
        assertEquals(ProtosTask.State.RUNNABLE, second.state());
        assertEquals(2, domain.runnableCount());

        domain.dispatchUntilIdle();

        assertEquals(List.of("target", "first", "second"), order);
        assertEquals(ProtosTask.State.COMPLETED, first.state());
        assertEquals(ProtosTask.State.COMPLETED, second.state());
    }

    @Test
    void cancelledExactWaiterFallsBackWithoutPreferentialExecution() {
        ProtosObjectValue context =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosActivation activation =
                ProtosTestPrelude.activation(context, List.of(), context);
        ProtosActorExecutionDomain domain = activation.executionDomain();
        ProtosFutureValue future =
                new ProtosFutureValue(
                        new ProtosObjectValue(ProtosObjectValue.rootObject()),
                        domain);

        List<String> order = new ArrayList<>();
        AtomicInteger targetSegments = new AtomicInteger();

        ProtosTask target =
                domain.createTask(
                        null,
                        task -> {
                            if (targetSegments.getAndIncrement() == 0) {
                                activation.attachTask(task);
                                Object marker =
                                        future.observeValueForContinuationForRuntime(
                                                activation,
                                                (waiting, resume) -> {
                                                    assertTrue(task.suspend(waiting));
                                                    return ProtosNullValue.INSTANCE;
                                                });
                                assertSame(ProtosNullValue.INSTANCE, marker);
                                return;
                            }

                            if (task.cancellationRequested()) {
                                assertTrue(task.observeCancellation());
                                return;
                            }
                            fail("cancelled waiter resumed as ordinary continuation work");
                        });

        assertTrue(domain.dispatchOne());
        assertEquals(ProtosTask.State.SUSPENDED, target.state());

        ProtosTask unrelated =
                domain.createTask(
                        null,
                        task -> {
                            order.add("unrelated");
                            task.complete("unrelated");
                        });

        assertTrue(target.requestCancellation());

        domain.enqueueTargetedFutureCompletionForRuntime(
                future,
                () ->
                        future.resolve(
                                new ProtosStringValue("done"),
                                activation));

        assertTrue(domain.dispatchOne());

        assertTrue(order.isEmpty());
        assertEquals(ProtosFutureValue.State.RESOLVED, future.state());
        assertEquals(ProtosTask.State.RUNNABLE, unrelated.state());
        assertEquals(ProtosTask.State.RUNNABLE, target.state());

        assertTrue(domain.dispatchOne());
        assertEquals(List.of("unrelated"), order);
        assertEquals(ProtosTask.State.COMPLETED, unrelated.state());

        assertTrue(domain.dispatchOne());
        assertEquals(ProtosTask.State.CANCELLED, target.state());
        assertEquals(List.of("unrelated"), order);
    }

    @Test
    void twoWaitersAreBothWokenThroughOrdinaryResumptionWithoutTargetedHandoff() {
        ProtosObjectValue context =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosActivation activation =
                ProtosTestPrelude.activation(context, List.of(), context);
        ProtosActorExecutionDomain domain = activation.executionDomain();
        ProtosFutureValue future =
                new ProtosFutureValue(
                        new ProtosObjectValue(ProtosObjectValue.rootObject()),
                        domain);

        List<String> order = new ArrayList<>();
        AtomicInteger firstSegments = new AtomicInteger();
        AtomicInteger secondSegments = new AtomicInteger();
        AtomicInteger resolutions = new AtomicInteger();
        ProtosTask first =
                waitingTask(domain, activation, future, firstSegments, "first", order);
        ProtosTask second =
                waitingTask(domain, activation, future, secondSegments, "second", order);

        assertTrue(domain.dispatchOne());
        assertTrue(domain.dispatchOne());
        assertEquals(ProtosTask.State.SUSPENDED, first.state());
        assertEquals(ProtosTask.State.SUSPENDED, second.state());
        assertEquals(2, future.waiterCountForTesting());

        domain.enqueueTargetedFutureCompletionForRuntime(
                future,
                () -> {
                    resolutions.incrementAndGet();
                    return future.resolve(new ProtosStringValue("done"), activation);
                });

        // Two waiters are never a sole terminal waiter: the completion only readies both,
        // and neither continuation runs preferentially inside the completion dispatch.
        assertTrue(domain.dispatchOne());
        assertEquals(1, resolutions.get());
        assertEquals(ProtosFutureValue.State.RESOLVED, future.state());
        assertEquals(0, future.waiterCountForTesting());
        assertTrue(order.isEmpty());
        assertEquals(ProtosTask.State.RUNNABLE, first.state());
        assertEquals(ProtosTask.State.RUNNABLE, second.state());
        assertEquals(2, domain.runnableCount());

        domain.dispatchUntilIdle();

        assertEquals(List.of("first", "second"), order);
        assertEquals(ProtosTask.State.COMPLETED, first.state());
        assertEquals(ProtosTask.State.COMPLETED, second.state());
        assertEquals(2, firstSegments.get());
        assertEquals(2, secondSegments.get());
        assertEquals(1, resolutions.get());
        assertEquals(0, future.waiterCountForTesting());
    }

    private static ProtosTask waitingTask(
            ProtosActorExecutionDomain domain,
            ProtosActivation activation,
            ProtosFutureValue future,
            AtomicInteger segments,
            String name,
            List<String> order) {
        AtomicReference<ProtosTask.WaitDependency> dependency =
                new AtomicReference<>();
        AtomicReference<Supplier<Object>> resumer =
                new AtomicReference<>();
        return domain.createTask(
                null,
                task -> {
                    if (segments.getAndIncrement() == 0) {
                        ProtosActivation taskActivation =
                                ProtosActivation.withPreludeAndModuleState(
                                        new ProtosObjectValue(ProtosObjectValue.rootObject()),
                                        activation.capturedLexicalEnvironmentForRuntime(),
                                        activation.receiver(),
                                        activation.preludeOrNullForRuntime(),
                                        activation.actorModuleState(),
                                        null,
                                        domain);
                        taskActivation.attachTask(task);
                        Object marker =
                                future.observeValueForContinuationForRuntime(
                                        taskActivation,
                                        (waiting, resume) -> {
                                            dependency.set(waiting);
                                            resumer.set(resume);
                                            assertTrue(task.suspend(waiting));
                                            return ProtosNullValue.INSTANCE;
                                        });
                        assertSame(ProtosNullValue.INSTANCE, marker);
                        return;
                    }

                    assertTrue(task.consumeResume(dependency.get()));
                    ProtosStringValue resolved =
                            assertInstanceOf(
                                    ProtosStringValue.class,
                                    resumer.get().get());
                    assertEquals("done", resolved.value());
                    order.add(name);
                    task.complete(name);
                });
    }
}
