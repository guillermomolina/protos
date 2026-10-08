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
import com.guillermomolina.protos.runtime.ProtosActorScheduler;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosEncodingValue;
import com.guillermomolina.protos.runtime.ProtosEnvironmentValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessExecutionHost;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * PLAT054 standard Polyglot embedding: the at-most-one Protos Process of one Polyglot Context.
 *
 * <p>It is created lazily by the first valid host evaluation ({@code PROCESS_IO.md}, Standard
 * Polyglot embedding bootstrap and authority) and reuses the ordinary standalone bootstrap: Core
 * from the resolved Core root (see {@link #resolveCoreRoot}), {@link ProtosStandaloneProcessBootstrap} for the Process and its
 * RootActor, and arguments, environment, and standard streams taken from the embedding {@link
 * TruffleLanguage.Env}. It grants no default Filesystem and no default Network. It is its own
 * {@link ProtosProcessExecutionHost}: the Polyglot Context the host already owns is the only
 * execution placement, so entering never opens another Context.
 *
 * <p>Host entries ({@link #evaluate}, binding reads, and host Closure execution) are RootActor
 * turns. An unsafe concurrent entry is rejected by {@link #enterRootActor} instead of waiting: no
 * universal call gate serializes host calls. An unhandled Error that ends an outermost entry fails
 * the RootActor and therefore terminates the Process ({@code ACTORS.md} §24C/§32); the Process is
 * never recreated in the same Context.
 */
final class ProtosEmbeddedProcess implements ProtosProcessExecutionHost {
    /** Distribution layout below a Protos home: the same convention as {@code PROTOS_HOME}. */
    private static final String HOME_CORE_DIRECTORY = "protos/lib/core";

    private static final String CORE_MARKER = "Context.protos";

    private final ProtosLanguageContext owner;
    private final ProtosProcessRuntime process;
    private final ProtosPrelude prelude;
    private final ProtosActor rootActor;
    private final Path coreRoot;
    private final AtomicReference<Thread> entryOwner = new AtomicReference<>();
    /* Written only by the thread that holds entryOwner. */
    private int entryDepth;
    /* The first entry consumes the bootstrap activation; later entries get fresh moduleContexts. */
    private ProtosActivation pendingInitialActivation;
    private volatile ProtosObjectValue selectedModuleContext;
    /*
     * Set once the Process stops admitting host entries (a fatal entry, Process termination, or
     * Context finalization). A plain volatile read keeps the per-call liveness check off the
     * Process monitor.
     */
    private volatile boolean stopped;
    private ExecutorService actorCarrierExecutor;
    private ProtosActorScheduler actorScheduler;

    private ProtosEmbeddedProcess(
            ProtosLanguageContext owner,
            ProtosStandaloneProcessBootstrap.Result bootstrap,
            ProtosPrelude prelude,
            Path coreRoot) {
        this.owner = owner;
        this.coreRoot = coreRoot;
        this.process = bootstrap.process();
        this.prelude = prelude;
        this.rootActor = process.rootActorForRuntime();
        this.pendingInitialActivation = bootstrap.activation();
    }

    /**
     * Bootstraps Core, the Process, and its RootActor inside the entered owning Context. Must be
     * called with the owning Context entered; a failure leaves no Process behind.
     */
    @TruffleBoundary
    static ProtosEmbeddedProcess bootstrap(ProtosLanguageContext owner) {
        Objects.requireNonNull(owner, "owner");
        TruffleLanguage.Env env = owner.env();
        Path coreRoot = resolveCoreRoot(owner);
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(coreRoot.getParent());
        ProtosPrelude prelude;
        try {
            prelude = new ProtosCoreBootstrap().bootstrap(coreRoot, resolver);
        } catch (IOException failure) {
            throw new ProtosEmbeddingException(
                    "Protos Core bootstrap failed from " + coreRoot + ": " + failure.getMessage());
        }
        ProtosEncodingValue utf8 = ProtosStandaloneHostedExecution.utf8(prelude);
        ProtosStandaloneProcessBootstrap.Result bootstrap =
                ProtosStandaloneProcessBootstrap.create(
                        prelude,
                        List.of(env.getApplicationArguments()),
                        ProtosStandaloneHostedExecution.HOST_ENVIRONMENT_NAME_DOMAIN,
                        environmentEntries(env),
                        ProtosStandaloneHostedExecution.readableBackend(env.in()),
                        ProtosStandaloneHostedExecution.writableBackend(env.out()),
                        ProtosStandaloneHostedExecution.writableBackend(env.err()),
                        utf8,
                        utf8,
                        utf8,
                        null,
                        null);
        ProtosEmbeddedProcess embedded = new ProtosEmbeddedProcess(owner, bootstrap, prelude, coreRoot);
        bootstrap.process().bindExecutionHostForRuntime(embedded);
        owner.bindModuleResolverForRuntime(resolver);
        return embedded;
    }

    /**
     * Core root precedence: the explicit {@code protos.CoreRoot} override, then the Core of the
     * Protos language home, then the Core packaged in the Protos JAR ({@link ProtosCoreResource}).
     * An override that does not name a Core directory fails explicitly and never falls back to
     * another origin; a language home without a Core directory is not a Core origin. Core sources
     * are read as implementation resources; doing so grants the guest no filesystem authority.
     */
    private static Path resolveCoreRoot(ProtosLanguageContext owner) {
        String override = owner.env().getOptions().get(ProtosLanguage.CORE_ROOT);
        if (!override.isEmpty()) {
            Path explicit = coreDirectoryOrNull(override);
            if (explicit == null) {
                throw new ProtosEmbeddingException(
                        "protos.CoreRoot does not name a Protos Core directory: " + override);
            }
            return explicit;
        }
        String home = owner.languageForRuntime().languageHomeForRuntime();
        if (home != null && !home.isEmpty()) {
            Path homeCore = coreDirectoryOrNull(home + "/" + HOME_CORE_DIRECTORY);
            if (homeCore != null) {
                return homeCore;
            }
        }
        return packagedCoreRoot(owner.env());
    }

    /**
     * The Core of the JAR's internal resource, unpacked by Truffle on first use. A JAR that lacks
     * the resource (for example a build that skipped packaging) fails explicitly.
     */
    private static Path packagedCoreRoot(TruffleLanguage.Env env) {
        String unpacked;
        try {
            unpacked =
                    env.getInternalResource(ProtosCoreResource.class)
                            .getAbsoluteFile()
                            .getPath();
        } catch (IOException | SecurityException failure) {
            throw new ProtosEmbeddingException(
                    "no Protos Core library is available: the packaged Core resource cannot be"
                            + " loaded ("
                            + failure.getMessage()
                            + "); set the protos.CoreRoot option or install Protos with a"
                            + " language home");
        }
        Path packaged =
                coreDirectoryOrNull(
                        unpacked + "/" + ProtosCoreResource.LIBRARY_DIRECTORY + "/core");
        if (packaged == null) {
            throw new ProtosEmbeddingException(
                    "the packaged Protos Core resource contains no Core directory");
        }
        return packaged;
    }

    private static Path coreDirectoryOrNull(String spelling) {
        try {
            Path candidate = Path.of(spelling).toAbsolutePath().normalize();
            return Files.isRegularFile(candidate.resolve(CORE_MARKER)) ? candidate : null;
        } catch (InvalidPathException invalid) {
            return null;
        }
    }

    private static List<ProtosEnvironmentValue.NativeEntry> environmentEntries(
            TruffleLanguage.Env env) {
        ArrayList<ProtosEnvironmentValue.NativeEntry> entries = new ArrayList<>();
        for (Map.Entry<String, String> entry : env.getEnvironment().entrySet()) {
            entries.add(new ProtosEnvironmentValue.NativeEntry(entry.getKey(), entry.getValue()));
        }
        return List.copyOf(entries);
    }

    /**
     * Executes one host evaluation as a direct RootActor entry ({@code MODULES.md}, Host-initiated
     * evaluations in an embedded RootActor). The first entry initializes the initial module with
     * its bootstrap slots; every later entry has no {@code ModuleKey} and gets a distinct fresh
     * standalone {@code moduleContext}. Only a normal completion changes the binding selection.
     */
    @TruffleBoundary
    Object evaluate(RootCallTarget bytecodeTarget) {
        Thread token = enterRootActor();
        try {
            ProtosActivation activation = nextEntryActivation();
            ProtosExecutionOutcome outcome =
                    ProtosRootTaskExecution.execute(bytecodeTarget, activation);
            switch (outcome.state()) {
                case COMPLETED -> {
                    selectedModuleContext = activation.context();
                    return outcome.value();
                }
                case FAILED -> {
                    ProtosSignalException failure = new ProtosSignalException(outcome.error());
                    outermostEntryFailed(token, failure);
                    throw failure;
                }
                default -> {
                    throw new ProtosEmbeddingException("Protos host evaluation was cancelled");
                }
            }
        } finally {
            exitRootActor(token);
        }
    }

    private ProtosActivation nextEntryActivation() {
        ProtosActivation initial = pendingInitialActivation;
        if (initial != null) {
            pendingInitialActivation = null;
            return initial;
        }
        return prelude.newModuleActivation(
                rootActor.moduleState(),
                null,
                prelude.newExecutionContext(),
                rootActor.executionDomain());
    }

    /**
     * Admits one host entry into the RootActor. Returns the entering thread for an outermost
     * entry, or {@code null} for a nested same-thread re-entry (whose escaping Error still has an
     * enclosing guest handler boundary). A concurrent entry from another thread is rejected, never
     * queued; a terminated Process admits nothing.
     */
    Thread enterRootActor() {
        Thread current = Thread.currentThread();
        if (entryOwner.get() == current) {
            requireLive();
            entryDepth++;
            return null;
        }
        if (!entryOwner.compareAndSet(null, current)) {
            throw concurrentEntryRejected();
        }
        entryDepth = 1;
        if (stopped) {
            entryDepth = 0;
            entryOwner.set(null);
            throw terminated();
        }
        return current;
    }

    /** Leaves an entry admitted by {@link #enterRootActor}; {@code token} is its result. */
    void exitRootActor(Thread token) {
        if (--entryDepth == 0) {
            entryOwner.set(null);
        }
    }

    /**
     * An Error escaped an entry. Only for an outermost entry ({@code token} non-null) did it escape
     * the outermost handler boundary of the RootActor turn; that fails the RootActor and thereby
     * terminates the Process. A nested entry's Error propagates to its enclosing guest code.
     */
    @TruffleBoundary
    void outermostEntryFailed(Thread token, ProtosSignalException failure) {
        if (token != null) {
            stopped = true;
            rootActor.failForRuntime(failure.error());
        }
    }

    boolean isLive() {
        return !stopped;
    }

    private void requireLive() {
        if (stopped) {
            throw terminated();
        }
    }

    @TruffleBoundary
    private static ProtosEmbeddingException concurrentEntryRejected() {
        return new ProtosEmbeddingException(
                "the Protos RootActor is executing another host entry; concurrent entry is"
                        + " rejected");
    }

    @TruffleBoundary
    private static ProtosEmbeddingException terminated() {
        return new ProtosEmbeddingException(
                "the Protos Process of this Context has terminated; it is not recreated");
    }

    /**
     * The own local slots of the selected {@code moduleContext}, or empty before the first normal
     * completion and after the Process stops running.
     */
    Optional<ProtosObjectValue> selectedModuleContext() {
        ProtosObjectValue selected = selectedModuleContext;
        return selected == null || !isLive() ? Optional.empty() : Optional.of(selected);
    }

    /**
     * Host read of one own local slot of {@code moduleContext}. A Closure value is extracted
     * receiver-bound exactly as an ordinary member read ({@code CALLABLES.md} §11): each read yields
     * a fresh extraction with the module context as receiver and {@code methodHome}, presented to
     * the host as an executable that retains exactly that extraction.
     */
    Object readSelectedMember(ProtosObjectValue moduleContext, Object value) {
        if (!(value instanceof ProtosClosureValue closure)) {
            return value;
        }
        ProtosClosureValue extracted = closure.bindMethod(moduleContext, moduleContext);
        // Task-free provenance: module state, Actor domain, and dynamic control of the RootActor.
        ProtosActivation caller =
                prelude.newModuleActivation(
                        rootActor.moduleState(), null, moduleContext, rootActor.executionDomain());
        return ProtosHostExecutableClosure.prepareForEmbeddingRuntime(extracted, caller, this);
    }

    @Override
    public <T> T callForRuntime(Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        if (ProtosLanguageContext.currentIfEnteredForRuntime() != owner) {
            /*
             * Every carrier of this placement is a polyglot thread of the owning Context (see
             * actorSchedulerForRuntime) or a host thread the framework already entered.
             */
            throw new IllegalStateException(
                    "embedded Protos Process execution requires its entered owning Context");
        }
        return action.get();
    }

    /**
     * Lazily creates the normal-Actor scheduler on polyglot threads of the owning Context, so Actor
     * carriers are entered for their lifetime and are joined before the Context is disposed. No
     * carrier thread exists until a program first schedules an Actor.
     */
    @Override
    public synchronized Optional<ProtosActorScheduler> actorSchedulerForRuntime() {
        if (actorScheduler == null) {
            int parallelism = Math.max(1, Runtime.getRuntime().availableProcessors());
            AtomicInteger sequence = new AtomicInteger();
            TruffleLanguage.Env env = owner.env();
            actorCarrierExecutor =
                    Executors.newFixedThreadPool(
                            parallelism,
                            command -> {
                                Thread carrier = env.newTruffleThreadBuilder(command).build();
                                carrier.setName(
                                        "protos-actor-carrier-" + sequence.incrementAndGet());
                                carrier.setDaemon(true);
                                return carrier;
                            });
            actorScheduler = new ProtosActorScheduler(actorCarrierExecutor, parallelism);
        }
        return Optional.of(actorScheduler);
    }

    @Override
    public void processTerminatedForRuntime() {
        // The Polyglot Context belongs to the host; Process termination does not close it.
        stopped = true;
    }

    /**
     * Context finalization: terminates the Process (revoking its Process-local authority) and
     * joins its Actor carriers while the Context can still be entered, so no guest callback runs
     * after the terminal boundary.
     */
    @TruffleBoundary
    void finalizeForContextClose() {
        stopped = true;
        process.requestTerminationForRuntime();
        process.awaitTerminationForRuntime();
        ExecutorService executor;
        synchronized (this) {
            executor = actorCarrierExecutor;
        }
        if (executor != null) {
            executor.close();
        }
    }

    Path coreRootForTesting() {
        return coreRoot;
    }

    ProtosProcessRuntime processForTesting() {
        return process;
    }

    synchronized boolean actorCarrierSubstrateInitializedForTesting() {
        return actorScheduler != null;
    }
}
