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
import com.guillermomolina.protos.runtime.ProtosActorExecutionDomain;
import com.guillermomolina.protos.runtime.ProtosActorExecutionDomain.HostEntryExtent;
import com.guillermomolina.protos.runtime.ProtosActorScheduler;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosTask;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.util.Objects;

/**
 * I158 / D251-A1 Task-free custodian of one suspended Actor initialization ({@code
 * FUTURES_AND_TASKS.md} §29, Actor initialization suspension).
 *
 * <p>One instance belongs to one {@link ProtosActorBootstrap.Sequence} and is created only when that
 * initialization first suspends; programs that never suspend allocate nothing here. It retains
 * exactly the pending C-prime continuation and the ordinary {@link ProtosFutureValue.Observer} of
 * the awaited Future. The observer is registered atomically with the pending check (no lost
 * wakeup) and its terminal notification only asks the Actor scheduler to re-evaluate the Actor.
 * The scheduler resumes the continuation exactly once through {@link #resume()}; a later
 * suspension of the same initialization stores the new continuation in this same custodian, so no
 * second bootstrap exists.
 *
 * <p>Termination is two-phase. {@link #terminationBegun()}, safe from any thread, only invalidates:
 * no resumption is admitted afterwards, the Future observer is removed and late notifications are
 * inert. What happens next depends on the state it finds:
 *
 * <ul>
 *   <li>a retained (suspended) continuation becomes a pending abandon, which the domain owner
 *       performs later in {@link #runCleanup()}; until then termination cannot complete;
 *   <li>a continuation already taken by {@link #takeForResume()} belongs to a segment that is still
 *       executing; custody stays registered until that segment exits, and the segment's own
 *       reservation in the execution domain keeps the Actor out of TERMINATED meanwhile;
 *   <li>with neither, custody is simply released.
 * </ul>
 *
 * <p>No monitor of this object, a Future, the Actor or the domain is held while guest code
 * executes, and {@code terminationBegun()} never runs guest code. No {@link ProtosTask} is created.
 */
final class ProtosActorBootstrapSuspension
        implements ProtosActorScheduler.ExclusiveBootstrap, ProtosActor.BootstrapCustody {
    private final ProtosActorBootstrap.Sequence sequence;
    private final ProtosActor actor;
    /** Null when a host thread drives the initialization synchronously. */
    private final ProtosActorScheduler scheduler;
    private static final Object SUSPENSION_CHANGES = new Object();

    /** Points at which a test may observe one suspension step on the executing thread. */
    enum ProbePoint {
        /** In {@code Future.value()}, before the bootstrap observer is registered. */
        BEFORE_REGISTRATION,
        /** In {@code Future.value()}, after registration found the Future still pending. */
        AFTER_REGISTRATION,
        /** On the segment owner, before custody retains the produced continuation. */
        BEFORE_RETENTION,
        /** Test observes the fully retained initialization before the provider replies. */
        AFTER_RETENTION
    }

    /**
     * Test-only probe; null in production. It runs on the thread executing the step, holds no
     * monitor of this runtime, and cannot alter the protocol except through ordinary runtime
     * operations a host could perform at that instant. Only suspension paths read it.
     */
    static volatile java.util.function.BiConsumer<ProbePoint, ProtosFutureValue> probeForTesting;

    private static void probe(ProbePoint point, ProtosFutureValue future) {
        java.util.function.BiConsumer<ProbePoint, ProtosFutureValue> installed = probeForTesting;
        if (installed != null) {
            installed.accept(point, future);
        }
    }
    private Object continuation;
    private FutureDependency dependency;
    /** Invalidated transports awaiting their abandon under the domain's exclusive ownership. */
    private Object pendingAbandon;
    private boolean custodyRegistered;
    private boolean terminated;
    /** A continuation was taken for resumption and its segment has not exited yet. */
    private boolean resuming;

    ProtosActorBootstrapSuspension(
            ProtosActorBootstrap.Sequence sequence,
            ProtosActor actor,
            ProtosActorScheduler scheduler) {
        this.sequence = Objects.requireNonNull(sequence, "sequence");
        this.actor = Objects.requireNonNull(actor, "actor");
        this.scheduler = scheduler;
    }

    /**
     * Whether {@code activation} executes directly inside an active Actor bootstrap segment. The
     * authority is the installed bootstrap execution mode of the initialization continuation,
     * never the INITIALIZING lifecycle alone and never the presence of a hosted Process.
     */
    static boolean suspendible(ProtosActivation activation) {
        ProtosActorExecutionDomain domain = activation.executionDomain();
        return domain != null
                && domain.hostEntryExtentForRuntime() == HostEntryExtent.BOOTSTRAP
                && activation.task().isEmpty()
                && activation.deferredCPrimeOperationForRuntime().isEmpty()
                && activation.deferredCPrimeReleaseForRuntime().isEmpty();
    }

    /**
     * Whether a suspension-capable native entered from {@code activation} runs its C-prime body.
     * D251-A1 authorizes only {@code Future.value()} (the only suspension-capable native whose
     * receiver is a Future); every other suspension-capable native, such as a text or byte I/O
     * operation, keeps its ordinary non-Task body during initialization, so no deferred C-prime
     * segment excluded by the initialization's exclusivity is ever required.
     */
    static boolean admitsNativeContinuation(ProtosActivation activation) {
        return suspendible(activation) && activation.receiver() instanceof ProtosFutureValue;
    }

    /**
     * The bootstrap continuation body of {@code Future.value()}: the ordinary terminal outcome now,
     * or a native suspension leaf whose resumer projects exactly the Future's resolution, Error or
     * cancellation after the observer fired.
     */
    @TruffleBoundary
    static Object awaitFuture(ProtosActivation activation, ProtosFutureValue observed) {
        FutureDependency waiting = new FutureDependency(observed);
        probe(ProbePoint.BEFORE_REGISTRATION, observed);
        // Registration and the pending check share the Future monitor: no terminalization is lost.
        observed.observe(waiting);
        if (waiting.isReady()) {
            // Terminal before or during registration: observe now, retain nothing.
            waiting.release();
            return observed.observeValue(activation);
        }
        probe(ProbePoint.AFTER_REGISTRATION, observed);
        return ProtosNativeSuspension.pending(waiting, () -> observed.observeValue(activation));
    }

    /**
     * Takes custody of {@code pending}. Called by the domain owner that just ran the segment.
     *
     * @return false when termination already invalidated this initialization; the continuation
     *     has then been abandoned by this owner and must not be used
     */
    boolean suspend(Object pending) {
        Objects.requireNonNull(pending, "pending");
        ProtosNativeSuspension leaf = ProtosTailCallContinuation.nativeSuspensionLeaf(pending);
        if (!(leaf.dependency() instanceof FutureDependency waiting)) {
            ProtosTailCallContinuation.abandon(pending);
            throw new IllegalStateException(
                    "Actor initialization may suspend only on a pending Future.value()");
        }
        probe(ProbePoint.BEFORE_RETENTION, waiting.future);
        boolean register;
        boolean stored;
        synchronized (this) {
            resuming = false;
            if (terminated) {
                register = false;
                stored = false;
            } else {
                if (continuation != null || pendingAbandon != null) {
                    throw new IllegalStateException(
                            "Actor initialization already retains a pending continuation");
                }
                continuation = pending;
                dependency = waiting;
                stored = true;
                register = !custodyRegistered;
                custodyRegistered = true;
            }
        }
        if (register && !actor.registerBootstrapCustodyForRuntime(this)) {
            terminationBegun();
        }
        if (scheduler != null && !isTerminated() && !scheduler.holdExclusiveBootstrap(actor, this)) {
            terminationBegun();
        }
        if (!isTerminated()) {
            // Test-only observation: custody and scheduler exclusivity are installed.
            // No runtime monitor is held. In production the probe is null.
            probe(ProbePoint.AFTER_RETENTION, waiting.future);
            signalSuspensionChange();
            return true;
        }
        // This caller owns the domain (it just ran the segment): abandon here, exactly once.
        waiting.release();
        if (!stored) {
            // Termination won while this segment ran: its fresh continuation is never retained.
            try {
                ProtosTailCallContinuation.abandon(pending);
                sequence.terminatedWhileInitializing(true);
            } finally {
                actor.clearBootstrapCustodyForRuntime(this);
            }
        } else if (takePendingAbandon(pending)) {
            try {
                ProtosTailCallContinuation.abandon(pending);
                sequence.terminatedWhileInitializing(true);
            } finally {
                actor.clearBootstrapCustodyForRuntime(this);
                actor.executionDomain().finishBootstrapCleanupForRuntime();
            }
        }
        return false;
    }

    private synchronized boolean takePendingAbandon(Object pending) {
        if (pendingAbandon != pending) {
            return false;
        }
        pendingAbandon = null;
        return true;
    }

    /**
     * Test-only, read-only: the Future on which {@code actor}'s initialization currently retains a
     * suspended continuation, or null when it is running, terminal or not suspended.
     */
    static ProtosFutureValue awaitedFutureForTesting(ProtosActor actor) {
        return actor.bootstrapCustodyForRuntime()
                .filter(ProtosActorBootstrapSuspension.class::isInstance)
                .map(custody -> ((ProtosActorBootstrapSuspension) custody).awaitedFuture())
                .orElse(null);
    }

    /** Whether a suspended continuation or its pending abandon is still held. */
    synchronized boolean retainsContinuationForRuntime() {
        return continuation != null || pendingAbandon != null;
    }

    /**
     * Test-only, read-only: waits, bounded by {@code timeoutMillis}, until {@code actor}'s
     * initialization retains a suspended continuation on a Future other than {@code previous}.
     * Woken by every custody change; it never polls in a busy loop.
     *
     * @return that Future, or null when the bound elapsed first
     */
    static ProtosFutureValue awaitSuspendedFutureForTesting(
            ProtosActor actor, ProtosFutureValue previous, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        synchronized (SUSPENSION_CHANGES) {
            while (true) {
                ProtosFutureValue awaited = awaitedFutureForTesting(actor);
                if (awaited != null && awaited != previous) {
                    return awaited;
                }
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return null;
                }
                java.util.concurrent.TimeUnit.NANOSECONDS.timedWait(SUSPENSION_CHANGES, remaining);
            }
        }
    }

    /*
     * Custody-change notification for the read-only test inspection above. Only suspension
     * transitions pay for it; an initialization that never suspends never reaches it. Always called
     * with no other monitor held.
     */
    private static void signalSuspensionChange() {
        synchronized (SUSPENSION_CHANGES) {
            SUSPENSION_CHANGES.notifyAll();
        }
    }

    private synchronized ProtosFutureValue awaitedFuture() {
        return !terminated && continuation != null && dependency != null ? dependency.future : null;
    }

    @Override
    public synchronized boolean resumeReady() {
        return !terminated && continuation != null && dependency.isReady();
    }

    /** Consumes the retained continuation exactly once; null when not resumable. */
    synchronized Object takeForResume() {
        if (terminated || continuation == null || !dependency.isReady()) {
            return null;
        }
        Object pending = continuation;
        continuation = null;
        dependency = null;
        resuming = true;
        return pending;
    }

    /**
     * Runs the resumed segment. The scheduler hold stays in place, so the Actor's exclusivity is
     * kept across the resumed segment.
     */
    @Override
    public void resume() {
        Object pending = takeForResume();
        if (pending != null) {
            sequence.resumeSegment(pending);
        }
    }

    /** The initialization reached a terminal outcome: drop custody and exclusivity. */
    void finished() {
        synchronized (this) {
            terminated = true;
            resuming = false;
        }
        actor.clearBootstrapCustodyForRuntime(this);
        if (scheduler != null) {
            scheduler.releaseExclusiveBootstrap(actor, this);
        }
    }

    /**
     * Phase 1, safe from any thread: invalidates the initialization, makes notifications inert and
     * removes the Future observer. A retained continuation is not abandoned here; it is handed to
     * the domain owner ({@link #runCleanup()}), and termination cannot complete before then.
     */
    @Override
    public void terminationBegun() {
        Object retained;
        FutureDependency waiting;
        boolean segmentActive;
        synchronized (this) {
            if (terminated) {
                return;
            }
            terminated = true;
            segmentActive = resuming;
            retained = continuation;
            waiting = dependency;
            continuation = null;
            dependency = null;
            pendingAbandon = retained;
        }
        if (waiting != null) {
            waiting.release();
        }
        if (retained == null) {
            if (!segmentActive) {
                // Neither suspended nor resuming: nothing remains to abandon.
                actor.clearBootstrapCustodyForRuntime(this);
            }
            // Otherwise a resumed segment is executing on its owner. It observes the invalidation
            // when it exits (suspend() or finished()) and releases custody there, never here.
            return;
        }
        actor.executionDomain().beginBootstrapCleanupForRuntime();
        if (scheduler != null) {
            scheduler.wakeForRuntime(actor);
        } else {
            actor.executionDomain().requestSchedulerWakeupForRuntime();
        }
    }

    @Override
    public synchronized boolean cleanupPending() {
        return pendingAbandon != null;
    }

    /**
     * Phase 2, under the domain's exclusive ownership: abandons the invalidated C-prime transports
     * exactly once (no guest code resumes and no {@code ensure} runs), fails the initial module
     * record, releases custody and lets termination complete.
     */
    @Override
    public void runCleanup() {
        Object retained;
        synchronized (this) {
            retained = pendingAbandon;
            pendingAbandon = null;
        }
        if (retained == null) {
            return;
        }
        try {
            // I112: deferred terminal owners close first, innermost first.
            ProtosTailCallContinuation.abandon(retained);
            sequence.terminatedWhileInitializing(true);
        } finally {
            actor.clearBootstrapCustodyForRuntime(this);
            actor.executionDomain().finishBootstrapCleanupForRuntime();
        }
    }

    private synchronized boolean isTerminated() {
        return terminated;
    }

    /** One ordinary observer of one awaited Future; inert once terminal or released. */
    static final class FutureDependency
            implements ProtosTask.WaitDependency, ProtosFutureValue.Observer {
        private final ProtosFutureValue future;
        private volatile boolean ready;
        private volatile boolean released;

        private FutureDependency(ProtosFutureValue future) {
            this.future = Objects.requireNonNull(future, "future");
        }

        @Override
        public void terminal(ProtosFutureValue terminal) {
            if (terminal != future || released) {
                return;
            }
            ready = true;
            future.domain().requestSchedulerWakeupForRuntime();
        }

        @Override
        public boolean isReady() {
            return ready;
        }

        void release() {
            released = true;
            future.removeObserver(this);
        }
    }
}
