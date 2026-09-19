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
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * TOOL009 asynchronous execution boundary for one discovered logical Case.
 *
 * <p>The physical source path is an execution locator only and is not logical Case identity.
 * Callers supply the authoritative discovery signature and one local selector. Host execution
 * retains only the bridge completion until a caller-domain completion task rematerializes the
 * ordinary observation.
 *
 * <p>This facility owns no scheduling topology, Test pass/fail interpretation, exit policy,
 * retry, fixture lifecycle, CaseId encoding or Case environment policy.
 */
public final class ProtosTestLogicalCaseExecutionFacility implements AutoCloseable {
    public static final String BOOTSTRAP_SLOT = "logicalCaseExecutionAsync";

    private final ProtosTestLogicalCaseAttemptBridge bridge;
    private final ProtosAsyncExactExecutionFacility.Submission submission;
    private final Set<Operation> outstanding = new LinkedHashSet<>();
    private boolean closed;

    private ProtosTestLogicalCaseExecutionFacility(
            Path core,
            ProtosModuleResolver fallbackResolver,
            ProtosPolyglotRuntimeHost runtimeHost,
            ProtosAsyncExactExecutionFacility.Submission submission) {
        this.bridge =
                new ProtosTestLogicalCaseAttemptBridge(
                        Objects.requireNonNull(core, "core"),
                        Objects.requireNonNull(
                                fallbackResolver,
                                "fallbackResolver"),
                        Objects.requireNonNull(
                                runtimeHost,
                                "runtimeHost"));
        this.submission =
                Objects.requireNonNull(
                        submission,
                        "submission");
    }

    public static ProtosTestLogicalCaseExecutionFacility install(
            ProtosActivation activation,
            Path core,
            ProtosModuleResolver fallbackResolver,
            ProtosPolyglotRuntimeHost runtimeHost,
            ProtosAsyncExactExecutionFacility.Submission submission) {
        return install(
                activation,
                BOOTSTRAP_SLOT,
                core,
                fallbackResolver,
                runtimeHost,
                submission);
    }

    public static ProtosTestLogicalCaseExecutionFacility install(
            ProtosActivation activation,
            String slotName,
            Path core,
            ProtosModuleResolver fallbackResolver,
            ProtosPolyglotRuntimeHost runtimeHost,
            ProtosAsyncExactExecutionFacility.Submission submission) {
        Objects.requireNonNull(activation, "activation");
        Objects.requireNonNull(slotName, "slotName");

        if (slotName.isEmpty()) {
            throw new IllegalArgumentException(
                    "logical Case execution bootstrap slot name must not be empty");
        }

        if (activation.context().hasLocalSlot(slotName)) {
            throw new IllegalStateException(
                    "logical Case execution bootstrap slot already exists: "
                            + slotName);
        }

        ProtosTestLogicalCaseExecutionFacility facility =
                new ProtosTestLogicalCaseExecutionFacility(
                        core,
                        fallbackResolver,
                        runtimeHost,
                        submission);

        activation
                .context()
                .createLocalSlot(
                        slotName,
                        ProtosExactExecutionFacility.exactExecutionBootstrapClosure(
                                (caller, arguments) ->
                                        facility.execute(
                                                caller,
                                                arguments)));

        return facility;
    }

    private Object execute(
            ProtosActivation caller,
            List<?> arguments) {
        if (arguments.size() != 4
                || !(arguments.get(0)
                        instanceof ProtosStringValue sourcePath)
                || !(arguments.get(1)
                        instanceof ProtosStringValue source)
                || !(arguments.get(2)
                        instanceof ProtosArrayValue signature)
                || !(arguments.get(3)
                        instanceof ProtosStringValue selector)) {
            throw ProtosExactExecutionFacility.ordinaryError(caller);
        }

        if (sourcePath.value().isEmpty()) {
            throw ProtosExactExecutionFacility.ordinaryError(caller);
        }

        Path physicalPath;
        try {
            physicalPath = Path.of(sourcePath.value());
        } catch (InvalidPathException failure) {
            throw ProtosExactExecutionFacility.ordinaryError(caller);
        }

        ArrayList<String> expectedSignature = new ArrayList<>();

        for (Object value : signature.indexedSnapshot()) {
            if (!(value instanceof ProtosStringValue name)
                    || name.value().isEmpty()) {
                throw ProtosExactExecutionFacility.ordinaryError(caller);
            }

            expectedSignature.add(name.value());
        }

        final ProtosTestLogicalCaseAttemptBridge.Request request;
        try {
            request =
                    new ProtosTestLogicalCaseAttemptBridge.Request(
                            physicalPath,
                            source.value(),
                            expectedSignature,
                            selector.value());
        } catch (IllegalArgumentException failure) {
            throw ProtosExactExecutionFacility.ordinaryError(caller);
        }

        return start(caller, request);
    }

    private ProtosFutureValue start(
            ProtosActivation caller,
            ProtosTestLogicalCaseAttemptBridge.Request request) {
        ProtosPrelude callerPrelude =
                caller
                        .prelude()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "logical Case completion requires caller Core prelude"));

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
        caller.executionDomain()
                .registerActorNonTaskFutureForRuntime(future);

        try {
            operation.submit();
        } catch (RuntimeException failure) {
            operation.submissionFailed();
            throw failure;
        }

        return future;
    }

    private synchronized void registerOutstanding(
            Operation operation) {
        if (closed) {
            throw new IllegalStateException(
                    "logical Case execution facility is closed");
        }

        outstanding.add(
                Objects.requireNonNull(
                        operation,
                        "operation"));
    }

    private void operationTerminal(Operation operation) {
        synchronized (this) {
            outstanding.remove(operation);
            notifyAll();
        }
    }

    private static ProtosObjectValue rematerialize(
            ProtosTestLogicalCaseAttemptBridge.Result result,
            ProtosActivation caller,
            ProtosPrelude callerPrelude) {
        ProtosObjectValue envelope =
                new ProtosObjectValue(
                        ProtosObjectValue.rootObject());

        String phase =
                switch (result.phase()) {
                    case REMATERIALIZATION_ERROR ->
                            "rematerialization-error";
                    case CASE_EXECUTION ->
                            "case-execution";
                };

        envelope.createLocalSlot(
                "phase",
                new ProtosStringValue(phase));

        envelope.createLocalSlot(
                "observation",
                ProtosExactExecutionFacility.observation(
                        result.captured(),
                        caller,
                        callerPrelude,
                        result.sourcePrelude()));

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
        private final ProtosTestLogicalCaseAttemptBridge.Request request;

        private ProtosAsyncExactExecutionFacility.Submitted submitted;
        private boolean cancellationRequested;
        private boolean running;
        private boolean hostTerminal;

        private Operation(
                ProtosActivation caller,
                ProtosPrelude callerPrelude,
                ProtosFutureValue future,
                ProtosTestLogicalCaseAttemptBridge.Request request) {
            this.caller =
                    Objects.requireNonNull(
                            caller,
                            "caller");
            this.callerPrelude =
                    Objects.requireNonNull(
                            callerPrelude,
                            "callerPrelude");
            this.future =
                    Objects.requireNonNull(
                            future,
                            "future");
            this.request =
                    Objects.requireNonNull(
                            request,
                            "request");
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

            ProtosTestLogicalCaseAttemptBridge.Result result = null;
            Exception hostFailure = null;

            try {
                result = bridge.execute(request);
            } catch (Exception failure) {
                hostFailure = failure;
            }

            enqueueCallerCompletion(
                    result,
                    hostFailure);
            finishHostAfterRun();
        }

        private void enqueueCallerCompletion(
                ProtosTestLogicalCaseAttemptBridge.Result result,
                Exception hostFailure) {
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
                                                                                result,
                                                                                "logical Case completion"),
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
