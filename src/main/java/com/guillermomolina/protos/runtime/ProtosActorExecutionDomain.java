/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
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
    private final ArrayDeque<ProtosTask> runnable = new ArrayDeque<>();
    private final Set<ProtosTask> liveTasks = new LinkedHashSet<>();
    private final Set<ProtosIoOperation> actorIoOperations = new LinkedHashSet<>();
    private ProtosActor ownerActor;

    public ProtosTask createTask(
            ProtosTask parent, Object associatedFuture, ProtosTask.Continuation continuation) {
        Objects.requireNonNull(continuation, "continuation");
        ProtosTask task = new ProtosTask(this, parent, associatedFuture, continuation);
        synchronized (this) {
            if (parent != null && parent.owner() != this) {
                throw new IllegalArgumentException("structured parent belongs to another Actor domain");
            }
            if (parent != null) {
                parent.addChild(task);
            }
            liveTasks.add(task);
        }
        enqueue(task);
        return task;
    }

    public ProtosTask createTask(Object associatedFuture, ProtosTask.Continuation continuation) {
        return createTask(null, associatedFuture, continuation);
    }

    void enqueue(ProtosTask task) {
        Objects.requireNonNull(task, "task");
        synchronized (this) {
            requireOwned(task);
            if (task.markQueued()) {
                runnable.addLast(task);
                notifyAll();
            }
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

    public synchronized int liveTaskCount() {
        return liveTasks.size();
    }

    public synchronized Optional<ProtosTask> nextRunnableForTesting() {
        return Optional.ofNullable(runnable.peekFirst());
    }

    void terminal(ProtosTask task) {
        synchronized (this) {
            requireOwned(task);
            liveTasks.remove(task);
            notifyAll();
            task.parent().ifPresent(parent -> parent.removeChild(task));
        }
    }

    void registerActorIoOperation(ProtosIoOperation operation) {
        synchronized (this) { actorIoOperations.add(Objects.requireNonNull(operation, "operation")); }
    }

    void terminalActorIoOperation(ProtosIoOperation operation) {
        synchronized (this) { actorIoOperations.remove(operation); }
    }

    /** Runtime Actor-incarnation termination hook; pending I/O receives ordinary cancellation requests. */
    public void actorTerminated() {
        Set<ProtosIoOperation> pending;
        synchronized (this) { pending = Set.copyOf(actorIoOperations); }
        for (ProtosIoOperation operation : pending) operation.requestCancellation();
    }

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
