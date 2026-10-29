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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.io.ProtosResolverBackend;
import com.guillermomolina.protos.runtime.io.ProtosResolverBackend.AddressDescriptor;
import com.guillermomolina.protos.runtime.io.ProtosResolverBackend.FailureKind;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** D223-A1 bounded host name-service adapter, exercised with a controlled name service only. */
final class ProtosResolverBackendTest {
    @Test
    void executorIsLazyAndWorkersCopyNumericDescriptorsOffTheCallerThread() throws Exception {
        List<String> names = new ArrayList<>();
        ProtosHostResolverBackend backend =
                new ProtosHostResolverBackend(
                        name -> {
                            names.add(name);
                            return new InetAddress[] {
                                InetAddress.getByAddress(new byte[] {(byte) 192, 0, 2, 7}),
                                InetAddress.getByAddress(ipv6Loopback())
                            };
                        });
        assertFalse(backend.executorInitializedForTesting());

        Outcome outcome = new Outcome();
        backend.lookup("example.com.", outcome);
        assertTrue(backend.executorInitializedForTesting());
        outcome.await();
        assertEquals(List.of("example.com."), names);
        assertEquals(2, outcome.addresses.size());
        assertArrayEquals(new byte[] {(byte) 192, 0, 2, 7}, outcome.addresses.get(0).octets());
        assertEquals(6, outcome.addresses.get(1).version());
        assertNotSame(outcome.addresses.get(0).octets(), outcome.addresses.get(0).octets());
        assertTrue(outcome.thread.getName().startsWith("protos-resolver-"));
        assertTrue(outcome.thread.isDaemon());
        assertNotSame(Thread.currentThread(), outcome.thread);
        backend.close();
    }

    @Test
    void hostConditionsMapOntoThePortableKindsOnly() throws Exception {
        assertEquals(FailureKind.NO_ADDRESS, failureOf(name -> {
            throw new UnknownHostException("nxdomain detail");
        }));
        assertEquals(FailureKind.NO_ADDRESS, failureOf(name -> new InetAddress[0]));
        assertEquals(FailureKind.DENIED, failureOf(name -> {
            throw new SecurityException("policy detail");
        }));
        assertEquals(FailureKind.UNAVAILABLE, failureOf(name -> {
            throw new IllegalStateException("backend detail");
        }));
        assertEquals(FailureKind.LIMIT, failureOf(name -> {
            InetAddress[] many = new InetAddress[ProtosResolverBackend.MAX_RAW_ADDRESSES + 1];
            for (int i = 0; i < many.length; i++) {
                many[i] = InetAddress.getByAddress(new byte[] {10, 0, (byte) (i / 256), (byte) i});
            }
            return many;
        }));
    }

    @Test
    void workerAndQueueBoundsRejectExcessRequestsAndCloseRetiresQueuedWork() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger started = new AtomicInteger();
        ProtosHostResolverBackend backend =
                new ProtosHostResolverBackend(
                        name -> {
                            started.incrementAndGet();
                            try {
                                release.await(5, TimeUnit.SECONDS);
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                            }
                            return new InetAddress[] {
                                InetAddress.getByAddress(new byte[] {127, 0, 0, 1})
                            };
                        });
        int capacity = ProtosHostResolverBackend.MAX_WORKERS + ProtosHostResolverBackend.MAX_QUEUED;
        List<Outcome> admitted = new ArrayList<>();
        List<ProtosResolverBackend.Cancellation> cancellations = new ArrayList<>();
        for (int i = 0; i < capacity; i++) {
            Outcome outcome = new Outcome();
            admitted.add(outcome);
            cancellations.add(backend.lookup("n" + i + ".", outcome));
        }
        Outcome rejected = new Outcome();
        backend.lookup("overflow.", rejected);
        assertEquals(FailureKind.LIMIT, rejected.failure);
        assertTrue(rejected.done.getCount() == 0);
        assertTrue(started.get() <= ProtosHostResolverBackend.MAX_WORKERS);

        // A cancelled queued request is never delivered.
        cancellations.get(capacity - 1).cancel();
        backend.close();
        backend.close();
        release.countDown();
        for (int i = 0; i < ProtosHostResolverBackend.MAX_WORKERS; i++) {
            admitted.get(i).done.await(5, TimeUnit.SECONDS);
        }
        assertEquals(1, admitted.get(capacity - 1).done.getCount());
        assertTrue(started.get() <= ProtosHostResolverBackend.MAX_WORKERS);

        Outcome afterClose = new Outcome();
        backend.lookup("late.", afterClose);
        assertEquals(FailureKind.UNAVAILABLE, afterClose.failure);
    }

    @Test
    void cancellationOfARunningLookupSuppressesItsLateResult() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ProtosHostResolverBackend backend =
                new ProtosHostResolverBackend(
                        name -> {
                            entered.countDown();
                            try {
                                release.await(5, TimeUnit.SECONDS);
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                            }
                            return new InetAddress[] {
                                InetAddress.getByAddress(new byte[] {127, 0, 0, 1})
                            };
                        });
        Outcome outcome = new Outcome();
        ProtosResolverBackend.Cancellation cancellation = backend.lookup("example.com.", outcome);
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        cancellation.cancel();
        release.countDown();
        assertFalse(outcome.done.await(200, TimeUnit.MILLISECONDS));
        backend.close();
    }

    private static FailureKind failureOf(ProtosHostResolverBackend.NameService service)
            throws Exception {
        ProtosHostResolverBackend backend = new ProtosHostResolverBackend(service);
        Outcome outcome = new Outcome();
        backend.lookup("example.com.", outcome);
        outcome.await();
        backend.close();
        return outcome.failure;
    }

    private static byte[] ipv6Loopback() {
        byte[] octets = new byte[16];
        octets[15] = 1;
        return octets;
    }

    private static final class Outcome implements ProtosResolverBackend.Completion {
        private final CountDownLatch done = new CountDownLatch(1);
        private volatile List<AddressDescriptor> addresses;
        private volatile FailureKind failure;
        private volatile Thread thread;

        @Override
        public void succeeded(List<AddressDescriptor> delivered) {
            addresses = delivered;
            thread = Thread.currentThread();
            done.countDown();
        }

        @Override
        public void failed(FailureKind kind) {
            failure = kind;
            thread = Thread.currentThread();
            done.countDown();
        }

        void await() throws InterruptedException {
            assertTrue(done.await(5, TimeUnit.SECONDS));
        }
    }
}
