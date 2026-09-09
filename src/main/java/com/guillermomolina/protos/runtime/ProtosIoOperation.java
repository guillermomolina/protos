/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.runtime;

import java.util.Objects;

/** Internal producer-side state for one asynchronous I/O operation. Commitment is not Future state. */
public final class ProtosIoOperation {
    enum Phase { UNCOMMITTED, ATTEMPTING_FIRST_EFFECT, COMMITTED, TERMINAL }

    private enum DeferredCutover { NONE, CANCELLATION, CLOSE }

    private final ProtosIoLifecycle lifecycle;
    private final ProtosActivation origin;
    private final ProtosFutureValue future;
    private Phase phase = Phase.UNCOMMITTED;
    private DeferredCutover deferredCutover = DeferredCutover.NONE;
    private ProtosObjectValue deferredCloseError;
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

    /**
     * Starts one PLAT009 first-effect attempt without committing the operation.
     *
     * <p>The caller must settle every successful begin with
     * {@link #finishFirstEffectAttempt(boolean)} after the host aftermath is known.
     */
    public boolean beginFirstEffectAttempt() {
        synchronized(lifecycle) {
            if (phase != Phase.UNCOMMITTED
                    || cancellationRequested
                    || !lifecycle.isOpenLocked()) {
                return false;
            }
            phase = Phase.ATTEMPTING_FIRST_EFFECT;
            return true;
        }
    }

    /**
     * Settles the in-flight PLAT009 attempt.
     *
     * @param contributed whether the attempt produced the first positive irreversible effect
     * @return true when backend progress may continue; false when a deferred zero-effect cutover
     *     terminalized the operation
     */
    public boolean finishFirstEffectAttempt(boolean contributed) {
        boolean cancel = false;
        ProtosObjectValue closeError = null;
        synchronized(lifecycle) {
            if (phase != Phase.ATTEMPTING_FIRST_EFFECT) {
                throw new IllegalStateException("no first-effect attempt is in flight");
            }

            if (contributed) {
                phase = Phase.COMMITTED;
                deferredCutover = DeferredCutover.NONE;
                deferredCloseError = null;
                return true;
            }

            phase = Phase.UNCOMMITTED;
            if (deferredCutover == DeferredCutover.CANCELLATION) {
                phase = Phase.TERMINAL;
                cancel = true;
            } else if (deferredCutover == DeferredCutover.CLOSE) {
                phase = Phase.TERMINAL;
                closeError = deferredCloseError;
            }
            deferredCutover = DeferredCutover.NONE;
            deferredCloseError = null;
        }

        if (cancel) {
            future.cancelTerminal();
            finishTerminal();
            return false;
        }
        if (closeError != null) {
            future.fail(closeError);
            finishTerminal();
            return false;
        }
        return true;
    }

    boolean firstEffectAttemptInFlight() {
        synchronized(lifecycle) {
            return phase == Phase.ATTEMPTING_FIRST_EFFECT;
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
            if (phase == Phase.ATTEMPTING_FIRST_EFFECT
                    && deferredCutover == DeferredCutover.CLOSE) {
                return false;
            }
            cancellationRequested=true;
            handler=cancellationHandler;
            if (phase == Phase.UNCOMMITTED) {
                phase=Phase.TERMINAL;
                cancel=true;
            } else if (phase == Phase.ATTEMPTING_FIRST_EFFECT) {
                deferredCutover=DeferredCutover.CANCELLATION;
            }
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
            if (phase == Phase.TERMINAL || phase == Phase.ATTEMPTING_FIRST_EFFECT) return false;
            phase=Phase.TERMINAL;
        }
        boolean won=future.fail(error);
        finishTerminal();
        return won;
    }

    ProtosObjectValue closeCutoverLocked() {
        if (phase == Phase.UNCOMMITTED) {
            phase=Phase.TERMINAL;
            return ProtosCoreErrors.newOccurrence(
                    origin,ProtosCoreErrors.StandardError.I_O_LIFECYCLE_ERROR);
        }
        if (phase == Phase.ATTEMPTING_FIRST_EFFECT
                && deferredCutover == DeferredCutover.NONE) {
            deferredCutover=DeferredCutover.CLOSE;
            deferredCloseError=ProtosCoreErrors.newOccurrence(
                    origin,ProtosCoreErrors.StandardError.I_O_LIFECYCLE_ERROR);
        }
        return null;
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
