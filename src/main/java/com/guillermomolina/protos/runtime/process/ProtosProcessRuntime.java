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
package com.guillermomolina.protos.runtime;

import com.guillermomolina.protos.runtime.io.ProtosFilesystemValue;
import com.guillermomolina.protos.runtime.process.ProtosProcessExecutionHost;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Internal local Protos Process failure-domain and RootActor failure-authority substrate.
 *
 * <p>A Process is semantic runtime capacity, not an operating-system process. This class performs
 * no OS termination. It owns the current local Process incarnation, its unique RootActor, hosted
 * Actor incarnations, stable bootstrap state, and the runtime authority behind represented Process
 * capability proxies. Optional default Filesystem and Network grants remain separate authority
 * and are exposed only to RootActor bootstrap machinery. An optional Resolver grant is likewise
 * separate authority, but it is owned by exactly this incarnation and retired at its termination
 * cutover.
 */
public final class ProtosProcessRuntime {
    public enum LifecycleState {
        RUNNING,
        TERMINATING,
        TERMINATED
    }

    public enum ArgumentsSnapshotState {
        UNESTABLISHED,
        AVAILABLE,
        UNREPRESENTABLE
    }

    public enum EnvironmentSnapshotState {
        UNESTABLISHED,
        AVAILABLE,
        INVALID
    }

    /**
     * Stable bootstrap state of one standard-stream Encoding association.
     *
     * <p>INVALID is distinct from UNAVAILABLE: it records a host configuration whose stream
     * availability and Encoding association disagree.
     */
    public enum StandardStreamEncodingState {
        UNESTABLISHED,
        AVAILABLE,
        UNAVAILABLE,
        INVALID
    }

    private final ProtosActor rootActor;
    private final ProtosFilesystemValue rootFilesystem;
    private final ProtosNetworkCapabilityValue rootNetwork;
    private final ProtosMapValue rootResources;
    private final ProtosResolverCapabilityValue rootResolver;
    private final ProtosEntropyCapabilityValue rootEntropy;
    private final ProtosClockCapabilityValue rootClock;
    private final ProtosExternalExecutionCapabilityValue rootExecution;
    /**
     * External OS children under this Process's exclusive custody: live children, exited children
     * whose endpoint handles are still owned or whose pumps are still alive, children registered
     * after the termination cutover and children in host-only cleanup. A child leaves only when it
     * was reaped, every native endpoint handle was released and its transport retired; resolution
     * of its termination stage alone never removes it.
     */
    private final Set<ProtosExternalChildValue> externalChildren = new LinkedHashSet<>();
    /**
     * Transient host custody of native children that already exist but have no custody wrapper
     * in {@link #externalChildren} yet, or never will because building it failed. A native child
     * enters immediately after native creation and leaves either atomically with its wrapper's
     * registration or after its verified reap and confirmed handle releases. The value is the
     * abandonment outcome once abandonment started, or null while the wrapper is being built.
     * Keys are host objects, never PIDs.
     */
    private final java.util.Map<ProtosExternalChildValue.NativeChild,
                    java.util.concurrent.CompletableFuture<Boolean>>
            nativeChildren = new java.util.IdentityHashMap<>();
    /**
     * Discharge obligations of native children in {@link #nativeChildren}, such as returning an
     * admission slot, retained across abandonment attempts and run once, at the verified
     * discharge.
     */
    private final java.util.Map<ProtosExternalChildValue.NativeChild, Runnable>
            nativeDischargers = new java.util.IdentityHashMap<>();
    private final Set<ProtosActor> liveActors = new LinkedHashSet<>();
    private final Set<ProtosActorGroupRuntime> ownedGroups = new LinkedHashSet<>();
    private LifecycleState lifecycle = LifecycleState.RUNNING;
    private Object rootFailureCause;
    private ArgumentsSnapshotState argumentsSnapshotState =
            ArgumentsSnapshotState.UNESTABLISHED;
    private List<ProtosStringValue> argumentsSnapshot;
    private EnvironmentSnapshotState environmentSnapshotState =
            EnvironmentSnapshotState.UNESTABLISHED;
    private ProtosEnvironmentValue environmentSnapshot;
    private boolean standardStreamsEstablished;
    private ProtosProcessStandardStreamBinding stdinBinding;
    private ProtosProcessStandardStreamBinding stdoutBinding;
    private ProtosProcessStandardStreamBinding stderrBinding;
    private StandardStreamEncodingState stdinEncodingState =
            StandardStreamEncodingState.UNESTABLISHED;
    private StandardStreamEncodingState stdoutEncodingState =
            StandardStreamEncodingState.UNESTABLISHED;
    private StandardStreamEncodingState stderrEncodingState =
            StandardStreamEncodingState.UNESTABLISHED;
    private ProtosEncodingValue stdinEncoding;
    private ProtosEncodingValue stdoutEncoding;
    private ProtosEncodingValue stderrEncoding;
    private ProtosProcessExecutionHost executionHost;

    /**
     * Creates one Process incarnation together with its unique RootActor and no optional
     * Filesystem or Network grant.
     */
    public ProtosProcessRuntime(ProtosObjectValue actorRefPrototype) {
        this(actorRefPrototype, null, null);
    }

    /** Creates one Process incarnation with an optional default Filesystem grant only. */
    public ProtosProcessRuntime(
            ProtosObjectValue actorRefPrototype,
            ProtosFilesystemValue rootFilesystem) {
        this(actorRefPrototype, rootFilesystem, null);
    }

    /**
     * Creates one Process incarnation with bootstrap-stable optional default Filesystem and
     * Network grants.
     *
     * <p>Both capabilities remain separate from Process authority. A null grant means the
     * RootActor initial module receives no corresponding local slot; each choice is fixed for this
     * Process incarnation and is never recoverable through the public Process capability.
     */
    public ProtosProcessRuntime(
            ProtosObjectValue actorRefPrototype,
            ProtosFilesystemValue rootFilesystem,
            ProtosNetworkCapabilityValue rootNetwork) {
        this(actorRefPrototype, rootFilesystem, rootNetwork, null);
    }

    /**
     * Creates one Process incarnation with bootstrap-stable optional default Filesystem, Network
     * and D107 RootActor resource-bundle grants.
     *
     * <p>The resources grant is host-provisioned authority, not Process authority. Null means the
     * RootActor initial module receives no {@code resources} local.
     */
    public ProtosProcessRuntime(
            ProtosObjectValue actorRefPrototype,
            ProtosFilesystemValue rootFilesystem,
            ProtosNetworkCapabilityValue rootNetwork,
            ProtosMapValue rootResources) {
        this(actorRefPrototype, rootFilesystem, rootNetwork, rootResources, null);
    }

    /**
     * Creates one Process incarnation that additionally carries an optional explicit D223-A1
     * Resolver grant. The grant is independent of every other one: no Network, Filesystem,
     * Process or socket authority implies it, and null means the RootActor initial module
     * receives no {@code resolver} local.
     *
     * <p>The grant is bound to this exact incarnation here, before any initial module can run;
     * a Resolver already granted to another Process, or already retired, is rejected. This
     * incarnation's termination cutover retires it (§14.9).
     *
     * @throws IllegalArgumentException when {@code rootResolver} is already owned or retired
     */
    public ProtosProcessRuntime(
            ProtosObjectValue actorRefPrototype,
            ProtosFilesystemValue rootFilesystem,
            ProtosNetworkCapabilityValue rootNetwork,
            ProtosMapValue rootResources,
            ProtosResolverCapabilityValue rootResolver) {
        this(
                actorRefPrototype,
                rootFilesystem,
                rootNetwork,
                rootResources,
                rootResolver,
                null);
    }

    /**
     * D243-C: independent RootActor Entropy authority.
     *
     * <p>Null is denial, not a default generator. Each grant binds to
     * precisely one Process incarnation and is never reusable.
     */
    public ProtosProcessRuntime(
            ProtosObjectValue actorRefPrototype,
            ProtosFilesystemValue rootFilesystem,
            ProtosNetworkCapabilityValue rootNetwork,
            ProtosMapValue rootResources,
            ProtosResolverCapabilityValue rootResolver,
            ProtosEntropyCapabilityValue rootEntropy) {
        this(actorRefPrototype, rootFilesystem, rootNetwork, rootResources, rootResolver, rootEntropy, null);
    }

    public ProtosProcessRuntime(
            ProtosObjectValue actorRefPrototype,
            ProtosFilesystemValue rootFilesystem,
            ProtosNetworkCapabilityValue rootNetwork,
            ProtosMapValue rootResources,
            ProtosResolverCapabilityValue rootResolver,
            ProtosEntropyCapabilityValue rootEntropy,
            ProtosClockCapabilityValue rootClock) {
        this(actorRefPrototype, rootFilesystem, rootNetwork, rootResources, rootResolver,
                rootEntropy, rootClock, null);
    }

    /**
     * Optional external execution grant (EXTERNAL_PROCESS.md §1). Null is denial; a grant binds
     * to precisely this incarnation and is retired at its termination cutover.
     */
    public ProtosProcessRuntime(
            ProtosObjectValue actorRefPrototype,
            ProtosFilesystemValue rootFilesystem,
            ProtosNetworkCapabilityValue rootNetwork,
            ProtosMapValue rootResources,
            ProtosResolverCapabilityValue rootResolver,
            ProtosEntropyCapabilityValue rootEntropy,
            ProtosClockCapabilityValue rootClock,
            ProtosExternalExecutionCapabilityValue rootExecution) {
        try {
            Objects.requireNonNull(actorRefPrototype, "actorRefPrototype");
            if (rootExecution != null) {
                rootExecution.bindOwnerForRuntime(this);
            }
            if (rootResolver != null) {
                rootResolver.bindOwnerForRuntime(this);
            }
            if (rootEntropy != null) {
                rootEntropy.bindOwnerForRuntime(this);
            }
            if (rootClock != null) {
                rootClock.bindOwnerForRuntime(this);
            }
        } catch (RuntimeException | Error failure) {
            if (rootExecution != null && (rootExecution.ownerForRuntime().isEmpty()
                    || rootExecution.ownerForRuntime().orElse(null) == this)) {
                rootExecution.retireForRuntime();
            }
            if (rootClock != null && (rootClock.ownerForRuntime().isEmpty()
                    || rootClock.ownerForRuntime().orElse(null) == this)) {
                rootClock.retireForRuntime();
            }
            if (rootEntropy != null && (rootEntropy.ownerForRuntime().isEmpty()
                    || rootEntropy.ownerForRuntime().orElse(null) == this)) {
                try {
                    rootEntropy.retireForRuntime();
                } catch (RuntimeException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            if (rootResolver != null && (rootResolver.ownerForRuntime().isEmpty()
                    || rootResolver.ownerForRuntime().orElse(null) == this)) {
                try {
                    rootResolver.retireForRuntime();
                } catch (RuntimeException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }

        this.rootResolver = rootResolver;
        this.rootEntropy = rootEntropy;
        this.rootClock = rootClock;
        this.rootExecution = rootExecution;
        this.rootFilesystem = rootFilesystem;
        this.rootNetwork = rootNetwork;
        this.rootResources = rootResources;

        try {
            rootActor = new ProtosActor(actorRefPrototype, this, true);
        } catch (RuntimeException | Error failure) {
            retireExecutionForRuntime();
            retireClockForRuntime();
            try {
                retireEntropyForRuntime();
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            try {
                retireResolverForRuntime();
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
        liveActors.add(rootActor);
    }

    public synchronized LifecycleState lifecycleState() {
        return lifecycle;
    }

    /**
     * Waits until this semantic Process has actually reached TERMINATED.
     *
     * <p>A termination request is not equivalent to terminality: hosted Actors may still be
     * unwinding. This host-only wait preserves interruption status while retaining lifecycle
     * custody until the Process terminal transition has fired.
     */
    public void awaitTerminationForRuntime() {
        boolean interrupted = false;

        synchronized (this) {
            while (lifecycle != LifecycleState.TERMINATED) {
                try {
                    wait();
                } catch (InterruptedException interruption) {
                    interrupted = true;
                }
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    public ProtosActor rootActorForRuntime() {
        return rootActor;
    }

    /**
     * Binds this Process incarnation to one host execution placement exactly once.
     *
     * <p>The binding is implementation placement, not semantic Process identity. A4B3 still owns
     * migration of every production driver; an unbound Process therefore retains the staged direct
     * host path until that cutover is complete.
     */
    public synchronized void bindExecutionHostForRuntime(ProtosProcessExecutionHost host) {
        Objects.requireNonNull(host, "host");
        if (lifecycle != LifecycleState.RUNNING) {
            throw new IllegalStateException(
                    "Process execution host cannot be bound after termination begins");
        }
        if (executionHost != null) {
            throw new IllegalStateException("Process execution host is already bound");
        }
        executionHost = host;
    }

    public synchronized Optional<ProtosProcessExecutionHost> executionHostForRuntime() {
        return Optional.ofNullable(executionHost);
    }

    /** Executes host placement machinery when this Process is bound; staged direct path otherwise. */
    public <T> T callInExecutionHostForRuntime(Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        ProtosProcessExecutionHost host;
        synchronized (this) {
            host = executionHost;
        }
        return host == null ? action.get() : host.callForRuntime(action);
    }

    public void runInExecutionHostForRuntime(Runnable action) {
        Objects.requireNonNull(action, "action");
        callInExecutionHostForRuntime(
                () -> {
                    action.run();
                    return null;
                });
    }

    /** Optional default Filesystem authority granted only to the RootActor initial module. */
    public Optional<ProtosFilesystemValue> rootFilesystemForRuntime() {
        return Optional.ofNullable(rootFilesystem);
    }

    /** Optional default Network authority granted only to the RootActor initial module. */
    public Optional<ProtosNetworkCapabilityValue> rootNetworkForRuntime() {
        return Optional.ofNullable(rootNetwork);
    }

    /** Optional explicit D223-A1 Resolver authority granted only to the RootActor initial module. */
    public Optional<ProtosResolverCapabilityValue> rootResolverForRuntime() {
        return Optional.ofNullable(rootResolver);
    }

    /**
     * Retires this incarnation's Resolver grant, if any; idempotent and activation-free. The
     * termination cutover calls it, and bootstrap machinery calls it when this incarnation fails
     * before its initial module runs. No other grant is affected.
     */
    public void retireResolverForRuntime() {
        if (rootResolver != null) {
            rootResolver.retireForRuntime();
        }
    }

    /** Optional D253-C1 Clock grant, absent from the public Process capability. */
    public Optional<ProtosClockCapabilityValue> rootClockForRuntime() {
        return Optional.ofNullable(rootClock);
    }

    /** Host-only, idempotent revocation independent of all other grants. */
    public void retireClockForRuntime() {
        if (rootClock != null) rootClock.retireForRuntime();
    }

    /** Optional external execution grant, absent from the public Process capability. */
    public Optional<ProtosExternalExecutionCapabilityValue> rootExecutionForRuntime() {
        return Optional.ofNullable(rootExecution);
    }

    /** Host-only, idempotent revocation of new launches; existing children keep custody. */
    public void retireExecutionForRuntime() {
        if (rootExecution != null) {
            rootExecution.retireForRuntime();
        }
    }

    /** Bounded per-child wait, before and after forced termination, during host close. */
    public static final long HOST_CLOSE_CHILD_GRACE_MILLIS = 2000;

    /**
     * Takes transient custody of a native child the instant it exists, before any wrapper or
     * transport is built for it. Admitted regardless of the lifecycle: custody never depends on
     * whether the result can still be published.
     */
    public void admitNativeChildForRuntime(ProtosExternalChildValue.NativeChild nativeChild) {
        Objects.requireNonNull(nativeChild, "nativeChild");
        synchronized (this) {
            nativeChildren.putIfAbsent(nativeChild, null);
        }
    }

    /**
     * Registers a freshly launched child under this Process's custody before guest publication.
     * Its native child leaves transient custody in the same atomic step, so no interval exists in
     * which neither registry owns it. The child is tracked until it was reaped and its endpoint
     * handles were released, even if it already exited; the result reports whether guest
     * publication is still admissible. When it is false the caller must move the child to
     * host-only custody.
     */
    public boolean registerExternalChildForRuntime(ProtosExternalChildValue child) {
        Objects.requireNonNull(child, "child");
        synchronized (this) {
            externalChildren.add(child);
            nativeChildren.remove(child.nativeChildForRuntime());
            nativeDischargers.remove(child.nativeChildForRuntime());
            // A child discharged before registration cannot reach the release callback again.
            if (child.custodyReleasedForRuntime()) {
                externalChildren.remove(child);
            }
            return lifecycle == LifecycleState.RUNNING;
        }
    }

    /**
     * Reclaims a native child in transient custody whose wrapper could not be built or
     * registered: requests forced termination and releases every handle, abandoning undelivered
     * input. The child leaves custody, and its retained discharge obligations run once, only
     * after the supervisor verified the reap, every handle release was confirmed and its
     * transport retired; a forced request alone establishes nothing. Never waits.
     *
     * <p>An attempt still in progress is reused. An attempt that completed without discharge
     * leaves the child in custody and may be repeated: termination is requested again, while the
     * single supervisor outcome and each memoized handle release are only observed again, so no
     * reap is repeated and no uncertain handle is closed twice. A failed supervisor or an
     * uncertain release therefore keeps the obligation on every attempt.
     *
     * <p>The answer is the outcome of the attempt now governing the child, the same stage stored
     * in {@link #nativeChildren}: it completes with true once the child left custody (after the
     * retained obligations ran) and with false when this attempt ended without discharge, in
     * which case the child and its retained obligations stay in custody for a later attempt. A
     * child no longer in transient custody answers true at once.
     */
    @com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
    public java.util.concurrent.CompletableFuture<Boolean> abandonNativeChildForRuntime(
            ProtosExternalChildValue.NativeChild nativeChild, Runnable discharged) {
        java.util.concurrent.CompletableFuture<Boolean> outcome =
                new java.util.concurrent.CompletableFuture<>();
        synchronized (this) {
            if (!nativeChildren.containsKey(nativeChild)) {
                return java.util.concurrent.CompletableFuture.completedFuture(true);
            }
            if (discharged != null) {
                nativeDischargers.putIfAbsent(nativeChild, discharged);
            }
            java.util.concurrent.CompletableFuture<Boolean> previous = nativeChildren.get(nativeChild);
            if (previous != null && !previous.isDone()) {
                return previous;
            }
            nativeChildren.put(nativeChild, outcome);
        }
        try {
            nativeChild.requestForcedTermination();
        } catch (java.io.IOException | RuntimeException refused) {
            // Custody continues; a later host close path observes the unreaped child.
        }
        java.util.concurrent.CompletableFuture<Boolean> released =
                java.util.concurrent.CompletableFuture.completedFuture(true);
        for (ProtosExternalChildValue.Stream stream : ProtosExternalChildValue.Stream.values()) {
            java.util.concurrent.CompletableFuture<Boolean> one;
            try {
                one = nativeChild.release(stream, true).toCompletableFuture();
            } catch (RuntimeException uncertain) {
                one = java.util.concurrent.CompletableFuture.completedFuture(false);
            }
            released = released.thenCombine(one, (left, right) -> left && Boolean.TRUE.equals(right));
        }
        java.util.concurrent.CompletableFuture<Boolean> reaped =
                nativeChild.termination().toCompletableFuture().handle(
                        (verified, failure) -> verified != null && failure == null);
        java.util.concurrent.CompletableFuture<Boolean> quiescent =
                nativeChild.transportRetired().toCompletableFuture().handle(
                        (ignored, failure) -> failure == null);
        reaped.thenCombine(released, Boolean::logicalAnd)
                .thenCombine(quiescent, Boolean::logicalAnd)
                .whenComplete(
                        (done, failure) -> {
                            boolean discharge = failure == null && Boolean.TRUE.equals(done);
                            Runnable obligation = null;
                            if (discharge) {
                                synchronized (this) {
                                    nativeChildren.remove(nativeChild);
                                    obligation = nativeDischargers.remove(nativeChild);
                                }
                            }
                            if (obligation != null) {
                                obligation.run();
                            }
                            outcome.complete(discharge);
                        });
        return outcome;
    }

    /**
     * Host close: after the termination cutover, waits a bounded time for committed launches of
     * the execution grant. If an attempt is still in flight its child may not be registered yet,
     * so no snapshot can be definitive: nothing is reclaimed as complete, the workers that may be
     * needed to finish it stay alive, and the result is false so the host does not consider the
     * close successful; a later call retries. Otherwise reclaims every child still tracked (live,
     * exited with owned endpoints, in host-only cleanup, or in transient native custody), waiting
     * a bounded time per child. Empty registries are not enough: the grant must also hold no
     * launch in flight and no reserved child admission, since an admission stays reserved until
     * its child's transport retired. The grant's workers are retired only once all of that holds.
     * Only the embedding host's closing thread calls this, never with this Process's monitor held.
     * Idempotent; a child whose reclamation was not established stays tracked.
     *
     * @return true when every native child is known to be reaped, every handle released, every
     *     transport retired and every admission of the grant returned
     */
    public boolean reclaimExternalChildrenForHostClose(long graceMillis) {
        retireExecutionForRuntime();
        if (rootExecution != null && !rootExecution.awaitLaunchesForHostClose(graceMillis)) {
            return false;
        }
        List<ProtosExternalChildValue> children;
        List<ProtosExternalChildValue.NativeChild> natives;
        synchronized (this) {
            children = List.copyOf(externalChildren);
            natives = List.copyOf(nativeChildren.keySet());
        }
        boolean reclaimed = true;
        for (ProtosExternalChildValue child : children) {
            reclaimed &= child.reclaimForHostCloseRuntime(graceMillis);
        }
        for (ProtosExternalChildValue.NativeChild nativeChild : natives) {
            reclaimed &= awaitDischarge(abandonNativeChildForRuntime(nativeChild, null), graceMillis);
        }
        // Slots are returned just after their children leave the registry, on the discharging
        // thread, so they are awaited for a bounded time rather than sampled once.
        reclaimed = reclaimed
                && (rootExecution == null
                        || rootExecution.awaitHostCustodyDischargeForHostClose(graceMillis))
                && !externalCustodyOutstandingForRuntime();
        if (reclaimed && rootExecution != null) {
            rootExecution.retireWorkersForHostClose();
        }
        return reclaimed;
    }

    private static boolean awaitDischarge(
            java.util.concurrent.CompletableFuture<Boolean> outcome, long graceMillis) {
        try {
            return Boolean.TRUE.equals(
                    outcome.get(graceMillis, java.util.concurrent.TimeUnit.MILLISECONDS));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        } catch (java.util.concurrent.ExecutionException
                | java.util.concurrent.TimeoutException notDischarged) {
            return false;
        }
    }

    /**
     * True while a native child or wrapped child is still owed supervision, release or transport
     * retirement, or while the grant still holds a launch in flight or a reserved admission.
     */
    public boolean externalCustodyOutstandingForRuntime() {
        synchronized (this) {
            if (!externalChildren.isEmpty() || !nativeChildren.isEmpty()) {
                return true;
            }
        }
        // The grant's own monitor is never taken while holding this Process's monitor.
        return rootExecution != null && !rootExecution.hostCustodyDischargedForRuntime();
    }

    /** Snapshot of native children in transient custody. */
    public List<ProtosExternalChildValue.NativeChild> nativeChildrenForRuntime() {
        synchronized (this) {
            return List.copyOf(nativeChildren.keySet());
        }
    }

    /** Snapshot of the children still under this Process's custody. */
    public List<ProtosExternalChildValue> externalChildrenForRuntime() {
        synchronized (this) {
            return List.copyOf(externalChildren);
        }
    }

    /**
     * Called once per child after it was reaped, all its endpoint handles were released and its
     * transport retired; never earlier, so the registry keeps a child whose pump is still alive.
     */
    void externalChildReleasedForRuntime(ProtosExternalChildValue child) {
        synchronized (this) {
            externalChildren.remove(child);
        }
    }

    /** Optional D243-C Entropy grant, absent from the public Process capability. */
    public Optional<ProtosEntropyCapabilityValue> rootEntropyForRuntime() {
        return Optional.ofNullable(rootEntropy);
    }

    /**
     * Host-only, idempotent retirement of this Process's Entropy backend.
     * No other Process or independent grant is affected.
     */
    public void retireEntropyForRuntime() {
        if (rootEntropy != null) {
            rootEntropy.retireForRuntime();
        }
    }

    /** Optional frozen D107 resource capability bundle granted only to RootActor bootstrap. */
    public Optional<ProtosMapValue> rootResourcesForRuntime() {
        return Optional.ofNullable(rootResources);
    }

    /**
     * Captures the complete application-argument bootstrap snapshot exactly once.
     *
     * <p>The host supplies application arguments only; executable/argv[0] identity is outside this
     * sequence. The complete detached host list is validated before any portable snapshot becomes
     * available. An unrepresentable element records one stable UNREPRESENTABLE bootstrap outcome;
     * later host mutation or a second establishment attempt cannot change that result.
     *
     * <p>The retained snapshot is only stable content: an immutable list of Strings with no Protos
     * container identity. Each successful accessor call materializes an ordinary frozen Array for
     * the calling Actor from this content.
     */
    public synchronized ArgumentsSnapshotState establishArgumentsForRuntime(
            List<String> hostArguments) {
        Objects.requireNonNull(hostArguments, "hostArguments");
        if (lifecycle != LifecycleState.RUNNING) {
            throw new IllegalStateException(
                    "Process arguments cannot be established after termination begins");
        }
        if (argumentsSnapshotState != ArgumentsSnapshotState.UNESTABLISHED) {
            throw new IllegalStateException(
                    "Process arguments bootstrap snapshot is already established");
        }

        // Form one detached host snapshot and validate the complete sequence before any Protos
        // String is retained.
        List<String> captured;
        try {
            captured = List.copyOf(hostArguments);
        } catch (NullPointerException nullElement) {
            argumentsSnapshot = null;
            argumentsSnapshotState = ArgumentsSnapshotState.UNREPRESENTABLE;
            return argumentsSnapshotState;
        }
        if (!captured.stream().allMatch(ProtosProcessRuntime::isUnicodeScalarString)) {
            argumentsSnapshot = null;
            argumentsSnapshotState = ArgumentsSnapshotState.UNREPRESENTABLE;
            return argumentsSnapshotState;
        }
        argumentsSnapshot =
                captured.stream()
                        .<ProtosStringValue>map(ProtosStringValue::new)
                        .toList();
        argumentsSnapshotState = ArgumentsSnapshotState.AVAILABLE;
        return argumentsSnapshotState;
    }

    public synchronized ArgumentsSnapshotState argumentsSnapshotStateForRuntime() {
        return argumentsSnapshotState;
    }

    /**
     * Returns the stable ordered argument Strings when bootstrap conversion succeeded.
     *
     * <p>UNESTABLISHED is a launcher/integration error. UNREPRESENTABLE intentionally returns empty
     * so the later Process accessor can create a fresh ordinary Error occurrence per failed call.
     * The returned list is immutable content, not a Protos value; callers wrap it in an ordinary
     * frozen Array.
     */
    public synchronized Optional<List<ProtosStringValue>> argumentsSnapshotForRuntime() {
        if (argumentsSnapshotState == ArgumentsSnapshotState.UNESTABLISHED) {
            throw new IllegalStateException(
                    "Process arguments bootstrap snapshot is not established");
        }
        return Optional.ofNullable(argumentsSnapshot);
    }

    private static boolean isUnicodeScalarString(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    return false;
                }
                index++;
            } else if (Character.isLowSurrogate(current)) {
                return false;
            }
        }
        return true;
    }

    public synchronized EnvironmentSnapshotState establishEnvironmentForRuntime(
            ProtosObjectValue environmentPrototype,
            ProtosEnvironmentValue.NativeNameDomain nameDomain,
            List<ProtosEnvironmentValue.NativeEntry> hostEntries) {
        Objects.requireNonNull(environmentPrototype, "environmentPrototype");
        Objects.requireNonNull(nameDomain, "nameDomain");
        Objects.requireNonNull(hostEntries, "hostEntries");
        if (lifecycle != LifecycleState.RUNNING) {
            throw new IllegalStateException(
                    "Process environment cannot be established after termination begins");
        }
        if (environmentSnapshotState != EnvironmentSnapshotState.UNESTABLISHED) {
            throw new IllegalStateException(
                    "Process Environment bootstrap snapshot is already established");
        }

        try {
            environmentSnapshot =
                    ProtosEnvironmentValue.captureForRuntime(
                            environmentPrototype, nameDomain, hostEntries);
            environmentSnapshotState = EnvironmentSnapshotState.AVAILABLE;
        } catch (IllegalArgumentException | NullPointerException invalidMapping) {
            environmentSnapshot = null;
            environmentSnapshotState = EnvironmentSnapshotState.INVALID;
        }
        return environmentSnapshotState;
    }

    public synchronized EnvironmentSnapshotState environmentSnapshotStateForRuntime() {
        return environmentSnapshotState;
    }

    public synchronized Optional<ProtosEnvironmentValue> environmentSnapshotForRuntime() {
        if (environmentSnapshotState == EnvironmentSnapshotState.UNESTABLISHED) {
            throw new IllegalStateException(
                    "Process Environment bootstrap snapshot is not established");
        }
        return Optional.ofNullable(environmentSnapshot);
    }

    /**
     * Establishes the three independently optional standard byte-stream bindings exactly once.
     *
     * <p>Null backend means that binding is unavailable. Every available binding is already usable
     * synchronously after this call; this method performs no deferred/waiting acquisition.
     */
    public synchronized void establishStandardStreamsForRuntime(
            ProtosObjectValue readablePrototype,
            ProtosObjectValue writablePrototype,
            ProtosObjectValue bytesPrototype,
            ProtosProcessStandardStreamBinding.ReadableBackend stdinBackend,
            ProtosProcessStandardStreamBinding.WritableBackend stdoutBackend,
            ProtosProcessStandardStreamBinding.WritableBackend stderrBackend) {
        Objects.requireNonNull(readablePrototype, "readablePrototype");
        Objects.requireNonNull(writablePrototype, "writablePrototype");
        Objects.requireNonNull(bytesPrototype, "bytesPrototype");
        if (lifecycle != LifecycleState.RUNNING) {
            throw new IllegalStateException(
                    "Process standard streams cannot be established after termination begins");
        }
        if (standardStreamsEstablished) {
            throw new IllegalStateException(
                    "Process standard streams are already established");
        }

        stdinBinding =
                stdinBackend == null
                        ? null
                        : ProtosProcessStandardStreamBinding.readableForRuntime(
                                this,
                                readablePrototype,
                                bytesPrototype,
                                stdinBackend);
        stdoutBinding =
                stdoutBackend == null
                        ? null
                        : ProtosProcessStandardStreamBinding.writableForRuntime(
                                this,
                                writablePrototype,
                                bytesPrototype,
                                stdoutBackend);
        stderrBinding =
                stderrBackend == null
                        ? null
                        : ProtosProcessStandardStreamBinding.writableForRuntime(
                                this,
                                writablePrototype,
                                bytesPrototype,
                                stderrBackend);
        standardStreamsEstablished = true;
    }

    public synchronized Optional<ProtosProcessStandardStreamValue> stdinForRuntime() {
        requireStandardStreamsEstablished();
        return stdinBinding == null
                ? Optional.empty()
                : Optional.of(stdinBinding.newViewForRuntime());
    }

    public synchronized Optional<ProtosProcessStandardStreamValue> stdoutForRuntime() {
        requireStandardStreamsEstablished();
        return stdoutBinding == null
                ? Optional.empty()
                : Optional.of(stdoutBinding.newViewForRuntime());
    }

    public synchronized Optional<ProtosProcessStandardStreamValue> stderrForRuntime() {
        requireStandardStreamsEstablished();
        return stderrBinding == null
                ? Optional.empty()
                : Optional.of(stderrBinding.newViewForRuntime());
    }

    /**
     * Establishes all three host-selected standard-stream Encoding associations exactly once.
     *
     * <p>An Encoding must exist exactly when its corresponding byte stream exists. Any mismatch is
     * stable INVALID bootstrap configuration; it is never repaired by a later host lookup,
     * inferred default, alias lookup, or codec discovery.
     */
    public synchronized void establishStandardStreamEncodingsForRuntime(
            ProtosEncodingValue stdinEncoding,
            ProtosEncodingValue stdoutEncoding,
            ProtosEncodingValue stderrEncoding) {
        if (lifecycle != LifecycleState.RUNNING) {
            throw new IllegalStateException(
                    "Process standard-stream Encodings cannot be established after termination begins");
        }
        requireStandardStreamsEstablished();
        if (stdinEncodingState != StandardStreamEncodingState.UNESTABLISHED
                || stdoutEncodingState != StandardStreamEncodingState.UNESTABLISHED
                || stderrEncodingState != StandardStreamEncodingState.UNESTABLISHED) {
            throw new IllegalStateException(
                    "Process standard-stream Encoding bootstrap state is already established");
        }

        this.stdinEncodingState = encodingState(stdinBinding != null, stdinEncoding);
        this.stdoutEncodingState = encodingState(stdoutBinding != null, stdoutEncoding);
        this.stderrEncodingState = encodingState(stderrBinding != null, stderrEncoding);

        this.stdinEncoding =
                this.stdinEncodingState == StandardStreamEncodingState.AVAILABLE
                        ? stdinEncoding
                        : null;
        this.stdoutEncoding =
                this.stdoutEncodingState == StandardStreamEncodingState.AVAILABLE
                        ? stdoutEncoding
                        : null;
        this.stderrEncoding =
                this.stderrEncodingState == StandardStreamEncodingState.AVAILABLE
                        ? stderrEncoding
                        : null;
    }

    public synchronized StandardStreamEncodingState stdinEncodingStateForRuntime() {
        return stdinEncodingState;
    }

    public synchronized StandardStreamEncodingState stdoutEncodingStateForRuntime() {
        return stdoutEncodingState;
    }

    public synchronized StandardStreamEncodingState stderrEncodingStateForRuntime() {
        return stderrEncodingState;
    }

    public synchronized Optional<ProtosEncodingValue> stdinEncodingForRuntime() {
        requireEncodingAssociationsEstablished();
        return Optional.ofNullable(stdinEncoding);
    }

    public synchronized Optional<ProtosEncodingValue> stdoutEncodingForRuntime() {
        requireEncodingAssociationsEstablished();
        return Optional.ofNullable(stdoutEncoding);
    }

    public synchronized Optional<ProtosEncodingValue> stderrEncodingForRuntime() {
        requireEncodingAssociationsEstablished();
        return Optional.ofNullable(stderrEncoding);
    }

    private static StandardStreamEncodingState encodingState(
            boolean streamAvailable, ProtosEncodingValue encoding) {
        if (streamAvailable && encoding != null) {
            return StandardStreamEncodingState.AVAILABLE;
        }
        if (!streamAvailable && encoding == null) {
            return StandardStreamEncodingState.UNAVAILABLE;
        }
        return StandardStreamEncodingState.INVALID;
    }

    private void requireEncodingAssociationsEstablished() {
        if (stdinEncodingState == StandardStreamEncodingState.UNESTABLISHED
                || stdoutEncodingState == StandardStreamEncodingState.UNESTABLISHED
                || stderrEncodingState == StandardStreamEncodingState.UNESTABLISHED) {
            throw new IllegalStateException(
                    "Process standard-stream Encoding bootstrap state is not established");
        }
    }

    private void requireStandardStreamsEstablished() {
        if (!standardStreamsEstablished) {
            throw new IllegalStateException(
                    "Process standard streams bootstrap state is not established");
        }
    }

    /**
     * Provisions one runtime-backed Process capability proxy for ordinary Actor delegation.
     *
     * <p>The wrapper itself is Actor-local state. Its only authority is the already-existing
     * logical Process represented by this runtime; provisioning does not create a Process and does
     * not manufacture filesystem, Node, Cluster, subprocess, or arbitrary host authority.
     */
    public synchronized ProtosProcessCapabilityValue provisionCapabilityForRuntime(
            ProtosObjectValue processPrototype) {
        Objects.requireNonNull(processPrototype, "processPrototype");
        if (lifecycle != LifecycleState.RUNNING) {
            throw new IllegalStateException(
                    "Process capability cannot be provisioned after termination begins");
        }
        return new ProtosProcessCapabilityValue(processPrototype, this);
    }

    /**
     * Creates one additional Actor hosted by this Process.
     *
     * <p>If Process termination already began while an existing non-preemptive Actor segment is
     * still running, the semantic Actor creation cutover may still occur. The new incarnation is
     * therefore registered and immediately receives the already-established Process termination
     * request instead of escaping into another failure domain.
     */
    public ProtosActor createHostedActorForRuntime(ProtosObjectValue actorRefPrototype) {
        Objects.requireNonNull(actorRefPrototype, "actorRefPrototype");
        ProtosActor actor;
        boolean terminateImmediately;
        synchronized (this) {
            if (lifecycle == LifecycleState.TERMINATED) {
                throw new IllegalStateException("terminated Process cannot host a new Actor");
            }
            actor = new ProtosActor(actorRefPrototype, this, false);
            liveActors.add(actor);
            terminateImmediately = lifecycle == LifecycleState.TERMINATING;
        }
        if (terminateImmediately) {
            actor.requestTerminationForRuntime();
        }
        return actor;
    }

    /**
     * Creates one D039 ActorGroup owned by this Process and returns its first GroupRef acquisition.
     *
     * <p>The caller validates the complete public argument vector before entering this boundary.
     * Construction keeps the exact ActorRef capabilities, collapses repeated Actor incarnations by
     * Group membership identity, and publishes no Group/control handle. If Process termination has
     * already begun concurrently with the caller's non-preemptive segment, the newly established
     * Group is immediately terminated before this method returns; its members are never stopped by
     * that Group termination.
     */
    public ProtosGroupRefValue createActorGroupForRuntime(
            ProtosObjectValue groupRefPrototype, List<ProtosActorRefValue> initialMembers) {
        Objects.requireNonNull(groupRefPrototype, "groupRefPrototype");
        Objects.requireNonNull(initialMembers, "initialMembers");
        if (initialMembers.isEmpty()) {
            throw new IllegalArgumentException("ActorGroup requires at least one initial member");
        }
        for (ProtosActorRefValue member : initialMembers) {
            Objects.requireNonNull(member, "initial ActorGroup member");
        }

        // The Group/GroupRef are not externally reachable until the complete cutover returns.
        ProtosActorGroupRuntime group = new ProtosActorGroupRuntime();
        for (ProtosActorRefValue member : initialMembers) {
            group.addInitialMemberReferenceForRuntime(member);
        }
        ProtosGroupRefValue reference =
                group.acquireReferenceForRuntime(groupRefPrototype, UUID.randomUUID());

        boolean terminateImmediately;
        synchronized (this) {
            ownedGroups.add(group);
            terminateImmediately = lifecycle != LifecycleState.RUNNING;
        }
        if (terminateImmediately) {
            group.markTerminatedForRuntime();
        }
        return reference;
    }

    /** Internal failure-authority entry used only for an unhandled fatal Actor failure. */
    void actorFatalFailureForRuntime(ProtosActor actor, Object failure) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(failure, "failure");
        requireOwned(actor);
        if (actor != rootActor) {
            // Core v0.1 non-root failure authority has no automatic replacement/escalation.
            actor.requestTerminationForRuntime();
            return;
        }
        beginTermination(failure);
    }

    /**
     * Host-only terminal classification. No guest capability or failure object
     * is exposed; the failure remains owned by the Process runtime.
     */
    public synchronized boolean failedFromRootForRuntime() {
        return rootFailureCause != null;
    }

    /**
     * Wait for the RootActor's terminal cutover without implying that an ordinary
     * graceful Actor stop is itself a Process termination request.
     *
     * Intended for hosts running an explicitly selected RootActor service.
     * Interruptions preserve the existing awaitTerminationForRuntime policy.
     */
    public void awaitRootActorTerminationForRuntime() {
        boolean interrupted = false;
        synchronized (this) {
            while (rootActor.lifecycleState() != ProtosActor.LifecycleState.TERMINATED
                    && lifecycle != LifecycleState.TERMINATED) {
                try {
                    wait();
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** Authoritative local Process termination request; this is not an OS-process operation. */
    public boolean requestTerminationForRuntime() {
        return beginTermination(null);
    }

    private boolean beginTermination(Object rootFailure) {
        Set<ProtosActor> terminate;
        Set<ProtosActorGroupRuntime> terminateGroups;
        synchronized (this) {
            if (rootFailure != null && rootFailureCause == null) {
                rootFailureCause = rootFailure;
            }
            if (lifecycle != LifecycleState.RUNNING) {
                return false;
            }
            lifecycle = LifecycleState.TERMINATING;
            terminate = Set.copyOf(liveActors);
            terminateGroups = Set.copyOf(ownedGroups);
        }

        // Withdraw the Resolver host authority at the cutover, outside the Process monitor: no
        // DNS work can start afterwards, and a running host lookup is never waited for. Pending
        // lookup Futures keep the ordinary Actor termination contract below.
        retireClockForRuntime();
        retireResolverForRuntime();
        retireEntropyForRuntime();

        // EXTERNAL_PROCESS.md §8: revoke launches, then move every child still in custody to
        // host-only cleanup custody. Tracking continues until each child is reaped; nothing here
        // waits for the OS or runs guest code.
        retireExecutionForRuntime();
        List<ProtosExternalChildValue> children;
        synchronized (this) {
            children = List.copyOf(externalChildren);
        }
        for (ProtosExternalChildValue child : children) {
            child.enterHostOnlyCustodyForRuntime();
        }

        // Stop Group routing at the Process cutover before member-Actor termination can trigger
        // routing eligibility callbacks. Group termination itself never stops a member Actor.
        for (ProtosActorGroupRuntime group : terminateGroups) {
            group.markTerminatedForRuntime();
        }

        // Never hold the Process monitor while entering Actor termination. Actor cleanup can invoke
        // producer callbacks and, when complete, calls back into actorTerminatedForRuntime().
        for (ProtosActor actor : terminate) {
            actor.requestTerminationForRuntime();
        }
        tryCompleteTermination();
        return true;
    }

    void actorTerminatedForRuntime(ProtosActor actor) {
        Objects.requireNonNull(actor, "actor");
        ProtosProcessExecutionHost actorHost;
        synchronized (this) {
            if (!liveActors.contains(actor)) {
                return;
            }
            actorHost = executionHost;
        }
        // Release host-owned Actor resources while the Actor still holds the Process open.
        if (actorHost != null) {
            actorHost.actorTerminatedForRuntime(actor);
        }
        ProtosProcessExecutionHost terminatedHost = null;
        synchronized (this) {
            if (!liveActors.remove(actor)) {
                return;
            }
            // A RootActor service may stop gracefully while its Process is
            // still RUNNING. Wake hosting code without changing Process policy.
            if (actor == rootActor) {
                notifyAll();
            }
            if (lifecycle == LifecycleState.TERMINATING && liveActors.isEmpty()) {
                lifecycle = LifecycleState.TERMINATED;
                notifyAll();
                terminatedHost = executionHost;
            }
        }
        notifyHostAfterTermination(terminatedHost);
    }

    private void tryCompleteTermination() {
        ProtosProcessExecutionHost terminatedHost = null;
        synchronized (this) {
            if (lifecycle == LifecycleState.TERMINATING && liveActors.isEmpty()) {
                lifecycle = LifecycleState.TERMINATED;
                notifyAll();
                terminatedHost = executionHost;
            }
        }
        notifyHostAfterTermination(terminatedHost);
    }

    private static void notifyHostAfterTermination(ProtosProcessExecutionHost host) {
        if (host != null) {
            host.processTerminatedForRuntime();
        }
    }

    private void requireOwned(ProtosActor actor) {
        if (actor.processForRuntime().orElse(null) != this) {
            throw new IllegalArgumentException("Actor belongs to another Process runtime");
        }
    }

    synchronized int liveActorCountForTesting() {
        return liveActors.size();
    }

    synchronized int ownedGroupCountForTesting() {
        return ownedGroups.size();
    }

    synchronized Optional<Object> rootFailureCauseForTesting() {
        return Optional.ofNullable(rootFailureCause);
    }
}
