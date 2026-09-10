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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProtosI026GLspTransportTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(8);

    @Test
    void realGraalVmLspInstrumentCompletesJsonRpcLifecycle() throws Exception {
        int port = reserveEphemeralPort();
        Context context = null;
        LspClient client = null;

        try {
            context =
                    Context.newBuilder(ProtosLanguage.ID)
                            .allowExperimentalOptions(true)
                            .option("lsp", "127.0.0.1:" + port)
                            .build();
            context.initialize(ProtosLanguage.ID);

            client = LspClient.connect(port, TIMEOUT);

            int initialize =
                    client.request(
                            "initialize",
                            "{"
                                    + "\"processId\":null,"
                                    + "\"rootUri\":null,"
                                    + "\"capabilities\":{}"
                                    + "}");
            String initializeResponse = client.awaitResponse(initialize, TIMEOUT);
            assertSuccessfulResponse(initializeResponse, initialize);
            String result = objectField(initializeResponse, "result");
            String capabilities = objectField(result, "capabilities");
            assertFalse(
                    capabilities.isEmpty(),
                    "real GraalVM LSP initialize response must carry capabilities");

            client.notification("initialized", "{}");

            int shutdown = client.request("shutdown", "null");
            String shutdownResponse = client.awaitResponse(shutdown, TIMEOUT);
            assertSuccessfulResponse(shutdownResponse, shutdown);
            assertTrue(
                    Pattern.compile("\"result\"\\s*:\\s*null")
                            .matcher(shutdownResponse)
                            .find(),
                    () -> "LSP shutdown must return a null result: " + shutdownResponse);

            client.notification("exit", "null");
            client.close();
            client = null;
        } finally {
            if (client != null) {
                client.bestEffortExit();
                client.close();
            }
            if (context != null) {
                context.close();
            }
        }
    }

    private static int reserveEphemeralPort() throws IOException {
        try (ServerSocket socket =
                new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    private static void assertSuccessfulResponse(String message, int requestId) {
        assertEquals("2.0", stringField(message, "jsonrpc"));
        assertEquals(
                requestId,
                integerField(message, "id"),
                "JSON-RPC response must correlate to request");
        assertFalse(
                Pattern.compile("\"error\"\\s*:").matcher(message).find(),
                () -> "LSP request failed: " + message);
    }

    private static String objectField(String json, String fieldName) {
        Matcher matcher =
                Pattern.compile("\\\"" + Pattern.quote(fieldName) + "\\\"\\s*:")
                        .matcher(json);
        assertTrue(
                matcher.find(),
                () -> "Missing JSON object field " + fieldName + ": " + json);

        int start = matcher.end();
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        assertTrue(
                start < json.length() && json.charAt(start) == '{',
                () -> "JSON field " + fieldName + " is not an object: " + json);
        int end = matchingObjectEnd(json, start);
        return json.substring(start, end + 1);
    }

    private static int matchingObjectEnd(String json, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int index = start; index < json.length(); index++) {
            char current = json.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
            } else if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        throw new AssertionError("Unterminated JSON object: " + json.substring(start));
    }

    private static String stringField(String json, String name) {
        Matcher matcher =
                Pattern.compile(
                                "\\\"" + Pattern.quote(name)
                                        + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"")
                        .matcher(json);
        assertTrue(
                matcher.find(),
                () -> "Missing JSON string field " + name + ": " + json);
        return matcher.group(1);
    }

    private static int integerField(String json, String name) {
        Matcher matcher =
                Pattern.compile(
                                "\\\"" + Pattern.quote(name) + "\\\"\\s*:\\s*([0-9]+)")
                        .matcher(json);
        assertTrue(
                matcher.find(),
                () -> "Missing JSON integer field " + name + ": " + json);
        return Integer.parseInt(matcher.group(1));
    }

    private static String jsonString(String value) {
        StringBuilder result = new StringBuilder(value.length() + 8);
        result.append('"');
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (current < 0x20) {
                        result.append(String.format("\\u%04x", (int) current));
                    } else {
                        result.append(current);
                    }
                }
            }
        }
        return result.append('"').toString();
    }

    private static final class LspClient implements AutoCloseable {
        private static final Pattern RESPONSE_ID =
                Pattern.compile("\\\"id\\\"\\s*:\\s*([0-9]+)");

        private final Socket socket;
        private final BufferedInputStream input;
        private final BufferedOutputStream output;
        private final Deque<String> deferred = new ArrayDeque<>();
        private int nextId = 1;

        private LspClient(Socket socket) throws IOException {
            this.socket = socket;
            this.input = new BufferedInputStream(socket.getInputStream());
            this.output = new BufferedOutputStream(socket.getOutputStream());
        }

        static LspClient connect(int port, Duration timeout)
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
                    return new LspClient(socket);
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
                    "Timed out connecting to real GraalVM LSP server on loopback:" + port,
                    last);
        }

        int request(String method, String paramsJson) throws IOException {
            int id = nextId++;
            write(
                    "{"
                            + "\"jsonrpc\":\"2.0\","
                            + "\"id\":" + id + ","
                            + "\"method\":" + jsonString(method) + ","
                            + "\"params\":" + paramsJson
                            + "}");
            return id;
        }

        void notification(String method, String paramsJson) throws IOException {
            write(
                    "{"
                            + "\"jsonrpc\":\"2.0\","
                            + "\"method\":" + jsonString(method) + ","
                            + "\"params\":" + paramsJson
                            + "}");
        }

        String awaitResponse(int requestId, Duration timeout) throws IOException {
            String existing = removeDeferredResponse(requestId);
            if (existing != null) {
                return existing;
            }

            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                String message = read();
                if (responseId(message) == requestId) {
                    return message;
                }
                deferred.addLast(message);
            }
            throw new IOException("Timed out waiting for LSP response id=" + requestId);
        }

        void bestEffortExit() {
            try {
                notification("exit", "null");
            } catch (Exception ignored) {
            }
        }

        private String removeDeferredResponse(int requestId) {
            Iterator<String> iterator = deferred.iterator();
            while (iterator.hasNext()) {
                String message = iterator.next();
                if (responseId(message) == requestId) {
                    iterator.remove();
                    return message;
                }
            }
            return null;
        }

        private static int responseId(String message) {
            Matcher matcher = RESPONSE_ID.matcher(message);
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
                    throw new IOException("Malformed LSP header: " + header);
                }
                String name = header.substring(0, colon).trim();
                String value = header.substring(colon + 1).trim();
                if ("Content-Length".equalsIgnoreCase(name)) {
                    contentLength = Integer.parseInt(value);
                }
            }

            if (contentLength < 0) {
                throw new IOException("LSP message has no Content-Length header");
            }

            byte[] body = input.readNBytes(contentLength);
            if (body.length != contentLength) {
                throw new EOFException(
                        "LSP body truncated: expected "
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
                    throw new EOFException("LSP socket closed while reading headers");
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
