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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class ProtosActorMailboxSchedulerTest {
    @Test
    void mailboxHasFiniteCapacityAndPreservesAcceptedFifo() {
        ManualExecutor carriers = new ManualExecutor();
        ProtosActorScheduler scheduler = new ProtosActorScheduler(carriers, 1);
        ProtosActor actor = actorWithMailboxCapacity(2);
        List<String> order = new ArrayList<>();

        scheduler.attach(actor);
        assertTrue(actor.markReady());
        assertTrue(actor.tryAcceptMessageForRuntime(turn(order, "first")));
        assertTrue(actor.tryAcceptMessageForRuntime(turn(order, "second")));
        assertFalse(actor.tryAcceptMessageForRuntime(turn(order, "overflow")));
        assertEquals(2, actor.mailboxForRuntime().size());
        assertEquals(2, actor.mailboxForRuntime().capacity());

        carriers.runNext();

        assertEquals(List.of("first", "second"), order);
        assertEquals(0, actor.mailboxForRuntime().size());
    }

    @Test
    void initializingActorRetainsAcceptedMessagesUntilReady() {
        ManualExecutor carriers = new ManualExecutor();
        ProtosActorScheduler scheduler = new ProtosActorScheduler(carriers, 1);
        ProtosActor actor = actorWithMailboxCapacity(2);
        List<String> order = new ArrayList<>();

        scheduler.attach(actor);
        assertTrue(actor.tryAcceptMessageForRuntime(turn(order, "message")));
        assertEquals(0, carriers.size());
        assertTrue(order.isEmpty());

        assertTrue(actor.markReady());
        assertEquals(1, carriers.size());
        carriers.runNext();

        assertEquals(List.of("message"), order);
    }

    @Test
    void implicitEventLoopDispatchesWithoutManualDomainPumping() {
        ManualExecutor carriers = new ManualExecutor();
        ProtosActorScheduler scheduler = new ProtosActorScheduler(carriers, 1);
        ProtosActor actor = actorWithMailboxCapacity(4);
        AtomicInteger handled = new AtomicInteger();

        scheduler.attach(actor);
        actor.markReady();
        assertTrue(actor.tryAcceptMessageForRuntime(
                task -> {
                    handled.incrementAndGet();
                    task.complete(null);
                }));

        assertEquals(0, handled.get());
        carriers.runNext();
        assertEquals(1, handled.get());
        assertEquals(0, actor.executionDomain().liveTaskCount());
    }

    @Test
    void runnableTasksAndAcceptedMessagesReceiveAlternatingTurns() {
        ManualExecutor carriers = new ManualExecutor();
        ProtosActorScheduler scheduler = new ProtosActorScheduler(carriers, 1);
        ProtosActor actor = actorWithMailboxCapacity(4);
        List<String> order = new ArrayList<>();

        scheduler.attach(actor);
        actor.markReady();
        actor.executionDomain().createTask(null, turn(order, "task-1"));
        actor.executionDomain().createTask(null, turn(order, "task-2"));
        actor.tryAcceptMessageForRuntime(turn(order, "message-1"));
        actor.tryAcceptMessageForRuntime(turn(order, "message-2"));

        carriers.runNext();

        assertEquals(List.of("task-1", "message-1", "task-2", "message-2"), order);
    }

    @Test
    void readyActorsAreRequeuedAtTailForWeakFairScheduling() {
        ManualExecutor carriers = new ManualExecutor();
        ProtosActorScheduler scheduler = new ProtosActorScheduler(carriers, 1);
        ProtosActor first = actorWithMailboxCapacity(4);
        ProtosActor second = actorWithMailboxCapacity(4);
        List<String> order = new ArrayList<>();

        scheduler.attach(first);
        scheduler.attach(second);
        first.markReady();
        second.markReady();
        first.tryAcceptMessageForRuntime(turn(order, "a1"));
        first.tryAcceptMessageForRuntime(turn(order, "a2"));
        first.tryAcceptMessageForRuntime(turn(order, "a3"));
        second.tryAcceptMessageForRuntime(turn(order, "b1"));
        second.tryAcceptMessageForRuntime(turn(order, "b2"));
        second.tryAcceptMessageForRuntime(turn(order, "b3"));

        carriers.runNext();

        assertEquals(List.of("a1", "b1", "a2", "b2", "a3", "b3"), order);
    }

    @Test
    void oneActorNeverRunsTwoSegmentsConcurrently() throws Exception {
        try (CarrierPool carriers = new CarrierPool(4)) {
            ProtosActorScheduler scheduler = new ProtosActorScheduler(carriers.executor(), 4);
            ProtosActor actor = actorWithMailboxCapacity(4);
            CountDownLatch firstEntered = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            CountDownLatch secondEntered = new CountDownLatch(1);
            CountDownLatch bothCompleted = new CountDownLatch(2);
            AtomicInteger active = new AtomicInteger();
            AtomicInteger maxActive = new AtomicInteger();

            scheduler.attach(actor);
            actor.markReady();
            actor.tryAcceptMessageForRuntime(
                    task -> {
                        try {
                            int now = active.incrementAndGet();
                            maxActive.accumulateAndGet(now, Math::max);
                            firstEntered.countDown();
                            await(releaseFirst);
                            active.decrementAndGet();
                            task.complete(null);
                        } finally {
                            bothCompleted.countDown();
                        }
                    });
            actor.tryAcceptMessageForRuntime(
                    task -> {
                        try {
                            int now = active.incrementAndGet();
                            maxActive.accumulateAndGet(now, Math::max);
                            secondEntered.countDown();
                            active.decrementAndGet();
                            task.complete(null);
                        } finally {
                            bothCompleted.countDown();
                        }
                    });

            assertTrue(firstEntered.await(2, TimeUnit.SECONDS));
            assertFalse(secondEntered.await(100, TimeUnit.MILLISECONDS));
            releaseFirst.countDown();
            assertTrue(secondEntered.await(2, TimeUnit.SECONDS));
            assertTrue(
                    bothCompleted.await(2, TimeUnit.SECONDS),
                    "both Actor turns must finish before carrier teardown");
            assertEquals(1, maxActive.get());
        }
    }

    @Test
    void distinctActorsCanMakeProgressOnDifferentCarriers() throws Exception {
        try (CarrierPool carriers = new CarrierPool(2)) {
            ProtosActorScheduler scheduler = new ProtosActorScheduler(carriers.executor(), 2);
            ProtosActor first = actorWithMailboxCapacity(2);
            ProtosActor second = actorWithMailboxCapacity(2);
            CountDownLatch bothEntered = new CountDownLatch(2);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch bothCompleted = new CountDownLatch(2);

            scheduler.attach(first);
            scheduler.attach(second);
            first.markReady();
            second.markReady();
            first.tryAcceptMessageForRuntime(
                    blockingTurn(bothEntered, release, bothCompleted));
            second.tryAcceptMessageForRuntime(
                    blockingTurn(bothEntered, release, bothCompleted));

            assertTrue(bothEntered.await(2, TimeUnit.SECONDS));
            release.countDown();
            assertTrue(
                    bothCompleted.await(2, TimeUnit.SECONDS),
                    "both Actor turns must finish before carrier teardown");
        }
    }

    @Test
    void terminatingActorRejectsNewMailboxOwnership() {
        ProtosActor actor = actorWithMailboxCapacity(2);
        assertTrue(actor.markReady());
        assertTrue(actor.beginTermination());

        assertFalse(actor.tryAcceptMessageForRuntime(task -> task.complete(null)));
    }

    private static ProtosTask.Continuation turn(List<String> order, String value) {
        return task -> {
            order.add(value);
            task.complete(null);
        };
    }

    private static ProtosTask.Continuation blockingTurn(
            CountDownLatch entered,
            CountDownLatch release,
            CountDownLatch completed) {
        return task -> {
            try {
                entered.countDown();
                await(release);
                task.complete(null);
            } finally {
                completed.countDown();
            }
        };
    }

    private static ProtosActor actorWithMailboxCapacity(int capacity) {
        return new ProtosActor(
                new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze(),
                new ProtosActorExecutionDomain(),
                new ProtosActorModuleState(),
                capacity);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }

    private static final class CarrierPool implements AutoCloseable {
        private final AtomicReference<Throwable> uncaughtFailure =
                new AtomicReference<>();
        private final ExecutorService executor;

        CarrierPool(int threadCount) {
            AtomicInteger sequence = new AtomicInteger();
            ThreadFactory threadFactory =
                    runnable -> {
                        Thread thread =
                                new Thread(
                                        runnable,
                                        "protos-test-carrier-"
                                                + sequence.incrementAndGet());
                        thread.setUncaughtExceptionHandler(
                                (ignored, failure) ->
                                        uncaughtFailure.compareAndSet(
                                                null, failure));
                        return thread;
                    };
            executor = Executors.newFixedThreadPool(threadCount, threadFactory);
        }

        ExecutorService executor() {
            return executor;
        }

        @Override
        public void close() throws Exception {
            executor.shutdown();
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                assertTrue(
                        executor.awaitTermination(2, TimeUnit.SECONDS),
                        "carrier pool failed to terminate");
            }

            Throwable failure = uncaughtFailure.get();
            if (failure != null) {
                AssertionError assertion =
                        new AssertionError(
                                "uncaught carrier-thread failure escaped the test body");
                assertion.initCause(failure);
                throw assertion;
            }
        }
    }

    private static final class ManualExecutor implements Executor {
        private final ArrayDeque<Runnable> queued = new ArrayDeque<>();

        @Override
        public synchronized void execute(Runnable command) {
            queued.addLast(command);
        }

        synchronized int size() {
            return queued.size();
        }

        void runNext() {
            Runnable command;
            synchronized (this) {
                command = queued.pollFirst();
            }
            assertNotNull(command, "expected one scheduled carrier worker");
            command.run();
        }
    }
}
