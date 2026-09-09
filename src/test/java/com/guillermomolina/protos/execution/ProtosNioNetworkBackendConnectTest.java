/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
 * DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
 * DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
 * OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS.
 * "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY OF THE
 * LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING THE
 * CONTENTS OF THIS FILE. IF A COPY OF THE LICENSE DOES NOT ACCOMPANY THIS FILE, A
 * COPY OF THE LICENSE MAY ALSO BE OBTAINED AT THE FOLLOWING WEB SITE:
 * https://github.com/guillermomolina/protos
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosNetworkCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosTcpConnectionValue;
import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

final class ProtosNioNetworkBackendConnectTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Duration WAIT = Duration.ofSeconds(5);

    @Test
    void ipv4ConnectUsesC4ContractAndMaterializesLogicalEndpoints() throws Exception {
        try (ProtosNioHostIoPoller poller =
                        new ProtosNioHostIoPoller("protos-test-nio-connect-v4");
                ServerSocketChannel server =
                        ServerSocketChannel.open(StandardProtocolFamily.INET)) {
            Inet4Address loopback =
                    (Inet4Address) InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
            server.bind(new InetSocketAddress(loopback, 0));
            server.configureBlocking(false);

            Fixture x =
                    fixture(
                            poller,
                            bytes -> {
                                throw new AssertionError("IPv6 resolver used by IPv4");
                            });
            int port = ((InetSocketAddress) server.getLocalAddress()).getPort();
            ProtosObjectValue remote =
                    endpoint(x.prelude, x.activation, 4, BigInteger.valueOf(0x7f000001L), port);

            ProtosFutureValue result = connect(x, remote);
            awaitTerminal(result);
            assertEquals(ProtosFutureValue.State.RESOLVED, result.state());

            SocketChannel accepted = awaitAccept(server);
            try {
                ProtosTcpConnectionValue connection =
                        assertInstanceOf(
                                ProtosTcpConnectionValue.class,
                                result.resolvedValue().orElseThrow());
                assertInstanceOf(
                        ProtosNioTcpConnectionBackend.class,
                        connection.resourceStateForRuntime());

                Object observedRemote =
                        ProtosInvocation.invokeMessage(
                                connection, "remoteEndpoint", List.of(), x.activation);
                assertSame(
                        ProtosBooleanValue.TRUE,
                        ProtosInvocation.invokeMessage(
                                observedRemote, "==", List.of(remote), x.activation));

                InetSocketAddress client = (InetSocketAddress) accepted.getRemoteAddress();
                ProtosObjectValue expectedLocal =
                        endpoint(
                                x.prelude,
                                x.activation,
                                4,
                                new BigInteger(1, client.getAddress().getAddress()),
                                client.getPort());
                Object observedLocal =
                        ProtosInvocation.invokeMessage(
                                connection, "localEndpoint", List.of(), x.activation);
                assertSame(
                        ProtosBooleanValue.TRUE,
                        ProtosInvocation.invokeMessage(
                                observedLocal, "==", List.of(expectedLocal), x.activation));

                ProtosFutureValue close =
                        (ProtosFutureValue)
                                ProtosInvocation.invokeMessage(
                                        connection, "close", List.of(), x.activation);
                awaitTerminal(close);
                assertEquals(ProtosFutureValue.State.RESOLVED, close.state());
            } finally {
                accepted.close();
            }
        }
    }

    @Test
    void cancellationBeforeQueuedConnectPreventsAcquisition() throws Exception {
        try (ProtosNioHostIoPoller poller =
                        new ProtosNioHostIoPoller("protos-test-nio-connect-cancel");
                ServerSocketChannel server =
                        ServerSocketChannel.open(StandardProtocolFamily.INET)) {
            Inet4Address loopback =
                    (Inet4Address) InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
            server.bind(new InetSocketAddress(loopback, 0));
            server.configureBlocking(false);

            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicReference<RuntimeException> blockerFailure = new AtomicReference<>();
            poller.submit(
                    () -> {
                        entered.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException interruption) {
                            Thread.currentThread().interrupt();
                        }
                    },
                    blockerFailure::set);
            assertTrue(entered.await(WAIT.toMillis(), TimeUnit.MILLISECONDS));

            Fixture x =
                    fixture(
                            poller,
                            bytes -> {
                                throw new AssertionError("IPv6 resolver used by IPv4");
                            });
            int port = ((InetSocketAddress) server.getLocalAddress()).getPort();
            ProtosFutureValue result =
                    connect(
                            x,
                            endpoint(
                                    x.prelude,
                                    x.activation,
                                    4,
                                    BigInteger.valueOf(0x7f000001L),
                                    port));

            assertTrue(result.cancelRequest());
            assertEquals(ProtosFutureValue.State.CANCELLED, result.state());
            CountDownLatch drained = new CountDownLatch(1);
            release.countDown();
            poller.submit(drained::countDown, blockerFailure::set);
            assertTrue(drained.await(WAIT.toMillis(), TimeUnit.MILLISECONDS));

            assertNull(blockerFailure.get());
            assertNull(server.accept());
        }
    }

    @Test
    void linkLocalIpv6RequiresExplicitNetworkScope() throws Exception {
        try (ProtosNioHostIoPoller poller =
                new ProtosNioHostIoPoller("protos-test-nio-connect-v6-scope")) {
            Fixture x =
                    fixture(
                            poller,
                            bytes -> Inet6Address.getByAddress(null, bytes, -1));
            BigInteger linkLocal =
                    new BigInteger("fe800000000000000000000000000001", 16);

            ProtosFutureValue result =
                    connect(x, endpoint(x.prelude, x.activation, 6, linkLocal, 443));

            assertFailedAs(result, x.prelude, "IOError");
        }
    }

    @Test
    void ipv6ScopeResolverCannotRewriteAddressBits() throws Exception {
        try (ProtosNioHostIoPoller poller =
                new ProtosNioHostIoPoller("protos-test-nio-connect-v6-bits")) {
            byte[] loopback = new byte[16];
            loopback[15] = 1;
            Fixture x =
                    fixture(
                            poller,
                            bytes -> Inet6Address.getByAddress(null, loopback, -1));
            BigInteger requested =
                    new BigInteger("20010db8000000000000000000000001", 16);

            ProtosFutureValue result =
                    connect(x, endpoint(x.prelude, x.activation, 6, requested, 443));

            assertFailedAs(result, x.prelude, "IOError");
        }
    }

    @Test
    void ipv6LoopbackUsesInet6WhenTheHostProvidesIt() throws Exception {
        ServerSocketChannel server = null;
        try {
            server = ServerSocketChannel.open(StandardProtocolFamily.INET6);
            byte[] loopbackBytes = new byte[16];
            loopbackBytes[15] = 1;
            Inet6Address loopback =
                    Inet6Address.getByAddress(null, loopbackBytes, -1);
            server.bind(new InetSocketAddress(loopback, 0));
            server.configureBlocking(false);
        } catch (Exception unavailableIpv6) {
            if (server != null) server.close();
            Assumptions.assumeTrue(false, "IPv6 loopback unavailable");
            return;
        }

        try (ServerSocketChannel ownedServer = server;
                ProtosNioHostIoPoller poller =
                        new ProtosNioHostIoPoller("protos-test-nio-connect-v6")) {
            Fixture x =
                    fixture(
                            poller,
                            bytes -> Inet6Address.getByAddress(null, bytes, -1));
            int port = ((InetSocketAddress) ownedServer.getLocalAddress()).getPort();
            ProtosObjectValue remote =
                    endpoint(x.prelude, x.activation, 6, BigInteger.ONE, port);

            ProtosFutureValue result = connect(x, remote);
            awaitTerminal(result);
            assertEquals(ProtosFutureValue.State.RESOLVED, result.state());

            SocketChannel accepted = awaitAccept(ownedServer);
            try {
                ProtosTcpConnectionValue connection =
                        assertInstanceOf(
                                ProtosTcpConnectionValue.class,
                                result.resolvedValue().orElseThrow());
                Object observedRemote =
                        ProtosInvocation.invokeMessage(
                                connection, "remoteEndpoint", List.of(), x.activation);
                assertSame(
                        ProtosBooleanValue.TRUE,
                        ProtosInvocation.invokeMessage(
                                observedRemote, "==", List.of(remote), x.activation));
                ProtosFutureValue close =
                        (ProtosFutureValue)
                                ProtosInvocation.invokeMessage(
                                        connection, "close", List.of(), x.activation);
                awaitTerminal(close);
                assertEquals(ProtosFutureValue.State.RESOLVED, close.state());
            } finally {
                accepted.close();
            }
        }
    }

    private static Fixture fixture(
            ProtosNioHostIoPoller poller,
            ProtosNioNetworkBackend.Ipv6ScopeResolver ipv6ScopeResolver)
            throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosNioNetworkBackend backend =
                new ProtosNioNetworkBackend(poller, ipv6ScopeResolver);
        return new Fixture(
                prelude,
                activation,
                new ProtosNetworkCapabilityValue(prelude, backend));
    }

    private static ProtosFutureValue connect(Fixture x, ProtosObjectValue endpoint) {
        return (ProtosFutureValue)
                ProtosInvocation.invokeMessage(
                        x.network, "connectTcp", List.of(endpoint), x.activation);
    }

    private static ProtosObjectValue endpoint(
            ProtosPrelude prelude,
            ProtosActivation activation,
            int version,
            BigInteger bits,
            int port) {
        Object addressFactory =
                prelude.bindings().readLocalSlot("IpAddress").orElseThrow();
        ProtosObjectValue address =
                (ProtosObjectValue)
                        ProtosInvocation.invoke(
                                addressFactory,
                                List.of(
                                        new ProtosIntegerValue(BigInteger.valueOf(version)),
                                        new ProtosIntegerValue(bits)),
                                activation);
        Object endpointFactory =
                prelude.bindings().readLocalSlot("IpEndpoint").orElseThrow();
        return (ProtosObjectValue)
                ProtosInvocation.invoke(
                        endpointFactory,
                        List.of(
                                address,
                                new ProtosIntegerValue(BigInteger.valueOf(port))),
                        activation);
    }

    private static SocketChannel awaitAccept(ServerSocketChannel server) throws Exception {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            SocketChannel accepted = server.accept();
            if (accepted != null) return accepted;
            Thread.sleep(5);
        }
        throw new AssertionError("timed out waiting for server-side accept");
    }

    private static void awaitTerminal(ProtosFutureValue future) throws Exception {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (future.state() == ProtosFutureValue.State.PENDING
                && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        if (future.state() == ProtosFutureValue.State.PENDING) {
            throw new AssertionError("timed out waiting for Future");
        }
    }

    private static void assertFailedAs(
            ProtosFutureValue future, ProtosPrelude prelude, String prototypeName) {
        assertEquals(ProtosFutureValue.State.FAILED, future.state());
        assertSame(
                prelude.bindings().readLocalSlot(prototypeName).orElseThrow(),
                future.failedError().orElseThrow().parent().orElseThrow());
    }

    private record Fixture(
            ProtosPrelude prelude,
            ProtosActivation activation,
            ProtosNetworkCapabilityValue network) {}
}
