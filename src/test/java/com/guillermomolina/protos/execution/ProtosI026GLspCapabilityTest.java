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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProtosI026GLspCapabilityTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    private static final String SOURCE_TEXT = "value: 42\nvalue\n";

    @Test
    void realGraalVmLspRecordsCurrentProtosCapabilityBoundary() throws Exception {
        Path sourceFile = Files.createTempFile("protos-i026-g2-", ".protos");
        Files.writeString(sourceFile, SOURCE_TEXT, StandardCharsets.UTF_8);

        int port = reserveEphemeralPort();
        Context context = null;
        LspClient client = null;
        boolean exited = false;

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
            assertCapabilityMatrix(initializeResponse);

            client.notification("initialized", "{}");

            String uri = sourceFile.toUri().toString();
            client.notification(
                    "textDocument/didOpen",
                    "{"
                            + "\"textDocument\":{"
                            + "\"uri\":" + jsonString(uri) + ","
                            + "\"languageId\":\"protos\","
                            + "\"version\":1,"
                            + "\"text\":" + jsonString(SOURCE_TEXT)
                            + "}"
                            + "}");

            String diagnostics =
                    client.awaitNotificationForUri(
                            "textDocument/publishDiagnostics", uri, TIMEOUT);
            String diagnosticsParams = objectField(diagnostics, "params");
            assertEquals(uri, stringField(diagnosticsParams, "uri"));
            assertTrue(
                    elementsInArray(arrayField(diagnosticsParams, "diagnostics")).isEmpty(),
                    () -> "valid Protos document must parse without diagnostics: " + diagnostics);

            int coverageRequest =
                    client.request(
                            "workspace/executeCommand",
                            "{"
                                    + "\"command\":\"get_coverage\","
                                    + "\"arguments\":[" + jsonString(uri) + "]"
                                    + "}");
            String coverageResponse = client.awaitResponse(coverageRequest, TIMEOUT);
            assertSuccessfulResponse(coverageResponse, coverageRequest);
            String coverage = objectField(coverageResponse, "result");
            assertTrue(
                    elementsInArray(arrayField(coverage, "covered")).isEmpty(),
                    "no guest execution occurred during G2 structural coverage probe");
            assertFalse(
                    elementsInArray(arrayField(coverage, "uncovered")).isEmpty(),
                    "parsed Protos StatementTag sections must be visible as uncovered ranges");

            String position =
                    "{"
                            + "\"textDocument\":{\"uri\":" + jsonString(uri) + "},"
                            + "\"position\":{\"line\":1,\"character\":2}"
                            + "}";

            int completionRequest = client.request("textDocument/completion", position);
            String completionResponse = client.awaitResponse(completionRequest, TIMEOUT);
            assertSuccessfulResponse(completionResponse, completionRequest);
            String completion = objectField(completionResponse, "result");
            assertFalse(booleanField(completion, "isIncomplete"));
            assertTrue(
                    elementsInArray(arrayField(completion, "items")).isEmpty(),
                    "without a suspended/coverage activation and without a top scope, "
                            + "generic Protos completion must stay empty");

            int hoverRequest = client.request("textDocument/hover", position);
            String hoverResponse = client.awaitResponse(hoverRequest, TIMEOUT);
            assertSuccessfulResponse(hoverResponse, hoverRequest);
            String hover = objectField(hoverResponse, "result");
            assertTrue(
                    elementsInArray(arrayField(hover, "contents")).isEmpty(),
                    "without runtime coverage data, generic Protos hover must stay empty");

            int highlightRequest = client.request("textDocument/documentHighlight", position);
            String highlightResponse = client.awaitResponse(highlightRequest, TIMEOUT);
            assertSuccessfulResponse(highlightResponse, highlightRequest);
            assertTrue(
                    elementsInArray(arrayFieldFromResult(highlightResponse)).isEmpty(),
                    "I026-C did not adopt ReadVariableTag/WriteVariableTag, so highlight is empty");

            int signatureRequest = client.request("textDocument/signatureHelp", position);
            String signatureResponse = client.awaitResponse(signatureRequest, TIMEOUT);
            assertSuccessfulResponse(signatureResponse, signatureRequest);
            String signatureHelp = objectField(signatureResponse, "result");
            assertTrue(
                    elementsInArray(arrayField(signatureHelp, "signatures")).isEmpty(),
                    "without a language trigger/runtime executable signature, "
                            + "generic Protos signatureHelp must stay empty");

            String codeRange =
                    "{"
                            + "\"start\":{\"line\":0,\"character\":0},"
                            + "\"end\":{\"line\":1,\"character\":5}"
                            + "}";
            int codeActionRequest =
                    client.request(
                            "textDocument/codeAction",
                            "{"
                                    + "\"textDocument\":{\"uri\":" + jsonString(uri) + "},"
                                    + "\"range\":" + codeRange + ","
                                    + "\"context\":{\"diagnostics\":[]}"
                                    + "}");
            String codeActionResponse = client.awaitResponse(codeActionRequest, TIMEOUT);
            assertSuccessfulResponse(codeActionResponse, codeActionRequest);
            assertTrue(
                    elementsInArray(arrayFieldFromResult(codeActionResponse)).isEmpty(),
                    "generic GraalVM codeAction provider currently yields no Protos actions");

            int codeLensRequest =
                    client.request(
                            "textDocument/codeLens",
                            "{\"textDocument\":{\"uri\":" + jsonString(uri) + "}}");
            String codeLensResponse = client.awaitResponse(codeLensRequest, TIMEOUT);
            assertSuccessfulResponse(codeLensResponse, codeLensRequest);
            assertTrue(
                    elementsInArray(arrayFieldFromResult(codeLensResponse)).isEmpty(),
                    "generic GraalVM codeLens provider currently yields no Protos lenses");

            client.notification(
                    "textDocument/didClose",
                    "{\"textDocument\":{\"uri\":" + jsonString(uri) + "}}");

            int shutdown = client.request("shutdown", "null");
            String shutdownResponse = client.awaitResponse(shutdown, TIMEOUT);
            assertSuccessfulResponse(shutdownResponse, shutdown);
            assertTrue(
                    Pattern.compile("\"result\"\\s*:\\s*null")
                            .matcher(shutdownResponse)
                            .find(),
                    () -> "LSP shutdown must return null: " + shutdownResponse);

            client.notification("exit", "null");
            exited = true;
            client.close();
            client = null;
        } finally {
            if (client != null) {
                if (!exited) {
                    client.bestEffortExit();
                }
                client.close();
            }
            if (context != null) {
                context.close();
            }
            Files.deleteIfExists(sourceFile);
        }
    }

    private static void assertCapabilityMatrix(String initializeResponse) {
        String result = objectField(initializeResponse, "result");
        String capabilities = objectField(result, "capabilities");

        assertEquals(
                2,
                integerField(capabilities, "textDocumentSync"),
                "GraalVM generic LSP must advertise incremental text synchronization");

        assertTrue(booleanField(capabilities, "hoverProvider"));
        assertTrue(booleanField(capabilities, "documentHighlightProvider"));
        assertTrue(booleanField(capabilities, "codeActionProvider"));

        assertFalse(booleanField(capabilities, "documentSymbolProvider"));
        assertFalse(booleanField(capabilities, "workspaceSymbolProvider"));
        assertFalse(booleanField(capabilities, "definitionProvider"));
        assertFalse(booleanField(capabilities, "referencesProvider"));

        assertFalse(objectField(capabilities, "completionProvider").isEmpty());
        assertFalse(objectField(capabilities, "signatureHelpProvider").isEmpty());
        assertFalse(objectField(capabilities, "codeLensProvider").isEmpty());

        String executeCommand = objectField(capabilities, "executeCommandProvider");
        Set<String> commands =
                stringValuesInArray(arrayField(executeCommand, "commands"));
        assertTrue(commands.contains("dry_run"));
        assertTrue(commands.contains("get_coverage"));
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

    private static String arrayFieldFromResult(String response) {
        Matcher matcher =
                Pattern.compile("\"result\"\\s*:")
                        .matcher(response);
        assertTrue(matcher.find(), () -> "Missing result field: " + response);
        int start = skipWhitespace(response, matcher.end());
        assertTrue(
                start < response.length() && response.charAt(start) == '[',
                () -> "LSP result is not an array: " + response);
        int end = matchingCompositeEnd(response, start, '[', ']');
        return response.substring(start, end + 1);
    }

    private static String objectField(String json, String fieldName) {
        Matcher matcher =
                Pattern.compile(
                                "\"" + Pattern.quote(fieldName) + "\"\\s*:")
                        .matcher(json);
        assertTrue(
                matcher.find(),
                () -> "Missing JSON object field " + fieldName + ": " + json);

        int start = skipWhitespace(json, matcher.end());
        assertTrue(
                start < json.length() && json.charAt(start) == '{',
                () -> "JSON field " + fieldName + " is not an object: " + json);
        int end = matchingCompositeEnd(json, start, '{', '}');
        return json.substring(start, end + 1);
    }

    private static String arrayField(String json, String fieldName) {
        Matcher matcher =
                Pattern.compile(
                                "\"" + Pattern.quote(fieldName) + "\"\\s*:")
                        .matcher(json);
        assertTrue(
                matcher.find(),
                () -> "Missing JSON array field " + fieldName + ": " + json);

        int start = skipWhitespace(json, matcher.end());
        assertTrue(
                start < json.length() && json.charAt(start) == '[',
                () -> "JSON field " + fieldName + " is not an array: " + json);
        int end = matchingCompositeEnd(json, start, '[', ']');
        return json.substring(start, end + 1);
    }

    private static int skipWhitespace(String json, int start) {
        int index = start;
        while (index < json.length() && Character.isWhitespace(json.charAt(index))) {
            index++;
        }
        return index;
    }

    private static int matchingCompositeEnd(
            String json, int start, char open, char close) {
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
            } else if (current == open) {
                depth++;
            } else if (current == close) {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        throw new AssertionError("Unterminated JSON composite: " + json.substring(start));
    }

    private static List<String> elementsInArray(String arrayJson) {
        List<String> elements = new ArrayList<>();
        int index = 1;

        while (index < arrayJson.length() - 1) {
            index = skipWhitespaceAndCommas(arrayJson, index);
            if (index >= arrayJson.length() - 1) {
                break;
            }

            char current = arrayJson.charAt(index);
            int end;
            if (current == '{') {
                end = matchingCompositeEnd(arrayJson, index, '{', '}');
            } else if (current == '[') {
                end = matchingCompositeEnd(arrayJson, index, '[', ']');
            } else if (current == '"') {
                end = matchingStringEnd(arrayJson, index);
            } else {
                end = scalarEnd(arrayJson, index);
            }
            elements.add(arrayJson.substring(index, end + 1));
            index = end + 1;
        }

        return elements;
    }

    private static Set<String> stringValuesInArray(String arrayJson) {
        return elementsInArray(arrayJson).stream()
                .map(
                        item -> {
                            assertTrue(
                                    item.length() >= 2
                                            && item.charAt(0) == '"'
                                            && item.charAt(item.length() - 1) == '"',
                                    () -> "Expected string array item: " + item);
                            return decodeJsonString(item.substring(1, item.length() - 1));
                        })
                .collect(Collectors.toSet());
    }

    private static int skipWhitespaceAndCommas(String text, int start) {
        int index = start;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (!Character.isWhitespace(current) && current != ',') {
                return index;
            }
            index++;
        }
        return index;
    }

    private static int matchingStringEnd(String json, int start) {
        boolean escaped = false;
        for (int index = start + 1; index < json.length(); index++) {
            char current = json.charAt(index);
            if (escaped) {
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else if (current == '"') {
                return index;
            }
        }
        throw new AssertionError("Unterminated JSON string: " + json.substring(start));
    }

    private static int scalarEnd(String json, int start) {
        int index = start;
        while (index + 1 < json.length()) {
            char next = json.charAt(index + 1);
            if (next == ',' || next == ']' || Character.isWhitespace(next)) {
                return index;
            }
            index++;
        }
        return index;
    }

    private static String stringField(String json, String name) {
        Matcher matcher =
                Pattern.compile(
                                "\"" + Pattern.quote(name)
                                        + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
                        .matcher(json);
        assertTrue(
                matcher.find(),
                () -> "Missing JSON string field " + name + ": " + json);
        return decodeJsonString(matcher.group(1));
    }

    private static int integerField(String json, String name) {
        Matcher matcher =
                Pattern.compile(
                                "\"" + Pattern.quote(name) + "\"\\s*:\\s*(-?[0-9]+)")
                        .matcher(json);
        assertTrue(
                matcher.find(),
                () -> "Missing JSON integer field " + name + ": " + json);
        return Integer.parseInt(matcher.group(1));
    }

    private static boolean booleanField(String json, String name) {
        Matcher matcher =
                Pattern.compile(
                                "\"" + Pattern.quote(name) + "\"\\s*:\\s*(true|false)")
                        .matcher(json);
        assertTrue(
                matcher.find(),
                () -> "Missing JSON boolean field " + name + ": " + json);
        return Boolean.parseBoolean(matcher.group(1));
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

    private static String decodeJsonString(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current != '\\') {
                result.append(current);
                continue;
            }

            if (++index >= value.length()) {
                throw new AssertionError("Malformed JSON escape: " + value);
            }

            char escaped = value.charAt(index);
            switch (escaped) {
                case '"' -> result.append('"');
                case '\\' -> result.append('\\');
                case '/' -> result.append('/');
                case 'b' -> result.append('\b');
                case 'f' -> result.append('\f');
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case 'u' -> {
                    if (index + 4 >= value.length()) {
                        throw new AssertionError("Malformed JSON unicode escape: " + value);
                    }
                    result.append(
                            (char)
                                    Integer.parseInt(
                                            value.substring(index + 1, index + 5), 16));
                    index += 4;
                }
                default -> throw new AssertionError("Malformed JSON escape: " + value);
            }
        }
        return result.toString();
    }

    private static final class LspClient implements AutoCloseable {
        private static final Pattern RESPONSE_ID =
                Pattern.compile("\"id\"\\s*:\\s*([0-9]+)");

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

        String awaitNotificationForUri(
                String method, String uri, Duration timeout) throws IOException {
            String existing = removeDeferredNotificationForUri(method, uri);
            if (existing != null) {
                return existing;
            }

            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                String message = read();
                if (matchesNotificationForUri(message, method, uri)) {
                    return message;
                }
                deferred.addLast(message);
            }

            throw new IOException(
                    "Timed out waiting for LSP notification "
                            + method
                            + " for "
                            + uri);
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

        private String removeDeferredNotificationForUri(
                String method, String uri) {
            Iterator<String> iterator = deferred.iterator();
            while (iterator.hasNext()) {
                String message = iterator.next();
                if (matchesNotificationForUri(message, method, uri)) {
                    iterator.remove();
                    return message;
                }
            }
            return null;
        }

        private static boolean matchesNotificationForUri(
                String message, String method, String uri) {
            String messageMethod = optionalStringField(message, "method");
            if (!method.equals(messageMethod)) {
                return false;
            }
            try {
                String params = objectField(message, "params");
                return uri.equals(stringField(params, "uri"));
            } catch (AssertionError ignored) {
                return false;
            }
        }

        private static int responseId(String message) {
            Matcher matcher = RESPONSE_ID.matcher(message);
            return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
        }

        private static String optionalStringField(String json, String name) {
            Matcher matcher =
                    Pattern.compile(
                                    "\"" + Pattern.quote(name)
                                            + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
                            .matcher(json);
            return matcher.find() ? decodeJsonString(matcher.group(1)) : null;
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
