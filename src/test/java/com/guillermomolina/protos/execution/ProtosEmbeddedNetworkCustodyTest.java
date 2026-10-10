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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosNetworkListenFlow;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosTcpListenerFlow;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * PLAT054-3E4: the Context-local custody behind the embedded default Network. Races are ordered by
 * barriers and checked by their terminal invariants, never by sleeps.
 */
final class ProtosEmbeddedNetworkCustodyTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final long SAFETY_SECONDS = 5;
    private static final ProtosNetworkListenFlow.ListenRequest ANY_IPV4_EPHEMERAL =
            new ProtosNetworkListenFlow.ListenRequest(4, null, null);

    private static ProtosPrelude prelude;

    @BeforeAll
    static void bootstrapCore() throws IOException {
        prelude = new ProtosCoreBootstrap().bootstrap(CORE);
    }

    /** Records every host it opens so the test can check that each one was retired. */
    private static final class RecordingFactory implements ProtosEmbeddedNetworkCustody.HostFactory {
        final List<ProtosNioNetworkHost> opened = new CopyOnWriteArrayList<>();
        final AtomicInteger failuresLeft;

        RecordingFactory(int failures) {
            failuresLeft = new AtomicInteger(failures);
        }

        @Override
        public ProtosNioNetworkHost open() throws IOException {
            if (failuresLeft.getAndDecrement() > 0) {
                throw new IOException("injected NIO host initialization failure");
            }
            ProtosNioNetworkHost host = new ProtosNioNetworkHost();
            opened.add(host);
            return host;
        }

        void assertAllRetired() {
            for (ProtosNioNetworkHost host : opened) {
                ProtosNioHostIoPoller poller = host.pollerForTesting();
                assertTrue(poller.isTerminatedForTesting(), "poller retired");
                assertFalse(poller.threadForTesting().isAlive(), "poller thread ended");
            }
        }
    }

    /** One listen completion; a success releases the untransferred listener immediately. */
    private static final class Outcome implements ProtosNetworkListenFlow.ListenCompletion {
        final CompletableFuture<Boolean> succeeded = new CompletableFuture<>();

        @Override
        public void succeeded(
                Object resourceState,
                int localPort,
                ProtosTcpListenerFlow.Backend listenerBackend,
                Runnable releaseIfUntransferred) {
            releaseIfUntransferred.run();
            succeeded.complete(true);
        }

        @Override
        public void failed() {
            succeeded.complete(false);
        }

        boolean await() throws Exception {
            return succeeded.get(SAFETY_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    void anUnusedCustodyCreatesNoHostAndClosesWithoutActivatingOne() {
        RecordingFactory factory = new RecordingFactory(0);
        ProtosEmbeddedNetworkCustody custody = new ProtosEmbeddedNetworkCustody(prelude, factory);
        assertNull(custody.pollerForTesting(), "no poller before first acquisition");
        custody.close();
        custody.close();
        assertTrue(custody.isClosedForTesting());
        assertTrue(factory.opened.isEmpty(), "closing an unused custody activates nothing");
    }

    @Test
    void theFirstAcquisitionMaterializesOneSharedPoller() throws Exception {
        RecordingFactory factory = new RecordingFactory(0);
        ProtosEmbeddedNetworkCustody custody = new ProtosEmbeddedNetworkCustody(prelude, factory);
        try {
            Outcome first = new Outcome();
            custody.listen(ANY_IPV4_EPHEMERAL, first);
            assertTrue(first.await());
            ProtosNioHostIoPoller poller = custody.pollerForTesting();
            assertNotNull(poller);

            Outcome second = new Outcome();
            custody.listen(ANY_IPV4_EPHEMERAL, second);
            assertTrue(second.await());
            assertSame(poller, custody.pollerForTesting(), "no poller per operation");
            assertEquals(1, factory.opened.size());
        } finally {
            custody.close();
        }
        factory.assertAllRetired();
        assertNull(custody.pollerForTesting());
    }

    @Test
    void aFailedInitializationFailsTheAcquisitionAndRetainsNothing() throws Exception {
        RecordingFactory factory = new RecordingFactory(1);
        ProtosEmbeddedNetworkCustody custody = new ProtosEmbeddedNetworkCustody(prelude, factory);
        try {
            Outcome failed = new Outcome();
            custody.listen(ANY_IPV4_EPHEMERAL, failed);
            assertTrue(failed.succeeded.isDone(), "the failure is reported synchronously");
            assertFalse(failed.await());
            assertNull(custody.pollerForTesting());

            Outcome retried = new Outcome();
            custody.listen(ANY_IPV4_EPHEMERAL, retried);
            assertTrue(retried.await(), "a later acquisition may initialize the backend");
        } finally {
            custody.close();
        }
        factory.assertAllRetired();
    }

    @Test
    void acquisitionsAfterCloseFailWithoutStartingAPoller() throws Exception {
        RecordingFactory factory = new RecordingFactory(0);
        ProtosEmbeddedNetworkCustody custody = new ProtosEmbeddedNetworkCustody(prelude, factory);
        custody.close();
        Outcome refused = new Outcome();
        custody.listen(ANY_IPV4_EPHEMERAL, refused);
        assertFalse(refused.await());
        assertTrue(factory.opened.isEmpty(), "no poller starts after the admission cutoff");
    }

    @Test
    void firstUseRacingCloseNeverLeavesALivePoller() throws Exception {
        for (int round = 0; round < 25; round++) {
            RecordingFactory factory = new RecordingFactory(0);
            ProtosEmbeddedNetworkCustody custody =
                    new ProtosEmbeddedNetworkCustody(prelude, factory);
            CyclicBarrier start = new CyclicBarrier(2);
            Outcome outcome = new Outcome();
            CompletableFuture<Void> acquiring =
                    CompletableFuture.runAsync(
                            () -> {
                                awaitBarrier(start);
                                custody.listen(ANY_IPV4_EPHEMERAL, outcome);
                            });
            CompletableFuture<Void> closing =
                    CompletableFuture.runAsync(
                            () -> {
                                awaitBarrier(start);
                                custody.close();
                            });
            acquiring.get(SAFETY_SECONDS, TimeUnit.SECONDS);
            closing.get(SAFETY_SECONDS, TimeUnit.SECONDS);
            // Either order is valid; both must terminalize the acquisition and retire the host.
            outcome.await();
            assertTrue(factory.opened.size() <= 1);
            factory.assertAllRetired();
        }
    }

    private static void awaitBarrier(CyclicBarrier barrier) {
        try {
            barrier.await(SAFETY_SECONDS, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }
}
