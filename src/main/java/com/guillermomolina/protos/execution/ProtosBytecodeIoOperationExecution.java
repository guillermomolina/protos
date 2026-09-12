/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosIoOperation;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import java.util.Objects;
import java.util.function.BiConsumer;

/** Private PLAT029 C-prime driver retained by one non-Task asynchronous I/O operation. */
final class ProtosBytecodeIoOperationExecution
        implements ProtosIoOperation.DeferredCPrimeExecutionForRuntime {
    private final ProtosIoOperation operation;
    private final CallTarget target;
    private final ProtosActivation activation;
    private final BiConsumer<ProtosIoOperation, Object> completion;
    private final BiConsumer<ProtosIoOperation, ProtosObjectValue> failure;
    private final Object stateLock = new Object();

    private boolean started;
    private boolean finished;
    private ContinuationResult continuation;
    private ProtosIoOperationSuspension suspension;

    private ProtosBytecodeIoOperationExecution(
            ProtosIoOperation operation,
            CallTarget target,
            ProtosActivation activation,
            BiConsumer<ProtosIoOperation, Object> completion,
            BiConsumer<ProtosIoOperation, ProtosObjectValue> failure) {
        this.operation = Objects.requireNonNull(operation, "operation");
        this.target = Objects.requireNonNull(target, "target");
        this.activation = Objects.requireNonNull(activation, "activation");
        this.completion = Objects.requireNonNull(completion, "completion");
        this.failure = Objects.requireNonNull(failure, "failure");
        if (activation.task().isPresent()) {
            throw new IllegalArgumentException(
                    "operation-owned C-prime execution cannot use a Task-owned activation");
        }
        if (activation.deferredCPrimeOperationForRuntime().orElse(null) != operation) {
            throw new IllegalArgumentException(
                    "operation-owned C-prime activation belongs to another execution owner");
        }
        if (activation.executionDomain() != operation.origin().executionDomain()) {
            throw new IllegalArgumentException(
                    "operation-owned C-prime activation belongs to another Actor domain");
        }
    }

    static void installAndSchedule(
            ProtosIoOperation operation,
            CallTarget target,
            ProtosActivation activation,
            BiConsumer<ProtosIoOperation, Object> completion,
            BiConsumer<ProtosIoOperation, ProtosObjectValue> failure) {
        ProtosBytecodeIoOperationExecution execution =
                new ProtosBytecodeIoOperationExecution(
                        operation, target, activation, completion, failure);
        operation.installDeferredCPrimeExecutionForRuntime(execution);
        if (!operation.requestDeferredCPrimeRunForRuntime()) {
            throw new IllegalStateException(
                    "fresh operation-owned C-prime execution could not become runnable");
        }
    }

    @Override
    public void runSegment(ProtosIoOperation scheduledOperation) {
        if (scheduledOperation != operation) {
            throw new IllegalArgumentException(
                    "operation-owned C-prime driver dispatched for another operation");
        }

        ContinuationResult resumeContinuation = null;
        ProtosIoOperationSuspension resumeSuspension = null;
        boolean initial = false;
        synchronized (stateLock) {
            if (finished) {
                return;
            }
            if (continuation != null) {
                if (!suspension.dependency().isReady()) {
                    return;
                }
                resumeContinuation = continuation;
                resumeSuspension = suspension;
                continuation = null;
                suspension = null;
            } else {
                if (started) {
                    throw new IllegalStateException(
                            "operation C-prime driver has no retained continuation to resume");
                }
                started = true;
                initial = true;
            }
        }

        try {
            Object outcome;
            if (initial) {
                outcome = Objects.requireNonNull(
                        target.call(activation),
                        "operation-owned C-prime entry returned null");
            } else {
                resumeSuspension.releaseWait();
                outcome = Objects.requireNonNull(
                        resumeContinuation.continueWith(ProtosNullValue.INSTANCE),
                        "operation-owned C-prime continuation returned null");
            }
            driveOutcome(outcome);
        } catch (ProtosBytecodeControlTransferException bridged) {
            throw bridged.transfer();
        } catch (ProtosSignalException signalled) {
            finishFailure(signalled.error());
        }
    }

    @Override
    public void operationTerminalized(ProtosIoOperation terminalOperation) {
        if (terminalOperation != operation) {
            throw new IllegalArgumentException(
                    "operation-owned C-prime terminal notification belongs to another operation");
        }
        ProtosIoOperationSuspension release;
        synchronized (stateLock) {
            finished = true;
            continuation = null;
            release = suspension;
            suspension = null;
        }
        if (release != null) {
            release.releaseWait();
        }
    }

    private void driveOutcome(Object initialOutcome) {
        Object outcome = initialOutcome;
        while (true) {
            if (!(outcome instanceof ContinuationResult next)) {
                finishCompletion(outcome);
                return;
            }

            ProtosIoOperationSuspension leaf = operationSuspensionLeaf(next);
            if (leaf.operation() != operation) {
                leaf.releaseWait();
                throw new IllegalStateException(
                        "C-prime continuation yielded a suspension owned by another I/O operation");
            }
            if (leaf.dependency().isReady()) {
                leaf.releaseWait();
                outcome = Objects.requireNonNull(
                        next.continueWith(ProtosNullValue.INSTANCE),
                        "ready operation-owned C-prime continuation returned null");
                continue;
            }

            boolean release;
            synchronized (stateLock) {
                release = finished;
                if (!release) {
                    if (continuation != null || suspension != null) {
                        throw new IllegalStateException(
                                "operation C-prime driver already retains a continuation");
                    }
                    continuation = next;
                    suspension = leaf;
                }
            }
            if (release) {
                leaf.releaseWait();
                return;
            }
            if (leaf.dependency().isReady()) {
                operation.requestDeferredCPrimeRunForRuntime();
            }
            return;
        }
    }

    private void finishCompletion(Object value) {
        synchronized (stateLock) {
            if (finished) {
                return;
            }
            finished = true;
        }
        completion.accept(operation, Objects.requireNonNull(value, "C-prime completion value"));
        if (!operation.terminal()) {
            throw new IllegalStateException(
                    "operation C-prime completion callback did not terminalize the I/O operation");
        }
    }

    private void finishFailure(ProtosObjectValue error) {
        synchronized (stateLock) {
            if (finished) {
                return;
            }
            finished = true;
        }
        failure.accept(operation, Objects.requireNonNull(error, "C-prime failure Error"));
        if (!operation.terminal()) {
            throw new IllegalStateException(
                    "operation C-prime failure callback did not terminalize the I/O operation");
        }
    }

    private static ProtosIoOperationSuspension operationSuspensionLeaf(ContinuationResult top) {
        Object current = top;
        while (current instanceof ContinuationResult nested) {
            current = nested.getResult();
        }
        if (!(current instanceof ProtosIoOperationSuspension suspension)) {
            throw new UnsupportedOperationException(
                    "PLAT029 operation-owned C-prime suspension requires an operation-owned suspension leaf");
        }
        return suspension;
    }
}
