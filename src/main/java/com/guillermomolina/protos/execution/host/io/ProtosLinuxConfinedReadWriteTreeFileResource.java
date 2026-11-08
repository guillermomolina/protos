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

import com.guillermomolina.protos.runtime.ProtosFileFlow;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

/**
 * One File resource selected by {@link ProtosLinuxConfinedReadWriteTreeFilesystemBackend}.
 *
 * <p>The resource owns one native descriptor acquired by the open, so later rename, replace or
 * removal of the original entry never retargets it (FILESYSTEM.md §18.4). Operations run on the
 * grant's driver thread, every system call they issue executes on the grant's Landlock-restricted
 * thread, and the File flow delivers the guest outcome to the owning Actor.
 *
 * <p>All I/O is positioned ({@code pread64}/{@code pwrite64}) at the explicit offset supplied by
 * {@link ProtosFileFlow}; the descriptor's own cursor is never used, so no native cursor competes
 * with the logical File position. A positioned write that begins beyond end of file leaves a
 * zero-reading hole without any separate fill, so a write that contributes no byte cannot have
 * grown the file (§18.2.1).
 *
 * <p><b>Confinement of state.</b> {@link #closed} and the descriptor are touched only on the
 * grant's driver thread, which serializes them; the descriptor is therefore closed exactly once and no
 * operation can run on a number the kernel has reused. {@code readable}/{@code writable} are the
 * guest-visible access, matching the access mode of the descriptor.
 */
final class ProtosLinuxConfinedReadWriteTreeFileResource
        implements ProtosFileFlow.ReadableResource,
                ProtosFileFlow.WritableResource,
                ProtosFileFlow.SeekableResource,
                ProtosFileFlow.SizedResource,
                ProtosFileFlow.TruncatableResource,
                ProtosFileFlow.SyncableResource {
    private static final int MAX_READ_CHUNK = 64 * 1024;
    private static final ProtosFileFlow.Cancellation NO_CANCELLATION = () -> {};

    /** Grant custody: the restricted worker and the registry of live resources. */
    interface Custody {
        ProtosLinuxConfinedFilesystemWorker worker();

        void unregister(ProtosLinuxConfinedReadWriteTreeFileResource resource);
    }

    private final Custody custody;
    private final int fd;
    private final boolean readable;
    private final boolean writable;
    /* Worker-confined; volatile only so tests can observe it. */
    private volatile boolean closed;

    ProtosLinuxConfinedReadWriteTreeFileResource(
            Custody custody, int fd, boolean readable, boolean writable) {
        this.custody = Objects.requireNonNull(custody, "custody");
        if (fd < 0) {
            throw new IllegalArgumentException("invalid descriptor");
        }
        if (!readable && !writable) {
            throw new IllegalArgumentException("File requires read and/or write access");
        }
        this.fd = fd;
        this.readable = readable;
        this.writable = writable;
    }

    /** The exact capability shape this resource can honor for the given access. */
    ProtosFileFlow.Capabilities capabilities() {
        return new ProtosFileFlow.Capabilities(readable, writable, true, true, writable, true);
    }

    boolean isClosedForTesting() {
        return closed;
    }

    /** Runs {@code work} on the worker, or {@code onRefused} when the grant refuses it. */
    private ProtosFileFlow.Cancellation submit(Runnable work, Runnable onRefused) {
        return submit(work, onRefused, onRefused);
    }

    /**
     * As {@link #submit(Runnable, Runnable)}, with {@code onUnexpected} as the disposition of a
     * started task that threw unexpectedly. It must be safe after the operation already reported
     * its outcome (the flow ignores a second settlement).
     */
    private ProtosFileFlow.Cancellation submit(
            Runnable work, Runnable onRefused, Runnable onUnexpected) {
        ProtosLinuxConfinedFilesystemWorker worker = custody.worker();
        ProtosLinuxConfinedFilesystemWorker.Task task =
                new ProtosLinuxConfinedFilesystemWorker.Task() {
                    @Override
                    public void run() {
                        work.run();
                    }

                    @Override
                    public void rejected() {
                        onRefused.run();
                    }

                    @Override
                    public void failedUnexpectedly(Throwable cause) {
                        onUnexpected.run();
                    }
                };
        if (!worker.submit(task)) {
            onRefused.run();
            return NO_CANCELLATION;
        }
        // Withdrawing a queued request is the only cancellation: started I/O runs to its result.
        return () -> worker.withdraw(task);
    }

    /**
     * Test-only fault injection, scoped to this File. Never affects other Files,
     * grants or tests running concurrently.
     */
    private volatile Runnable faultAfterContributionForTesting;

    void setFaultAfterContributionForTesting(Runnable fault) {
        faultAfterContributionForTesting = fault;
    }

    @Override
    public ProtosFileFlow.Cancellation readAt(
            long position, int maxBytes, ProtosFileFlow.ReadCompletion completion) {
        Objects.requireNonNull(completion, "completion");
        if (!readable || position < 0 || maxBytes <= 0) {
            completion.failed();
            return NO_CANCELLATION;
        }
        return submit(() -> readOnWorker(position, maxBytes, completion), completion::failed);
    }

    private void readOnWorker(long position, int maxBytes, ProtosFileFlow.ReadCompletion completion) {
        if (closed) {
            completion.failed();
            return;
        }
        byte[] bytes;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buffer = arena.allocate(Math.min(maxBytes, MAX_READ_CHUNK));
            long read = ProtosLinuxConfinedFilesystemNative.pread(fd, buffer, position);
            if (read == 0) {
                completion.eof();
                return;
            }
            if (read < 0 || read > buffer.byteSize()) {
                completion.failed();
                return;
            }
            bytes = buffer.asSlice(0, read).toArray(ValueLayout.JAVA_BYTE);
        } catch (ProtosLinuxConfinedFilesystemNative.NativeException failure) {
            completion.failed();
            return;
        }
        completion.data(bytes);
    }

    /**
     * Positioned overwrite. The commitment handshake runs before the first {@code pwrite64}; the
     * reported prefix is exactly the number of bytes the kernel accepted, including after a
     * partial failure.
     */
    @Override
    public ProtosFileFlow.Cancellation writeAt(
            long position, byte[] bytes, ProtosFileFlow.WriteCompletion completion) {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(completion, "completion");
        if (!writable || position < 0 || position > Long.MAX_VALUE - bytes.length) {
            completion.failed(0);
            return NO_CANCELLATION;
        }
        byte[] snapshot = bytes.clone();
        // A refused write contributed nothing; a write that threw may have contributed anything.
        return submit(
                () -> writeOnWorker(position, snapshot, completion),
                () -> completion.failed(0),
                completion::failedWithUnknownProgress);
    }

    private void writeOnWorker(long position, byte[] bytes, ProtosFileFlow.WriteCompletion completion) {
        if (closed) {
            completion.failed(0);
            return;
        }
        if (bytes.length == 0) {
            // An empty sequence neither modifies nor extends the resource.
            completion.succeeded();
            return;
        }
        if (!completion.commitFirstContribution()) {
            return;
        }
        long contributed = 0;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buffer = arena.allocate(bytes.length);
            MemorySegment.copy(bytes, 0, buffer, ValueLayout.JAVA_BYTE, 0, bytes.length);
            while (contributed < bytes.length) {
                long accepted;
                try {
                    accepted =
                            ProtosLinuxConfinedFilesystemNative.pwrite(
                                    fd,
                                    buffer.asSlice(contributed),
                                    position + contributed);
                } catch (ProtosLinuxConfinedFilesystemNative.NativeException failure) {
                    break;
                }
                if (accepted <= 0 || accepted > bytes.length - contributed) {
                    break;
                }
                contributed += accepted;
                Runnable fault = faultAfterContributionForTesting;
                if (fault != null) {
                    fault.run();
                }
            }
        }
        if (contributed == bytes.length) {
            completion.succeeded();
        } else {
            completion.failed((int) contributed);
        }
    }

    @Override
    public ProtosFileFlow.Cancellation endPosition(ProtosFileFlow.IntegerCompletion completion) {
        return size(completion);
    }

    @Override
    public ProtosFileFlow.Cancellation size(ProtosFileFlow.IntegerCompletion completion) {
        Objects.requireNonNull(completion, "completion");
        return submit(() -> sizeOnWorker(completion), completion::failed);
    }

    private void sizeOnWorker(ProtosFileFlow.IntegerCompletion completion) {
        if (closed) {
            completion.failed();
            return;
        }
        long size;
        try {
            size = ProtosLinuxConfinedFilesystemNative.fstat(fd).size();
        } catch (ProtosLinuxConfinedFilesystemNative.NativeException failure) {
            completion.failed();
            return;
        }
        if (size < 0) {
            completion.failed();
        } else {
            completion.succeeded(size);
        }
    }

    /** One {@code ftruncate} on the same descriptor: never extends, and a no-op commits nothing. */
    @Override
    public ProtosFileFlow.Cancellation truncate(long size, ProtosFileFlow.ChangeCompletion completion) {
        Objects.requireNonNull(completion, "completion");
        if (!writable || size < 0) {
            completion.failed();
            return NO_CANCELLATION;
        }
        return submit(() -> truncateOnWorker(size, completion), completion::failed);
    }

    private void truncateOnWorker(long size, ProtosFileFlow.ChangeCompletion completion) {
        if (closed) {
            completion.failed();
            return;
        }
        try {
            if (size >= ProtosLinuxConfinedFilesystemNative.fstat(fd).size()) {
                completion.succeeded();
                return;
            }
            if (!completion.commitChange()) {
                return;
            }
            ProtosLinuxConfinedFilesystemNative.ftruncate(fd, size);
        } catch (ProtosLinuxConfinedFilesystemNative.NativeException failure) {
            completion.failed();
            return;
        }
        completion.succeeded();
    }

    /** {@code fsync} of the descriptor: exactly the durability barrier of {@code File.sync()}. */
    @Override
    public ProtosFileFlow.Cancellation sync(ProtosFileFlow.SyncCompletion completion) {
        Objects.requireNonNull(completion, "completion");
        return submit(() -> syncOnWorker(completion), completion::failed);
    }

    private void syncOnWorker(ProtosFileFlow.SyncCompletion completion) {
        if (closed) {
            completion.failed();
            return;
        }
        if (!completion.commitDurability()) {
            return;
        }
        try {
            ProtosLinuxConfinedFilesystemNative.fsync(fd);
        } catch (ProtosLinuxConfinedFilesystemNative.NativeException failure) {
            completion.failed();
            return;
        }
        completion.succeeded();
    }

    /**
     * Ordinary File close; idempotent. When the grant is already revoked, its final cleanup has
     * closed (or will close) the descriptor, so the close still succeeds.
     */
    @Override
    public void close(ProtosFileFlow.CloseCompletion completion) {
        Objects.requireNonNull(completion, "completion");
        ProtosLinuxConfinedFilesystemWorker.Task task =
                new ProtosLinuxConfinedFilesystemWorker.Task() {
                    @Override
                    public void run() {
                        if (closeOnWorker()) {
                            completion.succeeded();
                        } else {
                            completion.failed();
                        }
                    }

                    @Override
                    public void rejected() {
                        completion.succeeded();
                    }

                    @Override
                    public void failedUnexpectedly(Throwable cause) {
                        // The close attempt did not report its result: it is not a success.
                        completion.failed();
                    }
                };
        if (!custody.worker().submitCleanup(task)) {
            completion.succeeded();
        }
    }

    /**
     * Custody release of an untransferred open result. It only queues the close; when the grant
     * is already revoked, the grant's final cleanup closes every still-registered descriptor.
     */
    void release() {
        ProtosLinuxConfinedFilesystemWorker worker = custody.worker();
        if (worker.isWorkerThread()) {
            closeOnWorker();
            return;
        }
        worker.submitCleanup(
                new ProtosLinuxConfinedFilesystemWorker.Task() {
                    @Override
                    public void run() {
                        closeOnWorker();
                    }

                    @Override
                    public void rejected() {
                        // Revocation's final cleanup owns the still-registered descriptor.
                    }

                    @Override
                    public void failedUnexpectedly(Throwable cause) {
                        // Custody release has no observer; a still-registered descriptor stays
                        // owned by the grant's final cleanup.
                    }
                });
    }

    /**
     * Closes the descriptor once and leaves the registry. Must run on the grant's driver thread.
     *
     * @return false when the kernel reported a close failure (the descriptor is still released)
     */
    boolean closeOnWorker() {
        if (closed) {
            return true;
        }
        closed = true;
        custody.unregister(this);
        try {
            ProtosLinuxConfinedFilesystemNative.close(fd);
            return true;
        } catch (ProtosLinuxConfinedFilesystemNative.NativeException failure) {
            return false;
        }
    }
}
