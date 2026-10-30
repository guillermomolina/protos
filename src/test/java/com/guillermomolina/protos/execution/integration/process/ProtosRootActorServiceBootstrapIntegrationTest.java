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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.guillermomolina.protos.execution.ProtosStandaloneHostedExecution.ResolverGrant;
import com.guillermomolina.protos.execution.ProtosWorkspacePackageApplicationExecution.NetworkGrant;
import com.guillermomolina.protos.runtime.ProtosActor;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * D225-B1 (ACTORS.md §32): the explicitly opted-in RootActor message-service bootstrap over the
 * real hosted Process, Actor scheduler and Group routing. The service and its client are ordinary
 * Protos source; the only observation channel is the Process standard output written by the
 * RootActor itself. Every wait is bounded.
 */
@Timeout(value = 10, unit = TimeUnit.SECONDS)
final class ProtosRootActorServiceBootstrapIntegrationTest {
    static final Path CORE = Path.of("protos", "lib", "core");
    static final long SAFETY_MILLIS = TimeUnit.SECONDS.toMillis(5);

    /**
     * The RootActor service: its module body runs once, its bootstrap creates a single-member
     * GroupRef to itself and hands it to a client Actor, and its behavior owns mutable state.
     */
    private static final String SERVICE =
            """
            ProcessStreams: import("std:io/ProcessStreams")
            bodyRuns: 0
            bodyRuns = bodyRuns + 1
            out: ProcessStreams.stdoutWriter(process)
            rootContext: context
            count: 0
            service: () => {
                self: Actor.group(Actor.current())
                client: Actor.spawn("./client.protos", "start")
                client.send("run", self)
                behavior: {
                    increment: () => {
                        count = count + 1
                        count
                    }
                    leak: () => {
                        rootContext
                    }
                    bodyRunCount: () => {
                        bodyRuns
                    }
                    report: (line) => {
                        out.writeLine(line).value()
                        null
                    }
                }
                behavior
            }
            """;

    /** A client Actor that reaches the service only through the GroupRef it received. */
    private static final String CLIENT =
            """
            ambient: context.hasSlot("process") || context.hasSlot("filesystem") || context.hasSlot("network") || context.hasSlot("resolver") || context.hasSlot("rootContext")
            start: () => {
                behavior: {
                    run: (service) => {
                        first: service.request("increment").value()
                        second: service.request("increment").value()
                        shared: "unshared"
                        if((first == 1) && (second == 2)) {
                            shared = "shared"
                        }
                        // A GroupRef has send/request (used here) but no stop.
                        stop: Error.handle(
                            () => {
                                service.stop()
                                "stop"
                            },
                            (error) => { "no-stop" }
                        )
                        isolated: "isolated"
                        if(ambient) {
                            isolated = "ambient"
                        }
                        leaked: NonTransferableValue.handle(
                            () => {
                                service.request("leak").value()
                                "leaked"
                            },
                            (error) => { "refused" }
                        )
                        once: "repeated"
                        if(service.request("bodyRunCount").value() == 1) {
                            once = "once"
                        }
                        service.send("report", "client " + shared + " " + stop + " " + isolated + " " + leaked + " " + once)
                        null
                    }
                }
                behavior
            }
            """;

    /**
     * A RootActor service whose modules come from the application catalog: it imports
     * {@code app:shared}, spawns {@code app:peer} and reports whether an unknown {@code app:}
     * import fails as an ordinary Error.
     */
    private static final String CATALOG_SERVICE =
            """
            ProcessStreams: import("std:io/ProcessStreams")
            shared: import("app:shared")
            out: ProcessStreams.stdoutWriter(process)
            service: () => {
                shared.bump()
                unknown: Error.handle(
                    () => {
                        import("app:unknown")
                        "unknown-imported"
                    },
                    (error) => { "unknown-refused" }
                )
                self: Actor.group(Actor.current())
                peer: Actor.spawn("app:peer", "start")
                peer.send("run", self)
                behavior: {
                    rootCount: () => {
                        shared.count
                    }
                    report: (line) => {
                        out.writeLine(line + " " + unknown).value()
                        null
                    }
                }
                behavior
            }
            """;

    /** One catalog module with module-instance state. */
    private static final String SHARED =
            """
            count: 0
            bump: () => {
                count = count + 1
                count
            }
            """;

    /** A catalog Actor module importing the same {@code app:shared} identity in its own Actor. */
    private static final String PEER =
            """
            shared: import("app:shared")
            start: () => {
                behavior: {
                    run: (service) => {
                        first: shared.bump()
                        second: shared.bump()
                        rootCount: service.request("rootCount").value()
                        state: "shared-state"
                        if((first == 1) && (second == 2) && (rootCount == 1)) {
                            state = "actor-local"
                        }
                        service.send("report", "catalog " + state)
                        null
                    }
                }
                behavior
            }
            """;

    @TempDir Path directory;

    @Test
    void aLocalBootstrapInstallsOneBehaviorAndServesGroupRefMessages() throws Exception {
        Path entry = write("service.protos", SERVICE);
        write("client.protos", CLIENT);
        ServiceOutput out = new ServiceOutput();

        try (ProtosRootActorServiceSession session = open(entry, "service", out)) {
            ProtosActor root = session.processForTesting().rootActorForRuntime();
            assertEquals(ProtosActor.LifecycleState.READY, root.lifecycleState());
            ProtosObjectValue behavior = root.currentBehavior().orElseThrow();

            assertEquals(
                    "client shared no-stop isolated refused once",
                    out.awaitLine("client "));

            // The READY behavior is never replaced by later message turns.
            assertSame(behavior, root.currentBehavior().orElseThrow());
            assertEquals(ProtosActor.LifecycleState.READY, root.lifecycleState());
            assertFalse(session.isTerminated());
        }
    }

    @Test
    void spawnedActorBootstrapResolvesRelativeImportsFromItsOwnModule()
            throws Exception {
        Path entry =
                write(
                        "relative-root.protos",
                        """
                        ProcessStreams: import("std:io/ProcessStreams")
                        out: ProcessStreams.stdoutWriter(process)

                        service: () => {
                            self: Actor.group(Actor.current())
                            worker: Actor.spawn("./relative-worker.protos", "start")
                            worker.send("run", self)
                            {
                                report: (message) => {
                                    out.writeLine(message).value()
                                    null
                                }
                            }
                        }
                        """);

        write(
                "relative-worker.protos",
                """
                start: () => {
                    dependency: import("./relative-dependency.protos")
                    {
                        run: (service) => {
                            service.send(
                                "report",
                                "relative-bootstrap " + dependency.answer
                            )
                            null
                        }
                    }
                }
                """);

        write(
                "relative-dependency.protos",
                "answer: \"module-relative\"\n");

        ServiceOutput out = new ServiceOutput();

        try (ProtosRootActorServiceSession session =
                open(entry, "service", out)) {
            assertEquals(
                    "relative-bootstrap module-relative",
                    out.awaitLine("relative-bootstrap "));
            assertFalse(session.isTerminated());
        }
    }

    @Test
    void openingDoesNotEndTheServiceAndCloseTerminatesIt() throws Exception {
        Path entry = write("service.protos", SERVICE);
        write("client.protos", CLIENT);
        ServiceOutput out = new ServiceOutput();

        ProtosRootActorServiceSession session = open(entry, "service", out);
        ProtosProcessRuntime process = session.processForTesting();
        ProtosPolyglotProcessContext processContext = session.processContextForTesting();
        try {
            out.awaitLine("client ");
            assertEquals(ProtosProcessRuntime.LifecycleState.RUNNING, process.lifecycleState());
            assertFalse(processContext.isClosedForTesting());
        } finally {
            session.close();
        }
        assertTrue(session.isTerminated());
        assertTrue(processContext.isClosedForTesting());
        assertEquals(0, session.runtimeHostForTesting().activeProcessContextCountForTesting());
        // Idempotent close.
        session.close();
    }

    @Test
    void catalogModulesAreImportedPerActorAndUnknownOnesFailOrdinarily() throws Exception {
        Path entry = write("catalog-service.protos", CATALOG_SERVICE);
        ServiceOutput out = new ServiceOutput();
        Map<String, String> catalog =
                Map.of(
                        "app:shared", SHARED,
                        "app:peer", PEER,
                        // A catalog entry never shadows the std: module the entry imports.
                        "app:io/ProcessStreams", "Error().signal()\n");

        try (ProtosRootActorServiceSession session = open(entry, "service", out, catalog)) {
            // The RootActor and the peer each bumped their own app:shared instance.
            assertEquals("catalog actor-local unknown-refused", out.awaitLine("catalog "));
            assertFalse(session.isTerminated());
        }
    }

    @Test
    void anInvalidCatalogIsRejectedBeforeAnythingIsOpened() {
        // The entry file does not exist: rejection precedes even reading the source.
        Path absent = directory.resolve("absent.protos");
        assertThrows(
                IllegalArgumentException.class,
                () -> open(absent, "service", new ServiceOutput(), Map.of("shared", SHARED)));
        assertThrows(
                IllegalArgumentException.class,
                () -> open(absent, "service", new ServiceOutput(), Map.of("app:", SHARED)));
        Map<String, String> missingSource = new HashMap<>();
        missingSource.put("app:shared", null);
        assertThrows(
                NullPointerException.class,
                () -> open(absent, "service", new ServiceOutput(), missingSource));
    }

    @Test
    void aMissingBindingFailsInitialization() throws Exception {
        assertInitializationFails("missing: () => { {} }\n", "service");
    }

    @Test
    void anInheritedSameNamedBindingDoesNotSatisfyBootstrap() throws Exception {
        // hasSlot is reachable from the module context only through delegation.
        assertInitializationFails("service: () => { {} }\n", "hasSlot");
    }

    @Test
    void aNonCallableBindingFailsInitialization() throws Exception {
        assertInitializationFails("service: 42\n", "service");
    }

    @Test
    void aNullOrNumericBehaviorFailsInitialization() throws Exception {
        assertInitializationFails("service: () => { null }\n", "service");
        assertInitializationFails("service: () => { 42 }\n", "service");
    }

    @Test
    void anEscapingBootstrapErrorTerminatesTheProcess() throws Exception {
        assertInitializationFails("service: () => { Error().signal() }\n", "service");
        assertInitializationFails("Error().signal()\nservice: () => { {} }\n", "service");
    }

    @Test
    void directEntryEvaluationIsUnchangedAndNeverInvokesTheBinding() throws Exception {
        Path entry =
                write(
                        "direct.protos",
                        "calls: 0\nservice: () => {\n    calls = calls + 1\n    {}\n}\ncalls\n");

        ProtosExecutionOutcome outcome = ProtosStandaloneHostedExecution.executeFile(CORE, entry);

        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
        assertEquals("0", ProtosNumericValueSupport.integerDecimalText(outcome.value()));
    }

    private void assertInitializationFails(String source, String binding) throws Exception {
        Path entry = write("failing.protos", source);
        assertThrows(
                ProtosRootActorServiceSession.InitializationFailedException.class,
                () -> open(entry, binding, new ServiceOutput()),
                source);
    }

    private ProtosRootActorServiceSession open(
            Path entry, String binding, OutputStream out, Map<String, String> catalog)
            throws IOException {
        return ProtosRootActorServiceSession.open(
                CORE,
                entry,
                binding,
                List.of(),
                InputStream.nullInputStream(),
                out,
                OutputStream.nullOutputStream(),
                NetworkGrant.NONE,
                ResolverGrant.NONE,
                catalog);
    }

    /** The original overload, without a catalog. */
    private ProtosRootActorServiceSession open(Path entry, String binding, OutputStream out)
            throws IOException {
        return ProtosRootActorServiceSession.open(
                CORE,
                entry,
                binding,
                List.of(),
                InputStream.nullInputStream(),
                out,
                OutputStream.nullOutputStream(),
                NetworkGrant.NONE,
                ResolverGrant.NONE);
    }

    private Path write(String name, String source) throws IOException {
        Path file = directory.resolve(name);
        Files.writeString(file, source, StandardCharsets.UTF_8);
        return file;
    }

    /** Process standard output that a test can wait on line by line. */
    static final class ServiceOutput extends OutputStream {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        @Override
        public synchronized void write(int value) {
            bytes.write(value);
            notifyAll();
        }

        @Override
        public synchronized void write(byte[] buffer, int offset, int length) {
            bytes.write(buffer, offset, length);
            notifyAll();
        }

        /** Waits for the first complete line starting with {@code prefix} and returns it. */
        synchronized String awaitLine(String prefix) throws InterruptedException {
            return awaitLineOrFailure(prefix, null);
        }

        /**
         * Waits for the expected line, but fails immediately if the guest
         * explicitly reports a startup failure. Other callers keep the
         * original awaitLine behavior.
         */
        synchronized String awaitLineOrFailure(String prefix, String failurePrefix)
                throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SAFETY_MILLIS);
            while (true) {
                String text = bytes.toString(StandardCharsets.UTF_8);
                int end = text.lastIndexOf('\n');
                if (end >= 0) {
                    for (String line : text.substring(0, end).split("\n")) {
                        String complete = line.strip();
                        if (failurePrefix != null && complete.startsWith(failurePrefix)) {
                            fail("guest service startup failed: " + complete);
                        }
                        if (complete.startsWith(prefix)) return complete;
                    }
                }
                long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                if (remaining <= 0) {
                    fail("no output line starting with '" + prefix + "'; output was: " + text);
                }
                wait(remaining);
            }
        }
    }
}
