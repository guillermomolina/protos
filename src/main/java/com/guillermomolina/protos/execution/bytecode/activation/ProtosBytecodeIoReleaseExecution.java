/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosIoReleaseExecution;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import java.util.Objects;
import java.util.function.BiConsumer;

/** Private PLAT030 Bytecode DSL driver for one lifecycle-release execution record. */
final class ProtosBytecodeIoReleaseExecution
        implements ProtosIoReleaseExecution.DeferredCPrimeExecutionForRuntime {
    private final ProtosIoReleaseExecution release;
    private final CallTarget target;
    private final ProtosActivation activation;
    private final Object entryState;
    private final boolean statefulEntry;
    private final BiConsumer<ProtosIoReleaseExecution, Object> completion;
    private final BiConsumer<ProtosIoReleaseExecution, ProtosObjectValue> failure;
    private final Object stateLock = new Object();
    private boolean started;
    private boolean finished;
    private ContinuationResult continuation;
    private ProtosIoReleaseSuspension suspension;

    private ProtosBytecodeIoReleaseExecution(
            ProtosIoReleaseExecution release,
            CallTarget target,
            ProtosActivation activation,
            Object entryState,
            boolean statefulEntry,
            BiConsumer<ProtosIoReleaseExecution, Object> completion,
            BiConsumer<ProtosIoReleaseExecution, ProtosObjectValue> failure) {
        this.release = Objects.requireNonNull(release, "release");
        this.target = Objects.requireNonNull(target, "target");
        this.activation = Objects.requireNonNull(activation, "activation");
        this.entryState = entryState;
        this.statefulEntry = statefulEntry;
        this.completion = Objects.requireNonNull(completion, "completion");
        this.failure = Objects.requireNonNull(failure, "failure");
        if (activation.task().isPresent()) {
            throw new IllegalArgumentException(
                    "release-owned C-prime execution cannot use a Task-owned activation");
        }
        if (activation.deferredCPrimeReleaseForRuntime().orElse(null) != release) {
            throw new IllegalArgumentException(
                    "release-owned C-prime activation belongs to another execution owner");
        }
        if (activation.executionDomain() != release.origin().executionDomain()) {
            throw new IllegalArgumentException(
                    "release-owned C-prime activation belongs to another Actor domain");
        }
    }

    static void installAndScheduleWithEntryState(
            ProtosIoReleaseExecution release,
            CallTarget target,
            ProtosActivation activation,
            Object entryState,
            BiConsumer<ProtosIoReleaseExecution, Object> completion,
            BiConsumer<ProtosIoReleaseExecution, ProtosObjectValue> failure) {
        ProtosBytecodeIoReleaseExecution execution =
                new ProtosBytecodeIoReleaseExecution(
                        release, target, activation,
                        Objects.requireNonNull(entryState, "entryState"), true,
                        completion, failure);
        release.installDeferredCPrimeExecutionForRuntime(execution);
        if (!release.requestDeferredCPrimeRunForRuntime()) {
            throw new IllegalStateException(
                    "fresh lifecycle-release C-prime execution could not become runnable");
        }
    }

    @Override
    public void runSegment(ProtosIoReleaseExecution scheduledRelease) {
        if (scheduledRelease != release) {
            throw new IllegalArgumentException(
                    "release-owned C-prime driver dispatched for another lifecycle release");
        }
        ContinuationResult resumeContinuation = null;
        ProtosIoReleaseSuspension resumeSuspension = null;
        boolean initial = false;
        synchronized (stateLock) {
            if (finished) return;
            if (continuation != null) {
                if (!suspension.dependency().isReady()) return;
                resumeContinuation = continuation;
                resumeSuspension = suspension;
                continuation = null;
                suspension = null;
            } else {
                if (started) {
                    throw new IllegalStateException(
                            "release C-prime driver has no retained continuation to resume");
                }
                started = true;
                initial = true;
            }
        }
        try {
            Object outcome;
            if (initial) {
                outcome = Objects.requireNonNull(
                        statefulEntry ? target.call(activation, entryState) : target.call(activation),
                        "release-owned C-prime entry returned null");
            } else {
                resumeSuspension.releaseWait();
                outcome = Objects.requireNonNull(
                        resumeContinuation.continueWith(ProtosNullValue.INSTANCE),
                        "release-owned C-prime continuation returned null");
            }
            driveOutcome(outcome);
        } catch (ProtosBytecodeControlTransferException bridged) {
            throw bridged.transfer();
        } catch (ProtosSignalException signalled) {
            finishFailure(signalled.error());
        }
    }

    @Override
    public void releaseTerminalized(ProtosIoReleaseExecution terminalRelease) {
        if (terminalRelease != release) {
            throw new IllegalArgumentException(
                    "release-owned C-prime terminal notification belongs to another release");
        }
        ProtosIoReleaseSuspension retained;
        synchronized (stateLock) {
            finished = true;
            continuation = null;
            retained = suspension;
            suspension = null;
        }
        if (retained != null) retained.releaseWait();
    }

    private void driveOutcome(Object initialOutcome) {
        Object outcome = initialOutcome;
        while (true) {
            if (!(outcome instanceof ContinuationResult next)) {
                finishCompletion(outcome);
                return;
            }
            ProtosIoReleaseSuspension leaf = releaseSuspensionLeaf(next);
            if (leaf.release() != release) {
                leaf.releaseWait();
                throw new IllegalStateException(
                        "C-prime continuation yielded a suspension owned by another lifecycle release");
            }
            if (leaf.dependency().isReady()) {
                leaf.releaseWait();
                outcome = Objects.requireNonNull(
                        next.continueWith(ProtosNullValue.INSTANCE),
                        "ready release-owned C-prime continuation returned null");
                continue;
            }
            boolean discard;
            synchronized (stateLock) {
                discard = finished;
                if (!discard) {
                    if (continuation != null || suspension != null) {
                        throw new IllegalStateException(
                                "release C-prime driver already retains a continuation");
                    }
                    continuation = next;
                    suspension = leaf;
                }
            }
            if (discard) {
                leaf.releaseWait();
                return;
            }
            leaf.retainWait();
            if (leaf.dependency().isReady()) release.requestDeferredCPrimeRunForRuntime();
            return;
        }
    }

    private void finishCompletion(Object value) {
        synchronized (stateLock) {
            if (finished) return;
            finished = true;
        }
        completion.accept(release, Objects.requireNonNull(value, "release C-prime completion value"));
        if (!release.terminal()) {
            throw new IllegalStateException(
                    "release C-prime completion callback did not terminalize the lifecycle release");
        }
    }

    private void finishFailure(ProtosObjectValue error) {
        synchronized (stateLock) {
            if (finished) return;
            finished = true;
        }
        failure.accept(release, Objects.requireNonNull(error, "release C-prime failure Error"));
        if (!release.terminal()) {
            throw new IllegalStateException(
                    "release C-prime failure callback did not terminalize the lifecycle release");
        }
    }

    private static ProtosIoReleaseSuspension releaseSuspensionLeaf(ContinuationResult top) {
        Object current = top;
        while (current instanceof ContinuationResult nested) current = nested.getResult();
        if (!(current instanceof ProtosIoReleaseSuspension suspension)) {
            throw new UnsupportedOperationException(
                    "PLAT030 release-owned C-prime suspension requires a release-owned suspension leaf");
        }
        return suspension;
    }
}
