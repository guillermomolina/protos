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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.guillermomolina.protos.execution.ProtosStandaloneHostedExecution.FilesystemGrant;
import com.guillermomolina.protos.execution.ProtosStandaloneHostedExecution.ResolverGrant;
import com.guillermomolina.protos.execution.ProtosWorkspacePackageApplicationExecution.NetworkGrant;
import com.guillermomolina.protos.runtime.ProtosActor;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
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
            ProcessStreams: import("std:io/process_streams")
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
            ProcessStreams: import("std:io/process_streams")
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
                        ProcessStreams: import("std:io/process_streams")
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

    /** I155: a RootActor service that uses its Filesystem grant only after READY. */
    private static final String FILESYSTEM_SERVICE =
            """
            ProcessStreams: import("std:io/process_streams")
            Files: import("std:io/files")
            out: ProcessStreams.stdoutWriter(process)
            path: Path.relative().child("nested").child("service.txt")
            heldFile: filesystem.open(path, write: true, create: true).value()
            service: () => {
                self: Actor.group(Actor.current())
                client: Actor.spawn("./fs-client.protos", "start")
                client.send("run", self)
                {
                    store: (text) => {
                        Files.writeAllText(filesystem, path, text, Encoding.UTF8).value()
                        out.writeLine("stored " + Files.readAllText(filesystem, path, Encoding.UTF8).value()).value()
                        null
                    }
                    report: (line) => {
                        out.writeLine(line).value()
                        null
                    }
                }
            }
            """;

    private static final String FILESYSTEM_CLIENT =
            """
            ambient: context.hasSlot("filesystem")
            start: () => {
                {
                    run: (service) => {
                        label: "absent"
                        if(ambient) {
                            label = "present"
                        }
                        service.send("report", "client-filesystem " + label)
                        service.send("store", "after-ready")
                        null
                    }
                }
            }
            """;

    @Test
    void grantedFilesystemStaysLiveAfterReadyAndIsRevokedAtClose() throws Exception {
        Path data = Files.createDirectories(directory.resolve("data/nested")).getParent();
        Path entry = write("fs-service.protos", FILESYSTEM_SERVICE);
        write("fs-client.protos", FILESYSTEM_CLIENT);
        ServiceOutput out = new ServiceOutput();
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();

        ProtosRootActorServiceSession session;
        try {
            session =
                    ProtosRootActorServiceSession.open(
                            CORE,
                            entry,
                            "service",
                            List.of(),
                            InputStream.nullInputStream(),
                            out,
                            err,
                            NetworkGrant.NONE,
                            ResolverGrant.NONE,
                            FilesystemGrant.directory(data),
                            Map.of());
        } catch (ProtosRootActorServiceSession.InitializationFailedException failed) {
            throw new AssertionError(
                    "service initialization failed; stderr:\n"
                            + err.toString(java.nio.charset.StandardCharsets.UTF_8),
                    failed);
        }
        ProtosPolyglotRuntimeHost runtimeHost = session.runtimeHostForTesting();
        try {
            assertEquals("client-filesystem absent", out.awaitLine("client-filesystem "));
            assertEquals("stored after-ready", out.awaitLine("stored "));
            assertEquals("after-ready", Files.readString(data.resolve("nested/service.txt")));
            assertFalse(session.isTerminated());
            assertEquals(1, runtimeHost.processFilesystemCountForTesting());
        } finally {
            session.close();
        }
        assertTrue(session.isTerminated());
        assertEquals(0, runtimeHost.processFilesystemCountForTesting());
    }

    @Test
    void failedInitializationRevokesTheGrantedFilesystem() throws Exception {
        Path data = Files.createDirectories(directory.resolve("failing-data"));
        Path entry =
                write(
                        "fs-failing.protos",
                        "held: filesystem.open(Path.relative().child(\"f\"), write: true, create: true).value()\n"
                                + "service: () => { null }\n");
        ProtosPolyglotRuntimeHost[] hosts = new ProtosPolyglotRuntimeHost[1];

        assertThrows(
                ProtosRootActorServiceSession.InitializationFailedException.class,
                () -> ProtosRootActorServiceSession.openWithHost(
                        CORE,
                        entry,
                        "service",
                        List.of(),
                        InputStream.nullInputStream(),
                        OutputStream.nullOutputStream(),
                        OutputStream.nullOutputStream(),
                        NetworkGrant.NONE,
                        ResolverGrant.NONE,
                        FilesystemGrant.directory(data),
                        Map.of(),
                        () -> hosts[0] = ProtosPolyglotRuntimeHost.open(),
                        (activation, process) -> {}));
        assertEquals(0, hosts[0].processFilesystemCountForTesting());
        assertTrue(Files.isRegularFile(data.resolve("f")));
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

    // ---- I158 / D251-A1: suspendible RootActor service initialization --------------------
    //
    // The host owns a loopback ServerSocket bound to port 0 for the whole test. The guest
    // connects to it and suspends in connection.read(1).value(); the test accepts the
    // connection, checks the still-pending initialization, and releases exactly one
    // suspension per byte it writes. No port is probed and no stdout write is a wait signal.

    /** Guest prelude connecting to the host fixture; {@code connection} is a RootActor local. */
    private static String connectPrelude(int port) {
        return "IpAddress: import(\"std:network/ip_addresses\").IpAddress\n"
                + "IpEndpoint: import(\"std:network/ip_endpoints\").IpEndpoint\n"
                + "connection: network.connectTcp(IpEndpoint(IpAddress(4, 2130706433), "
                + port
                + ")).value()\n";
    }

    @Test
    void i158SourceBodyAwaitsAPendingReadBeforeDefiningTheBinding() throws Exception {
        try (HostGate gate = new HostGate()) {
            Path entry =
                    write(
                            "i158-body.protos",
                            connectPrelude(gate.port())
                                    + "bodyRuns: 0\n"
                                    + "bodyRuns = bodyRuns + 1\n"
                                    + "pendingRead: connection.read(1)\n"
                                    + "first: pendingRead.value()\n"
                                    + "service: () => { { runs: bodyRuns } }\n");
            AtomicReference<ProtosProcessRuntime> process = new AtomicReference<>();
            CompletableFuture<ProtosRootActorServiceSession> opening =
                    startOpen(entry, new ServiceOutput(), process);
            try (Socket peer = gate.accept()) {
                ProtosActor root = process.get().rootActorForRuntime();
                assertEquals(ProtosActor.LifecycleState.INITIALIZING, root.lifecycleState());
                assertFalse(opening.isDone());
                assertTrue(root.currentBehavior().isEmpty());

                awaitSuspension(process.get().rootActorForRuntime(), null, opening);

                release(peer);
                try (ProtosRootActorServiceSession session = opening.get(5, TimeUnit.SECONDS)) {
                    assertEquals(ProtosActor.LifecycleState.READY, root.lifecycleState());
                    assertEquals(
                            "1",
                            ProtosNumericValueSupport.integerDecimalText(
                                    root.currentBehavior().orElseThrow().readLocalSlot("runs").orElseThrow()));
                }
            } finally {
                closeQuietly(opening);
            }
        }
    }

    @Test
    void i158BindingAwaitsAPendingReadAndThenProducesTheBehavior() throws Exception {
        try (HostGate gate = new HostGate()) {
            Path entry =
                    write(
                            "i158-binding.protos",
                            connectPrelude(gate.port())
                                    + "service: () => {\n"
                                    + "    pendingRead: connection.read(1)\n"
                                    + "    first: pendingRead.value()\n"
                                    + "    { first: first }\n"
                                    + "}\n");
            AtomicReference<ProtosProcessRuntime> process = new AtomicReference<>();
            CompletableFuture<ProtosRootActorServiceSession> opening =
                    startOpen(entry, new ServiceOutput(), process);
            try (Socket peer = gate.accept()) {
                ProtosActor root = process.get().rootActorForRuntime();
                assertEquals(ProtosActor.LifecycleState.INITIALIZING, root.lifecycleState());
                assertFalse(opening.isDone());

                awaitSuspension(process.get().rootActorForRuntime(), null, opening);

                release(peer);
                try (ProtosRootActorServiceSession session = opening.get(5, TimeUnit.SECONDS)) {
                    assertEquals(ProtosActor.LifecycleState.READY, root.lifecycleState());
                    assertTrue(root.currentBehavior().orElseThrow().hasLocalSlot("first"));
                }
            } finally {
                closeQuietly(opening);
            }
        }
    }

    @Test
    void i158NestedImportAwaitsAndReturnsWithoutReevaluatingTheInitialModule() throws Exception {
        try (HostGate gate = new HostGate()) {
            // The secondary module receives no network capability: it observes the Future the
            // initial module created and published before importing it, via a circular import.
            write(
                    "i158-gate.protos",
                    "main: import(\"./i158-importer.protos\")\n"
                            + "ambient: context.hasSlot(\"network\")\n"
                            + "seen: main.pending.value()\n");
            Path entry =
                    write(
                            "i158-importer.protos",
                            connectPrelude(gate.port())
                                    + "beforeRuns: 0\n"
                                    + "beforeRuns = beforeRuns + 1\n"
                                    + "pending: connection.read(1)\n"
                                    + "gate: import(\"./i158-gate.protos\")\n"
                                    + "afterRuns: 0\n"
                                    + "afterRuns = afterRuns + 1\n"
                                    + "service: () => { { ambient: gate.ambient; before: beforeRuns; after: afterRuns } }\n");
            AtomicReference<ProtosProcessRuntime> process = new AtomicReference<>();
            CompletableFuture<ProtosRootActorServiceSession> opening =
                    startOpen(entry, new ServiceOutput(), process);
            try (Socket peer = gate.accept()) {
                ProtosActor root = process.get().rootActorForRuntime();
                assertEquals(ProtosActor.LifecycleState.INITIALIZING, root.lifecycleState());
                assertFalse(opening.isDone());

                awaitSuspension(process.get().rootActorForRuntime(), null, opening);

                release(peer);
                try (ProtosRootActorServiceSession session = opening.get(5, TimeUnit.SECONDS)) {
                    ProtosObjectValue behavior = root.currentBehavior().orElseThrow();
                    assertEquals("1", ProtosNumericValueSupport.integerDecimalText(behavior.readLocalSlot("before").orElseThrow()));
                    assertEquals("1", ProtosNumericValueSupport.integerDecimalText(behavior.readLocalSlot("after").orElseThrow()));
                    assertEquals(
                            Boolean.FALSE,
                            asJavaBoolean(root.currentBehavior().orElseThrow().readLocalSlot("ambient").orElseThrow()));
                }
            } finally {
                closeQuietly(opening);
            }
        }
    }

    @Test
    void i158TwoConsecutiveSuspensionsInvokeTheBindingOnce() throws Exception {
        try (HostGate gate = new HostGate()) {
            Path entry =
                    write(
                            "i158-twice.protos",
                            connectPrelude(gate.port())
                                    + "bindingRuns: 0\n"
                                    + "pendingRead: connection.read(1)\n"
                                    + "first: pendingRead.value()\n"
                                    + "service: () => {\n"
                                    + "    bindingRuns = bindingRuns + 1\n"
                                    + "    secondRead: connection.read(1)\n"
                                    + "    second: secondRead.value()\n"
                                    + "    { runs: bindingRuns }\n"
                                    + "}\n");
            AtomicReference<ProtosProcessRuntime> process = new AtomicReference<>();
            CompletableFuture<ProtosRootActorServiceSession> opening =
                    startOpen(entry, new ServiceOutput(), process);
            try (Socket peer = gate.accept()) {
                ProtosActor root = process.get().rootActorForRuntime();
                ProtosFutureValue firstWait = awaitSuspension(root, null, opening);
                release(peer);
                ProtosFutureValue secondWait = awaitSuspension(root, firstWait, opening);
                assertNotSame(firstWait, secondWait, "both suspension points were reached");
                assertEquals(ProtosFutureValue.State.RESOLVED, firstWait.state());

                release(peer);
                try (ProtosRootActorServiceSession session = opening.get(5, TimeUnit.SECONDS)) {
                    assertEquals(
                            "1",
                            ProtosNumericValueSupport.integerDecimalText(
                                    root.currentBehavior().orElseThrow().readLocalSlot("runs").orElseThrow()));
                }
            } finally {
                closeQuietly(opening);
            }
        }
    }

    @Test
    void i158ProcessTerminationWhileAwaitingFailsInitializationWithoutLateReady() throws Exception {
        try (HostGate gate = new HostGate()) {
            Path entry =
                    write(
                            "i158-terminate.protos",
                            connectPrelude(gate.port())
                                    + "resumed: 0\n"
                                    + "pendingRead: connection.read(1)\n"
                                    + "first: pendingRead.value()\n"
                                    + "resumed = 1\n"
                                    + "service: () => { {} }\n");
            AtomicReference<ProtosProcessRuntime> process = new AtomicReference<>();
            CompletableFuture<ProtosRootActorServiceSession> opening =
                    startOpen(entry, new ServiceOutput(), process);
            try (Socket peer = gate.accept()) {
                ProtosActor root = process.get().rootActorForRuntime();
                ProtosFutureValue waiting = awaitSuspension(root, null, opening);
                process.get().requestTerminationForRuntime();

                ExecutionException failure =
                        assertThrows(ExecutionException.class, () -> opening.get(5, TimeUnit.SECONDS));
                assertInstanceOf(
                        ProtosRootActorServiceSession.InitializationFailedException.class,
                        failure.getCause());
                // The awaited read stays unresolved by the guest; a late byte resumes nothing.
                assertNull(ProtosActorBootstrapSuspension.awaitedFutureForTesting(root));
                lateByte(peer);
                assertTrue(root.currentBehavior().isEmpty());
                assertTrue(root.lifecycleState() != ProtosActor.LifecycleState.READY);
            } finally {
                closeQuietly(opening);
            }
        }
    }

    @Test
    void i158UnhandledErrorAfterResumptionFailsInitialization() throws Exception {
        try (HostGate gate = new HostGate()) {
            Path entry =
                    write(
                            "i158-unhandled.protos",
                            connectPrelude(gate.port())
                                    + "pendingRead: connection.read(1)\n"
                                    + "first: pendingRead.value()\n"
                                    + "Error().signal()\n"
                                    + "service: () => { {} }\n");
            AtomicReference<ProtosProcessRuntime> process = new AtomicReference<>();
            CompletableFuture<ProtosRootActorServiceSession> opening =
                    startOpen(entry, new ServiceOutput(), process);
            try (Socket peer = gate.accept()) {
                assertFalse(opening.isDone());
                awaitSuspension(process.get().rootActorForRuntime(), null, opening);
                release(peer);
                ExecutionException failure =
                        assertThrows(ExecutionException.class, () -> opening.get(5, TimeUnit.SECONDS));
                assertInstanceOf(
                        ProtosRootActorServiceSession.InitializationFailedException.class,
                        failure.getCause());
                assertTrue(process.get().rootActorForRuntime().currentBehavior().isEmpty());
            } finally {
                closeQuietly(opening);
            }
        }
    }

    @Test
    void i158HandledErrorAfterResumptionKeepsTheInitializationAlive() throws Exception {
        try (HostGate gate = new HostGate()) {
            Path entry =
                    write(
                            "i158-handled.protos",
                            connectPrelude(gate.port())
                                    + "label: Error.handle(\n"
                                    + "    () => {\n"
                                    + "        pendingRead: connection.read(1)\n"
                                    + "        pendingRead.value()\n"
                                    + "        Error().signal()\n"
                                    + "    },\n"
                                    + "    (error) => { \"handled\" })\n"
                                    + "service: () => { { label: label } }\n");
            AtomicReference<ProtosProcessRuntime> process = new AtomicReference<>();
            CompletableFuture<ProtosRootActorServiceSession> opening =
                    startOpen(entry, new ServiceOutput(), process);
            try (Socket peer = gate.accept()) {
                awaitSuspension(process.get().rootActorForRuntime(), null, opening);
                release(peer);
                try (ProtosRootActorServiceSession session = opening.get(5, TimeUnit.SECONDS)) {
                    ProtosActor root = session.processForTesting().rootActorForRuntime();
                    assertEquals(ProtosActor.LifecycleState.READY, root.lifecycleState());
                    assertEquals(
                            "handled",
                            ((com.guillermomolina.protos.runtime.ProtosStringValue)
                                            root.currentBehavior().orElseThrow().readLocalSlot("label").orElseThrow())
                                    .value());
                }
            } finally {
                closeQuietly(opening);
            }
        }
    }

    /**
     * Independent check: listenTcp(...).value() and accept().value() suspend the bootstrap and are
     * completed through targeted completions. The guest connects to its own listener, so no host
     * gate is involved and the other tests do not depend on this path.
     */
    @Test
    void i158ListenAndAcceptAwaitedDuringBootstrapComplete() throws Exception {
        Path entry =
                write(
                        "i158-listen.protos",
                        "IpAddress: import(\"std:network/ip_addresses\").IpAddress\n"
                                + "IpEndpoint: import(\"std:network/ip_endpoints\").IpEndpoint\n"
                                + "loopback: IpAddress(4, 2130706433)\n"
                                + "listener: network.listenTcp(ipVersion: 4, address: loopback, port: null).value()\n"
                                + "outbound: network.connectTcp(IpEndpoint(loopback, listener.localPort())).value()\n"
                                + "inbound: listener.accept().value()\n"
                                + "service: () => { {} }\n");
        AtomicReference<ProtosProcessRuntime> process = new AtomicReference<>();
        CompletableFuture<ProtosRootActorServiceSession> opening =
                startOpen(entry, new ServiceOutput(), process);
        try (ProtosRootActorServiceSession session = opening.get(5, TimeUnit.SECONDS)) {
            assertEquals(
                    ProtosActor.LifecycleState.READY,
                    session.processForTesting().rootActorForRuntime().lifecycleState());
        } finally {
            closeQuietly(opening);
        }
    }

    /**
     * Waits, bounded, until the RootActor initialization provably retains a suspended continuation
     * on a pending Future other than {@code previous}, then checks the still-pending state.
     */
    private static ProtosFutureValue awaitSuspension(
            ProtosActor root,
            ProtosFutureValue previous,
            CompletableFuture<ProtosRootActorServiceSession> opening)
            throws InterruptedException {
        // A bootstrap can first suspend on connectTcp().value(), whose Future
        // may become terminal before the host observes its retained continuation.
        // Skip such completed dependencies and wait for the next suspension.
        long deadline =
                System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SAFETY_MILLIS);
        ProtosFutureValue cursor = previous;

        while (true) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                fail("RootActor initialization never retained a pending Future");
            }

            ProtosFutureValue awaited =
                    ProtosActorBootstrapSuspension.awaitSuspendedFutureForTesting(
                            root,
                            cursor,
                            Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remaining)));
            if (awaited == null) {
                fail("RootActor initialization never retained a pending Future");
            }

            // The Future may terminalize between custody notification and
            // observation. Its old retained state is not a new pending wait.
            if (awaited.state() != ProtosFutureValue.State.PENDING) {
                cursor = awaited;
                continue;
            }

            assertEquals(ProtosActor.LifecycleState.INITIALIZING, root.lifecycleState());
            assertFalse(opening.isDone());
            assertTrue(root.currentBehavior().isEmpty());
            return awaited;
        }
    }

    /** A byte written after termination; the closed connection may refuse it. */
    private static void lateByte(Socket peer) {
        try {
            release(peer);
        } catch (IOException closed) {
            // The terminated Process may already have closed its end.
        }
    }

    /** Host-owned loopback ServerSocket kept bound for the whole test. */
    private static final class HostGate implements AutoCloseable {
        private final ServerSocket server;

        HostGate() throws IOException {
            // IPv4 loopback, matching the guest's IpAddress(4, 2130706433).
            server = new ServerSocket(0, 4, InetAddress.getByAddress(new byte[] {127, 0, 0, 1}));
            server.setSoTimeout((int) SAFETY_MILLIS);
        }

        int port() {
            return server.getLocalPort();
        }

        /** Accepts the guest connection; bounded by the safety timeout. */
        Socket accept() throws IOException {
            Socket accepted = server.accept();
            accepted.setSoTimeout((int) SAFETY_MILLIS);
            return accepted;
        }

        @Override
        public void close() throws IOException {
            server.close();
        }
    }

    /** Releases exactly one guest read(1) suspension. */
    private static void release(Socket peer) throws IOException {
        peer.getOutputStream().write(7);
        peer.getOutputStream().flush();
    }

    private static Object asJavaBoolean(Object value) {
        return value == Boolean.TRUE
                ? Boolean.TRUE
                : value == Boolean.FALSE
                        ? Boolean.FALSE
                        : value;
    }

    /** Session cleanups registered by this test; checked after it, failures included. */
    private final List<CompletableFuture<Void>> sessionCleanups = new ArrayList<>();

    /**
     * Closes a session that opened (or opens late) so no Process outlives the test, also after a
     * failed assertion. The derived cleanup Future is kept: a close failure is reported by {@link
     * #sessionCleanupsSucceeded()} with its original cause.
     */
    private void closeQuietly(CompletableFuture<ProtosRootActorServiceSession> opening) {
        sessionCleanups.add(
                opening.handle(
                        (session, failure) -> {
                            if (session != null) {
                                try {
                                    session.close();
                                } catch (IOException closeFailure) {
                                    throw new UncheckedIOException(closeFailure);
                                }
                            }
                            return null;
                        }));
    }

    @AfterEach
    void sessionCleanupsSucceeded() {
        for (CompletableFuture<Void> cleanup : sessionCleanups) {
            // An opening that never completed has no session to close.
            if (cleanup.isDone()) {
                cleanup.join();
            }
        }
    }

    /** Opens the service on a separate host thread; the open contract is synchronous. */
    private CompletableFuture<ProtosRootActorServiceSession> startOpen(
            Path entry, OutputStream out, AtomicReference<ProtosProcessRuntime> process) {
        CountDownLatch captured = new CountDownLatch(1);
        CompletableFuture<ProtosRootActorServiceSession> opening = new CompletableFuture<>();
        Thread host =
                new Thread(
                        () -> {
                            try {
                                opening.complete(
                                        ProtosRootActorServiceSession.openWithHost(
                                                CORE,
                                                entry,
                                                "service",
                                                List.of(),
                                                InputStream.nullInputStream(),
                                                out,
                                                OutputStream.nullOutputStream(),
                                                NetworkGrant.HOST_NETWORK,
                                                ResolverGrant.NONE,
                                                Map.of(),
                                                ProtosPolyglotRuntimeHost::open,
                                                (activation, runtime) -> {
                                                    process.set(runtime);
                                                    captured.countDown();
                                                }));
                            } catch (Throwable failure) {
                                opening.completeExceptionally(failure);
                            } finally {
                                captured.countDown();
                            }
                        },
                        "i158-service-open");
        host.setDaemon(true);
        host.start();
        try {
            assertTrue(captured.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
        if (process.get() == null) {
            fail("service open failed before bootstrap: " + opening);
        }
        return opening;
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

        /** Number of complete lines currently starting with {@code prefix}. */
        synchronized int countLines(String prefix) {
            String text = bytes.toString(StandardCharsets.UTF_8);
            int count = 0;
            for (String line : text.split("\n")) {
                if (line.strip().startsWith(prefix)) count++;
            }
            return count;
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
