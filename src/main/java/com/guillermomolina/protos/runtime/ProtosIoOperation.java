/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.runtime;

import java.util.Objects;

/** Internal producer-side state for one asynchronous I/O operation. Commitment is not Future state. */
public final class ProtosIoOperation {
    enum Phase { UNCOMMITTED, COMMITTED, TERMINAL }

    private final ProtosIoLifecycle lifecycle;
    private final ProtosActivation origin;
    private final ProtosFutureValue future;
    private Phase phase = Phase.UNCOMMITTED;
    private boolean cancellationRequested;
    private Runnable cancellationHandler;

    ProtosIoOperation(ProtosIoLifecycle lifecycle, ProtosActivation origin, ProtosFutureValue future) {
        this.lifecycle=Objects.requireNonNull(lifecycle,"lifecycle");
        this.origin=Objects.requireNonNull(origin,"origin");
        this.future=Objects.requireNonNull(future,"future");
        future.attachCancellationProducer(this::requestCancellation);
        origin.executionDomain().registerActorIoOperation(this);
    }

    public ProtosFutureValue future() { return future; }
    public ProtosActivation origin() { return origin; }
    public boolean committed() { synchronized(lifecycle) { return phase == Phase.COMMITTED; } }
    public boolean terminal() { synchronized(lifecycle) { return phase == Phase.TERMINAL; } }

    /** Crosses this operation's irreversible semantic commitment boundary exactly once. */
    public boolean commit() {
        synchronized(lifecycle) {
            if (phase != Phase.UNCOMMITTED || cancellationRequested || !lifecycle.isOpenLocked()) return false;
            phase=Phase.COMMITTED;
            return true;
        }
    }

    /** Installs the backend cancellation hook; a request that won the registration race is delivered immediately. */
    public void onCancellation(Runnable handler) {
        Objects.requireNonNull(handler,"handler");
        boolean call;
        synchronized(lifecycle) {
            if (cancellationHandler != null) throw new IllegalStateException("cancellation handler already installed");
            cancellationHandler=handler; call=cancellationRequested;
        }
        if (call) handler.run();
    }

    /** Producer-visible cancellation request. Pre-commit cancellation wins; post-commit cancellation cannot rewrite outcome. */
    public boolean requestCancellation() {
        boolean cancel=false; Runnable handler;
        synchronized(lifecycle) {
            if (phase == Phase.TERMINAL || cancellationRequested) return false;
            cancellationRequested=true;
            handler=cancellationHandler;
            if (phase == Phase.UNCOMMITTED) { phase=Phase.TERMINAL; cancel=true; }
        }
        if (handler != null) handler.run();
        if (cancel) {
            future.cancelTerminal();
            finishTerminal();
        }
        return true;
    }

    public boolean resolve(Object value) {
        Objects.requireNonNull(value,"value");
        synchronized(lifecycle) {
            if (phase != Phase.COMMITTED) return false;
            phase=Phase.TERMINAL;
        }
        boolean won=future.resolve(value,origin);
        finishTerminal();
        return won;
    }

    /** Directional lifecycle cutover may commit a terminal result for an otherwise uncommitted operation. */
    public boolean resolveAtLifecycleCutover(Object value) {
        Objects.requireNonNull(value,"value");
        synchronized(lifecycle) {
            if (phase != Phase.UNCOMMITTED) return false;
            phase=Phase.TERMINAL;
        }
        boolean won=future.resolve(value,origin);
        finishTerminal();
        return won;
    }

    public boolean fail(ProtosObjectValue error) {
        Objects.requireNonNull(error,"error");
        synchronized(lifecycle) {
            if (phase == Phase.TERMINAL) return false;
            phase=Phase.TERMINAL;
        }
        boolean won=future.fail(error);
        finishTerminal();
        return won;
    }

    ProtosObjectValue closeCutoverLocked() {
        if (phase != Phase.UNCOMMITTED) return null;
        phase=Phase.TERMINAL;
        return ProtosCoreErrors.newOccurrence(origin,ProtosCoreErrors.StandardError.I_O_LIFECYCLE_ERROR);
    }

    Runnable closeCutoverCancellationHandlerLocked() {
        return cancellationHandler;
    }

    void failAtCloseCutover(ProtosObjectValue error) {
        future.fail(error);
        finishTerminal();
    }

    private void finishTerminal() {
        origin.executionDomain().terminalActorIoOperation(this);
        lifecycle.operationTerminal(this);
    }
}
