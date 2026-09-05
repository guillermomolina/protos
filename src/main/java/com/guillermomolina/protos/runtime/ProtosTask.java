/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
 */
package com.guillermomolina.protos.runtime;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Internal Actor-local unit of cooperatively scheduled Protos execution.
 *
 * <p>This is runtime machinery, not a Protos-visible Task value.
 */
public final class ProtosTask {
    public enum State {
        RUNNABLE,
        RUNNING,
        SUSPENDED,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    /** Opaque semantic prerequisite. I009 can use a Future waiter as one implementation. */
    public interface WaitDependency {}

    @FunctionalInterface
    public interface Continuation {
        void resume(ProtosTask task);
    }

    private final ProtosActorExecutionDomain owner;
    private final ProtosTask parent;
    private final Set<ProtosTask> children = new LinkedHashSet<>();
    private final Object associatedFuture;
    private final Continuation continuation;

    private State state = State.RUNNABLE;
    private boolean queued;
    private boolean cancellationRequested;
    private WaitDependency waitDependency;
    private Object result;
    private Object failure;

    ProtosTask(
            ProtosActorExecutionDomain owner,
            ProtosTask parent,
            Object associatedFuture,
            Continuation continuation) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.parent = parent;
        this.associatedFuture = associatedFuture;
        this.continuation = Objects.requireNonNull(continuation, "continuation");
    }

    public ProtosActorExecutionDomain owner() {
        return owner;
    }

    public synchronized Optional<ProtosTask> parent() {
        return Optional.ofNullable(parent);
    }

    public synchronized Set<ProtosTask> children() {
        return Set.copyOf(children);
    }

    public synchronized Optional<Object> associatedFuture() {
        return Optional.ofNullable(associatedFuture);
    }

    public synchronized State state() {
        return state;
    }

    public synchronized boolean cancellationRequested() {
        return cancellationRequested;
    }

    public synchronized Optional<WaitDependency> waitDependency() {
        return Optional.ofNullable(waitDependency);
    }

    public synchronized Optional<Object> result() {
        return Optional.ofNullable(result);
    }

    public synchronized Optional<Object> failure() {
        return Optional.ofNullable(failure);
    }

    synchronized void addChild(ProtosTask child) {
        if (isTerminal()) {
            throw new IllegalStateException("terminal task cannot acquire a structured child");
        }
        children.add(Objects.requireNonNull(child, "child"));
    }

    synchronized void removeChild(ProtosTask child) {
        children.remove(child);
    }

    synchronized boolean markQueued() {
        if (state != State.RUNNABLE || queued) {
            return false;
        }
        queued = true;
        return true;
    }

    synchronized boolean beginDispatch() {
        if (state != State.RUNNABLE || !queued) {
            return false;
        }
        queued = false;
        state = State.RUNNING;
        return true;
    }

    void runContinuation() {
        continuation.resume(this);
    }

    public void suspend(WaitDependency dependency) {
        Objects.requireNonNull(dependency, "dependency");
        boolean cancellationWake;
        synchronized (this) {
            requireState(State.RUNNING, "suspend");
            if (cancellationRequested) {
                state = State.RUNNABLE;
                waitDependency = null;
                cancellationWake = true;
            } else {
                state = State.SUSPENDED;
                waitDependency = dependency;
                cancellationWake = false;
            }
        }
        if (cancellationWake) {
            owner.enqueue(this);
        }
    }

    public boolean resume(WaitDependency dependency) {
        Objects.requireNonNull(dependency, "dependency");
        synchronized (this) {
            if (state != State.SUSPENDED || waitDependency != dependency) {
                return false;
            }
            waitDependency = null;
            state = State.RUNNABLE;
        }
        owner.enqueue(this);
        return true;
    }

    /**
     * Requests cooperative cancellation. A suspended task becomes runnable immediately so that
     * cancellation can be observed without waiting for its prerequisite. The prerequisite itself
     * is deliberately untouched.
     */
    public boolean requestCancellation() {
        boolean enqueue = false;
        synchronized (this) {
            if (isTerminal()) {
                return false;
            }
            if (cancellationRequested) {
                return false;
            }
            cancellationRequested = true;
            if (state == State.SUSPENDED) {
                waitDependency = null;
                state = State.RUNNABLE;
                enqueue = true;
            }
        }
        if (enqueue) {
            owner.enqueue(this);
        }
        return true;
    }

    /**
     * Mandatory cooperative cancellation observation boundary used by future suspension/resume.
     */
    public boolean observeCancellation() {
        synchronized (this) {
            if (!cancellationRequested || isTerminal()) {
                return false;
            }
            if (state != State.RUNNING) {
                throw new IllegalStateException("cancellation can be observed only by running task");
            }
            state = State.CANCELLED;
        }
        owner.terminal(this);
        return true;
    }

    public void complete(Object value) {
        synchronized (this) {
            requireState(State.RUNNING, "complete");
            state = State.COMPLETED;
            result = value;
        }
        owner.terminal(this);
    }

    public void fail(Object error) {
        synchronized (this) {
            requireState(State.RUNNING, "fail");
            state = State.FAILED;
            failure = Objects.requireNonNull(error, "error");
        }
        owner.terminal(this);
    }

    private boolean isTerminal() {
        return state == State.COMPLETED || state == State.FAILED || state == State.CANCELLED;
    }

    private void requireState(State required, String operation) {
        if (state != required) {
            throw new IllegalStateException(operation + " requires " + required + ", was " + state);
        }
    }
}
