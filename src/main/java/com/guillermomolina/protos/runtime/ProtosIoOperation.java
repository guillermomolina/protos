/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.runtime;

import java.util.Objects;

/** Internal producer-side state for one asynchronous I/O operation. Commitment is not Future state. */
public final class ProtosIoOperation {
    /**
     * PLAT029 private non-Task execution hook. The concrete driver owns C-prime-specific
     * mechanics; this operation owns only the driver reference and scheduling state.
     */
    @FunctionalInterface
    public interface DeferredCPrimeExecutionForRuntime {
        void runSegment(ProtosIoOperation operation);

        /** Releases any retained C-prime/dependency state after operation terminality. */
        default void operationTerminalized(ProtosIoOperation operation) {}
    }

    enum Phase { UNCOMMITTED, ATTEMPTING_FIRST_EFFECT, COMMITTED, TERMINAL }

    private enum DeferredCutover { NONE, CANCELLATION, CLOSE }

    record CloseCutoverAction(ProtosObjectValue error, Runnable cancellationHandler) {}

    private final ProtosIoLifecycle lifecycle;
    private final ProtosActivation origin;
    private final ProtosFutureValue future;
    private Phase phase = Phase.UNCOMMITTED;
    private DeferredCutover deferredCutover = DeferredCutover.NONE;
    private ProtosObjectValue deferredCloseError;
    private boolean cancellationRequested;
    private Runnable cancellationHandler;
    private final Object deferredCPrimeLock = new Object();
    private DeferredCPrimeExecutionForRuntime deferredCPrimeExecution;
    private boolean deferredCPrimeQueued;
    private boolean deferredCPrimeRunning;
    private boolean deferredCPrimeRescheduleRequested;
    private ProtosActivation deferredCPrimeActivation;
    private ProtosDynamicControlState deferredCPrimeDynamicControlState;

    ProtosIoOperation(ProtosIoLifecycle lifecycle, ProtosActivation origin, ProtosFutureValue future) {
        this.lifecycle=Objects.requireNonNull(lifecycle,"lifecycle");
        this.origin=Objects.requireNonNull(origin,"origin");
        this.future=Objects.requireNonNull(future,"future");
        future.attachCancellationProducer(this::requestCancellation);
        origin.executionDomain().registerActorIoOperation(this);
    }

    public ProtosFutureValue future() { return future; }
    public ProtosActivation origin() { return origin; }

    /**
     * Returns the private non-Task caller activation for this operation-owned C-prime execution.
     * It owns a fresh internal execution Context while sharing the origin Actor/module/Prelude
     * authority; it never copies origin Task identity or caller lexical activation state.
     */
    public ProtosActivation deferredCPrimeActivationForRuntime() {
        synchronized (deferredCPrimeLock) {
            if (terminal()) {
                throw new IllegalStateException(
                        "terminal I/O operation has no deferred C-prime activation");
            }
            if (deferredCPrimeActivation == null) {
                ProtosPrelude prelude =
                        origin.prelude()
                                .orElseThrow(
                                        () ->
                                                new IllegalStateException(
                                                        "deferred I/O C-prime execution requires an owning Core prelude"));
                ProtosActivation created =
                        ProtosActivation.withPreludeAndModuleState(
                                prelude.newExecutionContext(),
                                java.util.List.of(),
                                origin.receiver(),
                                prelude,
                                origin.actorModuleState(),
                                origin.currentModuleKey().orElse(null),
                                origin.executionDomain());
                created.attachDeferredCPrimeOperationForRuntime(this);
                deferredCPrimeActivation = created;
            }
            return deferredCPrimeActivation;
        }
    }

    ProtosDynamicControlState deferredCPrimeDynamicControlStateForRuntime() {
        synchronized (deferredCPrimeLock) {
            if (deferredCPrimeDynamicControlState == null) {
                deferredCPrimeDynamicControlState = new ProtosDynamicControlState();
            }
            return deferredCPrimeDynamicControlState;
        }
    }

    java.util.Optional<ProtosDynamicControlState>
            deferredCPrimeDynamicControlStateIfPresentForRuntime() {
        synchronized (deferredCPrimeLock) {
            return java.util.Optional.ofNullable(deferredCPrimeDynamicControlState);
        }
    }

    public boolean committed() { synchronized(lifecycle) { return phase == Phase.COMMITTED; } }
    public boolean terminal() { synchronized(lifecycle) { return phase == Phase.TERMINAL; } }

    /** Installs exactly one PLAT029 operation-owned deferred C-prime driver. */
    public void installDeferredCPrimeExecutionForRuntime(
            DeferredCPrimeExecutionForRuntime execution) {
        Objects.requireNonNull(execution, "execution");
        synchronized (deferredCPrimeLock) {
            if (terminal()) {
                throw new IllegalStateException(
                        "terminal I/O operation cannot acquire deferred C-prime execution");
            }
            if (deferredCPrimeExecution != null && deferredCPrimeExecution != execution) {
                throw new IllegalStateException(
                        "I/O operation already owns deferred C-prime execution");
            }
            deferredCPrimeExecution = execution;
        }
    }

    /**
     * Publishes Actor-local readiness for the operation without executing guest work on the
     * caller thread. Multiple requests while queued coalesce; one request while running becomes
     * exactly one later Actor-domain segment.
     */
    public boolean requestDeferredCPrimeRunForRuntime() {
        boolean enqueue = false;
        boolean accepted;
        synchronized (deferredCPrimeLock) {
            if (terminal()) {
                return false;
            }
            if (deferredCPrimeExecution == null) {
                throw new IllegalStateException(
                        "I/O operation has no deferred C-prime execution driver");
            }
            if (deferredCPrimeQueued) {
                return false;
            }
            if (deferredCPrimeRunning) {
                accepted = !deferredCPrimeRescheduleRequested;
                deferredCPrimeRescheduleRequested = true;
            } else {
                deferredCPrimeQueued = true;
                enqueue = true;
                accepted = true;
            }
        }
        if (enqueue) {
            origin.executionDomain().enqueueActorIoOperationForRuntime(this);
        }
        return accepted;
    }

    boolean beginDeferredCPrimeDispatchForRuntime() {
        synchronized (deferredCPrimeLock) {
            if (!deferredCPrimeQueued) {
                return false;
            }
            deferredCPrimeQueued = false;
            if (terminal()) {
                return false;
            }
            if (deferredCPrimeRunning) {
                throw new IllegalStateException(
                        "I/O operation deferred C-prime execution is already running");
            }
            if (deferredCPrimeExecution == null) {
                throw new IllegalStateException(
                        "queued I/O operation lost deferred C-prime execution driver");
            }
            deferredCPrimeRunning = true;
            return true;
        }
    }

    void runDeferredCPrimeSegmentForRuntime() {
        DeferredCPrimeExecutionForRuntime execution;
        synchronized (deferredCPrimeLock) {
            if (!deferredCPrimeRunning) {
                throw new IllegalStateException(
                        "I/O operation deferred C-prime segment is not running");
            }
            execution = Objects.requireNonNull(
                    deferredCPrimeExecution,
                    "running I/O operation lost deferred C-prime execution driver");
        }

        boolean requeue = false;
        try {
            execution.runSegment(this);
        } finally {
            synchronized (deferredCPrimeLock) {
                if (!deferredCPrimeRunning) {
                    throw new IllegalStateException(
                            "I/O operation deferred C-prime running state was corrupted");
                }
                deferredCPrimeRunning = false;
                if (!terminal() && deferredCPrimeRescheduleRequested) {
                    deferredCPrimeRescheduleRequested = false;
                    deferredCPrimeQueued = true;
                    requeue = true;
                } else {
                    deferredCPrimeRescheduleRequested = false;
                    if (terminal()) {
                        deferredCPrimeExecution = null;
                        deferredCPrimeActivation = null;
                        deferredCPrimeDynamicControlState = null;
                    }
                }
            }
            if (requeue) {
                origin.executionDomain().enqueueActorIoOperationForRuntime(this);
            }
        }
    }

    boolean deferredCPrimeQueuedForTesting() {
        synchronized (deferredCPrimeLock) {
            return deferredCPrimeQueued;
        }
    }

    boolean deferredCPrimeRunningForTesting() {
        synchronized (deferredCPrimeLock) {
            return deferredCPrimeRunning;
        }
    }

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

    /**
     * D117 settlement for a delegated first-effect attempt whose lower operation failed while
     * its standard contract permits an irreversible effect but does not expose whether one
     * occurred. Competing zero-effect cancellation/close cannot replace this failure.
     */
    public boolean failFirstEffectAttemptWithUnknownEffect(ProtosObjectValue error) {
        Objects.requireNonNull(error, "error");
        synchronized(lifecycle) {
            if (phase != Phase.ATTEMPTING_FIRST_EFFECT) {
                throw new IllegalStateException("no first-effect attempt is in flight");
            }
            phase = Phase.TERMINAL;
            deferredCutover = DeferredCutover.NONE;
            deferredCloseError = null;
        }
        boolean won = future.fail(error);
        finishTerminal();
        return won;
    }

    boolean firstEffectAttemptInFlight() {
        synchronized(lifecycle) {
            return phase == Phase.ATTEMPTING_FIRST_EFFECT;
        }
    }

    boolean backendCancellationRequested() {
        synchronized(lifecycle) {
            return cancellationRequested
                    || deferredCutover == DeferredCutover.CLOSE
                    || phase == Phase.TERMINAL;
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

    CloseCutoverAction closeCutoverLocked() {
        if (phase == Phase.UNCOMMITTED) {
            phase=Phase.TERMINAL;
            return new CloseCutoverAction(
                    ProtosCoreErrors.newOccurrence(
                            origin,ProtosCoreErrors.StandardError.I_O_LIFECYCLE_ERROR),
                    cancellationHandler);
        }
        if (phase == Phase.ATTEMPTING_FIRST_EFFECT
                && deferredCutover == DeferredCutover.NONE) {
            deferredCutover=DeferredCutover.CLOSE;
            deferredCloseError=ProtosCoreErrors.newOccurrence(
                    origin,ProtosCoreErrors.StandardError.I_O_LIFECYCLE_ERROR);
            return new CloseCutoverAction(null, cancellationHandler);
        }
        return null;
    }

    void failAtCloseCutover(ProtosObjectValue error) {
        future.fail(error);
        finishTerminal();
    }

    private void finishTerminal() {
        DeferredCPrimeExecutionForRuntime terminalizedExecution;
        synchronized (deferredCPrimeLock) {
            deferredCPrimeQueued = false;
            deferredCPrimeRescheduleRequested = false;
            terminalizedExecution = deferredCPrimeExecution;
            if (!deferredCPrimeRunning) {
                deferredCPrimeExecution = null;
                deferredCPrimeActivation = null;
                deferredCPrimeDynamicControlState = null;
            }
        }
        if (terminalizedExecution != null) {
            terminalizedExecution.operationTerminalized(this);
        }
        origin.executionDomain().terminalActorIoOperation(this);
        lifecycle.operationTerminal(this);
    }
}
