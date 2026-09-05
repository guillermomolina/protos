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
            task.parent().ifPresent(parent -> parent.removeChild(task));
        }
    }

    private void requireOwned(ProtosTask task) {
        if (task.owner() != this) {
            throw new IllegalArgumentException("task belongs to another Actor execution domain");
        }
    }
}
