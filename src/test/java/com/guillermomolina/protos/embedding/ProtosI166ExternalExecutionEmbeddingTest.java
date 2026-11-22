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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guillermomolina.protos.execution.ProtosStandaloneHostedExecution.ExecutionGrant;
import com.guillermomolina.protos.runtime.ProtosExternalChildValue;
import com.guillermomolina.protos.runtime.ProtosExternalExecutionCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * External OS execution through the polyglot embedding selection protos.AllowExecution
 * (EXTERNAL_PROCESS.md §§1-9). Launch tests use real POSIX programs directly, never a shell.
 */
final class ProtosI166ExternalExecutionEmbeddingTest {
    private static final String CORE = Path.of("protos", "lib", "core").toAbsolutePath().toString();

    private static final String PROBE =
            """
            granted: context.hasSlot("execution")
            filesystemBound: context.hasSlot("filesystem")
            drain: (endpoint) => {
                text: ""
                chunk: endpoint.read(65536).value()
                while(chunk !== null) {
                    text = text + Encoding.UTF8.decode(chunk)
                    chunk = endpoint.read(65536).value()
                }
                text
            }
            0
            """;

    private static Context.Builder builder() {
        return Context.newBuilder(ProtosLanguage.ID)
                .option("protos.CoreRoot", CORE)
                .allowIO(IOAccess.NONE);
    }

    private static Context granted() {
        try {
            return builder()
                    .option("protos.AllowExecution", "true")
                    .allowCreateProcess(true)
                    .allowCreateThread(true)
                    .allowIO(IOAccess.ALL)
                    .processHandler(new ProtosPosixProcessHandler())
                    .build();
        } catch (IOException unavailable) {
            throw new java.io.UncheckedIOException(unavailable);
        }
    }

    private static Context grantedWithJdkProvider() {
        return builder()
                .option("protos.AllowExecution", "true")
                .allowCreateProcess(true)
                .allowCreateThread(true)
                .allowIO(IOAccess.ALL)
                .build();
    }

    private static boolean probeGranted(Context context) {
        context.eval(ProtosLanguage.ID, PROBE);
        return context.getBindings(ProtosLanguage.ID).getMember("granted").asBoolean();
    }

    /**
     * The grant is a bootstrap local of the first evaluation only, so the probe and the body are
     * evaluated together once; the body Closure captures that module context lexically.
     */
    private static Value run(Context context, String body) {
        context.eval(ProtosLanguage.ID, PROBE.replace("0\n", "") + "run: () => {\n" + body + "\n}\n0");
        return context.getBindings(ProtosLanguage.ID).getMember("run").execute();
    }

    private static void assumeProgram(String path) {
        assumeTrue(Files.isExecutable(Path.of(path)), path + " is required");
    }

    @Test
    void absentByDefaultAndNotImpliedByOtherPermissions() {
        try (Context implicit = builder().build()) {
            assertFalse(probeGranted(implicit));
        }
        try (Context all = Context.newBuilder(ProtosLanguage.ID)
                .option("protos.CoreRoot", CORE).allowAllAccess(true).build()) {
            assertFalse(probeGranted(all));
        }
        try (Context process = builder().allowCreateProcess(true).build()) {
            assertFalse(probeGranted(process));
        }
        try (Context others = builder()
                .option("protos.AllowEntropy", "true")
                .option("protos.AllowClock", "true")
                .build()) {
            assertFalse(probeGranted(others));
        }
    }

    @Test
    void selectionNeverWidensHostProcessPermission() {
        try (Context context = builder().option("protos.AllowExecution", "true").build()) {
            assertFalse(probeGranted(context));
        }
        // Host process creation alone suffices; the grant neither needs nor confers file access.
        try (Context context = builder()
                .option("protos.AllowExecution", "true").allowCreateProcess(true).build()) {
            assertTrue(probeGranted(context));
            assertFalse(context.getBindings(ProtosLanguage.ID).getMember("filesystemBound").asBoolean());
        }
    }

    @Test
    void grantIsRootLocalAndGrantsNothingElse() {
        try (Context context = granted()) {
            ProtosEmbeddedModules.install(context, Map.of(
                    "app:execution-probe", "this.hadExecution: context.hasSlot(\"execution\")\n"));
            assertTrue(run(context, """
                    probe: import("app:execution-probe")
                    granted && !probe.hadExecution
                    """).asBoolean());
        }
    }

    @Test
    void imitationsAreNotAuthority() {
        try (Context context = granted()) {
            assertTrue(run(context, """
                    forged: execution {}
                    InvalidIOArgument.handle(() => {
                        forged.start("/bin/echo", [], Map(), null).value()
                        false
                    }, (error) => { true })
                    """).asBoolean());
        }
    }

    @Test
    void startIsExactlyFourArguments() {
        try (Context context = granted()) {
            assertTrue(run(context, """
                    Error.handle(() => { execution.start("/bin/echo", [], Map()); false },
                            (error) => { true })
                    """).asBoolean());
        }
    }

    @Test
    void semanticInvalidityFailsBeforeAnyEffect() {
        try (Context context = granted()) {
            assertTrue(run(context, """
                    invalid: (thunk) => {
                        InvalidIOArgument.handle(() => { thunk().value(); false }, (error) => { true })
                    }
                    invalid(() => { execution.start("bin/echo", [], Map(), null) }) &&
                        invalid(() => { execution.start(5, [], Map(), null) }) &&
                        invalid(() => { execution.start("/bin/echo", [1], Map(), null) }) &&
                        invalid(() => { execution.start("/bin/echo", "x", Map(), null) }) &&
                        invalid(() => { execution.start("/bin/echo", [], [], null) }) &&
                        invalid(() => { execution.start("/bin/echo", [], Map(), 7) }) &&
                        invalid(() => { execution.start("", [], Map(), null) })
                    """).asBoolean());
        }
    }

    @Test
    void missingExecutableFailsWithIOError() {
        try (Context context = granted()) {
            assertEquals("io-error", run(context, """
                    pending: execution.start("/nonexistent/protos-missing-program", [], Map(), null)
                    IOError.handle(() => {
                        InvalidIOArgument.handle(() => {
                            pending.value()
                            "resolved"
                        }, (error) => { "invalid-argument" })
                    }, (error) => { "io-error" })
                    """).asString());
        }
    }

    @Test
    void argumentsArePassedExactlyWithoutShell() {
        assumeProgram("/bin/echo");
        try (Context context = granted()) {
            assertEquals("a b ñ $HOME *\n", run(context, """
                    child: execution.start("/bin/echo", ["a b", "ñ", "$HOME", "*"], Map(), null).value()
                    drain(child.stdout())
                    """).asString());
        }
    }

    @Test
    void environmentIsCompleteAndExplicit() {
        assumeProgram("/usr/bin/env");
        try (Context context = granted()) {
            assertEquals("", run(context, """
                    child: execution.start("/usr/bin/env", [], Map(), null).value()
                    drain(child.stdout())
                    """).asString());
        }
        try (Context context = granted()) {
            assertEquals("ONLY=value\n", run(context, """
                    environment: Map()
                    environment["ONLY"] = "value"
                    child: execution.start("/usr/bin/env", [], environment, null).value()
                    environment["LATER"] = "ignored"
                    drain(child.stdout())
                    """).asString());
        }
    }

    @Test
    void stdinRoundTripAndEndpointIdentity() {
        assumeProgram("/bin/cat");
        try (Context context = granted()) {
            assertEquals("payload", run(context, """
                    child: execution.start("/bin/cat", [], Map(), null).value()
                    same: (child.stdin() === child.stdin()) && (child.stdout() === child.stdout())
                    child.stdin().write(Encoding.UTF8.encode("payload")).value()
                    child.stdin().close().value()
                    if(same) { drain(child.stdout()) } else { "identity lost" }
                    """).asString());
        }
    }

    @Test
    void outputIsReadableAfterTheChildExited() {
        assumeProgram("/bin/echo");
        try (Context context = granted()) {
            assertTrue(run(context, """
                    child: execution.start("/bin/echo", ["after"], Map(), null).value()
                    exited: IOError.handle(() => { child.wait().value(); false }, (error) => { true })
                    exited && (drain(child.stdout()) == "after\\n")
                    """).asBoolean());
        }
    }

    /**
     * Guest helpers local to a run body. {@code tracedDrain} reads until EOF or a failed read and
     * answers {@code [text, state]} with state "eof" or "read-error"; {@code readExactly} reads
     * until {@code count} characters arrived and answers state "complete", "eof" or "read-error".
     * The fragments read through them are ASCII, so characters and bytes coincide.
     */
    private static final String TRACED_READS =
            """
            tracedDrain: (endpoint) => {
                text: ""
                state: "reading"
                chunk: null
                while(state == "reading") {
                    chunk = Error.handle(() => { endpoint.read(65536).value() }, (error) => { false })
                    if(chunk === false) {
                        state = "read-error"
                    } else {
                        if(chunk === null) { state = "eof" } else { text = text + Encoding.UTF8.decode(chunk) }
                    }
                }
                [text, state]
            }
            readExactly: (endpoint, count) => {
                text: ""
                state: "reading"
                chunk: null
                while(state == "reading") {
                    chunk = Error.handle(() => { endpoint.read(count - text.size()).value() }, (error) => { false })
                    if(chunk === false) {
                        state = "read-error"
                    } else {
                        if(chunk === null) {
                            state = "eof"
                        } else {
                            text = text + Encoding.UTF8.decode(chunk)
                            if(text.size() >= count) { state = "complete" } else { null }
                        }
                    }
                }
                [text, state]
            }
            """;

    /**
     * Guest wait() barrier: "io-error", "resolved" or "other-error". These Contexts are built by the
     * embedder, whose process handler Protos cannot verify, so "io-error" is the expected outcome.
     */
    private static final String WAIT_BARRIER =
            """
            waited: Error.handle(() => {
                IOError.handle(() => { child.wait().value(); "resolved" }, (error) => { "io-error" })
            }, (error) => { "other-error" })
            """;

    private static final String DESCENDANT_SCRIPT = "(/bin/sleep 1; printf late) & printf early";

    /** Splits a guest result joined with "|"; the last field is free text and may be empty. */
    private static String[] fields(Value result, int count) {
        String[] parts = result.asString().split("\\|", count);
        assertEquals(count, parts.length, "guest result: " + result.asString());
        return parts;
    }

    private static String jvm() {
        return System.getProperty("java.vm.name") + " " + System.getProperty("java.vm.version")
                + " (" + System.getProperty("java.vendor") + ", java " + System.getProperty("java.version") + ")";
    }

    private static String describeBytes(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return "\"" + text.replace("\n", "\\n") + "\" length=" + text.length()
                + " bytes=" + Arrays.toString(bytes);
    }

    /**
     * Normative (EXTERNAL_PROCESS.md §6): exit of the direct child is not EOF while a descendant
     * keeps the inherited stdout writer open, so the result is exactly "earlylate"; "early" is
     * wrong. On failure the message names the phase (child wait, stdout read, read completion),
     * the bytes actually received, their length, the observation time and the JVM, and appends a
     * diagnostic run of the same scenario through a plain java.lang.ProcessBuilder. That JDK
     * observation is information only; it never makes an incorrect result acceptable.
     */
    @Test
    void descendantKeepsStdoutOpenAfterTheDirectChildExited() {
        assumeProgram("/bin/sh");
        assumeProgram("/bin/sleep");
        try (Context context = granted()) {
            long started = System.nanoTime();
            String[] result = fields(run(context, TRACED_READS
                    + "child: execution.start(\"/bin/sh\", [\"-c\", \"" + DESCENDANT_SCRIPT
                    + "\"], Map(), null).value()\n"
                    + WAIT_BARRIER + """
                    traced: tracedDrain(child.stdout())
                    waited + "|" + traced[1] + "|" + traced[0]
                    """), 3);
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            String waited = result[0];
            String readState = result[1];
            String received = result[2];
            String phase = "other-error".equals(waited) ? "child wait"
                    : "read-error".equals(readState) ? "stdout read"
                    : "read completion (EOF after " + received.length() + " chars)";
            Supplier<String> diagnostic = () -> "phase=" + phase
                    + " wait=" + waited
                    + " readState=" + readState
                    + " received=" + describeBytes(received)
                    + " elapsedMillis=" + elapsedMillis
                    + " jvm=" + jvm()
                    + " | jdkProcessBuilderDiagnostic: " + jdkDescendantDiagnostic();
            assertFalse("other-error".equals(waited), diagnostic);
            assertEquals("eof", readState, diagnostic);
            assertEquals("earlylate", received, diagnostic);
        }
    }

    /**
     * Control A: the direct child stays alive (blocked reading stdin) while it emits two
     * fragments, so no exit-time stream withdrawal can intervene. Exact, ordered delivery here
     * isolates the transport; a failure is a Protos transfer defect or a fixture defect.
     */
    @Test
    void liveDirectChildDeliversBothFragmentsInOrder() {
        assumeProgram("/bin/sh");
        try (Context context = granted()) {
            String[] result = fields(run(context, TRACED_READS + """
                    child: execution.start("/bin/sh", ["-c", "printf early; read first; printf late; read second"], Map(), null).value()
                    early: readExactly(child.stdout(), 5)
                    child.stdin().write(Encoding.UTF8.encode("go\\n")).value()
                    late: readExactly(child.stdout(), 4)
                    child.stdin().close().value()
                    rest: tracedDrain(child.stdout())
                    early[1] + "|" + late[1] + "|" + rest[1] + "|" + early[0] + "|" + late[0] + "|" + rest[0]
                    """), 6);
            String observed = "earlyState=" + result[0] + " lateState=" + result[1]
                    + " restState=" + result[2] + " early=" + describeBytes(result[3])
                    + " late=" + describeBytes(result[4]) + " rest=" + describeBytes(result[5])
                    + " jvm=" + jvm();
            assertAll(
                    () -> assertEquals("complete", result[0], "EOF before the first fragment; " + observed),
                    () -> assertEquals("early", result[3], observed),
                    () -> assertEquals("complete", result[1], "EOF before the second fragment; " + observed),
                    () -> assertEquals("late", result[4], observed),
                    () -> assertEquals("eof", result[2], observed),
                    () -> assertEquals("", result[5], observed));
        }
    }

    /**
     * Control B: the direct child exits and a descendant keeps stdout open. Without any sleep, the
     * descendant writes its fragment only after it observed that its parent, the direct child,
     * no longer exists (kill -0 fails once the parent was reaped), and the guest additionally
     * passes the wait() barrier first. The second fragment must be received before EOF.
     */
    @Test
    void descendantFragmentAfterTheDirectChildExitedArrivesBeforeEof() {
        assumeProgram("/bin/sh");
        try (Context context = granted()) {
            long started = System.nanoTime();
            String[] result = fields(run(context, TRACED_READS + """
                    child: execution.start("/bin/sh", ["-c", "(while kill -0 $$ 2>/dev/null; do :; done; printf late) & printf early"], Map(), null).value()
                    """ + WAIT_BARRIER + """
                    early: readExactly(child.stdout(), 5)
                    rest: tracedDrain(child.stdout())
                    waited + "|" + early[1] + "|" + rest[1] + "|" + early[0] + "|" + rest[0]
                    """), 5);
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            String observed = "wait=" + result[0] + " earlyState=" + result[1]
                    + " restState=" + result[2] + " early=" + describeBytes(result[3])
                    + " rest=" + describeBytes(result[4]) + " elapsedMillis=" + elapsedMillis
                    + " jvm=" + jvm();
            assertAll(
                    () -> assertFalse("other-error".equals(result[0]), "child wait; " + observed),
                    () -> assertEquals("complete", result[1], "EOF before the first fragment; " + observed),
                    () -> assertEquals("early", result[3], observed),
                    () -> assertEquals("eof", result[2], "stdout read; " + observed),
                    () -> assertEquals("late", result[4],
                            "descendant fragment lost: exit of the direct child was turned into EOF; "
                                    + observed));
        }
    }

    /**
     * Diagnostic only, never a backend: runs {@link #DESCENDANT_SCRIPT} through a plain
     * java.lang.ProcessBuilder, waits for the direct child to exit, then reads its stdout until
     * the JDK stream reports EOF. Reports what was obtained, whether the descendant fragment
     * arrived and how long the read took. Every wait is bounded.
     */
    private static String jdkDescendantDiagnostic() {
        Process process = null;
        try {
            process = new ProcessBuilder("/bin/sh", "-c", DESCENDANT_SCRIPT)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            process.getOutputStream().close();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                return "direct child did not exit within 10 s";
            }
            long readStarted = System.nanoTime();
            CompletableFuture<byte[]> read = CompletableFuture.supplyAsync(
                    readAll(process.getInputStream()));
            byte[] bytes = read.get(10, TimeUnit.SECONDS);
            long readMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - readStarted);
            String output = new String(bytes, StandardCharsets.UTF_8);
            return "outputAfterDirectExit=" + describeBytes(output)
                    + " descendantFragmentReceived=" + output.contains("late")
                    + " readMillis=" + readMillis;
        } catch (Exception failure) {
            return "diagnostic failed: " + failure;
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
        }
    }

    private static java.util.function.Supplier<byte[]> readAll(java.io.InputStream input) {
        return () -> {
            try (input) {
                return input.readAllBytes();
            } catch (IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
        };
    }

    @Test
    void closingAnEndpointNeverTerminatesTheChild() {
        assumeProgram("/bin/cat");
        try (Context context = granted()) {
            assertEquals("still-running", run(context, """
                    child: execution.start("/bin/cat", [], Map(), null).value()
                    child.stderr().close().value()
                    child.stdin().write(Encoding.UTF8.encode("still-running")).value()
                    child.stdin().close().value()
                    drain(child.stdout())
                    """).asString());
        }
    }

    @Test
    void cancelledReadRecoversTheExactBytes() {
        assumeProgram("/bin/cat");
        try (Context context = granted()) {
            assertEquals("0123456789", run(context, """
                    child: execution.start("/bin/cat", [], Map(), null).value()
                    pending: child.stdout().read(4)
                    pending.cancel()
                    Cancelled.handle(() => { pending.value() }, (error) => { null })
                    child.stdin().write(Encoding.UTF8.encode("0123456789")).value()
                    child.stdin().close().value()
                    drain(child.stdout())
                    """).asString());
        }
    }

    @Test
    void multipleChildrenInMultipleContextsStayIndependent() {
        assumeProgram("/bin/cat");
        try (Context first = granted(); Context second = granted()) {
            String body = """
                    a: execution.start("/bin/cat", [], Map(), null).value()
                    b: execution.start("/bin/cat", [], Map(), null).value()
                    a.stdin().write(Encoding.UTF8.encode("%s-a")).value()
                    b.stdin().write(Encoding.UTF8.encode("%s-b")).value()
                    a.stdin().close().value()
                    b.stdin().close().value()
                    drain(a.stdout()) + drain(b.stdout())
                    """;
            assertEquals("one-aone-b", run(first, body.formatted("one", "one")).asString());
            assertEquals("two-atwo-b", run(second, body.formatted("two", "two")).asString());
        }
    }

    @Test
    void contextCloseReclaimsALiveChildWithoutLeavingCustody() {
        assumeProgram("/bin/sleep");
        Context context = granted();
        run(context, """
                execution.start("/bin/sleep", ["30"], Map(), null).value()
                true
                """);
        // The live child is terminated and reaped within the bounded host close; a close that
        // could not establish it would not be reported as clean.
        context.close();
    }

    @Test
    void stderrIsIndependentOfStdout() {
        assumeProgram("/bin/ls");
        try (Context context = granted()) {
            assertTrue(run(context, """
                    child: execution.start("/bin/ls", ["/nonexistent-protos-directory"], Map(), null).value()
                    (drain(child.stdout()) == "") && (drain(child.stderr()).size() > 0)
                    """).asBoolean());
        }
    }

    @Test
    void javaBackendWaitFailsWithIOErrorInsteadOfInferringStatus() {
        assumeProgram("/bin/echo");
        try (Context context = grantedWithJdkProvider()) {
            assertTrue(run(context, """
                    child: execution.start("/bin/echo", [], Map(), null).value()
                    drain(child.stdout())
                    first: IOError.handle(() => { child.wait().value(); false }, (error) => { true })
                    second: IOError.handle(() => { child.wait().value(); false }, (error) => { true })
                    first && second && (child.wait() !== child.wait())
                    """).asBoolean());
        }
    }

    @Test
    void terminateIsRepeatableBestEffortRequest() {
        assumeProgram("/bin/sleep");
        try (Context context = granted()) {
            assertTrue(run(context, """
                    child: execution.start("/bin/sleep", ["30"], Map(), null).value()
                    child.terminate().value()
                    child.terminate().value()
                    IOError.handle(() => { child.wait().value(); false }, (error) => { true })
                    """).asBoolean());
        }
    }

    @Test
    void grantChildrenAndEndpointsNeverEnterParallelExecution() {
        assumeProgram("/bin/cat");
        try (Context context = granted()) {
            assertTrue(run(context, """
                    denied: (value) => {
                        NonParallelValue.handle(() => { ((x) => { x }).parallel(value).value(); false },
                                (error) => { true })
                    }
                    child: execution.start("/bin/cat", [], Map(), null).value()
                    result: denied(execution) && denied(child) && denied(child.stdin()) &&
                            denied(child.stdout())
                    child.stdin().close().value()
                    result
                    """).asBoolean());
        }
    }

    @Test
    void contextsAreIndependentlyAuthorized() {
        try (Context first = granted(); Context second = builder().build()) {
            assertTrue(probeGranted(first));
            assertFalse(probeGranted(second));
        }
    }

    @Test
    void outputProducedBeforeExitIsStillDelivered() {
        assumeProgram("/bin/ls");
        try (Context context = granted()) {
            assertTrue(run(context, """
                    child: execution.start("/bin/ls", ["-d", "/", "/nonexistent-protos-i166"], Map(), null).value()
                    exited: IOError.handle(() => { child.wait().value(); false }, (error) => { true })
                    exited && (drain(child.stdout()) == "/\\n") && (drain(child.stderr()).size() > 0)
                    """).asBoolean());
        }
    }

    @Test
    void cancelledBlockedReadNeverRacesTheNextRead() {
        assumeProgram("/bin/cat");
        try (Context context = granted()) {
            assertTrue(run(context, """
                    child: execution.start("/bin/cat", [], Map(), null).value()
                    pending: child.stdout().read(2)
                    pending.cancel()
                    cancelled: Cancelled.handle(() => { pending.value(); false }, (error) => { true })
                    child.stdin().write(Encoding.UTF8.encode("ordered")).value()
                    child.stdin().close().value()
                    cancelled && (drain(child.stdout()) == "ordered")
                    """).asBoolean());
        }
    }

    @Test
    void contextCloseReclaimsLiveAndExitedChildren() {
        assumeProgram("/bin/sleep");
        assumeProgram("/bin/echo");
        // Neither the live child nor the exited child with unread, unclosed endpoints may make
        // the Context close fail; both are reclaimed by the bounded host close.
        try (Context context = granted()) {
            assertTrue(run(context, """
                    execution.start("/bin/sleep", ["30"], Map(), null).value()
                    exited: execution.start("/bin/echo", ["unread"], Map(), null).value()
                    IOError.handle(() => { exited.wait().value(); false }, (error) => { true })
                    """).asBoolean());
        }
    }

    @Test
    void environmentNamesFollowTheHostDomainWithoutInventedFolding() {
        assumeProgram("/usr/bin/env");
        try (Context context = granted()) {
            String output = run(context, """
                    environment: Map()
                    environment["PROTOS_I166"] = "upper"
                    environment["protos_i166"] = "lower"
                    child: execution.start("/usr/bin/env", [], environment, null).value()
                    drain(child.stdout())
                    """).asString();
            // On this POSIX host native names are case-sensitive, so both entries are distinct.
            assertTrue(output.contains("PROTOS_I166=upper\n"), output);
            assertTrue(output.contains("protos_i166=lower\n"), output);
        }
    }

    /**
     * A committed launch that is still in flight when the Process terminates keeps the host
     * custody: the Process Context is not closed (Context.close would close it before reporting
     * a live child), the refusal is reported and attributed to that Context only, and once the
     * launch ends a retry performs the first real close, running the close callback once.
     */
    @Test
    void refusedCloseIsRetriedOnlyAfterCustodyIsDischarged(@TempDir Path directory)
            throws IOException {
        ProtosStandaloneHostedSession session =
                ProtosI166ExternalExecutionLaunchTest.open(
                        directory, "probe: () => { true }", ExecutionGrant.HOST_EXECUTION);
        ProtosProcessRuntime process = session.processForTesting();
        ProtosPolyglotRuntimeHost host = session.runtimeHostForTesting();
        ProtosPolyglotProcessContext processContext = session.processContextForTesting();
        ProtosExternalExecutionCapabilityValue grant = process.rootExecutionForRuntime().orElseThrow();
        assertSame(Boolean.TRUE, ProtosI166ExternalExecutionLaunchTest.invoke(session, "probe"));
        assertTrue(grant.beginLaunchForRuntime());
        try {
            // First close: refused, not reported as clean, Context still counted as active.
            assertThrows(IllegalStateException.class, session::close);
            assertTrue(processContext.closeRefusedForExternalCustodyForRuntime());
            assertFalse(processContext.isClosedForTesting());
            assertEquals(1, host.activeProcessContextCountForTesting());
            assertEquals(1, host.refusedProcessContextCountForTesting());
            assertTrue(grant.retiredForRuntime());
            // While the obligation remains, a retry neither closes nor drops the Context.
            assertEquals(1, host.retryRefusedProcessContextClosesForRuntime());
            assertFalse(processContext.isClosedForTesting());
        } finally {
            grant.endLaunchForRuntime();
        }
        // The custody is discharged: the retry really closes the Context, exactly once.
        assertEquals(0, host.retryRefusedProcessContextClosesForRuntime());
        assertTrue(processContext.isClosedForTesting());
        assertFalse(processContext.closeRefusedForExternalCustodyForRuntime());
        assertEquals(0, host.activeProcessContextCountForTesting());
        assertEquals(0, host.retryRefusedProcessContextClosesForRuntime());
        assertEquals(0, host.activeProcessContextCountForTesting());
        assertFalse(process.externalCustodyOutstandingForRuntime());
        // The refusal was never recorded host-wide, so the host now closes cleanly.
        host.close();
    }

    @Test
    void refusedCloseOfOneContextLeavesAnotherIsolated(@TempDir Path directory)
            throws IOException {
        Path first = Files.createDirectories(directory.resolve("first"));
        Path second = Files.createDirectories(directory.resolve("second"));
        ProtosStandaloneHostedSession refused =
                ProtosI166ExternalExecutionLaunchTest.open(
                        first, "probe: () => { true }", ExecutionGrant.HOST_EXECUTION);
        ProtosExternalExecutionCapabilityValue grant =
                refused.processForTesting().rootExecutionForRuntime().orElseThrow();
        assertTrue(grant.beginLaunchForRuntime());
        try {
            assertThrows(IllegalStateException.class, refused::close);
            try (ProtosStandaloneHostedSession clean =
                    ProtosI166ExternalExecutionLaunchTest.open(
                            second, "probe: () => { true }", ExecutionGrant.HOST_EXECUTION)) {
                assertSame(Boolean.TRUE, ProtosI166ExternalExecutionLaunchTest.invoke(clean, "probe"));
            }
            assertFalse(refused.processContextForTesting().isClosedForTesting());
        } finally {
            grant.endLaunchForRuntime();
        }
        ProtosPolyglotRuntimeHost host = refused.runtimeHostForTesting();
        assertEquals(0, host.retryRefusedProcessContextClosesForRuntime());
        host.close();
    }

    /** Native child stand-in, already reaped, whose transport retires only when told to. */
    private static ProtosExternalChildValue.NativeChild reapedWithPumps(CompletableFuture<Void> pumps) {
        return new ProtosExternalChildValue.NativeChild() {
            @Override
            public com.guillermomolina.protos.runtime.ProtosByteIoFlow.Backend lane(
                    ProtosExternalChildValue.Stream stream) {
                throw new IllegalStateException("no guest transfer in this test");
            }

            @Override
            public CompletionStage<Boolean> release(
                    ProtosExternalChildValue.Stream stream, boolean discard) {
                return CompletableFuture.completedFuture(true);
            }

            @Override
            public CompletionStage<ProtosExternalChildValue.TerminalOutcome> termination() {
                return CompletableFuture.completedFuture(
                        ProtosExternalChildValue.TerminalOutcome.unknown());
            }

            @Override
            public CompletionStage<Void> transportRetired() {
                return pumps;
            }

            @Override
            public void requestTermination() {}

            @Override
            public void requestForcedTermination() {}
        };
    }

    /**
     * Closing the embedding Context while a reaped child still has a live pump: the bounded host
     * reclamation is not established, so the Process keeps the child in custody after the close
     * instead of reporting it reclaimed, and the grant keeps its workers. The custody is
     * discharged later, when the transport retires. No retry of the embedding Context close
     * exists or is attempted: the remaining obligation is held by the Process registry only.
     */
    @Test
    void contextCloseKeepsCustodyWhileAPumpOfAReapedChildLives() {
        CompletableFuture<Void> pumps = new CompletableFuture<>();
        ProtosProcessRuntime process;
        ProtosExternalExecutionCapabilityValue grant;
        ProtosExternalChildValue child;
        Context context = granted();
        try {
            context.eval(ProtosLanguage.ID, "0");
            context.enter();
            try {
                process = ProtosLanguageContext.current().embeddedProcessOrNull().processForTesting();
            } finally {
                context.leave();
            }
            grant = process.rootExecutionForRuntime().orElseThrow();
            child = grant.newChildForRuntime(process, reapedWithPumps(pumps));
            assertTrue(process.registerExternalChildForRuntime(child));
            assertTrue(child.reapedForRuntime());
        } finally {
            context.close();
        }
        assertTrue(process.externalChildrenForRuntime().contains(child));
        assertTrue(process.externalCustodyOutstandingForRuntime());
        assertFalse(child.custodyReleasedForRuntime());
        assertTrue(grant.executeForRuntime(() -> {}));

        pumps.complete(null);
        assertTrue(child.custodyReleasedForRuntime());
        assertFalse(process.externalCustodyOutstandingForRuntime());
        assertTrue(process.reclaimExternalChildrenForHostClose(1));
        assertFalse(grant.executeForRuntime(() -> {}));
    }

    private static void awaitOutput(
            java.io.ByteArrayOutputStream output, String marker,
            CompletableFuture<String> running) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!output.toString(StandardCharsets.UTF_8).contains(marker)) {
            if (running.isDone()) {
                org.junit.jupiter.api.Assertions.fail("guest completed before " + marker + ": " + running.get());
            }
            assertTrue(System.nanoTime() < deadline, "guest did not publish " + marker);
            Thread.sleep(5);
        }
    }

    @Test
    void explicitProviderKeepsReadsPendingAfterReapAndClosesEachOutputIndependently(
            @TempDir Path directory) throws Exception {
        assumeProgram("/bin/sh");
        assumeProgram("/bin/sleep");
        Path stdoutGate = directory.resolve("stdout");
        Path stderrGate = directory.resolve("stderr");
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        try (Context context = builder()
                .option("protos.AllowExecution", "true")
                .allowCreateProcess(true)
                .allowCreateThread(true)
                .allowIO(IOAccess.ALL)
                .processHandler(new ProtosPosixProcessHandler())
                .out(output)
                .build()) {
            String gates = stdoutGate.toString().replace("\\", "\\\\").replace("\"", "\\\"")
                    + "\", \"" + stderrGate.toString().replace("\\", "\\\\").replace("\"", "\\\"");
            String source = """
                    child: execution.start("/bin/sh",
                        ["-c", "(while [ ! -e \\"$1\\" ]; do /bin/sleep 0.01; done; printf late; exec 1>&-; while [ ! -e \\"$2\\" ]; do /bin/sleep 0.01; done; printf err >&2) & printf early",
                         "fixture", "%s"], Map(), null).value()
                    child.stdin().close().value()
                    """.formatted(gates) + WAIT_BARRIER + """
                    process.stdout().write(Encoding.UTF8.encode("reaped")).value()
                    stdout: drain(child.stdout())
                    process.stdout().write(Encoding.UTF8.encode("stdout-eof")).value()
                    stderr: drain(child.stderr())
                    stdout + "|" + stderr
                    """;
            CompletableFuture<String> running = CompletableFuture.supplyAsync(
                    () -> run(context, source).asString());
            try {
                awaitOutput(output, "reaped", running);
                assertThrows(java.util.concurrent.TimeoutException.class,
                        () -> running.get(100, TimeUnit.MILLISECONDS));
                Files.writeString(stdoutGate, "release");
                awaitOutput(output, "stdout-eof", running);
                assertThrows(java.util.concurrent.TimeoutException.class,
                        () -> running.get(100, TimeUnit.MILLISECONDS));
            } finally {
                Files.writeString(stdoutGate, "release");
                Files.writeString(stderrGate, "release");
                running.handle((value, failure) -> null).get(10, TimeUnit.SECONDS);
            }
            assertEquals("earlylate|err", running.get(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void jdkProviderDescendantBehaviourIsRecordedSeparately() {
        assumeProgram("/bin/sh");
        assumeProgram("/bin/sleep");
        String diagnostic = jdkDescendantDiagnostic();
        System.out.println("JDK_PROCESS_PROVIDER " + diagnostic);
        assertTrue(diagnostic.startsWith("outputAfterDirectExit="), diagnostic);
    }

    @Test
    void installingTheProviderNeverGrantsProcessCreation() throws IOException {
        ProtosPosixProcessHandler provider = new ProtosPosixProcessHandler();
        java.util.concurrent.atomic.AtomicInteger starts = new java.util.concurrent.atomic.AtomicInteger();
        try (Context context = builder()
                .option("protos.AllowExecution", "true")
                .allowIO(IOAccess.ALL)
                .processHandler(command -> {
                    starts.incrementAndGet();
                    return provider.start(command);
                })
                .build()) {
            assertFalse(probeGranted(context));
            assertEquals(0, starts.get());
        }
    }

    /**
     * A Context with the explicit POSIX provider and no guest I/O: only the two execution
     * selections vary. Never replace NONE with ALL to make a caller pass.
     */
    private static Context posixWithoutIo(boolean allowExecution, boolean allowCreateProcess)
            throws IOException {
        return builder()
                .option("protos.AllowExecution", Boolean.toString(allowExecution))
                .allowCreateProcess(allowCreateProcess)
                .allowCreateThread(true)
                .allowIO(IOAccess.NONE)
                .processHandler(new ProtosPosixProcessHandler())
                .build();
    }

    /** Strict independent-authority regression; never replace NONE with ALL to make it pass. */
    @Test
    void explicitPosixExecutionWorksWithoutGrantingFilesystem() throws IOException {
        assumeProgram("/bin/echo");
        assumeProgram("/usr/bin/printf");
        try (Context context = Context.newBuilder(ProtosLanguage.ID)
                .option("protos.AllowExecution", "true")
                .allowCreateProcess(true)
                .allowCreateThread(true)
                .allowIO(IOAccess.NONE)
                .processHandler(new ProtosPosixProcessHandler())
                .build()) {
            assertEquals("independent\n|a b||c|eof", run(context, """
                    if(!granted) {
                        "execution-grant-absent"
                    } else {
                        if(context.hasSlot("filesystem")) {
                            "unexpected-filesystem-grant"
                        } else {
                            child: execution.start("/bin/echo", ["independent"], Map(), null).value()
                            first: drain(child.stdout())
                            exact: execution.start("/usr/bin/printf", ["%s|", "a b", "", "c"], Map(), null).value()
                            second: drain(exact.stdout())
                            atEnd: exact.stdout().read(1).value() === null
                            ending: "data-after-eof"
                            if(atEnd) {
                                ending = "eof"
                            }
                            first + "|" + second + ending
                        }
                    }
                    """).asString(), "POSIX execution must work with IOAccess.NONE and no guest Filesystem grant");
        }
    }

    @Test
    void hostProcessDenialWithholdsExecutionEvenWithThePosixProvider() throws IOException {
        try (Context context = posixWithoutIo(true, false)) {
            assertFalse(probeGranted(context));
        }
    }

    @Test
    void executionDenialWithholdsTheGrantDespiteHostProcessPermission() throws IOException {
        try (Context context = posixWithoutIo(false, true)) {
            assertFalse(probeGranted(context));
        }
    }

    @Test
    void contextsWithoutIoStayIndependentlyAuthorizedAndCloseIndependently() throws IOException {
        assumeProgram("/bin/cat");
        try (Context authorized = posixWithoutIo(true, true)) {
            Context unauthorized = posixWithoutIo(false, true);
            // The authorized Context's child stays live in module state while the other closes.
            // This is the Context's first evaluation, so its module receives the grant.
            authorized.eval(ProtosLanguage.ID, PROBE.replace("0\n", "") + """
                    held: null
                    begin: () => {
                        held = execution.start("/bin/cat", [], Map(), null).value()
                        "started"
                    }
                    finish: () => {
                        held.stdin().write(Encoding.UTF8.encode("alive")).value()
                        held.stdin().close().value()
                        drain(held.stdout())
                    }
                    0
                    """);
            Value bindings = authorized.getBindings(ProtosLanguage.ID);
            assertEquals("started", bindings.getMember("begin").execute().asString());
            // A child launched by the authorized Context confers nothing on the other one.
            assertFalse(probeGranted(unauthorized));
            unauthorized.close();
            assertEquals("alive", bindings.getMember("finish").execute().asString(),
                    "closing an unauthorized Context must not terminate another Context's child");
        }
    }

    @Test
    void installedPosixProviderDoesNotOverrideExplicitExecutionDenial() throws IOException {
        try (Context context = builder()
                .option("protos.AllowExecution", "false")
                .allowCreateProcess(true)
                .allowIO(IOAccess.ALL)
                .processHandler(new ProtosPosixProcessHandler())
                .build()) {
            assertFalse(probeGranted(context));
        }
    }

}
