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

    private static final class State {
        private final ProtosActor actor;
        private final ArrayDeque<Runnable> controlTurns = new ArrayDeque<>();
        private boolean queued;
        private boolean running;
        private boolean preferMailbox;

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

    /** Attaches one Actor incarnation to this scheduler without creating background work. */
    public void attach(ProtosActor actor) {
        Objects.requireNonNull(actor, "actor");
        synchronized (lock) {
            if (states.containsKey(actor)) {
                throw new IllegalStateException("Actor is already attached to this scheduler");
            }
            states.put(actor, new State(actor));
        }
        try {
            actor.executionDomain().bindSchedulerWakeup(() -> signal(actor, false));
            actor.mailboxForRuntime().bindSchedulerWakeup(() -> signal(actor, false));
        } catch (RuntimeException failure) {
            synchronized (lock) {
                states.remove(actor);
            }
            throw failure;
        }
        signal(actor, false);
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

    /** Removes a terminal/unpublished Actor from this scheduler; queued work is not transferred. */
    public void detach(ProtosActor actor) {
        synchronized (lock) {
            State state = states.remove(actor);
            if (state == null) {
                return;
            }
            if (state.queued) {
                readyActors.remove(state);
                state.queued = false;
            }
            state.controlTurns.clear();
        }
    }

    private State requireState(ProtosActor actor) {
        Objects.requireNonNull(actor, "actor");
        State state = states.get(actor);
        if (state == null) {
            throw new IllegalStateException("Actor is not attached to this scheduler");
        }
        return state;
    }

    private void signal(ProtosActor actor, boolean propagateRejection) {
        boolean startWorker = false;
        synchronized (lock) {
            State state = states.get(actor);
            if (state == null) {
                return;
            }
            if (!state.queued && !state.running && hasSchedulableWork(state)) {
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
                    if (states.get(state.actor) == state && hasSchedulableWork(state)) {
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
        process.runInExecutionHostForRuntime(() -> runOneSegment(state));
    }

    private void runOneSegment(State state) {
        ProtosActor actor = state.actor;
        Runnable control = null;
        synchronized (lock) {
            if (actor.lifecycleState() == ProtosActor.LifecycleState.INITIALIZING) {
                control = state.controlTurns.pollFirst();
            } else {
                // Graceful stop/failure before bootstrap selection makes queued initialization
                // control stale. It must never begin after the termination cutover.
                state.controlTurns.clear();
            }
        }
        if (control != null) {
            control.run();
            return;
        }

        ProtosActorExecutionDomain domain = actor.executionDomain();
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
