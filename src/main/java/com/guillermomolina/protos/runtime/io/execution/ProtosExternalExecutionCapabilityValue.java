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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Host-granted external OS execution authority (EXTERNAL_PROCESS.md §1, §9).
 *
 * <p>The Execution prototype contains no authority. The logical authorization is one shared
 * {@link Authority}: grant identity, its single owning Process incarnation, its revocation state,
 * the native environment name domain of its host, the launcher that embodies the host policy
 * selected at provisioning, the bounded host executor and the child admission. Each value of this
 * class is one view of it: the initial grant installed in the RootActor, or an Actor-local view
 * materialized only by an explicit Actor transfer (a message argument or {@code Actor.spawn}
 * initialization) for exactly one destination Actor domain. A view is never a mutable alias of
 * its source, carries no slot, and shares only the host authority, so delegation never amplifies
 * it and revocation of the authority revokes every view at once. No view has a guest
 * constructor. Retiring stops new launches only: children already created stay under their
 * Process custody.
 *
 * <p>Physical resources per Process are bounded independently of guest behavior: at most
 * {@link #MAX_CHILDREN} admitted children, reserved before launch commitment, each with three
 * pump threads and three bounded Pipes; at most {@link #MAX_WORKERS} host workers with at most
 * {@link #MAX_QUEUED} waiting requests, used only for launch, termination requests and other
 * short host work, never for waiting on a pipe; and one shared selector thread for all endpoint
 * transfers. A request beyond a bound is rejected synchronously. The executor is created lazily,
 * so a granted but unused execution authority costs no thread. Worker retirement is a separate
 * host close step that follows child reclamation.
 */
@ExportLibrary(InteropLibrary.class)
public final class ProtosExternalExecutionCapabilityValue implements ProtosRepresentedValue {
    public static final int MAX_WORKERS = 16;
    public static final int MAX_QUEUED = 64;
    public static final int MAX_CHILDREN = 16;

    /** One frozen, already validated launch request. */
    public record LaunchRequest(
            String executable,
            List<String> arguments,
            Map<String, String> environment,
            String workingDirectory) {
        public LaunchRequest {
            Objects.requireNonNull(executable, "executable");
            arguments = List.copyOf(arguments);
            environment = Map.copyOf(environment);
        }

        /** The grant's stable default directory is selected. */
        public boolean defaultWorkingDirectory() {
            return workingDirectory == null;
        }
    }

    /**
     * Receives a native child the instant it exists, before any wrapper, transport or guest value
     * is built for it, so no interval leaves a started child without an owner.
     */
    public interface NativeCustody {
        void admit(ProtosExternalChildValue.NativeChild child);
    }

    /**
     * Host launch backend of one grant. {@link #launch} runs outside every Actor turn. It hands
     * a started child to {@code custody} immediately after native creation; when it throws
     * without having done so, no child exists. A failure after the hand-off leaves the admitted
     * child to the caller's custody.
     */
    public interface Launcher {
        ProtosExternalChildValue.NativeChild launch(LaunchRequest request, NativeCustody custody)
                throws IOException;

        /** Retires host transport shared by this launcher's children; idempotent. */
        default void retireTransport() {}
    }

    /** One reserved child slot; released at most once. */
    public static final class Admission {
        private final Authority authority;
        private boolean released;

        private Admission(Authority authority) {
            this.authority = authority;
        }

        public void release() {
            synchronized (authority) {
                if (released) {
                    return;
                }
                released = true;
                authority.admittedChildren--;
                authority.notifyAll();
            }
        }
    }

    /**
     * Inert, private transfer descriptor of one delegation: it is never a Protos value, has no
     * prototype and is accepted only by the destination stage of an Actor transfer.
     */
    public static final class Delegation {
        private final Authority authority;
        private final ProtosProcessRuntime process;

        private Delegation(Authority authority, ProtosProcessRuntime process) {
            this.authority = authority;
            this.process = process;
        }
    }

    private static final AtomicInteger THREAD_SEQUENCE = new AtomicInteger();

    /** The logical authorization shared by every view; guarded by its own monitor. */
    private static final class Authority {
        private final ProtosPrelude.ExecutionProtocols protocols;
        private final ProtosEnvironmentValue.NativeNameDomain environmentNameDomain;
        private Launcher launcher;
        private ProtosProcessRuntime owner;
        /** Domain of the RootActor the initial view was installed in; bound exactly once. */
        private ProtosActorExecutionDomain rootDomain;
        private boolean retired;
        private int launchesInFlight;
        private int admittedChildren;
        private boolean workersRetired;
        private ThreadPoolExecutor ioExecutor;

        private Authority(
                ProtosPrelude.ExecutionProtocols protocols,
                ProtosEnvironmentValue.NativeNameDomain environmentNameDomain,
                Launcher launcher) {
            this.protocols = protocols;
            this.environmentNameDomain = environmentNameDomain;
            this.launcher = launcher;
        }
    }

    private final Authority authority;
    /**
     * Destination domain of a delegated view; null for the initial view, whose domain is the
     * authority's bound RootActor domain.
     */
    private final ProtosActorExecutionDomain delegatedDomain;

    /**
     * Creates a grant. A null {@code launcher} denotes a grant whose launcher is bound later, once,
     * to the Context that hosts its Process; until then every launch fails without a child.
     */
    public ProtosExternalExecutionCapabilityValue(
            ProtosPrelude prelude,
            ProtosEnvironmentValue.NativeNameDomain environmentNameDomain,
            Launcher launcher) {
        this(
                new Authority(
                        Objects.requireNonNull(
                                Objects.requireNonNull(prelude, "prelude")
                                        .executionProtocolsForRuntime(),
                                "Execution protocols"),
                        Objects.requireNonNull(environmentNameDomain, "environmentNameDomain"),
                        launcher),
                null);
    }

    private ProtosExternalExecutionCapabilityValue(
            Authority authority, ProtosActorExecutionDomain delegatedDomain) {
        this.authority = authority;
        this.delegatedDomain = delegatedDomain;
    }

    /** Host native environment name equivalence used to reject duplicate names. */
    public ProtosEnvironmentValue.NativeNameDomain environmentNameDomainForRuntime() {
        return authority.environmentNameDomain;
    }

    /** One-time binding of the launcher of the Context that hosts the owning Process. */
    public void bindLauncherForRuntime(Launcher bound) {
        Objects.requireNonNull(bound, "launcher");
        synchronized (authority) {
            if (authority.retired) {
                throw new IllegalStateException("Execution capability is retired");
            }
            if (authority.launcher != null) {
                throw new IllegalStateException("Execution capability launcher is already bound");
            }
            authority.launcher = bound;
        }
    }

    /** The bound launcher, or null while unbound. */
    public Launcher launcherForRuntime() {
        synchronized (authority) {
            return authority.launcher;
        }
    }

    /** One-time binding before RootActor initialization. */
    void bindOwnerForRuntime(ProtosProcessRuntime process) {
        Objects.requireNonNull(process, "process");
        synchronized (authority) {
            if (authority.retired) {
                throw new IllegalArgumentException("Execution capability is retired");
            }
            if (authority.owner != null) {
                throw new IllegalArgumentException(
                        "Execution capability is already granted to a Process");
            }
            authority.owner = process;
        }
    }

    public Optional<ProtosProcessRuntime> ownerForRuntime() {
        synchronized (authority) {
            return Optional.ofNullable(authority.owner);
        }
    }

    /**
     * Binds the initial view to the execution domain of the RootActor of its owning Process, at
     * the point where the grant is installed in that RootActor. The binding happens once; binding
     * again to the same domain is a no-op and binding to any other domain fails, so the owner of
     * the initial view never changes.
     *
     * @throws IllegalStateException when the grant is retired, delegated, owned by another
     *     Process, or already bound to another domain
     */
    public void bindRootActorForRuntime(
            ProtosProcessRuntime process, ProtosActorExecutionDomain domain) {
        Objects.requireNonNull(process, "process");
        Objects.requireNonNull(domain, "domain");
        if (delegatedDomain != null) {
            throw new IllegalStateException("A delegated execution view cannot become a RootActor grant");
        }
        synchronized (authority) {
            if (authority.retired) {
                throw new IllegalStateException("Execution capability is retired");
            }
            if (authority.owner != process) {
                throw new IllegalStateException("Execution capability belongs to another Process");
            }
            if (authority.rootDomain == null) {
                authority.rootDomain = domain;
            } else if (authority.rootDomain != domain) {
                throw new IllegalStateException(
                        "Execution capability is already bound to another RootActor");
            }
        }
    }

    /**
     * Whether this view may be operated from {@code domain}, by identity of the Actor execution
     * domain: the initial view only from its bound RootActor domain, a delegated view only from
     * its destination Actor domain. An initial view not yet bound is usable from no domain.
     */
    public boolean usableFromForRuntime(ProtosActorExecutionDomain domain) {
        if (domain == null) {
            return false;
        }
        if (delegatedDomain != null) {
            return delegatedDomain == domain;
        }
        synchronized (authority) {
            return authority.rootDomain == domain;
        }
    }

    /** True for a view materialized by an Actor transfer. */
    public boolean delegatedForRuntime() {
        return delegatedDomain != null;
    }

    /** True when both views share one logical authorization. */
    public boolean sameAuthorityForRuntime(ProtosExternalExecutionCapabilityValue other) {
        return other != null && other.authority == authority;
    }

    /**
     * Source stage of an explicit delegation: the sending activation must be an Actor of the
     * Process that owns the authority and must be allowed to operate this view. Answers one inert
     * descriptor; launches nothing.
     *
     * @throws IllegalArgumentException when the view may not be delegated from {@code source}
     */
    public Delegation prepareDelegationForRuntime(ProtosActivation source) {
        Objects.requireNonNull(source, "source");
        ProtosActorExecutionDomain domain = source.executionDomain();
        if (!usableFromForRuntime(domain)) {
            throw new IllegalArgumentException("Execution delegation belongs to another Actor");
        }
        if (domain.currentActorForRuntime().isEmpty()) {
            throw new IllegalArgumentException("Execution delegation requires a source Actor");
        }
        ProtosProcessRuntime process =
                ProtosIoLifecycle.hostingProcessForRuntime(domain)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Execution delegation requires a hosting Process"));
        if (ownerForRuntime().orElse(null) != process) {
            throw new IllegalArgumentException("Execution delegation crosses a Process boundary");
        }
        return new Delegation(authority, process);
    }

    /**
     * Destination stage: one fresh view, local to the destination Actor domain, sharing only the
     * authority. Fails closed outside a destination Actor of the same Process and Context.
     *
     * @throws IllegalStateException when the descriptor does not belong to the destination
     */
    public static ProtosExternalExecutionCapabilityValue materializeDelegationForRuntime(
            Delegation descriptor, ProtosActivation destination) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(destination, "destination");
        ProtosActorExecutionDomain domain = destination.executionDomain();
        if (domain.currentActorForRuntime().isEmpty()) {
            throw new IllegalStateException("Execution delegation requires a destination Actor");
        }
        ProtosProcessRuntime process =
                ProtosIoLifecycle.hostingProcessForRuntime(domain).orElse(null);
        Authority authority = descriptor.authority;
        ProtosProcessRuntime owner;
        synchronized (authority) {
            owner = authority.owner;
        }
        if (process == null || process != descriptor.process || owner != process) {
            throw new IllegalStateException("Execution delegation crosses a Process boundary");
        }
        ProtosPrelude prelude =
                destination.prelude()
                        .orElseThrow(
                                () -> new IllegalStateException(
                                        "Execution delegation requires an owning Core prelude"));
        if (!prelude.retainsExecutionProtocolsForRuntime()
                || prelude.executionProtocolsForRuntime() != authority.protocols) {
            throw new IllegalStateException("Execution delegation crosses a Context boundary");
        }
        return new ProtosExternalExecutionCapabilityValue(authority, domain);
    }

    /** Host-only, idempotent revocation of new launches of every view; runs no guest code. */
    public void retireForRuntime() {
        synchronized (authority) {
            authority.retired = true;
        }
    }

    public boolean retiredForRuntime() {
        synchronized (authority) {
            return authority.retired;
        }
    }

    /**
     * Reserves one child slot before launch commitment, bounding the physical resources of the
     * Process. Answers null when every slot is in use or the grant is retired.
     */
    public Admission reserveChildForRuntime() {
        synchronized (authority) {
            if (authority.retired || authority.admittedChildren >= MAX_CHILDREN) {
                return null;
            }
            authority.admittedChildren++;
            return new Admission(authority);
        }
    }

    /** Number of children whose slot is still reserved (custody or transport not retired). */
    public int admittedChildrenForRuntime() {
        synchronized (authority) {
            return authority.admittedChildren;
        }
    }

    /**
     * Admits one committed launch attempt unless the grant was retired. Retirement and admission
     * are atomic, so no native launch begins after definitive revocation; an admitted attempt
     * must be ended by {@link #endLaunchForRuntime()} after its child, if any, is in custody.
     */
    public boolean beginLaunchForRuntime() {
        synchronized (authority) {
            if (authority.retired) {
                return false;
            }
            authority.launchesInFlight++;
            return true;
        }
    }

    public void endLaunchForRuntime() {
        synchronized (authority) {
            authority.launchesInFlight--;
            authority.notifyAll();
        }
    }

    /**
     * Host close: waits at most {@code graceMillis} for admitted launch attempts, so a child created
     * while the Context is closing is in Process custody before reclamation takes its snapshot. A
     * timeout proves nothing about the attempt: the caller keeps its obligation.
     *
     * @return true when no admitted attempt remains
     */
    @TruffleBoundary
    public boolean awaitLaunchesForHostClose(long graceMillis) {
        return awaitAuthority(graceMillis, false);
    }

    /**
     * Host close, after child reclamation: waits at most {@code graceMillis} until no launch is
     * in flight and no child slot is reserved. A child's slot is returned on the thread that
     * discharged it, just after it left the Process registry, so an empty registry alone does not
     * yet show the slot returned. A timeout proves nothing: the caller keeps its obligation.
     *
     * @return true when {@link #hostCustodyDischargedForRuntime()} holds
     */
    @TruffleBoundary
    public boolean awaitHostCustodyDischargeForHostClose(long graceMillis) {
        return awaitAuthority(graceMillis, true);
    }

    private boolean awaitAuthority(long graceMillis, boolean includeAdmissions) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(graceMillis);
        boolean interrupted = false;
        synchronized (authority) {
            try {
                while (authority.launchesInFlight > 0
                        || (includeAdmissions && authority.admittedChildren > 0)) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) {
                        return false;
                    }
                    try {
                        TimeUnit.NANOSECONDS.timedWait(authority, remaining);
                    } catch (InterruptedException interruption) {
                        interrupted = true;
                    }
                }
                return true;
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /** True when no launch is in flight and no child slot is reserved. */
    public boolean hostCustodyDischargedForRuntime() {
        synchronized (authority) {
            return authority.launchesInFlight == 0 && authority.admittedChildren == 0;
        }
    }

    /**
     * Submits short host work to the bounded executor.
     *
     * @return false when the work was rejected: capacity is exhausted or the workers were retired
     */
    public boolean executeForRuntime(Runnable work) {
        try {
            ioExecutorForRuntime().execute(work);
            return true;
        } catch (RejectedExecutionException rejected) {
            return false;
        }
    }

    /**
     * Lazily created bounded executor for launch and other short host work; it never waits on a
     * pipe. An unused grant creates no thread; after worker retirement every submission is
     * rejected.
     */
    public Executor ioExecutorForRuntime() {
        synchronized (authority) {
            if (authority.workersRetired) {
                return work -> {
                    throw new RejectedExecutionException("execution workers are retired");
                };
            }
            if (authority.ioExecutor == null) {
                ThreadPoolExecutor created =
                        new ThreadPoolExecutor(
                                MAX_WORKERS,
                                MAX_WORKERS,
                                30,
                                TimeUnit.SECONDS,
                                new ArrayBlockingQueue<>(MAX_QUEUED),
                                work -> {
                                    Thread thread =
                                            new Thread(
                                                    work,
                                                    "protos-external-execution-"
                                                            + THREAD_SEQUENCE.incrementAndGet());
                                    thread.setDaemon(true);
                                    return thread;
                                },
                                new ThreadPoolExecutor.AbortPolicy());
                created.allowCoreThreadTimeOut(true);
                authority.ioExecutor = created;
            }
            return authority.ioExecutor;
        }
    }

    /**
     * Host close, after child reclamation: stops admission of new host work and retires the
     * launcher's shared transport. Already queued and running work, including committed launches,
     * completes; no worker is interrupted. Idempotent.
     */
    @TruffleBoundary
    public void retireWorkersForHostClose() {
        ThreadPoolExecutor current;
        Launcher bound;
        synchronized (authority) {
            if (authority.workersRetired) {
                return;
            }
            authority.workersRetired = true;
            current = authority.ioExecutor;
            bound = authority.launcher;
        }
        if (current != null) {
            current.shutdown();
        }
        if (bound != null) {
            bound.retireTransport();
        }
    }

    public boolean executorInitializedForTesting() {
        synchronized (authority) {
            return authority.ioExecutor != null;
        }
    }

    public int workerCountForTesting() {
        synchronized (authority) {
            return authority.ioExecutor == null ? 0 : authority.ioExecutor.getPoolSize();
        }
    }

    /**
     * Wraps a started native child under this grant's protocols, without an admission slot and
     * owned by no Actor domain.
     */
    public ProtosExternalChildValue newChildForRuntime(
            ProtosProcessRuntime process, ProtosExternalChildValue.NativeChild nativeChild) {
        return newChildForRuntime(process, nativeChild, null);
    }

    /**
     * Wraps a started native child owned by no Actor domain, so no guest operation accepts it;
     * its admission is released when the child is quiescent.
     */
    public ProtosExternalChildValue newChildForRuntime(
            ProtosProcessRuntime process,
            ProtosExternalChildValue.NativeChild nativeChild,
            Admission admission) {
        return newChildForRuntime(process, nativeChild, admission, null);
    }

    /**
     * Wraps a started native child as the capability published into {@code ownerDomain}, the
     * only Actor domain from which it can ever be operated; its admission is released when the
     * child is quiescent.
     */
    public ProtosExternalChildValue newChildForRuntime(
            ProtosProcessRuntime process,
            ProtosExternalChildValue.NativeChild nativeChild,
            Admission admission,
            ProtosActorExecutionDomain ownerDomain) {
        return new ProtosExternalChildValue(
                authority.protocols,
                process,
                ownerDomain,
                nativeChild,
                ioExecutorForRuntime(),
                admission == null ? null : admission::release);
    }

    @Override
    public Object representedDelegationParent(ProtosPrelude ignored) {
        return authority.protocols.execution();
    }

    @ExportMessage
    String toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return "Object";
    }
}
