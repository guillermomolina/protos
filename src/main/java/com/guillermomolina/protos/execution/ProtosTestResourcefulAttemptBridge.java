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

import com.guillermomolina.protos.runtime.ProtosByteIoFlow;
import com.guillermomolina.protos.runtime.ProtosMapValue;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
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
 * Private TOOL002-I8D4B bridge from one already-reserved binding set to one D108 terminal attempt.
 *
 * <p>This class owns no scheduler policy and releases no I8B reservation. Provider provisioning is
 * allowed to remain asynchronous. Only after successful provisioning is child execution submitted
 * to the explicit off-domain host Submission. The returned stage completes only with a D108
 * terminal envelope after Process/host terminality and provider cleanup.
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

        final CompletionStage<ProtosTestResourceProviderTransaction> provisionStage;
        try {
            provisionStage =
                    Objects.requireNonNull(
                            coordinator.provision(request.bindings()),
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
                            return submitStarted(request, transaction);
                        });
    }

    private CompletionStage<ProtosTestResourceAttemptCompletion> submitStarted(
            Request request,
            ProtosTestResourceProviderTransaction transaction) {
        CompletableFuture<ProtosTestResourceAttemptCompletion> terminal =
                new CompletableFuture<>();

        try {
            ProtosAsyncExactExecutionFacility.Submitted accepted =
                    Objects.requireNonNull(
                            submission.submit(
                                    () -> runSubmitted(request, transaction, terminal)),
                            "resourceful attempt submission handle");
            // I8D4B intentionally exposes no cancellation policy yet. Keeping the accepted handle
            // local proves the submission contract was fulfilled without inventing withdrawal
            // semantics ahead of I8D4C.
            Objects.requireNonNull(accepted, "accepted resourceful submission");
        } catch (RuntimeException | Error failure) {
            return ProtosTestResourceAttemptTerminalizer.finishUnstarted(
                    failure, transaction);
        }

        return terminal;
    }

    private void runSubmitted(
            Request request,
            ProtosTestResourceProviderTransaction transaction,
            CompletableFuture<ProtosTestResourceAttemptCompletion> terminal) {
        final CompletionStage<ProtosTestResourceAttemptCompletion> completionStage;
        try {
            completionStage = executeStarted(request, transaction);
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
            Request request,
            ProtosTestResourceProviderTransaction transaction) {
        ProtosCapturedProcessExecution.Request execution = request.execution();

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
                    processContext.execute(
                            execution.entry(),
                            bootstrap.activation());
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
