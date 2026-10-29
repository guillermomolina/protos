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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActor;
import com.guillermomolina.protos.runtime.ProtosActorExecutionDomain;
import com.guillermomolina.protos.runtime.ProtosActorValueTransfer;
import com.guillermomolina.protos.runtime.ProtosBytesValue;
import com.guillermomolina.protos.runtime.ProtosEnvironmentValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.io.ProtosHostIpEndpoint;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosIoLifecycle;
import com.guillermomolina.protos.runtime.ProtosNetworkCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosTcpConnectionFlow;
import com.guillermomolina.protos.runtime.io.ProtosTcpAcceptorValue;
import com.guillermomolina.protos.runtime.io.ProtosTcpConnectionValue;
import com.guillermomolina.protos.runtime.io.ProtosTcpListenerFlow;
import com.guillermomolina.protos.runtime.io.ProtosTcpListenerValue;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ProtosNioNetworkProvisioningTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Duration WAIT = Duration.ofSeconds(10);

    @Test
    void runtimeHostNetworkProvisioningIsExplicitAndLazy() throws Exception {
        try (ProtosPolyglotRuntimeHost host = ProtosPolyglotRuntimeHost.open()) {
            assertFalse(host.networkHostInitializedForTesting());
            ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
            assertFalse(host.networkHostInitializedForTesting());

            ProtosNetworkCapabilityValue network = host.provisionHostNetwork(prelude);
            assertTrue(host.networkHostInitializedForTesting());
            assertSame(prelude.networkPrototype(), network.representedDelegationParent(prelude));

            // D223-A1: provisioning Network creates no Resolver authority or resolver backend.
            assertEquals(0, host.provisionedResolverCountForTesting());

            ProtosStandaloneProcessBootstrap.Result bootstrap = standalone(prelude, network);
            try {
                assertSame(
                        network,
                        bootstrap.activation().context().readLocalSlot("network").orElseThrow());
                assertFalse(bootstrap.activation().context().hasLocalSlot("resolver"));
            } finally {
                bootstrap.process().requestTerminationForRuntime();
            }
        }
    }

    @Test
    void provisionedHostNetworkComposesListenConnectAcceptAndDuplex() throws Exception {
        try (ProtosPolyglotRuntimeHost host = ProtosPolyglotRuntimeHost.open()) {
            ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
            ProtosNetworkCapabilityValue network = host.provisionHostNetwork(prelude);
            ProtosStandaloneProcessBootstrap.Result bootstrap = standalone(prelude, network);
            ProtosActivation activation = bootstrap.activation();

            try {
                ProtosFutureValue listening =
                        future(
                                network,
                                "listenTcp",
                                List.of(
                                        listenRequest(
                                                integer(4),
                                                ProtosNullValue.INSTANCE,
                                                ProtosNullValue.INSTANCE)),
                                activation);
                awaitTerminal(listening);
                assertEquals(ProtosFutureValue.State.RESOLVED, listening.state());
                ProtosTcpListenerValue listener =
                        assertInstanceOf(
                                ProtosTcpListenerValue.class,
                                listening.resolvedValue().orElseThrow());
                int port = listener.localPortForRuntime();

                ProtosFutureValue accepting = future(listener, "accept", List.of(), activation);
                ProtosFutureValue connecting =
                        future(
                                network,
                                "connectTcp",
                                List.of(ipv4LoopbackEndpoint(prelude, activation, port)),
                                activation);
                awaitTerminal(connecting);
                awaitTerminal(accepting);
                assertEquals(ProtosFutureValue.State.RESOLVED, connecting.state());
                assertEquals(ProtosFutureValue.State.RESOLVED, accepting.state());

                ProtosTcpConnectionValue client =
                        assertInstanceOf(
                                ProtosTcpConnectionValue.class,
                                connecting.resolvedValue().orElseThrow());
                ProtosTcpConnectionValue server =
                        assertInstanceOf(
                                ProtosTcpConnectionValue.class,
                                accepting.resolvedValue().orElseThrow());

                byte[] outbound = {11, 22, 33, 44, 55, 66};
                ProtosFutureValue read =
                        future(server, "read", List.of(integer(outbound.length)), activation);
                ProtosFutureValue write =
                        future(client, "write", List.of(bytes(prelude, outbound)), activation);
                awaitTerminal(write);
                awaitTerminal(read);
                assertEquals(ProtosFutureValue.State.RESOLVED, write.state());
                assertEquals(List.of(11, 22, 33, 44, 55, 66), resolvedBytes(read));

                closeAndAwait(client, activation);
                closeAndAwait(server, activation);
                closeAndAwait(listener, activation);
            } finally {
                bootstrap.process().requestTerminationForRuntime();
            }
        }
    }

    @Test
    void runtimeHostCloseReleasesUnclosedNetworkResources() throws Exception {
        ProtosPolyglotRuntimeHost host = ProtosPolyglotRuntimeHost.open();
        try {
            ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
            ProtosActivation activation = prelude.newModuleActivation();
            ProtosNetworkCapabilityValue network = host.provisionHostNetwork(prelude);
            ProtosFutureValue listening =
                    future(
                            network,
                            "listenTcp",
                            List.of(
                                    listenRequest(
                                            integer(4),
                                            ProtosNullValue.INSTANCE,
                                            ProtosNullValue.INSTANCE)),
                            activation);
            awaitTerminal(listening);
            ProtosTcpListenerValue listener =
                    assertInstanceOf(
                            ProtosTcpListenerValue.class,
                            listening.resolvedValue().orElseThrow());
            ProtosNioTcpListenerBackend backend =
                    assertInstanceOf(
                            ProtosNioTcpListenerBackend.class,
                            listener.resourceStateForRuntime());

            host.close();

            AtomicInteger failures = new AtomicInteger();
            backend.accept(
                    new ProtosTcpListenerFlow.AcceptCompletion() {
                        @Override
                        public void succeeded(
                                Object resourceState,
                                ProtosHostIpEndpoint localEndpoint,
                                ProtosHostIpEndpoint remoteEndpoint,
                                ProtosTcpConnectionFlow.Backend connectionBackend,
                                Runnable releaseIfUntransferred) {
                            releaseIfUntransferred.run();
                            fail("closed RuntimeHost accepted another connection");
                        }

                        @Override
                        public void failed() {
                            failures.incrementAndGet();
                        }
                    });
            assertEquals(1, failures.get());
            assertThrows(
                    IllegalStateException.class,
                    () -> host.provisionHostNetwork(prelude));
        } finally {
            host.close();
        }
    }

    @Test
    void delegatedTcpAcceptorUsesRealNioAndWorkerOwnsAcceptedConnection() throws Exception {
        try (ProtosPolyglotRuntimeHost host = ProtosPolyglotRuntimeHost.open()) {
            ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
            ProtosNetworkCapabilityValue network = host.provisionHostNetwork(prelude);
            ProtosStandaloneProcessBootstrap.Result bootstrap = standalone(prelude, network);
            ProtosActivation owner = bootstrap.activation();

            try {
                ProtosTcpListenerValue listener = listen(network, owner);
                int port = listener.localPortForRuntime();
                ProtosActor worker =
                        bootstrap.process().createHostedActorForRuntime(prelude.actorRefPrototypeForRuntime());
                ProtosActivation workerActivation = actorActivation(prelude, worker);

                ProtosTcpAcceptorValue source = acceptCapability(listener, owner);
                ProtosTcpAcceptorValue delegated = delegate(source, owner, workerActivation);
                assertNotSame(source, delegated);
                assertSame(prelude.tcpAcceptorPrototypeForRuntime(), delegated.parent().orElseThrow());
                assertTrue(delegated.ownedByForRuntime(workerActivation));
                assertFalse(delegated.ownedByForRuntime(owner));

                ProtosFutureValue workerAccept = future(delegated, "accept", List.of(), workerActivation);
                ProtosFutureValue ownerAccept = future(listener, "accept", List.of(), owner);
                assertSame(worker.executionDomain(), workerAccept.domain());
                assertSame(owner.executionDomain(), ownerAccept.domain());

                byte[] firstPayload = {1, 2, 3, 4};
                byte[] secondPayload = {5, 6, 7, 8};
                try (Socket first = loopbackPeer(port); Socket second = loopbackPeer(port)) {
                    send(first, firstPayload);
                    send(second, secondPayload);
                    awaitAll(
                            List.of(workerAccept, ownerAccept),
                            worker.executionDomain(),
                            owner.executionDomain());
                    assertEquals(ProtosFutureValue.State.RESOLVED, workerAccept.state());
                    assertEquals(ProtosFutureValue.State.RESOLVED, ownerAccept.state());
                    ProtosTcpConnectionValue workerConnection =
                            assertInstanceOf(ProtosTcpConnectionValue.class, workerAccept.resolvedValue().orElseThrow());
                    ProtosTcpConnectionValue ownerConnection =
                            assertInstanceOf(ProtosTcpConnectionValue.class, ownerAccept.resolvedValue().orElseThrow());
                    assertNotSame(workerConnection, ownerConnection);

                    // One listener, one port: both connections were accepted on it.
                    assertEquals(port, listener.localPortForRuntime());
                    assertEquals(port, endpointPort(workerConnection.localEndpointForRuntime()));
                    assertEquals(port, endpointPort(ownerConnection.localEndpointForRuntime()));

                    // Each connection is owned by the Actor that accepted it.
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> workerConnection.readForRuntime(owner, integer(1)));
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> ownerConnection.readForRuntime(workerActivation, integer(1)));

                    // Kernel acceptance order is not assumed: the remote port names the peer.
                    int workerRemote = endpointPort(workerConnection.remoteEndpointForRuntime());
                    int ownerRemote = endpointPort(ownerConnection.remoteEndpointForRuntime());
                    Socket workerPeer = workerRemote == first.getLocalPort() ? first : second;
                    Socket ownerPeer = workerPeer == first ? second : first;
                    assertEquals(workerPeer.getLocalPort(), workerRemote);
                    assertEquals(ownerPeer.getLocalPort(), ownerRemote);
                    byte[] expected = workerPeer == first ? firstPayload : secondPayload;

                    assertArrayEquals(expected, readFully(workerConnection, expected.length, workerActivation));

                    byte[] reply = {9, 10, 11};
                    ProtosFutureValue write =
                            future(workerConnection, "write", List.of(bytes(prelude, reply)), workerActivation);
                    assertSame(worker.executionDomain(), write.domain());
                    awaitTerminal(write);
                    assertEquals(ProtosFutureValue.State.RESOLVED, write.state());
                    workerPeer.setSoTimeout((int) WAIT.toMillis());
                    assertArrayEquals(reply, workerPeer.getInputStream().readNBytes(reply.length));

                    closeAndAwait(workerConnection, workerActivation);
                    closeAndAwait(ownerConnection, owner);
                }
                closeAndAwait(listener, owner);
                assertEquals(ProtosIoLifecycle.State.CLOSED_SUCCESS, listener.lifecycleForRuntime().state());
            } finally {
                bootstrap.process().requestTerminationForRuntime();
            }
        }
    }

    @Test
    void delegatedPendingNioAcceptIsCutOverByOwnerListenerClose() throws Exception {
        try (ProtosPolyglotRuntimeHost host = ProtosPolyglotRuntimeHost.open()) {
            ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
            ProtosNetworkCapabilityValue network = host.provisionHostNetwork(prelude);
            ProtosStandaloneProcessBootstrap.Result bootstrap = standalone(prelude, network);
            ProtosActivation owner = bootstrap.activation();

            try {
                ProtosTcpListenerValue listener = listen(network, owner);
                ProtosActor worker =
                        bootstrap.process().createHostedActorForRuntime(prelude.actorRefPrototypeForRuntime());
                ProtosActivation workerActivation = actorActivation(prelude, worker);
                ProtosTcpAcceptorValue delegated =
                        delegate(acceptCapability(listener, owner), owner, workerActivation);

                ProtosFutureValue pending = future(delegated, "accept", List.of(), workerActivation);
                assertSame(worker.executionDomain(), pending.domain());
                ProtosFutureValue close = future(listener, "close", List.of(), owner);
                assertSame(owner.executionDomain(), close.domain());

                // The worker settles its own cutover; only then can the owner publish close.
                awaitAll(List.of(pending, close), worker.executionDomain(), owner.executionDomain());
                assertLifecycleError(prelude, pending);
                assertSame(worker.executionDomain(), pending.domain());
                assertEquals(ProtosFutureValue.State.RESOLVED, close.state());
                assertSame(listener, close.resolvedValue().orElseThrow());
                assertEquals(ProtosIoLifecycle.State.CLOSED_SUCCESS, listener.lifecycleForRuntime().state());

                ProtosFutureValue again = future(listener, "close", List.of(), owner);
                assertNotSame(close, again);
                assertEquals(ProtosFutureValue.State.RESOLVED, again.state());
                assertSame(listener, again.resolvedValue().orElseThrow());
                assertEquals(ProtosIoLifecycle.State.CLOSED_SUCCESS, listener.lifecycleForRuntime().state());

                ProtosFutureValue later = future(delegated, "accept", List.of(), workerActivation);
                assertNotSame(pending, later);
                assertSame(worker.executionDomain(), later.domain());
                assertLifecycleError(prelude, later);
                assertNotSame(pending.failedError().orElseThrow(), later.failedError().orElseThrow());

                // Nothing accepted and nothing left queued for the worker.
                assertTrue(pending.resolvedValue().isEmpty());
                assertFalse(worker.executionDomain().dispatchOne());
            } finally {
                bootstrap.process().requestTerminationForRuntime();
            }
        }
    }

    private static ProtosTcpListenerValue listen(
            ProtosNetworkCapabilityValue network, ProtosActivation activation) throws Exception {
        ProtosFutureValue listening =
                future(
                        network,
                        "listenTcp",
                        List.of(listenRequest(integer(4), ProtosNullValue.INSTANCE, ProtosNullValue.INSTANCE)),
                        activation);
        awaitTerminal(listening);
        assertEquals(ProtosFutureValue.State.RESOLVED, listening.state());
        return assertInstanceOf(ProtosTcpListenerValue.class, listening.resolvedValue().orElseThrow());
    }

    private static ProtosActivation actorActivation(ProtosPrelude prelude, ProtosActor actor) {
        return prelude.newModuleActivation(
                actor.moduleState(), null, prelude.newExecutionContext(), actor.executionDomain());
    }

    private static ProtosTcpAcceptorValue acceptCapability(
            ProtosTcpListenerValue listener, ProtosActivation owner) {
        return assertInstanceOf(
                ProtosTcpAcceptorValue.class,
                ProtosInvocation.invokeMessage(listener, "acceptCapability", List.of(), owner));
    }

    /** The I125-B Actor.spawn initialization transfer: spawn snapshot, destination materialization. */
    private static ProtosTcpAcceptorValue delegate(
            ProtosTcpAcceptorValue acceptor, ProtosActivation source, ProtosActivation destination) {
        List<Object> snapshot = ProtosActorValueTransfer.snapshotSpawnArguments(List.of(acceptor), source);
        assertTrue(ProtosActorValueTransfer.requiresMaterialization(snapshot));
        return assertInstanceOf(
                ProtosTcpAcceptorValue.class,
                ProtosActorValueTransfer.materializeArguments(snapshot, destination).get(0));
    }

    private static Socket loopbackPeer(int port) throws Exception {
        Socket peer = new Socket(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), port);
        peer.setSoTimeout((int) WAIT.toMillis());
        return peer;
    }

    private static void send(Socket peer, byte[] payload) throws Exception {
        peer.getOutputStream().write(payload);
        peer.getOutputStream().flush();
    }

    private static int endpointPort(ProtosObjectValue endpoint) {
        return ProtosNumericValueSupport.exactInt(endpoint.readLocalSlot("port").orElseThrow());
    }

    /** Reads exactly {@code length} bytes; TCP may legitimately deliver them in several reads. */
    private static byte[] readFully(
            ProtosTcpConnectionValue connection, int length, ProtosActivation activation) throws Exception {
        byte[] result = new byte[length];
        int filled = 0;
        while (filled < length) {
            ProtosFutureValue read = future(connection, "read", List.of(integer(length - filled)), activation);
            assertSame(activation.executionDomain(), read.domain());
            awaitTerminal(read);
            assertEquals(ProtosFutureValue.State.RESOLVED, read.state());
            List<Integer> chunk = resolvedBytes(read);
            assertFalse(chunk.isEmpty(), "peer closed before sending its payload");
            for (int value : chunk) result[filled++] = (byte) value;
        }
        return result;
    }

    private static void assertLifecycleError(ProtosPrelude prelude, ProtosFutureValue future) {
        assertEquals(ProtosFutureValue.State.FAILED, future.state());
        assertSame(
                prelude.bindings().readLocalSlot("IOLifecycleError").orElseThrow(),
                future.failedError().orElseThrow().parent().orElseThrow());
    }

    /** Bounded drive of several Actor domains, with the same backoff as {@link #awaitTerminal}. */
    private static void awaitAll(List<ProtosFutureValue> futures, ProtosActorExecutionDomain... domains)
            throws Exception {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (futures.stream().anyMatch(f -> f.state() == ProtosFutureValue.State.PENDING)
                && System.nanoTime() < deadline) {
            boolean progressed = false;
            for (ProtosActorExecutionDomain domain : domains) {
                progressed |= domain.dispatchOne();
            }
            if (!progressed) {
                Thread.sleep(5);
            }
        }
        if (futures.stream().anyMatch(f -> f.state() == ProtosFutureValue.State.PENDING)) {
            throw new AssertionError("timed out waiting for Futures");
        }
    }

    private static ProtosStandaloneProcessBootstrap.Result standalone(
            ProtosPrelude prelude, ProtosNetworkCapabilityValue network) {
        return ProtosStandaloneProcessBootstrap.create(
                prelude,
                List.of(),
                exactDomain(),
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                network);
    }

    private static ProtosEnvironmentValue.NativeNameDomain exactDomain() {
        return new ProtosEnvironmentValue.NativeNameDomain() {
            @Override
            public boolean sameCapturedName(String left, String right) {
                return left.equals(right);
            }

            @Override
            public boolean isQueryRepresentable(String name) {
                return !name.contains("=") && name.indexOf('\0') < 0;
            }

            @Override
            public boolean matchesQuery(String captured, String query) {
                return captured.equals(query);
            }
        };
    }

    private static ProtosObjectValue listenRequest(Object version, Object address, Object port) {
        ProtosObjectValue request = new ProtosObjectValue(ProtosObjectValue.rootObject());
        request.createLocalSlot("ipVersion", version);
        request.createLocalSlot("address", address);
        request.createLocalSlot("port", port);
        return request;
    }

    private static ProtosObjectValue ipv4LoopbackEndpoint(
            ProtosPrelude prelude, ProtosActivation activation, int port) {
        Object addressFactory = prelude.ipAddressPrototypeForRuntime();
        ProtosObjectValue address =
                (ProtosObjectValue)
                        ProtosInvocation.invoke(
                                addressFactory,
                                List.of(
                                        integer(4),
                                        new ProtosIntegerValue(0x7f000001L)),
                                activation);
        Object endpointFactory = prelude.ipEndpointPrototypeForRuntime();
        return (ProtosObjectValue)
                ProtosInvocation.invoke(
                        endpointFactory, List.of(address, integer(port)), activation);
    }

    private static ProtosBytesValue bytes(ProtosPrelude prelude, byte[] values) {
        ProtosBytesValue bytes = new ProtosBytesValue(prelude.bytesPrototypeForRuntime());
        for (byte value : values) {
            bytes.indexedAdd(integer(value & 0xff));
        }
        return bytes;
    }

    private static ProtosIntegerValue integer(long value) {
        return new ProtosIntegerValue(value);
    }

    private static ProtosFutureValue future(
            Object receiver, String selector, List<?> arguments, ProtosActivation activation) {
        return assertInstanceOf(
                ProtosFutureValue.class,
                ProtosInvocation.invokeMessage(receiver, selector, arguments, activation));
    }

    private static List<Integer> resolvedBytes(ProtosFutureValue future) {
        ProtosBytesValue bytes =
                assertInstanceOf(
                        ProtosBytesValue.class,
                        future.resolvedValue().orElseThrow());
        ArrayList<Integer> result = new ArrayList<>();
        for (Object value : bytes.indexedSnapshot()) {
            result.add(
                    Integer.parseInt(com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(value)));
        }
        return result;
    }

    private static void closeAndAwait(Object receiver, ProtosActivation activation)
            throws Exception {
        ProtosFutureValue close = future(receiver, "close", List.of(), activation);
        awaitTerminal(close);
        assertEquals(ProtosFutureValue.State.RESOLVED, close.state());
        assertSame(receiver, close.resolvedValue().orElseThrow());
    }

    private static void awaitTerminal(ProtosFutureValue future) throws Exception {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (future.state() == ProtosFutureValue.State.PENDING
                && System.nanoTime() < deadline) {
            if (!future.domain().dispatchOne()) {
                Thread.sleep(5);
            }
        }
        if (future.state() == ProtosFutureValue.State.PENDING) {
            throw new AssertionError("timed out waiting for Future");
        }
    }
}
