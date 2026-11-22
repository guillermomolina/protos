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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.execution.ProtosStandaloneHostedExecution.ExecutionGrant;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosByteIoFlow;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Standard endpoints of standalone children (EXTERNAL_PROCESS.md §6, BYTE_IO.md): exit is not EOF,
 * stdout and stderr are independent, a cancelled blocked read never races a later read, blocked
 * reads never starve the writes that would unblock them, and buffering stays finite.
 * The standalone host installs its native POSIX provider, so wait() resolves with a verified
 * ExitStatus after the reap; it never waits for, or implies, EOF on any endpoint.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
final class ProtosI166ExternalExecutionStreamsTest {

    private static ProtosStandaloneHostedSession open(Path directory, String source)
            throws IOException {
        return ProtosI166ExternalExecutionLaunchTest.open(
                directory, source, ExecutionGrant.HOST_EXECUTION);
    }

    /**
     * Guest helper: reads {@code endpoint} until EOF or a read failure and answers
     * {@code [text, state]}, where state is "eof" or "read-error". A failed read is reported, not
     * hidden, and the bytes received before it are kept.
     */
    private static final String TRACED_DRAIN =
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

    private static List<Object> elements(Object value) {
        return assertInstanceOf(ProtosArrayValue.class, value).indexedSnapshot();
    }

    private static String text(Object value) {
        return value instanceof ProtosStringValue string ? string.value() : String.valueOf(value);
    }

    /**
     * Exit is not EOF for bytes produced before the exit. Each property is observed and asserted
     * separately: the wait outcome, the exact stdout and stderr contents, whether each read reached
     * EOF and the order in which the two endpoints were read. A wait() IOError or other guest
     * Error and any failed read are reported as such, never folded into a
     * generic false.
     */
    @Test
    void outputProducedBeforeExitIsDeliveredAfterExit(@TempDir Path directory) throws IOException {
        ProtosI166ExternalExecutionLaunchTest.assumeProgram("/bin/ls");
        try (ProtosStandaloneHostedSession session = open(directory, TRACED_DRAIN + """
                probe: () => {
                    child: execution.start("/bin/ls", ["-d", "/", "/nonexistent-protos-i166"], Map(), null).value()
                    waited: Error.handle(() => {
                        IOError.handle(() => {
                            status: child.wait().value()
                            if(status.exited() && (status.exitCode() != 0)) { "resolved" } else { "unexpected-status" }
                        }, (error) => { "io-error" })
                    }, (error) => { "other-error" })
                    order: "stdout"
                    out: tracedDrain(child.stdout())
                    order = order + ",stderr"
                    err: tracedDrain(child.stderr())
                    [waited, out[0], out[1], err[0], err[1], order]
                }
                """)) {
            List<Object> result =
                    elements(ProtosI166ExternalExecutionLaunchTest.invoke(session, "probe"));
            assertEquals(6, result.size(), "guest result: " + result);
            String waited = text(result.get(0));
            String out = text(result.get(1));
            String outState = text(result.get(2));
            String err = text(result.get(3));
            String errState = text(result.get(4));
            String order = text(result.get(5));
            String observed = "wait=" + waited
                    + " stdout=" + quoted(out) + " (" + out.length() + " chars, " + outState + ")"
                    + " stderr=" + quoted(err) + " (" + err.length() + " chars, " + errState + ")"
                    + " readOrder=" + order
                    + " jvm=" + jvm();
            assertAll(
                    () -> assertEquals("resolved", waited,
                            "wait() must resolve with a non-zero normal exit before EOF; "
                                    + observed),
                    () -> assertEquals("eof", outState, "stdout read must reach EOF; " + observed),
                    () -> assertEquals("/\n", out,
                            "stdout bytes produced before exit were not all delivered; " + observed),
                    () -> assertEquals("eof", errState, "stderr read must reach EOF; " + observed),
                    () -> assertFalse(err.isEmpty(),
                            "stderr bytes produced before exit were lost; " + observed),
                    () -> assertEquals("stdout,stderr", order, "read order; " + observed));
        }
    }

    /**
     * Control without exit: the child stays alive (it waits for stdin EOF) while two fragments
     * are produced and consumed, so the JDK reaper never intervenes. A failure here is a byte
     * transfer defect (classification B) or a fixture defect (C), never the exit-time stream
     * withdrawal of the JDK (A).
     */
    @Test
    void outputProducedWhileTheChildIsAliveIsDeliveredExactlyInOrder(@TempDir Path directory)
            throws IOException {
        ProtosI166ExternalExecutionLaunchTest.assumeProgram("/bin/cat");
        try (ProtosStandaloneHostedSession session = open(directory, TRACED_DRAIN + """
                probe: () => {
                    child: execution.start("/bin/cat", [], Map(), null).value()
                    child.stdin().write(Encoding.UTF8.encode("early")).value()
                    first: readExactly(child.stdout(), 5)
                    child.stdin().write(Encoding.UTF8.encode("late")).value()
                    second: readExactly(child.stdout(), 4)
                    child.stdin().close().value()
                    rest: tracedDrain(child.stdout())
                    [first[0], first[1], second[0], second[1], rest[0], rest[1]]
                }
                """)) {
            List<Object> result =
                    elements(ProtosI166ExternalExecutionLaunchTest.invoke(session, "probe"));
            assertEquals(6, result.size(), "guest result: " + result);
            String observed = "first=" + quoted(text(result.get(0))) + "/" + text(result.get(1))
                    + " second=" + quoted(text(result.get(2))) + "/" + text(result.get(3))
                    + " rest=" + quoted(text(result.get(4))) + "/" + text(result.get(5))
                    + " jvm=" + jvm();
            assertAll(
                    () -> assertEquals("complete", text(result.get(1)),
                            "no EOF or failure before the first fragment; " + observed),
                    () -> assertEquals("early", text(result.get(0)), observed),
                    () -> assertEquals("complete", text(result.get(3)),
                            "no EOF or failure before the second fragment; " + observed),
                    () -> assertEquals("late", text(result.get(2)), observed),
                    () -> assertEquals("eof", text(result.get(5)),
                            "EOF only after stdin closed; " + observed),
                    () -> assertEquals("", text(result.get(4)), observed));
        }
    }

    private static String quoted(String value) {
        return "\"" + value.replace("\n", "\\n") + "\"";
    }

    private static String jvm() {
        return System.getProperty("java.vm.name") + " " + System.getProperty("java.vm.version")
                + " (" + System.getProperty("java.vendor") + ")";
    }

    @Test
    void cancelledBlockedReadKeepsOrderForTheNextRead(@TempDir Path directory) throws IOException {
        ProtosI166ExternalExecutionLaunchTest.assumeProgram("/bin/cat");
        try (ProtosStandaloneHostedSession session = open(directory, """
                probe: () => {
                    child: execution.start("/bin/cat", [], Map(), null).value()
                    pending: child.stdout().read(3)
                    pending.cancel()
                    cancelled: Cancelled.handle(() => { pending.value(); false }, (error) => { true })
                    child.stdin().write(Encoding.UTF8.encode("abcdef")).value()
                    child.stdin().close().value()
                    cancelled && (drain(child.stdout()) == "abcdef")
                }
                """)) {
            assertSame(Boolean.TRUE, ProtosI166ExternalExecutionLaunchTest.invoke(session, "probe"));
        }
    }

    @Test
    void successiveReadsNeverOverlapOnThePipe(@TempDir Path directory) throws IOException {
        ProtosI166ExternalExecutionLaunchTest.assumeProgram("/bin/cat");
        try (ProtosStandaloneHostedSession session = open(directory, """
                probe: () => {
                    child: execution.start("/bin/cat", [], Map(), null).value()
                    first: child.stdout().read(4)
                    second: child.stdout().read(4)
                    child.stdin().write(Encoding.UTF8.encode("12345678")).value()
                    child.stdin().close().value()
                    a: Encoding.UTF8.decode(first.value())
                    b: Encoding.UTF8.decode(second.value())
                    rest: drain(child.stdout())
                    (a + b + rest) == "12345678"
                }
                """)) {
            assertSame(Boolean.TRUE, ProtosI166ExternalExecutionLaunchTest.invoke(session, "probe"));
        }
    }

    @Test
    void stdoutAndStderrAreIndependent(@TempDir Path directory) throws IOException {
        ProtosI166ExternalExecutionLaunchTest.assumeProgram("/bin/ls");
        try (ProtosStandaloneHostedSession session = open(directory, """
                probe: () => {
                    child: execution.start("/bin/ls", ["/nonexistent-protos-i166"], Map(), null).value()
                    (drain(child.stderr()).size() > 0) && (drain(child.stdout()) == "")
                }
                """)) {
            assertSame(Boolean.TRUE, ProtosI166ExternalExecutionLaunchTest.invoke(session, "probe"));
        }
    }

    private static final String ARRAYS = "Arrays: import(\"std:collections/array\")\n";

    /**
     * Regression for the shared-worker deadlock: every admitted child has a blocked stdout read
     * before any byte is written. Pipe waits never occupy a launch worker, so the writes and
     * closes still progress and every read completes with its exact bytes.
     */
    @Test
    void blockedReadsNeverStarveTheWritesThatUnblockThem(@TempDir Path directory)
            throws IOException {
        ProtosI166ExternalExecutionLaunchTest.assumeProgram("/bin/cat");
        try (ProtosStandaloneHostedSession session = open(directory, ARRAYS + """
                names: ["child-0", "child-1", "child-2", "child-3", "child-4", "child-5", "child-6", "child-7", "child-8", "child-9", "child-10", "child-11", "child-12", "child-13", "child-14", "child-15"]
                probe: () => {
                    children: []
                    reads: []
                    Range(0, 16).each((index) => {
                        children = Arrays.append(children, execution.start("/bin/cat", [], Map(), null).value())
                    })
                    children.each((child) => { reads = Arrays.append(reads, child.stdout().read(64)) })
                    Range(0, 16).each((index) => {
                        children[index].stdin().write(Encoding.UTF8.encode(names[index])).value()
                        children[index].stdin().close().value()
                    })
                    exact: true
                    Range(0, 16).each((index) => {
                        text: Encoding.UTF8.decode(reads[index].value()) + drain(children[index].stdout())
                        exact = exact && (text == names[index])
                    })
                    exact
                }
                """)) {
            assertSame(Boolean.TRUE, ProtosI166ExternalExecutionLaunchTest.invoke(session, "probe"));
        }
    }

    @Test
    void admissionBeyondTheChildBoundFailsWithIOCapacityExhausted(@TempDir Path directory)
            throws IOException {
        ProtosI166ExternalExecutionLaunchTest.assumeProgram("/bin/cat");
        try (ProtosStandaloneHostedSession session = open(directory, ARRAYS + """
                probe: () => {
                    children: []
                    Range(0, 16).each((index) => {
                        children = Arrays.append(children, execution.start("/bin/cat", [], Map(), null).value())
                    })
                    exhausted: IOCapacityExhausted.handle(() => {
                        execution.start("/bin/cat", [], Map(), null).value()
                        false
                    }, (error) => { true })
                    children.each((child) => { child.stdin().close().value(); drain(child.stdout()) })
                    exhausted
                }
                """)) {
            assertSame(Boolean.TRUE, ProtosI166ExternalExecutionLaunchTest.invoke(session, "probe"));
        }
    }

    @Test
    void outputLargerThanEveryBufferIsDeliveredWithBackpressure(@TempDir Path directory)
            throws IOException {
        ProtosI166ExternalExecutionLaunchTest.assumeProgram("/usr/bin/head");
        // 4 MiB exceed the pump chunk, the host Pipe and the OS pipe together: the child can only
        // finish because the guest keeps reading; nothing is drained in advance.
        try (ProtosStandaloneHostedSession session = open(directory, """
                probe: () => {
                    child: execution.start("/usr/bin/head", ["-c", "4194304", "/dev/zero"], Map(), null).value()
                    total: 0
                    chunk: child.stdout().read(65536).value()
                    while(chunk !== null) {
                        total = total + Encoding.UTF8.decode(chunk).size()
                        chunk = child.stdout().read(65536).value()
                    }
                    total == 4194304
                }
                """)) {
            assertSame(Boolean.TRUE, ProtosI166ExternalExecutionLaunchTest.invoke(session, "probe"));
        }
    }

    @Test
    void successiveWritesKeepTheirOrder(@TempDir Path directory) throws IOException {
        ProtosI166ExternalExecutionLaunchTest.assumeProgram("/bin/cat");
        try (ProtosStandaloneHostedSession session = open(directory, """
                probe: () => {
                    child: execution.start("/bin/cat", [], Map(), null).value()
                    first: child.stdin().write(Encoding.UTF8.encode("one-"))
                    second: child.stdin().write(Encoding.UTF8.encode("two-"))
                    third: child.stdin().write(Encoding.UTF8.encode("three"))
                    first.value()
                    second.value()
                    third.value()
                    child.stdin().close().value()
                    drain(child.stdout()) == "one-two-three"
                }
                """)) {
            assertSame(Boolean.TRUE, ProtosI166ExternalExecutionLaunchTest.invoke(session, "probe"));
        }
    }

    @Test
    void closeAfterACommittedWriteDeliversItsBytesFirst(@TempDir Path directory)
            throws IOException {
        ProtosI166ExternalExecutionLaunchTest.assumeProgram("/bin/cat");
        try (ProtosStandaloneHostedSession session = open(directory, """
                probe: () => {
                    child: execution.start("/bin/cat", [], Map(), null).value()
                    pending: child.stdin().write(Encoding.UTF8.encode("committed"))
                    closed: child.stdin().close()
                    // IO_CORE.md §8: commitment is asynchronous, so the write is either committed
                    // before the close cutover (its bytes reach the child before EOF) or still
                    // uncommitted and closure-terminated with IOLifecycleError (nothing reaches it).
                    written: IOLifecycleError.handle(() => { pending.value(); true }, (error) => {
                        false
                    })
                    closed.value()
                    out: drain(child.stdout())
                    (written && (out == "committed")) || ((!written) && (out == ""))
                }
                """)) {
            assertSame(Boolean.TRUE, ProtosI166ExternalExecutionLaunchTest.invoke(session, "probe"));
        }
    }

    @Test
    void stdoutEofIsIndependentOfAnOpenStderr(@TempDir Path directory) throws IOException {
        ProtosI166ExternalExecutionLaunchTest.assumeProgram("/bin/sh");
        // The child closes only its stdout and keeps stderr open until stdin ends; argv is passed
        // directly, the shell is merely the observed program.
        try (ProtosStandaloneHostedSession session = open(directory, """
                probe: () => {
                    child: execution.start("/bin/sh", ["-c", "printf out; exec 1>&-; cat >&2"], Map(), null).value()
                    out: drain(child.stdout())
                    child.stdin().write(Encoding.UTF8.encode("err")).value()
                    child.stdin().close().value()
                    (out == "out") && (drain(child.stderr()) == "err")
                }
                """)) {
            assertSame(Boolean.TRUE, ProtosI166ExternalExecutionLaunchTest.invoke(session, "probe"));
        }
    }

    @Test
    void manyChildrenCreateBoundedHostThreads(@TempDir Path directory) throws IOException {
        ProtosI166ExternalExecutionLaunchTest.assumeProgram("/bin/cat");
        int before = Thread.activeCount();
        try (ProtosStandaloneHostedSession session = open(directory, ARRAYS + """
                probe: () => {
                    children: []
                    Range(0, 16).each((index) => {
                        children = Arrays.append(children, execution.start("/bin/cat", [], Map(), null).value())
                    })
                    children
                }
                done: () => { true }
                """)) {
            ProtosI166ExternalExecutionLaunchTest.invoke(session, "probe");
            int during = Thread.activeCount();
            // Three pumps per admitted child, one selector and at most MAX_WORKERS launch
            // workers: independent of how many reads or writes are outstanding.
            assertTrue(during - before <= 16 * 3 + 1 + 16 + 8, "threads: " + (during - before));
        }
    }

    /** Counts closes of a native child stream stand-in; optionally reports them as failed. */
    private static final class CountingInput extends FilterInputStream {
        final AtomicInteger closes = new AtomicInteger();
        final boolean failClose;

        CountingInput(InputStream delegate, boolean failClose) {
            super(delegate);
            this.failClose = failClose;
        }

        @Override
        public void close() throws IOException {
            closes.incrementAndGet();
            super.close();
            if (failClose) {
                throw new IOException("uncertain close");
            }
        }
    }

    private static final class CountingOutput extends FilterOutputStream {
        final AtomicInteger closes = new AtomicInteger();

        CountingOutput(OutputStream delegate) {
            super(delegate);
        }

        @Override
        public void close() throws IOException {
            closes.incrementAndGet();
            super.close();
        }
    }

    @Test
    void attachFailureBeforeAnyPumpRetiresAtOnceAndReleasesNativeStreamsOnce() {
        ProtosNioProcessPipeBackend backend = new ProtosNioProcessPipeBackend("i166-test-pipe");
        backend.close();
        CountingOutput stdin = new CountingOutput(new ByteArrayOutputStream());
        CountingInput stdout = new CountingInput(new ByteArrayInputStream(new byte[0]), false);
        CountingInput stderr = new CountingInput(new ByteArrayInputStream(new byte[0]), true);
        ProtosNioProcessPipeBackend.ChildPipes pipes = backend.prepare(stdin, stdout, stderr);
        assertFalse(pipes.retired().toCompletableFuture().isDone());
        assertThrows(IOException.class, () -> backend.attach(pipes));
        // No pump ever ran: every lane slot was accounted once by the failure itself.
        assertTrue(pipes.retired().toCompletableFuture().isDone());
        assertFalse(pipes.attachedForRuntime());
        // The record owns the native streams: each is closed once, an uncertain close is final.
        assertTrue(pipes.releaseStdin(true).toCompletableFuture().join());
        assertTrue(pipes.releaseStdout().toCompletableFuture().join());
        assertFalse(pipes.releaseStderr().toCompletableFuture().join());
        assertTrue(pipes.releaseStdin(true).toCompletableFuture().join());
        assertTrue(pipes.releaseStdout().toCompletableFuture().join());
        assertFalse(pipes.releaseStderr().toCompletableFuture().join());
        assertEquals(1, stdin.closes.get());
        assertEquals(1, stdout.closes.get());
        assertEquals(1, stderr.closes.get());
    }

    @Test
    void retirementWaitsForEveryStartedPumpToExit() throws Exception {
        try (ProtosNioProcessPipeBackend backend = new ProtosNioProcessPipeBackend("i166-test-pipe")) {
            PipedOutputStream stdoutWriter = new PipedOutputStream();
            PipedOutputStream stderrWriter = new PipedOutputStream();
            CountingOutput stdin = new CountingOutput(new ByteArrayOutputStream());
            ProtosNioProcessPipeBackend.ChildPipes pipes = backend.prepare(
                    stdin, new PipedInputStream(stdoutWriter), new PipedInputStream(stderrWriter));
            backend.attach(pipes);
            assertTrue(pipes.attachedForRuntime());
            CompletableFuture<Void> retired = pipes.retired().toCompletableFuture();
            // Real EOF of stdout ends only its pump; the others still hold their streams.
            stdoutWriter.close();
            assertFalse(retired.isDone());
            stderrWriter.close();
            // The stdin pump is still blocked on its Pipe, so the transport is not retired.
            assertFalse(retired.isDone());
            // Closing stdin lets its pump hand the EOF to the child stream and exit.
            assertTrue(pipes.releaseStdin(false).toCompletableFuture().get(10, TimeUnit.SECONDS));
            retired.get(10, TimeUnit.SECONDS);
            assertEquals(1, stdin.closes.get());
            // Releases after retirement are memoized, never repeated on the native stream.
            assertTrue(pipes.releaseStdin(true).toCompletableFuture().join());
            assertEquals(1, stdin.closes.get());
        }
    }

    private static long pumpThreads(String prefix) {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.isAlive() && thread.getName().startsWith(prefix + "-std"))
                .count();
    }

    /**
     * The poller refuses the registration of the lanes: the refusal reaches attach, which fails
     * before any pump starts and before any ChildProcess could be published; every slot is
     * accounted, no lane accepts transfers and each native stream is closed once by its record.
     * The poller is stopped through its own close, so every lane fails; a failure of exactly one
     * of the three registrations cannot be provoked deterministically without a production hook.
     */
    @Test
    void refusedPollerRegistrationFailsAttachBeforeAnyPump() throws Exception {
        String prefix = "i166-refused-registration-" + System.nanoTime();
        ProtosNioProcessPipeBackend backend = new ProtosNioProcessPipeBackend(prefix);
        java.lang.reflect.Method poller =
                ProtosNioProcessPipeBackend.class.getDeclaredMethod("poller");
        poller.setAccessible(true);
        ((ProtosNioHostIoPoller) poller.invoke(backend)).close();
        CountingOutput stdin = new CountingOutput(new ByteArrayOutputStream());
        CountingInput stdout = new CountingInput(new ByteArrayInputStream(new byte[0]), false);
        CountingInput stderr = new CountingInput(new ByteArrayInputStream(new byte[0]), false);
        ProtosNioProcessPipeBackend.ChildPipes pipes = backend.prepare(stdin, stdout, stderr);
        assertThrows(IOException.class, () -> backend.attach(pipes));
        assertFalse(pipes.attachedForRuntime());
        assertEquals(0, pumpThreads(prefix));
        assertTrue(pipes.retired().toCompletableFuture().isDone());
        assertTrue(pipes.releaseStdin(true).toCompletableFuture().join());
        assertTrue(pipes.releaseStdout().toCompletableFuture().join());
        assertTrue(pipes.releaseStderr().toCompletableFuture().join());
        assertEquals(1, stdin.closes.get());
        assertEquals(1, stdout.closes.get());
        assertEquals(1, stderr.closes.get());
        backend.close();
    }

    /**
     * Stand-in for a native child output stream. It first yields {@code payload}, then blocks
     * until {@code readGate} opens and answers EOF or {@code readFailure}. Its close is counted,
     * signals {@code closeEntered} and can be held at {@code closeGate}, so a test observes a
     * physical close that is still in progress.
     */
    private static final class ControlledInput extends InputStream {
        final CountDownLatch readGate;
        final CountDownLatch closeEntered = new CountDownLatch(1);
        final CountDownLatch closeGate;
        final AtomicInteger closes = new AtomicInteger();
        final boolean failClose;
        volatile IOException readFailure;
        volatile RuntimeException hostFailure;
        private byte[] payload;

        ControlledInput(byte[] payload, boolean readOpen, boolean closeOpen, boolean failClose) {
            this.payload = payload;
            this.readGate = new CountDownLatch(readOpen ? 0 : 1);
            this.closeGate = new CountDownLatch(closeOpen ? 0 : 1);
            this.failClose = failClose;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            byte[] pending;
            synchronized (this) {
                pending = payload;
                payload = null;
            }
            if (pending != null && pending.length > 0) {
                System.arraycopy(pending, 0, buffer, offset, pending.length);
                return pending.length;
            }
            awaitQuietly(readGate);
            if (readFailure != null) {
                throw readFailure;
            }
            if (hostFailure != null) {
                throw hostFailure;
            }
            return -1;
        }

        @Override
        public void close() throws IOException {
            closes.incrementAndGet();
            closeEntered.countDown();
            awaitQuietly(closeGate);
            if (failClose) {
                throw new IOException("uncertain close");
            }
        }

        private static void awaitQuietly(CountDownLatch latch) throws IOException {
            try {
                if (!latch.await(20, TimeUnit.SECONDS)) {
                    throw new IOException("test gate never opened");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException(interrupted);
            }
        }
    }

    /** Records the single outcome of one lane read. */
    private static final class ReadProbe implements ProtosByteIoFlow.ReadCompletion {
        final CompletableFuture<Object> outcome = new CompletableFuture<>();

        @Override
        public void data(byte[] bytes) {
            outcome.complete(bytes);
        }

        @Override
        public void eof() {
            outcome.complete("eof");
        }

        @Override
        public void failed() {
            outcome.complete("failed");
        }

        @Override
        public void rejected(ProtosCoreErrors.StandardError kind) {
            outcome.complete(kind);
        }

        Object await() throws Exception {
            return outcome.get(10, TimeUnit.SECONDS);
        }
    }

    /** Reads a lane until EOF and answers every byte delivered before it. */
    private static byte[] drain(ProtosByteIoFlow.Backend lane) throws Exception {
        ByteArrayOutputStream collected = new ByteArrayOutputStream();
        for (;;) {
            ReadProbe probe = new ReadProbe();
            lane.read(64, probe);
            Object result = probe.await();
            if ("eof".equals(result)) {
                return collected.toByteArray();
            }
            assertTrue(result instanceof byte[], "unexpected read outcome: " + result);
            collected.write((byte[]) result);
        }
    }

    /** Runs {@code action} on another thread and fails when it does not return promptly. */
    private static <T> T returnsPromptly(java.util.function.Supplier<T> action) throws Exception {
        return CompletableFuture.supplyAsync(action).get(5, TimeUnit.SECONDS);
    }

    /**
     * Requesting a release and finishing it physically are distinct events. A release requested
     * while the pump is blocked reading a stream whose writer stays open returns at once,
     * rejects new reads and stays pending; the pump closes the stream itself once its read ends,
     * exactly once, and a close held at a barrier blocks no requester. The pump stays counted
     * until it exits, and one child has exactly three pumps.
     */
    @Test
    void outputReleaseRequestStaysPendingUntilThePumpClosesTheStream() throws Exception {
        String prefix = "i166-blocked-release-" + System.nanoTime();
        try (ProtosNioProcessPipeBackend backend = new ProtosNioProcessPipeBackend(prefix)) {
            PipedOutputStream stdoutWriter = new PipedOutputStream();
            CountingOutput stdin = new CountingOutput(new ByteArrayOutputStream());
            ControlledInput stderr = new ControlledInput(null, false, false, false);
            ProtosNioProcessPipeBackend.ChildPipes pipes = backend.prepare(
                    stdin, new PipedInputStream(stdoutWriter), stderr);
            backend.attach(pipes);
            assertTrue(pipes.attachedForRuntime());
            assertEquals(3, pumpThreads(prefix));
            CompletableFuture<Void> retired = pipes.retired().toCompletableFuture();

            CompletableFuture<Boolean> release =
                    returnsPromptly(() -> pipes.releaseStderr().toCompletableFuture());
            assertFalse(release.isDone());
            assertEquals(0, stderr.closes.get());
            ReadProbe late = new ReadProbe();
            pipes.stderrLane().read(16, late);
            assertEquals(ProtosCoreErrors.StandardError.I_O_LIFECYCLE_ERROR, late.await());
            // A repeated request while pending answers the same outcome and closes nothing.
            assertSame(release, returnsPromptly(() -> pipes.releaseStderr().toCompletableFuture()));
            assertEquals(0, stderr.closes.get());

            // The read ends; the pump enters the close, which is held at its barrier.
            stderr.readGate.countDown();
            assertTrue(stderr.closeEntered.await(10, TimeUnit.SECONDS));
            assertSame(release, returnsPromptly(() -> pipes.releaseStderr().toCompletableFuture()));
            assertFalse(release.isDone());
            assertFalse(retired.isDone());
            assertEquals(3, pumpThreads(prefix));

            stderr.closeGate.countDown();
            assertTrue(release.get(10, TimeUnit.SECONDS));
            assertEquals(1, stderr.closes.get());
            assertTrue(pipes.releaseStderr().toCompletableFuture().join());
            assertEquals(1, stderr.closes.get());
            assertFalse(retired.isDone());

            stdoutWriter.close();
            assertTrue(pipes.releaseStdout().toCompletableFuture().get(10, TimeUnit.SECONDS));
            assertFalse(retired.isDone());
            assertTrue(pipes.releaseStdin(false).toCompletableFuture().get(10, TimeUnit.SECONDS));
            retired.get(10, TimeUnit.SECONDS);
            assertEquals(1, stdin.closes.get());
        }
    }

    /**
     * At natural EOF the pump closes the native stream once; the bytes already in the Pipe stay
     * readable, and a later release answers the memoized outcome without a second close.
     */
    @Test
    void naturalEofThenReleaseAnswersThePumpsSingleClose() throws Exception {
        try (ProtosNioProcessPipeBackend backend = new ProtosNioProcessPipeBackend("i166-test-pipe")) {
            ControlledInput stdout = new ControlledInput(
                    "abc".getBytes(java.nio.charset.StandardCharsets.US_ASCII), true, true, false);
            ControlledInput stderr = new ControlledInput(null, true, true, false);
            CountingOutput stdin = new CountingOutput(new ByteArrayOutputStream());
            ProtosNioProcessPipeBackend.ChildPipes pipes = backend.prepare(stdin, stdout, stderr);
            backend.attach(pipes);
            assertTrue(stdout.closeEntered.await(10, TimeUnit.SECONDS));
            assertEquals("abc", new String(drain(pipes.stdoutLane()),
                    java.nio.charset.StandardCharsets.US_ASCII));
            CompletableFuture<Boolean> release = pipes.releaseStdout().toCompletableFuture();
            assertTrue(release.get(10, TimeUnit.SECONDS));
            assertSame(release, pipes.releaseStdout().toCompletableFuture());
            assertEquals(1, stdout.closes.get());
            // stderr reached EOF on its own; its outcome is independent of stdout.
            assertTrue(pipes.releaseStderr().toCompletableFuture().get(10, TimeUnit.SECONDS));
            assertEquals(1, stderr.closes.get());
            assertTrue(pipes.releaseStdin(false).toCompletableFuture().get(10, TimeUnit.SECONDS));
            pipes.retired().toCompletableFuture().get(10, TimeUnit.SECONDS);
        }
    }

    /**
     * A failed physical close is uncertain: the release answers false, never true, and neither a
     * repeated release nor a failed read during that close causes a second close.
     */
    @Test
    void failedPhysicalCloseIsUncertainAndNeverRepeated() throws Exception {
        try (ProtosNioProcessPipeBackend backend = new ProtosNioProcessPipeBackend("i166-test-pipe")) {
            ControlledInput stdout = new ControlledInput(null, false, true, true);
            stdout.readFailure = new IOException("stream failed");
            ControlledInput stderr = new ControlledInput(null, true, true, false);
            CountingOutput stdin = new CountingOutput(new ByteArrayOutputStream());
            ProtosNioProcessPipeBackend.ChildPipes pipes = backend.prepare(stdin, stdout, stderr);
            backend.attach(pipes);
            CompletableFuture<Boolean> release = pipes.releaseStdout().toCompletableFuture();
            assertFalse(release.isDone());
            stdout.readGate.countDown();
            assertFalse(release.get(10, TimeUnit.SECONDS));
            assertFalse(pipes.releaseStdout().toCompletableFuture().join());
            assertEquals(1, stdout.closes.get());
            assertTrue(pipes.releaseStdin(false).toCompletableFuture().get(10, TimeUnit.SECONDS));
            pipes.retired().toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertEquals(1, stdout.closes.get());
        }
    }

    /**
     * A cancelled pending read loses no byte: data that arrives afterwards is delivered to the
     * next read. A cancelled read followed by a release keeps the release pending until the
     * writer closes, then confirms it with a single close.
     */
    @Test
    void cancelledReadKeepsBytesForTheNextReadAndThenReleases() throws Exception {
        try (ProtosNioProcessPipeBackend backend = new ProtosNioProcessPipeBackend("i166-test-pipe")) {
            PipedOutputStream stdoutWriter = new PipedOutputStream();
            CountingInput stdout = new CountingInput(new PipedInputStream(stdoutWriter), false);
            ControlledInput stderr = new ControlledInput(null, true, true, false);
            CountingOutput stdin = new CountingOutput(new ByteArrayOutputStream());
            ProtosNioProcessPipeBackend.ChildPipes pipes = backend.prepare(stdin, stdout, stderr);
            backend.attach(pipes);

            ReadProbe cancelled = new ReadProbe();
            pipes.stdoutLane().read(16, cancelled).cancel();
            assertEquals("failed", cancelled.await());
            stdoutWriter.write("xyz".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            stdoutWriter.flush();
            ReadProbe next = new ReadProbe();
            pipes.stdoutLane().read(16, next);
            assertEquals("xyz", new String((byte[]) next.await(),
                    java.nio.charset.StandardCharsets.US_ASCII));

            ReadProbe cancelledAgain = new ReadProbe();
            pipes.stdoutLane().read(16, cancelledAgain).cancel();
            assertEquals("failed", cancelledAgain.await());
            CompletableFuture<Boolean> release = pipes.releaseStdout().toCompletableFuture();
            assertFalse(release.isDone());
            assertEquals(0, stdout.closes.get());
            stdoutWriter.close();
            assertTrue(release.get(10, TimeUnit.SECONDS));
            assertEquals(1, stdout.closes.get());
            assertTrue(pipes.releaseStdin(false).toCompletableFuture().get(10, TimeUnit.SECONDS));
            pipes.retired().toCompletableFuture().get(10, TimeUnit.SECONDS);
        }
    }

    /**
     * Native output stand-in in two phases: the first read yields {@code early}; the second
     * signals {@code barrierEntered} and blocks at {@code barrier} (the writer is still open),
     * then yields {@code late}; every later read is EOF. It counts closes and records the maximum
     * number of reads that were ever in progress at once.
     */
    private static final class PhasedInput extends InputStream {
        final CountDownLatch barrierEntered = new CountDownLatch(1);
        final CountDownLatch barrier = new CountDownLatch(1);
        final AtomicInteger closes = new AtomicInteger();
        final AtomicInteger readsInProgress = new AtomicInteger();
        final AtomicInteger maxReadsInProgress = new AtomicInteger();
        private final byte[] early;
        private final byte[] late;
        private int phase;

        PhasedInput(byte[] early, byte[] late) {
            this.early = early;
            this.late = late;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            maxReadsInProgress.accumulateAndGet(readsInProgress.incrementAndGet(), Math::max);
            try {
                int current;
                synchronized (this) {
                    current = phase++;
                }
                if (current == 0) {
                    return copy(early, buffer, offset, length);
                }
                if (current == 1) {
                    barrierEntered.countDown();
                    ControlledInput.awaitQuietly(barrier);
                    return copy(late, buffer, offset, length);
                }
                return -1;
            } finally {
                readsInProgress.decrementAndGet();
            }
        }

        private static int copy(byte[] source, byte[] buffer, int offset, int length) {
            if (source.length > length) {
                throw new IllegalStateException("fixture fragment exceeds the pump read size");
            }
            System.arraycopy(source, 0, buffer, offset, source.length);
            return source.length;
        }

        @Override
        public void close() {
            closes.incrementAndGet();
        }
    }

    /**
     * Transport control without the JDK ProcessImpl: the Protos pipe backend is fed by a
     * controlled source that delivers {@code early}, stays open at a barrier, then delivers
     * {@code late} and EOF. While the source is open no EOF is reported, nothing is closed and
     * the transport is not retired; a read issued meanwhile is answered with {@code late}, not
     * EOF. If this test fails, the defect is in the Protos transport or in this fixture
     * (classification B or C), not in ProcessImpl.processExited(). Passing isolates the backend
     * from the JDK but does not by itself prove that every real loss comes from the JDK.
     */
    @Test
    void controlledSourceHeldOpenIsNeverEofAndDeliversEveryFragment() throws Exception {
        try (ProtosNioProcessPipeBackend backend = new ProtosNioProcessPipeBackend("i166-test-pipe")) {
            PhasedInput stdout = new PhasedInput(
                    "early".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                    "late".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            ControlledInput stderr = new ControlledInput(null, true, true, false);
            CountingOutput stdin = new CountingOutput(new ByteArrayOutputStream());
            ProtosNioProcessPipeBackend.ChildPipes pipes = backend.prepare(stdin, stdout, stderr);
            CompletableFuture<Void> retired = pipes.retired().toCompletableFuture();
            try {
                backend.attach(pipes);
                // Retire the other two lanes first, so that the stdout pump is the only obligation.
                assertTrue(pipes.releaseStdin(false).toCompletableFuture().get(10, TimeUnit.SECONDS));
                assertTrue(stderr.closeEntered.await(10, TimeUnit.SECONDS));
                assertTrue(pipes.releaseStderr().toCompletableFuture().get(10, TimeUnit.SECONDS));

                ByteArrayOutputStream collected = new ByteArrayOutputStream();
                while (collected.size() < 5) {
                    ReadProbe probe = new ReadProbe();
                    pipes.stdoutLane().read(64, probe);
                    Object result = probe.await();
                    assertTrue(result instanceof byte[],
                            "outcome before the first fragment completed: " + result
                                    + " after " + collected.size() + " bytes");
                    collected.write((byte[]) result);
                }
                assertEquals("early", collected.toString(java.nio.charset.StandardCharsets.US_ASCII));

                // The pump is now blocked inside the open source.
                assertTrue(stdout.barrierEntered.await(10, TimeUnit.SECONDS),
                        "the pump never issued its second read");
                assertEquals(0, stdout.closes.get(), "physical close while the source is open");
                assertFalse(retired.isDone(), "transport retired while the source is open");
                ReadProbe pending = new ReadProbe();
                pipes.stdoutLane().read(64, pending);
                assertFalse(pending.outcome.isDone(),
                        "read completed while the source is open: " + pending.outcome.getNow(null));

                stdout.barrier.countDown();
                Object late = pending.await();
                assertTrue(late instanceof byte[], "read issued while open answered " + late);
                collected.write((byte[]) late);
                collected.write(drain(pipes.stdoutLane()));
                assertEquals("earlylate",
                        collected.toString(java.nio.charset.StandardCharsets.US_ASCII));

                retired.get(10, TimeUnit.SECONDS);
                assertEquals(1, stdout.closes.get());
                assertEquals(1, stdout.maxReadsInProgress.get(), "concurrent reads of the lane");
                assertTrue(pipes.releaseStdout().toCompletableFuture().get(10, TimeUnit.SECONDS));
                assertEquals(1, stdout.closes.get());
            } finally {
                // Never leave the pump blocked at the barrier, whatever failed.
                stdout.barrier.countDown();
            }
        }
    }

    @Test
    void nativeStdinReportsBrokenPipeAfterConfirmedChildExit() throws Exception {
        var provider = new ProtosPosixProcessHandler();
        var pipe = org.graalvm.polyglot.io.ProcessHandler.Redirect.PIPE;
        var command = org.graalvm.polyglot.io.ProcessHandler.ProcessCommand.create(
                java.util.List.of("/bin/true"), "/", java.util.Map.of(), false, pipe, pipe, pipe);
        Process child = provider.start(command);
        try {
            assertTrue(child.waitFor(10, TimeUnit.SECONDS));
            IOException failure = assertThrows(
                    IOException.class, () -> child.getOutputStream().write(new byte[] {1}));
            assertTrue(failure.getMessage().contains("-32"), "expected Linux EPIPE: " + failure);
            assertEquals(0, child.exitValue());
            // An operation after the failed write also proves the JVM survived SIGPIPE.
            Process next = provider.start(command);
            try {
                assertTrue(next.waitFor(10, TimeUnit.SECONDS));
                assertEquals(0, next.exitValue());
            } finally {
                next.destroyForcibly();
                assertTrue(next.waitFor(10, TimeUnit.SECONDS));
                next.getOutputStream().close();
                next.getInputStream().close();
                next.getErrorStream().close();
            }
        } finally {
            child.destroyForcibly();
            child.getOutputStream().close();
            child.getInputStream().close();
            child.getErrorStream().close();
            assertTrue(child.waitFor(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void descendantOutputAfterReapIsDeliveredThroughTheExistingTransport(
            @TempDir Path directory) throws IOException {
        ProtosI166ExternalExecutionLaunchTest.assumeProgram("/bin/sh");
        ProtosI166ExternalExecutionLaunchTest.assumeProgram("/bin/sleep");
        try (ProtosStandaloneHostedSession session = open(directory, """
                probe: () => {
                    child: execution.start("/bin/sh",
                        ["-c", "(/bin/sleep 0.2; printf late) & printf early"], Map(), null).value()
                    child.stdin().close().value()
                    status: child.wait().value()
                    status.exited() && (status.exitCode() == 0) && (drain(child.stdout()) == "earlylate")
                }
                """)) {
            assertSame(Boolean.TRUE, ProtosI166ExternalExecutionLaunchTest.invoke(session, "probe"));
        }
    }

    @Test
    void physicalReadFailuresAndHostDefectsAreReportedAsFailureRatherThanEof() throws Exception {
        for (boolean hostDefect : new boolean[] {false, true}) {
            try (ProtosNioProcessPipeBackend backend = new ProtosNioProcessPipeBackend("i166-read-failure")) {
                ControlledInput stdout = new ControlledInput(null, false, true, false);
                if (hostDefect) {
                    stdout.hostFailure = new IllegalStateException("host stream mechanism failed");
                } else {
                    stdout.readFailure = new IOException("physical read failed");
                }
                ControlledInput stderr = new ControlledInput(null, true, true, false);
                var pipes = backend.prepare(new ByteArrayOutputStream(), stdout, stderr);
                try {
                    backend.attach(pipes);
                    ReadProbe read = new ReadProbe();
                    pipes.stdoutLane().read(64, read);
                    stdout.readGate.countDown();
                    assertEquals("failed", read.await());
                    assertTrue(pipes.releaseStdout().toCompletableFuture().get(10, TimeUnit.SECONDS));
                    assertEquals(1, stdout.closes.get());
                    assertTrue(pipes.releaseStderr().toCompletableFuture().get(10, TimeUnit.SECONDS));
                    assertTrue(pipes.releaseStdin(false).toCompletableFuture().get(10, TimeUnit.SECONDS));
                    pipes.retired().toCompletableFuture().get(10, TimeUnit.SECONDS);
                } finally {
                    stdout.readGate.countDown();
                }
            }
        }
    }

    @Test
    void nativeBrokenPipeUsesTheOrdinaryLaneWriteFailure() throws Exception {
        var provider = new ProtosPosixProcessHandler();
        var pipe = org.graalvm.polyglot.io.ProcessHandler.Redirect.PIPE;
        Process child = provider.start(org.graalvm.polyglot.io.ProcessHandler.ProcessCommand.create(
                List.of("/bin/true"), "/", java.util.Map.of(), false, pipe, pipe, pipe));
        try (ProtosNioProcessPipeBackend backend = new ProtosNioProcessPipeBackend("i166-native-epipe")) {
            var pipes = backend.prepare(child.getOutputStream(), child.getInputStream(), child.getErrorStream());
            try {
                assertTrue(child.waitFor(10, TimeUnit.SECONDS));
                backend.attach(pipes);
                CompletableFuture<String> written = new CompletableFuture<>();
                AtomicInteger attempts = new AtomicInteger();
                pipes.stdinLane().write(new byte[4 * 1024 * 1024], new ProtosByteIoFlow.FirstEffectWriteCompletion() {
                    @Override
                    public boolean beginFirstEffectAttempt() {
                        attempts.incrementAndGet();
                        return true;
                    }

                    @Override
                    public boolean finishFirstEffectAttempt(boolean contributed) {
                        return true;
                    }

                    @Override
                    public void succeeded() {
                        written.complete("completed");
                    }

                    @Override
                    public void failed(int contributedPrefix) {
                        written.complete("failed");
                    }

                    @Override
                    public void rejected(ProtosCoreErrors.StandardError kind) {
                        written.complete(kind.name());
                    }
                });
                assertEquals("failed", written.get(10, TimeUnit.SECONDS));
                assertTrue(attempts.get() > 0, "the lane must attempt a transfer, not reject its callback");
            } finally {
                child.destroyForcibly();
                assertTrue(child.waitFor(10, TimeUnit.SECONDS));
                assertTrue(pipes.releaseStdin(true).toCompletableFuture().get(10, TimeUnit.SECONDS));
                assertTrue(pipes.releaseStdout().toCompletableFuture().get(10, TimeUnit.SECONDS));
                assertTrue(pipes.releaseStderr().toCompletableFuture().get(10, TimeUnit.SECONDS));
                pipes.retired().toCompletableFuture().get(10, TimeUnit.SECONDS);
            }
        }
    }

}
