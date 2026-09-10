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

import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProtosI026FDapTransportTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(8);

    @Test
    void realGraalVmDapInstrumentCompletesTcpHandshake() throws Exception {
        int port = reserveEphemeralPort();

        try (Context context =
                Context.newBuilder(ProtosLanguage.ID)
                        .option("dap", "127.0.0.1:" + port)
                        .option("dap.Suspend", "false")
                        .option("dap.WaitAttached", "false")
                        .build()) {
            context.initialize(ProtosLanguage.ID);

            try (DapClient client = DapClient.connect(port, TIMEOUT)) {
                int initialize =
                        client.request(
                                "initialize",
                                "{\"adapterID\":\"protos\","
                                        + "\"clientID\":\"protos-i026-f1\","
                                        + "\"clientName\":\"Protos I026-F1\","
                                        + "\"linesStartAt1\":true,"
                                        + "\"columnsStartAt1\":true,"
                                        + "\"pathFormat\":\"path\"}");
                String initializeResponse = client.awaitResponse(initialize, TIMEOUT);
                assertSuccessfulResponse(initializeResponse, initialize, "initialize");
                assertTrue(
                        booleanField(initializeResponse, "supportsConfigurationDoneRequest"),
                        "real GraalVM DAP must advertise configurationDone");

                String initialized = client.awaitEvent("initialized", TIMEOUT);
                assertJsonStringField(initialized, "event", "initialized");

                int attach = client.request("attach", "{}");
                assertSuccessfulResponse(
                        client.awaitResponse(attach, TIMEOUT), attach, "attach");

                int configurationDone = client.request("configurationDone", "{}");
                assertSuccessfulResponse(
                        client.awaitResponse(configurationDone, TIMEOUT),
                        configurationDone,
                        "configurationDone");

                int disconnect =
                        client.request(
                                "disconnect",
                                "{\"restart\":false,\"terminateDebuggee\":false}");
                assertSuccessfulResponse(
                        client.awaitResponse(disconnect, TIMEOUT),
                        disconnect,
                        "disconnect");
            }
        }
    }

    private static int reserveEphemeralPort() throws IOException {
        try (ServerSocket socket =
                new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    private static void assertSuccessfulResponse(
            String message, int requestSequence, String command) {
        assertJsonStringField(message, "type", "response");
        assertEquals(
                requestSequence,
                integerField(message, "request_seq"),
                "DAP response must correlate to request");
        assertJsonStringField(message, "command", command);
        assertTrue(
                booleanField(message, "success"),
                () -> "DAP " + command + " failed: " + message);
    }

    private static void assertJsonStringField(
            String message, String name, String expected) {
        String value = stringField(message, name);
        assertEquals(expected, value, () -> "Unexpected DAP message: " + message);
    }

    private static String stringField(String json, String name) {
        Pattern pattern =
                Pattern.compile(
                        "\\\"" + Pattern.quote(name)
                                + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");
        Matcher matcher = pattern.matcher(json);
        assertTrue(matcher.find(), () -> "Missing JSON string field " + name + ": " + json);
        return matcher.group(1);
    }

    private static int integerField(String json, String name) {
        Pattern pattern =
                Pattern.compile(
                        "\\\"" + Pattern.quote(name) + "\\\"\\s*:\\s*(-?[0-9]+)");
        Matcher matcher = pattern.matcher(json);
        assertTrue(matcher.find(), () -> "Missing JSON integer field " + name + ": " + json);
        return Integer.parseInt(matcher.group(1));
    }

    private static boolean booleanField(String json, String name) {
        Pattern pattern =
                Pattern.compile(
                        "\\\"" + Pattern.quote(name) + "\\\"\\s*:\\s*(true|false)");
        Matcher matcher = pattern.matcher(json);
        assertTrue(matcher.find(), () -> "Missing JSON boolean field " + name + ": " + json);
        return Boolean.parseBoolean(matcher.group(1));
    }

    private static final class DapClient implements AutoCloseable {
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

        static DapClient connect(int port, Duration timeout)
                throws IOException, InterruptedException {
            long deadline = System.nanoTime() + timeout.toNanos();
            IOException last = null;

            while (System.nanoTime() < deadline) {
                Socket socket = new Socket();
                try {
                    socket.connect(
                            new InetSocketAddress(
                                    InetAddress.getLoopbackAddress(), port),
                            250);
                    socket.setSoTimeout((int) timeout.toMillis());
                    return new DapClient(socket);
                } catch (IOException failure) {
                    last = failure;
                    try {
                        socket.close();
                    } catch (IOException ignored) {
                    }
                    Thread.sleep(25L);
                }
            }
            throw new IOException(
                    "Timed out connecting to real GraalVM DAP server on loopback:" + port,
                    last);
        }

        int request(String command, String argumentsJson) throws IOException {
            int sequence = nextSequence++;
            String body =
                    "{"
                            + "\"seq\":" + sequence + ","
                            + "\"type\":\"request\","
                            + "\"command\":\"" + command + "\","
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

        private static String optionalStringField(String json, String name) {
            Pattern pattern =
                    Pattern.compile(
                            "\\\"" + Pattern.quote(name)
                                    + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");
            Matcher matcher = pattern.matcher(json);
            return matcher.find() ? matcher.group(1) : null;
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
