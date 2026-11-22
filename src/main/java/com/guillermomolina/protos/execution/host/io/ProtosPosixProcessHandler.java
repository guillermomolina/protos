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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.graalvm.polyglot.io.ProcessHandler;

/**
 * Host-selected Linux process provider. Install on the Context builder before building it;
 * selection grants neither process creation nor guest native access.
 *
 * <p>Launch still passes through Truffle's process authorization. This provider owns the native
 * pipes and cached reap; stream reads never consult child liveness. The returned Process has no
 * guest references, and no typed terminal-state channel through Truffle's Process decoration.
 * Stream owners must retire their active transfer before closing that stream.
 */
public final class ProtosPosixProcessHandler implements ProcessHandler {
    private static final String RESOURCE = "/META-INF/protos/native/libprotos_posix_process.so";
    private static final int CHUNK_BYTES = 8192;

    private final Arena libraryArena = Arena.ofAuto();
    private final SymbolLookup symbols;
    private final List<Path> searchDirectories;
    private final Path defaultDirectory = Path.of("").toAbsolutePath();
    private final MethodHandle start;
    private final MethodHandle poll;
    private final MethodHandle signal;
    private final MethodHandle read;
    private final MethodHandle write;
    private final MethodHandle close;
    private final MethodHandle release;
    private final MethodHandle wifexited;
    private final MethodHandle wifsignaled;
    private final MethodHandle wexitstatus;
    private final MethodHandle wtermsig;

    /** Loads the packaged host library; failure never falls back to the JDK process provider. */
    @TruffleBoundary
    public ProtosPosixProcessHandler() throws IOException {
        this(null);
    }

    /**
     * Loads an explicit host library, or the packaged library when {@code library} is null.
     * An explicit path is useful to an embedding host that owns its native-library deployment.
     */
    @TruffleBoundary
    public ProtosPosixProcessHandler(Path library) throws IOException {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).equals("linux")) {
            throw new IOException("the native process provider requires Linux");
        }
        Path extracted = null;
        try {
            if (library == null) {
                try (InputStream resource = ProtosPosixProcessHandler.class.getResourceAsStream(RESOURCE)) {
                    if (resource == null) {
                        throw new IOException("the packaged native process provider is missing");
                    }
                    extracted = Files.createTempFile("protos-process-", ".so");
                    Files.copy(resource, extracted, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    library = extracted;
                }
            }
            symbols = SymbolLookup.libraryLookup(library.toAbsolutePath(), libraryArena);
            start = bind("protos_process_start", FunctionDescriptor.of(
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            poll = bind("protos_process_poll", FunctionDescriptor.of(
                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            signal = bind("protos_process_signal", FunctionDescriptor.of(
                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            read = bind("protos_process_read", FunctionDescriptor.of(
                    ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
            write = bind("protos_process_write", FunctionDescriptor.of(
                    ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
            close = bind("protos_process_close", FunctionDescriptor.of(
                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            release = bind("protos_process_release", FunctionDescriptor.of(
                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            wifexited = bind("protos_process_wifexited", FunctionDescriptor.of(
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            wifsignaled = bind("protos_process_wifsignaled", FunctionDescriptor.of(
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            wexitstatus = bind("protos_process_wexitstatus", FunctionDescriptor.of(
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            wtermsig = bind("protos_process_wtermsig", FunctionDescriptor.of(
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        } catch (RuntimeException | LinkageError unavailable) {
            throw new IOException("the native process provider could not be initialized", unavailable);
        } finally {
            // Linux keeps the loaded mapping alive after unlink; no extraction directory survives.
            if (extracted != null) {
                Files.deleteIfExists(extracted);
            }
        }
        String path = System.getenv("PATH");
        List<Path> directories = new ArrayList<>();
        for (String entry : (path == null ? "/bin:/usr/bin" : path).split(":", -1)) {
            directories.add(defaultDirectory.resolve(entry).normalize());
        }
        searchDirectories = List.copyOf(directories);
    }

    @TruffleBoundary
    private MethodHandle bind(String name, FunctionDescriptor descriptor) throws IOException {
        MemorySegment symbol = symbols.find(name)
                .orElseThrow(() -> new IOException("native process symbol is missing: " + name));
        return Linker.nativeLinker().downcallHandle(symbol, descriptor);
    }

    @Override
    @TruffleBoundary
    public Process start(ProcessCommand command) throws IOException {
        Objects.requireNonNull(command, "command");
        if (command.isRedirectErrorStream() || command.getInputRedirect() != Redirect.PIPE
                || command.getOutputRedirect() != Redirect.PIPE || command.getErrorRedirect() != Redirect.PIPE) {
            throw new IOException("the native process provider requires three separate PIPE redirects");
        }
        List<String> arguments = new ArrayList<>(command.getCommand());
        if (arguments.isEmpty()) {
            throw new IOException("the process command is empty");
        }
        String executable = arguments.getFirst();
        Path selected = Path.of(executable);
        if (!selected.isAbsolute()) {
            if (executable.isEmpty() || executable.contains("/")) {
                throw new IOException("the executable must be absolute or a simple name");
            }
            selected = null;
            for (Path directory : searchDirectories) {
                Path candidate = directory.resolve(executable);
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                    selected = candidate;
                    break;
                }
            }
            if (selected == null) {
                throw new IOException("the executable was not found by the host search policy");
            }
        }
        // Native creation receives one absolute executable and no implicit shell or environment.
        arguments.set(0, selected.toString());
        List<String> environment = new ArrayList<>();
        for (var entry : command.getEnvironment().entrySet()) {
            if (entry.getKey().isEmpty() || entry.getKey().contains("=")) {
                throw new IOException("the environment contains an invalid name");
            }
            environment.add(entry.getKey() + "=" + entry.getValue());
        }
        Path directory = command.getDirectory() == null
                ? defaultDirectory : Path.of(command.getDirectory());
        if (!directory.isAbsolute()) {
            throw new IOException("the process working directory must be absolute");
        }

        // Allocate the Process, streams and status cell before the native commitment frontier.
        NativeProcess child = new NativeProcess();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment argv = arena.allocate(ValueLayout.ADDRESS, arguments.size() + 1L);
            MemorySegment envp = arena.allocate(ValueLayout.ADDRESS, environment.size() + 1L);
            List<String> strings = new ArrayList<>(arguments);
            strings.addAll(environment);
            strings.add(directory.toString());
            MemorySegment cwd = MemorySegment.NULL;
            for (int index = 0; index < strings.size(); index++) {
                String text = strings.get(index);
                if (text.indexOf('\0') >= 0) {
                    throw new IOException("a process attribute contains NUL");
                }
                var encoded = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(text));
                MemorySegment bytes = arena.allocate(encoded.remaining() + 1L);
                int length = encoded.remaining();
                bytes.asSlice(0, length).asByteBuffer().put(encoded);
                if (index < arguments.size()) {
                    argv.setAtIndex(ValueLayout.ADDRESS, index, bytes);
                } else if (index < arguments.size() + environment.size()) {
                    envp.setAtIndex(ValueLayout.ADDRESS, index - arguments.size(), bytes);
                } else {
                    cwd = bytes;
                }
            }
            MemorySegment error = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment createdPid = arena.allocate(ValueLayout.JAVA_LONG);
            // The single creating call also yields the fork identity; no later query exists.
            child.handle = (MemorySegment) start.invokeExact(argv, envp, cwd, error, createdPid);
            child.hostPid = createdPid.get(ValueLayout.JAVA_LONG, 0);
            int launchError = error.get(ValueLayout.JAVA_INT, 0);
            if (child.handle.equals(MemorySegment.NULL)) {
                throw new IOException("native process launch failed (errno " + launchError + ")");
            }
            if (launchError != 0) {
                throw new IOException("native process launch requires reclamation (errno " + launchError + ")");
            }
            return child;
        } catch (Throwable failure) {
            /*
             * Before ProcessHandler.start returns, this launch remains its caller's pending
             * commitment. A possible child must be reclaimed before an exception can imply
             * that no child remains. No second supervisor or global process table is created.
             */
            boolean interrupted = false;
            if (!child.handle.equals(MemorySegment.NULL)) {
                for (;;) {
                    try {
                        child.destroyForcibly();
                        child.waitFor();
                        child.stdin.close();
                        child.stdout.close();
                        child.stderr.close();
                        if (child.handle.equals(MemorySegment.NULL)) {
                            break;
                        }
                    } catch (InterruptedException retry) {
                        interrupted = true;
                    } catch (IOException | RuntimeException | Error retry) {
                        try {
                            Thread.sleep(20);
                        } catch (InterruptedException ignored) {
                            interrupted = true;
                        }
                    }
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
            if (failure instanceof Error fatal) {
                throw fatal;
            }
            if (failure instanceof IOException io) {
                throw io;
            }
            throw new IOException("native process launch failed", failure);
        }
    }

    private final class NativeProcess extends Process {
        private volatile MemorySegment handle = MemorySegment.NULL;
        private final MemorySegment status = Arena.ofAuto().allocate(ValueLayout.JAVA_INT);
        private final NativeInput stdout = new NativeInput(this, 1);
        private final NativeInput stderr = new NativeInput(this, 2);
        private final NativeOutput stdin = new NativeOutput(this);
        private long hostPid;
        private boolean reaped;
        private int rawStatus;
        private int exitCode;
        private boolean terminalVerified;
        private int closedStreams;

        @Override
        public InputStream getInputStream() {
            return stdout;
        }

        @Override
        public OutputStream getOutputStream() {
            return stdin;
        }

        @Override
        public InputStream getErrorStream() {
            return stderr;
        }

        @Override
        public long pid() {
            return hostPid;
        }

        @Override
        @TruffleBoundary
        public int waitFor() throws InterruptedException {
            while (isAlive()) {
                Thread.sleep(20);
            }
            return exitValue();
        }

        @Override
        @TruffleBoundary
        public synchronized int exitValue() {
            if (!observeExit()) {
                throw new IllegalThreadStateException("the native child is still running");
            }
            if (!terminalVerified) {
                throw new IllegalStateException("the reaped native status is not a terminal exit or signal");
            }
            return exitCode;
        }

        @Override
        @TruffleBoundary
        public synchronized boolean isAlive() {
            return !observeExit();
        }

        @TruffleBoundary
        private boolean observeExit() {
            try {
                int result = reaped ? 1 : (int) poll.invokeExact(handle, status);
                if (result < 0) {
                    throw new IllegalStateException("native child supervision failed (errno " + -result + ")");
                }
                if (result == 0) {
                    return false;
                }
                if (!reaped) {
                    rawStatus = status.get(ValueLayout.JAVA_INT, 0);
                    /*
                     * Normalized POSIX return code decoded from the single reaped status: a
                     * normal exit keeps WEXITSTATUS (0..255); a signal termination is the
                     * negated WTERMSIG. Any other status publishes no terminal result.
                     */
                    if ((int) wifexited.invokeExact(rawStatus) != 0) {
                        exitCode = (int) wexitstatus.invokeExact(rawStatus);
                        terminalVerified = true;
                    } else if ((int) wifsignaled.invokeExact(rawStatus) != 0) {
                        exitCode = -(int) wtermsig.invokeExact(rawStatus);
                        terminalVerified = exitCode < 0;
                    }
                    reaped = true;
                }
                if (closedStreams == 7 && !handle.equals(MemorySegment.NULL)) {
                    int released = (int) release.invokeExact(handle);
                    if (released < 0) {
                        throw new IllegalStateException("native child state could not be released");
                    }
                    handle = MemorySegment.NULL;
                }
                return true;
            } catch (RuntimeException | Error failure) {
                throw failure;
            } catch (Throwable failure) {
                throw new IllegalStateException("native child supervision failed", failure);
            }
        }

        @Override
        @TruffleBoundary
        public synchronized void destroy() {
            signal(15);
        }

        @Override
        @TruffleBoundary
        public synchronized Process destroyForcibly() {
            signal(9);
            return this;
        }

        @TruffleBoundary
        private void signal(int number) {
            if (observeExit()) {
                return;
            }
            try {
                int result = (int) ProtosPosixProcessHandler.this.signal.invokeExact(handle, number);
                if (result < 0) {
                    throw new IllegalStateException("native child termination request failed (errno " + -result + ")");
                }
            } catch (RuntimeException | Error failure) {
                throw failure;
            } catch (Throwable failure) {
                throw new IllegalStateException("native child termination request failed", failure);
            }
        }

        @TruffleBoundary
        private synchronized void closeStream(int stream) throws IOException {
            int bit = 1 << stream;
            if ((closedStreams & bit) != 0) {
                return;
            }
            try {
                int result = (int) close.invokeExact(handle, stream);
                // The Linux descriptor is released even when close reports an error.
                closedStreams |= bit;
                if (closedStreams == 7 && reaped) {
                    int released = (int) release.invokeExact(handle);
                    if (released < 0) {
                        throw new IOException("native child state could not be released");
                    }
                    handle = MemorySegment.NULL;
                }
                if (result < 0) {
                    throw new IOException("native process stream close failed (errno " + -result + ")");
                }
            } catch (IOException failure) {
                throw failure;
            } catch (RuntimeException | Error failure) {
                throw failure;
            } catch (Throwable failure) {
                throw new IOException("native process stream close invocation failed", failure);
            }
        }
    }

    private final class NativeInput extends InputStream {
        private final NativeProcess owner;
        private final int stream;
        private boolean closed;

        private NativeInput(NativeProcess owner, int stream) {
            this.owner = owner;
            this.stream = stream;
        }

        @Override
        @TruffleBoundary
        public synchronized int read() throws IOException {
            byte[] single = new byte[1];
            return read(single, 0, 1) < 0 ? -1 : Byte.toUnsignedInt(single[0]);
        }

        @Override
        @TruffleBoundary
        public synchronized int read(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            if (closed) {
                throw new IOException("the native process stream is closed");
            }
            if (length == 0) {
                return 0;
            }
            try (Arena arena = Arena.ofConfined()) {
                int count = Math.min(length, CHUNK_BYTES);
                MemorySegment buffer = arena.allocate(count);
                long result = (long) ProtosPosixProcessHandler.this.read.invokeExact(
                        owner.handle, stream, buffer, (long) count);
                if (result < 0) {
                    throw new IOException("native process stream read failed (errno " + -result + ")");
                }
                if (result == 0) {
                    return -1;
                }
                MemorySegment.copy(buffer, ValueLayout.JAVA_BYTE, 0, bytes, offset, (int) result);
                return (int) result;
            } catch (IOException failure) {
                throw failure;
            } catch (RuntimeException | Error failure) {
                throw failure;
            } catch (Throwable failure) {
                throw new IOException("native process stream read invocation failed", failure);
            }
        }

        @Override
        @TruffleBoundary
        public synchronized void close() throws IOException {
            if (!closed) {
                closed = true;
                owner.closeStream(stream);
            }
        }
    }

    private final class NativeOutput extends OutputStream {
        private final NativeProcess owner;
        private boolean closed;

        private NativeOutput(NativeProcess owner) {
            this.owner = owner;
        }

        @Override
        @TruffleBoundary
        public synchronized void write(int value) throws IOException {
            write(new byte[] {(byte) value}, 0, 1);
        }

        @Override
        @TruffleBoundary
        public synchronized void write(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            if (closed) {
                throw new IOException("the native process stream is closed");
            }
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment buffer = arena.allocate(Math.min(Math.max(length, 1), CHUNK_BYTES));
                int remaining = length;
                while (remaining > 0) {
                    int chunk = Math.min(remaining, CHUNK_BYTES);
                    MemorySegment.copy(bytes, offset, buffer, ValueLayout.JAVA_BYTE, 0, chunk);
                    int written = 0;
                    while (written < chunk) {
                        long result = (long) ProtosPosixProcessHandler.this.write.invokeExact(
                                owner.handle, buffer.asSlice(written), (long) (chunk - written));
                        if (result <= 0) {
                            throw new IOException("native process stream write failed (result " + result + ")");
                        }
                        written += (int) result;
                    }
                    remaining -= chunk;
                    offset += chunk;
                }
            } catch (IOException failure) {
                throw failure;
            } catch (RuntimeException | Error failure) {
                throw failure;
            } catch (Throwable failure) {
                throw new IOException("native process stream write invocation failed", failure);
            }
        }

        @Override
        @TruffleBoundary
        public synchronized void close() throws IOException {
            if (!closed) {
                closed = true;
                owner.closeStream(0);
            }
        }
    }
}
