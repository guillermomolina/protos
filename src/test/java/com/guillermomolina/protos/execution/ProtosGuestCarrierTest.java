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

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Focused transport coverage for the dedicated guest carrier (PERF025-E2). */
final class ProtosGuestCarrierTest {
    @Test
    void runsOperationOnCarrierAndReturnsResult() throws Exception {
        try (ProtosGuestCarrier carrier = new ProtosGuestCarrier("protos-test-carrier")) {
            AtomicReference<Thread> runner = new AtomicReference<>();
            String value =
                    carrier.call(
                            () -> {
                                assertTrue(carrier.isCarrierThread());
                                runner.set(Thread.currentThread());
                                return "value";
                            });
            assertEquals("value", value);
            assertEquals("protos-test-carrier", runner.get().getName());
            assertFalse(carrier.isCarrierThread());
            assertSame(runner.get(), carrier.call(Thread::currentThread));
        }
    }

    @Test
    void propagatesFailuresUnchangedOrWrapsOtherCheckedThrowables() {
        try (ProtosGuestCarrier carrier = new ProtosGuestCarrier("protos-test-carrier")) {
            IOException io = new IOException("io");
            assertSame(io, assertThrows(IOException.class, () -> carrier.call(() -> { throw io; })));
            IllegalArgumentException runtime = new IllegalArgumentException("runtime");
            assertSame(
                    runtime,
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> carrier.call(() -> { throw runtime; })));
            AssertionError error = new AssertionError("error");
            assertSame(
                    error,
                    assertThrows(AssertionError.class, () -> carrier.call(() -> { throw error; })));
            Exception checked = new Exception("checked");
            IllegalStateException wrapped =
                    assertThrows(
                            IllegalStateException.class,
                            () -> carrier.call(() -> { throw checked; }));
            assertSame(checked, wrapped.getCause());
        }
    }

    @Test
    void interruptBeforeCallDoesNotAbandonOperationAndIsRestored() throws Exception {
        try (ProtosGuestCarrier carrier = new ProtosGuestCarrier("protos-test-carrier")) {
            Thread.currentThread().interrupt();
            try {
                assertEquals(7, carrier.call(() -> 7));
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
        }
    }

    @Test
    void interruptDuringWaitDoesNotAbandonOperationAndIsRestored() throws Exception {
        try (ProtosGuestCarrier carrier = new ProtosGuestCarrier("protos-test-carrier")) {
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicReference<Object> outcome = new AtomicReference<>();
            AtomicBoolean interruptedAfter = new AtomicBoolean();
            Thread caller =
                    new Thread(
                            () -> {
                                try {
                                    outcome.set(
                                            carrier.call(
                                                    () -> {
                                                        started.countDown();
                                                        release.await();
                                                        return "completed";
                                                    }));
                                } catch (Throwable thrown) {
                                    outcome.set(thrown);
                                }
                                interruptedAfter.set(Thread.currentThread().isInterrupted());
                            });
            caller.start();
            started.await();
            awaitBlocked(caller);
            caller.interrupt();
            release.countDown();
            caller.join();
            assertEquals("completed", outcome.get());
            assertTrue(interruptedAfter.get());
        }
    }

    @Test
    void concurrentCallersAreSerializedOnSingleCarrier() throws Exception {
        int callers = 8;
        int callsPerCaller = 50;
        try (ProtosGuestCarrier carrier = new ProtosGuestCarrier("protos-test-carrier")) {
            AtomicInteger active = new AtomicInteger();
            AtomicInteger overlaps = new AtomicInteger();
            AtomicInteger completed = new AtomicInteger();
            List<Throwable> failures = new CopyOnWriteArrayList<>();
            CyclicBarrier barrier = new CyclicBarrier(callers);
            Thread[] threads = new Thread[callers];
            for (int i = 0; i < callers; i++) {
                threads[i] =
                        new Thread(
                                () -> {
                                    try {
                                        barrier.await();
                                        for (int n = 0; n < callsPerCaller; n++) {
                                            carrier.call(
                                                    () -> {
                                                        if (!carrier.isCarrierThread()
                                                                || active.incrementAndGet() != 1) {
                                                            overlaps.incrementAndGet();
                                                        }
                                                        Thread.yield();
                                                        active.decrementAndGet();
                                                        return completed.incrementAndGet();
                                                    });
                                        }
                                    } catch (Throwable thrown) {
                                        failures.add(thrown);
                                    }
                                });
                threads[i].start();
            }
            for (Thread thread : threads) thread.join();
            assertTrue(failures.isEmpty(), failures::toString);
            assertEquals(0, overlaps.get());
            assertEquals(callers * callsPerCaller, completed.get());
        }
    }

    @Test
    void closeCompletesSubmittedOperationsBeforeStopAndRejectsLaterCalls() throws Exception {
        ProtosGuestCarrier carrier = new ProtosGuestCarrier("protos-test-carrier");
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<String> order = new CopyOnWriteArrayList<>();
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        Thread first =
                new Thread(
                        () -> {
                            try {
                                carrier.call(
                                        () -> {
                                            started.countDown();
                                            release.await();
                                            return order.add("first");
                                        });
                            } catch (Throwable thrown) {
                                failures.add(thrown);
                            }
                        });
        first.start();
        started.await();
        Thread second =
                new Thread(
                        () -> {
                            try {
                                carrier.call(() -> order.add("second"));
                            } catch (Throwable thrown) {
                                failures.add(thrown);
                            }
                        });
        second.start();
        // A blocked submitter has already queued its operation.
        awaitBlocked(second);
        Thread closer = new Thread(carrier::close);
        closer.start();
        // A blocked closer has already queued STOP behind the submitted operations.
        awaitBlocked(closer);
        release.countDown();
        first.join();
        second.join();
        closer.join();
        assertTrue(failures.isEmpty(), failures::toString);
        assertEquals(List.of("first", "second"), order);
        IllegalStateException rejected =
                assertThrows(IllegalStateException.class, () -> carrier.call(() -> "late"));
        assertEquals("guest carrier is closed", rejected.getMessage());
        carrier.close();
    }

    private static void awaitBlocked(Thread thread) {
        while (thread.isAlive()
                && thread.getState() != Thread.State.WAITING
                && thread.getState() != Thread.State.TIMED_WAITING) {
            Thread.onSpinWait();
        }
        assertTrue(thread.isAlive());
    }
}
