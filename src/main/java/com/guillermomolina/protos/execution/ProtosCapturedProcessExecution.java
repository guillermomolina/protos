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
import com.guillermomolina.protos.runtime.ProtosByteIoFlow;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosEncodingValue;
import com.guillermomolina.protos.runtime.ProtosEnvironmentValue;
import com.guillermomolina.protos.runtime.ProtosFilesystemValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosTask;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.oracle.truffle.api.source.Source;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Executes one exact Source in a fresh semantic Process with private in-memory standard streams.
 *
 * <p>This is a local host mechanism, not a test framework. It owns no manifest, expectation,
 * assertion, CaseId, retry, scheduling, reporting or worker policy. The caller has already selected
 * the exact Source and supplied the exact Process bootstrap data. Parsing occurs only inside the
 * fresh Process Context.
 *
 * <p>The initial implementation captures stdout/stderr in memory because TOOL002-C is deliberately
 * sequential. Later large-scale scheduling may replace the capture storage behind a higher-level
 * runner boundary without changing {@link ProtosFreshProcessExecutor}.
 */
public final class ProtosCapturedProcessExecution {
    private ProtosCapturedProcessExecution() {}

    public record Request(
            ProtosPrelude prelude,
            Source entry,
            List<String> applicationArguments,
            ProtosEnvironmentValue.NativeNameDomain environmentNameDomain,
            List<ProtosEnvironmentValue.NativeEntry> environmentEntries,
            byte[] stdin,
            ProtosEncodingValue stdinEncoding,
            ProtosEncodingValue stdoutEncoding,
            ProtosEncodingValue stderrEncoding,
            ProtosFilesystemValue defaultFilesystem) {

        public Request {
            Objects.requireNonNull(prelude, "prelude");
            Objects.requireNonNull(entry, "entry");
            Objects.requireNonNull(applicationArguments, "applicationArguments");
            Objects.requireNonNull(environmentNameDomain, "environmentNameDomain");
            Objects.requireNonNull(environmentEntries, "environmentEntries");
            Objects.requireNonNull(stdin, "stdin");
            Objects.requireNonNull(stdinEncoding, "stdinEncoding");
            Objects.requireNonNull(stdoutEncoding, "stdoutEncoding");
            Objects.requireNonNull(stderrEncoding, "stderrEncoding");

            applicationArguments = List.copyOf(applicationArguments);
            environmentEntries = List.copyOf(environmentEntries);
            stdin = Arrays.copyOf(stdin, stdin.length);
        }

        @Override
        public byte[] stdin() {
            return Arrays.copyOf(stdin, stdin.length);
        }
    }

    public record Result(
            ProtosExecutionOutcome outcome,
            byte[] stdout,
            byte[] stderr) {

        public Result {
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(stdout, "stdout");
            Objects.requireNonNull(stderr, "stderr");
            stdout = Arrays.copyOf(stdout, stdout.length);
            stderr = Arrays.copyOf(stderr, stderr.length);
        }

        @Override
        public byte[] stdout() {
            return Arrays.copyOf(stdout, stdout.length);
        }

        @Override
        public byte[] stderr() {
            return Arrays.copyOf(stderr, stderr.length);
        }
    }

    public static Result execute(Request request) {
        Objects.requireNonNull(request, "request");
        try (ProtosPolyglotRuntimeHost runtimeHost = ProtosPolyglotRuntimeHost.open()) {
            return execute(request, runtimeHost);
        }
    }

    public static Result execute(
            Request request, ProtosPolyglotRuntimeHost runtimeHost) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(runtimeHost, "runtimeHost");

        PrivateReadableBackend stdin = new PrivateReadableBackend(request.stdin());
        PrivateWritableBackend stdout = new PrivateWritableBackend();
        PrivateWritableBackend stderr = new PrivateWritableBackend();

        ProtosExecutionOutcome outcome =
                ProtosFreshProcessExecutor.execute(
                        new ProtosFreshProcessExecutor.Request(
                                request.prelude(),
                                request.entry(),
                                request.applicationArguments(),
                                request.environmentNameDomain(),
                                request.environmentEntries(),
                                stdin::read,
                                stdout::write,
                                stderr::write,
                                request.stdinEncoding(),
                                request.stdoutEncoding(),
                                request.stderrEncoding(),
                                request.defaultFilesystem()),
                        runtimeHost);

        return new Result(outcome, stdout.snapshot(), stderr.snapshot());
    }

    /**
     * Executes one exact source directly in a fresh Process, drains currently runnable
     * RootActor-local cooperative work to idle, then invokes one exact inspector Closure with the
     * still-live source result as a real RootActor-local cooperative task. Source tasks that are
     * suspended on the returned asynchronous result may remain live at that intermediate idle
     * boundary; the inspector may therefore await that result through ordinary Future semantics
     * while other Actors in the Process progress only through the production Actor scheduler.
     * Source parsing, cooperative dispatch, inspector parsing and inspector execution all remain
     * inside that Process Context.
     */
    public static Result executeThenInspect(Request request, Source inspector) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(inspector, "inspector");
        try (ProtosPolyglotRuntimeHost runtimeHost = ProtosPolyglotRuntimeHost.open()) {
            return executeThenInspect(request, inspector, runtimeHost);
        }
    }

    public static Result executeThenInspect(
            Request request,
            Source inspector,
            ProtosPolyglotRuntimeHost runtimeHost) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(inspector, "inspector");
        Objects.requireNonNull(runtimeHost, "runtimeHost");

        PrivateReadableBackend stdin = new PrivateReadableBackend(request.stdin());
        PrivateWritableBackend stdout = new PrivateWritableBackend();
        PrivateWritableBackend stderr = new PrivateWritableBackend();

        ProtosStandaloneProcessBootstrap.Result bootstrap =
                ProtosStandaloneProcessBootstrap.create(
                        request.prelude(),
                        request.applicationArguments(),
                        request.environmentNameDomain(),
                        request.environmentEntries(),
                        stdin::read,
                        stdout::write,
                        stderr::write,
                        request.stdinEncoding(),
                        request.stdoutEncoding(),
                        request.stderrEncoding(),
                        request.defaultFilesystem());

        try {
            ProtosActivation activation = bootstrap.activation();
            ProtosPolyglotProcessContext processContext =
                    runtimeHost.hostProcess(
                            bootstrap.process(),
                            InputStream.nullInputStream(),
                            OutputStream.nullOutputStream(),
                            OutputStream.nullOutputStream());
            Object subject;
            try {
                subject = processContext.evaluatePersistent(request.entry(), activation);
            } catch (ProtosSignalException signal) {
                return new Result(
                        ProtosExecutionOutcome.failed(signal.error()),
                        stdout.snapshot(),
                        stderr.snapshot());
            }

            processContext.callForRuntime(
                    () -> {
                        activation.executionDomain().dispatchUntilIdle();
                        return null;
                    });
            ProtosExecutionOutcome outcome;
            try {
                Object inspectorValue = processContext.evaluatePersistent(inspector, activation);
                if (!(inspectorValue instanceof ProtosClosureValue inspectorClosure)) {
                    throw new IllegalStateException(
                            "direct inspection inspector source must evaluate to Closure");
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
                            "cooperative inspection inspector left live RootActor tasks");
                }
                outcome = taskOutcome(inspectorTask);
            } catch (ProtosSignalException signal) {
                outcome = ProtosExecutionOutcome.failed(signal.error());
            }

            return new Result(outcome, stdout.snapshot(), stderr.snapshot());
        } finally {
            bootstrap.process().requestTerminationForRuntime();
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
                                                            "completed inspection task has no result")));
            case FAILED -> {
                Object failure =
                        task.failure()
                                .orElseThrow(
                                        () ->
                                                new IllegalStateException(
                                                        "failed inspection task has no error"));
                if (!(failure instanceof ProtosObjectValue error)) {
                    throw new IllegalStateException(
                            "inspection task failed with a non-Protos error value");
                }
                yield ProtosExecutionOutcome.failed(error);
            }
            case CANCELLED -> ProtosExecutionOutcome.cancelled();
            default ->
                    throw new IllegalStateException(
                            "inspection task returned before reaching a terminal state");
        };
    }

    private static final class PrivateReadableBackend {
        private final byte[] bytes;
        private int position;

        PrivateReadableBackend(byte[] bytes) {
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
