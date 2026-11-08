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

import com.guillermomolina.protos.runtime.io.ProtosFilesystemNamespaceMutationFlow;
import com.guillermomolina.protos.runtime.io.ProtosFilesystemOpenFlow;
import com.guillermomolina.protos.runtime.io.ProtosFilesystemOpenOptions;
import com.guillermomolina.protos.runtime.io.ProtosFilesystemTreeObservationFlow;
import com.guillermomolina.protos.runtime.io.ProtosPathComponents;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * I156 / D248-B / PLAT072-E Linux backend of the standalone {@code --allow-filesystem=<directory>}
 * grant: a recursive, writable Filesystem confined by the kernel.
 *
 * <p><b>Authority.</b> The root is selected once ({@link #provision}) and pinned as a directory
 * descriptor; its text is never resolved again. Every descendant is selected relative to that
 * descriptor by {@code openat2} with {@code RESOLVE_BENEATH | RESOLVE_NO_SYMLINKS |
 * RESOLVE_NO_MAGICLINKS | RESOLVE_NO_XDEV} plus {@code O_NOFOLLOW | O_CLOEXEC}, so no symbolic
 * link, magic link or other mount (bind mounts included) is ever traversed. Namespace effects
 * name an exact parent descriptor and entry ({@code renameat2}, {@code unlinkat}).
 *
 * <p><b>Kernel enforcement.</b> Every system call of the grant runs on the restricted thread of
 * its own {@link ProtosLinuxConfinedFilesystemWorker}, under a Landlock domain that allows only
 * the grant's rights beneath the root; the grant's operations are orchestrated by that worker's
 * unrestricted driver thread, which hands each system call over synchronously.
 * Namespace mutations are checked against the current hierarchy, including when a parent
 * directory moved outside the root after its descriptor was acquired (T04/T05).
 * Access through an already-open File descriptor retains its previously established rights;
 * Landlock does not reauthorize every read or write after a rename. This confines the
 * operations of this capability; it is not a sandbox of the whole JVM.
 *
 * <p><b>Fail closed.</b> Provisioning succeeds only after the root is pinned and the worker has
 * installed and confirmed its restriction; an unsupported architecture, missing FFM linkage,
 * missing or too old Landlock, refused {@code no_new_privs} or any preparation error refuses the
 * grant before guest code runs. There is no pathname, mount-table or NIO fallback.
 *
 * <p><b>Custody.</b> The backend owns the root descriptor, the worker and every open File
 * descriptor. {@link #close()} is idempotent: it refuses new operations, rejects queued ones, lets
 * the in-flight one finish (its effects re-check revocation first) and closes every descriptor on
 * the restricted thread, without guest execution or garbage collection.
 */
public final class ProtosLinuxConfinedReadWriteTreeFilesystemBackend
        implements ProtosStandardFilesystemProtocol.Backend,
                AutoCloseable,
                ProtosLinuxConfinedReadWriteTreeFileResource.Custody {
    /** Bound on open-or-create re-selection when concurrent creators and removers keep racing. */
    private static final int MAX_CREATE_SELECTIONS = 16;
    private static final AtomicInteger WORKER_SEQUENCE = new AtomicInteger();

    private static final ProtosFilesystemOpenFlow.Cancellation NO_OPEN_CANCELLATION = () -> {};
    private static final ProtosFilesystemNamespaceMutationFlow.Cancellation
            NO_MUTATION_CANCELLATION = () -> {};
    private static final ProtosFilesystemTreeObservationFlow.Cancellation
            NO_ENTRIES_CANCELLATION = () -> {};

    /** Namespace operation reaching its effect, for the private test synchronization point. */
    enum NamespaceOperation {
        REPLACE,
        REMOVE
    }

    /**
     * Private test synchronization point, run on the worker after every parent descriptor of a
     * namespace operation has been acquired and immediately before its effect system call.
     */
    @FunctionalInterface
    interface NamespaceEffectBarrier {
        void reached(NamespaceOperation operation);
    }

    private final int rootFd;
    private final ProtosLinuxConfinedFilesystemWorker worker;
    /* Guarded by this. */
    private final Set<ProtosLinuxConfinedReadWriteTreeFileResource> openResources = new HashSet<>();
    /* Guarded by this. */
    private boolean closed;
    private volatile NamespaceEffectBarrier namespaceEffectBarrier = operation -> {};

    /*
     * Test-only notification immediately after the grant is revoked.
     * Production installs no observer. It confers no authority and executes
     * no Filesystem operation.
     */
    private volatile Runnable revocationObserverForTesting = () -> {};

    private ProtosLinuxConfinedReadWriteTreeFilesystemBackend(
            int rootFd, ProtosLinuxConfinedFilesystemWorker worker) {
        this.rootFd = rootFd;
        this.worker = worker;
    }

    /**
     * Pins the host-selected directory and starts the grant's restricted worker.
     *
     * @param authorizedRoot an absolute host path naming an existing directory whose final
     *     component is not a symbolic link
     * @throws IOException when the root cannot be pinned, or when this host cannot provide every
     *     primitive the confinement requires; nothing remains open in that case
     */
    public static ProtosLinuxConfinedReadWriteTreeFilesystemBackend provision(Path authorizedRoot)
            throws IOException {
        return provision(authorizedRoot, () -> {});
    }

    /**
     * As {@link #provision(Path)}; {@code afterRootPinned} is a private test synchronization point
     * that runs after the root descriptor is pinned and before the worker is restricted (T01).
     */
    static ProtosLinuxConfinedReadWriteTreeFilesystemBackend provision(
            Path authorizedRoot, Runnable afterRootPinned) throws IOException {
        Objects.requireNonNull(authorizedRoot, "authorizedRoot");
        Objects.requireNonNull(afterRootPinned, "afterRootPinned");
        if (!authorizedRoot.isAbsolute()) {
            throw new IllegalArgumentException("authorized Filesystem root must be absolute");
        }
        if (authorizedRoot.getFileSystem() != FileSystems.getDefault()) {
            throw new IOException(
                    "the host filesystem provider cannot confine a writable Filesystem grant");
        }
        ProtosLinuxConfinedFilesystemNative.requireBridge();

        int root = ProtosLinuxConfinedFilesystemNative.openRootDirectory(authorizedRoot.toString());
        try {
            if (!ProtosLinuxConfinedFilesystemNative.fstat(root).directory()) {
                throw new IOException("the authorized Filesystem root is not a directory");
            }
            afterRootPinned.run();
            ProtosLinuxConfinedFilesystemWorker worker =
                    ProtosLinuxConfinedFilesystemWorker.start(
                            "protos-filesystem-grant-" + WORKER_SEQUENCE.incrementAndGet(),
                            () -> {
                                // The restricted thread only ever runs the native bridge.
                                ProtosLinuxConfinedFilesystemNative.preloadForRestrictedThread();
                                ProtosLinuxConfinedFilesystemLandlock.restrictCurrentThreadBeneath(root);
                            });
            return new ProtosLinuxConfinedReadWriteTreeFilesystemBackend(root, worker);
        } catch (IOException | RuntimeException failure) {
            ProtosLinuxConfinedFilesystemNative.closeQuietly(root);
            throw failure;
        }
    }

    @Override
    public ProtosLinuxConfinedFilesystemWorker worker() {
        return worker;
    }

    // ----- operation admission -----

    /**
     * One queued operation. Cancellation withdraws it only while it has not started; once it runs,
     * any irreversible effect is governed by the commitment handshake inside the operation.
     */
    private Runnable submit(Runnable work, Runnable onRefused) {
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
                        // The failure completion is inert once the operation published or
                        // committed (ProtosIoOperation.fail, idempotent flow finish).
                        onRefused.run();
                    }
                };
        if (!isOpen() || !worker.submit(task)) {
            onRefused.run();
            return () -> {};
        }
        return () -> worker.withdraw(task);
    }

    private synchronized boolean isOpen() {
        return !closed;
    }

    // ----- open -----

    @Override
    public ProtosFilesystemOpenFlow.Cancellation open(
            ProtosPathComponents path,
            ProtosFilesystemOpenOptions options,
            ProtosStandardFilesystemProtocol.OpenCompletion completion) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(completion, "completion");
        List<String> components = entryComponents(path);
        if (components == null) {
            completion.failed();
            return NO_OPEN_CANCELLATION;
        }
        Runnable withdraw =
                submit(() -> openOnWorker(components, options, completion), completion::failed);
        return withdraw::run;
    }

    private void openOnWorker(
            List<String> components,
            ProtosFilesystemOpenOptions options,
            ProtosStandardFilesystemProtocol.OpenCompletion completion) {
        if (!isOpen()) {
            completion.failed();
            return;
        }
        int fd;
        try {
            fd = select(ProtosLinuxConfinedFilesystemNames.relativePath(components), options, completion);
        } catch (IOException | RuntimeException failure) {
            completion.failed();
            return;
        }
        if (fd < 0) {
            // Cancellation won before any effect was published; it owns the outcome.
            return;
        }
        try {
            // Only a regular file can honor every advertised File capability (§18.4).
            if (!ProtosLinuxConfinedFilesystemNative.fstat(fd).regular()) {
                ProtosLinuxConfinedFilesystemNative.closeQuietly(fd);
                completion.failed();
                return;
            }
        } catch (ProtosLinuxConfinedFilesystemNative.NativeException failure) {
            ProtosLinuxConfinedFilesystemNative.closeQuietly(fd);
            completion.failed();
            return;
        }
        ProtosLinuxConfinedReadWriteTreeFileResource resource =
                new ProtosLinuxConfinedReadWriteTreeFileResource(
                        this, fd, options.readAccess(), options.writeAccess());
        if (!register(resource)) {
            resource.closeOnWorker();
            completion.failed();
            return;
        }
        completion.succeeded(resource, resource.capabilities(), resource::release);
    }

    /**
     * Selects and acquires the final file. Returns {@code -1} when cancellation won.
     *
     * <p>{@code existing} is one non-creating {@code openat2}. {@code createNew} is one {@code
     * O_CREAT | O_EXCL} {@code openat2} run as the open's native effect. {@code create} tries the
     * non-creating open and, only on {@code ENOENT}, the exclusive creation; an {@code EEXIST} from a
     * concurrent creator re-selects the then-existing target. No existence probe chooses a branch.
     *
     * <p>Truncation never happens inside the open system call: after selection, a single {@code
     * ftruncate(0)} on the selected descriptor is the open's native effect, so a refused or
     * cancelled open modifies nothing and no partial truncation can exist.
     */
    private int select(
            String relative,
            ProtosFilesystemOpenOptions options,
            ProtosStandardFilesystemProtocol.OpenCompletion completion)
            throws IOException {
        ProtosFilesystemOpenOptions.Creation creation = options.creation();
        int base = accessFlags(options);
        for (int selection = 0; selection < MAX_CREATE_SELECTIONS; selection++) {
            if (creation != ProtosFilesystemOpenOptions.Creation.CREATE_NEW) {
                int existing = -1;
                try {
                    existing = ProtosLinuxConfinedFilesystemNative.openBeneath(rootFd, relative, base, 0);
                } catch (ProtosLinuxConfinedFilesystemNative.NativeException failure) {
                    if (creation == ProtosFilesystemOpenOptions.Creation.EXISTING
                            || failure.errno() != ProtosLinuxConfinedFilesystemNative.ENOENT) {
                        throw failure;
                    }
                }
                if (existing >= 0) {
                    return options.truncateInitialContent()
                            ? truncateSelected(existing, completion)
                            : existing;
                }
            }
            int[] created = {-1};
            int[] errno = {0};
            ProtosFilesystemOpenFlow.EffectOutcome outcome =
                    completion.commitPortableEffect(
                            () -> {
                                if (!isOpen()) {
                                    return false;
                                }
                                try {
                                    created[0] =
                                            ProtosLinuxConfinedFilesystemNative.openBeneath(
                                                    rootFd,
                                                    relative,
                                                    base
                                                            | ProtosLinuxConfinedFilesystemNative.O_CREAT
                                                            | ProtosLinuxConfinedFilesystemNative.O_EXCL,
                                                    ProtosLinuxConfinedFilesystemNative.CREATE_MODE);
                                    return true;
                                } catch (ProtosLinuxConfinedFilesystemNative.NativeException failure) {
                                    errno[0] = failure.errno();
                                    return false;
                                }
                            });
            // No enum switch here: its synthetic map class would load lazily on the worker.
            if (outcome == ProtosFilesystemOpenFlow.EffectOutcome.COMMITTED) {
                return created[0];
            }
            if (outcome == ProtosFilesystemOpenFlow.EffectOutcome.CANCELLED) {
                if (created[0] >= 0) {
                    // Not published: the flow already settled the open without this descriptor.
                    ProtosLinuxConfinedFilesystemNative.closeQuietly(created[0]);
                }
                return -1;
            }
            if (creation != ProtosFilesystemOpenOptions.Creation.CREATE
                    || errno[0] != ProtosLinuxConfinedFilesystemNative.EEXIST) {
                throw new IOException("exclusive creation failed (errno " + errno[0] + ")");
            }
            // A concurrent creator won: open-or-create re-selects the then-existing target.
        }
        throw new IOException("concurrent namespace changes prevented an open-or-create selection");
    }

    /** Truncate-on-open of a selected existing descriptor; an empty file is a no-op. */
    private int truncateSelected(int fd, ProtosStandardFilesystemProtocol.OpenCompletion completion)
            throws IOException {
        try {
            if (ProtosLinuxConfinedFilesystemNative.fstat(fd).size() == 0) {
                return fd;
            }
        } catch (IOException failure) {
            ProtosLinuxConfinedFilesystemNative.closeQuietly(fd);
            throw failure;
        }
        ProtosFilesystemOpenFlow.EffectOutcome outcome =
                completion.commitPortableEffect(
                        () -> {
                            if (!isOpen()) {
                                return false;
                            }
                            try {
                                ProtosLinuxConfinedFilesystemNative.ftruncate(fd, 0);
                                return true;
                            } catch (ProtosLinuxConfinedFilesystemNative.NativeException failure) {
                                return false;
                            }
                        });
        if (outcome == ProtosFilesystemOpenFlow.EffectOutcome.COMMITTED) {
            return fd;
        }
        ProtosLinuxConfinedFilesystemNative.closeQuietly(fd);
        if (outcome == ProtosFilesystemOpenFlow.EffectOutcome.CANCELLED) {
            return -1;
        }
        throw new IOException("truncate-on-open failed without effect");
    }

    private static int accessFlags(ProtosFilesystemOpenOptions options) {
        int access;
        if (options.readAccess() && options.writeAccess()) {
            access = ProtosLinuxConfinedFilesystemNative.O_RDWR;
        } else if (options.writeAccess()) {
            access = ProtosLinuxConfinedFilesystemNative.O_WRONLY;
        } else {
            access = ProtosLinuxConfinedFilesystemNative.O_RDONLY;
        }
        // O_NONBLOCK keeps a FIFO or device from blocking the worker; regular files ignore it.
        return access
                | ProtosLinuxConfinedFilesystemNative.O_NOCTTY
                | ProtosLinuxConfinedFilesystemNative.O_NONBLOCK;
    }

    // ----- entries -----

    @Override
    public ProtosFilesystemTreeObservationFlow.Cancellation entries(
            ProtosPathComponents path,
            ProtosFilesystemTreeObservationFlow.EntriesCompletion completion) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(completion, "completion");
        List<String> components = directoryComponents(path);
        if (components == null) {
            completion.failed();
            return NO_ENTRIES_CANCELLATION;
        }
        Runnable withdraw = submit(() -> entriesOnWorker(components, completion), completion::failed);
        return withdraw::run;
    }

    private void entriesOnWorker(
            List<String> components, ProtosFilesystemTreeObservationFlow.EntriesCompletion completion) {
        List<ProtosFilesystemTreeObservationFlow.Entry> observed;
        try {
            if (!isOpen()) {
                throw new IOException("the Filesystem capability was revoked");
            }
            int directory =
                    ProtosLinuxConfinedFilesystemNative.openBeneath(
                            rootFd,
                            ProtosLinuxConfinedFilesystemNames.relativePath(components),
                            ProtosLinuxConfinedFilesystemNative.O_RDONLY
                                    | ProtosLinuxConfinedFilesystemNative.O_DIRECTORY,
                            0);
            try {
                observed = observe(ProtosLinuxConfinedFilesystemNative.readDirectory(directory));
            } finally {
                ProtosLinuxConfinedFilesystemNative.closeQuietly(directory);
            }
        } catch (IOException | RuntimeException failure) {
            completion.failed();
            return;
        }
        completion.succeeded(observed);
    }

    /**
     * Converts one directory reading. Name and kind always come from the same record; a record
     * whose type the filesystem does not report fails the observation instead of being classified
     * by a later, separate lookup that could see a substituted entry.
     */
    private static List<ProtosFilesystemTreeObservationFlow.Entry> observe(
            List<ProtosLinuxConfinedFilesystemNative.DirectoryEntry> records) throws IOException {
        ArrayList<ProtosFilesystemTreeObservationFlow.Entry> observed = new ArrayList<>(records.size());
        HashSet<String> names = new HashSet<>();
        for (ProtosLinuxConfinedFilesystemNative.DirectoryEntry record : records) {
            if (!ProtosLinuxConfinedFilesystemNames.isEntryName(record.name())) {
                throw new IOException("directory entry is not one representable Path component");
            }
            ProtosFilesystemTreeObservationFlow.EntryKind kind =
                    switch (record.type()) {
                        case ProtosLinuxConfinedFilesystemNative.DT_REG ->
                                ProtosFilesystemTreeObservationFlow.EntryKind.REGULAR;
                        case ProtosLinuxConfinedFilesystemNative.DT_DIR ->
                                ProtosFilesystemTreeObservationFlow.EntryKind.DIRECTORY;
                        case ProtosLinuxConfinedFilesystemNative.DT_LNK ->
                                ProtosFilesystemTreeObservationFlow.EntryKind.LINK;
                        case ProtosLinuxConfinedFilesystemNative.DT_UNKNOWN ->
                                throw new IOException("directory entry type is not reported");
                        default -> ProtosFilesystemTreeObservationFlow.EntryKind.OTHER;
                    };
            if (!names.add(record.name())) {
                throw new IOException("directory observation is not one finite exact-name set");
            }
            observed.add(new ProtosFilesystemTreeObservationFlow.Entry(record.name(), kind));
        }
        return List.copyOf(observed);
    }

    // ----- replace / remove -----

    /**
     * One {@code renameat2} between two exact parent descriptors. Source and target are final
     * entries, so a symbolic link moves as itself; the kernel's single transition provides the
     * atomic replacement, and a rename of one entry onto itself is its no-op.
     */
    @Override
    public ProtosFilesystemNamespaceMutationFlow.Cancellation replace(
            ProtosPathComponents sourcePath,
            ProtosPathComponents targetPath,
            ProtosFilesystemNamespaceMutationFlow.MutationCompletion completion) {
        Objects.requireNonNull(sourcePath, "sourcePath");
        Objects.requireNonNull(targetPath, "targetPath");
        Objects.requireNonNull(completion, "completion");
        List<String> source = entryComponents(sourcePath);
        List<String> target = entryComponents(targetPath);
        if (source == null || target == null) {
            completion.failed();
            return NO_MUTATION_CANCELLATION;
        }
        Runnable withdraw = submit(() -> replaceOnWorker(source, target, completion), completion::failed);
        return withdraw::run;
    }

    private void replaceOnWorker(
            List<String> source,
            List<String> target,
            ProtosFilesystemNamespaceMutationFlow.MutationCompletion completion) {
        int sourceParent = -1;
        int targetParent = -1;
        try {
            if (!isOpen()) {
                throw new IOException("the Filesystem capability was revoked");
            }
            sourceParent = openParent(source);
            targetParent = openParent(target);
            int from = sourceParent;
            int to = targetParent;
            namespaceEffectBarrier.reached(NamespaceOperation.REPLACE);
            completion.commitPortableEffect(
                    () -> {
                        requireOpen();
                        ProtosLinuxConfinedFilesystemNative.renameAt(
                                from, lastName(source), to, lastName(target));
                    });
        } catch (IOException | RuntimeException failure) {
            completion.failed();
        } finally {
            closeParent(targetParent);
            closeParent(sourceParent);
        }
    }

    /**
     * Removes exactly the selected final entry with one {@code unlinkat}: a file or link as an
     * entry. When the entry is a directory ({@code EISDIR}), one {@code unlinkat(AT_REMOVEDIR)}
     * removes it only if empty; nothing is ever removed recursively and no prior {@code stat}
     * decides the call. The empty Path names the root, which has no removable entry.
     */
    @Override
    public ProtosFilesystemNamespaceMutationFlow.Cancellation remove(
            ProtosPathComponents path,
            ProtosFilesystemNamespaceMutationFlow.MutationCompletion completion) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(completion, "completion");
        List<String> components = entryComponents(path);
        if (components == null) {
            completion.failed();
            return NO_MUTATION_CANCELLATION;
        }
        Runnable withdraw = submit(() -> removeOnWorker(components, completion), completion::failed);
        return withdraw::run;
    }

    private void removeOnWorker(
            List<String> components,
            ProtosFilesystemNamespaceMutationFlow.MutationCompletion completion) {
        int parent = -1;
        try {
            if (!isOpen()) {
                throw new IOException("the Filesystem capability was revoked");
            }
            parent = openParent(components);
            int directory = parent;
            String name = lastName(components);
            namespaceEffectBarrier.reached(NamespaceOperation.REMOVE);
            completion.commitPortableEffect(
                    () -> {
                        requireOpen();
                        try {
                            ProtosLinuxConfinedFilesystemNative.unlinkAt(directory, name, false);
                        } catch (ProtosLinuxConfinedFilesystemNative.NativeException notAnEntryFile) {
                            if (notAnEntryFile.errno() != ProtosLinuxConfinedFilesystemNative.EISDIR) {
                                throw notAnEntryFile;
                            }
                            ProtosLinuxConfinedFilesystemNative.unlinkAt(directory, name, true);
                        }
                    });
        } catch (IOException | RuntimeException failure) {
            completion.failed();
        } finally {
            closeParent(parent);
        }
    }

    /**
     * The exact parent directory descriptor of a final entry: the pinned root for a top-level
     * entry, otherwise one confined {@code openat2} of the parent components.
     */
    private int openParent(List<String> components) throws IOException {
        if (components.size() == 1) {
            return rootFd;
        }
        return ProtosLinuxConfinedFilesystemNative.openBeneath(
                rootFd,
                ProtosLinuxConfinedFilesystemNames.relativePath(
                        components.subList(0, components.size() - 1)),
                ProtosLinuxConfinedFilesystemNative.O_RDONLY
                        | ProtosLinuxConfinedFilesystemNative.O_DIRECTORY,
                0);
    }

    private void closeParent(int fd) {
        if (fd >= 0 && fd != rootFd) {
            ProtosLinuxConfinedFilesystemNative.closeQuietly(fd);
        }
    }

    /** Fails an effect that has not been published yet once revocation has begun. */
    private void requireOpen() throws IOException {
        if (!isOpen()) {
            throw new IOException("the Filesystem capability was revoked");
        }
    }

    // ----- Path components -----

    /** Components naming a final entry; the empty Path (the root itself) is not one. */
    private static List<String> entryComponents(ProtosPathComponents path) {
        return path.components().isEmpty() ? null : directoryComponents(path);
    }

    /** Components naming a directory; the empty Path selects the root. Invalid names refuse. */
    private static List<String> directoryComponents(ProtosPathComponents path) {
        for (String name : path.components()) {
            if (!ProtosLinuxConfinedFilesystemNames.isEntryName(name)) {
                return null;
            }
        }
        return List.copyOf(path.components());
    }

    private static String lastName(List<String> components) {
        return components.get(components.size() - 1);
    }

    // ----- custody -----

    private synchronized boolean register(ProtosLinuxConfinedReadWriteTreeFileResource resource) {
        if (closed) {
            return false;
        }
        openResources.add(resource);
        return true;
    }

    @Override
    public synchronized void unregister(ProtosLinuxConfinedReadWriteTreeFileResource resource) {
        openResources.remove(resource);
    }

    /**
     * Revokes the grant. New operations are refused at once; queued ones fail; an in-flight one
     * re-checks revocation before its effect. Then every open File descriptor and the root are
     * closed on the restricted thread and the worker ends. Idempotent; a concurrent call waits.
     */
    @Override
    public void close() {
        synchronized (this) {
            closed = true;
        }
        /*
         * The cutover precedes worker shutdown. A test can observe it while
         * an already-running worker operation remains held at its barrier.
         */
        revocationObserverForTesting.run();
        worker.shutdown(this::closeEverythingOnWorker);
    }

    private void closeEverythingOnWorker() {
        List<ProtosLinuxConfinedReadWriteTreeFileResource> retired;
        synchronized (this) {
            retired = List.copyOf(openResources);
        }
        for (ProtosLinuxConfinedReadWriteTreeFileResource resource : retired) {
            resource.closeOnWorker();
        }
        ProtosLinuxConfinedFilesystemNative.closeQuietly(rootFd);
    }

    // ----- test support -----

    static List<ProtosFilesystemTreeObservationFlow.Entry> observeForTesting(
            List<ProtosLinuxConfinedFilesystemNative.DirectoryEntry> records) throws IOException {
        return observe(records);
    }

    void setNamespaceEffectBarrierForTesting(NamespaceEffectBarrier barrier) {
        namespaceEffectBarrier = Objects.requireNonNull(barrier, "barrier");
    }

    void setRevocationObserverForTesting(Runnable observer) {
        revocationObserverForTesting = Objects.requireNonNull(observer, "observer");
    }

    synchronized boolean isClosedForTesting() {
        return closed;
    }

    synchronized int openResourceCountForTesting() {
        return openResources.size();
    }

    int rootDescriptorForTesting() {
        return rootFd;
    }
}
