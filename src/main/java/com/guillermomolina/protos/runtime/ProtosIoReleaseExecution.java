/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.runtime;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Private PLAT030 execution owner for one lifecycle release C-prime extent.
 *
 * <p>This is deliberately not a Task, Future or ordinary {@link ProtosIoOperation}.
 * The lifecycle remains the semantic close authority; this record owns only
 * release-continuation execution and Actor-domain scheduling state.
 */
public final class ProtosIoReleaseExecution {
    @FunctionalInterface
    public interface DeferredCPrimeExecutionForRuntime {
        void runSegment(ProtosIoReleaseExecution release);
        default void releaseTerminalized(ProtosIoReleaseExecution release) {}
    }

    private final ProtosIoLifecycle lifecycle;
    private final ProtosActivation origin;
    private final ProtosIoLifecycle.ReleaseCompletion completion;
    private final Object stateLock = new Object();
    private DeferredCPrimeExecutionForRuntime deferredCPrimeExecution;
    private boolean deferredCPrimeQueued;
    private boolean deferredCPrimeRunning;
    private boolean deferredCPrimeRescheduleRequested;
    private ProtosActivation deferredCPrimeActivation;
    private ProtosDynamicControlState deferredCPrimeDynamicControlState;
    private boolean terminal;

    ProtosIoReleaseExecution(
            ProtosIoLifecycle lifecycle,
            ProtosActivation origin,
            ProtosIoLifecycle.ReleaseCompletion completion) {
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.origin = Objects.requireNonNull(origin, "origin");
        this.completion = Objects.requireNonNull(completion, "completion");
    }

    public ProtosIoLifecycle lifecycle() { return lifecycle; }
    public ProtosActivation origin() { return origin; }
    public boolean terminal() { synchronized (stateLock) { return terminal; } }

    public ProtosActivation deferredCPrimeActivationForRuntime() {
        synchronized (stateLock) {
            if (terminal) {
                throw new IllegalStateException(
                        "terminal lifecycle release has no deferred C-prime activation");
            }
            if (deferredCPrimeActivation == null) {
                ProtosPrelude prelude =
                        origin.prelude().orElseThrow(
                                () -> new IllegalStateException(
                                        "lifecycle release C-prime requires an owning Core prelude"));
                ProtosActivation created =
                        ProtosActivation.withPreludeAndModuleState(
                                prelude.newExecutionContext(),
                                List.of(),
                                origin.receiver(),
                                prelude,
                                origin.actorModuleState(),
                                origin.currentModuleKey().orElse(null),
                                origin.executionDomain());
                created.attachDeferredCPrimeReleaseForRuntime(this);
                deferredCPrimeActivation = created;
            }
            return deferredCPrimeActivation;
        }
    }

    ProtosDynamicControlState deferredCPrimeDynamicControlStateForRuntime() {
        synchronized (stateLock) {
            if (deferredCPrimeDynamicControlState == null) {
                deferredCPrimeDynamicControlState = new ProtosDynamicControlState();
            }
            return deferredCPrimeDynamicControlState;
        }
    }

    Optional<ProtosDynamicControlState> deferredCPrimeDynamicControlStateIfPresentForRuntime() {
        synchronized (stateLock) {
            return Optional.ofNullable(deferredCPrimeDynamicControlState);
        }
    }

    public void installDeferredCPrimeExecutionForRuntime(
            DeferredCPrimeExecutionForRuntime execution) {
        Objects.requireNonNull(execution, "execution");
        synchronized (stateLock) {
            if (terminal) {
                throw new IllegalStateException(
                        "terminal lifecycle release cannot acquire C-prime execution");
            }
            if (deferredCPrimeExecution != null && deferredCPrimeExecution != execution) {
                throw new IllegalStateException(
                        "lifecycle release already owns deferred C-prime execution");
            }
            deferredCPrimeExecution = execution;
        }
    }

    public boolean requestDeferredCPrimeRunForRuntime() {
        boolean enqueue = false;
        boolean accepted;
        synchronized (stateLock) {
            if (terminal) return false;
            if (deferredCPrimeExecution == null) {
                throw new IllegalStateException(
                        "lifecycle release has no deferred C-prime execution driver");
            }
            if (deferredCPrimeQueued) return false;
            if (deferredCPrimeRunning) {
                accepted = !deferredCPrimeRescheduleRequested;
                deferredCPrimeRescheduleRequested = true;
            } else {
                deferredCPrimeQueued = true;
                enqueue = true;
                accepted = true;
            }
        }
        if (enqueue) origin.executionDomain().enqueueActorIoReleaseForRuntime(this);
        return accepted;
    }

    boolean beginDeferredCPrimeDispatchForRuntime() {
        synchronized (stateLock) {
            if (!deferredCPrimeQueued) return false;
            deferredCPrimeQueued = false;
            if (terminal) return false;
            if (deferredCPrimeRunning) {
                throw new IllegalStateException(
                        "lifecycle release C-prime execution is already running");
            }
            if (deferredCPrimeExecution == null) {
                throw new IllegalStateException(
                        "queued lifecycle release lost its C-prime execution driver");
            }
            deferredCPrimeRunning = true;
            return true;
        }
    }

    void runDeferredCPrimeSegmentForRuntime() {
        DeferredCPrimeExecutionForRuntime execution;
        synchronized (stateLock) {
            if (!deferredCPrimeRunning) {
                throw new IllegalStateException(
                        "lifecycle release C-prime segment is not running");
            }
            execution = Objects.requireNonNull(
                    deferredCPrimeExecution,
                    "running lifecycle release lost its C-prime execution driver");
        }
        boolean requeue = false;
        try {
            execution.runSegment(this);
        } finally {
            synchronized (stateLock) {
                if (!deferredCPrimeRunning) {
                    throw new IllegalStateException(
                            "lifecycle release C-prime running state was corrupted");
                }
                deferredCPrimeRunning = false;
                if (!terminal && deferredCPrimeRescheduleRequested) {
                    deferredCPrimeRescheduleRequested = false;
                    deferredCPrimeQueued = true;
                    requeue = true;
                } else {
                    deferredCPrimeRescheduleRequested = false;
                    if (terminal) {
                        deferredCPrimeExecution = null;
                        deferredCPrimeActivation = null;
                        deferredCPrimeDynamicControlState = null;
                    }
                }
            }
            if (requeue) origin.executionDomain().enqueueActorIoReleaseForRuntime(this);
        }
    }

    public boolean succeed() { return finish(null); }
    public boolean fail(ProtosObjectValue error) {
        return finish(Objects.requireNonNull(error, "error"));
    }

    private boolean finish(ProtosObjectValue error) {
        DeferredCPrimeExecutionForRuntime terminalizedExecution;
        synchronized (stateLock) {
            if (terminal) return false;
            terminal = true;
            deferredCPrimeQueued = false;
            deferredCPrimeRescheduleRequested = false;
            terminalizedExecution = deferredCPrimeExecution;
            if (!deferredCPrimeRunning) {
                deferredCPrimeExecution = null;
                deferredCPrimeActivation = null;
                deferredCPrimeDynamicControlState = null;
            }
        }
        if (terminalizedExecution != null) terminalizedExecution.releaseTerminalized(this);
        try {
            if (error == null) completion.succeeded();
            else completion.failed(error);
        } finally {
            lifecycle.releaseExecutionTerminalForRuntime(this);
            origin.executionDomain().terminalActorIoReleaseForRuntime(this);
        }
        return true;
    }
}
