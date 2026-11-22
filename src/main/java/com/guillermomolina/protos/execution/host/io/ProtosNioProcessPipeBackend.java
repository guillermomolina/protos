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

import com.guillermomolina.protos.runtime.ProtosByteIoFlow;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.SelectionKey;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Physical pipe adapter between the three standard streams of one native child and the ordinary
 * byte-I/O flows of its endpoints.
 *
 * <pre>
 * child stdout -&gt; pump -&gt; bounded Pipe -&gt; Selector -&gt; ByteIoFlow
 * child stderr -&gt; pump -&gt; bounded Pipe -&gt; Selector -&gt; ByteIoFlow
 * ByteIoFlow -&gt; Selector -&gt; bounded Pipe -&gt; pump -&gt; child stdin
 * </pre>
 *
 * <p>Each pump is one daemon host thread that moves bytes between a blocking child stream and
 * the blocking end of one {@link Pipe}; it never runs guest code and never completes a guest
 * Future. The selectable end of every Pipe is registered with one shared {@link
 * ProtosNioHostIoPoller} per adapter, which completes the backend requests of {@link
 * ProtosByteIoFlow} without dedicating a worker to a blocked read. Buffering is finite: one pump
 * chunk plus the OS pipe capacity per lane, so a guest that stops reading stops the pump, which
 * stops draining the child. EOF of an output lane is the real closure of the child's writers;
 * child exit is never consulted. The adapter does not wait for the child.
 *
 * <p>The number of pump threads is three per attached child; the caller bounds attached children.
 */
final class ProtosNioProcessPipeBackend implements AutoCloseable {
    // Implementation-local tuning only; not portable behavior.
    private static final int PUMP_CHUNK_BYTES = 8 * 1024;
    private static final int READ_CHUNK_BYTES = 64 * 1024;
    private static final ProtosByteIoFlow.Cancellation NO_CANCELLATION = () -> {};
    private static final AtomicInteger THREAD_SEQUENCE = new AtomicInteger();

    private final String threadPrefix;
    private ProtosNioHostIoPoller poller;
    private boolean closed;

    ProtosNioProcessPipeBackend(String threadPrefix) {
        this.threadPrefix = Objects.requireNonNull(threadPrefix, "threadPrefix");
    }

    /** The shared poller, created by the first attached child. */
    private synchronized ProtosNioHostIoPoller poller() throws IOException {
        if (closed) {
            throw new IOException("process pipe transport is closed");
        }
        if (poller == null) {
            poller = new ProtosNioHostIoPoller(threadPrefix + "-poller");
        }
        return poller;
    }

    /**
     * Creates the transport record of one started child without starting any pump, so the caller
     * can retain its retirement obligation before the first pump exists. Never fails.
     */
    ChildPipes prepare(OutputStream stdin, InputStream stdout, InputStream stderr) {
        return new ChildPipes(
                Objects.requireNonNull(stdin, "stdin"),
                Objects.requireNonNull(stdout, "stdout"),
                Objects.requireNonNull(stderr, "stderr"));
    }

    /**
     * Connects the standard streams of a prepared child. Returns only after every lane's Pipe was
     * created, its pump end configured, its selectable end registered with the poller (the
     * registration outcome itself, not the submission of the request) and its pump started; only
     * then does {@link ChildPipes#attachedForRuntime()} answer true. Runs off the Actor turn, on
     * the launching host thread, so waiting for the poller's registration answer blocks no guest
     * work; an admitted poller action always runs or fails, so the wait ends.
     *
     * <p>On failure the error is thrown here, every Pipe created so far is closed and no lane
     * accepts guest transfers; a pump that already started stays counted until it actually exits,
     * so {@link ChildPipes#retired()} never completes while a pump still holds a child stream. A
     * started pump closes its own native stream when it exits; a stream whose pump never ran is
     * closed by the release methods of {@code pipes}, which the caller's custody invokes.
     */
    @TruffleBoundary
    void attach(ChildPipes pipes) throws IOException {
        Objects.requireNonNull(pipes, "pipes");
        try {
            ProtosNioHostIoPoller shared = poller();
            pipes.stdout = new OutputLane(shared, pipes.stdoutStream, pipes, "stdout");
            pipes.stderr = new OutputLane(shared, pipes.stderrStream, pipes, "stderr");
            pipes.stdin = new InputLane(shared, pipes.stdinStream, pipes);
            Lane[] lanes = {pipes.stdout, pipes.stderr, pipes.stdin};
            for (Lane lane : lanes) {
                lane.requestRegistration();
            }
            for (Lane lane : lanes) {
                lane.awaitRegistration();
            }
            for (Lane lane : lanes) {
                lane.start();
            }
            pipes.attached = true;
        } catch (IOException | RuntimeException | Error failure) {
            pipes.abortAttach();
            throw failure;
        }
    }

    /**
     * Retires the shared poller; every registered channel is closed and its pending request
     * fails. Called only after every attached child's transport was retired. Idempotent.
     */
    @Override
    public void close() {
        ProtosNioHostIoPoller current;
        synchronized (this) {
            closed = true;
            current = poller;
            poller = null;
        }
        if (current != null) {
            current.close();
        }
    }

    private Thread pumpThread(Runnable body, String stream) {
        Thread thread =
                new Thread(body, threadPrefix + "-" + stream + "-" + THREAD_SEQUENCE.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    }

    /**
     * The three lanes of one child and the retirement of its transport. Each lane slot is
     * accounted exactly once in {@link #livePumps}: by its pump when that pump ran, otherwise by
     * the attach failure that prevented it from running.
     */
    final class ChildPipes {
        private final OutputStream stdinStream;
        private final InputStream stdoutStream;
        private final InputStream stderrStream;
        private volatile OutputLane stdout;
        private volatile OutputLane stderr;
        private volatile InputLane stdin;
        private final AtomicInteger livePumps = new AtomicInteger(3);
        private final CompletableFuture<Void> retired = new CompletableFuture<>();
        private final CompletableFuture<Boolean> stdinNativeRelease = new CompletableFuture<>();
        private final CompletableFuture<Boolean> stdoutNativeRelease = new CompletableFuture<>();
        private final CompletableFuture<Boolean> stderrNativeRelease = new CompletableFuture<>();
        private final AtomicBoolean attachAborted = new AtomicBoolean();
        /** Set once by a successful attach, after every lane was registered and its pump started. */
        private volatile boolean attached;

        private ChildPipes(OutputStream stdin, InputStream stdout, InputStream stderr) {
            this.stdinStream = stdin;
            this.stdoutStream = stdout;
            this.stderrStream = stderr;
        }

        /**
         * True once attach returned normally: every lane's registration was confirmed and its
         * pump started, so every lane accepts transfers. False while attach runs and forever after
         * an attach failure.
         */
        boolean attachedForRuntime() {
            return attached && !attachAborted.get();
        }

        ProtosByteIoFlow.Backend stdinLane() {
            return stdin;
        }

        ProtosByteIoFlow.Backend stdoutLane() {
            return stdout;
        }

        ProtosByteIoFlow.Backend stderrLane() {
            return stderr;
        }

        /** Completes once every lane slot was accounted; its channels are closed by then. */
        CompletionStage<Void> retired() {
            return retired;
        }

        /** Releases the child's stdin; see {@link InputLane#release}. */
        CompletionStage<Boolean> releaseStdin(boolean discard) {
            InputLane lane = stdin;
            if (lane != null && lane.started()) {
                return lane.release(discard);
            }
            return closeNativeOnce(stdinStream, stdinNativeRelease);
        }

        CompletionStage<Boolean> releaseStdout() {
            OutputLane lane = stdout;
            if (lane != null && lane.started()) {
                return lane.release();
            }
            return closeNativeOnce(stdoutStream, stdoutNativeRelease);
        }

        CompletionStage<Boolean> releaseStderr() {
            OutputLane lane = stderr;
            if (lane != null && lane.started()) {
                return lane.release();
            }
            return closeNativeOnce(stderrStream, stderrNativeRelease);
        }

        private void pumpExited() {
            if (livePumps.decrementAndGet() == 0) {
                retired.complete(null);
            }
        }

        /**
         * Attach failed. Every Pipe created so far is closed, so no lane accepts guest transfers
         * and a started stdin pump observes the closed source. Lanes never created and lanes whose
         * pump never ran are accounted here; a pump that runs accounts for itself when it exits.
         */
        private void abortAttach() {
            if (!attachAborted.compareAndSet(false, true)) {
                return;
            }
            Lane[] lanes = {stdout, stderr, stdin};
            for (Lane lane : lanes) {
                if (lane == null) {
                    pumpExited();
                } else {
                    lane.abortChannels();
                    if (!lane.started()) {
                        lane.accountExit();
                    }
                }
            }
        }
    }

    /**
     * Closes one native child stream whose lane never ran a pump; the outcome is the confirmation
     * of that close and is memoized, so an uncertain close is never repeated.
     */
    private static CompletionStage<Boolean> closeNativeOnce(
            java.io.Closeable stream, CompletableFuture<Boolean> outcome) {
        synchronized (outcome) {
            if (!outcome.isDone()) {
                boolean confirmed;
                try {
                    stream.close();
                    confirmed = true;
                } catch (IOException | RuntimeException uncertain) {
                    confirmed = false;
                }
                outcome.complete(confirmed);
            }
        }
        return outcome;
    }

    /** Shared state of one Pipe-backed lane. */
    private abstract class Lane implements ProtosNioHostIoPoller.SelectionHandler {
        final ProtosNioHostIoPoller poller;
        final Pipe pipe;
        final ChildPipes owner;
        /** Set before the pump thread starts; cleared only when that start failed. */
        private final AtomicBoolean pumpStarted = new AtomicBoolean();
        /** Guards the single accounting of this lane's slot in its owner. */
        private final AtomicBoolean exitAccounted = new AtomicBoolean();
        /** Outcome of the poller registration of the selectable end. */
        private final CompletableFuture<Void> registration = new CompletableFuture<>();
        volatile SelectionKey key;

        Lane(ProtosNioHostIoPoller poller, ChildPipes owner) throws IOException {
            this.poller = poller;
            this.owner = owner;
            this.pipe = Pipe.open();
        }

        /** Configures the pump's end; closes the Pipe when that fails, so it never leaks. */
        void configurePumpEnd(java.nio.channels.SelectableChannel pumpEnd) throws IOException {
            try {
                pumpEnd.configureBlocking(true);
            } catch (IOException | RuntimeException failure) {
                abortChannels();
                throw failure;
            }
        }

        abstract java.nio.channels.SelectableChannel selectable();

        abstract void pump();

        abstract String name();

        boolean started() {
            return pumpStarted.get();
        }

        /**
         * Submits the registration of the selectable end. Its outcome, including a refused
         * submission or a failure of the poller action, is recorded in {@link #registration}.
         */
        void requestRegistration() {
            try {
                poller.submit(
                        () -> {
                            try {
                                key = poller.register(selectable(), 0, this);
                                registration.complete(null);
                            } catch (IOException | RuntimeException failure) {
                                registration.completeExceptionally(failure);
                            }
                        },
                        registration::completeExceptionally);
            } catch (RuntimeException refused) {
                registration.completeExceptionally(refused);
            }
        }

        /** Waits for the registration outcome; a failed registration is thrown to attach. */
        void awaitRegistration() throws IOException {
            try {
                registration.get();
            } catch (java.util.concurrent.ExecutionException failure) {
                throw new IOException("child pipe registration failed", failure.getCause());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("child pipe registration was interrupted", interrupted);
            }
        }

        /**
         * Starts the pump of a registered lane. The lane is marked started before the thread can
         * run, so a pump that exits at once is never also accounted by a later attach failure; a
         * thread that failed to start is accounted here, exactly once.
         */
        void start() {
            Thread thread = pumpThread(this::runPump, name());
            pumpStarted.set(true);
            try {
                thread.start();
            } catch (RuntimeException | Error notStarted) {
                pumpStarted.set(false);
                accountExit();
                throw notStarted;
            }
        }

        private void runPump() {
            try {
                pump();
            } finally {
                accountExit();
            }
        }

        void accountExit() {
            if (exitAccounted.compareAndSet(false, true)) {
                owner.pumpExited();
            }
        }

        void abortChannels() {
            closeQuietly(pipe.sink());
            closeQuietly(pipe.source());
        }

        void setInterest(int op, boolean enabled) {
            SelectionKey current = key;
            if (current == null || !current.isValid()) {
                return;
            }
            int ops = current.interestOps();
            int desired = enabled ? ops | op : ops & ~op;
            if (desired != ops) {
                poller.interestOps(current, desired);
            }
        }

        /** Runs {@code action} on the poller thread, or {@code unavailable} when it has stopped. */
        void onPoller(Runnable action, Runnable unavailable) {
            try {
                poller.submit(action, ignored -> unavailable.run());
            } catch (RuntimeException stopped) {
                unavailable.run();
            }
        }
    }

    /** Child stdout or stderr: pump writes into the blocking sink; the poller reads the source. */
    private final class OutputLane extends Lane implements ProtosByteIoFlow.Backend {
        private final InputStream childStream;
        private final String stream;
        private final AtomicReference<ReadRequest> pending = new AtomicReference<>();
        private final CompletableFuture<Boolean> released = new CompletableFuture<>();
        private final AtomicBoolean releaseRequested = new AtomicBoolean();
        /** Set by the pump before it closes the sink when the child stream failed. */
        private volatile boolean physicalFailure;

        OutputLane(
                ProtosNioHostIoPoller poller, InputStream childStream, ChildPipes owner, String stream)
                throws IOException {
            super(poller, owner);
            this.childStream = Objects.requireNonNull(childStream, "childStream");
            this.stream = stream;
            configurePumpEnd(pipe.sink());
        }

        @Override
        java.nio.channels.SelectableChannel selectable() {
            return pipe.source();
        }

        @Override
        String name() {
            return stream;
        }

        /** Same transfer loop as a channel pump: finite chunk, blocking sink, close on EOF. */
        @Override
        void pump() {
            byte[] chunk = new byte[PUMP_CHUNK_BYTES];
            try {
                for (;;) {
                    int count = childStream.read(chunk);
                    if (count < 0) {
                        break;
                    }
                    ByteBuffer buffer = ByteBuffer.wrap(chunk, 0, count);
                    while (buffer.hasRemaining()) {
                        pipe.sink().write(buffer);
                    }
                }
            } catch (IOException | RuntimeException failure) {
                // A release closes the source on purpose; only an unrequested failure is reported
                // to the reader after the bytes already in the Pipe.
                physicalFailure = !releaseRequested.get();
            } finally {
                closeQuietly(pipe.sink());
                released.complete(closeChildStream());
            }
        }

        /** The single physical close of the child stream, run only by this lane's pump. */
        private boolean closeChildStream() {
            try {
                childStream.close();
                return true;
            } catch (IOException | RuntimeException uncertain) {
                return false;
            }
        }

        @Override
        public ProtosByteIoFlow.Cancellation read(
                int maxBytes, ProtosByteIoFlow.ReadCompletion completion) {
            Objects.requireNonNull(completion, "completion");
            if (releaseRequested.get()) {
                reject(completion, ProtosCoreErrors.StandardError.I_O_LIFECYCLE_ERROR);
                return NO_CANCELLATION;
            }
            if (maxBytes <= 0) {
                completion.failed();
                return NO_CANCELLATION;
            }
            ReadRequest request = new ReadRequest(maxBytes, completion);
            onPoller(() -> startRead(request), request::fail);
            return request::cancel;
        }

        @Override
        public ProtosByteIoFlow.Cancellation write(
                byte[] bytes, ProtosByteIoFlow.WriteCompletion completion) {
            throw new IllegalStateException("write reached a child output lane");
        }

        private void startRead(ReadRequest request) {
            if (request.cancelled.get() || key == null || !key.isValid()) {
                request.fail();
                return;
            }
            if (!pending.compareAndSet(null, request)) {
                // ByteIoFlow keeps one transfer in flight per lane; a second one is a defect.
                request.fail();
                return;
            }
            service();
        }

        private void service() {
            ReadRequest request = pending.get();
            if (request == null) {
                setInterestQuietly(false);
                return;
            }
            if (request.cancelled.get()) {
                finish(request, () -> request.fail());
                return;
            }
            ByteBuffer buffer = ByteBuffer.allocate(Math.min(request.maxBytes, READ_CHUNK_BYTES));
            int count;
            try {
                count = pipe.source().read(buffer);
            } catch (IOException failure) {
                finish(request, request::fail);
                return;
            }
            if (count > 0) {
                byte[] bytes = new byte[count];
                buffer.flip();
                buffer.get(bytes);
                finish(request, () -> request.data(bytes));
            } else if (count < 0) {
                finish(request, physicalFailure ? request::fail : request::eof);
            } else {
                try {
                    setInterest(SelectionKey.OP_READ, true);
                } catch (RuntimeException invalidKey) {
                    finish(request, request::fail);
                }
            }
        }

        private void finish(ReadRequest request, Runnable report) {
            if (!pending.compareAndSet(request, null)) {
                return;
            }
            setInterestQuietly(false);
            report.run();
        }

        private void setInterestQuietly(boolean enabled) {
            try {
                setInterest(SelectionKey.OP_READ, enabled);
            } catch (RuntimeException invalidKey) {
                // A closed channel owns its key.
            }
        }

        @Override
        public void ready(SelectionKey selected) {
            if (selected.isReadable()) {
                service();
            }
        }

        @Override
        public void failed(Exception failure) {
            ReadRequest request = pending.getAndSet(null);
            if (request != null) {
                request.fail();
            }
        }

        @Override
        public void closed() {
            failed(null);
        }

        /**
         * Requests the release of the child's native output handle and never blocks the caller,
         * which may be an Actor turn: new reads are rejected and the source end is closed on the
         * poller, so a pump still transferring observes a broken Pipe at its next write. The
         * native stream is closed only by the pump, in its {@code finally}, after EOF, a failure
         * or that broken Pipe. The outcome is that close's confirmation: false when it failed,
         * since the native release is then uncertain and is never retried. While the pump stays
         * blocked reading a stream whose writers remain open, the outcome stays pending and the
         * handle remains in custody. After EOF the pump has already closed the stream, and the
         * memoized outcome is returned.
         */
        CompletionStage<Boolean> release() {
            if (releaseRequested.compareAndSet(false, true)) {
                onPoller(
                        () -> {
                            SelectionKey current = key;
                            if (current != null) {
                                current.cancel();
                            }
                            failed(null);
                            closeQuietly(pipe.source());
                        },
                        () -> closeQuietly(pipe.source()));
            }
            return released;
        }

        private final class ReadRequest {
            final int maxBytes;
            final ProtosByteIoFlow.ReadCompletion completion;
            final AtomicBoolean cancelled = new AtomicBoolean();
            final AtomicBoolean reported = new AtomicBoolean();

            ReadRequest(int maxBytes, ProtosByteIoFlow.ReadCompletion completion) {
                this.maxBytes = maxBytes;
                this.completion = completion;
            }

            /** Bytes not yet read stay in the Pipe, so cancellation never loses committed input. */
            void cancel() {
                if (!cancelled.compareAndSet(false, true) || reported.get()) {
                    return;
                }
                onPoller(
                        () -> {
                            if (pending.get() == this) {
                                finish(this, this::fail);
                            }
                        },
                        () -> {});
            }

            void data(byte[] bytes) {
                if (reported.compareAndSet(false, true)) {
                    completion.data(bytes);
                }
            }

            void eof() {
                if (reported.compareAndSet(false, true)) {
                    completion.eof();
                }
            }

            void fail() {
                if (reported.compareAndSet(false, true)) {
                    completion.failed();
                }
            }
        }
    }

    /** Child stdin: the poller writes into the selectable sink; the pump drains the source. */
    private final class InputLane extends Lane implements ProtosByteIoFlow.Backend {
        private final OutputStream childStream;
        private final AtomicReference<WriteRequest> pending = new AtomicReference<>();
        private final CompletableFuture<Boolean> released = new CompletableFuture<>();
        private final AtomicBoolean releaseRequested = new AtomicBoolean();
        private final AtomicBoolean childStreamClosed = new AtomicBoolean();
        private volatile boolean discard;

        InputLane(ProtosNioHostIoPoller poller, OutputStream childStream, ChildPipes owner)
                throws IOException {
            super(poller, owner);
            this.childStream = Objects.requireNonNull(childStream, "childStream");
            configurePumpEnd(pipe.source());
        }

        @Override
        java.nio.channels.SelectableChannel selectable() {
            return pipe.sink();
        }

        @Override
        String name() {
            return "stdin";
        }

        /**
         * Drains the Pipe into the child in order. EOF of the Pipe (guest close) closes the child
         * stream after every accepted byte was handed to it; a child-side failure closes the
         * source, so later guest writes fail instead of being buffered without a reader.
         */
        @Override
        void pump() {
            ByteBuffer buffer = ByteBuffer.allocate(PUMP_CHUNK_BYTES);
            try {
                for (;;) {
                    buffer.clear();
                    int count = pipe.source().read(buffer);
                    if (count < 0 || discard) {
                        break;
                    }
                    childStream.write(buffer.array(), 0, count);
                    childStream.flush();
                }
            } catch (IOException | RuntimeException failure) {
                // Child no longer accepts input, or a discarding release closed the source.
            } finally {
                closeQuietly(pipe.source());
                released.complete(closeChildStream());
            }
        }

        private boolean closeChildStream() {
            if (!childStreamClosed.compareAndSet(false, true)) {
                return false;
            }
            try {
                childStream.close();
                return true;
            } catch (IOException | RuntimeException uncertain) {
                return false;
            }
        }

        @Override
        public ProtosByteIoFlow.Cancellation read(
                int maxBytes, ProtosByteIoFlow.ReadCompletion completion) {
            throw new IllegalStateException("read reached a child stdin lane");
        }

        @Override
        public ProtosByteIoFlow.Cancellation write(
                byte[] bytes, ProtosByteIoFlow.WriteCompletion completion) {
            Objects.requireNonNull(bytes, "bytes");
            Objects.requireNonNull(completion, "completion");
            if (releaseRequested.get()) {
                rejectWrite(completion, ProtosCoreErrors.StandardError.I_O_LIFECYCLE_ERROR);
                return NO_CANCELLATION;
            }
            if (!(completion instanceof ProtosByteIoFlow.FirstEffectWriteCompletion first)
                    || bytes.length == 0) {
                completion.failed(0);
                return NO_CANCELLATION;
            }
            WriteRequest request = new WriteRequest(bytes, first);
            onPoller(() -> startWrite(request), request::fail);
            return request::cancel;
        }

        private void startWrite(WriteRequest request) {
            if (request.cancelled.get() || key == null || !key.isValid()) {
                request.fail();
                return;
            }
            if (!pending.compareAndSet(null, request)) {
                request.fail();
                return;
            }
            service();
        }

        /** First-effect protocol as for a TCP connection; a partial prefix is never cancelled. */
        private void service() {
            WriteRequest request = pending.get();
            if (request == null) {
                setInterestQuietly(false);
                return;
            }
            if (request.cancelled.get() && request.contributed() == 0) {
                finish(request, request::fail);
                return;
            }
            boolean firstAttempt = request.contributed() == 0;
            if (firstAttempt && !request.completion.beginFirstEffectAttempt()) {
                retire(request);
                return;
            }
            int count;
            try {
                count = pipe.sink().write(request.buffer);
            } catch (IOException failure) {
                if (firstAttempt && !request.completion.finishFirstEffectAttempt(false)) {
                    retire(request);
                    return;
                }
                finish(request, request::fail);
                return;
            }
            if (firstAttempt && !request.completion.finishFirstEffectAttempt(count > 0)) {
                retire(request);
                return;
            }
            if (!request.buffer.hasRemaining()) {
                finish(request, request::succeed);
                return;
            }
            try {
                setInterest(SelectionKey.OP_WRITE, true);
            } catch (RuntimeException invalidKey) {
                finish(request, request::fail);
            }
        }

        private void finish(WriteRequest request, Runnable report) {
            if (!pending.compareAndSet(request, null)) {
                return;
            }
            setInterestQuietly(false);
            report.run();
        }

        private void retire(WriteRequest request) {
            if (pending.compareAndSet(request, null)) {
                setInterestQuietly(false);
                request.reported.set(true);
            }
        }

        private void setInterestQuietly(boolean enabled) {
            try {
                setInterest(SelectionKey.OP_WRITE, enabled);
            } catch (RuntimeException invalidKey) {
                // A closed channel owns its key.
            }
        }

        @Override
        public void ready(SelectionKey selected) {
            if (selected.isWritable()) {
                service();
            }
        }

        @Override
        public void failed(Exception failure) {
            WriteRequest request = pending.getAndSet(null);
            if (request != null) {
                request.fail();
            }
        }

        @Override
        public void closed() {
            failed(null);
        }

        /**
         * Requests the release of the child's stdin handle and never blocks the caller, which may
         * be an Actor turn: only the Pipe ends are closed here, on the poller. Ordinarily the sink
         * is closed after the write in flight, so the pump hands every accepted byte to the child
         * before closing its stream in its {@code finally}; {@code discard} (host-only cleanup)
         * also closes the source, abandoning undelivered bytes. Completes with the pump's
         * confirmation of the child stream's close; while the pump is blocked writing to a child
         * that does not read its input, the outcome stays pending and the handle in custody.
         */
        CompletionStage<Boolean> release(boolean discardPending) {
            if (discardPending) {
                discard = true;
            }
            if (releaseRequested.compareAndSet(false, true) || discardPending) {
                onPoller(
                        () -> {
                            SelectionKey current = key;
                            if (current != null) {
                                current.cancel();
                            }
                            failed(null);
                            closeQuietly(pipe.sink());
                            if (discard) {
                                closeQuietly(pipe.source());
                            }
                        },
                        () -> {
                            closeQuietly(pipe.sink());
                            if (discard) {
                                closeQuietly(pipe.source());
                            }
                        });
            }
            return released;
        }

        private final class WriteRequest {
            final ByteBuffer buffer;
            final ProtosByteIoFlow.FirstEffectWriteCompletion completion;
            final AtomicBoolean cancelled = new AtomicBoolean();
            final AtomicBoolean reported = new AtomicBoolean();

            WriteRequest(byte[] bytes, ProtosByteIoFlow.FirstEffectWriteCompletion completion) {
                this.buffer = ByteBuffer.wrap(bytes);
                this.completion = completion;
            }

            int contributed() {
                return buffer.position();
            }

            void cancel() {
                if (!cancelled.compareAndSet(false, true) || reported.get()) {
                    return;
                }
                onPoller(
                        () -> {
                            if (contributed() == 0 && pending.get() == this) {
                                finish(this, this::fail);
                            }
                        },
                        () -> {});
            }

            void succeed() {
                if (reported.compareAndSet(false, true)) {
                    completion.succeeded();
                }
            }

            void fail() {
                if (reported.compareAndSet(false, true)) {
                    completion.failed(contributed());
                }
            }
        }
    }

    private static void reject(
            ProtosByteIoFlow.ReadCompletion completion, ProtosCoreErrors.StandardError kind) {
        try {
            completion.rejected(kind);
        } catch (RuntimeException ignored) {
            // A defective completion cannot create a second backend outcome.
        }
    }

    private static void rejectWrite(
            ProtosByteIoFlow.WriteCompletion completion, ProtosCoreErrors.StandardError kind) {
        try {
            completion.rejected(kind);
        } catch (RuntimeException ignored) {
            // A defective completion cannot create a second backend outcome.
        }
    }

    private static void closeQuietly(java.nio.channels.Channel channel) {
        try {
            channel.close();
        } catch (IOException ignored) {
            // Host transport cleanup; the child's native handles are released separately.
        }
    }
}
