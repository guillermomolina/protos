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

import com.guillermomolina.protos.cli.ProtosCli;
import com.guillermomolina.protos.execution.ProtosDapTestSupport.DapClient;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BUG025 closure evidence: the original, unchanged {@code parallel-execution.protos} example runs
 * through the real {@code protos debug} CLI entry and a real GraalVM DAP session under each
 * exception filter. The valid {@code ^index} inside the real {@code std:collections/Array}
 * implementation must never surface as an exception stop. Unrelated stops are recorded with
 * their location and classification and are resumed; their handled-Error classification belongs
 * to PLAT071 and is not asserted here.
 */
final class ProtosBug025RealDapClosureTest {
    /** Bound for readiness, individual DAP requests and teardown. */
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    /** Bound for the complete guest program under the debugger. */
    private static final Duration RUN_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration STOP_POLL = Duration.ofMillis(200);
    private static final Duration DRAIN_POLL = Duration.ofMillis(1);
    private static final Path EXAMPLE =
            Path.of("protos", "examples", "concurrency", "parallel-execution.protos");
    private static final String EXPECTED_STDOUT =
            """
            42
            [9, 1, 16, 1, 25, 81, 4, 36]
            [4, 2, 6]
            2
            null
            31
            null
            [1, 1, 2, 3, 4, 5, 6, 9]
            """;
    private static final Pattern READINESS =
            Pattern.compile(
                    "^PROTOS_DEBUG_READY "
                            + "\\{\\\"version\\\":1,"
                            + "\\\"protocol\\\":\\\"dap\\\","
                            + "\\\"transport\\\":\\\"tcp\\\","
                            + "\\\"host\\\":\\\"([^\\\"]+)\\\","
                            + "\\\"port\\\":([0-9]+)\\}$");
    private static final Pattern INTEGER_FIELD_LINE =
            Pattern.compile("\\\"line\\\"\\s*:\\s*(-?[0-9]+)");
    private static final Pattern INTEGER_FIELD_COLUMN =
            Pattern.compile("\\\"column\\\"\\s*:\\s*(-?[0-9]+)");

    @Test
    void fullParallelExampleWithoutExceptionFilters() throws Exception {
        FullRun run = runFullExample("none", "[]");
        assertTrue(run.stops().isEmpty(), () -> "no filters but stops=" + run.stops());
    }

    @Test
    void fullParallelExampleUnderAllFilter() throws Exception {
        runFullExample("all", "[\"all\"]");
    }

    @Test
    void fullParallelExampleUnderUncaughtFilter() throws Exception {
        runFullExample("uncaught", "[\"uncaught\"]");
    }

    private record FullRun(int exitCode, String stdout, List<Stop> stops) {}

    /** One real DAP {@code stopped} event with its exceptionInfo and top semantic frame. */
    private record Stop(
            String filter,
            String reason,
            String description,
            int threadId,
            String exceptionId,
            String breakMode,
            String sourcePath,
            int line,
            int column,
            String lineContent,
            boolean validIndexReturn,
            String exceptionInfo) {
        boolean isException() {
            return "exception".equals(reason);
        }

        @Override
        public String toString() {
            return "{filter=" + filter
                    + ",reason=" + reason
                    + ",description=" + description
                    + ",threadId=" + threadId
                    + ",exceptionId=" + exceptionId
                    + ",breakMode=" + breakMode
                    + ",top=" + sourcePath + ":" + line + ":" + column
                    + ",lineContent=" + lineContent
                    + ",validIndexReturn=" + validIndexReturn
                    + ",exceptionInfo=" + exceptionInfo
                    + "}";
        }
    }

    /**
     * Runs the unchanged example through {@code protos debug} in a fresh CLI/DAP session
     * configured with {@code filtersJson}, asserts the program-level contract common to every
     * filter, prints the evidence summary and returns the observations.
     */
    private static FullRun runFullExample(String filterLabel, String filtersJson)
            throws Exception {
        Path source = EXAMPLE.toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(source), () -> "missing example " + source);

        LineQueueOutputStream controlBytes = new LineQueueOutputStream();
        ByteArrayOutputStream diagnosticBytes = new ByteArrayOutputStream();
        PrintStream controlOut = new PrintStream(controlBytes, true, StandardCharsets.UTF_8);
        PrintStream diagnostics = new PrintStream(diagnosticBytes, true, StandardCharsets.UTF_8);
        ExecutorService cliExecutor = Executors.newSingleThreadExecutor();
        Future<Integer> cli = null;
        DapClient client = null;
        boolean disconnected = false;
        Set<Thread> connectionThreadsBefore =
                ProtosI026FDapBehaviorTest.dapClientConnectionThreads();
        Thread connectionThread = null;
        List<Stop> stops = new ArrayList<>();
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        List<String> adapterMessages = new ArrayList<>();

        try {
            cli =
                    cliExecutor.submit(
                            () ->
                                    new ProtosCli()
                                            .run(
                                                    new String[] {"debug", source.toString()},
                                                    InputStream.nullInputStream(),
                                                    controlOut,
                                                    diagnostics));

            String readiness = controlBytes.lines.poll(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            Future<Integer> started = cli;
            assertNotNull(
                    readiness,
                    () -> "no PROTOS_DEBUG_READY within the bound; cliDone=" + started.isDone()
                            + " diagnostics=" + diagnosticBytes.toString(StandardCharsets.UTF_8));
            Matcher match = READINESS.matcher(readiness);
            assertTrue(match.matches(), () -> "unexpected readiness record: " + readiness);
            ProtosPolyglotRuntimeHost.DebugEndpoint endpoint =
                    new ProtosPolyglotRuntimeHost.DebugEndpoint(
                            match.group(1), Integer.parseInt(match.group(2)));

            client = DapClient.connect(endpoint, TIMEOUT);
            initializeAndLaunch(client);
            connectionThread =
                    ProtosI026FDapBehaviorTest.newDapClientConnectionThread(
                            connectionThreadsBefore);

            int exceptionBreakpoints =
                    client.request(
                            "setExceptionBreakpoints", "{\"filters\":" + filtersJson + "}");
            ProtosI026FDapBehaviorTest.assertSuccessfulResponse(
                    client.awaitResponse(exceptionBreakpoints, TIMEOUT),
                    exceptionBreakpoints,
                    "setExceptionBreakpoints");
            int configurationDone = client.request("configurationDone", "{}");
            ProtosI026FDapBehaviorTest.assertSuccessfulResponse(
                    client.awaitResponse(configurationDone, TIMEOUT),
                    configurationDone,
                    "configurationDone");

            // Resume every stop until the adapter reports the debuggee terminated. Non-stop
            // messages stay deferred in arrival order, so output events keep their order.
            long deadline = System.nanoTime() + RUN_TIMEOUT.toNanos();
            boolean terminated = false;
            while (true) {
                String stopped = client.pollEvent("stopped", STOP_POLL);
                if (stopped != null) {
                    stops.add(recordAndContinue(filterLabel, client, stopped));
                    continue;
                }
                if (client.pollEvent("terminated", DRAIN_POLL) != null) {
                    terminated = true;
                    break;
                }
                if (client.isEndOfStream()) {
                    break;
                }
                if (System.nanoTime() > deadline) {
                    throw new AssertionError(
                            "debuggee did not terminate within " + RUN_TIMEOUT
                                    + " under filter " + filterLabel
                                    + "; stops=" + stops
                                    + " diagnostics="
                                    + diagnosticBytes.toString(StandardCharsets.UTF_8));
                }
            }

            // The adapter may close the transport once the debuggee ends; that close is accepted
            // only after a real terminated event was received.
            assertTrue(
                    terminated,
                    () -> "DAP transport closed without a terminated event under filter "
                            + filterLabel + "; stops=" + stops + " diagnostics="
                            + diagnosticBytes.toString(StandardCharsets.UTF_8));

            String output;
            while ((output = client.pollEvent("output", DRAIN_POLL)) != null) {
                String body = ProtosI026FDapBehaviorTest.objectField(output, "body");
                String category = ProtosI026FDapBehaviorTest.optionalStringField(body, "category");
                String text = ProtosI026FDapBehaviorTest.optionalStringField(body, "output");
                if (text == null) {
                    continue;
                }
                if ("stdout".equals(category)) {
                    stdout.append(text);
                } else if ("stderr".equals(category)) {
                    if (isAdapterAttachMessage(text)) {
                        // GraalVM DAP's own launch() notice, not guest output: recorded apart.
                        adapterMessages.add(text);
                    } else {
                        stderr.append(text);
                    }
                }
            }

            // GraalVM's DAP listener sends terminated during disposal and then closes the
            // socket itself, so no disconnect request follows a received terminated event.
            String sessionEnd =
                    client.isEndOfStream()
                            ? "transport-closed-by-adapter-after-terminated"
                            : "client-closed-after-terminated";
            disconnected = true;
            client.close();
            client = null;

            int exitCode = cli.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            cliExecutor.shutdown();
            assertTrue(
                    cliExecutor.awaitTermination(TIMEOUT.toSeconds(), TimeUnit.SECONDS),
                    "protos debug CLI executor must terminate after normal termination");
            String diagnosticText = diagnosticBytes.toString(StandardCharsets.UTF_8);
            String failureContext =
                    "filter=" + filterLabel + " stops=" + stops + " stderr=" + stderr
                            + " adapterMessages=" + adapterMessages
                            + " diagnostics=" + diagnosticText;

            assertEquals(0, exitCode, () -> "nonzero exit; " + failureContext);
            assertEquals(EXPECTED_STDOUT, stdout.toString(), () -> failureContext);
            assertEquals("", stderr.toString(), () -> "guest stderr; " + failureContext);
            assertNull(
                    controlBytes.lines.poll(),
                    () -> "control stdout must carry exactly one readiness record");
            assertEquals("", controlBytes.pending(), "control stdout ended mid-line");
            for (String forbidden :
                    List.of("InvalidReturn", "ProtosSignalException", "Error:")) {
                assertFalse(
                        diagnosticText.contains(forbidden),
                        () -> forbidden + " in diagnostics; " + failureContext);
            }
            List<Stop> validReturnExceptionStops =
                    stops.stream().filter(Stop::validIndexReturn).toList();
            assertTrue(
                    validReturnExceptionStops.isEmpty(),
                    () -> "the valid ^index surfaced as a stop; " + failureContext);
            List<Stop> otherStops =
                    stops.stream().filter(stop -> !stop.validIndexReturn()).toList();

            System.out.println("BUG025_FULL_EXAMPLE_FILTER=" + filterLabel);
            System.out.println("BUG025_FULL_EXAMPLE_EXIT=" + exitCode);
            System.out.println("BUG025_FULL_EXAMPLE_STDOUT_MATCH=YES");
            System.out.println(
                    "BUG025_FULL_EXAMPLE_VALID_NLR_EXCEPTION_STOPS="
                            + validReturnExceptionStops.size());
            System.out.println("BUG025_FULL_EXAMPLE_OTHER_STOPS=" + otherStops.size());
            System.out.println("BUG025_FULL_EXAMPLE_SESSION_END=" + sessionEnd);
            System.out.println("BUG025_FULL_EXAMPLE_ADAPTER_STDERR=" + adapterMessages);
            if (!otherStops.isEmpty()) {
                System.out.println("BUG025_FULL_EXAMPLE_OTHER_STOP_DETAILS=" + otherStops);
            }
            return new FullRun(exitCode, stdout.toString(), stops);
        } finally {
            if (client != null) {
                if (!disconnected) {
                    client.bestEffortDisconnect();
                }
                client.close();
            }
            if (cli != null && !cli.isDone()) {
                cli.cancel(true);
            }
            cliExecutor.shutdownNow();
            assertTrue(
                    cliExecutor.awaitTermination(TIMEOUT.toSeconds(), TimeUnit.SECONDS),
                    "protos debug CLI execution must terminate");
            if (connectionThread != null) {
                ProtosI026FDapBehaviorTest.awaitDapClientConnectionThreadTermination(
                        connectionThread);
            }
        }
    }

    /**
     * Whether a DAP stderr output is exactly GraalVM DAP's own {@code Debugger attached.} notice
     * from {@code DebugProtocolServerImpl.launch()}, allowing only a trailing line terminator.
     */
    private static boolean isAdapterAttachMessage(String text) {
        String line =
                text.endsWith("\r\n")
                        ? text.substring(0, text.length() - 2)
                        : text.endsWith("\n") ? text.substring(0, text.length() - 1) : text;
        return line.equals("Debugger attached.");
    }

    private static void initializeAndLaunch(DapClient client) throws IOException {
        int initialize =
                client.request(
                        "initialize",
                        "{\"adapterID\":\"protos\","
                                + "\"clientID\":\"protos-bug025\","
                                + "\"clientName\":\"Protos BUG025\","
                                + "\"linesStartAt1\":true,"
                                + "\"columnsStartAt1\":true,"
                                + "\"pathFormat\":\"path\"}");
        ProtosI026FDapBehaviorTest.assertSuccessfulResponse(
                client.awaitResponse(initialize, TIMEOUT), initialize, "initialize");
        client.awaitEvent("initialized", TIMEOUT);
        int launch = client.request("launch", "{}");
        ProtosI026FDapBehaviorTest.assertSuccessfulResponse(
                client.awaitResponse(launch, TIMEOUT), launch, "launch");
    }

    /**
     * Records one real stop with its complete exceptionInfo response and the top frame that has
     * a source, classifies it against the debugger-reported source line, and resumes it.
     */
    private static Stop recordAndContinue(String filter, DapClient client, String stopped)
            throws IOException {
        String body = ProtosI026FDapBehaviorTest.objectField(stopped, "body");
        int threadId = ProtosI026FDapBehaviorTest.integerField(body, "threadId");
        String reason = ProtosI026FDapBehaviorTest.optionalStringField(body, "reason");
        String description = ProtosI026FDapBehaviorTest.optionalStringField(body, "description");

        int info = client.request("exceptionInfo", "{\"threadId\":" + threadId + "}");
        String exceptionInfo = client.awaitResponse(info, TIMEOUT);
        String exceptionId =
                ProtosI026FDapBehaviorTest.optionalStringField(exceptionInfo, "exceptionId");
        String breakMode =
                ProtosI026FDapBehaviorTest.optionalStringField(exceptionInfo, "breakMode");

        int frames = client.request("stackTrace", "{\"threadId\":" + threadId + "}");
        String stackTrace = client.awaitResponse(frames, TIMEOUT);
        String sourcePath = null;
        int line = -1;
        int column = -1;
        if (stackTrace.contains("\"stackFrames\"")) {
            for (String frame :
                    ProtosI026FDapBehaviorTest.objectsInArray(
                            ProtosI026FDapBehaviorTest.arrayField(stackTrace, "stackFrames"))) {
                if (!frame.contains("\"source\"")) {
                    continue;
                }
                String frameSource = ProtosI026FDapBehaviorTest.objectField(frame, "source");
                sourcePath = ProtosI026FDapBehaviorTest.optionalStringField(frameSource, "path");
                line = optionalInteger(INTEGER_FIELD_LINE, frame);
                column = optionalInteger(INTEGER_FIELD_COLUMN, frame);
                break;
            }
        }
        String lineContent = sourceLine(sourcePath, line);
        boolean validIndexReturn =
                sourcePath != null
                        && Path.of(sourcePath).getFileName().toString().equals("Array.protos")
                        && lineContent != null
                        && lineContent.strip().equals("^index");

        int continueRequest = client.request("continue", "{\"threadId\":" + threadId + "}");
        ProtosI026FDapBehaviorTest.assertSuccessfulResponse(
                client.awaitResponse(continueRequest, TIMEOUT), continueRequest, "continue");
        return new Stop(
                filter, reason, description, threadId, exceptionId, breakMode, sourcePath, line,
                column, lineContent, validIndexReturn, exceptionInfo);
    }

    /** The debugger-reported source line, read from the reported file, or {@code null}. */
    private static String sourceLine(String sourcePath, int line) throws IOException {
        if (sourcePath == null || line < 1) {
            return null;
        }
        Path path = Path.of(sourcePath);
        if (!Files.isRegularFile(path)) {
            return null;
        }
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        return line <= lines.size() ? lines.get(line - 1) : null;
    }

    private static int optionalInteger(Pattern pattern, String json) {
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }

    /**
     * Control-stdout sink: complete lines are handed to a bounded-wait consumer through a
     * thread-safe queue, so the test synchronizes on the readiness record without sleeping.
     */
    private static final class LineQueueOutputStream extends OutputStream {
        final BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        private final ByteArrayOutputStream partial = new ByteArrayOutputStream();

        @Override
        public synchronized void write(int value) {
            if (value == '\n') {
                String line = partial.toString(StandardCharsets.UTF_8);
                partial.reset();
                lines.add(line.endsWith("\r") ? line.substring(0, line.length() - 1) : line);
            } else {
                partial.write(value);
            }
        }

        @Override
        public synchronized void write(byte[] bytes, int offset, int length) {
            for (int index = offset; index < offset + length; index++) {
                write(bytes[index]);
            }
        }

        synchronized String pending() {
            return partial.toString(StandardCharsets.UTF_8);
        }
    }
}
