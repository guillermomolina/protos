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

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import com.oracle.truffle.api.CallTarget;
import com.guillermomolina.protos.execution.ProtosEvaluatorSuspension;
import com.guillermomolina.protos.execution.ProtosTaskCancellationException;

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
    public interface WaitDependency {
        /** Removes only this task's waiting relationship; it must not cancel the dependency itself. */
        default void waitingTaskCancelled(ProtosTask task) {}
        /** True once the prerequisite is already ready; used to close the register/suspend race. */
        default boolean isReady() { return false; }
    }

    @FunctionalInterface
    public interface Continuation {
        void resume(ProtosTask task);
    }

    private final ProtosActorExecutionDomain owner;
    private ProtosTask parent;
    private final Set<ProtosTask> children = new LinkedHashSet<>();
    private final Object associatedFuture;
    private final Continuation continuation;

    private State state = State.RUNNABLE;
    private boolean queued;
    private boolean cancellationRequested;
    private boolean continuationStarted;
    private WaitDependency waitDependency;
    private Object result;
    private Object failure;
    private final ProtosEvaluatorContinuation evaluatorContinuation = new ProtosEvaluatorContinuation();
    private WaitDependency resumedDependency;
    private final WaitDependency childDrain = new WaitDependency() {};
    private Object pendingCompletion;

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

    public ProtosEvaluatorContinuation evaluatorContinuation() {
        return evaluatorContinuation;
    }

    /** Executes one real Truffle evaluation segment for this cooperative task. */
    public void executeProtos(CallTarget target, ProtosActivation activation) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(activation, "activation");
        activation.attachTask(this);
        evaluatorContinuation.beginSegment();
        try {
            Object value = target.call(activation);
            complete(value);
        } catch (ProtosEvaluatorSuspension suspended) {
            // suspend() already changed the task state; returning yields the host thread to the domain.
        } catch (ProtosTaskCancellationException cancelled) {
            // observeCancellation() already terminalized the task.
        } catch (ProtosSignalException signalled) {
            fail(signalled.error());
        } finally {
            evaluatorContinuation.endSegment();
        }
    }

    public synchronized boolean consumeResume(WaitDependency dependency) {
        if (state != State.RUNNING || resumedDependency != dependency) {
            return false;
        }
        resumedDependency = null;
        return true;
    }

    synchronized void addChild(ProtosTask child) {
        if (isTerminal()) {
            throw new IllegalStateException("terminal task cannot acquire a structured child");
        }
        children.add(Objects.requireNonNull(child, "child"));
    }

    void removeChild(ProtosTask child) {
        boolean wake;
        synchronized (this) {
            children.remove(child);
            wake = children.isEmpty() && state == State.SUSPENDED && waitDependency == childDrain;
        }
        if (wake) resume(childDrain);
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

    synchronized boolean beginDirectDispatch() {
        if (state != State.RUNNABLE || queued) {
            return false;
        }
        state = State.RUNNING;
        return true;
    }

    void runContinuation() {
        Object deferredFailure;
        Object deferredCompletion;
        boolean cancelBeforeFirstOrdinaryInstruction;
        synchronized (this) {
            deferredFailure = failure;
            deferredCompletion = pendingCompletion;
            cancelBeforeFirstOrdinaryInstruction = !continuationStarted && cancellationRequested;
            if (!cancelBeforeFirstOrdinaryInstruction) {
                // This is the semantic first-execution boundary. Once crossed, later cancellation
                // cannot preempt arbitrary non-suspending ordinary code.
                continuationStarted = true;
            }
        }
        if (deferredFailure != null) { finalizeFailure(deferredFailure); return; }
        if (deferredCompletion != null) { complete(deferredCompletion); return; }
        if (cancelBeforeFirstOrdinaryInstruction) {
            if (!observeCancellation()) {
                throw new IllegalStateException("pre-start cancellation was not observable");
            }
            return;
        }
        continuation.resume(this);
    }

    public void executeAction(java.util.function.Supplier<Object> action) {
        Objects.requireNonNull(action, "action");
        evaluatorContinuation.beginSegment();
        try {
            complete(action.get());
        } catch (ProtosEvaluatorSuspension suspended) {
            // suspension already changed task state
        } catch (ProtosTaskCancellationException cancelled) {
            // cancellation already terminalized task
        } catch (ProtosSignalException signalled) {
            fail(signalled.error());
        } finally {
            evaluatorContinuation.endSegment();
        }
    }

    public void detachFromParent() {
        ProtosTask previous;
        synchronized (this) { previous = parent; parent = null; }
        if (previous != null) previous.removeChild(this);
    }

    public boolean suspend(WaitDependency dependency) {
        Objects.requireNonNull(dependency, "dependency");
        boolean cancellationWake;
        synchronized (this) {
            requireState(State.RUNNING, "suspend");
            if (cancellationRequested) {
                state = State.RUNNABLE;
                waitDependency = null;
                cancellationWake = true;
            } else if (dependency.isReady()) {
                return false;
            } else {
                state = State.SUSPENDED;
                waitDependency = dependency;
                cancellationWake = false;
            }
        }
        if (cancellationWake) {
            owner.enqueue(this);
        }
        return !cancellationWake;
    }

    public boolean resume(WaitDependency dependency) {
        Objects.requireNonNull(dependency, "dependency");
        synchronized (this) {
            if (state != State.SUSPENDED || waitDependency != dependency) {
                return false;
            }
            waitDependency = null;
            resumedDependency = dependency;
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
        WaitDependency cancelledWait = null;
        synchronized (this) {
            if (isTerminal()) {
                return false;
            }
            if (cancellationRequested) {
                return false;
            }
            cancellationRequested = true;
            resumedDependency = null;
            if (state == State.SUSPENDED) {
                cancelledWait = waitDependency;
                waitDependency = null;
                state = State.RUNNABLE;
                enqueue = true;
            }
        }
        if (cancelledWait != null) {
            cancelledWait.waitingTaskCancelled(this);
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
        java.util.Set<ProtosTask> cancelChildren;
        synchronized (this) {
            if (!cancellationRequested || isTerminal()) return false;
            if (state != State.RUNNING) throw new IllegalStateException("cancellation can be observed only by running task");
            cancelChildren = Set.copyOf(children);
            if (!cancelChildren.isEmpty()) {
                state = State.SUSPENDED;
                waitDependency = childDrain;
            } else {
                state = State.CANCELLED;
            }
        }
        for (ProtosTask child : cancelChildren) child.requestCancellation();
        if (!cancelChildren.isEmpty()) return true;
        owner.terminal(this);
        terminalizeAssociatedFuture(State.CANCELLED, null);
        return true;
    }

    public void complete(Object value) {
        Object completed;
        synchronized (this) {
            requireState(State.RUNNING, "complete");
            if (!children.isEmpty()) {
                pendingCompletion = value;
                state = State.SUSPENDED;
                waitDependency = childDrain;
                return;
            }
            state = State.COMPLETED;
            completed = pendingCompletion != null ? pendingCompletion : value;
            pendingCompletion = null;
            result = completed;
        }
        owner.terminal(this);
        terminalizeAssociatedFuture(State.COMPLETED, completed);
    }

    public void fail(Object error) {
        Object checked = Objects.requireNonNull(error, "error");
        java.util.Set<ProtosTask> cancelChildren;
        synchronized (this) {
            requireState(State.RUNNING, "fail");
            cancelChildren = Set.copyOf(children);
            if (!cancelChildren.isEmpty()) {
                failure = checked;
                state = State.SUSPENDED;
                waitDependency = childDrain;
            } else {
                state = State.FAILED;
                failure = checked;
            }
        }
        for (ProtosTask child : cancelChildren) child.requestCancellation();
        if (!cancelChildren.isEmpty()) return;
        finalizeFailure(checked);
    }

    private void finalizeFailure(Object error) {
        synchronized (this) {
            if (state == State.RUNNING) state = State.FAILED;
            failure = error;
        }
        owner.terminal(this);
        terminalizeAssociatedFuture(State.FAILED, error);
    }

    private void terminalizeAssociatedFuture(State terminal, Object outcome) {
        if (!(associatedFuture instanceof ProtosFutureValue future)) return;
        switch (terminal) {
            case COMPLETED -> future.resolve(outcome, future.producerActivation());
            case FAILED -> future.fail((ProtosObjectValue) outcome);
            case CANCELLED -> future.cancelTerminal();
            default -> { }
        }
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
