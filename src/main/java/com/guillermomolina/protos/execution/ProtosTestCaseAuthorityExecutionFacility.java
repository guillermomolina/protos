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
 * the specific language governing rights and limitations under the LICENSE.
 */

package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * D133/D134 asynchronous execution facility for one exact project-tree CaseAuthority binding.
 *
 * <p>The physical cases root is invocation-scoped host authority. Callers provide only exact
 * source and logical fixture identity. Each accepted execution materializes a fresh physical
 * authority through {@link ProtosTestCaseAuthorityAttemptBridge}.
 */
public final class ProtosTestCaseAuthorityExecutionFacility implements AutoCloseable {

    private final ProtosPrelude executionPrelude;
    private final Path casesRoot;
    private final ProtosTestCaseAuthorityAttemptBridge bridge;
    private final ProtosAsyncExactExecutionFacility.Submission submission;
    private final Set<Operation> outstanding = new LinkedHashSet<>();
    private boolean closed;

    private ProtosTestCaseAuthorityExecutionFacility(
            ProtosPrelude executionPrelude,
            Path casesRoot,
            ProtosPolyglotRuntimeHost runtimeHost,
            ProtosAsyncExactExecutionFacility.Submission submission) {
        this.executionPrelude =
                Objects.requireNonNull(executionPrelude, "executionPrelude");
        this.casesRoot =
                Objects.requireNonNull(casesRoot, "casesRoot")
                        .toAbsolutePath()
                        .normalize();
        this.bridge =
                new ProtosTestCaseAuthorityAttemptBridge(
                        Objects.requireNonNull(runtimeHost, "runtimeHost"));
        this.submission =
                Objects.requireNonNull(submission, "submission");
    }

    public static ProtosTestCaseAuthorityExecutionFacility install(
            ProtosActivation activation,
            String slotName,
            ProtosPrelude executionPrelude,
            Path casesRoot,
            ProtosPolyglotRuntimeHost runtimeHost,
            ProtosAsyncExactExecutionFacility.Submission submission) {
        Objects.requireNonNull(activation, "activation");
        Objects.requireNonNull(slotName, "slotName");
        Objects.requireNonNull(executionPrelude, "executionPrelude");
        Objects.requireNonNull(casesRoot, "casesRoot");
        Objects.requireNonNull(runtimeHost, "runtimeHost");
        Objects.requireNonNull(submission, "submission");

        if (slotName.isEmpty()) {
            throw new IllegalArgumentException(
                    "CaseAuthority execution bootstrap slot name must not be empty");
        }

        if (activation.context().hasLocalSlot(slotName)) {
            throw new IllegalStateException(
                    "CaseAuthority execution bootstrap slot already exists: "
                            + slotName);
        }

        ProtosTestCaseAuthorityExecutionFacility facility =
                new ProtosTestCaseAuthorityExecutionFacility(
                        executionPrelude,
                        casesRoot,
                        runtimeHost,
                        submission);

        activation
                .context()
                .createLocalSlot(
                        slotName,
                        ProtosExactExecutionFacility.exactExecutionBootstrapClosure(
                                (callActivation, arguments) ->
                                        facility.execute(
                                                callActivation,
                                                arguments)));

        return facility;
    }

    private Object execute(
            ProtosActivation caller,
            List<?> arguments) {
        if (arguments.size() != 2
                || !(arguments.get(0) instanceof ProtosStringValue source)
                || !(arguments.get(1) instanceof ProtosArrayValue descriptor)) {
            throw ProtosExactExecutionFacility.ordinaryError(caller);
        }

        ProtosCapturedProcessExecution.Request execution =
                ProtosExactExecutionFacility.executionRequest(
                        source,
                        executionPrelude);

        ProtosTestCaseAuthorityAttemptBridge.Request request =
                new ProtosTestCaseAuthorityAttemptBridge.Request(
                        execution,
                        casesRoot,
                        fixtureIdentity(descriptor, caller));

        return start(caller, request);
    }

    private static String fixtureIdentity(
            ProtosArrayValue descriptor,
            ProtosActivation caller) {
        if (!descriptor.indexedSize().equals(BigInteger.valueOf(4))) {
            throw ProtosExactExecutionFacility.ordinaryError(caller);
        }

        Object kind = descriptor.indexedAt(BigInteger.ZERO);
        Object fixture = descriptor.indexedAt(BigInteger.ONE);
        Object isolation = descriptor.indexedAt(BigInteger.valueOf(2));
        Object lifecycle = descriptor.indexedAt(BigInteger.valueOf(3));

        if (!(kind instanceof ProtosStringValue kindValue)
                || !(fixture instanceof ProtosStringValue fixtureValue)
                || !(isolation instanceof ProtosStringValue isolationValue)
                || !(lifecycle instanceof ProtosStringValue lifecycleValue)
                || !kindValue.value().equals("project-tree")
                || !isolationValue.value().equals("case")
                || !lifecycleValue.value().equals("case")
                || fixtureValue.value().isEmpty()) {
            throw ProtosExactExecutionFacility.ordinaryError(caller);
        }

        return fixtureValue.value();
    }

    private ProtosFutureValue start(
            ProtosActivation caller,
            ProtosTestCaseAuthorityAttemptBridge.Request request) {
        ProtosPrelude callerPrelude =
                caller
                        .prelude()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "CaseAuthority execution completion requires caller Core prelude"));

        ProtosFutureValue future =
                new ProtosFutureValue(
                        callerPrelude.futurePrototype(),
                        caller.executionDomain());

        Operation operation =
                new Operation(
                        caller,
                        callerPrelude,
                        future,
                        request);

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
                    "CaseAuthority execution facility is closed");
        }

        outstanding.add(
                Objects.requireNonNull(operation, "operation"));
    }

    private void operationTerminal(Operation operation) {
        synchronized (this) {
            outstanding.remove(operation);
            notifyAll();
        }
    }

    private ProtosObjectValue rematerialize(
            ProtosTestCaseAuthorityAttemptCompletion completion,
            ProtosActivation caller,
            ProtosPrelude callerPrelude) {
        Objects.requireNonNull(completion, "completion");

        ProtosObjectValue result =
                new ProtosObjectValue(
                        ProtosObjectValue.rootObject());

        Object guestObservation =
                completion
                        .guestObservation()
                        .<Object>map(
                                captured ->
                                        ProtosExactExecutionFacility.observation(
                                                captured,
                                                caller,
                                                callerPrelude,
                                                executionPrelude))
                        .orElse(ProtosNullValue.INSTANCE);

        ArrayList<Object> infrastructureEvidence =
                new ArrayList<>();

        for (Throwable failure :
                completion.infrastructureFailures()) {
            infrastructureEvidence.add(
                    new ProtosStringValue(
                            failure.toString()));
        }

        ProtosArrayValue failures =
                callerPrelude.newArray(
                        infrastructureEvidence);
        failures.freeze();

        result.createLocalSlot(
                "guestObservation",
                guestObservation);
        result.createLocalSlot(
                "infrastructureFailed",
                ProtosBooleanValue.of(
                        completion.infrastructureFailed()));
        result.createLocalSlot(
                "infrastructureFailures",
                failures);
        result.freeze();

        return result;
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
        private final ProtosTestCaseAuthorityAttemptBridge.Request request;

        private ProtosAsyncExactExecutionFacility.Submitted submitted;
        private boolean cancellationRequested;
        private boolean running;
        private boolean hostTerminal;

        private Operation(
                ProtosActivation caller,
                ProtosPrelude callerPrelude,
                ProtosFutureValue future,
                ProtosTestCaseAuthorityAttemptBridge.Request request) {
            this.caller =
                    Objects.requireNonNull(caller, "caller");
            this.callerPrelude =
                    Objects.requireNonNull(callerPrelude, "callerPrelude");
            this.future =
                    Objects.requireNonNull(future, "future");
            this.request =
                    Objects.requireNonNull(request, "request");
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

            boolean cancelAccepted = false;

            synchronized (this) {
                if (hostTerminal) {
                    return;
                }

                submitted = accepted;
                cancelAccepted =
                        cancellationRequested
                                && !running;
            }

            if (cancelAccepted
                    && cancelBeforeStart(accepted)) {
                finishHostWithoutStarting();
            }
        }

        private void runHost() {
            boolean cancelledBeforeStart;

            synchronized (this) {
                if (hostTerminal) {
                    return;
                }

                cancelledBeforeStart =
                        cancellationRequested;

                if (!cancelledBeforeStart) {
                    running = true;
                }
            }

            if (cancelledBeforeStart) {
                finishHostWithoutStarting();
                return;
            }

            ProtosTestCaseAuthorityAttemptCompletion completion = null;
            RuntimeException hostFailure = null;

            try {
                completion =
                        bridge.execute(request);
            } catch (RuntimeException failure) {
                hostFailure = failure;
            }

            enqueueCallerCompletion(
                    completion,
                    hostFailure);

            finishHostAfterRun();
        }

        private void enqueueCallerCompletion(
                ProtosTestCaseAuthorityAttemptCompletion completion,
                RuntimeException hostFailure) {
            try {
                caller
                        .executionDomain()
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
                                                                                completion,
                                                                                "CaseAuthority terminal completion"),
                                                                        caller,
                                                                        callerPrelude),
                                                                caller);
                                                    } catch (ProtosSignalException signal) {
                                                        future.fail(
                                                                signal.error());
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
                mayWithdraw =
                        !running
                                && !hostTerminal
                                && accepted != null;
            }

            future.cancelTerminal();

            if (mayWithdraw
                    && cancelBeforeStart(accepted)) {
                finishHostWithoutStarting();
            }
        }

        private boolean cancelBeforeStart(
                ProtosAsyncExactExecutionFacility.Submitted accepted) {
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
                terminalNow =
                        !hostTerminal
                                && !running;

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
