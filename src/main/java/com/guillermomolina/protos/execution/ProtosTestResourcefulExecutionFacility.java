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
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * Private TOOL002-I8D4C1/C2 caller-domain rematerialization of resourceful D108 terminal attempts.
 *
 * <p>{@code resourceExecutionAsync} is the closed C1 ordinary exact-source path.
 * {@code resourceExecutionInspectAsync} is the C2 live-result inspection peer. The inspection
 * callable accepts exact source, exact inspector source, and the same inert snapshot of
 * already-bound/already-reserved D077/D097/D098 facts. Source and inspector stay inside the same
 * fresh resourceful Process; only the inspector's detached observation is rematerialized.
 *
 * <p>Provider resolution/provisioning remains I8D2/I8D4B/C2. Scheduler admission and I8B release
 * remain Protos Runner policy. Infrastructure failure resolves as ordinary inert completion data
 * and is never converted into guest Error. Caller Futures become terminal only after the D108
 * Process/host/provider lifecycle is terminal.
 */
final class ProtosTestResourcefulExecutionFacility implements AutoCloseable {
    static final String BOOTSTRAP_SLOT = "resourceExecutionAsync";
    static final String INSPECTION_BOOTSTRAP_SLOT = "resourceExecutionInspectAsync";

    private final ProtosPrelude executionPrelude;
    private final ProtosTestResourcefulAttemptBridge bridge;
    private final Set<Operation> outstanding = new LinkedHashSet<>();
    private boolean closed;

    private ProtosTestResourcefulExecutionFacility(
            ProtosPrelude executionPrelude,
            ProtosTestResourcefulAttemptBridge bridge) {
        this.executionPrelude = Objects.requireNonNull(executionPrelude, "executionPrelude");
        this.bridge = Objects.requireNonNull(bridge, "bridge");
    }

    static ProtosTestResourcefulExecutionFacility install(
            ProtosActivation activation,
            ProtosTestResourceProviderRegistry registry,
            ProtosPolyglotRuntimeHost runtimeHost,
            ProtosAsyncExactExecutionFacility.Submission submission) {
        return install(
                activation,
                BOOTSTRAP_SLOT,
                registry,
                runtimeHost,
                submission,
                false);
    }

    static ProtosTestResourcefulExecutionFacility installInspection(
            ProtosActivation activation,
            ProtosTestResourceProviderRegistry registry,
            ProtosPolyglotRuntimeHost runtimeHost,
            ProtosAsyncExactExecutionFacility.Submission submission) {
        return install(
                activation,
                INSPECTION_BOOTSTRAP_SLOT,
                registry,
                runtimeHost,
                submission,
                true);
    }

    private static ProtosTestResourcefulExecutionFacility install(
            ProtosActivation activation,
            String slotName,
            ProtosTestResourceProviderRegistry registry,
            ProtosPolyglotRuntimeHost runtimeHost,
            ProtosAsyncExactExecutionFacility.Submission submission,
            boolean inspection) {
        Objects.requireNonNull(activation, "activation");
        Objects.requireNonNull(slotName, "slotName");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(runtimeHost, "runtimeHost");
        Objects.requireNonNull(submission, "submission");

        ProtosPrelude executionPrelude =
                activation
                        .prelude()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "resourceful execution facility requires Core prelude"));

        if (activation.context().hasLocalSlot(slotName)) {
            throw new IllegalStateException(
                    "resourceful execution bootstrap slot already exists: " + slotName);
        }

        ProtosTestResourcefulExecutionFacility facility =
                new ProtosTestResourcefulExecutionFacility(
                        executionPrelude,
                        new ProtosTestResourcefulAttemptBridge(
                                registry,
                                runtimeHost,
                                submission));

        activation
                .context()
                .createLocalSlot(
                        slotName,
                        ProtosExactExecutionFacility.exactExecutionBootstrapClosure(
                                (callActivation, arguments) ->
                                        inspection
                                                ? facility.inspect(callActivation, arguments)
                                                : facility.execute(callActivation, arguments)));
        return facility;
    }

    private Object execute(ProtosActivation caller, List<?> arguments) {
        if (arguments.size() != 2
                || !(arguments.get(0) instanceof ProtosStringValue source)
                || !(arguments.get(1) instanceof ProtosArrayValue bindingSnapshot)) {
            throw ProtosExactExecutionFacility.ordinaryError(caller);
        }

        List<ProtosTestResourceProviderRequest.Binding> bindings =
                parseBindings(caller, bindingSnapshot);

        ProtosTestResourcefulAttemptBridge.Request request =
                new ProtosTestResourcefulAttemptBridge.Request(
                        ProtosExactExecutionFacility.executionRequest(
                                source,
                                executionPrelude),
                        bindings);

        return start(
                caller,
                () -> bridge.execute(request));
    }

    private Object inspect(ProtosActivation caller, List<?> arguments) {
        if (arguments.size() != 3
                || !(arguments.get(0) instanceof ProtosStringValue source)
                || !(arguments.get(1) instanceof ProtosStringValue inspector)
                || !(arguments.get(2) instanceof ProtosArrayValue bindingSnapshot)) {
            throw ProtosExactExecutionFacility.ordinaryError(caller);
        }

        List<ProtosTestResourceProviderRequest.Binding> bindings =
                parseBindings(caller, bindingSnapshot);

        ProtosTestResourcefulAttemptBridge.InspectionRequest request =
                new ProtosTestResourcefulAttemptBridge.InspectionRequest(
                        ProtosExactExecutionFacility.inspectionInvocation(
                                source,
                                inspector,
                                executionPrelude),
                        bindings);

        return start(
                caller,
                () -> bridge.inspect(request));
    }

    private ProtosFutureValue start(
            ProtosActivation caller,
            AttemptStageFactory stageFactory) {
        Objects.requireNonNull(caller, "caller");
        Objects.requireNonNull(stageFactory, "stageFactory");

        ProtosPrelude callerPrelude =
                caller
                        .prelude()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "resourceful execution completion requires caller Core prelude"));

        ProtosFutureValue future =
                new ProtosFutureValue(
                        callerPrelude.futurePrototype(),
                        caller.executionDomain());
        Operation operation =
                new Operation(
                        caller,
                        callerPrelude,
                        future,
                        stageFactory);

        registerOutstanding(operation);
        caller.executionDomain().registerActorNonTaskFutureForRuntime(future);
        operation.start();
        return future;
    }

    private synchronized void registerOutstanding(Operation operation) {
        if (closed) {
            throw new IllegalStateException(
                    "resourceful execution facility is closed");
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

    private ProtosObjectValue rematerialize(
            ProtosTestResourceAttemptCompletion completion,
            ProtosActivation caller,
            ProtosPrelude callerPrelude) {
        Objects.requireNonNull(completion, "completion");

        ProtosObjectValue result =
                new ProtosObjectValue(ProtosObjectValue.rootObject());

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

        ArrayList<Object> infrastructureEvidence = new ArrayList<>();
        for (Throwable failure : completion.infrastructureFailures()) {
            infrastructureEvidence.add(
                    new ProtosStringValue(failure.toString()));
        }
        ProtosArrayValue failures =
                callerPrelude.newArray(infrastructureEvidence);
        failures.freeze();

        result.createLocalSlot("guestObservation", guestObservation);
        result.createLocalSlot(
                "infrastructureFailed",
                ProtosBooleanValue.of(completion.infrastructureFailed()));
        result.createLocalSlot("infrastructureFailures", failures);
        result.createLocalSlot(
                "capacitySafe",
                ProtosBooleanValue.of(
                        completion.capacityDisposition()
                                == ProtosTestResourceAttemptCompletion.CapacityDisposition.SAFE));
        result.freeze();
        return result;
    }

    private static List<ProtosTestResourceProviderRequest.Binding> parseBindings(
            ProtosActivation caller,
            ProtosArrayValue bindingSnapshot) {
        ArrayList<ProtosTestResourceProviderRequest.Binding> bindings =
                new ArrayList<>();

        for (Object rowValue : bindingSnapshot.indexedSnapshot()) {
            if (!(rowValue instanceof ProtosArrayValue row)) {
                throw ProtosExactExecutionFacility.ordinaryError(caller);
            }

            List<Object> fields = row.indexedSnapshot();
            if (fields.size() != 6) {
                throw ProtosExactExecutionFacility.ordinaryError(caller);
            }

            String key = requireString(caller, fields.get(0));
            String mode = requireString(caller, fields.get(1));
            BigInteger units = optionalInteger(caller, fields.get(2));
            String scope = requireString(caller, fields.get(3));
            String provider = requireString(caller, fields.get(4));
            String profile = optionalString(caller, fields.get(5));

            bindings.add(
                    new ProtosTestResourceProviderRequest.Binding(
                            key,
                            mode,
                            units,
                            scope,
                            provider,
                            profile));
        }

        if (bindings.isEmpty()) {
            throw ProtosExactExecutionFacility.ordinaryError(caller);
        }
        return List.copyOf(bindings);
    }

    private static String requireString(ProtosActivation caller, Object value) {
        if (!(value instanceof ProtosStringValue string)) {
            throw ProtosExactExecutionFacility.ordinaryError(caller);
        }
        return string.value();
    }

    private static String optionalString(ProtosActivation caller, Object value) {
        if (value == ProtosNullValue.INSTANCE) {
            return null;
        }
        return requireString(caller, value);
    }

    private static BigInteger optionalInteger(ProtosActivation caller, Object value) {
        if (value == ProtosNullValue.INSTANCE) {
            return null;
        }
        if (!(value instanceof ProtosIntegerValue integer)) {
            throw ProtosExactExecutionFacility.ordinaryError(caller);
        }
        return integer.value();
    }

    private static Throwable unwrapCompletionFailure(Throwable failure) {
        Throwable current = Objects.requireNonNull(failure, "failure");
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    @FunctionalInterface
    private interface AttemptStageFactory {
        CompletionStage<ProtosTestResourceAttemptCompletion> start();
    }

    private final class Operation {
        private final ProtosActivation caller;
        private final ProtosPrelude callerPrelude;
        private final ProtosFutureValue future;
        private final AttemptStageFactory stageFactory;
        private boolean terminal;

        private Operation(
                ProtosActivation caller,
                ProtosPrelude callerPrelude,
                ProtosFutureValue future,
                AttemptStageFactory stageFactory) {
            this.caller = Objects.requireNonNull(caller, "caller");
            this.callerPrelude = Objects.requireNonNull(callerPrelude, "callerPrelude");
            this.future = Objects.requireNonNull(future, "future");
            this.stageFactory = Objects.requireNonNull(stageFactory, "stageFactory");
        }

        private void start() {
            final CompletionStage<ProtosTestResourceAttemptCompletion> stage;
            try {
                stage =
                        Objects.requireNonNull(
                                stageFactory.start(),
                                "resourceful attempt stage");
            } catch (RuntimeException | Error failure) {
                enqueueCallerCompletion(
                        infrastructureUnsafeCompletion(failure));
                finishHost();
                return;
            }

            stage.whenComplete(
                    (completion, failure) -> {
                        ProtosTestResourceAttemptCompletion terminalCompletion =
                                failure == null
                                        ? Objects.requireNonNull(
                                                completion,
                                                "resourceful attempt completion")
                                        : infrastructureUnsafeCompletion(
                                                unwrapCompletionFailure(failure));
                        enqueueCallerCompletion(terminalCompletion);
                        finishHost();
                    });
        }

        private ProtosTestResourceAttemptCompletion infrastructureUnsafeCompletion(
                Throwable failure) {
            return new ProtosTestResourceAttemptCompletion(
                    null,
                    List.of(Objects.requireNonNull(failure, "failure")),
                    ProtosTestResourceAttemptCompletion.CapacityDisposition.UNSAFE);
        }

        private void enqueueCallerCompletion(
                ProtosTestResourceAttemptCompletion completion) {
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
                                                    Object value =
                                                            rematerialize(
                                                                    completion,
                                                                    caller,
                                                                    callerPrelude);
                                                    future.resolve(value, caller);
                                                    return ProtosNullValue.INSTANCE;
                                                }));
            } catch (IllegalStateException callerTerminated) {
                future.cancelTerminal();
            }
        }

        private void finishHost() {
            boolean terminalNow;
            synchronized (this) {
                terminalNow = !terminal;
                terminal = true;
            }
            if (terminalNow) {
                operationTerminal(this);
            }
        }
    }
}
