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

import static org.junit.jupiter.api.Assertions.*;

import com.guillermomolina.protos.runtime.io.ProtosEntropyBackend;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** I157: bounded, lazy host-only CSPRNG backend. */
final class ProtosHostEntropyBackendTest {
    @Test
    void theRealBackendIsLazyAndGeneratesOnAWorker() throws Exception {
        ProtosHostEntropyBackend backend = new ProtosHostEntropyBackend();

        assertFalse(backend.executorInitializedForTesting());
        assertFalse(backend.secureRandomInitializedForTesting());

        Outcome first = new Outcome();
        backend.generate(16, first);
        first.await();

        assertFalse(first.failed);
        assertNotNull(first.bytes);
        assertEquals(16, first.bytes.length);
        assertNotSame(Thread.currentThread(), first.deliveryThread);
        assertTrue(first.deliveryThread.isDaemon());

        Outcome second = new Outcome();
        backend.generate(32, second);
        second.await();
        assertFalse(second.failed);
        assertEquals(32, second.bytes.length);
        assertNotSame(first.bytes, second.bytes);

        backend.close();
        backend.close();
    }

    @Test
    void controlledSourcePreservesExactByteValues() throws Exception {
        ProtosHostEntropyBackend backend =
                new ProtosHostEntropyBackend(count -> {
                    byte[] bytes = new byte[count];
                    for (int index = 0; index < count; index++) {
                        bytes[index] = (byte) (index + 128);
                    }
                    return bytes;
                });
        Outcome result = new Outcome();

        backend.generate(256, result);
        result.await();

        assertFalse(result.failed);
        assertEquals(256, result.bytes.length);
        assertEquals((byte) 128, result.bytes[0]);
        assertEquals((byte) 255, result.bytes[127]);
        assertEquals((byte) 0, result.bytes[128]);
        assertEquals((byte) 127, result.bytes[255]);
        backend.close();
    }

    @Test
    void sourceFailureNeverPublishesPartialBytes() throws Exception {
        ProtosHostEntropyBackend backend =
                new ProtosHostEntropyBackend(count -> {
                    throw new IllegalStateException("provider internal detail");
                });
        Outcome failure = new Outcome();

        backend.generate(32, failure);
        failure.await();

        assertTrue(failure.failed);
        assertNull(failure.bytes);
        assertEquals(1, failure.deliveries.get());
        backend.close();
    }

    @Test
    void boundedQueueRejectsOverflowAndClosingSuppressesQueuedCallbacks()
            throws Exception {
        CountDownLatch entered = new CountDownLatch(4);
        CountDownLatch release = new CountDownLatch(1);
        ProtosHostEntropyBackend backend =
                new ProtosHostEntropyBackend(count -> {
                    entered.countDown();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException interruption) {
                        Thread.currentThread().interrupt();
                    }
                    return new byte[count];
                });

        List<Outcome> admitted = new ArrayList<>();
        List<ProtosEntropyBackend.Cancellation> tokens = new ArrayList<>();

        for (int index = 0; index < 4; index++) {
            Outcome result = new Outcome();
            admitted.add(result);
            tokens.add(backend.generate(16, result));
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS));

        for (int index = 0; index < 64; index++) {
            Outcome result = new Outcome();
            admitted.add(result);
            tokens.add(backend.generate(16, result));
        }

        Outcome overflow = new Outcome();
        backend.generate(16, overflow);
        overflow.await();
        assertTrue(overflow.failed);
        assertNull(overflow.bytes);

        // The last accepted request is still queued, not executing.
        Outcome cancelled = admitted.get(admitted.size() - 1);
        tokens.get(tokens.size() - 1).cancel();

        backend.close();
        backend.close();
        release.countDown();

        assertEquals(1L, cancelled.done.getCount());

        Outcome afterClose = new Outcome();
        backend.generate(16, afterClose);
        afterClose.await();
        assertTrue(afterClose.failed);
        assertNull(afterClose.bytes);
    }

    @Test
    void cancellingActiveWorkSuppressesLateCompletion() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ProtosHostEntropyBackend backend =
                new ProtosHostEntropyBackend(count -> {
                    entered.countDown();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException interruption) {
                        Thread.currentThread().interrupt();
                    }
                    return new byte[count];
                });

        Outcome outcome = new Outcome();
        ProtosEntropyBackend.Cancellation token =
                backend.generate(32, outcome);

        assertTrue(entered.await(5, TimeUnit.SECONDS));
        token.cancel();
        release.countDown();
        assertFalse(outcome.done.await(250, TimeUnit.MILLISECONDS));
        assertEquals(0, outcome.deliveries.get());
        backend.close();
    }

    @Test
    void oneBlockedBackendDoesNotDelayAnotherIndependentBackend()
            throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        ProtosHostEntropyBackend slow =
                new ProtosHostEntropyBackend(count -> {
                    entered.countDown();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException interruption) {
                        Thread.currentThread().interrupt();
                    }
                    return new byte[count];
                });
        ProtosHostEntropyBackend independent =
                new ProtosHostEntropyBackend(count -> new byte[count]);

        try {
            Outcome blocked = new Outcome();
            slow.generate(16, blocked);
            assertTrue(entered.await(5, TimeUnit.SECONDS));

            Outcome finished = new Outcome();
            independent.generate(32, finished);
            finished.await();
            assertFalse(finished.failed);
            assertEquals(32, finished.bytes.length);

            slow.close();
            assertFalse(blocked.done.await(250, TimeUnit.MILLISECONDS));
        } finally {
            release.countDown();
            slow.close();
            independent.close();
        }
    }

    private static final class Outcome
            implements ProtosEntropyBackend.Completion {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicInteger deliveries = new AtomicInteger();
        volatile byte[] bytes;
        volatile boolean failed;
        volatile Thread deliveryThread;

        @Override
        public void succeeded(byte[] result) {
            bytes = result;
            deliveryThread = Thread.currentThread();
            deliveries.incrementAndGet();
            done.countDown();
        }

        @Override
        public void failed() {
            failed = true;
            deliveryThread = Thread.currentThread();
            deliveries.incrementAndGet();
            done.countDown();
        }

        void await() throws InterruptedException {
            assertTrue(done.await(5, TimeUnit.SECONDS),
                    "host entropy request must complete");
        }
    }
}
