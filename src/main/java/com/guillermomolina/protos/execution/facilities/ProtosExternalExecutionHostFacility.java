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
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosByteIoFlow;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosEnvironmentValue;
import com.guillermomolina.protos.runtime.ProtosExternalChildValue;
import com.guillermomolina.protos.runtime.ProtosExternalChildValue.TerminalCategory;
import com.guillermomolina.protos.runtime.ProtosExternalChildValue.TerminalOutcome;
import com.guillermomolina.protos.runtime.ProtosExternalExecutionCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosExternalExecutionCapabilityValue.LaunchRequest;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosIoLifecycle;
import com.guillermomolina.protos.runtime.ProtosIoOperation;
import com.guillermomolina.protos.runtime.ProtosMapValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.runtime.callables.ProtosNativeClosureBody;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import org.graalvm.polyglot.io.ProcessHandler.Redirect;
import com.oracle.truffle.api.io.TruffleProcessBuilder;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;

/**
 * Private Core bridge of the source-backed external execution protocols (EXTERNAL_PROCESS.md).
 *
 * <p>Arity, portable String/Array recognition and the argv snapshot live in Execution.protos;
 * the public selectors of children, endpoints and exit statuses live in their .protos sources.
 * This bridge only authenticates host values, freezes the request, performs host effects off the
 * Actor turn and publishes results back into the owning Actor domain. Byte transfer reuses
 * {@code ProtosByteIoFlow}; commitment and cancellation reuse {@link ProtosIoOperation}.
 */
public final class ProtosExternalExecutionHostFacility {
    public static final String BOOTSTRAP_SLOT = "_coreExecutionBridge";

    private ProtosExternalExecutionHostFacility() {}

    public static ProtosObjectValue createBridge() {
        ProtosObjectValue bridge = new ProtosObjectValue(ProtosObjectValue.rootObject());

        operation(bridge, "failed", 1, ProtosHttpAsyncFacility::failed);
        operation(bridge, "start", 5, ProtosExternalExecutionHostFacility::start);
        operation(bridge, "childStdin", 1, (a, s) -> endpoint(a, s, ProtosExternalChildValue.Stream.STDIN));
        operation(bridge, "childStdout", 1, (a, s) -> endpoint(a, s, ProtosExternalChildValue.Stream.STDOUT));
        operation(bridge, "childStderr", 1, (a, s) -> endpoint(a, s, ProtosExternalChildValue.Stream.STDERR));
        operation(bridge, "childWait", 1, ProtosExternalExecutionHostFacility::childWait);
        operation(bridge, "childTerminate", 1, ProtosExternalExecutionHostFacility::childTerminate);
        operation(bridge, "endpointRead", 2, ProtosExternalExecutionHostFacility::endpointRead);
        operation(bridge, "endpointWrite", 2, ProtosExternalExecutionHostFacility::endpointWrite);
        operation(bridge, "endpointClose", 1, ProtosExternalExecutionHostFacility::endpointClose);
        operation(bridge, "statusExited", 1, (a, s) -> status(a, s.get(0)).exited());
        operation(bridge, "statusExitCode", 1, (a, s) -> status(a, s.get(0)).exitCode());
        operation(bridge, "statusSignaled", 1, (a, s) -> status(a, s.get(0)).signaled());

        return bridge.freeze();
    }

    /**
     * Host launcher backed by the Truffle process builder of the authorized Context. The
     * caller must have established that the host permits process creation; it never widens
     * native permissions. No shell is involved: the executable and arguments are passed
     * directly. A simple executable name is resolved by the host process-creation search of this
     * runtime, which is fixed for its lifetime. The grant's default working directory is the
     * Context's current working directory captured here, at provisioning, and applied explicitly
     * to every launch whose working directory is null, so a later change of the host's directory
     * never changes it. A relative working directory is denied by policy.
     *
     * <p>The directory is always set on the builder: with no directory, the Truffle process
     * builder resolves the current directory through the Context file system, which a
     * restricted Context denies, whereas a set directory is passed by path only.
     *
     * <p>Must be called while {@code env}'s Context is entered or initializing.
     */
    @TruffleBoundary
    public static ProtosExternalExecutionCapabilityValue.Launcher hostLauncher(
            TruffleLanguage.Env env) {
        return hostLauncher(env, null);
    }

    /**
     * As {@link #hostLauncher(TruffleLanguage.Env)}; when the Context's file system denies the
     * current-directory query (the standalone source-readability file system admits only source
     * paths and is never widened for this), the default directory is the absolute
     * {@code hostDirectory} the hosting runtime captured once before any guest code ran. Without
     * either, provisioning fails.
     */
    @TruffleBoundary
    public static ProtosExternalExecutionCapabilityValue.Launcher hostLauncher(
            TruffleLanguage.Env env, String hostDirectory) {
        return hostLauncher(env, hostDirectory, false);
    }

    /**
     * As {@link #hostLauncher(TruffleLanguage.Env, String)}. {@code nativeStatusProvider} is a
     * fact of the host configuration: it is {@code true} only when the host that built this
     * Context installed the native POSIX process handler itself, so the decorated
     * {@link Process#exitValue()} carries the normalized POSIX return code (a non-negative exit
     * status, or a negated terminating signal). Otherwise the provider is unverifiable and
     * every terminal outcome stays UNKNOWN.
     */
    @TruffleBoundary
    public static ProtosExternalExecutionCapabilityValue.Launcher hostLauncher(
            TruffleLanguage.Env env, String hostDirectory, boolean nativeStatusProvider) {
        Objects.requireNonNull(env, "env");
        TruffleFile captured;
        try {
            captured = env.getCurrentWorkingDirectory();
        } catch (SecurityException denied) {
            if (hostDirectory == null || !new File(hostDirectory).isAbsolute()) {
                throw denied;
            }
            captured = env.getPublicTruffleFile(hostDirectory);
        }
        TruffleFile defaultDirectory = captured;
        ProtosNioProcessPipeBackend transport =
                new ProtosNioProcessPipeBackend("protos-external-execution-pipe");
        return new ProtosExternalExecutionCapabilityValue.Launcher() {
            @Override
            public ProtosExternalChildValue.NativeChild launch(
                    LaunchRequest request,
                    ProtosExternalExecutionCapabilityValue.NativeCustody custody)
                    throws IOException {
                return launchWithTruffle(
                        env, defaultDirectory, transport, nativeStatusProvider, request, custody);
            }

            @Override
            public void retireTransport() {
                transport.close();
            }
        };
    }

    /**
     * Separate steps: (1) builder preparation, (2) native start, (3) the minimal host child,
     * which only holds the native process, (4) immediate transient custody of it, (5) its
     * supervision, then (6) its bounded pipe transport. A failure before (2) means no child
     * exists; (3) cannot fail. After (4) the caller owns the child: a failure of (5) or (6)
     * throws with the child already in custody, never losing its reference.
     */
    @TruffleBoundary
    private static ProtosExternalChildValue.NativeChild launchWithTruffle(
            TruffleLanguage.Env env,
            TruffleFile defaultDirectory,
            ProtosNioProcessPipeBackend transport,
            boolean nativeStatusProvider,
            LaunchRequest request,
            ProtosExternalExecutionCapabilityValue.NativeCustody custody)
            throws IOException {
        if (!request.defaultWorkingDirectory()
                && !new File(request.workingDirectory()).isAbsolute()) {
            throw new IOException("working directory is not admitted by the grant policy");
        }
        List<String> command = new ArrayList<>(request.arguments().size() + 1);
        command.add(request.executable());
        command.addAll(request.arguments());
        TruffleProcessBuilder builder = env.newProcessBuilder(command.toArray(String[]::new));
        builder.redirectInput(Redirect.PIPE);
        builder.redirectOutput(Redirect.PIPE);
        builder.redirectError(Redirect.PIPE);
        builder.directory(
                request.defaultWorkingDirectory()
                        ? defaultDirectory
                        : env.getPublicTruffleFile(request.workingDirectory()));
        builder.clearEnvironment(true);
        builder.environment(request.environment());
        // A start failure is reported only after the provider has reclaimed any created child.
        HostChild child = new HostChild(builder.start(), nativeStatusProvider);
        custody.admit(child);
        child.startSupervision();
        child.attachTransport(transport);
        return child;
    }

    /**
     * Native child backed by the Context provider's decorated java.lang.Process. The existing
     * {@link Process#onExit()} observation completes after the provider's confirmed reap. When
     * the host installed the native POSIX provider for this Context, the delegated
     * {@link Process#exitValue()} is that provider's decoded POSIX status and is classified;
     * from any other provider the decoration carries no verifiable category, so the outcome
     * remains UNKNOWN and every guest wait fails with IOError instead of fabricating an
     * ExitStatus. An exceptional
     * completion is a supervision failure, never evidence of reap. No additional supervisor
     * is installed here.
     */
    private static final class HostChild implements ProtosExternalChildValue.NativeChild {
        private final Process process;
        private final boolean nativeStatusProvider;
        private final CompletableFuture<TerminalOutcome> termination = new CompletableFuture<>();
        private final CompletableFuture<Void> transportRetired = new CompletableFuture<>();
        private final CompletableFuture<Boolean>[] nativeReleases = newReleases();
        private volatile ProtosNioProcessPipeBackend.ChildPipes pipes;

        /** Holds the started process only; starts nothing that could fail. */
        HostChild(Process process, boolean nativeStatusProvider) {
            this.process = process;
            this.nativeStatusProvider = nativeStatusProvider;
        }

        @SuppressWarnings("unchecked")
        private static CompletableFuture<Boolean>[] newReleases() {
            CompletableFuture<Boolean>[] releases = new CompletableFuture[3];
            for (int index = 0; index < releases.length; index++) {
                releases[index] = new CompletableFuture<>();
            }
            return releases;
        }

        /**
         * Installs the single supervisor, after the child is in custody. A failure to install it
         * is recorded as a supervision failure, never as a reap, and the obligation stays.
         */
        void startSupervision() {
            try {
                process.onExit()
                        .whenComplete(
                                (exited, failure) -> {
                                    if (failure == null) {
                                        completeReaped();
                                    } else {
                                        termination.completeExceptionally(failure);
                                    }
                                });
            } catch (RuntimeException | Error failure) {
                termination.completeExceptionally(failure);
                throw failure;
            }
        }

        /**
         * Runs on the supervision thread after the confirmed reap; never runs guest code. The
         * native provider's exit value is a normalized POSIX return code: non-negative for a
         * normal exit, negated WTERMSIG for a signal termination. The reap is already
         * confirmed here, so a status the provider cannot decode leaves the outcome UNKNOWN
         * rather than fabricating a category or reopening the reap.
         */
        private void completeReaped() {
            if (!nativeStatusProvider) {
                termination.complete(TerminalOutcome.unknown());
                return;
            }
            int code;
            try {
                code = process.exitValue();
            } catch (RuntimeException undecodable) {
                termination.complete(TerminalOutcome.unknown());
                return;
            }
            termination.complete(
                    code >= 0 ? TerminalOutcome.exited(code) : TerminalOutcome.signaled());
        }

        /**
         * Connects the three streams. The transport record, and with it the retirement
         * obligation, is retained before any pump can start. Returns only once every lane is
         * registered with the poller and its pump started; a registration or start failure is
         * thrown here, before any ChildProcess exists, so it never surfaces first at a read or
         * write. After a partial failure the record keeps every started pump counted until it
         * exits, and owns the release of the streams.
         */
        void attachTransport(ProtosNioProcessPipeBackend transport) throws IOException {
            ProtosNioProcessPipeBackend.ChildPipes prepared =
                    transport.prepare(
                            process.getOutputStream(), process.getInputStream(), process.getErrorStream());
            pipes = prepared;
            prepared.retired().whenComplete((ignored, failure) -> transportRetired.complete(null));
            transport.attach(prepared);
        }

        @Override
        public ProtosByteIoFlow.Backend lane(ProtosExternalChildValue.Stream stream) {
            ProtosNioProcessPipeBackend.ChildPipes attached = pipes;
            if (attached == null || !attached.attachedForRuntime()) {
                throw new IllegalStateException("child transport is not attached");
            }
            return switch (stream) {
                case STDIN -> attached.stdinLane();
                case STDOUT -> attached.stdoutLane();
                case STDERR -> attached.stderrLane();
            };
        }

        @Override
        public CompletionStage<Boolean> release(
                ProtosExternalChildValue.Stream stream, boolean discard) {
            ProtosNioProcessPipeBackend.ChildPipes attached = pipes;
            if (attached == null) {
                // No transport record exists: the native stream is closed directly, once; a
                // failure is reported as uncertain and never retried.
                CompletableFuture<Boolean> outcome = nativeReleases[stream.ordinal()];
                synchronized (outcome) {
                    if (!outcome.isDone()) {
                        java.io.Closeable handle =
                                switch (stream) {
                                    case STDIN -> process.getOutputStream();
                                    case STDOUT -> process.getInputStream();
                                    case STDERR -> process.getErrorStream();
                                };
                        boolean confirmed;
                        try {
                            handle.close();
                            confirmed = true;
                        } catch (IOException | RuntimeException uncertain) {
                            confirmed = false;
                        }
                        outcome.complete(confirmed);
                    }
                }
                return outcome;
            }
            return switch (stream) {
                case STDIN -> attached.releaseStdin(discard);
                case STDOUT -> attached.releaseStdout();
                case STDERR -> attached.releaseStderr();
            };
        }

        @Override
        public CompletionStage<TerminalOutcome> termination() {
            return termination;
        }

        /**
         * The real retirement stage of the transport record whenever one exists, including a
         * partially attached one. Only a child for which no record was ever prepared, so no Pipe
         * or pump exists, has nothing to retire.
         */
        @Override
        public CompletionStage<Void> transportRetired() {
            if (pipes == null) {
                return CompletableFuture.completedFuture(null);
            }
            return transportRetired;
        }

        @Override
        public void requestTermination() {
            process.destroy();
        }

        @Override
        public void requestForcedTermination() {
            process.destroyForcibly();
        }
    }

    private static void operation(
            ProtosObjectValue receiver, String name, int arity, ProtosNativeClosureBody body) {
        receiver.createLocalSlot(
                name,
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            if (activation.receiver() != receiver || supplied.size() != arity) {
                                throw new ProtosSignalException(
                                        ProtosCoreErrors.newError(activation));
                            }
                            return body.execute(activation, supplied);
                        }));
    }

    private static Object start(ProtosActivation activation, List<?> supplied) {
        ProtosExternalExecutionCapabilityValue grant = grantedOrNull(activation, supplied.get(0));
        if (grant == null) {
            return failedFuture(activation, ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT);
        }
        LaunchRequest request =
                snapshot(
                        grant.environmentNameDomainForRuntime(),
                        supplied.get(1),
                        supplied.get(2),
                        supplied.get(3),
                        supplied.get(4));
        if (request == null) {
            return failedFuture(activation, ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT);
        }
        ProtosProcessRuntime process = grant.ownerForRuntime().orElseThrow();
        ProtosIoOperation operation = beginOperation(grant, activation);
        if (!operation.future().isPending()) {
            return operation.future();
        }
        // Rejections before submission create no child. A terminating Process is a lifecycle
        // refusal; a revoked grant is a policy denial (IOError); a full bounded executor is
        // capacity exhaustion.
        if (process.lifecycleState() != ProtosProcessRuntime.LifecycleState.RUNNING) {
            operation.fail(occurrence(activation, ProtosCoreErrors.StandardError.I_O_LIFECYCLE_ERROR));
            return operation.future();
        }
        if (grant.retiredForRuntime()) {
            operation.fail(ioError(activation));
            return operation.future();
        }
        // Finite child admission precedes commitment and bounds the Process's physical resources.
        ProtosExternalExecutionCapabilityValue.Admission admission = grant.reserveChildForRuntime();
        if (admission == null) {
            operation.fail(
                    grant.retiredForRuntime()
                            ? ioError(activation)
                            : occurrence(
                                    activation,
                                    ProtosCoreErrors.StandardError.I_O_CAPACITY_EXHAUSTED));
            return operation.future();
        }
        if (!grant.executeForRuntime(
                () -> launch(grant, process, request, operation, activation, admission))) {
            admission.release();
            operation.fail(
                    grant.retiredForRuntime()
                            ? ioError(activation)
                            : occurrence(
                                    activation,
                                    ProtosCoreErrors.StandardError.I_O_CAPACITY_EXHAUSTED));
        }
        return operation.future();
    }

    /**
     * Runs outside the Actor turn. Commitment wins over cancellation once the attempt is admitted;
     * afterwards the Future either resolves to the registered child or fails with IOError only
     * once any child that was created is reaped and its resources are released. A JVM Error is never classified as an ordinary I/O failure: it
     * settles the Future so no guest waits forever and is rethrown.
     */
    private static void launch(
            ProtosExternalExecutionCapabilityValue grant,
            ProtosProcessRuntime process,
            LaunchRequest request,
            ProtosIoOperation operation,
            ProtosActivation activation,
            ProtosExternalExecutionCapabilityValue.Admission admission) {
        // Admission is atomic with revocation and precedes commitment: no native launch begins
        // after definitive revocation, and the host close sees the attempt before reclamation.
        // A refused admission leaves the operation uncommitted, so a cancellation that already
        // won keeps its outcome.
        if (!grant.beginLaunchForRuntime()) {
            admission.release();
            publish(activation, operation, () -> operation.fail(ioError(activation)));
            return;
        }
        try {
            // Commitment frontier: a cancellation that won before it guarantees zero children.
            if (!operation.commit()) {
                admission.release();
                return;
            }
            launchAdmitted(grant, process, request, operation, activation, admission);
        } finally {
            grant.endLaunchForRuntime();
        }
    }

    private static void launchAdmitted(
            ProtosExternalExecutionCapabilityValue grant,
            ProtosProcessRuntime process,
            LaunchRequest request,
            ProtosIoOperation operation,
            ProtosActivation activation,
            ProtosExternalExecutionCapabilityValue.Admission admission) {
        // Transient custody: the launcher hands the native child over the instant it exists.
        ProtosExternalChildValue.NativeChild[] admitted = new ProtosExternalChildValue.NativeChild[1];
        ProtosExternalExecutionCapabilityValue.NativeCustody custody =
                nativeChild -> {
                    process.admitNativeChildForRuntime(nativeChild);
                    admitted[0] = nativeChild;
                };
        ProtosExternalChildValue.NativeChild nativeChild;
        try {
            ProtosExternalExecutionCapabilityValue.Launcher launcher = grant.launcherForRuntime();
            if (launcher == null) {
                throw new IOException("execution grant has no bound launcher");
            }
            nativeChild = launcher.launch(request, custody);
            if (admitted[0] == null) {
                // A launcher that skipped the hand-off still answered a child: take custody now.
                custody.admit(nativeChild);
            }
        } catch (IOException | RuntimeException | Error failure) {
            failAfterLaunchAttempt(process, operation, activation, admission, admitted[0], failure);
            return;
        }
        // A native child exists and is in transient custody: every failure keeps it there.
        ProtosExternalChildValue child;
        try {
            // The capability belongs to the originating Actor domain from its construction on.
            child = grant.newChildForRuntime(
                    process, nativeChild, admission, activation.executionDomain());
        } catch (RuntimeException | Error failure) {
            failAfterLaunchAttempt(process, operation, activation, admission, nativeChild, failure);
            return;
        }
        boolean publishable;
        try {
            // Process custody precedes guest publication and takes over transient custody
            // atomically; a terminated Process keeps host custody.
            publishable = process.registerExternalChildForRuntime(child);
        } catch (RuntimeException | Error failure) {
            failAfterLaunchAttempt(process, operation, activation, admission, nativeChild, failure);
            return;
        }
        if (!publishable) {
            child.enterHostOnlyCustodyForRuntime();
            operation.abandonCommittedAcquisitionForRuntime();
            return;
        }
        publish(activation, operation, () -> operation.resolve(child));
    }

    /**
     * A committed launch attempt failed (EXTERNAL_PROCESS.md §4). No ChildProcess and no
     * fictitious cancellation is ever published.
     *
     * <p>Without an admitted native child none exists: the slot is returned and the Future fails
     * with IOError at once.
     *
     * <p>With one, the child stays in transient Process custody, forced termination is requested
     * through the backend and its handles are released. The failure is not classified yet: the
     * Future stays pending while that cleanup runs. The returned slot and the IOError are
     * retained discharge obligations of the child, run exactly once by whichever reclamation
     * attempt verifies the reap, the confirmed release of every handle and the retirement of the
     * transport; the IOError is then delivered into the originating Actor through its ordinary
     * targeted completion. An attempt that ends without that evidence (a failed supervisor, an
     * uncertain release) keeps the child, the slot and the pending Future in custody, publishes
     * nothing, never blocks the Actor and repeats no reap or uncertain close.
     */
    private static void failAfterLaunchAttempt(
            ProtosProcessRuntime process,
            ProtosIoOperation operation,
            ProtosActivation activation,
            ProtosExternalExecutionCapabilityValue.Admission admission,
            ProtosExternalChildValue.NativeChild nativeChild,
            Throwable failure) {
        if (nativeChild == null) {
            admission.release();
            publish(activation, operation, () -> operation.fail(ioError(activation)));
        } else {
            process.abandonNativeChildForRuntime(
                    nativeChild,
                    () -> {
                        admission.release();
                        publish(activation, operation, () -> operation.fail(ioError(activation)));
                    });
        }
        if (failure instanceof Error fatal) {
            throw fatal;
        }
    }

    private static Object endpoint(
            ProtosActivation activation, List<?> supplied, ProtosExternalChildValue.Stream stream) {
        ProtosExternalChildValue child = childOrNull(activation, supplied.get(0));
        if (child == null) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
        }
        return child.endpointForRuntime(activation, stream);
    }

    private static Object childWait(ProtosActivation activation, List<?> supplied) {
        ProtosExternalChildValue child = childOrNull(activation, supplied.get(0));
        if (child == null) {
            return failedFuture(activation, ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT);
        }
        if (!child.guestOperableForRuntime()) {
            return failedFuture(activation, ProtosCoreErrors.StandardError.I_O_LIFECYCLE_ERROR);
        }
        ProtosIoOperation operation = beginOperation(child, activation);
        if (!operation.future().isPending()) {
            return operation.future();
        }
        // Each wait is an independent observation of the single retained supervisor outcome;
        // cancelling it terminalizes only this operation.
        child.terminationForRuntime()
                .whenComplete(
                        (observed, failure) -> {
                            TerminalOutcome outcome =
                                    observed != null && failure == null
                                            ? observed
                                            : TerminalOutcome.unknown();
                            publish(activation, operation, () -> publishOutcome(activation, operation, child, outcome));
                        });
        return operation.future();
    }

    /** Executed only in the owning Actor domain. */
    private static boolean publishOutcome(
            ProtosActivation activation,
            ProtosIoOperation operation,
            ProtosExternalChildValue child,
            TerminalOutcome outcome) {
        if (operation.terminal() || !operation.commit()) {
            return false;
        }
        if (outcome.category() == TerminalCategory.UNKNOWN) {
            return operation.fail(ioError(activation));
        }
        return operation.resolve(child.statusForRuntime(outcome));
    }

    private static Object childTerminate(ProtosActivation activation, List<?> supplied) {
        ProtosExternalChildValue child = childOrNull(activation, supplied.get(0));
        if (child == null) {
            return failedFuture(activation, ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT);
        }
        if (!child.guestOperableForRuntime()) {
            return failedFuture(activation, ProtosCoreErrors.StandardError.I_O_LIFECYCLE_ERROR);
        }
        ProtosIoOperation operation = beginOperation(child, activation);
        if (!operation.future().isPending()) {
            return operation.future();
        }
        Runnable submit =
                () -> {
                    if (!operation.commit()) {
                        return;
                    }
                    boolean submitted = child.requestTerminationForRuntime();
                    publish(
                            activation,
                            operation,
                            () ->
                                    submitted
                                            ? operation.resolve(ProtosNullValue.INSTANCE)
                                            : operation.fail(ioError(activation)));
                };
        try {
            child.ioExecutorForRuntime().execute(submit);
        } catch (java.util.concurrent.RejectedExecutionException exhausted) {
            // Failure to submit a termination request is an IOError (EXTERNAL_PROCESS.md §7).
            operation.fail(ioError(activation));
        }
        return operation.future();
    }

    private static Object endpointRead(ProtosActivation activation, List<?> supplied) {
        ProtosExternalChildValue.Endpoint endpoint = endpointOrNull(activation, supplied.get(0));
        if (endpoint == null || !endpoint.readableForRuntime()) {
            return failedFuture(activation, ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT);
        }
        if (!endpoint.childForRuntime().guestOperableForRuntime()) {
            return failedFuture(activation, ProtosCoreErrors.StandardError.I_O_LIFECYCLE_ERROR);
        }
        return endpoint.readForRuntime(activation, supplied.get(1));
    }

    private static Object endpointWrite(ProtosActivation activation, List<?> supplied) {
        ProtosExternalChildValue.Endpoint endpoint = endpointOrNull(activation, supplied.get(0));
        if (endpoint == null || endpoint.readableForRuntime()) {
            return failedFuture(activation, ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT);
        }
        if (!endpoint.childForRuntime().guestOperableForRuntime()) {
            return failedFuture(activation, ProtosCoreErrors.StandardError.I_O_LIFECYCLE_ERROR);
        }
        return endpoint.writeForRuntime(activation, supplied.get(1));
    }

    private static Object endpointClose(ProtosActivation activation, List<?> supplied) {
        ProtosExternalChildValue.Endpoint endpoint = endpointOrNull(activation, supplied.get(0));
        if (endpoint == null) {
            return failedFuture(activation, ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT);
        }
        if (!endpoint.childForRuntime().guestOperableForRuntime()) {
            return failedFuture(activation, ProtosCoreErrors.StandardError.I_O_LIFECYCLE_ERROR);
        }
        return endpoint.closeForRuntime(activation);
    }

    private static ProtosExternalChildValue.Status status(ProtosActivation activation, Object receiver) {
        if (!(receiver instanceof ProtosExternalChildValue.Status status)) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
        }
        return status;
    }

    /**
     * Freezes and validates the request (EXTERNAL_PROCESS.md §§3.5-3.6). Answers null for every
     * ordinary semantic invalidity; nothing has been launched or acquired at that point.
     * Duplicate environment names are judged by the host's native name domain, the same one that
     * governs the Process environment snapshot; when the domain cannot decide, the request is
     * rejected without launching.
     */
    static LaunchRequest snapshot(
            ProtosEnvironmentValue.NativeNameDomain nameDomain,
            Object executable,
            Object arguments,
            Object environment,
            Object workingDirectory) {
        String program = nativeText(executable);
        if (program == null || program.isEmpty()) {
            return null;
        }
        boolean hasSeparator = program.indexOf('/') >= 0 || program.indexOf(File.separatorChar) >= 0;
        if (hasSeparator && !new File(program).isAbsolute()) {
            return null;
        }
        if (!(arguments instanceof ProtosArrayValue argv)) {
            return null;
        }
        List<String> argumentList = new ArrayList<>();
        for (Object argument : argv.indexedSnapshot()) {
            String text = nativeText(argument);
            if (text == null) {
                return null;
            }
            argumentList.add(text);
        }
        if (!(environment instanceof ProtosMapValue map)) {
            return null;
        }
        Map<String, String> entries = new LinkedHashMap<>();
        for (Map.Entry<Object, Object> entry : map.associationSnapshot()) {
            String name = nativeText(entry.getKey());
            String value = nativeText(entry.getValue());
            if (name == null || value == null || name.isEmpty() || name.indexOf('=') >= 0) {
                return null;
            }
            for (String admitted : entries.keySet()) {
                boolean same;
                try {
                    same = nameDomain.sameCapturedName(admitted, name);
                } catch (RuntimeException undecidable) {
                    return null;
                }
                if (same) {
                    return null;
                }
            }
            entries.put(name, value);
        }
        String directory = null;
        if (workingDirectory != ProtosNullValue.INSTANCE) {
            directory = nativeText(workingDirectory);
            if (directory == null || directory.isEmpty()) {
                return null;
            }
        }
        return new LaunchRequest(program, argumentList, entries, directory);
    }

    /** A String losslessly representable natively: no NUL and no unpaired surrogate. */
    private static String nativeText(Object value) {
        if (!(value instanceof ProtosStringValue string)) {
            return null;
        }
        String text = string.value();
        for (int index = 0; index < text.length(); index++) {
            char unit = text.charAt(index);
            if (unit == '\0') {
                return null;
            }
            if (Character.isHighSurrogate(unit)) {
                if (index + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(index + 1))) {
                    return null;
                }
                index++;
            } else if (Character.isLowSurrogate(unit)) {
                return null;
            }
        }
        return text;
    }

    private static ProtosExternalExecutionCapabilityValue grantedOrNull(
            ProtosActivation activation, Object receiver) {
        ProtosPrelude prelude = activation.prelude().orElseThrow();
        // Authenticates the actual host value, not its shape or delegation ancestry: the initial
        // grant, or a view materialized by an authorized Actor transfer for this very Actor.
        if (!(receiver instanceof ProtosExternalExecutionCapabilityValue grant)
                || !grant.usableFromForRuntime(activation.executionDomain())
                || !prelude.retainsExecutionProtocolsForRuntime()
                || grant.representedDelegationParent(prelude)
                        != prelude.executionProtocolsForRuntime().execution()
                || grant.ownerForRuntime().isEmpty()
                || grant.ownerForRuntime().get() != hostingProcess(activation)) {
            return null;
        }
        return grant;
    }

    /**
     * Authenticates the actual host child: it must belong to the hosting Process and to the very
     * Actor domain it was published into. A grant delegated to another Actor confers no control
     * over it, and a reference obtained outside the Actor protocol is rejected the same way.
     */
    private static ProtosExternalChildValue childOrNull(ProtosActivation activation, Object receiver) {
        if (!(receiver instanceof ProtosExternalChildValue child)
                || child.ownerForRuntime() != hostingProcess(activation)
                || !child.ownedByForRuntime(activation)) {
            return null;
        }
        return child;
    }

    /** As {@link #childOrNull}: an endpoint is operable only from its child's owning domain. */
    private static ProtosExternalChildValue.Endpoint endpointOrNull(
            ProtosActivation activation, Object receiver) {
        if (!(receiver instanceof ProtosExternalChildValue.Endpoint endpoint)
                || endpoint.childForRuntime().ownerForRuntime() != hostingProcess(activation)
                || !endpoint.childForRuntime().ownedByForRuntime(activation)) {
            return null;
        }
        return endpoint;
    }

    private static ProtosProcessRuntime hostingProcess(ProtosActivation activation) {
        return ProtosIoLifecycle.hostingProcessForRuntime(activation.executionDomain()).orElse(null);
    }

    private static ProtosIoOperation beginOperation(Object receiver, ProtosActivation activation) {
        ProtosIoLifecycle lifecycle =
                new ProtosIoLifecycle(
                        receiver,
                        activation.prelude().orElseThrow().futurePrototype(),
                        activation.executionDomain(),
                        completion -> completion.succeeded());
        return lifecycle.beginOperation(activation);
    }

    /**
     * Publishes a committed outcome inside the originating Actor domain. A terminating Actor
     * discards it without guest work; any child stays under Process custody.
     */
    private static void publish(
            ProtosActivation activation, ProtosIoOperation operation, BooleanSupplier action) {
        try {
            activation
                    .executionDomain()
                    .enqueueTargetedFutureCompletionForRuntime(
                            operation.future(),
                            action,
                            operation::abandonCommittedAcquisitionForRuntime);
        } catch (IllegalStateException terminatingActor) {
            operation.abandonCommittedAcquisitionForRuntime();
        }
    }

    private static ProtosObjectValue ioError(ProtosActivation activation) {
        return occurrence(activation, ProtosCoreErrors.StandardError.I_O_ERROR);
    }

    private static ProtosObjectValue occurrence(
            ProtosActivation activation, ProtosCoreErrors.StandardError error) {
        return ProtosCoreErrors.newOccurrence(activation, error);
    }

    private static ProtosFutureValue failedFuture(
            ProtosActivation activation, ProtosCoreErrors.StandardError error) {
        ProtosFutureValue future =
                new ProtosFutureValue(
                        activation.prelude().orElseThrow().futurePrototype(),
                        activation.executionDomain());
        future.fail(ProtosCoreErrors.newOccurrence(activation, error));
        return future;
    }
}
