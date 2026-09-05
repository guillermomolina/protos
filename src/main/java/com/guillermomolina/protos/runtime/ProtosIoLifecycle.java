/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.runtime;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Reusable whole-resource Closable lifecycle and admission/commitment cutover machinery. */
public final class ProtosIoLifecycle {
    public enum State { OPEN, CLOSING, CLOSED_SUCCESS, CLOSED_FAILED }

    @FunctionalInterface public interface ReleaseStarter { void start(ReleaseCompletion completion); }
    public interface ReleaseCompletion { void succeeded(); void failed(ProtosObjectValue error); }

    private final ProtosObjectValue receiver;
    private final ProtosObjectValue futurePrototype;
    private final ProtosActorExecutionDomain domain;
    private final ReleaseStarter releaseStarter;
    private final Set<ProtosIoOperation> operations=new LinkedHashSet<>();
    private final List<ProtosFutureValue> closeFollowers=new ArrayList<>();
    private State state=State.OPEN;
    private ProtosObjectValue closeError;
    private ProtosActivation closeActivation;
    private boolean releaseStarted;

    public ProtosIoLifecycle(ProtosObjectValue receiver, ProtosObjectValue futurePrototype,
            ProtosActorExecutionDomain domain, ReleaseStarter releaseStarter) {
        this.receiver=Objects.requireNonNull(receiver,"receiver");
        this.futurePrototype=Objects.requireNonNull(futurePrototype,"futurePrototype");
        this.domain=Objects.requireNonNull(domain,"domain");
        this.releaseStarter=Objects.requireNonNull(releaseStarter,"releaseStarter");
    }

    public synchronized State state() { return state; }
    synchronized boolean isOpenLocked() { return state == State.OPEN; }

    /** Admits ordinary resource work, or returns a fresh lifecycle-failed Future if close already cut over. */
    public ProtosIoOperation beginOperation(ProtosActivation origin) {
        Objects.requireNonNull(origin,"origin");
        if (origin.executionDomain()!=domain) throw new IllegalArgumentException("I/O operation belongs to another Actor domain");
        ProtosFutureValue future=new ProtosFutureValue(futurePrototype,domain);
        ProtosIoOperation operation;
        synchronized(this) {
            if (state != State.OPEN) {
                future.fail(ProtosCoreErrors.newOccurrence(origin,ProtosCoreErrors.StandardError.I_O_LIFECYCLE_ERROR));
                return newRejectedOperation(origin,future);
            }
            operation=new ProtosIoOperation(this,origin,future);
            operations.add(operation);
        }
        return operation;
    }

    private ProtosIoOperation newRejectedOperation(ProtosActivation origin, ProtosFutureValue future) {
        ProtosIoOperation operation=new ProtosIoOperation(this,origin,future);
        synchronized(this) { operation.closeCutoverLocked(); }
        origin.executionDomain().terminalActorIoOperation(operation);
        return operation;
    }

    /** Commits close at invocation, returns a fresh Future, and shares one permanent logical outcome. */
    public ProtosFutureValue close(ProtosActivation activation) {
        Objects.requireNonNull(activation,"activation");
        if (activation.executionDomain()!=domain) throw new IllegalArgumentException("close belongs to another Actor domain");
        ProtosFutureValue follower=new ProtosFutureValue(futurePrototype,domain);
        // Close is committed by invocation; Future.cancel() may stop a waiter but cannot cancel this lifecycle.
        follower.attachCancellationProducer(() -> {});
        List<CloseFailure> cutover=new ArrayList<>();
        boolean start=false;
        synchronized(this) {
            switch(state) {
                case CLOSED_SUCCESS -> { follower.resolve(receiver,activation); return follower; }
                case CLOSED_FAILED -> { follower.fail(closeError); return follower; }
                case CLOSING -> { closeFollowers.add(follower); return follower; }
                case OPEN -> {
                    state=State.CLOSING;
                    closeActivation=activation;
                    closeFollowers.add(follower);
                    for (ProtosIoOperation operation : List.copyOf(operations)) {
                        ProtosObjectValue error=operation.closeCutoverLocked();
                        if (error != null) {
                            cutover.add(
                                    new CloseFailure(
                                            operation,
                                            error,
                                            operation.closeCutoverCancellationHandlerLocked()));
                        }
                    }
                    start=readyToReleaseLocked();
                }
            }
        }
        for (CloseFailure failure : cutover) {
            if (failure.cancellationHandler != null) {
                try {
                    failure.cancellationHandler.run();
                } catch (RuntimeException ignored) {
                    // Backend cancellation is best-effort machinery after the semantic cutover.
                    // It cannot rewrite the required IOLifecycleError terminal outcome.
                }
            }
            failure.operation.failAtCloseCutover(failure.error);
        }
        if (start) startRelease();
        else maybeStartRelease();
        return follower;
    }

    void operationTerminal(ProtosIoOperation operation) {
        synchronized(this) { operations.remove(operation); }
        maybeStartRelease();
    }

    private void maybeStartRelease() {
        boolean start;
        synchronized(this) { start=readyToReleaseLocked(); }
        if (start) startRelease();
    }

    private boolean readyToReleaseLocked() {
        if (state != State.CLOSING || releaseStarted || !operations.isEmpty()) return false;
        releaseStarted=true;
        return true;
    }

    private void startRelease() {
        try {
            releaseStarter.start(new ReleaseCompletion() {
                @Override public void succeeded() { finishClose(null); }
                @Override public void failed(ProtosObjectValue error) { finishClose(Objects.requireNonNull(error,"error")); }
            });
        } catch (RuntimeException ex) {
            finishClose(ProtosCoreErrors.newOccurrence(closeActivation,ProtosCoreErrors.StandardError.I_O_ERROR));
        }
    }

    private void finishClose(ProtosObjectValue error) {
        List<ProtosFutureValue> followers;
        synchronized(this) {
            if (state != State.CLOSING) return;
            closeError=error;
            state=error==null ? State.CLOSED_SUCCESS : State.CLOSED_FAILED;
            followers=List.copyOf(closeFollowers);
            closeFollowers.clear();
        }
        ProtosActivation activation;
        synchronized(this) { activation=closeActivation; }
        for (ProtosFutureValue follower : followers) {
            if (error==null) follower.resolve(receiver, activation);
            else follower.fail(error);
        }
    }

    private record CloseFailure(
            ProtosIoOperation operation,
            ProtosObjectValue error,
            Runnable cancellationHandler) {}
}
