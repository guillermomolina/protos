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
import com.guillermomolina.protos.runtime.ProtosByteIoFlow;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosMapValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosTask;
import com.oracle.truffle.api.source.Source;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * Private TOOL002-I8D4B/C2 bridge from one already-reserved binding set to one D108 terminal
 * attempt.
 *
 * <p>The ordinary execution path remains the closed I8D4B contract. I8D4C2 adds only the
 * resourceful live-result inspection variant needed by already-owned Test Tool future expectation
 * policy: source and inspector both execute inside the same resource-provisioned fresh Process,
 * and only the inspector's detached terminal observation crosses back to the caller.
 *
 * <p>This class owns no scheduler policy and releases no I8B reservation. Provider provisioning is
 * allowed to remain asynchronous. Only after successful provisioning is child execution submitted
 * to the explicit off-domain host Submission. Returned stages complete only with D108 terminal
 * envelopes after Process/host terminality and provider cleanup.
 */
final class ProtosTestResourcefulAttemptBridge {

    record Request(
            ProtosCapturedProcessExecution.Request execution,
            List<ProtosTestResourceProviderRequest.Binding> bindings) {
        Request {
            Objects.requireNonNull(execution, "execution");
            Objects.requireNonNull(bindings, "bindings");
            bindings = List.copyOf(bindings);
            if (bindings.isEmpty()) {
                throw new IllegalArgumentException(
                        "resourceful attempt bridge requires at least one reserved binding");
            }
        }
    }

    record InspectionRequest(
            ProtosExactExecutionFacility.InspectionInvocation invocation,
            List<ProtosTestResourceProviderRequest.Binding> bindings) {
        InspectionRequest {
            Objects.requireNonNull(invocation, "invocation");
            Objects.requireNonNull(bindings, "bindings");
            bindings = List.copyOf(bindings);
            if (bindings.isEmpty()) {
                throw new IllegalArgumentException(
                        "resourceful inspection bridge requires at least one reserved binding");
            }
        }
    }

    private record ProvisionOutcome(
            ProtosTestResourceProviderTransaction transaction,
            Throwable failure) {}

    private final ProtosTestResourceProviderCoordinator coordinator;
    private final ProtosPolyglotRuntimeHost runtimeHost;
    private final ProtosAsyncExactExecutionFacility.Submission submission;

    ProtosTestResourcefulAttemptBridge(
            ProtosTestResourceProviderRegistry registry,
            ProtosPolyglotRuntimeHost runtimeHost,
            ProtosAsyncExactExecutionFacility.Submission submission) {
        coordinator =
                new ProtosTestResourceProviderCoordinator(
                        Objects.requireNonNull(registry, "registry"));
        this.runtimeHost = Objects.requireNonNull(runtimeHost, "runtimeHost");
        this.submission = Objects.requireNonNull(submission, "submission");
    }

    CompletionStage<ProtosTestResourceAttemptCompletion> execute(Request request) {
        Objects.requireNonNull(request, "request");
        return provisionThenSubmit(
                request.execution(),
                null,
                request.bindings());
    }

    CompletionStage<ProtosTestResourceAttemptCompletion> inspect(InspectionRequest request) {
        Objects.requireNonNull(request, "request");
        return provisionThenSubmit(
                request.invocation().request(),
                request.invocation().inspector(),
                request.bindings());
    }

    private CompletionStage<ProtosTestResourceAttemptCompletion> provisionThenSubmit(
            ProtosCapturedProcessExecution.Request execution,
            Source inspector,
            List<ProtosTestResourceProviderRequest.Binding> bindings) {
        final CompletionStage<ProtosTestResourceProviderTransaction> provisionStage;
        try {
            provisionStage =
                    Objects.requireNonNull(
                            coordinator.provision(bindings),
                            "provider provisioning transaction stage");
        } catch (RuntimeException | Error failure) {
            return CompletableFuture.completedFuture(
                    ProtosTestResourceAttemptTerminalizer.provisioningFailure(failure));
        }

        return provisionStage
                .handle(
                        (transaction, failure) ->
                                new ProvisionOutcome(
                                        transaction,
                                        failure == null
                                                ? null
                                                : unwrapCompletionFailure(failure)))
                .thenCompose(
                        outcome -> {
                            if (outcome.failure() != null) {
                                return CompletableFuture.completedFuture(
                                        ProtosTestResourceAttemptTerminalizer
                                                .provisioningFailure(outcome.failure()));
                            }
                            ProtosTestResourceProviderTransaction transaction =
                                    Objects.requireNonNull(
                                            outcome.transaction(),
                                            "successful provider transaction");
                            return submitStarted(execution, inspector, transaction);
                        });
    }

    private CompletionStage<ProtosTestResourceAttemptCompletion> submitStarted(
            ProtosCapturedProcessExecution.Request execution,
            Source inspector,
            ProtosTestResourceProviderTransaction transaction) {
        CompletableFuture<ProtosTestResourceAttemptCompletion> terminal =
                new CompletableFuture<>();

        try {
            ProtosAsyncExactExecutionFacility.Submitted accepted =
                    Objects.requireNonNull(
                            submission.submit(
                                    () ->
                                            runSubmitted(
                                                    execution,
                                                    inspector,
                                                    transaction,
                                                    terminal)),
                            "resourceful attempt submission handle");
            // I8D4C2 deliberately introduces no cancellation/withdrawal semantics. Keeping the
            // accepted handle local proves the existing Submission contract was fulfilled.
            Objects.requireNonNull(accepted, "accepted resourceful submission");
        } catch (RuntimeException | Error failure) {
            return ProtosTestResourceAttemptTerminalizer.finishUnstarted(
                    failure, transaction);
        }

        return terminal;
    }

    private void runSubmitted(
            ProtosCapturedProcessExecution.Request execution,
            Source inspector,
            ProtosTestResourceProviderTransaction transaction,
            CompletableFuture<ProtosTestResourceAttemptCompletion> terminal) {
        final CompletionStage<ProtosTestResourceAttemptCompletion> completionStage;
        try {
            completionStage = executeStarted(execution, inspector, transaction);
        } catch (RuntimeException | Error failure) {
            ProtosTestResourceAttemptTerminalizer
                    .finishUnstarted(failure, transaction)
                    .whenComplete(
                            (completion, terminalFailure) ->
                                    completeTerminal(
                                            terminal, completion, terminalFailure));
            return;
        }

        completionStage.whenComplete(
                (completion, terminalFailure) ->
                        completeTerminal(terminal, completion, terminalFailure));
    }

    private CompletionStage<ProtosTestResourceAttemptCompletion> executeStarted(
            ProtosCapturedProcessExecution.Request execution,
            Source inspector,
            ProtosTestResourceProviderTransaction transaction) {

        final ProtosMapValue resources;
        try {
            resources =
                    ProtosTestResourceCapabilityBundle.createResourceful(
                            execution.prelude(),
                            transaction.candidateGuestCapabilities());
        } catch (RuntimeException | Error failure) {
            return ProtosTestResourceAttemptTerminalizer.finishUnstarted(
                    failure, transaction);
        }

        PrivateReadableBackend stdin =
                new PrivateReadableBackend(execution.stdin());
        PrivateWritableBackend stdout = new PrivateWritableBackend();
        PrivateWritableBackend stderr = new PrivateWritableBackend();

        final ProtosStandaloneProcessBootstrap.Result bootstrap;
        try {
            bootstrap =
                    ProtosStandaloneProcessBootstrap.createWithRootResources(
                            execution.prelude(),
                            execution.applicationArguments(),
                            execution.environmentNameDomain(),
                            execution.environmentEntries(),
                            stdin::read,
                            stdout::write,
                            stderr::write,
                            execution.stdinEncoding(),
                            execution.stdoutEncoding(),
                            execution.stderrEncoding(),
                            execution.defaultFilesystem(),
                            null,
                            resources);
        } catch (RuntimeException | Error failure) {
            return ProtosTestResourceAttemptTerminalizer.finishUnstarted(
                    failure, transaction);
        }

        ProtosProcessRuntime process = bootstrap.process();
        final ProtosPolyglotProcessContext processContext;
        try {
            processContext =
                    runtimeHost.hostProcess(
                            process,
                            InputStream.nullInputStream(),
                            OutputStream.nullOutputStream(),
                            OutputStream.nullOutputStream());
        } catch (RuntimeException | Error failure) {
            return ProtosTestResourceAttemptTerminalizer.finishCreated(
                    process, null, List.of(failure), transaction);
        }

        final ProtosExecutionOutcome outcome;
        try {
            outcome =
                    inspector == null
                            ? processContext.execute(
                                    execution.entry(),
                                    bootstrap.activation())
                            : inspectStarted(
                                    processContext,
                                    bootstrap.activation(),
                                    execution.entry(),
                                    inspector);
        } catch (RuntimeException | Error failure) {
            return ProtosTestResourceAttemptTerminalizer.finishCreated(
                    process, null, List.of(failure), transaction);
        }

        ProtosCapturedProcessExecution.Result guestObservation =
                new ProtosCapturedProcessExecution.Result(
                        outcome,
                        stdout.snapshot(),
                        stderr.snapshot());

        return ProtosTestResourceAttemptTerminalizer.finishStarted(
                process, guestObservation, transaction);
    }

    private static ProtosExecutionOutcome inspectStarted(
            ProtosPolyglotProcessContext processContext,
            ProtosActivation activation,
            Source entry,
            Source inspector) {
        final Object subject;
        try {
            subject = processContext.evaluatePersistent(entry, activation);
        } catch (ProtosSignalException signal) {
            return ProtosExecutionOutcome.failed(signal.error());
        }

        processContext.callForRuntime(
                () -> {
                    activation.executionDomain().dispatchUntilIdle();
                    return null;
                });

        try {
            Object inspectorValue =
                    processContext.evaluatePersistent(inspector, activation);
            if (!(inspectorValue instanceof ProtosClosureValue inspectorClosure)) {
                throw new IllegalStateException(
                        "resourceful inspection inspector source must evaluate to Closure");
            }

            ProtosTask inspectorTask =
                    activation.executionDomain()
                            .createTask(
                                    null,
                                    null,
                                    task ->
                                            ProtosClosureInvoker.executeInTaskForRuntime(
                                                    inspectorClosure,
                                                    List.of(subject),
                                                    activation,
                                                    task));

            processContext.callForRuntime(
                    () -> {
                        activation.executionDomain()
                                .dispatchUntilTerminal(inspectorTask, () -> false);
                        return null;
                    });

            if (activation.executionDomain().liveTaskCount() != 0) {
                throw new IllegalStateException(
                        "resourceful inspection inspector left live RootActor tasks");
            }
            return taskOutcome(inspectorTask);
        } catch (ProtosSignalException signal) {
            return ProtosExecutionOutcome.failed(signal.error());
        }
    }

    private static ProtosExecutionOutcome taskOutcome(ProtosTask task) {
        return switch (task.state()) {
            case COMPLETED ->
                    ProtosExecutionOutcome.completed(
                            task.result()
                                    .orElseThrow(
                                            () ->
                                                    new IllegalStateException(
                                                            "completed resourceful inspection task has no result")));
            case FAILED -> {
                Object failure =
                        task.failure()
                                .orElseThrow(
                                        () ->
                                                new IllegalStateException(
                                                        "failed resourceful inspection task has no error"));
                if (!(failure instanceof ProtosObjectValue error)) {
                    throw new IllegalStateException(
                            "resourceful inspection task failed with a non-Protos error value");
                }
                yield ProtosExecutionOutcome.failed(error);
            }
            case CANCELLED -> ProtosExecutionOutcome.cancelled();
            default ->
                    throw new IllegalStateException(
                            "resourceful inspection task returned before reaching a terminal state");
        };
    }

    private static void completeTerminal(
            CompletableFuture<ProtosTestResourceAttemptCompletion> terminal,
            ProtosTestResourceAttemptCompletion completion,
            Throwable terminalFailure) {
        if (terminalFailure != null) {
            terminal.completeExceptionally(unwrapCompletionFailure(terminalFailure));
        } else {
            terminal.complete(
                    Objects.requireNonNull(completion, "terminal attempt completion"));
        }
    }

    private static Throwable unwrapCompletionFailure(Throwable failure) {
        Throwable current = Objects.requireNonNull(failure, "failure");
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static final class PrivateReadableBackend {
        private final byte[] bytes;
        private int position;

        private PrivateReadableBackend(byte[] bytes) {
            this.bytes = Arrays.copyOf(bytes, bytes.length);
        }

        synchronized ProtosByteIoFlow.Cancellation read(
                int maxBytes,
                ProtosByteIoFlow.ReadCompletion completion) {
            Objects.requireNonNull(completion, "completion");
            if (maxBytes <= 0) {
                completion.failed();
                return () -> {};
            }
            if (position >= bytes.length) {
                completion.eof();
                return () -> {};
            }
            int count = Math.min(maxBytes, bytes.length - position);
            byte[] chunk = Arrays.copyOfRange(bytes, position, position + count);
            position += count;
            completion.data(chunk);
            return () -> {};
        }
    }

    private static final class PrivateWritableBackend {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        synchronized ProtosByteIoFlow.Cancellation write(
                byte[] contribution,
                ProtosByteIoFlow.WriteCompletion completion) {
            Objects.requireNonNull(contribution, "contribution");
            Objects.requireNonNull(completion, "completion");
            bytes.write(contribution, 0, contribution.length);
            completion.succeeded();
            return () -> {};
        }

        synchronized byte[] snapshot() {
            return bytes.toByteArray();
        }
    }
}
