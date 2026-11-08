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

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Internal cross-Actor cooperative scheduler.
 *
 * <p>Each selected Actor receives at most one non-preemptive Protos segment before a still-runnable
 * incarnation is requeued at the tail. Distinct Actors may run on distinct carriers, while one
 * Actor incarnation never executes two Protos segments concurrently through this scheduler.
 */
public final class ProtosActorScheduler {
    private static final int DEFAULT_PARALLELISM =
            Math.max(1, Runtime.getRuntime().availableProcessors());

    /**
     * I158 / D251-A1: the custodian of one suspended Task-free Actor initialization.
     *
     * <p>While it is held for an INITIALIZING Actor, that initialization keeps the Actor's
     * execution exclusivity: the only work admitted for the Actor is the resumption of that same
     * initialization and targeted runtime completions dispatched without Task handoff. Ordinary
     * Tasks, deferred C-prime segments and mailbox turns stay queued. Implementations are invoked
     * under the scheduler lock for {@link #resumeReady()} and must therefore never call back into
     * this scheduler while holding their own monitor.
     */
    /**
     * A bootstrap control turn whose host placement can fail before run() starts.
     * This callback is never invoked after the control turn has been dequeued.
     */
    public interface HostEntryFailureAwareControl extends Runnable {
        void hostEntryFailed(Throwable failure);
    }

    public interface ExclusiveBootstrap {
        /** Whether the suspended continuation's prerequisite is terminal and it may resume. */
        boolean resumeReady();

        /** Runs one resumed initialization segment on the current carrier, in the hosted Context. */
        void resume();

        /**
         * Whether termination invalidated the initialization and its C-prime transports still
         * await their abandon under the domain's exclusive ownership.
         */
        default boolean cleanupPending() {
            return false;
        }

        /**
         * Abandons the invalidated transports exactly once and releases custody. Runs no Task,
         * message or Protos user code.
         */
        default void runCleanup() {}
    }

    private static final class State {
        private final ProtosActor actor;
        private final ArrayDeque<Runnable> controlTurns = new ArrayDeque<>();
        /** I158: the suspended exclusive initialization of this Actor, when present. */
        private ExclusiveBootstrap bootstrap;
        private boolean queued;
        private boolean running;
        private boolean preferMailbox;
        /** D228-A1: a live observation permit excludes Protos segments. */
        private boolean observed;
        /**
         * D228-A1: the State exists only to hold an observation permit for an
         * Actor this scheduler has not attached; it is not schedulable.
         */
        private boolean observationPlaceholder;

        private State(ProtosActor actor) {
            this.actor = actor;
        }
    }

    private final Executor carrierExecutor;
    private final int parallelism;
    private final Object lock = new Object();
    private final ArrayDeque<State> readyActors = new ArrayDeque<>();
    private final IdentityHashMap<ProtosActor, State> states = new IdentityHashMap<>();
    private int activeWorkers;

    /**
     * Staged fallback for unhosted or explicitly host-neutral execution.
     *
     * <p>Each scheduler worker uses one ordinary platform thread and may execute multiple Actor
     * segments before the ready queue drains. Hosted production Processes obtain the shared
     * RuntimeHost-owned reusable carrier substrate through their execution host instead.
     */
    public ProtosActorScheduler() {
        this(ProtosActorScheduler::startFallbackPlatformWorker, DEFAULT_PARALLELISM);
    }

    private static void startFallbackPlatformWorker(Runnable command) {
        Thread carrier = new Thread(command, "protos-actor-fallback");
        carrier.setDaemon(true);
        carrier.start();
    }

    public ProtosActorScheduler(Executor carrierExecutor, int parallelism) {
        this.carrierExecutor = Objects.requireNonNull(carrierExecutor, "carrierExecutor");
        if (parallelism <= 0) {
            throw new IllegalArgumentException("Actor scheduler parallelism must be positive");
        }
        this.parallelism = parallelism;
    }

    /**
     * D228-A1 short-lived exclusive observation permit for one Actor.
     *
     * <p>Admission is immediate and never waits: it is denied while the Actor is executing a
     * segment, already holds a permit, or is terminating/terminated. While the permit is live the
     * Actor is removed from, and cannot re-enter, the ready queue, so no segment of that Actor can
     * begin; its pending work is kept and becomes eligible again on {@link
     * ObservationPermit#close()}. Other Actors are unaffected. An Actor that this scheduler has not
     * attached cannot have a segment started here; its permit is held by a non-schedulable
     * placeholder that a concurrent {@link #attach} adopts without scheduling until release.</p>
     */
    public Optional<ObservationPermit> tryAcquireObservation(ProtosActor actor) {
        Objects.requireNonNull(actor, "actor");
        synchronized (lock) {
            ProtosActor.LifecycleState lifecycle = actor.lifecycleState();
            if (lifecycle == ProtosActor.LifecycleState.TERMINATING
                    || lifecycle == ProtosActor.LifecycleState.TERMINATED) {
                return Optional.empty();
            }
            State state = states.get(actor);
            if (state == null) {
                state = new State(actor);
                state.observationPlaceholder = true;
                states.put(actor, state);
            } else if (state.running || state.observed) {
                return Optional.empty();
            } else if (holdsExclusiveBootstrap(state)) {
                // I158: a suspended initialization retains Actor exclusivity; no observation entry.
                return Optional.empty();
            }
            state.observed = true;
            if (state.queued) {
                readyActors.remove(state);
                state.queued = false;
            }
            return Optional.of(new ObservationPermit(this, state));
        }
    }

    /** Releasable D228-A1 observation admission; closing is idempotent. */
    public static final class ObservationPermit implements AutoCloseable {
        private final ProtosActorScheduler scheduler;
        private State state;

        private ObservationPermit(ProtosActorScheduler scheduler, State state) {
            this.scheduler = scheduler;
            this.state = state;
        }

        @Override
        public void close() {
            State released;
            synchronized (scheduler.lock) {
                released = state;
                state = null;
            }
            if (released != null) {
                scheduler.releaseObservation(released);
            }
        }
    }

    private void releaseObservation(State state) {
        synchronized (lock) {
            state.observed = false;
            if (states.get(state.actor) != state) {
                return;
            }
            if (state.observationPlaceholder) {
                states.remove(state.actor);
                return;
            }
        }
        signal(state.actor, false);
    }

    /** Attaches one Actor incarnation to this scheduler without creating background work. */
    public void attach(ProtosActor actor) {
        Objects.requireNonNull(actor, "actor");
        synchronized (lock) {
            State existing = states.get(actor);
            if (existing != null && existing.observationPlaceholder) {
                // A live D228-A1 permit keeps the adopted State unschedulable until release.
                existing.observationPlaceholder = false;
            } else if (existing != null) {
                throw new IllegalStateException("Actor is already attached to this scheduler");
            } else {
                states.put(actor, new State(actor));
            }
        }
        try {
            actor.executionDomain().bindSchedulerWakeup(() -> signal(actor, false));
            actor.mailboxForRuntime().bindSchedulerWakeup(() -> signal(actor, false));
        } catch (RuntimeException failure) {
            synchronized (lock) {
                unregister(actor);
            }
            throw failure;
        }
        signal(actor, false);
    }

    /*
     * D228-A1: removes the attachment of a State under the lock. A State still holding a live
     * observation permit reverts to a non-schedulable placeholder instead, so a second permit
     * cannot be granted before release; the release then drops it.
     */
    private void unregister(ProtosActor actor) {
        State state = states.get(actor);
        if (state == null) {
            return;
        }
        if (state.queued) {
            readyActors.remove(state);
            state.queued = false;
        }
        state.controlTurns.clear();
        state.bootstrap = null;
        if (state.observed) {
            state.observationPlaceholder = true;
        } else {
            states.remove(actor);
        }
    }

    /**
     * Schedules one Actor-internal control turn, currently used for post-cutover bootstrap.
     * Control turns are runtime machinery and are not Actor messages.
     */
    public void submitControl(ProtosActor actor, Runnable turn) {
        Objects.requireNonNull(turn, "turn");
        synchronized (lock) {
            State state = requireState(actor);
            state.controlTurns.addLast(turn);
        }
        signal(actor, true);
    }

    /**
     * I158: records that the INITIALIZING Actor's single initialization suspended while running
     * its current segment. Its custodian becomes the only resumable control of the Actor until it
     * is released, READY is published or termination begins.
     *
     * @return false when the Actor is no longer INITIALIZING (or not attached); the caller then
     *     abandons the continuation itself
     */
    public boolean holdExclusiveBootstrap(ProtosActor actor, ExclusiveBootstrap bootstrap) {
        Objects.requireNonNull(bootstrap, "bootstrap");
        synchronized (lock) {
            State state = states.get(actor);
            if (state == null || state.observationPlaceholder
                    || actor.lifecycleState() != ProtosActor.LifecycleState.INITIALIZING) {
                return false;
            }
            if (state.bootstrap != null && state.bootstrap != bootstrap) {
                throw new IllegalStateException("Actor already holds another exclusive bootstrap");
            }
            state.bootstrap = bootstrap;
        }
        signal(actor, false);
        return true;
    }

    /** I158: releases a held initialization custodian; idempotent and identity-checked. */
    public void releaseExclusiveBootstrap(ProtosActor actor, ExclusiveBootstrap bootstrap) {
        Objects.requireNonNull(bootstrap, "bootstrap");
        synchronized (lock) {
            State state = states.get(actor);
            if (state != null && state.bootstrap == bootstrap) {
                state.bootstrap = null;
            }
        }
        signal(actor, false);
    }

    /** Re-evaluates one Actor's schedulability; used by runtime wakeups that hold no monitor. */
    public void wakeForRuntime(ProtosActor actor) {
        signal(actor, false);
    }

    /*
     * I158: exclusivity applies only to a held custodian of an INITIALIZING Actor. A direct-entry
     * RootActor is INITIALIZING without any held bootstrap and keeps its existing admission.
     */
    private static boolean holdsExclusiveBootstrap(State state) {
        return state.bootstrap != null
                && state.actor.lifecycleState() == ProtosActor.LifecycleState.INITIALIZING;
    }

    /** Removes a terminal/unpublished Actor from this scheduler; queued work is not transferred. */
    public void detach(ProtosActor actor) {
        synchronized (lock) {
            unregister(actor);
        }
    }

    private State requireState(ProtosActor actor) {
        Objects.requireNonNull(actor, "actor");
        State state = states.get(actor);
        if (state == null || state.observationPlaceholder) {
            throw new IllegalStateException("Actor is not attached to this scheduler");
        }
        return state;
    }

    private void signal(ProtosActor actor, boolean propagateRejection) {
        boolean startWorker = false;
        synchronized (lock) {
            State state = states.get(actor);
            if (state == null || state.observationPlaceholder) {
                return;
            }
            if (!state.queued && !state.running && !state.observed
                    && hasSchedulableWork(state)) {
                state.queued = true;
                readyActors.addLast(state);
            }
            if (!readyActors.isEmpty() && activeWorkers < parallelism) {
                activeWorkers++;
                startWorker = true;
            }
        }
        if (!startWorker) {
            return;
        }
        try {
            carrierExecutor.execute(this::workerLoop);
        } catch (RuntimeException rejection) {
            synchronized (lock) {
                activeWorkers--;
            }
            if (propagateRejection) {
                throw rejection;
            }
        }
    }

    private void workerLoop() {
        while (true) {
            State state;
            synchronized (lock) {
                state = readyActors.pollFirst();
                if (state == null) {
                    activeWorkers--;
                    return;
                }
                state.queued = false;
                state.running = true;
            }

            try {
                runOneHostedSegment(state);
            } catch (RuntimeException unhandledTurnFailure) {
                terminateAfterUnhandledTurn(state.actor);
            } finally {
                synchronized (lock) {
                    state.running = false;
                    if (states.get(state.actor) == state && !state.observed
                            && hasSchedulableWork(state)) {
                        state.queued = true;
                        readyActors.addLast(state);
                    } else if (state.actor.lifecycleState()
                            == ProtosActor.LifecycleState.TERMINATED) {
                        states.remove(state.actor);
                    }
                }
            }
        }
    }

    private void runOneHostedSegment(State state) {
        ProtosProcessRuntime process = state.actor.processForRuntime().orElse(null);
        if (process == null) {
            runOneSegment(state);
            return;
        }
        try {
            process.runInExecutionHostForRuntime(() -> runOneSegment(state));
        } catch (RuntimeException | Error hostFailure) {
            HostEntryFailureAwareControl rejected = null;
            synchronized (lock) {
                // If the control is still queued, the host rejected the entry
                // before runOneSegment could dequeue and execute it.
                // Never report this failure a second time after execution began.
                if (state.actor.lifecycleState()
                                == ProtosActor.LifecycleState.INITIALIZING
                        && state.controlTurns.peekFirst()
                                instanceof HostEntryFailureAwareControl pending) {
                    state.controlTurns.removeFirst();
                    rejected = pending;
                }
            }
            if (rejected != null) {
                rejected.hostEntryFailed(hostFailure);
            }
            throw hostFailure;
        }
    }

    private void runOneSegment(State state) {
        ProtosActor actor = state.actor;
        Runnable control = null;
        ExclusiveBootstrap bootstrap = null;
        ExclusiveBootstrap cleanup = null;
        synchronized (lock) {
            if (actor.lifecycleState() == ProtosActor.LifecycleState.TERMINATING
                    && state.bootstrap != null
                    && state.bootstrap.cleanupPending()) {
                // I158: the deferred abandon has priority over any stale resumption.
                cleanup = state.bootstrap;
                state.bootstrap = null;
                state.controlTurns.clear();
            } else if (actor.lifecycleState() == ProtosActor.LifecycleState.INITIALIZING) {
                bootstrap = state.bootstrap;
                if (bootstrap == null) {
                    control = state.controlTurns.pollFirst();
                }
            } else {
                // Graceful stop/failure before bootstrap selection makes queued initialization
                // control stale. It must never begin after the termination cutover. A held
                // suspended initialization is equally stale; its custodian was invalidated by the
                // termination cutover itself.
                state.controlTurns.clear();
                state.bootstrap = null;
            }
        }
        if (cleanup != null) {
            cleanup.runCleanup();
            return;
        }
        if (control != null) {
            control.run();
            return;
        }

        ProtosActorExecutionDomain domain = actor.executionDomain();
        if (bootstrap != null) {
            /*
             * I158 / D251-A1: a suspended initialization retains exclusivity. Its own resumption
             * wins; otherwise only one targeted runtime completion runs, without Task handoff.
             * Ordinary Tasks and mailbox turns are neither run nor discarded.
             */
            if (bootstrap.resumeReady()) {
                bootstrap.resume();
            } else {
                domain.dispatchTargetedRuntimeCompletionWithoutHandoffForRuntime();
            }
            return;
        }
        boolean taskReady = domain.hasRunnableForRuntime();
        boolean messageReady =
                actor.lifecycleState() == ProtosActor.LifecycleState.READY
                        && actor.mailboxForRuntime().hasAccepted();

        if (taskReady && messageReady) {
            boolean mailboxFirst;
            synchronized (lock) {
                mailboxFirst = state.preferMailbox;
                state.preferMailbox = !state.preferMailbox;
            }
            if (mailboxFirst) {
                dispatchAcceptedMessage(actor, domain);
            } else {
                domain.dispatchOne();
            }
            return;
        }
        if (taskReady) {
            domain.dispatchOne();
            return;
        }
        if (messageReady) {
            dispatchAcceptedMessage(actor, domain);
        }
    }

    private static void dispatchAcceptedMessage(
            ProtosActor actor, ProtosActorExecutionDomain domain) {
        ProtosTask.Continuation turn = actor.mailboxForRuntime().pollForDispatch();
        if (turn != null) {
            domain.dispatchAcceptedTurn(turn);
        }
    }

    private static void terminateAfterUnhandledTurn(ProtosActor actor) {
        actor.requestTerminationForRuntime();
    }

    private boolean hasSchedulableWork(State state) {
        ProtosActor actor = state.actor;
        if (actor.lifecycleState() == ProtosActor.LifecycleState.TERMINATED) {
            return false;
        }
        if (state.bootstrap != null
                && actor.lifecycleState() == ProtosActor.LifecycleState.TERMINATING
                && state.bootstrap.cleanupPending()) {
            return true;
        }
        if (holdsExclusiveBootstrap(state)) {
            // I158: excluded Tasks and accepted messages never make a suspended bootstrap runnable.
            return state.bootstrap.resumeReady()
                    || actor.executionDomain().hasTargetedRuntimeCompletionForRuntime();
        }
        if (actor.lifecycleState() == ProtosActor.LifecycleState.INITIALIZING
                && !state.controlTurns.isEmpty()) {
            return true;
        }
        if (actor.executionDomain().hasRunnableForRuntime()) {
            return true;
        }
        return actor.lifecycleState() == ProtosActor.LifecycleState.READY
                && actor.mailboxForRuntime().hasAccepted();
    }
}
