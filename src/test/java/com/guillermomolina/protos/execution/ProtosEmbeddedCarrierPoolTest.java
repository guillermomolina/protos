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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * PLAT054-3D: deterministic races of the embedded carrier pool, on plain threads (the production
 * factory and blocking region are Truffle's; {@link ProtosEmbeddingLifecycleTest} covers those).
 */
final class ProtosEmbeddedCarrierPoolTest {
    private static final long BOUND_SECONDS = 5;

    /** Plain-thread factory that records uncaught failures and can be switched to refuse. */
    private static final class Threads implements ProtosEmbeddedCarrierPool.CarrierFactory {
        final ConcurrentLinkedQueue<Throwable> uncaught = new ConcurrentLinkedQueue<>();
        final AtomicInteger created = new AtomicInteger();
        final AtomicBoolean refuse = new AtomicBoolean();

        @Override
        public Thread newCarrier(Runnable body, int number) {
            if (refuse.get()) {
                throw new IllegalStateException("thread creation refused");
            }
            created.incrementAndGet();
            Thread carrier = new Thread(body, "test-carrier-" + number);
            carrier.setDaemon(true);
            carrier.setUncaughtExceptionHandler((thread, failure) -> uncaught.add(failure));
            return carrier;
        }
    }

    private static ProtosEmbeddedCarrierPool pool(int parallelism, Threads threads) {
        return new ProtosEmbeddedCarrierPool(
                parallelism, threads, ProtosEmbeddedCarrierPool.InterruptibleTake::take, () -> false);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(BOUND_SECONDS, TimeUnit.SECONDS), "bounded wait expired");
        } catch (InterruptedException interruption) {
            throw new AssertionError(interruption);
        }
    }

    private static void awaitState(Thread thread, Thread.State state) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(BOUND_SECONDS);
        while (thread.getState() != state) {
            assertTrue(System.nanoTime() < deadline, thread + " never reached " + state);
            Thread.onSpinWait();
        }
    }

    private static void assertAllTerminated(ProtosEmbeddedCarrierPool pool) {
        for (Thread carrier : pool.carriersForTesting()) {
            assertEquals(Thread.State.TERMINATED, carrier.getState(), carrier.getName());
        }
    }

    @Test
    void closeWaitsForAStartInProgressAndJoinsThatCarrier() throws Exception {
        Threads threads = new Threads();
        ProtosEmbeddedCarrierPool pool = pool(1, threads);
        CountDownLatch inStart = new CountDownLatch(1);
        CountDownLatch releaseStart = new CountDownLatch(1);
        pool.beforeStartForTesting =
                () -> {
                    inStart.countDown();
                    await(releaseStart);
                };
        AtomicInteger ran = new AtomicInteger();
        Thread spawner = new Thread(() -> pool.execute(ran::incrementAndGet));
        spawner.start();
        await(inStart);

        Thread closer = new Thread(pool::closeAndJoin);
        closer.start();
        // The close cannot take its snapshot while the start critical section is held.
        awaitState(closer, Thread.State.BLOCKED);
        releaseStart.countDown();
        closer.join(TimeUnit.SECONDS.toMillis(BOUND_SECONDS));
        assertFalse(closer.isAlive());

        assertEquals(1, pool.carriersForTesting().size());
        assertAllTerminated(pool);
        int afterClose = ran.get();
        assertThrows(RejectedExecutionException.class, () -> pool.execute(ran::incrementAndGet));
        assertEquals(afterClose, ran.get(), "nothing runs after the close completed");
        assertEquals(1, threads.created.get(), "no carrier is created after close");
    }

    @Test
    void aRefusedCarrierRejectsOnlyItsOwnCommand() {
        Threads threads = new Threads();
        ProtosEmbeddedCarrierPool pool = pool(2, threads);
        CountDownLatch busy = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch queuedRan = new CountDownLatch(1);
        CountDownLatch failNow = new CountDownLatch(1);
        pool.execute(
                () -> {
                    busy.countDown();
                    await(release);
                });
        await(busy);
        // The second carrier fails with an Error while a third command is accepted and queued.
        pool.execute(
                () -> {
                    await(failNow);
                    threads.refuse.set(true);
                    throw new AssertionError("carrier failure");
                });
        pool.execute(queuedRan::countDown);
        assertEquals(1, pool.queuedCommandCountForTesting());
        failNow.countDown();
        awaitState(pool.carriersForTesting().get(1), Thread.State.TERMINATED);

        // The replacement was refused: the accepted command stays queued; a new command whose
        // carrier cannot start is rejected and never enters the queue.
        assertEquals(1, pool.queuedCommandCountForTesting());
        assertThrows(RejectedExecutionException.class, () -> pool.execute(() -> {}));
        assertEquals(1, pool.queuedCommandCountForTesting());

        release.countDown();
        await(queuedRan);
        assertTrue(threads.uncaught.peek() instanceof AssertionError, "a real uncaught failure");
        pool.closeAndJoin();
        assertAllTerminated(pool);
    }

    @Test
    void aFailedCarrierIsReplacedForAcceptedCommands() {
        Threads threads = new Threads();
        ProtosEmbeddedCarrierPool pool = pool(1, threads);
        CountDownLatch failNow = new CountDownLatch(1);
        CountDownLatch queuedRan = new CountDownLatch(1);
        pool.execute(
                () -> {
                    await(failNow);
                    throw new AssertionError("carrier failure");
                });
        pool.execute(queuedRan::countDown);
        failNow.countDown();
        await(queuedRan);
        assertEquals(2, threads.created.get(), "the queued command ran on a replacement");
        // The handler runs on the failed carrier after it started the replacement.
        awaitState(pool.carriersForTesting().get(0), Thread.State.TERMINATED);
        assertEquals(1, threads.uncaught.size());
        pool.closeAndJoin();
        assertAllTerminated(pool);
    }

    @Test
    void anInterruptedIdleCarrierKeepsServingAnOpenPool() {
        Threads threads = new Threads();
        ProtosEmbeddedCarrierPool pool = pool(1, threads);
        AtomicReference<Thread> first = new AtomicReference<>();
        CountDownLatch ran = new CountDownLatch(1);
        pool.execute(() -> first.set(Thread.currentThread()));
        Thread carrier = pool.carriersForTesting().get(0);
        awaitState(carrier, Thread.State.WAITING);

        carrier.interrupt();
        AtomicReference<Thread> second = new AtomicReference<>();
        pool.execute(
                () -> {
                    second.set(Thread.currentThread());
                    ran.countDown();
                });
        await(ran);
        assertSame(first.get(), second.get(), "the same carrier continued after the interrupt");
        assertEquals(1, threads.created.get());
        pool.closeAndJoin();
        assertAllTerminated(pool);
    }

    @Test
    void aCarrierCannotCloseItsOwnPool() {
        Threads threads = new Threads();
        ProtosEmbeddedCarrierPool pool = pool(1, threads);
        AtomicReference<Throwable> observed = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        pool.execute(
                () -> {
                    try {
                        pool.closeAndJoin();
                    } catch (IllegalStateException expected) {
                        observed.set(expected);
                    }
                    done.countDown();
                });
        await(done);
        assertTrue(observed.get() instanceof IllegalStateException);
        pool.closeAndJoin();
        assertAllTerminated(pool);
        assertEquals(List.of(), List.copyOf(threads.uncaught));
    }
}
