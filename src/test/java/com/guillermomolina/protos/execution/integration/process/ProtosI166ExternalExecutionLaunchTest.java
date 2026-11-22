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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guillermomolina.protos.execution.ProtosStandaloneHostedExecution.ClockGrant;
import com.guillermomolina.protos.execution.ProtosStandaloneHostedExecution.EntropyGrant;
import com.guillermomolina.protos.execution.ProtosStandaloneHostedExecution.ExecutionGrant;
import com.guillermomolina.protos.execution.ProtosStandaloneHostedExecution.ResolverGrant;
import com.guillermomolina.protos.execution.ProtosWorkspacePackageApplicationExecution.NetworkGrant;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Standalone (JVM hosted) launch through the canonical ExecutionGrant bootstrap
 * (EXTERNAL_PROCESS.md §§1, 3-4). Real POSIX programs are started directly, never through a shell.
 */
@org.junit.jupiter.api.parallel.Isolated
@Timeout(value = 30, unit = TimeUnit.SECONDS)
final class ProtosI166ExternalExecutionLaunchTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    static final String DRAIN =
            """
            drain: (endpoint) => {
                text: ""
                chunk: endpoint.read(65536).value()
                while(chunk !== null) {
                    text = text + Encoding.UTF8.decode(chunk)
                    chunk = endpoint.read(65536).value()
                }
                text
            }
            """;

    static ProtosStandaloneHostedSession open(Path directory, String source, ExecutionGrant grant)
            throws IOException {
        Path entry = directory.resolve("i166-entry.protos");
        Files.writeString(entry, DRAIN + source + "\nnull\n");
        return ProtosStandaloneHostedSession.open(
                CORE, entry, List.of(), InputStream.nullInputStream(),
                OutputStream.nullOutputStream(), OutputStream.nullOutputStream(),
                NetworkGrant.NONE, ResolverGrant.NONE, EntropyGrant.NONE, ClockGrant.NONE, grant);
    }

    static Object invoke(ProtosStandaloneHostedSession session, String name) throws IOException {
        assertEquals(ProtosExecutionOutcome.State.COMPLETED, session.initialOutcome().state());
        ProtosExecutionOutcome outcome = session.invokeTopLevel(name);
        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state(), String.valueOf(outcome));
        return outcome.value();
    }

    static void assumeProgram(String path) {
        assumeTrue(Files.isExecutable(Path.of(path)), path + " is required");
    }

    @Test
    void grantExistsOnlyWhenExplicitlySelected(@TempDir Path directory) throws IOException {
        String probe = "hadExecution: context.hasSlot(\"execution\")\nprobe: () => { hadExecution }";
        try (ProtosStandaloneHostedSession session = open(directory, probe, ExecutionGrant.NONE)) {
            assertSame(Boolean.FALSE, invoke(session, "probe"));
        }
        try (ProtosStandaloneHostedSession session =
                open(directory, probe, ExecutionGrant.HOST_EXECUTION)) {
            assertSame(Boolean.TRUE, invoke(session, "probe"));
        }
    }

    @Test
    void argumentsArePassedExactlyWithoutShell(@TempDir Path directory) throws IOException {
        assumeProgram("/bin/echo");
        try (ProtosStandaloneHostedSession session = open(directory, """
                probe: () => {
                    child: execution.start("/bin/echo", ["a b", "ñ", "$HOME", "*", ""], Map(), null).value()
                    drain(child.stdout()) == "a b ñ $HOME * \\n"
                }
                """, ExecutionGrant.HOST_EXECUTION)) {
            assertSame(Boolean.TRUE, invoke(session, "probe"));
        }
    }

    @Test
    void environmentIsExactlyTheSuppliedSnapshot(@TempDir Path directory) throws IOException {
        assumeProgram("/usr/bin/env");
        try (ProtosStandaloneHostedSession session = open(directory, """
                probe: () => {
                    empty: drain(execution.start("/usr/bin/env", [], Map(), null).value().stdout())
                    environment: Map()
                    environment["ONLY"] = "value"
                    child: execution.start("/usr/bin/env", [], environment, null).value()
                    environment["LATER"] = "ignored"
                    (empty == "") && (drain(child.stdout()) == "ONLY=value\\n")
                }
                """, ExecutionGrant.HOST_EXECUTION)) {
            assertSame(Boolean.TRUE, invoke(session, "probe"));
        }
    }

    @Test
    void defaultWorkingDirectoryIsStableAndExplicitDirectoryIsHonoured(@TempDir Path directory)
            throws IOException {
        assumeProgram("/bin/pwd");
        String expected = Path.of("").toAbsolutePath().toString();
        try (ProtosStandaloneHostedSession session = open(directory, """
                probe: () => {
                    first: drain(execution.start("/bin/pwd", [], Map(), null).value().stdout())
                    second: drain(execution.start("/bin/pwd", [], Map(), null).value().stdout())
                    root: drain(execution.start("/bin/pwd", [], Map(), "/").value().stdout())
                    (first == second) && (root == "/\\n") && (first == "%s\\n")
                }
                """.formatted(expected), ExecutionGrant.HOST_EXECUTION)) {
            assertSame(Boolean.TRUE, invoke(session, "probe"));
        }
    }

    @Test
    void unresolvableExecutableAndRelativeDirectoryFailWithIOError(@TempDir Path directory)
            throws IOException {
        try (ProtosStandaloneHostedSession session = open(directory, """
                failsWithIOError: (thunk) => {
                    IOError.handle(() => {
                        InvalidIOArgument.handle(() => { thunk().value(); false }, (error) => { false })
                    }, (error) => { true })
                }
                probe: () => {
                    failsWithIOError(() => { execution.start("/nonexistent/protos-i166", [], Map(), null) }) &&
                        failsWithIOError(() => { execution.start("/bin/echo", [], Map(), "relative") })
                }
                """, ExecutionGrant.HOST_EXECUTION)) {
            assertSame(Boolean.TRUE, invoke(session, "probe"));
        }
    }

    @Test
    void nativeProviderInitializationFailureHasNoJdkFallback(@TempDir Path directory) throws IOException {
        Path invalidLibrary = directory.resolve("invalid.so");
        Files.writeString(invalidLibrary, "not a native library");
        org.junit.jupiter.api.Assertions.assertThrows(IOException.class,
                () -> new ProtosPosixProcessHandler(invalidLibrary));
    }

    @Test
    void nativeProviderRejectsMergedOrInheritedStreamsBeforeCreation() throws IOException {
        ProtosPosixProcessHandler provider = new ProtosPosixProcessHandler();
        var pipe = org.graalvm.polyglot.io.ProcessHandler.Redirect.PIPE;
        var inherit = org.graalvm.polyglot.io.ProcessHandler.Redirect.INHERIT;
        for (int changed = 0; changed < 4; changed++) {
            var command = org.graalvm.polyglot.io.ProcessHandler.ProcessCommand.create(
                    java.util.List.of("/bin/echo"), "/", java.util.Map.of(), changed == 3,
                    changed == 0 ? inherit : pipe,
                    changed == 1 ? inherit : pipe,
                    changed == 2 ? inherit : pipe);
            org.junit.jupiter.api.Assertions.assertThrows(IOException.class, () -> provider.start(command));
        }
    }

    @Test
    void nativeExecFailuresAreReclaimedBeforeTheHandlerReturns(@TempDir Path directory) throws IOException {
        ProtosPosixProcessHandler provider = new ProtosPosixProcessHandler();
        var childrenBefore = childIds();
        Path denied = directory.resolve("not-executable");
        Files.writeString(denied, "this file must not be executed");
        Files.setPosixFilePermissions(denied, java.util.Set.of(
                java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
        var pipe = org.graalvm.polyglot.io.ProcessHandler.Redirect.PIPE;
        for (String executable : java.util.List.of(
                directory.resolve("missing").toString(), denied.toString())) {
            var command = org.graalvm.polyglot.io.ProcessHandler.ProcessCommand.create(
                    java.util.List.of(executable), "/", java.util.Map.of(), false, pipe, pipe, pipe);
            org.junit.jupiter.api.Assertions.assertThrows(IOException.class, () -> provider.start(command));
            assertNoNewChild(childrenBefore);
        }
    }

    private static java.util.Set<Long> childIds() {
        try (var children = ProcessHandle.current().children()) {
            return children.map(ProcessHandle::pid).collect(java.util.stream.Collectors.toSet());
        }
    }

    private static void assertNoNewChild(java.util.Set<Long> before) {
        try (var children = ProcessHandle.current().children()) {
            org.junit.jupiter.api.Assertions.assertTrue(
                    children.allMatch(child -> before.contains(child.pid())),
                    "a failed native launch left an unreaped OS child");
        }
    }

    private static org.graalvm.polyglot.io.ProcessHandler.ProcessCommand nativeCommand(
            List<String> arguments, String directory, java.util.Map<String, String> environment) {
        var pipe = org.graalvm.polyglot.io.ProcessHandler.Redirect.PIPE;
        return org.graalvm.polyglot.io.ProcessHandler.ProcessCommand.create(
                arguments, directory, environment, false, pipe, pipe, pipe);
    }

    @Test
    void invalidNativeAttributesFailBeforeCreationAndLeaveNoChild() throws IOException {
        var provider = new ProtosPosixProcessHandler();
        var before = childIds();
        for (var command : List.of(
                nativeCommand(List.of("/bin/echo", "bad\0argument"), "/", java.util.Map.of()),
                nativeCommand(List.of("/bin/echo", "\uD800"), "/", java.util.Map.of()),
                nativeCommand(List.of("/bin/echo"), "relative", java.util.Map.of()),
                nativeCommand(List.of("/bin/echo"), "/", java.util.Map.of("BAD=NAME", "value")))) {
            org.junit.jupiter.api.Assertions.assertThrows(IOException.class, () -> provider.start(command));
            assertNoNewChild(before);
        }
    }

    @Test
    void failedExecAndDirectoryLeaveNoChildAndTheSameHandlerCanLaunchAgain(@TempDir Path directory)
            throws Exception {
        assumeProgram("/bin/true");
        var provider = new ProtosPosixProcessHandler();
        var before = childIds();
        for (var command : List.of(
                nativeCommand(List.of(directory.resolve("missing").toString()), "/", java.util.Map.of()),
                nativeCommand(List.of("/bin/true"), directory.resolve("missing").toString(), java.util.Map.of()))) {
            org.junit.jupiter.api.Assertions.assertThrows(IOException.class, () -> provider.start(command));
            assertNoNewChild(before);
            Process child = provider.start(nativeCommand(List.of("/bin/true"), "/", java.util.Map.of()));
            try {
                org.junit.jupiter.api.Assertions.assertTrue(child.waitFor(10, TimeUnit.SECONDS));
                assertEquals(0, child.exitValue());
            } finally {
                child.destroyForcibly();
                org.junit.jupiter.api.Assertions.assertTrue(child.waitFor(10, TimeUnit.SECONDS));
                child.getOutputStream().close();
                child.getInputStream().close();
                child.getErrorStream().close();
            }
            assertNoNewChild(before);
        }
    }

    /**
     * The host identity is produced by the single creating call and cached; it stays readable
     * after reap and after all three streams released the native handle. It is host-side only.
     */
    @Test
    void creationIdentityIsPositiveAndStableAfterReapAndStreamRelease() throws Exception {
        assumeProgram("/bin/sleep");
        var provider = new ProtosPosixProcessHandler();
        var before = childIds();
        Process child = provider.start(nativeCommand(List.of("/bin/sleep", "30"), "/", java.util.Map.of()));
        long created = child.pid();
        try {
            org.junit.jupiter.api.Assertions.assertTrue(created > 0, "creation must yield a positive host identity");
            assertEquals(ProcessHandle.current().pid(),
                    ProcessHandle.of(created).flatMap(ProcessHandle::parent).map(ProcessHandle::pid).orElse(-1L),
                    "the creation identity must name this host's direct child");
        } finally {
            child.destroyForcibly();
            org.junit.jupiter.api.Assertions.assertTrue(child.waitFor(10, TimeUnit.SECONDS));
        }
        int status = child.waitFor();
        assertEquals(created, child.pid(), "identity must survive reap");
        child.getOutputStream().close();
        child.getInputStream().close();
        child.getErrorStream().close();
        // Reap plus three retired streams released the native handle; pid() must not consult it.
        assertEquals(created, child.pid(), "identity must survive release of the native handle");
        assertEquals(status, child.waitFor(), "waitFor must return the memorized result");
        assertEquals(status, child.exitValue());
        assertNoNewChild(before);
    }

    @Test
    void nonzeroExitIsSuccessfulCreationAndRetainsItsNumericHostStatus() throws Exception {
        assumeProgram("/bin/false");
        var provider = new ProtosPosixProcessHandler();
        var before = childIds();
        Process child = provider.start(nativeCommand(List.of("/bin/false"), "/", java.util.Map.of()));
        try {
            org.junit.jupiter.api.Assertions.assertTrue(child.waitFor(10, TimeUnit.SECONDS));
            assertEquals(1, child.exitValue());
            assertEquals(1, child.exitValue());
        } finally {
            child.destroyForcibly();
            org.junit.jupiter.api.Assertions.assertTrue(child.waitFor(10, TimeUnit.SECONDS));
            child.getOutputStream().close();
            child.getInputStream().close();
            child.getErrorStream().close();
        }
        assertEquals(1, child.exitValue());
        assertNoNewChild(before);
    }

    @Test
    void execFailurePublishesNoChildAndDoesNotPreventTheNextGuestLaunch(@TempDir Path directory)
            throws IOException {
        assumeProgram("/bin/echo");
        var before = childIds();
        try (ProtosStandaloneHostedSession session = open(directory, """
                probe: () => {
                    published: false
                    failed: IOError.handle(() => {
                        execution.start("/nonexistent/protos-native-exec", [], Map(), null).value()
                        published = true
                        false
                    }, (error) => { true })
                    next: execution.start("/bin/echo", ["after-error"], Map(), null).value()
                    failed && (published == false) && (drain(next.stdout()) == "after-error\\n")
                }
                """, ExecutionGrant.HOST_EXECUTION)) {
            assertSame(Boolean.TRUE, invoke(session, "probe"));
        }
        assertNoNewChild(before);
    }

    @Test
    void absentNativeLibraryFailsExplicitlyWithoutCreatingAChild(@TempDir Path directory) {
        var before = childIds();
        org.junit.jupiter.api.Assertions.assertThrows(IOException.class,
                () -> new ProtosPosixProcessHandler(directory.resolve("missing.so")));
        assertNoNewChild(before);
    }

    @Test
    void nonSearchableWorkingDirectoryFailsWithoutLeavingAChild(@TempDir Path directory) throws IOException {
        var provider = new ProtosPosixProcessHandler();
        Path denied = Files.createDirectory(directory.resolve("no-search"));
        var original = Files.getPosixFilePermissions(denied);
        var before = childIds();
        try {
            Files.setPosixFilePermissions(denied, java.util.Set.of());
            assumeTrue(!Files.isExecutable(denied), "the host principal bypasses directory permissions");
            var command = nativeCommand(List.of("/bin/true"), denied.toString(), java.util.Map.of());
            org.junit.jupiter.api.Assertions.assertThrows(IOException.class, () -> provider.start(command));
            assertNoNewChild(before);
        } finally {
            Files.setPosixFilePermissions(denied, original);
        }
    }

    @Test
    void loadableLibraryWithoutTheProcessAbiFailsAtSymbolResolution() {
        Path library = Path.of(System.getProperty("java.home"), "lib", "libjava.so");
        assumeTrue(Files.isRegularFile(library), "the Linux JDK shared library is required");
        var before = childIds();
        IOException failure = org.junit.jupiter.api.Assertions.assertThrows(IOException.class,
                () -> new ProtosPosixProcessHandler(library));
        org.junit.jupiter.api.Assertions.assertTrue(
                failure.getMessage().contains("native process symbol is missing: protos_process_start"),
                String.valueOf(failure));
        assertNoNewChild(before);
    }


    /**
     * Guest helpers: {@code matches} compares one ExitStatus with the expected exited flag, exit
     * code (null for none) and signaled flag; {@code describe} answers "io-error" when wait()
     * fails, "status-ok" when the status matches and "status-mismatch" otherwise.
     */
    static final String STATUS =
            """
            matches: (status, exited, code, signaled) => {
                codeOk: if(code === null) { status.exitCode() === null } else { status.exitCode() == code }
                (status.exited() == exited) && codeOk && (status.signaled() == signaled)
            }
            describe: (child, exited, code, signaled) => {
                IOError.handle(() => {
                    status: child.wait().value()
                    if(matches(status, exited, code, signaled)) { "status-ok" } else { "status-mismatch" }
                }, (error) => { "io-error" })
            }
            """;

    @Test
    void verifiedProviderReportsNormalExitStatusesIncluding143(@TempDir Path directory)
            throws IOException {
        assumeProgram("/bin/sh");
        try (ProtosStandaloneHostedSession session = open(directory, STATUS + """
                run: (script, code) => {
                    child: execution.start("/bin/sh", ["-c", script], Map(), null).value()
                    child.stdin().close().value()
                    describe(child, true, code, false)
                }
                probe: () => {
                    [run("exit 0", 0), run("exit 23", 23), run("exit 143", 143)]
                }
                """, ExecutionGrant.HOST_EXECUTION)) {
            assertEquals(List.of("status-ok", "status-ok", "status-ok"), texts(invoke(session, "probe")));
        }
    }

    @Test
    void verifiedProviderReportsSignalTerminationWithoutExitCode(@TempDir Path directory)
            throws IOException {
        assumeProgram("/bin/sh");
        try (ProtosStandaloneHostedSession session = open(directory, STATUS + """
                run: (script) => {
                    child: execution.start("/bin/sh", ["-c", script], Map(), null).value()
                    child.stdin().close().value()
                    describe(child, false, null, true)
                }
                probe: () => {
                    [run("kill -TERM $$"), run("kill -KILL $$")]
                }
                """, ExecutionGrant.HOST_EXECUTION)) {
            assertEquals(List.of("status-ok", "status-ok"), texts(invoke(session, "probe")));
        }
    }

    @Test
    void terminateIsARequestAndRepeatedWaitsObserveTheSameStatus(@TempDir Path directory)
            throws IOException {
        assumeProgram("/bin/sleep");
        try (ProtosStandaloneHostedSession session = open(directory, STATUS + """
                probe: () => {
                    child: execution.start("/bin/sleep", ["30"], Map(), null).value()
                    first: child.wait()
                    cancelled: child.wait()
                    cancelled.cancel()
                    child.terminate()
                    a: describe(child, false, null, true)
                    b: describe(child, false, null, true)
                    status: first.value()
                    sameStatus: matches(status, false, null, true)
                    (a == "status-ok") && (b == "status-ok") && sameStatus && (first !== child.wait())
                }
                """, ExecutionGrant.HOST_EXECUTION)) {
            assertSame(Boolean.TRUE, invoke(session, "probe"));
        }
    }

    private static List<String> texts(Object value) {
        List<String> texts = new java.util.ArrayList<>();
        for (Object element : ((com.guillermomolina.protos.runtime.ProtosArrayValue) value)
                .indexedSnapshot()) {
            texts.add(element instanceof com.guillermomolina.protos.runtime.ProtosStringValue string
                    ? string.value() : String.valueOf(element));
        }
        return texts;
    }

    @Test
    void nativeProviderNormalizesTheReapedPosixStatus() throws Exception {
        assumeProgram("/bin/sh");
        var provider = new ProtosPosixProcessHandler();
        String cwd = Path.of("").toAbsolutePath().toString();
        Process exited = provider.start(nativeCommand(List.of("/bin/sh", "-c", "exit 143"), cwd, java.util.Map.of()));
        exited.getOutputStream().close();
        assertEquals(143, exited.waitFor());
        assertEquals(143, exited.exitValue());
        Process signaled = provider.start(nativeCommand(List.of("/bin/sh", "-c", "kill -TERM $$"), cwd, java.util.Map.of()));
        signaled.getOutputStream().close();
        assertEquals(-15, signaled.waitFor());
        assertEquals(-15, signaled.exitValue());
        for (Process process : List.of(exited, signaled)) {
            process.getInputStream().close();
            process.getErrorStream().close();
        }
    }
}
