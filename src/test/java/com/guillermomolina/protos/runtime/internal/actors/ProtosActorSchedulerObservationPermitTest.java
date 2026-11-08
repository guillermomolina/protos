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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** D228-A1 exclusive, non-blocking Actor observation permits. */
final class ProtosActorSchedulerObservationPermitTest {
    @Test
    void idleActorIsGranted() {
        ProtosActorScheduler scheduler = new ProtosActorScheduler(new ManualExecutor(), 1);
        ProtosActor actor = actor();
        scheduler.attach(actor);

        Optional<ProtosActorScheduler.ObservationPermit> permit =
                scheduler.tryAcquireObservation(actor);
        assertTrue(permit.isPresent());
        permit.orElseThrow().close();
    }

    @Test
    void runningActorIsDeniedWithoutWaiting() throws Exception {
        Executor threads = command -> {
            Thread carrier = new Thread(command, "d228-permit-test");
            carrier.setDaemon(true);
            carrier.start();
        };
        ProtosActorScheduler scheduler = new ProtosActorScheduler(threads, 1);
        ProtosActor actor = actor();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);

        scheduler.attach(actor);
        actor.markReady();
        assertTrue(actor.tryAcceptMessageForRuntime(task -> {
            entered.countDown();
            await(release);
            task.complete(null);
            completed.countDown();
        }));

        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            var attempt = CompletableFuture.supplyAsync(
                    () -> scheduler.tryAcquireObservation(actor));
            assertTrue(attempt.get(2, TimeUnit.SECONDS).isEmpty());
        } finally {
            release.countDown();
        }
        assertTrue(completed.await(2, TimeUnit.SECONDS));
    }

    @Test
    void livePermitPreventsSegmentsUntilReleased() {
        ManualExecutor carriers = new ManualExecutor();
        ProtosActorScheduler scheduler = new ProtosActorScheduler(carriers, 1);
        ProtosActor actor = actor();
        List<String> order = new ArrayList<>();
        scheduler.attach(actor);
        actor.markReady();

        var permit = scheduler.tryAcquireObservation(actor).orElseThrow();
        assertTrue(actor.tryAcceptMessageForRuntime(turn(order, "message")));
        carriers.runAll();
        assertTrue(order.isEmpty());

        permit.close();
        carriers.runAll();
        assertEquals(List.of("message"), order);
    }

    @Test
    void secondSimultaneousPermitIsDenied() {
        ProtosActorScheduler scheduler = new ProtosActorScheduler(new ManualExecutor(), 1);
        ProtosActor actor = actor();
        scheduler.attach(actor);

        var first = scheduler.tryAcquireObservation(actor).orElseThrow();
        assertTrue(scheduler.tryAcquireObservation(actor).isEmpty());
        first.close();
        first.close();
        scheduler.tryAcquireObservation(actor).orElseThrow().close();
    }

    @Test
    void queuedWorkIsKeptAndRequeuedOnRelease() {
        ManualExecutor carriers = new ManualExecutor();
        ProtosActorScheduler scheduler = new ProtosActorScheduler(carriers, 1);
        ProtosActor actor = actor();
        List<String> order = new ArrayList<>();
        scheduler.attach(actor);
        actor.markReady();
        assertTrue(actor.tryAcceptMessageForRuntime(turn(order, "first")));
        assertTrue(actor.tryAcceptMessageForRuntime(turn(order, "second")));

        var permit = scheduler.tryAcquireObservation(actor).orElseThrow();
        carriers.runAll();
        assertTrue(order.isEmpty());
        assertEquals(2, actor.mailboxForRuntime().size());

        permit.close();
        carriers.runAll();
        assertEquals(List.of("first", "second"), order);
    }

    @Test
    void otherActorsProgressIndependently() {
        ManualExecutor carriers = new ManualExecutor();
        ProtosActorScheduler scheduler = new ProtosActorScheduler(carriers, 1);
        ProtosActor observed = actor();
        ProtosActor other = actor();
        List<String> order = new ArrayList<>();
        scheduler.attach(observed);
        scheduler.attach(other);
        observed.markReady();
        other.markReady();

        var permit = scheduler.tryAcquireObservation(observed).orElseThrow();
        assertTrue(observed.tryAcceptMessageForRuntime(turn(order, "observed")));
        assertTrue(other.tryAcceptMessageForRuntime(turn(order, "other")));
        carriers.runAll();
        assertEquals(List.of("other"), order);

        permit.close();
        carriers.runAll();
        assertEquals(List.of("other", "observed"), order);
    }

    @Test
    void permitIsReleasedWhenTheObserverFails() {
        ProtosActorScheduler scheduler = new ProtosActorScheduler(new ManualExecutor(), 1);
        ProtosActor actor = actor();
        scheduler.attach(actor);

        assertThrows(IllegalStateException.class, () -> {
            try (var permit = scheduler.tryAcquireObservation(actor).orElseThrow()) {
                assertNotNull(permit);
                throw new IllegalStateException("observer failure");
            }
        });
        scheduler.tryAcquireObservation(actor).orElseThrow().close();
    }

    @Test
    void terminatingOrDetachedTerminalActorsAreDenied() {
        ProtosActorScheduler scheduler = new ProtosActorScheduler(new ManualExecutor(), 1);
        ProtosActor actor = actor();
        scheduler.attach(actor);
        actor.markReady();
        assertTrue(actor.beginTermination());
        assertTrue(scheduler.tryAcquireObservation(actor).isEmpty());

        scheduler.detach(actor);
        assertTrue(scheduler.tryAcquireObservation(actor).isEmpty());
    }

    @Test
    void unattachedLiveActorPermitDefersALaterAttach() {
        ManualExecutor carriers = new ManualExecutor();
        ProtosActorScheduler scheduler = new ProtosActorScheduler(carriers, 1);
        ProtosActor actor = actor();
        List<String> order = new ArrayList<>();

        var permit = scheduler.tryAcquireObservation(actor).orElseThrow();
        assertTrue(scheduler.tryAcquireObservation(actor).isEmpty());
        assertThrows(IllegalStateException.class,
                () -> scheduler.submitControl(actor, () -> order.add("control")));

        scheduler.attach(actor);
        actor.markReady();
        assertTrue(actor.tryAcceptMessageForRuntime(turn(order, "message")));
        carriers.runAll();
        assertTrue(order.isEmpty());

        permit.close();
        carriers.runAll();
        assertEquals(List.of("message"), order);
    }

    @Test
    void unattachedPermitReleasedBeforeAttachRetainsNoState() {
        ManualExecutor carriers = new ManualExecutor();
        ProtosActorScheduler scheduler = new ProtosActorScheduler(carriers, 1);
        ProtosActor actor = actor();
        List<String> order = new ArrayList<>();

        scheduler.tryAcquireObservation(actor).orElseThrow().close();

        // No placeholder survives: control is still rejected, and attach is a
        // fresh, complete attachment rather than an "already attached" error.
        assertThrows(IllegalStateException.class,
                () -> scheduler.submitControl(actor, () -> order.add("control")));
        scheduler.attach(actor);
        actor.markReady();
        assertTrue(actor.tryAcceptMessageForRuntime(turn(order, "message")));
        carriers.runAll();
        assertEquals(List.of("message"), order);
    }

    @Test
    void concurrentAttachDuringPermitStartsNoTurnUntilRelease() throws Exception {
        ManualExecutor carriers = new ManualExecutor();
        ProtosActorScheduler scheduler = new ProtosActorScheduler(carriers, 1);
        ProtosActor actor = actor();
        List<String> order = new ArrayList<>();

        var permit = scheduler.tryAcquireObservation(actor).orElseThrow();
        CompletableFuture.runAsync(() -> scheduler.attach(actor)).get(2, TimeUnit.SECONDS);
        assertTrue(scheduler.tryAcquireObservation(actor).isEmpty());

        // The adopted State now owns the ordinary wakeups and control queue.
        scheduler.submitControl(actor, () -> order.add("control"));
        carriers.runAll();
        assertTrue(order.isEmpty());

        permit.close();
        carriers.runAll();
        assertEquals(List.of("control"), order);
        assertThrows(IllegalStateException.class, () -> scheduler.attach(actor));
    }

    @Test
    void detachDuringPermitKeepsExclusionAndReleaseDoesNotResurrect() {
        ManualExecutor carriers = new ManualExecutor();
        ProtosActorScheduler scheduler = new ProtosActorScheduler(carriers, 1);
        ProtosActor actor = actor();
        List<String> order = new ArrayList<>();
        scheduler.attach(actor);
        scheduler.submitControl(actor, () -> order.add("control"));

        var permit = scheduler.tryAcquireObservation(actor).orElseThrow();
        scheduler.detach(actor);
        assertTrue(scheduler.tryAcquireObservation(actor).isEmpty());
        assertThrows(IllegalStateException.class,
                () -> scheduler.submitControl(actor, () -> order.add("late")));

        permit.close();
        carriers.runAll();
        assertTrue(order.isEmpty());
        assertThrows(IllegalStateException.class,
                () -> scheduler.submitControl(actor, () -> order.add("late")));
    }

    @Test
    void terminationDuringPermitDeniesLaterObservers() {
        ManualExecutor carriers = new ManualExecutor();
        ProtosActorScheduler scheduler = new ProtosActorScheduler(carriers, 1);
        ProtosActor actor = actor();
        scheduler.attach(actor);
        actor.markReady();

        var permit = scheduler.tryAcquireObservation(actor).orElseThrow();
        assertTrue(actor.beginTermination());
        assertTrue(scheduler.tryAcquireObservation(actor).isEmpty());
        permit.close();
        carriers.runAll();
        assertTrue(scheduler.tryAcquireObservation(actor).isEmpty());
    }

    @Test
    void failedAttachDuringPermitKeepsExclusionWithoutOrphans() {
        ProtosActorScheduler owner = new ProtosActorScheduler(new ManualExecutor(), 1);
        ProtosActorScheduler other = new ProtosActorScheduler(new ManualExecutor(), 1);
        ProtosActor actor = actor();
        owner.attach(actor);

        var permit = other.tryAcquireObservation(actor).orElseThrow();
        // The wakeups are already bound to owner, so this attach fails part-way.
        assertThrows(IllegalStateException.class, () -> other.attach(actor));
        assertTrue(other.tryAcquireObservation(actor).isEmpty());
        assertThrows(IllegalStateException.class,
                () -> other.submitControl(actor, () -> { }));

        permit.close();
        assertThrows(IllegalStateException.class,
                () -> other.submitControl(actor, () -> { }));
        other.tryAcquireObservation(actor).orElseThrow().close();
    }

    @Test
    void concurrentObserversGetAtMostOnePermit() throws Exception {
        ProtosActorScheduler scheduler = new ProtosActorScheduler(new ManualExecutor(), 1);
        ProtosActor actor = actor();
        scheduler.attach(actor);
        CountDownLatch start = new CountDownLatch(1);

        List<CompletableFuture<Optional<ProtosActorScheduler.ObservationPermit>>> attempts =
                new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            attempts.add(CompletableFuture.supplyAsync(() -> {
                await(start);
                return scheduler.tryAcquireObservation(actor);
            }));
        }
        start.countDown();

        int granted = 0;
        for (var attempt : attempts) {
            var permit = attempt.get(2, TimeUnit.SECONDS);
            if (permit.isPresent()) {
                granted++;
            }
        }
        assertEquals(1, granted);
        for (var attempt : attempts) {
            attempt.get().ifPresent(ProtosActorScheduler.ObservationPermit::close);
        }
        scheduler.tryAcquireObservation(actor).orElseThrow().close();
    }

    private static ProtosActor actor() {
        return new ProtosActor(
                new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze(),
                new ProtosActorExecutionDomain(),
                new ProtosActorModuleState(),
                4);
    }

    /** I158: a suspended exclusive bootstrap denies observation and excludes ordinary work. */
    @Test
    void heldExclusiveBootstrapDeniesObservationAndKeepsOtherActorsRunning() {
        ManualExecutor carriers = new ManualExecutor();
        ProtosActorScheduler scheduler = new ProtosActorScheduler(carriers, 1);
        ProtosActor initializing = actor();
        ProtosActor other = actor();
        List<String> order = new ArrayList<>();
        boolean[] ready = {false};
        int[] resumed = {0};
        ProtosActorScheduler.ExclusiveBootstrap bootstrap =
                new ProtosActorScheduler.ExclusiveBootstrap() {
                    @Override
                    public boolean resumeReady() {
                        return ready[0] && resumed[0] == 0;
                    }

                    @Override
                    public void resume() {
                        resumed[0]++;
                    }
                };
        scheduler.attach(initializing);
        scheduler.attach(other);
        other.markReady();
        assertTrue(scheduler.holdExclusiveBootstrap(initializing, bootstrap));

        assertTrue(scheduler.tryAcquireObservation(initializing).isEmpty());
        var permit = scheduler.tryAcquireObservation(other).orElseThrow();
        permit.close();
        assertTrue(other.tryAcceptMessageForRuntime(turn(order, "other")));
        carriers.runAll();
        assertEquals(List.of("other"), order);
        assertEquals(0, resumed[0]);

        ready[0] = true;
        scheduler.wakeForRuntime(initializing);
        carriers.runAll();
        assertEquals(1, resumed[0]);

        // Termination makes the custodian stale: it is never resumed again and permits stay denied.
        initializing.requestTerminationForRuntime();
        scheduler.wakeForRuntime(initializing);
        carriers.runAll();
        assertEquals(1, resumed[0]);
        assertTrue(scheduler.tryAcquireObservation(initializing).isEmpty());
    }

    /**
     * I158: a deferred bootstrap abandon runs during TERMINATING with priority over a stale
     * resumption, exactly once, and holds off TERMINATED until it ran.
     */
    @Test
    void deferredBootstrapCleanupRunsOnceBeforeAnyStaleResumption() {
        ManualExecutor carriers = new ManualExecutor();
        ProtosActorScheduler scheduler = new ProtosActorScheduler(carriers, 1);
        ProtosActor initializing = actor();
        List<String> order = new ArrayList<>();
        boolean[] cleanup = {false};
        ProtosActorScheduler.ExclusiveBootstrap bootstrap =
                new ProtosActorScheduler.ExclusiveBootstrap() {
                    @Override
                    public boolean resumeReady() {
                        return true;
                    }

                    @Override
                    public void resume() {
                        order.add("resume");
                    }

                    @Override
                    public boolean cleanupPending() {
                        return cleanup[0];
                    }

                    @Override
                    public void runCleanup() {
                        order.add("cleanup");
                        cleanup[0] = false;
                        initializing.executionDomain().finishBootstrapCleanupForRuntime();
                    }
                };
        scheduler.attach(initializing);
        assertTrue(scheduler.holdExclusiveBootstrap(initializing, bootstrap));

        // Invalidation from a foreign thread: cleanup pending, termination cannot complete yet.
        cleanup[0] = true;
        initializing.executionDomain().beginBootstrapCleanupForRuntime();
        initializing.requestTerminationForRuntime();
        assertEquals(ProtosActor.LifecycleState.TERMINATING, initializing.lifecycleState());
        assertTrue(scheduler.tryAcquireObservation(initializing).isEmpty());

        scheduler.wakeForRuntime(initializing);
        carriers.runAll();

        assertEquals(List.of("cleanup"), order);
        assertEquals(ProtosActor.LifecycleState.TERMINATED, initializing.lifecycleState());
        scheduler.wakeForRuntime(initializing);
        carriers.runAll();
        assertEquals(List.of("cleanup"), order);
    }

    private static ProtosTask.Continuation turn(List<String> order, String value) {
        return task -> {
            order.add(value);
            task.complete(null);
        };
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }

    private static final class ManualExecutor implements Executor {
        private final ArrayDeque<Runnable> queued = new ArrayDeque<>();

        @Override
        public synchronized void execute(Runnable command) {
            queued.addLast(command);
        }

        void runAll() {
            while (true) {
                Runnable command;
                synchronized (this) {
                    command = queued.pollFirst();
                }
                if (command == null) {
                    return;
                }
                command.run();
            }
        }
    }

    /** I158: a permit acquired before the bootstrap control starts defers it until release. */
    @Test
    void permitBeforeBootstrapStartDefersTheControlUntilRelease() {
        ManualExecutor carriers = new ManualExecutor();
        ProtosActorScheduler scheduler = new ProtosActorScheduler(carriers, 1);
        ProtosActor initializing = actor();
        List<String> order = new ArrayList<>();
        scheduler.attach(initializing);

        var permit = scheduler.tryAcquireObservation(initializing).orElseThrow();
        scheduler.submitControl(initializing, () -> order.add("bootstrap"));
        carriers.runAll();
        assertTrue(order.isEmpty());

        permit.close();
        carriers.runAll();
        assertEquals(List.of("bootstrap"), order);
    }

    /** I158: a safe completion runs during the hold while a pending Task stays excluded. */
    @Test
    void safeCompletionProgressesWhileAPendingTaskStaysExcluded() {
        ManualExecutor carriers = new ManualExecutor();
        ProtosActorScheduler scheduler = new ProtosActorScheduler(carriers, 1);
        ProtosActor initializing = actor();
        List<String> order = new ArrayList<>();
        ProtosActorScheduler.ExclusiveBootstrap bootstrap =
                new ProtosActorScheduler.ExclusiveBootstrap() {
                    @Override
                    public boolean resumeReady() {
                        return false;
                    }

                    @Override
                    public void resume() {
                        order.add("resume");
                    }
                };
        scheduler.attach(initializing);
        assertTrue(scheduler.holdExclusiveBootstrap(initializing, bootstrap));
        ProtosActorExecutionDomain domain = initializing.executionDomain();
        domain.createTask(null, null, task -> {
            order.add("task");
            task.complete(null);
        });
        ProtosFutureValue future =
                new ProtosFutureValue(new ProtosObjectValue(ProtosObjectValue.rootObject()), domain);
        domain.enqueueTargetedFutureCompletionForRuntime(
                future,
                () -> {
                    order.add("completion");
                    return true;
                });
        carriers.runAll();

        assertEquals(List.of("completion"), order);
        assertEquals(1, domain.runnableCount(), "the Task is retained, not dropped");

        scheduler.releaseExclusiveBootstrap(initializing, bootstrap);
        carriers.runAll();
        assertEquals(List.of("completion", "task"), order);
    }

    /** I158: detaching never revives a terminated Actor whose cleanup already ran. */
    @Test
    void detachAfterCleanupDoesNotResurrectTheActor() {
        ManualExecutor carriers = new ManualExecutor();
        ProtosActorScheduler scheduler = new ProtosActorScheduler(carriers, 1);
        ProtosActor initializing = actor();
        int[] cleanups = {0};
        boolean[] pending = {true};
        ProtosActorScheduler.ExclusiveBootstrap bootstrap =
                new ProtosActorScheduler.ExclusiveBootstrap() {
                    @Override
                    public boolean resumeReady() {
                        return false;
                    }

                    @Override
                    public void resume() {}

                    @Override
                    public boolean cleanupPending() {
                        return pending[0];
                    }

                    @Override
                    public void runCleanup() {
                        cleanups[0]++;
                        pending[0] = false;
                        initializing.executionDomain().finishBootstrapCleanupForRuntime();
                    }
                };
        scheduler.attach(initializing);
        assertTrue(scheduler.holdExclusiveBootstrap(initializing, bootstrap));
        initializing.executionDomain().beginBootstrapCleanupForRuntime();
        initializing.requestTerminationForRuntime();
        scheduler.wakeForRuntime(initializing);
        carriers.runAll();
        assertEquals(1, cleanups[0]);
        assertEquals(ProtosActor.LifecycleState.TERMINATED, initializing.lifecycleState());

        scheduler.detach(initializing);
        scheduler.wakeForRuntime(initializing);
        carriers.runAll();
        assertEquals(1, cleanups[0]);
        assertTrue(scheduler.tryAcquireObservation(initializing).isEmpty());
    }

    /**
     * I158: the PERF019 targeted handoff is really reachable here (a single suspended Task waiter
     * registered through the ordinary Future.value() continuation path), yet under an exclusive
     * bootstrap hold the completion terminalizes the Future and makes the Task runnable without
     * running its continuation. Releasing the hold lets the Task progress normally.
     */
    @Test
    void exclusiveHoldBlocksTheRealTargetedHandoffToASuspendedTaskWaiter() {
        ProtosObjectValue context = new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosActivation activation = ProtosTestPrelude.activation(context, List.of(), context);
        ProtosActorExecutionDomain domain = activation.executionDomain();
        ProtosActor initializing =
                new ProtosActor(
                        new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze(),
                        domain,
                        new ProtosActorModuleState(),
                        4);
        ProtosFutureValue future =
                new ProtosFutureValue(new ProtosObjectValue(ProtosObjectValue.rootObject()), domain);
        List<String> order = new ArrayList<>();
        int[] segments = {0};
        ProtosTask.WaitDependency[] dependency = new ProtosTask.WaitDependency[1];
        java.util.function.Supplier<?>[] resumer = new java.util.function.Supplier<?>[1];
        ProtosTask waiter =
                domain.createTask(
                        null,
                        task -> {
                            if (segments[0]++ == 0) {
                                activation.attachTask(task);
                                future.observeValueForContinuationForRuntime(
                                        activation,
                                        (waiting, resume) -> {
                                            dependency[0] = waiting;
                                            resumer[0] = resume;
                                            assertTrue(task.suspend(waiting));
                                            return ProtosNullValue.INSTANCE;
                                        });
                                return;
                            }
                            assertTrue(task.consumeResume(dependency[0]));
                            resumer[0].get();
                            order.add("waiter");
                            task.complete(null);
                        });
        // Run the Task until it registered its ordinary waiter and suspended.
        assertTrue(domain.dispatchOne());
        assertEquals(ProtosTask.State.SUSPENDED, waiter.state());

        ManualExecutor carriers = new ManualExecutor();
        ProtosActorScheduler scheduler = new ProtosActorScheduler(carriers, 1);
        ProtosActorScheduler.ExclusiveBootstrap bootstrap =
                new ProtosActorScheduler.ExclusiveBootstrap() {
                    @Override
                    public boolean resumeReady() {
                        return false;
                    }

                    @Override
                    public void resume() {
                        order.add("bootstrap");
                    }
                };
        scheduler.attach(initializing);
        assertTrue(scheduler.holdExclusiveBootstrap(initializing, bootstrap));

        domain.enqueueTargetedFutureCompletionForRuntime(
                future, () -> future.resolve(new ProtosStringValue("done"), activation));
        carriers.runAll();

        assertEquals(ProtosFutureValue.State.RESOLVED, future.state());
        assertEquals(ProtosTask.State.RUNNABLE, waiter.state(), "eligible, not handed off");
        assertTrue(order.isEmpty());

        scheduler.releaseExclusiveBootstrap(initializing, bootstrap);
        carriers.runAll();
        assertEquals(List.of("waiter"), order);
        assertEquals(ProtosTask.State.COMPLETED, waiter.state());
    }

    /** I158: termination with a prepared resumption runs only the deferred cleanup, once. */
    @Test
    void terminationWithAPreparedResumptionRunsOnlyTheCleanup() {
        ManualExecutor carriers = new ManualExecutor();
        ProtosActorScheduler scheduler = new ProtosActorScheduler(carriers, 1);
        ProtosActor initializing = actor();
        List<String> order = new ArrayList<>();
        boolean[] cleanupPending = {false};
        ProtosActorScheduler.ExclusiveBootstrap bootstrap =
                new ProtosActorScheduler.ExclusiveBootstrap() {
                    @Override
                    public boolean resumeReady() {
                        return !cleanupPending[0];
                    }

                    @Override
                    public void resume() {
                        order.add("resume");
                    }

                    @Override
                    public boolean cleanupPending() {
                        return cleanupPending[0];
                    }

                    @Override
                    public void runCleanup() {
                        order.add("cleanup");
                        cleanupPending[0] = false;
                        initializing.executionDomain().finishBootstrapCleanupForRuntime();
                    }
                };
        scheduler.attach(initializing);
        assertTrue(scheduler.holdExclusiveBootstrap(initializing, bootstrap)); // resume queued
        // Invalidation wins before the queued carrier runs.
        cleanupPending[0] = true;
        initializing.executionDomain().beginBootstrapCleanupForRuntime();
        initializing.requestTerminationForRuntime();
        carriers.runAll();
        scheduler.wakeForRuntime(initializing);
        carriers.runAll();

        assertEquals(List.of("cleanup"), order);
        assertEquals(ProtosActor.LifecycleState.TERMINATED, initializing.lifecycleState());
    }

    /** I158: a deferred abandon runs while another Task stays in runnable; only cleanup runs. */
    @Test
    void pendingAbandonRunsWhileAnotherTaskStaysRunnable() {
        ManualExecutor carriers = new ManualExecutor();
        ProtosActorScheduler scheduler = new ProtosActorScheduler(carriers, 1);
        ProtosActor initializing = actor();
        List<String> order = new ArrayList<>();
        boolean[] pending = {false};
        ProtosActorScheduler.ExclusiveBootstrap bootstrap =
                new ProtosActorScheduler.ExclusiveBootstrap() {
                    @Override
                    public boolean resumeReady() {
                        return false;
                    }

                    @Override
                    public void resume() {}

                    @Override
                    public boolean cleanupPending() {
                        return pending[0];
                    }

                    @Override
                    public void runCleanup() {
                        order.add("cleanup");
                        pending[0] = false;
                        initializing.executionDomain().finishBootstrapCleanupForRuntime();
                    }
                };
        scheduler.attach(initializing);
        assertTrue(scheduler.holdExclusiveBootstrap(initializing, bootstrap));
        initializing.executionDomain().createTask(null, null, task -> {
            order.add("task");
            task.complete(null);
        });
        carriers.runAll();
        assertTrue(order.isEmpty(), "Task excluded by the hold");

        pending[0] = true;
        initializing.executionDomain().beginBootstrapCleanupForRuntime();
        initializing.requestTerminationForRuntime();
        scheduler.wakeForRuntime(initializing);
        carriers.runAll();

        assertEquals("cleanup", order.get(0), "the abandon runs before any other work");
        assertEquals(1, java.util.Collections.frequency(order, "cleanup"));
    }

    /** I158: releasing a permit while a host thread publishes a targeted completion. */
    @Test
    void permitReleaseRacingATargetedCompletionRunsEachCompletionOnce() throws Exception {
        ManualExecutor carriers = new ManualExecutor();
        ProtosActorScheduler scheduler = new ProtosActorScheduler(carriers, 1);
        ProtosActor actor = actor();
        ProtosActorExecutionDomain domain = actor.executionDomain();
        scheduler.attach(actor);
        List<String> order = java.util.Collections.synchronizedList(new ArrayList<>());
        var permit = scheduler.tryAcquireObservation(actor).orElseThrow();
        ProtosFutureValue first =
                new ProtosFutureValue(new ProtosObjectValue(ProtosObjectValue.rootObject()), domain);
        ProtosFutureValue second =
                new ProtosFutureValue(new ProtosObjectValue(ProtosObjectValue.rootObject()), domain);
        domain.enqueueTargetedFutureCompletionForRuntime(
                first,
                () -> {
                    order.add("first");
                    return true;
                });
        carriers.runAll();
        assertTrue(order.isEmpty(), "the permit excludes the completion");

        java.util.concurrent.CyclicBarrier start = new java.util.concurrent.CyclicBarrier(2);
        Thread host =
                new Thread(
                        () -> {
                            try {
                                start.await(5, TimeUnit.SECONDS);
                            } catch (Exception broken) {
                                throw new IllegalStateException(broken);
                            }
                            domain.enqueueTargetedFutureCompletionForRuntime(
                                    second,
                                    () -> {
                                        order.add("second");
                                        return true;
                                    });
                        },
                        "i158-permit-race");
        host.setDaemon(true);
        host.start();
        start.await(5, TimeUnit.SECONDS);
        permit.close();
        host.join(TimeUnit.SECONDS.toMillis(5));
        assertFalse(host.isAlive());
        carriers.runAll();

        assertEquals(List.of("first", "second"), order);
        assertFalse(domain.hasTargetedRuntimeCompletionForRuntime());
    }

    /** I158: termination just before a safe completion refuses it; nothing runs or resumes. */
    @Test
    void terminationJustBeforeASafeCompletionRefusesIt() {
        ManualExecutor carriers = new ManualExecutor();
        ProtosActorScheduler scheduler = new ProtosActorScheduler(carriers, 1);
        ProtosActor initializing = actor();
        List<String> order = new ArrayList<>();
        ProtosActorScheduler.ExclusiveBootstrap bootstrap = recordingBootstrap(order);
        scheduler.attach(initializing);
        assertTrue(scheduler.holdExclusiveBootstrap(initializing, bootstrap));
        ProtosActorExecutionDomain domain = initializing.executionDomain();
        ProtosFutureValue future =
                new ProtosFutureValue(new ProtosObjectValue(ProtosObjectValue.rootObject()), domain);

        initializing.requestTerminationForRuntime();
        assertThrows(
                IllegalStateException.class,
                () ->
                        domain.enqueueTargetedFutureCompletionForRuntime(
                                future,
                                () -> {
                                    order.add("completion");
                                    return true;
                                }));
        carriers.runAll();

        assertTrue(order.isEmpty());
        assertEquals(ProtosFutureValue.State.PENDING, future.state());
        assertEquals(ProtosActor.LifecycleState.TERMINATED, initializing.lifecycleState());
    }

    /** I158: termination just after a safe completion was queued never resumes the bootstrap. */
    @Test
    void terminationJustAfterASafeCompletionNeverResumes() {
        ManualExecutor carriers = new ManualExecutor();
        ProtosActorScheduler scheduler = new ProtosActorScheduler(carriers, 1);
        ProtosActor initializing = actor();
        List<String> order = new ArrayList<>();
        ProtosActorScheduler.ExclusiveBootstrap bootstrap = recordingBootstrap(order);
        scheduler.attach(initializing);
        assertTrue(scheduler.holdExclusiveBootstrap(initializing, bootstrap));
        ProtosActorExecutionDomain domain = initializing.executionDomain();
        ProtosFutureValue future =
                new ProtosFutureValue(new ProtosObjectValue(ProtosObjectValue.rootObject()), domain);
        domain.enqueueTargetedFutureCompletionForRuntime(
                future,
                () -> {
                    order.add("completion");
                    return true;
                });

        initializing.requestTerminationForRuntime();
        carriers.runAll();
        scheduler.wakeForRuntime(initializing);
        carriers.runAll();

        assertFalse(order.contains("resume"), "an invalidated bootstrap never resumes");
        assertTrue(java.util.Collections.frequency(order, "completion") <= 1);
        assertFalse(domain.hasTargetedRuntimeCompletionForRuntime());
        assertEquals(ProtosActor.LifecycleState.TERMINATED, initializing.lifecycleState());
    }

    /** I158: an executing bootstrap segment keeps the Actor out of TERMINATED until it exits. */
    @Test
    void terminationDuringAnActiveBootstrapSegmentWaitsForItsRelease() {
        ProtosActor initializing = actor();
        ProtosActorExecutionDomain domain = initializing.executionDomain();

        domain.beginBootstrapSegmentForRuntime();
        initializing.requestTerminationForRuntime();
        assertEquals(ProtosActor.LifecycleState.TERMINATING, initializing.lifecycleState());
        assertFalse(initializing.completeInitialization(
                new ProtosObjectValue(ProtosObjectValue.rootObject())), "no READY after termination");

        domain.finishBootstrapSegmentForRuntime();
        assertEquals(ProtosActor.LifecycleState.TERMINATED, initializing.lifecycleState());
        assertEquals(0, domain.activeBootstrapSegmentCountForRuntime());
        assertThrows(IllegalStateException.class, domain::finishBootstrapSegmentForRuntime,
                "a double release is an accounting error, never a second completion");
    }

    private static ProtosActorScheduler.ExclusiveBootstrap recordingBootstrap(List<String> order) {
        return new ProtosActorScheduler.ExclusiveBootstrap() {
            @Override
            public boolean resumeReady() {
                return false;
            }

            @Override
            public void resume() {
                order.add("resume");
            }
        };
    }
}
