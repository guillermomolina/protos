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

import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosNetworkConnectFlow;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import java.io.IOException;
import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** I028-E2 production NIO implementation of the existing host-neutral connect contract. */
final class ProtosNioNetworkBackend implements ProtosNetworkConnectFlow.Backend {
    @FunctionalInterface
    interface Ipv6ScopeResolver {
        Inet6Address resolve(byte[] addressBytes) throws IOException;
    }

    private static final Set<String> ENDPOINT_SLOTS = Set.of("address", "port");
    private static final Set<String> ADDRESS_SLOTS = Set.of("version", "bits");
    private static final ProtosNetworkConnectFlow.Cancellation NO_CANCELLATION = () -> {};

    private final ProtosNioHostIoPoller poller;
    private final Ipv6ScopeResolver ipv6ScopeResolver;

    ProtosNioNetworkBackend(
            ProtosNioHostIoPoller poller, Ipv6ScopeResolver ipv6ScopeResolver) {
        this.poller = Objects.requireNonNull(poller, "poller");
        this.ipv6ScopeResolver =
                Objects.requireNonNull(ipv6ScopeResolver, "ipv6ScopeResolver");
    }

    @Override
    public ProtosNetworkConnectFlow.Cancellation connect(
            ProtosObjectValue endpoint,
            ProtosNetworkConnectFlow.ConnectCompletion completion) {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(completion, "completion");

        HostEndpoint target;
        try {
            target = HostEndpoint.decode(endpoint, ipv6ScopeResolver);
        } catch (IOException | RuntimeException invalidHostInterpretation) {
            reportFailure(completion);
            return NO_CANCELLATION;
        }

        ConnectAttempt attempt = new ConnectAttempt(target, completion);
        try {
            poller.submit(attempt::startOnPoller, attempt::commandFailed);
        } catch (RuntimeException unavailablePoller) {
            attempt.commandFailed(unavailablePoller);
        }
        return attempt::cancel;
    }

    private final class ConnectAttempt implements ProtosNioHostIoPoller.SelectionHandler {
        private static final int ACQUIRING = 0;
        private static final int CANCELLED = 1;
        private static final int HANDED_OFF = 2;
        private static final int FAILED = 3;

        private final HostEndpoint target;
        private final ProtosNetworkConnectFlow.ConnectCompletion completion;
        private final AtomicInteger gate = new AtomicInteger(ACQUIRING);
        private volatile SocketChannel channel;
        private volatile ProtosNioTcpConnectionBackend connectionBackend;

        private ConnectAttempt(
                HostEndpoint target, ProtosNetworkConnectFlow.ConnectCompletion completion) {
            this.target = target;
            this.completion = completion;
        }

        private void startOnPoller() {
            if (gate.get() != ACQUIRING) return;
            try {
                SocketChannel opened = SocketChannel.open(target.protocolFamily());
                opened.configureBlocking(false);
                channel = opened;
                ProtosNioTcpConnectionBackend acquired =
                        new ProtosNioTcpConnectionBackend(poller, opened);
                connectionBackend = acquired;

                if (gate.get() != ACQUIRING) {
                    acquired.releaseIfUntransferred();
                    return;
                }

                boolean connected = opened.connect(target.socketAddress());
                if (gate.get() != ACQUIRING) {
                    acquired.releaseIfUntransferred();
                    return;
                }

                if (connected) {
                    SelectionKey key = poller.register(opened, 0, acquired);
                    acquired.attachKey(key);
                    completeConnected();
                } else {
                    SelectionKey key =
                            poller.register(opened, SelectionKey.OP_CONNECT, this);
                    acquired.attachKey(key);
                    if (gate.get() != ACQUIRING) acquired.releaseIfUntransferred();
                }
            } catch (IOException | RuntimeException failure) {
                fail(failure);
            }
        }

        @Override
        public void ready(SelectionKey key) {
            if (gate.get() != ACQUIRING) {
                releaseCurrent();
                return;
            }
            if (!key.isConnectable()) return;

            try {
                SocketChannel current = channel;
                if (current == null || !current.finishConnect()) return;
                ProtosNioTcpConnectionBackend acquired = connectionBackend;
                if (acquired == null) {
                    throw new IllegalStateException("connected channel has no backend");
                }
                poller.interestOps(key, 0);
                key.attach(acquired);
                completeConnected();
            } catch (IOException | RuntimeException failure) {
                fail(failure);
            }
        }

        @Override
        public void failed(Exception failure) {
            fail(failure);
        }

        @Override
        public void closed() {
            fail(new IOException("NIO poller closed during TCP connect"));
        }

        private void completeConnected() {
            ProtosNioTcpConnectionBackend acquired = connectionBackend;
            SocketChannel current = channel;
            if (acquired == null || current == null) {
                fail(new IllegalStateException("incomplete connected-channel state"));
                return;
            }

            ProtosObjectValue localEndpoint;
            try {
                Object local = current.getLocalAddress();
                if (!(local instanceof InetSocketAddress localSocket)) {
                    throw new IOException("connected TCP socket has no IP local endpoint");
                }
                localEndpoint = target.materializeLocalEndpoint(localSocket);
            } catch (IOException | RuntimeException failure) {
                fail(failure);
                return;
            }

            if (!gate.compareAndSet(ACQUIRING, HANDED_OFF)) {
                acquired.releaseIfUntransferred();
                return;
            }

            try {
                completion.succeeded(
                        acquired,
                        localEndpoint,
                        acquired,
                        acquired::releaseIfUntransferred);
            } catch (RuntimeException completionFailure) {
                acquired.releaseIfUntransferred();
            }
        }

        private void cancel() {
            if (gate.compareAndSet(ACQUIRING, CANCELLED)) {
                releaseCurrent();
            }
        }

        private void commandFailed(RuntimeException failure) {
            fail(failure);
        }

        private void fail(Exception failure) {
            if (gate.compareAndSet(ACQUIRING, FAILED)) {
                releaseCurrent();
                reportFailure(completion);
            } else if (gate.get() == CANCELLED) {
                releaseCurrent();
            }
        }

        private void releaseCurrent() {
            ProtosNioTcpConnectionBackend acquired = connectionBackend;
            if (acquired != null) {
                acquired.releaseIfUntransferred();
                return;
            }
            SocketChannel current = channel;
            if (current != null) {
                try {
                    current.close();
                } catch (IOException ignored) {
                    // Cleanup cannot manufacture another portable outcome.
                }
            }
        }
    }

    private static final class HostEndpoint {
        private final int version;
        private final InetSocketAddress socketAddress;
        private final ProtosObjectValue endpointPrototype;
        private final ProtosObjectValue addressPrototype;

        private HostEndpoint(
                int version,
                InetSocketAddress socketAddress,
                ProtosObjectValue endpointPrototype,
                ProtosObjectValue addressPrototype) {
            this.version = version;
            this.socketAddress = socketAddress;
            this.endpointPrototype = endpointPrototype;
            this.addressPrototype = addressPrototype;
        }

        private static HostEndpoint decode(
                ProtosObjectValue endpoint, Ipv6ScopeResolver ipv6ScopeResolver)
                throws IOException {
            Object endpointParent = endpoint.parent().orElse(null);
            if (!(endpointParent instanceof ProtosObjectValue endpointPrototype)) {
                throw new IOException("endpoint has no canonical ordinary parent");
            }

            Map<String, Object> endpointSlots = endpoint.localSlotsSnapshot();
            if (!endpoint.isFrozen()
                    || !endpointSlots.keySet().equals(ENDPOINT_SLOTS)
                    || !(endpointSlots.get("address") instanceof ProtosObjectValue address)
                    || !(endpointSlots.get("port") instanceof ProtosIntegerValue port)) {
                throw new IOException("endpoint is not canonical");
            }

            Object addressParent = address.parent().orElse(null);
            if (!(addressParent instanceof ProtosObjectValue addressPrototype)
                    || !ProtosStandardIpEndpointProtocol.recognizesValue(
                            endpoint, endpointPrototype, addressPrototype)) {
                throw new IOException("endpoint is not recognized");
            }

            Map<String, Object> addressSlots = address.localSlotsSnapshot();
            if (!addressSlots.keySet().equals(ADDRESS_SLOTS)
                    || !(addressSlots.get("version") instanceof ProtosIntegerValue versionValue)
                    || !(addressSlots.get("bits") instanceof ProtosIntegerValue bitsValue)) {
                throw new IOException("address is not canonical");
            }

            int version = versionValue.value().intValueExact();
            int portNumber = port.value().intValueExact();
            InetAddress hostAddress;
            if (version == 4) {
                byte[] bytes = unsignedBytes(bitsValue.value(), 4);
                hostAddress = InetAddress.getByAddress(bytes);
                if (!(hostAddress instanceof Inet4Address)) {
                    throw new IOException("IPv4 data did not materialize as IPv4");
                }
            } else if (version == 6) {
                byte[] bytes = unsignedBytes(bitsValue.value(), 16);
                Inet6Address resolved =
                        Objects.requireNonNull(
                                ipv6ScopeResolver.resolve(bytes.clone()),
                                "IPv6 scope resolver result");
                if (!Arrays.equals(bytes, resolved.getAddress())) {
                    throw new IOException(
                            "Network scope interpretation changed IPv6 address bits");
                }
                if (resolved.isLinkLocalAddress()
                        && resolved.getScopeId() == 0
                        && resolved.getScopedInterface() == null) {
                    throw new IOException(
                            "link-local IPv6 requires explicit Network scope");
                }
                hostAddress = resolved;
            } else {
                throw new IOException("unsupported IP version");
            }

            return new HostEndpoint(
                    version,
                    new InetSocketAddress(hostAddress, portNumber),
                    endpointPrototype,
                    addressPrototype);
        }

        private StandardProtocolFamily protocolFamily() {
            return version == 4
                    ? StandardProtocolFamily.INET
                    : StandardProtocolFamily.INET6;
        }

        private InetSocketAddress socketAddress() {
            return socketAddress;
        }

        private ProtosObjectValue materializeLocalEndpoint(InetSocketAddress local)
                throws IOException {
            InetAddress localAddress = local.getAddress();
            byte[] bytes = localAddress == null ? null : localAddress.getAddress();
            if (localAddress == null || bytes == null || local.getPort() <= 0) {
                throw new IOException("connected TCP socket has invalid local endpoint");
            }
            if ((version == 4
                            && (!(localAddress instanceof Inet4Address) || bytes.length != 4))
                    || (version == 6
                            && (!(localAddress instanceof Inet6Address) || bytes.length != 16))) {
                throw new IOException("local endpoint changed the requested IP version");
            }

            ProtosObjectValue address = new ProtosObjectValue(addressPrototype);
            address.createLocalSlot(
                    "version", new ProtosIntegerValue(BigInteger.valueOf(version)));
            address.createLocalSlot(
                    "bits", new ProtosIntegerValue(new BigInteger(1, bytes)));
            address.freeze();

            ProtosObjectValue endpoint = new ProtosObjectValue(endpointPrototype);
            endpoint.createLocalSlot("address", address);
            endpoint.createLocalSlot(
                    "port", new ProtosIntegerValue(BigInteger.valueOf(local.getPort())));
            return endpoint.freeze();
        }

        private static byte[] unsignedBytes(BigInteger value, int width)
                throws IOException {
            if (value.signum() < 0 || value.bitLength() > width * 8) {
                throw new IOException("IP address bits exceed canonical width");
            }
            byte[] raw = value.toByteArray();
            int offset = raw.length > 1 && raw[0] == 0 ? 1 : 0;
            int length = raw.length - offset;
            if (length > width) throw new IOException("IP address bits exceed width");
            byte[] result = new byte[width];
            System.arraycopy(raw, offset, result, width - length, length);
            return result;
        }
    }

    private static void reportFailure(
            ProtosNetworkConnectFlow.ConnectCompletion completion) {
        try {
            completion.failed();
        } catch (RuntimeException ignored) {
            // A callback bug cannot create a second backend outcome.
        }
    }
}
