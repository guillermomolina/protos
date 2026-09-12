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

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class ProtosPerf006B6A6A2TaskTerminalLifecycleTest {
    @Test
    void completedTaskRunsLifecycleExactlyOnceAfterDomainTerminalPublication() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<ProtosTask.State> terminal = new AtomicReference<>();
        AtomicReference<Object> outcome = new AtomicReference<>();

        ProtosTask task =
                domain.createTask(
                        null,
                        current -> {
                            current.installTerminalLifecycleForRuntime(
                                    (owned, terminalState, terminalOutcome) -> {
                                        assertSame(current, owned);
                                        assertEquals(ProtosTask.State.COMPLETED, owned.state());
                                        assertEquals(
                                                0,
                                                domain.liveTaskCount(),
                                                "domain bookkeeping must precede PLAT027 finalization");
                                        terminal.set(terminalState);
                                        outcome.set(terminalOutcome);
                                        calls.incrementAndGet();
                                    });
                            current.complete("done");
                        });

        assertTrue(domain.dispatchOne());
        assertEquals(ProtosTask.State.COMPLETED, task.state());
        assertEquals("done", task.result().orElseThrow());
        assertEquals(ProtosTask.State.COMPLETED, terminal.get());
        assertEquals("done", outcome.get());
        assertEquals(1, calls.get());

        assertFalse(task.requestCancellation());
        assertFalse(domain.dispatchOne());
        assertEquals(1, calls.get(), "terminal lifecycle is one-shot");
    }

    @Test
    void lifecycleWaitsForStructuredChildDrainBeforeCompletionPublication() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        AtomicInteger parentSegments = new AtomicInteger();
        AtomicInteger calls = new AtomicInteger();

        ProtosTask parent =
                domain.createTask(
                        null,
                        current -> {
                            parentSegments.incrementAndGet();
                            current.installTerminalLifecycleForRuntime(
                                    (owned, terminalState, terminalOutcome) -> {
                                        assertEquals(ProtosTask.State.COMPLETED, terminalState);
                                        assertEquals("parent", terminalOutcome);
                                        assertTrue(owned.children().isEmpty());
                                        calls.incrementAndGet();
                                    });
                            domain.createTask(
                                    current,
                                    null,
                                    child -> child.complete("child"));
                            current.complete("parent");
                        });

        assertTrue(domain.dispatchOne());
        assertEquals(ProtosTask.State.SUSPENDED, parent.state());
        assertEquals(0, calls.get());

        assertTrue(domain.dispatchOne());
        assertEquals(ProtosTask.State.RUNNABLE, parent.state());
        assertEquals(0, calls.get());

        assertTrue(domain.dispatchOne());
        assertEquals(ProtosTask.State.COMPLETED, parent.state());
        assertEquals(1, parentSegments.get(), "child drain must not re-enter ordinary parent code");
        assertEquals(1, calls.get());
    }

    @Test
    void failedAndCancelledTasksDeliverExactTerminalOutcome() {
        ProtosActorExecutionDomain failureDomain = new ProtosActorExecutionDomain();
        Object error = new Object();
        AtomicReference<ProtosTask.State> failedState = new AtomicReference<>();
        AtomicReference<Object> failedOutcome = new AtomicReference<>();

        ProtosTask failed =
                failureDomain.createTask(
                        null,
                        current -> {
                            current.installTerminalLifecycleForRuntime(
                                    (owned, terminalState, terminalOutcome) -> {
                                        failedState.set(terminalState);
                                        failedOutcome.set(terminalOutcome);
                                    });
                            current.fail(error);
                        });

        assertTrue(failureDomain.dispatchOne());
        assertEquals(ProtosTask.State.FAILED, failed.state());
        assertEquals(ProtosTask.State.FAILED, failedState.get());
        assertSame(error, failedOutcome.get());

        ProtosActorExecutionDomain cancellationDomain = new ProtosActorExecutionDomain();
        AtomicReference<ProtosTask.State> cancelledState = new AtomicReference<>();
        AtomicReference<Object> cancelledOutcome = new AtomicReference<>(new Object());

        ProtosTask cancelled =
                cancellationDomain.createTask(
                        null,
                        current -> {
                            current.installTerminalLifecycleForRuntime(
                                    (owned, terminalState, terminalOutcome) -> {
                                        cancelledState.set(terminalState);
                                        cancelledOutcome.set(terminalOutcome);
                                    });
                            assertTrue(current.requestCancellation());
                            assertTrue(current.observeCancellation());
                        });

        assertTrue(cancellationDomain.dispatchOne());
        assertEquals(ProtosTask.State.CANCELLED, cancelled.state());
        assertEquals(ProtosTask.State.CANCELLED, cancelledState.get());
        assertNull(cancelledOutcome.get());
    }

    @Test
    void lifecycleCardinalityIsOneAndPostTerminalInstallationIsRejected() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        AtomicInteger calls = new AtomicInteger();

        ProtosTask task =
                domain.createTask(
                        null,
                        current -> {
                            current.installTerminalLifecycleForRuntime(
                                    (owned, terminalState, terminalOutcome) ->
                                            calls.incrementAndGet());
                            assertThrows(
                                    IllegalStateException.class,
                                    () ->
                                            current.installTerminalLifecycleForRuntime(
                                                    (owned, terminalState, terminalOutcome) -> {}));
                            current.complete("done");
                        });

        assertTrue(domain.dispatchOne());
        assertEquals(1, calls.get());
        assertThrows(
                IllegalStateException.class,
                () ->
                        task.installTerminalLifecycleForRuntime(
                                (owned, terminalState, terminalOutcome) -> {}));
    }

    @Test
    void escapingLifecycleFailureRemainsHostInvariantFailureAfterTaskTerminalization() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        AtomicReference<ProtosTask> captured = new AtomicReference<>();

        ProtosTask task =
                domain.createTask(
                        null,
                        current -> {
                            captured.set(current);
                            current.installTerminalLifecycleForRuntime(
                                    (owned, terminalState, terminalOutcome) -> {
                                        throw new LifecycleInvariantFailure();
                                    });
                            current.complete("done");
                        });

        assertThrows(LifecycleInvariantFailure.class, domain::dispatchOne);
        assertSame(task, captured.get());
        assertEquals(ProtosTask.State.COMPLETED, task.state());
        assertEquals("done", task.result().orElseThrow());
        assertEquals(0, domain.liveTaskCount());
    }

    private static final class LifecycleInvariantFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
