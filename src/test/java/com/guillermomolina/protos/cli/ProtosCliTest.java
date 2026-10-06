/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.cli;

import static org.junit.jupiter.api.Assertions.*;

import com.guillermomolina.protos.execution.ProtosExecutionOutcome;
import com.guillermomolina.protos.execution.ProtosLanguage;
import com.guillermomolina.protos.execution.ProtosModuleResolver;
import com.guillermomolina.protos.execution.ProtosPolyglotRuntimeHost;
import com.guillermomolina.protos.execution.ProtosStandardLibraryModuleResolver;
import com.guillermomolina.protos.execution.ProtosWorkspacePackageApplicationExecution.NetworkGrant;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosNetworkCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.guillermomolina.protos.runtime.ProtosTask;
import com.oracle.truffle.api.source.Source;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

final class ProtosCliTest {
    private R run(String... args) {
        return runWithInput(new byte[0], args);
    }

    private R runWithInput(byte[] input, String... args) {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        int code =
                new ProtosCli()
                        .run(
                                args,
                                new ByteArrayInputStream(input),
                                new PrintStream(out),
                                new PrintStream(err));
        return new R(
                code,
                out.toString(StandardCharsets.UTF_8),
                err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void helpVersionAndNonInteractiveResultPolicy() {
        R help = run("--help");
        assertEquals(0, help.c);
        assertTrue(help.o.contains("[args...]"));
        assertTrue(help.o.contains("process.args()"));
        assertTrue(help.o.contains("protos package"));
        assertTrue(help.o.contains("protos test"));
        assertTrue(help.o.contains("protos debug <file> [args...]"));
        assertTrue(help.o.contains("PROTOS_DEBUG_READY"));
        assertTrue(help.o.contains("explicit program output"));
        assertTrue(help.o.contains("protos format [<file>]"));

        assertTrue(run("--version").o.startsWith("Protos "));
        assertEquals("", run("-e", "1 + 1").o);
        assertEquals("", run("-e", "\"hello\"").o);
        assertEquals("", run("-e", "null").o);
    }

    @Test
    void cliPrintRunsProtosSourceThroughProcessStdoutWithoutDoubleEcho() {
        R result = run("protos/tests/cli/print-output.protos");

        assertEquals(0, result.c);
        assertEquals("hello\n42\ntrue\nfalse\nnull\n", result.o);
        assertTrue(result.e.isBlank(), result.e);
    }

    @Test
    void printArityFailuresAreOrdinaryProtosErrors() {
        R zero = run("-e", "print()");
        assertEquals(1, zero.c);
        assertTrue(zero.o.isBlank(), zero.o);
        assertTrue(zero.e.startsWith("Error:"), zero.e);
        assertFalse(zero.e.contains("IllegalArgumentException"), zero.e);

        R two = run("-e", "print(1, 2)");
        assertEquals(1, two.c);
        assertTrue(two.o.isBlank(), two.o);
        assertTrue(two.e.startsWith("Error:"), two.e);
        assertFalse(two.e.contains("IllegalArgumentException"), two.e);
    }

    @Test
    void evalAndFileApplicationArgumentsExcludeLauncherIdentity() throws Exception {
        assertEquals(
                "2\n",
                run("-e", "print(process.args().size())", "one", "two").o);
        assertEquals(
                "two\n",
                run("-e", "print(process.args().at(1))", "one", "two").o);

        Path file = Files.createTempFile("protos-cli-", ".protos");
        try {
            Files.writeString(file, "print(process.args().at(0))");
            R result = run(file.toString(), "application-value");
            assertEquals(0, result.c);
            assertEquals("application-value\n", result.o);
            assertTrue(result.e.isBlank(), result.e);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void standaloneProcessEnvironmentActorAndEncodingAreBootstrapped() {
        assertEquals(
                "true\n",
                run("-e", "print(process.environment().contains(\"PROTOS_NO_SUCH_VARIABLE\") == false)").o);
        assertEquals(
                "true\n",
                run("-e", "print(process.stdinEncoding() === Encoding.UTF8)").o);
        assertEquals(
                "true\n",
                run("-e", "print(process.stdoutEncoding() === Encoding.UTF8)").o);
        assertEquals(
                "true\n",
                run("-e", "print(process.stderrEncoding() === Encoding.UTF8)").o);
        assertEquals(
                "true\n",
                run("-e", "print(Actor.current() === Actor.current())").o);
    }

    @Test
    void publishedHelloWorldAndValuesTutorialRunEndToEnd() {
        R hello = run("protos/examples/hello-world.protos");
        assertEquals(0, hello.c);
        assertEquals("Hello, Protos!\n", hello.o);
        assertTrue(hello.e.isBlank(), hello.e);

        R values = run("protos/tutorials/01-values-and-slots/01-values.protos");
        assertEquals(0, values.c);
        assertEquals("42\nhello\ntrue\nnull\n", values.o);
        assertTrue(values.e.isBlank(), values.e);
    }

    @Test
    void directFileLocalImportsAreCanonicalImporterRelativeAndConfined()
            throws Exception {
        Path root = Files.createTempDirectory("protos-cli009-");
        Path source = root.resolve("main.protos");
        Path helper = root.resolve("helper.protos");
        Path b = root.resolve("b.protos");
        Path caseTarget = root.resolve("CaseTarget.protos");
        Path sub = Files.createDirectory(root.resolve("sub"));
        Path a = sub.resolve("a.protos");
        Path outside =
                root.resolveSibling(
                        root.getFileName().toString() + "-outside.protos");
        try {
            Files.writeString(
                    source,
                    "print(\"main\")\n"
                            + "marker: Object()\n"
                            + "Helper: import(\"./helper.protos\")\n"
                            + "print(Helper.entryMarker === marker)\n",
                    StandardCharsets.UTF_8);
            Files.writeString(
                    helper,
                    "Main: import(\"./main.protos\")\n"
                            + "entryMarker: Main.marker\n",
                    StandardCharsets.UTF_8);

            R cycle = run(source.toString());
            assertEquals(0, cycle.c);
            assertEquals("main\ntrue\n", cycle.o);
            assertTrue(cycle.e.isBlank(), cycle.e);

            Files.writeString(
                    source,
                    "A: import(\"./sub/a.protos\")\n"
                            + "print(A.value)\n",
                    StandardCharsets.UTF_8);
            Files.writeString(
                    a,
                    "B: import(\"../b.protos\")\n"
                            + "value: B.value\n",
                    StandardCharsets.UTF_8);
            Files.writeString(
                    b,
                    "value: \"nested\"\n",
                    StandardCharsets.UTF_8);

            R nested = run(source.toString());
            assertEquals(0, nested.c);
            assertEquals("nested\n", nested.o);
            assertTrue(nested.e.isBlank(), nested.e);

            Files.writeString(
                    outside,
                    "value: \"outside\"\n",
                    StandardCharsets.UTF_8);
            Files.writeString(
                    source,
                    "Outside: import(\"../"
                            + outside.getFileName()
                            + "\")\n",
                    StandardCharsets.UTF_8);
            R escape = run(source.toString());
            assertEquals(1, escape.c);
            assertTrue(escape.o.isBlank(), escape.o);
            assertTrue(escape.e.startsWith("Error:"), escape.e);

            Files.writeString(
                    source,
                    "Missing: import(\"./helper\")\n",
                    StandardCharsets.UTF_8);
            R noImplicitExtension = run(source.toString());
            assertEquals(1, noImplicitExtension.c);
            assertTrue(noImplicitExtension.e.startsWith("Error:"), noImplicitExtension.e);

            Files.writeString(
                    caseTarget,
                    "value: 1\n",
                    StandardCharsets.UTF_8);
            Files.writeString(
                    source,
                    "WrongCase: import(\"./casetarget.protos\")\n",
                    StandardCharsets.UTF_8);
            R wrongCase = run(source.toString());
            assertEquals(1, wrongCase.c);
            assertTrue(wrongCase.e.startsWith("Error:"), wrongCase.e);

            R locationless = run("-e", "import(\"./helper.protos\")");
            assertEquals(1, locationless.c);
            assertTrue(locationless.e.startsWith("Error:"), locationless.e);
        } finally {
            Files.deleteIfExists(a);
            Files.deleteIfExists(sub);
            Files.deleteIfExists(caseTarget);
            Files.deleteIfExists(b);
            Files.deleteIfExists(helper);
            Files.deleteIfExists(source);
            Files.deleteIfExists(root);
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void packageSubcommandRunsBundledProtosToolWithProcessArgsStdoutAndRestrictedFilesystem() {
        R result = run("package");

        assertEquals(0, result.c);
        assertEquals("Protos package tool bootstrap\npackage\n", result.o);
        assertTrue(result.e.isBlank(), result.e);
    }

    @Test
    void testSubcommandBootstrapsBundledToolBeforeCorpusExecution() {
        R result = run("test", "--jobs", "0");

        assertEquals(1, result.c);
        assertTrue(result.o.isBlank(), result.o);
        assertTrue(result.e.startsWith("Test tool error:"), result.e);
        assertFalse(result.e.contains("Internal error:"), result.e);
    }

    @Test
    void ordinaryCliApplicationDoesNotReceivePackageToolFilesystemAuthority() {
        assertNotEquals(0, run("-e", "filesystem").c);
    }

    @Test
    void syntaxUsageAndMissingFile() throws Exception {
        assertNotEquals(0, run("-e", "(").c);
        assertEquals(2, run("-e").c);
        assertEquals(2, run("debug").c);
        assertEquals(2, run("--unknown").c);

        Path missingDebug = Files.createTempFile("protos-cli-debug-", ".protos");
        Files.deleteIfExists(missingDebug);
        R missingDebugResult = run("debug", missingDebug.toString());
        assertEquals(1, missingDebugResult.c);
        assertTrue(missingDebugResult.o.isBlank(), missingDebugResult.o);
        assertTrue(
                missingDebugResult.e.startsWith("protos debug: cannot read"),
                missingDebugResult.e);

        Path file = Files.createTempFile("protos-cli-", ".protos");
        Files.deleteIfExists(file);
        assertNotEquals(0, run(file.toString()).c);
    }

    @Test
    void formatFileAndStdinWriteOnlyCanonicalSourceToStdout() throws Exception {
        Path file = Files.createTempFile("protos-cli-format-", ".protos");
        try {
            byte[] original = "value:1".getBytes(StandardCharsets.UTF_8);
            Files.write(file, original);

            R fromFile = run("format", file.toString());
            assertEquals(0, fromFile.c, fromFile.e);
            assertEquals("value: 1\n", fromFile.o);
            assertEquals("", fromFile.e);
            assertArrayEquals(original, Files.readAllBytes(file));

            R fromStdin = runWithInput(original, "format");
            assertEquals(0, fromStdin.c, fromStdin.e);
            assertEquals("value: 1\n", fromStdin.o);
            assertEquals("", fromStdin.e);

            R canonical =
                    runWithInput("value: 1\n".getBytes(StandardCharsets.UTF_8), "format");
            assertEquals(0, canonical.c, canonical.e);
            assertEquals("value: 1\n", canonical.o);
            assertEquals("", canonical.e);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void formatSymlinkToRegularFileIsReadOnly() throws Exception {
        Path dir = Files.createTempDirectory("protos-cli-format-link-");
        Path target = dir.resolve("target.protos");
        Path link = dir.resolve("link.protos");
        try {
            Files.writeString(target, "value:1", StandardCharsets.UTF_8);
            try {
                Files.createSymbolicLink(link, target);
            } catch (UnsupportedOperationException | IOException | SecurityException e) {
                org.junit.jupiter.api.Assumptions.assumeTrue(false, "symlinks unavailable");
            }

            R result = run("format", link.toString());
            assertEquals(0, result.c, result.e);
            assertEquals("value: 1\n", result.o);
            assertEquals("", result.e);
            assertTrue(Files.isSymbolicLink(link));
            assertEquals("value:1", Files.readString(target, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(link);
            Files.deleteIfExists(target);
            Files.deleteIfExists(dir);
        }
    }

    @Test
    void formatInvalidSourceFailsClosedWithOriginalSourceOnStdout() throws Exception {
        String invalid = "foo(";
        Path file = Files.createTempFile("protos-cli-format-invalid-", ".protos");
        try {
            Files.writeString(file, invalid, StandardCharsets.UTF_8);

            R fromFile = run("format", file.toString());
            assertEquals(1, fromFile.c);
            assertEquals(invalid, fromFile.o);
            assertTrue(fromFile.e.startsWith("protos format:"), fromFile.e);
            assertFalse(fromFile.e.contains("Internal error:"), fromFile.e);
            assertEquals(invalid, Files.readString(file, StandardCharsets.UTF_8));

            R fromStdin = runWithInput(invalid.getBytes(StandardCharsets.UTF_8), "format");
            assertEquals(1, fromStdin.c);
            assertEquals(invalid, fromStdin.o);
            assertTrue(fromStdin.e.startsWith("protos format:"), fromStdin.e);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void formatInputAndUsageFailuresKeepStdoutEmpty() throws Exception {
        R tooMany = run("format", "a.protos", "b.protos");
        assertEquals(2, tooMany.c);
        assertEquals("", tooMany.o);
        assertTrue(tooMany.e.startsWith("protos: format"), tooMany.e);

        Path dir = Files.createTempDirectory("protos-cli-format-input-");
        Path missing = dir.resolve("missing.protos");
        Path malformed = dir.resolve("malformed.protos");
        byte[] malformedBytes = {'v', ':', ' ', (byte) 0xC3, (byte) 0x28, '\n'};
        try {
            Files.write(malformed, malformedBytes);

            for (R failure :
                    new R[] {
                        run("format", missing.toString()),
                        run("format", dir.toString()),
                        run("format", malformed.toString()),
                        runWithInput(malformedBytes, "format")
                    }) {
                assertEquals(1, failure.c, failure.e);
                assertEquals("", failure.o);
                assertTrue(failure.e.startsWith("protos format: cannot read"), failure.e);
            }
            assertArrayEquals(malformedBytes, Files.readAllBytes(malformed));
        } finally {
            Files.deleteIfExists(malformed);
            Files.deleteIfExists(dir);
        }
    }

    @Test
    void evalAndDirectFileApplicationsReceiveNoNetwork() throws Exception {
        R eval = run("-e", "network");
        assertEquals(1, eval.c);
        assertTrue(eval.e.startsWith("Error:"), eval.e);

        Path file = Files.createTempFile("protos-cli-network-", ".protos");
        try {
            Files.writeString(file, "network");
            R direct = run(file.toString());
            assertEquals(1, direct.c);
            assertTrue(direct.e.startsWith("Error:"), direct.e);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    /**
     * No public CLI route selects Network yet; this drives the internal hosting seam that a future
     * owning policy would use and checks it provisions from the session's own Prelude and host.
     */
    @Test
    void internalSessionSeamCarriesExplicitHostNetworkGrant() throws Exception {
        Path core = Path.of("protos", "lib", "core");
        Method createSession =
                ProtosCli.class.getDeclaredMethod(
                        "createSession",
                        Path.class,
                        ProtosModuleResolver.class,
                        List.class,
                        InputStream.class,
                        PrintStream.class,
                        PrintStream.class,
                        NetworkGrant.class);
        createSession.setAccessible(true);
        PrintStream discard = new PrintStream(OutputStream.nullOutputStream());
        Object session =
                createSession.invoke(
                        new ProtosCli(),
                        core,
                        new ProtosStandardLibraryModuleResolver(core.getParent()),
                        List.of(),
                        InputStream.nullInputStream(),
                        discard,
                        discard,
                        NetworkGrant.HOST_NETWORK);
        try {
            Method execute = session.getClass().getDeclaredMethod("execute", Source.class);
            execute.setAccessible(true);
            ProtosExecutionOutcome outcome =
                    (ProtosExecutionOutcome)
                            execute.invoke(
                                    session,
                                    Source.newBuilder(
                                                    ProtosLanguage.ID,
                                                    "[network, Network]",
                                                    "network-grant")
                                            .build());

            assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
            ProtosArrayValue result = assertInstanceOf(ProtosArrayValue.class, outcome.value());
            ProtosNetworkCapabilityValue network =
                    assertInstanceOf(
                            ProtosNetworkCapabilityValue.class, result.indexedAtForRuntime(0));
            assertSame(result.indexedAtForRuntime(1), network.representedDelegationParent(null));

            Method runtimeHostAccessor = session.getClass().getDeclaredMethod("runtimeHost");
            runtimeHostAccessor.setAccessible(true);
            Method networkHostInitialized =
                    ProtosPolyglotRuntimeHost.class.getDeclaredMethod(
                            "networkHostInitializedForTesting");
            networkHostInitialized.setAccessible(true);
            assertEquals(
                    Boolean.TRUE,
                    networkHostInitialized.invoke(runtimeHostAccessor.invoke(session)));
        } finally {
            Method terminate = session.getClass().getDeclaredMethod("terminate");
            terminate.setAccessible(true);
            terminate.invoke(session);
        }
    }

    @Test
    void sessionTerminationDrainsCancelledSuspendedRootTaskBeforeAwaitingProcess()
            throws Exception {
        Path core = Path.of("protos", "lib", "core");
        Method createSession =
                ProtosCli.class.getDeclaredMethod(
                        "createSession",
                        Path.class,
                        ProtosModuleResolver.class,
                        List.class,
                        InputStream.class,
                        PrintStream.class,
                        PrintStream.class,
                        NetworkGrant.class);
        createSession.setAccessible(true);

        PrintStream discard = new PrintStream(OutputStream.nullOutputStream());
        Object session =
                createSession.invoke(
                        new ProtosCli(),
                        core,
                        new ProtosStandardLibraryModuleResolver(core.getParent()),
                        List.of(),
                        InputStream.nullInputStream(),
                        discard,
                        discard,
                        NetworkGrant.NONE);

        Method activationAccessor = session.getClass().getDeclaredMethod("activation");
        activationAccessor.setAccessible(true);
        ProtosActivation activation =
                (ProtosActivation) activationAccessor.invoke(session);

        Method processAccessor = session.getClass().getDeclaredMethod("process");
        processAccessor.setAccessible(true);
        ProtosProcessRuntime process =
                (ProtosProcessRuntime) processAccessor.invoke(session);

        ProtosTask.WaitDependency pending = new ProtosTask.WaitDependency() {};
        ProtosTask suspended =
                activation.executionDomain()
                        .createTask(
                                null,
                                task -> {
                                    if (task.cancellationRequested()) {
                                        if (!task.observeCancellation()) {
                                            throw new IllegalStateException(
                                                    "pending cancellation was not observable");
                                        }
                                        return;
                                    }
                                    task.suspend(pending);
                                });
        activation.executionDomain().dispatchUntilIdle();
        assertEquals(ProtosTask.State.SUSPENDED, suspended.state());

        Method terminate = session.getClass().getDeclaredMethod("terminate");
        terminate.setAccessible(true);

        ExecutorService executor =
                Executors.newSingleThreadExecutor(
                        command -> {
                            Thread thread =
                                    new Thread(
                                            command,
                                            "protos-cli-session-termination-regression");
                            thread.setDaemon(true);
                            return thread;
                        });

        boolean externalDrainWasRequired = false;
        Future<?> termination =
                executor.submit(
                        () -> {
                            try {
                                terminate.invoke(session);
                            } catch (ReflectiveOperationException failure) {
                                Throwable cause =
                                        failure instanceof java.lang.reflect.InvocationTargetException invocation
                                                ? invocation.getCause()
                                                : failure;
                                if (cause instanceof RuntimeException runtime) {
                                    throw runtime;
                                }
                                if (cause instanceof Error error) {
                                    throw error;
                                }
                                throw new RuntimeException(cause);
                            }
                        });

        try {
            try {
                termination.get(2, TimeUnit.SECONDS);
            } catch (TimeoutException timeout) {
                externalDrainWasRequired = true;

                // Test-only recovery so a regression fails rather than hanging Surefire.
                activation.executionDomain().dispatchUntilIdle();
                termination.get(5, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertFalse(
                externalDrainWasRequired,
                "Session.terminate must drain cancellation work before awaiting Process termination");
        assertEquals(ProtosTask.State.CANCELLED, suspended.state());
        assertEquals(
                ProtosProcessRuntime.LifecycleState.TERMINATED,
                process.lifecycleState());
    }

    private record R(int c, String o, String e) {}
}
