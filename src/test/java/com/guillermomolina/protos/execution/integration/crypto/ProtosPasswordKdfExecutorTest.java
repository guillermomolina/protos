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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * I160 deterministic host KDF admission and cancellation contracts.
 *
 * <p>The package-private entrance barrier holds real executor
 * workers before provider invocation. No Argon2 computation is needed
 * to test scheduling, bounded admission or cancellation.
 */
final class ProtosPasswordKdfExecutorTest {

    private static final byte[] PASSWORD = {112, 97, 115, 115};
    private static final byte[] SALT = new byte[16];

    private static ProtosPasswordKdfExecutor.Ticket submit(
            ProtosPasswordKdfExecutor executor,
            AtomicInteger published) {

        return executor.submit(
                PASSWORD,
                SALT,
                null,
                result -> {
                    try {
                        published.incrementAndGet();
                    } finally {
                        if (result != null) {
                            result.close();
                        }
                    }
                });
    }

    @Test
    @Timeout(30)
    void boundedQueueAndQueuedCancellationReleaseCapacity()
            throws Exception {

        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger published = new AtomicInteger();

        ProtosPasswordKdfExecutor executor =
                new ProtosPasswordKdfExecutor(() -> {
                    entered.countDown();
                    try {
                        if (!release.await(20, TimeUnit.SECONDS)) {
                            throw new IllegalStateException(
                                    "KDF entrance gate timed out");
                        }
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(interrupted);
                    }
                });

        List<ProtosPasswordKdfExecutor.Ticket> tickets =
                new ArrayList<>();

        try {
            tickets.add(submit(executor, published));
            tickets.add(submit(executor, published));

            assertTrue(entered.await(10, TimeUnit.SECONDS));
            assertNotNull(tickets.get(0));
            assertNotNull(tickets.get(1));

            // Exactly sixteen more jobs fit in the bounded queue.
            for (int i = 0; i < 16; i++) {
                ProtosPasswordKdfExecutor.Ticket ticket =
                        submit(executor, published);

                assertNotNull(ticket);
                tickets.add(ticket);
            }

            assertNull(submit(executor, published));

            // Removing one queued job immediately releases a queue slot.
            tickets.get(2).cancel();

            ProtosPasswordKdfExecutor.Ticket replacement =
                    submit(executor, published);

            assertNotNull(replacement);
            tickets.add(replacement);

            assertNull(submit(executor, published));

            for (ProtosPasswordKdfExecutor.Ticket ticket : tickets) {
                ticket.cancel();
            }
        } finally {
            executor.close();
            release.countDown();
        }

        assertTrue(
                executor.awaitTerminationForTesting(
                        15, TimeUnit.SECONDS));

        assertEquals(0, published.get());
    }

    @Test
    @Timeout(30)
    void hostRetirementDoesNotInterruptActiveWorkers()
            throws Exception {

        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);

        AtomicBoolean interrupted = new AtomicBoolean();
        AtomicInteger published = new AtomicInteger();

        ProtosPasswordKdfExecutor executor =
                new ProtosPasswordKdfExecutor(() -> {
                    entered.countDown();

                    try {
                        release.await();
                    } catch (InterruptedException interruption) {
                        interrupted.set(true);
                        Thread.currentThread().interrupt();
                    }
                });

        try {
            assertNotNull(submit(executor, published));
            assertNotNull(submit(executor, published));

            assertTrue(entered.await(10, TimeUnit.SECONDS));

            assertNotNull(submit(executor, published));

            // Retire admission and cancel all outstanding jobs.
            // Running workers must not receive Thread.interrupt().
            executor.close();

            assertTrue(executor.isClosedForRuntime());
            assertNull(submit(executor, published));
        } finally {
            release.countDown();
            executor.close();
        }

        assertTrue(
                executor.awaitTerminationForTesting(
                        15, TimeUnit.SECONDS));

        assertFalse(interrupted.get());
        assertEquals(0, published.get());
    }

    @Test
    @Timeout(30)
    void runningCancellationSuppressesLatePublication()
            throws Exception {

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger published = new AtomicInteger();

        ProtosPasswordKdfExecutor executor =
                new ProtosPasswordKdfExecutor(() -> {
                    entered.countDown();

                    try {
                        release.await();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                });

        try {
            ProtosPasswordKdfExecutor.Ticket ticket =
                    submit(executor, published);

            assertNotNull(ticket);
            assertTrue(entered.await(10, TimeUnit.SECONDS));

            ticket.cancel();
            ticket.cancel();

            // Closing is idempotent and does not revoke
            // another host's unrelated executor.
            executor.close();
            executor.close();
        } finally {
            release.countDown();
            executor.close();
        }

        assertTrue(
                executor.awaitTerminationForTesting(
                        15, TimeUnit.SECONDS));

        assertEquals(0, published.get());
    }

    /**
     * The private provider seam fails before any Argon2 allocation.
     * Each failure must complete exactly once with an unavailable result.
     */
    @Test
    @Timeout(30)
    void backendFailuresCompleteAsUnavailableWithoutDeriving()
            throws Exception {

        assertBackendFailureBecomesUnavailable(
                new OutOfMemoryError("injected memory failure"));

        assertBackendFailureBecomesUnavailable(
                new LinkageError("injected provider linkage failure"));

        assertBackendFailureBecomesUnavailable(
                new IllegalStateException("injected provider failure"));
    }

    private static void assertBackendFailureBecomesUnavailable(
            Throwable injected) throws Exception {

        CountDownLatch delivered = new CountDownLatch(1);
        AtomicInteger publications = new AtomicInteger();
        AtomicReference<Boolean> unavailable = new AtomicReference<>();

        ProtosPasswordKdfExecutor executor =
                new ProtosPasswordKdfExecutor(() -> {
                    if (injected instanceof Error error) {
                        throw error;
                    }
                    if (injected instanceof RuntimeException failure) {
                        throw failure;
                    }
                    throw new AssertionError(
                            "Unsupported test failure", injected);
                });

        try {
            ProtosPasswordKdfExecutor.Ticket ticket =
                    executor.submit(
                            PASSWORD,
                            SALT,
                            null,
                            result -> {
                                try {
                                    publications.incrementAndGet();
                                    unavailable.set(
                                            result != null
                                                    && result.isUnavailable());
                                } finally {
                                    if (result != null) {
                                        result.close();
                                    }
                                    delivered.countDown();
                                }
                            });

            assertNotNull(ticket);

            assertTrue(
                    delivered.await(15, TimeUnit.SECONDS),
                    "Provider failure must deliver a terminal host result");

            assertEquals(1, publications.get());
            assertEquals(Boolean.TRUE, unavailable.get());
        } finally {
            executor.close();
        }

        assertTrue(
                executor.awaitTerminationForTesting(
                        15, TimeUnit.SECONDS));
    }

    /**
     * A completed admission ticket does not authorize publication
     * after the executor's terminal host-retirement boundary.
     */
    @Test
    @Timeout(30)
    void closeAfterSuccessfulAdmissionSuppressesPublication()
            throws Exception {

        CountDownLatch workerEntered = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        AtomicInteger publications = new AtomicInteger();
        AtomicBoolean interrupted = new AtomicBoolean();

        ProtosPasswordKdfExecutor executor =
                new ProtosPasswordKdfExecutor(() -> {
                    workerEntered.countDown();
                    try {
                        releaseWorker.await();
                    } catch (InterruptedException failure) {
                        interrupted.set(true);
                        Thread.currentThread().interrupt();
                    }
                });

        try {
            ProtosPasswordKdfExecutor.Ticket admitted =
                    submit(executor, publications);

            assertNotNull(admitted);
            assertTrue(workerEntered.await(10, TimeUnit.SECONDS));

            // The host closes only after successful admission.
            // The worker remains in a controlled pre-derivation extent.
            executor.close();

            assertTrue(executor.isClosedForRuntime());

            // Repeated cancellation and close remain harmless.
            admitted.cancel();
            executor.close();
        } finally {
            releaseWorker.countDown();
            executor.close();
        }

        assertTrue(
                executor.awaitTerminationForTesting(
                        15, TimeUnit.SECONDS));

        assertFalse(interrupted.get());
        assertEquals(0, publications.get());
    }
}
