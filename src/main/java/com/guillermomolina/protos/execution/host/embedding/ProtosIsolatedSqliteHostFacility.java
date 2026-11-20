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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActor;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.guillermomolina.protos.runtime.ProtosProcessStandardStreamBinding;
import com.guillermomolina.protos.runtime.ProtosProcessStandardStreamValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.runtime.callables.ProtosNativeClosureBody;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.io.TruffleProcessBuilder;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.graalvm.polyglot.io.ProcessHandler;

/**
 * Host custody of the confined durable SQLite helper processes of one runtime host.
 *
 * <p>This class only launches, connects, accounts for and retires the helper. It reads no
 * protocol message, executes no SQL and interprets no SQLite result: the protocol, every SQLite
 * operation, the statement table, transactions, durable receipts and reconciliation belong to the
 * Protos modules {@code std:sqlite} (client) and {@code protos/lib/sqlite/isolated-helper.protos}
 * (helper).
 *
 * <p>Authority: a host {@link Grant} names one private directory. Provisioning mints one opaque
 * frozen object that is recognized by identity only and is published solely as the RootActor
 * bootstrap-local {@code sqlite} slot of the Process it is bound to. An ordinary Actor copy of
 * that object has a fresh identity and therefore carries no authority.
 *
 * <p>Launch: the helper is started only through {@link TruffleLanguage.Env#newProcessBuilder}, so
 * a Context that does not allow process creation refuses the launch; there is no other launch
 * path and no unconfined fallback. The executable is always the packaged, digest-verified
 * Landlock launcher; every argument is built here from host state.
 *
 * <p>Custody: each helper belongs to the Actor that opened it. It is retired when its connection
 * closes, when that Actor terminates, when its Process terminates and when the runtime host
 * closes. Retirement closes the channel and then kills the OS process; it never runs guest code.
 * The launcher also installs parent-death delivery, so the helper is killed by the kernel when the
 * host process ends.
 *
 * <p>Launch attempts: every launch first holds a reservation keyed by its session. Withdrawing a
 * reservation (cancellation of that one launch, Actor or Process termination, host close) only
 * ever affects that attempt; cancelling one launch never revokes the Process authority. The
 * reservation is consumed and the helper is recorded in the same monitor section that starts the
 * process, so a withdrawn attempt is never started and a started attempt is always in custody.
 * Whoever removes a reservation owns deleting its private temporary directory.
 */
public final class ProtosIsolatedSqliteHostFacility {
    public static final String BOOTSTRAP_SLOT = "_isolatedSqliteFacility";

    static final String LAUNCHER = "lib/sqlite/sandbox-exec";
    static final String PROVIDER = "lib/sqlite/protos-ffm-provider.jar";
    static final String PROFILE = "lib/sqlite/PROFILE.txt";
    static final String HELPER_ENTRY = "protos/lib/sqlite/isolated-helper.protos";
    static final String PROFILE_NAME = "linux-landlock-abi10";

    private static final int MAX_DATABASE_NAME_OCTETS = 128;
    private static final long RELEASE_WAIT_MILLIS = 5_000;
    private static final long KILL_WAIT_MILLIS = 2_000;

    /**
     * Explicit host selection of one private SQLite directory. {@link #NONE} grants nothing. The
     * directory must already exist: the host never creates it.
     */
    public record Grant(Path directory) {
        public static final Grant NONE = new Grant(null);

        public Grant {
            if (directory != null && !directory.isAbsolute()) {
                throw new IllegalArgumentException("SQLite directory must be absolute");
            }
        }

        public boolean granted() {
            return directory != null;
        }

        /**
         * Validates {@code requested} as a private SQLite directory: absolute, canonical (no
         * symbolic link in any component), an existing directory writable by this user, not
         * writable by others, not the filesystem root, and disjoint from the toolchain and Java
         * runtime this host depends on.
         *
         * @throws IllegalArgumentException with a diagnostic naming the violated condition
         */
        public static Grant directory(Path requested, Path toolchainRoot) {
            Objects.requireNonNull(requested, "requested");
            if (!requested.isAbsolute() || !requested.normalize().equals(requested)) {
                throw new IllegalArgumentException("--allow-sqlite requires an absolute normalized directory");
            }
            Path canonical;
            try {
                canonical = requested.toRealPath();
            } catch (IOException missing) {
                throw new IllegalArgumentException("--allow-sqlite directory does not exist: " + requested);
            }
            if (!canonical.equals(requested)) {
                throw new IllegalArgumentException("--allow-sqlite directory is not canonical: " + requested);
            }
            if (canonical.getParent() == null) {
                throw new IllegalArgumentException("--allow-sqlite cannot grant the filesystem root");
            }
            if (!Files.isDirectory(canonical, LinkOption.NOFOLLOW_LINKS) || !Files.isWritable(canonical)) {
                throw new IllegalArgumentException("--allow-sqlite requires a writable directory: " + requested);
            }
            try {
                Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(canonical);
                if (permissions.contains(PosixFilePermission.OTHERS_WRITE)
                        || permissions.contains(PosixFilePermission.GROUP_WRITE)) {
                    throw new IllegalArgumentException(
                            "--allow-sqlite directory must not be writable by group or others: " + requested);
                }
            } catch (UnsupportedOperationException | IOException unsupported) {
                throw new IllegalArgumentException(
                        "--allow-sqlite directory permissions cannot be verified: " + requested);
            }
            for (Path dependency : runtimeDependencies(toolchainRoot)) {
                if (canonical.startsWith(dependency) || dependency.startsWith(canonical)) {
                    throw new IllegalArgumentException(
                            "--allow-sqlite directory overlaps a runtime dependency: " + requested);
                }
            }
            return new Grant(canonical);
        }
    }

    /** One live helper, owned by exactly one Actor of one Process. */
    private record Helper(
            long session, ProtosProcessRuntime owner, ProtosActor actor, Process process, Path temporary) {}

    private final IdentityHashMap<ProtosObjectValue, Path> grantedDirectories = new IdentityHashMap<>();
    private final IdentityHashMap<ProtosObjectValue, ProtosProcessRuntime> owners = new IdentityHashMap<>();
    private final Map<Long, Helper> helpers = new HashMap<>();
    /** Launch attempts not yet started; their {@code process} is null. */
    private final Map<Long, Helper> reservations = new HashMap<>();
    /** Sessions whose OS process could not be confirmed terminated within the bounded wait. */
    private final List<Long> unconfirmedTerminations = new ArrayList<>();
    /** Test observation of launch stages; never reachable from guest code and grants nothing. */
    volatile java.util.function.BiConsumer<Long, String> launchObserverForTesting;
    private long nextSession = 1;
    private boolean closed;
    /*
     * Parent-death delivery is tied to the OS thread that created the child. Every launch is
     * therefore performed by this one long-lived platform thread, which ends only when the
     * runtime host closes, after every helper has been retired.
     */
    private ExecutorService launcherThread;

    ProtosIsolatedSqliteHostFacility() {}

    /** Mints the opaque authority object of {@code grant}; it carries no slot and no path. */
    synchronized ProtosObjectValue provisionForRuntime(Grant grant) {
        Objects.requireNonNull(grant, "grant");
        if (!grant.granted()) {
            throw new IllegalArgumentException("SQLite grant is NONE");
        }
        if (closed) {
            throw new IllegalStateException("SQLite host facility is closed");
        }
        ProtosObjectValue authority = new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze();
        grantedDirectories.put(authority, grant.directory());
        return authority;
    }

    /** One-time binding of a provisioned authority to the Process whose RootActor receives it. */
    synchronized void bindOwnerForRuntime(ProtosObjectValue authority, ProtosProcessRuntime process) {
        Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(process, "process");
        if (!grantedDirectories.containsKey(authority) || owners.containsKey(authority)) {
            throw new IllegalStateException("SQLite authority is unknown or already bound");
        }
        owners.put(authority, process);
    }

    synchronized boolean authorizedForRuntime(ProtosProcessRuntime process) {
        return !closed && owners.containsValue(process);
    }

    /** Withdraws an authority whose Process bootstrap failed; no helper can exist for it. */
    synchronized void revokeForRuntime(ProtosObjectValue authority) {
        grantedDirectories.remove(authority);
        owners.remove(authority);
    }

    /** Kills every helper opened by {@code actor}; the connections become unusable (EOF). */
    void actorTerminatedForRuntime(ProtosActor actor) {
        withdrawReservations(reservation -> reservation.actor() == actor);
        retire(helper -> helper.actor() == actor);
    }

    /** Kills every helper of {@code process} and withdraws its authority. */
    void processTerminatedForRuntime(ProtosProcessRuntime process) {
        synchronized (this) {
            owners.entrySet().removeIf(entry -> {
                if (entry.getValue() == process) {
                    grantedDirectories.remove(entry.getKey());
                    return true;
                }
                return false;
            });
        }
        withdrawReservations(reservation -> reservation.owner() == process);
        retire(helper -> helper.owner() == process);
    }

    /** Runtime-host retirement: kills every remaining helper and ends the launcher thread. */
    void closeForRuntime() {
        ExecutorService launcher;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            grantedDirectories.clear();
            owners.clear();
            launcher = launcherThread;
            launcherThread = null;
        }
        // No launch is inside its start section once this monitor was acquired, and none can
        // start afterwards: every queued attempt finds itself closed or withdrawn.
        withdrawReservations(reservation -> true);
        retire(helper -> true);
        if (launcher != null) {
            launcher.shutdownNow();
        }
    }

    synchronized int liveHelperCountForTesting() {
        return helpers.size();
    }

    synchronized int pendingLaunchCountForTesting() {
        return reservations.size();
    }

    synchronized List<Long> unconfirmedTerminationsForTesting() {
        return List.copyOf(unconfirmedTerminations);
    }

    /** Withdraws unstarted launch attempts; no process exists for them, so their temporaries go. */
    private void withdrawReservations(java.util.function.Predicate<Helper> selected) {
        List<Helper> withdrawn = new ArrayList<>();
        synchronized (this) {
            reservations.values().removeIf(reservation -> {
                if (selected.test(reservation)) {
                    withdrawn.add(reservation);
                    return true;
                }
                return false;
            });
        }
        for (Helper reservation : withdrawn) {
            deleteTree(reservation.temporary());
        }
    }

    private void retire(java.util.function.Predicate<Helper> selected) {
        List<Helper> retired = new ArrayList<>();
        synchronized (this) {
            helpers.values().removeIf(helper -> {
                if (selected.test(helper)) {
                    retired.add(helper);
                    return true;
                }
                return false;
            });
        }
        for (Helper helper : retired) {
            if (!kill(helper)) {
                synchronized (this) {
                    unconfirmedTerminations.add(helper.session());
                }
            }
        }
    }

    // ---- Private guest bridge of std:sqlite -------------------------------------------------

    /** The private bridge object installed into {@code std:sqlite} and removed by that module. */
    public static ProtosObjectValue createBridge() {
        ProtosObjectValue bridge = new ProtosObjectValue(ProtosObjectValue.rootObject());
        operation(bridge, "launch", 2, ProtosIsolatedSqliteHostFacility::launch);
        operation(bridge, "release", 1, ProtosIsolatedSqliteHostFacility::release);
        operation(bridge, "owns", 1, ProtosIsolatedSqliteHostFacility::owns);
        return bridge.freeze();
    }

    private static void operation(
            ProtosObjectValue receiver, String name, int arity, ProtosNativeClosureBody body) {
        receiver.createLocalSlot(
                name,
                ProtosClosureValue.nativeClosure((activation, supplied) -> {
                    if (activation.receiver() != receiver || supplied.size() != arity) {
                        throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
                    }
                    return body.execute(activation, supplied);
                }));
    }

    /**
     * {@code launch(authority, databaseName)}: answers a fresh frozen object with slots
     * {@code reader} (helper stdout), {@code writer} (helper stdin) and {@code session} (an opaque
     * positive Integer), or a String naming a host refusal. A refusal is never an SQLite result.
     */
    @TruffleBoundary
    private static Object launch(ProtosActivation activation, List<?> supplied) {
        ProtosActor actor = activation.executionDomain().currentActorForRuntime().orElse(null);
        ProtosProcessRuntime process = actor == null ? null : actor.processForRuntime().orElse(null);
        ProtosIsolatedSqliteHostFacility facility = facilityOf(process);
        if (facility == null || !(supplied.get(0) instanceof ProtosObjectValue authority)) {
            return reason("UNAUTHORIZED");
        }
        String databaseName = stringOrNull(supplied.get(1));
        return facility.launchFor(activation, actor, process, authority, databaseName);
    }

    /** Checks host custody without exposing a process handle or changing permissions. */
    @TruffleBoundary
    private static Object owns(ProtosActivation activation, List<?> supplied) {
        ProtosActor actor = activation.executionDomain().currentActorForRuntime().orElse(null);
        ProtosProcessRuntime process = actor == null ? null : actor.processForRuntime().orElse(null);
        ProtosIsolatedSqliteHostFacility facility = facilityOf(process);
        if (facility == null || !(supplied.get(0) instanceof Long session)) return false;
        synchronized (facility) {
            Helper helper = facility.helpers.get(session);
            return helper != null && helper.actor() == actor && helper.owner() == process;
        }
    }

    /**
     * {@code release(session)}: closes the helper's input, waits a bounded time for its exit and
     * kills it otherwise. Answers its exit status, or null when the session is not a live session
     * of the calling Actor or the status could not be established.
     */
    @TruffleBoundary
    private static Object release(ProtosActivation activation, List<?> supplied) {
        ProtosActor actor = activation.executionDomain().currentActorForRuntime().orElse(null);
        ProtosProcessRuntime process = actor == null ? null : actor.processForRuntime().orElse(null);
        ProtosIsolatedSqliteHostFacility facility = facilityOf(process);
        if (facility == null || !(supplied.get(0) instanceof Long session)) {
            return null;
        }
        Helper helper;
        synchronized (facility) {
            helper = facility.helpers.get(session);
            if (helper == null || helper.actor() != actor) {
                return null;
            }
            facility.helpers.remove(session);
        }
        try {
            helper.process().getOutputStream().close();
        } catch (IOException ignored) {
            // The channel may already be broken; the process is still awaited and killed below.
        }
        Long status = null;
        try {
            if (helper.process().waitFor(RELEASE_WAIT_MILLIS, TimeUnit.MILLISECONDS)) {
                status = (long) helper.process().exitValue();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        if (kill(helper)) {
            status = (long) helper.process().exitValue();
        } else {
            synchronized (facility) {
                facility.unconfirmedTerminations.add(session);
            }
        }
        return status;
    }

    private static ProtosIsolatedSqliteHostFacility facilityOf(ProtosProcessRuntime process) {
        if (process == null) {
            return null;
        }
        return process.executionHostForRuntime()
                .filter(ProtosPolyglotProcessContext.class::isInstance)
                .map(host -> ((ProtosPolyglotProcessContext) host).isolatedSqliteForRuntime())
                .orElse(null);
    }

    private Object launchFor(
            ProtosActivation activation,
            ProtosActor actor,
            ProtosProcessRuntime process,
            ProtosObjectValue authority,
            String databaseName) {
        Path directory;
        long session;
        synchronized (this) {
            directory = grantedDirectories.get(authority);
            if (closed || directory == null || owners.get(authority) != process) {
                return reason("UNAUTHORIZED");
            }
            if (!validDatabaseName(databaseName)) {
                return reason("INVALID_DATABASE_NAME");
            }
            if (nextSession == Long.MAX_VALUE) return reason("SESSION_IDS_EXHAUSTED");
            session = nextSession++;
        }
        if (System.getProperty("org.graalvm.nativeimage.imagecode") != null
                || !System.getProperty("os.name", "").toLowerCase(Locale.ROOT).equals("linux")) {
            return reason("PLATFORM_UNSUPPORTED");
        }
        TruffleLanguage.Env env = ProtosLanguageContext.current().env();
        if (!env.isCreateProcessAllowed()) {
            return reason("PROCESS_CREATION_DENIED");
        }
        Path root;
        Path launcher;
        Path provider;
        Path entry;
        try {
            root = ProtosToolchainRoots.core().getParent().getParent().getParent().toRealPath();
            launcher = root.resolve(LAUNCHER).toRealPath();
            provider = root.resolve(PROVIDER).toRealPath();
            entry = root.resolve(HELPER_ENTRY).toRealPath();
            if (!Files.isRegularFile(launcher, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isExecutable(launcher)
                    || !Files.isRegularFile(provider)
                    || !Files.isRegularFile(entry)
                    || !launcherMatchesProfile(root.resolve(PROFILE), launcher)) {
                return reason("LAUNCHER_UNAVAILABLE");
            }
        } catch (IOException | RuntimeException unavailable) {
            return reason("LAUNCHER_UNAVAILABLE");
        }
        try {
            if (!directory.toRealPath().equals(directory)
                    || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                return reason("GRANT_UNAVAILABLE");
            }
        } catch (IOException missing) {
            return reason("GRANT_UNAVAILABLE");
        }
        Path temporary;
        try {
            temporary = Files.createTempDirectory(
                            "protos-sqlite-",
                            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
                    .toRealPath();
        } catch (IOException | UnsupportedOperationException failure) {
            return reason("LAUNCH_FAILED");
        }
        List<String> command;
        List<String> identity;
        try {
            identity = databaseIdentity(directory, databaseName);
        } catch (IOException | RuntimeException failure) {
            deleteTree(temporary);
            return reason("DATABASE_IDENTITY_REFUSED");
        }
        try {
            command = command(root, launcher, provider, entry, directory, temporary, databaseName, session, identity);
        } catch (IOException | IllegalArgumentException failure) {
            deleteTree(temporary);
            return reason("LAUNCHER_UNAVAILABLE");
        }
        TruffleProcessBuilder builder = env.newProcessBuilder(command.toArray(String[]::new))
                .clearEnvironment(true)
                .environment(Map.of())
                .directory(env.getInternalTruffleFile(temporary.toString()))
                .redirectInput(ProcessHandler.Redirect.PIPE)
                .redirectOutput(ProcessHandler.Redirect.PIPE);
        builder.redirectError(builder.createRedirectToStream(OutputStream.nullOutputStream()));
        synchronized (this) {
            if (!publishable(authority, process, actor)) {
                deleteTree(temporary);
                return reason("UNAUTHORIZED");
            }
            reservations.put(session, new Helper(session, process, actor, null, temporary));
        }
        observe(session, "SUBMITTING");
        Process started;
        try {
            // Start and publication happen on the launching thread even if the waiting
            // activation is interrupted. Termination therefore cannot orphan a child.
            started = launcherThread().submit(() -> {
                observe(session, "BEFORE_START");
                synchronized (this) {
                    // Consuming the reservation, starting and recording custody form one section.
                    if (reservations.remove(session) == null) {
                        throw new IOException("SQLite launch was withdrawn");
                    }
                    try {
                        if (!publishable(authority, process, actor)) {
                            throw new IOException("SQLite owner is terminating");
                        }
                        Process child = builder.start();
                        helpers.put(session, new Helper(session, process, actor, child, temporary));
                        return child;
                    } catch (IOException | RuntimeException failure) {
                        deleteTree(temporary);
                        throw failure;
                    }
                }
            }).get();
        } catch (ExecutionException | RuntimeException failure) {
            // Not started: the worker consumed the reservation and deleted the temporary, or the
            // submission was refused and the reservation is withdrawn here.
            withdrawReservations(reservation -> reservation.session() == session);
            return reason("LAUNCH_FAILED");
        } catch (InterruptedException interrupted) {
            // Cancellation of this one launch. The Process authority is untouched. Whichever of
            // the withdrawal or the worker's start section ran first decides: an unstarted
            // attempt is withdrawn and never started; a started one is in custody and retired.
            Thread.currentThread().interrupt();
            return cancelLaunch(session);
        }
        observe(session, "STARTED");
        if (Thread.currentThread().isInterrupted()) {
            return cancelLaunch(session);
        }
        Helper helper;
        synchronized (this) {
            helper = helpers.get(session);
            if (helper == null) return reason("UNAUTHORIZED");
        }
        if (!stillOwned(process, actor)) {
            retire(candidate -> candidate == helper);
            return reason("UNAUTHORIZED");
        }
        ProtosPrelude prelude = activation.prelude().orElseThrow();
        ProtosProcessStandardStreamValue reader;
        ProtosProcessStandardStreamValue writer;
        try {
            reader = process.openAuxiliaryReadableStreamForRuntime(
                    ProtosStandardProcessStreamProtocol.createReadablePrototype(),
                    prelude.bytesPrototypeForRuntime(),
                    ProtosStandaloneHostedExecution.readableBackend(started.getInputStream()));
            writer = process.openAuxiliaryWritableStreamForRuntime(
                    ProtosStandardProcessStreamProtocol.createWritablePrototype(),
                    prelude.bytesPrototypeForRuntime(),
                    flushingBackend(started.getOutputStream()));
        } catch (RuntimeException terminating) {
            retire(candidate -> candidate == helper);
            return reason("UNAUTHORIZED");
        }
        // The auxiliary bindings belong to the Process until it ends; once the helper is retired
        // its pipes are closed, so an unpublished binding can only observe EOF or a failed write.
        synchronized (this) {
            if (helpers.get(session) != helper) return reason("UNAUTHORIZED");
        }
        if (Thread.currentThread().isInterrupted()) {
            return cancelLaunch(session);
        }
        ProtosObjectValue result = new ProtosObjectValue(ProtosObjectValue.rootObject());
        result.createLocalSlot("reader", reader);
        result.createLocalSlot("writer", writer);
        result.createLocalSlot("session", session);
        result.createLocalSlot("identityHi", Long.parseLong(identity.get(0)));
        result.createLocalSlot("identityLo", Long.parseLong(identity.get(1)));
        return result.freeze();
    }

    /** Withdraws or retires exactly the attempt {@code session}; other helpers are unaffected. */
    private Object cancelLaunch(long session) {
        // The bounded termination wait must not be cut short by the cancellation itself; the
        // interrupt status is restored for the caller afterwards.
        boolean interrupted = Thread.interrupted();
        try {
            withdrawReservations(reservation -> reservation.session() == session);
            retire(candidate -> candidate.session() == session);
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        observe(session, "CANCELLED");
        return reason("LAUNCH_CANCELLED");
    }

    /** Publication condition; callers hold this monitor. */
    private boolean publishable(ProtosObjectValue authority, ProtosProcessRuntime process, ProtosActor actor) {
        return !closed && owners.get(authority) == process && stillOwned(process, actor);
    }

    private static boolean stillOwned(ProtosProcessRuntime process, ProtosActor actor) {
        return process.lifecycleState() == ProtosProcessRuntime.LifecycleState.RUNNING
                && actor.lifecycleState() != ProtosActor.LifecycleState.TERMINATING
                && actor.lifecycleState() != ProtosActor.LifecycleState.TERMINATED;
    }

    private void observe(long session, String stage) {
        java.util.function.BiConsumer<Long, String> observer = launchObserverForTesting;
        if (observer != null) {
            observer.accept(session, stage);
        }
    }

    private synchronized ExecutorService launcherThread() {
        if (closed) {
            throw new IllegalStateException("SQLite host facility is closed");
        }
        if (launcherThread == null) {
            launcherThread = Executors.newSingleThreadExecutor(
                    runnable -> Thread.ofPlatform().daemon().name("protos-sqlite-launcher").unstarted(runnable));
        }
        return launcherThread;
    }

    /**
     * Same commitment as {@link ProtosStandaloneHostedExecution#writableBackend}, plus a flush, so
     * every committed request line reaches the helper's input pipe.
     */
    private static ProtosProcessStandardStreamBinding.WritableBackend flushingBackend(OutputStream stream) {
        return (bytes, completion) -> {
            try {
                stream.write(bytes, 0, bytes.length);
                stream.flush();
                completion.succeeded();
            } catch (IOException | RuntimeException failure) {
                completion.failed(0);
            }
            return () -> {};
        };
    }

    private static List<String> command(
            Path root,
            Path launcher,
            Path provider,
            Path entry,
            Path directory,
            Path temporary,
            String databaseName,
            long session,
            List<String> identity)
            throws IOException {
        Path javaHome = Path.of(System.getProperty("java.home")).toRealPath();
        Path java = javaHome.resolve("bin").resolve("java").toRealPath();
        List<String> command = new ArrayList<>();
        command.add(launcher.toString());
        command.addAll(List.of("--expected-parent-pid", Long.toString(ProcessHandle.current().pid())));
        command.addAll(List.of("--db", directory.toString(), "--tmp", temporary.toString()));
        command.addAll(List.of("--rx", javaHome.toString()));
        for (String interpreter : List.of("/lib64/ld-linux-x86-64.so.2", "/lib/ld-linux-aarch64.so.1")) {
            Path path = Path.of(interpreter);
            if (Files.exists(path)) {
                command.addAll(List.of("--rx", path.toRealPath().toString()));
            }
        }
        Set<Path> readable = new LinkedHashSet<>();
        for (String library : List.of("/usr/lib64", "/usr/lib")) {
            if (Files.isDirectory(Path.of(library))) {
                readable.add(Path.of(library).toRealPath());
            }
        }
        for (String element : classPath()) {
            readable.add(Path.of(element).toRealPath());
        }
        readable.add(provider);
        readable.add(root.resolve("protos").resolve("lib").toRealPath());
        for (String system : List.of("/etc/ld.so.cache", "/etc/localtime", "/etc/nsswitch.conf",
                "/etc/passwd", "/etc/group", "/proc", "/sys", "/dev/urandom", "/dev/random")) {
            if (Files.exists(Path.of(system))) {
                readable.add(Path.of(system).toRealPath());
            }
        }
        for (Path path : readable) {
            if (path.startsWith(directory) || directory.startsWith(path)) {
                throw new IllegalArgumentException("SQLite directory overlaps a read grant");
            }
            command.addAll(List.of("--ro", path.toString()));
        }
        command.addAll(List.of("--dev", "/dev/null"));
        command.addAll(List.of(
                "--env", "PROTOS_HOME=" + root,
                "--env", "HOME=" + temporary,
                "--env", "TMPDIR=" + temporary,
                "--env", "LANG=C.UTF-8",
                "--env", "PATH=/usr/bin:/bin"));
        command.add("--");
        command.addAll(List.of(
                java.toString(),
                "--enable-native-access=ALL-UNNAMED",
                "--sun-misc-unsafe-memory-access=allow",
                "-XX:-UsePerfData",
                "-Djava.io.tmpdir=" + temporary,
                "-Duser.home=" + temporary,
                "-Dpolyglot.engine.userResourceCache=" + temporary.resolve("polyglot-cache"),
                "-cp", String.join(File.pathSeparator, classPath()),
                "com.guillermomolina.protos.cli.ProtosCli",
                "--foreign-provider-path", provider.toString(),
                entry.toString(),
                directory.toString(),
                databaseName,
                Long.toString(session),
                identity.get(0), identity.get(1), identity.get(2)));
        return command;
    }

    /**
     * Trusted per-database manifest, never guest IPC. CREATE_NEW is consumed and
     * forced before creating the empty file; a failed first launch requires host
     * intervention. VERIFY never creates a missing database. The locked manifest
     * inode and its containing namespace must remain under trusted host control.
     */
    private static List<String> databaseIdentity(Path directory, String name) throws IOException {
        // The sibling manifest directory is never granted to the helper.
        Path identityDirectory = directory.resolveSibling(directory.getFileName() + ".sqlite-identities");
        try {
            Grant.directory(identityDirectory, null);
        } catch (IllegalArgumentException invalid) {
            throw new IOException("invalid trusted SQLite manifest directory", invalid);
        }
        Path manifest = identityDirectory.resolve(name + ".identity");
        if (!Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("SQLite identity manifest is not a regular file");
        }
        Set<PosixFilePermission> manifestPermissions =
                Files.getPosixFilePermissions(manifest, LinkOption.NOFOLLOW_LINKS);
        if (manifestPermissions.contains(PosixFilePermission.GROUP_WRITE)
                || manifestPermissions.contains(PosixFilePermission.OTHERS_WRITE)) {
            throw new IOException("SQLite identity manifest is not private");
        }
        try (FileChannel channel = FileChannel.open(manifest, StandardOpenOption.READ,
                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                FileLock lock = channel.tryLock()) {
            if (lock == null || !lock.isValid() || channel.size() > 256 || channel.size() == 0) {
                throw new IOException("invalid SQLite identity manifest");
            }
            ByteBuffer buffer = ByteBuffer.allocate((int) channel.size());
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) < 0) throw new IOException("truncated identity manifest");
            }
            String text = new String(buffer.array(), StandardCharsets.US_ASCII);
            String[] lines = text.split("\n", -1);
            if (lines.length != 4 || !lines[0].startsWith("identity_hi=")
                    || !lines[1].startsWith("identity_lo=") || !lines[3].isEmpty()) {
                throw new IOException("invalid SQLite identity manifest");
            }
            String hi = lines[0].substring("identity_hi=".length());
            String lo = lines[1].substring("identity_lo=".length());
            if (!Long.toString(Long.parseLong(hi)).equals(hi)
                    || !Long.toString(Long.parseLong(lo)).equals(lo)) {
                throw new IOException("noncanonical SQLite identity");
            }
            Path database = directory.resolve(name);
            boolean provision = lines[2].equals("provision=CREATE_NEW");
            if (!provision && !lines[2].equals("provision=VERIFY")) {
                throw new IOException("invalid SQLite provisioning mode");
            }
            if (provision) {
                boolean alreadyExists = Files.exists(database, LinkOption.NOFOLLOW_LINKS);
                byte[] consumed = (lines[0] + "\n" + lines[1] + "\nprovision=VERIFY\n")
                        .getBytes(StandardCharsets.US_ASCII);
                channel.position(0);
                ByteBuffer replacement = ByteBuffer.wrap(consumed);
                while (replacement.hasRemaining()) channel.write(replacement);
                channel.truncate(consumed.length);
                channel.force(true);
                if (alreadyExists) {
                    throw new IOException("SQLite provisioning requires a new database");
                }
                Files.createFile(database);
            } else if (!Files.isRegularFile(database, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("SQLite database is absent or not a regular file");
            }
            return List.of(hi, lo, provision ? "PROVISION" : "VERIFY");
        } catch (java.nio.channels.OverlappingFileLockException busy) {
            throw new IOException("SQLite identity manifest is busy", busy);
        }
    }

    private static List<String> classPath() throws IOException {
        List<String> elements = new ArrayList<>();
        for (String element : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
            if (!element.isBlank()) {
                Path path = Path.of(element);
                if (!Files.exists(path)) {
                    throw new IOException("class path element is missing");
                }
                elements.add(path.toRealPath().toString());
            }
        }
        if (elements.isEmpty()) {
            throw new IOException("empty class path");
        }
        return elements;
    }

    /** The packaged launcher digest and confinement profile recorded by the distribution build. */
    private static boolean launcherMatchesProfile(Path profile, Path launcher) throws IOException {
        String expected = null;
        boolean profileMatches = false;
        for (String line : Files.readAllLines(profile, StandardCharsets.UTF_8)) {
            if (line.startsWith("launcher_sha256=")) {
                expected = line.substring("launcher_sha256=".length()).strip();
            }
            if (line.strip().equals("profile=" + PROFILE_NAME)) {
                profileMatches = true;
            }
        }
        if (expected == null || !profileMatches) {
            return false;
        }
        try (InputStream in = Files.newInputStream(launcher)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest()).equals(expected);
        } catch (NoSuchAlgorithmException impossible) {
            return false;
        }
    }

    /**
     * A database name is one plain file name inside the granted directory: 1..128 octets of
     * {@code [A-Za-z0-9._-]}, not starting with '.', and never a SQLite special name.
     */
    static boolean validDatabaseName(String name) {
        if (name == null || name.isEmpty() || name.length() > MAX_DATABASE_NAME_OCTETS || name.charAt(0) == '.') {
            return false;
        }
        for (int index = 0; index < name.length(); index++) {
            char c = name.charAt(index);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '_' || c == '-';
            if (!allowed) {
                return false;
            }
        }
        return !name.endsWith("-journal") && !name.endsWith("-wal") && !name.endsWith("-shm") && !name.endsWith(".identity");
    }

    private static List<Path> runtimeDependencies(Path toolchainRoot) {
        List<Path> dependencies = new ArrayList<>();
        try {
            dependencies.add(Path.of(System.getProperty("java.home")).toRealPath());
        } catch (IOException ignored) {
            // An unresolvable runtime path cannot overlap an existing canonical directory.
        }
        if (toolchainRoot != null) {
            try {
                dependencies.add(toolchainRoot.toRealPath());
            } catch (IOException ignored) {
                // Same as above.
            }
        }
        return dependencies;
    }

    private static Object reason(String text) {
        return new ProtosStringValue(text);
    }

    private static String stringOrNull(Object value) {
        if (value instanceof ProtosStringValue string) {
            return string.value();
        }
        return value instanceof String string ? string : null;
    }

    /**
     * Closes the channel and kills the OS process; never waits without bound. Answers whether
     * termination was confirmed; the temporary is deleted only then.
     */
    private static boolean kill(Helper helper) {
        Process process = helper.process();
        try {
            process.getOutputStream().close();
        } catch (IOException ignored) {
            // Already closed or broken.
        }
        if (process.isAlive()) process.destroyForcibly();
        try {
            // A process in uninterruptible sleep may outlive this bounded wait.
            process.waitFor(KILL_WAIT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        try {
            process.getInputStream().close();
        } catch (IOException ignored) {
            // Already closed.
        }
        if (process.isAlive()) {
            return false;
        }
        deleteTree(helper.temporary());
        return true;
    }

    private static void deleteTree(Path root) {
        if (root == null || !Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // Best effort: a private temporary directory may survive a helper that is not yet reaped.
        }
    }
}
