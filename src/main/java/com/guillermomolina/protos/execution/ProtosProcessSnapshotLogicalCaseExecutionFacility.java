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
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * TOOL009-E / D178 suite-native Logical Case protocol adapter for the Process-snapshot Test Tool
 * lane.
 *
 * <p>This adapter translates the 4-argument discovery-driven Logical Case protocol ({@code
 * sourceAssociation, source, signature, selector}) into a selected-{@code Test.call()}-authority
 * execution: {@code source} declares its top-level {@code tests} (discovery-observational; no Test
 * body runs during declaration), one Test is resolved through the same {@code
 * Discovery.resolveSelectedTest} authority the ordinary suite-native lane uses, and only that
 * selected Test's {@code call()} completion is the Case authority. A signature or selector mismatch
 * is rejected before the selected Test body ever runs.
 *
 * <p>Real execution is delegated to {@link ProtosProcessSnapshotExecution#executeCase(String,
 * ProtosPrelude, List, String)}, which remains the sole bootstrap for this lane: every accepted
 * Case rematerializes a fresh Process there, so two Logical Cases never share Process state. Async
 * custody, cancellation and caller-domain completion mirror {@link
 * ProtosTestLogicalCaseExecutionFacility}.
 *
 * <p>This adapter owns no CaseId encoding, scheduling, retry, fixture lifecycle or corpus
 * discovery policy.
 */
public final class ProtosProcessSnapshotLogicalCaseExecutionFacility implements AutoCloseable {
    public static final String BOOTSTRAP_SLOT = "processSnapshotLogicalCaseExecutionAsync";

    private final ProtosPrelude executionPrelude;
    private final ProtosAsyncExactExecutionFacility.Submission submission;
    private final Set<Operation> outstanding = new LinkedHashSet<>();
    private boolean closed;

    private ProtosProcessSnapshotLogicalCaseExecutionFacility(
            ProtosPrelude executionPrelude,
            ProtosAsyncExactExecutionFacility.Submission submission) {
        this.executionPrelude = Objects.requireNonNull(executionPrelude, "executionPrelude");
        this.submission = Objects.requireNonNull(submission, "submission");
    }

    public static ProtosProcessSnapshotLogicalCaseExecutionFacility install(
            ProtosActivation activation,
            ProtosPrelude executionPrelude,
            ProtosAsyncExactExecutionFacility.Submission submission) {
        return install(activation, BOOTSTRAP_SLOT, executionPrelude, submission);
    }

    public static ProtosProcessSnapshotLogicalCaseExecutionFacility install(
            ProtosActivation activation,
            String slotName,
            ProtosPrelude executionPrelude,
            ProtosAsyncExactExecutionFacility.Submission submission) {
        Objects.requireNonNull(activation, "activation");
        Objects.requireNonNull(slotName, "slotName");
        Objects.requireNonNull(executionPrelude, "executionPrelude");
        Objects.requireNonNull(submission, "submission");

        if (slotName.isEmpty()) {
            throw new IllegalArgumentException(
                    "process-snapshot logical Case execution bootstrap slot name must not be"
                            + " empty");
        }
        if (activation.context().hasLocalSlot(slotName)) {
            throw new IllegalStateException(
                    "process-snapshot logical Case execution bootstrap slot already exists: "
                            + slotName);
        }

        ProtosProcessSnapshotLogicalCaseExecutionFacility facility =
                new ProtosProcessSnapshotLogicalCaseExecutionFacility(
                        executionPrelude, submission);

        activation
                .context()
                .createLocalSlot(
                        slotName,
                        ProtosExactExecutionFacility.exactExecutionBootstrapClosure(
                                (caller, arguments) -> facility.execute(caller, arguments)));

        return facility;
    }

    private Object execute(ProtosActivation caller, List<?> arguments) {
        if (arguments.size() != 4
                || !(arguments.get(0) instanceof ProtosArrayValue sourceAssociation)
                || !(arguments.get(1) instanceof ProtosStringValue source)
                || !(arguments.get(2) instanceof ProtosArrayValue signature)
                || !(arguments.get(3) instanceof ProtosStringValue selector)) {
            throw ProtosExactExecutionFacility.ordinaryError(caller);
        }

        List<Object> association = sourceAssociation.indexedSnapshot();
        if (association.size() != 2
                || !(association.get(0) instanceof ProtosStringValue corpusId)
                || !(association.get(1) instanceof ProtosStringValue sourcePath)
                || corpusId.value().isEmpty()
                || sourcePath.value().isEmpty()) {
            throw ProtosExactExecutionFacility.ordinaryError(caller);
        }

        if (selector.value().isEmpty()) {
            throw ProtosExactExecutionFacility.ordinaryError(caller);
        }

        ArrayList<String> expectedSignature = new ArrayList<>();
        for (Object value : signature.indexedSnapshot()) {
            if (!(value instanceof ProtosStringValue name) || name.value().isEmpty()) {
                throw ProtosExactExecutionFacility.ordinaryError(caller);
            }
            expectedSignature.add(name.value());
        }

        return start(caller, source, expectedSignature, selector.value());
    }

    private ProtosFutureValue start(
            ProtosActivation caller,
            ProtosStringValue source,
            List<String> expectedSignature,
            String selector) {
        ProtosPrelude callerPrelude =
                caller.prelude()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "process-snapshot logical Case completion"
                                                        + " requires caller Core prelude"));

        ProtosFutureValue future =
                new ProtosFutureValue(callerPrelude.futurePrototype(), caller.executionDomain());

        Operation operation =
                new Operation(caller, callerPrelude, future, source, expectedSignature, selector);

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
                    "process-snapshot logical Case execution facility is closed");
        }
        outstanding.add(Objects.requireNonNull(operation, "operation"));
    }

    private void operationTerminal(Operation operation) {
        synchronized (this) {
            outstanding.remove(operation);
            notifyAll();
        }
    }

    private ProtosObjectValue rematerialize(
            ProtosProcessSnapshotExecution.CaseResult result,
            ProtosActivation caller,
            ProtosPrelude callerPrelude) {
        ProtosObjectValue envelope = new ProtosObjectValue(ProtosObjectValue.rootObject());

        String phase =
                switch (result.phase()) {
                    case REMATERIALIZATION_ERROR -> "rematerialization-error";
                    case CASE_EXECUTION -> "case-execution";
                };

        envelope.createLocalSlot("phase", new ProtosStringValue(phase));
        envelope.createLocalSlot(
                "observation",
                ProtosExactExecutionFacility.observation(
                        new ProtosCapturedProcessExecution.Result(
                                result.outcome(), new byte[0], new byte[0]),
                        caller,
                        callerPrelude,
                        executionPrelude));

        envelope.freeze();
        return envelope;
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
        private final ProtosStringValue source;
        private final List<String> expectedSignature;
        private final String selector;

        private ProtosAsyncExactExecutionFacility.Submitted submitted;
        private boolean cancellationRequested;
        private boolean running;
        private boolean hostTerminal;

        private Operation(
                ProtosActivation caller,
                ProtosPrelude callerPrelude,
                ProtosFutureValue future,
                ProtosStringValue source,
                List<String> expectedSignature,
                String selector) {
            this.caller = Objects.requireNonNull(caller, "caller");
            this.callerPrelude = Objects.requireNonNull(callerPrelude, "callerPrelude");
            this.future = Objects.requireNonNull(future, "future");
            this.source = Objects.requireNonNull(source, "source");
            this.expectedSignature =
                    List.copyOf(Objects.requireNonNull(expectedSignature, "expectedSignature"));
            this.selector = Objects.requireNonNull(selector, "selector");
        }

        private void submit() {
            synchronized (this) {
                if (cancellationRequested) {
                    finishHostWithoutStarting();
                    return;
                }
            }

            ProtosAsyncExactExecutionFacility.Submitted accepted =
                    Objects.requireNonNull(
                            submission.submit(this::runHost),
                            "submission returned null handle");

            boolean cancelAccepted;
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

            ProtosProcessSnapshotExecution.CaseResult result = null;
            RuntimeException hostFailure = null;

            try {
                result =
                        ProtosProcessSnapshotExecution.executeCase(
                                source.value(), executionPrelude, expectedSignature, selector);
            } catch (RuntimeException failure) {
                hostFailure = failure;
            }

            enqueueCallerCompletion(result, hostFailure);
            finishHostAfterRun();
        }

        private void enqueueCallerCompletion(
                ProtosProcessSnapshotExecution.CaseResult result, RuntimeException hostFailure) {
            try {
                caller.executionDomain()
                        .createTask(
                                null,
                                null,
                                task ->
                                        task.executeHostActionForRuntime(
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
                                                        future.resolve(
                                                                rematerialize(
                                                                        Objects.requireNonNull(
                                                                                result,
                                                                                "process-snapshot"
                                                                                    + " logical"
                                                                                    + " Case"
                                                                                    + " completion"),
                                                                        caller,
                                                                        callerPrelude),
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
            ProtosAsyncExactExecutionFacility.Submitted accepted;
            boolean mayWithdraw;

            synchronized (this) {
                if (cancellationRequested) {
                    return;
                }
                cancellationRequested = true;
                accepted = submitted;
                mayWithdraw = !running && !hostTerminal && accepted != null;
            }

            future.cancelTerminal();

            if (mayWithdraw && cancelBeforeStart(accepted)) {
                finishHostWithoutStarting();
            }
        }

        private boolean cancelBeforeStart(ProtosAsyncExactExecutionFacility.Submitted accepted) {
            try {
                return accepted.cancelIfNotStarted();
            } catch (RuntimeException ignored) {
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
