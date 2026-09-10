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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.source.Source;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.math.BigInteger;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProtosI026FDapBehaviorTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    private static final String SOURCE_TEXT = "text\nnumber\nflag\n";

    @Test
    void realDapBreakpointScopesValuesAndNextStayFaithfulToProtos() throws Exception {
        Path sourceFile = Files.createTempFile("protos-i026-f2-", ".protos");
        Files.writeString(sourceFile, SOURCE_TEXT, StandardCharsets.UTF_8);

        int port = reserveEphemeralPort();
        ExecutorService guestExecutor = Executors.newSingleThreadExecutor();
        Future<ProtosExecutionOutcome> guestFuture = null;
        Context context = null;
        DapClient client = null;
        boolean disconnected = false;

        try {
            context =
                    Context.newBuilder(ProtosLanguage.ID)
                            .option("dap", "127.0.0.1:" + port)
                            .option("dap.Suspend", "false")
                            .option("dap.WaitAttached", "false")
                            .build();
            context.initialize(ProtosLanguage.ID);

            client = DapClient.connect(port, TIMEOUT);
            initializeAndAttach(client);

            Source source =
                    Source.newBuilder(
                                    ProtosLanguage.ID,
                                    SOURCE_TEXT,
                                    sourceFile.getFileName().toString())
                            .uri(sourceFile.toUri())
                            .mimeType(ProtosLanguage.MIME_TYPE)
                            .build();

            final CallTarget target;
            context.enter();
            try {
                target = ProtosLanguageContext.current().parsePublic(source);
            } finally {
                context.leave();
            }

            String loadedSource = loadedSource(client, sourceFile.getFileName().toString());
            String loadedPath = stringField(loadedSource, "path");
            assertFalse(loadedPath.isEmpty(), "readable Protos source must have a DAP path");

            String dapSource =
                    "{\"name\":"
                            + jsonString(sourceFile.getFileName().toString())
                            + ",\"path\":"
                            + jsonString(loadedPath)
                            + "}";

            int setBreakpoints =
                    client.request(
                            "setBreakpoints",
                            "{\"source\":"
                                    + dapSource
                                    + ",\"lines\":[1],\"breakpoints\":[{\"line\":1}],"
                                    + "\"sourceModified\":false}");
            String breakpointResponse = client.awaitResponse(setBreakpoints, TIMEOUT);
            assertSuccessfulResponse(breakpointResponse, setBreakpoints, "setBreakpoints");
            List<String> breakpoints =
                    objectsInArray(arrayField(breakpointResponse, "breakpoints"));
            assertEquals(1, breakpoints.size(), "exactly one breakpoint must be returned");
            String requestedBreakpoint = breakpoints.get(0);
            int breakpointId = integerField(requestedBreakpoint, "id");
            assertEquals(1, integerField(requestedBreakpoint, "line"));
            boolean immediatelyVerified = booleanField(requestedBreakpoint, "verified");

            int configurationDone = client.request("configurationDone", "{}");
            assertSuccessfulResponse(
                    client.awaitResponse(configurationDone, TIMEOUT),
                    configurationDone,
                    "configurationDone");

            ProtosActivation activation = activationWithRepresentativeValues();
            Context guestContext = context;
            guestFuture =
                    guestExecutor.submit(
                            () -> {
                                guestContext.enter();
                                try {
                                    return ProtosRootTaskExecution.execute(target, activation);
                                } finally {
                                    guestContext.leave();
                                }
                            });

            if (!immediatelyVerified) {
                String resolvedEvent = client.awaitEvent("breakpoint", TIMEOUT);
                assertEquals("changed", stringField(resolvedEvent, "reason"));
                String resolvedBreakpoint =
                        objectField(objectField(resolvedEvent, "body"), "breakpoint");
                assertEquals(
                        breakpointId,
                        integerField(resolvedBreakpoint, "id"),
                        "resolved event must refer to the requested breakpoint");
                assertTrue(
                        booleanField(resolvedBreakpoint, "verified"),
                        () -> "pending Protos breakpoint did not resolve: " + resolvedEvent);
                assertEquals(
                        1,
                        integerField(resolvedBreakpoint, "line"),
                        "resolved breakpoint must stay on the requested Protos line");
            }

            String firstStop = client.awaitEvent("stopped", TIMEOUT);
            assertEquals("breakpoint", stringField(firstStop, "reason"));
            int threadId = integerField(firstStop, "threadId");

            int threadsRequest = client.request("threads", "{}");
            String threadsResponse = client.awaitResponse(threadsRequest, TIMEOUT);
            assertSuccessfulResponse(threadsResponse, threadsRequest, "threads");
            assertTrue(
                    objectsInArray(arrayField(threadsResponse, "threads")).stream()
                            .anyMatch(thread -> optionalIntegerField(thread, "id") == threadId),
                    "stopped Protos carrier must be present in DAP threads");

            int frameId =
                    assertTopFrame(
                            client,
                            threadId,
                            1,
                            sourceFile.getFileName().toString(),
                            loadedPath);

            int scopesRequest =
                    client.request("scopes", "{\"frameId\":" + frameId + "}");
            String scopesResponse = client.awaitResponse(scopesRequest, TIMEOUT);
            assertSuccessfulResponse(scopesResponse, scopesRequest, "scopes");
            List<String> scopes = objectsInArray(arrayField(scopesResponse, "scopes"));
            assertEquals(
                    1,
                    scopes.size(),
                    "PLAT015 must expose one activation scope and no parent/top scope");
            assertFalse(
                    booleanField(scopes.get(0), "expensive"),
                    "activation-native scope must not be an artificial global scope");
            int scopeReference = integerField(scopes.get(0), "variablesReference");
            assertTrue(scopeReference > 0, "activation scope must expose variables");

            int variablesRequest =
                    client.request(
                            "variables",
                            "{\"variablesReference\":" + scopeReference + "}");
            String variablesResponse = client.awaitResponse(variablesRequest, TIMEOUT);
            assertSuccessfulResponse(variablesResponse, variablesRequest, "variables");
            List<String> variables =
                    objectsInArray(arrayField(variablesResponse, "variables"));

            assertVariableValue(variables, "text", "hello");
            assertVariableValue(variables, "flag", "true");
            assertVariableValue(variables, "number", "42");
            String arrayVariable = variableNamed(variables, "array");
            assertEquals("Array", stringField(arrayVariable, "value"));
            assertEquals(2, integerField(arrayVariable, "indexedVariables"));
            int arrayReference = integerField(arrayVariable, "variablesReference");
            assertTrue(arrayReference > 0, "non-empty Array must be expandable through DAP");

            Set<String> names =
                    variables.stream()
                            .map(variable -> optionalStringField(variable, "name"))
                            .filter(name -> name != null)
                            .collect(Collectors.toSet());
            assertFalse(names.contains("this"), "DAP must not invent a named receiver");
            assertFalse(names.contains("self"), "DAP must not invent a named receiver");

            int indexedRequest =
                    client.request(
                            "variables",
                            "{\"variablesReference\":"
                                    + arrayReference
                                    + ",\"filter\":\"indexed\"}");
            String indexedResponse = client.awaitResponse(indexedRequest, TIMEOUT);
            assertSuccessfulResponse(indexedResponse, indexedRequest, "variables");
            List<String> indexed =
                    objectsInArray(arrayField(indexedResponse, "variables"));
            assertEquals(2, indexed.size(), "Array must expose its exact indexed surface");
            Set<String> indexedValues =
                    indexed.stream()
                            .map(variable -> stringField(variable, "value"))
                            .collect(Collectors.toSet());
            assertEquals(Set.of("first", "7"), indexedValues);

            int nextRequest =
                    client.request("next", "{\"threadId\":" + threadId + "}");
            assertSuccessfulResponse(
                    client.awaitResponse(nextRequest, TIMEOUT), nextRequest, "next");

            String secondStop = client.awaitEvent("stopped", TIMEOUT);
            assertEquals(threadId, integerField(secondStop, "threadId"));
            assertTopFrame(
                    client,
                    threadId,
                    2,
                    sourceFile.getFileName().toString(),
                    loadedPath);

            int continueRequest =
                    client.request("continue", "{\"threadId\":" + threadId + "}");
            assertSuccessfulResponse(
                    client.awaitResponse(continueRequest, TIMEOUT),
                    continueRequest,
                    "continue");

            ProtosExecutionOutcome outcome = guestFuture.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());

            int disconnect =
                    client.request(
                            "disconnect",
                            "{\"restart\":false,\"terminateDebuggee\":false}");
            assertSuccessfulResponse(
                    client.awaitResponse(disconnect, TIMEOUT), disconnect, "disconnect");
            disconnected = true;
        } finally {
            if (client != null) {
                if (!disconnected) {
                    client.bestEffortDisconnect();
                }
                client.close();
            }
            if (guestFuture != null && !guestFuture.isDone()) {
                guestFuture.cancel(true);
            }
            if (context != null) {
                context.close();
            }
            guestExecutor.shutdownNow();
            assertTrue(
                    guestExecutor.awaitTermination(TIMEOUT.toSeconds(), TimeUnit.SECONDS),
                    "DAP behavior test executor must terminate");
            Files.deleteIfExists(sourceFile);
        }
    }

    private static void initializeAndAttach(DapClient client) throws IOException {
        int initialize =
                client.request(
                        "initialize",
                        "{\"adapterID\":\"protos\","
                                + "\"clientID\":\"protos-i026-f2\","
                                + "\"clientName\":\"Protos I026-F2\","
                                + "\"linesStartAt1\":true,"
                                + "\"columnsStartAt1\":true,"
                                + "\"pathFormat\":\"path\","
                                + "\"supportsVariableType\":true,"
                                + "\"supportsVariablePaging\":true}");
        String initializeResponse = client.awaitResponse(initialize, TIMEOUT);
        assertSuccessfulResponse(initializeResponse, initialize, "initialize");
        assertTrue(
                booleanField(initializeResponse, "supportsConfigurationDoneRequest"),
                "real GraalVM DAP must advertise configurationDone");

        String initialized = client.awaitEvent("initialized", TIMEOUT);
        assertEquals("initialized", stringField(initialized, "event"));

        int attach = client.request("attach", "{}");
        assertSuccessfulResponse(client.awaitResponse(attach, TIMEOUT), attach, "attach");
    }

    private static String loadedSource(DapClient client, String fileName) throws IOException {
        int request = client.request("loadedSources", "{}");
        String response = client.awaitResponse(request, TIMEOUT);
        assertSuccessfulResponse(response, request, "loadedSources");
        List<String> sources = objectsInArray(arrayField(response, "sources"));
        return sources.stream()
                .filter(source -> fileName.equals(optionalStringField(source, "name")))
                .findFirst()
                .orElseThrow(
                        () ->
                                new AssertionError(
                                        "parsed Protos source missing from loadedSources: "
                                                + response));
    }

    private static int assertTopFrame(
            DapClient client,
            int threadId,
            int expectedLine,
            String expectedSourceName,
            String expectedPath)
            throws IOException {
        int request =
                client.request("stackTrace", "{\"threadId\":" + threadId + "}");
        String response = client.awaitResponse(request, TIMEOUT);
        assertSuccessfulResponse(response, request, "stackTrace");

        List<String> frames = objectsInArray(arrayField(response, "stackFrames"));
        assertFalse(frames.isEmpty(), "DAP stackTrace must expose a Protos frame");
        String frame = frames.get(0);
        assertEquals(expectedLine, integerField(frame, "line"));

        String source = objectField(frame, "source");
        assertEquals(expectedSourceName, stringField(source, "name"));
        assertEquals(expectedPath, stringField(source, "path"));

        int frameId = integerField(frame, "id");
        assertTrue(frameId > 0, "DAP frame id must be usable by scopes");
        return frameId;
    }

    private static ProtosActivation activationWithRepresentativeValues() {
        ProtosObjectValue context = new ProtosObjectValue(ProtosObjectValue.rootObject());
        context.createLocalSlot("text", new ProtosStringValue("hello"));
        context.createLocalSlot("flag", ProtosBooleanValue.TRUE);
        context.createLocalSlot("number", new ProtosIntegerValue(BigInteger.valueOf(42)));
        context.createLocalSlot(
                "array",
                new ProtosArrayValue(
                        ProtosObjectValue.rootObject(),
                        List.of(
                                new ProtosStringValue("first"),
                                new ProtosIntegerValue(BigInteger.valueOf(7)))));
        return new ProtosActivation(context, List.of(), context);
    }

    private static void assertVariableValue(
            List<String> variables, String name, String expectedValue) {
        assertEquals(expectedValue, stringField(variableNamed(variables, name), "value"));
    }

    private static String variableNamed(List<String> variables, String name) {
        return variables.stream()
                .filter(variable -> name.equals(optionalStringField(variable, "name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("DAP variable missing: " + name));
    }

    private static int reserveEphemeralPort() throws IOException {
        try (ServerSocket socket =
                new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    private static void assertSuccessfulResponse(
            String message, int requestSequence, String command) {
        assertEquals("response", stringField(message, "type"));
        assertEquals(
                requestSequence,
                integerField(message, "request_seq"),
                "DAP response must correlate to request");
        assertEquals(command, stringField(message, "command"));
        assertTrue(
                booleanField(message, "success"),
                () -> "DAP " + command + " failed: " + message);
    }

    private static String arrayField(String json, String fieldName) {
        return compositeField(json, fieldName, '[', ']');
    }

    private static String objectField(String json, String fieldName) {
        return compositeField(json, fieldName, '{', '}');
    }

    private static String compositeField(
            String json, String fieldName, char open, char close) {
        Matcher matcher =
                Pattern.compile(
                                "\\\"" + Pattern.quote(fieldName) + "\\\"\\s*:")
                        .matcher(json);
        assertTrue(
                matcher.find(),
                () -> "Missing JSON field " + fieldName + ": " + json);

        int start = matcher.end();
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        assertTrue(
                start < json.length() && json.charAt(start) == open,
                () -> "JSON field " + fieldName + " has unexpected shape: " + json);
        int end = matchingCompositeEnd(json, start, open, close);
        return json.substring(start, end + 1);
    }

    private static List<String> objectsInArray(String arrayJson) {
        List<String> objects = new ArrayList<>();
        int index = 1;
        while (index < arrayJson.length() - 1) {
            char current = arrayJson.charAt(index);
            if (Character.isWhitespace(current) || current == ',') {
                index++;
                continue;
            }
            assertEquals(
                    '{',
                    current,
                    "DAP arrays inspected by this smoke must contain JSON objects");
            int end = matchingCompositeEnd(arrayJson, index, '{', '}');
            objects.add(arrayJson.substring(index, end + 1));
            index = end + 1;
        }
        return objects;
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

    private static String stringField(String json, String name) {
        String value = optionalStringField(json, name);
        assertNotNull(value, () -> "Missing JSON string field " + name + ": " + json);
        return value;
    }

    private static String optionalStringField(String json, String name) {
        Matcher matcher =
                Pattern.compile(
                                "\\\"" + Pattern.quote(name)
                                        + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"")
                        .matcher(json);
        return matcher.find() ? decodeJsonString(matcher.group(1)) : null;
    }

    private static int integerField(String json, String name) {
        int value = optionalIntegerField(json, name);
        assertTrue(
                value != Integer.MIN_VALUE,
                () -> "Missing JSON integer field " + name + ": " + json);
        return value;
    }

    private static int optionalIntegerField(String json, String name) {
        Matcher matcher =
                Pattern.compile(
                                "\\\"" + Pattern.quote(name) + "\\\"\\s*:\\s*(-?[0-9]+)")
                        .matcher(json);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : Integer.MIN_VALUE;
    }

    private static boolean booleanField(String json, String name) {
        Matcher matcher =
                Pattern.compile(
                                "\\\"" + Pattern.quote(name) + "\\\"\\s*:\\s*(true|false)")
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
