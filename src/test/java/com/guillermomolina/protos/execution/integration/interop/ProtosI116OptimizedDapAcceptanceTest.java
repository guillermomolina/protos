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

import static com.guillermomolina.protos.execution.ProtosI026FDapBehaviorTest.*;
import static org.junit.jupiter.api.Assertions.*;

import com.guillermomolina.protos.execution.ProtosDapTestSupport.DapClient;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.source.Source;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.graalvm.polyglot.Engine;
import org.junit.jupiter.api.Test;

/**
 * I116-B: real Graal DAP acceptance for proof-optimized Bytecode DSL
 * Closure bodies. The existing DAP TCP client and JSON assertions are
 * reused; no alternate transport or guest execution mode is introduced.
 */
final class ProtosI116OptimizedDapAcceptanceTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(8);

    @Test
    void optimizedConstantReturnRemainsBreakpointVisibleOverRealDap()
            throws Exception {
        assertOptimizedBodyOverRealDap(
                "run: () => {\n"
                        + "    1\n"
                        + "}\n"
                        + "run()\n",
                new int[] {2},
                4,
                "1",
                null);
    }

    @Test
    void optimizedScalarLocalsRemainVisibleOverRealDap()
            throws Exception {
        assertOptimizedBodyOverRealDap(
                "run: () => {\n"
                        + "    first: 7\n"
                        + "    second: first\n"
                        + "    second\n"
                        + "}\n"
                        + "run()\n",
                new int[] {2, 3, 4},
                6,
                "7",
                new String[][] {
                    {null, null},
                    {"7", null},
                    {"7", "7"}
                });
    }

    /**
     * Uses one stackTrace response for each stop because a DAP frame
     * identifier is not a stable identity across separate requests.
     */
    private static void assertOptimizedBodyOverRealDap(
            String sourceText,
            int[] lines,
            int callerLine,
            String expectedResult,
            String[][] expectedBindings)
            throws Exception {

        if (expectedBindings != null) {
            assertEquals(lines.length, expectedBindings.length);
        }

        Path file = Files.createTempFile("protos-i116-dap-", ".protos");
        Files.writeString(file, sourceText, StandardCharsets.UTF_8);

        ProtosGraalDapReadinessAdapter readiness =
                ProtosDapTestSupport.newReadiness();

        ExecutorService executor = Executors.newSingleThreadExecutor();

        Future<ProtosExecutionOutcome> future = null;
        Engine engine = null;
        ProtosPolyglotExecutionContext context = null;
        DapClient client = null;
        Thread connectionThread = null;
        boolean disconnected = false;

        Set<Thread> threadsBefore = dapClientConnectionThreads();

        try {
            engine = Engine.newBuilder(ProtosLanguage.ID)
                    .option(
                            "dap",
                            ProtosDapTestSupport.EPHEMERAL_LOOPBACK)
                    .option("dap.Suspend", "false")
                    .option("dap.WaitAttached", "false")
                    .out(readiness)
                    .build();

            context = ProtosPolyglotExecutionContext.open(
                    engine,
                    InputStream.nullInputStream(),
                    OutputStream.nullOutputStream(),
                    OutputStream.nullOutputStream(),
                    () -> {});

            client = DapClient.connect(readiness, TIMEOUT);
            initializeAndAttach(client);

            connectionThread =
                    newDapClientConnectionThread(threadsBefore);

            final Source[] parsed = new Source[1];

            CallTarget target = context.callEntered(() -> {
                ProtosLanguageContext language =
                        ProtosLanguageContext.current();

                Source source =
                        language.materializeFileSource(file, sourceText);

                parsed[0] = source;
                return language.parsePublic(source);
            });

            Path exactPath = file.toAbsolutePath().normalize();

            assertEquals(exactPath.toString(), parsed[0].getPath());
            assertEquals(exactPath.toUri(), parsed[0].getURI());

            String name = file.getFileName().toString();

            String loaded = loadedSource(client, name);
            String loadedPath = stringField(loaded, "path");

            assertEquals(exactPath.toString(), loadedPath);

            String dapSource =
                    "{\"name\":" + jsonString(name)
                            + ",\"path\":" + jsonString(loadedPath)
                            + "}";

            String lineNumbers = Arrays.stream(lines)
                    .mapToObj(Integer::toString)
                    .collect(Collectors.joining(",", "[", "]"));

            String requestedBreakpoints = Arrays.stream(lines)
                    .mapToObj(line -> "{\"line\":" + line + "}")
                    .collect(Collectors.joining(",", "[", "]"));

            int request = client.request(
                    "setBreakpoints",
                    "{\"source\":" + dapSource
                            + ",\"lines\":" + lineNumbers
                            + ",\"breakpoints\":" + requestedBreakpoints
                            + ",\"sourceModified\":false}");

            String response = client.awaitResponse(request, TIMEOUT);

            assertSuccessfulResponse(
                    response, request, "setBreakpoints");

            List<String> breakpoints = objectsInArray(
                    arrayField(response, "breakpoints"));

            assertEquals(lines.length, breakpoints.size());

            Map<Integer, Integer> unresolved = new HashMap<>();
            Set<Integer> identifiers = new HashSet<>();

            for (int index = 0; index < lines.length; index++) {
                String breakpoint = breakpoints.get(index);

                assertEquals(
                        lines[index],
                        integerField(breakpoint, "line"));

                int id = integerField(breakpoint, "id");

                assertTrue(
                        identifiers.add(id),
                        "DAP breakpoint identifiers must be distinct");

                if (!booleanField(breakpoint, "verified")) {
                    unresolved.put(id, lines[index]);
                }
            }

            request = client.request("configurationDone", "{}");

            assertSuccessfulResponse(
                    client.awaitResponse(request, TIMEOUT),
                    request,
                    "configurationDone");

            // Closure invocation needs its owning Core Prelude.
            ProtosActivation activation = new ProtosCoreBootstrap()
                    .bootstrap(Path.of("protos", "lib", "core"))
                    .newModuleActivation();

            ProtosPolyglotExecutionContext guestContext = context;

            future = executor.submit(
                    () -> guestContext.callEntered(
                            () -> ProtosRootTaskExecution.execute(
                                    target, activation)));

            while (!unresolved.isEmpty()) {
                String event = client.awaitEvent("breakpoint", TIMEOUT);

                assertEquals(
                        "changed",
                        stringField(event, "reason"));

                String resolved = objectField(
                        objectField(event, "body"), "breakpoint");

                int id = integerField(resolved, "id");
                Integer expectedLine = unresolved.remove(id);

                assertNotNull(
                        expectedLine,
                        "Unexpected DAP breakpoint event: " + event);

                assertTrue(
                        booleanField(resolved, "verified"),
                        "Optimized breakpoint did not resolve: " + event);

                assertEquals(
                        expectedLine.intValue(),
                        integerField(resolved, "line"));
            }

            int observedThread = -1;

            for (int index = 0; index < lines.length; index++) {
                String stopped = client.awaitEvent("stopped", TIMEOUT);

                assertEquals(
                        "breakpoint",
                        stringField(stopped, "reason"));

                int threadId = integerField(stopped, "threadId");

                if (observedThread < 0) {
                    observedThread = threadId;
                } else {
                    assertEquals(observedThread, threadId);
                }

                request = client.request(
                        "stackTrace",
                        "{\"threadId\":" + threadId + "}");

                response = client.awaitResponse(request, TIMEOUT);

                assertSuccessfulResponse(
                        response, request, "stackTrace");

                List<String> frames = objectsInArray(
                        arrayField(response, "stackFrames"));

                assertEquals(
                        2,
                        frames.size(),
                        "Only the real Closure and caller frames may exist");

                String top = frames.get(0);
                String caller = frames.get(1);

                assertEquals(
                        lines[index],
                        integerField(top, "line"));

                assertEquals(
                        name,
                        stringField(
                                objectField(top, "source"), "name"));

                assertEquals(
                        loadedPath,
                        stringField(
                                objectField(top, "source"), "path"));

                assertEquals(
                        callerLine,
                        integerField(caller, "line"));

                assertEquals(
                        loadedPath,
                        stringField(
                                objectField(caller, "source"), "path"));

                int frameId = integerField(top, "id");
                assertTrue(frameId > 0);

                request = client.request(
                        "scopes",
                        "{\"frameId\":" + frameId + "}");

                response = client.awaitResponse(request, TIMEOUT);

                assertSuccessfulResponse(
                        response, request, "scopes");

                List<String> scopes = objectsInArray(
                        arrayField(response, "scopes"));

                assertEquals(1, scopes.size());
                assertFalse(booleanField(scopes.get(0), "expensive"));

                int reference = integerField(
                        scopes.get(0), "variablesReference");

                assertTrue(reference > 0);

                request = client.request(
                        "variables",
                        "{\"variablesReference\":" + reference + "}");

                response = client.awaitResponse(request, TIMEOUT);

                assertSuccessfulResponse(
                        response, request, "variables");

                List<String> variables = objectsInArray(
                        arrayField(response, "variables"));

                Set<String> names = variables.stream()
                        .map(variable ->
                                optionalStringField(variable, "name"))
                        .filter(nameValue -> nameValue != null)
                        .collect(Collectors.toSet());

                assertFalse(names.contains("this"));
                assertFalse(names.contains("self"));

                if (expectedBindings != null) {
                    String[] expected = expectedBindings[index];
                    String[] localNames = {"first", "second"};

                    assertEquals(localNames.length, expected.length);

                    for (int local = 0; local < localNames.length; local++) {
                        if (expected[local] == null) {
                            assertFalse(
                                    names.contains(localNames[local]),
                                    "Premature local on line "
                                            + lines[index] + ": "
                                            + localNames[local]);
                        } else {
                            assertVariableValue(
                                    variables,
                                    localNames[local],
                                    expected[local]);
                        }
                    }
                }

                request = client.request(
                        "continue",
                        "{\"threadId\":" + threadId + "}");

                assertSuccessfulResponse(
                        client.awaitResponse(request, TIMEOUT),
                        request,
                        "continue");
            }

            ProtosExecutionOutcome outcome =
                    future.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);

            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED,
                    outcome.state());

            assertEquals(
                    expectedResult,
                    ProtosNumericValueSupport.integerDecimalText(
                            outcome.value()));

            request = client.request(
                    "disconnect",
                    "{\"restart\":false,\"terminateDebuggee\":false}");

            assertSuccessfulResponse(
                    client.awaitResponse(request, TIMEOUT),
                    request,
                    "disconnect");

            disconnected = true;
        } finally {
            if (client != null) {
                if (!disconnected) {
                    client.bestEffortDisconnect();
                }
                client.close();
            }

            if (future != null && !future.isDone()) {
                future.cancel(true);
            }

            if (connectionThread != null) {
                awaitDapClientConnectionThreadTermination(
                        connectionThread);
            }

            if (context != null) {
                context.close();
            }

            if (engine != null) {
                engine.close();
            }

            executor.shutdownNow();

            assertTrue(
                    executor.awaitTermination(
                            TIMEOUT.toSeconds(),
                            TimeUnit.SECONDS));

            Files.deleteIfExists(file);
        }
    }
}
