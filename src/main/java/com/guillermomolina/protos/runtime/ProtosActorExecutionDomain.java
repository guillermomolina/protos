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
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Actor-local cooperative scheduling domain.
 *
 * <p>The queue is FIFO for this implementation. FIFO is an implementation policy, not a new
 * language-level total ordering guarantee; it also satisfies weak fairness for continuously
 * runnable tasks when dispatch continues.
 */
public final class ProtosActorExecutionDomain {
    private static final Runnable NOOP_WAKEUP = () -> {};

    private final ArrayDeque<ProtosTask> runnable = new ArrayDeque<>();
    private final Set<ProtosTask> liveTasks = new LinkedHashSet<>();
    private final Set<ProtosIoOperation> actorIoOperations = new LinkedHashSet<>();
    private final Set<ProtosFutureValue> actorNonTaskFutures = new LinkedHashSet<>();
    private ProtosActor ownerActor;
    private Runnable schedulerWakeup = NOOP_WAKEUP;

    public ProtosTask createTask(
            ProtosTask parent, Object associatedFuture, ProtosTask.Continuation continuation) {
        Objects.requireNonNull(continuation, "continuation");
        ProtosTask task = new ProtosTask(this, parent, associatedFuture, continuation);
        boolean cancelOnStart;
        synchronized (this) {
            if (parent != null && parent.owner() != this) {
                throw new IllegalArgumentException("structured parent belongs to another Actor domain");
            }
            if (ownerActor != null
                    && ownerActor.lifecycleState() == ProtosActor.LifecycleState.TERMINATED) {
                throw new IllegalStateException("terminated Actor cannot create Actor-local tasks");
            }
            if (parent != null) {
                parent.addChild(task);
            }
            liveTasks.add(task);
            cancelOnStart = ownerActor != null
                    && ownerActor.lifecycleState() == ProtosActor.LifecycleState.TERMINATING;
        }
        enqueue(task);
        if (cancelOnStart) {
            task.requestCancellation();
        }
        return task;
    }

    public ProtosTask createTask(Object associatedFuture, ProtosTask.Continuation continuation) {
        return createTask(null, associatedFuture, continuation);
    }

    void enqueue(ProtosTask task) {
        Objects.requireNonNull(task, "task");
        Runnable wakeup = null;
        synchronized (this) {
            requireOwned(task);
            if (task.markQueued()) {
                runnable.addLast(task);
                notifyAll();
                wakeup = schedulerWakeup;
            }
        }
        if (wakeup != null) {
            wakeup.run();
        }
    }

    /** Dispatches at most one cooperative execution segment. */
    public boolean dispatchOne() {
        ProtosTask task;
        synchronized (this) {
            do {
                task = runnable.pollFirst();
                if (task == null) {
                    return false;
                }
            } while (!task.beginDispatch());
        }
        task.runContinuation();
        return true;
    }

    /** Starts one already-accepted mailbox message as the current Actor segment. */
    ProtosTask dispatchAcceptedTurn(ProtosTask.Continuation continuation) {
        Objects.requireNonNull(continuation, "continuation");
        ProtosTask task = new ProtosTask(this, null, null, continuation);
        synchronized (this) {
            liveTasks.add(task);
        }
        if (!task.beginDirectDispatch()) {
            throw new IllegalStateException("fresh mailbox task could not begin dispatch");
        }
        task.runContinuation();
        return task;
    }

    public void dispatchUntilIdle() {
        while (dispatchOne()) {
            // Cooperative segments themselves decide whether to suspend or terminate.
        }
    }

    public void dispatchUntilTerminal(ProtosTask root,java.util.function.BooleanSupplier helper) {
        Objects.requireNonNull(root);Objects.requireNonNull(helper);
        while(true){ProtosTask.State s=root.state();if(s==ProtosTask.State.COMPLETED||s==ProtosTask.State.FAILED||s==ProtosTask.State.CANCELLED)return;if(dispatchOne()||helper.getAsBoolean())continue;synchronized(this){s=root.state();if(s==ProtosTask.State.COMPLETED||s==ProtosTask.State.FAILED||s==ProtosTask.State.CANCELLED)return;if(!runnable.isEmpty())continue;try{wait();}catch(InterruptedException e){Thread.currentThread().interrupt();root.requestCancellation();}}}
    }

    public synchronized int runnableCount() {
        return runnable.size();
    }

    synchronized boolean hasRunnableForRuntime() {
        return !runnable.isEmpty();
    }

    public synchronized int liveTaskCount() {
        return liveTasks.size();
    }

    public synchronized Optional<ProtosTask> nextRunnableForTesting() {
        return Optional.ofNullable(runnable.peekFirst());
    }

    void terminal(ProtosTask task) {
        ProtosActor actor;
        synchronized (this) {
            requireOwned(task);
            liveTasks.remove(task);
            notifyAll();
            task.parent().ifPresent(parent -> parent.removeChild(task));
            actor = ownerActor;
        }
        if (actor != null) {
            actor.tryCompleteTerminationForRuntime();
        }
    }

    void registerActorIoOperation(ProtosIoOperation operation) {
        synchronized (this) { actorIoOperations.add(Objects.requireNonNull(operation, "operation")); }
    }

    void terminalActorIoOperation(ProtosIoOperation operation) {
        synchronized (this) { actorIoOperations.remove(operation); }
    }

    /** TERMINATING cutover hook: request cooperative cancellation without undoing commitments. */
    public void actorTerminationBegun() {
        Set<ProtosTask> tasks;
        Set<ProtosIoOperation> io;
        Set<ProtosFutureValue> nonTask;
        synchronized (this) {
            tasks = Set.copyOf(liveTasks);
            io = Set.copyOf(actorIoOperations);
            nonTask = Set.copyOf(actorNonTaskFutures);
        }
        for (ProtosTask task : tasks) task.requestCancellation();
        for (ProtosIoOperation operation : io) operation.requestCancellation();
        for (ProtosFutureValue future : nonTask) future.cancelRequest();
    }

    /** Historical/internal compatibility hook; termination cancellation now starts at TERMINATING. */
    public void actorTerminated() {
        actorTerminationBegun();
    }

    void registerActorNonTaskFuture(ProtosFutureValue future) {
        Objects.requireNonNull(future, "future");
        boolean cancelNow;
        synchronized (this) {
            if (!future.isPending()) return;
            actorNonTaskFutures.add(future);
            cancelNow = ownerActor != null
                    && (ownerActor.lifecycleState() == ProtosActor.LifecycleState.TERMINATING
                            || ownerActor.lifecycleState() == ProtosActor.LifecycleState.TERMINATED);
        }
        future.observe(ignored -> terminalActorNonTaskFuture(future));
        if (cancelNow) future.cancelRequest();
    }

    void terminalActorNonTaskFuture(ProtosFutureValue future) {
        synchronized (this) { actorNonTaskFutures.remove(future); }
    }

    synchronized boolean hasLiveTasksForRuntime() { return !liveTasks.isEmpty(); }
    synchronized int actorNonTaskFutureCountForTesting() { return actorNonTaskFutures.size(); }
    synchronized int actorIoOperationCountForTesting() { return actorIoOperations.size(); }

    void bindActor(ProtosActor actor) {
        Objects.requireNonNull(actor, "actor");
        synchronized (this) {
            if (ownerActor != null && ownerActor != actor) {
                throw new IllegalStateException("execution domain already belongs to another Actor");
            }
            ownerActor = actor;
        }
    }

    void bindSchedulerWakeup(Runnable wakeup) {
        Objects.requireNonNull(wakeup, "wakeup");
        synchronized (this) {
            if (schedulerWakeup != NOOP_WAKEUP) {
                throw new IllegalStateException("execution domain is already attached to a scheduler");
            }
            schedulerWakeup = wakeup;
        }
    }

    /** Runtime substrate for the future Actor.current() primitive; no global current Actor. */
    public synchronized Optional<ProtosActorRefValue> currentActorReference() {
        return ownerActor == null ? Optional.empty() : Optional.of(ownerActor.reference());
    }

    private void requireOwned(ProtosTask task) {
        if (task.owner() != this) {
            throw new IllegalArgumentException("task belongs to another Actor execution domain");
        }
    }
}
