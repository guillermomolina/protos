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

import com.guillermomolina.protos.runtime.ProtosByteIoFlow;
import com.guillermomolina.protos.runtime.ProtosFilesystemValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Private D133/D134 physical project-tree CaseAuthority bridge.
 *
 * <p>The caller already owns scheduling admission. Each call materializes one fresh read-only
 * authority rooted at {@code casesRoot/fixtureIdentity}, executes exactly one fresh Process, and
 * tears the physical authority down before returning terminal evidence.
 */
final class ProtosTestCaseAuthorityAttemptBridge {

    record Request(
            ProtosCapturedProcessExecution.Request execution,
            Path casesRoot,
            String fixtureIdentity) {
        Request {
            Objects.requireNonNull(execution, "execution");
            Objects.requireNonNull(casesRoot, "casesRoot");
            Objects.requireNonNull(fixtureIdentity, "fixtureIdentity");
            if (fixtureIdentity.isEmpty()) {
                throw new IllegalArgumentException(
                        "CaseAuthority fixture identity must not be empty");
            }
            casesRoot = casesRoot.toAbsolutePath().normalize();
        }
    }

    private final ProtosPolyglotRuntimeHost runtimeHost;

    ProtosTestCaseAuthorityAttemptBridge(ProtosPolyglotRuntimeHost runtimeHost) {
        this.runtimeHost = Objects.requireNonNull(runtimeHost, "runtimeHost");
    }

    ProtosTestCaseAuthorityAttemptCompletion execute(Request request) {
        Objects.requireNonNull(request, "request");

        ArrayList<Throwable> infrastructureFailures = new ArrayList<>();
        ProtosCapturedProcessExecution.Result guestObservation = null;
        ProtosNioReadOnlyTreeFilesystemBackend backend = null;

        try {
            Path authorityRoot =
                    resolveAuthorityRoot(
                            request.casesRoot(),
                            request.fixtureIdentity());

            backend =
                    new ProtosNioReadOnlyTreeFilesystemBackend(
                            authorityRoot);

            if (!backend.secureConfinementAvailable()) {
                infrastructureFailures.add(
                        new IOException(
                                "secure project-tree CaseAuthority confinement is unavailable"));
            } else {
                guestObservation =
                        executeWithAuthority(
                                request.execution(),
                                backend,
                                infrastructureFailures);
            }
        } catch (IOException failure) {
            infrastructureFailures.add(failure);
        } finally {
            if (backend != null) {
                try {
                    backend.close();
                } catch (IOException | RuntimeException failure) {
                    infrastructureFailures.add(failure);
                }
            }
        }

        return new ProtosTestCaseAuthorityAttemptCompletion(
                guestObservation,
                infrastructureFailures);
    }

    private ProtosCapturedProcessExecution.Result executeWithAuthority(
            ProtosCapturedProcessExecution.Request execution,
            ProtosNioReadOnlyTreeFilesystemBackend backend,
            List<Throwable> infrastructureFailures) {
        PrivateReadableBackend stdin =
                new PrivateReadableBackend(execution.stdin());
        PrivateWritableBackend stdout = new PrivateWritableBackend();
        PrivateWritableBackend stderr = new PrivateWritableBackend();

        ProtosStandaloneProcessBootstrap.Result bootstrap =
                ProtosStandaloneProcessBootstrap.create(
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
                        execution.defaultFilesystem());

        ProtosProcessRuntime process = bootstrap.process();

        final ProtosPolyglotProcessContext processContext;
        try {
            processContext =
                    runtimeHost.hostProcess(
                            process,
                            InputStream.nullInputStream(),
                            OutputStream.nullOutputStream(),
                            OutputStream.nullOutputStream());
        } catch (RuntimeException failure) {
            infrastructureFailures.add(failure);
            terminateProcess(process, infrastructureFailures);
            return null;
        }

        try {
            ProtosObjectValue rawFilesystem =
                    ProtosStandardFilesystemProtocol.createCapability(
                            execution.prelude().bytesPrototypeForRuntime(),
                            bootstrap.activation(),
                            backend);

            if (!(rawFilesystem instanceof ProtosFilesystemValue filesystem)) {
                throw new IllegalStateException(
                        "project-tree CaseAuthority Filesystem has the wrong value family");
            }

            if (bootstrap.activation()
                    .context()
                    .hasLocalSlot("projectTreeFilesystem")) {
                throw new IllegalStateException(
                        "project-tree CaseAuthority slot already exists");
            }

            bootstrap.activation()
                    .context()
                    .createLocalSlot(
                            "projectTreeFilesystem",
                            filesystem);

            ProtosExecutionOutcome outcome =
                    processContext.execute(
                            execution.entry(),
                            bootstrap.activation());

            return new ProtosCapturedProcessExecution.Result(
                    outcome,
                    stdout.snapshot(),
                    stderr.snapshot());
        } finally {
            terminateProcess(process, infrastructureFailures);
        }
    }

    private static Path resolveAuthorityRoot(
            Path casesRoot,
            String fixtureIdentity) {
        Path logical = Path.of(fixtureIdentity);

        if (logical.isAbsolute()) {
            throw new IllegalArgumentException(
                    "CaseAuthority fixture identity must be relative");
        }

        Path resolved =
                casesRoot.resolve(logical).normalize();

        if (!resolved.startsWith(casesRoot)) {
            throw new IllegalArgumentException(
                    "CaseAuthority fixture identity escapes cases root");
        }

        return resolved;
    }

    private static void terminateProcess(
            ProtosProcessRuntime process,
            List<Throwable> infrastructureFailures) {
        try {
            process.requestTerminationForRuntime();
        } catch (RuntimeException failure) {
            infrastructureFailures.add(failure);
        }
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

            int count =
                    Math.min(
                            maxBytes,
                            bytes.length - position);

            byte[] chunk =
                    Arrays.copyOfRange(
                            bytes,
                            position,
                            position + count);

            position += count;
            completion.data(chunk);
            return () -> {};
        }
    }

    private static final class PrivateWritableBackend {
        private final ByteArrayOutputStream bytes =
                new ByteArrayOutputStream();

        synchronized ProtosByteIoFlow.Cancellation write(
                byte[] contribution,
                ProtosByteIoFlow.WriteCompletion completion) {
            Objects.requireNonNull(contribution, "contribution");
            Objects.requireNonNull(completion, "completion");

            bytes.write(
                    contribution,
                    0,
                    contribution.length);

            completion.succeeded();
            return () -> {};
        }

        synchronized byte[] snapshot() {
            return bytes.toByteArray();
        }
    }
}
