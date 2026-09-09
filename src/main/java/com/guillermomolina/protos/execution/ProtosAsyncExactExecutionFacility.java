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
package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Bootstrap-local asynchronous one-exact-execution bridge.
 *
 * <p>This class is test-neutral host/runtime machinery. Higher-level tool scheduling, result
 * interpretation and reporting remain outside this boundary. The caller supplies an off-domain
 * host submission mechanism explicitly; this class deliberately selects no concrete carrier
 * topology.
 *
 * <p>Each invocation returns one ordinary non-task caller-domain Future. Host execution retains
 * only captured inert evidence. Rematerialization of the frozen execution observation happens
 * only after a completion task has been enqueued into the caller Actor domain.
 */
public final class ProtosAsyncExactExecutionFacility implements AutoCloseable {
    public static final String BOOTSTRAP_SLOT = "executionAsync";
    public static final String INSPECTION_BOOTSTRAP_SLOT = "executionInspectAsync";

    /**
     * Host submission boundary. A successful call accepts {@code work} for execution outside the
     * caller Actor domain and must not execute it inline as part of the caller's Protos segment.
     *
     * <p>If this method throws, the work must not have been accepted.
     */
    @FunctionalInterface
    public interface Submission {
        Submitted submit(Runnable work);
    }

    /**
     * Handle for one accepted host submission.
     *
     * <p>{@link #cancelIfNotStarted()} returns true only when the work was prevented from starting.
     * It must not claim success for already-started work and must not interrupt such work.
     */
    @FunctionalInterface
    public interface Submitted {
        boolean cancelIfNotStarted();
    }


    @FunctionalInterface
    private interface HostExecution {
        ProtosCapturedProcessExecution.Result run();
    }

    private final ProtosPrelude executionPrelude;
    private final ProtosPolyglotRuntimeHost runtimeHost;
    private final Submission submission;
    private final Set<Operation> outstanding = new LinkedHashSet<>();
    private boolean closed;

    private ProtosAsyncExactExecutionFacility(
            ProtosPrelude executionPrelude,
            ProtosPolyglotRuntimeHost runtimeHost,
            Submission submission) {
        this.executionPrelude = Objects.requireNonNull(executionPrelude, "executionPrelude");
        this.runtimeHost = Objects.requireNonNull(runtimeHost, "runtimeHost");
        this.submission = Objects.requireNonNull(submission, "submission");
    }

    public static ProtosAsyncExactExecutionFacility install(
            ProtosActivation activation,
            ProtosPolyglotRuntimeHost runtimeHost,
            Submission submission) {
        Objects.requireNonNull(activation, "activation");
        ProtosPrelude executionPrelude =
                activation
                        .prelude()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "async exact execution facility requires Core prelude"));
        return install(
                activation,
                BOOTSTRAP_SLOT,
                executionPrelude,
                runtimeHost,
                submission);
    }

    public static ProtosAsyncExactExecutionFacility install(
            ProtosActivation activation,
            String slotName,
            ProtosPrelude executionPrelude,
            ProtosPolyglotRuntimeHost runtimeHost,
            Submission submission) {
        Objects.requireNonNull(activation, "activation");
        Objects.requireNonNull(slotName, "slotName");
        Objects.requireNonNull(executionPrelude, "executionPrelude");
        Objects.requireNonNull(runtimeHost, "runtimeHost");
        Objects.requireNonNull(submission, "submission");
        if (slotName.isEmpty()) {
            throw new IllegalArgumentException(
                    "async exact execution bootstrap slot name must not be empty");
        }
        if (activation.context().hasLocalSlot(slotName)) {
            throw new IllegalStateException(
                    "async exact execution bootstrap slot already exists: " + slotName);
        }

        ProtosAsyncExactExecutionFacility facility =
                new ProtosAsyncExactExecutionFacility(
                        executionPrelude,
                        runtimeHost,
                        submission);
        activation
                .context()
                .createLocalSlot(
                        slotName,
                        ProtosExactExecutionFacility.exactExecutionBootstrapClosure(
                                (callActivation, arguments) ->
                                        facility.execute(callActivation, arguments)));
        return facility;
    }


    /**
     * Installs the asynchronous live-result inspection variant under
     * {@link #INSPECTION_BOOTSTRAP_SLOT} using the caller's already-selected Prelude.
     */
    public static ProtosAsyncExactExecutionFacility installInspection(
            ProtosActivation activation,
            ProtosPolyglotRuntimeHost runtimeHost,
            Submission submission) {
        Objects.requireNonNull(activation, "activation");
        ProtosPrelude executionPrelude =
                activation
                        .prelude()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "async exact inspection facility requires Core prelude"));
        return installInspection(
                activation,
                INSPECTION_BOOTSTRAP_SLOT,
                executionPrelude,
                runtimeHost,
                submission);
    }

    public static ProtosAsyncExactExecutionFacility installInspection(
            ProtosActivation activation,
            String slotName,
            ProtosPrelude executionPrelude,
            ProtosPolyglotRuntimeHost runtimeHost,
            Submission submission) {
        Objects.requireNonNull(activation, "activation");
        Objects.requireNonNull(slotName, "slotName");
        Objects.requireNonNull(executionPrelude, "executionPrelude");
        Objects.requireNonNull(runtimeHost, "runtimeHost");
        Objects.requireNonNull(submission, "submission");
        if (slotName.isEmpty()) {
            throw new IllegalArgumentException(
                    "async exact inspection bootstrap slot name must not be empty");
        }
        if (activation.context().hasLocalSlot(slotName)) {
            throw new IllegalStateException(
                    "async exact inspection bootstrap slot already exists: " + slotName);
        }

        ProtosAsyncExactExecutionFacility facility =
                new ProtosAsyncExactExecutionFacility(
                        executionPrelude,
                        runtimeHost,
                        submission);
        activation
                .context()
                .createLocalSlot(
                        slotName,
                        ProtosExactExecutionFacility.exactExecutionBootstrapClosure(
                                (callActivation, arguments) ->
                                        facility.inspect(callActivation, arguments)));
        return facility;
    }


    private Object execute(
            ProtosActivation caller,
            List<?> arguments) {
        if (arguments.size() != 1
                || !(arguments.get(0) instanceof ProtosStringValue source)) {
            throw ProtosExactExecutionFacility.ordinaryError(caller);
        }

        ProtosCapturedProcessExecution.Request request =
                ProtosExactExecutionFacility.executionRequest(
                        source,
                        executionPrelude);
        return start(
                caller,
                () ->
                        ProtosCapturedProcessExecution.execute(
                                request,
                                runtimeHost));
    }

    private Object inspect(
            ProtosActivation caller,
            List<?> arguments) {
        if (arguments.size() != 2
                || !(arguments.get(0) instanceof ProtosStringValue source)
                || !(arguments.get(1) instanceof ProtosStringValue inspector)) {
            throw ProtosExactExecutionFacility.ordinaryError(caller);
        }

        ProtosExactExecutionFacility.InspectionInvocation invocation =
                ProtosExactExecutionFacility.inspectionInvocation(
                        source,
                        inspector,
                        executionPrelude);
        return start(
                caller,
                () ->
                        ProtosCapturedProcessExecution.executeThenInspect(
                                invocation.request(),
                                invocation.inspector(),
                                runtimeHost));
    }

    private ProtosFutureValue start(
            ProtosActivation caller,
            HostExecution hostExecution) {
        ProtosPrelude callerPrelude =
                caller
                        .prelude()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "async exact execution observation requires caller Core prelude"));
        ProtosFutureValue future =
                new ProtosFutureValue(
                        callerPrelude.futurePrototype(),
                        caller.executionDomain());
        Operation operation =
                new Operation(
                        caller,
                        callerPrelude,
                        future,
                        hostExecution);

        registerOutstanding(operation);
        future.attachCancellationProducer(operation::requestCancellation);
        caller.executionDomain().registerActorNonTaskFutureForRuntime(future);

        try {
            operation.submit();
        } catch (RuntimeException failure) {
            operation.submissionFailed();
            throw failure;
        }
        return future;
    }

    private synchronized void registerOutstanding(Operation operation) {
        if (closed) {
            throw new IllegalStateException(
                    "async exact execution facility is closed");
        }
        outstanding.add(Objects.requireNonNull(operation, "operation"));
    }

    private void operationTerminal(Operation operation) {
        synchronized (this) {
            outstanding.remove(operation);
            notifyAll();
        }
    }

    @Override
    public void close() {
        boolean interrupted = false;
        synchronized (this) {
            closed = true;
            while (!outstanding.isEmpty()) {
                try {
                    wait();
                } catch (InterruptedException interruption) {
                    interrupted = true;
                }
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private final class Operation {
        private final ProtosActivation caller;
        private final ProtosPrelude callerPrelude;
        private final ProtosFutureValue future;
        private final HostExecution hostExecution;

        private Submitted submitted;
        private boolean cancellationRequested;
        private boolean running;
        private boolean hostTerminal;

        private Operation(
                ProtosActivation caller,
                ProtosPrelude callerPrelude,
                ProtosFutureValue future,
                HostExecution hostExecution) {
            this.caller = Objects.requireNonNull(caller, "caller");
            this.callerPrelude = Objects.requireNonNull(callerPrelude, "callerPrelude");
            this.future = Objects.requireNonNull(future, "future");
            this.hostExecution = Objects.requireNonNull(hostExecution, "hostExecution");
        }

        private void submit() {
            synchronized (this) {
                if (cancellationRequested) {
                    finishHostWithoutStarting();
                    return;
                }
            }

            Submitted accepted =
                    Objects.requireNonNull(
                            submission.submit(this::runHost),
                            "submission returned null handle");

            boolean cancelAccepted = false;
            synchronized (this) {
                if (hostTerminal) {
                    return;
                }
                submitted = accepted;
                cancelAccepted = cancellationRequested && !running;
            }

            if (cancelAccepted && cancelBeforeStart(accepted)) {
                finishHostWithoutStarting();
            }
        }

        private void runHost() {
            boolean cancelledBeforeStart;
            synchronized (this) {
                if (hostTerminal) {
                    return;
                }
                cancelledBeforeStart = cancellationRequested;
                if (!cancelledBeforeStart) {
                    running = true;
                }
            }
            if (cancelledBeforeStart) {
                finishHostWithoutStarting();
                return;
            }

            ProtosCapturedProcessExecution.Result result = null;
            RuntimeException hostFailure = null;
            try {
                result = hostExecution.run();
            } catch (RuntimeException failure) {
                hostFailure = failure;
            }

            enqueueCallerCompletion(result, hostFailure);
            finishHostAfterRun();
        }

        private void enqueueCallerCompletion(
                ProtosCapturedProcessExecution.Result result,
                RuntimeException hostFailure) {
            try {
                caller
                        .executionDomain()
                        .createTask(
                                null,
                                null,
                                task ->
                                        task.executeAction(
                                                () -> {
                                                    if (!future.isPending()) {
                                                        return ProtosNullValue.INSTANCE;
                                                    }
                                                    if (hostFailure != null) {
                                                        future.fail(
                                                                ProtosCoreErrors.newError(
                                                                        caller));
                                                        return ProtosNullValue.INSTANCE;
                                                    }
                                                    try {
                                                        Object observation =
                                                                ProtosExactExecutionFacility.observation(
                                                                        Objects.requireNonNull(
                                                                                result,
                                                                                "captured result"),
                                                                        caller,
                                                                        callerPrelude,
                                                                        executionPrelude);
                                                        future.resolve(
                                                                observation,
                                                                caller);
                                                    } catch (ProtosSignalException signal) {
                                                        future.fail(signal.error());
                                                    }
                                                    return ProtosNullValue.INSTANCE;
                                                }));
            } catch (IllegalStateException callerTerminated) {
                future.cancelTerminal();
            }
        }

        private void requestCancellation() {
            Submitted accepted;
            boolean mayWithdraw;
            synchronized (this) {
                if (cancellationRequested) {
                    return;
                }
                cancellationRequested = true;
                accepted = submitted;
                mayWithdraw =
                        !running
                                && !hostTerminal
                                && accepted != null;
            }

            future.cancelTerminal();

            if (mayWithdraw && cancelBeforeStart(accepted)) {
                finishHostWithoutStarting();
            }
        }

        private boolean cancelBeforeStart(Submitted accepted) {
            try {
                return accepted.cancelIfNotStarted();
            } catch (RuntimeException ignored) {
                // Cancellation observation must not fail because a replaceable host
                // submission mechanism could not withdraw the work. If it was already
                // accepted, it retains custody and must eventually invoke or settle work.
                return false;
            }
        }

        private void submissionFailed() {
            future.cancelTerminal();
            finishHostWithoutStarting();
        }

        private void finishHostWithoutStarting() {
            boolean terminalNow;
            synchronized (this) {
                terminalNow = !hostTerminal && !running;
                if (terminalNow) {
                    hostTerminal = true;
                }
            }
            if (terminalNow) {
                operationTerminal(this);
            }
        }

        private void finishHostAfterRun() {
            boolean terminalNow;
            synchronized (this) {
                running = false;
                terminalNow = !hostTerminal;
                if (terminalNow) {
                    hostTerminal = true;
                }
            }
            if (terminalNow) {
                operationTerminal(this);
            }
        }
    }
}
