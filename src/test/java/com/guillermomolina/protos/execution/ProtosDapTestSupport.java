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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared real-DAP test infrastructure (AUD007-B2).
 *
 * <p>Port ownership never leaves the operating system: tests bind the DAP instrument to {@link
 * #EPHEMERAL_LOOPBACK} and learn the bound port from the readiness line the instrument publishes
 * after binding, through the same {@link ProtosGraalDapReadinessAdapter} the product debug host
 * uses. Pre-allocating a port number and releasing it before the DAP bind is a TOCTOU race and
 * is pinned out by {@code ProtosAud007DapPortOwnershipTest}.
 */
final class ProtosDapTestSupport {
    /** The only DAP address tests may configure: IPv4 loopback, OS-assigned ephemeral port. */
    static final String EPHEMERAL_LOOPBACK = "127.0.0.1:0";

    private ProtosDapTestSupport() {}

    /** Engine/Context {@code out} stream that captures the DAP-bound endpoint. */
    static ProtosGraalDapReadinessAdapter newReadiness() {
        return new ProtosGraalDapReadinessAdapter(OutputStream.nullOutputStream());
    }

    private static String optionalStringField(String json, String name) {
        Matcher matcher =
                Pattern.compile(
                                "\\\"" + Pattern.quote(name)
                                        + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"")
                        .matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String jsonString(String value) {
        if (!value.matches("[A-Za-z]+")) {
            throw new IllegalArgumentException("DAP command must be a plain identifier: " + value);
        }
        return "\"" + value + "\"";
    }

    static final class DapClient implements AutoCloseable {
        private static final Pattern RESPONSE_REQUEST =
                Pattern.compile("\\\"request_seq\\\"\\s*:\\s*([0-9]+)");

        private final Socket socket;
        private final BufferedInputStream input;
        private final BufferedOutputStream output;
        private final Deque<String> deferred = new ArrayDeque<>();
        private int nextSequence = 1;

        private DapClient(Socket socket) throws IOException {
            this.socket = socket;
            this.input = new BufferedInputStream(socket.getInputStream());
            this.output = new BufferedOutputStream(socket.getOutputStream());
        }

        /**
         * Connects to the endpoint that the real DAP instrument itself published after binding.
         * There is deliberately no overload taking a port number.
         */
        static DapClient connect(ProtosGraalDapReadinessAdapter readiness, Duration timeout)
                throws IOException {
            ProtosPolyglotRuntimeHost.DebugEndpoint endpoint = readiness.requireEndpoint();
            InetAddress address = InetAddress.getByName(endpoint.host());
            if (!address.isLoopbackAddress()) {
                throw new IOException("DAP endpoint escaped loopback: " + endpoint);
            }
            // The listener is already bound and listening when readiness is published, so one
            // connect attempt is authoritative; there is no bind race left to retry around.
            Socket socket = new Socket();
            try {
                socket.connect(
                        new InetSocketAddress(address, endpoint.port()),
                        (int) timeout.toMillis());
                socket.setSoTimeout((int) timeout.toMillis());
                return new DapClient(socket);
            } catch (IOException failure) {
                socket.close();
                throw failure;
            }
        }

        int request(String command, String argumentsJson) throws IOException {
            int sequence = nextSequence++;
            String body =
                    "{"
                            + "\"seq\":" + sequence + ","
                            + "\"type\":\"request\","
                            + "\"command\":" + jsonString(command) + ","
                            + "\"arguments\":" + argumentsJson
                            + "}";
            write(body);
            return sequence;
        }

        String awaitResponse(int requestSequence, Duration timeout) throws IOException {
            String existing = removeDeferredResponse(requestSequence);
            if (existing != null) {
                return existing;
            }

            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                String message = read();
                if ("response".equals(optionalStringField(message, "type"))
                        && responseRequestSequence(message) == requestSequence) {
                    return message;
                }
                deferred.addLast(message);
            }
            throw new IOException(
                    "Timed out waiting for DAP response request_seq=" + requestSequence);
        }

        String awaitEvent(String eventName, Duration timeout) throws IOException {
            String existing = removeDeferredEvent(eventName);
            if (existing != null) {
                return existing;
            }

            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                String message = read();
                if ("event".equals(optionalStringField(message, "type"))
                        && eventName.equals(optionalStringField(message, "event"))) {
                    return message;
                }
                deferred.addLast(message);
            }
            throw new IOException("Timed out waiting for DAP event " + eventName);
        }

        void bestEffortDisconnect() {
            try {
                int request =
                        request(
                                "disconnect",
                                "{\"restart\":false,\"terminateDebuggee\":false}");
                awaitResponse(request, Duration.ofSeconds(1));
            } catch (Exception ignored) {
            }
        }

        private String removeDeferredResponse(int requestSequence) {
            Iterator<String> iterator = deferred.iterator();
            while (iterator.hasNext()) {
                String message = iterator.next();
                if ("response".equals(optionalStringField(message, "type"))
                        && responseRequestSequence(message) == requestSequence) {
                    iterator.remove();
                    return message;
                }
            }
            return null;
        }

        private String removeDeferredEvent(String eventName) {
            Iterator<String> iterator = deferred.iterator();
            while (iterator.hasNext()) {
                String message = iterator.next();
                if ("event".equals(optionalStringField(message, "type"))
                        && eventName.equals(optionalStringField(message, "event"))) {
                    iterator.remove();
                    return message;
                }
            }
            return null;
        }

        private static int responseRequestSequence(String message) {
            Matcher matcher = RESPONSE_REQUEST.matcher(message);
            return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
        }

        private void write(String body) throws IOException {
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            output.write(
                    ("Content-Length: " + payload.length + "\r\n\r\n")
                            .getBytes(StandardCharsets.US_ASCII));
            output.write(payload);
            output.flush();
        }

        private String read() throws IOException {
            int contentLength = -1;
            while (true) {
                String header = readHeaderLine();
                if (header.isEmpty()) {
                    break;
                }
                int colon = header.indexOf(':');
                if (colon <= 0) {
                    throw new IOException("Malformed DAP header: " + header);
                }
                String name = header.substring(0, colon).trim();
                String value = header.substring(colon + 1).trim();
                if ("Content-Length".equalsIgnoreCase(name)) {
                    contentLength = Integer.parseInt(value);
                }
            }

            if (contentLength < 0) {
                throw new IOException("DAP message has no Content-Length header");
            }

            byte[] body = input.readNBytes(contentLength);
            if (body.length != contentLength) {
                throw new EOFException(
                        "DAP body truncated: expected "
                                + contentLength
                                + " bytes, got "
                                + body.length);
            }
            return new String(body, StandardCharsets.UTF_8);
        }

        private String readHeaderLine() throws IOException {
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            int previous = -1;

            while (true) {
                int current = input.read();
                if (current < 0) {
                    throw new EOFException("DAP socket closed while reading headers");
                }
                if (previous == '\r' && current == '\n') {
                    byte[] bytes = line.toByteArray();
                    int length = Math.max(0, bytes.length - 1);
                    return new String(bytes, 0, length, StandardCharsets.US_ASCII);
                }
                line.write(current);
                previous = current;
            }
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
