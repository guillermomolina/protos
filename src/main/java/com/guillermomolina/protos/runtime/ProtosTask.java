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

    /**
     * Internal lifecycle of the one idempotently recorded cancellation request.
     *
     * <p>This is runtime machinery, not a Protos-visible state model. REQUESTED means the
     * request still awaits a portable observation boundary. UNWINDING means that request has
     * already been observed and is running applicable ensure cleanup and/or draining structured
     * cancellation work. SUPERSEDED means a later cleanup transfer permanently replaced that
     * cancellation. TERMINAL means the recorded request can no longer be observed again because
     * the task has reached a terminal outcome.
     */
    public enum CancellationPhase {
        NONE,
        REQUESTED,
        UNWINDING,
        SUPERSEDED,
        TERMINAL
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
    private boolean cancellationRequestRecorded;
    private CancellationPhase cancellationPhase = CancellationPhase.NONE;
    private boolean continuationStarted;
    private WaitDependency waitDependency;

    /*
     * PLAT019 B-prime suspension publication state. Capture-pending remains
     * physically RUNNING and therefore is intentionally not a guest-visible
     * or public Task.State value.
     */
    private WaitDependency capturePendingDependency;
    private boolean captureWakeRecorded;
    private Continuation publishedSuspensionContinuation;

    private Object result;
    private Object failure;
    private final ProtosEvaluatorContinuation evaluatorContinuation = new ProtosEvaluatorContinuation();
    private WaitDependency resumedDependency;
    private final WaitDependency childDrain = new WaitDependency() {};
    private Object pendingCompletion;
    private ProtosDynamicControlState dynamicControlState;

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

    /**
     * Whether the recorded cancellation request still awaits portable observation.
     *
     * <p>Once observation begins, the same request is no longer pending. Suspension reached
     * while that same cancellation is unwinding through ensure cleanup therefore does not
     * re-deliver it; a genuinely later independent request would require a distinct task.
     */
    public synchronized boolean cancellationRequested() {
        return cancellationPhase == CancellationPhase.REQUESTED;
    }

    public synchronized CancellationPhase cancellationPhase() {
        return cancellationPhase;
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

    /** Internal lazy task-local handler/cleanup state; never inherited by child tasks. */
    public synchronized ProtosDynamicControlState dynamicControlState() {
        if (dynamicControlState == null) {
            dynamicControlState = new ProtosDynamicControlState();
        }
        return dynamicControlState;
    }

    public synchronized Optional<ProtosDynamicControlState> dynamicControlStateIfPresent() {
        return Optional.ofNullable(dynamicControlState);
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
            finishCancellationUnwind();
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
        boolean finishCancellationChildDrain;
        boolean cancelBeforeFirstOrdinaryInstruction;
        synchronized (this) {
            deferredFailure = failure;
            deferredCompletion = pendingCompletion;
            finishCancellationChildDrain =
                    cancellationPhase == CancellationPhase.UNWINDING
                            && resumedDependency == childDrain;
            if (finishCancellationChildDrain) {
                resumedDependency = null;
            }
            cancelBeforeFirstOrdinaryInstruction =
                    !continuationStarted && cancellationPhase == CancellationPhase.REQUESTED;
            if (!cancelBeforeFirstOrdinaryInstruction && !finishCancellationChildDrain) {
                // This is the semantic first-execution boundary. Once crossed, later cancellation
                // cannot preempt arbitrary non-suspending ordinary code.
                continuationStarted = true;
            }
        }
        if (deferredFailure != null) { finalizeFailure(deferredFailure); return; }
        if (deferredCompletion != null) { complete(deferredCompletion); return; }
        if (finishCancellationChildDrain) {
            finishCancellationAfterChildDrain();
            return;
        }
        if (cancelBeforeFirstOrdinaryInstruction) {
            if (!observeCancellation()) {
                throw new IllegalStateException("pre-start cancellation was not observable");
            }
            return;
        }
        Continuation resumeContinuation;
        synchronized (this) {
            resumeContinuation = publishedSuspensionContinuation;
            publishedSuspensionContinuation = null;
        }
        if (resumeContinuation != null) {
            resumeContinuation.resume(this);
        } else {
            continuation.resume(this);
        }
    }

    public void executeAction(java.util.function.Supplier<Object> action) {
        Objects.requireNonNull(action, "action");
        evaluatorContinuation.beginSegment();
        try {
            complete(action.get());
        } catch (ProtosEvaluatorSuspension suspended) {
            // suspension already changed task state
        } catch (ProtosTaskCancellationException cancelled) {
            finishCancellationUnwind();
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

    /**
     * Begins the private PLAT019 capture-pending phase after a wait relationship has been
     * registered.
     *
     * <p>The task remains physically RUNNING. A concurrent dependency wake or cancellation may
     * record its outcome, but neither is allowed to enqueue this task before
     * {@link #publishSuspensionContinuation(WaitDependency, Continuation)} installs the complete
     * top-level logical continuation.
     *
     * @return {@code true} when continuation capture/publication is required; {@code false} when
     *     readiness or an already-recorded cancellation makes suspension unnecessary
     */
    public boolean beginSuspensionCapture(WaitDependency dependency) {
        Objects.requireNonNull(dependency, "dependency");
        boolean detachCancelledWait = false;
        synchronized (this) {
            requireState(State.RUNNING, "begin suspension capture");
            if (capturePendingDependency != null) {
                throw new IllegalStateException("suspension capture is already pending");
            }
            if (publishedSuspensionContinuation != null) {
                throw new IllegalStateException(
                        "cannot begin capture while another published continuation is retained");
            }
            if (cancellationPhase == CancellationPhase.REQUESTED) {
                detachCancelledWait = true;
            } else if (dependency.isReady()) {
                return false;
            } else {
                capturePendingDependency = dependency;
                captureWakeRecorded = false;
                return true;
            }
        }
        /*
         * The caller contract registers the wait relationship before entering capture.
         * Cancellation therefore removes only that relationship; it never cancels the
         * dependency itself.
         */
        if (detachCancelledWait) {
            dependency.waitingTaskCancelled(this);
        }
        return false;
    }

    /**
     * Atomically publishes the complete top-level continuation for one PLAT019 suspension.
     *
     * <p>Publication and the RUNNABLE/SUSPENDED decision occur under the same Task monitor. A
     * wake recorded during capture, a last-moment ready dependency, or cancellation makes the
     * task runnable only after the continuation has become Task-owned. If the dependency remains
     * pending, the task becomes ordinarily SUSPENDED and later {@link #resume(WaitDependency)}
     * enqueues it through the existing Actor-local queue.
     *
     * @return {@code true} when the published task remains suspended; {@code false} when a
     *     ready/cancel signal makes it immediately runnable after publication
     */
    public boolean publishSuspensionContinuation(
            WaitDependency dependency, Continuation continuation) {
        Objects.requireNonNull(dependency, "dependency");
        Objects.requireNonNull(continuation, "continuation");

        boolean enqueue;
        boolean suspended;
        synchronized (this) {
            requireState(State.RUNNING, "publish suspension continuation");
            if (capturePendingDependency != dependency) {
                throw new IllegalStateException(
                        "published continuation does not match the active suspension capture");
            }
            if (publishedSuspensionContinuation != null) {
                throw new IllegalStateException("a suspension continuation is already published");
            }

            boolean cancellationWins = cancellationPhase == CancellationPhase.REQUESTED;
            boolean dependencyReady = captureWakeRecorded || dependency.isReady();

            publishedSuspensionContinuation = continuation;
            capturePendingDependency = null;
            captureWakeRecorded = false;

            if (cancellationWins || dependencyReady) {
                waitDependency = null;
                resumedDependency = cancellationWins ? null : dependency;
                state = State.RUNNABLE;
                enqueue = true;
                suspended = false;
            } else {
                waitDependency = dependency;
                resumedDependency = null;
                state = State.SUSPENDED;
                enqueue = false;
                suspended = true;
            }
        }

        if (enqueue) {
            owner.enqueue(this);
        }
        return suspended;
    }

    public boolean suspend(WaitDependency dependency) {
        Objects.requireNonNull(dependency, "dependency");
        boolean cancellationWake;
        synchronized (this) {
            requireState(State.RUNNING, "suspend");
            if (cancellationPhase == CancellationPhase.REQUESTED) {
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
            /*
             * PLAT019: a wake during capture is remembered but cannot make the task
             * dispatchable until the complete top-level continuation is published.
             */
            if (state == State.RUNNING && capturePendingDependency == dependency) {
                captureWakeRecorded = true;
                return true;
            }
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
            if (cancellationRequestRecorded) {
                return false;
            }
            cancellationRequestRecorded = true;
            cancellationPhase = CancellationPhase.REQUESTED;
            resumedDependency = null;
            if (state == State.RUNNING && capturePendingDependency != null) {
                /*
                 * Detach the registered waiter immediately, but keep capture-pending
                 * ownership until the continuation is published. Publication, not
                 * cancellation, is the first point at which this task may be enqueued.
                 */
                cancelledWait = capturePendingDependency;
            } else if (state == State.SUSPENDED) {
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
        boolean terminalNow;
        synchronized (this) {
            if (cancellationPhase != CancellationPhase.REQUESTED || isTerminal()) {
                return false;
            }
            if (state != State.RUNNING) {
                throw new IllegalStateException(
                        "cancellation can be observed only by running task");
            }

            cancellationPhase = CancellationPhase.UNWINDING;
            cancelChildren = Set.copyOf(children);
            boolean hasEnsureCleanup =
                    dynamicControlState != null
                            && dynamicControlState.hasActiveEnsureFrames();
            if (hasEnsureCleanup) {
                // Cleanup owns the running task first. Existing structured children still receive
                // cancellation below, but they must not force the parent into childDrain before
                // crossed ensure cleanup has had a chance to run or suspend.
                terminalNow = false;
            } else if (!cancelChildren.isEmpty()) {
                state = State.SUSPENDED;
                waitDependency = childDrain;
                terminalNow = false;
            } else {
                cancellationPhase = CancellationPhase.TERMINAL;
                state = State.CANCELLED;
                terminalNow = true;
            }
        }

        for (ProtosTask child : cancelChildren) {
            child.requestCancellation();
        }

        if (terminalNow) {
            publishCancellationTerminal();
        }
        return true;
    }

    /**
     * Begins cancellation unwind for an already-materialized C-prime continuation.
     *
     * <p>Unlike the legacy observation path, this entry deliberately does not
     * terminalize the task or enter childDrain merely because no replay-owned
     * ensure frame is visible. The suspended Bytecode continuation itself owns
     * the active structured cleanup extents and must receive the exact control
     * transfer first. Existing structured children still receive their one
     * idempotent cancellation request immediately; post-cleanup child drainage
     * remains owned by {@link #finishCancellationUnwind()}.
     */
    public boolean beginContinuationCancellationUnwindForRuntime() {
        java.util.Set<ProtosTask> cancelChildren;
        synchronized (this) {
            if (cancellationPhase != CancellationPhase.REQUESTED || isTerminal()) {
                return false;
            }
            requireState(State.RUNNING, "begin continuation cancellation unwind");
            cancellationPhase = CancellationPhase.UNWINDING;
            cancelChildren = Set.copyOf(children);
        }

        for (ProtosTask child : cancelChildren) {
            child.requestCancellation();
        }
        return true;
    }

    /**
     * Completes an already-delivered cancellation after all crossed ensure cleanup has finished.
     *
     * <p>Any structured children still owned at this cutover, including children created while
     * ensure cleanup runs, receive cancellation and are drained before the parent publishes its
     * terminal cancelled Future. Children that cleanup explicitly awaited may already be gone.
     */
    public boolean finishCancellationUnwind() {
        java.util.Set<ProtosTask> cancelChildren = Set.of();
        boolean terminalNow = false;
        synchronized (this) {
            if (cancellationPhase != CancellationPhase.UNWINDING || isTerminal()) {
                return false;
            }

            if (state == State.RUNNING) {
                cancelChildren = Set.copyOf(children);
                if (!cancelChildren.isEmpty()) {
                    state = State.SUSPENDED;
                    waitDependency = childDrain;
                } else {
                    cancellationPhase = CancellationPhase.TERMINAL;
                    state = State.CANCELLED;
                    terminalNow = true;
                }
            } else if (state == State.SUSPENDED && waitDependency == childDrain) {
                return true;
            } else {
                throw new IllegalStateException(
                        "finish cancellation unwind requires running cleanup or child drain");
            }
        }

        for (ProtosTask child : cancelChildren) {
            child.requestCancellation();
        }

        if (terminalNow) {
            publishCancellationTerminal();
        }
        return true;
    }

    /**
     * Records that a later cleanup transfer replaced the delivered cancellation.
     *
     * <p>The original request remains idempotently recorded but can never be delivered again.
     * Returning a suspended child-drain task to RUNNING lets the ordinary later transfer path
     * re-establish its own structured completion/failure drain.
     */
    public synchronized boolean supersedeCancellationUnwind() {
        if (cancellationPhase != CancellationPhase.UNWINDING || isTerminal()) {
            return false;
        }
        cancellationPhase = CancellationPhase.SUPERSEDED;
        if (state == State.SUSPENDED && waitDependency == childDrain) {
            state = State.RUNNING;
            waitDependency = null;
            resumedDependency = null;
        }
        return true;
    }

    /**
     * Completes the final structured-child drain of a cancellation unwind.
     *
     * <p>At this point applicable ensure cleanup has finished normally and every child that
     * remained owned at the post-cleanup cutover has already received a cancellation request.
     */
    private void finishCancellationAfterChildDrain() {
        synchronized (this) {
            if (cancellationPhase != CancellationPhase.UNWINDING) {
                throw new IllegalStateException(
                        "cancellation child drain requires UNWINDING phase");
            }
            requireState(State.RUNNING, "finish cancellation child drain");
            if (!children.isEmpty()) {
                throw new IllegalStateException(
                        "cancellation child drain resumed before children became terminal");
            }
            waitDependency = null;
            cancellationPhase = CancellationPhase.TERMINAL;
            state = State.CANCELLED;
        }
        publishCancellationTerminal();
    }

    private void publishCancellationTerminal() {
        owner.terminal(this);
        terminalizeAssociatedFuture(State.CANCELLED, null);
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
            if (cancellationRequestRecorded) {
                cancellationPhase = CancellationPhase.TERMINAL;
            }
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
                if (cancellationRequestRecorded) {
                    cancellationPhase = CancellationPhase.TERMINAL;
                }
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
            if (cancellationRequestRecorded) {
                cancellationPhase = CancellationPhase.TERMINAL;
            }
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
