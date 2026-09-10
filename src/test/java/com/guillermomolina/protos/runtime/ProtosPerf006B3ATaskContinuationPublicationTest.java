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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ProtosPerf006B3ATaskContinuationPublicationTest {
    private static final class Dependency implements ProtosTask.WaitDependency {
        private final AtomicInteger cancellationCalls = new AtomicInteger();
        private volatile boolean ready;

        @Override
        public boolean isReady() {
            return ready;
        }

        @Override
        public void waitingTaskCancelled(ProtosTask task) {
            cancellationCalls.incrementAndGet();
        }

        boolean complete(ProtosTask task) {
            ready = true;
            return task.resume(this);
        }

        void markReady() {
            ready = true;
        }

        int cancellationCalls() {
            return cancellationCalls.get();
        }
    }

    @Test
    void pendingCapturePublishesThenResumesThroughCapturedContinuationWithoutReplay() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        Dependency dependency = new Dependency();
        AtomicInteger originalSegments = new AtomicInteger();
        AtomicInteger resumedSegments = new AtomicInteger();

        ProtosTask task =
                domain.createTask(
                        null,
                        current -> {
                            originalSegments.incrementAndGet();
                            assertTrue(current.beginSuspensionCapture(dependency));
                            assertTrue(
                                    current.publishSuspensionContinuation(
                                            dependency,
                                            resumed -> {
                                                resumedSegments.incrementAndGet();
                                                assertTrue(resumed.consumeResume(dependency));
                                                resumed.complete("done");
                                            }));
                        });

        assertTrue(domain.dispatchOne());
        assertEquals(ProtosTask.State.SUSPENDED, task.state());
        assertEquals(0, domain.runnableCount());
        assertEquals(1, originalSegments.get());
        assertEquals(0, resumedSegments.get());

        assertTrue(dependency.complete(task));
        assertEquals(ProtosTask.State.RUNNABLE, task.state());
        assertEquals(1, domain.runnableCount());

        assertTrue(domain.dispatchOne());
        assertEquals(ProtosTask.State.COMPLETED, task.state());
        assertEquals("done", task.result().orElseThrow());
        assertEquals(1, originalSegments.get(), "the original task continuation must not replay");
        assertEquals(1, resumedSegments.get());
    }

    @Test
    void wakeDuringCaptureCannotEnqueueBeforeContinuationPublication() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        Dependency dependency = new Dependency();
        AtomicInteger originalSegments = new AtomicInteger();
        AtomicInteger resumedSegments = new AtomicInteger();

        ProtosTask task =
                domain.createTask(
                        null,
                        current -> {
                            originalSegments.incrementAndGet();
                            assertTrue(current.beginSuspensionCapture(dependency));

                            dependency.markReady();
                            assertTrue(current.resume(dependency));
                            assertEquals(
                                    ProtosTask.State.RUNNING,
                                    current.state(),
                                    "capture-pending remains physically running");
                            assertEquals(
                                    0,
                                    domain.runnableCount(),
                                    "wake cannot enqueue before the continuation exists");

                            assertFalse(
                                    current.publishSuspensionContinuation(
                                            dependency,
                                            resumed -> {
                                                resumedSegments.incrementAndGet();
                                                assertTrue(resumed.consumeResume(dependency));
                                                resumed.complete("done");
                                            }),
                                    "a wake recorded during capture makes the published task runnable");
                            assertEquals(ProtosTask.State.RUNNABLE, current.state());
                            assertEquals(1, domain.runnableCount());
                        });

        assertTrue(domain.dispatchOne());
        assertEquals(ProtosTask.State.RUNNABLE, task.state());
        assertEquals(1, originalSegments.get());
        assertEquals(0, resumedSegments.get());

        assertTrue(domain.dispatchOne());
        assertEquals(ProtosTask.State.COMPLETED, task.state());
        assertEquals(1, originalSegments.get());
        assertEquals(1, resumedSegments.get());
    }

    @Test
    void cancellationDuringCaptureDetachesWaitButCannotEnqueueBeforePublication() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        Dependency dependency = new Dependency();
        AtomicInteger resumedSegments = new AtomicInteger();

        ProtosTask task =
                domain.createTask(
                        null,
                        current -> {
                            assertTrue(current.beginSuspensionCapture(dependency));

                            assertTrue(current.requestCancellation());
                            assertTrue(current.cancellationRequested());
                            assertEquals(ProtosTask.CancellationPhase.REQUESTED, current.cancellationPhase());
                            assertEquals(1, dependency.cancellationCalls());
                            assertEquals(ProtosTask.State.RUNNING, current.state());
                            assertEquals(
                                    0,
                                    domain.runnableCount(),
                                    "cancellation cannot enqueue before continuation publication");

                            assertFalse(
                                    current.publishSuspensionContinuation(
                                            dependency,
                                            resumed -> {
                                                resumedSegments.incrementAndGet();
                                                assertTrue(resumed.cancellationRequested());
                                                assertFalse(
                                                        resumed.consumeResume(dependency),
                                                        "cancellation publication must not expose a dependency resume");
                                                assertTrue(resumed.observeCancellation());
                                            }),
                                    "pending cancellation makes the published task runnable");
                            assertEquals(ProtosTask.State.RUNNABLE, current.state());
                            assertEquals(1, domain.runnableCount());
                        });

        assertTrue(domain.dispatchOne());
        assertEquals(ProtosTask.State.RUNNABLE, task.state());
        assertEquals(1, dependency.cancellationCalls());

        assertTrue(domain.dispatchOne());
        assertEquals(ProtosTask.State.CANCELLED, task.state());
        assertEquals(ProtosTask.CancellationPhase.TERMINAL, task.cancellationPhase());
        assertEquals(1, resumedSegments.get());
    }

    @Test
    void concurrentWakeAndCancellationDuringCaptureStillPublishExactlyOneRunnableEntry()
            throws Exception {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        Dependency dependency = new Dependency();
        AtomicBoolean wakeAccepted = new AtomicBoolean();
        AtomicBoolean cancellationAccepted = new AtomicBoolean();
        AtomicInteger resumedSegments = new AtomicInteger();

        ProtosTask task =
                domain.createTask(
                        null,
                        current -> {
                            assertTrue(current.beginSuspensionCapture(dependency));

                            CountDownLatch start = new CountDownLatch(1);
                            Thread wake =
                                    new Thread(
                                            () -> {
                                                await(start);
                                                dependency.markReady();
                                                wakeAccepted.set(current.resume(dependency));
                                            });
                            Thread cancel =
                                    new Thread(
                                            () -> {
                                                await(start);
                                                cancellationAccepted.set(current.requestCancellation());
                                            });
                            wake.start();
                            cancel.start();
                            start.countDown();
                            join(wake);
                            join(cancel);

                            assertTrue(wakeAccepted.get());
                            assertTrue(cancellationAccepted.get());
                            assertEquals(ProtosTask.CancellationPhase.REQUESTED, current.cancellationPhase());
                            assertEquals(1, dependency.cancellationCalls());
                            assertEquals(ProtosTask.State.RUNNING, current.state());
                            assertEquals(
                                    0,
                                    domain.runnableCount(),
                                    "neither racing signal may enqueue while capture is pending");

                            assertFalse(
                                    current.publishSuspensionContinuation(
                                            dependency,
                                            resumed -> {
                                                resumedSegments.incrementAndGet();
                                                assertTrue(
                                                        resumed.cancellationRequested(),
                                                        "cancellation wins at the resume boundary");
                                                assertFalse(resumed.consumeResume(dependency));
                                                assertTrue(resumed.observeCancellation());
                                            }));
                            assertEquals(1, domain.runnableCount());
                        });

        assertTrue(domain.dispatchOne());
        assertEquals(ProtosTask.State.RUNNABLE, task.state());
        assertEquals(1, domain.runnableCount());

        assertTrue(domain.dispatchOne());
        assertEquals(ProtosTask.State.CANCELLED, task.state());
        assertEquals(1, resumedSegments.get());
        assertEquals(0, domain.runnableCount());
    }

    @Test
    void alreadyReadyOrAlreadyCancelledDependencyDoesNotEnterCapture() {
        ProtosActorExecutionDomain readyDomain = new ProtosActorExecutionDomain();
        Dependency readyDependency = new Dependency();
        readyDependency.markReady();
        ProtosTask ready =
                readyDomain.createTask(
                        null,
                        current -> {
                            assertFalse(current.beginSuspensionCapture(readyDependency));
                            assertEquals(ProtosTask.State.RUNNING, current.state());
                            current.complete("ready");
                        });

        assertTrue(readyDomain.dispatchOne());
        assertEquals(ProtosTask.State.COMPLETED, ready.state());
        assertEquals(0, readyDependency.cancellationCalls());

        ProtosActorExecutionDomain cancelledDomain = new ProtosActorExecutionDomain();
        Dependency cancelledDependency = new Dependency();
        ProtosTask cancelled =
                cancelledDomain.createTask(
                        null,
                        current -> {
                            assertTrue(current.requestCancellation());
                            assertFalse(current.beginSuspensionCapture(cancelledDependency));
                            assertEquals(
                                    1,
                                    cancelledDependency.cancellationCalls(),
                                    "a waiter registered before capture must be detached");
                            assertTrue(current.observeCancellation());
                        });

        assertTrue(cancelledDomain.dispatchOne());
        assertEquals(ProtosTask.State.CANCELLED, cancelled.state());
    }

    @Test
    void publicationRequiresTheExactActiveCaptureAndCannotBeRepeated() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        Dependency expected = new Dependency();
        Dependency other = new Dependency();

        ProtosTask task =
                domain.createTask(
                        null,
                        current -> {
                            assertTrue(current.beginSuspensionCapture(expected));
                            assertThrows(
                                    IllegalStateException.class,
                                    () ->
                                            current.publishSuspensionContinuation(
                                                    other,
                                                    resumed -> resumed.complete("wrong")));

                            assertTrue(
                                    current.publishSuspensionContinuation(
                                            expected,
                                            resumed -> {
                                                assertTrue(resumed.consumeResume(expected));
                                                resumed.complete("done");
                                            }));
                            assertThrows(
                                    IllegalStateException.class,
                                    () ->
                                            current.publishSuspensionContinuation(
                                                    expected,
                                                    resumed -> resumed.complete("twice")));
                        });

        assertTrue(domain.dispatchOne());
        assertEquals(ProtosTask.State.SUSPENDED, task.state());
        assertTrue(expected.complete(task));
        assertTrue(domain.dispatchOne());
        assertEquals(ProtosTask.State.COMPLETED, task.state());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }

    private static void join(Thread thread) {
        try {
            thread.join();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }
}
