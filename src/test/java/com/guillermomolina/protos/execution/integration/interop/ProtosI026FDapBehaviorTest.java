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

import com.guillermomolina.protos.execution.ProtosDapTestSupport.DapClient;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.source.Source;
import org.graalvm.polyglot.Engine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProtosI026FDapBehaviorTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    private static final String DAP_CLIENT_CONNECTION_THREAD_NAME =
            "DAP client connection thread";
    private static final String SOURCE_TEXT = "text\nnumber\nflag\n";

    @Test
    void realDapBreakpointScopesValuesAndNextStayFaithfulToProtos() throws Exception {
        Path sourceFile = Files.createTempFile("protos-i026-f2-", ".protos");
        Files.writeString(sourceFile, SOURCE_TEXT, StandardCharsets.UTF_8);

        ProtosGraalDapReadinessAdapter readiness = ProtosDapTestSupport.newReadiness();
        ExecutorService guestExecutor = Executors.newSingleThreadExecutor();
        Future<ProtosExecutionOutcome> guestFuture = null;
        Engine engine = null;
        ProtosPolyglotExecutionContext context = null;
        DapClient client = null;
        boolean disconnected = false;
        Set<Thread> dapClientConnectionThreadsBefore = dapClientConnectionThreads();
        Thread dapClientConnectionThread = null;

        try {
            engine =
                    Engine.newBuilder(ProtosLanguage.ID)
                            .option("dap", ProtosDapTestSupport.EPHEMERAL_LOOPBACK)
                            .option("dap.Suspend", "false")
                            .option("dap.WaitAttached", "false")
                            .out(readiness)
                            .build();
            context =
                    ProtosPolyglotExecutionContext.open(
                            engine,
                            java.io.InputStream.nullInputStream(),
                            java.io.OutputStream.nullOutputStream(),
                            java.io.OutputStream.nullOutputStream(),
                            () -> {});

            client = DapClient.connect(readiness, TIMEOUT);
            initializeAndAttach(client);
            dapClientConnectionThread =
                    newDapClientConnectionThread(dapClientConnectionThreadsBefore);

            final Source[] sourceHolder = new Source[1];
            final CallTarget target =
                    context.callEntered(
                            () -> {
                                ProtosLanguageContext languageContext =
                                        ProtosLanguageContext.current();
                                Source source =
                                        languageContext.materializeFileSource(
                                                sourceFile, SOURCE_TEXT);
                                sourceHolder[0] = source;
                                return languageContext.parsePublic(source);
                            });
            Source source = sourceHolder[0];

            Path exactSourcePath = sourceFile.toAbsolutePath().normalize();
            assertEquals(exactSourcePath.toString(), source.getPath());
            assertEquals(exactSourcePath.toUri(), source.getURI());

            String loadedSource = loadedSource(client, sourceFile.getFileName().toString());
            String loadedPath = stringField(loadedSource, "path");
            assertEquals(
                    exactSourcePath.toString(),
                    loadedPath,
                    "readable physical Protos source must keep the selected D065 path");
            assertEquals(
                    Integer.MIN_VALUE,
                    optionalIntegerField(loadedSource, "sourceReference"),
                    "readable physical source must not require a virtual DAP sourceReference");

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
            ProtosPolyglotExecutionContext guestContext = context;
            guestFuture =
                    guestExecutor.submit(
                            () ->
                                    guestContext.callEntered(
                                            () ->
                                                    ProtosRootTaskExecution.execute(
                                                            target, activation)));

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
            if (engine != null) {
                engine.close();
            }
            // GraalVM 25.5 retains its DAP listener for reconnects.
            // Engine disposal must precede the thread-leak assertion.
            if (dapClientConnectionThread != null) {
                awaitDapClientConnectionThreadTermination(dapClientConnectionThread);
            }
            guestExecutor.shutdownNow();
            assertTrue(
                    guestExecutor.awaitTermination(TIMEOUT.toSeconds(), TimeUnit.SECONDS),
                    "DAP behavior test executor must terminate");
            Files.deleteIfExists(sourceFile);
        }
    }

    /*
     * PLAT070 / BUG025, two separate claims with the real DAP adapter and the "uncaught" filter:
     *
     * 1. Observed result: with this adapter, a valid non-local return produced no "stopped"
     *    event and the program completed (strict on that observation). This is evidence for
     *    BUG025, not a contractual guarantee of the "uncaught" filter, and it does not by itself
     *    resolve BUG025.
     * 2. Guest Error and InvalidReturn Task failures: the program outcome is asserted exactly,
     *    while the DAP events are only recorded. Which events the adapter reports for a failing
     *    Task is the adapter's guarantee, not an assumption of this test, and a Task failure is
     *    not treated as equivalent to an "uncaught" exception event.
     *
     * The "all" filter is deliberately not exercised.
     */
    /**
     * The BUG025 findIndex program without print: this bare debugger harness binds no Process
     * standard streams, so print is exercised through the real CLI instead
     * ({@code ProtosCli008C1GuestStackCliTest}).
     */
    private static final String FIND_INDEX_VALUE_PROGRAM =
            """
            Arrays: import("std:collections/Array")

            Arrays.findIndex([1, 2, 3], (value) => {
                value == 2
            })
            """;
    private static final String UNHANDLED_ERROR_PROGRAM = "Error().signal()\n";
    private static final String INVALID_RETURN_TASK_PROGRAM =
            """
            m: () => {
                escape: () => { ^1 }
                child: (() => {
                    escape()
                }).future()
                Error.handle(() => { child.value() }, (caught) => { caught })
            }
            m()
            """;

    @Test
    void realDapObservedNoUncaughtStopForAValidNonLocalReturn() throws Exception {
        withExceptionFilteredSession(
                (client, context, executor, files, out) -> {
                    ExceptionRun value =
                            runUnderExceptionFilter(
                                    client, context, executor, files, FIND_INDEX_VALUE_PROGRAM,
                                    true);
                    assertEquals(
                            ProtosExecutionOutcome.State.COMPLETED,
                            value.outcome().state(),
                            () -> describe(value.outcome(), value.prelude()));
                    assertEquals(
                            "1",
                            com.guillermomolina.protos.runtime.ProtosNumericValueSupport
                                    .integerDecimalText(value.outcome().value()));

                    assertTrue(value.stops().isEmpty(), () -> "unexpected stops " + value.stops());
                });
        System.out.println("PLAT070_DAP_VALID_NLR_OBSERVED_UNCAUGHT_STOP=NO");
    }

    @Test
    void realDapTaskFailuresKeepTheirExactOutcomeWhateverTheAdapterReports() throws Exception {
        withExceptionFilteredSession(
                (client, context, executor, files, out) -> {
                    ExceptionRun unhandled =
                            runUnderExceptionFilter(
                                    client, context, executor, files, UNHANDLED_ERROR_PROGRAM,
                                    false);
                    assertEquals(ProtosExecutionOutcome.State.FAILED, unhandled.outcome().state());
                    System.out.println(
                            "PLAT070_DAP_UNHANDLED_ERROR_OBSERVED_STOPS=" + unhandled.stops());

                    ExceptionRun invalidReturn =
                            runUnderExceptionFilter(
                                    client, context, executor, files,
                                    INVALID_RETURN_TASK_PROGRAM, false);
                    assertEquals(
                            ProtosExecutionOutcome.State.COMPLETED,
                            invalidReturn.outcome().state(),
                            () -> "error=" + invalidReturn.outcome().error());
                    ProtosObjectValue failure =
                            (ProtosObjectValue) invalidReturn.outcome().value();
                    assertSame(
                            invalidReturn.prelude().invalidReturnPrototype(),
                            failure.parent().orElseThrow(),
                            "the producer Task fails with the standard InvalidReturn");
                    System.out.println(
                            "PLAT070_DAP_INVALID_RETURN_TASK_OBSERVED_STOPS="
                                    + invalidReturn.stops());
                });
    }

    @FunctionalInterface
    private interface SessionBody {
        void run(
                DapClient client,
                ProtosPolyglotExecutionContext context,
                ExecutorService executor,
                List<Path> files,
                java.io.ByteArrayOutputStream out)
                throws Exception;
    }

    private static void withExceptionFilteredSession(SessionBody body) throws Exception {
        ProtosGraalDapReadinessAdapter readiness = ProtosDapTestSupport.newReadiness();
        ExecutorService guestExecutor = Executors.newSingleThreadExecutor();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        List<Path> files = new ArrayList<>();
        Engine engine = null;
        ProtosPolyglotExecutionContext context = null;
        DapClient client = null;
        boolean disconnected = false;
        Set<Thread> dapClientConnectionThreadsBefore = dapClientConnectionThreads();
        Thread dapClientConnectionThread = null;

        try {
            engine =
                    Engine.newBuilder(ProtosLanguage.ID)
                            .option("dap", ProtosDapTestSupport.EPHEMERAL_LOOPBACK)
                            .option("dap.Suspend", "false")
                            .option("dap.WaitAttached", "false")
                            .out(readiness)
                            .build();
            context =
                    ProtosPolyglotExecutionContext.open(
                            engine,
                            java.io.InputStream.nullInputStream(),
                            out,
                            java.io.OutputStream.nullOutputStream(),
                            () -> {});

            client = DapClient.connect(readiness, TIMEOUT);
            initializeAndAttach(client);
            dapClientConnectionThread =
                    newDapClientConnectionThread(dapClientConnectionThreadsBefore);

            int exceptionBreakpoints =
                    client.request("setExceptionBreakpoints", "{\"filters\":[\"uncaught\"]}");
            assertSuccessfulResponse(
                    client.awaitResponse(exceptionBreakpoints, TIMEOUT),
                    exceptionBreakpoints,
                    "setExceptionBreakpoints");
            int configurationDone = client.request("configurationDone", "{}");
            assertSuccessfulResponse(
                    client.awaitResponse(configurationDone, TIMEOUT),
                    configurationDone,
                    "configurationDone");

            body.run(client, context, guestExecutor, files, out);

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
            if (context != null) {
                context.close();
            }
            if (engine != null) {
                engine.close();
            }
            if (dapClientConnectionThread != null) {
                awaitDapClientConnectionThreadTermination(dapClientConnectionThread);
            }
            guestExecutor.shutdownNow();
            assertTrue(
                    guestExecutor.awaitTermination(TIMEOUT.toSeconds(), TimeUnit.SECONDS),
                    "DAP exception-classification executor must terminate");
            for (Path file : files) {
                Files.deleteIfExists(file);
            }
        }
    }

    private record ExceptionRun(
            ProtosExecutionOutcome outcome,
            com.guillermomolina.protos.runtime.ProtosPrelude prelude,
            List<String> stops) {}

    /**
     * Runs {@code program} once under the configured "uncaught" filter. Every real {@code
     * stopped} event is recorded together with the adapter's {@code exceptionInfo} answer and
     * then continued. With {@code noStopAllowed} the program must finish without any stop.
     */
    private static ExceptionRun runUnderExceptionFilter(
            DapClient client,
            ProtosPolyglotExecutionContext context,
            ExecutorService guestExecutor,
            List<Path> files,
            String program,
            boolean noStopAllowed)
            throws Exception {
        Path file = Files.createTempFile("protos-plat070-dap-", ".protos");
        files.add(file);
        Files.writeString(file, program, StandardCharsets.UTF_8);
        CallTarget target =
                context.callEntered(
                        () -> {
                            ProtosLanguageContext languageContext =
                                    ProtosLanguageContext.current();
                            return languageContext.parsePublic(
                                    languageContext.materializeFileSource(file, program));
                        });
        com.guillermomolina.protos.runtime.ProtosPrelude prelude =
                new ProtosCoreBootstrap()
                        .bootstrap(
                                Path.of("protos", "lib", "core"),
                                // The findIndex program imports std:collections/Array.
                                new ProtosStandardLibraryModuleResolver(
                                        Path.of("protos", "lib")));
        ProtosActivation activation = prelude.newModuleActivation();
        Future<ProtosExecutionOutcome> guest =
                guestExecutor.submit(
                        () ->
                                context.callEntered(
                                        () -> ProtosRootTaskExecution.execute(target, activation)));

        List<String> stops = new ArrayList<>();
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (!guest.isDone() && System.nanoTime() < deadline) {
            String stopped = client.pollEvent("stopped", Duration.ofMillis(200));
            if (stopped == null) {
                continue;
            }
            int threadId = integerField(stopped, "threadId");
            int info = client.request("exceptionInfo", "{\"threadId\":" + threadId + "}");
            String evidence = stopped + " exceptionInfo=" + client.awaitResponse(info, TIMEOUT);
            if (noStopAllowed) {
                int frames = client.request("stackTrace", "{\"threadId\":" + threadId + "}");
                evidence += " stackTrace=" + client.awaitResponse(frames, TIMEOUT);
            }
            stops.add(evidence);
            int continueRequest = client.request("continue", "{\"threadId\":" + threadId + "}");
            assertSuccessfulResponse(
                    client.awaitResponse(continueRequest, TIMEOUT), continueRequest, "continue");
        }
        ProtosExecutionOutcome outcome = guest.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        String late = client.pollEvent("stopped", Duration.ofMillis(300));
        if (late != null) {
            stops.add(late);
        }
        if (noStopAllowed && !stops.isEmpty()) {
            throw new AssertionError(
                    "valid program suspended under the uncaught filter; "
                            + describe(outcome, prelude)
                            + " stops="
                            + stops);
        }
        return new ExceptionRun(outcome, prelude, stops);
    }

    /** Test evidence: the outcome state, the failing Error's identity and its guest trace. */
    private static String describe(
            ProtosExecutionOutcome outcome,
            com.guillermomolina.protos.runtime.ProtosPrelude prelude) {
        StringBuilder text = new StringBuilder("outcome=").append(outcome.state());
        if (outcome.state() == ProtosExecutionOutcome.State.FAILED) {
            Object parent = outcome.error().parent().orElse(null);
            String kind =
                    parent == prelude.invalidReturnPrototype()
                            ? "InvalidReturn"
                            : parent == prelude.errorPrototype() ? "Error" : "Error subtype";
            text.append(" error=").append(kind);
            outcome.failureDiagnosticTrace()
                    .ifPresent(
                            trace ->
                                    text.append(" trace=")
                                            .append(
                                                    trace.frames().stream()
                                                            .map(frame -> frame.line() + ":"
                                                                    + frame.column())
                                                            .toList()));
        }
        return text.toString();
    }

    static Set<Thread> dapClientConnectionThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(Thread::isAlive)
                .filter(thread -> DAP_CLIENT_CONNECTION_THREAD_NAME.equals(thread.getName()))
                .collect(Collectors.toSet());
    }

    static Thread newDapClientConnectionThread(Set<Thread> threadsBefore) {
        List<Thread> candidates =
                dapClientConnectionThreads().stream()
                        .filter(thread -> !threadsBefore.contains(thread))
                        .collect(Collectors.toList());
        assertEquals(
                1,
                candidates.size(),
                "exactly one new GraalVM DAP client connection thread must belong to this test");
        return candidates.get(0);
    }

    static void awaitDapClientConnectionThreadTermination(Thread thread)
            throws InterruptedException {
        thread.join(TIMEOUT.toMillis());
        assertFalse(
                thread.isAlive(),
                "GraalVM DAP client connection thread must terminate after Engine.close()");
    }

    static void initializeAndAttach(DapClient client) throws IOException {
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

    static String loadedSource(DapClient client, String fileName) throws IOException {
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
        context.createLocalSlot("number", new ProtosIntegerValue(42));
        context.createLocalSlot(
                "array",
                new ProtosArrayValue(
                        ProtosObjectValue.rootObject(),
                        List.of(
                                new ProtosStringValue("first"),
                                new ProtosIntegerValue(7))));
        return new ProtosActivation(context, List.of(), context);
    }

    static void assertVariableValue(
            List<String> variables, String name, String expectedValue) {
        assertEquals(expectedValue, stringField(variableNamed(variables, name), "value"));
    }

    private static String variableNamed(List<String> variables, String name) {
        return variables.stream()
                .filter(variable -> name.equals(optionalStringField(variable, "name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("DAP variable missing: " + name));
    }

    static void assertSuccessfulResponse(
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

    static String arrayField(String json, String fieldName) {
        return compositeField(json, fieldName, '[', ']');
    }

    static String objectField(String json, String fieldName) {
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

    static List<String> objectsInArray(String arrayJson) {
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

    static String stringField(String json, String name) {
        String value = optionalStringField(json, name);
        assertNotNull(value, () -> "Missing JSON string field " + name + ": " + json);
        return value;
    }

    static String optionalStringField(String json, String name) {
        Matcher matcher =
                Pattern.compile(
                                "\\\"" + Pattern.quote(name)
                                        + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"")
                        .matcher(json);
        return matcher.find() ? decodeJsonString(matcher.group(1)) : null;
    }

    static int integerField(String json, String name) {
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

    static boolean booleanField(String json, String name) {
        Matcher matcher =
                Pattern.compile(
                                "\\\"" + Pattern.quote(name) + "\\\"\\s*:\\s*(true|false)")
                        .matcher(json);
        assertTrue(
                matcher.find(),
                () -> "Missing JSON boolean field " + name + ": " + json);
        return Boolean.parseBoolean(matcher.group(1));
    }

    static String jsonString(String value) {
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
}
